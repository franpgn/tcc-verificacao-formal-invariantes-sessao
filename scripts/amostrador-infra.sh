#!/usr/bin/env bash
#
# amostrador-infra.sh
#
# Amostrador de infraestrutura do criterio h4 do rebaseline (DT-018; complemento 3 do pre-registro, C3 §2).
# Roda NA VM, como root, numa unit de sistema propria (systemd-run), FORA do runner congelado. A cada 1 s grava
# em /run/tccdiag/<id>/amostras.txt (tmpfs: escreve na memoria, nao no disco que mede) o relogio de parede
# ($EPOCHREALTIME), o /proc/uptime, os total= de /proc/pressure/{io,cpu,memory} e a linha do disco em
# /proc/diskstats. O laco nao cria processo por amostra (so builtins do bash). Uma segunda unit (espelho)
# copia a cada 300 s o trecho novo para ~/rebaseline-infra/<id>/amostras.txt; se o disco travar, so o
# espelho espera.
#
# Uso:
#   amostrador-infra.sh iniciar --id <fase>-<UTC> [--disco sda] [--espelho-dir <dir>]   # como usuario, usa sudo
#   amostrador-infra.sh parar   --id <fase>-<UTC> [--espelho-dir <dir>]   # para as duas units e faz a copia final
#   amostrador-infra.sh estado  [--id <fase>-<UTC>]                       # so leitura
#   amostrador-infra.sh _laco    --id ... --disco ...          # corpo da unit principal (nao chamar a mao)
#   amostrador-infra.sh _espelho --id ... --espelho-dir ...    # corpo da unit do espelho (nao chamar a mao)
#   <id> casa com ^(piloto|noite1|noite2|reposicoes|ensaio|diag)-[0-9]{8}T[0-9]{6}Z$
#   --espelho-dir padrao: ~/rebaseline-infra/<id> do usuario que chamou.
#
# Formato v1 de amostras.txt: uma linha de cabecalho
#   # tcc-amostrador-infra v1 id=<id> inicio=<date -Is> boot_id=<uuid> host=<hostname> disco=<disco> periodo_s=1 pid=<pid> versao_script_sha256=<sha256>
# e uma linha por amostra
#   <t_real> <uptime> io <some_total> <full_total> cpu <some_total> <full_total|-> mem <some_total> <full_total> <disco> <campos 4..20 de /proc/diskstats | ->
# mais linhas "# erro t_real=<...> <motivo>" e, no SIGTERM, "# fim t_real=<...> amostras=<n> atrasos_ge_1_5s=<n>".
#
# So nos testes: H4_MODO_TESTE=1 com H4_PROC (no lugar de /proc), H4_RUN (no lugar de /run/tccdiag) e
# H4_ESPELHO_PERIODO_S (no lugar dos 300 s); H4_PROC e H4_RUN precisam da marca .h4-shim. Sem H4_MODO_TESTE=1,
# qualquer uma delas e erro de uso.
#
# Exit codes: 0 OK; 2 uso; 3 pre-condicao (unit ja ativa, /proc/pressure/io ausente, disco ausente em
# /proc/diskstats, /run com menos de 64 MiB livres, bash sem EPOCHREALTIME, sudo -n negado, coleta com o mesmo
# id ja existente); 4 falha ao iniciar a unit, ao parar, ao copiar ou ao conferir.
# NAO faz git push. Nao le nada do workload.
set -uo pipefail
LC_ALL=C
export LC_ALL

