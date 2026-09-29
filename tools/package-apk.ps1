param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
$questRoot=Split-Path -Parent $PSScriptRoot
if (-not $SkipBuild) { & (Join-Path $PSScriptRoot 'android-build.ps1') }
$env:JAVA_HOME=Join-Path $questRoot '.toolchain\jdk-17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$metadata=Get-Content -Raw -LiteralPath (Join-Path $questRoot 'android\app\build\outputs\apk\debug\output-metadata.json') | ConvertFrom-Json
$version=$metadata.elements[0].versionName
$apkSource=Join-Path $questRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
$signer=Join-Path $questRoot '.toolchain\android-sdk\build-tools\35.0.0\apksigner.bat'
& $signer verify --verbose $apkSource
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
$artifactRoot=Join-Path $questRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
$apkTarget=Join-Path $artifactRoot "ChinaQuest-$version.apk"
Copy-Item -LiteralPath $apkSource -Destination $apkTarget
$digest=(Get-FileHash -LiteralPath $apkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
$digest | Set-Content -LiteralPath "$apkTarget.sha256" -Encoding ascii
Write-Output "APK=$apkTarget"
Write-Output "SHA256=$digest"
