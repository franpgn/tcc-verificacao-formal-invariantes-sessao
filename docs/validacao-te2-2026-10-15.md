# Validação V-E2 (T-E / B-33, parte E2: cenário S3) — 2026-10-15

> Senior. Entrega validada: branch `feature/b33-instrumentacao-particao`, worktree `wt-te`, HEAD `7ca5dfa`, base `8e2e815`.
> Plano: `docs/plano-fechamento-etapa-formal-2026-10-15.md` (T-E item 5, Q-10, Q-13). Validação anterior: `docs/validacao-te1-2026-10-15.md` (C-2 a C-5).
> Nenhum código foi alterado. Todas as mutações abaixo foram aplicadas em `wt-te` e revertidas com `git checkout`; o worktree terminou limpo. Sem push.

## Veredito

**APROVADA COM RESSALVAS.** T-E pode ser considerada **APROVADA PLENA (E1 + E2)** para integração **local**, com as ressalvas R-1 a R-6 registradas como condições de T-B/T-F (nenhuma exige mudança de código antes da integração).

## 1. Verificação independente de testes

| Item | Reprodução |
|---|---|
| Worktree | `git status` limpo em `7ca5dfa` antes e depois das mutações |
| `mvn -f workload/pom.xml test` | **184 executados, 0 falhas, 0 erros, 2 ignorados** (BUILD SUCCESS); os 2 são `ClassificadorMensagemRealTest` (esqueleto C-2) |
| `python -m unittest` em `analysis/` | **9 testes, OK** |
| Saída informativa reproduzida | `CUSTO-BLOCK local sessoes=100000 block_ms=22`; `500000 -> 114 ms`; `serializacao 100000 sessoes 530 B/sessão, serializar 393 ms, desserializar 1674 ms`; `PADROES-HOTROD socketTimeout=2000 connectionTimeout=2000 maxRetries=3` |

## 2. Diff `8e2e815..HEAD`

- `IdentityState`, `SessionState`, `Scenario`, `KeyGenerator` e `spec/`: **zero linhas alteradas** (`git diff --stat` vazio para esses caminhos).
- `SessionOps` (E2): somente um construtor package-private novo (com `RemoteCacheManager`, mapa compartilhado e rótulo de réplica) e um parâmetro novo no construtor interno; o campo `invalidacoesConfirmadas` deixou de ser inicializado inline e passa a vir do construtor (`null` cria mapa privado, comportamento antigo). **Mudança mínima e legítima (E2-D3):** não toca nenhuma regra de domínio (login, validate, block, etc.); o mapa é estado do observador do experimento, não da aplicação. Sem ela, I1/I4 ficariam cegos entre servidores de aplicação. Aceita.
- `Cli`: `--scenario` virou conversor; S1/S2 seguem pelo enum, `Scenario.values()` continua com 2 constantes (teste). `WorkloadMain`: S3 é desviado em `if (cli.scenarioS3)` antes de qualquer uso do enum; o ramo S1/S2 é o código anterior apenas envolvido em `else`. S1/S2 intactos.

## 3. Golden (C-3)

- **Reprodução independente.** Criei um worktree em `8e2e815` (fora de `wt-te`), compilei com `mvn compile` e rodei um gerador próprio (mesmas chamadas fixas de `EventCsvWriter.registrar`, `Scenario.proximaOperacao` com `Random(42)` por 2000 passos em S1 e S2, `KeyGenerator(42)` 500 chaves, `InvariantAuditor.dumpCsv`) contra as **classes compiladas da base**. Resultado: `sequencia-s1-s2-seed42.txt` **idêntico** (cmp), `eventos-sem-marcadores.csv` **idêntico** depois de normalizar o `op_id` e o CRLF, cabeçalho de violações idêntico. O golden foi, de fato, gerado a partir da base.
- **Falha com mutação (reproduzida):** cabeçalho do CSV com `keyX` falha `GoldenS1S2Test.csvDeEventosIdenticoAoDaBase` (e mais 4 testes); `r.nextInt(100)` trocado por `nextInt(101)` em `Scenario` falha `sequenciaPorSementeIdenticaADaBase`.
- **Normalizações.** `op_id` é um contador global do processo; o teste o reescreve relativo ao primeiro evento, o que preserva a ordem e a contagem de eventos e esconde só o valor absoluto (sem perda de poder de detecção para o formato). CRLF normalizado nos dois lados: só mascara a diferença de separador de linha da plataforma (`newLine`), que não é contrato. Aceitas. Limite: o golden cobre **formato** (eventos fixos, não uma execução S1/S2 completa) e **sequência de operações por semente**; o fluxo de `WorkloadMain` em S1/S2 vale por leitura de diff e pelos testes pré-existentes.
- **Golden quebrado (`e7010d1`).** O commit inicial tinha escape de CRLF corrompido (não compilava). `c9bd1d8` o corrige trocando literais por `(char) 13 + (char) 10`; o teste compila e passa em `HEAD`, e a mutação de cabeçalho agora falha de fato. Correção confirmada. (Histórico com um commit quebrado: aceitável, mas anotar; não reescrever histórico.)

