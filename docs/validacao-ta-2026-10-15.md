# V-A: validação da entrega T-A (auditoria do B-32 e catálogo) — 2026-10-16

Validador: Senior. Entrega: `test/b32-auditoria-controle` (e73f616, 2788273, 74a7418). Documento em progresso (salvo a cada etapa).

## Item 1 (tentativa anterior)
- SHA-256 do `tla2tools.jar` confere; `git diff 48d1571 -- spec/SessionStore.tla spec/MCSession.tla` vazio.

## Item 2. Recontagem
- `scripts/audit-tlc-runs.sh` reexecutado em saída separada: 100 `.out`; VIOLADA 77, FECHOU 6, TIMEOUT 17; 0 divergências; `tabela-recalculada.tsv` byte a byte igual à entregue (`cmp`).
- Recontagem independente por `grep` direto nos `.out` (sem usar o script do Worker): V=77, F=6, T=17.
- Amostra direta nos `.out` (linhas finais do TLC):
  - FECHOU `prop-queda-c2r3/r2-I1F`: 195.239 gerados, 15.690 distintos, 0 na fila, 02s, "No error".
  - FECHOU `prop-queda-c2r3-ev1/r3-I1F`: 197.610.219 / 8.037.698 / 0, 09min 31s.
  - FECHOU `prop-sem-c2r3/r3-I1P`: 53.541.786 / 1.627.731 / 0, 01min 23s.
  - TIMEOUT `prop-sem/r3-I1F`: último Progress 222.726.728 / 18.273.465 / fila 12.793.143; sem "No error" nem "Finished".
  - TIMEOUT `prop-sem/r3-I4Mp`: 76.614.049 / 7.061.453 / fila 5.176.880.
  - TIMEOUT `prop-queda/r3-I2Fa`: 734.680 / 110.313 / fila 88.042 (timebox de apenas 60 s, ver ressalva R1).
  - TIMEOUT `prop-particao-c2r3/r3-I1P`: 287.159.605 / 33.038.740 / fila 21.076.524.
  - Sem falha: VIOLADA `prop-sem/r3-I4F` (6 estados, 1.979/366), `I2Fb` (5, 378/76), `I4Fp` (6, 2.151/422) (4 workers).
  - Queda: `I4F` 5 estados (2.033/482), `I2Fb` 4 (382/99).
  - Partição: `I1F` 5 (1.119/317), `I2Fa` 6 (7.197/2.039), `I1M` 4 (1.066/306), `I4Mp` 5 (7.676/2.147) (4 workers; o catálogo usa os de 1 worker).
- Casam com `resumo-*.tsv` e com o catálogo (seção 3/4.1).

## Item 3. Controles reexecutados (JDK 17.0.2, TLC 2.19, 1 worker, **sem `SYMMETRY`** — `grep -c SYMMETRY` nos .cfg = 0; jar SHA-256 `936a2620...`)
Script corrigido de `wt-ta`, saída em diretório temporário, 3 execuções simultâneas (também serviu de teste de rc, 0 erros).

| Célula | Esperado (catálogo) | Reproduzido | gerados / distintos |
|---|---|---|---|
| I1F partição, r3, 3/5/2 | violada, 4 estados | VIOLADA, 4 estados (Logout, Particionar, Mesclar) | 1.008 / 478 |
| I2Fa partição, r3, 3/5/2 | violada, 4 estados | VIOLADA, 4 estados | 5.889 / 2.608 |
| I4F sem falha, r3, 3/5/2 | violada, 5 estados | VIOLADA, 5 estados | 7.260 / 2.174 |
| I1F queda, r2, MaxCanal=2, MaxRelogio=3, ev=2 | fechou, 51.840 distintos | SEM_VIOLACAO exaustivo, 0 na fila | 641.661 / 51.840 |

