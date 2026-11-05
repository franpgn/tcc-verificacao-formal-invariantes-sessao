# Complemento 3 ao pré-registro do *rebaseline*: critério h4, travamento de I/O ou pausa da VM (2026-11-04)

> Senior, escrito em 2026-11-05 ~02:00Z, com a data do dia de trabalho (04/11). **O critério h4 foi aprovado** pelo coordenador em
> nome do Gestor (P-3 da V-S7r), com o pedido de recalibrar os limiares pelos dados de 04/11. Esses limiares estão na
> seção 3. Esta versão tem **duas mudanças em relação à proposta da V-S7r §4.3**, ambas por conta dos dados e ambas
> **aguardando a confirmação do Gestor** (seção 9): a regra de parada da fase (seção 4.3) e o segmento do F1 (seção 4.2).
> Registro formal em `docs/decisoes-tecnicas.md`, **DT-018**.
>
> Complementa `docs/pre-registro-rebaseline-aprovado-2026-10-23.md` (PR), o complemento 1 (C1) e o complemento 2 (C2).
> **Nenhum deles é editado.** Este texto tem de estar commitado antes do piloto. Nenhum dado do piloto ou da campanha
> existia quando ele foi escrito. Os dados usados aqui são do *gate* (S7) e do diagnóstico do host, de 04 e 05/11.

## 1. Motivo

Na VM do *rebaseline*, o disco virtual travou mais de uma vez com o host em H0. O S7 caiu duas vezes por isso: uma pausa de
~10 s da VM inteira (`wt-gate-v4/runs/vm-s7-2026-11-04/V-S7.md`) e um travamento do disco de 176 s, em que a PSI io
`full` somou 145 s (`V-S7r.md`). Nesses intervalos, o podman, o journald e qualquer escrita em arquivo ficam parados.
Os JVMs continuam respondendo, mas a cauda de latência fica exposta: uma *thread* que grava log trava (V-S7 §1).

Os critérios do PR não percebem isso. K1 a K10 não olham a infraestrutura durante a execução, a parada (h) do C1 cobre só
*boot* e reinício de processo, e o silêncio no host (PR §5, C1 §5) não basta, porque o episódio da V-S7r aconteceu em
H0. Depois das mitigações do host (V-S7t §4.3), os episódios ficaram raros e curtos, mas não sumiram. Por isso entra o h4,
que tira da análise as execuções em que a VM travou e não olha nenhum desfecho medido.

## 2. Coleta (fora do *runner* congelado)

- **Amostrador** `scripts/amostrador-infra.sh` (novo, especificado no prompt
  `docs/prompt-worker-h4-amostrador-2026-11-04.md`). Roda **na VM, como root, numa unit própria do systemd**, iniciada
  antes do `go` de cada fase e parada depois do bloco de fim de fase do C1 §1.3. A cada 1 s, sem criar processo por
  amostra, ele grava uma linha com o relógio de parede, o `/proc/uptime`, os contadores `total=` de
  `/proc/pressure/{io,cpu,memory}` e a linha do disco da VM (`sda`) de `/proc/diskstats`.
- **Destino: `/run/tccdiag/<fase>-<UTC>/amostras.txt`.** `/run` é tmpfs, e o amostrador escreve na memória, não no disco
  que ele mede. Não se usa `/dev/shm`: com `RemoveIPC=yes` (padrão do logind), os objetos do usuário em `/dev/shm` são
  apagados quando a última sessão dele termina, e foi isso que fez a primeira coleta de 04/11 se perder. Os arquivos de
  root em `/run` não são afetados.
- **Espelho em disco.** Um segundo processo, separado do amostrador, acrescenta a cada 300 s o trecho novo a
  `~/rebaseline-infra/<fase>-<UTC>/amostras.txt`. Se o disco travar, só o espelho espera, e o amostrador segue. O espelho
  existe para que uma queda da VM (`/run` some no *boot*) não leve a coleta da fase inteira.
- **Fim da fase.** O amostrador é parado, e a cópia final de `/run/tccdiag/...` vai para `~/rebaseline-infra/` com
  `sha256sum`. Ela é versionada junto com os brutos da fase. A cópia final prevalece sobre o espelho, que só é usado se
  a original se perdeu.