## 4. Mutações repetidas (testes novos)

| Mutação | Resultado reproduzido |
|---|---|
| Sem *sticky* (`servidoresApp.get(i % n)` trocado por índice aleatório) | **detectada**: `determinismoPorSemente`, `falhaIsolaUsuariosDoNo` (2 falhas) |
| Semente ignorada (`ContextoS3.rng` com `nanoTime`) | **detectada**: `determinismoPorSemente` (1 falha) |
| Mapa de invalidações não compartilhado no harness de teste (`MundoS3`) | **detectada**: `invalidacoesCompartilhadas` (1 falha) |
| Mesma mutação no código de produção (`S3Clientes.abrir`, mapa novo por servidor) | **NÃO detectada** (184 verdes) |

A última linha é uma lacuna real: o teste exercita o mapa compartilhado pelo harness em memória (`MundoS3`), mas a fiação de produção em `S3Clientes.abrir` exige `RemoteCacheManager` conectado e não tem teste. Vira ressalva R-3 (verificar no smoke de T-F), sem exigir código agora.

## 5. Fidelidade à decisão Q-13

Cobertos (leitura do código + testes): servidores pinados BASIC com um nó cada (`S3ClientesTest`: 1 servidor, BASIC, 2000/2000, `max_retries` 1); console admin em `--admin-node`; sessões órfãs (abandono silencioso 30% por `--s3-logout-prob 0.7`, `Logout` que falha após retentativas, expiração no cliente); `Validate` pós-`Logout` (botão voltar, 1 a 3 s por 10 s); `Block`→`Unblock` com hold mínimo de 20 s; `Block` com erro retenta uma vez; sem reparo de estado; retentativa de aplicação limitada (2, esperas 500 ms e 1 s, só `ERROR_*`); pools U e L disjuntos; sticky por índice módulo 3; relógio injetável; determinismo por semente.

Lacunas (nenhuma bloqueia a aprovação):

1. **Densidade de eventos-tema.** Em 600 s simulados há só **12 `Block`** e 75 `Unblock` (a maioria do pool L). Numa janela de falha de 60 s esperam-se ~1 a 3 `Block`: pouca potência para I2 administrativo e para os temas (ii) e (iii) do plano. Ver seção 8.
2. **Timeout de sessão no cliente** só dispara quando uma ação trava mais de 30 s; com pensar médio de 1 s, a maior parte das órfãs vem do abandono silencioso e de `Logout` falho, não da expiração. É coerente com o plano (30% abandonam), mas o evento `SessaoExpiradaNoCliente` será raro.
3. **Mix efetivo e comportamento real de BASIC com nó particionado** não foram observados (só grid em memória): é a hipótese principal de T-F (RAST-S3-3).
4. Dois temas dependem do acaso do instante de falha (nenhum é forçado, conforme o plano); o relatório de T-F deve contar quantos eventos-tema caíram em cada janela.

## 6. Timeouts "curtos" (ajuste da descrição do plano)

Confirmado de forma independente em `infinispan-client-hotrod 15.0.0.Final` (programa próprio com `new ConfigurationBuilder()...build()`): `socketTimeout=2000`, `connectionTimeout=2000`, `maxRetries=3`, `batchSize=10000`, `clientIntelligence=HASH_DISTRIBUTION_AWARE`. Logo, **2 s já é o padrão da biblioteca**; o S3 muda de fato só **`max_retries` 3 para 1** e **BASIC com um servidor por cliente**. Pior caso por operação: (1+1 tentativa Hot Rod) x 2 s = ~4 s, mais 1,5 s de espera de aplicação, em até 3 rodadas (~13 s).

Texto proposto para substituir o item "Timeouts curtos de cliente" do plano (T-E item 5), a aplicar quando o plano for revisado:

> **Timeouts de cliente:** `socket_timeout` e `connect_timeout` de 2000 ms (valor padrão de `infinispan-client-hotrod` 15.0.0.Final, fixado explicitamente por `--hotrod-timeout-ms`); a configuração do S3 difere do padrão em `max_retries` = 1 (padrão 3, `--hotrod-retries`) e em `ClientIntelligence.BASIC` com um único servidor por cliente (padrão HASH_DISTRIBUTION_AWARE). Retentativa de aplicação: no máximo 2 (500 ms e 1 s), depois `falha-percebida-pelo-usuario`.

## 7. C-4: custo de `block()` (o que exigir em T-F)

