#!/usr/bin/env bash
#
# run-baseline.sh
#
# Executa a bateria baseline do TCC: cenarios {S1, S2} x falhas
# {none, F1, F3}, gravando os CSVs de eventos em runs/<cenario>-<falha>/ e
# acionando o pipeline analysis/ para produzir summary.json por cenario e
# comparacao.json (none x F1, none x F3) por cenario de carga.
#
# Falha F1 (crash silencioso de no): durante a janela de medicao, um no e
# suspenso via scripts/inject-crash.sh (podman stop) por DURACAO_CRASH
# segundos e reativado. Nao requer netem/VM (isso e F3).
#
# Falha F3 (jitter/atraso): durante a janela de medicao, um no recebe uma
# disciplina netem (atraso+jitter) via scripts/inject-jitter.sh, que
# aplica o netem a partir do HOST entrando no netns do container via
# nsenter (sudo nsenter -t <pid> -n tc qdisc ...), por DURACAO_JITTER
# segundos; a disciplina e sempre removida ao fim. F3 NAO derruba o no
# (cluster permanece size=3), logo nao ha health-gate apos a repeticao.
# Exige nsenter/tc/sudo/sch_netem NO HOST: roda na VM Hyper-V/Ubuntu (ver
# docs/plano-f3-vm-netem.md), nao no WSL2. O container NAO precisa de tc
# nem de NET_ADMIN.
#
# Pre-requisitos verificados pelo Worker:
#   - cluster Infinispan size=3 HEALTHY (cluster/podman-compose.yml up -d);
#   - jar empacotado em workload/target/session-workload-*.jar;
#   - Python 3 + numpy/scipy para analysis/.
#
# Uso:
#   scripts/run-baseline.sh [--reps N] [--duration SEG] [--warmup-min SEG]
#                           [--threads N] [--crash-node NOME]
#                           [--crash-duration SEG] [--jitter-node NOME]
#                           [--jitter-iface IF] [--jitter-delay Nms]
#                           [--jitter-stddev Nms] [--jitter-distribution D]
#                           [--scenarios "S1 S2"]
#                           [--faults "none F1 F3"] [--dry-run]
#
# Variavel de ambiente PODMAN: binario do podman, propagado (export) aos
# scripts-filhos inject-crash.sh/inject-jitter.sh. Padrao 'podman' (rootless).
# Para o cluster ROOTFUL da VM Hyper-V -- unico modo em que o netem do F3
# alcanca o trafego do cliente -- prefixe a bateria:
#   PODMAN="sudo podman" scripts/run-baseline.sh --faults "none F3" ...
#
# Defaults sao curtos (validacao): 3 reps x 120s. Aumente para a bateria
# final. NAO faz git push.
set -euo pipefail

# Forca UTF-8 na saida do Python: o terminal Windows usa cp1252 por padrao
# e o print de compare_scenarios.py contem 'Delta' (U+0394), o que
# dispararia UnicodeEncodeError no stdout (sem afetar o JSON gravado).
export PYTHONIOENCODING=utf-8
export PYTHONUTF8=1

# Binario do podman, propagado aos scripts-filhos (inject-crash.sh /
# inject-jitter.sh). Padrao: 'podman' (rootless). Para o cluster ROOTFUL da
# VM Hyper-V (unico modo em que o netem do F3 alcanca o trafego do cliente,
# ja que o rootless faz bypass do eth0 via pasta), prefixe a bateria com:
#   PODMAN="sudo podman" scripts/run-baseline.sh --faults "none F3" ...
# O export garante que o override chegue aos scripts-filhos invocados abaixo.
PODMAN="${PODMAN:-podman}"
export PODMAN

# ------------------------------------------------------------------ paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNS_DIR="${REPO_DIR}/runs"
ANALYSIS_DIR="${REPO_DIR}/analysis"
INJECT="${SCRIPT_DIR}/inject-crash.sh"
INJECT_JITTER="${SCRIPT_DIR}/inject-jitter.sh"

