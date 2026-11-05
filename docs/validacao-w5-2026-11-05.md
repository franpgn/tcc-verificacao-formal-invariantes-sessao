# Validação do W-5 (B-39.4): `comparar-bracos/2`, guarda G4 e poder (2026-11-05)

Senior, validação independente **só por leitura**. A VM mede a noite 1 do *rebaseline* e o *host* está em H0
estrito: não rodei Python, Maven nem testes. Usei `git log`, `git diff`, leitura dos arquivos e
`git apply --check` do patch pendente (aplica limpo; nada foi alterado na árvore).

- Branch `feature/w5-desfechos-coprincipais`, worktree `wt-w5`, ponta `7182a92` sobre `e869a31`.
- Commits: `0ffb7a4` (test G4), `87ff066` (fix G4), `34f119a` (test W-5), `347670b` (feat: `comparar-bracos/2`,
  `poder.py`, `referencia_erro.py`, energia 0,3/0,5) e `7182a92` (docs).
- Especificação: prompt W-5 de `docs/plano-execucao-2026-11-04.md` §9; Prompt 2 e §3.1-3.5 de
  `docs/plano-alinhamento-objetivos-2026-11-04.md` (lidos em `wt-plano-ctrl`, ponta `104cab6`). Decisões do
  Gestor: desfechos co-principais (G-1), interseção-união (D-1), calibração derivada (D-12), energia com razão
  ociosa central 0,3 (Barroso e Hölzle, p. 36) e sensibilidade 0,5 (p. 34), G3 e G4 de 28/10.
- Patch pendente: `autorizacao-test_comparar_bracos.patch` (no *scratchpad* do coordenador).

**Parecer: APROVADO COM RESSALVAS, sem liberação para integração.** O código segue a especificação na estrutura
(famílias, regra IU, Holm entre células, Δ_rec, Δ_conv, tipos de célula, poder, guarda G4) e não traz limiar novo
sem origem, com uma exceção herdada (`--w-recovery-s 60`, ponto 12). Antes de integrar faltam três coisas:
(1) as decisões dos pontos 4, 11 e 12 e do achado A-1, que mudam código ou protocolo; (2) rodar
`python -E -P -m unittest discover -s analysis` numa janela H2 e registrar a contagem (hoje 347 é só a esperada);
(3) o relatório do Worker, que o aceite do Prompt 2 pede e não existe.

## 1. O que conferi

| # | Verificação | Resultado |
|---|---|---|
| 1 | Diff `e869a31..7182a92` | 10 arquivos, +2933/−94. Li inteiros `comparar_bracos.py`, `poder.py`, `referencia_erro.py`, o diff de `energia_proxy.py` e as quatro suítes novas |
| 2 | `--delta-latencia` e `--w-analise-s` obrigatórios e sem padrão | sim. `required=True` (`comparar_bracos.py:1478-1479`), parâmetros só por palavra-chave e sem padrão em `comparar()` (`:1193`), recusa de valor ≤ 0, NaN ou booleano (`:1208-1210`, `:1506-1509`). Testes `test_parametros_obrigatorios_sem_padrao` e `test_sem_delta_latencia_ou_w_analise` |
| 3 | Guarda de braços trocados | existe: modo da linha `meta` da trilha tem de ser `shadow` em A e `active` em B, senão exit 2 (`:1247-1250`; teste `test_bracos_trocados_pela_trilha`). Só age quando a trilha existe (achado A-3) |
| 4 | Limiares novos | nenhum sem origem. α = 0,05 e IC 95 % (`Metodologia.tex:229, :326`), quantil 95 % da linha de base e confiança 95 % de p_ref⁺ (plano 3.4), blocos 8/10/12 e reduções 0..1 em passos de 0,1 (plano 3.3), δ da vazão 10 % (Gestor, 22/10), razões 0,3/0,5 (Gestor, 05/11). `LOTE = 512` em `poder.py` é tamanho de lote, não limiar. A exceção é `--w-recovery-s 60` (`:1488`, `:1194`), herdada do B-39 |
| 5 | `--regra` e `--primario` | saíram; teste `test_opcoes_retiradas` |
| 6 | Escala por desfecho | diferença para taxa de violações, Δ_rec, Δ_rec da Metodologia, Δ_conv e divergência (`:117`); log da razão para os demais. Conflito com a G3 nos pontos de erro (ponto 11) |
| 7 | Guarda G4 (`87ff066`) | B39-R1 confere W, presença de marcadores e as fases recalculadas sobre a janela do próprio JSON (`:790-819`); `energia_proxy` grava `janela` e `parametros` (`energia_proxy.py:424, :430`). B39-R2: `sem-amostra-valida`. Correto por leitura |
| 8 | Patch pendente | `git apply --check`: aplica limpo. Análise na seção 2 |
| 9 | Aceite do Prompt 2 | **em aberto**: suíte não rodada (o Worker declara só `py_compile`), contagem não medida, sem `docs/relatorio-b39-4-<data>.md` |

