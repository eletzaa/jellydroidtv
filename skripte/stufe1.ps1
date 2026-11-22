# Stufe 1: Werbung/Telemetrie-Apps REVERSIBEL deaktivieren (nichts wird deinstalliert).
# Rueckgaengig machen: stufe1-rueckgaengig.ps1
param([string]$Ip)
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

foreach ($p in $pakete) {
    $out = (& $adb -s $device shell pm disable-user --user 0 $p 2>&1) -join ' '
    Write-Output ("{0,-34} {1}" -f $p, $out)
}
