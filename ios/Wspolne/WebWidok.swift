import SwiftUI
import WebKit

// Wspólne dla LivePL i Poczty LivePL: osadzony WKWebView, pasek ładowania i widok błędu strony.

/// Cienki czerwony pasek ładowania pod paskiem adresu.
struct PasekPostepu: View {
    let postep: Double

    var body: some View {
        GeometryReader { miejsce in
            Rectangle()
                .fill(Kolor.czerwony)
                .frame(width: miejsce.size.width * max(0.08, min(1, postep)), height: 2)
                .animation(.easeOut(duration: 0.25), value: postep)
        }
        .frame(height: 2)
        .allowsHitTesting(false)
    }
}

struct WidokBledu: View {
    let blad: BladStrony
    let ponow: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 42))
                .foregroundStyle(Kolor.czerwony)
            Text("Nie udało się otworzyć strony")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Kolor.tekst)
            Text(blad.opis)
                .multilineTextAlignment(.center)
                .foregroundStyle(Kolor.slaby)
            if let url = blad.adres {
                Text(Adres.host(url))
                    .font(.footnote)
                    .foregroundStyle(Kolor.bardzoSlaby)
            }
            Button("Spróbuj ponownie", action: ponow)
                .buttonStyle(.borderedProminent)
                .padding(.top, 6)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Kolor.tlo)
    }
}

/// Osadza WKWebView karty. Ten sam WKWebView przechodzi między kontenerami, więc strona nie ładuje się od nowa.
struct WebWidok: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> UIView {
        let kontener = UIView()
        osadz(w: kontener)
        return kontener
    }

    func updateUIView(_ kontener: UIView, context: Context) {
        if webView.superview !== kontener { osadz(w: kontener) }
    }

    private func osadz(w kontener: UIView) {
        webView.removeFromSuperview()
        webView.translatesAutoresizingMaskIntoConstraints = false
        kontener.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: kontener.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: kontener.trailingAnchor),
            webView.topAnchor.constraint(equalTo: kontener.topAnchor),
            webView.bottomAnchor.constraint(equalTo: kontener.bottomAnchor)
        ])
    }
}
