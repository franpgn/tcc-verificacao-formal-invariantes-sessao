# Planejamento de TCC-I — alinhado ao Quadro 1 da Proposta atualizada

> Fonte: [`Main-PPEC.tex`](../Main-PPEC.tex) — Quadro 1 (Cronograma de TCC-I), 12 semanas em 6 etapas quinzenais. Versão sincronizada do `Main-PPEC.tex` em 2026-05-12 (Decisão 010).
>
> Metodologia: **mapeamento sistemático** (REF-037 Petersen 2008) + relato PRISMA (REF-038) + guia prático em PT (REF-030 Neiva & Silva 2016) → Decisão 010.
>
> Estrutura da monografia (6 capítulos — [`03-estrutura-capitulos.md`](03-estrutura-capitulos.md)) é a da monografia **completa**; em TCC-I redige-se principalmente Caps. 1, 2 e 3 + apêndice de protocolo, **com escrita em paralelo a todas as 12 semanas**.

---

## Visão geral das 6 etapas (Quadro 1)

| Sem | Atividade (do Quadro 1) | Entregável proposto | Alimenta no texto | Reuso de artefatos prévios |
|---|---|---|---|---|
| **1-2** | Pergunta de pesquisa + protocolo do mapeamento sistemático (bases, strings, critérios, esquema de extração) | `14-protocolo-mapeamento-tcc1.md` + parágrafo final de Cap. 1.2 + esboço Cap. 3.1-3.2 | Cap. 1.2, Cap. 3.1, apêndice "Protocolo do MS" | reutiliza [`02-metodologia.md`](02-metodologia.md) (já reescrito para MS) |
| **3-4** | Executar busca; triagem por título/resumo; estabilizar critérios I/E | `15-execucao-busca-tcc1.md` (registros por base + fluxograma PRISMA inicial) + atualizar [`05-referencias-candidatas.md`](05-referencias-candidatas.md) com novos itens | Cap. 3 (relato de busca); apêndice (string final por base) | reutiliza pool já curado em [`/workspace/referencias-gestor/`](../referencias-gestor) como **conjunto inicial** (não como universo total — o protocolo do Sem 1-2 deve incluir nova rodada de busca contemporânea) |
| **5-6** | Seleção por leitura completa; extração; classificação; síntese; **recorte do protótipo** | `16-sintese-mapeamento-tcc1.md` (esquema de classificação preenchido + síntese descritiva) + atualizar [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md) (recorte refinado pelos achados) | Cap. 2 (Fundamentação derivada da síntese), Cap. 3 (esquema de classificação); apêndice (tabela de extração) | conteúdo de [`12-revisao-dirigida-tcc1-sem3-4.md`](12-revisao-dirigida-tcc1-sem3-4.md) (obsoleto como entregável) pode informar parte da síntese; recorte O1-O7 / F1-F4 / I1-I6 / M1-M7 de [`11-`](11-recorte-criterios-tcc1.md) é o **rascunho inicial** a refinar |
| **7-8** | Ambiente experimental (Infinispan + observabilidade mínima); *workload* + checagens de invariantes; **modelo formal inicial** (transições + canal assíncrono) | `17-ambiente-experimental-tcc1.md` + repositório de *workload*/scripts + `18-modelo-formal-inicial-tcc1.md` (spec TLA+ ou equivalente, invariantes I1-I6 traduzidos) | Cap. 3 (ambiente, workload); Cap. 4 (modelo formal, versão inicial) | reutiliza recorte de Sem 5-6; recorte de [`11-`](11-recorte-criterios-tcc1.md) (O1-O7, F1-F4) traduz para a spec; REF-019 (Enea/Must) e REF-016 (FLP) e REF-003 (TLC) embasam a escolha da ferramenta (P-017) |
| **9-10** | *Model checking* inicial (rodar TLC sobre invariantes) + baseline experimental (sem falhas e com falhas selecionadas) | `19-mc-inicial-resultados-tcc1.md` (logs do TLC, contraexemplos iniciais, propostas de refinamento) + `20-baseline-experimental-tcc1.md` (dataset preliminar p95/p99, taxa de erro, taxa de violação) | Cap. 4 (resultados do MC inicial); Cap. 5 (resultados parciais do baseline) | recorte refinado + spec inicial; falhas: **F1 (crash) e F3 (latência/jitter) em TCC-I; F2 (partição) e F4 (state transfer) reservados para TCC-II** (Decisão 011 ponto 11) |
| **11-12** | Consolidar resultados parciais; ajustar recorte; **finalizar a escrita** (metodologia, resultados parciais, planejamento das próximas etapas — Quadro 2 como apêndice) | `Main-Monografia.tex` v1.0 (TCC-I entregável) com Caps. 1, 2, 3 completos + Cap. 4 (modelo formal inicial) + Cap. 5 (resultados parciais) + Apêndice A (protocolo MS) + Apêndice B (plano TCC-II = Quadro 2) | toda a monografia | toda a memória; cap. 1 v0.1 atualizado p/ v1.0 |

