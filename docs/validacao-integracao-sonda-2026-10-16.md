# Validacao V-I: integracao local da etapa formal e sonda Hot Rod (2026-10-16)

Senior. Sem push, nada executado na VM, nenhum codigo alterado. Medicoes no host (Git Bash; WSL Ubuntu com `flock` real) em checkouts limpos (`git status` vazio antes e depois).

## A. Integracao local `feature/etapa-formal-integracao` (e6e5985)

**Veredito: APROVADA** (ressalvas menores, nenhuma bloqueante).

| Verificacao | Resultado |
|---|---|
| Historico | 52 commits sobre `8e2e815` (51 + 1 docs), 0 merges, linear |
| Commits das origens | 53 commits unicos nas 9 pontas; patch-id identico para 47. Os 6 restantes sao os esperados: 3d1c0a9, a646b01, 7c33965 (`docs/testes.md`, mesmo numstat, muda so o texto de resultado/ordem), 946a2f6 (T-par-18, `unset` de shims unificado), 48d1571 (B-32, `run-tlc-propriedades.sh` ajustado pela correcao rc=150 posterior), 906f5b0 (docs de `main`, pertence a base da branch eol, fora da integracao: correto) |
| Arquivos por origem | todos os arquivos alterados por cada origem existem na integracao com blob identico, exceto os editados por varias origens: `scripts/inject-partition.sh`, `inject-partition.test.sh`, `docs/testes.md`, `docs/configuracao-infinispan.md`, `spec/run-tlc-propriedades.sh`, `.gitattributes`. Neles, as linhas de uma origem ausentes na integracao sao so as substituidas por commits posteriores (soma de pacotes por sentido, guarda de netns no lugar de `resolver_pid`, timestamp por chamada, linhas "Resultado desta rodada" antigas); nenhuma funcionalidade perdida |
| `spec/` | diff 8e2e815..HEAD: so `MCSession.tla` (+84) e `run-tlc-propriedades.sh` (+110, B-32 e correcao rc=150); `SessionStore.tla` identico |
| Conflitos | `inject-partition.test.sh`: T-par-16/17 e depois T-par-18 mantidos; `docs/testes.md`: as duas secoes e linha de resultado consolidada (125/0/2, 127/0/1) |
| LF / modo | `git ls-files --eol`: 0 arquivos `i/crlf` ou `i/mixed`; 206 arquivos com `eol=lf`, todos `w/lf`. 9 scripts 100755 (inject-*, inject-*.test.sh, run-baseline.sh, spec/run-tlc.sh); 9 `.sh` 100644 (`runs/gate-b29/g*.sh`, `scripts/audit-*.sh`, `run-controle-*.sh`, `spec/run-tlc-invariantes.sh`, `spec/run-tlc-propriedades.sh`), assim ja na origem e invocados via `bash`: aceitavel |
| Origens intactas | reflog das 9 pontas: a ultima entrada de cada uma e o proprio commit da ponta, anterior a integracao; SHAs (6dc7cbe, b56c6ea, bbd6e42, 4b17cc4, a646b01, 4f09c3f, 5f7e0c1, 48d1571, 7ca5dfa) conferem |

Suites medidas por mim:

| Suite | Esperado | Medido |
|---|---|---|
| inject-partition (Git Bash / WSL) | 125/0/2 e 127/0/1 | 125/0/2 e 127/0/1 |
| inject-netdegrade (Git Bash / WSL) | 298/0/1 e 306/0/0 | 298/0/1 e 306/0/0 |
| inject-jitter (Git Bash) | 35/0 | 35/0 |
| `mvn -f workload/pom.xml test` | 184, 0 falhas, 2 skipped | 184, 0 falhas, 0 erros, 2 skipped |
| analysis | 9 | 9 OK com `python -m unittest discover -s analysis`; **`python -m unittest analysis` roda 0 testes ("NO TESTS RAN")**: corrigir o comando na documentacao |

**Ordem de integracao.** O Worker aplicou eol, B-29, gate, contadores, netns, B-29.2, auditoria(+B-32), B-33 (fixes antes de B-29.2). Decisao: **manter a ordem do Worker e ajustar a secao 5.3 do plano**. B-29.2 edita `inject-partition.test.sh` e `testes.md` sobre a base ja corrigida; integrar os fixes depois obrigaria a refazer conflitos em arquivos maiores, e a ordem do plano (b29, b29.2, b32, b33) nao previa o eol nem os dois fixes de V-B. Texto proposto para 5.3: "ordem: eol, b29, gate, fix contadores, fix netns, b29.2, auditoria(+b32), b33; cherry-pick de faixas; conflitos em inject-partition.test.sh e docs/testes.md mantem ambas as partes". O plano nao foi alterado aqui (aplicar na proxima edicao, com aval do Gestor).

