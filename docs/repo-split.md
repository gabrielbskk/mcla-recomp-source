# Repository split (2026-11-16)

Two repositories, one project:

| Repository | Visibility | Role |
| --- | --- | --- |
| `gabrielbskk/mcla-recomp-rv` | public | Distribution: Releases with APK assets, changelog, player docs, issue tracking. |
| `gabrielbskk/mcla-recomp-source` | private | Everything else: full history, source, SDK patches, engineering notes, snapshots. |

## State of the public repository

Commit `3b0858c` ("Reduce the public repository to its distribution role")
removed from the working tree only — no history rewrite, no tag or release
deletion:

- `src/`, `android/`, `scripts/`, `config/`, `generated/`, `archive/`
- `patches/` and `docs/fork-base/` (~40 MB of duplicated SDK patches)
- `CMakeLists.txt`, `CMakePresets.json`, `mcla_manifest.toml`, `.recomp.json`
- internal docs: `android-build*.md`, `android-sdk-source.md`,
  `android-performance-handoff.md`, `ai-handoff.md`, `regression-analysis.md`,
  `repo-preservation.md`, `docs/archive/`
- `.github/workflows/` and `.github/dependabot.yml` (they build the source that
  is no longer there)

Kept: `README.md`, `LICENSE`, `CREDITS.md`, `KNOWN-ISSUES.md`, `SECURITY.md`,
`THIRD-PARTY-NOTICES.md`, `CHANGELOG.md`, player docs, `docs/assets/`,
`docs/images/`, and new issue templates.

The public source is still readable in history (it was public before) and in the
two pre-existing forks (`contatg/mcla-recomp-forkbr`,
`Rydev-pc/mcla-recomp-rv-helio-g85`). The split is a presentation change, not a
secrecy measure for anything that was already published.

## What was never public

LSFG/DXBC integration, `Lossless.dll`, shader packs, GPU drivers, build keys and
the diagnostic tooling were never committed. `git grep -i 'lsfg|Lossless'` on
the public `main` returned nothing before the split.

## Rules that follow from the split

- **Never `git pull` public `main` into the full local worktree**
  (`~/mcla-recomp-rv`). The slim commit deletes files that the worktree still
  holds; a merge would fight with the uncommitted changes. Fetch and cherry-pick
  a specific commit instead, or work from this private repository.
- Publishing an APK is a **Release asset** only. The APK is ~113 MB, above the
  100 MB git limit, and `*.apk` is ignored.
- Every public build owes GPL-3.0-or-later corresponding source. The public
  README carries a written offer for it (GPLv3 §6(b)); honour requests for at
  least three years per build.
- Release drafts: `v0.1.64` is a draft with the APK already attached. Publish
  only after in-game validation.
