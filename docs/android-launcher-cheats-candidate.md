# Launcher cheat requests (experimental)

Only three startup developer options are allowlisted: `unlimitednitrous`,
`nodamage`, `unlimitedspecial`. These are NOT money, unlock-all, save or
collectible edits. All are OFF by default. Select a request in Extras before
Play; requests are consumed and selections cleared for the next launch.
Their environment variables do not use the cvar persistence/config system.

The existing `mcla_dev_option_registered` hook handles them after the registrar
clears node+4. It reuses the existing guest heap allocation of ASCII `1`, not
the Windows implementation's writable-code scratch space. Native consumers
retain their own restriction checks. Requests do not force flags every frame:
retail mode/load/menu transitions may clear them. This candidate needs testing
from the title screen through profile load and free roam before support can be
claimed. A successful request log proves slot injection, not an audible/visible
effect or unrestricted career progress.

Static analysis of the matching game/codegen backup identified:

- `sub_82388340`: startup developer options feed flags checked in gameplay.
- `sub_8264B2B0`: six native menu toggles, requires a valid menu object.
- `sub_8264B3A0`: native menu rows gated at 10/20/30/40/50/60 collectibles.
- `sub_82387A98`: native active-cheat predicate, consulted in profile and other
  paths. Exact saving/reward/achievement consequences remain unvalidated.
- `sub_82387B38`: clears multiple cheat flags; it has several callers.

The earlier claim that official cheats cannot be toggled at runtime without
save edits was incorrect. The launcher approach deliberately does not invoke
the menu callback with an invalid object or bypass the predicate. Direct UI
unlocks are not part of this candidate.

Do not test on the only copy of a valuable save. Disabling the next-launch
request does not undo gameplay or saved progress effects. No automatic game
launch, save manipulation or cheat-enabled on-device test is authorized by
merely packaging this code. No APK is to be uploaded to GitHub.

## Local candidate delivery

`0.1.56-launcher-cheats-test` (versionCode 56) compiled and was installed as a
compatible update after checking that the game was not running. No automatic
game launch or enabled-cheat gameplay test was performed. Requests remain OFF.
The common build defaults the optional S23 visual-tradeoff profile OFF unless
explicitly chosen in preferences. Audio, input, syncdraw and shaderpack state
are not changed by the cheat policy.

Java and native policy tests passed, including environment-name agreement;
existing audio, keyboard/mouse, resolution and performance policy tests passed.
Native rebuild completed with codegen reporting all modules up to date. Only
`libmain.so` differs from v0.1.55; runtime/GPU/SDL and other libraries match.
Certificate, version, DEX controls, native markers and alignment were checked.
These checks do not establish working effects after profile load or save safety.

Local delivery filename: `MCLA-CHEATS-TEST-v0.1.56.apk` (Downloads and private
netbook APK folder). SHA-256:
`1153b843da714b438e2bb431650b0cdde7858621ec25c5d0a94633435950e083`.
No GitHub upload or source push was performed for this candidate.