Ressalvas A (nao bloqueiam): (1) comando `unittest` acima; (2) worktrees criados antes do `.gitattributes` devem ser refeitos (a nota do doc de integracao sobre 44 arquivos CRLF ja foi tratada); (3) o bundle da VM deve sair desta ponta, nao de `tmp/tb-vm-bundle` (ver C).

## B. Sonda Hot Rod `feature/b33-2-sonda-hotrod` (fb12c98)

**Veredito: APROVADA COM RESSALVAS** (executavel na VM depois das ressalvas 1 e 2, que sao de preparo, nao de codigo).

Medido: `mvn test` 205 testes, 0 falhas, 0 erros, 3 skipped (igual ao relatorio; os 3 `@Disabled` do C-2 seguem desabilitados, corretamente); `g1b.test.sh` falhas=0; `git status` limpo.

Leitura de codigo (g1b.sh, SondaHotrod, SondaCsv):
- Nao inicia injecao: nenhum `inject-*`, `tc`, `nsenter`, `systemd-run` nem `--heal`. Espera `event=start` (formato `fault=F2 node=.. event=start t_epoch_ns=..` confere com `inject-partition.sh:361`) e aborta com rc 2 apos 600 s sem marcador. No cluster so faz put/get/entrySet/remove de `sonda-<no>` e GET REST.
- Cliente: `BASIC`, um servidor, `socketTimeout` e `connectionTimeout` de 2000 ms, `maxRetries` 1, marshaller e allow-list do workload. Senha so por `SONDA_SENHA` (ausente aborta); no REST a credencial vai por `curl -K -` via stdin, fora do `ps`. O default `infinispan` do script e credencial de laboratorio: nao registrar em `go.log`.
- CSV com coluna `cache` (9 colunas), excecao e cadeia de causas integrais, RFC 4180; `Error` nao e engolido; `remove-final` no SIGTERM. O `grep -c ',ERRO,'` do resumo pode contar uma mensagem que contenha essa sequencia (improvavel).
- O pom gera jar sombreado (`maven-shade-plugin`) e o glob `session-workload-*.jar` nao pega `original-*`. Conferir `jar tf ... | grep SondaHotrod` antes do scp.
- `wait $(jobs -p | grep -v ...)` e fragil, mas funciona (coberto pelo teste); sem impacto de seguranca.

REST (Infinispan 15). Nao tenho a documentacao do 15 neste host, entao marco como **a verificar**: `GET /rest/v2/cache-managers/default/health` e `GET /rest/v2/caches/<c>?action=stats` sao conhecidos (health ja foi lido em T-B; stats e acao do REST v2). `GET .../cache-managers/default/caches` e `?action=get-availability` devem ser confirmados por um `curl --digest` de leitura na VM antes da REP=2 (passo 5 de C). Se der 404, o `indice.csv` registra e a disponibilidade vem do `health` e dos logs. Nao bloqueia, pois a coleta tolera falha de curl.

O que o desenho captura: a excecao que o cliente Hot Rod 15 lanca em `put/get/entrySet/remove` quando o no esta isolado/`DEGRADED_MODE`, com a mesma biblioteca e marshaller do workload. Isso basta para preencher `CLASSES_CAPTURADAS` e `MENSAGENS_CAPTURADAS` do C-2 e para decidir o criterio G-1 "Hot Rod a isn3 devolve erro, nao timeout" (comparando `duracao_ns` com 2 s). Divergencias em relacao ao que o workload veria, a declarar no relatorio da REP=2: (a) `maxRetries` 1 na sonda contra 3 no padrao do workload (medido: `PADROES-HOTROD ... maxRetries=3`); com retentativas a excecao final e a duracao mudam, logo a duracao da sonda subestima a do workload (aceitar `--max-retries` na sonda ou declarar o fator); (b) uma unica chave: com `DENY_READ_WRITES` o erro depende dos segmentos do no isolado, entao `OK` da sonda nao prova ausencia de erro em outras chaves (usar 2 ou mais chaves nas proximas REPs; nao bloqueia a REP=2); (c) cliente BASIC pinado, como S3 e leitores multi-replica, mas nao o de topologia do S1/S2; (d) o cache `counters` recebe `IdentityState` falso: remover `sonda-*` ao fim e nao rodar a varredura final do workload em paralelo.

