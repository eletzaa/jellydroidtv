# Jellydroid bauen

## Voraussetzungen

- JDK 17
- Android SDK mit Plattform 34 und Build-Tools 34.0.0
- Gradle 8.9 (der Wrapper `launcher/gradlew` lädt es selbst)

Projekt: Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20, Jetpack Compose for TV, Paket `de.jellydroid.launcher`, minSdk 28, targetSdk 34.

## Variante 1: Android Studio

Ordner `launcher` öffnen, Gradle-Sync abwarten, dann Build > Build APK(s) oder `assembleRelease`.

## Variante 2: Kommandozeile

```bash
cd launcher
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

Die APK liegt danach unter `launcher/app/build/outputs/apk/release/`.
Wenn die Umgebungsvariable `JELLYDROID_BUILD_DIR` gesetzt ist, landet die Build-Ausgabe stattdessen dort.

## Variante 3: `launcher/bauen.ps1` (Windows)

Richtet JDK 17, Android SDK und Gradle 8.9 beim ersten Lauf selbst unter `%LOCALAPPDATA%\JellydroidBuild` ein
(ca. 500 MB), baut und spielt die APK per ADB auf den Fernseher.

```powershell
.\launcher\bauen.ps1 -NurBauen            # nur bauen
.\launcher\bauen.ps1 -Ip 192.168.1.50     # bauen und auf den TV spielen
```

Für die TV-Installation erwartet das Skript ADB unter `platform-tools\adb.exe` im Hauptordner
(die [Platform-Tools von Google](https://developer.android.com/tools/releases/platform-tools) dort entpacken).

## Signatur

Der Signaturschlüssel liegt **nicht** im Repo. Jede Version muss mit demselben Schlüssel signiert sein,
sonst lässt sie sich nicht per `adb install -r` über die installierte Version spielen. Der Build sucht ihn so:

1. `JELLYDROID_KEYSTORE_B64`: der Keystore als Base64-Text (Cloud-Umgebung als Umgebungsvariable, GitHub-Secret)
2. `JELLYDROID_KEYSTORE`: Pfad zur Keystore-Datei
3. `launcher/signing/debug.keystore`: lokal abgelegt, per `.gitignore` ausgeschlossen

Passwort und Alias sind die Android-Debug-Standardwerte (`android` / `androiddebugkey`) und lassen sich mit
`JELLYDROID_KEYSTORE_PASSWORD`, `JELLYDROID_KEY_ALIAS` und `JELLYDROID_KEY_PASSWORD` überschreiben.
Ohne Schlüssel signiert der Build mit dem lokalen Debug-Schlüssel; die installierte Version muss dann vorher
deinstalliert werden. Wer das Projekt selbst baut, nimmt einen eigenen Schlüssel.

## Server-Adressen

Im Code stehen keine. Jellyfin und Seerr werden im Installer oder in den Launcher-Einstellungen eingetragen
(Übergabe per `setup.json`, siehe `installer/launcher-schnittstelle/SCHNITTSTELLE.md`).

## Weitere Skripte

- `paket-erstellen.ps1`: baut den Launcher und packt Installer + APK in `Jellydroid.zip` zum Weitergeben.
- `launcher/launcher-installieren.ps1`, `launcher/launcher-rueckgaengig.ps1`: Launcher als Startbildschirm einrichten bzw. zurück.
- `skripte/`: einzelne ADB-Skripte für das Aufräumen des Fernsehers.
