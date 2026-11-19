import UIKit

/// Okienka systemowe dla stron (alert, confirm, prompt) i arkusz „Udostępnij” dla pobranych plików.
@MainActor
enum Okna {
    static func najwyzszy() -> UIViewController? {
        let sceny = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scena = sceny.first { $0.activationState == .foregroundActive } ?? sceny.first
        let okno = scena?.windows.first { $0.isKeyWindow } ?? scena?.windows.first
        var vc = okno?.rootViewController
        while let wyzej = vc?.presentedViewController, !wyzej.isBeingDismissed {
            vc = wyzej
        }
        return vc
    }

    static func komunikat(_ tytul: String?, _ tresc: String, gotowe: @escaping () -> Void) {
        guard let vc = najwyzszy() else { gotowe(); return }
        let okno = UIAlertController(title: tytul, message: tresc, preferredStyle: .alert)
        okno.addAction(UIAlertAction(title: "OK", style: .default) { _ in gotowe() })
        vc.present(okno, animated: true)
    }

    static func potwierdzenie(_ tytul: String?, _ tresc: String, gotowe: @escaping (Bool) -> Void) {
        guard let vc = najwyzszy() else { gotowe(false); return }
        let okno = UIAlertController(title: tytul, message: tresc, preferredStyle: .alert)
        okno.addAction(UIAlertAction(title: "Anuluj", style: .cancel) { _ in gotowe(false) })
        okno.addAction(UIAlertAction(title: "OK", style: .default) { _ in gotowe(true) })
        vc.present(okno, animated: true)
    }

    static func pytanie(_ tytul: String?, _ tresc: String, domyslnie: String?, gotowe: @escaping (String?) -> Void) {
        guard let vc = najwyzszy() else { gotowe(nil); return }
        let okno = UIAlertController(title: tytul, message: tresc, preferredStyle: .alert)
        okno.addTextField { $0.text = domyslnie }
        okno.addAction(UIAlertAction(title: "Anuluj", style: .cancel) { _ in gotowe(nil) })
        okno.addAction(UIAlertAction(title: "OK", style: .default) { [weak okno] _ in
            gotowe(okno?.textFields?.first?.text ?? "")
        })
        vc.present(okno, animated: true)
    }

    static func udostepnij(_ elementy: [Any]) {
        guard let vc = najwyzszy() else { return }
        let arkusz = UIActivityViewController(activityItems: elementy, applicationActivities: nil)
        if let dymek = arkusz.popoverPresentationController {
            // iPad pokazuje arkusz jako dymek, więc potrzebuje miejsca zaczepienia
            dymek.sourceView = vc.view
            dymek.sourceRect = CGRect(x: vc.view.bounds.midX, y: vc.view.bounds.maxY - 80, width: 1, height: 1)
            dymek.permittedArrowDirections = []
        }
        vc.present(arkusz, animated: true)
    }
}
