# Rueckgaengig: per stufe1-uninstall.ps1 entfernte Apps fuer Benutzer 0 wiederherstellen.
param([string[]]$Nur, [string]$Ip)
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
if ($Nur) { $pakete = $Nur }

foreach ($p in $pakete) {
    $out = (& $adb -s $device shell cmd package install-existing --user 0 $p 2>&1) -join ' '
    $en  = (& $adb -s $device shell pm enable $p 2>&1) -join ' '
    Write-Output ("{0,-34} {1} | {2}" -f $p, $out, $en)
}
