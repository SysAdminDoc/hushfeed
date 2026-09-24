<#
.SYNOPSIS
    Exercise ResourceTableCheck.java against small APKs that aapt2 builds.

.DESCRIPTION
    The stock APK is a feature package with id 0x7e, the shape of TikTok's df_search_biz whose
    layout/bj went missing upstream (#84): two colors, a theme and one layout. Each patched APK
    moves one thing. An unchanged table, a rewritten color and a renamed entry pass and are
    reported; a lost layout, a layout file missing from the archive, a lost style item and a
    reference to nothing fail, and each failure names the id.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [string]$Java,
    [string]$DesktopJar,
    [string]$Aapt2
)

$ErrorActionPreference = 'Stop'
if (-not $Root) { $Root = Split-Path -Parent $PSScriptRoot }
. (Join-Path $PSScriptRoot 'Resolve-Java.ps1')
. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'release-receipt.ps1')

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Invoke-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Description)
    $output = @(& $Program @Arguments 2>&1 | ForEach-Object { "$_" })
    if ($LASTEXITCODE -ne 0) {
        throw "$Description exited $LASTEXITCODE.`n$($output -join "`n")"
    }
}

$package = 'com.zhiliaoapp.musically.df_search_biz'
$manifest = @"
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$package">
    <application android:theme="@style/SearchTheme" />
</manifest>
"@
$colors = @'
<resources>
    <color name="accent">#ffff3b5c</color>
    <color name="surface">#ff121212</color>
</resources>
'@
$styles = @'
<resources>
    <style name="SearchTheme">
        <item name="android:windowBackground">@color/surface</item>
        <item name="android:textColorPrimary">@color/accent</item>
    </style>
</resources>
'@
$layout = @'
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@color/surface" />
'@

<#
    Links one APK from the files given, keyed by path under res/. With -EmitIds the ids aapt2
    chose are written out; with -StableIds every build keeps the stock build's ids.
#>
function New-ResourceApk {
    param([string]$Name, [System.Collections.IDictionary]$Files, [string]$EmitIds, [string]$StableIds)
    $dir = Join-Path $caseRoot $Name
    foreach ($entry in $Files.GetEnumerator()) {
        $path = Join-Path $dir ($entry.Key -replace '/', [System.IO.Path]::DirectorySeparatorChar)
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
        [System.IO.File]::WriteAllText($path, $entry.Value, [System.Text.UTF8Encoding]::new($false))
    }
    $manifestPath = Join-Path $dir 'AndroidManifest.xml'
    [System.IO.File]::WriteAllText($manifestPath, $manifest, [System.Text.UTF8Encoding]::new($false))
    $compiled = Join-Path $dir 'compiled.zip'
    Invoke-Checked -Program $Aapt2 -Arguments @('compile', '--dir', (Join-Path $dir 'res'), '-o', $compiled) `
        -Description "aapt2 compile for $Name"
    $apk = Join-Path $caseRoot "$Name.apk"
    $link = @('link', '-o', $apk, '-I', $androidJar, '--manifest', $manifestPath,
        '--package-id', '0x7e', '--allow-reserved-package-id')
    if ($EmitIds) { $link += @('--emit-ids', $EmitIds) }
    if ($StableIds) { $link += @('--stable-ids', $StableIds) }
    Invoke-Checked -Program $Aapt2 -Arguments ($link + @($compiled)) -Description "aapt2 link for $Name"
    return $apk
}

function Copy-ApkWith {
    param([string]$From, [string]$Name, [scriptblock]$Change)
    $apk = Join-Path $caseRoot "$Name.apk"
    Copy-Item -LiteralPath $From -Destination $apk
    $archive = [System.IO.Compression.ZipFile]::Open($apk, [System.IO.Compression.ZipArchiveMode]::Update)
    try { & $Change $archive } finally { $archive.Dispose() }
    return $apk
}

function Invoke-Check {
    param([string]$Patched, [string]$Name)
    $report = Join-Path $caseRoot "$Name-report.txt"
    $global:LASTEXITCODE = 0
    $output = @(& $Java '-Xmx1g' '-cp' $DesktopJar (Join-Path $PSScriptRoot 'ResourceTableCheck.java') `
        $stockApk $Patched $report 2>&1 | ForEach-Object { "$_" })
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($output -join "`n"); Report = $report }
}

$verifyText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'verify-all-patches.ps1') -Raw
Assert-True ($verifyText -match 'ResourceTableCheck\.java') `
    'verify-all-patches.ps1 does not hold the patched resource table to the stock one.'
$prePushText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pre-push.ps1') -Raw
Assert-True ($prePushText -match 'scripts/test-resource-table-check\.ps1') `
    'The push gate does not run the resource table check fixtures.'

