# Relatório do Worker: W-6c, F3 na matriz S3, swap no portão e trilha completa (2026-11-05)

Branch `feature/w6c-f3-memoria`, worktree `wt-w6c`, criada de `6f366ac` (ponta de `feature/w6-orquestrador-s3`). **Testes: suíte completa pendente (interrompida para H0)**, seção 3. O
`wt-w6` não foi tocado. Nada foi enviado a `origin` e nada foi integrado. Escopo: o pedido do W-6c (F3 como falha da
matriz, swap no portão, trilha completa) e os acréscimos que o coordenador mandou depois, vindos da V-W6b
(`wt-w6/docs/validacao-w6b-2026-11-05.md`, `081e1c4`): N1, R3, R4, R7, R8, B1 no F3 e o teto de 1010 s.

## 1. Commits

| Commit | Tipo | O quê |
|---|---|---|
| `7b43d48` | test | F3 (esquema `/2`, célula, S26), swap (S27) e trilha (S28), vermelhos |
| `d030642` | test | S26h (raiz do nó do F3 ocupada no disparo) e ajustes da suíte do F3, vermelho |
| `f4bcf1f` | feat | F3, swap no portão e trilha completa no orquestrador e nos dois módulos Python |
| `2b44245` | test | acréscimos da V-W6b: S22 sem relógio com N1 e R4, dublê de F4 com o *ping*, S25 (R8), S26e sem relógio, S1 (R7), teto de 1010 s; vermelho |
| `aea4bdb` | feat | N1, R4 e R7 no orquestrador |
| `8fa508b` | test | S22, S25 e S26e sem relógio também com o *host* carregado (achado da primeira rodada das mutações em paralelo) |
| `d2c7241` | test | P-56: swap ainda ≥ limite depois da recriação para a fase com 11; vermelho |
| `e388c8f` | test | dublês de F2/F4 esperam a parada prevista do `java` (o controle sem mutação falhou uma vez sob carga com oito suítes) |
| `6ee7d51` | test | S22 F4 com janela de 300 s: sob carga, o arranque do dublê comia a folga de 80 s do N1 |
| `16ac98f` | test | S5 sem relógio (o AC-5 do dublê caía antes do disparo sob carga) e mais folga nos casos do F3 (amostra a +60 s) |
| `f2dac17` | feat | P-56: exit 11, `swap_portao.parada`, `--help` |
| (este) | docs | `implementacao.md`, `testes.md`, `registro-de-problemas.md` e este relatório |

## 2. O que foi feito

### 2.1 F3 como falha da matriz

- `campanha_s3_matriz.py` aceita `<SYNC|ASYNC>-F3`. Continua uma falha por linha: não existe célula com duas falhas,
  então F3 exclui F2 e F4 por construção.
- Números do F3 no esquema `campanha-s3/2` (o `/1` mais `f3_no`, `f3_delay_ms`, `f3_jitter_ms`, `f3_distribuicao`,
  `f3_dur_s`, cada um com origem e sem padrão). O `/1` continua aceito, porque a calibração não roda F3 (B-2 do B-40).
  F3 na fila sem o `/2` sai com 4 no pré-voo e no `--dry-run`. Com os valores do *rebaseline* (isn2, 20, 13, normal,
  251 a +49 s), a janela acaba em +300 s, no fim da carga, e passa na coerência.
- Injetor: o `inject-jitter.sh` **real e sem mudança** (`7e68962b…`), chamado como no `run-rebaseline.sh` (que só li):
  `--node --iface eth0 --delay <n>ms --jitter <n>ms --distribution --duration`, `PODMAN` exportado.
- Marcadores: o injetor não tem `--marker`, então o orquestrador grava `fault=F3 node=<no> event=start` logo antes de
  lançá-lo e `event=end` logo depois que ele sai (e no *trap*, se for interrompido), no formato do F1/F2/F4.
