#!/usr/bin/env bash
#
# run-rebaseline.sh
#
# Runner do rebaseline S1/S2 v3 em DIST_SYNC e DIST_ASYNC (DT-009, com DT-010 e DT-011). Roda DENTRO da
# VM tcc-f3, em tmux, contra o cluster rootful (PODMAN="sudo podman"), sob flock /var/lock/tcc-vm.lock.
# Uma invocacao executa UMA fase (piloto, noite1, noite2 ou reposicoes) da matriz pre-registrada.
#
# Pre-registro vinculante: docs/pre-registro-rebaseline-aprovado-2026-10-23.md (PR). Os valores do PR sao
# constantes nomeadas abaixo, NAO opcoes de linha de comando. A DT-011 substitui o instante de injecao
# (+49 s da ancora "Warm-up ate", em vez de +35 s) e a duracao de F3 (251 s, em vez de 265 s).
#
# Fluxo: pre-voo (flock, SHA-256 do jar e da matriz, dependencias, cluster, guarda de netns, linha de base
# de ISPN006010, ambiente.md) -> gate da noite (go <fase> <AAAA-MM-DD> digitado em /dev/tty) -> por
# execucao, na ordem da matriz: portao K1, gate f3 antes da primeira F3, workload, injecao a +49 s da
# ancora (dead-man armado antes do netem), pos (brutos, VerificadorSanidade, ISPN006010), analise
# (rebaseline.py checar e estado) -> resumo e MANIFEST.sha256.
#
# Uso:
#   scripts/run-rebaseline.sh --matriz <tsv> --matriz-sha256 <hex> --fase piloto|noite1|noite2|reposicoes
#       [--jar <jar> --jar-sha256 <hex>] [--saida <dir>] [--cluster-criado-apos <ISO-8601>]
#       [--servers h:p,h:p,h:p] [--username <u>] [--password <p>] [--dry-run]
#   (--jar, --jar-sha256 e --cluster-criado-apos sao obrigatorios fora do --dry-run.)
#
# Variaveis: PODMAN (padrao "sudo podman", exportada aos injetores), JAVA (padrao java), RB_PASSWORD
# (senha padrao do Infinispan, se --password nao for dado). So nos testes: RB_MODO_TESTE=1 (o gate le
# RB_TTY_ARQUIVO no lugar de /dev/tty e o PODMAN tem de ser shim de RB_SHIM_DIR).
#
# Exit codes:
#   0  fase concluida            2  uso                    3  dependencia ausente
#   4  pre-voo recusado          5  go nao dado            6  erro de infraestrutura (injetor com exit
#   10 parada automatica (PR §6)                              inesperado, netem residual, dead-man, passo
#                                                             obrigatorio com exit nao previsto)
# A ultima linha do log da fase (<saida>/<fase>/runner.log) traz o exit e o motivo. Nunca apaga brutos.
# NAO faz git push.
set -euo pipefail

NOME="run-rebaseline.sh"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="${SCRIPT_DIR}/$(basename "${BASH_SOURCE[0]}")"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
INJ_CRASH="${SCRIPT_DIR}/inject-crash.sh"
INJ_JITTER="${SCRIPT_DIR}/inject-jitter.sh"
ANALISE="${REPO_DIR}/analysis/rebaseline.py"
ARGS_ORIGINAIS=("$@")

# ------------------------------------------------------------------ constantes do PR (nao sao opcoes)
readonly DURACAO=300 WARMUP_MIN=30 THREADS=4 OPS=0 REP=1 MIX=v3 RESET_STATE=on   # PR §2
readonly S0=50000 PASSO_SEMENTE=100 I_REPOSICAO_INICIAL=120 I_PILOTO=1000         # PR §3, §4
readonly INJ_OFFSET=49            # DT-011: +49 s da ancora "Warm-up ate" (substitui os +35 s do PR §2)
readonly F1_DUR=60                # PR §2
readonly F3_DELAY=20ms F3_JITTER=13ms F3_DIST=normal   # PR §2
readonly F3_DUR=251               # DT-011: de +49 a +300 s (substitui os 265 s do PR §2)
readonly NO_ALVO=isn2 IFACE=eth0  # PR §2
readonly NOS="isn1 isn2 isn3"
readonly DEADMAN_ON_ACTIVE=$((F3_DUR + 90))   # 341 s (DT-011)
readonly NETEM_AMOSTRAS_S="70 280"            # DT-011: amostras de tc qdisc show em +70 s e +280 s
readonly K1_JANELA_S=60 K1_PERIODO_S=5 K1_TIMEOUT_S=600   # PR §6: K1 e parada (e)
readonly F1_VOLTA_TIMEOUT_S=180 F1_VOLTA_PERIODO_S=5      # PR §6: parada (e)
readonly ANCORA_TIMEOUT_S=120 ANCORA_POLL_S=0.2
readonly GATE_TIMEOUT_S=3600      # sem go em 1 h: exit 5
readonly EST_EXEC_S=420           # estimativa por execucao (portao 60 s + JVM/carga + 300 s + pos)
readonly LOCK=/var/lock/tcc-vm.lock
readonly CLASSE_VERIFICADOR=br.unipampa.tcc.session.VerificadorSanidade
readonly ANCORA_RE='^Warm-up ate \+'
# exits documentados dos injetores (inject-crash.sh: 0..4; inject-jitter.sh: 0..5); outro exit = infra (6)
readonly INJ_EXITS_DOCUMENTADOS=" 0 1 2 3 4 5 "

PODMAN="${PODMAN:-sudo podman}"
export PODMAN
JAVA="${JAVA:-java}"
read -r -a JAVA_CMD <<< "${JAVA}"
PYTHON=python3

# ------------------------------------------------------------------ opcoes
MATRIZ=""; MATRIZ_SHA=""; JAR=""; JAR_SHA=""; FASE=""; SAIDA="${REPO_DIR}/runs/rebaseline"
CRIADO_APOS=""; SERVERS="127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224"; USERNAME="admin"
PASSWORD="${RB_PASSWORD:-}"; DRY_RUN=0

uso() { sed -n '2,36p' "${SELF}" | sed 's/^# \{0,1\}//'; }
erro_uso() { echo "Erro: $*" >&2; exit 2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --matriz|--matriz-sha256|--jar|--jar-sha256|--fase|--saida|--cluster-criado-apos|--servers|--username|--password)
            [[ $# -ge 2 ]] || erro_uso "$1 exige valor"
            case "$1" in
                --matriz) MATRIZ="$2" ;; --matriz-sha256) MATRIZ_SHA="$2" ;; --jar) JAR="$2" ;;
                --jar-sha256) JAR_SHA="$2" ;; --fase) FASE="$2" ;; --saida) SAIDA="$2" ;;
                --cluster-criado-apos) CRIADO_APOS="$2" ;; --servers) SERVERS="$2" ;;
                --username) USERNAME="$2" ;; --password) PASSWORD="$2" ;;
            esac
            shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        --help|-h) uso; exit 0 ;;
        *) erro_uso "argumento desconhecido '$1'" ;;
    esac
done

