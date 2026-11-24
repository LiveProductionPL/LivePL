import SwiftUI

/// Start: kategorie, karuzela, kafel pogody, aktualności, najczęściej czytane i wideo.
/// Kolejność bloków przyjdzie z panelu (layout.widgetsMobile) razem z nowym API.
struct StartWidok: View {
    @EnvironmentObject private var m: Magazyn
    @State private var kategoria: String?
    @State private var strona: StronaWWW?
    @State private var film: Film?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(m.kategorie, id: \.nazwa) { k in
                            let wybrana = kategoria == k.slug
                            Button {
                                kategoria = k.slug
                                if let slug = k.slug { Task { await m.kategoria(slug) } }
                            } label: {
                                Text(k.nazwa)
                                    .czcionka(13, .semibold)
                                    .padding(.horizontal, 14).frame(minHeight: 34)
                                    .foregroundStyle(wybrana ? m.motyw.napisNaAkcencie : Barwy.tekst)
                                    .background(wybrana ? m.motyw.akcent : Barwy.karta, in: Capsule())
                                    .overlay(Capsule().stroke(wybrana ? Color.clear : Barwy.ramka))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .scrollClipDisabled()

                if let blad = m.blad {
                    Text(blad).czcionka(14).foregroundStyle(Barwy.slaby)
                }
                if m.najnowsze.isEmpty && m.laduje {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 300)
                }
                if let slug = kategoria {
                    let lista = m.wKategorii[slug] ?? []
                    if let pierwszy = lista.first {
                        NavigationLink(value: pierwszy) { KartaArtykulu(artykul: pierwszy, wysokosc: 260) }.buttonStyle(.plain)
                    }
                    ForEach(lista.dropFirst()) { a in
                        NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                    }
                } else {
                    ForEach(m.bloki) { blok in
                        BlokWidok(blok: blok, strona: $strona, film: $film)
                    }
                }
            }
            .padding(16)
        }
        .refreshable { await m.odswiez(wymus: true) }
        .background(Barwy.tlo)
        .sheet(item: $strona) { s in Safari(url: URL(string: "https://tvml.pl/" + s.sciezka)!).ignoresSafeArea() }
        .sheet(item: $film) { f in
            if let adres = f.osadzenie { Odtwarzacz(adres: adres) }
        }
    }
}

/// Jeden blok ekranu Start, tak jak ustawia go redakcja w panelu (Page builder).
struct BlokWidok: View {
    @EnvironmentObject private var m: Magazyn
    let blok: BlokStartu
    @Binding var strona: StronaWWW?
    @Binding var film: Film?
    @State private var ogloszenie: Ogloszenie?

