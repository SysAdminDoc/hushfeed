<#
.SYNOPSIS
    Build and install the test-only instrumentation that reaches Hushfeed settings on a phone.

.DESCRIPTION
    Built with the SDK tools directly rather than through the project's build, because it is not
    part of the bundle and has no business in its dependency graph or its dex.

    It is signed with the same keystore as the sideloaded build. That is not a formality: the
    platform refuses to instrument a package that is not debuggable unless the instrumentation
    carries the same signature, and the patched TikTok is not debuggable.

.EXAMPLE
    tools/verification-probe/build.ps1 -Serial R5CT139QJ5F -Install

.EXAMPLE
    Load it into TikTok, then send it work. It answers in the log rather than through
    "am instrument -w", because TikTok replaces the thread's instrumentation and there is no
    result bundle to answer with by the time the probe is running. Reinstalling TikTok or
    stopping it drops the probe, so run the instrument line again after either.

    adb -s R5CT139QJ5F shell am instrument app.hushfeed.verification/.Probe
    adb -s R5CT139QJ5F shell am broadcast -a app.hushfeed.verification.PROBE `
        -p com.zhiliaoapp.musically -e action dump
    adb -s R5CT139QJ5F shell am broadcast -a app.hushfeed.verification.PROBE `
        -p com.zhiliaoapp.musically -e action set -e key auto_advance -e value true
    adb -s R5CT139QJ5F logcat -d | Select-String HushfeedProbe
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [switch]$Install,
    [switch]$Uninstall,
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Keystore = "$HOME\.android\sideload-release.jks",
    [string]$KeystorePassword = 'sideload',
    [string]$KeyAlias = 'sideload',
    [string]$OutDir = (Join-Path $env:TEMP 'hushfeed-probe')
)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot

function Resolve-Adb {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $found = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages') -Recurse `
        -Filter 'adb.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { return $found.FullName }
    throw 'No adb found.'
}

if ($Uninstall) {
    if (-not $Serial) { throw '-Uninstall needs -Serial.' }
    & (Resolve-Adb) -s $Serial uninstall app.hushfeed.verification | Out-Host
    return
}

$buildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory |
    Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No build-tools under $Sdk." }
$platform = Get-ChildItem (Join-Path $Sdk 'platforms') -Directory |
    Sort-Object { [int]($_.Name -replace '\D', '') } -Descending | Select-Object -First 1
if (-not $platform) { throw "No platforms under $Sdk." }
$androidJar = Join-Path $platform.FullName 'android.jar'
Write-Host "[probe] build-tools $($buildTools.Name), $($platform.Name)"

$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javac.exe' } else { $null }
if (-not $javac -or -not (Test-Path $javac)) {
    $javac = (Get-ChildItem 'C:\Program Files\Android\Android Studio\jbr\bin\javac.exe' -ErrorAction SilentlyContinue).FullName
}
if (-not $javac -or -not (Test-Path $javac)) { throw 'No javac found. Set JAVA_HOME.' }

# Not silenced: a directory that cannot be cleared keeps its old classes, and every .class
# under it is dexed into the probe below, source file or not.
if (Test-Path -LiteralPath $OutDir) { Remove-Item -LiteralPath $OutDir -Recurse -Force }
if (Test-Path -LiteralPath $OutDir) { throw "Could not clear $OutDir." }
New-Item -ItemType Directory -Force -Path "$OutDir\classes", "$OutDir\dex" | Out-Null

# @() around both of these: with a single file the pipeline hands back a string rather than
# an array, and splatting a string spreads its characters, so javac is handed the colon out
# of C:\ as though it were a flag.
$sources = @(Get-ChildItem (Join-Path $here 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-Host "[probe] compiling $($sources.Count) source file(s)"
# --release rather than -source/-target with a boot class path: a current JDK refuses that
# pairing outright. android.jar stays on the ordinary class path, which is where the android.*
# classes come from; java.* comes from the release the flag names.
& $javac --release 11 -classpath $androidJar `
    -d "$OutDir\classes" -encoding UTF-8 -nowarn @sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed.' }

$classes = @(Get-ChildItem "$OutDir\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& (Join-Path $buildTools.FullName 'd8.bat') --lib $androidJar --min-api 23 --output "$OutDir\dex" @classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed.' }

$unsigned = Join-Path $OutDir 'probe-unsigned.apk'
& (Join-Path $buildTools.FullName 'aapt2.exe') link -I $androidJar `
    --manifest (Join-Path $here 'AndroidManifest.xml') -o $unsigned --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed.' }

# aapt2 writes the manifest and resource table; the dex goes in afterwards with the zip tool,
# which is what an ordinary build does too, just with more steps in between.
Push-Location "$OutDir\dex"
try {
    # aapt is gone from recent build-tools, and under Stop a missing command is a terminating
    # error that skipped the fallback below. Tested for, so the zip path is the one that runs.
    $aapt = Join-Path $buildTools.FullName 'aapt.exe'
    $added = $false
    if (Test-Path -LiteralPath $aapt -PathType Leaf) {
        & $aapt add $unsigned classes.dex | Out-Null
        $added = $LASTEXITCODE -eq 0
    }
    if (-not $added) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
        try {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zip, (Join-Path $OutDir 'dex\classes.dex'), 'classes.dex') | Out-Null
        } finally { $zip.Dispose() }
    }
} finally { Pop-Location }

$aligned = Join-Path $OutDir 'probe-aligned.apk'
& (Join-Path $buildTools.FullName 'zipalign.exe') -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }

$signed = Join-Path $OutDir 'hushfeed-verification-probe.apk'
& (Join-Path $buildTools.FullName 'apksigner.bat') sign --ks $Keystore `
    --ks-pass "pass:$KeystorePassword" --ks-key-alias $KeyAlias --key-pass "pass:$KeystorePassword" `
    --min-sdk-version 23 --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed.' }
Write-Host "[probe] $signed"

if ($Install) {
    if (-not $Serial) { throw '-Install needs -Serial.' }
    $adb = Resolve-Adb
    & $adb -s $Serial install -r $signed | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "adb install failed on $Serial. The output above says why." }
    Write-Host '[probe] installed. Load it into TikTok, then send it work:'
    Write-Host "  adb -s $Serial shell am instrument app.hushfeed.verification/.Probe"
    Write-Host "  adb -s $Serial shell am broadcast -a app.hushfeed.verification.PROBE -p com.zhiliaoapp.musically -e action dump"
    Write-Host "  adb -s $Serial logcat -d | Select-String HushfeedProbe"
}
