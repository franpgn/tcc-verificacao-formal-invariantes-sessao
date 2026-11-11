#!/bin/bash
# W-2b smoke (decisao 7 do coordenador, 05/11): S3 ASYNC de 120 s com o controlador e corte do isn3, na ponta da
# fix/p5-caminho-critico, para comparar stderr, trilha JSONL e linhas_perdidas com o I-3b (wt-w4/runs/i3b/, 17f2bd4,
# 300 s). Cluster local w2b (portas 12701-12703), mesmo XML, mesmo arquivo da calibracao, mesma referencia inerte e
# mesmo corte por nftables do I-3b. Uma encarnacao por execucao.
#
# Uso: w2b.sh NOME MODO(active|shadow) TETO_CONVERGENCIA_S SEMENTE JAR
#   ASYNC, S3, --warmup-min-sec 30, --duration 120, --convergence; corte do isn3 de origem+30 s ate o fim da carga
#   (sem religar: o no parado pelo AC-5 fica parado, como o ativo-sem-religar do I-3b).
source /c/Users/Francesco/Development/tcc/wt-w2b/runs/w2b-smoke/scripts/lib-w2b.sh
CFG=$R/config/controlador-calibracao-s3.json
REF=$R/config/referencia-S3-async.json

criar_caches() {
  CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12701 --conferir "$ADMIN" "$2" \
    > "$1/cria-caches.tsv" 2> "$1/cria-caches.err"
  local rc=$?; echo "rc=$rc" > "$1/cria-caches.rc"; echo "cria-caches $2: rc=$rc"; return $rc
}

carga() {
  local d=$1 modo=$2 semente=$3 teto=$4 jar=$5
  mkdir -p $d; : > $d/marcadores.log
  local cmd=("$JAVA" -jar "$jar" --scenario S3 --replication async --reset-state on --rep 1 --duration 120 --seed $semente
      --servers $SRV --rest-url http://127.0.0.1:12701 --csv-dir "$(cygpath -w $d)" --marker "$(cygpath -w $d/marcadores.log)"
      --warmup-min-sec 30 --convergence --convergence-timeout-sec $teto
      --controller $modo --controller-config "$(cygpath -w $CFG)" --controller-reference "$(cygpath -w $REF)"
      --controller-admin "$ADMIN")
  printf '%q ' "${cmd[@]}" > $d/comando.txt; echo >> $d/comando.txt
  ( "${cmd[@]}" > $d/stdout.log 2> $d/stderr.log; echo "exit=$?" > $d/exit.txt ) &
  echo $! > $d/pid
}

N=$1 MODO=$2 TETO=$3 SEMENTE=$4 JAR=$5
D=$R/$N
mkdir -p $D
fase $D "inicio $N modo=$MODO teto_convergencia_s=$TETO semente=$SEMENTE jar_sha256=$(sha256sum $JAR | cut -c1-64) cfg_sha256=$(sha256sum $CFG | cut -c1-64) base=$(git -C $W rev-parse --short HEAD)"
descer $D/compose-down-antes.txt
subir $D || { fase $D "falha: cluster nao subiu"; exit 1; }
estado $D
criar_caches $D canario-async || { fase $D "falha: cria-caches"; descer $D/compose-down.txt; exit 1; }
carga $D $MODO $SEMENTE $TETO $JAR
o=$(origem $D); fase $D "origem_ns=$o"
dormir_ate_ns $((o + 30000000000)); cortar $D
while ! grep -q "^Controlador: modo=" $D/stdout.log 2>/dev/null && [ ! -f $D/exit.txt ]; do sleep 1; done
fase $D "fim-da-carga-visto (linha Controlador:)"
curar $D
fase $D "carga $(esperar_carga $D)"
wait
estado $D
podman inspect w2b-isn3 --format '{{json .State}}' > $D/isn3-state.json 2>&1
logs $D
descer $D/compose-down.txt
fase $D "fim $N"