    var body: some View {
        switch blok.typ {
        case "hero":
            if !m.najnowsze.isEmpty {
                Karuzela(artykuly: Array(m.najnowsze.prefix(blok.limit > 0 ? blok.limit : 5)))
            }
        case "weather":
            KafelPogody()
        case "alert":
            // formularz Alertu TVML od razu (tvml.pl/#alert, PR #124 w Panel-TVML), a nie sama strona główna
            Button { strona = StronaWWW(sciezka: "#alert") } label: {
                Label(Napisy.t("app.reportIncident", "Zgłoś zdarzenie (Alert TVML)"), systemImage: "exclamationmark.triangle.fill")
                    .czcionka(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).frame(minHeight: 50)
                    .background(Color(hex: "#c8102e"), in: RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain)
        case "video":
            if blok.limit == 1, let f = m.filmy.first {
                tytul
                Button { film = f } label: { KafelFilmu(film: f, szerokosc: min(UIScreen.main.bounds.width, 760) - 32) }.buttonStyle(.plain)
            } else if !m.filmy.isEmpty {
                tytul
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(m.filmy.prefix(blok.limit > 0 ? blok.limit : 6)) { f in
                            Button { film = f } label: { KafelFilmu(film: f, szerokosc: 220) }.buttonStyle(.plain)
                        }
                    }
                }
                .scrollClipDisabled()
            }
        case "posts":
            let lista = listaWpisow
            if !lista.isEmpty {
                tytul
                ForEach(lista) { a in
                    NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                }
            }
        case "mostread":
            if !m.najczytniejsze.isEmpty {
                tytul
                let lista = Array(m.najczytniejsze.prefix(blok.limit > 0 ? blok.limit : 5))
                VStack(spacing: 0) {
                    ForEach(Array(lista.enumerated()), id: \.element.id) { i, a in
                        NavigationLink(value: a) {
                            HStack(alignment: .top, spacing: 12) {
                                Text("\(i + 1)").font(Czcionka.naglowek(22)).foregroundStyle(m.motyw.akcent).frame(width: 30)
                                Text(a.tytul).czcionka(15, .semibold).foregroundStyle(Barwy.tekst).frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(.vertical, 10)
                            .overlay(alignment: .bottom) { if i < lista.count - 1 { Barwy.ramka.frame(height: 1) } }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
            }
        case "gallery":
            let lista = m.wKategorii["_fotorelacje"] ?? []
            Group {
                if !lista.isEmpty {
                    tytul
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(lista) { a in
                                NavigationLink(value: a) { KartaArtykulu(artykul: a, wysokosc: 200).frame(width: 260) }.buttonStyle(.plain)
                            }
                        }
                    }
                    .scrollClipDisabled()
                }
            }
            .task { if m.wKategorii["_fotorelacje"] == nil { await m.fotorelacje(limit: blok.limit) } }
        case "events":
            // przy bloku poll kalendarz jest w miejscu nieaktywnej sondy, więc tu go nie powtarzamy
            if !m.wydarzenia.isEmpty && !m.bloki.contains(where: { $0.typ == "poll" }) {
                if !blok.ukryjTytul { Naglowek(tytul: blok.tytul.isEmpty ? Napisy.t("app.events", "Kalendarz wydarzeń") : blok.tytul) }
                let lista = Array(m.wydarzenia.prefix(blok.limit > 0 ? blok.limit : 5))
                VStack(spacing: 0) {
                    ForEach(Array(lista.enumerated()), id: \.element.id) { i, w in
                        Button { otworz(w) } label: { WierszWydarzenia(wydarzenie: w) }.buttonStyle(.plain)
                        if i < lista.count - 1 { Barwy.ramka.frame(height: 1) }
                    }
                }
                .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                Button { strona = StronaWWW(sciezka: "wydarzenia") } label: {
                    Text(Napisy.t("app.allEvents", "Wszystkie wydarzenia na tvml.pl")).czcionka(14, .semibold)
                }
            }
        case "poll":
            BlokSpotlight(blok: blok, strona: $strona)
        case "classifieds":
            if !m.ogloszenia.isEmpty {
                Naglowek(tytul: blok.tytul.isEmpty ? Napisy.t("app.classifieds", "Ogłoszenia drobne") : blok.tytul)
                VStack(spacing: 0) {
                    ForEach(Array(m.ogloszenia.prefix(blok.limit > 0 ? blok.limit : 4).enumerated()), id: \.element.id) { i, o in
                        Button { ogloszenie = o } label: { WierszOgloszenia(ogloszenie: o) }.buttonStyle(.plain)
                        if i < min(m.ogloszenia.count, blok.limit > 0 ? blok.limit : 4) - 1 { Barwy.ramka.frame(height: 1) }
                    }
                }
                .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                .sheet(item: $ogloszenie) { OgloszenieWidok(ogloszenie: $0) }
                Button { strona = StronaWWW(sciezka: "lista/ogloszenia") } label: {
                    Text(Napisy.t("app.classifiedsAll", "Wszystkie ogłoszenia na tvml.pl")).czcionka(14, .semibold)
                }
            }
        case "counties":
            if !m.gminy.isEmpty {
                tytul
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(m.gminy) { g in
                            NavigationLink(value: g) {
                                HStack(spacing: 8) {
                                    AsyncImage(url: g.herb) { $0.resizable().scaledToFit() } placeholder: { Color.clear }
                                        .frame(width: 22, height: 26)
                                    Text(g.nazwa).czcionka(14, .semibold).foregroundStyle(Barwy.tekst)
                                }
                                .padding(.horizontal, 12).frame(minHeight: 44)
                                .background(Barwy.karta, in: Capsule())
                                .overlay(Capsule().stroke(Barwy.ramka))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .scrollClipDisabled()
            }
        case "social":
            tytul
            HStack(spacing: 10) {
                spolecznosc("Facebook", "https://www.facebook.com/TVMLpl")
                spolecznosc("YouTube", "https://www.youtube.com/@TVMLlive")
                spolecznosc("Instagram", "https://www.instagram.com/tvmlpl/")
                spolecznosc("TikTok", "https://www.tiktok.com/@tvml.pl")
            }
        default:
            EmptyView()
        }
    }

    @ViewBuilder private var tytul: some View {
        if !blok.ukryjTytul && !blok.tytul.isEmpty { Naglowek(tytul: blok.tytul) }
    }

    /// Wpisy bloku: z kategorii albo flagi z panelu, albo kolejne najnowsze po karuzeli.
    private var listaWpisow: [Artykul] {
        let limit = blok.limit > 0 ? blok.limit : 4
        if let k = blok.klucz { return Array((m.wKategorii[k] ?? []).prefix(limit)) }
        let karuzela = m.bloki.first { $0.typ == "hero" }.map { $0.limit > 0 ? $0.limit : 5 } ?? 0
        return Array(m.najnowsze.dropFirst(karuzela).prefix(limit))
    }

    /// Wydarzenie z artykułem otwiera artykuł, a bez niego ekran wydarzenia w aplikacji.
    private func otworz(_ w: Wydarzenie) {
        if let id = w.artykulId {
            Task { @MainActor in
                if let a = await m.artykul(id) { m.otworz(a) } else { m.otworz(EkranWiecej.wydarzenie(w)) }
            }
        } else {
            m.otworz(EkranWiecej.wydarzenie(w))
        }
    }

    private func spolecznosc(_ nazwa: String, _ adres: String) -> some View {
        Link(destination: URL(string: adres)!) {
            Text(nazwa).czcionka(13, .semibold).foregroundStyle(Barwy.tekst)
                .frame(maxWidth: .infinity).frame(minHeight: 40)
                .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Barwy.ramka))
        }
    }
}

/// Wiadomości z jednej gminy powiatu.
struct ListaGminy: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    let gmina: Gmina
    @State private var lista: [Artykul] = []
    @State private var laduje = true

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Button { wroc() } label: {
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text(Napisy.t("app.back", "Wróć")) }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                HStack(spacing: 10) {
                    AsyncImage(url: gmina.herb) { $0.resizable().scaledToFit() } placeholder: { Color.clear }.frame(width: 30, height: 36)
                    Text(gmina.nazwa).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                }
                if laduje { ProgressView().frame(maxWidth: .infinity, minHeight: 200) }
                else if lista.isEmpty { Text(Napisy.t("app.noNewsCommune", "Brak wiadomości z tej gminy.")).foregroundStyle(Barwy.slaby) }
                ForEach(lista) { a in
                    NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            lista = await m.gmina(gmina)
            laduje = false
        }
    }
}

/// Lista jednej kategorii (zakładka Sport, a później także subportale z panelu).
struct ListaKategorii: View {
    @EnvironmentObject private var m: Magazyn
    let slug: String
    let tytul: String

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Text(tytul).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                let lista = m.wKategorii[slug] ?? []
                if lista.isEmpty { ProgressView().frame(maxWidth: .infinity, minHeight: 200) }
                if let pierwszy = lista.first {
                    NavigationLink(value: pierwszy) { KartaArtykulu(artykul: pierwszy, wysokosc: 240) }.buttonStyle(.plain)
                }
                ForEach(lista.dropFirst()) { a in
                    NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .refreshable { await m.kategoria(slug) }
        .task { if m.wKategorii[slug] == nil { await m.kategoria(slug) } }
        .background(Barwy.tlo)
    }
}

struct Naglowek: View {
    let tytul: String
    var body: some View {
        Text(tytul)
            .font(Czcionka.naglowek(21))
            .foregroundStyle(Barwy.tekst)
            .padding(.top, 4)
            .accessibilityAddTraits(.isHeader)
    }
}

struct Karuzela: View {
    let artykuly: [Artykul]
    @State private var wybrany = 0
    @EnvironmentObject private var m: Magazyn

    var body: some View {
        VStack(spacing: 8) {
            TabView(selection: $wybrany) {
                ForEach(Array(artykuly.enumerated()), id: \.element.id) { i, a in
                    NavigationLink(value: a) { KartaArtykulu(artykul: a, wysokosc: 300) }
                        .buttonStyle(.plain)
                        .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 300)
            HStack(spacing: 6) {
                ForEach(0..<artykuly.count, id: \.self) { i in
                    Capsule().fill(i == wybrany ? Barwy.zloto : Barwy.ramka).frame(width: i == wybrany ? 18 : 6, height: 6)
                }
            }
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)
        }
    }
}

