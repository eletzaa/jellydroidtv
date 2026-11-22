#!/usr/bin/env bash
# Jellydroid Installer (Linux)
# Geführte Einrichtung: Verbindung per ADB, Sicherung, Bloatware entfernen, Launcher installieren
# und vorkonfigurieren (Streamingdienste, Jellyfin mit Quick Connect, Seerr), Wiederherstellung.
#
# Start:  bash installer-linux.sh          (Menü mit whiptail/dialog, sonst Textmodus)
#         bash installer-linux.sh --text   (immer Textmodus)
# Benötigt: bash, curl; adb wird bei Bedarf von Google geladen (dafür unzip oder python3).
# Am Fernseher wird erst etwas geändert, wenn du in der Zusammenfassung "Jetzt ausführen" bestätigst.

set -u
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DATEN="$ROOT/daten"
SICHER="$ROOT/sicherungen"
EINST="$ROOT/installer-einstellungen.conf"
LOGF="$ROOT/installer-protokoll.txt"
LAUNCHER_PKG=de.jellydroid.launcher
A11Y_DIENST=de.jellydroid.launcher/de.jellydroid.launcher.HomeButtonService
SETUP_PFAD=/sdcard/Android/data/de.jellydroid.launcher/files
ALT_LAUNCHER_PKG=de.elias.launcher
ALT_A11Y_DIENST=de.elias.launcher/de.elias.launcher.HomeButtonService
mkdir -p "$SICHER"

# ------------------------------------------------------------------ Oberfläche

rd() { read -r "$@" || { echo >&2; echo "Eingabe beendet." >&2; exit 1; }; }

UI=plain
if [ "${1:-}" != "--text" ]; then
    if command -v whiptail >/dev/null 2>&1; then UI=whiptail
    elif command -v dialog >/dev/null 2>&1; then UI=dialog; fi
fi
TITEL="Jellydroid Installer"

dims() { H=$(( $(tput lines 2>/dev/null || echo 24) - 4 )); W=$(( $(tput cols 2>/dev/null || echo 80) - 6 ));
    [ "$H" -gt 30 ] && H=30; [ "$W" -gt 100 ] && W=100; [ "$H" -lt 18 ] && H=18; [ "$W" -lt 70 ] && W=70; LH=$(( H - 9 )); }

