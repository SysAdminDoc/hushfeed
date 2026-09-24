<#
.SYNOPSIS
    Tallies the kinds of strip above a caption and banner under a video the test phone is served.

.DESCRIPTION
    A strip above a caption (a place, an effect, a film, a link to another app) is one of the
    video's anchors, and the bar under a video is one of its banners. TikTok names each kind with
    a component key, and a switch that hides one kind needs that key. Guessing it from TikTok's
    config is no substitute: the config lists over a hundred and fifty of them.

    Each round asks the probe's stripkeys action for the keys of the video in front, then swipes
    to the next one, only while TikTok is in front. Keys are identifiers only, never what a strip
    says. The tally at the end says how often each key came up; an anchor also carries TikTok's
    type number for it.

    With -Shots, the first video of each new key other than a place is screenshotted to
    work/strip-hunt/, so a key can be matched with what its strip showed. The pictures show other
    people's videos, and work/ never goes into git.

.EXAMPLE
    Load the probe (tools/verification-probe/build.ps1, then its instrument line), open the For
    You feed in TikTok, then:

    tools/verification-probe/strip-hunt.ps1 -Rounds 150 -Shots
#>
param(
    [int]$Rounds = 100,
    # x1,y1,x2,y2 in panel pixels. The default moves a full-screen feed up one video.
    [string]$Swipe = '540,1750,540,650',
    [int]$SettleMs = 2000,
    [switch]$Shots
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$serial = $env:HUSHFEED_DEVICE_SERIAL
if (-not $serial) { throw 'Set HUSHFEED_DEVICE_SERIAL to the serial of the test phone this machine may drive.' }
$swipePoints = @($Swipe -split ',' | ForEach-Object { [int]$_.Trim() })
if ($swipePoints.Count -ne 4) { throw "-Swipe needs four numbers, x1,y1,x2,y2: $Swipe" }
$shotDir = Join-Path $root 'work/strip-hunt'
if ($Shots) { New-Item -ItemType Directory -Force $shotDir | Out-Null }

function Assert-TikTok {
    $top = [string](& adb -s $serial shell dumpsys activity activities | Select-String 'topResumedActivity' | Select-Object -First 1)
    if ($top -notmatch 'com\.zhiliaoapp\.musically') { throw "TikTok is not in front: $top" }
}

function Invoke-Phone([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments) {
    $windowsScript = [IO.Path]::GetFullPath((Join-Path $root 'scripts/phone.sh')).Replace('\', '/')
    if ($windowsScript -notmatch '^([A-Za-z]):/(.+)$') {
        throw "Could not translate scripts/phone.sh for the guarded input: $windowsScript"
    }
    $script = '/mnt/' + $Matches[1].ToLowerInvariant() + '/' + $Matches[2]
    & wsl.exe env "PHONE_SERIAL=$serial" "HUSHFEED_DEVICE_SERIAL=$serial" PHONE_SCALE=1 `
        bash $script @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Guarded phone input failed: $($Arguments -join ' ')" }
}

# The keys of the video in front, as "anchor <key>/<type>" and "banner <key>".
function Get-Keys {
    & adb -s $serial logcat -c
    & adb -s $serial shell am broadcast -a app.hushfeed.verification.PROBE -p com.zhiliaoapp.musically `
        -e action stripkeys | Out-Null
    Start-Sleep -Milliseconds 1200
    $line = [string](@(& adb -s $serial logcat -d -s HushfeedProbe:V -v raw) |
        Where-Object { $_ -like 'ok stripkeys *' } | Select-Object -Last 1)
    if ($line -notmatch 'anchors=\[(.*?)\] banners=\[(.*?)\]') { return @() }
    $anchors = @($Matches[1] -split ', ' | Where-Object { $_ } | ForEach-Object { "anchor $_" })
    $banners = @($Matches[2] -split ', ' | Where-Object { $_ } | ForEach-Object { "banner $_" })
    return $anchors + $banners
}

Assert-TikTok
$tally = @{}
for ($round = 1; $round -le $Rounds; $round++) {
    Assert-TikTok
    foreach ($key in @(Get-Keys)) {
        $tally[$key] = 1 + [int]$tally[$key]
        if ($tally[$key] -gt 1) { continue }
        "round ${round}: first $key"
        if ($Shots -and $key -notlike 'anchor anchor_poi/*') {
            $picture = Join-Path $shotDir (($key -replace '[^a-z0-9_]', '-') + '.png')
            & adb -s $serial shell screencap -p /sdcard/hushfeed-strip-hunt.png
            & adb -s $serial pull /sdcard/hushfeed-strip-hunt.png $picture 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Could not pull the screenshot for $key" }
            & adb -s $serial shell rm /sdcard/hushfeed-strip-hunt.png
        }
    }
    if ($round -lt $Rounds) {
        Invoke-Phone swipe $swipePoints[0] $swipePoints[1] $swipePoints[2] $swipePoints[3] 350 0
        Start-Sleep -Milliseconds $SettleMs
    }
}
"tally after $Rounds videos:"
$tally.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object { '  {0} x{1}' -f $_.Key, $_.Value }
