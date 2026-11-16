# S23 visual-tradeoff candidate v0.1.55

Requested as an alternative to the common v0.1.54 APK. This is NOT an unlock of
CPU/GPU clocks or thermal limits and does not promise increased performance.
It uses the same native libraries and compatible signing certificate. It is
an update of the same package, not a side-by-side installation.

Build with `MCLA_S23_PERFORMANCE_DEFAULT=1` to add manifest metadata defaulting
the common launcher's S23 performance switch to ON, unless explicitly selected
otherwise in its preference. Ordinary builds default OFF. The switch is shared
code, not a new permanent device-specific renderer branch.

The launch override policy requests 1280x720 / 16:9 at native scale, custom
detail, no shadows/foliage impostors/AA/blur/DOF, city and traffic LOD 0.35,
ambient traffic distance 150 m, no pedestrians/parked cars, environment map and
reflection rendering every fourth frame. The last two can look stepped, and
reduced detail/distances increase pop-in. Many effects were already disabled
in the S23's existing configuration, so incremental gains may be small.
Lower rendering heights are deliberately not used: they remain unvalidated.

No driver, syncdraw, shaderpack, cache, input, audio, clock or thermal changes.
The Java policy does not edit TOML or saves. Disable the launcher switch and
restart gameplay to remove its overrides and restore the resolution selector.
Restart is required; do not judge this switch by changing it mid-game. Avoid
editing overridden graphics settings in-game during comparisons.

Validation: all Java compiled; 21 unique performance overrides checked against
SDK/game source definitions and native binary presence; profile safety/range
checks, 37 audio, 128 keyboard/mouse, nine resolution policy checks passed.
Native libraries are byte-identical to v0.1.54; DEX controls, manifest ON default,
version, signing certificate and build alignment checks passed. No new device
gameplay/performance measurement is claimed.

Delivery: `Download/MCLA/MCLA-Recomp-S23-performance-v0.1.55.apk`.
SHA-256: `170d644eaf2fa2f6d272e87286d9922b3cdc60f6631144e3607ab3f589f86039`.
Installed as an update after checking that the game was not running. Previous
versioned APKs retained. Compare the same route using the switch OFF vs ON in
v0.1.55, with identical driver, syncdraw, audio, starting temperature and cache.
The former v0.1.54 APK remains available but is a lower versionCode, so normal
Android installation may reject a direct downgrade; use the switch to compare
without uninstalling or clearing data.
