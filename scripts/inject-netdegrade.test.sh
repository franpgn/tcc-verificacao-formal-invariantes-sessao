#!/usr/bin/env bash
#
# inject-netdegrade.test.sh
#
# Suite de testes do inject-netdegrade.sh (B-29.2, falha F4: degradacao parcial
# de rede -- perda, atraso/jitter, duplicacao, reordenacao, corrupcao) que NAO
# exigem VM, podman, tc nem privilegio. Plano: docs/plano-fechamento-etapa-formal-
# 2026-10-15.md, secao T-C1.
#
# Os testes com SHIMS (T-nd-7 em diante) colocam scripts falsos de podman, sudo,
# nsenter, tc, ping e flock no inicio do PATH. Os shims so registram as chamadas
# em $SHIM_LOG e simulam, por PID, o estado de uma qdisc raiz (prio do injetor ou
# netem "de F3"), de um netem na banda 3, dos filtros u32 e de um clsact (F2).
# Assim a LOGICA do injetor (ordem dos comandos, direcao, limpeza, codigos de
# saida, sinais, exclusao mutua) e verificada sem kernel. Os shims NAO provam:
#   - que o kernel da VM tem sch_prio/sch_netem/cls_u32 nem aceita os parametros
#     (gemodel, reorder, corrupt): isso e o gate L-0 da T-C2;
#   - que o filtro u32 por IP de destino casa o trafego JGroups e deixa Hot Rod e
#     SSH intactos, nem que o netem tem o efeito configurado (taxa real no fio,
#     retransmissoes do TCP, comportamento do JGroups);
#   - que o 'ping' real, o 'tc -s' real e o 'nsenter' real tem o formato de saida
#     que os shims imitam (o parser de contadores e de perda e escrito contra o
#     formato documentado de iproute2/iputils, nao contra a VM).
#
# Auto-contido (sem bats). Rodar:  bash scripts/inject-netdegrade.test.sh
# Exit 0 se todos passarem; 1 caso contrario.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ND="${ND_SCRIPT:-${DIR}/inject-netdegrade.sh}"

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

# conta <padrao-fixo> <texto>: numero de linhas que contem o padrao.
conta() { echo "$2" | grep -cF -e "$1" || true; }

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

# nsenter -t PID -n CMD...: registra a linha completa e marca que o comando
# filho foi chamado via nsenter (o shim de tc registra BARE_TC se nao foi).
cat >"${SHIMDIR}/nsenter" <<'EOF'
#!/usr/bin/env bash
echo "nsenter $*" >>"${SHIM_LOG}"
pid="$2"; shift 3
export SHIM_PID="${pid}" SHIM_VIA_NSENTER=1
exec "$@"
EOF

# ping: devolve o resumo no formato do iputils com SHIM_PING_LOSS (%).
cat >"${SHIMDIR}/ping" <<'EOF'
#!/usr/bin/env bash
echo "ping $*" >>"${SHIM_LOG}"
alvo="${*: -1}"
echo "--- ${alvo} ping statistics ---"
echo "200 packets transmitted, 160 received, ${SHIM_PING_LOSS:-0}% packet loss, time 39800ms"
exit 0
EOF

# tc: estado por PID. root.<pid> = 'prio' (do injetor) ou 'f3' (netem raiz de
# F3); netem.<pid> = argumentos do netem da banda 3; filters.<pid>; clsact.<pid>
# (F2). Falhas: SHIM_FAIL_PRIO, SHIM_FAIL_NETEM, SHIM_NETEM_FAIL_MATCH,
# SHIM_TC_FAIL_MATCH (filter add). Contadores: SHIM_SENT / SHIM_DROPPED.
cat >"${SHIMDIR}/tc" <<'EOF'
#!/usr/bin/env bash
pid="${SHIM_PID:-}"
[[ -n "${SHIM_VIA_NSENTER:-}" ]] || echo "BARE_TC $*" >>"${SHIM_LOG}"
echo "tc $*" >>"${SHIM_LOG}"
S="${SHIM_STATE}"
root="${S}/root.${pid}"; nm="${S}/netem.${pid}"; fl="${S}/filters.${pid}"; cs="${S}/clsact.${pid}"
args="$*"
mostra() { # mostra <stats|plain>
    local sent="${SHIM_SENT:-1000}" drop="${SHIM_DROPPED:-50}"
    if [[ -f "${root}" ]]; then
        if [[ "$(cat "${root}")" == "prio" ]]; then
            echo "qdisc prio 1: root refcnt 2 bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
            [[ "$1" == stats ]] && { echo " Sent 999999 bytes 8888 pkt (dropped 0, overlimits 0 requeues 0)"; echo " backlog 0b 0p requeues 0"; }
            if [[ -f "${nm}" ]]; then
                echo "qdisc netem 30: parent 1:3 limit 1000 $(cat "${nm}")"
                [[ "$1" == stats ]] && { echo " Sent $((sent * 120)) bytes ${sent} pkt (dropped ${drop}, overlimits 0 requeues 0)"; echo " backlog 0b 0p requeues 0"; }
            fi
        else
            echo "qdisc netem 8001: root refcnt 2 limit 1000 delay 20ms 13ms"
        fi
    else
        echo "qdisc noqueue 0: root refcnt 2"
    fi
    [[ -f "${cs}" ]] && echo "qdisc clsact ffff: parent ffff:fff1"
    return 0
}
case "${args}" in
    "qdisc show dev "*)      mostra plain ;;
    "-s qdisc show dev "*)   mostra stats ;;
    "qdisc add dev "*" root handle 1: prio bands 3 priomap "*)
        [[ -z "${SHIM_FAIL_PRIO:-}" ]] || { echo "Error: Specified qdisc kind is unknown." >&2; exit 2; }
        [[ ! -f "${root}" ]] || { echo "RTNETLINK answers: File exists" >&2; exit 2; }
        echo prio >"${root}" ;;
    "qdisc add dev "*" parent 1:3 handle 30: netem "*)
        [[ -z "${SHIM_FAIL_NETEM:-}" ]] || { echo "Error: Specified qdisc kind is unknown." >&2; exit 2; }
        if [[ -n "${SHIM_NETEM_FAIL_MATCH:-}" && "${args}" == *"${SHIM_NETEM_FAIL_MATCH}"* ]]; then
            echo "Illegal \"loss\"" >&2; exit 2
        fi
        [[ -f "${root}" && "$(cat "${root}")" == "prio" ]] || { echo "Error: Parent Qdisc doesn't exists." >&2; exit 2; }
        echo "${args#*netem }" >"${nm}" ;;
    "filter add dev "*" parent 1: protocol ip prio 1 u32 match ip dst "*" flowid 1:3")
        if [[ -n "${SHIM_TC_FAIL_MATCH:-}" && "${args}" == *"${SHIM_TC_FAIL_MATCH}"* ]]; then
            echo "RTNETLINK answers: Invalid argument" >&2; exit 2
        fi
        [[ -f "${root}" && "$(cat "${root}")" == "prio" ]] || { echo "Error: Parent Qdisc doesn't exists." >&2; exit 2; }
        echo "${args}" >>"${fl}" ;;
    "qdisc del dev "*" root")
        [[ -f "${root}" ]] || { echo "Error: Cannot delete qdisc with handle of zero." >&2; exit 2; }
        rm -f "${root}" "${nm}" "${fl}" ;;
    *)
        echo "UNEXPECTED_TC $*" >>"${SHIM_LOG}"
        echo "shim tc: comando inesperado: $*" >&2; exit 2 ;;
