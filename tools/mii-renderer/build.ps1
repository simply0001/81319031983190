[CmdletBinding()]
param(
    [string]$UpstreamSource = "",
    [string]$BunExecutable = "bun",
    [string[]]$BunPrefixArguments = @(),
    [switch]$UpdateBundle
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$toolRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $toolRoot "..\.."))
$manifestPath = Join-Path $toolRoot "provenance.json"
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$workRoot = Join-Path $temporaryRoot ("pocketpass-mii-renderer-" + [Guid]::NewGuid().ToString("N"))

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-Hash {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Label
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label is missing: $Path"
    }
    $actual = Get-Sha256 -Path $Path
    if ($actual -ne $Expected.ToLowerInvariant()) {
        throw "$Label checksum mismatch. Expected $Expected, got $actual."
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [string]$WorkingDirectory = ""
    )
    if ($WorkingDirectory) {
        Push-Location -LiteralPath $WorkingDirectory
    }
    try {
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Executable exited with code $LASTEXITCODE."
        }
    } finally {
        if ($WorkingDirectory) {
            Pop-Location
        }
    }
}

function Invoke-Bun {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [string]$WorkingDirectory = ""
    )
    $allArguments = @($BunPrefixArguments) + $Arguments
    Invoke-Checked -Executable $BunExecutable -Arguments $allArguments -WorkingDirectory $WorkingDirectory
}

try {
    foreach ($source in $manifest.pocketPassSources) {
        $sourcePath = Join-Path $toolRoot $source.path
        Assert-Hash -Path $sourcePath -Expected $source.sha256 -Label "PocketPass renderer source '$($source.path)'"
    }

    $bunVersionArguments = @($BunPrefixArguments) + @("--version")
    $bunVersionOutput = & $BunExecutable @bunVersionArguments
    if ($LASTEXITCODE -ne 0) {
        throw "$BunExecutable could not report its version."
    }
    $bunVersion = ($bunVersionOutput | Select-Object -Last 1).ToString().Trim()
    if ($bunVersion -ne $manifest.toolchain.bunVersion) {
        throw "Bun $($manifest.toolchain.bunVersion) is required for byte-for-byte output; found $bunVersion."
    }

    $cloneSource = if ([string]::IsNullOrWhiteSpace($UpstreamSource)) {
        $manifest.upstream.repository
    } elseif (Test-Path -LiteralPath $UpstreamSource) {
        (Resolve-Path -LiteralPath $UpstreamSource).Path
    } else {
        $UpstreamSource
    }

    Invoke-Checked -Executable "git" -Arguments @(
        "clone",
        "--no-checkout",
        "--quiet",
        $cloneSource,
        $workRoot
    )
    Invoke-Checked -Executable "git" -Arguments @(
        "-C",
        $workRoot,
        "checkout",
        "--detach",
        "--quiet",
        $manifest.upstream.commit
    )

    $head = (& git -C $workRoot rev-parse HEAD).ToString().Trim()
    if ($LASTEXITCODE -ne 0 -or $head -ne $manifest.upstream.commit) {
        throw "Pinned upstream checkout failed. Expected $($manifest.upstream.commit), got $head."
    }

    $pocketPassSourceRoot = Join-Path $workRoot "src\pocketpass"
    New-Item -ItemType Directory -Path $pocketPassSourceRoot -Force | Out-Null
    foreach ($name in @("renderer.ts", "RendererFFL.ts", "RendererSettings.ts", "RendererTypes.ts")) {
        Copy-Item -LiteralPath (Join-Path $toolRoot "src\$name") -Destination (Join-Path $pocketPassSourceRoot $name)
    }

    $patchPath = Join-Path $toolRoot "patches\pocketpass-renderer.patch"
    Invoke-Checked -Executable "git" -Arguments @("-C", $workRoot, "apply", "--check", $patchPath)
    Invoke-Checked -Executable "git" -Arguments @("-C", $workRoot, "apply", $patchPath)

    foreach ($asset in $manifest.upstreamRuntimeAssets) {
        Assert-Hash `
            -Path (Join-Path $workRoot $asset.upstreamPath) `
            -Expected $asset.sha256 `
            -Label "Pinned upstream asset '$($asset.upstreamPath)'"
    }
    foreach ($asset in $manifest.pocketPassRuntimeAssets) {
        Assert-Hash `
            -Path (Join-Path $workRoot $asset.derivedFrom.upstreamPath) `
            -Expected $asset.derivedFrom.sha256 `
            -Label "Pinned upstream base of '$($asset.packagedPath)'"
    }

    Invoke-Bun -Arguments @("install", "--frozen-lockfile") -WorkingDirectory $workRoot

    $builtBundle = Join-Path $workRoot "pocketpass-dist\renderer.js"
    New-Item -ItemType Directory -Path (Split-Path -Parent $builtBundle) -Force | Out-Null
    Invoke-Bun -Arguments @(
        "build",
        (Join-Path $workRoot "src\pocketpass\renderer.ts"),
        "--outfile",
        $builtBundle,
        "--target",
        "browser"
    ) -WorkingDirectory $workRoot
    Assert-Hash -Path $builtBundle -Expected $manifest.bundle.sha256 -Label "Rebuilt renderer bundle"

    if ($UpdateBundle) {
        $packagedBundle = Join-Path $projectRoot $manifest.bundle.path
        New-Item -ItemType Directory -Path (Split-Path -Parent $packagedBundle) -Force | Out-Null
        Copy-Item -LiteralPath $builtBundle -Destination $packagedBundle -Force

        foreach ($asset in $manifest.upstreamRuntimeAssets) {
            $packagedAsset = Join-Path $projectRoot $asset.packagedPath
            New-Item -ItemType Directory -Path (Split-Path -Parent $packagedAsset) -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $workRoot $asset.upstreamPath) -Destination $packagedAsset -Force
        }
    }

    Assert-Hash `
        -Path (Join-Path $projectRoot $manifest.bundle.path) `
        -Expected $manifest.bundle.sha256 `
        -Label "Packaged renderer bundle"
    foreach ($asset in $manifest.upstreamRuntimeAssets) {
        Assert-Hash `
            -Path (Join-Path $projectRoot $asset.packagedPath) `
            -Expected $asset.sha256 `
            -Label "Packaged runtime asset '$($asset.packagedPath)'"
    }
    foreach ($asset in $manifest.pocketPassRuntimeAssets) {
        Assert-Hash `
            -Path (Join-Path $projectRoot $asset.packagedPath) `
            -Expected $asset.sha256 `
            -Label "Packaged PocketPass runtime asset '$($asset.packagedPath)'"
    }
    Assert-Hash `
        -Path (Join-Path $projectRoot $manifest.fflResource.packagedPath) `
        -Expected $manifest.fflResource.sha256 `
        -Label "Packaged FFL resource"

    Write-Host "PocketPass Mii renderer is reproducible at $($manifest.bundle.sha256)."
    Write-Host "Pinned upstream: $($manifest.upstream.commit)"
} finally {
    if (Test-Path -LiteralPath $workRoot) {
        $resolvedWorkRoot = [System.IO.Path]::GetFullPath($workRoot)
        $allowedPrefix = $temporaryRoot.TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
        if (-not $resolvedWorkRoot.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unexpected build directory: $resolvedWorkRoot"
        }
        Remove-Item -LiteralPath $resolvedWorkRoot -Recurse -Force
    }
}
