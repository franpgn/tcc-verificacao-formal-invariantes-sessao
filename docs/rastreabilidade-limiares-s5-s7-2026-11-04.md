# Rastreabilidade dos limiares numéricos do S5, do S6 e do S7 (gate v4), 2026-11-04

Autor: Senior. Pedido do Gestor (04/11): as recomendações da V-s5sh e da V-s6s7sh só são aceitas se cada número usado como critério vier de fonte do projeto. Este documento classifica cada número e propõe substituto rastreável para os que não têm fonte. Não editei scripts, não acessei a VM e não houve push.

## 1. Objeto, versão lida e categorias

Li o código na ponta `2ac9ec8` da branch `test/b29-gate-v4` (`git show 2ac9ec8:<arquivo>`). As linhas citadas são dessa versão. Arquivos: `runs/vm-s5-2026-10-28/s5.sh`, `runs/vm-s6-2026-11-04/s6.sh`, `runs/vm-s7-2026-11-04/s7.sh`, `runs/gate-b29/g5b.sh` (igual na árvore de trabalho), e, por serem chamados por eles, `runs/gate-netdegrade/l1.sh`, `runs/gate-netdegrade/l-lib.sh`, `runs/gate-b29/g56-lib.sh`, `runs/vm-s2-2026-10-27/portao51.sh` e `scripts/inject-netdegrade.sh`.

Durante a leitura o Worker fez o commit `b843530` (só `s5.test.sh`) e deixou alterações não commitadas em `s5.sh`, nos roteiros e nos testes. As do `s5.sh` tratam de `--boot-esperado` e da regra do `go.log`, sem limiar novo. Duas alterações em curso trazem número novo e estão nos itens 33 e 46: o limite de 14 s do A6, que aparece como `PERSISTE_MIN_S=$(( PERSISTE_S - 1 ))` nos roteiros, e a FALHA do D-1 do S7 com `t_fire + 5 s`.

Categorias, na ordem do Gestor:
- **(a)** bibliografia do projeto (`monografia-atual/bibliografia.bib`, leituras em `tcc/leituras/`);
- **(b)** decisão pré-registrada e datada;
- **(c)** derivação matemática de (a)/(b) ou do código já validado;
- **(d)** valor medido nos brutos;
- **(e)** prazo operacional que não decide VÁLIDO/INVÁLIDO;
- **(f)** sem base.

Quando um item tem mais de uma, a tabela dá a primária (a mais alta da lista) e as de apoio.

Fontes (b) citadas, com as abreviações usadas na tabela:

| Abreviação | Documento | Data |
|---|---|---|
| DS §5.x | `docs/decisoes-senior-2026-10-16.md` (branch `docs/decisoes-senior-2026-10-16`, commit `ff68205`; cópia em `wt-senior-decisoes/docs/`), §5.1 linha 368, §5.5 linha 437, §5.6 linhas 456-470, §5.7 linhas 472-487 | 2026-10-16 |
| PF T-C2, PF §8.x, PF Q-8 | `docs/plano-fechamento-etapa-formal-2026-10-15.md` (em `wt-plano-fechamento/docs/`), tarefa T-C2 (tabela L-0..L-6), §8.1, §8.2 e decisão Q-8 da §2.2 | 2026-10-15 |
| DR | `docs/degradacao-de-rede.md`, seções "Severidades" e "Verificação de efetividade" (interface congelada no V-C1) | 2026-10-16 |
| V-S4 | `runs/vm-s4-2026-10-28/V-S4.md`, "O que o S5 precisa", itens 7 e 8, e P-2 | 2026-10-28 |
| V-s6s7sh | `runs/vm-s6-2026-11-04/V-s6s7sh.md`, dúvida 1 (análise do A6) | 2026-11-04 |
| MET T | `monografia-atual/Metodologia.tex:268`, linha da tabela de parâmetros com "p50, p95, p99 obrigatórios" (`dean_barroso_tail_2013`, `decandia_dynamo_2007`, `google_sre_slo`) | versão oficial atual |

Brutos (d) citados: `runs/vm-s4-2026-10-28/g1-rep4-copia/saude-log.csv`, `.../g1-rep4-copia/inj.log` e `marcadores.txt`, `runs/gate-b29/g1-rep3/saude-log.csv`, `runs/vm-s3-2026-10-28/02-pos-l0c-leitura.log`, `docs/relatorio-tb-v3-execucao-2026-10-16.md:36-37` (instantes de vista da REP=3), `runs/vm-s1-2026-10-27/13-latencia-ssh.log` e `runs/vm-s2-2026-10-27/00-ssh-latencia-e-ambiente.log`.

## 2. Tabela

Legenda da coluna "Papel": **C** = entra em VÁLIDO/INVÁLIDO/FALHA; **P** = parâmetro de injeção; **O** = prazo ou período operacional.

