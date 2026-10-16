# Correcao do g1.sh (V-B4 secao 2): log de saude pos-merge, 2026-10-16

Branch `test/b29-gate-v3-g1fix` (de `test/b29-gate-v3-execucao`, 8cc1349). Sem VM, sem push. Alterados: `runs/gate-b29/g1.sh`, `runs/gate-b29/g1.test.sh`.

## Mudancas no g1.sh
1. `G1_LOG_POS_S` (padrao 30): depois de detectar HEALTHY/size=3 nos 3 nos, o log de saude segue por esse tempo e so entao para (`parar_log_saude`; o trap EXIT continua cobrindo abort/erro). O `saude-depois.txt` e a extracao de logs continuam logo apos a deteccao.
2. `t_merge.txt` mantem a linha `t_merge_s=` (polling) e acrescenta, a partir do saude-log: `t_saude_primeira_ns`/`_iso` (1a amostra HEALTHY/size=3 de qualquer porta com t > end), `t_merge_saude_s`, e tambem `t_saude_todas_ns`/`_iso`/`t_merge_saude_todas_s` (instante em que as 3 portas ja haviam mostrado HEALTHY/3). Sem amostra: `t_saude_primeira_ns=ausente (...)`.
3. `G1_LOG_SAUDE_S=0`: sem log, sem espera pos, sem campos de saude.

Ressalva: se a janela terminar antes de o cluster perceber o split, uma amostra HEALTHY/3 logo apos o end seria vista como "merge"; nesse caso o t_merge nao tem significado (a janela de 40 s da G-5 e menor que a deteccao) e o `log-vista-*` deve ser consultado.

## TDD
- `af94c8d` (+ `22c6fff`, correcao de regex do proprio teste): T-g1-8 (STUB_POS_FUSAO=3, POS=4 s: amostras HEALTHY/3 pos-end, log segue >= 3 s apos a 1a, campos de t_merge.txt, log para ao fim) e T-g1-9 (SAUDE_S=0 com POS=60 nao espera nem escreve campos). Vermelho medido: 5 FAIL.
- `a568a39`: fix; g1.test.sh exporta `G1_LOG_POS_S=1` por padrao nos testes para nao esperar 30 s.

## Regressao (contagens medidas)
| Suite | Resultado |
|---|---|
| g1.test.sh | 61 PASS / 0 FAIL (Git Bash e WSL) |
| g1b.test.sh | falhas=0 |
| g5.test.sh | 50 / 0 |
| g6.test.sh | 70 / 0 (uma execucao isolada, sob carga paralela de outra suite, deu 63/7: temporizacao; repetida 2x sem carga: 70/0) |
| inject-partition.test.sh | Git Bash 125/0/2 SKIP; WSL 127/0/1 SKIP |

LF: 0 CR em g1.sh e g1.test.sh; modos 100755.
