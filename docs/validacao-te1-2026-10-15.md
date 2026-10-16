# Validação V-E1 (T-E, parte E1, B-33), 2026-10-15

> Senior. Entrega validada: branch `feature/b33-instrumentacao-particao`, HEAD `97c991b`, base `8e2e815`, relatório `docs/relatorio-b33-2026-10-15.md`. Verificação independente (não se confiou no relatório). Sem push, sem alteração de código.

## 1. Veredito

**APROVADA COM RESSALVAS.** O E1 cumpre os critérios de aceite (1) a (4) e (6) da seção T-E dentro do que é verificável sem cluster. As ressalvas (seção 7) são condições para T-F e para o E2, não bloqueios para iniciar o E2.

## 2. Reprodução (worktree limpo em `97c991b`)

| Verificação | Esperado pelo Worker | Reproduzido |
|---|---|---|
| `mvn -f workload/pom.xml test` (Surefire, 22 suítes) | 140 | **140 testes, 0 falhas, 0 erros, 0 skipped** |
| `python -m unittest discover -s analysis -p "test_*.py"` | 9 | **9, OK** |
| Base `8e2e815` | 58 | não reexecutada; o plano falava em 49 (divergência de memória, não reproduzida) |

## 3. Escopo do diff (`git diff 8e2e815..HEAD --stat`: 33 arquivos, +3235/-68)

- `IdentityState`, `SessionState`, `Scenario`, `KeyGenerator` e `spec/`: diff vazio. Confirmado.
- `OpResult`: acrescenta `ERROR_UNAVAILABLE`, `errorUnavailable()` e o `returnCode`; nada mais.
- `SessionOps` (diff lido integralmente): mudanças permitidas (constantes `OP_BLOCK`/`OP_UNBLOCK`, `try/catch` mais `emitir` em `block`/`unblock`, `ClassificadorDeErro.classificar(e)` no lugar de `errorTransport()` nas 5 operações já protegidas, gancho `rastro` em `emitir`, `invalidacoesConfirmadasSnapshot()`). Mais uma mudança não prevista no plano: a costura `Armazem<V>` (campos `RemoteCache` viram `Armazem`, adaptador 1:1 `Armazem.de`, construtores de composição package-private). Não altera regra de domínio (delegação direta de `get`/`put`/`entrySet`); aceito como D-2, pois a alternativa (Mockito ou dependência no `pom.xml`) é pior. A lógica de `block()` (identidade, cascata, `verificarI1I4`, `invalidacoesConfirmadas`) está intacta.
- S1/S2 byte a byte: `EventCsvWriter.flush(path)` delega a `flush(path, null)`, com cabeçalho e linhas idênticos sem marcadores; `dumpCsv(path)` delega a `dumpCsv(path, null, List.of())`; no `WorkloadMain` toda a instrumentação depende de `--marker`/`--varredura`/`--convergencia` (padrão desligado, `rastro == null`, `parar = () -> false`). S1/S2 não chamam `block`/`unblock`, então não surgem linhas novas. **Limite da evidência:** é leitura de diff mais `FasesNoCsvTest` (cabeçalho e 7 colunas), não comparação byte a byte de arquivos de execução real (sem cluster). Ver C-3.

## 4. Testes novos: substantivos?

Li `ClassificadorDeErroTest`, `SessionOpsBlockTest`, `FasesNoCsvTest` e a lista de casos de `VarreduraFinalTest`. Verificam códigos, estado do domínio após falha (cascata parcial permanece: identidade `BLOCKED`, 1ª sessão `INVALID`, demais `VALID`), conteúdo do CSV e casos negativos (`TransportException` com texto "not available" não promove a indisponibilidade; cadeia de causas cíclica). Não são tautológicos.

Mutações repetidas por mim (arquivo restaurado por `git checkout` após cada uma):

| Mutação | Resultado reproduzido |
|---|---|
| `ClassificadorDeErro.classificar` sempre `errorTransport()` | **3 falhas** (`availabilityExceptionEhIndisponivel`, `blockIndisponivel`, `demaisOperacoesClassificam`), 140 rodados; coincide com o relatório |
| `block()` sem `emitir(OP_BLOCK, ...)` | **falham** `SessionOpsBlockTest` (2 falhas, 2 erros) e `SessionOpsRastroTest` (3 falhas), 5 falhas + 2 erros no total como o relatório |

As outras três mutações do relatório não foram repetidas.

## 5. Desvios D-1 a D-9

