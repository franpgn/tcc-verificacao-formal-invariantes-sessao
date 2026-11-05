# Relatório de revisão de escrita do TCC (2026-06-16)

> Varredura completa conduzida pelos agentes Revisor, Bibliotecário e Escritor sobre os oito arquivos `.tex` da monografia. Cobre cruzamento `\cite` × `bibliografia.bib`, cruzamento `\ref` × `\label`, escrita humana, escrita técnica, conciseness e consistência terminológica. Inclui as correções aplicadas e as recomendações que dependem de decisão do Gestor.

## 1. Cruzamento bibliográfico (Bibliotecário)

### 1.1. Citações vs entradas na `.bib`

- ✅ **43 chaves distintas citadas** em todos os capítulos.
- ✅ **Todas as 43 chaves têm entrada correspondente em `bibliografia.bib`**: zero erro de compilação esperado em `bibtex`.
- ⚠️ **3 entradas na `.bib` não citadas no texto**, mantidas por motivos documentados:

| Chave | Status | Razão |
|---|---|---|
| `lemos_eradrs_2025` | mantida | REF-041; fonte conceitual disponível por Decisão 011 ponto 14; sem uso direto até esta etapa |
| `preguica_crdt_encyclopedia_2019` | mantida | REF-012; **dispensada** por Decisão 012; permanece como registro histórico |
| `salhi_benchmarking_cache_2018` | mantida | REF-014; **dispensada** por Decisão 012; permanece como registro histórico |

**Recomendação:** manter as três entradas; nenhuma é citada, portanto nenhuma aparece na lista de referências final do PDF. Se o Gestor preferir uma `.bib` enxuta, remover as dispensadas (REF-012 e REF-014) é seguro.

### 1.2. Referências cruzadas internas

- ✅ **28 `\ref{}` usados**, todos resolvem a um `\label{}` declarado: zero `??` esperado no PDF.
- ⚠️ **30 `\label{}` declarados mas não referenciados** diretamente em `\ref{}`. Comportamento esperado: cada `\chapter`, `\section` e `\subsection` recebe `\label` por padrão como infraestrutura para futura referência ou para hyperlinks na ToC; não exige ação.

## 2. Correções aplicadas nesta passagem

| ID | Localização | Tipo | Antes | Depois |
|---|---|---|---|---|
| G-02 | `Metodologia.tex` linha 8 | tipográfico | "Trabalho de Conclusão de Curso I e Trabalho de Conclusão de Curso II" (sem `~`) | "Trabalho de Conclusão de Curso~I e Trabalho de Conclusão de Curso~II" (com *non-breaking space*) |
| G-05 | `Desenvolvimento.tex` §4.2 | **inconsistência técnica crítica** | "a expectativa para a etapa atual é confirmar que o modelo … satisfaz I1 a I4. Os invariantes I5 e I6 … ficam reservados para [TCC-II]" | "Os invariantes I1 a I6 são declarados como fórmulas … com I1 a I4 caracterizadas como propriedades de segurança incondicionais e I5 e I6 como propriedades de convergência condicionadas ao predicado de quiescência … a execução do verificador e a leitura dos contraexemplos produzidos integram a Seção 4.3.1" |
| G-06 | `Desenvolvimento.tex` §4.4 | adjetivo subjetivo | "salvaguarda passiva para o escopo de TCC-I" | "configuração que define o comportamento padrão diante de partição involuntária no escopo de TCC-I" |
| G-07 | `Desenvolvimento.tex` cabeçalho | comentário desatualizado | "Resultados de Sem 9-10 ficam como plano de execução (a serem rodados após a defesa do TCC-I…)" | "Inclui Sem 7-8 (ambiente experimental, workload, modelo formal inicial) e Sem 9-10 (model checking executado em cinco rodadas; baseline experimental reposicionado por restrição de ambiente)" |
| G-10 | `Desenvolvimento.tex` §4.4 segunda frente | legibilidade | uma frase de ~245 palavras com sete `;` separando componentes | `\begin{itemize}` com sete itens nomeados |
| G-03 | `ConclusoesParciais.tex` §5.2 limitação 3 | conciseness | uma frase de ~87 palavras com `;` interno e várias subordinadas | três frases curtas + número absoluto explicitado ("suíte de 34 testes JUnit") |

A inconsistência G-05 era a mais crítica: §4.2 dizia que a verificação ainda não havia rodado e que I5/I6 ficavam para TCC-II, enquanto §4.3.1 (logo a seguir) descreve as cinco rodadas já executadas com contraexemplos para I1, I2, I5 e I6. As duas seções agora são coerentes entre si.

## 3. Verificações automáticas pós-correção

