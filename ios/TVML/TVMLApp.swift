import SwiftUI

/// TVML 2.0: serwis informacyjny tvml.pl na iPhone'a i iPada.
/// Baner (logo, pogoda, szukaj, pasek „NA ŻYWO”) jest nad wszystkimi ekranami, a jego wygląd zmienia się
/// razem z wersją ustawioną w panelu (zima, święta, Sylwester, 11 Listopada, żałoba).
@main
struct TVMLApp: App {
    @StateObject private var magazyn = Magazyn()
    @AppStorage("tvml-wyglad") private var wyglad = "auto"

    var body: some Scene {
        WindowGroup {
            Glowny()
                .environmentObject(magazyn)
                .preferredColorScheme(wyglad == "dzien" ? .light : (wyglad == "noc" ? .dark : nil))
        }
    }
}

struct Glowny: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.scenePhase) private var faza
    /// Do zrzutów ekranu: -ekran pogoda|artykul|wideo|wiecej, -motyw zima|wigilia|…
    private let ekranStartowy = UserDefaults.standard.string(forKey: "ekran")
    private let motywTestowy = Motyw.zArgumentu(UserDefaults.standard.string(forKey: "motyw"))

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
                    }
                    .tabItem { Label(z.tytul, systemImage: z.ikona) }
                    .tag(z.id)
                }
            }
            .tint(m.motyw.akcent)
        }
        .background(Barwy.tlo)
        .overlay { if m.wymaganaAktualizacja { EkranAktualizacji() } }
        .sheet(isPresented: $m.arkuszPogody) { NavigationStack { PogodaWidok().toolbar(.hidden, for: .navigationBar) } }
        .sheet(isPresented: $m.arkuszWideo) { NavigationStack { WideoWidok().toolbar(.hidden, for: .navigationBar) } }
        .task {
            await m.odswiez(wymus: true)
            if let motywTestowy { m.motyw = motywTestowy }
            switch ekranStartowy {
            case "pogoda": m.pokaz(.pogoda)
            case "wideo": m.pokaz(.wideo)
            case "wiecej": m.pokaz(.wiecej)
            case "artykul": if let pierwszy = m.najnowsze.first { m.otworz(pierwszy) }
            default: break
            }
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
    func sciezka(_ z: String) -> Binding<[Artykul]> {
        Binding(get: { self.sciezki[z] ?? [] }, set: { self.sciezki[z] = $0 })
    }

    func otworz(_ a: Artykul) {
        sciezki[zakladka, default: []].append(a)
    }
}

/// Baner nad każdym ekranem: pasek życzeń z panelu, logo z ozdobą, pogoda, szukaj i pasek „NA ŻYWO”.
struct Baner: View {
    @EnvironmentObject private var m: Magazyn
    @State private var szukaj = false

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
                .accessibilityLabel("Telewizja TVML, strona główna")
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
                .accessibilityLabel("Pogoda")
                Button { szukaj = true } label: {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 17, weight: .semibold))
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.12), in: Circle())
                }
                .accessibilityLabel("Szukaj")
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
                if !komunikat.tytul.isEmpty { Text(komunikat.tytul).font(.system(size: 14, weight: .bold)) }
                if !komunikat.tekst.isEmpty { Text(komunikat.tekst).font(.system(size: 13)).lineLimit(3) }
                if komunikat.link != nil { Text("Więcej").font(.system(size: 13, weight: .semibold)).underline() }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !komunikat.pilny {
                Button { m.zamknieteKomunikaty.append(komunikat.id) } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).frame(width: 32, height: 32)
                }
                .accessibilityLabel("Zamknij komunikat")
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
            Text("Zaktualizuj aplikację TVML").font(.system(size: 22, weight: .bold))
            Text("Ta wersja (\(Wersja.biezaca)) jest już nieaktualna. Pobierz nową z App Store, żeby dalej czytać wiadomości.")
                .font(.system(size: 15)).multilineTextAlignment(.center).opacity(0.85)
            Button { otworz(URL(string: "https://apps.apple.com/app/id1468277905")!) } label: {
                Text("Otwórz App Store").font(.system(size: 16, weight: .bold))
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
                Text(pasek.etykieta).font(.system(size: 11, weight: .heavy)).kerning(1.4)
            }
            .foregroundStyle(motyw.zlotoPaska)
            Text(pasek.tekst).font(.system(size: 13)).foregroundStyle(Color(hex: "#f6efe3"))
            if let doKiedy = pasek.odliczanieDo {
                TimelineView(.periodic(from: .now, by: 1)) { kontekst in
                    Text("● ODLICZANIE DO \(Calendar.current.component(.year, from: doKiedy).formatted(.number.grouping(.never))) • \(Odliczanie.tekst(od: kontekst.date, do: doKiedy))")
                        .font(.system(size: 11, weight: .bold)).kerning(1)
                        .foregroundStyle(motyw.zlotoPaska)
                }
            } else if let stopka = pasek.stopka {
                Text(stopka).font(.system(size: 11, weight: .bold)).kerning(1).foregroundStyle(motyw.zlotoPaska)
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
    @State private var szerokosc: CGFloat = 1

    var body: some View {
        let tekst = m.pasek.map(\.tytul).joined(separator: "     •     ")
        HStack(spacing: 0) {
            HStack(spacing: 6) {
                Circle().fill(.white).frame(width: 8, height: 8)
                Text("NA ŻYWO").font(.system(size: 11, weight: .heavy)).kerning(1)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 12).frame(maxHeight: .infinity)
            .background(m.motyw.naZywo)
            // tekst w nakładce, żeby jego szerokość nie wypychała etykiety „NA ŻYWO”
            Color.clear
                .frame(maxWidth: .infinity)
                .overlay(alignment: .leading) {
                    TimelineView(.animation(minimumInterval: 1.0 / 30)) { kontekst in
                        let przesuniecie = CGFloat(kontekst.date.timeIntervalSinceReferenceDate * 40).truncatingRemainder(dividingBy: szerokosc + 60)
                        Text(tekst)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Barwy.tekst)
                            .fixedSize()
                            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { szerokosc = max(1, $0) }
                            .offset(x: 10 - przesuniecie)
                    }
                }
                .clipped()
        }
        .frame(height: 40)
        .background(Barwy.karta)
        .overlay(alignment: .bottom) { Barwy.ramka.frame(height: 1) }
        .contentShape(Rectangle())
        .onTapGesture { if let pierwszy = m.pasek.first { m.otworz(pierwszy) } }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Na żywo: \(m.pasek.first?.tytul ?? "")")
        .accessibilityAddTraits(.isButton)
    }
}

/// Śnieg albo iskry w banerze, delikatnie opadające.
struct Platki: View {
    let kolor: Color
    let iskry: Bool

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 20)) { kontekst in
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
