# Kleiner lokaler Testserver: zeigt _site unter http://localhost:8123/leonardo/
# (genau wie später auf GitHub Pages). Beenden mit Strg+C.
param([int]$Port = 8123, [string]$Ordner = ([IO.Path]::Combine($PSScriptRoot, '..', '_site')), [string]$Basis = '/leonardo/')

$ordner = (Resolve-Path $Ordner).Path
$typen = @{
    '.html' = 'text/html; charset=utf-8'; '.js' = 'text/javascript; charset=utf-8'; '.css' = 'text/css; charset=utf-8'
    '.json' = 'application/json; charset=utf-8'; '.webmanifest' = 'application/manifest+json'; '.png' = 'image/png'
    '.ttf' = 'font/ttf'; '.svg' = 'image/svg+xml'; '.txt' = 'text/plain; charset=utf-8'
}
$server = New-Object System.Net.HttpListener
$server.Prefixes.Add("http://localhost:$Port/")
$server.Start()
"Leonardo läuft auf http://localhost:$Port$Basis  (Ordner: $ordner)"

try {
    while ($server.IsListening) {
        $kontext = $server.GetContext()
        $pfad = [Uri]::UnescapeDataString($kontext.Request.Url.AbsolutePath)
        $antwort = $kontext.Response
        try {
            if ($pfad -eq $Basis.TrimEnd('/')) { $antwort.Redirect($Basis); $antwort.Close(); continue }
            if (-not $pfad.StartsWith($Basis)) { $antwort.StatusCode = 404; $antwort.Close(); continue }
            $relativ = $pfad.Substring($Basis.Length)
            if ($relativ -eq '' -or $relativ.EndsWith('/')) { $relativ += 'index.html' }
            $datei = Join-Path $ordner ($relativ -replace '/', [IO.Path]::DirectorySeparatorChar)
            $voll = [IO.Path]::GetFullPath($datei)
            if (-not $voll.StartsWith($ordner) -or -not (Test-Path $voll -PathType Leaf)) {
                $antwort.StatusCode = 404
            } else {
                $bytes = [IO.File]::ReadAllBytes($voll)
                $endung = [IO.Path]::GetExtension($voll).ToLower()
                $antwort.ContentType = if ($typen.ContainsKey($endung)) { $typen[$endung] } else { 'application/octet-stream' }
                $antwort.Headers.Add('Cache-Control', 'no-cache')
                $antwort.ContentLength64 = $bytes.Length
                $antwort.OutputStream.Write($bytes, 0, $bytes.Length)
            }
            "{0} {1} {2}" -f (Get-Date -Format 'HH:mm:ss'), $antwort.StatusCode, $pfad
        } catch {
            "Fehler bei ${pfad}: $($_.Exception.Message)"
        } finally {
            $antwort.Close()
        }
    }
} finally {
    $server.Stop()
}
