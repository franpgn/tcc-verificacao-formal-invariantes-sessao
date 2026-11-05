# Decisões do Gestor

> Registro cronológico das decisões tomadas pelo **Gestor** (o autor do TCC). Todo agente deve consultar este arquivo antes de agir. Apenas o Gestor autoriza novas entradas com status "Decidido".

---

## Decisão 000 — Inicialização do sistema multiagente

### Data
2026-05-11.

### Contexto
O Gestor forneceu o "Prompt Mestre" do sistema de 3 agentes (Escritor, Revisor, Bibliotecário). O arquivo base foi identificado no diretório do projeto como `Main-PPEC.tex` (Proposta de TCC — Unipampa, Eng. de Computação; autor Francesco Pagani Galvão; orientador Prof. Dr. Leonardo Bidese de Pinho). Acompanham o projeto: `bibliografia.bib` (9 entradas), `ppec.sty`, classe `abntex2` e *bst* `abntex2-alf` disponíveis no diretório, `logo_unipampa_color.png`, `placeins`.

### Decisão tomada
Registro do que já está implicitamente decidido pelo arquivo base (ciência, sem ação necessária): tema, objetivo geral e específicos conforme `Main-PPEC.tex`; metodologia em duas trilhas (formal + experimental); plataforma experimental Infinispan; estilo bibliográfico `abntex2-alf`.

### Impacto no TCC
Base para todos os artefatos de memória criados em 2026-05-11.

### Agentes envolvidos
Bibliotecário (criação da memória); Gestor.

---

## Decisão 001 — Escopo: desenvolver a monografia, começando por TCC-1 ✅ *(corrigida em 2026-05-12)*

### Data
2026-05-11. *(Texto corrigido em 2026-05-12 — ver "Correção" abaixo.)*

### Contexto
O arquivo base é a Proposta. Opções apresentadas: (a) refinar a Proposta; (b) desenvolver a monografia completa começando por TCC-1; (c) já estar em TCC-2.

### Decisão tomada
**Desenvolver a monografia completa, começando por TCC-1**, conforme o cronograma do arquivo base. **TCC-1 corresponde às Semanas 1–14 do cronograma** (revisões dirigidas; preparação do ambiente experimental; *workload* e instrumentação; coleta piloto; escrita da monografia até Cap. 3 + apêndice com plano para TCC-2). **TCC-2 corresponde às Semanas 15–34** (Modelo formal v1/v2, *baselines* com falhas, alinhamento formal-experimental, controlador heurístico, análise comparativa, escrita da monografia TCC-2).

### Correção (2026-05-12)
A redação inicial dizia *"(Caps. 1–3 + modelo formal v1)"* — o termo "modelo formal v1" foi um erro de escopo. O **Modelo formal v1** está nas Semanas 15-16, portanto pertence a **TCC-II**. O Gestor apontou a leitura correta do cronograma em 2026-05-12.

### Impacto no TCC
- A estrutura de [`03-estrutura-capitulos.md`](03-estrutura-capitulos.md) (6 capítulos) é a estrutura da **monografia completa**; em TCC-1, **Caps. 1-3 são escritos**, **Caps. 4-6 ficam como placeholders / pré-escritos minimamente / com plano para TCC-2 num apêndice**.
- Prioridade imediata em TCC-1: revisões dirigidas (Sem 1-6), Caps. 1, 2, 3 (escrita em Sem 12-14).
- **Modelo formal, *baselines* experimentais e controlador heurístico são trabalho de TCC-II — não são entregáveis de TCC-1.**
- A seção "Cronograma" do arquivo base deve ser **dividida** ao migrar para a monografia (P-018): **Quadro 1 — Cronograma de TCC-I** (Sem 1-14) no corpo do trabalho; **Quadro 2 — Cronograma de TCC-II** (Sem 15-34) no apêndice "Plano de trabalho para TCC-2".

### Agentes envolvidos
Todos.

---

## Decisão 002 — Modelo LaTeX da monografia: classe `abntex2` ✅

### Data
2026-05-11.

### Contexto
Manter `article` + `ppec.sty` (modelo da Proposta) vs. migrar para `abntex2` (já presente no diretório).

### Decisão tomada
**Migrar para a classe `abntex2`** para a monografia. Implica: usar `\documentclass[...]{abntex2}`; carregar `abntex2cite.sty` → citações ABNT autor-data com `\citeonline{chave}` ("Autor (ano)") e `\cite{chave}` ("(AUTOR, ano)"); usar `\chapter`/`\section`/`\subsection`; elementos pré-textuais (capa, folha de rosto, folha de aprovação, resumo, *abstract*, listas, sumário) e pós-textuais (referências, apêndices) via os comandos do `abntex2`; `\imprimirfichacatalografica` opcional; figuras/tabelas com `\legend{Fonte: ...}`.

### Impacto no TCC
- Criado o esqueleto `Main-Monografia.tex` (raiz do projeto). O `Main-PPEC.tex` permanece intacto como a Proposta.
- [`09-padrao-escrita-latex-abnt.md`](09-padrao-escrita-latex-abnt.md) atualizado para o padrão `abntex2`.
- Comando de citação passa a ser `\citeonline`/`\cite` conforme o caso (não mais só `\cite`).

### Agentes envolvidos
Escritor (escreve no novo modelo); Revisor (checa conformidade).

---

## Decisão 003 — Revisão de literatura: narrativa + mini-revisão estruturada ⛔ SUPERADA (Decisão 010, 2026-05-12)

### Data
2026-05-11.

### Contexto
Definir entre revisão narrativa pura (como na Proposta), narrativa + mini-revisão estruturada, ou revisão sistemática completa.

### Decisão tomada (originalmente)
Revisão dirigida/narrativa na maior parte do Cap. 2, com um protocolo estruturado leve aplicado a um subconjunto de temas-chave.

### Por que foi superada
A Proposta foi atualizada pelo Gestor em 2026-05-12 e agora declara **mapeamento sistemático completo** (Petersen 2008 + PRISMA 2020 + Neiva & Silva 2016) como metodologia oficial. A Decisão 003 não é mais aplicável — substituída pela Decisão 010.

### Agentes envolvidos
Bibliotecário; Escritor; Gestor.

---

## Decisão 004 — Buscas bibliográficas: somente sob autorização explícita ✅

### Data
2026-05-11.

### Contexto
Pergunta sobre autorizar agora a rodada de busca/verificação das referências candidatas e confirmar a biblioteca herdada.

### Decisão tomada
**Nenhuma busca bibliográfica será realizada por enquanto** — o Bibliotecário só executará busca/verificação **quando o Gestor disser explicitamente para fazê-lo**. Até lá: (i) o Escritor trabalha **apenas com as 9 referências herdadas** de `bibliografia.bib` (mantidas como pré-aprovadas; o Gestor foi consultado e não objetou — confirmação final segue em aberto, P-014); (ii) toda afirmação que dependa de fonte ainda inexistente recebe o marcador `% [FONTE NECESSÁRIA: ...]` no LaTeX e entra como pendência; (iii) [`05-referencias-candidatas.md`](05-referencias-candidatas.md) permanece como lista de espera (não citável).

### Impacto no TCC
- P-015 atualizada para "aguardando autorização explícita do Gestor".
- Cap. 1 e Cap. 3 podem ser redigidos de imediato (cobertos pelas herdadas + marcadores). Cap. 2 só será redigido em esqueleto + trechos fundamentáveis pelas herdadas; o miolo aguarda a futura rodada de busca.

### Agentes envolvidos
Bibliotecário; Escritor; Gestor.

---

## Decisão 005 — Autorização parcial: análise do material curado pelo Gestor ✅ *(a confirmar)*

### Data
2026-05-12.

### Contexto
O Gestor forneceu o repositório `franpgn/repo` com material curado por ele (4 PDFs, 5 CSVs de buscas Springer, lista de URLs ACM/Springer), disponibilizado em `/workspace/referencias-gestor/`. A entrega deste material foi interpretada pelo Bibliotecário como **autorização explícita** (no sentido da Decisão 004) para realizar busca/análise **restrita a esse corpus**.

