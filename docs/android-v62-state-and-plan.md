# v62 state and planned change

Snapshot taken 2026-11-16 (local time, UTC-3). This document records what is
installed, what exists as a local candidate, what has actually been verified,
and what would change if the candidate is accepted. It changes no build input.

## Project in one paragraph

MCLA Recomp RV statically recompiles *Midnight Club: Los Angeles* (Xbox 360,
PowerPC) to native C++ and runs it on Android ARM64 through the ReXGlue runtime,
SDL3 and Vulkan. It is not an x86 emulator and not a Linux compatibility layer.
The GPU path translates the game's DXBC shaders to SPIR-V and submits them to
Vulkan; there is no runtime CPU emulation. Builds are produced locally in Termux
with Clang 21, CMake and Ninja; packaging uses `aapt2`, `javac`, `d8`, `zipalign`
and `apksigner`.

## Installed baseline: v61

| Field | Value |
| --- | --- |
| Version | `0.1.61-auto-log-launcher` (code 61) |
| Device | Galaxy S23, Adreno 740, 8 GB RAM |
| Signer | `CN=MCLA Recomp Debug`, SHA-256 `bcb81f4c885db48c42bfaddba68b3c5b2b86cbe305f44c0ff63c79078f341e9c` |
| Local copy | `Download/MCLA-NORMAL-v0.1.61.apk` |
| Netbook copy | `/home/void/MCLA-RECOMP-RV-/MCLA-APKs/MCLA-NORMAL-v0.1.61.apk` |

v61 contains: the isolated SDL Android sentinel self-wake fix (v59), the
frame-interval diagnostic added in v60, and the unified automatic log export
written to `Documents/MCLA/logs/MCLA-diagnostic.txt` (8 MiB cap, ~4 MiB kept on
trim, per-inode cursors, session/timezone markers). It has **no LSFG code**.

## Local candidate: v62

| Field | Value |
| --- | --- |
| Version | `0.1.62-lsfg-2x-experimental` (code 62) |
| Size | 113,321,338 bytes |
| SHA-256 | `fdf0b72b5aa6fdeba76a9b24e40320de16e1086564e7270e818a613d4a8f4cff` |
| Signature | same compatible debug certificate as v61 |
| Alignment | zipalign `-P 16 -c 4` passes; all packaged ELF LOAD segments are >= 16 KiB |
| Location | `out/android-arm64/MCLA-arm64.apk` and the private research temp dir only |
| Installed | **no** |
| Sent to netbook | **no** |
| Copied to `Download/` | **no** |

Packaged libraries: `libmain.so`, `librexruntime.so`, `librexgpu-xenos.so`,
`libSDL3.so`, `libc++_shared.so`, `libadrenotools.so` with its four hook
libraries, and the new optional `libmcla_lsfg.so` (1,010,480 bytes, SHA-256
`d906e57b7ac3b437b24d47d2d7aedde79d83668541dd25e23fe9c02dba81048b`), plus
`assets/lsfg-third-party-notices.txt`.

`libmcla_lsfg.so` contains no game or vendor shaders. The user's private
`Lossless.dll` is not packaged; it is read at runtime from the app's private
files directory, and is still absent there (`files/lsfg/` does not exist).

## What v62 changes

1. **New optional native backend.** `libmcla_lsfg.so` wraps lsfg-vk v1.0.0
   (MIT, commit `7113d7d02da9fc9df5cb3b03230d1f7de86f7056`), with the DXBC
   dependency pinned to `78ab59a8aaeb43cd1b0a5e91ba86722433a10b78`. Two local
   patches are applied from `patches/`: the earlier local-image fix and the new
   renderer adapter.
2. **Same-device, record-only adapter.** The backend borrows the renderer's
   `VkDevice`, physical device, queue family and queue. It records its compute
   chain into the caller's `VkCommandBuffer` through `mcla_lsfg_record`; it never
   creates queues, never submits, never ends the buffer and never waits on the
   CPU. The SDK keeps ownership of submission ordering, barriers and retirement.
3. **Versioned C ABI.** `include/rex/graphics/mcla_lsfg_api.h` exposes
   `mcla_lsfg_create`, `mcla_lsfg_record` and `mcla_lsfg_destroy`. The destroy
   call refuses to free resources while a submission is still in flight.
4. **Opt-in device features.** When `REX_MCLA_LSFG=1`, device creation additionally
   enables `synchronization2`, `vulkanMemoryModel`,
   `shaderStorageImageExtendedFormats`, and requires `nullDescriptor`. If any is
   missing, frame generation reports unavailable and the game renders normally.
5. **Routing in the existing frame-generation path.** With
   `frame_generation = lsfg`, `FgGenerate` delegates to `FgGenerateLsfg`, which
   writes a 2x frame into the same generated image the FidelityFX pacer already
   consumes. Only one pacer runs; LSFG does not add its own presentation loop.
6. **Warmup and fallback.** The first three frames have cold history and are
   discarded; those frames are presented as real frames only. Any creation or
   recording failure falls back to real frames for the session instead of
   corrupting output.