# --------------------------------------------------------------- defaults
REPS=3
DURATION=120
WARMUP_MIN=20
THREADS=8
CRASH_NODE="isn2"
CRASH_DURATION=60
JITTER_NODE="isn2"
JITTER_IFACE="eth0"
JITTER_DELAY="20ms"
JITTER_STDDEV="13ms"
JITTER_DISTRIBUTION="normal"
SERVERS="127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224"
USERNAME="admin"
PASSWORD="infinispan"
SEED_BASE=42
SCENARIOS="S1 S2"
FAULTS="none F1"
DRY_RUN=0

JAR="$(ls "${REPO_DIR}"/workload/target/session-workload-*.jar 2>/dev/null \
        | grep -v original | head -1 || true)"

# ----------------------------------------------------------------- parse
while [[ $# -gt 0 ]]; do
    case "$1" in
        --reps)            REPS="$2"; shift 2 ;;
        --duration)        DURATION="$2"; shift 2 ;;
        --warmup-min)      WARMUP_MIN="$2"; shift 2 ;;
        --threads)         THREADS="$2"; shift 2 ;;
        --crash-node)      CRASH_NODE="$2"; shift 2 ;;
        --crash-duration)  CRASH_DURATION="$2"; shift 2 ;;
        --jitter-node)         JITTER_NODE="$2"; shift 2 ;;
        --jitter-iface)        JITTER_IFACE="$2"; shift 2 ;;
        --jitter-delay)        JITTER_DELAY="$2"; shift 2 ;;
        --jitter-stddev)       JITTER_STDDEV="$2"; shift 2 ;;
        --jitter-distribution) JITTER_DISTRIBUTION="$2"; shift 2 ;;
        --scenarios)       SCENARIOS="$2"; shift 2 ;;
        --faults)          FAULTS="$2"; shift 2 ;;
        --servers)         SERVERS="$2"; shift 2 ;;
        --seed)            SEED_BASE="$2"; shift 2 ;;
        --dry-run)         DRY_RUN=1; shift ;;
        --help|-h)
            sed -n '2,45p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "Erro: argumento desconhecido '$1'" >&2; exit 2 ;;
    esac
done

log() { echo "[$(date -u +%H:%M:%S)] run-baseline: $*"; }

# O jar so e exigido em execucao real; o --dry-run descreve o plano em
# qualquer maquina (inclusive Windows/WSL antes do reboot da VM).
if [[ "${DRY_RUN}" -eq 0 ]] && { [[ -z "${JAR}" || ! -f "${JAR}" ]]; }; then
    echo "Erro: jar do workload nao encontrado em ${REPO_DIR}/workload/target/" >&2
    echo "      Rode: mvn -f workload/pom.xml package" >&2
    exit 3
fi
log "jar: ${JAR:-<dry-run: jar nao exigido>}"
log "reps=${REPS} duration=${DURATION}s warmup-min=${WARMUP_MIN}s threads=${THREADS}"
log "scenarios={${SCENARIOS}} faults={${FAULTS}} crash=${CRASH_NODE}/${CRASH_DURATION}s"
log "jitter=${JITTER_NODE}/${JITTER_IFACE} delay=${JITTER_DELAY} stddev=${JITTER_STDDEV} dist=${JITTER_DISTRIBUTION}"

