param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$toolchains = Join-Path $ProjectRoot ".toolchains"
$downloads = Join-Path $toolchains "downloads"
$jdkContainer = Join-Path $toolchains "jdk17"
$androidSdk = Join-Path $toolchains "android-sdk"
$commandLineTools = Join-Path $androidSdk "cmdline-tools\latest"

New-Item -ItemType Directory -Force -Path $downloads, $toolchains, $androidSdk | Out-Null

$jdkArchive = Join-Path $downloads "temurin-jdk17.zip"
if (-not (Test-Path -LiteralPath $jdkArchive)) {
    & curl.exe -L --fail --show-error -o $jdkArchive "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to download the portable Temurin JDK."
    }
}

if (-not (Test-Path -LiteralPath $jdkContainer)) {
    New-Item -ItemType Directory -Force -Path $jdkContainer | Out-Null
    Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkContainer
}

$jdkHome = Get-ChildItem -LiteralPath $jdkContainer -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\java.exe") } |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $jdkHome) {
    throw "Could not locate java.exe in the portable JDK."
}

$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome "bin") + [IO.Path]::PathSeparator + $env:Path

$toolsArchive = Join-Path $downloads "android-command-line-tools.zip"
if (-not (Test-Path -LiteralPath $toolsArchive)) {
    & curl.exe -L --fail --show-error -o $toolsArchive "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to download Android command-line tools."
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $commandLineTools "bin\sdkmanager.bat"))) {
    $expandedTools = Join-Path $toolchains "expanded-command-line-tools"
    if (-not (Test-Path -LiteralPath $expandedTools)) {
        New-Item -ItemType Directory -Force -Path $expandedTools | Out-Null
        Expand-Archive -LiteralPath $toolsArchive -DestinationPath $expandedTools
    }
    New-Item -ItemType Directory -Force -Path $commandLineTools | Out-Null
    Copy-Item -Path (Join-Path $expandedTools "cmdline-tools\*") -Destination $commandLineTools -Recurse -Force
}

$sdkManager = Join-Path $commandLineTools "bin\sdkmanager.bat"
$licenseAnswers = 1..40 | ForEach-Object { "y" }
$licenseAnswers | & $sdkManager "--sdk_root=$androidSdk" "--licenses" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Android SDK license acceptance failed."
}

& $sdkManager `
    "--sdk_root=$androidSdk" `
    "platform-tools" `
    "platforms;android-36" `
    "build-tools;36.0.0"
if ($LASTEXITCODE -ne 0) {
    throw "Android SDK package installation failed."
}

Write-Output "JAVA_HOME=$jdkHome"
Write-Output "ANDROID_HOME=$androidSdk"