case "${FASE}" in piloto|noite1|noite2|reposicoes) ;; *) erro_uso "--fase deve ser piloto, noite1, noite2 ou reposicoes" ;; esac
[[ -n "${MATRIZ}" && -f "${MATRIZ}" ]] || erro_uso "--matriz <tsv> existente e obrigatoria"
[[ "${MATRIZ_SHA}" =~ ^[0-9a-f]{64}$ ]] || erro_uso "--matriz-sha256 deve ter 64 hexadecimais"
if [[ "${DRY_RUN}" -eq 0 ]]; then
    [[ -n "${JAR}" ]] || erro_uso "--jar e obrigatorio fora do --dry-run"
    [[ "${JAR_SHA}" =~ ^[0-9a-f]{64}$ ]] || erro_uso "--jar-sha256 deve ter 64 hexadecimais"
    [[ -n "${CRIADO_APOS}" ]] || erro_uso "--cluster-criado-apos e obrigatorio fora do --dry-run"
    [[ -n "${PASSWORD}" ]] || erro_uso "--password (ou RB_PASSWORD) e obrigatorio fora do --dry-run"
fi
[[ "${SAIDA}" == /* ]] || SAIDA="$(pwd)/${SAIDA}"
FASE_DIR="${SAIDA}/${FASE}"

# ------------------------------------------------------------------ utilitarios
LOG_ARQ=""
agora_ms() { date +%s%3N; }
iso_ms() { date -u -d "@$(( $1 / 1000 )).$(printf '%03d' $(( $1 % 1000 )))" +%Y-%m-%dT%H:%M:%S.%3NZ; }
log() {
    local l; l="[$(iso_ms "$(agora_ms)")] ${NOME}: $*"
    echo "${l}"
    if [[ -n "${LOG_ARQ}" ]]; then echo "${l}" >> "${LOG_ARQ}"; fi
}
json_str() {
    local s="$1"
    s="${s//\\/\\\\}"; s="${s//\"/\\\"}"; s="${s//$'\n'/\\n}"; s="${s//$'\r'/}"; s="${s//$'\t'/\\t}"
    printf '"%s"' "${s}"
}
json_ou_null() { if [[ -n "${1:-}" ]]; then printf '%s' "$1"; else printf 'null'; fi; }
json_str_ou_null() { if [[ -n "${1:-}" ]]; then json_str "$1"; else printf 'null'; fi; }
ms_para_s() {  # ms (pode ser negativo) -> s com 3 casas
    local v="$1" sinal=""
    if [[ "${v}" -lt 0 ]]; then sinal="-"; v=$(( -v )); fi
    printf '%s%d.%03d' "${sinal}" $(( v / 1000 )) $(( v % 1000 ))
}
# Sono interrompivel: o trap de TERM/INT roda na hora (o bash so trata sinal depois de um comando em
# primeiro plano, mas o 'wait' retorna assim que chega um sinal com trap).
dormir() { sleep "$1" & wait $!; }
dormir_ate() {
    local r; r=$(( $1 - $(agora_ms) ))
    if [[ "${r}" -gt 0 ]]; then dormir "$(ms_para_s "${r}")"; fi
}
sha256() { sha256sum "$1" | cut -d' ' -f1; }

# ------------------------------------------------------------------ fim da fase
gravar_resumo() {
    local rc=0
    dirs_da_fase
    ${PYTHON} -I - "${SAIDA}" "${FASE_DIR}" "${MOTIVO}" "${DIRS_FASE[@]}" <<'PY' || rc=$?
import json, sys
from pathlib import Path
saida, fase_dir, motivo, dirs = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], sys.argv[4:]
ex = []
for d in dirs:
    c = saida / d / "checagem.json"
    ch = json.loads(c.read_text(encoding="utf-8")) if c.exists() else {}
    ex.append({"dir": d, "i": ch.get("i"), "celula": ch.get("celula"), "veredito": ch.get("veredito", "SEM CHECAGEM"),
               "motivos": ch.get("motivos", [])})
cont = {}
for e in ex:
    cont[e["veredito"]] = cont.get(e["veredito"], 0) + 1
res = {"fase": fase_dir.name, "motivo_fim": motivo, "execucoes": ex, "contagem": cont}
(fase_dir / "resumo.json").write_bytes((json.dumps(res, indent=1, ensure_ascii=False) + "\n").encode("utf-8"))
md = ["# Resumo da fase %s" % fase_dir.name, "", "Fim: %s" % motivo, "", "| Execucao | Veredito | Motivos |", "|---|---|---|"]
md += ["| %s | %s | %s |" % (e["dir"], e["veredito"], "; ".join(e["motivos"]).replace("|", "/") or "-") for e in ex]
md += ["", "Contagem: " + ", ".join("%s=%d" % kv for kv in sorted(cont.items()))]
(fase_dir / "resumo.md").write_bytes(("\n".join(md) + "\n").encode("utf-8"))
PY
    [[ "${rc}" -eq 0 ]] || log "AVISO: resumo nao gravado (python rc=${rc})"
}

gravar_manifesto() {
    local rc=0 alvos=("${FASE}" matriz.tsv)
    [[ "${FASE}" != reposicoes ]] || alvos+=(reposicoes)
    local d
    dirs_da_fase
    for d in "${DIRS_FASE[@]}"; do alvos+=("${d}"); done
    ( cd "${SAIDA}" && find "${alvos[@]}" -type f ! -path "${FASE}/MANIFEST.sha256" -print0 | LC_ALL=C sort -z \
        | xargs -0 sha256sum ) > "${FASE_DIR}/MANIFEST.sha256.tmp" || rc=$?
    if [[ "${rc}" -eq 0 ]]; then mv -f "${FASE_DIR}/MANIFEST.sha256.tmp" "${FASE_DIR}/MANIFEST.sha256"
    else echo "AVISO: MANIFEST.sha256 nao gravado (rc=${rc})" >&2; fi
}

# ------------------------------------------------------------------ estado para o trap
WPID=""; INJ_PID=""; F1_ATIVA=0; F3_ATIVA=0; DEADMAN_UNIT=""; PID_ALVO=""
MOTIVO=""; FASE_CRIADA=0
readonly EXITS_PREVISTOS=" 0 2 3 4 5 6 10 130 143 "

# finalizar <exit> <motivo>: registra o motivo e sai; o trap de EXIT (limpar) faz a limpeza, o resumo, a
# ultima linha do log (exit e motivo) e o MANIFEST.sha256, nessa ordem.
finalizar() { MOTIVO="$2"; exit "$1"; }

matar() {  # matar <pid>: o processo e os filhos diretos (um 'sleep' em primeiro plano adia o TERM do bash)
    local p="$1" rc=0
    if kill -0 "${p}" 2>/dev/null; then
        if command -v pkill >/dev/null 2>&1; then
            pkill -TERM -P "${p}" 2>/dev/null || rc=$?
        fi
        kill -TERM "${p}" 2>/dev/null || rc=$?
    fi
    return 0
}

remover_netem() {  # remover_netem <pid>: tenta remover e diz se ainda ha netem (0 = ausente)
    local pid="$1" rc=0 out=""
    sudo nsenter -t "${pid}" -n tc qdisc del dev "${IFACE}" root > /dev/null 2>&1 || rc=$?
    rc=0; out="$(sudo nsenter -t "${pid}" -n tc qdisc show dev "${IFACE}" 2>&1)" || rc=$?
    if [[ "${rc}" -eq 0 && "${out}" != *netem* ]]; then return 0; fi
    return 1
}

desarmar_deadman() {  # 0 se nao sobra timer tcc-heal-rb-*
    local rc=0 resto=""
    sudo systemctl stop "${DEADMAN_UNIT}.timer" > /dev/null 2>&1 || rc=$?
    rc=0; resto="$(systemctl list-timers --all --no-legend 'tcc-heal-rb-*' 2>&1)" || rc=$?
    if [[ "${rc}" -eq 0 && -z "${resto//[[:space:]]/}" ]]; then return 0; fi
    log "timers tcc-heal-rb-* restantes (rc=${rc}): ${resto}"
    return 1
}

limpar() {
    local rc=$?
    trap - EXIT INT TERM
    if [[ -n "${INJ_PID}" ]]; then matar "${INJ_PID}"; fi
    if [[ "${F3_ATIVA}" -eq 1 && -n "${PID_ALVO}" ]]; then
        if remover_netem "${PID_ALVO}"; then
            log "trap: netem removido de ${NO_ALVO}; desarmando o dead-man ${DEADMAN_UNIT}"
            desarmar_deadman || log "trap: AVISO: dead-man nao confirmou o desarme"
        else
            log "trap: AVISO: netem continua em ${NO_ALVO}; dead-man ${DEADMAN_UNIT} MANTIDO armado"
        fi
    fi
    if [[ "${F1_ATIVA}" -eq 1 ]]; then
        local r2=0
        log "trap: F1 em curso; ${PODMAN} start ${NO_ALVO}"
        ${PODMAN} start "${NO_ALVO}" > /dev/null 2>&1 || r2=$?
        [[ "${r2}" -eq 0 ]] || log "trap: AVISO: podman start ${NO_ALVO} saiu com ${r2}"
    fi
    if [[ -n "${WPID}" ]]; then matar "${WPID}"; fi
    if [[ "${EXITS_PREVISTOS}" != *" ${rc} "* ]]; then
        MOTIVO="infra: erro inesperado do runner (exit ${rc})${MOTIVO:+; ${MOTIVO}}"
        rc=6
    fi
    MOTIVO="${MOTIVO:-interrompido (exit ${rc})}"
    if [[ "${FASE_CRIADA}" -eq 1 ]]; then gravar_resumo; fi
    log "FIM exit=${rc} motivo=${MOTIVO}"
    if [[ "${FASE_CRIADA}" -eq 1 ]]; then gravar_manifesto; fi
    exit "${rc}"
}

dirs_da_fase() {  # diretorios exec-* da fila desta fase que existem (inclusive de execucoes anteriores do runner)
    local l i cel _r d
    DIRS_FASE=()
    for l in "${FILA[@]}"; do
        read -r i cel _r <<< "${l}"
        d="$(dir_exec "${i}" "${cel}")"
        if [[ -d "${d}" ]]; then DIRS_FASE+=("${d##*/}"); fi
    done
}

