# Prompt de implementação do B-27: portão de emissão e *knobs* K1..K4 (2026-10-27)

> Senior, 2026-10-27. Bloco do plano `docs/plano-implementacao-controlador-2026-10-22.md`, seções 3.4
> (*knobs*), 4 (restrição §4) e 6 (linha B-27), revisto à luz de `feature/controlador-integracao` em
> `c055399`. Referências `arquivo:linha` lidas em `c055399`; o Worker as reconfere. Roda em paralelo
> com B-26.2, B-37 e B-39. É o único bloco desta onda que toca `WorkloadMain.java`. Sem VM, sem push.

## 1. O que mudou desde o plano de 22/10 e afeta este bloco

1. **A assinatura de `rodarCliente` está congelada por testes existentes.** `MisturaSemeadaTest.java:215-217`
   chama o método por reflexão com exatamente 10 parâmetros (`SessionOps, int, long, Scenario, long,
   long, AtomicLong, BooleanSupplier, VersaoMistura, MisturaV3`). `MisturaSemeadaFonteTest.java:53-57`
   e `TokenAntigoFonteTest.java:48-49` leem o corpo do **primeiro** método que casa com
   `private static void rodarCliente\(` (`DetectorFonte.corpo`, `DetectorFonte.java:112-120`).
   `TokenAntigoFonteTest.java:92-101` exige no corpo de `executarRepeticao` a linha
   `final long seedThread = sementeThread(cli.seedBase, tid, rep);` e o trecho
   `seedThread, opsAlvoPorThread, totalOps, parar, cli.mix, misturaV3`. Para não editar nenhum desses
   testes, o desenho é este (seção 3, decisão D1 do Gestor pendente):
   - um método novo `private static void rodarCliente(..., MisturaV3 misturaV3, PortaoDeEmissao portao)`
     com 11 parâmetros, declarado **antes** do de 10, com o corpo de hoje mais as chamadas ao portão;
   - o de 10 parâmetros passa a ter uma linha só, que delega com `PortaoDeEmissao.livre()`;
   - `executarRepeticao` **não muda**: continua chamando o de 10. A troca do ponto de chamada
     (portão real quando `--controller active`) é do B-28.2, que já toca `WorkloadMain`.
2. **Os parâmetros dos *knobs* ficam fora de `ParametrosControlador`.** `ParametrosControlador.deJson`
   rejeita campo desconhecido, e o construtor canônico do *record* é chamado diretamente por
   `ParametrosControladorTest`, `ControladorPropriedadeTest`, `ClassificacaoTest` e `Roteiro` (9
   ocorrências em `c055399`). Acrescentar K1..K4 ali obrigaria a editar esses quatro arquivos de teste. Por isso nasce
   `ParametrosKnobs`, um *record* próprio com `padrao()`, `deJson(Path)`, `versao` e `sha256`, que
   segue o padrão de `ParametrosControlador`. Como os dois arquivos se combinam na CLI é decisão do
   B-28.2 (o plano de 22/10, seção 3.5, falava em um arquivo só).
3. **A latência percebida precisa de duas colunas, não de uma.** O desfecho primário da avaliação
   (plano, seção 8.3) é `t_fim_ns − t_ns` da ação S3. No braço *active*, K1 e K3 seguram a ação no
   cliente. Se só `t_fim_ns` for gravado, a análise não consegue separar espera no portão de
   latência do *grid*. Entram `t_fim_ns` e `espera_portao_ns`, no fim do cabeçalho. A definição do
   desfecho (com ou sem espera) é fixada no pré-registro B-40, e os dados servem às duas leituras.
4. **K3 é aplicado por nome de operação.** `Block`, `Unblock`, `IncrementFailure` e `ResetFailures`
   são adiados em CONSERVADOR, qualquer que seja o ator. Isso inclui o `Unblock` do "suporte" no pool
   L (`ContaLimiteS3.java:87`), que o plano não citava, e mantém a regra simples e verificável.
   `Login`, `Validate` e `Logout` nunca são adiados.
5. **Jitter de K4 sem estado e determinístico.** `AplicacaoS3` é compartilhada pelos atores
   (`ContextoS3.java:28`). Um `Random` compartilhado tornaria a sequência dependente da intercalação.
   O fator de *jitter* é uma função pura de `(sementeJitter, ator, inicioAcaoNs, tentativa)` via
   `MisturaV3.mix64`. Com relógio falso a sequência é reprodutível.
