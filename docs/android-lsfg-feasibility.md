# Android LSFG feasibility investigation

## Status

User requests optional Lossless Scaling frame generation using their own DLL.
Research only so far: no FG code integrated, game launched, runtime replaced,
driver changed or APK produced for this feature. Existing61 remains the baseline.
The user has now supplied `Lossless.dll` in Download. Static metadata and
resource checks below do not establish pipeline/GPU compatibility.

## Findings

- [Winlator-LSFG](https://github.com/amyroseee/Winlator-LSFG) explicitly uses a
  user-provided DLL and a glibc environment. Its
  `tools/lsfg-vk-glibc/build.sh` pins upstream commit
  `8b0da2661c6f3473a7fccc8ba643880050e71642`, builds aarch64 GNU/Linux, and
  applies `compatibility.patch`. This is not an Android/Bionic binary ready
  to place in this APK. That exact upstream commit's `LICENSE.md` is GPLv3.
- [Bannerlator](https://github.com/The412Banner/Bannerlator) has multiple
  generation approaches. Its `BIONIC_FG_INTEGRATION_REPORT.md` describes an
  Android layer, AHardwareBuffer interoperability and several revisions of
  crash/hang diagnosis. These reports are upstream claims, not validation on
  this game/device. Its root `build-lsfg-android.sh` alone is not proof of
  functional integration; it assumes a suitable source tree exists.
- [Native Vulkan pipeline explanation](https://lsfg-vk.dev/blog/porting-lsfg-to-native-vulkan/):
  lsfg-vk parses PE data, extracts shaders from the supplied DLL, translates
  DXBC using a DXVK subset and executes a rewritten Vulkan pipeline. It need
  not execute Windows DLL instructions through Wine. Consequently, rejecting
  native Android feasibility merely because the input is a DLL is incorrect.
- [Current upstream licensing](https://lsfg-vk.dev/docs/contributing/) changed
  to CC BY-NC-ND4.0 after a rewrite. Do not assume current sources can be
  modified and redistributed under the older terms. Private adaptation and
  distribution have distinct license implications; review before shipping.
- A concrete older candidate exists: tag `v1.0.0`, commit
  `7113d7d02da9fc9df5cb3b03230d1f7de86f7056`, whose root `LICENSE.md` is MIT.
  Its `framegen/CMakeLists.txt` has v3.1 and performance-model source groups.
  This is a candidate, not proof of compatibility with the user's DLL. Audit
  pinned submodules/dependencies and retain their notices before incorporation.
  Do not copy later restricted code into this older snapshot.

## Local integration considerations

The shared SDK already has a FidelityFX-related presenter implementation in
`src/ui/vulkan/vulkan_presenter_frame_generation.cpp`. It is not LSFG and its
presence does not mean generation is enabled or validated on Android. Preserve
this pre-existing work rather than replacing it wholesale.

`src/ui/vulkan/vulkan_instance.cpp:161–167` loads either the custom Android
driver through AdrenoTools or the system loader. `vulkan_presenter.cpp` creates
a real `VkSwapchainKHR` and calls `vkQueuePresentKHR`. These are promising
integration points, but Linux implicit-layer discovery cannot be assumed to
work identically in the Android/custom-driver loader chain. Test loader
discovery separately or evaluate explicit backend integration in the presenter.
Avoid requiring global Vulkan/root settings for a per-app optional feature.

## Smallest implementation sequence

1. Identify the supplied DLL by size/hash/PE version, without executing it or
   publishing it. Confirm extraction compatibility with a license-reviewed pin.
2. Build a standalone ARM64/Bionic backend/extractor outside the production
   SDK. Tests first for unsupported/corrupt DLL rejection, bounds and disabled
   behavior; compiler success is not GPU validation.
3. GPU-only test with two known input images: check generated image differs
   from both, verify output pixels/flow and measure GPU time, memory and errors.
   No repeated real frame should be counted as generated.
4. Only after that, integrate optional Off/2x with Off as default. Enforce
   semaphore/image ownership, bounded waits, resize/pause cleanup, reset of
   history on discontinuities and safe handling of device loss. Do not stack
   two FG backends/pacers. Retain native frame metrics separately from output.
5. Device A/B on the same route: base frame intervals, output cadence, FG GPU
   cost, input latency, artifacts, thermal and memory. No promise that FG fixes
   CPU/streaming stutters; extra GPU work can reduce base FPS. Larger multipliers
   should not precede a correct and measured2x path.

DLL/shaders remain user-supplied private assets; no bundling in public source,
APK or GitHub upload. New delivered APKs must also be backed up privately to
the user's netbook under the established delivery rule.

## Supplied DLL — initial static checks

- 7,521,280 bytes; SHA256
  `626b196d799606cd4250b7b29e04228692ab70cf56a5d1bbb56d748c8219f0eb`.
- PE32+ x86-64 (`Machine=0x8664`), fixed file/product version candidate3.2.2.0.
  This is a resource metadata value, not a signature/authenticity check.
- Bounds-checked resource traversal found300 RCDATA resources, one version
  resource and one manifest.202 bounded DXBC containers with unique SHA256s.
- Compared the old MIT extractor's `src/extract/extract.cpp` name/ID table:
  all52 names (48 distinct resource IDs) exist, begin with DXBC, and contain
  shader-model5.0 compute programs. Presence/model checks alone do not show
  matching shader interfaces, expected constants or correct output pixels.
- No DLL code was loaded/executed; no proprietary shader bytes were written
  into the source tree. Private metadata manifests are retained outside Git.

## Dependency recovery

Cloning the v1.0.0 recursive source initially failed because
`https://github.com/PancakeTAS/dxbc.git` no longer resolved. The gitlink expects
`78ab59a8aaeb43cd1b0a5e91ba86722433a10b78`. GitHub's commit API located exactly
that object in `Username404-59/dxbc`; the isolated research clone's local
submodule URL was redirected there. Do not replace the pinned commit with a
fork's arbitrary HEAD. License/dependency audit and successful Android build
remain separate gates before integration.

## ARM64/Bionic build and validation results

The isolated v1.0.0 source and pinned dependencies built successfully with the
Termux Clang21 ARM64/Bionic toolchain, CMake/Ninja, Release and two build jobs.
No game SDK/library was replaced. The original shared layer is a research
artifact, not an APK-ready or functionally validated integration.

Standalone extraction/translation probe:

- Malformed non-PE input was rejected with a nonzero result.
- Supplied DLL extracted and translated all52 named shaders (48 distinct
  resource IDs). Generated SPIR-V remains private outside the repository.
- `spirv-val --target-env vulkan1.1` rejected all binaries because the translator
  emits SPIR-V1.6, not because of a demonstrated malformed shader.
- Matching the backend's Vulkan1.3 requirement,
  `spirv-val --target-env vulkan1.3` passed52/52. This is structural validation,
  not a shader-interface, algorithmic or image-quality proof.

A read-only capability probe loaded `/system/lib64/libvulkan.so`, enumerated
Adreno740 Vulkan1.3.128, and reported external-memory-fd, external-semaphore-fd,
robustness2 and AHardwareBuffer support; timeline semaphores, Vulkan memory
model, synchronization2 and nullDescriptor were reported true. This does not
test external-handle type import/export compatibility. It is the system driver,
**not the selected in-app Turnip driver**. Android's `cmd gpu vkjson` service
query failed, so the results above came from the separate native probe.

### Context smoke test: observed failure and isolated fix

The performance-model2x backend initialized, but creating a256x256 context
with local images (`fd=-1`, as used by upstream's benchmark) failed with
`VK_ERROR_INVALID_EXTERNAL_HANDLE` (-1000072003) while binding image memory.
Inspection found that this path declared an OPAQUE_FD external-memory image,
then allocated ordinary, non-export memory. The isolated research adaptation
omits the external-memory image pNext when `fd<0`, preserving real-FD imports.

After rebuilding the framegen archive and relinking the **same** context probe,
create/delete passed in3350ms, including setup/translation/pipeline compilation
and teardown. This is **not per-frame FG time**. No compute dispatch or image
presentation occurred. The preceding failure and subsequent PASS are a limited
device regression test, not comprehensive unit coverage or a production fix.

Reproducible adaptation is recorded in
`patches/lsfg-vk-v1-android-local-image.patch`, targeting the pinned old MIT
source. It is NOT applied to the production SDK or packaging. Research probe
sources, logs, dependency manifests and private binaries are in the isolated
`lsfg-investigation/` workspace. No executable DLL code was invoked.

Remaining gates: known input/output pixel test, bounded synchronization,
external-image interoperability (if used), selected Turnip path, real GPU FG
cost/latency and correct integration with presenter pause/resize/device loss.
Do not expose a working FG toggle based only on these smoke-test results.

## Known-image dispatch/readback prototype

The private research probe now uploads two deterministic RGBA8 patterns into
local input images, dispatches the performance-model2x shader chain, waits on
the completion fence (5s maximum), and reads the generated image back. There
is no swapchain, Android window, game capture or game process involved.

Test-only friend access in the isolated context/generator classes exposes
input/output images and completion fences. Local images have transfer-source
and transfer-destination usage for staging; real-FD imports are unchanged.
The probe's missing private access was first observed as a compile-time failure;
the test-only access hooks then allowed compilation. These are research hooks,
not a published runtime API or a replacement of the production SDK.

`scripts/ci/test_lsfg_readback.py` verifies the readbacks. It checks fidelity
with static inputs, foreground area/position with moving inputs, and rejects
copied input0/input1, a black image, and a simple crossfade as negative controls.
This is a narrow pattern test; it cannot establish general game-image quality.

System-driver results:

| Resolution | Static RGB MAE (0–255 scale) | Expected moving center | Observed center |
| --- | --- | --- | --- |
|256x256|0.06385|(103.5,127.5)|(102.7839,125.7155)|
|1280x720|0.00471|(103.5,127.5)|(102.5789,125.8191)|

The256x256 result repeated with identical output metrics. Generation is not
merely repeating a real input or averaging the pair for this stimulus.
Three720p samples of CPU submission-to-fence time were19.4058,18.8564 and
18.7282ms, excluding upload/readback. These are **not pure GPU durations**,
steady-state percentiles or a game FPS prediction. No GPU timestamp query,
whole-app memory/thermal comparison or input-latency measurement was done.

### Selected Turnip path and history gate

A read-only private copy of the selected app driver was tested through
AdrenoTools. Driver SHA256 is
`5b0ae37bc3cbc504f5c8f6cd3323013381bd82448a652b8c22830a711225f0f6`.
The probe reports Turnip Adreno740 Vulkan1.4.368 with the required features.
An initial backend test deliberately rejected falling back to the system
driver when Turnip was requested. After adding an optional research-only
loader callback, the backend used Turnip and completed dispatch/readback.
This callback does not change system settings or the installed driver's files.

Crucially, the initial2-static-frame Turnip test **failed** static fidelity
(RGB MAE7.7162). No threshold was relaxed. A longer10-static-frame experiment
showed failures during cold history and recovery at frame3; **all seven**
static outputs at indices3–9 met the unchanged fidelity threshold, with MAE
0.01311. Moving frame10 again centered near(102.7839,125.7155). The model uses
three-slot temporal feature history, so an integration must not expose its
first outputs as valid immediately after creation/reset. A three-frame discard
is currently an observed test gate, not proof of sufficiency for every input,
resize or device event. Check repeatability and720p before relying on it.

Turnip measurements above use the selected driver binary, not yet the whole
game's queue/presenter chain or all its launch environment settings (including
`TU_DEBUG=syncdraw`). This distinction must remain explicit. Preserve syncdraw
in the app and validate the prototype under that environment before integration.

### Follow-up: longer-history720p, including syncdraw

The longer experiment passed at1280x720 with the selected Turnip copy. All
seven static frames at indices3–9 satisfied the original thresholds, followed
by the moving-frame and copy/black/crossfade negative-control checks. Maximum
static RGB MAE was0.0009346; moving center was(102.5789,125.8191), versus the
expected(103.5,127.5).

The same720p experiment then passed with `TU_DEBUG=syncdraw`. No driver or
game setting was changed: that variable was set only in the test subprocess.
Submission-to-fence times for mature frames3–10 ranged4.007–10.699ms in this
one short run. The previous run without syncdraw ranged7.563–17.113ms over
those indices. These are not controlled A/B results (clock/thermal/scheduling
state differed) and must not be interpreted as a demonstrated syncdraw speedup.
Neither includes transfer/readback, presentation, game rendering or input lag.

Commands actually executed:

```sh
python3 scripts/ci/test_lsfg_readback.py PRIVATE/turnip-warm-history \
  --static-start 3 --motion-frame 10
python3 scripts/ci/test_lsfg_readback.py PRIVATE/turnip-warm-720 1280 720 \
  --static-start 3 --motion-frame 10
python3 scripts/ci/test_lsfg_readback.py PRIVATE/turnip-syncdraw-720 1280 720 \
  --static-start 3 --motion-frame 10
```

Here `PRIVATE` denotes the research workspace, not a literal project path.
The tests exercise local images on one backend-owned device. They do not yet
validate the external-memory path or access to images owned by the game.

## Next integration gate (not yet implemented)

Prefer investigating an explicit same-device backend/presenter adapter rather
than blindly injecting the Linux layer. The current SDK already serializes
queue submissions through `VulkanDevice::Queue::Acquisition`; preserve this
ownership. Its existing FidelityFX pacer must not run concurrently with a new
LSFG pacer. Current device creation enables null descriptors for GPU emulation,
but synchronization2/Vulkan memory-model enablement must be reviewed before
borrowing the game device; physical support alone is insufficient.

Required adapter tests: disabled behavior unchanged, warmup/reset state and
history invalidation, proper submission dependencies, no reuse before completion,
bounded shutdown/pause, resize/device-loss cleanup, and correct real/generated
frame counters. A generated frame must never advance guest logic, input or save
state. Only after these gates should an optional Off/2x launcher control and
a new APK be delivered. The current APK61 is unchanged and contains no LSFG.

## Same-device / caller-command-buffer adapter in progress

The next isolated adaptation replaces the prototype's own device ownership and
per-frame submissions with borrowed renderer handles and recording-only work.
The test was extended first: compilation failed because borrowed-device and
recording-buffer constructors and `Context::record` did not exist. The minimal
research implementation now adds those interfaces; its build/readback validation
is a separate gate, not yet an APK integration.

The borrowed device view must never destroy the caller's VkDevice. A borrowed
recording-buffer view rejects begin/end/submit and never frees the caller's
VkCommandBuffer. `Context::record` emits the compute pipeline into that buffer
without creating queues, submitting work, allocating per-frame fences or waiting
on the CPU. The caller retains feature validation, queue serialization, GPU
resource retirement and submission ownership. The current view implementation
still allocates small shared-pointer control blocks; no zero-allocation claim
is made. This adaptation remains outside the production SDK until validated.

The packaged candidate and its unverified gates are recorded in
[android-v62-state-and-plan.md](android-v62-state-and-plan.md). That file also
lists the artifact hash, the installed baseline and the checks still missing.
