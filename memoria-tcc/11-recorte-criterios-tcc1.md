# Recorte Detalhado e Critérios de Avaliação — TCC-1 (entregável de Sem 5-6 na Proposta atualizada)

> ## ⚠️ DOCUMENTO REPOSICIONADO (Decisão 010, 2026-05-12)
>
> Produzido inicialmente em 2026-05-12 como entregável de Sem 1-2 com base em versão desatualizada da Proposta. Na **Proposta atualizada** (Quadro 1 — Cronograma de TCC-I, 12 semanas/6 etapas quinzenais), Sem 1-2 é "pergunta + protocolo do mapeamento sistemático", e o **recorte do protótipo** (operações de sessão, tipos de falha, invariantes, critérios de avaliação) passa a ser o **entregável de Sem 5-6**, **derivado dos achados do mapeamento sistemático**.
>
> **Status do conteúdo:** **válido como rascunho preliminar do recorte** (operações O1-O7, falhas F1-F4, invariantes I1-I6 e métricas M1-M7 permanecem aprovados pelo Gestor — Decisões 007 e 008). **Será revisitado em Sem 5-6** à luz dos achados do mapeamento e potencialmente refinado. Conteúdo alimenta o Cap. 3 (Metodologia), Cap. 4 (modelo formal inicial em Sem 7-8 de TCC-I), Cap. 5 (resultados parciais em Sem 9-10).

> Entregável **de Sem 5-6** do TCC-I conforme Quadro 1 da Proposta atualizada (`Main-PPEC.tex` linhas 90-92, Quadro 1): *"Seleção por leitura completa, extração e classificação"* + *"Síntese do mapeamento e recorte do protótipo"*.
>
> **TCC-I = 12 semanas**, 6 etapas quinzenais. Modelo formal **inicial** começa em Sem 7-8 (TCC-I). *Baseline* experimental inicial em Sem 9-10 (TCC-I). Maturação (v1/v2), baselines completos e controlador heurístico ficam em **TCC-II** (12 semanas).
>
> Este documento **estende** [`01-tema-problema-objetivos.md`](01-tema-problema-objetivos.md) e é **insumo direto** para o Cap. 3 (Metodologia), Cap. 4 (modelo formal inicial), Cap. 5 (resultados parciais).
>
> **Status:** draft do **Escritor** (2026-05-12), pendente de validação do Bibliotecário (consistência com fontes), do Revisor (coerência e ABNT) e do **Gestor** (aprovação final — fecha P-003, P-016 e abre caminho para Cap. 3 — métricas e Cap. 4 — invariantes formais).

---

## 1. Operações de sessão (núcleo do protocolo)

O sistema sob estudo é o **núcleo de gerência de estado de sessão** — não um produto de autenticação completo. Sete operações compõem o conjunto modelado:

| ID | Operação | Entrada | Efeito esperado no estado | Códigos de retorno |
|---|---|---|---|---|
| O1 | `Login(id, cred)` | identidade `id`, credencial `cred` | cria nova sessão `s` para `id`, com estado lógico `Valid`, versão inicial, contadores zerados | `OK(s)`, `BLOCKED(id)`, `ERR(cred)` |
| O2 | `Validate(s)` | sessão `s` | retorna estado lógico atual de `s` | `VALID`, `INVALID`, `UNKNOWN` (sessão não encontrada na réplica consultada) |
| O3 | `Logout(s)` | sessão `s` | marca `s` como `Invalid` com motivo `logout`; idempotente | `OK`, `ALREADY_INVALID` |
| O4 | `IncrementFailure(id)` | identidade `id` | incrementa contador de tentativas falhas de `id`; se ≥ limite, marca `id` como `Blocked` | `COUNTED(n)`, `BLOCKED(id)` |
| O5 | `ResetFailures(id)` | identidade `id` | zera contador de `id` (após sucesso de login dentro da janela) | `OK` |
| O6 | `Block(id, motivo)` | identidade `id`, motivo administrativo | marca `id` como `Blocked`; invalida todas as sessões ativas associadas | `OK`, `ALREADY_BLOCKED` |
| O7 | `Unblock(id)` | identidade `id` | remove a marca `Blocked` (decisão administrativa) | `OK`, `NOT_BLOCKED` |

