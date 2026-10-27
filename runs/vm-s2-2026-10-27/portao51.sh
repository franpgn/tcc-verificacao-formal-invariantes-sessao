#!/usr/bin/env bash
# runs/vm-s2-2026-10-27/portao51.sh -- portao 5.1 de decisoes-senior-2026-10-16 (SO LEITURA: curl, inspect, nsenter tc show, logs).
# uso: portao51.sh <duracao-min-s> <saida.csv>
# Amostra as 3 portas a cada 5 s por >= <duracao-min-s>; qualquer amostra que nao seja cluster HEALTHY com
# number_of_nodes=3, ou que contenha HEALTHY_REBALANCING/DEGRADED/FAILED/UNHEALTHY em qualquer parte do corpo, zera a contagem.
# Ao fim imprime as demais condicoes (caches, timers, tc nos 3 netns, lock, mudancas de vista nos ultimos 5 min).
set -uo pipefail
DUR="${1:-130}"; CSV="${2:-/dev/null}"
nl=$'\n'
amostra() { local p="$1" raw http body
  raw=$(curl -s -m 3 --digest -u admin:infinispan -w "${nl}%{http_code}" "http://localhost:$p/rest/v2/cache-managers/default/health" 2>/dev/null) || true
  http="${raw##*"$nl"}"; body=$(printf '%s' "${raw%"$nl"*}" | tr -d " $nl")
  printf '%s' "$http|$body"; }
echo "t_iso,porta,http,cluster_status,number_of_nodes,ruim,corpo" > "$CSV"
T0=$(date +%s); OK0=; MAXOK=0; RUINS=0; N=0
while :; do
  agora=$(date +%s); ruim=0
  for p in 11222 11223 11224; do
    r=$(amostra $p); http="${r%%|*}"; body="${r#*|}"
    st=$(printf '%s' "$body" | grep -o '"cluster_health":{[^}]*' | grep -o '"health_status":"[A-Z_]*"' | cut -d'"' -f4)
    nn=$(printf '%s' "$body" | grep -o '"number_of_nodes":[0-9]*' | head -1 | cut -d: -f2)
    b=0
    [ "$http" = 200 ] && [ "$st" = HEALTHY ] && [ "$nn" = 3 ] || b=1
    printf '%s' "$body" | grep -qE 'HEALTHY_REBALANCING|DEGRADED|FAILED|UNHEALTHY' && b=1
    [ "$b" = 1 ] && ruim=1
    echo "$(date -Is),$p,$http,$st,$nn,$b,$body" >> "$CSV"
  done
  N=$((N+1))
  if [ "$ruim" = 0 ]; then [ -z "$OK0" ] && OK0=$agora; else OK0=; RUINS=$((RUINS+1)); fi
  [ -n "$OK0" ] && [ $((agora - OK0)) -gt "$MAXOK" ] && MAXOK=$((agora - OK0))
  [ $((agora - T0)) -ge "$DUR" ] && break
  sleep 5
done
echo "amostras=$N amostras_ruins=$RUINS janela_s=$(( $(date +%s) - T0 )) maior_sequencia_boa_s=$MAXOK ultima_sequencia_boa_desde=$( [ -n "$OK0" ] && date -Is -d @$OK0 || echo NENHUMA)"
for c in sessions counters; do for p in 11222 11223 11224; do
  echo "size $c porta $p: $(curl -s -m 3 --digest -u admin:infinispan "http://localhost:$p/rest/v2/caches/$c?action=size")"; done; done
echo "timers tcc-heal: $(systemctl list-timers --all --no-legend | grep -c tcc-heal) units tcc-heal: $(systemctl list-units --all --no-legend | grep -c tcc-heal)"
for n in isn1 isn2 isn3; do
  pid=$(sudo podman inspect -f '{{.State.Pid}}' $n)
  out=$(sudo nsenter -t "$pid" -n tc qdisc show)
  echo "$n pid=$pid tc qdisc show (todas as interfaces): $(echo $out)"
  echo "$n proibidos(clsact|prio|netem|ingress)=$(printf '%s\n' "$out" | grep -cE 'clsact|prio|netem|ingress') eth0_noqueue=$(sudo nsenter -t "$pid" -n tc qdisc show dev eth0 | grep -c '^qdisc noqueue 0: root')"
  echo "$n tc filter eth0: [$(sudo nsenter -t "$pid" -n tc filter show dev eth0 | tr '\n' ';')]"
done
flock -n /var/lock/tcc-vm.lock true && echo "lock: LIVRE" || echo "lock: OCUPADO"
for n in isn1 isn2 isn3; do
  echo "$n mudancas de vista nos ultimos 5 min (ISPN000094|ISPN000093|ISPN100000|ISPN100001|MergeView): $(sudo podman logs --since 5m $n 2>&1 | grep -cE 'ISPN000094|ISPN000093|ISPN100000|ISPN100001|MergeView')"
  echo "$n ultima mudanca de vista: $(sudo podman logs $n 2>&1 | grep -E 'ISPN000094|ISPN000093' | tail -1 | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-200)"
done
echo "fim $(date -Is)"
