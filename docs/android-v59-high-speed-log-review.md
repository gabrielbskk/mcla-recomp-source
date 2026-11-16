# v59 — queda em alta velocidade: revisão dos logs

## Retorno do usuário

Após a correção SDL, o desempenho melhorou consideravelmente. Queda restante
relatada em alta velocidade. Não alterar/reverter o fix SDL, cheat ou controles.

## Sessão analisada

Sessão de 2026-11-14 13:23:46 até aproximadamente 13:41:42, código instalado 59.
Processo já encerrado na consulta; não foi reaberto nem dirigido automaticamente.
Cópias privadas de `mcla.log`, `mcla_a730.log` e recortes da última sessão em
`high-speed-v60/`, pasta de diagnóstico fora do repositório. O nome da pasta não
representa APK v60: nenhuma candidata nova foi compilada nesta análise.

| Trecho | Média dos intervalos nas janelas recentes | p95 das janelas | Pior máximo observado nas janelas |
| --- | --- | --- | --- |
| 13:36 | 42,112–49,175 ms | 54,415–73,466 ms | 129,804 ms |
| 13:38 | 35,684–47,276 ms | 44,976–64,797 ms | 515,454 ms |
| 13:40 | 43,165–50,727 ms | 56,993–76,373 ms | 784,483 ms |
| 13:41 | 45,335–55,876 ms | 67,816–90,875 ms | 741,689 ms |

São faixas de métricas de janelas sobrepostas de até 512 swaps, não percentis
agregados por minuto nem tempos GPU. Não há `telemetry: speed` nessa sessão:
**os logs não identificam quais frames correspondem à alta velocidade**.

## Evidências e hipóteses

1. **Novos materiais/pipelines durante deslocamento:** existem 13 registros de
   criação de pipeline em 13:38 (vários às 13:38:57), 3 em 13:40 e 6 em 13:41.
   São registros de pipelines, não número de shaders únicos. Há picos nos
   mesmos períodos amplos, mas isso não mede duração de compilação nem prova
   que cada pico foi causado por ela. Em 13:36 não há criação registrada e a
   média ainda fica acima de 42 ms: compilação não explica todo custo contínuo.
2. **Calor:** os heartbeats do trecho 13:34–13:41 registram `thermal_status=2`
   (MODERATE). Antes dos testes iniciais estava em 0/1. É um sinal térmico
   relevante, mas não há série de clocks nessa captura para quantificar
   throttling ou atribuir a ele sozinho a queda. Não desativar proteção térmica.
3. **Memória:** fim da sessão com RSS ~1,1 GB, VmSwap ~263 MB, memória disponível
   ~1,2–1,3 GB, `system_low_memory=false`. Pode tornar streaming mais caro;
   contagem de bytes em swap não mede page-ins por frame nem prova causalidade.
4. **Não acompanha GC:** ART mantém GC-count=1, GC-time=6 ms e blocking-GC=0
   no trecho final. Não há novas pausas de GC correlacionadas à queda contínua.
5. **Sem loop de limpeza do cheat observado:** `layout_rejected=15` e
   `reenable_rejected=2` permanecem constantes por vários minutos. Não há
   crescimento de rejeições/descarte junto a cada frame lento. O registro bruto
   oscila entre categorias `[0,13,0]` e `[0,16,0]`; sem classificar os slots não
   é correto chamá-los de NPCs reaparecidos nem declarar qual seu custo.
6. **Outro gargalo já perfilado, ainda não isolado neste trajeto:** consultas a
   `/proc/self/maps` no tratamento de write-watch/MMIO foram caras no perfil
   anterior. Alta velocidade pode aumentar streaming e faltas de páginas, mas
   falta um perfil durante a aceleração para confirmar esse vínculo. Não
   remover validação de permissões nem inventar cache obsoleto como fix.

Não há novos erros de device-loss/OOM nos logs consultados. Avisos de assets de
teste ausentes são do boot; não aparecem repetindo durante o trecho final.

