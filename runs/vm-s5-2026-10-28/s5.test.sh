#!/usr/bin/env bash
# runs/vm-s5-2026-10-28/s5.test.sh -- teste SEM VM do involucro s5.sh (S5: L-1 e L-2), com shims (sudo/podman/nsenter/
# flock/uname/systemctl/java/curl/ping/ip/tc/hostname) e stubs de l1.sh, l-lib.sh, inject-netdegrade.sh e
# portao51.sh numa arvore falsa $HOME/tcc-gate (git proprio).
# Prova: ordem pre -> linha de base -> portao pre -> TcpRetransSegs antes -> coletor + l1.sh (GO do Gestor) -> pos-L-1 ->
# portao pos -> boot_id fim -> avaliacao; o GO chega ao l1.sh so pelo stdin (o s5.sh nao o digita); o rc de cada passo e
# lido na mesma linha do pipeline; REST e Hot Rod de isn3 na porta 11224 (nao 11222, como diz o comentario de l1.sh:10);
# cada pre-condicao que falha aborta ANTES da linha de base (sem sonda, sem l1, sem portao); a tabela de hashes embutida e
# igual aos arquivos do repositorio e esses arquivos nao mudaram desde o v4c (902654b); cada coleta (base, >= 2 amostras de
# tc -s qdisc/filter, ping, saude, ssh22, sonda, TcpRetransSegs, vistas, copias do L1.log/marcadores/go.log); a avaliacao
# automatica aceita a execucao boa e recusa cada criterio violado (perdas 39..81, dropped ~20 %, latencia, ping, intervalo
# do saude-log > 2x o nominal, boot_id, marcadores, L-2, portao pos); mudanca de vista e RESULTADO, nao falha.
# Desde 2026-11-04 (queda de energia; cluster recriado): o boot_id esperado vem de --boot-esperado (Q-11, nenhum boot_id no
# codigo); a unica modificacao versionada aceita e runs/gate-b29/go.log com linhas 'go ' acrescentadas ao fim (Q-12); o passo
# 0 registra IDs/StartedAt/IPs/vista do cluster atual sem exigir a vista do S4.
# NAO prova: podman, tc, netem, systemd, Infinispan, a sonda Java nem os scripts reais (estes tem testes proprios).
# NAO toca na VM nem em rede.
# Rodar:  bash runs/vm-s5-2026-10-28/s5.test.sh      Exit 0 se tudo passar.
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$AQUI/../.." && pwd)"
S5="$AQUI/s5.sh"
P=0; F=0
ok()     { if [ "$2" = "$3" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (esperado '$2', obtido '$3')"; F=$((F+1)); fi; }
contem() { if printf '%s' "$3" | grep -qF -- "$2"; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (sem '$2')"; F=$((F+1)); fi; }
nao()    { if printf '%s' "$3" | grep -qF -- "$2"; then echo "FAIL: $1 (com '$2')"; F=$((F+1)); else echo "PASS: $1"; P=$((P+1)); fi; }
antes()  { # antes <desc> <arquivo> <a> <b>: primeira linha com a vem antes da primeira com b
  local la lb; la=$(grep -nF -m1 -- "$3" "$2" | cut -d: -f1); lb=$(grep -nF -m1 -- "$4" "$2" | cut -d: -f1)
  if [ -n "$la" ] && [ -n "$lb" ] && [ "$la" -lt "$lb" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 ('$3'@${la:-?} '$4'@${lb:-?})"; F=$((F+1)); fi; }
existe() { ok "$1" 1 "$([ -s "$2" ] && echo 1 || echo 0)"; }

[ -f "$S5" ] || { echo "FAIL: $S5 nao existe (vermelho esperado antes do feat)"; echo "RESUMO: 0 PASS, 1 FAIL"; exit 1; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
BIN="$T/bin"; mkdir -p "$BIN"
FIXTURE="$T/portao-ok.txt"
sed -n '/^amostras=/,/^fim /p' "$REPO/runs/vm-s3-2026-10-28/00-portao51-pre-console.txt" > "$FIXTURE"
BOOT=b088606d-5d07-4424-b308-0a58fedf0546   # boot da VM depois da queda de 2026-11-04 (so valor de teste: o s5.sh nao o contem)

# ---------------- shims
cat > "$BIN/sudo" <<'EOF'
#!/usr/bin/env bash
echo "sudo $*" >> "$SHIM_LOG"
case "$1" in
  -n) [ -z "${SHIM_SUDO_FALHA:-}" ]; exit $?;;
  podman) shift; exec podman "$@";;
  systemctl) shift; exec systemctl "$@";;
  readlink) case "$2" in /proc/1/ns/net) echo 'net:[4026531840]';;
              *) if [ -n "${SHIM_NETNS_HOST:-}" ]; then echo 'net:[4026531840]'; else echo 'net:[4026532999]'; fi;; esac; exit 0;;
  nsenter) shift 4  # -t <pid> -n
    J="$SHIM_STATE/janela"
    case "$*" in
      "tc -s qdisc show dev eth0")
        if [ -e "$J" ]; then
          echo "qdisc prio 1: root refcnt 2 bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
          echo " Sent 90000 bytes 900 pkt (dropped 0, overlimits 0 requeues 0)"
          echo "qdisc netem 30: parent 1:3 limit 1000 loss 20%"
          echo " Sent 8000 bytes 80 pkt (dropped ${SHIM_TC_DROP:-20}, overlimits 0 requeues 0)"
        else echo "qdisc noqueue 0: root refcnt 2"; echo " Sent 0 bytes 0 pkt (dropped 0, overlimits 0 requeues 0)"; fi;;
      "tc -s filter show dev eth0")
        if [ -e "$J" ]; then
          echo "filter parent 1: protocol ip pref 1 u32 chain 0"
          echo "filter parent 1: protocol ip pref 1 u32 chain 0 fh 800: ht divisor 1"
          echo "filter parent 1: protocol ip pref 1 u32 chain 0 fh 800::800 order 2048 key ht 800 bkt 0 flowid 1:3 not_in_hw  (rule hit 40 success 20)"
          echo "  match 0a59000b/ffffffff at 16 (success 20 )"
          echo "filter parent 1: protocol ip pref 1 u32 chain 0 fh 800::801 order 2049 key ht 800 bkt 0 flowid 1:3 not_in_hw  (rule hit 40 success 20)"
          echo "  match 0a59000c/ffffffff at 16 (success 20 )"
        fi;;
      "tc qdisc show dev eth0"|"tc qdisc show") echo "qdisc noqueue 0: root refcnt 2";;
      "cat /proc/net/snmp") c=$(cat "$SHIM_STATE/retrans" 2>/dev/null || echo 100); echo $((c+7)) > "$SHIM_STATE/retrans"
        echo "Tcp: RtoAlgorithm RtoMin RtoMax MaxConn ActiveOpens PassiveOpens AttemptFails EstabResets CurrEstab InSegs OutSegs RetransSegs InErrs OutRsts InCsumErrors"
        echo "Tcp: 1 200 120000 -1 10 20 0 0 4 5000 6000 $c 0 3 0";;
      "ss -tn") echo "State Recv-Q Send-Q Local Address:Port Peer Address:Port Process"
        if [ -n "${SHIM_IPV6:-}" ]; then echo "ESTAB 0 0 [::ffff:10.89.0.13]:7800 [::ffff:10.89.0.11]:41000"
        else echo "ESTAB 0 0 10.89.0.13:7800 10.89.0.11:41000"; echo "ESTAB 0 0 10.89.0.13:52000 10.89.0.12:7800"; fi;;
      *) echo "shim nsenter: inesperado: $*" >&2; exit 99;;
    esac; exit 0;;
