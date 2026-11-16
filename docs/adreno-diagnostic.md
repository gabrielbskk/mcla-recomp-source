# Adreno 730 diagnostic APK (experimental)

This variant is **not** a freeze fix. It retains the stable package ID and must be
signed with the certificate used for v0.1.45 so installation over the existing
app preserves game data and saves. Never distribute the signing key.

Build the Android target with `-DMCLA_ADRENO_DIAGNOSTIC_APK=ON`, then package
with `MCLA_APK_VARIANT=adreno-diagnostic`, an explicit version code higher than
the installed one, and `MCLA_KEYSTORE_PATH` pointing to the compatible key.
The packager refuses a native library without the watchdog marker. The normal
build option is OFF; ordinary APK packaging still uses the ordinary manifest.

The native watchdog logs every five seconds even if the UI stops repainting:
update and swap deltas/totals and ages, UI-paint age, and the age of the last
completed host presentation. Host presentations are **not** display scanout.
The Android-side heartbeat records battery level/status and the Android thermal
status every five seconds, with lifecycle events and the selected driver/debug
switch. The latter is a separate, bounded private log (`mcla_a730.log`, with
one rotation). Native and Android logs are exported to `Documents/MCLA/logs/`
when returning to the launcher or pausing, and periodically while the UI runs.
Native logging flushes every five seconds. Process death can still lose up to
five seconds of data. If the **entire process** hangs, automatic export may
require force-closing and relaunching the app.

Run first with **Use system driver**, then optionally try trusted Turnip with
**No switch**. The diagnostic variant uses a separate Turnip preference, so
an old `syncdraw` selection is not silently reused. An explicit `driver.env`
override still takes precedence and is reported in the log. Record the time,
game location, audio and controller response, and whether the picture is
frozen. Share `mcla-current.txt` and `mcla_a730.txt` (plus rotated copies, if
present) after removing personal paths. A gap in updates can also mean a
loading screen or pause; the watchdog hints alone do not prove a GPU fault.
