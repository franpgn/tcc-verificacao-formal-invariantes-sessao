#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/g-lib.sh"
echo "=== G-0 $(date -Is) vm=$(hostname -I | awk "{print \$1}") sha=$(git rev-parse HEAD)"
echo "--- ambiente"; uname -r; tc -V; sudo podman info --format "{{.Host.NetworkBackend}} rootless={{.Host.Security.Rootless}}"
for m in cls_u32 sch_ingress act_gact; do lsmod | grep -E "^$m " || echo "(sem $m em lsmod)"; done
PID=$(pid_de isn3)
echo "--- guarda ok: pid isn3=$PID netns_pid1=$(sudo readlink /proc/1/ns/net) netns_isn3=$(sudo readlink /proc/$PID/ns/net)"
armar 60
T() { sudo nsenter -t "$PID" -n "$@"; }
echo "--- interfaces/IPs no netns de isn3"; T ip -4 addr show; T ip -br link
echo "--- IPv6/TCP JGroups (ss -tn)"; T ss -tn 2>&1 | head -20
echo "--- tc antes"; T tc qdisc show dev eth0
echo "--- add clsact"; T tc qdisc add dev eth0 clsact; echo "rc=$?"
echo "--- add filter ingress"; T tc filter add dev eth0 ingress protocol ip prio 1 u32 match ip src 192.0.2.1/32 action drop; echo "rc=$?"
echo "--- add filter egress";  T tc filter add dev eth0 egress  protocol ip prio 1 u32 match ip dst 192.0.2.1/32 action drop; echo "rc=$?"
echo "--- tc -s filter show (ingress/egress)"; T tc -s filter show dev eth0 ingress; T tc -s filter show dev eth0 egress
echo "--- del clsact"; T tc qdisc del dev eth0 clsact; echo "rc=$?"
echo "--- tc depois"; T tc qdisc show dev eth0; T tc filter show dev eth0 ingress; T tc filter show dev eth0 egress
sem_clsact isn3
desarmar
echo "--- saude pos-G-0"; health
echo "G-0 concluido sem residuo"
