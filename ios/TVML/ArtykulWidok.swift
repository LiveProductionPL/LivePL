import SwiftUI
import AVFoundation
import WebKit

/// Artykuł: baner zostaje u góry, pod nim „Wróć”, rozmiar tekstu i udostępnianie.
/// „Odsłuchaj” po lewej, ikony „Zapisz” i „Komentarze” po prawej, tagi na samym końcu.
struct ArtykulWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    let poczatkowy: Artykul
    @State private var pelny: Artykul?
    @StateObject private var lektor = Lektor()
    @AppStorage("tvml-rozmiar") private var rozmiar = 1
    @State private var postep: CGFloat = 0
    @State private var komentarze = false
    @State private var film: String?

    private var a: Artykul { pelny ?? poczatkowy }
    private var wielkosc: CGFloat { [16, 18, 21][min(max(rozmiar, 0), 2)] }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Button { wroc() } label: {
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text("Wróć") }
                        .font(.system(size: 15, weight: .bold))
                        .padding(.horizontal, 10).frame(height: 44)
                }
                Spacer()
                Button { rozmiar = (rozmiar + 1) % 3 } label: {
                    Text("Aa").font(.system(size: 15, weight: .bold)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Rozmiar tekstu")
                ShareLink(item: a.link, subject: Text(a.tytul)) {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 17, weight: .semibold)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Udostępnij")
            }
            .foregroundStyle(Barwy.tekst)
            .padding(.horizontal, 4)
            .background(Barwy.karta)
            .overlay(alignment: .bottomLeading) {
                GeometryReader { g in Barwy.zloto.frame(width: g.size.width * postep, height: 3) }
                    .frame(height: 3).offset(y: 1)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    EtykietaKategorii(artykul: a)
                    Text(a.tytul)
                        .font(Czcionka.naglowek(wielkosc + 10))
                        .foregroundStyle(Barwy.tekst)
                    HStack(spacing: 10) {
                        Text(inicjaly(a.autor))
                            .font(.system(size: 13, weight: .bold)).foregroundStyle(.white)
                            .frame(width: 36, height: 36).background(Barwy.granat, in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(a.autor.isEmpty ? "Redakcja TVML" : a.autor).font(.system(size: 14, weight: .bold)).foregroundStyle(Barwy.tekst)
                            Text(opisDaty).font(.system(size: 12)).foregroundStyle(Barwy.slaby)
                        }
                    }
                    HStack(spacing: 8) {
                        Button { lektor.przelacz(tekstDoCzytania) } label: {
                            Label(lektor.mowi ? "Zatrzymaj" : "Odsłuchaj", systemImage: lektor.mowi ? "stop.fill" : "speaker.wave.2")
                                .font(.system(size: 13, weight: .semibold))
                                .padding(.horizontal, 14).frame(height: 40)
                                .foregroundStyle(.white)
                                .background(Barwy.granat, in: Capsule())
                        }
                        Spacer()
                        Button { m.przelaczZapis(a) } label: {
                            Image(systemName: m.zapisane.contains(a.id) ? "bookmark.fill" : "bookmark")
                                .frame(width: 44, height: 40)
                                .overlay(Capsule().stroke(Barwy.ramka))
                        }
                        .accessibilityLabel(m.zapisane.contains(a.id) ? "Usuń z zapisanych" : "Zapisz")
                        Button { komentarze = true } label: {
                            Image(systemName: "bubble.left")
                                .frame(width: 44, height: 40)
                                .overlay(Capsule().stroke(Barwy.ramka))
                        }
                        .accessibilityLabel("Komentarze: \(a.komentarze)")
                    }
                    .foregroundStyle(Barwy.tekst)

                    VStack(alignment: .leading, spacing: 6) {
                        Obraz(url: a.miniatura(1200))
                            .frame(height: 220).frame(maxWidth: .infinity).clipShape(RoundedRectangle(cornerRadius: 14))
                        if !a.zrodloZdjecia.isEmpty {
                            Text(a.zrodloZdjecia).font(.system(size: 12)).foregroundStyle(Barwy.slaby)
                        }
                    }

                    if let tresc = pelny?.tresc {
                        ForEach(Array(Html.bloki(tresc).enumerated()), id: \.offset) { i, blok in
                            switch blok {
                            case .akapit(let t):
                                Text(t)
                                    .font(.system(size: wielkosc, weight: i == 0 ? .semibold : .regular))
                                    .lineSpacing(wielkosc * 0.4)
                                    .foregroundStyle(Barwy.tekst)
                                    .textSelection(.enabled)
                            case .srodtytul(let t):
                                Text(t).font(Czcionka.naglowek(wielkosc + 4)).foregroundStyle(Barwy.tekst)
                            case .obraz(let u):
                                Obraz(url: Siec.obraz(u.absoluteString, szerokosc: 1200))
                                    .frame(height: 220).frame(maxWidth: .infinity).clipShape(RoundedRectangle(cornerRadius: 14))
                            case .film(let id):
                                Button { film = id } label: {
                                    ZStack {
                                        Obraz(url: URL(string: "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"))
                                        Image(systemName: "play.circle.fill").font(.system(size: 54)).foregroundStyle(.white, Barwy.zloto)
                                    }
                                    .frame(height: 200).frame(maxWidth: .infinity).clipShape(RoundedRectangle(cornerRadius: 14))
                                }
                                .accessibilityLabel("Odtwórz film")
                            }
                        }
                    } else {
                        Text(a.lead).font(.system(size: wielkosc, weight: .semibold)).foregroundStyle(Barwy.tekst)
                        ProgressView().frame(maxWidth: .infinity)
                    }

                    if !a.tagi.isEmpty {
                        FlowTagi(tagi: a.tagi)
                            .padding(.top, 12)
                            .overlay(alignment: .top) { Barwy.ramka.frame(height: 1) }
                    }
                }
                .padding(18)
            }
            .onScrollGeometryChange(for: CGFloat.self) { g in
                let zakres = g.contentSize.height - g.containerSize.height
                return zakres > 0 ? min(1, max(0, g.contentOffset.y / zakres)) : 0
            } action: { _, nowy in postep = nowy }
        }
        .background(Barwy.karta)
        .toolbar(.hidden, for: .navigationBar)
        .task { if pelny == nil { pelny = await m.artykul(poczatkowy.id) } }
        .onDisappear { lektor.zatrzymaj() }
        .sheet(isPresented: $komentarze) { KomentarzeWidok(artykul: a) }
        .sheet(item: Binding(get: { film.map { FilmId(id: $0) } }, set: { film = $0?.id })) { f in
            Odtwarzacz(adres: URL(string: "https://www.youtube-nocookie.com/embed/\(f.id)?playsinline=1&rel=0")!)
        }
    }

    private var opisDaty: String {
        var czesci: [String] = []
        if let d = a.data { czesci.append(d.formatted(.dateTime.day().month(.wide).year().locale(Locale(identifier: "pl_PL")))) }
        let slowa = (pelny?.tresc.map { Html.tekst($0) } ?? a.lead).split(separator: " ").count
        czesci.append("czytanie ok. \(max(1, slowa / 200)) min")
        if a.odslony > 0 { czesci.append("\(a.odslony) odsłon") }
        return czesci.joined(separator: " · ")
    }

    private var tekstDoCzytania: String {
        let tresc = pelny?.tresc.map { Html.akapity($0).joined(separator: "\n") } ?? a.lead
        return a.tytul + ".\n" + tresc
    }

    private func inicjaly(_ s: String) -> String {
        let litery = s.split(separator: " ").compactMap(\.first).prefix(2)
        return litery.isEmpty ? "TV" : String(litery).uppercased()
    }
}

