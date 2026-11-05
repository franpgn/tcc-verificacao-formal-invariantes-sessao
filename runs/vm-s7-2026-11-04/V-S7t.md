# V-S7t: validação da terceira tentativa do S7 (G-5b-B), 2026-11-05

Autor: Senior. Bloco rodado pelo coordenador de 01:24:58 a 01:36:13Z, com GO às 01:27:13Z (`go.log`, sha `f6483c5`), sob a autorização do Gestor de 2026-11-04. VM `172.27.114.62`, `boot_id` `f9e98b74-…` no início (`00-pre.log`) e no fim (`07-boot-fim.txt`), o mesmo do S5, do S6 e das duas tentativas anteriores. Cluster sem mudança (isn1-15077, isn2-28862, isn3-9749). `s7.sh` com sha256 `2385609e…`, o das tentativas anteriores. Bloco anterior: S6 `vm-20261104T223805Z` (`S6: VALIDO`).

Brutos em `vm-20261105T012458Z/`. Conferi no host: `MANIFEST.sha256` (47), `g5b-B.sha256` (24) e `copia-host.sha256` (54), todos OK. Fora do `MANIFEST` e dentro do `copia-host.sha256` ficam os acréscimos do coordenador: `checagem-pre-coordenador/` (portão de 600 s e PSI de io a 1 s) e `coleta-paralela-coordenador/` (`vmstat 1` e PSI de io e de CPU a 1 s, de 01:24:56 a 01:36:29Z).

Dados do diagnóstico do host e da VM entre as duas tentativas, que estavam só no *scratchpad*, foram copiados para `diag-io-2026-11-05/` com `MANIFEST.sha256` próprio: `typeperf` antes (23:55-00:30Z) e depois (00:43-01:13Z) das mitigações, PSI e `diskstats` da VM depois das mitigações, a lista de contadores e os *scripts* de leitura (os do coordenador e os meus, `h4.py`, `lista.py`, `taxa.py`).

O que rodei: leitura dos brutos, `sha256sum -c`, `s7.sh --avaliar` sobre a cópia (saída igual à `08-avaliacao.txt`, exceto a linha do carimbo) e os *scripts* acima em Python sobre os arquivos de PSI e de `typeperf`. Nada na VM, nada no host além de leitura. Nenhum push.

Veredito automático: `S7: VALIDO (19 criterios OK)`.

## 1. Condição de entrada (V-S7r §6, item 3)

A regra registrada antes do bloco era: só dar o GO se, nos últimos 10 min da checagem curta, não houver janela de 10 s com PSI io `full` ≥ 1,5 s.

| Item | Valor | Fonte |
|---|---|---|
| Checagem | `portao51.sh 600` de 01:14:21 a 01:24:22Z: 120 rodadas, `amostras_ruins=0`, `maior_sequencia_boa_s=600` | `checagem-pre-coordenador/portao51-600.log` |
| PSI na checagem | 618 amostras, maior intervalo entre amostras 1,004 s, 0 intervalos ≥ 1,5 s | `checagem-pre-coordenador/psi.txt` |
| Pior janela de 10 s | **1,17 s**, às 01:14:43 | idem |

**A condição de entrada foi cumprida.** A pior janela (1,17 s) fica no primeiro minuto da checagem e é a cauda do episódio provocado no host às 01:12:53-01:13:06 (seção 4.2). Do minuto 1 ao fim, nenhuma janela passou de 0,6 s.

Os outros itens da V-S7r §6 também foram seguidos. O passo 0 mostra `runs/gate-b29/g5b-B: inexistente`, ou seja, o diretório da segunda tentativa saiu do lugar. O nome de destino não aparece nos brutos, e o coordenador confirma se foi o `g5b-B.s7-invalido-20261104T232939Z`. `--bloco-anterior` e `--boot-esperado` foram os do S6. O `go.log` tem 4 linhas `go ` a mais sobre o `HEAD`, aceitas pela Q-12. A coleta paralela rodou durante o bloco.

