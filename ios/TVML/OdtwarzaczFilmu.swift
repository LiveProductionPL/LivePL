import SwiftUI
import WebKit

/// Orientacja aplikacji. Na iPhonie aplikacja stoi w pionie, a film gra na pełnym ekranie w poziomie
/// (Karol, 24.11, „droga 1”: „od razu w poziomie, a nie pion”). iPad obraca się jak dotąd.
@MainActor
enum Orientacja {
    static var maska: UIInterfaceOrientationMask = UIDevice.current.userInterfaceIdiom == .phone ? .portrait : .all

    static func ustaw(_ nowa: UIInterfaceOrientationMask) {
        guard UIDevice.current.userInterfaceIdiom == .phone else { return }
        maska = nowa
        guard let scena = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else { return }
        for okno in scena.windows { okno.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations() }
        scena.requestGeometryUpdate(.iOS(interfaceOrientations: nowa)) { _ in }
    }
}

/// Stan filmu z odtwarzacza YouTube (IFrame API), do naszego paska.
@MainActor
final class StanFilmu: ObservableObject {
    @Published var gotowy = false
    @Published var gra = false
    @Published var czas: Double = 0
    @Published var dlugosc: Double = 0
    @Published var blad: Int?
    weak var web: WKWebView?

    func js(_ kod: String) { web?.evaluateJavaScript(kod, completionHandler: nil) }
    func przelaczOdtwarzanie() { js(gra ? "pauza()" : "graj()") }
    func przeskocz(_ sekundy: Double) {
        let cel = min(max(0, czas + sekundy), max(dlugosc - 1, 0))
        czas = cel
        js("idz(\(cel))")
    }
    func idz(_ sekunda: Double) { czas = sekunda; js("idz(\(sekunda))") }
}

/// Film z YouTube na pełnym ekranie w poziomie, z naszym prostym paskiem zamiast przycisków YouTube:
/// odtwórz/pauza, ±10 s, postęp, zamknij. Odtwarzacz YouTube (IFrame API) bez jego przycisków (controls=0), bez obcych
/// podpowiedzi (rel=0), bez adnotacji (iv_load_policy=3). Logo i tytuł YouTube, które YouTube pokazuje na starcie i przy
/// pauzie, zostają widoczne: regulamin YouTube API zabrania ich zasłaniania. Stuknięcia łapie nasza warstwa (pasek),
/// więc nic nie otwiera aplikacji YouTube przypadkiem.
struct Odtwarzacz: View {
    @Environment(\.dismiss) private var zamknij
    @Environment(\.accessibilityReduceMotion) private var bezRuchu
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    let adres: URL
    @StateObject private var s = StanFilmu()
    @State private var pasek = true
    @State private var przesuwa = false
    @State private var pozycja: Double = 0
    @State private var chowanie: Task<Void, Never>?