### 2.1 S5 (L-1 e L-2)

| # | Número | Onde (`2ac9ec8`) | Papel | Cat. | Referência exata |
|---|---|---|---|---|---|
| 1 | perda 20 % (`--loss 20`, `LOSS=0.20`) | `l1.sh:7`; `s5.sh:47` | P, C | **b** | PF T-C2, linha L-1 ("`loss 20%` isn3→pares por 60 s"); DS §5.6 linha 460. É o N3 da escala (item 50) |
| 2 | janela de 60 s | `l1.sh:7` | P | **b** | PF T-C2 L-1; DS §5.6 linha 460 |
| 3 | 300 pings por par (`--ping-count 300`, `PING_N=300`) | `l1.sh:7`; `s5.sh:47` | P, C | **b** | PF T-C2 L-1 ("≥ 300 amostras"); DS §5.6 linha 463 ("n = 300") |
| 4 | intervalo de 0,2 s dos pings | `inject-netdegrade.sh:760`; `s5.sh:297, 407` | P | **b** | PF T-C2 L-1 ("ping a 0,2 s") |
| 5 | 39 a 81 perdas em 300 | `s5.sh:47, 132-140` | C | **b** (+c) | DS §5.6 linha 463: "39 a 81 perdas (13% a 27%) dentro de 3 sigma". Derivação: 60 ± 3·√(300·0,2·0,8) = 60 ± 20,78, isto é, [39,22; 80,78]; os inteiros dentro são 40 a 80. O §5.6 arredondou para fora. Ver 3.3 |
| 6 | razão `dropped/(Sent+dropped)` a 3σ de 0,20 | `s5.sh:142-148` | C | **b** (+c, a) | DS §5.6 linha 464 ("próxima de 20%"); DR "Verificação de efetividade" (tolerância `max(3σ, 2/n)`, a mesma do injetor). A hipótese binomial vem do modelo `loss random`, que descarta cada pacote de forma independente (`netem_manpage`, seção LOSS; a seção não foi conferida nesta rodada) |
| 7 | piso `2/n` da tolerância | `s5.sh:146` | C | **f** (sem efeito) | Vem do injetor (DR), sem justificativa escrita. Com perda de 20 % ele só valeria com n ≤ 2 pacotes, porque 3σ = 1,2/√n > 2/n para n ≥ 3. **Inativo no S5** |
| 8 | `dropped > 0` | `s5.sh:145` | C | **b** | DS §5.6 linha 464 |
| 9 | ≥ 2 amostras de `tc -s qdisc/filter` na janela | `s5.sh:164` | C | **b** | DS §5.6 linha 470 ("mais de uma amostra durante a janela"); V-S4 item 3 |
| 10 | amostras de `tc` em +10, +30 e +50 s | `s5.sh:43, 301` | O | **c** | Três instantes dentro da janela de 60 s (item 2), o que garante as 2 do item 9 com folga |
| 11 | **`FOLGA_LAT_S` = 1 ms** ("mediana durante ≤ p95 da base + 1 ms"), aplicada a REST isn3, Hot Rod isn3 (conexão TCP), REST de controle isn1, ssh (conexão TCP) e RTT do ping | `s5.sh:48, 166-179, 195` | C | **f** | O §5.6 (linha 465) diz "latência dentro da dispersão da linha de base". Os 1 ms são "a leitura do Worker" (`ROTEIRO-S5.md:78`; V-s5sh P-2). Nenhum documento os deriva. Análise na seção 3 |
| 12 | p95 da base como limite da dispersão | `s5.sh:90-93, 170` | C | **b** | MET T (p95 é percentil obrigatório do projeto). Ressalva: as fontes daquela linha justificam o p95 como métrica de latência, não como limite de aceitação |
| 13 | mínimo de 3 amostras na base e 1 durante | `s5.sh:170, 412` | C | **f** | Sem fonte. Com o `stats()` do `s5.sh`, o p95 de n < 20 amostras é o máximo, então "p95" só existe de fato com n ≥ 20 |
| 14 | 0 perdas no ping do host a isn3 (base e janela) | `s5.sh:188-193` | C | **b** | DS §5.6 linha 460 ("ping do host a isn3 sem degradação"); PF T-C2 L-1 ("sem degradação mensurável") |
| 15 | HTTP 200 em 100 % das amostras de isn3 (base e janela); 0 conexões ssh recusadas | `s5.sh:180-186` | C | **b** | DS §5.6 linha 465 (Hot Rod/SSH "sem degradação"); V-S4 item 6 |
| 16 | tempos-limite das sondas: `curl --connect-timeout 2 -m 2` (ssh e porta Hot Rod), `curl -m 3` (REST), `ping -W 2` | `s5.sh:250, 259, 297, 407` | O (e definem ERRO/000) | **e** | Mantêm o laço periódico. Também definem "conexão não aceita" no item 15. Qualquer limite muito acima da latência medida dá o mesmo veredito, e a latência medida é de milissegundos: REST /health de isn3 entre 2,5 e 3,5 ms e máximo de 8,8 ms (V-S4 item 6, de `g1-rep4-copia/janela.log` e `hotrod/indice.csv`) |
| 17 | intervalo máximo do saúde-log ≤ 2 × o nominal | `s5.sh:197-203`; `s6.sh:220-226`; `s7.sh:177-183` | C | **b** (+d) | V-S4 item 7 e P-2 (2026-10-28). Medido: máximo de 2,029 s por porta com nominal de 2 s na REP=4R (`g1-rep4-copia/saude-log.csv`, n = 86 intervalos por porta) e 2,036 s na REP=3 (`g1-rep3/saude-log.csv`, n = 71). Folga de ~2 s até o limite de 4 s |
| 18 | nominal do saúde-log `SAUDE_S` = 2 s | `s5.sh:40`; `s6.sh:33`; `s7.sh:35` | P | **b** | DS §5.5 linha 437 ("log de saúde a 2 s") |
| 19 | linha de base `BASE_S` = 60 s | `s5.sh:39` | P | **c** | Igual à janela (item 2): base e janela ficam com o mesmo número de amostras (~30 por fonte) |
| 20 | 300 pings na base e 250 na janela | `s5.sh:41-42` | P | **c** | 300 × 0,2 s = 60 s = `BASE_S`; 250 × 0,2 s = 50 s cabem na janela de 60 s |
| 21 | o coletor espera o GO por 900 s | `s5.sh:44, 292` | O | **e** | Prazo de presença do Gestor (V-s5sh R-5). Se estourar, o bloco sai INVÁLIDO por falta de dado da janela, e não por algo medido |
| 22 | 30 s depois do `end`, espera de 120 s pelo coletor, desistência em 900 + 600 s | `s5.sh:45-46, 305` | O | **e** | Prazos de coleta e de encerramento |
| 23 | dead-man do L-1: `armar 60` → 60 + 90 = 150 s (até ~210 s com `AccuracySec` de 1 min) | `l-lib.sh:32-34, 42` (verbatim) | O | **e** | Rede de segurança, sem papel no veredito (V-S3 R-4) |

