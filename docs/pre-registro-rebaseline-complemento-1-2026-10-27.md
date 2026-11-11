# Complemento 1 ao pré-registro do *rebaseline* (2026-10-27)

> Senior, 2026-10-27. **Aprovado pelo Gestor em 2026-10-27** (decisões transmitidas ao Senior pelo
> coordenador). Complementa `docs/pre-registro-rebaseline-aprovado-2026-10-23.md` (doravante PR), cujo
> registro formal é a DT-009 de `docs/decisoes-tecnicas.md`. O PR **não é editado**: este documento
> acrescenta regras e declarações, e cada item diz qual seção do PR ele complementa. Pela regra do
> cabeçalho de `decisoes-tecnicas.md`, ele precisa estar commitado **antes do piloto** e antes de
> qualquer dado do *rebaseline*. Nenhum dado do piloto ou da campanha existia quando foi escrito.
>
> Origens: validação V-S1 (`docs/validacao-v-s1-2026-10-27.md` da branch `test/b29-gate-v4`, commit
> `012a621`), pontos P-5 e P-6 e seções 3 e 5.6; incidente P-28 (`docs/registro-de-problemas.md`);
> T12 de `tcc-escrita/memoria-tcc/19-metodologia-testes-sem7-9.md` e da Tabela de parâmetros de
> `Metodologia.tex`; limiares do `VerificadorSanidade` (`docs/validacao-b43-3a-i-2026-10-22.md`
> seção 5.2 e `docs/validacao-b43-3a-i-ressalvas-2026-10-22.md` item 2). O item (d) também está
> registrado como **DT-015**. Sem push.

## 1. Item (a): parada automática (h), mudança de *boot* ou de processo do *cluster*

Complementa PR §6 ("Paradas automáticas da campanha", (a)..(g)) e a DT-014.

### 1.1 Motivo

Em 2026-10-18 07:49:35 UTC o *host* Windows caiu sem desligamento ordenado (Kernel-Power 41, EventLog
6008) e ficou fora até 2026-10-20 16:31 UTC. A VM voltou sozinha, com os contêineres em `created`, o
histórico do *cluster* perdido e o kernel trocado por `unattended-upgrade` (P-28). Numa noite de
campanha, o mesmo evento mata o *runner* junto com a VM. O PR não diz o que acontece com a execução em
curso nem com a noite, e não tem nenhum critério que perceba um *cluster* que voltou depois de um
*boot*, ou um nó cujo processo foi reiniciado fora do roteiro: o portão K1 olha só a saúde, e passaria.

### 1.2 Regra

Há **parada (h)** quando, dentro de uma fase (piloto, noite 1, noite 2 ou noite de reposições), ocorrer
qualquer um destes eventos:

- **h1.** O `boot_id` da VM (`/proc/sys/kernel/random/boot_id`) muda. Também vale entre fases: se o
  `boot_id` no início de uma fase for diferente do registrado no fim da fase anterior, a fase **não
  começa**.
- **h2.** O `.State.StartedAt` de `isn1` ou de `isn3` muda, ou há evento `start`/`died` de um deles.
- **h3.** O `.State.StartedAt` de `isn2` muda, ou há evento `start` de `isn2`, **fora** de uma
  execução F1. Dentro de uma execução F1, o reinício esperado é o do `inject-crash.sh` daquela
  execução: um único `start` de `isn2` entre o disparo do injetor e o fim da execução, coerente com
  `f1_reiniciado_desde_ancora_s` gravado pelo *runner*.

Efeito:

1. **A fase para.** Nenhuma execução nova da fase começa depois do evento. Se o evento for percebido
   só depois (seção 1.3), as execuções da fase que começaram depois do instante do evento recebem o
   mesmo tratamento do item 2.
2. **A execução em curso no instante do evento é descartada por infraestrutura.** Fica nos brutos,
   rotulada `descartada-infra (h<n>)`, e não entra na análise. Ela **não** é uma recusa da célula:
   não conta para as paradas (a) e (b) do PR §6 nem para o limite de uma reposição por célula.
   - Se ela não tinha `stdout.log` (o *workload* não foi lançado), vale a DT-014 item 1: mesmo i, mesma
     semente.
   - Se tinha `stdout.log`, ela é reposta no fim da campanha com o **próximo i livre**, como manda a
     DT-014 item 2 para a reposição. Uma semente que já alimentou o *workload* nunca é reaproveitada.
