<#
.SYNOPSIS
    Check that nothing the bundle injects uses a register its host method never declared.

.DESCRIPTION
    A patch that writes into a register beyond the method's register count assembles without
    complaint and applies without complaint. The desktop CLI reports it as applied. It fails
    only when a device verifies the class, and then it fails as a VerifyError on whatever
    screen happens to reach it first.

    Two checks, either of which catches that:

    Static. scripts/DexDiff.java lists every method the patched APK does not share with the
    clean build it came from, and holds each line that was not in the clean body, and every
    line of the bundle's own added methods, to that method's register count.

    On a device. The Android runtime's own verifier is the authority, so with -Serial the
    clean APK and the patched APK are both put through dex2oat with the verify filter and the
    verifier's messages are compared. This target's own code raises lock-verification warnings
    on both, which is why it compares the two tallies rather than looking for an empty one: the
    patched build has to raise the messages the clean build raises, as often, and nothing else.

    Both halves refuse to pass on absent evidence. A run where the clean build raised nothing,
    or the patched build raised nothing, or the dex comparison found no difference at all, is
    a run that compared the wrong things, and it says so instead of reporting success.

    The CLI's own --verify-with-sdk is not used here. Its d8 stage cannot round-trip this
    target's classes14.dex on any installed build-tools version, patched or not, so it reports
    a failure that says nothing about the bundle.

.EXAMPLE
    scripts/verify-injected-registers.ps1 -PatchedApk C:\work\patched.apk

.EXAMPLE
    scripts/verify-injected-registers.ps1 -FromDevice -Serial R5CT139QJ5F
