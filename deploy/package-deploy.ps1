$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$PkgDir = Join-Path $Root "deploy/package"
$OutDir = Join-Path $PkgDir "s1000d-viewer"

Write-Output "[1/4] Building backend jar..."
Push-Location $Root
try {
  .\gradlew.bat :application:bootJar -x test
} finally {
  Pop-Location
}

Write-Output "[2/4] Building webapp dist..."
Push-Location (Join-Path $Root "webapp")
try {
  npm.cmd ci
  npm.cmd run build
} finally {
  Pop-Location
}

Write-Output "[3/4] Assembling package..."
if (Test-Path $OutDir) {
  Remove-Item -Recurse -Force $OutDir
}
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "app") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "webapp") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "deploy") | Out-Null

$Jar = Get-ChildItem (Join-Path $Root "application/build/libs") -Filter *.jar | Select-Object -First 1
Copy-Item $Jar.FullName (Join-Path $OutDir "app/application.jar")
Copy-Item -Recurse (Join-Path $Root "webapp/dist") (Join-Path $OutDir "webapp/dist")
Copy-Item (Join-Path $Root "deploy/.env.example") (Join-Path $OutDir "deploy/.env.example")

@'
#!/usr/bin/env bash
set -euo pipefail

mkdir -p logs
nohup java -jar app/application.jar > logs/backend.log 2>&1 &
nohup npx serve -s webapp/dist -l 5173 > logs/webapp.log 2>&1 &
echo "Started backend on http://localhost:8080 and webapp on http://localhost:5173"
'@ | Set-Content -NoNewline (Join-Path $OutDir "start-prod.sh")

@'
New-Item -ItemType Directory -Force logs | Out-Null
Start-Process java -ArgumentList '-jar','app/application.jar' -RedirectStandardOutput 'logs/backend.log' -RedirectStandardError 'logs/backend.err.log'
Start-Process npx -ArgumentList 'serve','-s','webapp/dist','-l','5173' -RedirectStandardOutput 'logs/webapp.log' -RedirectStandardError 'logs/webapp.err.log'
Write-Output "Started backend on http://localhost:8080 and webapp on http://localhost:5173"
'@ | Set-Content -NoNewline (Join-Path $OutDir "start-prod.ps1")

Write-Output "[4/4] Creating archive..."
$ZipPath = Join-Path $PkgDir "s1000d-viewer-deploy.zip"
if (Test-Path $ZipPath) {
  Remove-Item -Force $ZipPath
}
Compress-Archive -Path (Join-Path $OutDir "*") -DestinationPath $ZipPath

Write-Output "Package ready:"
Write-Output " - Folder: $OutDir"
Write-Output " - Archive: $ZipPath"