3. **Retomada.** A fase seguinte só começa com decisão do Gestor registrada (entrada nova em
   `decisoes-tecnicas.md`) sobre o estado do *cluster*. Depois de um h1, o *cluster* foi reiniciado
   (ou ficou em `created`) e deixou de ser o *cluster* criado depois do S7 que o complemento da DT-009
   parte 2 registra. Recriar o *cluster* e registrar o novo instante de criação é a resposta esperada,
   mas a decisão é do Gestor.

Relação com o código congelado: `analysis/rebaseline.py estado` classifica uma execução interrompida
com `stdout.log` como RECUSADA "interrompida" (DT-014 item 2) e a conta nas paradas (a) e (b). O
código não conhece (h). Por isso a classificação `descartada-infra` é feita no parecer da fase pelo
Senior, a partir do registro da seção 1.3, e prevalece sobre o rótulo do código **na análise**. A
reposição gerada pelo código (próximo i livre) coincide com a do item 2. Se a contagem automática do
código produzir uma parada (a) ou (b) que **só** existe por causa de execuções `descartada-infra`, o
Senior declara isso no parecer e o Gestor decide a retomada, com registro. A decisão depende só da
classificação de infraestrutura, nunca do desfecho medido das execuções.

### 1.3 Verificação

Os itens abaixo valem em qualquer caso, inclusive se o Gestor decidir levar parte da checagem para o
*runner* (a checagem entre fases e o descarte retrospectivo não podem ser feitos por ele).

- **Abertura de cada fase**, antes do `go`: um bloco só de leitura gravado em arquivo **fora** do
  diretório de saída do *runner* (por exemplo `~/rebaseline-infra/<fase>-inicio-<UTC>.txt`) e depois
  versionado junto com os brutos. Conteúdo: `date -Is`; `cat /proc/sys/kernel/random/boot_id`;
  `uptime -s`; `uname -r`; `podman --version` e `sudo podman version`; `apt-mark showhold`;
  `systemctl is-enabled apt-daily-upgrade.timer unattended-upgrades` e `systemctl is-active` dos dois;
  para `isn1`, `isn2` e `isn3`, `sudo podman inspect -f '{{.Name}} {{.State.Status}} {{.State.StartedAt}}
  {{.State.StartedAt.Unix}} {{.Created}}'`. O `boot_id` é comparado com o do fim da fase anterior (h1).
- **Fim de cada fase** (manhã seguinte, ou depois de uma parada): o mesmo bloco, mais
  `journalctl --list-boots --no-pager | tail -n 3` e
  `sudo podman events --since <date -Is da abertura> --until <agora> --filter event=start --filter
  event=died --filter event=restart` (lista de eventos de contêiner da fase).
- **Parecer da fase** (Senior): confere h1 (`boot_id` igual na abertura e no fim, e igual ao do fim da
  fase anterior); h2 (`StartedAt` de `isn1`/`isn3` igual na abertura e no fim, nenhum evento deles na
  lista); h3 (todo `start` de `isn2` na lista cai dentro de uma execução F1 da fase, pelos instantes de
  `runner.log` e do `falha_efetiva` da execução, e o `StartedAt` final de `isn2` é o do último F1 da fase,
  ou o da abertura se a fase não teve F1). Qualquer divergência é (h), aplicada como na seção 1.2.
- Cada bloco registra o comando, o carimbo do cliente no início e no fim e o `date -Is` remoto
  (ressalvas R-1 e R-2 da V-S1).

### 1.4 Sem política de reinício nos contêineres

O `podman-compose.yml` continua **sem** `restart:` (nem `always` nem `on-failure`). Motivos (V-S1
seção 5.6 item 4):

1. O `podman-compose.yml` é um dos fixos do PR §2 (*cluster* de 3 nós com o XML aprovado). Mudar o
   arquivo muda a configuração medida.
2. O F1 é `podman stop` + 60 s + `podman start`. A JVM sai com 143 no SIGTERM. Uma política de
   reinício que trouxesse `isn2` de volta antes dos 60 s encurtaria a falha sem aviso e contaminaria
   as células F1. Com `on-failure`, esse risco é direto. O comportamento do podman 4.9.3 com contêiner
   parado pelo usuário não foi verificado, e não precisa ser, porque a política não entra.
3. Depois de uma queda do *host*, o *runner* morre com a VM. Um *cluster* que voltasse sozinho não
   retomaria a campanha e só esconderia a queda, porque o portão K1 passaria. Contêineres em `created`
   depois do *boot* são a parada segura que se quer, detectada pelo `boot_id` (h1) e não contornada.

## 2. Item (b): covariáveis de plataforma por noite

Complementa PR §9 (identificadores registrados antes e durante a campanha).

