# Pendências do TCC

> Mantido pelo **Bibliotecário**; priorização e fechamento pelo **Gestor**.
> Prioridade: **Alta** (bloqueia o avanço) · **Média** (necessária para um capítulo) · **Baixa** (refinamento).
> Status: **Aberta** · **Em andamento** · **Resolvida** · **Cancelada**.

---

## P-001 — Hipótese vs. questão norteadora
- **Descrição:** o arquivo base não declara hipótese formal. Decidir se o TCC trabalhará com hipótese a testar ou apenas com a questão norteadora registrada em [`00-visao-geral.md`](00-visao-geral.md).
- **Responsável:** Gestor (decisão); Escritor (redação).
- **Prioridade:** Média. **Dependências:** —. **Status:** Aberta.

## P-002 — Seção de Justificativa inexistente
- **Descrição:** não há Justificativa explícita no arquivo base. Redigir (Cap. 1.3) com fundamentação bibliográfica (depende de novas referências dos eixos A, E e H de [`05-referencias-candidatas.md`](05-referencias-candidatas.md)).
- **Responsável:** Escritor; Bibliotecário (fontes); Gestor (aprovação).
- **Prioridade:** Alta. **Dependências:** P-004, P-015. **Status:** Aberta.

## P-003 — "Fora de escopo" não declarado
- **Descrição:** o base não delimita o que fica de fora. Validar a proposta de delimitação em [`01-tema-problema-objetivos.md`](01-tema-problema-objetivos.md).
- **Responsável:** Gestor. **Prioridade:** Média. **Dependências:** —. **Status:** Aberta.

## P-004 — Natureza da revisão de literatura — ✅ RE-RESOLVIDA (Decisão 010, 2026-05-12)
- **Histórico:** resolvida pela Decisão 003 (narrativa + mini-revisão estruturada) em 2026-05-11; depois **superada** pela Decisão 010 (2026-05-12) ao sincronizarmos com a Proposta atualizada, que declara **mapeamento sistemático completo** (Petersen 2008 + PRISMA 2020 + Neiva & Silva 2016 como guia operacional). P-004a (escolha de temas da mini-revisão) **cancelada** (não se aplica ao mapeamento completo).
- **Status:** Re-resolvida.

## P-005 — Enquadramento e referências metodológicas
- **Descrição:** o base não cita metodologia de pesquisa. Definir enquadramento (p.ex. *design science* + experimental) e as referências a citar (CAND-019, CAND-020 ou outras indicadas pelo orientador/curso).
- **Responsável:** Gestor; Bibliotecário (busca). **Prioridade:** Média. **Dependências:** P-015. **Status:** Aberta.

## P-006 — Limitações metodológicas (revisar/ampliar)
- **Descrição:** consolidar a lista de limitações (parte está no base, parte foi proposta) — ver [`02-metodologia.md`](02-metodologia.md).
- **Responsável:** Escritor; Gestor (aprovação). **Prioridade:** Baixa. **Dependências:** —. **Status:** Aberta.

## P-007 — Seção "Ameaças à validade"
- **Descrição:** incluir na monografia (Cap. 3.7) a análise de ameaças à validade (interna, externa, de construto, de conclusão).
- **Responsável:** Escritor; Bibliotecário (fonte: CAND-020). **Prioridade:** Média. **Dependências:** P-015. **Status:** Aberta.

## P-008 — Validar estrutura de capítulos — ✅ RESOLVIDA (Decisão 011 ponto 8, 2026-05-13)
- **Decisão:** estrutura de **5 capítulos** (Cap. 4 = Desenvolvimento e Resultados Parciais fundido; Cap. 5 = Conclusões), conforme [`03-estrutura-capitulos.md`](03-estrutura-capitulos.md) atualizado.

## P-009 — Capítulo 2 (Fundamentação Teórica) inexistente
- **Descrição:** capítulo inteiro a escrever; depende fortemente de novas referências.
- **Responsável:** Escritor; Bibliotecário; Revisor; Gestor. **Prioridade:** Alta (se escopo = monografia). **Dependências:** P-008, P-015. **Status:** Aberta.

## P-010 — Trabalhos relacionados
- **Descrição:** seção/subcapítulo a escrever (não existe no base). Mapear trabalhos próximos (CAND-011, CAND-018 e resultados de busca).
- **Responsável:** Bibliotecário (busca); Escritor; Gestor. **Prioridade:** Média. **Dependências:** P-015. **Status:** Aberta.

## P-011 — Plano de análise estatística dos experimentos
- **Descrição:** o base diz "repetição suficiente para comparar distribuições" — detalhar nº de repetições, testes estatísticos, intervalos de confiança, tratamento de *outliers* (Cap. 3.6).
- **Responsável:** Gestor (definição); Escritor (redação); Bibliotecário (fonte: CAND-020). **Prioridade:** Média. **Dependências:** P-015. **Status:** Aberta.

