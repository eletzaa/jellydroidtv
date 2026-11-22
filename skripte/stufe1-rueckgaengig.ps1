# Stufe 1 rueckgaengig: alle in stufe1.ps1 deaktivierten Apps wieder aktivieren.
param([string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip

$pakete = @(
    'org.droidtv.usagelogger',          # Philips Nutzungs-Telemetrie
    'org.droidtv.nettvregistration',    # Philips TV-Registrierung / Marketing
    'org.droidtv.nettvrecommender',     # Philips Empfehlungen / Werbung
    'org.droidtv.demome',               # Laden-Demomodus
    'com.google.android.feedback',      # Google Feedback / Diagnose-Upload
    'com.zeasn.whalelive',              # Zeasn/Whale vorinstallierte werbefinanzierte Live-Kanaele
    'com.google.android.videos',        # Google Play Filme (eingestellt)
    'com.google.android.play.games',    # Google Play Spiele
    'org.droidtv.android.fitness'       # Philips Fitness-App
)

foreach ($p in $pakete) {
    $out = (& $adb -s $device shell pm enable $p 2>&1) -join ' '
    Write-Output ("{0,-34} {1}" -f $p, $out)
}
