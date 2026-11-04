#!/usr/bin/env bash
# runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.test.sh -- teste SEM VM do ensaio-f1.sh, com shims (sudo, podman com
# inspect/logs -t/events/kill/start, nsenter, ip, tc, readlink, flock, systemctl, curl, java com stub de workload e de
# VerificadorSanidade, python3, uname, hostname, pgrep) numa arvore falsa $HOME/tcc-gate e com um --rb-dir falso montado a
# partir dos blobs de 20805b0 (inject-crash.sh REAL; run-rebaseline.sh real com as constantes de tempo encurtadas).
# Prova: caminho feliz das 4 execucoes; cada pre-condicao que falha aborta antes de qualquer injecao; GO ausente ou com
# rotulo errado nao injeta; cada F-n violado isoladamente; cada parada (Δdet 29 s, volta acima do prazo, injetor exit 1, 2
# e 5); --so 3; Ctrl-C real ao grupo; a senha nunca aparece no vm-UTC; constantes e montar_cmd vem do --rb-dir (THREADS=8
# muda a linha de comando); nenhum tc de escrita e nenhum podman kill/start/ip link set sai do script fora do injetor;
# --avaliar refaz a avaliacao igual; rc lido na mesma linha; copia verbatim do s5.sh; tabela de hashes = 20805b0 + jar.
# NAO prova: podman, Infinispan, o workload Java nem a VM. NAO toca na VM nem em rede.
# Rodar sozinho:  bash runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.test.sh      Exit 0 se tudo passar.
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$AQUI/../.." && pwd)"
EN="$AQUI/ensaio-f1.sh"
P=0; F=0; T0=$(date +%s)
ok()     { if [ "$2" = "$3" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (esperado '$2', obtido '$3')"; F=$((F+1)); fi; }
contem() { if printf '%s' "$3" | grep -qF -- "$2"; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (sem '$2')"; F=$((F+1)); fi; }
nao()    { if printf '%s' "$3" | grep -qF -- "$2"; then echo "FAIL: $1 (com '$2')"; F=$((F+1)); else echo "PASS: $1"; P=$((P+1)); fi; }
existe() { ok "$1" 1 "$([ -s "$2" ] && echo 1 || echo 0)"; }
antes()  { local la lb; la=$(grep -nE -m1 -- "$3" "$2" | cut -d: -f1); lb=$(grep -nE -m1 -- "$4" "$2" | cut -d: -f1)
  if [ -n "$la" ] && [ -n "$lb" ] && [ "$la" -lt "$lb" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 ('$3'@${la:-?} '$4'@${lb:-?})"; F=$((F+1)); fi; }

[ -f "$EN" ] || { echo "FAIL: $EN nao existe (vermelho esperado antes do feat)"; echo "RESUMO: 0 PASS, 1 FAIL"; exit 1; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
BIN="$T/bin"; ST="$T/state"; mkdir -p "$BIN"
PW='S3nh4-Teste-Xq9'
BOOT=5d7119b8-3b50-435d-bd0d-cbdb6bd410df
JARREAL="$REPO/../dados-vm/rebaseline-2026-10/jar/session-workload-0.1.0-SNAPSHOT.jar"

# ---------------- shims
cat > "$BIN/sudo" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = -n ]; then shift; [ -z "${SHIM_SUDO_FALHA:-}" ] || exit 1; [ "$1" = true ] && exit 0; fi
exec "$@"
EOF
cat > "$BIN/podman" <<'EOF'
#!/usr/bin/env bash
case "$(tr '\0' ' ' < /proc/$PPID/cmdline 2>/dev/null)" in *inject-crash*) O=injetor;; *ensaio-f1.sh*) O=ensaio;; *) O=outro;; esac
echo "podman[$O] $*" >> "$SHIM_LOG"
S="$SHIM_STATE"; n="${@: -1}"
g() { cat "$S/$1/$2" 2>/dev/null; }
tsn() { local t="$1"; printf '%s.%09d+00:00' "$(date -u -d "@$((t / 1000000000))" +%Y-%m-%dT%H:%M:%S)" $((t % 1000000000)); }
case "$1" in
  ps) for m in isn1 isn2 isn3; do echo "$m id-$m $(g $m status)"; done;;
  inspect)
    [ -d "$S/$n" ] || { echo "Error: no such object: $n" >&2; exit 125; }
    if [ "$2" != -f ]; then echo "[{\"Name\":\"$n\",\"State\":{\"Status\":\"$(g $n status)\",\"Pid\":$(g $n pid),\"ExitCode\":$(g $n exit)}}]"; exit 0; fi
    case "$3" in
      '{{.State.Status}}') g $n status;;
      '{{.State.Pid}}') if [ "$(g $n status)" = running ]; then g $n pid; else echo 0; fi;;
      '{{.State.ExitCode}}') g $n exit;;
      '{{.Created.Unix}} {{.State.StartedAt.Unix}}') echo "$(g $n created) $(g $n started)";;
      '{{.State.StartedAt.Unix}}') g $n started;;
      '{{.Id}}') echo "${n}aaaabbbbccccdddd";;
      *IPAddress*) echo "$(g $n ip) ";;
      *Mounts*) echo "$S/infinispan.xml";;
      *) echo "shim podman: formato inesperado: $3" >&2; exit 99;;
    esac;;
  kill) n="$2"; c="${SHIM_EXITCODE:-137}"
    echo exited > "$S/$n/status"; echo "$c" > "$S/$n/exit"; date +%s%N > "$S/kill_ns"
    echo "{\"ID\":\"x\",\"Name\":\"$n\",\"Status\":\"kill\",\"Type\":\"container\"}" >> "$S/events.$n"
    echo "{\"ID\":\"x\",\"Name\":\"$n\",\"Status\":\"died\",\"Type\":\"container\",\"ContainerExitCode\":$c}" >> "$S/events.$n"
    [ -z "${SHIM_DIED_EXTRA:-}" ] || echo "{\"ID\":\"x\",\"Name\":\"$n\",\"Status\":\"died\",\"Type\":\"container\",\"ContainerExitCode\":$SHIM_DIED_EXTRA}" >> "$S/events.$n";;
  start) n="$2"; [ -z "${SHIM_START_FALHA:-}" ] || { echo "Error: start falhou" >&2; exit 125; }
    p=$(( $(g $n pid) + 100 )); echo "$p" > "$S/$n/pid"; echo running > "$S/$n/status"; date +%s > "$S/$n/started"
    date +%s%N > "$S/start_ns"; echo "${SHIM_ETH0_NOVO:-UP}" > "$S/eth0.$p"; echo "$n" > "$S/pid.$p"
    [ -z "${SHIM_IP_NOVO:-}" ] || echo "$SHIM_IP_NOVO" > "$S/$n/ip"
    echo "{\"ID\":\"x\",\"Name\":\"$n\",\"Status\":\"start\",\"Type\":\"container\"}" >> "$S/events.$n"; echo "$n";;
  events) echo "$$" >> "$S/pids.events"; touch "$S/events.isn2"; exec tail -n 0 -f "$S/events.isn2";;
  logs) iso=$(cat "$S/iso_ns" 2>/dev/null); kl=$(cat "$S/kill_ns" 2>/dev/null); stt=$(cat "$S/start_ns" 2>/dev/null)
    [ -n "$iso" ] || exit 0
    case "$n" in
      isn1|isn3) v="SHIM_DDET_$n"; dd="${!v:-${SHIM_DDET:-38}}"
        [ -n "${SHIM_SEM_VISTA:-}" ] || echo "$(tsn $((iso + dd * 1000000000))) 2026-11-04 10:00:40,000 INFO  [org.infinispan.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn1-1001|6] (2) [isn1-1001, isn3-1003]"
        [ -z "$stt" ] || echo "$(tsn $((stt + 3000000000))) 2026-11-04 10:01:30,000 INFO  [org.infinispan.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn1-1001|7] (3) [isn1-1001, isn3-1003, isn2-2002]";;
      isn2) echo "$(tsn $((iso - 1000000000))) 2026-11-04 10:00:01,000 INFO  [org.infinispan.CLUSTER] ISPN000078: Starting JGroups channel cluster-tcc"
        [ -z "${SHIM_F5:-}" ] || echo "$(tsn $((kl - 500000000))) 2026-11-04 10:00:02,000 INFO  [org.infinispan.CLUSTER] ISPN000390: Persisted state, version=15.0.21.Final"
        if [ -n "$stt" ]; then
          [ -z "${SHIM_693:-}" ] || echo "$(tsn $((stt + 500000000))) 2026-11-04 10:01:00,500 WARN  [org.infinispan.CONFIG] ISPN000693: Dangling lock file"
          echo "$(tsn $((stt + 1000000000))) 2026-11-04 10:01:01,000 INFO  [org.infinispan.CONTAINER] ISPN000389: Loaded global state, version=15.0.21.Final"
          echo "$(tsn $((stt + 2000000000))) 2026-11-04 10:01:02,000 INFO  [org.infinispan.SERVER] ISPN080001: Infinispan Server 15.0.21.Final started in 9000ms"
        fi;;
    esac;;
  *) echo "shim podman: inesperado: $*" >&2; exit 99;;
