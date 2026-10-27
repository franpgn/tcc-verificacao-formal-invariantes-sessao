#!/usr/bin/env bash
#
# collect-resources.sh
#
# Coletor de recursos para o proxy de energia (B-37; contrato "recursos/1",
# docs/contrato-recursos-v1.md). Amostra, a cada --intervalo-ms, a CPU
# acumulada e a memoria de:
#   cg:<conteiner>  cgroup v2 do conteiner (cpu.stat usage_usec, memory.current)
#   pid:<rotulo>    um processo dado por --pid rotulo=PID (/proc/<pid>/stat e status)
#   vm              a maquina inteira (/proc/stat, /proc/meminfo), com steal
# e, ao fim, uma linha "coletor" com a CPU do proprio coletor (filhos inclusos).
#
# NAO e chamado por run-rebaseline.sh (o rebaseline nao coleta CPU; DT-009).
# Quem integra o coletor a uma bateria e o runner da T-F.
#
# Custo de medir e desfecho: o caminho quente (laco + leituras) so usa
# builtins do bash (read com redirecionamento, printf -v, aritmetica) e o
# relogio EPOCHREALTIME; os unicos processos criados por ciclo sao o sleep e,
# a cada --podman-stats-s, o "podman stats" de verificacao cruzada. Cadencia
# por prazo absoluto: prazo_k = inicio + k * intervalo (sem deriva acumulada).
#
# Uso:
#   collect-resources.sh --saida DIR --rep N --containers "isn1 isn2 isn3"
#       [--pid rotulo=PID]... [--intervalo-ms 1000] [--podman-stats-s 10]
#       [--duracao-s S | --max-amostras N] [--dry-run] [--help]
#
# Ambiente:
#   PODMAN               binario do podman (padrao "podman"; na VM: "sudo podman")
#   COLETOR_RAIZ_CGROUP  raiz do cgroup v2 (padrao /sys/fs/cgroup)
#   COLETOR_RAIZ_PROC    raiz do procfs (padrao /proc)
#   COLETOR_AGORA_NS_CMD comando que imprime o instante em ns (SO TESTE; sem ele:
#                        EPOCHREALTIME, sem fork)
#   COLETOR_SLEEP_CMD    comando de espera, recebe segundos decimais (SO TESTE;
#                        sem ele: sleep)
#
# Saidas (NNN = --rep com 3 digitos):
#   DIR/recursos-rep-NNN.csv           append a cada ciclo, sem temporario
#   DIR/recursos-meta-rep-NNN.json     so no fim (ausencia = coleta interrompida)
#   DIR/recursos-podman-rep-NNN.jsonl  se --podman-stats-s > 0
#
# Exit codes:
#   0  coleta encerrada (sinal, --duracao-s ou --max-amostras) ou --dry-run/--help
#   2  uso invalido
#   3  conteiner/cgroup ou PID inexistente no inicio (nada e medido)
#   4  saida ja existente (nunca sobrescreve brutos)
#   1  erro generico (ex.: bash < 5, diretorio de saida nao gravavel)
set -uo pipefail

NOME="${0##*/}"
VERSAO_COLETOR="1.0.0"
CONTRATO="recursos/1"
CABECALHO="t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado"

PODMAN="${PODMAN:-podman}"
RAIZ_CGROUP="${COLETOR_RAIZ_CGROUP:-/sys/fs/cgroup}"
RAIZ_PROC="${COLETOR_RAIZ_PROC:-/proc}"