| Desvio | Parecer |
|---|---|
| D-1 `OpResultTest` alterado | **Consequência legítima de Q-9, não enfraquecimento.** A premissa antiga ("só `ERROR_TRANSPORT` é erro") ficou falsa por decisão do Gestor; o teste novo exige que os dois códigos de erro estejam fora de `SUCESSOS` e que todos os demais estejam, e há um teste novo. Diff lido. Aceito |
| D-2 `Armazem` | Aceito (seção 3) |
| D-3 rótulo `durante` sem marcador `split` | Aceito. Pendência real: nada grava `split` hoje; decidir em T-D/T-F (derivar dos logs de vista, G-3). Sem isso as fases `durante-pre/pos-split` não existem nos dados |
| D-4 tolerância de relógio 50 ms | Aceito como limitação declarada; T-F deve citá-la (violação por limite até 50 ms antes de um `Block` na mesma identidade pode sair `REAL`) |
| D-5 `I2-PERDIDO` com `Unblock` posterior com erro = `INCERTA` | **Coerente com P-10 e com a regra Q-10** (efeito de operação com erro é desconhecido; análogo a `BLOQUEIO-INCERTO`). Lido literalmente o plano diria `REAL`; o desvio é conservador contra falso positivo. Aceito, desde que `INCERTA` seja sempre reportada separada (nunca somada a `REAL`, nunca descartada) nas tabelas de T-F |
| D-6 marcas de indisponibilidade e campos de saúde REST provisórios | Está registrado como a confirmar (relatório D-6 e Javadoc de `ClassificadorDeErro`, "Pendente (G-3, T-B)"), e o plano já exige que o G-3 registre mensagens e campos REST. **Falta** o teste de regressão previsto e um item rastreável fora do relatório (C-2). Risco real: as mensagens dos testes são fabricadas; se o cliente Hot Rod real não casar com nenhuma marca, toda indisponibilidade sairá `ERROR_TRANSPORT` e H-L7 fica sem dado |
| D-7 `W_recovery` 60 s | Valor de partida; fixar no pré-registro de T-F |
| D-8 `t_fim` = fim da carga sem marcador `end` | Aceito (documentado) |
| D-9 relatório em `docs/` | O aceite literal (5) ("nenhum arquivo fora de `workload/` e `analysis/`") só é violado pelo relatório, que o próprio plano exige em `docs/relatorio-b33-<data>.md`. Contradição interna do plano; **desvio aceitável**. Nenhum outro arquivo fora dos dois diretórios mudou (`docs/testes.md` intocado) |

## 6. Riscos de desempenho e smoke

- **Varredura global:** `materializarTudo` percorre `entrySet()` completo dos dois caches pelo Hot Rod (~100 k sessões Zipfian mais identidades) e carrega tudo em `HashMap`. Custo e memória **não medidos** (o relatório admite). Tolerável uma vez por repetição, depois da convergência; em cache DEGRADED a leitura falha e grava `.erro.txt` (correto, nunca resultado parcial). Risco anterior ao E1 e relevante para o S3: `block()` varre `sessions.entrySet()` inteiro a cada chamada; com ator administrativo a cada ~15 s isso é custo recorrente e pode estourar o timeout de cliente de 2 s, gerando `Block` com erro. Medir no smoke.
- `RastroCliente` guarda um mapa sid para identidade por `Login` OK, sem limite (ativo só com `--marker`/`--varredura`); no baseline de ~10 M eventos é da ordem de 10^5 a 10^6 entradas, aceitável, mas anotar.
- **Smoke ponta a ponta pendente:** `WorkloadMain` com as novas opções (sonda REST via `curl --digest`, leitura global, `--stop-at-fault-end`, laço de convergência) tem as peças testadas, mas a integração só está compilada. Condição: smoke real como **primeiro passo de T-F** (ou na VM após T-B), com critérios: CSV sem `--marker` idêntico ao baseline; com marcadores, coluna `phase`; `convergencia-*.csv` e `varredura-*.csv` produzidos; tempo e memória da varredura medidos.

## 7. Correções e condições exigidas

Bloqueantes: **nenhuma**.

- **C-1:** o E2 continua na mesma branch e deve manter os 140 testes verdes e o diff do domínio vazio.
- **C-2 (D-6):** abrir item rastreável (`docs/registro-de-problemas.md` ou relatório de T-B) "marcas de `ClassificadorDeErro` e campos de `HealthRest` a validar no G-3"; ao receber o G-3, acrescentar teste de regressão com a mensagem real capturada e ajustar a lista. Sem isso, T-F não pode alegar H-L7.
- **C-3 (no E2, junto da regressão por semente de S1/S2):** teste golden de contrato, com eventos gravados pelo `EventCsvWriter` sem marcadores comparados byte a byte a arquivo de referência gerado a partir de `8e2e815`, para fechar a lacuna da seção 3.
- **C-4 (condições de T-F):** (a) smoke ponta a ponta; (b) medir custo da varredura global e do `block()` com ~100 k chaves; (c) `INCERTA` e `BLOQUEIO-INCERTO` relatados à parte; (d) citar D-4 e D-3; (e) P-10 em qualquer "zero violações".
- **C-5 (plano):** registrar que a contagem Surefire da base é 58 (não 49) e que o aceite (5) admite o relatório em `docs/`.

## 8. O E2 (S3) pode começar?

**Sim.** O E2 não depende do smoke nem do G-3, e `RastroCliente` e `VarreduraFinal` já estão prontos para os pools U e L. A aprovação plena de T-E só vem depois do E2 e do aceite (a) a (f) do item 5.
