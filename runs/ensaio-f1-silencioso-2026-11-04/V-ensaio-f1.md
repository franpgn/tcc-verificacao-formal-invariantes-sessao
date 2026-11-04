# V-ensaio-f1: validação independente do `ensaio-f1.sh` (Senior, 2026-11-04)

Branch `test/ensaio-f1-silencioso`, base `99c1d7e`, commits do Worker `d32eacb` (chore), `46ecbf0` (test), `a8feb22`
(feat), `47144e5` (docs). Prompt: `docs/prompt-worker-ensaio-f1-2026-11-04.md`. Nenhuma VM tocada e nenhum push. O código
foi lido inteiro, sem partir do relatório do Worker.

**Veredito: APROVADO COM RESSALVAS.** Nenhuma ressalva impede o uso na VM. A R-1 e a R-2 devem ser resolvidas antes do
*bundle* v4h, e são pequenas.

## 1. Artefatos conferidos

| Item | Valor |
|---|---|
| `ensaio-f1.sh` | sha256 `b83e82230b8c69e61777499047e81d3055ec35423378af8a87500cf5ffda8d8c` (igual ao relatado; inalterado depois dos mutantes) |
| `ensaio-f1.test.sh` | sha256 `707c1e28c4690ee7b00bdb976a51207953f63c89b61dd05192671385fa2bb4f7` |
| Commits | 4, na ordem pedida (`chore:` → `test:` vermelho → `feat:` → `docs:`), árvore limpa |
| *Bundle* v4h | **não gerado** (o prompt §1 pede; ver R-2) |

## 2. Aderência ao prompt e critérios F-1..F-10

| # | Implementação (linha do `ensaio-f1.sh`) | Origem | Parecer |
|---|---|---|---|
| F-1 | 166-170: as 5 regex de `k6` de `analysis/rebaseline.py:528-532` (conferidas no *blob* de `20805b0`) e `injetor_exit=0` | DT-017, compl. 2 §3 (K6) | conforme |
| F-2 | 172-176: todo `died` com `"ContainerExitCode":137` e `isn2 ExitCode=137` no `injetor.log`; exige ≥ 1 `died` (D-6) | DT-017, nota P2 da adenda | conforme, mais estrito (D-6) |
| F-3 | 178-184: `INJ_OFFSET−1 ≤ disp−anc ≤ INJ_OFFSET+2` = [48, 51] s, só com carga | DT-011 P3 | conforme |
| F-4 | 186-189: Δdet = 1ª `ISPN000094` sem isn2 − linha `isolando` do `injetor.log`, ≥ 30 s em isn1 **e** isn3; sem leitura = FALHA | §4.1 | conforme; parada em 580-582 |
| F-5 | 192-193: nenhuma `ISPN000390`/`ISPN080002` antes do carimbo `podman start isn2` | §4.1 | conforme |
| F-6 | 194-196: vida nova com `ISPN000389` e `ISPN080001`, sem `ISPN000693` | §4.1, compl. 2 §4 | conforme |
| F-7 | 199-201 e 564-571: `volta_ok=1` e `volta_s < 180` do carimbo do `start` | §4.1, parada (e) do PR §6 | conforme (granularidade: ver item 8) |
| F-8 | 203-204: `eth0` UP no `ip -br link` do netns novo | §4.1 | conforme |
| F-9 | 138-141 (`boot_id` início = fim = argumento) e 206-209 (intervalo do saude-log ≤ 2 × 2 s, regra do `s5.sh`) | V-S4 P-2 | conforme; ver R-5 |
| F-10 | 144-153: portão K1 final, 0 timers, `size` 0 nos caches não usados por carga (D-1) | §5.1 das decisões de 16/10 | desvio declarado (D-1); sem teste (R-1) |

Os passos 0..8 da seção 3, as 4 execuções da seção 4 (i 2000..2003, sementes 250 200/250 300 calculadas de `S0` e
`PASSO_SEMENTE` lidos), a interface da seção 2 e os códigos de saída 0/1/2/3/4/6/130 estão presentes. As marcas
`# E-n (pendente do Gestor)` estão no código. O GO é por execução, com rótulo exato e prazo de 900 s, gravado em `go.log`
e `go.txt`. O portão K1 roda entre o GO e a injeção de cada execução (verificado no código e no T-2).

## 3. Constantes, *hashes* e jar

- **Constantes lidas, não redigitadas.** `carregar_rb` (238-260) exige que cada linha `readonly X=` apareça exatamente uma
  vez e case com um regex que só admite `[A-Za-z0-9_.]` nos valores; só então a avalia. Confere as 19 constantes,
  `SERVERS`/`USERNAME` da linha 80 e extrai o corpo de `montar_cmd` (`run-rebaseline.sh:273-283` de `20805b0`), exigindo
  `--password "${PASSWORD}"` e a máscara `CMD_MASC`. Conferi contra `git show 20805b0:scripts/run-rebaseline.sh`
  (linhas 48-51, 59-61, 65, 80, 273-283). Só a `ANCORA_RE` é literal no script, e o T-0 confere a igualdade.
  O mutante M5 do Worker (redigitação) morre.
