# Visão Geral do TCC

> Documento de memória mantido pelo **Bibliotecário Acadêmico**. Todos os agentes devem
> consultá-lo antes de iniciar qualquer tarefa. Construído a partir do arquivo base
> `Main-PPEC.tex` (Proposta de TCC, Unipampa).
> Última atualização: 2026-05-11 — inicialização do sistema multiagente.

## Tema

**Verificação formal de invariantes de sessão em *data grids*: replicação assíncrona, *failover* e controle orientado a SLO.**

- **Autor (Orientando):** Francesco Pagani Galvão
- **Orientador:** Prof. Dr. Leonardo Bidese de Pinho
- **Instituição:** Universidade Federal do Pampa (Unipampa)
- **Curso:** Bacharelado em Engenharia de Computação
- **Tipo do documento atual:** *Proposta* de Trabalho de Conclusão de Curso — **não** a monografia final (TCC-1/TCC-2).

## Área de pesquisa

Sistemas distribuídos; armazenamento de dados em memória (*In-Memory Data Grids* — IMDG / *data grids*); métodos formais (especificação e *model checking*); tolerância a falhas e *failover*; engenharia de desempenho (latência de cauda, SLO); sistemas autoadaptativos / controle por realimentação (heurístico, baseado em regras).

## Contexto

Sistemas distribuídos que atendem requisições autenticadas mantêm estados de curta duração associados a identidades (estado de sessão, metadados de renovação, contadores de tentativas, marcas de bloqueio). Em arquiteturas com múltiplas instâncias e balanceamento de carga, esse estado é frequentemente externalizado para um *In-Memory Data Grid* (data grid), que oferece armazenamento chave-valor em memória com particionamento, replicação e realocação de dados quando a composição do cluster muda. A replicação pode ser **síncrona** (confirmação dependente de réplica/quórum, sujeita ao *Round-Trip Time* — RTT) ou **assíncrona** (confirmação local com propagação posterior — reduz dependência do RTT na escrita, mas admite estados defasados entre réplicas e reordenação efetiva de eventos observados por nós distintos). Sob *failover*, partições de rede, *rebalancing* e *state transfer*, subconjuntos do sistema podem operar com visões divergentes do estado, exigindo reconciliação posterior (resolução de conflitos ou convergência eventual).

## Problema de pesquisa

Sob replicação assíncrona e *failover* em *data grids*, é possível que **invariantes de domínio do estado de sessão** sejam violados — por exemplo: reversão de invalidação (sessão volta a "válida" após *logout*), regressão de contadores monotônicos, ou inconsistência de bloqueio (identidade bloqueada validada como ativa). O problema central: **como especificar formalmente esses invariantes, detectar suas violações (por verificação formal e por experimentação) e mitigar sua ocorrência — controlando também métricas de latência sob falhas — por meio de um mecanismo de decisão heurístico?**

## Hipótese ou questão norteadora

**Questão norteadora:** um controlador heurístico que observa métricas e sinais do sistema e seleciona políticas/ações de mitigação pode reduzir a taxa de violações de invariantes de sessão e controlar percentis de latência (p95/p99) sob cenários de falha, em comparação com uma configuração estática equivalente.

> *(Pendência P-001: confirmar com o Gestor se o trabalho declara uma hipótese formal a testar ou apenas a questão norteadora acima — o arquivo base não declara hipótese explícita.)*

## Objetivo geral

Especificar e verificar invariantes do estado de sessão sob replicação assíncrona e *failover* em *data grids*, e avaliar um mecanismo de decisão heurístico que modifique políticas ou ações operacionais visando reduzir a ocorrência de violações desses invariantes e controlar métricas de latência sob falhas.

## Objetivos específicos

1. Definir um modelo formal do estado de sessão e das operações relevantes (login, validação, renovação, invalidação e atualização de contadores e *flags*), incluindo explicitamente atrasos e reordenação de propagação de atualizações, e eventos de falha ou recuperação.
2. Declarar invariantes de segurança, correção e propriedades de convergência, e verificar essas propriedades via *model checking*, registrando contraexemplos quando existirem.
3. Estabelecer uma arquitetura experimental com Infinispan em cluster, com suporte a alterações de topologia e cenários de replicação, instrumentando um *workload* que gera operações concorrentes e detecta violações de invariantes.
4. Implementar um controlador heurístico que, com base em taxas e parâmetros observáveis, selecione entre políticas e aplique ações de mitigação.
5. Comparar experimentalmente o modelo estático com o modelo acrescido do controlador em cenários com injeção de falhas, quantificando taxa de violações, tempo de recuperação e convergência e estatísticas de latência.

## Metodologia resumida

