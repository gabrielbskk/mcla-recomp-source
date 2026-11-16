# v59 — regressão de frame time: diagnóstico e experimento isolado

## Estado funcional preservado

O usuário confirmou a v58 na prática: remoção de pedestres/trânsito/estacionados,
analógicos, feedback dos pedais e demais correções funcionam. Não reverter esses
itens. A investigação abaixo é de desempenho, não uma nova implementação do
cheat. Save/preferências respaldados antes da captura; nenhuma limpeza de cache,
alteração de driver/syncdraw, preset S23, clock ou proteção térmica.

## Evidência anterior ao experimento

Logs de gameplay do usuário, 13:07–13:09, v58:

- Start 13:08:10.725; lighting guard observado +216 ms; game-state raw muda
  +2.362 ms; carro válido no HUD +5.646 ms. Isso NÃO identifica exatamente
  liberação do controle nem ativação real da câmera.
- Janela recente de 512 swaps em uptime 50 s: média 63,895 ms, p95 111,588 ms,
  p99 320,845 ms, máximo 730,037 ms. Em 115 s: 73,101/123,028/314,774 ms,
  máximo 1.116,472 ms. São janelas sobrepostas, não agregáveis entre si.
- `layout_rejected=0 reenable_rejected=0` em todo esse trecho. A remoção não
  está executando destruição/varredura no loop de frames; as flags são zeradas
  na inicialização e os hooks só atuam na admissão/enable. As seis leituras de
  contagem ocorrem uma vez por 5 s. O registro inclui categoria não classificada;
  não interpretar o único slot observado como NPC visível ou prova de IA ativa.
- ART: GC-count permanece 2, GC-time 27 ms, blocking-GC-time 0 durante a queda
  contínua; bytes alocados crescem lentamente. Não há evidência de GC recorrente
  como origem desse aumento. São contadores cumulativos, não alocações por frame.
- VmSwap sobe para ~258 MB e thermal_status chega a 1. Pressão de memória e
  aquecimento são fatores concorrentes; não desativar proteção térmica.

## Perfil v58 controlado

Captura nova sem compilação simultânea: simpleperf cpu-clock 99 Hz, DWARF 8192,
69,984 s, 21.905 amostras, zero perdidas; PID 31288. Um único toque no Start em
~22 s, sem dirigir ou iniciar corrida. Mesmas configurações e cenário de carga
serão usados na comparação. Arquivos privados em `performance-v59/` fora do Git.

Distribuição de CPU amostrado por thread:

| Thread | % do CPU amostrado |
| --- | ---: |
| SDLThread | 29,37 |
| Guest render thread | 22,06 |
| Audio Worker | 16,95 |
| GPU Commands | 7,38 |
| SDLActivity | 0,47 |
| Main Android thread | 0,22 |

`__kernel_clock_gettime` na SDLThread: 15,91% self do processo. Em 22 s iniciais,
simpleperf stat registra 61,659 s de task-clock (~2,802 CPUs) e 212.498 faults.
Percentuais de CPU não são milissegundos de frame nem prova de ausência de
custo GPU de composição. A thread Android não aparece como gargalo de CPU nesse
perfil; não foi instrumentado overdraw GPU por widget.

Segundo hotspot identificado, sem alteração nesta candidata:
`MMIOHandler::ExceptionCallback → QueryProtect → FindEntryForAddress →
/proc/self/maps` (getline/read/parse) no caminho de faults. Requer otimização
separada que mantenha a correção das permissões e das corridas de write-watch;
não remover verificações ou cachear permissões obsoletas para ganhar FPS.

## Causa concreta corrigida no experimento

Arquivo: SDK SDL `thirdparty/sdl3/src/events/SDL_events.c`.

1. `SDL_PumpEventsInternal(true)` cria `SDL_EVENT_POLL_SENTINEL` como marcador
   interno de fim do ciclo de polling.
2. `SDL_PeepEventsInternal(... SDL_ADDEVENT ...)` chama `SDL_SendWakeupEvent`
   para qualquer evento adicionado, inclusive o marcador.
3. No Android, esse wakeup posta `SDL_ANDROID_LIFECYCLE_WAKE` no semáforo.
4. O consumidor ignora/remove o sentinel, consome o wakeup que ele próprio
   produziu e repete o pump. Produz outro sentinel/wakeup, sem entrada real.

Isso explica o busy-loop e o custo de clock/atomics/event pumping. Não é polling
de NPCs ou reconstrução do layout touch. O binário SDL era idêntico entre v57 e
v58: este bug já existia, portanto não há prova de que tenha sido introduzido
pelo cheat. Pode competir por CPU/energia e agravar a regressão percebida, mas
não deve ser apresentado como explicação única de todo hitch de carregamento.