## Próxima medição necessária

- Trajeto repetível parado/velocidade baixa/alta, registrando velocidade junto
  aos intervalos de frame; classificar corrida/free roam e mudanças de câmera.
- Simpleperf durante esse trajeto, com clocks/temperatura, faults/page-ins e
  tempos do caminho de streaming/compilação, para separar CPU, GPU e I/O.
- Repetir o mesmo trajeto quente para distinguir primeira visita/material novo
  de custo sustentado de visibilidade, física e atualização da cidade.

Não foi aplicado preset S23, redução de LOD/distância, cap de FPS, alteração de
syncdraw, limpeza de cache ou mudança especulativa no streaming. Nenhum APK novo
foi gerado. A conclusão atual é hipótese de custo de streaming/novos materiais
somado ao estado térmico/memória, **não uma causa raiz já comprovada e corrigida**.

## Preparação offline da próxima instrumentação

Alterações ainda somente em fontes; v59 instalada permanece intacta. Nenhum
APK foi aberto, compilado ou instalado nesta etapa.

- `src/frame_sample_window.h`: `SnapshotSince(cursor)` permite consumir
  sequências disjuntas; informa `dropped` por capacidade excedida e
  `unavailable` por publicação ausente/sobrescrita concorrente. Cursor além do
  contador retorna vazio com `cursor_reset`, sem inventar amostras. O caminho
  produtor permanece com duas operações atômicas e sem I/O/alocação adicional.
- Snapshots agora usam array local de 512 valores (2 KiB de armazenamento de
  amostras), em vez de vector. Mantido `Snapshot()` recente para comparação
  com logs anteriores. Isso não significa que todo o watchdog seja sem heap:
  o histórico de apresentação e a infraestrutura de logging são separados.
- `src/android_diagnostics.cpp`: preparada linha `frame-interval` no worker
  a cada aproximadamente 5 s, com média/p95/p99/máximo, perdas, timestamp
  monotônico e leitura dos atomics existentes de velocidade/HUD. Não há nova
  leitura de memória guest ou logging no caminho por frame desta alteração.

### TDD e verificação efetivamente executada

1. `scripts/ci/test_frame_sample_delta.cpp`: RED observado por ausência de
   `SnapshotSince`; GREEN após implementar cursor, disjunção, overflow,
   saturação, reset e leitura concorrente.
2. `scripts/ci/test_frame_sample_noalloc.cpp`: RED observado com três
   alocações nos snapshots recente/disjunto/vazio. Após substituir vector por
   array, recompilação e execução passaram, com zero chamadas ao `operator new`
   monitorado nesses três snapshots. Não é medição global de alocações/GC.
3. `scripts/ci/test_frame_sample_window.cpp`: regressão de estatísticas,
   sobrescrita limitada e snapshots concorrentes passou novamente.
4. Os três testes foram compilados com C++17, `-Wall -Wextra -Werror` e pthread,
   com prioridade reduzida. `git diff --check` passou nos dois fontes alterados.
5. LLVM source coverage do teste delta sobre **somente o helper** registrou
   43/43 linhas, 21/21 regiões, 4/4 funções e 12/12 ramos (100%). Não representa
   cobertura do aplicativo, integração Android ou exaustividade de concorrência.

Resultados privados em `high-speed-v60/`: `delta-red.txt`, `noalloc-red.txt`,
`test_frame_sample_*-green.txt` e `helper-coverage.txt`.

### Limites e critérios pendentes

- A unidade Android modificada ainda não teve compilação/syntax-check com as
  dependências reais. Formato e argumentos da nova linha foram revisados
  manualmente; isso não substitui a checagem do compilador/logger real.
- Timestamp e `window_ms` são aproximações do ciclo do worker, não barreiras
  simultâneas dos buffers. A primeira janela inclui amostras anteriores ao
  início do worker, se houver; reiniciar o worker não limpa os buffers.
