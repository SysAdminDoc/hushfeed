<#
.SYNOPSIS
    Find the smallest heap the patcher finishes patching in.

.DESCRIPTION
    Morphe Manager patches with a memory limit the user can set, and a limit below what the
    work needs ends in an out of memory error rather than a patched app. This runs the
    desktop CLI at a given -Xmx and says whether it finished.

    Measure the floor, not the peak: an unconstrained JVM grows its heap until the collector
    feels like running, so its peak says nothing about what is needed.

    Cases are "<what>:<Xmx>", where <what> is "settings" (the Settings patch alone, which
    does the resource write) or "all" (every patch in the list).

.EXAMPLE
    $env:HUSHFEED_WORKDIR = "C:\scratch"
    $env:HUSHFEED_JAVA = "C:\jdk-21\bin\java.exe"
    scripts/measure-patch-heap.ps1 settings:640m settings:768m all:768m
#>
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot

$work = $env:HUSHFEED_WORKDIR
if (-not $work) { throw "Set HUSHFEED_WORKDIR to a directory holding morphe-desktop.jar and the fixture APK." }
$java = if ($env:HUSHFEED_JAVA) { $env:HUSHFEED_JAVA } else { "java" }
$apk = if ($env:HUSHFEED_APK) { $env:HUSHFEED_APK } else { Join-Path $work "tt\native-fixture.apk" }
$bundle = (Get-ChildItem (Join-Path $root 'patches/build/libs') -Filter '*.mpp' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
if (-not $bundle) { throw "No bundle found. Run :patches:buildAndroid first." }

$all = (Get-Content (Join-Path $root 'patches-list.json') -Raw | ConvertFrom-Json).patches |
    ForEach-Object { $_.name }

foreach ($case in $args) {
    $parts = $case -split ':'
    $which = $parts[0]
    $mx = $parts[1]

    $out = Join-Path $work "heap-out.apk"
    $temp = Join-Path $work "heap-tmp"
    if (Test-Path $out) { Remove-Item -LiteralPath $out -Force }
    if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }

    $enable = @()
    if ($which -eq 'settings') { $enable = @('-e', 'Settings') }
    else { foreach ($name in $all) { $enable += '-e'; $enable += $name } }

    $log = & $java "-Xmx$mx" -jar (Join-Path $work 'morphe-desktop.jar') patch --exclusive `
        --continue-on-error --unsigned -p $bundle -o $out -t $temp @enable $apk 2>&1
    $outOfMemory = ($log | Select-String -Pattern 'OutOfMemoryError|GC overhead' | Measure-Object).Count
    $saved = ($log | Select-String -Pattern 'Saved to' | Measure-Object).Count
    $verdict = if ($outOfMemory -gt 0) { 'OUT OF MEMORY' } elseif ($saved -gt 0) { 'ok' } else { 'did not finish' }
    Write-Host "[$which @ -Xmx$mx] $verdict"
}
