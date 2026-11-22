# Jellydroid Installer

Geführte Einrichtung für Android-TV-Geräte (getestet für Philips Android TV 11): Verbindung per ADB,
Sicherung, vorinstallierte Apps entfernen, eigenen Launcher installieren und vorkonfigurieren,
Wiederherstellung. Am Fernseher wird erst etwas geändert, wenn du in der Zusammenfassung zustimmst.

## Starten
- **Windows:** Doppelklick auf `Installer starten (Windows).cmd`
- **Linux:** `bash installer-linux.sh` (Menü mit whiptail/dialog, sonst Textmodus; `--text` erzwingt Textmodus).
  Braucht `curl`; ADB wird bei Bedarf von Google geladen (dafür `unzip` oder `python3`).

## Hauptmenü
| Punkt | Was passiert |
|---|---|
| Geführte Einrichtung | Vorbereitung abhaken, verbinden, Sicherung, Apps entfernen, Launcher, Streamingdienste, Jellyfin/Seerr, Home Assistant, Tipps, ausführen |
| Launcher aktualisieren | Installiert eine neue APK über die alte. Alle Einstellungen bleiben. Neue Funktionen der neuen Version werden erkannt und können gleich eingerichtet werden |
| Auswahl nach Neustart erneut anwenden | Entfernt die zuletzt gewählten Apps wieder (Philips stellt sie beim echten Neustart her) |
| Nur Sicherung erstellen | Speichert App-Listen und Einstellungen unter `sicherungen\` |
| Wiederherstellen | Apps zurückholen, Einstellungen und Startbildschirm zurücksetzen, Launcher entfernen |
| Launcher installieren oder neu einrichten | Launcher, Profilbild, Design, Schrift, Dienste, Jellyfin, Seerr und Home Assistant ohne Apps anzufassen |
| Tipps lesen | Plezy, Streamingdienste, Neustarts usw. |

## Dateien
- `daten/apps.tsv` – Liste entfernbarer Apps mit Gruppe und Empfehlung; Gruppe `geschuetzt` wird nie angeboten.
- `daten/dienste.tsv`, `daten/jellyfin-apps.tsv` – Streamingdienste und Jellyfin-Apps (Schlüssel wie im Launcher).
- `daten/funktionen.tsv` – Launcher-Funktionen mit der Version, ab der es sie gibt, und der Installer-Seite zum Einrichten. Grundlage für „Launcher aktualisieren“.
- `daten/tipps.txt` – Tipps (Titelzeile, dann Text, Leerzeile trennt).
- `launcher-schnittstelle/` – wie der Installer die Einstellungen an den Launcher übergibt (`setup.json`).
- Entsteht beim Benutzen: `sicherungen/`, `platform-tools/`, `installer-einstellungen.*` (ohne Passwörter), `installer-protokoll.txt`.

## Launcher-APK
Die Launcher-APK heißt `Jellydroid-V<Version>.apk` (z. B. `Jellydroid-V1.3.apk`). Der Installer sucht sie neben sich und in `..\launcher\ausgabe\` und nimmt die höchste Version. Gibt es keine, nimmt er `launcher.apk` neben sich.

## Schrift
Der Launcher bringt Inter mit (frei, SIL Open Font License). Auf der Seite „Schrift“ lässt sich stattdessen eine
eigene Schrift wählen, zum Beispiel Aptos (kostenlos bei Microsoft) oder SF Pro (bei Apple). Solche Schriften
dürfen nicht im Launcher oder im Repo weitergegeben werden, deshalb lädt sie jeder selbst herunter. Ausgewählt werden
die TTF- oder OTF-Dateien einer Familie (Windows: mehrere Dateien im Dialog, Linux: Datei oder Ordner). Mehrere
Stärken (Regular, Medium, Semibold, Bold) sehen am besten aus; kursive Schnitte werden ignoriert. Der Installer
überträgt sie mit `config.font` (siehe `launcher-schnittstelle/SCHNITTSTELLE.md`). Ab V1.9.

## Update und neue Funktionen
„Launcher aktualisieren“ vergleicht drei Dinge:
1. die Version der neuen APK (aus dem Dateinamen `Jellydroid-V<Version>.apk`),
2. die Funktionen in `daten/funktionen.tsv` bis zu dieser Version,
3. was am Fernseher schon eingerichtet ist: `installer-stand.txt` im Launcher-Ordner am TV
   (`/sdcard/Android/data/de.elias.launcher/files/`). Die Datei schreibt der Installer nach jeder Einrichtung.
   Sie bleibt bei Updates erhalten und gilt für jeden PC. Fehlt sie (ältere Einrichtung), gilt als eingerichtet,
   was die installierte Version schon konnte.

Angeboten wird nur, was in der neuen APK steckt und am TV noch nicht eingerichtet ist. Bereits eingerichtete
Funktionen stehen ohne Haken in der Liste und lassen sich bei Bedarf erneut einrichten. Übertragen werden nur
die Felder der gewählten Funktionen, alles andere bleibt im Launcher, wie es ist.

**Neue Funktion im Launcher:** eine Zeile in `daten/funktionen.tsv` ergänzen. Braucht sie Einstellungen, dazu eine
Installer-Seite (Windows: `$Pages['<seite>']`, Linux: `step_<seite>`) und ihre setup.json-Felder in
`Add-FunktionSetup` bzw. `setup_cfg`/`setup_block`. Ohne Seite (`-`) wird sie nur als neu angezeigt.

Alte APKs liegen in `launcher/ausgabe/archiv/` (öffentlich ab V1.9). Der Installer schaut dort nicht hinein und nimmt immer die höchste Version.