esac
exit 0
EOF
cat > "$BIN/nsenter" <<'EOF'
#!/usr/bin/env bash
# nsenter -t <pid> -n <cmd...>
export SHIM_NS_PID="$2"; shift 3; exec "$@"
EOF
cat > "$BIN/ip" <<'EOF'
#!/usr/bin/env bash
case "$(tr '\0' ' ' < /proc/$PPID/cmdline 2>/dev/null)" in *inject-crash*) O=injetor;; *ensaio-f1.sh*) O=ensaio;; *) O=outro;; esac
echo "ip[$O] $*" >> "$SHIM_LOG"
S="$SHIM_STATE"; p="${SHIM_NS_PID:-host}"; e=$(cat "$S/eth0.$p" 2>/dev/null)
fl() { if [ "$e" = UP ]; then echo "<BROADCAST,MULTICAST,UP,LOWER_UP>"; else echo "<BROADCAST,MULTICAST>"; fi; }
case "$*" in
  "link show dev eth0"|"-o link show dev eth0") [ -n "$e" ] || { echo 'Device "eth0" does not exist.' >&2; exit 1; }
    echo "2: eth0@if9: $(fl) mtu 1500 qdisc noqueue state $e mode DEFAULT group default qlen 1000";;
  "link set dev eth0 down") [ -n "${SHIM_LINK_NAO_DESCE:-}" ] || echo DOWN > "$S/eth0.$p"
    [ "$(cat "$S/pid.$p" 2>/dev/null)" != isn2 ] || date +%s%N > "$S/iso_ns";;
  "link set dev eth0 up") echo UP > "$S/eth0.$p";;
  "-br link") echo "lo               UNKNOWN        00:00:00:00:00:00 <LOOPBACK,UP,LOWER_UP>"
    echo "eth0@if9         ${e:-?}             8a:1c:00:00:00:02 $(fl)";;
  *) echo "shim ip: inesperado: $*" >&2; exit 99;;
esac
EOF
cat > "$BIN/tc" <<'EOF'
#!/usr/bin/env bash
case "$(tr '\0' ' ' < /proc/$PPID/cmdline 2>/dev/null)" in *inject-crash*) O=injetor;; *ensaio-f1.sh*) O=ensaio;; *) O=outro;; esac
echo "tc[$O] $*" >> "$SHIM_LOG"
if [ -n "${SHIM_NETEM:-}" ]; then echo "qdisc netem 8001: root refcnt 2 limit 1000 delay 20ms 13ms"; else echo "qdisc noqueue 0: root refcnt 2"; fi
EOF
cat > "$BIN/readlink" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  /proc/1/ns/net) echo 'net:[4026531840]';;
  /proc/*/ns/net) if [ -n "${SHIM_NETNS_HOST:-}" ]; then echo 'net:[4026531840]'; else p=${1#/proc/}; echo "net:[40265${p%%/*}]"; fi;;
  *) exec /usr/bin/readlink "$@";;
esac
EOF
cat > "$BIN/flock" <<'EOF'
#!/usr/bin/env bash
[ "${SHIM_LOCK:-ocupado}" = livre ]
EOF
cat > "$BIN/systemctl" <<'EOF'
#!/usr/bin/env bash
case "$1" in list-timers) [ -z "${SHIM_TIMERS:-}" ] || echo "Wed 2026-11-04 n/a tcc-heal-1-2.timer tcc-heal-1-2.service";; esac
exit 0
EOF
cat > "$BIN/uname" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = -r ]; then echo "${SHIM_KERNEL:-6.8.0-147-generic}"; else exec /usr/bin/uname "$@"; fi
EOF
cat > "$BIN/hostname" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = -I ]; then echo "172.31.251.37 10.89.0.1 "; else echo server1; fi
EOF
cat > "$BIN/pgrep" <<'EOF'
#!/usr/bin/env bash
[ -n "${SHIM_PGREP:-}" ] && { echo 4242; exit 0; }; exit 1
EOF
cat > "$BIN/python3" <<'EOF'
#!/usr/bin/env bash
cat > /dev/null
echo '{"C1": {"ok": true, "dif_1_3_pp": 0.4}, "C2": {"ok": true, "frac_pct": 5.1}, "C3": {"ok": true, "k": 3, "n": 60}}'
EOF
# curl: /health conforme o estado de isn2 (fora ou dentro de SHIM_VOLTA_S s do start: REBALANCING); ?action=size; ssh
cat > "$BIN/curl" <<'EOF'
#!/usr/bin/env bash
url="${@: -1}"; o=; w=; prev=
for a in "$@"; do case "$prev" in -o) o="$a";; -w) w="$a";; esac; prev="$a"; done
echo "curl $url" >> "$SHIM_LOG"
S="$SHIM_STATE"; code=200; body=; ex=0
case "$url" in
  *:22/) printf '0.000250'; exit 1;;
  *action=size*) c="${url##*/caches/}"; c="${c%%\?*}"
    if [ -n "${SHIM_SEM_ASYNC:-}" ] && [[ "$c" == *-async ]]; then code=404; body="ISPN012010: cache $c not found"
    elif [ "${SHIM_CACHE_CHEIO:-}" = "$c" ]; then body=7; else body=0; fi;;
  */health) p="${url#*127.0.0.1:}"; p="${p%%/*}"
    s2=$(cat "$S/isn2/status"); st0=$(cat "$S/start_ns" 2>/dev/null || echo 0); now=$(date +%s%N)
    volta=1; [ "$s2" = running ] || volta=0
    [ "$st0" = 0 ] || [ "$now" -ge $(( st0 + ${SHIM_VOLTA_S:-1} * 1000000000 )) ] || volta=0
    [ -z "${SHIM_HEALTH_RUIM:-}" ] || volta=0
    if [ "$p" = 11223 ] && [ "$s2" != running ]; then code=000; ex=7
    elif [ "$volta" = 1 ]; then body='{"cluster_health":{"cluster_name":"cluster-tcc","health_status":"HEALTHY","number_of_nodes":3,"node_names":["a","b","c"]},"cache_health":[{"status":"HEALTHY","cache_name":"sessions"}]}'
    else body='{"cluster_health":{"cluster_name":"cluster-tcc","health_status":"HEALTHY_REBALANCING","number_of_nodes":2,"node_names":["a","c"]},"cache_health":[{"status":"HEALTHY_REBALANCING","cache_name":"sessions"}]}'; fi;;
  *) code=404;;
