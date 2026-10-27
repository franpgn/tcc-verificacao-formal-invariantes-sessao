#!/usr/bin/env bash
#
# collect-resources.test.sh
#
# Suite do coletor de recursos (B-37, contrato recursos/1). Auto-contida, no
# padrao de inject-jitter.test.sh (ok/contem, PASS/FAIL, exit 1 se alguma
# falhar). Nenhum podman real: podman, getconf, relogio e sleep sao shims
# num diretorio temporario; /sys/fs/cgroup e /proc sao arvores falsas
# (COLETOR_RAIZ_CGROUP, COLETOR_RAIZ_PROC). Roda no Git Bash e em Linux.
#   bash scripts/collect-resources.test.sh
# R1..R12 conforme docs/prompt-b37-coletor-recursos-2026-10-27.md.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COL="${DIR}/collect-resources.sh"

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

PY="$(command -v python3 2>/dev/null || command -v python 2>/dev/null || true)"
# No Git Bash o python3 do WindowsApps e um atalho que nao roda; testa.
if [[ -n "${PY}" ]] && ! "${PY}" -c 'import json' >/dev/null 2>&1; then
    PY="$(command -v python 2>/dev/null || true)"
fi

RAIZ_TMP="$(mktemp -d)"
trap 'rm -rf "${RAIZ_TMP}"' EXIT
SHIMS="${RAIZ_TMP}/shims"
mkdir -p "${SHIMS}"

ID1="a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
ID2="b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3"
ID3="c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"
# Formato real da maquina WSL podman-machine-default (Podman 5.7, cgroupfs):
FMT_WSL="/libpod_parent/libpod-"
# Formato rootful tipico de Ubuntu (systemd): /machine.slice/libpod-<id>.scope

# ---------------------------------------------------------------- shims
# podman: inspect -> le ${SHIM_ESTADO}/cg-<nome> (ausente => exit 125);
#         stats   -> JSON multi-linha, ou exit 125 se ${SHIM_ESTADO}/stats-falha.
cat > "${SHIMS}/podman" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "${SHIM_ESTADO}/podman.log"
case "$1" in
    inspect)
        nome="${!#}"
        if [[ -f "${SHIM_ESTADO}/cg-${nome}" ]]; then
            cat "${SHIM_ESTADO}/cg-${nome}"; exit 0
        fi
        echo "Error: no such container ${nome}" >&2; exit 125 ;;
    stats)
        if [[ -f "${SHIM_ESTADO}/stats-falha" ]]; then
            echo "Error: stats \"falhou\" de proposito" >&2; exit 125
        fi
        printf '[\n {\n  "name": "isn1",\n  "cpu_percent": "1.5%%"\n }\n]\n'; exit 0 ;;
esac
echo "shim podman: subcomando inesperado $1" >&2; exit 99
EOF
cat > "${SHIMS}/getconf" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == CLK_TCK ]]; then echo "${SHIM_CLK_TCK:-100}"; exit 0; fi
exit 1
EOF
# Relogio: chamadas pares (inicio do ciclo) devolvem o estado; chamadas impares
# (depois das leituras) somam o "custo" do ciclo (lista em custos-ms, uma por
# ciclo; padrao custo-padrao-ms) e devolvem. O sleep soma a duracao pedida.
cat > "${SHIMS}/agora" <<'EOF'
#!/usr/bin/env bash
E="${SHIM_ESTADO}"
read -r t < "${E}/relogio-ns"
read -r n < "${E}/relogio-chamadas"
if (( n % 2 == 1 )); then
    ciclo=$(( n / 2 ))
    custo="$(sed -n "$((ciclo + 1))p" "${E}/custos-ms" 2>/dev/null)"
    [[ -n "${custo}" ]] || read -r custo < "${E}/custo-padrao-ms"
    t=$(( t + custo * 1000000 ))
    echo "${t}" > "${E}/relogio-ns"
