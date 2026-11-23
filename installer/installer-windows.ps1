#Requires -Version 5.1
<#
  Jellydroid Installer (Windows)
  Geführte Einrichtung: Verbindung per ADB, Sicherung, Bloatware entfernen, Launcher installieren
  und vorkonfigurieren (Streamingdienste, Jellyfin mit Quick Connect, Seerr), Wiederherstellung.

  Start: Doppelklick auf "Installer starten (Windows).cmd"
  oder:  powershell -ExecutionPolicy Bypass -STA -File .\installer-windows.ps1

  Nichts am Fernseher wird geändert, bevor du auf der letzten Seite "Jetzt ausführen" klickst.
#>

$ErrorActionPreference = 'Continue'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Web
[System.Windows.Forms.Application]::EnableVisualStyles()
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$Root        = $PSScriptRoot
$DatenDir    = Join-Path $Root 'daten'
$SicherDir   = Join-Path $Root 'sicherungen'
$EinstDatei  = Join-Path $Root 'installer-einstellungen.json'
$LogDatei    = Join-Path $Root 'installer-protokoll.txt'
$LauncherPkg = 'de.jellydroid.launcher'
$A11yDienst  = 'de.jellydroid.launcher/de.jellydroid.launcher.HomeButtonService'
$SetupPfad   = '/sdcard/Android/data/de.jellydroid.launcher/files'
$AltLauncherPkg = 'de.elias.launcher'
$AltA11yDienst  = 'de.elias.launcher/de.elias.launcher.HomeButtonService'
New-Item -ItemType Directory -Force -Path $SicherDir | Out-Null

# ------------------------------------------------------------------ Daten

function Read-Tsv([string]$name, [string[]]$cols) {
    $f = Join-Path $DatenDir $name
    $rows = @()
    if (-not (Test-Path $f)) { return $rows }
    foreach ($line in (Get-Content -Path $f -Encoding UTF8)) {
        if (-not $line.Trim() -or $line.TrimStart().StartsWith('#')) { continue }
        $p = $line -split "`t"
        $o = [ordered]@{}
        for ($i = 0; $i -lt $cols.Count; $i++) { $o[$cols[$i]] = if ($i -lt $p.Count) { $p[$i].Trim() } else { '' } }
        $rows += [pscustomobject]$o
    }
    return $rows
}

$Katalog      = Read-Tsv 'apps.tsv' @('paket','name','gruppe','empfohlen','info')
$Dienste      = Read-Tsv 'dienste.tsv' @('key','name','pakete','direkt')
$JellyfinApps = Read-Tsv 'jellyfin-apps.tsv' @('paket','name','direkt')
$Funktionen   = @(Read-Tsv 'funktionen.tsv' @('key','ab','name','seite'))
$Geschuetzt   = @($Katalog | Where-Object { $_.gruppe -eq 'geschuetzt' } | ForEach-Object { $_.paket })
# Grundbestandteile, die auch in der Expertenliste nie auftauchen
$SystemPraefixe = @('android', 'com.android.', 'com.google.android.gms', 'com.google.android.gsf', 'com.google.android.ext.',
    'com.google.android.overlay.', 'com.google.android.modulemetadata', 'com.google.android.permissioncontroller',
    'com.google.android.packageinstaller', 'com.mediatek.', 'org.droidtv.ui.', 'org.droidtv.tv.', 'org.droidtv.common.')
$Orte = @('Deutschland','Österreich','Schweiz','Niederlande','Belgien','Luxemburg','Frankreich','Italien','Spanien','Portugal',
    'Vereinigtes Königreich','Irland','Dänemark','Schweden','Norwegen','Finnland','Polen','Tschechien','Ungarn','Kroatien',
    'Griechenland','Türkei','USA','Kanada')
$Gruppen = [ordered]@{
    werbung    = 'Werbung und Telemetrie (empfohlen)'
    philips    = 'Philips-Extras'
    google     = 'Google-Extras'
    freeview   = 'Freeview (britische Apps)'
    sonstige   = 'Weitere Pakete (nur für Experten)'
    geschuetzt = 'Geschützt (wird nie entfernt)'
}

# Gemerkte Eingaben vom letzten Mal (ohne Passwörter und Tokens)
$Merk = [ordered]@{ ip = ''; port = '5555'; jellyfinUrl = ''; seerrUrl = ''; seerrUser = ''; seerrArt = 'jellyfin';
    dienste = @(); reihen = @(); jellyfinApp = 'com.edde746.plezy'; theme = 'DARK'; ort = 0; dns = 'dns.adguard-dns.com'; airplay = $true; airplayName = 'Jellydroid'; schrift = 'standard'; schriftDateien = @()
    ha = $true; haPopup = $true; haTv = ''; haBell = ''; haDoor = '' }
if (Test-Path $EinstDatei) {
    try {
        $j = Get-Content $EinstDatei -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($k in @($Merk.Keys)) { if ($null -ne $j.$k) { $Merk[$k] = $j.$k } }
    } catch {}
}
function Save-Merk { try { $Merk | ConvertTo-Json | Set-Content -Path $EinstDatei -Encoding UTF8 } catch {} }

# Zustand des Assistenten
$S = @{
    Modus = ''; Geraet = ''; Verbunden = $false; Modell = ''; Pakete = @(); Deaktiviert = @()
    LauncherDa = $false; AltLauncherDa = $false; SicherungOrdner = ''
    JfToken = ''; JfUser = ''; JfUserName = ''; JfDevice = ''; JfServerId = ''
    SeerrCookie = ''; SeerrUserId = -1; SeerrName = ''
    Ausgefuehrt = $false
}

# ------------------------------------------------------------------ ADB

$script:Adb = $null
function Find-Adb {
    $kandidaten = @(
        (Join-Path $Root 'platform-tools\adb.exe'),
        (Join-Path $Root '..\platform-tools\adb.exe')
    )
    foreach ($k in $kandidaten) { if (Test-Path $k) { return (Resolve-Path $k).Path } }
    $c = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    return $null
}

function Ensure-Adb([System.Windows.Forms.Label]$status) {
    if ($script:Adb) { return $true }
    $script:Adb = Find-Adb
    if ($script:Adb) { return $true }
    if ($status) { $status.Text = 'Lade Android Platform-Tools von Google (ca. 7 MB) ...'; [System.Windows.Forms.Application]::DoEvents() }
    try {
        $zip = Join-Path $env:TEMP 'platform-tools-installer.zip'
        $old = $ProgressPreference; $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
        $ProgressPreference = $old
        Expand-Archive $zip -DestinationPath $Root -Force
        Remove-Item $zip -ErrorAction SilentlyContinue
        $script:Adb = Join-Path $Root 'platform-tools\adb.exe'
        return (Test-Path $script:Adb)
    } catch {
        if ($status) { $status.Text = "Download fehlgeschlagen: $($_.Exception.Message)" }
        return $false
    }
}

