#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/g-lib.sh"
PID=$(pid_de isn3); armar 30; T() { sudo nsenter -t "$PID" -n "$@"; }
echo "=== G-0b: o show ingress/egress separa os sentidos quando os dois filtros existem?"
T tc qdisc add dev eth0 clsact
T tc filter add dev eth0 ingress protocol ip prio 1 u32 match ip src 192.0.2.1/32 action drop
T tc filter add dev eth0 egress  protocol ip prio 1 u32 match ip dst 192.0.2.1/32 action drop
echo "ingress: $(T tc filter show dev eth0 ingress | grep -c match) match(es)"; T tc filter show dev eth0 ingress | grep -E "match|fh 80"
echo "egress:  $(T tc filter show dev eth0 egress | grep -c match) match(es)"; T tc filter show dev eth0 egress | grep -E "match|fh 80"
echo "--- parent explicito"; T tc filter show dev eth0 parent ffff:fff2 | grep -c match; T tc filter show dev eth0 parent ffff:fff3 | grep -c match
T tc qdisc del dev eth0 clsact; sem_clsact isn3; desarmar
