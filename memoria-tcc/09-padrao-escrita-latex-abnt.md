# Padrão de Escrita, LaTeX e ABNT

> Extraído da análise de `Main-PPEC.tex`, `ppec.sty` e dos arquivos `abntex2*`. Referência obrigatória para o **Escritor** e checklist para o **Revisor**.
>
> **⚠️ ATENÇÃO — dois documentos, dois modelos (Decisão 002, 2026-05-11):**
> - **`Main-PPEC.tex`** = a **Proposta** já entregue — classe `article` + `ppec.sty`, citações com `\cite`. **Não alterar o modelo dela.**
> - **`Main-Monografia.tex`** = a **monografia** (TCC-1/TCC-2) em construção — classe **`abntex2`** + `abntex2cite.sty`, citações com **`\citeonline`/`\cite`**, `\chapter`, elementos pré/pós-textuais ABNT, `\legend{Fonte: ...}`. **É neste arquivo que o Escritor trabalha a partir de agora.**
> As convenções textuais (voz impessoal, terminologia, paráfrase com fonte etc.) valem para ambos; as convenções de *comandos LaTeX* diferem conforme o documento. As seções abaixo trazem o padrão para a **monografia (`abntex2`)**, com nota sobre o que era diferente na Proposta.

## Padrão textual

- **Idioma:** português do Brasil (`\usepackage[brazilian]{babel}` em `ppec.sty`).
- **Pessoa do discurso:** impessoal. O arquivo base usa consistentemente a 3ª pessoa / voz passiva analítica: "O problema alvo *será formulado*…", "A metodologia *é organizada*…", "*será montado* um ambiente…", "*serão implementadas* checagens…". **Não usar 1ª pessoa** ("eu", "nós", "fizemos") no corpo do TCC. (Exceção: o arquivo base assina como "Autor (2026)" nas fontes de tabela — manter esse formato em legendas/fontes.)
- **Tom:** acadêmico, formal, objetivo. Frases predominantemente na ordem direta (sujeito + verbo + predicado). O Revisor deve podar excessos sem empobrecer a precisão.
- **Parágrafos:** cada parágrafo com uma ideia central; parágrafos médios a longos (5–10 linhas), densos em conteúdo técnico. Evitar parágrafos de uma só frase.

### Regras permanentes de redação (Gestor, 2026-05-13)

1. **Pontuação — nunca usar travessão `---`** em português do Brasil. Substituir, conforme o contexto, por:
   - **Vírgula** para apartes curtos e cláusulas explicativas.
   - **Parênteses** para listagens dentro de frase ou comentários acessórios.
   - **Dois-pontos** para introduzir definição, listagem ou esclarecimento.
   - **Ponto-final** e reescrita em duas orações quando o aparte estiver longo o suficiente para se tornar uma frase própria.
   - Em **títulos de seção** (`\section{}`), substituir o travessão por dois-pontos (`Frente 1: Mapeamento sistemático...`) ou reescrever (`Mapeamento sistemático da literatura (Frente 1)`).
   - Em **rótulos de lista** (`\item[...]`, `\textbf{S1 --- ...}`), substituir o travessão por dois-pontos.
   - O caractere `---` (em-dash) é convenção tipográfica do inglês, não do português do Brasil. Usar apenas em comentários internos do `.tex` (`% ---`), nunca no texto que vai ao PDF.

2. **Adjetivos e advérbios — somente quando descrevem ou definem.** Eliminar qualificadores avaliativos sem função técnica, como **muito**, **importante**, **eficiente**, **bastante**, **realmente**, **certamente**, **claramente**, **simplesmente**, **basicamente**, **efetivamente**, **fortemente**, **altamente**, **extremamente**, **bem**, **muito bem**. **Manter** adjetivos e advérbios quando integram o nome do conceito (ex. *controlador heurístico*, *consenso assíncrono*, *replicação otimista*, *fully asynchronous*), quando distinguem um caso técnico (ex. *execução manual*, *literatura cinza*, *estudos seminais*, *texto-completo acessível*) ou quando precisam o sentido de uma afirmação (ex. *explicitamente registrada como limitação*, *previamente identificadas*). Em caso de dúvida, ler a frase sem o qualificador: se o sentido técnico não se perde, retirar.

