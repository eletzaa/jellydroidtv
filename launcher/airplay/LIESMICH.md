# AirPlay-Empfänger (Modul `airplay`)

Macht Jellydroid zum AirPlay-Ziel für die Bildschirmsynchronisierung vom iPhone, iPad oder Mac
(H.264/H.265 über den Hardware-Decoder des Fernsehers, mit Ton). Als reiner Musik-Lautsprecher
meldet sich der Fernseher bewusst nicht an.

- Läuft im eigenen Prozess `:airplay`, damit ein Fehler im nativen Teil nie den Startbildschirm beendet.
- Ein/Aus, Name und Code: Zahnrad › Konfiguration › AirPlay.
- Ton kommt als AAC (ALAC ist abgeschaltet, dafür bräuchte es FFmpeg). AirPlay-Video-Links
  (HLS) sind nicht angemeldet, iPhone-Apps spiegeln dann den Bildschirm. DRM-Inhalte bleiben schwarz.

## Herkunft und Lizenzen

| Teil | Quelle | Lizenz |
|---|---|---|
| `src/main/cpp/*.c, *.cpp, *.h`, Teile von `src/main/java` | [android-airplay-server](https://github.com/jqssun/android-airplay-server) von jqssun | GPL-3.0 |
| `src/main/cpp/third_party/UxPlay` (inkl. Patches aus android-airplay-server) | [UxPlay](https://github.com/FDH2/UxPlay) | GPL-3.0 |
| `src/main/cpp/third_party/libplist` | [libplist](https://github.com/libimobiledevice/libplist) | LGPL-2.1 |
| OpenSSL 1.1.1 (aus Maven, `com.android.ndk.thirdparty:openssl`) | openssl.org | OpenSSL-Lizenz |
| Oboe (aus Maven, `com.google.oboe:oboe`) | Google | Apache-2.0 |

Wegen der GPL-3.0-Teile muss Jellydroid bei einer Weitergabe (GitHub, Paket) ebenfalls unter
GPL-3.0 stehen und der Quellcode mitgegeben werden.
