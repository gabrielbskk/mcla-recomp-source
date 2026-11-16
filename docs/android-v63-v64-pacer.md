# v63/v64 — pacer do LSFG: diagnóstico da reconexão e correção

Instantâneo em 2026-11-16 (horário local, UTC-3). Este documento registra o que a
v63 provou, qual é a causa sustentada pelas evidências e o que a v64 muda. Ele
não reescreve conclusões anteriores: a v61 continua sendo a versão de uso até a
v64 ser validada em jogo.

## Escopo

A v63 (`0.1.63-lsfg-pacer-shared`) habilitou o pacer do framegen na fila
gráfica compartilhada, porque o Turnip do Adreno 740 expõe uma única fila. O
framegen inicializou o contexto LSFG, mas a conexão do swapchain passou a ser
recriada em laço, derrubando o desempenho. A v64 corrige o laço com evidência e
instrumentação, e reduz o risco de bloquear a fila compartilhada.

## v63: o que foi observado

Sessão `2026-11-16 00:35:52` a `00:38`, log `mcla.log` do aparelho:

| Evidência | Valor |
| --- | --- |
| Pacer | `frame pacer on queue family 0 index 0`, fila gráfica compartilhada |
| Contexto LSFG | `LSFG: SDR 2x context ready 1280x720; three cold frames suppressed` |
| Present mode | FIFO (`presentation mode 2`), porque a surface não oferece IMMEDIATE |
| Swapchains criados | 33, 279, 167 e 32 por minuto (00:35 a 00:38), contra 1 por sessão na v61 |
| Drops do pacer | no máximo 1 por conexão, sempre `reconnect` (frames ainda na fila no momento da desconexão) |
| Paint lento | `frame-interval` com framegen ~75–79 ms, p95 até 227 ms e máximo 262 ms |
| Limiter | alvo 30 FPS reais (metade do refresh) enquanto o framegen estava ligado |

Nenhuma linha `Presentation to the swapchain image has been dropped as the
swapchain or the surface has become outdated` aparece no intervalo do laço, e
nenhum drop de `acquire` é registrado antes do `reconnect`. Ou seja: o laço não
vinha do acquire nem do paint da thread de UI; vinha do present do pacer.

## Causa sustentada

1. `FramePacerPresentImage` (`src/ui/vulkan/vulkan_presenter_frame_generation.cpp`)
   adquire a imagem e apresenta. Se `vkQueuePresentKHR` devolve erro que não seja
   `VK_SUCCESS`, `VK_SUBOPTIMAL_KHR` ou `VK_ERROR_DEVICE_LOST`, o `default` do
   switch marca `kFramePacerStatusOutdated` **sem registrar o `VkResult`** e sem
   contar drop. O erro fica invisível no log.
2. No paint seguinte da thread de saída do guest, `WaitForFramePacerSlot` vê
   `kFramePacerStatusOutdated` e devolve `kNotPresentedConnectionOutdated`. O
   `Presenter` marca a conexão como desatualizada; a thread de UI destrói o pacer
   (`PauseFramePacerFromUIThread` registra `reconnect` para os frames que restam)
   e recria o swapchain. O ciclo recomeça no mesmo frame.
3. Agravante de projeto: o pacer compartilhava a fila gráfica com present mode
   FIFO. Um `vkQueuePresentKHR` FIFO bloqueia dentro da chamada segurando a fila,
   atrasando as submissões do próprio jogo por até um refresh. A v61 não usava
   pacer; usava MAILBOX.

O `VkResult` exato do present ainda não foi medido — é o que a v64 passa a
registrar. Por isso a correção de modo (MAILBOX) é justificada por si, e a causa
exata fica registrada para a próxima sessão.

## v64: mudanças

### `src/ui/vulkan/vulkan_presenter_frame_generation.cpp`

- `VkResultName`: nome legível para os resultados usados no log.
- Log dos 6 primeiros presents de cada conexão: índice do present, conexão,
  `acquire`, `submit`, `present` (número e nome), imagem do swapchain e se é o
  frame real ou um gerado.
- `default` do switch do present agora registra `WARN` com o resultado; a
  conexão continua sendo recriada, como exige a spec depois de um present que
  falhou.
- O paint que encontra a conexão `outdated` registra uma vez por conexão o frame
  e o status, para casar com o log do present.