### 2.2 Portão 5.1 (comum ao S5, ao S6 e ao S7)

| # | Número | Onde | Papel | Cat. | Referência |
|---|---|---|---|---|---|
| 24 | ≥ 120 s de sequência boa | `s5.sh:38`; `s6.sh:32`; `s7.sh:34` | C | **b** | DS §5.1 linha 368 ("por >= 120 s") |
| 25 | 0 mudanças de vista nos últimos 5 min | `portao51.sh` (linhas finais); `avaliar_portao` | C | **b** | DS §5.1 linha 368 |
| 26 | duração mínima do portão de 130 s | `s5.sh:38` (e iguais) | O | **c** | O portão amostra a cada 5 s (`portao51.sh:32`). Uma sequência boa ≥ 120 s exige ≥ 125 s de execução, e 130 s dá uma amostra a mais |
| 27 | fim do portão pós ≥ fim do bloco + 345 s | `s5.sh:38`; `s6.sh:32`; `s7.sh:34` | O | **e** | 300 s da regra do item 25 mais 45 s de folga (`s4.sh:32`). Só define quando o portão termina. O critério é o do item 25 |

### 2.3 S6 e S7 (G-5b) e `g5b.sh`

| # | Número | Onde | Papel | Cat. | Referência |
|---|---|---|---|---|---|
| 28 | janela de 120 s | `g5b.sh:22`; `s6.sh:38`; `s7.sh:40` | P | **b** (+d, a) | DS §5.7 linha 479 ("`--duration 120` (maior que a detecção)"). Detecção medida: maioria em +46,4 s e isn3 em +39,8 s na REP=3 (`relatorio-tb-v3-execucao-2026-10-16.md:36-37`); +43,5 s na REP=4R (V-S4 item 2). O mecanismo FD_ALL3 está em `jgroups_manual_5` §6.8.4 |
| 29 | KILL em ≥ +70 s do `start` (A4) | `g5b.sh:22, 126`; `s6.sh:149-152`; `s7.sh:149-151` | P, C | **b** | DS §5.7 linha 479 ("aprox. +70 s") |
| 30 | margem do dead-man de 90 s (S6) | `g5b.sh:22`; `s6.sh:38` | P | **b** | DS §5.7 linha 479 |
| 31 | margem do dead-man de 25 s (S7) | `g5b.sh:22`; `s7.sh:40` | P | **b** | DS §5.7 linha 484 ("por exemplo, janela de 120 s e margem de 25 s") |
| 32 | persistência ≥ 15 s (A6, B1/A6) | `g5b.sh:23, 140-148`; `s6.sh:38, 164`; `s7.sh:40, 160` | C | **b** | DS §5.7 linha 479 ("persiste por >= 15 s") |
| 33 | persistência ≥ 14 s, com [14, 15) s como RESULTADO (alteração em curso) | árvore de trabalho: `ROTEIRO-S6.md`/`ROTEIRO-S7.md` ("`PERSISTE_MIN_S=$(( PERSISTE_S - 1 ))`") | C | **c** | Derivado do código validado: o laço de `g5b.sh:140-147` (`date +%s`, `-lt 15`) só garante `t_persistencia_ok − t_kill > 14 s` (V-s6s7sh, dúvida 1). O roteiro em edição registra a decisão do Gestor de 2026-11-04. É desvio explícito do texto do item 32 |
| 34 | disparo em [dur+margem, dur+margem+10 s] = [145, 155] s (B3) | `g5b.sh:24, 178-182`; `s7.sh:41, 192-198` | C | **b** (+d) | DS §5.7 linha 485. Medido com `AccuracySec=1s` no S3: atraso de 0,341 s pelo journal e 1,33 s pela sonda de 1 s (V-S3 item 10; `02-pos-l0c-leitura.log:32-33`) |
| 35 | prazo do dead-man de 210 s e 145 s (D-3) | `s6.sh:61`; `s7.sh:65` | C | **c** | dur + margem (itens 28, 30 e 31) |
| 36 | `AccuracySec=1s` (D-3) | `g5b.sh:15-18, 52`; `s6.sh:259`; `s7.sh:277` | P, C | **b** (+d) | Achado do S0 (cabeçalho do `g5b.sh`: +3, +3, +52, +41 s de atraso com o padrão de 1 min no WSL); V-S3 item 7 (R-1 da V-S2 fechada) |
| 37 | o `g5b.sh` desiste em prazo + 10 + 30 s | `g5b.sh:24, 167`; `s7.sh:41` | O | **e** | Depois de 155 s o B3 já é FALHA. Os 30 s só decidem quando o trap do `g5b.sh` cura |
| 38 | `event=start` em até 40 s do lançamento do injetor | `g5b.sh:23, 116` | C (FAIL do `g5b.sh`) | **f** | Sem fonte. Medido no S4: GO às 07:12:13 e marcador `start` em 07:12:14,52 (`g1-rep4-copia/inj.log:1`, `marcadores.txt:1`), menos de 2 s. O veredito é o mesmo para qualquer limite bem acima disso |
| 39 | split confirmado em até 100 s do `start` | `g5b.sh:23, 118-123` | C (FAIL) | **f** | Sem fonte. O valor tem de ficar entre a detecção medida (≤ 46,4 s, item 28) e o fim da janela (120 s, item 28), para que o KILL venha com o injetor vivo. Qualquer valor nesse intervalo dá o mesmo veredito para o comportamento medido |
| 40 | MergeView e `HEALTHY` x3 em até 150 s do heal ou do disparo | `g5b.sh:24, 92-95, 158-160, 196-198`; `g56-lib.sh:17-25` | C (FAIL) | **f** | Sem fonte. **Sensível ao valor**: o tempo de recomposição medido foi de 44 a 48 s nas REPs em cluster novo, mas de **188 s** na REP=2 (`Desenvolvimento.tex:88`). Um evento como o da REP=2 faz o `g5b.sh` falhar e o bloco sair INVÁLIDO |
| 41 | intervalos de espera do `g5b.sh` (2 s no split e na persistência, 1 s no disparo, 60 s pelos órfãos) | `g5b.sh:122, 144, 174, 207` | O | **e** | Períodos de amostragem. O de 1 s entra na derivação do item 45 |
| 42 | série de `tc` a cada `TC_S` = 2 s | `s6.sh:34`; `s7.sh:36` | O | **e** | Só define quantas amostras o D-2 tem. O D-2 exige ≥ 1 por intervalo |
| 43 | espera do GO de 900 s, coletor + 900 s, espera do coletor de 60 s, pós-falha prazo + 60 s com amostra a cada 5 s | `s6.sh:35-40`; `s7.sh:37-43` | O | **e** | Prazos. A espera pós-falha só registra a cura do dead-man |
| 44 | D-2: folga de 5 s depois de `t_start` e de `t_heal`/`t_fire` | `s6.sh:39, 244-253`; `s7.sh:42, 266-275` | C | **f** | Sem fonte. Ver 4.4 |
| 45 | D-2 do S7: até `t_fire − 2 s` | `s7.sh:42, 268` | C | **c** | O `t_fire` é a primeira sondagem do `g5b.sh` sem `clsact`, com sondagens a cada 1 s (`g5b.sh:170-175`). A remoção real cai em (t_fire − 1 s − ε, t_fire], e uma amostra do coletor nesse trecho pode ler 0 legitimamente. 2 s = ⌈1 s + ε⌉, com ε ≈ 6 ms (duração de um `sudo nsenter tc qdisc show`, `03-sudo-pre-armar-e-pos.log`) |
| 46 | D-1 do S7: ≥ 1 `del` da unit entre `t_kill` e `t_fire + 5 s` | `s7.sh:262` (LEITURA HUMANA na ponta; FALHA na alteração em curso, `ROTEIRO-S7.md`) | C (em curso) | **f** | Os 5 s não têm fonte. Ver 4.5 |
| 47 | LEITURA "espera-se ≤ 2 s" entre `Started <unit>` e `t_fire` | `s7.sh:296` | não decide | **e** | Só leitura humana |

