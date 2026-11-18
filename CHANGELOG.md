# Changelog

Build notes for the Android ARM64 recompilation port.

Versioning follows the APK `versionName` (`0.1.<build>`); the build number is
the APK `versionCode`.

> **Active development has resumed**, focusing on Snapdragon / Adreno performance,
> audio stability, touch controls, and Turnip / Vulkan rendering refinements.

## 0.1.64 — Launcher & Xbox 360 % Pedals Redesign, Snapdragon FPS Profiles, RedMagic/S23 Audio Fix, and Log Recorder Repair *(Experimental)*

> **Experimental build (`v0.1.64` / `versionCode 64`).** Active community test
> release addressing Galaxy S23 gameplay drops, RedMagic 11 Air audio cuts,
> Qualcomm system Vulkan overhead, log export failures, and a full Launcher +
> Touch HUD overhaul.

- **Fixed diagnostic log recording & export (`MclaLogExporter.java`).**
  - Restored periodic 15-second background export during gameplay plus lifecycle
    flushes on pause, resume, exit, and launcher startup.
  - Added self-healing `MediaStore` URI resolution when `MCLA-diagnostic.txt` or
    `mcla-current.txt` is deleted/moved by the user, an external app-specific
    mirror (`Android/data/org.mcla.recomp/files/logs/`), and a manual **Export
    Diagnostic Log Now** trigger in `MclaLauncherActivity`.
- **Snapdragon Performance Presets (`MclaRuntimeProfiles.java`) & `mcla_empty_streets` (`src/perf_options.cpp`).**
  - Added `s23_balanced` (default), `max_fps`, and `console` profiles so Turnip
    `TU_DEBUG=syncdraw` runs shed expensive Xbox 360 shadow cascades, motion blur,
    depth of field, fullscreen blur, and excess distant geometry by default.
  - Added `mcla_empty_streets` CVar to zero ambient zone `unspawn`, `pedestrians`,
    and `parked` density while preserving race opponent AI.
- **Qualcomm System Vulkan `VKDBGUTILWARN003` storm fix (`rexglue-android-vulkan-debug-audio.patch`).**
  - Guarded `VK_EXT_debug_utils` and defaulted `vulkan_log_debug_messages = false`
    on Android unless validation or debug logging is explicitly requested,
    eliminating 1,000+/sec `VKDBGUTILWARN003` callbacks observed in Zenfone 9 and
    Snapdragon system-driver logs.
- **Audio Anti-Cut Engine for Galaxy S23 & RedMagic 11 Air (`audio_thread.cpp`, `sdl_audio_driver.cpp`).**
  - Implemented Android/Linux `setpriority` elevation + `CLOCK_THREAD_CPUTIME_ID`
    instrumentation in `rex::audio::RaiseCallingAudioThread`.
  - Released `SDLAudioDriver::frames_mutex_` before 6ch → 2ch float conversion
    and `SDL_PutAudioStreamData`, and forwarded `SDL_AUDIO_DEVICE_SAMPLE_FRAMES`
    to `SDL_HINT_AUDIO_DEVICE_SAMPLE_FRAMES`.
  - Added `s23_adaptive` (16–28 frames), `redmagic_safe` (20–32 frames, 1024
    sample quantum, auto-selected on RedMagic/nubia), and `low_latency` profiles.
- **Redesigned Launcher, App Icon & Authentic Xbox 360 Touch Controls with `%` Pedals.**
  - Packaged adaptive & mipmap launcher icons (`@mipmap/ic_launcher`) from the
    project cover artwork and updated `scripts/build_android_apk.sh` resource
    compilation (`aapt2 compile` + `--auto-add-overlay`).
  - Rebuilt `MclaLauncherActivity`, `MclaTouchControls`, and
    `MclaTouchEditorActivity` with authentic Xbox 360 visuals and vertical analog
    `GAS` (`RT`) / `BRK` (`LT`) pedals showing live `0%–100%` pressure.

## 0.1.63 — full-screen restoration, Turnip syncdraw stability, and S23 performance fixes *(Experimental)*

Experimental follow-up build addressing Galaxy S23 test feedback and diagnostic
telemetry from experimental build `0.1.62`. **Superseded by `0.1.64`.**

- **Full-screen presentation restored** (`mcla_render_height = 720`, native panel scaling).
- **Turnip `syncdraw` enabled by default again** to fix wheel & headlight flicker.
- **Fixed `0.1.62` GPU & memory regressions** (`texture_load_direct_unscaled = true`,
  256 KiB write-watch pages).
- **Sustained Performance Mode** enabled by default on Android; ADPF CPU boost removed.

## 0.1.62 — experimental Snapdragon optimization test build *(Experimental)*

Experimental test build used to profile Snapdragon 8 Gen 2 / Adreno 740 bottlenecks
on Galaxy S23. **Superseded by `0.1.63` / `0.1.64`.**

- Added persistent disk `VkPipelineCache`, native Android `TextView` FPS HUD,
  2-channel stereo output default, and zero-allocation frame-timing stats.

## 0.1.61 — automatic log export and launcher cleanup

- Automatic diagnostic log export to `Documents/MCLA/logs/MCLA-diagnostic.txt`
  with session UUIDs, ISO timestamps, and an 8 MiB history cap.

## 0.1.45 — stable-snap

- Snapdragon / Adreno build published as a long-lived snapshot.
- Mali / Immortalis and PanVK support discontinued; diagnostic APK withdrawn.
