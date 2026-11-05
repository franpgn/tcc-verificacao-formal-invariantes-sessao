#!/bin/bash
# W-2a I-3, I-4 e I-5 em sequencia (AC-5 forcado pelo SmokeAtuador; carga sem controlador)
S=/c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2/scripts
O=/c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2/integracao
run() { local sc=$1; shift; bash $S/$sc "$@" > $O/$1.out 2>&1; python -I $S/analisar_parada.py $O/$1 > $O/$1/resumo.txt 2>&1; echo "$(date -u +%T) $1 $(grep INSTANTES $O/$1/resumo.txt | cut -c1-220)"; }
run parada.sh I3-corte-async-com corte async com 53001
run parada.sh I3-corte-async-sem corte async sem 53001
run parada.sh I3-corte-sync-com  corte sync  com 53002
run parada.sh I3-corte-sync-sem  corte sync  sem 53002
run parada.sh I4-semcorte-async-com semcorte async com 54001
run parada.sh I4-semcorte-async-sem semcorte async sem 54001
run pausa_parada.sh I5-frio frio
run pausa_parada.sh I5-aquecido aquecido
run pausa_parada.sh I5-sem sem
echo "lote I-3..I-5 pronto"
