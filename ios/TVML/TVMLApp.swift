import SwiftUI

/// TVML 2.0: serwis informacyjny tvml.pl na iPhone'a i iPada.
/// Baner (logo, pogoda, szukaj, pasek „NA ŻYWO”) jest nad wszystkimi ekranami, a jego wygląd zmienia się
/// razem z wersją ustawioną w panelu (zima, święta, Sylwester, 11 Listopada, żałoba).
@main
struct TVMLApp: App {
    /// token APNs i dotknięcia powiadomień (Powiadomienia.swift)
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegat
    @StateObject private var magazyn = Magazyn()
    @AppStorage("tvml-wyglad") private var wyglad = "auto"

    var body: some Scene {
        WindowGroup {
            Glowny()
                .environmentObject(magazyn)
                // daty, liczby i systemowe napisy w języku aplikacji (pl, uk, en)
                .environment(\.locale, Jezyk.locale(magazyn.jezyk))
                // „Większy tekst” do poziomu AX3 (ponad dwa razy większy tekst); wyżej układ kart i pasków przestaje się mieścić
                .dynamicTypeSize(...DynamicTypeSize.accessibility3)
                .preferredColorScheme(wyglad == "dzien" ? .light : (wyglad == "noc" ? .dark : nil))
        }
    }
}

struct Glowny: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.scenePhase) private var faza
    /// Do zrzutów ekranu: -ekran pogoda|artykul|wideo|wiecej|zakladka2 (druga zakładka z panelu)|powiadomienia, -motyw zima|wigilia|…
    private let ekranStartowy = UserDefaults.standard.string(forKey: "ekran")
    private let motywTestowy = Motyw.zArgumentu(UserDefaults.standard.string(forKey: "motyw"))
    /// Do zrzutu odtwarzacza (-ekran film): pierwszy film z kanału na pełnym ekranie.
    @State private var filmTestowy: Film?
    /// Wybór języka z „Więcej” albo z flagi w nagłówku (ten sam zapis), zmiana obsłużona tutaj raz.
    @AppStorage(Jezyk.kluczWyboru) private var wyborJezyka = "system"

    var body: some View {
        VStack(spacing: 0) {
            Baner()
            TabView(selection: $m.zakladka) {
                ForEach(m.zakladki) { z in
                    NavigationStack(path: m.sciezka(z.id)) {
                        tresc(z)
                            .kolumna()
                            .toolbar(.hidden, for: .navigationBar)
                            .navigationDestination(for: Artykul.self) { ArtykulWidok(poczatkowy: $0).kolumna() }
                            .navigationDestination(for: Gmina.self) { ListaGminy(gmina: $0).kolumna() }
                            .navigationDestination(for: EkranWiecej.self) { EkranWiecejWidok(ekran: $0) }
                    }
                    .tabItem { Label(z.tytul, systemImage: z.ikona) }
                    .tag(z.id)
                }
            }
            .tint(m.motyw.akcent)
        }
        // zmiana języka w „Więcej” buduje ekrany od nowa (bez restartu); otwarte ekrany zostają w Magazynie
        .id(m.jezyk)
        .background(Barwy.tlo)
        .overlay { if m.wymaganaAktualizacja { EkranAktualizacji() } }
        .sheet(isPresented: $m.arkuszPogody) { NavigationStack { PogodaWidok().toolbar(.hidden, for: .navigationBar) } }
        .sheet(isPresented: $m.arkuszWideo) { NavigationStack { WideoWidok().toolbar(.hidden, for: .navigationBar) } }
        .fullScreenCover(item: $filmTestowy) { f in
            if let adres = f.osadzenie { Odtwarzacz(adres: adres) }
        }
        .task {
            // CI: karta pogody we wszystkich scenach do Documents/galeria (sprawdzanie czytelności)
            if GaleriaPogody.wlaczona { await GaleriaPogody.zapisz() }
            await Powiadomienia.shared.przyStarcie()
            await m.odswiez(wymus: true)
            if let motywTestowy { m.motyw = motywTestowy }
            switch ekranStartowy {
            case "pogoda": m.pokaz(.pogoda)
            case "wideo": m.pokaz(.wideo)
            case "wiecej": m.pokaz(.wiecej)
            case "artykul": if let pierwszy = m.najnowsze.first { m.otworz(pierwszy) }
            case "zakladka2": if m.zakladki.count > 1 { m.zakladka = m.zakladki[1].id }
            case "powiadomienia": m.pokaz(.wiecej); m.otworz(EkranWiecej.powiadomienia)
            case "film": filmTestowy = m.filmy.first
            default: break
            }
        }
        // dotknięte powiadomienie push: artykuł z articleId (także gdy aplikacja dopiero się uruchamia)
        .onReceive(Powiadomienia.shared.$doOtwarcia) { id in
            guard let id else { return }
            Powiadomienia.shared.doOtwarcia = nil
            Task { if let a = await m.artykul(id) { m.otworz(a) } }
        }
        .onChange(of: faza) { _, nowa in
            guard nowa == .active else { return }
            Task {
                await m.odswiez()
                if let motywTestowy { m.motyw = motywTestowy }
            }
        }
        .onChange(of: m.motyw) { _, nowy in
            guard motywTestowy == nil else { return }
            ustawIkone(nowy.ikona)
        }
        .onChange(of: wyborJezyka) { _, _ in Task { await m.zmienJezyk() } }
    }

    @ViewBuilder private func tresc(_ z: ZakladkaApp) -> some View {
        switch z.cel {
        case .start: StartWidok()
        case .wideo: WideoWidok()
        case .pogoda: PogodaWidok()
        case .wiecej: WiecejWidok()
        case .kategoria(let slug): ListaKategorii(slug: slug, tytul: z.tytul)
        }
    }

    /// Ikona zimowa i świąteczna są w aplikacji. Panel wybiera je przez wersję wyglądu, iOS pokazuje wtedy komunikat.
    private func ustawIkone(_ nazwa: String?) {
        let app = UIApplication.shared
        guard app.supportsAlternateIcons, app.applicationState == .active, app.alternateIconName != nazwa else { return }
        app.setAlternateIconName(nazwa) { _ in }
    }
}

