# Graph Report - launcher  (2026-11-19)

## Corpus Check
- 191 files · ~217,223 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 36 file(s) not represented in the graph (top: .xml 8, .otf 8, .apk 6)

## Summary
- 2972 nodes · 7449 edges · 135 communities (91 shown, 44 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 1124 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- llhttp_status
- crypto.c
- native_bridge.cpp
- plist_t
- Node
- llhttp__internal__run
- android_raop_callbacks.c
- raop.h
- TimelineBuffer
- srp.c
- api.c
- bplist.c
- airplay_video.c
- Sidebar.kt
- llhttp_method
- raop.c
- SystemActions
- AudioEngine
- AudioOutput
- node_first_child
- Sidebar.kt
- raop_rtp_mirror_thread
- getvalue
- Discover.kt
- llhttp_errno
- LauncherApp.kt
- Key
- conn_request
- Screens.kt
- README.md
- xplist.c
- jplist.c
- VideoRenderer
- ConfigScreen.kt
- out-default.c
- http_handler_set_property
- omg_hax.c
- llhttp_settings_s
- http_request.c
- logger_log
- plist_err_t
- Overlay.kt
- audio_decoder.h
- MediaCodecDecoder
- time64.c
- DelayTracker
- Structure
- TERMS AND CONDITIONS
- LatencyReporter
- plist_free
- NativeBridge
- llhttp__internal_s
- DecoderSelector
- plist_set_element_val
- jplist.c
- logger_t
- AirPlayService.kt
- RaopCallbackHandler
- MainActivity.kt
- JellyfinClient.kt
- HomeButtonService
- HaPrefs
- NsdServiceManager.kt
- LogSink
- playback_info_s
- Dictionary
- AudioConfig
- src/plist.h
- DefaultSidebarActions
- HomeAssistant.kt
- native_bridge.cpp
- Avatar.kt
- MediaActions
- Real
- Data
- Backdrop.kt
- http_response.c
- CreatedDecoder
- llhttp_lenient_flags
- plist_free_data
- fairplay_playfair.c
- llhttp_flags
- llhttp.h
- String
- Icons.kt
- http.c
- reset_type_e
- llhttp_init
- string
- raop_ntp.c
- raop_rtp.c
- VideoPipeline
- AirPlayActivity
- LongPress.kt
- AirPlay-Empfänger (Modul `airplay`)
- AudioDebugData
- out-limd.c
- GlassRenderer.kt
- EglCore
- HaFilter
- raop_rtp_mirror.c
- readNet
- raop_buffer_enqueue
- .run
- utils.c
- video_codec_e
- AudioConfig

## God Nodes (most connected - your core abstractions)
1. `plist_t` - 159 edges
2. `llhttp_status` - 100 edges
3. `llhttp__internal__run()` - 92 edges
4. `Node` - 76 edges
5. `AudioEngine` - 53 edges
6. `logger_log()` - 53 edges
7. `plist_err_t` - 53 edges
8. `raop_handler_setup()` - 51 edges
9. `llhttp_method` - 48 edges
10. `TimelineBuffer` - 43 edges

## Surprising Connections (you probably didn't know these)
- `Java_de_elias_launcher_airplay_NativeBridge_nativeSetDefaultStreamValues()` --calls--> `audio_engine_set_default_stream_values()`  [INFERRED]
  airplay/src/main/cpp/native_bridge.cpp → airplay/src/main/cpp/audio_engine.cpp
- `raop_handler_info()` --calls--> `utils_parse_hex()`  [INFERRED]
  airplay/src/main/cpp/third_party/UxPlay/lib/raop_handlers.h → airplay/src/main/cpp/third_party/UxPlay/lib/utils.c
- `plist_data_hash()` --calls--> `plist_get_data()`  [INFERRED]
  airplay/src/main/cpp/third_party/libplist/src/bplist.c → airplay/src/main/cpp/third_party/libplist/src/plist.c
- `raop_rtp_resend_callback()` --calls--> `logger_log()`  [INFERRED]
  airplay/src/main/cpp/third_party/UxPlay/lib/raop_rtp.c → airplay/src/main/cpp/third_party/UxPlay/lib/logger.c
- `httpd_accept_connection()` --calls--> `netutils_get_address()`  [INFERRED]
  airplay/src/main/cpp/third_party/UxPlay/lib/httpd.c → airplay/src/main/cpp/third_party/UxPlay/lib/netutils.c

## Import Cycles
- None detected.

## Communities (135 total, 44 thin omitted)

### Community 0 - "llhttp_status"
Cohesion: 0.02
Nodes (100): llhttp_status, HTTP_STATUS_ACCEPTED, HTTP_STATUS_ALREADY_REPORTED, HTTP_STATUS_BAD_GATEWAY, HTTP_STATUS_BAD_REQUEST, HTTP_STATUS_BANDWIDTH_LIMIT_EXCEEDED, HTTP_STATUS_BLOCKED_BY_PARENTAL_CONTROL, HTTP_STATUS_CLIENT_CLOSED_LOAD_BALANCED_REQUEST (+92 more)

### Community 1 - "crypto.c"
Cohesion: 0.05
Nodes (83): aes_cbc_decrypt(), aes_cbc_destroy(), aes_cbc_encrypt(), aes_cbc_init(), aes_cbc_reset(), aes_ctr_decrypt(), aes_ctr_destroy(), aes_ctr_encrypt() (+75 more)

### Community 2 - "native_bridge.cpp"
Cohesion: 0.08
Nodes (41): android_dnssd_get_airplay_txt_count(), android_dnssd_get_airplay_txt_key(), android_dnssd_get_airplay_txt_val(), android_dnssd_get_raop_servname(), android_dnssd_get_raop_txt_count(), android_dnssd_get_raop_txt_key(), android_dnssd_get_raop_txt_val(), android_dnssd_set_codecs() (+33 more)

### Community 3 - "plist_t"
Cohesion: 0.07
Nodes (77): plist_dict_iter, plist_t, node_prev_sibling(), Array::Remove(), Boolean::GetValue(), Date::GetValue(), Integer::GetUnsignedValue(), Integer::GetValue() (+69 more)

### Community 4 - "Node"
Cohesion: 0.03
Nodes (36): Array, _array, Date, Node, _node, _parent, plist_array_iter, plist_type (+28 more)

### Community 5 - "llhttp__internal__run"
Cohesion: 0.08
Nodes (61): llhttp__internal__c_and_flags(), llhttp__internal__c_is_equal_content_length(), llhttp__internal__c_is_equal_method(), llhttp__internal__c_is_equal_upgrade(), llhttp__internal__c_load_header_state(), llhttp__internal__c_load_http_major(), llhttp__internal__c_load_http_minor(), llhttp__internal__c_load_initial_message_completed() (+53 more)

### Community 6 - "android_raop_callbacks.c"
Cohesion: 0.06
Nodes (38): android_callback_ctx_t, android_callbacks_fill(), android_callbacks_init(), android_callbacks_update_playback_info(), _audio_get_format(), _audio_process(), _audio_remote_control_id(), _audio_set_client_volume() (+30 more)

### Community 7 - "raop.h"
Cohesion: 0.14
Nodes (11): airplay_video_s, media_item_s, media_item_t, logger_callback_t, logger_s, playback_info_t, raop_callbacks_s, cls (+3 more)

### Community 8 - "TimelineBuffer"
Cohesion: 0.04
Nodes (38): SpscRing, mBuf, mCapacity, mRead, mWrite, TimelineBuffer, Debug, backlogMs (+30 more)

### Community 9 - "srp.c"
Cohesion: 0.13
Nodes (34): calculate_H_AMK(), calculate_M(), calculate_x(), delete_ng(), H_nn_orig(), H_nn_rfc5054(), H_ns(), handle_error() (+26 more)

### Community 10 - "api.c"
Cohesion: 0.07
Nodes (55): llhttp__debug(), llhttp_errno_name(), llhttp_execute(), llhttp_finish(), llhttp_free(), llhttp_get_errno(), llhttp_get_error_pos(), llhttp_get_error_reason() (+47 more)

### Community 11 - "bplist.c"
Cohesion: 0.10
Nodes (45): node_create(), get_unaligned_16(), get_unaligned_32(), get_unaligned_64(), is_ascii_string(), parse_array_node(), parse_bin_node(), parse_bin_node_at_index() (+37 more)

### Community 12 - "airplay_video.c"
Cohesion: 0.11
Nodes (48): adjust_master_playlist(), adjust_yt_condensed_playlist(), airplay_video_destroy(), airplay_video_init(), airplay_video_t, analyze_media_playlist(), create_media_data_store(), create_media_uri_table() (+40 more)

### Community 13 - "Sidebar.kt"
Cohesion: 0.06
Nodes (5): glass(), BellButton(), DoorbellCard(), LauncherColors, LauncherType

### Community 14 - "llhttp_method"
Cohesion: 0.04
Nodes (48): llhttp_method, HTTP_ACL, HTTP_ANNOUNCE, HTTP_BIND, HTTP_CHECKOUT, HTTP_CONNECT, HTTP_COPY, HTTP_DELETE (+40 more)

### Community 15 - "raop.c"
Cohesion: 0.11
Nodes (30): android_callbacks_destroy(), Java_de_elias_launcher_airplay_NativeBridge_nativeInit(), netutils_cleanup(), netutils_get_address(), netutils_init(), netutils_parse_address(), pairing_destroy(), raop_callbacks_t (+22 more)

### Community 16 - "SystemActions"
Cohesion: 0.10
Nodes (15): SystemActions, GlassPanel, GlassState, SidebarController, SideView, Ha, Main, Settings (+7 more)

### Community 17 - "AudioEngine"
Cohesion: 0.06
Nodes (27): audio_engine_create(), audio_engine_destroy(), audio_engine_get_debug(), audio_engine_on_format(), audio_engine_pause(), audio_engine_set_client_gain(), audio_engine_start(), DECODER_RETRY_NS (+19 more)

### Community 18 - "AudioOutput"
Cohesion: 0.06
Nodes (20): AudioOutput, Debug, xrun, mCallbacks, mChannels, mClosing, mLastXrun, mLock (+12 more)

### Community 19 - "node_first_child"
Cohesion: 0.11
Nodes (36): main(), copy_func_t, node, node_list, begin, count, end, node_list_t (+28 more)

### Community 20 - "Sidebar.kt"
Cohesion: 0.10
Nodes (23): HaEntity, BackHeader(), DeviceTile(), disp(), fmtTemp(), HaView(), HeatTile(), HeatTrack() (+15 more)

### Community 21 - "raop_rtp_mirror_thread"
Cohesion: 0.15
Nodes (17): byteutils_get_float(), byteutils_get_int(), byteutils_get_int_be(), byteutils_get_long(), byteutils_get_long_be(), byteutils_get_ntp_timestamp(), byteutils_get_short(), byteutils_get_short_be() (+9 more)

### Community 22 - "getvalue"
Cohesion: 0.06
Nodes (18): AirPlayState, Genres, ThemeMode, AUTO, DARK, LIGHT, GlassMode, Matte (+10 more)

### Community 23 - "Discover.kt"
Cohesion: 0.15
Nodes (22): MediaItem, Badge(), CarouselDots(), ConnectPanel(), ContinueWatchingRow(), DetailDialog(), DiscoverScreen(), FeaturedHero() (+14 more)

### Community 24 - "llhttp_errno"
Cohesion: 0.05
Nodes (38): llhttp_errno, HPE_CB_CHUNK_COMPLETE, HPE_CB_CHUNK_EXTENSION_NAME_COMPLETE, HPE_CB_CHUNK_EXTENSION_VALUE_COMPLETE, HPE_CB_CHUNK_HEADER, HPE_CB_HEADER_FIELD_COMPLETE, HPE_CB_HEADER_VALUE_COMPLETE, HPE_CB_HEADERS_COMPLETE (+30 more)

### Community 25 - "LauncherApp.kt"
Cohesion: 0.09
Nodes (10): AppEntry, AppRepository, AppMenu(), MenuOption, sp(), LauncherApp(), MinimalBringIntoView, MinimalScroll() (+2 more)

### Community 26 - "Key"
Cohesion: 0.21
Nodes (6): Key, Key::Clone(), Key::GetValue(), Key::Key(), Key::SetValue(), plist_set_key_val()

### Community 27 - "conn_request"
Cohesion: 0.16
Nodes (17): httpd_accept_connection(), httpd_add_connection(), httpd_count_connection_type(), httpd_destroy(), httpd_get_connection_by_type(), httpd_get_connection_socket(), httpd_get_connection_socket_by_type(), httpd_get_connection_typename() (+9 more)

### Community 28 - "Screens.kt"
Cohesion: 0.10
Nodes (9): AppsScreen(), fadeEdges(), fadeRightEdge(), fadeTopEdge(), RowTitle(), sp(), StartScreen(), AddTile() (+1 more)

### Community 29 - "README.md"
Cohesion: 0.06
Nodes (35): 0. Problems in compiling UxPlay., 1. **Avahi/DNS_SD Bonjour/Zeroconf issues**, 2. uxplay starts, but stalls after "Initialized server socket(s)" appears, *with the server name showing on the client* (but the client fails to connect when the UxPlay server is selected)., 3. Problems *after* the client-server connection has been made:, 4. GStreamer issues (missing plugins, etc.):, 5. Mirror screen freezes (a network problem):, 6. Protocol issues (with decryption of the encrypted audio and video streams sent by the client)., After installation: (+27 more)

### Community 30 - "xplist.c"
Cohesion: 0.12
Nodes (22): plist_bin_set_debug(), plist_json_set_debug(), plist_ostep_set_debug(), plist_set_debug(), dtostr(), find_char(), find_next(), find_str() (+14 more)

### Community 31 - "jplist.c"
Cohesion: 0.17
Nodes (33): plist_from_bin(), parse_array(), parse_object(), parse_primitive(), parse_string(), plist_from_json(), strndup(), unescape_string() (+25 more)

### Community 33 - "ConfigScreen.kt"
Cohesion: 0.17
Nodes (18): LauncherConfig, JellyfinApp, JellyfinApps, Places, Services, StreamService, AirPlayPage(), ConfigDialog() (+10 more)

### Community 34 - "out-default.c"
Cohesion: 0.14
Nodes (15): serialize_plist(), hash_table_destroy(), hash_table_insert(), hash_table_lookup(), hash_table_new(), hash_table_remove(), dtostr(), _node_estimate_size() (+7 more)

### Community 35 - "http_handler_set_property"
Cohesion: 0.40
Nodes (11): hls_get_current_video(), http_handler_fpsetup2(), http_handler_get_property(), http_handler_hls(), http_handler_playback_info(), http_handler_rate(), http_handler_reverse(), http_handler_scrub() (+3 more)

### Community 36 - "omg_hax.c"
Cohesion: 0.16
Nodes (25): garble(), weird_rol32(), weird_rol8(), weird_ror8(), cycle(), decrypt_key(), decrypt_sap(), decryptMessage() (+17 more)

### Community 37 - "llhttp_settings_s"
Cohesion: 0.07
Nodes (26): llhttp_settings_s, on_body, on_chunk_complete, on_chunk_extension_name, on_chunk_extension_name_complete, on_chunk_extension_value, on_chunk_extension_value_complete, on_chunk_header (+18 more)

### Community 38 - "http_request.c"
Cohesion: 0.16
Nodes (21): http_request_add_data(), http_request_destroy(), http_request_get_error_description(), http_request_get_error_name(), http_request_get_header(), http_request_get_header_string(), http_request_get_method(), http_request_get_protocol() (+13 more)

### Community 39 - "logger_log"
Cohesion: 0.36
Nodes (17): http_request_get_data(), http_response_add_header(), logger_log(), pairing_session_set_setup_status(), raop_handler_audiomode(), raop_handler_feedback(), raop_handler_flush(), raop_handler_fpsetup() (+9 more)

### Community 40 - "plist_err_t"
Cohesion: 0.16
Nodes (21): plist_err_t, plist_write_options_t, plist_to_json(), plist_to_json_with_options(), dtostr(), _node_estimate_size(), node_estimate_size(), node_to_openstep() (+13 more)

### Community 42 - "audio_decoder.h"
Cohesion: 0.10
Nodes (15): buildAlacAtomCookie(), buildAlacMagicCookie(), CodecConfig, aacProfile, channels, csd, csdLen, codecConfig() (+7 more)

### Community 43 - "MediaCodecDecoder"
Cohesion: 0.10
Nodes (15): LatencyProbe, CAP, Entry, ptsUs, queueNs, mBuf, mRead, mWrite (+7 more)

### Community 44 - "time64.c"
Cohesion: 0.19
Nodes (20): asctime64_r(), check_tm(), cmp_date(), copy_TM64_to_tm(), copy_tm_to_TM64(), ctime64_r(), cycle_offset(), date_in_safe_range() (+12 more)

### Community 45 - "DelayTracker"
Cohesion: 0.09
Nodes (20): DelayTracker, BASE_CREEP_SHIFT, BUCKET_MS, BUCKET_NS, FORGET_SHIFT, mAdaptive, MAX_CUSHION_MS, mBaseNs (+12 more)

### Community 46 - "Structure"
Cohesion: 0.14
Nodes (13): plist_format_t, Structure, plist_from_memory(), plist_is_binary(), plist_read_from_file(), ImportStruct(), Structure::FromBin(), Structure::FromMemory() (+5 more)

### Community 47 - "TERMS AND CONDITIONS"
Cohesion: 0.08
Nodes (23): 0. Definitions., 10. Automatic Licensing of Downstream Recipients., 11. Patents., 12. No Surrender of Others' Freedom., 13. Use with the GNU Affero General Public License., 14. Revised Versions of this License., 15. Disclaimer of Warranty., 16. Limitation of Liability. (+15 more)

### Community 48 - "LatencyReporter"
Cohesion: 0.12
Nodes (15): LatencyReporter, Debug, held, maxUs, meanUs, mHeld, mLogging, mMaxNs (+7 more)

### Community 49 - "plist_free"
Cohesion: 0.26
Nodes (15): Array::operator=(), Boolean::operator=(), Data::operator=(), Date::operator=(), Integer::operator=(), Key::operator=(), node_from_openstep(), parse_dict_data() (+7 more)

### Community 51 - "llhttp__internal_s"
Cohesion: 0.09
Nodes (22): llhttp__internal_s, content_length, _current, data, error, error_pos, finish, flags (+14 more)

### Community 53 - "plist_set_element_val"
Cohesion: 0.08
Nodes (15): Boolean, Integer, Boolean::Boolean(), Boolean::Clone(), Boolean::SetValue(), Integer::Clone(), Integer::Integer(), Integer::isNegative() (+7 more)

### Community 54 - "jplist.c"
Cohesion: 0.11
Nodes (17): plist_bin_deinit(), plist_bin_init(), dtostr(), _node_estimate_size(), node_estimate_size(), node_to_json(), num_digits_i(), num_digits_u() (+9 more)

### Community 55 - "logger_t"
Cohesion: 0.48
Nodes (6): logger_destroy(), logger_get_level(), logger_init(), logger_set_callback(), logger_set_level(), logger_t

### Community 56 - "AirPlayService.kt"
Cohesion: 0.10
Nodes (3): AirPlay, onBind(), onCreate()

### Community 58 - "MainActivity.kt"
Cohesion: 0.10
Nodes (5): BootReceiver, MainActivity, OnBackPressedCallback, NavKeys, ServiceKeeper

### Community 59 - "JellyfinClient.kt"
Cohesion: 0.26
Nodes (3): QuickConnectStart, SeerrClient, HttpException

### Community 62 - "NsdServiceManager.kt"
Cohesion: 0.19
Nodes (3): MdnsService, NsdServiceManager, RegistrationListener

### Community 63 - "LogSink"
Cohesion: 0.15
Nodes (5): LogSink, mMethod, mObj, mVm, netutils_init_socket()

### Community 64 - "playback_info_s"
Cohesion: 0.13
Nodes (15): playback_info_s, duration, loadedTimeRanges, num_loaded_time_ranges, num_seekable_time_ranges, playback_buffer_empty, playback_buffer_full, playback_likely_to_keep_up (+7 more)

### Community 65 - "Dictionary"
Cohesion: 0.10
Nodes (11): Dictionary, _map, Dictionary::Begin(), Dictionary::Dictionary(), Dictionary::End(), dictionary_fill(), Dictionary::Find(), Dictionary::GetNodeKey() (+3 more)

### Community 66 - "AudioConfig"
Cohesion: 0.15
Nodes (10): audio_engine_configure(), AudioConfig, benchmarkLog, forceSwAac, forceSwAlac, lowLatency, oboeBufferFrames, percentilePct (+2 more)

### Community 67 - "src/plist.h"
Cohesion: 0.16
Nodes (8): dtostr(), _node_estimate_size(), node_estimate_size(), node_to_string(), num_digits_i(), num_digits_u(), _plist_write_to_strbuf(), plist_write_to_string_plutil()

### Community 69 - "HomeAssistant.kt"
Cohesion: 0.14
Nodes (9): HaStatus, AuthFailed, Connecting, Error, Off, Ready, prefs(), set (+1 more)

### Community 70 - "native_bridge.cpp"
Cohesion: 0.21
Nodes (23): Java_de_elias_launcher_airplay_NativeBridge_nativeDestroy(), Java_de_elias_launcher_airplay_NativeBridge_nativeGetAirplayTxtRecords(), Java_de_elias_launcher_airplay_NativeBridge_nativeGetRaopServiceName(), Java_de_elias_launcher_airplay_NativeBridge_nativeGetRaopTxtRecords(), Java_de_elias_launcher_airplay_NativeBridge_nativeGetServerName(), Java_de_elias_launcher_airplay_NativeBridge_nativeServerAudioConfigure(), Java_de_elias_launcher_airplay_NativeBridge_nativeServerAudioDebug(), Java_de_elias_launcher_airplay_NativeBridge_nativeServerAudioFormat() (+15 more)

### Community 71 - "Avatar.kt"
Cohesion: 0.17
Nodes (3): qrBitmap(), AvatarFile, rememberAvatar()

### Community 73 - "Real"
Cohesion: 0.22
Nodes (5): Real, plist_set_real_val(), Real::Clone(), Real::Real(), Real::SetValue()

### Community 74 - "Data"
Cohesion: 0.23
Nodes (6): Data, Data::Clone(), Data::Data(), Data::GetValue(), Data::SetValue(), plist_set_data_val()

### Community 75 - "Backdrop.kt"
Cohesion: 0.09
Nodes (7): Sun, paintBackdrop(), paintGlow(), paintGlowLight(), Scene, Scenes, withAlpha()

### Community 76 - "http_response.c"
Cohesion: 0.26
Nodes (12): fcup_request(), http_response_add_data(), http_response_create(), http_response_destroy(), http_response_finish(), http_response_get_data(), http_response_get_disconnect(), http_response_init() (+4 more)

### Community 78 - "CreatedDecoder"
Cohesion: 0.18
Nodes (7): Decoder, CreatedDecoder, ct, decoder, format, CodecFormat, spf

### Community 79 - "llhttp_lenient_flags"
Cohesion: 0.18
Nodes (11): llhttp_lenient_flags, LENIENT_CHUNKED_LENGTH, LENIENT_DATA_AFTER_CLOSE, LENIENT_HEADERS, LENIENT_KEEP_ALIVE, LENIENT_OPTIONAL_CR_BEFORE_LF, LENIENT_OPTIONAL_CRLF_AFTER_CHUNK, LENIENT_OPTIONAL_LF_AFTER_CR (+3 more)

### Community 80 - "plist_free_data"
Cohesion: 0.32
Nodes (10): node_detach(), _plist_array_post_set(), plist_array_set_item(), plist_copy_node(), plist_copy_node_shallow(), plist_free_children(), _plist_free_data(), plist_free_data() (+2 more)

### Community 81 - "fairplay_playfair.c"
Cohesion: 0.24
Nodes (7): fairplay_decrypt(), fairplay_destroy(), fairplay_handshake(), fairplay_init(), fairplay_setup(), conn_destroy(), conn_init()

### Community 82 - "llhttp_flags"
Cohesion: 0.20
Nodes (10): llhttp_flags, F_CHUNKED, F_CONNECTION_CLOSE, F_CONNECTION_KEEP_ALIVE, F_CONNECTION_UPGRADE, F_CONTENT_LENGTH, F_SKIPBODY, F_TRAILING (+2 more)

### Community 83 - "llhttp.h"
Cohesion: 0.22
Nodes (8): llhttp_finish, HTTP_FINISH_SAFE, HTTP_FINISH_SAFE_WITH_CB, HTTP_FINISH_UNSAFE, llhttp_type, HTTP_BOTH, HTTP_REQUEST, HTTP_RESPONSE

### Community 84 - "String"
Cohesion: 0.21
Nodes (6): String, plist_set_string_val(), String::Clone(), String::GetValue(), String::SetValue(), String::String()

### Community 85 - "Icons.kt"
Cohesion: 0.31
Nodes (19): AirPlayIcon(), BackIcon(), BellIcon(), BulbIcon(), CheckIcon(), circle24(), GearIcon(), HouseIcon() (+11 more)

### Community 86 - "http.c"
Cohesion: 0.50
Nodes (6): wasm_on_headers_complete_wrap(), llhttp__after_headers_complete(), llhttp__after_message_complete(), llhttp__before_headers_complete(), llhttp_message_needs_eof(), llhttp_should_keep_alive()

### Community 87 - "reset_type_e"
Cohesion: 0.25
Nodes (8): reset_type_e, RESET_TYPE_HLS_CONN_CLOSED, RESET_TYPE_HLS_EOS, RESET_TYPE_HLS_SHUTDOWN, RESET_TYPE_NOHOLD, RESET_TYPE_ON_VIDEO_PLAY, RESET_TYPE_RTP_SHUTDOWN, RESET_TYPE_RTP_TO_HLS_TEARDOWN

### Community 88 - "llhttp_init"
Cohesion: 0.33
Nodes (5): llhttp_alloc(), llhttp_init(), llhttp_reset(), llhttp_settings_init(), llhttp__internal_init()

### Community 89 - "string"
Cohesion: 0.16
Nodes (7): F(), G(), H(), I(), modified_md5(), rol(), swap()

### Community 90 - "raop_ntp.c"
Cohesion: 0.19
Nodes (16): get_local_time(), raop_ntp_convert_local_time(), raop_ntp_convert_remote_time(), raop_ntp_destroy(), raop_ntp_get_local_time(), raop_ntp_get_port(), raop_ntp_get_remote_time(), raop_ntp_get_video_arrival_offset() (+8 more)

### Community 91 - "raop_rtp.c"
Cohesion: 0.20
Nodes (16): raop_rtp_destroy(), raop_rtp_flush(), raop_rtp_init(), raop_rtp_init_sockets(), raop_rtp_is_running(), raop_rtp_parse_remote(), raop_rtp_process_events(), raop_rtp_remote_control_id() (+8 more)

### Community 93 - "AirPlayActivity"
Cohesion: 0.15
Nodes (5): AirPlayActivity, Mirror(), Callback, Pin(), Screen()

### Community 107 - "AudioDebugData"
Cohesion: 0.33
Nodes (5): AudioDebugData, decode, decodeErrors, output, timeline

### Community 108 - "out-limd.c"
Cohesion: 0.20
Nodes (10): base64decode(), base64encode(), dtostr(), _node_estimate_size(), node_estimate_size(), node_to_string(), num_digits_i(), num_digits_u() (+2 more)

### Community 111 - "HaFilter"
Cohesion: 0.50
Nodes (4): HaFilter, All, Light, Switch

### Community 118 - "raop_rtp_mirror.c"
Cohesion: 0.30
Nodes (7): raop_rtp_mirror_destroy(), raop_rtp_mirror_init(), raop_rtp_mirror_init_aes(), raop_rtp_mirror_init_socket(), raop_rtp_mirror_parse_remote(), raop_rtp_mirror_start(), raop_rtp_mirror_stop()

### Community 120 - "readNet"
Cohesion: 0.22
Nodes (6): NetInfo, Kind, Ethernet, None, Wifi, readNet()

### Community 121 - "raop_buffer_enqueue"
Cohesion: 0.29
Nodes (8): raop_buffer_decrypt(), raop_buffer_dequeue(), raop_buffer_destroy(), raop_buffer_enqueue(), raop_buffer_flush(), raop_buffer_handle_resends(), raop_buffer_init(), seqnum_cmp()

### Community 123 - "utils.c"
Cohesion: 0.22
Nodes (5): gmt_time_string(), parse_int(), utils_ipaddress_to_string(), utils_parse_hex(), utils_strip_data_from_plist_xml()

### Community 125 - "video_codec_e"
Cohesion: 0.50
Nodes (4): video_codec_e, VIDEO_CODEC_H264, VIDEO_CODEC_H265, VIDEO_CODEC_UNKNOWN

## Knowledge Gaps
- **457 isolated node(s):** `Main`, `Settings`, `Wifi`, `Ha`, `All` (+452 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 961 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **44 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TimelineBuffer` connect `TimelineBuffer` to `AudioConfig`, `audio_decoder.h`, `MediaCodecDecoder`, `DelayTracker`, `AudioEngine`, `AudioOutput`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **Are the 28 inferred relationships involving `llhttp__internal__run()` (e.g. with `llhttp__on_body()` and `llhttp__on_chunk_complete()`) actually correct?**
  _`llhttp__internal__run()` has 28 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Main`, `Settings`, `Wifi` to the rest of the system?**
  _457 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `llhttp_status` be split into smaller, more focused modules?**
  _Cohesion score 0.02 - nodes in this community are weakly interconnected._
- **Why does `llhttp_status` connect `llhttp_status` to `llhttp.h`?**
  _High betweenness centrality (0.041) - this node is a cross-community bridge._
- **Should `crypto.c` be split into smaller, more focused modules?**
  _Cohesion score 0.05412371134020619 - nodes in this community are weakly interconnected._
- **Why does `Dictionary` connect `Dictionary` to `string`, `Node`, `Structure`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._