6. **NORMAL tem de ser idêntico a desligado.** `S3SimulacaoTest` (determinístico, com relógio falso)
   e os *golden* S1/S2 ficam sem edição. Testes novos comparam a simulação com portão em NORMAL
   permanente à simulação com `livre()`, ação por ação.

## 2. Escopo

**Entra:** `PerfilAtual`, `ParametrosKnobs`, `PortaoDeEmissao` (K1 admissão, K2 ritmo, K3 adiamento,
K4 *backoff*), `EventoK3`, `Dormidor`; ganchos em `rodarCliente` (sobrecarga), `AplicacaoS3`,
`ContextoS3` e `UsuarioVirtual`; `AcaoS3` com `tFimNs` e `esperaPortaoNs` e o CSV S3 com duas
colunas no fim; contrato documentado e teste de contrato em `analysis/`.

**Não entra:** CLI (`--controller*`), `executarRepeticao`, `executarS3` (a criação do portão real),
`LacoDeControle`, JSONL de K3 (o portão só entrega `EventoK3` a um ouvinte). Tudo isso é do B-28.2.
K5 está fora por decisão do Gestor (22/10).

## 3. Prompt para o Worker

````text
Você é o WORKER. Uma entrega, B-27 (portão de emissão e knobs K1..K4). Commits só locais. NÃO faça
push. NÃO use a VM. NÃO use tc/netem.

BASE: c055399. Branch feature/b27-portao-emissao, worktree ../wt-b27
(git worktree add ../wt-b27 -b feature/b27-portao-emissao c055399).

PASSO 0:
 a) `mvn -q -o -f workload/pom.xml clean test` na base; some os TEST-*.xml; esperado 106 arquivos,
    761/0/0/1. Outro número: PARE e reporte.
 b) `python -m unittest discover -s analysis -p "test_*.py"` na base; anote "Ran N tests" e OK.
 c) Confira em c055399, como descritos: WorkloadMain.java rodarCliente (:777-794, 10 parâmetros,
    `Random r = randomDaMistura(seed);`, laço com executarOperacao) e a chamada em executarRepeticao
    (:398-401); MisturaSemeadaTest.java:215-217; MisturaSemeadaFonteTest.java:53-57;
    TokenAntigoFonteTest.java:48-49 e :92-101; DetectorFonte.corpo (:112-120, primeiro match);
    AplicacaoS3.java:46-70; AcaoS3.java:20-30 (record de 8 componentes, HEADER, paraCsv);
    S3Registro.java:71-85; ContextoS3.java:23-29; UsuarioVirtual.java:55-56;
    AtorAdministrativoS3.java:59, :76; ContaLimiteS3.java:66, :75, :81, :87; RelogioS3.java;
    MisturaV3.mix64. Divergência: PARE e reporte.

PAUSA OBRIGATÓRIA (regra 5.1 de docs/decisoes-senior-2026-10-16.md): quando o coordenador avisar uma
janela de injeção na VM ou uma noite de campanha, interrompa mvn, python pesado e smoke Podman, e só
retome com liberação expressa (inclui 5 min antes e depois).

