# tcc-desenvolvimento

Protótipo experimental do Trabalho de Conclusão de Curso de Francesco Pagani Galvão, Bacharelado em Engenharia de Computação, Universidade Federal do Pampa (UNIPAMPA, Campus Bagé), sob orientação do Prof. Dr. Leonardo Bidese de Pinho.

**Tema:** Verificação formal de invariantes de sessão em *data grids*: replicação assíncrona, *failover* e controle orientado a SLO.

Este repositório materializa a frente experimental do trabalho. A frente teórica (mapeamento sistemático, fundamentação, redação) é mantida fora deste repositório e consome este código exclusivamente por meio dos resumos técnicos em [`docs/resumos-para-tcc/`](docs/resumos-para-tcc/).

## Vínculo com o TCC

A Tabela 1 do Capítulo 3 §3.3.5 da monografia (*Parâmetros experimentais e fundamentação na literatura*) fixa, em 22 linhas T1 a T22, os parâmetros operacionais do *baseline*. Este repositório realiza cada linha de forma observável e reproduzível. A coluna `Cobertura` da tabela abaixo indica onde cada parâmetro é implementado.

| ID | Parâmetro (Cap. 3 §3.3.5) | Cobertura no repositório |
|---|---|---|
| T1 | Modo de replicação (`DIST_SYNC` alvo + `DIST_ASYNC` controle) | `cluster/infinispan-cluster.xml` (perfis SYNC e ASYNC) |
| T2 | Número de nós: 3 | `cluster/podman-compose.yml` |
| T3 | `numOwners` = 2 | `cluster/infinispan-cluster.xml` |
| T4 | `numSegments` = 256 | `cluster/infinispan-cluster.xml` |
| T5 | *Payload* fixo de 1 KB | `workload/.../SessionOps.java` |
| T6 | Distribuição Zipfian ρ=0,99 | `workload/.../KeyGenerator.java` |
| T7 | Mistura 50/50 (S1) + 95/5 (S2) | `workload/.../WorkloadMain.java` (CLI) |
| T8 | 100 000 chaves | `workload/` + `scripts/run-baseline.sh` |
| T9 | 10⁶ operações por execução | idem |
| T10 | 30 repetições por cenário | `scripts/run-baseline.sh` |
| T11 | *Warm-up* 60 s ou 10 % | `workload/.../WorkloadMain.java` |
| T12 | Carga = 75 % do pico do piloto | `scripts/calibrate.sh` |
| T13 | p50, p95, p99 (p99,9 quando n ≥ 10⁶) | `analysis/percentis.py` |
| T14 | 10 min sem falha; 30 min com falha | `scripts/run-baseline.sh` |
| T15 | MTU 1500 | `cluster/podman-compose.yml` |
| T16 | FD\_ALL3 padrão (8 s / 40 s) | `cluster/infinispan-cluster.xml` |
| T17 | F1: *crash* silencioso de 60 s (isolamento do `eth0` + `podman kill --signal KILL`, detecção pelo FD\_ALL3) | `scripts/inject-crash.sh` |
| T18 | F3: +50 ms p99 lognormal | `scripts/inject-jitter.sh` |
| T19 | M1-M7 expostas via OpenMetrics | `cluster/` + `scripts/collect-metrics.sh` |
| T20 | Endpoint OpenMetrics | nativo do Infinispan 15 |
| T21 | TLC 2-2-2, 3-3-2, 3-3-3 | `spec/SessionStore.tla` + `spec/run-tlc.sh` |
| T22 | Mann-Whitney + *bootstrap* 10 000 | `analysis/bootstrap_ic.py` + `analysis/compare_scenarios.py` |

## Estrutura de pastas

```
tcc-desenvolvimento/
├── cluster/                # podman-compose.yml + infinispan-cluster.xml
├── workload/               # projeto Maven Java 17 (Hot Rod client)
├── scripts/                # injeção de falha, warmup, coleta de métricas
├── spec/                   # SessionStore.tla + cfg + runner TLC
├── analysis/               # parsing, percentis, bootstrap, Mann-Whitney
├── runs/                   # saídas brutas e summary.json (ver D-005)
├── docs/                   # documentação técnica + resumos para o TCC
│   ├── planejamento-inicial.md
│   ├── pull-request-template.md
│   └── resumos-para-tcc/   # consumidos pelo trabalho acadêmico
├── README.md
├── CONTRIBUTING.md
├── LICENSE
└── .gitignore
```

