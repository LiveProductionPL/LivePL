import SwiftUI

/// Start: kategorie, karuzela, kafel pogody, aktualności, najczęściej czytane i wideo.
/// Kolejność bloków przyjdzie z panelu (layout.widgetsMobile) razem z nowym API.
struct StartWidok: View {
    @EnvironmentObject private var m: Magazyn
    @State private var kategoria: String?

    var body: some View {
        let lista = kategoria.flatMap { m.wKategorii[$0] } ?? m.najnowsze
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
                                    .font(.system(size: 13, weight: .semibold))
                                    .padding(.horizontal, 14).frame(height: 34)
                                    .foregroundStyle(wybrana ? Color.white : Barwy.tekst)
                                    .background(wybrana ? m.motyw.akcent : Barwy.karta, in: Capsule())
                                    .overlay(Capsule().stroke(wybrana ? Color.clear : Barwy.ramka))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .scrollClipDisabled()

                if let blad = m.blad {
                    Text(blad).font(.system(size: 14)).foregroundStyle(Barwy.slaby)
                }
                if lista.isEmpty && m.laduje {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 300)
                }
                if !lista.isEmpty {
                    Karuzela(artykuly: Array(lista.prefix(5)))
                }
                if kategoria == nil {
                    KafelPogody()
                }
                if lista.count > 5 {
                    Naglowek(tytul: kategoria.flatMap { s in m.kategorie.first { $0.slug == s }?.nazwa } ?? "Aktualności")
                    ForEach(lista.dropFirst(5)) { a in
                        NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                    }
                }
                if kategoria == nil && !m.najczytniejsze.isEmpty {
                    Naglowek(tytul: "Najczęściej czytane")
                    VStack(spacing: 0) {
                        ForEach(Array(m.najczytniejsze.prefix(5).enumerated()), id: \.element.id) { i, a in
                            NavigationLink(value: a) {
                                HStack(alignment: .top, spacing: 12) {
                                    Text("\(i + 1)").font(.system(size: 22, weight: .heavy, design: .serif)).foregroundStyle(m.motyw.akcent).frame(width: 26)
                                    Text(a.tytul).font(.system(size: 15, weight: .semibold)).foregroundStyle(Barwy.tekst).frame(maxWidth: .infinity, alignment: .leading)
                                }
                                .padding(.vertical, 10)
                                .overlay(alignment: .bottom) { if i < 4 { Barwy.ramka.frame(height: 1) } }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 14)
                    .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                }
                if kategoria == nil && !m.filmy.isEmpty {
                    Naglowek(tytul: "Wideo")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(m.filmy.prefix(6)) { f in
                                Button { m.zakladka = .wideo } label: { KafelFilmu(film: f, szerokosc: 220) }.buttonStyle(.plain)
                            }
                        }
                    }
                    .scrollClipDisabled()
                }
            }
            .padding(16)
        }
        .refreshable { await m.odswiez(wymus: true) }
        .background(Barwy.tlo)
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
                Text(tytul).font(.system(size: 26, weight: .heavy, design: .serif)).foregroundStyle(Barwy.tekst)
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
            .font(.system(size: 21, weight: .heavy, design: .serif))
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
            Obraz(url: artykul.miniatura(900))
                .frame(height: wysokosc).frame(maxWidth: .infinity).clipped()
            LinearGradient(colors: [.clear, Color(red: 0.02, green: 0.05, blue: 0.11).opacity(0.92)], startPoint: .init(x: 0.5, y: 0.25), endPoint: .bottom)
            VStack(alignment: .leading, spacing: 8) {
                EtykietaKategorii(artykul: artykul)
                Text(artykul.tytul)
                    .font(.system(size: 24, weight: .heavy, design: .serif))
                    .foregroundStyle(.white)
                    .lineLimit(4)
                Text([artykul.autor, artykul.kiedy].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.system(size: 12)).foregroundStyle(Color(hex: "#d7deea"))
            }
            .padding(18)
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .contentShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct EtykietaKategorii: View {
    let artykul: Artykul
    var body: some View {
        let tlo = artykul.kolorKategorii
        Text(artykul.kategoria.uppercased())
            .font(.system(size: 11, weight: .heavy)).kerning(1)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .foregroundStyle(Kontrast.napis(na: artykul.kolor))
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
                    Text(artykul.kategoria.uppercased()).font(.system(size: 11, weight: .bold)).kerning(0.6).foregroundStyle(Barwy.slaby)
                }
                Text(artykul.tytul).font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).lineLimit(3)
                Text(artykul.kiedy).font(.system(size: 12)).foregroundStyle(Barwy.slaby)
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Barwy.ramka))
    }
}

/// Obrazek z sieci; w trybie żałobnym w odcieniach szarości.
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

    var body: some View {
        Button { m.zakladka = .pogoda } label: {
            ZStack(alignment: .leading) {
                ScenaPogody(stan: StanPogody.z(ikony: m.pogoda?.ikona ?? "cloud"),
                            pora: PoraDnia.teraz(dzien: m.pogoda?.dzien ?? true),
                            scena: m.motyw.scena(miesiac: Calendar.current.component(.month, from: .now)))
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("POGODA W LESZNIE").font(.system(size: 11, weight: .bold)).kerning(1)
                        Text(m.pogoda.map { Pogoda.stopnie($0.temperatura) + "C" } ?? "–").font(.system(size: 30, weight: .heavy))
                        Text(m.pogoda?.opis ?? "").font(.system(size: 13))
                    }
                    Spacer()
                    HStack(spacing: 12) {
                        ForEach((m.pogoda?.dni ?? []).dropFirst().prefix(3)) { d in
                            VStack(spacing: 2) {
                                Text(d.nazwa).font(.system(size: 12)).opacity(0.85)
                                Text(Pogoda.stopnie(d.max)).font(.system(size: 12, weight: .bold))
                            }
                        }
                    }
                }
                .foregroundStyle(.white)
                .shadow(color: .black.opacity(0.45), radius: 6)
                .padding(.horizontal, 18)
            }
            .frame(height: 110)
            .clipShape(RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Pogoda w Lesznie: \(m.pogoda.map { Pogoda.stopnie($0.temperatura) } ?? ""), \(m.pogoda?.opis ?? "")")
    }
}