Registro também um fato, sem efeito no S7. O critério de passagem do item 3 da V-S7r §5 ("nenhuma janela de 10 s com io `full` ≥ 1,5 s" nos 30 min medidos depois das mitigações) **não foi cumprido ao pé da letra**. Houve 3 janelas de 1,66 a 1,73 s, todas entre 01:13:00 e 01:13:06Z. O coordenador as atribuiu à própria atividade no host, e os contadores do host confirmam isso (seção 4.2). O S7 não dependia desse item, que era uma recomendação para decidir se valia repetir o bloco. A regra de entrada pré-registrada era a do §6 e foi cumprida.

## 2. Linha do tempo do bloco

Referência: `t_start` = 01:27:14,993Z (`cronologia.txt`). O `event=start` do injetor é de 01:27:14,341Z (`marcadores.txt`).

| Instante (UTC) | Fonte | Evento | Relativo a `t_start` |
|---|---|---|---|
| 01:27:13 | `go.log` | GO | −1,9 s |
| 01:27:13,939 / 13,948 | cronologia | `t_antes_armar` / `t_depois_armar`, **1 s depois do GO** (na V-S7r foram 28 s) | −1,05 s |
| 01:27:14,281-14,337 | journal do `sudo` | pré-limpeza, sonda e corte: 2 `del`, 2 `add clsact`, 6 `filter add`, todos da `session-84.scope` | −0,7 s |
| 01:27:54,057 | log de isn3 | `ISPN000094` `[isn3-9749\|9] (1)`; `DEGRADED_MODE` às 54,061 | **+39,06 s** (+39,72 s do `event=start`) |
| 01:28:00,854 / ,859 | isn1 / isn2 | `ISPN000094` `[isn1-15077\|9] (2)` | **+45,86 s** |
| 01:28:01,392 | cronologia | `t_split_confirmado`, 0,54 s depois do último sinal do JVM | +46,40 s |
| 01:28:25,531 | cronologia | `t_kill` (`rc_injetor=137`) | +70,54 s |
| 01:28:41,688 | cronologia | `t_persistencia_ok`, 8 amostras com `clsact` e sem `end` | +86,70 s (16,16 s depois do KILL) |
| 01:29:38,982 | journal | `Started tcc-heal-220823-17766.service` | |
| 01:29:39,156 | journal do `sudo` | `tc qdisc del dev eth0 clsact` com `_SYSTEMD_UNIT` = a unit | |
| 01:29:39,184 | cronologia | `t_fire` (sondagem de 1 s), 145,245 s depois de `t_antes_armar` | +144,19 s |
| 01:30:24,858-,861 | 3 nós | `MergeView::[isn1-15077\|10] (3)` | `del` + 45,70 s; `t_mergeview` (sondagem) = `t_fire` + 47,14 s |
| 01:30:26 | `rc.txt` | `g5b rc=0` | |

## 3. Leituras humanas