uso() {
    cat <<EOF
Uso: ${NOME} --saida DIR --rep N --containers "isn1 isn2 isn3"
       [--pid rotulo=PID]... [--intervalo-ms 1000] [--podman-stats-s 10]
       [--duracao-s S | --max-amostras N] [--dry-run] [--help]

  --saida DIR          diretorio da celula (criado se nao existir)
  --rep N              numero da repeticao (arquivos *-rep-NNN.*)
  --containers "..."   conteineres (cgroup resolvido por podman inspect)
  --pid rotulo=PID     processo adicional (repetivel), fonte "pid:rotulo"
  --intervalo-ms MS    periodo de amostragem (padrao 1000, >= 1)
  --podman-stats-s S   periodo do "podman stats" de verificacao (padrao 10; 0 desliga)
  --duracao-s S        encerra apos S segundos (exclusivo com --max-amostras)
  --max-amostras N     encerra apos N amostras (exclusivo com --duracao-s)
  --dry-run            mostra o plano; nao le contadores nem escreve nada
  --help               esta ajuda

Encerramento: SIGTERM/SIGINT, --duracao-s ou --max-amostras (exit 0 em todos).
Contrato: docs/contrato-recursos-v1.md (recursos/1).
EOF
}

erro_uso() { echo "${NOME}: $*" >&2; echo "Use --help." >&2; exit 2; }
e_inteiro() { [[ "$1" =~ ^[0-9]+$ ]]; }

if (( BASH_VERSINFO[0] < 5 )); then
    echo "${NOME}: requer bash >= 5 (EPOCHREALTIME); encontrado ${BASH_VERSION}" >&2
    exit 1
fi

# ---------------------------------------------------------------- argumentos
SAIDA=""
REP=""
CONTAINERS_STR=""
CONTAINERS_DADO=0
declare -a PID_ROTULOS=() PID_VALORES=()
INTERVALO_MS=1000
PODMAN_STATS_S=10
DURACAO_S=""
MAX_AMOSTRAS=""
DRY_RUN=0

precisa_valor() { [[ $# -ge 2 && -n "${2-}" ]] || erro_uso "$1 exige valor"; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --saida)          precisa_valor "$@"; SAIDA="$2"; shift 2 ;;
        --rep)            precisa_valor "$@"; REP="$2"; shift 2 ;;
        --containers)     [[ $# -ge 2 ]] || erro_uso "--containers exige valor"
                          CONTAINERS_STR="$2"; CONTAINERS_DADO=1; shift 2 ;;
        --pid)            precisa_valor "$@"
                          [[ "$2" =~ ^([A-Za-z0-9_.-]+)=([0-9]+)$ ]] \
                              || erro_uso "--pid espera rotulo=PID (rotulo [A-Za-z0-9_.-]); recebido '$2'"
                          PID_ROTULOS+=("${BASH_REMATCH[1]}"); PID_VALORES+=("${BASH_REMATCH[2]}")
                          shift 2 ;;
        --intervalo-ms)   precisa_valor "$@"; INTERVALO_MS="$2"; shift 2 ;;
        --podman-stats-s) precisa_valor "$@"; PODMAN_STATS_S="$2"; shift 2 ;;
        --duracao-s)      precisa_valor "$@"; DURACAO_S="$2"; shift 2 ;;
        --max-amostras)   precisa_valor "$@"; MAX_AMOSTRAS="$2"; shift 2 ;;
        --dry-run)        DRY_RUN=1; shift ;;
        -h|--help)        uso; exit 0 ;;
        *)                erro_uso "argumento desconhecido: $1" ;;
    esac
done

