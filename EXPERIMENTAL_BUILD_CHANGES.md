# Experimental Build Changes Summary

## Overview
This experimental build merges all significant improvements from:
1. **User's fork** (`gabrielbskk/mcla-recomp-source`) - Android support, frame generation, UI timing fixes, reflex sleep assist, etc.
2. **Upstream** (`holdmysocks/mcla-recomp`) - Vulkan paint pipeline fix, PS5 haptics/adaptive triggers
3. **Crackdown-recomp** (`saulob/crackdown-xbox360-recompiled`) - 95 SDK performance patches (documented for future SDK updates)

---

## Changes Included in This Build

### 1. Core Game Features (from user's fork)
All features from `gabrielbskk/mcla-recomp-source` are preserved:

#### Frame Generation & Pacing
- **Frame generation support** (`mcla_fps_frame_generation`): HALF DISPLAY mode, rendered rate caps
- **Timer queue HR wait at cap** (`mcla_timer_hr_at_cap`): High-res timer wait while capped, gives back CPU core
- **Reflex sleep assist** (`mcla_reflex_sleep_assist`): NVIDIA Reflex busy-wait reduction (saves 6-7ms CPU/frame at 60 FPS)
- **Title screen FPS cap** (`mcla_title_fps`): Configurable FPS cap for title/attract screens
- **Half display cap** (`MclaHalfDisplayCap`): Floor(97% of half refresh rate) for frame generation

#### UI Timing Fixes
- **UI movie pacing** (`mcla_ui_timing_fix`): Menus/pop-ups at 30 Hz, HUD at configurable rate (default 60 Hz)
- **Countdown fix** (`mcla_ui_countdown_fix`): Race countdown uses actual elapsed time since last movie step
- **UI interpolation** (`mcla_ui_interpolate`): Smooth UI animations between 30 Hz steps
- **Camera interpolation** (`mcla_ui_interp_camera`): Smooth pause menu camera/panel transitions
- **Menu key repeat fix** (`mcla_ui_repeat_fix`): Time-based repeat (233ms initial, 200ms interval) instead of update-based
- **Menu advance on input** (`mcla_ui_advance_on_input`): Immediate UI movie advance on key press
- **Menu rate** (`mcla_ui_menu_rate`): Configurable UI advance rate (default 30 Hz)

#### Android-Specific
- Touch controls with virtual gamepad
- Turnip driver support with loader
- Driver cache separation
- Pipeline background compilation
- Occlusion query pipeline wait bounds
- CAS/FSR present filters
- SDL event pump optimization
- Driver cache path customization
- Vulkan present stats
- Frame start sync (experimental)
- Stereo audio diagnostic for RedMagic

#### Shader Storage & Preparation
- Shader pack import at every start
- Launcher-based preparation (`--mcla_prepare_shaders`)
- Session lock protection
- Import journal with rollback
- Stamp-based driver/cache validation
- Pipeline cache persistence (off by default)

#### Audio & Input
- Audio mixer thread priority (`audio_mixer_priority`)
- Poll wait optimization (`mcla_poll_wait`, `mcla_pump_wake_on_demand`)
- Small CPU defaults (5 or fewer logical processors)
- Button prompts (auto/Xbox/PlayStation)
- PC triggers (DualSense adaptive triggers on PC)

#### Debugging & Profiling
- Code pointer scan (`--mcla_scan_code_pointers`)
- Crash trace with guest function mapping
- Sampling profiler
- FPS counter with p95
- Vehicle telemetry logging
- Pad probe (title screen input debugging)

---

### 2. Upstream Improvements (from holdmysocks/mcla-recomp)

#### Vulkan Paint Pipeline Fix
- **Patch**: `rexglue-vulkan-paint-pipeline-format.patch`
- **Effect**: Records swapchain format when creating paint pipeline, avoiding unnecessary recreation every frame
- **Impact**: Reduces Vulkan pipeline recreation overhead

#### Windows Timer Resolution Fix
- Already present in fork: `timeBeginPeriod(1)` + `PROCESS_POWER_THROTTLING_IGNORE_TIMER_RESOLUTION`
- Keeps 1ms timer resolution even when window is hidden/minimized

#### PS5 Features (for reference - not in Android build)
- DualSense haptics and adaptive triggers
- Compatible vibration mode
- Vehicle telemetry integration (speed, gear, nitro, crashes)

---