- **Hashes.** A tabela embutida (44-50) é igual aos *blobs* de `20805b0` que recalculei (6 arquivos). O jar congelado
  `dados-vm/rebaseline-2026-10/jar/` tem `5eced136…efaf`, e a lista `sha256sum -b` dos 7 dá `003814e9…33a4`, que é a
  adenda. O XML `6d52413b…` é o de `20805b0`. No passo 0 o script confere arquivo a arquivo (`sha256sum -c`) **e** a
  lista: são duas guardas independentes (o meu MS4 confirma isso).
- **Jar só leitura.** O script só lê o jar (`java -jar` e `java -cp`); nenhum caminho escreve no `--rb-dir`. No host o
  jar é `-r--r--r--`. O script **não** confere o bit de escrita do jar na VM, só o *hash* (R-4, menor).

## 4. Testes e mutantes (rodados por mim, sozinhos)

- **Suíte completa:** `bash runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.test.sh` → **444 PASS / 0 FAIL, exit 0, em
  1919 s** (caminho feliz das 4: 141 s; `--so 3`: 56 s; Git Bash, Windows). O Worker relatou 444/0 em 1526 s; a diferença
  é de máquina e carga, não de resultado.
- **Mutantes do Senior** (troca no script com `sed`, rodada contra as seções do teste indicadas e script restaurado; sha256
  final conferido = `b83e8223…`):

| Id | Mutação | Seções | Resultado |
|---|---|---|---|
| MS2 | `interromper` sem o `kill -TERM` ao injetor | T-9 | **morto** (1 FAIL: "injetor saiu com 143 depois do TERM") |
| MS3 | F-6 sem a exigência de `ISPN000693` ausente | T-7 | **morto** (2 FAIL) |
| MS4 | conferência arquivo a arquivo dos *hashes* trocada por `true` | T-3 | **morto** (1 FAIL; a lista ainda aborta com rc 1) |
| MS1 | F-10 sem nenhum cache exigido vazio (`vazios=""`) | T-2, T-8 | **sobrevive** (0 FAIL) |
| MS5 | `conferir_started` sempre 0 (contêiner reiniciado fora do injetor passa) | T-2, T-6 | **sobrevive** (147 PASS) |

MS1 e MS5 sobrevivem porque nenhum caso do teste põe um cache não vazio no estado final, nem reinicia um contêiner
**entre** execuções (o T-3 só cobre o reinício antes do passo 0). As duas guardas estão corretas no código, mas não têm
teste (R-1).

## 5. Parecer sobre as dúvidas

| # | Dúvida | Parecer | Recomendação ao Gestor |
|---|---|---|---|
| D-1 | F-10 só exige vazios os caches não usados por carga | Correto: o S1 deixa dados e o reset é no início da execução seguinte. Com as 4 execuções, nenhum cache é exigido vazio, e o tamanho fica como RESULTADO. O cluster é recriado antes do piloto (E-2) | **Aprovar** |
| D-2 | "lock do injetor livre" virou "nenhum `inject-crash.sh` em execução" (`pgrep -f`) | Conferido: o `inject-crash.sh` de `20805b0` não tem `flock` | **Aprovar** |
| D-3 | Com `--so 2..4`, o `StartedAt − Created` de isn2 só é registrado | Inevitável. O `conferir_started` dentro da execução continua valendo, mas não tem teste (R-1) | **Aprovar**; o Senior lê o `00-pre.log` antes de qualquer `--so` |
| D-4 | GO ausente ou errado sai com rc 3 | Coerente: nada é injetado, mesma classe do portão | **Aprovar** |
| D-5 | Workload sem âncora vira parada rc 4, sem injeção | Correto: não há o que medir, e a parada mata o workload e os coletores | **Aprovar** |
| D-6 | F-2 exige ao menos um `died` | Correto: protege contra coletor de eventos quebrado. O formato `"ContainerExitCode":137` do Podman 4.9.3 não está provado pelo teste; se divergir, a execução sai INVALIDA (não é perigoso) e o `injetor.log` ainda mostra `ExitCode=137` | **Aprovar** |
| 8a | `admin:infinispan` fixo no `saude_loop` copiado do `s5.sh` | A senha do cluster é `PASS=infinispan` em `cluster/podman-compose.yml` (já versionada); a cópia verbatim é a regra. Com outra senha, o saude-log teria HTTP 401 e o F-9 continuaria medindo só o intervalo. O portão e a volta usam `RB_PASSWORD` | **Aprovar como está** |
| 8b | Volta com granularidade de 5 s | É o `F1_VOLTA_PERIODO_S=5` lido do *runner*, o mesmo da parada (e) da campanha. A margem para 180 s é grande, e o saude-log de 2 s dá a leitura fina | **Aprovar**; relatar o tempo de volta como "≤ +5 s" |
| 9 | Script numa branch nova, não em `test/b29-gate-v4` | Justificável, porque outro Worker escrevia S6/S7 em `wt-gate-v4`. `git merge-tree` com `test/b29-gate-v4` (`73df40a`) não dá conflito: os arquivos são disjuntos. O *rebase* não muda o sha256 do script | **Aprovar** o *rebase* linear dos 4 commits sobre `test/b29-gate-v4` e depois gerar o v4h (R-2) |