/// Duża karta: zdjęcie, kategoria, tytuł, autor.
struct KartaArtykulu: View {
    @EnvironmentObject private var m: Magazyn
    let artykul: Artykul
    let wysokosc: CGFloat

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            // zdjęcie w nakładce: przy scaledToFill jego szerokość nie może poszerzać karty (w karuzeli
            // na iPhonie 16/17 Pro karta wychodziła poza ekran i ucinała tytuł z lewej)
            Color.clear
                .frame(height: wysokosc).frame(maxWidth: .infinity)
                .overlay { Obraz(url: artykul.miniatura(900)) }
                .clipped()
            LinearGradient(colors: [.clear, Color(red: 0.02, green: 0.05, blue: 0.11).opacity(0.92)], startPoint: .init(x: 0.5, y: 0.25), endPoint: .bottom)
            VStack(alignment: .leading, spacing: 8) {
                EtykietaKategorii(artykul: artykul)
                Text(artykul.tytul)
                    .font(Czcionka.naglowek(24))
                    .foregroundStyle(.white)
                    .lineLimit(4)
                Text([artykul.autor, artykul.kiedy].filter { !$0.isEmpty }.joined(separator: " · "))
                    .czcionka(12).foregroundStyle(Color(hex: "#d7deea"))
            }
            .padding(18)
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .contentShape(RoundedRectangle(cornerRadius: 18))
    }
}

/// Kategoria artykułu, a przed nią „PILNE” i po niej „SPONSOROWANE”, gdy redakcja tak oznaczyła artykuł (API 1.2).
struct EtykietaKategorii: View {
    let artykul: Artykul
    var body: some View {
        HStack(spacing: 6) {
            if artykul.pilne { znaczek("PILNE", tlo: Color(hex: "#c8102e"), napis: .white) }
            if !artykul.kategoria.isEmpty {
                znaczek(artykul.kategoria.uppercased(), tlo: artykul.kolorKategorii, napis: Kontrast.napis(na: artykul.kolor))
            }
            if artykul.sponsorowany { znaczek("SPONSOROWANE", tlo: Color(hex: "#5b6475"), napis: .white) }
        }
    }

    private func znaczek(_ tekst: String, tlo: Color, napis: Color) -> some View {
        Text(tekst)
            .czcionka(11, .heavy).kerning(1)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .foregroundStyle(napis)
            .background(tlo, in: RoundedRectangle(cornerRadius: 8))
    }
}

/// Kolor napisu na kolorze kategorii: biały albo ciemny, zależnie od jasności tła (czytelność jak w WCAG).
enum Kontrast {
    static func napis(na hex: String) -> Color {
        var t = hex
        if t.hasPrefix("#") { t.removeFirst() }
        guard t.count == 6, let v = UInt32(t, radix: 16) else { return Color(hex: "#1d1400") }
        func kanal(_ c: UInt32) -> Double {
            let x = Double(c) / 255
            return x <= 0.03928 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4)
        }
        let l = 0.2126 * kanal((v >> 16) & 0xff) + 0.7152 * kanal((v >> 8) & 0xff) + 0.0722 * kanal(v & 0xff)
        return (1.05 / (l + 0.05)) >= 4.5 ? .white : Color(hex: "#1d1400")
    }

    /// To samo jako liczba (do kolorów zmieniających się z trybem dzień/noc).
    static func napisHex(na hex: String) -> UInt32 {
        var t = hex
        if t.hasPrefix("#") { t.removeFirst() }
        guard t.count == 6, let v = UInt32(t, radix: 16) else { return 0x1d1400 }
        func kanal(_ c: UInt32) -> Double {
            let x = Double(c) / 255
            return x <= 0.03928 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4)
        }
        let l = 0.2126 * kanal((v >> 16) & 0xff) + 0.7152 * kanal((v >> 8) & 0xff) + 0.0722 * kanal(v & 0xff)
        return (1.05 / (l + 0.05)) >= 4.5 ? 0xffffff : 0x1d1400
    }
}

struct WierszArtykulu: View {
    @EnvironmentObject private var m: Magazyn
    let artykul: Artykul

    var body: some View {
        HStack(spacing: 12) {
            Obraz(url: artykul.miniatura(300))
                .frame(width: 92, height: 70).clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Circle().fill(artykul.kolorKategorii).frame(width: 8, height: 8)
                    Text(artykul.kategoria.uppercased()).czcionka(11, .bold).kerning(0.6).foregroundStyle(Barwy.slaby)
                }
                Text(artykul.tytul).czcionka(15, .bold).foregroundStyle(Barwy.tekst).lineLimit(3)
                Text(artykul.kiedy).czcionka(12).foregroundStyle(Barwy.slaby)
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Barwy.ramka))
    }
}

/// Obrazek z sieci; w trybie żałobnym w odcieniach szarości.
struct WierszOgloszenia: View {
    let ogloszenie: Ogloszenie
    var body: some View {
        HStack(spacing: 12) {
            if let z = ogloszenie.zdjecia.first {
                Obraz(url: z).frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 10))
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(ogloszenie.tytul).czcionka(15, .semibold).foregroundStyle(Barwy.tekst).lineLimit(2)
                Text([ogloszenie.kategoria, ogloszenie.cena].filter { !$0.isEmpty }.joined(separator: " · "))
                    .czcionka(13).foregroundStyle(Barwy.slaby)
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Barwy.slaby)
        }
        .padding(12)
        .contentShape(Rectangle())
    }
}