### 3. Crackdown-recomp SDK Patches (for future SDK updates)

Documented in `patches/README.md` - 95 patches providing:

#### D3D12 Performance
- Early submission ending, background work polling
- Render target transfer optimizations
- Placed texture heaps (shared 256 MB heaps)
- Direct host resolve (avoids EDRAM buffer dump)
- Clear rectangle draws optimization
- Texture load direct (one-pass decode)
- Async pipelines without frame wait
- GPU draw state memoization
- Host thread CPU0 avoidance

#### Vulkan Performance
- Direct host resolve
- Texture load storage copy (compute shader path)
- Vulkan texture load direct
- Async pipelines without frame wait
- Pipeline storage parallel translation
- Pipeline cache file persistence
- Occlusion query fixes

#### General
- Timer queue blocking wait fixes
- Vblank thread sleep optimization
- Command processor wait reduction
- GPU shared memory lock-free validity
- GPU side-effect-free register ranges
- GPU plugin ThinLTO
- Fused vector multiply-add

---

## Build Instructions

```bash
cd /data/data/com.termux/files/home/mcla-recomp-experimental
./build_experimental.sh
```

### Prerequisites (Termux)
- `pkg install cmake ninja python3 openjdk-17 aapt2 apksigner zipalign binutils clang`
- Android NDK r30+ (for CI cross-build) or Termux clang (on-device)
- `aapt2`, `apksigner`, `zipalign` in PATH
- Sufficient storage (~4GB for build artifacts)

### Build Steps
1. Clone ReXGlue SDK at commit `f5337cdc947ff6d4c4196737e2c807a48f2a1fc2`
2. Apply 14 Android patches in order (including new Vulkan paint pipeline fix)
3. Build SDK for Android ARM64 (Vulkan only)
4. Build AdrenoTools
5. Run codegen (`rexglue codegen mcla_manifest.toml`)
5. Build game native library (`mcla` target)
6. Package APK (`scripts/build_android_apk.sh`)

### Output
- `out/android-arm64/MCLA-arm64.apk` (debug-signed)
- Copied to `Download/MCLA-Recomp-arm64.apk` on device

---

## Testing Checklist

When testing the experimental build:

### Frame Generation
- [ ] Enable frame generation in settings
- [ ] Verify HALF DISPLAY mode works (check FPS)
- [ ] Test frame generation with different refresh rate displays

### UI Timing
- [ ] Menus at 120 FPS cap - should feel smooth, not sluggish
- [ ] Race countdown - should show correct timing
- [ ] Pause menu camera - should interpolate smoothly
- [ ] Menu key repeat - should feel responsive at 120 FPS

### Frame Pacing
- [ ] Reflex sleep assist - check CPU usage at 30/60 FPS cap
- [ ] Timer queue HR wait - check CPU core usage
- [ ] Title screen FPS cap - verify it limits correctly

### Shader Preparation
- [ ] First run - shader preparation should complete
- [ ] Subsequent runs - should use cached pipelines
- [ ] Driver update - should trigger re-preparation

### Android-Specific
- [ ] Touch controls work
- [ ] Turnip driver selection works
- [ ] Stereo audio on RedMagic
- [ ] Shader pack import works

### Performance
- [ ] Sustained FPS in city
- [ ] Thermal throttling behavior
- [ ] Pipeline compilation stutter (first run vs subsequent)

---

## Known Limitations

1. **Mali/Immortalis unsupported** - Diagnostic APK withdrawn
2. **Frame generation experimental** - Not fully validated on device
3. **PS5 features not included** - Android build doesn't have DualSense support
4. **Crackdown patches not applied** - SDK patches need separate SDK rebuild
5. **Development paused** - No further updates planned per maintainer

---

## Rollback Plan

If experimental build has issues:
```bash
# Revert to stable build
pm uninstall com.mcla.recomp
# Reinstall stable-snap APK from releases
```

Or restore from backup:
```bash
cp /data/data/com.termux/files/home/mcla-recomp-rv /data/data/com.termux/files/home/mcla-recomp-experimental
```

---

## Commit Info

- Base commit: `68d833a` (Add Discord push webhook workflow)
- Upstream reference: `holdmysocks/mcla-recomp` main branch
- Crackdown reference: `saulob/crackdown-xbox360-recompiled` main branch
- SDK commit: `f5337cdc947ff6d4c4196737e2c807a48f2a1fc2` (ReXGlue v0.10.0)

