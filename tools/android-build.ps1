param([string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$questRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $questRoot '.toolchain\jdk-17'
$env:ANDROID_HOME = Join-Path $questRoot '.toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $questRoot '.toolchain\gradle-cache'
$env:ANDROID_USER_HOME = Join-Path $questRoot '.toolchain\android-user'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
# This Windows host rejects AF_UNIX connects. JDK PipeImpl falls back to TCP
# when its Unix socket directory is unavailable. This relative directory must
# remain absent; the setting is process-local and leaves OS networking unchanged.
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djdk.net.unixdomain.tmpdir=__china_quest_tcp_fallback__".Trim()
$questGradle = Join-Path $questRoot '.toolchain\gradle-8.9\bin\gradle.bat'
Push-Location (Join-Path $questRoot 'android')
try {
    & $questGradle @Tasks --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE). Inspect the error before any retry." }
} finally { Pop-Location }
