#!/usr/bin/env bash
#
# inject-crash.sh
#
# Injeta a falha F1 (crash silencioso de no) no cluster Infinispan, conforme
# Cap. 3 secao 3.3.2 e parametros T16/T17 da Tabela 1 do Cap. 3 secao 3.3.5 da
# monografia: o no deixa de responder sem aviso previo e sem saida ordenada, os
# pares so percebem a queda pelo FD_ALL3 (timeout padrao de 40 s, T16) e o no
# volta depois de uma duracao fixa (T17, 60 s no minimo).
#
# Mecanismo do modo padrao (silencioso, opcao B de docs/prompt-f1-kill-2026-10-27.md):
#   1. 'sudo nsenter -t <pid> -n ip link set dev eth0 down' no netns do container;
#   2. logo em seguida 'podman kill <no> --signal KILL' (ExitCode 137);
#   3. espera de --duration segundos;
#   4. 'podman start <no>' (mesmo container; netns novo, eth0 de pe).
# Por que o isolamento vem ANTES do kill: a pilha tem FD_SOCK2, que mantem uma
# conexao TCP com o vizinho do anel e suspeita dele na hora em que a conexao
# fecha. Um processo morto por SIGKILL tem os sockets fechados pelo kernel, que
# manda FIN/RST aos pares enquanto o netns existe: a queda seria detectada em
# ~1 s pelo FD_SOCK2, nao pelo FD_ALL3. Com a interface ja DOWN, nenhum FIN sai e
# o FD_ALL3 so decide quando o no some sem fechar conexoes, que e o caso de T16.
# O netns morre com o container (o PID 1 dele morre e o kernel mata o resto),
# entao nao ha regra nem interface residual para limpar; 'podman start' cria um
# netns novo. 'podman rm' nunca e usado (mudaria Created, K8 e a regra (h)).
#
# Outros modos: 'kill' (so o SIGKILL, sem isolar; detectado pelo FD_SOCK2) e
# 'stop' (o F1 de junho de 2026: SIGTERM, saida ordenada com leave). Nenhum dos
# dois e usado no rebaseline; ficam para reproducao e para o smoke comparativo.
#
# Dependencias: podman (CLI); no modo silencioso tambem sudo, nsenter
# (util-linux) e ip (iproute2) no HOST. '--dry-run' e '--help' nao requerem nada.
#
# Exit codes (contrato C2 do run-rebaseline.sh: 1 e 4 => recusa K6; 2, 3 e 5 =>
# exit 6 do runner):
#   0    sucesso
#   1    falha da injecao com o cluster devolvido (isolamento nao confirmado,
#        ExitCode != 137)
#   2    argumento invalido
#   3    dependencia ausente (podman; no modo silencioso tambem sudo, nsenter, ip)
#   4    alvo ausente, nao running, PID invalido, interface inexistente ou guarda
#        de netns (netns do alvo igual ao do PID 1, ou ilegivel)
#   5    infraestrutura (kill/stop nao terminou em 10 s, stop ou start falhou)
#   129/130/143  sinal (HUP/INT/TERM); o trap devolve o no antes de sair
#
# Cobertura: T16/T17 (crash silencioso de 60 s, detectado pelo FD_ALL3).
set -euo pipefail

NOME="$(basename "$0")"
# Binario do podman. Override para cluster ROOTFUL (VM Hyper-V/Ubuntu):
#   PODMAN="sudo podman" bash scripts/inject-crash.sh ...
# Necessario no rootful, onde 'podman' (rootless) nao enxerga os containers
# iniciados via 'sudo podman'.
PODMAN="${PODMAN:-podman}"
NODE_PADRAO="isn2"
DURACAO_PADRAO=60
MODO_PADRAO="silencioso"
IFACE_PADRAO="eth0"
# Espera maxima para o container sair de running depois do kill/stop:
# POLL_N consultas com POLL_S segundos entre elas (~10 s).
POLL_N=50
POLL_S=0.2

