<#
.SYNOPSIS
    Apply every patch in the bundle and reject partial or unsuccessful output.

.DESCRIPTION
    The per-patch check with --exclusive answers one patch at a time. This applies all of
    them in a single run and validates the desktop CLI process, result report, patch names,
    target version and saved APK. A result file can be written from a finally block after a
    failed compile, so a zero failed-patch count is not enough to call the run successful.

.EXAMPLE
    scripts/verify-all-patches.ps1 -Apk C:\path\to\native-fixture.apk `
        -DesktopJar C:\path\to\morphe-desktop.jar -WorkDir C:\path\to\scratch
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$DesktopJar,
    [Parameter(Mandatory = $true)][string]$WorkDir,
    [string]$Bundle,
    [string]$PatchList,
    [string]$Java
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'Resolve-Java.ps1')
$Java = Resolve-Java -Explicit $Java
$root = Split-Path -Parent $PSScriptRoot
$expectedPackageName = 'com.zhiliaoapp.musically'
$expectedPackageVersion = '46.2.3'

. (Join-Path $PSScriptRoot 'patch-report.ps1')

function Resolve-WithinRoot {
    param([string]$Path, [string]$Root)
    $candidate = [System.IO.Path]::GetFullPath($Path)
    $prefix = $Root.TrimEnd('\') + '\'
    if (-not $candidate.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to use a generated path outside the work directory: $candidate"
    }
    return $candidate
}

function Remove-GeneratedPath {
    param([string]$Path, [string]$Root, [switch]$Recurse)
    try {
        $safe = Resolve-WithinRoot -Path $Path -Root $Root
        if (-not (Test-Path -LiteralPath $safe)) { return }
        if ($Recurse) { Remove-Item -LiteralPath $safe -Recurse -Force -ErrorAction Stop }
        else { Remove-Item -LiteralPath $safe -Force -ErrorAction Stop }
    } catch {
        Write-Warning "Could not remove generated path: $($_.Exception.Message)"
    }
}

if (-not $Bundle) {
    $version = ((Get-Content (Join-Path $root 'gradle.properties')) -match '^version\s*=' | Select-Object -First 1) -replace '^version\s*=\s*', ''
    $Bundle = Join-Path $root "patches/build/libs/patches-$version.mpp"
    if (-not (Test-Path -LiteralPath $Bundle -PathType Leaf)) {
        throw "No bundle for version $version at $Bundle. Run :patches:generatePatchesList then :patches:buildAndroid."
    }
}
if (-not $PatchList) { $PatchList = Join-Path $root 'patches-list.json' }
if (-not $Bundle -or -not (Test-Path -LiteralPath $Bundle -PathType Leaf)) { throw "No bundle found. Run :patches:buildAndroid first." }
if (-not (Test-Path -LiteralPath $PatchList -PathType Leaf)) { throw "No patch list found: $PatchList" }
if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
if (-not (Test-Path -LiteralPath $DesktopJar -PathType Leaf)) { throw "Desktop CLI jar not found: $DesktopJar" }

try {
    $names = @((Get-Content -LiteralPath $PatchList -Raw | ConvertFrom-Json).patches | ForEach-Object { $_.name })
} catch {
    throw "Could not read patch list ${PatchList}: $($_.Exception.Message)"
}
if ($names.Count -eq 0 -or @($names | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -ne 0) {
    throw "No valid patches listed in $PatchList."
}
Write-Host "[verify] $($names.Count) patches from $(Split-Path -Leaf $Bundle)"

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$workRoot = (Resolve-Path -LiteralPath $WorkDir).Path
$runId = [guid]::NewGuid().ToString('N')
$runDir = Join-Path $workRoot "verify-$runId"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$out = Resolve-WithinRoot -Path (Join-Path $runDir 'verify-all.apk') -Root $workRoot
$temp = Resolve-WithinRoot -Path (Join-Path $runDir 'verify-all-tmp') -Root $workRoot
$result = Resolve-WithinRoot -Path (Join-Path $workRoot "verify-all-result-$runId.json") -Root $workRoot
$enable = @()
foreach ($name in $names) { $enable += '-e'; $enable += $name }
$arguments = @('patch', '--exclusive', '--continue-on-error', '--unsigned', '-p', $Bundle,
    '-o', $out, '-t', $temp, '-r', $result) + $enable + @($Apk)
$exitCode = 1

try {
    $cliOutput = @(& $Java '-jar' $DesktopJar @arguments 2>&1)
    $cliExitCode = $LASTEXITCODE
    $cliOutput | ForEach-Object {
        $line = [string]$_
        if ($line -match 'SEVERE|ERROR|Exception|result saved|Saved to') { Write-Host "[verify] $line" }
    }

    $report = $null
    if (Test-Path -LiteralPath $result -PathType Leaf) {
        try { $report = Get-Content -LiteralPath $result -Raw | ConvertFrom-Json }
        catch { Write-Warning "Could not parse result JSON: $($_.Exception.Message)" }
    }
    $validation = Test-PatchingReport -Report $report -ExpectedNames $names -OutputPath $out `
        -ExpectedPackageName $expectedPackageName -ExpectedPackageVersion $expectedPackageVersion
    $reportApplied = if ($null -ne $report) { @($report.appliedPatches).Count } else { 0 }
    $reportFailed = if ($null -ne $report) { @($report.failedPatches).Count } else { 0 }
    $target = if ($null -ne $report) { "$($report.packageName) $($report.packageVersion)" } else { 'unknown target' }
    Write-Host "[verify] ${target}: applied $reportApplied, failed $reportFailed, CLI exit $cliExitCode"
    if ($null -ne $report) {
        foreach ($failure in @($report.failedPatches)) {
            $patchName = if ($null -ne $failure.patch) { $failure.patch.name } else { 'unknown patch' }
            Write-Host "[verify] FAILED ${patchName}: $($failure.reason -split "`n" | Select-Object -First 1)"
        }
    }
    Write-Host "[verify] result file: $result"
    if ($cliExitCode -ne 0) { Write-Warning "The desktop CLI exited with $cliExitCode." }
    if (-not $validation.Valid) { Write-Warning "[verify] $($validation.Reason)" }
    if ($cliExitCode -eq 0 -and $validation.Valid) {
        Write-Host '[verify] success: every requested patch applied to a valid APK.'
        $exitCode = 0
    }
} finally {
    Remove-GeneratedPath -Path $runDir -Root $workRoot -Recurse
}

exit $exitCode
