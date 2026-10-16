#!/usr/bin/env bash
# Teste da guarda de netns (somente leitura): deve recusar PID 1 (netns do host), PID vazio, 0 e inexistente; aceitar o pid de isn3.
set -uo pipefail
source "$(dirname "$0")/l-lib.sh"
t() { local d="$1" esp="$2"; shift 2; guarda "$@"; local rc=$?; echo "guarda($d) rc=$rc esperado=$esp $([ $rc = $esp ] && echo PASS || echo FAIL)"; }
echo "netns PID1: $(sudo readlink /proc/1/ns/net)"
echo "netns self shell: $(sudo readlink /proc/$$/ns/net)"
t "pid=1 host" 1 1
t "pid=\$\$ shell do host" 1 $$
t "pid vazio" 1 ""
t "pid 0" 1 0
t "pid inexistente 999999" 1 999999
P=$($PODMAN inspect -f '{{.State.Pid}}' isn3); echo "isn3 pid=$P netns=$(sudo readlink /proc/$P/ns/net)"
t "isn3" 0 "$P"
# pid_de com container host-net inexistente nao e testavel sem criar contêiner; ver relatorio.
( pid_de nao-existe-xyz ) ; echo "pid_de(nao-existe) rc=$? (esperado 1)"
