#!/usr/bin/env bash
#
# inject-jitter.test.sh
#
# Suite minima de testes do inject-jitter.sh que NAO exigem VM/netem
# (T-jit-1..4 e T-jit-7 do plano docs/plano-f3-vm-netem.md secao 2.6).
# Os testes que exigem a VM com sch_netem (T-jit-5 Ctrl-C/cleanup e
# T-jit-6 execucao real curta) NAO sao cobertos aqui e ficam para o gate
# pos-reboot (secao 3.5/3.7 do plano).
#
# Nao ha harness de teste de shell no repo (bats etc.); este arquivo e
# auto-contido. Rodar com bash:
#   bash scripts/inject-jitter.test.sh
# Exit 0 se todos passarem; 1 caso contrario. Nao requer podman/tc.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JITTER="${DIR}/inject-jitter.sh"
BASE="${DIR}/run-baseline.sh"

PASS=0
FAIL=0

# ok <descricao> <esperado> <obtido>
ok() {
    if [[ "$2" == "$3" ]]; then
        echo "PASS: $1"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $1 (esperado='$2' obtido='$3')"
        FAIL=$((FAIL + 1))
    fi
}

# contem <descricao> <substring> <texto>
contem() {
    if echo "$3" | grep -qF -e "$2"; then
        echo "PASS: $1"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $1 (nao encontrou '$2')"
        FAIL=$((FAIL + 1))
    fi
}

# Garante que podman NAO esta no PATH durante os testes de dry-run/help,
# para provar que esses caminhos nao dependem dele (CA-2).
PATH_SEM_PODMAN="/usr/bin:/bin"

echo "== T-jit-1: --help retorna 0 e documenta as flags =="
OUT="$(bash "${JITTER}" --help)"; RC=$?
ok "T-jit-1 exit code" "0" "${RC}"
for f in --node --iface --delay --jitter --distribution --duration --dry-run --help; do
    contem "T-jit-1 documenta ${f}" "${f}" "${OUT}"
done

echo "== T-jit-2: --dry-run (defaults) imprime nsenter add/sleep/del, exit 0, sem podman =="
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${JITTER}" --dry-run 2>&1)"; RC=$?
ok "T-jit-2 exit code" "0" "${RC}"
contem "T-jit-2 comando add (nsenter ... tc netem)" "sudo nsenter -t <pid> -n tc qdisc add dev eth0 root netem delay 20ms 13ms distribution normal" "${OUT}"
contem "T-jit-2 resolve pid via podman inspect" "podman inspect -f '{{.State.Pid}}'" "${OUT}"
ok "T-jit-2 nao usa podman exec" "0" "$(echo "${OUT}" | grep -cF 'podman exec')"
contem "T-jit-2 sleep" "sleep 1800" "${OUT}"
contem "T-jit-2 comando del" "sudo nsenter -t <pid> -n tc qdisc del dev eth0 root" "${OUT}"
contem "T-jit-2 nada executado" "dry-run: nada executado" "${OUT}"

echo "== T-jit-2b: --dry-run com node/distribuicao custom reflete no plano =="
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${JITTER}" --dry-run --node isn1 \
        --distribution lognormal --delay 25ms --jitter 18ms --duration 90 2>&1)"; RC=$?
ok "T-jit-2b exit code" "0" "${RC}"
contem "T-jit-2b inspect do node custom" "podman inspect -f '{{.State.Pid}}' isn1" "${OUT}"
contem "T-jit-2b node/dist custom" "sudo nsenter -t <pid> -n tc qdisc add dev eth0 root netem delay 25ms 18ms distribution lognormal" "${OUT}"
contem "T-jit-2b duration custom" "sleep 90" "${OUT}"

echo "== T-jit-3: --duration nao-inteiro retorna 2 =="
bash "${JITTER}" --duration abc >/dev/null 2>&1; RC=$?
ok "T-jit-3 exit code" "2" "${RC}"

echo "== T-jit-4: validacao de argumentos -> exit 2 =="
bash "${JITTER}" --delay 20 >/dev/null 2>&1; RC=$?      # falta sufixo ms/us/s
ok "T-jit-4a --delay sem sufixo" "2" "${RC}"
bash "${JITTER}" --jitter xyz >/dev/null 2>&1; RC=$?
ok "T-jit-4b --jitter invalido" "2" "${RC}"
bash "${JITTER}" --distribution gauss >/dev/null 2>&1; RC=$?
ok "T-jit-4c --distribution desconhecida" "2" "${RC}"
bash "${JITTER}" --duration 0 >/dev/null 2>&1; RC=$?
ok "T-jit-4d --duration 0" "2" "${RC}"
bash "${JITTER}" --foo >/dev/null 2>&1; RC=$?
ok "T-jit-4e argumento desconhecido" "2" "${RC}"
bash "${JITTER}" --node >/dev/null 2>&1; RC=$?          # flag sem valor
ok "T-jit-4f --node sem valor" "2" "${RC}"

echo "== T-jit-2c: --delay/--jitter decimais e unidades aceitos =="
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${JITTER}" --dry-run --delay 0.5ms --jitter 200us 2>&1)"; RC=$?
ok "T-jit-2c exit code" "0" "${RC}"
contem "T-jit-2c decimal/us" "netem delay 0.5ms 200us" "${OUT}"

echo "== T-jit-2d: override PODMAN se reflete no plano do --dry-run =="
# Cluster ROOTFUL (VM Hyper-V): PODMAN="sudo podman" deve aparecer no comando
# que resolve o PID do container. O default (sem override) usa 'podman'.
OUT="$(PATH="${PATH_SEM_PODMAN}" PODMAN="sudo podman" bash "${JITTER}" --dry-run 2>&1)"; RC=$?
ok "T-jit-2d exit code" "0" "${RC}"
contem "T-jit-2d plano usa 'sudo podman inspect'" "pid=\$(sudo podman inspect -f '{{.State.Pid}}' isn2)" "${OUT}"
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${JITTER}" --dry-run 2>&1)"
ok "T-jit-2d default nao usa sudo no inspect" "0" "$(echo "${OUT}" | grep -cF 'sudo podman inspect')"
contem "T-jit-2d default usa 'podman inspect'" "pid=\$(podman inspect -f '{{.State.Pid}}' isn2)" "${OUT}"

echo "== T-jit-7: run-baseline.sh --faults 'none F3' --dry-run descreve o plano F3 =="
if [[ -f "${BASE}" ]]; then
    OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${BASE}" --faults "none F3" \
            --scenarios S1 --reps 1 --duration 60 --dry-run 2>&1)"; RC=$?
    ok "T-jit-7 exit code" "0" "${RC}"
    contem "T-jit-7 menciona ramo F3" "S1 / F3" "${OUT}"
else
    echo "SKIP: T-jit-7 (run-baseline.sh ausente)"
fi

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