- **Detecção pelo JGroups.** isn3 viu a vista de 1 membro em +39,06 s do `t_start` e isn1/isn2 viram a de 2 em +45,86 s. Contadas do `event=start` do injetor, que é a origem das medições anteriores, são +39,72 s e +46,51 s. A faixa anterior ia de 39,8 a 46,4 s. Os dois valores ficam no limite dela, um 0,1 s abaixo e o outro 0,1 s acima, o que é coerente com o FD_ALL3 (*timeout* de 40 s contado a partir do último *heartbeat* recebido antes do corte, verificado a cada 8 s). É só `RESULTADO`.
- **Sondagem do `g5b.sh`.** Desta vez a confirmação do split saiu 0,54 s depois do último sinal do JVM. Na V-S7r, com o podman preso atrás do disco, foram 78 s. O preflight (GO até a armação) levou 1 s, contra 28 s na V-S7r. Nenhum comando podman do `g5b.sh` travou.
- **Cura pelo dead-man.** A unit começou 0,202 s antes do `t_fire` medido por sondagem (espera-se ≤ 2 s). O `del clsact` da unit veio 0,028 s antes do `t_fire`, dentro da resolução de 1 s da sondagem. O `--heal` da unit passou por `sudo podman inspect isn3` e levou 0,2 s, de 01:29:38,98 a 01:29:39,16 (`journal-unit.txt`). O disco não travou nessa hora, então o que a V-S7r chamou de risco (dead-man depende do podman) não apareceu.
- **Os outros `tc` de escrita.** O avaliador conta 8 além dos 3 `del`. Conferi no `05-sudo-journal.txt`: são 2 `qdisc add clsact` e 6 `filter add`, todos da `session-84.scope`, entre 01:27:14,287 e 14,337, antes do `t_start`. Ou seja, a sonda do injetor (filtros para `192.0.2.1`, depois removidos pelo 2º `del`) e o corte (4 filtros, para isn1 e isn2). Depois do `t_kill`, o único `tc` de escrita é o `del` da unit (D-1).
- **REST de isn3 na janela.** 97 amostras, mediana 2,3 ms, p95 3,1 ms, máximo 3,9 ms.
- **Saúde-log.** Intervalo máximo de 2,255 s (limite 4 s). 389 linhas.
- **Fusão.** O `MergeView` saiu 45,70 s depois do `del` da unit. O S7 anterior deu 43,9 s, o repetido 44,9 s e o S6 53 s. É dado de segurança, não `t_merge`.
- **Portões.** No portão pré, `maior_sequencia_boa_s=131` e 0 ruins. No pós, 342 s e 0 ruins, com vista estável nos últimos 5 min, caches vazios, netns limpos e 0 *timers*.
- **`AccuracySec`.** Vale a mesma ressalva das tentativas anteriores: a unit do S7 tem `AccuracySec=1s` e a de produção não. O S7 prova o corpo do dead-man de produção, não a precisão do timer de produção.

Concordo com os 19 OK. Nenhum deles depende de leitura que eu não tenha refeito.

## 4. Disco da VM durante o bloco

### 4.1 O bloco ficou limpo

PSI io `full`, a partir da `coleta-paralela-coordenador/psi.txt` (692 amostras, maior intervalo 1,006 s, nenhum ≥ 1,5 s):

| Trecho | Pior janela de 10 s | io `full` somado |
|---|---|---|
| Pré-GO (01:24:56-01:27:13) | 0,57 s | 2,1 s |
| **GO até o fim do `g5b.sh` (01:27:13-01:30:26)** | **0,53 s** | 3,3 s |
| Portão pós (01:30:29-01:36:13) | **5,17 s** (01:34:00) | 14,7 s |

### 4.2 O episódio do portão pós e o de 01:13

**01:33:50 a 01:34:27Z, no portão pós.** Houve 20 janelas de 10 s ≥ 1,5 s (24 ≥ 1,0 s), com pico de 5,17 s. No `vmstat`, `b`=1 e `wa` de 16 a 32 % (um dos 4 vCPUs esperando I/O) por ~15 s, com escrita de 100 a 470 KB/s. É do mesmo tipo dos episódios da V-S7r, **mais curto e mais leve**: ~25 s e io `full` de ~0,5 s por segundo, contra 176 s e ~0,8 s/s. O portão pós não perdeu nenhuma rodada (cadência de 5 s mantida, todas HEALTHY), e nada do S7 dependia desse trecho. Os contadores do host não foram gravados durante o bloco, então **a origem do episódio fica sem atribuição**.

**01:12:53 a 01:15:04Z, entre o diagnóstico e a checagem.** As 3 janelas ≥ 1,5 s do diagnóstico pós-mitigação (01:13:00-01:13:06) e a cauda de até 1,17 s no início da checagem são do mesmo evento. No mesmo segundo, o `typeperf` do host mostra o `PhysicalDisk` com fila de 200 pedidos (01:12:39Z) e de 134 (01:13:02Z), e a fila do AVHDX em 11 (01:12:59Z). É a atividade do próprio coordenador no host (cópia e `git push`), e **mostra que trabalho de disco no host ainda trava a VM depois das mitigações**. Na campanha, o H0 tem de valer também para cópias, `scp` e `git`, não só para TLC e `mvn`.