**Observações:**
- O conjunto cobre o ciclo de vida do par (`id`, `s`) e os pontos onde *invariantes* podem ser violados sob replicação assíncrona.
- A operação `Renew(s)` (extensão de TTL) **não é modelada formalmente em TCC-1** — entra como trabalho futuro (TCC-2) ou no apêndice se o Gestor preferir incluí-la.
- Cada operação registra, na trilha experimental, *timestamp* de início/fim, nó atendente e código de retorno, alimentando as métricas de latência e violação.

---

## 2. Tipos de falha modelados

Cinco classes de falha são consideradas, parametrizáveis para reprodução experimental:

| ID | Falha | Parâmetros | Janela | Recuperação |
|---|---|---|---|---|
| F1 | *Crash* silencioso de nó | `n` nós afetados (1 ≤ `n` < `N-quórum_min`) | duração `Δ_crash` | reentrada do nó com *state transfer* |
| F2 | Partição de rede (2 sub-clusters) | maior componente `n_M`, menor `n_m` | duração `Δ_part` | reconciliação com política do sistema |
| F3 | Atraso/jitter de mensagens | percentil/distribuição (ex.: +50 ms p99 lognormal) | janela contínua | imediata (sem ação) |
| F4 | *State transfer* induzido | nó entra/sai do cluster | janela do *rebalancing* | implícita |
| F5 | (Multi-site) Indisponibilização temporária de site | site `B` *offline*; site `A` continua | duração `Δ_site` | retorno + sincronização *cross-site* |

**Observações:**
- F1, F2, F3 são **obrigatórios** para o *baseline* de TCC-1 (Semanas 19-20 do cronograma).
- F4 é **derivado** de F1 e da configuração de cluster — não é falha em si, mas evento operacional consequente.
- F5 é **opcional** em TCC-1 e **necessário** apenas se o escopo incluir *cross-site replication* (REF-007). **Decisão pendente do Gestor:** TCC-1 cobre F5 ou só TCC-2? *(Sub-pendência P-016a.)*
- Falhas bizantinas (mensagens corrompidas ou maliciosas) estão **fora de escopo** — ver Seção 5.

---

## 3. Invariantes-alvo (Cap. 4 — modelo formal v1)

A formalização precisa virá no modelo (TLA+ ou equivalente — P-017). Aqui registra-se o **conjunto mínimo de TCC-1**:

> **Nota de escopo TCC-I × TCC-II:** os invariantes I1..I6 abaixo são **definidos e justificados em TCC-I** (insumo escrito para o Cap. 4 — Desenvolvimento — e referenciados em Cap. 1.2). A **formalização operacional e verificação por *model checking*** ocorre em TCC-II (Sem 15-18 do cronograma).

### 3.1 Invariantes de segurança (safety)

- **I1 — Irreversibilidade de invalidação por *logout*.**
  Se em algum momento `s.estado = Invalid ∧ s.motivo_invalid = logout`, então em nenhum estado futuro `s.estado = Valid`.

- **I2 — Consistência de bloqueio.**
  Se `id.estado = Blocked`, então para toda sessão `s` com `s.id = id`, em nenhum estado futuro `Validate(s)` retorna `VALID` enquanto `id.estado = Blocked`.

- **I3 — Monotonicidade local do contador de falhas.**
  Em qualquer execução restrita a operações de um único nó, `id.contador_falhas` é não-decrescente entre `Login`/`IncrementFailure` consecutivos *até* uma operação `ResetFailures` ou `Unblock` que o zere explicitamente. *(I3 é declarado para o estado local; sob replicação assíncrona, a versão global de `id.contador_falhas` pode aparecer regredida em uma réplica — isso é o sintoma observável a contabilizar como violação operacional, não falha de I3 em si.)*

- **I4 — Unicidade de motivo de invalidação.**
  Para qualquer `s`, a partir do momento em que `s.motivo_invalid` é fixado (`logout`, `expirou`, `admin_block`), ele não é reescrito por outra causa em estados futuros.

### 3.2 Invariantes de convergência (eventual consistency)

