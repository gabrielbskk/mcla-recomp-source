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

### Release pendente

- `v0.1.64` existe como **rascunho** no repo público, com `MCLA-v0.1.64-lsfg-pacer-mailbox.apk` já anexado (113.329.530 bytes, sha256 `c6bea39228976be833dc1f42fb3c70d1f78edc574d6887e8e5614a2ad96c55fa`).
- Publicar só depois de validar em jogo:
  `gh release edit v0.1.64 -R gabrielbskk/mcla-recomp-rv --draft=false --prerelease`
- v64 não foi testada em jogo nem enviada ao netbook (`ssh mcla-cloud`, `/home/void/MCLA-RECOMP-RV-/MCLA-APKs/`).

### O que nunca esteve público

LSFG/DXBC, `Lossless.dll`, shader packs, drivers de GPU, keystore e ferramentas de diagnóstico. `git grep -i 'lsfg|Lossless'` no `main` público retornava vazio antes do split. O código antigo segue no histórico público e nos forks `contatg/mcla-recomp-forkbr` e `Rydev-pc/mcla-recomp-rv-helio-g85` — o split é apresentação, não sigilo retroativo.

### Ambiente

- Build: SDK `cmake --build out/build/android-arm64 -j 2` + `cmake --install` (prefixo `out/install/android-arm64`); app `cmake --build out/build/mcla-android-arm64 --target mcla -j 2`; empacotar com `MCLA_VERSION_CODE/NAME MCLA_INCLUDE_LSFG=1 MCLA_APK_VARIANT=adreno-diagnostic MCLA_KEYSTORE_PATH=... scripts/build_android_apk.sh`.
- `gh` ativo = `gabrielbskk`; clone/push HTTPS usa `gh auth git-credential`.
- Sem ADB: instalação via `su -c pm install`. Log do jogo em `Documents/MCLA/logs/` (`mcla.log`, rotativo ~4,8 MB).
- Driver ativo: `files/gpu-drivers/selected.txt` = `libvulkan_freedreno.so` (Turnip).

<!-- Atualize esta seção quando o split ou a release mudarem. -->
