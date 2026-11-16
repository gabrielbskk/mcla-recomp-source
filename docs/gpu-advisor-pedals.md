# GPU advisor and analog pedals

## Current UI revision

After testing v0.1.50, the user requested removal of the external GitHub
catalog. The advisor now shows only system GPU detection and general guidance;
the source picker, browser intents and curated source table have been removed
from Java. Local ZIP import is unchanged. No in-app driver downloader has been
implemented. The source research below describes the previous experiment, not
the current UI. This revision has compiled but is not yet packaged/installed.

## GPU advisory

The driver-import section probes the **system OpenGL renderer** using a small
offscreen EGL context on a background thread. This is a hardware hint, **not**
the identity of the Vulkan driver loaded by the game. Probe failures produce
`Unknown` and a system-driver recommendation; do not guess GPU from phone model.

No driver is downloaded, installed or selected automatically. The UI opens
GitHub release/compatibility pages in a browser. Imported libraries still need
the user's trust. GPU name alone cannot prove compatibility or improve FPS.

Sources reviewed via GitHub:

- [Mesa](https://gitlab.freedesktop.org/mesa/mesa): upstream Turnip/PanVK development.
- [K11MCH1/AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers/releases):
  community distributions of Adreno drivers. Release notes distinguish GPU
  families and experimental a8xx builds. Qualcomm proprietary releases in the
  same repository are **not** interchangeable with the Turnip import format.
- [MrPurple666/freedreno-CI](https://github.com/MrPurple666/freedreno-CI): another
  Turnip build project; the latest release returned by the API was 23.0.0 R5.
  It is not the default recommendation just because it is another source.
- [zenithblue-oss/panvk-kbase-android](https://github.com/zenithblue-oss/panvk-kbase-android):
  Mesa PanVK on Android mali_kbase; kernel/GPU support and packaging must be
  checked. Its claims are upstream-project claims, not MCLA gameplay validation.
- [FristOneRR PanVK v3.7.0](https://github.com/FristOneRR/FristOneRR-Panvk-Driver/releases/tag/v3.7.0):
  published compatibility includes G52/G57/G68/G71/G72/G76/G77/G78. Explicitly
  excludes G610/G615/G710/G720 and Immortalis; this link is only offered for
  listed GPU names, with numeric boundaries (G720 must not match G72).
  The release says the ZIP contains `libvulkan_panfrost.so`; this has not been
  tested with MCLA. Its Winlator wrapper/DXVK instructions are not MCLA settings.
- [StevenMXZ v27.0.0-R1](https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/tag/v27.0.0-R1):
  separate common, OneUI and Gen8 ZIPs in one release. Its Gen8 V38 notes list
  A810/A825/A829/A830/A840; these are not the A730/A740 package.
- [The412Banner/Banners-Turnip](https://github.com/The412Banner/Banners-Turnip/releases):
  Android/AdrenoTools ZIPs differ from Linux/glibc and Wayland artifacts.
  The 8G2-OneUI recipe is conditional on the described **system UI** flicker,
  not a universal workaround for game rendering artifacts. Hourly builds are
  CI-checked but not individually tested on devices according to their notes.
- [faux123 v1.2.10](https://github.com/faux123/turnip-faux123-driver/releases/tag/v1.2.10):
  publisher reports tests on Adreno 640/650/660/722/730/740/830, including Galaxy
  S23. Driver tests are not evidence of MCLA gameplay or all phones with that GPU.

The source picker shows GPU-family links and a per-source warning before opening
GitHub. These are curated snapshots of release notes, not a live compatibility
database. No source has been benchmarked here and the order is not a ranking.

The baseline is always the system driver. Adreno 6xx/7xx users are directed to
Turnip release notes, not a hardcoded supposedly optimal ZIP. Other Adreno GPUs
receive an explicit warning against assuming support. Mali/Immortalis users are
warned that Turnip is incompatible; the PanVK link is research, not an endorsement
of a specific package or a promise that the installed MCLA variant can import it.

## Pedals

Enable **Analog driving pedals** in the touch editor. The default remains the
previous Xbox-style layout. The two vertical pedals replace LT/RT, use the same
0–255 analog trigger bridge, and retain other Xbox buttons. RT is gas and LT is
brake/reverse according to the game's control mapping. They do not alter vehicle
physics, maximum speed or the game's button bindings.

Touch position controls intensity: bottom 0%, top 100%, center 50%. Drag up/down
to vary it; the display shows the percentage. Releasing, cancelling or losing
focus resets input. Steering and pedals can be used simultaneously. Multiple
fingers on a trigger combine using the maximum, not iteration order. Pedal
positions have separate saved keys, preserving the old LT/RT layout on toggle.
Both can be moved and scaled in the editor. Reset clears both layouts and returns
to the old mode. Physical gamepads continue to hide/clear touch controls.

Validation needed on-device: GPU text on system/custom Vulkan, no-network case,
failed EGL probe, both pedals with steering, release/cancel/focus-loss, layout
editing/reset and a connected gamepad. Compilation alone does not validate
gameplay ergonomics or driver performance.
