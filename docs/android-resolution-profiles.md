# Android resolution experiment

The Graphics card has three explicit, restart-required options:

- **Existing game settings** (default): no profile overrides. Existing TOML
  settings are neither rewritten nor deleted.
- **720p / screen aspect**: 720 guest lines with automatic display aspect,
  resolution scale 1 and both draw-axis scales 1. Width depends on the device.
- **720p / 16:9**: 1280x720 guest video mode, scale 1 on both axes. A wider
  screen is pillarboxed; the presenter still uses the display-sized swapchain.

For the SD695 log's 1600x720 mode, 1280x720 removes 20% of the scene pixels.
It does not promise a proportional speedup, reduce all render targets by 20%,
or cap total RAM consumption. Driver, shader pack, audio, detail settings,
pipeline scheduling and frame target are not part of this profile.

Profiles apply five REX environment overrides before native initialization.
Switching back clears those overrides even if the Activity reuses a process.
Unknown profile values fall back to existing settings. On application failure,
all five overrides are cleared rather than leaving a partly applied mode.

The current SDK only supports integer render scales >= 1. A scale of 0.75 or
0.5 is not a supported sub-720p path. Lowering the guest video height itself
previously caused loading faults; 480p/540p/600p are not advertised as working
choices. Proper lower-resolution support requires a separate investigation.

The diagnostic APK also records process RSS/swap (when available), total and
available system RAM, and the Android low-memory signal in its five-second
heartbeat. These are not GPU-memory or per-allocation measurements. No root
or repeated PSS collection is needed. The counters and profile still need
on-device validation, particularly on SD695/4 GB; Java tests and compilation
cannot establish gameplay improvement.

## Local candidate

`0.1.52-resolution-test` (versionCode 52), diagnostic candidate, was packaged
and installed as a compatible update on the maintainer's device. It is not a
public stable release. APK SHA-256:
`1568a92aaa83451145930dfd54627efcfef570c8952b091aabd386ade90f2d8f`.
Native/Java builds and nine resolution-policy checks passed; the packaged
native library matches the final build, and signature/alignment checks passed.
No resolution profile was silently selected during installation. `syncdraw`,
disabled shader-pack state, saves and existing game settings were preserved.
The SD695 still needs a test of the selected mode and memory telemetry.
