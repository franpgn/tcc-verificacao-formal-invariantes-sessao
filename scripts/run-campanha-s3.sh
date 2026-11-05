#!/usr/bin/env bash
#
# run-campanha-s3.sh
#
# Orquestrador das campanhas S3 do controlador (W-6, B-46): calibracao (so braco A, shadow) e avaliacao (blocos
# A/B, shadow x active), com F1 (crash silencioso), F2 (particao) e F4 (degradacao parcial). Roda DENTRO da VM
# tcc-f3, em tmux, contra o cluster rootful (PODMAN="sudo podman"), sob flock /var/lock/tcc-vm.lock. Uma invocacao
# executa UMA fase (calibracao, nucleo, extra ou reposicoes) da matriz (scripts/campanha_s3_matriz.py).
#
# Estrutura copiada do runner congelado do rebaseline (scripts/run-rebaseline.sh, d95305fe..., que NAO muda):
# pre-voo, GO por tty com texto exato, portao K1 antes de cada execucao, execucao.json, MANIFEST.sha256, paradas
# e DT-014 para execucao interrompida. O que e novo, com a origem:
#   - cluster recriado antes de cada execucao com o XML SEM caches (cluster/podman-compose-sem-caches.yml) e os
#     seis caches criados pela REST (scripts/cria-caches.sh, leitura de volta byte a byte nos 3 nos): P-30 do
#     B-44.1b, docs/acoes-controlador-cluster-2026-11-05.md secao 6 (requisito 1);
#   - camada 3 da reversao antes e depois de cada execucao: rebalancing_enabled, disponibilidade e configuracao dos
#     seis caches nos tres nos (plano de execucao 4.4.3; acoes secao 6, item 5);
#   - stdout do programa lido por um FIFO, sem disco no caminho: ancora "Warm-up ate +" e a linha de fim da carga
#     "Fim da carga: ... ac5_alvo=<host:porta|->". Com AC-5 aplicado, podman start do no parado logo ao ler a
#     linha (P-6 da V-W5, achado A-1, opcao a; requisito 4). O programa espera a topologia completa e grava
#     t_religamento_ns;
#   - timeout -k K com K = T_enc no pior caso (21 s, contrato JSONL v1.2 depois do P-42: AC-5 com prazo de 17 s e
#     a P2 habilitada; 19 s sem ela) + encerramento_folga_s, SIGTERM antes de SIGKILL (requisito 2);
#   - braco B recusado se a meta da trilha tiver atuador nulo, politica nenhuma, sem parametros_politica ou
#     param_sha256 diferente do --controller-config da campanha (o mesmo arquivo nos dois bracos): A-3 da V-W5,
#     O10 da V-W2a (requisito 3);
#   - P2 fora do braco B (D-B do coordenador, 05/11): toda fase deste orquestrador e de calibracao ou avaliacao, entao
#     o --controller-config tem de ter politica.p2Habilitada = false (pre-voo) e a meta de cada execucao, nos dois
#     bracos, parametros_politica.p2_habilitada = false (senao a carga e parada, exit 6);
#   - volta do no depois do F1 conferida pela REST, nao pelo exit do inject-crash.sh (P-35; requisito 5);
#   - criterio h4 do C3 e da DT-019 pelo scripts/campanha_s3_h4.py (sobre analysis/h4.py) depois de cada execucao,
#     com a parada do C3 secao 4.3 e a unit do amostrador conferida antes e depois (requisito 6);
#   - F1 por inject-crash.sh --marker (B-25.1), F2 por inject-partition.sh e F4 por inject-netdegrade.sh, cada uma
#     com dead-man (systemd-run, margem de 90 s, AccuracySec=1s) e marcadores start/end (requisito 7);
#   - GO por tty (requisito 8): "go <fase> <AAAA-MM-DD>" no inicio e "go rede <i>" antes da primeira F2/F4;
#   - progresso.json por troca atomica, recusa de retomada com o bundle alterado, arquivo PARAR entre execucoes,
#     limite de disco (plano de execucao 9, W-6, itens 3 e 4).
# Todo numero da campanha vem do --parametros (esquema campanha-s3/1, cada um com a sua origem, sem padrao;
# scripts/campanha_s3_conferir.py). As constantes abaixo tem a origem ao lado.
#
# Uso:
#   scripts/run-campanha-s3.sh --matriz <tsv> --matriz-sha256 <hex> --fase calibracao|nucleo|extra|reposicoes
#       --parametros <json> --parametros-sha256 <hex> --controlador <json> --controlador-sha256 <hex>
#       --referencia <json> --referencia-sha256 <hex> --saida <dir>
#       [--jar <jar> --jar-sha256 <hex> --boot-esperado <uuid> --h4-id <id>]
#       [--servers h:p,h:p,h:p] [--admin http://h:p,...] [--username <u>] [--password <p>] [--dry-run]
#   (--jar, --jar-sha256, --boot-esperado e --h4-id sao obrigatorios fora do --dry-run.)
#   A fase "reposicoes" le <saida>/reposicoes/reposicoes.tsv, escrito antes por campanha_s3_matriz.py repor.
#
# Variaveis: PODMAN (padrao "sudo podman", exportada aos injetores), PODMAN_COMPOSE (padrao "sudo podman-compose"),
# JAVA (padrao java), CS_PASSWORD (senha, se --password nao for dado). So nos testes: CS_MODO_TESTE=1 com
# CS_SHIM_DIR (marca .cs-shim; PODMAN e PODMAN_COMPOSE tem de ser shims dele), CS_TTY_ARQUIVO (no lugar de
# /dev/tty), CS_H4_RUN (no lugar de /run/tccdiag), CS_COLETOR (no lugar de scripts/collect-resources.sh) e
# CS_INJ_PART/CS_INJ_DEGR (duble dos injetores de F2 e F4; o F1 roda o inject-crash.sh real contra os shims).
#
# Exit codes (os do runner do rebaseline):
#   0  fase concluida ou parada por PARAR     2  uso      3  dependencia ausente     4  pre-voo recusado
#   5  GO nao dado    6  erro de infraestrutura (braco B que nao atua, cache nao criado, residuo de rede, injetor
#   com exit deterministico)    10 parada (K1, no sem volta, h4, disco, cluster que nao volta)
# A ultima linha de <saida>/<fase>/orquestrador.log traz o exit e o motivo. Nunca apaga brutos. NAO faz git push.
set -euo pipefail

NOME="run-campanha-s3.sh"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="${SCRIPT_DIR}/$(basename "${BASH_SOURCE[0]}")"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONFERIR="${SCRIPT_DIR}/campanha_s3_conferir.py"
H4S3="${SCRIPT_DIR}/campanha_s3_h4.py"
CRIA_CACHES="${SCRIPT_DIR}/cria-caches.sh"
INJ_CRASH="${SCRIPT_DIR}/inject-crash.sh"
INJ_PART="${SCRIPT_DIR}/inject-partition.sh"
INJ_DEGR="${SCRIPT_DIR}/inject-netdegrade.sh"
COMPOSE_ARQ="${REPO_DIR}/cluster/podman-compose-sem-caches.yml"
DIR_CACHES="${REPO_DIR}/cluster/caches"
ARGS_ORIGINAIS=("$@")

# ------------------------------------------------------------------ constantes (cada uma com a origem)
readonly K1_JANELA_S=60 K1_PERIODO_S=5 K1_TIMEOUT_S=600   # PR do rebaseline secao 6 (K1, parada (e)); run-rebaseline.sh
readonly VOLTA_TIMEOUT_S=180 VOLTA_PERIODO_S=5            # PR secao 6 (e), F1_VOLTA_TIMEOUT_S do run-rebaseline.sh; P-35
readonly RECRIAR_TIMEOUT_S=180 RECRIAR_PERIODO_S=2        # runs/gate-b29/recriar.sh (ate 180 s, leitura a cada 2 s)
readonly ANCORA_TIMEOUT_S=120 POLL_S=0.2                  # run-rebaseline.sh (ANCORA_TIMEOUT_S, ANCORA_POLL_S)
readonly GATE_TIMEOUT_S=3600                              # run-rebaseline.sh
readonly REST_PRAZO_S=5                                   # run-rebaseline.sh (curl --max-time 5 na saude)
readonly DEADMAN_MARGEM_S=90                              # runs/gate-netdegrade/l-lib.sh e g5b.sh modo A (V-S6A)
readonly DEADMAN_PRECISAO="AccuracySec=1s"                # runs/gate-b29/g5b.sh (achado de S0: 1 min por padrao)
readonly T_ENC_PIOR_S=21                                  # contrato JSONL v1.2, P-42 (AC-5 17 s; P2 habilitada; 19 s sem ela)
readonly CRIACAO_AVISO_MS=8000                            # acoes-controlador secao 6.4: maior criacao vista, 8,0 s
readonly DISCO_FATOR_X10=15                               # plano de execucao 9 (W-6, item 4): livre >= 1,5 x volume
readonly NOS="isn1 isn2 isn3"
readonly IFACE=eth0
readonly CACHES="sessions counters sessions-async counters-async canario canario-async"   # cluster/caches/*.json
readonly ANCORA_RE='^Warm-up ate \+([0-9]+)s; medicao a partir de \+([0-9]+)s; total ([0-9]+)s'
readonly SINAL_RE='^Fim da carga: rep=([0-9]+) t_fim_carga_ns=([0-9]+) ac5_alvo=([^ ]+)$'
readonly EXITS_F1_SEGUEM=" 0 1 4 "      # C3/P2 do Gestor (23/10), como no rebaseline: 1 e 4 sao recusa
readonly EXITS_REDE_SEGUEM=" 0 1 4 6 "  # idem, mais 6 (a falha nao se efetivou) dos injetores de rede: recusa
readonly EXITS_PREVISTOS=" 0 2 3 4 5 6 10 130 143 "

PODMAN="${PODMAN:-sudo podman}"
export PODMAN
PODMAN_COMPOSE="${PODMAN_COMPOSE:-sudo podman-compose}"
JAVA="${JAVA:-java}"
read -r -a JAVA_CMD <<< "${JAVA}"
PYTHON=python3
COLETOR="${SCRIPT_DIR}/collect-resources.sh"
H4_RUN=/run/tccdiag
LOCK=/var/lock/tcc-vm.lock

# ------------------------------------------------------------------ opcoes
MATRIZ=""; MATRIZ_SHA=""; FASE=""; PARAMS=""; PARAMS_SHA=""; CTRL=""; CTRL_SHA=""; REF=""; REF_SHA=""
JAR=""; JAR_SHA=""; SAIDA=""; BOOT_ESPERADO=""; H4_ID=""; DRY_RUN=0
SERVERS="127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224"
ADMIN="http://127.0.0.1:11222,http://127.0.0.1:11223,http://127.0.0.1:11224"
USERNAME="admin"; PASSWORD="${CS_PASSWORD:-}"

uso() { sed -n '2,60p' "${SELF}" | sed 's/^# \{0,1\}//'; }
erro_uso() { echo "Erro: $*" >&2; exit 2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --matriz|--matriz-sha256|--fase|--parametros|--parametros-sha256|--controlador|--controlador-sha256|\
--referencia|--referencia-sha256|--jar|--jar-sha256|--saida|--boot-esperado|--h4-id|--servers|--admin|\
--username|--password)
            [[ $# -ge 2 ]] || erro_uso "$1 exige valor"
            case "$1" in
                --matriz) MATRIZ="$2" ;; --matriz-sha256) MATRIZ_SHA="$2" ;; --fase) FASE="$2" ;;
                --parametros) PARAMS="$2" ;; --parametros-sha256) PARAMS_SHA="$2" ;;
                --controlador) CTRL="$2" ;; --controlador-sha256) CTRL_SHA="$2" ;;
                --referencia) REF="$2" ;; --referencia-sha256) REF_SHA="$2" ;;
                --jar) JAR="$2" ;; --jar-sha256) JAR_SHA="$2" ;; --saida) SAIDA="$2" ;;
                --boot-esperado) BOOT_ESPERADO="$2" ;; --h4-id) H4_ID="$2" ;;
                --servers) SERVERS="$2" ;; --admin) ADMIN="$2" ;;
                --username) USERNAME="$2" ;; --password) PASSWORD="$2" ;;
            esac
            shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        --help|-h) uso; exit 0 ;;
        *) erro_uso "argumento desconhecido '$1'" ;;
    esac