/// Szczegóły ogłoszenia: zdjęcia, cena, treść i kontakt (do zaznaczenia i skopiowania).
struct OgloszenieWidok: View {
    @Environment(\.dismiss) private var zamknij
    let ogloszenie: Ogloszenie
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if !ogloszenie.zdjecia.isEmpty {
                        TabView {
                            ForEach(ogloszenie.zdjecia, id: \.self) { Obraz(url: $0).clipped() }
                        }
                        .tabViewStyle(.page)
                        .frame(height: 260)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    if !ogloszenie.kategoria.isEmpty {
                        Text(ogloszenie.kategoria.uppercased()).czcionka(11, .heavy).kerning(1).foregroundStyle(Barwy.slaby)
                    }
                    Text(ogloszenie.tytul).font(Czcionka.naglowek(22, 700)).foregroundStyle(Barwy.tekst)
                    if !ogloszenie.cena.isEmpty { Text(ogloszenie.cena).czcionka(18, .bold).foregroundStyle(Barwy.tekst) }
                    if !ogloszenie.tekst.isEmpty { Text(ogloszenie.tekst).czcionka(16).foregroundStyle(Barwy.tekst) }
                    if !ogloszenie.kontakt.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(Napisy.t("app.classifiedContact", "Kontakt")).czcionka(13, .bold).foregroundStyle(Barwy.slaby)
                            Text(ogloszenie.kontakt).czcionka(16).foregroundStyle(Barwy.tekst).textSelection(.enabled)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(16)
            }
            .background(Barwy.tlo)
            .navigationTitle(Napisy.t("app.classified", "Ogłoszenie"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(Napisy.t("app.done", "Gotowe")) { zamknij() } } }
        }
    }
}

struct Obraz: View {
    @EnvironmentObject private var m: Magazyn
    let url: URL?

    var body: some View {
        AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.2))) { faza in
            switch faza {
            case .success(let obraz): obraz.resizable().scaledToFill()
            default: Rectangle().fill(Barwy.ramka)
            }
        }
        .grayscale(m.motyw.szarosc ? 1 : 0)
        .accessibilityHidden(true)
    }
}

/// Kafel pogody na Start: ta sama scena co na ekranie Pogoda, w mniejszym wydaniu.
/// Na grafice napis, temperatura i prognoza (drzewa w pasie pod tekstem); pod grafiką belka z datą
/// (i świętem) oraz imieninami, jak pasek daty na tvml.pl.
struct KafelPogody: View {
    @EnvironmentObject private var m: Magazyn

    /// „Poniedziałek, 23 listopada”; data z config.today (po uk/en przetłumaczona), bez roku, z wielkiej litery.
    /// Święto idzie osobną linią (PaletaBelki.linie), żeby nie ucinało się w pół słowa.
    private var data: String {
        var data = m.dzisiaj?.etykieta ?? ""
        // rok jest zbędny na karcie: „… 2026”, „… 2026 р.”
        data = data.replacingOccurrences(of: #"\s*\d{4}(\s*р\.)?\s*$"#, with: "", options: .regularExpression)
        if data.isEmpty {
            data = Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Jezyk.locale()))
        }
        return data.prefix(1).uppercased(with: Jezyk.locale()) + data.dropFirst()
    }

    /// „Imieniny: Michała, Grzegorza”; imiona zawsze po polsku.
    private var imieniny: String? {
        guard let imiona = m.dzisiaj?.imieniny, !imiona.isEmpty else { return nil }
        return Napisy.t("app.nameDays", "Imieniny") + ": " + imiona.joined(separator: ", ")
    }

    @Environment(\.accessibilityReduceMotion) private var bezRuchu

    var body: some View {
        let scena = m.aktywnaScena
        let linie = PaletaBelki.linie(scena, swieto: m.dzisiaj?.swieto, imieniny: imieniny)
        Button { m.pokaz(.pogoda) } label: {
            KartaPogody(temperatura: m.pogoda.map { Pogoda.stopnie($0.temperatura) + "C" } ?? "–",
                        opis: m.pogoda?.opis ?? "",
                        prognoza: (m.pogoda?.dni ?? []).dropFirst().prefix(3).map { (nazwa: $0.nazwa, max: Pogoda.stopnie($0.max)) },
                        data: data, wyroznienie: linie.wyroznienie, imieniny: linie.imieniny,
                        stan: StanPogody.z(ikony: m.pogoda?.ikona ?? "cloud"),
                        pora: PoraDnia.teraz(dzien: m.pogoda?.dzien ?? true),
                        scena: m.motyw.scena(miesiac: Calendar.current.component(.month, from: .now)),
                        efekt: m.pogoda?.efekt, okolicznosciowy: m.motyw.rodzaj != .zwykly,
                        scenaKarty: scena,
                        warunki: WarunkiSceny.z(stan: m.pogoda?.ikona ?? "", efekt: m.pogoda?.efekt, dzien: m.pogoda?.dzien ?? true,
                                                ustawienia: m.ustawieniaScen[m.pogoda?.ikona ?? ""], karta: false, bezRuchu: bezRuchu))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(opisDostepnosci(linie))
    }

    /// Dla VoiceOver: pogoda, data, święto albo motyw, imieniny (w częściach, bo długie złożenie zatyka kompilator).
    private func opisDostepnosci(_ linie: (wyroznienie: String?, imieniny: String?)) -> String {
        let tytul: String = Napisy.t("app.weatherLeszno", "Pogoda w Lesznie")
        let temp: String = m.pogoda.map { Pogoda.stopnie($0.temperatura) } ?? ""
        let opis: String = m.pogoda?.opis ?? ""
        var czesci: [String] = [tytul + ": " + temp + ", " + opis, data]
        if let w = linie.wyroznienie { czesci.append(w) }
        if let i = linie.imieniny { czesci.append(i) }
        return czesci.joined(separator: ". ")
    }
}

/// Sama karta pogody ze Startu (bez danych z Magazynu), żeby dało się ją narysować w każdej scenie
/// do sprawdzenia czytelności (GaleriaPogody, -galeria-pogody YES).
struct KartaPogody: View {
    enum Warstwa { case calosc, scena, tekst }

    let temperatura: String
    let opis: String
    let prognoza: [(nazwa: String, max: String)]
    let data: String
    /// święto z kalendarza albo przedrostek motywu („Zima w Lesznie”, „Wieczór andrzejkowy”)
    var wyroznienie: String? = nil
    /// „Imieniny: …” (albo „Jutro imieniny: Andrzeja”)
    var imieniny: String? = nil
    let stan: StanPogody
    let pora: PoraDnia
    let scena: Motyw.Scena
    var efekt: EfektPogody? = nil
    var okolicznosciowy = false
    /// scena ze strony wybrana przez serwer (theme.weatherScene, harmonogram) albo obraz SVG motywu z panelu;
    /// nil = dawna panorama według pory roku
    var scenaKarty: WyborSceny.Scena? = nil
    /// isNight, burza, deszcz, mgła i ustawienia sceny z panelu (jak na stronie)
    var warunki: WarunkiSceny? = nil
    /// stała chwila animacji (galeria); nil = scena żyje
    var czas: Double? = nil
    /// galeria: sama scena (z przyciemnieniem, tekst niewidoczny, ale zajmuje miejsce) albo sam tekst na przezroczystym tle
    var warstwa: Warstwa = .calosc
    @Environment(\.dynamicTypeSize) private var rozmiarTekstu

