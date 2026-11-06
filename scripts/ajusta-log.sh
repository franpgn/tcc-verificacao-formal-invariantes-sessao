#!/usr/bin/env bash
#
# ajusta-log.sh
#
# P-64 (decisao do coordenador, 06/11): reduz na origem o log das pilhas do servidor, pela REST de logging do
# Infinispan 15.0.21, igual nos tres nos e nos quatro bracos. Para cada categoria do arquivo (padrao
# cluster/log/loggers-avaliacao.tsv), em cada no de --nos:
#   PUT <no>/rest/v2/logging/loggers/<categoria>?level=<nivel>    (204; nivel invalido -> 400 ISPN080036)
# e depois le de volta GET <no>/rest/v2/logging/loggers e confere {"name":"<categoria>","level":"<nivel>"} em cada no.
# Medido no cluster local p1ic (06/11, runs/p64-log/): o nivel vale so no no que recebe o pedido (cada no e um
# pedido) e nao sobrevive ao reinicio do no (o orquestrador repoe no no que volta); a lista vem compacta, com os
# campos na ordem name, level, appenders.
#
# Arquivo: uma categoria por linha, categoria<TAB>nivel<TAB>origem; linhas vazias e comecadas por # sao ignoradas.
# Categoria: nome de logger Java (letras, digitos, _, $ e pontos); nivel: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE
# ou ALL; origem: texto nao vazio (por que a categoria esta aqui e de onde vem o numero).
#
# Credencial: usuario em --usuario (padrao admin); senha SO pela variavel AJUSTA_LOG_SENHA, passada ao curl pela
# entrada padrao (-K -), nunca na linha de comando (mesmo padrao do cria-caches.sh).
#
# Uso:
#   AJUSTA_LOG_SENHA=... scripts/ajusta-log.sh --nos http://h1:p1,http://h2:p2,http://h3:p3 \
#       [--arquivo cluster/log/loggers-avaliacao.tsv] [--usuario admin] [--prazo-s N] [--so-conferir]
#   scripts/ajusta-log.sh --validar [--arquivo ...]     (so confere o arquivo; sem rede e sem senha)
#
# Saida: uma linha TSV por chamada: instante UTC, categoria ("-" na leitura), metodo, url, codigo HTTP, duracao (s).
# Com --validar: categoria<TAB>nivel de cada linha, na ordem do arquivo.
#
# Exit codes:
#   0  niveis postos e conferidos (ou arquivo valido, com --validar)
#   1  argumento invalido ou senha ausente
#   2  arquivo ausente ou invalido
#   3  PUT recusado (codigo HTTP diferente de 204/200) ou curl falhou
#   4  leitura de volta falhou ou divergente em algum no
set -uo pipefail

DIR_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARQ="${DIR_SCRIPT}/../cluster/log/loggers-avaliacao.tsv"
NOS=""
USUARIO="admin"
PRAZO_S=""
SO_CONFERIR=0
VALIDAR=0
readonly NIVEIS=" OFF FATAL ERROR WARN INFO DEBUG TRACE ALL "

uso() {
    cat >&2 <<'USO'
Uso: AJUSTA_LOG_SENHA=... ajusta-log.sh --nos http://h1:p1,... [--arquivo <tsv>] [--usuario admin] [--prazo-s N]
         [--so-conferir]
     ajusta-log.sh --validar [--arquivo <tsv>]
Poe o nivel de cada categoria do arquivo em cada no pela REST e le de volta.
USO
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --nos) NOS="${2:-}"; shift 2 ;;
        --arquivo) ARQ="${2:-}"; shift 2 ;;
        --usuario) USUARIO="${2:-}"; shift 2 ;;
        --prazo-s) PRAZO_S="${2:-}"; shift 2 ;;
        --so-conferir) SO_CONFERIR=1; shift ;;
        --validar) VALIDAR=1; shift ;;
        -h|--help) uso; exit 0 ;;
        *) echo "opcao desconhecida: $1" >&2; uso; exit 1 ;;
    esac
done
if [[ -n "${PRAZO_S}" && ! "${PRAZO_S}" =~ ^[1-9][0-9]*$ ]]; then
    echo "--prazo-s deve ser inteiro > 0: ${PRAZO_S}" >&2; exit 1
fi

