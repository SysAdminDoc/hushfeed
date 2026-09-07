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
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
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
$testCount = 0
foreach ($file in $testFiles) {
    try {
        $results = [xml](Get-Content -LiteralPath $file.FullName -Raw)
        $testCount += @($results.testsuite.testcase).Count
    } catch {
        throw "Could not read test results from $($file.FullName): $($_.Exception.Message)"
    }
}
Require-Match -Text ([string]$bundle.description) -Pattern "\b$testCount runtime tests passed\b" -Description 'bundle description test count'

Write-Host ("[facts] " + $sourceVersion + ": " + $patchCount + " patches for " + $targetPackage + " " + $targetVersion + "; " + $testCount + " runtime tests")