done

case "${FASE}" in calibracao|nucleo|extra|reposicoes) ;; *) erro_uso "--fase deve ser calibracao, nucleo, extra ou reposicoes" ;; esac
[[ -n "${MATRIZ}" && -f "${MATRIZ}" ]] || erro_uso "--matriz <tsv> existente e obrigatoria"
for par in "MATRIZ_SHA:--matriz-sha256" "PARAMS_SHA:--parametros-sha256" "CTRL_SHA:--controlador-sha256" \
           "REF_SHA:--referencia-sha256"; do
    v="${par%%:*}"; [[ "${!v}" =~ ^[0-9a-f]{64}$ ]] || erro_uso "${par#*:} deve ter 64 hexadecimais"
done
for par in "PARAMS:--parametros" "CTRL:--controlador" "REF:--referencia"; do
    v="${par%%:*}"; [[ -n "${!v}" && -f "${!v}" ]] || erro_uso "${par#*:} <arquivo> existente e obrigatorio"
done
[[ -n "${SAIDA}" ]] || erro_uso "--saida e obrigatoria"
if [[ "${DRY_RUN}" -eq 0 ]]; then
    [[ -n "${JAR}" ]] || erro_uso "--jar e obrigatorio fora do --dry-run"
    [[ "${JAR_SHA}" =~ ^[0-9a-f]{64}$ ]] || erro_uso "--jar-sha256 deve ter 64 hexadecimais"
    [[ "${BOOT_ESPERADO}" =~ ^[0-9a-f-]{36}$ ]] || erro_uso "--boot-esperado <boot_id da VM> e obrigatorio"
    [[ "${H4_ID}" =~ ^[a-z0-9]+-[0-9]{8}T[0-9]{6}Z$ ]] || erro_uso "--h4-id <id do amostrador-infra.sh> e obrigatorio"
    [[ -n "${PASSWORD}" ]] || erro_uso "--password (ou CS_PASSWORD) e obrigatorio fora do --dry-run"
