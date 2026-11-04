#!/usr/bin/env bash
# runs/vm-s7-2026-11-04/s7.test.sh -- teste SEM VM do involucro s7.sh (S7: G-5b-B, o dead-man disparando sozinho),
# com shims (sudo/podman/nsenter/flock/uname/systemctl/journalctl/curl/ip/tc/hostname) e stubs de g5b.sh, g56-lib.sh,
# g-lib.sh, inject-partition.sh e portao51.sh numa arvore falsa $HOME/tcc-gate (git proprio).
# O stub de g5b.sh REPRODUZ uma saida do g5b.sh REAL: no inicio o teste roda o runs/gate-b29/g5b.sh verdadeiro (modo B)
# sobre os shims de gate-test-lib-v4.sh com G5B_* curtos e guarda o diretorio g5b-B como fixture; o stub reencena a
# cronologia dele em tempo real (clsact em isn3, vistas, disparo da unit do dead-man no journal do sudo e da unit) e copia o fixture com os
# instantes deslocados para "agora" (prompt da secao 4.6, primeira opcao).
# Prova: --boot-esperado/--bloco-anterior (2.1); ordem pre -> dry-run -> portao pre -> coletor + g5b.sh (GO so pelo stdin)
# -> pos -> portao pos -> boot_id fim -> avaliacao VALIDO -> MANIFEST; cada pre-condicao que falha aborta antes do portao e
# do g5b.sh; a regra do go.log (Q-12) nos 5 casos; tabela de hashes = repositorio e sem diff desde 902654b; funcoes do
# s5.sh copiadas byte a byte; rc lido na mesma linha em todo tee "$OUT/; portao pre ruim, g5b.sh sem GO, GO fora do prazo do
# coletor, g5b.sh falhando depois de armar (espera do dead-man), reboot no meio, Ctrl-C real, nenhum tc de escrita vindo do
# involucro, --avaliar reproduzivel, --dry-run sem efeito; cada criterio da avaliacao violado isoladamente.
# NAO prova: podman, tc, systemd, journald, Infinispan, nem o g5b.sh na VM (tem teste proprio, g5b.test.sh).
# NAO toca na VM nem em rede.
# Rodar (sozinho, sem outra suite pesada em paralelo):  bash runs/vm-s7-2026-11-04/s7.test.sh      Exit 0 se tudo passar.
# S7T_SO="T-0 T-2 ..." roda so as secoes listadas (T-7 e T-11 dependem de T-2; usado nos mutantes).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$AQUI/../.." && pwd)"
S7="$AQUI/s7.sh"
S5="$REPO/runs/vm-s5-2026-10-28/s5.sh"
P=0; F=0; T_INI=$(date +%s)
ok()     { if [ "$2" = "$3" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (esperado '$2', obtido '$3')"; F=$((F+1)); fi; }
contem() { if printf '%s' "$3" | grep -qF -- "$2"; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (sem '$2')"; F=$((F+1)); fi; }
nao()    { if printf '%s' "$3" | grep -qF -- "$2"; then echo "FAIL: $1 (com '$2')"; F=$((F+1)); else echo "PASS: $1"; P=$((P+1)); fi; }
antes()  { # antes <desc> <arquivo> <a> <b>: primeira linha com a vem antes da primeira com b
  local la lb; la=$(grep -nF -m1 -- "$3" "$2" | cut -d: -f1); lb=$(grep -nF -m1 -- "$4" "$2" | cut -d: -f1)
  if [ -n "$la" ] && [ -n "$lb" ] && [ "$la" -lt "$lb" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 ('$3'@${la:-?} '$4'@${lb:-?})"; F=$((F+1)); fi; }
existe() { ok "$1" 1 "$([ -s "$2" ] && echo 1 || echo 0)"; }
secao()  { [ -z "${S7T_SO:-}" ] && return 0; case " $S7T_SO " in *" $1 "*) return 0;; esac; return 1; }

[ -f "$S7" ] || { echo "FAIL: $S7 nao existe (vermelho esperado antes do feat)"; echo "RESUMO: 0 PASS, 1 FAIL"; exit 1; }

T="$(mktemp -d)"
BIN="$T/bin"; mkdir -p "$BIN"
FIXTURE="$T/portao-ok.txt"
sed -n '/^amostras=/,/^fim /p' "$REPO/runs/vm-s3-2026-10-28/00-portao51-pre-console.txt" > "$FIXTURE"
BOOT=5d7119b8-3b50-435d-bd0d-cbdb6bd410df
ANTN=vm-20261104T050000Z
limpa() { local p; for p in $(cat "$T/pids" 2>/dev/null); do kill -TERM "$p" 2>/dev/null; done; rm -rf "$T"; }
trap limpa EXIT

# ---------------- fixture: o g5b.sh REAL (modo A) sobre os shims de gate-test-lib-v4.sh, com G5B_* curtos
cat > "$T/gerar.sh" <<'EOF'
# gerar.sh <dir runs/gate-b29> <A|B> <destino>
source "$1/gate-test-lib-v4.sh"
export SHIM_SYSTEMD_255=1
reset_shims_v4
export G5B_DUR=8 G5B_T_KILL=4 G5B_ESPERA_START=6 G5B_ESPERA_SPLIT=8 G5B_PERSISTE_S=3 G5B_LIMITE_S=20 G5B_MARGEM_B=6 G5B_TOL_S=5 G5B_FOLGA_S=3
roda "GO" "$1/g5b.sh" "$2"
echo "g5b.sh $2 (real, shims de gate-test-lib-v4.sh) rc=$RC"
matar_timers
[ "$RC" = 0 ] || { echo "$SAIDA"; exit 1; }
cp -r "$TCC/runs/gate-b29/g5b-$2" "$3" && echo "$TCC" > "$3/.tcc-origem"
EOF
TF=$(date +%s)
bash "$T/gerar.sh" "$REPO/runs/gate-b29" B "$T/fixB" > "$T/gerar.log" 2>&1
ok "fixture: g5b.sh B real gerou g5b-B ($(( $(date +%s) - TF )) s)" 1 "$([ -s "$T/fixB/cronologia.txt" ] && grep -q '^t_saudavel=' "$T/fixB/cronologia.txt" && echo 1 || echo 0)"
[ -s "$T/fixB/cronologia.txt" ] || { cat "$T/gerar.log"; echo "RESUMO: $P PASS, $((F+1)) FAIL"; exit 1; }
contem "fixture: resultado.txt do g5b.sh real sem FAIL" "PASS cronologia coerente" "$(cat "$T/fixB/resultado.txt")"

# ---------------- shims
cat > "$BIN/sudo" <<'EOF'
#!/usr/bin/env bash
echo "sudo $* origem=${SHIM_ORIGEM:-involucro}" >> "$SHIM_LOG"
# journal do sudo (formato de journalctl -o json: um objeto por linha); a unit do dead-man ou a sessao
if [ -n "${SHIM_UNIDADE:-}" ]; then quem="    root : PWD=$PWD ; USER=root ; COMMAND=/usr/bin/$*"; u="$SHIM_UNIDADE"
else quem="  server1 : TTY=pts/1 ; PWD=$PWD ; USER=root ; COMMAND=/usr/bin/$*"; u=session-3.scope; fi
[ "$1" = -n ] && quem="${quem/COMMAND=\/usr\/bin\/-n /COMMAND=/usr/bin/}"
printf '{"__CURSOR":"s=f00;i=%s","__REALTIME_TIMESTAMP":"%s","_BOOT_ID":"x","PRIORITY":"5","SYSLOG_IDENTIFIER":"sudo","MESSAGE":"%s","_COMM":"sudo","_SYSTEMD_UNIT":"%s"}\n' \
  "$RANDOM" "$(( $(date +%s%N) / 1000 ))" "$quem" "$u" >> "$SHIM_STATE/sudo-journal.json"
case "$1" in
  -n) [ -z "${SHIM_SUDO_FALHA:-}" ]; exit $?;;
  podman) shift; exec podman "$@";;
  systemctl) shift; exec systemctl "$@";;
  journalctl) shift; exec journalctl "$@";;
  readlink) case "$2" in /proc/1/ns/net) echo 'net:[4026531840]';;
              *) if [ -n "${SHIM_NETNS_HOST:-}" ]; then echo 'net:[4026531840]'; else echo 'net:[4026532999]'; fi;; esac; exit 0;;
  nsenter) shift 4  # -t <pid> -n
    case "$*" in
      "tc qdisc show dev eth0") echo "qdisc noqueue 0: root refcnt 2"; [ ! -e "$SHIM_STATE/clsact" ] || echo "qdisc clsact ffff: parent ffff:fff1";;
      "tc qdisc add dev eth0 clsact") : > "$SHIM_STATE/clsact";;
      "tc qdisc del dev eth0 clsact") [ -e "$SHIM_STATE/clsact" ] || { echo "Error: Cannot find specified qdisc on specified device." >&2; exit 2; }; rm -f "$SHIM_STATE/clsact";;
      "ss -tn") echo "State Recv-Q Send-Q Local Address:Port Peer Address:Port Process"
        echo "ESTAB 0 0 10.89.0.13:7800 10.89.0.11:41000"; echo "ESTAB 0 0 10.89.0.13:52000 10.89.0.12:7800";;
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
  ps) for m in isn1 isn2 isn3; do echo "$m 5bf9476b5b97 Up 7 days (healthy) criado=2026-10-28 07:06:1x"; done;;
  logs) if [ "$2" = --since ]; then sed -n "s/^$n|//p" "$SHIM_STATE/vistas" 2>/dev/null
        else echo "2026-10-28 07:14:34,088 INFO  [o.i.CLUSTER] ISPN000093: Received new, MERGED cluster view for channel cluster-tcc: MergeView::[isn3-54173|4] (3) [isn3-54173, isn1-23890, isn2-1]"; fi;;
  inspect) v="SHIM_ID_$n"; s="SHIM_ST_$n"
    case "$*" in
      *IPAddress*) case "$n" in isn1) echo "10.89.0.11 ";; isn2) echo "10.89.0.12 ";; isn3) echo "10.89.0.13 ";; esac;;
      *State.Pid*) echo "${SHIM_PID:-4003}";; *StartedAt*) echo "${!s:-2026-10-28 07:06:19.82 +0000 UTC}";;
      *.Id*) echo "${!v:-5bf9476b5b97aaaa}";; *) echo '[{"Name":"x"}]';; esac;;
esac
exit 0
EOF
cat > "$BIN/flock" <<'EOF'
#!/usr/bin/env bash
echo "flock $*" >> "$SHIM_LOG"
case "$*" in *inject-partition*) [ "${SHIM_INJ_LOCK:-livre}" = livre ];; *) [ "${SHIM_LOCK:-ocupado}" = livre ];; esac
EOF
cat > "$BIN/uname" <<'EOF'
#!/usr/bin/env bash
echo "${SHIM_KERNEL:-6.8.0-147-generic}"
EOF
cat > "$BIN/systemctl" <<'EOF'
#!/usr/bin/env bash
echo "systemctl $*" >> "$SHIM_LOG"
case "$1" in
  list-timers) [ -z "${SHIM_TIMERS:-}" ] || echo "Wed 2026-11-04 n/a tcc-heal-1-2.timer tcc-heal-1-2.service"
               [ ! -s "$SHIM_STATE/timer" ] || { u=$(cat "$SHIM_STATE/timer"); echo "Wed 2026-11-04 06:00:00 UTC 3min left - - $u.timer $u.service"; };;
  list-units)  [ -z "${SHIM_UNITS:-}" ] || echo "tcc-heal-1-2.service loaded active running";;
