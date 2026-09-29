param([string]$Ffprobe = 'ffprobe')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$contentRoot = Join-Path $root 'content'
$charactersPath = Join-Path $contentRoot 'characters.json'
$wordsPath = Join-Path $contentRoot 'words.json'
$manifestPath = Join-Path $contentRoot 'audio\manifest.json'
$probe = (Get-Command $Ffprobe -ErrorAction Stop).Source

$expected = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
foreach ($card in (Get-Content $charactersPath -Raw -Encoding UTF8 | ConvertFrom-Json)) {
    [void]$expected.Add([string]$card.character)
}
if (Test-Path $wordsPath) {
    foreach ($card in (Get-Content $wordsPath -Raw -Encoding UTF8 | ConvertFrom-Json)) {
        foreach ($word in $card.words) { [void]$expected.Add([string]$word.text) }
    }
}
$manifest = Get-Content $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.version -ne 1) { throw "Unsupported audio manifest version $($manifest.version)." }
$seenTexts = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
$seenPaths = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
$shortest = [double]::PositiveInfinity
$longest = 0.0
foreach ($clip in $manifest.clips) {
    if (-not $seenTexts.Add([string]$clip.text)) { throw "Duplicate clip text: $($clip.text)" }
    if (-not $seenPaths.Add([string]$clip.path)) { throw "Duplicate clip path: $($clip.path)" }
    if (-not $expected.Contains([string]$clip.text)) { throw "Unexpected clip text: $($clip.text)" }
    if ($clip.path -notmatch '^audio/clips/[0-9a-f]{24}\.ogg$') { throw "Invalid clip path: $($clip.path)" }
    $file = Join-Path $contentRoot $clip.path
    if (-not (Test-Path -LiteralPath $file)) { throw "Missing clip file: $($clip.path)" }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant()
    if ($actualHash -ne $clip.sha256) { throw "Audio hash mismatch: $($clip.path)" }
    $durationText = (& $probe -v error -show_entries format=duration -of 'default=noprint_wrappers=1:nokey=1' $file)
    if ($LASTEXITCODE -ne 0) { throw "ffprobe failed for $($clip.path)." }
    $duration = 0.0
    if (-not [double]::TryParse($durationText, [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$duration) -or $duration -le 0) {
        throw "Clip has no positive audio duration: $($clip.path) ($durationText)."
    }
    $shortest = [math]::Min($shortest, $duration)
    $longest = [math]::Max($longest, $duration)
}
if ($seenTexts.Count -ne $expected.Count) { throw "Manifest has $($seenTexts.Count) terms but source requires $($expected.Count)." }
Write-Output "Validated $($seenTexts.Count) exact-text clips; all SHA-256 hashes match and durations are positive ($([math]::Round($shortest,2))–$([math]::Round($longest,2)) s)."
