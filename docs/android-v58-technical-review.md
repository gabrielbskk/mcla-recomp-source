# v58: revisão técnica e critérios de aceite

**Validação posterior pelo usuário:** remoção de pedestres/trânsito/estacionados
e demais correções funcionais confirmadas na prática. Os estados pendentes da
matriz ao final registram a situação na entrega original, não invalidam esse
retorno. A regressão de desempenho e o teste isolado da v59 estão em
[android-v59-performance-regression.md](android-v59-performance-regression.md).

Estado: trabalho local/candidata; não interpretar build ou injeção de opção como
validação de gameplay. Versão normal, pacote/assinatura preservados. Nenhuma
limpeza de saves/caches, mudança em syncdraw ou APK enviado ao GitHub.

## 1. Remoção de população

**Hipótese:** v57 alterava parâmetros de densidade e switches de tráfego, mas não
todos os canais nativos de admissão/enable. NPCs de layout e reativação por script
continuavam possíveis. Dois personagens observados pelo usuário ainda não foram
classificados; podem pertencer a outro sistema de atores, não ao AmbientMgr.

**Evidência:** logs v57, 04:49:31.567: `noautomatictraffic`, `notriggeredtraffic`
e `noparkedcars` registrados antes de carregar a cidade. Às 04:49:41.534 há
`ambient density applied to 31 zones`. Isso prova os pedidos, NÃO remoção de
entidades. Relato do usuário confirma pedestres e estacionados remanescentes.

**Revisão nativa:**

- XEX correspondente contém bools inicialmente `01 01 01` em
  `0x8282A7C8..CA`, habilitando tráfego/pedestres/estacionados.
- APIs `sub_826D5D90`, `sub_826D5E20`, `sub_826D5EB0` atualizam esses bools;
  com false também chamam descarte `sub_826D56F0` ou agendamento de limpeza
  `sub_826D9530` quando o worker está ativo. Não chamar essas funções de thread
  host com contexto/stack inventado.
- `sub_826E1418` é admissão de pedido de layout: aloca um slot, enfileira o
  pedido e devolve ID. Seu caminho original de falha devolve -1. Bloqueá-lo
  antes do prólogo impede criar/enfileirar esse pedido, não apenas desenhá-lo.
- Registro `manager+12`: três grupos de 52 bytes; +20/+44 são contagens ativas,
  +16/+40 são livres. Confirmado pelas atualizações em `sub_826D6390`.

**Alteração:** inicializar os três enables a zero no primeiro registro nativo,
antes da cena; interceptar as três APIs de enable para impedir reativação quando
a opção está ligada, mantendo seu código original de descarte/sincronização;
recusar admissão de layout com -1, antes da alocação. Quando OFF, hooks deixam
o caminho original intacto. Sem varrer ponteiros/destruir entidades no render,
sem mexer nas fábricas de jogador/adversários, sem esconder apenas renderização.

**Instrumentação:** contadores de pedidos rejeitados/reenables bloqueados e
contagens nativas por categoria a cada 5 s no update guest; sem caminhar listas.
Ponteiro/contagem inválidos produzem `unknown`, nunca zero fabricado. Categorias
ainda não são mapeadas para classes semânticas; a amostra não é uma fotografia
atômica de todos os sistemas de atores. Não contá-la como prova de todo o mundo.

**Arquivos:** `config/ambient_population.toml`, `mcla_manifest.toml`,
`src/perf_options.cpp`.

**Impacto esperado:** evitar simulação/colisão/IA/renderização de objetos que
deixarem de ser instanciados nesses caminhos. Impacto medido: pendente.

**Aceite:** NÃO validado. Falta classificar os dois pedestres, confirmar quais
sistemas ainda têm entidades, verificar zero por classe após carregar e após
respawn/transições, verificar ausência em simulação/colisão e testar adversários
em corrida. Não há limpeza genérica segura de atores de missão já existentes.
Opção continua sendo aplicada antes do início, não toggle em cena.

## 2. Indicadores dos pedais

**Causa confirmada em código:** v57 usava
`editing || !animateFeedback ? 0 : triggerValue(control)` ao desenhar e impedia
redesenho com feedback desligado. O estado JNI continuava analógico; a regressão
era no binding visual introduzido pela política de HUD estático.

