# Prompt de implementação do B-39: referência de p99, *proxy* de energia e análise pareada (2026-10-27)

> Senior, 2026-10-27. Bloco do plano `docs/plano-implementacao-controlador-2026-10-22.md`, seções 3.2
> (referência), 5.2 (energia), 8.3/8.4 (desfechos e estatística) e 6 (linha B-39), revisto em
> `c055399`. Três sub-blocos numa branch: B-39.1 `referencia_p99.py`, B-39.2 `energia_proxy.py`, B-39.3
> `comparar_bracos.py`. Só Python em `analysis/`. Roda em paralelo com B-26.2, B-27 e B-37. Os
> contratos de entrada (CSV de recursos do B-37 e CSV S3 v2 do B-27) estão copiados na seção 3, com o
> mesmo texto dos prompts deles. Sem VM, sem push.

## 1. O que mudou desde o plano de 22/10 e afeta este bloco

1. **População da referência = a do agregador ao vivo.** No B-26.1, `AgregadorDeJanela.java:126-129`
   conta como elegível **todo** evento cuja `operation` está na referência, qualquer que seja o
   `return_code`, e como excedência `latência > p99_ref` (estrito). Para que a taxa de excedência sem
   falha seja de fato ≤ 1 %, a referência tem de ser calculada sobre a **mesma** população: todos os
   eventos de cada `operation` (sem separar isolado/tríade), da janela de medição do CSV. Isso **difere** do p99 do
   *rebaseline* (DT-009, PR §7: só desfechos válidos, `analysis/rebaseline.py:60-61`), que mede
   outra coisa (desempenho por desfecho). As duas definições convivem, cada uma no seu uso. É a
   decisão D2 pendente do Gestor; a recomendação do Senior é esta.
2. **Percentil por posto mais próximo, não `lower`.** O plano (8.4, passo 1) dizia "método `lower`,
   igual ao `bootstrap_ic.py`". Depois disso, a DT-009 fixou o posto mais próximo
   (`rebaseline.percentil`, `ordenado[ceil(p·n) − 1]`, aritmética exata). Para não haver dois p99 no
   mesmo trabalho, o B-39 usa o posto mais próximo, com `n ≥ 1 000`. Os módulos novos **não importam**
   `rebaseline.py`. Ele é do *bundle* congelado e `python -I` não põe o diretório do *script* no
   `sys.path`. A definição é copiada, e um teste de paridade compara as duas implementações.
3. **CSV de contrato 2** (B-43.4, `docs/contrato-csv-v2.md`): `return_code` fiel. CSV sem
   `meta-csv-rep-NNN.json` com `contrato_csv = 2` ao lado é recusado (CSVs de junho e de antes do
   B-43.4 medem outra coisa).
4. **IC com n = 8 blocos.** O plano exigia que o IC por *bootstrap* percentil sobre blocos cobrisse o
   efeito em ≥ 90 % de 200 simulações. Uma simulação do Senior (2 000 réplicas, `d_b` normal, 4 000
   reamostragens) deu cobertura de **0,88** para o percentil com n = 8 (0,86 com n = 6) e **0,95**
   para o intervalo t. O percentil por *bootstrap* é estreito demais com amostra pequena, e o
   critério de 90 % reprovaria uma implementação correta. Pelo critério de aceite revisto (seção 3,
   E1), o teste mede e reporta as coberturas dos três intervalos e exige 0,93 ou mais do intervalo t.
   Qual intervalo é o primário fica para o pré-registro B-40 (decisão D3 pendente; o Senior recomenda
   o intervalo t sobre `d_b` como primário e o *bootstrap* de blocos e o hierárquico como
   sensibilidade). O teste de permutação exato não muda.
5. **Desfecho primário S3 com duas leituras.** O B-27 grava `t_fim_ns` e `espera_portao_ns`. O
   extrator do B-39.3 calcula as duas latências percebidas (com e sem espera no portão). A escolha
   é do B-40.
6. **Exclusão por `steal`** (plano 5.3, item 5): `steal > 5 %` do tempo da execução, medido pela
   coluna `steal_usec_acum` da fonte `vm` do contrato `recursos/1`.

## 2. Escopo

