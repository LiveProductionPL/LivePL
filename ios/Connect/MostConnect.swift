import SwiftUI
import WebKit

/// Mostek między stroną connect.livepl.eu a iPhone'em. Strona używa zwykłych funkcji przeglądarki, których
/// WKWebView na iPhonie nie ma albo nie spełnia: blokady wygaszania ekranu (navigator.wakeLock), wibracji
/// (navigator.vibrate) i pełnego ekranu (requestFullscreen). Skrypt podmienia je na wywołania aplikacji,
/// więc ta sama strona działa w Safari, w przeglądarce na komputerze i tutaj, bez zmian na serwerze.
@MainActor
final class MostConnect: NSObject, ObservableObject, WKScriptMessageHandler {
    nonisolated static let host = "connect.livepl.eu"

    /// Pełny ekran z przycisku w sesji: bez paska stanu i z chowającą się kreską na dole iPhone'a.
    @Published private(set) var pelnyEkran = false
    private let wibracja = UIImpactFeedbackGenerator(style: .medium)
    private let kontrolery = NSHashTable<WKUserContentController>.weakObjects()

    /// Dokłada skrypt i odbiornik do konfiguracji nowej karty (Przegladarka.dostosujKonfiguracje).
    func podlacz(_ konfiguracja: WKWebViewConfiguration) {
        let kontroler = konfiguracja.userContentController
        guard !kontrolery.contains(kontroler) else { return }
        kontrolery.add(kontroler)
        kontroler.addUserScript(WKUserScript(source: Self.skrypt, injectionTime: .atDocumentStart,
                                             forMainFrameOnly: true, in: .page))
        kontroler.add(self, contentWorld: .page, name: "liveplConnect")
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        // tylko sama strona Connect (główna ramka), nie reklamy ani obce ramki
        guard message.frameInfo.isMainFrame,
              message.frameInfo.securityOrigin.host == Self.host,
              let dane = message.body as? [String: Any],
              let co = dane["co"] as? String
        else { return }
        let wlaczony = dane["wlaczony"] as? Bool ?? false
        switch co {
        case "ekran": UIApplication.shared.isIdleTimerDisabled = wlaczony
        case "wibracja": wibracja.impactOccurred()
        case "pelny": pelnyEkran = wlaczony
        default: break
        }
    }

    /// Strona ładuje się od nowa (odświeżenie, błąd sieci): sesja się skończyła, telefon znowu może gasnąć.
    func koniecSesji() {
        UIApplication.shared.isIdleTimerDisabled = false
        pelnyEkran = false
    }

    private static let skrypt = #"""
    (() => {
      const most = window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.liveplConnect;
      if (!most) return;
      const wyslij = (w) => { try { most.postMessage(w); } catch (e) { /* aplikacja zamknięta */ } };
      const ustaw = (obiekt, nazwa, wartosc) =>
        Object.defineProperty(obiekt, nazwa, { configurable: true, writable: true, value: wartosc });

      // pełny ekran: iPhone nie daje go stronom, więc aplikacja chowa pasek stanu i kreskę na dole
      let pelny = null;
      const zmiana = () => document.dispatchEvent(new Event('fullscreenchange'));
      const wyjdz = () => {
        if (!pelny) return;
        pelny = null;
        wyslij({ co: 'pelny', wlaczony: false });
        zmiana();
      };
      ustaw(Element.prototype, 'requestFullscreen', function () {
        pelny = this;
        wyslij({ co: 'pelny', wlaczony: true });
        zmiana();
        return Promise.resolve();
      });
      ustaw(Document.prototype, 'exitFullscreen', function () { wyjdz(); return Promise.resolve(); });
      Object.defineProperty(Document.prototype, 'fullscreenElement', { configurable: true, get: () => pelny });
      Object.defineProperty(Document.prototype, 'fullscreenEnabled', { configurable: true, get: () => true });

      // ekran nie gaśnie w trakcie sesji. Strona prosi o blokadę na początku sesji i po powrocie do aplikacji,
      // a zwalnia ją na końcu sesji. Wtedy kończy się też pełny ekran (jego przycisk jest tylko w sesji).
      let biezaca = null;
      const wakeLock = {
        request() {
          if (biezaca) biezaca.released = true;
          const b = new EventTarget();
          Object.assign(b, { type: 'screen', released: false, onrelease: null });
          b.release = () => {
            if (!b.released) {
              b.released = true;
              if (biezaca === b) {
                biezaca = null;
                wyslij({ co: 'ekran', wlaczony: false });
                wyjdz();
              }
            }
            return Promise.resolve();
          };
          biezaca = b;
          wyslij({ co: 'ekran', wlaczony: true });
          return Promise.resolve(b);
        }
      };
      Object.defineProperty(Navigator.prototype, 'wakeLock', { configurable: true, get: () => wakeLock });

      // wibracja (np. przytrzymanie palca = prawy przycisk myszy): Safari na iPhonie jej nie ma, aplikacja tak
      ustaw(Navigator.prototype, 'vibrate', function () { wyslij({ co: 'wibracja' }); return true; });
    })();
    """#
}