**Evidência:** evento v57 `touch HUD=static` e relato da barra ausente; expressão
acima força zero nessa configuração. Logs anteriores não continham amostras de
pressão, portanto não medem latência.

**Correção:** desenho usa os mesmos valores LT/RT de 0..255 enviados ao JNI,
independentemente do feedback dos sticks. Mudança de pressão, inclusive soltar
ou cancelar, solicita redraw agrupado por vsync; caixa dos pedais invalidada
quando aplicável. Sem polling contínuo/timer de animação, sem mexer na SDL.

**Arquivos:** `android/MclaTouchControls.java`, texto em
`android/MclaLauncherActivity.java`.

**Impacto esperado:** barra e percentual corretos no próximo frame Android,
sem atraso artificial. Redesenhos necessários dos pedais retornam; dirty rect
não é garantia de composição parcial em todo backend Android.

**Aceite:** binding corrigido em código; pressão 0/50/100%, dois dedos,
release/cancel e latência real precisam ser conferidos no aparelho.

## 3. Launcher em inglês

### Solicitação adicional: analógicos refeitos

**Causa confirmada em código:** v57 calculava o eixo a partir de
`stickOrigins` (ponto inicial do dedo), mas desenhava a base nas coordenadas do
layout. Com HUD estático não havia indicação de deslocamento; com animação,
curso visual de 0,82r e escala de entrada de 1,12r divergiam. Mais de um dedo
podia possuir o mesmo stick e a iteração do HashMap decidir qual eixo prevalecia.

**Correção:** nova política pura `MclaTouchStick`, base fixa, zona morta radial
de 8%, magnitude linear e clamp circular. Um proprietário por stick até soltar
ou cancelar. Desenho usa exatamente os eixos quantizados enviados ao JNI,
com curso de 0,8r; centro do cursor + raio de 0,48r respeita a base de 1,28r.
Redesenho dos sticks sempre funcional, agrupado por vsync apenas quando eixo ou
estado ativo muda. Sem smoothing que introduza atraso, timer de animação ou
realocação da base durante o gesto. Removido switch de HUD estático/animação;
preferência histórica mantida no disco, sem efeito na nova política.

**Arquivos:** `android/MclaTouchStick.java`, `android/MclaTouchControls.java`,
`android/MclaLauncherActivity.java`, `android/MclaActivity.java`,
`scripts/build_android_apk.sh`, `scripts/ci/android-input/TouchStickTest.java`.

**Evidência/medição:** relato do usuário e inconsistência identificada em código;
739 testes de ownership, cardinais/diagonais, clamp, zona morta, cancel/release
e quantização passam. Latência física, sensação de direção/câmera e eventual
flicker ao voltar o feedback ainda exigem teste Android. Não há alteração no
driver de gamepad/MnK.

### Idioma

**Causa:** textos portugueses hardcoded introduzidos na reorganização v57.
**Evidência:** 19 ocorrências encontradas em `MclaLauncherActivity.java`.

**Correção:** categorias, botões, estados e descrições em inglês; expansão
inicial de cards passa a boolean explícito, não depende de comparar traduções.
Demais mensagens de erro/carregamento e controles auxiliares foram inventariados.
Preferências e chaves persistidas não são traduzidas nem migradas.

**Arquivos:** `android/MclaLauncherActivity.java`, testes de contrato.
**Impacto:** UI, não motor do jogo; medição de FPS não se aplica.
**Aceite:** verificar scan de strings da aplicação e hierarquia do launcher;
nomes de arquivos/driver informados pelo usuário não são textos traduzíveis.
Não foi implementado sistema multilíngue: inglês é a única língua da UI própria.

## 4. Stutter de carregamento e câmera

**Hipóteses:** custo de CPU no guest/streaming, sincronização de render, criação
de materiais novos ou ativação simultânea de sistemas. Não há evidência para
atribuir todo o pico a GC ou a uma alocação específica da câmera.

**Evidência prévia:** v57 restaura 1.063 pipelines às 04:49:31.295; após a
transição os updates caem para 55–65 por janela de 5 s (~11–13/s). Uma amostra
registra 359 ms sem update, mas logs de 5 s não fornecem p95/p99, timestamp de
Start, spawn ou liberação do controle. Não inventar esses valores.

