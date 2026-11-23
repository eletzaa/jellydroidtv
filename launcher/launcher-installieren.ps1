# Installiert den eigenen Launcher auf dem Fernseher und macht ihn zum Startbildschirm.
# Weil die Philips-Firmware den Standard-Startbildschirm ignoriert, wird zusaetzlich die
# Bedienungshilfe "Jellydroid: Home-Taste" eingeschaltet, die die Home-Taste abfaengt.
# Vorher wird der bisherige Zustand in ..\sicherung\ gesichert. Rueckgaengig: launcher-rueckgaengig.ps1
param([string]$Apk = (Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'), [string]$Ip)
$adb    = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip
$sicherungHome = Join-Path $PSScriptRoot '..\sicherung\startbildschirm-vorher.txt'
$sicherungA11y = Join-Path $PSScriptRoot '..\sicherung\bedienungshilfen-vorher.txt'
$dienst = 'de.jellydroid.launcher/de.jellydroid.launcher.HomeButtonService'
New-Item -ItemType Directory -Force -Path (Split-Path $sicherungHome) | Out-Null

& $adb connect $device | Out-Null
if (-not (Test-Path $Apk)) { Write-Output "APK nicht gefunden: $Apk  (in Android Studio: Build > Build APK)"; exit 1 }

if (-not (Test-Path $sicherungHome)) {
    $vorher = (& $adb -s $device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1 | Select-Object -Last 1).Trim()
    Set-Content -Path $sicherungHome -Value $vorher
    Write-Output "Bisheriger Startbildschirm gesichert: $vorher"
}

Write-Output (& $adb -s $device install -r $Apk 2>&1)
Write-Output (& $adb -s $device shell cmd package set-home-activity de.jellydroid.launcher/.MainActivity 2>&1)

# Bedienungshilfe einschalten, vorhandene Eintraege behalten
$aktuell = ((& $adb -s $device shell settings get secure enabled_accessibility_services 2>&1) -join '').Trim()
if (-not (Test-Path $sicherungA11y)) { Set-Content -Path $sicherungA11y -Value $aktuell }
if ($aktuell -eq 'null' -or $aktuell -eq '') { $neu = $dienst }
elseif ($aktuell -like "*$dienst*") { $neu = $aktuell }
else { $neu = "$aktuell`:$dienst" }
& $adb -s $device shell settings put secure enabled_accessibility_services "$neu"
& $adb -s $device shell settings put secure accessibility_enabled 1
Write-Output ("Bedienungshilfen jetzt: " + ((& $adb -s $device shell settings get secure enabled_accessibility_services 2>&1) -join ''))

& $adb -s $device shell input keyevent KEYCODE_HOME
Write-Output 'Fertig. Druecke die Home-Taste auf der Fernbedienung.'