### 4.3 Antes e depois das mitigações do host

| Medida | Antes (V-S7r e diagnóstico 1) | Depois (diagnóstico 2, checagem e este bloco) |
|---|---|---|
| `PhysicalDisk` p99 de escrita / leitura | 305 / 310 ms (23:55-00:30Z) | 21 / 25 ms (00:43-01:13Z) |
| `Error Count` do AVHDX | 1 | 1 (sem erro novo) |
| PSI io `full` somado | 33,9 s em 10 min (checagem da V-S7r); 152 s em 12 min (bloco da V-S7r) | 14,6 s em 30 min; 8,5 s em 10 min; 20,5 s em 11,5 min |
| Janelas de 10 s ≥ 1,5 s | 76 em 10 min; 164 em 12 min | 3 em 30 min; 0 em 10 min; 20 em 11,5 min |
| Intervalo máximo do amostrador | 1,65 s; 5,74 s | 1,014 s; 1,004 s; 1,006 s |
| Pior janela de 10 s fora dos episódios | — | 0,69 s (01:35:55Z); fora isso, < 0,6 s nos ~71 min |

A melhora é grande, e o comportamento passou a ser outro: antes o disco ficava travado uma fração alta do tempo, e agora há silêncio com episódios curtos e raros. Em ~71 min de observação depois das mitigações houve **2 episódios** com janela ≥ 1,5 s, um provocado no host e outro sem atribuição. É com esse regime que o critério h4 é calibrado (complemento 3 do pré-registro, `wt-plano-ctrl`).

**Errata da V-S7r.** A V-S7r deu como pior janela do episódio do bloco "17 s" e como pior janela da checagem "2,5 s (10 amostras)". Esses números contam 10 amostras seguidas, e no bloco essas amostras atravessavam os buracos de 4,4 e 5,7 s do amostrador. Pela definição por relógio que o h4 usa (janela que termina numa amostra e começa na amostra mais antiga a no máximo 10 s dela), os valores são **9,32 s** no bloco e **2,45 s** na checagem. As conclusões da V-S7r não mudam.

## 5. Veredito

**S7 VÁLIDO.** Concordo com o veredito automático e com os 19 critérios. O dead-man disparou sozinho em 145,245 s desde antes de armar (no limite de 145 a 155 s), foi o único agente de cura (D-1), e o cluster voltou a 3 membros com `MergeView` nos 3 nós e `HEALTHY` x3. O corte durou 144,19 s e persistiu 16,16 s depois do `kill -KILL` do injetor. A condição de entrada pré-registrada na V-S7r §6 foi cumprida, e o bloco (GO até o fim do `g5b.sh`) rodou sem travamento de disco (pior janela de 10 s de 0,53 s).

Com isso, **S1 a S7 estão aprovados**. A cadeia do *gate* fecha aqui, e o S7 não é repetido. O PR §5 ("depois de S1..S7 aprovados") fica satisfeito para o *rebaseline*, desde que o complemento 3 (h4) esteja commitado antes do piloto.

## 6. Pontos que precisam de validação do Gestor

- **P-1.** Aceitar a V-S7t e dar o S7 por encerrado (S1..S7 aprovados).
- **P-2.** Registrar que o item 3 da V-S7r §5 não passou ao pé da letra (3 janelas de até 1,73 s, provocadas pelo coordenador no host) e que isso não afeta o S7, cuja condição de entrada era a do §6.
- **P-3.** Estender o H0 das noites e dos blocos de medição a toda atividade de disco no host: cópias, `scp`, `git`, compressão e o *push* que o Gestor autorizar ficam para as janelas H2 (seção 4.2).
- **P-4.** Registrar como covariáveis fixas do host para a campanha as mitigações aplicadas pelo coordenador: ASPM desligado na tomada, `ReTrim` feito, exclusão de `C:\Hyper-V` no Defender e *checkpoint* mantido. A exclusão no Defender é uma troca de segurança (era a M4 da V-S7r, condicionada a falha do item 3). Ela foi aplicada antes da medição, e o Gestor confirma se fica até o fim da campanha.
