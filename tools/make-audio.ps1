# Nimmt alle italienischen Wörter und Sätze aus ./content einmal mit der freien Sprach-KI Piper auf
# und legt sie als MP3 in ./audio ab (plus audio/index.json als Inhaltsverzeichnis).
# Läuft komplett lokal, ohne Konto. Bereits vorhandene Aufnahmen werden übersprungen.
#
#   ./tools/make-audio.ps1 -Piper C:\…\piper.exe -Stimme C:\…\it_IT-paola-medium.onnx
#
# Piper: https://github.com/rhasspy/piper (MIT) · Stimmen: https://huggingface.co/rhasspy/piper-voices
# ffmpeg wird zum Umwandeln in MP3 gebraucht (winget install Gyan.FFmpeg.Essentials).
param(
    [Parameter(Mandatory)] [string]$Piper,
    [Parameter(Mandatory)] [string]$Stimme,
    [string]$Ffmpeg = 'ffmpeg',
    [double]$Langsamer = 1.1,   # > 1 = etwas langsamer gesprochen
    [switch]$AllesNeu
)
$ErrorActionPreference = 'Stop'
$wurzel = Split-Path $PSScriptRoot -Parent
$utf8 = New-Object System.Text.UTF8Encoding($false)
$stimmenName = [IO.Path]::GetFileNameWithoutExtension($Stimme)

# Gleiche Bereinigung wie beim Vorlesen in der App (web/js/app.js → bereinige).
function Bereinige([string]$s) {
    if (-not $s) { return '' }
    ((($s -replace '\(.*?\)', ' ') -replace '/', ', ' -replace '…', ' ' -replace '_+', ' ') -replace '\s+', ' ').Trim()
}

# Dateiname aus dem Text: "Ciao, Anna!" → ciao-anna.mp3 (Akzente bleiben unterscheidbar: è → e-)
function Dateiname([string]$text, $vergeben) {
    $s = $text.ToLowerInvariant()
    $s = $s -replace 'à', 'a-' -replace 'è', 'e-' -replace 'é', 'e_' -replace 'ì', 'i-' -replace 'ò', 'o-' -replace 'ù', 'u-'
    $s = ($s -replace '[^a-z0-9\-_]+', '-').Trim('-')
    if ($s.Length -gt 60) { $s = $s.Substring(0, 60).Trim('-') }
    if (-not $s) { $s = 'text' }
    # Windows verbietet Dateinamen wie con, nul, aux, com1 … („con“ ist aber ein italienisches Wort!)
    if ($s -match '^(con|prn|aux|nul|com\d|lpt\d)$') { $s = "$s-wort" }
    $name = $s; $n = 2
    while ($vergeben.Contains($name)) { $name = "$s-$n"; $n++ }
    [void]$vergeben.Add($name)
    "$name.mp3"
}

