# Graph Report - launcher  (2026-11-23)

## Corpus Check
- 79 files · ~66,193 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 23 file(s) not represented in the graph (top: .ttf 9, .xml 8, .properties 2)
- WARNING: 23 of 106 files seen (22%) have no supported extractor and are missing from this graph entirely. It may look complete while representing only part of the codebase.

## Summary
- 1547 nodes · 2910 edges · 298 communities (35 shown, 263 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 153 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `aadf82f3`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- OboeCallbacks
- android_raop_callbacks.c
- SpscRing
- TimelineBuffer
- TopBar.kt
- LauncherApp.kt
- Sidebar.kt
- .Icon
- AudioEngine
- AudioOutput
- Discover.kt
- .debugInfo
- JellyfinClient.kt
- LauncherApp.kt
- VideoRenderer
- .run
- TimelineMetrics
- Overlay.kt
- audio_decoder.h
- MediaCodecDecoder
- DelayTracker
- LatencyReporter
- SystemActions
- HomeAssistant.kt
- DecoderSelector
- CodecConfig
- AirPlayService.kt
- NativeBridge
- HomeButtonService
- BootReceiver.kt
- HaPrefs
- NsdServiceManager.kt
- LogSink
- ConfigScreen.kt
- AudioConfig
- AppEntry
- Avatar.kt
- native_bridge.cpp
- .onCreate
- Backdrop.kt
- CreatedDecoder
- getvalue
- VideoPipeline
- Theme.kt
- Screens.kt
- AirPlay-Empfänger (Modul `airplay`)
- RaopCallbackHandler
- AudioDebugData
- AppFonts.kt
- Services
- AirPlayActivity
- audio_engine.h
- RegistrationListener
- AudioConfig
- JellyfinStatus
- LocalMarks
- ThemeMode

## God Nodes (most connected - your core abstractions)
1. `AudioEngine` - 53 edges
2. `TimelineBuffer` - 43 edges
3. `AudioOutput` - 30 edges
4. `VideoRenderer` - 30 edges
5. `DelayTracker` - 29 edges
6. `LauncherApp()` - 29 edges
7. `NativeBridge` - 24 edges
8. `LatencyReporter` - 23 edges
9. `_get_env()` - 23 edges
10. `RaopCallbackHandler` - 22 edges

## Surprising Connections (you probably didn't know these)
- `Medien (jellyfin/)` --references--> `MediaItem`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/jellydroid/launcher/jellyfin/JellyfinClient.kt
- `Start und Systemdienste (app/src/main/java/de/jellydroid/launcher/)` --references--> `LauncherApp()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/jellydroid/launcher/ui/LauncherApp.kt
- `Oberfläche (ui/)` --references--> `appleSpring()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/jellydroid/launcher/ui/Theme.kt
- `Oberfläche (ui/)` --references--> `okKey()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/jellydroid/launcher/ui/LongPress.kt
- `Sidebar (sidebar/)` --references--> `DefaultSidebarActions`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/jellydroid/launcher/sidebar/Doorbell.kt

## Import Cycles
- None detected.

## Communities (298 total, 263 thin omitted)

### Community 0 - "OboeCallbacks"
Cohesion: 0.12
Nodes (6): OboeCallbacks, mChannels, mClientGain, mLog, mOwner, mTimeline

### Community 6 - "android_raop_callbacks.c"
Cohesion: 0.07
Nodes (29): android_callback_ctx_t, android_callbacks_destroy(), android_callbacks_fill(), android_callbacks_init(), android_callbacks_update_playback_info(), _audio_get_format(), _audio_process(), _audio_remote_control_id() (+21 more)

### Community 7 - "SpscRing"
Cohesion: 0.16
Nodes (5): SpscRing, mBuf, mCapacity, mRead, mWrite

### Community 8 - "TimelineBuffer"
Cohesion: 0.10
Nodes (19): TimelineBuffer, mAboveCapSinceNs, MAX_GAP_NS, mChannels, mExpectedPtsNs, mLastTrimBlockNs, mMetrics, mPrimeSilenceFrames (+11 more)

### Community 13 - "TopBar.kt"
Cohesion: 0.06
Nodes (3): BellButton(), DoorbellCard(), Pal

### Community 14 - "LauncherApp.kt"
Cohesion: 0.14
Nodes (9): AppMenu(), MenuOption, sp(), HeroBackdrop(), LauncherApp(), MinimalBringIntoView, MinimalScroll(), RowSnapScroll() (+1 more)

### Community 15 - "Sidebar.kt"
Cohesion: 0.06
Nodes (58): NetInfo, Kind, Ethernet, None, Wifi, readNet(), HaEntity, qrBitmap() (+50 more)

### Community 17 - "AudioEngine"
Cohesion: 0.07
Nodes (26): audio_engine_configure(), audio_engine_create(), audio_engine_destroy(), audio_engine_on_format(), audio_engine_pause(), audio_engine_set_client_gain(), DECODER_RETRY_NS, mApplied (+18 more)

### Community 18 - "AudioOutput"
Cohesion: 0.11
Nodes (14): AudioOutput, Debug, xrun, mCallbacks, mChannels, mClosing, mLastXrun, mLock (+6 more)

### Community 23 - "Discover.kt"
Cohesion: 0.13
Nodes (23): MediaItem, JellyfinState, Badge(), CarouselDots(), CloseButton(), ConnectPanel(), ContinueWatchingRow(), DetailDialog() (+15 more)

### Community 24 - ".debugInfo"
Cohesion: 0.15
Nodes (9): Debug, backlogMs, metrics, tunedCushionMs, Debug, drops, silences, trims (+1 more)

### Community 25 - "JellyfinClient.kt"
Cohesion: 0.24
Nodes (3): QuickConnectStart, SeerrClient, HttpException

### Community 36 - "TimelineMetrics"
Cohesion: 0.20
Nodes (5): TimelineMetrics, mDrops, mSilences, mTrims, mUnderruns

### Community 42 - "audio_decoder.h"
Cohesion: 0.15
Nodes (4): buildAlacAtomCookie(), buildAlacMagicCookie(), codecConfig(), audio_engine_set_default_stream_values()

### Community 43 - "MediaCodecDecoder"
Cohesion: 0.10
Nodes (15): LatencyProbe, CAP, Entry, ptsUs, queueNs, mBuf, mRead, mWrite (+7 more)

### Community 45 - "DelayTracker"
Cohesion: 0.08
Nodes (20): DelayTracker, BASE_CREEP_SHIFT, BUCKET_MS, BUCKET_NS, FORGET_SHIFT, mAdaptive, MAX_CUSHION_MS, mBaseNs (+12 more)

### Community 48 - "LatencyReporter"
Cohesion: 0.10
Nodes (18): LatencyReporter, Debug, held, maxUs, meanUs, mHeld, mLogging, mMaxNs (+10 more)

### Community 50 - "SystemActions"
Cohesion: 0.05
Nodes (29): SystemActions, glass(), GlassMode, Matte, Refraction, GlassPanel, GlassState, DefaultSidebarActions (+21 more)

### Community 51 - "HomeAssistant.kt"
Cohesion: 0.15
Nodes (7): HaStatus, AuthFailed, Connecting, Error, Off, Ready, set

### Community 55 - "CodecConfig"
Cohesion: 0.25
Nodes (8): CodecConfig, aacProfile, channels, csd, csdLen, mime, preferName, sampleRate

### Community 58 - "HomeButtonService"
Cohesion: 0.07
Nodes (5): HomeButtonService, MainActivity, OnBackPressedCallback, NavKeys, ServiceKeeper

### Community 63 - "LogSink"
Cohesion: 0.14
Nodes (5): startCodec(), LogSink, mMethod, mObj, mVm

### Community 64 - "ConfigScreen.kt"
Cohesion: 0.40
Nodes (14): LauncherConfig, AirPlayPage(), ConfigDialog(), HomeAssistantPage(), JellyfinPage(), MenuKeyRow(), NavRow(), rowBackground() (+6 more)

### Community 66 - "AudioConfig"
Cohesion: 0.22
Nodes (9): AudioConfig, benchmarkLog, forceSwAac, forceSwAlac, lowLatency, oboeBufferFrames, percentilePct, realtimePriority (+1 more)

### Community 68 - "AppEntry"
Cohesion: 0.10
Nodes (4): AirPlay, AppEntry, AppRepository, MediaActions

### Community 70 - "native_bridge.cpp"
Cohesion: 0.08
Nodes (42): android_dnssd_get_airplay_txt_count(), android_dnssd_get_airplay_txt_key(), android_dnssd_get_airplay_txt_val(), android_dnssd_get_raop_servname(), android_dnssd_get_raop_txt_count(), android_dnssd_get_raop_txt_key(), android_dnssd_get_raop_txt_val(), android_dnssd_set_codecs() (+34 more)

### Community 75 - "Backdrop.kt"
Cohesion: 0.09
Nodes (7): Sun, paintBackdrop(), paintGlow(), paintGlowLight(), Scene, Scenes, withAlpha()

### Community 78 - "CreatedDecoder"
Cohesion: 0.12
Nodes (8): Decoder, audio_engine_start(), CreatedDecoder, ct, decoder, format, CodecFormat, spf

### Community 92 - "VideoPipeline"
Cohesion: 0.07
Nodes (3): EglCore, VideoPipeline, GlassRenderer

### Community 94 - "Screens.kt"
Cohesion: 0.08
Nodes (12): isOk(), okKey(), PressState, AppsScreen(), fadeEdges(), fadeRightEdge(), fadeTopEdge(), RowTitle() (+4 more)

### Community 107 - "AudioDebugData"
Cohesion: 0.33
Nodes (5): AudioDebugData, decode, decodeErrors, output, timeline

### Community 126 - "Services"
Cohesion: 0.36
Nodes (5): JellyfinApp, JellyfinApps, Places, Services, StreamService

### Community 205 - "AirPlayActivity"
Cohesion: 0.15
Nodes (5): AirPlayActivity, Mirror(), Callback, Pin(), Screen()

### Community 295 - "JellyfinStatus"
Cohesion: 0.33
Nodes (6): JellyfinStatus, Error, Loading, LoggedOut, Ready, WaitingForCode

### Community 297 - "ThemeMode"
Cohesion: 0.50
Nodes (4): ThemeMode, AUTO, DARK, LIGHT

## Knowledge Gaps
- **163 isolated node(s):** `Error`, `Loading`, `LoggedOut`, `Ready`, `WaitingForCode` (+158 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 690 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **263 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AudioEngine` connect `AudioEngine` to `AudioConfig`, `android_raop_callbacks.c`, `TimelineBuffer`, `audio_decoder.h`, `AudioDebugData`, `CreatedDecoder`, `LatencyReporter`, `AudioOutput`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `VideoRenderer` (e.g. with `onCreate()` and `DecoderSelector`) actually correct?**
  _`VideoRenderer` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Error`, `Loading`, `LoggedOut` to the rest of the system?**
  _163 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `OboeCallbacks` be split into smaller, more focused modules?**
  _Cohesion score 0.12105263157894737 - nodes in this community are weakly interconnected._
- **Why does `VideoRenderer` connect `VideoRenderer` to `AirPlayService.kt`, `DecoderSelector`, `VideoPipeline`?**
  _High betweenness centrality (0.036) - this node is a cross-community bridge._
- **Should `android_raop_callbacks.c` be split into smaller, more focused modules?**
  _Cohesion score 0.0653061224489796 - nodes in this community are weakly interconnected._
- **Why does `TimelineBuffer` connect `TimelineBuffer` to `OboeCallbacks`, `TimelineMetrics`, `SpscRing`, `audio_decoder.h`, `MediaCodecDecoder`, `DelayTracker`, `CreatedDecoder`, `LatencyReporter`, `AudioEngine`, `AudioOutput`, `.debugInfo`?**
  _High betweenness centrality (0.034) - this node is a cross-community bridge._