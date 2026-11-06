# Smoke local do P1-IC: o controlador active manda o nó suspeito ignorar o cache de sessões (06/11/2026)

Branch `feature/p1-ignorar-cache` (worktree `wt-p1ic`). Relatório: `docs/relatorio-p1-ignorar-cache-2026-11-06.md`.

## Ambiente

- Máquina do Podman do WSL, cluster local próprio: projeto `p1ic`, contêineres `p1ic-isn1..3`, rede `p1ic-net`, portas
  13001 a 13003, log `k8s-file` (G-5). `cluster/podman-compose.p1ic.yml` é cópia do `podman-compose.a4.yml` do A4 (que é o
  `cluster/podman-compose-sem-caches.yml` da base com nomes, rede e portas próprios); `cluster/infinispan-sem-caches.xml` e
  `cluster/jgroups-tcp.xml` são cópias dos da base. Os seis caches pelo `scripts/cria-caches.sh` da base. A A1 não está
  integrada na base: regra de fusão atual (`PREFERRED_NON_NULL`).
- Nenhum outro cluster estava de pé durante as execuções (conferido com `podman ps` antes de cada lote).
- Carga S3 ASYNC com o controlador **active** e o arquivo da calibração da base
  (`cluster/controlador/controlador-calibracao-s3.json`, SHA-256 na primeira linha de cada `fases.tsv`; sem o campo
  `acaoP1`, então P1 = AC-7), referência inerte `config/referencia-S3-async.json` (cópia da do smoke do W-2b), carga L+
  do piloto de violações (`--s3-users 40 --s3-think-ms 500 --s3-session-median-sec 10 --s3-logout-prob 0.9
  --s3-admin-mean-sec 5`), `--sweep`, `--s3-replay-probe`, `--convergence --convergence-timeout-sec 120
  --rejoin-timeout-sec 120`, `--warmup-min-sec 30`. Comando exato em `<execução>/comando.txt`.
- Jars (SHA-256 na primeira linha de cada `fases.tsv`): `corte-r1` e `falso-r1` com `ae07fad` (`c1fab662…`); `curto-r1`
  com `c532215` (`f7d3867a…`, só o tempo do POST pendente no `lido` muda); `curto-r2` e `corte-r2` com `78a151d`
  (`ccdb05d7…`, com a guarda contínua).

## Scripts (`scripts/`)

| Arquivo | Papel |
|---|---|
| `smoke.sh ROTULO CASO SEMENTE` | uma execução (casos `corte`, `curto`, `falso`; ver o cabeçalho) |
| `lib-p1ic.sh` | funções (cópia do `lib-a4.sh` do A4 com os nomes p1ic e a carga com o controlador) |
| `particao-p1ic.sh` | corte e cura por nftables no netns do alvo (cópia do `particao-a4.sh`) |
| `SondaA4.java` | sonda Hot Rod pinada do A4, sem mudança (25 ms no alvo, 100 ms nos outros) |
| `vigia_p1ic.py` | cópia do `vigia_a4.py` do A4 (listas de ignorados a cada 100 ms; disponibilidade a cada 500 ms; só leituras locais) |
| `SmokeAc7.java` | falso alarme provocado sem corte: o AC-7 mandado ao alvo pelo adaptador de produção, como o executor mandaria |
| `analisar_p1ic.py DIR` | resumo de uma execução (`resumo.json`) |

## Execuções

| Execução | Caso | Semente | O que mostra |
|---|---|---|---|
| `corte-r1` | corte do alvo de o+60 s a o+150 s | 81001 | disparo do P1, recusa no alvo, desfazer quando o alvo se vê sozinho, volta na fusão |
| `falso-r1` | sem corte, AC-7 forçado ao isn3 em o+60 s | 82001 | guarda de falso alarme do adaptador |
| `curto-r1` | corte de 24 s, curado antes da detecção | 83001 | a gravação que deu 500 chega à maioria depois da cura; `suspeita-desfeita` |
| `curto-r2` | o mesmo, com a guarda contínua | 83002 | guarda contínua |
| `corte-r2` | o mesmo do `corte-r1`, com a guarda contínua | 81002 | a guarda contínua não age num corte de verdade |

```bash
cd wt-p1ic/workload && mvn -o -q -DskipTests package
cd wt-p1ic/runs/p1ic-smoke
bash scripts/smoke.sh corte-r1 corte 81001 > corte-r1.out 2>&1; bash scripts/smoke.sh falso-r1 falso 82001 > falso-r1.out 2>&1
bash scripts/smoke.sh curto-r1 curto 83001 > curto-r1.out 2>&1
bash scripts/smoke.sh curto-r2 curto 83002 > curto-r2.out 2>&1; bash scripts/smoke.sh corte-r2 corte 81002 > corte-r2.out 2>&1
python -I scripts/analisar_p1ic.py <execução> > <execução>/resumo.json
```

## O que fica fora do git

Pelo `.gitignore` da base (`runs/**/*.csv`, `*.log`, `*.txt`), ficam só na worktree, com SHA-256 no `MANIFEST.sha256`:
`rep-001.csv`, `s3-acoes-rep-001.csv`, `violacoes-rep-001.csv`, `varredura-rep-001.csv` e os demais CSV da carga,
`stdout.log`, `comando.txt`, `compose-*.txt`, `corte.txt`, `cura.txt`, `subir.txt`, `marcadores.log`, `exit.txt`. As
trilhas do controlador (`*.jsonl`), as sondas, o stderr da carga e os logs dos servidores (comprimidos) entram no git.
`.gitattributes` com `* -text`.
