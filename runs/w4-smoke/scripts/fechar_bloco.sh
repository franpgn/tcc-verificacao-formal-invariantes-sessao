#!/bin/bash
# W-4 smoke: criterios (analisar_smoke.py) e MANIFEST.sha256 de um bloco ja executado.
source /c/Users/Francesco/Development/tcc/wt-w4/runs/w4-smoke/scripts/lib-w4s.sh
B=$1; D=$R/$B
case $B in A) SEM=S0; COM=S1 ;; B) SEM=S2a; COM=S2 ;; *) echo "uso: fechar_bloco.sh A|B"; exit 2 ;; esac
python -I $R/scripts/analisar_smoke.py $D/$SEM --servidores $D/servidores > $D/$SEM/criterios.json
python -I $R/scripts/analisar_smoke.py $D/$COM --servidores $D/servidores \
  --chaves-de $D/$SEM/observaveis-rep-001.jsonl > $D/$COM/criterios.json
(cd $D && find . -type f ! -name MANIFEST.sha256 ! -name pid -print0 | sort -z | xargs -0 sha256sum > MANIFEST.sha256)
wc -l < $D/MANIFEST.sha256
