#!/bin/bash
# uso: recriar.sh <dir-saida>
D=$1; mkdir -p $D; cd ~/tcc-gate
hs() { for p in 11222 11223 11224; do curl -s -m 3 --digest -u admin:infinispan "http://localhost:$p/rest/v2/cache-managers/default/health" | tr -d ' \n' | grep -o '"health_status":"[A-Z_]*","number_of_nodes":[0-9]*' || echo "$p:ERR"; done | paste -sd' '; }
sudo podman-compose -f cluster/podman-compose.yml down > $D/down.log 2>&1; echo "down rc=$?"
sudo podman images | grep infinispan/server
sudo podman-compose -f cluster/podman-compose.yml up -d > $D/up.log 2>&1; echo "up rc=$?"
T0=$(date +%s); echo "t0_up_epoch=$T0" > $D/validacao.txt
OKS=; ESTAVEL=
while [ $(( $(date +%s) - T0 )) -lt 180 ]; do
  r=$(hs); echo "$(date +%s.%N) $r" >> $D/validacao.txt
  if [ "$(echo "$r" | grep -o '"health_status":"HEALTHY","number_of_nodes":3' | wc -l)" = 3 ]; then
    [ -z "$OKS" ] && OKS=$(date +%s)
    [ $(( $(date +%s) - OKS )) -ge 20 ] && { ESTAVEL=$(date +%s); break; }
  else OKS=; fi
  sleep 2
done
if [ -n "$ESTAVEL" ]; then echo "ESTAVEL size=3 HEALTHY: primeira leitura boa +$((OKS-T0))s, estavel por $((ESTAVEL-OKS))s (total $((ESTAVEL-T0))s)" | tee -a $D/validacao.txt
else echo "FALHA: nao fechou em 180s" | tee -a $D/validacao.txt; fi
echo "timers tcc-heal: $(systemctl list-timers --all | grep -c tcc-heal)"
for n in isn1 isn2 isn3; do echo -n "$n: "; sudo podman inspect -f '{{.State.StartedAt}}' $n; sudo nsenter -t "$(sudo podman inspect -f '{{.State.Pid}}' $n)" -n tc qdisc show dev eth0 | tr '\n' ';'; echo; done
for n in isn1 isn2 isn3; do sudo podman logs $n 2>&1 | grep -E "ISPN000094|ISPN000093" | tail -1 | cut -c1-200; done