fi
IFS=',' read -r -a SRV <<< "${SERVERS}"
IFS=',' read -r -a ADM <<< "${ADMIN}"
[[ "${#SRV[@]}" -eq 3 && "${#ADM[@]}" -eq 3 ]] || erro_uso "--servers e --admin com 3 itens cada (isn1, isn2, isn3, nessa ordem)"
for k in 0 1 2; do ADM[$k]="${ADM[$k]%/}"; [[ "${ADM[$k]}" == http://* ]] || ADM[$k]="http://${ADM[$k]}"; done
[[ "${SAIDA}" == /* ]] || SAIDA="$(pwd)/${SAIDA}"
FASE_DIR="${SAIDA}/${FASE}"

# ------------------------------------------------------------------ utilitarios (os do runner do rebaseline)
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
json_lista() { local x out=""; for x in "$@"; do out+="${out:+,}$(json_str "${x}")"; done; printf '[%s]' "${out}"; }
ms_para_s() {
    local v="$1" sinal=""
    if [[ "${v}" -lt 0 ]]; then sinal="-"; v=$(( -v )); fi
    printf '%s%d.%03d' "${sinal}" $(( v / 1000 )) $(( v % 1000 ))
}
dormir() { sleep "$1" & wait $!; }
sha256() { sha256sum "$1" | cut -d' ' -f1; }
gravar_atomico() { local alvo="$1"; cat > "${alvo}.tmp" && mv -f "${alvo}.tmp" "${alvo}"; }
py() {  # python3 -I com stdout e stderr sem CR (o python do Windows dos testes escreve CRLF); mantem o exit
    local rc=0 o
    o="$(${PYTHON} -I "$@" 2>&1)" || rc=$?
    printf '%s\n' "${o//$'\r'/}"
    return "${rc}"
}

# ------------------------------------------------------------------ modo de teste (como o R11 do runner)
if [[ "${CS_MODO_TESTE:-}" == 1 ]]; then
    [[ -n "${CS_SHIM_DIR:-}" && -f "${CS_SHIM_DIR}/.cs-shim" ]] \
        || { echo "Erro: CS_MODO_TESTE=1 exige CS_SHIM_DIR com a marca .cs-shim" >&2; exit 4; }
    if [[ "${DRY_RUN}" -eq 0 ]]; then
        for palavra in ${PODMAN} ${PODMAN_COMPOSE}; do
            cam="$(command -v "${palavra}" 2>/dev/null)" || cam=""
            [[ "${cam}" == "${CS_SHIM_DIR}"/* ]] \
                || { echo "Erro: CS_MODO_TESTE=1 com '${palavra}' fora dos shims (${cam})" >&2; exit 4; }
        done
        [[ -n "${CS_TTY_ARQUIVO:-}" ]] || { echo "Erro: CS_MODO_TESTE=1 exige CS_TTY_ARQUIVO" >&2; exit 4; }
        [[ -n "${CS_H4_RUN:-}" && -f "${CS_H4_RUN}/.cs-shim" ]] || { echo "Erro: CS_H4_RUN sem a marca .cs-shim" >&2; exit 4; }
        H4_RUN="${CS_H4_RUN}"
        [[ -n "${CS_COLETOR:-}" ]] && COLETOR="${CS_COLETOR}"
        [[ -n "${CS_INJ_PART:-}" ]] && INJ_PART="${CS_INJ_PART}"
        [[ -n "${CS_INJ_DEGR:-}" ]] && INJ_DEGR="${CS_INJ_DEGR}"
        LOCK="${CS_SHIM_DIR}/tcc-vm.lock"
    fi
elif [[ -n "${CS_TTY_ARQUIVO:-}${CS_H4_RUN:-}${CS_COLETOR:-}${CS_INJ_PART:-}${CS_INJ_DEGR:-}" ]]; then
    echo "Erro: CS_TTY_ARQUIVO, CS_H4_RUN, CS_COLETOR, CS_INJ_PART e CS_INJ_DEGR so valem com CS_MODO_TESTE=1" >&2; exit 4
fi

# ------------------------------------------------------------------ parametros da campanha
declare -A P
ler_parametros() {
    local rc=0 out="" k v
    out="$(py "${CONFERIR}" parametros --arquivo "${PARAMS}")" || rc=$?
    if [[ "${rc}" -ne 0 ]]; then echo "Erro: parametros da campanha recusados: ${out}" >&2; exit 4; fi
    while IFS=$'\t' read -r k v; do [[ -n "${k}" ]] && P[${k}]="${v}"; done <<< "${out}"
}

# ------------------------------------------------------------------ matriz e plano
COLS="i fase rodada posicao celula modo falha papel bloco braco controlador seed repoe"
FILA=()
ler_fila() {  # ler_fila <tsv> <fase|"">: linhas "i celula modo falha papel bloco braco controlador seed"
    local l
    while IFS= read -r l; do FILA+=("${l}"); done < <(awk -F'\t' -v f="$2" \
        'NR == 1 {next} (f == "" || $2 == f) {print $1, $5, $6, $7, $8, $9, $10, $11, $12}' "$1")
}
conferir_cabecalho() {
    local cab; cab="$(head -1 "$1" | tr '\t' ' ' | tr -d '\r')"
    [[ "${cab}" == "${COLS}" ]] || { echo "Erro: cabecalho de $1 nao e o de campanha_s3_matriz.py ('${cab}')" >&2; exit 4; }
}
dir_exec() { printf '%s/exec-%04d-%s-%s' "${SAIDA}" "$1" "$2" "$3"; }
conferir_pareamento() {  # A e B de cada bloco: os dois bracos, a mesma celula e a mesma semente (plano de execucao 5.2)
    local out
    out="$(awk -F'\t' '
        NR == 1 {next}
        $9 == "-" { if ($10 != "A") print "linha " NR ": braco " $10 " sem bloco"; next }
        { k = $9; n[k]++; b[k] = b[k] $10
          c[k] = (k in c && c[k] != $5) ? "DIFERENTE" : $5
          s[k] = (k in s && s[k] != $12) ? "DIFERENTE" : $12 }
        END { for (k in n)
                  if (n[k] != 2 || (b[k] != "AB" && b[k] != "BA") || c[k] == "DIFERENTE" || s[k] == "DIFERENTE")
                      print "bloco " k ": " n[k] " linha(s), bracos " b[k] ", celula " c[k] ", semente " s[k] }' "$1")"
    if [[ -n "${out}" ]]; then
        echo "Erro: pareamento quebrado em $1 (plano 5.2: um A e um B por bloco, mesma celula e mesma semente): ${out//$'\n'/; }" >&2
        exit 4
    fi
}
tirar_nul() {  # o podman machine ssh do Git Bash grava o known_hosts num arquivo NUL no diretorio corrente: nao e bruto
    local x
    for x in "$@"; do
        if [[ -f "${x}/NUL" ]]; then rm -f "${x}/NUL"; log "AVISO: arquivo NUL (artefato do podman machine ssh) removido de ${x}"; fi
    done
}

K_ENC=0
montar_cmd() {  # montar_cmd <modo> <seed> <csv-dir> <controlador> <marcadores> -> CMD e CMD_MASC
    K_ENC=$(( T_ENC_PIOR_S + P[encerramento_folga_s] ))
    CMD=(timeout -k "${K_ENC}s" "${P[exec_teto_s]}s" "${JAVA_CMD[@]}" -jar "${JAR:-<jar>}"
         --scenario S3 --replication "$1" --reset-state on --duration "${P[duracao_s]}"
         --warmup-min-sec "${P[warmup_min_s]}" --rep 1 --seed "$2" --servers "${SERVERS}"
         --username "${USERNAME}" --password "${PASSWORD}" --csv-dir "$3" --rest-url "${ADM[0]}"
         --marker "$5" --w-recovery-sec "${P[w_recovery_s]}" --convergence
         --convergence-timeout-sec "${P[convergencia_teto_s]}" --convergence-period-sec "${P[convergencia_periodo_s]}"
         --controller "$4" --controller-config "${CTRL}" --controller-reference "${REF}"
         --controller-admin "${ADMIN}" --hotrod-timeout-ms "${P[hotrod_timeout_ms]}"
         --rejoin-timeout-sec "${P[religamento_prazo_s]}")
    CMD_MASC=("${CMD[@]}")
    local k
    for k in "${!CMD_MASC[@]}"; do
        if [[ "${CMD_MASC[$k]}" == "--password" ]]; then CMD_MASC[$((k + 1))]="***"; fi
    done
}

descrever_falha() {  # descrever_falha <falha>
    case "$1" in
        F1) echo "F1: inject-crash.sh --node ${P[f1_no]} --duration ${P[f1_dur_s]} --marker a +${P[inj_offset_s]} s da ancora; volta pela REST em ate ${VOLTA_TIMEOUT_S} s" ;;
        F2) echo "F2: inject-partition.sh --minority ${P[f2_no]} --duration ${P[f2_dur_s]} --marker a +${P[inj_offset_s]} s da ancora; dead-man $(( P[f2_dur_s] + DEADMAN_MARGEM_S )) s" ;;
        F4) echo "F4: inject-netdegrade.sh --target ${P[f4_no]} --severity ${P[f4_severidade]} --direction ${P[f4_direcao]} --duration ${P[f4_dur_s]} --marker a +${P[inj_offset_s]} s da ancora; dead-man $(( P[f4_dur_s] + DEADMAN_MARGEM_S )) s" ;;
        *) echo "sem falha" ;;
    esac
}

imprimir_plano() {
    local t0 k=0 l i cel modo falha papel bloco braco ctl seed por_exec
    t0="$(agora_ms)"
    # pior caso por execucao: recriacao + portao K1 + teto do processo (estimativa de calendario, nao limiar)
    por_exec=$(( RECRIAR_TIMEOUT_S + K1_JANELA_S + P[exec_teto_s] ))
    for l in "${FILA[@]}"; do
        read -r i cel modo falha papel bloco braco ctl seed <<< "${l}"
        montar_cmd "${modo}" "${seed}" "$(dir_exec "${i}" "${cel}" "${braco}")/.scratch" "${ctl}" \
            "$(dir_exec "${i}" "${cel}" "${braco}")/marcadores.txt"
        echo "PLANO i=${i} celula=${cel} papel=${papel} bloco=${bloco} braco=${braco} controller=${ctl} seed=${seed} inicio_ate=$(iso_ms $(( t0 + k * por_exec * 1000 ))) $(descrever_falha "${falha}")"
        echo "      ${CMD_MASC[*]}"
        k=$((k + 1))
    done
    echo "PLANO execucoes=${#FILA[@]} encerramento: SIGTERM no teto (${P[exec_teto_s]} s) e SIGKILL ${K_ENC} s depois (T_enc pior caso ${T_ENC_PIOR_S} s + folga ${P[encerramento_folga_s]} s) fim_no_pior_caso=$(iso_ms $(( t0 + k * por_exec * 1000 )))"
}

ARQ_FILA="${MATRIZ}"
[[ "${FASE}" != reposicoes ]] || ARQ_FILA="${SAIDA}/reposicoes/reposicoes.tsv"

# ------------------------------------------------------------------ dry-run
if [[ "${DRY_RUN}" -eq 1 ]]; then
    for par in "MATRIZ:MATRIZ_SHA" "PARAMS:PARAMS_SHA" "CTRL:CTRL_SHA" "REF:REF_SHA"; do
        a="${par%%:*}"; s="${par#*:}"
        [[ "$(sha256 "${!a}")" == "${!s}" ]] || { echo "Erro: SHA-256 de ${!a} difere do registrado" >&2; exit 4; }
    done
    if [[ -n "${JAR}" && -n "${JAR_SHA}" && "$(sha256 "${JAR}")" != "${JAR_SHA}" ]]; then
        echo "Erro: SHA-256 do jar difere do registrado" >&2; exit 4
    fi
    conferir_cabecalho "${MATRIZ}"
    ler_parametros
    PASSWORD="***"
    if [[ "${FASE}" == reposicoes ]]; then
        [[ -f "${ARQ_FILA}" ]] && ler_fila "${ARQ_FILA}" ""
    else
        ler_fila "${MATRIZ}" "${FASE}"
    fi
    [[ ! -f "${ARQ_FILA}" ]] || conferir_pareamento "${ARQ_FILA}"
    echo "DRY-RUN fase=${FASE} matriz=${MATRIZ} (SHA-256 confere) parametros=${PARAMS} ($(grep -c . <<< "$(printf '%s\n' "${!P[@]}")") numeros, todos com origem) controlador=${CTRL}"
    imprimir_plano
    echo "DRY-RUN: nada executado (sem java, podman, sudo, tty)"
    exit 0
fi

# ------------------------------------------------------------------ pre-voo 1: flock (reexecuta-se)
if [[ -z "${CS_SOB_FLOCK:-}" ]]; then
    command -v flock > /dev/null 2>&1 || { echo "Erro: 'flock' nao encontrado" >&2; exit 3; }
    export CS_SOB_FLOCK=1
    exec flock -n -F -E 4 "${LOCK}" bash "${SELF}" "${ARGS_ORIGINAIS[@]}"
fi

# ------------------------------------------------------------------ pre-voo 2: hashes, dependencias, boot, parametros
for par in "MATRIZ:MATRIZ_SHA" "PARAMS:PARAMS_SHA" "CTRL:CTRL_SHA" "REF:REF_SHA" "JAR:JAR_SHA"; do
    a="${par%%:*}"; s="${par#*:}"
    [[ -f "${!a}" ]] || { echo "Erro: '${!a}' ausente" >&2; exit 4; }
    [[ "$(sha256 "${!a}")" == "${!s}" ]] || { echo "Erro: SHA-256 de ${!a} difere do registrado" >&2; exit 4; }
done
for dep in "${JAVA_CMD[0]}" "${PYTHON}" curl sudo nsenter tc systemd-run systemctl flock sha256sum awk timeout mkfifo df; do
    command -v "${dep}" > /dev/null 2>&1 || { echo "Erro: dependencia '${dep}' ausente" >&2; exit 3; }
done
for dep in ${PODMAN} ${PODMAN_COMPOSE}; do command -v "${dep}" > /dev/null 2>&1 || { echo "Erro: '${dep}' ausente" >&2; exit 3; }; done
rc_sudo=0; sudo -n true > /dev/null 2>&1 || rc_sudo=$?
[[ "${rc_sudo}" -eq 0 ]] || { echo "Erro: 'sudo -n true' falhou (sudo sem senha e obrigatorio)" >&2; exit 3; }
NSENTER_BIN="$(command -v nsenter)"; TC_BIN="$(command -v tc)"
ler_boot() { cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r\n'; }
if [[ "${CS_MODO_TESTE:-}" == 1 ]]; then ler_boot() { cat "${CS_SHIM_DIR}/boot_id" | tr -d '\r\n'; }; fi
BOOT="$(ler_boot)"
[[ "${BOOT}" == "${BOOT_ESPERADO}" ]] || { echo "Erro: boot_id da VM (${BOOT}) difere de --boot-esperado (${BOOT_ESPERADO})" >&2; exit 4; }
conferir_cabecalho "${MATRIZ}"
ler_parametros

# ------------------------------------------------------------------ fila da fase
mkdir -p "${SAIDA}" "${FASE_DIR}"
if [[ -f "${SAIDA}/matriz.tsv" ]]; then
    [[ "$(sha256 "${SAIDA}/matriz.tsv")" == "${MATRIZ_SHA}" ]] || { echo "Erro: ${SAIDA}/matriz.tsv difere da matriz dada" >&2; exit 4; }
else
    cp "${MATRIZ}" "${SAIDA}/matriz.tsv"
fi
if [[ "${FASE}" == reposicoes ]]; then
    [[ -f "${ARQ_FILA}" ]] || { echo "Erro: ${ARQ_FILA} ausente (rode antes campanha_s3_matriz.py repor)" >&2; exit 4; }
    conferir_cabecalho "${ARQ_FILA}"
    ler_fila "${ARQ_FILA}" ""
else
    ler_fila "${MATRIZ}" "${FASE}"
fi
[[ "${#FILA[@]}" -gt 0 ]] || { echo "Erro: nenhuma execucao da fase ${FASE} em ${ARQ_FILA}" >&2; exit 4; }
conferir_pareamento "${ARQ_FILA}"
TEM_B=0; TEM_REDE=0
for l in "${FILA[@]}"; do
    read -r _i _c _m f _p _b br _ct _s <<< "${l}"
    [[ "${br}" == B ]] && TEM_B=1
    [[ "${f}" == F2 || "${f}" == F4 ]] && TEM_REDE=1
done

# ------------------------------------------------------------------ pre-voo 3: controlador, h4, bundle
rc=0; out="$(py "${CONFERIR}" controlador --arquivo "${CTRL}" --hotrod-timeout-ms "${P[hotrod_timeout_ms]}" \
    --exigir-p2-desabilitada $([[ "${TEM_B}" -eq 1 ]] && echo --exigir-politica))" || rc=$?
[[ "${rc}" -eq 0 ]] || { echo "Erro: --controlador recusado: ${out}" >&2; exit 4; }
CTRL_INFO="${out}"
H4_UNIT="tcc-amostrador-${H4_ID}"
H4_AMOSTRAS="${H4_RUN}/${H4_ID}/amostras.txt"
h4_unit_ativa() { systemctl is-active --quiet "${H4_UNIT}"; }
h4_unit_ativa || { echo "Erro: unit ${H4_UNIT} inativa (inicie o amostrador-infra.sh antes; C3 secao 2)" >&2; exit 4; }
[[ -s "${H4_AMOSTRAS}" ]] || { echo "Erro: ${H4_AMOSTRAS} ausente ou vazio" >&2; exit 4; }

bundle_lista() {  # SHA-256 de tudo o que a fase usa; a retomada recusa se mudar
    local f
    for f in "${SELF}" "${CONFERIR}" "${H4S3}" "${SCRIPT_DIR}/campanha_s3_matriz.py" "${REPO_DIR}/analysis/h4.py" \
             "${CRIA_CACHES}" "${INJ_CRASH}" "${INJ_PART}" "${INJ_DEGR}" "${COMPOSE_ARQ}" \
             "${REPO_DIR}/cluster/infinispan-sem-caches.xml" "${REPO_DIR}/cluster/jgroups-tcp.xml" \
             "${DIR_CACHES}"/*.json "${JAR}" "${MATRIZ}" "${PARAMS}" "${CTRL}" "${REF}"; do
        printf '%s  %s\n' "$(sha256 "${f}")" "${f##*/}"
    done
}
BUNDLE_LISTA="$(bundle_lista)"
BUNDLE_SHA="$(printf '%s\n' "${BUNDLE_LISTA}" | sha256sum | cut -d' ' -f1)"
PROGRESSO="${FASE_DIR}/progresso.json"
if [[ -f "${PROGRESSO}" ]]; then
    ant="$(sed -n 's/.*"bundle_sha256": *"\([0-9a-f]*\)".*/\1/p' "${PROGRESSO}" | head -1)"
    [[ "${ant}" == "${BUNDLE_SHA}" ]] || { echo "Erro: bundle mudou desde a ultima execucao da fase (${ant} -> ${BUNDLE_SHA}); retomada recusada" >&2; exit 4; }
fi
printf '%s\n' "${BUNDLE_LISTA}" > "${FASE_DIR}/bundle.sha256"

# ------------------------------------------------------------------ pre-voo 4: o programa aceita as duas linhas de comando
for ctl in shadow active; do
    [[ "${ctl}" == shadow || "${TEM_B}" -eq 1 ]] || continue
    mkdir -p "${FASE_DIR}/.dry-run"
    montar_cmd async 1 "${FASE_DIR}/.dry-run" "${ctl}" "${FASE_DIR}/.dry-run/marcadores.txt"
    rc=0; "${CMD[@]:4}" --dry-run > "${FASE_DIR}/dry-run-${ctl}.txt" 2>&1 || rc=$?
    [[ "${rc}" -eq 0 ]] || { echo "Erro: o programa recusou a linha de comando ${ctl} (--dry-run exit ${rc}): $(tail -3 "${FASE_DIR}/dry-run-${ctl}.txt")" >&2; exit 4; }
done

# ------------------------------------------------------------------ estado e encerramento
WPID=""; LPID=""; INJ_PID=""; COL_PID=""; EV_FD=""; F1_ATIVA=0; REDE_ATIVA=""; DEADMAN_UNIT=""
MOTIVO=""; FASE_CRIADA=1
LOG_ARQ="${FASE_DIR}/orquestrador.log"

finalizar() { MOTIVO="$2"; exit "$1"; }

parar_carga() {  # SIGTERM ao timeout (que repassa ao java e arma o SIGKILL K s depois); espera ate K + 2 s
    local k
    [[ -n "${WPID}" ]] || return 0
    E_PAROU=1
    if kill -0 "${WPID}" 2>/dev/null; then
        kill -TERM "${WPID}" 2>/dev/null || true
        for ((k = 0; k < (K_ENC + 2) * 5; k++)); do kill -0 "${WPID}" 2>/dev/null || break; sleep 0.2; done
        if kill -0 "${WPID}" 2>/dev/null; then
            log "AVISO: carga ainda viva ${K_ENC} s depois do SIGTERM; SIGKILL"
            kill -KILL "${WPID}" 2>/dev/null || true
        fi
    fi
}

curar_rede() {  # curar_rede: --heal do injetor ativo e confere; 0 se nao sobra regra
    local rc=0
    case "${REDE_ATIVA}" in
        F2) bash "${INJ_PART}" --heal --minority "${P[f2_no]}" > /dev/null 2>&1 || rc=$? ;;
        F4) bash "${INJ_DEGR}" --heal > /dev/null 2>&1 || rc=$? ;;
        *) return 0 ;;
    esac
    residuo_rede && return 1
    return 0
}