## 6. Riscos para a VM

- **O script nunca isola, mata nem religa.** `grep` estático e T-0/T-2 confirmam: todo `link set` e todo `podman
  kill/start` vem do `inject-crash.sh` congelado. O `tc` só é usado com `qdisc show`. Isso exclui um caminho do ensaio
  que derrube a NIC da VM, porque a guarda de netns fica no injetor e é conferida também no passo 0.
- **isn2 parado sem restauração, por desenho:** injetor exit 5 por `start` falho → parada rc 4 com
  `sudo podman start isn2` impresso para o Gestor (o T-5 confirma que nada é tentado). Era o pedido.
- **`eth0` DOWN com isn2 ainda `running`:** só no exit 5 por "kill não terminou em 10 s" **e** com o `link set up` do
  injetor falhando. O `parada.txt` grava o `ip -br link`, mas só imprime instrução quando isn2 não está `running` (R-6).
- **Ctrl-C:** o injetor, em segundo plano num shell não interativo, ignora SIGINT. Por isso o `interromper` manda TERM,
  e o *trap* do injetor (FASE 1: kill + start; FASE 2: start) devolve isn2 em até 150 s. Se o injetor passar de 150 s,
  o ensaio sai com 130 e o injetor termina sozinho. O T-9 prova esse fluxo **no Git Bash**. No Linux não está provado
  que o `tee` da substituição de processo (`exec > >(tee …)`) sobreviva ao SIGINT do grupo. Se ele morrer, o script pode
  cair por SIGPIPE antes do TERM. isn2 ainda volta, porque o injetor não recebeu o INT e termina o ciclo, mas sobram
  órfãos: o workload, o `podman events` e um `saude_loop` com limite de 100 000 s (R-7).
- **F-9 por motivo errado:** o `saude_loop` faz os `curl` em série (`-m 3` cada). Se dois `/health` lentos caírem na
  mesma volta durante a troca de vista, o intervalo passa de 4 s e a execução sai INVALIDA sem pausa de host (R-5).
  Antes de descartar uma execução por F-9, ler o `saude.csv`.

## 7. Ressalvas

| # | Ressalva | Ação |
|---|---|---|
| R-1 | Duas guardas sem teste: F-10 com cache não vazio no fim (MS1) e `conferir_started` com reinício entre execuções (MS5) | Worker: 2 casos no `ensaio-f1.test.sh` (um no T-8, mutando `07-estado-final.log` com `--so 1`; um no T-2/T-4 reiniciando isn3 entre a 1 e a 2 → rc 3, sem injeção na 2), antes do v4h |
| R-2 | *Bundle* `tcc/gate-v4h.bundle` não gerado; branch fora de `test/b29-gate-v4` | Depois da R-1: *rebase* sobre `test/b29-gate-v4`, sem conflito, e gerar o v4h + `.sha256` |
| R-3 | `--dry-run` roda o `inject-crash.sh --dry-run` e avalia `montar_cmd` do `--rb-dir` sem conferir *hashes*; no passo 0, `carregar_rb` roda num *subshell* mesmo com *hash* falho (só define a função; a execução aborta) | Menor. Recomendado: conferir os *hashes* também no `--dry-run` |
| R-4 | O bit de escrita do jar no `--rb-dir` da VM não é conferido (só o *hash*) | Opcional: registrar `stat -c %A` do jar no `00-pre.log` |
| R-5 | F-9 pode invalidar por `/health` lento, não por pausa | Só leitura humana; nenhuma mudança de critério agora |
| R-6 | `parada.txt` sem instrução para isn2 `running` com `eth0` DOWN | Opcional: imprimir que "eth0 DOWN com isn2 running exige decisão do Gestor (não consertar à mão sem o Senior)" |
| R-7 | Comportamento do `tee` sob Ctrl-C provado só no Git Bash | No uso real: depois de um Ctrl-C, conferir `pgrep -af 'saude\|session-workload\|podman events'` antes de seguir. Opcional: `exec > >(trap '' INT; tee -a …)` |

## 8. Perguntas ao Gestor

1. Aprova D-1..D-6, 8a, 8b e 9 conforme a seção 5, e E-1..E-7 conforme as recomendações do prompt?
2. Exige R-1 (2 casos de teste) antes do *bundle* v4h, como recomendo, ou aceita levar sem eles?
3. Aceita incluir as opcionais R-3, R-6 e R-7 (`tee` ignorando INT) no mesmo ciclo do Worker, ou deixa como estão?
