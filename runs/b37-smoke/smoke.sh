#!/usr/bin/env bash
#
# smoke.sh: smoke real do coletor de recursos (B-37) dentro da maquina WSL
# podman-machine-default (NAO a VM). Roda no Git Bash do host Windows.
# Smoke local: nao e medida de energia.
#
# Projeto podman-compose -p b37, conteineres b37-isn1..3, portas 12537..12539.
# Nunca para nem reinicia a maquina Podman. O coletor roda dentro da maquina
# (root, rootful), copiado por "podman machine ssh" e conferido por sha256.
#
# A maquina WSL e cgroup HIBRIDO v1 (v2 so em /sys/fs/cgroup/unified, sem
# memory.current), entao o coletor roda com COLETOR_RAIZ_CGROUP=/sys/fs/cgroup/unified:
# as linhas cg: saem erro:leitura (contrato: linha inteira vazia). A CPU por
# conteiner e medida pelas fontes pid: (processo principal de cada conteiner)
# e, como evidencia lateral, por leituras de usage_usec (unified) e
# memory.usage_in_bytes (v1) antes e depois de cada janela (cg-lateral-*.txt).
#
# Uso: bash runs/b37-smoke/smoke.sh <repo> <jar>
set -uo pipefail

REPO="$1"; JAR="$2"
SM="${REPO}/runs/b37-smoke"
CL="${SM}/cluster"
PORTAS=(12537 12538 12539)
CONT=(b37-isn1 b37-isn2 b37-isn3)
RM="/root/b37-smoke"           # diretorio de trabalho dentro da maquina
JAVA="${JAVA_HOME:+${JAVA_HOME}/bin/}java"

ssh_m() { podman machine ssh "$@" 2>/dev/null; }
log() { echo "[smoke $(date +%H:%M:%S)] $*"; }

log "estado antes"
podman ps -a --format '{{.Names}} {{.Status}}' | sed 's/ Exited.*$/ Exited/' | sort > "${SM}/podman-ps-antes.txt"
podman network ls --format '{{.Name}}' | sort > "${SM}/podman-network-antes.txt"
if podman ps --format '{{.Names}}' | grep -q .; then
    echo "ha conteiner rodando; outro smoke em curso? abortando" >&2
    podman ps >&2; exit 9
fi

log "subindo cluster b37"
( cd "${CL}" && podman-compose -p b37 -f podman-compose.b37.yml up -d ) > "${SM}/compose-up.log" 2>&1 || { cat "${SM}/compose-up.log"; exit 9; }
for p in "${PORTAS[@]}"; do
    for _ in $(seq 1 90); do
        h="$(curl -s --digest -u admin:infinispan "http://127.0.0.1:${p}/rest/v2/cache-managers/default/health" 2>/dev/null)"
        if echo "${h}" | grep -q '"health_status":"HEALTHY"' && echo "${h}" | grep -q '"number_of_nodes":3'; then break; fi
        sleep 2
    done
    echo "${h}" > "${SM}/health-antes-${p}.json"
    echo "${h}" | grep -q '"number_of_nodes":3' || { echo "porta ${p} sem cluster de 3" >&2; exit 9; }
done
log "cluster saudavel nas 3 portas"

log "copiando o coletor para a maquina"
ssh_m "rm -rf ${RM} && mkdir -p ${RM}"
ssh_m "cat > ${RM}/collect-resources.sh" < "${REPO}/scripts/collect-resources.sh"
H_HOST="$(sha256sum "${REPO}/scripts/collect-resources.sh" | cut -d' ' -f1)"
H_MAQ="$(ssh_m "sha256sum ${RM}/collect-resources.sh" | cut -d' ' -f1)"
printf 'host    %s\nmaquina %s\n' "${H_HOST}" "${H_MAQ}" > "${SM}/sha256-coletor.txt"
[[ "${H_HOST}" == "${H_MAQ}" ]] || { echo "sha256 difere" >&2; exit 9; }

log "ambiente da maquina"
ssh_m 'bash --version | head -1; uname -r; stat -fc %T /sys/fs/cgroup; stat -fc %T /sys/fs/cgroup/unified; getconf CLK_TCK; nproc; podman --version' > "${SM}/ambiente-maquina.txt"

log "PIDs e cgroups dos conteineres"
PIDARGS=""
: > "${SM}/conteineres.txt"
for c in "${CONT[@]}"; do
    pid="$(ssh_m "podman inspect --format '{{.State.Pid}}' ${c}" | tr -d '\r')"
    cg="$(ssh_m "podman inspect --format '{{.State.CgroupPath}}' ${c}" | tr -d '\r')"
    # processo java do conteiner (o servidor); se o PID principal ja for o java, e ele
    jpid="$(ssh_m "for p in \$(cat /sys/fs/cgroup/unified${cg}/cgroup.procs); do if grep -q java /proc/\$p/comm 2>/dev/null; then echo \$p; break; fi; done" | tr -d '\r')"
    echo "${c} pid_principal=${pid} pid_java=${jpid} cgroup=${cg} procs=$(ssh_m "cat /sys/fs/cgroup/unified${cg}/cgroup.procs | tr '\n' ' '")" >> "${SM}/conteineres.txt"
    PIDARGS+=" --pid ${c#b37-}=${jpid:-${pid}}"
done
cat "${SM}/conteineres.txt"

