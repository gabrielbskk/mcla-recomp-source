# Bounded FPS telemetry experiment

The Android FPS dialog no longer requests its own next repaint from `OnDraw`.
This applies to both ordinary and Adreno diagnostic builds. Other dialogs can
still request continuous repainting: this change only removes the FPS dialog's
self-sustaining paint loop.

A worker requests at most two HUD refreshes per second while the FPS overlay is
enabled. Guest frames and other dialogs may cause additional UI draws, so this
is **not** a two-FPS cap on the game, menus, touch controls, or all UI painting.
One last refresh clears the overlay when disabled. Queued callbacks are
coalesced and invalidated before the dialog is destroyed; the worker is stopped
and joined before the SDK destroys the runtime/presenter.

Every five seconds the worker samples the presenter's synchronized history and
logs host presentation statistics and game-update statistics independently of
UI paints. It starts in `OnPostSetup`, after runtime initialization (dialogs
are constructed earlier), and is joined in `OnShutdown` before runtime teardown.
The existing diagnostic watchdog continues independently as well.
Logging alone does not request paints. The HUD shows game-update rate; neither
metric is display scanout or a count of visually unique frames.

No Vulkan synchronization, pipeline, cache, resolution, frame-target or thermal
policy defaults change. This does not establish a fix for Turnip light/wheel
artifacts or sustained FPS loss.

The experimental APK also includes the optional analog pedals and GPU advice
described in `gpu-advisor-pedals.md`. No driver is downloaded or selected
automatically. Mali advice does not make the Adreno APK a Mali-compatible build.
The combined APK is not a controlled performance-only A/B comparison.

## On-device acceptance checks (pending)

- Verify the startup `FPS telemetry` marker and five-second logs during loading,
  gameplay, and a scene with no advancing guest frames.
- Compare diagnostic UI-paint deltas, update counts, CPU and thermal state against
  the same scene/driver/cache/settings; do not use host FPS alone as a speedup.
- Toggle the counter and logging separately; verify the old HUD clears, log-only
  operation works, and enabling the counter again restores it.
- Exercise pause/settings dialogs, controller hotplug, touch input, minimize,
  resume, and shutdown, including closing while a UI callback is pending.
- Enable pedals in the editor; check both triggers plus steering, release/cancel,
  focus loss, saved positions, reset, and the unchanged classic layout.
- Check system GPU detection and curated driver warnings. Opening a browser must
  not modify the imported or selected driver.

Compilation and host-side pedal/advisor tests do not replace these checks.

## Local candidate

- `0.1.50-ui-pedals-test` (versionCode 50), Adreno diagnostic variant; not
  installed or published by this change.
- APK SHA-256:
  `1f62afb9a5c4ba0b2b9664fb04d548a7fc9677def29012e4d645653eed7f6114`.
- Packaged `libmain.so` SHA-256:
  `95c0e411e6b9429b772dc164bf0f6b21d60d6a3cd882e6c087af116e13ccf3fc`.
- Native and Java builds passed. All 20 existing pedal/advisor checks passed;
  a host-side stats harness also checked empty, concurrent, populated and stale
  update statistics. These do not test the Android timer/callback lifecycle.
- APK signature matches retained v0.1.49, ZIP alignment verified, and packaged
  native bytes match the final build. Existing APK retained separately.

## User test follow-up

The candidate was subsequently installed at the user's request. The user
reported substantially lower performance. The 19:32–19:33 session records
approximately 11–20 game updates/s in multiple windows. Its Android log reports
`TU_DEBUG=syncdraw`, whereas the previously analyzed 17:21 session used `none`.
Both identify Turnip Mesa 27 `git-e04357c464`. This is not a matched A/B of the
telemetry change; it does not establish that either the counter or syncdraw is
the sole cause. UI-paint counts did decrease to roughly swap counts plus timer
refreshes, but that alone is not a measured performance improvement.

No driver/debug switch or app graphics settings were changed during this
follow-up. The external GitHub catalog was removed from Java separately;
that source revision is not included in the installed candidate.