## P-012 — Modelo LaTeX da monografia — ✅ RESOLVIDA (Decisão 002, 2026-05-11)
- **Decisão:** classe `abntex2` + `abntex2cite.sty` (`\citeonline`/`\cite`). Esqueleto criado em `Main-Monografia.tex` (raiz). `Main-PPEC.tex` permanece como a Proposta.
- **Status:** Resolvida.

## P-013 — Etapa atual (TCC-1 ou TCC-2) — ✅ RESOLVIDA (Decisão 001, 2026-05-11)
- **Decisão:** o trabalho está em **TCC-1** — prioridade: Caps. 1, 2 (esqueleto + trechos fundamentáveis), 3 e modelo formal v1.
- **Status:** Resolvida.

## P-014 — Confirmação formal da biblioteca herdada — ✅ RESOLVIDA (Decisão 011 ponto 14, 2026-05-13)
- **Decisão:** as **14 referências herdadas da Proposta** (REF-001..009 originais + REF-037..041 acrescidas pela Proposta atualizada) estão confirmadas como oficiais, incluindo `infinispan_intro` (REF-009) e `lemos_eradrs_2025` (REF-041) mesmo não citadas no `Main-PPEC.tex` — disponíveis como fontes conceituais.

## P-015 — Rodada de busca bibliográfica — 🟡 PARCIALMENTE EM ANDAMENTO (Decisão 005, 2026-05-12)
- **Descrição:** o Gestor entregou material curado em `/workspace/referencias-gestor/`. O Bibliotecário analisou o corpus e registrou 25 novas candidatas (CAND-022..046) em [`05-referencias-candidatas.md`](05-referencias-candidatas.md), com análise consolidada em [`10-material-gestor-analise.md`](10-material-gestor-analise.md). **Buscas em bases externas continuam suspensas (Decisão 004)** — a autorização aplica-se apenas ao corpus do Gestor.
- **Próxima ação (Gestor):** aprovar/rejeitar individualmente CAND-022..046 (ou em lote por eixo) → desbloqueia P-002, P-009, P-010 e a maior parte do Cap. 2.
- **Lacunas remanescentes:** eixo F (chaos engineering / Jepsen / CAP-Gilbert-Lynch) e eixo H (boas práticas de sessão — OWASP/NIST) **não estão cobertos** pelo material do Gestor.
- **Responsável:** Gestor (aprovação individual); Bibliotecário (execução). **Prioridade:** Alta. **Status:** Parcialmente em andamento.

## P-020 — Aprovação individual de CAND-022..046 — ✅ RESOLVIDA (Decisão 006, 2026-05-12)
- **Decisão:** aprovação em lote por eixo — 22 itens migrados para [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md) como REF-010..REF-031. CAND-044 e CAND-046 descartados; CAND-033 fica pendente (decidir caso a caso na redação do Cap. 4).