ui_msg() { # titel text
    if [ "$UI" = plain ]; then printf '\n== %s ==\n%b\n' "$1" "$2"; rd -p "[Enter] " _; return 0; fi
    dims; $UI --title "$1" --backtitle "$TITEL" --msgbox "$2" "$H" "$W"
}
ui_yesno() { # titel text -> 0 = ja
    if [ "$UI" = plain ]; then printf '\n== %s ==\n%b\n' "$1" "$2"; local a; rd -p "Ja/Nein [j/n]: " a; [[ "$a" =~ ^[jJyY] ]]; return; fi
    dims; $UI --title "$1" --backtitle "$TITEL" --yesno "$2" "$H" "$W"
}
ui_input() { # titel text vorgabe -> stdout
    if [ "$UI" = plain ]; then printf '\n== %s ==\n%b\n' "$1" "$2" >&2; local a; rd -p "[$3]: " a; echo "${a:-$3}"; return 0; fi
    dims; $UI --title "$1" --backtitle "$TITEL" --inputbox "$2" "$H" "$W" "$3" 3>&1 1>&2 2>&3
}
ui_password() { # titel text -> stdout
    if [ "$UI" = plain ]; then printf '\n== %s ==\n%b\n' "$1" "$2" >&2; local a; rd -s -p "Passwort: " a; echo >&2; echo "$a"; return 0; fi
    dims; $UI --title "$1" --backtitle "$TITEL" --passwordbox "$2" "$H" "$W" 3>&1 1>&2 2>&3
}
ui_menu() { # titel text tag eintrag ... -> stdout tag
    local t="$1" x="$2"; shift 2
    if [ "$UI" = plain ]; then
        printf '\n== %s ==\n%b\n' "$t" "$x" >&2
        local -a tags=(); local i=1
        while [ $# -gt 0 ]; do tags+=("$1"); printf '  %2d) %s\n' "$i" "$2" >&2; shift 2; i=$((i+1)); done
        local a; rd -p "Auswahl (Enter = zurück): " a
        [[ "$a" =~ ^[0-9]+$ ]] && [ "$a" -ge 1 ] && [ "$a" -le ${#tags[@]} ] || return 1
        echo "${tags[$((a-1))]}"; return 0
    fi
    dims; $UI --title "$t" --backtitle "$TITEL" --menu "$x" "$H" "$W" "$LH" "$@" 3>&1 1>&2 2>&3
}
ui_checklist() { # titel text tag eintrag on|off ... -> stdout tags (eine pro Zeile)
    local t="$1" x="$2"; shift 2
    if [ "$UI" = plain ]; then
        local -a tags=() items=() st=()
        while [ $# -gt 0 ]; do tags+=("$1"); items+=("$2"); st+=("$3"); shift 3; done
        while true; do
            printf '\n== %s ==\n%b\n' "$t" "$x" >&2
            local i
            for i in "${!tags[@]}"; do printf '  %2d) [%s] %s\n' $((i+1)) "$([ "${st[$i]}" = on ] && echo x || echo ' ')" "${items[$i]}" >&2; done
            local a; rd -p "Nummern zum Umschalten (z. B. 1 3 5), a = alle, k = keine, Enter = fertig: " a
            [ -z "$a" ] && break
            if [ "$a" = a ]; then for i in "${!st[@]}"; do st[$i]=on; done; continue; fi
            if [ "$a" = k ]; then for i in "${!st[@]}"; do st[$i]=off; done; continue; fi
            for n in $a; do
                [[ "$n" =~ ^[0-9]+$ ]] && [ "$n" -ge 1 ] && [ "$n" -le ${#tags[@]} ] || continue
                i=$((n-1)); [ "${st[$i]}" = on ] && st[$i]=off || st[$i]=on
            done
        done
        for i in "${!tags[@]}"; do [ "${st[$i]}" = on ] && echo "${tags[$i]}"; done
        return 0
    fi
    dims; $UI --title "$t" --backtitle "$TITEL" --separate-output --checklist "$x" "$H" "$W" "$LH" "$@" 3>&1 1>&2 2>&3
}

# ------------------------------------------------------------------ Hilfsfunktionen

log() { printf '%s\n' "$*"; printf '%s  %s\n' "$(date '+%F %T')" "$*" >> "$LOGF"; }
trim() { local s="$*"; s="${s#"${s%%[![:space:]]*}"}"; echo "${s%"${s##*[![:space:]]}"}"; }
json_esc() { local s="$1"; s=${s//\\/\\\\}; s=${s//\"/\\\"}; s=${s//$'\n'/\\n}; s=${s//$'\r'/}; s=${s//$'\t'/\\t}; printf '"%s"' "$s"; }
json_arr() { local out="" x; for x in "$@"; do [ -n "$out" ] && out+=","; out+=$(json_esc "$x"); done; printf '[%s]' "$out"; }
json_get() { # json pfad (z. B. User.Id)
    if command -v python3 >/dev/null 2>&1; then
        python3 -I -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
    for k in sys.argv[2].split("."):
        d=d.get(k) if isinstance(d,dict) else None
    print("" if d is None else (str(d).lower() if isinstance(d,bool) else d))
except Exception: print("")' "$1" "$2"
    elif command -v jq >/dev/null 2>&1; then jq -r ".$2 // empty" <<<"$1" 2>/dev/null
    else local k=${2##*.}; sed -n "s/.*\"$k\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\).*/\1/p" <<<"$1" | head -n1; fi
}
norm_url() { local u; u=$(trim "$1"); u=${u%/}; [ -n "$u" ] && [[ ! "$u" =~ ^https?:// ]] && u="https://$u"; echo "$u"; }

# Kataloge laden (TSV, Zeilen mit # sind Kommentare)
declare -a K_PKG=() K_NAME=() K_GRP=() K_EMPF=() K_INFO=()
while IFS=$'\t' read -r a b c d e; do
    [ -z "$a" ] || [[ "$a" == \#* ]] && continue
    K_PKG+=("$a"); K_NAME+=("$b"); K_GRP+=("$c"); K_EMPF+=("$d"); K_INFO+=("$e")
done < "$DATEN/apps.tsv"
declare -a D_KEY=() D_NAME=() D_PKGS=() D_DIREKT=()
while IFS=$'\t' read -r a b c d; do
    [ -z "$a" ] || [[ "$a" == \#* ]] && continue
    D_KEY+=("$a"); D_NAME+=("$b"); D_PKGS+=("$c"); D_DIREKT+=("$d")
done < "$DATEN/dienste.tsv"
declare -a J_PKG=() J_NAME=() J_DIREKT=()
while IFS=$'\t' read -r a b c; do
    [ -z "$a" ] || [[ "$a" == \#* ]] && continue
    J_PKG+=("$a"); J_NAME+=("$b"); J_DIREKT+=("$c")
done < "$DATEN/jellyfin-apps.tsv"
# Launcher-Funktionen (für das Update: was ist neu, was muss noch eingerichtet werden)
declare -a F_KEY=() F_AB=() F_NAME=() F_SEITE=()
if [ -f "$DATEN/funktionen.tsv" ]; then
    while IFS=$'\t' read -r a b c d; do
        [ -z "$a" ] || [[ "$a" == \#* ]] && continue
        F_KEY+=("$a"); F_AB+=("$b"); F_NAME+=("$c"); F_SEITE+=("${d:--}")
    done < "$DATEN/funktionen.tsv"
fi
GESCHUETZT=" "; for i in "${!K_PKG[@]}"; do [ "${K_GRP[$i]}" = geschuetzt ] && GESCHUETZT+="${K_PKG[$i]} "; done
ist_geschuetzt() { [[ "$GESCHUETZT" == *" $1 "* ]]; }
SYSTEM_PRAEFIXE="android com.android. com.google.android.gms com.google.android.gsf com.google.android.ext. com.google.android.overlay. com.google.android.modulemetadata com.google.android.permissioncontroller com.google.android.packageinstaller com.mediatek. org.droidtv.ui. org.droidtv.tv. org.droidtv.common."
ORTE=("Deutschland" "Österreich" "Schweiz" "Niederlande" "Belgien" "Luxemburg" "Frankreich" "Italien" "Spanien" "Portugal"
    "Vereinigtes Königreich" "Irland" "Dänemark" "Schweden" "Norwegen" "Finnland" "Polen" "Tschechien" "Ungarn" "Kroatien"
    "Griechenland" "Türkei" "USA" "Kanada")

# Gemerkte Eingaben (ohne Passwörter und Tokens)
M_IP=""; M_PORT=5555; M_JF_URL=""; M_SEERR_URL=""; M_SEERR_USER=""; M_SEERR_ART=jellyfin
M_DIENSTE=""; M_REIHEN=""; M_JF_APP=com.edde746.plezy; M_THEME=DARK; M_ORT=0; M_DNS=dns.adguard-dns.com
M_AIRPLAY=1; M_AIRPLAY_NAME=Jellydroid; M_SCHRIFT=standard; M_SCHRIFT_PFAD=""
M_HA=1; M_HA_POPUP=1; M_HA_TV=""; M_HA_BELL=""; M_HA_DOOR=""
# shellcheck disable=SC1090
[ -f "$EINST" ] && . "$EINST"
save_merk() {
    { for v in M_IP M_PORT M_JF_URL M_SEERR_URL M_SEERR_USER M_SEERR_ART M_DIENSTE M_REIHEN M_JF_APP M_THEME M_ORT M_DNS M_AIRPLAY M_AIRPLAY_NAME M_SCHRIFT M_SCHRIFT_PFAD M_HA M_HA_POPUP M_HA_TV M_HA_BELL M_HA_DOOR; do
        printf '%s=%q\n' "$v" "${!v}"; done; } > "$EINST"
}

# Zustand
GERAET=""; VERBUNDEN=0; MODELL=""; PAKETE=" "; DEAKTIVIERT=" "; LAUNCHER_DA=0; ALT_LAUNCHER_DA=0; SICHER_ORDNER=""
OPT_APK=0; SEL_APPS=""; METHODE=uninstall; OPT_DNS=0; OPT_ANIM=0
OPT_LAUNCHER=0; APK_DATEI=""; OPT_HOME=1; OPT_FAV=1; AVATAR=""
JF_TOKEN=""; JF_USER=""; JF_USERNAME=""; JF_DEVICE=""; JF_SERVER=""
SEERR_COOKIE=""; SEERR_UID=-1; SEERR_NAME=""
HA_URL=""; HA_TOKEN=""
UPD_WAHL=""; KONFIGURIERT=""; SB_KEY=""; SB_NAME=""
R_ORDNER=""; R_APPS=1; R_EINST=1; R_HOME=0; R_DEINST=0; R_APK=0

# ------------------------------------------------------------------ ADB

ADB=""
find_adb() {
    local k
    for k in "$ROOT/platform-tools/adb" "$ROOT/../platform-tools/adb"; do [ -x "$k" ] && { ADB="$k"; return 0; }; done
    command -v adb >/dev/null 2>&1 && { ADB=$(command -v adb); return 0; }
    return 1
}
ensure_adb() {
    [ -n "$ADB" ] && return 0
    find_adb && return 0
    echo "Lade Android Platform-Tools von Google (ca. 7 MB) ..."
    local zip="$ROOT/platform-tools-linux.zip"
    if command -v curl >/dev/null 2>&1; then curl -fL -o "$zip" https://dl.google.com/android/repository/platform-tools-latest-linux.zip || return 1
    else wget -O "$zip" https://dl.google.com/android/repository/platform-tools-latest-linux.zip || return 1; fi
    if command -v unzip >/dev/null 2>&1; then unzip -oq "$zip" -d "$ROOT"
    else python3 -I -m zipfile -e "$zip" "$ROOT"; fi
    rm -f "$zip"; chmod +x "$ROOT/platform-tools/adb" 2>/dev/null
    find_adb
}
adbc() { "$ADB" "$@" 2>&1 | tr -d '\r'; }
tvsh() { "$ADB" -s "$GERAET" shell "$1" 2>&1 | tr -d '\r'; }
liste() { tvsh "pm list packages $1" | sed -n 's/^package://p' | sort; }
hat_paket() { [[ "$PAKETE" == *" $1 "* ]]; }
lade_pakete() { PAKETE=" $(liste '--user 0' | tr '\n' ' ')"; DEAKTIVIERT=" $(liste '-d' | tr '\n' ' ')"; hat_paket "$LAUNCHER_PKG" && LAUNCHER_DA=1 || LAUNCHER_DA=0
    hat_paket "$ALT_LAUNCHER_PKG" && ALT_LAUNCHER_DA=1 || ALT_LAUNCHER_DA=0; }

# ------------------------------------------------------------------ Schritte

step_vorbereitung() {
    local sel
    sel=$(ui_checklist "Fernseher vorbereiten" "Diese Schritte machst du einmal am Fernseher. Hake ab, was erledigt ist." \
        netz "TV und dieser PC sind im selben Heimnetz" off \
        dev "Entwickleroptionen aktiv: Geräteeinstellungen > Info > 7x auf Build" off \
        usb "USB-Debugging an (Entwickleroptionen), ggf. auch Netzwerk-Debugging" off \
        ip "IP des TV bekannt: Einstellungen > Netzwerk > IP-Adresse" off \
        ok "Fernbedienung bereit: am TV 'Immer zulassen' + OK drücken" off) || return 1
    if [ "$(echo "$sel" | grep -c .)" -lt 5 ]; then
        ui_msg "Fernseher vorbereiten" "So geht's (Philips: Einstellungen > Alle Einstellungen > Android-Einstellungen):\n\n1. Geräteeinstellungen > Info > 7-mal auf 'Build' drücken, bis 'Du bist jetzt Entwickler' erscheint.\n2. Geräteeinstellungen > Entwickleroptionen > USB-Debugging einschalten. Gibt es 'Netzwerk-Debugging' oder 'ADB über Netzwerk', auch einschalten.\n3. IP-Adresse: Einstellungen > Netzwerk und Internet > dein Netzwerk.\n4. Beim ersten Verbinden fragt der TV 'USB-Debugging zulassen?': 'Immer von diesem Computer zulassen' anhaken und OK.\n\nBitte alle Punkte erledigen und abhaken."
        step_vorbereitung; return
    fi
}

scan_netz() {
    local port="$1" pre i
    local pres
    pres=$( (ip -4 -o addr show scope global 2>/dev/null | awk '{print $4}' | cut -d/ -f1; hostname -I 2>/dev/null | tr ' ' '\n') \
        | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | grep -v '^127\.' | sed 's/\.[0-9]*$//' | sort -u)
    for pre in $pres; do
        for i in $(seq 1 254); do
            ( timeout 1 bash -c "echo > /dev/tcp/$pre.$i/$port" 2>/dev/null && echo "$pre.$i" ) &
        done
        wait
    done
}

step_verbindung() {
    local ip port treffer wahl a
    while true; do
        a=$(ui_menu "Mit dem Fernseher verbinden" "Aktuell: ${GERAET:-nicht verbunden} ${MODELL:+($MODELL)}" \
            verbinden "IP-Adresse eingeben und verbinden" suchen "Im Netzwerk nach Fernsehern suchen" \
            weiter "Weiter") || return 1
        case "$a" in
            suchen)
                echo "Suche Geräte mit offenem ADB-Port $M_PORT ..."
                treffer=$(scan_netz "$M_PORT")
                if [ -z "$treffer" ]; then ui_msg "Suche" "Nichts gefunden. Ist USB-/Netzwerk-Debugging am Fernseher an? Sonst die IP von Hand eingeben."; continue; fi
                local -a args=(); for ip in $treffer; do args+=("$ip" "ADB-Port offen"); done
                wahl=$(ui_menu "Gefundene Geräte" "Wähle den Fernseher:" "${args[@]}") || continue
                M_IP="$wahl"; verbinde ;;
            verbinden)
                ip=$(ui_input "IP-Adresse" "IP-Adresse des Fernsehers, z. B. 192.168.1.50:" "$M_IP") || continue
                port=$(ui_input "Port" "ADB-Port (meist 5555):" "$M_PORT") || continue
                if [[ ! "$ip" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then ui_msg "IP-Adresse" "Bitte eine gültige IP-Adresse eingeben."; continue; fi
                M_IP="$ip"; M_PORT="$port"; verbinde ;;
            weiter)
                [ "$VERBUNDEN" = 1 ] && return 0
                ui_msg "Verbinden" "Bitte zuerst mit dem Fernseher verbinden." ;;
        esac
    done
}

verbinde() {
    ensure_adb || { ui_msg "ADB" "ADB konnte nicht geladen werden. Bitte 'adb' über die Paketverwaltung installieren (z. B. android-tools-adb)."; return 1; }
    GERAET="$M_IP:$M_PORT"; VERBUNDEN=0
    echo "Verbinde mit $GERAET ..."
    adbc connect "$GERAET" >/dev/null
    local ende=$(( $(date +%s) + 90 )) hinweis=0 zeile
    while [ "$(date +%s)" -lt "$ende" ]; do
        zeile=$(adbc devices | grep -F "$GERAET" | head -n1)
        if [[ "$zeile" =~ [[:space:]]device$ ]]; then VERBUNDEN=1; break; fi
        if [[ "$zeile" == *unauthorized* ]]; then
            [ $hinweis = 0 ] && echo "Bitte am Fernseher 'Immer von diesem Computer zulassen' anhaken und OK drücken ..."; hinweis=1
        elif [ -z "$zeile" ] || [[ "$zeile" == *offline* ]]; then adbc connect "$GERAET" >/dev/null; fi
        sleep 1.5
    done
    if [ "$VERBUNDEN" != 1 ]; then ui_msg "Verbinden" "Keine Verbindung zu $GERAET. Prüfe IP, USB-Debugging und ob du am Fernseher OK gedrückt hast."; return 1; fi
    MODELL="$(tvsh 'getprop ro.product.manufacturer') $(tvsh 'getprop ro.product.model')"
    local android; android=$(tvsh 'getprop ro.build.version.release')
    lade_pakete
    save_merk
    ui_msg "Verbunden" "$MODELL, Android $android\n$(echo "$PAKETE" | wc -w) Apps installiert$([ $LAUNCHER_DA = 1 ] && echo ', eigener Launcher ist installiert')$([ $LAUNCHER_DA = 0 ] && [ $ALT_LAUNCHER_DA = 1 ] && echo ', ältere Jellydroid-Version ist installiert (wird beim Einrichten ersetzt)')."
}

step_sicherung() {
    if ui_yesno "Sicherung" "Gesichert wird: App-Listen, Privates DNS, Animationen, Bedienungshilfen, Startbildschirm.\nOrt: $SICHER\n\nPlay-Store-Apps zusätzlich als APK sichern?"; then OPT_APK=1; else OPT_APK=0; fi
}

step_bloatware() {
    local modus="$1" vor=" " i p
    if [ "$modus" = erneut ] && [ -f "$SICHER/letzte-auswahl.txt" ]; then
        vor=" $(grep -v '^#' "$SICHER/letzte-auswahl.txt" | cut -d'|' -f1 | tr '\n' ' ')"
        grep -q '|disable' "$SICHER/letzte-auswahl.txt" && METHODE=disable
    else
        for i in "${!K_PKG[@]}"; do [ "${K_EMPF[$i]}" = ja ] && vor+="${K_PKG[$i]} "; done
    fi
    ui_yesno "Auf eigene Gefahr" "Das Entfernen vorinstallierter Apps kann Funktionen des Fernsehers beeinträchtigen. Über 'Wiederherstellen' kommt alles zurück.\n\nAuf eigene Gefahr fortfahren?" || { SEL_APPS=""; return 0; }
    local -a args=()
    local grp
    for grp in werbung philips google freeview; do
        for i in "${!K_PKG[@]}"; do
            [ "${K_GRP[$i]}" = "$grp" ] || continue
            hat_paket "${K_PKG[$i]}" || continue
            args+=("${K_PKG[$i]}" "[$grp] ${K_NAME[$i]}: ${K_INFO[$i]}" "$([[ "$vor" == *" ${K_PKG[$i]} "* ]] && echo on || echo off)")
        done
    done
    local experte=0
    for p in $vor; do [[ " ${K_PKG[*]} " == *" $p "* ]] || { hat_paket "$p" && experte=1; }; done
    if [ $experte = 1 ] || ui_yesno "Experten" "Weitere Pakete anzeigen?"; then
        for p in $PAKETE; do
            [[ " ${K_PKG[*]} " == *" $p "* ]] && continue
            local sys=0 pre
            for pre in $SYSTEM_PRAEFIXE; do [[ "$p" == "$pre" || "$p" == "$pre"* ]] && { sys=1; break; }; done
            [ $sys = 1 ] && continue
            args+=("$p" "[unbekannt] nur wenn du sicher bist" "$([[ "$vor" == *" $p "* ]] && echo on || echo off)")
        done
    fi
    if [ ${#args[@]} -eq 0 ]; then ui_msg "Apps" "Keine der bekannten Apps ist installiert."; SEL_APPS=""; return 0; fi
    SEL_APPS=$(ui_checklist "Vorinstallierte Apps entfernen" "Auswahl:" "${args[@]}") || return 1
    local m
    m=$(ui_menu "Methode" "Wie sollen die Apps entfernt werden?" uninstall "Für meinen Benutzer entfernen (empfohlen)" disable "Nur deaktivieren") || return 1
    METHODE="$m"
    local def; [ "$modus" = voll ] && def=on || def=off
    local ex
    ex=$(ui_checklist "Werbefilter und Tempo" "Zusätzliche Einstellungen:" \
        dns "Privates DNS als Werbefilter setzen ($M_DNS)" "$def" \
        anim "Animationen verkürzen (halbe Dauer)" "$def") || return 1
    OPT_DNS=0; OPT_ANIM=0
    [[ "$ex" == *dns* ]] && OPT_DNS=1; [[ "$ex" == *anim* ]] && OPT_ANIM=1
    if [ $OPT_DNS = 1 ]; then
        local d; d=$(ui_input "Privates DNS" "Hostname des DNS-Anbieters:" "$M_DNS") || return 1
        [[ "$d" =~ ^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] && M_DNS="$d" || { ui_msg "Privates DNS" "Ungültiger Hostname, Werbefilter wird übersprungen."; OPT_DNS=0; }
    fi
    save_merk
}

find_apk() {
    # Jellydroid-V<Version>.apk neben dem Installer oder in ../launcher/ausgabe; die höchste Version gewinnt.
    # Ohne Versionsdatei: launcher.apk neben dem Installer.
    local best
    best=$(for f in "$ROOT"/Jellydroid-V*.apk "$ROOT"/../launcher/ausgabe/Jellydroid-V*.apk; do
        [ -f "$f" ] || continue; v=$(basename "$f" .apk); v=${v#Jellydroid-V}
        [[ "$v" =~ ^[0-9]+(\.[0-9]+)*$ ]] && printf '%s\t%s\n' "$v" "$f"
    done | sort -t$'\t' -k1,1V | tail -n1 | cut -f2)
    [ -z "$best" ] && [ -f "$ROOT/launcher.apk" ] && best="$ROOT/launcher.apk"
    [ -n "$best" ] && (cd "$(dirname "$best")" && echo "$(pwd)/$(basename "$best")")
}

step_launcher() {
    local modus="$1" d
    [ -z "$APK_DATEI" ] && APK_DATEI=$(find_apk)
    local frage="Den eigenen Launcher installieren oder aktualisieren?"
    [ $LAUNCHER_DA = 1 ] && frage="Der Launcher ist schon installiert. Mit einer neuen APK aktualisieren?"
    if { [ "$modus" = voll ] || [ $LAUNCHER_DA = 0 ]; } || ui_yesno "Launcher" "$frage"; then
        if [ "$modus" = voll ] || [ $LAUNCHER_DA = 0 ]; then ui_yesno "Launcher" "$frage" && OPT_LAUNCHER=1 || OPT_LAUNCHER=0; else OPT_LAUNCHER=1; fi
    else OPT_LAUNCHER=0; fi
    if [ $OPT_LAUNCHER = 1 ]; then
        while true; do
            d=$(ui_input "Launcher-APK" "Pfad zur APK-Datei des Launchers:" "$APK_DATEI") || return 1
            [ -f "$d" ] && { APK_DATEI="$d"; break; }
            ui_msg "Launcher-APK" "Datei nicht gefunden: $d"
        done
        ui_yesno "Home-Taste" "Home-Taste auf den Launcher legen?" && OPT_HOME=1 || OPT_HOME=0
    elif [ $LAUNCHER_DA = 0 ]; then
        ui_yesno "Launcher" "Der Launcher ist nicht installiert. Die folgenden Einstellungen werden dann nicht übertragen. Trotzdem weiter?" || return 1
    fi
    local bild
    while true; do
        bild=$(ui_input "Profilbild" "Bilddatei für das Profilbild oben rechts (jpg, png, webp; leer = nicht ändern):" "$AVATAR") || return 1
        bild=$(trim "$bild"); bild=${bild/#\~/$HOME}
        if [ -z "$bild" ]; then AVATAR=""; break; fi
        if [ -f "$bild" ] && [[ "${bild,,}" =~ \.(jpg|jpeg|png|webp)$ ]]; then AVATAR="$bild"; break; fi
        ui_msg "Profilbild" "Keine jpg-, png- oder webp-Datei: $bild"
    done
    local t
    t=$(ui_menu "Design" "Design des Launchers:" DARK "Dunkel" LIGHT "Hell" AUTO "Dynamisch (nach Sonnenstand)") || return 1
    M_THEME="$t"
    if [ "$t" = AUTO ]; then
        local -a args=(); local i
        for i in "${!ORTE[@]}"; do args+=("$i" "${ORTE[$i]}"); done
        M_ORT=$(ui_menu "Ort" "Für den Sonnenstand:" "${args[@]}") || M_ORT=0
    fi
    local -a ja=(); local i
    for i in "${!J_PKG[@]}"; do
        local inst; hat_paket "${J_PKG[$i]}" && inst=installiert || inst="nicht installiert"
        local dir; [ "${J_DIREKT[$i]}" = ja ] && dir="öffnet Titel direkt, empfohlen" || dir="startet nur die App"
        ja+=("${J_PKG[$i]}" "${J_NAME[$i]} ($dir, $inst)")
    done
    ui_msg "Tipp: Plezy" "Nimm Plezy als Jellyfin-App. Nur Plezy öffnet einen Film oder eine Serie direkt beim Titel, wenn du ihn im Launcher auswählst. Jellyfin, Reefin, Findroid und Wholphin starten nur die App. Plezy hat außerdem Seerr eingebaut."
    M_JF_APP=$(ui_menu "Jellyfin-App" "Mit welcher App sollen Titel aus Jellyfin geöffnet werden?" "${ja[@]}") || return 1
    if ! hat_paket com.edde746.plezy && [ "$M_JF_APP" = com.edde746.plezy ]; then
        ui_yesno "Plezy" "Plezy ist noch nicht installiert. Jetzt am Fernseher im Play Store öffnen?" && \
            tvsh 'am start -a android.intent.action.VIEW -d market://details?id=com.edde746.plezy' >/dev/null
    fi
    save_merk
}

step_airplay() {
    if ui_yesno "AirPlay" "Bildschirmsynchronisierung vom iPhone, iPad oder Mac aktivieren?"; then
        M_AIRPLAY=1
        local n; n=$(ui_input "AirPlay" "Name auf dem iPhone:" "${M_AIRPLAY_NAME:-Jellydroid}") || n=""
        n=$(trim "$n"); M_AIRPLAY_NAME="${n:-Jellydroid}"
    else M_AIRPLAY=0; fi
    save_merk
}

# Schrift: Standard ist Inter (steckt im Launcher). Eigene Schriften (z. B. Aptos oder SF Pro) lädt man selbst
# herunter; angegeben wird eine Datei oder ein Ordner mit den TTF/OTF-Dateien einer Familie.
schrift_dateien() { # -> eine Datei pro Zeile
    local p="$M_SCHRIFT_PFAD"
    if [ -d "$p" ]; then find "$p" -maxdepth 1 -type f \( -iname '*.ttf' -o -iname '*.otf' \) | sort | head -n 24
    elif [ -f "$p" ]; then printf '%s\n' "$p"; fi
}
step_schrift() {
    local w
    w=$(ui_menu "Schrift" "Schrift des Launchers:" standard "Inter (Standard)" eigene "Eigene Schrift (TTF/OTF-Datei oder Ordner)") || return 1
    if [ "$w" = eigene ]; then
        local p
        while :; do
            p=$(ui_input "Schrift" "Pfad zur Schriftdatei oder zum Ordner mit den TTF/OTF-Dateien:" "$M_SCHRIFT_PFAD") || return 1
            p=$(trim "$p"); p="${p/#\~/$HOME}"; M_SCHRIFT_PFAD="$p"
            [ -n "$(schrift_dateien)" ] && break
            ui_msg "Schrift" "Dort wurde keine TTF- oder OTF-Datei gefunden."
        done
        M_SCHRIFT=eigene
    else M_SCHRIFT=standard; fi
    save_merk
}
# Schiebt die eigenen Schriftdateien nach files/schrift-neu/ (vor send_setup, der Launcher übernimmt sie dann)
push_schrift() {
    [ "$M_SCHRIFT" = eigene ] || return 0
    tvsh "rm -rf $SETUP_PFAD/schrift-neu; mkdir -p $SETUP_PFAD/schrift-neu" >/dev/null
    local f n
    while IFS= read -r f; do
        n=$(basename "$f" | tr -c 'A-Za-z0-9._\n-' '_')
        log "Schrift übertragen: $(adbc -s "$GERAET" push "$f" "$SETUP_PFAD/schrift-neu/$n")"
    done < <(schrift_dateien)
}
schrift_text() {
    if [ "$M_SCHRIFT" = eigene ]; then echo "eigene ($(schrift_dateien | xargs -d '\n' -n1 basename 2>/dev/null | paste -sd, - | sed 's/,/, /g'))"; else echo "Inter (Standard)"; fi
}

# Der Launcher protokolliert jede Taste ("Taste code=<n> (KEYCODE_…) action=<0|1>", Tag JellydroidHome).
# Der Installer liest das mit, während die Taste gedrückt wird, und schickt den Code als config.sidebar_key.
step_sidebar() {
    if [ $LAUNCHER_DA = 0 ]; then
        ui_msg "Sidebar-Taste" "Anlernen geht, sobald der Launcher installiert ist. Bis dahin öffnen Menü- und Einstellungstaste die Sidebar."; return 0
    fi
    local a log c n ende
    while true; do
        a=$(ui_menu "Sidebar-Taste" "Taste: $( [ -z "$SB_KEY" ] && echo unverändert || { [ "$SB_KEY" -le 0 ] && echo 'Standard (Menü/Einstellungen)' || echo "$SB_NAME (Code $SB_KEY)"; } )" \
            lernen "Taste anlernen" standard "Standard (Menü- oder Einstellungstaste)" weiter "Weiter") || return 1
        case "$a" in
            standard) SB_KEY=-1; SB_NAME="" ;;
            weiter) return 0 ;;
            lernen)
                tvsh 'logcat -c' >/dev/null
                ui_msg "Sidebar-Taste" "Nach OK hast du 20 Sekunden, um die gewünschte Taste auf der Fernbedienung zu drücken."
                ende=$(( $(date +%s) + 20 )); c=""
                while [ "$(date +%s)" -lt "$ende" ] && [ -z "$c" ]; do
                    sleep 0.7
                    log=$(tvsh 'logcat -d -s JellydroidHome:I')
                    c=$(grep -oE 'Taste code=[0-9]+ \([A-Z0-9_]+\) action=1' <<<"$log" | sed -E 's/Taste code=([0-9]+) \(([A-Z0-9_]+)\).*/\1 \2/' \
                        | awk '$1!=3 && $1!=4 && ($1<19 || $1>23) && $1!=66 && $1!=160' | tail -n1)
                done
                if [ -n "$c" ]; then SB_KEY=${c%% *}; n=${c#* }; SB_NAME=${n#KEYCODE_}; ui_msg "Sidebar-Taste" "Angelernt: $SB_NAME"
                else ui_msg "Sidebar-Taste" "Keine Taste erkannt. Läuft die Bedienungshilfe des Launchers? Pfeiltasten, OK, Zurück und Home gehen nicht."; fi ;;
        esac
    done
}

# ------------------------------------------------------------------ Update

# Welche Funktionen am Fernseher schon eingerichtet sind, steht in installer-stand.txt im Launcher-Ordner
# am TV (überlebt Updates, gilt für jeden PC). Welche Funktionen es gibt und ab welcher Version, steht in
# daten/funktionen.tsv. Neu = in der neuen APK enthalten, aber am TV noch nicht eingerichtet.
ver_le() { [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -n1)" = "$1" ]; }   # $1 <= $2
apk_ver() { local n; n=$(basename "$1"); [[ "$n" =~ ^Jellydroid-V([0-9]+(\.[0-9]+)*)\.apk$ ]] && echo "${BASH_REMATCH[1]}"; }
inst_ver() { tvsh "dumpsys package $LAUNCHER_PKG" | sed -n 's/.*versionName=\([0-9][0-9.]*\).*/\1/p' | head -n1; }
lies_stand() { # Ausgabe: Schlüssel; Rückgabe 1 = keine Stand-Datei
    local t; t=$(tvsh "cat $SETUP_PFAD/installer-stand.txt")
    [[ "$t" == *funktionen=* ]] || return 1
    sed -n 's/^funktionen=//p' <<<"$t" | head -n1
}
schreib_stand() { # schlüssel ...
    local f; f=$(printf '%s\n' "$@" | sed '/^$/d' | awk '!s[$0]++' | tr '\n' ' '); f=$(trim "$f")
    tvsh "mkdir -p $SETUP_PFAD; echo 'funktionen=$f' > $SETUP_PFAD/installer-stand.txt" >/dev/null
    log "  Eingerichtete Funktionen gespeichert: ${f:-keine}"
}
funktionen_bis() { # version (leer = alle) -> Indizes
    local i; for i in "${!F_KEY[@]}"; do { [ -z "$1" ] || ver_le "${F_AB[$i]}" "$1"; } && echo "$i"; done
}
konfiguriert() {
    local st iv i
    if st=$(lies_stand); then echo "$st"; return; fi
    # Ältere Einrichtung ohne Stand-Datei: was die installierte Version schon konnte, gilt als eingerichtet
    iv=$(inst_ver); [ -z "$iv" ] && return
    for i in $(funktionen_bis "$iv"); do printf '%s ' "${F_KEY[$i]}"; done
}
hat_seite() { [ -n "$1" ] && [ "$1" != - ] && declare -F "step_$1" >/dev/null; }

step_update() {
    if [ $LAUNCHER_DA = 0 ] && [ $ALT_LAUNCHER_DA = 1 ]; then
        ui_msg "Update" "Ab V1.10 hat Jellydroid eine neue App-Kennung und lässt sich nicht über die alte Version aktualisieren. Bitte \"Launcher installieren oder neu einrichten\" wählen, die alte Version wird dabei ersetzt."; return 1
    fi
    if [ $LAUNCHER_DA = 0 ]; then
        ui_msg "Update" "Der Launcher ist noch nicht installiert. Bitte \"Geführte Einrichtung\" oder \"Launcher installieren oder neu einrichten\" wählen."; return 1
    fi
    local d iv av
    [ -z "$APK_DATEI" ] && APK_DATEI=$(find_apk)
    iv=$(inst_ver)
    while true; do
        d=$(ui_input "Launcher aktualisieren" "Installiert: Jellydroid ${iv:-(Version unbekannt)}\n\nNeue APK:" "$APK_DATEI") || return 1
        d=$(trim "$d"); d=${d/#\~/$HOME}
        [ -f "$d" ] && { APK_DATEI="$d"; break; }
        ui_msg "Launcher aktualisieren" "Datei nicht gefunden: $d"
    done
    av=$(apk_ver "$APK_DATEI")
    if [ -n "$av" ] && [ -n "$iv" ]; then
        if [ "$av" = "$iv" ]; then ui_yesno "Update" "Version $av ist schon installiert. Trotzdem neu installieren?" || return 1
        elif ver_le "$av" "$iv"; then ui_msg "Update" "Die APK ($av) ist älter als die installierte Version ($iv). Zum Zurückgehen erst über \"Wiederherstellen\" deinstallieren."; return 1; fi
    fi
    KONFIGURIERT=$(konfiguriert)
    local -a args=(); local i neu an namen_ohne=""
    for i in $(funktionen_bis "$av"); do
        [[ " $KONFIGURIERT " == *" ${F_KEY[$i]} "* ]] && neu=0 || neu=1
        if hat_seite "${F_SEITE[$i]}"; then
            [ $neu = 1 ] && an=on || an=off
            args+=("${F_KEY[$i]}" "${F_NAME[$i]}$([ $neu = 1 ] && echo '   neu' || echo '   (schon eingerichtet)')" "$an")
        elif [ $neu = 1 ]; then namen_ohne+="${namen_ohne:+, }${F_NAME[$i]}"; fi
    done
    UPD_WAHL=""
    if [ ${#args[@]} -gt 0 ]; then
        local sel; sel=$(ui_checklist "Neue Funktionen" "Jetzt einrichten:$([ -n "$namen_ohne" ] && echo "\n(Ebenfalls neu: $namen_ohne)")" "${args[@]}") || return 1
        UPD_WAHL=$(echo $sel)
    elif [ -n "$namen_ohne" ]; then ui_msg "Neue Funktionen" "Neu: $namen_ohne"; fi
    local k
    for k in $UPD_WAHL; do
        for i in "${!F_KEY[@]}"; do [ "${F_KEY[$i]}" = "$k" ] && { "step_${F_SEITE[$i]}" || return 1; }; done
    done
    return 0
}

dienst_paket() { local p; for p in ${D_PKGS[$1]//,/ }; do hat_paket "$p" && { echo "$p"; return; }; done; }

step_dienste() {
    local -a args=(); local i an
    for i in "${!D_KEY[@]}"; do
        local inst; [ -n "$(dienst_paket "$i")" ] && inst=installiert || inst=fehlt
        local dir; [ "${D_DIREKT[$i]}" = ja ] && dir="öffnet Titel direkt" || dir="nur App-Start"
        if [ -n "$M_DIENSTE" ]; then [[ " $M_DIENSTE " == *" ${D_KEY[$i]} "* ]] && an=on || an=off
        else [ "$inst" = installiert ] && an=on || an=off; fi
        args+=("${D_KEY[$i]}" "${D_NAME[$i]} ($inst, $dir)" "$an")
    done
    local sel; sel=$(ui_checklist "Streamingdienste" "Welche Dienste nutzt du?" "${args[@]}") || return 1
    M_DIENSTE=$(echo $sel)
    args=()
    for i in "${!D_KEY[@]}"; do
        [[ " $M_DIENSTE " == *" ${D_KEY[$i]} "* ]] || continue
        if [ -n "$M_REIHEN" ]; then [[ " $M_REIHEN " == *" ${D_KEY[$i]} "* ]] && an=on || an=off
        else [ "${D_KEY[$i]}" = netflix ] && an=on || an=off; fi
        args+=("${D_KEY[$i]}" "Reihe 'Neu bei ${D_NAME[$i]}'" "$an")
    done
    if [ ${#args[@]} -gt 0 ]; then
        sel=$(ui_checklist "Reihen im Entdecken-Tab" "Reihe 'Neu bei ...' für:" "${args[@]}") || return 1
        M_REIHEN=$(echo $sel)
    else M_REIHEN=""; fi
    local fehlt=()
    for i in "${!D_KEY[@]}"; do [[ " $M_DIENSTE " == *" ${D_KEY[$i]} "* ]] && [ -z "$(dienst_paket "$i")" ] && fehlt+=("$i"); done
    for i in "${fehlt[@]}"; do
        ui_yesno "App fehlt" "${D_NAME[$i]} ist am Fernseher nicht installiert. Jetzt im Play Store am Fernseher öffnen?" && \
            tvsh "am start -a android.intent.action.VIEW -d market://details?id=${D_PKGS[$i]%%,*}" >/dev/null
    done
    ui_yesno "Favoriten" "Favoritenleiste aus Jellyfin-App und gewählten Diensten zusammenstellen?" && OPT_FAV=1 || OPT_FAV=0
    save_merk
}

jf_header() {
    [ -z "$JF_DEVICE" ] && JF_DEVICE="jellydroid-$(head -c 8 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    local t=""; [ -n "$JF_TOKEN" ] && t=", Token=\"$JF_TOKEN\""
    echo "MediaBrowser Client=\"Jellydroid\", Device=\"${MODELL:-TV}\", DeviceId=\"$JF_DEVICE\", Version=\"0.2\"$t"
}

step_konten() {
    local u r
    u=$(ui_input "Jellyfin" "Jellyfin-Adresse (leer = überspringen):" "") || return 1
    M_JF_URL=$(norm_url "$u")
    if [ -n "$M_JF_URL" ]; then
        r=$(curl -fsS --max-time 10 "$M_JF_URL/System/Info/Public" 2>&1)
        if [ -z "$(json_get "$r" ServerName)" ]; then
            ui_msg "Jellyfin" "Server nicht erreichbar:\n$r"
        else
            JF_SERVER=$(json_get "$r" Id)
            local qc; qc=$(curl -fsS --max-time 10 "$M_JF_URL/QuickConnect/Enabled" 2>/dev/null)
            if [ "$qc" = true ] && ui_yesno "Jellyfin" "$(json_get "$r" ServerName) (Jellyfin $(json_get "$r" Version))\n\nJetzt per Quick Connect anmelden?"; then
                jf_quickconnect
            elif [ "$qc" != true ]; then
                ui_msg "Jellyfin" "Quick Connect ist auf dem Server aus."
            fi
        fi
    fi
    u=$(ui_input "Seerr" "Seerr-Adresse (leer = überspringen):" "") || return 1
    M_SEERR_URL=$(norm_url "$u")
    if [ -n "$M_SEERR_URL" ] && ui_yesno "Seerr" "Jetzt bei Seerr anmelden?"; then
        seerr_login
    fi
    save_merk
}

jf_quickconnect() {
    local r code secret ende
    JF_TOKEN=""
    r=$(curl -fsS --max-time 10 -X POST -H "Authorization: $(jf_header)" -H "X-Emby-Authorization: $(jf_header)" -H 'Content-Length: 0' "$M_JF_URL/QuickConnect/Initiate" 2>&1)
    code=$(json_get "$r" Code); secret=$(json_get "$r" Secret)
    if [ -z "$code" ]; then ui_msg "Quick Connect" "Start fehlgeschlagen:\n$r"; return; fi
    clear 2>/dev/null
    printf '\n  Quick Connect\n\n  Öffne Jellyfin am Handy oder PC: Profil > Quick Connect\n  und gib diesen Code ein:\n\n          %s\n\n  Warte auf Bestätigung (Strg+C bricht den Installer ab) ...\n' "$code"
    ende=$(( $(date +%s) + 300 ))
    while [ "$(date +%s)" -lt "$ende" ]; do
        sleep 3
        r=$(curl -fsS --max-time 10 -H "Authorization: $(jf_header)" -H "X-Emby-Authorization: $(jf_header)" "$M_JF_URL/QuickConnect/Connect?secret=$secret" 2>/dev/null)
        if [ "$(json_get "$r" Authenticated)" = true ]; then
            r=$(curl -fsS --max-time 10 -X POST -H "Authorization: $(jf_header)" -H "X-Emby-Authorization: $(jf_header)" -H 'Content-Type: application/json' \
                -d "{\"Secret\":$(json_esc "$secret")}" "$M_JF_URL/Users/AuthenticateWithQuickConnect" 2>&1)
            JF_TOKEN=$(json_get "$r" AccessToken); JF_USER=$(json_get "$r" User.Id); JF_USERNAME=$(json_get "$r" User.Name)
            if [ -n "$JF_TOKEN" ]; then ui_msg "Quick Connect" "Angemeldet als $JF_USERNAME"; return; fi
            ui_msg "Quick Connect" "Anmeldung fehlgeschlagen:\n$r"; return
        fi
    done
    ui_msg "Quick Connect" "Zeit abgelaufen. Du kannst dich später am Fernseher anmelden."
}

seerr_login() {
    local art user pw pfad body jar r
    art=$(ui_menu "Seerr-Anmeldung" "Womit meldest du dich bei Seerr an?" jellyfin "Mit Jellyfin-Konto" lokal "Mit Seerr-Konto (E-Mail)") || return
    M_SEERR_ART="$art"
    user=$(ui_input "Seerr-Anmeldung" "$([ "$art" = lokal ] && echo E-Mail || echo Benutzername):" "$M_SEERR_USER") || return
    M_SEERR_USER="$user"
    pw=$(ui_password "Seerr-Anmeldung" "Passwort:") || return
    if [ "$art" = lokal ]; then pfad=/api/v1/auth/local; body="{\"email\":$(json_esc "$user"),\"password\":$(json_esc "$pw")}"
    else pfad=/api/v1/auth/jellyfin; body="{\"username\":$(json_esc "$user"),\"password\":$(json_esc "$pw")}"; fi
    jar=$(mktemp); chmod 600 "$jar"
    r=$(curl -sS --max-time 15 -c "$jar" -H 'Content-Type: application/json' --data-binary @- -w '\n%{http_code}' "$M_SEERR_URL$pfad" <<<"$body" 2>&1)
    pw=""; body=""
    local status=${r##*$'\n'} inhalt=${r%$'\n'*}
    local sid; sid=$(awk '$6=="connect.sid"{print $7}' "$jar" | tail -n1); rm -f "$jar"
    if [ "$status" = 200 ] && [ -n "$sid" ]; then
        SEERR_COOKIE="connect.sid=$sid"; SEERR_UID=$(json_get "$inhalt" id)
        SEERR_NAME=$(json_get "$inhalt" displayName); [ -z "$SEERR_NAME" ] && SEERR_NAME="$user"
        [[ "$SEERR_UID" =~ ^[0-9]+$ ]] || SEERR_UID=-1
        ui_msg "Seerr" "Angemeldet als $SEERR_NAME."
    elif [ "$status" = 401 ] || [ "$status" = 403 ]; then
        SEERR_COOKIE=""; ui_msg "Seerr" "Benutzer oder Passwort falsch."
    else
        SEERR_COOKIE=""; ui_msg "Seerr" "Anmeldung fehlgeschlagen (HTTP $status)."
    fi
}

# Home Assistant: unter Linux mit langlebigem Token (HA › Profil › Sicherheit). Die Adresse wird nicht gemerkt.
ha_ids() { # json präfix -> Zeilen "entity_id<TAB>Name"
    if command -v python3 >/dev/null 2>&1; then
        python3 -I -c 'import sys,json
for e in sorted(json.loads(sys.argv[1]), key=lambda e: e.get("entity_id","")):
    i=e.get("entity_id","")
    if any(i.startswith(p) for p in sys.argv[2].split(",")):
        print(i+"\t"+(e.get("attributes",{}).get("friendly_name") or i))' "$1" "$2" 2>/dev/null
    else
        local p; for p in ${2//,/ }; do grep -o "\"entity_id\": *\"${p//./\\.}[^\"]*\"" <<<"$1" | sed 's/.*"\([^"]*\)"$/\1/' | while read -r i; do printf '%s\t%s\n' "$i" "$i"; done; done
    fi
}
ha_waehle() { # titel text json präfix "keine"-text vorauswahl-muster -> stdout entity_id
    local -a args=(); local id name
    [ -n "$5" ] && args+=("-" "$5")
    while IFS=$'\t' read -r id name; do [ -n "$id" ] && args+=("$id" "$name"); done < <(ha_ids "$3" "$4")
    [ ${#args[@]} -eq 0 ] && return 0
    local r; r=$(ui_menu "$1" "$2" "${args[@]}") || r="-"
    [ "$r" = "-" ] && r=""
    echo "$r"
}
# Home Assistant suchen: alle Geräte mit offenem Port 8123, geprüft über /manifest.json (ohne Anmeldung lesbar)
ha_suchen() {
    local ips ip
    echo "Suche Home Assistant im Netzwerk ..." >&2
    ips=$(scan_netz 8123)
    if [ -z "$ips" ]; then   # Rückfall: Standardname des Home-Assistant-Betriebssystems (mDNS)
        ips=$(getent ahostsv4 homeassistant.local 2>/dev/null | awk '{print $1}' | sort -u)
    fi
    for ip in $ips; do
        if curl -fsS --max-time 4 "http://$ip:8123/manifest.json" 2>/dev/null | grep -q 'Home Assistant'; then echo "http://$ip:8123"
        elif curl -fsS --max-time 4 "https://$ip:8123/manifest.json" 2>/dev/null | grep -q 'Home Assistant'; then echo "https://$ip:8123"; fi
    done
}
step_homeassistant() {
    HA_URL=""; HA_TOKEN=""
    if ! ui_yesno "Home Assistant" "Home Assistant in der Sidebar einrichten?\n\n(Geht auch später am Fernseher per QR-Code.)"; then M_HA=0; save_merk; return 0; fi
    M_HA=1
    local u t r
    u=""
    if ui_yesno "Home Assistant" "Home Assistant im Netzwerk suchen?"; then
        local -a args=(); local g
        for g in $(ha_suchen); do args+=("$g" "Home Assistant"); done
        if [ ${#args[@]} -gt 0 ]; then
            args+=(manuell "Andere Adresse eingeben")
            u=$(ui_menu "Home Assistant" "Gefunden:" "${args[@]}") || u=manuell
            [ "$u" = manuell ] && u=""
        else ui_msg "Home Assistant" "Nichts gefunden. Bitte die Adresse von Hand eingeben."; fi
    fi
    [ -z "$u" ] && { u=$(ui_input "Home Assistant" "Adresse von Home Assistant (leer = später am Fernseher):" "") || return 1; }
    u=$(trim "$u"); u=${u%/}; [ -n "$u" ] && [[ ! "$u" =~ ^https?:// ]] && u="http://$u"
    if [ -n "$u" ]; then
        t=$(ui_password "Home Assistant" "Langlebiger Token (Home Assistant › Profil › Sicherheit › Token erstellen):") || t=""
        t=$(trim "$t")
        if [ -n "$t" ]; then
            r=$(curl -fsS --max-time 15 -H "Authorization: Bearer $t" "$u/api/states" 2>&1)
            if [[ "$r" != \[* ]]; then
                ui_msg "Home Assistant" "Anmeldung fehlgeschlagen:\n$r"
            else
                HA_URL=$u; HA_TOKEN=$t
                local w
                w=$(ha_waehle "Home Assistant" "Welcher Eintrag ist dieser Fernseher? (Sein Bereich bestimmt den Raum.)" "$r" "media_player." "" "") && M_HA_TV=$w
                if ui_yesno "Türklingel" "Hinweis am Fernseher, wenn es klingelt?"; then
                    M_HA_POPUP=1
                    w=$(ha_waehle "Türklingel" "Klingel (Ereignis):" "$r" "event." "keine" "") && M_HA_BELL=$w
                    w=$(ha_waehle "Türklingel" "Türöffner:" "$r" "button.,lock." "keiner" "") && M_HA_DOOR=$w
                else M_HA_POPUP=0; fi
            fi
        fi
    fi
    save_merk
}

step_tipps() {
    local text="" titel=1 line
    while IFS= read -r line || [ -n "$line" ]; do
        if [ -z "$(trim "$line")" ]; then text+="\n"; titel=1; continue; fi
        if [ $titel = 1 ]; then text+="» $line\n"; titel=0; else text+="$line\n"; fi
    done < "$DATEN/tipps.txt"
    if [ "$UI" = plain ]; then printf '\n== Tipps ==\n%b\n' "$text"; rd -p "[Enter] " _
    else dims; $UI --title "Tipps" --backtitle "$TITEL" --scrolltext --msgbox "$text" "$H" "$W" 2>/dev/null || $UI --title "Tipps" --msgbox "$text" "$H" "$W"; fi
}

sicher_liste() { local d; for d in "$SICHER"/*/; do [ -f "$d/pakete-alle.txt" ] && basename "$d"; done | sort -r; }

step_restore() {
    local -a args=(); local d
    for d in $(sicher_liste); do args+=("$d" "$(head -n2 "$SICHER/$d/geraet.txt" 2>/dev/null | tr '\n' ' ')"); done
    if [ ${#args[@]} -eq 0 ]; then
        ui_msg "Wiederherstellen" "Keine Sicherung gefunden in $SICHER."; R_ORDNER=""
    else
        R_ORDNER=$(ui_menu "Wiederherstellen" "Welche Sicherung?" "${args[@]}") || return 1
        local alt fehlen aus neu
        alt=" $(tr '\n' ' ' < "$SICHER/$R_ORDNER/pakete-alle.txt") "
        fehlen=0; for d in $alt; do hat_paket "$d" || fehlen=$((fehlen+1)); done
        neu=0; for d in $PAKETE; do [[ "$alt" == *" $d "* ]] || neu=$((neu+1)); done
        ui_msg "Vergleich" "Seit der Sicherung entfernt: $fehlen\nSeitdem neu installiert: $neu (bleiben)\nAPK-Dateien gesichert: $([ -d "$SICHER/$R_ORDNER/apks" ] && echo ja || echo nein)"
    fi
    local -a opt=()
    [ -n "$R_ORDNER" ] && opt+=(apps "Entfernte und deaktivierte Apps zurückholen" on einst "Einstellungen zurücksetzen (Privates DNS, Animationen)" on)
    [ -n "$R_ORDNER" ] && [ -d "$SICHER/$R_ORDNER/apks" ] && opt+=(apk "Fehlende Play-Store-Apps aus APK neu installieren" off)
    { [ $LAUNCHER_DA = 1 ] || [ $ALT_LAUNCHER_DA = 1 ]; } && opt+=(home "Startbildschirm zurück auf Android TV" off deinst "Eigenen Launcher deinstallieren" off)
    [ ${#opt[@]} -eq 0 ] && return 1
    local sel; sel=$(ui_checklist "Wiederherstellen" "Was soll zurückgesetzt werden?" "${opt[@]}") || return 1
    R_APPS=0; R_EINST=0; R_APK=0; R_HOME=0; R_DEINST=0
    [[ "$sel" == *apps* ]] && R_APPS=1; [[ "$sel" == *einst* ]] && R_EINST=1; [[ "$sel" == *apk* ]] && R_APK=1
    [[ "$sel" == *home* ]] && R_HOME=1; [[ "$sel" == *deinst* ]] && { R_DEINST=1; R_HOME=1; }
    return 0
}

# ------------------------------------------------------------------ Aktionen

do_sicherung() {
    local mit_apk="$1" d p
    d="$SICHER/$(date +%Y-%m-%d_%H%M%S)"; mkdir -p "$d"; SICHER_ORDNER="$d"
    log "Sicherung nach $d"
    { tvsh 'getprop ro.product.manufacturer'; tvsh 'getprop ro.product.model'; echo "Android $(tvsh 'getprop ro.build.version.release')"
      tvsh 'getprop ro.build.fingerprint'; echo "Gesichert $(date '+%d.%m.%Y %H:%M')"; } > "$d/geraet.txt"
    liste '--user 0' > "$d/pakete-alle.txt"; liste '-s' > "$d/pakete-system.txt"
    liste '-3' > "$d/pakete-nutzer.txt"; liste '-d' > "$d/pakete-deaktiviert.txt"
    { for p in private_dns_mode private_dns_specifier window_animation_scale transition_animation_scale animator_duration_scale; do echo "global|$p|$(tvsh "settings get global $p")"; done
      for p in enabled_accessibility_services accessibility_enabled; do echo "secure|$p|$(tvsh "settings get secure $p")"; done; } > "$d/einstellungen.txt"
    tvsh 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n1 > "$d/startbildschirm.txt"
    log "  $(wc -l < "$d/pakete-alle.txt") Apps, Einstellungen und Startbildschirm ($(cat "$d/startbildschirm.txt")) gesichert"
    if [ "$mit_apk" = 1 ]; then
        local n=0 gesamt; gesamt=$(wc -l < "$d/pakete-nutzer.txt")
        while read -r p; do
            n=$((n+1)); log "  APK $n/$gesamt: $p"; mkdir -p "$d/apks/$p"
            tvsh "pm path $p" | sed -n 's/^package://p' | while read -r f; do adbc -s "$GERAET" pull "$f" "$d/apks/$p/" >/dev/null; done
        done < "$d/pakete-nutzer.txt"
    fi
}

do_entfernen() {
    local p r
    { echo "# Letzte Auswahl vom $(date '+%d.%m.%Y %H:%M') - paket|methode"; } > "$SICHER/letzte-auswahl.txt.neu"
    for p in $SEL_APPS; do
        if ist_geschuetzt "$p"; then log "  übersprungen (geschützt): $p"; continue; fi
        if [ "$METHODE" = disable ]; then r=$(tvsh "pm disable-user --user 0 $p"); else r=$(tvsh "pm uninstall -k --user 0 $p"); fi
        log "$(printf '  %-45s %s' "$p" "$r")"
        echo "$p|$METHODE" >> "$SICHER/letzte-auswahl.txt.neu"
    done
    mv "$SICHER/letzte-auswahl.txt.neu" "$SICHER/letzte-auswahl.txt"
    [ -n "$SICHER_ORDNER" ] && cp "$SICHER/letzte-auswahl.txt" "$SICHER_ORDNER/entfernt.txt"
    if [ $OPT_DNS = 1 ]; then
        tvsh 'settings put global private_dns_mode hostname' >/dev/null
        tvsh "settings put global private_dns_specifier $M_DNS" >/dev/null
        log "  Privates DNS: $(tvsh 'settings get global private_dns_specifier')"
    fi
    if [ $OPT_ANIM = 1 ]; then
        for p in window_animation_scale transition_animation_scale animator_duration_scale; do tvsh "settings put global $p 0.5" >/dev/null; done
        log "  Animationen: 0.5"
    fi
}

# Nach "am force-stop" bindet Android die Bedienungshilfe des Launchers nicht neu an, die Home-Taste
# landet dann beim Android-TV-Startbildschirm. Eintrag kurz entfernen und wieder setzen, damit sie neu startet.
restart_home_dienst() {
    local akt rest="" p; akt=$(trim "$(tvsh 'settings get secure enabled_accessibility_services')")
    [[ "$akt" == *"$A11Y_DIENST"* ]] || return 0
    for p in ${akt//:/ }; do [ "$p" != "$A11Y_DIENST" ] && [ "$p" != null ] && rest+="${rest:+:}$p"; done
    if [ -n "$rest" ]; then tvsh "settings put secure enabled_accessibility_services $rest" >/dev/null
    else tvsh 'settings delete secure enabled_accessibility_services' >/dev/null; fi
    sleep 1
    tvsh "settings put secure enabled_accessibility_services $akt" >/dev/null
    tvsh 'settings put secure accessibility_enabled 1' >/dev/null
    sleep 1.5
    if tvsh 'dumpsys accessibility' | grep -q 'Bound services.*HomeButtonService'; then log "  Home-Taste aktiv"
    else log "  Home-Taste: Bedienungshilfe bitte am Fernseher prüfen (Einstellungen > Bedienungshilfen)"; fi
}

remove_alt_launcher() {
    [ $ALT_LAUNCHER_DA = 1 ] || return 0
    log "Entferne ältere Jellydroid-Version (de.elias.launcher)"
    tvsh "pm revoke $ALT_LAUNCHER_PKG android.permission.WRITE_SECURE_SETTINGS" >/dev/null
    tvsh "am force-stop $ALT_LAUNCHER_PKG" >/dev/null
    local akt p rest=""; akt=$(trim "$(tvsh 'settings get secure enabled_accessibility_services')")
    for p in ${akt//:/ }; do [ "$p" != "$ALT_A11Y_DIENST" ] && [ "$p" != null ] && rest+="${rest:+:}$p"; done
    if [ -n "$rest" ]; then tvsh "settings put secure enabled_accessibility_services $rest" >/dev/null
    else tvsh 'settings delete secure enabled_accessibility_services' >/dev/null; fi
    tvsh "cmd role remove-role-holder android.app.role.HOME $ALT_LAUNCHER_PKG" >/dev/null
    log "  Deinstallieren: $(adbc -s "$GERAET" uninstall "$ALT_LAUNCHER_PKG")"
    ALT_LAUNCHER_DA=0
}

do_launcher() {
    local r
    if [ $OPT_LAUNCHER = 1 ]; then
        log "Installiere Launcher: $APK_DATEI"
        r=$(adbc -s "$GERAET" install -r "$APK_DATEI"); log "  $r"
        if [[ "$r" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
            log "  Die APK ist anders signiert als der installierte Launcher. Erst über 'Wiederherstellen' deinstallieren, dann erneut."; return
        fi
        [[ "$r" == *Success* ]] || { log "  Installation fehlgeschlagen."; return; }
        LAUNCHER_DA=1
        remove_alt_launcher
        # Erlaubt dem AirPlay-Empfänger, das Vollbild aus dem Hintergrund zu öffnen
        tvsh "appops set $LAUNCHER_PKG SYSTEM_ALERT_WINDOW allow" >/dev/null
        # Sidebar: WLAN-Name lesen (Android gibt ihn nur mit Standortberechtigung heraus)
        tvsh "pm grant $LAUNCHER_PKG android.permission.ACCESS_FINE_LOCATION" >/dev/null
        if [ $OPT_HOME = 1 ]; then
            # Selbstheilung: der Launcher trägt die Bedienungshilfe nach jedem Neustart wieder ein
            tvsh "pm grant $LAUNCHER_PKG android.permission.WRITE_SECURE_SETTINGS" >/dev/null
            local akt neu; akt=$(trim "$(tvsh 'settings get secure enabled_accessibility_services')")
            if [ "$akt" = null ] || [ -z "$akt" ]; then neu=$A11Y_DIENST
            elif [[ "$akt" == *"$A11Y_DIENST"* ]]; then neu=$akt
            else neu="$akt:$A11Y_DIENST"; fi
            tvsh "settings put secure enabled_accessibility_services $neu" >/dev/null
            tvsh 'settings put secure accessibility_enabled 1' >/dev/null
            tvsh "cmd package set-home-activity $LAUNCHER_PKG/.MainActivity" >/dev/null
            tvsh "cmd role add-role-holder android.app.role.HOME $LAUNCHER_PKG" >/dev/null
            log "  Home-Taste übernommen (Bedienungshilfe + Selbstheilung)"
        fi
    fi
    [ $LAUNCHER_DA = 1 ] || { log "Launcher nicht installiert, Einrichtung übersprungen."; return; }

    # Vorkonfiguration als setup.json übergeben (siehe launcher-schnittstelle/SCHNITTSTELLE.md)
    local -a favs=(); local i p
    if [ $OPT_FAV = 1 ]; then
        hat_paket "$M_JF_APP" && favs+=("$M_JF_APP")
        for i in "${!D_KEY[@]}"; do [[ " $M_DIENSTE " == *" ${D_KEY[$i]} "* ]] && { p=$(dienst_paket "$i"); [ -n "$p" ] && favs+=("$p"); }; done
    fi
    local cfg="\"theme\":$(json_esc "$M_THEME"),\"place\":${M_ORT:-0},\"jellyfin_app\":$(json_esc "$M_JF_APP"),\"services_used\":$(json_arr $M_DIENSTE),\"services_rows\":$(json_arr $M_REIHEN)"
    cfg+=$(setup_cfg airplay)$(setup_cfg sidebartaste)$(setup_cfg schrift)
    [ -n "$M_JF_URL" ] && cfg+=",\"jellyfin_url\":$(json_esc "$M_JF_URL")"
    [ -n "$M_SEERR_URL" ] && cfg+=",\"seerr_url\":$(json_esc "$M_SEERR_URL")"
    local json="{\"version\":1,\"config\":{$cfg}"
    [ ${#favs[@]} -gt 0 ] && json+=",\"favorites\":$(json_arr "${favs[@]}")"
    [ -n "$JF_TOKEN" ] && json+=",\"jellyfin\":{\"device_id\":$(json_esc "$JF_DEVICE"),\"token\":$(json_esc "$JF_TOKEN"),\"user_id\":$(json_esc "$JF_USER"),\"server_id\":$(json_esc "$JF_SERVER")}"
    [ -n "$SEERR_COOKIE" ] && json+=",\"seerr\":{\"cookie\":$(json_esc "$SEERR_COOKIE"),\"user_id\":$SEERR_UID}"
    json+=$(setup_block homeassistant)
    json+="}"
    if [ -n "$AVATAR" ]; then
        local ext=${AVATAR##*.}; ext=${ext,,}
        tvsh "mkdir -p $SETUP_PFAD; rm -f $SETUP_PFAD/avatar.jpg $SETUP_PFAD/avatar.jpeg $SETUP_PFAD/avatar.png $SETUP_PFAD/avatar.webp" >/dev/null
        log "Profilbild übertragen: $(adbc -s "$GERAET" push "$AVATAR" "$SETUP_PFAD/avatar.$ext")"
    fi
    push_schrift
    if send_setup "$json"; then
        # Eingerichtet: Funktionen dieser Version, deren Schritt in diesem Ablauf vorkam (oder die keinen brauchen)
        local -a ks=(); local i
        for i in $(funktionen_bis "$(inst_ver)"); do
            { ! hat_seite "${F_SEITE[$i]}" || [[ " $ABLAUF_SEITEN " == *" ${F_SEITE[$i]} "* ]]; } && ks+=("${F_KEY[$i]}")
        done
        schreib_stand "${ks[@]}"
    fi
}

# setup.json-Felder je Funktion aus daten/funktionen.tsv (Funktionen ohne Felder: leer)
setup_cfg() { # Felder im "config"-Block, mit führendem Komma
    case "$1" in
        sidebartaste) [ -n "$SB_KEY" ] && printf ',"sidebar_key":%d' "$SB_KEY" ;;
        schrift) printf ',"font":%s' "$(json_esc "${M_SCHRIFT:-standard}")" ;;
        airplay)
            if [ "${M_AIRPLAY:-1}" = 1 ]; then printf ',"airplay_enabled":true'; else printf ',"airplay_enabled":false'; fi
            printf ',"airplay_name":%s' "$(json_esc "${M_AIRPLAY_NAME:-Jellydroid}")" ;;
    esac
}
setup_block() { # eigene Blöcke, mit führendem Komma
    case "$1" in
        homeassistant)
            [ "${M_HA:-1}" = 1 ] || return 0
            local ha="\"media_player\":$(json_esc "$M_HA_TV"),\"doorbell_event\":$(json_esc "$M_HA_BELL"),\"door_button\":$(json_esc "$M_HA_DOOR")"
            if [ "${M_HA_POPUP:-1}" = 1 ]; then ha+=",\"doorbell_popup\":true"; else ha+=",\"doorbell_popup\":false"; fi
            [ -n "$HA_URL" ] && [ -n "$HA_TOKEN" ] && ha+=",\"url\":$(json_esc "$HA_URL"),\"token\":$(json_esc "$HA_TOKEN")"
            printf ',"homeassistant":{%s}' "$ha" ;;
    esac
}

# Überträgt setup.json (leer = nichts übertragen), startet den Launcher neu und prüft die Übernahme.
send_setup() { # json -> 0 = übernommen oder nichts zu übertragen
    local json="$1" r leer=0
    [ "$json" = '{"version":1,"config":{}}' ] && leer=1
    if [ $leer = 0 ]; then
        local tmp; tmp=$(mktemp); chmod 600 "$tmp"; printf '%s' "$json" > "$tmp"
        tvsh "mkdir -p $SETUP_PFAD" >/dev/null
        r=$(adbc -s "$GERAET" push "$tmp" "$SETUP_PFAD/setup.json"); rm -f "$tmp"
        log "Vorkonfiguration übertragen: $r"
    fi
    tvsh "am force-stop $LAUNCHER_PKG" >/dev/null
    tvsh "am start -n $LAUNCHER_PKG/.MainActivity" >/dev/null
    sleep 5
    restart_home_dienst
    [ $leer = 1 ] && return 0
    if [[ "$(tvsh "ls $SETUP_PFAD/setup.json")" != *"No such file"* ]]; then
        tvsh "rm -f $SETUP_PFAD/setup.json" >/dev/null
        log "  Hinweis: Diese Launcher-Version kennt die Vorkonfiguration noch nicht. Die Datei wurde wieder gelöscht."
        log "  Bitte Launcher aktualisieren oder Einstellungen am Fernseher unter Sidebar > Einstellungen > Launcher Konfiguration setzen."
        return 1
    fi
    # Ab V1.4 schreibt der Launcher setup-ergebnis.txt ("ok" oder "fehler: …")
    local erg; erg=$(trim "$(tvsh "cat $SETUP_PFAD/setup-ergebnis.txt")")
    tvsh "rm -f $SETUP_PFAD/setup-ergebnis.txt" >/dev/null
    if [[ "$erg" == fehler* ]]; then log "  Launcher konnte die Einstellungen nicht übernehmen: $erg"; return 1; fi
    log "  Launcher hat die Einstellungen übernommen."
}

# Update: neue APK über die alte installieren (adb install -r behält alle Einstellungen),
# danach nur die gewählten neuen Funktionen übertragen.
do_update() {
    local r vorher nachher k i
    vorher=$(inst_ver)
    log "Aktualisiere Launcher: $APK_DATEI"
    r=$(adbc -s "$GERAET" install -r "$APK_DATEI"); log "  $r"
    if [[ "$r" == *INSTALL_FAILED_VERSION_DOWNGRADE* ]]; then
        log "  Die APK ist älter als die installierte Version. Zum Zurückgehen erst über 'Wiederherstellen' deinstallieren."; return
    fi
    if [[ "$r" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
        log "  Die APK ist anders signiert als der installierte Launcher. Erst über 'Wiederherstellen' deinstallieren, dann erneut."; return
    fi
    [[ "$r" == *Success* ]] || { log "  Installation fehlgeschlagen."; return; }
    nachher=$(inst_ver)
    log "  Version: ${vorher:-?} → ${nachher:-?}"
    tvsh "appops set $LAUNCHER_PKG SYSTEM_ALERT_WINDOW allow" >/dev/null
    tvsh "pm grant $LAUNCHER_PKG android.permission.ACCESS_FINE_LOCATION" >/dev/null
    [[ "$(tvsh 'settings get secure enabled_accessibility_services')" == *"$A11Y_DIENST"* ]] && \
        tvsh "pm grant $LAUNCHER_PKG android.permission.WRITE_SECURE_SETTINGS" >/dev/null
    local cfg="" bl=""
    for k in $UPD_WAHL; do cfg+=$(setup_cfg "$k"); bl+=$(setup_block "$k"); done
    [[ " $UPD_WAHL " == *" schrift "* ]] && push_schrift
    if send_setup "{\"version\":1,\"config\":{${cfg#,}}$bl}"; then
        # Bisher eingerichtet + jetzt eingerichtet + neue Funktionen, die keine Einrichtung brauchen
        local -a ks=($KONFIGURIERT $UPD_WAHL)
        for i in $(funktionen_bis "$nachher"); do hat_seite "${F_SEITE[$i]}" || ks+=("${F_KEY[$i]}"); done
        schreib_stand "${ks[@]}"
    fi
}

do_restore() {
    local d="" p
    [ -n "$R_ORDNER" ] && d="$SICHER/$R_ORDNER"
    local LAUNCHER_PKG=$LAUNCHER_PKG A11Y_DIENST=$A11Y_DIENST
    if [ $LAUNCHER_DA = 0 ] && [ $ALT_LAUNCHER_DA = 1 ]; then LAUNCHER_PKG=$ALT_LAUNCHER_PKG; A11Y_DIENST=$ALT_A11Y_DIENST; fi
    if [ $R_HOME = 1 ]; then
        log "Startbildschirm zurücksetzen"
        tvsh "pm revoke $LAUNCHER_PKG android.permission.WRITE_SECURE_SETTINGS" >/dev/null
        tvsh "am force-stop $LAUNCHER_PKG" >/dev/null
        local akt rest=""; akt=$(trim "$(tvsh 'settings get secure enabled_accessibility_services')")
        for p in ${akt//:/ }; do [ "$p" != "$A11Y_DIENST" ] && [ "$p" != null ] && rest+="${rest:+:}$p"; done
        if [ -n "$rest" ]; then tvsh "settings put secure enabled_accessibility_services $rest" >/dev/null
        else tvsh 'settings delete secure enabled_accessibility_services' >/dev/null; fi
        tvsh "cmd role remove-role-holder android.app.role.HOME $LAUNCHER_PKG" >/dev/null
        local vorher=com.google.android.tvlauncher/.MainActivity v
        if [ -n "$d" ] && [ -f "$d/startbildschirm.txt" ]; then
            v=$(trim "$(cat "$d/startbildschirm.txt")"); [[ "$v" == */* && "$v" != "$LAUNCHER_PKG"* && "$v" != "$ALT_LAUNCHER_PKG"* ]] && vorher="$v"
        fi
        log "  $(tvsh "cmd package set-home-activity $vorher")  ($vorher)"
        [ $R_DEINST = 1 ] && log "  Deinstallieren: $(adbc -s "$GERAET" uninstall "$LAUNCHER_PKG")"
    fi
    [ -n "$d" ] || return
    lade_pakete
    if [ $R_APPS = 1 ]; then
        log "Apps zurückholen"
        while read -r p; do
            [ -z "$p" ] && continue; hat_paket "$p" && continue
            [ $R_DEINST = 1 ] && [ "$p" = "$LAUNCHER_PKG" ] && continue
            log "$(printf '  %-45s %s' "$p" "$(tvsh "cmd package install-existing --user 0 $p")")"
        done < "$d/pakete-alle.txt"
        local altaus; altaus=" $(tr '\n' ' ' < "$d/pakete-deaktiviert.txt") "
        for p in $DEAKTIVIERT; do [[ "$altaus" == *" $p "* ]] || log "$(printf '  %-45s %s' "$p" "$(tvsh "pm enable --user 0 $p")")"; done
        lade_pakete
    fi
    if [ $R_APK = 1 ] && [ -d "$d/apks" ]; then
        local ad files
        for ad in "$d"/apks/*/; do
            p=$(basename "$ad"); hat_paket "$p" && continue
            files=("$ad"*.apk); [ -f "${files[0]}" ] || continue
            if [ ${#files[@]} -eq 1 ]; then r=$(adbc -s "$GERAET" install "${files[0]}"); else r=$(adbc -s "$GERAET" install-multiple "${files[@]}"); fi
            log "$(printf '  APK %-41s %s' "$p" "$r")"
        done
    fi
    if [ $R_EINST = 1 ] && [ -f "$d/einstellungen.txt" ]; then
        log "Einstellungen zurücksetzen"
        local ns k v
        while IFS='|' read -r ns k v; do
            [ -z "$k" ] && continue; [[ "$k" == *accessibility* ]] && continue   # regelt der Startbildschirm-Punkt
            if [ -z "$v" ] || [ "$v" = null ]; then tvsh "settings delete $ns $k" >/dev/null; else tvsh "settings put $ns $k $v" >/dev/null; fi
            log "  $k = $v"
        done < "$d/einstellungen.txt"
    fi
}

zusammenfassung() {
    local modus="$1" z=""
    case "$modus" in
        voll|sicherung) z+="1. Sicherung erstellen$([ $OPT_APK = 1 ] && echo ' (mit APK-Dateien)')\n" ;;
        erneut|launcher|update) z+="1. Kurze Sicherung erstellen\n" ;;
    esac
    if [ "$modus" = update ]; then
        z+="2. Launcher aktualisieren: $(basename "$APK_DATEI") (Einstellungen bleiben)\n"
        if [ -z "$UPD_WAHL" ]; then z+="3. Keine Funktion neu einrichten\n"; else
            z+="3. Einrichten:\n"
            local k i
            for k in $UPD_WAHL; do
                case "$k" in
                    airplay) if [ "${M_AIRPLAY:-1}" = 1 ]; then z+="   AirPlay: an, als „${M_AIRPLAY_NAME:-Jellydroid}“\n"; else z+="   AirPlay: aus\n"; fi ;;
                    schrift) z+="   Schrift: $(schrift_text)\n" ;;
                    sidebartaste) z+="   Sidebar-Taste: $( [ -z "$SB_KEY" ] && echo unverändert || { [ "$SB_KEY" -le 0 ] && echo Standard || echo "$SB_NAME"; } )\n" ;;
                    homeassistant) if [ "${M_HA:-1}" != 1 ]; then z+="   Home Assistant: aus\n"; elif [ -n "$HA_TOKEN" ]; then z+="   Home Assistant: angemeldet\n"; else z+="   Home Assistant: später am Fernseher anmelden\n"; fi ;;
                    *) for i in "${!F_KEY[@]}"; do [ "${F_KEY[$i]}" = "$k" ] && z+="   ${F_NAME[$i]}\n"; done ;;
                esac
            done
        fi
    fi
    if [ "$modus" = voll ] || [ "$modus" = erneut ]; then
        z+="2. $(echo $SEL_APPS | wc -w) Apps $([ "$METHODE" = disable ] && echo deaktivieren || echo 'für deinen Benutzer entfernen')\n"
        [ $OPT_DNS = 1 ] && z+="   Privates DNS: $M_DNS\n"; [ $OPT_ANIM = 1 ] && z+="   Animationen: halbe Dauer\n"
    fi
    if [ "$modus" = voll ] || [ "$modus" = launcher ]; then
        [ $OPT_LAUNCHER = 1 ] && z+="3. Launcher installieren: $(basename "$APK_DATEI")$([ $OPT_HOME = 1 ] && echo ', Home-Taste übernehmen')\n"
        local jf se
        if [ -n "$JF_TOKEN" ]; then jf="Jellyfin angemeldet als $JF_USERNAME"; elif [ -n "$M_JF_URL" ]; then jf="Jellyfin ohne Anmeldung"; else jf="ohne Jellyfin"; fi
        if [ -n "$SEERR_COOKIE" ]; then se="Seerr angemeldet als $SEERR_NAME"; elif [ -n "$M_SEERR_URL" ]; then se="Seerr ohne Anmeldung"; else se="ohne Seerr"; fi
        z+="4. Launcher einrichten: $(echo $M_DIENSTE | wc -w) Dienste, $jf, $se$([ -n "$AVATAR" ] && echo ', neues Profilbild')\n"
        if [ "${M_AIRPLAY:-1}" = 1 ]; then z+="   AirPlay: an, als „${M_AIRPLAY_NAME:-Jellydroid}“\n"; else z+="   AirPlay: aus\n"; fi
        z+="   Schrift: $(schrift_text)\n"
        if [ "${M_HA:-1}" != 1 ]; then z+="   Home Assistant: aus\n"; elif [ -n "$HA_TOKEN" ]; then z+="   Home Assistant: angemeldet\n"; else z+="   Home Assistant: später am Fernseher anmelden\n"; fi
        [ -n "$SB_KEY" ] && z+="   Sidebar-Taste: $( [ "$SB_KEY" -le 0 ] && echo Standard || echo "$SB_NAME")\n"
    fi
    if [ "$modus" = restore ]; then
        z+="Sicherung: ${R_ORDNER:-keine}\n"
        [ $R_APPS = 1 ] && z+="- Apps zurückholen\n"; [ $R_APK = 1 ] && z+="- Fehlende Apps aus APK neu installieren\n"
        [ $R_EINST = 1 ] && z+="- Einstellungen zurücksetzen\n"; [ $R_HOME = 1 ] && z+="- Startbildschirm zurück auf Android TV\n"
        [ $R_DEINST = 1 ] && z+="- Launcher deinstallieren\n"
    fi
    echo "$z"
}

step_ausfuehren() {
    local modus="$1"
    ui_yesno "Zusammenfassung" "Folgendes wird jetzt auf $MODELL ($GERAET) ausgeführt:\n\n$(zusammenfassung "$modus")\nJetzt ausführen?" || return 1
    clear 2>/dev/null
    log "=== Jellydroid Installer: $modus auf $MODELL ($GERAET) ==="
    [ "$(adbc -s "$GERAET" get-state)" = device ] || { adbc connect "$GERAET" >/dev/null; sleep 2; }
    case "$modus" in
        voll)      do_sicherung $OPT_APK; do_entfernen; do_launcher ;;
        erneut)    do_sicherung 0; do_entfernen ;;
        sicherung) do_sicherung $OPT_APK ;;
        launcher)  do_sicherung 0; do_launcher ;;
        update)    do_sicherung 0; do_update ;;
        restore)   do_restore ;;
    esac
    { [ "$modus" = voll ] || [ "$modus" = launcher ] || [ "$modus" = update ]; } && [ $LAUNCHER_DA = 1 ] && tvsh "am start -n $LAUNCHER_PKG/.MainActivity" >/dev/null
    log "=== Fertig ==="
    lade_pakete
    echo; rd -p "Fertig. Protokoll: $LOGF   [Enter] " _
}

# ------------------------------------------------------------------ Abläufe

ablauf() {
    local modus="$1"
    case "$modus" in voll) ABLAUF_SEITEN="airplay schrift homeassistant" ;; launcher) ABLAUF_SEITEN="airplay schrift sidebar homeassistant" ;; *) ABLAUF_SEITEN="" ;; esac
    case "$modus" in
        voll)      step_vorbereitung && step_verbindung && step_sicherung && step_bloatware voll && step_launcher voll && step_airplay && step_schrift && step_dienste && step_konten && step_homeassistant && { step_tipps; true; } && step_ausfuehren voll ;;
        erneut)    step_verbindung && step_bloatware erneut && step_ausfuehren erneut ;;
        sicherung) step_verbindung && step_sicherung && step_ausfuehren sicherung ;;
        restore)   step_verbindung && step_restore && step_ausfuehren restore ;;
        launcher)  step_verbindung && step_launcher launcher && step_airplay && step_schrift && step_sidebar && step_dienste && step_konten && step_homeassistant && step_ausfuehren launcher ;;
        update)    step_verbindung && step_update && step_ausfuehren update ;;
        tipps)     step_tipps ;;
    esac
}

command -v curl >/dev/null 2>&1 || echo "Hinweis: curl fehlt. Jellyfin/Seerr-Anmeldung und ADB-Download brauchen curl."
while true; do
    wahl=$(ui_menu "$TITEL" "Was möchtest du tun?" \
        voll "Geführte Einrichtung (empfohlen)" \
        update "Launcher aktualisieren (Einstellungen bleiben)" \
        erneut "Auswahl erneut anwenden (nach Neustart)" \
        sicherung "Nur Sicherung erstellen" \
        restore "Wiederherstellen" \
        launcher "Launcher installieren oder neu einrichten" \
        tipps "Tipps lesen" \
        ende "Beenden") || break
    [ "$wahl" = ende ] && break
    ablauf "$wahl"
done
save_merk
clear 2>/dev/null
echo "Auf Wiedersehen."