### Decisão tomada
1. O Bibliotecário **fica autorizado** a analisar o material em `/workspace/referencias-gestor/`, extrair candidatas com metadados verificados (via leitura direta dos PDFs ou Crossref/API), e registrá-las em [`05-referencias-candidatas.md`](05-referencias-candidatas.md).
2. **Buscas em bases externas** (IEEE Xplore, ACM DL, Scopus, etc.) **permanecem suspensas** até nova autorização (Decisão 004 segue válida para o restante).
3. As candidatas extraídas (CAND-022..046, 25 itens) **continuam não-citáveis** — cada uma exige aprovação individual do Gestor para migrar para [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md).
4. **Documento de síntese do Gestor** (`Research Basis From File Analysis.pdf`) é um **insumo interno** (produzido em Google Docs/Gemini) — **não citável**, usado apenas como mapa temático.
5. Link sci-hub presente em `referencias-web.txt` é **ignorado** (questão ética/legal); links Gemini/Scholar privados também.

### Impacto no TCC
- Cobertura forte agora nos eixos A, B, C, D, E e G; lacunas remanescentes em F (chaos engineering/Jepsen) e H (sessão — OWASP/NIST).
- **FLP 1985** (PDF integral) entra como candidata fundamental (CAND-028) — recomendação forte do Bibliotecário.
- **Neiva & Silva 2016** entra como referência metodológica em português (CAND-043) — recomendação forte.
- P-015 passa de "aguardando autorização" a "parcialmente em andamento, restrita ao corpus do Gestor".
- Análise consolidada em [`10-material-gestor-analise.md`](10-material-gestor-analise.md).

### Próximas ações
- **(Gestor)** revisar e aprovar/rejeitar individualmente CAND-022..046 (ou em lote por eixo).
- **(Bibliotecário)** após aprovação, migrar cada item para a biblioteca oficial.
- **(Escritor)** seguir com **TCC-1 Semanas 1-2** (recorte e critérios) usando as fontes já aprovadas (REF-001..009) e os `% [FONTE NECESSÁRIA: ...]` para o que depender de CAND ainda não validadas.

### Agentes envolvidos
Bibliotecário (análise e registro); Gestor (autorização tácita pela entrega + aprovação individual pendente); Escritor (afetado pelas futuras aprovações).

---

## Decisão 006 — Aprovação em lote das candidatas do corpus do Gestor ✅

### Data
2026-05-12.

### Decisão tomada
Aprovação em lote por eixo das 25 candidatas (CAND-022..046) extraídas do material do Gestor:
- **Aprovadas (22):** CAND-022, 023, 024, 025, 026, 027, 028, 029, 030, 031, 032, 034, 035, 036, 037, 038, 039, 040, 041, 042, 043, 045. Migradas para [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md) como **REF-010..REF-031** (entradas BibTeX em `bibliografia.bib`).
- **Descartadas (2):** CAND-044 (Jain, Murty, Flynn 1999 — uso fraco no TCC) e CAND-046 (Benítez-Pérez 2018 — modelagem genérica de baixo retorno).
- **Pendente (1):** CAND-033 (Chen et al. 2026 — model checking + decision support) — decidir caso a caso na redação do Cap. 4.

### Impacto no TCC
Biblioteca oficial passa de 9 para **31 referências citáveis**. Eixos A, B, C, D, E e G ganharam cobertura substancial. P-015 segue parcialmente em andamento (lacunas F e H — ver Decisão 009).

### Agentes envolvidos
Gestor (decisão); Bibliotecário (execução da migração).

---

## Decisão 007 — Conjunto de invariantes I1..I6 aprovado ✅

### Data
2026-05-12.

### Decisão tomada
O conjunto **I1..I6** proposto em [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md) é aprovado tal como apresentado: I1 (irreversibilidade de logout), I2 (consistência de bloqueio), I3 (monotonicidade local do contador), I4 (unicidade do motivo de invalidação), I5 (convergência do estado lógico), I6 (convergência do contador). Estes são os invariantes-alvo do **modelo formal v1**.

### Impacto no TCC
P-016 resolvida. Habilita início do modelo formal v1 (Semanas 15-16 do cronograma). Cap. 4 (Desenvolvimento) ganha conjunto formal estável para trabalhar.

### Agentes envolvidos
Gestor; Escritor (formalização no modelo); Revisor (já tinha aprovado em 1ª rodada).

---

## Decisão 008 — Escopo de falhas em TCC-1: F1..F4 (multi-site reservado para TCC-2) ✅

### Data
2026-05-12.

### Decisão tomada
TCC-1 cobre **F1** (*crash*/restart), **F2** (partição de rede), **F3** (atraso/jitter) e **F4** (*state transfer* induzido). **F5** (multi-site / *cross-site replication*) fica reservada para **TCC-2**.

### Impacto no TCC
- P-016a resolvida.
- Modelo formal v1 simplificado: canal assíncrono único, sem múltiplos sites.
- Cap. 1 (escopo) e Cap. 3 (cenários) limitados a F1-F4 em TCC-1.
- REF-007 (Infinispan *xsite*) continua citável quando o texto for fazer contraste conceitual; mas não é experimentada em TCC-1.

### Agentes envolvidos
Gestor; Escritor.

---

## Decisão 009 — Busca focada nas lacunas F e H autorizada ✅

### Data
2026-05-12.

### Decisão tomada
Bibliotecário fica autorizado a buscar referências específicas para:
- **Eixo F** — Gilbert & Lynch 2002 (CAP); Bailis & Kingsbury 2014 (*The Network is Reliable*); Jepsen (Kyle Kingsbury); Chandra & Toueg 1996 (failure detectors — CAND-006 retomada).
- **Eixo H** — OWASP Session Management Cheat Sheet; NIST SP 800-63B (Digital Identity Guidelines: Authentication & Lifecycle Mgmt).

Resultados retornam como candidatas (CAND-047..) para aprovação individual.

### Impacto no TCC
P-021 e P-022 saem de "aguardando autorização" para "em execução". Fecha as lacunas conhecidas antes das Semanas 3-4 (revisão dirigida).

### Agentes envolvidos
Bibliotecário (execução); Gestor (validação dos resultados).

---

## Decisão 010 — Sincronia com Proposta atualizada: mapeamento sistemático adotado; Decisão 003 superada ✅

### Data
2026-05-12.

### Contexto
O Gestor identificou que a versão de `Main-PPEC.tex` em `/workspace/` estava desatualizada. A Proposta correta está em `/workspace/Modelo_LaTeX_para_Projeto_de_Trabalho_de_Conclusão_de_Curso/Main-PPEC.tex` e traz três mudanças materiais:
1. **Introdução** acrescenta um parágrafo de **sustentabilidade computacional** (Turner 2023 + Morán et al. 2024).
2. **Objetivos** foram reformulados — agora começam pela **pergunta central de pesquisa** explicitamente declarada.
3. **Metodologia** passou de "duas trilhas" (formal + experimental) para **duas frentes**: **(i) mapeamento sistemático** (Petersen 2008 + PRISMA 2020) **+** (ii) implementação e validação experimental, com o recorte da Frente 2 **derivado dos achados da Frente 1**.
4. **Cronograma** dividido em **dois Quadros**: Quadro 1 (TCC-I) e Quadro 2 (TCC-II), cada um com **12 semanas em 6 etapas quinzenais**.

### Decisão tomada
1. **Sincronizar workspace:** a nova `Main-PPEC.tex` é a fonte de verdade; `/workspace/Main-PPEC.tex` foi atualizado em 2026-05-12; `bibliografia.bib` foi fundido (mantém REF-010..036 do trabalho anterior + 5 novas pré-aprovadas REF-037..041); `ppec.sty` é idêntico.
2. **Adotar mapeamento sistemático** como metodologia de revisão de literatura — **substitui Decisão 003** (narrativa + mini-revisão).
3. **Decisão 004 (suspensão de buscas externas)** é **superada** no escopo do mapeamento — buscas serão executadas em Sem 3-4 de TCC-I conforme protocolo da Sem 1-2.
4. **TCC-I = 12 semanas, 6 etapas quinzenais** (Quadro 1 da Proposta atualizada). Atividades:
   - Sem 1-2: pergunta de pesquisa + protocolo do mapeamento sistemático + esquema de extração;
   - Sem 3-4: busca + triagem (título/resumo) + estabilizar critérios I/E;
   - Sem 5-6: leitura completa + extração + classificação + síntese + recorte do protótipo;
   - Sem 7-8: ambiente experimental + *workload* + invariantes + modelo formal inicial;
   - Sem 9-10: *model checking* inicial + baseline experimental com falhas (p95/p99);
   - Sem 11-12: consolidação de resultados parciais + ajustes + escrita final da monografia.
   - **Escrita da monografia em paralelo durante todas as 12 semanas.**