# ----------------------------------------------------- one workload run
# Executa uma repeticao gravando em <dir>/rep-NNN.csv. Para fault=F1, agenda
# inject-crash.sh para disparar apos o warm-up + uma folga, dentro da janela
# de medicao.
rodar_rep() {
    local cenario="$1" fault="$2" rep="$3" destino="$4"
    local nnn; nnn="$(printf '%03d' "${rep}")"

    # Cada repeticao grava num diretorio-scratch proprio para evitar que a
    # CLI (que sempre emite rep-001.csv) sobrescreva a saida da repeticao
    # anterior. O CSV unico e movido para <destino>/rep-NNN.csv no fim.
    local scratch="${destino}/.scratch-${nnn}"
    mkdir -p "${scratch}"

    local fault_pid=""
    if [[ "${fault}" == "F1" ]]; then
        # Dispara o crash apos (warmup + 5s), ja na janela de medicao.
        local atraso=$(( WARMUP_MIN + 5 ))
        ( sleep "${atraso}"
          "${INJECT}" --node "${CRASH_NODE}" --duration "${CRASH_DURATION}" \
              >> "${destino}/inject-crash-rep${nnn}.log" 2>&1 ) &
        fault_pid=$!
        log "  rep ${rep}: crash F1 agendado em +${atraso}s (pid ${fault_pid})"
    elif [[ "${fault}" == "F3" ]]; then
        # Dispara o jitter apos (warmup + 5s) e cobre o restante da janela
        # de medicao (DURATION - warmup - margem). Floor de 1s. F3 NAO
        # derruba o no; o inject-jitter.sh garante a remocao do netem.
        local atraso=$(( WARMUP_MIN + 5 ))
        local jdur=$(( DURATION - WARMUP_MIN - 5 ))
        [[ "${jdur}" -lt 1 ]] && jdur=1
        ( sleep "${atraso}"
          "${INJECT_JITTER}" --node "${JITTER_NODE}" --iface "${JITTER_IFACE}" \
              --delay "${JITTER_DELAY}" --jitter "${JITTER_STDDEV}" \
              --distribution "${JITTER_DISTRIBUTION}" --duration "${jdur}" \
              >> "${destino}/inject-jitter-rep${nnn}.log" 2>&1 ) &
        fault_pid=$!
        log "  rep ${rep}: jitter F3 agendado em +${atraso}s por ${jdur}s (pid ${fault_pid})"
    fi

    java -jar "${JAR}" \
        --scenario "${cenario}" \
        --duration "${DURATION}" \
        --warmup-min-sec "${WARMUP_MIN}" \
        --ops 0 \
        --rep 1 \
        --threads "${THREADS}" \
        --servers "${SERVERS}" \
        --username "${USERNAME}" \
        --password "${PASSWORD}" \
        --seed "$(( SEED_BASE + rep ))" \
        --csv-dir "${scratch}" \
        > "${destino}/rep-${nnn}.stdout.log" 2>&1 || true

    if [[ -f "${scratch}/rep-001.csv" ]]; then
        mv -f "${scratch}/rep-001.csv" "${destino}/rep-${nnn}.csv"
    else
        log "  AVISO: rep ${rep} nao gerou CSV (ver rep-${nnn}.stdout.log)"
    fi
    rm -rf "${scratch}"

    if [[ -n "${fault_pid}" ]]; then
        wait "${fault_pid}" 2>/dev/null || true
        if [[ "${fault}" == "F1" ]]; then
            # Aguarda o no voltar a HEALTHY antes da proxima repeticao.
            aguardar_healthy
        elif [[ "${fault}" == "F3" ]]; then
            # F3 nao derruba o no (size=3 mantido): basta garantir que
            # nenhuma disciplina netem ficou residual no no alvo.
            verificar_sem_netem
        fi
    fi
}

