#!/bin/bash
# B-44.1: funcoes auxiliares das sondas administrativas (REST v2 do Infinispan 15.0.21.Final).
# Cada chamada grava uma linha em $R/chamadas.tsv:
#   utc  rotulo  metodo  url  http_code  time_total_s  auth  arquivo_corpo
R=/c/Users/Francesco/Development/tcc/wt-b44-1/runs/b44-1b
AUTH="admin:infinispan"
porta() { case $1 in isn1) echo 12441;; isn2) echo 12442;; isn3) echo 12443;; esac; }
# rest ROTULO NO METODO CAMINHO [auth=sim|nao]
rest() {
  local rot=$1 no=$2 m=$3 p=$4 au=${5:-sim}
  local out=$R/corpos/$rot.txt; mkdir -p $R/corpos
  local url="http://127.0.0.1:$(porta $no)$p"
  local a=(); [ "$au" = sim ] && a=(--digest -u "$AUTH")
  local res
  res=$(curl -s "${a[@]}" -X "$m" -o "$out" -w '%{http_code}\t%{time_total}' "$url")
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$(date -u +%FT%T.%3NZ)" "$rot" "$m" "$url" "$res" "$au" "corpos/$rot.txt" >> $R/chamadas.tsv
  printf '%s %s -> %s | ' "$rot" "$m $p" "$res"; head -c 400 "$out"; echo
}

