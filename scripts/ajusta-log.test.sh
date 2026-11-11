#!/usr/bin/env bash
#
# ajusta-log.test.sh
#
# Suite sem cluster do ajusta-log.sh (P-64, 06/11). Um shim de curl no inicio do PATH registra cada chamada
# (argumentos e a entrada padrao, onde vai a credencial) em $SHIM_LOG e guarda o nivel de cada categoria por porta,
# como o servidor (a mudanca pela REST vale so no no que a recebe; medido no cluster local p1ic em 06/11):
#   PUT .../rest/v2/logging/loggers/<categoria>?level=<nivel> -> 204 ($SHIM_PUT muda o codigo)
#   GET .../rest/v2/logging/loggers -> a lista do no, no formato do 15.0.21 ({"name":..,"level":..,"appenders":[..]});
#       SHIM_DIVERGE=<porta> devolve ERROR no lugar do nivel guardado nesse no
# O shim NAO prova o formato real: ele foi lido no cluster local (runs/p64-log/) e o smoke mede o efeito.
#
# Rodar:  bash scripts/ajusta-log.test.sh     (exit 0 se todos passarem)
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALVO="${AJUSTA_SCRIPT:-${DIR}/ajusta-log.sh}"
ARQ_REPO="${DIR}/../cluster/log/loggers-avaliacao.tsv"

PASS=0
FAIL=0
ok() { if [[ "$2" == "$3" ]]; then echo "PASS: $1"; PASS=$((PASS + 1)); else echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1)); fi; }
contem() { if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "PASS: $1"; PASS=$((PASS + 1)); else echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1)); fi; }
nao_contem() { if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "FAIL: $1 (encontrou '$2')"; FAIL=$((FAIL + 1)); else echo "PASS: $1"; PASS=$((PASS + 1)); fi; }

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
mkdir -p "${TMP}/shims" "${TMP}/estado"
export SHIM_LOG="${TMP}/shim.log" SHIM_ESTADO="${TMP}/estado"

cat > "${TMP}/shims/curl" <<'EOF'
#!/usr/bin/env bash
saida=""; formato=""; metodo="GET"; url=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
    case "${args[$i]}" in
        -o) saida="${args[$((i + 1))]}"; i=$((i + 1)) ;;
        -w) formato="${args[$((i + 1))]}"; i=$((i + 1)) ;;
        -X) metodo="${args[$((i + 1))]}"; i=$((i + 1)) ;;
        -K|-H|--max-time) i=$((i + 1)) ;;
        -*) ;;
        *) url="${args[$i]}" ;;
    esac
done
entrada="$(cat)"
echo "curl ${metodo} ${url} | argv: $* | stdin: ${entrada}" >> "${SHIM_LOG}"
porta="${url#*://*:}"; porta="${porta%%/*}"
f="${SHIM_ESTADO}/loggers-${porta}"
codigo=200; resposta=""
if [[ "${metodo}" == PUT ]]; then
    codigo="${SHIM_PUT:-204}"
    cat="${url##*/rest/v2/logging/loggers/}"; nivel="${cat#*\?level=}"; cat="${cat%%\?*}"
    if [[ "${codigo}" == 204 ]]; then
        touch "${f}"; grep -v "^${cat} " "${f}" > "${f}.n"; echo "${cat} ${nivel}" >> "${f}.n"; mv "${f}.n" "${f}"
    else
        resposta="ISPN080036: Invalid level"
    fi
else
    resposta='[{"name":"","level":"INFO","appenders":["STDOUT","FILE"]}'
    if [[ -f "${f}" ]]; then
        while read -r c n; do
            [[ -n "${SHIM_DIVERGE:-}" && "${porta}" == "${SHIM_DIVERGE}" ]] && n=ERROR
            resposta+=",{\"name\":\"${c}\",\"level\":\"${n}\",\"appenders\":[]}"
        done < "${f}"
    fi
    resposta+=',{"name":"org.infinispan.HOTROD_ACCESS_LOG","level":"INFO","appenders":["HR-ACCESS-FILE"]}]'
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
limpar() { rm -f "${SHIM_ESTADO}"/loggers-*; unset SHIM_PUT SHIM_DIVERGE; }

NOS=http://127.0.0.1:11222,http://127.0.0.1:11223,http://127.0.0.1:11224
ARQ="${TMP}/loggers.tsv"
printf '# comentario\n\norg.exemplo.Um\tFATAL\torigem do um\norg.exemplo.Dois$Tres\tWARN\torigem do dois\n' > "${ARQ}"

