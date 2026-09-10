<#
.SYNOPSIS
    Check that nothing the bundle injects uses a register its host method never declared.

.DESCRIPTION
    A patch that writes into a register beyond the method's register count assembles without
    complaint and applies without complaint. The desktop CLI reports it as applied. It fails
    only when a device verifies the class, and then it fails as a VerifyError on whatever
    screen happens to reach it first.

    Two checks, either of which catches that:

    Static. Every host method whose body differs between the clean APK and the patched one is
    listed with its register count before and after and the instructions the patched body
    added. Any added instruction naming a register at or above the method's register count
    fails the run.

    On a device. The Android runtime's own verifier is the authority, so with -Serial the
    clean APK and the patched APK are both put through dex2oat with the verify filter and the
    verifier's messages are compared. TikTok's own code raises lock-verification warnings on
    both, which is why this compares the two sets rather than looking for an empty one: the
    patched build has to raise the messages the clean build raises and nothing else.

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
    [string]$Java
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Resolve-Java.ps1')
$Java = Resolve-Java -Explicit $Java
$root = Split-Path -Parent $PSScriptRoot

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
    $line = & $adbPath -s $Serial shell pm path com.zhiliaoapp.musically 2>&1 | Select-Object -First 1
    if ($line -notmatch '^package:(.+)$') { throw "The target is not installed on ${Serial}: $line" }
    $onDevice = $Matches[1].Trim()
    $PatchedApk = Join-Path $work 'patched-installed.apk'
    Write-Host "[registers] pulling $onDevice"
    & $adbPath -s $Serial pull $onDevice $PatchedApk | Out-Null
}

if (-not $PatchedApk -or -not (Test-Path -LiteralPath $PatchedApk -PathType Leaf)) {
    throw 'No patched APK. Pass -PatchedApk, or -FromDevice -Serial <serial>.'
}

Write-Host "[registers] clean   $CleanApk"
Write-Host "[registers] patched $PatchedApk"

$diffOutput = & $Java '-Xmx6g' '-cp' $env:HUSHFEED_DESKTOP_JAR (Join-Path $PSScriptRoot 'DexDiff.java') `
    $CleanApk $PatchedApk $ReportPath 2>&1
$diffOutput | ForEach-Object { Write-Host "[registers] $_" }
if ($LASTEXITCODE -ne 0) { throw "The dex comparison failed with $LASTEXITCODE." }

$outOfRange = -1
foreach ($l in $diffOutput) {
    if ("$l" -match 'injected lines naming an out-of-range register:\s*(\d+)') { $outOfRange = [int]$Matches[1] }
}
if ($outOfRange -lt 0) { throw 'The dex comparison did not report a register count.' }

$failed = $false
if ($outOfRange -ne 0) {
    Write-Host "[registers] FAIL: $outOfRange injected instruction(s) name a register the host method never declared."
    Write-Host "[registers] see $ReportPath"
    $failed = $true
} else {
    Write-Host '[registers] static: every injected instruction stays inside its host method register count.'
}

if ($Serial) {
    Write-Host "[registers] running the device verifier on $Serial"
    $remoteClean = '/data/local/tmp/hushfeed-verify-clean.apk'
    $remotePatched = '/data/local/tmp/hushfeed-verify-patched.apk'

    function Invoke-ArtVerify {
        param([string]$Local, [string]$Remote, [string]$Label)
        & $adbPath -s $Serial push $Local $Remote | Out-Null
        $dir = "/data/local/tmp/hushfeed-verify-$Label"
        & $adbPath -s $Serial shell "rm -rf $dir && mkdir -p $dir" | Out-Null
        & $adbPath -s $Serial logcat -c | Out-Null
        & $adbPath -s $Serial shell "dex2oat64 --dex-file=$Remote --oat-file=$dir/out.oat --output-vdex=$dir/out.vdex --instruction-set=arm64 --compiler-filter=verify --runtime-arg -Xmx1024m -j4" 2>&1 | Out-Null
        $log = & $adbPath -s $Serial logcat -d 2>$null
        & $adbPath -s $Serial shell "rm -rf $dir $Remote" | Out-Null
        # The message carries the method it is about, so the method name is the identity here:
        # timestamps and pids differ between two runs of the same APK and mean nothing.
        return @($log | Select-String -Pattern 'dex2oat' |
            ForEach-Object { "$_" } |
            Where-Object { $_ -match '(failed lock verification|Verification error|Rejecting class|VerifyError)' } |
            ForEach-Object { ($_ -replace '^.*dex2oat[0-9]*:\s*', '').Trim() } |
            Sort-Object -Unique)
    }

    $cleanMessages = Invoke-ArtVerify -Local $CleanApk -Remote $remoteClean -Label 'clean'
    $patchedMessages = Invoke-ArtVerify -Local $PatchedApk -Remote $remotePatched -Label 'patched'
    Write-Host "[registers] verifier messages: clean $($cleanMessages.Count), patched $($patchedMessages.Count)"

    $extra = @($patchedMessages | Where-Object { $cleanMessages -notcontains $_ })
    if ($extra.Count -ne 0) {
        Write-Host "[registers] FAIL: the patched build raises $($extra.Count) verifier message(s) the clean build does not:"
        $extra | Select-Object -First 20 | ForEach-Object { Write-Host "[registers]   $_" }
        $failed = $true
    } else {
        Write-Host '[registers] device: the patched build raises no verifier message the clean build does not.'
    }
    if ($cleanMessages.Count -eq 0) {
        # The clean build of this target raises lock-verification warnings. None at all means the
        # messages were not captured, and an empty set would compare equal to anything.
        Write-Host '[registers] FAIL: the clean build raised no verifier message at all, so the comparison proves nothing.'
        $failed = $true
    }
}

if ($failed) { exit 1 }
Write-Host '[registers] success.'
exit 0
