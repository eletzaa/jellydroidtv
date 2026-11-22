# Jellydroid bauen

## Voraussetzungen

- JDK 17
- Android SDK mit Plattform 34 und Build-Tools 34.0.0
- Gradle 8.9 (der Wrapper `launcher/gradlew` lädt es selbst)

Projekt: Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20, Jetpack Compose for TV, Paket `de.elias.launcher`, minSdk 28, targetSdk 34.

## Variante 1: Android Studio

Ordner `launcher` öffnen, Gradle-Sync abwarten, dann Build > Build APK(s) oder `assembleRelease`.

## Variante 2: Kommandozeile

```bash
cd launcher
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

Die APK liegt danach unter `launcher/app/build/outputs/apk/release/`.
Wenn die Umgebungsvariable `ELIAS_BUILD_DIR` gesetzt ist, landet die Build-Ausgabe stattdessen dort.

## Variante 3: `launcher/bauen.ps1` (Windows)

Richtet JDK 17, Android SDK und Gradle 8.9 beim ersten Lauf selbst unter `%LOCALAPPDATA%\EliasBuild` ein
(ca. 500 MB), baut und spielt die APK per ADB auf den Fernseher.

```powershell
.\launcher\bauen.ps1 -NurBauen            # nur bauen
.\launcher\bauen.ps1 -Ip 192.168.1.50     # bauen und auf den TV spielen
```

Für die TV-Installation erwartet das Skript ADB unter `platform-tools\adb.exe` im Hauptordner
(die [Platform-Tools von Google](https://developer.android.com/tools/releases/platform-tools) dort entpacken).

## Signatur

Liegt `launcher/signing/debug.keystore` vor, wird damit signiert, sonst mit dem lokalen Debug-Schlüssel.
Der Schlüssel liegt im (privaten) Repo, damit jede Version gleich signiert ist. Er gehört nicht in Weitergabe-Pakete. Eine selbst gebaute APK lässt sich daher nicht per `adb install -r`
über eine anders signierte installierte Version spielen: vorher deinstallieren.

## Server-Adressen

Im Code stehen keine. Jellyfin und Seerr werden im Installer oder in den Launcher-Einstellungen eingetragen
(Übergabe per `setup.json`, siehe `installer/launcher-schnittstelle/SCHNITTSTELLE.md`).

## Weitere Skripte

- `paket-erstellen.ps1`: baut den Launcher und packt Installer + APK in `Jellydroid.zip` zum Weitergeben.
- `launcher/launcher-installieren.ps1`, `launcher/launcher-rueckgaengig.ps1`: Launcher als Startbildschirm einrichten bzw. zurück.
- `skripte/`: einzelne ADB-Skripte für das Aufräumen des Fernsehers.