    private var idFilmu: String { adres.lastPathComponent }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            OknoGracza(idFilmu: idFilmu, stan: s).ignoresSafeArea()
            // nasza przezroczysta warstwa nad filmem (bez przyciemniania, żeby nie zasłaniać logo i tytułu YouTube):
            // stuknięcie pokazuje albo chowa pasek
            Color.black.opacity(0.001)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { przelaczPasek() }
                .accessibilityHidden(true)
            if pasek || !s.gra || voiceOver { przyciski }
            if s.blad != nil { komunikatBledu }
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onAppear { Orientacja.ustaw(.landscape); planujChowanie() }
        .onDisappear { chowanie?.cancel(); Orientacja.ustaw(.portrait) }
        .onChange(of: s.gra) { _, _ in planujChowanie() }
        .onChange(of: s.czas) { _, nowy in if !przesuwa { pozycja = nowy } }
    }

    private var przyciski: some View {
        VStack(spacing: 0) {
            HStack {
                Button { zamknij() } label: {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 44, height: 44).background(.black.opacity(0.45), in: Circle())
                }
                .accessibilityLabel(Napisy.t("app.close", "Zamknij"))
                Spacer()
            }
            .padding(.horizontal, 20).padding(.top, 12)
            Spacer()
            HStack(spacing: 44) {
                przycisk("gobackward.10", Napisy.t("app.videoBack10", "Cofnij o 10 sekund"), rozmiar: 30) { s.przeskocz(-10); planujChowanie() }
                przycisk(s.gra ? "pause.fill" : "play.fill", s.gra ? Napisy.t("app.pause", "Pauza") : Napisy.t("app.play", "Odtwórz"), rozmiar: 40) {
                    s.przelaczOdtwarzanie(); planujChowanie()
                }
                przycisk("goforward.10", Napisy.t("app.videoForward10", "Do przodu o 10 sekund"), rozmiar: 30) { s.przeskocz(10); planujChowanie() }
            }
            .disabled(!s.gotowy)
            .opacity(s.gotowy ? 1 : 0.5)
            Spacer()
            // postęp; z prawej wolne miejsce na logo YouTube, którego nie zasłaniamy
            HStack(spacing: 12) {
                Text(Odtwarzacz.czas(pozycja)).czcionka(13, .semibold).monospacedDigit().foregroundStyle(.white)
                Slider(value: $pozycja, in: 0...max(s.dlugosc, 1), onEditingChanged: { edycja in
                    przesuwa = edycja
                    if !edycja { s.idz(pozycja); planujChowanie() } else { chowanie?.cancel() }
                })
                .tint(Barwy.zloto)
                .accessibilityLabel(Napisy.t("app.videoProgress", "Postęp filmu"))
                .accessibilityValue("\(Odtwarzacz.czas(pozycja)) / \(Odtwarzacz.czas(s.dlugosc))")
                Text(Odtwarzacz.czas(s.dlugosc)).czcionka(13, .semibold).monospacedDigit().foregroundStyle(.white.opacity(0.8))
            }
            .disabled(!s.gotowy)
            .padding(.leading, 24).padding(.trailing, 130).padding(.bottom, 18)
        }
        .transition(bezRuchu ? .identity : .opacity)
    }

    private func przycisk(_ ikona: String, _ opis: String, rozmiar: CGFloat, akcja: @escaping () -> Void) -> some View {
        Button(action: akcja) {
            Image(systemName: ikona).font(.system(size: rozmiar, weight: .bold)).foregroundStyle(.white)
                .frame(width: rozmiar * 1.9, height: rozmiar * 1.9)
                .background(.black.opacity(0.45), in: Circle())
        }
        .accessibilityLabel(opis)
    }

    /// Film, którego YouTube nie pozwala odtworzyć w aplikacji (np. właściciel wyłączył osadzanie): przejście do YouTube.
    private var komunikatBledu: some View {
        VStack(spacing: 14) {
            Text(Napisy.t("app.videoUnavailable", "Tego filmu nie da się odtworzyć w aplikacji.")).czcionka(16, .semibold).foregroundStyle(.white)
            if let yt = URL(string: "https://www.youtube.com/watch?v=" + idFilmu) {
                Link(Napisy.t("app.openYoutube", "Otwórz w YouTube"), destination: yt)
                    .czcionka(15, .bold).padding(.horizontal, 18).padding(.vertical, 10)
                    .background(Barwy.zloto, in: Capsule()).foregroundStyle(Color(hex: "#1d1400"))
            }
        }
        .padding(24).background(.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 16))
    }

    private func przelaczPasek() {
        if bezRuchu { pasek.toggle() } else { withAnimation(.easeInOut(duration: 0.2)) { pasek.toggle() } }
        planujChowanie()
    }

    /// W trakcie odtwarzania pasek chowa się po 3 s; przy pauzie i z VoiceOver zostaje.
    private func planujChowanie() {
        chowanie?.cancel()
        guard s.gra, !voiceOver else { return }
        chowanie = Task { @MainActor in
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled, s.gra, !przesuwa else { return }
            if bezRuchu { pasek = false } else { withAnimation(.easeInOut(duration: 0.3)) { pasek = false } }
        }
    }

    static func czas(_ sekundy: Double) -> String {
        guard sekundy.isFinite, sekundy > 0 else { return "0:00" }
        let s = Int(sekundy.rounded(.down))
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
    }
}

