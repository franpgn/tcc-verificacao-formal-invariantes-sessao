# Gate v4 (S0): scripts L-0/L-0c/L-1 e G-5b, preparo sem VM - 2026-10-16

> Preparo do bloco S0 de `docs/decisoes-senior-2026-10-16.md` (secao 5). Nada foi executado na VM nem enviado a `origin`.

Branch `test/b29-gate-v4`, base `4b680a2`. Commits, em ordem: `0d62953` (roteiro corrigido), `52820a3` (shims e harness de mutacao), `c63aa6a` (L-0, L-1, `l0c.sh`), `302b03d` (`g5b.sh`), `dd67af8` (T-l-9c), `629bc01` (cherry-pick -x de `111e673`, g5b.test sem pendurar), `5510c92` (`verbatim.sh` sem SIGPIPE), `90ddd32` (cherry-pick -x de `ff18fe3`, T-b-6d/T-b-8f sem corrida), mais os dois commits `docs:` desta rodada. O HEAD final e o sha256 do bundle estao em `tcc/gate-v4.bundle.sha256` (nao podem constar aqui: estao dentro do que o documento descreve).

## 1. O que foi entregue

| Arquivo | Papel |
|---|---|
| `runs/gate-netdegrade/{l-lib,l0,l1}.sh` | L-0 e L-1/L-2 e a biblioteca, copia **verbatim** do roteiro de `docs/relatorio-b29-2-implementacao-2026-10-16.md` |
| `runs/gate-netdegrade/verbatim.sh` | `verificar` faz o diff doc x arquivo; `extrair` imprime o bloco |
| `runs/gate-netdegrade/l0c.sh` | L-0c: dead-man do netdegrade dispara sozinho (filtro TEST-NET, sem impacto) |
| `runs/gate-netdegrade/l-gate.test.sh` | testes com shims de l-lib, l0, l1, l0c |
| `runs/gate-b29/g5b.sh`, `g5b.test.sh` | G-5b-A (cura apos split real) e G-5b-B (dead-man disparando sozinho) |
| `runs/gate-b29/gate-test-lib-v4.sh`, `mutacao-gate-v4.sh` | shims estendidos e harness de mutacao (timeout por caso 240 s, por mutante 1200 s) |

## 2. Medido (detalhes e ressalvas em `docs/testes.md`, secao "Gate v4")

- **Verbatim:** `verbatim.sh verificar` = IDENTICO para `l-lib.sh` (80 linhas), `l0.sh` (19) e `l1.sh` (17), no Git Bash e no WSL.
- **`l-gate.test.sh`:** 162/0 (Git Bash), 157/0 (WSL; 5 casos de modo git pulados).
- **`g5b.test.sh`:** 128/0 (Git Bash, 2 rodadas, ~330 s), 126/0 (WSL, 2 rodadas; 2 casos de modo git pulados).
- **Mutacao:** Git Bash, 36 mutantes (19 `l`, 17 `g5b`), 36 MORTO, 0 SOBREVIVEU, 0 TRAVOU; WSL, `g5b` 17/17 MORTO. V1..V6 reexecutados apos o conserto do `verbatim.sh`: 6/6 MORTO.
- **Suites existentes:** `inject-netdegrade` 298/0/1 (Git Bash), 306/0/0 (WSL, `flock` real, `T-nd-21` roda); `inject-partition` 125/0/2; `inject-jitter` 35/0; `g5` 50/0; `g6` 70/0 isolado; `g1b` falhas=0; `g1` 61/0 em 4 de 5 rodadas.
- **Hashes dos scripts** (sha256 completos): `l-lib.sh` `9bdd1faeca0399d11886ee5c77189825c18f5bf69d78797c579c1fd71e9d73fd`; `l0.sh` `38325ff08f9f368014f6de5fbfa5a64a46e5d1e1a4e7c620ace939a18e6731f7`; `l1.sh` `9f3deacc6c408d2e70125ede52d409d87b876202cd33b370277b6330775e1253`; `l0c.sh` `8c8c35416a5d2ff89449bb12edd2c93672fcece8768c4dc4897a5c40be9d3955`; `g5b.sh` `94f7ae9232ac8aebe194e606acdc0ad7e531b7bbaa492cfddbbd4fa18db7a372`; `inject-netdegrade.sh` `4ebdd8a34a7d5c1e6981138272a4f0b90a67c44dd8626e9521884e185297abb1`; `inject-partition.sh` `59dd2beee5b003d85f96cae71866227d7bca1c87a9ceeab034180ce4254d94e1`; `g56-lib.sh` `4f750fe3cf59f299585c12f66b3020973976e0e979474fde6354bf6998a18e2a`; `g-lib.sh` `af3436d5ede54f5f2047ba48620130a803b4bbd8929e2cbc242fbbe0ebb922dd`. Modo git: `100755` nos executaveis e nos `*.test.sh`, `100644` em `l-lib.sh` e `gate-test-lib-v4.sh`; 0 `\r` nos `.sh` novos.

## 3. O que NAO foi validado

- Nada contra o kernel, o systemd real, o `tc -s` ou o journal reais: so shims. `AccuracySec`, o formato de `tc -s` e o disparo real do `systemd-run` ficam para S1 a S3 (VM).
- `inject-partition`, `inject-jitter`, `g1`, `g1b`, `g5`, `g6` nao foram medidos no WSL nesta rodada.
- `g1.test.sh`: `T-g1-4`/`T-g1-2` instaveis (corrida de `parar_log_saude`, `g-lib.sh:64-69`; `g-lib.sh` nao foi alterado, ressalva aceita pelo Gestor). `T-g6-5` so falha sob carga.
- A bateria completa de mutacao de `l` nao foi repetida depois do commit do `verbatim.sh`, nem a de `g5b` no Git Bash depois do `ff18fe3` (o WSL foi).
- O `--dry-run` de `l0c.sh` e `g5b.sh` e coberto pelos testes (T-b-0 e T-l-*), nao foi rodado a mao num host sem shims.
- Um TLC do B-35 (java) rodou em paralelo durante parte da primeira bateria; a segunda bateria, a mutacao final e as contagens citadas acima do `g5b`/`l-gate` finais foram sem java.

## 4. O que o Senior confere na V-S0

1. `bash runs/gate-netdegrade/verbatim.sh verificar` (3 IDENTICO) e, se quiser, extrair e `diff` a mao.
2. Leitura integral de `runs/gate-netdegrade/l0c.sh` e `runs/gate-b29/g5b.sh`; `--dry-run` de cada um.
3. Que `5510c92` (`verbatim.sh`) e os dois cherry-picks (`629bc01`, `90ddd32`) so tocam arquivos de teste ou o verificador, nao `l-lib/l0/l1/l0c/g5b/g-lib/g1/g6`.
4. Hashes da secao 2 contra `git rev-parse HEAD`/`sha256sum` do bundle (S1 os repete na VM).
5. As ressalvas de `docs/testes.md` (T-g1-2/T-g1-4, T-g6-5, SKIP de modo git no WSL) e a rodada de mutacao em `docs/testes.md`.