$Java = Resolve-Java -Explicit $Java
$DesktopJar = Resolve-DesktopCli -Explicit $DesktopJar -Root $Root -Required
$Aapt2 = Resolve-Aapt2 -Explicit $Aapt2 -Root $Root
# The platform jar beside the SDK's build-tools: sdk/build-tools/<version>/aapt2 -> sdk/platforms.
$sdk = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $Aapt2))
$androidJar = Get-ChildItem -LiteralPath (Join-Path $sdk 'platforms') -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Join-Path $_.FullName 'android.jar' } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -Last 1
if (-not $androidJar) { throw "No platforms/*/android.jar in the SDK at $sdk for aapt2 to link against." }

$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$caseRoot = [System.IO.Path]::GetFullPath((Join-Path $tempBase `
    ('hushfeed-resource-check-' + [guid]::NewGuid().ToString('N'))))
$requiredPrefix = $tempBase.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + `
    [System.IO.Path]::DirectorySeparatorChar
if (-not $caseRoot.StartsWith($requiredPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create test files outside the temporary directory: $caseRoot"
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    New-Item -ItemType Directory -Path $caseRoot | Out-Null
    $ids = Join-Path $caseRoot 'stock-ids.txt'
    $stockFiles = [ordered]@{
        'res/values/colors.xml' = $colors
        'res/values/styles.xml' = $styles
        'res/layout/bj.xml' = $layout
    }
    $stockApk = New-ResourceApk -Name 'stock' -Files $stockFiles -EmitIds $ids
    $idOf = @{}
    foreach ($line in Get-Content -LiteralPath $ids) {
        if ($line -match '^[^:]+:(\S+) = (0x[0-9a-f]{8})$') { $idOf[$Matches[1]] = $Matches[2] }
    }
    Assert-True ($idOf['layout/bj'] -like '0x7e*' -and $idOf['color/surface'] -like '0x7e*') `
        "aapt2 did not build package 0x7e: $($idOf.Keys -join ', ')"

    $same = Invoke-Check -Patched $stockApk -Name 'unchanged'
    Assert-True ($same.ExitCode -eq 0) "An unchanged table failed.`n$($same.Output)"
    Assert-True ($same.Output -match 'resolves in the patched table') "An unchanged table was not called whole.`n$($same.Output)"

    $blackFiles = [ordered]@{
        'res/values/colors.xml' = $colors -replace '#ffff3b5c', '#ff000000'
        'res/values/styles.xml' = $styles
        'res/layout/bj.xml' = $layout
    }
    $black = Invoke-Check -Patched (New-ResourceApk -Name 'rewritten' -Files $blackFiles -StableIds $ids) -Name 'rewritten'
    Assert-True ($black.ExitCode -eq 0) "A rewritten color failed the check.`n$($black.Output)"
    Assert-True ($black.Output -match [regex]::Escape("$($idOf['color/accent']) color/accent [default] #ffff3b5c -> #ff000000")) `
        "The rewritten color was not reported with its id and both values.`n$($black.Output)"

    $renamedIds = Join-Path $caseRoot 'renamed-ids.txt'
    [System.IO.File]::WriteAllText($renamedIds, ((Get-Content -LiteralPath $ids -Raw) -replace ':color/accent =', ':color/accent_renamed ='))
    $renamedFiles = [ordered]@{
        'res/values/colors.xml' = $colors -replace '"accent"', '"accent_renamed"'
        'res/values/styles.xml' = $styles -replace '@color/accent<', '@color/accent_renamed<'
        'res/layout/bj.xml' = $layout
    }
    $renamed = Invoke-Check -Patched (New-ResourceApk -Name 'renamed' -Files $renamedFiles -StableIds $renamedIds) -Name 'renamed'
    Assert-True ($renamed.ExitCode -eq 0) "A renamed entry that keeps its id failed.`n$($renamed.Output)"
    Assert-True ($renamed.Output -match [regex]::Escape("$($idOf['color/accent']) color/accent is color/accent_renamed")) `
        "The renamed entry was not reported.`n$($renamed.Output)"

    $noLayoutFiles = [ordered]@{
        'res/values/colors.xml' = $colors
        'res/values/styles.xml' = $styles
    }
    $lost = Invoke-Check -Patched (New-ResourceApk -Name 'lost-layout' -Files $noLayoutFiles -StableIds $ids) -Name 'lost-layout'
    Assert-True ($lost.ExitCode -eq 1) "A table that lost layout/bj passed.`n$($lost.Output)"
    Assert-True ($lost.Output -match [regex]::Escape("FAIL $($idOf['layout/bj']) layout/bj: not in the patched table")) `
        "The lost layout's failure did not name its id.`n$($lost.Output)"
    Assert-True ((Get-Content -LiteralPath $lost.Report -Raw) -match [regex]::Escape($idOf['layout/bj'])) `
        'The report file did not name the lost id.'

    $noFile = Copy-ApkWith -From $stockApk -Name 'lost-file' -Change {
        param($archive)
        $entry = $archive.GetEntry('res/layout/bj.xml')
        if (-not $entry) { throw 'the stock APK has no res/layout/bj.xml to remove' }
        $entry.Delete()
    }
    $file = Invoke-Check -Patched $noFile -Name 'lost-file'
    Assert-True ($file.ExitCode -eq 1) "A layout whose file left the archive passed.`n$($file.Output)"
    Assert-True ($file.Output -match [regex]::Escape("FAIL $($idOf['layout/bj']) layout/bj [default]: names res/layout/bj.xml, which is not in the patched archive")) `
        "The missing file's failure did not name the id and the path.`n$($file.Output)"

    $noItemFiles = [ordered]@{
        'res/values/colors.xml' = $colors
        'res/values/styles.xml' = $styles -replace '(?m)^\s*<item name="android:textColorPrimary">@color/accent</item>\r?\n', ''
        'res/layout/bj.xml' = $layout
    }
    $item = Invoke-Check -Patched (New-ResourceApk -Name 'lost-item' -Files $noItemFiles -StableIds $ids) -Name 'lost-item'
    Assert-True ($item.ExitCode -eq 1) "A style that lost an item passed.`n$($item.Output)"
    Assert-True ($item.Output -match ([regex]::Escape("FAIL $($idOf['style/SearchTheme']) style/SearchTheme [default] item 0x01010036") + '.*the item is gone')) `
        "The lost item's failure did not name the style and the attr.`n$($item.Output)"

    # A value that points at nothing: the theme's window background, as bytes, moved to an id the
    # table does not have.
    $surface = [Convert]::ToInt32($idOf['color/surface'].Substring(2), 16)
    $nowhere = $surface -bor 0xfff0
    $dangling = Copy-ApkWith -From $stockApk -Name 'dangling' -Change {
        param($archive)
        $entry = $archive.GetEntry('resources.arsc')
        $stream = $entry.Open()
        try { $buffer = [System.IO.MemoryStream]::new(); $stream.CopyTo($buffer); $bytes = $buffer.ToArray() } finally { $stream.Dispose() }
        $pattern = [byte[]](@(8, 0, 0, 1) + [BitConverter]::GetBytes([int]$surface))
        $hits = @()
        for ($i = 0; $i -le $bytes.Length - $pattern.Length; $i++) {
            $match = $true
            for ($j = 0; $j -lt $pattern.Length; $j++) { if ($bytes[$i + $j] -ne $pattern[$j]) { $match = $false; break } }
            if ($match) { $hits += $i }
        }
        if ($hits.Count -ne 1) { throw "expected one reference to color/surface in the table, found $($hits.Count)" }
        [BitConverter]::GetBytes([int]$nowhere).CopyTo($bytes, $hits[0] + 4)
        $entry.Delete()
        $replacement = $archive.CreateEntry('resources.arsc', [System.IO.Compression.CompressionLevel]::NoCompression)
        $out = $replacement.Open()
        try { $out.Write($bytes, 0, $bytes.Length) } finally { $out.Dispose() }
    }
    $pointer = Invoke-Check -Patched $dangling -Name 'dangling'
    $nowhereHex = '0x{0:x8}' -f $nowhere
    Assert-True ($pointer.ExitCode -eq 1) "A reference to nothing passed.`n$($pointer.Output)"
    Assert-True ($pointer.Output -match ([regex]::Escape("FAIL $($idOf['style/SearchTheme']) style/SearchTheme [default] item 0x01010054") + ".*points at $nowhereHex, which is not in the patched table")) `
        "The dangling reference's failure did not name the style, the attr and the id.`n$($pointer.Output)"
} finally {
    if ($caseRoot.StartsWith($requiredPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and `
        (Test-Path -LiteralPath $caseRoot)) {
        Remove-Item -LiteralPath $caseRoot -Recurse -Force
    }
}

$global:LASTEXITCODE = 0
Write-Host '[scripts] resource table check contracts passed'
