# Graph Report - launcher  (2026-11-22)

## Corpus Check
- 78 files · ~64,825 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 23 file(s) not represented in the graph (top: .xml 8, .otf 8, .properties 2)
- WARNING: 23 of 104 files seen (22%) have no supported extractor and are missing from this graph entirely. It may look complete while representing only part of the codebase.

## Summary
- 1437 nodes · 2852 edges · 205 communities (39 shown, 166 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 135 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `13c68368`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- OboeCallbacks
- android_raop_callbacks.c
- SpscRing
- TimelineBuffer
- Sidebar.kt
- GlassState
- MdiIcons.kt
- TopBar.kt
- AudioEngine
- AudioOutput
- Sidebar.kt
- Discover.kt
- .debugInfo
- LauncherApp.kt
- Screens.kt
- RemoteImage.kt
- VideoRenderer
- ConfigScreen.kt
- TimelineMetrics
- Overlay.kt
- audio_decoder.h
- MediaCodecDecoder
- DelayTracker
- Services
- LatencyReporter
- NativeBridge
- SystemActions
- DecoderSelector
- .Icon
- CodecConfig
- AirPlayService.kt
- RaopCallbackHandler
- MainActivity.kt
- Intent
- HomeButtonService
- HaPrefs
- NsdServiceManager.kt
- LogSink
- HaStatus
- AudioConfig
- DefaultSidebarActions
- HomeAssistant.kt
- native_bridge.cpp
- JellyfinStatus
- AppEntry
- LocalMarks
- Backdrop.kt
- CreatedDecoder
- _audio_process
- ThemeMode
- VideoPipeline
- AirPlayActivity
- LongPress.kt
- AirPlay-Empfänger (Modul `airplay`)
- AudioDebugData
- GlassRenderer.kt
- AudioConfig

## God Nodes (most connected - your core abstractions)
1. `AudioEngine` - 53 edges
2. `TimelineBuffer` - 43 edges
3. `AudioOutput` - 30 edges
4. `VideoRenderer` - 30 edges
5. `DelayTracker` - 29 edges
6. `LauncherApp()` - 24 edges
7. `NativeBridge` - 24 edges
8. `LatencyReporter` - 23 edges
9. `_get_env()` - 23 edges
10. `DecoderSelector` - 22 edges

## Surprising Connections (you probably didn't know these)
- `Medien (jellyfin/)` --references--> `MediaItem`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/jellyfin/JellyfinClient.kt
- `Start und Systemdienste (app/src/main/java/de/elias/launcher/)` --references--> `LauncherApp()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/ui/LauncherApp.kt
- `Oberfläche (ui/)` --references--> `okKey()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/ui/LongPress.kt
- `Oberfläche (ui/)` --references--> `appleSpring()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/ui/Theme.kt
- `Sidebar (sidebar/)` --references--> `DefaultSidebarActions`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/sidebar/Doorbell.kt

## Import Cycles
- None detected.

## Communities (205 total, 166 thin omitted)

### Community 0 - "OboeCallbacks"
Cohesion: 0.12
Nodes (6): OboeCallbacks, mChannels, mClientGain, mLog, mOwner, mTimeline

### Community 6 - "android_raop_callbacks.c"
Cohesion: 0.06
Nodes (29): android_callback_ctx_t, android_callbacks_destroy(), android_callbacks_fill(), android_callbacks_init(), android_callbacks_update_playback_info(), _audio_get_format(), _audio_remote_control_id(), _audio_set_client_volume() (+21 more)

### Community 7 - "SpscRing"
Cohesion: 0.16
Nodes (5): SpscRing, mBuf, mCapacity, mRead, mWrite

### Community 8 - "TimelineBuffer"
Cohesion: 0.10
Nodes (19): TimelineBuffer, mAboveCapSinceNs, MAX_GAP_NS, mChannels, mExpectedPtsNs, mLastTrimBlockNs, mMetrics, mPrimeSilenceFrames (+11 more)

### Community 13 - "Sidebar.kt"
Cohesion: 0.07
Nodes (3): Pal, AddTile(), AppTile()

### Community 14 - "GlassState"
Cohesion: 0.18
Nodes (6): glass(), GlassMode, Matte, Refraction, GlassPanel, GlassState

### Community 16 - "TopBar.kt"
Cohesion: 0.16
Nodes (6): CircleButton(), Clock(), SystemCapsule(), TabCapsule(), TabItem(), TopBar()

### Community 17 - "AudioEngine"
Cohesion: 0.06
Nodes (27): audio_engine_configure(), audio_engine_create(), audio_engine_destroy(), audio_engine_on_format(), audio_engine_pause(), audio_engine_set_client_gain(), audio_engine_start(), DECODER_RETRY_NS (+19 more)

### Community 18 - "AudioOutput"
Cohesion: 0.11
Nodes (14): AudioOutput, Debug, xrun, mCallbacks, mChannels, mClosing, mLastXrun, mLock (+6 more)

### Community 20 - "Sidebar.kt"
Cohesion: 0.07
Nodes (57): NetInfo, Kind, Ethernet, None, Wifi, readNet(), HaEntity, BackHeader() (+49 more)

### Community 23 - "Discover.kt"
Cohesion: 0.16
Nodes (25): MediaItem, JellyfinState, Badge(), CarouselDots(), CloseButton(), ConnectPanel(), ContinueWatchingRow(), DetailDialog() (+17 more)

### Community 24 - ".debugInfo"
Cohesion: 0.15
Nodes (9): Debug, backlogMs, metrics, tunedCushionMs, Debug, drops, silences, trims (+1 more)

### Community 25 - "LauncherApp.kt"
Cohesion: 0.15
Nodes (8): AppMenu(), MenuOption, sp(), LauncherApp(), MinimalBringIntoView, MinimalScroll(), RowSnapScroll(), RowTopBringIntoView

### Community 28 - "Screens.kt"
Cohesion: 0.11
Nodes (7): AppsScreen(), fadeEdges(), fadeRightEdge(), fadeTopEdge(), RowTitle(), sp(), StartScreen()

### Community 33 - "ConfigScreen.kt"
Cohesion: 0.37
Nodes (13): LauncherConfig, AirPlayPage(), ConfigDialog(), HomeAssistantPage(), JellyfinPage(), MenuKeyRow(), NavRow(), rowBackground() (+5 more)

### Community 36 - "TimelineMetrics"
Cohesion: 0.20
Nodes (5): TimelineMetrics, mDrops, mSilences, mTrims, mUnderruns

### Community 41 - "Overlay.kt"
Cohesion: 0.07
Nodes (5): qrBitmap(), OverlayOwner, OverlayWindow, AvatarFile, rememberAvatar()

### Community 42 - "audio_decoder.h"
Cohesion: 0.14
Nodes (4): buildAlacAtomCookie(), buildAlacMagicCookie(), codecConfig(), audio_engine_set_default_stream_values()

### Community 43 - "MediaCodecDecoder"
Cohesion: 0.10
Nodes (15): LatencyProbe, CAP, Entry, ptsUs, queueNs, mBuf, mRead, mWrite (+7 more)

### Community 45 - "DelayTracker"
Cohesion: 0.08
Nodes (20): DelayTracker, BASE_CREEP_SHIFT, BUCKET_MS, BUCKET_NS, FORGET_SHIFT, mAdaptive, MAX_CUSHION_MS, mBaseNs (+12 more)

### Community 47 - "Services"
Cohesion: 0.32
Nodes (5): JellyfinApp, JellyfinApps, Places, Services, StreamService

### Community 48 - "LatencyReporter"
Cohesion: 0.10
Nodes (19): LatencyReporter, Debug, held, maxUs, meanUs, mHeld, mLogging, mMaxNs (+11 more)

### Community 55 - "CodecConfig"
Cohesion: 0.25
Nodes (8): CodecConfig, aacProfile, channels, csd, csdLen, mime, preferName, sampleRate

### Community 58 - "MainActivity.kt"
Cohesion: 0.09
Nodes (5): BootReceiver, MainActivity, OnBackPressedCallback, NavKeys, ServiceKeeper

### Community 61 - "HaPrefs"
Cohesion: 0.06
Nodes (9): SetupImport, HaPrefs, AuthExpired, JellydroidApp, QuickConnectStart, SeerrClient, HttpException, BellButton() (+1 more)

### Community 62 - "NsdServiceManager.kt"
Cohesion: 0.19
Nodes (3): MdnsService, NsdServiceManager, RegistrationListener

### Community 63 - "LogSink"
Cohesion: 0.16
Nodes (4): LogSink, mMethod, mObj, mVm

### Community 64 - "HaStatus"
Cohesion: 0.33
Nodes (6): HaStatus, AuthFailed, Connecting, Error, Off, Ready

### Community 66 - "AudioConfig"
Cohesion: 0.22
Nodes (9): AudioConfig, benchmarkLog, forceSwAac, forceSwAlac, lowLatency, oboeBufferFrames, percentilePct, realtimePriority (+1 more)

### Community 68 - "DefaultSidebarActions"
Cohesion: 0.09
Nodes (16): DefaultSidebarActions, SidebarController, SideView, Ha, Main, Settings, Wifi, AirPlay (airplay/, eigenes Modul, Prozess :airplay) (+8 more)

### Community 69 - "HomeAssistant.kt"
Cohesion: 0.15
Nodes (3): AirPlayState, Genres, set

### Community 70 - "native_bridge.cpp"
Cohesion: 0.08
Nodes (38): android_dnssd_get_airplay_txt_count(), android_dnssd_get_airplay_txt_key(), android_dnssd_get_airplay_txt_val(), android_dnssd_get_raop_servname(), android_dnssd_get_raop_txt_count(), android_dnssd_get_raop_txt_key(), android_dnssd_get_raop_txt_val(), android_dnssd_set_codecs() (+30 more)

### Community 71 - "JellyfinStatus"
Cohesion: 0.33
Nodes (6): JellyfinStatus, Error, Loading, LoggedOut, Ready, WaitingForCode

### Community 72 - "AppEntry"
Cohesion: 0.12
Nodes (3): AppEntry, AppRepository, MediaActions

### Community 75 - "Backdrop.kt"
Cohesion: 0.09
Nodes (7): Sun, paintBackdrop(), paintGlow(), paintGlowLight(), Scene, Scenes, withAlpha()

### Community 78 - "CreatedDecoder"
Cohesion: 0.18
Nodes (7): Decoder, CreatedDecoder, ct, decoder, format, CodecFormat, spf

### Community 83 - "ThemeMode"
Cohesion: 0.50
Nodes (4): ThemeMode, AUTO, DARK, LIGHT

### Community 93 - "AirPlayActivity"
Cohesion: 0.15
Nodes (5): AirPlayActivity, Mirror(), Callback, Pin(), Screen()

### Community 107 - "AudioDebugData"
Cohesion: 0.33
Nodes (5): AudioDebugData, decode, decodeErrors, output, timeline

## Knowledge Gaps
- **163 isolated node(s):** `Off`, `Connecting`, `Ready`, `AuthFailed`, `Error` (+158 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 595 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **166 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TimelineBuffer` connect `TimelineBuffer` to `OboeCallbacks`, `TimelineMetrics`, `SpscRing`, `audio_decoder.h`, `MediaCodecDecoder`, `DelayTracker`, `LatencyReporter`, `AudioEngine`, `AudioOutput`, `.debugInfo`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `VideoRenderer` (e.g. with `onCreate()` and `DecoderSelector`) actually correct?**
  _`VideoRenderer` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Off`, `Connecting`, `Ready` to the rest of the system?**
  _163 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `OboeCallbacks` be split into smaller, more focused modules?**
  _Cohesion score 0.12105263157894737 - nodes in this community are weakly interconnected._
- **Why does `LauncherApp()` connect `LauncherApp.kt` to `ConfigScreen.kt`, `DefaultSidebarActions`, `AppEntry`, `LocalMarks`, `Backdrop.kt`, `GlassState`, `TopBar.kt`, `SystemActions`, `Sidebar.kt`, `Discover.kt`, `MainActivity.kt`, `Screens.kt`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Should `android_raop_callbacks.c` be split into smaller, more focused modules?**
  _Cohesion score 0.06184012066365008 - nodes in this community are weakly interconnected._
- **Why does `VideoRenderer` connect `VideoRenderer` to `AirPlayService.kt`, `DecoderSelector`, `VideoPipeline`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._