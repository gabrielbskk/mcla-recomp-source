# Candidata normal v0.1.57 — touch, carregamento e launcher

## Escopo e evidências (2026-11-14)

Esta candidata usa a versão normal, sem aplicar o preset visual S23. Preserva
assinatura/pacote, saves, configurações, caches, driver, `syncdraw` e shaderpack
desativado. Não modifica caminhos de gamepad/MnK, clocks ou proteção térmica.
Nenhum APK/release será publicado no GitHub.

Foram preservadas cópias dos logs anteriores na pasta privada de investigação
`normal-touch-investigation`, fora do repositório. No lançamento de 04:10:

- Swapchain 2340×1080 criada às 04:10:28.423; guest 1560×720, sem preset S23.
- Inicialização do shader storage às 04:10:28.619; 19.014.525 bytes de cache
  Vulkan carregados às 04:10:30.280; 1.053 pipelines restaurados às 30.653.
- Há pipelines novos durante gameplay. Cache funcional não elimina compilação
  de material novo, streaming ou espera da CPU/GPU. Não foi feito perfil de
  funções/A-B de FPS nesta candidata, nem demonstrada a causa de toda a regressão.
- O aviso de surface/swapchain outdated aparece no encerramento às 04:14:43.920;
  isoladamente não prova recriação repetida durante touch.

## Alterações

### Touch (`android/MclaTouchControls.java`, `MclaTouchLayout.java`)

- HUD estático por padrão: movimentos não solicitam redesenhos contínuos;
  valores de direção/gatilhos continuam sendo enviados normalmente, sem
  reduzir frequência de entrada. Desaparecimento por inatividade mantido.
- Animação antiga disponível como teste A/B em Controles; quando ligada,
  redesenhos são agrupados por frame via `postInvalidateOnAnimation`.
- Coordenadas de dedos reaproveitadas durante movimentos, em vez de criar
  arrays a cada amostra. Preferências/posições lidas no carregamento do layout,
  não repetidamente em desenho, hit-test e envio de estado.
- Separação geométrica limitada a 96 passes, apenas ao carregar/redimensionar
  layout; evita interseções nos casos cobertos pelos testes. Aplica ajustes em
  memória, sem regravar nem apagar o layout personalizado. Não é garantia
  matemática para qualquer conjunto arbitrário de controles sobrepostos.
- Botões têm prioridade sobre as áreas ampliadas de captura dos sticks.
  Sticks animados permanecem em suas posições; apenas o cursor se desloca.
- Corrigidos rótulos START/BACK trocados (os bits nativos não foram alterados).
- Sem superfície adicional, textura fullscreen extra, mudança de Z-order,
  alteração da SurfaceView SDL ou recriação de swapchain.

Isso reduz trabalho evitável, mas o desaparecimento do flicker precisa ser
confirmado no aparelho com touch real. Sobreposição visual sozinha não foi
comprovada como a origem dos flashes da imagem do jogo.

### Carregamento e I/O (`MclaActivity.java`, `MclaAdrenoDiagnostics.java`, `MclaLogExporter.java`)

- Plugin Xenos reutilizado quando tamanho e stamp `lastUpdateTime:tamanho`
  coincidem. Atualização copia para arquivo temporário, valida tamanho e faz
  rename atômico; erro impede carregar versão parcial/incompatível. Primeira
  abertura após atualização ainda copia. Duração e reuse/copy registrados.
- Removidas exportações periódicas MediaStore dos dois agendadores (Activity
  e heartbeat). Logs privados limitados e amostragem de diagnóstico mantidos;
  exportação ocorre ao pausar/encerrar ou por botão no launcher.
- Exportações simultâneas de pause/destroy/botão são agrupadas em um único
  trabalho, sem enfileirar múltiplas cópias integrais dos mesmos logs.
- Removido `fsync` por evento de diagnóstico, inclusive eventos na UI durante
  inicialização. Isto só altera durabilidade de logs após queda de energia,
  não o caminho de salvamento do jogo.
- Nenhuma limpeza de shader storage, redução silenciosa de sincronização,
  alteração de threads de áudio ou supressão de draws para esconder stalls.