O que NAO prova: comportamento sob carga, divergencia ou perda de dados no merge, a classificacao `ERROR_UNAVAILABLE` x `ERROR_TRANSPORT` (so registra), variancia (uma janela), particao de outro no, G-5/G-6, nem o caminho de rede host-container das aplicacoes reais; e a propria sonda perturba o cluster minimamente.

Ressalvas B: (1) bundle novo, nao o descrito no relatorio (ver C); (2) confirmar os dois endpoints antes; (3) divergencia de `maxRetries` declarada; (4) o relatorio da REP=2 deve dizer que sonda e coleta estavam ligadas.

## C. Bundle para a VM (ordem segura)

O relatorio da sonda manda fundir com `tmp/tb-vm-bundle`, que **nao contem os fixes de V-B** (contadores, netns, g1.sh A4) nem o `.gitattributes`. Substituir por sonda cherry-pickada sobre a integracao:

1. Host: worktree novo `tmp/vm-bundle-2` a partir de `feature/etapa-formal-integracao`; `git cherry-pick 7ca5dfa..fb12c98` (5 commits da sonda; B-33 ja esta na base, conflito improvavel). Anotar o SHA.
2. Host: `mvn -f workload/pom.xml test` (esperado 205/0/3), suites partition/netdegrade/jitter e `g1b.test.sh` na ponta combinada; `mvn package -DskipTests`; `jar tf` com `SondaHotrod`; `sha256sum` do jar e do bundle; `git bundle create` e `verify`. JDK do build >= 17 (o `java` do PATH e Java 8; usar o JDK 22 do Maven).
3. A VM precisa de `java -version` >= 17 (classe 61). Se faltar, instalar e mudanca na VM e exige go.
4. scp de bundle e jar, conferir sha256 na VM, `git fetch` do bundle e `merge --ff-only`, `grep -rlI $'\r' runs/gate-b29/*.sh scripts/*.sh | wc -l` igual a 0, `bash runs/gate-b29/g1b.test.sh` e as suites de partition (sem cluster).
5. Pre-voo somente leitura: `podman ps`, `health` com size=3 HEALTHY, `tc qdisc show` limpo em cada netns, `java -version`, os dois `curl --digest` de endpoint.
6. REP=2: terminal B (`g1b.sh`) primeiro, terminal A (`g1.sh`) depois, ambos sob `flock /var/lock/tcc-vm.lock`; dead-man armado pelo `g1.sh` antes da injecao; Gestor presente nos 90 s; ao fim, `tc` limpo, size=3, remover `sonda-*` de sessions e counters, trazer `g1-rep2/` inteiro ao host.

## D. Go do Gestor e ordem das proximas execucoes na VM

Go necessarios (cada um explicito, nenhum herdado):
- **Go-1**: transferencia (scp), fetch, suites sem injecao e pre-voo somente leitura (inclui instalar Java 17 se faltar).
- **Go-2**: REP=2 com sonda (uma injecao F2 de 90 s em isn3, g1b.sh ligado).
- **Go-3**: REP=3 (G-4 exige 3 ou mais janelas; mesma configuracao).
- **Go-4**: G-5 (`kill -TERM` e `kill -KILL` do injetor; isn3 fica cortado ate o `--heal`).
- **Go-5**: G-6 (coexistencia F2 e F3), so depois de G-5.
- **Go-6**: L-0 (sonda de modulos `prio`/`netem`/`u32`, reversivel, sem falha injetada).
- **Go-7**: L-1 e cada bloco seguinte de L-2 a L-6 que injete degradacao.

Ordem otima: Go-1, REP=2 (Go-2), REP=3 (Go-3), G-5 (Go-4), G-6 (Go-5), L-0 (Go-6), L-1 em diante (Go-7). Razoes: a REP=2 com sonda fecha a lacuna do G-1 e destrava o C-2 (classificador); a REP=3 reaproveita bundle e cluster ja saudavel; G-5 deixa isn3 cortado de proposito e G-6 depende dele, entao vem depois das janelas limpas; L-0 so com a particao estabilizada, para nao misturar causas; L-1 a L-6 por ultimo, pois exigem o roteiro corrigido (V-C1c) e o injetor F4. Entre execucoes: `tc` limpo, size=3 HEALTHY, sem residuos `sonda-*`.