| Verificação | Resultado |
|---|---|
| `\cite*` sem entrada na `.bib` | 0 |
| `\ref` sem `\label` correspondente | 0 |
| Ocorrências de `---` em texto visível | 0 |
| Ocorrências de "raramente" | 0 (em todos os 8 `.tex`) |
| Ocorrências de "baixa incidência" | 0 |
| Adjetivos da lista negra ("muito", "bastante", "altamente", "realmente", "claramente", "simplesmente", "basicamente", "efetivamente", "extremamente", "certamente") | 0 |
| Referência fabricada `hormann_rejection_inversion_1996` | 0 (removida na sessão anterior) |
| "expectativa para a etapa atual" (frase obsoleta) | 0 |
| "salvaguarda passiva" (adjetivo subjetivo) | 0 |
| `\date{Junho}{2026}` em pré-textuais | 1 |
| `\defesa{30}{Junho}{2026}` em pré-textuais | 1 |
| Contagem "trinta e uma referências" usada consistentemente em §4.5 e §5.2 | ✅ |

## 4. Linhas finais por arquivo

| Arquivo | Linhas |
|---|---|
| `Main-Monografia.tex` | 146 |
| `Introducao.tex` | 59 |
| `FundamentacaoTeorica.tex` | 75 |
| `Metodologia.tex` | 248 |
| `Desenvolvimento.tex` | 73 (era 64; +9 pelo `itemize`) |
| `ConclusoesParciais.tex` | 24 |
| `ApProtocoloMS.tex` | 104 |
| `ApPlanoTCC2.tex` | 34 |
| **Total** | **763 linhas .tex** |

## 5. Recomendações que dependem de decisão do Gestor

### 5.1. G-01 — padronização "TCC-I/TCC-II"

A monografia mistura duas formas: "Trabalho de Conclusão de Curso~I" (forma expandida, ~28 ocorrências) e "TCC-I" (forma abreviada, ~8 ocorrências). A sigla `TCC` está na lista de abreviaturas do `Main-Monografia.tex`. Duas saídas:

- **Saída A (recomendada):** introduzir "Trabalho de Conclusão de Curso~I (TCC-I)" na primeira menção de cada um dos dois (provavelmente em `Introducao.tex` §1.5 e/ou no Cap. 3), e usar TCC-I / TCC-II em todas as demais ocorrências. Reduz o texto em aproximadamente 1 800 caracteres e padroniza a leitura.
- **Saída B:** manter a forma expandida em todas as ocorrências e remover as siglas TCC-I / TCC-II do texto, preservando apenas no Quadro 1 (cabeçalho da Tabela do Cronograma) onde a abreviação cabe por restrição de espaço.

Custo de aplicar: 30 `replace_all` consistentes, ~5 minutos. Não aplico sem confirmação.

### 5.2. G-04 — sobreposição §4.4 / §4.5 / §5.2

Os três blocos cobrem o mesmo terreno (estado dos artefatos vs resultado parcial vs limitação) com ângulos distintos. A sobreposição é convenção ABNT (Cap. 4 reporta + Cap. 5 sintetiza). Recomendo aceitar; remover qualquer um perde informação.

### 5.3. G-08, G-09 — variações terminológicas

O texto usa: "armazenamento chave-valor", "data grid", "*In-Memory Data Grid* (IMDG)", "*key-value store*". Cada uma tem nuance técnica (categoria geral, termo informal, subcategoria, termo em inglês). A variação está sob controle e ajuda a leitura. Sem ação recomendada.

### 5.4. Refinamentos opcionais que não chamariam atenção da banca

- `Introducao.tex` §1.5 (Organização do texto, 1 parágrafo): poderia ganhar uma frase a mais antecipando os apêndices.
- `FundamentacaoTeorica.tex` §2.4 (último parágrafo): "estudos de usuário registram" foi removido em E-02; o resto da subseção pode ganhar um fechamento explícito relacionando p99 ao recorte de TCC.
- `ApProtocoloMS.tex` §A.4: o fluxograma PRISMA atual é tabular; poderia virar figura TikZ na monografia final.

Nenhum desses é bloqueante.

## 6. Pendências de informação externa (não-edição)

| Pendência | Origem |
|---|---|
| Nome dos membros da banca | a confirmar com o orientador antes da defesa (atualmente a folha de aprovação está oculta pela ausência de `\banca{}`) |
| Confirmação da data de defesa | hoje fixada em 30 de junho de 2026 como provisória (`\defesa{30}{Junho}{2026}`) |

## 7. Veredito do Revisor

A monografia está **coerente, tecnicamente precisa e dentro das regras de redação aprovadas**. As três inconsistências críticas (G-05, G-06, G-07) e os três pontos de legibilidade (G-02, G-03, G-10) foram corrigidos nesta passagem. Nenhuma das recomendações remanescentes (G-01, G-04, G-08, G-09) é bloqueante; G-01 é a única com retorno significativo se aplicada (padronização TCC-I/TCC-II).