esac
echo "shim sudo: inesperado: $*" >&2; exit 99
EOF
cat > "$BIN/podman" <<'EOF'
#!/usr/bin/env bash
echo "podman $*" >> "$SHIM_LOG"
n="${@: -1}"
case "$1" in
  ps) for m in isn1 isn2 isn3; do echo "$m abc Up 8 hours (healthy) criado=2026-10-28 07:06:1x"; done;;
  logs) if [ "$2" = --since ]; then
          [ -z "${SHIM_VISTA:-}" ] || echo "2026-10-28 09:00:40,000 INFO  [o.i.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn1-23890|5] (2) [isn1-23890, isn2-1]"
        else echo "2026-11-04 19:14:20,511 INFO  [o.i.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn1-54943|2] (3) [isn1-54943, isn2-7781, isn3-30412]"; fi;;
  inspect) case "$*" in
      *IPAddress*) case "$n" in isn1) echo "10.89.0.11 ";; isn2) echo "10.89.0.12 ";; isn3) echo "${SHIM_IP3-10.89.0.13} ";; esac;;
      *State.Pid*) echo "${SHIM_PID:-4003}";; *StartedAt*) echo "2026-10-28 07:06:19.82 +0000 UTC";;
      *.Id*) echo 5bf9476b5b97aaaa;; *) echo '[{"Name":"x"}]';; esac;;
esac
exit 0
EOF
cat > "$BIN/flock" <<'EOF'
#!/usr/bin/env bash
echo "flock $*" >> "$SHIM_LOG"
[ "${SHIM_LOCK:-ocupado}" = livre ]
EOF
cat > "$BIN/uname" <<'EOF'
#!/usr/bin/env bash
echo "${SHIM_KERNEL:-6.8.0-147-generic}"
EOF
cat > "$BIN/systemctl" <<'EOF'
#!/usr/bin/env bash
echo "systemctl $*" >> "$SHIM_LOG"
case "$1" in
  list-timers) [ -z "${SHIM_TIMERS:-}" ] || echo "Wed 2026-10-28 n/a tcc-heal-1-2.timer tcc-heal-1-2.service";;
  list-units)  [ -z "${SHIM_UNITS:-}" ] || echo "tcc-heal-1-2.service loaded active running";;
esac
exit 0
EOF
cat > "$BIN/hostname" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = -I ]; then echo "172.31.251.37 10.89.0.1 "; else echo server1; fi
EOF
cat > "$BIN/ip" <<'EOF'
#!/usr/bin/env bash
case "$*" in "route show default") echo "default via 172.31.240.1 dev eth0 proto dhcp src 172.31.251.37 metric 100";;
  *) echo "shim ip: inesperado: $*" >&2; exit 99;; esac
EOF
cat > "$BIN/tc" <<'EOF'
#!/usr/bin/env bash
echo "tc $*" >> "$SHIM_LOG"
if [ -n "${SHIM_NIC_PRIO:-}" ]; then echo "qdisc prio 1: root refcnt 2 bands 3"; else echo "qdisc mq 0: root"; fi
EOF
# curl: /health com corpo HEALTHY/3; durante a janela, SHIM_REST_LENTO aumenta o time_total de isn3
cat > "$BIN/curl" <<'EOF'
#!/usr/bin/env bash
url="${@: -1}"; o=; prev=
for a in "$@"; do [ "$prev" = -o ] && o="$a"; prev="$a"; done
echo "curl $url" >> "$SHIM_LOG"
case "$url" in *:22/) if [ -n "${SHIM_SSH_FALHA:-}" ]; then printf '0.000000'; exit 7; fi; printf '0.000250'; exit 1;; esac
t=0.003
[ -e "$SHIM_STATE/janela" ] && [ -n "${SHIM_REST_LENTO:-}" ] && case "$url" in *:11224/*) t=0.400;; esac
[ -n "$o" ] && echo '{"cluster_health":{"cluster_name":"cluster-tcc","health_status":"HEALTHY","number_of_nodes":3,"node_names":["a","b","c"]},"cache_health":[]}' > "$o"
printf '200 0.000300 %s' "$t"
EOF
# ping -n -c N -i 0.2 -W 2 IP; durante a janela, SHIM_PING_PERDA perde 1 pacote
cat > "$BIN/ping" <<'EOF'
#!/usr/bin/env bash
echo "ping $*" >> "$SHIM_LOG"
n=$(printf '%s\n' "$@" | awk 'p{print; exit} $0=="-c"{p=1}'); ip="${@: -1}"; r=$n
[ -e "$SHIM_STATE/janela" ] && [ -n "${SHIM_PING_PERDA:-}" ] && r=$((n-1))
echo "PING $ip ($ip) 56(84) bytes of data."
for i in $(seq 1 "$r"); do echo "64 bytes from $ip: icmp_seq=$i ttl=64 time=0.05${i: -1} ms"; done
echo; echo "--- $ip ping statistics ---"
echo "$n packets transmitted, $r received, $(( (n-r)*100/n ))% packet loss, time 1000ms"
echo "rtt min/avg/max/mdev = 0.050/0.054/0.059/0.003 ms"
EOF
# java: -version e a SondaHotrod (CSV; --duracao-s ou TERM -> remove-final)
cat > "$BIN/java" <<'EOF'
#!/usr/bin/env bash
case "$*" in *-version*) echo 'openjdk version "17.0.20.1" 2026-08-18' >&2; exit 0;; esac
echo "java $*" >> "$SHIM_LOG"
dur=$(printf '%s\n' "$@" | awk 'p{print; exit} $0=="--duracao-s"{p=1}')
echo "t_epoch_ns,no,cache,operacao,duracao_ns,resultado,excecao_classe,mensagem,causas"
fim() { if [ -z "${SHIM_SONDA_SEM_FINAL:-}" ]; then for c in sessions counters; do echo "$(date +%s%N),isn3,$c,remove-final,1500000,OK,,,"; done; fi; exit 0; }
trap fim TERM
t0=$(date +%s)
while :; do
  for c in sessions counters; do echo "$(date +%s%N),isn3,$c,put,2000000,OK,,,"; echo "$(date +%s%N),isn3,$c,get,1000000,OK,,,"; done
  [ -n "$dur" ] && [ $(( $(date +%s) - t0 )) -ge "$dur" ] && fim
  sleep 0.3 & wait $!
done
EOF
chmod +x "$BIN"/*

# ---------------- arvore falsa (stubs nos caminhos reais) e tabela de hashes dos stubs
nova_arvore() {
  rm -rf "$T/home" "$T/state"; mkdir -p "$T/state"; : > "$T/shim.log"; echo "$BOOT" > "$T/boot_id"
  G="$T/home/tcc-gate"; mkdir -p "$G/scripts" "$G/runs/gate-netdegrade" "$G/runs/gate-b29" "$G/runs/vm-s2-2026-10-27" "$G/runs/vm-s5-2026-10-28" "$G/workload/target"
  git -C "$REPO" show HEAD:runs/gate-b29/go.log > "$G/runs/gate-b29/go.log"   # o go.log versionado (5 linhas)
  cp "$S5" "$G/runs/vm-s5-2026-10-28/s5.sh"
  echo jar > "$G/workload/target/session-workload-0.1.0-SNAPSHOT.jar"
  JARH=$(sha256sum "$G/workload/target/session-workload-0.1.0-SNAPSHOT.jar" | cut -d' ' -f1)
  echo "# stub" > "$G/scripts/inject-netdegrade.sh"; echo "# stub" > "$G/runs/gate-netdegrade/l-lib.sh"
  printf 'go 2026-10-28T03:16:00+00:00 vm=x bloco=L-0\ngo 2026-10-28T04:59:09+00:00 vm=x bloco=L-0c\n' > "$G/runs/gate-netdegrade/go.log"
  cat > "$G/runs/vm-s2-2026-10-27/portao51.sh" <<'EOF'
echo "PORTAO dur=$1 csv=$2" >> "$SHIM_LOG"; : > "$2"
n=$(cat "$SHIM_STATE/portoes" 2>/dev/null || echo 0); n=$((n+1)); echo "$n" > "$SHIM_STATE/portoes"
if [ -n "${S5T_PORTAO_RUIM:-}" ] && { [ "$S5T_PORTAO_RUIM" = todos ] || [ "$S5T_PORTAO_RUIM" = "$n" ]; }; then
  sed 's/amostras_ruins=0/amostras_ruins=1/' "$S5T_FIXTURE"; else cat "$S5T_FIXTURE"; fi
EOF
  cat > "$G/runs/gate-netdegrade/l1.sh" <<'EOF'
echo "L1STUB TCC_DIR=${TCC_DIR:-} cwd=$PWD LOCK_DIR=${LOCK_DIR:-} PODMAN=${PODMAN:-}" >> "$SHIM_LOG"
read -rp "L-1 -- go do Gestor registrado no relatorio anterior? [digite GO] " r
echo "L1STUB resposta=$r" >> "$SHIM_LOG"
[ "$r" = GO ] || { echo "ABORTAR: sem go" >&2; exit 1; }
LOG=runs/gate-netdegrade; M=$LOG/marcadores.txt
echo "go $(date -Is) vm=172.31.251.37 bloco=L-1" >> "$LOG/go.log"
echo "fault=F4 node=isn3 event=start t_epoch_ns=$(date +%s%N)" >> "$M"; : > "$SHIM_STATE/janela"
[ -z "${S5T_TROCA_BOOT:-}" ] || echo 11111111-2222-3333-4444-555555555555 > "$S5T_BOOT_FILE"
sleep "${S5T_JANELA:-5}"
rm -f "$SHIM_STATE/janela"; echo "fault=F4 node=isn3 event=end t_epoch_ns=$(date +%s%N)" >> "$M"
{ echo "[2026-10-28T09:00:00Z] inject-netdegrade.sh: perda observada isn3 -> isn1: ${S5T_PERDA1:-20.3333}% (esperada 20.00%, n=300)"
  echo "[2026-10-28T09:00:00Z] inject-netdegrade.sh: perda observada isn3 -> isn2: ${S5T_PERDA2:-19}% (esperada 20.00%, n=300)"
  echo "[2026-10-28T09:00:00Z] inject-netdegrade.sh: contadores de isn3 (alvo):"
  echo "    qdisc prio 1: root refcnt 2 bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
  echo "     Sent 900000 bytes 9000 pkt (dropped 0, overlimits 0 requeues 0)"
  echo "    qdisc netem 30: parent 1:3 limit 1000 loss 20%"
  echo "     Sent 160000 bytes ${S5T_SENT:-1600} pkt (dropped ${S5T_DROP:-400}, overlimits 0 requeues 0)"
  echo "[2026-10-28T09:00:00Z] inject-netdegrade.sh: OK: F4 (degradacao parcial) injetada por 60s em isn3 (direcao out) e removida; sem qdisc residual"
} > "$LOG/L1.log"
cat "$LOG/L1.log"; echo "rc=${S5T_INJ_RC:-0} (esperado 0)" | tee -a "$LOG/L1.log"
[ "${S5T_INJ_RC:-0}" = 0 ] || { echo "ABORTAR: rc=$S5T_INJ_RC != 0; dead-man's tcc-heal-1-2 continua armado" >&2; exit 1; }
echo "ok sem prio em isn3"
exit "${S5T_L1_RC:-0}"
EOF
  ( cd "$G" && for f in scripts/inject-netdegrade.sh runs/gate-netdegrade/l-lib.sh runs/gate-netdegrade/l1.sh runs/vm-s2-2026-10-27/portao51.sh; do
      sha256sum "$f" | sed 's/ \*/  /'; done ) > "$T/hashes"
  ( cd "$G" && git init -q && git config core.autocrlf false && git add -A && git -c user.name=t -c user.email=t@t commit -qm stub )
  # como na VM em 2026-11-04 (Q-01/Q-12): o go.log versionado com a linha 'go ' do GO da REP=4 (S4) acrescentada
  echo "go G-1 (REP=4, janela de 90 s em isn3) 2026-10-28T07:12:13+00:00 vm=172.31.251.37 sha=1266c129904e73469926d95708e7c83290498c41" >> "$G/runs/gate-b29/go.log"
}
# roda o s5.sh na arvore falsa; $1 = stdin (o que o "Gestor" digita); demais = VAR=valor extras
roda() {
  local entrada="$1"; shift
  SAIDA="$(printf '%s' "$entrada" | env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX="/tmp/tmux-1000/default,1,0" \
    SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S5T_FIXTURE="$FIXTURE" S5T_BOOT_FILE="$T/boot_id" \
    S5_TESTE=1 S5_HASHES="$T/hashes" S5_JAR_ESPERADO="$JARH" S5_BOOT_ID_FILE="$T/boot_id" S5_PORTAO_MIN_S=1 S5_PORTAO_FOLGA_S=0 \
    S5_BASE_S=7 S5_SAUDE_S=2 S5_PING_BASE_N=6 S5_PING_JANELA_N=4 S5_TC_AMOSTRAS="1 2" S5_ESPERA_START_S=30 S5_POS_FIM_S=1 \
    S5_L1_ESPERA_COLETOR_S=30 S5_BASE_MIN_N=3 "$@" bash "$T/home/tcc-gate/runs/vm-s5-2026-10-28/s5.sh" --boot-esperado "${ARG_BOOT:-$BOOT}" 2>&1)"; RC=$?
  OUT="$(ls -d "$T/home/tcc-gate/runs/vm-s5-2026-10-28"/vm-* 2>/dev/null | head -1)"
  sleep 0.3
}
nada_destrutivo() { # nada_destrutivo <rotulo>: nem linha de base (sonda), nem l1, nem portao
  ok "$1: sonda nao chamada" 0 "$(grep -c '^java ' "$T/shim.log")"
  ok "$1: l1 nao chamado" 0 "$(grep -c '^L1STUB' "$T/shim.log")"
  ok "$1: portao nao chamado" 0 "$(grep -c '^PORTAO' "$T/shim.log")"
}

