# Prompt de implementação do B-26.2: sonda de topologia (2026-10-27)

> Senior, 2026-10-27. Bloco do plano `docs/plano-implementacao-controlador-2026-10-22.md`, seção 6
> (linha B-26.2) e seção 3.2 (`S_top`), revisto à luz do que entrou em `feature/controlador-integracao`
> até `c055399` (B-28.1, B-26.1 com ressalvas, B-42.1 com Tarefa 0, bloco B-43, *runner* do
> *rebaseline*). Referências `arquivo:linha` lidas em `c055399`; o Worker as reconfere antes de
> começar. Roda em paralelo com B-27, B-37 e B-39 (arquivos disjuntos). Sem VM, sem push.

## 1. O que mudou desde o plano de 22/10 e afeta este bloco

1. **A sonda não pode morar inteira em `controle/`.** O plano previa `controle/SondaTopologia.java`
   "adaptador sobre `HealthRest`". Só que `HealthRest` está em `br.unipampa.tcc.session` e
   `ArquiteturaControleTest.java:28-30` reprova qualquer `import br.unipampa.tcc.session` em `controle/`.
   O bloco se divide em duas partes. Em `controle/` fica a sonda pura: classificação, rodada, agenda e
   resumo por janela, sem processo nem rede. Em `session/` fica o adaptador que chama `curl` e
   reaproveita `HealthRest.interpretar` e `HealthRest.configCurl`.
2. **Nomes de *cache* vêm do perfil (B-42.1).** `PerfilReplicacao.caches()` (`PerfilReplicacao.java:48-50`)
   é a única fonte. `NomesDeCacheSoNoResolvedorTest` varre **todo** `workload/src/main`, inclusive
   `controle/`, e reprova literal `"sessions"`/`"counters"` fora de `PerfilReplicacao.java`.
   `PerfilFixoForaDoResolvedorTest` reprova `PerfilReplicacao.SYNC`/`.ASYNC` e
   `porRotulo("...")` com literal fora de dois *defaults* permitidos. A sonda recebe a lista de
   *caches* pronta. A CLI de *smoke* exige `--replication`, sem *default* (seção 4.4).
3. **Contrato herdado da V-B28.1 (seção 6):** falha de consulta REST conta como amostra **anômala**,
   com `EstadoTopologia.disponivel = true`. `disponivel = false` fica reservado para "sem sonda
   configurada". `number_of_nodes < 3` e *cache* diferente de `HEALTHY` são classificados na sonda.
   O núcleo só vê a contagem (`EstadoTopologia.java:13`, `anomala() = disponivel && anomalas > 0`).
4. **Tempo:** o *tick* de 1 s e o `L` = 100 ms entraram no B-26.1. A sonda usa a mesma `FonteDeTempo`
   (`controle/FonteDeTempo.java`) e carimba cada amostra no mesmo relógio dos `end_ns`. A *thread* de
   *tick* que chama `resumirAte` é do B-28.2. Este bloco só entrega a API.
5. **Prazo por rodada.** `HealthRest.consultar` usa `--max-time 5` e `waitFor(10 s)`
   (`HealthRest.java:70-71`, `:77`). Com isso uma rodada sequencial sob partição pode passar de 15 s, mais
   que 3 janelas de 5 s. A sonda consulta os nós **em paralelo**, com prazo por rodada menor que o
   período. Nó que não respondeu no prazo conta como anômalo (`timeout`).

## 2. Escopo

**Entra:** classificação de uma leitura de saúde; rodada paralela com prazo; agenda periódica numa
*thread daemon*; resumo por janela para o núcleo (`EstadoTopologia`), com amostras atrasadas
promovidas; ouvinte de amostra para o JSONL futuro; adaptador REST via `curl`; derivação das URLs a
partir de `--servers`; CLI de *smoke*.

**Não entra:** `Cli.java`, `WorkloadMain.java`, `SessionOps.java`, `HealthRest.java` (só se
reaproveitam os métodos existentes), gravação de JSONL, *thread* de *tick*, opção `--controller*`
(tudo isso é B-28.2); *replay* de `saude-log.csv` (B-38).