> **Escrita da monografia em paralelo:** o Quadro 1 marca "Escrita da monografia" como atividade contínua nas 6 etapas. Isto significa que cada etapa **alimenta seu trecho do texto imediatamente** (não acumula para Sem 11-12). A Sem 11-12 é só **consolidação final e revisão**.

---

## Detalhamento por etapa

### Etapa 1 (Sem 1-2) — Pergunta + Protocolo do MS

**Atividades (Quadro 1):**
1. Definir pergunta de pesquisa e escopo (raiz já está em [`Main-PPEC.tex`](../Main-PPEC.tex) linha 49 — refinar).
2. Protocolo do mapeamento sistemático (bases, strings, critérios I/E).

**Entregáveis:**
- **`14-protocolo-mapeamento-tcc1.md`** — documento estruturado com: (a) questão central + sub-questões de mapeamento; (b) bases (ACM, IEEE, Scopus, SpringerLink); (c) strings refinadas por base; (d) critérios I/E; (e) esquema de classificação (D1-D7 esboçados em [`02-metodologia.md`](02-metodologia.md)); (f) procedimento de triagem; (g) registro/instrumentação (planilha + Mendeley/JabRef).
- **Esboço de Cap. 1.2 e Cap. 3.1-3.2** no [`Main-Monografia.tex`](../Main-Monografia.tex).
- **Esboço de apêndice "Protocolo do MS"**.

**Insumos:**
- Pergunta de pesquisa atual: [`Main-PPEC.tex`](../Main-PPEC.tex) linha 49.
- Metodologia já reescrita: [`02-metodologia.md`](02-metodologia.md).
- Recortes-piloto: [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md) (invariantes I1-I6 ajudam a calibrar as questões de mapeamento).
- Guias: REF-030 (Neiva & Silva 2016 — em português), REF-037 (Petersen 2008), REF-038 (PRISMA 2020).

**Decisões resolvidas (Decisão 011, 2026-05-13):**
- **P-004b:** escopo do MS = **todos os 8 eixos (A-H)** — não os 4 centrais (Decisão 011 ponto 3).
- **P-017** (ferramenta MC) — decidida em Sem 7-8 (Decisão 011 ponto 9).

### Etapa 2 (Sem 3-4) — Busca + Triagem

**Atividades (Quadro 1):**
1. Executar busca (string aprovada por base, registrando data e quantidade).
2. Triagem por título e resumo.
3. Estabilizar critérios I/E.

**Entregáveis:**
- **`15-execucao-busca-tcc1.md`** com: por base — string, data, n itens retornados; após deduplicação; após triagem por título/resumo. Já no formato compatível com **fluxograma PRISMA**.
- **Atualização de [`05-referencias-candidatas.md`](05-referencias-candidatas.md)** com novos itens (CAND-047..) — só os que passarem na triagem.
- **Sub-seção Cap. 3.3 e início do Apêndice A** atualizados em [`Main-Monografia.tex`](../Main-Monografia.tex).

**Insumos:**
- Pool inicial já curado em [`/workspace/referencias-gestor/`](../referencias-gestor) (5 CSVs Springer + 7 ACM + 3 LNCS) — usa-se como **conjunto-semente para verificar se o protocolo captura os clássicos**, **não substitui a busca formal**.
- REF-001..REF-036 já aprovadas têm prioridade de inclusão na análise (referências-âncora).

### Etapa 3 (Sem 5-6) — Leitura, Extração, Síntese, Recorte

**Atividades (Quadro 1):**
1. Seleção por leitura completa dos itens que passaram da triagem.
2. Extração e classificação conforme esquema D1-D7.
3. Síntese descritiva.
4. **Definir o recorte implementável do protótipo** (operações, falhas, invariantes, métricas) **com base nos achados**.

**Entregáveis:**
- **`16-sintese-mapeamento-tcc1.md`** com tabela de extração preenchida e síntese descritiva por dimensão.
- **Atualização de [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md)**: o recorte preliminar (O1-O7, F1-F4, I1-I6, M1-M7) é **revisitado** à luz dos achados — refinar/justificar/eventualmente ajustar conjunto de invariantes (a aprovação dos I1-I6 pela Decisão 007 permanece válida, mas o Gestor pode optar por refinar a redação ou acrescentar/remover invariantes em função do que o mapeamento mostrar).
- **Cap. 2 (Fundamentação Teórica) escrito** — seções organizadas em torno do esquema de classificação (D1-D7) e justificando o recorte.
- **Cap. 3 (Metodologia)** com tabela de extração no apêndice.