- **Não muda nada congelado.** `scripts/run-rebaseline.sh` (`d95305fe…`), `analysis/rebaseline.py` (`3005f49a…`), o jar
  (`5eced136…`), a matriz e a lista `003814e9…` ficam como estão. O amostrador e a leitura vão para a VM num arquivo
  `tar` separado, com lista SHA-256 própria registrada na adenda da DT-018 antes do piloto.
- **Host.** O `typeperf` do host (contadores de `wt-gate-v4/runs/vm-s7-2026-11-04/diag-io-2026-11-05/contadores-io.txt`)
  continua sendo diagnóstico e não entra no critério.

## 3. Regra e limiares

### 3.1 Janela de cada execução

A janela h4 de uma execução é a **janela de medição** do PR §2 (DT-011): de `âncora + 44 s` a `âncora + 300 s`, com a
âncora (`ancora_ms`) lida do `execucao.json` gravado pelo *runner*. Se o *workload* terminou antes (`fim_utc` <
`âncora + 300 s`), a janela vai até `fim_utc`.

### 3.2 Definições

- **Janela de 10 s.** Para cada amostra *i* dentro da janela h4, *j* é a amostra mais antiga com `t_i − t_j ≤ 10 s`. O
  valor da janela é `(full_total_i − full_total_j) / 10^6` segundos, com `full_total` o `total=` da linha `full` de
  `/proc/pressure/io`. Entra toda janela cuja amostra final *i* está dentro da janela h4.
- **Intervalo entre amostras.** É `t_i − t_{i−1}` pelo relógio de parede (`EPOCHREALTIME`). Uma pausa da VM aparece aí
  depois que a sincronização de hora do Hyper-V corrige o relógio.
- **Salto de relógio.** Entre duas amostras seguidas, o avanço do relógio de parede e o do `/proc/uptime` diferem em 1 s
  ou mais. A correção normal de NTP é de frações de milissegundo por segundo, então uma diferença dessas é um degrau,
  e o caso típico é a hora sendo acertada depois de uma pausa da VM.

### 3.3 Critério

| Código | Condição na janela h4 | Limiar |
|---|---|---|
| **h4a** | alguma janela de 10 s com io `full` acumulado ≥ limiar | **1,5 s** |
| **h4b** | algum intervalo entre amostras seguidas ≥ limiar, ou algum salto de relógio | **3,0 s** (salto: 1 s) |
| **h4c** | cobertura incompleta: falta amostra na janela h4 por mais de 3 s seguidos no início ou no fim, ou o arquivo não é legível | — |

Qualquer um dos três deixa a execução **`descartada-infra (h4)`**, com o código.

### 3.4 Recalibração com os dados de 04 e 05/11

Brutos: `wt-gate-v4/runs/vm-s7-2026-11-04/` (`vm-20261104T232939Z/`, `vm-20261105T012458Z/` e `diag-io-2026-11-05/`).
Contas feitas com `diag-io-2026-11-05/scripts/h4.py`, pela definição da seção 3.2.

| Regime | Conjunto | Duração | Pior janela de 10 s | Maior intervalo | Janelas ≥ 1,5 s |
|---|---|---|---|---|---|
| Antes das mitigações | checagem da V-S7r (23:18-23:28Z) | 10 min | 2,45 s | 1,65 s | 76 |
| Antes das mitigações | bloco da V-S7r (23:29-23:41Z) | 12 min | 9,32 s | 5,74 s | 164 |
| Depois | diagnóstico 2 (00:43-01:13Z) | 30 min | 1,73 s (atividade do coordenador no host) | 1,014 s | 3 |
| Depois | checagem do S7t (01:14-01:24Z) | 10 min | 1,17 s (cauda do mesmo evento) | 1,004 s | 0 |
| Depois | bloco do S7t (01:24-01:36Z) | 11,5 min | 5,17 s (episódio sem atribuição) | 1,006 s | 20 |

