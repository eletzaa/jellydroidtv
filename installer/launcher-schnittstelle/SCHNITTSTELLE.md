# Schnittstelle Installer → Launcher (setup.json, Version 1)

Der Installer schreibt per adb eine JSON-Datei nach
`/sdcard/Android/data/de.elias.launcher/files/setup.json`, beendet den Launcher
(`am force-stop de.elias.launcher`) und startet ihn neu (`am start -n de.elias.launcher/.MainActivity`).
Der Launcher liest die Datei einmal beim Start (`SetupImport.run(this)` in `MainActivity.onCreate`
vor `setContent`), schreibt die Werte in seine SharedPreferences und löscht die Datei.

Alle Felder sind optional. Fehlt ein Feld, bleibt der bisherige Wert.

```json
{
  "version": 1,
  "config": {
    "jellyfin_url": "https://jellyfin.example.org",
    "seerr_url": "https://jellyfin.example.org/requests",
    "theme": "DARK",                      // DARK | LIGHT | AUTO
    "jellyfin_app": "com.edde746.plezy",
    "place": 0,                           // Index in Places.all (für AUTO)
    "services_used": ["netflix", "disney"],
    "services_rows": ["netflix"],
    "airplay_enabled": true,              // AirPlay-Bildschirmsynchronisierung an/aus (ab V1.2)
    "airplay_name": "Jellydroid",         // Name, unter dem der Fernseher auf dem iPhone erscheint
    "sidebar_key": 176,                   // Tastencode, der die Sidebar öffnet (ab V1.3, sonst Menu/Settings)
    "font": "eigene"                      // ab V1.9: "standard" = Inter, "eigene" = Dateien aus files/schrift-neu/
  },
  "favorites": ["com.edde746.plezy", "com.netflix.ninja"],
  "jellyfin": { "device_id": "elias-launcher-…", "token": "…", "user_id": "…", "server_id": "…" },
  "seerr":    { "cookie": "connect.sid=…", "user_id": 3 },
  "homeassistant": {                      // ab V1.3
    "url": "http://…:8123",
    "client_id": "http://localhost:18234/", "refresh_token": "…",   // nach Anmeldung im Browser (Windows)
    "token": "…",                                                    // ODER langlebiger Token (Linux)
    "media_player": "media_player.fernseher",  // sein Bereich bestimmt den Raum der Sidebar
    "doorbell_popup": true,
    "doorbell_event": "event.haustuer_ding",   // Klingel-Ereignis (z. B. Ring Intercom)
    "door_button": "button.haustuer_oeffnen",   // Türöffner: button.* (press) oder lock.* (unlock)
    "default_room": "wohnzimmer"               // ab V1.8: Standardraum (area_id), leer = zuletzt gewählter
  }
}
```

- `jellyfin`: Ergebnis von Quick Connect, das der Installer am PC durchführt. Weil der Token an die
  DeviceId gebunden ist, erzeugt der Installer die DeviceId selbst und übergibt sie mit.
- `seerr`: Der Installer meldet sich am PC an (`POST {seerr_url}/api/v1/auth/jellyfin` oder `/auth/local`)
  und übergibt nur das Sitzungs-Cookie. Das Passwort kommt nie auf den Fernseher.
- Schlüssel für `services_*` entsprechen `Services.all[].key`.
- AirPlay: Damit der Empfänger das Vollbild auch aus dem Hintergrund öffnen darf, setzt der Installer nach
  der Installation `appops set de.elias.launcher SYSTEM_ALERT_WINDOW allow`.

- `homeassistant`: Windows meldet sich wie eine App an (Browser öffnet die HA-Anmeldung, Antwort an
  `http://localhost:<Port>/cb`), übergeben werden `client_id` und `refresh_token`; der Launcher holt sich damit
  selbst neue Zugriffstokens. Linux übergibt einen langlebigen Token. Ohne Anmeldung werden nur die Auswahlen
  übernommen, angemeldet wird dann am Fernseher per QR-Code. Die Adresse steht nur in dieser Datei, die der
  Launcher nach dem Lesen löscht. Die Sidebar braucht für den WLAN-Namen `pm grant de.elias.launcher android.permission.ACCESS_FINE_LOCATION`.

- `installer-stand.txt` (gleicher Ordner, eine Zeile `funktionen=airplay sidebar homeassistant`): schreibt der
  Installer, damit er beim nächsten Update weiß, welche Funktionen schon eingerichtet sind. Der Launcher muss
  sie nicht lesen und darf sie nicht löschen. Beim Update schickt der Installer eine setup.json, die nur die
  Felder der neu eingerichteten Funktionen enthält.

- `setup-ergebnis.txt` (ab V1.4, gleicher Ordner): schreibt der Launcher nach dem Lesen von setup.json, Inhalt
  `ok` oder `fehler: <Meldung>`. Der Installer liest und löscht sie und meldet Fehler im Protokoll.
- `config.sidebar_key`: Android-Tastencode der Sidebar-Taste (-1 = Standard: Menü/Einstellungen). Der Installer lernt
  ihn an, indem er `logcat -s EliasHome` mitliest („Taste code=<n> (KEYCODE_…) action=1“ aus HomeButtonService).
- `config.font` (ab V1.9): Bei `"eigene"` schiebt der Installer vorher die TTF/OTF-Dateien einer Schriftfamilie
  nach `files/schrift-neu/` (Dateinamen nur `A-Za-z0-9._-`). Der Launcher kopiert sie nach `filesDir/schrift/`,
  liest die Stärke jeder Datei aus ihrer OS/2-Tabelle (kursive Schnitte werden übersprungen) und nutzt sie überall.
  Ist keine Datei lesbar, bleibt Inter und `setup-ergebnis.txt` meldet einen Fehler. `"standard"` löscht eine eigene
  Schrift wieder. Der Launcher bringt nur Inter (SIL Open Font License) mit; Schriften wie Aptos oder SF Pro dürfen
  nicht weitergegeben werden, die lädt jeder selbst herunter.

Einbau im Launcher: `SetupImport.kt` nach `app/src/main/java/de/elias/launcher/data/` kopieren und in
`MainActivity.onCreate` direkt nach `super.onCreate(...)` `SetupImport.run(this)` aufrufen.