## Item 4. Vacuidade (`-coverage 1`, 1 worker, r2, MaxCanal=2, MaxRelogio=3, ev=2, com simetria)
| Coluna / propriedade | Resultado | `Particionar` | `Mesclar` |
|---|---|---|---|
| sem falha, I2Fa e I1M | fechou, 1.988 distintos (38.643 gerados) | 0:3.976 | **0:0** |
| queda, I4Mp | fechou, 15.690 distintos (195.239 gerados) | 0:11.928 | **0:0** |
| partição, I2Fa (controle positivo) | violada, 4 estados | 41:48 | **11:14** |
`Particionar 0:3976` significa que a ação gera sucessores, todos barrados pela restrição (`distintos` 0); `Mesclar 0:0` significa nunca habilitada. Catálogo (seção 6) distingue `vácuo-verificado` (r2, r3 c2r3 sem falha, r2 queda, r3 queda c2r3 só I2Fa) de `vácuo-estrutural` (cotas do plano, TLC não fechou). A distinção está correta e honesta.

## Item 5. `rc=150`
Script antigo (`git show 48d1571:spec/run-tlc-propriedades.sh`) versus corrigido, cópias isoladas de `spec/` em diretório temporário; `semeado particao r3 I2Fb`, MaxCanal=3, 1 worker, 5 rodadas de 20 execuções simultâneas (100 por variante).
- Antigo: **2 de 100** `ERRO rc=150` (98 VIOLADA, 3 estados); a assinatura no `.out` é a do relatório (falha de parse / Module-Table lookup).
- Corrigido: **0 de 100** (100 VIOLADA, 3 estados).
- Observação: um único lote de 20 execuções *longas* (r2, ~1 s cada) não reproduziu o erro (0 de 20 nas duas variantes); a janela é a extração de `TLC.tla` etc. para `java.io.tmpdir` no início da JVM, então exige muitas partidas curtas concorrentes. A taxa é baixa (2% aqui; 5/80 e 4/80 no Worker). A correção (tmpdir por execução) é consistente com o mecanismo; evidência do Worker (0 de 120) mais a minha (0 de 100) sustentam eliminação, **sem** prova absoluta (a ausência de erro é amostral).

## Item 6. Leitura de traços (P-7), conferidos contra `Next` de `spec/SessionStore.tla`
Lidos passo a passo (11 traços): `prop-particao-w1/r3-I1F` (Logout, Particionar {r1}|{r2,r3}, Mesclar com rf=r2/r3 ainda `VALID@1` copia sobre r1: reversão correta, `relogio` de r1 volta a 1 como no `Mesclar`); `prop-particao-w1/r3-I2Fa` (Block, Particionar, Mesclar: r1 volta `OPEN` e sessões `VALID`); `prop-sem-w1/r3-I4F` e `I4Fp` e `prop-queda-w1/r3-I4F` (Logout s1, Logout s2 em r1; Block em r2 vê s1,s2 `VALID`; entrega do Logout(s2) ts3>2 troca `ADMIN_BLOCK` por `LOGOUT` mantendo `INVALID`; sem `Cair`/`Particionar` no traço, de fato sem falha); `prop-sem-w1/r3-I2Fb` (mecanismo a: IncrementFailure x2, `LIMITE=2`, BLOCKED com sessões VALID); `prop-sem-lim6-w1/r3-I2Fb` (mecanismo b: `afetadas={s2}` calculada na origem; r2 aplica `ID_BLOQUEIA` com s1 ainda VALID); `prop-particao/r3-I4Mp` (Logout r1, Block r2 concorrentes, Particionar, Mesclar rf=r2: motivo LOGOUT->ADMIN_BLOCK); `prop-livre-particao-w1/r3-MesclarPerdeChave` (Login r1 não propagado, Mesclar: r1 VALID->NONE); `prop-livre-sem-w1/r3-I1P` (D-4: Logout em r2 com `relogio`=0 gera ts 1, não supera ts 1 local; mensagem ts 1 descartada pelos pares); `prop-livre-sem-w1/r3-I1F` (7 estados, ressurreição por reordenação; D-4).
Resultado: todas as leituras do Worker (`tracos/traco-*.md`) e do catálogo (seções 4.1 a 4.5) estão corretas; nenhuma correção necessária ao sentido. Tamanhos de traço do catálogo conferem com a tabela recalculada (`*-w1`: I1F/I1M/I4F/I4M partição 4; I2Fa 4; I4Fp 5 nas três colunas; I4F sem falha e queda 5; I2Fb 3 e, com LIMITE=6, 4). A tabela `classificacao-d4.tsv` marca D-4 só onde há Logout rejeitado por ts; coerente.
Nota de leitura (não é erro): nos traços de partição, r1 (origem do Logout/Login/Block) nunca recebe a própria mensagem (Broadcast exclui a origem), logo após o `Mesclar` revertido r1 não se recupera sozinha; é o mecanismo da divergência permanente já registrada em I5/I6 e vale como detalhe para o catálogo, não muda nenhum veredito.

