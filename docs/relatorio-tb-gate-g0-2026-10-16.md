# T-B: gate B-29, fase 1 (preparo da VM, suítes, cluster e G-0) — 2026-10-16

Worker-2, branch `test/b29-gate-vm` (de `b56c6ea`), worktree `wt-tb`. Sem push. **G-1 e G-5 (`kill -KILL`) NÃO foram executados**: aguardam o "go" do Gestor. Artefatos brutos em `runs/gate-b29/`.

## 1. Código na VM

- Ponta combinada em worktree temporário (`wt-tb-tmp`, branch `tmp/tb-vm-bundle`, não integrar): merge `--no-ff` de `chore/gitattributes-eol-lf` (`6dc7cbe`) sobre `b56c6ea` = **`7e8bab77c7cdf2f5d6fc1fab5cfb8ddcfff9674a`**. As branches originais não foram alteradas.
- `git bundle create` + `verify` ("complete history"), sha256 `242ebf02c450c44f832bce6010c0b1b879920325a57e4795ede3d99e8d3fafc2` (conferido na VM), `scp` com a chave `id_ed25519_tccf3`. Na VM: `git clone` em `~/tcc-gate` (o clone antigo `~/tcc-desenvolvimento` ficou intocado), `HEAD` = `7e8bab7…`, igual ao host.
- LF: `git ls-files --eol` na VM = 16 arquivos `i/lf w/lf attr/text` + `.gitkeep`; `grep -rlI $'\r' scripts cluster spec | wc -l` = **0**. (No Git Bash do Windows `grep -c $'\r'` em pipe dá falso positivo; `od -c` e `tr -cd '\r' | wc -c` = 0 confirmam LF no objeto.)
- Origem do `cluster/`: já em `b56c6ea` (`infinispan-cluster.xml`, `jgroups-tcp.xml`, `podman-compose.yml`), idênticos (`cmp`) aos de `feature/f3-baseline` e ao clone antigo da VM; nada foi integrado dela.

## 2. Ambiente (VM `server1`, 192.168.137.150)

Ubuntu 24.04, kernel `6.8.0-124-generic`, `tc utility, iproute2-6.1.0, libbpf 1.3.0`, podman 4.9.3 **rootful** (`netavark rootless=false`), JDK 17.0.19, Maven 3.8.7, numpy/scipy ok; nenhum binário faltando (`git tc nsenter flock ping curl tmux systemd-run java mvn python3`). Módulos carregados: `cls_u32`, `sch_ingress`, `act_gact`, `sch_prio`, `sch_netem`. **`/usr/lib/tc/` não existe** (sem `normal.dist`): relevante para T-C2 (`distribution normal`), não para B-29.

## 3. Suítes na VM (flock real)

| Suíte | Resultado |
|---|---|
| `inject-partition.test.sh` | **101 PASS / 0 FAIL / 1 SKIP** (T-par-6, escopo de B-29.1; igual ao esperado) |
| `inject-jitter.test.sh` | 35 PASS / 0 FAIL |

## 4. Cluster

Contêineres antigos (3 meses, `Exited`) removidos com `sudo podman-compose -f cluster/podman-compose.yml down` e recriados com `up -d` a partir de `~/tcc-gate/cluster`. Após 60 s: `isn1..3 Up (healthy)`. `curl --digest -u … /rest/v2/cache-managers/default/health` nas portas 11222/11223/11224: `HEALTHY`, `number_of_nodes":3`, nós `isn2-19096, isn1-47759, isn3-5889`, caches `sessions` e `counters` HEALTHY. IPs: isn1 10.89.1.2, isn2 10.89.1.3, isn3 10.89.1.4; PIDs 4111, 4247, 4656 (rede `cluster_infinispan-net`).

## 5. G-0: o kernel aceita clsact + u32 + drop no netns de isn3? **PASS**

`runs/gate-b29/g0.sh` (log bruto `g0.log`), em `tmux` dentro de `flock /var/lock/tcc-vm.lock`, com `guarda` de netns (`net:[4026531840]` do PID 1 ≠ `net:[4026532291]` de isn3) e dead-man `systemd-run --on-active=150s … inject-partition.sh --heal --minority isn3` armado antes e desarmado depois. Trechos brutos:

```
--- tc antes            qdisc noqueue 0: root refcnt 2
--- add clsact          rc=0
--- add filter ingress  rc=0   (u32 match ip src 192.0.2.1/32 action drop)
--- add filter egress   rc=0   (u32 match ip dst 192.0.2.1/32 action drop)
  match c0000201/ffffffff at 12   action order 1: gact action drop ... index 1
  match c0000201/ffffffff at 16   action order 1: gact action drop ... index 2
--- del clsact          rc=0
--- tc depois           qdisc noqueue 0: root refcnt 2
ok sem clsact em isn3: qdisc noqueue 0: root refcnt 2
--- saude pos-G-0       HEALTHY, 3 nos (x3)
```

Estado final (`final-estado-vm.txt`): `qdisc noqueue 0: root` em isn1, isn2 e isn3 (sem clsact); 0 timers `tcc-heal-*`; nenhum tmux; clone limpo. No netns de isn3: `eth0@if6` 10.89.1.4/24, **IPv4 apenas** (sem inet6); `ss -tn` mostra os TCP de JGroups (7800 e 57800) em IPv4.

## 6. Achados que o Senior precisa ver antes de G-1

1. **`tc filter show dev eth0 ingress` e `… egress` listam OS DOIS filtros** neste kernel/iproute2 (`g0b.log`: 2 matches em cada; também com `parent ffff:fff2` e `fff3`). Com só o filtro de ingress, `show ingress` dá 1 e `show egress` dá 0. Consequência: `soma_pkts` do `inject-partition.sh` (linhas 451-455 e 466-467) soma os mesmos contadores nos dois sentidos, então o critério de G-1 "ingress > 0 e egress > 0" fica cego à assimetria e o aviso de "um só sentido" nunca dispara. Mitigação sem alterar o script: em G-1 ler por filtro, separando pelo offset do `match` (`at 12` = src/ingress, `at 16` = dst/egress), como faz `cont_filtros` em `g1.sh`. Correção do script, se o Senior quiser, vai em `fix/b29-contadores-por-sentido` com teste T-par novo.
2. `inject-partition.sh` não tem guarda de netns (a guarda de R4 existe só em `inject-netdegrade.sh`). Aqui o operador usa `pid_de` (guarda) antes; isn1..3 têm netns próprio, então o risco é nulo, mas a lacuna existe.
3. O dry-run mostra que a verificação TCP roda com o `bash` do host (`nsenter … timeout 3 bash -c 'exec 3<>/dev/tcp/<ip>/7800'`); presente na VM.

## 7. Pré-G-1: comando exato proposto (NÃO executado)

Pré-condições: cluster `size=3 HEALTHY` (conferido), sem clsact nos três, Gestor disponível durante cerca de 2 min (janela de 90 s mais verificações). `--dry-run` (rc=0, `dryrun-g1.log`) previu: pares isn1/isn2, `flock /tmp/inject-partition-isn3.lock`, pré-limpeza, sonda, 2 filtros por par, verificação TCP, marcadores `start`/`end`, `sleep 90`, `del clsact`.

```bash
# na VM; o GO e digitado no terminal do tmux
cd ~/tcc-gate && flock /var/lock/tcc-vm.lock tmux new -s g-gate
# dentro do tmux:
REP=1 bash runs/gate-b29/g1.sh
```

`g1.sh` (versionado em `runs/gate-b29/g1.sh`; só vai à VM por novo bundle depois do "go"): exige `GO` digitado; `pid_de isn3`/`isn1` (guarda); confere size=3 e ausência de clsact; **dead-man `armar 90`** (heal em 180 s); inicia `bash scripts/inject-partition.sh --minority isn3 --duration 90 --marker runs/gate-b29/g1-rep1/marcadores.txt` em segundo plano; amostra em t=+8, 25, 45, 65 e 82 s os contadores por filtro, `isn3→isn1:7800` (esperado: falha), `isn1→isn2:7800` (esperado rc=0), HTTP e tempo de `:11224`, saúde das 3 portas e `ss -tn` de isn3; `wait` do injetor (rc 0, senão aborta com o dead-man armado); `sem_clsact`; `desarmar`; extrai mensagens de vista dos logs (G-3) e mede `t_merge` de `event=end` até HEALTHY com 3 nós (G-4). `REP=2,3` para as ≥ 3 janelas de G-4. `g1.sh` passou em `bash -n` mas nunca rodou.

Não executar sem "go": G-1 (acima), G-5 (`kill -TERM` e `kill -KILL` + `--heal`; deixa isn3 cortado até o `--heal`) e G-6 (coexistência com F3).

## 8. Pontos para o Senior / Gestor

- A VM está pronta: cluster size=3 HEALTHY, sem resíduo, sem timers, sem tmux.
- Decidir sobre o achado 6.1 (contadores por sentido) antes ou depois de G-1.
- "Go" do Gestor para G-1 e, depois, para G-5.