function Adb([string[]]$a) {
    $out = & $script:Adb @a 2>&1 | ForEach-Object { "$_" }
    return (($out -join "`n").Trim())
}
function TvShell([string]$cmd) { return (Adb @('-s', $S.Geraet, 'shell', $cmd)) }
function Get-Liste([string]$opt) {
    $r = TvShell "pm list packages $opt"
    return @($r -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -like 'package:*' } | ForEach-Object { $_.Substring(8) })
}
function Pause-Ui([int]$ms) {
    $ende = (Get-Date).AddMilliseconds($ms)
    while ((Get-Date) -lt $ende) { [System.Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 40 }
}

# ------------------------------------------------------------------ Oberfläche: Grundgerüst

$Blau  = [System.Drawing.Color]::FromArgb(0, 95, 184)
$Grau  = [System.Drawing.Color]::FromArgb(110, 110, 110)
$Hell  = [System.Drawing.Color]::FromArgb(245, 247, 250)
$Gelb  = [System.Drawing.Color]::FromArgb(255, 243, 205)
$Font  = New-Object System.Drawing.Font('Segoe UI', 10)
$FontB = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
$FontH = New-Object System.Drawing.Font('Segoe UI Semibold', 16)
$FontC = New-Object System.Drawing.Font('Consolas', 9)

$Form = New-Object System.Windows.Forms.Form
$Form.Text = 'Jellydroid Installer'
$Form.Size = New-Object System.Drawing.Size(1040, 720)
$Form.MinimumSize = $Form.Size
$Form.StartPosition = 'CenterScreen'
$Form.Font = $Font
$Form.BackColor = [System.Drawing.Color]::White

$Seite = New-Object System.Windows.Forms.Panel
$Seite.Dock = 'Left'; $Seite.Width = 230; $Seite.BackColor = $Hell
$Seite.Padding = New-Object System.Windows.Forms.Padding(16, 20, 8, 8)
$Form.Controls.Add($Seite)

$Unten = New-Object System.Windows.Forms.Panel
$Unten.Dock = 'Bottom'; $Unten.Height = 56; $Unten.BackColor = $Hell
$Form.Controls.Add($Unten)

$Inhalt = New-Object System.Windows.Forms.Panel
$Inhalt.Dock = 'Fill'; $Inhalt.Padding = New-Object System.Windows.Forms.Padding(24, 18, 24, 8)
$Form.Controls.Add($Inhalt)
$Inhalt.BringToFront()

function New-Btn([string]$t, [int]$x, [int]$w = 110) {
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $t; $b.Width = $w; $b.Height = 32; $b.Left = $x; $b.Top = 12
    $b.Anchor = 'Top,Right'
    return $b
}
$BtnZurueck = New-Btn '< Zurück' 0
$BtnWeiter  = New-Btn 'Weiter >' 0
$BtnMenue   = New-Btn 'Hauptmenü' 0
foreach ($ctl in @($BtnZurueck, $BtnWeiter, $BtnMenue)) { $Unten.Controls.Add($ctl) }
function Place-Buttons {
    $w = $Unten.ClientSize.Width
    $BtnWeiter.Left = $w - 130; $BtnZurueck.Left = $w - 250; $BtnMenue.Left = 16; $BtnMenue.Anchor = 'Top,Left'
}
$Unten.Add_Resize({ Place-Buttons })

function Lbl([string]$t, [int]$x, [int]$y, [int]$w = 700, [int]$h = 0, $font = $null, $color = $null) {
    $l = New-Object System.Windows.Forms.Label
    $l.Text = $t; $l.Left = $x; $l.Top = $y; $l.Width = $w
    if ($h -gt 0) { $l.Height = $h } else { $l.AutoSize = $false; $l.Height = 24 }
    if ($font) { $l.Font = $font }
    if ($color) { $l.ForeColor = $color }
    return $l
}
function Chk([string]$t, [int]$x, [int]$y, [int]$w = 700, [int]$h = 24) {
    $c = New-Object System.Windows.Forms.CheckBox
    $c.Text = $t; $c.Left = $x; $c.Top = $y; $c.Width = $w; $c.Height = $h
    return $c
}
function Txt([string]$t, [int]$x, [int]$y, [int]$w = 300) {
    $b = New-Object System.Windows.Forms.TextBox
    $b.Text = $t; $b.Left = $x; $b.Top = $y; $b.Width = $w
    return $b
}
function Btn([string]$t, [int]$x, [int]$y, [int]$w = 160) {
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $t; $b.Left = $x; $b.Top = $y; $b.Width = $w; $b.Height = 30
    return $b
}
function New-Page([string]$titel, [string]$untertitel) {
    $p = New-Object System.Windows.Forms.Panel
    $p.Dock = 'Fill'; $p.AutoScroll = $true; $p.Visible = $false
    $p.Controls.Add((Lbl $titel 0 0 740 34 $FontH))
    if ($untertitel) { $p.Controls.Add((Lbl $untertitel 0 38 740 44 $null $Grau)) }
    $Inhalt.Controls.Add($p)
    return $p
}

$Pages = @{}
$Ablauf = @()
$script:Pos = -1
$Schritte = [ordered]@{
    vorbereitung = 'Fernseher vorbereiten'; verbindung = 'Verbinden'; sicherung = 'Sicherung'
    bloatware = 'Apps entfernen'; launcher = 'Launcher'; airplay = 'AirPlay'; schrift = 'Schrift'; dienste = 'Streamingdienste'; konten = 'Jellyfin und Seerr'; homeassistant = 'Home Assistant'
    update = 'Update'; sidebar = 'Sidebar-Taste'
    tipps = 'Tipps'; wiederherstellen = 'Wiederherstellen'; ausfuehren = 'Ausführen'
}
$Modi = @{
    voll       = @('vorbereitung','verbindung','sicherung','bloatware','launcher','airplay','schrift','dienste','konten','homeassistant','tipps','ausfuehren')
    update     = @('verbindung','update','ausfuehren')   # wird auf der Update-Seite um die Seiten der neuen Funktionen ergänzt
    erneut     = @('verbindung','bloatware','ausfuehren')
    sicherung  = @('verbindung','sicherung','ausfuehren')
    restore    = @('verbindung','wiederherstellen','ausfuehren')
    launcher   = @('verbindung','launcher','airplay','schrift','sidebar','dienste','konten','homeassistant','ausfuehren')
    tipps      = @('tipps')
}

function Show-Sidebar {
    $Seite.Controls.Clear()
    $Seite.Controls.Add((Lbl 'Jellydroid' 16 18 200 30 $FontH $Blau))
    if ($script:Pos -lt 0) { return }
    $y = 70
    for ($i = 0; $i -lt $Ablauf.Count; $i++) {
        $n = $Ablauf[$i]
        $prefix = if ($i -lt $script:Pos) { '✓ ' } elseif ($i -eq $script:Pos) { '› ' } else { '   ' }
        $f = if ($i -eq $script:Pos) { $FontB } else { $Font }
        $c = if ($i -le $script:Pos) { [System.Drawing.Color]::Black } else { $Grau }
        $Seite.Controls.Add((Lbl ($prefix + $Schritte[$n]) 16 $y 200 26 $f $c))
        $y += 30
    }
}

function Show-Page([string]$name) {
    foreach ($p in $Pages.Values) { $p.Panel.Visible = $false }
    $pg = $Pages[$name]
    $pg.Panel.Visible = $true
    if ($pg.Enter) { & $pg.Enter }
    Show-Sidebar
    $BtnZurueck.Visible = ($script:Pos -gt 0) -and -not $S.Ausgefuehrt
    $BtnMenue.Visible = ($script:Pos -ge 0)
    $BtnWeiter.Visible = ($script:Pos -ge 0) -and ($script:Pos -lt $Ablauf.Count - 1)
    Place-Buttons
}

function Start-Modus([string]$m) {
    $S.Modus = $m; $S.Ausgefuehrt = $false
    $script:Ablauf = $Modi[$m]; $script:Pos = 0
    Show-Page $Ablauf[0]
}

$BtnWeiter.Add_Click({
    $pg = $Pages[$Ablauf[$script:Pos]]
    if ($pg.Leave) { if (-not (& $pg.Leave)) { return } }
    $script:Pos++
    Show-Page $Ablauf[$script:Pos]
})
$BtnZurueck.Add_Click({ if ($script:Pos -gt 0) { $script:Pos--; Show-Page $Ablauf[$script:Pos] } })
$BtnMenue.Add_Click({ $script:Pos = -1; Show-Page 'menue' })

function Msg([string]$t, [string]$icon = 'Information') {
    [void][System.Windows.Forms.MessageBox]::Show($Form, $t, 'Jellydroid', 'OK', $icon)
}
function Frage([string]$t) {
    return ([System.Windows.Forms.MessageBox]::Show($Form, $t, 'Jellydroid', 'YesNo', 'Question') -eq 'Yes')
}

# ------------------------------------------------------------------ Seite: Hauptmenü

$p = New-Page 'Was möchtest du tun?' ''
$menue = @(
    @('voll',      'Geführte Einrichtung (empfohlen)', 'Alles in einem: Sicherung, Apps entfernen, Launcher installieren und einrichten.'),
    @('update',    'Launcher aktualisieren',            'Neue APK installieren, Einstellungen bleiben. Nur neue Funktionen werden abgefragt.'),
    @('erneut',    'Auswahl nach Neustart erneut anwenden',          'Nach einem echten Neustart kommen vorinstallierte Apps zurück. Hiermit entfernst du sie wieder.'),
    @('sicherung', 'Nur Sicherung erstellen',           'Liest Apps und Einstellungen des Fernsehers aus und speichert sie auf diesem PC.'),
    @('restore',   'Wiederherstellen',                  'Holt entfernte Apps zurück, setzt Einstellungen und Startbildschirm zurück.'),
    @('launcher',  'Launcher installieren oder neu einrichten', 'Dienste, Jellyfin, Seerr und Design ändern, ohne Apps anzufassen.'),
    @('tipps',     'Tipps lesen',                       'Was du über Plezy, Streamingdienste und Neustarts wissen solltest.')
)
$y = 100
foreach ($m in $menue) {
    $b = Btn $m[1] 0 $y 400
    $b.Height = 44; $b.Font = $FontB; $b.TextAlign = 'MiddleLeft'; $b.Tag = $m[0]
    $b.Add_Click({ Start-Modus $this.Tag })
    $p.Controls.Add($b)
    $y += 58
}
$Pages['menue'] = @{ Panel = $p; Enter = $null; Leave = $null }

# ------------------------------------------------------------------ Seite: Vorbereitung

$p = New-Page 'Fernseher vorbereiten' ''
$vorb = @(
    'Fernseher und dieser PC sind im selben Heimnetz (WLAN oder Kabel).',
    'Entwickleroptionen sind aktiv: Einstellungen > Geräteeinstellungen > Info > 7-mal auf "Build" drücken, bis "Du bist jetzt Entwickler" erscheint. Bei Philips: Einstellungen > Alle Einstellungen > Android-Einstellungen > Geräteeinstellungen > Info.',
    'USB-Debugging ist eingeschaltet: Geräteeinstellungen > Entwickleroptionen > USB-Debugging. Gibt es dort "Netzwerk-Debugging" oder "ADB über Netzwerk", das ebenfalls einschalten.',
    'Die IP-Adresse des Fernsehers ist bekannt: Einstellungen > Netzwerk und Internet > dein Netzwerk > IP-Adresse (z. B. 192.168.1.50). Du kannst sie auf der nächsten Seite auch suchen lassen.',
    'Die Fernbedienung liegt bereit: Beim ersten Verbinden fragt der Fernseher "USB-Debugging zulassen?". Dann "Immer von diesem Computer zulassen" anhaken und OK drücken.'
)
$script:VorbChecks = @()
$y = 96
foreach ($t in $vorb) {
    $c = Chk $t 0 $y 740 52
    $c.CheckAlign = 'TopLeft'; $c.TextAlign = 'TopLeft'
    $c.Add_CheckedChanged({ $BtnWeiter.Enabled = (@($script:VorbChecks | Where-Object { -not $_.Checked }).Count -eq 0) })
    $script:VorbChecks += $c; $p.Controls.Add($c)
    $y += 64
}
$Pages['vorbereitung'] = @{ Panel = $p
    Enter = { $BtnWeiter.Enabled = (@($script:VorbChecks | Where-Object { -not $_.Checked }).Count -eq 0) }
    Leave = { $true } }

# ------------------------------------------------------------------ Seite: Verbindung

$p = New-Page 'Mit dem Fernseher verbinden' ''
$p.Controls.Add((Lbl 'IP-Adresse:' 0 100 120))
$TxtIp = New-Object System.Windows.Forms.ComboBox
$TxtIp.Left = 120; $TxtIp.Top = 97; $TxtIp.Width = 220; $TxtIp.Text = $Merk.ip
$p.Controls.Add($TxtIp)
$p.Controls.Add((Lbl 'Port:' 360 100 50))
$TxtPort = Txt $Merk.port 410 97 70; $p.Controls.Add($TxtPort)
$BtnSuchen = Btn 'Im Netzwerk suchen' 500 95 170; $p.Controls.Add($BtnSuchen)
$BtnVerbinden = Btn 'Verbinden' 120 140 220; $BtnVerbinden.Font = $FontB; $p.Controls.Add($BtnVerbinden)
$LblVerb = Lbl '' 0 190 740 60; $p.Controls.Add($LblVerb)
$LblGeraet = Lbl '' 0 255 740 60 $FontB; $p.Controls.Add($LblGeraet)

# Sucht im eigenen Netz (/24 jeder aktiven Netzwerkkarte) alle Geräte mit offenem TCP-Port
function Find-OffenerPort([int]$port) {
    $praefixe = @()
    foreach ($ni in [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
        if ($ni.OperationalStatus -ne 'Up') { continue }
        foreach ($ua in $ni.GetIPProperties().UnicastAddresses) {
            $a = $ua.Address.ToString()
            if ($ua.Address.AddressFamily -eq 'InterNetwork' -and $a -notlike '127.*' -and $a -notlike '169.254.*') {
                $praefixe += ($a -replace '\.\d+$', '')
            }
        }
    }
    $treffer = @()
    foreach ($pre in ($praefixe | Select-Object -Unique)) {
        $tests = @()
        foreach ($i in 1..254) {
            $c = New-Object System.Net.Sockets.TcpClient
            $tests += ,@("$pre.$i", $c, $c.ConnectAsync("$pre.$i", $port))
        }
        Pause-Ui 1500
        foreach ($t in $tests) {
            if ($t[2].Status -eq 'RanToCompletion' -and $t[1].Connected) { $treffer += $t[0] }
            try { $t[1].Close() } catch {}
        }
    }
    return $treffer
}

$BtnSuchen.Add_Click({
    $port = [int]($TxtPort.Text.Trim())
    $LblVerb.Text = 'Suche Geräte mit offenem ADB-Port ...'; [System.Windows.Forms.Application]::DoEvents()
    $treffer = @(Find-OffenerPort $port)
    $TxtIp.Items.Clear()
    foreach ($t in $treffer) { [void]$TxtIp.Items.Add($t) }
    if ($treffer.Count -gt 0) { $TxtIp.Text = $treffer[0]; $LblVerb.Text = "Gefunden: $($treffer -join ', '). Wähle den Fernseher aus und klicke Verbinden." }
    else { $LblVerb.Text = 'Nichts gefunden. Ist USB-/Netzwerk-Debugging am Fernseher an? Sonst die IP von Hand eingeben.' }
})

$BtnVerbinden.Add_Click({
    $ip = $TxtIp.Text.Trim(); $port = $TxtPort.Text.Trim()
    if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { Msg 'Bitte eine gültige IP-Adresse eingeben, z. B. 192.168.1.50.' 'Warning'; return }
    $BtnVerbinden.Enabled = $false
    try {
        if (-not (Ensure-Adb $LblVerb)) { return }
        $S.Geraet = "$ip`:$port"; $S.Verbunden = $false
        $LblVerb.Text = "Verbinde mit $($S.Geraet) ..."; [System.Windows.Forms.Application]::DoEvents()
        [void](Adb @('connect', $S.Geraet))
        $ende = (Get-Date).AddSeconds(90); $hinweis = $false
        while ((Get-Date) -lt $ende) {
            $zeile = (Adb @('devices')) -split "`n" | Where-Object { $_ -like "$($S.Geraet)*" } | Select-Object -First 1
            if ($zeile -match "`tdevice$|\sdevice$") { $S.Verbunden = $true; break }
            if ($zeile -match 'unauthorized') {
                if (-not $hinweis) { $LblVerb.Text = 'Bitte am Fernseher "Immer von diesem Computer zulassen" anhaken und OK drücken ...'; $hinweis = $true }
            } elseif (-not $zeile -or $zeile -match 'offline') {
                [void](Adb @('connect', $S.Geraet))
            }
            Pause-Ui 1500
        }
        if (-not $S.Verbunden) {
            $LblVerb.Text = 'Keine Verbindung. Prüfe IP, USB-Debugging und ob du am Fernseher OK gedrückt hast, dann erneut "Verbinden".'
            return
        }
        $hersteller = TvShell 'getprop ro.product.manufacturer'
        $S.Modell = TvShell 'getprop ro.product.model'
        $android = TvShell 'getprop ro.build.version.release'
        $S.Pakete = Get-Liste '--user 0'
        $S.Deaktiviert = Get-Liste '-d'
        $S.LauncherDa = ($S.Pakete -contains $LauncherPkg)
        $S.AltLauncherDa = ($S.Pakete -contains $AltLauncherPkg)
        $LblVerb.Text = 'Verbunden.'
        $LblGeraet.Text = "$hersteller $($S.Modell), Android $android`n$($S.Pakete.Count) Apps installiert" + $(if ($S.LauncherDa) { ', eigener Launcher ist installiert' } elseif ($S.AltLauncherDa) { ', ältere Jellydroid-Version ist installiert (wird beim Einrichten ersetzt)' } else { '' })
        $Merk.ip = $ip; $Merk.port = $port; Save-Merk
    } finally { $BtnVerbinden.Enabled = $true }
})

$Pages['verbindung'] = @{ Panel = $p
    Enter = { $BtnWeiter.Enabled = $true }
    Leave = {
        if (-not $S.Verbunden) { Msg 'Bitte zuerst mit dem Fernseher verbinden.' 'Warning'; return $false }
        return $true
    } }

# ------------------------------------------------------------------ Seite: Sicherung

$p = New-Page 'Sicherung' ''
$p.Controls.Add((Lbl 'Gesichert wird:' 0 50 740 24 $FontB))
$p.Controls.Add((Lbl ("• Liste aller installierten, deaktivierten und System-Apps`n" +
    "• Einstellungen: Privates DNS, Animationen, Bedienungshilfen`n" +
    "• Welcher Startbildschirm aktiv ist`n" +
    "• Modell und Firmware-Stand") 0 76 740 90))
$ChkApk = Chk 'Play-Store-Apps zusätzlich als APK sichern' 0 175 740
$p.Controls.Add($ChkApk)
$LblSicherOrt = Lbl '' 0 215 740 44 $null $Grau; $p.Controls.Add($LblSicherOrt)
$Pages['sicherung'] = @{ Panel = $p
    Enter = { $BtnWeiter.Enabled = $true; $LblSicherOrt.Text = "Speicherort: $SicherDir" }
    Leave = { $true } }

# ------------------------------------------------------------------ Seite: Bloatware

$p = New-Page 'Vorinstallierte Apps entfernen' ''
$warnLbl = Lbl ('Auf eigene Gefahr: Das Entfernen vorinstallierter Apps kann Funktionen des Fernsehers beeinträchtigen. ' +
    'Über "Wiederherstellen" kommt alles zurück.') 0 40 740 46
$warnLbl.BackColor = $Gelb; $warnLbl.Padding = New-Object System.Windows.Forms.Padding(6)
$p.Controls.Add($warnLbl)
$LvApps = New-Object System.Windows.Forms.ListView
$LvApps.Left = 0; $LvApps.Top = 94; $LvApps.Width = 740; $LvApps.Height = 268
$LvApps.View = 'Details'; $LvApps.CheckBoxes = $true; $LvApps.FullRowSelect = $true; $LvApps.ShowGroups = $true
[void]$LvApps.Columns.Add('App', 220); [void]$LvApps.Columns.Add('Paket', 230); [void]$LvApps.Columns.Add('Hinweis', 270)
$p.Controls.Add($LvApps)
$BtnEmpf = Btn 'Empfohlene auswählen' 0 368 180; $p.Controls.Add($BtnEmpf)
$BtnKeine = Btn 'Keine' 190 368 80; $p.Controls.Add($BtnKeine)
$ChkExperte = Chk 'Weitere Pakete anzeigen (Experten)' 290 372 300; $p.Controls.Add($ChkExperte)
$RbUninst = New-Object System.Windows.Forms.RadioButton
$RbUninst.Text = 'Für meinen Benutzer entfernen (empfohlen)'; $RbUninst.Left = 0; $RbUninst.Top = 406; $RbUninst.Width = 360; $RbUninst.Checked = $true
$RbDisable = New-Object System.Windows.Forms.RadioButton
$RbDisable.Text = 'Nur deaktivieren'; $RbDisable.Left = 370; $RbDisable.Top = 406; $RbDisable.Width = 200
foreach ($ctl in @($RbUninst, $RbDisable)) { $p.Controls.Add($ctl) }
$ChkDns = Chk 'Werbefilter: Privates DNS setzen auf' 0 438 300; $p.Controls.Add($ChkDns)
$TxtDns = Txt $Merk.dns 300 436 220; $p.Controls.Add($TxtDns)
$ChkAnim = Chk 'Animationen verkürzen (halbe Dauer, Fernseher wirkt flotter)' 0 466 500; $p.Controls.Add($ChkAnim)
$ChkGefahr = Chk 'Ich habe den Hinweis gelesen und entferne Apps auf eigene Gefahr.' 0 500 740; $ChkGefahr.Font = $FontB
$p.Controls.Add($ChkGefahr)

$LvApps.Add_ItemCheck({
    param($lvSender, $e)
    $it = $LvApps.Items[$e.Index]
    if ($it.Tag -and $it.Tag.gruppe -eq 'geschuetzt') { $e.NewValue = 'Unchecked' }
})
function Fill-Apps([string[]]$vorauswahl) {
    $LvApps.BeginUpdate(); $LvApps.Items.Clear(); $LvApps.Groups.Clear()
    $grp = @{}
    foreach ($k in $Gruppen.Keys) { $g = New-Object System.Windows.Forms.ListViewGroup($k, $Gruppen[$k]); [void]$LvApps.Groups.Add($g); $grp[$k] = $g }
    $imKatalog = @{}
    foreach ($a in $Katalog) {
        $imKatalog[$a.paket] = $true
        if ($S.Pakete -notcontains $a.paket) { continue }
        $it = New-Object System.Windows.Forms.ListViewItem($a.name)
        [void]$it.SubItems.Add($a.paket); [void]$it.SubItems.Add($a.info)
        $it.Group = $grp[$a.gruppe]; $it.Tag = $a
        if ($a.gruppe -eq 'geschuetzt') { $it.ForeColor = $Grau }
        elseif ($a.info -like 'RISKANT*') { $it.ForeColor = [System.Drawing.Color]::DarkRed }
        [void]$LvApps.Items.Add($it)
        if ($vorauswahl -contains $a.paket -and $a.gruppe -ne 'geschuetzt') { $it.Checked = $true }
    }
    if ($ChkExperte.Checked) {
        foreach ($pk in ($S.Pakete | Sort-Object)) {
            if ($imKatalog.ContainsKey($pk)) { continue }
            $sys = $false; foreach ($pre in $SystemPraefixe) { if ($pk -eq $pre -or $pk.StartsWith($pre)) { $sys = $true; break } }
            if ($sys) { continue }
            $o = [pscustomobject]@{ paket = $pk; name = $pk; gruppe = 'sonstige'; empfohlen = 'nein'; info = 'Unbekannt: nur wenn du sicher bist' }
            $it = New-Object System.Windows.Forms.ListViewItem($pk)
            [void]$it.SubItems.Add($pk); [void]$it.SubItems.Add($o.info)
            $it.Group = $grp['sonstige']; $it.Tag = $o
            [void]$LvApps.Items.Add($it)
            if ($vorauswahl -contains $pk) { $it.Checked = $true }
        }
    }
    $LvApps.EndUpdate()
}
function Get-AppAuswahl { return @($LvApps.CheckedItems | ForEach-Object { $_.Tag.paket } | Where-Object { $Geschuetzt -notcontains $_ }) }
$BtnEmpf.Add_Click({ foreach ($it in $LvApps.Items) { $it.Checked = ($it.Tag.empfohlen -eq 'ja') } })
$BtnKeine.Add_Click({ foreach ($it in $LvApps.Items) { $it.Checked = $false } })
$ChkExperte.Add_CheckedChanged({ Fill-Apps (Get-AppAuswahl) })
$script:AppsGeladen = $false

$Pages['bloatware'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        if (-not $script:AppsGeladen) {
            $vor = @()
            $letzte = Join-Path $SicherDir 'letzte-auswahl.txt'
            if ($S.Modus -eq 'erneut' -and (Test-Path $letzte)) {
                $vor = @(Get-Content $letzte -Encoding UTF8 | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object { ($_ -split '\|')[0] })
                if ((Get-Content $letzte -Encoding UTF8) -match '\|disable') { $RbDisable.Checked = $true }
            } else {
                $vor = @($Katalog | Where-Object { $_.empfohlen -eq 'ja' } | ForEach-Object { $_.paket })
            }
            # Pakete aus der letzten Auswahl, die nicht im Katalog stehen, in der Expertenliste zeigen
            $katPakete = @($Katalog | ForEach-Object { $_.paket })
            $fremd = @($vor | Where-Object { $katPakete -notcontains $_ -and $S.Pakete -contains $_ })
            if ($fremd.Count -gt 0) { $ChkExperte.Checked = $true }
            Fill-Apps $vor
            $ChkDns.Checked = ($S.Modus -eq 'voll'); $ChkAnim.Checked = ($S.Modus -eq 'voll')
            $script:AppsGeladen = $true
        }
    }
    Leave = {
        $sel = Get-AppAuswahl
        if ($sel.Count -gt 0 -and -not $ChkGefahr.Checked) { Msg 'Bitte bestätige den Hinweis "auf eigene Gefahr" oder wähle keine App aus.' 'Warning'; return $false }
        if ($ChkDns.Checked -and $TxtDns.Text.Trim() -notmatch '^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$') { Msg 'Bitte einen gültigen DNS-Hostnamen eingeben, z. B. dns.adguard-dns.com.' 'Warning'; return $false }
        $Merk.dns = $TxtDns.Text.Trim(); Save-Merk
        return $true
    } }

# ------------------------------------------------------------------ Seite: Launcher

$p = New-Page 'Eigener Launcher' ''
$ChkLauncher = Chk 'Launcher installieren oder aktualisieren' 0 96 400; $ChkLauncher.Font = $FontB; $p.Controls.Add($ChkLauncher)
$p.Controls.Add((Lbl 'APK-Datei:' 20 130 90))
$TxtApk = Txt '' 110 127 480; $p.Controls.Add($TxtApk)
$BtnApk = Btn 'Durchsuchen ...' 600 125 130; $p.Controls.Add($BtnApk)
$ChkHome = Chk 'Home-Taste auf den Launcher legen' 20 158 720; $ChkHome.Checked = $true
$p.Controls.Add($ChkHome)
$p.Controls.Add((Lbl 'Profilbild:' 0 196 120 24 $FontB))
$TxtAvatar = Txt '' 120 193 330; $p.Controls.Add($TxtAvatar)
$BtnAvatar = Btn 'Bild wählen ...' 460 191 130; $p.Controls.Add($BtnAvatar)
$BtnAvatarWeg = Btn 'Leeren' 600 191 80; $p.Controls.Add($BtnAvatarWeg)
$BtnAvatar.Add_Click({
    $d = New-Object System.Windows.Forms.OpenFileDialog
    $d.Filter = 'Bilder (*.jpg;*.jpeg;*.png;*.webp)|*.jpg;*.jpeg;*.png;*.webp'
    if ($d.ShowDialog($Form) -eq 'OK') { $TxtAvatar.Text = $d.FileName }
})
$BtnAvatarWeg.Add_Click({ $TxtAvatar.Text = '' })
$p.Controls.Add((Lbl 'Design:' 0 236 120 24 $FontB))
$CbTheme = New-Object System.Windows.Forms.ComboBox
$CbTheme.DropDownStyle = 'DropDownList'; $CbTheme.Left = 120; $CbTheme.Top = 233; $CbTheme.Width = 200
[void]$CbTheme.Items.AddRange(@('Dunkel', 'Hell', 'Dynamisch (nach Sonnenstand)'))
$p.Controls.Add($CbTheme)
$p.Controls.Add((Lbl 'Ort:' 340 236 40))
$CbOrt = New-Object System.Windows.Forms.ComboBox
$CbOrt.DropDownStyle = 'DropDownList'; $CbOrt.Left = 380; $CbOrt.Top = 233; $CbOrt.Width = 210
[void]$CbOrt.Items.AddRange($Orte); $p.Controls.Add($CbOrt)
$CbTheme.Add_SelectedIndexChanged({ $CbOrt.Enabled = ($CbTheme.SelectedIndex -eq 2) })
$p.Controls.Add((Lbl 'Jellyfin-App:' 0 276 120 24 $FontB))
$CbJfApp = New-Object System.Windows.Forms.ComboBox
$CbJfApp.DropDownStyle = 'DropDownList'; $CbJfApp.Left = 120; $CbJfApp.Top = 273; $CbJfApp.Width = 470
$p.Controls.Add($CbJfApp)
$BtnPlezy = Btn 'Plezy im Play Store öffnen' 120 306 260; $p.Controls.Add($BtnPlezy)
$tipp = Lbl ('Tipp: Nimm Plezy. Nur Plezy öffnet einen Film oder eine Serie direkt beim Titel, wenn du ihn im Launcher auswählst. ' +
    'Jellyfin, Reefin, Findroid und Wholphin starten nur die App. Plezy hat außerdem Seerr eingebaut.') 0 346 740 64
$tipp.BackColor = [System.Drawing.Color]::FromArgb(230, 242, 255); $tipp.Padding = New-Object System.Windows.Forms.Padding(6)
$p.Controls.Add($tipp)

function Find-Apk {
    # Jellydroid-V<Version>.apk neben dem Installer oder in ..\launcher\ausgabe; die höchste Version gewinnt.
    # Ohne Versionsdatei: launcher.apk neben dem Installer.
    $ordner = @($Root, (Join-Path $Root '..\launcher\ausgabe')) | Where-Object { Test-Path $_ }
    $best = $null; $bestVer = $null
    foreach ($o in $ordner) {
        foreach ($f in (Get-ChildItem $o -Filter 'Jellydroid-V*.apk' -ErrorAction SilentlyContinue)) {
            if ($f.Name -match '^Jellydroid-V(\d+(\.\d+){0,3})\.apk$') {
                $v = [version]($(if ($Matches[1] -notmatch '\.') { $Matches[1] + '.0' } else { $Matches[1] }))
                if (-not $bestVer -or $v -gt $bestVer) { $best = $f.FullName; $bestVer = $v }
            }
        }
    }
    if ($best) { return $best }
    $l = Join-Path $Root 'launcher.apk'
    if (Test-Path $l) { return (Resolve-Path $l).Path }
    return ''
}
$BtnApk.Add_Click({
    $d = New-Object System.Windows.Forms.OpenFileDialog
    $d.Filter = 'Android-App (*.apk)|*.apk'
    if ($d.ShowDialog($Form) -eq 'OK') { $TxtApk.Text = $d.FileName; $ChkLauncher.Checked = $true }
})
$BtnPlezy.Add_Click({
    [void](TvShell 'am start -a android.intent.action.VIEW -d market://details?id=com.edde746.plezy')
    Msg 'Der Play Store sollte jetzt am Fernseher Plezy zeigen. Nach der Installation hier weitermachen.'
})
function Fill-JfApps {
    $CbJfApp.Items.Clear(); $script:JfAppListe = @()
    foreach ($a in $JellyfinApps) {
        $da = if ($S.Pakete -contains $a.paket) { 'installiert' } else { 'nicht installiert' }
        $dir = if ($a.direkt -eq 'ja') { 'öffnet Titel direkt, empfohlen' } else { 'startet nur die App' }
        [void]$CbJfApp.Items.Add("$($a.name)  ($dir, $da)")
        $script:JfAppListe += $a.paket
    }
    $i = [array]::IndexOf($script:JfAppListe, [string]$Merk.jellyfinApp); if ($i -lt 0) { $i = 0 }
    $CbJfApp.SelectedIndex = $i
    $BtnPlezy.Visible = ($S.Pakete -notcontains 'com.edde746.plezy')
}
$Pages['launcher'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        if (-not $TxtApk.Text) { $TxtApk.Text = Find-Apk }
        if ($S.Modus -eq 'voll') { $ChkLauncher.Checked = $true } elseif (-not $S.LauncherDa) { $ChkLauncher.Checked = $true }
        $CbTheme.SelectedIndex = [Math]::Max(0, [array]::IndexOf(@('DARK','LIGHT','AUTO'), [string]$Merk.theme))
        $CbOrt.SelectedIndex = [Math]::Min([int]$Merk.ort, $Orte.Count - 1); $CbOrt.Enabled = ($CbTheme.SelectedIndex -eq 2)
        Fill-JfApps
    }
    Leave = {
        if ($TxtAvatar.Text -and -not (Test-Path $TxtAvatar.Text)) { Msg 'Das Profilbild wurde nicht gefunden.' 'Warning'; return $false }
        if ($ChkLauncher.Checked -and -not (Test-Path $TxtApk.Text)) { Msg 'Die APK-Datei des Launchers wurde nicht gefunden. Bitte über "Durchsuchen" auswählen.' 'Warning'; return $false }
        if (-not $ChkLauncher.Checked -and -not $S.LauncherDa) {
            if (-not (Frage 'Der Launcher ist nicht installiert und soll auch nicht installiert werden. Die Einstellungen der nächsten Seiten werden dann nicht übertragen. Trotzdem weiter?')) { return $false }
        }
        $Merk.theme = @('DARK','LIGHT','AUTO')[$CbTheme.SelectedIndex]; $Merk.ort = $CbOrt.SelectedIndex
        $Merk.jellyfinApp = $script:JfAppListe[$CbJfApp.SelectedIndex]; Save-Merk
        return $true
    } }