- `uname -r` e a versão do podman (cliente e servidor, *rootful*) passam a ser **covariáveis
  registradas por fase**. Motivo: o kernel mudou de `6.8.0-124-generic` (REP=1..3, G-5, G-6) para
  `6.8.0-147-generic` (S1, 27/10) por `unattended-upgrade` (ressalva R-3 da V-S1).
- O *runner* congelado já grava `uname -a` e `podman version` em `ambiente.md` no pré-voo de cada fase
  (`scripts/run-rebaseline.sh`, bloco "pre-voo 5"). O bloco manual da seção 1.3 grava os mesmos
  valores na abertura e no fim. Os dois registros têm de coincidir.
- Valor esperado: o que estiver instalado e segurado (`apt-mark hold`) quando o piloto começar. No S1 a
  VM tinha `6.8.0-147-generic` e podman 4.9.3. O valor do piloto é o de referência das noites.
- Uma troca de kernel exige *boot*, então já cai em h1. Uma troca da versão do podman sem *boot*
  (atualização de pacote durante a campanha) não é parada automática: é registrada, reportada no
  resultado como mudança de covariável e levada ao Gestor antes da fase seguinte.
- A tabela de resultados declara kernel e podman de cada noite.

## 3. Item (c): T12 não aplicado (carga em laço fechado)

Complementa PR §2 (invocação do *workload*) e PR §11 (efeito no texto acadêmico).

- T12 da metodologia pede carga a **75 % do pico** de vazão medido num piloto sem falhas (Jaiman et
  al., TailX). O *rebaseline* **não aplica** T12.
- O *workload* roda em **laço fechado**: 4 *threads*, cada uma emite a operação seguinte assim que a
  anterior termina, sem alvo nem limitação de vazão (`--threads 4 --ops 0`, sem opção de vazão
  alvo, que o jar congelado `5eced136…` não tem). A vazão de cada execução é **medida e reportada**
  (PR §7, "Vazão"), não imposta.
- Consequência para a leitura: com laço fechado, a vazão cai quando a latência sobe (F1, F3), e a carga
  oferecida não é a mesma entre células. Os contrastes de p99 comparam o sistema sob a mesma
  concorrência (4 clientes), não sob a mesma taxa de chegada. Isso é declarado junto das Tabelas 5 e 6.
- Este afastamento é declarado **antes de qualquer dado** do *rebaseline*. O texto de
  `Metodologia.tex` já descreve o afastamento nesses termos; este item é o registro a que ele se
  refere.

## 4. Item (d): limiares do `VerificadorSanidade` (K3), PRÉ-REGISTRO

Complementa PR §6, critério K3. Mesmo conteúdo da **DT-015**.

| Regra | Limiar |
|---|---|
| Login: OK/(OK + BLOCKED) | ≥ 0,95 |
| Validate: VALID | > 0 |
| Validate ausente: (NONE + NOT_FOUND)/Validate | ≤ 5 % |
| Mistura: desvio por operação em relação ao peso nominal do cenário | ± 2,0 p.p. |
| Versão do critério gravada no JSON | `b43-3a-i/1` |

- O Gestor aprovou esses valores como os **limiares do *rebaseline***. Eles estão fixados no código do
  jar congelado (`Sanidade.Limiares.provisorios()` = `(0.95, 0.05, 2.0)` e `Sanidade.VERSAO =
  "b43-3a-i/1"` na ponta `988fd3c`, da qual saiu o jar SHA-256
  `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf`).
- O nome `provisorios()` no código e o comentário "PROVISORIOS" ficam como estão: o jar não é
  reconstruído (G2 da revalidação do *runner*). Para o *rebaseline*, os valores são os definitivos.
- A calibração separada recomendada na V-B43.3a-i (seção 5.2, uma execução pós-B-43.2 por cenário antes
  de congelar o limiar de Login) **não** foi feita como etapa própria. O Gestor fixou os valores sem
  ela. Se o piloto mostrar que K3 recusa sistematicamente uma célula por razão que não é defeito, vale
  o PR §8: mudança por entrada nova em `decisoes-tecnicas.md`, aprovada antes da noite 1, com subida
  da versão do critério.
- No parecer de cada fase, o Senior confere em uma amostra de JSON do verificador que `versao` é
  `b43-3a-i/1` e que os limiares gravados são 0,95, 0,05 e 2,0.

## 5. Condições operacionais aprovadas (P-6)

Não mudam regra de recusa. São condições para a fase começar, conferidas na abertura (seção 1.3).