    private var noc: Bool {
        if let warunki { return warunki.noc }
        if !okolicznosciowy, let e = efekt { return e.faza == "night" }
        return pora == .noc
    }
    private var paleta: PaletaBelki {
        let p = PaletaBelki.dla(scenaKarty ?? WyborSceny.Scena(), noc: noc, rok: Calendar.current.component(.year, from: Date()))
        guard let s = scenaKarty, s.panorama, s.svg == nil else { return p }
        // panorama Leszna: belka w kolorze jej ziemi według pory roku (zimą i w Święta śnieg, wtedy ciemny tekst)
        let jasna = ScenaPogody.zimowa(scena)
        return PaletaBelki(tlo: ScenaPogody.kolorZiemi(scena: scena, noc: noc), akcent: p.akcent,
                           data: jasna ? Color(hex: "#0f172a") : Color.white,
                           drugaLinia: jasna ? Color(hex: "#334155") : Color(hex: "#e2e8f0"),
                           medal: p.medal, obwodka: p.obwodka, ikona: p.ikona)
    }

    var body: some View {
        VStack(spacing: 0) {
            grafika
            belka
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    /// Grafika w proporcjach zbliżonych do karty ze strony (scena rysowana jak na stronie, dół sceny pod tekstem);
    /// przy dużym tekście karta rośnie razem z nim.
    private var grafika: some View {
        ZStack(alignment: .topLeading) {
            Color.clear.aspectRatio(1.85, contentMode: .fit)
            tekst
        }
        .frame(maxWidth: .infinity)
        .background {
            if warstwa != .tekst {
                tlo
                    // przyciemnienie pod tekstem: mocniej u góry (napis, chmury), słabnie ku scenie na dole,
                    // i lekko z lewej, pod temperaturą i opisem (np. ośnieżone szczyty alpine-peaks)
                    .overlay {
                        LinearGradient(stops: [.init(color: .black.opacity(0.34), location: 0),
                                               .init(color: .black.opacity(0.2), location: 0.5),
                                               .init(color: .black.opacity(0), location: 0.78)],
                                       startPoint: .top, endPoint: .bottom)
                    }
                    .overlay {
                        LinearGradient(stops: [.init(color: .black.opacity(0.24), location: 0),
                                               .init(color: .black.opacity(0.1), location: 0.38),
                                               .init(color: .black.opacity(0), location: 0.6)],
                                       startPoint: .leading, endPoint: .trailing)
                    }
            }
        }
    }

    @ViewBuilder private var tlo: some View {
        if let s = scenaKarty, let svg = s.svg {
            TloSVG(adres: noc ? (s.svgNoc ?? svg) : svg, przyciemnij: noc && s.svgNoc == nil)
        } else {
            ScenaPogody(stan: stan, pora: pora, scena: scena, efekt: efekt,
                        okolicznosciowy: okolicznosciowy, kafel: true, czas: czas,
                        scenaStrony: scenaKarty?.doRysowania, warunki: warunki)
        }
    }

    private var tekst: some View {
        // galeria: w warstwie sceny tekst niewidoczny, ale pigułki prognozy zostają (są częścią tła pod tekstem)
        let widac: Double = warstwa == .scena ? 0 : 1
        let cien = Color.black.opacity(warstwa == .calosc ? 0.6 : 0)
        return VStack(alignment: .leading, spacing: 0) {
            Text(Napisy.t("app.weatherLeszno", "Pogoda w Lesznie").uppercased(with: Jezyk.locale())).czcionka(11, .bold).kerning(1)
                .shadow(color: cien, radius: 4).opacity(widac)
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(temperatura).czcionka(30, .heavy)
                    Text(opis).czcionka(13).lineLimit(1)
                }
                .shadow(color: cien, radius: 4).opacity(widac)
                Spacer(minLength: 8)
                // przy bardzo dużym tekście prognoza na 3 dni nie mieści się obok temperatury (jest na ekranie Pogoda)
                if !rozmiarTekstu.isAccessibilitySize {
                    HStack(spacing: 6) {
                        ForEach(Array(prognoza.enumerated()), id: \.offset) { _, d in
                            VStack(spacing: 2) {
                                Text(d.nazwa).czcionka(12).opacity(0.9)
                                Text(d.max).czcionka(12, .bold)
                            }
                            .shadow(color: cien, radius: 3).opacity(widac)
                            // pigułka jak na karcie ze strony: prognoza czytelna także na wieży Ratusza i śniegu
                            .padding(.horizontal, 6).padding(.vertical, 5)
                            .background(Color.black.opacity(0.26), in: RoundedRectangle(cornerRadius: 9))
                        }
                    }
                }
            }
            .padding(.top, 4)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 12)
    }