echo "== T-s5-0: arquivo, tabela de hashes, porta 11224 e rc na mesma linha =="
bash -n "$S5"; ok "T-s5-0 bash -n" 0 $?
ok "T-s5-0 nenhum CR" 0 "$(tr -cd '\r' < "$S5" | wc -c | tr -d ' ')"
H="$(bash "$S5" --hashes)"
ok "T-s5-0 tabela embutida = arquivos do repositorio (4 OK)" 4 "$(cd "$REPO" && printf '%s\n' "$H" | sha256sum -c - 2>/dev/null | grep -c ': OK$')"
contem "T-s5-0 l1.sh = hash da VM no S1 (9f3deacc...1253)" "9f3deacc6c408d2e70125ede52d409d87b876202cd33b370277b6330775e1253  runs/gate-netdegrade/l1.sh" "$H"
contem "T-s5-0 l-lib.sh = hash da VM no S2 (9bdd1fae...73fd)" "9bdd1faeca0399d11886ee5c77189825c18f5bf69d78797c579c1fd71e9d73fd  runs/gate-netdegrade/l-lib.sh" "$H"
contem "T-s5-0 inject-netdegrade.sh = hash da VM no S2 (4ebdd8a3...abb1)" "4ebdd8a34a7d5c1e6981138272a4f0b90a67c44dd8626e9521884e185297abb1  scripts/inject-netdegrade.sh" "$H"
contem "T-s5-0 portao51.sh = hash do S4 (2598b37d...2338)" "2598b37d4969b1bd717ddbe514110fb6a0077e1332e38c1b76c5caea61802338  runs/vm-s2-2026-10-27/portao51.sh" "$H"
ok "T-s5-0 os 4 arquivos da tabela iguais aos do v4c (902654b)" 0 "$(cd "$REPO" && git diff --name-only 902654b -- scripts/inject-netdegrade.sh \
  runs/gate-netdegrade/l-lib.sh runs/gate-netdegrade/l1.sh runs/vm-s2-2026-10-27/portao51.sh | wc -l | tr -d ' ')"
contem "T-s5-0 l1.sh intacto: o comentario da linha 10 ainda diz 11222 (divergencia registrada, nao editada)" "127.0.0.1:11222" "$(sed -n 10p "$REPO/runs/gate-netdegrade/l1.sh")"
contem "T-s5-0 porta de isn3 = 11224" "PORTA_ISN3=11224" "$(cat "$S5")"
ok "T-s5-0 nenhum boot_id (UUID) no codigo: o esperado vem de --boot-esperado (Q-11)" 0 \
  "$(grep -cE '[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}' "$S5")"