esac
if [ -n "$o" ]; then printf '%s' "$body" > "$o"; else printf '%s' "$body"; fi
if [ -n "$w" ]; then r="${w//%\{http_code\}/$code}"; r="${r//%\{time_connect\}/0.000300}"; r="${r//%\{time_total\}/0.003000}"; printf '%s' "$r"; fi
exit "$ex"
EOF
# java: -version; -jar (stub do workload: ancora, CSV minimo); -cp ... VerificadorSanidade (stub)
cat > "$BIN/java" <<'EOF'
#!/usr/bin/env bash
case "$*" in *-version*) echo 'openjdk version "21.0.5" 2024-10-15' >&2; exit 0;; esac
S="$SHIM_STATE"; echo "$$" >> "$S/pids.java"
arg() { local k="$1"; shift; printf '%s\n' "$@" | awk -v k="$k" 'p { print; exit } $0 == k { p = 1 }'; }
if [ "$1" = -jar ]; then
  echo "java-workload $*" >> "$S/java.log"
  dur=$(arg --duration "$@"); csv=$(arg --csv-dir "$@"); warm=$(arg --warmup-min-sec "$@"); seed=$(arg --seed "$@")
  trap 'exit 143' TERM
  echo "Configuracao: cenario=S1 seed=$seed rep=1"
  echo "Reset: reset_state=on caches=sessions,counters antes=0,0 depois=0,0 ms=3"
  sleep "${SHIM_ANCORA_ATRASO:-0.3}" & wait $!
  [ -n "${SHIM_SEM_ANCORA:-}" ] || echo "Warm-up ate +${warm}s; medicao a partir de +${warm}s; total ${dur}s"
  t0=$(date +%s%N); sleep "$dur" & wait $!
  { echo "op_id,operation,start_ns,end_ns,replica,return_code,key"
    for k in $(seq 0 $((dur * 2))); do s=$((t0 + k * 500000000))
      if [ $((k % 5)) = 3 ]; then rc=ERROR_TRANSPORT; l=900000000; else rc=VALID; l=2000000; fi
      echo "$k,Validate,$s,$((s + l)),isn1,$rc,sid-$k"; done; } > "$csv/rep-001.csv"
  echo "op_id,invariante,classe" > "$csv/violacoes-rep-001.csv"
  echo "Total de operacoes: $((dur * 2 + 1))"
  exit "${SHIM_WEXIT:-0}"