**Entra:** três módulos com CLI e funções puras testáveis; um módulo comum de fases
(`analysis/fases.py`, porte de `Marcadores.faseDe`); testes `unittest`; fixtures sintéticas.
**Não entra:** leitura da referência em Java (B-28.2, que segue o esquema daqui); `replay` (B-38);
qualquer mudança em `rebaseline.py`, `bootstrap_ic.py`, `percentis.py`, `compare_scenarios.py`;
escolha das execuções-fonte da referência e do intervalo primário (B-40).

## 3. Prompt para o Worker

````text
Você é o WORKER. Uma entrega, B-39 (B-39.1, B-39.2, B-39.3). Commits só locais. NÃO faça push. NÃO
use a VM. NÃO use tc/netem.

BASE: c055399. Branch feature/b39-analise-pareada, worktree ../wt-b39
(git worktree add ../wt-b39 -b feature/b39-analise-pareada c055399).

PASSO 0:
 a) `python --version` (esperado 3.12.x), `python -c "import numpy, scipy; print(numpy.__version__,
    scipy.__version__)"`. Anote.
 b) `python -m unittest discover -s analysis -p "test_*.py"` e `python -I scripts/test_rebaseline_matriz.py`
    na base: anote "Ran N tests" e OK.
 c) `mvn -q -o -f workload/pom.xml clean test`: esperado 106 arquivos, 761/0/0/1 (o bloco não toca
    Java; repita no fim).
 d) Confira: analysis/rebaseline.py:60-61 (VALIDOS), :75-83 (percentil), :86-88 (p99 com n >= 1000);
    AgregadorDeJanela.java:126-129 (elegível = operation na referência; excedência estrita);
    Marcadores.java faseDe (:137-157) e o formato das linhas de marcador; MarcadoresTest (casos);
    ConvergenciaLoop.escreverResumo (t_convergencia_abs_ns, divergencia_persistente);
    docs/contrato-csv-v2.md; MetaCsv (nome e campo contrato_csv do meta-csv-rep-NNN.json).
    Divergência: PARE e reporte.

PAUSA OBRIGATÓRIA (regra 5.1 de docs/decisoes-senior-2026-10-16.md): quando o coordenador avisar uma
janela de injeção na VM ou uma noite de campanha, interrompa as suítes (as simulações de cobertura
pesam na CPU) e só retome com liberação expressa (inclui 5 min antes e depois).