/// Odtwarzacz YouTube IFrame API w WKWebView. YouTube od 2025 r. wymaga strony, która osadza odtwarzacz (inaczej
/// „Błąd 153”): HTML z bazą https://tvml.pl i origin. Stan wraca do aplikacji przez window.webkit.messageHandlers.tvml.
struct OknoGracza: UIViewRepresentable {
    let idFilmu: String
    let stan: StanFilmu

    final class Koordynator: NSObject, WKScriptMessageHandler {
        weak var stan: StanFilmu?
        init(stan: StanFilmu) { self.stan = stan }

        func userContentController(_ uc: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let d = message.body as? [String: Any], let typ = d["typ"] as? String else { return }
            Task { @MainActor [weak stan] in
                guard let stan else { return }
                switch typ {
                case "gotowy":
                    stan.gotowy = true
                    if let l = d["dlugosc"] as? Double, l > 0 { stan.dlugosc = l }
                case "stan":
                    // YT.PlayerState: 1 gra, 3 buforuje (traktujemy jak granie), 2 pauza, 0 koniec
                    let s = (d["stan"] as? Int) ?? -1
                    stan.gra = s == 1 || s == 3
                case "czas":
                    if let c = d["czas"] as? Double { stan.czas = c }
                    if let l = d["dlugosc"] as? Double, l > 0 { stan.dlugosc = l }
                case "blad":
                    stan.blad = (d["kod"] as? Int) ?? 0
                default: break
                }
            }
        }
    }

    func makeCoordinator() -> Koordynator { Koordynator(stan: stan) }

    func makeUIView(context: Context) -> WKWebView {
        let k = WKWebViewConfiguration()
        k.allowsInlineMediaPlayback = true
        k.allowsPictureInPictureMediaPlayback = false
        k.mediaTypesRequiringUserActionForPlayback = []
        k.userContentController.add(context.coordinator, name: "tvml")
        let w = WKWebView(frame: .zero, configuration: k)
        w.isOpaque = false
        w.backgroundColor = .black
        w.scrollView.isScrollEnabled = false
        // stuknięcia obsługuje nasza warstwa nad filmem
        w.isUserInteractionEnabled = false
        stan.web = w
        let id = idFilmu.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-_"))) ?? ""
        let html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html, body { margin: 0; height: 100%; background: #000; overflow: hidden; } #p { position: fixed; inset: 0; width: 100%; height: 100%; }</style>
        </head><body><div id="p"></div>
        <script>
        var gracz = null;
        function wyslij(o) { try { window.webkit.messageHandlers.tvml.postMessage(o); } catch (e) {} }
        function onYouTubeIframeAPIReady() {
          gracz = new YT.Player('p', {
            host: 'https://www.youtube-nocookie.com',
            videoId: '\(id)',
            playerVars: { controls: 0, rel: 0, iv_load_policy: 3, fs: 0, playsinline: 1, disablekb: 1, autoplay: 1, origin: 'https://tvml.pl' },
            events: {
              onReady: function (e) { e.target.playVideo(); wyslij({ typ: 'gotowy', dlugosc: e.target.getDuration() || 0 }); },
              onStateChange: function (e) { wyslij({ typ: 'stan', stan: e.data }); },
              onError: function (e) { wyslij({ typ: 'blad', kod: e.data }); }
            }
          });
          setInterval(function () {
            if (gracz && gracz.getCurrentTime) wyslij({ typ: 'czas', czas: gracz.getCurrentTime() || 0, dlugosc: gracz.getDuration() || 0 });
          }, 500);
        }
        function graj() { if (gracz) gracz.playVideo(); }
        function pauza() { if (gracz) gracz.pauseVideo(); }
        function idz(t) { if (gracz) gracz.seekTo(t, true); }
        </script>
        <script src="https://www.youtube.com/iframe_api"></script>
        </body></html>
        """
        w.loadHTMLString(html, baseURL: URL(string: "https://tvml.pl"))
        return w
    }

    func updateUIView(_ w: WKWebView, context: Context) {}

    static func dismantleUIView(_ w: WKWebView, coordinator: Koordynator) {
        w.configuration.userContentController.removeScriptMessageHandler(forName: "tvml")
        w.stopLoading()
    }
}
