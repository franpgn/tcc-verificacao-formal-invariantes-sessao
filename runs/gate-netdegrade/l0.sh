#!/usr/bin/env bash
# runs/gate-netdegrade/l0.sh
set -euo pipefail
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-0"
PID=$(pid_de isn3)                 # a guarda ABORTA o script (exit 1) antes de qualquer armar/tc
armar 120                          # L-0 deve durar < 120 s
T() { sudo nsenter -t "$PID" -n "$@"; }
T ip -4 addr show                  # interface e IPv4; confirmar ausencia de IPv6 no TCP do JGroups (ss -tn no netns)
T tc qdisc show dev eth0           # raiz padrao (noqueue/fq_codel)
T tc qdisc add dev eth0 root handle 1: prio bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
T tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20% 25% delay 20ms 5ms distribution normal duplicate 1% reorder 25% 50% corrupt 1%
T tc qdisc change dev eth0 parent 1:3 handle 30: netem loss gemodel 5% 25% 100% 0%
T tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32 flowid 1:3
T tc qdisc del dev eth0 root
sem_prio isn3                      # sem prio/netem
desarmar
$INJ --target isn3 --severity N3 --direction both --dry-run
echo "L-0 concluido sem residuo"
