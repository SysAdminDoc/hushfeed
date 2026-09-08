<#
.SYNOPSIS
    Finds a JDK new enough to run the Morphe desktop CLI.

.DESCRIPTION
    The CLI is built for a newer JDK than the java that is usually first on PATH here, and it
    says so through an UnsupportedClassVersionError on a class nobody recognises. Every script
    that shells out to it shares this, so they all fail the same readable way.

    Candidates are tried in order and each one is asked its version, so a JAVA_HOME pinned to an
    old JDK for some other build cannot shadow a new enough java already on PATH.
#>

function Get-JavaMajorVersion {
    <# Returns the major version of a java, or 0 if it will not run. #>
    param([string]$Java)

    try {
        $global:LASTEXITCODE = 0
        $output = & $Java '-version' 2>&1
    } catch {
        return 0
    }
    if ($LASTEXITCODE -ne 0) { return 0 }
    foreach ($line in @($output)) {
        # Both shapes: `openjdk version "21.0.12"` and the older `java version "1.8.0_401"`.
        if ("$line" -match 'version "1\.([0-9]+)') { return [int]$Matches[1] }
        if ("$line" -match 'version "([0-9]+)') { return [int]$Matches[1] }
    }
    return 0
}

function Resolve-Java {
    param([string]$Explicit, [int]$Minimum = 21)

    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($candidate in @($Explicit, $env:HUSHFEED_JAVA)) {
        if ($candidate) { $candidates.Add($candidate) }
    }
    # Ahead of JAVA_HOME on purpose: a JAVA_HOME held at an old JDK for another build must not
    # turn a machine whose PATH java is new enough into a failing one.
    $candidates.Add('java')
    foreach ($root in @($env:JAVA_HOME, $env:JDK_HOME)) {
        if (-not $root) { continue }
        foreach ($leaf in @('bin/java.exe', 'bin/java')) {
            $path = Join-Path $root $leaf
            if (Test-Path -LiteralPath $path -PathType Leaf) {
                $candidates.Add($path)
                break
            }
        }
    }

    $seen = @{}
    $rejected = @()
    foreach ($candidate in $candidates) {
        if ($seen.ContainsKey($candidate)) { continue }
        $seen[$candidate] = $true
        $version = Get-JavaMajorVersion -Java $candidate
        if ($version -ge $Minimum) { return $candidate }
        $rejected += if ($version -gt 0) { "$candidate is Java $version" } else { "$candidate would not run" }
    }

    throw ("No Java $Minimum or newer was found, and the Morphe desktop CLI needs one. Set " +
        'HUSHFEED_JAVA to a JDK, or pass -Java. Tried: ' + ($rejected -join '; ') + '.')
}
