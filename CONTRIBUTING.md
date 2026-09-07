# Contributing

Feature ideas, bug reports, and pull requests are welcome.

If you open an issue, include:

- the TikTok package name and version
- the Morphe Manager version and Hushfeed bundle version
- every selected patch, including the settings patch
- the affected surface, such as Feed, Inbox, Comments or Downloads
- the expected and actual behavior
- ordered reproduction steps, including each Hushfeed setting changed before the first failure
- logs or screenshots if the issue is visual or crash related

Small focused pull requests are easier to test than large mixed changes.

For patch changes, please include what APK version you tested against and what behavior you verified.

Diagnostic tools can copy or save a redacted report. Attach that report instead of raw logcat
when possible, and remove any private messages or account details from screenshots.

Before publishing a release, run `scripts/validate-release-facts.ps1` after the runtime tests
and patch list generation. It checks the generated version, target package, target version,
patch count and test count against the README and `patches-bundle.json`. After uploading the
bundle and `SHA256SUMS.txt`, run it again with `-VerifyPublishedAsset` to check the indexed URL,
the local artifact hash and the hosted checksum entry. The local release helpers are
`scripts/gen-l10n.py`, `scripts/verify-all-patches.ps1` and
`scripts/measure-patch-heap.ps1`; the latter two need a Morphe desktop jar and a fixture APK.
The release check also refuses results older than the sources, so rerun the tests after an edit
rather than reusing the last run's XML.

Run `scripts/install-hooks.ps1` once per checkout. It installs a pre-push hook that runs the
runtime tests when a push changes anything under `extensions/` or `patches/`, and the release
check when it changes `README.md`, `gradle.properties`, `patches-list.json` or
`patches-bundle.json`. Nothing builds on GitHub, so a push is the last place either can run.
Set `HUSHFEED_SKIP_PRE_PUSH=1` to push without it.

## Source notices

Preserve every existing copyright, license, author-credit, and source-origin
notice when modifying or moving a file. Do not remove or replace a notice unless
the attributed code is removed from the file.

New source written specifically for this project may use:

```text
/*
 * Copyright <year> Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
```

For source derived from another project, retain its existing notices and add a
clear `Forked from:` URL when the origin is not already stated. Do not use a
project-only copyright header in a way that implies exclusive ownership of
upstream code.