# ------------------------------------------------------------------ arquivo
CATS=(); NIVS=()
[[ -f "${ARQ}" ]] || { echo "arquivo de loggers ausente: ${ARQ}" >&2; exit 2; }
n=0
while IFS= read -r linha || [[ -n "${linha}" ]]; do
    n=$((n + 1))
    linha="${linha%$'\r'}"
    [[ -z "${linha//[[:space:]]/}" || "${linha}" == \#* ]] && continue
    IFS=$'\t' read -r cat niv origem extra <<< "${linha}"
    if [[ ! "${cat:-}" =~ ^[A-Za-z_][A-Za-z0-9_\$]*(\.[A-Za-z_][A-Za-z0-9_\$]*)*$ ]]; then
        echo "${ARQ}:${n}: categoria invalida: '${cat:-}'" >&2; exit 2
    fi
    if [[ -z "${niv:-}" || "${NIVEIS}" != *" ${niv} "* ]]; then
        echo "${ARQ}:${n}: nivel invalido para ${cat}: '${niv:-}' (um de${NIVEIS% })" >&2; exit 2
    fi
    if [[ -z "${origem//[[:space:]]/}" || -n "${extra:-}" ]]; then
        echo "${ARQ}:${n}: ${cat}: origem ausente ou coluna a mais (categoria<TAB>nivel<TAB>origem)" >&2; exit 2
    fi
    CATS+=("${cat}"); NIVS+=("${niv}")
done < "${ARQ}"
[[ ${#CATS[@]} -gt 0 ]] || { echo "nenhuma categoria em ${ARQ}" >&2; exit 2; }

if [[ "${VALIDAR}" -eq 1 ]]; then
    for i in "${!CATS[@]}"; do printf '%s\t%s\n' "${CATS[$i]}" "${NIVS[$i]}"; done
    exit 0
fi

[[ -n "${NOS}" ]] || { echo "falta --nos" >&2; uso; exit 1; }
[[ -n "${AJUSTA_LOG_SENHA:-}" ]] || { echo "defina AJUSTA_LOG_SENHA (a senha nunca vai na linha de comando)" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

# chamar <metodo> <url> <saida>: imprime o codigo HTTP (000 se o curl falhou)
chamar() {
    local args=(-s -o "$3" -w '%{http_code}' --digest -K - -X "$1")
    [[ -n "${PRAZO_S}" ]] && args+=(--max-time "${PRAZO_S}")
    local codigo cred
    cred="${USUARIO}:${AJUSTA_LOG_SENHA}"
    cred="${cred//\\/\\\\}"; cred="${cred//\"/\\\"}" # mesmo escape do cria-caches.sh
    codigo="$(printf 'user = "%s"\n' "${cred}" | curl "${args[@]}" "$2")" || codigo="000"
    printf '%s' "${codigo:-000}"
}

registrar() { # registrar <categoria> <metodo> <url> <codigo> <inicio_ns>
    local fim; fim="$(date +%s%N)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)" "$1" "$2" "$3" "$4" \
        "$(awk -v a="$5" -v b="${fim}" 'BEGIN { printf "%.3f", (b - a) / 1e9 }')"
}

IFS=',' read -r -a LISTA <<< "${NOS}"
URLS=()
for u in "${LISTA[@]}"; do
    u="${u// /}"; [[ -n "${u}" ]] || continue
    URLS+=("${u%/}")
done
[[ ${#URLS[@]} -gt 0 ]] || { echo "--nos sem URL" >&2; exit 1; }

if [[ "${SO_CONFERIR}" -eq 0 ]]; then
    for u in "${URLS[@]}"; do
        for i in "${!CATS[@]}"; do
            url="${u}/rest/v2/logging/loggers/${CATS[$i]}?level=${NIVS[$i]}"
            ini="$(date +%s%N)"
            codigo="$(chamar PUT "${url}" "${TMP}/put.txt")"
            registrar "${CATS[$i]}" PUT "${url}" "${codigo}" "${ini}"
            if [[ "${codigo}" != 204 && "${codigo}" != 200 ]]; then
                echo "${u}: nivel de ${CATS[$i]} recusado (HTTP ${codigo}): $(head -c 300 "${TMP}/put.txt" 2>/dev/null)" >&2
                exit 3
            fi
        done
    done
fi

for u in "${URLS[@]}"; do
    url="${u}/rest/v2/logging/loggers"
    ini="$(date +%s%N)"
    codigo="$(chamar GET "${url}" "${TMP}/lido.txt")"
    registrar - GET "${url}" "${codigo}" "${ini}"
    [[ "${codigo}" == 200 ]] || { echo "${u}: leitura dos loggers falhou (HTTP ${codigo})" >&2; exit 4; }
    lido="$(tr -d '\r\n' < "${TMP}/lido.txt")"
    for i in "${!CATS[@]}"; do
        if [[ "${lido}" != *"{\"name\":\"${CATS[$i]}\",\"level\":\"${NIVS[$i]}\""* ]]; then
            obtido="$(grep -o "{\"name\":\"${CATS[$i]//\$/\\\$}\",\"level\":\"[A-Z]*\"" <<< "${lido}" | head -1 | sed 's/.*"level":"\([A-Z]*\)"/\1/')"
            echo "${u}: ${CATS[$i]} esperado ${NIVS[$i]}, lido ${obtido:-ausente}" >&2
            exit 4
        fi
    done
done
exit 0