[[ -n "${SAIDA}" ]] || erro_uso "--saida e obrigatorio"
[[ -n "${REP}" ]] || erro_uso "--rep e obrigatorio"
e_inteiro "${REP}" || erro_uso "--rep deve ser inteiro >= 0; recebido '${REP}'"
[[ "${CONTAINERS_DADO}" -eq 1 ]] || erro_uso "--containers e obrigatorio"
read -ra CONTAINERS <<< "${CONTAINERS_STR}"
[[ ${#CONTAINERS[@]} -ge 1 ]] || erro_uso "--containers vazio"
for c in "${CONTAINERS[@]}"; do
    [[ "${c}" =~ ^[A-Za-z0-9_.-]+$ ]] || erro_uso "nome de conteiner invalido: '${c}'"
done
{ e_inteiro "${INTERVALO_MS}" && (( 10#${INTERVALO_MS} >= 1 )); } \
    || erro_uso "--intervalo-ms deve ser inteiro >= 1; recebido '${INTERVALO_MS}'"
e_inteiro "${PODMAN_STATS_S}" || erro_uso "--podman-stats-s deve ser inteiro >= 0; recebido '${PODMAN_STATS_S}'"
if [[ -n "${DURACAO_S}" && -n "${MAX_AMOSTRAS}" ]]; then
    erro_uso "--duracao-s e --max-amostras sao exclusivos"
fi
if [[ -n "${DURACAO_S}" ]]; then
    { e_inteiro "${DURACAO_S}" && (( 10#${DURACAO_S} >= 1 )); } \
        || erro_uso "--duracao-s deve ser inteiro >= 1; recebido '${DURACAO_S}'"
fi
if [[ -n "${MAX_AMOSTRAS}" ]]; then
    { e_inteiro "${MAX_AMOSTRAS}" && (( 10#${MAX_AMOSTRAS} >= 1 )); } \
        || erro_uso "--max-amostras deve ser inteiro >= 1; recebido '${MAX_AMOSTRAS}'"
fi

INTERVALO_MS=$((10#${INTERVALO_MS}))
PODMAN_STATS_S=$((10#${PODMAN_STATS_S}))
printf -v NNN '%03d' "$((10#${REP}))"
CSV="${SAIDA}/recursos-rep-${NNN}.csv"
META="${SAIDA}/recursos-meta-rep-${NNN}.json"
JSONL="${SAIDA}/recursos-podman-rep-${NNN}.jsonl"
STATS_TMP="${SAIDA}/.recursos-podman-rep-${NNN}.tmp"
read -ra PODMAN_CMD <<< "${PODMAN}"

# Sinal: o trap entra ANTES de qualquer processo externo, para que um SIGTERM
# no inicio (resolucao, getconf...) nao mate o coletor sem linha "coletor" e
# sem meta; o pedido e atendido depois da primeira amostra.
FIM_PEDIDO=""
SLEEP_PID=""
ao_sinal() {
    FIM_PEDIDO="sinal"
    [[ -n "${SLEEP_PID}" ]] && kill "${SLEEP_PID}" 2>/dev/null
    return 0
}
trap ao_sinal TERM INT

# ------------------------------------------------- resolucao das fontes (inicio)
# Cgroup de cada conteiner por "podman inspect"; conteiner inexistente ou
# caminho ausente sob a raiz => exit 3 ANTES da primeira amostra.
declare -a CG_REL=() CG_DIR=()
for c in "${CONTAINERS[@]}"; do
    rel="$("${PODMAN_CMD[@]}" inspect --format '{{.State.CgroupPath}}' "${c}" 2>/dev/null)"; rc=$?
    rel="${rel%%$'\n'*}"; rel="${rel%$'\r'}"
    if [[ ${rc} -ne 0 || -z "${rel}" ]]; then
        echo "${NOME}: conteiner '${c}' nao encontrado (podman inspect rc=${rc})" >&2
        exit 3
    fi
    [[ "${rel}" == /* ]] || rel="/${rel}"
    dir="${RAIZ_CGROUP}${rel}"
    if [[ ! -d "${dir}" ]]; then
        echo "${NOME}: cgroup de '${c}' nao existe: ${dir}" >&2
        exit 3
    fi
    CG_REL+=("${rel}"); CG_DIR+=("${dir}")
done
for i in "${!PID_VALORES[@]}"; do
    if [[ ! -d "${RAIZ_PROC}/${PID_VALORES[i]}" ]]; then
        echo "${NOME}: PID ${PID_VALORES[i]} (${PID_ROTULOS[i]}) inexistente em ${RAIZ_PROC}" >&2
        exit 3
    fi
done
for f in "${CSV}" "${META}" "${JSONL}"; do
    if [[ -e "${f}" ]]; then
        echo "${NOME}: saida ja existe, nada sobrescrito: ${f}" >&2
        exit 4
    fi
done

if [[ ${DRY_RUN} -eq 1 ]]; then
    echo "dry-run: coletor ${VERSAO_COLETOR} (contrato ${CONTRATO})"
    echo "podman: ${PODMAN}"
    echo "raiz cgroup: ${RAIZ_CGROUP}"
    echo "raiz proc: ${RAIZ_PROC}"
    for i in "${!CONTAINERS[@]}"; do
        echo "fonte cg:${CONTAINERS[i]} cgroup=${CG_REL[i]} dir=${CG_DIR[i]}"
    done
    for i in "${!PID_VALORES[@]}"; do
        echo "fonte pid:${PID_ROTULOS[i]} pid=${PID_VALORES[i]}"
    done
    echo "fonte vm ${RAIZ_PROC}/stat ${RAIZ_PROC}/meminfo"
    echo "intervalo: ${INTERVALO_MS} ms; podman stats a cada ${PODMAN_STATS_S} s"
    if [[ -n "${DURACAO_S}" ]]; then echo "fim: --duracao-s ${DURACAO_S}"
    elif [[ -n "${MAX_AMOSTRAS}" ]]; then echo "fim: --max-amostras ${MAX_AMOSTRAS}"
    else echo "fim: SIGTERM/SIGINT"; fi
    echo "escreveria: ${CSV}"
    echo "escreveria: ${META}"
    [[ ${PODMAN_STATS_S} -gt 0 ]] && echo "escreveria: ${JSONL}"
    echo "dry-run: nada lido nem escrito"
    exit 0
fi

# ------------------------------------------------------- constantes do sistema
CLK_TCK="$(getconf CLK_TCK 2>/dev/null)"
if ! e_inteiro "${CLK_TCK}" || (( CLK_TCK < 1 )); then
    echo "${NOME}: getconf CLK_TCK invalido: '${CLK_TCK}'" >&2
    exit 1
fi
NPROC="$(nproc 2>/dev/null || echo 0)"
UNAME_R="$(uname -r 2>/dev/null || echo desconhecido)"
SHA256_SCRIPT="$(sha256sum "${BASH_SOURCE[0]}" 2>/dev/null)"; SHA256_SCRIPT="${SHA256_SCRIPT%% *}"
INTERVALO_NS=$(( INTERVALO_MS * 1000000 ))
STATS_NS=$(( PODMAN_STATS_S * 1000000000 ))

# --------------------------------------------------------------- criar saidas
mkdir -p "${SAIDA}" || { echo "${NOME}: nao foi possivel criar ${SAIDA}" >&2; exit 1; }
# noclobber: cria o CSV atomicamente e falha se alguem o criou nesse meio tempo.
if ! ( set -o noclobber; printf '%s\n' "${CABECALHO}" > "${CSV}" ) 2>/dev/null; then
    echo "${NOME}: saida ja existe ou nao gravavel: ${CSV}" >&2
    exit 4
fi
exec 3>>"${CSV}"

# -------------------------------------------------------------- relogio/espera
if [[ -n "${COLETOR_AGORA_NS_CMD:-}" ]]; then
    # SO TESTE: relogio injetado (cria processo; fora do caminho quente real).
    read -ra AGORA_CMD <<< "${COLETOR_AGORA_NS_CMD}"
    agora_ns() { AGORA_NS="$("${AGORA_CMD[@]}")"; }
fi
if [[ -n "${COLETOR_SLEEP_CMD:-}" ]]; then
    read -ra SLEEP_CMD <<< "${COLETOR_SLEEP_CMD}"
else
    SLEEP_CMD=(sleep)
fi

# --------------------------------------------------------------- estado global
STATS_PID=""
STATS_T=""
AMOSTRAS=0
ATRASO_MAX_NS=0
INICIO_NS=""
PROXIMO_STATS_NS=0

# >>> caminho-quente (R12: sem cat/awk/sed/cut/grep/date, sem $(...) nem crase)
if [[ -z "${COLETOR_AGORA_NS_CMD:-}" ]]; then
    agora_ns() {
        local t="${EPOCHREALTIME/[.,]/}"
        AGORA_NS=$(( 10#${t} * 1000 ))
    }
fi

# Cada leitor preenche R_CPU, R_MEM, R_STEAL e R_ESTADO ("ok" ou "erro:<motivo>").
ler_cgroup() {   # $1 = diretorio do cgroup
    R_CPU=""; R_MEM=""; R_STEAL=""
    if [[ ! -d "$1" ]]; then R_ESTADO="erro:cgroup-ausente"; return; fi
    local k v cpu="" mem=""
    while read -r k v; do
        if [[ "${k}" == usage_usec ]]; then cpu="${v}"; break; fi
    done 2>/dev/null < "$1/cpu.stat"
    read -r mem 2>/dev/null < "$1/memory.current"
    if [[ ! -d "$1" ]]; then R_ESTADO="erro:cgroup-ausente"; return; fi
    if [[ ! "${cpu}" =~ ^[0-9]+$ || ! "${mem}" =~ ^[0-9]+$ ]]; then R_ESTADO="erro:leitura"; return; fi
    R_CPU="${cpu}"; R_MEM="${mem}"; R_ESTADO="ok"
}

# /proc/<pid>/stat: o comm pode ter espaco/parentese; corta ate o ultimo ") ".
# Depois do corte, c[0] e o campo 3 (state): utime=c[11] stime=c[12]
# cutime=c[13] cstime=c[14] (campos 14..17 do proc(5)).
ler_pid_bruto() {   # $1 = raiz proc, $2 = pid, $3 = 1 para incluir filhos
    R_CPU=""; R_MEM=""; R_STEAL=""
    if [[ ! -d "$1/$2" ]]; then R_ESTADO="erro:pid-ausente"; return; fi
    local linha="" resto k v rss=""
    local -a c
    read -r linha 2>/dev/null < "$1/$2/stat"
    resto="${linha##*) }"
    read -ra c <<< "${resto}"
    while read -r k v _; do
        if [[ "${k}" == VmRSS: ]]; then rss="${v}"; break; fi
    done 2>/dev/null < "$1/$2/status"
    if [[ -z "${linha}" || "${resto}" == "${linha}" || ${#c[@]} -lt 15 ]]; then
        if [[ ! -d "$1/$2" ]]; then R_ESTADO="erro:pid-ausente"; else R_ESTADO="erro:leitura"; fi
        return
    fi
    local ticks=$(( c[11] + c[12] ))
    if [[ "$3" == 1 ]]; then ticks=$(( ticks + c[13] + c[14] )); fi
    if [[ ! "${rss}" =~ ^[0-9]+$ ]]; then R_ESTADO="erro:leitura"; return; fi
    R_CPU=$(( ticks * 1000000 / CLK_TCK )); R_MEM=$(( rss * 1024 )); R_ESTADO="ok"
}
ler_pid() { ler_pid_bruto "${RAIZ_PROC}" "$1" 0; }

# Linha "cpu " de /proc/stat: user nice system idle iowait irq softirq steal
# guest guest_nice. CPU ocupada = user+nice+system+irq+softirq (guest e
# guest_nice ja estao dentro de user/nice; idle/iowait fora; steal a parte).
ler_vm() {
    R_CPU=""; R_MEM=""; R_STEAL=""
    local nome="" us=0 ni=0 sy=0 id=0 io=0 irq=0 sirq=0 st=0 resto k v
    local total="" disp=""
    read -r nome us ni sy id io irq sirq st resto 2>/dev/null < "${RAIZ_PROC}/stat"
    while read -r k v _; do
        case "${k}" in
            MemTotal:)     total="${v}" ;;
            MemAvailable:) disp="${v}" ;;
        esac
        [[ -n "${total}" && -n "${disp}" ]] && break
    done 2>/dev/null < "${RAIZ_PROC}/meminfo"
    if [[ "${nome}" != cpu || ! "${us}${ni}${sy}${irq:-0}${sirq:-0}${st:-0}" =~ ^[0-9]+$ \
          || ! "${total}" =~ ^[0-9]+$ || ! "${disp}" =~ ^[0-9]+$ ]]; then
        R_ESTADO="erro:leitura"; return
    fi
    R_CPU=$(( (us + ni + sy + ${irq:-0} + ${sirq:-0}) * 1000000 / CLK_TCK ))
    R_STEAL=$(( ${st:-0} * 1000000 / CLK_TCK ))
    R_MEM=$(( (total - disp) * 1024 ))
    R_ESTADO="ok"
}

# Uma amostra: TODAS as fontes com o MESMO t (inicio do ciclo); uma escrita.
ciclo() {   # $1 = t_epoch_ns da amostra
    local t="$1" buf="" i
    for i in "${!CG_DIR[@]}"; do
        ler_cgroup "${CG_DIR[i]}"
        buf+="${t},cg:${CONTAINERS[i]},${R_CPU},${R_MEM},${R_STEAL},${R_ESTADO}"$'\n'
    done
    for i in "${!PID_VALORES[@]}"; do
        ler_pid "${PID_VALORES[i]}"
        buf+="${t},pid:${PID_ROTULOS[i]},${R_CPU},${R_MEM},${R_STEAL},${R_ESTADO}"$'\n'
    done
    ler_vm
    buf+="${t},vm,${R_CPU},${R_MEM},${R_STEAL},${R_ESTADO}"$'\n'
    printf '%s' "${buf}" >&3
}

# podman stats em segundo plano (unico processo periodico alem do sleep): a
# saida vai para um temporario e e recolhida num ciclo seguinte, sem bloquear
# a cadencia.
stats_lancar() {   # $1 = t
    STATS_T="$1"
    "${PODMAN_CMD[@]}" stats --no-stream --format json > "${STATS_TMP}" 2>&1 &
    STATS_PID=$!
}
stats_recolher() {   # $1 = 1 se deve esperar (fim)
    local rc=0 conteudo="" erro
    if [[ "$1" != 1 ]] && kill -0 "${STATS_PID}" 2>/dev/null; then return 1; fi
    wait "${STATS_PID}" 2>/dev/null; rc=$?
    read -r -d '' conteudo 2>/dev/null < "${STATS_TMP}"
    conteudo="${conteudo//$'\r'/}"
    conteudo="${conteudo//$'\n'/ }"
    if [[ ${rc} -eq 0 && "${conteudo}" =~ ^[[:space:]]*[\[\{] ]]; then
        printf '{"t_epoch_ns":%s,"ok":true,"stats":%s}\n' "${STATS_T}" "${conteudo}" >> "${JSONL}"
    else
        erro="rc=${rc} ${conteudo:0:200}"
        erro="${erro//\\/\\\\}"; erro="${erro//\"/\\\"}"; erro="${erro//$'\t'/ }"
        printf '{"t_epoch_ns":%s,"ok":false,"erro":"%s"}\n' "${STATS_T}" "${erro}" >> "${JSONL}"
    fi
    STATS_PID=""
    return 0
}

dormir() {   # $1 = ns (> 0)
    local s=$(( $1 / 1000000000 )) us=$(( ($1 % 1000000000) / 1000 )) d
    printf -v d '%d.%06d' "${s}" "${us}"
    "${SLEEP_CMD[@]}" "${d}" &
    SLEEP_PID=$!
    wait "${SLEEP_PID}" 2>/dev/null
    kill "${SLEEP_PID}" 2>/dev/null
    wait "${SLEEP_PID}" 2>/dev/null
    SLEEP_PID=""
}

laco() {
    local k=0 t prazo restante fim_dur=""
    while :; do
        agora_ns; t="${AGORA_NS}"
        if [[ -z "${INICIO_NS}" ]]; then
            INICIO_NS="${t}"
            [[ -n "${DURACAO_S}" ]] && fim_dur=$(( t + 10#${DURACAO_S} * 1000000000 ))
        fi
        ciclo "${t}"
        k=$(( k + 1 )); AMOSTRAS="${k}"
        if [[ ${STATS_NS} -gt 0 ]]; then
            if [[ -n "${STATS_PID}" ]]; then stats_recolher 0; fi
            if [[ -z "${STATS_PID}" && ${t} -ge ${PROXIMO_STATS_NS} ]]; then
                stats_lancar "${t}"
                PROXIMO_STATS_NS=$(( (PROXIMO_STATS_NS > 0 ? PROXIMO_STATS_NS : INICIO_NS) + STATS_NS ))
                while (( PROXIMO_STATS_NS <= t )); do PROXIMO_STATS_NS=$(( PROXIMO_STATS_NS + STATS_NS )); done
            fi
        fi
        [[ -n "${FIM_PEDIDO}" ]] && return
        if [[ -n "${MAX_AMOSTRAS}" ]] && (( k >= 10#${MAX_AMOSTRAS} )); then FIM_PEDIDO="max-amostras"; return; fi
        prazo=$(( INICIO_NS + k * INTERVALO_NS ))
        if [[ -n "${fim_dur}" ]] && (( prazo >= fim_dur )); then FIM_PEDIDO="duracao"; return; fi
        agora_ns
        restante=$(( prazo - AGORA_NS ))
        if (( restante > 0 )); then
            dormir "${restante}"
        elif (( -restante > ATRASO_MAX_NS )); then
            ATRASO_MAX_NS=$(( -restante ))
        fi
        [[ -n "${FIM_PEDIDO}" ]] && return
    done
}
# <<< caminho-quente

json_str() {   # escapa \ e " para string JSON
    local s="$1"
    s="${s//\\/\\\\}"; s="${s//\"/\\\"}"
    printf '"%s"' "${s}"
}

finalizar() {
    trap '' TERM INT
    if [[ -n "${SLEEP_PID}" ]]; then kill "${SLEEP_PID}" 2>/dev/null; wait "${SLEEP_PID}" 2>/dev/null; SLEEP_PID=""; fi
    if [[ -n "${STATS_PID}" ]]; then
        if kill -0 "${STATS_PID}" 2>/dev/null; then
            kill "${STATS_PID}" 2>/dev/null
        fi
        stats_recolher 1
    fi
    rm -f "${STATS_TMP}"
    agora_ns
    local fim="${AGORA_NS}"
    [[ -n "${INICIO_NS}" ]] || INICIO_NS="${fim}"
    # CPU do proprio coletor (utime+stime+cutime+cstime): sempre do /proc real,
    # pois e o processo coletor, nao uma fonte medida.
    ler_pid_bruto /proc "$$" 1
    printf '%s,coletor,%s,%s,,%s\n' "${fim}" "${R_CPU}" "${R_MEM}" "${R_ESTADO}" >&3
    exec 3>&-

    local fontes="" i sep=""
    for i in "${!CONTAINERS[@]}"; do
        fontes+="${sep}{\"fonte\":$(json_str "cg:${CONTAINERS[i]}"),\"cgroup\":$(json_str "${CG_REL[i]}")}"; sep=","
    done
    for i in "${!PID_VALORES[@]}"; do
        fontes+="${sep}{\"fonte\":$(json_str "pid:${PID_ROTULOS[i]}"),\"pid\":${PID_VALORES[i]}}"; sep=","
    done
    {
        printf '{"contrato":%s,' "$(json_str "${CONTRATO}")"
        printf '"versao_coletor":%s,' "$(json_str "${VERSAO_COLETOR}")"
        printf '"sha256_script":%s,' "$(json_str "${SHA256_SCRIPT}")"
        printf '"uname_r":%s,' "$(json_str "${UNAME_R}")"
        printf '"clk_tck":%s,"nproc":%s,' "${CLK_TCK}" "${NPROC:-0}"
        printf '"intervalo_ms":%s,"podman_stats_s":%s,' "${INTERVALO_MS}" "${PODMAN_STATS_S}"
        printf '"fontes":[%s],' "${fontes}"
        printf '"inicio_epoch_ns":%s,"fim_epoch_ns":%s,' "${INICIO_NS}" "${fim}"
        printf '"amostras":%s,"motivo_fim":%s,' "${AMOSTRAS}" "$(json_str "${FIM_PEDIDO}")"
        printf '"atraso_max_ns":%s}\n' "${ATRASO_MAX_NS}"
    } > "${META}"
}

laco
finalizar
exit 0