### 2.4 Escala de severidades N1..N5

Nenhuma severidade além de N3 entra no S5, no S6 ou no S7. A escala é parâmetro da campanha (PF §8.1, matriz L).

| # | Nível | Onde | Papel | Cat. | Referência |
|---|---|---|---|---|---|
| 48 | N1 = 1 % e N2 = 5 % | `inject-netdegrade.sh:289-290`; DR "Severidades" | P (campanha) | **b** | PF Q-8 e PF §8.1 ("hipótese de partida"; T-C2/L-4 ajusta). **Sem citação bibliográfica** |
| 49 | N3 = 20 % | `inject-netdegrade.sh:291` | P | **b** | Como 48, e PF T-C2 L-1 (item 1). **Sem citação bibliográfica** |
| 50 | N4 = 50 % | `inject-netdegrade.sh:292` | P (campanha) | **a** (+b) | `bailis_kingsbury_network_2014`, p. 50 (Dean: racks com "50% packet loss"; `leituras/REF-034...txt:214-220`); PF §8.2 H-L3 |
| 51 | N5 = 99,9 % | `inject-netdegrade.sh:293` | P (campanha) | **a** (+b) | `bailis_kingsbury_network_2014`, p. 52 (Freistil IT: "50%–100% packet loss", `REF-034...txt:474-477`); PF §8.2 H-L4 (≈ partição) |

