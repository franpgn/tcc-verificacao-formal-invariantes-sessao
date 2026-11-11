#!/bin/bash
# P1-IC (06/11/2026): funcoes do smoke do controlador com o AC-7 no cluster local p1ic (portas 13001-13003, projeto
# Podman proprio, log k8s-file pelo G-5). Copia do lib-a4.sh do A4 (wt-a4/runs/a4-teste/scripts) com os nomes p1ic e
# a carga S3 com o controlador. A senha do cluster de laboratorio (admin:infinispan, a do podman-compose.yml do
# repositorio) vai so por stdin ao curl e por variavel de ambiente aos programas.
W=/c/Users/Francesco/Development/tcc/wt-p1ic
R=$W/runs/p1ic-smoke
C=$R/cluster
JAR=$W/workload/target/session-workload-0.1.0-SNAPSHOT.jar
CFG=$W/cluster/controlador/controlador-calibracao-s3.json
REF=$R/config/referencia-S3-async.json
JAVA="$JAVA_HOME/bin/java"
SENHA=infinispan
export SONDA_SENHA=$SENHA VIGIA_SENHA=$SENHA SMOKE_SENHA=$SENHA
SRV=127.0.0.1:13001,127.0.0.1:13002,127.0.0.1:13003
ADMIN=http://127.0.0.1:13001,http://127.0.0.1:13002,http://127.0.0.1:13003
CACHES6="sessions counters sessions-async counters-async canario canario-async"
# carga L+ do piloto de violacoes (wt-plano-ctrl/docs/roteiro-piloto-violacoes-2026-11-06.md, tabela L+), com a
# varredura final (--sweep) e a sonda de replay (--s3-replay-probe)
LMAIS=(--s3-users 40 --s3-think-ms 500 --s3-session-median-sec 10 --s3-logout-prob 0.9 --s3-admin-mean-sec 5
       --sweep --s3-replay-probe)
porta() { case $1 in isn1) echo 13001;; isn2) echo 13002;; isn3) echo 13003;; esac; }
agora_ns() { python -I -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }
fase() { mkdir -p "$1"; printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$2" >> "$1/fases.tsv"; echo "== $(utc) $2"; }
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }
nos_vistos() {
  curl_adm "http://127.0.0.1:$(porta ${1:-isn1})/rest/v2/cache-managers/default/health" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['cluster_health']['number_of_nodes'])" 2>/dev/null
}
eh_coordenador() {
  curl_adm "http://127.0.0.1:$(porta $1)/rest/v2/container" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['coordinator'])" 2>/dev/null
}
subir() {
  (cd $C && podman-compose -p p1ic -f podman-compose.p1ic.yml up -d > "$1/compose-up.txt" 2>&1)
  local n=""
  for i in $(seq 1 120); do n=$(nos_vistos isn1); [ "$n" = 3 ] && break; sleep 2; done
  [ "$n" = 3 ] || { echo "cluster p1ic nao formou 3 nos"; return 1; }
  echo "cluster p1ic: 3 nos"
}
descer() { (cd $C && podman-compose -p p1ic -f podman-compose.p1ic.yml down > "${1:-/dev/null}" 2>&1); }
criar_caches() {
  CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:13001 --conferir "$ADMIN" $CACHES6 \
    > "$1/cria-caches.tsv" 2> "$1/cria-caches.err"
  local rc=$?; echo "rc=$rc" > "$1/cria-caches.rc"; echo "cria-caches: rc=$rc"; return $rc
}
# carga DIR SEMENTE DURACAO MODO: S3 ASYNC com o controlador, carga L+, varredura e convergencia, em segundo plano
carga() {
  local d=$1 semente=$2 dur=$3 modo=$4
  : > $d/marcadores.log
  local cmd=("$JAVA" -jar "$JAR" --scenario S3 --replication async --reset-state on --rep 1 --duration $dur --seed $semente
      --servers $SRV --rest-url http://127.0.0.1:13001 --csv-dir "$(cygpath -w $d)" --marker "$(cygpath -w $d/marcadores.log)"
      --warmup-min-sec 30 --convergence --convergence-timeout-sec 120 --rejoin-timeout-sec 120
      --controller $modo --controller-config "$(cygpath -w $CFG)" --controller-reference "$(cygpath -w $REF)"
      --controller-admin "$ADMIN" "${LMAIS[@]}")
  printf '%q ' "${cmd[@]}" > $d/comando.txt; echo >> $d/comando.txt
  ( "${cmd[@]}" > $d/stdout.log 2> $d/stderr.log; echo "exit=$?" > $d/exit.txt ) &
  echo $! > $d/pid
}
esperar_carga() { while [ ! -f $1/exit.txt ]; do sleep 2; done; cat $1/exit.txt; }
# origem DIR: origem_ns da meta do observaveis (inicio da medicao)
origem() {
  local f=$1/observaveis-rep-001.jsonl
  for i in $(seq 1 180); do
    [ -s $f ] && { head -1 $f | python -I -c "import json,sys;print(json.load(sys.stdin)['origem_ns'])"; return; }
    [ -f $1/exit.txt ] && return 1
    sleep 1
  done
  return 1
}
dormir_ate_ns() { local d; d=$(python -I -c "import time;print(max(0,($1-time.time_ns())/1e9))"); sleep "$d"; }
marcador() { echo "fault=F2 node=$3 event=$2 t_epoch_ns=$(agora_ns)" >> $1/marcadores.log; }
sonda() {
  local d=$1 no=$2 iv=$3
  ( "$JAVA" -cp "$(cygpath -w $JAR)" "$(cygpath -w $R/scripts/SondaA4.java)" $no 127.0.0.1:$(porta $no) \
      sessions-async,counters-async $iv 1200 "$(cygpath -w $d/sonda-$no.tsv)" "$(cygpath -w $d/fim-sondas)" \
      > $d/sonda-$no.out 2>&1; echo "exit=$?" >> $d/sonda-$no.out ) &
}
vigia() { ( python -I $R/scripts/vigia_p1ic.py "$(cygpath -w $1)" "$(cygpath -w $1/fim-vigia)" 13001 13002 13003 > $1/vigia.out 2>&1 ) & }
cortar() { bash $R/scripts/particao-p1ic.sh cortar $2 $3 $4 > "$1/corte.txt" 2>&1; }
curar() { bash $R/scripts/particao-p1ic.sh curar $2 $3 $4 > "$1/cura.txt" 2>&1; }
logs() { mkdir -p $1/servidores; for n in isn1 isn2 isn3; do podman logs -t p1ic-$n > $1/servidores/$n.log 2>&1; done; }
estado() { for n in isn1 isn2 isn3; do printf '%s\t%s\t%s\t%s\n' "$(utc)" "$2" "$n" "$(podman inspect p1ic-$n --format '{{.State.Status}} exit={{.State.ExitCode}}' 2>&1)"; done >> $1/conteineres.tsv; }