- **I5 — Convergência do estado lógico.**
  Em qualquer execução em que, a partir de algum ponto `t`, não ocorram novas operações nem novas falhas e todos os canais entreguem todas as mensagens pendentes, existe `t' ≥ t` tal que todas as réplicas concordam quanto a `s.estado` e `id.estado` para todo `s, id`.

- **I6 — Convergência do contador (após estabilização).**
  Sob a mesma premissa de I5, todas as réplicas eventualmente concordam quanto a `id.contador_falhas`. *(Importante para evidenciar o que conta como violação operacional vs. comportamento esperado de consistência eventual.)*

### 3.3 Invariantes não inclusos em TCC-1

- **Unicidade de sessão ativa por identidade.** A Proposta original menciona; o Escritor recomenda **excluir de TCC-1** porque depende de decisão de domínio (multi-device login). *(Decisão pendente — P-016b.)*
- **Validade de TTL/expiração.** Excluído por consequência da exclusão de `Renew(s)`.

### 3.4 Mapeamento invariante → tipo de falha → operação

| Invariante | Falha que tende a violar | Operação envolvida |
|---|---|---|
| I1 | F2 (partição reconciliada) | O3 ↔ O1/O2 concorrentes |
| I2 | F1, F2 | O6, O2 concorrentes |
| I3, I6 | F1, F2 | O4, O5 concorrentes |
| I4 | F2 (reconciliação) | O3, O6 concorrentes |
| I5 | F2 (extrema), F4 | qualquer |

---

## 4. Critérios de avaliação

### 4.1 Métricas primárias (operacionais)

| Métrica | Unidade | Como coletar | Onde aparece |
|---|---|---|---|
| **M1** — Latência por operação | ms (p50, p95, p99, p99.9) | *timestamp* início/fim no cliente do *workload* | Cap. 3 (métricas), Cap. 5 |
| **M2** — Taxa de violação por invariante | violações/min por `I_k` | checagem programática no cliente após cada `O2`/`O4` | Cap. 5 |
| **M3** — Tempo de recuperação | s (mediana, p95) | janela entre fim da falha e estabilização da taxa de erro | Cap. 5 |
| **M4** — Tempo de convergência | s (mediana, p95) | janela entre fim da falha e desaparecimento de divergências entre réplicas | Cap. 5 |
| **M5** — Taxa de erro | % requisições com código `ERR` (não-violação) | log do *workload* | Cap. 5 |

### 4.2 Métricas secundárias (sistêmicas)

- **M6** — Uso de CPU/memória/heap por nó (via Infinispan Metrics — REF-008).
- **M7** — Métricas de *cluster* Infinispan: mensagens trocadas, eventos de *rebalancing*, *state transfer* concluído.
- M6 e M7 servem de *contexto*; entram em apêndice ou figuras secundárias.

### 4.3 Propriedades formais a verificar (Trilha 1 — execução em TCC-II, Sem 15-18)

| Propriedade | Como verificar | Saída esperada |
|---|---|---|
| **P1** — Cada `I_k` é invariante do modelo? | TLC com `INVARIANT I_k` | OK ou contraexemplo |
| **P2** — Existe execução em que I1 é violada? | TLC com `PROPERTY ◇¬I1` | espera-se OK (ou seja, existe), para validar que o modelo *captura* o risco |
| **P3** — Eventual convergência (I5, I6) | TLC com `<> consensus_predicate` sob *fairness* fraca | OK ou contraexemplo |

> Em TCC-I, P1..P3 entram no Cap. 3 (Metodologia) e no Cap. 4 como **propriedades a verificar** — sem execução. A execução é Sem 15-18 (TCC-II).

### 4.4 Critérios de comparação *baseline* vs. controlador (TCC-2 — registrado aqui por completude)

Para cada cenário de falha (F1–F5):

- **C1** — Redução estatisticamente significativa de M2 (taxa de violação) com o controlador vs. *baseline*.
- **C2** — Não-degradação significativa de M1 (latência p95/p99) sob o controlador.
- **C3** — Tempo de recuperação M3 não piora em > 20% (limite a ser refinado em TCC-2).
- **C4** — Decisões do controlador são auditáveis (log de cada mudança de perfil com observáveis que a motivaram).

