import SwiftUI
import UIKit
import UserNotifications

/// Temat powiadomień z config.push.topics: urgent („Pilne”), działy, gminy jako county:<slug>.
struct TematPush: Identifiable, Hashable {
    let id: String
    let tytul: String
    let domyslny: Bool
    var gmina: Bool { id.hasPrefix("county:") }

    init?(_ j: Slownik) {
        guard !j.s("id").isEmpty else { return nil }
        id = j.s("id")
        tytul = j.s("title").isEmpty ? j.s("id") : j.s("title")
        domyslny = j.b("default")
    }
}

/// Powiadomienia o nowych wpisach. Serwer tvml.pl wysyła je przez APNs bezpośrednio (bez Firebase).
/// - TVML 2.2 (Karol 24.11: „domyślnie powinna być funkcja włączona, bo ludzie nie wiedzą, że muszą włączyć 🙂 na wszystkie
///   tematy”, „daj przy pierwszym uruchomieniu czy włączyć powiadomienia i opcje tak o nie”): przy pierwszym uruchomieniu
///   (także po aktualizacji, gdy nikt jeszcze nie pytał) raz nasze okno z „Tak” i „Nie”. „Tak” → systemowe pytanie o zgodę,
///   po „Pozwól” wszystkie tematy z panelu. „Nie” → nic, bez systemowego pytania (zostaje na później, przełącznik w Więcej).
///   Po „Nie pozwalaj” nic nie wysyłamy; w „Więcej → Powiadomienia” jest wtedy przycisk do Ustawień iOS.
/// - Przy pierwszym włączeniu (pytanie przy starcie albo przełącznik) zaznaczone są wszystkie tematy; każdy można odznaczyć.
/// - Token APNs (hex) z tematami, językiem i środowiskiem idzie do POST /api/v1/devices; wyłączenie
///   albo odznaczenie wszystkich tematów → POST /api/v1/devices/delete.
/// - Dotknięcie powiadomienia otwiera artykuł z userInfo["articleId"].
/// Uprawnienie aps-environment: project.yml (entitlements), profil „TVML App Store” w sekrecie APPLE_PROFIL_TVML.
@MainActor
final class Powiadomienia: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = Powiadomienia()

    private enum Klucz {
        static let wlaczone = "tvml-push-wlaczone"
        static let tematy = "tvml-push-tematy"
        static let token = "tvml-push-token"
        static let zapytano = "tvml-push-zapytano"
    }

    @Published private(set) var wlaczone = UserDefaults.standard.bool(forKey: Klucz.wlaczone)
    @Published private(set) var tematy = Set(UserDefaults.standard.stringArray(forKey: Klucz.tematy) ?? [])
    /// Zgoda cofnięta w Ustawieniach iOS: ekran pokazuje podpowiedź i przycisk do Ustawień.
    @Published private(set) var brakZgody = false
    /// Artykuł do otwarcia po dotknięciu powiadomienia; Glowny otwiera go i czyści.
    @Published var doOtwarcia: Int?
    /// Ostatni błąd rejestracji (pokazany pod przełącznikiem), nil = w porządku.
    @Published private(set) var blad: String?

    private var token = UserDefaults.standard.string(forKey: Klucz.token)
    private var wysylka: Task<Void, Never>?

    /// Środowisko APNs: budowy deweloperskie sandbox, TestFlight i App Store production.
    static var srodowisko: String {
        #if DEBUG
        return "sandbox"
        #else
        return "production"
        #endif
    }

    // MARK: włączanie i tematy

    /// Włączenie przełącznikiem: zgoda iOS, przy pierwszym razie tematy domyślne, rejestracja w APNs.
    func wlacz(dostepne: [TematPush]) async {
        let zgoda = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        guard zgoda else {
            brakZgody = true
            ustaw(wlaczone: false)
            return
        }
        brakZgody = false
        if UserDefaults.standard.stringArray(forKey: Klucz.tematy) == nil {
            tematy = Set(dostepne.map(\.id))
            zapiszTematy()
        }
        ustaw(wlaczone: true)
        UIApplication.shared.registerForRemoteNotifications()
        zglos()
    }

    func wylacz() {
        ustaw(wlaczone: false)
        wysylka?.cancel()
        blad = nil
        guard let token else { return }
        Task { _ = try? await Siec.v1Wyslij("devices/delete", ["token": token]) }
    }

    func ustaw(_ temat: TematPush, _ zaznaczony: Bool) {
        if zaznaczony { tematy.insert(temat.id) } else { tematy.remove(temat.id) }
        zapiszTematy()
        zglos()
    }

    /// Nasze okno „Włączyć powiadomienia o nowych wpisach?” (PytanieOPowiadomienia) przy pierwszym uruchomieniu.
    @Published var pytanieStartowe = false

    /// Pierwsze uruchomienie (po wczytaniu tematów z panelu): raz nasze okno z „Tak” i „Nie”, tylko gdy iOS jeszcze
    /// nie pytał o zgodę. Kto już odmówił albo sam wyłączył powiadomienia w aplikacji, tego nie pytamy.
    func zapytajRaz(dostepne: [TematPush]) async {
        #if targetEnvironment(simulator)
        // zrzuty ekranu w CI: bez okna nad aplikacją (osobny zrzut: -ekran pytanie-push)
        return
        #else
        guard !UserDefaults.standard.bool(forKey: Klucz.zapytano), !wlaczone, !dostepne.isEmpty else { return }
        guard await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .notDetermined else {
            UserDefaults.standard.set(true, forKey: Klucz.zapytano)
            return
        }
        pytanieStartowe = true
        #endif
    }

    /// Odpowiedź w naszym oknie (Karol 24.11: „daj przy pierwszym uruchomieniu czy włączyć powiadomienia i opcje tak o nie”).
    /// „Tak”: systemowe pytanie iOS, po zgodzie wszystkie tematy. „Nie”: nic, a systemowe pytanie zostaje na później
    /// (przełącznik w Więcej → Powiadomienia).
    func odpowiedz(tak: Bool, dostepne: [TematPush]) async {
        pytanieStartowe = false
        UserDefaults.standard.set(true, forKey: Klucz.zapytano)
        if tak { await wlacz(dostepne: dostepne) }
    }

    /// Start aplikacji: gdy włączone, sprawdź zgodę i odśwież token (Apple może go zmienić).
    func przyStarcie() async {
        guard wlaczone else { return }
        let ustawienia = await UNUserNotificationCenter.current().notificationSettings()
        brakZgody = ustawienia.authorizationStatus == .denied
        if !brakZgody { UIApplication.shared.registerForRemoteNotifications() }
    }

    private func ustaw(wlaczone: Bool) {
        self.wlaczone = wlaczone
        UserDefaults.standard.set(wlaczone, forKey: Klucz.wlaczone)
    }

    private func zapiszTematy() {
        UserDefaults.standard.set(Array(tematy).sorted(), forKey: Klucz.tematy)
    }

    // MARK: rejestracja na serwerze

    /// Token z APNs (AppDelegate): 32 bajty jako hex małymi literami.
    func otrzymano(token dane: Data) {
        let hex = dane.map { String(format: "%02x", $0) }.joined()
        token = hex
        UserDefaults.standard.set(hex, forKey: Klucz.token)
        zglos()
    }

    func nieUdaloSie(_ e: Error) {
        blad = e.localizedDescription
    }

    /// Zgłoszenie urządzenia (po tokenie, zmianie tematów albo języka). Serwer nadpisuje wpis po tokenie;
    /// kilka przełączeń z rzędu daje jedno zgłoszenie (limit 120 na godzinę z jednego adresu).
    func zglos() {
        guard wlaczone, let token else { return }
        wysylka?.cancel()
        let wybrane = Array(tematy).sorted()
        wysylka = Task {
            try? await Task.sleep(nanoseconds: 700_000_000)
            guard !Task.isCancelled else { return }
            if wybrane.isEmpty {
                _ = try? await Siec.v1Wyslij("devices/delete", ["token": token])
                return
            }
            let cialo: [String: Any] = [
                "platform": "ios", "token": token, "topics": wybrane,
                "appVersion": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
                "env": Powiadomienia.srodowisko, "lang": Jezyk.biezacy
            ]
            do {
                let (kod, j) = try await Siec.v1Wyslij("devices", cialo)
                let komunikat = j.slownik("error").s("message")
                blad = kod == 200 ? nil : (komunikat.isEmpty ? "HTTP \(kod)" : komunikat)
            } catch {
                blad = error.localizedDescription
            }
        }
    }

    // MARK: UNUserNotificationCenterDelegate

    /// Powiadomienie przy otwartej aplikacji: baner jak poza nią.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    /// Dotknięcie powiadomienia: artykuł z articleId (liczba albo tekst).
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let dane = response.notification.request.content.userInfo
        let id: Int? = (dane["articleId"] as? NSNumber)?.intValue ?? (dane["articleId"] as? String).flatMap { Int($0) }
        guard let id, id > 0 else { return }
        await MainActor.run { Powiadomienia.shared.doOtwarcia = id }
    }
}

