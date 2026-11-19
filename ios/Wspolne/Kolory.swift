import SwiftUI
import UIKit

extension UIColor {
    convenience init(rgb: UInt32, alfa: CGFloat = 1) {
        self.init(red: CGFloat((rgb >> 16) & 0xff) / 255,
                  green: CGFloat((rgb >> 8) & 0xff) / 255,
                  blue: CGFloat(rgb & 0xff) / 255,
                  alpha: alfa)
    }

    /// Kolor na dzień i na noc, jak w LivePL na Windows („Reżyserka”).
    static func livepl(_ dzien: UInt32, _ noc: UInt32, alfaDzien: CGFloat = 1, alfaNoc: CGFloat = 1) -> UIColor {
        UIColor { cechy in
            cechy.userInterfaceStyle == .dark
                ? UIColor(rgb: noc, alfa: alfaNoc)
                : UIColor(rgb: dzien, alfa: alfaDzien)
        }
    }
}

/// Te same kolory co w styl.css wersji webowej i w powloka.css na Windows.
enum Kolor {
    static let tlo = Color(uiColor: .livepl(0xe9ecef, 0x0d0f12))
    static let karta = Color(uiColor: .livepl(0xffffff, 0x16191e))
    static let tekst = Color(uiColor: .livepl(0x1d2733, 0xe6e9ee))
    static let slaby = Color(uiColor: .livepl(0x6b7886, 0x8b95a1))
    static let bardzoSlaby = Color(uiColor: .livepl(0x98a3ae, 0x5e6874))
    static let ramka = Color(uiColor: .livepl(0xd3d9df, 0x252a31))
    static let wcisniety = Color(uiColor: .livepl(0x142332, 0xffffff, alfaDzien: 0.09, alfaNoc: 0.1))
    static let czerwony = Color(uiColor: .livepl(0xb0261f, 0xe3463a))
    static let czerwonyTlo = Color(uiColor: .livepl(0xb0261f, 0xe3463a, alfaDzien: 0.08, alfaNoc: 0.12))
    static let ok = Color(uiColor: .livepl(0x1f8a4c, 0x45c486))
}

extension View {
    /// Biała (nocą grafitowa) karta z cienką ramką, jak kafelki w Reżyserce.
    func kartaLivePL(promien: CGFloat = 16) -> some View {
        background(Kolor.karta, in: RoundedRectangle(cornerRadius: promien, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: promien, style: .continuous).stroke(Kolor.ramka, lineWidth: 1))
    }
}

extension Bundle {
    /// np. „0.1.0 (12)”
    var wersjaLivePL: String {
        let wersja = infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let budowa = infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(wersja) (\(budowa))"
    }
}