3. **Construção das frases:** preferir **sujeito + verbo + predicado** com verbo concreto. Reduzir nominalizações ("a condução do mapeamento segue..." → "o mapeamento segue..."; "é adotado o guia..." → "adota-se o guia..."). Evitar frases de mais de três orações subordinadas; quando ocorrer, reescrever em duas frases.

4. **Estrutura — não criar seção/subseção para parágrafo isolado.** Se uma (sub)seção tiver duas linhas ou menos, fundir com a seção anterior ou seguinte e usar parágrafos contínuos. Seções existem para agrupar conteúdo afim, não para etiquetar parágrafos.

5. **Estas regras vinculam Escritor e Revisor** para todo conteúdo redigido a partir de 2026-05-13.
- **Terminologia:** estável e consistente — ver [`08-glossario.md`](08-glossario.md). Termos em inglês de uso consagrado em itálico via `\emph{...}` (o base faz isso: `\emph{data grid}`, `\emph{failover}`, `\emph{rebalancing}`, `\emph{state transfer}`, `\emph{model checking}`, `\emph{workload}`, `\emph{sticky sessions}` etc.). Siglas: introduzir a forma por extenso seguida da sigla entre parênteses na primeira ocorrência — o base faz: "*In-Memory Data Grid* (IMDG)", "*Round-Trip Time* (RTT)".
- **Enumerações dentro do texto:** o base usa o padrão "(i) … ; (ii) … ; (iii) …" e também "(1) … ; (2) … ; (5) …". Manter um dos dois estilos por contexto, com consistência.
- **Não** afirmar sem fundamentação: trechos que precisem de fonte recebem o marcador
  ```latex
  % [FONTE NECESSÁRIA: explicar qual afirmação precisa de fundamentação]
  ```

## Padrão de citações

- **Estilo bibliográfico:** ABNT autor-data — `\bibliographystyle{abntex2-alf}` (`abntex2-alf.bst`). Na monografia, carregar `\usepackage[alf]{abntex2cite}` (ou `\usepackage{abntex2cite}` com a opção adequada).
- **Comandos na monografia (`abntex2cite.sty`):**
  - `\citeonline{chave}` → **"Autor (ano)"** — quando o autor é sujeito da frase ("Segundo \citeonline{decandia_dynamo_2007}, ...").
  - `\cite{chave}` → **"(AUTOR, ano)"** — citação entre parênteses ao fim da afirmação.
  - `\cite{c1,c2}` → citação múltipla.
  - Páginas em citação direta: `\cite[p.~45]{chave}` → "(AUTOR, ano, p. 45)".
  - *Apud*: `\apud{citado}{fonte}` (evitar; usar só quando inevitável).
- **Na Proposta (`Main-PPEC.tex`)** usava-se só `\cite` (sem `abntex2cite`) — **não mexer**.
- **Citação direta:** curta (até 3 linhas) entre aspas no corpo, com `\cite[p.~X]{chave}`; longa (> 3 linhas) em parágrafo recuado 4 cm, fonte menor, sem aspas, com a chamada da fonte. **Preferir paráfrase com citação indireta** (protocolo de segurança acadêmica: não copiar trechos longos).
- **Chaves BibTeX:** padrão observado em `bibliografia.bib` = `autor_palavra-chave_ano` (ex.: `decandia_dynamo_2007`, `yu_lamport_tla_modelchecking_1999`) ou `tecnologia_assunto` para documentação (`infinispan_xsite`, `infinispan_metrics`). Manter esse padrão para novas entradas.
- **`note = {Acesso em: DD mmm. AAAA}`** nas entradas `@misc`/web — padrão do base (todas com "Acesso em: 19 mar. 2026"). Atualizar a data ao revisitar a fonte.

## Padrão de seções