### 4.5 Critérios de comparabilidade entre execuções

- **Repetibilidade:** mínimo de **30 repetições** por cenário (refinar em P-011).
- **Calibração:** volume de carga ajustado em piloto (Semana 11 do cronograma).
- **Reprodutibilidade:** *scripts* de inicialização/encerramento versionados; arquivos de configuração de cluster congelados por cenário.
- **Análise estatística:** testes não-paramétricos (Mann-Whitney/Wilcoxon) e intervalos de confiança bootstrap para p95/p99 — *detalhes em P-011, após CAND-020*.

---

## 5. Fora de escopo (atualizado para TCC-1)

Confirmando e refinando o que já estava em [`01-tema-problema-objetivos.md`](01-tema-problema-objetivos.md):

- Implementação de produto pronto para produção (autenticação, OAuth, federação).
- **Falhas bizantinas** (mensagens corrompidas/maliciosas) — protocolos do Infinispan assumem canal honesto.
- **Prova dedutiva mecanizada** (Coq, Isabelle) — usa-se *model checking* somente.
- **Aprendizado de máquina** na política de decisão — o controlador é determinístico/baseado em regras (com histerese e *rate limit*).
- Outros *data grids* além de Infinispan (Hazelcast, Apache Ignite, Redis Cluster, Apache Geode) — trabalho futuro.
- **Segurança criptográfica** de tokens/sessões; modelagem de adversário; *replay attacks*.
- **`Renew(s)` / TTL** (justificativa: aumentaria espaço de estados sem ganho conceitual no TCC-1; pode entrar em TCC-2).
- **Unicidade de sessão ativa por identidade** (depende de decisão de domínio — P-016b).

---

## 6. Pendências derivadas

Registradas / atualizadas em [`07-pendencias.md`](07-pendencias.md):

- **P-016a** *(nova)* — Incluir F5 (multi-site) em TCC-1? *(Decisão do Gestor.)*
- **P-016b** *(nova)* — Incluir invariante de "unicidade de sessão ativa por identidade"? *(Decisão de domínio do Gestor.)*
- **P-016** *(em andamento)* — Conjunto formal de invariantes (I1–I6) proposto; aguarda aprovação do Gestor.
- **P-003** *(encaminhada)* — Fora de escopo detalhado nesta seção 5; aguarda aprovação.
- **P-011** *(em andamento)* — Plano de análise estatística depende de CAND-020 (Wohlin et al., *Experimentation in SE*) — ainda candidata, não aprovada.
- **P-017** *(aberta)* — Escolha entre TLA+/TLC, Alloy ou outro (P3 acima depende disso).

---

## 7. Mapeamento desta delimitação → capítulos

- **Cap. 1 (Introdução)** — usa Seções 1, 2 e 3 deste documento para precisar o problema (1.2) e o escopo (1.2 ou 1.3 conforme a decisão de estrutura).
- **Cap. 3 (Metodologia)** — usa Seções 4.1, 4.2, 4.4, 4.5 (métricas, critérios, análise estatística).
- **Cap. 4 (Desenvolvimento — modelo formal)** — usa Seções 1, 2, 3 (formalização) e 4.3 (propriedades a verificar).
- **Cap. 5 (Resultados)** — usa Seções 4.1, 4.2 como esqueleto das tabelas/figuras.
- **Apêndice A (Plano de TCC-2)** — usa Seção 4.4 (critérios *baseline* vs. controlador).

---

## 8. Próximos passos no cronograma

Concluídas as Semanas 1-2 (este documento), as Semanas 3-4 trazem a **revisão dirigida sobre replicação assíncrona, partições, failover e reconciliação**. Insumos disponíveis:
- 9 referências oficiais (REF-001..REF-009).
- 25 candidatas pendentes de aprovação do Gestor (CAND-022..046; ver [`10-material-gestor-analise.md`](10-material-gestor-analise.md)).
- Lacunas conhecidas em F (chaos/Jepsen) e H (sessão/OWASP) — buscas externas ainda suspensas (Decisão 004).

