#!/bin/bash
# Smoke do P-48 (06/11/2026, pedido do coordenador): a topologia do rcm principal fica velha depois de um F1 em S3, e
# isso muda as leituras por replica do laco de convergencia? Cluster local p48 (portas 12901-12903) com o
# cluster/infinispan-sem-caches.xml e o jgroups-tcp.xml da ponta 1f65a02 e os seis caches criados pelo
# scripts/cria-caches.sh da ponta. A sonda (SondaP48.java, com o jar da ponta) faz o que o programa faz com o rcm
# principal; este driver faz o F1 (podman kill do isn2, 60 s, podman start, como o F1 do rebaseline e da calibracao:
# f1_dur_s = 60) e, depois, uma F2 (isn3 isolado por nftables por 40 s e curado), esperando a saude voltar.
# Uso: bash runs/p48-smoke/scripts/p48.sh
set -uo pipefail
W=/c/Users/Francesco/Development/tcc/wt-p48
R=$W/runs/p48-smoke
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
saude() {  # saude <no>: "<number_of_nodes> <status sessions-async> <status counters-async>"
  curl_adm "http://127.0.0.1:$(porta $1)/rest/v2/cache-managers/default/health" 2>/dev/null | python -I -c "
import json,sys
h=json.load(sys.stdin); c={x['cache_name']:x['status'] for x in h['cache_health']}
print(h['cluster_health']['number_of_nodes'], c.get('sessions-async','-'), c.get('counters-async','-'))" 2>/dev/null
}
esperar_saude() {  # 3 nos e os dois caches HEALTHY nos tres nos, ate 300 s
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

rm -rf $D; mkdir -p $G
fase "inicio base=$(git -C $W rev-parse --short HEAD) jar_sha256=$(sha256sum $JAR | cut -c1-64) xml_sha256=$(sha256sum $C/infinispan-sem-caches.xml | cut -c1-64)"
descer $D/compose-down-antes.txt
(cd $C && podman-compose -p p48 -f podman-compose.p48.yml up -d > $D/compose-up.txt 2>&1)
for i in $(seq 1 120); do [ "$(saude isn1 | cut -d' ' -f1)" = 3 ] && break; sleep 2; done
fase "cluster com $(saude isn1 | cut -d' ' -f1) nos"
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12901 --conferir "$ADMIN" --usuario admin \
  --dir $W/cluster/caches sessions counters sessions-async counters-async canario canario-async > $D/cria-caches.tsv 2> $D/cria-caches.err
fase "cria-caches rc=$?"
esperar_saude && fase "saude ok" || { fase "falha: saude"; exit 1; }
registrar_saude antes

"$JAVA" -cp "$(cygpath -w $JAR)" "$(cygpath -w $R/scripts/SondaP48.java)" $SRV admin $SENHA 2000 "$(cygpath -w $G)" f2 \
  > $D/sonda.out 2> $D/sonda.err &
SP=$!
while [ ! -f $G/pronto-para-f1 ]; do sleep 1; kill -0 $SP 2>/dev/null || { fase "falha: sonda saiu antes do F1"; exit 1; }; done
fase "F1 inicio: podman kill p48-isn2"
podman kill p48-isn2 > $D/f1-kill.txt 2>&1
sleep 5; registrar_saude f1-durante
sleep 55
fase "F1: podman start p48-isn2"
podman start p48-isn2 > $D/f1-start.txt 2>&1
esperar_saude && fase "F1 fim: saude ok" || fase "F1: saude nao voltou em 300 s"
sleep 10; registrar_saude f1-depois
touch $G/f1-feito

while [ ! -f $G/pronto-para-f2 ]; do sleep 1; kill -0 $SP 2>/dev/null || { fase "falha: sonda saiu antes da F2"; break; }; done
if [ -f $G/pronto-para-f2 ]; then
  fase "F2 inicio: isn3 isolado (nftables)"
  bash $R/scripts/particao-p48.sh cortar isn3 isn1 isn2 > $D/f2-corte.txt 2>&1
  sleep 10; registrar_saude f2-durante
  sleep 30
  bash $R/scripts/particao-p48.sh curar isn3 isn1 isn2 > $D/f2-cura.txt 2>&1
  fase "F2: curado; esperando a fusao"
  esperar_saude && fase "F2 fim: saude ok (fusao)" || fase "F2: saude nao voltou em 300 s"
  sleep 10; registrar_saude f2-depois
  touch $G/f2-feito
fi
wait $SP; fase "sonda exit=$?"
mkdir -p $D/servidores; for n in isn1 isn2 isn3; do podman logs -t p48-$n > $D/servidores/$n.log 2>&1; done
descer $D/compose-down.txt
fase "fim"
