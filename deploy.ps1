# Build, install and launch the debug APK on the connected phone.
#
#   .\deploy.ps1            build, install, launch
#   .\deploy.ps1 -Log       ...then stream this app's logcat
#
# Debug build on purpose. Debug and release share one package name so that only
# one Google OAuth client is ever needed.

param(
    [switch]$Log,
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$adb      = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk      = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
$package  = "net.shehane.watching"
$activity = "net.shehane.watching/net.shehane.watching.MainActivity"

# Android Studio's bundled JDK is the one this project is known to build with.
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }

$target = @()
if ($Serial -ne "") { $target = @("-s", $Serial) }

# --- device first, so a failed build is never blamed on a missing phone ---
Write-Host "Checking for a device..." -ForegroundColor Cyan
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\S" }
$ready = $devices | Where-Object { $_ -match "\sdevice$" }

if (-not $ready) {
    Write-Host "No ready device." -ForegroundColor Red
    if ($devices) {
        $devices | ForEach-Object { Write-Host "  $_" }
        Write-Host "unauthorized: accept the USB debugging prompt on the phone."
        Write-Host "offline: unlock or replug it, or run adb kill-server."
    } else {
        Write-Host "Nothing attached. Plug in over USB, turn on Developer options -> USB"
        Write-Host "debugging, and make sure the cable carries data rather than just power."
    }
    exit 1
}
if (($ready | Measure-Object).Count -gt 1 -and $Serial -eq "") {
    Write-Host "More than one device is ready. Pass -Serial <serial>:" -ForegroundColor Yellow
    $ready | ForEach-Object { Write-Host "  $_" }
    exit 1
}

# --- build ---
Write-Host "Building..." -ForegroundColor Cyan
.\gradlew.bat :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed." -ForegroundColor Red; exit 1 }

# --- install ---
# -r keeps the library. A plain install would wipe library.json.
Write-Host "Installing (keeping your library)..." -ForegroundColor Cyan
& $adb @target install -r $apk
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed." -ForegroundColor Red; exit 1 }

# --- launch ---
# force-stop first, or am start can resume the old process and show old code.
Write-Host "Launching..." -ForegroundColor Cyan
& $adb @target shell am force-stop $package
Start-Sleep -Milliseconds 400
# am start writes a harmless "already top-most" warning to stderr when the app
# survived the force-stop. That is not a failure, so it must not stop the script.
$ErrorActionPreference = "Continue"
& $adb @target shell am start -n "$package/net.shehane.watching.MainActivity" | Out-Null
$ErrorActionPreference = "Stop"

Write-Host "Done." -ForegroundColor Green

if ($Log) {
    Write-Host "Streaming logcat. Ctrl+C to stop." -ForegroundColor Cyan
    & $adb @target logcat -c
    & $adb @target logcat | Select-String "shehane|AndroidRuntime|FATAL"
}