usage() {
    cat <<EOF
Uso: ${NOME} [--node <nome>] [--duration <segundos>] [--modo silencioso|kill|stop]
            [--iface <if>] [--dry-run] [--help]

Injeta a falha F1 (crash silencioso) no cluster Infinispan. Cobre T17 da
Tabela 1 (60s no minimo) e respeita o timeout de deteccao do FD_ALL3 (T16 = 40s).

Modos:
  silencioso (padrao)  cala a interface do no no netns dele
                       (sudo nsenter -t <pid> -n ip link set dev <if> down) e
                       logo em seguida mata o container (podman kill --signal
                       KILL, ExitCode 137); espera; podman start. Sem FIN nem
                       leave: os pares so detectam a queda pelo FD_ALL3 (~40 s).
                       O isolamento vem antes do kill porque o FD_SOCK2 detecta
                       em ~1 s o fechamento das conexoes de um processo morto.
                       O netns morre com o container: nao ha residuo a limpar.
  kill                 so o podman kill <no> --signal KILL (sem isolar); detectado
                       pelo FD_SOCK2. Nao usado no rebaseline.
  stop                 podman stop (SIGTERM, saida ordenada com leave), o F1 de
                       junho de 2026. Nao usado no rebaseline.

Opcoes:
  --node <nome>          Nome do container alvo. Padrao: ${NODE_PADRAO}
  --duration <segundos>  Duracao do crash em segundos. Padrao: ${DURACAO_PADRAO}
  --modo <modo>          silencioso, kill ou stop. Padrao: ${MODO_PADRAO}
  --iface <if>           Interface dentro do container (modo silencioso).
                         Padrao: ${IFACE_PADRAO}
  --dry-run              Imprime o plano e sai sem executar (sem podman, sudo,
                         nsenter nem ip).
  --help                 Mostra esta mensagem.

Variavel de ambiente:
  PODMAN  Binario do podman. Padrao: 'podman' (rootless). Para cluster
          ROOTFUL (VM Hyper-V), prefixe: PODMAN="sudo podman" ${NOME} ...

Codigos de saida: 0 ok; 1 injecao falhou com o cluster devolvido (isolamento
nao confirmado, ExitCode != 137); 2 argumento; 3 dependencia ausente; 4 alvo
ausente, parado, PID invalido, interface inexistente ou guarda de netns;
5 infraestrutura (kill nao terminou, start falhou); 129/130/143 sinal.

Exemplos:
  ${NOME} --node isn2 --duration 60
  PODMAN="sudo podman" ${NOME} --node isn1 --duration 90   # cluster rootful
  ${NOME} --modo stop --dry-run
EOF
}

NODE="${NODE_PADRAO}"
DURACAO="${DURACAO_PADRAO}"
MODO="${MODO_PADRAO}"
IFACE="${IFACE_PADRAO}"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --node)
            [[ $# -ge 2 ]] || { echo "Erro: --node exige valor" >&2; exit 2; }
            NODE="$2"; shift 2 ;;
        --duration)
            [[ $# -ge 2 ]] || { echo "Erro: --duration exige valor" >&2; exit 2; }
            DURACAO="$2"; shift 2 ;;
        --modo)
            [[ $# -ge 2 ]] || { echo "Erro: --modo exige valor" >&2; exit 2; }
            MODO="$2"; shift 2 ;;
        --iface)
            [[ $# -ge 2 ]] || { echo "Erro: --iface exige valor" >&2; exit 2; }
            IFACE="$2"; shift 2 ;;
        --dry-run)
            DRY_RUN=1; shift ;;
        --help|-h)
            usage; exit 0 ;;
        *)
            echo "Erro: argumento desconhecido '$1'" >&2
            usage >&2
            exit 2 ;;
    esac
done

if ! [[ "${DURACAO}" =~ ^[0-9]+$ ]] || [[ "${DURACAO}" -lt 1 ]]; then
    echo "Erro: --duration deve ser inteiro positivo, recebido '${DURACAO}'" >&2
    exit 2
fi
case "${MODO}" in
    silencioso|kill|stop) ;;
    *) echo "Erro: --modo deve ser silencioso, kill ou stop, recebido '${MODO}'" >&2; exit 2 ;;