esac
exit 0
EOF
cat > "$BIN/journalctl" <<'EOF'
#!/usr/bin/env bash
echo "journalctl $*" >> "$SHIM_LOG"
case "$*" in
  *_COMM=sudo*) s=0; prev=; for a in "$@"; do [ "$prev" = --since ] && s="${a#@}"; prev="$a"; done
    awk -v s="$s" 'match($0, /"__REALTIME_TIMESTAMP":"[0-9]+"/) { t = substr($0, RSTART + 24, RLENGTH - 25); if (t + 0 >= s * 1e6) print }' "$SHIM_STATE/sudo-journal.json" 2>/dev/null;;
  *-u*) if [ -s "$SHIM_STATE/journal-unit" ]; then cat "$SHIM_STATE/journal-unit"; else echo "-- No entries --"; fi;;
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
echo "tc $* origem=${SHIM_ORIGEM:-involucro}" >> "$SHIM_LOG"
if [ -n "${SHIM_NIC_PRIO:-}" ]; then echo "qdisc prio 1: root refcnt 2 bands 3"; else echo "qdisc mq 0: root"; fi
EOF
cat > "$BIN/curl" <<'EOF'
#!/usr/bin/env bash
url="${@: -1}"; o=; prev=
for a in "$@"; do [ "$prev" = -o ] && o="$a"; prev="$a"; done
echo "curl $url" >> "$SHIM_LOG"
case "$url" in *:22/) printf '0.000250'; exit 1;; esac
[ -n "$o" ] && echo '{"cluster_health":{"cluster_name":"cluster-tcc","health_status":"HEALTHY","number_of_nodes":3,"node_names":["a","b","c"]},"cache_health":[]}' > "$o"
printf '200 0.000300 0.003'
EOF
chmod +x "$BIN"/*

# ---------------- arvore falsa (stubs nos caminhos reais), bloco anterior (S6) e tabela de hashes dos stubs
nova_arvore() {
  local p
  for p in $(cat "$T/pids" 2>/dev/null); do kill -TERM "$p" 2>/dev/null; done; : > "$T/pids"
  rm -rf "$T/home" "$T/state"; mkdir -p "$T/state"; : > "$T/shim.log"; echo "$BOOT" > "$T/boot_id"
  G="$T/home/tcc-gate"; mkdir -p "$G/scripts" "$G/runs/gate-b29" "$G/runs/vm-s2-2026-10-27" "$G/runs/vm-s7-2026-11-04"
  cp "$S7" "$G/runs/vm-s7-2026-11-04/s7.sh"
  echo "# stub" > "$G/scripts/inject-partition.sh"; echo "# stub" > "$G/runs/gate-b29/g56-lib.sh"; echo "# stub" > "$G/runs/gate-b29/g-lib.sh"
  tr -d '\r' < "$REPO/runs/gate-b29/go.log" > "$G/runs/gate-b29/go.log"   # = HEAD (o T-0 confere que nao mudou desde o 902654b)
  cat > "$G/runs/vm-s2-2026-10-27/portao51.sh" <<'EOF'
echo "PORTAO dur=$1 csv=$2" >> "$SHIM_LOG"; : > "$2"
n=$(cat "$SHIM_STATE/portoes" 2>/dev/null || echo 0); n=$((n+1)); echo "$n" > "$SHIM_STATE/portoes"
if [ -n "${S7T_PORTAO_RUIM:-}" ] && { [ "$S7T_PORTAO_RUIM" = todos ] || [ "$S7T_PORTAO_RUIM" = "$n" ]; }; then
  sed 's/amostras_ruins=0/amostras_ruins=1/' "$S7T_PORTAO_FIX"; else cat "$S7T_PORTAO_FIX"; fi
EOF
  cat > "$G/runs/gate-b29/g5b.sh" <<'EOF'
#!/usr/bin/env bash
# STUB de g5b.sh: reencena a cronologia do fixture gerado pelo g5b.sh REAL e o copia com os instantes deslocados.
modo="${1:-}"
if [ "${2:-}" = --dry-run ]; then
  echo "G5BSTUB dry-run modo=$modo" >> "$SHIM_LOG"
  G5B_DUR="${S7T_DRY_DUR:-$S7_DUR}" G5B_T_KILL="$S7_T_KILL_S" G5B_MARGEM_B="$S7_MARGEM_S" G5B_TOL_S="${S7_TOL_S:-}" G5B_FOLGA_S="${S7_G5B_FOLGA:-}" exec bash "$S7T_G5B_REAL" "$modo" --dry-run
fi
echo "G5BSTUB modo=$modo TCC_DIR=${TCC_DIR:-} cwd=$PWD LOCK_DIR=${LOCK_DIR:-} PODMAN=${PODMAN:-} G5B_DUR=${G5B_DUR:-}" >> "$SHIM_LOG"
export SHIM_ORIGEM=g5b
LOG=runs/gate-b29; F="$S7T_FIX"; O="$LOG/g5b-$modo"; S="$SHIM_STATE"
read -rp "G-5b-$modo (split real + kill -KILL do injetor em isn3, ${S7_DUR} s, dead-man ${S7_MARGEM_S} s): go do Gestor registrado no relatorio? [digite GO] " r
echo "G5BSTUB resposta=$r" >> "$SHIM_LOG"
[ "$r" = GO ] || { echo "ABORTAR: sem go" >&2; exit 1; }
[ -z "${S7T_GO_ATRASO:-}" ] || sleep "$S7T_GO_ATRASO"
echo "go G-5b-$modo (split real + kill -KILL do injetor em isn3, ${S7_DUR} s, dead-man ${S7_MARGEM_S} s) $(date -Is) vm=172.31.251.37 sha=stub" | tee -a "$LOG/go.log"
cr() { sed -n "s/^$1=//p" "$F/cronologia.txt" | tail -1; }
D=$(( $(date +%s%N) - $(cr t_antes_armar) ))
UNIT=$(sed -n 's/^unit=\([^ ]*\).*/\1/p' "$F/unit-armada.txt"); ORIG=$(cat "$F/.tcc-origem")
desloca() { # desloca <arq>: soma D a todo numero de 19 digitos (ns) e troca o TCC_DIR do gerador pelo daqui
  local l o n; while IFS= read -r l || [ -n "$l" ]; do o=
    while [[ "$l" =~ ([0-9]{19}) ]]; do n="${BASH_REMATCH[1]}"; o+="${l%%"$n"*}$(( n + D ))"; l="${l#*"$n"}"; done
    printf '%s\n' "${o}${l}"; done < "$1" | sed "s|$ORIG|$TCC_DIR|g"; }
ate() { local a; a=$(( $(cr "$1") + D )); while [ "$(date +%s%N)" -lt "$a" ]; do sleep 0.1; done; }
lg() { date -u +'%Y-%m-%d %H:%M:%S,%3N'; }
jn() { date +'%Y-%m-%dT%H:%M:%S.%6N%:z'; }
vista() { local n; for n in isn1 isn2 isn3; do echo "$n|$(lg) INFO  [o.i.CLUSTER] $1" >> "$S/vistas"; done; }
mkdir -p "$O"; ate t_depois_armar; echo "$UNIT" > "$S/timer"
echo "$(jn) server1 systemd[1]: Started $UNIT.timer - /usr/bin/bash scripts/inject-partition.sh --heal --minority isn3." >> "$S/journal-unit"
if [ -n "${S7T_FALHA_APOS_ARMAR:-}" ]; then
  desloca "$F/unit-armada.txt" > "$O/unit-armada.txt"; grep -E '^t_(antes|depois)_armar=' "$F/cronologia.txt" > "$O/.c"; desloca "$O/.c" > "$O/cronologia.txt"; rm -f "$O/.c"
  [ -z "${S7T_TIMER_ZERA_S:-}" ] || { ( sleep "$S7T_TIMER_ZERA_S"; rm -f "$S/timer" ) >/dev/null 2>&1 & echo $! >> "$S7T_PIDS"; }
  echo "ABORTANDO (rc=1): acionando --heal em isn3; dead-man permanece armado" >&2; echo "--heal" > "$O/abort-heal.log"; exit 1