5. **Itens de TCC-I de Decisão 001 corrigidos:** modelo formal **inicial** começa em Sem 7-8 de TCC-I (não é exclusivo de TCC-II como eu havia corrigido erroneamente na conversa anterior). *Baseline* experimental com falhas inicial também é TCC-I (Sem 9-10). Modelo formal v1/v2 maduros, *baselines* completos e controlador heurístico permanecem como TCC-II.
6. **REF-037..041** entram como **pré-aprovadas** (mesma lógica das 9 originais — vêm na Proposta).

### Impacto no TCC
- [`02-metodologia.md`](02-metodologia.md) reescrito.
- [`01-tema-problema-objetivos.md`](01-tema-problema-objetivos.md): adicionar ângulo de sustentabilidade (P-002 ganha argumento extra).
- [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md) ganha REF-037..041.
- [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md) **reutilizado** mas reposicionado: o "recorte do protótipo" agora é entregável de **Sem 5-6** (depois do mapeamento), não Sem 1-2.
- [`12-revisao-dirigida-tcc1-sem3-4.md`](12-revisao-dirigida-tcc1-sem3-4.md) ⛔ **OBSOLETO** — Sem 3-4 não é mais revisão dirigida; é execução de busca + triagem do mapeamento sistemático.
- P-004 (natureza da revisão) reaberta e re-resolvida com mapeamento sistemático completo. P-004a (temas da mini-revisão) cancelada.
- P-015 (rodada de busca) — agora será executada em Sem 3-4 do TCC-I com protocolo formal.
- Novo planejamento em [`13-planejamento-tcc1.md`](13-planejamento-tcc1.md) (a criar).

### Agentes envolvidos
Gestor (atualização da Proposta + identificação do desalinhamento); Bibliotecário (sincronização e re-catalogação); Escritor (re-planejamento das semanas de TCC-I).

---

## Decisão 011 — Aprovação consolidada do planejamento de TCC-I (16 pontos) ✅

### Data
2026-05-13.

### Contexto
Após a sincronia com a Proposta atualizada (Decisão 010) e a publicação de [`13-planejamento-tcc1.md`](13-planejamento-tcc1.md), o Gestor revisou 16 pontos do planejamento em 4 lotes de aprovação.

### Decisões tomadas (lote por lote)

**Lote 1 — Macro plano + Sem 1-2:**
1. ✅ Plano macro de 6 etapas quinzenais aprovado como está.
2. ✅ Pergunta de pesquisa atual do [`Main-PPEC.tex`](../Main-PPEC.tex) linha 49 — **usar tal qual** (sem refinamento em Sem 1-2).
3. ✅ **Escopo do mapeamento sistemático: TODOS os 8 eixos (A-H)** — replicação/consistência, IMDG, métodos formais, SLO/latência/observabilidade, autoadaptativo, falhas/chaos, metodologia, sessão. Resolve P-004b. *Risco aceito: maior carga de Sem 3-6.*
4. ✅ Pool em `/workspace/referencias-gestor/` como **conjunto-semente para calibração** do protocolo — busca formal nas bases (ACM, IEEE, Scopus, Springer) é o que conta para PRISMA.

**Lote 2 — Sem 3-6 + reuso:**
5. ✅ [`12-revisao-dirigida-tcc1-sem3-4.md`](12-revisao-dirigida-tcc1-sem3-4.md) — **arquivar para reuso seletivo** no Cap. 2 (conteúdo preservado abaixo do cabeçalho "OBSOLETO").
6. ✅ CAND-018 (Jepsen) — **inclusão restrita** como literatura cinza/apoio empírico, decisão caso a caso na análise dos relatórios.
7. ✅ Recorte preliminar (O1-O7, F1-F4, I1-I6, M1-M7) — **aberto a refinamento** em Sem 5-6 conforme achados do MS; cada alteração precisa de aprovação individual.
8. ✅ **Estrutura da monografia: 5 capítulos** — Cap. 1 Introdução; Cap. 2 Fundamentação Teórica; Cap. 3 Metodologia; **Cap. 4 Desenvolvimento e Resultados Parciais (FUNDIDO)**; Cap. 5 Conclusões. *Resolve P-008.*

**Lote 3 — Sem 7-10 (técnico):**
9. ✅ **P-017 (TLA+ vs Alloy vs Must) — decidir em Sem 7-8** com base no que o MS revelar. Não bloqueia Sem 1-2.
10. ✅ Especificação formal **inicial em Sem 7-8 cobre O1-O7 + canal assíncrono + I1-I6 SEM falhas explícitas**. Falhas no modelo são TCC-II (Sem 3-4).
11. ✅ **Baseline experimental em TCC-I (Sem 9-10) cobre F1 (crash) + F3 (latência/jitter)**; F2 (partição) e F4 (state transfer) ficam para TCC-II.
12. ✅ **P-016b — não incluir I7** (unicidade de sessão ativa por identidade). Mantém o conjunto I1-I6.

**Lote 4 — Sem 11-12 + administrativo:**
13. ✅ Apêndice "Plano de trabalho para TCC-II" = **Quadro 2 + narrativa expandida** (2-3 parágrafos por etapa explicando entregáveis, dependências, riscos).
14. ✅ **P-014 — todas as 14 referências herdadas confirmadas como oficiais** (REF-001..009 + REF-037..041), incluindo `infinispan_intro` (REF-009) e `lemos_eradrs_2025` (REF-041), mesmo não citadas no `Main-PPEC.tex` (disponíveis como fontes conceituais).
15. ✅ **Defesa de TCC-I** (slides + script) **entra como entregável formal de Sem 11-12**; esboço de slides pode começar em Sem 9-10.
16. ✅ **Subdiretório `/workspace/prototipo/`** para *workload*, *scripts* de cluster Infinispan, *configs* de cache, scripts de injeção de falhas e coleta de métricas. A ser criado em Sem 7-8.

### Pendências resolvidas
- P-004 (já re-resolvida pela Decisão 010); P-004b (resolvida pelo ponto 3); P-008 (resolvida pelo ponto 8); P-014 (resolvida pelo ponto 14); P-016b (resolvida pelo ponto 12); P-018 (encaminhada pelo ponto 13 — fechamento final em Sem 11-12).

### Pendências repactuadas
- P-017 — confirmação de que a decisão acontece em Sem 7-8 (não bloqueia Sem 1-2).
- P-019 (pré-textuais) — fechamento em Sem 11-12 (junto com a escrita final).

### Impacto no TCC
- [`13-planejamento-tcc1.md`](13-planejamento-tcc1.md) **atualizado** com os parâmetros aprovados (MS amplo nos 8 eixos; estrutura de 5 caps; F1+F3 em TCC-I; defesa como entregável; diretório `prototipo/`).
- [`03-estrutura-capitulos.md`](03-estrutura-capitulos.md) **reescrito** para 5 caps.
- TCC-I está **autorizado a arrancar Sem 1-2** assim que o Bibliotecário/Escritor estiverem prontos.

### Agentes envolvidos
Gestor (16 aprovações); Bibliotecário (registro e propagação); Escritor (próximo a iniciar Sem 1-2).

---

## Decisão 012 — Curadoria da biblioteca pós-aquisição CAPES (2026-05-17) ✅

### Contexto
Após o ciclo de aquisição de 2026-05-17 (Unpaywall automático trouxe 7 itens; Gestor entregou +8 PDFs via CAPES/repositórios), a biblioteca tem 28 das 41 REFs com texto integral. Restam bloqueios em 7 itens SpringerLink (CAPES-Springer com falha de autenticação reportada pelo Gestor), além de 2 itens HAL não recuperados (REF-019, REF-022). O Bibliotecário propôs uma curadoria em três frentes (despromoção de redundantes, promoção de seminais já em mãos, aquisição de OAs faltantes) e o Gestor delegou execução com "faça o que achar melhor".

