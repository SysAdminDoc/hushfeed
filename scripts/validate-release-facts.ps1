<#
.SYNOPSIS
    Check public release facts against the generated patch list.

.DESCRIPTION
    The generated patches-list.json is the local source for the release version, target
    package, target version and patch count. This check makes README.md, patches-bundle.json
    and the recorded runtime test count agree before a release is published.
#>
[CmdletBinding()]
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$VerifyPublishedAsset,
    [string]$ArtifactPath
)

$ErrorActionPreference = 'Stop'

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required file is missing: $Path"
    }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        throw "Could not read JSON from ${Path}: $($_.Exception.Message)"
    }
}

function Require-Match {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Description
    )
    if ($Text -notmatch $Pattern) {
        throw "${Description} does not match the generated release facts."
    }
}

$rootPath = (Resolve-Path -LiteralPath $Root).Path
$patchListPath = Join-Path $rootPath 'patches-list.json'
$bundlePath = Join-Path $rootPath 'patches-bundle.json'
$propertiesPath = Join-Path $rootPath 'gradle.properties'
$readmePath = Join-Path $rootPath 'README.md'

$patchList = Read-JsonFile $patchListPath
$bundle = Read-JsonFile $bundlePath
$readme = Get-Content -LiteralPath $readmePath -Raw
$properties = Get-Content -LiteralPath $propertiesPath -Raw

$sourceVersion = [string]$patchList.version
if ($sourceVersion -notmatch '^v\d+\.\d+\.\d+$') {
    throw "patches-list.json has an invalid version: $sourceVersion"
}
$releaseVersion = $sourceVersion.Substring(1)
$propertyMatch = [regex]::Match($properties, '(?m)^\s*version\s*=\s*(\S+)\s*$')
if (-not $propertyMatch.Success -or $propertyMatch.Groups[1].Value -ne $releaseVersion) {
    throw "gradle.properties does not match $sourceVersion."
}

$patches = @($patchList.patches)
if ($patches.Count -eq 0) { throw 'patches-list.json contains no patches.' }
$patchCount = $patches.Count

$targets = @{}
foreach ($patch in $patches) {
    foreach ($property in $patch.compatiblePackages.PSObject.Properties) {
        $versions = @($property.Value | ForEach-Object { [string]$_ })
        if (-not $targets.ContainsKey($property.Name)) { $targets[$property.Name] = @() }
        $targets[$property.Name] += $versions
    }
}
$targetPackages = @($targets.Keys | Sort-Object)
if ($targetPackages.Count -ne 1) {
    throw "Expected one compatible package, found $($targetPackages -join ', ')."
}
$targetPackage = $targetPackages[0]
$targetVersions = @($targets[$targetPackage] | Sort-Object -Unique)
if ($targetVersions.Count -ne 1) {
    throw "Expected one compatible version for $targetPackage, found $($targetVersions -join ', ')."
}
$targetVersion = $targetVersions[0]

if ([string]$bundle.version -ne $releaseVersion) {
    throw "patches-bundle.json version does not match $sourceVersion."
}
Require-Match -Text ([string]$bundle.download_url) -Pattern "/v$([regex]::Escape($releaseVersion))/patches-$([regex]::Escape($releaseVersion))\.mpp$" -Description 'patches-bundle.json download URL'
Require-Match -Text $readme -Pattern "\b$patchCount patches\b" -Description 'README patch count'
Require-Match -Text $readme -Pattern ([regex]::Escape($targetPackage)) -Description 'README package name'
Require-Match -Text $readme -Pattern "TikTok\s+$([regex]::Escape($targetVersion))" -Description 'README target version'
Require-Match -Text ([string]$bundle.description) -Pattern "\b$patchCount patches\b" -Description 'bundle description patch count'
Require-Match -Text ([string]$bundle.description) -Pattern ([regex]::Escape($targetVersion)) -Description 'bundle description target version'

$testRoot = Join-Path $rootPath 'extensions/tiktok/build/test-results/testDebugUnitTest'
$testFiles = @(Get-ChildItem -LiteralPath $testRoot -Filter '*.xml' -File -ErrorAction SilentlyContinue)
if ($testFiles.Count -eq 0) {
    throw "No runtime test results found under $testRoot. Run :extensions:tiktok:test first."
}

# Gradle leaves the previous run's XML in place, so results from before the last edit satisfy
# every check below and a release can be validated against code that was never tested.
#
# Only the trees the test task actually reads are compared. patches/src is not one of them:
# :extensions:tiktok:test depends on the shared library and the stub, so editing a patch leaves
# the task up to date, no XML is rewritten, and comparing against it would refuse every release
# from then on with no rerun that could clear it.
#
# The newest result is the one to compare. Gradle never removes the XML of a test class that was
# deleted or renamed, and that file keeps its original timestamp through every later run, so
# taking the oldest would refuse forever after the first class is dropped.
$sourceRoots = @('extensions/tiktok/src', 'extensions/shared/library/src') |
    ForEach-Object { Join-Path $rootPath $_ } |
    Where-Object { Test-Path -LiteralPath $_ }