fi
echo "java-verificador $*" >> "$S/java.log"
saida=$(arg --saida "$@")
echo "{\"veredito\": \"${SHIM_VEREDITO:-OK}\", \"motivos\": []}" > "$saida"; echo "veredito ${SHIM_VEREDITO:-OK}"
exit "${SHIM_VEXIT:-0}"
EOF
chmod +x "$BIN"/*

# ---------------- --rb-dir falso (blobs de 20805b0; constantes de tempo encurtadas) e arvore falsa
ARQS_RB="analysis/rebaseline.py runs/rebaseline/matriz.tsv scripts/inject-crash.sh scripts/inject-jitter.sh scripts/rebaseline_matriz.py scripts/run-rebaseline.sh workload/target/session-workload-0.1.0-SNAPSHOT.jar"
encurtar() { sed -e 's/^readonly DURACAO=300 WARMUP_MIN=30 THREADS=4 /readonly DURACAO=8 WARMUP_MIN=1 THREADS=4 /' \
  -e 's/^readonly INJ_OFFSET=49 /readonly INJ_OFFSET=2 /' -e 's/^readonly F1_DUR=60 /readonly F1_DUR=2 /' \
  -e 's/^readonly K1_JANELA_S=60 K1_PERIODO_S=5 K1_TIMEOUT_S=600 /readonly K1_JANELA_S=1 K1_PERIODO_S=1 K1_TIMEOUT_S=6 /' \
  -e 's/^readonly F1_VOLTA_TIMEOUT_S=180 F1_VOLTA_PERIODO_S=5 /readonly F1_VOLTA_TIMEOUT_S=6 F1_VOLTA_PERIODO_S=1 /' \
  -e 's/^readonly ANCORA_TIMEOUT_S=120 ANCORA_POLL_S=0.2/readonly ANCORA_TIMEOUT_S=6 ANCORA_POLL_S=0.2/'; }
tabela_rb() { ( cd "$RBF" && sha256sum -b $ARQS_RB ) > "$T/hashes"; LISTA=$(sha256sum < "$T/hashes" | cut -d' ' -f1); ambiente; }
nova_arvore() {
  rm -rf "$T/home" "$ST" "$T/rb" "$T/s7"; mkdir -p "$ST"; : > "$T/shim.log"; echo "$BOOT" > "$T/boot_id"
  RBF="$T/rb"; mkdir -p "$RBF/analysis" "$RBF/runs/rebaseline" "$RBF/scripts" "$RBF/workload/target" "$RBF/cluster"
  for f in analysis/rebaseline.py runs/rebaseline/matriz.tsv scripts/inject-crash.sh scripts/inject-jitter.sh scripts/rebaseline_matriz.py cluster/infinispan-cluster.xml; do
    git -C "$REPO" show "20805b0:$f" > "$RBF/$f"; done
  git -C "$REPO" show 20805b0:scripts/run-rebaseline.sh | encurtar > "$RBF/scripts/run-rebaseline.sh"
  echo "jar falso" > "$RBF/workload/target/session-workload-0.1.0-SNAPSHOT.jar"
  cp "$RBF/cluster/infinispan-cluster.xml" "$ST/infinispan.xml"; tabela_rb
  G="$T/home/tcc-gate"; mkdir -p "$G/runs/ensaio-f1-silencioso-2026-11-04"; cp "$EN" "$G/runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh"
  ANTD="$T/s7/vm-20261104T050000Z"; mkdir -p "$ANTD"; local agora; agora=$(date +%s)
  echo "boot_id fim: $BOOT $(date -u -d "@$((agora - 200))" -Is)" > "$ANTD/07-boot-fim.txt"
  echo "uname -r: 6.8.0-147-generic" > "$ANTD/00-pre.log"
  local k=1 n; for n in isn1 isn2 isn3; do mkdir -p "$ST/$n"; echo running > "$ST/$n/status"; echo $((4000 + k)) > "$ST/$n/pid"
    echo UP > "$ST/eth0.$((4000 + k))"; echo "$n" > "$ST/pid.$((4000 + k))"; echo 0 > "$ST/$n/exit"
    echo $((agora - 100)) > "$ST/$n/created"; echo $((agora - 95)) > "$ST/$n/started"; echo "10.89.0.1$k" > "$ST/$n/ip"; k=$((k + 1)); done
  touch "$ST/events.isn2"
}
GOS=$'GO F1 n=1 i=2000 carga=sem\nGO F1 n=2 i=2001 carga=sem\nGO F1 n=3 i=2002 carga=S1-sync\nGO F1 n=4 i=2003 carga=S1-async\n'
EN_T() { echo "$T/home/tcc-gate/runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh"; }
ambiente() { AMB=(env -u TCC_DIR -u PODMAN -u JAVA "HOME=$T/home" "PATH=$BIN:$PATH" TMUX=/tmp/tmux-1000/default,1,0 "RB_PASSWORD=$PW" \
  "SHIM_LOG=$T/shim.log" "SHIM_STATE=$ST" ENSAIO_TESTE=1 "ENSAIO_HASHES=$T/hashes" "ENSAIO_LISTA_SHA=$LISTA" \
  "ENSAIO_BOOT_ID_FILE=$T/boot_id" ENSAIO_ESPERA_GO_S=5 ENSAIO_INJ_ESPERA_TRAP_S=30); }
XARGS=()
roda() { # roda <stdin> [VAR=valor...]; usa XARGS (argumentos extras) e BOOTA (boot do argumento)
  local entrada="$1"; shift
  SAIDA="$(printf '%s' "$entrada" | "${AMB[@]}" "$@" bash "$(EN_T)" --rb-dir "$RBF" --boot-esperado "${BOOTA:-$BOOT}" \
    --bloco-anterior "$ANTD" "${XARGS[@]}" 2>&1)"; RC=$?
  OUT="$(ls -d "$T/home/tcc-gate/runs/ensaio-f1-silencioso-2026-11-04"/vm-* 2>/dev/null | head -1)"
}
nada_injetado() { # nada_injetado <rotulo>
  ok "$1: nenhum ip link set" 0 "$(grep -c 'link set' "$T/shim.log")"
  ok "$1: nenhum podman kill/start" 0 "$(grep -cE '^podman\[[a-z]*\] (kill|start)' "$T/shim.log")"
  ok "$1: nenhum workload" 0 "$(grep -c java-workload "$ST/java.log" 2>/dev/null || echo 0)"; }
L() { cat "$T/shim.log"; }

echo "== T-0: arquivo, hashes da adenda, rc na mesma linha, copia verbatim do s5.sh, extracao do --rb-dir =="
bash -n "$EN"; ok "T-0 bash -n" 0 $?
ok "T-0 nenhum CR" 0 "$(tr -cd '\r' < "$EN" | wc -c | tr -d ' ')"
H="$(bash "$EN" --hashes)"
X=$(cd "$T" && for f in analysis/rebaseline.py runs/rebaseline/matriz.tsv scripts/inject-crash.sh scripts/inject-jitter.sh scripts/rebaseline_matriz.py scripts/run-rebaseline.sh; do
  echo "$(git -C "$REPO" show "20805b0:$f" | sha256sum | cut -d' ' -f1) *$f"; done
  [ -f "$JARREAL" ] && echo "$(sha256sum "$JARREAL" | cut -d' ' -f1) *workload/target/session-workload-0.1.0-SNAPSHOT.jar")
ok "T-0 tabela embutida = blobs de 20805b0 + jar congelado (7 linhas)" "$X" "$(printf '%s\n' "$H" | head -7)"
ok "T-0 sha256 da lista embutida = 003814e9...33a4 (adenda)" 003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4 \
  "$(printf '%s\n' "$H" | head -7 | sha256sum | cut -d' ' -f1)"
contem "T-0 lista declarada" "lista (sha256 das 7 linhas acima): 003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4" "$H"
contem "T-0 XML de 20805b0" "$(git -C "$REPO" show 20805b0:cluster/infinispan-cluster.xml | sha256sum | cut -d' ' -f1)" "$H"
ok "T-0 todo '| tee' le o PIPESTATUS na mesma linha (R-3)" 0 "$(grep -E '\| *tee ' "$EN" | grep -vc 'PIPESTATUS')"
fn() { awk -v f="$2" '$0 ~ "^" f "\\(\\) \\{" { p = 1 } p { print } p && (/^\}/ || /; \}$/) { exit }' "$1"; }
for f in stats tcp_s saude_loop; do
  ok "T-0 $f verbatim do s5.sh" "" "$(diff <(fn "$REPO/runs/vm-s5-2026-10-28/s5.sh" $f) <(fn "$EN" $f))"
  ok "T-0 $f nao vazio" 1 "$([ -n "$(fn "$EN" $f)" ] && echo 1 || echo 0)"; done
contem "T-0 regra do intervalo do saude-log = a do s5.sh" \
  "$(grep -F "q=\$(awk -F, 'NR > 1 { if (\$2 in u)" "$REPO/runs/vm-s5-2026-10-28/s5.sh" | sed 's/^ *//')" "$(cat "$EN")"
contem "T-0 ANCORA_RE = a do run-rebaseline.sh" "$(git -C "$REPO" show 20805b0:scripts/run-rebaseline.sh | sed -n "s/^readonly ANCORA_RE=//p")" "$(grep '^ANCORA_RE=' "$EN")"
ok "T-0 nenhuma constante do PR redigitada no ensaio-f1.sh" 0 "$(grep -cE '^[[:space:]]*(readonly )?(DURACAO|WARMUP_MIN|THREADS|OPS|REP|MIX|RESET_STATE|S0|PASSO_SEMENTE|INJ_OFFSET|F1_DUR|K1_[A-Z_]*|F1_VOLTA_[A-Z_]*|CLASSE_VERIFICADOR)=' "$EN")"
ok "T-0 nenhum 'montar_cmd() {' proprio" 0 "$(grep -c '^ *montar_cmd() {' "$EN")"
ok "T-0 nenhum tc de escrita no script" 0 "$(grep -E '\btc (qdisc|filter) (add|del|change|replace)' "$EN" | grep -vc '^ *#')"
ok "T-0 nenhum podman kill/start/rm/stop como comando no script" 0 "$(grep -cE '^[^#"]*(sudo podman|PODMAN[}]?) (kill|start|rm|stop)' "$EN")"
ok "T-0 nenhum 'link set' no script fora de comentario e do RE_ISO" 0 "$(grep 'link set' "$EN" | grep -vE '^ *#|^RE_ISO=' | wc -l | tr -d ' ')"