- ***Host*.** Alimentação conferida pelo Gestor (no-break, ou notebook na tomada com bateria saudável);
  suspensão e hibernação desligadas nas noites; Windows Update pausado na janela da campanha;
  `AutomaticStopAction=ShutDown` na VM `tcc-f3` (lido e, se preciso, ajustado em PowerShell elevado:
  `Get-VM tcc-f3 | Select AutomaticStopAction,AutomaticStartAction`). Com `ShutDown`, um desligamento
  ordenado do *host* desliga a VM de forma ordenada, e o *cluster* registra `ISPN080002`.
- **VM.** Kernel segurado (`apt-mark hold` dos pacotes do kernel em uso) e `unattended-upgrades`
  pausado (timer `apt-daily-upgrade` desabilitado) durante a campanha. São escritas na VM: feitas
  numa sessão de VM aprovada, antes do piloto, com os comandos e as saídas nos brutos. Ao fim da
  campanha, o Gestor decide se desfaz.

## 6. O que não muda

Matriz, ordem, sementes, `matriz.tsv` (`8654900c…`), jar (`5eced136…`), *bundle* da VM, K1..K10,
paradas (a)..(g), DT-010, DT-011, DT-013 e DT-014, análise do PR §7 e ponta do *runner* registrada no
complemento da DT-009 parte 2. Este complemento só acrescenta a parada (h), as covariáveis, a
declaração sobre T12 e o pré-registro dos limiares de K3.

## 7. Adenda de 2026-10-27: forma de verificar (h) e item (e), T4

> Acrescentada no mesmo dia, depois de novas decisões do Gestor (transmitidas pelo coordenador),
> antes de qualquer dado do *rebaseline*. As seções 1 a 6 acima não foram editadas.

### 7.1 Verificação de (h): manual, pela checklist

O Gestor decidiu que a parada (h) é verificada **manualmente**, pelo procedimento da seção 1.3 (bloco
de abertura antes do `go`, bloco de fim, conferência h1/h2/h3 no parecer da fase). O
`scripts/run-rebaseline.sh` e o `analysis/rebaseline.py` **não mudam**. Os *hashes* do complemento da
DT-009 parte 2 continuam valendo. Consequências, já previstas na seção 1.2:

- (h) é aplicada de forma retrospectiva, no parecer da fase. O efeito sobre os dados é o mesmo da
  parada automática: a execução em curso e as que começaram depois do evento ficam `descartada-infra`.
- A classificação do código para uma execução interrompida com `stdout.log` continua sendo RECUSADA
  "interrompida" (DT-014 item 2). Na análise, prevalece `descartada-infra`, com a regra da seção 1.2
  para uma parada (a) ou (b) que só exista por causa dessas execuções.
- Os blocos de abertura e de fim são pré-condição da fase. Sem o bloco de abertura, não há `go`. Sem o
  bloco de fim, a fase não recebe parecer.

### 7.2 Item (e): T4, `numSegments` = 64 em vez de 256

Complementa PR §2 ("Outros fixos": *cluster* de 3 nós com o XML do B-42.1). Mesmo conteúdo da
**DT-016**.

- A tabela de parâmetros da metodologia (T4, `Metodologia.tex`) fixa `numSegments` = 256, o padrão
  do Infinispan (fonte `infinispan_tuning`).
- O *cluster* usa **64 segmentos** em todos os *caches* e em todas as execuções: a bateria de junho
  (`feature/f3-baseline`), o gate (REP=1..3, G-5, G-6, S1..S7 com o XML atual da v4) e o
  *rebaseline* (XML do B-42.1, quatro *caches*, todos com `segments="64"`). O valor vem do esqueleto
  do *cluster* de 25/05 (commit `20b80b1`), com a justificativa informal de "menos *state transfer*
  com 3 nós". Essa justificativa nunca foi registrada em DT nem tem fonte. `docs/configuracao-infinispan.md`
  apontava o ponto como pendente.
- **Decisão do Gestor:** manter 64 e declarar o desvio. Motivo: comparabilidade com a bateria de junho
  e com as partições REP=1..3 do gate, todas com 64. Não há fonte que justifique 64 contra 256, e o
  texto não deve afirmar que 64 é melhor para 3 nós. O efeito de 64 contra 256 sobre latência e
  *state transfer* não foi medido e fica fora do escopo.
- Declarado antes de qualquer dado do *rebaseline*. Para o texto acadêmico (via `docs/resumos-para-tcc/`),
  T4 entra na lista de afastamentos declarados, ao lado de T10, T12, T14 e T18.