### Cidade vazia (`MclaWorldOptions.java`, `src/perf_options.cpp`)

- Switch persistente, OFF padrão, somente próximo início; envia
  `REX_MCLA_EMPTY_STREETS=true/false`. OFF explícito evita cvar antigo ativado.
- Cvar nativo `mcla_empty_streets`: usa o hook de registro existente para
  `noautomatictraffic`, `notriggeredtraffic`, `noparkedcars`. Densidade de
  pedestres/estacionados zerada no hook de zonas já existente.
- Análise do XEX correspondente e codegen: registros `sub_827AE4F0`,
  `sub_827AE510`, `sub_827AE710`; consumidor automático em `sub_826F6920`
  (leitura 0x826F69C8) e tráfego acionado em 0x82687F70; construtor do
  gerenciador `sub_826F5CB0`. Não se usou `noambientspawn` nem desativação
  global de veículos, nem se alterou a IA de adversários.
- Não foi executado gameplay com o switch: ausência de todo tráfego/pedestre,
  preservação dos adversários e efeitos em missões/cutscenes ainda precisam de
  validação. É um controle experimental, não promessa de FPS ou de segurança
  de progressão. Desligar e reiniciar restaura os pedidos normais.

### Launcher

- Jogar no topo; categorias recolhíveis: Arquivos, Gráficos/driver, Cidade,
  Shaders/diagnóstico, Controles, Cheats, Áudio. Arquivos abre automaticamente
  quando faltam assets. Preferências existentes e controles preservados.
- Removido switch do preset S23; aplicação desse preset desativada sem apagar
  preferência histórica. Seletor independente de resolução continua funcionando.
- Animação do touch, cidade vazia e exportação manual com descrições/avisos.
- Nome visível do aplicativo passa a MCLA. Telemetria diagnóstica existente
  permanece para investigar regressões, sem impor preset de desempenho.

## Verificações e limites

- `python3 scripts/ci/test_android_normal_candidate.py`: Java integral,
  7.344 pares de controles em 4 resoluções, escalas 60/100/114/150%, layouts
  normal/pedais/personalização atual S23; contratos de fonte para preset OFF,
  ausência de exportação periódica/fsync de eventos e allowlist ambiente.
- Testes anteriores: áudio 37, MnK 128, resolução 9, overrides de perfil 21,
  política Java de cheats. Esses testes não equivalem a teste funcional Android.
- Build C++ passou. Primeiro link abortou no `ld.lld` com SIGSEGV; repetido com
  `CMAKE_SHARED_LINKER_FLAGS=-Wl,--threads=1` e `-j1` passou. É configuração local
  do linker, não redução de threads do jogo. Codegen não reescreveu módulos.

Teste pendente: touch real sem dispositivos externos, multitouch e pedais,
foco/pausa/reconexão; comparação estático/animado; gamepad e MnK; carregamento
frio/quente e percentis de frame; corrida com adversários e cidade vazia OFF/ON.
Não declarar flicker eliminado ou ausência de perda de desempenho sem esse A/B.

## Entrega local

- `0.1.57-normal-touch-test`, versionCode 57, instalada com `pm install -r`
  após confirmar processo parado; nenhuma desinstalação ou limpeza de dados.
- APK: `Download/MCLA-NORMAL-v0.1.57.apk`. SHA-256:
  `45f02bc3eaf7952d4609bb0acc1a63eafe34f968f7fb14e1412c05e43ed6a18d`.
- Certificado compatível, assinatura, versão, alinhamento de 16 KiB, marcadores
  DEX/nativos conferidos. Entre bibliotecas nativas, só `libmain.so` difere da
  v56; SDL, runtime, GPU e hooks do driver são idênticos.
- Launcher aberto para smoke test: hierarquia Android capturada e sem fatal
  do aplicativo nos logs inspecionados. Não foi iniciado gameplay automatizado
  nem ativado cheat/cidade vazia para validação.
- APKs antigos mantidos. Nenhum commit/push/upload de APK ou release efetuado.
