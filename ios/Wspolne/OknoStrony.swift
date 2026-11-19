import SwiftUI

/// Okno otwarte przez stronę w Poczcie albo w Connect (podgląd, logowanie, link), z przyciskiem „Gotowe”.
struct OknoStrony: View {
    @EnvironmentObject private var p: Przegladarka
    @ObservedObject var karta: Karta

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                WebWidok(webView: karta.webView)
                if karta.laduje { PasekPostepu(postep: karta.postep) }
            }
            .navigationTitle(karta.tytul.isEmpty ? Adres.host(karta.adres) : karta.tytul)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gotowe") { p.zamknij(karta) }
                }
                ToolbarItem(placement: .topBarLeading) {
                    if let url = karta.adres {
                        ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                    }
                }
            }
        }
    }
}