### Decisões executadas pelo Bibliotecário sob delegação
1. ✅ **Dispensar REF-012** (Preguiça/Baquero/Shapiro — *CRDTs Encyclopedia* 2019) — redundante com REF-002 (Shapiro 2011 seminal, em mãos) + bloqueada no SpringerLink.
2. ✅ **Dispensar REF-014** (Salhi — *Benchmarking Distributed Cache* 2018) — coberta pelas 4 REFs Infinispan + REF-013/015 + bloqueada no SpringerLink.
3. ✅ **Promover CAND-001 a REF-042** (Lamport 1978 — *Time, Clocks*) — fecha lacuna sobre relógios lógicos / *happens-before* no Cap. 2.2; texto integral já em mãos desde 2026-05-14.
4. ✅ **Promover CAND-004 a REF-043** (Ongaro & Ousterhout 2014 — Raft) — fecha lacuna sobre **algoritmo de consenso prático** no Cap. 2.4 (FLP + CAP + Chandra-Toueg eram todos resultados abstratos); PDF OA baixado de `raft.github.io/raft.pdf` (18 pg, 92 KB) em 2026-05-17.
5. ✅ **Aprovar CAND-005** (Herlihy & Wing 1990 — Linearizability, ACM TOPLAS) **em princípio, aguardando aquisição via CAPES/ACM** — DOI 10.1145/78969.78972. Sem isso, a definição de I1 (linearizabilidade-de-sessão) fica sem ancoragem canônica.
6. ✅ **Adquirir REF-023** (Nguyen JBCS 2019) **via URL direta SpringerOpen** — `link.springer.com/content/pdf/10.1186/s13173-019-0091-9.pdf` (PDF 25 pg, 115 KB de texto).
7. ⚠️ **REF-019, REF-022, REF-026 permanecem em busca** — HAL inacessível por DNS no sandbox (REF-019/022); preprint Anna (REF-026) não está na página de Hellerstein consultada. Gestor pode tentar HAL manualmente; Bibliotecário sugere aceitar perda de REF-026 (substituível por REF-025/027) se preprint não for localizado em mais 1 ciclo.
8. ✅ **Aceitar perda controlada** de REF-013, REF-020, REF-028 e REF-029 (todos SpringerLink, todos Tier 2/3 com substitutos em mãos) — citação via abstract OpenAlex apenas; marcar limitação no Cap. 2.4 (eixo E).

