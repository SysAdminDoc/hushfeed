<#
.SYNOPSIS
    Check public release facts against the generated patch list.

.DESCRIPTION
    The generated patches-list.json is the local source for the release version, target
    package, target version and patch count. This check makes README.md, patches-bundle.json
    and the recorded runtime test count agree before a release is published.
#>
[CmdletBinding()]
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$VerifyPublishedAsset,
    [string]$ArtifactPath,
    # The Morphe desktop CLI, the only thing that can read a patch list back out of a bundle
    # this checkout did not build. Falls back to HUSHFEED_DESKTOP_JAR.
    [string]$DesktopJar,
    # The JDK that runs it. Falls back to HUSHFEED_JAVA, then JAVA_HOME.
    [string]$Java,
    # Only for running the rest of the checks with no network. Nothing in the repo
    # passes it; the pre-push escape hatch is HUSHFEED_SKIP_PRE_PUSH=1.
    [switch]$SkipUrlCheck
)

$ErrorActionPreference = 'Stop'

function Resolve-DesktopCli {
    <#
    .SYNOPSIS
        The Morphe desktop CLI jar, or $null when there is none to be found.
    .DESCRIPTION
        Taken in order from -DesktopJar, HUSHFEED_DESKTOP_JAR, HUSHFEED_WORKDIR and the repo's
        own build/morphe-tools. The jar ships under its version, so the newest by write time is
        taken rather than one filename that goes stale: sorting those as text puts 1.9.0 above
        1.15.0.
    #>
    param([string]$Explicit, [string]$Root)

    if ($Explicit) { return $Explicit }
    if ($env:HUSHFEED_DESKTOP_JAR) { return $env:HUSHFEED_DESKTOP_JAR }
    $searched = @($env:HUSHFEED_WORKDIR, (Join-Path $Root 'build/morphe-tools')) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }
    foreach ($directory in $searched) {
        $found = @(Get-ChildItem -LiteralPath $directory -Filter 'morphe-desktop*.jar' -File `
            -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
        if ($found.Count -gt 0) { return $found[0].FullName }
    }
    return $null
}

function Resolve-Java {
    <#
    .SYNOPSIS
        A java to run the desktop CLI with, taken from -Java, HUSHFEED_JAVA or JAVA_HOME.
    .DESCRIPTION
        The CLI is built for a newer JDK than the one that is usually first on PATH here, and
        the pre-push hook has no way to pass -Java. A bare 'java' therefore failed the check on
        a machine that did have a new enough JDK, just not in front.
    #>
    param([string]$Explicit)

    if ($Explicit) { return $Explicit }
    if ($env:HUSHFEED_JAVA) { return $env:HUSHFEED_JAVA }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        $candidate = Join-Path $env:JAVA_HOME 'bin/java'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    return 'java'
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required file is missing: $Path"
    }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        throw "Could not read JSON from ${Path}: $($_.Exception.Message)"
    }
}

function Require-Match {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Description
    )
    if ($Text -notmatch $Pattern) {
        throw "${Description} does not match the generated release facts."
    }
}

function Assert-AssetReachable {
    param([Uri]$Uri)
    try {
        $response = Invoke-WebRequest -Uri $Uri -Method Head -MaximumRedirection 5 `
            -TimeoutSec 60 -SkipHttpErrorCheck
    } catch {
        throw ("Could not reach the indexed bundle URL ${Uri}: $($_.Exception.Message). " +
            'If the network is down, push with HUSHFEED_SKIP_PRE_PUSH=1 and run this again later.')
    }
    $status = [int]$response.StatusCode
    if ($status -ne 200) {
        throw ("The indexed bundle URL ${Uri} answered HTTP ${status}. " +
            'The Manager fetches that address, so the release it names has to exist first.')
    }
    Write-Host ("[release] indexed URL answers 200: " + $Uri)
}

$rootPath = (Resolve-Path -LiteralPath $Root).Path
$patchListPath = Join-Path $rootPath 'patches-list.json'
$bundlePath = Join-Path $rootPath 'patches-bundle.json'
$propertiesPath = Join-Path $rootPath 'gradle.properties'
$readmePath = Join-Path $rootPath 'README.md'

$patchList = Read-JsonFile $patchListPath
$bundle = Read-JsonFile $bundlePath
$readme = Get-Content -LiteralPath $readmePath -Raw
$properties = Get-Content -LiteralPath $propertiesPath -Raw

