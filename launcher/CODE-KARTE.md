# Code-Karte Jellydroid (launcher/)

Paket `de.elias.launcher`, Kotlin + Compose for TV. Stand V1.9.

## Start und Systemdienste (app/src/main/java/de/elias/launcher/)
- `JellydroidApp.kt`: Application, führt SetupImport aus (nur im Hauptprozess), startet HA und AirPlay
- `MainActivity.kt`: Home-Activity, ruft `LauncherApp()` auf, Extra `open=config`
- `HomeButtonService.kt`: Bedienungshilfe für Home-Taste, Sidebar-Taste und Sidebar-Overlay
- `ServiceKeeper.kt`, `BootReceiver.kt`: Selbstheilung der Bedienungshilfe nach Neustart

## Oberfläche (ui/)
- `LauncherApp.kt`: Wurzel. Tabs, Hell/Dunkel (`Pal.light`), erzeugt JellyfinState/SeerrState/LauncherConfig
- `Screens.kt`: Startseite (Favoriten) und Apps. `Tiles.kt`: App-Kacheln. `AppMenu.kt`: Langdruck-Menü
- `Discover.kt`: Entdecken, Hero-Banner, Medienreihen, **DetailDialog** (Technik-Etiketten, Markierungen), StartHero, ContinueWatchingRow, PillButton
- `ConfigScreen.kt`: Launcher Konfiguration (Konten, Design, Dienste, Genres, AirPlay, HA, Menütaste)
- `TopBar.kt`: obere Leiste/Systemkapsel. `Palette.kt`: Farben hell/dunkel. `Theme.kt`: Textstile (`DisplayFont`/`TextFont`), `appleSpring()`. `AppFonts.kt`: Inter oder eigene Schrift aus `filesDir/schrift/` (vom Installer, `config.font`)
- `Icons.kt`: gezeichnete Symbole. `Avatar.kt`, `LongPress.kt` (`okKey`)

## Sidebar (sidebar/)
- `Sidebar.kt`: alle Ansichten (Haupt, Einstellungen, WLAN, Home Assistant inkl. Raumauswahl, Szenen, Lampen, Heizung, QR-Anmeldung). Farben in `object S` (folgen `Pal.light`)
- `Overlay.kt`: Overlay-Fenster + `SidebarController`. `Doorbell.kt`: Klingel-Popup, `DefaultSidebarActions`

## Home Assistant (home/)
- `HomeAssistant.kt`: WebSocket-Singleton, Zustände, Räume (`rooms`, `selectRoom`), `iconOf`, Dienstaufrufe
- `HaPrefs.kt`: Prefs "ha" (Adresse, Token, media_player, room, default_room = Standardraum, Klingel). `HaSetupServer.kt` + `Qr.kt`: Handy-Anmeldung
- `MdiIcons.kt`: HA-Symbole über die Schrift `res/font/mdi_icons.ttf` + `assets/mdi.txt`

## Medien (jellyfin/)
- `JellyfinClient.kt`: REST (Quick Connect, Weiterschauen, Genres, `techInfo`, `markPlayed`). `MediaItem`
- `JellyfinState.kt`: Reihen, `play` (Plezy), `complete`, `seriesWatched`, `hideSeries`
- `SeerrClient.kt` / `SeerrState.kt`: Trends, Genres, Anfragen, Streamingdienste
- `LocalMarks.kt`: lokale Markierungen (Prefs "marks"). `MediaActions.kt`: Plezy- und Trailer-Links. `RemoteImage.kt`: Bildcache

## Daten (data/)
- `LauncherConfig.kt` (Prefs "config", ThemeMode), `SetupImport.kt` (setup.json vom Installer), `Services.kt` (Streamingdienste), `Sun.kt` (Dynamisch), `AppRepository.kt`, `SystemActions.kt`, `WifiInfo.kt`

## AirPlay (airplay/, eigenes Modul, Prozess :airplay)
- `AirPlayService.kt`: Start, Featurebits, Callbacks. `AirPlayActivity.kt`: Vollbild. `VideoPipeline.kt`/`VideoRenderer.kt`/`DecoderSelector.kt`: Bild. `AudioRenderer.kt`: Ton
- `src/main/cpp/`: JNI-Brücke (`native_bridge.cpp`, `android_dnssd_shim.c`), `third_party/` = UxPlay, libplist, llhttp (fremder Code, für Graphify ausgeschlossen)

## Bauen
Version in `app/build.gradle.kts` (versionName/versionCode). Signatur: Schlüssel nicht im Repo, siehe BAUEN.md › Signatur (`JELLYDROID_KEYSTORE_B64` oder lokal `signing/debug.keystore`). Release ohne Lint:
`assembleRelease -x lintVitalAnalyzeRelease -x lintVitalRelease -x lintVitalReportRelease`