## 2. Ponto 1: o patch de autorização

**O patch não retira nenhuma linha de asserção.** Ele troca a chamada do `/1` pela do `/2` (`ARGS_V2` no lugar de
`--primario`), completa o *fixture* `energia_min` com `janela` e `parametros` coerentes com a guarda B39-R1, e
atualiza os valores esperados que o `/2` muda por desenho: nome do esquema, família `primario` →
`co-principal-L` e o conjunto de desfechos, que continua conferido por igualdade estrita (agora com os quatro
novos). Em `C5Pareamento` e `Guardas` o patch até fortalece: sem ele, esses testes passam pelo motivo errado (o
`argparse` recusa `--primario`); com ele, voltam a exercitar a guarda que nomeiam. `ExclusoesERegistro` muda só a
assinatura de `comparar`.

Duas edições, porém, afrouxam o que é conferido:

1. `test_boot_de_blocos_so_depende_de_d_b` escolhe a escala pelo campo `escala` da própria saída
   (`exp = e["escala"] == "log-razao"`). Se um desfecho sair com a escala errada, o teste passa. Correção: lista
   explícita no teste dos desfechos em diferença (sem importar `ESCALA_DIFERENCA` do módulo testado) e
   `assertEqual` da escala esperada por nome antes de conferir o IC.
2. `test_esquema_e_familias` filtra os secundários com `p_permutacao is None` antes de conferir o Holm. Se um
   secundário antigo (por exemplo `taxa_erro`) passar a sair sem p, o teste não vê. Correção: afirmar que o
   conjunto dos secundários sem p é exatamente `{delta_rec_s, delta_conv_s, divergencia_persistente}` (os que o
   *fixture* não tem como calcular: marcador `end` além da medição e sem resumo de convergência).

**Recomendação: autorizar com essas duas correções.** Se os pontos 11 e 12 forem aprovados, o patch também
precisa de `--w-recovery-s 60` em `ARGS_V2` e `w_recovery_s=60` na chamada de `ExclusoesERegistro` (o 60 do
*fixture* vira valor de teste, não padrão), e a lista explícita de escala e o conjunto de desfechos passam a
incluir `taxa_erro`, `falhas_percebidas_por_1000` e as duas sensibilidades novas.

## 3. Pontos 2 a 12

### Ponto 2: p_L = min(1, 2·F_t) e p_V = 1 com média de d_b ≥ 0

**Manter os dois.** Fonte: derivação, com a regra do plano 3.2 e o IC 95 % da `Metodologia.tex:229`.

A distribuição da troca de sinal é simétrica (cada arranjo tem o oposto). Com média observada m < 0, os arranjos
com |média| ≥ |m| são os de média ≤ m mais os de média ≥ −m, em igual número, e o p bilateral é exatamente o dobro
do unilateral inferior. Com m ≥ 0, o unilateral inferior é pelo menos 1/2, e o dobro passa de 1. Logo o p_V
implementado (`comparar_bracos.py:436`) é min(1, 2·p unilateral), a mesma convenção do p_L (`:291-306`). Com
isso, p_IU = max(p_V, p_L) ≤ 0,05 exatamente quando H_V e H_L são rejeitadas pela regra do plano, e o Holm entre
células trabalha numa escala só. Consequência a declarar no B-40: cada componente é um teste unilateral a 0,025,
e a IU fica mais conservadora que o mínimo de Berger (que admitiria 0,05 em cada um). Foi o que o plano e a
Metodologia fixaram; não mudar sem decisão. Para o Escritor (FONTES B-7, MET-72): o p da condição do p99 é o
valor-p dual do IC t 95 %.