Fora dos dois episódios do regime "depois", a pior janela de 10 s foi de 0,69 s, e quase todas ficaram abaixo de 0,6 s.
O intervalo entre amostras nunca passou de 1,014 s.

- **h4a = 1,5 s fica.** O valor tem folga de ~2,2× sobre o ruído limpo (0,69 s) e fica abaixo do episódio mais leve
  já visto de origem externa (1,73 s, causado por cópia e `git` no host). O episódio sem atribuição de 01:33Z (5,17 s)
  e os da V-S7r (2,45 e 9,32 s) passam bem acima. Com 2,0 s, o resultado seria o mesmo em todos os episódios menos o de
  01:13Z, que é exatamente o tipo de interferência que se quer pegar.
- **h4b = 3,0 s fica.** A folga é de ~3× sobre o maior intervalo limpo (1,014 s). Os buracos de 4,4 e 5,7 s da V-S7r
  vieram de um amostrador gravando em disco. Com o amostrador em tmpfs, um buraco passa a significar pausa da VM ou
  falta de CPU.
- **Conferência no piloto (PR §8).** Se, no piloto, 2 ou mais das 4 execuções `none` que não forem h4 tiverem a pior
  janela de 10 s entre 1,1 e 1,5 s, o limiar é revisto por DT nova antes da noite 1, nunca depois dela. Isso quer dizer
  que o ruído com carga ficou perto do limiar.

## 4. Efeito sobre a execução, as células e a fase

### 4.1 A execução

- Uma execução h4 **fica nos brutos e não entra na análise.** Ela **não** é recusa da célula: não conta para as paradas
  (a) e (b) do PR §6 nem para o limite de uma reposição por célula. É o mesmo tratamento da parada (h) do C1 §1.2,
  item 2.
- **Reposição.** Toda execução de campanha ou de reposição que o código classificou como ACEITA e que é h4 é reposta com o
  **próximo i livre** (i ≥ 120) e a semente `S0 + 100·i` (R-S1). Uma semente que já alimentou o *workload* nunca é
  reaproveitada.
- **Quando o código também recusou** a execução (RECUSADA por algum K), ela é tratada como h4 na análise, e a reposição é
  a do próprio *runner* (DT-014). Se essa recusa produzir uma parada (a) ou (b) que **só** existe por causa de execuções
  h4, vale a regra do C1 §1.2: o Senior declara isso no parecer da fase e o Gestor decide a retomada, com registro.
- **Piloto.** Execuções h4 do piloto não são repostas (o piloto nunca entra em resultado). Elas não impedem a liberação
  pelo PR §8, se os K passaram, e entram na conta da viabilidade (seção 6).

### 4.2 Segmento do F1 (mudança em relação à V-S7r)

No F1 (C2), o `podman start isn2` acontece a ≈ +109 s da âncora, dentro da janela de medição. A subida de um contêiner
e de uma JVM lê e grava em disco. Se isso gerar io `full` ≥ 1,5 s numa janela de 10 s, o h4a descartaria as células F1
por causa do próprio tratamento, e não da infraestrutura. Por isso, **nas execuções F1, o h4a não é avaliado nas
janelas de 10 s cuja amostra final cai em `[t_reinicio − 2 s, t_reinicio + 60 s]`**, com `t_reinicio` =
`âncora + falha_efetiva.f1_reiniciado_desde_ancora_s` do `execucao.json`. Se esse campo for nulo, vale `âncora + 109 s`.

- O h4b e o h4c valem na janela inteira, também no F1.
- Dentro do segmento, a pior janela de 10 s é **registrada** (coluna `pior_janela_seg_f1_s`) e reportada por célula como
  covariável.
- **Assimetria declarada.** Nas células F1, um travamento de disco que caia inteiro dentro do segmento (62 s de 256 s) não
  é detectado. Esse custo é declarado junto das tabelas do F1.
- O ensaio do F1 (antes do piloto) roda com o amostrador como diagnóstico. A leitura do Senior do ensaio informa quanto
  io o `podman start` produz nesta VM, mas **não muda esta regra**.

### 4.3 Parada da fase (mudança em relação à V-S7r)