ARQUIVOS PERMITIDOS (todos NOVOS): analysis/fases.py, analysis/referencia_p99.py,
  analysis/energia_proxy.py, analysis/comparar_bracos.py, analysis/test_fases.py,
  analysis/test_referencia_p99.py, analysis/test_energia_proxy.py, analysis/test_comparar_bracos.py,
  analysis/fixtures/b39/** (sintéticas, geradas por código versionado no próprio teste ou num
  gerar_fixtures_b39.py novo), docs/relatorio-b39-<data>.md.
ARQUIVOS PROIBIDOS: todo arquivo existente, em especial analysis/rebaseline.py,
  analysis/test_rebaseline.py, analysis/bootstrap_ic.py, analysis/percentis.py,
  analysis/compare_scenarios.py, analysis/README.md (a entrada no README é feita na integração),
  analysis/requirements.txt (se precisar de dependência nova, PARE: numpy e scipy já existem),
  scripts/*, workload/*, runs/rebaseline/*, .gitattributes.
Só biblioteca padrão + numpy + scipy. Nenhum módulo novo importa rebaseline.py (só os testes, para a
paridade). Todos rodam com `python -I analysis/<modulo>.py ...`.

CONTRATOS DE ENTRADA (texto igual ao dos prompts do B-27 e do B-37):
 (i) rep-NNN.csv, contrato 2: colunas op_id,operation,start_ns,end_ns,replica,return_code,key
     [,phase]; latência = end_ns − start_ns; meta-csv-rep-NNN.json ao lado com contrato_csv = 2.
 (ii) s3-acoes-rep-NNN.csv, contrato S3 v2 (B-27), cabeçalho exato
     t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns
     (epoch ns no relógio dos end_ns; latência percebida com espera = t_fim_ns − t_ns; sem espera =
     t_fim_ns − t_ns − espera_portao_ns). CSV S3 com 8 colunas (antes do B-27) é recusado no
     desfecho S3, com exit 2.
 (iii) recursos-rep-NNN.csv, contrato recursos/1 (B-37), cabeçalho exato
     t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado
     fonte ∈ {cg:<conteiner>, pid:<rotulo>, vm, coletor}; contadores acumulados em µs; steal só em vm;
     estado "ok" ou "erro:<motivo>" com numéricos vazios; recursos-meta-rep-NNN.json com
     contrato "recursos/1", clk_tck, nproc, intervalo_ms.
 (iv) marcadores: linhas "fault=<F> node=<no> event=start|split|end t_epoch_ns=<n>" (# e vazias
     ignoradas), como Marcadores.ler.

B-39.1 analysis/referencia_p99.py
 - gerar_referencia(csvs, cenario, perfil, n_min=1000) -> dict; CLI:
   python -I analysis/referencia_p99.py --cenario S1|S2|S3 --perfil sync|async --saida ref.json
     [--n-min 1000] CSV [CSV ...]
 - População: TODOS os eventos de cada valor de `operation` (qualquer return_code), de todos os CSVs
   juntos. p99 por posto mais próximo (cópia exata de rebaseline.percentil). Operação com n < n_min:
   fora de p99_ns, listada em "omitidas" com o n.
 - Guardas (exit 2, sem escrever saída): CSV sem meta de contrato 2; CSV com coluna phase contendo
   algo diferente de "antes" (execução com falha não serve de referência); cenário ou perfil
   inválidos; nenhuma operação com n >= n_min; saída já existente (nunca sobrescreve).
 - Saída (JSON, chaves ordenadas, inteiros como inteiros):
   {"esquema":"referencia-p99/1","cenario","perfil","metodo":"posto-mais-proximo",
    "populacao":"todos-os-eventos-por-operation","n_min","p99_ns":{op:int},"n":{op:int},
    "omitidas":{op:int},"origem":[{"arquivo":<caminho relativo>,"sha256","linhas"}],
    "gerado_por":{"script":"analysis/referencia_p99.py","sha256":<do próprio script>}}
   Esse é o esquema que o loader Java do B-28.2 vai ler (p99_ns e n).
 - Função auxiliar excedencia(csv, ref) -> {op: (elegiveis, excedencias)} com a MESMA regra do
   agregador (latência > p99_ref; operações fora da referência não contam), para o teste de
   calibração abaixo e para o B-38.

B-39.2 analysis/energia_proxy.py (+ analysis/fases.py)
 - fases.py: fase_de(t_ns, inicio, split, fim, w_recovery_ns, convergencia_abs_ns,
   divergencia_persistente) com os MESMOS rótulos e fronteiras de Marcadores.faseDe ("antes",
   "durante", "durante-pre-split", "durante-pos-split", "recuperacao", "pos-convergencia"; durante
   fechado nas duas pontas; recuperação até max(fim + W, convergência), ou até o fim se divergência
   persistente); ler_marcadores(caminho); intervalos_de_fase(...) -> lista ordenada de
   (rotulo, t_ini, t_fim) cobrindo [t0, t1).
 - energia_proxy.py, por execução:
   cpu_por_fase(recursos_csv, intervalos) -> {fonte: {fase: cpu_s}} com interpolação LINEAR do
   contador acumulado nas fronteiras (fronteira fora das amostras: fase sem valor, nunca
   extrapolada); amostra com estado erro é ignorada na interpolação daquela fonte, e a fase que
   depender de um trecho sem amostra válida por mais de 2 × intervalo_ms fica sem valor e é marcada
   "lacuna".
   utilizacao = cpu_s / (duração da fase × nproc), para fonte vm e para a soma dos cg.
   steal_frac(vm) = Δsteal / (Δt × nproc) na execução inteira; excluida = steal_frac > 0,05
   (estritamente maior).
   operacoes_uteis: S1/S2 = eventos com return_code em VALIDOS (cópia de rebaseline.VALIDOS);
   S3 = ações com falha_percebida == "false". CPU-s por 1000 úteis, por fase e total, para a soma dos
   cg, para pid:workload e para vm.
   retrabalho (S3): soma de (tentativas − 1) e erros por ação útil.
   energia estimada, modelo declarado P(u) = P_ocioso + (P_max − P_ocioso)·u, com --p-max-w e
   --razoes-ocioso 0.3,0.5,0.7. Numa fase de duração Δt, com u = cpu_s/(Δt·nproc):
   E_total = P_ocioso·Δt + (P_max − P_ocioso)·cpu_s/nproc e E_dinamica = (P_max − P_ocioso)·cpu_s/nproc,
   com P_ocioso = razão·P_max; ambas por 1000 úteis, para cada razão. Os parâmetros entram no JSON de saída; sem --p-max-w a energia não é
   calculada (só CPU), e o JSON diz isso.
 - CLI: python -I analysis/energia_proxy.py --execucao DIR --rep N --cenario S1|S2|S3
   [--marcadores ARQ --w-recovery-s 60 [--convergencia ARQ.json]] [--p-max-w W
   --razoes-ocioso 0.3,0.5,0.7] --saida energia-rep-NNN.json. Sem marcadores: fase única "antes".
   Saída nunca sobrescreve (exit 2).

B-39.3 analysis/comparar_bracos.py
 - Entrada: manifesto TSV com cabeçalho exato `bloco	celula	braco	execucao` (braco ∈ {A, B}; A =
   shadow, B = active), um par A/B por (celula, bloco); bloco incompleto ⇒ exit 2.
 - Desfechos (por execução e fase, conjunto de fases dado por --fases, padrão
   durante,recuperacao): acao_s3_p99_com_espera, acao_s3_p99_sem_espera (s3-acoes, TODAS as ações,
   inclusive as com falha percebida, como no plano 8.3; registre o n), evento_p99:<operation>
   (todos os eventos da operation, como na referência), taxa_erro, falhas_percebidas_por_1000,
   vazao_util (ações S3 sem falha percebida por segundo de fase), cpu_por_1000_uteis (do
   energia-rep-NNN.json, se existir). p99 por posto mais próximo; n < 1000 ⇒ desfecho ausente
   naquela execução, e o bloco sai da análise daquele desfecho (registrado).
 - Por célula e desfecho: d_b = ln(y_B / y_A); estimativa = exp(média d_b);
   IC t: média ± t_{0,975; n−1}·s/√n, em escala de razão;
   IC bootstrap percentil sobre blocos (10 000 reamostragens, numpy default_rng com
   SeedSequence([semente, indice_do_desfecho]), semente da CLI --semente-boot, obrigatória);
   IC hierárquico (reamostra blocos e, dentro de cada execução, os eventos do desfecho, recalculando
   o p99; só para desfechos de percentil; 2 000 reamostragens por padrão, --n-hier);
   teste de permutação exato por troca de sinal sobre d_b: p = #{arranjos com |média| >= |média
   observada| − 1e−12} / 2^n, bilateral, com a identidade incluída (p mínimo = 2/2^n com n = 8: 2/256);
   n > 16 ⇒ exit 2 (enumeração exata limitada; não use Monte Carlo sem decisão do Senior).
   Holm (step-down) sobre os secundários de cada célula, com o primário (--primario) fora da
   correção.
   Não-inferioridade de vazao_util: limite inferior do IC t da razão B/A >= 1 − δ (--delta, padrão
   0,10), resultado "nao-inferior" / "inconclusivo".
   Violações (contagens I1, I2_REAL, I4 por braço, lidas de violacoes-rep-NNN.csv com a mesma regra
   da DT-009: I2 só com rótulo "I2" e classificacao REAL; *_concorrentes e I2_JANELA_CASCATA não
   contam, DT-001/DT-002): com zero num braço, limite superior unilateral de 95 % = 1 − 0,05^(1/N)
   sobre N ações (e o rótulo "≈ 3/N"); com contagens nos dois braços, razão de taxas com IC por
   bootstrap de blocos.
   Exclusão: execução com energia excluida (steal > 5 %) sai SÓ dos desfechos de CPU/energia, com o
   bloco inteiro, e o relatório diz quantos.
 - Saída JSON por célula e desfecho (estimativa, três IC, p, p_holm, n_blocos, blocos_excluidos e
   motivo) + uma tabela Markdown de leitura humana. Nunca sobrescreve (exit 2).

ORDEM DOS COMMITS (sem --amend; histórico linear; todo commit importável: `python -I -c "import ast,
sys; [ast.parse(open(f, encoding='utf-8').read()) for f in sys.argv[1:]]" analysis/*.py` com código
0, e a suíte roda sem erro de importação):
 1. refactor: os quatro módulos novos com as funções e as CLIs declaradas; corpos que lançam
    NotImplementedError (CLI: exit 3 "nao implementado").
 2. test: test_fases.py e test_referencia_p99.py (vermelhos registrados).
 3. feat: fases.py e referencia_p99.py.
 4. test: test_energia_proxy.py (vermelhos registrados).
 5. feat: energia_proxy.py.
 6. test: test_comparar_bracos.py (vermelhos registrados).
 7. feat: comparar_bracos.py.
 8. docs: relatório.
 Em cada commit test:, os testes dos módulos ainda não implementados falham por NotImplementedError
 ou asserção, nunca por ImportError/SyntaxError.

TESTES (unittest; seeds fixas; a suíte nova inteira em menos de 120 s nesta máquina; a parte de
cobertura pode ser marcada para rodar só com B39_LONGO=1, desde que uma versão curta rode sempre):
 Fases
  F1 paridade com Marcadores.faseDe: tabela de casos copiada de MarcadoresTest (sem start; só start;
     start/end; com split; t == start; t == end; t == fim + W; convergência depois de fim + W;
     divergência persistente), cada um com o rótulo esperado do teste Java (cite arquivo:linha).
  F2 intervalos_de_fase cobre [t0, t1) sem lacuna nem sobreposição.
 Referência
  R1 paridade: percentil e VALIDOS copiados == rebaseline.percentil e rebaseline.VALIDOS em 10 000
     amostras aleatórias de tamanhos 1..5 000 e p ∈ {0,5; 0,95; 0,99; 0,999} (import de rebaseline
     só no teste).
  R2 população: CSV sintético em que os erros são lentos ⇒ p99 com todos os eventos > p99 só dos
     válidos; o gerado é o de todos (mata a população de DT-009).
  R3 posto mais próximo vs lower: amostra de n = 1 001 onde os dois diferem ⇒ o gerado é o do posto.
  R4 n_min: operação com 999 eventos vai para omitidas; com 1 000 entra.
  R5 guardas: sem meta, meta com contrato 1, phase "durante", saída existente, cenário inválido ⇒
     exit 2 e nenhum arquivo escrito.
  R6 origem: sha256 e linhas de cada CSV conferidos; o JSON é determinístico (duas execuções dão os
     mesmos bytes).
  R7 calibração sob H0: gere 20 CSVs sintéticos i.i.d. (log-normal, 5 operações); referência com 10;
     excedencia nos outros 10 ⇒ fração de excedências por operação ≤ 1 % + 3,29·√(0,01·0,99/n), e
     nas 10 de origem ≤ 1 % (por construção do posto mais próximo, estritamente maior).
 Energia
  E-a interpolação: contador linear sintético ⇒ cpu_s por fase exato até 1 µs; fronteira entre duas
     amostras ⇒ interpolação; fronteira antes da 1ª amostra ⇒ sem valor.
  E-b lacuna: 3 amostras com erro seguidas em intervalo 1 s ⇒ fase "lacuna".
  E-c steal: 4 % ⇒ não excluída; 5 % exato ⇒ não excluída; 5,01 % ⇒ excluída.
  E-d úteis: S1 com ERROR_TRANSPORT e BLOCKED ⇒ só VALIDOS contam (BLOCKED de Login não é útil, como
     na DT-009); S3 ⇒ só falha_percebida false.
  E-e modelo: P_max 100 W, nproc 4, u conhecido ⇒ energia dinâmica e total iguais ao cálculo à mão
     para as três razões; sem --p-max-w ⇒ sem energia e JSON explícito.
  E-f contrato: CSV de recursos com cabeçalho diferente ou meta com outro contrato ⇒ exit 2.
 Comparação
  C1 permutação exata, n = 4: d = (0,1; 0,2; −0,05; 0,3) ⇒ p = 4/16 = 0,25 (enumeração manual:
     |soma| >= 0,55 só na identidade, no oposto e nos dois arranjos que trocam só o sinal de 0,05); n = 8 com todos positivos e iguais ⇒ p = 2/256.
  C2 Holm: p = (0,01; 0,02; 0,03; 0,5) com m = 4 ⇒ ajustados (0,04; 0,06; 0,06; 0,5) (monotonia
     step-down); compare com Bonferroni e Hochberg em um caso em que diferem.
  C3 regra de três: N = 1 000, zero eventos ⇒ 1 − 0,05^(1/1000) = 0,00299125 (literal, 8 casas decimais) e rótulo
     ≈ 3/N; N = 30 ⇒ 0,0950339
  C4 não-inferioridade: razão verdadeira 0,95 com variabilidade pequena ⇒ "nao-inferior" com δ =
     0,10; razão 0,85 ⇒ "inconclusivo"; o critério usa o limite inferior do IC, não a média.
  C5 pareamento: manifesto com bloco sem B ⇒ exit 2; blocos trocados de célula ⇒ exit 2.
  C6 determinismo: mesma semente ⇒ mesmo JSON (bytes); outra semente ⇒ IC bootstrap diferente e IC
     t igual.
  E1 cobertura (revisto, ver seção 1, item 4): 1 000 simulações (versão curta: 200), n = 8 blocos,
     d_b ~ Normal(ln 0,9; 0,15): cobertura do IC t >= 0,93; do bootstrap percentil e do hierárquico,
     medidas e REPORTADAS (sem limiar; espera-se cerca de 0,88 no percentil); versão de eventos (uma
     reamostragem de eventos de todas as execuções juntas, como bootstrap_ic.py) com efeito entre
     execuções forte ⇒ cobertura visivelmente menor que a do t (documenta a pseudorreplicação, A8).
  E2 efeito conhecido: dados sintéticos com efeito multiplicativo 0,8 no p99 de B ⇒ estimativa em
     [0,75; 0,85] e p < 0,05 com 8 blocos; sem efeito ⇒ p > 0,05 em pelo menos 90 % de 200 conjuntos
     (o teste exato tem nível ≤ α por construção).
  E3 hierárquico: com variabilidade só dentro da execução, o IC hierárquico é mais largo que o de
     blocos; com variabilidade só entre execuções, os dois ficam próximos (razão das larguras
     reportada).

MUTANTES (cópia no scratchpad, um por vez, suíte completa; todos precisam morrer):
 Y1 percentil com method "lower"                         → R3
 Y2 referência só com desfechos válidos                  → R2
 Y3 n_min com >                                          → R4
 Y4 aceita phase "durante"                               → R5
 Y5 excedência com >=                                    → R7
 Y6 interpolação por amostra mais próxima                → E-a
 Y7 steal >= 0,05                                        → E-c
 Y8 úteis incluem BLOCKED                                → E-d
 Y9 permutação sem a identidade (p mínimo 1/256)         → C1
 Y10 permutação unilateral                               → C1
 Y11 Bonferroni no lugar de Holm                         → C2
 Y12 regra de três como 3/N exato                        → C3
 Y13 não-inferioridade pela média                        → C4
 Y14 bootstrap reamostra eventos em vez de blocos         → E1 (versão curta) ou C6
 Y15 d_b = y_B − y_A (sem log)                           → E2
 Y16 *_concorrentes contados como violação               → teste novo que o Worker escreve com uma
     fixture de violacoes-rep-NNN.csv (formato do auditor atual; cite a fonte do formato)

RELATÓRIO docs/relatorio-b39-<data>.md: versões, suítes por commit (vermelhos dos commits test:),
Y1..Y16, coberturas medidas em E1 (tabela: t, percentil, hierárquico, eventos), resultado de R7, os
esquemas de saída com um exemplo de cada, `git diff --name-status c055399 HEAD` (só A), Surefire
final 761/0/0/1, Python final (base + novos), desvios e pontos para o Gestor. Liste SHAs, worktree e
`git status`, e pare. Não integre nem faça push.
````

## 4. Critérios de aceite

1. Python da base + novos, OK; nenhum arquivo existente alterado; Surefire inalterado.
2. Commits na ordem `refactor → test → feat` por sub-bloco, sem erro de importação nos `test:`.
3. Y1..Y15 mortos (Y16 morto com teste novo).
4. Referência na população do agregador (R2, R7) e percentil do *rebaseline* (R1, R3), com esquema
   `referencia-p99/1` estável.
5. Cobertura do IC t ≥ 0,93 com n = 8; as dos outros intervalos medidas e reportadas.
6. Contratos `recursos/1` e S3 v2 consumidos exatamente como os prompts do B-37 e do B-27 os definem.

## 5. O que fica para depois (registrar no plano)

- B-28.2: o *loader* Java da referência lê `p99_ns` e `n` do esquema `referencia-p99/1` e recusa
  operação com `n < n_min` e esquema diferente.
- B-38: `excedencia()` serve de oráculo para o *replay*; a referência *leave-one-out* sai de
  `gerar_referencia` com a lista de CSVs sem o da execução.
- B-40: escolher as execuções-fonte das referências (para S1/S2, candidatas naturais são as células
  `none` do *rebaseline*, uma referência por cenário e perfil; para S3, as células R0 da T-F), o
  intervalo primário (D3), a leitura do desfecho S3 (com ou sem espera) e `--semente-boot`.