    /// Belka pod grafiką w kolorze podłoża sceny: medal, data, święto albo przedrostek motywu, imieniny.
    /// Dłuższe linie przechodzą do drugiego wiersza zamiast ucinać się w pół słowa.
    private var belka: some View {
        let p = paleta
        return HStack(spacing: 10) {
            MedalBelki(paleta: p)
            VStack(alignment: .leading, spacing: 1) {
                Text(data).czcionka(13, .bold).foregroundStyle(p.data)
                    .lineLimit(1).minimumScaleFactor(0.8)
                if let wyroznienie {
                    Text(wyroznienie).czcionka(12, .bold).foregroundStyle(p.data)
                        .lineLimit(2).fixedSize(horizontal: false, vertical: true)
                }
                if let imieniny {
                    Text(imieniny).czcionka(12, .semibold).foregroundStyle(p.drugaLinia)
                        .lineLimit(2).fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .opacity(warstwa == .scena ? 0 : 1)
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            if warstwa != .tekst {
                p.tlo.overlay(alignment: .top) { if let a = p.akcent { a.frame(height: 3) } }
            }
        }
    }
}

/// Wiersz kalendarza wydarzeń: kafelek z dniem i miesiącem, tytuł, godzina i miejsce.
struct WierszWydarzenia: View {
    @EnvironmentObject private var m: Magazyn
    let wydarzenie: Wydarzenie
    /// Dzień wybrany w kalendarzu („2026-11-23”); nil = dziś. Wystawa, która trwa od 20 listopada, w dniu 23 listopada
    /// ma na kafelku 23 LIS, a nie dzień rozpoczęcia (Karol, 23.11: „co tu robi ta data?”).
    var naDzien: String? = nil

    private var dzienOdniesienia: Date { naDzien.flatMap { Wydarzenie.fISO.date(from: $0) } ?? Date() }

    /// Dzień i miesiąc na kafelku: wybrany dzień, gdy wielodniowe już trwa, inaczej dzień rozpoczęcia.
    private var kafelek: (dzien: String, miesiac: String) {
        guard wydarzenie.trwaW(dzienOdniesienia) else { return (wydarzenie.dzien, wydarzenie.miesiac) }
        let d = dzienOdniesienia
        return (Wydarzenie.fDzien.string(from: d), Wydarzenie.fMiesiac.string(from: d).uppercased())
    }

    /// Linia pod tytułem: przy wielodniowym zakres „20 lis – 5 gru”, inaczej dzień tygodnia i godzina; potem miejsce.
    private var podpis: String {
        var czesci: [String] = []
        if wydarzenie.wielodniowe {
            czesci.append(wydarzenie.zakres)
        } else {
            let kiedy: String = wydarzenie.obraz == nil
                ? wydarzenie.dzienTygodnia
                : wydarzenie.dzienTygodnia + " " + wydarzenie.dzien + " " + wydarzenie.miesiac.lowercased()
            czesci.append(kiedy)
            czesci.append(wydarzenie.godzina)
        }
        czesci.append(wydarzenie.miejsce)
        return czesci.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            if let u = wydarzenie.obraz {
                Color.clear.frame(width: 72, height: 56)
                    .overlay { AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Barwy.ramka } }
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .accessibilityHidden(true)
            } else {
                let k = kafelek
                VStack(spacing: 0) {
                    Text(k.dzien).font(Czcionka.naglowek(22)).foregroundStyle(m.motyw.napisNaAkcencie)
                    Text(k.miesiac).czcionka(11, .bold).foregroundStyle(m.motyw.napisNaAkcencie)
                }
                .frame(width: 52)
                .frame(minHeight: 56)
                .background(m.motyw.akcent, in: RoundedRectangle(cornerRadius: 10))
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(wydarzenie.tytul).czcionka(15, .semibold).foregroundStyle(Barwy.tekst).lineLimit(3)
                Text(podpis)
                    .czcionka(12).foregroundStyle(Barwy.slaby).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Barwy.slaby)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

/// Blok „poll” na Start (Karol, 22.11.2026): sonda tylko wtedy, gdy jest aktywna, a w pozostałym czasie
/// w tym miejscu kalendarz wydarzeń. Decyduje serwer (/api/v1/spotlight, pole type).
struct BlokSpotlight: View {
    @EnvironmentObject private var m: Magazyn
    let blok: BlokStartu
    @Binding var strona: StronaWWW?

    var body: some View {
        switch m.spotlight[blok.id] {
        case .sonda(let s)?:
            WidokSondy(sonda: s, blok: blok)
        case .kalendarz(let k)?:
            KalendarzWidok(kalendarz: k, tytul: blok.ukryjTytul ? nil : Napisy.t("app.events", "Kalendarz wydarzeń"), strona: $strona)
        case nil:
            EmptyView()
        }
    }
}

/// Sonda: najpierw tylko odpowiedzi do wyboru i „Zagłosuj”, wyniki dopiero po głosie.
/// Głos zapamiętuje tylko telefon (id sondy → odpowiedź), bez żadnego identyfikatora.
struct WidokSondy: View {
    @EnvironmentObject private var m: Magazyn
    let sonda: Sonda
    let blok: BlokStartu
    @State private var wybor: String?
    @State private var wysyla = false
    @State private var komunikat: String?

    var body: some View {
        if !blok.ukryjTytul { Naglowek(tytul: blok.tytul.isEmpty ? Napisy.t("app.poll", "Sonda") : blok.tytul) }
        VStack(alignment: .leading, spacing: 12) {
            Text(sonda.pytanie).font(Czcionka.naglowek(19)).foregroundStyle(Barwy.tekst)
            if let moj = m.glosy[sonda.id] {
                wyniki(moj)
            } else if !sonda.otwarta {
                wyniki("")
            } else {
                VStack(spacing: 8) {
                    ForEach(sonda.opcje) { o in
                        Button { wybor = o.klucz } label: {
                            HStack(spacing: 10) {
                                Image(systemName: wybor == o.klucz ? "largecircle.fill.circle" : "circle")
                                    .foregroundStyle(wybor == o.klucz ? m.motyw.akcent : Barwy.slaby)
                                Text(o.etykieta).czcionka(15).foregroundStyle(Barwy.tekst)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 44)
                            .background(Barwy.tlo, in: RoundedRectangle(cornerRadius: 10))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(wybor == o.klucz ? .isSelected : [])
                    }
                }
                .accessibilityElement(children: .contain)
                .accessibilityLabel(sonda.pytanie)
                Button { Task { await glosuj() } } label: {
                    Text(wysyla ? Napisy.t("app.sending", "Wysyłam…") : Napisy.t("app.vote", "Zagłosuj")).czcionka(15, .bold)
                        .foregroundStyle(m.motyw.napisNaAkcencie)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 46)
                        .background(m.motyw.akcent.opacity(wybor == nil ? 0.4 : 1), in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(wybor == nil || wysyla)
            }
            if let d = sonda.doKiedy {
                Text(Napisy.t("app.pollUntil", "Sonda trwa do {d}").replacingOccurrences(of: "{d}", with: Sonda.doKiedyTekst(d))).czcionka(12).foregroundStyle(Barwy.slaby)
            }
            if let k = komunikat { Text(k).czcionka(13).foregroundStyle(Barwy.slaby) }
        }
        .padding(16)
        .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
    }

    private func wyniki(_ moj: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(sonda.opcje) { o in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(o.etykieta).czcionka(14, o.klucz == moj ? .bold : .regular).foregroundStyle(Barwy.tekst)
                        if o.klucz == moj { Text(Napisy.t("app.yourVote", "Twój głos")).czcionka(11, .bold).foregroundStyle(m.motyw.akcent) }
                        Spacer(minLength: 8)
                        Text("\(o.procent)%").czcionka(14, .semibold).foregroundStyle(Barwy.tekst)
                    }
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Barwy.ramka)
                            Capsule().fill(o.klucz == moj ? m.motyw.akcent : Barwy.slaby.opacity(0.6))
                                .frame(width: g.size.width * CGFloat(min(max(o.procent, 0), 100)) / 100)
                        }
                    }
                    .frame(height: 8)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(o.etykieta), \(o.procent)\(Jezyk.polski ? " procent" : "%"), \(Sonda.glosyTekst(o.glosy))\(o.klucz == moj ? ", " + Napisy.t("app.yourVote", "twój głos").lowercased() : "")")
            }
            Text(Napisy.t("app.votesCast", "Oddano {votes}").replacingOccurrences(of: "{votes}", with: Sonda.glosyTekst(sonda.razem))).czcionka(12).foregroundStyle(Barwy.slaby)
        }
    }

