# Relatorio B-36 / R.4: variante `MCRelogio` (relogio acompanha o `ts` recebido), resultados de R.3

> Branch `test/b36-relogio-max`. Somente `runs/tlc/relogio-*`, `runs/tlc/audit-b36/` e este documento foram adicionados. `SessionStore.tla`, `MCSession.tla` e os `runs/tlc` existentes nao foram tocados. Nenhuma VM tocada, nenhum push.
> Este relatorio NAO abre P-28, NAO reclassifica P-26 e NAO propoe texto de catalogo: a leitura da tabela 1.6 e o status sao decisao do Senior. O que segue e o que os brutos sustentam.

## 1. Como foi executado

- Runner: `spec/run-tlc-relogio.sh`, modo `livre`, sempre `SEM_SIMETRIA=1`, `WORKERS=1`, `MAXEVENTOSFALHA=2`. Comandos exatos em `runs/tlc/audit-b36/r3run.sh`; saida do runner em `runs/tlc/audit-b36/r3run.log`. TLC de `tla2tools.jar` do scratchpad da sessao (mesmo jar das rodadas anteriores).
- Colunas: `sem` = restricao `SemFalha`; `queda` = `SoQueda`; `particao` = `SoParticao` (as restricoes do proprio modelo).
- **Timebox: o `TIMEOUT_SECS` do runner vale por propriedade, nao por coluna.** Usei 600 s por propriedade (niveis 1 e 1b) e 1800 s por propriedade (nivel 2). Portanto "10 min por coluna" foi aplicado como 10 min por (coluna, propriedade). Se o Senior queria 10 min no total por coluna, a leitura abaixo nao muda para as celulas ja fechadas ou violadas, mas as celulas `SEM_VIOLACAO_ATE_AQUI` tiveram mais tempo do que o literal pedido.
- Verificacao previa de carga: nenhum `java` rodando. Havia atividade do Worker A (`mutacao-gate-v4.sh todas 3`, `l-gate.test.sh` e `l0c.sh`, todos bash) durante toda a rodada; por isso o nivel 2 foi com `WORKERS=1`. Tempos de parede podem estar inflados por essa concorrencia; contagens de estados nao dependem disso. Havia um processo `vmwp` (Hyper-V) no sistema; nao ha janela de injecao ativa segundo o enunciado, nao inspecionei a VM.
- Inicio 2026-10-16 13:35, fim 16:01 (horario da maquina).

## 2. Resultados medidos (copiados do `resumo-*.tsv`/log; "gerados / distintos")

Diretorios: nivel 1 `runs/tlc/relogio-livre-<falha>-n1/`, nivel 1b `...-n1b/`, nivel 2 `runs/tlc/relogio-livre-sem-n2/`.

### Nivel 1: r2, MaxCanal=2, MaxRelogio=3, MaxEventosFalha=2, timebox 600 s

| Prop | sem | queda | particao |
|---|---|---|---|
| I1P | SEM_VIOLACAO (exaustivo), 4505457 / 201783, profundidade 13, 35 s | SEM_VIOLACAO (exaustivo), 22930847 / 1614264, profundidade 17, 158 s | SEM_VIOLACAO_ATE_AQUI, limite 600 s, 65139502 / 7424564 |
| I1F | VIOLADA, traco 8 estados, 145551 / 20019 | VIOLADA, traco 8, 193717 / 36272 | VIOLADA, traco 5, 2314 / 646 |
| I4F | VIOLADA, traco 7, 35315 / 5872 | VIOLADA, traco 7, 44979 / 9684 | VIOLADA, traco 5, 2314 / 646 |
| I2Fb | VIOLADA, traco 4, 296 / 87 | VIOLADA, traco 4, 316 / 115 | VIOLADA, traco 4, 316 / 115 |
| RelogioDomina | SEM_VIOLACAO (exaustivo), 4505457 / 201783, 23 s | SEM_VIOLACAO (exaustivo), 22930847 / 1614264, 129 s | SEM_VIOLACAO_ATE_AQUI, limite 600 s, 106537915 / 11101276 |

(Em `sem`, I1P e RelogioDomina fecharam com as mesmas contagens 4505457 / 201783, o que e esperado se ambos varrem o mesmo grafo; idem `queda`.)