A V-S7r propunha parar a fase com 3 execuções h4. Com a taxa estimada na seção 6 (~7 %), essa regra pararia ~80 % das
noites de 60 execuções sem que nada tivesse mudado no host (simulação na seção 6). A regra passa a ser:

**Parada h4** quando, na mesma fase, ocorrer qualquer um destes:

1. **5 ou mais execuções h4 entre as últimas 12 executadas** (uma rodada), ou **4 execuções h4 seguidas**;
2. uma execução **h4c** (amostrador ilegível ou ausente);
3. a unit do amostrador inativa durante a fase.

Com p = 7 %, a chance de uma parada h4 numa noite de 60 execuções é de ~1,5 %. Com p = 12 %, de ~12 %. Com p = 25 %, de
~80 %. No regime de antes das mitigações (p ≈ 60 %), a parada vem na primeira rodada. A regra separa o regime de
episódios raros do regime de disco ruim, sem parar noites boas.

**Efeito da parada**, como na parada (h) do C1 §1.2: nenhuma execução nova da fase começa. Como o *runner* é congelado e
não conhece o h4, a parada é feita pelo coordenador. O vigia (seção 5) avisa, e o coordenador interrompe o *runner* com
Ctrl-C **durante o portão K1 da execução seguinte**, antes do lançamento do *workload*. Sem `stdout.log`, essa execução
volta com o mesmo i e a mesma semente (DT-014, item 1). A retomada pede decisão do Gestor registrada, a partir da
leitura do `typeperf` e do amostrador.

### 4.4 Teto de reposições h4

Na campanha inteira, no máximo **30 reposições h4** (25 % das 120). Passando disso, a campanha para, e o Gestor decide.
Uma célula que, ao fim das reposições, tiver menos de 10 execuções aceitas que não sejam h4 entra na análise com o que
tem. O PR §7 já exige ≥ 8 execuções aceitas para estimar o contraste.

## 5. Leitura do resultado

O *runner* não lê o h4. A leitura é feita por `analysis/h4.py` (novo, Python 3, só biblioteca padrão, roda com `-I`),
que recebe o arquivo do amostrador e o diretório de saída do *runner*:

| Subcomando | Faz | Saída |
|---|---|---|
| `fase` | Para cada `exec-*/execucao.json` da fase, calcula a janela h4, as janelas de 10 s, os intervalos, o segmento do F1 e o veredito (`ok`, `h4a`, `h4b`, `h4c`, ou combinações). Aplica a regra de parada da seção 4.3 na ordem do `lancamento_ms`. | `<saida>/h4/h4-<fase>.tsv`, `h4-<fase>.json`, resumo na saída padrão; exit 0 (segue), 30 (parada h4), 2 (uso), 4 (arquivo ilegível) |
| `repor` | Acrescenta a `<saida>/reposicoes/reposicoes.tsv` uma linha por execução h4 ACEITA ainda não reposta, com o próximo i livre e a semente `S0 + 100·i`, no formato que o *runner* lê. | linhas novas e o SHA-256 do TSV |
| `visao` | Monta `<destino>/` com *hard links* de todos os `exec-*` da saída, menos os h4 e os da lista `descartada-infra (h)` dada por `--excluir`. Copia quando o *link* não é possível, conferindo o SHA-256. | árvore para `rebaseline.py tabelas --saida <destino>` |

- **Vigia.** Durante a fase, `scripts/vigia-h4.sh` roda numa janela `tmux` à parte e chama `h4.py fase` a cada 300 s
  sobre a cópia em `/run/tccdiag/` (a leitura é em memória). Com exit 30, grava `~/rebaseline-infra/<fase>-<UTC>/PARADA-H4`
  e mostra um aviso. O vigia **não** interrompe o *runner*.
- **Reposições na prática.** Ao fim da noite 2, roda primeiro o `run-rebaseline.sh --fase reposicoes`, que repõe as
  recusas do código. Depois roda o `h4.py repor`, e por fim o `run-rebaseline.sh --fase reposicoes` de novo. O *runner*
  lê o `reposicoes.tsv` existente, não acrescenta células que já estão lá, pula os `exec-*` que já existem e roda as
  linhas novas. O prompt do Worker exige um teste com os *shims* do *runner* que prove essa sequência sem mudar o
  *runner*.
