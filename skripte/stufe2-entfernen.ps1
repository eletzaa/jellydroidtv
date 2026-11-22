# Stufe 2: ungewollte vorinstallierte Apps fuer Benutzer 0 entfernen (pm uninstall --user 0).
# ACHTUNG: Die Philips-Firmware stellt diese Apps bei jedem echten Neustart wieder her.
#          Nach einem Neustart (z. B. Stromausfall, Update) dieses Skript einfach erneut ausfuehren.
# Rueckgaengig: stufe2-wiederherstellen.ps1
param([string[]]$Nur, [string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip

$pakete = @(
    # --- von Elias genannt ---
    'com.google.android.youtube.tvmusic',   # YouTube Music
    'com.google.android.videos',            # Google Play Filme & TV (eingestellt)
    'com.google.android.play.games',        # Google Play Spiele
    'org.droidtv.demome',                   # Demo-Menue (Laden-Demomodus)
    'org.droidtv.amazonalexa',              # Alexa (Philips-Integration)
    'io.mimi.tvapp',                        # Mimi Sound Personalization (App)
    'io.mimi.sdk.service',                  # Mimi Hintergrunddienst
    'com.phorus.playfi.tv',                 # DTS Play-Fi (App)
    'com.phorus.playfi.softap',             # DTS Play-Fi Hotspot-Dienst
    'com.vewd.core.browserui',              # Webbrowser (Vewd/Opera-Oberflaeche)
    'org.droidtv.nettvrecommender',         # "Top-Auswahl" (Philips Empfehlungen/Werbung)
    'org.droidtv.astrahdplus',              # HD+ Info-App
    'org.droidtv.nettv.market',             # "Smart TV Sammlung" / Philips App-Galerie
    # --- bereits genehmigte Gruppe A (Telemetrie/Werbung) ---
    'org.droidtv.usagelogger',              # Philips Nutzungs-Telemetrie
    # 'org.droidtv.nettvregistration',      # NICHT entfernen: sonst Absturzschleife in org.droidtv.xtvsystem (2026-11-17)
    'com.google.android.feedback',          # Google Feedback / Diagnose-Upload
    'com.zeasn.whalelive',                  # Zeasn/Whale werbefinanzierte Live-Kanaele
    'org.droidtv.android.fitness'           # Philips Fitness-App
)
if ($Nur) { $pakete = $Nur }

foreach ($p in $pakete) {
    $out = (& $adb -s $device shell pm uninstall --user 0 $p 2>&1) -join ' '
    Write-Output ("{0,-36} {1}" -f $p, $out)
}
