# A4: `ignored-caches` mandado ao nó suspeito, medido no cluster local (06/11/2026)

Branch `test/a4-ignored-caches`, criada da tag `int/2026-11-06-bench` (`cc2b71c`) na worktree `wt-a4`. Nenhum código do
repositório mudou: tudo aqui é harness de medição. Relatório: `docs/relatorio-a4-2026-11-06.md`.

## Ambiente

- Máquina do Podman do WSL (`podman-machine-default`), cluster local próprio: projeto `a4`, contêineres `a4-isn1..3`,
  rede `a4-net`, portas 12901 a 12903 (`cluster/podman-compose.a4.yml`, cópia do `cluster/podman-compose-sem-caches.yml`
  da base com nomes, rede e portas próprios). `cluster/infinispan-sem-caches.xml` e `cluster/jgroups-tcp.xml` são
  cópias byte a byte dos da base. Os seis caches são criados pelo `scripts/cria-caches.sh` da base, com leitura de volta
  nos três nós.
- Ao mesmo tempo, a partir de 16:49Z, outro Worker rodou o cluster `a1-isn1..3` (portas 12801 a 12803) na mesma máquina
  do Podman, e um TLC rodava no host. Cada execução registra em `lote-*.out` os contêineres de pé e a memória livre.
- Jar: `mvn -q -o -DskipTests package` em `wt-a4/workload` (`session-workload-0.1.0-SNAPSHOT.jar`, SHA-256
  `1bf157dd89c8e680d99c30e76b8ad481d84874ca689acfec52644155010631a8`, também na primeira linha de cada `fases.tsv`).

## Scripts (`scripts/`)

| Arquivo | Papel |
|---|---|
| `a4.sh ROTULO CASO SEMENTE` | uma execução: sobe o cluster, cria os caches, carga S3 ASYNC sem controlador, sondas, ação, cura, desfazer, logs, desce |
| `lib-a4.sh` | funções (cópia do `lib-w2b.sh` do smoke do W-2b com os nomes `a4`) |
| `particao-a4.sh` | corte e cura do alvo por nftables no netns do contêiner (cópia do `particao-w2b.sh`) |
| `SondaA4.java` | sonda Hot Rod pinada num nó (BASIC, sem retentativa, prazo 1 s), `put` e `get` nos dois caches ASYNC a cada 25 ms (alvo) ou 100 ms (outros) |
| `vigia_a4.py` | lista de caches ignorados de cada nó a cada 100 ms; `get-availability` dos caches ASYNC a cada 500 ms |
| `acao_a4.py` | pedidos REST da ação (POST/DELETE `ignored-caches`, POST `?action=stop`) com instante de envio e de resposta |
| `analisar_a4.py DIR` | resumo de uma execução (`resumo.json`) |
| `tabela_a4.py RAIZ EXEC...` | tabela por execução (`tabela-a4.tsv`) |
| `lote.sh "ROT CASO SEMENTE"...` | lote em sequência, com a análise de cada execução |

Casos de `a4.sh` (instantes relativos à linha `S3:` da carga, `o`): corte em `o+30 s`, ação em `o+35 s`, cura em
`o+120 s`; desfazer 15 s depois de a fusão ser vista (tamanho 3 no alvo e no isn1).

| Caso | Ação no alvo |
|---|---|
| `corte-ignorar` | POST dos dois caches ASYNC em paralelo; depois da fusão, DELETE dos dois em paralelo |
| `corte-sessoes` | POST só do `sessions-async`; depois da fusão, DELETE só dele |
| `corte-sequencial` | POST dos dois caches em sequência (o segundo depois da resposta do primeiro); DELETE também em sequência |
| `corte-seq-detec` | como `corte-sequencial`, mas os DELETE (em sequência, no alvo) saem assim que o alvo lê `DEGRADED_MODE` no `get-availability` do `sessions-async`, antes da cura; nenhum DELETE depois da fusão |
| `corte-seq-maioria` | como `corte-sequencial`, mas o desfazer é **um** DELETE do `sessions-async` mandado a um nó da maioria depois da fusão |
| `corte-parar` | POST `/rest/v2/server?action=stop` |
| `corte-controle` | nenhuma ação |
| `semcorte-ignorar` | sem corte; POST dos dois caches em paralelo em `o+35 s`, DELETE dos dois em `o+55 s` |
| `semcorte-sessoes` | sem corte; POST só do `sessions-async` em `o+35 s`, DELETE em `o+55 s` |

## Ordem das execuções

1. `piloto-semcorte` (semente 72000): piloto do harness, válido como execução do caso `semcorte-ignorar`.
2. `lote-1-interrompido.out`: `corte-ignorar-r1` (71001) e `semcorte-ignorar-r1` (72001); o lote foi parado depois
   dessas duas, para trocar o destino do log (abaixo).