desarmar_deadman() {
    local rc=0 resto=""
    [[ -n "${DEADMAN_UNIT}" ]] || return 0
    sudo systemctl stop "${DEADMAN_UNIT}.timer" > /dev/null 2>&1 || rc=$?
    rc=0; resto="$(systemctl list-timers --all --no-legend 'tcc-heal-cs-*' 2>&1)" || rc=$?
    if [[ "${rc}" -eq 0 && -z "${resto//[[:space:]]/}" ]]; then DEADMAN_UNIT=""; return 0; fi
    log "timers tcc-heal-cs-* restantes (rc=${rc}): ${resto}"
    return 1
}

limpar() {
    local rc=$?
    trap - EXIT INT TERM
    if [[ -n "${INJ_PID}" ]] && kill -0 "${INJ_PID}" 2>/dev/null; then kill -TERM "${INJ_PID}" 2>/dev/null || true; wait "${INJ_PID}" 2>/dev/null || true; fi
    if [[ -n "${REDE_ATIVA}" ]]; then
        if curar_rede; then
            log "trap: ${REDE_ATIVA} curada; desarmando o dead-man ${DEADMAN_UNIT}"
            desarmar_deadman || log "trap: AVISO: dead-man nao confirmou o desarme"
        else
            log "trap: AVISO: regra de rede continua; dead-man ${DEADMAN_UNIT} MANTIDO armado"
        fi
    fi
    if [[ "${F1_ATIVA}" -eq 1 ]]; then
        local r2=0
        log "trap: F1 em curso; ${PODMAN} start ${P[f1_no]}"
        ${PODMAN} start "${P[f1_no]}" > /dev/null 2>&1 || r2=$?
        [[ "${r2}" -eq 0 ]] || log "trap: AVISO: podman start ${P[f1_no]} saiu com ${r2}"
    fi
    parar_carga
    if [[ -n "${COL_PID}" ]]; then kill -TERM "${COL_PID}" 2>/dev/null || true; wait "${COL_PID}" 2>/dev/null || true; fi
    if [[ -n "${LPID}" ]]; then kill -TERM "${LPID}" 2>/dev/null || true; fi
    if [[ "${EXITS_PREVISTOS}" != *" ${rc} "* ]]; then
        MOTIVO="infra: erro inesperado do orquestrador (exit ${rc})${MOTIVO:+; ${MOTIVO}}"
        rc=6
    fi
    MOTIVO="${MOTIVO:-interrompido (exit ${rc})}"
    MOTIVO="${MOTIVO//$'\n'/ | }"   # a ultima linha do log traz exit e motivo inteiros (contrato do runner)
    gravar_progresso "" "fim"
    gravar_resumo
    tirar_nul "${SAIDA}" "${FASE_DIR}"
    log "FIM exit=${rc} motivo=${MOTIVO}"
    gravar_manifesto
    exit "${rc}"
}

dirs_da_fase() {
    local l i cel _m _f _p _b br _ct _s d
    DIRS_FASE=()
    for l in "${FILA[@]}"; do
        read -r i cel _m _f _p _b br _ct _s <<< "${l}"
        d="$(dir_exec "${i}" "${cel}" "${br}")"
        if [[ -d "${d}" ]]; then DIRS_FASE+=("${d##*/}"); fi
        for d in "${d}".abortada-*; do if [[ -d "${d}" ]]; then DIRS_FASE+=("${d##*/}"); fi; done
    done
}

gravar_progresso() {  # gravar_progresso <i em curso ou ""> <passo>: troca atomica
    local i="$1" passo="$2"
    printf '{"fase": %s, "bundle_sha256": %s, "boot_id": %s, "em_curso": {"i": %s, "passo": %s}, "atualizado_utc": %s}\n' \
        "$(json_str "${FASE}")" "$(json_str "${BUNDLE_SHA}")" "$(json_str "${BOOT}")" "$(json_ou_null "${i}")" \
        "$(json_str "${passo}")" "$(json_str "$(iso_ms "$(agora_ms)")")" | gravar_atomico "${PROGRESSO}"
}

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
    ex.append({"dir": d, "i": ch.get("i"), "celula": ch.get("celula"), "braco": ch.get("braco"),
               "bloco": ch.get("bloco"),
               "veredito": "ABORTADA (DT-014)" if ".abortada-" in d else ch.get("veredito", "SEM CHECAGEM"),
               "motivos": ch.get("motivos", []), "avisos": ch.get("avisos", [])})
cont = {}
for e in ex:
    cont[e["veredito"]] = cont.get(e["veredito"], 0) + 1
res = {"fase": fase_dir.name, "motivo_fim": motivo, "execucoes": ex, "contagem": cont}
(fase_dir / "resumo.json").write_bytes((json.dumps(res, indent=1, ensure_ascii=False) + "\n").encode("utf-8"))
md = ["# Resumo da fase %s" % fase_dir.name, "", "Fim: %s" % motivo, "",
      "| Execucao | Veredito | Motivos | Avisos |", "|---|---|---|---|"]
md += ["| %s | %s | %s | %s |" % (e["dir"], e["veredito"], "; ".join(e["motivos"]).replace("|", "/") or "-",
                                  "; ".join(e["avisos"]).replace("|", "/") or "-") for e in ex]
md += ["", "Contagem: " + ", ".join("%s=%d" % kv for kv in sorted(cont.items()))]
(fase_dir / "resumo.md").write_bytes(("\n".join(md) + "\n").encode("utf-8"))
# manifesto do comparar_bracos.py (cabecalho bloco/celula/braco/execucao): so blocos com os dois bracos ACEITA,
# de todas as fases ja rodadas nesta saida
blocos = {}
for c in sorted(saida.glob("exec-*/checagem.json")):
    if ".abortada-" in c.parent.name:
        continue
    ch = json.loads(c.read_text(encoding="utf-8"))
    if ch.get("bloco") in (None, "-") or ch.get("veredito") != "ACEITA":
        continue
    blocos.setdefault((ch["celula"], str(ch["bloco"])), {})[ch["braco"]] = c.parent.name
linhas = ["bloco\tcelula\tbraco\texecucao"]
for (cel, b), par in sorted(blocos.items(), key=lambda kv: (kv[0][0], int(kv[0][1]))):
    if set(par) == {"A", "B"}:
        linhas += ["%s\t%s\t%s\t%s" % (b, cel, br, par[br]) for br in ("A", "B")]
if len(linhas) > 1:
    (saida / "manifesto-bracos.tsv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
PY
    [[ "${rc}" -eq 0 ]] || log "AVISO: resumo nao gravado (python rc=${rc})"
}

gravar_manifesto() {
    local rc=0 alvos=("${FASE}" matriz.tsv) d
    [[ "${FASE}" != reposicoes ]] || alvos+=(reposicoes)
    dirs_da_fase
    for d in "${DIRS_FASE[@]}"; do alvos+=("${d}"); done
    ( cd "${SAIDA}" && find "${alvos[@]}" -type f ! -path "${FASE}/MANIFEST.sha256" ! -name '*.fifo' ! -name NUL -print0 \
        | LC_ALL=C sort -z | xargs -0 sha256sum ) > "${FASE_DIR}/MANIFEST.sha256.tmp" || rc=$?
    if [[ "${rc}" -eq 0 ]]; then mv -f "${FASE_DIR}/MANIFEST.sha256.tmp" "${FASE_DIR}/MANIFEST.sha256"
    else echo "AVISO: MANIFEST.sha256 nao gravado (rc=${rc})" >&2; fi
}

trap 'limpar' EXIT
trap 'MOTIVO="sinal TERM"; exit 143' TERM
trap 'MOTIVO="sinal INT"; exit 130' INT
log "inicio fase=${FASE} saida=${SAIDA} matriz=${MATRIZ} bundle=${BUNDLE_SHA} boot=${BOOT} h4=${H4_ID}"
tirar_nul "${SAIDA}"
gravar_progresso "" "pre-voo"

# ------------------------------------------------------------------ ambiente.md
cmd_txt() { local rc=0 o=""; o="$("$@" 2>&1)" || rc=$?; printf '%s' "${o}"; [[ "${rc}" -eq 0 ]] || printf '\n(falhou: exit %s)' "${rc}"; }
{
    echo "## Pre-voo $(iso_ms "$(agora_ms)") (fase ${FASE})"
    echo
    echo '```'
    echo "date -u: $(date -u)"
    echo "uname -a: $(cmd_txt uname -a)"
    echo "boot_id: ${BOOT}"
    echo "java -version:"; cmd_txt "${JAVA_CMD[@]}" -version; echo
    echo "podman version:"; cmd_txt ${PODMAN} version; echo
    echo "python3: $(cmd_txt "${PYTHON}" --version)"
    echo "bundle_sha256: ${BUNDLE_SHA} (lista em ${FASE}/bundle.sha256)"
    echo "controlador: ${CTRL} ${CTRL_INFO//$'\n'/ }"
    echo "parametros:"; for k in "${!P[@]}"; do echo "  ${k}=${P[${k}]}"; done | sort
    echo "constantes: K1=${K1_JANELA_S}/${K1_PERIODO_S}/${K1_TIMEOUT_S}s volta=${VOLTA_TIMEOUT_S}s recriar=${RECRIAR_TIMEOUT_S}s ancora=${ANCORA_TIMEOUT_S}s dead-man=+${DEADMAN_MARGEM_S}s ${DEADMAN_PRECISAO} T_enc_pior=${T_ENC_PIOR_S}s"
    echo "h4: unit ${H4_UNIT}, amostras ${H4_AMOSTRAS}"
    echo '```'
    echo
    echo "Execucoes da fase (ordem da matriz):"
    echo
    for l in "${FILA[@]}"; do echo "- ${l}"; done
    echo
} >> "${FASE_DIR}/ambiente.md"