$sourceVersion = [string]$patchList.version
if ($sourceVersion -notmatch '^v\d+\.\d+\.\d+$') {
    throw "patches-list.json has an invalid version: $sourceVersion"
}
$releaseVersion = $sourceVersion.Substring(1)
$propertyMatch = [regex]::Match($properties, '(?m)^\s*version\s*=\s*(\S+)\s*$')
if (-not $propertyMatch.Success -or $propertyMatch.Groups[1].Value -ne $releaseVersion) {
    throw "gradle.properties does not match $sourceVersion."
}

$patches = @($patchList.patches)
if ($patches.Count -eq 0) { throw 'patches-list.json contains no patches.' }
$patchCount = $patches.Count

$targets = @{}
foreach ($patch in $patches) {
    foreach ($property in $patch.compatiblePackages.PSObject.Properties) {
        $versions = @($property.Value | ForEach-Object { [string]$_ })
        if (-not $targets.ContainsKey($property.Name)) { $targets[$property.Name] = @() }
        $targets[$property.Name] += $versions
    }
}
$targetPackages = @($targets.Keys | Sort-Object)
if ($targetPackages.Count -ne 1) {
    throw "Expected one compatible package, found $($targetPackages -join ', ')."
}
$targetPackage = $targetPackages[0]
$targetVersions = @($targets[$targetPackage] | Sort-Object -Unique)
if ($targetVersions.Count -ne 1) {
    throw "Expected one compatible version for $targetPackage, found $($targetVersions -join ', ')."
}
$targetVersion = $targetVersions[0]

if ([string]$bundle.version -ne $releaseVersion) {
    throw "patches-bundle.json version does not match $sourceVersion."
}
Require-Match -Text ([string]$bundle.download_url) -Pattern "/v$([regex]::Escape($releaseVersion))/patches-$([regex]::Escape($releaseVersion))\.mpp$" -Description 'patches-bundle.json download URL'

# Matching the pattern only proves the index spells the version right. Reaching the address is
# what catches an index pointed at a tag nobody published, which is how the bundle went missing
# once already, and the hash comparison further down runs only when this checkout built a bundle.
# So the URL is fetched on every run, not only on a release.
$assetUri = [Uri]$bundle.download_url
if ($assetUri.Scheme -ne 'https') {
    throw "The published bundle URL must use HTTPS: $($bundle.download_url)"
}
$assetName = [IO.Path]::GetFileName($assetUri.AbsolutePath)
if ($assetName -ne "patches-$releaseVersion.mpp") {
    throw "The published bundle URL names $assetName instead of patches-$releaseVersion.mpp."
}
if ($SkipUrlCheck) {
    Write-Host '[release] the indexed URL was not fetched because -SkipUrlCheck was given'
} else {
    Assert-AssetReachable -Uri $assetUri
}
Require-Match -Text $readme -Pattern "\b$patchCount patches\b" -Description 'README patch count'
Require-Match -Text $readme -Pattern ([regex]::Escape($targetPackage)) -Description 'README package name'
Require-Match -Text $readme -Pattern "TikTok\s+$([regex]::Escape($targetVersion))(?!\d)" -Description 'README target version'
Require-Match -Text ([string]$bundle.description) -Pattern "\b$patchCount patches\b" -Description 'bundle description patch count'
Require-Match -Text ([string]$bundle.description) -Pattern "$([regex]::Escape($targetVersion))(?!\d)" -Description 'bundle description target version'

# The one line GitHub shows above the README, which is also what search results, the awesome
# lists and the Manager's community button repeat. Nothing here read it until now, and it had
# gone two releases and two patches stale before anyone noticed. The repository it reads is the
# one the index points at, so this cannot drift onto some other fork.
if ($SkipUrlCheck) {
    Write-Host '[release] the repository description was not read because -SkipUrlCheck was given'
} else {
    if ($assetUri.Host -ne 'github.com') {
        throw "The indexed bundle URL is not on github.com, so the repository description cannot be checked: $assetUri"
    }
    $segments = @($assetUri.AbsolutePath.Trim('/') -split '/')
    if ($segments.Count -lt 2) {
        throw "Could not read an owner and repository out of the indexed bundle URL: $assetUri"
    }
    $slug = $segments[0] + '/' + $segments[1]

    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw ('The gh CLI is needed to read the repository description of ' + $slug +
            '. Install it, or pass -SkipUrlCheck to run the rest with no network.')
    }
    $description = (& gh api "repos/$slug" --jq '.description' 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($description)) {
        throw ("Could not read the description of $slug through gh. Run gh auth login, or pass " +
            '-SkipUrlCheck to run the rest with no network.')
    }
    $description = $description.Trim()

    $wanted = @(
        @{ Pattern = "\b$([regex]::Escape($sourceVersion))\b";  Wanted = $sourceVersion }
        @{ Pattern = "\b$patchCount patches\b";                 Wanted = "$patchCount patches" }
        @{ Pattern = "TikTok\s+$([regex]::Escape($targetVersion))(?!\d)"; Wanted = "TikTok $targetVersion" }
    )
    $missing = @($wanted | Where-Object { $description -notmatch $_.Pattern } | ForEach-Object { $_.Wanted })
    if ($missing.Count -gt 0) {
        throw ("The GitHub description of $slug does not say " + ($missing -join ', ') + '. It reads: ' +
            $description + [Environment]::NewLine +
            'Set it with: gh repo edit ' + $slug + ' --description "Hushfeed ' + $sourceVersion +
            ': ... ' + $patchCount + ' patches for TikTok ' + $targetVersion + '."')
    }
    Write-Host ("[release] the GitHub description of " + $slug + " names " + $sourceVersion +
        ", " + $patchCount + " patches and TikTok " + $targetVersion)
}