- Antes do disparo: PID do nó, raiz do nó livre (sem `netem`, `clsact` nem o `prio 1:` do F4) e *dead-man* armado
  antes do `netem` (`systemd-run`, `f3_dur_s` + 90 s = 341 s, `AccuracySec=1s`, `nsenter -t <PID> -n tc qdisc del dev
  eth0 root`, o comando do `run-rebaseline.sh:789-790`). Na saída: `end`, resto de rede (o "heal" do F3 é o `tc qdisc
  del` da raiz no PID atual), desarme.
- Conferência do `netem` a +2 s do disparo (C5 do *rebaseline*; o B-40 §4.3 a torna recusa). As 9 F3 do *rebaseline*
  em `dados-vm/rebaseline-2026-11/` tinham `netem` a +2 s, todas.
- Exits: 0, 1 e 4 seguem (C3/P2, os do *rebaseline*), 1 e 4 como recusa; 5 (netem rejeitado) para a fase com 6, salvo
  com o nó já parado (recusa).
- B1 no F3 (P-54): o AC-5 que para o nó sob o `netem` leva o netns junto, e o injetor real sai com **0** e escreve "OK"
  no log (a remoção é `|| true`). O orquestrador trata exit 0 com o nó parado como pendente e confere linha de fim,
  trilha e `start`, como no B1 de F2/F4.
- *Trap* (P-55): o injetor dorme a janela em primeiro plano; com o F3 ativo o `limpar` dá `pkill -TERM -P` no filho,
  como o `matar` do runner.
- GO de rede também antes da primeira F3; o pré-voo recusa (exit 4) resto de rede em qualquer nó de pé, inclusive
  `netem` na raiz.
- `execucao.json`: `injetor_exit`, `deadman` (com o PID), `rede_pos`, `falha_efetiva.netem_efetiva`, `disparo_ms`,
  `inj_inicio_offset_s`, `injetor_alvo_parado`, como as outras falhas.

### 2.2 Swap no portão

`SwapTotal − SwapFree` de `/proc/meminfo`, lido depois do K1 de cada execução, vai a `swap_usado_kb_k1` e a
`swap_portao` (`limite_kb`, `leituras_kb`, `recriado`, fonte). Com `--swap-limite-kb N` (sem padrão) e swap ≥ N, os
brutos da primeira recriação vão para `antes-recriacao-swap/`, o *cluster* é recriado por `preparar_cluster` (o
mesmo caminho: `down`/`up`, seis *caches*, camada 3, K1) e a execução roda com o mesmo i e a mesma semente, sem
recusa. Uma recriação por portão; se o swap continuar ≥ N depois dela, a fase para com **exit 11** sem rodar a
execução (decisão do coordenador sobre o P-56: nas 4 recriações da noite 1 o swap caiu para 39 a 51 MB), com o motivo
no fim do log e em `swap_portao.parada`; a retomada roda o mesmo i (DT-014). Com o limite, o pré-voo exige o
`meminfo` legível.

### 2.3 Trilha completa

`campanha_s3_conferir.py trilha` imprime `fim`, `linhas_perdidas` (campo da linha `fim` de `decisoes`, contrato 1.1+),
`trilha_incompleta` e o motivo, sem mudar o exit. O `execucao.json` ganha `trilha_incompleta` e `trilha`. Sem opção,
aviso; com `--exigir-trilha-completa`, motivo `infra:` e DESCARTADA-INFRA, e a fase segue. Execução sem a trilha ou com
a trilha ilegível conta como incompleta. As duas opções entram no *bundle* (linha `opcoes:`), e retomar a fase com
outras é recusado.

### 2.4 Acréscimos da V-W6b

- **N1:** `fim_nominal_desde_ancora_s` (`inj_inicio_offset_s` + `f*_dur_s`) e `injetor_saida_desde_ancora_s` em toda
  falha injetada. Dublê de F4 com o *ping* de 40 s: com o alvo parado durante o *ping*, sai com 6 e grava o `end` no fim
  dele, como o real. O S22 confere que o F4 do braço B sai mais de 20 s antes do fim nominal, e que o braço A e a F2 do
  braço B saem depois dele.