## Item 7. P-26 e P-27 contra o código real e o texto
- `SessionOps.incrementFailure` (linhas 256 a 276): lê `IdentityState`, chama `incrementar(LIMITE_TENTATIVAS, ...)` e grava só `identities`; **não** toca `sessions`. `IdentityState.incrementar` marca `BLOCKED` ao atingir o limite. Confere com P-27 (a).
- `SessionOps.block` (linhas 306 a 325): `sessions.entrySet().forEach` invalida as sessões `VALID` da identidade; é o único caminho com cascata. Confere.
- `SessionState.invalidar`: devolve `this` se já `INVALID`; senão `versao + 1`. Confere com a classificação de P-26 (defeito de modelagem D-4; o código não tem relógio por réplica que fique defasado).
- `classificacao-d4.tsv`: 77 linhas, 18 `SIM`, todas em `prop-livre-*`; 0 em semeado. Confere com P-26 e relatório §7.
- I2Fb: `VIOLADA-POR-CONSTRUCAO` nas três colunas, dois mecanismos, traços 3 (LIMITE=2) e 4 (LIMITE=6) estados, ambos lidos e corretos; (a) é comportamento do código (conferido), (b) é artefato do modelo (afirmação sobre o real marcada como leitura, não evidência).
- Cotas e linguagem: nenhuma célula do catálogo diz "prova geral"; "preservada" sempre vem com cotas; timeouts são `SEM-VIOLACAO-ATE-AQUI`; não há "zero = não ocorre" (colunas reais estão `PENDENTE`).

## Item 8. Recomendações
- **I4Fp/I4Mp: manter**, com o rótulo `VIOLADA-POR-CONSTRUCAO` (I4Fp, 5 estados nas três colunas) e `CONFIRMADO-NO-MODELO` apenas no sentido "o passo violador é `Mesclar`" (I4Mp); nunca como efeito da partição. Concordo com o Worker.
- **Q-5 (corrigir `relogio` em variante)**: não alterar `SessionStore.tla`. Manter P-26 `ABERTO-ACEITO` e, como teste de causalidade barato, criar (só se o Gestor quiser) um módulo-variante separado com `relogio' = max(relogio, ts da mensagem)` e rodar apenas `I1P`/`I1F` livre sem falha com 1 worker para mostrar que as violações D-4 desaparecem (minutos); não reverificar todos os fechamentos. Valor: transformar a classificação "defeito de modelagem" de leitura em evidência. Os vereditos do semeado já são independentes de D-4 nos traços de violação; portanto a variante não é bloqueante para T-F.

## Veredito: APROVADA COM RESSALVAS
Nenhum número, traço ou veredito foi invalidado; todos os reproduzidos conferem com o catálogo.
Ressalvas (não bloqueantes):
1. Cabeçalho do catálogo diz "status provisórios até V-A": atualizar após esta aprovação.
2. Nas células `VACUA` da matriz (seção 3), a marca `vácuo-verificado` vale nas cotas reduzidas (r2/r3 com `MaxCanal=2`, `MaxRelogio=3`); nas cotas do plano é `vácuo-estrutural`. O texto entre parênteses diz isso, mas a marca em negrito pode ser lida como cobrindo as cotas do plano; sugerir "vácuo-verificado (cotas reduzidas); estrutural nas do plano" na própria marca.
3. Vários `SEM-VIOLACAO-ATE-AQUI` têm timebox curto (60 s para I2Fa/I1M/I4M em sem falha e queda; 120 s para I1P): corretos como "até aqui", mas fracos como evidência; não promover.
4. `rc=150` é raro (2/100 aqui; 5/80 e 4/80 no Worker); a eliminação é amostral (0/100 aqui, 0/120 no Worker), coerente com o mecanismo.
5. Critério mecânico de D-4 inclui `IncrementFailure`, além dos passos listados no plano; inofensivo, mas declarar.