Medição só em memória: 22 ms para 100 mil sessões, 114 ms para 500 mil; desserialização estimada em ~1,7 s para 100 mil (530 B/sessão). O custo remoto não foi medido. O `batchSize` padrão é 10000, ou seja, ~10 lotes para 100 mil sessões, cada requisição com seu `socket_timeout`: um `block()` de vários segundos é plausível **sem** estourar 2 s por requisição, mas isto ainda é hipótese. **Exigir em T-F:** (a) o smoke de S3 mede a duração de `Block` (evento do CSV, `end_ns - start_ns`) com cache vazio **e** com o cache carregado (~100 mil chaves do baseline); (b) o relatório informa p50/p99 de `Block` nas duas condições; (c) se o p99 carregado passar de 2 s (ou aparecer `ERROR_TRANSPORT` em `Block` sem falha injetada), T-F executa S3 com `sessions` esvaziado entre cenários e declara isso no pré-registro; (d) a duração do `Block` entra como fator de alargamento da janela da cascata (tema iii), citado e não "corrigido".

## 8. Carga: ~14 ações/s contra "ordem de 10^2"

Reproduzido em E2-D1: 8164 ações em 600 s simulados, ~14 ações/s com 20 usuários, 10 contas L e 1 admin. **Decisão:** o plano foca semântica, não vazão; portanto o critério do pré-registro deve ser a **densidade de eventos-tema por janela de falha**, não ops/s. Recomendação para T-F: (i) substituir "ordem de 10^2 ops/s" por "ordem de 10^1 a 10^2 ops/s, dimensionada para garantir um mínimo de eventos-tema por janela" e (ii) fixar no pré-registro `--s3-users` (sugestão de partida 60 a 100) e `--s3-admin-mean-sec` (sugestão de partida 5 s) de modo que a janela de falha contenha, em expectativa, pelo menos 10 `Block` e 20 `Logout`; (iii) medir o mix efetivo e o número de eventos-tema por janela no smoke e registrá-los. O padrão atual (20 usuários, admin 15 s) é aceitável como valor padrão da CLI; só não deve ser o valor do experimento por inércia.

## 9. E2-D4, E2-D5 e demais itens

- **E2-D4 (mensagem de erro de `--scenario`): aceita.** A nova mensagem lista os valores aceitos (S1, S2, S3) e `S9` continua erro de parâmetro (teste), sem cair no fallback S1. Nenhum consumidor depende do texto antigo.
- **E2-D5 (parâmetros de modelagem): aceita como valores de partida**, todos parametrizáveis. **Registrar no pré-registro de T-F**, com justificativa de cada um, os que afetam o resultado: sigma da log-normal 0,6; pausa entre visitas 5 s; espera após `BLOCKED` 10 s; hold do admin 20 s + exponencial de 10 s; chegadas escalonadas; admin só escolhe U "online". São escolhas do Worker dentro do plano, sem fonte empírica; o texto do TCC deve chamá-los de premissas de modelagem, não de medidas.
- **E2-D6, D7, D8:** coerentes (warm-up documentado no JSON; rótulo `topology` não ancora escrita por desenho; ator nunca derruba o experimento).
- **RAST-G3-1 e esqueleto C-2: adequados.** Os dois testes `@Disabled` terminam em `fail(...)` com os placeholders; ao habilitar sem preencher, quebram, então não passam em silêncio. O item rastreável está no relatório (seção 7, RAST-G3-1), que o aceite do plano admite (C-5). **Exigir** que o prompt de T-B (gate G-3) cite RAST-G3-1 nominalmente e que a V-B só aprove o G-3 com o esqueleto preenchido.

## 10. Ressalvas (condições de T-B/T-F, nenhuma bloqueia a integração local)

- **R-1** (T-B): G-3 captura exceção real e JSON de saúde e habilita `ClassificadorMensagemRealTest`; sem isso T-F não alega H-L7.
- **R-2** (T-F): pré-registro fixa `--s3-users`, `--s3-admin-mean-sec` e demais parâmetros da seção 9 com a meta de eventos-tema por janela (seção 8).
- **R-3** (T-F): smoke real prova a fiação de produção do mapa compartilhado (um `Logout` num servidor e `Validate` em outro vistos pela varredura I1/I4), pois `S3Clientes.abrir` não tem teste automatizado.
- **R-4** (T-F): custo de `Block` medido no cluster, vazio e carregado, com a regra da seção 7.
- **R-5** (T-F): smoke de `--scenario S3` contra o cluster real (RAST-S3-3): `replica` nos três nós, comportamento de BASIC com nó isolado, `s3-acoes`, duração real de operação sob partição; contagem de eventos-tema por janela.
- **R-6** (plano): atualizar o texto de timeouts (seção 6) e a frase de carga (seção 8); a contagem Surefire da base é 58 (C-5, já registrada na V-E1).

## 11. Integração

T-E (E1 aprovada com ressalvas + E2 aprovada com ressalvas) é **APROVADA PLENA** para integração local. Observação operacional: a branch `feature/etapa-formal-integracao` **ainda não existe** no repositório; ela deve ser criada pelo orquestrador a partir da base escolhida do Gestor e a `feature/b33-instrumentacao-particao` incorporada localmente (histórico linear, sem force-push, sem reescrever o commit `e7010d1`). Nenhum push sem aprovação explícita do Gestor.