# JSON-Text nur mit ASCII-Zeichen (Umlaute/Akzente als \uXXXX) – so kommt alles heil bei Piper an.
function JsonText([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($c in $s.ToCharArray()) {
        if ($c -eq '"' -or $c -eq '\') { [void]$sb.Append('\').Append($c) }
        elseif ([int]$c -gt 127 -or [int]$c -lt 32) { [void]$sb.AppendFormat('\u{0:x4}', [int]$c) }
        else { [void]$sb.Append($c) }
    }
    '"' + $sb.ToString() + '"'
}

# ---------------------------------------------------------------- Alle Texte sammeln
$texte = New-Object 'System.Collections.Generic.List[string]'
$gesehen = New-Object 'System.Collections.Generic.HashSet[string]'
function Merke([string]$roh) {
    $t = Bereinige $roh
    if ($t -and $gesehen.Add($t.ToLowerInvariant())) { $texte.Add($t) }
}
Merke 'Ciao! Come stai?'   # „Stimme testen“ in den Einstellungen
foreach ($f in Get-ChildItem (Join-Path $wurzel 'content') -Filter '*.json' | Sort-Object Name) {
    $j = [IO.File]::ReadAllText($f.FullName, $utf8) | ConvertFrom-Json
    foreach ($v in $j.verben) { foreach ($x in $v.formen) { Merke $x } }
    foreach ($e in $j.einheiten) {
        foreach ($v in $e.vokabeln) { Merke $v.it }
        foreach ($k in $e.erklaerung) { foreach ($b in $k.beispiele) { Merke $b.it } }
        foreach ($u in $e.uebungen) { if ($u.typ -ne 'multiple_choice' -or $u.loesung_de) { Merke $u.loesung } }
    }
}

# ---------------------------------------------------------------- Was fehlt noch?
$ordner = Join-Path $wurzel 'audio'
New-Item -ItemType Directory -Force $ordner | Out-Null
$indexDatei = Join-Path $ordner 'index.json'
$alt = @{}
if (-not $AllesNeu -and (Test-Path $indexDatei)) {
    $altJson = [IO.File]::ReadAllText($indexDatei, $utf8) | ConvertFrom-Json
    if ($altJson.stimme -eq $stimmenName) { $altJson.dateien.PSObject.Properties | ForEach-Object { $alt[$_.Name] = $_.Value } }
}
$vergeben = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($d in $alt.Values) { [void]$vergeben.Add([IO.Path]::GetFileNameWithoutExtension($d)) }

$liste = [ordered]@{}
$auftraege = @()
$temp = Join-Path ([IO.Path]::GetTempPath()) "leonardo-audio-$PID"
New-Item -ItemType Directory -Force $temp | Out-Null
foreach ($t in $texte) {
    $key = $t.ToLowerInvariant()
    $datei = $alt[$key]
    if (-not $datei -or -not (Test-Path (Join-Path $ordner $datei))) {
        if (-not $datei) { $datei = Dateiname $t $vergeben }
        $auftraege += [pscustomobject]@{ text = $t; mp3 = (Join-Path $ordner $datei); wav = (Join-Path $temp ($datei -replace '\.mp3$', '.wav')) }
    }
    $liste[$key] = $datei
}
"$($texte.Count) Texte, davon $($auftraege.Count) neu aufzunehmen (Stimme: $stimmenName)"

# ---------------------------------------------------------------- Aufnehmen (ein Piper-Lauf für alle Texte)
if ($auftraege.Count) {
    $jsonl = Join-Path $temp 'auftraege.jsonl'
    $zeilen = $auftraege | ForEach-Object { '{"text": ' + (JsonText $_.text) + ', "output_file": ' + (JsonText $_.wav) + '}' }
    [IO.File]::WriteAllText($jsonl, ($zeilen -join "`n") + "`n", [Text.Encoding]::ASCII)
    $piperArgs = @('--model', $Stimme, '--json-input', '--length_scale', "$Langsamer".Replace(',', '.'), '--sentence_silence', '0.1', '--quiet')
    Get-Content $jsonl -Encoding Ascii | & $Piper @piperArgs | Out-Null
    $nr = 0
    foreach ($a in $auftraege) {
        if (-not (Test-Path $a.wav)) { throw "Piper hat keine Aufnahme erzeugt für: $($a.text)" }
        # Mono, 40 kbit/s, Stille am Anfang/Ende entfernt
        & $Ffmpeg -y -loglevel error -i $a.wav -af 'silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse,apad=pad_dur=0.15' -ac 1 -b:a 40k $a.mp3
        $nr++
        if ($nr % 200 -eq 0) { "… $nr von $($auftraege.Count) umgewandelt" }
    }
}
Remove-Item $temp -Recurse -Force

# Nicht mehr gebrauchte Aufnahmen entfernen
$benutzt = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($d in $liste.Values) { [void]$benutzt.Add($d) }
Get-ChildItem $ordner -Filter '*.mp3' | Where-Object { -not $benutzt.Contains($_.Name) } | Remove-Item

$json = [ordered]@{ stimme = $stimmenName; dateien = $liste } | ConvertTo-Json -Depth 3 -Compress
[IO.File]::WriteAllText($indexDatei, $json, $utf8)
$groesse = (Get-ChildItem $ordner -Filter '*.mp3' | Measure-Object Length -Sum).Sum / 1MB
"Fertig: $($texte.Count) Texte, $($auftraege.Count) neu aufgenommen, {0:N1} MB" -f $groesse
