#!/bin/bash
# W-10 smoke (06/11): sonda de replay do S3 numa particao F2 do isn3, no cluster local w10 (projeto Podman proprio,
# portas 12901-12903, maquina Podman do WSL; sem VM). Harness no estilo de runs/w2b-smoke e runs/w6-int-smoke; o codigo
# do repositorio nao muda. Corte por nftables no netns do isn3 (particao-w10.sh), porque a maquina WSL nao tem tc; os
# marcadores sao gravados por este harness no formato do injetor (fault=F2 node=isn3 event=start|end t_epoch_ns=<n>).
#
# Uso: bash w10.sh NOME SEMENTE JAR [DURACAO_S] [CORTE_APOS_S] [CORTE_DUR_S]
#   S3 ASYNC, --warmup-min-sec 30, --convergence (teto 180 s), --controller off, --s3-replay-probe --sweep;
#   30 usuarios (10 no isn3), sessao mediana de 8 s e logout sempre (mais logouts na janela; e smoke, nao calibracao).
#   O corte comeca CORTE_APOS_S depois do inicio da medicao (a linha "Warm-up ate ...") e dura CORTE_DUR_S.
set -u
W=/c/Users/Francesco/Development/tcc/wt-w10
R=$W/runs/w10-smoke
C=$R/cluster
JAVA="$JAVA_HOME/bin/java"
SENHA=infinispan   # credencial de laboratorio do podman-compose.yml do repositorio; vai so por stdin ao curl
SRV=127.0.0.1:12901,127.0.0.1:12902,127.0.0.1:12903
ADMIN=http://127.0.0.1:12901,http://127.0.0.1:12902,http://127.0.0.1:12903
CACHES="sessions counters sessions-async counters-async canario canario-async"
porta() { case $1 in isn1) echo 12901;; isn2) echo 12902;; isn3) echo 12903;; esac; }
agora_ns() { python -I -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }
fase() { mkdir -p "$1"; printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$2" >> "$1/fases.tsv"; echo "== $(utc) $2"; }
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }
nos() {
  curl_adm "http://127.0.0.1:$(porta ${1:-isn1})/rest/v2/cache-managers/default/health" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['cluster_health']['number_of_nodes'])" 2>/dev/null
}
descer() { (cd $C && podman-compose -p w10 -f podman-compose.w10.yml down > "$1" 2>&1); }
estado() {
  for n in isn1 isn2 isn3; do
    printf '%s\t%s\t%s\n' "$(utc)" "$n" "$(podman inspect w10-$n --format '{{.State.Status}} {{.State.StartedAt}}' 2>&1)"
  done >> "$1/conteineres.tsv"
}
marcar() { printf 'fault=F2 node=isn3 event=%s t_epoch_ns=%s\n' "$2" "$(agora_ns)" >> "$1/marcadores.txt"; }
dormir_ate_ns() { local d; d=$(python -I -c "import time;print(max(0,($1-time.time_ns())/1e9))"); sleep "$d"; }

N=$1 SEMENTE=$2 JAR=$3 DUR=${4:-200} CORTE_APOS=${5:-15} CORTE_DUR=${6:-80}
D=$R/$N
mkdir -p "$D"
fase "$D" "inicio $N semente=$SEMENTE duracao_s=$DUR corte_apos_medicao_s=$CORTE_APOS corte_dur_s=$CORTE_DUR jar_sha256=$(sha256sum "$JAR" | cut -c1-64) base=$(git -C $W rev-parse --short HEAD) xml_sha256=$(sha256sum $C/infinispan-sem-caches.xml | cut -c1-64) jgroups_sha256=$(sha256sum $C/jgroups-tcp.xml | cut -c1-64)"
descer "$D/compose-down-antes.txt"
(cd $C && podman-compose -p w10 -f podman-compose.w10.yml up -d > "$D/compose-up.txt" 2>&1)
n=""; for i in $(seq 1 90); do n=$(nos); [ "$n" = 3 ] && break; sleep 2; done
[ "$n" = 3 ] || { fase "$D" "falha: cluster w10 nao formou 3 nos"; estado "$D"; descer "$D/compose-down.txt"; exit 1; }
fase "$D" "cluster com 3 nos"
estado "$D"
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12901 --conferir "$ADMIN" $CACHES \
  > "$D/cria-caches.tsv" 2> "$D/cria-caches.err"
rc=$?; fase "$D" "cria-caches rc=$rc"
[ "$rc" = 0 ] || { descer "$D/compose-down.txt"; exit 1; }
: > "$D/marcadores.txt"
cmd=("$JAVA" -jar "$JAR" --scenario S3 --replication async --reset-state on --rep 1 --duration "$DUR" --seed "$SEMENTE"
     --servers "$SRV" --username admin --password "$SENHA" --rest-url http://127.0.0.1:12901
     --csv-dir "$(cygpath -w "$D")" --marker "$(cygpath -w "$D/marcadores.txt")" --w-recovery-sec 60
     --warmup-min-sec 30 --convergence --convergence-timeout-sec 180 --convergence-period-sec 5 --controller off
     --s3-users 30 --s3-session-median-sec 8 --s3-logout-prob 1.0 --hotrod-timeout-ms 2000
     --s3-replay-probe --sweep)
printf '%q ' "${cmd[@]}" | sed "s/--password $SENHA/--password ***/" > "$D/comando.txt"; echo >> "$D/comando.txt"
( "${cmd[@]}" > "$D/stdout.log" 2> "$D/stderr.log"; echo "exit=$?" > "$D/exit.txt" ) &
for i in $(seq 1 120); do grep -q '^Warm-up ate' "$D/stdout.log" 2>/dev/null && break; sleep 1; done
t_anc=$(agora_ns)
fase "$D" "ancora (linha Warm-up vista): $(grep -m1 '^Warm-up ate' "$D/stdout.log")"
med=$(sed -n 's/^Warm-up ate +[0-9]*s; medicao a partir de +\([0-9]*\)s.*/\1/p' "$D/stdout.log" | head -1)
dormir_ate_ns $(( t_anc + (${med:-30} + CORTE_APOS) * 1000000000 ))
fase "$D" "corte-inicio isn3"
marcar "$D" start
bash $R/scripts/particao-w10.sh cortar isn3 isn1 isn2 > "$D/corte.txt" 2>&1
fase "$D" "corte-fim isn3 rc=$?"
estado "$D"
sleep "$CORTE_DUR"
fase "$D" "cura-inicio isn3"
bash $R/scripts/particao-w10.sh curar isn3 isn1 isn2 > "$D/cura.txt" 2>&1
rc=$?
marcar "$D" end
fase "$D" "cura-fim isn3 rc=$rc"
while [ ! -f "$D/exit.txt" ]; do sleep 2; done
fase "$D" "carga $(cat "$D/exit.txt")"
estado "$D"
mkdir -p "$D/servidores"
for n in isn1 isn2 isn3; do podman logs -t w10-$n > "$D/servidores/$n.log" 2>&1; done
descer "$D/compose-down.txt"
fase "$D" "fim $N (cluster descido)"
