# Metodologia

> Fonte primária: seção *Metodologia* de [`Main-PPEC.tex`](../Main-PPEC.tex) **(Proposta atualizada, 2026-05-12)**.
>
> **Decisão 010 do Gestor (2026-05-12):** a metodologia adota **mapeamento sistemático da literatura** (Petersen et al. 2008 / REF-037) com elementos de relato PRISMA 2020 (REF-038), substituindo a Decisão 003 anterior (revisão narrativa + mini-revisão estruturada). O modelo prático em português é Neiva & Silva 2016 (REF-030). **Decisão 004 (suspensão de buscas) também é superada** para o escopo do mapeamento — buscas serão executadas em Sem 3-4 de TCC-I.

## Tipo de pesquisa

Pesquisa **aplicada**, de natureza **experimental e quantitativa**, com:
- componente de **mapeamento sistemático da literatura** (frente 1, principal em TCC-I);
- componente de **pesquisa de projeto** (construção e avaliação de artefatos: protótipo experimental em TCC-I, modelo formal + controlador heurístico em TCC-II);
- componente de **verificação formal** (*model checking*) — execução parcial em TCC-I (Sem 9-10), continuação em TCC-II.

## Frentes metodológicas

A Proposta atualizada organiza o trabalho em **duas frentes** (não mais "trilhas"):

### Frente 1 — Mapeamento sistemático da literatura

Conduzido conforme diretrizes para *systematic mapping studies* em computação \citeonline{petersen_mapping_2008}, com relato apoiado por elementos de PRISMA 2020 \citeonline{prisma_2020}. Apoio operacional: guia prático em português de Neiva & Silva \citeonline{neiva_silva_revisao_sistematica_2016}.

**Protocolo (a detalhar em TCC-I Sem 1-2):**
- **Questões de mapeamento:** quais invariantes de estado de sessão são tratados? quais modelos de falha são assumidos? quais mecanismos de reconciliação são empregados? quais métricas são reportadas?
- **Bases de busca:** ACM Digital Library, IEEE Xplore, Scopus, SpringerLink.
- **Strings de busca:** a definir, refinadas em Sem 1-2.
- **Critérios de inclusão/exclusão:** estabilizados em Sem 3-4.
- **Triagem:** título/resumo (Sem 3-4) → leitura completa (Sem 5-6).
- **Extração padronizada:** esquema de classificação a ser produzido em Sem 1-2 e refinado em Sem 5-6.
- **Resultado:** esquema de classificação + síntese descritiva que **justificará o recorte implementado** nas frentes formal e experimental (Sem 5-6).

### Frente 2 — Implementação e validação experimental

Recorte **definido pelos achados do mapeamento** (Sem 5-6 do TCC-I); execução parcial em TCC-I:

- **Ambiente:** Infinispan em cluster \citeonline{infinispan_docs_stable}; *workload* concorrente sobre chaves de sessão/identidade; *cross-site replication* apenas se o escopo incluir (decisão a confirmar após o mapeamento).
- **Detecção de violações:** checagens explícitas no cliente/serviço; coleta de métricas do cluster via APIs \citeonline{infinispan_metrics}.
- **Injeção de falhas:** cenários parametrizados — (i) *crash*/restart; (ii) latência/jitter; (iii) partição temporária; (iv) mudanças de topologia que induzam *state transfer*; (v) *multi-site* (condicional).
- **Trilha formal (subconjunto da Frente 2):** sistema de transições discretas, múltiplas réplicas, canal assíncrono abstrato, falhas como transições. *Model checking* com TLC ou alternativa equivalente \citeonline{yu_lamport_tla_modelchecking_1999}. Início da especificação em TCC-I (Sem 7-8); execução inicial em TCC-I (Sem 9-10).
- **Controlador heurístico:** **fora do escopo de TCC-I**; desenvolvimento e avaliação em TCC-II.

## Bases de busca

ACM Digital Library; IEEE Xplore; Scopus; SpringerLink. Documentação oficial (Infinispan, TLA+) como fontes primárias técnicas. Buscas executam em **Sem 3-4 do TCC-I**, conforme protocolo definido em Sem 1-2.