- **R4:** o B1 exige o `start` da falha no nó (F2, F3, F4) e, no F4, a linha `degradacao aplicada: alvo <no>`. Casos
  negativos para F2 sem `start`, F4 sem `start` e F4 sem a linha.
- **R3/R8:** o dublê de `java` para o nó 2 s virtuais depois do `start` da falha (`ac5-apos-start`), só escreve a
  linha de fim depois do `end` e, no S25, segura a linha de fim até o nó do F1 parar (`java-espera-parado-isn2`). O
  S26e (B1 no F3) também passou a esperar o `start`. A primeira rodada das mutações em paralelo mostrou que ainda
  havia relógio: com quatro suítes ao mesmo tempo, a janela da F2 passava do fim da carga (o nó já religado, exit 0)
  e o *kill* do F1 do S25 vinha depois do teto de 200 s; daí a espera pelo `end` e os tetos de 900 s (`8fa508b`). Na
  segunda rodada, com oito suítes no *host*, o controle sem mutação ainda falhou uma vez no N1 (a parada do `java` veio
  depois do *ping*); agora o `java` grava `ac5-previsto` e os dublês de F2/F4 só olham o alvo depois da parada
  (`e388c8f`). Na terceira, com a suíte completa ao lado, o N1 do F4 falhou mais uma vez por relógio (o arranque do
  dublê comia a folga de 80 s); a janela do F4 no S22 passou a 300 s (`6ee7d51`). Na mesma rodada, o S5 (que não
  estava na lista da V-W6b) falhou por relógio: o AC-5 do dublê caiu antes do disparo da F2 e a guarda de *netns*
  parou a fase com 6 (a O1); passou a usar o `start` também, e os casos do F3 ganharam folga (`16ac98f`).
- **B1 no F3:** item 2.1.
- **R7:** `uso()` vai até o `set -euo pipefail`; o S1 confere "Variaveis", "Exit codes" e a última linha.
- **C-6:** 1010 s passa (mínimo do código com os números do pré-registro: 986 s), também com o esquema `/2` do B-40.

## 3. Testes

Rodados no *host* (Git Bash) com *shims*; sem Maven, Podman nem *cluster* (nada em `workload/` mudou; o Surefire do
W-6, 1100/0/0/1, vale para esta árvore). Antes das 22:00Z, só as suítes leves; depois, a suíte completa.

| Suíte | Comando | Resultado |
|---|---|---|
| Orquestrador (S1..S28) | `bash scripts/run-campanha-s3.test.sh` | **suíte completa pendente (interrompida para H0)**; ver abaixo |
| Conferências | `python -I scripts/test_campanha_s3_conferir.py` | **44 OK** (30 + 14) |
| Matriz | `python -I scripts/test_campanha_s3_matriz.py` | **16 OK** |
| h4 do S3 | `python -I scripts/test_campanha_s3_h4.py` | **11 OK** |
| `inject-crash.sh` | `bash scripts/inject-crash.test.sh` (como carga das mutações) | **141 PASS, 0 FAIL** (injetor sem mudança; rodado como carga da primeira rodada das mutações) |

**Estado: suíte completa pendente (interrompida para H0).** A rodada da ponta `16ac98f` estava no S16, sem falha, quando
o coordenador mandou o *host* para H0 (X-17 e calibração com `1f65a02`, sem o W-6c); parei os meus processos pelo PID e
descartei o resultado parcial. O W-6c entra na integração depois da calibração, antes da avaliação, com a suíte completa
rodada com o *host* livre. Antes, em pontas anteriores: S28 24/0 e S26 + S26h + S27 87/0 (implementação de `f4bcf1f`),
S1 25/0 (`aea4bdb`); as rodadas completas de `f2dac17`, `e388c8f` e `6ee7d51` foram paradas (duas por teste que ainda
dependia do relógio sob carga, corrigido em `6ee7d51` e `16ac98f`, e uma pela parada do limite de uso).