# ----------------------------------------------------- health gate
aguardar_healthy() {
    local i size
    for i in $(seq 1 30); do
        size=$(curl -s --digest -u "${USERNAME}:${PASSWORD}" \
            http://localhost:11222/rest/v2/cache-managers/default/health 2>/dev/null \
            | python -c 'import sys,json
try: print(json.load(sys.stdin)["cluster_health"]["number_of_nodes"])
except Exception: print(0)' 2>/dev/null || echo 0)
        if [[ "${size}" == "3" ]]; then
            log "  cluster reestabelecido (size=3)"
            return 0
        fi
        sleep 3
    done
    log "  AVISO: cluster nao voltou a size=3 apos a janela de espera"
    return 0
}

# ----------------------------------------------------- netem residual gate
# Verificacao leve pos-F3: o inject-jitter.sh ja remove o netem (e tem trap
# de cleanup), mas confirmamos aqui que o no alvo nao ficou degradado entre
# repeticoes. Limpa de forma idempotente caso encontre residuo.
# Mesmo mecanismo do inject-jitter.sh: resolve o PID do container e opera o
# tc no netns via 'sudo nsenter -t <pid> -n tc ...' (a imagem nao tem
# tc/NET_ADMIN, entao 'podman exec' nao serve). Se o PID nao resolver,
# apenas registra e segue (a verificacao e best-effort).
verificar_sem_netem() {
    local pid
    pid="$(${PODMAN} inspect -f '{{.State.Pid}}' "${JITTER_NODE}" 2>/dev/null || true)"
    if [[ -z "${pid}" || "${pid}" == "0" ]]; then
        log "  AVISO: nao resolveu PID de ${JITTER_NODE}; pulando gate de netem residual"
        return 0
    fi
    if sudo nsenter -t "${pid}" -n tc qdisc show dev "${JITTER_IFACE}" 2>/dev/null \
            | grep -q netem; then
        log "  AVISO: netem residual em ${JITTER_NODE} (${JITTER_IFACE}); removendo"
        sudo nsenter -t "${pid}" -n tc qdisc del dev "${JITTER_IFACE}" root \
            >/dev/null 2>&1 || true
    else
        log "  ${JITTER_NODE} sem netem residual (cluster intacto, size=3)"
    fi
}

# ----------------------------------------------------- main loop
for cenario in ${SCENARIOS}; do
    for fault in ${FAULTS}; do
        destino="${RUNS_DIR}/${cenario}-${fault}"
        log "== ${cenario} / ${fault} -> ${destino} =="
        if [[ "${DRY_RUN}" -eq 1 ]]; then
            log "  dry-run: ${REPS} reps de ${DURATION}s seriam executadas"
            continue
        fi
        mkdir -p "${destino}"
        for rep in $(seq 1 "${REPS}"); do
            log "  rep ${rep}/${REPS}"
            rodar_rep "${cenario}" "${fault}" "${rep}" "${destino}"
        done
        # summary.json por cenario (glob nao-recursivo: aponta para a pasta).
        ( cd "${ANALYSIS_DIR}" && python percentis.py "${destino}" ) || \
            log "  AVISO: percentis.py falhou para ${destino}"
    done
done

# ----------------------------------------------------- comparacao none x F1
if [[ "${DRY_RUN}" -eq 0 ]] && echo "${FAULTS}" | grep -qw none \
        && echo "${FAULTS}" | grep -qw F1; then
    for cenario in ${SCENARIOS}; do
        dir_none="${RUNS_DIR}/${cenario}-none"
        dir_f1="${RUNS_DIR}/${cenario}-F1"
        if [[ -d "${dir_none}" && -d "${dir_f1}" ]]; then
            log "== comparacao ${cenario}: none x F1 =="
            ( cd "${ANALYSIS_DIR}" && \
              python compare_scenarios.py "${dir_none}" "${dir_f1}" \
                  --saida "${dir_f1}/comparacao.json" ) || \
                log "  AVISO: compare_scenarios.py falhou para ${cenario}"
        fi
    done
fi

# ----------------------------------------------------- comparacao none x F3
if [[ "${DRY_RUN}" -eq 0 ]] && echo "${FAULTS}" | grep -qw none \
        && echo "${FAULTS}" | grep -qw F3; then
    for cenario in ${SCENARIOS}; do
        dir_none="${RUNS_DIR}/${cenario}-none"
        dir_f3="${RUNS_DIR}/${cenario}-F3"
        if [[ -d "${dir_none}" && -d "${dir_f3}" ]]; then
            log "== comparacao ${cenario}: none x F3 =="
            ( cd "${ANALYSIS_DIR}" && \
              python compare_scenarios.py "${dir_none}" "${dir_f3}" \
                  --saida "${dir_f3}/comparacao.json" ) || \
                log "  AVISO: compare_scenarios.py falhou para ${cenario}"
        fi
    done
fi

log "bateria concluida."
