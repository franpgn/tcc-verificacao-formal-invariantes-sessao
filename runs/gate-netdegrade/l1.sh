#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-1"
PID=$(pid_de isn3)
# loss 20% por 60 s (cluster ocioso), SEM --loss-corr com verificacao por ping; criterio: exit 0
rodar_inj 60 0 "$LOG/L1.log" --target isn3 --loss 20 --duration 60 --ping-count 300 --marker "$LOG/marcadores.txt"
# durante a janela, em outro pane (gravar em $LOG):
#   sudo nsenter -t $PID -n tc -s qdisc show dev eth0 ; sudo nsenter -t $PID -n tc -s filter show dev eth0
#   curl -s -o /dev/null -w '%{time_total}\n' --digest -u admin:infinispan http://127.0.0.1:11222/rest/v2/cache-managers/default/health   # comparar com a linha de base SEM F4
#   ping -c 50 -i 0.2 <ip-isn3-host>    # sem degradacao esperada (Hot Rod/SSH poupados)
#   registrar dropped/(Sent+dropped) dos contadores, alem do ping
# esperado: exit 0, perda observada dentro de 3 sigma de 20%, dropped > 0
# L-2: limpeza
sem_prio isn3
sudo nsenter -t "$PID" -n tc filter show dev eth0
esperar_locks