## Alteração aplicada

- Não acordar o consumidor Android por lotes contendo somente poll sentinels.
- Eventos reais aceitos, inclusive lotes mistos, continuam acordando
  imediatamente. Eventos descartados não geram wakeup adicional.
- Retomar espera com o intervalo padrão SDL de polling de dispositivos (1 ms),
  para que HID/sensores que dependem de polling não fiquem sem atualização.
  Timeout explícito usa o menor prazo restante. Não é limitador de FPS.
- Wakeups de callbacks na main thread, quit e lifecycle não foram suprimidos.
- Outros sistemas operacionais mantêm o envio anterior de wakeup.
- Não modificar código de NPC, limpeza, culling/LOD/batches/instancing, câmera,
  qualidade gráfica, áudio, geometria ou bindings do touch.

Escopo binário isolado: reconstruído somente `SDL_events.c.o` e relink de SDL.
Conjunto de exports dinâmicos idêntico. DEX e todas as outras bibliotecas,
inclusive `libmain.so`, são idênticos à v58.

## Arquivos e testes

- SDK: `thirdparty/sdl3/src/events/SDL_events.c`.
- Patch reproduzível no repo: `patches/rexglue-sdl-android-sentinel-wake.patch`
  (aplicar na raiz do submódulo SDL, sobre o caminho Android já existente).
- `scripts/ci/test_android_sdl_wake.py`: compila a condição C real extraída do
  código e verifica sentinel-only, real, mixed, dropped e modelo de self-wake;
  verifica preservação do polling limitado. É teste de lógica/contrato, não
  substitui gamepad/HID/foco/reconexão no aparelho.

## Comparação e critérios de aceite

Registrar abaixo apenas resultados obtidos. Não converter percentuais de CPU
em frame time, não agregar janelas recentes sobrepostas, não confundir carro
válido no HUD com primeiro frame controlável. A/B de carregamento frio é
limitado por caches de SO e shader storage já aquecidos; não apagá-los.

Ainda não medidos diretamente: draw calls/batches por frame e por material,
alocações nativas/guest por frame, custo isolado da câmera/colisão/IA e composição
GPU da UI. Sem evidência de regressão nesses subsistemas, não aplicar pooling,
rebatching, mudanças de FOV/interpolação ou threading guest especulativos.

### Resultados obtidos

Segunda captura: PID 9335, 69,9902 s, 14.995 amostras, zero perdidas, mesmos
99 Hz e unwind. Sem compilação simultânea; uma única injeção programada de Start.

| Métrica | v58 | v59 |
| --- | ---: | ---: |
| CPU-clock total amostrado em ~70 s | 221,26 s de CPU | 151,46 s de CPU |
| SDLThread, fração do CPU amostrado | 29,37% | 1,07% |
| CPU SDL estimado (fração × CPU-clock) | ~64,98 s | ~1,62 s |
| Task-clock dos primeiros 22 s | 61,659 s | 32,080 s |
| CPUs médias no stat inicial | 2,802 | 1,458 |
| Frequência média no stat inicial | 2,352 GHz | 2,282 GHz |
| Últimos 512 swaps em uptime ~70 s: média | 79,309 ms | 41,125 ms |
| Mesma seleção: p95 | 142,174 ms | 54,530 ms |
| Mesma seleção: p99 | 248,805 ms | 61,281 ms |
| Mesma seleção: máximo | 853,670 ms | 74,994 ms |
| Primeiro Start → carro válido no HUD (proxy) | 4.345,792 ms | 3.681,815 ms |
| Borda Start → lighting guard (proxy) | 113 ms | 70 ms |
| Borda Start → game-state raw 3 (proxy) | 1.223 ms | 789 ms |
| Activity launch COLD (`am start -W`, não gameplay) | 244 ms | 447 ms |
| Cópia do plugin nessa abertura | reuse, 1 ms | copied, 19 ms |
| ART GC acumulado até heartbeat ~65 s | 1 / 6 ms | 1 / 6 ms |
| ART blocking GC acumulado | 0 ms | 0 ms |
| Bytes Java alocados observados, inicial → ~65 s | +2.646.128 | +2.920.560 |

CPU SDL reduzido aproximadamente 97,5%; CPU total amostrado cai ~31,5%.
Frame time na seleção observada cai ~48,1% na média, ~61,6% no p95 e ~75,4%
no p99. Não são resultados de todos os frames da execução nem de medição GPU.
O tempo de Activity piorou nessa abertura: não omitir essa métrica; houve
inicialização após atualização/cópia, em condições de cache diferentes. O ganho
não foi produzido aumentando clocks ou reduzindo gráficos.

