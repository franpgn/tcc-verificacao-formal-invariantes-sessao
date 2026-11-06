#!/bin/bash
# A1 smoke, teste dirigido do mecanismo (06/11), no mesmo cluster local a1 (portas 12801-12803) das execucoes com
# carga, rodado entre elas. Reproduz sem acaso o caso que a politica de fusao deve corrigir:
#   1. cria NSESS sessoes VALID pelo isn1 (antes do corte; espera a replicacao assincrona chegar aos donos);
#   2. corta o isn3 (particao-a1.sh) e, logo depois, faz o logout de cada sessao PELO isn3 com a mesma sequencia do
#      programa de carga (getWithMetadata + replaceWithVersion); o isn3 so confirma as de que e dono primario, e a
#      copia de backup na maioria fica VALID;
#   3. cura depois de CORTE_DUR s, espera o fim da CR do sessions-async (ISPN100013) em algum no e mais 10 s;
#   4. le as sessoes com logout confirmado pelo isn1, isn2 e isn3.
# Esperado: controle (PREFERRED_NON_NULL) -> as confirmadas voltam a VALID (I1); A1 -> ficam INVALID.
#
# Uso: bash mecanismo.sh NOME CONDICAO JAR [NSESS] [CORTE_DUR_S]     (CONDICAO: a1 | controle)
set -u
W=/c/Users/Francesco/Development/tcc/wt-a1
R=$W/runs/a1-smoke
C=$R/cluster
JAVA="$JAVA_HOME/bin/java"
export A1_SENHA=infinispan   # credencial de laboratorio do podman-compose.yml do repositorio
SENHA=$A1_SENHA
ADMIN=http://127.0.0.1:12801,http://127.0.0.1:12802,http://127.0.0.1:12803
CACHES="sessions counters sessions-async counters-async canario canario-async"
porta() { case $1 in isn1) echo 12801;; isn2) echo 12802;; isn3) echo 12803;; esac; }
agora_ns() { python -I -c "import time;print(time.time_ns())"; }
utc() { date -u +%FT%T.%3NZ; }
fase() { mkdir -p "$1"; printf '%s\t%s\t%s\n' "$(agora_ns)" "$(utc)" "$2" >> "$1/fases.tsv"; echo "== $(utc) $2"; }
curl_adm() { printf 'user = "admin:%s"\n' "$SENHA" | curl -s -m 5 --digest -K - "$@"; }
nos() {
  curl_adm "http://127.0.0.1:$(porta ${1:-isn1})/rest/v2/cache-managers/default/health" 2>/dev/null \
    | python -I -c "import json,sys;print(json.load(sys.stdin)['cluster_health']['number_of_nodes'])" 2>/dev/null
}
descer() { (cd $C && podman-compose -p a1 -f podman-compose.a1.yml down > "$1" 2>&1); }
cr_fim() { for n in isn1 isn2 isn3; do podman logs a1-$n 2>&1 | grep -q 'Context=sessions-async\]ISPN100013' && return 0; done; return 1; }

N=$1 COND=$2 JAR=$3 NSESS=${4:-60} CORTE_DUR=${5:-90}
case $COND in a1) CD=$R/caches-a1;; controle) CD=$R/caches-controle;; *) echo "condicao invalida: $COND" >&2; exit 1;; esac
D=$R/$N
[ -e "$D" ] && { echo "ja existe: $D" >&2; exit 1; }
mkdir -p "$D"
cd "$D"
PROG=$(cygpath -w $R/scripts/MecanismoA1.java)
CP=$(cygpath -w "$JAR")
fase "$D" "inicio $N condicao=$COND nsess=$NSESS corte_dur_s=$CORTE_DUR jar_sha256=$(sha256sum "$JAR" | cut -c1-64) extensao_sha256=$(sha256sum $C/tcc-extensao-servidor.jar | cut -c1-64) base=$(git -C $W rev-parse --short HEAD) sessions_async_sha256=$(sha256sum $CD/sessions-async.json | cut -c1-64) programa_sha256=$(sha256sum $R/scripts/MecanismoA1.java | cut -c1-64)"
descer "$D/compose-down-antes.txt"
(cd $C && podman-compose -p a1 -f podman-compose.a1.yml up -d > "$D/compose-up.txt" 2>&1)
n=""; for i in $(seq 1 90); do n=$(nos); [ "$n" = 3 ] && break; sleep 2; done
[ "$n" = 3 ] || { fase "$D" "falha: cluster a1 nao formou 3 nos"; descer "$D/compose-down.txt"; exit 1; }
fase "$D" "cluster com 3 nos"
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12801 --conferir "$ADMIN" --dir "$CD" \
  $CACHES > "$D/cria-caches.tsv" 2> "$D/cria-caches.err"
rc=$?; fase "$D" "cria-caches rc=$rc (dir=$(basename $CD))"
[ "$rc" = 0 ] || { descer "$D/compose-down.txt"; exit 1; }
for n in isn1 isn2 isn3; do
  curl_adm "http://127.0.0.1:$(porta $n)/rest/v2/caches/sessions-async?action=config" > "$D/config-sessions-async-$n.json"
done
"$JAVA" -cp "$CP" "$PROG" criar 127.0.0.1:12801 "$(cygpath -w "$D/sids.txt")" "$NSESS" > "$D/criar.txt" 2>&1
fase "$D" "criar: $(grep -v SLF4J "$D/criar.txt" | tail -1)"
sleep 5
fase "$D" "corte-inicio isn3"
bash $R/scripts/particao-a1.sh cortar isn3 isn1 isn2 > "$D/corte.txt" 2>&1
fase "$D" "corte-fim isn3 rc=$?"
"$JAVA" -cp "$CP" "$PROG" logout 127.0.0.1:12803 "$(cygpath -w "$D/sids.txt")" > "$D/logout.txt" 2>&1
fase "$D" "logout pelo isn3: $(grep -v SLF4J "$D/logout.txt" | tail -1)"
t_corte=$(grep 'corte-fim' "$D/fases.tsv" | cut -f1)
d=$(python -I -c "import time;print(max(0,($t_corte+$CORTE_DUR*10**9-time.time_ns())/1e9))"); sleep "$d"
fase "$D" "cura-inicio isn3"
bash $R/scripts/particao-a1.sh curar isn3 isn1 isn2 > "$D/cura.txt" 2>&1
fase "$D" "cura-fim isn3 rc=$?"
for i in $(seq 1 120); do cr_fim && break; sleep 3; done
if cr_fim; then fase "$D" "CR do sessions-async terminou"; else fase "$D" "CR do sessions-async NAO apareceu em 360 s"; fi
sleep 10
for n in isn1 isn2 isn3; do
  "$JAVA" -cp "$CP" "$PROG" ler 127.0.0.1:$(porta $n) "$(cygpath -w "$D/sids.txt.confirmados")" > "$D/ler-$n.txt" 2>&1
  fase "$D" "ler $n: $(grep RESUMO "$D/ler-$n.txt")"
done
mkdir -p "$D/servidores"
for n in isn1 isn2 isn3; do podman logs -t a1-$n > "$D/servidores/$n.log" 2>&1; done
descer "$D/compose-down.txt"
rm -f "$D/NUL"
fase "$D" "fim $N (cluster descido)"
