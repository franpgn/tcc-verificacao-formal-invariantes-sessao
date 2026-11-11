#!/bin/bash
# Smoke da correcao do P-48 (06/11/2026): o mesmo cluster local p48 do runs/p48-smoke (portas 12901-12903, XML sem
# caches e os seis caches da ponta), com o jar da branch fix/p48-topologia-convergencia.
#   Parte A (sonda): SondaP48Fix.java repete o smoke de antes (rcm principal sem operacao durante um F1 de 60 s) e
#     mede o erro das leituras por replica antes e depois de AtualizacaoTopologia.atualizar().
#   Parte B (programa): o programa de carga de verdade, S3 ASYNC de 90 s com --convergence e sem controlador, com um
#     F1 do isn2 no meio da carga (podman kill, 30 s, podman start; marcadores start/end como os do inject-crash.sh);
#     confere a linha "Topologia do principal: ..." do laco de convergencia e o resumo da convergencia.
# Uso: bash runs/p48-fix-smoke/scripts/p48fix.sh
set -uo pipefail
W=/c/Users/Francesco/Development/tcc/wt-p48
R=$W/runs/p48-fix-smoke
C=$R/cluster
D=$R/execucao
G=$D/gatilhos
JAVA="$JAVA_HOME/bin/java"
JAR=$W/workload/target/session-workload-0.1.0-SNAPSHOT.jar
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
registrar_saude() { for no in isn1 isn2 isn3; do printf '%s\t%s\t%s\t%s\n' "$(utc)" "$1" "$no" "$(saude $no)"; done >> $D/saude.tsv; }
descer() { (cd $C && podman-compose -p p48 -f podman-compose.p48.yml down > "$1" 2>&1); }
marcar() { echo "fault=F1 node=isn2 event=$1 t_epoch_ns=$(agora_ns)" >> $D/programa/marcadores.txt; }

rm -rf $D; mkdir -p $G $D/programa
fase "inicio base=$(git -C $W rev-parse --short HEAD) jar_sha256=$(sha256sum $JAR | cut -c1-64) xml_sha256=$(sha256sum $C/infinispan-sem-caches.xml | cut -c1-64)"
descer $D/compose-down-antes.txt
(cd $C && podman-compose -p p48 -f podman-compose.p48.yml up -d > $D/compose-up.txt 2>&1)
for i in $(seq 1 120); do [ "$(saude isn1 | cut -d' ' -f1)" = 3 ] && break; sleep 2; done
fase "cluster com $(saude isn1 | cut -d' ' -f1) nos"
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12901 --conferir "$ADMIN" --usuario admin \
  --dir $W/cluster/caches sessions counters sessions-async counters-async canario canario-async > $D/cria-caches.tsv 2> $D/cria-caches.err
fase "cria-caches rc=$?"
esperar_saude && fase "saude ok" || { fase "falha: saude"; exit 1; }

# ------------------------------------------------------------------ parte A: sonda
"$JAVA" -cp "$(cygpath -w $JAR)" "$(cygpath -w $R/scripts/SondaP48Fix.java)" $SRV admin $SENHA 2000 "$(cygpath -w $G)" \
  > $D/sonda.out 2> $D/sonda.err &
SP=$!
while [ ! -f $G/pronto-para-f1 ]; do sleep 1; kill -0 $SP 2>/dev/null || { fase "falha: sonda saiu antes do F1"; exit 1; }; done
fase "A: F1 inicio (podman kill p48-isn2)"
podman kill p48-isn2 > $D/a-f1-kill.txt 2>&1
sleep 60
fase "A: F1 podman start p48-isn2"
podman start p48-isn2 > $D/a-f1-start.txt 2>&1
esperar_saude && fase "A: F1 fim, saude ok" || fase "A: saude nao voltou em 300 s"
sleep 10; registrar_saude a-depois-do-f1
touch $G/f1-feito
wait $SP; fase "A: sonda exit=$?"

# ------------------------------------------------------------------ parte B: o programa de carga
P=$D/programa
"$JAVA" -jar "$(cygpath -w $JAR)" --scenario S3 --replication async --reset-state on --rep 1 --duration 90 --seed 4801 \
  --servers $SRV --username admin --password $SENHA --csv-dir "$(cygpath -w $P)" --marker "$(cygpath -w $P/marcadores.txt)" \
  --warmup-min-sec 20 --convergence --convergence-timeout-sec 120 --convergence-period-sec 5 \
  --rest-url http://127.0.0.1:12901 > $P/stdout.log 2> $P/stderr.log &
JP=$!
for i in $(seq 1 120); do grep -q '^Warm-up ate +' $P/stdout.log 2>/dev/null && break; sleep 1; done
fase "B: ancora vista; F1 a +25 s"
sleep 25
marcar start; podman kill p48-isn2 > $D/b-f1-kill.txt 2>&1; fase "B: F1 inicio (podman kill p48-isn2)"
sleep 30
podman start p48-isn2 > $D/b-f1-start.txt 2>&1; marcar end; fase "B: F1 podman start p48-isn2"
esperar_saude && fase "B: F1 fim, saude ok" || fase "B: saude nao voltou em 300 s"
registrar_saude b-depois-do-f1
wait $JP; fase "B: programa exit=$?"
grep -n 'Topologia do principal\|^Convergencia:\|AVISO: topologia\|Fim da carga\|Leitura por replica' $P/stdout.log > $D/b-linhas.txt
mkdir -p $D/servidores; for n in isn1 isn2 isn3; do podman logs -t p48-$n > $D/servidores/$n.log 2>&1; done
descer $D/compose-down.txt
fase "fim"