**A escala tem citação só nos extremos.** N4 e N5 estão na faixa relatada por Bailis e Kingsbury. N1, N2 e N3 são valores de engenharia pré-registrados como hipótese, sem fonte. A monografia imprime os cinco valores (`Desenvolvimento.tex:197`) e cita Bailis e Kingsbury (p. 50-53) para a faixa de 50 % a 100 %. Para N1 a N3, a nota adequada é a mesma que a T18 já usa para o F3, "valor de engenharia, sem fonte" (`Metodologia.tex:274`). Isso fica para os agentes acadêmicos.

### 2.5 Contagem

| Categoria | Itens (primária) | Quantos |
|---|---|---|
| (a) | 50, 51 | 2 |
| (b) | 1, 2, 3, 4, 5, 6, 8, 9, 12, 14, 15, 17, 18, 24, 25, 28, 29, 30, 31, 32, 34, 36, 48, 49 | 24 |
| (c) | 10, 19, 20, 26, 33, 35, 45 | 7 |
| (d) | nenhum como primária; apoio em 16, 17, 28, 34, 36, 38, 39 e 40 | 0 |
| (e) | 16, 21, 22, 23, 27, 37, 41, 42, 43, 47 | 10 |
| (f) | 7, 11, 13, 38, 39, 40, 44, 46 | 8 |
| **Total** | | **51** |

## 3. A regra de latência: o que o §5.6 pede e o que os 1 ms fazem

### 3.1 O que a regra mede de fato

O §5.6 (linha 465) pede "latência dentro da dispersão da linha de base". A regra atual aceita quando a mediana da janela é ≤ p95 da base + 1 ms. Os dois termos têm naturezas diferentes. O p95 vem da própria base do bloco, e a constante é absoluta, a mesma para os cinco canais.

Tamanho medido de cada canal:
- REST `/health` de isn3: 2,5 a 3,5 ms, com máximo de 8,8 ms (V-S4 item 6). Nesse canal, 1 ms equivale a ~30 % da mediana.
- Conexão TCP à porta Hot Rod (127.0.0.1:11224), conexão TCP ao sshd da VM e RTT do ping do host a isn3: os brutos **não têm nenhuma medida** antes do S5 (nenhum `saude.csv` com `t_connect` existe em `runs/`). São handshakes de kernel em loopback ou na ponte local, da ordem de décimos de milissegundo. Nesses canais, 1 ms é várias vezes o valor típico, e a regra vira "a mediana não subiu 1 ms".