- Estrutura atual (article + `ppec.sty`): `\section{...}` → `\subsection{...}`. O base usa `\section{Introdução}`, `\section{Objetivos}` com `\subsection{Objetivo Geral}` / `\subsection{Objetivos Específicos}`, `\section{Metodologia}`, `\section{Cronograma}`, `\section*{Assinaturas}`.
- **`\label`:** padrão do base = `sec-nomedaseção` (com hífen, sem prefixo `sec:`): `\label{sec-introducao}`, `\label{sec-objetivos}`, `\label{sec-metodologia}`, `\label{sec-cronograma}`. Manter esse padrão (ou padronizar para `sec:` se migrar para `abntex2` — decidir em P-012; **escolher um e ser consistente**).
- Títulos de seção/subseção: capitalização do base = "*sentence case* com iniciais maiúsculas nas palavras principais" (ex.: "Objetivo Geral", "Objetivos Específicos"). Sem ponto final.
- Referências cruzadas: `ppec.sty` carrega `cleveref` (`\usepackage[nameinlink,capitalize]{cleveref}`) → usar `\Cref{label}` / `\cref{label}` (gera "Seção 2", "Tabela 1" automaticamente). O base também carrega `hyperref`.
- Se migrar para `abntex2` (monografia): `\chapter{}` para capítulos numerados; `\section`/`\subsection`/`\subsubsection` abaixo; elementos textuais via `\textual`, pré-textuais via `\pretextual`, pós-textuais via `\postextual`.

## Padrão de figuras

> O arquivo base **não contém figuras** (apenas a logo da Unipampa, inserida via `ppec.sty`). Padrão recomendado (compatível com ABNT; **o comando `\legend` só existe na classe `abntex2`** — em `article`, usar a alternativa abaixo):

```latex
% Em abntex2:
\begin{figure}[htb]
    \centering
    \caption{Título da figura}
    \label{fig:nome-da-figura}
    \includegraphics[width=0.8\textwidth]{figuras/nome-da-figura.png}
    \legend{Fonte: Elaborado pelo autor.}
\end{figure}

% Em article + ppec.sty (sem \legend):
\begin{figure}[htb]
    \centering
    \caption{Título da figura}
    \label{fig:nome-da-figura}
    \includegraphics[width=0.8\textwidth]{figuras/nome-da-figura.png}
    \par\footnotesize\textit{Fonte:} Elaborado pelo autor (2026).
\end{figure}
```
- ABNT: legenda (*caption*) **acima** da figura; fonte **abaixo**. `\caption` deve vir antes de `\includegraphics` no código para que a legenda apareça no topo. `\label` logo após `\caption`.
- Toda figura deve ser citada no texto (`\Cref{fig:...}`) antes de aparecer.

## Padrão de quadros

- ABNT distingue **tabela** (dados numéricos, bordas laterais abertas) de **quadro** (texto/esquema, fechado por bordas). A classe `abntex2` não tem ambiente `quadro` nativo; convenção usual: usar `table` com `\caption{Quadro N -- ...}` ou o pacote `tabularx`/`booktabs`. Definir o padrão quando o primeiro quadro surgir; registrar aqui.

## Padrão de tabelas

Padrão **observado no arquivo base** (manter):
```latex
\begin{table}[ht]
\centering
\label{tab:nome-da-tabela}
\begin{tabular}{p{3.2cm}p{9.6cm}p{3.0cm}}
\hline
\textbf{Coluna 1} & \textbf{Coluna 2} & \textbf{Coluna 3} \\
\hline
Valor 1 & Valor 2 & Valor 3 \\
\hline
\end{tabular}
\footnotesize\textit{Fonte:} Autor (2026).
\end{table}
```
- O base usa `\hline` (linhas horizontais simples) e colunas `p{largura}` para texto multilinha. **Observação para o Revisor:** o base **omitiu `\caption`** na tabela do cronograma — isso é uma falha de padronização ABNT; novas tabelas **devem ter `\caption{Título}` acima** (antes de `\begin{tabular}`) e o `\label` logo após o `\caption`. ABNT recomenda `booktabs` (`\toprule`/`\midrule`/`\bottomrule`) em vez de `\hline`; decidir se adotamos `booktabs` (atualmente não carregado).
- Fonte: `\footnotesize\textit{Fonte:} Autor (2026).` (padrão do base) ou, em `abntex2`, `\legend{Fonte: ...}`.
- `placeins` está carregado (`\FloatBarrier` impede que floats "vazem" para a seção seguinte) — o base usa `\FloatBarrier` após o cronograma. `\newpage` é usado para forçar quebras antes/depois de tabelas grandes.
- Toda tabela deve ser citada no texto antes de aparecer.

