# Gemeinsame Hilfe fuer alle Skripte: ermittelt die ADB-Adresse des Fernsehers.
# Reihenfolge: Parameter -Ip, dann die zuletzt im Installer verbundene Adresse
# (installer\installer-einstellungen.json), sonst Abfrage in der Konsole.
# Einbinden mit:  . (Join-Path $PSScriptRoot '..\skripte\fernseher.ps1')
function Get-Fernseher([string]$Ip, [string]$Port) {
    $merk = $null
    $datei = Join-Path $PSScriptRoot '..\installer\installer-einstellungen.json'
    if (Test-Path $datei) { try { $merk = Get-Content $datei -Raw | ConvertFrom-Json } catch {} }
    if ($Ip -match '^(.+):(\d+)$') { $Ip = $Matches[1]; $Port = $Matches[2] }
    if (-not $Ip -and $merk -and $merk.ip) { $Ip = [string]$merk.ip }
    if (-not $Port) { $Port = if ($merk -and $merk.port) { [string]$merk.port } else { '5555' } }
    if (-not $Ip) { $Ip = (Read-Host 'IP-Adresse des Fernsehers (steht unter Einstellungen > Netzwerk)').Trim() }
    if ($Ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw "Ungueltige IP-Adresse: '$Ip'" }
    return "${Ip}:$Port"
}