esac
exit 0
EOF

# flock: sempre concede o lock (a exclusao real e o T-nd-17).
cat >"${SHIMDIR}/flock" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${SHIMDIR}"/*

PATH_SHIMS="${SHIMDIR}:${PATH_SEM_PODMAN}"

reset_shims() {
    : >"${SHIM_LOG}"
    rm -f "${SHIM_STATE}"/* "${TMP}/marker" "${TMP}/saida" "${LOCK_DIR}"/* 2>/dev/null || true
    unset SHIM_MISSING SHIM_STATUS SHIM_FAIL_PRIO SHIM_FAIL_NETEM SHIM_NETEM_FAIL_MATCH \
          SHIM_TC_FAIL_MATCH SHIM_SENT SHIM_DROPPED
    export SHIM_PING_LOSS=20
}

# residuo: quantos nos ainda tem a qdisc raiz do injetor (root.<pid> = prio).
residuo() {
    local n=0 f
    for f in "${SHIM_STATE}"/root.*; do
        [[ -f "${f}" && "$(cat "${f}")" == "prio" ]] && n=$((n + 1))
    done
    echo "${n}"
}

# roda_shim <args...>: executa o injetor com os shims; saida em $OUT, rc em $RC.
roda_shim() {
    OUT="$(PATH="${PATH_SHIMS}" bash "${ND}" "$@" 2>&1)"; RC=$?
}

# dry-run sem podman: dry <args...> -> $OUT, $RC
dry() {
    OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${ND}" --dry-run "$@" 2>&1)"; RC=$?
}

# ================================================================ sem shims
echo "== T-nd-0: o script existe e a sintaxe e valida =="
if [[ ! -f "${ND}" ]]; then
    echo "FAIL: T-nd-0 scripts/inject-netdegrade.sh nao existe"; FAIL=$((FAIL + 1))
    echo
    echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL, ${SKIP} SKIP ===="
    exit 1
fi
bash -n "${ND}" 2>/dev/null; RC=$?
ok "T-nd-0 bash -n limpo" "0" "${RC}"
ok "T-nd-0 nenhum 'tc' fora de nsenter no script (linha iniciada por tc/sudo tc)" "0" \
    "$(grep -cE '^[[:space:]]*(sudo[[:space:]]+)?tc[[:space:]]' "${ND}" || true)"
ok "T-nd-0 o script nao usa 'podman exec'" "0" "$(grep -E '^[^#]*podman exec' "${ND}" | wc -l | tr -d ' ')"

echo "== T-nd-1: --help retorna 0 e documenta as flags e os exit codes =="
OUT="$(bash "${ND}" --help)"; RC=$?
ok "T-nd-1 exit code" "0" "${RC}"
for f in --target --nodes --peers --direction --loss --loss-corr --loss-in --loss-model --burst \
         --delay --jitter --duplicate --reorder --corrupt --severity --duration --marker \
         --iface --ping-count --heal --dry-run --help; do
    contem "T-nd-1 documenta ${f}" "${f}" "${OUT}"
done
for c in "0 " "1 " "2 " "3 " "4 " "5 " "6 " "7 "; do
    contem "T-nd-1 documenta exit ${c}" "  ${c}" "${OUT}"
done
contem "T-nd-1 documenta PODMAN" "PODMAN" "${OUT}"
contem "T-nd-1 documenta raiz ocupada" "raiz" "${OUT}"

echo "== T-nd-2: --dry-run (defaults, direcao out) sem podman =="
dry --target isn3 --loss 20
ok "T-nd-2 exit code" "0" "${RC}"
contem "T-nd-2 resolve pid" "podman inspect -f '{{.State.Pid}}' isn3" "${OUT}"
contem "T-nd-2 resolve IP dos pares" "IPAddress" "${OUT}"
contem "T-nd-2 pre-limpeza condicionada a qdisc propria" "tc qdisc show dev eth0" "${OUT}"
contem "T-nd-2 add prio raiz" "sudo nsenter -t <pid> -n tc qdisc add dev eth0 root handle 1: prio bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0" "${OUT}"
contem "T-nd-2 netem na banda 3" "sudo nsenter -t <pid> -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20%" "${OUT}"
contem "T-nd-2 filtro u32 por par" "sudo nsenter -t <pid> -n tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst <ip-par>/32 flowid 1:3" "${OUT}"
contem "T-nd-2 del raiz" "sudo nsenter -t <pid> -n tc qdisc del dev eth0 root" "${OUT}"
contem "T-nd-2 contadores" "tc -s qdisc show dev eth0" "${OUT}"
contem "T-nd-2 duracao padrao 120" "sleep 120" "${OUT}"
contem "T-nd-2 menciona lock do F2" "inject-partition-isn3.lock" "${OUT}"
ok "T-nd-2 nao usa podman exec" "0" "$(conta 'podman exec' "${OUT}")"
ok "T-nd-2 dois pares (isn1, isn2) -> 2 filtros" "2" "$(conta 'tc filter add dev eth0 parent 1:' "${OUT}")"
ok "T-nd-2 direcao out -> 1 netem" "1" "$(conta 'netem loss 20%' "${OUT}")"
contem "T-nd-2 nada executado" "dry-run: nada executado" "${OUT}"
ok "T-nd-2 toda linha com 'tc ' do plano passa por nsenter" "0" \
    "$(echo "${OUT}" | grep -E ' tc (qdisc|filter|-s)' | grep -vc 'nsenter -t <pid> -n tc' || true)"

echo "== T-nd-3: direcao in (entrada = egress dos pares) e both =="
dry --target isn3 --loss 20 --direction in
ok "T-nd-3a exit code" "0" "${RC}"
ok "T-nd-3a in -> um netem por par (2)" "2" "$(conta 'netem loss 20%' "${OUT}")"
ok "T-nd-3a in -> filtros apontam para o IP do alvo" "2" "$(conta 'match ip dst <ip-alvo>/32' "${OUT}")"
contem "T-nd-3a netem no no isn1" "# no isn1" "${OUT}"
contem "T-nd-3a netem no no isn2" "# no isn2" "${OUT}"
nao_contem "T-nd-3a o alvo nao recebe netem em 'in'" "netem loss 20%   # no isn3" "${OUT}"
dry --target isn3 --loss 20 --direction both
ok "T-nd-3b exit code" "0" "${RC}"
ok "T-nd-3b both -> 3 netem (alvo + 2 pares)" "3" "$(conta 'netem loss 20%' "${OUT}")"
dry --target isn3 --nodes isn1,isn2,isn3,isn4,isn5 --loss 20 --direction both
ok "T-nd-3c 5 nos both -> 5 netem" "5" "$(conta 'netem loss 20%' "${OUT}")"
dry --target isn3 --nodes isn1,isn2,isn3,isn4,isn5 --peers isn1,isn5 --loss 20
ok "T-nd-3d --peers restringe os filtros (2)" "2" "$(conta 'tc filter add dev eth0 parent 1:' "${OUT}")"
nao_contem "T-nd-3d par fora de --peers nao aparece" "par isn2" "${OUT}"

echo "== T-nd-4: perda assimetrica (--loss-in) =="
dry --target isn3 --direction both --loss 20 --loss-in 50
ok "T-nd-4a exit code" "0" "${RC}"
ok "T-nd-4a saida do alvo com 20%" "1" "$(conta 'netem loss 20%   # no isn3' "${OUT}")"
ok "T-nd-4a entrada (pares) com 50%" "2" "$(conta 'netem loss 50%   # no isn' "${OUT}")"
dry --target isn3 --direction in --loss-in 50
ok "T-nd-4b so entrada com --loss-in" "0" "${RC}"
ok "T-nd-4b 2 netem de 50%" "2" "$(conta 'netem loss 50%' "${OUT}")"
dry --target isn3 --direction both --loss 20 --loss-in 0
ok "T-nd-4c --loss-in 0 em both: so a saida degrada" "0" "${RC}"
ok "T-nd-4c 1 netem" "1" "$(conta 'tc qdisc add dev eth0 parent 1:3' "${OUT}")"

echo "== T-nd-5: parametros do netem =="
dry --target isn3 --delay 100ms --jitter 50ms
ok "T-nd-5a exit" "0" "${RC}"
contem "T-nd-5a atraso+jitter normal" "netem delay 100ms 50ms distribution normal" "${OUT}"
dry --target isn3 --delay 20
contem "T-nd-5b delay sem unidade vira ms" "netem delay 20ms" "${OUT}"
dry --target isn3 --loss 20 --loss-corr 50
contem "T-nd-5c perda correlacionada" "netem loss 20% 50%" "${OUT}"
dry --target isn3 --duplicate 5
contem "T-nd-5d duplicacao" "netem duplicate 5%" "${OUT}"
dry --target isn3 --delay 20ms --reorder 25,50
contem "T-nd-5e reordenacao com correlacao" "reorder 25% 50%" "${OUT}"
dry --target isn3 --delay 20ms --reorder 25
contem "T-nd-5f reordenacao sem correlacao" "reorder 25%" "${OUT}"
dry --target isn3 --corrupt 1
contem "T-nd-5g corrupcao" "netem corrupt 1%" "${OUT}"
dry --target isn3 --loss 5 --delay 100ms --jitter 50ms --duplicate 1
contem "T-nd-5h combinacao 'rede ruim' (M)" "netem loss 5% delay 100ms 50ms distribution normal duplicate 1%" "${OUT}"
dry --target isn3 --loss 20 --loss-model gemodel --burst 4
ok "T-nd-5i gemodel exit" "0" "${RC}"
contem "T-nd-5i gemodel p=6.25% r=25% (media 20%)" "netem loss gemodel 6.25% 25% 100% 0%" "${OUT}"
dry --target isn3 --loss 20 --loss-model gemodel --burst 2
contem "T-nd-5j gemodel burst 2 -> r=50%, p=12.5%" "netem loss gemodel 12.5% 50% 100% 0%" "${OUT}"
dry --target isn3 --loss 0.5
contem "T-nd-5k perda fracionaria" "netem loss 0.5%" "${OUT}"
dry --target isn3 --loss 20 --iface ens4
contem "T-nd-5l --iface" "tc qdisc add dev ens4 root handle 1: prio" "${OUT}"
dry --target isn3 --loss 20 --duration 200
contem "T-nd-5m --duration" "sleep 200" "${OUT}"
dry --target isn3 --loss 20 --marker /tmp/m.txt
contem "T-nd-5n marcador citado (fault=F4)" "fault=F4" "${OUT}"
contem "T-nd-5n marcador citado (arquivo)" "/tmp/m.txt" "${OUT}"

echo "== T-nd-6: severidades S1..S5 =="
i=0
for par in "S1 1%" "S2 5%" "S3 20%" "S4 50%" "S5 99.9%"; do
    sev="${par%% *}"; perda="${par##* }"
    dry --target isn3 --severity "${sev}" --direction both
    ok "T-nd-6 ${sev} exit" "0" "${RC}"
    contem "T-nd-6 ${sev} -> loss ${perda}" "netem loss ${perda}" "${OUT}"
done
contem "T-nd-6 S5 avisa equivalencia com particao" "equivale a particao" "${OUT}"
dry --target isn3 --loss 99
contem "T-nd-6b loss >= 99 avisa" "inject-partition.sh" "${OUT}"
dry --target isn3 --loss 20
nao_contem "T-nd-6c 20% nao avisa equivalencia" "equivale a particao" "${OUT}"
dry --target isn3 --severity S3 --loss 20; ok "T-nd-6d --severity junto com --loss" "2" "${RC}"
dry --target isn3 --severity S6;            ok "T-nd-6e severidade desconhecida" "2" "${RC}"
dry --target isn3 --severity S2 --loss-model gemodel
ok "T-nd-6f severidade com gemodel aceita" "0" "${RC}"
contem "T-nd-6f gemodel com media 5%" "netem loss gemodel" "${OUT}"

echo "== T-nd-7: avisos de duracao =="
dry --target isn3 --loss 20 --duration 20
ok "T-nd-7a exit" "0" "${RC}"
contem "T-nd-7a aviso de duracao curta" "atraso severo" "${OUT}"
contem "T-nd-7a plano segue" "sleep 20" "${OUT}"
dry --target isn3 --loss 20 --duration 60
nao_contem "T-nd-7b 60 s sem aviso" "atraso severo" "${OUT}"

echo "== T-nd-8: argumentos invalidos -> exit 2 =="
inv() { # inv <descricao> <args...>
    local d="$1"; shift
    PATH="${PATH_SEM_PODMAN}" bash "${ND}" --dry-run "$@" >/dev/null 2>&1; RC=$?
    ok "T-nd-8 ${d}" "2" "${RC}"
}
inv "sem --target"                         --loss 20
inv "nenhum efeito (so --target)"          --target isn3
inv "loss 101"                             --target isn3 --loss 101
inv "loss negativa"                        --target isn3 --loss -1
inv "loss nao numerica"                    --target isn3 --loss abc
inv "loss-in 101"                          --target isn3 --direction in --loss-in 101
inv "loss-corr 101"                        --target isn3 --loss 20 --loss-corr 101
inv "loss-corr sem loss"                   --target isn3 --delay 20 --loss-corr 10
inv "loss-corr com gemodel"                --target isn3 --loss 20 --loss-model gemodel --loss-corr 10
inv "--loss-model invalido"                --target isn3 --loss 20 --loss-model foo
inv "gemodel sem perda"                    --target isn3 --loss-model gemodel --delay 20
inv "gemodel com p > 100% (loss 90 burst 4)" --target isn3 --loss 90 --loss-model gemodel --burst 4
inv "gemodel loss 100"                     --target isn3 --loss 100 --loss-model gemodel
inv "burst 0"                              --target isn3 --loss 20 --loss-model gemodel --burst 0
inv "burst nao numerico"                   --target isn3 --loss 20 --loss-model gemodel --burst x
inv "--reorder sem --delay"                --target isn3 --reorder 25
inv "--reorder 101"                        --target isn3 --delay 20 --reorder 101
inv "--reorder corr 101"                   --target isn3 --delay 20 --reorder 25,101
inv "--jitter sem --delay"                 --target isn3 --loss 5 --jitter 10
inv "delay com unidade invalida"           --target isn3 --delay 20xs
inv "delay negativo"                       --target isn3 --delay -5
inv "duplicate 101"                        --target isn3 --duplicate 101
inv "corrupt 101"                          --target isn3 --corrupt 101
inv "direcao invalida"                     --target isn3 --loss 20 --direction up
inv "--loss-in com direcao out"            --target isn3 --loss 20 --loss-in 50 --direction out
inv "alvo fora de --nodes"                 --target isn9 --loss 20
inv "--peers contem o alvo"                --target isn3 --loss 20 --peers isn3,isn1
inv "--peers fora de --nodes"              --target isn3 --loss 20 --peers isn9
inv "--peers vazio"                        --target isn3 --loss 20 --peers ""
inv "--nodes com 1 no"                     --target isn3 --loss 20 --nodes isn3
inv "--duration 0"                         --target isn3 --loss 20 --duration 0
inv "--duration nao inteiro"               --target isn3 --loss 20 --duration abc
inv "--ping-count nao inteiro"             --target isn3 --loss 20 --ping-count x
inv "flag desconhecida"                    --foo
inv "--target sem valor"                   --target
inv "--loss sem valor"                     --target isn3 --loss
inv "nome de no com caracteres invalidos"  --target 'isn3;rm' --loss 20
inv "--iface invalida"                     --target isn3 --loss 20 --iface 'eth0;x'
bash "${ND}" --target isn3 --loss 101 >/dev/null 2>&1; RC=$?
ok "T-nd-8 argumento invalido sem --dry-run tambem exit 2" "2" "${RC}"

# ================================================================ com shims
echo "== T-nd-9: execucao completa com shims (direcao out, --duration 1) =="
reset_shims
roda_shim --target isn3 --loss 20 --duration 1 --marker "${TMP}/marker"
ok "T-nd-9 exit code" "0" "${RC}"
ordem "T-nd-9 sequencia resolve > inspeciona raiz > sonda > del > apply > ping > contadores > del" "${SHIM_LOG}" \
    "podman inspect -f {{.State.Pid}} isn3" \
    "nsenter -t 4003 -n tc qdisc show dev eth0" \
    "tc qdisc add dev eth0 root handle 1: prio bands 3 priomap" \
    "tc qdisc add dev eth0 parent 1:3 handle 30: netem loss" \
    "tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32 flowid 1:3" \
    "tc qdisc del dev eth0 root" \
    "tc qdisc add dev eth0 root handle 1: prio bands 3 priomap" \
    "tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20%" \
    "tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 10.89.0.1/32 flowid 1:3" \
    "tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 10.89.0.2/32 flowid 1:3" \
    "ping" \
    "tc -s qdisc show dev eth0" \
    "tc qdisc del dev eth0 root"
LOG="$(cat "${SHIM_LOG}")"
nao_contem "T-nd-9 nenhum tc chamado fora de nsenter" "BARE_TC" "${LOG}"
nao_contem "T-nd-9 nenhum tc inesperado" "UNEXPECTED_TC" "${LOG}"
ok "T-nd-9 cada 'tc' do log foi precedido de um nsenter com tc" \
    "$(echo "${LOG}" | grep -c '^tc ')" "$(echo "${LOG}" | grep -c '^nsenter -t [0-9]* -n tc ')"
contem "T-nd-9 resolve IP de isn1" "podman inspect -f {{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}} isn1" "${LOG}"
nao_contem "T-nd-9 so toca no PID de isn3 (nao toca 4001)" "nsenter -t 4001" "${LOG}"
nao_contem "T-nd-9 nao toca no PID de isn2 (4002)" "nsenter -t 4002" "${LOG}"
ok "T-nd-9 sem residuo da qdisc do injetor" "0" "$(residuo)"
ok "T-nd-9 marcador com 2 linhas" "2" "$(wc -l <"${TMP}/marker" | tr -d ' ')"
contem "T-nd-9 marcador start" "fault=F4 node=isn3 event=start t_epoch_ns=" "$(cat "${TMP}/marker")"
contem "T-nd-9 marcador end" "fault=F4 node=isn3 event=end t_epoch_ns=" "$(cat "${TMP}/marker")"
ok "T-nd-9 formato do marcador compativel com o de B-29" "2" \
    "$(grep -cE '^fault=F4 node=isn3 event=(start|end) t_epoch_ns=[0-9]+$' "${TMP}/marker")"
contem "T-nd-9 imprime contadores" "pkt" "${OUT}"
contem "T-nd-9 loga a perda observada no ping" "perda observada" "${OUT}"
contem "T-nd-9 mensagem final de sucesso" "OK: F4" "${OUT}"
ok "T-nd-9 ping: 200 pacotes por par, 2 pares" "2" "$(grep -c '^ping .*-c 200' "${SHIM_LOG}")"

echo "== T-nd-10: --direction in/both toca os PIDs dos PARES =="
reset_shims
roda_shim --target isn3 --loss 20 --direction in --duration 1
ok "T-nd-10a exit (in)" "0" "${RC}"
LOG="$(cat "${SHIM_LOG}")"
contem "T-nd-10a netem em isn1 (4001)" "nsenter -t 4001 -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20%" "${LOG}"
contem "T-nd-10a netem em isn2 (4002)" "nsenter -t 4002 -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20%" "${LOG}"
contem "T-nd-10a filtro no par aponta para o IP do ALVO" "nsenter -t 4001 -n tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 10.89.0.3/32 flowid 1:3" "${LOG}"
nao_contem "T-nd-10a alvo (4003) nao recebe netem em 'in'" "nsenter -t 4003 -n tc qdisc add" "${LOG}"
ok "T-nd-10a sem residuo" "0" "$(residuo)"
contem "T-nd-10a o ping de verificacao sai do alvo" "nsenter -t 4003 -n ping" "${LOG}"
reset_shims
export SHIM_PING_LOSS=36    # 1-(0.8*0.8): requisicao e resposta, 20% em cada sentido
roda_shim --target isn3 --loss 20 --direction both --duration 1
ok "T-nd-10b exit (both)" "0" "${RC}"
LOG="$(cat "${SHIM_LOG}")"
for pidn in 4001 4002 4003; do
    contem "T-nd-10b netem em ${pidn}" "nsenter -t ${pidn} -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20%" "${LOG}"
done
ok "T-nd-10b sem residuo em nenhum no" "0" "$(residuo)"
ok "T-nd-10b nenhum tc fora de nsenter" "0" "$(grep -c BARE_TC "${SHIM_LOG}")"
reset_shims
export SHIM_PING_LOSS=20
roda_shim --target isn3 --direction both --loss 20 --loss-in 50 --duration 1 --ping-count 0
ok "T-nd-10c assimetrica exit" "0" "${RC}"
LOG="$(cat "${SHIM_LOG}")"
contem "T-nd-10c saida do alvo: 20%" "nsenter -t 4003 -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20%" "${LOG}"
contem "T-nd-10c entrada via isn1: 50%" "nsenter -t 4001 -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 50%" "${LOG}"
contem "T-nd-10c entrada via isn2: 50%" "nsenter -t 4002 -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 50%" "${LOG}"
ok "T-nd-10c dois valores distintos de perda" "2" \
    "$(grep -o 'netem loss [0-9]*%' "${SHIM_LOG}" | grep -E '20%|50%' | sort -u | wc -l | tr -d ' ')"
reset_shims
roda_shim --target isn3 --direction both --loss 20 --loss-in 0 --duration 1 --ping-count 0
ok "T-nd-10d --loss-in 0 em both: so a saida (pares intocados)" "0" "${RC}"
nao_contem "T-nd-10d par isn1 nao tocado" "nsenter -t 4001" "$(cat "${SHIM_LOG}")"

echo "== T-nd-11: parametros chegam intactos ao tc (todas as impairments) =="
reset_shims
roda_shim --target isn3 --loss 5 --loss-corr 10 --delay 100ms --jitter 50ms --duplicate 1 \
    --reorder 25,50 --corrupt 1 --duration 1 --ping-count 0
ok "T-nd-11 exit" "0" "${RC}"
contem "T-nd-11 netem completo" "netem loss 5% 10% delay 100ms 50ms distribution normal duplicate 1% reorder 25% 50% corrupt 1%" "$(cat "${SHIM_LOG}")"
reset_shims
roda_shim --target isn3 --loss 20 --loss-model gemodel --burst 4 --duration 1 --ping-count 0
ok "T-nd-11b gemodel exit" "0" "${RC}"
contem "T-nd-11b gemodel chega ao tc" "netem loss gemodel 6.25% 25% 100% 0%" "$(cat "${SHIM_LOG}")"
reset_shims
roda_shim --target isn3 --delay 50ms --jitter 10ms --duration 1
ok "T-nd-11c so atraso: exit" "0" "${RC}"
contem "T-nd-11c ping-check nao se aplica sem perda" "nao se aplica" "${OUT}"
reset_shims
roda_shim --target isn3 --nodes isn1,isn2,isn3,isn4,isn5 --peers isn1,isn5 --loss 20 --duration 1
ok "T-nd-11d --peers com 5 nos: exit" "0" "${RC}"
contem "T-nd-11d filtro para isn5" "match ip dst 10.89.0.5/32" "$(cat "${SHIM_LOG}")"
nao_contem "T-nd-11d nenhum filtro para isn2" "match ip dst 10.89.0.2/32" "$(cat "${SHIM_LOG}")"

echo "== T-nd-12: kernel rejeita (sonda ou aplicacao) -> exit 5 e limpeza =="
reset_shims
export SHIM_TC_FAIL_MATCH="192.0.2.1"
roda_shim --target isn3 --loss 20 --duration 1 --marker "${TMP}/marker"
ok "T-nd-12a filtro u32 rejeitado na sonda: exit" "5" "${RC}"
contem "T-nd-12a diagnostico" "sonda" "${OUT}"
nao_contem "T-nd-12a nenhum filtro de par aplicado" "10.89.0.1/32" "$(cat "${SHIM_LOG}")"
ok "T-nd-12a sem residuo" "0" "$(residuo)"
ok "T-nd-12a sem marcador (a falha nem comecou)" "0" "$([[ -f "${TMP}/marker" ]] && echo 1 || echo 0)"
reset_shims
export SHIM_FAIL_PRIO=1
roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-12b prio rejeitado (sch_prio ausente): exit" "5" "${RC}"
reset_shims
export SHIM_FAIL_NETEM=1
roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-12c netem rejeitado (sch_netem ausente): exit" "5" "${RC}"
ok "T-nd-12c sem residuo (prio da sonda removido)" "0" "$(residuo)"
reset_shims
export SHIM_NETEM_FAIL_MATCH="gemodel"
roda_shim --target isn3 --loss 20 --loss-model gemodel --duration 1
ok "T-nd-12d a sonda usa os parametros reais (gemodel rejeitado): exit" "5" "${RC}"
contem "T-nd-12d diagnostico cita o gate" "L-0" "${OUT}"
reset_shims
export SHIM_TC_FAIL_MATCH="10.89.0.2"    # falha so na aplicacao real, a sonda passa
roda_shim --target isn3 --loss 20 --duration 1 --marker "${TMP}/marker"
ok "T-nd-12e falha na aplicacao: exit" "5" "${RC}"
ok "T-nd-12e regra parcial removida pelo trap" "0" "$(residuo)"
reset_shims
export SHIM_TC_FAIL_MATCH="10.89.0.3"    # direcao in: o filtro nos pares aponta ao alvo
roda_shim --target isn3 --direction in --loss 20 --duration 1
ok "T-nd-12f falha em NO PAR (direcao in): exit" "5" "${RC}"
ok "T-nd-12f nada residual em nenhum no" "0" "$(residuo)"

echo "== T-nd-13: SIGTERM/SIGHUP durante a janela (SIGINT nao e testavel: o bash ignora INT em jobs assincronos nao interativos) -> limpeza antes de sair =="
reset_shims
PATH="${PATH_SHIMS}" bash "${ND}" --target isn3 --direction both --loss 20 --duration 30 \
    --ping-count 0 --marker "${TMP}/marker" >"${TMP}/saida" 2>&1 &
BGPID=$!
ESPERA=0
until grep -q 'event=start' "${TMP}/marker" 2>/dev/null; do
    sleep 0.2; ESPERA=$((ESPERA + 1))
    [[ "${ESPERA}" -lt 100 ]] || break
done
sleep 0.5
ok "T-nd-13a chegou a janela ativa (3 nos com a regra)" "3" "$(residuo)"
LINHAS_ANTES="$(wc -l <"${SHIM_LOG}" | tr -d ' ')"
kill -TERM "${BGPID}" 2>/dev/null
T0=$SECONDS
wait "${BGPID}" 2>/dev/null; RC=$?
ok "T-nd-13a saiu rapido (nao esperou os 30 s)" "1" "$(( (SECONDS - T0) < 10 ? 1 : 0 ))"
ok "T-nd-13a exit code 143 (128+SIGTERM)" "143" "${RC}"
tail -n +"$((LINHAS_ANTES + 1))" "${SHIM_LOG}" >"${TMP}/depois.log"
ok "T-nd-13a 3 'qdisc del root' apos o sinal (alvo + 2 pares)" "3" "$(grep -c '^tc qdisc del dev eth0 root' "${TMP}/depois.log")"
ok "T-nd-13a sem residuo" "0" "$(residuo)"
contem "T-nd-13a marcador end gravado mesmo com sinal" "event=end" "$(cat "${TMP}/marker")"
ok "T-nd-13a marcador end gravado uma vez so" "1" "$(grep -c 'event=end' "${TMP}/marker")"
reset_shims
PATH="${PATH_SHIMS}" bash "${ND}" --target isn3 --loss 20 --duration 30 --ping-count 0 \
    >"${TMP}/saida" 2>&1 &
BGPID=$!
ESPERA=0
until grep -qF -e "tc -s qdisc show dev eth0" "${SHIM_LOG}" 2>/dev/null; do
    sleep 0.2; ESPERA=$((ESPERA + 1))
    [[ "${ESPERA}" -lt 100 ]] || break
done
kill -HUP "${BGPID}" 2>/dev/null
wait "${BGPID}" 2>/dev/null; RC=$?
ok "T-nd-13b SIGHUP: exit 129" "129" "${RC}"
ok "T-nd-13b SIGHUP: sem residuo" "0" "$(residuo)"
# SIGTERM durante o ping de verificacao (antes da janela): o trap tambem limpa.
reset_shims
cat >"${TMP}/ping-lento" <<'EOF'
#!/usr/bin/env bash
echo "ping $*" >>"${SHIM_LOG}"
sleep 20
EOF
SHIMDIR_LENTO="${TMP}/shims-lento"; mkdir -p "${SHIMDIR_LENTO}"
cp "${SHIMDIR}"/* "${SHIMDIR_LENTO}/"; cp "${TMP}/ping-lento" "${SHIMDIR_LENTO}/ping"; chmod +x "${SHIMDIR_LENTO}"/*
PATH="${SHIMDIR_LENTO}:${PATH_SEM_PODMAN}" bash "${ND}" --target isn3 --loss 20 --duration 30 \
    >"${TMP}/saida" 2>&1 &
BGPID=$!
ESPERA=0
until grep -q '^ping ' "${SHIM_LOG}" 2>/dev/null; do
    sleep 0.2; ESPERA=$((ESPERA + 1))
    [[ "${ESPERA}" -lt 100 ]] || break
done
kill -TERM "${BGPID}" 2>/dev/null
T0=$SECONDS
wait "${BGPID}" 2>/dev/null; RC=$?
ok "T-nd-13c SIGTERM durante o ping: exit 143" "143" "${RC}"
ok "T-nd-13c SIGTERM durante o ping: saiu sem esperar o ping" "1" "$(( (SECONDS - T0) < 10 ? 1 : 0 ))"
ok "T-nd-13c SIGTERM durante o ping: sem residuo" "0" "$(residuo)"

echo "== T-nd-14: --heal =="
reset_shims
echo prio >"${SHIM_STATE}/root.4003"; echo "loss 20%" >"${SHIM_STATE}/netem.4003"   # sobra de SIGKILL
echo prio >"${SHIM_STATE}/root.4001"                                              # sobra de direcao in
echo f3   >"${SHIM_STATE}/root.4002"                                              # netem raiz de F3: NAO e nosso
roda_shim --heal --target isn3
ok "T-nd-14a exit" "0" "${RC}"
LOG="$(cat "${SHIM_LOG}")"
contem "T-nd-14a remove a raiz em isn3" "nsenter -t 4003 -n tc qdisc del dev eth0 root" "${LOG}"
contem "T-nd-14a remove a raiz tambem nos pares (sobra de 'in')" "nsenter -t 4001 -n tc qdisc del dev eth0 root" "${LOG}"
nao_contem "T-nd-14a NAO apaga a raiz de F3 em isn2" "nsenter -t 4002 -n tc qdisc del" "${LOG}"
ok "T-nd-14a raiz de F3 intacta" "f3" "$(cat "${SHIM_STATE}/root.4002")"
nao_contem "T-nd-14a nao adiciona qdisc" "qdisc add" "${LOG}"
nao_contem "T-nd-14a nao adiciona filtro" "filter add" "${LOG}"
ok "T-nd-14a sem residuo do injetor" "0" "$(residuo)"
reset_shims
echo prio >"${SHIM_STATE}/root.4003"
roda_shim --heal
ok "T-nd-14b --heal sem --target: exit 0" "0" "${RC}"
ok "T-nd-14b inspeciona os 3 nos" "3" "$(grep -c '^tc qdisc show dev eth0' "${SHIM_LOG}")"
ok "T-nd-14b so 1 'del' (so isn3 tinha sobra)" "1" "$(grep -c '^tc qdisc del dev eth0 root' "${SHIM_LOG}")"
reset_shims
SHIM_MISSING="isn2" roda_shim --heal
ok "T-nd-14c --heal tolera no ausente" "0" "${RC}"
reset_shims
roda_shim --heal
ok "T-nd-14d --heal idempotente sem nada a limpar" "0" "${RC}"
ok "T-nd-14d nenhum 'del' sem alvo" "0" "$(grep -c '^tc qdisc del' "${SHIM_LOG}")"
reset_shims
dry --heal
ok "T-nd-14e --heal --dry-run exit" "0" "${RC}"
contem "T-nd-14e plano de heal" "tc qdisc del dev eth0 root" "${OUT}"
nao_contem "T-nd-14e plano de heal nao aplica" "qdisc add" "${OUT}"

echo "== T-nd-15: exit 6 -- a degradacao nao se efetivou =="
reset_shims
export SHIM_PING_LOSS=0
roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-15a perda observada 0% contra 20% configurados: exit" "6" "${RC}"
contem "T-nd-15a diagnostico" "nao se efetivou" "${OUT}"
ok "T-nd-15a limpeza apos exit 6" "0" "$(residuo)"
reset_shims
export SHIM_PING_LOSS=40
roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-15b perda observada 40% contra 20%: exit" "6" "${RC}"
reset_shims
export SHIM_PING_LOSS=25
roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-15c 25% observado contra 20% esta na tolerancia binomial: exit" "0" "${RC}"
reset_shims
export SHIM_PING_LOSS=100
roda_shim --target isn3 --severity S5 --duration 1
ok "T-nd-15d S5 com 100% observado (esperado 99,9%): exit" "0" "${RC}"
reset_shims
export SHIM_PING_LOSS=50
roda_shim --target isn3 --severity S5 --duration 1
ok "T-nd-15e S5 com 50% observado: exit" "6" "${RC}"
reset_shims
export SHIM_PING_LOSS=0
roda_shim --target isn3 --loss 20 --duration 1 --ping-count 0
ok "T-nd-15f --ping-count 0 desliga a verificacao por ping (contadores ainda passam)" "0" "${RC}"
reset_shims
export SHIM_SENT=0 SHIM_DROPPED=0
roda_shim --target isn3 --loss 20 --duration 1 --ping-count 0
ok "T-nd-15g contador 'Sent' zerado (filtro nao casou nada): exit" "6" "${RC}"
contem "T-nd-15g diagnostico" "zerad" "${OUT}"
ok "T-nd-15g limpeza apos exit 6" "0" "$(residuo)"
reset_shims
export SHIM_SENT=1000 SHIM_DROPPED=0
roda_shim --target isn3 --loss 20 --duration 1 --ping-count 0
ok "T-nd-15h 1000 pacotes, 0 descartados com perda 20%: exit" "6" "${RC}"
reset_shims
export SHIM_SENT=20 SHIM_DROPPED=0
roda_shim --target isn3 --loss 1 --duration 1 --ping-count 0
ok "T-nd-15i poucos pacotes, perda 1%, 0 descartados e so aviso (esperado < 10 descartes): exit" "0" "${RC}"
contem "T-nd-15i aviso de amostra pequena" "Aviso" "${OUT}"
reset_shims
export SHIM_SENT=20 SHIM_DROPPED=0
roda_shim --target isn3 --delay 50ms --duration 1 --ping-count 0
ok "T-nd-15j so atraso: 'dropped' 0 e normal" "0" "${RC}"
reset_shims
export SHIM_SENT=0 SHIM_DROPPED=0
roda_shim --target isn3 --delay 50ms --duration 1 --ping-count 0
ok "T-nd-15k so atraso mas 'Sent' 0: exit" "6" "${RC}"

echo "== T-nd-16: exit 4 -- no ausente, parado ou sem IP =="
reset_shims
SHIM_MISSING="isn3" roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-16a alvo inexistente" "4" "${RC}"
reset_shims
SHIM_MISSING="isn2" roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-16b par inexistente" "4" "${RC}"
reset_shims
SHIM_STATUS="exited" roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-16c container nao running" "4" "${RC}"
nao_contem "T-nd-16 nada alterado no kernel" "qdisc add" "$(cat "${SHIM_LOG}")"

echo "== T-nd-17: exit 3 -- dependencia ausente =="
reset_shims
OUT="$(PATH="${PATH_SHIMS}" PODMAN="podman-que-nao-existe" bash "${ND}" --target isn3 --loss 20 --duration 1 2>&1)"; RC=$?
ok "T-nd-17 podman ausente" "3" "${RC}"
contem "T-nd-17 mensagem" "nao encontrado" "${OUT}"

echo "== T-nd-18: exit 7 -- raiz ocupada (F3) / F2 no mesmo no =="
reset_shims
echo f3 >"${SHIM_STATE}/root.4003"
roda_shim --target isn3 --loss 20 --duration 1 --marker "${TMP}/marker"
ok "T-nd-18a netem raiz de F3 no ALVO: exit" "7" "${RC}"
contem "T-nd-18a mensagem cita a raiz ocupada" "raiz" "${OUT}"
contem "T-nd-18a mensagem cita inject-jitter" "inject-jitter" "${OUT}"
nao_contem "T-nd-18a nada foi adicionado" "qdisc add" "$(cat "${SHIM_LOG}")"
nao_contem "T-nd-18a nada foi removido (a raiz de F3 e alheia)" "qdisc del" "$(cat "${SHIM_LOG}")"
ok "T-nd-18a raiz de F3 intacta" "f3" "$(cat "${SHIM_STATE}/root.4003")"
ok "T-nd-18a sem marcador" "0" "$([[ -f "${TMP}/marker" ]] && echo 1 || echo 0)"
reset_shims
echo f3 >"${SHIM_STATE}/root.4001"
roda_shim --target isn3 --direction in --loss 20 --duration 1
ok "T-nd-18b F3 num PAR com direcao in: exit" "7" "${RC}"
nao_contem "T-nd-18b nada foi adicionado (nem no alvo nem nos pares)" "qdisc add" "$(cat "${SHIM_LOG}")"
reset_shims
echo f3 >"${SHIM_STATE}/root.4001"
roda_shim --target isn3 --direction out --loss 20 --duration 1
ok "T-nd-18c F3 em no NAO tocado (direcao out) coexiste: exit" "0" "${RC}"
ok "T-nd-18c a raiz de F3 em isn1 segue intacta" "f3" "$(cat "${SHIM_STATE}/root.4001")"
nao_contem "T-nd-18c injetor nao tocou isn1" "nsenter -t 4001 -n tc qdisc del" "$(cat "${SHIM_LOG}")"
reset_shims
: >"${SHIM_STATE}/clsact.4003"
roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-18d F2 (clsact) ativo no mesmo no: exit" "7" "${RC}"
contem "T-nd-18d mensagem cita F2" "F2" "${OUT}"
nao_contem "T-nd-18d nada foi adicionado" "qdisc add" "$(cat "${SHIM_LOG}")"
ok "T-nd-18d clsact de F2 intacto" "1" "$(ls "${SHIM_STATE}"/clsact.4003 2>/dev/null | wc -l | tr -d ' ')"
reset_shims
: >"${SHIM_STATE}/clsact.4001"
roda_shim --target isn3 --loss 20 --duration 1
ok "T-nd-18e F2 em outro no (nao tocado) coexiste: exit" "0" "${RC}"
reset_shims
echo f3 >"${SHIM_STATE}/root.4003"
roda_shim --heal
ok "T-nd-18f --heal tambem respeita a raiz alheia" "0" "${RC}"
ok "T-nd-18f raiz de F3 intacta apos heal" "f3" "$(cat "${SHIM_STATE}/root.4003")"

echo "== T-nd-19: pre-limpeza idempotente de sobra de execucao morta =="
reset_shims
echo prio >"${SHIM_STATE}/root.4003"; echo "loss 99%" >"${SHIM_STATE}/netem.4003"
roda_shim --target isn3 --loss 20 --duration 1 --ping-count 0
ok "T-nd-19 exit" "0" "${RC}"
ordem "T-nd-19 a sobra (propria) e removida ANTES da sonda/aplicacao" "${SHIM_LOG}" \
    "tc qdisc show dev eth0" "tc qdisc del dev eth0 root" "tc qdisc add dev eth0 root handle 1: prio"
ok "T-nd-19 sem residuo" "0" "$(residuo)"
reset_shims
roda_shim --target isn3 --loss 20 --duration 1 --ping-count 0
roda_shim --target isn3 --loss 20 --duration 1 --ping-count 0
ok "T-nd-19b duas execucoes seguidas: ambas exit 0 (limpeza completa na primeira)" "0" "${RC}"

echo "== T-nd-20: PODMAN='sudo podman' no plano e no uso real =="
OUT="$(PATH="${PATH_SEM_PODMAN}" PODMAN="sudo podman" bash "${ND}" --dry-run --target isn3 --loss 20 2>&1)"; RC=$?
ok "T-nd-20 dry-run exit" "0" "${RC}"
contem "T-nd-20 plano usa sudo podman inspect" "pid=\$(sudo podman inspect -f '{{.State.Pid}}' isn3)" "${OUT}"
reset_shims
OUT="$(PATH="${PATH_SHIMS}" PODMAN="sudo podman" bash "${ND}" --target isn3 --loss 20 --duration 1 2>&1)"; RC=$?
ok "T-nd-20 execucao com PODMAN='sudo podman'" "0" "${RC}"

echo "== T-nd-21: flock real -- duas injecoes no mesmo no; F2 e F4 se excluem =="
if command -v flock >/dev/null 2>&1; then
    # PATH sem o shim de flock para usar o flock real do host.
    SHIMDIR2="${TMP}/shims-semflock"; mkdir -p "${SHIMDIR2}"
    for s in podman sudo nsenter tc ping; do cp "${SHIMDIR}/${s}" "${SHIMDIR2}/${s}"; done
    FLOCK_DIR="$(dirname "$(command -v flock)")"
    reset_shims
    PATH="${SHIMDIR2}:${FLOCK_DIR}:${PATH_SEM_PODMAN}" bash "${ND}" --target isn3 --loss 20 --duration 5 --ping-count 0 >/dev/null 2>&1 &
    P1=$!
    sleep 1.5
    OUT="$(PATH="${SHIMDIR2}:${FLOCK_DIR}:${PATH_SEM_PODMAN}" bash "${ND}" --target isn3 --loss 20 --duration 1 --ping-count 0 2>&1)"; RC=$?
    ok "T-nd-21a segunda injecao no mesmo no recusada" "1" "${RC}"
    contem "T-nd-21a mensagem de lock" "ja ha uma injecao" "${OUT}"
    wait "${P1}" 2>/dev/null
    ok "T-nd-21a a primeira terminou e limpou" "0" "$(residuo)"
    # F2 segura o lock de F2 no no: o injetor de degradacao recusa (exit 7).
    reset_shims
    ( exec 9>"${LOCK_DIR}/inject-partition-isn3.lock"; flock -n 9; sleep 4 ) &
    P2=$!
    sleep 1
    OUT="$(PATH="${SHIMDIR2}:${FLOCK_DIR}:${PATH_SEM_PODMAN}" bash "${ND}" --target isn3 --loss 20 --duration 1 --ping-count 0 2>&1)"; RC=$?
    ok "T-nd-21b lock de F2 ocupado no alvo: exit 7" "7" "${RC}"
    contem "T-nd-21b mensagem cita F2" "F2" "${OUT}"
    nao_contem "T-nd-21b nada adicionado" "qdisc add" "$(cat "${SHIM_LOG}")"
    wait "${P2}" 2>/dev/null
    # F4 segura o lock do no; F2 (inject-partition) usaria o MESMO arquivo e seria recusado.
    reset_shims
    PATH="${SHIMDIR2}:${FLOCK_DIR}:${PATH_SEM_PODMAN}" bash "${ND}" --target isn3 --loss 20 --duration 5 --ping-count 0 >/dev/null 2>&1 &
    P3=$!
    sleep 1.5
    ok "T-nd-21c F4 em andamento segura o lock de F2 do no (arquivo existe)" "1" \
        "$([[ -f "${LOCK_DIR}/inject-partition-isn3.lock" ]] && echo 1 || echo 0)"
    ( exec 9>"${LOCK_DIR}/inject-partition-isn3.lock"; flock -n 9 ) ; RC=$?
    ok "T-nd-21c flock -n no lock de F2 do no falha enquanto F4 roda" "1" "${RC}"
    wait "${P3}" 2>/dev/null
else
    pulado "T-nd-21 (flock real ausente neste host; roda no WSL/VM)"
fi

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL, ${SKIP} SKIP ===="
[[ "${FAIL}" -eq 0 ]]
