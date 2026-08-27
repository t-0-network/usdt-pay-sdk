param(
    [Parameter(ValueFromRemainingArguments)]
    [string[]]$InitArgs
)

$ErrorActionPreference = "Stop"

$Repo = "t-0-network/usdt-pay-sdk"
$BinaryName = "usdt-pay"

$Arch = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "arm64" } else { "amd64" }
$Asset = "$BinaryName-windows-${Arch}.exe"
$Url = "https://github.com/$Repo/releases/latest/download/$Asset"

$InstallDir = Join-Path $env:LOCALAPPDATA $BinaryName
$InstallPath = Join-Path $InstallDir "$BinaryName.exe"

Write-Host "Downloading $Asset..."

if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

try {
    Invoke-WebRequest -Uri $Url -OutFile $InstallPath -UseBasicParsing
} catch {
    Write-Error "Failed to download: $_"
    exit 1
}

Write-Host "Installed to $InstallPath"

$UserPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($UserPath -notlike "*$InstallDir*") {
    [Environment]::SetEnvironmentVariable("Path", "$UserPath;$InstallDir", "User")
    $env:Path = "$env:Path;$InstallDir"
    Write-Host "Added $InstallDir to user PATH (restart your terminal for it to take effect)"
}

if ($InitArgs.Count -gt 0) {
    Write-Host ""
    $exitCode = 0
    & $InstallPath @InitArgs
    $exitCode = $LASTEXITCODE
    exit $exitCode
} else {
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  $BinaryName init --lang=<language> --role=<role> <project-name>"
}
