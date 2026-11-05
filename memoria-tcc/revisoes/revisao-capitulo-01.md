# Revisões — Capítulo 1 (Introdução)

> Registro das revisões do **Revisor Acadêmico** sobre o Cap. 1. Ver controle do capítulo em [`../capitulos/capitulo-01-introducao.md`](../capitulos/capitulo-01-introducao.md) e o LaTeX em `Main-Monografia.tex`.

## Histórico de revisões

| Rodada | Data | Versão revisada | Resultado |
|---|---|---|---|
| 1 | 2026-05-11 | v0.1 (em `Main-Monografia.tex`, `\chapter{Introdução}`) | **Aprovada estruturalmente** com ajustes obrigatórios pendentes (abaixo). Encaminhada ao Gestor para aprovação. |
| R4 | 2026-10-30 | Monografia inteira (resumo/abstract, Cap. 1 a 5, Apêndices A e B), após backup `*-r3-20261030.tex` | **Aprovada com ressalvas.** Passada final de linguagem (registro único para todos os capítulos, anotado aqui). Edições: ponto e vírgula encadeado convertido em frases (~40 casos); "não-bloqueante/não-decrescente/não-empírico/auto-gestão/pré-definidos" corrigidos; grafia `\emph{cache}` uniformizada; anglicismos trocados (key-value stores, datacenters, multisite, sítios/sites); SLO expandido no resumo/abstract; CRDT, sloppy quorum, hinted handoff, vector clocks e baseline definidos na 1a ocorrência; jargão de repositório (ramo, ramo de integração, portão) reescrito; "duas frentes" da detecção de violações trocado por "dois caminhos" (colisão com Frente 1/2); referências cruzadas acrescentadas para O1-O7, F4 e I1-I6 antes de sua definição; "três tipos de resultado" (Cap. 5) removido; plural de citação única corrigido (Dynamo, Nair). Pendências de conteúdo listadas no relatório ao coordenador: "cinco" modos do Infinispan x seis listados; M1-M7 (T19) sem definição; dois exemplos distintos de Dean e Barroso (Cap. 1 x Cap. 2); C(p) não definido na fórmula de Lamport; menções a "versão anterior deste texto" (Cap. 4); "referências oficiais" ambíguo (Cap. 5). Sem compilação (sem Bash). |

---

## Revisão do trecho — Cap. 1, versão 0.1

### 1. Problemas encontrados
- **Justificativa incompleta por restrição de fontes.** As afirmações sobre (a) ocorrência de defeitos de consistência sob partição/*failover* em sistemas reais, (b) aplicação industrial de métodos formais a protocolos de replicação e (c) o posicionamento frente a trabalhos relacionados estão **sem citação**, marcadas com `% [FONTE NECESSÁRIA: ...]`. São pendências legítimas (Decisão 004 suspendeu buscas), mas **não podem permanecer assim na versão final** — bloqueiam o fechamento do capítulo (P-002).
- **Seção 1.1 quase transposta da Proposta.** O texto reaproveita, com pouca expansão, os parágrafos da Introdução de `Main-PPEC.tex`. É aceitável reusar o próprio projeto, mas numa monografia espera-se **aprofundamento** (mais detalhe sobre o ciclo de vida de sessão, sobre IMDGs, sobre os modos de replicação) — não apenas a transposição.
- **Falta um parágrafo de delimitação em 1.2.** O recorte (quais operações, quais falhas, quais invariantes mínimos) aparece disperso; convém um parágrafo de fechamento em 1.2 ou remissão explícita ao Cap. 3 (relaciona-se a P-016).
- **Referências cruzadas a capítulos ainda não escritos** (`\ref{cap:fundamentacao}` etc.) em 1.5 — gerarão "Capítulo ??" até a compilação completa; aceitável no esqueleto.
- **Resumo e *abstract* preliminares** — coerentes com o objetivo, mas precisam ser reescritos ao final, com os resultados (P-019).
- **Textura das citações monótona** — todas as citações são `\cite{...}` ao fim da frase (herança do estilo da Proposta); pouca variação com `\citeonline`.

### 2. Sugestões de melhoria
- Expandir 1.1: (i) caracterizar o ciclo de vida de sessão (criação, validação, renovação, invalidação, bloqueio por tentativas); (ii) detalhar particionamento/replicação/*rebalancing*/*state transfer* em IMDGs; (iii) explicitar por que o estado de sessão é um caso especialmente sensível à defasagem entre réplicas. (Boa parte depende de novas referências — eixos A, B, H de [`../05-referencias-candidatas.md`](../05-referencias-candidatas.md).)
- Em 1.3, assim que a busca for autorizada (P-015): substituir os três marcadores por citações reais (CAND-007 *The Network is Reliable*; CAND-011 *AWS Formal Methods*; CAND-018 *Jepsen*) e acrescentar 1–2 parágrafos de posicionamento, ou remeter formalmente à seção de trabalhos relacionados (2.9).
- Acrescentar parágrafo de delimitação ao fim de 1.2 (ou subseção 1.2.1 "Delimitação do problema").
- Alternar `\citeonline{...}` (autor como sujeito: "Conforme \citeonline{decandia_dynamo_2007}, ...") com `\cite{...}` (entre parênteses ao fim) para variar a leitura.
- Revisar 1.5 quando a estrutura final for aprovada (P-008).

### 3. Ajustes obrigatórios (para o Escritor aplicar na v0.2)
1. **Manter** os marcadores `% [FONTE NECESSÁRIA: ...]` até a Decisão do Gestor sobre busca — **não removê-los nem inventar citação** para tapá-los (protocolo de segurança acadêmica).
2. Confirmar que `bibliografia.bib` contém todas as 9 chaves citadas — ✅ verificado nesta rodada (`infinispan_docs_stable`, `infinispan_xsite`, `decandia_dynamo_2007`, `shapiro_crdt_2011`, `yu_lamport_tla_modelchecking_1999`, `dean_barroso_tail_2013`, `google_sre_slo` — todas presentes; `infinispan_metrics` e `infinispan_intro` ainda não citadas no Cap. 1, ok).
3. Atualizar a seção 1.5 (Organização do texto) quando a estrutura de capítulos for aprovada (P-008).
4. Acrescentar a delimitação do problema em 1.2 (P-016).
5. **Não considerar o capítulo pronto sem aprovação do Gestor** (§16 do Prompt Mestre).

### 4. Versão revisada em LaTeX
Nesta 1ª rodada, a versão revisada **coincide com a v0.1 já escrita** em `Main-Monografia.tex` — os apontamentos acima são, em sua maioria, (i) dependentes de fontes ainda não autorizadas ou (ii) dependentes de decisões do Gestor (estrutura), não correções pontuais de redação. A v0.2 (com a Justificativa fechada, 1.1 expandida e 1.5 atualizada) será produzida após a autorização de busca e a aprovação da estrutura. O Revisor reexaminará a v0.2 integralmente.
