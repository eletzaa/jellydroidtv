# Jellydroid

Jellydroid macht aus einem Android-TV-Gerät einen Fernseher ohne Werbung, dessen Startbildschirm sich um die eigene Mediathek und das eigene Zuhause dreht. Es besteht aus einem eigenen Launcher und einem Installer, der das Gerät per ADB im Heimnetz einrichtet. Root ist nicht nötig.

## Was anders ist als bei Android TV

Android TV zeigt ab Werk gesponserte Reihen, Empfehlungen von Streaminganbietern und vorinstallierte Apps des Herstellers. Jellydroid entfernt diese Apps, filtert Werbung über Privates DNS und ersetzt den Startbildschirm. Übrig bleiben die eigenen Favoriten, die eigene Mediathek und die Apps, die man wirklich nutzt.

Jellyfin ist fest in den Startbildschirm eingebaut. Weiterschauen, Beliebtes und Genres kommen direkt aus der eigenen Mediathek, mit Auflösung und Tonformat im Detail-Fenster. Abgespielt wird mit einem Klick in [Plezy](https://github.com/edde746/plezy). Serien lassen sich als gesehen markieren oder ausblenden.

Was noch fehlt, wird direkt im Launcher über Seerr angefragt. Trends und Neuerscheinungen stehen neben der Mediathek, ein Klick auf „Anfragen“ reicht. Eine zweite App oder der Umweg übers Handy entfallen.

Eine Taste auf der Fernbedienung öffnet die Sidebar, ein Schnellmenü, das über jeder App aufgeht. Dort liegen Uhrzeit, WLAN, Eingangsquelle, AirPlay, die Launcher-Einstellungen und Home Assistant. Man muss also nicht erst zurück zum Startbildschirm, um das Licht zu dimmen oder die Quelle zu wechseln.

Home Assistant ist tief eingebunden. Licht, Schalter und Szenen werden pro Raum angezeigt, die Heizung lässt sich direkt mit den Pfeiltasten regeln. Die Anmeldung läuft per QR-Code vom Handy. Klingelt eine smarte Türklingel, erscheint am Fernseher ein Hinweis, egal welche App gerade läuft. Ist ein smarter Türsummer eingebunden, öffnet man die Tür von dort aus mit der Fernbedienung.

Dazu kommt AirPlay: iPhone, iPad und Mac spiegeln ihren Bildschirm samt Ton auf den Fernseher, ohne Zusatzgerät.

Der Installer führt durch alles: Verbindung, Sicherung, Apps entfernen, Launcher, Jellyfin, Seerr und Home Assistant. Geändert wird erst nach Zustimmung, und alles lässt sich zurücknehmen.

Entwickelt und getestet wurde Jellydroid auf einem Philips-Fernseher mit Android TV 11. Launcher und Installer sind nicht an einen Hersteller gebunden. Welche vorinstallierten Apps sich entfernen lassen, unterscheidet sich je nach Gerät.

## Inhalt

| Ordner | Inhalt |
|---|---|
| `installer/` | Geführter Installer für Windows und Linux. Der einfachste Einstieg. |
| `launcher/` | Quellcode des Launchers, Kotlin mit Jetpack Compose for TV. |
| `skripte/` | Einzelne PowerShell-Skripte für ADB: Apps entfernen und zurückholen, DNS, Animationen. |

## Vorbereitung am Fernseher

1. Einstellungen > Geräteeinstellungen > Info: 7-mal auf „Build“ drücken, um die Entwickleroptionen freizuschalten.
2. In den Entwickleroptionen USB-Debugging und, falls vorhanden, Netzwerk-Debugging einschalten.
3. IP-Adresse notieren: Einstellungen > Netzwerk und Internet > eigenes Netzwerk.
4. Beim ersten Verbinden „Immer von diesem Computer zulassen“ bestätigen.

## Installer

- **Windows:** `installer/Installer starten (Windows).cmd` doppelklicken
- **Linux:** `bash installer/installer-linux.sh`

Der Installer lädt ADB bei Bedarf von Google. Über „Launcher aktualisieren“ kommt eine neue Version auf den Fernseher, die Einstellungen bleiben, neue Funktionen werden zur Einrichtung angeboten. Server-Adressen werden dort eingetragen, im Code und im Repo stehen keine.

Mehr dazu in [`installer/LIESMICH.md`](installer/LIESMICH.md).

## Einzelne Skripte

| Skript | Wirkung | Rückgängig |
|---|---|---|
| `stufe1.ps1` | Werbe- und Telemetrie-Apps deaktivieren | `stufe1-rueckgaengig.ps1` |
| `stufe1-uninstall.ps1` | Dieselben Apps für Benutzer 0 entfernen | `stufe1-uninstall-rueckgaengig.ps1` |
| `stufe2-entfernen.ps1` | Weitere vorinstallierte Apps entfernen | `stufe2-wiederherstellen.ps1` |
| `dns-animationen.ps1` | Privates DNS über AdGuard, kürzere Animationen | `dns-animationen-rueckgaengig.ps1` |

ADB wird unter `platform-tools/adb.exe` im Hauptordner erwartet. Der Installer legt es dort an, alternativ die [Platform-Tools von Google](https://developer.android.com/tools/releases/platform-tools) entpacken. Die Adresse des Fernsehers kommt aus `-Ip`, sonst aus der letzten Installer-Verbindung, sonst fragt das Skript.

```powershell
.\skripte\stufe2-entfernen.ps1 -Ip 192.168.1.50
.\skripte\stufe2-entfernen.ps1 -Nur paket1,paket2
```

## Launcher

Viele Hersteller lassen den Startbildschirm nicht ohne Weiteres austauschen. Deshalb fängt eine Bedienungshilfe die Home-Taste ab und trägt sich nach jedem Neustart selbst wieder ein. Dafür braucht die App `WRITE_SECURE_SETTINGS`, das Installationsskript erteilt die Berechtigung per ADB.

AirPlay läuft als eigenes Modul `launcher/airplay/` in einem separaten Prozess.

### Bauen

Voraussetzungen: JDK 17, Android SDK 34, Gradle 8.9 über den Wrapper.

```bash
cd launcher
./gradlew assembleRelease
```

Alternativ in Android Studio den Ordner `launcher` öffnen, oder unter Windows `launcher/bauen.ps1` nutzen, das die Werkzeuge beim ersten Lauf selbst einrichtet. Der Signaturschlüssel liegt nicht im Repo. Varianten, Signatur und Umgebungsvariablen stehen in [`BAUEN.md`](BAUEN.md).

### Installieren und zurück

```powershell
.\launcher\launcher-installieren.ps1 -Apk <pfad-zur-apk>
.\launcher\launcher-rueckgaengig.ps1                  # alter Startbildschirm, Launcher bleibt
.\launcher\launcher-rueckgaengig.ps1 -Deinstallieren  # Launcher zusätzlich entfernen
```

### Weitergeben

`.\paket-erstellen.ps1` baut den Launcher und packt Installer und APK nach `Jellydroid.zip`, ohne persönliche Einstellungen, Sicherungen oder Protokolle. `-OhneBauen` nimmt die zuletzt gebaute APK.

## Gut zu wissen

- Manche Geräte stellen bei einem echten Neustart, also Strom weg oder Update, die vorinstallierten Apps wieder her. Ohne Root lässt sich das nicht verhindern. Danach im Installer „Auswahl nach Neustart erneut anwenden“ oder Stufe 2 ausführen.
- Den Standard-Launcher von Google nicht deinstallieren. Er bleibt als Rückweg.
- Systemdienste des Herstellers nur entfernen, wenn klar ist, wofür sie da sind. Der Installer bietet bekannte kritische Pakete gar nicht erst an, auf Philips-Geräten etwa `org.droidtv.nettvregistration`.
- Alle Änderungen gelten nur für Benutzer 0 und lassen sich mit den Rückgängig-Skripten oder einem Werksreset aufheben. Nutzung auf eigene Gefahr.

## Mitarbeiten

- Aktuelle APK: `launcher/ausgabe/Jellydroid-V<x.y>.apk`, ältere Versionen in `launcher/ausgabe/archiv/`. Jede Version erhöht `versionName` und `versionCode` in `launcher/app/build.gradle.kts`.
- Vor Änderungen am Code `launcher/CODE-KARTE.md` lesen. Abhängigkeiten stehen in `launcher/graphify-out/graph.json`.
- Der Installer nimmt die höchste `Jellydroid-V*.apk` neben sich oder aus `launcher/ausgabe/`.
- Keine Server-Adressen, Tokens, Schlüssel oder persönlichen Daten committen. Das Repo ist öffentlich.

## Schriften

Der Launcher bringt Inter mit. Eine andere Schrift lädt jeder selbst herunter und wählt sie im Installer auf der Seite „Schrift“ aus. Sie wird nicht mit ausgeliefert. Versionen vor V1.9 enthielten eine nicht weitergebbare Schrift und sind deshalb nicht veröffentlicht.

## Lizenz

Jellydroid steht unter der **GNU General Public License v3.0**, siehe [`LICENSE`](LICENSE).

Der AirPlay-Empfänger in `launcher/airplay/` basiert auf [android-airplay-server](https://github.com/jqssun/android-airplay-server) von jqssun und [UxPlay](https://github.com/FDH2/UxPlay), beide GPL-3.0.

| Fremder Teil | Lizenz | Lizenztext |
|---|---|---|
| android-airplay-server, UxPlay | GPL-3.0 | `launcher/airplay/src/main/cpp/third_party/UxPlay/LICENSE` |
| libplist | LGPL-2.1 | `launcher/airplay/src/main/cpp/third_party/libplist/COPYING.LESSER` |
| llhttp | MIT | `launcher/airplay/src/main/cpp/third_party/UxPlay/lib/llhttp/LICENSE-MIT` |
| Inter | SIL OFL 1.1 | `launcher/app/src/main/assets/lizenzen/inter-OFL.txt` |
| Material Design Icons | Pictogrammers Free License / Apache-2.0 | `launcher/app/src/main/assets/mdi-LICENSE.txt` |
| OpenSSL, Oboe über Maven | OpenSSL-Lizenz, Apache-2.0 | `launcher/airplay/LIESMICH.md` |

Herkunft des AirPlay-Codes: [`launcher/airplay/LIESMICH.md`](launcher/airplay/LIESMICH.md).

Jellydroid ist ein privates Projekt ohne Verbindung zu Jellyfin, Seerr, Apple, Google oder einem TV-Hersteller.