extension Magazyn {
    func sciezka(_ z: String) -> Binding<NavigationPath> {
        Binding(get: { self.sciezki[z] ?? NavigationPath() }, set: { self.sciezki[z] = $0 })
    }

    func otworz(_ a: Artykul) {
        sciezki[zakladka, default: NavigationPath()].append(a)
    }

    func otworz(_ e: EkranWiecej) {
        sciezki[zakladka, default: NavigationPath()].append(e)
    }
}

/// Baner nad każdym ekranem: pasek życzeń z panelu, logo z ozdobą, pogoda, szukaj i pasek „NA ŻYWO”.
struct Baner: View {
    @EnvironmentObject private var m: Magazyn
    @State private var szukaj = false
    @AppStorage(Jezyk.kluczWyboru) private var wyborJezyka = "system"

    var body: some View {
        let motyw = m.motyw
        VStack(spacing: 0) {
            if let pasek = motyw.pasek {
                PasekZyczen(pasek: pasek, motyw: motyw)
                    .background(LinearGradient(colors: motyw.tloPaska, startPoint: .top, endPoint: .bottom).ignoresSafeArea(edges: .top))
            }
            HStack(spacing: 8) {
                Button { m.naStart() } label: {
                    Image(motyw.logo)
                        .resizable().scaledToFit()
                        .frame(height: 40)
                        .overlay(alignment: .topTrailing) { OzdobaLogo(motyw: motyw) }
                }
                .accessibilityLabel(Napisy.t("app.homeLogo", "Telewizja TVML, strona główna"))
                Spacer()
                Button { m.pokaz(.pogoda) } label: {
                    HStack(spacing: 6) {
                        Image(systemName: ikonaPogody(m.pogoda?.ikona ?? "cloud"))
                        Text(m.pogoda.map { Pogoda.stopnie($0.temperatura) + "C" } ?? "–")
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .padding(.horizontal, 12).frame(height: 36)
                    .background(.white.opacity(0.12), in: Capsule())
                }
                .accessibilityLabel(Napisy.t("app.weather", "Pogoda"))
                // flaga bieżącego języka; menu z pozostałymi (tylko języki z config.languages), jak „Więcej → Język”
                if Jezyk.dostepne.count > 1 {
                    Menu {
                        ForEach(["pl", "en", "uk"].filter { $0 != m.jezyk && Jezyk.dostepne.contains($0) }, id: \.self) { j in
                            Button { wyborJezyka = j } label: {
                                Label { Text(Jezyk.nazwy[j] ?? j) } icon: { Image(uiImage: FlagaJezyka.obraz(j)) }
                            }
                        }
                    } label: {
                        HStack(spacing: 5) {
                            FlagaJezyka(jezyk: m.jezyk)
                            Image(systemName: "chevron.down").font(.system(size: 10, weight: .bold))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12).frame(height: 36)
                        .background(.white.opacity(0.12), in: Capsule())
                    }
                    .menuIndicator(.hidden)
                    .accessibilityLabel(Napisy.t("app.language", "Język") + ": " + (Jezyk.nazwy[m.jezyk] ?? m.jezyk))
                }
                Button { szukaj = true } label: {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 17, weight: .semibold))
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.12), in: Circle())
                }
                .accessibilityLabel(Napisy.t("app.search", "Szukaj"))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 16).padding(.top, 6).padding(.bottom, 10)
            .background {
                ZStack {
                    LinearGradient(colors: motyw.naglowek, startPoint: .top, endPoint: .bottom)
                    if motyw.snieg || motyw.iskry { Platki(kolor: motyw.kolorPlatkow, iskry: motyw.iskry) }
                }
                .ignoresSafeArea(edges: motyw.pasek == nil ? .top : [])
            }
            .overlay(alignment: .bottom) {
                if motyw.paskiLinii.count >= 2 {
                    VStack(spacing: 0) { ForEach(Array(motyw.paskiLinii.enumerated()), id: \.offset) { $0.element.frame(height: 2) } }
                } else if let linia = motyw.linia {
                    linia.frame(height: 2)
                }
            }
            NaZywo()
            if let k = m.komunikat, k.pilny || !m.zamknieteKomunikaty.contains(k.id) { BanerKomunikatu(komunikat: k) }
            if let n = m.nowaWersja, !m.wymaganaAktualizacja, !m.zamknieteKomunikaty.contains(n.id) { BanerKomunikatu(komunikat: n) }
        }
        .sheet(isPresented: $szukaj) { SzukajWidok() }
    }
}