/// Zdarzenia UIKit potrzebne do push: delegat powiadomień i token z APNs.
@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = Powiadomienia.shared
        return true
    }

    /// iPhone: aplikacja w pionie, film na pełnym ekranie w poziomie (OdtwarzaczFilmu.swift, Orientacja).
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        Orientacja.maska
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Powiadomienia.shared.otrzymano(token: deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Powiadomienia.shared.nieUdaloSie(error)
    }
}

/// Okno przy pierwszym uruchomieniu: „Włączyć powiadomienia o nowych wpisach?” z „Tak” i „Nie” (klucze wspólne z Androidem:
/// app.pushAskTitle, app.pushAskText, app.yes, app.no; polskie teksty wbudowane jako zapas).
struct PytanieOPowiadomienia: View {
    @EnvironmentObject private var m: Magazyn
    @ObservedObject private var p = Powiadomienia.shared
    @State private var pracuje = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            VStack(spacing: 14) {
                Image(systemName: "bell.badge.fill").font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(Barwy.zloto).accessibilityHidden(true)
                Text(Napisy.t("app.pushAskTitle", "Włączyć powiadomienia o nowych wpisach?"))
                    .font(Czcionka.naglowek(21)).foregroundStyle(Barwy.tekst).multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text(Napisy.t("app.pushAskText", "Pilne informacje i nowe artykuły z Leszna i powiatu. Tematy zmienisz w Więcej → Powiadomienia."))
                    .czcionka(15).foregroundStyle(Barwy.slaby).multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 12) {
                    przycisk(Napisy.t("app.no", "Nie"), glowny: false) { await p.odpowiedz(tak: false, dostepne: m.tematyPush) }
                    przycisk(Napisy.t("app.yes", "Tak"), glowny: true) { await p.odpowiedz(tak: true, dostepne: m.tematyPush) }
                }
                .padding(.top, 4)
                .disabled(pracuje)
            }
            .padding(22)
            .frame(maxWidth: 380)
            .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 22))
            .padding(24)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
        }
    }

    private func przycisk(_ tytul: String, glowny: Bool, akcja: @escaping () async -> Void) -> some View {
        Button {
            pracuje = true
            Task { await akcja(); pracuje = false }
        } label: {
            Text(tytul).czcionka(16, .bold)
                .frame(maxWidth: .infinity, minHeight: 48)
                .foregroundStyle(glowny ? Color(hex: "#1d1400") : Barwy.tekst)
                .background(glowny ? Barwy.zloto : Barwy.tlo, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(glowny ? Color.clear : Barwy.ramka))
        }
        .buttonStyle(.plain)
    }
}