- **Análise final.** `rebaseline.py tabelas` roda sobre a árvore do `visao`, não sobre a saída bruta. O parecer de cada
  fase traz a tabela `h4-<fase>.tsv`.

## 6. Taxa esperada e viabilidade

Depois das mitigações, ~71 min de observação tiveram **2 episódios** que dariam h4a, um provocado pela atividade do
coordenador no host e outro sem atribuição. Para uma janela de 256 s:

| Estimativa | Base | Fração de execuções h4 |
|---|---|---|
| Otimista | 1 episódio em 71 min (o de 01:13Z não se repete com H0 estrito), Poisson | ~6 % |
| Empírica | janelas deslizantes de 256 s nos 3 conjuntos "depois" | 7,0 % (163 de 2.333) |
| Pessimista | 2 episódios em 71 min | ~12 % |
| Regime de antes das mitigações | janelas deslizantes nos conjuntos da V-S7r | 60 a 70 % |

A incerteza é grande: com 1 ou 2 eventos, o intervalo de 95 % da taxa de Poisson vai de menos de 1 % a ~40 % de execuções h4. O
ensaio e o piloto acrescentam ~3,5 h de observação antes da noite 1.

**Consequência para a campanha**, com cada execução levando de 6,5 a 8 min (PR §8):

- Reposições h4 esperadas nas 120 execuções, contando as que caírem de novo em h4: ~8 com 6 %, ~9 com 7 %, ~16 com 12 %.
- Tempo: ~1 h (8 reposições) a ~2 h (16), somado às até 6 reposições de recusa do PR. Cabe no fim da noite 2 (que já
  previa 6,5 a 9 h) se a noite começar cedo. Senão, vira uma noite curta só de reposições, de 1 a 2,5 h, dentro da
  reserva X-25 do plano de execução contínua.
- **A campanha continua viável** se a taxa medida no ensaio e no piloto ficar abaixo de ~25 %.

**Condição para a noite 1.** Contam-se os episódios (trechos contínuos com janela de 10 s ≥ 1,5 s ou intervalo ≥ 3 s) no
ensaio e no piloto juntos. Com **3,5 episódios por hora ou mais**, que corresponde a ~25 % de execuções h4, a noite 1 não
começa sem decisão do Gestor, registrada. Fica registrado também que 5 ou mais execuções h4 entre as 12 do piloto
acionam a regra 4.3 dentro do próprio piloto. As opções nesse caso são as M2 e M5 da V-S7r §5 ou aceitar a taxa como
limitação declarada.

## 7. Covariáveis do host (complementa C1 §2)

Ficam fixas e registradas na abertura e no fim de cada fase, junto das covariáveis da VM: ASPM do PCIe desligado na
tomada; exclusão de `C:\Hyper-V` no Defender; *checkpoint* da VM mantido (A-9); espaço livre em `C:` (regra 2.5 do plano
de execução contínua); H0 estrito durante a fase, **incluindo** cópia, `scp`, `git`, compressão e extração no host
(V-S7t, P-3). Mudar uma delas entre fases é mudança de covariável, reportada e levada ao Gestor antes da fase seguinte.

## 8. O que não muda

Matriz, ordem, sementes, jar, *runner*, `rebaseline.py`, K1..K10, paradas (a)..(h), DT-010 a DT-017 e a análise do PR §7.
O h4 só tira execuções da análise por critério de infraestrutura, decidido sem olhar o desfecho, e as repõe.

## 9. Pontos para o Gestor confirmar

- **C3-1.** A regra de parada da seção 4.3 (5 em 12 ou 4 seguidas, mais o h4c), no lugar das 3 por fase da V-S7r.
- **C3-2.** O segmento do F1 da seção 4.2 e a assimetria declarada.
- **C3-3.** O teto de 30 reposições h4 (seção 4.4) e a condição de 3,5 episódios por hora para a noite 1 (seção 6).
- **C3-4.** As covariáveis do host da seção 7, inclusive manter a exclusão do Defender até o fim da campanha.
