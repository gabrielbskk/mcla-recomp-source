# Android SDK patches

The active recipe targets ReXGlue v0.10.0 at commit
`f5337cdc947ff6d4c4196737e2c807a48f2a1fc2`. Apply these patches in order to a
clean checkout:

1. `rexglue-android-arm64.patch` — the reviewed SDK/fork Android ARM64 delta.
2. `rexglue-turnip-loader.patch` — optional user-selected Turnip loading.
3. `rexglue-driver-cache-path.patch` — Vulkan cache separation by driver.
4. `rexglue-vulkan-pipeline-background-work.patch` — on Android, run pipeline
   compilation and pipeline-cache writes below the game and render threads, and
   keep the driver pipeline-cache save out of a burst of new pipelines (for
   example while a car loads).
5. `rexglue-vulkan-occlusion-query-pipeline-wait.patch` — time the pipeline
   creation and the pipeline wait of the draws inside an occlusion query, and
   let both be bounded by a cvar (`vulkan_occlusion_query_pipeline_wait_ms`,
   `vulkan_occlusion_query_async_pipelines`) so their cost can be measured on
   the device before the behavior is changed.
6. `rexglue-android-present-filters.patch` — compile the presenter's spatial
   scaling filters (FidelityFX's Contrast Adaptive Sharpening and FSR 1, whose
   shaders are in the SDK tree and need neither its source nor its runtime)
   into the Android build, so `present_effect = cas` or `fsr` can be tried
   there. The temporal upscaler still needs the runtime and is not offered.
7. `rexglue-android-sdl-event-pump.patch` — avoid a second joystick/HIDAPI
   maintenance pump before the first Android event wait when the entry pump
   has already run. Later wakeups and lifecycle events retain the SDL path.
8. `rexglue-pipeline-storage-layout.patch` — expose the native Vulkan pipeline
   record size so importers can reject incompatible packed-bitfield layouts.
9. `rexglue-android-present-stats.patch` — retain the last 256 successful
   Android host-presentation timestamps for optional FPS and timing summaries.
10. `rexglue-vulkan-frame-start-sync.patch` — experimental, opt-in Vulkan
    submission-fence waits at the start of each guest frame, each new
    submission, or only after an eligible guest primary command buffer ends.
    All are disabled by default. On the tested A740, waiting at frame start
    did not help; waiting before every submission removed the artifact but
    reduced FPS substantially. The selective wait also did not remove the
    artifact and reduced FPS. Keep all three modes disabled by default.
11. `rexglue-android-bionic-link.patch` — link Android against `dl` without
    requesting separate `pthread` or `rt` libraries: Bionic provides those
    functions in libc, and the NDK does not ship those two libraries. No
    change to the observed gameplay behavior is intended.
12. `rexglue-android-driver-selection.patch` — Android driver selection logic.
13. `rexglue-android-stereo-diagnostic.patch` — stereo audio diagnostic for
    RedMagic devices.
14. `rexglue-vulkan-paint-pipeline-format.patch` — Records the swapchain format
    when creating the paint pipeline, so it is only recreated when the format
    actually changes instead of every frame. From upstream `holdmysocks/mcla-recomp`
    (commit e984176).

---

## Upstream SDK patches (for future consideration — from crackdown-recomp)

These patches from `saulob/crackdown-xbox360-recompiled` provide significant
performance improvements and are recommended for future SDK updates:

- `0034-d3d12-submissions.windows.patch` — Early submission ending, background
  work polling for D3D12 command processor
- `0035-d3d12-render-target-transfers.windows.patch` — Render target transfer
  optimizations
- `0041-resolve-copy-into-textures.patch` — Direct host resolve (avoids EDRAM
  buffer dump, ~10-15% faster resolves)
- `0047-internal-render-scale.patch` — Internal render scale support
- `0053-vulkan-occlusion-query-results.linux.patch` — Vulkan occlusion query
  fixes
- `0078-gpu-shared-memory-lock-free-validity.patch` — Lock-free GPU shared
  memory
- `0079-gpu-side-effect-free-register-ranges.patch` — GPU register range
  optimizations
- `0080-gpu-draw-state-memo.patch` — GPU draw state memoization
- `0081-d3d12-async-submission.windows.patch` — D3D12 async submission
- `0082-d3d12-draw-state-memo.windows.patch` — D3D12 draw state memoization
- `0083-d3d12-host-threads-avoid-cpu0.windows.patch` — Avoid CPU0 for host
  threads
- `0094-input-activity-callback.patch` — Input activity callback
- `0095-fused-vector-multiply-add.patch` — Fused vector multiply-add
- `0098-gpu-plugin-thinlto.windows.patch` — GPU plugin ThinLTO
- `0100-texture-cache-resolve-target-index.windows.patch` — Texture cache
  resolve target index
- `0101-d3d12-per-draw-loops.windows.patch` — D3D12 per-draw loop
  optimizations
- `0102-gpu-plugin-thinlto.windows.patch` — GPU plugin ThinLTO
- `0103-d3d12-present-latency-wait.windows.patch` — Present latency wait
- `rexglue-clear-rectangle-draws.patch` — Clear rectangle draws optimization
- `rexglue-texture-load-direct.patch` — Direct texture load (one-pass decode)
- `rexglue-direct-resolve.patch` — Direct host resolve
- `rexglue-vulkan-texture-load-storage-copy.patch` — Vulkan texture load
  storage copy
- `rexglue-async-pipelines-no-frame-wait.patch` — Async pipelines without
  frame wait
- `rexglue-vulkan-pipeline-storage-parallel-translation.patch` — Parallel
  pipeline translation
- `rexglue-vulkan-pipeline-cache-file.patch` — Vulkan pipeline cache file
- `rexglue-d3d12-storage-preload-threads.patch` — D3D12 storage preload
  threads
- `rexglue-vulkan-pipeline-storage-keep-unsupported.patch` — Keep unsupported
  pipelines

---

## Upstream PS5 patches (for reference)

From `holdmysocks/mcla-recomp` (commits 43b58f7, d45be0c):

- DualSense haptics and adaptive triggers support
- Compatible vibration mode for rumble
- Vehicle telemetry integration for haptics (speed, gear, nitro, crashes)
- Adaptive trigger resistance for throttle/brake

