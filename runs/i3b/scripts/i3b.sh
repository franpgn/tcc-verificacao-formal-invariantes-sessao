#!/bin/bash
# I-3b (condicao da V-6 para liberar a calibracao): P1 de ponta a ponta na versao integrada
# feature/controlador-integracao = 17f2bd4 (jar construido numa worktree temporaria detached, sem commit nela).
# Cluster local w4s (portas 12611-12613, compose e corte por nftables do smoke do W-4). Uma encarnacao por execucao.
#
# Uso: i3b.sh NOME MODO(active|shadow) RELIGAR(sim|nao) TETO_CONVERGENCIA_S SEMENTE
#   ASYNC, S3, --warmup-min-sec 30, --duration 300, --convergence; corte do isn3 de origem+60 s a origem+150 s.
#   RELIGAR=sim: 30 s depois de a carga acabar (linha "Controlador:" no stdout), podman start do isn3 a mao, como o W-6.
source /c/Users/Francesco/Development/tcc/wt-w4/runs/w4-smoke/scripts/lib-w4s.sh
INT=/c/Users/Francesco/Development/tcc/wt-i3b-tmp
JAR=$INT/workload/target/session-workload-0.1.0-SNAPSHOT.jar
CFG=$INT/cluster/controlador/controlador-calibracao-s3.json
I=/c/Users/Francesco/Development/tcc/wt-w4/runs/i3b
REF=$I/config/referencia-S3-async.json

# o cria-caches.sh e os JSON da versao integrada
criar_caches_int() {
  CRIA_CACHES_SENHA=$SENHA bash $INT/scripts/cria-caches.sh --url http://127.0.0.1:12611 --conferir "$ADMIN" "$2" \
    > "$1/cria-caches.tsv" 2> "$1/cria-caches.err"
  local rc=$?; echo "rc=$rc" > "$1/cria-caches.rc"; echo "cria-caches $2: rc=$rc"; return $rc
}

carga_int() {
  local d=$1 modo=$2 semente=$3 teto=$4
  mkdir -p $d; : > $d/marcadores.log
  local cmd=("$JAVA" -jar "$JAR" --scenario S3 --replication async --reset-state on --rep 1 --duration 300 --seed $semente
      --servers $SRV --rest-url http://127.0.0.1:12611 --csv-dir "$(cygpath -w $d)" --marker "$(cygpath -w $d/marcadores.log)"
      --warmup-min-sec 30 --convergence --convergence-timeout-sec $teto
      --controller $modo --controller-config "$(cygpath -w $CFG)" --controller-reference "$(cygpath -w $REF)"
      --controller-admin "$ADMIN")
  printf '%q ' "${cmd[@]}" > $d/comando.txt; echo >> $d/comando.txt
  ( "${cmd[@]}" > $d/stdout.log 2> $d/stderr.log; echo "exit=$?" > $d/exit.txt ) &
  echo $! > $d/pid
}

# saude do isn3 a cada ~250 ms (sem credencial, como a leitura de volta do AC-5), por DUR segundos
sonda_saude_isn3() {
  local d=$1 dur=$2 fim=$(( $(date +%s) + $2 ))
  while [ "$(date +%s)" -lt $fim ]; do
    local c; c=$(curl -s -m 1 -o /dev/null -w '%{http_code}' http://127.0.0.1:12613/rest/v2/container/health/status)
    printf '%s\t%s\n' "$(date +%s%N)" "$c" >> $d/saude-isn3.tsv
    sleep 0.25
  done
}

N=$1 MODO=$2 RELIGAR=$3 TETO=$4 SEMENTE=$5
D=$I/$N
mkdir -p $D
fase $D "inicio $N modo=$MODO religar=$RELIGAR teto_convergencia_s=$TETO semente=$SEMENTE jar_sha256=$(sha256sum $JAR | cut -c1-64) cfg_sha256=$(sha256sum $CFG | cut -c1-64) base=$(git -C $INT rev-parse --short HEAD)"
descer $D/compose-down-antes.txt
subir $D || { fase $D "falha: cluster nao subiu"; exit 1; }
estado $D
criar_caches_int $D canario-async || { fase $D "falha: cria-caches"; descer $D/compose-down.txt; exit 1; }
carga_int $D $MODO $SEMENTE $TETO
o=$(origem $D); fase $D "origem_ns=$o"
dormir_ate_ns $((o + 60000000000)); cortar $D
sonda_saude_isn3 $D 120 &
dormir_ate_ns $((o + 150000000000)); curar $D
if [ "$RELIGAR" = sim ]; then
  while ! grep -q "^Controlador: modo=" $D/stdout.log 2>/dev/null && [ ! -f $D/exit.txt ]; do sleep 1; done
  fase $D "fim-da-carga-visto (linha Controlador:)"
  sleep 30
  fase $D "religar-inicio podman start w4s-isn3"
  podman start w4s-isn3 > $D/religar.txt 2>&1; fase $D "religar-fim rc=$?"
  for i in $(seq 1 60); do
    n=$(nos_saudaveis isn1); [ "$n" = 3 ] && break; sleep 2
  done
  fase $D "religar-3-nos n=$n"
fi
fase $D "carga $(esperar_carga $D)"
wait
estado $D
podman inspect w4s-isn3 --format '{{json .State}}' > $D/isn3-state.json 2>&1
logs $D
descer $D/compose-down.txt
fase $D "fim $N"