**Conclusão: com os 1 ms, a regra não cumpre o §5.6 nos quatro canais de sub-milissegundo.** No REST ela fica próxima do texto, mas a folga não sai da base.

### 3.2 Sem a constante: falso INVÁLIDO e falso VÁLIDO

Proposta: **mediana da janela ≤ p95 da base**, com o p95 calculado no mesmo bloco.

- **Falso INVÁLIDO sem efeito real (base e janela da mesma distribuição).** Para a mediana de n_d amostras passar o p95 de n_b amostras, mais da metade da janela precisa cair acima da segunda maior amostra da base. Por simulação (2·10⁵ réplicas, script em scratchpad, não versionado): com n_b = n_d = 30, que é o caso do saúde-log, a probabilidade é ~5·10⁻⁶ por canal; com n_b = 300 e n_d = 250, o caso do ping, não houve nenhuma ocorrência em 5·10⁴ réplicas. **A constante não protege contra falso INVÁLIDO nesse caso, porque ele já é desprezível.**
- **Falso INVÁLIDO com deriva entre base e janela.** É o único risco real de tirar a constante. A base é colhida ~3 a 4 min antes da janela, e uma mudança de carga no host ou na VM pode deslocar a mediana de um canal de dispersão estreita. Não há dado para estimar isso antes do S5. Mas a deriva aparece também nos dois canais de controle (REST de isn1 e ssh), que o F4 não toca. Nesse caso o INVÁLIDO é o correto: o bloco não consegue separar efeito do filtro de efeito do ambiente.
- **Falso VÁLIDO.** Diminui. Um deslocamento real da mediana entre (p95 − mediana) da base e 1 ms passa a ser detectado. Isso só pesa nos canais de sub-milissegundo.

### 3.3 O ssh

O critério automático do ssh **não mede a sessão ssh**. Ele mede o tempo de conexão TCP ao sshd, feita de dentro da VM ao próprio IP (`tcp_s`, `s5.sh:247-251` e `266`). Esse handshake é feito pelo kernel e tem a variância de qualquer conexão TCP local. A sessão do Windows à VM, que de fato varia muito (205 a 1058 ms em `vm-s1-2026-10-27/13-latencia-ssh.log` e `vm-s2-2026-10-27/00-ssh-latencia-e-ambiente.log`), entra só como LEITURA HUMANA (`s5.sh:237`). **Tirar a constante não traz essa variância para o critério.** Além disso, o filtro do F4 fica no netns de isn3 e não tem como tocar o sshd da VM. O ssh funciona como canal de controle de carga.

### 3.4 Limite que vale com ou sem a constante

A frase "um filtro que pegasse o tráfego do host atrasaria a maioria das requisições" (`ROTEIRO-S5.md:78`) vale para o REST, porque uma requisição HTTP põe vários pacotes na saída de isn3, e com 20 % de perda a maioria das requisições perde algum. Ela **não vale** para a conexão TCP à porta Hot Rod. Ali isn3 envia um pacote só (o SYN-ACK), então só ~20 % das conexões atrasariam, cerca de 1 s cada, e a mediana não se mexe. Nenhuma regra de mediana pega esse caso. Uma regra de cauda calibrada no mesmo nível de 3σ do §5.6, isto é, P(falso INVÁLIDO) ≤ 0,135 % calculada pela distribuição exata do número de amostras da janela acima do p95 da base, aceitaria até 10 de 30. O poder contra 20 % de conexões atrasadas seria de ~13 %, o que não serve.

Por isso **a prova de que o filtro não pega o tráfego do host é estrutural, e não a latência**:
- o critério 6 (`s5.sh:150-164`) exige nas amostras de `tc -s filter` exatamente os dois `match` de destino isn1 e isn2 para `1:3`, o que exclui o host por construção;
- o ping do host com 0 perdas (item 14) detectaria com certeza um filtro que pegasse o host: P(0 perdas em 250 | 20 %) = 0,8²⁵⁰ ≈ 5·10⁻²⁵.

A latência fica como evidência secundária de degradação geral, por exemplo CPU da VM. Para esse papel, "dentro da dispersão" sem constante é a leitura literal.

## 4. Propostas para os itens (f)

### 4.1 Item 11: `FOLGA_LAT_S` = 1 ms