ARQUIVOS PERMITIDOS:
  NOVOS em workload/src/main/java/br/unipampa/tcc/controle/: PerfilAtual.java, ParametrosKnobs.java,
    PortaoDeEmissao.java, EventoK3.java, Dormidor.java
  ALTERADOS em workload/src/main/java/br/unipampa/tcc/session/: WorkloadMain.java (SÓ a sobrecarga
    de rodarCliente; nada em executarRepeticao, executarS3, Cli), AplicacaoS3.java, AcaoS3.java,
    S3Registro.java (só escreverCsv), ContextoS3.java, UsuarioVirtual.java (só pensarNs)
  NOVOS testes: workload/src/test/java/br/unipampa/tcc/controle/{PortaoDeEmissao*Test,
    ParametrosKnobsTest, PerfilAtualTest, ArquiteturaPortaoTest}.java;
    workload/src/test/java/br/unipampa/tcc/session/{PortaoS3*Test, PortaoS1S2*Test,
    AcaoS3FimTest}.java (nomes livres, todos NOVOS)
  NOVOS: docs/contrato-s3-acoes-v2.md; analysis/test_contrato_s3_acoes.py;
    analysis/fixtures/s3-acoes-v2-exemplo.csv; runs/b27-smoke/**; docs/relatorio-b27-<data>.md
ARQUIVOS PROIBIDOS: todos os outros, em especial Cli.java, SessionOps.java, InvariantAuditor.java,
  EventCsvWriter.java, S3Simulacao.java, S3Parametros.java, AtorAdministrativoS3.java,
  ContaLimiteS3.java, ParametrosControlador.java, Controlador.java, AgregadorDeJanela.java,
  cluster/*, scripts/*, analysis/*.py existentes (rebaseline.py, bootstrap_ic.py etc.),
  runs/rebaseline/*, .gitattributes, docs/* existentes.
  (AtorAdministrativoS3 e ContaLimiteS3 não mudam: o K3 atua dentro de AplicacaoS3.executar, pelo
  nome da operação.)

DESENHO EXIGIDO (nomes públicos fixos; B-28.2 depende deles):

 controle/PerfilAtual: AtomicReference<Perfil> com Perfil.NORMAL inicial; definir(Perfil) e atual();
   ouvintes de mudança (registrar(Runnable)) chamados FORA de qualquer lock do portão, depois da
   escrita; definir com o mesmo perfil não notifica.
 controle/Dormidor: interface { void dormirNs(long ns) throws InterruptedException; } com
   static Dormidor real() (LockSupport.parkNanos em laço, como RelogioS3.sistema()).
 controle/ParametrosKnobs: record (String versao, double k1FracaoConservador, long k2PausaConservadorNs,
   double k2MultPensarConservador, long k3AdiaMaxNs, long k3SondagemNs, double k4FatorBaseConservador,
   double k4JitterConservador, String sha256). padrao() = (VERSAO_PADRAO "provisorio-2026-10-22",
   0.5, 5 ms, 1.5, 30 s, 100 ms, 2.0, 0.5, null): os valores da seção 3.4 do plano. Validação:
   0 < k1 <= 1; k2PausaConservadorNs >= 0; k2Mult >= 1; k3AdiaMaxNs > 0; 0 < k3SondagemNs <=
   k3AdiaMaxNs; k4Fator >= 1; 0 <= k4Jitter < 1. deJson(Path) com o mesmo rigor de
   ParametrosControlador.deJson: campo desconhecido, ausente, tipo errado ou valor inválido =
   IllegalArgumentException citando o campo; sha256 do arquivo lido. Nenhum valor fixo fora de padrao().
 controle/EventoK3: record (String ator, String operacao, String alvo, long adiadoEmNs,
   long liberadoEmNs, String motivo), motivo ∈ {"saida-conservador", "prazo", "encerramento"}.
 controle/PortaoDeEmissao:
   - static PortaoDeEmissao livre(): singleton sem efeito (entrar devolve 0 sem bloquear; sair não
     faz nada; pausa 0; multiplicador 1.0; K3 não adia; K4 devolve a base). Nenhuma alocação por
     chamada.
   - construtor (PerfilAtual perfil, ParametrosKnobs k, int concorrenciaNominal, FonteDeTempo tempo,
     Dormidor dormidor, Consumer<EventoK3> ouvinteK3, long sementeJitter); concorrenciaNominal >= 1.
   - K1: long entrar() throws InterruptedException (bloqueia enquanto emVoo >= limite; devolve os ns
     esperados) e void sair(). limite = concorrenciaNominal em NORMAL e
     max(1, ceil(k1FracaoConservador * concorrenciaNominal)) em CONSERVADOR, relido a cada mudança de
     perfil (quem espera é acordado: signalAll). Redução de limite NÃO interrompe quem já entrou:
     emVoo pode ficar acima do limite até as saídas. sair() sem entrar() correspondente lança
     IllegalStateException.
   - K2: long pausaAposOperacaoNs() (0 em NORMAL; k2PausaConservadorNs em CONSERVADOR) e
     long aplicarPensar(long pensarNs) (em NORMAL devolve o argumento SEM nenhuma conta; em
     CONSERVADOR max(1 ms, round(pensarNs * k2Mult))).
   - K3: long aguardarAdministrativa(String ator, String operacao, String alvo) throws
     InterruptedException. Para Login/Validate/Logout ou em NORMAL devolve 0 na hora. Para
     Block/Unblock/IncrementFailure/ResetFailures em CONSERVADOR espera até o perfil voltar a NORMAL,
     ou até k3AdiaMaxNs desde o início da espera, ou até encerrar(), o que vier primeiro, sondando
     a cada k3SondagemNs pelo Dormidor (assim o teste usa relógio falso); depois emite um EventoK3
     com o motivo e devolve os ns esperados. NUNCA descarta: a operação sempre segue depois.
   - K4: long esperaRetentativaNs(String ator, long baseNs, int tentativa, long inicioAcaoNs).
     NORMAL: devolve baseNs (o chamador continua dobrando como hoje). CONSERVADOR:
     round(baseNs * k4FatorBaseConservador * (1 + k4JitterConservador * (2u - 1))) com
     u = (mix64(sementeJitter ^ mix64(ator.hashCode()) ^ mix64(inicioAcaoNs) ^ tentativa) >>> 11) * 0x1.0p-53.
     Não muda o número de tentativas.
   - void encerrar(): libera todos os que esperam em K1 e K3 e faz entrar()/aguardarAdministrativa()
     seguintes retornarem na hora; idempotente.
   - contadores: emVoo(), limiteAtual(), esperaK1TotalNs(), adiamentosK3().
 session/WorkloadMain: a sobrecarga de 11 parâmetros (corpo atual + portao.entrar() antes de
   executarOperacao, portao.sair() num finally, e, se pausaAposOperacaoNs() > 0, LockSupport.parkNanos
   da pausa). A de 10 parâmetros fica com uma linha:
   `rodarCliente(ops, tid, deadline, cenario, seed, opsAlvo, totalOps, parar, mix, misturaV3, PortaoDeEmissao.livre());`
   A de 11 vem ANTES da de 10 no arquivo (o DetectorFonte pega o primeiro match). A linha
   `Random r = randomDaMistura(seed);` continua exatamente uma vez no corpo da de 11. Interrupção em
   entrar(): restaura a flag e sai do laço.
 session/AplicacaoS3: construtor novo (RelogioS3, S3Parametros, S3Registro, PortaoDeEmissao); o
   antigo delega com livre(). Em executar: inicio = relogio.agoraNs(); espera =
   portao.aguardarAdministrativa(ator, operacao, alvo); cada tentativa fica envolvida por
   entrar()/sair() (finally), somando a espera; entre tentativas a espera passa a ser
   portao.esperaRetentativaNs(ator, espera, tentativas, inicio), e a dobra continua como hoje sobre a
   base. Em NORMAL o resultado é numericamente igual ao atual. tFim = relogio.agoraNs() depois da
   última tentativa. Registra AcaoS3 com tFimNs e esperaPortaoNs.
 session/ContextoS3: construtor novo com PortaoDeEmissao (o antigo delega com livre()); campo
   público final `portao`; app criado com o portão.
 session/UsuarioVirtual.pensarNs: `return ctx.portao.aplicarPensar(ContextoS3.exponencialNs(rng, ...));`
   (o sorteio continua igual, o mesmo consumo do rng).
 session/AcaoS3: dois componentes novos NO FIM, `long tFimNs, long esperaPortaoNs`. tFimNs >= tNs,
   esperaPortaoNs >= 0 (validação no construtor canônico). HEADER passa a
   "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns".
   paraCsv(long tEpochNs, long tFimEpochNs). S3Registro.escreverCsv converte os dois instantes com o
   mesmo EpochClock (sem relógio: grava os valores crus, como hoje).
 docs/contrato-s3-acoes-v2.md: as 10 colunas, unidades, que t_ns e t_fim_ns são epoch ns no relógio
   dos end_ns, que a latência percebida com espera é t_fim_ns − t_ns e sem espera é
   t_fim_ns − t_ns − espera_portao_ns, e que as 8 primeiras colunas não mudaram. Diagrama, se houver,
   em Mermaid.

ORDEM DOS COMMITS (TODO commit compila com `mvn -q -o -f workload/pom.xml test-compile`; sem
--amend; histórico linear):
 1. refactor: tipos novos de controle/ com as assinaturas finais e corpos provisórios (livre() sem
    efeito; o construtor do portão aceita os parâmetros, mas o portão se comporta como livre();
    ParametrosKnobs.padrao() completo e deJson lançando UnsupportedOperationException); AcaoS3 com
    os dois componentes novos (AplicacaoS3 grava tFimNs = tNs e espera 0) e HEADER/paraCsv novos;
    construtores novos de AplicacaoS3 e ContextoS3 delegando; a sobrecarga de rodarCliente com o
    corpo movido e o de 10 delegando com livre(); UsuarioVirtual com aplicarPensar. Comportamento
    idêntico: Surefire 761/0/0/1.
 2. test: testes novos (seção TESTES), inclusive analysis/test_contrato_s3_acoes.py. REGISTRE no
    relatório os vermelhos e as mensagens (esperados: K1..K4, P1..P3, F1..F2 e o contrato Python
    se depender de t_fim > t_ns).
 3. feat: PortaoDeEmissao, PerfilAtual e ParametrosKnobs completos.
 4. feat: AplicacaoS3 (K1, K3, K4, tFim, espera) e WorkloadMain (K1, K2 na sobrecarga). Surefire =
    761 + novos, 0/0/1. Python: N base + novos, OK.
 5. docs: contrato-s3-acoes-v2.md.
 6. test: smoke local e fixture analysis/fixtures/s3-acoes-v2-exemplo.csv (recorte de até 200
    linhas do smoke, com cabeçalho).
 7. docs: relatório.

TESTES NOVOS (JUnit 5 e unittest; relógio falso onde houver tempo; suíte nova em menos de 30 s):
 K1a reduz e aumenta: 8 threads reais, nominal 8, CONSERVADOR ⇒ o máximo de emVoo observado depois
     da troca (e depois que as que já estavam dentro saíram) é 4; volta a NORMAL ⇒ chega a 8; nenhuma
     permissão perdida nem duplicada (contador de entradas = de saídas no fim; emVoo() == 0).
 K1b arredondamento: nominal 1 em CONSERVADOR ⇒ limite 1 (nunca 0); nominal 3 com fração 0,5 ⇒ 2.
 K1c acordar todos: 6 threads presas com limite 1; troca para NORMAL ⇒ todas entram em até 1 s
     (mata signal() no lugar de signalAll()).
 K1d sair() em finally: operação que lança RuntimeException dentro do laço da sobrecarga ⇒ emVoo()
     volta a 0 (OpsGravadas/FakeCaches com uma operação que lança).
 K2a S1/S2: em NORMAL, a sobrecarga não chama parkNanos (contador via Dormidor ou ganho de tempo
     medido com um portão espião); em CONSERVADOR, uma pausa de k2PausaConservadorNs por operação.
 K2b S3: aplicarPensar em NORMAL devolve o argumento idêntico (inclusive 1 ms); em CONSERVADOR,
     ×1,5 com piso de 1 ms; a sequência de sorteios do rng do usuário é igual com e sem CONSERVADOR
     (mesmos u em ordem; o multiplicador age depois do sorteio).
 K3a adia e libera na saída: CONSERVADOR, Block do admin espera; ao definir NORMAL no instante t,
     a operação roda no máximo k3SondagemNs depois de t (relógio falso); EventoK3 com motivo
     "saida-conservador" e os instantes certos.
 K3b prazo: CONSERVADOR permanente ⇒ a operação roda em k3AdiaMaxNs (± k3SondagemNs), motivo "prazo".
 K3c nunca descarta: em todos os casos a chamada (Supplier) é feita exatamente o número de vezes
     que seria sem portão (conte as chamadas).
 K3d ordem por ator: duas operações administrativas seguidas do mesmo ator, ambas adiadas, chegam
     ao armazenamento na ordem de emissão.
 K3e escopo: Login/Validate/Logout em CONSERVADOR não esperam; Unblock do "suporte" espera (decisão
     da seção 1, item 4).
 K3f encerrar(): libera todos os adiados com motivo "encerramento", e aguardarAdministrativa
     seguinte devolve 0.
 K4a retentativas: em CONSERVADOR o número de tentativas e de chamadas é igual ao de NORMAL para a
     mesma sequência de erros (AplicacaoS3Test é a referência de comportamento; teste NOVO).
 K4b intervalo: com jitter 0,5 e fator 2, a espera fica em [base·2·0,5; base·2·1,5] em 10 000 casos;
     a média fica no IC de 99,9 % de base·2; NORMAL devolve a base exata.
 K4c determinismo: mesma (semente, ator, início, tentativa) ⇒ mesmo valor; mudar qualquer um dos
     quatro muda o valor em pelo menos 99 % de 1 000 casos.
 P1 NORMAL = desligado no S3: a mesma simulação de S3SimulacaoTest (MundoS3, relógio falso, mesma
     semente) com ContextoS3 com portão em NORMAL permanente e com livre() produz a MESMA lista de
     AcaoS3 (todos os 10 componentes) e as mesmas chamadas ao armazenamento.
 P2 NORMAL = desligado no S1/S2: para S1 e S2, v2 e v3, a sobrecarga com portão NORMAL e a de 10
     (livre) dão a mesma lista de chamadas (op, arg) num MundoV3 novo, como W6/W7 (reflexão com 11
     parâmetros para a nova).
 P3 restrição §4: em CONSERVADOR, numa simulação S3 com relógio falso, o multiconjunto de operações
     emitidas por ator (até o mesmo instante lógico de fim) é igual ao de NORMAL, salvo a ordem no
     tempo; nada é criado nem suprimido (K3 só atrasa).
 F1 AcaoS3: tFimNs = instante depois da última tentativa (relógio falso: inclui as esperas de
     backoff e de K3); esperaPortaoNs = soma das esperas K1 e K3; tFimNs < tNs ou espera < 0 ⇒
     IllegalArgumentException.
 F2 CSV: escreverCsv com EpochClock conhecido grava o HEADER novo e as duas colunas convertidas;
     as 8 primeiras colunas iguais às de antes para a mesma ação.
 J1 ParametrosKnobs.deJson: válido; campo desconhecido; campo ausente; cada validação; sha256.
 J2 PerfilAtual: inicial NORMAL; ouvinte chamado uma vez por mudança, fora do lock; definir igual
     não notifica.
 A1 arquitetura (ArquiteturaPortaoTest, NOVO): ArquiteturaControleTest.importsProibidos vazio nos 5
     arquivos novos de controle/; os 5 existem.
 Py analysis/test_contrato_s3_acoes.py (unittest, só biblioteca padrão): o cabeçalho da fixture é
     exatamente o do contrato; 10 colunas por linha; t_fim_ns >= t_ns; espera_portao_ns >= 0;
     t_fim_ns − t_ns − espera_portao_ns >= 0; os inteiros cabem em int64.

TESTES EXISTENTES: nenhum pode ser editado. Ficam verdes SEM edição, em especial:
  MisturaSemeadaTest (W6/W7/W8 por reflexão de 10 parâmetros), MisturaSemeadaFonteTest (W9 no corpo
  do primeiro rodarCliente), TokenAntigoFonteTest (U9, :48-49, :92-101), GoldenS1S2Test,
  SequenciaMisturaV2Test, SequenciaMisturaV3Test, S3SimulacaoTest, AplicacaoS3Test (construtor de 3
  parâmetros), AdminELimiteS3Test, UsuarioVirtualTest, MundoS3 (construtor de 4 parâmetros de
  ContextoS3), ArquiteturaControleTest, NomesDeCacheSoNoResolvedorTest,
  PerfilFixoForaDoResolvedorTest, ParametrosControladorTest.
  `git diff --stat c055399 HEAD -- workload/src/test` só com arquivos A. Se QUALQUER teste existente
  exigir edição, PARE antes do commit e reporte arquivo:linha, asserção e motivo: cada edição exige
  autorização do Gestor, caso a caso.

MUTANTES (cópia git archive da ponta, um por vez, Surefire completo; todos precisam morrer, ou o
relatório explica a equivalência):
 N1 sair() não devolve a permissão                         → K1a, K1d
 N2 signal() no lugar de signalAll() na troca de perfil    → K1c
 N3 limite CONSERVADOR com floor (nominal 1 ⇒ 0)            → K1b
 N4 sair() fora do finally na sobrecarga                    → K1d
 N5 pausa K2 aplicada também em NORMAL                      → K2a, P2
 N6 aplicarPensar multiplica em NORMAL (×1,0 com arredondamento) → K2b, P1
 N7 K3 descarta a operação em vez de adiar                  → K3c, P3
 N8 K3 ignora k3AdiaMaxNs                                   → K3b
 N9 K3 adia também Validate                                 → K3e
 N10 encerrar() não libera os adiados                       → K3f
 N11 K4 muda o número de retentativas em CONSERVADOR         → K4a, P3
 N12 jitter em [0, 2) no lugar de [1−j, 1+j)                → K4b
 N13 jitter por new Random() (não determinístico)           → K4c
 N14 tFimNs = tNs                                           → F1, Py
 N15 esperaPortaoNs sem a espera de K3                      → F1
 N16 a de 10 parâmetros passa um portão NORMAL novo em vez de livre() → teste novo que o Worker
     escreve (por exemplo, livre() é o singleton e a de 10 não aloca portão), se nenhum acima matar

SMOKE LOCAL (Podman local, NÃO a VM; depois do commit 5):
 - Peça a vez no Podman ao coordenador (B-26.2 e B-37 também usam a máquina). NÃO pare nem reinicie
   a máquina Podman.
 - podman-compose -p b27, contêineres b27-isn1..3, portas 12271..12273; ps/network antes e depois
   iguais. Jar da árvore limpa do commit 5 (SHA-256 em runs/b27-smoke/jars.json).
 - Q1: S3 sync, 90 s, --warmup-min-sec 10, --reset-state on, --rep 1, semente 27 100;
   Q2: S1 sync v3 --threads 4, 90 s, mesma configuração, semente 27 200.
   (O portão real não é ligável pela CLI neste bloco: o smoke prova que o caminho desligado não
   mudou e que o CSV S3 novo está bem formado.)
 - Critérios: exit 0; s3-acoes-rep-001.csv com o HEADER novo, t_fim_ns >= t_ns em 100 %,
   espera_portao_ns = 0 em 100 % (portão livre); Q2 com as checagens de sanidade de sempre
   (VerificadorSanidade) e I1 = I4 = 0; nenhum ERROR nos logs dos servidores.
 - Brutos com MANIFEST.sha256. "Smoke local: latências não são evidência."

RELATÓRIO docs/relatorio-b27-<data>.md: Surefire e Python do passo 0 e por commit (vermelhos do
commit 2 com mensagem), test-compile por commit, N1..N16, smoke, `git diff --name-status c055399
HEAD`, `git diff --stat c055399 HEAD -- workload/src/test` (só A), desvios e pontos para o Gestor.
Liste SHAs, worktree e `git status`, e pare. Não integre nem faça push.
````

## 4. Critérios de aceite

1. Surefire = 761 + novos, 0 falhas, 0 erros, 1 ignorado; Python da base + novos, OK; nenhum teste
   existente editado.
2. Todo commit compila; vermelhos do commit `test:` registrados.
3. N1..N15 mortos (N16 morto ou justificado).
4. `--controller` ainda não existe: `executarRepeticao` e `executarS3` sem mudança
   (`git diff c055399 HEAD -- WorkloadMain.java` só na região de `rodarCliente`).
5. NORMAL ≡ desligado (P1, P2) e restrição §4 (P3, K3c, K4a).
6. CSV S3 com 10 colunas, as 8 primeiras inalteradas, contrato documentado e testado.

## 5. O que fica para o B-28.2 (registrar no plano)

- Criar `PerfilAtual` e `PortaoDeEmissao` só em `--controller active` (em `shadow` o perfil fica
  NORMAL e o portão pode ser `livre()`; em `off`, nenhum objeto novo); `concorrenciaNominal` =
  `cli.threads` em S1/S2 e o número de atores em S3; `sementeJitter` derivada de `--seed` com sal
  próprio (registrar como DT, no padrão da DT-012).
- Trocar a chamada de `executarRepeticao` para a sobrecarga de 11 parâmetros. O trecho exigido por
  `TokenAntigoFonteTest:100-101` continua presente se o argumento novo vier **depois** de
  `misturaV3`.
- `portao.encerrar()` no fim da carga, antes da convergência; JSONL de `EventoK3`
  (`{"tipo":"k3",...}`, plano 3.6); combinação de `ParametrosControlador` e `ParametrosKnobs` na CLI.
