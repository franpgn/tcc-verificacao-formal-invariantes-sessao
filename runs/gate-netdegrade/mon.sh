#!/usr/bin/env bash
# mon.sh <rotulo> <intervalo-s> : amostrador de apoio ao gate L (nao altera a rede). Para quando existir $LOG/<rotulo>.stop
# Cada ciclo: timestamp, tc -s qdisc/filter dos 3 nos, timers tcc-heal, health REST (http, status, size, time_total) nas 3 portas, ping host->isn3.
set -uo pipefail
cd "${TCC_DIR:-$HOME/tcc-netdeg}"
LOG=runs/gate-netdegrade; R="$1"; IV="${2:-5}"; OUT="$LOG/mon-$R.log"; rm -f "$LOG/$R.stop"
IP3=$(sudo podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' isn3)
while [ ! -e "$LOG/$R.stop" ]; do
  {
  echo "=== $(date -u +%FT%T.%3NZ) epoch_ns=$(date +%s%N)"
  echo "--- timers: $(systemctl list-timers --all 2>/dev/null | grep tcc-heal | tr -s ' ' | tr '\n' '|')"
  for n in isn1 isn2 isn3; do
    p=$(sudo podman inspect -f '{{.State.Pid}}' $n)
    echo "--- $n pid=$p tc -s qdisc"; sudo nsenter -t $p -n tc -s qdisc show dev eth0
    echo "--- $n tc filter"; sudo nsenter -t $p -n tc filter show dev eth0
  done
  for port in 11222 11223 11224; do
    curl -s -m 5 --digest -u admin:infinispan -w "\nHTTP=%{http_code} time_total=%{time_total}\n" "http://localhost:$port/rest/v2/cache-managers/default/health" | tr -d ' \n' | sed -E 's/.*"health_status":"([A-Z_]*)".*"number_of_nodes":([0-9]+).*(HTTP=[0-9]+)time_total=([0-9.]+)/port='$port' status=\1 size=\2 \3 time_total=\4/'; echo
  done
  echo "--- ping host->isn3($IP3): $(ping -c 5 -i 0.2 -W 2 -q $IP3 | tail -2 | tr '\n' ' ')"
  } >> "$OUT" 2>&1
  sleep "$IV"
done
echo "mon $R fim $(date -u +%FT%T.%3NZ)" >> "$OUT"