### Etapa 4 (Sem 7-8) — Ambiente + Workload + Modelo formal inicial

**Atividades (Quadro 1):**
1. Preparar ambiente experimental com Infinispan e observabilidade mínima.
2. Implementar *workload* e checagens de invariantes.
3. Modelo formal com transições e canal assíncrono (**versão inicial**, não exaustiva).

**Entregáveis:**
- **`17-ambiente-experimental-tcc1.md`**: documentação do *cluster* Infinispan (modo de execução, configuração de cache, número de nós, observabilidade — REF-008/Infinispan Metrics); *scripts* de inicialização/encerramento.
- **Repositório de *workload*/scripts** (a definir local — possivelmente sub-diretório `/workspace/protótipo/`).
- **`18-modelo-formal-inicial-tcc1.md`**: especificação inicial em TLA+ (ou alternativa — P-017 decidida nesta etapa) com operações O1-O7 e invariantes I1-I6 traduzidas; canal assíncrono abstrato; ainda **sem** falhas explícitas (entra em TCC-II Sem 3-4).
- **Cap. 4 (Desenvolvimento) — versão preliminar** com a especificação documentada.

**Decisão a tomar nesta etapa:**
- **P-017** — escolha definitiva entre TLC/TLA+ (REF-003 + REF-017 + REF-029 candidata), Alloy (CAND-?) ou Must (REF-019).

### Etapa 5 (Sem 9-10) — MC inicial + Baseline experimental

**Atividades (Quadro 1):**
1. Executar *model checking* inicial sobre a spec da Sem 7-8.
2. Rodar baseline experimental sem falhas e com falhas selecionadas — coletar p95/p99, taxa de erro.

**Entregáveis:**
- **`19-mc-inicial-resultados-tcc1.md`**: log do TLC, contraexemplos encontrados (se houver), tempo de exploração, espaço de estados coberto, propostas de refinamento para TCC-II.
- **`20-baseline-experimental-tcc1.md`**: dataset preliminar — para cada cenário (sem falha; F1 *crash*; F3 latência/jitter), distribuição de latência (boxplots, p50/p95/p99), taxa de violações de cada invariante (M2), taxa de erro (M5). Inclui análise crítica dos resultados parciais.
- **Cap. 4 (Desenvolvimento)** — adicionar resultados do MC.
- **Cap. 5 (Resultados parciais)** — primeiros dados; deixar claro o que é "parcial" (resultados completos com F2 partição e F4 state transfer ficam para TCC-II Sem 5-6).

### Etapa 6 (Sem 11-12) — Consolidação + Escrita final

**Atividades (Quadro 1):**
1. Consolidar resultados parciais (ler em conjunto MC + experimentos; identificar padrões).
2. Ajustar o recorte conforme evidências (eventualmente refinar invariantes ou cenários para TCC-II).
3. **Finalizar a escrita do trabalho** com metodologia, resultados parciais e planejamento das próximas etapas (= Quadro 2 da Proposta como **Apêndice B — Plano de Trabalho para TCC-II**).

**Entregáveis:**
- **`Main-Monografia.tex` v1.0 (TCC-I entregável)**:
  - Cap. 1 Introdução (incluir sustentabilidade computacional);
  - Cap. 2 Fundamentação Teórica (derivada do mapeamento);
  - Cap. 3 Metodologia (mapeamento + frente experimental);
  - Cap. 4 Desenvolvimento (modelo formal inicial + ambiente + workload);
  - Cap. 5 Resultados Parciais (MC inicial + baseline);
  - Cap. 6 Conclusões Parciais (sumário, limitações conhecidas, *next steps*);
  - Apêndice A — Protocolo do MS;
  - Apêndice B — Plano de trabalho para TCC-II (= Quadro 2 ampliado);
  - Referências.
- **Pré-textuais** preenchidos (P-019 fechada).
- **Defesa de TCC-I** preparada (slides + script).

---

## Mapeamento das pendências atuais ao novo cronograma