esac
if ! [[ "${IFACE}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
    echo "Erro: --iface invalida '${IFACE}'" >&2
    exit 2
fi
if ! [[ "${NODE}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
    echo "Erro: --node invalido '${NODE}'" >&2
    exit 2
fi

if [[ "${DURACAO}" -lt 40 ]]; then
    echo "Aviso: --duration=${DURACAO} e menor que o timeout padrao do FD_ALL3 (40s)." >&2
    echo "       O cluster pode nao detectar o crash antes do start." >&2
fi

# Carimbo calculado a cada linha, com milissegundos (B-25.1 minimo; achado A4:
# antes todas as linhas tinham o carimbo do inicio do script).
log() {
    echo "[$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)] ${NOME}: $*"
}

log "Plano: NODE=${NODE} MODO=${MODO} DURACAO=${DURACAO}s IFACE=${IFACE} DRY_RUN=${DRY_RUN}"
log "modo=${MODO}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    log "Comandos previstos:"
    case "${MODO}" in
        silencioso)
            log "  pid=\$(${PODMAN} inspect -f '{{.State.Pid}}' ${NODE})   # inteiro > 1"
            log "  guarda: sudo readlink /proc/<pid>/ns/net != sudo readlink /proc/1/ns/net"
            log "  sudo nsenter -t <pid> -n ip link show dev ${IFACE}"
            log "  sudo nsenter -t <pid> -n ip link set dev ${IFACE} down"
            log "  sudo nsenter -t <pid> -n ip -o link show dev ${IFACE}   # confirma que nao esta UP"
            log "  ${PODMAN} kill ${NODE} --signal KILL"
            log "  ${PODMAN} inspect -f '{{.State.ExitCode}}' ${NODE}   # esperado 137" ;;
        kill)
            log "  ${PODMAN} kill ${NODE} --signal KILL"
            log "  ${PODMAN} inspect -f '{{.State.ExitCode}}' ${NODE}   # esperado 137" ;;
        stop)
            log "  ${PODMAN} stop ${NODE}" ;;
    esac
    log "  sleep ${DURACAO}"
    log "  ${PODMAN} start ${NODE}"
    log "dry-run: nada executado"
    exit 0
fi

# ---------------------------------------------------------- dependencias
# ${PODMAN} sem aspas de proposito: aceita "sudo podman" (verifica ambos).
if ! command -v ${PODMAN} >/dev/null 2>&1; then
    echo "Erro: '${PODMAN}' nao encontrado no PATH" >&2
    exit 3
fi
if [[ "${MODO}" == silencioso ]]; then
    for dep in sudo nsenter ip; do
        if ! command -v "${dep}" >/dev/null 2>&1; then
            echo "Erro: '${dep}' nao encontrado no PATH do host (modo silencioso)" >&2
            exit 3
        fi
    done
fi

# ---------------------------------------------------------------- alvo
if ! ${PODMAN} inspect "${NODE}" >/dev/null 2>&1; then
    echo "Erro: container '${NODE}' nao encontrado" >&2
    exit 4
fi

ESTADO="$(${PODMAN} inspect -f '{{.State.Status}}' "${NODE}")"
if [[ "${ESTADO}" != "running" ]]; then
    echo "Erro: container '${NODE}' nao esta running (estado='${ESTADO}')" >&2
    exit 4
fi

nsip() { # nsip <args...>: ip no netns do container alvo
    sudo nsenter -t "${PID}" -n ip "$@"
}

# interface_up: 0 se a lista de flags de 'ip -o link show' contem UP.
interface_up() {
    local out="" rc=0 flags
    out="$(nsip -o link show dev "${IFACE}" 2>&1)" || rc=$?
    [[ "${rc}" -eq 0 ]] || return 2
    if [[ "${out}" =~ \<([^>]*)\> ]]; then
        flags=",${BASH_REMATCH[1]},"
        [[ "${flags}" == *",UP,"* ]] && return 0
        return 1
    fi
    return 2
}

PID=""
if [[ "${MODO}" == silencioso ]]; then
    PID="$(${PODMAN} inspect -f '{{.State.Pid}}' "${NODE}" 2>/dev/null)" || PID=""
    if ! [[ "${PID}" =~ ^[0-9]+$ ]] || [[ "${PID}" -le 1 ]]; then
        echo "Erro: PID invalido para '${NODE}' (obtido '${PID}'); esperado inteiro > 1" >&2
        exit 4
    fi
    # GUARDA DE NETNS antes de qualquer 'ip': um PID errado (ou um container em
    # rede do host) faria o 'link set down' derrubar a NIC do host/VM.
    NS_ALVO="$(sudo readlink "/proc/${PID}/ns/net" 2>/dev/null)" || NS_ALVO=""
    NS_HOST="$(sudo readlink /proc/1/ns/net 2>/dev/null)" || NS_HOST=""
    if [[ -z "${NS_ALVO}" || -z "${NS_HOST}" ]]; then
        echo "Erro: nao foi possivel ler o netns de '${NODE}' (pid ${PID}) ou do PID 1; recusando por seguranca" >&2
        exit 4
    fi
    if [[ "${NS_ALVO}" == "${NS_HOST}" ]]; then
        echo "Erro: '${NODE}' (pid ${PID}) compartilha o netns do host (${NS_HOST}); recusando para nao derrubar a NIC do host" >&2
        exit 4
    fi
    if ! nsip link show dev "${IFACE}" >/dev/null 2>&1; then
        echo "Erro: interface '${IFACE}' nao existe no netns de '${NODE}' (pid ${PID})" >&2
        exit 4
    fi
fi

# ----------------------------------------------------------- trap por fase
# FASE: 0 nada alterado; 1 isolado ou kill/stop em curso (container ainda pode
# estar running); 2 morto (espera); 3 start feito.
FASE=0
SLEEP_PID=""
TRATADO=0

tratar_sinal() { # tratar_sinal <exit>
    local codigo="$1" r=0
    trap '' INT TERM HUP
    if [[ "${TRATADO}" -eq 1 ]]; then exit "${codigo}"; fi
    TRATADO=1
    if [[ -n "${SLEEP_PID}" ]]; then kill "${SLEEP_PID}" 2>/dev/null || r=$?; fi
    case "${FASE}" in
        0) log "trap: sinal (exit ${codigo}) antes da injecao; nada a desfazer" ;;
        1)
            r=0
            log "trap: sinal (exit ${codigo}) com ${NODE} isolado ou em queda; ${PODMAN} kill ${NODE} e ${PODMAN} start ${NODE}"
            ${PODMAN} kill "${NODE}" --signal KILL >/dev/null 2>&1 || r=$?
            [[ "${r}" -eq 0 ]] || log "trap: AVISO: podman kill ${NODE} saiu com ${r}"
            r=0; ${PODMAN} start "${NODE}" >/dev/null 2>&1 || r=$?
            [[ "${r}" -eq 0 ]] || log "trap: AVISO: podman start ${NODE} saiu com ${r}" ;;
        2)
            r=0
            log "trap: sinal (exit ${codigo}) durante a espera; ${PODMAN} start ${NODE}"
            ${PODMAN} start "${NODE}" >/dev/null 2>&1 || r=$?
            [[ "${r}" -eq 0 ]] || log "trap: AVISO: podman start ${NODE} saiu com ${r}" ;;
        *) log "trap: sinal (exit ${codigo}) depois do start; nada a desfazer" ;;
    esac
    exit "${codigo}"
}
# Armado ANTES do isolamento. O 'sleep' da espera roda em segundo plano com
# 'wait' para que o trap rode durante a espera (um 'sleep' em primeiro plano
# adiaria o trap ate o fim da duracao).
trap 'tratar_sinal 130' INT
trap 'tratar_sinal 143' TERM
trap 'tratar_sinal 129' HUP

