#!/bin/bash
# W-4 smoke (V-W4 secao 9): funcoes do harness no cluster local w4s (projeto Podman proprio, portas 12611-12613).
# O harness pode usar podman, nftables e Python (H2 liberada pelo coordenador em 05/11); o codigo da sonda, nao (P-5).
# A senha do cluster de laboratorio (admin:infinispan, a do podman-compose.yml do repositorio) vai so por stdin ao curl.
W=/c/Users/Francesco/Development/tcc/wt-w4
R=$W/runs/w4-smoke
C=$R/cluster
JAR=$W/workload/target/session-workload-0.1.0-SNAPSHOT.jar
CFG=$W/cluster/controlador/controlador-calibracao-s3.json
JAVA="$JAVA_HOME/bin/java"
SENHA=infinispan
SRV=127.0.0.1:12611,127.0.0.1:12612,127.0.0.1:12613
ADMIN=http://127.0.0.1:12611,http://127.0.0.1:12612,http://127.0.0.1:12613
porta() { case $1 in isn1) echo 12611;; isn2) echo 12612;; isn3) echo 12613;; esac; }
agora_ns() { python -I -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }

# fase DIR TEXTO: linha com epoch-ns e UTC em DIR/fases.tsv
fase() { mkdir -p "$1"; printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$2" >> "$1/fases.tsv"; echo "== $(utc) $2"; }

# curl com a credencial pela entrada padrao
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }

nos_saudaveis() {
  curl_adm "http://127.0.0.1:$(porta ${1:-isn1})/rest/v2/cache-managers/default/health" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['cluster_health']['number_of_nodes'])" 2>/dev/null
}

subir() {
  (cd $C && podman-compose -p w4s -f podman-compose.w4s.yml up -d > "$1/compose-up.txt" 2>&1)
  local n=""
  for i in $(seq 1 120); do
    n=$(nos_saudaveis isn1); [ "$n" = 3 ] && break; sleep 2
  done
  [ "$n" = 3 ] || { echo "cluster w4s nao formou 3 nos"; return 1; }
  for no in isn1 isn2 isn3; do
    curl_adm "http://127.0.0.1:$(porta $no)/rest/v2/cache-managers/default/health" > "$1/saude-subida-$no.json"
  done
  echo "cluster w4s: 3 nos"
}
descer() { (cd $C && podman-compose -p w4s -f podman-compose.w4s.yml down > "${1:-/dev/null}" 2>&1); }

# criar_caches DIR NOME: cria pela REST e confere nos tres nos (cria-caches.sh do W-4)
criar_caches() {
  CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12611 --conferir "$ADMIN" "$2" \
    > "$1/cria-caches.tsv" 2> "$1/cria-caches.err"
  local rc=$?; echo "rc=$rc" > "$1/cria-caches.rc"; echo "cria-caches $2: rc=$rc"; return $rc
}

# carga DIR sync|async SEMENTE DURACAO: S3 em shadow com a sonda de defasagem, em segundo plano
carga() {
  local d=$1 rep=$2 semente=$3 dur=$4
  mkdir -p $d; : > $d/marcadores.log
  local cmd=("$JAVA" -jar "$JAR" --scenario S3 --replication $rep --reset-state on --rep 1 --duration $dur --seed $semente
      --servers $SRV --rest-url http://127.0.0.1:12611 --csv-dir "$(cygpath -w $d)" --marker "$(cygpath -w $d/marcadores.log)"
      --warmup-min-sec 30 --controller shadow --controller-config "$(cygpath -w $CFG)" --controller-admin "$ADMIN")
  printf '%q ' "${cmd[@]}" > $d/comando.txt; echo >> $d/comando.txt
  ( "${cmd[@]}" > $d/stdout.log 2> $d/stderr.log; echo "exit=$?" > $d/exit.txt ) &
  echo $! > $d/pid
}
esperar_carga() { while [ ! -f $1/exit.txt ]; do sleep 2; done; cat $1/exit.txt; }

# origem DIR: origem_ns da meta de observaveis (espera a linha aparecer)
origem() {
  local f=$1/observaveis-rep-001.jsonl
  for i in $(seq 1 120); do
    [ -s $f ] && { head -1 $f | python -I -c "import json,sys;print(json.load(sys.stdin)['origem_ns'])"; return; }
    sleep 1
  done
  return 1
}
dormir_ate_ns() { local alvo=$1; local d; d=$(python -I -c "import time;print(max(0,($alvo-time.time_ns())/1e9))"); sleep "$d"; }

# corte e cura do isn3 (nftables no netns, como no B-44.1/W-2a)
cortar() { fase "$1" "corte-inicio isn3"; bash $R/scripts/particao-w4s.sh cortar isn3 isn1 isn2 > "$1/corte.txt" 2>&1; fase "$1" "corte-fim isn3 rc=$?"; }
curar() { fase "$1" "cura-inicio isn3"; bash $R/scripts/particao-w4s.sh curar isn3 isn1 isn2 > "$1/cura.txt" 2>&1; fase "$1" "cura-fim isn3 rc=$?"; }

# prova DIR CACHE ARQ_CHAVES ROTULO: ?action=distribution de cada chave, com os bytes Java da String em hexadecimal (R2)
prova() {
  local d=$1/prova-$4; mkdir -p $d
  # o Python do Windows termina as linhas com CR: tirar antes de usar (a primeira prova do bloco A falhou por isso)
  python -I $R/scripts/chaves.py "$3" | tr -d '\r' > $d/chaves.tsv
  while IFS=$'\t' read -r no chave; do
    # a REST so aceita o hexadecimal em minusculas depois do 0x (com maiusculas: "Invalid digit found in hex format!")
    local hex; hex=$(python -I $R/scripts/chaves.py --hex "$chave" | tr -d '\r' | tr A-F a-f)
    for alvo in isn1 isn2 isn3; do
      printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - -w '\n%{http_code}\n' \
        -H 'Key-Content-Type: application/octet-stream; encoding=hex' \
        "http://127.0.0.1:$(porta $alvo)/rest/v2/caches/$2/0x$hex?action=distribution" > "$d/dist-${no//[:.]/_}-via-$alvo.txt"
    done
  done < $d/chaves.tsv
  python -I $R/scripts/chaves.py --resumir-prova $d | tr -d '\r' > $d/resumo.tsv; cat $d/resumo.tsv
}

logs() { mkdir -p $1/servidores; for n in isn1 isn2 isn3; do podman logs -t w4s-$n > $1/servidores/$n.log 2>&1; done; }
estado() { for n in isn1 isn2 isn3; do printf '%s\t%s\t%s\n' "$(utc)" "$n" "$(podman inspect w4s-$n --format '{{.State.Status}} ip={{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' 2>&1)"; done >> $1/conteineres.tsv; }
