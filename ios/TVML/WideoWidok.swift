import SwiftUI

/// Wideo z kanału YouTube TVML (dziś @TVMLlive; kanał i wyróżniony film przejdą do panelu).
struct WideoWidok: View {
    @EnvironmentObject private var m: Magazyn
    @State private var odtwarzany: Film?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Wideo").font(.system(size: 26, weight: .heavy, design: .serif))
                    Spacer()
                    Label("YouTube @TVMLlive", systemImage: "play.rectangle").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color(hex: "#a8b3c8"))
                }
                .foregroundStyle(.white)
                if m.filmy.isEmpty { ProgressView().tint(.white).frame(maxWidth: .infinity, minHeight: 240) }
                if let pierwszy = m.filmy.first {
                    Button { odtwarzany = pierwszy } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            ZStack(alignment: .topLeading) {
                                AsyncImage(url: pierwszy.obraz) { $0.resizable().scaledToFill() } placeholder: { Color(hex: "#1c2840") }
                                    .frame(height: 200).frame(maxWidth: .infinity).clipped()
                                Text("WYRÓŻNIONE").font(.system(size: 11, weight: .heavy)).kerning(1).foregroundStyle(.white)
                                    .padding(.horizontal, 8).padding(.vertical, 4).background(Color(hex: "#c8102e"), in: RoundedRectangle(cornerRadius: 6)).padding(12)
                                Image(systemName: "play.circle.fill").font(.system(size: 60)).foregroundStyle(Color(hex: "#1d1400"), Barwy.zloto)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                            }
                            .frame(height: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            Text(pierwszy.tytul).font(.system(size: 18, weight: .bold)).foregroundStyle(.white).multilineTextAlignment(.leading)
                            Text(Film.data(pierwszy.data)).font(.system(size: 12)).foregroundStyle(Color(hex: "#a8b3c8"))
                        }
                    }
                    .buttonStyle(.plain)
                }
                ForEach(m.filmy.dropFirst()) { f in
                    Button { odtwarzany = f } label: {
                        HStack(spacing: 12) {
                            KafelFilmu(film: f, szerokosc: 150, zPodpisem: false)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(f.tytul).font(.system(size: 14, weight: .bold)).foregroundStyle(.white).multilineTextAlignment(.leading).lineLimit(3)
                                Text(Film.data(f.data)).font(.system(size: 12)).foregroundStyle(Color(hex: "#a8b3c8"))
                            }
                            Spacer(minLength: 0)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .refreshable { await m.odswiez(wymus: true) }
        .background(Color(hex: "#070d1a"))
        .sheet(item: $odtwarzany) { f in
            if let adres = f.osadzenie { Odtwarzacz(adres: adres) }
        }
    }
}

struct KafelFilmu: View {
    let film: Film
    var szerokosc: CGFloat = 220
    var zPodpisem = true

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack {
                AsyncImage(url: film.obraz) { $0.resizable().scaledToFill() } placeholder: { Color(hex: "#1c2840") }
                Image(systemName: "play.fill").font(.system(size: 18)).foregroundStyle(.white)
                    .frame(width: 40, height: 40).background(.black.opacity(0.55), in: Circle())
            }
            .frame(width: szerokosc, height: szerokosc * 9 / 16)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            if zPodpisem {
                Text(film.tytul).font(.system(size: 13, weight: .semibold)).foregroundStyle(Barwy.tekst).lineLimit(2).frame(width: szerokosc, alignment: .leading)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Film: \(film.tytul)")
    }
}

extension Film {
    static func data(_ iso: String) -> String {
        guard let d = ISO8601DateFormatter().date(from: iso) else { return "" }
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "pl_PL")
        return f.localizedString(for: d, relativeTo: .now)
    }
}
