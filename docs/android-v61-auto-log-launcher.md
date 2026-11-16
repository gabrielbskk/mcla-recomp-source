# v61 — exportação automática unificada e launcher

## Escopo

Pedido: retirar a ação manual de exportar logs, organizar o launcher e manter
um arquivo entre sessões, com horários explícitos e limite de 8 MiB
(8.388.608 bytes). A importação dos arquivos do jogo e os seletores de driver
e shaderpack continuam disponíveis. Idioma inglês e preferências preservados.

## Implementação

- `android/MclaLogExporter.java`: worker serial de baixa prioridade; primeiro
  ciclo após 5 s, depois a cada 30 s após terminar o anterior, mais pedidos ao
  retornar ao launcher e sair/pausar o jogo. Usa o contexto da aplicação, sem
  reter Activity, sem serviço em foreground, wake lock ou novas permissões.
- Um documento MediaStore em
  `Documents/MCLA/logs/MCLA-diagnostic.txt`. Append incremental dos quatro
  arquivos nativos/Android atuais e rotacionados, até 64 KiB por fonte/ciclo.
  Não copia toda a história em cada atualização nem faz I/O por frame.
- Cursor persistido por device/inode acompanha renomeação de rotação. Compara
  até 64 bytes anteriores ao cursor para detectar truncamento/regravação.
  Cursor só é confirmado após fechar a saída. Um documento removido pode ser
  recriado na próxima tentativa, reiniciando a coleta das fontes disponíveis.
- Marcadores de processo/sessão com UUID aleatório local, data ISO com fuso,
  tempo monotônico, versão, aparelho e configurações selecionadas. Cada lote
  identifica fonte, offset e eventuais reset/linha parcial. O diagnóstico
  Android registra data/fuso e sessão em todos os eventos de lifecycle e
  heartbeats, permitindo distinguir retomadas no mesmo processo e virada de dia.
- Ao exceder 8 MiB, conserva aproximadamente os 4 MiB mais recentes a partir
  de uma fronteira de linha, acrescenta aviso `HISTORY TRIMMED`, identificação
  do processo e lote novo. Tamanho projetado é verificado antes da gravação.
  Não acumula arquivos históricos novos sem limite. Exportações antigas e
  fontes privadas existentes não são apagadas por esta mudança.
- `android/MclaLogChunk.java`: leitura limitada e preservação de linhas
  incompletas pequenas até o próximo ciclo. Linhas maiores que o lote podem
  ser divididas, com aviso explícito. Cópias são byte a byte, sem reinterpretar
  UTF-8 de linhas completas.
- `android/MclaLauncherActivity.java`: removido botão manual de logs;
  controles antes das opções gráficas/avançadas, shaders separados de
  diagnósticos, explicação do caminho e do limite, último status conhecido
  da exportação ao retornar ao launcher. Falhas são registradas e retentadas.
- `scripts/build_android_apk.sh`: inclui o novo helper Java no APK.

Nenhuma alteração nativa nesta versão: preserva a instrumentação60, SDL59,
syncdraw, shaderpack desativado, controles e correções de população.

## Artefato entregue

`Download/MCLA-NORMAL-v0.1.61.apk`, versão `0.1.61-auto-log-launcher`, code61.
SHA256: `55ab01dd4c1d6fdf96c7b35f33056f658a757b0ceebe9f939930383a7cf3bc32`.
Assinatura compatível e zipalign16KiB verificados. Comparado ao APK60, somente
manifesto/DEX e metadados de assinatura mudaram; todas as bibliotecas nativas
são byte a byte idênticas. Nenhuma instalação, abertura ou publicação foi feita.
APKs59/60 continuam preservados em Downloads e backups privados.

## Evidência TDD e verificação

`scripts/ci/android-input/TestLogChunk.java` foi escrito antes do helper;
RED observado no javac por `MclaLogChunk` ausente. Após implementação, GREEN
para deltas, idle, truncamento/regravação, UTF-8 e tamanho limitado.
Os testes de retenção foram adicionados depois: segundo RED por `recentTail`
ausente, seguido de GREEN ao implementar retenção limitada, inclusive com
entrada sintética de 9 MiB. Evidências privadas em `auto-log/red.txt`,
`green.txt`, `retention-red.txt` e `retention-green.txt`.

`python3 scripts/ci/test_android_normal_candidate.py` passou: compilação de
todos os fontes Android/SDL contra android.jar35, 7.344 verificações geométricas
de touch, testes do helper e contratos de fonte de perfil normal/importação.
Warnings existentes: Java8 obsoleto e APIs Android deprecated. Não medimos
cobertura Java; contagem de verificações não é cobertura ou teste visual.
Checkpoints Git não foram criados: árvore experimental contém trabalho
anterior misturado e não houve solicitação de commit/publicação.

## Limitações de confiabilidade e desempenho

- Integração MediaStore, append, permissões/armazenamento cheio, lançamento,
  retorno e renderização do launcher ainda exigem teste no aparelho. Compilar
  contra a API Android não equivale a executar um provider real.
- Android10+ é necessário para esta exportação. Não há garantia de execução
  depois que o processo for morto; não se inventa um fechamento normal. O
  próximo processo tenta recuperar somente o que restar nas fontes privadas.
- Os eventos Android têm hora/sessão de origem. Os lotes nativos mantêm os
  timestamps originais; coleta de backlog pode anteceder a sessão exportadora.
  As fontes são agrupadas, não fundidas em ordem temporal global.
- Morte entre append e persistência do cursor pode repetir o lote, identificado
  por fonte/offset. Gravação/compactação interrompida pode deixar texto parcial
  ou perder parte do histórico exportado. Não é um journal transacional com
  exatamente-uma-vez, nem backup durável de saves.
- Se as fontes rotacionarem mais rápido que a coleta, podem existir lacunas.
  O fingerprint local não detecta toda regravação arbitrária ou reutilização
  de inode. Não alegar captura completa de cada frame/evento.
- Compactação faz I/O de aproximadamente 4 MiB e aloca buffers no worker;
  overhead de CPU/I/O/GC durante gameplay ainda não foi medido. Não promete
  zero stutter nem melhora de FPS. Primeira execução pode coletar backlog.
- Sem captura de vídeo/áudio, envio de rede ou identificador persistente de
  aparelho novo. Os logs de origem podem conter caminhos e dados técnicos:
  revisar antes de compartilhar; não são sanitizados automaticamente.

## Aceitação no aparelho (pendente)

Instalar por cima sem desinstalar; abrir launcher e jogo manualmente. Confirmar
um documento criado/atualizado, novos eventos após ~30 s, pausa/retorno e nova
sessão no mesmo arquivo. Conferir data/fuso, offsets e eventos de lifecycle,
preservação de saves/configurações, controles e modo normal. Simular limite
e interrupção em ambiente de teste; comparar frame times/GC com60 em rota
equivalente antes de declarar overhead aceitável. Não iniciar automaticamente.