echo "== T-1: argumentos (exit 2) e --dry-run =="
nova_arvore
X=$("${AMB[@]}" bash "$(EN_T)" --rb-dir "$RBF" --bloco-anterior "$ANTD" 2>&1); ok "T-1 sem --boot-esperado: rc 2" 2 $?
X=$("${AMB[@]}" bash "$(EN_T)" --rb-dir "$RBF" --boot-esperado 5D7119B8-xx --bloco-anterior "$ANTD" 2>&1); ok "T-1 UUID malformado: rc 2" 2 $?
X=$("${AMB[@]}" bash "$(EN_T)" --rb-dir "$RBF" --boot-esperado "$BOOT" 2>&1); ok "T-1 sem --bloco-anterior: rc 2" 2 $?
X=$("${AMB[@]}" bash "$(EN_T)" --rb-dir "$RBF" --boot-esperado "$BOOT" --bloco-anterior "$ANTD" --so 5 2>&1); ok "T-1 --so 5: rc 2" 2 $?
X=$("${AMB[@]}" bash "$(EN_T)" --xyz 2>&1); ok "T-1 argumento desconhecido: rc 2" 2 $?
ok "T-1 argumentos invalidos nao criam vm-UTC" 0 "$(ls -d "$T/home/tcc-gate/runs/ensaio-f1-silencioso-2026-11-04"/vm-* 2>/dev/null | wc -l | tr -d ' ')"
X=$("${AMB[@]}" bash "$(EN_T)" --dry-run --rb-dir "$RBF" 2>&1); ok "T-1 --dry-run: rc 0" 0 $?
contem "T-1 dry-run: constantes lidas do --rb-dir" "duracao=8 warmup_min=1 threads=4" "$X"
contem "T-1 dry-run: injetor --dry-run real" "dry-run: nada executado" "$X"
contem "T-1 dry-run: linha de comando com a senha mascarada" "--password ***" "$X"
contem "T-1 dry-run: semente 250200 (S0 + 100 i)" "--seed 250200" "$X"
contem "T-1 dry-run: replicacao async na 4" "--replication async" "$X"
ok "T-1 dry-run: nada tocado" 0 "$(grep -cE 'link set|^podman\[[a-z]*\] (kill|start)' "$T/shim.log")"
sed -i 's/^readonly DURACAO=8 WARMUP_MIN=1 THREADS=4 /readonly DURACAO=8 WARMUP_MIN=1 THREADS=8 /' "$RBF/scripts/run-rebaseline.sh"
X=$("${AMB[@]}" bash "$(EN_T)" --dry-run --rb-dir "$RBF" 2>&1); contem "T-1 THREADS=8 no --rb-dir muda a linha de comando" "--threads 8" "$X"
sed -i '/^readonly INJ_OFFSET=/d' "$RBF/scripts/run-rebaseline.sh"
X=$("${AMB[@]}" bash "$(EN_T)" --dry-run --rb-dir "$RBF" 2>&1); ok "T-1 run-rebaseline.sh sem INJ_OFFSET: rc 1" 1 $?
contem "T-1 motivo: linha ausente (nao redigita)" "readonly INJ_OFFSET=' aparece 0 vez" "$X"

echo "== T-2: caminho feliz das 4 execucoes =="
nova_arvore; XARGS=(); TS=$(date +%s); roda "$GOS" SHIM_IP_NOVO=10.89.0.99; DUR2=$(( $(date +%s) - TS ))
ok "T-2 rc 0" 0 "$RC"
[ "$RC" = 0 ] || printf '%s\n' "$SAIDA" | tail -40
for f in 00-pre.log parametros.txt 01-injetor-dry-run.log 02-portao.log 07-estado-final.log 07-portao-final.log 07-boot-fim.txt 08-avaliacao.txt MANIFEST.sha256 rc.txt ensaio-console.log; do
  existe "T-2 gravado $f" "$OUT/$f"; done
for e in exec-1-i2000 exec-2-i2001 exec-3-i2002 exec-4-i2003; do
  for f in go.txt execucao.txt portao.log started.txt saude.csv events-isn2.json inspect-isn2-antes.json injetor.log volta.log inspect-isn2-depois.json ip-br-link-isn2.txt logs-isn1.txt logs-isn2.txt logs-isn3.txt; do
    existe "T-2 $e/$f" "$OUT/$e/$f"; done; done
for e in exec-3-i2002 exec-4-i2003; do
  for f in stdout.log rep-001.csv.gz rep-001.csv.sha256 violacoes-rep-001.csv sanidade.json verificador.out csv-resumo.txt c1-c3.json; do existe "T-2 $e/$f" "$OUT/$e/$f"; done
  ok "T-2 $e: rep-001.csv sem comprimir nao ficou" 0 "$([ -e "$OUT/$e/rep-001.csv" ] && echo 1 || echo 0)"
  ok "T-2 $e: sha256 gravado antes do gzip confere" "$(cut -d' ' -f1 "$OUT/$e/rep-001.csv.sha256")" "$(gzip -dc "$OUT/$e/rep-001.csv.gz" | sha256sum | cut -d' ' -f1)"; done
PRE="$(cat "$OUT/00-pre.log")"
contem "T-2 hashes do --rb-dir conferidos" "hashes do --rb-dir: iguais a adenda" "$PRE"
contem "T-2 lista conferida" "lista: $LISTA (= adenda)" "$PRE"
contem "T-2 sha256 do proprio script" "ensaio-f1.sh: $(sha256sum "$EN" | cut -d' ' -f1)" "$PRE"
contem "T-2 lock ocupado" "OCUPADO (este flock)" "$PRE"
contem "T-2 boot_id" "boot_id: $BOOT" "$PRE"
contem "T-2 XML em uso conferido" "isn2 XML em uso: $ST/infinispan.xml 6d52413b" "$PRE"
contem "T-2 cache ASYNC conferido" "size counters-async porta 11224: http=200 0" "$PRE"
contem "T-2 eth0 de isn2 UP" "eth0@if9         UP" "$PRE"
contem "T-2 RB_PASSWORD presente" "RB_PASSWORD: presente (nao gravado)" "$PRE"
ok "T-2 rc.txt na ordem" "pre injetor-dry-run portao execucao-1 execucao-2 execucao-3 execucao-4 estado-final avaliacao" \
  "$(grep -oE '^[a-z0-9-]+ rc=' "$OUT/rc.txt" | sed 's/ rc=//' | paste -sd' ')"