# ------------------------------------------------------------------ Seite: AirPlay

$p = New-Page 'AirPlay' ''
$ChkAirPlay = Chk 'Bildschirmsynchronisierung vom iPhone, iPad oder Mac' 0 56 600; $p.Controls.Add($ChkAirPlay)
$p.Controls.Add((Lbl 'Name:' 20 94 80))
$TxtAirPlay = Txt '' 100 91 300; $p.Controls.Add($TxtAirPlay)
$ChkAirPlay.Add_CheckedChanged({ $TxtAirPlay.Enabled = $ChkAirPlay.Checked })
$Pages['airplay'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        $ChkAirPlay.Checked = [bool]$Merk.airplay; $TxtAirPlay.Text = [string]$Merk.airplayName; $TxtAirPlay.Enabled = $ChkAirPlay.Checked
    }
    Leave = {
        $Merk.airplay = $ChkAirPlay.Checked; $Merk.airplayName = $(if ($TxtAirPlay.Text.Trim()) { $TxtAirPlay.Text.Trim() } else { 'Jellydroid' }); Save-Merk
        return $true
    } }

# ------------------------------------------------------------------ Seite: Schrift

# Standard ist Inter (steckt im Launcher). Eigene Schriften (z. B. Aptos oder SF Pro) darf der Launcher nicht
# mitbringen; man lädt sie selbst herunter und wählt hier die Dateien einer Familie (mehrere Stärken möglich).
$p = New-Page 'Schrift' ''
$RbSchriftStd = New-Object System.Windows.Forms.RadioButton
$RbSchriftStd.Text = 'Inter (Standard)'; $RbSchriftStd.Left = 0; $RbSchriftStd.Top = 56; $RbSchriftStd.Width = 400
$RbSchriftEigen = New-Object System.Windows.Forms.RadioButton
$RbSchriftEigen.Text = 'Eigene Schrift (TTF oder OTF, auch mehrere Stärken einer Familie)'; $RbSchriftEigen.Left = 0; $RbSchriftEigen.Top = 86; $RbSchriftEigen.Width = 600
foreach ($ctl in @($RbSchriftStd, $RbSchriftEigen)) { $p.Controls.Add($ctl) }
$LbSchrift = New-Object System.Windows.Forms.ListBox
$LbSchrift.Left = 20; $LbSchrift.Top = 120; $LbSchrift.Width = 560; $LbSchrift.Height = 160
$p.Controls.Add($LbSchrift)
$BtnSchrift = Btn 'Dateien wählen ...' 590 120 150; $p.Controls.Add($BtnSchrift)
$BtnSchriftWeg = Btn 'Leeren' 590 156 150; $p.Controls.Add($BtnSchriftWeg)
function Set-SchriftUi { $LbSchrift.Enabled = $RbSchriftEigen.Checked; $BtnSchrift.Enabled = $RbSchriftEigen.Checked; $BtnSchriftWeg.Enabled = $RbSchriftEigen.Checked }
$RbSchriftEigen.Add_CheckedChanged({ Set-SchriftUi })
$BtnSchrift.Add_Click({
    $d = New-Object System.Windows.Forms.OpenFileDialog
    $d.Filter = 'Schriften (*.ttf;*.otf)|*.ttf;*.otf'; $d.Multiselect = $true
    if ($d.ShowDialog($Form) -eq 'OK') { $LbSchrift.Items.Clear(); foreach ($f in $d.FileNames) { [void]$LbSchrift.Items.Add($f) } }
})
$BtnSchriftWeg.Add_Click({ $LbSchrift.Items.Clear() })
$Pages['schrift'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        $LbSchrift.Items.Clear(); foreach ($f in @($Merk.schriftDateien)) { if ($f) { [void]$LbSchrift.Items.Add([string]$f) } }
        if ($Merk.schrift -eq 'eigene') { $RbSchriftEigen.Checked = $true } else { $RbSchriftStd.Checked = $true }
        Set-SchriftUi
    }
    Leave = {
        $dateien = @($LbSchrift.Items | ForEach-Object { [string]$_ })
        if ($RbSchriftEigen.Checked) {
            if ($dateien.Count -eq 0) { Msg 'Bitte mindestens eine Schriftdatei auswählen oder Inter nehmen.' 'Warning'; return $false }
            $fehlt = @($dateien | Where-Object { -not (Test-Path $_) })
            if ($fehlt.Count -gt 0) { Msg "Nicht gefunden: $($fehlt -join ', ')" 'Warning'; return $false }
        }
        $Merk.schrift = $(if ($RbSchriftEigen.Checked) { 'eigene' } else { 'standard' }); $Merk.schriftDateien = $dateien; Save-Merk
        return $true
    } }

