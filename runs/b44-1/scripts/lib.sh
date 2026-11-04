#!/bin/bash
# B-44.1: funcoes auxiliares das sondas administrativas (REST v2 do Infinispan 15.0.21.Final).
# Cada chamada grava uma linha em $R/chamadas.tsv:
#   utc  rotulo  metodo  url  http_code  time_total_s  auth  arquivo_corpo
R=/c/Users/Francesco/Development/tcc/wt-b44-1/runs/b44-1
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
