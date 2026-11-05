#!/bin/bash
# W-2b smoke (decisao 7 do coordenador, 05/11, janela H2): funcoes do harness no cluster local w2b (projeto Podman
# proprio, portas 12701-12703). Copia do lib-w4s.sh do smoke do W-4 com os nomes w2b. O harness pode usar podman,
# nftables e curl; o codigo do controlador, nao (P-5).
# A senha do cluster de laboratorio (admin:infinispan, a do podman-compose.yml do repositorio) vai so por stdin ao curl.
W=/c/Users/Francesco/Development/tcc/wt-w2b
R=$W/runs/w2b-smoke
C=$R/cluster
JAVA="$JAVA_HOME/bin/java"
SENHA=infinispan
SRV=127.0.0.1:12701,127.0.0.1:12702,127.0.0.1:12703
ADMIN=http://127.0.0.1:12701,http://127.0.0.1:12702,http://127.0.0.1:12703
porta() { case $1 in isn1) echo 12701;; isn2) echo 12702;; isn3) echo 12703;; esac; }
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
  (cd $C && podman-compose -p w2b -f podman-compose.w2b.yml up -d > "$1/compose-up.txt" 2>&1)
  local n=""
  for i in $(seq 1 120); do
    n=$(nos_saudaveis isn1); [ "$n" = 3 ] && break; sleep 2
  done
  [ "$n" = 3 ] || { echo "cluster w2b nao formou 3 nos"; return 1; }
  for no in isn1 isn2 isn3; do
    curl_adm "http://127.0.0.1:$(porta $no)/rest/v2/cache-managers/default/health" > "$1/saude-subida-$no.json"
  done
  echo "cluster w2b: 3 nos"
}
descer() { (cd $C && podman-compose -p w2b -f podman-compose.w2b.yml down > "${1:-/dev/null}" 2>&1); }

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

# corte e cura do isn3 (nftables no netns, como no B-44.1/W-2a/W-4)
cortar() { fase "$1" "corte-inicio isn3"; bash $R/scripts/particao-w2b.sh cortar isn3 isn1 isn2 > "$1/corte.txt" 2>&1; fase "$1" "corte-fim isn3 rc=$?"; }
curar() { fase "$1" "cura-inicio isn3"; bash $R/scripts/particao-w2b.sh curar isn3 isn1 isn2 > "$1/cura.txt" 2>&1; fase "$1" "cura-fim isn3 rc=$?"; }

logs() { mkdir -p $1/servidores; for n in isn1 isn2 isn3; do podman logs -t w2b-$n > $1/servidores/$n.log 2>&1; done; }
estado() { for n in isn1 isn2 isn3; do printf '%s\t%s\t%s\n' "$(utc)" "$n" "$(podman inspect w2b-$n --format '{{.State.Status}} ip={{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' 2>&1)"; done >> $1/conteineres.tsv; }