AV="$(cat "$OUT/08-avaliacao.txt")"
contem "T-2 ENSAIO: 4 de 4 VALIDAS" "ENSAIO: 4 de 4 VALIDAS" "$AV"
nao "T-2 sem FALHA" "FALHA" "$AV"
for k in 1 2 4 5 6 7 8 9; do ok "T-2 F-$k OK nas 4" 4 "$(grep -c "^OK: \[n=[1-4]\] F-$k " "$OUT/08-avaliacao.txt")"; done
ok "T-2 F-3 OK so nas com carga" 2 "$(grep -c '^OK: \[n=[34]\] F-3 ' "$OUT/08-avaliacao.txt")"
contem "T-2 F-9 geral" "OK: F-9 boot_id igual no inicio e no fim do ensaio" "$AV"
contem "T-2 F-10 geral" "OK: F-10 estado final" "$AV"
contem "T-2 Δdet ~38 s e RESULTADO" "RESULTADO: [n=1] Δdet isn1 = 38." "$AV"
contem "T-2 Δdet dentro de [32, 49]" "dentro de [32, 49] s" "$AV"
contem "T-2 K3 como RESULTADO" "RESULTADO: [n=3] VerificadorSanidade: exit=0 veredito=OK; K3 do PR ACEITARIA" "$AV"
contem "T-2 contagem de ERROR_*" "RESULTADO: [n=4] csv: erro ERROR_TRANSPORT=" "$AV"
contem "T-2 latencia maxima na janela da falha" "latencia_max_s=0.9" "$AV"
contem "T-2 C1..C3 como RESULTADO" '"C3": {"ok": true' "$AV"
contem "T-2 leitura humana da ordem" "LEITURA HUMANA: [n=2] ordem nos carimbos: isolamento=" "$AV"
contem "T-2 leitura humana do podman start" "duracao do podman start" "$AV"
contem "T-2 IP de isn2 registrado (o start da 1 troca o IP)" "RESULTADO: [n=1] IP de isn2: antes=10.89.0.12 depois=10.89.0.99 (diferente)" "$AV"
contem "T-2 IP de isn2 antes da 2 = o atual, nao o do passo 0" "RESULTADO: [n=2] IP de isn2: antes=10.89.0.99 depois=10.89.0.99 (igual)" "$AV"
ok "T-2 4 GOs no go.log do ensaio" 4 "$(grep -c 'bloco=ensaio-f1 F1 n=' "$T/home/tcc-gate/runs/ensaio-f1-silencioso-2026-11-04/go.log")"
ok "T-2 4 portoes K1 de execucao, todos OK" 4 "$(cat "$OUT"/exec-*/portao.log | grep -c '^PORTAO K1: OK')"
ok "T-2 entre o GO e o isolamento de cada execucao ha um portao K1 OK" "1 2 3 4" "$(awk '/^== execucao [1-4]:/ { n = $3; sub(":", "", n); k = 0 } /^PORTAO K1: OK/ && n { k = 1 } /isolando isn2/ && n { if (k) printf "%s%s", (o++ ? " " : ""), n; n = 0 }' "$OUT/ensaio-console.log")"
ok "T-2 4 kills, todos do injetor" "4 4" "$(grep -c '^podman\[[a-z]*\] kill' "$T/shim.log") $(grep -c '^podman\[injetor\] kill isn2 --signal KILL' "$T/shim.log")"
ok "T-2 4 starts, todos do injetor" "4 4" "$(grep -c '^podman\[[a-z]*\] start' "$T/shim.log") $(grep -c '^podman\[injetor\] start isn2' "$T/shim.log")"
ok "T-2 ip link set so do injetor" 0 "$(grep 'link set' "$T/shim.log" | grep -vc '^ip\[injetor\]')"
ok "T-2 tc so leitura (qdisc show)" 0 "$(grep '^tc\[' "$T/shim.log" | grep -vc 'qdisc show dev eth0$')"
ok "T-2 senha em nenhum arquivo do vm-UTC" 0 "$(grep -rlF "$PW" "$OUT" | wc -l | tr -d ' ')"
ok "T-2 senha fora do go.log do ensaio" 0 "$(grep -cF "$PW" "$T/home/tcc-gate/runs/ensaio-f1-silencioso-2026-11-04/go.log")"
contem "T-2 workload recebeu a senha (pela linha de comando, nao por arquivo)" "--password $PW" "$(cat "$ST/java.log")"
contem "T-2 workload com --threads 4 do --rb-dir" "--threads 4 --ops 0 --rep 1 --seed 250200" "$(cat "$ST/java.log")"
contem "T-2 workload async na 4 com semente 250300" "--replication async --reset-state on --duration 8" "$(cat "$ST/java.log")"
contem "T-2 verificador com a classe do --rb-dir" "br.unipampa.tcc.session.VerificadorSanidade --csv $OUT/exec-3-i2002/rep-001.csv --scenario S1" "$(cat "$ST/java.log")"
contem "T-2 linha de comando mascarada em execucao.txt" "--password ***" "$(cat "$OUT/exec-3-i2002/execucao.txt")"
ok "T-2 MANIFEST confere" 0 "$(cd "$OUT" && sha256sum -c --quiet MANIFEST.sha256 >/dev/null 2>&1; echo $?)"
nao "T-2 MANIFEST nao inclui a si mesmo" "MANIFEST.sha256" "$(cut -d' ' -f3 "$OUT/MANIFEST.sha256")"
X=$(bash "$EN" --avaliar "$OUT"); ok "T-2 --avaliar: rc 0" 0 $?
ok "T-2 --avaliar refaz a avaliacao igual" "$(tail -n +2 "$OUT/08-avaliacao.txt")" "$(printf '%s\n' "$X" | tail -n +2)"
cp -r "$OUT" "$T/bom"

echo "== T-3: pre-condicoes que falham abortam antes de qualquer injecao =="
precond() { # precond <rotulo> <trecho> VAR=valor...
  local rot="$1" tr="$2"; shift 2; nova_arvore; prep_extra; roda "$GOS" "$@"
  ok "T-3 $rot: rc 1" 1 "$RC"; contem "T-3 $rot: motivo" "$tr" "$(cat "$OUT/00-pre.log" 2>/dev/null)"
  contem "T-3 $rot: abortado" "ENSAIO ABORTADO (rc=1): pre-condicoes" "$SAIDA"; nada_injetado "T-3 $rot"; prep_extra() { :; }; }
prep_extra() { :; }
precond "fora do tmux" "fora do tmux" TMUX=
precond "lock livre" "LIVRE" SHIM_LOCK=livre
precond "inject-crash.sh em execucao" "inject-crash.sh ja em execucao" SHIM_PGREP=1
precond "sudo -n falha" "sudo -n true falhou" SHIM_SUDO_FALHA=1
precond "kernel diferente do bloco anterior" "kernel 6.8.0-150-generic diferente do bloco anterior" SHIM_KERNEL=6.8.0-150-generic
prep_extra() { echo 11111111-2222-3333-4444-555555555555 > "$T/boot_id"; }
precond "boot_id atual diferente" "reboot da VM entre blocos ou referencia errada"
prep_extra() { sed -i "s/$BOOT/11111111-2222-3333-4444-555555555555/" "$ANTD/07-boot-fim.txt"; }
precond "esperado diferente do bloco anterior" "bloco anterior=11111111-2222-3333-4444-555555555555"
prep_extra() { rm "$ANTD/07-boot-fim.txt"; }
precond "bloco anterior sem 07-boot-fim.txt" "bloco anterior sem brutos"
prep_extra() { echo "# alterado" >> "$RBF/scripts/inject-crash.sh"; }
precond "hash do inject-crash.sh do --rb-dir diferente" "hash do --rb-dir diferente da adenda"
precond "lista diferente da adenda" "lista dos 7 arquivos do --rb-dir" ENSAIO_LISTA_SHA=0000
precond "XML em uso diferente" "XML em uso por isn1" ENSAIO_XML_SHA=8792dea3
precond "cache ASYNC ausente" "cache sessions-async inexistente ou ilegivel na porta 11222 (http 404)" SHIM_SEM_ASYNC=1
precond "cache nao vazio" "cache counters nao vazio na porta 11222 (7)" SHIM_CACHE_CHEIO=counters
precond "RB_PASSWORD ausente" "RB_PASSWORD ausente" RB_PASSWORD=
precond "PODMAN herdado" "variaveis herdadas que mudariam o injetor/workload: PODMAN" PODMAN=podman
precond "RB_MODO_TESTE herdado" "variaveis herdadas que mudariam o injetor/workload: RB_MODO_TESTE" RB_MODO_TESTE=1
precond "timer tcc-heal armado" "1 timers tcc-heal armados" SHIM_TIMERS=1
prep_extra() { echo $(( $(cat "$ST/isn3/created") + 900 )) > "$ST/isn3/started"; }
precond "conteiner reiniciado depois da recriacao" "isn3 StartedAt-Created=900 s"
prep_extra() { echo $(( $(date +%s) - 400 )) > "$ST/isn1/created"; }
precond "cluster criado antes do fim do S7" "isn1 criado em"
prep_extra() { echo exited > "$ST/isn2/status"; }
precond "isn2 parado" "isn2 nao esta running"
precond "isn2 no netns do host" "netns de isn2 igual ao do host" SHIM_NETNS_HOST=1
prep_extra() { echo DOWN > "$ST/eth0.4002"; }
precond "eth0 de isn2 DOWN" "eth0 de isn2 nao esta UP"
prep_extra() { sed -i 's/^montar_cmd() {/montar_cmd_x() {/' "$RBF/scripts/run-rebaseline.sh"; tabela_rb; }
precond "run-rebaseline.sh sem montar_cmd" "'montar_cmd() {' aparece 0 vez(es)" ENSAIO_LISTA_SHA=
prep_extra() { :; }
nova_arvore; roda "$GOS" TCC_DIR="$T/home/outro"; ok "T-3 TCC_DIR diferente: rc 1" 1 "$RC"; nada_injetado "T-3 TCC_DIR"

