<#
.SYNOPSIS
    Patch the vendor APK with every Hushfeed patch, sign it, and optionally install it.

.DESCRIPTION
    verify-all-patches.ps1 answers whether the patches apply and throws its APK away. This
    keeps one, signed with the sideload keystore so it installs on a phone, and installs it
    over adb when a serial is given. The stock TikTok on the phone has a different signer, so
    it has to be uninstalled first; that is what -Replace does, and it wipes TikTok's data on
    that phone.

.EXAMPLE
    scripts/patch-for-device.ps1 -Serial R5CT139QJ5F -Replace
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [switch]$Replace,
    [string]$Apk = (Get-ChildItem 'C:\_claude-backups\tiktok-fixture' -Filter '*46.2.3*.apk' -File | Select-Object -First 1).FullName,
    [string]$DesktopJar = (Get-ChildItem 'C:\_claude-backups\morphe-tools' -Filter 'morphe-desktop*.jar' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName,
    [string]$Java = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe',
    [string]$Keystore = "$HOME\.android\sideload-release.jks",
    [string]$KeystorePassword = 'sideload',
    [string]$KeyAlias = 'sideload',
    [string]$OutDir = (Join-Path $env:TEMP 'hushfeed-device')
)

$ErrorActionPreference = 'Stop'
# The defaults above are evaluated before this line, so a missing folder leaves them empty
# and the failure would surface later as a confusing Split-Path error.
if (-not $Apk -or -not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "No vendor APK. Pass -Apk with the 46.2.3 build." }
if (-not $DesktopJar -or -not (Test-Path -LiteralPath $DesktopJar -PathType Leaf)) { throw 'No Morphe desktop CLI jar. Pass -DesktopJar.' }
$root = Split-Path -Parent $PSScriptRoot
$version = ((Get-Content (Join-Path $root 'gradle.properties')) -match '^version\s*=' | Select-Object -First 1) -replace '^version\s*=\s*', ''
$bundle = Join-Path $root "patches\build\libs\patches-$version.mpp"
if (-not (Test-Path $bundle)) { throw "No bundle at $bundle. Build it first: :patches:generatePatchesList then :patches:buildAndroid, through the governor." }
$names = (Get-Content (Join-Path $root 'patches-list.json') -Raw | ConvertFrom-Json).patches | ForEach-Object { $_.name }

New-Item -ItemType Directory -Force $OutDir | Out-Null
$out = Join-Path $OutDir "hushfeed-$version-signed.apk"
$temp = Join-Path $OutDir 'tmp'
$result = Join-Path $OutDir 'result.json'
if (Test-Path $out) { Remove-Item $out -Force }

Write-Host "[device] $($names.Count) patches from $(Split-Path -Leaf $bundle) onto $(Split-Path -Leaf $Apk)"
$enable = @()
foreach ($name in $names) { $enable += '-e'; $enable += $name }
$arguments = @('patch', '--exclusive', '-p', $bundle, '-o', $out, '-t', $temp, '-r', $result,
    '--keystore', $Keystore, '--keystore-password', $KeystorePassword,
    '--keystore-entry-alias', $KeyAlias, '--keystore-entry-password', $KeystorePassword) + $enable + @($Apk)
try {
    & $Java -jar $DesktopJar @arguments 2>&1 | ForEach-Object {
        $line = [string]$_
        if ($line -match 'SEVERE|ERROR|Exception|Saved to') { Write-Host "[device] $line" }
    }
    if ($LASTEXITCODE -ne 0) { throw "The desktop CLI exited with $LASTEXITCODE" }
} finally {
    # The CLI unpacks the whole APK here and a run against TikTok leaves gigabytes behind.
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue }
}
# The same report check the throwaway verification applies: every requested patch, every
# step, the target, and a real APK. The build that goes onto a phone deserves no less.
. (Join-Path $PSScriptRoot 'patch-report.ps1')
$report = $null
if (Test-Path -LiteralPath $result -PathType Leaf) { $report = Get-Content -LiteralPath $result -Raw | ConvertFrom-Json }
$validation = Test-PatchingReport -Report $report -ExpectedNames $names -OutputPath $out `
    -ExpectedPackageName 'com.zhiliaoapp.musically' -ExpectedPackageVersion '46.2.3'
if (-not $validation.Valid) { throw "Patching did not produce a complete APK: $($validation.Reason)" }
Write-Host "[device] applied $(@($report.appliedPatches).Count), failed $(@($report.failedPatches).Count), target $($report.packageName) $($report.packageVersion)"
Write-Host "[device] $out"

if (-not $Serial) { return }
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter adb.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if (-not $adb) { throw 'No adb found. Put it on the PATH or install the platform tools.' }
if ($Replace) {
    Write-Host "[device] uninstalling com.zhiliaoapp.musically on $Serial"
    & $adb -s $Serial uninstall com.zhiliaoapp.musically | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "adb uninstall failed on $Serial." }
}
Write-Host "[device] installing on $Serial"
# adb prints Failure [...] and exits non-zero on a refused install; without this the script
# went on to print the version of whatever was already on the phone, as if it were this build.
& $adb -s $Serial install -r -g $out | Out-Host
if ($LASTEXITCODE -ne 0) { throw "adb install failed on $Serial. The output above says why." }
& $adb -s $Serial shell dumpsys package com.zhiliaoapp.musically | Select-String 'versionName' | Out-Host