JAR=/c/Users/Francesco/Development/tcc/wt-b44-1/workload/target/session-workload-0.1.0-SNAPSHOT.jar
SRV=127.0.0.1:12441,127.0.0.1:12442,127.0.0.1:12443
CACHES="sessions counters sessions-async counters-async"
fase() { printf '%s\t%s\n' "$(date -u +%FT%T.%3NZ)" "$*" >> $R/fases.tsv; echo "== $*"; }
# snapshot ROTULO: por no e por cache, time_since_start, rebalancing_enabled, availability
snapshot() {
  local rot=$1
  for n in isn1 isn2 isn3; do for c in $CACHES; do
    local p=$(porta $n)
    local det=$(curl -s --digest -u $AUTH "http://127.0.0.1:$p/rest/v2/caches/$c")
    local av=$(curl -s --digest -u $AUTH "http://127.0.0.1:$p/rest/v2/caches/$c?action=get-availability")
    printf '%s\t%s\t%s\t%s\t%s\n' "$(date -u +%FT%T.%3NZ)" "$rot" "$n" "$c" \
      "$(echo "$det" | python -c 'import json,sys
try:
  d=json.load(sys.stdin); print("tss=%s\treb=%s\trehash=%s" % (d["stats"].get("time_since_start"), d.get("rebalancing_enabled"), d.get("rehash_in_progress")))
except Exception as e: print("erro=%r" % e)')	av=$av" >> $R/snapshots.tsv
  done; done
}
# carga NOME sync|async SEMENTE DURACAO  (em segundo plano; PID em $R/carga/NOME/pid)
carga() {
  local d=$R/carga/$1; mkdir -p $d
  ( "$JAVA_HOME/bin/java" -jar "$JAR" --scenario S3 --replication $2 --reset-state on --rep 1 \
      --duration $4 --seed $3 --servers $SRV --rest-url http://127.0.0.1:12441 \
      --csv-dir "$(cygpath -w $d)" ${EXTRA:-} > $d/stdout.log 2> $d/stderr.log; echo "exit=$?" > $d/exit.txt ) &
  echo $! > $d/pid
}
# cli NO ROTULO "comandos..."  (CLI do proprio container, conectado ao no local)
cli() {
  local n=$1 rot=$2; shift 2
  mkdir -p $R/corpos
  printf '%s\n' "$@" | podman exec -i b441-$n bash -c 'cat > /tmp/b441.cli'
  local t0=$(date +%s.%N)
  MSYS_NO_PATHCONV=1 podman exec b441-$n /opt/infinispan/bin/cli.sh -c http://admin:infinispan@127.0.0.1:11222 -f /tmp/b441.cli > $R/corpos/cli-$rot.txt 2>&1
  local ec=$? t1=$(date +%s.%N)
  printf '%s\t%s\tCLI\t%s\t%s\t%s\tsim\tcorpos/cli-%s.txt\n' "$(date -u +%FT%T.%3NZ)" "cli-$rot" "$n: $*" "exit=$ec" "$(python -c "print(round($t1-$t0,3))")" "$rot" >> $R/chamadas.tsv
  echo "cli-$rot ($n) exit=$ec"; tail -c 600 $R/corpos/cli-$rot.txt; echo
}
setattr() { rest $1 $2 POST "/rest/v2/caches/$3?action=set-mutable-attribute&attribute-name=$4&attribute-value=$5"; }
getattr() { for n in isn1 isn2 isn3; do rest $1-$n $n GET "/rest/v2/caches/$2?action=get-mutable-attribute&attribute-name=$3"; done; }
# puts ROTULO CACHE N [no]: N PUTs REST paralelos (chaves b441-<rotulo>-i) no no indicado (isn1);
# grava utc_inicio, chave, http, time_total, corpo em $R/efeito/ROTULO.tsv
puts() {
  local rot=$1 c=$2 nn=$3 no=${4:-isn1}; mkdir -p $R/efeito
  local p=$(porta $no)
  for i in $(seq 1 $nn); do
    ( t=$(date -u +%FT%T.%3NZ); r=$(curl -s --digest -u $AUTH -X PUT -H 'Content-Type: text/plain' --data "v$i" \
        -w '\t%{http_code}\t%{time_total}' "http://127.0.0.1:$p/rest/v2/caches/$c/b441-$rot-$i" | tr '\n' ' ' | cut -c1-300)
      printf '%s\tb441-%s-%s\t%s\n' "$t" "$rot" "$i" "$r" >> $R/efeito/$rot.tsv ) &
  done
}
sobe() { # sobe COMPOSE: sobe o cluster e espera HEALTHY 3
  (cd $R/cluster && date -u +%FT%TZ >> $R/compose-up.txt && podman-compose -p b441 -f $1 up -d >> $R/compose-up.txt 2>&1)
  for i in $(seq 1 60); do s=$(curl -s --digest -u $AUTH http://127.0.0.1:12441/rest/v2/cache-managers/default/health 2>/dev/null | python -c 'import json,sys
try:
 d=json.load(sys.stdin); print(d["cluster_health"]["health_status"], d["cluster_health"]["number_of_nodes"])
except: print("X")'); [ "$s" = "HEALTHY 3" ] && break; sleep 3; done; echo "$s"; }
desce() { (cd $R/cluster && podman-compose -p b441 -f $1 down >> $R/compose-down.txt 2>&1); }
saude() { # saude NO: espera o no responder ou o container sair; imprime estado
  local p=$(porta $1); for i in $(seq 1 90); do
    st=$(podman ps -a --filter name=b441-$1 --format '{{.State}}'); [ "$st" = exited ] && { echo "exited"; return; }
    curl -s -m 2 --digest -u $AUTH http://127.0.0.1:$p/rest/v2/caches/sessions?action=get-availability 2>/dev/null | grep -q AVAILABLE && { echo "ok"; return; }
    sleep 2; done; echo "timeout"; }
debug() { for n in isn1 isn2 isn3; do rest dbg-$1-$n $n PUT "/rest/v2/logging/loggers/org.infinispan.CONFIG?level=DEBUG" >/dev/null; rest dbg2-$1-$n $n PUT "/rest/v2/logging/loggers/org.infinispan.globalstate?level=DEBUG" >/dev/null; done; }
salvalogs() { mkdir -p $R/servidores/$1; for n in isn1 isn2 isn3; do podman logs b441-$n > $R/servidores/$1/$n-stdout.log 2>&1; done; }
cfg () 
{ 
    echo "{\"$1\":{\"distributed-cache\":{\"owners\":\"2\",\"segments\":\"64\",\"mode\":\"$2\",\"statistics\":true,\"locking\":{\"concurrency-level\":\"1000\",\"acquire-timeout\":\"15000\",\"striping\":false},\"state-transfer\":{\"enabled\":true,\"timeout\":\"60000\"},\"partition-handling\":{\"when-split\":\"DENY_READ_WRITES\",\"merge-policy\":\"PREFERRED_NON_NULL\"}}}}"
}
cria () 
{ 
    mkdir -p corpos/json;
    cfg $3 $4 > corpos/json/$1.json;
    local h=();
    [ -n "$5" ] && h=(-H "flags: $5");
    local res=$(curl -s --digest -u $AUTH -X POST -H 'Content-Type: application/json' "${h[@]}" --data-binary @corpos/json/$1.json -o corpos/$1.txt -w '%{http_code}\t%{time_total}' "http://127.0.0.1:$(porta $2)/rest/v2/caches/$3");
    printf '%s\t%s\tPOST\t%s\t%s\tsim\tcorpos/%s.txt\n' "$(date -u +%FT%T.%3NZ)" "$1" "http://127.0.0.1:$(porta $2)/rest/v2/caches/$3 (corpo corpos/json/$1.json${5:+, flags: $5})" "$res" "$1" >> chamadas.tsv;
    echo "$1 -> $res $(head -c 200 corpos/$1.txt)"
}
limpa () 
{ 
    MSYS_NO_PATHCONV=1 podman cp "$V" b441-$1:/opt/infinispan/server/data/caches.xml
}
cria2 () 
{ 
    mkdir -p corpos/json;
    echo "{\"distributed-cache\":{\"owners\":\"2\",\"segments\":\"64\",\"mode\":\"$4\",${5:+\"remote-timeout\":\"$5\",}\"statistics\":true,\"locking\":{\"concurrency-level\":\"1000\",\"acquire-timeout\":\"15000\",\"striping\":false},\"state-transfer\":{\"enabled\":true,\"timeout\":\"60000\"},\"partition-handling\":{\"when-split\":\"DENY_READ_WRITES\",\"merge-policy\":\"PREFERRED_NON_NULL\"}}}" > corpos/json/$1.json;
    local res=$(curl -s --digest -u $AUTH -X POST -H 'Content-Type: application/json' --data-binary @corpos/json/$1.json -o corpos/$1.txt -w '%{http_code}\t%{time_total}' "http://127.0.0.1:$(porta $2)/rest/v2/caches/$3");
    printf '%s\t%s\tPOST\t%s\t%s\tsim\tcorpos/%s.txt\n' "$(date -u +%FT%T.%3NZ)" "$1" "http://127.0.0.1:$(porta $2)/rest/v2/caches/$3 (corpo corpos/json/$1.json)" "$res" "$1" >> chamadas.tsv;
    echo "$1 -> $res $(head -c 200 corpos/$1.txt)"
}
# (redefinicao final) limpa NO: copia um caches.xml vazio para o container parado
limpa() { MSYS_NO_PATHCONV=1 podman cp "$(cygpath -w $R/cluster/caches-vazio.xml)" b441-$1:/opt/infinispan/server/data/caches.xml; }
# amostra ROT NO: por cache (sessions, counters): N (size), soma das copias por no, faltantes = 2N - soma,
# copias por no, rebalancing_enabled, disponibilidade. Grava em $R/efeito/R-amostras.tsv
amostra() { local p=$(porta $2)
  for c in sessions counters; do
    local n=$(curl -s -m 5 --digest -u $AUTH "http://127.0.0.1:$p/rest/v2/caches/$c?action=size")
    local d=$(curl -s -m 5 --digest -u $AUTH "http://127.0.0.1:$p/rest/v2/caches/$c?action=distribution")
    local reb=$(curl -s -m 5 --digest -u $AUTH "http://127.0.0.1:$p/rest/v2/caches/$c" | python -c 'import json,sys
try: print(json.load(sys.stdin).get("rebalancing_enabled"))
except Exception: print("?")')
    local av=$(curl -s -m 5 --digest -u $AUTH "http://127.0.0.1:$p/rest/v2/caches/$c?action=get-availability")
    local ts=$(date -u +%FT%T.%3NZ)
    D="$d" N="$n" TS="$ts" ROT="$1" C="$c" REB="$reb" AV="$av" python -c '
import json,os
e=os.environ
try:
    d=json.loads(e["D"]); s=sum(x["total_entries"] for x in d); n=int(e["N"])
    nos=";".join(x["node_name"].split("-")[0]+"="+str(x["total_entries"]) for x in d)
    print("\t".join([e["TS"],e["ROT"],e["C"],str(n),str(s),str(2*n-s),nos,e["REB"],e["AV"]]))
except Exception as x:
    print("\t".join([e["TS"],e["ROT"],e["C"],"?","?","?",repr(x)[:60],e["REB"],e["AV"]]))' >> $R/efeito/R-amostras.tsv
  done; }
# amostrador ROT NO SEGUNDOS: amostra em laco
amostrador() { local fim=$(( $(date +%s)+$3 )); while [ $(date +%s) -lt $fim ]; do amostra $1 $2; sleep 1; done; }
