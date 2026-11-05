#!/usr/bin/env bash
#
# cria-caches.test.sh
#
# Suite sem cluster do cria-caches.sh (W-4, B-26.3). Um shim de curl no inicio do PATH registra
# cada chamada (argumentos e a entrada padrao, onde vai a credencial) em $SHIM_LOG e responde:
#   POST .../rest/v2/caches/<nome>             -> codigo $SHIM_POST (padrao 200)
#   GET  .../rest/v2/caches/<nome>?action=config -> {"<nome>":<arquivo>} com codigo 200, ou, com
#        SHIM_DIVERGE=<porta>, um corpo alterado no no dessa porta
# O shim NAO prova que o Infinispan aceita o corpo nem o formato real da leitura de volta: isso
# ficou medido no B-44.1b (runs/b44-1b/corpos/B0-cfgf-*.txt) e e conferido no smoke local do W-4.
#
# Rodar:  bash scripts/cria-caches.test.sh     (exit 0 se todos passarem)
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALVO="${CRIA_SCRIPT:-${DIR}/cria-caches.sh}"
CACHES="${DIR}/../cluster/caches"

PASS=0
FAIL=0
ok() { if [[ "$2" == "$3" ]]; then echo "PASS: $1"; PASS=$((PASS + 1)); else echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1)); fi; }
contem() { if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "PASS: $1"; PASS=$((PASS + 1)); else echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1)); fi; }
nao_contem() { if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "FAIL: $1 (encontrou '$2')"; FAIL=$((FAIL + 1)); else echo "PASS: $1"; PASS=$((PASS + 1)); fi; }

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
mkdir -p "${TMP}/shims"
export SHIM_LOG="${TMP}/shim.log"
export SHIM_CACHES="${CACHES}"

cat > "${TMP}/shims/curl" <<'EOF'
#!/usr/bin/env bash
saida=""; formato=""; metodo="GET"; corpo=""; url=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
    case "${args[$i]}" in
        -o) saida="${args[$((i + 1))]}"; i=$((i + 1)) ;;
        -w) formato="${args[$((i + 1))]}"; i=$((i + 1)) ;;
        -X) metodo="${args[$((i + 1))]}"; i=$((i + 1)) ;;
        --data-binary) corpo="${args[$((i + 1))]#@}"; i=$((i + 1)) ;;
        -K|-H|--max-time) i=$((i + 1)) ;;
        -*) ;;
        *) url="${args[$i]}" ;;
    esac
done
entrada="$(cat)"
echo "curl ${metodo} ${url} | argv: $* | stdin: ${entrada}" >> "${SHIM_LOG}"
[[ -n "${corpo}" ]] && echo "corpo: $(cat "${corpo}")" >> "${SHIM_LOG}"
nome="${url##*/rest/v2/caches/}"; nome="${nome%%\?*}"
codigo=200
if [[ "${metodo}" == POST ]]; then
    codigo="${SHIM_POST:-200}"
    resposta=""
else
    resposta="{\"${nome}\":$(tr -d '\r\n' < "${SHIM_CACHES}/${nome}.json")}"
    porta="${url#*://*:}"; porta="${porta%%/*}"
    if [[ -n "${SHIM_DIVERGE:-}" && "${porta}" == "${SHIM_DIVERGE}" ]]; then
        resposta="${resposta/\"remote-timeout\":\"17500\",/}"
        resposta="${resposta/\"owners\":\"2\"/\"owners\":\"1\"}"
    fi
fi
[[ -n "${saida}" ]] && printf '%s' "${resposta}" > "${saida}"
[[ "${formato}" == '%{http_code}' ]] && printf '%s' "${codigo}"
exit 0
EOF
chmod +x "${TMP}/shims/curl"
PATH="${TMP}/shims:${PATH}"

rodar() { # rodar <args...>: exit code em RC, stdout em OUT, stderr em ERR
    : > "${SHIM_LOG}"
    OUT="$(bash "${ALVO}" "$@" 2> "${TMP}/err")"; RC=$?
    ERR="$(cat "${TMP}/err")"
}

U1=http://127.0.0.1:11222
CONF="${U1},http://127.0.0.1:11223,http://127.0.0.1:11224"