NOME="amostrador-infra.sh"
_src="${BASH_SOURCE[0]}"
[[ "${_src}" == /* ]] || _src="${PWD}/${_src}"
SCRIPT_DIR="$(cd "${_src%/*}" && pwd -P)"
SELF="${SCRIPT_DIR}/${_src##*/}"
readonly ID_RE='^(piloto|noite1|noite2|reposicoes|ensaio|diag)-[0-9]{8}T[0-9]{6}Z$'
readonly PERIODO_US=1000000          # C3 §2: 1 s
readonly ATRASO_US=1500000           # contagem de atrasos >= 1,5 s na linha "# fim"
readonly ESPELHO_PERIODO_S=300       # C3 §2
readonly RUN_MIN_LIVRE=$((64 * 1024 * 1024))
readonly ESPERA_PRIMEIRA_S=3 ESPERA_PARAR_S=10

erro_uso() { echo "Erro: $*" >&2; exit 2; }
pre() { echo "Erro (pre-condicao): $*" >&2; exit 3; }
falha() { echo "Erro: $*" >&2; exit 4; }

# ------------------------------------------------------------------ caminhos (e modo de teste)
PROC=/proc
RUN=/run/tccdiag
ESP_PERIODO="${ESPELHO_PERIODO_S}"
if [[ -n "${H4_PROC:-}${H4_RUN:-}${H4_ESPELHO_PERIODO_S:-}" ]]; then
    [[ "${H4_MODO_TESTE:-}" == 1 ]] || erro_uso "H4_PROC, H4_RUN e H4_ESPELHO_PERIODO_S so valem com H4_MODO_TESTE=1"
    if [[ -n "${H4_PROC:-}" ]]; then
        [[ -f "${H4_PROC}/.h4-shim" ]] || erro_uso "H4_PROC sem a marca .h4-shim"
        PROC="${H4_PROC}"
    fi
    if [[ -n "${H4_RUN:-}" ]]; then
        [[ -f "${H4_RUN}/.h4-shim" ]] || erro_uso "H4_RUN sem a marca .h4-shim"
        RUN="${H4_RUN}"
    fi
    if [[ -n "${H4_ESPELHO_PERIODO_S:-}" ]]; then
        [[ "${H4_ESPELHO_PERIODO_S}" =~ ^[0-9]+$ ]] || erro_uso "H4_ESPELHO_PERIODO_S deve ser inteiro"
        ESP_PERIODO="${H4_ESPELHO_PERIODO_S}"
    fi
fi
RUN_BASE="${RUN%/*}"     # /run (onde o df mede o espaco livre)
[[ -n "${RUN_BASE}" ]] || RUN_BASE=/

# ------------------------------------------------------------------ opcoes
CMD="${1:-}"
[[ $# -gt 0 ]] && shift
ID=""; DISCO="sda"; ESPELHO_DIR=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --id|--disco|--espelho-dir)
            [[ $# -ge 2 ]] || erro_uso "$1 exige valor"
            case "$1" in --id) ID="$2" ;; --disco) DISCO="$2" ;; --espelho-dir) ESPELHO_DIR="$2" ;; esac
            shift 2 ;;
        --help|-h) sed -n '2,38p' "${SELF}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) erro_uso "argumento desconhecido '$1'" ;;
    esac
done
case "${CMD}" in iniciar|parar|estado|_laco|_espelho) ;; *) erro_uso "subcomando deve ser iniciar, parar, estado, _laco ou _espelho" ;; esac
if [[ "${CMD}" != estado || -n "${ID}" ]]; then
    [[ "${ID}" =~ ${ID_RE} ]] || erro_uso "--id '${ID}' nao casa com ${ID_RE}"
fi
[[ "${DISCO}" =~ ^[a-z][a-z0-9]*$ ]] || erro_uso "--disco invalido: '${DISCO}'"
if [[ -z "${ESPELHO_DIR}" && "${CMD}" != _laco && "${CMD}" != estado ]]; then
    [[ "${CMD}" != _espelho ]] || erro_uso "_espelho exige --espelho-dir"
    ESPELHO_DIR="${HOME}/rebaseline-infra/${ID}"