### Ponto 3: célula não avaliável no Holm com p = 1

**Manter.** Fonte: plano de execução §5.2, que calcula os limiares do Holm com m = número de células de teste
pré-registradas (0,025 com duas, 0,0167 com três), e `holm_sequential_1979`, procedimento definido para uma
família fixada antes dos dados. Tirar a célula encolheria a família pelo resultado.

Há também uma derivação que dispensa a convenção: H_V é não avaliável quando o braço A não tem violação em
nenhum bloco; então todo d_b = r_B ≥ 0, a média é ≥ 0 e o p direcional do ponto 2 já é 1. Só quando a causa é
H_L não avaliável (menos de dois blocos com p99 válido) o 1 é convenção, e conservadora.

### Ponto 4: instante do evento anômalo, censura e fim de medição de reserva

**(a) Instante: usar o início da operação, não o fim.** Fonte: a regra de fase do projeto classifica cada evento
pelo início (`EventCsvWriter.java:157`, `marcadores.rotuloDe(e.startNs)`; `comparar_bracos.py:900` e `:910`,
`dentro(e[0])` e `dentro(a[0])`). Com o fim, a mesma operação que começou durante a falha e voltou com erro depois
dela conta na fase `durante` em `taxa_erro` e `falhas_percebidas`, e como anomalia pós-falha no Δ_rec. Pior: o fim
inclui a duração da operação, que o braço B altera. Com o AC-5, pedidos ao nó parado falham na hora; no braço A,
os mesmos pedidos esperam o tempo-limite. Medido pelo fim, o Δ_rec do braço B fica menor só por isso, a favor do
controlador. O início não tem esse viés. Muda `comparar_bracos.py:953` (`e[0]`, `a[0]`) e `:961` (`e[0]`), e o
teste `test_instante_de_observacao_e_o_fim_da_operacao` passa a esperar Δ_rec = 0. O texto (MET-63) passa a dizer
"a última operação iniciada depois do fim da falha que terminou em anomalia" (Escritor, pelo coordenador).

**(b) Censura: manter.** Último anômalo com t > fim − W_c, valor = fim − t_fim-falha (`:501-502`), igual à MET-63
e ao plano 3.4.