- Watchdog: 4 paints que encontram a conexão `outdated` em 3 s chamam
  `SetFrameGenerationUnavailable("its connection kept going outdated")`. A
  conexão seguinte é criada sem pacer; `CapFromSettings` volta ao cap do jogador
  (`mcla_fps`) em até 250 ms. Os switches e o menu não são alterados: a próxima
  sessão tenta de novo.

### `src/ui/vulkan/vulkan_presenter.cpp`

- Escolha do present mode com pacer:
  1. `tearing` + IMMEDIATE disponível → `IMMEDIATE`;
  2. pacer na fila gráfica + `auto` + MAILBOX disponível → `MAILBOX` (não
     bloqueia a fila; a imagem mais nova é a próxima mostrada);
  3. senão → `FIFO`, com o aviso já existente.
- O paint registra quando o estado de framegen da conexão muda (`wanted` ↔
  `not wanted`), que é uma das causas possíveis de reconexão.

### `src/ui/presenter.cpp`

- Descrição do cvar `frame_generation_present` atualizada para o caso de fila
  compartilhada (MAILBOX antes de FIFO no `auto`).

Não alterado: SDL, syncdraw, instrumentação de frame-interval, exportação
automática de logs, menu in-game, launcher, shaderpack desativado, controles,
DLL do LSFG e o adaptador same-device/record-only.

## Artefato

| Campo | Valor |
| --- | --- |
| Versão | `0.1.64-lsfg-pacer-mailbox` (code 64) |
| Tamanho | 113.329.530 bytes |
| SHA-256 | `c6bea39228976be833dc1f42fb3c70d1f78edc574d6887e8e5614a2ad96c55fa` |
| Assinatura | `CN=MCLA Recomp Debug`, SHA-256 `bcb81f4c885db48c42bfaddba68b3c5b2b86cbe305f44c0ff63c79078f341e9c` |
| Alinhamento | `zipalign -P 16 -c 4` passa; ELF com LOAD >= 16 KiB |
| Cópia local | `Download/MCLA-v0.1.64-lsfg-pacer-mailbox.apk` |
| Instalada | sim, sobre a v61 com `-d -r`; `files/lsfg/Lossless.dll` e `shared_prefs/frame-generation.xml` (`lsfg_2x=true`) preservados |
| Netbook | não enviada (aguarda validação em jogo) |

A v63 continua preservada em `Download/MCLA-v0.1.63-lsfg-pacer-shared.apk` e a
v61 em `Download/MCLA-NORMAL-v0.1.61.apk`.

## Gates

Verificado nesta sessão: compilação do SDK e do app, empacotamento, assinatura,
zipalign, presença das novas strings no `librexruntime.so` empacotado,
instalação code 64 e preservação dos dados.

**Não verificado**: comportamento em jogo. O usuário optou por deixar a v64
instalada e testar depois; nenhum jogo foi aberto nesta sessão.

## Roteiro do próximo teste

1. Abrir o MCLA com framegen ligado e jogar de 1 a 2 minutos (incluindo alta
   velocidade).
2. Filtrar no log: `frame pacer: present`, `frame generation's pacer shares`,
   `kept going outdated`, `frame-interval`, `frame pacer: frame`.
3. Sucesso: os presents retornam `success`; a conexão é uma só (sem
   `reconnect` em série); `frame-interval` com framegen não passa de ~2× o
   intervalo da v61 no mesmo trecho; o watchdog não dispara.
4. Se o watchdog disparar, o `WARN` mostra qual `VkResult` o present devolveu —
   aí a correção é dirigida a esse resultado, sem adivinhar.
5. Comparar com a v61 no mesmo trecho antes de enviar ao netbook.

## Riscos e limites

- MAILBOX pode substituir o frame gerado pelo real se o pacer atrasar; o pacing
  por intervalo (metade do intervalo real) existe para evitá-lo. Em troca, o
  present não bloqueia a fila gráfica compartilhada.
- O watchdog é por sessão: se disparar, o framegen fica desligado até reiniciar
  o jogo (o cap do jogador volta sozinho).
- 3×/4× continua não implementado (`generationCount=1` fixo no adaptador).
- Sem medição de latência de input, tempo de GPU puro, memória/térmica e
  qualidade em cenas reais.