# esperar_saida: 0 quando o container sai de running (ate ~10 s).
esperar_saida() {
    local k est
    for ((k = 0; k < POLL_N; k++)); do
        est="$(${PODMAN} inspect -f '{{.State.Status}}' "${NODE}" 2>/dev/null)" || est=""
        [[ "${est}" == "running" ]] || return 0
        sleep "${POLL_S}"
    done
    return 1
}

ANOMALIA=0

# --------------------------------------------------------------- injecao
if [[ "${MODO}" == silencioso ]]; then
    FASE=1
    log "isolando ${NODE}: ip link set dev ${IFACE} down (pid ${PID})"
    rd=0; nsip link set dev "${IFACE}" down || rd=$?
    rc=0; interface_up || rc=$?
    if [[ "${rc}" -ne 1 ]]; then
        log "ERRO: ${IFACE} de ${NODE} nao confirmou DOWN (link set down rc=${rd}, verificacao rc=${rc}); tentando link set up"
        r=0; nsip link set dev "${IFACE}" up || r=$?
        log "link set dev ${IFACE} up saiu com ${r}; injecao abortada sem kill"
        FASE=0
        exit 1
    fi
fi

if [[ "${MODO}" == stop ]]; then
    FASE=1
    log "${PODMAN} stop ${NODE}"
    rc=0; ${PODMAN} stop "${NODE}" || rc=$?
    if [[ "${rc}" -ne 0 ]]; then
        log "ERRO: ${PODMAN} stop ${NODE} saiu com ${rc}"
        exit 5
    fi