Duas trilhas paralelas:
1. **Especificação e verificação formal** — modelagem como sistema de transições discretas (réplicas; canal de replicação assíncrona com atraso/reordenação/perda; falhas e partições; *state transfer* abstrato), com verificação por *model checking* (TLC/TLA+ ou equivalente) e análise de contraexemplos.
2. **Implementação e validação experimental** — cluster Infinispan; *workload* de operações tipo sessão; instrumentação de latência (incl. p95/p99) e checagens programáticas de invariantes; injeção de falhas parametrizada (crash/restart; latência/jitter; partição; *rebalancing*/*state transfer*; multi-site); comparação *baseline* estático vs. *baseline* com controlador heurístico (regras determinísticas com histerese e limites de taxa de mudança, para evitar *flapping*).

Detalhes em [`02-metodologia.md`](02-metodologia.md).

## Estrutura prevista

O arquivo base (`Main-PPEC.tex`) é a **Proposta** (Introdução; Objetivos; Metodologia; Cronograma; Referências; Assinaturas). **Decisão do Gestor (001/002):** desenvolver a **monografia**, começando por **TCC-1**, na classe **`abntex2`** (esqueleto em `Main-Monografia.tex`). Estrutura de capítulos adotada (liberada para TCC-1; ajustes finos pelo Gestor — ver [`03-estrutura-capitulos.md`](03-estrutura-capitulos.md)):

1. Introdução
2. Fundamentação Teórica (+ Trabalhos Relacionados)
3. Metodologia
4. Desenvolvimento (modelo formal + arquitetura experimental + controlador heurístico) — *majoritariamente TCC-2*
5. Resultados e Discussão — *TCC-2 (parciais em TCC-1)*
6. Conclusão — *TCC-2 (conclusões parciais em TCC-1)*

**Foco de TCC-1:** Caps. 1, 2 (esqueleto + trechos fundamentáveis pelas referências herdadas), 3, e modelo formal v1; *baseline* piloto.

## Status atual do trabalho

- [x] Arquivo base (`Main-PPEC.tex` — Proposta de TCC) lido e analisado.
- [x] Estrutura de memória `memoria-tcc/` criada e preenchida.
- [x] Lacunas teóricas, metodológicas e estruturais identificadas (ver [`07-pendencias.md`](07-pendencias.md)).
- [x] Decisões do Gestor 001–004 registradas (escopo = monografia/TCC-1; LaTeX = `abntex2`; revisão = narrativa + mini-revisão estruturada; **buscas bibliográficas só sob autorização explícita**).
- [x] Esqueleto `Main-Monografia.tex` (`abntex2`) criado, com Cap. 1 em **versão 0.1** ([Escritor]) e **1ª revisão** registrada ([Revisor]).
- [ ] Biblioteca oficial: 9 referências herdadas (em uso); confirmação formal do Gestor ainda em aberto (P-014). **Nenhuma busca de novas referências autorizada por ora.**

## Ponto de retomada (atualizado 2026-05-11 — fim da sessão de inicialização)

> O Gestor encerrou a sessão e pediu para registrar tudo. **Estado salvo.** Para retomar:

**Decisões do Gestor pendentes (nenhuma resposta dada — retomar por aqui):**
1. **Aprovar o Cap. 1 v0.1** (escrito em `Main-Monografia.tex`, `\chapter{Introdução}`; revisão em [`revisoes/revisao-capitulo-01.md`](revisoes/revisao-capitulo-01.md)) — aprovar como rascunho? ler/ajustar antes? refazer com mudanças?
2. **Escolher o próximo passo** — opções colocadas ao Gestor: (a) Cap. 3 Metodologia v0.1 *(recomendado — base sólida no arquivo base, sem depender de fontes novas)*; (b) Cap. 2 esqueleto + trechos cobertos pelas 9 herdadas; (c) modelo formal v1 (TLA+/PlusCal); (d) revisar a memória com o Gestor.
3. **P-008** — revisar/aprovar a estrutura de capítulos ([`03-estrutura-capitulos.md`](03-estrutura-capitulos.md)).
4. **P-014** — confirmar as 9 referências herdadas como oficiais (e decidir o destino de `infinispan_intro`, não citada).
5. **P-004a** — quais temas entram na mini-revisão estruturada.
6. **P-015** — autorizar (ou não) a rodada de busca bibliográfica. Enquanto não autorizada, a Justificativa do Cap. 1 (e o miolo do Cap. 2) ficam com marcadores `% [FONTE NECESSÁRIA]`.
7. **P-019** — fornecer dados de capa/folha de rosto (campus, tipo de trabalho, ano, banca) e, ao final, o resumo/abstract definitivos.

**Onde estão as coisas:** monografia em `/workspace/Main-Monografia.tex` (não compilada — não há LaTeX no ambiente); Proposta intacta em `/workspace/Main-PPEC.tex`; toda a memória em `/workspace/memoria-tcc/`; decisões em [`06-decisoes-do-gestor.md`](06-decisoes-do-gestor.md); pendências P-001..P-019 em [`07-pendencias.md`](07-pendencias.md).
