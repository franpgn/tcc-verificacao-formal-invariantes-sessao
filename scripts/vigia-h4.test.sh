#!/usr/bin/env bash
#
# vigia-h4.test.sh
#
# Testes V1..V4 do scripts/vigia-h4.sh (prompt W-h4 §4.3). O python3 de shim faz o papel do analysis/h4.py e
# devolve, a cada chamada, o proximo exit de uma lista; o systemctl de shim responde is-active pelo arquivo
# unit-ativa e falha o teste em qualquer outro subcomando; kill, pkill e killall de shim falham o teste se forem
# chamados. Sem VM e sem amostrador.
#
# Uso: bash scripts/vigia-h4.test.sh   Exit 0 se todos passarem.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VIGIA="${DIR}/vigia-h4.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
ID="noite1-20261105T000000Z"

PASS=0
FAIL=0
ok() {
    if [[ "$2" == "$3" ]]; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1)); fi
}
contem() {
    if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1)); fi
}
verdade() {
    local d="$1"; shift
    if "$@"; then echo "PASS: ${d}"; PASS=$((PASS + 1)); else echo "FAIL: ${d}"; FAIL=$((FAIL + 1)); fi
}

novo_caso() {  # novo_caso <nome> <exits separados por espaco>
    CASO="${TMP}/$1"; SH="${CASO}/shims"; ST="${CASO}/estado"; HOMEC="${CASO}/home"; RUN="${CASO}/run/tccdiag"
    SAIDA="${CASO}/saida"
    mkdir -p "${SH}" "${ST}" "${HOMEC}" "${RUN}/${ID}" "${SAIDA}"
    : > "${RUN}/.h4-shim"; : > "${ST}/unit-ativa"; : > "${ST}/chamadas.log"
    echo "$2" > "${ST}/exits"
    cat > "${SH}/python3" <<'EOF'
#!/usr/bin/env bash
( IFS='|'; echo "python3|$*" >> "${ST}/chamadas.log" )
read -r -a ex < "${ST}/exits"
rc="${ex[0]:-0}"; echo "${ex[*]:1}" > "${ST}/exits"
echo "h4 i=7 S1-sync-none falha=none veredito=h4a (shim)"
echo "h4: 1 de 9 execucoes; episodios/h=0.500; parada=$([[ ${rc} == 30 ]] && echo sim || echo nao)"
[[ "${rc}" == 4 ]] && echo "erro: arquivo ilegivel (shim)" >&2
exit "${rc}"
EOF
    cat > "${SH}/systemctl" <<'EOF'
#!/usr/bin/env bash
( IFS='|'; echo "systemctl|$*" >> "${ST}/chamadas.log" )
if [[ "$1" != is-active ]]; then echo "systemctl $*" >> "${ST}/proibido"; exit 1; fi
[[ -f "${ST}/unit-ativa" ]] && exit 0
exit 3
EOF
    for k in kill pkill killall; do
        printf '#!/usr/bin/env bash\necho "%s $*" >> "${ST}/proibido"\nexit 1\n' "${k}" > "${SH}/${k}"
    done
    chmod +x "${SH}"/*
    export ST
}

vigiar() {  # vigiar <ciclos>: roda o vigia com periodo de 1 s; define RC e OUT
    OUT="$(PATH="${SH}:${PATH}" HOME="${HOMEC}" H4_MODO_TESTE=1 H4_RUN="${RUN}" \
        bash "${VIGIA}" --id "${ID}" --saida "${SAIDA}" --fase noite1 --periodo 1 --ciclos "$1" 2>&1)"; RC=$?
}
D() { echo "${HOMEC}/rebaseline-infra/${ID}"; }

echo "== V1: exit 30 grava PARADA-H4 e continua; 4 em 2 ciclos seguidos grava ALERTA-H4C =="
novo_caso v1 "0 30 4 4 0"
vigiar 5
ok "V1 exit 0 no fim dos ciclos" "0" "${RC}"
ok "V1 h4.py chamado em todos os ciclos (continua depois do 30)" "5" "$(grep -c '^python3|' "${ST}/chamadas.log")"
contem "V1 comando do h4.py" "python3|-I|$(cd "${DIR}/.." && pwd -P)/analysis/h4.py|fase|--amostras|${RUN}/${ID}/amostras.txt|--saida|${SAIDA}|--fase|noite1" "$(cat "${ST}/chamadas.log")"
verdade "V1 PARADA-H4 gravado" test -s "$(D)/PARADA-H4"
contem "V1 PARADA-H4 traz a saida do h4.py" "parada=sim" "$(cat "$(D)/PARADA-H4")"
ok "V1 PARADA-H4 so do exit 30 (unit ativa)" "1" "$(grep -c '^== ' "$(D)/PARADA-H4")"
contem "V1 aviso grande" "#### PARADA H4 da fase noite1" "${OUT}"
verdade "V1 ALERTA-H4C gravado" test -s "$(D)/ALERTA-H4C"
ok "V1 ALERTA-H4C so no segundo 4 seguido" "1" "$(grep -c '^== ' "$(D)/ALERTA-H4C")"
contem "V1 aviso do h4c" "#### ALERTA h4c" "${OUT}"

echo "== V2: um 4 isolado nao gera ALERTA-H4C =="
novo_caso v2 "4 0 4 0"
vigiar 4
ok "V2 exit 0" "0" "${RC}"
verdade "V2 sem ALERTA-H4C" test ! -e "$(D)/ALERTA-H4C"
verdade "V2 sem PARADA-H4" test ! -e "$(D)/PARADA-H4"

echo "== V3: unit do amostrador inativa (regra 3) =="
novo_caso v3 "0 0"
rm -f "${ST}/unit-ativa"
vigiar 2
ok "V3 exit 0" "0" "${RC}"
contem "V3 PARADA-H4 com a unit inativa" "unit tcc-amostrador-${ID} inativa" "$(cat "$(D)/PARADA-H4" 2> /dev/null)"
contem "V3 aviso" "INATIVA (regra 3)" "${OUT}"

echo "== V4: nunca manda sinal nem para unit =="
ok "V4 nenhum kill/pkill/killall nem systemctl alem de is-active" "" "$(cat "${TMP}"/*/estado/proibido 2> /dev/null)"
ok "V4 o vigia nao tem kill, pkill, killall nem systemctl stop no codigo" "0" \
    "$(grep -v '^ *#' "${VIGIA}" | grep -cE '\b(kill|pkill|killall)\b|systemctl +(stop|kill|restart)')"
novo_caso v4 "0"
OUT="$(PATH="${SH}:${PATH}" HOME="${HOMEC}" bash "${VIGIA}" --id "x" --saida "${SAIDA}" --fase noite1 2>&1)"; RC=$?
ok "V4 --id invalido -> exit 2" "2" "${RC}"
OUT="$(PATH="${SH}:${PATH}" HOME="${HOMEC}" H4_RUN="${RUN}" bash "${VIGIA}" --id "${ID}" --saida "${SAIDA}" --fase noite1 2>&1)"; RC=$?
ok "V4 H4_RUN sem H4_MODO_TESTE -> exit 2" "2" "${RC}"
OUT="$(PATH="${SH}:${PATH}" HOME="${HOMEC}" bash "${VIGIA}" --id "${ID}" --saida "${SAIDA}" --fase noite3 2>&1)"; RC=$?
ok "V4 --fase invalida -> exit 2" "2" "${RC}"

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