/// Komunikat redakcji z panelu: informacja, ostrzeżenie albo pilny. Stuknięcie otwiera link, krzyżyk zamyka (poza pilnym).
struct BanerKomunikatu: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.openURL) private var otworz
    @Environment(\.colorScheme) private var schemat
    let komunikat: Komunikat

    var body: some View {
        let (tlo, napis, ikona) = wyglad
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: ikona).font(.system(size: 15, weight: .bold)).padding(.top, 1)
            VStack(alignment: .leading, spacing: 2) {
                if !komunikat.tytul.isEmpty { Text(komunikat.tytul).czcionka(14, .bold) }
                if !komunikat.tekst.isEmpty { Text(komunikat.tekst).czcionka(13).lineLimit(3) }
                if komunikat.link != nil { Text(komunikat.etykieta).czcionka(13, .semibold).underline() }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !komunikat.pilny {
                Button { m.zamknieteKomunikaty.append(komunikat.id) } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).frame(width: 32, height: 32)
                }
                .accessibilityLabel(Napisy.t("app.close", "Zamknij komunikat"))
            }
        }
        .foregroundStyle(napis)
        .padding(.leading, 16).padding(.trailing, komunikat.pilny ? 16 : 6).padding(.vertical, 10)
        .background(tlo)
        .contentShape(Rectangle())
        .onTapGesture { if let l = komunikat.link { otworz(l) } }
    }

    private var wyglad: (Color, Color, String) {
        let ciemny = schemat == .dark
        switch komunikat.poziom {
        case "urgent": return (Color(hex: "#c8102e"), .white, "exclamationmark.octagon.fill")
        case "warning": return ciemny ? (Color(hex: "#3a2a0a"), Color(hex: "#f5c463"), "exclamationmark.triangle.fill")
                                      : (Color(hex: "#fdf1dc"), Color(hex: "#7a4a00"), "exclamationmark.triangle.fill")
        default: return ciemny ? (Color(hex: "#0f2140"), Color(hex: "#dbe4f3"), "info.circle.fill")
                               : (Color(hex: "#e8f0fe"), Color(hex: "#0a1f44"), "info.circle.fill")
        }
    }
}

