#!/bin/bash
# B-44.3 (investigacao): roda DENTRO da maquina Podman. A cada 1 s grava, para cada netns de no dado,
# as conexoes TCP (ss -tn: Recv-Q/Send-Q por par) com o instante em ms; nos segundos listados em DUMPS,
# manda SIGQUIT (kill -3) ao java dos nos, que imprime o dump de threads no stdout do contenedor.
# Uso: bash -s -- SEGUNDOS "DUMPS" PID_no1 [PID_no2 ...]
N=$1; DUMPS=$2; shift 2
JS=""; for p in "$@"; do JS="$JS $(pgrep -P $p java)"; done
echo "# java:$JS"
for i in $(seq 0 $N); do
  t=$(date +%s%3N)
  for p in "$@"; do nsenter -t $p -n ss -tn | awk -v t=$t -v p=$p 'NR>1{print t, p, $0}'; done
  for d in $DUMPS; do [ "$i" = "$d" ] && { echo "$t DUMP"; kill -3 $JS; }; done
  sleep 1
done