- `speed_raw`, em `guest_velocity_raw`, é observação próxima ao fim da janela;
  não é km/h confirmado nem velocidade de cada frame. Atomics de velocidade,
  contador HUD e congelamento são lidos separadamente, sem snapshot coerente.
  `speed_observed`/`hud_delta` não provam controle ou física ativos.
- Intervalos são classificados pela sequência de registro, não divididos na
  fronteira temporal exata; perdas devem acompanhar qualquer análise. Não
  agregar p95/p99 de janelas como percentis globais. Buffer mantém até 512
  intervalos; o marcador de geração usa 32 bits e não oferece contrato para
  leitores suspensos por uma volta completa de 2^32 publicações.
- A concorrência foi exercitada, não provada por sanitizador ou exploração
  exaustiva. Publicação ainda pendente pode ser contada como `unavailable` e
  não será reprocessada depois do avanço do cursor: perda é explícita.
- Antes de entrega: compilar a unidade real, validar emissão e perdas em
  aparelho em horário autorizado e comparar overhead. Ainda faltam duração
  de streaming/compilação, tempo GPU e faults/page-ins para demonstrar causa.

Nenhum ganho de FPS é reivindicado por essa instrumentação. SDL v59, syncdraw,
shaderpack desativado, controles e correções de população permanecem preservados.

## Candidata 60 — preparação após autorização para testar

- Compilação real de `android_diagnostics.cpp` e link de `libmain.so`
  concluídos com sucesso, usando comandos do Ninja existente e os demais
  objetos da v59. Sem regenerar/recompilar código guest e sem rebuild do SDK.
  Isso valida sintaxe/tipos/formato de logging desta unidade, não um clean build.
- Antes do link, o `libmain.so` local era idêntico ao contido no APK59 de
  SHA256 `31caf56cfe2766c24823ce931abaecc59258c3567553da05d6fd6bafed8e09d2`.
  As outras nove bibliotecas de empacotamento também foram comparadas e
  coincidiram com esse APK. APK59 e objeto/biblioteca nativa anteriores foram
  preservados no backup privado `high-speed-v60/v59-native-backup/`.
- Três testes focados reexecutados: PASS. Sem instalação ou abertura automática.
- APK60 empacotado e validado: versão `0.1.60-frame-speed-diagnostic`, code60,
  SHA256 `7cc01456ffbf298b303796a264142496c1fa333b09cd3936cb4b548e95b814f2`.
  Assinatura igual à v59 e zipalign de 16 KiB passaram. Comparação de conteúdo:
  somente `AndroidManifest.xml` e `libmain.so` mudaram, além dos metadados de
  assinatura em `META-INF/`. DEX e as demais bibliotecas permaneceram idênticos.
  Disponível em `Download/MCLA-NORMAL-v0.1.60.apk`; APK59 também preservado em
  Downloads. Nenhuma publicação GitHub, instalação ou abertura automática.

### Roteiro de teste manual (depois de instalar a candidata validada)

1. Manter configurações, câmera, caches e modo de jogo; deixar o aparelho
   recuperar naturalmente do aquecimento da compilação antes da captura.
2. No mundo jogável, ficar parado por cerca de 20 s.
3. Dirigir devagar por cerca de 30 s e acelerar por cerca de 30 s em uma rota
   reproduzível. Anotar aproximadamente quando ocorrerem os engasgos.
4. Repetir a mesma rota uma vez sem apagar caches, mantendo a câmera.
5. Exportar os logs pelo launcher e informar os trechos lento/rápido e modo.

Verificar nos logs a presença de `frame-interval`, as perdas e `hud_delta`.
Comparar janelas, não atribuir todos os frames à velocidade observada no fim.
Nesta sessão o ADB não tem dispositivo conectado e não há `su` funcional:
instalação manual/exportação podem ser necessárias. Não presumir que existe
captura simpleperf, acesso a dados privados ou telemetria de GPU/faults.
