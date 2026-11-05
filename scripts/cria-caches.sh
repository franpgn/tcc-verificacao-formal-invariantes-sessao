#!/usr/bin/env bash
#
# cria-caches.sh
#
# W-4 (B-26.3): cria pela REST de administracao do Infinispan os caches cujos JSON estao em
# cluster/caches/<nome>.json e confere a leitura de volta em cada no. E o contorno do P-30 do
# B-44.1b (cache criado pela REST nao causa ISPN000438 no reinicio do no) e a forma decidida para
# os caches canario e canario-async da sonda de defasagem (S_lag), que NAO estao no XML
# (docs/acoes-controlador-cluster-2026-11-05.md, secao 6, itens 2 e 4). Promovido de
# runs/b44-1b/scripts/cria-caches.sh (B-44.1b), com o corpo lido do arquivo versionado em vez de
# montado no script, e com a leitura de volta. O W-6 (orquestrador) usa este mesmo script; ele
# nao cria cache nenhum por conta propria.
#
# Corpo: o arquivo inteiro, SEM o envoltorio do nome ({"distributed-cache":{...}}): com o nome,
# "counters" colide com o elemento <counters> do parser (ISPN029520). Os JSON SYNC trazem
# remote-timeout 17500 explicito (o valor efetivo do XML; criado sem ele, seria 15000).
#
# Leitura de volta: GET <url>/rest/v2/caches/<nome>?action=config em cada URL de --conferir,
# comparada byte a byte com {"<nome>":<arquivo>} (o servidor devolve o JSON compacto, na mesma
# ordem de campos; o arquivo e mantido assim, CachesCanarioJsonTest).
#
# Credencial: usuario em --usuario (padrao admin); senha SO pela variavel de ambiente
# CRIA_CACHES_SENHA, passada ao curl pela entrada padrao (-K -), nunca na linha de comando.
#
# Uso:
#   CRIA_CACHES_SENHA=... scripts/cria-caches.sh --url http://127.0.0.1:11222 \
#       [--conferir http://127.0.0.1:11222,http://127.0.0.1:11223,http://127.0.0.1:11224] \
#       [--usuario admin] [--dir cluster/caches] [--prazo-s N] canario canario-async
#
# Saida: uma linha TSV por chamada: instante UTC, nome, metodo, url, codigo HTTP, duracao (s).
#
# Exit codes:
#   0  todos criados e conferidos
#   1  argumento invalido ou senha ausente
#   2  arquivo de configuracao ausente ou com mais de uma linha
#   3  criacao recusada (codigo HTTP diferente de 200) ou curl falhou
#   4  leitura de volta divergente ou falhou em algum no
set -uo pipefail

DIR_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
URL=""
CONFERIR=""
USUARIO="admin"
DIR_CACHES="${DIR_SCRIPT}/../cluster/caches"
PRAZO_S=""
NOMES=()

uso() {
    cat >&2 <<'USO'
Uso: CRIA_CACHES_SENHA=... cria-caches.sh --url http://h:p [--conferir http://h1:p1,http://h2:p2,...]
         [--usuario admin] [--dir cluster/caches] [--prazo-s N] nome [nome...]
Cria cada cache pela REST a partir de <dir>/<nome>.json e confere ?action=config em cada URL de --conferir.
USO
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --url) URL="${2:-}"; shift 2 ;;
        --conferir) CONFERIR="${2:-}"; shift 2 ;;
        --usuario) USUARIO="${2:-}"; shift 2 ;;
        --dir) DIR_CACHES="${2:-}"; shift 2 ;;
        --prazo-s) PRAZO_S="${2:-}"; shift 2 ;;
        -h|--help) uso; exit 0 ;;
        -*) echo "opcao desconhecida: $1" >&2; uso; exit 1 ;;
        *) NOMES+=("$1"); shift ;;
    esac
done

