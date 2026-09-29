$ErrorActionPreference='Stop'
$questRoot=Split-Path -Parent $PSScriptRoot
$questWeb=Join-Path $questRoot 'web'
$questNext=Join-Path $questWeb 'node_modules\next\dist\bin\next'
if (-not (Test-Path -LiteralPath (Join-Path $questWeb '.next\BUILD_ID'))) { throw 'Run npm run build in web/ before starting the dashboard.' }
$questListening=Get-NetTCPConnection -LocalPort 3210 -State Listen -ErrorAction SilentlyContinue
if ($questListening) { throw 'Port 3210 is already in use. Do not stop an unknown process; inspect the existing dashboard first.' }
$env:NEXT_TELEMETRY_DISABLED='1'
$questNode=(Get-Command node.exe -ErrorAction Stop).Source
$questLogRoot=Join-Path $questRoot '.toolchain'
New-Item -ItemType Directory -Force -Path $questLogRoot | Out-Null
$questArguments='"' + $questNext + '" start -H 127.0.0.1 -p 3210'
$questProcess=Start-Process -FilePath $questNode -ArgumentList $questArguments -WorkingDirectory $questWeb -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $questLogRoot 'dashboard.stdout.log') -RedirectStandardError (Join-Path $questLogRoot 'dashboard.stderr.log')
$questProcess.Id | Set-Content -LiteralPath (Join-Path $questLogRoot 'dashboard.pid')
Write-Output "China Quest dashboard PID $($questProcess.Id): http://127.0.0.1:3210"