# C1: os dois canario*, criados e conferidos nos tres nos
export CRIA_CACHES_SENHA='s3gr"e\do'
rodar --url "${U1}/" --conferir "${CONF}" canario canario-async
ok "C1 exit 0" 0 "${RC}"
ok "C1 2 POST" 2 "$(grep -c '^curl POST' "${SHIM_LOG}")"
ok "C1 6 GET de leitura de volta" 6 "$(grep -c '^curl GET .*?action=config' "${SHIM_LOG}")"
contem "C1 POST no endpoint do cache" "curl POST ${U1}/rest/v2/caches/canario |" "$(cat "${SHIM_LOG}")"
contem "C1 corpo = arquivo, sem o envoltorio do nome" "corpo: $(tr -d '\r\n' < "${CACHES}/canario.json")" "$(cat "${SHIM_LOG}")"
contem "C1 SYNC com remote-timeout 17500" '"remote-timeout":"17500"' "$(grep 'corpo:.*"mode":"SYNC"' "${SHIM_LOG}")"
nao_contem "C1 ASYNC sem remote-timeout" '"remote-timeout"' "$(grep 'corpo:.*"mode":"ASYNC"' "${SHIM_LOG}")"
nao_contem "C1 senha fora da linha de comando" 's3gr' "$(grep -o 'argv: [^|]*' "${SHIM_LOG}")"
contem "C1 credencial pela entrada padrao, com escape" 'stdin: user = "admin:s3gr\"e\\do"' "$(cat "${SHIM_LOG}")"
contem "C1 digest" "--digest" "$(cat "${SHIM_LOG}")"
ok "C1 8 linhas TSV" 8 "$(printf '%s\n' "${OUT}" | grep -c $'\t')"

# C2: leitura de volta divergente num no -> exit 4
SHIM_DIVERGE=11223 rodar --url "${U1}" --conferir "${CONF}" canario
ok "C2 divergencia exit 4" 4 "${RC}"
contem "C2 aponta o no" "11223" "${ERR}"

# C3: criacao recusada -> exit 3, sem leitura de volta
SHIM_POST=409 rodar --url "${U1}" --conferir "${CONF}" canario
ok "C3 recusa exit 3" 3 "${RC}"
ok "C3 sem GET" 0 "$(grep -c '^curl GET' "${SHIM_LOG}")"

# C4: arquivo ausente -> exit 2, sem chamada
rodar --url "${U1}" naoexiste
ok "C4 arquivo ausente exit 2" 2 "${RC}"
ok "C4 nenhuma chamada" 0 "$(grep -c '^curl' "${SHIM_LOG}")"

# C5: argumentos
rodar canario
ok "C5 sem --url exit 1" 1 "${RC}"
rodar --url "${U1}"
ok "C5 sem nome exit 1" 1 "${RC}"
rodar --url "${U1}" 'Canario;rm'
ok "C5 nome invalido exit 1" 1 "${RC}"
rodar --url "${U1}" --prazo-s 0 canario
ok "C5 prazo invalido exit 1" 1 "${RC}"
unset CRIA_CACHES_SENHA
rodar --url "${U1}" canario
ok "C5 sem senha exit 1" 1 "${RC}"
export CRIA_CACHES_SENHA=x

# C6: sem --conferir so cria; --prazo-s vira --max-time
rodar --url "${U1}" --prazo-s 30 canario-async
ok "C6 exit 0" 0 "${RC}"
ok "C6 so o POST" 1 "$(grep -c '^curl' "${SHIM_LOG}")"
contem "C6 --max-time" "--max-time 30" "$(cat "${SHIM_LOG}")"

# C7: arquivo com mais de uma linha -> exit 2
mkdir -p "${TMP}/caches"
printf '{\n"distributed-cache":{}}\n' > "${TMP}/caches/dois.json"
rodar --url "${U1}" --dir "${TMP}/caches" dois
ok "C7 JSON nao compacto exit 2" 2 "${RC}"

echo "----"
echo "PASS=${PASS} FAIL=${FAIL}"
[[ "${FAIL}" -eq 0 ]]
