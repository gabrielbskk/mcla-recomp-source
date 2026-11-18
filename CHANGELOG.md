# Changelog

Build notes for the Android ARM64 recompilation port.

Versioning follows the APK `versionName` (`0.1.<build>`); the build number is
the APK `versionCode`.

> **Active development has resumed**, focusing on Snapdragon / Adreno performance,
> thermal stability, and Turnip / Vulkan rendering refinements.

## 0.1.63 — full-screen restoration, Turnip syncdraw stability, and S23 performance fixes

Follow-up release addressing Galaxy S23 test feedback and diagnostic telemetry
from experimental build `0.1.62`.

- **Full-screen presentation restored.** Removed `SurfaceHolder` hardware scaler
  overrides in `MclaActivity` so SDL3 / Vulkan presents across the full native
  display panel while keeping internal Xenos 3D rendering at 720p
  (`mcla_render_height = 720`).
- **Turnip `syncdraw` enabled by default again.** Restores `TU_DEBUG=syncdraw`
  whenever a custom Turnip driver is selected, eliminating flickering wheels and
  headlights on Adreno Turnip drivers while keeping `"No switch"` available in the
  launcher for opt-in testing.
- **Fixed `0.1.62` GPU & memory regressions.** Restored single-pass compute
  decode for unscaled 2D EDRAM-resolved textures (`texture_load_direct_unscaled = true`)
  and restored 256 KiB shared-memory write-watch pages
  (`shared_memory_invalidation_pages_log2 = 6`) to avoid excess staging copies and
  `SIGSEGV` / `mprotect` overhead.
- **Mobile-friendly graphics defaults without overriding `mcla.toml`.** Defaults
  anisotropic filtering to `off` (`anisotropic_override = 0`, replacing the
  desktop 16× default) and MSAA to `off` on Android via `SetDefaultValue`, while
  clearing launcher `REX_MCLA_*` overrides so in-game Pause Menu (`mcla.toml`)
  choices persist cleanly across launches.
- **Thermal stability on Galaxy S23.** Enabled Android Sustained Performance Mode
  by default (toggleable in Launcher → `03 Extras`) and removed aggressive ADPF
  CPU boosting (`AdpfHintState`) that triggered thermal throttling during
  extended drives.
- **Retained from `0.1.62`:** persistent on-disk `VkPipelineCache`
  (`vulkan_pipeline_cache_file`), native Android `TextView` FPS / frametime HUD
  with zero Vulkan render-pass cost, 2-channel stereo audio output default, and
  zero-allocation frame-timing statistics.

## 0.1.62 — experimental Snapdragon optimization test build *(Experimental)*

Experimental test build used to profile Snapdragon 8 Gen 2 / Adreno 740 bottlenecks
on Galaxy S23. **Superseded by `0.1.63`.**

- **Added (kept in `0.1.63`):** persistent disk `VkPipelineCache`, native Android
  `TextView` FPS / frametime / `1% low` overlay (replacing the ImGui Vulkan pass
  on Android), 2-channel stereo output default (`audio_android_force_stereo`),
  and lock-free / zero-allocation frame-timing stats.
- **Experimental changes (reverted or refined in `0.1.63`):** tested `SurfaceHolder`
  fixed-size DPU scaling, Turnip with `TU_DEBUG` cleared by default, ADPF CPU hint
  sessions, launcher `REX_MCLA_*` environment overrides, and `texture_load_direct_unscaled = false`
  + 16 KiB write-watch pages. Device logs (`mcla-current.txt`) showed reduced
  aspect coverage, wheel/headlight flicker on Turnip without `syncdraw`, zero
  direct texture loads, and thermal throttling after sustained boost — all
  resolved in `0.1.63`.

## 0.1.61 — automatic log export and launcher cleanup

- **Automatic diagnostic log export** to `Documents/MCLA/logs/MCLA-diagnostic.txt`
  with session UUIDs, ISO timestamps, incremental rotation cursors, and an 8 MiB
  bounded history cap.
- **Launcher cleanup:** manual log export button removed; controls and shader
  options reorganized.

## 0.1.45 — stable-snap

- Snapdragon / Adreno build published as a long-lived snapshot.
- Mali / Immortalis and PanVK support discontinued; diagnostic APK withdrawn.
- RedMagic / 8 Elite diagnostic withheld pending the audio-cut investigation.