# ------------------------------------------------------------------ Seite: Sidebar-Taste

# Der Launcher protokolliert jede Taste ("Taste code=<n> (KEYCODE_…) action=<0|1>", Tag JellydroidHome).
# Der Installer liest das mit, während die Taste gedrückt wird, und schickt den Code als config.sidebar_key.
$p = New-Page 'Sidebar-Taste' ''
$p.Controls.Add((Lbl 'Taste:' 0 60 120 24 $FontB))
$LblSbKey = Lbl '' 120 60 600; $p.Controls.Add($LblSbKey)
$BtnSbLernen = Btn 'Taste anlernen' 120 96 180; $p.Controls.Add($BtnSbLernen)
$BtnSbStandard = Btn 'Standard' 310 96 120; $p.Controls.Add($BtnSbStandard)
$LblSb = Lbl '' 120 140 620 60; $p.Controls.Add($LblSb)
$S.SbKey = $null; $S.SbName = ''
$NavKeys = @(3, 4, 19, 20, 21, 22, 23, 66, 160)
function Show-SbKey {
    $LblSbKey.Text = if ($null -eq $S.SbKey) { 'unverändert' } elseif ($S.SbKey -le 0) { 'Menü- oder Einstellungstaste (Standard)' } else { "$($S.SbName) (Code $($S.SbKey))" }
}
$BtnSbLernen.Add_Click({
    $BtnSbLernen.Enabled = $false; $BtnSbStandard.Enabled = $false
    try {
        [void](TvShell 'logcat -c')
        $LblSb.Text = 'Jetzt die gewünschte Taste auf der Fernbedienung drücken …'
        $ende = (Get-Date).AddSeconds(20); $gefunden = $false
        while ((Get-Date) -lt $ende -and -not $gefunden) {
            Pause-Ui 700
            $log = TvShell 'logcat -d -s JellydroidHome:I'
            foreach ($m in [regex]::Matches($log, 'Taste code=(\d+) \((\w+)\) action=1')) {
                $c = [int]$m.Groups[1].Value
                if ($NavKeys -notcontains $c) { $S.SbKey = $c; $S.SbName = $m.Groups[2].Value -replace '^KEYCODE_', ''; $gefunden = $true }
            }
        }
        if ($gefunden) { $LblSb.Text = 'Angelernt. Wird beim Ausführen an den Launcher übertragen.' }
        else { $LblSb.Text = 'Keine Taste erkannt. Läuft die Bedienungshilfe des Launchers? Pfeiltasten, OK, Zurück und Home gehen nicht.' }
        Show-SbKey
    } finally { $BtnSbLernen.Enabled = $S.LauncherDa; $BtnSbStandard.Enabled = $true }
})
$BtnSbStandard.Add_Click({ $S.SbKey = -1; $S.SbName = ''; $LblSb.Text = ''; Show-SbKey })
$Pages['sidebar'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        $BtnSbLernen.Enabled = $S.LauncherDa
        if (-not $S.LauncherDa) { $LblSb.Text = 'Anlernen geht, sobald der Launcher installiert ist. Bis dahin öffnen Menü- und Einstellungstaste die Sidebar.' }
        Show-SbKey
    }
    Leave = { $true } }

# ------------------------------------------------------------------ Seite: Update

# Welche Funktionen am Fernseher schon eingerichtet sind, steht in installer-stand.txt im Launcher-Ordner
# am TV (überlebt Updates, gilt für jeden PC). Welche Funktionen es gibt und ab welcher Version, steht in
# daten\funktionen.tsv. Neu = in der neuen APK enthalten, aber am TV noch nicht eingerichtet.
function Get-ApkVer([string]$pfad) {
    if ((Split-Path $pfad -Leaf) -match '^Jellydroid-V(\d+(\.\d+){0,3})\.apk$') {
        $v = $Matches[1]; if ($v -notmatch '\.') { $v += '.0' }; return [version]$v
    }
    return $null
}
function Get-InstVer {
    $d = TvShell "dumpsys package $LauncherPkg"
    if ($d -match 'versionName=(\d+(\.\d+){1,3})') { return [version]$Matches[1] }
    return $null
}
function Read-Stand {
    # $null = keine Datei (Launcher wurde nie mit Update-fähigem Installer eingerichtet)
    $t = TvShell "cat $SetupPfad/installer-stand.txt"
    if ($t -match 'funktionen=([^\r\n]*)') { return $Matches[1].Trim() }
    return $null
}
function Write-Stand([string[]]$keys) {
    $f = (@($keys | Where-Object { $_ } | Select-Object -Unique) -join ' ')
    [void](TvShell "mkdir -p $SetupPfad; echo 'funktionen=$f' > $SetupPfad/installer-stand.txt")
    Log "  Eingerichtete Funktionen gespeichert: $(if ($f) { $f } else { 'keine' })"
}
function Get-FunktionenBis($ver) { return @($Funktionen | Where-Object { -not $ver -or [version]$_.ab -le $ver }) }
function Get-Konfiguriert {
    $st = Read-Stand
    if ($null -ne $st) { return @($st -split '\s+' | Where-Object { $_ }) }
    # Ältere Einrichtung ohne Stand-Datei: was die installierte Version schon konnte, gilt als eingerichtet
    $iv = Get-InstVer
    if (-not $iv) { return @() }
    return @(Get-FunktionenBis $iv | ForEach-Object { $_.key })
}

$p = New-Page 'Launcher aktualisieren' ''
$p.Controls.Add((Lbl 'Installiert:' 0 56 120 24 $FontB))
$LblUpdInst = Lbl '' 120 56 600; $p.Controls.Add($LblUpdInst)
$p.Controls.Add((Lbl 'Neue APK:' 0 92 120 24 $FontB))
$TxtUpdApk = Txt '' 120 89 470; $TxtUpdApk.ReadOnly = $true; $p.Controls.Add($TxtUpdApk)
$BtnUpdApk = Btn 'Durchsuchen ...' 600 87 130; $p.Controls.Add($BtnUpdApk)
$LblUpdWarn = Lbl '' 120 120 620 24 $null ([System.Drawing.Color]::FromArgb(176, 0, 32)); $p.Controls.Add($LblUpdWarn)
$p.Controls.Add((Lbl 'Jetzt einrichten:' 0 156 740 24 $FontB))
$ClbUpd = New-Object System.Windows.Forms.CheckedListBox
$ClbUpd.Left = 0; $ClbUpd.Top = 184; $ClbUpd.Width = 740; $ClbUpd.Height = 150; $ClbUpd.CheckOnClick = $true
$p.Controls.Add($ClbUpd)
$LblUpdInfo = Lbl '' 0 344 740 70; $p.Controls.Add($LblUpdInfo)
$script:UpdKeys = @(); $S.UpdWahl = @(); $S.UpdNeuOhne = @(); $S.Konfiguriert = @()

function Fill-Update {
    $iv = Get-InstVer; $av = Get-ApkVer $TxtUpdApk.Text
    $LblUpdInst.Text = if (-not $S.LauncherDa) { 'nicht installiert' } elseif ($iv) { "Jellydroid $iv" } else { 'Jellydroid (Version unbekannt)' }
    $LblUpdWarn.Text = ''
    if ($av -and $iv) {
        if ($av -lt $iv) { $LblUpdWarn.Text = "Die APK ($av) ist älter als die installierte Version." }
        elseif ($av -eq $iv) { $LblUpdWarn.Text = 'Diese Version ist schon installiert.' }
    }
    $S.Konfiguriert = Get-Konfiguriert
    $ClbUpd.Items.Clear(); $script:UpdKeys = @(); $S.UpdNeuOhne = @(); $neuMitSeite = 0
    foreach ($f in (Get-FunktionenBis $av)) {
        $neu = ($S.Konfiguriert -notcontains $f.key)
        if ($f.seite -and $f.seite -ne '-' -and $Pages.ContainsKey($f.seite)) {
            $i = $ClbUpd.Items.Add($f.name + $(if ($neu) { '   neu' } else { '   (schon eingerichtet)' }))
            $ClbUpd.SetItemChecked($i, $neu); $script:UpdKeys += $f.key
            if ($neu) { $neuMitSeite++ }
        } elseif ($neu) { $S.UpdNeuOhne += $f.key }
    }
    $z = @()
    $ohne = @($Funktionen | Where-Object { $S.UpdNeuOhne -contains $_.key } | ForEach-Object { $_.name })
    if ($neuMitSeite -eq 0) { $z += 'Nichts einzurichten. Mit "Weiter" wird die neue Version aufgespielt, alle Einstellungen bleiben.' }
    if ($ohne.Count -gt 0) { $z += "Neu in dieser Version: $($ohne -join ', ')" }
    $LblUpdInfo.Text = $z -join "`n"
}
$BtnUpdApk.Add_Click({
    $d = New-Object System.Windows.Forms.OpenFileDialog
    $d.Filter = 'Android-App (*.apk)|*.apk'
    if ($d.ShowDialog($Form) -eq 'OK') { $TxtUpdApk.Text = $d.FileName; Fill-Update }
})
$Pages['update'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        if (-not $TxtUpdApk.Text) { $TxtUpdApk.Text = Find-Apk }
        Fill-Update
    }
    Leave = {
        if (-not $S.LauncherDa -and $S.AltLauncherDa) { Msg 'Ab V1.10 hat Jellydroid eine neue App-Kennung und lässt sich nicht über die alte Version aktualisieren. Bitte "Launcher installieren oder neu einrichten" wählen, die alte Version wird dabei ersetzt.' 'Warning'; return $false }
        if (-not $S.LauncherDa) { Msg 'Der Launcher ist noch nicht installiert. Bitte "Geführte Einrichtung" oder "Launcher installieren oder neu einrichten" wählen.' 'Warning'; return $false }
        if (-not $TxtUpdApk.Text -or -not (Test-Path $TxtUpdApk.Text)) { Msg 'Die APK-Datei wurde nicht gefunden. Bitte über "Durchsuchen" auswählen.' 'Warning'; return $false }
        $S.UpdWahl = @()
        for ($i = 0; $i -lt $ClbUpd.Items.Count; $i++) { if ($ClbUpd.GetItemChecked($i)) { $S.UpdWahl += $script:UpdKeys[$i] } }
        $seiten = @()
        foreach ($k in $S.UpdWahl) {
            $f = $Funktionen | Where-Object { $_.key -eq $k } | Select-Object -First 1
            if ($f -and $seiten -notcontains $f.seite) { $seiten += $f.seite }
        }
        $script:Ablauf = @('verbindung', 'update') + $seiten + @('ausfuehren')
        return $true
    } }

# ------------------------------------------------------------------ Seite: Streamingdienste

$p = New-Page 'Streamingdienste' ''
$Grid = New-Object System.Windows.Forms.DataGridView
$Grid.Left = 0; $Grid.Top = 96; $Grid.Width = 740; $Grid.Height = 300
$Grid.AllowUserToAddRows = $false; $Grid.AllowUserToDeleteRows = $false; $Grid.RowHeadersVisible = $false
$Grid.SelectionMode = 'FullRowSelect'; $Grid.AutoSizeColumnsMode = 'Fill'; $Grid.BackgroundColor = [System.Drawing.Color]::White
$c1 = New-Object System.Windows.Forms.DataGridViewTextBoxColumn; $c1.HeaderText = 'Dienst'; $c1.ReadOnly = $true
$c2 = New-Object System.Windows.Forms.DataGridViewTextBoxColumn; $c2.HeaderText = 'App am TV'; $c2.ReadOnly = $true
$c3 = New-Object System.Windows.Forms.DataGridViewCheckBoxColumn; $c3.HeaderText = 'Nutze ich'
$c4 = New-Object System.Windows.Forms.DataGridViewCheckBoxColumn; $c4.HeaderText = 'Reihe "Neu bei"'
$c5 = New-Object System.Windows.Forms.DataGridViewTextBoxColumn; $c5.HeaderText = 'Öffnet Titel'; $c5.ReadOnly = $true
foreach ($c in @($c1, $c2, $c3, $c4, $c5)) { [void]$Grid.Columns.Add($c) }
foreach ($c in $Grid.Columns) { $c.SortMode = 'NotSortable' }
$p.Controls.Add($Grid)
$BtnStore = Btn 'Fehlende App am Fernseher im Play Store öffnen' 0 404 360; $p.Controls.Add($BtnStore)
$ChkFav = Chk 'Favoritenleiste aus Jellyfin-App und gewählten Diensten zusammenstellen' 0 444 740; $ChkFav.Checked = $true
$p.Controls.Add($ChkFav)
$Grid.Add_CurrentCellDirtyStateChanged({ if ($Grid.IsCurrentCellDirty) { $Grid.CommitEdit('CurrentCellChange') } })
$Grid.Add_CellValueChanged({
    param($sndr, $e)
    if ($e.RowIndex -lt 0) { return }
    $r = $Grid.Rows[$e.RowIndex]
    if ($e.ColumnIndex -eq 3 -and $r.Cells[3].Value) { $r.Cells[2].Value = $true }
    if ($e.ColumnIndex -eq 2 -and -not $r.Cells[2].Value) { $r.Cells[3].Value = $false }
})
function Get-DienstPaket($d) { foreach ($pk in ($d.pakete -split ',')) { if ($S.Pakete -contains $pk) { return $pk } }; return $null }
$BtnStore.Add_Click({
    if ($Grid.SelectedRows.Count -eq 0) { return }
    $d = $Dienste[$Grid.SelectedRows[0].Index]
    if (Get-DienstPaket $d) { Msg "$($d.name) ist bereits installiert."; return }
    $pk = ($d.pakete -split ',')[0]
    [void](TvShell "am start -a android.intent.action.VIEW -d market://details?id=$pk")
    Msg "Der Play Store zeigt jetzt $($d.name) am Fernseher."
})
$script:GridGeladen = $false
$Pages['dienste'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        if ($script:GridGeladen) { return }
        $Grid.Rows.Clear()
        $gemerkt = @($Merk.dienste)
        foreach ($d in $Dienste) {
            $da = [bool](Get-DienstPaket $d)
            $nutze = if ($gemerkt.Count -gt 0) { $gemerkt -contains $d.key } else { $da }
            $reihe = if (@($Merk.reihen).Count -gt 0) { @($Merk.reihen) -contains $d.key } else { $nutze -and $d.key -eq 'netflix' }
            [void]$Grid.Rows.Add($d.name, $(if ($da) { 'installiert' } else { 'fehlt' }), $nutze, $reihe, $(if ($d.direkt -eq 'ja') { 'direkt' } else { 'nur App-Start' }))
        }
        $script:GridGeladen = $true
    }
    Leave = {
        $Grid.EndEdit()
        $Merk.dienste = @(); $Merk.reihen = @()
        for ($i = 0; $i -lt $Dienste.Count; $i++) {
            if ($Grid.Rows[$i].Cells[2].Value) { $Merk.dienste += $Dienste[$i].key }
            if ($Grid.Rows[$i].Cells[3].Value) { $Merk.reihen += $Dienste[$i].key }
        }
        Save-Merk
        return $true
    } }