else
    FASE=1
    log "${PODMAN} kill ${NODE} (--signal KILL)"
    rc=0; ${PODMAN} kill "${NODE}" --signal KILL || rc=$?
    [[ "${rc}" -eq 0 ]] || log "AVISO: ${PODMAN} kill ${NODE} saiu com ${rc}"
fi

if ! esperar_saida; then
    log "ERRO: ${NODE} continua running ~10 s depois do ${MODO}"
    if [[ "${MODO}" == silencioso ]]; then
        r=0; nsip link set dev "${IFACE}" up || r=$?
        log "tentativa de devolver ${IFACE} de ${NODE} (link set up) saiu com ${r}"
    fi
    exit 5
fi
FASE=2

EXITCODE="$(${PODMAN} inspect -f '{{.State.ExitCode}}' "${NODE}" 2>/dev/null)" || EXITCODE=""
log "${NODE} ExitCode=${EXITCODE}"
if [[ "${MODO}" != stop && "${EXITCODE}" != "137" ]]; then
    log "ANOMALIA: ExitCode=${EXITCODE:-<vazio>} (esperado 137); seguindo com a espera e o start para devolver o cluster"
    ANOMALIA=1
fi

log "aguardando ${DURACAO}s"
sleep "${DURACAO}" &
SLEEP_PID=$!
wait "${SLEEP_PID}"
SLEEP_PID=""

log "${PODMAN} start ${NODE}"
rc=0; ${PODMAN} start "${NODE}" || rc=$?
if [[ "${rc}" -ne 0 ]]; then
    log "ERRO: ${PODMAN} start ${NODE} saiu com ${rc}"
    exit 5
fi
FASE=3

if [[ "${ANOMALIA}" -eq 1 ]]; then
    log "FALHA: F1 (modo=${MODO}) com ExitCode=${EXITCODE:-<vazio>}; reentrada de ${NODE} acionada"
    exit 1
fi
log "OK: F1 (modo=${MODO}) injetada e reentrada de ${NODE} acionada"
