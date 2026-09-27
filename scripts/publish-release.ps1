[CmdletBinding()]
param(
    [string]$Notes,
    [string]$NotesFile,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int]$MinSupportedVersionCode,
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

function Invoke-Tool {
    param([string]$Path, [string[]]$Arguments)
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& $Path @Arguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $old
    }
    if ($exitCode -ne 0) { Fail "$(Split-Path -Leaf $Path) failed with exit code $($exitCode):`n$($output -join "`n")" }
    return $output
}

$releasePackage = "com.pocketpass.app"
$releaseCertSha256 = "8aebd2a225025c6ae31f089a357f98c8eb61b5db1e00274616652fda3bdaf358"
$productionSupabaseUrl = "https://api.pocketpass.xyz"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $repoRoot "app\build.gradle.kts"
$apkSource = Join-Path $env:LOCALAPPDATA "PocketPass\gradle\app\outputs\apk\release\app-release.apk"
$buildConfigFile = Join-Path $env:LOCALAPPDATA "PocketPass\gradle\app\generated\source\buildConfig\release\com\pocketpass\app\BuildConfig.java"
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
if ($MinSupportedVersionCode -gt $versionCode) { Fail "-MinSupportedVersionCode $MinSupportedVersionCode is above this release's versionCode $versionCode." }

Write-Host "Publishing PocketPass $versionName (versionCode $versionCode, floor $MinSupportedVersionCode) as $tag to $Repo"

if ((GhExitCode @("auth", "status")) -ne 0) { Fail "gh CLI is not authenticated. Run: gh auth login" }
if ((GhExitCode @("repo", "view", $Repo)) -ne 0) { Fail "Cannot access repo $Repo with the current gh account." }
if ((GhExitCode @("release", "view", $tag, "--repo", $Repo)) -eq 0) { Fail "Release $tag already exists on $Repo. Bump versionName/versionCode first." }

try {
    $served = Invoke-RestMethod -Uri "https://links.pocketpass.xyz/updates/latest.json" -TimeoutSec 10
    if ($served.versionCode -ge $versionCode) {
        Write-Warning "Served latest.json already has versionCode $($served.versionCode); this release ($versionCode) will not be offered to anyone."
    }
    if ($served.minSupportedVersionCode -and $MinSupportedVersionCode -lt $served.minSupportedVersionCode) {
        Write-Warning "Served latest.json has floor $($served.minSupportedVersionCode); this release lowers it to $MinSupportedVersionCode."
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
        & .\gradlew.bat :app:assembleRelease -PPOCKETPASS_REQUIRE_FIREBASE=true 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { Fail "assembleRelease failed." }
    } finally {
        $ErrorActionPreference = $previousPreference
        Pop-Location
    }
}
if (-not (Test-Path $apkSource)) { Fail "Release APK not found at $apkSource" }
if (-not (Test-Path $buildConfigFile)) { Fail "Release BuildConfig not found at $buildConfigFile" }
if ((Get-Item $buildConfigFile).LastWriteTimeUtc -gt (Get-Item $apkSource).LastWriteTimeUtc) { Fail "The release BuildConfig is newer than $apkSource. Rebuild without -SkipBuild." }

$staging = Join-Path $env:TEMP "pocketpass-release-$versionCode-$([Guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $staging | Out-Null
$stagedApk = Join-Path $staging "PocketPass.apk"
Copy-Item $apkSource $stagedApk

$zipalign = Get-ChildItem (Join-Path $repoRoot ".toolchains\android-sdk\build-tools") -Filter zipalign.exe -Recurse | Sort-Object { [version]$_.Directory.Name } -Descending | Select-Object -First 1
if (-not $zipalign) { Fail "zipalign.exe not found under .toolchains\android-sdk\build-tools" }
$aapt2 = Join-Path $zipalign.Directory.FullName "aapt2.exe"
$apksigner = Join-Path $zipalign.Directory.FullName "apksigner.bat"
if (-not (Test-Path $aapt2)) { Fail "aapt2.exe not found next to $($zipalign.FullName)" }
if (-not (Test-Path $apksigner)) { Fail "apksigner.bat not found next to $($zipalign.FullName)" }
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

$buildConfig = Get-Content $buildConfigFile -Raw
function Get-BuildConfigValue([string]$Name) {
    $match = [regex]::Match($buildConfig, "\b$Name\s*=\s*(""(?<text>[^""]*)""|(?<raw>[^;]+));")
    if (-not $match.Success) { Fail "BuildConfig has no $Name field." }
    if ($match.Groups["text"].Success) { return $match.Groups["text"].Value }
    return $match.Groups["raw"].Value.Trim()
}
$publishableKey = Get-BuildConfigValue "SUPABASE_PUBLISHABLE_KEY"
if ((Get-BuildConfigValue "APPLICATION_ID") -ne $releasePackage) { Fail "BuildConfig APPLICATION_ID is not $releasePackage." }
if ((Get-BuildConfigValue "BUILD_TYPE") -ne "release") { Fail "BuildConfig is not a release build." }
if ((Get-BuildConfigValue "BACKEND_ENABLED") -ne "true") { Fail "BuildConfig BACKEND_ENABLED is not true; this is a fixture build. Check ~/.gradle/gradle.properties and GRADLE_USER_HOME." }
if ((Get-BuildConfigValue "FIREBASE_CONFIGURED") -ne "true") { Fail "BuildConfig FIREBASE_CONFIGURED is not true." }
if ((Get-BuildConfigValue "SUPABASE_URL") -ne $productionSupabaseUrl) { Fail "BuildConfig SUPABASE_URL is not $productionSupabaseUrl." }
if ($publishableKey -notmatch '^sb_publishable_\S+$') { Fail "BuildConfig SUPABASE_PUBLISHABLE_KEY is empty or not a publishable key; this is a fixture build." }
if (((Get-BuildConfigValue "RELEASE_CERT_SHA256") -replace ':', '').ToLowerInvariant() -ne $releaseCertSha256) { Fail "BuildConfig RELEASE_CERT_SHA256 does not match the production certificate." }
if ((Get-BuildConfigValue "VERSION_CODE") -ne "$versionCode") { Fail "BuildConfig VERSION_CODE does not match $gradleFile ($versionCode)." }
if ((Get-BuildConfigValue "VERSION_NAME") -ne $versionName) { Fail "BuildConfig VERSION_NAME does not match $gradleFile ($versionName)." }

$badging = Invoke-Tool $aapt2 @("dump", "badging", $stagedApk)
$packageLine = $badging | Where-Object { $_ -like "package:*" } | Select-Object -First 1
$badge = [regex]::Match("$packageLine", "name='([^']*)' versionCode='([^']*)' versionName='([^']*)'")
if (-not $badge.Success) { Fail "aapt2 dump badging printed no package line." }
if ($badge.Groups[1].Value -ne $releasePackage) { Fail "APK package is $($badge.Groups[1].Value), not $releasePackage." }
if ($badge.Groups[2].Value -ne "$versionCode") { Fail "APK versionCode is $($badge.Groups[2].Value), but $gradleFile says $versionCode. Rebuild without -SkipBuild." }
if ($badge.Groups[3].Value -ne $versionName) { Fail "APK versionName is $($badge.Groups[3].Value), but $gradleFile says $versionName. Rebuild without -SkipBuild." }

$signers = Invoke-Tool $apksigner @("verify", "--print-certs", $stagedApk)
$digests = @($signers | Where-Object { $_ -match '^Signer #\d+ certificate SHA-256 digest: ' } | ForEach-Object { ($_ -replace '^Signer #\d+ certificate SHA-256 digest: ', '').Trim().ToLowerInvariant() })
if ($digests.Count -ne 1 -or $digests[0] -ne $releaseCertSha256) { Fail "APK is not signed with the production certificate only (found: $($digests -join ', '))." }

$latin1 = [System.Text.Encoding]::GetEncoding(28591)
$keyInDex = $false
$urlInDex = $false
$archive = [System.IO.Compression.ZipFile]::OpenRead($stagedApk)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -notmatch '^classes\d*\.dex$') { continue }
        $buffer = New-Object System.IO.MemoryStream
        $stream = $entry.Open()
        try { $stream.CopyTo($buffer) } finally { $stream.Dispose() }
        $dex = $latin1.GetString($buffer.ToArray())
        if ($dex.IndexOf($publishableKey, [StringComparison]::Ordinal) -ge 0) { $keyInDex = $true }
        if ($dex.IndexOf($productionSupabaseUrl, [StringComparison]::Ordinal) -ge 0) { $urlInDex = $true }
    }
} finally {
    $archive.Dispose()
}
if (-not $keyInDex -or -not $urlInDex) { Fail "The APK's code does not carry the production backend URL and publishable key from BuildConfig." }
Write-Host "Verified $releasePackage $versionName ($versionCode), production backend, production signing certificate."

$sha = (Get-FileHash -Algorithm SHA256 $stagedApk).Hash.ToLowerInvariant()
$size = (Get-Item $stagedApk).Length
$updateJson = [ordered]@{
    schemaVersion           = 1
    versionCode             = $versionCode
    versionName             = $versionName
    apkSha256               = $sha
    apkSizeBytes            = $size
    minSupportedVersionCode = $MinSupportedVersionCode
}
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
