<#
.SYNOPSIS
    Find the smallest heap the patcher finishes patching in.

.DESCRIPTION
    Morphe Manager patches with a memory limit the user can set. This runs the desktop CLI at
    a given -Xmx and accepts a case only when the process, result report and saved APK all pass
    the same gates as the full verification script. An observed OutOfMemoryError is retained as
    a useful measurement, while a partial or contradictory result is reported as invalid.

    Cases are "<what>:<Xmx>", where <what> is "settings" (the Settings patch alone) or "all"
    (every patch in the list).

.EXAMPLE
    $env:HUSHFEED_WORKDIR = "C:\scratch"
    $env:HUSHFEED_JAVA = "C:\jdk-21\bin\java.exe"
    scripts/measure-patch-heap.ps1 settings:640m settings:768m all:768m
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Cases
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

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
    param([string]$Path, [string]$Root)
    try {
        $safe = Resolve-WithinRoot -Path $Path -Root $Root
        if (Test-Path -LiteralPath $safe) { Remove-Item -LiteralPath $safe -Recurse -Force -ErrorAction Stop }
    } catch {
        Write-Warning "Could not remove generated path: $($_.Exception.Message)"
    }
}

function Test-ApkFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
        try {
            $dex = @($archive.Entries | Where-Object { $_.FullName -match '(^|/)classes\d*\.dex$' })
            $manifest = @($archive.Entries | Where-Object { $_.FullName -eq 'AndroidManifest.xml' })
            return $dex.Count -gt 0 -and $manifest.Count -gt 0
        } finally {
            $archive.Dispose()
        }
    } catch {
        return $false
    }
}

function Get-ReportPatchNames {
    param([object]$Entries)
    $names = New-Object System.Collections.Generic.List[string]
    foreach ($entry in @($Entries)) {
        if ($null -eq $entry) { continue }
        if ($entry -is [string]) {
            $names.Add([string]$entry)
            continue
        }
        $name = $entry.PSObject.Properties['name']
        if ($null -ne $name -and $null -ne $name.Value) { $names.Add([string]$name.Value) }
    }
    return $names.ToArray()
}

function Test-SameNames {
    param([string[]]$Expected, [string[]]$Actual)
    $expectedCounts = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::Ordinal)
    foreach ($name in @($Expected)) {
        if ($null -eq $name) { return $false }
        if (-not $expectedCounts.ContainsKey($name)) { $expectedCounts[$name] = 0 }
        $expectedCounts[$name]++
    }
    $actualCounts = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::Ordinal)
    foreach ($name in @($Actual)) {
        if ($null -eq $name) { return $false }
        if (-not $actualCounts.ContainsKey($name)) { $actualCounts[$name] = 0 }
        $actualCounts[$name]++
    }
    if ($expectedCounts.Count -ne $actualCounts.Count) { return $false }
    foreach ($name in $expectedCounts.Keys) {
        if (-not $actualCounts.ContainsKey($name) -or $actualCounts[$name] -ne $expectedCounts[$name]) { return $false }
    }
    return $true
}

function Test-TrueBoolean {
    param([object]$Value)
    return $Value -is [bool] -and [bool]$Value
}

function Test-PatchingReport {
    param([object]$Report, [string[]]$ExpectedNames, [string]$OutputPath)
    if ($null -eq $Report) { return [pscustomobject]@{ Valid = $false; Reason = 'missing or invalid result JSON' } }
    $success = $Report.PSObject.Properties['success']
    $steps = @($Report.patchingSteps)
    $stepsOk = $steps.Count -gt 0 -and @($steps | Where-Object {
        $property = $_.PSObject.Properties['success']
        $null -eq $property -or -not (Test-TrueBoolean $property.Value)
    }).Count -eq 0
    $failed = @($Report.failedPatches)
    $namesOk = Test-SameNames -Expected $ExpectedNames -Actual (Get-ReportPatchNames $Report.appliedPatches)
    $targetOk = $null -ne $Report.PSObject.Properties['packageName'] -and
        $null -ne $Report.PSObject.Properties['packageVersion'] -and
        [string]::Equals([string]$Report.packageName, 'com.zhiliaoapp.musically', [System.StringComparison]::Ordinal) -and
        [string]::Equals([string]$Report.packageVersion, '46.2.3', [System.StringComparison]::Ordinal)
    $outputOk = Test-ApkFile $OutputPath
    $valid = $null -ne $success -and (Test-TrueBoolean $success.Value) -and $stepsOk -and
        $failed.Count -eq 0 -and $namesOk -and $targetOk -and $outputOk
    $reason = if ($valid) { 'ok' } else {
        $parts = New-Object System.Collections.Generic.List[string]
        if ($null -eq $success -or -not (Test-TrueBoolean $success.Value)) { $parts.Add('report.success is false or not a boolean') }
        if (-not $stepsOk) { $parts.Add('a patching step failed or is missing') }
        if ($failed.Count -ne 0) { $parts.Add("$($failed.Count) failed patches") }
        if (-not $namesOk) { $parts.Add('requested and applied patch names differ') }
        if (-not $targetOk) { $parts.Add('unexpected package or version') }
        if (-not $outputOk) { $parts.Add('saved APK is missing or invalid') }
        $parts -join '; '
    }
    return [pscustomobject]@{ Valid = $valid; Reason = $reason }
}