fi
if [[ -n "${ESPELHO_DIR}" && "${ESPELHO_DIR}" != /* ]]; then ESPELHO_DIR="${PWD}/${ESPELHO_DIR}"; fi
UNIT="tcc-amostrador-${ID}"
UNIT_ESP="tcc-amostrador-espelho-${ID}"
DIR="${RUN}/${ID}"
ARQ="${DIR}/amostras.txt"

# ================================================================== _laco
# Tudo entre <laco> e </laco> roda a cada amostra: so builtins, sem $( ), sem pipe, sem sleep.
#<laco>
P_UP="${PROC}/uptime"; P_IO="${PROC}/pressure/io"; P_CPU="${PROC}/pressure/cpu"; P_MEM="${PROC}/pressure/memory"
P_DISK="${PROC}/diskstats"
N_AMOSTRAS=0; N_ATRASOS=0; N_ERROS=0; T_ANT=0
PSI_S=""; PSI_F=""; DSK="-"; T_REAL=""

ler_psi() {  # ler_psi <arquivo>: PSI_S e PSI_F (total= da linha some e da full); status 1 se ilegivel
    PSI_S=""; PSI_F=""
    [[ -r "$1" ]] || return 1
    local k a b c tot
    while read -r k a b c tot; do
        case "${k}" in
            some) PSI_S="${tot#total=}" ;;
            full) PSI_F="${tot#total=}" ;;
        esac
    done < "$1"
    [[ "${PSI_S}" =~ ^[0-9]+$ ]] || return 1
    [[ -z "${PSI_F}" || "${PSI_F}" =~ ^[0-9]+$ ]] || return 1
    return 0
}

ler_disco() {  # DSK = campos 4..20 da linha do disco em /proc/diskstats, ou "-"
    DSK="-"
    [[ -r "${P_DISK}" ]] || return 1
    local -a c
    while read -r -a c; do
        if [[ "${c[2]:-}" == "${DISCO}" ]]; then
            DSK="${c[*]:3:17}"
            return 0
        fi
    done < "${P_DISK}"
    return 1
}

registrar_erro() {  # registrar_erro <motivo>
    N_ERROS=$((N_ERROS + 1))
    printf '# erro t_real=%s %s\n' "${T_REAL}" "$1" >> "${ARQ}"
}

amostrar() {
    local up="" resto="" io_s io_f cpu_s cpu_f mem_s mem_f t_us
    if [[ -r "${P_UP}" ]]; then read -r up resto < "${P_UP}"; fi
    T_REAL="${EPOCHREALTIME/,/.}"
    if [[ ! "${up}" =~ ^[0-9]+\.[0-9]+$ ]]; then registrar_erro "uptime ilegivel"; return 1; fi
    if ! ler_psi "${P_IO}" || [[ -z "${PSI_F}" ]]; then registrar_erro "pressure/io ilegivel"; return 1; fi
    io_s="${PSI_S}"; io_f="${PSI_F}"
    if ! ler_psi "${P_CPU}"; then registrar_erro "pressure/cpu ilegivel"; return 1; fi
    cpu_s="${PSI_S}"; cpu_f="${PSI_F:--}"
    if ! ler_psi "${P_MEM}" || [[ -z "${PSI_F}" ]]; then registrar_erro "pressure/memory ilegivel"; return 1; fi
    mem_s="${PSI_S}"; mem_f="${PSI_F}"
    ler_disco
    printf '%s %s io %s %s cpu %s %s mem %s %s %s %s\n' "${T_REAL}" "${up}" "${io_s}" "${io_f}" "${cpu_s}" "${cpu_f}" \
        "${mem_s}" "${mem_f}" "${DISCO}" "${DSK}" >> "${ARQ}"
    t_us="${T_REAL/./}"
    t_us=$((10#${t_us}))
    if [[ "${T_ANT}" -gt 0 && $((t_us - T_ANT)) -ge "${ATRASO_US}" ]]; then N_ATRASOS=$((N_ATRASOS + 1)); fi
    T_ANT="${t_us}"
    N_AMOSTRAS=$((N_AMOSTRAS + 1))
    return 0
}

fim_laco() {
    T_REAL="${EPOCHREALTIME/,/.}"
    printf '# fim t_real=%s amostras=%d atrasos_ge_1_5s=%d\n' "${T_REAL}" "${N_AMOSTRAS}" "${N_ATRASOS}" >> "${ARQ}"
    exit 0
}

laco_principal() {  # prazo absoluto t0 + k*1 s; atraso nao e recuperado (pula para o proximo prazo futuro)
    local t0 k=0 prazo agora esp espf rc
    t0="${EPOCHREALTIME/[.,]/}"; t0=$((10#${t0}))
    while :; do
        amostrar
        k=$((k + 1)); prazo=$((t0 + k * PERIODO_US))
        agora="${EPOCHREALTIME/[.,]/}"; agora=$((10#${agora}))
        if [[ "${prazo}" -le "${agora}" ]]; then
            k=$(( (agora - t0) / PERIODO_US + 1 )); prazo=$((t0 + k * PERIODO_US))
        fi
        esp=$((prazo - agora))
        printf -v espf '%d.%06d' $((esp / 1000000)) $((esp % 1000000))
        rc=0
        read -r -t "${espf}" -u "${RELOGIO_FD}" || rc=$?
        if [[ "${rc}" -le 128 ]]; then
            T_REAL="${EPOCHREALTIME/,/.}"
            registrar_erro "relogio: read -t devolveu ${rc} (descritor de espera quebrado)"
            fim_laco
        fi
    done
}
#</laco>

laco() {
    [[ -d "${DIR}" ]] || pre "${DIR} ausente (o iniciar cria com install -d)"
    [[ -n "${EPOCHREALTIME:-}" ]] || pre "bash sem EPOCHREALTIME"
    # descritor de espera: um pipe sem escritor que nunca tem dados (read -t so sai por tempo ou sinal)
    local rc=0
    exec {RELOGIO_FD}<> <(:)
    read -r -t 0.01 -u "${RELOGIO_FD}" || rc=$?
    if [[ "${rc}" -le 128 ]]; then
        # sem /dev/fd de pipe reabrivel (ex.: Git Bash): FIFO no diretorio da coleta, criado uma vez
        exec {RELOGIO_FD}<&-
        local mk=mkfifo
        command -v mkfifo > /dev/null 2>&1 || mk=/usr/bin/mkfifo
        [[ -p "${DIR}/.relogio" ]] || "${mk}" "${DIR}/.relogio" || falha "mkfifo ${DIR}/.relogio falhou"
        exec {RELOGIO_FD}<> "${DIR}/.relogio"
    fi
    # cabecalho: a unica linha montada com processo externo (sha256sum), e so no inicio
    local sha="-" boot="-" inicio o="" bin=""
    if command -v sha256sum > /dev/null 2>&1; then bin=sha256sum; elif [[ -x /usr/bin/sha256sum ]]; then bin=/usr/bin/sha256sum; fi
    if [[ -n "${bin}" ]]; then o="$("${bin}" "${SELF}" 2> /dev/null)" && sha="${o%% *}"; sha="${sha#\\}"; fi
    if [[ -r "${PROC}/sys/kernel/random/boot_id" ]]; then read -r boot < "${PROC}/sys/kernel/random/boot_id"; fi
    printf -v inicio '%(%Y-%m-%dT%H:%M:%S%z)T' -1
    inicio="${inicio:0:22}:${inicio:22}"
    printf '# tcc-amostrador-infra v1 id=%s inicio=%s boot_id=%s host=%s disco=%s periodo_s=1 pid=%s versao_script_sha256=%s\n' \
        "${ID}" "${inicio}" "${boot:--}" "${HOSTNAME:-?}" "${DISCO}" "$$" "${sha}" >> "${ARQ}" || falha "nao consegui escrever ${ARQ}"
    trap 'fim_laco' TERM INT
    laco_principal
}

# ================================================================== _espelho
SAIR=0
espelho_copiar() {  # 0 copiou (ou nada a copiar), 1 erro, 2 copia em curso quando chegou o TERM
    local src="${ARQ}" off_f="${DIR}/espelho.offset" dst="${ESPELHO_DIR}/amostras.txt" off=0 tam n cp rc
    [[ -f "${src}" ]] || return 0
    if [[ -r "${off_f}" ]]; then read -r off < "${off_f}" || true; fi
    [[ "${off}" =~ ^[0-9]+$ ]] || off=0
    tam="$(stat -c %s "${src}")" || return 1
    [[ "${tam}" -gt "${off}" ]] || return 0
    n=$((tam - off))
    # dd em segundo plano: um TERM durante uma escrita bloqueada nao fica esperando por ela
    dd if="${src}" iflag=skip_bytes,count_bytes skip="${off}" count="${n}" bs=65536 status=none >> "${dst}" &
    cp=$!
    while :; do
        rc=0; wait "${cp}" || rc=$?
        if [[ "${rc}" -gt 128 && "${SAIR}" -eq 1 ]] && kill -0 "${cp}" 2> /dev/null; then return 2; fi
        kill -0 "${cp}" 2> /dev/null || break
    done
    [[ "${rc}" -eq 0 ]] || { echo "${NOME}: espelho: dd saiu com ${rc}" >&2; return 1; }
    printf '%s\n' "${tam}" > "${off_f}.tmp" && mv -f "${off_f}.tmp" "${off_f}"
    chown "$(stat -c %u:%g "${ESPELHO_DIR}")" "${dst}" 2> /dev/null || true
    return 0
}

espelho() {
    [[ -d "${ESPELHO_DIR}" ]] || pre "${ESPELHO_DIR} ausente"
    trap 'SAIR=1' TERM INT
    local rc s
    while :; do
        rc=0; espelho_copiar || rc=$?
        [[ "${rc}" -ne 2 ]] || exit 0
        [[ "${SAIR}" -eq 0 ]] || break
        sleep "${ESP_PERIODO}" &
        s=$!
        wait "${s}" || true
        kill "${s}" 2> /dev/null || true
        [[ "${SAIR}" -eq 0 ]] || break
    done
    espelho_copiar || true    # ultima copia
    exit 0
}

# ================================================================== iniciar / parar / estado
ativa() { systemctl is-active --quiet "$1" > /dev/null 2>&1; }

disco_presente() {
    local -a c
    [[ -r "${PROC}/diskstats" ]] || return 1
    while read -r -a c; do [[ "${c[2]:-}" != "${DISCO}" ]] || return 0; done < "${PROC}/diskstats"
    return 1
}

iniciar() {
    [[ -n "${EPOCHREALTIME:-}" ]] || pre "bash sem EPOCHREALTIME"
    sudo -n true > /dev/null 2>&1 || pre "'sudo -n true' negado (sudo sem senha e obrigatorio)"
    local u
    for u in "${UNIT}" "${UNIT_ESP}"; do ! ativa "${u}" || pre "unit ${u} ja ativa"; done
    [[ -r "${PROC}/pressure/io" ]] || pre "${PROC}/pressure/io ausente (kernel sem PSI)"
    disco_presente || pre "disco '${DISCO}' ausente em ${PROC}/diskstats"
    local livre
    livre="$(df -B1 --output=avail "${RUN_BASE}" 2> /dev/null | tail -n 1)"; livre="${livre//[[:space:]]/}"
    [[ "${livre}" =~ ^[0-9]+$ ]] || pre "df de ${RUN_BASE} ilegivel ('${livre}')"
    [[ "${livre}" -ge "${RUN_MIN_LIVRE}" ]] || pre "${RUN_BASE} com ${livre} bytes livres (< 64 MiB)"
    [[ ! -e "${ARQ}" ]] || pre "${ARQ} ja existe (use um --id novo)"
    [[ ! -e "${ESPELHO_DIR}/amostras.txt" ]] || pre "${ESPELHO_DIR}/amostras.txt ja existe (use um --id novo)"
    mkdir -p "${ESPELHO_DIR}" || falha "mkdir ${ESPELHO_DIR} falhou"
    sudo -n install -d -m 0755 "${DIR}" || falha "sudo install -d ${DIR} falhou"
    sudo -n systemd-run --unit="${UNIT}" --description="tcc amostrador infra ${ID}" --property=Type=simple \
        --property=StandardOutput=null --property=StandardError=journal --property=Restart=no \
        /usr/bin/bash "${SELF}" _laco --id "${ID}" --disco "${DISCO}" || falha "systemd-run ${UNIT} falhou"
    if ! sudo -n systemd-run --unit="${UNIT_ESP}" --description="tcc amostrador infra espelho ${ID}" \
        --property=Type=simple --property=StandardOutput=null --property=StandardError=journal --property=Restart=no \
        /usr/bin/bash "${SELF}" _espelho --id "${ID}" --espelho-dir "${ESPELHO_DIR}"; then
        sudo -n systemctl stop "${UNIT}" || true
        falha "systemd-run ${UNIT_ESP} falhou (unit principal parada)"
    fi
    local k l primeira=""
    for ((k = 0; k < ESPERA_PRIMEIRA_S * 10; k++)); do
        if [[ -r "${ARQ}" ]]; then
            while IFS= read -r l; do
                if [[ "${l}" != \#* ]]; then primeira="${l}"; break; fi
            done < "${ARQ}"
        fi
        [[ -z "${primeira}" ]] || break
        sleep 0.1
    done
    [[ -n "${primeira}" ]] || falha "nenhuma amostra em ${ARQ} em ${ESPERA_PRIMEIRA_S} s (journalctl -u ${UNIT})"
    echo "unit: ${UNIT}"
    echo "unit: ${UNIT_ESP}"
    echo "arquivo: ${ARQ}"
    echo "espelho: ${ESPELHO_DIR}/amostras.txt"
    echo "primeira amostra: ${primeira}"
}

parar() {
    sudo -n true > /dev/null 2>&1 || pre "'sudo -n true' negado"
    local rc=0
    sudo -n systemctl stop "${UNIT}" || rc=$?
    [[ "${rc}" -eq 0 ]] || echo "AVISO: systemctl stop ${UNIT} saiu com ${rc}" >&2
    rc=0; sudo -n systemctl stop "${UNIT_ESP}" || rc=$?
    [[ "${rc}" -eq 0 ]] || echo "AVISO: systemctl stop ${UNIT_ESP} saiu com ${rc}" >&2
    local k
    for ((k = 0; k < ESPERA_PARAR_S * 10; k++)); do
        if ! ativa "${UNIT}" && ! ativa "${UNIT_ESP}"; then break; fi
        sleep 0.1
    done
    if ativa "${UNIT}" || ativa "${UNIT_ESP}"; then falha "units ainda ativas depois de ${ESPERA_PARAR_S} s"; fi
    [[ -f "${ARQ}" ]] || falha "${ARQ} ausente (a VM reiniciou? use o espelho ${ESPELHO_DIR}/amostras.txt)"
    mkdir -p "${ESPELHO_DIR}" || falha "mkdir ${ESPELHO_DIR} falhou"
    local usuario fin="${ESPELHO_DIR}/amostras-final.txt" h1 h2
    usuario="$(id -un)"
    sudo -n install -m 0644 -o "${usuario}" "${ARQ}" "${fin}" || falha "copia final falhou"
    h1="$(sha256sum "${ARQ}")" || falha "sha256sum ${ARQ} falhou"; h1="${h1%% *}"; h1="${h1#\\}"
    h2="$(sha256sum "${fin}")" || falha "sha256sum ${fin} falhou"; h2="${h2%% *}"; h2="${h2#\\}"
    [[ "${h1}" == "${h2}" ]] || falha "copia final difere da original (${h1} != ${h2})"
    printf '%s  amostras-final.txt\n' "${h2}" > "${fin}.sha256" || falha "nao gravei ${fin}.sha256"
    echo "copia final: ${fin} sha256=${h2}"
    awk 'substr($0, 1, 1) != "#" {
             n++; if (n == 1) p = $1; if (n > 1 && $1 - u > mi) { mi = $1 - u; mt = $1 }; u = $1; ul = $1 }
         /^# erro / { e++ }
         END { printf "amostras=%d primeira=%s ultima=%s maior_intervalo_s=%.6f (ate %s) linhas_erro=%d\n",
                      n, p, ul, mi, mt, e }' "${fin}"
}

estado() {
    echo "== units"
    systemctl list-units --all --no-legend --plain "tcc-amostrador-${ID:-*}*" 2>&1 || true
    echo "== coletas em ${RUN}"
    local f l n
    for f in "${RUN}"/${ID:-*}/amostras.txt; do
        [[ -f "${f}" ]] || continue
        n="$(stat -c %s "${f}" 2> /dev/null)" || n="?"
        l="$(tail -n 1 "${f}" 2> /dev/null)" || l="?"
        echo "${f} bytes=${n}"
        echo "  ultima: ${l}"
    done
    echo "== df ${RUN_BASE}"
    df -B1 "${RUN_BASE}" 2>&1 || true
}

case "${CMD}" in
    iniciar) iniciar ;;
    parar) parar ;;
    estado) estado ;;
    _laco) laco ;;
    _espelho) espelho ;;
esac
exit 0
