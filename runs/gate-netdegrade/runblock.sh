#!/usr/bin/env bash
# runblock.sh <bloco: l0|l1> <intervalo-mon-s> : executa o bloco com monitor em paralelo e xtrace com timestamp. Precisa de TTY (exigir_go).
set -uo pipefail
export TCC_DIR=~/tcc-netdeg; cd $TCC_DIR/runs/gate-netdegrade
B="$1"; IV="${2:-5}"; L=$(echo "$B" | tr a-z A-Z | tr -d -)  # L0/L1
L=$(echo "$B" | tr a-z A-Z)
for ph in pre; do bash mon.sh $L-pre 1 & mp=$!; sleep 2.5; touch $L-pre.stop; wait $mp; done
echo "inicio $(date -u +%FT%T.%3NZ) bloco=$B" | tee -a $L-run.log
bash mon.sh $L-durante $IV & MP=$!
sleep 1
PS4='+ $(date +%H:%M:%S.%N | cut -c1-15) ' bash -x $B.sh > $L-bloco.log 2>&1; rc=$?
echo "rc do bloco $B = $rc ($(date -u +%FT%T.%3NZ))" | tee -a $L-run.log
sleep $IV; touch $L-durante.stop; wait $MP
bash mon.sh $L-pos 1 & mp=$!; sleep 2.5; touch $L-pos.stop; wait $mp
echo "fim $(date -u +%FT%T.%3NZ)" | tee -a $L-run.log
exit $rc
