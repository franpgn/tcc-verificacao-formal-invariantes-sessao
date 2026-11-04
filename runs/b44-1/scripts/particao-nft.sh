#!/bin/bash
# B-44.1 (copia de trabalho, NAO substitui scripts/inject-partition.sh): isola um no do cluster
# local b441 dos pares JGroups com nftables no netns do container, dentro da maquina do Podman
# (a maquina WSL nao tem tc, entao o mecanismo clsact/u32 do inject-partition.sh nao roda aqui).
# Mesmo efeito: descarte nos dois sentidos so para os IPs dos pares; o Hot Rod/REST do host
# (origem no gateway da bridge) continua passando.
# Uso: particao-nft.sh cortar|curar <no> <par1> <par2>
set -euo pipefail
acao=$1 no=$2 p1=$3 p2=$4
ip_de() { podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' b441-$1; }
pid=$(podman machine ssh "podman inspect -f '{{.State.Pid}}' b441-$no" 2>/dev/null | tr -d '\r')
a=$(ip_de $p1); b=$(ip_de $p2)
case $acao in
  cortar) podman machine ssh "nsenter -t $pid -n nft add table inet b441 && \
      nsenter -t $pid -n nft add chain inet b441 ent '{ type filter hook input priority 0; }' && \
      nsenter -t $pid -n nft add chain inet b441 sai '{ type filter hook output priority 0; }' && \
      nsenter -t $pid -n nft add rule inet b441 ent ip saddr '{ $a, $b }' counter drop && \
      nsenter -t $pid -n nft add rule inet b441 sai ip daddr '{ $a, $b }' counter drop && \
      nsenter -t $pid -n nft list table inet b441" 2>&1 | grep -v cgroups ;;
  curar) podman machine ssh "nsenter -t $pid -n nft list table inet b441; nsenter -t $pid -n nft delete table inet b441; nsenter -t $pid -n nft list tables" 2>&1 | grep -v cgroups ;;
esac
