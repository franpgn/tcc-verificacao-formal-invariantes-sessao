#!/usr/bin/env bash
#
# amostrador-infra.test.sh
#
# Testes A1..A7 do scripts/amostrador-infra.sh (prompt W-h4 §4.2). Sem VM, sem /proc real, sem systemd: H4_PROC e
# H4_RUN apontam para diretorios do teste (com a marca .h4-shim, so com H4_MODO_TESTE=1) e sudo, systemd-run,
# systemctl, df e install sao shims em PATH que registram os argumentos. O systemd-run de shim roda o comando
# da unit em segundo plano, e o systemctl de shim para com SIGTERM e responde is-active pelo PID.
#
# Uso: bash scripts/amostrador-infra.test.sh [A1 A3 ...]   (sem argumentos: todos). Exit 0 se todos passarem.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "${DIR}/.." && pwd)"
AMOSTRADOR="${DIR}/amostrador-infra.sh"
BASH_ABS=/usr/bin/bash
REAL_INSTALL="$(command -v install)"
REAL_PY="$(command -v python3 || command -v python)"
TMP="$(mktemp -d)"
PIDS=()
limpar() {
    local p
    for p in "${PIDS[@]}"; do kill -TERM "${p}" 2> /dev/null; done
    for f in "${TMP}"/*/estado/unit-*.pid; do [[ -f "${f}" ]] && kill -KILL "$(cat "${f}")" 2> /dev/null; done
    sleep 0.3
    for p in "${PIDS[@]}"; do kill -KILL "${p}" 2> /dev/null; done
    rm -rf "${TMP}"
}
trap limpar EXIT
SELECAO=" ${*:-A1 A2 A3 A4 A5 A6 A7} "
ID="diag-20261105T000000Z"

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
nao_contem() {
    if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "FAIL: $1 (encontrou '$2')"; FAIL=$((FAIL + 1))
    else echo "PASS: $1"; PASS=$((PASS + 1)); fi
}
verdade() {
    local d="$1"; shift
    if "$@"; then echo "PASS: ${d}"; PASS=$((PASS + 1)); else echo "FAIL: ${d}"; FAIL=$((FAIL + 1)); fi
}
quer() { [[ "${SELECAO}" == *" $1 "* ]]; }

# ----------------------------------------------------------------------------- ambiente de um caso
# novo_caso <nome>: PROC e RUN de teste, shims, HOME do caso; exporta as variaveis do modo de teste.
novo_caso() {
    CASO="${TMP}/$1"
    PROC="${CASO}/proc"; RUN="${CASO}/run/tccdiag"; SH="${CASO}/shims"; ST="${CASO}/estado"; HOMEC="${CASO}/home"
    mkdir -p "${PROC}/pressure" "${PROC}/sys/kernel/random" "${RUN}" "${SH}" "${ST}" "${HOMEC}"
    : > "${PROC}/.h4-shim"; : > "${RUN}/.h4-shim"
    printf 'some avg10=0.00 avg60=0.00 avg300=0.00 total=111\nfull avg10=0.00 avg60=0.00 avg300=0.00 total=222\n' > "${PROC}/pressure/io"
    printf 'some avg10=0.00 avg60=0.00 avg300=0.00 total=333\nfull avg10=0.00 avg60=0.00 avg300=0.00 total=0\n' > "${PROC}/pressure/cpu"
    printf 'some avg10=0.00 avg60=0.00 avg300=0.00 total=444\nfull avg10=0.00 avg60=0.00 avg300=0.00 total=555\n' > "${PROC}/pressure/memory"
    printf '12345.67 999.99\n' > "${PROC}/uptime"
    printf '   8       0 sda 17555 6432 1406200 226815 348088 363847 5907290 22389283 0 1892400 24166498 0 0 0 0 147425 1550398\n   8       1 sda1 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17\n' > "${PROC}/diskstats"
    echo "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0" > "${PROC}/sys/kernel/random/boot_id"
    criar_shims
    export H4_MODO_TESTE=1 H4_PROC="${PROC}" H4_RUN="${RUN}" ST
    export PATH_TESTE="${SH}:${PATH}"
}

criar_shims() {
    cat > "${SH}/sudo" <<'EOF'
#!/usr/bin/env bash
( IFS='|'; echo "sudo|$*" >> "${ST}/chamadas.log" )
[[ "$1" == "-n" ]] && shift
if [[ "$1" == "true" && -f "${ST}/sudo-negado" ]]; then exit 1; fi
exec "$@"
EOF
    cat > "${SH}/systemd-run" <<'EOF'
#!/usr/bin/env bash
( IFS='|'; echo "systemd-run|$*" >> "${ST}/chamadas.log" )
[[ -f "${ST}/systemd-run-falha" ]] && exit 1
u=""
while [[ $# -gt 0 && "$1" == --* ]]; do [[ "$1" == --unit=* ]] && u="${1#--unit=}"; shift; done
[[ -f "${ST}/systemd-run-nao-inicia" ]] && exit 0
"$@" > /dev/null 2>> "${ST}/unit-${u}.stderr" &
echo "$!" > "${ST}/unit-${u}.pid"
echo "Running as unit: ${u}.service"
EOF
    cat > "${SH}/systemctl" <<'EOF'
#!/usr/bin/env bash
( IFS='|'; echo "systemctl|$*" >> "${ST}/chamadas.log" )
case "$1" in
    is-active)
        u="${@: -1}"; f="${ST}/unit-${u}.pid"
        [[ -f "${f}" ]] && kill -0 "$(cat "${f}")" 2> /dev/null && exit 0
        exit 3 ;;
    stop)
        u="$2"; f="${ST}/unit-${u}.pid"
        if [[ -f "${f}" ]]; then
            p="$(cat "${f}")"; kill -TERM "${p}" 2> /dev/null
            for _ in $(seq 1 50); do kill -0 "${p}" 2> /dev/null || break; sleep 0.1; done
        fi
        exit 0 ;;
    list-units)
        for f in "${ST}"/unit-*.pid; do
            [[ -f "${f}" ]] || continue
            u="${f##*/unit-}"; u="${u%.pid}"
            if kill -0 "$(cat "${f}")" 2> /dev/null; then echo "${u}.service loaded active running"; else echo "${u}.service loaded inactive dead"; fi
        done ;;