# ------------------------------------------------------------------ Seite: Jellyfin und Seerr

$p = New-Page 'Jellyfin und Seerr' ''
$p.Controls.Add((Lbl 'Jellyfin' 0 90 200 24 $FontB))
$p.Controls.Add((Lbl 'Server-Adresse:' 0 120 120))
$TxtJf = Txt '' 120 117 380; $p.Controls.Add($TxtJf)
$BtnJfTest = Btn 'Server prüfen' 120 150 140; $p.Controls.Add($BtnJfTest)
$BtnQc = Btn 'Mit Quick Connect anmelden' 270 150 230; $p.Controls.Add($BtnQc)
$LblJf = Lbl '' 120 186 620 24; $p.Controls.Add($LblJf)
$LblCode = Lbl '' 120 210 620 44 (New-Object System.Drawing.Font('Segoe UI Semibold', 22)) $Blau; $p.Controls.Add($LblCode)

$p.Controls.Add((Lbl 'Seerr / Jellyseerr' 0 270 200 24 $FontB))
$p.Controls.Add((Lbl 'Adresse:' 0 300 120))
$TxtSeerr = Txt '' 120 297 380; $p.Controls.Add($TxtSeerr)
$RbSeerrJf = New-Object System.Windows.Forms.RadioButton; $RbSeerrJf.Text = 'Mit Jellyfin-Konto'; $RbSeerrJf.Left = 120; $RbSeerrJf.Top = 328; $RbSeerrJf.Width = 170
$RbSeerrLok = New-Object System.Windows.Forms.RadioButton; $RbSeerrLok.Text = 'Mit Seerr-Konto (E-Mail)'; $RbSeerrLok.Left = 300; $RbSeerrLok.Top = 328; $RbSeerrLok.Width = 220
if ($Merk.seerrArt -eq 'lokal') { $RbSeerrLok.Checked = $true } else { $RbSeerrJf.Checked = $true }
foreach ($ctl in @($RbSeerrJf, $RbSeerrLok)) { $p.Controls.Add($ctl) }
$p.Controls.Add((Lbl 'Benutzer:' 0 360 120)); $TxtSeerrU = Txt $Merk.seerrUser 120 357 250; $p.Controls.Add($TxtSeerrU)
$p.Controls.Add((Lbl 'Passwort:' 0 392 120)); $TxtSeerrP = Txt '' 120 389 250; $TxtSeerrP.UseSystemPasswordChar = $true; $p.Controls.Add($TxtSeerrP)
$BtnSeerr = Btn 'Anmelden und prüfen' 380 387 180; $p.Controls.Add($BtnSeerr)
$LblSeerr = Lbl '' 120 424 620 24; $p.Controls.Add($LblSeerr)

function Get-JfHeader {
    if (-not $S.JfDevice) { $S.JfDevice = 'jellydroid-' + ([guid]::NewGuid().ToString('N').Substring(0, 16)) }
    $t = if ($S.JfToken) { ", Token=`"$($S.JfToken)`"" } else { '' }
    $h = "MediaBrowser Client=`"Jellydroid`", Device=`"$($S.Modell)`", DeviceId=`"$($S.JfDevice)`", Version=`"0.2`"$t"
    return @{ 'Authorization' = $h; 'X-Emby-Authorization' = $h; 'Accept' = 'application/json' }
}
function Norm-Url([string]$u) { $u = $u.Trim().TrimEnd('/'); if ($u -and $u -notmatch '^https?://') { $u = "https://$u" }; return $u }
$BtnJfTest.Add_Click({
    $u = Norm-Url $TxtJf.Text; $TxtJf.Text = $u
    try {
        $i = Invoke-RestMethod "$u/System/Info/Public" -TimeoutSec 10
        $S.JfServerId = $i.Id
        $qc = try { (Invoke-RestMethod "$u/QuickConnect/Enabled" -TimeoutSec 10) } catch { $false }
        $LblJf.Text = "Gefunden: $($i.ServerName) (Jellyfin $($i.Version)). Quick Connect: " + $(if ($qc) { 'an' } else { 'aus, bitte im Jellyfin-Dashboard einschalten' })
    } catch { $LblJf.Text = "Nicht erreichbar: $($_.Exception.Message)" }
})
$BtnQc.Add_Click({
    $u = Norm-Url $TxtJf.Text; $TxtJf.Text = $u
    if (-not $u) { Msg 'Bitte zuerst die Server-Adresse eingeben.' 'Warning'; return }
    $BtnQc.Enabled = $false
    try {
        $S.JfToken = ''; $S.JfUser = ''
        try { $S.JfServerId = (Invoke-RestMethod "$u/System/Info/Public" -TimeoutSec 10).Id } catch {}
        $start = Invoke-RestMethod "$u/QuickConnect/Initiate" -Method Post -Headers (Get-JfHeader) -Body '' -TimeoutSec 10
        $LblCode.Text = "Code: $($start.Code)"
        $LblJf.Text = 'In Jellyfin unter Profil > Quick Connect eingeben'
        $ende = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $ende) {
            Pause-Ui 3000
            $st = Invoke-RestMethod "$u/QuickConnect/Connect?secret=$([uri]::EscapeDataString($start.Secret))" -Headers (Get-JfHeader) -TimeoutSec 10
            if ($st.Authenticated) {
                $body = @{ Secret = $start.Secret } | ConvertTo-Json
                $auth = Invoke-RestMethod "$u/Users/AuthenticateWithQuickConnect" -Method Post -Headers (Get-JfHeader) -ContentType 'application/json' -Body $body -TimeoutSec 10
                $S.JfToken = $auth.AccessToken; $S.JfUser = $auth.User.Id; $S.JfUserName = $auth.User.Name
                $LblCode.Text = ''
                $LblJf.Text = "Angemeldet als $($S.JfUserName)"
                return
            }
        }
        $LblCode.Text = ''; $LblJf.Text = 'Zeit abgelaufen. Bitte erneut versuchen.'
    } catch {
        $code = $null; try { $code = [int]$_.Exception.Response.StatusCode } catch {}
        $LblCode.Text = ''
        $LblJf.Text = if ($code -eq 401 -or $code -eq 403) { 'Quick Connect ist auf dem Server ausgeschaltet.' } else { "Fehler: $($_.Exception.Message)" }
    } finally { $BtnQc.Enabled = $true }
})
$BtnSeerr.Add_Click({
    $u = Norm-Url $TxtSeerr.Text; $TxtSeerr.Text = $u
    if (-not $u -or -not $TxtSeerrU.Text -or -not $TxtSeerrP.Text) { Msg 'Bitte Adresse, Benutzer und Passwort eingeben.' 'Warning'; return }
    try {
        if ($RbSeerrJf.Checked) { $pfad = '/api/v1/auth/jellyfin'; $body = @{ username = $TxtSeerrU.Text; password = $TxtSeerrP.Text } }
        else { $pfad = '/api/v1/auth/local'; $body = @{ email = $TxtSeerrU.Text; password = $TxtSeerrP.Text } }
        $sess = $null
        $r = Invoke-WebRequest "$u$pfad" -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json) -SessionVariable sess -UseBasicParsing -TimeoutSec 15
        $j = $r.Content | ConvertFrom-Json
        $ck = $sess.Cookies.GetCookies([uri]$u) | Where-Object { $_.Name -eq 'connect.sid' } | Select-Object -First 1
        if (-not $ck) { $ck = $sess.Cookies.GetCookies([uri]($u -replace '^(https?://[^/]+).*$', '$1/')) | Where-Object { $_.Name -eq 'connect.sid' } | Select-Object -First 1 }
        if (-not $ck) { throw 'Keine Sitzung erhalten' }
        $S.SeerrCookie = "connect.sid=$($ck.Value)"; $S.SeerrUserId = [int]$j.id
        $S.SeerrName = if ($j.displayName) { $j.displayName } else { $TxtSeerrU.Text }
        $LblSeerr.Text = "Angemeldet als $($S.SeerrName)."
        $TxtSeerrP.Text = ''
    } catch {
        $code = $null; try { $code = [int]$_.Exception.Response.StatusCode } catch {}
        $LblSeerr.Text = if ($code -eq 401 -or $code -eq 403) { 'Benutzer oder Passwort falsch.' } else { "Fehler: $($_.Exception.Message)" }
        $S.SeerrCookie = ''; $S.SeerrUserId = -1
    }
})
$Pages['konten'] = @{ Panel = $p
    Enter = { $BtnWeiter.Enabled = $true }
    Leave = {
        $TxtJf.Text = Norm-Url $TxtJf.Text; $TxtSeerr.Text = Norm-Url $TxtSeerr.Text
        if ($TxtSeerrP.Text -and -not $S.SeerrCookie) {
            if (-not (Frage 'Du hast ein Seerr-Passwort eingegeben, aber noch nicht auf "Anmelden und prüfen" geklickt. Ohne Anmeldung weiter?')) { return $false }
        }
        $Merk.jellyfinUrl = $TxtJf.Text; $Merk.seerrUrl = $TxtSeerr.Text; $Merk.seerrUser = $TxtSeerrU.Text
        $Merk.seerrArt = if ($RbSeerrLok.Checked) { 'lokal' } else { 'jellyfin' }; Save-Merk
        return $true
    } }

# ------------------------------------------------------------------ Seite: Home Assistant

# Anmeldung wie eine App: Browser öffnet die Home-Assistant-Anmeldung, die Antwort kommt an
# http://localhost:<Port>/cb zurück. Die Adresse wird nicht gemerkt (nur im setup.json für den TV).
$p = New-Page 'Home Assistant' ''
$ChkHa = Chk 'Home Assistant in der Sidebar' 0 90 740; $p.Controls.Add($ChkHa)
$p.Controls.Add((Lbl 'Adresse:' 0 126 120))
$TxtHa = New-Object System.Windows.Forms.ComboBox
$TxtHa.Left = 120; $TxtHa.Top = 123; $TxtHa.Width = 230; $p.Controls.Add($TxtHa)
$BtnHaSuche = Btn 'Im Netzwerk suchen' 360 121 140; $p.Controls.Add($BtnHaSuche)
$BtnHaLogin = Btn 'Im Browser anmelden' 510 121 200; $p.Controls.Add($BtnHaLogin)
$p.Controls.Add((Lbl 'oder Token:' 0 160 120))
$TxtHaTok = Txt '' 120 157 380; $TxtHaTok.UseSystemPasswordChar = $true; $p.Controls.Add($TxtHaTok)
$BtnHaTok = Btn 'Token prüfen' 510 155 200; $p.Controls.Add($BtnHaTok)
$LblHa = Lbl '' 120 190 620 24; $p.Controls.Add($LblHa)
$p.Controls.Add((Lbl 'Fernseher:' 0 230 120 24 $FontB))
$CbHaTv = New-Object System.Windows.Forms.ComboBox; $CbHaTv.DropDownStyle = 'DropDownList'; $CbHaTv.Left = 120; $CbHaTv.Top = 227; $CbHaTv.Width = 470; $p.Controls.Add($CbHaTv)
$ChkHaBell = Chk 'Hinweis am Fernseher, wenn es klingelt' 0 270 740; $p.Controls.Add($ChkHaBell)
$p.Controls.Add((Lbl 'Klingel:' 0 306 120))
$CbHaBell = New-Object System.Windows.Forms.ComboBox; $CbHaBell.DropDownStyle = 'DropDownList'; $CbHaBell.Left = 120; $CbHaBell.Top = 303; $CbHaBell.Width = 470; $p.Controls.Add($CbHaBell)
$p.Controls.Add((Lbl 'Türöffner:' 0 340 120))
$CbHaDoor = New-Object System.Windows.Forms.ComboBox; $CbHaDoor.DropDownStyle = 'DropDownList'; $CbHaDoor.Left = 120; $CbHaDoor.Top = 337; $CbHaDoor.Width = 470; $p.Controls.Add($CbHaDoor)
$S.HaUrl = ''; $S.HaClient = ''; $S.HaRefresh = ''; $S.HaToken = ''; $S.HaAccess = ''
$script:HaTv = @(); $script:HaBell = @(''); $script:HaDoor = @('')

function Norm-HaUrl([string]$u) { $u = $u.Trim().TrimEnd('/'); if ($u -and $u -notmatch '^https?://') { $u = "http://$u" }; return $u }
function Set-HaEnabled {
    $an = $ChkHa.Checked
    foreach ($c in @($TxtHa, $BtnHaSuche, $BtnHaLogin, $TxtHaTok, $BtnHaTok, $CbHaTv, $ChkHaBell)) { $c.Enabled = $an }
    $CbHaBell.Enabled = $an -and $ChkHaBell.Checked; $CbHaDoor.Enabled = $an -and $ChkHaBell.Checked
}
$ChkHa.Add_CheckedChanged({ Set-HaEnabled }); $ChkHaBell.Add_CheckedChanged({ Set-HaEnabled })

