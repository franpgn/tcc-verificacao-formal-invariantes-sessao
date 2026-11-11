#!/bin/bash
# A1 smoke (06/11): politica de fusao do sessions-async (A1) contra PREFERRED_NON_NULL (controle) numa particao F2 do
# isn3, no cluster local a1 (projeto Podman proprio, portas 12801-12803, maquina Podman do WSL; sem VM). Harness no
# estilo de runs/w10-smoke e runs/w2b-smoke; o codigo do repositorio nao muda. Corte por nftables no netns do isn3
# (particao-a1.sh); marcadores gravados no formato do injetor (fault=F2 node=isn3 event=start|end t_epoch_ns=<n>).
#
# Uso: bash a1.sh NOME CONDICAO SEMENTE JAR [DURACAO_S] [CORTE_APOS_S] [CORTE_DUR_S]
#   CONDICAO: a1 (caches-a1/: sessions-async com br.unipampa.tcc.servidor.PoliticaFusaoSessoes) ou
#             controle (caches-controle/: PREFERRED_NON_NULL, os JSON da tag int/2026-11-06-bench).
#   O JAR da extensao (cluster/tcc-extensao-servidor.jar) e montado nas duas condicoes.
#   Carga S3 ASYNC L+ da recalibracao (DT-026): --s3-users 40 --s3-think-ms 500 --s3-session-median-sec 10
#   --s3-logout-prob 0.9 --s3-admin-mean-sec 5 --s3-replay-late-max 268 --s3-replay-probe --sweep; warm-up 30 s,
#   --w-recovery-sec 191, convergencia ate 600 s, como no execucao.json da recalibracao (exec-0004); --controller off
#   (o controlador nao entra neste teste). Duracao padrao 480 s (a recalibracao usou 300 s): na execucao de preparo com
#   300 s a cura levou 38 s no "podman machine ssh" e a fusao (MERGE3 depois do recuo de retransmissao do TCP) nao
#   aconteceu antes da varredura; o laco de convergencia so confere I5/I6, nao a vista do cluster.
#   O corte comeca CORTE_APOS_S depois do inicio da medicao (linha "Warm-up ate ...") e dura CORTE_DUR_S (90 s).
set -u
W=/c/Users/Francesco/Development/tcc/wt-a1
R=$W/runs/a1-smoke
C=$R/cluster
JAVA="$JAVA_HOME/bin/java"
SENHA=infinispan   # credencial de laboratorio do podman-compose.yml do repositorio; vai so por stdin ao curl
SRV=127.0.0.1:12801,127.0.0.1:12802,127.0.0.1:12803
ADMIN=http://127.0.0.1:12801,http://127.0.0.1:12802,http://127.0.0.1:12803
CACHES="sessions counters sessions-async counters-async canario canario-async"
porta() { case $1 in isn1) echo 12801;; isn2) echo 12802;; isn3) echo 12803;; esac; }
agora_ns() { python -I -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }
fase() { mkdir -p "$1"; printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$2" >> "$1/fases.tsv"; echo "== $(utc) $2"; }
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }
nos() {
  curl_adm "http://127.0.0.1:$(porta ${1:-isn1})/rest/v2/cache-managers/default/health" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['cluster_health']['number_of_nodes'])" 2>/dev/null
}
descer() { (cd $C && podman-compose -p a1 -f podman-compose.a1.yml down > "$1" 2>&1); }
estado() {
  for n in isn1 isn2 isn3; do
    printf '%s\t%s\t%s\n' "$(utc)" "$n" "$(podman inspect a1-$n --format '{{.State.Status}} {{.State.StartedAt}}' 2>&1)"
  done >> "$1/conteineres.tsv"
}
marcar() { printf 'fault=F2 node=isn3 event=%s t_epoch_ns=%s\n' "$2" "$(agora_ns)" >> "$1/marcadores.txt"; }
dormir_ate_ns() { local d; d=$(python -I -c "import time;print(max(0,($1-time.time_ns())/1e9))"); sleep "$d"; }

