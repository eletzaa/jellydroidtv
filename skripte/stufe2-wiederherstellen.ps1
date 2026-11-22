# Rueckgaengig: per stufe2-entfernen.ps1 entfernte Apps wiederherstellen (alternativ: TV neu starten).
param([string[]]$Nur, [string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip

$pakete = @(
    'com.google.android.youtube.tvmusic','com.google.android.videos','com.google.android.play.games',
    'org.droidtv.demome','org.droidtv.amazonalexa','io.mimi.tvapp','io.mimi.sdk.service',
    'com.phorus.playfi.tv','com.phorus.playfi.softap','com.vewd.core.browserui',
    'org.droidtv.nettvrecommender','org.droidtv.astrahdplus','org.droidtv.nettv.market',
    'org.droidtv.usagelogger','org.droidtv.nettvregistration','com.google.android.feedback',
    'com.zeasn.whalelive','org.droidtv.android.fitness'
)
if ($Nur) { $pakete = $Nur }

foreach ($p in $pakete) {
    $out = (& $adb -s $device shell cmd package install-existing --user 0 $p 2>&1) -join ' '
    Write-Output ("{0,-36} {1}" -f $p, $out)
}