$newestSource = $sourceRoots |
    ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -File -ErrorAction SilentlyContinue } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if ($null -ne $newestSource) {
    $newestResult = $testFiles | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($newestResult.LastWriteTimeUtc -lt $newestSource.LastWriteTimeUtc) {
        throw ("Runtime test results are older than the sources. The newest result " +
            "$($newestResult.Name) was written $($newestResult.LastWriteTimeUtc.ToString('u')) but " +
            "$($newestSource.FullName) changed $($newestSource.LastWriteTimeUtc.ToString('u')). " +
            'Run :extensions:tiktok:test again.')
    }
}
$testCount = 0
foreach ($file in $testFiles) {
    try {
        $results = [xml](Get-Content -LiteralPath $file.FullName -Raw)
    } catch {
        throw "Could not read test results from $($file.FullName): $($_.Exception.Message)"
    }
    foreach ($suite in @($results.testsuite)) {
        $failed = [int]$suite.failures
        $errors = [int]$suite.errors
        $skipped = [int]$suite.skipped
        if ($failed -gt 0 -or $errors -gt 0 -or $skipped -gt 0) {
            throw "Runtime test suite $($file.Name) has failures=$failed, errors=$errors, skipped=$skipped."
        }
    }
    $testCount += @($results.testsuite.testcase).Count
}
Require-Match -Text ([string]$bundle.description) -Pattern "\b$testCount runtime tests passed\b" -Description 'bundle description test count'

if ($VerifyPublishedAsset) {
    if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
        $ArtifactPath = Join-Path $rootPath "patches/build/libs/patches-$releaseVersion.mpp"
    }
    if (-not (Test-Path -LiteralPath $ArtifactPath -PathType Leaf)) {
        throw "The local release artifact is missing: $ArtifactPath"
    }

    $assetUri = [Uri]$bundle.download_url
    if ($assetUri.Scheme -ne 'https') {
        throw "The published bundle URL must use HTTPS: $($bundle.download_url)"
    }
    $assetName = [IO.Path]::GetFileName($assetUri.AbsolutePath)
    if ($assetName -ne "patches-$releaseVersion.mpp") {
        throw "The published bundle URL names $assetName instead of patches-$releaseVersion.mpp."
    }

    $temporaryArtifact = Join-Path ([IO.Path]::GetTempPath()) ("hushfeed-$([Guid]::NewGuid()).mpp")
    try {
        try {
            $assetResponse = Invoke-WebRequest -Uri $assetUri -OutFile $temporaryArtifact -MaximumRedirection 5 -TimeoutSec 60 -PassThru
        } catch {
            throw "Could not download the indexed bundle URL: $($_.Exception.Message)"
        }
        if ($assetResponse.StatusCode -ne 200) {
            throw "The indexed bundle URL returned HTTP $($assetResponse.StatusCode)."
        }

        $localHash = (Get-FileHash -LiteralPath $ArtifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $publishedHash = (Get-FileHash -LiteralPath $temporaryArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($localHash -ne $publishedHash) {
            throw "The hosted bundle hash $publishedHash does not match the local artifact hash $localHash."
        }

        $checksumUri = [Uri]::new($assetUri, 'SHA256SUMS.txt')
        try {
            $checksumResponse = Invoke-WebRequest -Uri $checksumUri -MaximumRedirection 5 -TimeoutSec 60
        } catch {
            throw "Could not download the hosted SHA256SUMS.txt: $($_.Exception.Message)"
        }
        if ($checksumResponse.StatusCode -ne 200) {
            throw "The hosted SHA256SUMS.txt returned HTTP $($checksumResponse.StatusCode)."
        }
        $checksumText = [Text.Encoding]::UTF8.GetString([byte[]]$checksumResponse.Content)
        $checksumMatch = [regex]::Match(
            $checksumText,
            "(?im)^\s*([0-9a-f]{64})\s+\*?$([regex]::Escape($assetName))\s*$"
        )
        if (-not $checksumMatch.Success) {
            throw "SHA256SUMS.txt has no entry for $assetName."
        }
        $listedHash = $checksumMatch.Groups[1].Value.ToLowerInvariant()
        if ($listedHash -ne $publishedHash) {
            throw "SHA256SUMS.txt lists $listedHash for $assetName, but the hosted artifact is $publishedHash."
        }
        Write-Host ("[release] verified " + $assetName + " from the indexed URL; sha256=" + $publishedHash)
    } finally {
        Remove-Item -LiteralPath $temporaryArtifact -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ("[facts] " + $sourceVersion + ": " + $patchCount + " patches for " + $targetPackage + " " + $targetVersion + "; " + $testCount + " runtime tests")