# ------------------------------------------------------------------ GO (como o runner do rebaseline)
GATES_JSON=""; TTY_N=0; INICIO_ORQ="$(iso_ms "$(agora_ms)")"
gravar_gates() {
    ${PYTHON} -I - "${FASE_DIR}/gates.json" "${INICIO_ORQ}" "[${GATES_JSON}]" <<'PY'
import json, sys
from pathlib import Path
p, inicio, gates = Path(sys.argv[1]), sys.argv[2], json.loads(sys.argv[3])
d = json.loads(p.read_text(encoding="utf-8")) if p.exists() else {"execucoes_do_orquestrador": []}
ex = [e for e in d["execucoes_do_orquestrador"] if e.get("inicio") != inicio]
ex.append({"inicio": inicio, "gates": gates})
d["execucoes_do_orquestrador"] = ex
p.write_bytes((json.dumps(d, indent=1, ensure_ascii=False) + "\n").encode("utf-8"))
PY
}
ler_gate() {  # ler_gate <tipo> <esperado> [i]
    local tipo="$1" esperado="$2" linha="" rc=0 agora
    if [[ "${CS_MODO_TESTE:-}" == 1 ]]; then
        TTY_N=$((TTY_N + 1))
        linha="$(sed -n "${TTY_N}p" "${CS_TTY_ARQUIVO}")"
    else
        { printf 'Digite exatamente: %s\n> ' "${esperado}" > /dev/tty; } 2> /dev/null || log "AVISO: /dev/tty indisponivel para o prompt"
        IFS= read -r -t "${GATE_TIMEOUT_S}" linha < /dev/tty || rc=$?
        if [[ "${rc}" -ne 0 ]]; then log "leitura de /dev/tty falhou (gate ${tipo}, rc=${rc})"; linha=""; fi
    fi
    agora="$(iso_ms "$(agora_ms)")"
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
PASSWORD="***"
imprimir_plano | tee -a "${LOG_ARQ}"
PASSWORD="${PASSWORD_REAL}"
HOJE="$(date +%F)"
log "GATE fase pedido: aguardando 'go ${FASE} ${HOJE}' em /dev/tty"
ler_gate fase "go ${FASE} ${HOJE}"

# ------------------------------------------------------------------ REST, saude e netns
REST_COD=""
rest() {  # rest <metodo> <url> <arquivo-do-corpo>: codigo HTTP em REST_COD (000 se o curl falhou)
    local cred rc=0
    cred="${USERNAME}:${PASSWORD_REAL}"; cred="${cred//\\/\\\\}"; cred="${cred//\"/\\\"}"
    REST_COD="$(printf 'user = "%s"\n' "${cred}" | curl -s -o "$3" -w '%{http_code}' --max-time "${REST_PRAZO_S}" \
        --digest -K - -X "$1" "$2" 2>/dev/null)" || rc=$?
    [[ "${rc}" -eq 0 && "${REST_COD}" =~ ^[0-9]{3}$ ]] || REST_COD=000
}
TMPC="$(mktemp -d)"
podman_campo() { ${PODMAN} inspect -f "$1" "$2" 2>/dev/null; }
pid_de() {
    local p="" rc=0
    p="$(podman_campo '{{.State.Pid}}' "$1")" || rc=$?
    [[ "${rc}" -eq 0 && "${p}" =~ ^[0-9]+$ && "${p}" != 0 ]] || return 1
    echo "${p}"
}
guarda_netns() {  # padrao R4 do inject-netdegrade.sh (copiado do runner)
    local p ns_no="" ns_host="" rc=0
    p="$(pid_de "$1")" || { log "PID de $1 nao resolvido"; return 1; }
    ns_no="$(sudo readlink "/proc/${p}/ns/net" 2>/dev/null)" || rc=$?
    ns_host="$(sudo readlink /proc/1/ns/net 2>/dev/null)" || rc=$?
    [[ "${rc}" -eq 0 && -n "${ns_no}" && -n "${ns_host}" && "${ns_no}" != "${ns_host}" ]]
}
RESIDUO=""
residuo_rede() {  # 0 (verdade) se algum netns tem netem, clsact ou o prio do inject-netdegrade; ou iptables alem das politicas
    local no p out rc
    RESIDUO=""
    for no in ${NOS}; do
        p="$(pid_de "${no}")" || continue
        rc=0; out="$(sudo "${NSENTER_BIN}" -t "${p}" -n "${TC_BIN}" qdisc show dev "${IFACE}" 2>&1)" || rc=$?
        if [[ "${rc}" -ne 0 || "${out}" == *netem* || "${out}" == *clsact* || "${out}" == *"prio 1:"* ]]; then
            RESIDUO+="${RESIDUO:+,}${no}:tc"
        fi
        if command -v iptables > /dev/null 2>&1; then
            rc=0; out="$(sudo "${NSENTER_BIN}" -t "${p}" -n iptables -S 2>&1)" || rc=$?
            if [[ "${rc}" -eq 0 ]] && grep -qv '^-P ' <<< "${out}"; then RESIDUO+="${RESIDUO:+,}${no}:iptables"; fi
        fi
    done
    [[ -n "${RESIDUO}" ]]
}
AM_OK=0; AM_JSON=""
amostra_saude() {  # AM_OK=1 se os 3 endpoints dizem HEALTHY com 3 nos
    local ok=1 partes="" k st nn corpo
    for k in 0 1 2; do
        rest GET "${ADM[$k]}/rest/v2/cache-managers/default/health" "${TMPC}/saude"
        corpo="$(cat "${TMPC}/saude" 2>/dev/null)"; st=""; nn=""
        if [[ "${corpo}" =~ \"health_status\":\"([A-Z_]+)\" ]]; then st="${BASH_REMATCH[1]}"; fi
        if [[ "${corpo}" =~ \"number_of_nodes\":([0-9]+) ]]; then nn="${BASH_REMATCH[1]}"; fi
        if [[ "${REST_COD}" != 200 || "${st}" != HEALTHY || "${nn}" != 3 ]]; then ok=0; fi
        partes+="${partes:+,}{\"url\":$(json_str "${ADM[$k]}"),\"http\":${REST_COD},\"health_status\":$(json_str "${st}"),\"number_of_nodes\":$(json_ou_null "${nn}")}"
    done
    AM_OK="${ok}"
    AM_JSON="{\"utc\":$(json_str "$(iso_ms "$(agora_ms)")"),\"ok\":$([[ ${ok} -eq 1 ]] && echo true || echo false),\"endpoints\":[${partes}]}"
}
esperar_saude() {  # esperar_saude <timeout-s> <periodo-s>: 0 quando 3 HEALTHY; ESPERA_MS
    local t0 agora
    t0="$(agora_ms)"
    while :; do
        amostra_saude
        agora="$(agora_ms)"; ESPERA_MS=$(( agora - t0 ))
        [[ "${AM_OK}" -eq 1 ]] && return 0
        [[ $(( agora - t0 )) -lt $(( $1 * 1000 )) ]] || return 1
        dormir "$2"
    done
}
portao_k1() {  # 3 HEALTHY e sem residuo de rede em todas as amostras dos ultimos 60 s (ate 600 s)
    local d="$1" t0 agora inicio_bom="" amostras="" ok=0 res
    t0="$(agora_ms)"
    while :; do
        amostra_saude
        res=0; residuo_rede && res=1
        amostras+="${amostras:+,}${AM_JSON%\}},\"residuo\":$(json_str "${RESIDUO}")}"
        agora="$(agora_ms)"
        if [[ "${AM_OK}" -eq 1 && "${res}" -eq 0 ]]; then
            [[ -n "${inicio_bom}" ]] || inicio_bom="${agora}"
            if [[ $(( agora - inicio_bom )) -ge $(( K1_JANELA_S * 1000 )) ]]; then ok=1; break; fi
        else
            inicio_bom=""
        fi
        [[ $(( agora - t0 )) -lt $(( K1_TIMEOUT_S * 1000 )) ]] || break
        dormir "${K1_PERIODO_S}"
    done
    printf '{"ok":%s,"janela_s":%d,"periodo_s":%d,"timeout_s":%d,"amostras":[%s]}\n' \
        "$([[ ${ok} -eq 1 ]] && echo true || echo false)" "${K1_JANELA_S}" "${K1_PERIODO_S}" "${K1_TIMEOUT_S}" \
        "${amostras}" > "${d}/portao.json"
    [[ "${ok}" -eq 1 ]]
}
started_at() {  # StartedAt (ms) dos tres nos, "isn1=<ms> isn2=<ms> isn3=<ms>"
    local no v out=""
    for no in ${NOS}; do v="$(podman_campo '{{.State.StartedAt.UnixMilli}}' "${no}")" || v="?"; out+="${out:+ }${no}=${v}"; done
    echo "${out}"
}
no_do_rotulo() {  # no_do_rotulo <host:porta>: isn1..isn3 pela posicao em --admin
    local k
    for k in 0 1 2; do [[ "${ADM[$k]#http://}" == "$1" ]] && { echo "isn$((k + 1))"; return 0; }; done
    return 1
}

# ------------------------------------------------------------------ camada 3 (configuracao lida de volta)
CAM3_JSON=""; CAM3_DIVERGE=""; CAM3_CONFIG_DIVERGE=0
camada3() {  # camada3 <pre|pos>: rebalanceamento, disponibilidade e configuracao dos 6 caches nos 3 nos
    local k c u esperado obtido partes=""
    CAM3_DIVERGE=""; CAM3_CONFIG_DIVERGE=0
    for k in 0 1 2; do
        u="${ADM[$k]}"
        rest GET "${u}/rest/v2/container" "${TMPC}/c3"
        if [[ "${REST_COD}" != 200 ]] || ! grep -q '"rebalancing_enabled":true' "${TMPC}/c3"; then
            CAM3_DIVERGE+="${CAM3_DIVERGE:+; }isn$((k + 1)) rebalancing_enabled!=true (HTTP ${REST_COD})"
        fi
        for c in ${CACHES}; do
            rest GET "${u}/rest/v2/caches/${c}?action=get-availability" "${TMPC}/c3"
            obtido="$(tr -d '\r\n' < "${TMPC}/c3" 2>/dev/null)"
            if [[ "${REST_COD}" != 200 || "${obtido}" != AVAILABLE ]]; then
                CAM3_DIVERGE+="${CAM3_DIVERGE:+; }isn$((k + 1)) ${c} disponibilidade='${obtido}' (HTTP ${REST_COD})"
            fi
            rest GET "${u}/rest/v2/caches/${c}?action=config" "${TMPC}/c3"
            esperado="{\"${c}\":$(tr -d '\r\n' < "${DIR_CACHES}/${c}.json")}"
            obtido="$(tr -d '\r\n' < "${TMPC}/c3" 2>/dev/null)"
            if [[ "${REST_COD}" != 200 || "${obtido}" != "${esperado}" ]]; then
                CAM3_DIVERGE+="${CAM3_DIVERGE:+; }isn$((k + 1)) ${c} configuracao difere do JSON (HTTP ${REST_COD})"
                CAM3_CONFIG_DIVERGE=1
            fi
        done
    done
    CAM3_JSON="{\"momento\":$(json_str "$1"),\"utc\":$(json_str "$(iso_ms "$(agora_ms)")"),\"divergencias\":$(json_str_ou_null "${CAM3_DIVERGE}")}"
    [[ -z "${CAM3_DIVERGE}" ]]
}

# ------------------------------------------------------------------ recriacao do cluster e criacao dos caches
recriar_cluster() {  # recriar_cluster <dir>: down/up com o XML sem caches, 3 HEALTHY, seis caches, StartedAt
    local d="$1" rc=0 t0 no s linha_c nome dur
    t0="$(agora_ms)"
    E_RECRIACAO_MS="${t0}"
    ${PODMAN_COMPOSE} -f "${COMPOSE_ARQ}" down > "${d}/compose-down.log" 2>&1 || rc=$?
    [[ "${rc}" -eq 0 ]] || finalizar 6 "infra: podman-compose down saiu com ${rc} antes de i=${E_I}"
    rc=0; ${PODMAN_COMPOSE} -f "${COMPOSE_ARQ}" up -d > "${d}/compose-up.log" 2>&1 || rc=$?
    [[ "${rc}" -eq 0 ]] || finalizar 6 "infra: podman-compose up saiu com ${rc} antes de i=${E_I}"
    esperar_saude "${RECRIAR_TIMEOUT_S}" "${RECRIAR_PERIODO_S}" \
        || finalizar 10 "parada: cluster recriado sem 3 HEALTHY em ${RECRIAR_TIMEOUT_S} s antes de i=${E_I}"
    for no in ${NOS}; do
        guarda_netns "${no}" || finalizar 4 "pre-voo: guarda de netns de ${no} falhou (netns do host ou ilegivel)"
        s="$(podman_campo '{{.State.StartedAt.UnixMilli}}' "${no}")" || s=""
        [[ "${s}" =~ ^[0-9]+$ && "${s}" -ge $(( t0 - 1000 )) ]] \
            || finalizar 6 "infra: StartedAt de ${no} (${s}) anterior a recriacao (decisao de 28/10)"
    done
    rc=0
    CRIA_CACHES_SENHA="${PASSWORD_REAL}" bash "${CRIA_CACHES}" --url "${ADM[0]}" \
        --conferir "${ADM[0]},${ADM[1]},${ADM[2]}" --usuario "${USERNAME}" --dir "${DIR_CACHES}" ${CACHES} \
        > "${d}/caches.tsv" 2> "${d}/caches.err" || rc=$?
    [[ "${rc}" -eq 0 ]] || finalizar 6 "infra: cria-caches.sh saiu com ${rc} antes de i=${E_I} ($(head -c 200 "${d}/caches.err"))"
    while IFS=$'\t' read -r _u nome m _url cod dur; do
        [[ "${m}" == POST && "${dur}" =~ ^[0-9]+\.[0-9]+$ ]] || continue
        if [[ "${dur/./}" =~ ^0*([0-9]+)$ ]] && [[ $(( 10#${BASH_REMATCH[1]} )) -gt "${CRIACAO_AVISO_MS}" ]]; then
            E_AVISOS+=("criacao de ${nome} levou ${dur} s (> 8,0 s, acoes-controlador secao 6.4; anotado, sem abortar)")
        fi
    done < "${d}/caches.tsv"
    E_STARTED_PRE="$(started_at)"
}

# ------------------------------------------------------------------ uma execucao
gravar_execucao() {
    local d="$1" cmdj="" a
    for a in "${CMD_MASC[@]}"; do cmdj+="${cmdj:+,}$(json_str "${a}")"; done
    cat > "${d}/execucao.json" <<EOF
{
 "i": ${E_I},
 "fase": $(json_str "${FASE}"),
 "celula": $(json_str "${E_CEL}"),
 "papel": $(json_str "${E_PAPEL}"),
 "bloco": $(json_str "${E_BLOCO}"),
 "braco": $(json_str "${E_BRACO}"),
 "controller": $(json_str "${E_CTL}"),
 "modo": $(json_str "${E_MODO}"),
 "falha": $(json_str "${E_FALHA}"),
 "seed": ${E_SEED},
 "jar_sha256": $(json_str "${JAR_SHA}"),
 "parametros_sha256": $(json_str "${PARAMS_SHA}"),
 "controlador_sha256": $(json_str "${CTRL_SHA}"),
 "bundle_sha256": $(json_str "${BUNDLE_SHA}"),
 "boot_id": $(json_str "${BOOT}"),
 "comando": [${cmdj}],
 "encerramento": {"timeout_s": ${P[exec_teto_s]}, "kill_apos_s": ${K_ENC}, "t_enc_pior_s": ${T_ENC_PIOR_S}, "folga_s": ${P[encerramento_folga_s]}},
 "recriacao_ms": $(json_ou_null "${E_RECRIACAO_MS}"),
 "started_at_pre": $(json_str_ou_null "${E_STARTED_PRE}"),
 "started_at_pos": $(json_str_ou_null "${E_STARTED_POS}"),
 "camada3_pre": ${E_CAM3_PRE:-null},
 "camada3_pos": ${E_CAM3_POS:-null},
 "lancamento_ms": $(json_ou_null "${E_LANC_MS}"),
 "lancamento_utc": $(json_str_ou_null "${E_LANC_UTC}"),
 "ancora_ms": $(json_ou_null "${E_ANC_MS}"),
 "ancora_utc": $(json_str_ou_null "${E_ANC_UTC}"),
 "janela_h4": ${E_JANELA_H4:-null},
 "disparo_ms": $(json_ou_null "${E_DISP_MS}"),
 "inj_inicio_offset_s": $(json_ou_null "${E_OFFSET}"),
 "injetor_exit": $(json_ou_null "${E_IEXIT}"),
 "deadman": ${E_DEADMAN:-null},
 "rede_pos": ${E_REDE_POS:-null},
 "falha_efetiva": ${E_FALHA_EF:-null},
 "volta_f1": ${E_VOLTA_F1:-null},
 "sinal_fim_carga": ${E_SINAL_JSON:-null},
 "religamento": ${E_RELIG_JSON:-null},
 "fim_utc": $(json_str_ou_null "${E_FIM_UTC}"),
 "workload_exit": $(json_ou_null "${E_WEXIT}"),
 "teto_estourado": ${E_TETO:-false}
}
EOF
}

gravar_checagem() {  # gravar_checagem <dir> <veredito> : motivos em E_MOTIVOS, avisos em E_AVISOS
    local d="$1" v="$2"
    printf '{"i": %s, "fase": %s, "celula": %s, "bloco": %s, "braco": %s, "veredito": %s, "motivos": %s, "avisos": %s}\n' \
        "${E_I}" "$(json_str "${FASE}")" "$(json_str "${E_CEL}")" "$(json_str "${E_BLOCO}")" "$(json_str "${E_BRACO}")" \
        "$(json_str "${v}")" "$(json_lista "${E_MOTIVOS[@]}")" \
        "$(json_lista "${E_AVISOS[@]}")" > "${d}/checagem.json"
}

instante_efetivo_f1() {  # como no runner: parada e reinicio do no do F1 pelo podman, relativos a ancora
    local rc=0 fim="" ini="" pj="null" rj="null" no="${P[f1_no]}"
    fim="$(podman_campo '{{.State.FinishedAt.UnixMilli}}' "${no}")" || rc=$?
    if [[ "${rc}" -eq 0 && "${fim}" =~ ^[0-9]+$ ]]; then pj="$(ms_para_s $(( fim - E_ANC_MS )))"; fi
    rc=0; ini="$(podman_campo '{{.State.StartedAt.UnixMilli}}' "${no}")" || rc=$?
    if [[ "${rc}" -eq 0 && "${ini}" =~ ^[0-9]+$ ]]; then rj="$(ms_para_s $(( ini - E_ANC_MS )))"; fi
    E_FALHA_EF="{\"f1_parado_desde_ancora_s\":${pj},\"f1_reiniciado_desde_ancora_s\":${rj}}"
}

disparar_injecao() {
    local d="$1" rc=0 dur unit
    E_DISP_MS="$(agora_ms)"
    E_OFFSET="$(ms_para_s $(( E_DISP_MS - E_ANC_MS )))"
    case "${E_FALHA}" in
        F1)
            bash "${INJ_CRASH}" --node "${P[f1_no]}" --duration "${P[f1_dur_s]}" --marker "${d}/marcadores.txt" \
                > "${d}/injetor.log" 2>&1 &
            INJ_PID=$!; F1_ATIVA=1 ;;
        F2|F4)
            if [[ "${E_FALHA}" == F2 ]]; then dur="${P[f2_dur_s]}"; else dur="${P[f4_dur_s]}"; fi
            unit="tcc-heal-cs-${E_I}"
            if [[ "${E_FALHA}" == F2 ]]; then
                sudo systemd-run --quiet --unit="${unit}" --on-active="$(( dur + DEADMAN_MARGEM_S ))s" \
                    --timer-property="${DEADMAN_PRECISAO}" --setenv=PODMAN="${PODMAN}" --working-directory="${REPO_DIR}" \
                    bash "${INJ_PART}" --heal --minority "${P[f2_no]}" > "${d}/deadman.log" 2>&1 || rc=$?
            else
                sudo systemd-run --quiet --unit="${unit}" --on-active="$(( dur + DEADMAN_MARGEM_S ))s" \
                    --timer-property="${DEADMAN_PRECISAO}" --setenv=PODMAN="${PODMAN}" --working-directory="${REPO_DIR}" \
                    bash "${INJ_DEGR}" --heal > "${d}/deadman.log" 2>&1 || rc=$?
            fi
            if [[ "${rc}" -ne 0 ]]; then
                E_DEADMAN="{\"unit\":$(json_str "${unit}"),\"armado\":false,\"rc\":${rc}}"
                E_INFRA_STOP=6; E_MOTIVOS+=("infra: dead-man ${unit} nao armou (systemd-run rc=${rc}); falha nao aplicada")
                parar_carga; return 0
            fi
            DEADMAN_UNIT="${unit}"; REDE_ATIVA="${E_FALHA}"
            E_DEADMAN="{\"unit\":$(json_str "${unit}"),\"armado\":true,\"on_active_s\":$(( dur + DEADMAN_MARGEM_S )),\"precisao\":$(json_str "${DEADMAN_PRECISAO}")}"
            if [[ "${E_FALHA}" == F2 ]]; then
                bash "${INJ_PART}" --minority "${P[f2_no]}" --duration "${dur}" --marker "${d}/marcadores.txt" \
                    > "${d}/injetor.log" 2>&1 &
            else
                bash "${INJ_DEGR}" --target "${P[f4_no]}" --severity "${P[f4_severidade]}" --direction "${P[f4_direcao]}" \
                    --duration "${dur}" --marker "${d}/marcadores.txt" > "${d}/injetor.log" 2>&1 &
            fi
            INJ_PID=$! ;;
    esac
    INJ_ESTADO=rodando
    log "exec i=${E_I}: ${E_FALHA} disparada a +${E_OFFSET} s da ancora"
}

concluir_injecao() {
    local rc=0 seguem
    wait "${INJ_PID}" || rc=$?
    E_IEXIT="${rc}"; INJ_PID=""; INJ_ESTADO=feita
    log "exec i=${E_I}: injetor saiu com ${rc}"
    if [[ "${E_FALHA}" == F1 ]]; then
        F1_ATIVA=0; seguem="${EXITS_F1_SEGUEM}"
        instante_efetivo_f1
        # P-35: o exit do inject-crash.sh nao prova a volta do no; a REST prova
        VOLTA_ESTADO=esperando; VOLTA_T0="$(agora_ms)"; VOLTA_PROX=0
    else
        seguem="${EXITS_REDE_SEGUEM}"
        local residual=false extra=false
        if residuo_rede; then
            extra=true
            log "exec i=${E_I}: resto de rede depois do injetor (${RESIDUO}); --heal uma vez"
            curar_rede || residual=true
        fi
        E_REDE_POS="{\"residuo\":${residual},\"heal_extra\":${extra},\"onde\":$(json_str "${RESIDUO}")}"
        if [[ "${residual}" == true ]]; then
            E_INFRA_STOP=6; E_MOTIVOS+=("infra: resto de rede (${RESIDUO}) que o --heal nao removeu; dead-man ${DEADMAN_UNIT} mantido")
            parar_carga; return 0
        fi
        REDE_ATIVA=""
        if ! desarmar_deadman; then
            E_INFRA_STOP=6; E_MOTIVOS+=("infra: dead-man ${DEADMAN_UNIT} nao desarmou"); parar_carga; return 0
        fi
    fi
    if [[ "${seguem}" != *" ${E_IEXIT} "* ]]; then
        E_INFRA_STOP=6; E_MOTIVOS+=("infra: injetor ${E_FALHA} saiu com ${E_IEXIT} (so${seguem}seguem)")
        parar_carga
    elif [[ "${E_IEXIT}" -ne 0 ]]; then
        E_RECUSAS+=("injetor ${E_FALHA} saiu com ${E_IEXIT} (recusa, como o K6 do rebaseline)")
    fi
}

amostra_volta_f1() {
    local agora
    amostra_saude
    agora="$(agora_ms)"
    if [[ "${AM_OK}" -eq 1 ]]; then
        VOLTA_ESTADO=ok
        E_VOLTA_F1="{\"ok\":true,\"espera_s\":$(ms_para_s $(( agora - VOLTA_T0 ))),\"metodo\":\"REST (P-35)\"}"
        log "exec i=${E_I}: ${P[f1_no]} de volta (3 HEALTHY pela REST) em $(ms_para_s $(( agora - VOLTA_T0 ))) s"
    elif [[ $(( agora - VOLTA_T0 )) -ge $(( VOLTA_TIMEOUT_S * 1000 )) ]]; then
        VOLTA_ESTADO=falhou
        E_VOLTA_F1="{\"ok\":false,\"espera_s\":$(ms_para_s $(( agora - VOLTA_T0 ))),\"metodo\":\"REST (P-35)\",\"ultima\":${AM_JSON}}"
        E_INFRA_STOP=10; E_MOTIVOS+=("infra: ${P[f1_no]} nao voltou a 3 HEALTHY pela REST em ${VOLTA_TIMEOUT_S} s depois do F1 (P-35; parada (e))")
        parar_carga
    else
        VOLTA_PROX=$(( agora + VOLTA_PERIODO_S * 1000 ))
    fi
}

tratar_sinal_fim() {  # tratar_sinal_fim <alvo>: religa o no parado pelo AC-5 (P-6 da V-W5)
    local alvo="$1" no rc=0 t0 parados="" n est
    E_SINAL_MS="$(agora_ms)"; E_SINAL_ALVO="${alvo}"
    if [[ "${alvo}" != "-" ]]; then
        if no="$(no_do_rotulo "${alvo}")"; then
            t0="$(agora_ms)"
            ${PODMAN} start "${no}" > "${E_DIR}/religamento.log" 2>&1 || rc=$?
            E_RELIG_NO="${no}"
            E_RELIG_JSON="{\"alvo\":$(json_str "${alvo}"),\"no\":$(json_str "${no}"),\"podman_start_ms\":${t0},\"podman_start_rc\":${rc},\"duracao_ms\":$(( $(agora_ms) - t0 ))}"
            [[ "${rc}" -eq 0 ]] || E_INFRAS+=("religamento: podman start ${no} saiu com ${rc}")
        else
            E_INFRAS+=("religamento: alvo do AC-5 '${alvo}' nao corresponde a nenhuma URL de --admin")
        fi
    fi
    # O8 da V-W2a: um no parado sem AC-5 APLICADA (por exemplo, AC-5 FALHOU que parou o no) e desvio; religa
    for n in ${NOS}; do
        [[ "${n}" == "${E_RELIG_NO:-}" ]] && continue
        est="$(podman_campo '{{.State.Status}}' "${n}")" || est="?"
        if [[ "${est}" != running ]]; then
            parados+="${parados:+,}${n}"
            ${PODMAN} start "${n}" >> "${E_DIR}/religamento.log" 2>&1 || true
        fi
    done
    [[ -z "${parados}" ]] || E_INFRAS+=("no(s) parado(s) no fim da carga sem AC-5 APLICADA na linha de fim: ${parados} (religado(s); O8 da V-W2a)")
    E_SINAL_JSON="{\"recebido_ms\":${E_SINAL_MS},\"ac5_alvo\":$(json_str "${alvo}"),\"outros_parados\":$(json_str_ou_null "${parados}")}"
    log "exec i=${E_I}: linha de fim da carga (ac5_alvo=${alvo})${E_RELIG_NO:+; podman start ${E_RELIG_NO}}"
}

leitor_stdout() {  # stdin: saida do programa; stdout: eventos (FIFO em memoria); o log vai a disco por outro processo
    local l
    exec 3> >(cat >> "$1")
    while IFS= read -r l || [[ -n "${l}" ]]; do
        if [[ "${l}" =~ ${ANCORA_RE} ]]; then printf 'ANCORA %s %s %s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}"; fi
        if [[ "${l}" =~ ${SINAL_RE} ]]; then printf 'SINAL %s\n' "${BASH_REMATCH[3]}"; fi
        printf '%s\n' "${l}" >&3
    done
    printf 'EOF\n'
    exec 3>&-
}

conferir_meta() {  # conferir_meta <decisoes>: braco certo e, no B, que ele atua (A-3 da V-W5, O10 da V-W2a)
    local rc=0 out=""
    out="$(py "${CONFERIR}" meta --decisoes "$1" --braco "${E_BRACO}" --param-sha256 "${CTRL_SHA}" --exigir-p2-desabilitada)" || rc=$?
    E_META="${out//$'\n'/ }"
    if [[ "${rc}" -ne 0 ]]; then
        E_INFRA_STOP=6; E_MOTIVOS+=("infra: meta da trilha recusada (exit ${rc}): ${E_META}")
        log "exec i=${E_I}: meta recusada: ${E_META}; parando a carga"
        parar_carga
    fi
}

rodar_carga() {  # lanca o programa, le os eventos e conduz a falha; volta quando o stdout fecha
    local d="$1" scratch="$2" ev tipo agora
    rm -f "${d}/.java.fifo" "${d}/.eventos.fifo"
    mkfifo "${d}/.java.fifo" "${d}/.eventos.fifo"
    exec {EV_FD}<> "${d}/.eventos.fifo"
    leitor_stdout "${d}/stdout.log" < "${d}/.java.fifo" > "${d}/.eventos.fifo" &
    LPID=$!
    E_LANC_MS="$(agora_ms)"; E_LANC_UTC="$(iso_ms "${E_LANC_MS}")"
    log "exec i=${E_I}: lancando a carga (${CMD_MASC[*]})"
    "${CMD[@]}" > "${d}/.java.fifo" 2>&1 &
    WPID=$!
    INJ_ESTADO=pendente; VOLTA_ESTADO=""; local fim_stdout=0
    while [[ "${fim_stdout}" -eq 0 ]]; do
        # Eventos ja disponiveis: read -t 0 so testa (nao le); a leitura da linha disponivel bloqueia ate o '\n', que o
        # leitor escreve junto. A espera entre as voltas e o dormir (sleep & wait), o padrao do runner do rebaseline:
        # o SIGTERM chega no wait e o trap roda. Com read -t 0,2 esperando no FIFO, o bash do Git Bash caiu com SIGSEGV
        # ao receber o SIGTERM (P-50), e a leitura vencida no meio da linha perdia o evento (P-49).
        while [[ "${fim_stdout}" -eq 0 ]] && IFS= read -r -t 0 -u "${EV_FD}" 2>/dev/null; do
            ev=""
            IFS= read -r -u "${EV_FD}" ev || break
            tipo="${ev%% *}"
            case "${tipo}" in
                ANCORA)
                    if [[ -z "${E_ANC_MS}" ]]; then
                        E_ANC_MS="$(agora_ms)"; E_ANC_UTC="$(iso_ms "${E_ANC_MS}")"
                        read -r _t _w ini tot <<< "${ev}"
                        local f1p=null
                        [[ "${E_FALHA}" != F1 ]] || f1p=$(( P[inj_offset_s] + P[f1_dur_s] ))
                        E_JANELA_H4="{\"ini_desde_ancora_s\":${ini},\"fim_desde_ancora_s\":${tot},\"f1_reinicio_padrao_desde_ancora_s\":${f1p}}"
                        log "exec i=${E_I}: ancora a +$(ms_para_s $(( E_ANC_MS - E_LANC_MS ))) s do lancamento (medicao +${ini}..+${tot} s)"
                        conferir_meta "${scratch}/decisoes-rep-001.jsonl"
                        iniciar_coletor "${d}"
                    fi ;;
                SINAL) tratar_sinal_fim "${ev#SINAL }" ;;
                EOF) fim_stdout=1 ;;
            esac
        done
        agora="$(agora_ms)"
        if [[ -z "${E_ANC_MS}" && -z "${E_SEM_ANCORA}" && $(( agora - E_LANC_MS )) -ge $(( ANCORA_TIMEOUT_S * 1000 )) ]]; then
            E_SEM_ANCORA=1; E_RECUSAS+=("sem ancora em ${ANCORA_TIMEOUT_S} s"); log "exec i=${E_I}: sem ancora; parando a carga"
            parar_carga
        fi
        if [[ -n "${E_ANC_MS}" && "${E_FALHA}" != none && "${INJ_ESTADO}" == pendente && -z "${E_INFRA_STOP}" \
              && "${agora}" -ge $(( E_ANC_MS + P[inj_offset_s] * 1000 )) ]]; then
            if [[ "${E_FALHA}" != F1 ]]; then
                guarda_netns "${P[${E_FALHA,,}_no]}" || { E_INFRA_STOP=6; E_MOTIVOS+=("infra: guarda de netns antes da ${E_FALHA}"); parar_carga; INJ_ESTADO=feita; }
            fi
            [[ "${INJ_ESTADO}" != pendente ]] || disparar_injecao "${d}"
        fi
        if [[ "${INJ_ESTADO}" == rodando ]] && ! kill -0 "${INJ_PID}" 2>/dev/null; then concluir_injecao; fi
        if [[ "${VOLTA_ESTADO}" == esperando && "${agora}" -ge "${VOLTA_PROX}" ]]; then amostra_volta_f1; fi
        [[ "${fim_stdout}" -eq 1 ]] || dormir "${POLL_S}"
    done
    local rc=0
    wait "${WPID}" || rc=$?
    E_WEXIT="${rc}"; WPID=""
    # 124: o timeout mandou o SIGTERM no teto; 137 sem parada pedida: SIGKILL do timeout (teto + K)
    if [[ "${rc}" -eq 124 || ( "${rc}" -eq 137 && -z "${E_PAROU}" ) ]]; then E_TETO=true; fi
    # carga que terminou sem a ancora antes do prazo de 120 s: tambem e recusa "sem ancora" (K6 do rebaseline)
    if [[ -z "${E_ANC_MS}" && -z "${E_SEM_ANCORA}" ]]; then E_SEM_ANCORA=1; E_RECUSAS+=("sem ancora (a carga terminou antes)"); fi
    wait "${LPID}" 2>/dev/null || true
    LPID=""
    exec {EV_FD}>&-
    EV_FD=""
    rm -f "${d}/.java.fifo" "${d}/.eventos.fifo"
    if [[ "${INJ_ESTADO}" == rodando ]]; then concluir_injecao; fi
    while [[ "${VOLTA_ESTADO}" == esperando ]]; do amostra_volta_f1; [[ "${VOLTA_ESTADO}" != esperando ]] || dormir "${VOLTA_PERIODO_S}"; done
    parar_coletor
    E_FIM_UTC="$(iso_ms "$(agora_ms)")"
    log "exec i=${E_I}: carga saiu com ${E_WEXIT}$([[ "${E_TETO}" == true ]] && echo " (teto estourado)")"
}

iniciar_coletor() {  # OP-13 (energia estimada): collect-resources.sh durante a carga, com a JVM do programa
    local d="$1" jpid=""
    jpid="$(pgrep -P "${WPID}" 2>/dev/null | head -1)" || jpid=""
    if [[ -n "${jpid}" ]]; then
        PODMAN="${PODMAN}" bash "${COLETOR}" --saida "${d}" --rep 1 --containers "${NOS}" --pid "workload=${jpid}" \
            > "${d}/coletor.log" 2>&1 &
    else
        E_AVISOS+=("coletor sem a JVM do programa (pgrep -P ${WPID} vazio)")
        PODMAN="${PODMAN}" bash "${COLETOR}" --saida "${d}" --rep 1 --containers "${NOS}" > "${d}/coletor.log" 2>&1 &
    fi
    COL_PID=$!
}
parar_coletor() {
    [[ -n "${COL_PID}" ]] || return 0
    kill -TERM "${COL_PID}" 2>/dev/null || true
    wait "${COL_PID}" 2>/dev/null || true
    COL_PID=""
}

pos_execucao() {  # pos-checagem igual a pre: boot, StartedAt explicado, saude, residuo, camada 3, trilha
    local d="$1" n antes depois rc out
    [[ "$(ler_boot)" == "${BOOT}" ]] || E_MOTIVOS+=("infra: boot_id mudou durante a execucao")
    E_STARTED_POS="$(started_at)"
    for n in ${NOS}; do
        antes="$(sed -n "s/.*${n}=\([0-9?]*\).*/\1/p" <<< "${E_STARTED_PRE}")"
        depois="$(sed -n "s/.*${n}=\([0-9?]*\).*/\1/p" <<< "${E_STARTED_POS}")"
        if [[ "${antes}" != "${depois}" ]]; then
            if [[ ( "${E_FALHA}" == F1 && "${n}" == "${P[f1_no]}" ) || "${n}" == "${E_RELIG_NO:-}" ]]; then continue; fi
            E_MOTIVOS+=("infra: ${n} reiniciou sem F1 nem religamento (StartedAt ${antes} -> ${depois}; decisao de 28/10)")
        fi
    done
    if ! esperar_saude "${VOLTA_TIMEOUT_S}" "${VOLTA_PERIODO_S}"; then
        E_INFRA_STOP=10; E_MOTIVOS+=("infra: cluster sem 3 HEALTHY ${VOLTA_TIMEOUT_S} s depois da execucao")
    fi
    if residuo_rede; then E_INFRA_STOP=6; E_MOTIVOS+=("infra: resto de rede depois da execucao (${RESIDUO})"); fi
    out="$(systemctl list-timers --all --no-legend 'tcc-heal-cs-*' 2>&1)" || true
    [[ -z "${out//[[:space:]]/}" ]] || { E_INFRA_STOP=6; E_MOTIVOS+=("infra: timer de dead-man sobrando: ${out}"); }
    camada3 pos || true
    E_CAM3_POS="${CAM3_JSON}"
    if [[ -n "${CAM3_DIVERGE}" ]]; then
        if [[ "${CAM3_CONFIG_DIVERGE}" -eq 1 ]]; then
            E_MOTIVOS+=("infra: camada 3 depois da execucao: configuracao de cache mudou (${CAM3_DIVERGE})")
        else
            E_AVISOS+=("camada 3 depois da execucao: ${CAM3_DIVERGE} (o cluster e recriado antes da proxima)")
        fi
    fi
    if [[ -f "${d}/decisoes-rep-001.jsonl" ]]; then
        rc=0; out="$(py "${CONFERIR}" trilha --decisoes "${d}/decisoes-rep-001.jsonl" \
            --sinal-alvo "${E_SINAL_ALVO:--}")" || rc=$?
        printf '%s\n' "${out}" > "${d}/trilha-ac5.txt"
        case "${rc}" in
            0) [[ "${out}" == *$'ac5_falhou_executada	0'* ]] || E_AVISOS+=("AC-5 FALHOU executado na trilha (O8 da V-W2a): $(grep ac5_falhou_alvos <<< "${out}")") ;;
            5) E_MOTIVOS+=("infra: trilha e linha de fim da carga discordam sobre o AC-5: ${out//$'\n'/ }") ;;
            *) E_MOTIVOS+=("infra: trilha ilegivel (exit ${rc})") ;;
        esac
    else
        E_RECUSAS+=("sem decisoes-rep-001.jsonl")
    fi
    [[ -n "${E_SINAL_MS}" ]] || E_RECUSAS+=("sem a linha de fim da carga")
    if [[ -n "${E_RELIG_NO:-}" ]] && ! grep -q '"t_religamento_ns" : [0-9]' "${d}/convergencia-resumo-rep-001.json" 2>/dev/null; then
        E_AVISOS+=("AC-5 religado sem t_religamento_ns no resumo (Delta_conv nao avaliavel na analise, P-6 da V-W5)")
    fi
    if grep -q '"convergencia_estado" : "sem_rodada_valida"' "${d}/convergencia-resumo-rep-001.json" 2>/dev/null; then
        E_AVISOS+=("convergencia sem rodada valida (convergencia_estado = sem_rodada_valida): Delta_conv e divergencia nao avaliaveis na analise")
    fi
    E_MOTIVOS+=("${E_INFRAS[@]}")
}

avaliar_h4() {  # C3 e DT-019 pelo campanha_s3_h4.py; parada h4 pela regra 4.3
    local d="$1" rc=0 out v
    if ! h4_unit_ativa; then
        E_MOTIVOS+=("h4c: unit ${H4_UNIT} inativa (C3 secao 4.3, regra 3)"); E_INFRA_STOP=10; return 0
    fi
    out="$(py "${H4S3}" fase --amostras "${H4_AMOSTRAS}" --saida "${SAIDA}" --fase "${FASE}" --exec "${d}")" || rc=$?
    printf '%s\n' "${out}" > "${d}/h4.txt"
    v="$(sed -n 's/^VEREDITO [0-9]* //p' <<< "${out}")"
    case "${rc}" in
        0|30) ;;
        4) E_MOTIVOS+=("h4c: amostras ilegiveis (C3 secao 3.3)"); E_INFRA_STOP=10; return 0 ;;
        *) E_INFRA_STOP=6; E_MOTIVOS+=("infra: campanha_s3_h4.py saiu com ${rc}"); return 0 ;;
    esac
    if [[ "${v}" == *h4* ]]; then E_MOTIVOS+=("h4: ${v}"); fi
    [[ "${rc}" -ne 30 ]] || { E_INFRA_STOP=10; E_PARADA_H4="$(grep '^PARADA H4' <<< "${out}" | head -1)"; }
}

