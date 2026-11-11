#!/usr/bin/env bash
#
# inject-jitter.sh
#
# Injeta a falha F3 (jitter/atraso na rede de um no) no cluster
# Infinispan, conforme Cap. 3 secao 3.3.2 (modelo de falhas, F3) e
# parametro T18 da Tabela 1 do Cap. 3 secao 3.3.5 da monografia. Aplica
# uma disciplina de fila 'netem' do tc (iproute2) sobre a interface
# egress do no alvo. O netem e aplicado a partir do HOST da VM, ENTRANDO
# no namespace de rede do container via 'nsenter' (sudo nsenter -t <pid>
# -n tc qdisc ...), e NAO via 'podman exec'. A imagem oficial Infinispan
# (UBI minimal) nao traz 'tc' no PATH nem a capability NET_ADMIN, entao o
# 'podman exec <node> tc ...' falha; o host (VM Ubuntu) tem util-linux
# (nsenter), iproute2 (tc) e o modulo sch_netem, e age sobre o eth0 do
# container pelo namespace de rede. O workload roda na VM e alcanca os
# nos pelas portas Hot Rod mapeadas. Ao fim da janela (ou em qualquer
# sinal/erro), a disciplina e SEMPRE removida.
#
# Calibracao padrao (execucao oficial TCC-I, aprovada pelo Gestor):
# 'delay 20ms 13ms distribution normal'. Para uma normal, o p99 do
# atraso adicionado e ~ MEAN + 2,326*JITTER = 20 + 2,326*13 ~= 50ms,
# atingindo o alvo de cauda +50ms do T18. A distribuicao lognormal
# estrita fica como rodada de sensibilidade (--distribution lognormal,
# que exige tabela /usr/lib/tc/lognormal.dist instalada no HOST).
#
# Dependencias (execucao real): podman (CLI) para resolver o PID do
# container; nsenter (util-linux) e tc (iproute2) NO HOST; sudo para
# entrar no namespace de rede; e o modulo de kernel 'sch_netem'. O
# container NAO precisa de tc nem de NET_ADMIN. O kernel do WSL2 NAO traz
# sch_netem; por isso a execucao real exige a VM (Hyper-V/Ubuntu com
# linux-modules-extra). O '--dry-run' NAO requer nenhuma dependencia e
# roda em qualquer maquina (inclusive Windows/WSL antes do reboot).
#
# Nota de operacao: cada celula da bateria chama este script e cada
# chamada faz varios 'sudo nsenter'. Como o cache de credencial do sudo
# expira (~15 min), recomenda-se configurar sudo SEM senha para o usuario
# da bateria na VM (ver docs/testes.md e o runbook do F3).
#
# Exit codes:
#   0  sucesso (netem aplicado, aguardado, removido)
#   1  erro generico
#   2  argumento invalido ou --help
#   3  dependencia ausente (podman, nsenter ou sudo no host)
#   4  no nao encontrado, nao-running ou PID nao resolvido
#   5  netem rejeitado pelo kernel (qdisc kind unknown / sch_netem ausente)
#
# Cobertura: T18 (jitter/atraso, alvo p99 = +50ms via tc-netem).
set -euo pipefail

NOME="$(basename "$0")"
# Binario do podman. Override para cluster ROOTFUL (VM Hyper-V/Ubuntu):
#   PODMAN="sudo podman" bash scripts/inject-jitter.sh ...
# No rootless o port-forwarding (pasta) faz bypass do eth0 e o netem nao
# alcanca o trafego do cliente; o cluster oficial F3 roda em rootful, logo o
# 'podman inspect' precisa rodar como root para enxergar o PID do container.
PODMAN="${PODMAN:-podman}"
NODE_PADRAO="isn2"
IFACE_PADRAO="eth0"
DELAY_PADRAO="20ms"
JITTER_PADRAO="13ms"
DISTRIBUICAO_PADRAO="normal"
# Janela padrao alinhada a T14 (30 min com falha). A bateria
# (run-baseline.sh) sobrescreve para cobrir a janela de medicao da celula.
DURACAO_PADRAO=1800
TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

