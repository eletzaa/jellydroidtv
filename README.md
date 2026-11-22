# Jellydroid

Android TV ohne Werbung, mit eigenem Startbildschirm für Jellyfin.

Jellydroid bündelt Werkzeuge, um einen Android-TV-Fernseher (entwickelt und getestet mit einem Philips Android TV 11)
aufzuräumen: Werbe- und Telemetrie-Apps entfernen, Werbung per Privatem DNS filtern und den
Startbildschirm durch einen eigenen Launcher mit Jellyfin- und Seerr-Anbindung ersetzen.
Alles läuft über ADB im Heimnetz, Root ist nicht nötig.

## Inhalt

| Ordner | Was drin ist |
|---|---|
| `installer/` | Geführter Installer für Windows (Fenster) und Linux (Terminal). Der einfachste Einstieg. |
| `launcher/` | Quellcode des eigenen Launchers (Kotlin, Jetpack Compose for TV). |
| `skripte/` | Einzelne PowerShell-Skripte für ADB: Apps entfernen und zurückholen, DNS und Animationen. |

## Vorbereitung am Fernseher

1. Entwickleroptionen einschalten: Einstellungen > Geräteeinstellungen > Info > 7-mal auf „Build“ drücken.
2. In den Entwickleroptionen USB-Debugging (und, falls vorhanden, Netzwerk-Debugging) einschalten.
3. IP-Adresse des Fernsehers notieren: Einstellungen > Netzwerk und Internet > dein Netzwerk.
4. Beim ersten Verbinden am Fernseher „Immer von diesem Computer zulassen“ bestätigen.

## Weitergeben als Paket

`.\paket-erstellen.ps1` im Hauptordner baut den Launcher und legt den Ordner `Jellydroid`
sowie `Jellydroid.zip` an: Installer und Launcher-App zusammen, ohne persönliche Einstellungen,
Sicherungen oder Protokolle. Nur diese ZIP-Datei weitergeben. Mit `-OhneBauen` wird die zuletzt
gebaute APK verwendet.

## Installer

- **Windows:** Doppelklick auf `installer/Installer starten (Windows).cmd`
- **Linux:** `bash installer/installer-linux.sh`

Der Installer fragt die IP-Adresse des Fernsehers ab, lädt bei Bedarf ADB von Google, legt eine
Sicherung an und ändert erst etwas, wenn du in der Zusammenfassung zustimmst. Details stehen in
[`installer/LIESMICH.md`](installer/LIESMICH.md).

## Einzelne Skripte (`skripte/`)

| Skript | Wirkung | Rückgängig |
|---|---|---|
| `stufe1.ps1` | Werbe- und Telemetrie-Apps deaktivieren | `stufe1-rueckgaengig.ps1` |
| `stufe1-uninstall.ps1` | dieselben Apps für Benutzer 0 entfernen | `stufe1-uninstall-rueckgaengig.ps1` |
| `stufe2-entfernen.ps1` | weitere vorinstallierte Apps entfernen | `stufe2-wiederherstellen.ps1` |
| `dns-animationen.ps1` | Privates DNS (AdGuard) und kürzere Animationen | `dns-animationen-rueckgaengig.ps1` |