**Limitação importante:** a v58 registrou dois Starts adicionais antes de 70 s
(3 bordas totais), embora o script tenha injetado apenas um; a v59 registrou 1.
Ambas chegaram a thermal_status=1; a memória disponível no heartbeat final era
966 MB contra 1.140 MB, VmSwap 251,6 MB contra 151,1 MB. Cena, pausa, caches e
pressão de memória não estão perfeitamente pareados. O bug de self-wake e a
redução de CPU SDL têm evidência direta; não atribuir exclusivamente ao patch
toda a redução de percentis observada. Repetir várias rodadas controladas para
aceite quantitativo estrito contra versões históricas, cujos p99 não existem.

**Picos restantes:** durante a transição da v59 ainda aparece swap de
611,906 ms e update de 554,982 ms. O critério de zero stutter no carregamento/
câmera **não foi atendido**. Não foi adicionada interpolação para esconder um
stall; nem movido código guest para uma thread sem rastrear suas dependências.
O caminho de faults/consulta de permissões é o próximo gargalo a isolar.

**Preservação funcional:** DLL de jogo, runtime, GPU e DEX byte a byte idênticos
à v58, exceto SDL. Start do touch e chegada ao HUD observados na v59; contador
de re-enable/admissão permanece zero no cenário, sem trabalho de remoção por
frame. Não se executou corrida/IA de adversários neste teste. Gamepad/HID,
mouse/teclado, foco e reconexão precisam de teste físico após a mudança de espera,
apesar de o polling padrão e os wakeups reais terem sido mantidos.

## Revisão da UI e população

- Launcher cria seus cards ao iniciar a Activity; não recria widgets durante
  gameplay. A hierarquia não é reconstruída pelo loop nativo.
- Barras/sticks já possuem dirty-check de pressão/eixo e invalidação por vsync.
  Coordenadas de dedos são reutilizadas; não há timer contínuo de redraw.
- O desenho da View pode executar comandos para vários controles quando
  invalidada; dirty rect não garante composição parcial em todo Android. Seu
  custo GPU isolado ainda não foi medido, portanto não o declarar literalmente
  zero. DEX intocado evita reintroduzir a regressão de feedback estático.
- NPCs são bloqueados antes da criação. Setters mantêm o descarte e a
  sincronização nativos por evento. Nenhuma varredura ou destruição em massa
  foi acrescentada ao update da candidata. Contadores/perfil não apontam esse
  caminho como gargalo desta captura.
- Não houve alteração de batching/culling/LOD/instancing. Faltam contadores
  por material/frame para aceitar ou rejeitar uma hipótese específica de
  batching, mas não há evidência que justifique reorganizar draws agora.

## Reprodução, entrega e aceite

`scripts/compare_android_frame_windows.py BEFORE AFTER --uptime-ms 70000`
seleciona uma única janela recente por sessão e avisa sobre limitações. Resultado
privado: `performance-v59/comparison.json`. Não agrega janelas sobrepostas.
Logs/perfis antes/depois e backup ficam nessa mesma pasta privada, fora do Git.

Testes: `test_android_sdl_wake.py`, build incremental SDL (um objeto), ABI dinâmica,
7.344 verificações de layout, 739 de joystick, política/concorrência de estatísticas,
idioma e contratos nativos passaram. Certificado, versão, alinhamento, DEX e
comparação de libs conferidos. Não aplicar em bloco outras alterações locais
do SDK: esta candidata contém somente o delta SDL documentado.

Instalada como atualização: `0.1.59-android-event-wake-test`, code 59. A execução
v58 iniciada para o perfil foi encerrada antes da atualização, sem limpar dados.
APK: `Download/MCLA-NORMAL-v0.1.59.apk`.
SHA-256: `31caf56cfe2766c24823ce931abaecc59258c3567553da05d6fd6bafed8e09d2`.
Versões anteriores, assinatura, saves, configurações e caches preservados. Sem
commit/push de fonte nesta etapa, APK no GitHub ou release.

| Critério | Estado |
| --- | --- |
| Eliminar busy-loop SDL sem entrada real | Corrigido em código; CPU SDL caiu fortemente no perfil |
| Média/p95/p99 iguais ou melhores | Melhores na seleção observada; A/B rigoroso pendente pelas diferenças de entrada/memória/cache |
| Nenhum stutter em Start/câmera | Não atendido; ainda há pico ~612 ms na transição |
| Cheat sem limpeza recorrente | Revisão e contadores não mostram execução recorrente de limpeza; não prova custo absoluto zero |
| UI sem custo relevante | CPU Android baixo neste perfil; custo GPU por widget não isolado |
| Regressões funcionais cruzadas | Código/Dex preservados; Start/HUD smoke test passou; periféricos/corridas pendentes |
