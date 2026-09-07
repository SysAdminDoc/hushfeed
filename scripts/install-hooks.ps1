<#
.SYNOPSIS
    Installs this repository's git hooks.

.DESCRIPTION
    Writes .git/hooks/pre-push so a push runs scripts/pre-push.ps1 first. Hooks live outside the
    working tree, so every checkout needs this run once. Pass -Force to replace a hook that is
    already there and was not written by this script.
#>
[CmdletBinding()]
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$hooksDirectory = & git -C $Root rev-parse --git-path hooks
if ($LASTEXITCODE -ne 0) {
    throw "$Root is not a git checkout."
}
if (-not [System.IO.Path]::IsPathRooted($hooksDirectory)) {
    $hooksDirectory = Join-Path $Root $hooksDirectory
}
if (-not (Test-Path -LiteralPath $hooksDirectory)) {
    New-Item -ItemType Directory -Path $hooksDirectory | Out-Null
}

$marker = '# hushfeed-pre-push'
$hookPath = Join-Path $hooksDirectory 'pre-push'
if ((Test-Path -LiteralPath $hookPath) -and -not $Force) {
    $existing = Get-Content -LiteralPath $hookPath -Raw
    if ($existing -notmatch [regex]::Escape($marker)) {
        throw "$hookPath already exists and was not written by this script. Pass -Force to replace it."
    }
}

# Git runs hooks through its bundled sh even on Windows, so the hook is a shell script that
# hands the refs on stdin to PowerShell. pwsh where it exists, Windows PowerShell otherwise.
$hook = @"
#!/bin/sh
$marker
script="`$(git rev-parse --show-toplevel)/scripts/pre-push.ps1"
if command -v pwsh >/dev/null 2>&1; then
    shell=pwsh
else
    shell=powershell
fi
exec "`$shell" -NoProfile -ExecutionPolicy Bypass -File "`$script" "`$@"
"@

Set-Content -LiteralPath $hookPath -Value ($hook -replace "`r`n", "`n") -NoNewline -Encoding utf8NoBOM
if ($IsLinux -or $IsMacOS) {
    & chmod +x $hookPath
}

Write-Host "Installed $hookPath"
Write-Host 'A push now runs the runtime tests when extension or patch sources changed, and the'
Write-Host 'release check when a published file changed. Set HUSHFEED_SKIP_PRE_PUSH=1 to skip it.'
