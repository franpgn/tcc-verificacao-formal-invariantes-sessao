#!/bin/bash
# A4 (06/11/2026): funcoes do harness no cluster local a4 (projeto Podman proprio, portas 12901-12903). Copia do
# lib-w2b.sh do smoke do W-2b (wt-ctrl-integ/runs/w2b-smoke/scripts) com os nomes a4, o XML sem caches e os seis
# caches criados pelo scripts/cria-caches.sh da base (como o orquestrador e o smoke do W-6). Nunca toca nos clusters
# de outros projetos (w6i, a1, ...).
# A senha do cluster de laboratorio (admin:infinispan, a do podman-compose.yml do repositorio) vai so por stdin ao
# curl e por variavel de ambiente aos programas.
W=/c/Users/Francesco/Development/tcc/wt-a4
R=$W/runs/a4-teste
C=$R/cluster
JAR=$W/workload/target/session-workload-0.1.0-SNAPSHOT.jar
JAVA="$JAVA_HOME/bin/java"
SENHA=infinispan
export SONDA_SENHA=$SENHA VIGIA_SENHA=$SENHA ACAO_SENHA=$SENHA
SRV=127.0.0.1:12901,127.0.0.1:12902,127.0.0.1:12903
ADMIN=http://127.0.0.1:12901,http://127.0.0.1:12902,http://127.0.0.1:12903
CACHES6="sessions counters sessions-async counters-async canario canario-async"
porta() { case $1 in isn1) echo 12901;; isn2) echo 12902;; isn3) echo 12903;; esac; }
agora_ns() { python -I -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }

# fase DIR TEXTO: linha com epoch-ns e UTC em DIR/fases.tsv
fase() { mkdir -p "$1"; printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$2" >> "$1/fases.tsv"; echo "== $(utc) $2"; }

# curl com a credencial pela entrada padrao
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }

nos_vistos() {
  curl_adm "http://127.0.0.1:$(porta ${1:-isn1})/rest/v2/container" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['cluster_size'])" 2>/dev/null
}
eh_coordenador() {
  curl_adm "http://127.0.0.1:$(porta $1)/rest/v2/container" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['coordinator'])" 2>/dev/null
}

subir() {
  (cd $C && podman-compose -p a4 -f podman-compose.a4.yml up -d > "$1/compose-up.txt" 2>&1)
  local n=""
  for i in $(seq 1 120); do
    n=$(nos_vistos isn1); [ "$n" = 3 ] && break; sleep 2
  done
  [ "$n" = 3 ] || { echo "cluster a4 nao formou 3 nos"; return 1; }
  for no in isn1 isn2 isn3; do
    curl_adm "http://127.0.0.1:$(porta $no)/rest/v2/cache-managers/default/health" > "$1/saude-subida-$no.json"
  done
  echo "cluster a4: 3 nos"
}
descer() { (cd $C && podman-compose -p a4 -f podman-compose.a4.yml down > "${1:-/dev/null}" 2>&1); }

criar_caches() {
  CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12901 --conferir "$ADMIN" $CACHES6 \
    > "$1/cria-caches.tsv" 2> "$1/cria-caches.err"
  local rc=$?; echo "rc=$rc" > "$1/cria-caches.rc"; echo "cria-caches: rc=$rc"; return $rc
}

# carga DIR SEMENTE DURACAO: S3 ASYNC sem controlador, em segundo plano (PID em DIR/pid)
carga() {
  local d=$1 semente=$2 dur=$3
  : > $d/marcadores.log
  local cmd=("$JAVA" -jar "$JAR" --scenario S3 --replication async --reset-state on --rep 1 --duration $dur --seed $semente
      --servers $SRV --rest-url http://127.0.0.1:12901 --csv-dir "$(cygpath -w $d)" --marker "$(cygpath -w $d/marcadores.log)"
      --warmup-min-sec 15 --controller off)
  printf '%q ' "${cmd[@]}" > $d/comando.txt; echo >> $d/comando.txt
  ( "${cmd[@]}" > $d/stdout.log 2> $d/stderr.log; echo "exit=$?" > $d/exit.txt ) &
  echo $! > $d/pid
}
esperar_carga() { while [ ! -f $1/exit.txt ]; do sleep 2; done; cat $1/exit.txt; }

# origem DIR: instante (epoch-ns) em que a linha "S3:" aparece no stdout da carga (depois do reset e da carga inicial)
origem() {
  for i in $(seq 1 240); do
    grep -q '^S3: ' $1/stdout.log 2>/dev/null && { agora_ns; return 0; }
    [ -f $1/exit.txt ] && return 1
    sleep 0.5
  done
  return 1
}
dormir_ate_ns() { local d; d=$(python -I -c "import time;print(max(0,($1-time.time_ns())/1e9))"); sleep "$d"; }

# marcador DIR start|end NO: formato B-29 (fault=F2), lido pela carga para a coluna de fase
marcador() { echo "fault=F2 node=$3 event=$2 t_epoch_ns=$(agora_ns)" >> $1/marcadores.log; }

# sondas Hot Rod pinadas: DIR NO INTERVALO_MS DURACAO_S (para no arquivo DIR/fim-sondas)
sonda() {
  local d=$1 no=$2 iv=$3 dur=$4
  ( "$JAVA" -cp "$(cygpath -w $JAR)" "$(cygpath -w $R/scripts/SondaA4.java)" $no 127.0.0.1:$(porta $no) \
      sessions-async,counters-async $iv $dur "$(cygpath -w $d/sonda-$no.tsv)" "$(cygpath -w $d/fim-sondas)" \
      > $d/sonda-$no.out 2>&1; echo "exit=$?" >> $d/sonda-$no.out ) &
}
vigia() { ( python -I $R/scripts/vigia_a4.py "$(cygpath -w $1)" "$(cygpath -w $1/fim-vigia)" 12901 12902 12903 > $1/vigia.out 2>&1 ) & }

# corte e cura do alvo (nftables no netns, como no B-44.1/W-2a/W-4/W-2b)
cortar() { bash $R/scripts/particao-a4.sh cortar $2 $3 $4 > "$1/corte.txt" 2>&1; }
curar() { bash $R/scripts/particao-a4.sh curar $2 $3 $4 > "$1/cura.txt" 2>&1; }

ignorados_agora() {
  for n in isn1 isn2 isn3; do
    printf '%s\t%s\t%s\t%s\n' "$(utc)" "$2" "$n" "$(curl_adm http://127.0.0.1:$(porta $n)/rest/v2/server/ignored-caches 2>&1)"
  done >> $1/ignorados-instantaneos.tsv
}
logs() { mkdir -p $1/servidores; for n in isn1 isn2 isn3; do podman logs -t a4-$n > $1/servidores/$n.log 2>&1; done; }
estado() { for n in isn1 isn2 isn3; do printf '%s\t%s\t%s\t%s\n' "$(utc)" "$2" "$n" "$(podman inspect a4-$n --format '{{.State.Status}} exit={{.State.ExitCode}} ip={{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' 2>&1)"; done >> $1/conteineres.tsv; }
