#!/usr/bin/env bash
#
# inject-partition.test.sh
#
# Suite de testes do inject-partition.sh que NAO exigem VM, podman, tc nem
# privilegio (T-par-1..T-par-10 de docs/plano-b29-particao-real-e-contraexemplos-
# 2026-10-09.md, secao 3.9, mais casos extras T-par-11..T-par-15). Os gates reais
# (G-0..G-6, secao 3.7 do plano) exigem a VM e NAO sao cobertos aqui.
#
# T-par-7..T-par-15 usam SHIMS (scripts falsos de podman, sudo, nsenter, tc,
# flock) colocados no inicio do PATH. Os shims so registram as chamadas em
# $SHIM_LOG e simulam o estado de um qdisc clsact por PID em $SHIM_STATE; assim
# a LOGICA do injetor (ordem dos comandos, limpeza, codigos de saida, sinais)
# e verificada sem kernel. Eles NAO provam que o kernel aceita clsact/u32/drop
# (isso e o gate G-0).
#
# Auto-contido (sem bats). Rodar:  bash scripts/inject-partition.test.sh
# Exit 0 se todos passarem; 1 caso contrario.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PART="${DIR}/inject-partition.sh"
BASE="${DIR}/run-baseline.sh"

PASS=0
FAIL=0
SKIP=0

ok() { # ok <descricao> <esperado> <obtido>
    if [[ "$2" == "$3" ]]; then
        echo "PASS: $1"; PASS=$((PASS + 1))
    else
        echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1))
    fi
}

contem() { # contem <descricao> <substring> <texto>
    if echo "$3" | grep -qF -e "$2"; then
        echo "PASS: $1"; PASS=$((PASS + 1))
    else
        echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1))
    fi
}

nao_contem() { # nao_contem <descricao> <substring> <texto>
    if echo "$3" | grep -qF -e "$2"; then
        echo "FAIL: $1 (encontrou '$2' indevidamente)"; FAIL=$((FAIL + 1))
    else
        echo "PASS: $1"; PASS=$((PASS + 1))
    fi
}

# ordem <descricao> <arquivo-log> <padrao1> <padrao2> ...
# Passa se os padroes (substring fixa) aparecem NESSA ordem (subsequencia).
ordem() {
    local desc="$1" arq="$2"; shift 2
    local ini=1 pad achou
    for pad in "$@"; do
        achou="$(tail -n +"${ini}" "${arq}" | grep -nF -m1 -e "${pad}" | cut -d: -f1)"
        if [[ -z "${achou}" ]]; then
            echo "FAIL: ${desc} (padrao '${pad}' ausente apos a linha ${ini})"
            FAIL=$((FAIL + 1)); return
        fi
        ini=$((ini + achou))
    done
    echo "PASS: ${desc}"; PASS=$((PASS + 1))
}

pulado() { echo "SKIP: $1"; SKIP=$((SKIP + 1)); }

PATH_SEM_PODMAN="/usr/bin:/bin"

# ------------------------------------------------------------------ shims
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
SHIMDIR="${TMP}/shims"
mkdir -p "${SHIMDIR}"
export SHIM_LOG="${TMP}/shim.log"
export SHIM_STATE="${TMP}/state"
export LOCK_DIR="${TMP}/locks"
mkdir -p "${SHIM_STATE}" "${LOCK_DIR}"

# podman: 'inspect [-f FMT] NOME'. PID = 4000 + ultimo digito do nome; IP =
# 10.89.0.<ultimo digito>. SHIM_MISSING (lista) simula container inexistente;
# SHIM_STATUS sobrescreve o estado.
cat >"${SHIMDIR}/podman" <<'EOF'
#!/usr/bin/env bash
echo "podman $*" >>"${SHIM_LOG}"
[[ "$1" == "inspect" ]] || exit 0
shift
fmt=""
if [[ "$1" == "-f" ]]; then fmt="$2"; shift 2; fi
nome="$1"
case " ${SHIM_MISSING:-} " in *" ${nome} "*) exit 1 ;; esac
d="${nome: -1}"
case "${fmt}" in
    *State.Pid*)    echo "$((4000 + d))" ;;
    *State.Status*) echo "${SHIM_STATUS:-running}" ;;
    *IPAddress*)    echo "10.89.0.${d} " ;;
