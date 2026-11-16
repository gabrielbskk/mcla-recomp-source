# SDK worktree snapshot (2026-11-16)

`third_party/rexglue-sdk` is a separate Git repository (fork of
`rexglue/rexglue-sdk`) and is not part of this repository's history.

- `base-commit.txt`: the SDK commit the worktree was based on.
- `tracked-changes.patch`: `git diff --binary` of the tracked changes
  (includes the v64 LSFG pacer instrumentation, MAILBOX present mode and the
  pacer watchdog).
- `untracked-files.tar.gz`: the untracked, non-ignored files (LSFG API headers,
  Android surface, low-latency code, shader headers, FidelityFX shaders).

Apply the patch from the SDK root, then extract the tarball there. Restore with
`git checkout -- .` and removing the extracted files.
