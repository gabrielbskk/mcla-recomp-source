# Shared Snapdragon audio queue candidate

This is a possible workaround for short audio underruns, not a confirmed
RedMagic fix. The existing logs do not establish the cause of the cuts.
The candidate uses the existing native SDL audio queue implementation; it does
not replace the decoder or introduce device-specific native libraries.

The common launcher's Extras section exposes three restart-required modes:

- **Saved/default queue:** removes the experiment's queue/statistics overrides.
  The runtime normally uses 8 blocks, approximately 42.7 ms of guest audio.
- **Anti-cut test / 16 blocks:** fixes the queue depth at 16, approximately
  85.3 ms of guest audio. More lead time can absorb a short mixer delay; the
  added latency and other system buffers must be considered.
- **Adaptive / 8 to 24 blocks:** starts at 8 and adds 4 after a steady underrun,
  bounded at 24 (128 ms). It cannot compensate for sustained late production.

The two test modes enable `audio_stats` at five-second intervals. No WAV dump
is requested. The existing statistics include SDL underruns, queue minimum,
worker timing and the game's mixer timing. Inspect them after a test instead
of inferring audio quality from host FPS or channel-count logs.

Stereo is an independent explicit switch; queue profiles do not alter channels,
normal/low-latency mixer selection, driver, GPU flags, frame target or visuals.
On the supported common build the default keeps the prior output mode and
queue/configuration. The old RedMagic-specific adaptive button is replaced by
the common controls; historical diagnostic APKs remain historical, not an
additional consumer build target.

## First RedMagic test

Keep the same working driver, graphics settings and normal/low-latency mode.
If comparing against the previous stereo diagnostic, enable stereo so channel
layout remains the same. Select **16 blocks**, restart the game, and play the
same section for 1–2 minutes. Record when cuts occur and share the exported
log after pause/exit. Revert to Saved/default if latency or stutters worsen.
Do not enable adaptive mode or change drivers in the same comparison.

A successful test means fewer steady underruns and fewer reported cuts without
unacceptable latency, loss of sound, or worse gameplay. If underruns persist,
use mixer/wake timings to decide whether to test the existing separate audio
client lock next. If no underruns accompany the audible defect, investigate
routing, downmix, samples/decoder and Android backend instead of growing the
queue indefinitely. S23 validation cannot establish a fix on RedMagic.

## Local delivery

Version `0.1.54-audio-queue-test` (54) was packaged and installed as an update
while the game was not running. No audio preferences were changed automatically.
The versioned APK is `Download/MCLA/MCLA-Recomp-audio-queue-v0.1.54.apk`.
SHA-256: `7d3681329ef6d2dff200873ea2bad0f14f8f1583e5f74d2f94c692973033ac96`.

Java compilation, 37 audio policy checks, 128 keyboard/mouse policy checks and
nine resolution checks passed. Audio cvar definitions were checked against SDK
source and their presence checked in packaged native libraries; controls were
checked in DEX. All packaged native libraries are byte-identical to v0.1.53.
Signing certificate matches the prior candidate; build alignment checks passed.
These checks do not validate gameplay or sound on RedMagic. The original
RedMagic diagnostic's adaptive preference is retained only in that variant's
Saved/default mode; explicit common profiles take precedence.