/// Prośba o aktualizację, gdy panel ustawi wyższą minimalną wersję (config.minAppVersion.ios).
struct EkranAktualizacji: View {
    @Environment(\.openURL) private var otworz

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Image("LogoNoc").resizable().scaledToFit().frame(height: 56)
            Text(Napisy.t("app.forceUpdateTitle", "Zaktualizuj aplikację TVML")).czcionka(22, .bold)
            Text(Napisy.t("app.forceUpdateText", "Ta wersja ({v}) jest już nieaktualna. Pobierz nową z App Store, żeby dalej czytać wiadomości.")
                    .replacingOccurrences(of: "{v}", with: Wersja.biezaca))
                .czcionka(15).multilineTextAlignment(.center).opacity(0.85)
            Button { otworz(URL(string: "https://apps.apple.com/app/id1468277905")!) } label: {
                Text(Napisy.t("app.openAppStore", "Otwórz App Store")).czcionka(16, .bold)
                    .frame(maxWidth: 320).frame(height: 50)
                    .background(Color(hex: "#e29818"), in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Color(hex: "#1d1400"))
            }
            Spacer()
        }
        .padding(28)
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(hex: "#0a1f44").ignoresSafeArea())
    }
}

struct PasekZyczen: View {
    let pasek: Motyw.Pasek
    let motyw: Motyw

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Gwiazda().fill(motyw.zlotoPaska).frame(width: 12, height: 12)
                Text(pasek.etykieta).czcionka(11, .heavy).kerning(1.4)
            }
            .foregroundStyle(motyw.zlotoPaska)
            Text(pasek.tekst).czcionka(13).foregroundStyle(Color(hex: "#f6efe3"))
            if let doKiedy = pasek.odliczanieDo {
                TimelineView(.periodic(from: .now, by: 1)) { kontekst in
                    Text("● " + Napisy.t("app.countdownTo", "Odliczanie do {year}")
                        .replacingOccurrences(of: "{year}", with: String(Calendar.current.component(.year, from: doKiedy)))
                        .uppercased(with: Jezyk.locale()) + " • " + Odliczanie.tekst(od: kontekst.date, do: doKiedy))
                        .czcionka(11, .bold).kerning(1)
                        .foregroundStyle(motyw.zlotoPaska)
                }
            } else if let stopka = pasek.stopka {
                Text(stopka).czcionka(11, .bold).kerning(1).foregroundStyle(motyw.zlotoPaska)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 18).padding(.top, 6).padding(.bottom, 12)
    }
}

enum Odliczanie {
    static func tekst(od: Date, do koniec: Date) -> String {
        let s = max(0, Int(koniec.timeIntervalSince(od)))
        return String(format: "%dD %02d:%02d:%02d", s / 86400, (s % 86400) / 3600, (s % 3600) / 60, s % 60)
    }
}