7. **Scope limits.** SDR only (not selected when HDR is active), 2x only, integer
   generation size, and no effect on guest logic, input or save state.
8. **Launcher control.** `MclaFrameGeneration` stores a per-launch preference
   (default **off**) and sets `REX_MCLA_LSFG` / `REX_MCLA_LSFG_DLL` before the
   game starts. The switch lives in the "World and performance" card and states
   that the DLL is user-supplied and that three initial frames are not generated.
9. **Build inputs.** `scripts/build_lsfg_android.sh` builds the backend from a
   pinned checkout with a pinned submodule check, and
   `scripts/build_android_apk.sh` includes it only when `MCLA_INCLUDE_LSFG=1`.

## What v62 does not change

- No change to saves, game data, imported files, touch layout, gamepad/keyboard
  mapping, presets, audio settings or the shader pack (still disabled).
- No change to the v59 SDL wake fix, the v60 frame-interval instrumentation or
  the v61 log exporter.
- No new permissions, no background service, no network use. The normal and the
  diagnostic manifests differ only by a `GPU_VARIANT` meta-data tag; the
  permission sets are identical.
- With the switch off, no LSFG module is loaded and the device is created as
  before.
- One nuance to settle before merging: on Android, `mcla_app.cpp` writes the
  `frame_generation` cvar from `REX_MCLA_LSFG` at startup, so it becomes `off`
  unless the switch enables LSFG. The build used for v62 is compiled without
  `REX_HAS_FSR`, so nothing working is disabled here; on an FSR-enabled build
  this would override a manually selected `fsr`.

## Evidence collected

Verified in this workspace:

- Targeted compiles of the four changed SDK translation units and of the app
  startup integration: pass.
- Relink of `librexruntime.so`, `librexgpu-xenos.so`, `libmain.so`: pass.
- `scripts/ci/test_lsfg_sdk_wiring.py`: pass (source contracts only).
- `scripts/ci/test_android_normal_candidate.py`: pass (7344 touch-layout checks,
  log deltas/idle/truncation/UTF-8, normal profile and import contracts).
- Backend build and package: pass, producing the artifact and hashes above.
- Isolated GPU readback at 720p on Turnip, with and without `TU_DEBUG=syncdraw`,
  for RGBA8 and RGBA16F: static frames match (max RGB MAE 0.0009346), the moving
  midpoint is within ~1 px of the expected value, and negative controls
  (copy, black, crossfade) are rejected. The first three frames are excluded as
  cold history.
- Borrowed-device/borrowed-buffer guards: ending or submitting the caller's
  buffer throws, and destroying with an in-flight submission returns "busy".

Not verified, and therefore not claimed:

- Any behaviour inside the running game. The candidate has never been installed.
- The external-memory path: all pixel tests used backend-local images, not images
  owned by the game's swapchain.
- Frame time, pure GPU time, input latency, memory use, thermals, battery, or
  image quality in real scenes.
- Interaction with HDR, FSR, FXAA, resolution scaling or the pause menu.
- Upgrade-in-place over v61 on the device (same key is expected to allow it).

## Planned verification before any acceptance

1. Install v62 locally and confirm the package reports code 62 and keeps data.
2. With the switch off, run the same route and camera as the v61 baseline and
   confirm no regression in the existing frame-interval log.
3. Copy `Lossless.dll` into the app's private `files/lsfg/` and enable the switch.
4. Capture a fixed route: stationary, low speed, high speed. Compare off vs 2x
   for frame interval, GPU time, memory, temperature and input latency.
5. Exercise pause, resume, resize/orientation change and backgrounding, then
   confirm no reuse of in-flight resources and no leak across resets.
6. Only if steps 1-5 pass, publish the artifact to the netbook with its SHA-256
   and update the release notes. Otherwise keep v61 as the installed baseline.

## Files added or touched for this candidate (documentation view)

- `src/lsfg/mcla_lsfg_backend.cpp`, `src/lsfg/mcla_lsfg.exports`
- `third_party/rexglue-sdk/include/rex/graphics/mcla_lsfg_api.h`
- `third_party/rexglue-sdk/src/graphics/vulkan/frame_generation.cpp`
- `third_party/rexglue-sdk/src/graphics/vulkan/command_processor.cpp`
- `third_party/rexglue-sdk/include/rex/graphics/vulkan/command_processor.h`
- `third_party/rexglue-sdk/src/ui/presenter.cpp`
- `third_party/rexglue-sdk/src/ui/vulkan/vulkan_device.cpp`
- `android/MclaFrameGeneration.java`, `android/MclaActivity.java`,
  `android/MclaLauncherActivity.java`
- `src/mcla_app.cpp`
- `scripts/build_lsfg_android.sh`, `scripts/build_android_apk.sh`
- `patches/lsfg-vk-v1-android-local-image.patch`,
  `patches/lsfg-vk-v1-renderer-adapter.patch`
- `scripts/ci/test_lsfg_readback.py`, `scripts/ci/test_lsfg_sdk_wiring.py`
- `docs/android-lsfg-feasibility.md`, this document

The working tree has many unrelated uncommitted changes; this list is scoped to
the LSFG candidate and does not describe the whole tree.