    private func glosuj() async {
        guard let k = wybor else { return }
        wysyla = true
        komunikat = await m.glosuj(sonda: sonda, klucz: k, blok: blok)
        wysyla = false
    }
}

/// Kalendarz wydarzeń w miejscu sondy: miesiąc (Pn–Nd, dziś w obwódce, dni z wydarzeniami z kropką),
/// strzałki do miesiąca ostatniego wydarzenia. Lista pokazuje wydarzenia wybranego dnia: domyślnie dzisiaj,
/// a po stuknięciu dnia ten dzień (Karol, 22.11.2026), razem z wydarzeniami, które wtedy trwają.
struct KalendarzWidok: View {
    @EnvironmentObject private var m: Magazyn
    let kalendarz: KalendarzSpot
    let tytul: String?
    @Binding var strona: StronaWWW?
    @State private var przesuniecie = 0
    @State private var wybranyDzien: String
    @State private var listaDnia: [Wydarzenie]?
    @State private var dzienNaStronie = false

    init(kalendarz: KalendarzSpot, tytul: String?, strona: Binding<StronaWWW?>) {
        self.kalendarz = kalendarz
        self.tytul = tytul
        _strona = strona
        let dzis = kalendarz.dzisiaj.isEmpty ? KalendarzWidok.iso.string(from: Date()) : kalendarz.dzisiaj
        _wybranyDzien = State(initialValue: dzis)
    }

