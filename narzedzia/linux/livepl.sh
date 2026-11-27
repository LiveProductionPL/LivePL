#!/bin/sh
# LivePL na Linuxa: polecenie /usr/bin/livepl (pakiet .deb) i AppRun (plik AppImage).
#   livepl [adres albo plik .livepl-dodatek]   uruchamia LivePL (drugie uruchomienie tylko pokazuje okno)
#   livepl --ai-most                            most MCP dla aplikacji AI (Claude Code i inne), zob. src/ai-most.js
if [ -n "$APPDIR" ] && [ -x "$APPDIR/opt/LivePL/livepl" ]; then
  KATALOG="$APPDIR/opt/LivePL"
else
  KATALOG=/opt/LivePL
fi

# Most AI: z aktualizacji w folderze użytkownika, jeśli jest (zob. src/nakladka-linux.js), inaczej wbudowany
if [ "$1" = "--ai-most" ]; then
  DANE="${LIVEPL_DANE:-${XDG_CONFIG_HOME:-$HOME/.config}/LivePL}"
  MOST="$DANE/program/src/ai-most.js"
  [ -f "$MOST" ] || MOST="$KATALOG/resources/app/src/ai-most.js"
  ELECTRON_RUN_AS_NODE=1 exec "$KATALOG/livepl" "$MOST"
fi

# Ubuntu 23.10 i nowsze (AppArmor) blokują piaskownicę Chromium w programach bez własnego profilu, a plik
# AppImage nie może go mieć. Bez piaskownicy strony nie byłyby od siebie odizolowane, więc zamiast przeglądarki
# LivePL pokazuje tylko okienko systemu z prośbą o instalację z pakietu .deb (ten ma profil AppArmor).
if [ -n "$APPIMAGE" ] && [ "$(cat /proc/sys/kernel/apparmor_restrict_unprivileged_userns 2>/dev/null)" = "1" ]; then
  exec "$KATALOG/livepl" --no-sandbox --livepl-bez-piaskownicy
fi

exec "$KATALOG/livepl" "$@"