- **Substituto:** `FOLGA_LAT_S=0`, isto é, mediana da janela ≤ p95 da base, nos cinco canais. Fonte: DS §5.6 linha 465 (b) e p95 do projeto (MET T, b). Não sobra número sem fonte.
- **Efeito:** falso VÁLIDO cai nos canais de sub-milissegundo. Falso INVÁLIDO fica em ~5·10⁻⁶ por canal sem deriva. Com deriva, o INVÁLIDO aparece também nos controles e está correto como "ambiente instável".
- **Quando:** antes do S5. Decidir depois de ver os dados seria escolha a posteriori. O `s5.sh` já está aberto no Worker (Q-11), então o custo marginal é uma constante e os casos de teste da fronteira do item 7 de `s5.test.sh`. O hash muda de qualquer forma.
- **Alternativa, se o Gestor preferir não mexer:** manter os 1 ms e registrar no relatório do S5 que a regra cumpre o §5.6 no REST mas não nos canais de sub-milissegundo, e que a inocuidade do filtro para o host se apoia nos itens 6 e 14.
- **Não recomendo** a regra de cauda "no máximo 20 % das amostras acima do p95 + 1 ms" sugerida na V-s5sh P-2. Os 20 % e os 1 ms não têm fonte, e a versão calibrada (3.4) tem pouco poder com n = 30.

### 4.2 Item 13: mínimo de 3 amostras na base

- **Substituto:** n_base ≥ 20 por canal. Derivação (c): no `stats()` (`s5.sh:90-93`), k = ⌈0,95·n⌉, e para n < 20 o "p95" é o máximo. Com 20 ou mais, ele é de fato um quantil abaixo do máximo. Com `BASE_S`/`SAUDE_S` = 30, a base real passa folgada. O mínimo de 1 amostra na janela já é implicado pelo critério do item 17: intervalos ≤ 4 s numa janela de 60 s dão ≥ 15 amostras por fonte.
- **Efeito:** nenhum numa execução normal. Só recusa uma base truncada que hoje passaria com 3 amostras.

### 4.3 Itens 38, 39 e 40: tempos-limite do `g5b.sh`

O `g5b.sh` está congelado pelo hash conferido na VM no S3 (`6f50e3e3…0378`). Qualquer mudança refaz a cadeia de validação descrita na V-s6s7sh.
- **38 (40 s até o `start`) e 39 (100 s até o split):** **não mudar o código**. Basta registrar a faixa em que o veredito não depende do valor: para o 38, acima de ~2 s (medido no S4); para o 39, entre 46,4 s (maior detecção medida) e 120 s (fim da janela, b). Os dois valores estão dentro dessas faixas, e o veredito é o mesmo para qualquer valor nelas. Efeito sobre falso VÁLIDO e falso INVÁLIDO: nenhum para o comportamento medido.
- **40 (150 s até MergeView/`HEALTHY`):** aqui o valor importa, por causa dos 188 s da REP=2. Há dois substitutos rastreáveis. Um é o teto de convergência de 600 s do PF §8.3 (b), que só pode entrar se o `g5b.sh` for reaberto. O outro, recomendado agora, é pré-registrar antes do S6 que, se o `g5b.sh` falhar só pelo limite de 150 s e o MergeView de 3 membros e `HEALTHY` x3 aparecerem depois nos brutos (`05-vistas.log`, portão pós), o bloco sai INVÁLIDO para a ferramenta e o tempo é registrado como RESULTADO, como a REP=2. Efeito: nenhum caso de falso VÁLIDO, porque a cura e o merge são conferidos de forma independente pelo A8/B6 e pelo portão. O falso INVÁLIDO continua possível num evento raro, com o custo de refazer o bloco.

### 4.4 Item 44: folga de 5 s do D-2

- **Substituto:** 1 s nos dois lados do heal ou do disparo e 0 s depois do `start`.
  - Depois do `start`: o injetor aplica o `clsact` antes de gravar o marcador (`g1-rep4-copia/inj.log`: "particao aplicada" e marcador no mesmo segundo), e o `t_start` do `g5b.sh` vem depois do marcador (`g5b.sh:116-117`). Logo toda amostra depois do `t_start` deve ler 1, e a folga necessária é 0 (c).
  - Depois do `t_heal`: o carimbo é gravado antes de chamar o `--heal` (`g5b.sh:153-154`), e um heal medido levou 0,223 s (S3, `02-pos-l0c-leitura.log:33-38`, de 04:59:55,689 a 04:59:55,912; heal do `inject-netdegrade`, usado como aproximação do de partição). ⌈0,223⌉ = 1 s (d + c).
- **Efeito:** o D-2 confere mais amostras, ~2 a mais por intervalo, e o falso VÁLIDO cai pouco. O falso INVÁLIDO só aparece se o heal de partição levar mais de 1 s. Se o Gestor não quiser mudar o hash de novo, manter os 5 s é o lado conservador: tira amostras do teste e nunca cria falso INVÁLIDO. Nesse caso, registrar que o número não tem fonte e que o efeito dele é só diminuir a cobertura.

### 4.5 Item 46: `t_fire + 5 s` no D-1 do S7 (alteração em curso)