## 3. Prompt para o Worker

````text
Você é o WORKER. Uma entrega, B-26.2 (sonda de topologia). Commits só locais. NÃO faça push. NÃO use a
VM. NÃO use tc/netem.

BASE: c055399 (ponta de feature/controlador-integracao). Branch feature/b26-2-sonda-topologia,
worktree ../wt-b26-2 (git worktree add ../wt-b26-2 -b feature/b26-2-sonda-topologia c055399).

PASSO 0 (antes de qualquer edição):
 a) `mvn -q -o -f workload/pom.xml clean test` na base. Some os TEST-*.xml e anote arquivos, testes,
    falhas, erros e ignorados. Esperado: 106 arquivos, 761/0/0/1. Se der outro número, PARE e reporte.
 b) Confira que existem como descritas: ArquiteturaControleTest.java:28-30 (padrão de import proibido)
    e :32-36 (TIPOS_DO_NUCLEO); EstadoTopologia.java:13 e :33-35; HealthRest.java (interpretar,
    configCurl package-private, consultar com --max-time 5); PerfilReplicacao.caches();
    NomesDeCacheSoNoResolvedorTest (LITERAL, CHAMADA, ARG_OK); PerfilFixoForaDoResolvedorTest
    (PERMITIDOS só Cli.java e SondaHotrod.java); Cli.java --servers (padrão
    127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224); captura-real/*.json (4 arquivos). Se alguma
    divergir, PARE e reporte.

PAUSA OBRIGATÓRIA (regra 5.1 de docs/decisoes-senior-2026-10-16.md): quando o coordenador avisar uma
janela de injeção na VM ou uma noite de campanha (piloto/rebaseline/T-F), interrompa mvn, python
pesado e smoke Podman, e não retome antes da liberação expressa (inclui 5 min antes e depois). Se
um mvn estiver rodando no aviso, deixe terminar só se faltar menos de 1 min; senão, interrompa e
rode de novo depois.

ARQUIVOS PERMITIDOS (só estes; todos NOVOS, salvo o relatório):
  workload/src/main/java/br/unipampa/tcc/controle/FonteDeSaude.java
  workload/src/main/java/br/unipampa/tcc/controle/LeituraSaude.java
  workload/src/main/java/br/unipampa/tcc/controle/ResultadoNo.java
  workload/src/main/java/br/unipampa/tcc/controle/AmostraTopologia.java
  workload/src/main/java/br/unipampa/tcc/controle/SondaTopologia.java
  workload/src/main/java/br/unipampa/tcc/session/ConsultaSaudeRest.java
  workload/src/main/java/br/unipampa/tcc/session/SondaTopologiaCli.java
  workload/src/test/java/br/unipampa/tcc/controle/SondaTopologia*Test.java (quantos precisar)
  workload/src/test/java/br/unipampa/tcc/controle/ArquiteturaSondaTest.java
  workload/src/test/java/br/unipampa/tcc/session/ConsultaSaudeRest*Test.java
  workload/src/test/resources/captura-sintetica/*.json (derivados das capturas reais; ver T3)
  runs/b26-2-smoke/** (brutos do smoke, MANIFEST.sha256, analise.py)
  docs/relatorio-b26-2-<data>.md
ARQUIVOS PROIBIDOS (não tocar, nem formatação): qualquer outro arquivo existente, em especial
  Cli.java, WorkloadMain.java, SessionOps.java, HealthRest.java, PerfilReplicacao.java,
  SondaHotrod.java, EstadoTopologia.java, Controlador.java, AgregadorDeJanela.java,
  cluster/*, analysis/*, scripts/*, runs/rebaseline/*, .gitattributes, docs/* existentes
  (docs/implementacao.md e docs/testes.md entram na integração, não aqui).
Ao terminar: `git diff --name-status c055399 HEAD` só com A (adicionados) + o relatório.

DESENHO EXIGIDO (nomes públicos fixos; B-28.2 depende deles):

 controle/LeituraSaude: record LeituraSaude(int numeroDeNos, Map<String,String> statusPorCache),
   cópia imutável ordenada; numeroDeNos >= 0.
 controle/FonteDeSaude: interface { int nos(); String rotulo(int indice);
   LeituraSaude consultar(int indice, long prazoNs) throws Exception; }
   (uma consulta a UM nó; deve respeitar o prazo; exceção = falha de consulta).
 controle/ResultadoNo: record ResultadoNo(String no, boolean respondeu, int numeroDeNos,
   String motivo, boolean anomalo). numeroDeNos = -1 se não respondeu. motivo é um destes, sem
   vírgula: "ok", "timeout", "falha:<NomeSimplesDaExcecao>", "nos:<n><<esperados>",
   "cache-ausente:<nome>", "cache:<nome>=<status>". Com mais de um motivo, o primeiro na ordem
   timeout/falha > nos > cache-ausente > cache (determinístico, caches em ordem da lista do perfil).
 controle/AmostraTopologia: record AmostraTopologia(long tNs, boolean anomala, int nosMinimo,
   List<ResultadoNo> porNo). anomala = algum nó anômalo. nosMinimo = menor numeroDeNos entre os nós
   que responderam; 0 se nenhum respondeu. tNs = instante em que a rodada TERMINOU (a informação só
   existe a partir daí), lido da FonteDeTempo.
 controle/SondaTopologia implements AutoCloseable:
   - construtor (FonteDeSaude fonte, List<String> cachesDoPerfil, int nosEsperados, long periodoNs,
     long prazoRodadaNs, FonteDeTempo tempo, Consumer<AmostraTopologia> ouvinte). Rejeita: lista de
     caches vazia ou com nome vazio, nosEsperados < 1, periodoNs <= 0, prazoRodadaNs <= 0,
     prazoRodadaNs >= periodoNs, ouvinte nulo (use x -> {} para nenhum).
   - static ResultadoNo classificar(String no, LeituraSaude leituraOuNull, String motivoFalha,
     List<String> caches, int nosEsperados): função pura. Anômalo se: não respondeu; ou
     numeroDeNos < nosEsperados; ou algum cache DA LISTA ausente da resposta; ou algum cache DA LISTA
     com status diferente de exatamente "HEALTHY" (HEALTHY_REBALANCING, DEGRADED, qualquer outro).
     Caches fora da lista (___protobuf_metadata, ___script_cache, os irmãos do outro perfil) são
     IGNORADOS.
   - AmostraTopologia rodada(): consulta os fonte.nos() nós EM PARALELO (um executor próprio de
     tamanho nos(), threads daemon com nome "sonda-topologia-no-<i>"), espera no máximo
     prazoRodadaNs no total; nó sem resposta no prazo vira "timeout" e a sua tarefa é cancelada
     (interrupção). Entrega a amostra ao ouvinte e ao buffer interno. Uma exceção do ouvinte é
     capturada e contada (errosOuvinte()), nunca derruba a sonda.
   - void iniciar(): agenda rodada() a taxa fixa de periodoNs numa thread daemon "sonda-topologia".
     Se a rodada anterior ainda não terminou no instante da próxima, a próxima é PULADA e contada
     (rodadasPuladas()); nunca há duas rodadas simultâneas (no máximo nos() consultas em voo).
     iniciar() duas vezes lança IllegalStateException.
   - synchronized EstadoTopologia resumirAte(long fimNs): consome TODAS as amostras do buffer com
     tNs < fimNs que ainda não foram consumidas, inclusive as de janelas anteriores que chegaram
     tarde (essas contam também em amostrasAtrasadas()). Devolve
     new EstadoTopologia(true, consumidas, anomalasConsumidas, nosMinimoConsumidas ou 0 se nenhuma).
     Amostra com tNs >= fimNs fica para a próxima chamada. fimNs que recua lança
     IllegalArgumentException. Nunca devolve disponivel = false (esse é o caso "sem sonda", que fica
     com o chamador, via EstadoTopologia.indisponivel()).
   - close(): para a agenda, interrompe rodadas em curso, aguarda o término das threads (prazo de
     2·prazoRodadaNs) e é idempotente. Depois de close, resumirAte continua respondendo com o que já
     está no buffer; iniciar() lança IllegalStateException.
   - contadores: rodadas(), rodadasPuladas(), amostrasAtrasadas(), errosOuvinte().
 session/ConsultaSaudeRest implements controle.FonteDeSaude:
   - static List<String> urlsDe(String servidores): "h:p,h:p,..." -> "http://h:p" na mesma ordem;
     espaços nas pontas aceitos; item sem porta, porta não numérica ou fora de 1..65535, ou lista
     vazia: IllegalArgumentException com o item.
   - construtor (List<String> urls, String usuario, String senha, LancadorCurl lancador), em que
     LancadorCurl é uma interface package-private do adaptador
     (Process lancar(List<String> comando) throws IOException), com o padrão = ProcessBuilder.start.
   - consultar(i, prazoNs): curl -s --fail --digest --max-time <ceil(prazo em s), mínimo 1> -K -
     <url>/rest/v2/cache-managers/default/health; credenciais SÓ por stdin com
     HealthRest.configCurl (a senha nunca aparece na lista do comando); espera o processo até o
     prazo e, se passar, destroyForcibly + exceção de timeout; exit != 0: IOException com o exit;
     corpo interpretado por HealthRest.interpretar e convertido em LeituraSaude. Não chame
     HealthRest.Estado.healthy(...) (a guarda CHAMADA exige argumento do perfil; aqui não há).
 session/SondaTopologiaCli (main de smoke, sem nenhum default de perfil):
   java -cp <jar> br.unipampa.tcc.session.SondaTopologiaCli --servers h:p,... --replication sync|async
     [--usuario admin] [--periodo-ms 2000] [--prazo-ms 1500] [--duracao-s 60]
   senha da variável de ambiente SONDA_SENHA (como SondaHotrod; ausente = exit 2). Perfil por
   PerfilReplicacao.porRotulo(valorLidoDaCli) (variável, nunca literal). Escreve uma linha JSON por
   amostra em stdout: {"t_epoch_ns":..,"anomala":..,"nos_min":..,"por_no":[{"no","respondeu",
   "numero_de_nos","motivo","anomalo"}]} e, no fim, {"resumo":{"rodadas","puladas","anomalas"}}.
   Relógio: EpochClock.capturarAgora() + paraEpochNanos(System.nanoTime()) (mesmo relógio dos
   end_ns). --replication ausente ou inválido: exit 2.

ORDEM DOS COMMITS (TODO commit compila: `mvn -q -o -f workload/pom.xml test-compile` com código 0;
sem --amend; histórico linear; nenhum commit que não compile):
 1. refactor: tipos e esqueletos. LeituraSaude, ResultadoNo, AmostraTopologia e FonteDeSaude
    completos (são só dados e contrato). SondaTopologia, ConsultaSaudeRest e SondaTopologiaCli com
    as assinaturas finais e corpo provisório: classificar devolve sempre ResultadoNo não anômalo
    "ok"; rodada() devolve amostra vazia; resumirAte devolve EstadoTopologia(true, 0, 0, 0);
    urlsDe devolve List.of(); consultar lança UnsupportedOperationException. Nenhum teste novo.
    Surefire = 761/0/0/1.
 2. test: todos os testes novos (seção TESTES). Rode o Surefire completo e REGISTRE no relatório
    quais ficaram vermelhos e com que mensagem. Esperado: vermelhos em T1..T13, exceto os que só
    exercitam os records. Os 761 existentes continuam verdes.
 3. feat: implementação de controle/ (classificar, rodada, agenda, resumo, close).
 4. feat: ConsultaSaudeRest e SondaTopologiaCli. Ao fim deste commit: Surefire = 761 + novos, 0
    falhas, 0 erros, 1 ignorado.
 5. test: smoke local (seção SMOKE) com brutos e analise.py.
 6. docs: relatório.
Se precisar dividir 3 ou 4, cada pedaço continua compilando e o relatório diz qual teste ficou
verde em qual commit.

TESTES NOVOS (JUnit 5; relógio falso; nenhum sleep real acima de 200 ms por teste; a suíte nova
inteira em menos de 15 s):
 T1 contrato V-B28.1: FonteDeSaude que lança IOException para um nó ⇒ ResultadoNo anômalo
    "falha:IOException"; a amostra é anômala; resumirAte dá EstadoTopologia com disponivel = true e
    anomala() == true. Todos os nós falhando ⇒ nosMinimo = 0 e ainda disponivel = true.
 T2 capturas reais (workload/src/test/resources/captura-real, lidas sem cópia), perfil sync
    (lista = PerfilReplicacao.SYNC.caches(); nos testes o literal é permitido):
      rep3-isn3-healthy-3nos.json   ⇒ não anômalo, "ok", numeroDeNos 3
      rep3-isn1-healthy-2nos.json   ⇒ anômalo, "nos:2<3"
      rep3-isn3-degraded-1no.json   ⇒ anômalo, "nos:1<3" (o primeiro motivo pela ordem)
      rep2-isn1-healthy-rebalancing-3nos.json ⇒ anômalo, "cache:sessions=HEALTHY_REBALANCING"
 T3 perfil async: crie em captura-sintetica/ cópias de rep3-isn3-healthy-3nos.json com os quatro
    caches (sessions, counters, sessions-async, counters-async), documentadas como SINTÉTICAS no
    relatório (de qual captura vieram e o que mudou). Casos: só sessions (sync) DEGRADED e perfil
    async ⇒ não anômalo; só counters-async HEALTHY_REBALANCING e perfil async ⇒ anômalo
    "cache:counters-async=HEALTHY_REBALANCING"; counters-async ausente ⇒ "cache-ausente:counters-async";
    ___protobuf_metadata HEALTHY_REBALANCING e o resto HEALTHY ⇒ não anômalo.
 T4 fronteira de nós: numeroDeNos = nosEsperados ⇒ não anômalo; nosEsperados - 1 ⇒ anômalo.
 T5 ordem dos motivos: leitura com 2 nós e cache DEGRADED ⇒ motivo "nos:2<3"; com 3 nós e dois
    caches problemáticos ⇒ o do primeiro cache da lista.
 T6 agregação: 3 nós, só o 2º anômalo ⇒ amostra anômala; nenhum anômalo ⇒ não anômala;
    nosMinimo = mínimo entre os que responderam (casos 3,2,3 ⇒ 2; 3,falha,3 ⇒ 3).
 T7 prazo: fonte em que o nó 1 bloqueia até ser interrompido e os outros respondem na hora ⇒ a
    rodada termina em no máximo prazoRodadaNs + 100 ms (relógio real, prazo de 150 ms no teste), nó
    1 "timeout", os outros "ok", e a tarefa bloqueada recebeu interrupção (latch).
 T8 janelas: com relógio falso, amostras em t = 1, 3, 5, 7 s e resumirAte(5 s) ⇒ 2 amostras
    (fronteira: tNs == fimNs fica para depois); resumirAte(10 s) ⇒ 2. Amostra com t = 4 s
    entregue DEPOIS de resumirAte(5 s) ⇒ entra em resumirAte(10 s) e amostrasAtrasadas() == 1.
    fimNs que recua ⇒ IllegalArgumentException.
 T9 janela sem amostra ⇒ EstadoTopologia(true, 0, 0, 0) e anomala() == false (registre no
    relatório que esse caso é neutro por desenho e que o prazo < período o torna raro).
 T10 agenda: com a agenda real, período 100 ms e prazo 50 ms, fonte instantânea: em 1 s há entre 8
    e 12 rodadas; fonte que demora 150 ms numa rodada só ⇒ rodadasPuladas() >= 1 e nunca há mais
    de nos() consultas simultâneas (contador atômico na fonte de teste, máximo observado).
 T11 ciclo de vida: depois de close(), nenhuma thread com nome iniciado por "sonda-topologia" fica
    viva (Thread.getAllStackTraces, espera de até 1 s); close() duas vezes não lança; iniciar()
    depois de close() e iniciar() duas vezes lançam IllegalStateException; todas as threads da sonda
    são daemon.
 T12 ouvinte: recebe cada amostra uma vez, na ordem das rodadas; ouvinte que lança não para a
    agenda e errosOuvinte() conta.
 T13 adaptador (LancadorCurl falso, sem curl real): o comando contém "--digest", "-K", "-",
    "--max-time" e a URL .../rest/v2/cache-managers/default/health; NÃO contém a senha nem
    "usuario:senha" em nenhum argumento; o stdin recebido é exatamente HealthRest.configCurl(u, s);
    exit 22 ⇒ IOException com "22"; processo que não termina no prazo ⇒ destroyForcibly chamado e
    exceção de timeout; corpo JSON da captura real ⇒ LeituraSaude igual a HealthRest.interpretar.
 T14 urlsDe: padrão da Cli ⇒ 3 URLs http://127.0.0.1:1122{2,3,4} na ordem; " h:1 , g:2 " aceito;
    "h", "h:x", "h:0", "h:70000", "" ⇒ IllegalArgumentException citando o item.
 T15 arquitetura (ArquiteturaSondaTest, arquivo NOVO): aplica
    ArquiteturaControleTest.importsProibidos (package-private, mesmo pacote) às fontes
    SondaTopologia, FonteDeSaude, LeituraSaude, ResultadoNo e AmostraTopologia e exige lista vazia;
    exige que as cinco existam (não vacuidade). NÃO edite ArquiteturaControleTest: ele já varre
    todo controle/ e pega import proibido em qualquer arquivo novo.
 T16 CLI: sem --replication ⇒ exit 2; "--replication xyz" ⇒ exit 2; sem SONDA_SENHA ⇒ exit 2
    (teste pelo método de parse, sem rede).

TESTES EXISTENTES: nenhum pode ser editado. Atenção especial a NomesDeCacheSoNoResolvedorTest e
PerfilFixoForaDoResolvedorTest (varrem os seus arquivos novos de main: nenhum literal de nome de
cache, nenhum PerfilReplicacao.SYNC/ASYNC, nenhum porRotulo("...") com literal) e a
ArquiteturaControleTest. Se QUALQUER teste existente exigir edição, PARE antes do commit e reporte
arquivo:linha, asserção e motivo: cada edição exige autorização do Gestor, caso a caso.

MUTANTES (script no scratchpad aplicando um por vez sobre cópia `git archive` da ponta; Surefire
completo por mutante; fonte restaurado; liste o(s) teste(s) que mataram cada um; todos precisam
morrer, ou o relatório explica por que é equivalente):
 M1 falha de consulta vira não anômala                         → T1
 M2 falha de consulta vira EstadoTopologia(false, ...)          → T1
 M3 limiar de nós com <= (3 de 3 vira anômalo)                  → T2, T4
 M4 status aceito por startsWith("HEALTHY")                     → T2, T3
 M5 cache ausente aceito                                        → T3
 M6 classificar olha todos os caches da resposta                → T3
 M7 amostra anômala só se TODOS os nós anômalos                 → T6
 M8 nosMinimo = máximo                                          → T6
 M9 tNs == fimNs entra na janela                                → T8
 M10 amostra atrasada descartada em vez de promovida            → T8
 M11 rodada sequencial sem prazo por nó                         → T7
 M12 close() não interrompe/aguarda as threads                  → T11
 M13 rodadas sobrepostas permitidas (sem pular)                 → T10
 M14 senha passada por "-u usuario:senha" no comando            → T13
 M15 urlsDe ignora a porta (sempre 11222)                       → T14
 M16 tNs = início da rodada                                     → teste novo que o Worker escreve
     se nenhum dos acima o matar (fixe o instante de fim com relógio falso)

SMOKE LOCAL (Podman local, NÃO a VM; só depois do commit 4):
 - Peça ao coordenador a vez no Podman (os smokes do B-27 e do B-37 também usam a máquina
   podman-machine-default). NÃO pare nem reinicie a máquina Podman; se ela estiver parada, peça ao
   coordenador para iniciá-la.
 - podman-compose -p b262, contêineres b262-isn1..3, portas 12262..12264, cluster/*.xml da base
   copiados em runs/b26-2-smoke/cluster/. `podman ps -a` e `podman network ls` antes e depois,
   iguais ao final (diff vazio).
 - Jar construído da árvore limpa do commit 4 (SHA-256 em runs/b26-2-smoke/jars.json).
 - P1: cluster HEALTHY 3 nós, SondaTopologiaCli --replication sync --duracao-s 30 ⇒ 0 amostras
   anômalas. P2: idem --replication async ⇒ 0 anômalas. P3: --replication async --duracao-s 120;
   aos +30 s `podman stop b262-isn3`; aos +60 s `podman start b262-isn3` (é queda local de
   contêiner, não injeção na VM). Critérios: primeira amostra anômala até 2 períodos + prazo depois
   do stop (carimbo do stop por `date +%s%N` gravado num arquivo ao lado); o nó parado com
   "timeout" ou "falha:..."; os outros com "nos:2<3" depois que a vista mudar; volta a 0 anômalas
   depois do start (registre quanto tempo o HEALTHY_REBALANCING durou); rodadasPuladas = 0.
 - Custo: conte os processos curl lançados (log do LancadorCurl ou contagem por rodada) e confirme
   3 por rodada; meça o tempo de CPU do processo da CLI (/usr/bin/time ou ps no fim) e registre.
 - analise.py (unittest próprio, test_analise.py) confere os critérios a partir do stdout JSONL.
   Brutos com MANIFEST.sha256. "Smoke local: tempos não são evidência de detecção na VM."

RELATÓRIO docs/relatorio-b26-2-<data>.md: Surefire do passo 0 e por commit (com os vermelhos do
commit 2 e as mensagens), test-compile por commit, mutantes M1..M16, smoke (tabela P1..P3, tempos,
custo), `git diff --name-status c055399 HEAD`, confirmação de nenhum teste existente editado
(`git diff --stat c055399 HEAD -- workload/src/test` só com A), desvios e pontos para o Gestor/Senior.
Ao terminar, liste SHAs, worktree e `git status`, e pare. Não integre nem faça push.
````

## 4. Critérios de aceite

1. Surefire = 761 + novos, 0 falhas, 0 erros, 1 ignorado; nenhum teste existente editado.
2. Todo commit compila; o commit `test:` tem os vermelhos registrados com mensagem.
3. M1..M15 mortos (M16 morto ou justificado como equivalente).
4. Contrato V-B28.1: falha REST ⇒ `disponivel = true` e `anomala() == true` (T1).
5. `controle/` sem `import` de `session` nem `org.infinispan`; nenhum literal de nome de *cache* nem
   perfil fixo em `main`.
6. No máximo `nos()` processos `curl` em voo e 3 por rodada de 2 s; senha nunca na linha de comando.
7. *Smoke*: P1/P2 sem anomalia, P3 detecta a queda e a volta; máquina Podman devolvida ao estado
   inicial.

## 5. O que fica para o B-28.2 (registrar no plano)

- `LacoDeControle` chama `sonda.resumirAte(fim_j)` a cada janela fechada e passa o resultado ao
  núcleo; sem sonda configurada, `EstadoTopologia.indisponivel()`.
- O ouvinte de amostra grava as amostras no JSONL (`top.amostras`, `top.anomalas`, `top.nos_min`,
  `top.status`) e, para *replay*, uma linha `{"tipo":"topologia",...}` por amostra.
- Opções `--controller-topology-period-ms` e `--controller-topology-deadline-ms` (padrões 2000 e 1500),
  URLs de `ConsultaSaudeRest.urlsDe(cli.servidores)`, *caches* de `cli.replicacao.caches()`.
- CA-T4 da V-B26.1 (carga parada 30 s e sonda anômala ⇒ R2 por `S_top` em no máximo `W + L + Δ`)
  usa esta API com relógio falso.
