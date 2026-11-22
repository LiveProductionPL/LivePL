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
            Button { strona = StronaWWW(sciezka: "") } label: {
                Label("Zgłoś zdarzenie (Alert TVML)", systemImage: "exclamationmark.triangle.fill")
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
            if !m.wydarzenia.isEmpty {
                if !blok.ukryjTytul { Naglowek(tytul: blok.tytul.isEmpty ? "Kalendarz wydarzeń" : blok.tytul) }
                let lista = Array(m.wydarzenia.prefix(blok.limit > 0 ? blok.limit : 5))
                VStack(spacing: 0) {
                    ForEach(Array(lista.enumerated()), id: \.element.id) { i, w in
                        Button { otworz(w) } label: { WierszWydarzenia(wydarzenie: w) }.buttonStyle(.plain)
                        if i < lista.count - 1 { Barwy.ramka.frame(height: 1) }
                    }
                }
                .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                Button { strona = StronaWWW(sciezka: "wydarzenia") } label: {
                    Text("Wszystkie wydarzenia na tvml.pl").czcionka(14, .semibold)
                }
            }
        case "classifieds":
            if !m.ogloszenia.isEmpty {
                Naglowek(tytul: blok.tytul.isEmpty ? "Ogłoszenia drobne" : blok.tytul)
                VStack(spacing: 0) {
                    ForEach(Array(m.ogloszenia.prefix(blok.limit > 0 ? blok.limit : 4).enumerated()), id: \.element.id) { i, o in
                        Button { ogloszenie = o } label: { WierszOgloszenia(ogloszenie: o) }.buttonStyle(.plain)
                        if i < min(m.ogloszenia.count, blok.limit > 0 ? blok.limit : 4) - 1 { Barwy.ramka.frame(height: 1) }
                    }
                }
                .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                .sheet(item: $ogloszenie) { OgloszenieWidok(ogloszenie: $0) }
                Button { strona = StronaWWW(sciezka: "lista/ogloszenia") } label: {
                    Text("Wszystkie ogłoszenia na tvml.pl").czcionka(14, .semibold)
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

    /// Wydarzenie z artykułem otwiera artykuł w aplikacji, a bez niego swoją stronę na tvml.pl.
    private func otworz(_ w: Wydarzenie) {
        if let id = w.artykulId {
            Task { @MainActor in
                if let a = await m.artykul(id) { m.otworz(a) } else { pokaz(w) }
            }
        } else {
            pokaz(w)
        }
    }

    private func pokaz(_ w: Wydarzenie) {
        let baza = "https://tvml.pl/"
        let s = w.adres?.absoluteString ?? ""
        strona = StronaWWW(sciezka: s.hasPrefix(baza) ? String(s.dropFirst(baza.count)) : "wydarzenia?id=\(w.id)")
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
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text("Wróć") }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                HStack(spacing: 10) {
                    AsyncImage(url: gmina.herb) { $0.resizable().scaledToFit() } placeholder: { Color.clear }.frame(width: 30, height: 36)
                    Text(gmina.nazwa).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                }
                if laduje { ProgressView().frame(maxWidth: .infinity, minHeight: 200) }
                else if lista.isEmpty { Text("Brak wiadomości z tej gminy.").foregroundStyle(Barwy.slaby) }
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
                            Text("Kontakt").czcionka(13, .bold).foregroundStyle(Barwy.slaby)
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
            .navigationTitle("Ogłoszenie")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Gotowe") { zamknij() } } }
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
struct KafelPogody: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dynamicTypeSize) private var rozmiarTekstu

    var body: some View {
        Button { m.pokaz(.pogoda) } label: {
            ZStack(alignment: .leading) {
                ScenaPogody(stan: StanPogody.z(ikony: m.pogoda?.ikona ?? "cloud"),
                            pora: PoraDnia.teraz(dzien: m.pogoda?.dzien ?? true),
                            scena: m.motyw.scena(miesiac: Calendar.current.component(.month, from: .now)))
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("POGODA W LESZNIE").czcionka(11, .bold).kerning(1)
                        Text(m.pogoda.map { Pogoda.stopnie($0.temperatura) + "C" } ?? "–").czcionka(30, .heavy)
                        Text(m.pogoda?.opis ?? "").czcionka(13)
                    }
                    Spacer()
                    // przy bardzo dużym tekście prognoza na 3 dni nie mieści się obok temperatury (jest na ekranie Pogoda)
                    if !rozmiarTekstu.isAccessibilitySize {
                    HStack(spacing: 12) {
                        ForEach((m.pogoda?.dni ?? []).dropFirst().prefix(3)) { d in
                            VStack(spacing: 2) {
                                Text(d.nazwa).czcionka(12).opacity(0.85)
                                Text(Pogoda.stopnie(d.max)).czcionka(12, .bold)
                            }
                        }
                    }
                    }
                }
                .foregroundStyle(.white)
                .shadow(color: .black.opacity(0.45), radius: 6)
                .padding(.horizontal, 18)
            }
            .frame(minHeight: 110)
            .clipShape(RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Pogoda w Lesznie: \(m.pogoda.map { Pogoda.stopnie($0.temperatura) } ?? ""), \(m.pogoda?.opis ?? "")")
    }
}

/// Wiersz kalendarza wydarzeń: kafelek z dniem i miesiącem, tytuł, godzina i miejsce.
struct WierszWydarzenia: View {
    @EnvironmentObject private var m: Magazyn
    let wydarzenie: Wydarzenie

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(spacing: 0) {
                Text(wydarzenie.dzien).font(Czcionka.naglowek(22)).foregroundStyle(m.motyw.napisNaAkcencie)
                Text(wydarzenie.miesiac).czcionka(11, .bold).foregroundStyle(m.motyw.napisNaAkcencie)
            }
            .frame(width: 52)
            .frame(minHeight: 56)
            .background(m.motyw.akcent, in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 3) {
                Text(wydarzenie.tytul).czcionka(15, .semibold).foregroundStyle(Barwy.tekst).lineLimit(3)
                Text([wydarzenie.dzienTygodnia, wydarzenie.godzina, wydarzenie.miejsce].filter { !$0.isEmpty }.joined(separator: " · "))
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
