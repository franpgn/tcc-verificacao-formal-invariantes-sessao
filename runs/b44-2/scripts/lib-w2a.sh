#!/bin/bash
# W-2a (B-44.2) item 9: funcoes do harness de integracao I-1..I-5 no cluster local w2a (portas 12501-12503).
# O harness pode usar podman e nftables; o codigo do controlador, nao (P-5). Nunca toca no cluster b443.
R=/c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2
I=$R/integracao
C=$R/cluster
JAR=/c/Users/Francesco/Development/tcc/wt-w2a/workload/target/session-workload-0.1.0-SNAPSHOT.jar
JAVA="$JAVA_HOME/bin/java"
AUTH="admin:infinispan"
SRV=127.0.0.1:12501,127.0.0.1:12502,127.0.0.1:12503
porta() { case $1 in isn1) echo 12501;; isn2) echo 12502;; isn3) echo 12503;; esac; }
agora_ns() { python -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }

# fase DIR TEXTO: linha com epoch-ns e UTC em DIR/fases.tsv
fase() { printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$2" >> "$1/fases.tsv"; echo "== $(utc) $2"; }

subir() {
  (cd $C && podman-compose -p w2a -f podman-compose.w2a.yml up -d > /dev/null 2>&1)
  for i in $(seq 1 90); do
    n=$(curl -s -m 2 --digest -u $AUTH http://127.0.0.1:12501/rest/v2/cache-managers/default/health 2>/dev/null \
        | python -c "import json,sys;print(json.load(sys.stdin)['cluster_health']['number_of_nodes'])" 2>/dev/null)
    [ "$n" = 3 ] && break; sleep 2
  done
  [ "$n" = 3 ] || { echo "cluster w2a nao formou 3 nos"; return 1; }
  bash $R/scripts/cria-caches.sh http://127.0.0.1:12501 $AUTH > /dev/null || return 1
  echo "cluster w2a: 3 nos, 4 caches"
}
descer() { (cd $C && podman-compose -p w2a -f podman-compose.w2a.yml down > /dev/null 2>&1); }
recriar() { descer; subir; }

# coordenador: rotulo isnN do coordenador atual
coordenador() {
  for n in isn1 isn2 isn3; do
    c=$(curl -s -m 2 --digest -u $AUTH http://127.0.0.1:$(porta $n)/rest/v2/container 2>/dev/null \
        | python -c "import json,sys;print(json.load(sys.stdin).get('coordinator'))" 2>/dev/null)
    [ "$c" = True ] && { echo $n; return; }
  done
}

# rebal DIR ROTULO: rebalancing_enabled (container) em cada no que responde
rebal() {
  for n in isn1 isn2 isn3; do
    v=$(curl -s -m 2 --digest -u $AUTH http://127.0.0.1:$(porta $n)/rest/v2/container 2>/dev/null \
        | python -c "import json,sys;d=json.load(sys.stdin);print(d.get('rebalancing_enabled'),d.get('coordinator'),d.get('cluster_size'))" 2>/dev/null)
    printf '%s\t%s\t%s\t%s\n' "$(utc)" "$2" "$n" "${v:-sem-resposta}" >> "$1/rebal.tsv"
  done
}

# carga DIR sync|async SEMENTE DURACAO MODO(off|shadow|active): em segundo plano, PID em DIR/pid
carga() {
  local d=$1 rep=$2 semente=$3 dur=$4 modo=$5
  mkdir -p $d; : > $d/marcadores.log
  local ctl=()
  if [ "$modo" != off ]; then
    ctl=(--controller $modo --controller-config "$(cygpath -w $I/controlador-integracao.json)"
         --controller-reference "$(cygpath -w $I/referencia-S3-$rep.json)" --controller-admin $SRV)
  fi
  ( "$JAVA" -jar "$JAR" --scenario S3 --replication $rep --reset-state on --rep 1 --duration $dur --seed $semente \
      --servers $SRV --rest-url http://127.0.0.1:12501 --csv-dir "$(cygpath -w $d)" \
      --marker "$(cygpath -w $d/marcadores.log)" --warmup-min-sec ${WARMUP:-15} "${ctl[@]}" > $d/stdout.log 2> $d/stderr.log
    echo "exit=$?" > $d/exit.txt ) &
  echo $! > $d/pid
}
esperar_carga() { while [ ! -f $1/exit.txt ]; do sleep 2; done; cat $1/exit.txt; }

# marcador DIR start|end NO
marcador() { echo "fault=F1 node=$3 event=$2 t_epoch_ns=$(agora_ns)" >> $1/marcadores.log; }

# logs DIR: logs dos tres conteineres (com instante) ao fim da execucao
logs() { for n in isn1 isn2 isn3; do podman logs -t w2a-$n > $1/servidor-$n.log 2>&1; done; }

# estado DIR ROTULO: status dos conteineres
estado() {
  for n in isn1 isn2 isn3; do
    printf '%s\t%s\t%s\t%s\n' "$(utc)" "$2" "$n" \
      "$(podman inspect w2a-$n --format '{{.State.Status}} exit={{.State.ExitCode}} fim={{.State.FinishedAt}}' 2>&1)" >> $1/conteineres.tsv
  done
}
