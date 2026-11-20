# LivePL i LivePL Connect: odpowiedź dla App Review (wytyczna 2.1, „Information Needed”)

20.11.2026 o 21:54 Apple zatrzymało LivePL 1.0 (budowa 24). To ten sam szablon co przy Poczcie: Apple nie znalazło
błędu, tylko prosi o nagranie i 6 punktów informacji. Te same informacje mają trafić do pola Notes
(App Review Information). Connect 1.0 (zgłoszony 19.11 o 23:50) dostał ten sam szablon; Karol pokazał go 20.11.
Notatki po angielsku są w metadane.json (notatkaLivePL, notatkaConnect).

Nagrania robi Karol na prawdziwym iPhonie. Przed każdym nagraniem:
- iPhone z najnowszym iOS (Ustawienia → Ogólne → Uaktualnienia).
- Włączone „Nie przeszkadzać”, żeby na nagraniu nie było prywatnych powiadomień.
- Przycisk „Nagrywanie ekranu” jest w Centrum sterowania (Ustawienia → Centrum sterowania).
- Nagranie dołączasz w App Store Connect: App Review → zgłoszenie → „Reply to App Review”, ikona spinacza.

## LivePL: nagranie (1,5–2 minuty)

1. Zacznij nagrywanie na ekranie głównym i stuknij ikonę LivePL. Nagranie ma się zacząć od uruchomienia aplikacji.
2. Ekran Start: pokaż programy (Poczta, Live Production, TVML) i przewiń w dół.
3. Stuknij TVML, otwórz jeden artykuł.
4. Wróć na Start i stuknij Live Production. Pokaż, że każdy program ma swoją kartę.
5. W polu „Adres albo szukaj” wpisz `bbc.com` i otwórz stronę.
6. Menu → „Przetłumacz na polski”: strona zmienia się na polską. Potem „Pokaż oryginał”.
7. Menu → „Dodaj do Moich stron”, wróć na Start i pokaż ją tam.
8. Otwórz listę kart, przełącz się na inną kartę i jedną zamknij.
9. Menu: „Znajdź na stronie” (wpisz jakieś słowo), potem „Udostępnij” i zamknij arkusz.
10. Przełącz wygląd: dzień, noc, automatycznie.
11. Otwórz „Urządzenie LivePL” (rejestracja telefonu w urzędzie) i pokaż stan.
12. Wyjdź na ekran główny i zakończ nagrywanie.

Logowanie do poczty nie jest potrzebne. Na nagraniu nie otwieraj własnej skrzynki ani prywatnych stron.

## LivePL: odpowiedź (wklejana w „Reply to App Review”)

Hello,

Thank you for the review. Below is the information you asked for. The same information is now in the Notes field of the App Review Information section.

1. Screen recording
The attached screen recording was made on a physical iPhone with the latest iOS. It starts by launching the app from the Home Screen and shows: the Start screen with the Live Production programs, opening the TVML news site and the company website in their own tabs, opening a foreign-language page from the "Address or search" field, translating it into Polish on the device and showing the original again, adding the page to "My pages", the list of open tabs, the browser menu (share, find on page, translate, open in Safari), the light and dark appearance and the device registration screen with the key in the Secure Enclave.
LivePL has no accounts of its own: there is no registration and no sign-in to the app, so there is also no account deletion. Websites opened in the app (for example the webmail) have their own sign-in pages, and no sign-in is needed to review the app. The app has no user-generated content of its own and no paid content.