fi
pid=${SHIM_PID:-4003}
sudo nsenter -t "$pid" -n tc qdisc del dev eth0 clsact 2>/dev/null   # pre-limpeza do injetor
sudo nsenter -t "$pid" -n tc qdisc add dev eth0 clsact; sudo nsenter -t "$pid" -n tc qdisc del dev eth0 clsact   # sonda
sudo nsenter -t "$pid" -n tc qdisc add dev eth0 clsact
ate t_start; ate t_split_confirmado
for n in isn1 isn2; do echo "$n|$(lg) INFO  [o.i.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn2-19096|5] (2) [isn2-19096, isn1-47759]" >> "$S/vistas"; done
echo "isn3|$(lg) INFO  [o.i.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn3-5889|5] (1) [isn3-5889]" >> "$S/vistas"
echo "isn3|$(lg) INFO  [o.i.CLUSTER] [Context=sessions]ISPN100011: Entering availability mode DEGRADED_MODE, topology id 23" >> "$S/vistas"
[ -z "${S7T_TROCA_BOOT:-}" ] || echo 11111111-2222-3333-4444-555555555555 > "$S7T_BOOT_FILE"
ate t_kill
[ -z "${S7T_MERGE_DURANTE:-}" ] || { sleep 1; vista "ISPN000093: Received new, MERGED cluster view for channel cluster-tcc: MergeView::[isn2-19096|6] (3) [isn2-19096, isn1-47759, isn3-5889], 2 subgroups"; }
ate t_persistencia_ok; ate t_fire
# o dead-man dispara: a unit (root) chama o inject-partition.sh --heal, que faz o sudo nsenter ... tc qdisc del ... clsact
echo "$(jn) server1 systemd[1]: Started $UNIT.service - /usr/bin/bash scripts/inject-partition.sh --heal --minority isn3." >> "$S/journal-unit"
SHIM_UNIDADE="$UNIT.service" sudo nsenter -t "$pid" -n tc qdisc del dev eth0 clsact 2>/dev/null; rm -f "$S/timer"
echo "$(jn) server1 bash[9999]: [inject-partition] Heal: removendo clsact em isn3 (iface eth0)" >> "$S/journal-unit"
echo "$(jn) server1 systemd[1]: $UNIT.service: Deactivated successfully." >> "$S/journal-unit"
ate t_mergeview; vista "ISPN000093: Received new, MERGED cluster view for channel cluster-tcc: MergeView::[isn2-19096|6] (3) [isn2-19096, isn1-47759, isn3-5889], 2 subgroups"
ate t_saudavel
for f in "$F"/*; do desloca "$f" > "$O/${f##*/}"; done
sleep "${S7T_POS_S:-3}"
echo "G-5b-$modo concluido: ver $O/resultado.txt"
exit "${S7T_G5B_RC:-0}"
EOF
  mkdir -p "$G/runs/vm-s6-2026-11-04/$ANTN"; ANT="$G/runs/vm-s6-2026-11-04/$ANTN"
  { echo "uname -r: 6.8.0-147-generic"; echo "boot_id: $BOOT"
    for p in 1 2 3; do echo "isn$p ip=10.89.0.1$p id=5bf9476b5b97 StartedAt=2026-10-28 07:06:19.82 +0000 UTC"; done; } > "$ANT/00-pre.log"
  echo "boot_id fim: $BOOT 2026-11-04T05:40:00+00:00" > "$ANT/07-boot-fim.txt"; echo "S6: VALIDO (16 criterios OK)" > "$ANT/08-avaliacao.txt"
  # o S6 (G-5b-A) rodou antes: runs/gate-b29/g5b-A/resultado.txt existe (nao versionado)
  mkdir -p "$G/runs/gate-b29/g5b-A"; echo "PASS sem ISPN000524/ISPN100014/ISPN000638 nos logs" > "$G/runs/gate-b29/g5b-A/resultado.txt"
  ( cd "$G" && for f in runs/gate-b29/g5b.sh runs/gate-b29/g56-lib.sh runs/gate-b29/g-lib.sh scripts/inject-partition.sh runs/vm-s2-2026-10-27/portao51.sh; do
      sha256sum "$f" | sed 's/ \*/  /'; done ) > "$T/hashes"
  ( cd "$G" && git init -q && git config core.autocrlf false && git add scripts runs/gate-b29/*.sh runs/gate-b29/go.log runs/vm-s2-2026-10-27 runs/vm-s7-2026-11-04 \
      && git -c user.name=t -c user.email=t@t commit -qm stub )
  REF=$(git -C "$G" rev-parse HEAD)
  # como na VM depois do S6: o go.log versionado com as linhas 'go ' do S4 e do S6 a mais (Q-12)
  echo "go G-1 (REP=4, janela de 90 s em isn3) 2026-10-28T07:12:13+00:00 vm=172.31.251.37 sha=1266c129904e73469926d95708e7c83290498c41" >> "$G/runs/gate-b29/go.log"
  echo "go G-5b-A (split real + kill -KILL do injetor em isn3, 120 s, dead-man 90 s) 2026-11-04T05:30:00+00:00 vm=172.31.251.37 sha=0000000" >> "$G/runs/gate-b29/go.log"
}
ENVT=()   # VAR=valor extras do caso
# roda o s7.sh na arvore falsa; $1 = stdin (o que o "Gestor" digita); demais = VAR=valor extras
roda() {
  local entrada="$1"; shift
  SAIDA="$(printf '%s' "$entrada" | env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX="/tmp/tmux-1000/default,1,0" \
    SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S7T_PORTAO_FIX="$FIXTURE" S7T_FIX="$T/fixB" S7T_PIDS="$T/pids" \
    S7T_G5B_REAL="$REPO/runs/gate-b29/g5b.sh" S7T_BOOT_FILE="$T/boot_id" \
    S7_TESTE=1 S7_HASHES="$T/hashes" S7_BOOT_ID_FILE="$T/boot_id" S7_COMMIT_REF="$REF" S7_INJ_LOCK="$T/inject-partition-isn3.lock" \
    S7_PORTAO_MIN_S=1 S7_PORTAO_FOLGA_S=0 S7_SAUDE_S=2 S7_TC_S=1 S7_ESPERA_GO_S=30 S7_ESPERA_COLETOR_S=30 \
    S7_DUR=8 S7_T_KILL_S=4 S7_MARGEM_S=6 S7_TOL_S=5 S7_G5B_FOLGA=3 S7_D2_PRE_FIRE_S=2 S7_PERSISTE_S=2 S7_D2_FOLGA_S=1 S7_POS_FALHA_EXTRA_S=2 S7_POS_FALHA_AMOSTRA_S=1 \
    "$@" bash "$G/runs/vm-s7-2026-11-04/s7.sh" --boot-esperado "${ARG_BOOT:-$BOOT}" --bloco-anterior "${ARG_ANT:-$ANT}" 2>&1)"; RC=$?
  OUT="$(ls -d "$G/runs/vm-s7-2026-11-04"/vm-* 2>/dev/null | head -1)"
  sleep 0.3
}
# variaveis de ambiente do --avaliar (os mesmos limites curtos do roda)
AVENV=(S7_TESTE=1 S7_SAUDE_S=2 S7_TC_S=1 S7_DUR=8 S7_T_KILL_S=4 S7_MARGEM_S=6 S7_TOL_S=5 S7_G5B_FOLGA=3 S7_D2_PRE_FIRE_S=2 S7_PERSISTE_S=2 S7_D2_FOLGA_S=1)
avalia() { env "${AVENV[@]}" bash "$S7" --avaliar "$1"; }
nada_destrutivo() { # nada_destrutivo <rotulo>: nem dry-run, nem portao, nem g5b.sh
  ok "$1: g5b.sh nao chamado (nem --dry-run)" 0 "$(grep -c '^G5BSTUB' "$T/shim.log")"
  ok "$1: portao nao chamado" 0 "$(grep -c '^PORTAO' "$T/shim.log")"
}
pre_ok() { # pre_ok <rotulo>: o passo 0 passou (o portao pre ruim parou o bloco em seguida, rc 3)
  ok "$1: rc 3 (pre-condicoes OK, parou no portao pre de proposito)" 3 "$RC"
  contem "$1: pre rc=0" "pre rc=0 " "$(cat "$OUT/rc.txt" 2>/dev/null)"; }

if secao T-0; then
echo "== T-0: arquivo, modo git, CR, hashes, funcoes copiadas do s5.sh e rc na mesma linha =="
bash -n "$S7"; ok "T-0 bash -n s7.sh" 0 $?
for f in s7.sh s7.test.sh; do ok "T-0 modo git 100755 em $f" 100755 "$(git -C "$REPO" ls-files -s -- "runs/vm-s7-2026-11-04/$f" | cut -d' ' -f1)"; done
ok "T-0 nenhum CR" 0 "$(tr -cd '\r' < "$S7" | wc -c | tr -d ' ')"
H="$(bash "$S7" --hashes)"
ok "T-0 tabela embutida = arquivos do repositorio (5 OK)" 5 "$(cd "$REPO" && printf '%s\n' "$H" | sha256sum -c - 2>/dev/null | grep -c ': OK$')"
contem "T-0 g5b.sh = hash da VM no S3 (6f50e3e3...0378)" "6f50e3e36b2d93aba59e13a7765265935c83d5ccd20bc4c636531d7b1b260378  runs/gate-b29/g5b.sh" "$H"
contem "T-0 g56-lib.sh (4f750fe3...8e2a)" "4f750fe3cf59f299585c12f66b3020973976e0e979474fde6354bf6998a18e2a  runs/gate-b29/g56-lib.sh" "$H"
contem "T-0 g-lib.sh (af3436d5...22dd)" "af3436d5ede54f5f2047ba48620130a803b4bbd8929e2cbc242fbbe0ebb922dd  runs/gate-b29/g-lib.sh" "$H"
contem "T-0 inject-partition.sh (59dd2bee...94e1)" "59dd2beee5b003d85f96cae71866227d7bca1c87a9ceeab034180ce4254d94e1  scripts/inject-partition.sh" "$H"
contem "T-0 portao51.sh (2598b37d...2338)" "2598b37d4969b1bd717ddbe514110fb6a0077e1332e38c1b76c5caea61802338  runs/vm-s2-2026-10-27/portao51.sh" "$H"
ok "T-0 os 5 arquivos da tabela sem diff desde o v4c (902654b)" 0 "$(cd "$REPO" && git diff --name-only 902654b HEAD -- runs/gate-b29/g5b.sh runs/gate-b29/g56-lib.sh \
  runs/gate-b29/g-lib.sh scripts/inject-partition.sh runs/vm-s2-2026-10-27/portao51.sh | wc -l | tr -d ' ')"
ok "T-0 os 5 arquivos da tabela sem modificacao na copia de trabalho" 0 "$(cd "$REPO" && git status --porcelain -- runs/gate-b29/g5b.sh runs/gate-b29/g56-lib.sh \
  runs/gate-b29/g-lib.sh scripts/inject-partition.sh runs/vm-s2-2026-10-27/portao51.sh | wc -l | tr -d ' ')"
ok "T-0 runs/gate-b29/go.log do HEAD = o do v4c (base da arvore falsa e da regra Q-12)" 0 "$(cd "$REPO" && git diff --name-only 902654b HEAD -- runs/gate-b29/go.log | wc -l | tr -d ' ')"
# funcoes copiadas VERBATIM do s5.sh (do nome ate a ultima linha do corpo; so as mensagens que citam s5.sh/S5 podem mudar)
fn() { awk -v n="$2" 'index($0, n "() {") == 1 { p = 1; print; next } p && /^[ }]/ { print; next } p { exit }' "$1" | sed 's/s[567]\.sh/sN.sh/g; s/S[567]/SN/g'; }
for f in avaliar_portao stats tcp_s saude_loop portao; do
  a=$(fn "$S5" "$f"); b=$(fn "$S7" "$f")
  ok "T-0 $f() identica a do s5.sh ($(printf '%s\n' "$a" | wc -l | tr -d ' ') linhas)" "1 $a" "$([ -n "$b" ] && echo 1) $b"
done
corpo() { awk -v a="$2" 'index($0, a) == 1 { p = 1; next } p && /^  done$/ { exit } p' "$1"; }
a=$(corpo "$S5" '  for f in "$D/base/saude.csv" "$D/janela/saude.csv"; do'); b=$(corpo "$S7" '  for f in "$D/janela/saude.csv"; do')
ok "T-0 corpo do criterio 'intervalo do saude-log <= 2x o nominal' identico ao do s5.sh ($(printf '%s\n' "$a" | wc -l | tr -d ' ') linhas)" "1 $a" "$([ -n "$b" ] && echo 1) $b"
n=$(grep -c 'tee "$OUT/' "$S7")
ok "T-0 todo 'tee \"\$OUT/' (inclusive em funcoes, R-3) le o PIPESTATUS na mesma linha ($n linhas)" 0 "$(grep 'tee "$OUT/' "$S7" | grep -vc 'PIPESTATUS')"
ok "T-0 ha linhas 'tee \"\$OUT/' para conferir" 1 "$([ "$n" -ge 8 ] && echo 1 || echo 0)"
ok "T-0 nenhum boot_id (UUID) fixo no codigo" 0 "$(grep -cE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' "$S7")"
contem "T-0 g5b.sh B chamado sem tocar no arquivo, rc na mesma linha" 'bash runs/gate-b29/g5b.sh "$MODO" 2>&1 | ( trap '"''"' INT; exec tee "$OUT/04-g5b.log" ); RC=${PIPESTATUS[0]}' "$(cat "$S7")"
contem "T-0 modo B" "BLOCO=S7; MODO=B" "$(cat "$S7")"
ok "T-0 nenhuma escrita (sed -i/>/>>/cp) sobre os scripts da tabela" 0 \
  "$(grep -E "(sed -i|>>?|cp [^|]*) *\"?[^ ]*(g5b\.sh|g56-lib\.sh|g-lib\.sh|inject-partition\.sh|portao51\.sh)" "$S7" | grep -vcE 'sha256sum|^ *#')"
contem "T-0 marca Q-11..Q-16 no codigo" "Q-11 (pendente do Gestor)" "$(cat "$S7")"
for q in Q-12 Q-13 Q-14 Q-16; do contem "T-0 marca $q" "$q (pendente do Gestor)" "$(cat "$S7")"; done
# o que e igual no s6.sh e no s7.sh (utilitarios, coletor, espera depois de falha, console) e igual byte a byte
S6F="$REPO/runs/vm-s6-2026-11-04/s6.sh"
for f in num seg log_ns iso_ns span_s secao_no dels_sudo nlinhas_go serie_tc coletor timers_units tc_3 nic_linha estado_dm pos_falha manifesto passo_rc abortar sem_brutos par; do
  a=$(fn "$S6F" "$f"); b=$(fn "$S7" "$f")
  ok "T-0 $f() identica a do s6.sh" "1 $a" "$([ -n "$b" ] && echo 1) $b"
done
contem "T-0 aviso do S7: nao curar a mao (Q-16)" "NAO curar a mao: qualquer --heal manual invalida o bloco (§5.7)" "$(cat "$S7")"
fi

if secao T-1; then
echo "== T-1: argumentos (2.1): ausente, malformado, bloco anterior sem brutos; nada alem de leitura =="
nova_arvore
arg() { # arg <rotulo> <rc> <trecho> <args...>
  local rot="$1" rc="$2" tr="$3"; shift 3; : > "$T/shim.log"
  X=$(env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX=x SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S7_TESTE=1 bash "$G/runs/vm-s7-2026-11-04/s7.sh" "$@" 2>&1); local r=$?
  ok "T-1 $rot: rc $rc" "$rc" "$r"; contem "T-1 $rot: mensagem" "$tr" "$X"
  ok "T-1 $rot: nenhum vm-* criado" 0 "$(ls -d "$G/runs/vm-s7-2026-11-04"/vm-* 2>/dev/null | wc -l | tr -d ' ')"
  ok "T-1 $rot: nenhum sudo/podman/flock/systemctl" 0 "$(grep -cE '^(sudo|podman|flock|systemctl|journalctl|tc) ' "$T/shim.log")"; }
arg "sem argumentos" 2 "--boot-esperado ausente"
arg "--boot-esperado sem valor" 2 "falta o valor de --boot-esperado" --boot-esperado
arg "UUID curto" 2 "fora do formato UUID" --boot-esperado 5d7119b8-3b50-435d-bd0d --bloco-anterior "$ANT"
arg "UUID com maiusculas" 2 "fora do formato UUID" --boot-esperado 5D7119B8-3B50-435D-BD0D-CBDB6BD410DF --bloco-anterior "$ANT"
arg "UUID com lixo no fim" 2 "fora do formato UUID" --boot-esperado "${BOOT}x" --bloco-anterior "$ANT"
arg "sem --bloco-anterior" 2 "--bloco-anterior ausente" --boot-esperado "$BOOT"
arg "argumento desconhecido" 2 "argumento desconhecido: --rep" --boot-esperado "$BOOT" --rep 4
arg "bloco anterior inexistente" 1 "bloco anterior sem brutos" --boot-esperado "$BOOT" --bloco-anterior "$T/nao-existe"
rm "$ANT/07-boot-fim.txt"
arg "bloco anterior sem 07-boot-fim.txt" 1 "bloco anterior sem brutos: sem $ANT/07-boot-fim.txt" --boot-esperado "$BOOT" --bloco-anterior "$ANT"
echo "boot_id: $BOOT" > "$ANT/07-boot-fim.txt"
arg "07-boot-fim.txt sem 'boot_id fim:'" 1 "sem a linha 'boot_id fim: <uuid>'" --boot-esperado "$BOOT" --bloco-anterior "$ANT"
echo "boot_id fim: $BOOT x" > "$ANT/07-boot-fim.txt"; sed -i '/^isn2 /d' "$ANT/00-pre.log"
arg "00-pre.log sem id/StartedAt de isn2" 1 "sem 'id=' e 'StartedAt=' de isn2" --boot-esperado "$BOOT" --bloco-anterior "$ANT"
sed -i '/^uname -r/d' "$ANT/00-pre.log"
arg "00-pre.log sem uname -r" 1 "sem 'uname -r:'" --boot-esperado "$BOOT" --bloco-anterior "$ANT"
fi

if secao T-2 || secao T-7 || secao T-11; then
echo "== T-2: caminho completo (TCC_DIR nao definido; go.log com a linha do S4; Gestor digita GO) =="
TI=$(date +%s); nova_arvore; roda $'GO\n'; TD=$(( $(date +%s) - TI ))
echo "   (duracao do caso: ${TD} s)"
ok "T-2 rc 0" 0 "$RC"
[ "$RC" = 0 ] || printf '%s\n' "$SAIDA" | tail -40
contem "T-2 TCC_DIR exportado pelo s7.sh" "TCC_DIR=$T/home/tcc-gate (exportado pelo s7.sh)" "$SAIDA"
ok "T-2 saida em runs/vm-s7-2026-11-04/vm-*" 1 "$([ -n "$OUT" ] && [ -d "$OUT" ] && echo 1 || echo 0)"
for f in 00-pre.log parametros.txt 01-g5b-dry-run.log 02-portao51-pre.log 02-portao51-pre.avaliacao 03-coletor.log 04-g5b.log \
         janela/saude.csv janela/tc-isn3.csv g5b-B-copia/resultado.txt g5b-B-copia/cronologia.txt g5b-B.sha256 go.log 05-journal-unit.txt \
         05-sudo-journal.txt 05-pos-g5b.log 05-vistas.log 06-portao51-pos.log 06-portao51-pos.avaliacao 07-boot-fim.txt 08-avaliacao.txt \
         MANIFEST.sha256 s7-console.log rc.txt; do existe "T-2 gravado $f" "$OUT/$f"; done
ok "T-2 02-portao51-pre.csv e 06-portao51-pos.csv gravados" 2 "$(ls "$OUT"/0[26]-portao51-p*.csv 2>/dev/null | wc -l | tr -d ' ')"
PRE="$(cat "$OUT/00-pre.log")"
contem "T-2 hashes conferidos" "hashes: iguais a referencia" "$PRE"
contem "T-2 boot_id registrado no inicio" "boot_id: $BOOT" "$PRE"
contem "T-2 boot_id esperado registrado" "boot_id esperado (--boot-esperado): $BOOT" "$PRE"
contem "T-2 boot_id do bloco anterior registrado" "boot_id fim do bloco anterior: $BOOT" "$PRE"
contem "T-2 kernel do bloco anterior registrado" "uname -r do bloco anterior: 6.8.0-147-generic" "$PRE"
contem "T-2 lock ocupado" "OCUPADO (este flock)" "$PRE"
contem "T-2 lock do injetor livre" "lock do injetor $T/inject-partition-isn3.lock: livre" "$PRE"
contem "T-2 regra do go.log (Q-12): 2 linhas 'go ' a mais (S4 e S6) aceitas" "HEAD e prefixo exato; 2 linha(s) 'go ' acrescentada(s)" "$PRE"
contem "T-2 id e StartedAt de isn3" "isn3 ip=10.89.0.13 id=5bf9476b5b97 StartedAt=2026-10-28 07:06:19.82 +0000 UTC" "$PRE"
contem "T-2 guarda de netns" "isn3 netns: diferente do host" "$PRE"
contem "T-2 JGroups registrado (ipv4/ipv6)" "isn3 JGroups 7800: ipv4=2 ipv6=0" "$PRE"
contem "T-2 NIC sem prio/netem/clsact" "nic eth0 proibidos(prio|netem|clsact)=0" "$PRE"
contem "T-2 g5b-B inexistente" "runs/gate-b29/g5b-B: inexistente" "$PRE"
contem "T-2 g5b-A/resultado.txt do S6 conferido" "runs/gate-b29/g5b-A/resultado.txt: existe (o S6 rodou antes)" "$PRE"
contem "T-2 bloco anterior registrado" "bloco anterior: S6: VALIDO" "$PRE"
contem "T-2 boot_id do fim" "boot_id fim: $BOOT" "$(cat "$OUT/07-boot-fim.txt")"
ok "T-2 rc.txt na ordem (pre, dry-run, portao pre, g5b, coletor, pos, portao pos, boot-fim, avaliacao)" \
  "pre g5b-dry-run 02-portao51-pre 02-portao51-pre-avaliacao g5b coletor pos-g5b 06-portao51-pos 06-portao51-pos-avaliacao boot-fim avaliacao" \
  "$(grep -oE '^[a-z0-9-]+ rc=' "$OUT/rc.txt" | sed 's/ rc=//' | paste -sd' ')"
ok "T-2 todos os passos com rc=0" 0 "$(grep -E '^[a-z0-9-]+ rc=' "$OUT/rc.txt" | grep -vc ' rc=0 ')"
contem "T-2 parametros efetivos do dry-run conferidos" "parametros efetivos do g5b.sh B conferidos: duracao=8s, KILL em +4s, margem do dead-man=6s, tolerancia do disparo=5s" "$(cat "$OUT/rc.txt")"
contem "T-2 dry-run real do g5b.sh gravado" "G-5b-B (dry-run; nada executado): duracao=8s, KILL em +4s do start" "$(cat "$OUT/01-g5b-dry-run.log")"
L="$T/shim.log"
antes "T-2 dry-run antes do portao pre" "$L" "G5BSTUB dry-run" "PORTAO dur="
antes "T-2 portao pre antes do g5b.sh" "$L" "PORTAO dur=" "G5BSTUB modo=B"
ok "T-2 portao duas vezes (pre e pos)" 2 "$(grep -c '^PORTAO' "$L")"
contem "T-2 g5b.sh com TCC_DIR exportado, de ~/tcc-gate, sem LOCK_DIR/PODMAN/G5B_DUR herdados" "G5BSTUB modo=B TCC_DIR=$T/home/tcc-gate cwd=$T/home/tcc-gate LOCK_DIR= PODMAN= G5B_DUR=" "$(cat "$L")"
contem "T-2 o GO que chegou ao g5b.sh e o digitado (stdin)" "G5BSTUB resposta=GO" "$(cat "$L")"
contem "T-2 aviso do S7 (isn3 cortado ate ~prazo; nao curar a mao) antes do GO" ">>> AVISO: o no isn3 fica cortado ate ~14 s depois de armar (8 + 6 s, AccuracySec=1s); NAO curar a mao: qualquer --heal manual invalida o bloco" "$SAIDA"
contem "T-2 aviso do S7: prazo + tolerancia + folga (no real 145 + 10 + 30 = 185 s)" "Se o dead-man nao disparar ate 14 + 5 + 3 = 22 s, o g5b.sh falha e o trap dele cura" "$SAIDA"
contem "T-2 instrucao de cura manual" "PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3" "$SAIDA"
contem "T-2 aviso de 15 min para o GO" "em ate 0 min" "$SAIDA"
antes "T-2 aviso antes da linha de GO do g5b.sh" "$OUT/s7-console.log" ">>> AVISO" "go G-5b-B ("
CL="$(cat "$OUT/03-coletor.log")"
contem "T-2 coletor esperou a linha nova de GO" "esperando a linha nova 'go G-5b-B'" "$CL"; contem "T-2 coletor viu o GO" "GO visto" "$CL"; contem "T-2 coletor terminou" "coletor fim" "$CL"
ok "T-2 serie tc de isn3 com amostras clsact=1 e clsact=0" "1 1" "$(grep -q '^[0-9]*,1,' "$OUT/janela/tc-isn3.csv" && echo 1) $(grep -q '^[0-9]*,0,' "$OUT/janela/tc-isn3.csv" && echo 1)"
contem "T-2 copia do g5b-B com sha256" "g5b-B-copia/resultado.txt" "$(cat "$OUT/g5b-B.sha256")"
ok "T-2 g5b-B.sha256 confere" 0 "$(cd "$OUT" && sha256sum -c g5b-B.sha256 2>/dev/null | grep -vc ': OK$')"
contem "T-2 go.log copiado com o GO do S7" "go G-5b-B (" "$(cat "$OUT/go.log")"
contem "T-2 journal da unit pedido com -u .service e -u .timer em short-iso-precise" "journalctl -u tcc-heal-" "$(cat "$L")"
contem "T-2 journal do sudo desde o passo 3, -o json" "_COMM=sudo --since @" "$(grep journalctl "$L")"
contem "T-2 05-pos-g5b: 0 timers e 0 units" "timers tcc-heal: 0 units tcc-heal: 0" "$(cat "$OUT/05-pos-g5b.log")"
contem "T-2 05-pos-g5b: tc dos 3 netns" "isn2 pid=4003 tc qdisc show dev eth0:" "$(cat "$OUT/05-pos-g5b.log")"
contem "T-2 05-vistas com MergeView" "MergeView::" "$(cat "$OUT/05-vistas.log")"
MF="$(cd "$OUT" && sha256sum -c MANIFEST.sha256 2>&1)"
ok "T-2 MANIFEST.sha256 confere (sha256sum -c)" 0 "$(printf '%s\n' "$MF" | grep -vc ': OK$')"
ok "T-2 MANIFEST cobre todos os arquivos de vm-<UTC>/ menos ele mesmo" "$(( $(cd "$OUT" && find . -type f | wc -l) - 1 ))" "$(grep -c . "$OUT/MANIFEST.sha256")"
contem "T-2 MANIFEST inclui o console" "s7-console.log" "$(cat "$OUT/MANIFEST.sha256")"
AV="$(cat "$OUT/08-avaliacao.txt")"
contem "T-2 avaliacao: VALIDO" "S7: VALIDO" "$AV"; nao "T-2 avaliacao sem FALHA" "FALHA" "$AV"
for c in B1/A1 B1/A2 B1/A3 B1/A4 B1/A5 B1/A6 B1/A11 B2 B3 B4 B5 B6 B7 D-1 D-2 D-3; do contem "T-2 avaliacao: $c OK" "OK: $c " "$AV"; done
contem "T-2 avaliacao: A12 intervalo do saude-log OK" "OK: intervalo maximo do saude-log em janela/saude.csv" "$AV"
contem "T-2 avaliacao: D-1 sem cura da sessao" "OK: D-1 detector do sudo: 0 tc qdisc del ... clsact da sessao desde t_kill" "$AV"
contem "T-2 avaliacao: D-1 dels da unit como leitura humana" "LEITURA HUMANA: D-1 1 tc qdisc del ... clsact da unit do dead-man desde t_kill" "$AV"
contem "T-2 avaliacao: inicio da unit no journal x t_fire" "LEITURA HUMANA: inicio da unit no journal" "$AV"
contem "T-2 avaliacao: limite do AccuracySec declarado" "o S7 prova o CORPO do dead-man de producao" "$AV"
ok "T-2 nenhum systemctl stop (o dead-man nunca e desarmado)" 0 "$(grep -c "systemctl stop" "$T/shim.log")"
contem "T-2 avaliacao: t_mergeview - t_fire como RESULTADO rotulado" "dado de seguranca, NAO e t_merge" "$AV"
contem "T-2 avaliacao: Q-14 como RESULTADO" "RESULTADO: sem ISPN000524/ISPN100014/ISPN000638" "$AV"
contem "T-2 avaliacao: leitura humana declarada" "LEITURA HUMANA:" "$AV"
nao "T-2 nenhuma chamada inesperada" "inesperado" "$SAIDA"
nao "T-2 sem variavel nao definida" "unbound variable" "$SAIDA"
rm -rf "$T/bom"; cp -r "$OUT" "$T/bom"   # base de T-7 e T-11
[ -z "${S7T_GUARDA:-}" ] || { rm -rf "$S7T_GUARDA"; cp -r "$OUT" "$S7T_GUARDA"; }   # depuracao: guarda a execucao boa
fi

if secao T-3; then
echo "== T-3: pre-condicoes que falham abortam antes do portao e do g5b.sh =="
precond() { # precond <rotulo> <trecho-esperado> VAR=valor...
  local rot="$1" tr="$2"; shift 2; nova_arvore; prep_extra; roda $'GO\n' "$@"
  ok "T-3 $rot: rc 1" 1 "$RC"; contem "T-3 $rot: motivo" "$tr" "$(cat "$OUT/00-pre.log" 2>/dev/null)"
  contem "T-3 $rot: abortado" "S7 ABORTADO (rc=1): pre-condicoes" "$SAIDA"; nada_destrutivo "T-3 $rot"; prep_extra() { :; }; }
prep_extra() { :; }
precond "fora do tmux" "fora do tmux" TMUX=
precond "lock livre" "LIVRE" SHIM_LOCK=livre
precond "lock do injetor ocupado" "lock do injetor $T/inject-partition-isn3.lock OCUPADO" SHIM_INJ_LOCK=ocupado
precond "sudo -n falha" "sudo -n true falhou" SHIM_SUDO_FALHA=1
precond "timer tcc-heal armado" "1 timers tcc-heal armados" SHIM_TIMERS=1
precond "unit tcc-heal carregada" "1 units tcc-heal carregadas" SHIM_UNITS=1
for v in LOCK_DIR=/tmp/x PODMAN=podman INJ=x LOG=x DMS=x UNIT=x G_TESTE=1 G5B_DUR=8 G1_DUR=1 REP=4 COLETA=1 SONDA_SENHA=x L0C_PRAZO=1 TCC_GATE=x; do
  precond "${v%%=*} herdada" "variaveis herdadas que mudariam o g5b.sh/injetor: ${v%%=*}" "$v"; done
precond "isn3 no netns do host" "netns de isn3 igual ao do host" SHIM_NETNS_HOST=1
precond "PID de isn3 invalido" "PID de isn3 invalido" SHIM_PID=0
precond "NIC da VM com prio" "NIC da VM (eth0) com prio/netem/clsact" SHIM_NIC_PRIO=1
precond "HEAD sem o commit de referencia" "HEAD nao contem" S7_COMMIT_REF=0123456789abcdef0123456789abcdef01234567
echo 11111111-2222-3333-4444-555555555555 > "$T/boot_outro"
precond "boot_id atual diferente do esperado (Q-11)" "boot_id atual 11111111-2222-3333-4444-555555555555 diferente de --boot-esperado" S7_BOOT_ID_FILE="$T/boot_outro"
prep_extra() { echo "boot_id fim: 99999999-2222-3333-4444-555555555555 2026-11-04" > "$ANT/07-boot-fim.txt"; }
precond "esperado diferente do boot_id fim do bloco anterior (Q-11)" "--boot-esperado $BOOT diferente do boot_id fim do bloco anterior (99999999-2222-3333-4444-555555555555)"
prep_extra() { echo 99999999-2222-3333-4444-555555555555 > "$T/boot_id"; echo "boot_id fim: 99999999-2222-3333-4444-555555555555 x" > "$ANT/07-boot-fim.txt"; }
precond "atual = bloco anterior, mas o esperado e outro (Q-11)" "boot_id atual 99999999-2222-3333-4444-555555555555 diferente de --boot-esperado $BOOT"
precond "kernel diferente do bloco anterior" "kernel 6.8.0-150-generic diferente do bloco anterior (6.8.0-147-generic)" SHIM_KERNEL=6.8.0-150-generic
precond "id de isn2 diferente do bloco anterior (D-4)" "id de isn2 (aaaaaaaaaaaa) diferente do bloco anterior (5bf9476b5b97): conteiner recriado" SHIM_ID_isn2=aaaaaaaaaaaabbbb
precond "StartedAt de isn2 diferente do bloco anterior (D-4)" "StartedAt de isn2 (2026-11-04 01:00:00.1 +0000 UTC) diferente do bloco anterior" "SHIM_ST_isn2=2026-11-04 01:00:00.1 +0000 UTC"
prep_extra() { mkdir -p "$G/runs/gate-b29/g5b-B"; echo x > "$G/runs/gate-b29/g5b-B/resultado.txt"; }
precond "g5b-B de uma execucao anterior" "runs/gate-b29/g5b-B ja existe"
prep_extra() { rm -rf "$G/runs/gate-b29/g5b-A"; }
precond "S6 nao rodou (g5b-A/resultado.txt ausente)" "runs/gate-b29/g5b-A/resultado.txt ausente: o S6 (G-5b-A) tem de rodar antes do S7"
prep_extra() { echo "# alterado" >> "$G/runs/gate-b29/g56-lib.sh"; ( cd "$G" && git -c user.name=t -c user.email=t@t commit -qm alt -- runs/gate-b29/g56-lib.sh ); }
precond "hash do g56-lib.sh diferente" "hash diferente da referencia"
# regra do go.log (Q-12), 5 casos
prep_extra() { sed -i 's/^go 2026-10-16T05:11:03+00:00/go 2026-10-16T05:11:04+00:00/' "$G/runs/gate-b29/go.log"; }
precond "go.log com linha antiga editada (Q-12)" "o conteudo do HEAD nao e prefixo exato do arquivo (linha antiga alterada)"
prep_extra() { echo "GO G-5b-B sem o prefixo" >> "$G/runs/gate-b29/go.log"; }
precond "go.log com linha a mais sem 'go ' (Q-12)" "linha acrescentada que nao comeca com 'go '"
prep_extra() { echo "# sujo" >> "$G/runs/gate-b29/g-lib.sh"; }
precond "outro arquivo versionado modificado (Q-12)" "1 arquivo(s) versionado(s) modificado(s) alem de runs/gate-b29/go.log"
prep_extra() { ( cd "$G" && git add runs/gate-b29/go.log ); }
precond "go.log no indice (M , nao ' M')" "alem de runs/gate-b29/go.log (ou runs/gate-b29/go.log em estado diferente de ' M')"
nova_arvore; git -C "$G" checkout -q -- runs/gate-b29/go.log; roda $'GO\n' S7T_PORTAO_RUIM=1
pre_ok "T-3 arvore limpa (Q-12)"; contem "T-3 arvore limpa: 0 modificados" "arquivos versionados modificados: 0" "$(cat "$OUT/00-pre.log")"
nova_arvore; echo "go G-5b-B (x) 2026-11-04T06:00:00+00:00 vm=x sha=y" >> "$G/runs/gate-b29/go.log"; roda $'GO\n' S7T_PORTAO_RUIM=1
pre_ok "T-3 go.log com 3 linhas 'go ' a mais (Q-12)"; contem "T-3 3 linhas aceitas" "3 linha(s) 'go ' acrescentada(s) (aceito, Q-12)" "$(cat "$OUT/00-pre.log")"
contem "T-3 GO_N0 conta as linhas 'go G-5b-B' ja existentes" "GO_N0=1" "$(cat "$OUT/parametros.txt")"
nova_arvore; roda $'GO\n' S7T_DRY_DUR=100
ok "T-3 dry-run com parametros efetivos diferentes do plano: rc 1" 1 "$RC"
contem "T-3 dry-run diferente: motivo" "sem 'duracao=8s' (parametros efetivos diferentes do plano)" "$SAIDA"
ok "T-3 dry-run diferente: portao nao chamado" 0 "$(grep -c '^PORTAO' "$T/shim.log")"; ok "T-3 dry-run diferente: g5b.sh nao chamado" 0 "$(grep -c '^G5BSTUB modo=' "$T/shim.log")"
nova_arvore; roda $'GO\n' TCC_DIR="$T/home/tcc-desenvolvimento"
ok "T-3 TCC_DIR diferente: rc 1" 1 "$RC"; contem "T-3 TCC_DIR diferente: mensagem" "precisa ser $T/home/tcc-gate" "$SAIDA"; nada_destrutivo "T-3 TCC_DIR"
nova_arvore; roda $'GO\n' TCC_DIR="$T/home/tcc-gate" S7T_PORTAO_RUIM=1
pre_ok "T-3 TCC_DIR igual a ~/tcc-gate"; contem "T-3 ja exportado" "(ja exportado)" "$SAIDA"
nova_arvore; ARG_ANT="runs/vm-s6-2026-11-04/$ANTN"; SAIDA="$(cd "$G" && printf 'GO\n' | env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX=x SHIM_LOG="$T/shim.log" \
  SHIM_STATE="$T/state" S7T_PORTAO_FIX="$FIXTURE" S7T_G5B_REAL="$REPO/runs/gate-b29/g5b.sh" S7_TESTE=1 S7_HASHES="$T/hashes" S7_BOOT_ID_FILE="$T/boot_id" \
  S7_COMMIT_REF="$REF" S7_INJ_LOCK="$T/inject-partition-isn3.lock" S7_PORTAO_MIN_S=1 S7_DUR=8 S7_T_KILL_S=4 S7_MARGEM_S=6 S7_TOL_S=5 S7_G5B_FOLGA=3 S7_D2_PRE_FIRE_S=2 S7T_PORTAO_RUIM=1 \
  bash runs/vm-s7-2026-11-04/s7.sh --boot-esperado "$BOOT" --bloco-anterior "$ARG_ANT" 2>&1)"; RC=$?; unset ARG_ANT
OUT="$(ls -d "$G/runs/vm-s7-2026-11-04"/vm-* 2>/dev/null | head -1)"
pre_ok "T-3 --bloco-anterior relativo a ~/tcc-gate"
fi

if secao T-4; then
echo "== T-4: portao pre ruim PARA (sem g5b.sh) =="
nova_arvore; roda $'GO\n' S7T_PORTAO_RUIM=1
ok "T-4 rc 3" 3 "$RC"; contem "T-4 mensagem" "portao 5.1 pre falhou: S7 NAO roda" "$SAIDA"
contem "T-4 avaliacao do portao gravada" "FALHA portao: amostras_ruins=1" "$(cat "$OUT/02-portao51-pre.avaliacao")"
ok "T-4 g5b.sh nao chamado" 0 "$(grep -c '^G5BSTUB modo=' "$T/shim.log")"; ok "T-4 sem coletor" 0 "$([ -e "$OUT/03-coletor.log" ] && echo 1 || echo 0)"
fi

if secao T-5; then
echo "== T-5: Gestor nao digita GO: g5b.sh sai antes de armar; coletor encerrado sem coletar; sem portao pos =="
nova_arvore; roda $'nao\n'
ok "T-5 rc 4" 4 "$RC"; contem "T-5 resposta que chegou ao g5b.sh" "G5BSTUB resposta=nao" "$(cat "$T/shim.log")"
contem "T-5 rc do g5b.sh lido na mesma linha" "g5b rc=1 " "$(cat "$OUT/rc.txt")"
contem "T-5 mensagem" "sem GO (saiu antes de armar" "$SAIDA"
contem "T-5 coletor saiu sem coletar" "nada coletado" "$(cat "$OUT/03-coletor.log")"
contem "T-5 coletor rc 2" "coletor rc=2 " "$(cat "$OUT/rc.txt")"
ok "T-5 sem saude.csv nem tc-isn3.csv" 0 "$(ls "$OUT/janela"/*.csv 2>/dev/null | wc -l | tr -d ' ')"
ok "T-5 portao so o pre" 1 "$(grep -c '^PORTAO' "$T/shim.log")"
ok "T-5 sem 05-pos-falha.log (nada armado)" 0 "$([ -e "$OUT/05-pos-falha.log" ] && echo 1 || echo 0)"
ok "T-5 sem GO novo no go.log" 0 "$(grep -c '^go G-5b-B' "$G/runs/gate-b29/go.log")"
echo "== T-5b: GO depois do prazo do coletor (ESPERA_GO_S): o coletor desiste e nao coleta (R-5) =="
nova_arvore; roda $'GO\n' S7_ESPERA_GO_S=2 S7T_GO_ATRASO=5
contem "T-5b coletor desistiu no prazo" "sem GO em 2 s: nada coletado" "$(cat "$OUT/03-coletor.log")"
ok "T-5b sem saude.csv nem tc-isn3.csv" 0 "$(ls "$OUT/janela"/*.csv 2>/dev/null | wc -l | tr -d ' ')"
ok "T-5b g5b.sh rodou (rc 0) e a avaliacao recusa (rc 6)" "0 6" "$(grep -o '^g5b rc=[0-9]*' "$OUT/rc.txt" | cut -d= -f2) $RC"
contem "T-5b avaliacao: saude-log ausente e FALHA" "FALHA: intervalo maximo do saude-log em janela/saude.csv" "$(cat "$OUT/08-avaliacao.txt")"
fi

if secao T-6; then
echo "== T-6: g5b.sh falha depois de armar: 05-pos-falha.log e espera ate 0 timers (D-5), rc 4 =="
nova_arvore; TI=$(date +%s); roda $'GO\n' S7T_FALHA_APOS_ARMAR=1 S7T_TIMER_ZERA_S=12; TD=$(( $(date +%s) - TI ))
ok "T-6 rc 4" 4 "$RC"; PF="$(cat "$OUT/05-pos-falha.log" 2>/dev/null)"
contem "T-6 rc do g5b.sh" "g5b rc=1 " "$(cat "$OUT/rc.txt")"
contem "T-6 estado gravado: timers" "timers tcc-heal: 1 units tcc-heal: 0" "$PF"
contem "T-6 estado gravado: tc dos 3 netns" "isn3 pid=4003 tc qdisc show dev eth0:" "$PF"
contem "T-6 instrucao de cura manual" "Cura manual (em ~/tcc-gate): PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3" "$PF"
contem "T-6 esperou o dead-man" "esperando 0 timers tcc-heal ate" "$PF"
contem "T-6 amostras com clsact de isn3" "timers_tcc_heal=1 clsact_isn3=" "$PF"
contem "T-6 parou em 0 timers" "timers_tcc_heal=0" "$PF"
contem "T-6 registrou de quem veio a cura" "cura: o trap do g5b.sh chamou o --heal" "$PF"
ok "T-6 esperou de fato (>= 12 s; levou ${TD} s)" 1 "$([ "$TD" -ge 12 ] && echo 1 || echo 0)"
contem "T-6 rc do passo pos-falha" "pos-falha rc=0 " "$(cat "$OUT/rc.txt")"
ok "T-6 portao so o pre" 1 "$(grep -c '^PORTAO' "$T/shim.log")"
existe "T-6 copia do g5b-B mesmo na falha" "$OUT/g5b-B-copia/unit-armada.txt"
nova_arvore; roda $'GO\n' S7T_FALHA_APOS_ARMAR=1 S7_DUR=1 S7_MARGEM_S=1 S7_POS_FALHA_EXTRA_S=2
ok "T-6b timer nunca zera: rc 4" 4 "$RC"; PF="$(cat "$OUT/05-pos-falha.log" 2>/dev/null)"
contem "T-6b prazo esgotado registrado" "prazo esgotado com 1 timer(s) tcc-heal: use a cura manual" "$PF"
fi

if secao T-7; then
echo "== T-7: avaliacao (--avaliar) sobre copias mutadas da execucao boa: cada criterio isolado =="
B="$T/bom"
av() { # av <rotulo> <trecho-esperado> <comando-de-mutacao (roda em $M)>
  rm -rf "$T/m"; cp -r "$B" "$T/m"; M="$T/m"; ( cd "$M" && eval "$3" ); X=$(avalia "$M"); local r=$?
  ok "T-7 $1: rc 1" 1 "$r"; contem "T-7 $1: motivo" "$2" "$X"; contem "T-7 $1: INVALIDO" "S7: INVALIDO" "$X"; }
avok() { rm -rf "$T/m"; cp -r "$B" "$T/m"; M="$T/m"; ( cd "$M" && eval "$3" ); X=$(avalia "$M"); local r=$?
  ok "T-7 $1: rc 0" 0 "$r"; contem "T-7 $1: $2" "$2" "$X"; contem "T-7 $1: VALIDO" "S7: VALIDO" "$X"; }
X=$(avalia "$B"); ok "T-7 copia boa: VALIDO" 0 $?
# utilitarios das mutacoes (rodam dentro de $M)
CR='C=g5b-B-copia/cronologia.txt; cr() { sed -n "s/^$1=//p" $C; }; poe() { sed -i "s/^$1=.*/$1=$2/" $C; }'
# utilitarios das mutacoes do S7 (rodam dentro de $M, num subshell do av/avok)
arma() { # arma <ns de t_depois_armar ate t_fire>: muda so a armacao e o tempos.txt (como o g5b.sh calcula)
  local C=g5b-B-copia/cronologia.txt tf a1 a0; tf=$(sed -n 's/^t_fire=//p' $C); a1=$(( tf - $1 )); a0=$(( a1 - 116000000 ))
  sed -i "s/^t_antes_armar=.*/t_antes_armar=$a0/; s/^t_depois_armar=.*/t_depois_armar=$a1/" $C
  echo "delta_desde_antes_de_armar_ms=$(( (tf - a0) / 1000000 )) delta_desde_depois_de_armar_ms=$(( (tf - a1) / 1000000 )) prazo_s=14 tolerancia_s=5" > g5b-B-copia/tempos.txt; }
jsudo() { # jsudo <t_us> <MESSAGE (depois de 'USER=root ; ')> <_SYSTEMD_UNIT>: acrescenta uma entrada ao journal do sudo (-o json)
  printf '{"__REALTIME_TIMESTAMP":"%s","MESSAGE":"  server1 : TTY=pts/2 ; PWD=/x ; USER=root ; %s","_SYSTEMD_UNIT":"%s","_COMM":"sudo"}\n' "$1" "$2" "$3" >> 05-sudo-journal.txt; }
formato_real() { # formato_real [descricao-do-timer]: reescreve a unit no formato real do systemd 255 (como em runs/vm-s3-2026-10-28/vm/l0c/unit-armada.txt)
  local f=g5b-B-copia/unit-armada.txt w u dt; w=$(sed -n 's/^WorkingDirectory=//p' $f); u=$(sed -n 's/^unit=\([^ ]*\).*/\1/p' $f)
  dt="${1:-/usr/bin/bash scripts/inject-partition.sh --heal --minority isn3}"
  awk -v w="$w" -v u="$u" -v dt="$dt" '
    /^--- systemctl cat .*\.service/ { print; s = 1
      print "# /run/systemd/transient/" u ".service"; print "# This is a transient unit file, created programmatically via the systemd API. Do not edit."
      print "[Unit]"; print "Description=/usr/bin/bash scripts/inject-partition.sh --heal --minority isn3"; print ""
      print "[Service]"; print "WorkingDirectory=" w; print "Environment=\"PODMAN=sudo podman\""; print "ExecStart="
      print "ExecStart=\"/usr/bin/bash\" \"scripts/inject-partition.sh\" \"--heal\" \"--minority\" \"isn3\""; next }
    /^--- systemctl cat .*\.timer/ { print; s = 1
      print "# /run/systemd/transient/" u ".timer"; print "# This is a transient unit file, created programmatically via the systemd API. Do not edit."
      print "[Unit]"; print "Description=" dt; print ""; print "[Timer]"; print "OnActiveSec=14s"; print "AccuracySec=1s"; print "RemainAfterElapse=no"; next }
    /^--- / { s = 0 } !s' $f > $f.n && mv $f.n $f; }
av "B1/A1 boot_id do fim diferente" "FALHA: B1/A1 boot_id" "sed -i 's/^boot_id fim: [^ ]*/boot_id fim: 11111111-2222-3333-4444-555555555555/' 07-boot-fim.txt"
av "B1/A1 boot_id do inicio diferente" "FALHA: B1/A1 boot_id" "sed -i 's/^boot_id: .*/boot_id: 11111111-2222-3333-4444-555555555555/' 00-pre.log"
av "B1/A2 g5b rc != 0 no rc.txt" "FALHA: B1/A2 g5b.sh sem rc=0" "sed -i 's/^g5b rc=0 /g5b rc=1 /' rc.txt"
av "B1/A2 resultado.txt com FAIL" "FALHA: B1/A2 resultado.txt" "echo 'FAIL qualquer' >> g5b-B-copia/resultado.txt"
av "B1/A2 resultado.txt com ABORTADO" "FALHA: B1/A2 resultado.txt" "echo 'ABORTADO rc=1 x' >> g5b-B-copia/resultado.txt"
av "B1/A3 split confirmado depois do KILL (M1)" "FALHA: B1/A3 split nao confirmado antes do KILL" "$CR; poe t_split_confirmado \$(( \$(cr t_kill) + 1 ))"
av "B1/A4 KILL 1 ms antes de +4 s" "FALHA: B1/A4 KILL antes de +4 s" "$CR; poe t_kill \$(( \$(cr t_start) + 3999000000 ))"
av "B1/A5 rc_injetor=143 (M2)" "FALHA: B1/A5 rc_injetor=143" "sed -i 's/^rc_injetor=137/rc_injetor=143/' g5b-B-copia/estado.txt"
av "B1/A5 marcador end presente" "FALHA: B1/A5" "echo 'fault=F2 node=isn3 event=end t_epoch_ns=1' >> g5b-B-copia/marcadores.txt"
av "B1/A6 sem clsact antes do KILL" "FALHA: B1/A6 estado.txt sem clsact antes do KILL" "sed -i '/clsact/d' g5b-B-copia/estado.txt"
av "B1/A6 sem clsact depois do KILL" "FALHA: B1/A6 qdisc-apos-kill.txt sem clsact" "sed -i '/clsact/d' g5b-B-copia/qdisc-apos-kill.txt"
av "B1/A6 persistencia de 2 s menos 1 ms" "FALHA: B1/A6 t_persistencia_ok - t_kill" "$CR; poe t_persistencia_ok \$(( \$(cr t_kill) + 1999000000 ))"
av "B1/A6 persistencia.txt vazio (MS-4)" "FALHA: B1/A6 persistencia.txt: 0 amostra(s)" ": > g5b-B-copia/persistencia.txt"
av "B1/A6 MergeView entre o KILL e a persistencia" "FALHA: B1/A6 05-vistas.log" \
   "$CR; t=\$(( (\$(cr t_kill) + 1000000000) / 1000000000 )); echo \"\$(date -u -d @\$t +'%Y-%m-%d %H:%M:%S'),000 INFO  [o.i.CLUSTER] ISPN000093: MergeView::[isn2|6] (3) [a, b, c]\" >> 05-vistas.log"
av "B1/A11 portao pos FALHA" "FALHA: B1/A11 portao 5.1 pos" "echo 'FALHA portao: amostras_ruins=1' > 06-portao51-pos.avaliacao"
av "B1/A12 intervalo do saude-log > 2x o nominal" "FALHA: intervalo maximo do saude-log" \
   "awk -F, 'BEGIN{OFS=\",\"} NR>1 && \$2==\"11223\" && ++k==2 {\$1=\$1+5000000000} {print}' janela/saude.csv > x && mv x janela/saude.csv"
av "B2 um --heal manual em chamadas-injetor" "FALHA: B2 chamadas-injetor: 2 linha(s), 1 injecao(oes), 1 --heal" "echo '2026-11-04T06:00:00+00:00 --heal --minority isn3' >> g5b-B-copia/chamadas-injetor.txt"
av "B2 resultado.txt sem 'PASS nenhum --heal manual'" "FALHA: B2" "sed -i '/^PASS nenhum --heal manual/d' g5b-B-copia/resultado.txt"
# B3: muda so a armacao (t_antes/t_depois_armar e tempos.txt), para nao mexer em nenhum outro criterio
av "B3 disparo 1 ms depois de prazo + tolerancia (M3)" "FALHA: B3 disparo fora de [14, 14+5] s" "arma 19001000000"
avok "B3 disparo exatamente em prazo + tolerancia" "OK: B3 disparo" "arma 19000000000"
av "B3 disparo antes do prazo" "FALHA: B3 disparo fora de [14, 14+5] s" "arma 13800000000"
avok "B3 disparo no prazo (desde antes de armar = 14 s)" "OK: B3 disparo em 14000 ms" "arma 13884000000"
av "B3 tempos.txt diferente da cronologia" "FALHA: B3" "sed -i 's/delta_desde_antes_de_armar_ms=[0-9]*/delta_desde_antes_de_armar_ms=15000/' g5b-B-copia/tempos.txt"
av "B3 sem tempos.txt" "FALHA: B3" "rm g5b-B-copia/tempos.txt"
av "B4 journal da unit (copia) com 'Failed with result'" "FALHA: B4 g5b-B-copia/journal-unit.txt" "echo 'x systemd[1]: u.service: Failed with result exit-code.' >> g5b-B-copia/journal-unit.txt"
av "B4 05-journal-unit.txt sem 'Heal: removendo'" "FALHA: B4 05-journal-unit.txt" "sed -i '/Heal: removendo/d' 05-journal-unit.txt"
av "B4 05-journal-unit.txt sem 'Deactivated successfully'" "FALHA: B4 05-journal-unit.txt" "sed -i '/Deactivated successfully/d' 05-journal-unit.txt"
av "B4 unit failed" "FALHA: B4 unit em estado failed" "sed -i 's/^--- is-failed: .*/--- is-failed: failed/' g5b-B-copia/estado-systemd.txt"
av "B5 ExecStart diferente (isn2)" "FALHA: B5 ExecStart" "sed -i 's/^ExecStart=bash scripts\/inject-partition.sh --heal --minority isn3/ExecStart=bash scripts\/inject-partition.sh --heal --minority isn2/' g5b-B-copia/unit-armada.txt"
av "B5 sem Environment PODMAN" "FALHA: B5 Environment" "sed -i '/^Environment=/d' g5b-B-copia/unit-armada.txt"
av "B5 WorkingDirectory diferente" "FALHA: B5 WorkingDirectory" "sed -i 's/^WorkingDirectory=.*/WorkingDirectory=\/tmp/' g5b-B-copia/unit-armada.txt"
av "B5 .service com diretiva a mais" "FALHA: B5 .service com diretiva a mais" "sed -i 's/^\(ExecStart=bash .*\)$/\1\nRestart=always/' g5b-B-copia/unit-armada.txt"
av "B5 .timer com diretiva a mais" "FALHA: B5 .timer com diretiva" "sed -i 's/^AccuracySec=1s$/AccuracySec=1s\nPersistent=true/' g5b-B-copia/unit-armada.txt"
av "B5 .timer sem AccuracySec=1s" "FALHA: B5 .timer sem 'AccuracySec=1s'" "sed -i '/^AccuracySec=1s$/d' g5b-B-copia/unit-armada.txt"
av "B5 .timer com outro prazo (15 s)" "FALHA: B5 .timer com OnActiveSec='15s'" "sed -i 's/^OnActiveSec=14s/OnActiveSec=15s/' g5b-B-copia/unit-armada.txt"
avok "B5 unit no formato real do systemd 255 (ExecStart com aspas, /usr/bin/bash, Description, RemainAfterElapse=no)" "OK: B5 corpo da unit = armar de producao" "formato_real"
av "B5 formato real com Description diferente no .timer" "FALHA: B5 Description do .timer" "formato_real outra"
av "B6 clsact no disparo" "FALHA: B6 qdisc-no-disparo.txt ausente ou com clsact" "echo 'qdisc clsact ffff: parent ffff:fff1' >> g5b-B-copia/qdisc-no-disparo.txt"
av "B6 isn1 sem MergeView de 3 membros" "FALHA: B6 log-vista-isn1.txt sem MergeView de 3 membros" "sed -i 's/ (3) / (2) /' g5b-B-copia/log-vista-isn1.txt"
av "B6 05-vistas sem MergeView de isn2 depois do disparo" "FALHA: B6 05-vistas.log: isn2 sem MergeView" "awk '/^## / { s = (\$2 == \"isn2\") } !(s && /ISPN000093/)' 05-vistas.log > x && mv x 05-vistas.log"
av "B6 saude-depois com size=2" "FALHA: B6 saude-depois.txt" "sed -i '3s/number_of_nodes\":3/number_of_nodes\":2/' g5b-B-copia/saude-depois.txt"
av "B6 timer ao fim" "FALHA: B6 timer tcc-heal ao fim" "sed -i 's/^(nenhum)\$/tcc-heal-1-2.timer n\/a/' g5b-B-copia/estado-systemd.txt"
av "B6 systemctl stop no journal do sudo (desarmado)" "FALHA: B6 1 'systemctl stop'" "jsudo 1 'COMMAND=/usr/bin/systemctl stop tcc-heal-1-2.timer' session-3.scope"
av "B7 sem t_mergeview" "FALHA: B7 cronologia" "sed -i '/^t_mergeview=/d' g5b-B-copia/cronologia.txt"
av "B7 t_saudavel antes de t_mergeview" "FALHA: B7 cronologia" "$CR; poe t_saudavel \$(( \$(cr t_mergeview) - 1 ))"
av "D-1 cura manual (del da sessao) depois de t_fire (MS-3)" "FALHA: D-1 detector do sudo: 1 tc qdisc del ... clsact da sessao desde t_kill" \
   "$CR; jsudo \$(( \$(cr t_fire) / 1000 + 1000000 )) 'COMMAND=/usr/bin/nsenter -t 4003 -n tc qdisc del dev eth0 clsact' session-9.scope"
av "D-1 cura manual (del da sessao) depois do KILL" "FALHA: D-1 detector do sudo: 1 tc qdisc del ... clsact da sessao desde t_kill" \
   "$CR; jsudo \$(( \$(cr t_kill) / 1000 + 500000 )) 'COMMAND=/usr/bin/nsenter -t 4003 -n tc qdisc del dev eth0 clsact' session-9.scope"
av "D-1 o del da unit vira sessao (M4: sem filtro de origem)" "FALHA: D-1 detector do sudo: 1 tc qdisc del ... clsact da sessao" \
   "u=\$(sed -n 's/^unit=\([^ ]*\).*/\1/p' g5b-B-copia/unit-armada.txt); sed -i \"s/\$u.service/session-3.scope/\" 05-sudo-journal.txt"
avok "D-1 dels da sessao antes do KILL (pre-limpeza e sonda do injetor) nao contam" "OK: D-1 detector do sudo: 0" "[ \$(grep -c 'tc qdisc del dev eth0 clsact' 05-sudo-journal.txt) -ge 3 ]"
avok "D-1 sem del da unit: so leitura humana" "nada provou ainda que o sudo de root dentro da unit e registrado" \
   "u=\$(sed -n 's/^unit=\([^ ]*\).*/\1/p' g5b-B-copia/unit-armada.txt); grep -v \"\$u.service\" 05-sudo-journal.txt > x; mv x 05-sudo-journal.txt"
av "D-1 journal do sudo ausente" "FALHA: D-1" "rm 05-sudo-journal.txt"
av "D-2 amostra sem clsact durante o corte" "FALHA: D-2 serie tc de isn3 incoerente" \
   "$CR; a=\$(( \$(cr t_start) + 1000000000 )); h=\$(( \$(cr t_fire) - 2000000000 )); awk -F, -v a=\"\$a\" -v h=\"\$h\" 'BEGIN{OFS=\",\"} NR>1 && \$1+0>=a+0 && \$1+0<=h+0 && !d {\$2=\"0\"; d=1} {print}' janela/tc-isn3.csv > x && mv x janela/tc-isn3.csv"
av "D-2 clsact depois do disparo" "FALHA: D-2 serie tc de isn3 incoerente" \
   "$CR; h=\$(( \$(cr t_fire) + 1000000000 )); awk -F, -v h=\"\$h\" 'BEGIN{OFS=\",\"} NR>1 && \$1+0>=h+0 && !d {\$2=\"1\"; d=1} {print}' janela/tc-isn3.csv > x && mv x janela/tc-isn3.csv"
av "D-2 serie vazia" "FALHA: D-2" "head -1 janela/tc-isn3.csv > x && mv x janela/tc-isn3.csv"
av "D-2 serie truncada logo depois do disparo (MS-5)" "FALHA: D-2 serie tc de isn3 incoerente" \
   "$CR; h=\$(( \$(cr t_fire) + 1000000000 )); awk -F, -v h=\"\$h\" 'NR==1 || \$1+0 < h+0' janela/tc-isn3.csv > x && mv x janela/tc-isn3.csv"
av "D-3 AccuracyUSec=1min" "FALHA: D-3 timer: AccuracyUSec=1min" "sed -i 's/^AccuracyUSec=1s/AccuracyUSec=1min/' g5b-B-copia/unit-armada.txt"
av "D-3 prazo_s diferente (15)" "FALHA: D-3 timer" "sed -i '1s/prazo_s=14/prazo_s=15/' g5b-B-copia/unit-armada.txt"
av "sem parametros.txt" "FALHA: sem" "rm parametros.txt"
avok "Q-14 ISPN000524 nos logs e RESULTADO" "RESULTADO: 1 linha(s) ISPN000524/ISPN100014/ISPN000638" "echo '2026-11-04 06:00:00,000 WARN ISPN000524: timeout' >> g5b-B-copia/log-completo-isn2.txt"
avok "Q-14 com leitura humana" "LEITURA HUMANA: ler os timeouts de resolucao de conflito" "echo '2026-11-04 06:00:00,000 WARN ISPN100014: x' >> g5b-B-copia/log-completo-isn1.txt"
avok "tempo total de corte como RESULTADO" "RESULTADO: tempo total de corte (t_start a t_fire)" "true"
fi

if secao T-8; then
echo "== T-8: reboot no meio do bloco (o arquivo do boot_id muda antes do passo 6): INVALIDO =="
nova_arvore; roda $'GO\n' S7T_TROCA_BOOT=1
ok "T-8 rc 6" 6 "$RC"; contem "T-8 motivo" "FALHA: B1/A1 boot_id inicio=$BOOT fim=11111111-2222-3333-4444-555555555555" "$(cat "$OUT/08-avaliacao.txt")"
contem "T-8 INVALIDO" "S7: INVALIDO" "$(cat "$OUT/08-avaliacao.txt")"
fi

if secao T-9; then
echo "== T-9: Ctrl-C real (SIGINT ao grupo) durante a janela: rc 130, rc.txt, coletor parado, nada vivo =="
nova_arvore
set -m
( printf 'GO\n' | env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX=x SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S7T_PORTAO_FIX="$FIXTURE" \
    S7T_FIX="$T/fixB" S7T_PIDS="$T/pids" S7T_G5B_REAL="$REPO/runs/gate-b29/g5b.sh" S7T_BOOT_FILE="$T/boot_id" S7_TESTE=1 S7_HASHES="$T/hashes" \
    S7_BOOT_ID_FILE="$T/boot_id" S7_COMMIT_REF="$REF" S7_INJ_LOCK="$T/inject-partition-isn3.lock" S7_PORTAO_MIN_S=1 S7_PORTAO_FOLGA_S=0 S7_SAUDE_S=2 S7_TC_S=1 \
    S7_ESPERA_GO_S=30 S7_ESPERA_COLETOR_S=30 S7_DUR=8 S7_T_KILL_S=4 S7_MARGEM_S=6 S7_TOL_S=5 S7_G5B_FOLGA=3 S7_D2_PRE_FIRE_S=2 S7_PERSISTE_S=2 \
    bash "$G/runs/vm-s7-2026-11-04/s7.sh" --boot-esperado "$BOOT" --bloco-anterior "$ANT" > "$T/ctrlc.out" 2>&1 ) &
PG=$!; set +m; echo "$PG" >> "$T/pids"
for _ in $(seq 1 600); do OUT="$(ls -d "$G/runs/vm-s7-2026-11-04"/vm-* 2>/dev/null | head -1)"
  [ -n "$OUT" ] && [ "$(grep -c ',1,' "$OUT/janela/tc-isn3.csv" 2>/dev/null || true)" -ge 2 ] 2>/dev/null && break; sleep 0.2; done
ok "T-9 janela aberta (clsact visto pelo coletor) antes do Ctrl-C" 1 "$([ "$(grep -c ',1,' "$OUT/janela/tc-isn3.csv" 2>/dev/null || true)" -ge 2 ] 2>/dev/null && echo 1 || echo 0)"
kill -INT -- "-$PG" 2>/dev/null; wait "$PG"; RC=$?
ok "T-9 rc 130" 130 "$RC"
contem "T-9 rc.txt registra a interrupcao" "interrompido" "$(cat "$OUT/rc.txt")"
contem "T-9 mensagem com a cura manual e o estado do dead-man" "Estado do dead-man:" "$(cat "$T/ctrlc.out")"
contem "T-9 mensagem com a cura manual" "Cura manual (em ~/tcc-gate): PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3" "$(cat "$T/ctrlc.out")"
sleep 4
contem "T-9 coletor interrompido" "coletor interrompido" "$(cat "$OUT/03-coletor.log")"
CP=$(sed -n 's/^coletor em segundo plano pid=\([0-9]*\).*/\1/p' "$OUT/rc.txt")
ok "T-9 coletor (pid ${CP:-?}) nao esta mais vivo" 1 "$([ -n "$CP" ] && ! kill -0 "$CP" 2>/dev/null && echo 1 || echo 0)"
a1=$(grep -c . "$OUT/janela/saude.csv"); b1=$(grep -c . "$OUT/janela/tc-isn3.csv"); sleep 4
a2=$(grep -c . "$OUT/janela/saude.csv"); b2=$(grep -c . "$OUT/janela/tc-isn3.csv")
ok "T-9 saude-log e serie tc pararam de crescer (nenhum filho do coletor vivo)" "$a1 $b1" "$a2 $b2"
ok "T-9 sem portao pos" 1 "$(grep -c '^PORTAO' "$T/shim.log")"
ok "T-9 sem MANIFEST (bloco interrompido)" 0 "$([ -e "$OUT/MANIFEST.sha256" ] && echo 1 || echo 0)"
kill -TERM -- "-$PG" 2>/dev/null
# T-9b (M9 sobrevivia ao T-9: com SIGINT ao grupo o proprio coletor tem trap de INT e para sozinho). Aqui o SIGINT vai SO ao
# processo do s7.sh, como um 'kill -INT <pid>': quem tem de parar o coletor e o trap de INT do s7.sh
echo "== T-9b: SIGINT so ao processo do s7.sh (nao ao grupo): o trap do s7.sh encerra o coletor =="
nova_arvore; printf 'GO\n' > "$T/go.txt"
set -m
env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX=x SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S7T_PORTAO_FIX="$FIXTURE" \
    S7T_FIX="$T/fixB" S7T_PIDS="$T/pids" S7T_G5B_REAL="$REPO/runs/gate-b29/g5b.sh" S7T_BOOT_FILE="$T/boot_id" S7_TESTE=1 S7_HASHES="$T/hashes" \
    S7_BOOT_ID_FILE="$T/boot_id" S7_COMMIT_REF="$REF" S7_INJ_LOCK="$T/inject-partition-isn3.lock" S7_PORTAO_MIN_S=1 S7_PORTAO_FOLGA_S=0 S7_SAUDE_S=2 S7_TC_S=1 \
    S7_ESPERA_GO_S=30 S7_ESPERA_COLETOR_S=30 S7_DUR=8 S7_T_KILL_S=4 S7_MARGEM_S=6 S7_TOL_S=5 S7_G5B_FOLGA=3 S7_D2_PRE_FIRE_S=2 S7_PERSISTE_S=2 \
    bash "$G/runs/vm-s7-2026-11-04/s7.sh" --boot-esperado "$BOOT" --bloco-anterior "$ANT" < "$T/go.txt" > "$T/ctrlc2.out" 2>&1 &
PS7=$!; set +m; echo "$PS7" >> "$T/pids"
for _ in $(seq 1 600); do OUT="$(ls -d "$G/runs/vm-s7-2026-11-04"/vm-* 2>/dev/null | head -1)"
  [ -n "$OUT" ] && [ "$(grep -c ',1,' "$OUT/janela/tc-isn3.csv" 2>/dev/null || true)" -ge 2 ] 2>/dev/null && break; sleep 0.2; done
ok "T-9b janela aberta antes do SIGINT" 1 "$([ "$(grep -c ',1,' "$OUT/janela/tc-isn3.csv" 2>/dev/null || true)" -ge 2 ] 2>/dev/null && echo 1 || echo 0)"
kill -INT "$PS7" 2>/dev/null
# espera limitada: sem o kill do coletor no trap, o s7.sh fica preso no EXIT (o coletor segura o pipe do console) ate o limite do coletor
for _ in $(seq 1 450); do kill -0 "$PS7" 2>/dev/null || break; sleep 0.2; done
ok "T-9b s7.sh terminou em ate 90 s depois do SIGINT" 1 "$(kill -0 "$PS7" 2>/dev/null && echo 0 || echo 1)"
kill -0 "$PS7" 2>/dev/null && kill -TERM -- "-$PS7" 2>/dev/null
wait "$PS7"; RC=$?
ok "T-9b rc 130 (o trap roda quando o g5b.sh termina)" 130 "$RC"
contem "T-9b rc.txt registra a interrupcao" "interrompido" "$(cat "$OUT/rc.txt")"
sleep 4
contem "T-9b coletor encerrado pelo trap do s7.sh" "coletor interrompido" "$(cat "$OUT/03-coletor.log")"
CP=$(sed -n 's/^coletor em segundo plano pid=\([0-9]*\).*/\1/p' "$OUT/rc.txt")
ok "T-9b coletor (pid ${CP:-?}) nao esta mais vivo" 1 "$([ -n "$CP" ] && ! kill -0 "$CP" 2>/dev/null && echo 1 || echo 0)"
a1=$(grep -c . "$OUT/janela/saude.csv"); b1=$(grep -c . "$OUT/janela/tc-isn3.csv"); sleep 4
a2=$(grep -c . "$OUT/janela/saude.csv"); b2=$(grep -c . "$OUT/janela/tc-isn3.csv")
ok "T-9b saude-log e serie tc pararam de crescer" "$a1 $b1" "$a2 $b2"
kill -TERM -- "-$PS7" 2>/dev/null
fi

if secao T-10; then
echo "== T-10: nenhum tc de escrita sai do involucro (4.1) =="
nova_arvore; roda $'GO\n'
ok "T-10 rc 0" 0 "$RC"
ok "T-10 0 tc qdisc/filter add/del/change/replace com origem no involucro" 0 \
  "$(grep -E '(tc (qdisc|filter) (add|del|change|replace))' "$T/shim.log" | grep -c 'origem=involucro')"
ok "T-10 o shim distingue a origem: ha tc de escrita vindo do g5b.sh (controle positivo)" 1 \
  "$([ "$(grep -E 'tc qdisc (add|del) dev eth0 clsact' "$T/shim.log" | grep -c 'origem=g5b')" -ge 4 ] && echo 1 || echo 0)"
ok "T-10 o involucro le o tc de isn3 (serie do coletor)" 1 "$([ "$(grep -c 'nsenter -t 4003 -n tc qdisc show dev eth0 origem=involucro' "$T/shim.log")" -ge 5 ] && echo 1 || echo 0)"
ok "T-10 o involucro nao chama systemctl stop/start nem systemd-run" 0 "$(grep -cE '^systemctl (stop|start|disable)|systemd-run' "$T/shim.log")"
fi

if secao T-11; then
echo "== T-11: --avaliar sobre a copia reproduz o 08-avaliacao.txt (byte a byte, exceto a linha de data) =="
rm -rf "$T/copia-host"; cp -r "$T/bom" "$T/copia-host"; avalia "$T/copia-host" > "$T/reav.txt"
ok "T-11 --avaliar rc 0" 0 $?
ok "T-11 identico ao 08-avaliacao.txt menos a linha '== avaliacao do S7 <data> sobre <dir>'" "" \
  "$(diff <(grep -v '^== avaliacao do S7 ' "$T/bom/08-avaliacao.txt") <(grep -v '^== avaliacao do S7 ' "$T/reav.txt"))"
ok "T-11 so a linha de data difere" 1 "$(diff "$T/bom/08-avaliacao.txt" "$T/reav.txt" | grep -c '^> == avaliacao do S7 ')"
fi

if secao T-12; then
echo "== T-12: --dry-run nao chama sudo, podman, tc, systemctl nem journalctl =="
nova_arvore; : > "$T/shim.log"
X=$(env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S7_TESTE=1 S7_DUR=8 S7_T_KILL_S=4 S7_MARGEM_S=6 S7_TOL_S=5 S7_G5B_FOLGA=3 S7_D2_PRE_FIRE_S=2 \
  S7T_G5B_REAL="$REPO/runs/gate-b29/g5b.sh" bash "$G/runs/vm-s7-2026-11-04/s7.sh" --dry-run --boot-esperado "$BOOT" --bloco-anterior "$ANT" 2>&1); r=$?
ok "T-12 rc 0" 0 "$r"; contem "T-12 plano do S7" "S7 (G-5b-B) dry-run: nada executado" "$X"
contem "T-12 plano do passo 3 com o modo B (R-2)" "e bash runs/gate-b29/g5b.sh B neste terminal" "$X"; nao "T-12 plano sem 'g5b.sh A' (R-2)" "g5b.sh A " "$X"
contem "T-12 inclui o dry-run do g5b.sh B" "G-5b-B (dry-run; nada executado): duracao=8s, KILL em +4s do start" "$X"
ok "T-12 nenhum sudo/podman/tc/systemctl/journalctl/flock" 0 "$(grep -cE '^(sudo|podman|tc|systemctl|journalctl|flock) ' "$T/shim.log")"
ok "T-12 nenhum vm-* criado" 0 "$(ls -d "$G/runs/vm-s7-2026-11-04"/vm-* 2>/dev/null | wc -l | tr -d ' ')"
X=$(env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" SHIM_LOG="$T/shim.log" S7T_G5B_REAL="$REPO/runs/gate-b29/g5b.sh" bash "$G/runs/vm-s7-2026-11-04/s7.sh" --dry-run 2>&1); r=$?
ok "T-12 --dry-run sem os outros argumentos: rc 0" 0 "$r"; contem "T-12 dry-run com os parametros reais (120/70/90)" "duracao=120s, KILL em +70s" "$X"
fi

echo "RESUMO: $P PASS, $F FAIL ($(( $(date +%s) - T_INI )) s)"
[ "$F" = 0 ]
