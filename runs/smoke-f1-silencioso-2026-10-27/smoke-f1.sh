#!/usr/bin/env bash
#
# smoke-f1.sh -- smoke local do F1 (secao 4.1 de docs/prompt-f1-kill-2026-10-27.md).
# Roda DENTRO da maquina Podman local (podman machine ssh, root), nunca na VM.
# Cluster proprio do smoke: f1s-isn1..3 (hostnames e aliases isn1..3) na rede
# f1smoke-net, com cluster/ de c055399; os containers isn1..3 antigos nao sao tocados.
#
#   smoke-f1.sh setup                  cria rede e containers e espera 3 nos HEALTHY
#   smoke-f1.sh ensaio <modo> <rotulo> um F1 (--modo <modo>) contra f1s-isn2, com brutos
#   smoke-f1.sh teardown               remove f1s-isn1..3 e a rede f1smoke-net
set -uo pipefail
WT="${WT:-/mnt/c/Users/Francesco/Development/tcc/wt-f1-kill}"
OUTB="${WT}/runs/smoke-f1-silencioso-2026-10-27"
INJ="${INJ:-${WT}/scripts/inject-crash.sh}"
IMG="quay.io/infinispan/server:15.0.21.Final"
export PODMAN_IGNORE_CGROUPSV1_WARNING=1
P=podman
NOS="f1s-isn1 f1s-isn2 f1s-isn3"
ALVO=f1s-isn2

utc() { date -u +%Y-%m-%dT%H:%M:%S.%3NZ; }

saude() {  # 0 se as 3 portas dizem HEALTHY com 3 nos; imprime o resumo
    local ok=0 porta corpo st nn res=""
    for porta in 11222 11223 11224; do
        corpo="$(curl -s --max-time 5 --digest -u admin:infinispan "http://127.0.0.1:${porta}/rest/v2/cache-managers/default/health" 2>/dev/null)"
        st="$(sed -n 's/.*"cluster_health":{[^}]*"health_status":"\([A-Z_]*\)".*/\1/p' <<< "${corpo}")"
        nn="$(sed -n 's/.*"number_of_nodes":\([0-9]*\).*/\1/p' <<< "${corpo}")"
        res+="${porta}=${st:-?}/${nn:-?} "
        [[ "${st}" == HEALTHY && "${nn}" == 3 ]] || ok=1
    done
    echo "${res}"
    return "${ok}"
}

esperar_saude() {  # esperar_saude <s>: imprime segundos ate HEALTHY (ou FALHOU)
    local lim="$1" t0 r
    t0="$(date +%s%3N)"
    while :; do
        if r="$(saude)"; then echo "$(( $(date +%s%3N) - t0 )) ms ${r}"; return 0; fi
        if (( $(date +%s%3N) - t0 > lim * 1000 )); then echo "FALHOU apos ${lim}s: ${r}"; return 1; fi
        sleep 1
    done
}

ip_de() { ${P} inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$1"; }

case "${1:-}" in
    setup)
        ${P} network exists f1smoke-net || ${P} network create f1smoke-net
        k=1
        for no in ${NOS}; do
            h="isn${k}"; porta=$((11221 + k))
            ${P} run -d --name "${no}" --hostname "${h}" --network f1smoke-net --network-alias "${h}" \
                -e USER=admin -e PASS=infinispan -e INFINISPAN_CLUSTER_NAME=cluster-tcc \
                -v "${WT}/cluster/infinispan-cluster.xml:/opt/infinispan/server/conf/infinispan.xml:ro" \
                -v "${WT}/cluster/jgroups-tcp.xml:/opt/infinispan/server/conf/jgroups-tcp.xml:ro" \
                -p "${porta}:11222" "${IMG}" -c infinispan.xml "-Dinfinispan.hotrod.external-port=${porta}"
            k=$((k + 1))
        done
        esperar_saude 300 ;;
    ensaio)
        modo="$2"; rot="$3"; D="${OUTB}/${rot}"; mkdir -p "${D}"
        { echo "rotulo=${rot} modo=${modo} inicio=$(utc)"; } > "${D}/ensaio.txt"
        echo "saude antes: $(esperar_saude 300)" >> "${D}/ensaio.txt" || exit 1
        T0="$(utc)"; echo "T0=${T0}" >> "${D}/ensaio.txt"
        for no in ${NOS}; do
            echo "${no} ip=$(ip_de "${no}") pid=$(${P} inspect -f '{{.State.Pid}}' "${no}") started=$(${P} inspect -f '{{.State.StartedAt}}' "${no}")" >> "${D}/antes.txt"
        done
        bash "${INJ}" --node "${ALVO}" --duration 60 --modo "${modo}" > "${D}/injetor.log" 2>&1 &
        IPID=$!
        until grep -q 'aguardando' "${D}/injetor.log" 2>/dev/null || ! kill -0 "${IPID}" 2>/dev/null; do sleep 0.2; done
        sleep 1
        ${P} inspect "${ALVO}" > "${D}/inspect-parado.json" 2>&1
        ${P} inspect -f 'status={{.State.Status}} exitcode={{.State.ExitCode}} finished={{.State.FinishedAt}}' "${ALVO}" > "${D}/estado-parado.txt" 2>&1
        for arq in ___global.lck ___global.state; do
            rc=0; ${P} cp "${ALVO}:/opt/infinispan/server/data/${arq}" "${D}/parado${arq}" > /dev/null 2>&1 || rc=$?
            if [[ "${rc}" -eq 0 ]]; then echo "${arq} presente bytes=$(stat -c %s "${D}/parado${arq}")" >> "${D}/lck.txt"
            else echo "${arq} ausente (podman cp rc=${rc})" >> "${D}/lck.txt"; fi
        done
        wait "${IPID}"; echo "injetor_rc=$?" >> "${D}/ensaio.txt"
        echo "volta (desde o fim do injetor): $(esperar_saude 180)" >> "${D}/ensaio.txt"
        T1="$(utc)"; echo "T1=${T1}" >> "${D}/ensaio.txt"
        for no in ${NOS}; do
            ${P} logs -t --since "${T0}" "${no}" > "${D}/logs-${no}.log" 2>&1
            echo "${no} ip=$(ip_de "${no}") pid=$(${P} inspect -f '{{.State.Pid}}' "${no}") started=$(${P} inspect -f '{{.State.StartedAt}}' "${no}")" >> "${D}/depois.txt"
        done
        ${P} events --stream=false --since "${T0}" --until "${T1}" --filter "container=${ALVO}" --format '{{json .}}' > "${D}/events.jsonl" 2>&1
        PIDN="$(${P} inspect -f '{{.State.Pid}}' "${ALVO}")"
        nsenter -t "${PIDN}" -n ip -br link > "${D}/ip-br-link-depois.txt" 2>&1
        echo "fim=$(utc)" >> "${D}/ensaio.txt" ;;
    teardown)
        for no in ${NOS}; do ${P} rm -f "${no}"; done
        ${P} network rm f1smoke-net ;;
    *) echo "uso: $0 setup|ensaio <modo> <rotulo>|teardown" >&2; exit 2 ;;
esac