| Pendência | Resolução prevista |
|---|---|
| P-001 (hipótese vs questão) | Sem 1-2 (decisão do Gestor ao revisar a pergunta de pesquisa) |
| P-002 (Justificativa) | Sem 5-6 (fechada com achados do mapeamento) |
| P-003 (fora de escopo) | Sem 5-6 (refinado após recorte) |
| P-005 (referências metodológicas) | ✅ Decisão 010 — REF-030/037/038 cobrem |
| P-006 (limitações) | Sem 11-12 |
| P-007 (ameaças à validade) | Sem 11-12 (apoio em CAND-020 se autorizado) |
| P-008 (estrutura capítulos) | Sem 5-6 (decisão final do Gestor; até lá usar estrutura corrente) |
| P-009 (Cap. 2) | Sem 5-6 |
| P-010 (trabalhos relacionados) | Sem 5-6 |
| P-011 (plano estatístico) | Sem 9-10 (com base na coleta piloto) |
| P-014 (confirmar 9+5 herdadas) | aberta — confirmação do Gestor (Cap. 2 já as usa) |
| P-016b (unicidade de sessão ativa) | Sem 7-8 (decisão do Gestor ao montar a spec) |
| P-017 (TLA+/Alloy/Must) | Sem 7-8 |
| P-018 (cronograma na monografia) | Sem 11-12 (Apêndice B = Quadro 2) |
| P-019 (pré-textuais) | Sem 11-12 |
| P-021 (chaos engineering/Jepsen) | Sem 3-4 (se a busca do MS capturar; senão, fica como TCC-II) |

---

## Riscos e mitigações

| Risco | Mitigação |
|---|---|
| MS extenso atrasa Sem 7-8 | Limitar a 4 eixos centrais (sessão + replicação async + falhas + MC); não cobrir controle adaptativo em TCC-I |
| Modelo formal complexo demais para Sem 7-8 | Spec **inicial** sem falhas; falhas são TCC-II. Aceitar simplicidade |
| Ambiente Infinispan instável | Usar versão estável (REF-006); reduzir cluster a 3 nós em TCC-I |
| Dataset baseline ruidoso | Limitar falhas a F1 (crash) e F3 (latência) em TCC-I; F2 e F4 ficam para TCC-II |
| Buscas em ACM bloqueadas (vimos com WebFetch) | Usar Crossref para metadata; download manual via biblioteca da Unipampa/CAPES quando necessário |

---

## Validações de coerência (Revisor — 1ª rodada)

- ✅ Cada atividade do Quadro 1 tem entregável associado.
- ✅ Escrita da monografia é alocada em paralelo conforme o Quadro 1.
- ✅ Reuso explícito de artefatos prévios (11- e 12-) — sem "começar do zero".
- ✅ TCC-II preview entra como Apêndice B (Quadro 2).
- ⚠️ Etapa 4 (Sem 7-8) é a mais carregada (3 entregáveis grandes). Aceitar e priorizar: ambiente > workload > modelo formal — nessa ordem se houver atraso.
- ⚠️ A spec formal inicial de Sem 7-8 **não inclui falhas** — o Gestor precisa concordar que isso é suficiente para TCC-I; falhas no modelo formal são TCC-II (Sem 3-4 de TCC-II).

**Status do planejamento:** ✅ **Aprovado pelo Gestor em 2026-05-13** (Decisão 011, 16 pontos em 4 lotes). Parâmetros consolidados:

- Escopo MS: **todos os 8 eixos (A-H)** — Decisão 011 ponto 3 (mais amplo que a recomendação do Escritor; risco de carga em Sem 3-6 aceito);
- Pergunta de pesquisa: **usar tal qual** o `Main-PPEC.tex` linha 49 (sem refinamento);
- Pool curado como **conjunto-semente para calibração** do protocolo;
- Estrutura: **5 capítulos** (Cap. 4 fundido — Desenvolvimento e Resultados Parciais);
- Jepsen: **inclusão restrita** (literatura cinza);
- Recorte I1-I6: **aberto a refinamento** em Sem 5-6;
- Ferramenta MC: **decisão em Sem 7-8**;
- Spec inicial Sem 7-8: **sem falhas**;
- Baseline Sem 9-10: **F1 + F3** (F2 + F4 para TCC-II);
- I7 unicidade de sessão: **não incluir**;
- Apêndice B (Plano TCC-II): **Quadro 2 + narrativa expandida**;
- 14 herdadas (REF-001..009, REF-037..041): **confirmadas oficiais**;
- Defesa TCC-I: **entregável de Sem 11-12** (slides + script);
- Diretório protótipo: **`/workspace/prototipo/`** (a criar em Sem 7-8).

**Próximo passo:** Sem 1-2 — pergunta de pesquisa + protocolo do mapeamento sistemático cobrindo os 8 eixos. Entregável `14-protocolo-mapeamento-tcc1.md`.