[[ -n "${URL}" ]] || { echo "falta --url" >&2; exit 1; }
[[ ${#NOMES[@]} -gt 0 ]] || { echo "falta o nome de ao menos um cache" >&2; exit 1; }
[[ -n "${CRIA_CACHES_SENHA:-}" ]] || { echo "defina CRIA_CACHES_SENHA (a senha nunca vai na linha de comando)" >&2; exit 1; }
if [[ -n "${PRAZO_S}" && ! "${PRAZO_S}" =~ ^[1-9][0-9]*$ ]]; then
    echo "--prazo-s deve ser inteiro > 0: ${PRAZO_S}" >&2; exit 1
fi
for n in "${NOMES[@]}"; do
    [[ "${n}" =~ ^[a-z][a-z0-9-]*$ ]] || { echo "nome de cache invalido: ${n}" >&2; exit 1; }
done
URL="${URL%/}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

# curl com a credencial pela entrada padrao; o resto dos argumentos vem do chamador.
# chamar <metodo> <url> <saida> [corpo]: imprime o codigo HTTP (000 se o curl falhou).
chamar() {
    local metodo="$1" url="$2" saida="$3" corpo="${4:-}"
    local args=(-s -o "${saida}" -w '%{http_code}' --digest -K - -X "${metodo}")
    [[ -n "${PRAZO_S}" ]] && args+=(--max-time "${PRAZO_S}")
    [[ -n "${corpo}" ]] && args+=(-H 'Content-Type: application/json' --data-binary "@${corpo}")
    local codigo cred
    cred="${USUARIO}:${CRIA_CACHES_SENHA}"
    cred="${cred//\\/\\\\}"; cred="${cred//\"/\\\"}" # mesmo escape de HealthRest.configCurl
    codigo="$(printf 'user = "%s"\n' "${cred}" | curl "${args[@]}" "${url}")" || codigo="000"
    printf '%s' "${codigo:-000}"
}

registrar() { # registrar <nome> <metodo> <url> <codigo> <inicio_ns>
    local fim; fim="$(date +%s%N)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)" "$1" "$2" "$3" "$4" \
        "$(awk -v a="$5" -v b="${fim}" 'BEGIN { printf "%.3f", (b - a) / 1e9 }')"
}

IFS=',' read -r -a NOS_CONFERIR <<< "${CONFERIR}"

for nome in "${NOMES[@]}"; do
    arq="${DIR_CACHES}/${nome}.json"
    [[ -f "${arq}" ]] || { echo "arquivo ausente: ${arq}" >&2; exit 2; }
    corpo="${TMP}/${nome}.json"
    tr -d '\r' < "${arq}" | sed '/^[[:space:]]*$/d' > "${corpo}"
    if [[ "$(grep -c '' "${corpo}")" -ne 1 ]]; then
        echo "o arquivo deve ter uma linha so (JSON compacto): ${arq}" >&2; exit 2
    fi
    conteudo="$(cat "${corpo}")"

    ini="$(date +%s%N)"
    codigo="$(chamar POST "${URL}/rest/v2/caches/${nome}" "${TMP}/resp-${nome}.txt" "${corpo}")"
    registrar "${nome}" POST "${URL}/rest/v2/caches/${nome}" "${codigo}" "${ini}"
    if [[ "${codigo}" != "200" ]]; then
        echo "criacao de ${nome} recusada (HTTP ${codigo}): $(head -c 300 "${TMP}/resp-${nome}.txt" 2>/dev/null)" >&2
        exit 3
    fi

    esperado="{\"${nome}\":${conteudo}}"
    for u in "${NOS_CONFERIR[@]}"; do
        [[ -n "${u// /}" ]] || continue
        u="${u// /}"; u="${u%/}"
        ini="$(date +%s%N)"
        lido="${TMP}/cfg-${nome}.txt"
        codigo="$(chamar GET "${u}/rest/v2/caches/${nome}?action=config" "${lido}")"
        registrar "${nome}" GET "${u}/rest/v2/caches/${nome}?action=config" "${codigo}" "${ini}"
        obtido="$(tr -d '\r\n' < "${lido}" 2>/dev/null)"
        if [[ "${codigo}" != "200" || "${obtido}" != "${esperado}" ]]; then
            echo "leitura de volta de ${nome} em ${u} diverge (HTTP ${codigo})" >&2
            echo "  esperado: ${esperado}" >&2
            echo "  obtido:   ${obtido}" >&2
            exit 4
        fi
    done
done
exit 0
