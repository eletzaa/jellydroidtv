# Erstellt den Ordner "Jellydroid" und "Jellydroid.zip" zum Weitergeben:
# Installer und aktuelle Launcher-App in einem Ordner, ohne deine Einstellungen,
# Sicherungen und Protokolle. Der Empfaenger startet darin "Installer starten (Windows).cmd"
# bzw. "bash installer-linux.sh". ADB laedt der Installer bei Bedarf selbst.
#
# Aufruf im Projektordner:  .\paket-erstellen.ps1             (Launcher neu bauen, dann packen)
#                           .\paket-erstellen.ps1 -OhneBauen  (zuletzt gebaute APK verwenden)
#                           .\paket-erstellen.ps1 -Apk <pfad>  (bestimmte APK verwenden)
param([switch]$OhneBauen, [string]$Apk)
$ErrorActionPreference = 'Stop'
$root   = $PSScriptRoot
$quelle = Join-Path $root 'installer'
$ziel   = Join-Path $root 'Jellydroid'
$zip    = Join-Path $root 'Jellydroid.zip'

if (-not $OhneBauen -and -not $Apk) { & (Join-Path $root 'launcher\bauen.ps1') -NurBauen }

# APK suchen: angegebene Datei, sonst die aktuelle launcher\ausgabe\Jellydroid-V*.apk, sonst die neueste aus den Build-Ordnern
$fertig = Get-ChildItem (Join-Path $root 'launcher\ausgabe') -Filter 'Jellydroid*.apk' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName   # hierhin kopiert bauen.ps1 die fertige APK
if ($Apk) { $apk = $Apk } elseif ($fertig) { $apk = $fertig } else {
    $orte = @((Join-Path $env:LOCALAPPDATA 'EliasBuild\out'), (Join-Path $root 'launcher\app\build\outputs'),
              (Join-Path $root 'launcher\build'))
    $apk = $orte | Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem $_ -Recurse -Filter '*.apk' -File -ErrorAction SilentlyContinue } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $apk -or -not (Test-Path $apk)) { throw 'Keine Launcher-APK gefunden. Erst den Launcher bauen oder -Apk <pfad> angeben.' }
Write-Output "Launcher-APK: $apk ($((Get-Item $apk).LastWriteTime))"

# Was nicht ins Paket gehoert: persoenliche Laufzeitdateien und Entwickler-Unterlagen
$ohne = @('sicherungen', 'platform-tools', 'installer-einstellungen.*', 'installer-protokoll.txt',
          'launcher.apk', 'Jellydroid-V*.apk', 'setup.json', 'avatar.*', 'launcher-schnittstelle')

if (Test-Path $ziel) { Remove-Item $ziel -Recurse -Force }
New-Item -ItemType Directory -Path $ziel | Out-Null
Get-ChildItem $quelle -Force |
    Where-Object { $n = $_.Name; -not ($ohne | Where-Object { $n -like $_ }) } |
    Copy-Item -Destination $ziel -Recurse
# Nur eine APK im Paket, mit Versionsnummer im Namen (der Installer nimmt die hoechste Jellydroid-V*.apk)
$apkName = if ((Split-Path $apk -Leaf) -match '^Jellydroid-V\d+(\.\d+){0,3}\.apk$') { Split-Path $apk -Leaf } else { 'launcher.apk' }
Copy-Item $apk (Join-Path $ziel $apkName)

if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $ziel -DestinationPath $zip
Write-Output "Fertig: $ziel"
Write-Output "        $zip  (diese Datei weitergeben)"
Get-ChildItem $ziel -Recurse -File | ForEach-Object { '  ' + $_.FullName.Substring($ziel.Length + 1) }