/// Pasek „NA ŻYWO”: najnowsze wiadomości przesuwają się jak na stronie. Stuknięcie otwiera pierwszą z nich.
struct NaZywo: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.accessibilityReduceMotion) private var bezRuchu
    // wysokość paska rośnie razem z tekstem (40 pt przy domyślnej wielkości)
    @ScaledMetric(relativeTo: .footnote) private var wysokosc: CGFloat = 40
    @State private var szerokosc: CGFloat = 1

    var body: some View {
        HStack(spacing: 0) {
            HStack(spacing: 6) {
                // kropka i „NA ŻYWO” tylko przy transmisji (config.ticker.live), inaczej etykieta z panelu, np. „NAJNOWSZE”
                if m.pasekNaZywo { Circle().fill(.white).frame(width: 8, height: 8) }
                Text(m.pasekNaZywo ? Napisy.t("app.live", "NA ŻYWO") : m.etykietaPaska.uppercased(with: Jezyk.locale())).czcionka(11, .heavy).kerning(1)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 12).frame(maxHeight: .infinity)
            .background(m.motyw.naZywo)
            // tekst w nakładce, żeby jego szerokość nie wypychała etykiety
            Color.clear
                .frame(maxWidth: .infinity)
                .overlay(alignment: .leading) {
                    // „Ogranicz ruch”: najnowsza wiadomość stoi w miejscu zamiast przesuwać się
                    if bezRuchu {
                        Text(m.pasek.first?.tytul ?? "")
                            .czcionka(13, .semibold)
                            .foregroundStyle(Barwy.tekst)
                            .lineLimit(1)
                            .padding(.horizontal, 10)
                            .contentShape(Rectangle())
                            .onTapGesture { if let pierwszy = m.pasek.first { m.otworz(pierwszy) } }
                    } else {
                    TimelineView(.animation(minimumInterval: 1.0 / 30)) { kontekst in
                        let przesuniecie = CGFloat(kontekst.date.timeIntervalSinceReferenceDate * 40).truncatingRemainder(dividingBy: szerokosc + 60)
                        // każda wiadomość to osobny przycisk z własnym artykułem (wcześniej cały pasek otwierał jeden)
                        HStack(spacing: 0) {
                            ForEach(Array(m.pasek.enumerated()), id: \.offset) { i, a in
                                if i > 0 { Text("     •     ").czcionka(13, .semibold).foregroundStyle(Barwy.tekst) }
                                Button { m.otworz(a) } label: {
                                    Text(a.tytul).czcionka(13, .semibold).foregroundStyle(Barwy.tekst)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .fixedSize()
                        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { szerokosc = max(1, $0) }
                        .offset(x: 10 - przesuniecie)
                    }
                    }
                }
                .clipped()
        }
        .frame(height: wysokosc)
        .background(Barwy.karta)
        .overlay(alignment: .bottom) { Barwy.ramka.frame(height: 1) }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(m.pasekNaZywo ? Napisy.t("app.live", "Na żywo") : m.etykietaPaska)
    }
}

/// Śnieg albo iskry w banerze, delikatnie opadające.
struct Platki: View {
    let kolor: Color
    let iskry: Bool
    @Environment(\.accessibilityReduceMotion) private var bezRuchu

    var body: some View {
        // „Ogranicz ruch”: płatki stoją (jedna klatka zamiast animacji)
        TimelineView(.animation(minimumInterval: 1.0 / 20, paused: bezRuchu)) { kontekst in
            Canvas { ctx, size in
                let t = kontekst.date.timeIntervalSinceReferenceDate
                for i in 0..<18 {
                    let x0 = Double((i * 73) % 390) / 390 * size.width
                    let szybkosc = 8 + Double(i % 5) * 3
                    let y = (Double(i * 37 % 60) + t * szybkosc).truncatingRemainder(dividingBy: size.height + 10) - 5
                    let x = x0 + sin(t * 0.8 + Double(i)) * 6
                    let r = iskry ? 1.4 : (i % 3 == 0 ? 2.2 : 1.6)
                    ctx.opacity = iskry ? 0.5 + 0.5 * sin(t * 3 + Double(i)) : 0.85
                    ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: r * 2, height: r * 2)), with: .color(kolor))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

func ikonaPogody(_ ikona: String) -> String {
    let i = ikona.lowercased()
    if i.contains("storm") || i.contains("thunder") { return "cloud.bolt.rain.fill" }
    if i.contains("snow") { return "cloud.snow.fill" }
    if i.contains("rain") || i.contains("sleet") || i.contains("hail") || i.contains("shower") { return "cloud.rain.fill" }
    if i.contains("fog") { return "cloud.fog.fill" }
    if i.contains("wind") { return "wind" }
    if i.contains("night") { return "moon.stars.fill" }
    if i.contains("cloud-sun") || i.contains("partly") { return "cloud.sun.fill" }
    if i.contains("sun") || i.contains("clear") { return "sun.max.fill" }
    return "cloud.fill"
}

extension View {
    /// Na iPadzie treść w kolumnie do 760 pt, jak gazeta, zamiast rozciągniętego widoku z telefonu.
    /// Na iPhonie (węższym) nic się nie zmienia.
    func kolumna() -> some View {
        frame(maxWidth: 760).frame(maxWidth: .infinity).background(Barwy.tlo)
    }
}

/// Flaga języka narysowana w aplikacji (nie emoji), żeby wyglądała tak samo na każdym telefonie.
struct FlagaJezyka: View {
    let jezyk: String

    var body: some View {
        Canvas { ctx, s in
            let w = s.width, h = s.height
            switch jezyk {
            case "uk":
                ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h / 2)), with: .color(Color(hex: "#0057b7")))
                ctx.fill(Path(CGRect(x: 0, y: h / 2, width: w, height: h / 2)), with: .color(Color(hex: "#ffd700")))
            case "en":
                ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .color(Color(hex: "#012169")))
                var skosy = Path()
                skosy.move(to: .zero); skosy.addLine(to: CGPoint(x: w, y: h))
                skosy.move(to: CGPoint(x: w, y: 0)); skosy.addLine(to: CGPoint(x: 0, y: h))
                ctx.stroke(skosy, with: .color(.white), lineWidth: h * 0.2)
                ctx.stroke(skosy, with: .color(Color(hex: "#c8102e")), lineWidth: h * 0.07)
                var krzyz = Path()
                krzyz.move(to: CGPoint(x: w / 2, y: 0)); krzyz.addLine(to: CGPoint(x: w / 2, y: h))
                krzyz.move(to: CGPoint(x: 0, y: h / 2)); krzyz.addLine(to: CGPoint(x: w, y: h / 2))
                ctx.stroke(krzyz, with: .color(.white), lineWidth: h * 0.34)
                ctx.stroke(krzyz, with: .color(Color(hex: "#c8102e")), lineWidth: h * 0.2)
            default:
                ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h / 2)), with: .color(.white))
                ctx.fill(Path(CGRect(x: 0, y: h / 2, width: w, height: h / 2)), with: .color(Color(hex: "#dc143c")))
            }
        }
        .frame(width: 22, height: 15)
        .clipShape(RoundedRectangle(cornerRadius: 2.5))
        .overlay(RoundedRectangle(cornerRadius: 2.5).stroke(.black.opacity(0.2), lineWidth: 0.5))
        .accessibilityHidden(true)
    }

    @MainActor private static var obrazy: [String: UIImage] = [:]

    /// Ta sama flaga jako obrazek do menu (systemowe menu pokazuje tylko obrazy), w oryginalnych kolorach.
    @MainActor static func obraz(_ jezyk: String) -> UIImage {
        if let o = obrazy[jezyk] { return o }
        let r = ImageRenderer(content: FlagaJezyka(jezyk: jezyk))
        r.scale = 3
        let o = (r.uiImage ?? UIImage()).withRenderingMode(.alwaysOriginal)
        obrazy[jezyk] = o
        return o
    }
}