**Instrumentação adicionada:**

- Timestamp monotônico da borda Start do pad guest 0 (touch/gamepad/MnK passam
  por esse pad), sem mudar mapeamento ou latch de entrada.
- Alterações dos words nativos de game-state/city-pending/lighting, rotulados
  como observações RAW, sem supor semântica de estado.
- Primeiro carro com estruturas válidas no hook do HUD: observação, NÃO
  timestamp exato de spawn nem primeiro frame controlável.
- Pedido script `Game_SetCamera` em `sub_822C9E18`, limitado a 1 log/s e
  contador total. Não equivale a toda ativação de câmera de perseguição.
- Janelas recentes de até 512 intervalos guest de update/swap: média,
  p95/p99 nearest-rank, máximo e n. Buffer fixo, publicação atômica, sem
  alocação/I/O no registro por frame. Sort/formatação apenas no worker 5 s.
- Estatísticas ART de GC/bytes alocados, heap nativo/RSS e uptime Android
  a cada heartbeat, para correlação. Heap nativo não mede sozinho alocações
  do guest/pools; métricas cumulativas não são pausas de GC por frame.

**Arquivos:** `src/android_diagnostics.{h,cpp}`, `src/frame_sample_window.h`,
`src/pad_probe.cpp`, `src/vehicle_telemetry.cpp`,
`config/ambient_population.toml`, `src/perf_options.cpp`,
`android/MclaAdrenoDiagnostics.java`, `scripts/analyze_android_transition_log.py`.

**Correção de câmera/async:** NÃO aplicada sem evidência. Este é código PowerPC
recompilado, não uma cena Unity/Unreal com scheduler/pools acessíveis. Mover uma
rotina guest para thread host ou interpolar uma matriz ainda não identificada
pode quebrar TLS, ordem de física, câmera de missão/cutscene e colisão. Falta
identificar ativação real, target/FOV/transform e liberação do controle e medir
o caminho crítico. Nenhum draw foi omitido para aparentar fluidez.

**Aceite:** NÃO atendido ainda. Faltam trace do evento completo e comparação
frio/quente com as mesmas condições. Pedido de câmera e HUD válido não serão
apresentados como a sequência semântica completa solicitada.

## 5. Revisão e comparação de desempenho

### Perfil real antes da candidata

Captura `simpleperf record`, PID 6131 da v57, 99 Hz `cpu-clock`, unwind DWARF
8192 bytes, duração medida 39,9794 s: **10.777 amostras, zero perdidas**.
Backup privado do save/preferências (559.616 bytes) feito antes de abrir o jogo.
Captura ocorreu antes de iniciar a compilação nativa. Não foi injetado Start nem
dirigido o carro; não é perfil da sequência específica Start→controle.

Dados em `normal-v58/`, pasta privada de diagnóstico fora do Git:
`v57-startup.data`, `v57-report.txt`, `v57-simpleperf-record.txt`,
`v57-profile-native.log`, `v57-profile-logcat.txt`.

- UI launch COLD: 368 ms (`am start -W`), **não** tempo até carro controlável.
- Shader storage: 05:12:32.070 → 34.314 (2.244 ms); 19.063.861 bytes de cache
  lidos, 1.063 pipelines restaurados. Loaded-cache→pipelines: 508 ms.
- Cópia redundante da biblioteca corrigida na v57: `startup xenos=reuse;
  elapsed_ms=0` nesta execução, contra 60 ms na primeira cópia anterior. Não
  é comparação pareada de carregamento total.
- Janelas de updates: 54/5 s, 36/5 s, 41/5 s, 46/5 s após o trecho inicial.
  Taxas agregadas, não frame time individual/p95/p99.
- Perfil, percentuais **self** do CPU-clock do processo: SDLThread em
  `__kernel_clock_gettime` 11,85%; Audio Worker na mesma função 8,16%; funções
  guest `sub_82412F98` 3,95% e `sub_82411E98` 3,41%. `mcla_fence_spin`:
  1,09% self / 7,21% inclusive; caminho de espera de áudio 17,18% inclusive.
  Não somar percentuais inclusive de frames ancestrais.
