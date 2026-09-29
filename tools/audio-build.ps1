param(
    [string]$Voice = 'Microsoft Huihui Desktop',
    [string]$Ffmpeg = 'ffmpeg'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$charactersPath = Join-Path $root 'content\characters.json'
$wordsPath = Join-Path $root 'content\words.json'
$audioRoot = Join-Path $root 'content\audio'
$clipRoot = Join-Path $audioRoot 'clips'
$manifestPath = Join-Path $audioRoot 'manifest.json'
$utf8 = New-Object System.Text.UTF8Encoding($false)

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$installed = $synth.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Name -eq $Voice -and $_.VoiceInfo.Culture.Name -eq 'zh-CN' }
if (-not $installed) { throw "Offline zh-CN System.Speech voice '$Voice' is not installed." }
$synth.SelectVoice($Voice)
$synth.Rate = -1
$ffmpegCommand = Get-Command $Ffmpeg -ErrorAction Stop

$texts = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
foreach ($card in (Get-Content $charactersPath -Raw -Encoding UTF8 | ConvertFrom-Json)) {
    [void]$texts.Add([string]$card.character)
}
if (Test-Path $wordsPath) {
    foreach ($card in (Get-Content $wordsPath -Raw -Encoding UTF8 | ConvertFrom-Json)) {
        foreach ($word in $card.words) { [void]$texts.Add([string]$word.text) }
    }
}
$ordered = New-Object 'System.Collections.Generic.List[string]'
foreach ($value in $texts) { $ordered.Add($value) }
$ordered.Sort([System.StringComparer]::Ordinal)

New-Item -ItemType Directory -Path $clipRoot -Force | Out-Null
$previousHashes = @{}
if (Test-Path $manifestPath) {
    $previousManifest = Get-Content $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($clip in $previousManifest.clips) { $previousHashes[[string]$clip.path] = [string]$clip.sha256 }
}
$tempWav = Join-Path $audioRoot '_render.wav'
$clips = New-Object 'System.Collections.Generic.List[object]'
$created = 0
$skipped = 0
try {
    foreach ($value in $ordered) {
        $identity = "v1|$Voice|ogg-vorbis-q3|$value"
        $identityBytes = [System.Text.Encoding]::UTF8.GetBytes($identity)
        $hashAlgorithm = [System.Security.Cryptography.SHA256]::Create()
        try { $name = ([BitConverter]::ToString($hashAlgorithm.ComputeHash($identityBytes)) -replace '-', '').ToLowerInvariant().Substring(0, 24) }
        finally { $hashAlgorithm.Dispose() }
        $relative = "audio/clips/$name.ogg"
        $destination = Join-Path $clipRoot "$name.ogg"
        $existingValid = (Test-Path $destination) -and $previousHashes.ContainsKey($relative) -and
            ((Get-FileHash -Algorithm SHA256 -Path $destination).Hash.ToLowerInvariant() -eq $previousHashes[$relative])
        if ($existingValid) {
            $skipped++
        } else {
            $synth.SetOutputToWaveFile($tempWav)
            try { $synth.Speak($value) }
            finally { $synth.SetOutputToNull() }
            & $ffmpegCommand.Source -nostdin -hide_banner -loglevel error -y -i $tempWav -vn -c:a libvorbis -q:a 3 -ar 22050 $destination
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path $destination)) {
                throw "ffmpeg could not encode '$value' (exit $LASTEXITCODE)."
            }
            $created++
        }
        $fileHash = (Get-FileHash -Algorithm SHA256 -Path $destination).Hash.ToLowerInvariant()
        $clips.Add([ordered]@{ text = $value; path = $relative; sha256 = $fileHash })
    }
} finally {
    $synth.Dispose()
    Remove-Item -LiteralPath $tempWav -ErrorAction SilentlyContinue
}

$manifest = [ordered]@{
    version = 1
    voice = $Voice
    codec = 'ogg-vorbis-q3-22050hz'
    clips = $clips
}
$json = $manifest | ConvertTo-Json -Depth 6
$previous = if (Test-Path $manifestPath) { [System.IO.File]::ReadAllText($manifestPath, $utf8) } else { '' }
if ($previous -ne ($json + "`n")) { [System.IO.File]::WriteAllText($manifestPath, $json + "`n", $utf8) }
Write-Output "Audio clips: $($ordered.Count) total, $created new, $skipped reused. Manifest: $manifestPath"