echo "== T-4: GO ausente ou com rotulo errado: nada injetado (rc 3) =="
for g in "" $'GO\n' $'GO F1 n=2 i=2001 carga=sem\n' $'go F1 n=1 i=2000 carga=sem\n'; do
  nova_arvore; roda "$g"; ok "T-4 GO [${g%$'\n'}]: rc 3" 3 "$RC"; contem "T-4 GO [${g%$'\n'}]: motivo" "GO ausente ou diferente de 'GO F1 n=1 i=2000 carga=sem'" "$SAIDA"
  nada_injetado "T-4 GO [${g%$'\n'}]"; done
nova_arvore; roda $'GO F1 n=1 i=2000 carga=sem\nGO F1 n=1 i=2000 carga=sem\n'
ok "T-4 GO certo na 1 e errado na 2: rc 3" 3 "$RC"; ok "T-4 so 1 kill (a 1)" 1 "$(grep -c '^podman\[injetor\] kill' "$T/shim.log")"
ok "T-4 a 2 nao injetou" 0 "$([ -e "$OUT/exec-2-i2001/injetor.log" ] && echo 1 || echo 0)"

echo "== T-5: paradas automaticas (exit 4), sem conserto =="
XARGS=(--so 1)
nova_arvore; roda "$GOS" SHIM_DDET=29
ok "T-5 Δdet 29 s: rc 4" 4 "$RC"; contem "T-5 Δdet 29 s: motivo" "PARADA: Δdet de isn1 = 29." "$SAIDA"; existe "T-5 parada.txt" "$OUT/parada.txt"
contem "T-5 Δdet 29 s: F-4 FALHA na avaliacao" "FALHA: [n=1] F-4 Δdet isn1=29." "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_DDET=30; ok "T-5 Δdet 30 s (no limite): rc 0" 0 "$RC"
nova_arvore; roda "$GOS" SHIM_DDET_isn3=29; ok "T-5 Δdet 29 s so em isn3: rc 4" 4 "$RC"; contem "T-5 isn3: motivo" "Δdet de isn3 = 29." "$SAIDA"
nova_arvore; roda "$GOS" SHIM_VOLTA_S=100
ok "T-5 volta acima do prazo: rc 4" 4 "$RC"; contem "T-5 volta: motivo" "parada (e): cluster nao voltou" "$SAIDA"
contem "T-5 volta: F-7 FALHA" "FALHA: [n=1] F-7" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_EXITCODE=143
ok "T-5 injetor exit 1 (ExitCode 143): rc 4" 4 "$RC"; contem "T-5 exit 1: motivo" "injetor da execucao 1 saiu com 1" "$SAIDA"
contem "T-5 exit 1: F-2 FALHA" "FALHA: [n=1] F-2" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_START_FALHA=1
ok "T-5 injetor exit 5 (start falhou): rc 4" 4 "$RC"; contem "T-5 exit 5: motivo" "saiu com 5" "$SAIDA"
contem "T-5 exit 5: instrucao para o Gestor, sem conserto" "decisao do Gestor, depois de o Senior ler os brutos: sudo podman start isn2" "$(cat "$OUT/parada.txt")"
ok "T-5 exit 5: o ensaio nao tentou podman start" 0 "$(grep -c '^podman\[ensaio\] start' "$T/shim.log")"
ok "T-5 exit 5: isn2 continua parado" exited "$(cat "$ST/isn2/status")"
nova_arvore; printf '#!/usr/bin/env bash\ncase "$*" in *--dry-run*) echo "modo=silencioso"; echo "dry-run: nada executado"; exit 0;; esac\nexit 2\n' > "$RBF/scripts/inject-crash.sh"; tabela_rb
roda "$GOS"; ok "T-5 injetor exit 2: rc 4" 4 "$RC"; contem "T-5 exit 2: motivo" "saiu com 2 (C2: 1/4 = recusa K6, 2/3/5 = infraestrutura)" "$SAIDA"
nova_arvore; XARGS=(); roda "$GOS" SHIM_DDET=29
ok "T-5 parada na 1 encerra o ensaio inteiro (E-7)" 0 "$(ls -d "$OUT"/exec-2-* 2>/dev/null | wc -l | tr -d ' ')"
contem "T-5 parada indica a retomada com --so" "--so <n> com GO novo" "$(cat "$OUT/parada.txt")"

echo "== T-6: --so 3 e THREADS=8 no --rb-dir =="
nova_arvore; sed -i 's/^readonly DURACAO=8 WARMUP_MIN=1 THREADS=4 /readonly DURACAO=8 WARMUP_MIN=1 THREADS=8 /' "$RBF/scripts/run-rebaseline.sh"; tabela_rb
XARGS=(--so 3); TS=$(date +%s); roda $'GO F1 n=3 i=2002 carga=S1-sync\n'; DUR6=$(( $(date +%s) - TS ))
ok "T-6 --so 3: rc 0" 0 "$RC"; ok "T-6 so a execucao 3" "exec-3-i2002" "$(cd "$OUT" && ls -d exec-* | paste -sd' ')"
contem "T-6 ENSAIO: 1 de 1" "ENSAIO: 1 de 1 VALIDAS" "$(cat "$OUT/08-avaliacao.txt")"
contem "T-6 THREADS=8 do --rb-dir chegou ao workload" "--threads 8" "$(cat "$ST/java.log")"
contem "T-6 disparo a +2 s da ancora (INJ_OFFSET do --rb-dir)" "OK: [n=3] F-3 disparo a +2." "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; XARGS=(--so 2); roda $'GO F1 n=2 i=2001 carga=sem\n'; ok "T-6 --so 2: rc 0" 0 "$RC"
nova_arvore; echo $(( $(cat "$ST/isn2/created") + 900 )) > "$ST/isn2/started"; XARGS=(--so 2); roda $'GO F1 n=2 i=2001 carga=sem\n'
ok "T-6 --so 2 com isn2 reiniciado por F1 anterior: aceito e registrado" 0 "$RC"; contem "T-6 registro" "isn2 StartedAt-Created=900 s (--so 2" "$(cat "$OUT/00-pre.log")"

echo "== T-7: F-n violados isoladamente na execucao real (rc 6) =="
XARGS=(--so 1)
nova_arvore; roda "$GOS" SHIM_DIED_EXTRA=143; ok "T-7 died extra com 143: rc 6" 6 "$RC"; contem "T-7 F-2" "FALHA: [n=1] F-2 died de isn2: 2 evento(s), 1 com 137" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_F5=1; ok "T-7 ISPN000390 na vida antiga: rc 6" 6 "$RC"; contem "T-7 F-5" "FALHA: [n=1] F-5 1 linha(s)" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_693=1; ok "T-7 ISPN000693 na vida nova: rc 6" 6 "$RC"; contem "T-7 F-6" "FALHA: [n=1] F-6 vida nova de isn2: ISPN000389=1 ISPN080001=1 ISPN000693=1" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_ETH0_NOVO=DOWN; ok "T-7 eth0 DOWN no netns novo: rc 6" 6 "$RC"; contem "T-7 F-8" "FALHA: [n=1] F-8" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_SEM_VISTA=1; ok "T-7 sem vista sem isn2: rc 6 (F-4, nao parada)" 6 "$RC"; contem "T-7 F-4 sem leitura" "FALHA: [n=1] F-4 Δdet isn1=? s" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda "$GOS" SHIM_IP_NOVO=10.89.0.99; ok "T-7 IP novo de isn2 so registrado: rc 0" 0 "$RC"; contem "T-7 IP diferente e RESULTADO" "depois=10.89.0.99 (diferente)" "$(cat "$OUT/08-avaliacao.txt")"
XARGS=(--so 3)
nova_arvore; roda $'GO F1 n=3 i=2002 carga=S1-sync\n' SHIM_VEREDITO=FALHA SHIM_VEXIT=4
ok "T-7 verificador FALHA e RESULTADO (E-5), nao invalida: rc 0" 0 "$RC"; contem "T-7 K3 recusaria" "K3 do PR RECUSARIA" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda $'GO F1 n=3 i=2002 carga=S1-sync\n' SHIM_SEM_ANCORA=1
ok "T-7 workload sem ancora: rc 4, nada injetado" 4 "$RC"; ok "T-7 sem ancora: nenhum kill" 0 "$(grep -c '^podman\[injetor\] kill' "$T/shim.log")"