## Quickstart (planejado — válido após blocos 1 a 4 do *backlog*)

### Subir o *cluster* Infinispan

```bash
cd cluster
podman-compose up -d
podman ps --filter "label=infinispan" --format "table {{.Names}}\t{{.Status}}"
```

### Rodar o *workload* sem falha (Cenário S1, 50/50)

```bash
cd workload
mvn -q package
java -jar target/session-workload-0.1.0-SNAPSHOT.jar \
    --scenario S1 \
    --duration 600 \
    --warmup-min-sec 60 \
    --ops 1000000 \
    --servers 127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224 \
    --csv-dir runs/S1-none
```

O *cluster* já foi validado de ponta a ponta: forma `cluster_size=3`
HEALTHY (discovery TCPPING, imagem pinada `15.0.21.Final`) e o *workload*
grava a trilha de eventos `op_id,operation,start_ns,end_ns,replica,return_code,key`
consumida por `analysis/`. A bateria *baseline* S1/S2 × {none, F1} é
orquestrada por `scripts/run-baseline.sh`.

### Rodar o *model checking* inicial

```bash
cd spec
./run-tlc.sh small         # configuração 2-2-2
./run-tlc.sh medium        # configuração 3-3-2
./run-tlc.sh full          # configuração 3-3-3 (pode estourar timebox)
```

### Analisar resultados de uma rodada

```bash
cd analysis
python percentis.py ../runs/S1-none           # glob nao-recursivo: aponte para a pasta da rep
python bootstrap_ic.py ../runs/S1-none
python compare_scenarios.py ../runs/S1-none ../runs/S1-F1
```

### Campanhas S3 do controlador (W-6, B-46; só na VM, com GO)

Matriz (pré-registro fixa as sementes e o número de blocos; nada tem padrão):

```bash
python3 -I scripts/campanha_s3_matriz.py gerar --modo avaliacao \
  --nucleo "ASYNC-F1:teste:8,ASYNC-F2:teste:8,ASYNC-none:descritiva:4,SYNC-F2:referencia:4" \
  --extras "ASYNC-F4:teste:8,SYNC-F1:referencia:4,SYNC-F4:referencia:4" \
  --ordem-seed <N> --s0 <N> --passo-semente <N> --i0 <N> --b0 <N> --saida runs/campanha-s3/matriz.tsv
```

Plano sem executar nada, depois a fase (dentro da VM, em `tmux`, com o amostrador h4 já iniciado):

```bash
scripts/run-campanha-s3.sh --dry-run --matriz <tsv> --matriz-sha256 <hex> --fase nucleo \
  --parametros <campanha-s3.json> --parametros-sha256 <hex> --controlador <controlador.json> --controlador-sha256 <hex> \
  --referencia-async <referencia-S3-async.json> --referencia-async-sha256 <hex> \
  --referencia-sync <referencia-S3-sync.json> --referencia-sync-sha256 <hex> --saida runs/campanha-s3
# mesma linha sem --dry-run e com --jar <jar> --jar-sha256 <hex> --boot-esperado <boot_id> --h4-id diag-<UTC>
```

Uma referência de p99 por modo de replicação presente na fase (o programa recusa a de outro perfil). O amostrador h4
é iniciado com `--id diag-<UTC>` (o `amostrador-infra.sh` não muda enquanto o *rebaseline* não fechar).

Detalhes e porquês: `docs/implementacao.md` (TD-014); testes: `scripts/run-campanha-s3.test.sh` e `docs/testes.md`.

## Fluxo de desenvolvimento

O trabalho neste repositório segue um protocolo estruturado em quatro passos, descrito em `docs/planejamento-inicial.md`:

1. **Planejamento técnico** de cada incremento, com critérios de aceite e *branch* nominal.
2. **Aprovação do autor** antes da execução.
3. **Implementação** em *branch* específica, com testes e documentação técnica.
4. **Revisão e validação do autor** antes de qualquer *merge* em `main` ou `git push`.

Resumos técnicos das entregas são depositados em `docs/resumos-para-tcc/` para alimentar a escrita do TCC. Detalhes em [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Estado atual

`feature/bootstrap-repo` — *bootstrap* inicial em curso. Nenhum bloco do *backlog* finalizado ainda. Consulte [`docs/planejamento-inicial.md`](docs/planejamento-inicial.md).

## Licença

MIT. Ver [`LICENSE`](LICENSE).