contem "T-s5-0 BOOT_ESPERADO = o argumento --boot-esperado" 'BOOT_ESPERADO="$BOOT_ARG"' "$(cat "$S5")"
nao "T-s5-0 sem override de boot_id esperado no modo de teste" "S5_BOOT_ESPERADO" "$(cat "$S5")"
ok "T-s5-0 FOLGA_LAT_S definido uma unica vez (a decisao do Gestor muda so esta linha)" 1 "$(grep -c '^FOLGA_LAT_S=' "$S5")"
contem "T-s5-0 FOLGA_LAT_S gravado em parametros.txt (a avaliacao le dali)" 'echo "FOLGA_LAT_S=$FOLGA_LAT_S"' "$(cat "$S5")"
# rastreabilidade dos limiares (docs/rastreabilidade-limiares-s5-s7-2026-11-04.md, 4.1 e 4.2; decisao do Gestor, 2026-11-04)
ok "T-s5-0 FOLGA_LAT_S=0 (mediana da janela <= p95 da base, leitura literal do §5.6)" 1 "$(grep -cE '^FOLGA_LAT_S=0( |$)' "$S5")"
ok "T-s5-0 BASE_MIN_N=20 (com n < 20 o p95 por posto mais proximo e o maximo)" 1 "$(grep -cE '^BASE_MIN_N=20( |$)' "$S5")"
contem "T-s5-0 BASE_MIN_N gravado em parametros.txt (a avaliacao le dali)" 'echo "BASE_MIN_N=$BASE_MIN_N"' "$(cat "$S5")"
ok "T-s5-0 nenhum minimo de amostras fixo em 3 na base" 0 "$(grep -cE 'nb" -ge 3|-ge 3 \]' "$S5")"
H=$(( 60 / 2 )); ok "T-s5-0 a base de producao (BASE_S/SAUDE_S = $H) cobre o minimo de 20 por fonte" 1 "$([ "$H" -ge 20 ] && echo 1 || echo 0)"
contem "T-s5-0 BASE_S=60 e SAUDE_S=2 (a conta acima)" "BASE_S=60" "$(cat "$S5")"
contem "T-s5-0 jar esperado = o do S4 (3594c690...7bee)" "JAR_ESPERADO=3594c690cfacd2be1f88e58d2a65a4cd944a06688abcb9ae5b4d3df2f7dc7bee" "$(cat "$S5")"
contem "T-s5-0 l1.sh chamado sem tocar no arquivo" 'bash runs/gate-netdegrade/l1.sh 2>&1 | tee "$OUT/04-l1.log"; RC=${PIPESTATUS[0]}' "$(cat "$S5")"
ok "T-s5-0 todo passo com '| tee \"\$OUT/NN-...' le o PIPESTATUS na mesma linha" 0 \
  "$(grep -E '\| tee "\$OUT/[0-9]' "$S5" | grep -vc 'PIPESTATUS')"
nao "T-s5-0 o s5.sh nao escreve em l1.sh/l-lib.sh/injetor/portao" "> runs/gate-netdegrade/l1.sh" "$(cat "$S5")"
ok "T-s5-0 nenhuma escrita (sed -i/>/>>) sobre os scripts da tabela" 0 \
  "$(grep -E "(sed -i|>>?) *\"?[^ ]*(l1\.sh|l-lib\.sh|inject-netdegrade\.sh|portao51\.sh)" "$S5" | grep -vc 'sha256sum')"

echo "== T-s5-1: avaliar_portao com a saida real do portao do S3 e mutacoes =="
X=$(S5_TESTE=1 bash "$S5" --avaliar-portao "$FIXTURE"); ok "T-s5-1 fixture S3 aceita (rc)" 0 $?
contem "T-s5-1 fixture S3: OK" "PORTAO 5.1: OK (amostras_ruins=0, maior_sequencia_boa_s=131)" "$X"
mut() { sed "$2" "$FIXTURE" > "$T/mut.txt"; X=$(bash "$S5" --avaliar-portao "$T/mut.txt"); local r=$?
  ok "T-s5-1 recusa: $1" 1 "$r"; contem "T-s5-1 motivo: $1" "FALHA portao" "$X"; }
mut "amostra ruim" 's/amostras_ruins=0/amostras_ruins=2/'
mut "sequencia boa < 120 s" 's/maior_sequencia_boa_s=131/maior_sequencia_boa_s=100/'
mut "cache com 1 entrada (sonda nao limpou)" 's/^size sessions porta 11224: 0$/size sessions porta 11224: 1/'
mut "timer tcc-heal" 's/timers tcc-heal: 0 units/timers tcc-heal: 1 units/'
mut "netem residual em isn3" 's/^isn3 proibidos(clsact|prio|netem|ingress)=0/isn3 proibidos(clsact|prio|netem|ingress)=1/'
mut "filtro u32 residual em isn3" 's/^isn3 tc filter eth0: \[\]/isn3 tc filter eth0: [filter parent 1: protocol ip pref 1 u32]/'
mut "lock LIVRE" 's/^lock: OCUPADO/lock: LIVRE/'
mut "mudanca de vista em isn3" 's/^\(isn3 mudancas de vista nos ultimos 5 min .*\): 0$/\1): 1/'
mut "sem a linha fim" '/^fim /d'

echo "== T-s5-1b: argumentos (Q-11): --boot-esperado obrigatorio e em formato UUID; nada alem de leitura =="
nova_arvore
arg() { # arg <rotulo> <trecho> <args...>: rc 1, nenhum vm-* criado, nenhuma chamada a sudo/podman/flock/systemctl/java
  local rot="$1" tr="$2"; shift 2; : > "$T/shim.log"
  X=$(env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX=x SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S5_TESTE=1 \
      bash "$G/runs/vm-s5-2026-10-28/s5.sh" "$@" 2>&1); local r=$?
  ok "T-s5-1b $rot: rc 1" 1 "$r"; contem "T-s5-1b $rot: mensagem" "$tr" "$X"
  contem "T-s5-1b $rot: uso" "uso: bash runs/vm-s5-2026-10-28/s5.sh --boot-esperado <uuid>" "$X"
  ok "T-s5-1b $rot: nenhum vm-* criado" 0 "$(ls -d "$G/runs/vm-s5-2026-10-28"/vm-* 2>/dev/null | wc -l | tr -d ' ')"
  ok "T-s5-1b $rot: nenhum sudo/podman/flock/systemctl/java" 0 "$(grep -cE '^(sudo|podman|flock|systemctl|java|L1STUB|PORTAO)' "$T/shim.log")"; }
arg "sem argumentos" "--boot-esperado ausente"
arg "--boot-esperado sem valor" "falta o valor de --boot-esperado" --boot-esperado
arg "UUID curto" "fora do formato UUID" --boot-esperado b088606d-5d07-4424-b308
arg "UUID com maiusculas" "fora do formato UUID" --boot-esperado B088606D-5D07-4424-B308-0A58FEDF0546
arg "UUID com lixo no fim" "fora do formato UUID" --boot-esperado "${BOOT}x"
arg "argumento desconhecido" "argumento desconhecido: --bloco-anterior" --boot-esperado "$BOOT" --bloco-anterior x

echo "== T-s5-2: caminho completo (TCC_DIR nao definido; go.log com a linha do S4; Gestor digita GO) =="
nova_arvore; roda $'GO\n'
ok "T-s5-2 rc 0" 0 "$RC"
contem "T-s5-2 TCC_DIR exportado pelo s5.sh" "TCC_DIR=$T/home/tcc-gate (exportado pelo s5.sh)" "$SAIDA"
ok "T-s5-2 saida em runs/vm-s5-2026-10-28/vm-*" 1 "$([ -n "$OUT" ] && [ -d "$OUT" ] && echo 1 || echo 0)"
for f in 00-pre.log parametros.txt 01-base.log base/saude.csv base/ping.txt base/sonda-isn3.csv 02-portao51-pre.log 02-portao51-pre.avaliacao \
         03-pre-l1.log 04-l1.log 04-coletor.log janela/saude.csv janela/ping.txt janela/sonda-isn3.csv janela/tc-1.txt janela/tc-2.txt \
         l1/L1.log l1/marcadores.txt l1/go.log l1.sha256 05-pos-l1.log 06-portao51-pos.log 06-portao51-pos.avaliacao 07-boot-fim.txt \
         08-avaliacao.txt s5-console.log rc.txt; do existe "T-s5-2 gravado $f" "$OUT/$f"; done