# ------------------------------------------------------------------ matriz e plano
FILA=()   # linhas "i celula cenario modo falha seed"
ler_fila() {  # ler_fila <tsv> <filtro-noite ou vazio>
    local l
    while IFS= read -r l; do FILA+=("${l}"); done < <(awk -F'\t' -v n="$2" \
        'NR > 1 && (n == "" || $3 == n) {print $1, $6, $7, $8, $9, $10}' "$1")
}

montar_cmd() {  # montar_cmd <cenario> <modo> <seed> <csv-dir> -> CMD e CMD_MASC (PR §2, na ordem)
    CMD=("${JAVA_CMD[@]}" -jar "${JAR:-<jar>}" --scenario "$1" --mix "${MIX}" --replication "$2"
         --reset-state "${RESET_STATE}" --duration "${DURACAO}" --warmup-min-sec "${WARMUP_MIN}"
         --threads "${THREADS}" --ops "${OPS}" --rep "${REP}" --seed "$3" --servers "${SERVERS}"
         --username "${USERNAME}" --password "${PASSWORD}" --csv-dir "$4")
    CMD_MASC=("${CMD[@]}")
    local k
    for k in "${!CMD_MASC[@]}"; do
        if [[ "${CMD_MASC[$k]}" == "--password" ]]; then CMD_MASC[$((k + 1))]="***"; fi
    done
}

dir_exec() { printf '%s/exec-%04d-%s' "${SAIDA}" "$1" "$2"; }

imprimir_plano() {
    local t0 k=0 l i cel cen modo falha seed primeira_f3=""
    t0="$(agora_ms)"
    for l in "${FILA[@]}"; do
        read -r i cel cen modo falha seed <<< "${l}"
        montar_cmd "${cen}" "${modo}" "${seed}" "$(dir_exec "${i}" "${cel}")/.scratch"
        local inj="sem falha"
        case "${falha}" in
            F1) inj="F1: inject-crash.sh --node ${NO_ALVO} --duration ${F1_DUR} a +${INJ_OFFSET} s da ancora" ;;
            F3) inj="F3: inject-jitter.sh --node ${NO_ALVO} --iface ${IFACE} --delay ${F3_DELAY} --jitter ${F3_JITTER} --distribution ${F3_DIST} --duration ${F3_DUR} a +${INJ_OFFSET} s da ancora; dead-man ${DEADMAN_ON_ACTIVE} s"
                [[ -n "${primeira_f3}" ]] || primeira_f3="${i}" ;;
        esac
        echo "PLANO i=${i} celula=${cel} seed=${seed} hora_estimada=$(iso_ms $(( t0 + k * EST_EXEC_S * 1000 ))) ${inj}"
        echo "      ${CMD_MASC[*]}"
        if [[ "${falha}" == F3 && "${primeira_f3}" == "${i}" ]]; then
            PRIMEIRA_F3_HORA="$(iso_ms $(( t0 + k * EST_EXEC_S * 1000 )))"
        fi
        k=$((k + 1))
    done
    PRIMEIRA_F3="${primeira_f3}"
    echo "PLANO execucoes=${#FILA[@]} primeira_F3=${PRIMEIRA_F3:-nenhuma} hora_estimada_primeira_F3=${PRIMEIRA_F3_HORA:-n/a} fim_estimado=$(iso_ms $(( t0 + k * EST_EXEC_S * 1000 )))"
}
PRIMEIRA_F3=""; PRIMEIRA_F3_HORA=""