$testRoot = Join-Path $rootPath 'extensions/tiktok/build/test-results/testDebugUnitTest'
$testFiles = @(Get-ChildItem -LiteralPath $testRoot -Filter '*.xml' -File -ErrorAction SilentlyContinue)
if ($testFiles.Count -eq 0) {
    throw "No runtime test results found under $testRoot. Run :extensions:tiktok:test first."
}

# Gradle leaves the previous run's XML in place, so results from before the last edit satisfy
# every check below and a release can be validated against code that was never tested.
#
# Only the trees the test task actually reads are compared. patches/src is not one of them:
# :extensions:tiktok:test depends on the shared library and the stub, so editing a patch leaves
# the task up to date, no XML is rewritten, and comparing against it would refuse every release
# from then on with no rerun that could clear it.
#
# The newest result is the one to compare. Gradle never removes the XML of a test class that was
# deleted or renamed, and that file keeps its original timestamp through every later run, so
# taking the oldest would refuse forever after the first class is dropped.
$sourceRoots = @('extensions/tiktok/src', 'extensions/shared/library/src') |
    ForEach-Object { Join-Path $rootPath $_ } |
    Where-Object { Test-Path -LiteralPath $_ }
$newestSource = $sourceRoots |
    ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -File -ErrorAction SilentlyContinue } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if ($null -ne $newestSource) {
    $newestResult = $testFiles | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($newestResult.LastWriteTimeUtc -lt $newestSource.LastWriteTimeUtc) {
        throw ("Runtime test results are older than the sources. The newest result " +
            "$($newestResult.Name) was written $($newestResult.LastWriteTimeUtc.ToString('u')) but " +
            "$($newestSource.FullName) changed $($newestSource.LastWriteTimeUtc.ToString('u')). " +
            'Run :extensions:tiktok:test again.')
    }
}
# Gradle clears the results directory on every run and writes only the classes that ran, so a
# filtered run such as --tests *SomeTest leaves that one XML and nothing else. Every count below
# then describes part of a run: on 2026-09-08 this summed 83 tests from six files while the index
# said 682. The failure named the index rather than the filtered run, and anyone reconciling the
# index to match would have written down a number no full run ever produced.
$testSourceRoot = Join-Path $rootPath 'extensions/tiktok/src/test'
if (Test-Path -LiteralPath $testSourceRoot) {
    $sourceClasses = @(Get-ChildItem -LiteralPath $testSourceRoot -Recurse -File -Filter '*Test.java' |
        ForEach-Object { $_.BaseName })
    # TEST-<package>.<Class>.xml, and the package is not needed to tell one class from another.
    $ranClasses = @($testFiles | ForEach-Object { ($_.BaseName -replace '^TEST-', '') -replace '^.*\.', '' })
    $missing = @($sourceClasses | Where-Object { $ranClasses -notcontains $_ } | Sort-Object)
    if ($missing.Count -gt 0) {
        throw ("Runtime test results are missing " + $missing.Count + " of " + $sourceClasses.Count +
            " test classes, so the counts here describe part of a run: " +
            (($missing | Select-Object -First 8) -join ', ') +
            ". Run :extensions:tiktok:test unfiltered.")
    }
}

$testCount = 0
foreach ($file in $testFiles) {
    try {
        $results = [xml](Get-Content -LiteralPath $file.FullName -Raw)
    } catch {
        throw "Could not read test results from $($file.FullName): $($_.Exception.Message)"
    }
    foreach ($suite in @($results.testsuite)) {
        $failed = [int]$suite.failures
        $errors = [int]$suite.errors
        $skipped = [int]$suite.skipped
        if ($failed -gt 0 -or $errors -gt 0 -or $skipped -gt 0) {
            throw "Runtime test suite $($file.Name) has failures=$failed, errors=$errors, skipped=$skipped."
        }
    }
    $testCount += @($results.testsuite.testcase).Count
}
Require-Match -Text ([string]$bundle.description) -Pattern "\b$testCount runtime tests passed\b" -Description 'bundle description test count'

