#!/bin/bash
# W-4 smoke (V-W4 secao 9), um bloco numa encarnacao propria do cluster w4s.
#   bloco.sh A   -> ASYNC: S0 sem falha (300 s), prova REST, prova de novo, S1 com corte do isn3 (origem+60 s, 90 s)
#   bloco.sh B   -> SYNC:  S2a sem falha (120 s), prova REST, S2 com corte do isn3 (origem+60 s, 90 s)
# Todas as cargas: S3, --controller shadow, --warmup-min-sec 30, controlador-calibracao-s3.json.
source /c/Users/Francesco/Development/tcc/wt-w4/runs/w4-smoke/scripts/lib-w4s.sh
B=$1
case $B in
  A) REP=async; CACHE=canario-async; SEM=S0; COM=S1; DSEM=300; SSEM=61001; SCOM=61002 ;;
  B) REP=sync;  CACHE=canario;       SEM=S2a; COM=S2; DSEM=120; SSEM=62001; SCOM=62002 ;;
  *) echo "uso: bloco.sh A|B"; exit 2 ;;
esac
D=$R/$B
mkdir -p $D
fase $D "bloco $B inicio (rep=$REP cache=$CACHE jar_sha256=$(sha256sum $JAR | cut -c1-64))"
descer $D/compose-down-antes.txt
subir $D || { fase $D "falha: cluster nao subiu"; exit 1; }
estado $D
criar_caches $D $CACHE || { fase $D "falha: cria-caches"; descer $D/compose-down.txt; exit 1; }

# execucao sem falha
fase $D/$SEM "$SEM inicio"
carga $D/$SEM $REP $SSEM $DSEM
o=$(origem $D/$SEM); fase $D/$SEM "origem_ns=$o"
fase $D/$SEM "$SEM $(esperar_carga $D/$SEM)"
prova $D/$SEM $CACHE $D/$SEM/observaveis-rep-001.jsonl depois-$SEM

# execucao com corte do isn3, na mesma encarnacao
prova $D/$COM $CACHE $D/$SEM/observaveis-rep-001.jsonl antes-$COM
fase $D/$COM "$COM inicio"
carga $D/$COM $REP $SCOM 300
o=$(origem $D/$COM); fase $D/$COM "origem_ns=$o"
dormir_ate_ns $((o + 60000000000)); cortar $D/$COM
dormir_ate_ns $((o + 150000000000)); curar $D/$COM
fase $D/$COM "$COM $(esperar_carga $D/$COM)"

estado $D
logs $D
descer $D/compose-down.txt
fase $D "bloco $B fim"