### Nivel 1b: r2, MaxCanal=3, MaxRelogio=5, MaxEventosFalha=2, timebox 600 s

| Prop | sem | queda | particao |
|---|---|---|---|
| I1P | SEM_VIOLACAO_ATE_AQUI, 90820078 / 11394153 | SEM_VIOLACAO_ATE_AQUI, 77797571 / 13750323 | SEM_VIOLACAO_ATE_AQUI, 71007705 / 14302955 |
| I1F | VIOLADA, traco 7, 63664 / 13652 | VIOLADA, traco 7, 77974 / 20065 | VIOLADA, traco 5, 2484 / 845 |
| I4F | VIOLADA, traco 7, 46446 / 10223 | VIOLADA, traco 7, 57036 / 14978 | VIOLADA, traco 5, 2484 / 845 |
| I2Fb | VIOLADA, traco 4, 296 / 97 | VIOLADA, traco 4, 316 / 125 | VIOLADA, traco 4, 316 / 125 |
| RelogioDomina | SEM_VIOLACAO_ATE_AQUI, 110014415 / 13272600 | SEM_VIOLACAO_ATE_AQUI, 96882690 / 16893678 | SEM_VIOLACAO_ATE_AQUI, 91621057 / 18172529 |

Nenhuma celula de I1P ou RelogioDomina fechou no nivel 1b.

### Nivel 2: livre, r3, sem falha, MaxCanal=2, MaxRelogio=3, timebox 1800 s por propriedade, WORKERS=1

| Prop | Resultado |
|---|---|
| I1P | SEM_VIOLACAO_ATE_AQUI, limite 1800 s, 293709729 / 16096582 |
| I1F | VIOLADA, traco 8 estados, 461197 / 41948 |
| I4F | VIOLADA, traco 8 estados, 461197 / 41948 |
| RelogioDomina | SEM_VIOLACAO_ATE_AQUI, limite 1800 s, 377819350 / 19625662 |

Nivel 3 (opcional): nao executado.

## 3. Auditoria dos tracos (`scripts/audit_tracos.py`, saida em `runs/tlc/audit-b36/`)

Rodado sobre todos os 20 tracos de violacao (I1F, I4F e I2Fb dos niveis 1 e 1b; I1F e I4F do nivel 2). Criterio mecanico do script: algum passo local (`Login/Logout/Block/...`) que nao altera o registro local (gate de `ts` rejeitou). **Resultado: coluna `depende_D4` = NAO nos 20 tracos; nenhum passo com gate rejeitado** (`classificacao-d4.tsv`). Observacao: o campo "Leitura" gerado e `(sem texto pre-definido)` para as sequencias com `EntregaMensagemMax`, porque o script tem textos fixos so para as sequencias antigas; a classificacao D-4 e o criterio mecanico e nao depende desse texto.

Sequencias de acoes (medidas):

| Traco | Sequencia |
|---|---|
| I1F sem/queda n1 | Login, Login, Logout, Logout, EntregaMensagemMax, Login, EntregaMensagemMax |
| I1F sem/queda n1b | Login, Login, Logout, Login, Logout, EntregaMensagemMax |
| I4F sem/queda n1 | Login, Login, EntregaMensagemMax, Logout, Block, EntregaMensagemMax |
| I4F sem/queda n1b | Login, Login, Login, Logout, Block, EntregaMensagemMax |
| I1F e I4F particao (n1 e n1b) | Login, Logout, Particionar, Mesclar |
| I2Fb (todas) | Login, IncrementFailure, IncrementFailure |
| I1F e I4F nivel 2 (r3) | Login, Login, EntregaMensagemMax, EntregaMensagemMax, Login, Logout, EntregaMensagemMax |