PRE="$(cat "$OUT/00-pre.log")"
contem "T-s5-2 hashes conferidos" "hashes: iguais a referencia" "$PRE"
contem "T-s5-2 boot_id registrado no inicio" "boot_id: $BOOT" "$PRE"
contem "T-s5-2 argumento registrado" "argumentos: --boot-esperado $BOOT" "$PRE"
contem "T-s5-2 boot_id esperado registrado (Q-11)" "boot_id esperado (--boot-esperado): $BOOT" "$PRE"
contem "T-s5-2 BOOT_ESPERADO em parametros.txt = o argumento" "BOOT_ESPERADO=$BOOT" "$(cat "$OUT/parametros.txt")"
contem "T-s5-2 FOLGA_LAT_S=0 em parametros.txt" "FOLGA_LAT_S=0" "$(grep -x 'FOLGA_LAT_S=0' "$OUT/parametros.txt")"
contem "T-s5-2 BASE_MIN_N em parametros.txt" "BASE_MIN_N=3" "$(cat "$OUT/parametros.txt")"
contem "T-s5-2 go.log versionado com a linha 'go ' do S4 aceito (Q-12)" "runs/gate-b29/go.log: HEAD e prefixo exato; 1 linha(s) 'go ' acrescentada(s) (aceito, Q-12)" "$PRE"
contem "T-s5-2 go.log versionado registrado" "go G-1 (REP=4, janela de 90 s em isn3)" "$PRE"
contem "T-s5-2 boot_id registrado no fim" "boot_id fim: $BOOT" "$(cat "$OUT/07-boot-fim.txt")"
contem "T-s5-2 uname -r registrado" "uname -r: 6.8.0-147-generic" "$PRE"
contem "T-s5-2 lock ocupado registrado" "OCUPADO (este flock)" "$PRE"
contem "T-s5-2 jar conferido" "jar: igual ao do S4" "$PRE"
contem "T-s5-2 IP de isn3 registrado" "isn3 ip=10.89.0.13" "$PRE"
contem "T-s5-2 guarda de netns de isn3" "isn3 netns: diferente do host" "$PRE"
contem "T-s5-2 JGroups sem IPv6" "isn3 JGroups 7800: ipv4=2 ipv6=0" "$PRE"
contem "T-s5-2 NIC da VM sem prio/netem/clsact" "nic eth0 proibidos(prio|netem|clsact)=0" "$PRE"
contem "T-s5-2 go.log do netdegrade registrado" "bloco=L-0c" "$PRE"
contem "T-s5-2 ultima vista do cluster atual registrada (sem exigir a do S4)" "isn3 ultima vista: 2026-11-04 19:14:20,511" "$PRE"
contem "T-s5-2 vista atual com 3 membros registrada" "[isn1-54943|2] (3)" "$PRE"
contem "T-s5-2 ID e StartedAt de isn2 registrados (o S6 os compara, D-4)" "isn2 ip=10.89.0.12 id=5bf9476b5b97 StartedAt=2026-10-28 07:06:19.82 +0000 UTC" "$PRE"
contem "T-s5-2 sem 0 timers e 0 units" "timers tcc-heal: 0 units tcc-heal: 0" "$PRE"
ok "T-s5-2 rc.txt na ordem (pre, base, portao pre, pre-l1, l1, coletor, pos-l1, portao pos, boot-fim, avaliacao)" \
  "pre base 02-portao51-pre 02-portao51-pre-avaliacao pre-l1 l1 coletor pos-l1 06-portao51-pos 06-portao51-pos-avaliacao boot-fim avaliacao" \
  "$(grep -oE '^[a-z0-9-]+ rc=' "$OUT/rc.txt" | sed 's/ rc=//' | paste -sd' ')"
ok "T-s5-2 todos os passos com rc=0" 0 "$(grep -E '^[a-z0-9-]+ rc=' "$OUT/rc.txt" | grep -vc ' rc=0 ')"
L="$T/shim.log"
contem "T-s5-2 REST de isn3 na porta 11224" "curl http://127.0.0.1:11224/rest/v2/cache-managers/default/health" "$(cat "$L")"
contem "T-s5-2 REST de controle (isn1) na porta 11222" "curl http://127.0.0.1:11222/rest/v2/cache-managers/default/health" "$(cat "$L")"
contem "T-s5-2 sonda Hot Rod pinada em isn3:11224" "--no isn3=127.0.0.1:11224 --caches sessions,counters" "$(cat "$L")"
contem "T-s5-2 sonda da base com --duracao-s" "--duracao-s 7" "$(cat "$L")"
contem "T-s5-2 ping do host a isn3 (base)" "ping -n -c 6 -i 0.2 -W 2 10.89.0.13" "$(cat "$L")"
contem "T-s5-2 ping do host a isn3 (janela)" "ping -n -c 4 -i 0.2 -W 2 10.89.0.13" "$(cat "$L")"
contem "T-s5-2 sonda TCP do ssh (VM:22) pelo curl" "curl http://172.31.251.37:22/" "$(cat "$L")"
antes "T-s5-2 linha de base antes do portao pre" "$L" "java -cp" "PORTAO dur="
antes "T-s5-2 portao pre antes do l1" "$L" "PORTAO dur=" "L1STUB"
ok "T-s5-2 portao duas vezes (pre e pos)" 2 "$(grep -c '^PORTAO' "$L")"
contem "T-s5-2 portao pre com a duracao minima" "PORTAO dur=1 csv=$OUT/02-portao51-pre.csv" "$(cat "$L")"
contem "T-s5-2 l1 com TCC_DIR exportado, a partir de ~/tcc-gate, sem LOCK_DIR/PODMAN herdados" "L1STUB TCC_DIR=$T/home/tcc-gate cwd=$T/home/tcc-gate LOCK_DIR= PODMAN=" "$(cat "$L")"
contem "T-s5-2 o GO que chegou ao l1 e o digitado" "L1STUB resposta=GO" "$(cat "$L")"
contem "T-s5-2 aviso do dead-man (~210 s) antes do GO" "~210 s" "$SAIDA"
antes "T-s5-2 aviso do dead-man antes da saida do l1.sh" "$OUT/s5-console.log" "~210 s" "perda observada isn3 -> isn1"
contem "T-s5-2 TcpRetransSegs antes" "isn3 TcpRetransSegs=" "$(cat "$OUT/03-pre-l1.log")"
contem "T-s5-2 TcpRetransSegs depois" "isn3 TcpRetransSegs=" "$(cat "$OUT/05-pos-l1.log")"
contem "T-s5-2 amostra tc -s qdisc durante (netem 30)" "qdisc netem 30:" "$(cat "$OUT/janela/tc-1.txt")"
contem "T-s5-2 amostra tc -s filter durante (u32 -> 1:3)" "flowid 1:3" "$(cat "$OUT/janela/tc-2.txt")"
contem "T-s5-2 coletor viu o start" "start_ns=" "$(cat "$OUT/04-coletor.log")"
contem "T-s5-2 coletor terminou" "coletor fim" "$(cat "$OUT/04-coletor.log")"
contem "T-s5-2 sonda da janela fez remove-final" "remove-final" "$(cat "$OUT/janela/sonda-isn3.csv")"
contem "T-s5-2 sonda da base fez remove-final" "remove-final" "$(cat "$OUT/base/sonda-isn3.csv")"
contem "T-s5-2 marcadores copiados" "event=end" "$(cat "$OUT/l1/marcadores.txt")"
contem "T-s5-2 go do L-1 copiado" "bloco=L-1" "$(cat "$OUT/l1/go.log")"
contem "T-s5-2 sha256 das copias" "l1/L1.log" "$(cat "$OUT/l1.sha256")"
contem "T-s5-2 vistas desde o inicio do L-1 pedidas" "podman logs --since" "$(cat "$L")"
contem "T-s5-2 timers e units depois do L-1" "timers tcc-heal: 0 units tcc-heal: 0" "$(cat "$OUT/05-pos-l1.log")"
AV="$(cat "$OUT/08-avaliacao.txt")"
contem "T-s5-2 avaliacao: VALIDO" "S5: VALIDO" "$AV"
nao "T-s5-2 avaliacao sem FALHA" "FALHA" "$AV"
contem "T-s5-2 avaliacao: perdas isn1 61/300" "isn3 -> isn1: 20.3333% = 61 perdas em 300" "$AV"
contem "T-s5-2 avaliacao: perdas isn2 57/300" "isn3 -> isn2: 19% = 57 perdas em 300" "$AV"
contem "T-s5-2 avaliacao: dropped/(Sent+dropped)" "dropped/(Sent+dropped) = 400/2000 = 0.2000" "$AV"
contem "T-s5-2 avaliacao: 2 amostras tc validas" "2 amostras de tc -s qdisc/filter validas durante a janela" "$AV"
contem "T-s5-2 avaliacao: sem mudanca de vista e RESULTADO" "RESULTADO: nenhuma mudanca de vista" "$AV"
contem "T-s5-2 avaliacao: TcpRetransSegs e RESULTADO (H-L)" "RESULTADO: TcpRetransSegs isn3" "$AV"
contem "T-s5-2 avaliacao: declara a leitura humana" "LEITURA HUMANA:" "$AV"
contem "T-s5-2 avaliacao rodada de novo (--avaliar) da o mesmo veredito" "S5: VALIDO" "$(bash "$S5" --avaliar "$OUT")"
nao "T-s5-2 nenhuma chamada inesperada" "inesperado" "$SAIDA"
cp -r "$OUT" "$T/bom"   # base das mutacoes de T-s5-8

