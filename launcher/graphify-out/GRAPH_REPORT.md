# Graph Report - launcher  (2026-11-22)

## Corpus Check
- 79 files · ~66,085 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 23 file(s) not represented in the graph (top: .ttf 9, .xml 8, .properties 2)
- WARNING: 23 of 106 files seen (22%) have no supported extractor and are missing from this graph entirely. It may look complete while representing only part of the codebase.

## Summary
- 1441 nodes · 2869 edges · 202 communities (36 shown, 166 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 141 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c1da71d0`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- OboeCallbacks
- android_raop_callbacks.c
- SpscRing
- TimelineBuffer
- MediaActions
- Sidebar.kt
- AudioEngine
- AudioOutput
- Sidebar.kt
- getvalue
- LauncherApp
- .debugInfo
- JellyfinClient.kt
- LauncherApp.kt
- VideoRenderer
- log
- TimelineMetrics
- Overlay.kt
- audio_decoder.h
- MediaCodecDecoder
- DelayTracker
- Services
- LatencyReporter
- NativeBridge
- audio_engine.h
- DecoderSelector
- MdiIcons.kt
- CodecConfig
- AirPlayService.kt
- RaopCallbackHandler
- MainActivity.kt
- Intent
- BootReceiver.kt
- HaPrefs
- NsdServiceManager.kt
- LogSink
- HaStatus
- AudioConfig
- SystemActions
- HomeAssistant.kt
- native_bridge.cpp
- .ensure
- AppEntry
- .onCreate
- Backdrop.kt
- CreatedDecoder
- VideoPipeline
- AirPlayActivity
- Doorbell.kt
- AirPlay-Empfänger (Modul `airplay`)
- AudioDebugData

## God Nodes (most connected - your core abstractions)
1. `AudioEngine` - 53 edges
2. `TimelineBuffer` - 43 edges
3. `AudioOutput` - 30 edges
4. `VideoRenderer` - 30 edges
5. `DelayTracker` - 29 edges
6. `NativeBridge` - 24 edges
7. `LauncherApp()` - 24 edges
8. `LatencyReporter` - 23 edges
9. `_get_env()` - 23 edges
10. `DecoderSelector` - 22 edges

## Surprising Connections (you probably didn't know these)
- `Start und Systemdienste (app/src/main/java/de/elias/launcher/)` --references--> `LauncherApp()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/ui/LauncherApp.kt
- `Medien (jellyfin/)` --references--> `MediaItem`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/jellyfin/JellyfinClient.kt
- `Oberfläche (ui/)` --references--> `appleSpring()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/ui/Theme.kt
- `Sidebar (sidebar/)` --references--> `DefaultSidebarActions`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/sidebar/Doorbell.kt
- `Oberfläche (ui/)` --references--> `okKey()`  [INFERRED]
  CODE-KARTE.md → app/src/main/java/de/elias/launcher/ui/LongPress.kt

## Import Cycles
- None detected.

## Communities (202 total, 166 thin omitted)

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

### Community 15 - "Sidebar.kt"
Cohesion: 0.07
Nodes (8): fmtTemp(), HaFilter, All, Light, Switch, S, LauncherColors, LauncherType

### Community 17 - "AudioEngine"
Cohesion: 0.07
Nodes (26): audio_engine_configure(), audio_engine_create(), audio_engine_destroy(), audio_engine_on_format(), audio_engine_pause(), audio_engine_set_client_gain(), DECODER_RETRY_NS, mApplied (+18 more)

### Community 18 - "AudioOutput"
Cohesion: 0.11
Nodes (14): AudioOutput, Debug, xrun, mCallbacks, mChannels, mClosing, mLastXrun, mLock (+6 more)

### Community 20 - "Sidebar.kt"
Cohesion: 0.12
Nodes (45): HaEntity, BackHeader(), DeviceTile(), disp(), FollowLauncherTheme(), HaView(), HeatTile(), HeatTrack() (+37 more)

### Community 22 - "getvalue"
Cohesion: 0.07
Nodes (16): AirPlayState, Genres, ThemeMode, AUTO, DARK, LIGHT, GlassMode, Matte (+8 more)

### Community 23 - "LauncherApp"
Cohesion: 0.07
Nodes (48): LauncherConfig, MediaItem, JellyfinState, LocalMarks, RemoteImage(), AppMenu(), MenuOption, sp() (+40 more)

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

### Community 47 - "Services"
Cohesion: 0.32
Nodes (5): JellyfinApp, JellyfinApps, Places, Services, StreamService

### Community 48 - "LatencyReporter"
Cohesion: 0.10
Nodes (18): LatencyReporter, Debug, held, maxUs, meanUs, mHeld, mLogging, mMaxNs (+10 more)

### Community 53 - "MdiIcons.kt"
Cohesion: 0.08
Nodes (5): MdiIcons, qrBitmap(), AppFonts, AvatarFile, rememberAvatar()

### Community 55 - "CodecConfig"
Cohesion: 0.25
Nodes (8): CodecConfig, aacProfile, channels, csd, csdLen, mime, preferName, sampleRate

### Community 56 - "AirPlayService.kt"
Cohesion: 0.11
Nodes (4): onBind(), onCreate(), AudioConfig, AudioRenderer

### Community 58 - "MainActivity.kt"
Cohesion: 0.15
Nodes (3): MainActivity, OnBackPressedCallback, NavKeys

### Community 61 - "HaPrefs"
Cohesion: 0.08
Nodes (5): HaPrefs, AuthExpired, HomeButtonService, BellButton(), DoorbellCard()

### Community 62 - "NsdServiceManager.kt"
Cohesion: 0.10
Nodes (9): MdnsService, NsdServiceManager, RegistrationListener, NetInfo, Kind, Ethernet, None, Wifi (+1 more)

### Community 63 - "LogSink"
Cohesion: 0.14
Nodes (5): startCodec(), LogSink, mMethod, mObj, mVm

### Community 64 - "HaStatus"
Cohesion: 0.33
Nodes (6): HaStatus, AuthFailed, Connecting, Error, Off, Ready

### Community 66 - "AudioConfig"
Cohesion: 0.22
Nodes (9): AudioConfig, benchmarkLog, forceSwAac, forceSwAlac, lowLatency, oboeBufferFrames, percentilePct, realtimePriority (+1 more)

### Community 68 - "SystemActions"
Cohesion: 0.06
Nodes (25): SystemActions, glass(), GlassPanel, GlassState, DefaultSidebarActions, SidebarController, SideView, Ha (+17 more)

### Community 70 - "native_bridge.cpp"
Cohesion: 0.08
Nodes (38): android_dnssd_get_airplay_txt_count(), android_dnssd_get_airplay_txt_key(), android_dnssd_get_airplay_txt_val(), android_dnssd_get_raop_servname(), android_dnssd_get_raop_txt_count(), android_dnssd_get_raop_txt_key(), android_dnssd_get_raop_txt_val(), android_dnssd_set_codecs() (+30 more)

### Community 72 - "AppEntry"
Cohesion: 0.11
Nodes (11): AppEntry, AppRepository, AppsScreen(), fadeEdges(), fadeRightEdge(), fadeTopEdge(), RowTitle(), sp() (+3 more)

### Community 75 - "Backdrop.kt"
Cohesion: 0.09
Nodes (7): Sun, paintBackdrop(), paintGlow(), paintGlowLight(), Scene, Scenes, withAlpha()

### Community 78 - "CreatedDecoder"
Cohesion: 0.12
Nodes (8): Decoder, audio_engine_start(), CreatedDecoder, ct, decoder, format, CodecFormat, spf

### Community 92 - "VideoPipeline"
Cohesion: 0.07
Nodes (3): EglCore, VideoPipeline, GlassRenderer

### Community 93 - "AirPlayActivity"
Cohesion: 0.15
Nodes (5): AirPlayActivity, Mirror(), Callback, Pin(), Screen()

### Community 94 - "Doorbell.kt"
Cohesion: 0.13
Nodes (3): okKey(), PressState, Oberfläche (ui/)

### Community 107 - "AudioDebugData"
Cohesion: 0.33
Nodes (5): AudioDebugData, decode, decodeErrors, output, timeline

## Knowledge Gaps
- **163 isolated node(s):** `Main`, `Settings`, `Wifi`, `Ha`, `All` (+158 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 597 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **166 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TimelineBuffer` connect `TimelineBuffer` to `OboeCallbacks`, `TimelineMetrics`, `SpscRing`, `audio_decoder.h`, `MediaCodecDecoder`, `DelayTracker`, `CreatedDecoder`, `LatencyReporter`, `AudioEngine`, `AudioOutput`, `.debugInfo`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `VideoRenderer` (e.g. with `onCreate()` and `DecoderSelector`) actually correct?**
  _`VideoRenderer` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Main`, `Settings`, `Wifi` to the rest of the system?**
  _163 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `OboeCallbacks` be split into smaller, more focused modules?**
  _Cohesion score 0.12105263157894737 - nodes in this community are weakly interconnected._
- **Why does `AudioEngine` connect `AudioEngine` to `AudioConfig`, `android_raop_callbacks.c`, `TimelineBuffer`, `audio_decoder.h`, `AudioDebugData`, `CreatedDecoder`, `LatencyReporter`, `AudioOutput`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **Should `android_raop_callbacks.c` be split into smaller, more focused modules?**
  _Cohesion score 0.0653061224489796 - nodes in this community are weakly interconnected._
- **Why does `LauncherApp()` connect `LauncherApp` to `SystemActions`, `AppEntry`, `Backdrop.kt`, `Sidebar.kt`, `MainActivity.kt`, `LauncherApp.kt`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._