usage() {
    cat <<EOF
Uso: ${NOME} [--node <nome>] [--iface <if>] [--delay <Nms>] [--jitter <Nms>]
            [--distribution <dist>] [--duration <segundos>]
            [--dry-run] [--help]

Injeta atraso/jitter (F3) no no alvo via nsenter host-side:
'sudo nsenter -t <pid> -n tc qdisc add dev <iface> root netem ...', onde
<pid> e o PID do container (podman inspect). NAO usa 'podman exec' (a
imagem nao tem tc/NET_ADMIN). Cobre T18 da Tabela 1: alvo p99 = +50ms com
os parametros padrao (delay=${DELAY_PADRAO} jitter=${JITTER_PADRAO} distribution=${DISTRIBUICAO_PADRAO}).
A disciplina netem e SEMPRE removida ao fim da janela ou em interrupcao
(trap de cleanup), para nao deixar o no degradado.

Opcoes:
  --node <nome>          Container alvo. Padrao: ${NODE_PADRAO}
  --iface <if>           Interface egress dentro do container. Padrao: ${IFACE_PADRAO}
  --delay <Nms>          Atraso medio (sufixo ms/us/s). Padrao: ${DELAY_PADRAO}
  --jitter <Nms>         Jitter/desvio (sufixo ms/us/s). Padrao: ${JITTER_PADRAO}
  --distribution <dist>  Distribuicao do netem: normal (padrao, TCC-I),
                         lognormal (sensibilidade; exige tabela custom no
                         host), pareto, paretonormal. Padrao: ${DISTRIBUICAO_PADRAO}
  --duration <segundos>  Janela de injecao em segundos. Padrao: ${DURACAO_PADRAO} (T14)
  --dry-run              Imprime o plano de comandos e sai 0, sem deps.
  --help                 Mostra esta mensagem.

Dependencias (execucao real, no host da VM): podman, nsenter (util-linux),
tc (iproute2), sudo e o modulo sch_netem. O container NAO precisa de tc
nem de NET_ADMIN. Recomenda-se sudo sem senha para a bateria.

Variavel de ambiente:
  PODMAN  Binario do podman usado para resolver o PID do container.
          Padrao: 'podman' (rootless). Para o cluster ROOTFUL (VM Hyper-V,
          onde o netem so atinge o trafego do cliente), prefixe:
            PODMAN="sudo podman" ${NOME} --node isn2 --duration 90
          O nsenter/tc continuam com 'sudo' (host), independentemente disso.

Exemplos:
  ${NOME} --node isn2 --duration 90
  ${NOME} --node isn2 --distribution lognormal --delay 25ms --jitter 18ms
  PODMAN="sudo podman" ${NOME} --node isn2 --duration 90   # cluster rootful
  ${NOME} --dry-run
EOF
}