echo "== T-s5-3: TCC_DIR definido igual a ~/tcc-gate e aceito; diferente aborta =="
nova_arvore; roda $'GO\n' TCC_DIR="$T/home/tcc-gate"
ok "T-s5-3 TCC_DIR igual: rc 0" 0 "$RC"; contem "T-s5-3 ja exportado" "(ja exportado)" "$SAIDA"
nova_arvore; roda $'GO\n' TCC_DIR="$T/home/tcc-desenvolvimento"
ok "T-s5-3 TCC_DIR diferente: rc 1" 1 "$RC"; contem "T-s5-3 mensagem" "precisa ser $T/home/tcc-gate" "$SAIDA"; nada_destrutivo "T-s5-3"

echo "== T-s5-4: pre-condicoes que falham abortam antes da linha de base =="
precond() { # precond <rotulo> <trecho-esperado> VAR=valor...
  local rot="$1" tr="$2"; shift 2; nova_arvore; prep_extra; roda $'GO\n' "$@"
  ok "T-s5-4 $rot: rc 1" 1 "$RC"; contem "T-s5-4 $rot: motivo" "$tr" "$(cat "$OUT/00-pre.log" 2>/dev/null)"
  contem "T-s5-4 $rot: abortado" "S5 ABORTADO (rc=1): pre-condicoes" "$SAIDA"; nada_destrutivo "T-s5-4 $rot"; prep_extra() { :; }; }
prep_extra() { :; }
precond "fora do tmux" "fora do tmux" TMUX=
precond "lock livre" "LIVRE" SHIM_LOCK=livre
precond "sudo -n falha" "sudo -n true falhou" SHIM_SUDO_FALHA=1
precond "kernel diferente" "kernel 6.8.0-150-generic" SHIM_KERNEL=6.8.0-150-generic
echo 11111111-2222-3333-4444-555555555555 > "$T/boot_outro"
precond "boot_id atual diferente do esperado (Q-11)" "boot_id atual 11111111-2222-3333-4444-555555555555 diferente de --boot-esperado $BOOT" S5_BOOT_ID_FILE="$T/boot_outro"
ARG_BOOT=00000000-0000-0000-0000-000000000000
precond "--boot-esperado diferente do atual (Q-11)" "boot_id atual $BOOT diferente de --boot-esperado 00000000-0000-0000-0000-000000000000"
unset ARG_BOOT
precond "timer tcc-heal armado" "1 timers tcc-heal armados" SHIM_TIMERS=1
precond "unit tcc-heal carregada" "1 units tcc-heal carregadas" SHIM_UNITS=1
precond "LOCK_DIR herdado" "variaveis herdadas que mudariam o l1/injetor: LOCK_DIR" LOCK_DIR=/tmp/x
precond "PODMAN herdado" "variaveis herdadas que mudariam o l1/injetor: PODMAN" PODMAN=podman
precond "jar diferente do S4" "jar diferente do S4" S5_JAR_ESPERADO=0000
precond "IPv6 no TCP do JGroups" "IPv6 no TCP do JGroups" SHIM_IPV6=1
precond "NIC da VM com prio" "NIC da VM (eth0) com prio/netem/clsact" SHIM_NIC_PRIO=1
precond "isn3 no netns do host" "netns de isn3 igual ao do host" SHIM_NETNS_HOST=1
precond "PID de isn3 invalido" "PID de isn3 invalido" SHIM_PID=0
precond "IP de isn3 invalido" "IP de isn3 invalido" SHIM_IP3=
prep_extra() { echo "# alterado" >> "$G/runs/gate-netdegrade/l1.sh"; ( cd "$G" && git -c user.name=t -c user.email=t@t commit -qam alt ); }
precond "hash do l1.sh diferente" "hash diferente da referencia"
prep_extra() { echo "# sujo" >> "$G/runs/gate-netdegrade/l-lib.sh"; }
precond "outro arquivo versionado modificado (Q-12)" "1 arquivo(s) versionado(s) modificado(s) alem de runs/gate-b29/go.log"
# regra do go.log (Q-12), a mesma do s6.sh/s7.sh
prep_extra() { sed -i 's/^go 2026-10-16T05:11:03+00:00/go 2026-10-16T05:11:04+00:00/' "$G/runs/gate-b29/go.log"; }
precond "go.log com linha antiga editada (Q-12)" "o conteudo do HEAD nao e prefixo exato do arquivo (linha antiga alterada)"
prep_extra() { echo "GO L-1 sem o prefixo" >> "$G/runs/gate-b29/go.log"; }
precond "go.log com linha a mais sem 'go ' (Q-12)" "linha acrescentada que nao comeca com 'go '"
prep_extra() { head -3 "$G/runs/gate-b29/go.log" > "$T/gl"; cp "$T/gl" "$G/runs/gate-b29/go.log"; }
precond "go.log truncado (Q-12)" "o conteudo do HEAD nao e prefixo exato do arquivo (linha antiga alterada)"
prep_extra() { ( cd "$G" && git add runs/gate-b29/go.log ); }
precond "go.log no indice (M , nao ' M')" "alem de runs/gate-b29/go.log (ou runs/gate-b29/go.log em estado diferente de ' M')"
pre_ok() { # pre_ok <rotulo>: o passo 0 passou (o portao pre ruim parou o bloco em seguida, rc 3)
  ok "$1: rc 3 (pre-condicoes OK, parou no portao pre de proposito)" 3 "$RC"; contem "$1: pre rc=0" "pre rc=0 " "$(cat "$OUT/rc.txt" 2>/dev/null)"; }
