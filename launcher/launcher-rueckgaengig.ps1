# Setzt den bisherigen Startbildschirm (Philips/Google) wieder als Standard und schaltet die
# Bedienungshilfe "Jellydroid: Home-Taste" aus. Der Launcher bleibt installiert;
# deinstallieren mit dem Schalter -Deinstallieren.
param([switch]$Deinstallieren, [string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip
$sicherungHome = Join-Path $PSScriptRoot '..\sicherung\startbildschirm-vorher.txt'
$sicherungA11y = Join-Path $PSScriptRoot '..\sicherung\bedienungshilfen-vorher.txt'
$dienst = 'de.jellydroid.launcher/de.jellydroid.launcher.HomeButtonService'

& $adb connect $device | Out-Null

# Bedienungshilfe entfernen
# Zuerst die Selbstheilung abschalten, sonst traegt sich der Dienst wieder ein
& $adb -s $device shell pm revoke de.jellydroid.launcher android.permission.WRITE_SECURE_SETTINGS 2>&1 | Out-Null
& $adb -s $device shell am force-stop de.jellydroid.launcher 2>&1 | Out-Null

$aktuell = ((& $adb -s $device shell settings get secure enabled_accessibility_services 2>&1) -join '').Trim()
$rest = ($aktuell -split ':' | Where-Object { $_ -and $_ -ne $dienst -and $_ -ne 'null' }) -join ':'
if ($rest) { & $adb -s $device shell settings put secure enabled_accessibility_services "$rest" }
else { & $adb -s $device shell settings delete secure enabled_accessibility_services | Out-Null }
Write-Output "Bedienungshilfe ausgeschaltet."

# Startbildschirm-Rolle und Standard zuruecksetzen
& $adb -s $device shell cmd role remove-role-holder android.app.role.HOME de.jellydroid.launcher 2>&1 | Out-Null
$vorher = 'com.google.android.tvlauncher/.MainActivity'
if (Test-Path $sicherungHome) { $vorher = (Get-Content $sicherungHome -Raw).Trim() }
Write-Output "Setze Startbildschirm zurueck auf: $vorher"
Write-Output (& $adb -s $device shell cmd package set-home-activity $vorher 2>&1)
if ($Deinstallieren) { Write-Output (& $adb -s $device uninstall de.jellydroid.launcher 2>&1) }
& $adb -s $device shell input keyevent KEYCODE_HOME