Leitura minha (inferida da leitura de dois tracos, `sem-n1` I1F e `particao-n1` I1F; nao e do classificador):
- `sem-n1` I1F: a replica 2 faz `Login` de `s1` com `ts` 3 enquanto o `Logout` de `s1` (ts 2) esta so em r1; a entrega do `SESSAO_VALIDA` ts 3 a r1 sobrescreve INVALID ts 2 por VALID ts 3. Nenhum passo local rejeitado. Isto e o mesmo tipo de mecanismo descrito na validacao do Senior (login concorrente da mesma `sid` em replicas diferentes, LWW).
- `particao` I1F/I4F: mesma sequencia `Login, Logout, Particionar, Mesclar` das violacoes ja conhecidas do modelo livre (nesse traco `Mesclar` leva a sessao de INVALID para NONE; o classificador ja marcava essa familia como independente de D-4). I2Fb: auto-bloqueio por limite (P-27), independente do relogio.
- Nao li os demais tracos linha a linha; as sequencias acima sao medidas, a leitura do mecanismo dos outros e extrapolacao.

## 4. Tabela 1.6 preenchida so com o que os dados sustentam

| Linha pre-registrada | O que os dados mostram | Situacao |
|---|---|---|
| (1) `I1P`, `I1F`, `I4F` sem violacao com `RelogioDomina` verde | `I1P` e `RelogioDomina` fecharam exaustivamente, sem violacao, **apenas** em r2, cotas 2/3/2, colunas `sem` e `queda`. `I1F` e `I4F` estao VIOLADAS em todas as colunas e niveis medidos (`sem`, `queda`, `particao` no n1 e n1b; `sem` r3 no n2). | **Nao atendida** para `I1F`/`I4F`. |
| (2) alguma ainda violada em traco sem passo de gate rejeitado | `I1F` e `I4F`: violadas em todas as celulas medidas; nos 20 tracos auditados, nenhum passo local com gate rejeitado (`depende_D4 = NAO`). | **Ativa para `I1F` e `I4F`**, em todas as colunas e niveis. Mecanismo de pelo menos dois tracos lido por mim (secao 3); restante nao lido. |
| (3) nao fechou no timebox | `I1P` e `RelogioDomina`: particao n1; as tres colunas do n1b; r3 do n2 (`SEM_VIOLACAO_ATE_AQUI`, nunca verde). | **Ativa para `I1P` e `RelogioDomina` fora de r2/cotas 2-3 em sem e queda.** |

Linha por propriedade:

| Propriedade | Fechou sem violacao | Violada (traco) | Nao fechou |
|---|---|---|---|
| `I1P` | r2 n1: sem, queda | nenhuma celula | r2 n1 particao; r2 n1b sem/queda/particao; r3 n2 sem |
| `RelogioDomina` | r2 n1: sem, queda | nenhuma celula | r2 n1 particao; r2 n1b sem/queda/particao; r3 n2 sem |
| `I1F` | nenhuma | todas as 7 celulas medidas | nenhuma |
| `I4F` | nenhuma | todas as 7 celulas medidas | nenhuma |
| `I2Fb` | nenhuma | 6 celulas (n1 e n1b; nao executada no n2), traco de 4 estados, mecanismo P-27 | nenhuma |

## 5. O que NAO e sustentado por estes dados

- Que `I1P` esteja fechada em geral: so fechou em r2 com `MaxCanal=2`, `MaxRelogio=3`, colunas `sem` e `queda`. Nas demais celulas e `SEM_VIOLACAO_ATE_AQUI`.
- Que `RelogioDomina` esteja verde em r3 ou nas cotas 3/5/2: nao fechou.
- Que `I1F`/`I4F` existam no sistema real (pergunta ao Gestor sobre `Login` concorrente da mesma `sid`): estes dados sao sobre o modelo.
- A afirmacao do smoke da validacao de que `I1P` "some" em r3: aqui em r3 a execucao de 1800 s nao encontrou violacao mas nao fechou.
- Qualquer mudanca de status de P-26 ou abertura de P-28: nao feita (decisao do Senior).

## 6. Pontos para o Senior

1. Interpretacao do timebox (por propriedade, secao 1). Posso refazer com limite agregado se pedido.
2. Nivel 3 nao rodado. Cabe se o Senior quiser a comparacao com o semeado auditado.
3. Fechar `I1P`/`RelogioDomina` nas celulas abertas exigiria mais tempo ou mais workers (e uma janela sem Worker A), ou cotas menores; nao tentei alem do pedido.
4. Se a decisao for isolar o mecanismo `I1F`/`I4F` (login concorrente da mesma `sid`), uma variante com `Login` condicionado a `sid` livre globalmente seria experimento novo; nao feito.
