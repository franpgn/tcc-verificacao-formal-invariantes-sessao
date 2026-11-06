#!/bin/bash
# Smoke da correcao do P-48, parte C (06/11/2026): o programa de carga SEM a correcao (jar da 1f65a02, a ponta da
# calibracao), S3 ASYNC de 90 s com --convergence e um F1 do isn2 no meio da carga, como na parte B. Ao lado, a
# SondaDivergencia abre o seu cliente antes do F1 e, depois que o programa sai, conta as chaves divergentes nos mesmos
# dados com a topologia velha (a do rcm principal sem operacao) e com a atualizada.
# Uso: bash runs/p48-fix-smoke/scripts/p48fix-c.sh <jar-sem-a-correcao>
set -uo pipefail
W=/c/Users/Francesco/Development/tcc/wt-p48
R=$W/runs/p48-fix-smoke
C=$R/cluster
D=$R/execucao-c
G=$D/gatilhos
JAVA="$JAVA_HOME/bin/java"
JAR_ANTIGO="$1"
JAR_SONDA=$W/workload/target/session-workload-0.1.0-SNAPSHOT.jar
SENHA=infinispan
SRV=127.0.0.1:12901,127.0.0.1:12902,127.0.0.1:12903
ADMIN=http://127.0.0.1:12901,http://127.0.0.1:12902,http://127.0.0.1:12903
porta() { case $1 in isn1) echo 12901;; isn2) echo 12902;; isn3) echo 12903;; esac; }
agora_ns() { python -I -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }
fase() { mkdir -p "$D"; printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$1" >> "$D/fases.tsv"; echo "== $(utc) $1"; }
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }
saude() {
  curl_adm "http://127.0.0.1:$(porta $1)/rest/v2/cache-managers/default/health" 2>/dev/null | python -I -c "
import json,sys
h=json.load(sys.stdin); c={x['cache_name']:x['status'] for x in h['cache_health']}
print(h['cluster_health']['number_of_nodes'], c.get('sessions-async','-'), c.get('counters-async','-'))" 2>/dev/null
}
esperar_saude() {
  local i ok
  for i in $(seq 1 150); do
    ok=1
    for no in isn1 isn2 isn3; do [ "$(saude $no)" = "3 HEALTHY HEALTHY" ] || ok=0; done
    [ $ok = 1 ] && return 0
    sleep 2
  done
  return 1
}
descer() { (cd $C && podman-compose -p p48 -f podman-compose.p48.yml down > "$1" 2>&1); }
marcar() { echo "fault=F1 node=isn2 event=$1 t_epoch_ns=$(agora_ns)" >> $D/programa/marcadores.txt; }

rm -rf $D; mkdir -p $G $D/programa
fase "inicio base=$(git -C $W rev-parse --short HEAD) jar_programa_sha256=$(sha256sum "$JAR_ANTIGO" | cut -c1-64) jar_sonda_sha256=$(sha256sum $JAR_SONDA | cut -c1-64)"
descer $D/compose-down-antes.txt
(cd $C && podman-compose -p p48 -f podman-compose.p48.yml up -d > $D/compose-up.txt 2>&1)
for i in $(seq 1 120); do [ "$(saude isn1 | cut -d' ' -f1)" = 3 ] && break; sleep 2; done
fase "cluster com $(saude isn1 | cut -d' ' -f1) nos"
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12901 --conferir "$ADMIN" --usuario admin \
  --dir $W/cluster/caches sessions counters sessions-async counters-async canario canario-async > $D/cria-caches.tsv 2> $D/cria-caches.err
fase "cria-caches rc=$?"
esperar_saude && fase "saude ok" || { fase "falha: saude"; exit 1; }

P=$D/programa
"$JAVA" -jar "$(cygpath -w "$JAR_ANTIGO")" --scenario S3 --replication async --reset-state on --rep 1 --duration 90 --seed 4801 \
  --servers $SRV --username admin --password $SENHA --csv-dir "$(cygpath -w $P)" --marker "$(cygpath -w $P/marcadores.txt)" \
  --warmup-min-sec 20 --convergence --convergence-timeout-sec 120 --convergence-period-sec 5 \
  --rest-url http://127.0.0.1:12901 > $P/stdout.log 2> $P/stderr.log &
JP=$!
for i in $(seq 1 120); do grep -q '^Warm-up ate +' $P/stdout.log 2>/dev/null && break; sleep 1; done
"$JAVA" -cp "$(cygpath -w $JAR_SONDA)" "$(cygpath -w $R/scripts/SondaDivergencia.java)" $SRV admin $SENHA "$(cygpath -w $G)" \
  sessions-async counters-async > $D/sonda.out 2> $D/sonda.err &
SP=$!
for i in $(seq 1 60); do [ -f $G/sonda-pronta ] && break; sleep 1; done
fase "ancora vista e sonda pronta; F1 a +25 s da ancora"
sleep 20
marcar start; podman kill p48-isn2 > $D/f1-kill.txt 2>&1; fase "F1 inicio (podman kill p48-isn2)"
sleep 30
podman start p48-isn2 > $D/f1-start.txt 2>&1; marcar end; fase "F1 podman start p48-isn2"
esperar_saude && fase "F1 fim, saude ok" || fase "saude nao voltou em 300 s"
wait $JP; fase "programa exit=$?"
touch $G/programa-fim
wait $SP; fase "sonda exit=$?"
grep -n 'Topologia do principal\|^Convergencia:\|Fim da carga\|Violacoes detectadas' $P/stdout.log > $D/linhas-programa.txt
mkdir -p $D/servidores; for n in isn1 isn2 isn3; do podman logs -t p48-$n > $D/servidores/$n.log 2>&1; done
descer $D/compose-down.txt
fase "fim"