function Fill-HaListen {
    # Geräte über die REST-API lesen (Raum und Bereich ermittelt später der Launcher selbst)
    $st = Invoke-RestMethod "$($S.HaUrl)/api/states" -Headers @{ Authorization = "Bearer $($S.HaAccess)" } -TimeoutSec 15
    $name = { param($e) $(if ($e.attributes.friendly_name) { "$($e.attributes.friendly_name)  ($($e.entity_id))" } else { $e.entity_id }) }
    $CbHaTv.Items.Clear(); $CbHaBell.Items.Clear(); $CbHaDoor.Items.Clear()
    $script:HaTv = @(); $script:HaBell = @(''); $script:HaDoor = @('')
    [void]$CbHaBell.Items.Add('keine'); [void]$CbHaDoor.Items.Add('keiner')
    foreach ($e in ($st | Sort-Object entity_id)) {
        if ($e.entity_id -like 'media_player.*') { [void]$CbHaTv.Items.Add((& $name $e)); $script:HaTv += $e.entity_id }
        elseif ($e.entity_id -like 'event.*') { [void]$CbHaBell.Items.Add((& $name $e)); $script:HaBell += $e.entity_id }
        elseif ($e.entity_id -like 'button.*' -or $e.entity_id -like 'lock.*') { [void]$CbHaDoor.Items.Add((& $name $e)); $script:HaDoor += $e.entity_id }
    }
    $i = [array]::IndexOf($script:HaTv, [string]$Merk.haTv)
    if ($i -lt 0) { $i = [array]::FindIndex([string[]]$script:HaTv, [Predicate[string]] { param($x) $x -match 'philips|tv|fernseh' }) }
    if ($CbHaTv.Items.Count -gt 0) { $CbHaTv.SelectedIndex = [Math]::Max(0, $i) }
    $i = [array]::IndexOf($script:HaBell, [string]$Merk.haBell)
    if ($i -lt 0) { $i = [array]::FindIndex([string[]]$script:HaBell, [Predicate[string]] { param($x) $x -match 'ding|klingel|doorbell' }) }
    $CbHaBell.SelectedIndex = [Math]::Max(0, $i)
    $i = [array]::IndexOf($script:HaDoor, [string]$Merk.haDoor)
    if ($i -lt 0) { $i = [array]::FindIndex([string[]]$script:HaDoor, [Predicate[string]] { param($x) $x -match 'intercom|door|tuer|tür' }) }
    $CbHaDoor.SelectedIndex = [Math]::Max(0, $i)
}

# Home Assistant suchen: alle Geräte mit offenem Port 8123, geprüft über /manifest.json (ohne Anmeldung lesbar)
function Test-HaAdresse([string]$u) {
    try {
        $r = Invoke-WebRequest "$u/manifest.json" -UseBasicParsing -TimeoutSec 4
        return ($r.Content -match 'Home Assistant')
    } catch { return $false }
}
$BtnHaSuche.Add_Click({
    $BtnHaSuche.Enabled = $false
    try {
        $LblHa.Text = 'Suche Home Assistant im Netzwerk ...'; [System.Windows.Forms.Application]::DoEvents()
        $ips = @(Find-OffenerPort 8123)
        if ($ips.Count -eq 0) {
            # Rückfall: Standardname des Home-Assistant-Betriebssystems (mDNS)
            try { $ips = @([System.Net.Dns]::GetHostAddresses('homeassistant.local') | Where-Object { $_.AddressFamily -eq 'InterNetwork' } | ForEach-Object { $_.ToString() }) } catch {}
        }
        $gefunden = @()
        foreach ($ip in ($ips | Select-Object -Unique)) {
            [System.Windows.Forms.Application]::DoEvents()
            if (Test-HaAdresse "http://${ip}:8123") { $gefunden += "http://${ip}:8123" }
            elseif (Test-HaAdresse "https://${ip}:8123") { $gefunden += "https://${ip}:8123" }
        }
        $TxtHa.Items.Clear()
        foreach ($g in $gefunden) { [void]$TxtHa.Items.Add($g) }
        if ($gefunden.Count -gt 0) {
            $TxtHa.Text = $gefunden[0]
            $LblHa.Text = $(if ($gefunden.Count -eq 1) { 'Home Assistant gefunden. Jetzt anmelden.' } else { "$($gefunden.Count) gefunden. Adresse auswählen, dann anmelden." })
        } else { $LblHa.Text = 'Nichts gefunden. Bitte die Adresse von Hand eingeben.' }
    } finally { $BtnHaSuche.Enabled = $ChkHa.Checked }
})

$BtnHaLogin.Add_Click({
    $u = Norm-HaUrl $TxtHa.Text; $TxtHa.Text = $u
    if (-not $u) { Msg 'Bitte zuerst die Adresse von Home Assistant eingeben.' 'Warning'; return }
    $BtnHaLogin.Enabled = $false
    $l = New-Object System.Net.HttpListener
    try {
        $port = Get-Random -Minimum 18100 -Maximum 18999
        $client = "http://localhost:$port/"
        $l.Prefixes.Add($client); $l.Start()
        $state = [guid]::NewGuid().ToString('N')
        $auth = "$u/auth/authorize?response_type=code&client_id=$([uri]::EscapeDataString($client))&redirect_uri=$([uri]::EscapeDataString($client + 'cb'))&state=$state"
        Start-Process $auth
        $LblHa.Text = 'Bitte im Browser bei Home Assistant anmelden …'
        $ctxTask = $l.GetContextAsync(); $ende = (Get-Date).AddMinutes(5); $code = $null
        while ((Get-Date) -lt $ende) {
            Pause-Ui 300
            if (-not $ctxTask.IsCompleted) { continue }
            $ctx = $ctxTask.Result
            $q = [System.Web.HttpUtility]::ParseQueryString($ctx.Request.Url.Query)
            $ok = ($ctx.Request.Url.AbsolutePath -eq '/cb' -and $q['state'] -eq $state -and $q['code'])
            $html = if ($ok) { 'Fertig. Du kannst dieses Fenster schließen und zum Installer zurückkehren.' } else { 'Jellydroid' }
            $b = [Text.Encoding]::UTF8.GetBytes("<!doctype html><meta charset=utf-8><body style='font-family:sans-serif;padding:30px'>$html</body>")
            $ctx.Response.ContentType = 'text/html; charset=utf-8'; $ctx.Response.OutputStream.Write($b, 0, $b.Length); $ctx.Response.Close()
            if ($ok) { $code = $q['code']; break }
            $ctxTask = $l.GetContextAsync()
        }
        if (-not $code) { $LblHa.Text = 'Zeit abgelaufen. Bitte erneut versuchen.'; return }
        $tok = Invoke-RestMethod "$u/auth/token" -Method Post -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 15 `
            -Body @{ grant_type = 'authorization_code'; code = $code; client_id = $client }
        $S.HaUrl = $u; $S.HaClient = $client; $S.HaRefresh = $tok.refresh_token; $S.HaAccess = $tok.access_token; $S.HaToken = ''
        Fill-HaListen
        $LblHa.Text = 'Angemeldet. Fernseher und Klingel unten auswählen.'
    } catch {
        $LblHa.Text = "Fehler: $($_.Exception.Message)"
    } finally { try { $l.Stop(); $l.Close() } catch {}; $BtnHaLogin.Enabled = $true }
})
$BtnHaTok.Add_Click({
    $u = Norm-HaUrl $TxtHa.Text; $TxtHa.Text = $u
    if (-not $u -or -not $TxtHaTok.Text.Trim()) { Msg 'Bitte Adresse und Token eingeben.' 'Warning'; return }
    try {
        $S.HaAccess = $TxtHaTok.Text.Trim()
        $S.HaUrl = $u; $S.HaToken = $S.HaAccess; $S.HaClient = ''; $S.HaRefresh = ''
        Fill-HaListen
        $LblHa.Text = 'Token gültig. Fernseher und Klingel unten auswählen.'
    } catch {
        $code = $null; try { $code = [int]$_.Exception.Response.StatusCode } catch {}
        $LblHa.Text = if ($code -eq 401) { 'Token ungültig.' } else { "Fehler: $($_.Exception.Message)" }
        $S.HaUrl = ''; $S.HaToken = ''
    }
})
$Pages['homeassistant'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        if ($null -eq $script:HaInit) { $ChkHa.Checked = [bool]$Merk.ha; $ChkHaBell.Checked = [bool]$Merk.haPopup; $script:HaInit = $true }
        Set-HaEnabled
    }
    Leave = {
        if ($ChkHa.Checked -and -not $S.HaUrl) {
            if (-not (Frage 'Home Assistant ist noch nicht angemeldet. Ohne Anmeldung weiter? (Geht später auch am Fernseher per QR-Code.)')) { return $false }
        }
        $Merk.ha = $ChkHa.Checked; $Merk.haPopup = $ChkHaBell.Checked
        if ($CbHaTv.SelectedIndex -ge 0) { $Merk.haTv = $script:HaTv[$CbHaTv.SelectedIndex] }
        if ($CbHaBell.SelectedIndex -ge 0) { $Merk.haBell = $script:HaBell[$CbHaBell.SelectedIndex] }
        if ($CbHaDoor.SelectedIndex -ge 0) { $Merk.haDoor = $script:HaDoor[$CbHaDoor.SelectedIndex] }
        Save-Merk
        return $true
    } }

# ------------------------------------------------------------------ Seite: Tipps

$p = New-Page 'Tipps' ''
$Rtb = New-Object System.Windows.Forms.RichTextBox
$Rtb.Left = 0; $Rtb.Top = 44; $Rtb.Width = 740; $Rtb.Height = 470; $Rtb.ReadOnly = $true; $Rtb.BorderStyle = 'None'; $Rtb.BackColor = [System.Drawing.Color]::White
$Rtb.Anchor = 'Top,Left,Right,Bottom'
$p.Controls.Add($Rtb)
$tf = Join-Path $DatenDir 'tipps.txt'
if (Test-Path $tf) {
    $bloecke = ((Get-Content $tf -Raw -Encoding UTF8) -replace "`r", '') -split "`n\s*`n"
    foreach ($b in $bloecke) {
        $zeilen = $b.Trim() -split "`n"
        if (-not $zeilen[0]) { continue }
        $Rtb.SelectionFont = $FontB; $Rtb.AppendText($zeilen[0] + "`n")
        $Rtb.SelectionFont = $Font; $Rtb.AppendText((($zeilen | Select-Object -Skip 1) -join ' ') + "`n`n")
    }
}
$Pages['tipps'] = @{ Panel = $p; Enter = { $BtnWeiter.Enabled = $true }; Leave = { $true } }

# ------------------------------------------------------------------ Seite: Wiederherstellen

$p = New-Page 'Wiederherstellen' ''
$LbSich = New-Object System.Windows.Forms.ListBox
$LbSich.Left = 0; $LbSich.Top = 96; $LbSich.Width = 360; $LbSich.Height = 200
$p.Controls.Add($LbSich)
$LblSichInfo = Lbl '' 380 96 360 200; $p.Controls.Add($LblSichInfo)
$ChkRApps = Chk 'Entfernte und deaktivierte Apps zurückholen' 0 310 740; $ChkRApps.Checked = $true
$ChkREinst = Chk 'Einstellungen zurücksetzen (Privates DNS, Animationen)' 0 338 740; $ChkREinst.Checked = $true
$ChkRHome = Chk 'Startbildschirm zurück auf Android TV (Launcher-Home-Taste ausschalten)' 0 366 740
$ChkRDeinst = Chk 'Eigenen Launcher deinstallieren' 0 394 740
$ChkRApk = Chk 'Fehlende Play-Store-Apps aus gesicherten APK-Dateien neu installieren' 0 422 740
foreach ($ctl in @($ChkRApps, $ChkREinst, $ChkRHome, $ChkRDeinst, $ChkRApk)) { $p.Controls.Add($ctl) }
$ChkRDeinst.Add_CheckedChanged({ if ($ChkRDeinst.Checked) { $ChkRHome.Checked = $true } })
function Get-SicherListe { return @(Get-ChildItem $SicherDir -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'pakete-alle.txt') } | Sort-Object Name -Descending) }
function Read-Liste([string]$f) { if (Test-Path $f) { return @(Get-Content $f -Encoding UTF8 | ForEach-Object { $_.Trim() } | Where-Object { $_ }) } else { return @() } }
$LbSich.Add_SelectedIndexChanged({
    if ($LbSich.SelectedIndex -lt 0) { return }
    $d = (Get-SicherListe)[$LbSich.SelectedIndex].FullName
    $alt = Read-Liste (Join-Path $d 'pakete-alle.txt')
    $fehlen = @($alt | Where-Object { $S.Pakete -notcontains $_ })
    $neu = @($S.Pakete | Where-Object { $alt -notcontains $_ })
    $aus = @($S.Deaktiviert | Where-Object { (Read-Liste (Join-Path $d 'pakete-deaktiviert.txt')) -notcontains $_ })
    $geraet = (Read-Liste (Join-Path $d 'geraet.txt')) -join ', '
    $apks = Test-Path (Join-Path $d 'apks')
    $LblSichInfo.Text = "$geraet`n`nSeit der Sicherung entfernt: $($fehlen.Count)`nSeitdem deaktiviert: $($aus.Count)`nSeitdem neu installiert: $($neu.Count) (bleiben)`nAPK-Dateien gesichert: " + $(if ($apks) { 'ja' } else { 'nein' })
    $ChkRApk.Enabled = $apks; if (-not $apks) { $ChkRApk.Checked = $false }
})
$Pages['wiederherstellen'] = @{ Panel = $p
    Enter = {
        $BtnWeiter.Enabled = $true
        $LbSich.Items.Clear()
        foreach ($d in (Get-SicherListe)) { [void]$LbSich.Items.Add($d.Name) }
        if ($LbSich.Items.Count -gt 0) { $LbSich.SelectedIndex = 0 } else { $LblSichInfo.Text = "Keine Sicherung gefunden in`n$SicherDir" }
        $ChkRHome.Enabled = ($S.LauncherDa -or $S.AltLauncherDa); $ChkRDeinst.Enabled = ($S.LauncherDa -or $S.AltLauncherDa)
    }
    Leave = {
        if ($LbSich.SelectedIndex -lt 0 -and ($ChkRApps.Checked -or $ChkREinst.Checked -or $ChkRApk.Checked)) { Msg 'Bitte eine Sicherung auswählen.' 'Warning'; return $false }
        return $true
    } }

# ------------------------------------------------------------------ Seite: Ausführen

$p = New-Page 'Zusammenfassung' ''
$LblPlan = Lbl '' 0 44 740 170; $p.Controls.Add($LblPlan)
$BtnLos = Btn 'Jetzt ausführen' 0 220 200; $BtnLos.Font = $FontB; $BtnLos.BackColor = $Blau; $BtnLos.ForeColor = [System.Drawing.Color]::White
$p.Controls.Add($BtnLos)
$BtnOrdner = Btn 'Sicherungsordner öffnen' 210 220 200; $p.Controls.Add($BtnOrdner)
$Bar = New-Object System.Windows.Forms.ProgressBar; $Bar.Left = 420; $Bar.Top = 224; $Bar.Width = 320; $Bar.Height = 22
$p.Controls.Add($Bar)
$TxtLog = New-Object System.Windows.Forms.TextBox
$TxtLog.Left = 0; $TxtLog.Top = 260; $TxtLog.Width = 740; $TxtLog.Height = 260; $TxtLog.Multiline = $true; $TxtLog.ScrollBars = 'Vertical'
$TxtLog.ReadOnly = $true; $TxtLog.Font = $FontC; $TxtLog.Anchor = 'Top,Left,Right,Bottom'
$p.Controls.Add($TxtLog)
$BtnOrdner.Add_Click({ Start-Process explorer.exe $SicherDir })