3. `invalidas/lote-2-interrompido.out`: quatro execuções inválidas (`invalidas/*-tentativa1`): a `a4.sh` saiu depois do
   corte por variável não definida (`CACHES_ACAO`), deixando carga e sondas de pé; a seguinte falhou na criação dos
   caches com esses processos ainda batendo nas portas. Todos foram parados e o lote refeito.
4. `lote-3.out`: dez execuções válidas, de `corte-ignorar-r2` a `corte-controle-r1`.
5. `lote-4.out`: `corte-seq-detec-r1` (78001) e `corte-seq-maioria-r1` (79001), as duas regras de desfazer, criadas
   depois de ver na `corte-sequencial-r1` que o primeiro DELETE publica o resto da lista do alvo.

## Mudanças no harness durante a sessão (registradas também no cabeçalho dos scripts)

- **Log k8s-file** (depois da `corte-ignorar-r1`): com o padrão `journald` da máquina do Podman, cada operação recusada
  grava um ERROR com pilha (`ISPN005003`) e o journald passou a descartar linhas do `podman.service` inteiro ("Suppressed
  N messages", de todos os contêineres, inclusive de outros projetos). Nas execuções `piloto-semcorte`,
  `corte-ignorar-r1` e `semcorte-ignorar-r1`, os logs dos servidores estão incompletos (sem as vistas do corte).
- **Vigia sem `GET /rest/v2/container`** (depois da `corte-ignorar-r2`): no nó isolado, esse GET espera resposta do
  cluster dentro de uma thread da REST que também atende os pedidos de `ignored-caches` (despejo de threads em
  `corte-ignorar-r2/threads-alvo-mais3s.txt.gz`). `corte-ignorar-r1` e `-r2` rodaram com esse GET.
- **Casos novos** `corte-sessoes`, `semcorte-sessoes` e `corte-sequencial`, depois de ver que pedidos simultâneos de
  `ignored-caches` no mesmo nó se perdem (`semcorte-ignorar-r1`, POST; `corte-ignorar-r2`, DELETE); depois,
  `corte-seq-detec` e `corte-seq-maioria` (regras de desfazer).
- `analisar_a4.py` e `tabela_a4.py` ganharam campos ao longo da sessão; todos os `resumo.json` e a `tabela-a4.tsv`
  foram refeitos no fim com a versão final, sobre os logs já comprimidos.

## Comandos

```bash
cd wt-a4/workload && mvn -q -o -DskipTests package
cd wt-a4/runs/a4-teste
bash scripts/a4.sh piloto-semcorte semcorte-ignorar 72000
bash scripts/lote.sh "corte-ignorar-r1 corte-ignorar 71001" "semcorte-ignorar-r1 semcorte-ignorar 72001" ...   # parado depois destas duas
bash scripts/lote.sh "corte-ignorar-r2 corte-ignorar 71002" "semcorte-sessoes-r1 semcorte-sessoes 75001" \
  "corte-sessoes-r1 corte-sessoes 76001" "corte-parar-r1 corte-parar 73001" "semcorte-sessoes-r2 semcorte-sessoes 75002" \
  "corte-sessoes-r2 corte-sessoes 76002" "corte-parar-r2 corte-parar 73002" "corte-sequencial-r1 corte-sequencial 77001" \
  "corte-sessoes-r3 corte-sessoes 76003" "corte-controle-r1 corte-controle 74001" > lote-3.out 2>&1
bash scripts/lote.sh "corte-seq-detec-r1 corte-seq-detec 78001" "corte-seq-maioria-r1 corte-seq-maioria 79001" > lote-4.out 2>&1
python -I scripts/analisar_a4.py <execucao> > <execucao>/resumo.json
python -I scripts/tabela_a4.py . piloto-semcorte semcorte-ignorar-r1 semcorte-sessoes-r1 semcorte-sessoes-r2   corte-sessoes-r1 corte-sessoes-r2 corte-sessoes-r3 corte-ignorar-r1 corte-ignorar-r2 corte-sequencial-r1   corte-seq-detec-r1 corte-seq-maioria-r1 corte-parar-r1 corte-parar-r2 corte-controle-r1 > tabela-a4.tsv
```

## O que fica fora do git

Pelo `.gitignore` da base (`runs/**/*.csv`, `*.log`, `*.txt`), ficam só na worktree, com SHA-256 no `MANIFEST.sha256`:
`rep-001.csv`, `s3-acoes-rep-001.csv` e `violacoes-rep-001.csv` da carga, `stdout.log`, `comando.txt`, `compose-*.txt`,
`corte.txt`, `cura.txt`, `marcadores.log` e `exit.txt`. Os logs dos servidores (`servidores/isnN.log.gz`), as sondas
(`sonda-isnN.tsv.gz`, `sonda-isnN.out.gz`), o stderr da carga (`stderr.log.gz`) e o despejo de threads vão comprimidos
e entram no git. `.gitattributes` com `* -text`, para o MANIFEST continuar valendo depois do checkout.
