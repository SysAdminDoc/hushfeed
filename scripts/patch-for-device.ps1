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
    [string]$Apk = (Get-ChildItem 'C:\_claude-backups\tiktok-fixture' -Filter *.apk -File | Select-Object -First 1).FullName,
    [string]$DesktopJar = (Get-ChildItem 'C:\_claude-backups\morphe-tools' -Filter 'morphe-desktop*.jar' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName,
    [string]$Java = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe',
    [string]$Keystore = "$HOME\.android\sideload-release.jks",
    [string]$KeystorePassword = 'sideload',
    [string]$KeyAlias = 'sideload',
    [string]$OutDir = (Join-Path $env:TEMP 'hushfeed-device')
)

$ErrorActionPreference = 'Stop'
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
& $Java -jar $DesktopJar @arguments 2>&1 | ForEach-Object {
    $line = [string]$_
    if ($line -match 'SEVERE|ERROR|Exception|Saved to') { Write-Host "[device] $line" }
}
if ($LASTEXITCODE -ne 0) { throw "The desktop CLI exited with $LASTEXITCODE" }
$report = Get-Content $result -Raw | ConvertFrom-Json
Write-Host "[device] applied $(@($report.appliedPatches).Count), failed $(@($report.failedPatches).Count), target $($report.packageName) $($report.packageVersion)"
if (@($report.failedPatches).Count -gt 0 -or -not (Test-Path $out)) { throw 'Patching did not produce a complete APK.' }
Write-Host "[device] $out"

if (-not $Serial) { return }
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter adb.exe | Select-Object -First 1 -ExpandProperty FullName }
if ($Replace) {
    Write-Host "[device] uninstalling com.zhiliaoapp.musically on $Serial"
    & $adb -s $Serial uninstall com.zhiliaoapp.musically | Out-Host
}
Write-Host "[device] installing on $Serial"
& $adb -s $Serial install -r -g $out | Out-Host
& $adb -s $Serial shell dumpsys package com.zhiliaoapp.musically | Select-String 'versionName' | Out-Host