echo "== T-8: --avaliar sobre copias mutadas da execucao boa =="
B="$T/bom"
av() { rm -rf "$T/m"; cp -r "$B" "$T/m"; M="$T/m"; ( cd "$M" && eval "$3" ); X=$(bash "$EN" --avaliar "$M"); local r=$?
  ok "T-8 $1: rc 6" 6 "$r"; contem "T-8 $1: motivo" "$2" "$X"; contem "T-8 $1: 3 de 4" "${4:-ENSAIO: 3 de 4 VALIDAS}" "$X"; }
av "F-1 sem 'eth0 down'" "FALHA: [n=1] F-1" "sed -i '/eth0 down/d' exec-1-i2000/injetor.log"
av "F-1 injetor exit 1" "FALHA: [n=2] F-1 injetor exit=1" "sed -i 's/^injetor_exit=0/injetor_exit=1/' exec-2-i2001/execucao.txt"
av "F-2 died com 143" "FALHA: [n=1] F-2" "sed -i 's/\"ContainerExitCode\":137/\"ContainerExitCode\":143/' exec-1-i2000/events-isn2.json"
av "F-2 died sem codigo (omitempty = 0)" "FALHA: [n=1] F-2" "sed -i 's/,\"ContainerExitCode\":137//' exec-1-i2000/events-isn2.json"
av "F-2 sem died" "FALHA: [n=1] F-2 died de isn2: 0 evento(s)" ": > exec-1-i2000/events-isn2.json"
av "F-3 disparo 1,5 s cedo" "FALHA: [n=3] F-3" "a=\$(sed -n 's/^anc_ms=//p' exec-3-i2002/execucao.txt); echo disp_ms=\$((a + 500)) >> exec-3-i2002/execucao.txt"
av "F-3 disparo 2,5 s tarde" "FALHA: [n=4] F-3" "a=\$(sed -n 's/^anc_ms=//p' exec-4-i2003/execucao.txt); echo disp_ms=\$((a + 4500)) >> exec-4-i2003/execucao.txt"
av "F-4 sem vista sem isn2 em isn3" "FALHA: [n=2] F-4" "sed -i '/(2) \\[isn1-1001, isn3-1003\\]/d' exec-2-i2001/logs-isn3.txt"
av "F-5 ISPN000390 na vida antiga" "FALHA: [n=1] F-5" "sed -i '1s/ISPN000078/ISPN000390/' exec-1-i2000/logs-isn2.txt"
av "F-6 sem ISPN000389" "FALHA: [n=1] F-6" "sed -i '/ISPN000389/d' exec-1-i2000/logs-isn2.txt"
av "F-7 volta em 181 s" "FALHA: [n=1] F-7" "echo volta_s=181.000 >> exec-1-i2000/execucao.txt"
av "F-7 volta_ok=0" "FALHA: [n=4] F-7" "echo volta_ok=0 >> exec-4-i2003/execucao.txt"
av "F-8 eth0 DOWN" "FALHA: [n=3] F-8" "sed -i 's/^eth0@if9         UP/eth0@if9         DOWN/' exec-3-i2002/ip-br-link-isn2.txt"
av "F-9 intervalo do saude-log > 2x" "FALHA: [n=2] F-9 intervalo maximo do saude-log" \
   "awk -F, 'BEGIN{OFS=\",\"} NR>1 && \$2==\"11223\" && ++k==2 {\$1=\$1+5000000000} {print}' exec-2-i2001/saude.csv > x && mv x exec-2-i2001/saude.csv"
av "F-9 boot_id do fim diferente" "FALHA: F-9 boot_id" "sed -i 's/^boot_id fim: [^ ]*/boot_id fim: 11111111-2222-3333-4444-555555555555/' 07-boot-fim.txt" "ENSAIO: 0 de 4 VALIDAS"
av "F-10 timer no fim" "FALHA: F-10 timers" "sed -i 's/^timers tcc-heal: 0$/timers tcc-heal: 1/' 07-estado-final.log" "ENSAIO: 0 de 4 VALIDAS"
av "F-10 portao final" "FALHA: F-10 portao K1 final" "sed -i 's/^PORTAO K1: OK/PORTAO K1: FALHA/' 07-estado-final.log" "ENSAIO: 0 de 4 VALIDAS"
rm -rf "$T/m"; cp -r "$B" "$T/m"; X=$(bash "$EN" --avaliar "$T/m"); ok "T-8 copia sem mutacao: rc 0" 0 $?

echo "== T-9: Ctrl-C real ao grupo durante o F1 com carga =="
nova_arvore; XARGS=(--so 3)
set -m
( printf '%s' $'GO F1 n=3 i=2002 carga=S1-sync\n' | "${AMB[@]}" bash "$(EN_T)" --rb-dir "$RBF" --boot-esperado "$BOOT" --bloco-anterior "$ANTD" --so 3 > "$T/ctrlc.out" 2>&1 ) &
PG=$!
for _ in $(seq 1 150); do [ -e "$ST/iso_ns" ] && break; sleep 0.2; done
sleep 0.5; kill -INT -- "-$PG" 2>/dev/null; wait "$PG"; RC=$?
set +m
OUT="$(ls -d "$T/home/tcc-gate/runs/ensaio-f1-silencioso-2026-11-04"/vm-* 2>/dev/null | head -1)"
ok "T-9 Ctrl-C: rc 130" 130 "$RC"
contem "T-9 rc.txt registra a interrupcao" "interrompido" "$(cat "$OUT/rc.txt")"
contem "T-9 TERM ao injetor; trap dele devolve isn2" "injetor saiu com 143 depois do TERM" "$(cat "$OUT/rc.txt")"
ok "T-9 isn2 de volta (pelo injetor, nao pelo ensaio)" "running 0" "$(cat "$ST/isn2/status") $(grep -c '^podman\[ensaio\] start' "$T/shim.log")"
sleep 3; n1=$(grep -c '^curl' "$T/shim.log"); sleep 3; n2=$(grep -c '^curl' "$T/shim.log")
ok "T-9 saude_loop parou" "$n1" "$n2"
vivos=0; for p in $(cat "$ST"/pids.* 2>/dev/null); do kill -0 "$p" 2>/dev/null && vivos=$((vivos + 1)); done
ok "T-9 nenhum filho vivo (workload, events)" 0 "$vivos"

echo "== duracao: total $(( $(date +%s) - T0 )) s (caminho feliz das 4: ${DUR2} s; --so 3: ${DUR6} s)"
echo "RESUMO: $P PASS, $F FAIL"
[ "$F" = 0 ]
