# Poczta LivePL: odpowiedź dla App Review (wytyczna 2.1, „Information Needed”)

20.11.2026 Apple zatrzymało Poczty LivePL 1.0 (budowa 23). Nie znalazło błędu, tylko prosi o informacje.
Odpowiedź musi mieć wszystkie 6 punktów, a te same informacje mają trafić do pola Notes (App Review Information).
Punkt 1 to nagranie ekranu z prawdziwego iPhone'a. Musi je zrobić Karol.

## Nagranie (Karol, na iPhonie)

Przed nagraniem:
- iPhone z najnowszym iOS (Ustawienia → Ogólne → Uaktualnienia).
- Włącz „Nie przeszkadzać”, żeby na nagraniu nie było prywatnych powiadomień.
- W Poczcie LivePL wyloguj się, żeby nagranie pokazało logowanie.
- Jeśli w Centrum sterowania nie ma przycisku nagrywania ekranu: Ustawienia → Centrum sterowania → dodaj „Nagrywanie ekranu”.

Nagranie (1–2 minuty, tylko konto demo, to samo co w App Store Connect):
1. Zacznij nagrywanie na ekranie głównym i stuknij ikonę Poczty LivePL. Nagranie ma się zacząć od uruchomienia aplikacji.
2. Zaloguj się kontem demo (hasło jest ukryte kropkami).
3. Pokaż skrzynkę odbiorczą i otwórz jedną wiadomość.
4. Napisz nową wiadomość do konta demo, dołącz zdjęcie z telefonu i wyślij.
5. Otwórz tę wiadomość, stuknij załącznik (podgląd) i pokaż „Zapisz w Plikach” albo udostępnianie.
6. Zostaw jedną wiadomość nieprzeczytaną, wyjdź na ekran główny i pokaż liczbę na ikonie. Za pierwszym razem iOS zapyta o zgodę na plakietkę: zezwól.
7. Wróć do aplikacji, wyloguj się i zakończ nagrywanie.

Nagranie dołączasz do odpowiedzi w App Store Connect (App Review → zgłoszenie → „Reply to App Review”, ikona spinacza).

## Odpowiedź (wklejana w „Reply to App Review”)

Hello,

Thank you for the review. Below is the information you asked for. The same information is now in the Notes field of the App Review Information section.

1. Screen recording
The attached screen recording was made on a physical iPhone with the latest iOS. It starts by launching the app from the Home Screen and shows: signing in with the demo account, the inbox, writing a message with a photo from the phone attached, sending it, opening the received message, saving the attachment through the iOS share sheet, and signing out in Settings.
The app has no account registration, so it also has no account deletion: mailboxes are not created in the app (see point 2). There is no public user-generated content and no paid content.

2. Purpose and target audience
Poczta LivePL is the email app for customers of LIVEPL (https://livepl.eu), the web hosting and domain service of Live Production, a company in Leszno, Poland. LIVEPL hosting plans include business mailboxes on the customer's own domain, which the customers and their staff use for everyday work. The app works only with mailboxes hosted on the LIVEPL mail server; it is not a general email client for other providers.
Until now, these users could only use the webmail (poczta.livepl.eu) in Safari. The app gives them their mailbox as a separate app with its own icon: the unread count on the app icon, a sign-in that stays valid for 90 days of use, attachments that open in a preview and can be saved to Files or shared, photo attachments from the phone, and links from messages that open in the browser while the mailbox stays in place. The device also gets a certificate whose key is kept in the Secure Enclave.
Mailboxes are created by the hosting customer in the LIVEPL client panel after ordering a plan on livepl.eu, and they are deleted there as well. The app does not sell anything and does not link to any purchase.

3. Setup and access
No setup is needed. Open the app and sign in with the demo account from the Sign-In Information section. After signing in you see the inbox, where you can read, reply, write messages, add attachments and use folders. The app asks only for permission to show the unread count on its icon (badge only, no notifications).

4. External services
- The LIVEPL mail server (livepl.eu), run by Live Production: IMAP and SMTP.
- Afterlogic WebMail Pro: webmail software installed on our own server at poczta.livepl.eu, shown in the app with WKWebView.
- The LivePL certificate authority (cert.livepl.eu), run by Live Production: it issues the device certificate for a key that stays in the Secure Enclave.
- On the device, only Apple frameworks (WebKit, UserNotifications for the badge, Secure Enclave).
The app uses no third-party analytics, advertising, AI, payment or sign-in services.

5. Regional differences
The app works the same in all regions. The webmail interface follows the device language (for example English or Polish). The App Store description is in Polish because LIVEPL is a Polish service.

6. Regulated industry or third-party material
Not applicable. The app does not belong to a regulated industry and contains no protected third-party material. Users see only their own email.

Best regards,
Karol Kowalski
Live Production
