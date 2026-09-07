<#
.SYNOPSIS
    Checks a push before it leaves the machine.

.DESCRIPTION
    This project builds nothing on GitHub, so a push is the last point at which anything can
    be checked. Its only reported failure so far was a source index that advertised a bundle
    the release did not carry, which the release check catches when it is actually run.

    Called by .git/hooks/pre-push with the remote name and URL, reading the pushed refs from
    standard input the way git supplies them. Run scripts/install-hooks.ps1 once to wire it up.

    Only what changed is checked: runtime tests when extension or patch sources move, and the
    release facts when a published file moves. Set HUSHFEED_SKIP_PRE_PUSH=1 to push anyway.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)][string]$RemoteName,
    [Parameter(Position = 1)][string]$RemoteUrl,
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string[]]$ChangedPaths
)

$ErrorActionPreference = 'Stop'
$zeroObject = '0' * 40

function Write-Step {
    param([string]$Message)
    Write-Host "[pre-push] $Message"
}

function Get-PushedPaths {
    <#
        Git writes "<local ref> <local sha> <remote ref> <remote sha>" per ref on stdin. A remote
        sha of all zeroes means the branch is new there, so compare against the upstream default
        instead of diffing against nothing and checking the entire history.
    #>
    $paths = New-Object System.Collections.Generic.HashSet[string]
    foreach ($line in @($input)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line.Trim() -split '\s+'
        if ($parts.Count -lt 4) { continue }
        $localSha = $parts[1]
        $remoteSha = $parts[3]
        if ($localSha -eq $zeroObject) { continue }

        if ($remoteSha -eq $zeroObject) {
            $range = $localSha
            $names = git diff --name-only "$localSha^" $localSha 2>$null
        } else {
            $range = "$remoteSha..$localSha"
            $names = git diff --name-only $remoteSha $localSha 2>$null
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read what $range changes. Fetch the remote and try again."
        }
        foreach ($name in @($names)) {
            if (-not [string]::IsNullOrWhiteSpace($name)) { [void]$paths.Add($name.Trim()) }
        }
    }
    return $paths
}

if ($env:HUSHFEED_SKIP_PRE_PUSH -eq '1') {
    Write-Step 'skipped by HUSHFEED_SKIP_PRE_PUSH'
    exit 0
}

Push-Location $Root
try {
    if ($PSBoundParameters.ContainsKey('ChangedPaths')) {
        $paths = New-Object System.Collections.Generic.HashSet[string]
        foreach ($name in @($ChangedPaths)) { [void]$paths.Add($name) }
    } else {
        $paths = $input | Get-PushedPaths
    }

    if ($paths.Count -eq 0) {
        Write-Step 'nothing to check'
        exit 0
    }

    $touchesCode = @($paths | Where-Object { $_ -like 'extensions/*' -or $_ -like 'patches/*' }).Count -gt 0
    $touchesRelease = @($paths | Where-Object {
        $_ -eq 'patches-bundle.json' -or $_ -eq 'patches-list.json' -or
        $_ -eq 'gradle.properties' -or $_ -eq 'README.md'
    }).Count -gt 0

    if ($touchesCode) {
        Write-Step 'extension or patch sources changed, running the runtime tests'
        $governor = Join-Path $HOME '.claude/scripts/build-governor.ps1'
        if (Test-Path -LiteralPath $governor) {
            & $governor -ProjectDir $Root -MinFreeGb 2 -NoReap -Tasks ':extensions:tiktok:test'
        } else {
            & (Join-Path $Root 'gradlew.bat') ':extensions:tiktok:test'
        }
        if ($LASTEXITCODE -ne 0) {
            throw 'The runtime tests failed. Fix them or push with HUSHFEED_SKIP_PRE_PUSH=1.'
        }
    }

    if ($touchesRelease) {
        Write-Step 'a published file changed, checking the release facts'
        $validate = Join-Path $Root 'scripts/validate-release-facts.ps1'
        $artifacts = @(Get-ChildItem -LiteralPath (Join-Path $Root 'patches/build/libs') `
            -Filter '*.mpp' -File -ErrorAction SilentlyContinue)
        if ($artifacts.Count -eq 1) {
            # The bundle this checkout built, so the indexed URL, its hash and the hosted
            # checksum entry can all be compared against something real.
            & $validate -Root $Root -VerifyPublishedAsset -ArtifactPath $artifacts[0].FullName
        } else {
            if ($artifacts.Count -gt 1) {
                Write-Step "found $($artifacts.Count) bundles, so the published asset is not checked"
            }
            & $validate -Root $Root
        }
        if ($LASTEXITCODE -ne 0) {
            throw 'The release facts do not agree. Fix them or push with HUSHFEED_SKIP_PRE_PUSH=1.'
        }
    }

    if (-not $touchesCode -and -not $touchesRelease) {
        Write-Step 'no code or published file changed'
    }
    Write-Step 'ok'
    exit 0
} finally {
    Pop-Location
}
