# Discussão Senior + Worker sobre o objetivo do projeto de software (2026-06-14)

> Discussão conduzida em oito rodadas pelos agentes de desenvolvimento (Senior e Worker) a pedido do Gestor, para responder duas perguntas: qual é o objetivo atual do projeto de software, e se esse objetivo está alinhado com o que está escrito no TCC após a rodada de reescrita de 2026-06-14 (itens E-01 a E-12).

## Rodada 1, Senior declara o objetivo atual do projeto de software

**Senior:** o objetivo declarado em `tcc-desenvolvimento/docs/planejamento-inicial.md` (2026-05-25) é:

> "Construir o artefato experimental descrito no Capítulo 4 da monografia, capaz de **(a)** instanciar um *cluster* Infinispan em três nós com replicação assíncrona, **(b)** executar um *workload* parametrizado que gera as operações de sessão O1 a O7 sobre os *caches* `sessions` e `counters`, **(c)** injetar de forma reproduzível as falhas F1 (*crash* silencioso) e F3 (atraso e *jitter* de mensagens), **(d)** coletar latência por operação e taxa de violação dos invariantes I1 a I6 declarados no Cap. 3 §3.3.3, e **(e)** disponibilizar, lado a lado, a especificação TLA+ correspondente para *model checking* incremental. A meta para TCC-I é o ***baseline* parcial sob F1 e F3** previsto para as Semanas 9 e 10 do cronograma."

Esse texto foi escrito antes de o Senior saber que o *sandbox* não permite Podman nem `tc-netem`.

## Rodada 2, Worker confronta o objetivo declarado com o estado real

**Worker:** o objetivo declarado tem cinco entregas (a, b, c, d, e). O estado atual é:

| Entrega | Declarada | Estado real |
|---|---|---|
| (a) Cluster Infinispan em 3 nós DIST_SYNC + DIST_ASYNC | sim | Configuração pronta (`cluster/`), sem execução real do cluster no sandbox |
| (b) Workload com O1-O7 sobre `sessions`/`counters` | sim | Esqueleto + O1-O7 com `OpResult` + auditor I1-I6 + `LatencyRegistry`; falta Hot Rod real exercido, Zipfian (B-08), warm-up (B-09), CLI (B-10) |
| (c) Injeção F1 + F3 | sim | 0 %, bloqueado por Podman e `NET_ADMIN` |
| (d) Coleta de latência + taxa de violação | sim | `LatencyRegistry` e auditor prontos; coleta real depende de (a), (b), (c) |
| (e) Spec TLA+ + model checking incremental | sim | Completo e executado (cinco rodadas, contraexemplos para I1, I2, I5 e I6) |
| Meta TCC-I, baseline F1+F3 nas Sem 9-10 | sim | Não atingida, depende da máquina do autor |

A leitura é direta: a meta declarada não foi atingida, a entrega (e) foi atingida com mais profundidade do que o objetivo previa, as entregas (a), (b) e (d) estão em metade do caminho e (c) está zerada por restrição de ambiente.

## Rodada 3, Senior pergunta se o objetivo do software ainda é o que o TCC declara

**Senior:** sim, o TCC reescrito hoje declara (Cap. 4 e Apêndice B) que o objetivo do trabalho cobre cinco eixos: (1) modelo formal, (2) verificação por MC, (3) ambiente experimental + workload, (4) controlador heurístico, (5) comparação baseline vs controlador. Os itens (4) e (5) são reservados a TCC-II. Os itens (1) e (2) estão atingidos em TCC-I. O item (3) precisa ser dissecado: o que conta como "ambiente preparado", o cluster configurado (sim) ou o cluster operando sob falhas (não)?

## Rodada 4, Worker propõe que há desalinhamento entre objetivo declarado do software e objetivo real do TCC-I

**Worker:** sim, há desalinhamento. O documento `planejamento-inicial.md` foi escrito para alinhar com a leitura agressiva do cronograma (baseline F1+F3 nas Sem 9-10 como entregável de TCC-I). O Cap. 4 da monografia, reescrito em 2026-06-14, declara o que TCC-I efetivamente entrega:

> Cap. 4 §4.3 (reescrito): "A execução das semanas 9 e 10 do cronograma de TCC-I compreende duas atividades, com graus distintos de cobertura nesta entrega. A primeira, verificação formal por *model checking*, foi concluída. A segunda, *baseline* experimental sob falhas selecionadas, depende de permissões de sistema operacional indisponíveis no ambiente de execução desta etapa, e a sua condução é tratada ao final desta seção."

Isso significa que, a partir do texto consolidado, o objetivo do projeto de software para TCC-I deve ser realinhado para refletir o que de fato precisa ser entregue até o fechamento da disciplina, e o que entra em TCC-II.

## Rodada 5, Senior verifica se há contradição irreconciliável

