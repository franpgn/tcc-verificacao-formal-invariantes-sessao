#!/bin/bash
# Smoke da integracao do W-6 (05/11, pedido do coordenador, item 5): cluster local w6i (portas 12801-12803, projeto
# Podman proprio) com o XML SEM caches da ponta (cluster/infinispan-sem-caches.xml), os seis caches criados pelo
# scripts/cria-caches.sh da ponta (leitura de volta byte a byte nos tres nos) e uma releitura independente no formato
# da camada 3 do orquestrador (run-campanha-s3.sh, camada3): rebalancing_enabled, get-availability e ?action=config
# de cada cache em cada no, mais a lista de caches de cada no. Harness no estilo do smoke do W-2b
# (wt-w2b/runs/w2b-smoke/scripts/lib-w2b.sh). O codigo do repositorio nao muda.
#
# Uso: bash smoke-caches.sh
# Saida: runs/w6-int-smoke/caches/ (fases.tsv, conteineres.tsv, cria-caches.{tsv,err,rc}, camada3.tsv, caches-isnN.json,
# config-isnN-<cache>.json, saude-isnN.json, servidores/*.log). Desce o cluster no fim.
set -u
W=/c/Users/Francesco/Development/tcc/wt-ctrl-integ
R=$W/runs/w6-int-smoke
C=$R/cluster
D=$R/caches
SENHA=infinispan   # credencial de laboratorio do podman-compose.yml do repositorio; vai so por stdin ao curl
CACHES="sessions counters sessions-async counters-async canario canario-async"
porta() { case $1 in isn1) echo 12801;; isn2) echo 12802;; isn3) echo 12803;; esac; }
utc() { date -u +%FT%T.%3NZ; }
fase() { printf '%s\t%s\n' "$(utc)" "$1" >> "$D/fases.tsv"; echo "== $(utc) $1"; }
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }
nos() {
  curl_adm "http://127.0.0.1:$(porta isn1)/rest/v2/cache-managers/default/health" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['cluster_health']['number_of_nodes'])" 2>/dev/null
}
descer() { (cd $C && podman-compose -p w6i -f podman-compose.w6i.yml down > "$1" 2>&1); }
estado() {
  for n in isn1 isn2 isn3; do
    printf '%s\t%s\t%s\n' "$(utc)" "$n" "$(podman inspect w6i-$n --format '{{.State.Status}} {{.State.StartedAt}}' 2>&1)"
  done >> "$D/conteineres.tsv"
}

mkdir -p "$D"
fase "inicio base=$(git -C $W rev-parse --short HEAD) xml_sha256=$(sha256sum $C/infinispan-sem-caches.xml | cut -c1-64) compose_sha256=$(sha256sum $C/podman-compose.w6i.yml | cut -c1-64) cria_caches_sha256=$(sha256sum $W/scripts/cria-caches.sh | cut -c1-64)"
descer "$D/compose-down-antes.txt"
fase "compose up"
(cd $C && podman-compose -p w6i -f podman-compose.w6i.yml up -d > "$D/compose-up.txt" 2>&1)
n=""
for i in $(seq 1 90); do n=$(nos); [ "$n" = 3 ] && break; sleep 2; done
[ "$n" = 3 ] || { fase "falha: cluster w6i nao formou 3 nos"; estado; descer "$D/compose-down.txt"; exit 1; }
fase "cluster com 3 nos"
estado
for no in isn1 isn2 isn3; do
  curl_adm "http://127.0.0.1:$(porta $no)/rest/v2/cache-managers/default/health" > "$D/saude-$no.json"
  curl_adm "http://127.0.0.1:$(porta $no)/rest/v2/caches" > "$D/caches-antes-$no.json"
done
fase "cria-caches (seis JSON)"
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12801 \
  --conferir http://127.0.0.1:12801,http://127.0.0.1:12802,http://127.0.0.1:12803 $CACHES \
  > "$D/cria-caches.tsv" 2> "$D/cria-caches.err"
rc=$?; echo "rc=$rc" > "$D/cria-caches.rc"
fase "cria-caches rc=$rc"
# releitura independente, no formato da camada 3 do orquestrador
printf 'no\titem\tesperado\tobtido\tresultado\n' > "$D/camada3.tsv"
falhas=0
for no in isn1 isn2 isn3; do
  u="http://127.0.0.1:$(porta $no)"
  curl_adm "$u/rest/v2/caches" > "$D/caches-$no.json"
  curl_adm "$u/rest/v2/container" > "$D/container-$no.json"
  if grep -q '"rebalancing_enabled":true' "$D/container-$no.json"; then r=OK; else r=DIVERGE; falhas=$((falhas+1)); fi
  printf '%s\trebalancing_enabled\ttrue\t%s\t%s\n' "$no" "$(grep -o '"rebalancing_enabled":[a-z]*' "$D/container-$no.json")" "$r" >> "$D/camada3.tsv"
  for c in $CACHES; do
    a="$(curl_adm "$u/rest/v2/caches/$c?action=get-availability" | tr -d '\r\n')"
    if [ "$a" = AVAILABLE ]; then r=OK; else r=DIVERGE; falhas=$((falhas+1)); fi
    printf '%s\t%s:disponibilidade\tAVAILABLE\t%s\t%s\n' "$no" "$c" "$a" "$r" >> "$D/camada3.tsv"
    curl_adm "$u/rest/v2/caches/$c?action=config" > "$D/config-$no-$c.json"
    esperado="{\"$c\":$(tr -d '\r\n' < $W/cluster/caches/$c.json)}"
    obtido="$(tr -d '\r\n' < "$D/config-$no-$c.json")"
    if [ "$obtido" = "$esperado" ]; then r=OK; else r=DIVERGE; falhas=$((falhas+1)); fi
    printf '%s\t%s:config\t%s\t%s\t%s\n' "$no" "$c" "$(printf '%s' "$esperado" | sha256sum | cut -c1-16)" "$(printf '%s' "$obtido" | sha256sum | cut -c1-16)" "$r" >> "$D/camada3.tsv"
  done
done
fase "camada3 releitura: $falhas divergencias"
estado
mkdir -p "$D/servidores"
for n in isn1 isn2 isn3; do podman logs -t w6i-$n > "$D/servidores/$n.log" 2>&1; done
descer "$D/compose-down.txt"
fase "fim (cluster descido)"
exit $(( rc != 0 || falhas != 0 ))
