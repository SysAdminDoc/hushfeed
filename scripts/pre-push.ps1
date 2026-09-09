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
    [string]$Root,
    [string[]]$ChangedPaths
)

$ErrorActionPreference = 'Stop'

# Not a parameter default. Windows PowerShell leaves $PSScriptRoot empty while it evaluates the
# defaults of a script that declares any [Parameter(Position = N)], so the default threw and the
# hook failed before it checked anything. The hook prefers pwsh, which does not have this, and
# falls back to Windows PowerShell wherever pwsh is off the PATH: a git hook runs with git's
# environment, so that is the ordinary case rather than the rare one.
if (-not $Root) { $Root = Split-Path -Parent $PSScriptRoot }
$zeroObject = '0' * 40

function Write-Step {
    param([string]$Message)
    Write-Host "[pre-push] $Message"
}

function Get-PushedPaths {
    <#
        Git writes "<local ref> <local sha> <remote ref> <remote sha>" per ref on stdin. A remote
        sha of all zeroes means the branch is new there, so compare against its first parent
        instead of diffing against nothing and checking the entire history.

        Read from the console rather than $input: a script started with -File binds stdin to its
        parameters, so piping into it fails to bind and leaves $input empty, which made the hook
        report that a push changed nothing.
    #>
    param([string]$Text)

    $paths = New-Object System.Collections.Generic.HashSet[string]
    foreach ($line in ($Text -split "`r?`n")) {
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
        $refs = ''
        if (-not [Console]::IsInputRedirected) {
            throw 'No pushed refs on standard input. Run this from the pre-push hook, or pass -ChangedPaths.'
        }
        $refs = [Console]::In.ReadToEnd()
        $paths = Get-PushedPaths -Text $refs
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
        Write-Step 'extension or patch sources changed, running the runtime tests and the API level check'

        # The Morphe settings plugin resolves from GitHub Packages, which needs a reader token.
        # A hook runs with git's environment, not the shell's, so these are usually absent and
        # the build fails while applying the plugin, long before a test runs.
        if (-not $env:GITHUB_ACTOR -or -not $env:GITHUB_TOKEN) {
            if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
                throw ('Set GITHUB_ACTOR and GITHUB_TOKEN, or install the gh CLI: the patches ' +
                    'plugin resolves from GitHub Packages and cannot be applied without them.')
            }
            $login = (& gh api user --jq .login 2>$null)
            $token = (& gh auth token 2>$null)
            if ([string]::IsNullOrWhiteSpace($login) -or [string]::IsNullOrWhiteSpace($token)) {
                throw 'gh is not signed in, so the patches plugin cannot be resolved. Run gh auth login.'
            }
            $env:GITHUB_ACTOR = $login
            $env:GITHUB_TOKEN = $token
        }

        # The lint runs alongside the tests because the tests cannot see this class of defect at
        # all: they run on a desktop JVM, where every java.util method exists whatever the
        # payload's floor says. Only the API level check reads minSdk, and it reads the SDK_INT
        # guards with it, so a call that is properly guarded stays quiet.
        $tasks = @(
            ':extensions:tiktok:test',
            ':extensions:shared:library:lint',
            ':extensions:tiktok:lint'
        )
        $governor = Join-Path $HOME '.claude/scripts/build-governor.ps1'
        $global:LASTEXITCODE = 0
        if (Test-Path -LiteralPath $governor) {
            & $governor -ProjectDir $Root -MinFreeGb 2 -NoReap -Tasks $tasks
        } else {
            & (Join-Path $Root 'gradlew.bat') @tasks
        }
        if ($LASTEXITCODE -ne 0) {
            throw ('The runtime test build did not pass. Read the output above: it says whether a ' +
                'test failed, an API level above the payload floor was reached, or the build could ' +
                'not start. Push anyway with HUSHFEED_SKIP_PRE_PUSH=1.')
        }
    }

    if ($touchesRelease) {
        Write-Step 'a published file changed, checking the release facts'
        $validate = Join-Path $Root 'scripts/validate-release-facts.ps1'
        $global:LASTEXITCODE = 0
        # The sources and javadoc jars share the .mpp extension, so an unfiltered listing found
        # three files after every build, took the branch below, and the hash comparison this
        # exists for never ran once.
        $artifacts = @(Get-ChildItem -LiteralPath (Join-Path $Root 'patches/build/libs') `
            -Filter '*.mpp' -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch '(^|-)(sources|javadoc)\.mpp$' })
        if ($artifacts.Count -eq 1) {
            # The bundle this checkout built, so the indexed URL, its hash and the hosted
            # checksum entry can all be compared against something real.
            & $validate -Root $Root -VerifyPublishedAsset -ArtifactPath $artifacts[0].FullName
        } else {
            if ($artifacts.Count -gt 1) {
                Write-Step "found $($artifacts.Count) bundles, so the hosted artifact is not compared"
            } else {
                Write-Step 'no local bundle here, so the hosted artifact is not compared'
            }
            # The indexed URL is still fetched. Only the byte-for-byte hash comparison needs a
            # local bundle to compare against.
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