2. Purpose and target audience
LivePL is the web browser of Live Production, a company in Leszno, Poland. Live Production runs the LIVEPL web hosting and domain service (https://livepl.eu), the local news service TVML (https://tvml.pl) and other web services. A desktop version of LivePL for Windows already exists; this app brings the same browser to iPhone and iPad.
The audience is LIVEPL hosting customers and their staff, Live Production staff, and local readers who want the company's services in one place.
Instead of bookmarks in Safari, the Start screen gives one-tap access to the company programs (webmail, company website, TVML news). Each program opens in its own tab that remembers where you left off. Any other site can be added to "My pages".
Pages in other languages can be translated into Polish on the device with Apple's Translation framework, so the page text is not sent anywhere. The app trusts certificates of the LivePL certificate authority used on company sites, without installing a configuration profile. The device can also be registered in the LivePL certificate authority with a key kept in the Secure Enclave.
There are no ads and no tracking.

3. Setup and access
No setup and no sign-in are needed. Open the app: the Start screen shows the company programs and the "Address or search" field. Tap a program (for example TVML) to open it in its own tab, or type any address.
To try the translator, open a page in another language (for example bbc.com) and tap "Przetłumacz na polski" (Translate into Polish) in the menu.

4. External services
- Websites the user opens. The built-in programs are Live Production's own sites: poczta.livepl.eu (webmail on our own server), liveproduction.pl and tvml.pl.
- Google Search: text typed into the "Address or search" field that is not an address is opened as a search on https://www.google.com, as in other browsers.
- The LivePL certificate authority (cert.livepl.eu), run by Live Production. It issues the device certificate for a key that stays in the Secure Enclave. The app also contains our public root certificate, so it can trust company sites.
- Apple's iTunes Search API, used only to check whether our Poczta LivePL app is available in the App Store before suggesting it.
- On the device, only Apple frameworks: WebKit, Translation (on-device) and Secure Enclave.
The app uses no third-party analytics, advertising, AI, payment or sign-in services.

5. Regional differences
The app works the same in all regions. The app interface is in Polish, because Live Production and its services are Polish. Websites appear in their own language.

6. Regulated industry or third-party material
Not applicable. Like other browsers, the app only displays the websites the user opens.

Best regards,
Karol Kowalski
Live Production

## Connect: nagranie (1,5–2 minuty)

Do nagrania potrzebny jest komputer z programem LivePL Connect: komputer testowy dla Apple albo Twój. Na nagraniu
nie pokazuj prywatnych danych z ekranu komputera ani hasła w czytelnej postaci.

1. Zacznij nagrywanie na ekranie głównym i stuknij ikonę LivePL Connect.
2. Wpisz ID komputera i hasło, stuknij „Połącz”.
3. Pokaż ekran komputera. W trybie dotyku: stuknięcie (kliknięcie), przytrzymanie (prawy przycisk), przewijanie jednym palcem, powiększanie dwoma.
4. Przełącz na tryb myszy i przesuń kursor jak na touchpadzie.
5. Otwórz klawiaturę, wpisz kilka liter (np. w Notatniku) i użyj paska klawiszy (Esc, strzałki).
6. Stuknij „Dopasuj”, obróć telefon do poziomu.
7. Rozłącz się. Pokaż ostatnie połączenia na ekranie startowym, wyjdź na ekran główny i zakończ nagrywanie.

## Connect: odpowiedź (wklejana w „Reply to App Review”)

Hello,

Thank you for the review. Below is the information you asked for. The same information is now in the Notes field of the App Review Information section.

1. Screen recording
The attached screen recording was made on a physical iPhone with the latest iOS. It starts by launching the app from the Home Screen and shows: entering the ID and password of a Windows computer running the LivePL Connect program, connecting, the computer's screen on the phone with the notice on the computer that a session is active, touch control (tap to click, long press for right click, scroll, pinch to zoom), typing with the phone keyboard and the key bar, landscape view, disconnecting and the list of recent connections.
The recording connects to our test computer with exactly the ID and password that we entered in the Sign-In Information section. We show them on purpose, so you can see that these credentials work.
LivePL Connect has no accounts: there is no registration, no sign-in to the app and therefore no account deletion. Each connection is authorized only by the password shown on (or set in) the program on the computer the user connects to. There is no user-generated content and no paid content.

2. Purpose and target audience
LivePL Connect is the remote desktop app of Live Production, a company in Leszno, Poland, that runs the LIVEPL hosting service (https://livepl.eu). Live Production technicians and staff use it to help customers, and users use it to reach their own computers.
On the computer, the free LivePL Connect program for Windows (open source, MIT license, https://app.livepl.eu) shows an ID and a password. With this app you enter them on an iPhone or iPad, see the whole screen of that computer and control it.
It is a generic remote desktop client that mirrors the entire host screen, not specific software or services. Screen and control data are encrypted end to end between the devices (WebRTC with DTLS). They usually go directly between the devices; when the network does not allow that, they go through a LivePL relay that cannot read them.
There are no ads and no tracking.

3. Setup and access
Our test computer runs 24/7 with a fixed ID. In the app, enter the ID from the "User name" field and the password from the "Password" field of the Sign-In Information section, then tap "Połącz" (Connect). You will see the Windows desktop of the test computer and can control it with touch or mouse mode.
The app asks no permissions; it only keeps the screen awake during a session.

4. External services
- connect.livepl.eu, the connection server run by Live Production. It only matches the two devices by ID and never sees screen content; the password is checked by the program on the computer.
- A TURN relay run by Live Production. It is used only when a direct connection is not possible, and it carries encrypted data it cannot read.
- Google's public STUN server (stun.l.google.com). It is used only to find the device's public network address for a direct connection; no screen content goes there.
- The LivePL certificate authority (cert.livepl.eu), run by Live Production, for the device certificate whose key stays in the Secure Enclave.
- On the device, only Apple frameworks: WebKit (including WebRTC) and Secure Enclave.
The app uses no third-party analytics, advertising, AI, payment or sign-in services.

5. Regional differences
The app works the same in all regions. The interface is in Polish, because Live Production and its services are Polish.

6. Regulated industry or third-party material
Not applicable. The app does not belong to a regulated industry and contains no protected third-party material. Users see only the screen of a computer whose password they were given.

Best regards,
Karol Kowalski
Live Production
