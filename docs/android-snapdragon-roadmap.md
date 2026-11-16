# Unified Snapdragon APK: stability and low-memory roadmap

Status: proposed implementation and validation sequence, 2026-11-14.
No profile below is a validated performance fix. Current development excludes
Mali/Immortalis. Do not publish local experiments as stable updates.

## Priority and existing evidence

1. Snapdragon 695 / Adreno 619 / reported 4 GB RAM: eliminate severe stalls
   where possible, and determine their CPU, GPU, compilation or memory origin.
2. Preserve S23/Adreno 740 visuals with its existing explicit `syncdraw` choice;
   investigate the regression reported after the bounded-counter experiment.
3. Consolidate RedMagic audio experiments into optional controls in one APK.

The nine SD695 logs already target 30 FPS and render at 1600x720. Merely
selecting 30 FPS again is not a new optimization. One system-driver log records
361 ms and 153 ms pipeline creation inside occlusion queries; a different
Turnip session drops to 6.23 and 3.83 host presentations/s. These are distinct
observations, not proof of one cause. No existing SD695 memory/thermal series
establishes OOM, swapping, throttling or the actual gameplay-update rate.

## Phase 1: reproducible control and low-overhead diagnostics

- Preserve signing compatibility, saves, imported drivers, caches and existing
  control preferences. Archive every tested APK with version and hash.
- Compare the pre-counter-change presentation behavior with the experimental
  bounded-counter behavior using the same driver, debug switch and scene. Do
  not bundle a new Vulkan tuning change into that comparison. The current
  2 ms wait-budget experiment has not demonstrated a benefit; use the original
  4 ms as the control when preparing a matched comparison.
- Add opt-in, rootless Android memory telemetry: device total/available RAM,
  system low-memory state, process RSS and swap where available, thermal state
  and battery state. Missing counters must be reported as unavailable, not 0.
  Avoid frequent PSS collection or per-frame filesystem work.
- Use existing `gpu_memory_log_ms = 5000` for GPU budgets and cache allocation
  counts where supported. Do not substitute total phone RAM for GPU budget.
- Record game-update/swap progress, pipeline queue length, compilation/wait
  durations and startup-versus-gameplay phases. Keep logs bounded and diagnostics
  outside UI repaint scheduling. Measure instrumentation overhead too.

## Phase 2: SD695 single-variable experiments

Run in this order, restoring the control between experiments:

1. **Narrower scene:** `mcla_aspect_ratio = "16:9"`, retaining 720-line guest
   mode and resolution scale 1. Expected guest size is 1280x720 instead of
   1600x720: 20% fewer pixels, not a promised 20% speedup. Verify effective size,
   HUD and framing; pillarboxing is an explicit visual trade-off. Avoid 540/600
   line modes until the old lower-height crashes have been understood.
2. **Compiler concurrency:** `vulkan_pipeline_creation_threads = 2` instead
   of automatic. Check whether CPU contention decreases without extending the
   missing-pipeline queue or increasing visual defects. Restart required.
3. **Memory cache trial, only with telemetry:** soft/hard texture cache limits
   256/512 MiB versus current defaults 384/768. This bounds a cache, not total
   app RAM. Watch for eviction/reload churn and worse p95 stalls. Existing GPU
   pressure logic can halve these values again; check that interaction before
   combining settings. Test a 15-second unused-render-target trim separately
   from cache limits (current app default 60 seconds).
4. **Submission scheduling, after the above:** test split-draws 0 versus 1000
   while retaining threaded submission. Do not use split-draws 1, which stalled
   a previous loading experiment. Test primary-buffer-end submission separately
   only if measurements indicate submission overhead.

Do not disable accurate occlusion queries or vertex memory exports, force
unsupported feature bits, clear caches, enable unsafe compiler options, or
install a different driver in the same test. Preserve an explicit user driver
switch; never apply the S23 `syncdraw` workaround automatically to all Adrenos.

## Phase 3: one APK, explicit reversible settings

- One Snapdragon/Adreno app/package and release artifact; remove device-branded
  build behavior in favor of optional settings. Diagnostic tools can remain
  developer build targets, not separate consumer downloads.
- Suggested sections: Standard, Low-memory experiment, Visual compatibility,
  Audio, and Diagnostics. Profiles display exact changes and restart needs.
- Hardware detection suggests a profile; it does not silently rewrite existing
  settings. Consider GPU, actual driver/version/capabilities, RAM and firmware;
  GPU family alone does not establish driver compatibility.
- Apply profiles atomically with a preview and reversible backup. Reverting a
  profile must not overwrite unrelated settings changed by the player later.
- A low-memory profile becomes recommended only after the SD695 experiment
  passes. Keep unrelated changes out of each test APK.

## Phase 4: RedMagic audio

- Expose existing stereo/automatic, normal/low-latency and adaptive-queue
  controls in the common launcher. Preserve old preferences during migration;
  no automatic change of S23 output mode or driver flags.
- Enable `audio_stats` without audio dumps for a diagnostic run: underruns,
  queue minimum, mixer wake delay and mixer execution time. Correlate with
  frame stalls. Existing supplied RedMagic logs do not identify the cause.
- Test stereo first, then fixed queue depth 16 versus 8 blocks (roughly
  85 vs 43 ms of guest audio buffering), then adaptive queue, then the separate
  client lock if timing indicates lock contention. Restore control between tests.
- Do not promise Android gains from Windows-only mixer priority or require
  root/SCHED_FIFO scheduling. More buffering adds latency; it does not repair
  an indefinitely late mixer or defective samples.

## Acceptance and delivery

Use identical saved scene/route, cache state, driver and debug flag, plus similar
starting thermal conditions. Obtain repeated warm-cache runs, including a
longer run for stability. Separate startup preparation from gameplay. Compare
update/swap intervals, p95/p99, gaps above 100/250/500 ms, memory growth/pressure,
audio underruns and visible/input errors. Host presentation rate alone is not
the result. A stability improvement without higher average FPS can still pass;
new visual, input or audio regressions cannot.

On-device SD695 and RedMagic validation requires those devices/testers. S23
checks and host compilation cannot establish a fix for another GPU. Build,
signature/hash and package checks precede delivery; copy candidates to the
requested MCLA download directory and preserve previous working APKs. Do not
uninstall, clear app data, or replace the public stable APK without validation.
