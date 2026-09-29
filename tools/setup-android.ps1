param(
  [string]$Root = (Split-Path -Parent $PSScriptRoot),
  [switch]$AcceptAndroidLicenses
)
$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $Root '.toolchain'
$downloads = Join-Path $toolRoot 'downloads'
$jdkHome = Join-Path $toolRoot 'jdk-17'
$sdkRoot = Join-Path $toolRoot 'android-sdk'
$gradleHome = Join-Path $toolRoot 'gradle-8.9'
New-Item -ItemType Directory -Force -Path $downloads,$toolRoot | Out-Null

function Assert-TaskPath($Path) {
  $safeRoot = [System.IO.Path]::GetFullPath($toolRoot).TrimEnd('\') + '\'
  $resolved = [System.IO.Path]::GetFullPath($Path)
  if (-not $resolved.StartsWith($safeRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw "Refusing path outside toolchain: $resolved" }
}

function Get-File($Url, $Path) {
  if (-not (Test-Path -LiteralPath $Path)) {
    Write-Host "Downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Path -MaximumRedirection 10
  }
  $hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
  [pscustomobject]@{ url=$Url; path=$Path; sha256=$hash }
}
function Expand-One($Archive, $Destination, $ExpectedChild) {
  if (-not (Test-Path -LiteralPath $Destination)) {
    $temp = Join-Path $toolRoot ('extract-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $temp | Out-Null
    Expand-Archive -LiteralPath $Archive -DestinationPath $temp
    $child = Get-ChildItem -LiteralPath $temp -Directory | Select-Object -First 1
    if ($ExpectedChild -and $child.Name -ne $ExpectedChild) { throw "Unexpected archive root '$($child.Name)'" }
    Assert-TaskPath $child.FullName
    Assert-TaskPath $Destination
    Assert-TaskPath $temp
    Move-Item -LiteralPath $child.FullName -Destination $Destination
    Remove-Item -LiteralPath $temp -Recurse -Force
  }
}

$jdkZip = Join-Path $downloads 'temurin-jdk17-windows-x64.zip'
$jdkInfo = Get-File 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $jdkZip
Expand-One $jdkZip $jdkHome $null
$jdkBin = Join-Path $jdkHome 'bin'

$gradleZip = Join-Path $downloads 'gradle-8.9-bin.zip'
$gradleInfo = Get-File 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' $gradleZip
# Pinned from Gradle's official release-checksums page; check locally without another network request.
$gradleExpected = 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab'
if ($gradleInfo.sha256 -ne $gradleExpected) { throw "Gradle SHA-256 mismatch: got $($gradleInfo.sha256), expected $gradleExpected" }
Expand-One $gradleZip $gradleHome 'gradle-8.9'

$sdkZip = Join-Path $downloads 'commandlinetools-win-latest.zip'
$sdkInfo = Get-File 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' $sdkZip
$sdkCmd = Join-Path $sdkRoot 'cmdline-tools\latest'
if (-not (Test-Path -LiteralPath $sdkCmd)) {
  $temp = Join-Path $toolRoot ('sdk-extract-' + [guid]::NewGuid().ToString('N'))
  Expand-Archive -LiteralPath $sdkZip -DestinationPath $temp
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $sdkCmd) | Out-Null
  Assert-TaskPath (Join-Path $temp 'cmdline-tools')
  Assert-TaskPath $sdkCmd
  Assert-TaskPath $temp
  Move-Item -LiteralPath (Join-Path $temp 'cmdline-tools') -Destination $sdkCmd
  Remove-Item -LiteralPath $temp -Recurse -Force
}
$sdkManager = Join-Path $sdkCmd 'bin\sdkmanager.bat'
$sdkPackages = @('platform-tools','platforms;android-35','build-tools;35.0.0')
if ($AcceptAndroidLicenses) {
  $env:JAVA_HOME = $jdkHome
  $env:ANDROID_HOME = $sdkRoot
  $env:ANDROID_SDK_ROOT = $sdkRoot
  $env:PATH = "$jdkBin;$env:PATH"
  ("y`n" * 20) | & $sdkManager "--sdk_root=$sdkRoot" @sdkPackages
  if ($LASTEXITCODE -ne 0) { throw "sdkmanager package installation failed ($LASTEXITCODE)" }
} else {
  Write-Host 'Android licenses not accepted yet. Rerun with -AcceptAndroidLicenses to install only the requested platform-tools, API 35, and Build Tools 35.0.0 packages.'
}

$manifest = [ordered]@{
  generatedUtc = [DateTime]::UtcNow.ToString('o')
  artifacts = @($jdkInfo,$gradleInfo,$sdkInfo)
  gradlePublishedSha256 = $gradleExpected
  sdkPackages = $sdkPackages
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $toolRoot 'downloads\manifest.json') -Encoding UTF8
Write-Host "JDK: $jdkBin\java.exe"
Write-Host "Gradle: $gradleHome\bin\gradle.bat"
Write-Host "SDK: $sdkRoot"
Write-Host 'The script sets environment variables only in this PowerShell process.'
