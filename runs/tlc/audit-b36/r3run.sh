cd /c/Users/Francesco/Development/tcc/wt-b36
export TLA_TOOLS=/tmp/claude/C--Users-Francesco-Development-tcc/5fa87c0b-e6a2-4e8e-82b3-1a099fd59db7/scratchpad/tla2tools.jar
export SEM_SIMETRIA=1 WORKERS=1 MAXEVENTOSFALHA=2 TIMEOUT_SECS=600
for F in sem queda particao; do
 TAG=n1 MAXCANAL=2 MAXRELOGIO=3 spec/run-tlc-relogio.sh livre $F r2 I1P I1F I4F I2Fb RelogioDomina
done
for F in sem queda particao; do
 TAG=n1b MAXCANAL=3 MAXRELOGIO=5 spec/run-tlc-relogio.sh livre $F r2 I1P I1F I4F I2Fb RelogioDomina
done
TAG=n2 TIMEOUT_SECS=1800 MAXCANAL=2 MAXRELOGIO=3 spec/run-tlc-relogio.sh livre sem r3 I1P I1F I4F RelogioDomina
echo FIM