## Strings de busca (rascunhos a refinar em Sem 1-2)

- `("in-memory data grid" OR IMDG OR "distributed cache") AND ("asynchronous replication" OR replication) AND (consistency OR invariant OR "eventual consistency")`
- `("model checking" OR "TLA+" OR Alloy) AND (replication OR "distributed protocol" OR "fault tolerance")`
- `(failover OR "network partition") AND (replication OR "state transfer" OR rebalancing)`
- `(session OR "session state") AND (distributed OR replicated OR "in-memory") AND (consistency OR invariant)`

## Critérios de inclusão (rascunho)

- Trabalhos revisados por pares (conferências/periódicos reconhecidos) **ou** obras clássicas/seminais;
- Documentação técnica oficial das tecnologias usadas (Infinispan, TLA+);
- Relevância direta às questões de mapeamento;
- Idioma português ou inglês; texto completo acessível.

## Critérios de exclusão (rascunho)

- Resumos sem texto completo;
- Fora do tema;
- Duplicatas (manter o veículo mais completo);
- *White papers* comerciais sem conteúdo verificável (admitidos só como apoio);
- Trabalhos sem revisão por pares **como base teórica principal** (relatórios técnicos do Jepsen, por exemplo, entram como apoio empírico, não como fundamentação teórica).

## Processo de seleção dos trabalhos

Triagem por título/resumo (Sem 3-4) → leitura completa dos selecionados (Sem 5-6) → registro em [`05-referencias-candidatas.md`](05-referencias-candidatas.md) (formato "Referência candidata") → validação do Gestor → migração das aprovadas para [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md). **Registrar o número de itens em cada etapa** para reportar segundo PRISMA 2020 (fluxograma).

## Processo de análise (esquema de classificação)

Dimensões propostas (a refinar em Sem 1-2 e validar em Sem 5-6 após calibragem):
- **D1 — Modelo de consistência alvo** (forte, sequencial, causal, sessão, eventual, SEC/CRDT).
- **D2 — Modelo de falhas** (crash, omissão, partição, bizantino).
- **D3 — Mecanismo de reconciliação** (LWW, aplicação, CRDT, *vector clocks*, consenso).
- **D4 — Ferramenta/abordagem formal** (TLA+/TLC, Alloy, SPIN, Coq, nenhuma).
- **D5 — Validação empírica** (sim — escala, workload; não).
- **D6 — Domínio de aplicação** (sessão/autenticação, KV genérico, banco de dados, mensageria, outro).
- **D7 — Métricas reportadas** (latência, p95/p99, taxa de violação, tempo de recuperação, etc.).

## Limitações metodológicas

- *Model checking* é limitado pelo tamanho do espaço de estados explorável; o modelo formal será necessariamente uma **abstração**.
- Validação experimental cobre **cenários parametrizados** de falha, não o espaço completo de falhas reais.
- Resultados experimentais dependem do ambiente; reprodutibilidade buscada via *scripts*.
- O mapeamento sistemático é restrito a quatro bases — pode haver viés de cobertura (literatura cinza, *workshops*, blogs técnicos).
- **Ameaças à validade** (interna, externa, de construto, conclusão) serão tratadas em seção própria do Cap. 3 — P-007.

## Referências metodológicas (oficiais)

- **REF-030** Neiva & Silva (2016) — guia prático em PT.
- **REF-037** Petersen et al. (2008) — *systematic mapping*.
- **REF-038** PRISMA (2020) — relato.

## Ângulo de sustentabilidade computacional (novo na Proposta)

A Introdução da Proposta atualizada inclui um parágrafo sobre **computação sustentável**: reduzir violações e estabilizar comportamento sob falhas pode reduzir reexecuções e retrabalho computacional, com impacto em consumo de recursos \citeonline{turner_carbon_footprint_2023, ft_hpc_energy_2024}. Esse ângulo entra no Cap. 1 (motivação) e pode informar uma discussão secundária em Cap. 5 (eventualmente). REF-041 (Lemos *et al.* 2025, ERAD-RS — trabalho do orientador) está disponível para citação como referência do grupo de pesquisa local.