nova_arvore; git -C "$G" checkout -q -- runs/gate-b29/go.log; roda $'GO\n' S5T_PORTAO_RUIM=1
pre_ok "T-s5-4 arvore limpa (Q-12)"; contem "T-s5-4 arvore limpa: 0 modificados" "arquivos versionados modificados: 0" "$(cat "$OUT/00-pre.log")"
nova_arvore; echo "go L-1 (x) 2026-11-04T20:00:00+00:00 vm=x sha=y" >> "$G/runs/gate-b29/go.log"; roda $'GO\n' S5T_PORTAO_RUIM=1
pre_ok "T-s5-4 go.log com 2 linhas 'go ' a mais (Q-12)"; contem "T-s5-4 2 linhas aceitas" "2 linha(s) 'go ' acrescentada(s) (aceito, Q-12)" "$(cat "$OUT/00-pre.log")"
prep_extra() { echo "rc=0 (esperado 0)" > "$G/runs/gate-netdegrade/L1.log"; }
precond "L1.log de uma execucao anterior" "runs/gate-netdegrade/L1.log ja existe"
prep_extra() { echo "fault=F4 node=isn3 event=start t_epoch_ns=1" > "$G/runs/gate-netdegrade/marcadores.txt"; }
precond "marcadores.txt de uma execucao anterior" "runs/gate-netdegrade/marcadores.txt ja existe"
prep_extra() { rm -f "$G/workload/target/"*.jar; ( cd "$G" && git -c user.name=t -c user.email=t@t commit -qam semjar ); }
precond "jar ausente" "jar workload/target/session-workload-*.jar ausente"

echo "== T-s5-5: linha de base sem remove-final da sonda aborta antes do portao =="
nova_arvore; roda $'GO\n' SHIM_SONDA_SEM_FINAL=1
ok "T-s5-5 rc 2" 2 "$RC"; contem "T-s5-5 motivo" "remove-final" "$(cat "$OUT/01-base.log")"
ok "T-s5-5 sem portao" 0 "$(grep -c '^PORTAO' "$T/shim.log")"; ok "T-s5-5 l1 nao chamado" 0 "$(grep -c '^L1STUB' "$T/shim.log")"

nova_arvore; roda $'GO\n' S5_BASE_MIN_N=50
ok "T-s5-5 base com menos de BASE_MIN_N amostras por fonte: rc 2" 2 "$RC"; contem "T-s5-5 motivo BASE_MIN_N" "menos de 50 amostras de 11222" "$(cat "$OUT/01-base.log")"

echo "== T-s5-6: portao pre ruim PARA (sem l1) =="
nova_arvore; roda $'GO\n' S5T_PORTAO_RUIM=1
ok "T-s5-6 rc 3" 3 "$RC"; contem "T-s5-6 mensagem" "portao 5.1 pre falhou: S5 NAO roda" "$SAIDA"
contem "T-s5-6 avaliacao gravada" "FALHA portao: amostras_ruins=1" "$(cat "$OUT/02-portao51-pre.avaliacao")"
ok "T-s5-6 l1 nao chamado" 0 "$(grep -c '^L1STUB' "$T/shim.log")"

echo "== T-s5-7: Gestor nao digita GO: l1 aborta, coletor encerrado sem coletar, sem portao pos =="
nova_arvore; roda $'nao\n'
ok "T-s5-7 rc 4" 4 "$RC"; contem "T-s5-7 resposta que chegou ao l1" "L1STUB resposta=nao" "$(cat "$T/shim.log")"
contem "T-s5-7 rc do l1 lido na mesma linha" "l1 rc=1 " "$(cat "$OUT/rc.txt")"
contem "T-s5-7 coletor encerrado" "coletor encerrado (l1 sem event=start)" "$(cat "$OUT/rc.txt")"
ok "T-s5-7 sem amostras tc" 0 "$(ls "$OUT/janela"/tc-*.txt 2>/dev/null | wc -l | tr -d ' ')"
ok "T-s5-7 portao so o pre" 1 "$(grep -c '^PORTAO' "$T/shim.log")"
ok "T-s5-7 sem go do L-1 no go.log" 0 "$(grep -c 'bloco=L-1$' "$T/home/tcc-gate/runs/gate-netdegrade/go.log")"
contem "T-s5-7 instrucao de cura manual" "PODMAN='sudo podman' bash scripts/inject-netdegrade.sh --heal" "$SAIDA"
existe "T-s5-7 estado apos falha gravado" "$OUT/05-pos-falha.log"
sleep 1; ok "T-s5-7 nenhum java da janela" 1 "$(grep -c '^java ' "$T/shim.log")"

echo "== T-s5-8: injetor falha depois do start: rc 4, dead-man avisado, coletas preservadas =="
nova_arvore; roda $'GO\n' S5T_INJ_RC=6
ok "T-s5-8 rc 4" 4 "$RC"; contem "T-s5-8 aviso do dead-man armado (~210 s)" "~210 s" "$(cat "$OUT/05-pos-falha.log")"
contem "T-s5-8 L1.log copiado mesmo assim" "rc=6 (esperado 0)" "$(cat "$OUT/l1/L1.log")"
existe "T-s5-8 amostra tc preservada" "$OUT/janela/tc-1.txt"
ok "T-s5-8 portao so o pre" 1 "$(grep -c '^PORTAO' "$T/shim.log")"

echo "== T-s5-9: portao pos ruim: rc 5, avaliacao roda e marca a falha =="
nova_arvore; roda $'GO\n' S5T_PORTAO_RUIM=2
ok "T-s5-9 rc 5" 5 "$RC"; contem "T-s5-9 avaliacao INVALIDO" "S5: INVALIDO" "$(cat "$OUT/08-avaliacao.txt")"
contem "T-s5-9 criterio do portao pos" "FALHA: portao 5.1 pos" "$(cat "$OUT/08-avaliacao.txt")"

echo "== T-s5-10: caminho completo com criterio violado: rc 6 =="
nova_arvore; roda $'GO\n' S5T_PERDA1=10
ok "T-s5-10 perdas fora do intervalo: rc 6" 6 "$RC"; contem "T-s5-10 motivo" "FALHA: perda observada isn3 -> isn1: 10% = 30 perdas" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda $'GO\n' S5T_TROCA_BOOT=1
ok "T-s5-10 boot_id mudou durante o bloco: rc 6" 6 "$RC"; contem "T-s5-10 motivo boot" "FALHA: boot_id inicio=$BOOT fim=11111111" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda $'GO\n' SHIM_REST_LENTO=1
ok "T-s5-10 REST de isn3 degradado na janela: rc 6" 6 "$RC"; contem "T-s5-10 motivo REST" "FALHA: REST de isn3 (11224) time_total" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda $'GO\n' SHIM_PING_PERDA=1
ok "T-s5-10 ping host->isn3 com perda na janela: rc 6" 6 "$RC"; contem "T-s5-10 motivo ping" "FALHA: ping do host a isn3 durante a janela" "$(cat "$OUT/08-avaliacao.txt")"
nova_arvore; roda $'GO\n' SHIM_VISTA=1
ok "T-s5-10 mudanca de vista: resultado, nao falha (rc 0)" 0 "$RC"
contem "T-s5-10 vista como RESULTADO" "RESULTADO: 3 linha(s) de mudanca de vista" "$(cat "$OUT/08-avaliacao.txt")"
contem "T-s5-10 vista bloqueia o S6" "bloqueia o S6" "$(cat "$OUT/08-avaliacao.txt")"

echo "== T-s5-11: avaliacao (--avaliar) sobre copias mutadas da execucao boa =="
B="$T/bom"
av() { # av <rotulo> <trecho-esperado> <comando-de-mutacao (roda em $M)>
  rm -rf "$T/m"; cp -r "$B" "$T/m"; M="$T/m"; ( cd "$M" && eval "$3" ); X=$(bash "$S5" --avaliar "$M"); local r=$?
  ok "T-s5-11 $1: rc 1" 1 "$r"; contem "T-s5-11 $1: motivo" "$2" "$X"; contem "T-s5-11 $1: INVALIDO" "S5: INVALIDO" "$X"; }
avok() { rm -rf "$T/m"; cp -r "$B" "$T/m"; M="$T/m"; ( cd "$M" && eval "$3" ); X=$(bash "$S5" --avaliar "$M"); local r=$?
  ok "T-s5-11 $1: rc 0" 0 "$r"; contem "T-s5-11 $1: $2" "$2" "$X"; }