    private static let iso: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Europe/Warsaw")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
    // nazwy miesięcy i dni w języku aplikacji
    private static var nazwaMiesiaca: DateFormatter { Jezyk.format("LLLL yyyy") }
    private static var dzienSlownie: DateFormatter { Jezyk.format("d MMMM") }
    private static var dzienNaglowek: DateFormatter { Jezyk.format("EEEE, d MMMM") }
    /// Nagłówki kolumn od poniedziałku: po polsku jak dotąd, w innym języku skróty z systemu (Mon, Tue… / Пн, Вт…).
    private static var dniTygodnia: [String] {
        guard !Jezyk.polski else { return ["Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"] }
        var c = Calendar(identifier: .gregorian)
        c.locale = Jezyk.locale()
        let s = c.shortStandaloneWeekdaySymbols.map { $0.prefix(1).uppercased() + $0.dropFirst() }
        return s.count == 7 ? Array(s[1...] + s[..<1]) : ["Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"]
    }
    /// „Niedziela, 22 listopada · dziś”
    private var naglowekDnia: String {
        guard let d = KalendarzWidok.iso.date(from: wybranyDzien) else { return wybranyDzien }
        let t = KalendarzWidok.dzienNaglowek.string(from: d)
        return t.prefix(1).uppercased() + t.dropFirst() + (wybranyDzien == kalendarz.dzisiaj ? " · " + Napisy.t("app.today", "dziś").lowercased() : "")
    }
    private var kal: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.locale = Jezyk.locale()
        c.timeZone = TimeZone(identifier: "Europe/Warsaw") ?? .current
        c.firstWeekday = 2
        return c
    }
    private var bazaMiesiaca: Date { KalendarzWidok.iso.date(from: kalendarz.miesiac + "-01") ?? Date() }
    private var poczatekMiesiaca: Date { kal.date(byAdding: .month, value: przesuniecie, to: bazaMiesiaca) ?? bazaMiesiaca }
    private var maksPrzesuniecie: Int {
        guard let ostatni = kalendarz.dni.max(), let d = KalendarzWidok.iso.date(from: ostatni) else { return 0 }
        return max(0, kal.dateComponents([.month], from: bazaMiesiaca, to: d).month ?? 0)
    }
    private var komorki: [String?] {
        let pierwszy = poczatekMiesiaca
        let dniWMiesiacu = kal.range(of: .day, in: .month, for: pierwszy)?.count ?? 30
        let puste = (kal.component(.weekday, from: pierwszy) + 5) % 7
        var wynik: [String?] = Array(repeating: nil, count: puste)
        for i in 0..<dniWMiesiacu {
            if let d = kal.date(byAdding: .day, value: i, to: pierwszy) { wynik.append(KalendarzWidok.iso.string(from: d)) }
        }
        return wynik
    }

    var body: some View {
        if let t = tytul { Naglowek(tytul: t) }
        VStack(spacing: 12) {
            HStack {
                Button { przesuniecie -= 1 } label: { Image(systemName: "chevron.left").frame(width: 44, height: 44) }
                    .disabled(przesuniecie <= 0)
                    .accessibilityLabel(Napisy.t("app.prevMonth", "Poprzedni miesiąc"))
                Spacer()
                let nazwa = KalendarzWidok.nazwaMiesiaca.string(from: poczatekMiesiaca)
                Text(nazwa.prefix(1).uppercased() + nazwa.dropFirst()).czcionka(15, .bold).foregroundStyle(Barwy.tekst)
                Spacer()
                Button { przesuniecie += 1 } label: { Image(systemName: "chevron.right").frame(width: 44, height: 44) }
                    .disabled(przesuniecie >= maksPrzesuniecie)
                    .accessibilityLabel(Napisy.t("app.nextMonth", "Następny miesiąc"))
            }
            .foregroundStyle(m.motyw.akcent)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
                ForEach(KalendarzWidok.dniTygodnia, id: \.self) { d in
                    Text(d).czcionka(11, .semibold).foregroundStyle(Barwy.slaby)
                }
                ForEach(Array(komorki.enumerated()), id: \.offset) { _, d in
                    if let d { komorka(d) } else { Color.clear.frame(height: 36) }
                }
            }
            // bez nagłówka wybranego dnia („Poniedziałek, 23 listopada · dziś”) – Karol, 23.11: „tego nie powinno być”;
            // wybrany dzień widać w siatce kalendarza
            if let lista = listaDnia, !lista.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(lista.enumerated()), id: \.element.id) { i, w in
                        Button { otworz(w) } label: { WierszWydarzenia(wydarzenie: w, naDzien: wybranyDzien) }.buttonStyle(.plain)
                        if i < lista.count - 1 { Barwy.ramka.frame(height: 1) }
                    }
                }
                .background(Barwy.tlo.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
            } else if listaDnia == nil {
                ProgressView().frame(maxWidth: .infinity, minHeight: 60)
            } else if dzienNaStronie {
                Button { strona = StronaWWW(sciezka: "wydarzenia") } label: {
                    Text(Napisy.t("app.dayOnWebsite", "Szczegóły tego dnia są na tvml.pl")).czcionka(14, .semibold)
                }
            } else {
                Text(Napisy.t("app.noEventsDay", "Brak wydarzeń tego dnia.")).czcionka(14).foregroundStyle(Barwy.slaby)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack {
                Button { strona = StronaWWW(sciezka: "wydarzenia#dodaj") } label: { Text(Napisy.t("app.eventSubmit", "Zgłoś wydarzenie")).czcionka(14, .semibold) }
                Spacer()
                Button { strona = StronaWWW(sciezka: "wydarzenia") } label: { Text(Napisy.t("app.eventsAllShort", "Wszystkie")).czcionka(14, .semibold) }
            }
        }
        .padding(14)
        .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
        .task(id: wybranyDzien) { await wczytajDzien() }
    }

    private func komorka(_ d: String) -> some View {
        let maWydarzenia = kalendarz.dni.contains(d)
        let dzis = d == kalendarz.dzisiaj
        let wybrany = d == wybranyDzien
        let numer = String(Int(d.suffix(2)) ?? 0)
        let data = KalendarzWidok.iso.date(from: d)
        return Button {
            wybranyDzien = d
        } label: {
            VStack(spacing: 2) {
                Text(numer).czcionka(13, maWydarzenia ? .bold : .regular)
                    .foregroundStyle(wybrany ? m.motyw.napisNaAkcencie : (maWydarzenia ? m.motyw.akcent : Barwy.tekst))
                Circle().fill(maWydarzenia && !wybrany ? m.motyw.akcent : Color.clear).frame(width: 5, height: 5)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 36)
            .background(wybrany ? m.motyw.akcent : Color.clear, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(dzis ? Barwy.slaby : Color.clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!maWydarzenia && !dzis)
        .accessibilityAddTraits(wybrany ? .isSelected : [])
        .accessibilityLabel((data.map { KalendarzWidok.dzienSlownie.string(from: $0) } ?? d) + (maWydarzenia ? ": " + Napisy.t("app.hasEvents", "są wydarzenia") : "") + (dzis ? ", " + Napisy.t("app.today", "dziś").lowercased() : ""))
    }

    /// Wydarzenia wybranego dnia z /api/v1/events?from=D&to=D (także trwające wtedy, np. wystawy).
    /// Bez sieci: te z listy kalendarza, które zaczynają się tego dnia.
    private func wczytajDzien() async {
        let d = wybranyDzien
        dzienNaStronie = false
        listaDnia = nil
        if let z = await m.wydarzeniaDnia(d) {
            guard d == wybranyDzien else { return }
            listaDnia = z
            return
        }
        guard d == wybranyDzien else { return }
        let juzSa = kalendarz.wydarzenia.filter { $0.dzienISO == d }
        listaDnia = juzSa
        dzienNaStronie = juzSa.isEmpty && kalendarz.dni.contains(d)
    }

    private func otworz(_ w: Wydarzenie) {
        if let id = w.artykulId {
            Task { @MainActor in
                if let a = await m.artykul(id) { m.otworz(a) } else { m.otworz(EkranWiecej.wydarzenie(w)) }
            }
        } else {
            m.otworz(EkranWiecej.wydarzenie(w))
        }
    }
}

/// Wydarzenie w aplikacji: zdjęcie, data i godzina, miejsce, organizator, bilety i opis (/api/v1/events/{id}, API 1.7).
/// Do czasu 1.7 pokazuje to, co było na liście, i link do strony wydarzenia.
struct WydarzenieWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    let poczatkowe: Wydarzenie
    @State private var pelne: Wydarzenie?
    @State private var strona: StronaWWW?

    private var w: Wydarzenie { pelne ?? poczatkowe }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Button { wroc() } label: {
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text(Napisy.t("app.back", "Wróć")) }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                if let u = w.obraz {
                    Color.clear
                        .frame(height: 210).frame(maxWidth: .infinity)
                        .overlay { AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Barwy.ramka } }
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .accessibilityHidden(true)
                }
                Text(w.tytul).font(Czcionka.naglowek(24)).foregroundStyle(Barwy.tekst).accessibilityAddTraits(.isHeader)
                VStack(alignment: .leading, spacing: 8) {
                    if !w.dataPelna.isEmpty { szczegol("calendar", w.dataPelna) }
                    if !w.miejsce.isEmpty { szczegol("mappin.and.ellipse", w.miejsce) }
                    if !w.organizator.isEmpty { szczegol("person.2", w.organizator) }
                    if !w.bilety.isEmpty { szczegol("ticket", w.bilety) }
                }
                if !w.opis.isEmpty {
                    Text(w.opis).czcionka(16).foregroundStyle(Barwy.tekst).textSelection(.enabled)
                }
                Button { strona = StronaWWW(sciezka: "wydarzenia?id=\(w.id)") } label: {
                    Text(Napisy.t("app.seeOnWebsite", "Zobacz na tvml.pl")).czcionka(14, .semibold)
                }
            }
            .padding(16)
        }
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $strona) { s in Safari(url: URL(string: "https://tvml.pl/" + s.sciezka)!).ignoresSafeArea() }
        .task { pelne = await m.wydarzenie(poczatkowe.id) }
    }

    private func szczegol(_ ikona: String, _ tekst: String) -> some View {
        Label { Text(tekst).czcionka(15).foregroundStyle(Barwy.tekst) } icon: {
            Image(systemName: ikona).foregroundStyle(m.motyw.akcent)
        }
    }
}
