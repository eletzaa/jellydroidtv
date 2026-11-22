# AirPlay-Empfänger für Jellydroid.
# Grundlage: android-airplay-server (jqssun, GPL-3.0) mit UxPlay (GPL-3.0), libplist (LGPL-2.1).
# Anders als dort ohne FFmpeg und ohne selbst gebautes OpenSSL: OpenSSL und Oboe kommen als
# fertige Bibliotheken aus Maven, damit der Build auch unter Windows ohne Perl/Make läuft.
cmake_minimum_required(VERSION 3.22.1)
project(airplay_native C CXX)

set(CMAKE_C_STANDARD 17)
set(CMAKE_CXX_STANDARD 17)

set(UXPLAY_LIB third_party/UxPlay/lib)

# Von Gradle entpackt (Task extractNativeDeps), siehe build.gradle.kts
foreach(lib crypto oboe)
    add_library(${lib} SHARED IMPORTED)
    set_target_properties(${lib} PROPERTIES
        IMPORTED_LOCATION ${NATIVE_DEPS}/jniLibs/${ANDROID_ABI}/lib${lib}.so
        INTERFACE_INCLUDE_DIRECTORIES ${NATIVE_DEPS}/include/${lib})
endforeach()

# ---------- libplist ----------
set(PLIST_SRC third_party/libplist/src)
set(PLIST_CNARY third_party/libplist/libcnary)
add_library(plist STATIC
    ${PLIST_SRC}/base64.c
    ${PLIST_SRC}/bplist.c
    ${PLIST_SRC}/bytearray.c
    ${PLIST_SRC}/hashtable.c
    ${PLIST_SRC}/jplist.c
    ${PLIST_SRC}/jsmn.c
    ${PLIST_SRC}/oplist.c
    ${PLIST_SRC}/out-default.c
    ${PLIST_SRC}/out-limd.c
    ${PLIST_SRC}/out-plutil.c
    ${PLIST_SRC}/plist.c
    ${PLIST_SRC}/ptrarray.c
    ${PLIST_SRC}/time64.c
    ${PLIST_SRC}/xplist.c
    ${PLIST_CNARY}/cnary.c
    ${PLIST_CNARY}/node.c
    ${PLIST_CNARY}/node_list.c
)
target_include_directories(plist
    PUBLIC third_party/libplist/include
    PRIVATE ${PLIST_SRC} ${PLIST_CNARY}/include
)
target_compile_definitions(plist PRIVATE _GNU_SOURCE HAVE_STRNDUP PACKAGE_VERSION="2.6.0")

# ---------- llhttp + playfair (in UxPlay enthalten) ----------
add_library(llhttp STATIC
    ${UXPLAY_LIB}/llhttp/api.c
    ${UXPLAY_LIB}/llhttp/http.c
    ${UXPLAY_LIB}/llhttp/llhttp.c
)
target_include_directories(llhttp PUBLIC ${UXPLAY_LIB}/llhttp)

add_library(playfair STATIC
    ${UXPLAY_LIB}/playfair/hand_garble.c
    ${UXPLAY_LIB}/playfair/modified_md5.c
    ${UXPLAY_LIB}/playfair/omg_hax.c
    ${UXPLAY_LIB}/playfair/playfair.c
    ${UXPLAY_LIB}/playfair/sap_hash.c
)
target_include_directories(playfair PUBLIC ${UXPLAY_LIB}/playfair)

# ---------- UxPlay-Kern + Android-Brücke ----------
add_library(airplay_native SHARED
    ${UXPLAY_LIB}/airplay_video.c
    ${UXPLAY_LIB}/byteutils.c
    ${UXPLAY_LIB}/compat.c
    ${UXPLAY_LIB}/crypto.c
    ${UXPLAY_LIB}/fairplay_playfair.c
    ${UXPLAY_LIB}/http_request.c
    ${UXPLAY_LIB}/http_response.c
    ${UXPLAY_LIB}/httpd.c
    ${UXPLAY_LIB}/logger.c
    ${UXPLAY_LIB}/mirror_buffer.c
    ${UXPLAY_LIB}/netutils.c
    ${UXPLAY_LIB}/pairing.c
    ${UXPLAY_LIB}/raop.c
    ${UXPLAY_LIB}/raop_buffer.c
    ${UXPLAY_LIB}/raop_ntp.c
    ${UXPLAY_LIB}/raop_rtp.c
    ${UXPLAY_LIB}/raop_rtp_mirror.c
    ${UXPLAY_LIB}/srp.c
    ${UXPLAY_LIB}/utils.c
    android_dnssd_shim.c
    android_raop_callbacks.c
    native_bridge.cpp
    audio_engine.cpp
)
target_include_directories(airplay_native PRIVATE
    ${UXPLAY_LIB}
    ${UXPLAY_LIB}/llhttp
    ${UXPLAY_LIB}/playfair
    ${CMAKE_CURRENT_SOURCE_DIR}
)
target_compile_definitions(airplay_native PRIVATE
    ANDROID
    __STDC_CONSTANT_MACROS
    PLIST_210
    TARGET_RT_LITTLE_ENDIAN=1
)
target_link_libraries(airplay_native
    llhttp
    playfair
    crypto
    plist
    oboe
    mediandk
    m
    log
    android
)
