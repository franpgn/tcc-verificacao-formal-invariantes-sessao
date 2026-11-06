# P-64: log do alvo do AC-7 sob corte com as pilhas reduzidas na origem (06/11/2026)

Branch `feature/p1ic-conferidor-analise` (worktree `wt-p1ic-ca`). Decisão do coordenador de 06/11 (P-64, itens a a d).

## Ambiente

- Mesmo harness do smoke do P1-IC (`runs/p1ic-smoke/`, worktree `wt-p1ic`), copiado para cá com três mudanças:
  `lib-p1ic.sh` aponta para o `wt-p1ic-ca` e ganhou `ajustar_log` (o `scripts/ajusta-log.sh` do repositório, com
  `cluster/log/loggers-avaliacao.tsv`, depois dos caches, como o orquestrador); `smoke.sh` chama `ajustar_log` (com
  `SEM_AJUSTE_LOG=1` não chama) e grava o driver de log no `fases.tsv`; o compose `podman-compose.p1ic.yml` ganhou
  `max-size: "512mb"` no `k8s-file`, como o `cluster/podman-compose-sem-caches.yml`.
- Cluster local `p1ic` (portas 13001 a 13003), Podman 5.7.0 e `podman-compose` 1.5.0 da máquina do WSL. Nenhum outro
  cluster de pé.
- Caso `corte` do smoke (corte do alvo de o+60 s a o+150 s, carga S3 ASYNC L+ de 300 s com o controlador active, sonda
  Hot Rod de 25 ms no alvo e de 100 ms nos outros), o mesmo do `corte-r1` e do `corte-r2` do P1-IC, que são o "antes".
- Jar do `wt-p1ic-ca` (trilha 1.3); o comportamento do controlador é o do `78a151d` usado no `corte-r2`.

## Leitura da API de logging no 15.0.21 (antes da medição, no mesmo cluster)

- `GET /rest/v2/logging/loggers`: lista compacta `{"name":..,"level":..,"appenders":[..]}`; a raiz grava em `STDOUT`
  e `FILE` (`server/log/server.log` dentro do contêiner).
- `PUT /rest/v2/logging/loggers/<categoria>?level=FATAL`: 204; nível inválido: 400 `ISPN080036`.
- O nível vale **só no nó que recebe o pedido** (os outros dois não mudaram) e **não sobrevive** ao `podman stop` e
  `start` do nó.
- Teste funcional: sonda de 25 ms no isn3 com o `sessions-async` ignorado; em 5 s, 416 `ISPN005003` e 1,24 MB de log
  sem o ajuste, 64 e 0,06 MB com `BaseRequestProcessor` em `FATAL` (as 64 caíram antes do PUT valer).

## Comandos

```bash
cd wt-p1ic-ca/workload && mvn -o -q -DskipTests package
cd wt-p1ic-ca/runs/p64-log
bash scripts/smoke.sh corte-log-r1 corte 81003 > corte-log-r1.out 2>&1
python -I scripts/analisar_p1ic.py corte-log-r1 > corte-log-r1/resumo.json
```

## Resultado (`medicao.json`, `python -I scripts/medir_log.py ../p1ic-smoke/corte-r1 ../p1ic-smoke/corte-r2 corte-log-r1`)

Texto de `podman logs -t` de cada nó, a execução inteira (subida, carga de 300 s, convergência).

| Execução | Alvo | Log do alvo | Linhas | `ISPN005003` | `ISPN000136` | Linhas de pilha | Outros dois nós | Sonda no alvo: cache ignorado / AvailabilityException |
|---|---|---|---|---|---|---|---|---|
| `corte-r1` (antes) | isn2 | 268,29 MB | 2 073 585 | 20 647 | 18 131 | 1 995 141 | 0,10 e 0,19 MB | 2 515 / 13 878 |
| `corte-r2` (antes) | isn3 | 263,68 MB | 2 038 512 | 20 423 | 17 895 | 1 960 954 | 0,10 e 0,16 MB | 2 488 / 13 759 |
| `corte-log-r1` (depois) | isn3 | **0,09 MB** | 385 | 0 | 0 | 21 | 0,09 e 0,14 MB | 2 868 / 6 276 |

- O servidor continua recusando do mesmo jeito (a sonda vê as recusas do cache ignorado e as AvailabilityException no
  alvo); só a linha do log some. O controlador fez o mesmo: P1 em +14,5 s, efeito do AC-7 em 35 ms, desfazer
  `desfazer-alvo-sozinho` em +54,5 s (+49,6 e +50,0 s antes; a detecção do corte veio em +50,9 s, contra +45,6 e
  +44,9 s antes), trilha 1.3 completa, 0 linhas perdidas.
- As 21 linhas de pilha que sobraram são de 6 `ERROR [o.i.CLUSTER]` e 1 `ISPN012005` do `RestRequestHandler`, que não
  estão no arquivo.
- A fusão veio 9 s depois da cura (98 s contra 141 s antes). É n = 1, e a variação da fusão já medida vai de 0 a 96 s;
  não dá para atribuir isso ao log.
- Driver de log do contêiner: `k8s-file 512MB` (linha `log-driver` do `fases.tsv`): o `podman-compose` 1.5.0 passa o
  `max-size` do compose ao Podman.
