# Baut die Release-APK lokal und spielt sie auf den Fernseher.
# Werkzeuge liegen außerhalb von OneDrive unter %LOCALAPPDATA%\JellydroidBuild (JDK 17, Android SDK, Gradle 8.9).
# Fehlen sie, richtet das Skript sie beim ersten Lauf selbst ein (ca. 500 MB Download, einmalig;
# für AirPlay kommen NDK und CMake dazu, noch einmal rund 1 GB).
# Aufruf im Ordner launcher:  .\bauen.ps1            (bauen + installieren)
#                             .\bauen.ps1 -NurBauen  (nur bauen)
#                             .\bauen.ps1 -Ip 192.168.1.50  (andere TV-Adresse; sonst die aus dem Installer)
# Die fertige APK liegt danach unter %LOCALAPPDATA%\JellydroidBuild\out\app\outputs\apk\release\app-release.apk
param([switch]$NurBauen, [string]$Ip)
$b = "$env:LOCALAPPDATA\JellydroidBuild"
# Früherer Ordnername: einmalig umbenennen, damit JDK und SDK nicht neu geladen werden
$alt = "$env:LOCALAPPDATA\EliasBuild"
if (-not (Test-Path $b) -and (Test-Path $alt)) { Rename-Item $alt $b }
$env:JAVA_HOME = "$b\jdk17"
$env:ANDROID_HOME = "$b\sdk"
$env:JELLYDROID_BUILD_DIR = "$b\out"   # Build-Ausgabe außerhalb von OneDrive
$apk = "$b\out\app\outputs\apk\release\app-release.apk"
# Windows-Zertifikatsspeicher (Firmen-Proxy) und Selector-Einstellung, ohne die Java hier keine Loopback-Verbindung aufbaut
$o = "-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.WindowsSelectorProvider -Djdk.net.unixdomain.tmpdir=C:\Windows\Temp"
$env:JAVA_TOOL_OPTIONS = $o

function Get-Archiv([string]$url, [string]$name) {
    $zip = Join-Path $b "$name.zip"
    Write-Host "Lade $name ..."
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest $url -OutFile $zip -UseBasicParsing
    return $zip
}

# ---- Werkzeuge bei Bedarf einrichten ----
New-Item -ItemType Directory -Force $b | Out-Null
if (-not (Test-Path "$b\jdk17\bin\java.exe")) {
    $zip = Get-Archiv "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" "jdk17"
    $tmp = Join-Path $b "jdk-tmp"
    Expand-Archive $zip $tmp -Force
    Move-Item (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName "$b\jdk17" -Force
    Remove-Item $tmp, $zip -Recurse -Force
}
if (-not (Test-Path "$b\gradle-8.9\bin\gradle.bat")) {
    $zip = Get-Archiv "https://services.gradle.org/distributions/gradle-8.9-bin.zip" "gradle"
    Expand-Archive $zip $b -Force
    Remove-Item $zip -Force
}
$sdkm = "$b\sdk\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkm)) {
    $zip = Get-Archiv "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" "cmdline-tools"
    $tmp = Join-Path $b "sdk\cmdline-tools-tmp"
    Expand-Archive $zip $tmp -Force
    New-Item -ItemType Directory -Force "$b\sdk\cmdline-tools" | Out-Null
    Move-Item "$tmp\cmdline-tools" "$b\sdk\cmdline-tools\latest" -Force
    Remove-Item $tmp, $zip -Recurse -Force
}
if (-not (Test-Path "$b\sdk\platforms\android-34") -or -not (Test-Path "$b\sdk\build-tools\34.0.0") -or
    -not (Test-Path "$b\sdk\ndk\27.0.12077973") -or -not (Test-Path "$b\sdk\cmake\3.22.1")) {
    Write-Host "Richte Android SDK ein (Lizenzen werden angenommen) ..."
    $yes = Join-Path $b "yes.txt"
    Set-Content $yes (("y`r`n") * 30) -Encoding ascii
    cmd /c "`"$sdkm`" --sdk_root=`"$b\sdk`" --licenses < `"$yes`" > nul 2>&1"
    cmd /c "`"$sdkm`" --sdk_root=`"$b\sdk`" platforms;android-34 build-tools;34.0.0 ndk;27.0.12077973 cmake;3.22.1 < `"$yes`" > nul 2>&1"
    Remove-Item $yes -Force
}
# SDK-Pfad für Gradle (local.properties ist rechnerspezifisch und wird nicht veröffentlicht)
Set-Content (Join-Path $PSScriptRoot "local.properties") ("sdk.dir=" + ("$b\sdk" -replace '\\', '\\' -replace ':', '\:')) -Encoding ascii
if (-not $env:JELLYDROID_KEYSTORE_B64 -and -not $env:JELLYDROID_KEYSTORE -and -not (Test-Path (Join-Path $PSScriptRoot "signing\debug.keystore"))) {
    Write-Warning "Kein Signaturschlüssel (launcher\signing\debug.keystore oder JELLYDROID_KEYSTORE): die APK bekommt eine neue Signatur und lässt sich nicht über die installierte Version spielen."
}

