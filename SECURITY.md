# Security policy

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting on either
[mcla-recomp-source](https://github.com/gabrielbskk/mcla-recomp-source/security/advisories/new)
or [mcla-recomp-rv](https://github.com/gabrielbskk/mcla-recomp-rv/security/advisories/new)
for security issues, such as unsafe file import, malicious driver handling,
CI/workflow weaknesses, or exposure of private files. Include affected versions,
reproduction steps and impact; do not post working exploits, credentials, game
files or private logs publicly. There is no guaranteed response or fix timeline.

Issues are disabled on `mcla-recomp-source`. For ordinary crashes, compatibility
and performance reports, open a
[public issue on `mcla-recomp-rv`](https://github.com/gabrielbskk/mcla-recomp-rv/issues)
after removing personal information from logs and screenshots.

## Scope and practical precautions

The current public APKs are experimental and debug-signed; older builds may not
receive security fixes. Download them from the project's official releases and
verify their published SHA-256 hashes. Debug signing establishes update
compatibility, not an independent security audit.

Imported Vulkan driver ZIPs contain native code that runs with the app's
permissions. Use only drivers from sources you trust. The import checks do not
guarantee a driver is safe. Keep your game files, saves, shader packs, signing
keys and credentials private; never attach them to issues or commits.