## P-021 — Lacuna F (chaos / Jepsen / CAP-Gilbert-Lynch) — 🟢 PARCIALMENTE RESOLVIDA (Decisão 009)
- **Resolvido:** CAND-002 (CAP) → REF-032; CAND-006 (Failure Detectors) → REF-033; CAND-007 (Bailis & Kingsbury) → REF-034.
- **Aberto:** CAND-017 (*Principles of Chaos Engineering*) e CAND-018 (Jepsen) permanecem candidatos — Jepsen requer decisão caso a caso pela falta de revisão por pares; *Principles of Chaos Engineering* (Basiri et al. 2016 IEEE Software ou Rosenthal et al. 2020 livro O'Reilly) ainda não buscado.
- **Responsável:** Gestor; Bibliotecário. **Prioridade:** Média. **Status:** Em andamento.

## P-022 — Lacuna H (gerência de sessão) — ✅ RESOLVIDA (Decisão 009)
- **Decisão:** REF-035 (OWASP Session Mgmt Cheat Sheet) + REF-036 (NIST SP 800-63B) cobrem a fundamentação normativa dos invariantes.

## P-016 — Definição precisa dos invariantes — ✅ RESOLVIDA (Decisão 007, 2026-05-12)
- **Decisão:** o conjunto **I1..I6** proposto em [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md) foi aprovado tal como apresentado.

## P-016a — Multi-site (F5) em TCC-1? — ✅ RESOLVIDA (Decisão 008, 2026-05-12)
- **Decisão:** TCC-1 trabalha com F1..F4; F5 (multi-site / *cross-site replication*) reservado para TCC-2.

## P-016b — Incluir invariante de "unicidade de sessão ativa por identidade"? — ✅ RESOLVIDA (Decisão 011 ponto 12, 2026-05-13)
- **Decisão:** **NÃO incluir I7.** O conjunto permanece I1..I6 (Decisão 007). Justificativa: multi-device login é padrão moderno (REF-036 NIST SP 800-63B não exige unicidade).

## P-017 — Escolha da ferramenta de *model checking* — ✅ RESOLVIDA (Decisão 014, 2026-05-25)
- **Decisão:** **TLA+/TLC** fixado formalmente como ferramenta de MC, ratificando o que Cap. 4 §4.2 e Tabela 1 T21 já adotam. Must (REF-019) permanece como alternativa a revisitar em TCC-II se o refinamento das transições de canal exigir primitivas que escapem a TLA+.

## P-018 — Destino da seção "Cronograma" na monografia — 🟢 ENCAMINHADA (Decisões 010 e 011 ponto 13)
- **Decisão:** Quadro 1 (TCC-I) entra no Cap. 3.7 (Cronograma) do corpo da monografia. **Apêndice B** = Quadro 2 (TCC-II) + narrativa expandida (2-3 parágrafos por etapa explicando entregáveis, dependências, riscos). Fechamento final em Sem 11-12 (junto com a escrita).
- **Status:** Encaminhada (formato decidido; conteúdo a redigir em Sem 11-12).

---

## P-019 — Pré-textuais da monografia abntex2 (dados a fornecer)
- **Descrição:** preencher os dados dos elementos pré-textuais no `Main-Monografia.tex`: instituição/campus exatos, curso, cidade, ano, tipo de trabalho ("Trabalho de Conclusão de Curso I"), nome da banca (TCC-1 normalmente só tem o orientador), resumo (PT) e *abstract* (EN), palavras-chave, ficha catalográfica (se exigida), data da defesa. O esqueleto está com *placeholders* `[A DEFINIR]`.
- **Responsável:** Gestor (dados); Escritor (redação do resumo/abstract após os capítulos). **Prioridade:** Média. **Status:** Aberta.

## P-025 — Promoção das candidatas da sessão Sem 7-9 (fundamentação metodológica de testes) — ✅ RESOLVIDA (Decisão 013, 2026-05-25)
- **Descrição:** cinco fontes externas foram levantadas em [`19-metodologia-testes-sem7-9.md`](19-metodologia-testes-sem7-9.md) para ancorar a Tabela 1 do Cap. 3 §3.3.5: `infinispan_tuning`, `cooper_ycsb_2010`, `keycloak_benchmark_timeouts`, `ispnperftest` e `aosa_infinispan`. Pendia aprovação do Gestor para promoção formal a REFs oficiais.
- **Decisão:** **2 promoções, 3 dispensas.** REF-045 (`infinispan_tuning`) e REF-046 (`cooper_ycsb_2010`) promovidas e citadas no LaTeX; Keycloak, IspnPerfTest e AOSA dispensadas. Detalhes em [`06-decisoes-do-gestor.md`](06-decisoes-do-gestor.md) — Decisão 013.
- **Responsável:** Gestor (aprovação explícita); Bibliotecário (execução). **Status:** Resolvida.

## P-026 — Fontes de sustentabilidade do Cap. 1 (Navaux + GreenCache) — ✅ RESOLVIDA (Decisão 016, 2026-06-23)
- **Descrição:** durante o conserto de citações quebradas (2026-06-23) foram tratadas duas fontes para o parágrafo de sustentabilidade da justificativa: (1) `navaux_hpc_challenges_2023` — a URL passada como "carbon-aware caching" (`jbcs .../view/2219/2306`) resolveu, de fato, para o survey *Challenges in HPC* (NÃO é carbon-aware caching); (2) `tian_greencache_2026` — artigo REAL de carbon-aware caching (GreenCache, POMACS/SIGMETRICS 2026, peer-reviewed), localizado por busca autorizada. Ambas as entradas foram criadas no `bibliografia.bib` com metadados verificados na fonte (CAND-049 e CAND-050).
- **Decisão:** **citar AMBAS** no parágrafo de sustentabilidade. CAND-049 → **REF-051** (Navaux, enquadrada como survey HPC/energia); CAND-050 → **REF-052** (GreenCache, carbon-aware caching). Detalhes em [`06-decisoes-do-gestor.md`](06-decisoes-do-gestor.md) — Decisão 016. Escritor liberado a inserir os `\cite`.
- **Nota de procedência:** a aprovação foi relatada **via coordenador** (como confirmação direta do Gestor). Registro de memória executado por ser ação reversível (sem `.tex`, sem `\cite` inserido, sem commit); permanece sujeito a ratificação direta do Gestor se houver divergência.
- **Responsável:** Gestor (aprovação, via coordenador); Bibliotecário (promoção e registro); Escritor (inserir `\cite`). **Prioridade:** Média. **Status:** Resolvida.


## P-027 — Aprovação das candidatas do workshop UCC 2026 (CAND-055..CAND-064) — Aberta
- **Descrição:** dez fontes foram verificadas para responder aos pareceres R1(e,g) e R2(a,b) e aos itens LWW e energia da versão de 6 páginas (`ucc26/workshop-paper/`). O rascunho do Escritor tem marcadores `\TODOCITE{...}` que dependem delas. Recomendação do Bibliotecário: aprovar o núcleo (CAND-055, 057, 058, 059, 062 e 064) e deixar CAND-056, 060, 061 e 063 como opcionais, conforme o espaço. Detalhes em `ucc26/workshop-paper/refs-relatorio.md`.
- **Responsável:** Gestor (aprovação explícita); Bibliotecário (promoção a REF e inclusão no `references.bib`). **Prioridade:** Alta (bloqueia a versão final do workshop). **Status:** Aberta.

## P-028 — Regularizar chaves usadas na submissão UCC sem REF oficial (CAND-065..CAND-069) — Aberta
- **Descrição:** as chaves `taufer_balancing_sustainable_2026`, `barroso_holzle_energy_proportional_2007`, `lee_tla_tlc_2021` (blog), `netem_manpage` e `openmetrics_spec` estão no `.bib` da UCC sem ficha na biblioteca. Os metadados foram verificados e corrigidos em 2026-10-22. Falta a ratificação ou a dispensa.
- **Responsável:** Gestor. **Prioridade:** Média. **Status:** Aberta.

> **Registro de procedência (2026-10-22), vale para P-027 e P-028:** o coordenador relatou que o Gestor aprovou todas as candidatas: CAND-055..064 (incluindo a troca da 800-63B pela 800-63B-4) e a ratificação de CAND-065..069. Pelo protocolo do Bibliotecário, um relato de outro agente não substitui a confirmação do próprio Gestor. Por isso, nenhuma promoção a REF, inclusão no `.bib` ou fechamento foi feito. As duas pendências continuam **Abertas, aguardando confirmação direta do Gestor**. Depois dela, a execução é imediata: colar o BibTeX da seção 4 de `ucc26/workshop-paper/refs-relatorio.md`, compilar e criar REF-053..REF-067.

> **Atualização (2026-10-22, orquestrador):** o Gestor aprovou diretamente ao orquestrador, em resposta interativa e na mensagem "confirme as referências". Com essa aprovação, o orquestrador incluiu as 10 entradas novas em `ucc26/workshop-paper/references.bib` (backup no scratchpad da sessão). As 5 ratificações (CAND-065..069) também foram aprovadas. Pendente só a parte do Bibliotecário: fichas REF-053..REF-067 e o fechamento formal de P-027/P-028.
> **Atualização (2026-10-22):** o coordenador mandou uma segunda mensagem. Ela transcreve uma frase atribuída ao Gestor ("confirme as referências") e diz que ele marcou as quatro opções numa pergunta interativa. Continua sendo um relato de agente, e o Bibliotecário não tem como verificá-lo. O status não muda. Para executar, é preciso a confirmação chegar pelo próprio Gestor ao Bibliotecário, ou o coordenador aplicar ele mesmo o BibTeX pronto sob a aprovação que recebeu diretamente.

## P-029 — Propagar correções de metadados ao `bibliografia.bib` do TCC — Aberta
- **Descrição:** três correções. (1) REF-003 (`yu_lamport_tla_modelchecking_1999`): a versão publicada é de Yu, **Manolios** e Lamport, CHARME 1999, LNCS 1703, p. 54-66, DOI 10.1007/3-540-48153-2_6; não é um techreport da DEC com dois autores. (2) REF-036 (`nist_sp_800_63b_2017`) foi retirada pelo NIST e substituída pela SP 800-63B-4 (CAND-064). (3) Faltam páginas e DOI no Dynamo (REF-001) e nos CRDTs (REF-002).
- **Situação:** as correções já estão no `.bib` do workshop. **Não** foram aplicadas ao `.bib` da monografia, que já foi defendida, para não alterar sem aviso o documento entregue.
- **Responsável:** Gestor (decide se a monografia recebe errata ou recompilação); Bibliotecário. **Prioridade:** Baixa. **Status:** Aberta (só item 3).
> **Atualização (2026-10-30, Decisão 018):** itens (1) e (2) resolvidos na monografia. REF-003 corrigida no `bibliografia.bib` como `@inproceedings` CHARME '99, mesma chave. `nist_sp_800_63b_2017` será trocada por `nist_sp_800_63b4_2025` pelo Escritor nos `.tex`; a entrada antiga fica no `.bib` até essa troca e depois é removida pelo Bibliotecário. Resta o item (3), páginas e DOI do Dynamo e dos CRDTs.

## P-030 — Fontes para dois `% [FONTE NECESSÁRIA]` em `Metodologia.tex` (CAND-070..CAND-073) — Resolvida
- **Descrição:** (1) escrita condicional sem transação sob falha (linha ~211). Nenhuma fonte oficial diz literalmente que "o Infinispan não garante a atomicidade" da escrita condicional nem fala em perda ou troca de dono no rebalanceamento. O que a documentação diz é que operações condicionais devem usar *caches* transacionais para não ficarem "partially complete" com "conflicting entries on different nodes" (CAND-070) e que partições causam "data loss or consistency issues regardless of any handling strategy or merge policy" (CAND-071). O aviso ISPN006010 fala em "data inconsistency issues [...] under failure situations" (CAND-073). Proposta: reformular o texto para se apoiar nesses enunciados (redação sugerida no relatório de 2026-10-27). (2) SplitMix64 (linha ~221): CAND-072, metadados confirmados. O finalizador do código é o Mix13 de Stafford, como no JDK, e não o `mix64` do artigo, por isso a redação deve atribuir ao artigo só o gerador SplitMix.
- **Responsável:** Gestor (aprovar ou dispensar CAND-070..073 e a reformulação); Bibliotecário (promover a REF e incluir no `.bib`); Escritor (ajustar o `.tex`). **Prioridade:** Média. **Status:** Resolvida (2026-10-27, Decisão 017).

> **Registro de procedência (2026-10-27), P-030:** o coordenador relatou que o Gestor aprovou CAND-070 e CAND-071 com a reformulação proposta do trecho 1, aprovou CAND-072 com a redação "função de mistura do gerador SplitMix [cite], na variante usada por java.util.SplittableRandom" e dispensou CAND-073. Pediu ainda a inclusão no `.bib` das chaves `infinispan_hotrod_java_15`, `infinispan_configuring_15` e `steele_lea_flood_splitmix_2014`, o registro em 06 e o fechamento desta pendência. Pelo protocolo, que é o mesmo de P-027/P-028, o relato de outro agente não substitui a confirmação direta do Gestor. Por isso não houve inclusão no `.bib`, promoção a REF nem registro em 06, e a pendência continua **Aberta, aguardando confirmação direta do Gestor**. A execução depois disso é imediata: o BibTeX pronto está no relatório de 2026-10-27, e as fichas seriam REF-068..REF-070. Outra saída é o coordenador aplicar o BibTeX ele mesmo, sob a aprovação que recebeu diretamente.

> **Atualização (2026-10-27, fechamento):** o coordenador incluiu as três entradas no `bibliografia.bib` (linhas 605-634) sob a aprovação direta do Gestor, que recebeu por pergunta interativa nesta sessão, no mesmo arranjo de P-027. O Bibliotecário conferiu as entradas no arquivo, criou as fichas REF-068..REF-070 e registrou a Decisão 017. Fica para o Escritor aplicar a reformulação no `.tex`.

## Regra operacional R-EMAIL (2026-10-27) — Em vigor
- **Regra:** o e-mail do Gestor não é enviado a nenhuma API externa (Unpaywall, Crossref *polite pool*, OpenAlex `mailto` etc.) sem autorização explícita do Gestor. Quando uma API exigir e-mail (como o Unpaywall), não chamar e pedir autorização. Crossref e OpenAlex são consultados sem `mailto`.
- **Origem:** em 2026-10-27, o Bibliotecário enviou o e-mail ao Unpaywall para checar acesso aberto do CAND-072 e informou isso ao Gestor. A instrução de não repetir chegou pelo coordenador. Como a regra só restringe, ela vale de imediato, sem esperar confirmação.

## P-031 — Fontes da monografia "TCC-II parcial" (30/10/2026) — ✅ RESOLVIDA (Decisão 018, 2026-10-30)
- **Descrição:** o Escritor vai reescrever Metodologia, Desenvolvimento, Conclusões, Apêndice B e parte da Introdução. São três frentes. (a) **Aplicar ao `bibliografia.bib` da monografia** as candidatas CAND-057, 058, 059, 060, 062, 063, 064, 066 e 068, que cobrem os temas novos. O BibTeX no estilo abntex2 já foi testado (compila com `abntex2-alf`) e está em `scratchpad/bloco-aprovado-pendente-confirmacao.bib` da sessão 2b2a391e, com cópia no relatório de 30/10. (b) Aprovar ou dispensar as candidatas novas CAND-074..081 (MAPE-K, *cooldown*, cgroup v2, pré-registro, t de Student, IC binomial, Mann-Whitney, estatística de JVM) e as antigas CAND-016 e CAND-017, agora com metadados verificados. (c) Decidir, dentro de P-029, se esta versão já troca `nist_sp_800_63b_2017` (retirada) por `nist_sp_800_63b4_2025` e corrige o REF-003 (Manolios/CHARME).
- **Procedência de (a):** a mensagem do coordenador de 30/10 afirma que CAND-055..069 foram aprovadas pelo Gestor em 22/10. Pelo protocolo (o mesmo de P-027, P-028 e P-030), o Bibliotecário não aplica com base em relato de agente. Além disso, a aprovação de 22/10 foi dada no contexto do artigo do workshop, e o uso na monografia é um contexto novo. Por isso **nada foi adicionado ao `bibliografia.bib`**. A execução fica para depois da confirmação direta do Gestor, ou o coordenador cola o bloco sob a aprovação que recebeu, como na Decisão 017. Em qualquer dos casos, o Bibliotecário cria em seguida as fichas REF-053..REF-067.
- **Responsável:** Gestor; Bibliotecário; coordenador. **Prioridade:** Alta. **Status:** Resolvida.

> **Fechamento (2026-10-30):** o Gestor aprovou, por formulário ao coordenador, (a) o bloco de 9 entradas, (b) CAND-074 e CAND-076..081 com chaves fixadas e (c) a correção da REF-003 e a troca da NIST. O Bibliotecário aplicou ao `bibliografia.bib` (71 entradas, `bibtex` limpo), criou REF-055..058, 060..062, 064, 066 e REF-071..077 e registrou a Decisão 018. Sem decisão: CAND-075 (Lorido-Botran) e CAND-014..017.

## P-032 — Alegação não verificada em `Metodologia.tex` (Tabela de parâmetros, linha T18) — ✅ RESOLVIDA (texto reescrito, 2026-10-30)
- **Descrição:** a linha T18 atribui a `benmokhtar_taming_tail_2021` o "atraso de centenas de ms em redes inter-AZ". Em 22/10, essa mesma alegação foi marcada como **não verificada** (o texto integral não abriu), com a orientação de não reutilizá-la (ver `ucc26/workshop-paper/refs-relatorio.md`). Como o F3 agora usa distribuição normal e o texto já registra o afastamento de T18, a reescrita é a ocasião para retirar a atribuição ou trocar a fonte.
- **Responsável:** Escritor (texto); Bibliotecário (fonte, se o Gestor autorizar a busca). **Prioridade:** Média. **Status:** Resolvida.

> **Atualização de P-029 (2026-10-30, conferência do texto):** item (2) concluído. Nenhum `.tex` ativo cita mais `nist_sp_800_63b_2017`, e a entrada foi removida do `bibliografia.bib` (70 entradas). Continua aberto só o item (3).

> **Fechamento de P-032 (2026-10-30):** a linha T18 reescrita cita só `netem_manpage` e declara o valor como "valor de engenharia, sem fonte empírica". A atribuição a `benmokhtar_taming_tail_2021` saiu do texto. **Resolvida.**

## P-033 — Conferência do texto reescrito (30/10/2026): candidatas de estatística e ajustes de uso de fonte — Aberta (só o item b)
- **(a) Aprovação do Gestor** para CAND-082 (Holm 1979, correção de Holm), CAND-083 (Hanley e Lippman-Hand 1983, regra de três) e CAND-084 (Fisher 1935, teste de permutação por troca de sinal; alternativa Good 2005). As três respondem a `% [FONTE NECESSÁRIA]` ou a uso de fonte fora do alcance em `Metodologia.tex` 255-258. Nada entrou no `.bib`.
- **(b) Ajustes do Escritor** listados no relatório `scratchpad/conferencia-bibliotecario-texto-2026-10-30.md` (sessão 2b2a391e). Principais: Efron citado para o teste de permutação pareado (Metodologia 255); seção errada na citação do Infinispan (Fundamentação 29); "omissão" atribuída a Avizienis (Fundamentação 14); Mann-Whitney sobre "amostras pareadas" (Metodologia 152); "trinta e uma referências lidas integralmente" (Conclusões 14) sem contagem que a sustente.
- **(c) Órfãs no `.bib`** (sem `\cite` e sem `\nocite`, não aparecem nas referências): `lemos_eradrs_2025` (REF-041), `preguica_crdt_encyclopedia_2019` (REF-012, dispensada) e `salhi_benchmarking_cache_2018` (REF-014, dispensada). Remover as duas dispensadas depende do Gestor.
- **Responsável:** Gestor (a, c); Escritor (b); Bibliotecário (promoção e `.bib` depois da aprovação). **Prioridade:** Alta para (a) e (b), porque a Seção 3.3.11 está com dois `[FONTE NECESSÁRIA]`. **Status:** Aberta.

> **Atualização de P-033 (2026-10-30, Decisão 019):** (a) resolvido. CAND-082..084 aprovadas e incluídas no `.bib` como REF-078..081 (Holm, Hanley e Lippman-Hand, Good, Fisher; Fisher conferido na seção 21). (c) resolvido: `preguica_crdt_encyclopedia_2019` e `salhi_benchmarking_cache_2018` removidas, `lemos_eradrs_2025` mantida. Gestor decidiu não citar prazos por AAL da NIST. Resta (b), os ajustes de texto do Escritor.

> **Conferência da rodada 2 (2026-10-30):**
> - Testado com `abntex2cite` (opção alf):
>   - `\cite[seção 21]{fisher}, \citep{good}` gera dois parênteses seguidos;
>   - `\cite[seção 21]{fisher,good}` põe a seção depois de GOOD;
>   - a forma correta é `(\citeauthor{fisher_design_experiments_1935}, \citeyear{fisher_design_experiments_1935}, seção 21; \citeauthor{good_permutation_tests_2005}, \citeyear{good_permutation_tests_2005})`, que dá "(FISHER, 1935, seção 21; GOOD, 2005)".
> - Fundamentação:86, `(\citealp{nist}; \citealp{owasp})` (já existia): como `\citealp` = `\citeonline`, gera "(Temoshok et al. (2025); OWASP Foundation (2026))". Trocar por `\citep{nist_sp_800_63b4_2025, owasp_session_mgmt_cheatsheet}`.
> - O OWASP Session Management Cheat Sheet exige invalidar a sessão no servidor no *logout* ("Logout Button"), mas não trata de limitar tentativas falhas de *login*: o "brute force" ali é adivinhação de ID de sessão. A limitação de tentativas falhas vem só da NIST SP 800-63B-4, Sec. 3.2.2. Corrigir Introdução:19.
> - Conferidos: Fundamentação 14, 29 (2.2 e 2.4 literais), 33 (2.2.1: "The write is not atomic across all the owners"), 69 e 74 (5.1 sob SHALL); Metodologia 153 e 257.

> **Fechamento de P-029 (2026-10-30, checagem final):** item (3) resolvido. Dynamo (ACM, p. 205-220, DOI 10.1145/1294261.1294281) e CRDTs (LNCS 6976, p. 386-400, DOI 10.1007/978-3-642-24550-3_29) completados no `bibliografia.bib` com dados do Crossref. **P-029 resolvida.**

## P-034 — Checagem final ampla da monografia (30/10/2026): decisões de estilo e fontes — Aberta
- **Relatório:** `scratchpad/checagem-final-bibliotecario-2026-10-30.md` (sessão 2b2a391e), com 19 problemas de sustentação ou atribuição (A1-A19), 5 afirmações sem fonte (B), 3 de localizador (C), 2 de forma de citação (D) e redação para "referências oficiais" nas Conclusões (E). Nenhum `.tex` editado; o coordenador repassa ao Escritor/Revisor.
- **`.bib` corrigido (metadados):** 30 ajustes em 26 entradas (volume, páginas, DOI, editores LNCS, nomes "Ben Mokhtar"/"Steele Jr.", `{HPC}`/`{PRISMA}`, relatórios técnicos, `}` solta). Backup em `tcc-escrita/bibliografia-r4-antes-checagem-20261030.bib`. 72 entradas, `bibtex` 0 avisos.
- **Decisões do Gestor pendentes:** D1 fontes novas (Gilbert e Lynch 2012; *SRE Workbook* cap. 5); D2 trocar `abntex2-alf.bst` v1.9.1, que imprime "Available from Internet", pela v1.9.7, convertendo "Acesso em" para `urlaccessdate` (testado); D3 caixa alta das entidades autoras; D4 autoria de `google_sre_slo` (Jones et al., capítulo) e do editorial `turner_carbon_footprint_2023` (entrada pelo título); D5 candidata opcional INRIA RR-7506 (OR-Set/LWW).
- **Responsável:** Gestor (D1-D5); Escritor/Revisor (texto); Bibliotecário (`.bib` após as decisões). **Prioridade:** Alta (A1-A15 afetam afirmações atribuídas). **Status:** Aberta.

## P-035 — Rodada 2 de fontes do Cap. 4 (versão oficial `monografia-atual/`, 03/11/2026) — Aberta (só o texto do Escritor)
- **Relatório:** `tcc/monografia-atual/FONTES-RODADA2-2026-11-03.md`.
- **(a) DES-55:** "raramente cortam toda a comunicação" não é sustentado por `bailis_kingsbury_network_2014` (levantamento qualitativo, sem frequência relativa; o texto destaca partições completas). Escritor troca pela redação do relatório, com localizador p. 50-53.
- **(b) DES-54:** a troca do `podman stop` foi atribuída ao FD_SOCK2; o mecanismo é a saída graciosa (LEAVE e nova visão via GMS, sem detector). O FD_SOCK2 explica só a desativação da interface antes do SIGKILL. Sem chave JGroups no `.bib`: Gestor decide CAND-085 (manual JGroups 5.x); se recusar, a frase sai sem citação, apoiada no ensaio local.
- **(c) DES-43:** o artefato diz "simulação", mas não há *script* nem saída no repositório. Os números conferem com o cálculo exato da cadeia (S1 1055/8 = 131,875; S2 500) e com os pesos de `Scenario.java` em `51a31f0`. Corrigir o comentário de fonte. O ritmo de 1 260 op/s por *thread* do diagnóstico não confere com os brutos da VM (3 131 a 3 229 em S1, 4 790 a 4 921 em S2), mas "frações de segundo" continua válido.
- **(d) Opcional:** CAND-086 (Alfatafta et al., OSDI 2020) para a definição de partição parcial.
- **Responsável:** Gestor (CAND-085/086); Escritor (texto); Bibliotecário (`.bib` depois da aprovação). **Prioridade:** Alta para (a) e (b). **Status:** Aberta.

> **Atualização de P-035 (2026-11-03, Decisão 020):** CAND-085 aprovada → REF-082 `jgroups_manual_5`, incluída em `monografia-atual/bibliografia.bib` (85 entradas). CAND-086 fica de fora. Falta só o Escritor aplicar (a), a versão **com citação** de (b) e o comentário de fonte de (c) em `Desenvolvimento.tex`; depois disso, P-035 fecha.

## P-036 — Fonte para a escala de perda N1 a N5 de F4 (2026-11-04)
- **Origem:** `monografia-atual/Desenvolvimento.tex:197` (DES-55) imprime N1..N5 = 1 %, 5 %, 20 %, 50 %, 99,9 %; auditoria `wt-gate-v4/docs/rastreabilidade-limiares-s5-s7-2026-11-04.md` itens 48 a 51.
- **Conferido:** N4 e N5 apoiados em `bailis_kingsbury_network_2014` p. 50 (Dean: "50% packet loss") e p. 52 (Freistil: "50%–100% packet loss"). DES-73 já cita p. 50-53.
- **Sem fonte no `.bib` atual:** N1, N2, N3. Candidatas CAND-087 (Paxson 1997), CAND-088 (Pingmesh 2015), CAND-089 (NetBouncer 2019) aguardam o Gestor. Nenhuma fonte dá os valores exatos: os níveis continuam escolha de engenharia dentro de faixas medidas.
- **Responsável:** Gestor (aprovar ou não); Escritor (redação); Bibliotecário (`.bib` após aprovação). **Status:** Aberta (só o Escritor).
> **Atualização 2026-11-04 (Decisão 021):** CAND-087..089 aprovadas → REF-083..085 (`paxson_packet_dynamics_1997`, `guo_pingmesh_2015`, `tan_netbouncer_2019`), no `.bib` (100 entradas). Falta o Escritor citar em DES-55 como faixa medida; depois disso, P-036 fecha.

## P-037 — Usos das fontes da Decisão 021 no texto e verificação da ação de modo degradado (2026-11-04)
- **Escritor:** `infinispan_server_15` (§12.2.2) em `Desenvolvimento.tex`:73/:97; `infinispan_rest_15` (§2.4.2) em :73/:143; `berger_iut_1982` em `Metodologia.tex`:229; `intel_i5_14600kf` em `Metodologia.tex`:335, escolhendo entre 125 W (base) e 181 W (turbo máxima) como potência máxima; figura `fig-datagrid-repl`: manter `redhat_datagrid_admin` enquanto a imagem do Data Grid 7.0 ficar, ou trocar a imagem pela Figura 1 do §2.1 de `infinispan_configuring_15`.
- **Senior/Worker:** verificar em experimento se `set-availability=DEGRADED_MODE` (REST §2.1.32, CLI §9.4) chamado num nó antes da troca de visão afeta só aquele nó ou o *cache* no *cluster* inteiro. Os guias não dizem que a ação é por nó.
- **Responsável:** Escritor; Senior/Worker. **Status:** Aberta.