**Bloqueio principal:** sem a aprovação das CAND novas, a fundamentação teórica do Cap. 2 ficará majoritariamente sobre os 9 herdados, o que é insuficiente para cobrir os temas das Semanas 3-4 e 5-6 com profundidade adequada à mini-revisão estruturada (Decisão 003).

---

## Revisão (1ª rodada — Revisor, 2026-05-12)

**Coerência interna:**
- ✅ Operações (Seção 1) e tipos de falha (Seção 2) cobrem todos os invariantes (Seção 3) — matriz da Seção 3.4 fecha.
- ✅ Métricas (4.1) instrumentam todos os invariantes operacionalmente verificáveis (M2 ↔ I1..I4; M4 ↔ I5..I6).
- ✅ Propriedades formais (4.3) cobrem segurança (P1, P2) e *liveness* (P3); P2 é um teste de modelo (sanity check), o que é boa prática.
- ⚠️ A operação O5 (`ResetFailures`) tem condição "após sucesso de login dentro da janela" não totalmente precisada — recomendo o Escritor definir a janela (TTL do contador) antes do modelo formal, ou marcar como parâmetro do modelo.
- ⚠️ I3 (monotonicidade local) é uma propriedade de "uma única réplica"; sob replicação isto pode confundir o leitor. Sugiro renomear para "I3 — Monotonicidade local do contador (por réplica)" e ressaltar que a propriedade global é I6 (convergência do contador).

**Coerência com a Proposta (`Main-PPEC.tex`):**
- ✅ Mapeamento `invariante ↔ falha ↔ operação` (3.4) está consistente com o discurso da Proposta sobre os três tipos de falha (crash/restart, partição, latência).
- ✅ Métricas (M1–M5) batem com as métricas declaradas na Proposta (latência p95/p99, taxa de violações, tempo de recuperação).
- ✅ Critérios C1–C4 antecipam a comparação *baseline* vs. controlador prevista para TCC-2 — boa prática registrá-los agora.

**Coerência com decisões e pendências:**
- ✅ Fora de escopo (Seção 5) refina P-003 com itens concretos.
- ✅ P-016a e P-016b ficam explicitamente registradas como decisões pendentes do Gestor.
- ✅ Dependências em P-011 (estatística) e P-017 (ferramenta de MC) corretamente sinalizadas.

**Pendência:** o Bibliotecário deve verificar, ao receber autorização para a próxima rodada de busca, se algum trabalho relacionado (em particular CAND-036 — Nguyen et al. 2019) define um conjunto de invariantes incompatível com I1–I6, exigindo justificativa explícita das escolhas do TCC.

**Status:** **Aprovado pelo Revisor** — pendente de aprovação do Gestor (P-003, P-016, P-016a, P-016b).

---

## Refino pós-mapeamento, Sem 5-6 (2026-05-14)

A síntese do mapeamento sistemático ([`16-sintese-mapeamento-tcc1.md`](16-sintese-mapeamento-tcc1.md)) gerou os refinos a seguir, dentro do recorte aprovado pela Decisão 007.

O conjunto O1 a O7, F1 a F4, I1 a I6 e M1 a M7 permanece como definido. A classificação automática dos 165 estudos selecionados confirma a pertinência das escolhas anteriores em quatro pontos. A escassez de trabalhos que reportam a taxa de violação de invariantes como métrica primária (D7) sustenta M2 como contribuição do TCC. A predominância das categorias formais TLA+, Coq e variantes (D4) confirma a pendência P-017 como decisão a ser tomada em Sem 7-8, com critério de escolha apoiado pelos achados do mapeamento. A presença marginal de modelos bizantinos na amostra (D2) reforça a exclusão de falhas bizantinas mantida no escopo. A presença de trabalhos com leituras com garantia de sessão (read-your-writes, monotonic reads) em KV stores causalmente consistentes, como Chapar e Cure, sugere uma variante opcional de configuração do cliente para TCC-II, sem alteração no recorte de TCC-I.

A próxima revisão deste documento incorporará a numeração definitiva dos estudos primários incluídos no mapeamento (MS-001 em diante) e as referências citadas no Capítulo 2, conforme aprovação individual do Gestor.