Vermelho conferido antes de cada implementação: Python 4 falhas e 11 erros (conferir) e 1 falha (matriz); S27/S28 com
10 PASS e 40 FAIL contra o orquestrador do W-6b; S26h com 1 FAIL contra a primeira versão do W-6c; S1/S22/S25 da
V-W6b com 68 PASS e 16 FAIL (S1: 3; S22: 13; o S25 já passava, a mudança dele é de robustez) contra `f4bcf1f`; S1 e S27
do P-56 com 52 PASS e 7 FAIL contra `d2c7241`.

Mutações da V-W6b §2.1, as quatro em paralelo e com o `inject-crash.test.sh` de carga:

| Mutação (V-W6b §2.1) | Casos | Ponta | Em paralelo com | Resultado |
|---|---|---|---|---|
| sem o ramo do B1 em `concluir_injecao` | S22 | `6ee7d51` | m2 e a suíte completa | **32 PASS, 21 FAIL**: F2 e F4 positivos RECUSADA sem `injetor_alvo_parado`, braço A PULADA; nos negativos (`sem-ok`, `sem-start`, `sem-aplicada`) e no nó sem AC-5, `injetor_alvo_parado` vazio |
| sem o prefixo `infra: ` (P-53) | S22, S23 | `6ee7d51` | m1 e a suíte completa | **56 PASS, 3 FAIL**: nó parado sem AC-5 RECUSADA em vez de DESCARTADA-INFRA e sem o motivo `infra:`; S23 com o motivo sem `infra:` |
| sem o desvio do F1 em curso no O8 (S25) | S25 | `e388c8f` | as outras três e a suíte completa | **2 PASS, 4 FAIL**: DESCARTADA-INFRA, `outros_parados` = `isn2`, dois `podman start`, e o log sem "parado pelo F1 ainda em curso" |
| sem o religamento do S3 em `pos_execucao` | S23 | `e388c8f` | as outras três e a suíte completa | **1 PASS, 5 FAIL**: a fase sai com 10, `isn3` fica parado, sem `podman start` |

As mesmas duas últimas também falharam com os testes de `aea4bdb` e `8fa508b` nas rodadas anteriores (com até oito
suítes no *host*). Entre `e388c8f` e a ponta, o S23 e o S25 não mudaram. Uma suíte de controle sem mutação, rodada ao
lado na segunda rodada, falhou uma vez no N1 do S22 (corrigido em `e388c8f` e `6ee7d51`).

Contratempos de teste, todos de dublê ou de relógio, não de código: as *flags* `netem-*` do dublê de `tc` eram apagadas
pelo `podman-compose down` e contadas no glob dos asserts; pelos *shims*, o injetor real leva ~3,6 s reais para aplicar
o `netem` (medido com outra suíte rodando), então os casos que precisam do `netem` na amostra rodam na escala 5 com a
amostra a +60 s (`CS_NETEM_EFETIVA_MS`, só no modo de teste); o S27b esperava um `compose-up.log` não vazio. O S26e
mostrou uma corrida da primeira versão (o resíduo dos três nós lido no disparo demorava o bastante para o AC-5 do
dublê cair no meio) e levou à leitura só da raiz do nó do F3, presa pelo S26h.

## 4. Congelados

`git diff 6f366ac -- scripts/run-rebaseline.sh scripts/inject-*.sh scripts/amostrador-infra.sh scripts/vigia-h4.sh
analysis/ scripts/cria-caches.sh scripts/collect-resources.sh scripts/rebaseline_matriz.py workload/ cluster/` é vazio.
SHA-256: `inject-jitter.sh` `7e68962b…` (o da lista `003814e9` citado no B-40), `run-rebaseline.sh` `d95305fe…`,
`inject-netdegrade.sh` `4ebdd8a3…`, `inject-crash.sh` `531a98a1…`, `inject-partition.sh` `59dd2bee…`.

## 5. Parâmetros novos e origem