# L1: aplica as duas categorias nos tres nos e le de volta em cada um
export AJUSTA_LOG_SENHA='s3gr"e\do'
limpar
rodar --nos "${NOS}" --arquivo "${ARQ}"
ok "L1 exit 0" "0" "${RC}"
ok "L1 dois PUT por no" "6" "$(grep -c '^curl PUT ' "${SHIM_LOG}")"
ok "L1 uma leitura por no" "3" "$(grep -c '^curl GET .*/rest/v2/logging/loggers |' "${SHIM_LOG}")"
contem "L1 PUT com o nivel na consulta" "curl PUT http://127.0.0.1:11224/rest/v2/logging/loggers/org.exemplo.Um?level=FATAL" "$(cat "${SHIM_LOG}")"
contem "L1 PUT com \$ na categoria" "loggers/org.exemplo.Dois\$Tres?level=WARN" "$(cat "${SHIM_LOG}")"
nao_contem "L1 senha fora da linha de comando" "s3gr" "$(sed 's/| stdin:.*//' "${SHIM_LOG}")"
contem "L1 senha pela entrada padrao, com escape" 'user = "admin:s3gr\"e\\do"' "$(cat "${SHIM_LOG}")"
ok "L1 TSV: uma linha por chamada" "9" "$(grep -c . <<< "${OUT}")"
contem "L1 TSV com o codigo do PUT" $'org.exemplo.Um\tPUT\thttp://127.0.0.1:11222/rest/v2/logging/loggers/org.exemplo.Um?level=FATAL\t204' "${OUT}"
contem "L1 TSV com a leitura conferida" $'-\tGET\thttp://127.0.0.1:11223/rest/v2/logging/loggers\t200' "${OUT}"

# L2: --so-conferir le sem mudar nada; com os niveis ja postos passa, sem eles falha (4) com o no e a categoria
rodar --nos "${NOS}" --arquivo "${ARQ}" --so-conferir
ok "L2 so-conferir com os niveis postos: exit 0" "0" "${RC}"
ok "L2 nenhum PUT" "0" "$(grep -c '^curl PUT ' "${SHIM_LOG}")"
limpar
rodar --nos "${NOS}" --arquivo "${ARQ}" --so-conferir
ok "L2 so-conferir sem os niveis: exit 4" "4" "${RC}"
contem "L2 motivo com o no e a categoria" "http://127.0.0.1:11222: org.exemplo.Um" "${ERR}"

# L3: PUT recusado -> 3; leitura divergente num no -> 4 com esse no
limpar
SHIM_PUT=400 rodar --nos "${NOS}" --arquivo "${ARQ}"
ok "L3 PUT recusado: exit 3" "3" "${RC}"
contem "L3 motivo com o HTTP" "HTTP 400" "${ERR}"
limpar
SHIM_DIVERGE=11223 rodar --nos "${NOS}" --arquivo "${ARQ}"
ok "L3 leitura divergente: exit 4" "4" "${RC}"
contem "L3 motivo com o no divergente" "http://127.0.0.1:11223: org.exemplo.Um esperado FATAL, lido ERROR" "${ERR}"

# L4: --validar so confere o arquivo (sem rede, sem senha)
limpar
( unset AJUSTA_LOG_SENHA; : > "${SHIM_LOG}"; bash "${ALVO}" --validar --arquivo "${ARQ}" > "${TMP}/v.out" 2> "${TMP}/v.err"; echo $? > "${TMP}/v.rc" )
ok "L4 --validar: exit 0 sem senha" "0" "$(cat "${TMP}/v.rc")"
ok "L4 --validar: nenhuma chamada" "0" "$(grep -c . "${SHIM_LOG}")"
contem "L4 --validar imprime as categorias" $'org.exemplo.Um\tFATAL' "$(cat "${TMP}/v.out")"

# L5: arquivo invalido -> 2 (nivel fora da lista, sem origem, categoria invalida, vazio, ausente)
for caso in nivel origem categoria vazio ausente; do
    case "${caso}" in
        nivel) printf 'org.x.Y\tSILENT\to\n' > "${TMP}/ruim.tsv" ;;
        origem) printf 'org.x.Y\tFATAL\t\n' > "${TMP}/ruim.tsv" ;;
        categoria) printf 'org.x.Y?level=INFO\tFATAL\to\n' > "${TMP}/ruim.tsv" ;;
        vazio) printf '# so comentario\n' > "${TMP}/ruim.tsv" ;;
        ausente) rm -f "${TMP}/ruim.tsv" ;;
    esac
    rodar --nos "${NOS}" --arquivo "${TMP}/ruim.tsv"
    ok "L5 arquivo ${caso}: exit 2" "2" "${RC}"
    ok "L5 arquivo ${caso}: nenhuma chamada" "0" "$(grep -c . "${SHIM_LOG}")"
done

# L6: uso: sem --nos, sem senha, opcao desconhecida -> 1
rodar --arquivo "${ARQ}"
ok "L6 sem --nos: exit 1" "1" "${RC}"
( unset AJUSTA_LOG_SENHA; bash "${ALVO}" --nos "${NOS}" --arquivo "${ARQ}" > /dev/null 2>&1; echo $? > "${TMP}/s.rc" )
ok "L6 sem senha: exit 1" "1" "$(cat "${TMP}/s.rc")"
rodar --nos "${NOS}" --arquivo "${ARQ}" --xyz
ok "L6 opcao desconhecida: exit 1" "1" "${RC}"

# L7: o arquivo versionado da avaliacao: as duas categorias que gravam as pilhas, em FATAL, valido
rodar --validar --arquivo "${ARQ_REPO}"
ok "L7 arquivo da avaliacao valido" "0" "${RC}"
ok "L7 categorias e niveis" $'org.infinispan.server.hotrod.BaseRequestProcessor\tFATAL\norg.infinispan.interceptors.impl.InvocationContextInterceptor\tFATAL' "${OUT}"

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