esac
exit 0
EOF

# sudo: nao registra; repassa o resto (o registro e feito pelo nsenter).
cat >"${SHIMDIR}/sudo" <<'EOF'
#!/usr/bin/env bash
exec "$@"
EOF

# nsenter -t PID -n CMD...: registra a linha completa. 'timeout' (teste de
# conexao TCP) devolve SHIM_CONN_RC (padrao 124 = bloqueado). O resto vai ao tc.
cat >"${SHIMDIR}/nsenter" <<'EOF'
#!/usr/bin/env bash
echo "nsenter $*" >>"${SHIM_LOG}"
pid="$2"; shift 3
if [[ "$1" == "timeout" ]]; then exit "${SHIM_CONN_RC:-124}"; fi
export SHIM_PID="${pid}"
exec "$@"
EOF

# tc: estado de clsact por PID em $SHIM_STATE/clsact.<pid>. Falha em qualquer
# 'filter add' que contenha SHIM_TC_FAIL_MATCH (usado para simular a sonda).
cat >"${SHIMDIR}/tc" <<'EOF'
#!/usr/bin/env bash
st="${SHIM_STATE}/clsact.${SHIM_PID}"
args="$*"
case "${args}" in
    "qdisc add dev "*" clsact")
        [[ -z "${SHIM_FAIL_CLSACT:-}" ]] || { echo "Error: Specified qdisc kind is unknown." >&2; exit 2; }
        : >"${st}" ;;
    "filter add "*)
        if [[ -n "${SHIM_TC_FAIL_MATCH:-}" && "${args}" == *"${SHIM_TC_FAIL_MATCH}"* ]]; then
            echo "RTNETLINK answers: Invalid argument" >&2; exit 2
        fi
        [[ -f "${st}" ]] || { echo "Error: Parent Qdisc doesn't exists." >&2; exit 2; }
        echo "${args}" >>"${st}" ;;
    "qdisc del dev "*" clsact")
        [[ -f "${st}" ]] || { echo "Error: Cannot find specified qdisc on specified device." >&2; exit 2; }
        rm -f "${st}" ;;
    "qdisc show dev "*)
        [[ -f "${st}" ]] && echo "qdisc clsact ffff: parent ffff:fff1" ;;
    "-s filter show dev "*)
        # Saida real observada na VM (runs/gate-b29/g0b.log e g1-rep1): o kernel
        # lista os DOIS filtros (match ... at 12 = ip src; at 16 = ip dst) tanto
        # em 'ingress' quanto em 'egress'; so o contador do filtro que morde em
        # cada sentido e > 0. Um filtro por par (isn1, isn2) por offset.
        # SHIM_PKTS_SRC/SHIM_PKTS_DST (padrao SHIM_PKTS, padrao 7): pacotes por
        # filtro em at 12 / at 16.
        if [[ -f "${st}" ]]; then
            n12="${SHIM_PKTS_SRC:-${SHIM_PKTS:-7}}"
            n16="${SHIM_PKTS_DST:-${SHIM_PKTS:-7}}"
            echo "filter protocol ip pref 1 u32 chain 0 "
            for par in 2 3; do
                n="${n16}"
                echo "filter protocol ip pref 1 u32 chain 0 fh 801::80$((par - 2)) order 2048 key ht 801 bkt 0 terminal flowid not_in_hw "
                echo "  match 0a59010${par}/ffffffff at 16"
                echo "        action order 1: gact action drop"
                echo "        Action statistics:"
                echo "        Sent $((n * 120)) bytes ${n} pkt (dropped ${n}, overlimits 0 requeues 0) "
                echo "        backlog 0b 0p requeues 0"
            done
            echo "filter protocol ip pref 1 u32 chain 0 fh 800: ht divisor 1 "
            for par in 2 3; do
                n="${n12}"
                echo "filter protocol ip pref 1 u32 chain 0 fh 800::80$((par - 2)) order 2048 key ht 800 bkt 0 terminal flowid not_in_hw "
                echo "  match 0a59010${par}/ffffffff at 12"
                echo "        action order 1: gact action drop"
                echo "        Action statistics:"
                echo "        Sent $((n * 120)) bytes ${n} pkt (dropped ${n}, overlimits 0 requeues 0) "
                echo "        backlog 0b 0p requeues 0"
            done
        fi ;;