rodar_execucao() {  # rodar_execucao "<i> <celula> <modo> <falha> <papel> <bloco> <braco> <controlador> <seed>"
    read -r E_I E_CEL E_MODO E_FALHA E_PAPEL E_BLOCO E_BRACO E_CTL E_SEED <<< "$1"
    local d scratch f outro
    d="$(dir_exec "${E_I}" "${E_CEL}" "${E_BRACO}")"; E_DIR="${d}"
    E_RECRIACAO_MS=""; E_STARTED_PRE=""; E_STARTED_POS=""; E_CAM3_PRE=""; E_CAM3_POS=""; E_LANC_MS=""; E_LANC_UTC=""
    E_ANC_MS=""; E_ANC_UTC=""; E_JANELA_H4=""; E_DISP_MS=""; E_OFFSET=""; E_IEXIT=""; E_DEADMAN=""; E_REDE_POS=""
    E_FALHA_EF=""; E_VOLTA_F1=""; E_SINAL_JSON=""; E_SINAL_MS=""; E_SINAL_ALVO=""; E_RELIG_JSON=""; E_RELIG_NO=""
    E_FIM_UTC=""; E_WEXIT=""; E_TETO=false; E_SEM_ANCORA=""; E_INFRA_STOP=""; E_PARADA_H4=""; E_META=""; E_PAROU=""
    E_MOTIVOS=(); E_RECUSAS=(); E_AVISOS=(); E_INFRAS=()
    if [[ -d "${d}" ]]; then
        if [[ -f "${d}/checagem.json" ]]; then log "exec i=${E_I} ${E_CEL} ${E_BRACO}: ja checada; pulando"; return 0; fi
        if [[ ! -e "${d}/stdout.log" ]]; then
            local ab; ab="${d}.abortada-$(date -u +%Y%m%dT%H%M%SZ)"
            mv "${d}" "${ab}"
            log "exec i=${E_I}: interrompida antes da carga (DT-014); mantida em ${ab##*/}; o mesmo i roda de novo"
        else
            log "exec i=${E_I}: interrompida depois do lancamento da carga (DT-014); RECUSADA e reposta"
            E_MOTIVOS=("interrompida depois do lancamento da carga (DT-014)")
            gravar_checagem "${d}" RECUSADA
            return 0
        fi
    fi
    # bloco ja perdido: o outro braco nao e rodado (o bloco inteiro e refeito, plano de execucao 5.2)
    if [[ "${E_BLOCO}" != "-" ]]; then
        for outro in "${SAIDA}"/exec-*-"${E_CEL}"-[AB]; do
            [[ -f "${outro}/checagem.json" && "${outro}" != "${d}" ]] || continue
            grep -q "\"bloco\": \"${E_BLOCO}\"" "${outro}/checagem.json" || continue
            if ! grep -q '"veredito": "ACEITA"' "${outro}/checagem.json"; then
                mkdir -p "${d}"
                E_MOTIVOS=("bloco ${E_BLOCO} ja perdido em ${outro##*/}: o bloco sera refeito inteiro (plano 5.2)")
                gravar_checagem "${d}" PULADA
                log "exec i=${E_I}: PULADA (bloco ${E_BLOCO} ja perdido)"
                return 0
            fi
        done
    fi
    # GO de rede antes da primeira F2/F4 da invocacao, antes do diretorio e do K1 (C2 do runner)
    if [[ ( "${E_FALHA}" == F2 || "${E_FALHA}" == F4 ) && "${GATE_REDE_DADO}" -eq 0 ]]; then
        log "GATE rede pedido: primeira F2/F4 (i=${E_I}); aguardando 'go rede ${E_I}' em /dev/tty"
        ler_gate rede "go rede ${E_I}" "${E_I}"
        GATE_REDE_DADO=1
    fi
    mkdir -p "${d}"
    gravar_progresso "${E_I}" "recriacao"
    log "exec i=${E_I} ${E_CEL} braco=${E_BRACO} bloco=${E_BLOCO} seed=${E_SEED}: recriando o cluster"
    recriar_cluster "${d}"
    if ! camada3 pre; then
        finalizar 6 "infra: camada 3 antes de i=${E_I}: ${CAM3_DIVERGE}"
    fi
    E_CAM3_PRE="${CAM3_JSON}"
    gravar_progresso "${E_I}" "k1"
    log "exec i=${E_I}: portao K1"
    portao_k1 "${d}" || finalizar 10 "parada (e): portao K1 nao passou em ${K1_TIMEOUT_S} s antes de i=${E_I}"
    scratch="${d}/.scratch"
    mkdir -p "${scratch}"
    montar_cmd "${E_MODO}" "${E_SEED}" "${scratch}" "${E_CTL}" "${d}/marcadores.txt"
    gravar_progresso "${E_I}" "carga"
    rodar_carga "${d}" "${scratch}"
    for f in "${scratch}"/* "${scratch}"/.[!.]*; do [[ -e "${f}" ]] || continue; mv -n "${f}" "${d}/"; done
    rmdir "${scratch}" 2> /dev/null || log "exec i=${E_I}: AVISO: ${scratch} nao ficou vazio"
    gravar_progresso "${E_I}" "pos"
    pos_execucao "${d}"
    [[ "${E_WEXIT}" -eq 0 ]] || E_RECUSAS+=("carga saiu com ${E_WEXIT}")
    [[ "${E_TETO}" != true ]] || E_RECUSAS+=("teto do processo (${P[exec_teto_s]} s) estourado")
    gravar_execucao "${d}"
    avaliar_h4 "${d}"
    local v=ACEITA
    if [[ "${#E_RECUSAS[@]}" -gt 0 ]]; then v=RECUSADA; E_MOTIVOS+=("${E_RECUSAS[@]}"); fi
    if [[ -n "${E_INFRA_STOP}" ]] || printf '%s\n' "${E_MOTIVOS[@]}" | grep -qE '^(infra|h4)'; then v=DESCARTADA-INFRA; fi
    gravar_checagem "${d}" "${v}"
    tirar_nul "${d}"
    ( cd "${d}" && find . -type f ! -name MANIFEST.sha256 ! -name NUL -print0 | LC_ALL=C sort -z | xargs -0 sha256sum ) > "${d}/MANIFEST.sha256"
    log "exec i=${E_I}: ${v}${E_MOTIVOS[*]+ (${E_MOTIVOS[*]})}"
    gravar_progresso "" "entre-execucoes"
    if [[ -n "${E_PARADA_H4}" ]]; then finalizar 10 "parada h4 depois de i=${E_I}: ${E_PARADA_H4}"; fi
    case "${E_INFRA_STOP}" in
        6) finalizar 6 "infra em i=${E_I}: ${E_MOTIVOS[*]}" ;;
        10) finalizar 10 "parada em i=${E_I}: ${E_MOTIVOS[*]}" ;;
    esac
}

# ------------------------------------------------------------------ laco da fase
GATE_REDE_DADO=0
for linha in "${FILA[@]}"; do
    if [[ -e "${SAIDA}/PARAR" ]]; then finalizar 0 "parada pedida pelo arquivo PARAR (antes de $(cut -d' ' -f1 <<< "${linha}"))"; fi
    [[ "$(ler_boot)" == "${BOOT}" ]] || finalizar 10 "parada: boot_id mudou (queda da VM; complemento 1, h1)"
    h4_unit_ativa || finalizar 10 "parada h4: unit ${H4_UNIT} inativa (C3 secao 4.3, regra 3)"
    livre="$(df -Pm "${SAIDA}" | awk 'NR == 2 {print $4}')"
    [[ "${livre}" =~ ^[0-9]+$ && $(( livre * 10 )) -ge $(( P[volume_exec_mb] * DISCO_FATOR_X10 )) ]] \
        || finalizar 10 "parada: disco com ${livre} MB livres < 1,5 x ${P[volume_exec_mb]} MB (volume de uma execucao)"
    rodar_execucao "${linha}"
done
finalizar 0 "fase ${FASE} concluida"
