[CmdletBinding()]
param(
    [string]$Notes,
    [string]$NotesFile,
    [int]$MinSupportedVersionCode = 0,
    [string]$Repo = "Hinoaaaaaf212/pocketpass-release",
    [switch]$SkipBuild,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

function GhExitCode {
    param([string[]]$GhArgs)
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & gh @GhArgs 2>$null | Out-Null
    } finally {
        $ErrorActionPreference = $old
    }
    return $LASTEXITCODE
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $repoRoot "app\build.gradle.kts"
$apkSource = Join-Path $env:LOCALAPPDATA "PocketPass\gradle\app\outputs\apk\release\app-release.apk"
$signingProperties = Join-Path $HOME ".pocketpass\signing\signing.properties"

if (-not $Notes -and -not $NotesFile) { Fail "Pass -Notes or -NotesFile with the changelog for this release." }
if ($NotesFile -and -not (Test-Path $NotesFile)) { Fail "Notes file not found: $NotesFile" }
if (-not (Test-Path $signingProperties)) { Fail "Release signing config missing at $signingProperties - fielded devices only accept updates signed with the production key." }

$gradle = Get-Content $gradleFile -Raw
$versionCodeMatch = [regex]::Match($gradle, 'versionCode\s*=\s*(\d+)')
$versionNameMatch = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"')
if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) { Fail "Could not parse versionCode/versionName from $gradleFile" }
$versionCode = [int]$versionCodeMatch.Groups[1].Value
$versionName = $versionNameMatch.Groups[1].Value
$tag = "v$versionName"

Write-Host "Publishing PocketPass $versionName (versionCode $versionCode) as $tag to $Repo"

if ((GhExitCode @("auth", "status")) -ne 0) { Fail "gh CLI is not authenticated. Run: gh auth login" }
if ((GhExitCode @("repo", "view", $Repo)) -ne 0) { Fail "Cannot access repo $Repo with the current gh account." }
if ((GhExitCode @("release", "view", $tag, "--repo", $Repo)) -eq 0) { Fail "Release $tag already exists on $Repo. Bump versionName/versionCode first." }

try {
    $served = Invoke-RestMethod -Uri "https://links.pocketpass.xyz/updates/latest.json" -TimeoutSec 10
    if ($served.versionCode -ge $versionCode) {
        Write-Warning "Served latest.json already has versionCode $($served.versionCode); this release ($versionCode) will not be offered to anyone."
    }
} catch {
    Write-Warning "Could not read the served latest.json (continuing): $($_.Exception.Message)"
}

if (-not $SkipBuild) {
    Write-Host "Building release APK..."
    Push-Location $repoRoot
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & .\gradlew.bat :app:assembleRelease 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { Fail "assembleRelease failed." }
    } finally {
        $ErrorActionPreference = $previousPreference
        Pop-Location
    }
}
if (-not (Test-Path $apkSource)) { Fail "Release APK not found at $apkSource" }

$staging = Join-Path $env:TEMP "pocketpass-release-$versionCode"
if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
New-Item -ItemType Directory -Path $staging | Out-Null
$stagedApk = Join-Path $staging "PocketPass.apk"
Copy-Item $apkSource $stagedApk

$zipalign = Get-ChildItem (Join-Path $repoRoot ".toolchains\android-sdk\build-tools") -Filter zipalign.exe -Recurse | Sort-Object { [version]$_.Directory.Name } -Descending | Select-Object -First 1
if (-not $zipalign) { Fail "zipalign.exe not found under .toolchains\android-sdk\build-tools" }
$alignPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & $zipalign.FullName -c -P 16 -v 4 $stagedApk 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "APK is not 16 KB page aligned (zipalign -c -P 16 failed); do not ship it." }
} finally {
    $ErrorActionPreference = $alignPreference
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($stagedApk)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -notmatch '^lib/(arm64-v8a|x86_64)/.*\.so$') { continue }
        $stream = $entry.Open()
        $bytes = New-Object byte[] $entry.Length
        $read = 0
        while ($read -lt $bytes.Length) {
            $n = $stream.Read($bytes, $read, $bytes.Length - $read)
            if ($n -le 0) { break }
            $read += $n
        }
        $stream.Dispose()
        if ($bytes[4] -ne 2) { continue }
        $phoff = [BitConverter]::ToInt64($bytes, 0x20)
        $phentsize = [BitConverter]::ToUInt16($bytes, 0x36)
        $phnum = [BitConverter]::ToUInt16($bytes, 0x38)
        for ($i = 0; $i -lt $phnum; $i++) {
            $base = $phoff + $i * $phentsize
            $type = [BitConverter]::ToUInt32($bytes, $base)
            if ($type -ne 1) { continue }
            $align = [BitConverter]::ToUInt64($bytes, $base + 0x30)
            if ($align -lt 16384) { Fail "$($entry.FullName) has a PT_LOAD segment aligned to $align bytes; 16 KB devices cannot load it." }
        }
    }
} finally {
    $archive.Dispose()
}
Write-Host "16 KB page alignment verified (zip entries and ELF load segments)."

$sha = (Get-FileHash -Algorithm SHA256 $stagedApk).Hash.ToLowerInvariant()
$size = (Get-Item $stagedApk).Length
$updateJson = [ordered]@{
    schemaVersion = 1
    versionCode   = $versionCode
    versionName   = $versionName
    apkSha256     = $sha
    apkSizeBytes  = $size
}
if ($MinSupportedVersionCode -gt 0) { $updateJson.minSupportedVersionCode = $MinSupportedVersionCode }
$updateJsonPath = Join-Path $staging "update.json"
$updateJson | ConvertTo-Json | Out-File -FilePath $updateJsonPath -Encoding ascii

$notesPath = Join-Path $staging "notes.md"
if ($NotesFile) { Copy-Item $NotesFile $notesPath } else { $Notes | Out-File -FilePath $notesPath -Encoding utf8 }

Write-Host "Staged $([math]::Round($size / 1MB, 1)) MB APK, sha256 $sha"
if ($DryRun) {
    Write-Host "Dry run: staged files in $staging - no release created."
    Get-Content $updateJsonPath
    exit 0
}

$hasCommits = (GhExitCode @("api", "repos/$Repo/commits?per_page=1")) -eq 0
if (-not $hasCommits) {
    Write-Host "Bootstrapping empty release repo with an initial commit..."
    $readme = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("# PocketPass releases`n`nAPK releases for the PocketPass app. Each release carries PocketPass.apk and update.json assets consumed by the in-app updater.`n"))
    gh api --method PUT "repos/$Repo/contents/README.md" -f message="Bootstrap release repository" -f content=$readme | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "Could not bootstrap the empty repo." }
}

gh release create $tag --repo $Repo --title "PocketPass $versionName" --notes-file $notesPath $stagedApk $updateJsonPath
if ($LASTEXITCODE -ne 0) { Fail "gh release create failed." }

Write-Host ""
Write-Host "Release $tag published. The VM poller picks it up within ~6 minutes;"
Write-Host "verify with: curl https://links.pocketpass.xyz/updates/latest.json"