# ------------------------------------------------------------------ modo de teste (R11)
if [[ "${RB_MODO_TESTE:-}" == 1 && "${DRY_RUN}" -eq 0 ]]; then
    [[ -n "${RB_SHIM_DIR:-}" && -f "${RB_SHIM_DIR}/.rb-shim" ]] \
        || { echo "Erro: RB_MODO_TESTE=1 exige RB_SHIM_DIR com a marca .rb-shim" >&2; exit 4; }
    for palavra in ${PODMAN}; do
        cam="$(command -v "${palavra}" 2>/dev/null)" || cam=""
        [[ "${cam}" == "${RB_SHIM_DIR}"/* ]] \
            || { echo "Erro: RB_MODO_TESTE=1 com PODMAN que nao e shim de ${RB_SHIM_DIR} ('${palavra}' -> '${cam}')" >&2; exit 4; }
    done
    [[ -n "${RB_TTY_ARQUIVO:-}" ]] || { echo "Erro: RB_MODO_TESTE=1 exige RB_TTY_ARQUIVO" >&2; exit 4; }
fi

# ------------------------------------------------------------------ dry-run
if [[ "${DRY_RUN}" -eq 1 ]]; then
    if [[ "$(sha256 "${MATRIZ}")" != "${MATRIZ_SHA}" ]]; then echo "Erro: SHA-256 da matriz difere do registrado" >&2; exit 4; fi
    if [[ -n "${JAR}" && -n "${JAR_SHA}" && "$(sha256 "${JAR}")" != "${JAR_SHA}" ]]; then
        echo "Erro: SHA-256 do jar difere do registrado" >&2; exit 4
    fi
    PASSWORD="***"
    if [[ "${FASE}" == reposicoes ]]; then
        [[ -f "${SAIDA}/reposicoes/reposicoes.tsv" ]] && ler_fila "${SAIDA}/reposicoes/reposicoes.tsv" ""
    else
        ler_fila "${MATRIZ}" "${FASE}"
    fi
    echo "DRY-RUN fase=${FASE} matriz=${MATRIZ} (SHA-256 confere) duracao=${DURACAO}s warmup-min=${WARMUP_MIN}s threads=${THREADS} injecao=+${INJ_OFFSET} s da ancora 'Warm-up ate' F1=${F1_DUR}s F3=${F3_DUR}s"
    imprimir_plano
    echo "DRY-RUN: nada executado (sem java, podman, sudo, tty)"
    exit 0
fi

# ------------------------------------------------------------------ pre-voo 1: flock (reexecuta-se)
if [[ -z "${RB_SOB_FLOCK:-}" ]]; then
    command -v flock > /dev/null 2>&1 || { echo "Erro: 'flock' nao encontrado" >&2; exit 3; }
    export RB_SOB_FLOCK=1
    # -F (sem fork): o runner continua com o mesmo PID e recebe os sinais; -E 4: lock ocupado = exit 4.
    exec flock -n -F -E 4 "${LOCK}" bash "${SELF}" "${ARGS_ORIGINAIS[@]}"
fi

# ------------------------------------------------------------------ pre-voo 2: hashes, dependencias, cluster
ATUAL_MATRIZ_SHA="$(sha256 "${MATRIZ}")"
[[ "${ATUAL_MATRIZ_SHA}" == "${MATRIZ_SHA}" ]] || { echo "Erro: SHA-256 da matriz (${ATUAL_MATRIZ_SHA}) difere do registrado" >&2; exit 4; }
[[ -f "${JAR}" ]] || { echo "Erro: jar '${JAR}' ausente" >&2; exit 4; }
ATUAL_JAR_SHA="$(sha256 "${JAR}")"
[[ "${ATUAL_JAR_SHA}" == "${JAR_SHA}" ]] || { echo "Erro: SHA-256 do jar (${ATUAL_JAR_SHA}) difere do registrado" >&2; exit 4; }

for dep in "${JAVA_CMD[0]}" "${PYTHON}" curl sudo nsenter tc systemd-run systemctl flock sha256sum awk; do
    command -v "${dep}" > /dev/null 2>&1 || { echo "Erro: dependencia '${dep}' ausente" >&2; exit 3; }
done
rc_sudo=0; sudo -n true > /dev/null 2>&1 || rc_sudo=$?
[[ "${rc_sudo}" -eq 0 ]] || { echo "Erro: 'sudo -n true' falhou (sudo sem senha e obrigatorio)" >&2; exit 3; }
for dep in ${PODMAN}; do command -v "${dep}" > /dev/null 2>&1 || { echo "Erro: '${dep}' (PODMAN) ausente" >&2; exit 3; }; done
NSENTER_BIN="$(command -v nsenter)"; TC_BIN="$(command -v tc)"

podman_campo() {  # podman_campo <formato> <no>
    ${PODMAN} inspect -f "$1" "$2" 2>/dev/null
}
rc_data=0; CRIADO_APOS_S="$(date -u -d "${CRIADO_APOS}" +%s 2>/dev/null)" || rc_data=$?
[[ "${rc_data}" -eq 0 && -n "${CRIADO_APOS_S}" ]] || erro_uso "--cluster-criado-apos invalido: '${CRIADO_APOS}'"
declare -A CRIADO_EM
for no in ${NOS}; do
    est=""; rc=0; est="$(podman_campo '{{.State.Status}}' "${no}")" || rc=$?
    [[ "${rc}" -eq 0 && "${est}" == running ]] || { echo "Erro: container ${no} nao esta running (rc=${rc}, '${est}')" >&2; exit 4; }
    c=""; rc=0; c="$(podman_campo '{{.Created.Unix}}' "${no}")" || rc=$?
    [[ "${rc}" -eq 0 && "${c}" =~ ^[0-9]+$ ]] || { echo "Erro: nao foi possivel ler .Created de ${no}" >&2; exit 4; }
    [[ "${c}" -ge "${CRIADO_APOS_S}" ]] || { echo "Erro: ${no} criado em $(date -u -d "@${c}" +%FT%TZ), antes de ${CRIADO_APOS} (cluster nao e o recriado depois do S7)" >&2; exit 4; }
    CRIADO_EM[${no}]="${c}"
done

# ------------------------------------------------------------------ pre-voo 3: guarda de netns
# Padrao R4 de inject-netdegrade.sh: um container com o netns do PID 1 (rede do host) faria o netem
# atingir a NIC da VM (e o SSH). Recusa.
pid_de() {  # pid_de <no>: PID do container running (status 1 se parado)
    local p="" rc=0
    p="$(podman_campo '{{.State.Pid}}' "$1")" || rc=$?
    [[ "${rc}" -eq 0 && "${p}" =~ ^[0-9]+$ && "${p}" != 0 ]] || return 1
    echo "${p}"
}
guarda_netns() {  # guarda_netns <no>: 0 se o netns do no difere do netns do PID 1
    local p ns_no="" ns_host="" rc=0
    p="$(pid_de "$1")" || { echo "Erro: PID de $1 nao resolvido" >&2; return 1; }
    ns_no="$(sudo readlink "/proc/${p}/ns/net" 2>/dev/null)" || rc=$?
    ns_host="$(sudo readlink /proc/1/ns/net 2>/dev/null)" || rc=$?
    if [[ "${rc}" -ne 0 || -z "${ns_no}" || -z "${ns_host}" ]]; then
        echo "Erro: netns de $1 (pid ${p}) ou do PID 1 ilegivel; recusando por seguranca" >&2; return 1
    fi
    if [[ "${ns_no}" == "${ns_host}" ]]; then
        echo "Erro: $1 (pid ${p}) compartilha o netns do host (${ns_host}); recusando para nao degradar a NIC da VM" >&2
        return 1
    fi
    return 0
}
for no in ${NOS}; do guarda_netns "${no}" || exit 4; done

# ------------------------------------------------------------------ pre-voo 4: linha de base de ISPN006010
contar_ispn() {  # contar_ispn <no> [--since <unix>] -> contagem (status != 0 se podman logs falhar)
    contar_logs ISPN006010 "$@"
}
contar_logs() {  # contar_logs <codigo> <no> [--since <unix>] -> linhas com o codigo (status != 0 se podman logs falhar)
    local cod="$1" no="$2"; shift 2
    ${PODMAN} logs "$@" "${no}" 2>&1 | awk -v c="${cod}" 'index($0, c) {n++} END {print n + 0}'
}
declare -A ISPN_BASE
for no in ${NOS}; do
    rc=0; ISPN_BASE[${no}]="$(contar_ispn "${no}")" || rc=$?
    [[ "${rc}" -eq 0 ]] || { echo "Erro: podman logs ${no} falhou (rc=${rc})" >&2; exit 4; }
done

# ------------------------------------------------------------------ fila da fase
mkdir -p "${SAIDA}" "${FASE_DIR}"
FASE_CRIADA=1
LOG_ARQ="${FASE_DIR}/runner.log"
trap 'limpar' EXIT
trap 'MOTIVO="sinal TERM"; exit 143' TERM
trap 'MOTIVO="sinal INT"; exit 130' INT
log "inicio fase=${FASE} saida=${SAIDA} matriz=${MATRIZ} sha256=${ATUAL_MATRIZ_SHA} jar=${JAR} sha256=${ATUAL_JAR_SHA}"

if [[ -f "${SAIDA}/matriz.tsv" ]]; then
    [[ "$(sha256 "${SAIDA}/matriz.tsv")" == "${ATUAL_MATRIZ_SHA}" ]] || finalizar 4 "pre-voo: ${SAIDA}/matriz.tsv difere da matriz dada"
else
    cp "${MATRIZ}" "${SAIDA}/matriz.tsv"
fi

MATRIZ_CHECAR="${MATRIZ}"
if [[ "${FASE}" == reposicoes ]]; then
    REP_DIR="${SAIDA}/reposicoes"; REP_TSV="${REP_DIR}/reposicoes.tsv"
    mkdir -p "${REP_DIR}"
    rc=0; LISTA="$(${PYTHON} -I "${ANALISE}" estado --saida "${SAIDA}" --matriz "${MATRIZ}" --listar-reposicoes)" || rc=$?
    case "${rc}" in 0) ;; 20) finalizar 10 "parada: estado da campanha antes das reposicoes" ;; *) finalizar 6 "infra: rebaseline.py estado saiu com ${rc}" ;; esac
    if [[ ! -f "${REP_TSV}" ]]; then printf 'i\tfase\tnoite\trodada\tposicao\tcelula\tcenario\tmodo\tfalha\tseed\n' > "${REP_TSV}"; fi
    prox="${I_REPOSICAO_INICIAL}"
    for d in "${SAIDA}"/exec-*; do
        [[ -d "${d}" ]] || continue
        b="${d##*/exec-}"; n=$((10#${b%%-*}))
        if [[ "${n}" -ge "${I_REPOSICAO_INICIAL}" && "${n}" -lt "${I_PILOTO}" && "${n}" -ge "${prox}" ]]; then prox=$((n + 1)); fi
    done
    while IFS=$'\t' read -r i_tsv _; do
        if [[ "${i_tsv}" =~ ^[0-9]+$ && "${i_tsv}" -ge "${prox}" ]]; then prox=$((i_tsv + 1)); fi
    done < "${REP_TSV}"
    pos="$(awk 'NR > 1' "${REP_TSV}" | wc -l)"
    while IFS=$'\t' read -r cel i_rec; do
        [[ -n "${cel}" ]] || continue
        if awk -F'\t' -v c="${cel}" 'NR > 1 && $6 == c {achou = 1} END {exit achou ? 0 : 1}' "${REP_TSV}"; then continue; fi
        IFS=- read -r cen modo falha <<< "${cel}"
        printf '%s\treposicao\treposicoes\t-\t%s\t%s\t%s\t%s\t%s\t%s\n' "${prox}" "${pos}" "${cel}" "${cen}" "${modo}" "${falha}" \
            "$((S0 + PASSO_SEMENTE * prox))" >> "${REP_TSV}"
        log "reposicao: ${cel} (recusa i=${i_rec}) -> i=${prox}"
        prox=$((prox + 1)); pos=$((pos + 1))
    done <<< "${LISTA}"
    REP_SHA="$(sha256 "${REP_TSV}")"
    echo "reposicoes.tsv SHA-256 ${REP_SHA}"
    log "reposicoes.tsv SHA-256 ${REP_SHA}"
    ler_fila "${REP_TSV}" ""
    MATRIZ_CHECAR="${REP_TSV}"
else
    ler_fila "${MATRIZ}" "${FASE}"
fi
[[ "${#FILA[@]}" -gt 0 ]] || finalizar 0 "fase sem execucoes"

# ------------------------------------------------------------------ pre-voo 5: ambiente.md
cmd_txt() {  # saida de um comando informativo (falha nao para o runner)
    local rc=0 o=""
    o="$("$@" 2>&1)" || rc=$?
    printf '%s' "${o}"
    [[ "${rc}" -eq 0 ]] || printf '\n(falhou: exit %s)' "${rc}"
}
{
    echo "## Pre-voo $(iso_ms "$(agora_ms)") (fase ${FASE})"
    echo
    echo '```'
    echo "date -u: $(date -u)"
    echo "uname -a: $(cmd_txt uname -a)"
    echo "java -version:"; cmd_txt "${JAVA_CMD[@]}" -version; echo
    echo "podman version:"; cmd_txt ${PODMAN} version; echo
    echo "ip -br addr:"; cmd_txt ip -br addr; echo
    echo "ip -br link (MAC do eth0):"; cmd_txt ip -br link; echo
    echo "python3: $(cmd_txt "${PYTHON}" --version)"
    echo "jar: ${JAR} sha256=${ATUAL_JAR_SHA}"
    echo "matriz: ${MATRIZ} sha256=${ATUAL_MATRIZ_SHA}"
    for no in ${NOS}; do echo "${no}: criado=$(date -u -d "@${CRIADO_EM[${no}]}" +%FT%TZ) ISPN006010_linha_de_base=${ISPN_BASE[${no}]}"; done
    echo "cluster criado depois de: ${CRIADO_APOS}"
    echo "constantes: duracao=${DURACAO} warmup_min=${WARMUP_MIN} threads=${THREADS} injecao=+${INJ_OFFSET}s F1=${F1_DUR}s F3=${F3_DELAY}/${F3_JITTER}/${F3_DIST}/${F3_DUR}s no=${NO_ALVO} iface=${IFACE} dead-man=${DEADMAN_ON_ACTIVE}s"
    echo '```'
    echo
    echo "Execucoes da fase (ordem da matriz):"
    echo
    for l in "${FILA[@]}"; do echo "- ${l}"; done
    echo
} >> "${FASE_DIR}/ambiente.md"

# ------------------------------------------------------------------ gates (R6)
GATES_JSON=""
TTY_N=0
INICIO_RUNNER="$(iso_ms "$(agora_ms)")"
gravar_gates() {
    ${PYTHON} -I - "${FASE_DIR}/gates.json" "${INICIO_RUNNER}" "[${GATES_JSON}]" <<'PY'
import json, sys
from pathlib import Path
p, inicio, gates = Path(sys.argv[1]), sys.argv[2], json.loads(sys.argv[3])
d = json.loads(p.read_text(encoding="utf-8")) if p.exists() else {"execucoes_do_runner": []}
ex = [e for e in d["execucoes_do_runner"] if e.get("inicio") != inicio]
ex.append({"inicio": inicio, "gates": gates})
d["execucoes_do_runner"] = ex
p.write_bytes((json.dumps(d, indent=1, ensure_ascii=False) + "\n").encode("utf-8"))
PY
}
ler_gate() {  # ler_gate <tipo> <esperado> [i]
    local tipo="$1" esperado="$2" linha="" rc=0
    if [[ "${RB_MODO_TESTE:-}" == 1 ]]; then
        TTY_N=$((TTY_N + 1))
        linha="$(sed -n "${TTY_N}p" "${RB_TTY_ARQUIVO}")"
    else
        { printf 'Digite exatamente: %s
> ' "${esperado}" > /dev/tty; } 2> /dev/null || log "AVISO: /dev/tty indisponivel para o prompt"
        IFS= read -r -t "${GATE_TIMEOUT_S}" linha < /dev/tty || rc=$?
        if [[ "${rc}" -ne 0 ]]; then
            log "leitura de /dev/tty falhou (gate ${tipo}, rc=${rc})"
            linha=""
        fi
    fi
    local agora; agora="$(iso_ms "$(agora_ms)")"
    [[ -z "${GATES_JSON}" ]] || GATES_JSON+=","
    GATES_JSON+="{\"tipo\":$(json_str "${tipo}"),\"i\":$(json_ou_null "${3:-}"),\"esperado\":$(json_str "${esperado}"),\"digitado\":$(json_str "${linha}"),\"utc\":$(json_str "${agora}"),\"aceito\":$([[ "${linha}" == "${esperado}" ]] && echo true || echo false)}"
    gravar_gates
    if [[ "${linha}" != "${esperado}" ]]; then
        log "go recusado (${tipo}): digitado '${linha}', esperado '${esperado}'"
        finalizar 5 "go nao dado (${tipo})"
    fi
    log "go aceito (${tipo}): '${linha}' em ${agora}"
}

PASSWORD_REAL="${PASSWORD}"
imprimir_plano | tee -a "${LOG_ARQ}"
HOJE="$(date +%F)"
log "GATE noite pedido: aguardando 'go ${FASE} ${HOJE}' em /dev/tty"
ler_gate noite "go ${FASE} ${HOJE}"

# ------------------------------------------------------------------ saude do cluster
AM_OK=0; AM_JSON=""
amostra_saude() {  # define AM_OK (1 = 3 nos HEALTHY nos 3 endpoints) e AM_JSON; com "netem" checa os 3 netns
    local ok=1 partes="" srv host porta corpo rc st nn no p out
    for srv in ${SERVERS//,/ }; do
        host="${srv%:*}"; porta="${srv##*:}"
        rc=0; corpo="$(curl -s --max-time 5 --digest -u "${USERNAME}:${PASSWORD_REAL}" \
            "http://${host}:${porta}/rest/v2/cache-managers/default/health")" || rc=$?
        st=""; nn=""
        if [[ "${corpo}" =~ \"health_status\":\"([A-Z_]+)\" ]]; then st="${BASH_REMATCH[1]}"; fi
        if [[ "${corpo}" =~ \"number_of_nodes\":([0-9]+) ]]; then nn="${BASH_REMATCH[1]}"; fi
        if [[ "${rc}" -ne 0 || "${st}" != HEALTHY || "${nn}" != 3 ]]; then ok=0; fi
        partes+="${partes:+,}{\"porta\":${porta},\"curl_rc\":${rc},\"health_status\":$(json_str "${st}"),\"number_of_nodes\":$(json_ou_null "${nn}")}"
    done
    local netem=""
    if [[ "${1:-}" == netem ]]; then
        for no in ${NOS}; do
            if p="$(pid_de "${no}")"; then
                rc=0; out="$(sudo nsenter -t "${p}" -n tc qdisc show dev "${IFACE}" 2>&1)" || rc=$?
                if [[ "${rc}" -ne 0 || "${out}" == *netem* ]]; then ok=0; netem+="${netem:+,}$(json_str "${no}")"; fi
            else
                ok=0; netem+="${netem:+,}$(json_str "${no}:parado")"
            fi
        done
    fi
    AM_OK="${ok}"
    AM_JSON="{\"utc\":$(json_str "$(iso_ms "$(agora_ms)")"),\"ok\":$([[ ${ok} -eq 1 ]] && echo true || echo false),\"endpoints\":[${partes}],\"netem_em\":[${netem}]}"
}

portao_k1() {  # portao_k1 <dir>: 3 nos HEALTHY e sem netem em todas as amostras dos ultimos 60 s (ate 10 min)
    local d="$1" t0 agora inicio_bom="" amostras="" ok=0
    t0="$(agora_ms)"
    while :; do
        amostra_saude netem
        amostras+="${amostras:+,}${AM_JSON}"
        agora="$(agora_ms)"
        if [[ "${AM_OK}" -eq 1 ]]; then
            [[ -n "${inicio_bom}" ]] || inicio_bom="${agora}"
            if [[ $(( agora - inicio_bom )) -ge $(( K1_JANELA_S * 1000 )) ]]; then ok=1; break; fi
        else
            inicio_bom=""
        fi
        if [[ $(( agora - t0 )) -ge $(( K1_TIMEOUT_S * 1000 )) ]]; then break; fi
        dormir "${K1_PERIODO_S}"
    done
    printf '{"ok":%s,"janela_s":%d,"periodo_s":%d,"timeout_s":%d,"inicio_utc":%s,"fim_utc":%s,"amostras":[%s]}\n' \
        "$([[ ${ok} -eq 1 ]] && echo true || echo false)" "${K1_JANELA_S}" "${K1_PERIODO_S}" "${K1_TIMEOUT_S}" \
        "$(json_str "$(iso_ms "${t0}")")" "$(json_str "$(iso_ms "$(agora_ms)")")" "${amostras}" > "${d}/portao.json"
    [[ "${ok}" -eq 1 ]]
}

esperar_cluster() {  # esperar_cluster: 3 nos HEALTHY em ate 180 s depois do F1; define VOLTA_S
    local t0 agora
    t0="$(agora_ms)"
    while :; do
        amostra_saude
        agora="$(agora_ms)"
        if [[ "${AM_OK}" -eq 1 ]]; then VOLTA_S="$(ms_para_s $(( agora - t0 )))"; return 0; fi
        if [[ $(( agora - t0 )) -ge $(( F1_VOLTA_TIMEOUT_S * 1000 )) ]]; then VOLTA_S="$(ms_para_s $(( agora - t0 )))"; return 1; fi
        dormir "${F1_VOLTA_PERIODO_S}"
    done
}

tem_ancora() {  # tem_ancora <stdout.log>: a linha "Warm-up ate +" ja apareceu (leitura sem fork)
    local l
    [[ -f "$1" ]] || return 1
    while IFS= read -r l || [[ -n "${l}" ]]; do
        if [[ "${l}" =~ ${ANCORA_RE} ]]; then return 0; fi
    done < "$1"
    return 1
}

# ------------------------------------------------------------------ uma execucao
gravar_execucao() {
    local d="$1"
    local cmdj="" a
    for a in "${CMD_MASC[@]}"; do cmdj+="${cmdj:+,}$(json_str "${a}")"; done
    cat > "${d}/execucao.json" <<EOF
{
 "i": ${E_I},
 "celula": $(json_str "${E_CEL}"),
 "fase": $(json_str "${E_FASE}"),
 "cenario": $(json_str "${E_CEN}"),
 "modo": $(json_str "${E_MODO}"),
 "falha": $(json_str "${E_FALHA}"),
 "seed": ${E_SEED},
 "jar_sha256": $(json_str "${ATUAL_JAR_SHA}"),
 "matriz_sha256": $(json_str "$(sha256 "${MATRIZ_CHECAR}")"),
 "comando": [${cmdj}],
 "constantes": {"duracao_s": ${DURACAO}, "warmup_min_s": ${WARMUP_MIN}, "threads": ${THREADS}, "inj_offset_s": ${INJ_OFFSET}, "f1_dur_s": ${F1_DUR}, "f3_dur_s": ${F3_DUR}, "f3": "${F3_DELAY} ${F3_JITTER} ${F3_DIST}", "no_alvo": "${NO_ALVO}", "deadman_s": ${DEADMAN_ON_ACTIVE}},
 "lancamento_utc": $(json_str_ou_null "${E_LANC_UTC}"),
 "lancamento_ms": $(json_ou_null "${E_LANC_MS}"),
 "ancora_utc": $(json_str_ou_null "${E_ANC_UTC}"),
 "ancora_ms": $(json_ou_null "${E_ANC_MS}"),
 "ancora_desde_lancamento_s": $(json_ou_null "${E_ANC_DESDE}"),
 "disparo_utc": $(json_str_ou_null "${E_DISP_UTC}"),
 "disparo_ms": $(json_ou_null "${E_DISP_MS}"),
 "inj_inicio_offset_s": $(json_ou_null "${E_OFFSET}"),
 "fim_utc": $(json_str_ou_null "${E_FIM_UTC}"),
 "workload_exit": $(json_ou_null "${E_WEXIT}"),
 "injetor_exit": $(json_ou_null "${E_IEXIT}"),
 "verificador_exit": $(json_ou_null "${E_VEXIT}"),
 "recusa_k6": $(json_str_ou_null "${E_RECUSA_K6}"),
 "netem_amostras": [${E_NETEM}],
 "netem_pos": ${E_NETEM_POS:-null},
 "cluster_pos_f1": ${E_CLUSTER_F1:-null},
 "deadman": ${E_DEADMAN:-null}
}
EOF
}

gravar_ispn() {  # K8 (DT-013): por no, total desde a criacao, vida atual (--since StartedAt) e vidas (ISPN080001)
    local d="$1" no rc tot desde ini vidas partes="" partes2="" partes3="" base=""
    for no in ${NOS}; do
        rc=0; tot="$(contar_ispn "${no}")" || rc=$?
        [[ "${rc}" -eq 0 && "${tot}" =~ ^[0-9]+$ ]] || finalizar 6 "infra: podman logs ${no} falhou (rc=${rc}); K8 ilegivel (DT-013)"
        rc=0; vidas="$(contar_logs ISPN080001 "${no}")" || rc=$?
        [[ "${rc}" -eq 0 && "${vidas}" =~ ^[0-9]+$ ]] || finalizar 6 "infra: contagem de ISPN080001 de ${no} falhou (rc=${rc}); K8 ilegivel (DT-013)"
        rc=0; ini="$(podman_campo '{{.State.StartedAt.Unix}}' "${no}")" || rc=$?
        [[ "${rc}" -eq 0 && "${ini}" =~ ^[0-9]+$ ]] || finalizar 6 "infra: StartedAt de ${no} ilegivel (rc=${rc}); K8 ilegivel (DT-013)"
        rc=0; desde="$(contar_ispn "${no}" --since "${ini}")" || rc=$?
        [[ "${rc}" -eq 0 && "${desde}" =~ ^[0-9]+$ ]] || finalizar 6 "infra: podman logs --since ${ini} ${no} falhou (rc=${rc}); K8 ilegivel (DT-013)"
        partes+="${partes:+,}\"${no}\":${tot}"
        partes2+="${partes2:+,}\"${no}\":${desde}"
        partes3+="${partes3:+,}\"${no}\":${vidas}"
        base+="${base:+,}\"${no}\":${ISPN_BASE[${no}]}"
    done
    printf '{"por_no":{%s},"por_no_desde_ultimo_start":{%s},"vidas_por_no":{%s},"linha_de_base_pre_voo":{%s},"nota":"K8 (DT-013): por no, <= 1 ISPN006010 na vida atual (podman logs --since StartedAt) e total desde a criacao do container <= vidas (linhas ISPN080001)"}\n' \
        "${partes}" "${partes2}" "${partes3}" "${base}" > "${d}/ispn006010.json"
}

GATE_F3_DADO=0
rodar_execucao() {  # rodar_execucao "<i> <celula> <cenario> <modo> <falha> <seed>"
    read -r E_I E_CEL E_CEN E_MODO E_FALHA E_SEED <<< "$1"
    E_FASE="$(awk -F'\t' -v i="${E_I}" 'NR > 1 && $1 == i {print $2}' "${MATRIZ_CHECAR}")"
    E_LANC_UTC=""; E_LANC_MS=""; E_ANC_UTC=""; E_ANC_MS=""; E_ANC_DESDE=""; E_DISP_UTC=""; E_DISP_MS=""
    E_OFFSET=""; E_FIM_UTC=""; E_WEXIT=""; E_IEXIT=""; E_VEXIT=""; E_RECUSA_K6=""; E_NETEM=""
    E_NETEM_POS=""; E_CLUSTER_F1=""; E_DEADMAN=""
    local d rc
    d="$(dir_exec "${E_I}" "${E_CEL}")"
    if [[ -d "${d}" ]]; then
        if [[ -f "${d}/checagem.json" ]]; then log "exec i=${E_I} ${E_CEL}: ja executada e checada; pulando"; return 0; fi
        finalizar 4 "pre-voo: ${d} existe sem checagem.json (execucao interrompida); decisao manual do Gestor"
    fi
    # C2: o gate f3 vem ANTES do diretorio da execucao e do K1. Um go f3 errado sai com 5 sem deixar
    # exec-<i>, e o K1 (>= 60 s) fica imediatamente antes do workload mesmo que o Gestor demore.
    if [[ "${E_FALHA}" == F3 && "${GATE_F3_DADO}" -eq 0 ]]; then
        log "GATE f3 pedido: primeira F3 da fase (i=${E_I}); aguardando 'go f3 ${E_I}' em /dev/tty (depois vem o K1, >= 60 s)"
        ler_gate f3 "go f3 ${E_I}" "${E_I}"
        GATE_F3_DADO=1
    fi
    mkdir -p "${d}"
    log "exec i=${E_I} ${E_CEL} seed=${E_SEED}: portao K1"
    if ! portao_k1 "${d}"; then
        finalizar 10 "parada (e): portao K1 nao passou em ${K1_TIMEOUT_S} s antes de i=${E_I}"
    fi
    local scratch="${d}/.scratch"
    mkdir -p "${scratch}"
    PASSWORD="${PASSWORD_REAL}"
    montar_cmd "${E_CEN}" "${E_MODO}" "${E_SEED}" "${scratch}"
    E_LANC_MS="$(agora_ms)"; E_LANC_UTC="$(iso_ms "${E_LANC_MS}")"
    log "exec i=${E_I}: lancando workload i=${E_I} (${CMD_MASC[*]})"
    "${CMD[@]}" > "${d}/stdout.log" 2>&1 &
    WPID=$!

    # ancora: a linha "Warm-up ate +" (inicio do aquecimento); polling <= 200 ms; timeout 120 s
    local agora
    while :; do
        if tem_ancora "${d}/stdout.log"; then
            E_ANC_MS="$(agora_ms)"; E_ANC_UTC="$(iso_ms "${E_ANC_MS}")"
            E_ANC_DESDE="$(ms_para_s $(( E_ANC_MS - E_LANC_MS )))"
            break
        fi
        if ! kill -0 "${WPID}" 2>/dev/null; then E_RECUSA_K6="sem ancora"; break; fi
        agora="$(agora_ms)"
        if [[ $(( agora - E_LANC_MS )) -ge $(( ANCORA_TIMEOUT_S * 1000 )) ]]; then
            E_RECUSA_K6="sem ancora"
            log "exec i=${E_I}: sem ancora em ${ANCORA_TIMEOUT_S} s; matando o workload (recusa K6)"
            matar "${WPID}"
            break
        fi
        dormir "${ANCORA_POLL_S}"
    done
    [[ -z "${E_ANC_MS}" ]] || log "exec i=${E_I}: ancora a +${E_ANC_DESDE} s do lancamento"

    if [[ -n "${E_ANC_MS}" && "${E_FALHA}" != none ]]; then
        local alvo=$(( E_ANC_MS + INJ_OFFSET * 1000 ))
        if [[ "${E_FALHA}" == F3 ]]; then
            guarda_netns "${NO_ALVO}" || finalizar 6 "infra: guarda de netns de ${NO_ALVO} falhou antes da F3"
            PID_ALVO="$(pid_de "${NO_ALVO}")" || finalizar 6 "infra: PID de ${NO_ALVO} nao resolvido antes da F3"
        fi
        dormir_ate "${alvo}"
        if [[ "${E_FALHA}" == F1 ]]; then
            E_DISP_MS="$(agora_ms)"
            bash "${INJ_CRASH}" --node "${NO_ALVO}" --duration "${F1_DUR}" > "${d}/injetor.log" 2>&1 &
            INJ_PID=$!; F1_ATIVA=1
        else
            # dead-man ANTES do netem: se o runner e o injetor morrerem, o systemd remove o netem
            DEADMAN_UNIT="tcc-heal-rb-${E_I}"
            rc=0
            sudo systemd-run --unit "${DEADMAN_UNIT}" --on-active="${DEADMAN_ON_ACTIVE}s" \
                "${NSENTER_BIN}" -t "${PID_ALVO}" -n "${TC_BIN}" qdisc del dev "${IFACE}" root > "${d}/deadman.log" 2>&1 || rc=$?
            if [[ "${rc}" -ne 0 ]]; then
                E_DEADMAN="{\"unit\":$(json_str "${DEADMAN_UNIT}"),\"armado\":false,\"rc\":${rc}}"
                gravar_execucao "${d}"
                finalizar 6 "infra: dead-man ${DEADMAN_UNIT} nao armou (systemd-run rc=${rc}); netem nao aplicado"
            fi
            F3_ATIVA=1
            E_DEADMAN="{\"unit\":$(json_str "${DEADMAN_UNIT}"),\"armado\":true,\"on_active_s\":${DEADMAN_ON_ACTIVE}}"
            E_DISP_MS="$(agora_ms)"
            bash "${INJ_JITTER}" --node "${NO_ALVO}" --iface "${IFACE}" --delay "${F3_DELAY}" --jitter "${F3_JITTER}" \
                --distribution "${F3_DIST}" --duration "${F3_DUR}" > "${d}/injetor.log" 2>&1 &
            INJ_PID=$!
        fi
        E_DISP_UTC="$(iso_ms "${E_DISP_MS}")"
        E_OFFSET="$(ms_para_s $(( E_DISP_MS - E_ANC_MS )))"
        log "exec i=${E_I}: ${E_FALHA} disparada a +${E_OFFSET} s da ancora"
        if [[ "${E_FALHA}" == F3 ]]; then
            local a out netem
            for a in ${NETEM_AMOSTRAS_S}; do
                dormir_ate $(( E_ANC_MS + a * 1000 ))
                rc=0; out="$(sudo nsenter -t "${PID_ALVO}" -n tc qdisc show dev "${IFACE}" 2>&1)" || rc=$?
                netem=false; if [[ "${rc}" -eq 0 && "${out}" == *netem* ]]; then netem=true; fi
                E_NETEM+="${E_NETEM:+,}{\"alvo_s\":${a},\"offset_s\":$(ms_para_s $(( $(agora_ms) - E_ANC_MS ))),\"netem\":${netem},\"rc\":${rc},\"saida\":$(json_str "${out}")}"
            done
        fi
        rc=0; wait "${INJ_PID}" || rc=$?
        E_IEXIT="${rc}"; INJ_PID=""
        log "exec i=${E_I}: injetor saiu com ${rc}"
        if [[ "${E_FALHA}" == F1 ]]; then
            F1_ATIVA=0
            local VOLTA_S=""
            if esperar_cluster; then
                E_CLUSTER_F1="{\"ok\":true,\"espera_s\":${VOLTA_S}}"
            else
                E_CLUSTER_F1="{\"ok\":false,\"espera_s\":${VOLTA_S}}"
                gravar_execucao "${d}"
                finalizar 10 "parada (e): cluster nao voltou a 3 nos HEALTHY em ${F1_VOLTA_TIMEOUT_S} s depois do F1 de i=${E_I}"
            fi
        else
            local residual=false extra=false
            rc=0; out="$(sudo nsenter -t "${PID_ALVO}" -n tc qdisc show dev "${IFACE}" 2>&1)" || rc=$?
            if [[ "${rc}" -ne 0 || "${out}" == *netem* ]]; then
                extra=true
                log "exec i=${E_I}: netem ainda presente depois do injetor; removendo uma vez"
                if ! remover_netem "${PID_ALVO}"; then residual=true; fi
            fi
            E_NETEM_POS="{\"netem\":${residual},\"remocao_extra\":${extra}}"
            if [[ "${residual}" == true ]]; then
                gravar_execucao "${d}"
                finalizar 6 "parada (f): netem residual em ${NO_ALVO} que o runner nao removeu; dead-man ${DEADMAN_UNIT} mantido"
            fi
            if ! desarmar_deadman; then
                gravar_execucao "${d}"
                finalizar 6 "infra: dead-man ${DEADMAN_UNIT} nao desarmou"
            fi
            F3_ATIVA=0
        fi
        if [[ " ${INJ_EXITS_DOCUMENTADOS} " != *" ${E_IEXIT} "* ]]; then
            rc=0; wait "${WPID}" || rc=$?
            E_WEXIT="${rc}"; WPID=""
            gravar_execucao "${d}"
            finalizar 6 "infra: injetor de i=${E_I} saiu com exit inesperado ${E_IEXIT}"
        fi
    fi
    rc=0; wait "${WPID}" || rc=$?
    E_WEXIT="${rc}"; WPID=""
    E_FIM_UTC="$(iso_ms "$(agora_ms)")"
    log "exec i=${E_I}: workload saiu com ${E_WEXIT}"

    # pos: brutos para o diretorio da execucao (nada e apagado)
    local f
    for f in "${scratch}"/* "${scratch}"/.[!.]*; do
        [[ -e "${f}" ]] || continue
        mv -n "${f}" "${d}/"
    done
    rmdir "${scratch}" 2> /dev/null || log "exec i=${E_I}: AVISO: ${scratch} nao ficou vazio"
    if [[ -f "${d}/rep-001.csv" ]]; then
        rc=0
        "${JAVA_CMD[@]}" -cp "${JAR}" "${CLASSE_VERIFICADOR}" --csv "${d}/rep-001.csv" --scenario "${E_CEN}" \
            --saida "${d}/sanidade.json" > "${d}/verificador.out" 2>&1 || rc=$?
        E_VEXIT="${rc}"
    fi
    gravar_ispn "${d}"
    gravar_execucao "${d}"

    rc=0; ${PYTHON} -I "${ANALISE}" checar --exec "${d}" --matriz "${MATRIZ_CHECAR}" >> "${LOG_ARQ}" 2>&1 || rc=$?
    case "${rc}" in
        0) log "exec i=${E_I}: checar ACEITA" ;;
        10) log "exec i=${E_I}: checar RECUSADA (segue)" ;;
        20) finalizar 10 "parada (c): checar PARADA em i=${E_I}" ;;
        *) finalizar 6 "infra: rebaseline.py checar saiu com ${rc} em i=${E_I}" ;;
    esac
    rc=0; ${PYTHON} -I "${ANALISE}" estado --saida "${SAIDA}" --matriz "${MATRIZ}" >> "${LOG_ARQ}" 2>&1 || rc=$?
    case "${rc}" in
        0) ;;
        20) finalizar 10 "parada: rebaseline.py estado (paradas a..d, R-S3) depois de i=${E_I}" ;;
        *) finalizar 6 "infra: rebaseline.py estado saiu com ${rc} depois de i=${E_I}" ;;
    esac
}

# ------------------------------------------------------------------ laco da fase
for linha in "${FILA[@]}"; do
    rodar_execucao "${linha}"
done
finalizar 0 "fase ${FASE} concluida"