**(c) Fim de medição de reserva: retirar.** Sem `t_fim_carga_ns`, Δ_rec e Δ_rec da Metodologia ficam ausentes com
motivo, a mesma regra do Δ_conv. Fonte: plano 3.4 e MET-63 ("o fim da medição é fixado pelo protocolo e é igual
nos dois braços"). O último instante observado (`:952`) não é fixado pelo protocolo, muda entre braços e pode ser o
fim da própria anomalia, o que censura a execução automaticamente. Muda `:951-952` e o teste
`test_fim_da_medicao_sem_t_fim_carga`.

### Ponto 5: Δ_rec de sensibilidade sem janelas vazias e sem a janela final incompleta

**Manter as duas regras.** Fonte: derivação, com o plano 3.4 e 3.5. Numa janela vazia a taxa de erro é 0/0; a
regra "contagem ≤ quantil de Binomial(0, p)" daria verdadeiro por vacuidade, sem evidência de linha de base. E W é
derivado para que toda janela sem falha tenha ao menos n_lat eventos, então uma janela vazia já é anomalia de
vazão. A janela final incompleta é mais curta que W, o que contradiz a derivação; a execução sem janela inteira na
base fica censurada no máximo observável, como na definição principal. Com o ponto 4, os instantes daqui também
passam a ser o início.

### Ponto 6: sensibilidade (a) como razão agregada nos blocos com violação em A

**Manter a razão agregada.** Fonte: plano 3.3, sensibilidade (a), e G3. O estimador é o mesmo de `razao_de_taxas`
por invariante do B-39 (`_violacoes_celula`, validado na V-B37/B39), com IC por *bootstrap* de blocos. A
alternativa por bloco (média de ln(r_B/r_A)) teria de excluir também os blocos com r_B = 0, que são justamente
aqueles em que o controlador eliminou a violação: é o viés que a G3 tirou da análise principal (V-B37/B39 §6,
item 3). Ajuste menor: com menos de dois blocos, IC `None`, como `ic_boot_blocos`, em vez do intervalo degenerado
de hoje (`:1124-1129`).

### Ponto 7: Wilson ou Jeffreys por braço

**Manter Wilson.** `brown_cai_dasgupta_binomial_2001` está no `.bib` (REF-075, Decisão 018), mas a biblioteca
registra "só metadados conferidos (texto não lido)", com o enquadramento "recomenda Wilson ou Agresti-Coull"
(`memoria-tcc/04-biblioteca-oficial.md:971`). Jeffreys não tem registro no projeto. O intervalo é descritivo e a
ressalva de independência pesa mais que a escolha do método, então trocar não muda conclusão. Antes de o texto
citar o artigo para Wilson, o Bibliotecário lê e dá a página, incluindo o que o artigo diz sobre Wilson com
contagem pequena perto de zero, que é o caso das violações. Para contagem zero vale a regra de três (unilateral,
≈ 3/N, `hanley_lippmanhand_zero_1983`), não o limite de Wilson (bilateral, ≈ 3,84/N); a tabela já faz assim.

### Ponto 8: tempo ativo por tipo, com o AC-5 ativo até o fim

**Manter o tempo ativo por tipo; a ação que segue ativa no fim conta até o fim da medição (`t_fim_carga_ns`, o
mesmo do Δ_rec), não até a última linha da trilha (`:632`, `:670-674`).** `ativa_no_fim` continua marcado. Sem
`t_fim_carga_ns`, `tempo_ativo_s` ausente para essas ações. Fonte: o AC-5 não tem reversão na execução por desenho
(`acoes-controlador-cluster-2026-11-05.md` §3.1 e tabela da política, "R4 ou encerramento: reverter a pilha (só
AC-2); AC-5 segue"; decisão D-A). A última linha da trilha depende de quando o laço para; o que interessa à
comparação é o tempo com o nó parado sob carga. Ver também o achado A-1, que é mais grave.

### Ponto 9: células descritivas e de referência só com IC

**Manter: IC sem p, sem Holm e sem IU.** Fonte: plano de execução §5.2 (célula sem falha "descritiva", SYNC
"referência sem teste") e `Metodologia.tex:340`. Acrescentar na tabela Markdown, nessas células, que o IC é
descritivo, sem correção de multiplicidade, e que um IC que exclui 1 não é resultado de teste. Ver o achado A-2:
do jeito que está, a célula sem falha sai vazia.

### Ponto 10: modelo do poder com B sorteado de forma independente

**Manter o sorteio independente como curva principal e acrescentar a curva de correlação perfeita como limite
superior.** Fonte: derivação, com o plano 3.3. Com y_B = (1 − r)·y_A no mesmo sorteio, todo d_b não nulo tem o
mesmo sinal, e o poder é P(K ≥ k_min), K ~ Binomial(n, q̂), k_min = menor k com 2/2^k ≤ α, que é a conta da 3.3.
É o melhor caso. O sorteio independente é o pior caso entre correlações não negativas (heurístico: correlação
positiva dentro do bloco reduz a variância de d_b). As duas curvas lado a lado dão ao Gestor a faixa para escolher
8, 10 ou 12 blocos. O "conservador" do *docstring* (`poder.py`, cabeçalho) vale só com correlação ≥ 0, e isso deve
ser dito. O α da curva usada para decidir é o do primeiro passo do Holm entre células, α/m com m do
pré-registro (0,025 com duas células, 0,0167 com três; plano de execução §5.2), não o 0,05 padrão.

### Ponto 11: G3 (28/10) × Prompt 2 na escala de `taxa_erro` e `falhas_percebidas`

**Seguir a G3: `taxa_erro` e `falhas_percebidas_por_1000` em diferença, como secundários no Holm da célula, com a
razão com exclusão como sensibilidade (sem Holm).** Fonte: decisão G3 do Gestor de 28/10 (`decisoes-tecnicas.md`,
"Decisões do Gestor de 2026-10-28"), tomada sobre a recomendação da V-B37/B39 §6, item 3, que nomeia exatamente
essas duas taxas. O "log da razão para os demais (inalterado)" do Prompt 2 foi omissão minha ao redigir o plano de
04/11, e decisão do Gestor prevalece sobre o plano. Nomes e unidades ficam; muda `ESCALA_DIFERENCA` (`:117`),
entram as duas sensibilidades (nome a fixar pelo Worker), o README e o patch (ponto 1).

### Ponto 12: `--w-recovery-s 60`

**Tornar obrigatório e sem padrão** (`comparar_bracos.py:1488` e o padrão de `comparar()` em `:1194`). Origem do
60: B-33, D-7, "valor de partida a fixar no pré-registro de T-F" (`relatorio-b33-2026-10-15.md:85`, aceito como tal
na V-TE1); nunca foi fixado. O B-39 o copiou como padrão.

Proposta de origem para o B-40, por derivação: W_recovery = intervalo entre o fim da falha e o fim da carga no
protocolo do W-6. Com o resumo de convergência presente, a fase `recuperacao` já vai do fim da falha até depois do
fim da carga (`fases.py:39-44`: limite = max(fim + W, t_convergencia), e a convergência só roda em quiescência),
então W não muda nenhum desfecho dessas execuções; com o valor derivado, as execuções sem resumo ficam com a mesma
fronteira. A guarda B39-R1 já obriga o `energia_proxy` a usar o mesmo valor. O `--w-recovery-sec` do programa de
carga (padrão 60, `Cli.java:166`) só rotula colunas que a análise não lê; o W-6 passa o mesmo valor por coerência.

## 4. Achados fora da lista

- **A-1. AC-5 × laço de convergência (decisão do Gestor, antes do W-6 e do B-40).** Uma rodada do laço só vale com
  todos os servidores esperados na topologia (`ConvergenciaLoop.java:20-24`, `:144-146`). O AC-5 para um nó e não
  é revertido na execução. Então toda execução do braço B em que o AC-5 agir vai a `divergencia_persistente`
  depois do teto de 600 s, e as divergências da última rodada são gravadas como I5/I6. Δ_conv e divergência do
  braço B nessas execuções passam a refletir o desenho, não a convergência, e cada uma custa 10 min a mais de VM.
  O co-principal V não é afetado (I5/I6 ficam fora). Opções: (a) o orquestrador religa, no fim da carga, qualquer
  nó parado (passo simétrico: no braço A não há nó parado), e Δ_conv passa a incluir a volta do nó, que é custo
  real do AC-5; (b) Δ_conv e divergência ficam não avaliáveis nas execuções com AC-5 aplicado (identificadas pela
  trilha) e são reportadas à parte. **Recomendação: (a)**, porque mantém o tempo de convergência avaliável nas
  células de partição (objetivo 5) e evita os 600 s; (b) se o W-6 não comportar o passo no prazo.
- **A-2. Célula descritiva sem falha sai vazia.** Sem marcadores, todo instante é `antes` (`fases.py:33-34`), e as
  fases padrão `durante,recuperacao` não selecionam nada: p99 com "n<1000", vazão "fase sem duração", taxa de
  violações com denominador zero. O exemplo do README (`--celulas-descritivas ASYNC-none` na mesma chamada das
  células de teste) grava essa célula vazia sem erro. Recomendação: a célula sem falha roda em chamada própria
  com `--fases antes` (README e B-40), e o script recusa (exit 2) célula em que nenhuma execução tem evento nas
  fases pedidas. Sem limiar novo.
- **A-3. A guarda de braços pode ser contornada pela falta da trilha.** Nas células de teste, exigir a trilha nos
  dois braços (exit 2 sem ela; execução interrompida já é refeita pela DT-014) e conferir, no braço B, `atuador` ≠
  `nulo` e `politica` ≠ `nenhuma` na linha `meta` (`contrato-jsonl-controlador-v1.md`, tabela `meta`), e
  `param_sha256` igual entre A e B do mesmo bloco. Um braço B `active` com atuador nulo grava `NAO_EXECUTADA` e não
  age, o que na prática é um braço trocado.
- **A-4. Registro da decisão de energia.** `energia_proxy.py:50` e `comparar_bracos.py:50-51` atribuem a escolha
  0,3/0,5 a "decisão do coordenador de 05/11"; a decisão é do Gestor, transmitida pelo coordenador. Ela não está em
  `decisoes-tecnicas.md` (conferido em `wt-plano-ctrl`, ponta `104cab6`), e o ST-13 do plano de execução contínua
  ainda diz r = 0,5 (0,3; 0,7). Registrar antes do B-40.
- **A-5. Menores.** (i) Reversão depois de aplicação que falhou (`FALHA_NA_APLICACAO`) conta como "reversão com
  sucesso" nos descritivos (cenário do teste `Controlador.test_descritivos`, `{"sucesso": 2}`); separar por origem.
  (ii) O Holm dos secundários tira da família o desfecho sem bloco válido (`:1313`, comportamento que já vinha do
  `/1`). Por coerência com o ponto 3, o B-40 pode listar a família de secundários e fazer entrar com p = 1 quem
  ficar sem dado.

## 5. Ressalvas para o Worker (depois das decisões, na mesma branch, TDD)

| # | O quê | Origem |
|---|---|---|
| R-1 | Aplicar o patch com as duas correções da seção 2 | ponto 1 |
| R-2 | Instante de início no Δ_rec e no Δ_rec da Metodologia; retirar o fim de medição de reserva | ponto 4 |
| R-3 | `taxa_erro` e `falhas_percebidas_por_1000` em diferença, razão com exclusão como sensibilidade | ponto 11 |
| R-4 | `--w-recovery-s` obrigatório e sem padrão | ponto 12 |
| R-5 | Tempo ativo da ação ativa no fim até `t_fim_carga_ns` | ponto 8 |
| R-6 | Curva de correlação perfeita em `poder.py`; *docstring* com a condição ρ ≥ 0 | ponto 10 |
| R-7 | IC `None` na sensibilidade (a) com menos de dois blocos | ponto 6 |
| R-8 | Rótulo "descritivo" no IC das células sem teste | ponto 9 |
| R-9 | Recusa de célula sem evento nas fases pedidas; README com chamada própria para a célula sem falha | A-2 |
| R-10 | Trilha obrigatória nos dois braços das células de teste; `atuador`, `politica` e `param_sha256` conferidos | A-3 |
| R-11 | Comentários de energia: "decisão do Gestor de 05/11" | A-4 |
| R-12 | Rodar `python -E -P -m unittest discover -s analysis` em H2, registrar a contagem, matar os mutantes do Prompt 2 e escrever `docs/relatorio-b39-4-2026-11-<dd>.md` | aceite |

## 6. Perguntas ao Gestor

| # | Pergunta | Recomendação |
|---|---|---|
| P-1 | Autoriza o patch dos testes do `/1` com as duas correções? | sim |
| P-2 | Instante do evento anômalo: início (recomendado) ou fim da operação? | início |
| P-3 | Sem `t_fim_carga_ns`, Δ_rec fica ausente (sem fim de medição de reserva)? | sim |
| P-4 | `taxa_erro` e `falhas_percebidas` em diferença, como a G3 de 28/10 já decidiu? | sim |
| P-5 | `--w-recovery-s` obrigatório, com o valor derivado do protocolo no B-40? | sim |
| P-6 | AC-5 × convergência: (a) religar o nó parado no fim da carga ou (b) Δ_conv não avaliável nessas execuções? | (a); (b) se o W-6 não comportar |
| P-7 | Poder com as duas curvas (independente e correlação perfeita) e α do primeiro passo do Holm? | sim |

Os pontos 2, 3, 5, 6, 7 e 9 ficam como estão no código, com as fontes da seção 3.
