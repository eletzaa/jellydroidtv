# Werbefilter per Privatem DNS (AdGuard) + kuerzere Animationen (0.5x).
# Rueckgaengig: dns-animationen-rueckgaengig.ps1
param([string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip

$werte = [ordered]@{
    'private_dns_mode'           = 'hostname'              # Privates DNS: festen Anbieter verwenden
    'private_dns_specifier'      = 'dns.adguard-dns.com'   # AdGuard DNS (blockt Werbung/Tracking)
    'window_animation_scale'     = '0.5'                   # Fenster-Animationen halb so lang
    'transition_animation_scale' = '0.5'                   # Uebergangs-Animationen halb so lang
    'animator_duration_scale'    = '0.5'                   # Animationsdauer halb so lang
}
foreach ($k in $werte.Keys) {
    & $adb -s $device shell settings put global $k $werte[$k]
    $neu = (& $adb -s $device shell settings get global $k) -join ''
    Write-Output ("{0,-28} {1}" -f $k, $neu)
}
