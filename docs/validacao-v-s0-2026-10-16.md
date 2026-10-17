# Validacao V-S0 do gate v4 - 2026-10-16

Validador: Senior. Alvo: branch `test/b29-gate-v4`, HEAD `9eadbdbe30f0bc8635f88e273c775b43c615e147`. Sem VM, sem push, sem java ativo.

**Veredito: APROVADO COM RESSALVAS** (nenhuma bloqueante; ver secao 3).

## 1. Medido contra alegado

| Item | Alegado | Medido na V-S0 |
|---|---|---|
| sha256 do bundle | `39e9f100...f6c0` | igual; `git bundle verify` OK; HEAD gravado no `.sha256` = HEAD |
| Verbatim `l-lib/l0/l1` | IDENTICO (80/19/17 linhas) | IDENTICO no Git Bash e no WSL; `diff` e `cmp` proprios contra `wt-tc` `39ceb5b` sem diferenca (bytes iguais) |
| `l-gate.test.sh` | 162/0 Git Bash, 157/0 WSL | 162/0 (120 s), 157/0 (56 s); os 5 casos ausentes no WSL sao os `modo git` |
| `g5b.test.sh` | 128/0 (~330 s), 126/0 WSL | 128/0 (332 s), 126/0 (209 s) |
| Mutacao `l` | 19 MORTO | amostra de 11 (L1 L3 L6 L9 L13 V1..V6) + baseline: 11/11 MORTO, baseline PASSOU |
| Mutacao `g5b` | 17 MORTO | amostra de 8 (G1 G2 G6 G7 G9 G12 G14 G15): 8/8 MORTO (baseline nao incluido na amostra) |
| `inject-netdegrade` | 298/0/1; WSL 306/0/0 | igual nos dois |
| `inject-partition` | 125/0/2 | igual |
| `inject-jitter`, `g5`, `g6`, `g1b` | 35/0, 50/0, 70/0, falhas=0 | igual |
| `g1.test.sh` | 4x 61/0 e 1x 60/1 | 4x 61/0 e 1x 60/1 (T-g1-4 desta vez) |
| Hashes sha256 dos 9 scripts | tabela do relatorio | todos iguais; modos git `100755`/`100644` e 0 CR conferem |

## 2. Conferencias de codigo

- `l0c.sh` e `g5b.sh` lidos integralmente. `--dry-run` de ambos (A e B) imprime o plano e sai com rc 0 sem tocar em nada (`git status` limpo). Sem `--dry-run`, a entrada `GO` por pipe e recusada (`sem terminal`, rc 1): o go interativo e exigido. Escritas remotas restringem-se ao roteiro: `systemd-run` do dead-man, `tc` em `isn3` via `nsenter` (L-0c com filtro u32 so para 192.0.2.1), `--heal` de abort, `systemctl stop` (so G-5b-A) e arquivos em `runs/`.
- `5510c92` toca so `verbatim.sh` (1 linha, `exit` para `next`). `629bc01` e `90ddd32` tocam so `g5b.test.sh`, `gate-test-lib-v4.sh` e `mutacao-gate-v4.sh`; patch-id identico ao de `111e673` e `ff18fe3`. `g-lib.sh`, `g1.sh`, `g6.sh`, `g56-lib.sh` e `scripts/` sem mudanca desde `4b680a2`.
- O roteiro `docs/relatorio-b29-2-implementacao-2026-10-16.md` mudou desde `39ceb5b` so no bloco B0 (ssh -i, checkout do branch, caminho do normal.dist), fora dos tres blocos verificados.

## 3. Ressalvas e pendencias

1. `g5b.sh` e `l0c.sh` redefinem `armar` com `AccuracySec=1s` (diferenca deliberada do `armar` verbatim, documentada nos cabecalhos). Nao foi validada contra systemd real; fica para S1 a S3.
2. `exigir_go` de `g56-lib.sh` aceita `G_TESTE=1` sem terminal (so afrouxa a exigencia de tty; o texto `GO` continua obrigatorio). Deve ser garantido que a variavel nao esta exportada na VM. `l-lib.sh` nao tem esse atalho.
3. A amostra de mutacao desta validacao foi de 19 dos 36 mutantes; a bateria completa nao foi repetida. Os mutantes de `g5b` rodaram 4 em paralelo e as mensagens de falha batem com o efeito esperado.
4. `gate-test-lib-v4.sh` (funcao `roda`) tem quebras de linha literais dentro de aspas e bytes nao ASCII em um comentario; funcional, cosmetico.
5. `T-g1-2`/`T-g1-4` (`parar_log_saude`, `g-lib.sh:64-69`): ressalva ACEITA como ja decidida pelo Gestor. Reproduzida (1 falha em 5 rodadas). So `g1.sh` usa o log de saude; `l0c.sh`, `g5b.sh` e o gate v4 nao dependem dele. Efeito real: ate 3 linhas extras de saude no CSV apos o abort.
6. `T-g6-5` so sob carga e rodar suites em serie: nao reproduzido (isolado 70/0), nao exercitado sob carga.
7. Numero sem log bruto no repositorio: todas as contagens acima foram remedidas; as de 3 rodadas, 2 rodadas, mutacao completa (36) e as de `g1` das sessoes anteriores nao tem log bruto versionado e foram confirmadas so por amostra.
