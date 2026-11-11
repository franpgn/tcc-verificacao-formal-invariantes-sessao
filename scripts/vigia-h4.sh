#!/usr/bin/env bash
#
# vigia-h4.sh
#
# Vigia do criterio h4 durante uma fase do rebaseline (DT-018; complemento 3 do pre-registro, C3 §4.3 e §5).
# Roda NA VM numa janela tmux a parte. A cada periodo, chama
#   python3 -I <repo>/analysis/h4.py fase --amostras /run/tccdiag/<id>/amostras.txt --saida <SAIDA> --fase <fase>
# (leitura em memoria, sobre o tmpfs) e:
#   - exit 30 (parada h4, regras 1 e 2): grava ~/rebaseline-infra/<id>/PARADA-H4 com a saida e mostra um aviso;
#   - exit 4 em 2 ciclos seguidos (arquivo ilegivel): grava ~/rebaseline-infra/<id>/ALERTA-H4C e avisa;
#   - unit tcc-amostrador-<id> inativa (regra 3): grava PARADA-H4 e avisa.
# Em todos os casos CONTINUA vigiando. Nao interrompe o runner e nao manda sinal a processo nenhum: a parada e
# feita pelo coordenador (C3 §4.3, Ctrl-C durante o portao K1 da execucao seguinte).
#
# Uso:
#   scripts/vigia-h4.sh --id <fase>-<UTC> --saida <SAIDA do runner> --fase piloto|noite1|noite2|reposicoes
#       [--periodo 300] [--ciclos N]      (--ciclos 0 = sem fim, o padrao)
# So nos testes: H4_MODO_TESTE=1 com H4_RUN (no lugar de /run/tccdiag, com a marca .h4-shim).
# Exit: 0 fim dos ciclos pedidos; 2 uso.
set -uo pipefail
LC_ALL=C
export LC_ALL

_src="${BASH_SOURCE[0]}"
[[ "${_src}" == /* ]] || _src="${PWD}/${_src}"
SCRIPT_DIR="$(cd "${_src%/*}" && pwd -P)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd -P)"
H4PY="${REPO_DIR}/analysis/h4.py"
readonly ID_RE='^(piloto|noite1|noite2|reposicoes|ensaio|diag)-[0-9]{8}T[0-9]{6}Z$'

erro_uso() { echo "Erro: $*" >&2; exit 2; }

RUN=/run/tccdiag
if [[ -n "${H4_RUN:-}" ]]; then
    [[ "${H4_MODO_TESTE:-}" == 1 ]] || erro_uso "H4_RUN so vale com H4_MODO_TESTE=1"
    [[ -f "${H4_RUN}/.h4-shim" ]] || erro_uso "H4_RUN sem a marca .h4-shim"
    RUN="${H4_RUN}"
fi

ID=""; SAIDA=""; FASE=""; PERIODO=300; CICLOS=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --id|--saida|--fase|--periodo|--ciclos)
            [[ $# -ge 2 ]] || erro_uso "$1 exige valor"
            case "$1" in
                --id) ID="$2" ;; --saida) SAIDA="$2" ;; --fase) FASE="$2" ;;
                --periodo) PERIODO="$2" ;; --ciclos) CICLOS="$2" ;;
            esac
            shift 2 ;;
        --help|-h) sed -n '2,21p' "${_src}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) erro_uso "argumento desconhecido '$1'" ;;
    esac
done
[[ "${ID}" =~ ${ID_RE} ]] || erro_uso "--id '${ID}' nao casa com ${ID_RE}"
case "${FASE}" in piloto|noite1|noite2|reposicoes) ;; *) erro_uso "--fase deve ser piloto, noite1, noite2 ou reposicoes" ;; esac
[[ -n "${SAIDA}" && -d "${SAIDA}" ]] || erro_uso "--saida <dir> existente e obrigatoria"
[[ "${PERIODO}" =~ ^[0-9]+$ && "${PERIODO}" -ge 1 ]] || erro_uso "--periodo deve ser inteiro >= 1"
[[ "${CICLOS}" =~ ^[0-9]+$ ]] || erro_uso "--ciclos deve ser inteiro >= 0"

ARQ="${RUN}/${ID}/amostras.txt"
UNIT="tcc-amostrador-${ID}"
DESTINO="${HOME}/rebaseline-infra/${ID}"
mkdir -p "${DESTINO}" || erro_uso "nao consegui criar ${DESTINO}"

agora() { date -u +%Y-%m-%dT%H:%M:%SZ; }

aviso() {  # aviso <titulo> <texto>
    printf '\a\n'
    printf '%s\n' "################################################################################"
    printf '#### %s\n' "$1"
    printf '#### %s\n' "$(agora)"
    printf '%s\n' "$2" | sed 's/^/#### /'
    printf '%s\n' "################################################################################"
}

gravar() {  # gravar <arquivo> <cabecalho> <texto>: acrescenta (mantem o historico)
    { printf '== %s %s\n' "$(agora)" "$2"; printf '%s\n' "$3"; } >> "${DESTINO}/$1"
}

echo "vigia-h4: id=${ID} fase=${FASE} saida=${SAIDA} arquivo=${ARQ} periodo=${PERIODO}s unit=${UNIT}"
seguidos4=0
ciclo=0
while :; do
    ciclo=$((ciclo + 1))
    rc=0
    out="$(python3 -I "${H4PY}" fase --amostras "${ARQ}" --saida "${SAIDA}" --fase "${FASE}" 2>&1)" || rc=$?
    echo "[$(agora)] ciclo ${ciclo}: h4.py exit ${rc}: $(printf '%s\n' "${out}" | tail -n 1)"
    case "${rc}" in
        0) seguidos4=0 ;;
        30)
            seguidos4=0
            gravar PARADA-H4 "h4.py exit 30 (parada h4, C3 §4.3 regras 1/2)" "${out}"
            aviso "PARADA H4 da fase ${FASE}: interromper o runner no portao K1 da proxima execucao (C3 §4.3)" "${out}" ;;
        4)
            seguidos4=$((seguidos4 + 1))
            if [[ "${seguidos4}" -ge 2 ]]; then
                gravar ALERTA-H4C "h4.py exit 4 em ${seguidos4} ciclos seguidos (arquivo de amostras ilegivel)" "${out}"
                aviso "ALERTA h4c: ${ARQ} ilegivel em ${seguidos4} ciclos seguidos" "${out}"
            fi ;;
        *)
            seguidos4=0
            aviso "h4.py saiu com ${rc} (inesperado)" "${out}" ;;
    esac
    if ! systemctl is-active --quiet "${UNIT}" > /dev/null 2>&1; then
        gravar PARADA-H4 "unit ${UNIT} inativa (parada h4, C3 §4.3 regra 3)" "systemctl is-active ${UNIT}: inativa"
        aviso "PARADA H4 da fase ${FASE}: unit ${UNIT} INATIVA (regra 3)" "journalctl -u ${UNIT}"
    fi
    if [[ "${CICLOS}" -gt 0 && "${ciclo}" -ge "${CICLOS}" ]]; then break; fi
    sleep "${PERIODO}"
done
exit 0
