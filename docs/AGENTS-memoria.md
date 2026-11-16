# Memória do ambiente (Termux / home)

## Projeto MCLA Recomp RV — estado desde 2026-11-16

### Repositórios

| Repo | Visibilidade | Papel |
| --- | --- | --- |
| `gabrielbskk/mcla-recomp-rv` | público | Distribuição: Releases com APK, changelog, docs de jogador, Issues. **Sem fonte.** |
| `gabrielbskk/mcla-recomp-source` | privado | Histórico completo, fonte, patches do SDK, notas de engenharia, snapshots. |

- Público: commit `3b0858c` ("Reduce the public repository to its distribution role") removeu do **topo** `src/`, `android/`, `scripts/`, `config/`, `generated/`, `archive/`, `patches/`, `docs/fork-base/`, build system, docs internos e `.github/workflows/`. Sem force-push, sem remoção de tag/Release/fork.
- Mantidos no público: `README.md`, `LICENSE`, `CREDITS.md`, `KNOWN-ISSUES.md`, `SECURITY.md`, `THIRD-PARTY-NOTICES.md`, `CHANGELOG.md`, docs de jogador, `docs/assets/`, `docs/images/`, `docs/turnip-a740-rendering-artifacts.md`, templates de Issue.
- Privado: branches `main`, `android-tested-input-20261114`, `retire-mali-20261114`, `worktree-2026-11-16` (worktree atual + snapshot do SDK em `patches/sdk-2026-11-16/`), `public-main` (espelho do estado público). Doc do split: `docs/repo-split.md`.

### Regras que não podem ser quebradas

1. **Nunca `git pull` do `main` público em `~/mcla-recomp-rv`.** O commit slim apaga arquivos que a árvore completa ainda tem (dezenas de alterações não commitadas). Use o repo privado ou cherry-pick de commit específico.
2. **APK só como asset de Release** (~113 MB > limite de 100 MB do git; `*.apk` está no `.gitignore`).
3. Licença é **GPL-3.0-or-later**: build público exige fonte correspondente. O README público traz oferta escrita (GPLv3 §6(b)) e a promessa vale por 3 anos por build.
4. Não apagar nada sem autorização explícita; instalações/repos só com autorização; não abrir o jogo sem pedido.

### Release final

- **v0.1.61** publicada como **release final** no repo público — "This is the final public release. Development is paused indefinitely. I'm stepping away from recompilation work and AI-related projects due to internal circumstances. The private repository will remain but no further public builds are planned. No ETA for any future work."
- Issues fechadas (6/6) com contato Discord `gabrielbskk`
- CHANGELOG e README atualizados com aviso de pausa definitiva

### Build hardened v0.1.61 (2026-11-16)

APK normal **hardened** publicado no Download e no netbook:

| Campo | Valor |
| --- | --- |
| Arquivo | `MCLA-NORMAL-v0.1.61-hardened.apk` |
| Tamanho | 52 MB (vs 112 MB original — strip + R8) |
| SHA-256 | `277430ede257abd57fb91e82fa5854c3d6aa65d92d806600d6ca2a6b48b318c3` |
| Keystore | Release RSA 3072 (`out/release-keys/mcla-release.keystore`, alias `mcla-release`) |
| Assinatura | v1 + v2 + v3 |
| Native libs | Strip `--strip-unneeded` (libmain.so 82→34.9 MB, sem `.debug_*`, `.symtab`) |
| Java | R8 full mode + `android/proguard-rules.pro` (ofuscação, remoção código morto) |
| Anti-tamper | `AntiTamper.verifySignature(this)` em `MclaLauncherActivity.onCreate`; hash do cert SHA-256 embutido (`nsYCX6oRoiMcwiaLy4eZiCaL3UWKZsZ2rdx0dHr+AWQ=`) |
| Variante | Normal (sem LSFG), code 61 |

Script: `scripts/build_android_apk_hardened.sh` (usa `MCLA_VERSION_CODE=61 MCLA_VERSION_NAME=0.1.61-hardened`).

### O que nunca esteve público

LSFG/DXBC, `Lossless.dll`, shader packs, drivers de GPU, keystore e ferramentas de diagnóstico. `git grep -i 'lsfg|Lossless'` no `main` público retornava vazio antes do split. O código antigo segue no histórico público e nos forks `contatg/mcla-recomp-forkbr` e `Rydev-pc/mcla-recomp-rv-helio-g85` — o split é apresentação, não sigilo retroativo.

### Ambiente

- Build: SDK `cmake --build out/build/android-arm64 -j 2` + `cmake --install` (prefixo `out/install/android-arm64`); app `cmake --build out/build/mcla-android-arm64 --target mcla -j 2`; empacotar com `MCLA_VERSION_CODE/NAME MCLA_INCLUDE_LSFG=1 MCLA_APK_VARIANT=adreno-diagnostic MCLA_KEYSTORE_PATH=... scripts/build_android_apk.sh`.
- `gh` ativo = `gabrielbskk`; clone/push HTTPS usa `gh auth git-credential`.
- Sem ADB: instalação via `su -c pm install`. Log do jogo em `Documents/MCLA/logs/` (`mcla.log`, rotativo ~4,8 MB).
- Driver ativo: `files/gpu-drivers/selected.txt` = `libvulkan_freedreno.so` (Turnip).

<!-- Atualize esta seção quando o split ou a release mudarem. -->