N=$1 COND=$2 SEMENTE=$3 JAR=$4 DUR=${5:-480} CORTE_APOS=${6:-10} CORTE_DUR=${7:-90}
case $COND in a1) CD=$R/caches-a1;; controle) CD=$R/caches-controle;; *) echo "condicao invalida: $COND" >&2; exit 1;; esac
D=$R/$N
[ -e "$D" ] && { echo "ja existe: $D" >&2; exit 1; }
mkdir -p "$D"
cd "$D"   # o "podman machine ssh" grava um arquivo NUL no diretorio corrente (removido no fim)
fase "$D" "inicio $N condicao=$COND semente=$SEMENTE duracao_s=$DUR corte_apos_medicao_s=$CORTE_APOS corte_dur_s=$CORTE_DUR jar_sha256=$(sha256sum "$JAR" | cut -c1-64) extensao_sha256=$(sha256sum $C/tcc-extensao-servidor.jar | cut -c1-64) base=$(git -C $W rev-parse --short HEAD) xml_sha256=$(sha256sum $C/infinispan-sem-caches.xml | cut -c1-64) jgroups_sha256=$(sha256sum $C/jgroups-tcp.xml | cut -c1-64) sessions_async_sha256=$(sha256sum $CD/sessions-async.json | cut -c1-64)"
descer "$D/compose-down-antes.txt"
(cd $C && podman-compose -p a1 -f podman-compose.a1.yml up -d > "$D/compose-up.txt" 2>&1)
n=""; for i in $(seq 1 90); do n=$(nos); [ "$n" = 3 ] && break; sleep 2; done
[ "$n" = 3 ] || { fase "$D" "falha: cluster a1 nao formou 3 nos"; estado "$D"; descer "$D/compose-down.txt"; exit 1; }
fase "$D" "cluster com 3 nos"
estado "$D"
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12801 --conferir "$ADMIN" --dir "$CD" \
  $CACHES > "$D/cria-caches.tsv" 2> "$D/cria-caches.err"
rc=$?; fase "$D" "cria-caches rc=$rc (dir=$(basename $CD))"
[ "$rc" = 0 ] || { descer "$D/compose-down.txt"; exit 1; }
for n in isn1 isn2 isn3; do
  curl_adm "http://127.0.0.1:$(porta $n)/rest/v2/caches/sessions-async?action=config" > "$D/config-sessions-async-$n.json"
  curl_adm "http://127.0.0.1:$(porta $n)/rest/v2/caches/counters-async?action=config" > "$D/config-counters-async-$n.json"
done
fase "$D" "config lida nos tres nos: $(grep -ho '"merge-policy":"[^"]*"' $D/config-sessions-async-isn*.json | sort | uniq -c | tr -s ' ' | tr '\n' ';')"
: > "$D/marcadores.txt"
cmd=("$JAVA" -jar "$JAR" --scenario S3 --replication async --reset-state on --rep 1 --duration "$DUR" --seed "$SEMENTE"
     --servers "$SRV" --username admin --password "$SENHA" --rest-url http://127.0.0.1:12801
     --csv-dir "$(cygpath -w "$D")" --marker "$(cygpath -w "$D/marcadores.txt")" --w-recovery-sec 191
     --warmup-min-sec 30 --convergence --convergence-timeout-sec 600 --convergence-period-sec 5 --controller off
     --hotrod-timeout-ms 2000
     --s3-users 40 --s3-think-ms 500 --s3-session-median-sec 10 --s3-logout-prob 0.9 --s3-admin-mean-sec 5
     --s3-replay-late-max 268 --s3-replay-probe --sweep)
printf '%q ' "${cmd[@]}" | sed "s/--password $SENHA/--password ***/" > "$D/comando.txt"; echo >> "$D/comando.txt"
( "${cmd[@]}" > "$D/stdout.log" 2> "$D/stderr.log"; echo "exit=$?" > "$D/exit.txt" ) &
for i in $(seq 1 120); do grep -q '^Warm-up ate' "$D/stdout.log" 2>/dev/null && break; sleep 1; done
t_anc=$(agora_ns)
fase "$D" "ancora (linha Warm-up vista): $(grep -m1 '^Warm-up ate' "$D/stdout.log")"
med=$(sed -n 's/^Warm-up ate +[0-9]*s; medicao a partir de +\([0-9]*\)s.*/\1/p' "$D/stdout.log" | head -1)
dormir_ate_ns $(( t_anc + (${med:-30} + CORTE_APOS) * 1000000000 ))
fase "$D" "corte-inicio isn3"
marcar "$D" start
bash $R/scripts/particao-a1.sh cortar isn3 isn1 isn2 > "$D/corte.txt" 2>&1
fase "$D" "corte-fim isn3 rc=$?"
estado "$D"
sleep "$CORTE_DUR"
fase "$D" "cura-inicio isn3"
bash $R/scripts/particao-a1.sh curar isn3 isn1 isn2 > "$D/cura.txt" 2>&1
rc=$?
marcar "$D" end
fase "$D" "cura-fim isn3 rc=$rc"
while [ ! -f "$D/exit.txt" ]; do sleep 2; done
fase "$D" "carga $(cat "$D/exit.txt")"
estado "$D"
mkdir -p "$D/servidores"
for n in isn1 isn2 isn3; do podman logs -t a1-$n > "$D/servidores/$n.log" 2>&1; done
descer "$D/compose-down.txt"
rm -f "$D/NUL"
fase "$D" "fim $N (cluster descido)"