- RSS cresce aproximadamente 865 MB→1,19 GB, VmSwap ~111–180 MB,
  `system_low_memory=false`, thermal_status=0. Indica pressão possível, não
  prova de que swap/GC cause o hitch. Logcat inspecionado não estabelece GC
  no frame crítico; métricas ART por etapa não existiam na v57.

**Conclusão causal:** há custo real em polling/esperas e trabalho guest, não
evidência de que apenas o layout cause toda a queda. O perfil não localiza o
frame de posse/câmera; não autoriza remover sincronização, trocar mixer ou
parallelizar código guest. Nenhuma alteração desses caminhos foi aplicada.

O buffer estatístico é limitado e instrumentação não aloca por frame.
Snapshots recentes se sobrepõem; não somar/ponderar suas médias/percentis como
se fossem janelas independentes. Scripts de análise mantêm os campos observados
e reportam critérios não estabelecidos, em vez de inferir sucesso.

Sem perfil causal, não alterar async de assets, pools, compilação, LOD, culling,
clocks ou syncdraw. A política nova de NPCs evita instâncias na admissão; ela
também pode causar tentativas repetidas de scripts. Os contadores permitem
detectar esse custo antes de declarar ganho.

**Validação pendente:** métricas A/B média/p95/p99, carregamento, população por
classe, carros adversários, missões/cutscenes, colisão/IA, touch/foco/reconexão.
Preservar o backup anterior ao primeiro teste; não sobrescrever saves para
fabricar um cenário comparável.

## Build, entrega e matriz de aceite

Build nativo concluído: codegen 5 arquivos escritos, 240 inalterados, nenhum
apagado; cinco módulos recompilados para os hooks novos. Foram emitidos avisos
`Unexpected float16_4 pack instruction` em funções 0x8243Cxxx/0x8243Dxxx durante
análise; os respectivos módulos não foram reescritos nesta mudança. Aviso
`filesystem::u8path` deprecated também permanece. Não são fixes de shader/CRT.

Java integral, 7.344 verificações de geometria, 739 de joystick, testes C++ de
estatística/concorrência e contratos de idioma/hooks/análise passaram:
`python3 scripts/ci/test_android_v58_review.py`. Hooks gerados revisados:
admissão retorna antes de salvar registradores/alterar a stack guest; setters
coagem o argumento antes do corpo original; câmera só observa o wrapper.

`0.1.58-normal-input-population-test`, versionCode 58, instalada com `pm install -r`
após verificar ausência de processo. Nenhuma limpeza de dados ou abertura
automática após instalar. APKs v56/v57 preservados. Bibliotecas SDL/runtime/GPU/
hooks idênticas à v57; somente `libmain.so` difere entre libs nativas. Certificado,
versão, DEX inglês, marcadores de input/diagnóstico e alinhamento conferidos.

APK: `Download/MCLA-NORMAL-v0.1.58.apk`.
SHA-256: `3ee60ed17e5cdd27e15adbc7e1a1128192dfc6e2ac2d02a8578539058f8bff09`.
Backup anterior ao perfil: pasta privada de diagnóstico
`normal-v58/pre-v58-save-and-preferences.tar`; não restaurado/sobrescrito.
Nenhum commit/push, APK no GitHub ou release criado.

| Item | Implementação/verificação | Critério funcional |
| --- | --- | --- |
| NPCs | Flags iniciais, bloqueio de re-enable/admissão e contadores adicionados | Pendente: zero por classe, ausência em física/IA e adversários intactos |
| Pressão dos pedais | Binding JNI/desenho corrigido, política estática removida | Pendente: pressão real/latência no aparelho e multitouch |
| Inglês | Inventário de literais Java e DEX passaram; categorias/estados próprios traduzidos | Sem smoke test visual de todos os caminhos de erro nesta instalação |
| Stutter/câmera | Perfil v57 válido e instrumentação nova; câmera/async não alterados | Não atendido: falta sequência Start→controle real e A/B |
| Revisão geral | Build, revisão do codegen e testes offline passaram | Sem ganho de FPS/p95/p99 comprovado |
| Analógicos adicionais | Refatoração completa e 739 testes passaram | Pendente: dirigibilidade/câmera/flicker com dedos reais |
