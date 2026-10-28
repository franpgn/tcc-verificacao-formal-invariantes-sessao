#!/usr/bin/env bash
# carga-f1.sh <modo> <rotulo> <seed> -- roda no host Windows (Git Bash): workload congelado S1 sync 180 s,
# 4 threads, contra f1s-isn1..3 (portas 11222..11224); F1 (--modo <modo>) disparado dentro da maquina
# Podman a +49 s da ancora "Warm-up ate"; depois o VerificadorSanidade. So para avaliar o risco de K3.
# HOSTSRV: IP da maquina WSL (ip -4 -br addr, eth0). O encaminhamento de localhost do Windows para a
# porta de f1s-isn2 nao volta depois do kill/start (artefato local; ver c1-carga-silencioso.invalido-portforward).
set -uo pipefail
B="$(cd "$(dirname "$0")" && pwd)"
JAR="/c/Users/Francesco/Development/tcc/dados-vm/rebaseline-2026-10/jar/session-workload-0.1.0-SNAPSHOT.jar"
JAVA="/c/Program Files/Java/jdk-22.0.2/bin/java"
HOSTSRV="${HOSTSRV:-127.0.0.1}"
modo="$1"; rot="$2"; seed="$3"; D="${B}/${rot}"; mkdir -p "${D}/csv"
echo "jar_sha256=$(sha256sum "${JAR}" | cut -d' ' -f1)" > "${D}/carga.txt"
echo "servidores=${HOSTSRV}:11222..11224 seed=${seed}" >> "${D}/carga.txt"
"${JAVA}" -jar "${JAR}" --scenario S1 --mix v3 --replication sync --reset-state on --duration 180 \
    --warmup-min-sec 30 --threads 4 --ops 0 --rep 1 --seed "${seed}" \
    --servers ${HOSTSRV}:11222,${HOSTSRV}:11223,${HOSTSRV}:11224 --username admin --password infinispan \
    --csv-dir "$(cygpath -w "${D}/csv")" > "${D}/stdout.log" 2>&1 &
JP=$!
until grep -q 'Warm-up ate' "${D}/stdout.log" 2>/dev/null || ! kill -0 "${JP}" 2>/dev/null; do sleep 0.1; done
echo "ancora_utc=$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)" >> "${D}/carga.txt"
sleep 49
echo "disparo_utc=$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)" >> "${D}/carga.txt"
podman machine ssh "bash /mnt/c/Users/Francesco/Development/tcc/wt-f1-kill/runs/smoke-f1-silencioso-2026-10-27/smoke-f1.sh ensaio ${modo} ${rot}" 2>&1 | grep -v cgroups-v1
wait "${JP}"; echo "workload_exit=$?" >> "${D}/carga.txt"
"${JAVA}" -cp "${JAR}" br.unipampa.tcc.session.VerificadorSanidade --csv "$(cygpath -w "${D}/csv/rep-001.csv")" \
    --scenario S1 --saida "$(cygpath -w "${D}/sanidade.json")" > "${D}/verificador.log" 2>&1
echo "verificador_exit=$?" >> "${D}/carga.txt"
