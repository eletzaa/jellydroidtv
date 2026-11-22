# Rueckgaengig: Privates DNS auf "Automatisch", Animationen auf 1.0 (Zustand vor der Aenderung).
param([string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip

& $adb -s $device shell settings put global private_dns_mode opportunistic   # Privates DNS: Automatisch (Standard)
& $adb -s $device shell settings delete global private_dns_specifier          # war vorher nicht gesetzt
& $adb -s $device shell settings put global window_animation_scale 1.0
& $adb -s $device shell settings put global transition_animation_scale 1.0
& $adb -s $device shell settings put global animator_duration_scale 1.0
foreach ($k in 'private_dns_mode','private_dns_specifier','window_animation_scale','transition_animation_scale','animator_duration_scale') {
    Write-Output ("{0,-28} {1}" -f $k, ((& $adb -s $device shell settings get global $k) -join ''))
}