| Nome | Valor | Origem |
|---|---|---|
| `f3_no`, `f3_delay_ms`, `f3_jitter_ms`, `f3_distribuicao`, `f3_dur_s` | no `campanha-s3/2`, sem padrão | B-40 §4.3; no *rebaseline*: isn2, 20, 13, normal (PR §2), 251 (DT-011) |
| `NETEM_EFETIVA_APOS_S` | 2 s | C5 do *rebaseline* (`NETEM_EFETIVA_S` = 51 = 49 + 2); recusa pelo B-40 §4.3 |
| `EXITS_F3_SEGUEM` | 0 1 4 | C3/P2 do Gestor (23/10), `INJ_EXITS_SEGUEM` do `run-rebaseline.sh` |
| dead-man do F3 | `f3_dur_s` + 90 s | `DEADMAN_MARGEM_S` (V-S6A), igual ao 341 s do DT-011 |
| `--swap-limite-kb` | sem padrão (o pré-registro dá 1048576) | pedido do W-6c; medida da campanha de 05/11 |

## 6. Pontos que precisam de decisão

1. **Swap no S3 (P-56): decidido.** O coordenador, sob autorização do Gestor, decidiu parar a fase quando o swap
   continua ≥ N depois da recriação; está implementado com o exit 11 (novo). Fica como observação: o S3 já recria o
   *cluster* antes de toda execução, então a leitura do portão mede o que sobrevive à recriação, e o
   `swapoff -a && swapon -a` (que esvazia o swap com RAM livre) não entrou.
2. **Conferência do `netem` a +2 s como recusa.** No *rebaseline* era só relatório; segui o B-40 §4.3. As 9 F3 do
   *rebaseline* passariam todas.
3. **B-40 §4.3, texto do B1 no F3 (P-54):** o injetor não "falha na remoção", sai com 0 e diz "OK". O código trata
   isso; o texto deve dizer.
4. **N1 na análise:** o orquestrador registra os dois instantes, mas a regra (fim nominal ou `end` do injetor nas
   execuções com o aviso do B1) é do Gestor no B-40 §9 e do W-5b.
5. **O1 da V-W6b** (AC-5 antes do disparo para a fase com 6, inclusive no F3): mantido como está; não foi pedido.
6. **`--exigir-trilha-completa` e `--swap-limite-kb` no *bundle*:** mudar qualquer um no meio de uma fase recusa a
   retomada (é mudança de protocolo). Se o Gestor preferir que só o arquivo de parâmetros conte, sai da linha `opcoes:`.
7. **`trilha_incompleta` só olha `decisoes`.** O `observaveis-rep-NNN.jsonl` também tem `fim` e `linhas_perdidas`; o
   pedido e o B-7 falam da trilha de decisões. Estender é uma linha na conferência, se o Gestor quiser.
8. **Trilha ausente com a opção:** a execução já era RECUSADA ("sem decisoes-rep-001.jsonl") e passa a
   DESCARTADA-INFRA. As duas vão a reposição; muda só a contagem por tipo.
9. **`pkill` no `limpar`:** guardado por `command -v`. Na VM (procps) existe; se faltar, o `trap` espera o `sleep` do
   injetor, até 251 s com o `netem` aplicado e o *dead-man* armado.
10. **Renumeração na integração:** o integrador usou o P-56 para o I-1, então o P-56 deste registro (swap) passa a
    P-57 (aviso do coordenador, 06/11). O destino de P-54, P-55 e P-57 fica com o integrador.
11. **Integração:** depois da calibração e antes da avaliação, com a suíte completa rodada com o *host* livre. A ponta
    para integrar é a deste relatório; a VM roda hoje `1f65a02`, sem o W-6c.

## 7. Documentação atualizada

`docs/implementacao.md` (TD-014 itens 21 a 24 e o fluxograma), `docs/testes.md` (seção W-6c),
`docs/registro-de-problemas.md` (P-54 a P-57) e este relatório.
