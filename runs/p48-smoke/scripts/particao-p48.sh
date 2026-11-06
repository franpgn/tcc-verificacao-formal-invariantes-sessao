#!/bin/bash
# Smoke do P-48 (06/11): copia do runs/w2b-smoke/scripts/particao-w2b.sh com os nomes p48 (nftables no netns do
# container, dentro da maquina do Podman; a maquina WSL nao tem tc). NAO substitui scripts/inject-partition.sh.
# Uso: particao-nft.sh cortar|curar <no> <par1> <par2>
set -euo pipefail
acao=$1 no=$2 p1=$3 p2=$4
ip_de() { podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' p48-$1; }
pid=$(podman machine ssh "podman inspect -f '{{.State.Pid}}' p48-$no" 2>/dev/null | tr -d '\r')
a=$(ip_de $p1); b=$(ip_de $p2)
case $acao in
  cortar) podman machine ssh "nsenter -t $pid -n nft add table inet p48 && \
      nsenter -t $pid -n nft add chain inet p48 ent '{ type filter hook input priority 0; }' && \
      nsenter -t $pid -n nft add chain inet p48 sai '{ type filter hook output priority 0; }' && \
      nsenter -t $pid -n nft add rule inet p48 ent ip saddr '{ $a, $b }' counter drop && \
      nsenter -t $pid -n nft add rule inet p48 sai ip daddr '{ $a, $b }' counter drop && \
      nsenter -t $pid -n nft list table inet p48" 2>&1 | grep -v cgroups ;;
  curar) podman machine ssh "nsenter -t $pid -n nft list table inet p48; nsenter -t $pid -n nft delete table inet p48; nsenter -t $pid -n nft list tables" 2>&1 | grep -v cgroups ;;
esac