Die Skripte erwarten ADB unter `platform-tools/adb.exe` im Hauptordner (legt der Installer an,
oder die [Platform-Tools von Google](https://developer.android.com/tools/releases/platform-tools)
dort entpacken). Die Adresse des Fernsehers holen sie sich über `skripte/fernseher.ps1`:
zuerst aus dem Parameter `-Ip`, sonst aus der letzten Verbindung im Installer, sonst fragen sie nach.

```powershell
.\skripte\stufe2-entfernen.ps1 -Ip 192.168.1.50
```

Mit `-Nur paket1,paket2` wirken die Entfernen- und Wiederherstellen-Skripte nur auf diese Pakete.

## Launcher (`launcher/`)

Der Jellydroid-Launcher ist ein eigener Startbildschirm mit Tabs für Startseite, Entdecken und Apps. „Entdecken“ zeigt
Weiterschauen und Beliebtes aus Jellyfin und Seerr, Anfragen gehen direkt an Seerr,
abgespielt wird in [Plezy](https://github.com/edde746/plezy). Die Server-Adressen trägst du
im Installer oder in den Einstellungen des Launchers ein, im Code stehen keine.

Weil die Philips-Firmware den Standard-Startbildschirm ignoriert, fängt eine Bedienungshilfe
die Home-Taste ab. Sie trägt sich nach einem Neustart selbst wieder ein (dafür braucht die App
`WRITE_SECURE_SETTINGS`, das Installationsskript erteilt es per ADB).

**Bauen**
- Mit Android Studio: Ordner `launcher` öffnen, Build > Build APK(s).
- Oder mit `launcher/bauen.ps1`: erwartet JDK 17, Android SDK und Gradle 8.9 unter
  `%LOCALAPPDATA%\EliasBuild` und legt die Build-Ausgabe dort ab (außerhalb von OneDrive).
  `.\bauen.ps1` baut und spielt die APK auf den Fernseher, `.\bauen.ps1 -NurBauen` baut nur.

Die Release-APK wird mit dem lokalen Debug-Schlüssel signiert, ein eigener Schlüssel ist nicht nötig.

**Installieren und zurück**

```powershell
.\launcher\launcher-installieren.ps1 -Apk <pfad-zur-apk>
.\launcher\launcher-rueckgaengig.ps1                  # alter Startbildschirm, Launcher bleibt
.\launcher\launcher-rueckgaengig.ps1 -Deinstallieren  # zusätzlich entfernen
```

## Gut zu wissen

- Philips stellt bei jedem echten Neustart (Strom weg, Update) alle vorinstallierten Apps wieder her.
  Ohne Root geht das nicht dauerhaft. Danach einfach Stufe 2 bzw. im Installer
  „Auswahl nach Neustart erneut anwenden“ ausführen.
- `org.droidtv.nettvregistration` **nicht** entfernen, sonst stürzt der Philips-Systemdienst in einer Schleife ab.
- Den Google-TV-Launcher (`com.google.android.tvlauncher`) nicht deinstallieren, er bleibt als Rückweg.
- Alle Änderungen erfolgen nur für Benutzer 0 und lassen sich mit den Rückgängig-Skripten
  oder einem Zurücksetzen auf Werkseinstellungen aufheben. Nutzung auf eigene Gefahr.

## Arbeiten mit diesem Repo

- Das Repo ist die einzige Quelle für Jellydroid. Änderungen am Launcher werden hier gemacht, gebaut und als neue Version veröffentlicht.
- Fertige APKs liegen unter **Releases** (`v1.8` usw.), nicht im Repo. Jede Version zählt `versionName`/`versionCode` in `launcher/app/build.gradle.kts` hoch (1.8 = 18).
- Vor Code-Arbeit `launcher/CODE-KARTE.md` lesen, für Abhängigkeiten `launcher/graphify-out/graph.json`.
- Der Installer nimmt die höchste `Jellydroid-V*.apk` neben sich oder aus `launcher/ausgabe/`. Für ein Weitergabe-Paket die APK aus dem Release dorthin legen.
- Keine Server-Adressen, Tokens oder persönlichen Daten committen.

## Lizenz

Noch nicht festgelegt. Der AirPlay-Teil (`launcher/airplay/`) basiert auf GPL-3.0-Code. Wird das Repo öffentlich
oder die APK weitergegeben, muss Jellydroid unter GPL-3.0 stehen (siehe `launcher/airplay/LIESMICH.md`).

## Schriften

Der Launcher verwendet SF Pro von Apple (`launcher/app/src/main/res/font/`). Diese Schriften
stehen unter Apples Lizenz und nicht unter der Lizenz dieses Projekts.