# ---- Bauen ----
& "$b\gradle-8.9\bin\gradle.bat" -p $PSScriptRoot assembleRelease --no-daemon "-Dorg.gradle.jvmargs=-Xmx3g $o"
if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen" }
# Kopie im Projektordner mit Versionsnummer aus app/build.gradle.kts (versionName), z. B. Jellydroid-V1.4.apk.
# In "ausgabe" liegt immer nur die AKTUELLE APK; ältere Versionen wandern ins Archiv "ausgabe\archiv"
# (nichts wird gelöscht). Installer und paket-erstellen.ps1 suchen nur direkt in "ausgabe", nie im Archiv.
# Neue Funktionen kommen immer in diese eine APK; vor dem Bau versionName/versionCode hochzählen.
$ver = (Select-String -Path (Join-Path $PSScriptRoot "app\build.gradle.kts") -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
$out = Join-Path $PSScriptRoot "ausgabe"
$archiv = Join-Path $out "archiv"
New-Item -ItemType Directory -Force $out, $archiv | Out-Null
$copy = Join-Path $out "Jellydroid-V$ver.apk"
Get-ChildItem $out -Filter "Jellydroid*.apk" -File | Where-Object { $_.FullName -ne $copy } | ForEach-Object {
    $dest = Join-Path $archiv $_.Name
    if (Test-Path $dest) { $dest = Join-Path $archiv ("{0}-{1:yyyyMMdd-HHmm}{2}" -f $_.BaseName, $_.LastWriteTime, $_.Extension) }
    Move-Item $_.FullName $dest
    Write-Host "Archiviert: $dest"
}
Copy-Item $apk $copy -Force
Write-Host "APK: $copy"
# Sofort in den Installer übernehmen (Weitergabe-Ordner "Jellydroid" neben dem Quellcode)
$paket = Join-Path $PSScriptRoot "..\..\Jellydroid"
if (Test-Path $paket) {
    # Unter dem Versionsnamen ablegen, denn der Installer nimmt die höchste Jellydroid-V*.apk vor launcher.apk
    Get-ChildItem $paket -Filter "Jellydroid-V*.apk" -File | Where-Object { $_.Name -ne "Jellydroid-V$ver.apk" } | Remove-Item -Force
    Copy-Item $copy (Join-Path $paket "Jellydroid-V$ver.apk") -Force
    Copy-Item $copy (Join-Path $paket "launcher.apk") -Force
    Write-Host "Installer aktualisiert: $paket\Jellydroid-V$ver.apk"
}
if ($NurBauen) { return }
$adb = "$PSScriptRoot\..\platform-tools\adb.exe"
. (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
$device = Get-Fernseher $Ip
& $adb connect $device | Out-Null
& $adb -s $device install -r $apk
& $adb -s $device shell am start -n de.jellydroid.launcher/.MainActivity | Out-Null