esac
exit 0
EOF

# readlink: so /proc/<pid>/ns/net (guarda de netns, A2 de V-B). Cada PID tem
# netns proprio, exceto os nos em SHIM_HOSTNET (simula --network=host: mesmo
# netns do PID 1) e SHIM_NO_NS (leitura falha). Igual ao de inject-netdegrade.
cat >"${SHIMDIR}/readlink" <<'EOF2'
#!/usr/bin/env bash
echo "readlink $*" >>"${SHIM_LOG}"
case "$1" in
    /proc/1/ns/net) echo "net:[4026531840]" ;;
    /proc/*/ns/net)
        p="${1#/proc/}"; p="${p%%/*}"; d=$((p - 4000))
        case " ${SHIM_NO_NS:-} " in *" isn${d} "*) exit 1 ;; esac
        case " ${SHIM_HOSTNET:-} " in *" isn${d} "*) echo "net:[4026531840]" ;; *) echo "net:[40265${p}]" ;; esac ;;
    *) exit 1 ;;
esac
EOF2

# flock: sempre concede o lock (o teste de exclusao real e o T-par-15).
cat >"${SHIMDIR}/flock" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${SHIMDIR}"/*

PATH_SHIMS="${SHIMDIR}:${PATH_SEM_PODMAN}"

reset_shims() {
    : >"${SHIM_LOG}"
    rm -f "${SHIM_STATE}"/clsact.* "${TMP}/marker" "${TMP}/saida" "${LOCK_DIR}"/* 2>/dev/null || true
    unset SHIM_MISSING SHIM_STATUS SHIM_CONN_RC SHIM_TC_FAIL_MATCH SHIM_PKTS SHIM_PKTS_SRC SHIM_PKTS_DST SHIM_HOSTNET SHIM_NO_NS SHIM_FAIL_CLSACT
}

# roda_shim <args...>: executa o injetor com os shims; saida em $OUT, rc em $RC.
roda_shim() {
    OUT="$(PATH="${PATH_SHIMS}" bash "${PART}" "$@" 2>&1)"; RC=$?
}

# ================================================================ sem shims
echo "== T-par-1: --help retorna 0 e documenta as flags =="
OUT="$(bash "${PART}" --help)"; RC=$?
ok "T-par-1 exit code" "0" "${RC}"
for f in --minority --nodes --iface --duration --marker --heal --dry-run --help; do
    contem "T-par-1 documenta ${f}" "${f}" "${OUT}"
done
contem "T-par-1 documenta exit 6" "6" "${OUT}"

echo "== T-par-2: --dry-run (defaults) sem podman: nsenter/clsact/filtros por par =="
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${PART}" --dry-run --minority isn3 2>&1)"; RC=$?
ok "T-par-2 exit code" "0" "${RC}"
contem "T-par-2 resolve pid" "podman inspect -f '{{.State.Pid}}' isn3" "${OUT}"
contem "T-par-2 resolve IP dos pares" "IPAddress" "${OUT}"
contem "T-par-2 add clsact" "sudo nsenter -t <pid> -n tc qdisc add dev eth0 clsact" "${OUT}"
contem "T-par-2 filtro ingress (src)" "sudo nsenter -t <pid> -n tc filter add dev eth0 ingress protocol ip prio 1 u32 match ip src <ip-par>/32 action drop" "${OUT}"
contem "T-par-2 filtro egress (dst)" "sudo nsenter -t <pid> -n tc filter add dev eth0 egress protocol ip prio 1 u32 match ip dst <ip-par>/32 action drop" "${OUT}"
contem "T-par-2 del clsact" "sudo nsenter -t <pid> -n tc qdisc del dev eth0 clsact" "${OUT}"
contem "T-par-2 duracao padrao 90" "sleep 90" "${OUT}"
ok "T-par-2 nao usa podman exec" "0" "$(echo "${OUT}" | grep -cF 'podman exec')"
ok "T-par-2 dois pares (isn1, isn2) -> 2 filtros ingress" "2" "$(echo "${OUT}" | grep -c 'tc filter add dev eth0 ingress')"
contem "T-par-2 nada executado" "dry-run: nada executado" "${OUT}"
ok "T-par-2 sem aviso de atraso severo com 90 s" "0" "$(echo "${OUT}" | grep -ci 'atraso severo')"

echo "== T-par-3: --dry-run com 5 nos, --minority isn1, --duration, --marker =="
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${PART}" --dry-run --minority isn1 \
        --nodes isn1,isn2,isn3,isn4,isn5 --duration 120 --iface ens4 \
        --marker /tmp/m.txt 2>&1)"; RC=$?
ok "T-par-3 exit code" "0" "${RC}"
contem "T-par-3 inspect do no isolado" "podman inspect -f '{{.State.Pid}}' isn1" "${OUT}"
ok "T-par-3 4 pares -> 4 filtros ingress" "4" "$(echo "${OUT}" | grep -c 'tc filter add dev ens4 ingress')"
ok "T-par-3 4 pares -> 4 filtros egress" "4" "$(echo "${OUT}" | grep -c 'tc filter add dev ens4 egress')"
for p in isn2 isn3 isn4 isn5; do contem "T-par-3 par ${p} listado" "par ${p}" "${OUT}"; done
nao_contem "T-par-3 o proprio no isolado nao e par" "par isn1" "${OUT}"
contem "T-par-3 duracao custom" "sleep 120" "${OUT}"
contem "T-par-3 marcador citado" "/tmp/m.txt" "${OUT}"
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${PART}" --dry-run --minority isn1,isn2 \
        --nodes isn1,isn2,isn3,isn4,isn5 2>&1)"; RC=$?
ok "T-par-3b minoria de 2 em 5 nos aceita" "0" "${RC}"
ok "T-par-3b 3 pares por no isolado" "3" "$(echo "${OUT}" | grep -c 'tc filter add dev eth0 ingress.*# no isn1')"

echo "== T-par-4: argumentos invalidos -> exit 2 =="
bash "${PART}" --dry-run >/dev/null 2>&1; RC=$?
ok "T-par-4a sem --minority" "2" "${RC}"
bash "${PART}" --dry-run --minority isn9 >/dev/null 2>&1; RC=$?
ok "T-par-4b no fora de --nodes" "2" "${RC}"
bash "${PART}" --dry-run --minority isn1,isn2 >/dev/null 2>&1; RC=$?
ok "T-par-4c isolar a maioria (2 de 3)" "2" "${RC}"
bash "${PART}" --dry-run --minority isn1,isn2,isn3 >/dev/null 2>&1; RC=$?
ok "T-par-4c2 isolar todos os nos" "2" "${RC}"
bash "${PART}" --dry-run --minority isn3 --nodes isn1,isn2,isn3,isn4 --duration 90 \
    >/dev/null 2>&1; RC=$?
ok "T-par-4c3 1 de 4 nos aceito" "0" "${RC}"
bash "${PART}" --dry-run --minority isn3,isn4 --nodes isn1,isn2,isn3,isn4 >/dev/null 2>&1; RC=$?
ok "T-par-4c4 2 de 4 (metade, empate) rejeitado" "2" "${RC}"
bash "${PART}" --dry-run --minority isn3 --duration 0 >/dev/null 2>&1; RC=$?
ok "T-par-4d --duration 0" "2" "${RC}"
bash "${PART}" --dry-run --minority isn3 --duration abc >/dev/null 2>&1; RC=$?
ok "T-par-4d2 --duration nao inteiro" "2" "${RC}"
bash "${PART}" --foo >/dev/null 2>&1; RC=$?
ok "T-par-4e flag desconhecida" "2" "${RC}"
bash "${PART}" --minority >/dev/null 2>&1; RC=$?
ok "T-par-4f --minority sem valor" "2" "${RC}"
bash "${PART}" --dry-run --minority isn3 --nodes >/dev/null 2>&1; RC=$?
ok "T-par-4f2 --nodes sem valor" "2" "${RC}"
bash "${PART}" --dry-run --minority 'isn3;rm' >/dev/null 2>&1; RC=$?
ok "T-par-4g nome de no com caracteres invalidos" "2" "${RC}"
bash "${PART}" --dry-run --minority isn3 --nodes isn1,isn2 >/dev/null 2>&1; RC=$?
ok "T-par-4h no isolado fora de --nodes (lista de 2)" "2" "${RC}"
bash "${PART}" --dry-run --minority isn3 --nodes isn1 >/dev/null 2>&1; RC=$?
ok "T-par-4i cluster de 1 no" "2" "${RC}"

echo "== T-par-5: --duration 20 -> aviso de atraso severo e plano normal =="
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${PART}" --dry-run --minority isn3 --duration 20 2>&1)"; RC=$?
ok "T-par-5 exit code" "0" "${RC}"
contem "T-par-5 aviso" "atraso severo" "${OUT}"
contem "T-par-5 plano segue (sleep 20)" "sleep 20" "${OUT}"
contem "T-par-5 plano segue (clsact)" "tc qdisc add dev eth0 clsact" "${OUT}"

echo "== T-par-6: run-baseline.sh --faults 'none F2' --dry-run descreve o ramo F2 =="
if [[ -f "${BASE}" ]] && grep -q 'F2' "${BASE}" && grep -q 'inject-partition' "${BASE}"; then
    OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${BASE}" --faults "none F2" \
            --scenarios S1 --reps 1 --duration 60 --dry-run 2>&1)"; RC=$?
    ok "T-par-6 exit code" "0" "${RC}"
    contem "T-par-6 menciona ramo F2" "S1 / F2" "${OUT}"
else
    pulado "T-par-6 (run-baseline.sh ainda nao integra F2: escopo de B-29.1, nao de B-29)"
fi

# ================================================================ com shims
echo "== T-par-7: execucao completa com shims (--duration 1) =="
reset_shims
roda_shim --minority isn3 --duration 1 --marker "${TMP}/marker"
ok "T-par-7 exit code" "0" "${RC}"
ordem "T-par-7 sequencia resolve > pre-limpeza > sonda > add > filtros > show > del" "${SHIM_LOG}" \
    "podman inspect -f {{.State.Pid}} isn3" \
    "tc qdisc del dev eth0 clsact" \
    "tc qdisc add dev eth0 clsact" \
    "tc filter add dev eth0 ingress" \
    "tc qdisc del dev eth0 clsact" \
    "tc qdisc add dev eth0 clsact" \
    "tc filter add dev eth0 ingress protocol ip prio 1 u32 match ip src 10.89.0.1/32 action drop" \
    "tc filter add dev eth0 egress protocol ip prio 1 u32 match ip dst 10.89.0.1/32 action drop" \
    "tc filter add dev eth0 ingress protocol ip prio 1 u32 match ip src 10.89.0.2/32 action drop" \
    "tc filter add dev eth0 egress protocol ip prio 1 u32 match ip dst 10.89.0.2/32 action drop" \
    "tc -s filter show dev eth0 ingress" \
    "tc -s filter show dev eth0 egress" \
    "tc qdisc del dev eth0 clsact"
LOG="$(cat "${SHIM_LOG}")"
contem "T-par-7 resolve IP de isn1" "podman inspect -f {{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}} isn1" "${LOG}"
contem "T-par-7 teste de conexao ao par:7800" "timeout" "${LOG}"
nao_contem "T-par-7 so toca no PID de isn3 (4003)" "nsenter -t 4001" "${LOG}"
nao_contem "T-par-7 nao toca no PID de isn2 (4002)" "nsenter -t 4002" "${LOG}"
ok "T-par-7 sem residuo de clsact no fim" "0" "$(ls "${SHIM_STATE}"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
ok "T-par-7 marcador com 2 linhas" "2" "$(wc -l <"${TMP}/marker" | tr -d ' ')"
contem "T-par-7 marcador start" "fault=F2 node=isn3 event=start t_epoch_ns=" "$(cat "${TMP}/marker")"
contem "T-par-7 marcador end" "fault=F2 node=isn3 event=end t_epoch_ns=" "$(cat "${TMP}/marker")"
contem "T-par-7 imprime contadores" "pkt" "${OUT}"

echo "== T-par-8: tc falha na sonda -> exit 5 e limpeza chamada =="
reset_shims
export SHIM_TC_FAIL_MATCH="192.0.2.1"   # IP da sonda (TEST-NET-1, RFC 5737)
roda_shim --minority isn3 --duration 1 --marker "${TMP}/marker"
ok "T-par-8 exit code" "5" "${RC}"
contem "T-par-8 mensagem de diagnostico" "sonda" "${OUT}"
ordem "T-par-8 sonda tentou o filtro e a limpeza veio depois" "${SHIM_LOG}" \
    "tc filter add dev eth0 ingress" "tc qdisc del dev eth0 clsact"
nao_contem "T-par-8 nenhum filtro de par aplicado" "10.89.0." "$(cat "${SHIM_LOG}")"
ok "T-par-8 sem residuo de clsact" "0" "$(ls "${SHIM_STATE}"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
reset_shims
export SHIM_FAIL_CLSACT=1               # kernel sem sch_clsact
roda_shim --minority isn3 --duration 1
ok "T-par-8b clsact rejeitado -> exit 5" "5" "${RC}"
reset_shims

echo "== T-par-9: SIGTERM durante a janela -> qdisc del clsact antes de sair =="
reset_shims
PATH="${PATH_SHIMS}" bash "${PART}" --minority isn3 --duration 30 \
    --marker "${TMP}/marker" >"${TMP}/saida" 2>&1 &
BGPID=$!
ESPERA=0
until grep -qF -e "tc -s filter show dev eth0 egress" "${SHIM_LOG}" 2>/dev/null; do
    sleep 0.2; ESPERA=$((ESPERA + 1))
    [[ "${ESPERA}" -lt 100 ]] || break
done
ok "T-par-9 chegou a janela ativa" "1" "$(ls "${SHIM_STATE}"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
LINHAS_ANTES="$(wc -l <"${SHIM_LOG}" | tr -d ' ')"
kill -TERM "${BGPID}" 2>/dev/null
T0=$SECONDS
wait "${BGPID}" 2>/dev/null; RC=$?
ok "T-par-9 saiu rapido (nao esperou os 30 s)" "1" "$(( (SECONDS - T0) < 10 ? 1 : 0 ))"
ok "T-par-9 exit code 143 (128+SIGTERM)" "143" "${RC}"
tail -n +"$((LINHAS_ANTES + 1))" "${SHIM_LOG}" >"${TMP}/depois.log"
contem "T-par-9 qdisc del clsact apos o sinal" "tc qdisc del dev eth0 clsact" "$(cat "${TMP}/depois.log")"
ok "T-par-9 sem residuo de clsact" "0" "$(ls "${SHIM_STATE}"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
contem "T-par-9 marcador end gravado mesmo com sinal" "event=end" "$(cat "${TMP}/marker")"
reset_shims

echo "== T-par-10: --heal chama so a limpeza =="
reset_shims
: >"${SHIM_STATE}/clsact.4003"   # simula sobra de uma execucao morta por SIGKILL
roda_shim --heal --minority isn3
ok "T-par-10 exit code" "0" "${RC}"
LOG="$(cat "${SHIM_LOG}")"
contem "T-par-10 chama del clsact em isn3" "nsenter -t 4003 -n tc qdisc del dev eth0 clsact" "${LOG}"
nao_contem "T-par-10 nao adiciona qdisc" "qdisc add" "${LOG}"
nao_contem "T-par-10 nao adiciona filtro" "filter add" "${LOG}"
ok "T-par-10 resto removido" "0" "$(ls "${SHIM_STATE}"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
reset_shims
roda_shim --heal
ok "T-par-10b --heal sem --minority: exit 0" "0" "${RC}"
ok "T-par-10b limpa os 3 nos" "3" "$(grep -c 'tc qdisc del dev eth0 clsact' "${SHIM_LOG}")"
reset_shims
SHIM_MISSING="isn2" roda_shim --heal
ok "T-par-10c --heal tolera no ausente (container caiu: netns sumiu)" "0" "${RC}"
reset_shims
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${PART}" --heal --dry-run 2>&1)"; RC=$?
ok "T-par-10d --heal --dry-run" "0" "${RC}"
contem "T-par-10d plano de heal" "tc qdisc del dev eth0 clsact" "${OUT}"

echo "== T-par-11: codigo 6 -- conexao ainda possivel ao par =="
reset_shims
SHIM_CONN_RC=0 roda_shim --minority isn3 --duration 1
ok "T-par-11 exit code" "6" "${RC}"
contem "T-par-11 diagnostico" "nao se efetivou" "${OUT}"
ok "T-par-11 sem residuo de clsact" "0" "$(ls "${SHIM_STATE}"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"

echo "== T-par-12: codigo 6 -- contadores zerados no fim da janela =="
reset_shims
SHIM_PKTS=0 roda_shim --minority isn3 --duration 1
ok "T-par-12 exit code" "6" "${RC}"
contem "T-par-12 diagnostico" "zerad" "${OUT}"
ok "T-par-12 sem residuo de clsact" "0" "$(ls "${SHIM_STATE}"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"

echo "== T-par-13: container inexistente/parado -> exit 4 =="
reset_shims
SHIM_MISSING="isn3" roda_shim --minority isn3 --duration 1
ok "T-par-13a no isolado inexistente" "4" "${RC}"
reset_shims
SHIM_MISSING="isn2" roda_shim --minority isn3 --duration 1
ok "T-par-13b par inexistente" "4" "${RC}"
reset_shims
SHIM_STATUS="exited" roda_shim --minority isn3 --duration 1
ok "T-par-13c container nao running" "4" "${RC}"
nao_contem "T-par-13 nada alterado no kernel" "filter add" "$(cat "${SHIM_LOG}")"

echo "== T-par-14: PODMAN='sudo podman' aparece no plano e no uso real =="
OUT="$(PATH="${PATH_SEM_PODMAN}" PODMAN="sudo podman" bash "${PART}" --dry-run --minority isn3 2>&1)"; RC=$?
ok "T-par-14 dry-run exit" "0" "${RC}"
contem "T-par-14 plano usa sudo podman inspect" "pid=\$(sudo podman inspect -f '{{.State.Pid}}' isn3)" "${OUT}"
reset_shims
OUT="$(PATH="${PATH_SHIMS}" PODMAN="sudo podman" bash "${PART}" --minority isn3 --duration 1 2>&1)"; RC=$?
ok "T-par-14 execucao com PODMAN='sudo podman'" "0" "${RC}"

echo "== T-par-15: flock real impede duas injecoes simultaneas no mesmo no =="
if command -v flock >/dev/null 2>&1; then
    # PATH sem o shim de flock para usar o flock real do host.
    SHIMDIR2="${TMP}/shims-semflock"; mkdir -p "${SHIMDIR2}"
    for s in podman sudo nsenter tc readlink; do cp "${SHIMDIR}/${s}" "${SHIMDIR2}/${s}"; done
    FLOCK_DIR="$(dirname "$(command -v flock)")"
    reset_shims
    PATH="${SHIMDIR2}:${FLOCK_DIR}:${PATH_SEM_PODMAN}" bash "${PART}" --minority isn3 --duration 5 >/dev/null 2>&1 &
    P1=$!
    sleep 1.5
    OUT="$(PATH="${SHIMDIR2}:${FLOCK_DIR}:${PATH_SEM_PODMAN}" bash "${PART}" --minority isn3 --duration 1 2>&1)"; RC=$?
    ok "T-par-15 segunda injecao no mesmo no recusada" "1" "${RC}"
    contem "T-par-15 mensagem de lock" "ja ha uma injecao" "${OUT}"
    wait "${P1}" 2>/dev/null
else
    pulado "T-par-15 (flock real ausente neste host; roda na VM/WSL)"
fi

echo "== T-par-16: contadores por sentido (A1 de V-B): at 12 = ingress, at 16 = egress =="
reset_shims
SHIM_PKTS_SRC=5 SHIM_PKTS_DST=30 roda_shim --minority isn3 --duration 1
ok "T-par-16a exit code" "0" "${RC}"
contem "T-par-16a ingress soma so os filtros 'at 12' (2 pares x 5)" "ingress=10 pkt, egress=60 pkt" "${OUT}"
nao_contem "T-par-16a nao soma os dois filtros em cada sentido" "ingress=70" "${OUT}"
nao_contem "T-par-16a ambos > 0: sem aviso de um so sentido" "um so sentido" "${OUT}"
reset_shims
SHIM_PKTS_SRC=0 SHIM_PKTS_DST=9 roda_shim --minority isn3 --duration 1
ok "T-par-16b ingress=0 e egress>0 -> exit 0 (nao e falha total)" "0" "${RC}"
contem "T-par-16b contadores por sentido" "ingress=0 pkt, egress=18 pkt" "${OUT}"
contem "T-par-16b avisa descarte em um so sentido" "um so sentido" "${OUT}"
reset_shims
SHIM_PKTS_SRC=9 SHIM_PKTS_DST=0 roda_shim --minority isn3 --duration 1
contem "T-par-16c egress=0 tambem avisa" "um so sentido" "${OUT}"
contem "T-par-16c contadores por sentido" "ingress=18 pkt, egress=0 pkt" "${OUT}"
reset_shims
SHIM_PKTS=0 roda_shim --minority isn3 --duration 1
ok "T-par-16d ambos 0 continua exit 6" "6" "${RC}"

echo "== T-par-17: timestamp do log() calculado a cada chamada (A3 de V-B) =="
reset_shims
roda_shim --minority isn3 --duration 2
ok "T-par-17 exit code" "0" "${RC}"
TS_PRIMEIRO="$(echo "${OUT}" | grep -m1 '^\[' | cut -d']' -f1)"
TS_ULTIMO="$(echo "${OUT}" | grep '^\[' | tail -n 1 | cut -d']' -f1)"
if [[ -n "${TS_PRIMEIRO}" && "${TS_PRIMEIRO}" != "${TS_ULTIMO}" ]]; then
    echo "PASS: T-par-17 primeira e ultima linha de log tem timestamps distintos"; PASS=$((PASS + 1))
else
    echo "FAIL: T-par-17 timestamps iguais/ausentes (primeiro='${TS_PRIMEIRO}' ultimo='${TS_ULTIMO}')"; FAIL=$((FAIL + 1))
fi

echo "== T-par-18: guarda de netns (A2 de V-B): nunca tocar o netns do host =="
reset_shims
roda_shim --minority isn3 --duration 1
ok "T-par-18a netns distinto do PID 1: exit" "0" "${RC}"
contem "T-par-18a consultou o netns do alvo" "readlink /proc/4003/ns/net" "$(cat "${SHIM_LOG}")"
contem "T-par-18a consultou o netns do PID 1" "readlink /proc/1/ns/net" "$(cat "${SHIM_LOG}")"
reset_shims
SHIM_HOSTNET="isn3" roda_shim --minority isn3 --duration 1
ok "T-par-18b alvo com netns do host: exit 4" "4" "${RC}"
contem "T-par-18b diagnostico" "netns do host" "${OUT}"
nao_contem "T-par-18b nenhum nsenter/tc executado" "nsenter" "$(cat "${SHIM_LOG}")"
reset_shims
SHIM_HOSTNET="isn1" roda_shim --minority isn3 --duration 1
ok "T-par-18c par com netns do host: exit 4" "4" "${RC}"
nao_contem "T-par-18c nenhum nsenter/tc executado" "nsenter" "$(cat "${SHIM_LOG}")"
reset_shims
SHIM_NO_NS="isn3" roda_shim --minority isn3 --duration 1
ok "T-par-18d netns ilegivel: recusa (exit 4)" "4" "${RC}"
nao_contem "T-par-18d nenhum nsenter/tc executado" "nsenter" "$(cat "${SHIM_LOG}")"
reset_shims
SHIM_HOSTNET="isn2" roda_shim --heal
ok "T-par-18e --heal com um no em netns do host: exit 4" "4" "${RC}"
LOG="$(cat "${SHIM_LOG}")"
nao_contem "T-par-18e --heal nao toca no no com netns do host" "nsenter -t 4002" "${LOG}"
contem "T-par-18e --heal ainda limpa isn1" "nsenter -t 4001 -n tc qdisc del dev eth0 clsact" "${LOG}"
contem "T-par-18e --heal ainda limpa isn3" "nsenter -t 4003 -n tc qdisc del dev eth0 clsact" "${LOG}"
reset_shims

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL, ${SKIP} SKIP ===="
[[ "${FAIL}" -eq 0 ]]
