# Baut die Web-App (PWA) nach _site/.
# Die Inhalte kommen direkt aus ./content und die Schrift aus der Android-App –
# nichts wird doppelt gepflegt. Läuft lokal (Windows PowerShell) und auf GitHub (pwsh).
param([string]$Ziel = '_site')
$ErrorActionPreference = 'Stop'

$wurzel = $PSScriptRoot
$aus = Join-Path $wurzel $Ziel
$utf8 = New-Object System.Text.UTF8Encoding($false)

if (Test-Path $aus) { Remove-Item $aus -Recurse -Force }
New-Item -ItemType Directory -Force $aus | Out-Null

# 1. App-Dateien
Copy-Item -Path ([IO.Path]::Combine($wurzel, 'web', '*')) -Destination $aus -Recurse -Force

# 2. Inhalte + Inhaltsliste
$inhaltAus = Join-Path $aus 'content'
New-Item -ItemType Directory -Force $inhaltAus | Out-Null
$dateien = Get-ChildItem -Path (Join-Path $wurzel 'content') -Filter '*.json' | Sort-Object Name
foreach ($d in $dateien) { Copy-Item $d.FullName $inhaltAus }
$liste = '[' + (($dateien | ForEach-Object { '"' + $_.Name + '"' }) -join ', ') + ']'
[IO.File]::WriteAllText((Join-Path $inhaltAus 'index.json'), $liste, $utf8)

# 3. Schrift aus der Android-App
$schriftAus = Join-Path $aus 'fonts'
New-Item -ItemType Directory -Force $schriftAus | Out-Null
Copy-Item -Path ([IO.Path]::Combine($wurzel, 'app', 'src', 'main', 'res', 'font', '*.ttf')) -Destination $schriftAus

# 4. Version für den Service Worker (neue Version = Handys laden die Inhalte neu)
$version = if ($env:GITHUB_SHA) { $env:GITHUB_SHA.Substring(0, 8) } else { Get-Date -Format 'yyyyMMddHHmmss' }
$sw = Join-Path $aus 'sw.js'
[IO.File]::WriteAllText($sw, [IO.File]::ReadAllText($sw, $utf8).Replace('__VERSION__', $version), $utf8)

# 5. GitHub Pages: keine Jekyll-Verarbeitung
[IO.File]::WriteAllText((Join-Path $aus '.nojekyll'), '', $utf8)

$anzahl = (Get-ChildItem $aus -Recurse -File).Count
"Web-App gebaut: $aus ($anzahl Dateien, Version $version, Inhalte: $($dateien.Name -join ', '))"
