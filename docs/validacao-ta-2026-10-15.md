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