struct FilmId: Identifiable { let id: String }

struct FlowTagi: View {
    let tagi: [String]
    var body: some View {
        FlowUklad(odstep: 6) {
            ForEach(tagi, id: \.self) { t in
                Text("#" + t)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Barwy.link)
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Barwy.link.opacity(0.1), in: Capsule())
            }
        }
    }
}

/// Prosty układ, który zawija elementy do następnej linii.
struct FlowUklad: Layout {
    var odstep: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let szer = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, wiersz: CGFloat = 0
        for s in subviews {
            let r = s.sizeThatFits(.unspecified)
            if x + r.width > szer, x > 0 { x = 0; y += wiersz + odstep; wiersz = 0 }
            x += r.width + odstep; wiersz = max(wiersz, r.height)
        }
        return CGSize(width: szer == .infinity ? x : szer, height: y + wiersz)
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, wiersz: CGFloat = 0
        for s in subviews {
            let r = s.sizeThatFits(.unspecified)
            if x + r.width > b.maxX, x > b.minX { x = b.minX; y += wiersz + odstep; wiersz = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(r))
            x += r.width + odstep; wiersz = max(wiersz, r.height)
        }
    }
}

/// „Odsłuchaj”: czyta artykuł polskim głosem telefonu, także przy zablokowanym ekranie.
@MainActor
final class Lektor: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    @Published var mowi = false
    private let syntezator = AVSpeechSynthesizer()

    override init() {
        super.init()
        syntezator.delegate = self
    }

    func przelacz(_ tekst: String) {
        if syntezator.isSpeaking { zatrzymaj(); return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try? AVAudioSession.sharedInstance().setActive(true)
        let wypowiedz = AVSpeechUtterance(string: tekst)
        wypowiedz.voice = AVSpeechSynthesisVoice(language: "pl-PL")
        syntezator.speak(wypowiedz)
        mowi = true
    }

    func zatrzymaj() {
        syntezator.stopSpeaking(at: .immediate)
        mowi = false
    }

    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didFinish u: AVSpeechUtterance) {
        Task { @MainActor in self.mowi = false }
    }
}