/// Więcej → Powiadomienia: główny przełącznik, tematy z panelu (gminy w osobnej sekcji).
struct PowiadomieniaWidok: View {
    @EnvironmentObject private var m: Magazyn
    @ObservedObject private var p = Powiadomienia.shared
    @Environment(\.openURL) private var otworzAdres
    @Environment(\.dismiss) private var wroc
    @State private var pracuje = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { wroc() } label: {
                HStack(spacing: 2) { Image(systemName: "chevron.left"); Text(Napisy.t("app.back", "Wróć")) }
                    .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
            }
            .padding(.horizontal, 16)
            Text(Napisy.t("app.notifications", "Powiadomienia")).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                .padding(.horizontal, 16)
            lista
        }
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var lista: some View {
        List {
            Section {
                Toggle(Napisy.t("app.pushEnable", "Powiadomienia o nowych wpisach"), isOn: Binding(
                    get: { p.wlaczone },
                    set: { nowe in
                        if nowe {
                            pracuje = true
                            Task { await p.wlacz(dostepne: m.tematyPush); pracuje = false }
                        } else {
                            p.wylacz()
                        }
                    }))
                .disabled(pracuje)
            } footer: {
                if p.brakZgody {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(Napisy.t("app.pushDenied", "Powiadomienia dla TVML są wyłączone w ustawieniach iPhone'a."))
                        Button(Napisy.t("app.openSettings", "Otwórz Ustawienia")) {
                            if let u = URL(string: UIApplication.openSettingsURLString) { otworzAdres(u) }
                        }
                    }
                } else if let blad = p.blad, p.wlaczone {
                    Text(Napisy.t("app.pushError", "Nie udało się włączyć powiadomień:") + " " + blad)
                } else {
                    Text(Napisy.t("app.pushHint", "Wybierz, o czym chcesz dostawać powiadomienia. Możesz to zmienić w każdej chwili."))
                }
            }
            if p.wlaczone {
                let zwykle = m.tematyPush.filter { !$0.gmina }
                let gminy = m.tematyPush.filter(\.gmina)
                if !zwykle.isEmpty {
                    Section(Napisy.t("app.pushTopics", "Tematy")) {
                        ForEach(zwykle) { t in przelacznik(t) }
                    }
                }
                if !gminy.isEmpty {
                    Section(Napisy.t("app.pushCounties", "Gminy")) {
                        ForEach(gminy) { t in przelacznik(t) }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
    }

    private func przelacznik(_ t: TematPush) -> some View {
        Toggle(t.tytul, isOn: Binding(get: { p.tematy.contains(t.id) }, set: { p.ustaw(t, $0) }))
    }
}