esac
exit 0
EOF
    cat > "${SH}/df" <<'EOF'
#!/usr/bin/env bash
( IFS='|'; echo "df|$*" >> "${ST}/chamadas.log" )
v="$(cat "${ST}/df-livre" 2> /dev/null || echo 1073741824)"
if [[ "$*" == *--output=avail* ]]; then printf 'Avail\n%s\n' "${v}"; else printf 'Filesystem 1B-blocks Used Available Use%% Mounted on\ntmpfs 2147483648 1 %s 1%% /run\n' "${v}"; fi
EOF
    cat > "${SH}/install" <<EOF
#!/usr/bin/env bash
( IFS='|'; echo "install|\$*" >> "\${ST}/chamadas.log" )
args=(); while [[ \$# -gt 0 ]]; do case "\$1" in -o|-g) shift 2 ;; *) args+=("\$1"); shift ;; esac; done
"${REAL_INSTALL}" "\${args[@]}" || exit \$?
if [[ -f "\${ST}/install-corrompe" && "\${args[0]}" != -d ]]; then echo lixo >> "\${args[-1]}"; fi
exit 0
EOF
    chmod +x "${SH}"/*
    : > "${ST}/chamadas.log"
}

amostrador() {  # amostrador <args...>: roda com os shims; define RC e OUT
    OUT="$(PATH="${PATH_TESTE}" HOME="${HOMEC}" "${BASH_ABS}" "${AMOSTRADOR}" "$@" 2>&1)"; RC=$?
}
dados() { grep -v '^#' "$1"; }
n_dados() { grep -cv '^#' "$1"; }
intervalos() {  # intervalos <arquivo>: um intervalo (s) por linha entre amostras seguidas
    awk 'substr($0, 1, 1) != "#" { if (n++) printf "%.6f\n", $1 - u; u = $1 }' "$1"
}
esperar_linhas() {  # esperar_linhas <arquivo> <n> <timeout_s>
    local k
    for ((k = 0; k < $3 * 10; k++)); do
        [[ -f "$1" && "$(n_dados "$1")" -ge "$2" ]] && return 0
        sleep 0.1
    done
    return 1
}
laco_fundo() {  # laco_fundo [env extra...]: _laco em segundo plano; define LPID
    mkdir -p "${RUN}/${ID}"
    env "$@" "${BASH_ABS}" "${AMOSTRADOR}" _laco --id "${ID}" --disco sda > "${CASO}/laco.out" 2>&1 &
    LPID=$!; PIDS+=("${LPID}")
}
parar_pid() { kill -TERM "$1" 2> /dev/null; local rc=0; wait "$1" || rc=$?; echo "${rc}"; }

# ============================================================================= A1
if quer A1; then
echo "== A1: _laco com PATH vazio por 5 s grava >= 4 amostras (nenhum processo externo no laco) =="
novo_caso a1
laco_fundo PATH=""
sleep 5
RC_FIM="$(parar_pid "${LPID}")"
A="${RUN}/${ID}/amostras.txt"
N="$(n_dados "${A}")"
verdade "A1 >= 4 amostras com PATH vazio (obtido ${N})" test "${N}" -ge 4
ok "A1 nenhuma linha de erro" "0" "$(grep -c '^# erro' "${A}")"
ok "A1 saida padrao/erro vazias" "" "$(cat "${CASO}/laco.out")"
# o trecho do laco nao tem substituicao de comando, crase, pipe nem sleep (PATH vazio nao pegaria $( ) de builtin)
TRECHO="$(sed -n '/^#<laco>$/,/^#<\/laco>$/p' "${AMOSTRADOR}")"
verdade "A1 marcadores <laco> encontrados" test "$(printf '%s\n' "${TRECHO}" | grep -c .)" -gt 50
ok "A1 trecho do laco sem \$( ) nem crase" "0" "$(printf '%s\n' "${TRECHO}" | grep -v '^ *#' | grep -cE '\$\([^(]|`')"
ok "A1 trecho do laco sem pipe" "0" "$(printf '%s\n' "${TRECHO}" | grep -v '^ *#' | sed 's/||//g' | grep -c '|')"
ok "A1 trecho do laco sem sleep" "0" "$(printf '%s\n' "${TRECHO}" | grep -v '^ *#' | grep -cw 'sleep')"
fi

# ============================================================================= A2
if quer A2; then
echo "== A2: formato v1 linha a linha; cabecalho com o SHA-256 do proprio arquivo =="
novo_caso a2
laco_fundo PATH="${PATH}"
esperar_linhas "${RUN}/${ID}/amostras.txt" 3 10
# cpu sem linha full (kernel antigo) e disco fora do diskstats
printf 'some avg10=0.00 avg60=0.00 avg300=0.00 total=777\n' > "${PROC}/pressure/cpu"
printf '   8       1 sda1 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17\n' > "${PROC}/diskstats"
sleep 2.5
parar_pid "${LPID}" > /dev/null
A="${RUN}/${ID}/amostras.txt"
SHA="$(sha256sum "${AMOSTRADOR}" | cut -d' ' -f1)"; SHA="${SHA#\\}"
CAB_RE="^# tcc-amostrador-infra v1 id=${ID} inicio=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-][0-9]{2}:[0-9]{2} boot_id=0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0 host=[^ ]+ disco=sda periodo_s=1 pid=[0-9]+ versao_script_sha256=${SHA}$"
verdade "A2 cabecalho v1 (linha 1)" bash -c "head -1 '${A}' | grep -qE '${CAB_RE}'"
L_RE='^[0-9]+\.[0-9]{6} [0-9]+\.[0-9]+ io [0-9]+ [0-9]+ cpu [0-9]+ ([0-9]+|-) mem [0-9]+ [0-9]+ sda( [0-9]+){17}$|^[0-9]+\.[0-9]{6} [0-9]+\.[0-9]+ io [0-9]+ [0-9]+ cpu [0-9]+ ([0-9]+|-) mem [0-9]+ [0-9]+ sda -$'
ok "A2 toda linha de dados casa com o formato v1" "0" "$(dados "${A}" | grep -cvE "${L_RE}")"
contem "A2 amostra completa" "12345.67 io 111 222 cpu 333 0 mem 444 555 sda 17555 6432 1406200 226815 348088 363847 5907290 22389283 0 1892400 24166498 0 0 0 0 147425 1550398" "$(cat "${A}")"
contem "A2 cpu sem full grava '-' e disco ausente grava 'sda -'" "io 111 222 cpu 777 - mem 444 555 sda -" "$(cat "${A}")"
ok "A2 ultima linha e '# fim'" "1" "$(tail -1 "${A}" | grep -cE '^# fim t_real=[0-9]+\.[0-9]{6} amostras=[0-9]+ atrasos_ge_1_5s=[0-9]+$')"
if [[ -n "${REAL_PY}" ]]; then
    N_PY="$("${REAL_PY}" -I -c "import sys; sys.path.insert(0, sys.argv[2]); import h4; print(h4.Serie([sys.argv[1]]).n)" "${A}" "${RAIZ}/analysis" 2>&1)"
    ok "A2 analysis/h4.py le o arquivo (mesmas amostras)" "$(n_dados "${A}")" "${N_PY}"
fi
fi

# ============================================================================= A3
if quer A3; then
echo "== A3: cadencia de prazo absoluto; leitura bloqueada 2,5 s gera um intervalo e nenhuma rajada =="
novo_caso a3
rm -f "${PROC}/uptime"; mkfifo "${PROC}/uptime"
( trap '' PIPE
  k=0
  while :; do
      k=$((k + 1))
      [[ "${k}" -eq 4 ]] && sleep 2.5
      printf '%d.00 1.00\n' "$((1000 + k))" > "${PROC}/uptime" || true
      sleep 0.3
  done ) &
FEED=$!; PIDS+=("${FEED}")
laco_fundo PATH="${PATH}"
sleep 9
parar_pid "${LPID}" > /dev/null
kill -KILL "${FEED}" 2> /dev/null
A="${RUN}/${ID}/amostras.txt"
IV="$(intervalos "${A}")"
echo "   intervalos: $(printf '%s ' ${IV})"
ok "A3 exatamente um intervalo >= 1,5 s" "1" "$(printf '%s\n' "${IV}" | awk '$1 >= 1.5' | grep -c .)"
verdade "A3 o intervalo longo fica entre 2,4 e 3,6 s" bash -c "printf '%s\n' \"${IV}\" | awk '\$1 >= 1.5 { if (\$1 < 2.4 || \$1 > 3.6) exit 1 }'"
ok "A3 sem rajada: no maximo um intervalo < 0,9 s (o do realinhamento ao prazo)" "1" \
    "$(( $(printf '%s\n' "${IV}" | awk '$1 < 0.9' | grep -c .) <= 1 ))"
ok "A3 depois do realinhamento, intervalos de 1 s (+-0,1)" "0" \
    "$(printf '%s\n' "${IV}" | awk 'f && ($1 < 0.9 || $1 > 1.1) {b++} $1 >= 1.5 {f = 1; s = 1; next} s {s = 0; next} END {print b + 0}')"
contem "A3 '# fim' conta o atraso" "atrasos_ge_1_5s=1" "$(tail -1 "${A}")"
fi

# ============================================================================= A4
if quer A4; then
echo "== A4: SIGTERM grava '# fim' e sai com 0 =="
novo_caso a4
laco_fundo PATH="${PATH}"
esperar_linhas "${RUN}/${ID}/amostras.txt" 3 10
RC_FIM="$(parar_pid "${LPID}")"
A="${RUN}/${ID}/amostras.txt"
ok "A4 exit 0 no SIGTERM" "0" "${RC_FIM}"
ok "A4 '# fim' com a contagem de amostras" "1" "$(tail -1 "${A}" | grep -c "^# fim t_real=.* amostras=$(n_dados "${A}") atrasos_ge_1_5s=0$")"
ok "A4 uma linha '# fim'" "1" "$(grep -c '^# fim' "${A}")"
fi

# ============================================================================= A5
if quer A5; then
echo "== A5: _espelho copia so o delta, e idempotente; destino bloqueado nao para o _laco =="
novo_caso a5
E="${HOMEC}/rebaseline-infra/${ID}"; mkdir -p "${E}" "${RUN}/${ID}"
A="${RUN}/${ID}/amostras.txt"
printf 'linha 1\nlinha 2\n' > "${A}"
"${BASH_ABS}" "${AMOSTRADOR}" _espelho --id "${ID}" --espelho-dir "${E}" > "${CASO}/esp.out" 2>&1 &
EP=$!; PIDS+=("${EP}")
for _ in $(seq 1 30); do cmp -s "${A}" "${E}/amostras.txt" 2> /dev/null && break; sleep 0.1; done
ok "A5 primeira copia" "$(cat "${A}")" "$(cat "${E}/amostras.txt" 2> /dev/null)"
ok "A5 offset = tamanho copiado" "$(stat -c %s "${A}")" "$(cat "${RUN}/${ID}/espelho.offset" 2> /dev/null)"
printf 'linha 3\n' >> "${A}"
sleep 0.2
RC_E="$(parar_pid "${EP}")"
ok "A5 _espelho sai com 0 no SIGTERM" "0" "${RC_E}"
ok "A5 a copia final do SIGTERM traz so o delta (sem duplicar)" "$(cat "${A}")" "$(cat "${E}/amostras.txt")"
H4_ESPELHO_PERIODO_S=1 "${BASH_ABS}" "${AMOSTRADOR}" _espelho --id "${ID}" --espelho-dir "${E}" > /dev/null 2>&1 &
EP2=$!; PIDS+=("${EP2}")
sleep 2.5
printf 'linha 4\n' >> "${A}"
sleep 1.8
parar_pid "${EP2}" > /dev/null
ok "A5 idempotente entre execucoes do espelho; delta periodico (1 s de teste)" "$(cat "${A}")" "$(cat "${E}/amostras.txt")"
ok "A5 nenhuma linha duplicada" "0" "$(sort "${E}/amostras.txt" | uniq -d | grep -c .)"
# destino bloqueado: FIFO sem leitor
novo_caso a5b
E="${HOMEC}/rebaseline-infra/${ID}"; mkdir -p "${E}"
mkfifo "${E}/amostras.txt"
laco_fundo PATH="${PATH}"
esperar_linhas "${RUN}/${ID}/amostras.txt" 1 10
H4_ESPELHO_PERIODO_S=1 "${BASH_ABS}" "${AMOSTRADOR}" _espelho --id "${ID}" --espelho-dir "${E}" > /dev/null 2>&1 &
EP3=$!; PIDS+=("${EP3}")
sleep 1
N1="$(n_dados "${RUN}/${ID}/amostras.txt")"
sleep 3
N2="$(n_dados "${RUN}/${ID}/amostras.txt")"
verdade "A5 _laco continua gravando com o espelho bloqueado (${N1} -> ${N2})" test "$((N2 - N1))" -ge 2
verdade "A5 espelho continua vivo (bloqueado na escrita)" kill -0 "${EP3}"
parar_pid "${LPID}" > /dev/null
kill -KILL "${EP3}" 2> /dev/null
( exec 3<> "${E}/amostras.txt"; sleep 0.2 ) 2> /dev/null   # destrava o dd orfao, se houver
fi

# ============================================================================= A6
if quer A6; then
echo "== A6: iniciar / estado / parar com shims de sudo, systemd-run, systemctl, df e install =="
novo_caso a6
amostrador iniciar --id "${ID}"
ok "A6 iniciar exit 0" "0" "${RC}"
L="$(cat "${ST}/chamadas.log")"
contem "A6 install -d -m 0755" "sudo|-n|install|-d|-m|0755|${RUN}/${ID}" "${L}"
contem "A6 unit principal de sistema, com as propriedades" "sudo|-n|systemd-run|--unit=tcc-amostrador-${ID}|--description=tcc amostrador infra ${ID}|--property=Type=simple|--property=StandardOutput=null|--property=StandardError=journal|--property=Restart=no|/usr/bin/bash|${AMOSTRADOR}|_laco|--id|${ID}|--disco|sda" "${L}"
contem "A6 unit do espelho, com o --espelho-dir padrao" "systemd-run|--unit=tcc-amostrador-espelho-${ID}|" "${L}"
contem "A6 espelho em ~/rebaseline-infra/<id>" "_espelho|--id|${ID}|--espelho-dir|${HOMEC}/rebaseline-infra/${ID}" "${L}"
nao_contem "A6 sem --user" "--user" "${L}"
nao_contem "A6 sem --scope" "--scope" "${L}"
verdade "A6 caminho absoluto do script" bash -c "[[ '${AMOSTRADOR}' == /* ]]"
contem "A6 imprime a primeira amostra" "primeira amostra: " "${OUT}"
contem "A6 imprime as duas units" "unit: tcc-amostrador-espelho-${ID}" "${OUT}"
amostrador iniciar --id "${ID}"
ok "A6 unit ja ativa -> exit 3" "3" "${RC}"
contem "A6 motivo" "ja ativa" "${OUT}"
amostrador estado
ok "A6 estado exit 0" "0" "${RC}"
contem "A6 estado lista a unit ativa" "tcc-amostrador-${ID}.service loaded active running" "${OUT}"
contem "A6 estado mostra o arquivo" "${RUN}/${ID}/amostras.txt bytes=" "${OUT}"
contem "A6 estado mostra o df" "tmpfs" "${OUT}"
sleep 1.5
amostrador parar --id "${ID}"
ok "A6 parar exit 0" "0" "${RC}"
F="${HOMEC}/rebaseline-infra/${ID}/amostras-final.txt"
L="$(cat "${ST}/chamadas.log")"
contem "A6 stop da principal" "sudo|-n|systemctl|stop|tcc-amostrador-${ID}" "${L}"
contem "A6 stop do espelho" "sudo|-n|systemctl|stop|tcc-amostrador-espelho-${ID}" "${L}"
contem "A6 copia final com install -m 0644 -o <usuario>" "sudo|-n|install|-m|0644|-o|$(id -un)|${RUN}/${ID}/amostras.txt|${F}" "${L}"
ok "A6 copia final igual a original" "$(sha256sum < "${RUN}/${ID}/amostras.txt")" "$(sha256sum < "${F}")"
verdade "A6 .sha256 confere com sha256sum -c" bash -c "cd '${HOMEC}/rebaseline-infra/${ID}' && sha256sum -c --quiet amostras-final.txt.sha256"
contem "A6 resumo do parar" "amostras=$(n_dados "${F}") primeira=" "${OUT}"
contem "A6 resumo traz linhas de erro" "linhas_erro=0" "${OUT}"
ok "A6 a original fica em /run/tccdiag" "1" "$([[ -f "${RUN}/${ID}/amostras.txt" ]] && echo 1)"
ok "A6 '# fim' na copia final (unit parou com SIGTERM)" "1" "$(grep -c '^# fim' "${F}")"
verdade "A6 espelho em disco existe" test -s "${HOMEC}/rebaseline-infra/${ID}/amostras.txt"
amostrador iniciar --id "${ID}"
ok "A6 mesmo id de novo (arquivo ja existe) -> exit 3" "3" "${RC}"

for caso in pressao disco df sudo; do
    novo_caso "a6-${caso}"
    case "${caso}" in
        pressao) rm -f "${PROC}/pressure/io"; amostrador iniciar --id "${ID}" ;;
        disco) amostrador iniciar --id "${ID}" --disco sdz ;;
        df) echo $((60 * 1024 * 1024)) > "${ST}/df-livre"; amostrador iniciar --id "${ID}" ;;
        sudo) : > "${ST}/sudo-negado"; amostrador iniciar --id "${ID}" ;;
    esac
    ok "A6 pre-condicao (${caso}) -> exit 3" "3" "${RC}"
    ok "A6 pre-condicao (${caso}) nao chama systemd-run" "0" "$(grep -c '^systemd-run' "${ST}/chamadas.log")"
done
novo_caso a6-falha
: > "${ST}/systemd-run-falha"
amostrador iniciar --id "${ID}"
ok "A6 systemd-run falha -> exit 4" "4" "${RC}"
novo_caso a6-sem-linha
: > "${ST}/systemd-run-nao-inicia"
amostrador iniciar --id "${ID}"
ok "A6 nenhuma amostra em 3 s -> exit 4" "4" "${RC}"
contem "A6 motivo" "nenhuma amostra" "${OUT}"
novo_caso a6-corrompe
amostrador iniciar --id "${ID}"
sleep 1.2
: > "${ST}/install-corrompe"
amostrador parar --id "${ID}"
ok "A6 copia final diferente -> exit 4" "4" "${RC}"
verdade "A6 sem .sha256 quando nao confere" test ! -e "${HOMEC}/rebaseline-infra/${ID}/amostras-final.txt.sha256"
novo_caso a6-uso
amostrador iniciar
ok "A6 sem --id -> exit 2" "2" "${RC}"
amostrador reiniciar --id "${ID}"
ok "A6 subcomando desconhecido -> exit 2" "2" "${RC}"
OUT="$(PATH="${PATH_TESTE}" env -u H4_MODO_TESTE "${BASH_ABS}" "${AMOSTRADOR}" estado 2>&1)"; RC=$?
ok "A6 H4_PROC/H4_RUN sem H4_MODO_TESTE -> exit 2" "2" "${RC}"
rm -f "${PROC}/.h4-shim"
amostrador estado
ok "A6 H4_PROC sem a marca .h4-shim -> exit 2" "2" "${RC}"
fi

# ============================================================================= A7
if quer A7; then
echo "== A7: --id invalido -> exit 2 =="
novo_caso a7
for id in "noite3-20261105T000000Z" "noite1-20261105T0000Z" "noite1-20261105T000000" "x" "piloto-20261105T000000Z;rm"; do
    amostrador iniciar --id "${id}"
    ok "A7 iniciar --id '${id}' -> exit 2" "2" "${RC}"
done
amostrador parar --id "ensaio-2026-11-05"
ok "A7 parar --id invalido -> exit 2" "2" "${RC}"
amostrador _laco --id "../etc-20261105T000000Z" --disco sda
ok "A7 _laco --id invalido -> exit 2" "2" "${RC}"
ok "A7 nada chamado com id invalido" "0" "$(grep -c . "${ST}/chamadas.log")"
fi

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