fi
echo $(( n + 1 )) > "${E}/relogio-chamadas"
echo "${t}"
EOF
cat > "${SHIMS}/dorme" <<'EOF'
#!/usr/bin/env bash
E="${SHIM_ESTADO}"
echo "$1" >> "${E}/sleeps.log"
s="${1%%.*}"; f="${1#*.}"; f="${f}000000000"; f="${f:0:9}"
read -r t < "${E}/relogio-ns"
echo $(( t + 10#${s} * 1000000000 + 10#${f} )) > "${E}/relogio-ns"
if [[ -x "${E}/gancho-sleep" ]]; then "${E}/gancho-sleep"; fi
exit 0
EOF
chmod +x "${SHIMS}"/*

# novo_caso <nome> <formato: wsl|ubuntu>: cria estado, arvores falsas e cgroups
# de isn1..isn3; exporta as variaveis do caso.
novo_caso() {
    CASO="${RAIZ_TMP}/$1"
    export SHIM_ESTADO="${CASO}/estado"
    CG="${CASO}/cg"; PROC="${CASO}/proc"; OUTD="${CASO}/saida"
    mkdir -p "${SHIM_ESTADO}" "${CG}" "${PROC}"
    echo 1700000000000000000 > "${SHIM_ESTADO}/relogio-ns"
    echo 0 > "${SHIM_ESTADO}/relogio-chamadas"
    echo 0 > "${SHIM_ESTADO}/custo-padrao-ms"
    : > "${SHIM_ESTADO}/custos-ms"
    local i id rel
    for i in 1 2 3; do
        id="ID${i}"; id="${!id}"
        if [[ "$2" == wsl ]]; then rel="${FMT_WSL}${id}"; else rel="/machine.slice/libpod-${id}.scope"; fi
        echo "${rel}" > "${SHIM_ESTADO}/cg-isn${i}"
        mkdir -p "${CG}${rel}"
        printf 'usage_usec %s\nuser_usec 1\nsystem_usec 2\n' "$(( i * 1000 ))" > "${CG}${rel}/cpu.stat"
        echo "$(( i * 1048576 ))" > "${CG}${rel}/memory.current"
    done
    # /proc falso: maquina e um processo (pid 4242, comm com espaco e parentese)
    printf 'cpu  100 0 50 9000 7 3 2 40 30 0\ncpu0 100 0 50 9000 7 3 2 40 30 0\n' > "${PROC}/stat"
    printf 'MemTotal:       8000000 kB\nMemFree:        1000000 kB\nMemAvailable:   6000000 kB\n' > "${PROC}/meminfo"
    mkdir -p "${PROC}/4242"
    echo '4242 (java (x) y) S 1 4242 4242 0 -1 4194560 100 0 0 0 250 150 7 9 20 0 30 0 12345 999999 2000 18446744073709551615' > "${PROC}/4242/stat"
    printf 'Name:\tjava\nVmPeak:\t  999 kB\nVmRSS:\t  51200 kB\nThreads:\t30\n' > "${PROC}/4242/status"
    export PODMAN="${SHIMS}/podman"
    export COLETOR_RAIZ_CGROUP="${CG}" COLETOR_RAIZ_PROC="${PROC}"
}
# roda o coletor com relogio e sleep injetados
coletar() {
    PATH="${SHIMS}:${PATH}" COLETOR_AGORA_NS_CMD="${SHIMS}/agora" COLETOR_SLEEP_CMD="${SHIMS}/dorme" \
        bash "${COL}" "$@"
}
contar() { grep -c -e "$1" "$2" 2>/dev/null || true; }
json_campo() {   # json_campo <arquivo> <campo>
    "${PY}" -c 'import json,sys; v=json.load(open(sys.argv[1],encoding="utf-8"))[sys.argv[2]]; print(v if not isinstance(v,(list,dict)) else json.dumps(v))' "$1" "$2" 2>/dev/null
}

echo "== R1: --dry-run resolve os 3 cgroups pelo podman inspect (2 formatos) e nao escreve nada =="
for fmt in wsl ubuntu; do
    novo_caso "r1-${fmt}" "${fmt}"
    OUT="$(PATH="${SHIMS}:${PATH}" bash "${COL}" --dry-run --saida "${OUTD}" --rep 1 \
            --containers "isn1 isn2 isn3" --pid java=4242 2>&1)"; RC=$?
    ok "R1 ${fmt} exit code" "0" "${RC}"
    for i in 1 2 3; do
        rel="$(cat "${SHIM_ESTADO}/cg-isn${i}")"
        contem "R1 ${fmt} cg:isn${i} resolvido" "fonte cg:isn${i} cgroup=${rel}" "${OUT}"
    done
    contem "R1 ${fmt} pid listado" "fonte pid:java pid=4242" "${OUT}"
    contem "R1 ${fmt} intervalo" "intervalo: 1000 ms" "${OUT}"
    contem "R1 ${fmt} csv que seria escrito" "recursos-rep-001.csv" "${OUT}"
    ok "R1 ${fmt} inspect chamado 3x" "3" "$(contar '^inspect --format {{.State.CgroupPath}} isn' "${SHIM_ESTADO}/podman.log")"
    ok "R1 ${fmt} nenhum arquivo criado" "0" "$(find "${OUTD}" -type f 2>/dev/null | wc -l | tr -d ' ')"
done

echo "== R2: uso invalido => exit 2 com mensagem =="
novo_caso r2 wsl
ERR="$(bash "${COL}" --rep 1 --containers isn1 2>&1)"; RC=$?
ok "R2a sem --saida exit" "2" "${RC}"; contem "R2a mensagem" "--saida" "${ERR}"
ERR="$(bash "${COL}" --saida "${OUTD}" --rep x1 --containers isn1 2>&1)"; RC=$?
ok "R2b --rep nao numerico exit" "2" "${RC}"; contem "R2b mensagem" "--rep" "${ERR}"
ERR="$(bash "${COL}" --saida "${OUTD}" --rep 1 --containers isn1 --intervalo-ms 0 2>&1)"; RC=$?
ok "R2c --intervalo-ms 0 exit" "2" "${RC}"; contem "R2c mensagem" "--intervalo-ms" "${ERR}"
ERR="$(bash "${COL}" --saida "${OUTD}" --rep 1 --containers isn1 --duracao-s 5 --max-amostras 3 2>&1)"; RC=$?
ok "R2d --duracao-s com --max-amostras exit" "2" "${RC}"; contem "R2d mensagem" "exclusivos" "${ERR}"
ERR="$(bash "${COL}" --saida "${OUTD}" --rep 1 --containers isn1 --pid semigual 2>&1)"; RC=$?
ok "R2e --pid malformado exit" "2" "${RC}"
ERR="$(bash "${COL}" --saida "${OUTD}" --rep 1 --containers isn1 --bogus 2>&1)"; RC=$?
ok "R2f argumento desconhecido exit" "2" "${RC}"
ok "R2 nenhum arquivo criado" "0" "$(find "${OUTD}" -type f 2>/dev/null | wc -l | tr -d ' ')"

echo "== R3: conteiner ausente (inspect exit 125) => exit 3 antes de medir =="
novo_caso r3 wsl
rm -f "${SHIM_ESTADO}/cg-isn2"
coletar --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" --max-amostras 2 >/dev/null 2>&1; RC=$?
ok "R3a exit code" "3" "${RC}"
ok "R3a nenhum CSV criado" "0" "$(find "${OUTD}" -name '*.csv' 2>/dev/null | wc -l | tr -d ' ')"
novo_caso r3b wsl
rm -rf "${CG}$(cat "${SHIM_ESTADO}/cg-isn3")"
coletar --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" --max-amostras 2 >/dev/null 2>&1; RC=$?
ok "R3b cgroup inexistente sob a raiz exit" "3" "${RC}"
ok "R3b nenhum CSV criado" "0" "$(find "${OUTD}" -name '*.csv' 2>/dev/null | wc -l | tr -d ' ')"
novo_caso r3c wsl
coletar --saida "${OUTD}" --rep 1 --containers "isn1" --pid java=9999 --max-amostras 2 >/dev/null 2>&1; RC=$?
ok "R3c PID inexistente exit" "3" "${RC}"
ok "R3c nenhum CSV criado" "0" "$(find "${OUTD}" -name '*.csv' 2>/dev/null | wc -l | tr -d ' ')"

echo "== R4: saida existente => exit 4, arquivo intacto =="
novo_caso r4 wsl
mkdir -p "${OUTD}"
printf 'conteudo bruto anterior\n' > "${OUTD}/recursos-rep-007.csv"
H0="$(sha256sum "${OUTD}/recursos-rep-007.csv" | cut -d' ' -f1)"
coletar --saida "${OUTD}" --rep 7 --containers "isn1 isn2 isn3" --max-amostras 2 >/dev/null 2>&1; RC=$?
ok "R4 exit code" "4" "${RC}"
ok "R4 sha256 inalterado" "${H0}" "$(sha256sum "${OUTD}/recursos-rep-007.csv" | cut -d' ' -f1)"
ok "R4 meta nao criado" "no" "$([[ -e "${OUTD}/recursos-meta-rep-007.json" ]] && echo yes || echo no)"

echo "== R5: formato com --max-amostras 3 (relogio e sleep injetados) =="
novo_caso r5 wsl
echo 300 > "${SHIM_ESTADO}/custo-padrao-ms"
coletar --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" --pid java=4242 \
    --max-amostras 3 --podman-stats-s 0 >/dev/null 2>&1; RC=$?
F="${OUTD}/recursos-rep-001.csv"
ok "R5 exit code" "0" "${RC}"
ok "R5 cabecalho exato" "t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado" "$(head -1 "${F}" 2>/dev/null)"
ok "R5 linhas totais (1 + 3x5 + 1)" "17" "$(wc -l < "${F}" 2>/dev/null | tr -d ' ')"
ok "R5 linhas cg" "9" "$(contar ',cg:isn' "${F}")"
ok "R5 linhas pid" "3" "$(contar ',pid:java,' "${F}")"
ok "R5 linhas vm" "3" "$(contar ',vm,' "${F}")"
ok "R5 uma linha coletor (a ultima)" "1 coletor" "$(contar ',coletor,' "${F}") $(tail -1 "${F}" 2>/dev/null | cut -d, -f2)"
ok "R5 3 instantes distintos nas amostras" "3" "$(grep -v -e '^t_epoch' -e ',coletor,' "${F}" 2>/dev/null | cut -d, -f1 | sort -u | wc -l | tr -d ' ')"
ok "R5 5 linhas por instante" "5 5 5" "$(grep -v -e '^t_epoch' -e ',coletor,' "${F}" 2>/dev/null | cut -d, -f1 | uniq -c | awk '{printf "%s%s", s, $1; s=" "}')"
ok "R5 t crescente entre amostras" "sim" "$(grep -v -e '^t_epoch' -e ',coletor,' "${F}" 2>/dev/null | cut -d, -f1 | uniq | awk 'NR>1 && $1<=p {bad=1} {p=$1} END {print (NR==3 && !bad) ? "sim" : "nao"}')"
ok "R5 ordem das fontes na amostra" "cg:isn1 cg:isn2 cg:isn3 pid:java vm" "$(sed -n '2,6p' "${F}" 2>/dev/null | cut -d, -f2 | tr '\n' ' ' | sed 's/ $//')"
ok "R5 6 colunas em todas as linhas" "0" "$(awk -F, 'NF!=6' "${F}" 2>/dev/null | wc -l | tr -d ' ')"
ok "R5 LF (sem CR)" "0" "$(grep -c $'\r' "${F}" 2>/dev/null || true)"

echo "== R6: conversoes (CLK_TCK=100 pelo shim de getconf) =="
L_CG1="$(grep ',cg:isn1,' "${F}" 2>/dev/null | head -1)"
L_PID="$(grep ',pid:java,' "${F}" 2>/dev/null | head -1)"
L_VM="$(grep ',vm,' "${F}" 2>/dev/null | head -1)"
ok "R6 cg usage_usec" "1000" "$(echo "${L_CG1}" | cut -d, -f3)"
ok "R6 cg memory.current" "1048576" "$(echo "${L_CG1}" | cut -d, -f4)"
ok "R6 cg steal vazio e ok" ",ok" "$(echo "${L_CG1}" | cut -d, -f5-6)"
ok "R6 pid (250+150) ticks x 1e6 / 100" "4000000" "$(echo "${L_PID}" | cut -d, -f3)"
ok "R6 pid VmRSS x 1024" "52428800" "$(echo "${L_PID}" | cut -d, -f4)"
ok "R6 pid steal vazio e ok" ",ok" "$(echo "${L_PID}" | cut -d, -f5-6)"
ok "R6 vm cpu (100+0+50+3+2) x 10000" "1550000" "$(echo "${L_VM}" | cut -d, -f3)"
ok "R6 vm steal 40 x 10000" "400000" "$(echo "${L_VM}" | cut -d, -f5)"
ok "R6 vm mem (MemTotal-MemAvailable) x 1024" "2048000000" "$(echo "${L_VM}" | cut -d, -f4)"
ok "R6 vm ok" "ok" "$(echo "${L_VM}" | cut -d, -f6)"
# usage_usec 123456 (valor do prompt), noutro caso
novo_caso r6b ubuntu
printf 'usage_usec 123456\nuser_usec 100000\nsystem_usec 23456\n' > "${CG}$(cat "${SHIM_ESTADO}/cg-isn2")/cpu.stat"
coletar --saida "${OUTD}" --rep 2 --containers "isn2" --max-amostras 1 --podman-stats-s 0 >/dev/null 2>&1
ok "R6b usage_usec 123456" "123456" "$(grep ',cg:isn2,' "${OUTD}/recursos-rep-002.csv" 2>/dev/null | head -1 | cut -d, -f3)"
ok "R6b clk_tck no meta" "100" "$(json_campo "${OUTD}/recursos-meta-rep-002.json" clk_tck)"
novo_caso r6c wsl
SHIM_CLK_TCK=250 coletar --saida "${OUTD}" --rep 3 --containers "isn1" --pid java=4242 --max-amostras 1 --podman-stats-s 0 >/dev/null 2>&1
ok "R6c pid com CLK_TCK=250" "1600000" "$(grep ',pid:java,' "${OUTD}/recursos-rep-003.csv" 2>/dev/null | head -1 | cut -d, -f3)"
ok "R6c clk_tck 250 no meta" "250" "$(json_campo "${OUTD}/recursos-meta-rep-003.json" clk_tck)"

echo "== R7: cgroup de isn2 some depois da 1a amostra =="
novo_caso r7 wsl
REL2="$(cat "${SHIM_ESTADO}/cg-isn2")"
cat > "${SHIM_ESTADO}/gancho-sleep" <<EOF
#!/usr/bin/env bash
rm -rf "${CG}${REL2}"
EOF
chmod +x "${SHIM_ESTADO}/gancho-sleep"
echo 100 > "${SHIM_ESTADO}/custo-padrao-ms"
coletar --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" --pid java=4242 \
    --max-amostras 3 --podman-stats-s 0 >/dev/null 2>&1; RC=$?
F="${OUTD}/recursos-rep-001.csv"
ok "R7 exit code" "0" "${RC}"
ok "R7 isn2 ok na 1a amostra" "ok" "$(grep ',cg:isn2,' "${F}" 2>/dev/null | sed -n 1p | cut -d, -f6)"
ok "R7 isn2 erro nas seguintes" "erro:cgroup-ausente erro:cgroup-ausente" "$(grep ',cg:isn2,' "${F}" 2>/dev/null | sed -n '2,3p' | cut -d, -f6 | tr '\n' ' ' | sed 's/ $//')"
ok "R7 colunas numericas vazias no erro" ",,," "$(grep ',cg:isn2,' "${F}" 2>/dev/null | sed -n 2p | cut -d, -f3-5),"
ok "R7 demais fontes ok" "12" "$(grep -v -e '^t_epoch' -e ',cg:isn2,' -e ',coletor,' "${F}" 2>/dev/null | grep -c ',ok$' || true)"
ok "R7 3 amostras no meta" "3" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" amostras)"
# pid que some e /proc/stat ilegivel
novo_caso r7b wsl
cat > "${SHIM_ESTADO}/gancho-sleep" <<EOF
#!/usr/bin/env bash
rm -rf "${PROC}/4242"; rm -f "${PROC}/stat"
EOF
chmod +x "${SHIM_ESTADO}/gancho-sleep"
coletar --saida "${OUTD}" --rep 1 --containers "isn1" --pid java=4242 --max-amostras 2 --podman-stats-s 0 >/dev/null 2>&1; RC=$?
F="${OUTD}/recursos-rep-001.csv"
ok "R7b exit code" "0" "${RC}"
ok "R7b pid ausente na 2a amostra" ",,,erro:pid-ausente" "$(grep ',pid:java,' "${F}" 2>/dev/null | sed -n 2p | cut -d, -f3-6)"
ok "R7b vm erro de leitura na 2a amostra" ",,,erro:leitura" "$(grep ',vm,' "${F}" 2>/dev/null | sed -n 2p | cut -d, -f3-6)"

echo "== R8: cadencia por prazo absoluto =="
novo_caso r8 wsl
echo 300 > "${SHIM_ESTADO}/custo-padrao-ms"
coletar --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" --max-amostras 4 --podman-stats-s 0 >/dev/null 2>&1; RC=$?
ok "R8a exit code" "0" "${RC}"
ok "R8a sleeps de 0,7 s (custo 300 ms)" "0.700000 0.700000 0.700000" "$(tr '\n' ' ' < "${SHIM_ESTADO}/sleeps.log" 2>/dev/null | sed 's/ $//')"
ok "R8a atraso_max_ns 0" "0" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" atraso_max_ns)"
ok "R8a t das amostras a cada 1 s exato" "1000000000 1000000000 1000000000" "$(grep ',cg:isn1,' "${OUTD}/recursos-rep-001.csv" 2>/dev/null | cut -d, -f1 | awk 'NR>1 {printf "%s%s", s, $1-p; s=" "} {p=$1}')"
novo_caso r8b wsl
printf '300\n1200\n300\n300\n' > "${SHIM_ESTADO}/custos-ms"
echo 300 > "${SHIM_ESTADO}/custo-padrao-ms"
coletar --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" --max-amostras 4 --podman-stats-s 0 >/dev/null 2>&1; RC=$?
ok "R8b exit code" "0" "${RC}"
# ciclo 0: 0,7 s; ciclo 1 (custo 1,2 s): sem sleep; ciclo 2 recupera o prazo: 0,5 s
ok "R8b sleeps (ciclo atrasado sem sleep; volta ao prazo)" "0.700000 0.500000" "$(tr '\n' ' ' < "${SHIM_ESTADO}/sleeps.log" 2>/dev/null | sed 's/ $//')"
ok "R8b atraso_max_ns = 200 ms" "200000000" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" atraso_max_ns)"
ok "R8b amostras em 0, 1,0, 2,2, 3,0 s" "0 1000 2200 3000" "$(grep ',cg:isn1,' "${OUTD}/recursos-rep-001.csv" 2>/dev/null | cut -d, -f1 | awk 'NR==1 {b=$1} {printf "%s%d", s, ($1-b)/1000000; s=" "}')"

echo "== R9: SIGTERM no coletor real (relogio e sleep reais, 200 ms) =="
novo_caso r9 wsl
PATH="${SHIMS}:${PATH}" bash "${COL}" --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" \
    --intervalo-ms 200 --podman-stats-s 0 >/dev/null 2>"${CASO}/r9.err" &
CPID=$!
sleep 1.2
FILHOS_ANTES="$(ps -ef 2>/dev/null | awk -v p="${CPID}" '$3==p' | wc -l | tr -d ' ')"
kill -TERM "${CPID}"
wait "${CPID}"; RC=$?
ok "R9 exit code" "0" "${RC}"
F="${OUTD}/recursos-rep-001.csv"
ok "R9 linha coletor presente (ultima)" "coletor" "$(tail -1 "${F}" 2>/dev/null | cut -d, -f2)"
ok "R9 motivo_fim sinal" "sinal" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" motivo_fim)"
N9="$(json_campo "${OUTD}/recursos-meta-rep-001.json" amostras)"
ok "R9 entre 4 e 8 amostras em ~1,2 s" "sim" "$([[ "${N9:-0}" -ge 4 && "${N9:-0}" -le 8 ]] && echo sim || echo "nao(${N9})")"
sleep 1
ok "R9 nenhum filho vivo 1 s depois" "0" "$(ps -ef 2>/dev/null | awk -v p="${CPID}" '$3==p' | wc -l | tr -d ' ')"
echo "  (info) filhos do coletor antes do sinal: ${FILHOS_ANTES}"
ok "R9 coletor nao esta mais vivo" "morto" "$(kill -0 "${CPID}" 2>/dev/null && echo vivo || echo morto)"
# SIGINT tambem encerra com 0 e motivo sinal
novo_caso r9b wsl
PATH="${SHIMS}:${PATH}" bash "${COL}" --saida "${OUTD}" --rep 1 --containers "isn1" \
    --intervalo-ms 200 --podman-stats-s 0 >/dev/null 2>&1 &
CPID=$!
sleep 0.7
kill -INT "${CPID}"
wait "${CPID}"; RC=$?
ok "R9b SIGINT exit code" "0" "${RC}"
ok "R9b motivo_fim sinal" "sinal" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" motivo_fim)"

echo "== R10: podman stats periodico (shim) =="
novo_caso r10 wsl
PATH="${SHIMS}:${PATH}" bash "${COL}" --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" \
    --intervalo-ms 250 --podman-stats-s 1 --max-amostras 12 >/dev/null 2>&1; RC=$?
J="${OUTD}/recursos-podman-rep-001.jsonl"
ok "R10a exit code" "0" "${RC}"
NJ="$(wc -l < "${J}" 2>/dev/null | tr -d ' ')"
ok "R10a 2 a 4 linhas em ~3 s" "sim" "$([[ "${NJ:-0}" -ge 2 && "${NJ:-0}" -le 4 ]] && echo sim || echo "nao(${NJ})")"
ok "R10a todas as linhas JSON validas" "ok" "$("${PY}" -c 'import json,sys
[json.loads(l) for l in open(sys.argv[1],encoding="utf-8")]
print("ok")' "${J}" 2>&1 | tail -1)"
ok "R10a primeira linha ok:true com stats" "True isn1" "$("${PY}" -c 'import json,sys
d=json.loads(open(sys.argv[1],encoding="utf-8").readline()); print(d["ok"], d["stats"][0]["name"], end="")' "${J}" 2>&1)"
ok "R10a t_epoch_ns da 1a linha = t da 1a amostra" "$(sed -n 2p "${OUTD}/recursos-rep-001.csv" 2>/dev/null | cut -d, -f1)" "$("${PY}" -c 'import json,sys; print(json.loads(open(sys.argv[1],encoding="utf-8").readline())["t_epoch_ns"])' "${J}" 2>&1)"
ok "R10a nenhum temporario sobrando" "0" "$(find "${OUTD}" -name '.*tmp' | wc -l | tr -d ' ')"
novo_caso r10b wsl
touch "${SHIM_ESTADO}/stats-falha"
PATH="${SHIMS}:${PATH}" bash "${COL}" --saida "${OUTD}" --rep 1 --containers "isn1 isn2 isn3" \
    --intervalo-ms 250 --podman-stats-s 1 --max-amostras 8 >/dev/null 2>&1; RC=$?
J="${OUTD}/recursos-podman-rep-001.jsonl"
ok "R10b exit code (coletor segue)" "0" "${RC}"
ok "R10b 8 amostras mesmo com stats falhando" "8" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" amostras)"
ok "R10b linhas ok:false validas com erro" "sim" "$("${PY}" -c 'import json,sys
L=[json.loads(l) for l in open(sys.argv[1],encoding="utf-8")]
print("sim" if L and all(d["ok"] is False and isinstance(d["erro"],str) and d["erro"] for d in L) else "nao")' "${J}" 2>&1 | tail -1)"

echo "== R11: meta =="
novo_caso r11 ubuntu
echo 100 > "${SHIM_ESTADO}/custo-padrao-ms"
coletar --saida "${OUTD}" --rep 12 --containers "isn1 isn2 isn3" --pid java=4242 \
    --intervalo-ms 500 --max-amostras 5 --podman-stats-s 0 >/dev/null 2>&1; RC=$?
M="${OUTD}/recursos-meta-rep-012.json"
ok "R11 exit code" "0" "${RC}"
ok "R11 JSON valido" "ok" "$("${PY}" -c 'import json,sys; json.load(open(sys.argv[1],encoding="utf-8")); print("ok")' "${M}" 2>&1 | tail -1)"
ok "R11 contrato" "recursos/1" "$(json_campo "${M}" contrato)"
ok "R11 sha256_script = sha256 do script" "$(sha256sum "${COL}" | cut -d' ' -f1)" "$(json_campo "${M}" sha256_script)"
ok "R11 amostras = ciclos" "5" "$(json_campo "${M}" amostras)"
ok "R11 amostras = instantes no CSV" "5" "$(grep ',vm,' "${OUTD}/recursos-rep-012.csv" 2>/dev/null | wc -l | tr -d ' ')"
ok "R11 motivo_fim" "max-amostras" "$(json_campo "${M}" motivo_fim)"
ok "R11 intervalo_ms" "500" "$(json_campo "${M}" intervalo_ms)"
ok "R11 podman_stats_s" "0" "$(json_campo "${M}" podman_stats_s)"
ok "R11 inicio = t da 1a amostra" "$(sed -n 2p "${OUTD}/recursos-rep-012.csv" 2>/dev/null | cut -d, -f1)" "$(json_campo "${M}" inicio_epoch_ns)"
ok "R11 fontes" '[{"fonte": "cg:isn1", "cgroup": "/machine.slice/libpod-'"${ID1}"'.scope"}, {"fonte": "cg:isn2", "cgroup": "/machine.slice/libpod-'"${ID2}"'.scope"}, {"fonte": "cg:isn3", "cgroup": "/machine.slice/libpod-'"${ID3}"'.scope"}, {"fonte": "pid:java", "pid": 4242}]' "$(json_campo "${M}" fontes)"
ok "R11 chaves do contrato" "atraso_max_ns amostras clk_tck contrato fim_epoch_ns fontes inicio_epoch_ns intervalo_ms motivo_fim nproc podman_stats_s sha256_script uname_r versao_coletor" "$("${PY}" -c 'import json,sys; print(" ".join(sorted(json.load(open(sys.argv[1],encoding="utf-8")))))' "${M}" 2>&1)"
ok "R11 fim >= inicio" "sim" "$("${PY}" -c 'import json,sys; d=json.load(open(sys.argv[1],encoding="utf-8")); print("sim" if d["fim_epoch_ns"]>=d["inicio_epoch_ns"] else "nao")' "${M}" 2>&1)"
# --duracao-s: 3 s a 1000 ms => 3 amostras, motivo duracao
novo_caso r11b wsl
coletar --saida "${OUTD}" --rep 1 --containers "isn1" --duracao-s 3 --podman-stats-s 0 >/dev/null 2>&1; RC=$?
ok "R11b --duracao-s exit" "0" "${RC}"
ok "R11b --duracao-s 3 => 3 amostras" "3" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" amostras)"
ok "R11b motivo_fim duracao" "duracao" "$(json_campo "${OUTD}/recursos-meta-rep-001.json" motivo_fim)"

echo "== R12: sem fork no caminho quente (inspecao da fonte) =="
QUENTE="$(sed -n '/^# >>> caminho-quente/,/^# <<< caminho-quente/p' "${COL}")"
ok "R12 bloco caminho-quente presente" "sim" "$([[ "$(echo "${QUENTE}" | wc -l)" -gt 20 ]] && echo sim || echo nao)"
SEM_COMENT="$(echo "${QUENTE}" | sed -e 's/^[[:space:]]*#.*$//' -e 's/[[:space:]]#[[:space:]].*$//')"
for f in ciclo laco ler_cgroup ler_pid_bruto ler_vm agora_ns dormir; do
    contem "R12 funcao ${f} dentro do bloco" "${f}() {" "${QUENTE}"
done
for proibido in cat awk sed cut grep date head tail tr wc; do
    ok "R12 nao chama ${proibido}" "0" "$(echo "${SEM_COMENT}" | grep -cwE "${proibido}" || true)"
done
ok "R12 sem substituicao de comando \$(...)" "0" "$(echo "${SEM_COMENT}" | grep -cE '\$\([^(]' || true)"
ok "R12 sem crase" "0" "$(echo "${SEM_COMENT}" | grep -c '`' || true)"
ok "R12 usa EPOCHREALTIME" "1" "$(echo "${SEM_COMENT}" | grep -c 'EPOCHREALTIME' || true)"
ok "R12 processos so em sleep e podman stats (2 linhas com &)" "2" "$(echo "${SEM_COMENT}" | grep -cE '&[[:space:]]*$' || true)"

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