if ($VerifyPublishedAsset) {
    if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
        $ArtifactPath = Join-Path $rootPath "patches/build/libs/patches-$releaseVersion.mpp"
    }
    if (-not (Test-Path -LiteralPath $ArtifactPath -PathType Leaf)) {
        throw "The local release artifact is missing: $ArtifactPath"
    }

    $temporaryArtifact = Join-Path ([IO.Path]::GetTempPath()) ("hushfeed-$([Guid]::NewGuid()).mpp")
    try {
        try {
            $assetResponse = Invoke-WebRequest -Uri $assetUri -OutFile $temporaryArtifact -MaximumRedirection 5 -TimeoutSec 60 -PassThru
        } catch {
            throw "Could not download the indexed bundle URL: $($_.Exception.Message)"
        }
        if ($assetResponse.StatusCode -ne 200) {
            throw "The indexed bundle URL returned HTTP $($assetResponse.StatusCode)."
        }

        # Every other check here reads patches-list.json, which describes the bundle this
        # checkout just built. That moves the moment a patch is added; the file people download
        # does not. The index once advertised 70 patches while the published asset carried 68 and
        # nothing complained, because nothing had read the published asset. This does.
        $localHash = (Get-FileHash -LiteralPath $ArtifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $publishedHash = (Get-FileHash -LiteralPath $temporaryArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($localHash -ne $publishedHash) {
            throw "The hosted bundle hash $publishedHash does not match the local artifact hash $localHash."
        }

        $checksumUri = [Uri]::new($assetUri, 'SHA256SUMS.txt')
        try {
            $checksumResponse = Invoke-WebRequest -Uri $checksumUri -MaximumRedirection 5 -TimeoutSec 60
        } catch {
            throw "Could not download the hosted SHA256SUMS.txt: $($_.Exception.Message)"
        }
        if ($checksumResponse.StatusCode -ne 200) {
            throw "The hosted SHA256SUMS.txt returned HTTP $($checksumResponse.StatusCode)."
        }
        $checksumText = [Text.Encoding]::UTF8.GetString([byte[]]$checksumResponse.Content)
        $checksumMatch = [regex]::Match(
            $checksumText,
            "(?im)^\s*([0-9a-f]{64})\s+\*?$([regex]::Escape($assetName))\s*$"
        )
        if (-not $checksumMatch.Success) {
            throw "SHA256SUMS.txt has no entry for $assetName."
        }
        $listedHash = $checksumMatch.Groups[1].Value.ToLowerInvariant()
        if ($listedHash -ne $publishedHash) {
            throw "SHA256SUMS.txt lists $listedHash for $assetName, but the hosted artifact is $publishedHash."
        }
        Write-Host ("[release] verified " + $assetName + " from the indexed URL; sha256=" + $publishedHash)
        # No caller passed -DesktopJar and nothing in the repo set the variable, so this check
        # printed "NOT COUNTED" and passed on every run it has ever had. A switch named
        # -VerifyPublishedAsset that quietly skips the only check reading the published asset is
        # worse than no switch: look for the CLI, and say so plainly when there is none.
        #
        # Last of the three on purpose. It is the only one needing a tool from outside the
        # repository, so running it first meant a machine without that tool also lost the hash
        # and checksum comparisons, which need nothing but the download.
        $countJar = Resolve-DesktopCli -Explicit $DesktopJar -Root $Root
        if (-not $countJar) {
            throw ('The published bundle was downloaded but its patches cannot be counted: no ' +
                'Morphe desktop CLI was found. Pass -DesktopJar, set HUSHFEED_DESKTOP_JAR, or put ' +
                'morphe-desktop*.jar under HUSHFEED_WORKDIR or build/morphe-tools, so the ' +
                $patchCount + ' patches the index describes can be compared against the asset.')
        } elseif (-not (Test-Path -LiteralPath $countJar -PathType Leaf)) {
            throw "The Morphe desktop CLI is missing: $countJar"
        } else {
            $javaCommand = Resolve-Java -Explicit $Java
            $listing = Join-Path ([IO.Path]::GetTempPath()) ("hushfeed-$([Guid]::NewGuid()).txt")
            try {
                # --out keeps the list clear of the CLI's own log lines, which share stdout.
                $global:LASTEXITCODE = 0
                $cliOutput = & $javaCommand '-jar' $countJar 'list-patches' "--patches=$temporaryArtifact" `
                    '-d=false' '-i=false' "--out=$listing" 2>&1
                if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $listing -PathType Leaf)) {
                    $detail = $cliOutput -join ' '
                    # The one failure worth naming: the CLI is built for a newer JDK than the
                    # java that was found, which reads as an unrelated class loading error.
                    if ($detail -match 'UnsupportedClassVersionError') {
                        throw ("The Morphe desktop CLI needs a newer JDK than $javaCommand. " +
                            'Set HUSHFEED_JAVA or JAVA_HOME to a JDK 21 or newer, or pass -Java. ' +
                            $detail)
                    }
                    throw "Could not list the patches in the published bundle: $detail"
                }
                $publishedCount = @(Get-Content -LiteralPath $listing |
                    Where-Object { $_ -match '^Name:\s*\S' }).Count
                if ($publishedCount -eq 0) {
                    throw 'Read no patch names out of the published bundle, so its count could not be compared.'
                }
                if ($publishedCount -ne $patchCount) {
                    throw ("The published bundle carries $publishedCount patches but the index " +
                        "describes $patchCount. Publish the built bundle before the index advertises " +
                        'what the release does not serve.')
                }
                Write-Host "[release] the published bundle carries $publishedCount patches, as described"
            } finally {
                Remove-Item -LiteralPath $listing -Force -ErrorAction SilentlyContinue
            }
        }

    } finally {
        Remove-Item -LiteralPath $temporaryArtifact -Force -ErrorAction SilentlyContinue
    }
}

# Manager refuses a bundle whose Patcher-Version is newer than its own patcher, and the README
# names the Manager release that first shipped the pinned one. The manifest is written by the
# Gradle plugin from the resolved patcher, so a catalog pin that drifts from it means the README
# names the wrong floor and the bundle is refused by a Manager the README says is new enough.
$catalogPath = Join-Path $rootPath 'gradle/libs.versions.toml'
if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) {
    throw "The version catalog is missing: $catalogPath"
}
$catalogText = Get-Content -LiteralPath $catalogPath -Raw
$catalogMatch = [regex]::Match($catalogText, '(?m)^\s*morphe-patcher\s*=\s*"([^"]+)"')
if (-not $catalogMatch.Success) {
    throw 'gradle/libs.versions.toml does not pin morphe-patcher.'
}
$pinnedPatcher = $catalogMatch.Groups[1].Value

$bundlePath = if ($ArtifactPath) { $ArtifactPath } else {
    Join-Path $rootPath "patches/build/libs/patches-$releaseVersion.mpp"
}
if (-not (Test-Path -LiteralPath $bundlePath -PathType Leaf)) {
    throw ("The built bundle is missing, so its patcher version cannot be checked against the " +
        "$pinnedPatcher the catalog pins: $bundlePath. Run :patches:buildAndroid first.")
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$manifestText = $null
$bundleZip = [System.IO.Compression.ZipFile]::OpenRead($bundlePath)
try {
    $manifestEntry = $bundleZip.Entries | Where-Object { $_.FullName -eq 'META-INF/MANIFEST.MF' }
    if (-not $manifestEntry) { throw "The bundle has no META-INF/MANIFEST.MF: $bundlePath" }
    $manifestReader = New-Object System.IO.StreamReader($manifestEntry.Open())
    try { $manifestText = $manifestReader.ReadToEnd() } finally { $manifestReader.Dispose() }
} finally {
    $bundleZip.Dispose()
}
# Manifest lines wrap at 72 characters with a leading space on the continuation.
$manifestText = $manifestText -replace "\r?\n ", ''
$stampMatch = [regex]::Match($manifestText, '(?m)^Patcher-Version:\s*(\S+)\s*$')
if (-not $stampMatch.Success) {
    throw "The bundle manifest has no Patcher-Version: $bundlePath"
}
if ($stampMatch.Groups[1].Value -ne $pinnedPatcher) {
    throw ("The bundle stamps Patcher-Version " + $stampMatch.Groups[1].Value + " but the catalog " +
        "pins morphe-patcher " + $pinnedPatcher + ". The README's Manager floor is written from " +
        'the pin, so one of the two is now wrong.')
}
Write-Host "[release] the bundle stamps patcher $pinnedPatcher, as the catalog pins"

Write-Host ("[facts] " + $sourceVersion + ": " + $patchCount + " patches for " + $targetPackage + " " + $targetVersion + "; " + $testCount + " runtime tests")

# Callers check the exit code, and a script invoked with & leaves the previous native
# command's code in $LASTEXITCODE, so a clean run has to say so itself.
exit 0
