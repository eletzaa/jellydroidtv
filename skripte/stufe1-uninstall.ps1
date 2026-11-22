# Stufe 1 (Variante uninstall): Apps fuer Benutzer 0 entfernen (pm uninstall --user 0).
# Die APK bleibt im System; rueckgaengig mit stufe1-uninstall-rueckgaengig.ps1
# Optional: Paketnamen als Parameter uebergeben, um nur diese zu entfernen.
param([string[]]$Nur, [string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip

$pakete = @(
    'org.droidtv.usagelogger',          # Philips Nutzungs-Telemetrie
    # 'org.droidtv.nettvregistration',  # NICHT entfernen: sonst Absturzschleife in org.droidtv.xtvsystem (2026-11-17)
    'org.droidtv.nettvrecommender',     # Philips Empfehlungen / Werbung
    'org.droidtv.demome',               # Laden-Demomodus
    'com.google.android.feedback',      # Google Feedback / Diagnose-Upload
    'com.zeasn.whalelive',              # Zeasn/Whale vorinstallierte werbefinanzierte Live-Kanaele
    'com.google.android.videos',        # Google Play Filme (eingestellt)
    'com.google.android.play.games',    # Google Play Spiele
    'org.droidtv.android.fitness'       # Philips Fitness-App
)
if ($Nur) { $pakete = $Nur }

foreach ($p in $pakete) {
    $out = (& $adb -s $device shell pm uninstall --user 0 $p 2>&1) -join ' '
    Write-Output ("{0,-34} {1}" -f $p, $out)
}