avok "81 perdas (27%) no limite" "27% = 81 perdas em 300" "sed -i 's/isn1: 20.3333%/isn1: 27%/' l1/L1.log"
av   "82 perdas (27.3333%)" "FALHA: perda observada isn3 -> isn1: 27.3333% = 82 perdas" "sed -i 's/isn1: 20.3333%/isn1: 27.3333%/' l1/L1.log"
avok "39 perdas (13%) no limite" "13% = 39 perdas em 300" "sed -i 's/isn2: 19%/isn2: 13%/' l1/L1.log"
av   "38 perdas (12.6667%)" "FALHA: perda observada isn3 -> isn2: 12.6667% = 38 perdas" "sed -i 's/isn2: 19%/isn2: 12.6667%/' l1/L1.log"
av   "so uma linha de perda" "FALHA: perda observada: esperadas 2 linhas" "sed -i '/-> isn2/d' l1/L1.log"
av   "dropped 0" "FALHA: contadores de isn3" "sed -i 's/(dropped 400,/(dropped 0,/' l1/L1.log"
av   "dropped/(Sent+dropped) longe de 20%" "FALHA: contadores de isn3" "sed -i 's/(dropped 400,/(dropped 1000,/' l1/L1.log"
av   "injetor rc != 0" "FALHA: injetor" "sed -i 's/^rc=0 (esperado 0)$/rc=6 (esperado 0)/' l1/L1.log"
av   "l1 rc != 0 no rc.txt" "FALHA: l1.sh" "sed -i 's/^l1 rc=0 /l1 rc=1 /' rc.txt"
av   "start duplicado" "FALHA: marcadores" "head -1 l1/marcadores.txt >> l1/marcadores.txt"
av   "uma amostra tc so" "FALHA: 1 amostras de tc -s qdisc/filter validas" "rm janela/tc-2.txt"
av   "amostra tc sem netem" "FALHA: 1 amostras de tc -s qdisc/filter validas" "sed -i '/netem 30:/d' janela/tc-1.txt"
av   "filtro u32 com IP extra" "FALHA: 1 amostras de tc -s qdisc/filter validas" "echo '  match 0a5900ff/ffffffff at 16 (success 1 )' >> janela/tc-1.txt"
av   "amostra tc depois do end" "FALHA: 1 amostras de tc -s qdisc/filter validas" "sed -i 's/^t_epoch_ns=[0-9]*/t_epoch_ns=9999999999999999999/' janela/tc-2.txt"
av   "REST de isn3 com HTTP 000 na janela" "FALHA: REST de isn3 (11224) com HTTP diferente de 200" \
     "S=\$(sed -n 's/.*event=start t_epoch_ns=//p' l1/marcadores.txt); awk -F, -v s=\"\$S\" 'BEGIN{OFS=\",\"} NR>1 && \$2==\"11224\" && \$1>=s && !d {\$3=\"000\"; d=1} {print}' janela/saude.csv > x && mv x janela/saude.csv"
av   "ssh22 sem resposta na janela" "FALHA: ssh (VM:22)" \
     "S=\$(sed -n 's/.*event=start t_epoch_ns=//p' l1/marcadores.txt); awk -F, -v s=\"\$S\" 'BEGIN{OFS=\",\"} NR>1 && \$2==\"ssh22\" && \$1>=s && !d {\$4=\"ERRO\"; d=1} {print}' janela/saude.csv > x && mv x janela/saude.csv"
av   "intervalo do saude-log > 2x o nominal" "FALHA: intervalo maximo do saude-log" \
     "awk -F, 'BEGIN{OFS=\",\"} NR>1 && \$2==\"11223\" && ++k==2 {\$1=\$1+5000000000} {print}' base/saude.csv > x && mv x base/saude.csv"
av   "linha de base do ping com perda" "FALHA: ping do host a isn3 na linha de base" "sed -i 's/^6 packets transmitted, 6 received/6 packets transmitted, 5 received/' base/ping.txt"
av   "boot_id do fim ausente" "FALHA: boot_id" "rm 07-boot-fim.txt"
av   "L-2 sem 'ok sem prio em isn3'" "FALHA: L-2" "sed -i '/^ok sem prio em isn3$/d' 04-l1.log"
av   "NIC da VM com netem depois do L-1" "FALHA: NIC da VM" "sed -i 's/^nic eth0 proibidos(prio|netem|clsact)=0/nic eth0 proibidos(prio|netem|clsact)=1/' 05-pos-l1.log"
av   "timer tcc-heal depois do L-1" "FALHA: timers/units tcc-heal depois do L-1" "sed -i 's/^timers tcc-heal: 0 units tcc-heal: 0$/timers tcc-heal: 1 units tcc-heal: 1/' 05-pos-l1.log"
av   "portao pos FALHA" "FALHA: portao 5.1 pos" "echo 'FALHA portao: amostras_ruins=1' > 06-portao51-pos.avaliacao"
av   "sem parametros.txt" "FALHA: sem" "rm parametros.txt"
# a regra de latencia usa o FOLGA_LAT_S gravado em parametros.txt; com FOLGA_LAT_S=0 a copia boa e o caso de igualdade
# (o shim do curl da 0,003 s em todas as amostras: mediana da janela = p95 da base) e 1 us acima ja recusa
contem "T-s5-11 copia boa: mediana = p95 da base aceita com folga 0 (fronteira)" "OK: REST de controle isn1 (11222) time_total: mediana durante 0.003000 s <= p95 da base 0.003000 s + 0 s" "$(bash "$S5" --avaliar "$B")"
av   "mediana da janela 1 us acima do p95 da base (folga 0)" "FALHA: REST de controle isn1 (11222) time_total: mediana durante 0.003001 s" \
     "awk -F, 'BEGIN{OFS=\",\"} NR>1 && \$2==\"11222\" {\$5=\"0.003001\"} {print}' janela/saude.csv > x && mv x janela/saude.csv"
# minimo de amostras da base lido de parametros.txt (BASE_MIN_N): o menor n de base dos canais da regra de latencia
NB=$(cd "$B" && { for p in 11222 11224 ssh22; do awk -F, -v p="$p" 'NR > 1 && $2 == p' base/saude.csv | wc -l; done; grep -c 'time=' base/ping.txt; } | sort -n | head -1 | tr -d ' ')
avok "BASE_MIN_N igual ao menor n da base ($NB) aceita (fronteira)" "S5: VALIDO" "sed -i 's/^BASE_MIN_N=.*/BASE_MIN_N=$NB/' parametros.txt"
av   "BASE_MIN_N = menor n da base + 1 recusa" "amostras insuficientes (n base=$NB >= $((NB + 1))" "sed -i 's/^BASE_MIN_N=.*/BASE_MIN_N=$((NB + 1))/' parametros.txt"
av   "parametros.txt sem BASE_MIN_N" "FALHA: parametros.txt sem BASE_MIN_N" "sed -i '/^BASE_MIN_N=/d' parametros.txt"
av   "FOLGA_LAT_S negativa em parametros.txt recusa a latencia" "FALHA: REST de isn3 (11224) time_total: mediana durante" "sed -i 's/^FOLGA_LAT_S=.*/FOLGA_LAT_S=-1/' parametros.txt"
avok "a regra da latencia cita o FOLGA_LAT_S de parametros.txt" "+ 0.0025 s" "sed -i 's/^FOLGA_LAT_S=.*/FOLGA_LAT_S=0.0025/' parametros.txt"
avok "DEGRADED no saude-log e RESULTADO" "RESULTADO: 1 amostra(s) do saude-log na janela fora de HEALTHY/3" \
     "S=\$(sed -n 's/.*event=start t_epoch_ns=//p' l1/marcadores.txt); awk -F, -v s=\"\$S\" 'BEGIN{OFS=\",\"} NR>1 && \$2==\"11224\" && \$1>=s && !d {\$6=\"DEGRADED\"; d=1} {print}' janela/saude.csv > x && mv x janela/saude.csv"

echo "RESUMO: $P PASS, $F FAIL"
[ "$F" = 0 ]
