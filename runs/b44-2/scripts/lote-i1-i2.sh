#!/bin/bash
# W-2a I-1 e I-2 em sequencia (cluster w2a recriado em cada execucao pelo queda.sh)
S=/c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2/scripts
O=/c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2/integracao
run() { bash $S/queda.sh "$@" > $O/$1.out 2>&1; python -I $S/analisar_queda.py $O/$1 > $O/$1/resumo.txt 2>&1; echo "$(date -u +%T) $1 $(grep INTERVALOS $O/$1/resumo.txt | cut -c1-200)"; }
# I-1: kill de 55 s (> T_det), no comum e coordenador, active e controle (shadow)
run I1-comum-async-active  kill comum async active 51001 40 55 150
run I1-comum-async-shadow  kill comum async shadow 51001 40 55 150
run I1-coord-async-active  kill coord async active 51002 40 55 150
run I1-coord-async-shadow  kill coord async shadow 51002 40 55 150
# I-2: pausa de 52 s (entre 50 e 55 s), 3 repeticoes active e um controle
run I2-pausa-async-active-r1 pause comum async active 52001 40 52 160
run I2-pausa-async-active-r2 pause comum async active 52002 40 52 160
run I2-pausa-async-active-r3 pause comum async active 52003 40 52 160
run I2-pausa-async-shadow    pause comum async shadow 52001 40 52 160
echo "lote I-1/I-2 pronto"