function Log([string]$t) {
    $TxtLog.AppendText($t + "`r`n")
    Add-Content -Path $LogDatei -Value ("{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $t) -Encoding UTF8
    [System.Windows.Forms.Application]::DoEvents()
}
function Get-SchriftText {
    if ($Merk.schrift -ne 'eigene') { return 'Inter (Standard)' }
    return "eigene ($((@($Merk.schriftDateien) | ForEach-Object { Split-Path $_ -Leaf }) -join ', '))"
}
function Plan-Text {
    $z = @()
    switch ($S.Modus) {
        'voll'      { $z += '1. Sicherung erstellen' + $(if ($ChkApk.Checked) { ' (mit APK-Dateien)' } else { '' }) }
        'sicherung' { $z += '1. Sicherung erstellen' + $(if ($ChkApk.Checked) { ' (mit APK-Dateien)' } else { '' }) }
        'erneut'    { $z += '1. Kurze Sicherung erstellen' }
        'launcher'  { $z += '1. Kurze Sicherung erstellen' }
        'update'    { $z += '1. Kurze Sicherung erstellen' }
    }
    if ($S.Modus -eq 'update') {
        $z += "2. Launcher aktualisieren: $(Split-Path $TxtUpdApk.Text -Leaf) (Einstellungen bleiben)"
        if ($S.UpdWahl.Count -eq 0) { $z += '3. Keine Funktion neu einrichten' }
        else {
            $z += '3. Einrichten:'
            if ($S.UpdWahl -contains 'airplay') { $z += '    AirPlay: ' + $(if ($Merk.airplay) { ('an, als „' + $Merk.airplayName + '“') } else { 'aus' }) }
            if ($S.UpdWahl -contains 'homeassistant') { $z += '    Home Assistant: ' + $(if (-not $Merk.ha) { 'aus' } elseif ($S.HaUrl) { 'angemeldet' + $(if ($Merk.haPopup -and $Merk.haBell) { ', mit Türklingel' } else { '' }) } else { 'später am Fernseher anmelden' }) }
            if ($S.UpdWahl -contains 'schrift') { $z += '    Schrift: ' + (Get-SchriftText) }
            if ($S.UpdWahl -contains 'sidebartaste') { $z += '    Sidebar-Taste: ' + $(if ($null -eq $S.SbKey) { 'unverändert' } elseif ($S.SbKey -le 0) { 'Standard' } else { $S.SbName }) }
            foreach ($k in $S.UpdWahl) { if ($k -notin @('airplay','homeassistant','sidebartaste','schrift')) { $z += "    $(($Funktionen | Where-Object { $_.key -eq $k } | Select-Object -First 1).name)" } }
        }
    }
    if ($S.Modus -in @('voll','erneut')) {
        $sel = Get-AppAuswahl
        $art = if ($RbDisable.Checked) { 'deaktivieren' } else { 'für deinen Benutzer entfernen' }
        $z += "2. $($sel.Count) Apps $art"
        if ($ChkDns.Checked) { $z += "    Privates DNS: $($TxtDns.Text)" }
        if ($ChkAnim.Checked) { $z += '    Animationen: halbe Dauer' }
    }
    if ($S.Modus -in @('voll','launcher')) {
        if ($ChkLauncher.Checked) { $z += "3. Launcher installieren: $(Split-Path $TxtApk.Text -Leaf)" + $(if ($ChkHome.Checked) { ', Home-Taste übernehmen' } else { '' }) }
        $jf = if ($S.JfToken) { "Jellyfin angemeldet als $($S.JfUserName)" } elseif ($TxtJf.Text) { 'Jellyfin-Adresse ohne Anmeldung' } else { 'ohne Jellyfin' }
        $se = if ($S.SeerrCookie) { "Seerr angemeldet als $($S.SeerrName)" } elseif ($TxtSeerr.Text) { 'Seerr-Adresse ohne Anmeldung' } else { 'ohne Seerr' }
        $z += "4. Launcher einrichten: $(@($Merk.dienste).Count) Dienste, $jf, $se" + $(if ($TxtAvatar.Text) { ', neues Profilbild' } else { '' })
        $z += '    AirPlay: ' + $(if ($Merk.airplay) { ('an, als „' + $Merk.airplayName + '“') } else { 'aus' })
        $z += '    Schrift: ' + (Get-SchriftText)
        $z += '    Home Assistant: ' + $(if (-not $Merk.ha) { 'aus' } elseif ($S.HaUrl) { 'angemeldet' + $(if ($Merk.haPopup -and $Merk.haBell) { ', mit Türklingel' } else { '' }) } else { 'später am Fernseher anmelden' })
        if ($null -ne $S.SbKey) { $z += '    Sidebar-Taste: ' + $(if ($S.SbKey -le 0) { 'Standard' } else { $S.SbName }) }
    }
    if ($S.Modus -eq 'restore') {
        $z += "Sicherung: $($LbSich.SelectedItem)"
        if ($ChkRApps.Checked) { $z += '• Apps zurückholen' }
        if ($ChkRApk.Checked) { $z += '• Fehlende Apps aus APK neu installieren' }
        if ($ChkREinst.Checked) { $z += '• Einstellungen zurücksetzen' }
        if ($ChkRHome.Checked) { $z += '• Startbildschirm zurück auf Android TV' }
        if ($ChkRDeinst.Checked) { $z += '• Launcher deinstallieren' }
    }
    return ($z -join "`n")
}

# ---------- Aktionen

function Do-Sicherung([bool]$mitApk) {
    $d = Join-Path $SicherDir (Get-Date -Format 'yyyy-MM-dd_HHmmss')
    New-Item -ItemType Directory -Force -Path $d | Out-Null
    $S.SicherungOrdner = $d
    Log "Sicherung nach $d"
    @((TvShell 'getprop ro.product.manufacturer'), (TvShell 'getprop ro.product.model'), ('Android ' + (TvShell 'getprop ro.build.version.release')),
      (TvShell 'getprop ro.build.fingerprint'), ('Gesichert ' + (Get-Date -Format 'dd.MM.yyyy HH:mm'))) | Set-Content (Join-Path $d 'geraet.txt') -Encoding UTF8
    (Get-Liste '--user 0') | Set-Content (Join-Path $d 'pakete-alle.txt') -Encoding UTF8
    (Get-Liste '-s')       | Set-Content (Join-Path $d 'pakete-system.txt') -Encoding UTF8
    (Get-Liste '-3')       | Set-Content (Join-Path $d 'pakete-nutzer.txt') -Encoding UTF8
    (Get-Liste '-d')       | Set-Content (Join-Path $d 'pakete-deaktiviert.txt') -Encoding UTF8
    $einst = @()
    foreach ($k in @('private_dns_mode','private_dns_specifier','window_animation_scale','transition_animation_scale','animator_duration_scale')) {
        $einst += "global|$k|" + (TvShell "settings get global $k")
    }
    foreach ($k in @('enabled_accessibility_services','accessibility_enabled')) { $einst += "secure|$k|" + (TvShell "settings get secure $k") }
    $einst | Set-Content (Join-Path $d 'einstellungen.txt') -Encoding UTF8
    $startb = (TvShell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME') -split "`n" | Select-Object -Last 1
    $startb.Trim() | Set-Content (Join-Path $d 'startbildschirm.txt') -Encoding UTF8
    Log "  $((Get-Liste '--user 0').Count) Apps, Einstellungen und Startbildschirm ($($startb.Trim())) gesichert"
    if ($mitApk) {
        $nutzer = Get-Liste '-3'
        $i = 0
        foreach ($pk in $nutzer) {
            $i++; Log "  APK $i/$($nutzer.Count): $pk"
            $ziel = Join-Path $d "apks\$pk"; New-Item -ItemType Directory -Force -Path $ziel | Out-Null
            foreach ($pfad in ((TvShell "pm path $pk") -split "`n" | Where-Object { $_ -like 'package:*' })) {
                [void](Adb @('-s', $S.Geraet, 'pull', $pfad.Substring(8).Trim(), $ziel))
            }
        }
    }
}

function Do-Entfernen {
    $sel = Get-AppAuswahl
    $methode = if ($RbDisable.Checked) { 'disable' } else { 'uninstall' }
    $zeilen = @("# Letzte Auswahl vom $(Get-Date -Format 'dd.MM.yyyy HH:mm') - paket|methode")
    $i = 0
    foreach ($pk in $sel) {
        $i++
        if ($Geschuetzt -contains $pk) { Log "  übersprungen (geschützt): $pk"; continue }
        $cmd = if ($methode -eq 'disable') { "pm disable-user --user 0 $pk" } else { "pm uninstall -k --user 0 $pk" }
        $r = TvShell $cmd
        Log ("  {0,-45} {1}" -f $pk, $r)
        $zeilen += "$pk|$methode"
        $Bar.Value = [Math]::Min(100, [int](20 + 40 * $i / [Math]::Max(1, $sel.Count)))
    }
    $zeilen | Set-Content (Join-Path $SicherDir 'letzte-auswahl.txt') -Encoding UTF8
    if ($S.SicherungOrdner) { $zeilen | Set-Content (Join-Path $S.SicherungOrdner 'entfernt.txt') -Encoding UTF8 }
    if ($ChkDns.Checked) {
        [void](TvShell 'settings put global private_dns_mode hostname')
        [void](TvShell "settings put global private_dns_specifier $($TxtDns.Text.Trim())")
        Log "  Privates DNS: $(TvShell 'settings get global private_dns_specifier')"
    }
    if ($ChkAnim.Checked) {
        foreach ($k in @('window_animation_scale','transition_animation_scale','animator_duration_scale')) { [void](TvShell "settings put global $k 0.5") }
        Log '  Animationen: 0.5'
    }
}

# Nach "am force-stop" bindet Android die Bedienungshilfe des Launchers nicht neu an, die Home-Taste
# landet dann beim Android-TV-Startbildschirm. Eintrag kurz entfernen und wieder setzen, damit sie neu startet.
function Restart-HomeDienst {
    $akt = (TvShell 'settings get secure enabled_accessibility_services').Trim()
    if ($akt -notlike "*$A11yDienst*") { return }
    $rest = ($akt -split ':' | Where-Object { $_ -and $_ -ne $A11yDienst -and $_ -ne 'null' }) -join ':'
    if ($rest) { [void](TvShell "settings put secure enabled_accessibility_services $rest") } else { [void](TvShell 'settings delete secure enabled_accessibility_services') }
    Pause-Ui 1000
    [void](TvShell "settings put secure enabled_accessibility_services $akt")
    [void](TvShell 'settings put secure accessibility_enabled 1')
    Pause-Ui 1500
    $jetzt = ((TvShell 'dumpsys accessibility') -split "`n") -match 'Bound services.*HomeButtonService'
    Log $(if ($jetzt) { '  Home-Taste aktiv' } else { '  Home-Taste: Bedienungshilfe bitte am Fernseher prüfen (Einstellungen > Bedienungshilfen)' })
}

function Remove-AltLauncher {
    if (-not $S.AltLauncherDa) { return }
    Log 'Entferne ältere Jellydroid-Version (de.elias.launcher)'
    [void](TvShell "pm revoke $AltLauncherPkg android.permission.WRITE_SECURE_SETTINGS")
    [void](TvShell "am force-stop $AltLauncherPkg")
    $akt = (TvShell 'settings get secure enabled_accessibility_services').Trim()
    $rest = ($akt -split ':' | Where-Object { $_ -and $_ -ne $AltA11yDienst -and $_ -ne 'null' }) -join ':'
    if ($rest) { [void](TvShell "settings put secure enabled_accessibility_services $rest") } else { [void](TvShell 'settings delete secure enabled_accessibility_services') }
    [void](TvShell "cmd role remove-role-holder android.app.role.HOME $AltLauncherPkg")
    Log "  Deinstallieren: $(Adb @('-s', $S.Geraet, 'uninstall', $AltLauncherPkg))"
    $S.AltLauncherDa = $false
}

function Do-Launcher {
    if ($ChkLauncher.Checked) {
        Log "Installiere Launcher: $($TxtApk.Text)"
        $r = Adb @('-s', $S.Geraet, 'install', '-r', $TxtApk.Text)
        Log "  $r"
        if ($r -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            Log '  Die APK ist anders signiert als der installierte Launcher. Erst über "Wiederherstellen" den Launcher deinstallieren, dann erneut.'
            return
        }
        if ($r -notmatch 'Success') { Log '  Installation fehlgeschlagen.'; return }
        $S.LauncherDa = $true
        Remove-AltLauncher
        # AirPlay: Vollbild darf sich öffnen, auch wenn gerade eine andere App läuft
        [void](TvShell "appops set $LauncherPkg SYSTEM_ALERT_WINDOW allow")
        # Sidebar: WLAN-Name lesen (Android gibt ihn nur mit Standortberechtigung heraus)
        [void](TvShell "pm grant $LauncherPkg android.permission.ACCESS_FINE_LOCATION")
        if ($ChkHome.Checked) {
            # Selbstheilung: der Launcher trägt die Bedienungshilfe nach jedem Neustart wieder ein
            [void](TvShell "pm grant $LauncherPkg android.permission.WRITE_SECURE_SETTINGS")
            $akt = (TvShell 'settings get secure enabled_accessibility_services').Trim()
            if ($akt -eq 'null' -or -not $akt) { $neu = $A11yDienst }
            elseif ($akt -like "*$A11yDienst*") { $neu = $akt }
            else { $neu = "$akt`:$A11yDienst" }
            [void](TvShell "settings put secure enabled_accessibility_services $neu")
            [void](TvShell 'settings put secure accessibility_enabled 1')
            [void](TvShell "cmd package set-home-activity $LauncherPkg/.MainActivity")
            [void](TvShell "cmd role add-role-holder android.app.role.HOME $LauncherPkg")
            Log '  Home-Taste übernommen (Bedienungshilfe + Selbstheilung)'
        }
    }
    if (-not $S.LauncherDa) { Log 'Launcher nicht installiert, Einrichtung übersprungen.'; return }

    # Vorkonfiguration als setup.json übergeben (siehe launcher-schnittstelle\SCHNITTSTELLE.md)
    $favs = @()
    if ($ChkFav.Checked) {
        if ($S.Pakete -contains $Merk.jellyfinApp) { $favs += $Merk.jellyfinApp }
        foreach ($d in $Dienste) { if (@($Merk.dienste) -contains $d.key) { $pk = Get-DienstPaket $d; if ($pk) { $favs += $pk } } }
    }
    $cfg = [ordered]@{
        theme = $Merk.theme; place = [int]$Merk.ort; jellyfin_app = $Merk.jellyfinApp
        services_used = @($Merk.dienste); services_rows = @($Merk.reihen)
    }
    if ($TxtJf.Text) { $cfg.jellyfin_url = $TxtJf.Text }
    if ($TxtSeerr.Text) { $cfg.seerr_url = $TxtSeerr.Text }
    $setup = [ordered]@{ version = 1; config = $cfg }
    if ($favs.Count -gt 0) { $setup.favorites = @($favs) }
    if ($S.JfToken) { $setup.jellyfin = [ordered]@{ device_id = $S.JfDevice; token = $S.JfToken; user_id = $S.JfUser; server_id = $S.JfServerId } }
    if ($S.SeerrCookie) { $setup.seerr = [ordered]@{ cookie = $S.SeerrCookie; user_id = $S.SeerrUserId } }
    foreach ($k in @($Funktionen | ForEach-Object { $_.key })) { Add-FunktionSetup $setup $k }
    if ($TxtAvatar.Text) {
        $ext = [IO.Path]::GetExtension($TxtAvatar.Text).TrimStart('.').ToLower()
        [void](TvShell "mkdir -p $SetupPfad")
        [void](TvShell "rm -f $SetupPfad/avatar.jpg $SetupPfad/avatar.jpeg $SetupPfad/avatar.png $SetupPfad/avatar.webp")
        Log "Profilbild übertragen: $(Adb @('-s', $S.Geraet, 'push', $TxtAvatar.Text, "$SetupPfad/avatar.$ext"))"
    }
    if (Send-Setup $setup) {
        # Eingerichtet: Funktionen dieser Version, deren Seite in diesem Ablauf vorkam (oder die keine brauchen)
        Write-Stand @(Get-FunktionenBis (Get-InstVer) | Where-Object { -not $_.seite -or $_.seite -eq '-' -or $Ablauf -contains $_.seite } | ForEach-Object { $_.key })
    }
}

# Fügt die setup.json-Felder einer Funktion aus daten\funktionen.tsv hinzu (Funktionen ohne Felder: nichts)
function Add-FunktionSetup($setup, [string]$key) {
    switch ($key) {
        'airplay' {
            $setup.config.airplay_enabled = [bool]$Merk.airplay; $setup.config.airplay_name = [string]$Merk.airplayName
        }
        'sidebartaste' {
            if ($null -ne $S.SbKey) { $setup.config.sidebar_key = [int]$S.SbKey }
        }
        'schrift' {
            # Dateien nach files/schrift-neu/, der Launcher übernimmt sie beim Lesen von setup.json
            $setup.config.font = [string]$Merk.schrift
            if ($Merk.schrift -eq 'eigene') {
                [void](TvShell "rm -rf $SetupPfad/schrift-neu")
                [void](TvShell "mkdir -p $SetupPfad/schrift-neu")
                foreach ($f in @($Merk.schriftDateien)) {
                    $name = ([IO.Path]::GetFileName($f)) -replace '[^A-Za-z0-9._-]', '_'
                    Log "Schrift übertragen: $(Adb @('-s', $S.Geraet, 'push', $f, "$SetupPfad/schrift-neu/$name"))"
                }
            }
        }
        'homeassistant' {
            if (-not $Merk.ha) { return }
            $ha = [ordered]@{ media_player = [string]$Merk.haTv; doorbell_popup = [bool]$Merk.haPopup; doorbell_event = [string]$Merk.haBell; door_button = [string]$Merk.haDoor }
            if ($S.HaUrl -and $S.HaRefresh) { $ha.url = $S.HaUrl; $ha.client_id = $S.HaClient; $ha.refresh_token = $S.HaRefresh }
            elseif ($S.HaUrl -and $S.HaToken) { $ha.url = $S.HaUrl; $ha.token = $S.HaToken }
            $setup.homeassistant = $ha
        }
    }
}

# Überträgt setup.json (falls etwas drinsteht), startet den Launcher neu und prüft die Übernahme.
# Rückgabe: $true, wenn der Launcher die Datei gelesen hat oder nichts zu übertragen war.
function Send-Setup($setup) {
    $leer = ($setup.Keys.Count -le 2 -and $setup.config.Keys.Count -eq 0)
    if (-not $leer) {
        $tmp = Join-Path $env:TEMP 'setup.json'
        [System.IO.File]::WriteAllText($tmp, ($setup | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
        [void](TvShell "mkdir -p $SetupPfad")
        $r = Adb @('-s', $S.Geraet, 'push', $tmp, "$SetupPfad/setup.json")
        Remove-Item $tmp -ErrorAction SilentlyContinue
        Log "Vorkonfiguration übertragen: $r"
    }
    [void](TvShell "am force-stop $LauncherPkg")
    [void](TvShell "am start -n $LauncherPkg/.MainActivity")
    Pause-Ui 5000
    Restart-HomeDienst | Out-Null
    if ($leer) { return $true }
    $rest = TvShell "ls $SetupPfad/setup.json"
    if ($rest -notmatch 'No such file') {
        [void](TvShell "rm -f $SetupPfad/setup.json")
        Log '  Hinweis: Diese Launcher-Version kennt die Vorkonfiguration noch nicht. Die Datei wurde wieder gelöscht.'
        Log '  Bitte Launcher aktualisieren oder Einstellungen am Fernseher unter Sidebar > Einstellungen > Launcher Konfiguration setzen.'
        return $false
    }
    # Ab V1.4 schreibt der Launcher setup-ergebnis.txt ("ok" oder "fehler: …")
    $erg = (TvShell "cat $SetupPfad/setup-ergebnis.txt").Trim()
    [void](TvShell "rm -f $SetupPfad/setup-ergebnis.txt")
    if ($erg -like 'fehler*') { Log "  Launcher konnte die Einstellungen nicht übernehmen: $erg"; return $false }
    Log '  Launcher hat die Einstellungen übernommen.'
    return $true
}

# Update: neue APK über die alte installieren (adb install -r behält alle Einstellungen),
# danach nur die gewählten neuen Funktionen übertragen.
function Do-Update {
    Log "Aktualisiere Launcher: $($TxtUpdApk.Text)"
    $vorher = Get-InstVer
    $r = Adb @('-s', $S.Geraet, 'install', '-r', $TxtUpdApk.Text)
    Log "  $r"
    if ($r -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
        Log '  Die APK ist älter als die installierte Version. Zum Zurückgehen erst über "Wiederherstellen" deinstallieren.'; return
    }
    if ($r -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Log '  Die APK ist anders signiert als der installierte Launcher. Erst über "Wiederherstellen" den Launcher deinstallieren, dann erneut.'; return
    }
    if ($r -notmatch 'Success') { Log '  Installation fehlgeschlagen.'; return }
    $nachher = Get-InstVer
    Log "  Version: $(if ($vorher) { $vorher } else { '?' }) → $(if ($nachher) { $nachher } else { '?' })"
    [void](TvShell "appops set $LauncherPkg SYSTEM_ALERT_WINDOW allow")
    [void](TvShell "pm grant $LauncherPkg android.permission.ACCESS_FINE_LOCATION")
    if ((TvShell 'settings get secure enabled_accessibility_services') -like "*$A11yDienst*") {
        [void](TvShell "pm grant $LauncherPkg android.permission.WRITE_SECURE_SETTINGS")
    }
    $setup = [ordered]@{ version = 1; config = [ordered]@{} }
    foreach ($k in $S.UpdWahl) { Add-FunktionSetup $setup $k }
    if (Send-Setup $setup) {
        # Bisher eingerichtet + jetzt eingerichtet + neue Funktionen, die keine Einrichtung brauchen
        $neuOhne = @(Get-FunktionenBis $nachher | Where-Object { -not $_.seite -or $_.seite -eq '-' } | ForEach-Object { $_.key })
        Write-Stand (@($S.Konfiguriert) + @($S.UpdWahl) + $neuOhne)
    }
}

function Do-Restore {
    $d = if ($LbSich.SelectedIndex -ge 0) { (Get-SicherListe)[$LbSich.SelectedIndex].FullName } else { $null }
    if ($ChkRHome.Checked -or $ChkRDeinst.Checked) {
        if ($S.AltLauncherDa -and -not $S.LauncherDa) { $LauncherPkg = $AltLauncherPkg; $A11yDienst = $AltA11yDienst }
        Log 'Startbildschirm zurücksetzen'
        [void](TvShell "pm revoke $LauncherPkg android.permission.WRITE_SECURE_SETTINGS")
        [void](TvShell "am force-stop $LauncherPkg")
        $akt = (TvShell 'settings get secure enabled_accessibility_services').Trim()
        $rest = ($akt -split ':' | Where-Object { $_ -and $_ -ne $A11yDienst -and $_ -ne 'null' }) -join ':'
        if ($rest) { [void](TvShell "settings put secure enabled_accessibility_services $rest") } else { [void](TvShell 'settings delete secure enabled_accessibility_services') }
        [void](TvShell "cmd role remove-role-holder android.app.role.HOME $LauncherPkg")
        $vorher = 'com.google.android.tvlauncher/.MainActivity'
        if ($d -and (Test-Path (Join-Path $d 'startbildschirm.txt'))) {
            $v = (Get-Content (Join-Path $d 'startbildschirm.txt') -Raw).Trim()
            if ($v -match '/' -and $v -notlike "$LauncherPkg*" -and $v -notlike "$AltLauncherPkg*") { $vorher = $v }
        }
        Log "  $(TvShell "cmd package set-home-activity $vorher")  ($vorher)"
        if ($ChkRDeinst.Checked) { Log "  Deinstallieren: $(Adb @('-s', $S.Geraet, 'uninstall', $LauncherPkg))" }
    }
    if (-not $d) { return }
    $Bar.Value = 30
    if ($ChkRApps.Checked) {
        Log 'Apps zurückholen'
        $alt = Read-Liste (Join-Path $d 'pakete-alle.txt')
        $jetzt = Get-Liste '--user 0'
        foreach ($pk in ($alt | Where-Object { $jetzt -notcontains $_ })) {
            if ($ChkRDeinst.Checked -and $pk -eq $LauncherPkg) { continue }
            Log ("  {0,-45} {1}" -f $pk, (TvShell "cmd package install-existing --user 0 $pk"))
        }
        $altAus = Read-Liste (Join-Path $d 'pakete-deaktiviert.txt')
        foreach ($pk in ((Get-Liste '-d') | Where-Object { $altAus -notcontains $_ })) {
            Log ("  {0,-45} {1}" -f $pk, (TvShell "pm enable --user 0 $pk"))
        }
    }
    $Bar.Value = 60
    if ($ChkRApk.Checked) {
        $jetzt = Get-Liste '--user 0'
        foreach ($ad in (Get-ChildItem (Join-Path $d 'apks') -Directory -ErrorAction SilentlyContinue)) {
            if ($jetzt -contains $ad.Name) { continue }
            $files = @(Get-ChildItem $ad.FullName -Filter *.apk | ForEach-Object { $_.FullName })
            if ($files.Count -eq 0) { continue }
            $r = if ($files.Count -eq 1) { Adb @('-s', $S.Geraet, 'install', $files[0]) } else { Adb (@('-s', $S.Geraet, 'install-multiple') + $files) }
            Log ("  APK {0,-41} {1}" -f $ad.Name, $r)
        }
    }
    $Bar.Value = 80
    if ($ChkREinst.Checked -and (Test-Path (Join-Path $d 'einstellungen.txt'))) {
        Log 'Einstellungen zurücksetzen'
        foreach ($z in (Read-Liste (Join-Path $d 'einstellungen.txt'))) {
            $t = $z -split '\|', 3
            if ($t.Count -lt 3 -or $t[1] -like '*accessibility*') { continue }   # Bedienungshilfen regelt der Startbildschirm-Punkt
            if ($t[2] -eq 'null' -or -not $t[2]) { [void](TvShell "settings delete $($t[0]) $($t[1])") }
            else { [void](TvShell "settings put $($t[0]) $($t[1]) $($t[2])") }
            Log "  $($t[1]) = $($t[2])"
        }
    }
}

$BtnLos.Add_Click({
    if (-not $S.Verbunden) { Msg 'Keine Verbindung zum Fernseher.' 'Warning'; return }
    if (-not (Frage "Jetzt am Fernseher ausführen?`n`n$(Plan-Text)")) { return }
    $BtnLos.Enabled = $false; $BtnZurueck.Visible = $false; $BtnMenue.Enabled = $false
    $TxtLog.Clear(); $Bar.Value = 5
    Log "=== $($Form.Text): $($S.Modus) auf $($S.Modell) ($($S.Geraet)) ==="
    try {
        $st = (Adb @('-s', $S.Geraet, 'get-state'))
        if ($st -ne 'device') { [void](Adb @('connect', $S.Geraet)); Pause-Ui 2000 }
        switch ($S.Modus) {
            'voll'      { Do-Sicherung $ChkApk.Checked; $Bar.Value = 20; Do-Entfernen; $Bar.Value = 60; Do-Launcher }
            'erneut'    { Do-Sicherung $false; $Bar.Value = 20; Do-Entfernen }
            'sicherung' { Do-Sicherung $ChkApk.Checked }
            'launcher'  { Do-Sicherung $false; $Bar.Value = 30; Do-Launcher }
            'update'    { Do-Sicherung $false; $Bar.Value = 30; Do-Update }
            'restore'   { Do-Restore }
        }
        $Bar.Value = 100
        Log '=== Fertig ==='
        if ($S.Modus -in @('voll','launcher','update') -and $S.LauncherDa) { [void](TvShell "am start -n $LauncherPkg/.MainActivity") }
        $S.Ausgefuehrt = $true
        $S.Pakete = Get-Liste '--user 0'; $S.Deaktiviert = Get-Liste '-d'
        Msg 'Fertig. Das Protokoll steht unten und in installer-protokoll.txt.'
    } catch {
        Log "FEHLER: $($_.Exception.Message)"
        Msg "Es ist ein Fehler aufgetreten:`n$($_.Exception.Message)" 'Error'
    } finally { $BtnMenue.Enabled = $true; $BtnLos.Enabled = -not $S.Ausgefuehrt; $script:AppsGeladen = $false }
})
$Pages['ausfuehren'] = @{ Panel = $p
    Enter = { $LblPlan.Text = Plan-Text; $BtnLos.Enabled = $true; $TxtLog.Clear(); $Bar.Value = 0; $BtnWeiter.Enabled = $false }
    Leave = { $true } }

# ------------------------------------------------------------------ Start

# Seiten ohne Untertitel: Inhalte näher an die Überschrift rücken
foreach ($n in @('menue','vorbereitung','verbindung','launcher','dienste','konten','wiederherstellen')) {
    foreach ($ctl in $Pages[$n].Panel.Controls) { if ($ctl.Top -ge 88) { $ctl.Top -= 40 } }
}
$Form.Add_Shown({ Place-Buttons; Show-Page 'menue' })
$Form.Add_FormClosing({ Save-Merk })
[void]$Form.ShowDialog()