NODE="${NODE_PADRAO}"
IFACE="${IFACE_PADRAO}"
DELAY="${DELAY_PADRAO}"
JITTER="${JITTER_PADRAO}"
DISTRIBUICAO="${DISTRIBUICAO_PADRAO}"
DURACAO="${DURACAO_PADRAO}"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --node)
            [[ $# -ge 2 ]] || { echo "Erro: --node exige valor" >&2; exit 2; }
            NODE="$2"; shift 2 ;;
        --iface)
            [[ $# -ge 2 ]] || { echo "Erro: --iface exige valor" >&2; exit 2; }
            IFACE="$2"; shift 2 ;;
        --delay)
            [[ $# -ge 2 ]] || { echo "Erro: --delay exige valor" >&2; exit 2; }
            DELAY="$2"; shift 2 ;;
        --jitter)
            [[ $# -ge 2 ]] || { echo "Erro: --jitter exige valor" >&2; exit 2; }
            JITTER="$2"; shift 2 ;;
        --distribution)
            [[ $# -ge 2 ]] || { echo "Erro: --distribution exige valor" >&2; exit 2; }
            DISTRIBUICAO="$2"; shift 2 ;;
        --duration)
            [[ $# -ge 2 ]] || { echo "Erro: --duration exige valor" >&2; exit 2; }
            DURACAO="$2"; shift 2 ;;
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

# --delay/--jitter: numero (inteiro ou decimal) com sufixo de tempo do tc.
TEMPO_RE='^[0-9]+(\.[0-9]+)?(us|ms|s)$'
if ! [[ "${DELAY}" =~ ${TEMPO_RE} ]]; then
    echo "Erro: --delay deve ser <numero>{us|ms|s}, recebido '${DELAY}'" >&2
    exit 2
fi
if ! [[ "${JITTER}" =~ ${TEMPO_RE} ]]; then
    echo "Erro: --jitter deve ser <numero>{us|ms|s}, recebido '${JITTER}'" >&2
    exit 2
fi

case "${DISTRIBUICAO}" in
    normal|lognormal|pareto|paretonormal) ;;
    *)
        echo "Erro: --distribution deve ser normal, lognormal, pareto ou paretonormal" >&2
        exit 2 ;;
esac

if ! [[ "${DURACAO}" =~ ^[0-9]+$ ]] || [[ "${DURACAO}" -lt 1 ]]; then
    echo "Erro: --duration deve ser inteiro positivo, recebido '${DURACAO}'" >&2
    exit 2
fi

log() {
    echo "[${TIMESTAMP}] ${NOME}: $*"
}

# Plano (texto) do mecanismo nsenter host-side. No --dry-run o <pid> ainda
# nao foi resolvido (sem podman), entao usa-se o placeholder <pid>.
PLANO_INSPECT="${PODMAN} inspect -f '{{.State.Pid}}' ${NODE}"
PLANO_ADD="sudo nsenter -t <pid> -n tc qdisc add dev ${IFACE} root netem delay ${DELAY} ${JITTER} distribution ${DISTRIBUICAO}"
PLANO_SHOW="sudo nsenter -t <pid> -n tc qdisc show dev ${IFACE}"
PLANO_DEL="sudo nsenter -t <pid> -n tc qdisc del dev ${IFACE} root"

log "Plano: NODE=${NODE} IFACE=${IFACE} DELAY=${DELAY} JITTER=${JITTER} DIST=${DISTRIBUICAO} DURACAO=${DURACAO}s DRY_RUN=${DRY_RUN}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    log "Comandos previstos (netem aplicado via nsenter a partir do host):"
    log "  pid=\$(${PLANO_INSPECT})"
    log "  ${PLANO_ADD}"
    log "  sleep ${DURACAO}"
    log "  ${PLANO_DEL}"
    log "dry-run: nada executado (sem podman/nsenter/sudo/sch_netem)"
    exit 0
fi

# ---------------------------------------------------------- dependencias
if ! command -v ${PODMAN} >/dev/null 2>&1; then
    echo "Erro: '${PODMAN}' nao encontrado no PATH" >&2
    exit 3
fi
if ! command -v nsenter >/dev/null 2>&1; then
    echo "Erro: 'nsenter' (util-linux) nao encontrado no PATH do host" >&2
    echo "      O netem do F3 e aplicado a partir do host via nsenter." >&2
    exit 3
fi
if ! command -v sudo >/dev/null 2>&1; then
    echo "Erro: 'sudo' nao encontrado no PATH do host" >&2
    echo "      nsenter -n exige privilegio para entrar no netns do container." >&2
    exit 3
fi

if ! ${PODMAN} inspect "${NODE}" >/dev/null 2>&1; then
    echo "Erro: container '${NODE}' nao encontrado" >&2
    exit 4
fi

ESTADO="$(${PODMAN} inspect -f '{{.State.Status}}' "${NODE}")"
if [[ "${ESTADO}" != "running" ]]; then
    echo "Erro: container '${NODE}' nao esta running (estado='${ESTADO}')" >&2
    exit 4
fi

# Resolve o PID do container ANTES de registrar o trap, para que o cleanup
# opere sobre um pid estavel mesmo se o 'add' falhar no meio. Se vazio (ou
# 0), o container nao esta com processo valido -> exit 4.
PID="$(${PODMAN} inspect -f '{{.State.Pid}}' "${NODE}" 2>/dev/null || true)"
if [[ -z "${PID}" || "${PID}" == "0" ]]; then
    echo "Erro: nao foi possivel resolver o PID do container '${NODE}'." >&2
    echo "      ${PODMAN} inspect retornou PID vazio/0 (container nao-running?)." >&2
    exit 4
fi
log "PID do container ${NODE}: ${PID}"

# Helper: executa 'tc' no namespace de rede do container via nsenter.
nstc() {
    sudo nsenter -t "${PID}" -n tc "$@"
}

# ----------------------------------------------------------- cleanup/trap
# Registrar o cleanup ANTES de aplicar a disciplina: requisito de robustez
# nº1 do F3. Mesmo em Ctrl-C/erro o netem e removido. Idempotente: nao
# falha se a disciplina ja nao existir (2>/dev/null || true). Usa o PID ja
# resolvido acima.
cleanup() {
    sudo nsenter -t "${PID}" -n tc qdisc del dev "${IFACE}" root >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

# Sonda netem: aplica e remove uma regra trivial NO HOST, no netns do
# container, via nsenter. Se o 'sch_netem' nao existir (sintoma do WSL2:
# "Specified qdisc kind is unknown") ou 'tc' faltar no host, falha aqui com
# diagnostico, em vez de stack trace mais adiante. Como o trap ja esta
# ativo, qualquer residuo da sonda e removido. Falha de sonda => exit 5
# (netem rejeitado pelo kernel).
log "sondando netem em ${NODE} (pid ${PID}, ${IFACE}) via nsenter"
SONDA_OUT="$(sudo nsenter -t "${PID}" -n sh -c \
    "tc qdisc add dev ${IFACE} root netem delay 1ms && tc qdisc del dev ${IFACE} root" 2>&1)" \
    || {
        echo "Erro: sonda netem falhou em '${NODE}' (pid ${PID}, ${IFACE})." >&2
        echo "      Saida: ${SONDA_OUT}" >&2
        echo "      Provavel sch_netem/tc ausente no kernel do HOST (sintoma do WSL2)." >&2
        echo "      Veja docs/plano-f3-vm-netem.md (gate da VM, secao 3.5)." >&2
        exit 5
    }

# -------------------------------------------------------------- aplicar
log "sudo nsenter -t ${PID} -n tc qdisc add dev ${IFACE} root netem delay ${DELAY} ${JITTER} distribution ${DISTRIBUICAO}"
if ! ADD_OUT="$(nstc qdisc add dev "${IFACE}" root netem \
        delay "${DELAY}" "${JITTER}" distribution "${DISTRIBUICAO}" 2>&1)"; then
    echo "Erro: netem rejeitado ao aplicar a disciplina em '${NODE}' (pid ${PID}, ${IFACE})." >&2
    echo "      Saida: ${ADD_OUT}" >&2
    if echo "${ADD_OUT}" | grep -qi "unknown"; then
        echo "      'qdisc kind unknown' => sch_netem ausente no kernel." >&2
    fi
    echo "      (o trap de cleanup removera qualquer disciplina parcial)" >&2
    exit 5
fi

log "disciplina efetiva:"
nstc qdisc show dev "${IFACE}" | sed "s/^/    /" || true

log "aguardando ${DURACAO}s com F3 ativa"
sleep "${DURACAO}"

# -------------------------------------------------------------- remover
log "sudo nsenter -t ${PID} -n tc qdisc del dev ${IFACE} root"
nstc qdisc del dev "${IFACE}" root >/dev/null 2>&1 || true

# Verificacao: nenhuma disciplina netem residual na interface.
if nstc qdisc show dev "${IFACE}" 2>/dev/null | grep -q netem; then
    echo "Aviso: ainda ha netem residual em ${NODE} (${IFACE}); o trap tentara remover." >&2
else
    log "OK: F3 (jitter) injetada por ${DURACAO}s e removida; sem netem residual"
fi
