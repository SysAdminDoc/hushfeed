<#
.SYNOPSIS
    Apply every patch in the bundle to one APK and report which of them landed.

.DESCRIPTION
    The per-patch check with --exclusive answers one patch at a time. This applies all of
    them in a single run and writes the desktop CLI's result file, which names each patch
    that failed and the exception it failed with.

    Use the stripped native fixture rather than an APK that has been patched before: the
    extension classes a previous run left behind hide the anchors that Playback speed and
    Remember clear display resolve, and both fail for that reason alone.

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
    [string]$Java = "java"
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $Bundle) {
    $Bundle = (Get-ChildItem (Join-Path $root 'patches/build/libs') -Filter '*.mpp' |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $PatchList) { $PatchList = Join-Path $root 'patches-list.json' }
if (-not $Bundle -or -not (Test-Path $Bundle)) { throw "No bundle found. Run :patches:buildAndroid first." }

$names = (Get-Content $PatchList -Raw | ConvertFrom-Json).patches | ForEach-Object { $_.name }
if (-not $names) { throw "No patches listed in $PatchList." }
Write-Host "[verify] $($names.Count) patches from $(Split-Path -Leaf $Bundle)"

$enable = @()
foreach ($name in $names) { $enable += '-e'; $enable += $name }

New-Item -ItemType Directory -Force $WorkDir | Out-Null
$out = Join-Path $WorkDir 'verify-all.apk'
$temp = Join-Path $WorkDir 'verify-all-tmp'
$result = Join-Path $WorkDir 'verify-all-result.json'
Remove-Item $out, $result -ErrorAction SilentlyContinue
Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue

& $Java -jar $DesktopJar patch --exclusive --continue-on-error --unsigned `
    -p $Bundle -o $out -t $temp -r $result @enable $Apk 2>&1 |
    Select-String -Pattern '^SEVERE|result saved' | ForEach-Object { $_.Line }

if (-not (Test-Path $result)) { throw "The CLI wrote no result file." }
$report = Get-Content $result -Raw | ConvertFrom-Json
Write-Host "[verify] $($report.packageName) $($report.packageVersion): applied $($report.appliedPatches.Count), failed $($report.failedPatches.Count)"
foreach ($failure in $report.failedPatches) {
    Write-Host "[verify] FAILED $($failure.patch.name): $($failure.reason -split "`n" | Select-Object -First 1)"
}
Write-Host "[verify] result file: $result"
Remove-Item $out -ErrorAction SilentlyContinue
Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
exit $report.failedPatches.Count
