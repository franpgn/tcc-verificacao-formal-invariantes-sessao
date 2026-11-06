#!/bin/bash
# A1 smoke (copia do particao-w10.sh do W-10 com os nomes a1 e a tabela nft "a1"; copia de trabalho, NAO substitui
# scripts/inject-partition.sh): isola um no do cluster local a1 dos pares JGroups com nftables no netns do container,
# dentro da maquina do Podman (a maquina WSL nao tem tc, entao o mecanismo clsact/u32 do inject-partition.sh nao roda
# aqui). Mesmo efeito: descarte nos dois sentidos so para os IPs dos pares; o Hot Rod/REST do host (origem no gateway
# da bridge) continua passando.
# As linhas nft-antes/nft-aplicado/nft-removido trazem o relogio da maquina do Podman em volta do nft (o "podman machine
# ssh" pode levar dezenas de segundos sob carga; o instante efetivo do corte e da cura e o dessas linhas).
# Uso: particao-a1.sh cortar|curar <no> <par1> <par2>
set -euo pipefail
acao=$1 no=$2 p1=$3 p2=$4
ip_de() { podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' a1-$1; }
pid=$(podman machine ssh "podman inspect -f '{{.State.Pid}}' a1-$no" 2>/dev/null | tr -d '\r')
a=$(ip_de $p1); b=$(ip_de $p2)
case $acao in
  cortar) podman machine ssh "echo nft-antes \$(date -u +%FT%T.%NZ) && \
      nsenter -t $pid -n nft add table inet a1 && \
      nsenter -t $pid -n nft add chain inet a1 ent '{ type filter hook input priority 0; }' && \
      nsenter -t $pid -n nft add chain inet a1 sai '{ type filter hook output priority 0; }' && \
      nsenter -t $pid -n nft add rule inet a1 ent ip saddr '{ $a, $b }' counter drop && \
      nsenter -t $pid -n nft add rule inet a1 sai ip daddr '{ $a, $b }' counter drop && \
      echo nft-aplicado \$(date -u +%FT%T.%NZ) && \
      nsenter -t $pid -n nft list table inet a1" 2>&1 | grep -v cgroups ;;
  curar) podman machine ssh "nsenter -t $pid -n nft list table inet a1; \
      echo nft-antes \$(date -u +%FT%T.%NZ); \
      nsenter -t $pid -n nft delete table inet a1; \
      echo nft-removido \$(date -u +%FT%T.%NZ); \
      nsenter -t $pid -n nft list tables" 2>&1 | grep -v cgroups ;;
esac