log "dry-run com a raiz padrao (/sys/fs/cgroup; esperado exit 3 no cgroup v1 hibrido)"
ssh_m "cd ${RM} && bash collect-resources.sh --dry-run --saida ${RM}/dry --rep 1 --containers '${CONT[*]}'${PIDARGS} 2>&1; echo exit=\$?" > "${SM}/dry-run-raiz-padrao.txt"
ssh_m "cd ${RM} && COLETOR_RAIZ_CGROUP=/sys/fs/cgroup/unified bash collect-resources.sh --dry-run --saida ${RM}/dry --rep 1 --containers '${CONT[*]}'${PIDARGS} 2>&1; echo exit=\$?" > "${SM}/dry-run-raiz-unified.txt"

lateral() {   # lateral <arquivo>: usage_usec (unified), cpuacct.usage (v1, ns), memory.usage_in_bytes (v1)
    ssh_m "echo t_epoch_ns=\$(date +%s%N); for c in ${CONT[*]}; do r=\$(podman inspect --format '{{.State.CgroupPath}}' \$c); \
        u=\$(awk '/^usage_usec/ {print \$2}' /sys/fs/cgroup/unified\$r/cpu.stat); \
        a=\$(cat /sys/fs/cgroup/cpuacct\$r/cpuacct.usage 2>/dev/null); \
        m=\$(cat /sys/fs/cgroup/memory\$r/memory.usage_in_bytes 2>/dev/null); \
        echo \"\$c usage_usec=\$u cpuacct_usage_ns=\$a memory_usage_in_bytes=\$m\"; done; head -1 /proc/stat" > "$1"
}
coletar() {   # coletar <rotulo> [podman_stats_s, padrao 10]
    podman ps --format '{{.Names}}' | sort > "${SM}/$1-podman-ps-durante.txt"   # outros smokes na maquina?
    ssh_m "cd ${RM} && COLETOR_RAIZ_CGROUP=/sys/fs/cgroup/unified bash collect-resources.sh --saida ${RM}/$1 --rep 1 \
        --containers '${CONT[*]}'${PIDARGS} --intervalo-ms 1000 --podman-stats-s ${2:-10} --duracao-s 60 2>&1; echo exit=\$?" > "${SM}/$1-coletor.log"
}
trazer() {   # trazer <rotulo>
    mkdir -p "${SM}/$1"
    for f in $(ssh_m "ls ${RM}/$1" | tr -d '\r'); do
        ssh_m "cat ${RM}/$1/${f}" > "${SM}/$1/${f}"
        ssh_m "cd ${RM}/$1 && sha256sum ${f}" | tr -d '\r' >> "${SM}/$1/sha256-maquina.txt"
    done
}

log "S1: 60 s ocioso"
lateral "${SM}/S1-cg-lateral-antes.txt"
coletar S1
lateral "${SM}/S1-cg-lateral-depois.txt"
trazer S1
cat "${SM}/S1-coletor.log"

log "S2: 60 s com carga leve (S1 sync v3, 2 threads) do jar da base, no Windows"
mkdir -p "${SM}/S2/workload"
"${JAVA}" -jar "${JAR}" --scenario S1 --mix v3 --replication sync --reset-state on \
    --duration 60 --warmup-min-sec 10 --threads 2 --ops 0 --rep 1 --seed 37000 \
    --servers 127.0.0.1:12537,127.0.0.1:12538,127.0.0.1:12539 --username admin --password infinispan \
    --csv-dir "${SM}/S2/workload" > "${SM}/S2-workload.log" 2>&1 &
WPID=$!
# espera o reset e a carga ("== Repeticao 1 / 1 ==") e o aquecimento minimo (10 s)
# antes de abrir a janela de 60 s do coletor; a medicao do workload dura 60 s
# depois do aquecimento, e a janela do coletor cai quase toda dentro dela.
for _ in $(seq 1 180); do
    grep -q '== Repeticao' "${SM}/S2-workload.log" 2>/dev/null && break
    sleep 1
done
sleep 12
log "janela do coletor em S2"
lateral "${SM}/S2-cg-lateral-antes.txt"
coletar S2
lateral "${SM}/S2-cg-lateral-depois.txt"
trazer S2
cat "${SM}/S2-coletor.log"
wait "${WPID}"; echo "workload exit=$?" >> "${SM}/S2-workload.log"
tail -5 "${SM}/S2-workload.log"

log "C0: 60 s ocioso SEM podman stats (custo so do laco do coletor)"
lateral "${SM}/C0-cg-lateral-antes.txt"
coletar C0 0
lateral "${SM}/C0-cg-lateral-depois.txt"
trazer C0
cat "${SM}/C0-coletor.log"

log "derrubando o cluster b37"
for p in "${PORTAS[@]}"; do
    curl -s --digest -u admin:infinispan "http://127.0.0.1:${p}/rest/v2/cache-managers/default/health" > "${SM}/health-depois-${p}.json" 2>/dev/null
done
for c in "${CONT[@]}"; do
    echo "${c} ERROR=$(podman logs "${c}" 2>&1 | grep -c ' ERROR ')" >> "${SM}/logs-erros.txt"
done
( cd "${CL}" && podman-compose -p b37 -f podman-compose.b37.yml down ) > "${SM}/compose-down.log" 2>&1
ssh_m "rm -rf ${RM}"
podman ps -a --format '{{.Names}} {{.Status}}' | sed 's/ Exited.*$/ Exited/' | sort > "${SM}/podman-ps-depois.txt"
podman network ls --format '{{.Name}}' | sort > "${SM}/podman-network-depois.txt"
diff "${SM}/podman-ps-antes.txt" "${SM}/podman-ps-depois.txt" && diff "${SM}/podman-network-antes.txt" "${SM}/podman-network-depois.txt" \
    && log "ps/network antes e depois iguais"
log "fim"