### Balanço da biblioteca após Decisão 012
- **31 REFs efetivas** (41 originais + 2 promovidas − 2 dispensadas − 10 ainda sem texto integral; das 10, REF-023 e REF-031 ficaram com texto integral; 4 sem versão OA viáveis (REF-013, 020, 028, 029); 4 ainda em busca (REF-019, 022, 026, e REF-005 Herlihy-Wing como CAND aprovada).
- **30 leituras integrais** (28 anteriores + REF-023 + REF-043; REF-042 já contava como CAND-001 desde 2026-05-14).
- Eixos A, B, C, D, F, G, H, contexto geral e tempo/ordenação: cobertos com profundidade.
- Eixo E (autoadaptativo): com gap residual; aceita-se REF-025+REF-027 como cobertura mínima.

### Pendências resolvidas
- Lacuna "Lamport 1978 seminal" — fechada (REF-042).
- Lacuna "algoritmo de consenso prático" — fechada (REF-043).
- REF-023 e REF-031 do bucket de pendentes — fechadas.

### Pendências repactuadas / novas
- **P-023 (nova):** aquisição de Herlihy & Wing 1990 via CAPES/ACM (depende do Gestor).
- **P-024 (nova):** caçada final por REF-019 (Enea Must) e REF-026 (Anna preprint); se infrutífera em 1 ciclo adicional, aceitar perda.

### Impacto no TCC
- `bibliografia.bib`: +1 entrada (Raft `ongaro_raft_2014`) — total 42 entradas.
- `04-biblioteca-oficial.md`: REF-012 e REF-014 marcadas DISPENSADA; REF-042 e REF-043 adicionadas; mapa de eixos atualizado.
- `05-referencias-candidatas.md`: header com promoções e dispensas; CANDs marcadas inline.
- `17-leitura-integral-refs.md`: total sobe a 30; seção "adições 2026-05-17 ciclo 2" adicionada.
- `PENDENTES-DOWNLOAD-MANUAL.md`: REF-023 e REF-031 marcadas resolvidas; status REF-019/022/026 atualizado.
- Escritor liberado a citar REF-042 (Lamport) em §2.2 e REF-043 (Raft) em §2.4 imediatamente.

### Agentes envolvidos
Bibliotecário (execução autorizada pelo Gestor com "faça o que achar melhor"); Gestor (delegação de decisão).

---

## Decisão 013 — Promoção de duas candidatas da sessão Sem 7-9 (2026-05-25) ✅

### Contexto
Na sessão de 2026-05-25, o Gestor pediu fundamentação bibliográfica dos parâmetros experimentais das Sem 7-9 do TCC-I. O Bibliotecário levantou cinco fontes externas como candidatas (P-025), registrou-as em [`19-metodologia-testes-sem7-9.md`](19-metodologia-testes-sem7-9.md) e produziu análise comparada. Em consulta ao Gestor, recomendou promover apenas duas das cinco. O Gestor aceitou a recomendação.

### Decisões aprovadas
1. ✅ **Promover `infinispan_tuning` a REF-045** (*Infinispan Performance Considerations and Tuning Guidelines*, documentação oficial do Infinispan Project). Justificativa: mesma família e mesma autoridade das REFs Infinispan já aprovadas (REF-006/007/008/009); fornece valores numéricos oficiais para `numOwners`, `numSegments`, MTU e *thread pools*, ancorando as linhas T3, T4 e T15 da Tabela~1 do Cap.~3 §3.3.5.
2. ✅ **Promover `cooper_ycsb_2010` a REF-046** (Cooper, Silberstein, Tam, Ramakrishnan & Sears, 2010, SoCC). Justificativa: paper canônico do YCSB, revisado por pares (ACM), com mais de 6 000 citações; define os \emph{workloads core} A-F (incluindo \emph{Workload A} descrito explicitamente pelos autores como \emph{``session store recording recent actions''}), as distribuições Zipfian/uniforme/\emph{latest} e a separação \emph{load}/\emph{run}. Ancora as linhas T6, T7 e T11 da Tabela~1; até esta decisão, o texto recorria a REF-015 (TailX) como \emph{proxy} para YCSB.
3. ✅ **Dispensar `keycloak_benchmark_timeouts`** (documentação do Keycloak Benchmark). Justificativa: não é revisado por pares e não é documentação oficial \emph{da tecnologia utilizada} (Infinispan/JGroups), apenas de um consumidor. Os mesmos valores de detecção de falha (FD\_ALL3 \emph{heartbeat} 8\,s, \emph{timeout} 40\,s) são alcançáveis pela documentação oficial do Infinispan (REF-006 / `infinispan_docs_stable`), já citada em T16.
4. ✅ **Dispensar `ispnperftest`** (repositório GitHub de Bela Ban). Justificativa: artefato informal, sem revisão por pares; os parâmetros que poderia justificar (mistura 80/20, 50\,000 ops, 1\,000\,B) já são cobertos por REF-046 (YCSB Cooper) e REF-015 (TailX) e o texto adota valores distintos.
5. ✅ **Dispensar `aosa_infinispan`** (capítulo de Manik Surtani em \emph{AOSA}). Justificativa: capítulo de livro editado fora de \emph{venue} acadêmico tradicional; o conteúdo qualitativo sobre \emph{bottlenecks}, \emph{thread pools} e RadarGun é redundante com REF-045 e REF-024 (Observability Survey).

### Balanço da biblioteca após Decisão 013
- **33 REFs efetivas** (31 anteriores + REF-045 + REF-046; REF-044 Herlihy-Wing já contava desde Decisão 012).
- Total de entradas no `bibliografia.bib`: 44 (era 43 com `infinispan_tuning` já adicionada em 2026-05-25 antes da ratificação, + `cooper_ycsb_2010` nesta decisão).
- Eixo **B** (IMDG/cache) ganha REF-045; eixo **D** (SLO/latência/observabilidade) ganha REF-046.
- Lacunas residuais: as mesmas listadas após Decisão 012 (não alteradas).

### Pendências resolvidas
- **P-025:** fechada com 2 promoções e 3 dispensas.

### Pendências repactuadas / novas
- Nenhuma criada.

### Impacto no TCC
- `bibliografia.bib`: + entrada `cooper_ycsb_2010`. Total 44 entradas.
- `04-biblioteca-oficial.md`: fichas de REF-045 e REF-046 adicionadas; mapa de eixos atualizado (B e D); contagem efetiva sobe para 33.
- `Metodologia.tex` (Cap.~3 §3.3.5): citações a `cooper_ycsb_2010` adicionadas nas linhas T6, T7 e T11 da Tabela~\ref{tab-parametros-baseline}; parágrafo de comentário ajustado para citar Cooper diretamente; linha \verb|\fonte| atualizada.
- `19-metodologia-testes-sem7-9.md`: marcações inline de promoção/dispensa nas cinco candidatas.

### Agentes envolvidos
Bibliotecário (análise e execução); Gestor (aprovação explícita da recomendação).

---

## Decisão 014 — Aprovações de Sem 7-9, fechamento de P-017 e ratificação de pontos pendentes (2026-05-25) ✅

### Contexto
Com a criação dos agentes Senior e Worker (sessão 2026-05-25), o Senior produziu o planejamento inicial do repositório `tcc-desenvolvimento` em [`/workspace/tcc-desenvolvimento/docs/planejamento-inicial.md`](file:///workspace/tcc-desenvolvimento/docs/planejamento-inicial.md) com nove pontos D-001 a D-009. O Gestor aprovou em duas etapas: o "perfeito, pode trabalhar" liberou B-01 e a recomendação geral do Senior (D-001); a aprovação posterior ("aprovado!" sobre a análise do cronograma Sem 7-9) ratificou os demais pontos e a sequência semana-a-semana.

### Decisões aprovadas
1. ✅ **D-001 (Migração esqueleto → repositório).** Confirmada a migração do esqueleto `/workspace/prototipo/` para `tcc-desenvolvimento/`. O caminho `/workspace/prototipo/` fica congelado como *snapshot* coerente com o Cap. 4 §4.4 já entregue.
2. ✅ **D-002 (Toolchain do *workload*).** Java 17 + Maven mantidos como única plataforma do *workload*.
3. ✅ **D-003 (Toolchain de análise estatística).** **Python (SciPy + Matplotlib)** adotado como linguagem padrão para `analysis/`. R fica fora do escopo de TCC-I.
4. ✅ **D-005 (Versionamento de `runs/`).** Apenas `summary.json` é versionado; CSVs e logs brutos ficam em `.gitignore`. Sem Git LFS. Justificativa: simplicidade do *workflow* e tamanho previsível dos *summaries*.
5. ✅ **D-006 (Primeiro `push`).** Autorização **diferida** até o fechamento do **B-04** (migração do esqueleto do *workload*). Razão: agrupar B-01 a B-04 em um *commit-set* coerente antes do primeiro PR.
6. ✅ **D-008 (Bloco análise em TCC-I).** Parsing + percentis (B-16) entram em TCC-I; Mann-Whitney + *bootstrap* completos (B-17, B-18) ficam para TCC-II. A Tabela 1 do Cap. 3 §3.3.5 (T22) permanece como compromisso metodológico; a execução plena fica para a etapa seguinte.
7. ✅ **D-009 (`DIST_SYNC` + `DIST_ASYNC`).** Ambos os modos são executados no *baseline* de TCC-I. `DIST_ASYNC` é o cenário-alvo (objeto de estudo); `DIST_SYNC` entra como controle de sanidade. Coerente com a linha T1 da Tabela 1.
8. ✅ **P-017 — RESOLVIDA.** Ferramenta de *model checking* fica formalmente fixada como **TLA+/TLC**, ratificando o que o Cap. 4 §4.2 e a Tabela 1 T21 já adotam *de facto*. Must (REF-019) permanece como alternativa a revisitar em TCC-II caso o refinamento das transições de canal exija primitivas que escapem a TLA+ (já registrado em §4.2).
9. ✅ **Sequência Sem 7-9 aprovada** conforme análise de cronograma apresentada em 2026-05-25 (Sem 7: B-02 + B-03 + B-04 + B-14; Sem 8: B-05 a B-10 + B-15; Sem 9: B-11 + B-12 + B-13 + primeira execução TLC + B-19 + início B-16).
10. ✅ **Pontos abertos P-A1, P-A2, P-A3 da [`docs/planejamento-inicial.md`](file:///workspace/tcc-desenvolvimento/docs/planejamento-inicial.md):** P-A1 (Java/Hot Rod) e P-A2 (Python) cobertos por D-002/D-003 acima; P-A3 (versionamento de `runs/`) coberto por D-005.

### Pendências resolvidas
- **P-017** (escolha da ferramenta de MC) — fechada com TLA+/TLC.
- **D-001 a D-009** — todas ratificadas (D-004 ratificada implicitamente desde o bootstrap com `main` + *feature branches* sem `develop` intermediária; D-007 ratificado com a aprovação do início por B-01).

### Pendências repactuadas / novas
- **P-024** (caçada final REF-019/REF-026) — mantida em aberto; Bibliotecário pode executar o ciclo adicional autorizado pela Decisão 012 quando o cronograma permitir.

### Impacto no TCC
- `bibliografia.bib`: nenhuma alteração.
- `Metodologia.tex`: nenhuma alteração imediata; eventual ajuste em §4.2 do Cap. 4 para registrar P-017 fechada se o Gestor pedir.
- `tcc-desenvolvimento/`: liberada execução de B-02 (migração do *cluster*), B-03 (perfil ASYNC), B-04 (migração do *workload*) e B-14 (spec completa); o primeiro `push` fica condicionado ao fechamento de B-04 (D-006).
- `06-decisoes-do-gestor.md`, `07-pendencias.md` e auto-memória atualizados.

### Agentes envolvidos
Gestor (aprovação em duas etapas); Senior (planejamento e proposta de sequência); Bibliotecário (registro).

---

## Decisão 015 — Tarefa T-A: subseção da Metodologia justificando a escolha do TLA+ + promoção de 4 fontes ✅

### Data
2026-06-18.

### Contexto
O Gestor aprovou a Tarefa T-A: criar uma subseção na Metodologia justificando a escolha do TLA+ comparando-o com alternativas. O Bibliotecário foi instruído a promover ao `bibliografia.bib` as quatro fontes necessárias (já verificadas em avaliação anterior) antes da redação pelo Escritor.

### Decisões aprovadas
1. ✅ **Promover quatro fontes** para a subseção T-A, com metadados verificados pelo Bibliotecário:
   - **REF-047** `lamport_specifying_systems_2002` (Lamport, *Specifying Systems*, Addison-Wesley, 2002, ISBN 978-0-321-14306-8) — ex-CAND-010; referência primária da linguagem TLA+.
   - **REF-048** `newcombe_aws_formal_methods_2015` (Newcombe et al., "How Amazon Web Services Uses Formal Methods", *CACM* 58(4):66-73, 2015, DOI 10.1145/2699417) — ex-CAND-011; evidência de adoção industrial.
   - **REF-049** `jackson_software_abstractions_2012` (Jackson, *Software Abstractions*, 2. ed., MIT Press, 2012, ISBN 978-0-262-01715-2) — nova CAND-047; alternativa Alloy.
   - **REF-050** `holzmann_spin_2004` (Holzmann, *The SPIN Model Checker*, Addison-Wesley, 2004, ISBN 978-0-321-22862-8) — nova CAND-048; alternativa SPIN/Promela.
2. ✅ `bailis_kingsbury_network_2014` (REF-034) **já existia** no `.bib` — não duplicada; permanece citável para ancorar a menção a Jepsen/abordagens empíricas na subseção T-A.
3. ✅ Nenhum `.tex` editado nesta etapa — a redação da subseção é do Escritor (próximo passo).

### Observações de catalogação
- **REF-048 ≠ REF-017:** REF-017 é Newcombe, "Why Amazon Chose TLA+" (ABZ 2014, LNCS); REF-048 é o artigo CACM 2015 de seis autores. Mesmo tema e autor principal, obras distintas — ambas citáveis.
- **REF-049 (Jackson):** ISBN 978-0-262-01715-2 corresponde à 2. ed. capa dura de 2012; não confundir com a ed. revisada de 2016 (ISBN 978-0-262-52890-0).

### Balanço da biblioteca após Decisão 015
- **37 REFs efetivas** (33 anteriores + REF-047, REF-048, REF-049, REF-050).
- Total de entradas no `bibliografia.bib`: 48 (era 44 + 4).
- Eixo **C** (métodos formais) ganha quatro fontes voltadas à justificativa de escolha de ferramenta.

### Pendências resolvidas
- Lacuna residual "Lamport 2002 (livro TLA+)" — fechada (REF-047).

### Impacto no TCC
- `bibliografia.bib`: +4 entradas (`lamport_specifying_systems_2002`, `newcombe_aws_formal_methods_2015`, `jackson_software_abstractions_2012`, `holzmann_spin_2004`).
- `04-biblioteca-oficial.md`: fichas REF-047..REF-050 adicionadas; lacuna residual Lamport 2002 marcada fechada.
- `05-referencias-candidatas.md`: CAND-010 e CAND-011 marcadas promovidas; CAND-047 e CAND-048 criadas e promovidas.
- Escritor liberado a citar as quatro novas REFs na subseção T-A.

### Agentes envolvidos
Gestor (aprovação da T-A); Bibliotecário (verificação, promoção e registro); Escritor (redação — próximo passo).

## Decisão 016 — Duas fontes de sustentabilidade para o Cap. 1 (Navaux + GreenCache) ✅

### Data
2026-06-23.

### Contexto
Durante o conserto de citações quebradas/sem ano no Cap. 1 (Infinispan/OWASP/Google), o Gestor pediu uma nova fonte de carbon-aware caching para o parágrafo de sustentabilidade da justificativa. A URL JBCS originalmente indicada (`.../jbcs/article/view/2219/2306`) resolveu, na verificação, para o survey *Challenges in HPC* (Navaux et al.), que NÃO é carbon-aware caching. Sob autorização explícita de busca, o Bibliotecário localizou um artigo REAL e revisado por pares de carbon-aware caching (GreenCache). Ambas as entradas foram criadas no `bibliografia.bib` com metadados verificados na fonte (CAND-049 e CAND-050).

### Decisões aprovadas
1. ✅ **Citar AMBAS** as fontes no parágrafo de sustentabilidade, ao lado de REF-039 (Turner) e REF-040 (Morán):
   - **REF-051** `navaux_hpc_challenges_2023` (Navaux; Lorenzon; Serpa, *Challenges in High-Performance Computing*, JBCS 29(1):51-62, 2023, DOI 10.5753/jbcs.2023.2219) — ex-CAND-049. Enquadramento: **survey de HPC / energia**, NÃO carbon-aware caching.
   - **REF-052** `tian_greencache_2026` (Tian; Sun; Ding; Liu, *Cache Your Prompt When It's Green: Carbon-Aware Caching for LLM Serving*, POMACS 10(1):1-28, 2026 / ACM SIGMETRICS 2026, DOI 10.1145/3788087) — ex-CAND-050. Enquadramento: **carbon-aware caching** (exemplo geral de impacto de carbono do caching).
2. ✅ Nenhum `.tex` editado nesta etapa — a inserção dos `\cite` é do Escritor (próximo passo).

### Observações de catalogação e procedência
- **GreenCache:** artigo arXiv (2505.23970) **publicado** em POMACS vol. 10 n. 1 e apresentado na SIGMETRICS 2026; usamos a DOI POMACS (peer-reviewed) como canônica. Metadados confirmados via Crossref + WebFetch (arXiv).
- **Navaux:** mantido por decisão expressa do Gestor como reforço de "energia em HPC", com o enquadramento real (survey de HPC) registrado para evitar que o Escritor o apresente como carbon-aware caching.
- **Procedência da aprovação:** a decisão chegou ao Bibliotecário **via coordenador** (relato de confirmação direta do Gestor). Conforme o protocolo de segurança acadêmica, relatos do coordenador não substituem confirmação do próprio Gestor; o registro foi executado por ser ação de memória reversível (sem `.tex`, sem citação inserida, sem commit) e **fica sujeito a ratificação direta do Gestor** caso haja divergência.

### Balanço da biblioteca após Decisão 016
- **39 REFs efetivas** (37 anteriores + REF-051 + REF-052).
- Total de entradas no `bibliografia.bib`: 50 (era 48 + 2).
- Eixo de contexto geral / sustentabilidade ganha duas fontes (HPC/energia e carbon-aware caching).

### Pendências resolvidas
- **P-026** — fechada (promoção e registro concluídos).

### Impacto no TCC
- `bibliografia.bib`: +2 entradas (`navaux_hpc_challenges_2023`, `tian_greencache_2026`) — já adicionadas no conserto de citações de 2026-06-23, junto da inclusão de `year` em 7 entradas Infinispan/OWASP/Google.
- `04-biblioteca-oficial.md`: fichas REF-051 e REF-052 adicionadas.
- `05-referencias-candidatas.md`: CAND-049 e CAND-050 marcadas promovidas.
- Escritor liberado a inserir `\cite{navaux_hpc_challenges_2023}` e `\cite{tian_greencache_2026}` no parágrafo de sustentabilidade do Cap. 1.

### Agentes envolvidos
Gestor (aprovação, relatada via coordenador); Bibliotecário (verificação, promoção e registro); Escritor (inserção dos `\cite` — próximo passo).

## Decisão 017 — Fontes para a escrita condicional sem transação e para o SplitMix na Metodologia ✅

### Data
2026-10-27.

### Contexto
O Escritor deixou dois `% [FONTE NECESSÁRIA]` em `Metodologia.tex`: (1) a escrita condicional (`replaceWithVersion`) em *cache* não transacional sob falha; (2) o finalizador SplitMix64 usado na derivação das sementes. O Bibliotecário verificou as fontes na origem (P-030). Nenhuma fonte oficial diz literalmente que o Infinispan "não garante a atomicidade" da escrita condicional.

### Decisões aprovadas
1. **CAND-070 → REF-068** `infinispan_hotrod_java_15` e **CAND-071 → REF-069** `infinispan_configuring_15`, **com a reformulação do trecho 1** proposta pelo Bibliotecário. O texto deixa de afirmar "não garante a atomicidade" e passa a se apoiar no aviso ISPN006010, transcrito do log do *cluster*, na recomendação de *caches* transacionais para evitar aplicação parcial no *failover* (REF-068) e na advertência sobre perda ou inconsistência em partições (REF-069). A atribuição ao *grid* fica como regra de análise: a violação "é tratada na análise como efeito do *grid*".
2. **CAND-072 → REF-070** `steele_lea_flood_splitmix_2014`, **com ressalva**. Redação: "função de mistura do gerador SplitMix \cite{...}, na variante usada por java.util.SplittableRandom". O artigo não é citado como fonte das constantes do Mix13.
3. **CAND-073** (código-fonte `Log.java`, ISPN006010) **dispensada**.

### Procedência
Aprovação do Gestor dada diretamente ao coordenador, por pergunta interativa nesta sessão. O coordenador incluiu as três entradas em `bibliografia.bib` (linhas 605-634), no mesmo arranjo de P-027. O Bibliotecário conferiu as entradas no arquivo e fez a promoção e os registros.

### Impacto no TCC
- `bibliografia.bib`: +3 entradas, total de 55 `@` (incluídas pelo coordenador).
- `04-biblioteca-oficial.md`: fichas REF-068..REF-070. As REF-053..REF-067 continuam reservadas para P-027/P-028.
- `05-referencias-candidatas.md`: CAND-070..072 promovidas e CAND-073 dispensada.
- `07-pendencias.md`: P-030 resolvida.
- **Escritor:** aplicar a reformulação do trecho 1 e a redação do SplitMix em `Metodologia.tex` e remover os dois `% [FONTE NECESSÁRIA]`.

### Agentes envolvidos
Gestor (aprovação, dada ao coordenador); coordenador (inclusão no `.bib`); Bibliotecário (verificação, promoção e registro); Escritor (`.tex`, próximo passo).

## Decisão 018 — Fontes da monografia "TCC-II parcial" e correções de P-029 ✅

### Data
2026-10-30.

### Contexto
A reescrita de Metodologia, Desenvolvimento, Conclusões, Apêndice B e parte da Introdução precisa de fontes para controlador autoadaptativo, energia, estatística e pré-registro (P-031). Também estavam pendentes duas correções de P-029 no `.bib` da monografia.

### Decisões aprovadas
1. **Bloco de 9 entradas** (P-031 a) aplicado ao `bibliografia.bib`: CAND-057 → REF-055 `avizienis_taxonomy_2004`; CAND-058 → REF-056 `yuan_simple_testing_2014`; CAND-059 → REF-057 `hoefler_belli_benchmarking_2015`; CAND-060 → REF-058 `efron_tibshirani_bootstrap_1993`; CAND-062 → REF-060 `fan_power_provisioning_2007`; CAND-063 → REF-061 `kansal_vm_power_2010`; CAND-066 → REF-064 `barroso_holzle_energy_proportional_2007`; CAND-064 → REF-062 `nist_sp_800_63b4_2025`; CAND-068 → REF-066 `netem_manpage`.
2. **Sete candidatas novas** com chaves fixadas pelo Gestor (o Escritor cita em paralelo): CAND-074 → REF-071 `kephart_chess_autonomic_2003`; CAND-076 → REF-072 `linux_cgroup_v2`; CAND-077 → REF-073 `nosek_preregistration_2018`; CAND-078 → REF-074 `student_probable_error_1908`; CAND-079 → REF-075 `brown_cai_dasgupta_binomial_2001`; CAND-080 → REF-076 `mann_whitney_1947`; CAND-081 → REF-077 `georges_jvm_statistics_2007`.
3. **Correção da REF-003** (mesma chave `yu_lamport_tla_modelchecking_1999`): Yu, Manolios e Lamport, "Model Checking TLA+ Specifications", CHARME '99, eds. Laurence Pierre e Thomas Kropf, LNCS 1703, Springer, 1999, p. 54-66, DOI 10.1007/3-540-48153-2_6. `@techreport` trocado por `@inproceedings`.
4. **Substituição da NIST:** onde o texto cita `nist_sp_800_63b_2017` (retirada), passa a citar `nist_sp_800_63b4_2025`. A troca nos `.tex` é do Escritor. A entrada antiga fica no `.bib`, marcada como transitória, até o Escritor terminar; depois o Bibliotecário a remove.

### Verificações do Bibliotecário (2026-10-30)
- Metadados das sete novas conferidos no Crossref (sem `mailto`, R-EMAIL) e, onde preciso, no OpenAlex. Sem divergência em relação às fichas CAND. Student 1908: a página final 25 vem da folha de rosto do JSTOR; o Crossref só traz a página inicial. CHARME '99: o Crossref confirma autores, título, LNCS e p. 54-66, mas não lista os editores; estes vieram da verificação do Gestor.
- **Kephart & Chess e MAPE-K:** conferido no texto integral (cópia de curso do Inria). A Figura 2 (p. 44) mostra Monitor, Analyze, Plan e Execute em torno de Knowledge no gerente autônomo. A citação está liberada para o laço MAPE-K. A sigla "MAPE-K" não aparece no artigo, então o texto não deve dizer que os autores a cunharam.
- Ressalvas de uso nas fichas: REF-058 (só se houver *bootstrap* ou permutação), REF-074 (só se houver t de Student), REF-075 e REF-077 (texto não lido; não atribuir recomendações específicas além do resumo).
- `bibtex` com `abntex2-alf` numa cópia, citando todas as 71 entradas: sem aviso, sem erro, sem duplicata.

### Procedência
Aprovação do Gestor por resposta em formulário ao coordenador, nesta sessão, relatada ao Bibliotecário pelo coordenador. Como na Decisão 016, o registro foi executado por ser reversível (backup do `.bib` e das memórias no *scratchpad* da sessão 2b2a391e; nenhum `.tex` editado; sem *commit*) e fica sujeito a ratificação direta do Gestor se houver divergência.

### Balanço
- `bibliografia.bib`: 55 → **71** entradas (+9 do bloco, +7 novas); REF-003 corrigida; `nist_sp_800_63b_2017` transitória.
- REF-053, 054, 059, 063, 065 e 067 seguem reservadas (candidatas do workshop sem uso na monografia).

### Pendências
- **P-031:** resolvida.
- **P-029:** itens (1) e (2) resolvidos; item (3), páginas e DOI do Dynamo e dos CRDTs, continua aberto.

### Impacto no TCC
- `04-biblioteca-oficial.md`: fichas REF-055..058, 060..062, 064, 066 e REF-071..077; REF-003 corrigida; REF-036 marcada como substituída; lacuna MAPE-K fechada.
- `05-referencias-candidatas.md`: 16 candidatas marcadas como promovidas.
- **Escritor:** trocar `nist_sp_800_63b_2017` por `nist_sp_800_63b4_2025` nos `.tex`; usar as chaves acima.

### Agentes envolvidos
Gestor (aprovação, via formulário ao coordenador); coordenador (repasse); Bibliotecário (verificação, inclusão no `.bib`, promoção e registro); Escritor (`.tex`, em paralelo).

### Execução do item 4 da Decisão 018 (2026-10-30, Bibliotecário)
O Escritor trocou todas as citações da NIST de 2017 nos `.tex` ativos. A entrada `nist_sp_800_63b_2017` foi removida do `bibliografia.bib`, que passa a ter **70** entradas. Cruzamento `\cite` × `.bib`: 67 chaves citadas, nenhuma faltante, todas com ficha REF; `bibtex` com `abntex2-alf` numa cópia gera 67 itens, sem aviso. Órfãs pré-existentes e ajustes de uso de fonte ficam em P-033.

## Decisão 019 — Fontes estatísticas da avaliação do controlador, limpeza do `.bib` e NIST sem prazos ✅

### Data
2026-10-30.

### Contexto
Conferência do texto reescrito (P-033). A Seção 3.3.11 de `Metodologia.tex` tinha dois `[FONTE NECESSÁRIA]` (Holm e regra de três), e Efron & Tibshirani estava citado para o teste de permutação por troca de sinal, que o livro não cobre.

### Decisões aprovadas
1. **CAND-082 → REF-078** `holm_sequential_1979` (URL do JSTOR, sem o DOI que não resolve). **CAND-083 → REF-079** `hanley_lippmanhand_zero_1983`. **CAND-084 → REF-081** `fisher_design_experiments_1935` e a alternativa **→ REF-080** `good_permutation_tests_2005`. Para Fisher, a liberação dependia de conferir capítulo e seção. **Conferido**: cap. III, seção 21 "Test of a Wider Hypothesis", p. 50-53 da 1. ed. (2^15 combinações de sinais nos 15 pares de Darwin). Fisher está liberado; citar pela seção.
2. **Remover do `.bib`** `preguica_crdt_encyclopedia_2019` (REF-012) e `salhi_benchmarking_cache_2018` (REF-014), ambas dispensadas desde a Decisão 012. **Manter** `lemos_eradrs_2025`.
3. **NIST SP 800-63B-4:** o texto **não cita** prazos de reautenticação por AAL. Os valores conferidos ficam só na ficha REF-062.

### Procedência
Aprovação do Gestor por formulário direto, relatada ao Bibliotecário pelo coordenador nesta sessão. Registro feito no mesmo arranjo da Decisão 018 (reversível; backup em `scratchpad/bibliografia-antes-decisao019-20261030.bib`, sessão 2b2a391e), sujeito a ratificação direta do Gestor se houver divergência.

### Verificações do Bibliotecário
- `bibtex` com `abntex2-alf` numa cópia, citando as 72 entradas: 72 itens, 0 avisos. O subtítulo de Hanley foi protegido (`{Interpreting}`).
- A pedido do Senior, conferida a citação literal da seção 7.1.1 do *Configuring Infinispan* 15 usada em `Desenvolvimento.tex`:19 (texto na ficha REF-069). Ela sustenta a frase, com a nuance de que a perda vale para escritas feitas na partição menor **entre a divisão e sua detecção** e não ocorre se todas as partições estiverem em DEGRADED.

### Balanço
`bibliografia.bib`: 70 − 2 + 4 = **72** entradas. Fichas REF-078..081 criadas; REF-012 e REF-014 anotadas como removidas do `.bib`.

### Impacto no TCC
- **Escritor**, em `Metodologia.tex` 255-258:
  - trocar Efron por `\cite[seção 21]{fisher_design_experiments_1935}` e/ou `\citep{good_permutation_tests_2005}` no teste por troca de sinal (Efron fica no *bootstrap* sobre blocos);
  - citar `holm_sequential_1979` na correção de Holm;
  - citar `hanley_lippmanhand_zero_1983` na regra de três;
  - remover os dois `[FONTE NECESSÁRIA]`.
- **Escritor**, em `FundamentacaoTeorica.tex`:74: não reintroduzir prazos por AAL.

### Agentes envolvidos
Gestor (aprovação, via formulário ao coordenador); coordenador (repasse); Senior (pedido de conferência da 7.1.1); Bibliotecário (verificação, `.bib`, fichas, registro); Escritor (`.tex`).

### Checagem final do Bibliotecário (2026-10-30, sem nova decisão)
Conferidas todas as citações da monografia. O `.bib` recebeu correções só de metadados (verificados no Crossref/Springer, sem `mailto`), sem mudança de chaves ou de fontes. Isso fecha o item (3) do P-029. As decisões que dependem do Gestor ficaram registradas em P-034 (`07-pendencias.md`).

---

## Decisão 020 — Manual do JGroups 5.x como fonte da saída graciosa (DES-54) ✅

### Data
2026-11-03.

### Contexto
Rodada 2 de fontes do Cap. 4 da versão oficial (`tcc/monografia-atual/`, P-035). DES-54 atribuía ao FD_SOCK2 o motivo de o `podman stop` não exercitar a detecção de falhas. O mecanismo correto é a saída graciosa (LEAVE e nova visão via GMS), e não havia chave JGroups no `.bib`. Relatório: `tcc/monografia-atual/FONTES-RODADA2-2026-11-03.md`.

### Decisões aprovadas
1. **CAND-085 → REF-082** `jgroups_manual_5` (Ban, Bela. *Reliable group communication with JGroups*, manual 5.x, http://www.jgroups.org/manual5/index.html, acesso em 3 nov. 2026).
2. **CAND-086** (Alfatafta et al., OSDI 2020) fica de fora: é opcional e não foi pedida.

### Procedência
Aprovação do autor/Gestor, relatada ao Bibliotecário pelo coordenador em 03/11/2026, sujeita a ratificação direta se houver divergência. Backup do `.bib`: `scratchpad/bibliografia-antes-decisao020-20261103.bib` (sessão 041075e7).

### Verificações do Bibliotecário
- Só metadados conferidos na própria página: título, autor, série 5.x, URL e data de acesso. Formato `@misc` igual ao das entradas Infinispan.
- `bibtex` com `abntex2-alf` numa cópia: a entrada sai como "BAN, Bela. 2026. *Reliable group communication with JGroups: JGroups Manual, version 5.x*. Website. Acesso em: 3 nov. 2026."; nenhum erro nas entradas do projeto.

### Balanço
`monografia-atual/bibliografia.bib`: 84 + 1 = **85** entradas.

### Impacto no TCC
- **Escritor:** aplicar em DES-54 a versão **com citação** do relatório (`\cite[seções 3.10.13 e 6.8.7]{jgroups_manual_5}`), além das correções de DES-55 e do comentário de DES-43.

### Agentes envolvidos
Gestor (aprovação); coordenador (repasse); Bibliotecário (verificação, `.bib`, registro); Escritor (`.tex`).



---

## Decisão 021 — Aval geral para fontes relevantes: escala F4, Infinispan 15.0, IUT, energia e detecção antecipada ✅

### Data
2026-11-04.

### Contexto
Duas tarefas repassadas pelo coordenador: (1) incluir CAND-087..089 (P-036), os guias Infinispan 15.0 de servidor, REST e CLI, Berger 1982, Barroso e Hölzle 2007 e a ficha do i5-14600KF; (2) pesquisa nova sobre detecção antecipada de partição, falha cinzenta e defasagem de replicação, antes que escritas no lado minoritário se percam na fusão.

### Decisão aprovada
Aval geral do autor em 04/11/2026: **"entra tudo que for relevante de verdade"**, no lugar da aprovação item a item **só nesta rodada**. Com base nele, o Bibliotecário promoveu CAND-086..100 a REF-083..097 (tabela em `04-biblioteca-oficial.md`) e deixou CAND-101 (Alquraan 2018) de fora por redundância. Barroso e Hölzle 2007 já estava no `.bib` (REF-064) e não foi duplicado.

### Procedência
Aval do autor relatado ao Bibliotecário pelo coordenador em 04/11/2026, no mesmo arranjo das Decisões 019 e 020, sujeito a ratificação direta se houver divergência. Backup do `.bib`: `scratchpad/bibliografia-antes-decisao021-20261104.bib` (sessão 0786b06c).

### Verificações do Bibliotecário
- Metadados conferidos no Crossref e no OpenAlex, sem `mailto` (R-EMAIL); páginas USENIX conferidas no rodapé dos PDFs. Não há DOI para os artigos USENIX e CIDR.
- Berger 1982: texto integral não obtido; conteúdo conferido em fonte secundária do próprio autor (Casella e Berger 1999). Citar sem página.
- Guias Infinispan: não existe guia fixado em 15.0.21; a página da série 15.0.x mostrava 15.0.22.Final em 04/11. As entradas seguem o padrão `(Infinispan 15.0)` de `infinispan_configuring_15`.
- `bibtex` com `abntex2-alf` numa cópia, citando todas as entradas: 100 itens, 0 avisos.

### Balanço
`monografia-atual/bibliografia.bib`: 85 + 15 = **100** entradas.

### Impacto no TCC
- **Escritor (P-036):** citar `paxson_packet_dynamics_1997`, `guo_pingmesh_2015` e `tan_netbouncer_2019` em DES-55 como faixa medida, sem dizer que dão os valores exatos de N1..N5.
- **Escritor (P-037):**
  - `infinispan_server_15` §12.2.2 em `Desenvolvimento.tex`:73/:97 (TCPPING);
  - `infinispan_rest_15` §2.4.2 em :73 e :143 (saúde);
  - `berger_iut_1982` em `Metodologia.tex`:229;
  - `intel_i5_14600kf` em `Metodologia.tex`:335. A ficha dá 125 W (potência base) e 181 W (potência turbo máxima) e nenhum valor ocioso. O autor decide qual é o $P_\text{máx}$.
  - Figura `fig-datagrid-repl` (`FundamentacaoTeorica.tex`:49): a figura do Data Grid 7.0 (com índices) **não existe** nos guias 15.0, então a chave da figura não pode ser trocada sem trocar a imagem. O texto do parágrafo (cópia completa por nó, leitura local) é sustentado por `\cite[seção 2.1]{infinispan_configuring_15}`. Se a imagem for trocada pela Figura 1 dessa seção (`replicated_cache_ispn.png`), a fonte passa a ser a mesma chave.
- **Senior e Escritor:** síntese da pesquisa de detecção antecipada no relatório desta rodada. Ressalva para o desenho: o `set-availability` do REST/CLI vale por *cache*, e os guias não dizem que atua num nó só. Forçar DEGRADED_MODE antes da troca de visão pode degradar o *cluster* inteiro. Isso precisa ser verificado em experimento antes de ser escrito como ação "no nó suspeito".

### Agentes envolvidos
Autor/Gestor (aval geral); coordenador (repasse); Bibliotecário (pesquisa, verificação, `.bib`, registro); Escritor (`.tex`).
