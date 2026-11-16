# Normal power policy and optional keyboard/mouse candidate

This experiment exposes Android sustained-performance mode instead of forcing
it on. In this **test candidate**, the default is OFF (normal Android policy);
the launcher switch restores ON for comparison. Neither setting forces clocks,
changes the governor, disables thermal protection, or guarantees higher FPS.
The requested value is logged. GPU clock caps still require on-device sampling.

A 60-second S23 capture with v0.1.52 found maximum permitted GPU frequency
295 MHz in 51/61 samples and 348 MHz in 10/61, with GPU occupancy often 99%.
Game-update windows ranged about 7.6–18/s, with p95 up to 821 ms. Battery
temperature was 38.2–38.4 C, Android thermal status 1. Four new pipelines were
logged during the capture; no device-loss or out-of-memory error was logged.
These are observations, not proof that sustained mode caused the cap/stalls.
CPU activity was also substantial. Raw traces remain private and outside Git.

## Input

The optional launcher modes enable the SDK's existing keyboard/mouse synthetic
controller, merged into guest user 0 with touch and physical pads. No second
Android native event injector is added. Input mode is OFF by default so the
power-policy comparison is not contaminated by enabling another input source.
The preset uses launch environment overrides; it does not rewrite TOML.

- W/S: RT/LT (gas/brake in the standard game mapping).
- A/D: steering. Digital keys send full input, not analog pedal pressure.
- Arrows: D-pad/menu navigation.
- Space: A; Backspace/Escape: B; R: X; F: Y.
- Q/E: LB/RB; G/C: left/right stick press.
- Enter: Start; Tab: Back.
- I/J/K/L: right-stick camera.
- Keyboard+mouse mode: movement drives camera; LMB/RMB map to A/B.

The touch HUD hides when the selected external keyboard/mouse is connected,
as it does for a physical pad. Without such a device it remains available.
Mouse capture depends on Android/SDL support; relative capture generally needs
Android 8+. The SDK falls back to cursor recentering if capture fails, which
also needs device validation. Returning to Android Home loses focus and the
SDK clears held input and releases capture. Mouse is stick emulation, not a
native unlimited-rate PC camera or steering control.

## Validation still needed

- Compare sustained ON/OFF on the same route with identical resolution, driver,
  debug switch, cache and starting conditions. Do not use the input mode during
  this comparison. Observe clocks, updates, p95 and temperature.
- With real USB/Bluetooth devices, check simultaneous steering and triggers,
  menu presses/releases/repeats, camera motion, mouse capture/release, focus
  loss, reconnect, and switching back to touch/gamepad mode.
- Host policy tests and packaged-symbol checks do not prove working Android
  events or gameplay. Do not advertise this as validated keyboard/mouse support
  or as a performance fix before those tests.

The prior resolution choices, pedals, explicit syncdraw setting and disabled
shader-pack state are not changed by these additions.

## Local delivery

`0.1.53-power-input-test` (versionCode 53) was installed as an update after
checking that the game process was no longer running. The prior versioned APK
is retained. SHA-256:
`cbf2bd3af027326ab41265ea5418a13f90faeebe913f76f92decf65f0fdd0477`.

All four main native libraries (`libmain`, runtime, GPU plugin, SDL) are
byte-identical to v0.1.52, so the normal input-OFF comparison changes Java-side
power policy, not renderer code. The runtime contains the MnK implementation.
Java build, nine resolution checks, keyboard/mouse policy checks and validation
of every preset cvar/key name against SDK source passed. Signature matches the
previous candidate and alignment verification passed. No hardware input or
sustained-mode performance result is claimed yet; this is not a public release.

## Subsequent S23 observation (not a controlled A/B)

The user reported improved performance after installation. A second 60-second
capture, still using syncdraw and 1280x720 with keyboard/mouse OFF, observed:

| Measure | Earlier v0.1.52 capture | Subsequent v0.1.53 capture |
| --- | --- | --- |
| Median of sampled two-second game-update rates | 8.91/s | 23.35/s |
| Median of the sampled windows' p95 intervals (not a whole-run percentile) | 272.09 ms | 62.02 ms |
| GPU maximum permitted frequency | 295 MHz (51/61), 348 MHz (10/61) | 550 MHz (14/61), 475 MHz (40/61), 401 MHz (7/61) |
| Battery temperature | 38.2–38.4 C | 40.0–40.8 C |
| Android thermal status | 1 | 2 |
| New pipelines logged during capture | 4 | 0 |

The second capture still logs guest-frame gaps of 418–580 ms; it is not fixed.
No device-loss/OOM error was logged and the system low-memory signal was false.
SDLThread consumed about 89% of one CPU core, and a guest thread about 79%.

Crucially, querying the framework's effective
`android:bool/config_sustainedPerformanceModeSupported` returned **false**.
The app guards its sustained-mode call with the corresponding support check;
there is no diagnostic `sustained_performance` event in either session. Thus
this improvement must **not** be attributed to disabling sustained mode. The
main native libraries are identical. Different routes, OS power policy, clocks
and other runtime conditions remain uncontrolled. Higher available GPU clocks
coincide with improvement but do not identify the source of the changed cap.
Investigate remaining CPU work/frame gaps rather than advertising a proven
power-policy fix. The keyboard/mouse feature remains untested with hardware.