- **Substituto:** `t_fire + 1 s`. Derivação (c): o `sudo` registra o comando antes de executá-lo, e o `t_fire` é detectado depois da remoção. Então o `del` que curou tem instante de journal ≤ `t_fire`, a menos da latência de recepção do journald. A folga que cabe é a resolução do próprio `t_fire`, a sondagem de 1 s (`g5b.sh:174`).
- **Efeito:** o falso VÁLIDO cai, porque deixa de aceitar como cura um `del` da unit até 5 s depois do disparo. O falso INVÁLIDO só aparece se a recepção do journald atrasar mais de 1 s, o que não aparece nos brutos do S3 (mesma origem `_SYSTEMD_UNIT`, V-s6s7sh). Os 5 s também são aceitáveis no lado conservador, mas sem fonte.

### 4.6 Item 7: piso 2/n

Inativo no S5. Pertence ao injetor (`inject-netdegrade.sh:775`) e ao `s5.sh` (`146`). **Substituto:** nenhum agora. Ele só pesa no N5 (99,9 %), em que 3σ < 2/n com n = 300. Fica para a revisão do L-4, fora do S5 ao S7.

### 4.7 Item 5: 39 a 81 contra 40 a 80 (não é f, só coerência)

O injetor aplica o 3σ exato e sai com exit 6 em 39 ou 81, então o `s5.sh` nunca chega a aceitar esses dois valores: o critério "injetor rc=0" falha antes. Probabilidade exata de reprovar por acaso com perda real de 20 %: 0,19 % por par (39 a 81) ou 0,31 % (40 a 80), e para dois pares 0,38 % e 0,62 %. Trocar para 40 a 80 alinharia o `s5.sh` à derivação (c), sem efeito no veredito. Fica opcional.

## 5. Algum item (f) afeta resultado do TCC?

**Nenhum item (f) altera um número que o TCC reporte como resultado.** Os RESULTADOs do S5 (mudança de vista, `DEGRADED_MODE`, `TcpRetransSegs`) e os tempos do S6/S7, que são dado de segurança fora do conjunto do G-4 pelo DS §5.7, não passam por nenhum desses limiares.

O que eles afetam é a **validação das ferramentas de injeção**, e por ela o TCC é tocado em dois pontos:
1. **A afirmação de que as verificações foram concluídas** (`ApPlanoTCC2.tex:44`, `Desenvolvimento.tex:90`). Um falso INVÁLIDO só custa repetir o bloco. Um falso VÁLIDO deixaria entrar na campanha um injetor com defeito, e aí, sim, os resultados de F4 e de F2 seriam afetados.
2. **A premissa do F4 no texto:** "O tráfego do cliente não é afetado, porque o filtro casa apenas os endereços dos pares" (`Desenvolvimento.tex:197`). A regra de latência com 1 ms (item 11) é parte da evidência. Com a constante, ela sustenta só "não afetado além de 1 ms na mediana". A prova forte vem dos itens 6 (filtros) e 14 (ping sem perda), que não dependem de número sem fonte. Retirar a constante fecha o ponto pelo texto do §5.6.

Fora dos critérios, a escala N1 a N3 (itens 48 e 49) é parâmetro da campanha e aparece na monografia sem fonte. Isso afeta o desenho experimental do TCC, não o S5 ao S7, e é assunto do Escritor e do Bibliotecário.

## 6. Itens pedidos que não existem nesses arquivos

- **"20 s de estabilidade"**: não aparece em `s5.sh`, `s6.sh`, `s7.sh`, `g5b.sh`, `g56-lib.sh`, `g-lib.sh` nem `portao51.sh` na ponta `2ac9ec8`, e também não nas alterações em curso. A estabilidade exigida nesses blocos é a do portão (≥ 120 s, item 24).
- **"180 s"**: não aparece nesses arquivos. Está no ensaio do F1 (`wt-ensaio-f1/runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh:20`, "cluster que nao volta em 180 s do start"; `docs/prompt-worker-ensaio-f1-2026-11-04.md`, F-7), outra branch, fora deste escopo. Se o Gestor quiser, faço a mesma auditoria lá. O "180 s" do dead-man da REP=4 (`05-g1.log`, 90 + 90) é do S4.

## 7. Pontos para o Gestor

1. **Item 11:** retirar os 1 ms antes do S5 (recomendado, 4.1) ou manter com a ressalva registrada?
2. **Item 40:** aceitar o pré-registro de 4.3 (falha só pelo limite de 150 s = INVÁLIDO da ferramenta, com o tempo como RESULTADO), sem mexer no `g5b.sh`?
3. **Itens 44 e 46:** trocar por 1 s (derivado) agora, já que o Worker está mexendo no `s6.sh`/`s7.sh`, ou manter os 5 s como lado conservador sem fonte?
4. **Item 13:** subir o mínimo da base para 20 amostras?
5. **Escala N1 a N3:** encaminhar aos agentes acadêmicos a nota "valor de engenharia, sem fonte", como na T18?