$work = $env:HUSHFEED_WORKDIR
if (-not $work) { throw 'Set HUSHFEED_WORKDIR to a directory holding morphe-desktop.jar and the fixture APK.' }
New-Item -ItemType Directory -Force -Path $work | Out-Null
$workRoot = (Resolve-Path -LiteralPath $work).Path
$java = if ($env:HUSHFEED_JAVA) { $env:HUSHFEED_JAVA } else { 'java' }
$jar = Join-Path $workRoot 'morphe-desktop.jar'
$apk = if ($env:HUSHFEED_APK) { $env:HUSHFEED_APK } else { Join-Path $workRoot 'tt/native-fixture.apk' }
$bundle = Get-ChildItem (Join-Path $root 'patches/build/libs') -Filter '*.mpp' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $bundle) { throw 'No bundle found. Run :patches:buildAndroid first.' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Desktop CLI jar not found: $jar" }
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK not found: $apk" }

try {
    $all = @((Get-Content -LiteralPath (Join-Path $root 'patches-list.json') -Raw | ConvertFrom-Json).patches |
        ForEach-Object { $_.name })
} catch {
    throw "Could not read patches-list.json: $($_.Exception.Message)"
}
if ($all.Count -eq 0) { throw 'No patches listed in patches-list.json.' }
if (@($Cases).Count -eq 0) { throw 'Provide at least one case such as settings:640m.' }

$invalidCases = 0
foreach ($case in @($Cases)) {
    $parts = $case -split ':', 2
    if ($parts.Count -ne 2 -or $parts[0] -notin @('settings', 'all') -or
        $parts[1] -notmatch '^\d+[kmg]$') {
        Write-Warning "[$case] invalid case format; use settings:640m or all:768m"
        $invalidCases++
        continue
    }
    $which = $parts[0]
    $mx = $parts[1]
    $runId = [guid]::NewGuid().ToString('N')
    $runDir = Join-Path $workRoot "heap-$runId"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    $out = Resolve-WithinRoot -Path (Join-Path $runDir 'output.apk') -Root $workRoot
    $temp = Resolve-WithinRoot -Path (Join-Path $runDir 'tmp') -Root $workRoot
    $result = Resolve-WithinRoot -Path (Join-Path $workRoot "heap-$which-$mx-$runId-result.json") -Root $workRoot
    $enable = if ($which -eq 'settings') { @('-e', 'Settings') } else {
        $argsForAll = @()
        foreach ($name in $all) { $argsForAll += '-e'; $argsForAll += $name }
        $argsForAll
    }
    $arguments = @('patch', '--exclusive', '--continue-on-error', '--unsigned', '-p', $bundle.FullName,
        '-o', $out, '-t', $temp, '-r', $result) + $enable + @($apk)
    try {
        $log = @(& $java "-Xmx$mx" '-jar' $jar @arguments 2>&1)
        $cliExitCode = $LASTEXITCODE
        $outOfMemory = @($log | Where-Object { [string]$_ -match 'OutOfMemoryError|GC overhead' }).Count -gt 0
        $report = $null
        if (Test-Path -LiteralPath $result -PathType Leaf) {
            try { $report = Get-Content -LiteralPath $result -Raw | ConvertFrom-Json }
            catch { Write-Warning "[$which @ -Xmx$mx] could not parse result JSON: $($_.Exception.Message)" }
        }
        $validation = Test-PatchingReport -Report $report -ExpectedNames $(if ($which -eq 'settings') { @('Settings') } else { $all }) -OutputPath $out
        if ($outOfMemory) {
            $verdict = 'OUT OF MEMORY'
            $invalidCases++
        } elseif ($cliExitCode -eq 0 -and $validation.Valid) {
            $verdict = 'ok'
        } else {
            $verdict = 'INVALID'
            $invalidCases++
        }
        Write-Host "[$which @ -Xmx$mx] $verdict (CLI $cliExitCode, report $($validation.Reason))"
        Write-Host "[$which @ -Xmx$mx] result file: $result"
    } catch {
        Write-Warning "[$which @ -Xmx$mx] failed to run CLI: $($_.Exception.Message)"
        $invalidCases++
    } finally {
        Remove-GeneratedPath -Path $runDir -Root $workRoot
    }
}

exit $invalidCases