**Senior:** não há contradição irreconciliável. O Cap. 4 §4.3 e §4.4 (reescritos) e o §5.3 (Conclusões Parciais, reescrito) já admitem oficialmente:

- Que o MC foi executado e produziu o catálogo inicial de contraexemplos.
- Que o programa de carga, o cluster e o pipeline de análise estão prontos.
- Que o baseline F1+F3 depende de ambiente fora do sandbox.
- Que o fechamento dessa entrega tem duas saídas: execução em infraestrutura local do autor antes do fim da disciplina, ou reposicionamento para o início de TCC-II.

O `planejamento-inicial.md` (frente prática) ainda diz "Meta TCC-I = baseline parcial F1+F3". Isto está desatualizado em relação ao texto reescrito hoje.

## Rodada 6, Acordo entre Senior e Worker sobre o objetivo real do projeto de software

**Senior + Worker (consenso):** o objetivo do projeto de software para TCC-I, a partir do texto consolidado da monografia, deve ser:

> **Construir e disponibilizar os artefatos executáveis e reproduzíveis que sustentam o Capítulo 4 da monografia, garantindo que:**
>
> **(1) a especificação TLA+ `SessionStore.tla` cubra as operações O1 a O7 e os invariantes I1 a I6 e seja verificada pelo TLC nas três configurações T21 da Tabela 1 (concluído);**
>
> **(2) o cluster Infinispan esteja parametrizado conforme T1 a T4, T15 e T16 da Tabela 1 e admita execução nos modos DIST_SYNC e DIST_ASYNC (concluído como configuração; execução real depende de Podman);**
>
> **(3) o programa de carga em Java sobre Hot Rod implemente O1 a O7 com auditor de invariantes (I1 a I4 imediato, I5 e I6 periódico) e instrumentação de latência por HdrHistogram (~55 % via B-05/06/07; faltam B-08 Zipfian, B-09 warm-up, B-10 CLI);**
>
> **(4) o pipeline de análise estatística produza p50/p95/p99, IC bootstrap e Mann-Whitney sobre os dados quando existirem (concluído e validado em dados sintéticos);**
>
> **(5) os scripts de injeção de F1 e F3 estejam escritos e revisados, com execução condicionada a ambiente com Podman e NET_ADMIN disponíveis (não escritos; a escrita é viável no sandbox, a execução não).**
>
> **A meta TCC-I é o conjunto (1) a (4) executado e os scripts (5) prontos para revisão; o baseline experimental F1+F3 com dados reais é entregável condicional, com duas saídas: execução pelo autor em infraestrutura própria com incorporação a esta monografia em revisão posterior, ou reposicionamento para a primeira etapa de TCC-II conforme o Apêndice B.**

## Rodada 7, Worker registra o que vai mudar na prática

**Worker:** a consequência operacional da nova formulação é:

1. **`docs/planejamento-inicial.md`** precisa ter o seu §"Objetivo do projeto prático" atualizado para a formulação acima.
2. **`README.md`** §"Estado atual" precisa ser atualizado de "feature/bootstrap-repo, bootstrap inicial em curso" para o estado real (sete PRs merged, um aberto, dois locais; cinco rodadas de TLC executadas; pipeline análise validado).
3. **Os PRs B-06 e B-07** precisam ser abertos a partir de `feature/workload-auditor` (atualmente empilhados localmente).
4. **B-08, B-09, B-10** são os próximos a executar no sandbox.
5. **B-11, B-12, B-13, B-19** são escritos aqui mas executados na máquina do autor.
6. **`docs/resumos-para-tcc/`** deve ser populado com um resumo curto por bloco fechado, para alimentar a redação final da monografia.

## Rodada 8, Senior fecha a discussão com pergunta aberta para o Gestor

**Senior:** restam três decisões do Gestor para que essa formulação seja oficial:

- **D-A:** aprovar a atualização de `docs/planejamento-inicial.md` para a formulação consolidada na Rodada 6, ou manter o documento original e tratar a divergência como limitação reportada apenas no Cap. 4 da monografia?
- **D-B:** autorizar a abertura dos PRs B-06 e B-07 (atualmente empilhados em `feature/workload-auditor`) para que o autor possa revisá-los e fazer merge antes do início de B-08, B-09 e B-10?
- **D-C:** confirmar se a execução do baseline F1+F3 vai ocorrer na máquina do autor antes da entrega de TCC-I (caminho 1 da Rodada 6) ou se já fica reposicionada para TCC-II (caminho 2). A escolha afeta diretamente como o Cap. 4 §4.3 deverá ser ajustado na versão final entregue à banca.

## Status

Discussão produzida pelos agentes Senior e Worker em 2026-06-14, após a rodada de reescrita E-01 a E-12 da mesma data. Pendente das três decisões do Gestor (D-A, D-B, D-C) listadas na Rodada 8.