#>
[CmdletBinding()]
param(
    [string]$CleanApk,
    [string]$PatchedApk,
    [switch]$FromDevice,
    [string]$Serial,
    [string]$Adb,
    [string]$ReportPath,
    [string]$Java,
    [string]$DesktopJar
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Resolve-Java.ps1')
$Java = Resolve-Java -Explicit $Java

if (-not $DesktopJar) { $DesktopJar = $env:HUSHFEED_DESKTOP_JAR }
if (-not $DesktopJar -or -not (Test-Path -LiteralPath $DesktopJar -PathType Leaf)) {
    # dexlib2 comes from the desktop CLI. Without it the comparison cannot run at all, and
    # saying so beats a stack trace about a missing class.
    throw 'No Morphe desktop CLI. Pass -DesktopJar or set HUSHFEED_DESKTOP_JAR.'
}

function Resolve-Adb {
    param([string]$Explicit)
    if ($Explicit) { return $Explicit }
    if ($env:HUSHFEED_ADB) { return $env:HUSHFEED_ADB }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $winget = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    $found = Get-ChildItem $winget -Recurse -Filter 'adb.exe' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($found) { return $found.FullName }
    throw 'No adb found. Pass -Adb or set HUSHFEED_ADB.'
}

if (-not $CleanApk) {
    $fixture = Get-ChildItem 'C:\_claude-backups\tiktok-fixture' -Filter '*.apk' -ErrorAction SilentlyContinue |
        Sort-Object Length -Descending | Select-Object -First 1
    if ($fixture) { $CleanApk = $fixture.FullName }
}
if (-not $CleanApk -or -not (Test-Path -LiteralPath $CleanApk -PathType Leaf)) {
    throw 'No clean APK. Pass -CleanApk with the vendor build this bundle targets.'
}

$work = Join-Path ([System.IO.Path]::GetTempPath()) ("hushfeed-regs-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $work | Out-Null
if (-not $ReportPath) { $ReportPath = Join-Path $work 'injected-registers.txt' }

$adbPath = $null
if ($FromDevice -or $Serial) { $adbPath = Resolve-Adb -Explicit $Adb }

if ($FromDevice) {
    if (-not $Serial) { throw '-FromDevice needs -Serial so it cannot pull from somebody else''s phone.' }
    $paths = @(& $adbPath -s $Serial shell pm path com.zhiliaoapp.musically 2>&1 |
        ForEach-Object { "$_" } | Where-Object { $_ -match '^package:' })
    if ($paths.Count -eq 0) { throw "The target is not installed on $Serial." }
    if ($paths.Count -ne 1) {
        # A split install has no single APK to compare, and quietly taking the first would
        # compare the base against a whole clean build and call the rest missing.
        throw "The install on $Serial is split across $($paths.Count) APKs; pass -PatchedApk instead."
    }
    $onDevice = ($paths[0] -replace '^package:', '').Trim()
    $PatchedApk = Join-Path $work 'patched-installed.apk'
    Write-Host "[registers] pulling $onDevice"
    & $adbPath -s $Serial pull $onDevice $PatchedApk | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not pull the installed APK from $Serial." }
}

if (-not $PatchedApk -or -not (Test-Path -LiteralPath $PatchedApk -PathType Leaf)) {
    throw 'No patched APK. Pass -PatchedApk, or -FromDevice -Serial <serial>.'
}

Write-Host "[registers] clean   $CleanApk"
Write-Host "[registers] patched $PatchedApk"

$diffOutput = & $Java '-Xmx6g' '-cp' $DesktopJar (Join-Path $PSScriptRoot 'DexDiff.java') `
    $CleanApk $PatchedApk $ReportPath 2>&1
$diffExit = $LASTEXITCODE
$diffOutput | ForEach-Object { Write-Host "[registers] $_" }

$failed = $false
if ($diffExit -ne 0) {
    Write-Host "[registers] FAIL: the dex comparison exited $diffExit; see $ReportPath"
    $failed = $true
} else {
    Write-Host '[registers] static: every injected instruction stays inside its host method register count.'
}

if ($Serial) {
    Write-Host "[registers] running the device verifier on $Serial"

    function Invoke-ArtVerify {
        param([string]$Local, [string]$Label)
        $remote = "/data/local/tmp/hushfeed-verify-$Label.apk"
        $dir = "/data/local/tmp/hushfeed-verify-$Label"
        & $adbPath -s $Serial push $Local $remote | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not push $Label to $Serial." }
        & $adbPath -s $Serial shell "rm -rf $dir && mkdir -p $dir" | Out-Null
        & $adbPath -s $Serial logcat -c | Out-Null
        & $adbPath -s $Serial shell "dex2oat64 --dex-file=$remote --oat-file=$dir/out.oat --output-vdex=$dir/out.vdex --instruction-set=arm64 --compiler-filter=verify --runtime-arg -Xmx1024m -j4; echo exit=`$?" |
            ForEach-Object { if ("$_" -match 'exit=(\d+)') { $script:dexoatExit = [int]$Matches[1] } }
        $log = & $adbPath -s $Serial logcat -d 2>$null
        & $adbPath -s $Serial shell "rm -rf $dir $remote" | Out-Null
        if ($script:dexoatExit -ne 0) { throw "dex2oat on $Label exited $($script:dexoatExit)." }
        # The message carries the method it is about, so the method name is the identity here:
        # timestamps and pids differ between two runs of the same APK and mean nothing. Counted
        # rather than de-duplicated, because a patch that makes one warning fire five hundred
        # times is a change and a set comparison cannot see it.
        $messages = @($log | ForEach-Object { "$_" } |
            Where-Object { $_ -match 'dex2oat' } |
            Where-Object { $_ -match '(failed lock verification|Verification error|Rejecting class|VerifyError)' } |
            ForEach-Object { ($_ -replace '^.*dex2oat[0-9]*:\s*', '').Trim() })
        $tally = @{}
        foreach ($m in $messages) { $tally[$m] = 1 + [int]$tally[$m] }
        return $tally
    }

    $cleanTally = Invoke-ArtVerify -Local $CleanApk -Label 'clean'
    $patchedTally = Invoke-ArtVerify -Local $PatchedApk -Label 'patched'
    $cleanTotal = ($cleanTally.Values | Measure-Object -Sum).Sum
    $patchedTotal = ($patchedTally.Values | Measure-Object -Sum).Sum
    Write-Host "[registers] verifier messages: clean $cleanTotal, patched $patchedTotal"

    # Both sides have to have said something. An empty set compares equal to anything, so a
    # push or a verify that produced nothing would otherwise read as a clean result.
    if ($cleanTotal -eq 0 -or $patchedTotal -eq 0) {
        Write-Host '[registers] FAIL: one of the two runs raised no verifier message at all, so the comparison proves nothing.'
        $failed = $true
    } else {
        $extra = @()
        foreach ($m in $patchedTally.Keys) {
            $was = [int]$cleanTally[$m]
            if ($patchedTally[$m] -gt $was) {
                $extra += ("{0}  (clean {1}, patched {2})" -f $m, $was, $patchedTally[$m])
            }
        }
        if ($extra.Count -ne 0) {
            Write-Host "[registers] FAIL: the patched build raises $($extra.Count) verifier message(s) more often than the clean build:"
            $extra | Select-Object -First 20 | ForEach-Object { Write-Host "[registers]   $_" }
            $failed = $true
        } else {
            Write-Host '[registers] device: the patched build raises no verifier message the clean build does not.'
        }
    }
}

if ($failed) { exit 1 }
Write-Host '[registers] success.'
exit 0
