# In-game cheats and upstream input investigation

Status: research only, 2026-11-14. No cheats have been enabled or packaged.
Do not present a nonfunctional menu as implemented support.

Follow-up after v0.1.54 delivery: the user reported working keyboard and mouse
on Android. This validates basic operation by user report, not every capture,
focus-loss, menu-repeat or reconnect edge case. RedMagic testing remains pending.

## Requested cheat menu

Expose the game's own offline cheats in an in-game menu, rather than requiring
typed codes or a permanent save edit. Candidate first set:

| Native cheat | Original collection unlock |
| --- | --- |
| Unlimited nitro | 10 barrels |
| Never damage out | 20 barrels |
| Unlimited special abilities | 30 barrels |
| Top-down view | 40 barrels |
| No police | 50 barrels |
| No fines | 60 barrels |

The unlock list is documented by GamesRadar's MCLA cheat guide. It does not
identify runtime setters, active-state storage, progression effects or save
behavior for this executable. The Xenia Canary Complete Edition patch file
(title 545407F8, media 5940C9DB) contains frame-rate/graphics patches, not these
cheats. No verified cheat setter or active-state offset was found in the
currently inspected port/upstream code. Do not invent addresses or substitute
telemetry fields for a verified cheat implementation.

Before adding toggles:

1. Identify the native cheat manager/menu handlers and executable compatibility.
   Prefer calling verified game setters, on the appropriate guest thread,
   over writing arbitrary heap offsets or faking collectible totals.
2. Verify native effects on saving, rewards, races and progression; display
   the actual restrictions. Back up saves before any authorized gameplay test.
3. Keep cheats OFF by default, not automatically persisted or enabled on load.
   Test toggling, car changes, death/recovery, pause, restart and return to
   ordinary play. Do not claim that disabling a cheat undoes a saved change.
4. Only then add real toggles via the existing in-game settings/menu system.
   No money/car/collection editing is included in this request by implication.

## Original input support

The identified holdmysocks and RenanQueiroz MCLA projects, and mzzvxm/LARecomp,
are C++, not Rust. The current Android candidate enables the SDK's existing
MnK synthetic pad; it does not include LARecomp's native-keyboard implementation.

LARecomp's `src/mc_engine/keyboard_native.cpp` contains useful architecture:
separate menu/driving mappers, contextual Start vs accept/back, held-key
disarming at menu transitions and keyboard glyph handling. Its host key feed
uses Win32 `GetAsyncKeyState` / `GetForegroundWindow`; on other platforms its
fallbacks return false. Directly copying it does not enable Android input.
It also depends on that project's hook, modloader, glyph and UI systems.

An Android adaptation would need a licensed/attributed implementation or an
independently written equivalent, SDL/Android key events, focus-loss clearing,
verified hook compatibility and mutual exclusion with synthetic MnK emulation.
Do not run both feeds simultaneously. Mouse capture remains a separate SDK
capability, not evidence that the native keyboard/menu code is ported.

## Profiling follow-up

An attempted 20-second simpleperf capture for PID 24378 failed because that
process had already exited. No CPU profile or function-level attribution was
obtained. Do not treat a stale/empty capture file as a valid recording or retry
against the old PID. Discover the live PID before the next authorized sample.
The previous completed 60-second sensor captures remain valid observations,
not a controlled build-performance benchmark.

## Sources

- https://www.gamesradar.com/midnight-club-los-angeles/cheats/
- https://github.com/xenia-canary/game-patches
- https://github.com/mzzvxm/LARecomp/blob/main/src/mc_engine/keyboard_native.cpp
- https://github.com/holdmysocks/mcla-recomp
- https://github.com/RenanQueiroz/mcla-recomp