## Padrão de referências (seção bibliográfica)

```latex
\bibliographystyle{abntex2-alf}
\bibliography{bibliografia}
```
- Arquivo `.bib`: `bibliografia.bib` (na raiz do projeto). Compilação: `pdflatex → bibtex → pdflatex → pdflatex`.
- Tipos de entrada usados no base: `@inproceedings` (artigos de conferência), `@article` (periódico), `@techreport` (relatório técnico), `@misc` com `howpublished = {Website}` (páginas web/documentação). Todos com `url = {...}` e `note = {Acesso em: ...}` quando aplicável.
- Autores institucionais entre chaves duplas: `author = {{Infinispan Project}}`, `author = {{Google}}` — preserva a grafia e evita inversão de nome.
- Caracteres especiais em URLs no `.bib`: escapar `_` como `\_` (o base faz: `CRDTs\_SSS-2011.pdf`).

## Comandos LaTeX úteis (no contexto deste projeto)

| Comando | Origem | Uso |
|---|---|---|
| `\emph{...}` | LaTeX | termos em inglês / ênfase leve |
| `\cite{chave}` / `\cite{c1,c2}` | LaTeX + abntex2-alf | citação (padrão atual do base) |
| `\citeonline{chave}` | `abntex2cite.sty` (a carregar — P-012) | "Autor (ano)" no curso do texto |
| `\Cref{label}` / `\cref{label}` | `cleveref` (carregado por `ppec.sty`) | referência cruzada com rótulo automático |
| `\FloatBarrier` | `placeins` (carregado pelo base) | barreira para floats |
| `\newpage` | LaTeX | quebra de página manual (usado em torno de tabelas grandes) |
| `\maketitle` | redefinido por `ppec.sty` | gera a capa da Proposta (logo + cabeçalho Unipampa) — **só na Proposta** |
| `\author{}`, `\title{}`, `\advisor{}`, `\coadvisor{}` | `ppec.sty` | metadados da capa da Proposta |
| `\renewcommand{\advisorname}{Orientadora}` | `ppec.sty` | ajuste de gênero do rótulo |
| `\namegroup{...}` | definido no `Main-PPEC.tex` | blocos de assinatura (só na Proposta) |
| `\section*{...}` | LaTeX | seção não numerada (o base usa em "Assinaturas") |

## Observações extraídas do arquivo base

1. **O arquivo base é uma *Proposta de TCC*** (classe `article` + `ppec.sty`, modelo Unipampa "Proposta de TCC I"), não a monografia. Estrutura: Introdução → Objetivos → Metodologia → Cronograma → Referências → Assinaturas. Não há resumo/abstract, sumário, listas, fundamentação teórica, resultados nem conclusão.
2. **A classe `abntex2` e o estilo `abntex2-alf` já estão no diretório** — viabiliza migração para a monografia ABNT completa (decisão P-012).
3. **Citações:** o base usa só `\cite`; `\citeonline` exigiria `abntex2cite.sty`.
4. **Padrão de `\label`:** `sec-nome` (hífen, sem prefixo de tipo) para seções; recomenda-se `fig:`, `tab:`, `eq:`, `lst:` para os demais (a padronizar formalmente).
5. **Falha de padronização identificada no base:** a tabela do cronograma não tem `\caption` — a monografia deve corrigir (e o Revisor deve vigiar isso).
6. **Voz impessoal estrita** no corpo; "Autor (AAAA)" apenas em fontes de tabela/figura.
7. **Datas de acesso** das fontes web: "19 mar. 2026" no base — manter o formato "DD mmm. AAAA" (mês abreviado, minúsculo, com ponto), conforme ABNT.
8. **`\selectlanguage{brazilian}`** está em `ppec.sty` — hifenização e termos automáticos (Figura, Tabela, Seção, Referências) em português.