/// Komentarze czytelników (zatwierdzone przez redakcję). Dodawanie z aplikacji przyjdzie z nowym API.
struct KomentarzeWidok: View {
    @Environment(\.dismiss) private var zamknij
    @Environment(\.openURL) private var otworzAdres
    let artykul: Artykul
    @State private var lista: [Slownik] = []
    @State private var laduje = true
    @State private var bezPoczty = false

    var body: some View {
        NavigationStack {
            List {
                if laduje { ProgressView() }
                else if lista.isEmpty { Text("Nie ma jeszcze komentarzy.").foregroundStyle(Barwy.slaby) }
                ForEach(Array(lista.enumerated()), id: \.offset) { _, k in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(autor(k)).font(.system(size: 14, weight: .bold))
                            Spacer()
                            // zgłoszenie nieodpowiedniego komentarza trafia do redakcji, która moderuje komentarze w panelu
                            Button { zglos(k) } label: { Image(systemName: "flag").font(.system(size: 13)) }
                                .buttonStyle(.borderless)
                                .foregroundStyle(Barwy.slaby)
                                .accessibilityLabel("Zgłoś komentarz")
                        }
                        Text(tresc(k)).font(.system(size: 15))
                        Text(k.s("created_at").isEmpty ? k.s("date") : k.s("created_at")).font(.system(size: 12)).foregroundStyle(Barwy.slaby)
                    }
                    .padding(.vertical, 4)
                }
                Section {
                    Link("Dodaj komentarz na tvml.pl", destination: artykul.link)
                } footer: {
                    if !lista.isEmpty { Text("Komentarz łamie zasady? Stuknij chorągiewkę, a zgłoszenie trafi do redakcji TVML.") }
                }
            }
            .alert("Zgłoś komentarz", isPresented: $bezPoczty) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Napisz do redakcji: redakcja@tvml.pl")
            }
            .navigationTitle("Komentarze")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Gotowe") { zamknij() } } }
            .task {
                let j = try? await Siec.json("tvml-db.php?action=get-comments&post_id=\(artykul.id)")
                if let l = j as? [Slownik] { lista = l }
                else if let d = j as? Slownik { lista = d.lista("comments").isEmpty ? d.lista("items") : d.lista("comments") }
                laduje = false
            }
        }
    }

    private func autor(_ k: Slownik) -> String { k.s("author_name").isEmpty ? k.s("author") : k.s("author_name") }
    private func tresc(_ k: Slownik) -> String { Html.tekst(k.s("content").isEmpty ? k.s("text") : k.s("content")) }

    private func zglos(_ k: Slownik) {
        let id = k.s("id")
        var c = URLComponents()
        c.scheme = "mailto"
        c.path = "redakcja@tvml.pl"
        c.queryItems = [
            URLQueryItem(name: "subject", value: "Zgłoszenie komentarza" + (id.isEmpty ? "" : " nr \(id)")),
            URLQueryItem(name: "body", value: "Artykuł: \(artykul.tytul)\n\(artykul.link.absoluteString)\n\nKomentarz (\(autor(k))):\n\(tresc(k))\n\nDlaczego zgłaszam:\n")
        ]
        guard let adres = c.url else { bezPoczty = true; return }
        otworzAdres(adres) { otwarte in if !otwarte { bezPoczty = true } }
    }
}

/// Film z YouTube (bez ciasteczek śledzących: youtube-nocookie), z odtwarzaniem w miejscu i obrazem w obrazie.
struct Odtwarzacz: View {
    @Environment(\.dismiss) private var zamknij
    let adres: URL

    var body: some View {
        NavigationStack {
            OknoWideo(adres: adres)
                .aspectRatio(16 / 9, contentMode: .fit)
                .frame(maxHeight: .infinity)
                .background(.black)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Gotowe") { zamknij() } } }
                .toolbarBackground(.black, for: .navigationBar)
        }
    }
}

struct OknoWideo: UIViewRepresentable {
    let adres: URL
    func makeUIView(context: Context) -> WKWebView {
        let k = WKWebViewConfiguration()
        k.allowsInlineMediaPlayback = true
        k.allowsPictureInPictureMediaPlayback = true
        k.mediaTypesRequiringUserActionForPlayback = []
        let w = WKWebView(frame: .zero, configuration: k)
        w.isOpaque = false
        w.backgroundColor = .black
        w.scrollView.isScrollEnabled = false
        w.load(URLRequest(url: adres))
        return w
    }
    func updateUIView(_ w: WKWebView, context: Context) {}
}
