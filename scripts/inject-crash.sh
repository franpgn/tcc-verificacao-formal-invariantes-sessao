#!/usr/bin/env bash
#
# inject-crash.sh
#
# Injeta a falha F1 (crash silencioso de no) no cluster Infinispan,
# conforme Cap. 3 secao 3.3.2 e parametro T17 da Tabela 1 do Cap. 3
# secao 3.3.5 da monografia. O no alvo e suspenso por uma duracao
# fixa, suficientemente longa para ser detectada pelo FD_ALL3 (cujo
# timeout padrao e 40s, T16) e em seguida reativado.
#
# Dependencias: podman (CLI). Sem aprovacao do autor para o sandbox
# atual; smoke-test via --dry-run nao requer podman.
#
# Exit codes:
#   0  sucesso
#   1  erro generico
#   2  argumento invalido ou --help
#   3  dependencia ausente (podman)
#   4  no nao encontrado ou em estado inesperado
#
# Cobertura: T17 (crash 60s no minimo).
set -euo pipefail

NOME="$(basename "$0")"
# Binario do podman. Override para cluster ROOTFUL (VM Hyper-V/Ubuntu):
#   PODMAN="sudo podman" bash scripts/inject-crash.sh ...
# Necessario para o re-smoke F1 no rootful, onde 'podman' (rootless) nao
# enxerga os containers iniciados via 'sudo podman'.
PODMAN="${PODMAN:-podman}"
NODE_PADRAO="isn2"
DURACAO_PADRAO=60
TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

usage() {
    cat <<EOF
Uso: ${NOME} [--node <nome>] [--duration <segundos>] [--dry-run] [--help]

Injeta crash silencioso (F1) no cluster Infinispan via 'podman stop'
seguido de espera e 'podman start'. Cobre T17 da Tabela 1 (60s no
minimo) e respeita o timeout de deteccao do FD_ALL3 (T16 = 40s).

Opcoes:
  --node <nome>          Nome do container alvo. Padrao: ${NODE_PADRAO}
  --duration <segundos>  Duracao do crash em segundos. Padrao: ${DURACAO_PADRAO}
  --dry-run              Imprime o plano e sai sem executar.
  --help                 Mostra esta mensagem.

Variavel de ambiente:
  PODMAN  Binario do podman. Padrao: 'podman' (rootless). Para cluster
          ROOTFUL (VM Hyper-V), prefixe: PODMAN="sudo podman" ${NOME} ...

Exemplos:
  ${NOME} --node isn1 --duration 90
  PODMAN="sudo podman" ${NOME} --node isn1 --duration 90   # cluster rootful
  ${NOME} --dry-run
EOF
}

NODE="${NODE_PADRAO}"
DURACAO="${DURACAO_PADRAO}"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --node)
            [[ $# -ge 2 ]] || { echo "Erro: --node exige valor" >&2; exit 2; }
            NODE="$2"; shift 2 ;;
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

if ! [[ "${DURACAO}" =~ ^[0-9]+$ ]] || [[ "${DURACAO}" -lt 1 ]]; then
    echo "Erro: --duration deve ser inteiro positivo, recebido '${DURACAO}'" >&2
    exit 2
fi

if [[ "${DURACAO}" -lt 40 ]]; then
    echo "Aviso: --duration=${DURACAO} e menor que o timeout padrao do FD_ALL3 (40s)." >&2
    echo "       O cluster pode nao detectar o crash antes do start." >&2
fi

log() {
    echo "[${TIMESTAMP}] ${NOME}: $*"
}

log "Plano: NODE=${NODE} DURACAO=${DURACAO}s DRY_RUN=${DRY_RUN}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    log "Comandos previstos:"
    log "  ${PODMAN} stop ${NODE}"
    log "  sleep ${DURACAO}"
    log "  ${PODMAN} start ${NODE}"
    log "dry-run: nada executado"
    exit 0
fi

if ! command -v ${PODMAN} >/dev/null 2>&1; then
    echo "Erro: '${PODMAN}' nao encontrado no PATH" >&2
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

log "${PODMAN} stop ${NODE}"
${PODMAN} stop "${NODE}"

log "aguardando ${DURACAO}s"
sleep "${DURACAO}"

log "${PODMAN} start ${NODE}"
${PODMAN} start "${NODE}"

log "OK: F1 (crash) injetada e reentrada de ${NODE} acionada"
