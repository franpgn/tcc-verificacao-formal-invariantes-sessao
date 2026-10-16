#!/usr/bin/env bash
#
# inject-netdegrade.sh
#
# Injeta a falha F4 (degradacao parcial de rede, B-29.2) no cluster Infinispan:
# perda de pacotes (aleatoria ou em rajada), atraso com jitter, duplicacao,
# reordenacao e corrupcao, de forma simetrica ou ASSIMETRICA, SOMENTE no
# trafego entre os nos do cluster. O trafego Hot Rod do cliente (origem no
# gateway da bridge) e o SSH nao casam o filtro e passam intactos. Plano:
# docs/plano-fechamento-etapa-formal-2026-10-15.md (T-C1) e
# docs/degradacao-de-rede.md.
#
# Mecanismo: disciplina 'prio' (3 bandas, tudo na banda 0 por padrao) na RAIZ
# da interface do container, 'netem' na banda 3 e um filtro 'u32' por IP de
# destino do par que manda o trafego para essa banda:
#     tc qdisc  add dev <if> root handle 1: prio bands 3 priomap 0 0 ... 0
#     tc qdisc  add dev <if> parent 1:3 handle 30: netem <parametros>
#     tc filter add dev <if> parent 1: protocol ip prio 1 u32 match ip dst <ip>/32 flowid 1:3
# sempre via 'sudo nsenter -t <pid> -n tc ...' a partir do HOST, dentro do netns
# do container (a imagem nao tem tc nem NET_ADMIN). So ha EGRESS: a perda de
# SAIDA do alvo e aplicada no proprio alvo (filtro por IP de cada par); a perda
# de ENTRADA no alvo e obtida aplicando a mesma regra no egress de cada par com
# destino ao alvo (um nsenter por par). Assim '--direction out|in|both' e
# '--loss' / '--loss-in' (valores diferentes por sentido) dao perda assimetrica
# sem 'ifb'. IPs resolvidos por 'podman inspect', nunca fixos.
#
# Convivencia: inject-partition.sh (F2) usa 'clsact' (nao ocupa a raiz) mas, por
# desenho experimental, F2 e F4 NAO sao combinados no mesmo no: aqui o injetor
# toma tambem o lock de F2 do no (inject-partition-<no>.lock) e recusa se
# houver 'clsact' no no (exit 7). inject-jitter.sh (F3) usa 'root netem' sem
# filtro e ocupa a raiz: se a raiz de um no tocado estiver ocupada por qualquer
# qdisc que nao seja a do proprio injetor, sai com exit 7 SEM alterar nada.
# F3 e F4 em nos DIFERENTES coexistem.
#
# Robustez: trap registrado ANTES de qualquer alteracao; pre-limpeza idempotente
# (so remove a qdisc 'prio 1:' do proprio injetor, nunca raiz alheia); flock por
# no; sonda do kernel com os parametros REAIS (prio/netem/u32, gemodel, reorder,
# corrupt) em IP de TEST-NET-1; verificacao de efetividade (taxa de perda no
# fio medida por 'ping' dentro do netns do alvo, com tolerancia binomial, e
# contadores 'Sent'/'dropped' de 'tc -s qdisc'); verificacao final de ausencia
# de residuo; '--heal' para o caso de SIGKILL, que nenhum trap captura.
#
# Dependencias (execucao real): podman, nsenter (util-linux), tc (iproute2),
# sudo, flock e, com verificacao por ping, ping (iputils) NO HOST; modulos
# sch_prio, sch_netem e cls_u32. '--dry-run' e '--help' nao requerem nada.
#
# Exit codes:
#   0  sucesso (degradacao aplicada, verificada, aguardada e removida)
#   1  erro generico (inclui lock ocupado e residuo apos a limpeza)
#   2  argumento invalido ou --help
#   3  dependencia ausente (podman, nsenter, sudo, flock, ping)
#   4  no (alvo ou par) nao encontrado, nao-running, sem PID ou sem IP
#   5  kernel rejeitou prio/netem/u32 (modulo ausente ou parametro invalido)
#   6  a degradacao NAO se efetivou (taxa de perda observada incompativel com
#      a configurada, contador 'Sent' zerado ou perda sem descartes)
#   7  raiz ocupada: qdisc raiz alheia (F3) ou clsact (F2) ou lock de F2 num
#      no que seria tocado; nada foi alterado
#
# Cobertura: F4 (degradacao parcial de rede) -- B-29.2.
set -euo pipefail

NOME="$(basename "$0")"
# Binario do podman. Cluster ROOTFUL (VM Hyper-V): PODMAN="sudo podman".
PODMAN="${PODMAN:-podman}"
LOCK_DIR="${LOCK_DIR:-/tmp}"
NODES_PADRAO="isn1,isn2,isn3"
IFACE_PADRAO="eth0"
DURACAO_PADRAO=120
# Abaixo disto a janela e menor que a deteccao do FD_ALL3 (~40 s + 1 s de
# VERIFY_SUSPECT2): a vista pode nem mudar.
DURACAO_AVISO=45
PING_PADRAO=200
PROBE_IP="192.0.2.1"   # TEST-NET-1 (RFC 5737): nunca roteado, so para a sonda
TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PRIO_ARGS="bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
# Qdiscs raiz padrao de uma interface de container (nao contam como ocupadas).
RAIZ_BENIGNA='noqueue|pfifo_fast|fq_codel|mq|fq|pfifo'

usage() {
    cat <<EOF
Uso: ${NOME} --target <no> [--nodes isn1,isn2,isn3] [--peers <no[,no...]>]
            [--direction out|in|both] [--iface eth0] [--duration <seg>]
            [--severity N1..N5 | --loss <pct>] [--loss-corr <pct>] [--loss-in <pct>]
            [--loss-model random|gemodel] [--burst <pacotes>]
            [--delay <Nms>] [--jitter <Nms>] [--duplicate <pct>]
            [--reorder <pct[,corr]>] [--corrupt <pct>]
            [--ping-count <n>] [--marker <arquivo>] [--dry-run]
${NOME} --heal [--target <no>] [--nodes ...] [--iface eth0] [--dry-run]
${NOME} --help

Injeta a falha F4 (degradacao parcial de rede) entre o no --target e seus
pares: perda, atraso/jitter, duplicacao, reordenacao e corrupcao, via netem na
banda 3 de uma disciplina prio na raiz, com filtro u32 por IP de par, sempre
por 'sudo nsenter -t <pid> -n' (host-side). O trafego Hot Rod do cliente e o
SSH nao sao afetados. Tudo e removido ao fim da janela, em sinal (INT, TERM,
HUP) ou erro.

Direcao (so ha egress; a entrada do alvo e o egress dos pares):
  --direction out    (padrao) degrada o que o ALVO envia aos pares
  --direction in     degrada o que os PARES enviam ao alvo (tc nos pares)
  --direction both   os dois sentidos (perda simetrica com --loss; assimetrica
                     se --loss-in for diferente de --loss)

Opcoes:
  --target <no>          Obrigatorio (exceto com --heal). No alvo.
  --nodes <lista>        Nos do cluster. Padrao: ${NODES_PADRAO}
  --peers <lista>        Pares afetados. Padrao: todos os nos de --nodes menos o alvo.
  --direction <d>        out (padrao), in ou both.
  --loss <pct>           Perda 0..100 (aceita fracao, ex.: 0.5). Sentido de saida
                         (e o de entrada, se --loss-in nao for dado).
  --loss-in <pct>        Perda no sentido de ENTRADA (assimetrica). Exige --direction in|both.
  --loss-corr <pct>      Correlacao da perda (modelo random).
  --loss-model <m>       random (padrao) ou gemodel (Gilbert-Elliott: perda em
                         rajada; media = --loss, rajada media = --burst pacotes).
  --burst <n>            Comprimento medio da rajada do gemodel (padrao 4, >= 1).
  --delay <Nms>          Atraso (sufixo ms/us/s; sem sufixo = ms).
  --jitter <Nms>         Jitter (distribuicao normal); exige --delay.
  --duplicate <pct>      Duplicacao 0..100.
  --reorder <pct[,corr]> Reordenacao 0..100 (exige --delay).
  --corrupt <pct>        Corrupcao 0..100.
  --severity <N1..N5>    Atalho de perda: N1=1%, N2=5%, N3=20%, N4=50%, N5=99.9%
                         (nao combina com --loss).
  --iface <if>           Interface dentro do container. Padrao: ${IFACE_PADRAO}
  --duration <seg>       Janela da degradacao. Padrao: ${DURACAO_PADRAO}. Menos de ${DURACAO_AVISO}: aviso.
  --ping-count <n>       Pings por par para medir a perda no fio (padrao ${PING_PADRAO}; 0 desliga).
  --marker <arquivo>     Acrescenta marcadores 'fault=F4 node=<alvo> event=start|end
                         t_epoch_ns=<n>' (formato de B-25/B-29).
  --heal                 So remove a qdisc 'prio 1:' do injetor em TODOS os nos de
                         --nodes (ex.: apos SIGKILL) e sai. Nao toca raiz alheia.
  --dry-run              Imprime o plano e sai 0, sem podman/sudo/nsenter/tc.
  --help                 Mostra esta mensagem.

Variaveis de ambiente:
  PODMAN    Binario do podman. Padrao 'podman'. Cluster ROOTFUL:
              PODMAN="sudo podman" ${NOME} --target isn3 --severity N3
  LOCK_DIR  Diretorio dos arquivos de lock (flock). Padrao /tmp.

Codigos de saida:
  0  sucesso
  1  erro generico (lock ocupado, residuo apos a limpeza)
  2  argumento invalido
  3  dependencia ausente (podman, nsenter, sudo, flock, ping)
  4  no ausente, parado ou sem IP
  5  kernel rejeitou prio/netem/u32
  6  a degradacao nao se efetivou (perda no fio incompativel, contador zerado)
  7  raiz ocupada (F3 ou outra qdisc raiz; clsact/lock de F2) num no a tocar

Exemplos:
  ${NOME} --target isn3 --severity N3 --direction both --marker runs/marcadores.txt
  ${NOME} --target isn3 --direction both --loss 20 --loss-in 50
  ${NOME} --target isn3 --delay 100ms --jitter 50ms --loss 5 --duplicate 1
  ${NOME} --target isn3 --loss 20 --loss-model gemodel --burst 4
  ${NOME} --heal
  ${NOME} --target isn3 --severity N2 --dry-run
EOF
}

TARGET=""
NODES="${NODES_PADRAO}"
PEERS_ARG=""
PEERS_SET=0
DIRECAO="out"
IFACE="${IFACE_PADRAO}"
DURACAO="${DURACAO_PADRAO}"
LOSS=""
LOSS_IN=""
LOSS_CORR=""
LOSS_MODEL="random"
BURST="4"
DELAY=""
JITTER=""
DUPLICATE=""
REORDER=""
CORRUPT=""
SEVERITY=""
PING_COUNT="${PING_PADRAO}"
MARKER=""
HEAL=0
DRY_RUN=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target|--nodes|--peers|--direction|--iface|--duration|--loss|--loss-in|\
        --loss-corr|--loss-model|--burst|--delay|--jitter|--duplicate|--reorder|\
        --corrupt|--severity|--ping-count|--marker)
            [[ $# -ge 2 ]] || { echo "Erro: $1 exige valor" >&2; exit 2; }
            case "$1" in
                --target)     TARGET="$2" ;;
                --nodes)      NODES="$2" ;;
                --peers)      PEERS_ARG="$2"; PEERS_SET=1 ;;
                --direction)  DIRECAO="$2" ;;
                --iface)      IFACE="$2" ;;
                --duration)   DURACAO="$2" ;;
                --loss)       LOSS="$2" ;;
                --loss-in)    LOSS_IN="$2" ;;
                --loss-corr)  LOSS_CORR="$2" ;;
                --loss-model) LOSS_MODEL="$2" ;;
                --burst)      BURST="$2" ;;
                --delay)      DELAY="$2" ;;
                --jitter)     JITTER="$2" ;;
                --duplicate)  DUPLICATE="$2" ;;
                --reorder)    REORDER="$2" ;;
                --corrupt)    CORRUPT="$2" ;;
                --severity)   SEVERITY="$2" ;;
                --ping-count) PING_COUNT="$2" ;;
                --marker)     MARKER="$2" ;;
            esac
            shift 2 ;;
        --heal)    HEAL=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        --help|-h) usage; exit 0 ;;
        *)
            echo "Erro: argumento desconhecido '$1'" >&2
            usage >&2
            exit 2 ;;
    esac
done

# ------------------------------------------------------------- validacao
NOME_RE='^[A-Za-z0-9][A-Za-z0-9_.-]*$'
IFACE_RE='^[A-Za-z0-9][A-Za-z0-9_.-]*$'
IPV4_RE='^[0-9]{1,3}(\.[0-9]{1,3}){3}$'
NUM_RE='^[0-9]+(\.[0-9]+)?$'
DELAY_RE='^[0-9]+(\.[0-9]+)?(ms|us|s)?$'

erro_arg() { echo "Erro: $*" >&2; exit 2; }

# pct_ok <valor>: numero decimal nao negativo em 0..100.
pct_ok() {
    [[ "$1" =~ ${NUM_RE} ]] && awk -v a="$1" 'BEGIN { exit !(a + 0 <= 100) }'
}
# positivo <valor>: numero > 0.
positivo() { awk -v a="$1" 'BEGIN { exit !(a + 0 > 0) }'; }
# fmt <numero>: 4 casas, sem zeros a direita (6.2500 -> 6.25; 25.0000 -> 25).
fmt() {
    awk -v a="$1" 'BEGIN { printf "%.4f", a }' | sed -E 's/(\.[0-9]*[1-9])0+$/\1/; s/\.0+$//'
}

[[ "${IFACE}" =~ ${IFACE_RE} ]] || erro_arg "--iface invalida '${IFACE}'"
if ! [[ "${DURACAO}" =~ ^[0-9]+$ ]] || [[ "${DURACAO}" -lt 1 ]]; then
    erro_arg "--duration deve ser inteiro positivo, recebido '${DURACAO}'"
fi
[[ "${PING_COUNT}" =~ ^[0-9]+$ ]] || erro_arg "--ping-count deve ser inteiro >= 0, recebido '${PING_COUNT}'"

IFS=',' read -r -a TODOS <<<"${NODES}"
if [[ "${#TODOS[@]}" -lt 2 ]]; then
    erro_arg "--nodes precisa listar ao menos 2 nos, recebido '${NODES}'"
fi
for n in "${TODOS[@]}"; do
    [[ "${n}" =~ ${NOME_RE} ]] || erro_arg "nome de no invalido em --nodes: '${n}'"
done

no_em_nodes() { # no_em_nodes <no>: 0 se estiver em TODOS
    local x
    for x in "${TODOS[@]}"; do [[ "${x}" == "$1" ]] && return 0; done
    return 1
}

if [[ -n "${TARGET}" ]]; then
    [[ "${TARGET}" =~ ${NOME_RE} ]] || erro_arg "nome de no invalido em --target: '${TARGET}'"
    no_em_nodes "${TARGET}" || erro_arg "no '${TARGET}' de --target nao esta em --nodes (${NODES})"
elif [[ "${HEAL}" -eq 0 ]]; then
    echo "Erro: --target e obrigatorio (exceto com --heal)" >&2
    usage >&2
    exit 2
fi

# --heal: so precisa de --nodes/--iface; o resto dos parametros nao se aplica.
if [[ "${HEAL}" -eq 1 ]]; then
    if [[ "${DRY_RUN}" -eq 1 ]]; then
        log() { echo "[${TIMESTAMP}] ${NOME}: $*"; }
        log "Plano (--heal): IFACE=${IFACE} NOS=${TODOS[*]} DRY_RUN=1"
        for m in "${TODOS[@]}"; do
            log "  pid=\$(${PODMAN} inspect -f '{{.State.Pid}}' ${m})"
            log "  sudo nsenter -t <pid> -n tc qdisc show dev ${IFACE}   # no ${m}"
            log "  sudo nsenter -t <pid> -n tc qdisc del dev ${IFACE} root   # no ${m}; so se for a qdisc 'prio 1:' do injetor (raiz alheia, p.ex. F3, nao e tocada)"
        done
        log "dry-run: nada executado (sem podman/nsenter/sudo/tc)"
        exit 0
    fi
else
    case "${DIRECAO}" in out|in|both) ;; *) erro_arg "--direction invalida '${DIRECAO}' (use out, in ou both)" ;; esac
    case "${LOSS_MODEL}" in random|gemodel) ;; *) erro_arg "--loss-model invalido '${LOSS_MODEL}' (use random ou gemodel)" ;; esac

    # severidade -> perda
    if [[ -n "${SEVERITY}" ]]; then
        [[ -z "${LOSS}" ]] || erro_arg "--severity e --loss sao mutuamente exclusivos"
        case "${SEVERITY}" in
            N1) LOSS="1" ;;
            N2) LOSS="5" ;;
            N3) LOSS="20" ;;
            N4) LOSS="50" ;;
            N5) LOSS="99.9" ;;
            *)  erro_arg "--severity invalida '${SEVERITY}' (use N1..N5)" ;;
        esac
    fi

    [[ -z "${LOSS}" ]]      || pct_ok "${LOSS}"      || erro_arg "--loss deve estar em 0..100, recebido '${LOSS}'"
    [[ -z "${LOSS_IN}" ]]   || pct_ok "${LOSS_IN}"   || erro_arg "--loss-in deve estar em 0..100, recebido '${LOSS_IN}'"
    [[ -z "${LOSS_CORR}" ]] || pct_ok "${LOSS_CORR}" || erro_arg "--loss-corr deve estar em 0..100, recebido '${LOSS_CORR}'"
    [[ -z "${DUPLICATE}" ]] || pct_ok "${DUPLICATE}" || erro_arg "--duplicate deve estar em 0..100, recebido '${DUPLICATE}'"
    [[ -z "${CORRUPT}" ]]   || pct_ok "${CORRUPT}"   || erro_arg "--corrupt deve estar em 0..100, recebido '${CORRUPT}'"
    if [[ -n "${LOSS_IN}" && "${DIRECAO}" == "out" ]]; then
        erro_arg "--loss-in exige --direction in ou both"
    fi
    if [[ -n "${LOSS_CORR}" ]]; then
        [[ -n "${LOSS}${LOSS_IN}" ]] || erro_arg "--loss-corr exige --loss (ou --loss-in)"
        [[ "${LOSS_MODEL}" == "random" ]] || erro_arg "--loss-corr so existe no modelo random (no gemodel a correlacao vem de --burst)"
    fi
    [[ "${BURST}" =~ ${NUM_RE} ]] && awk -v a="${BURST}" 'BEGIN { exit !(a + 0 >= 1) }' \
        || erro_arg "--burst deve ser numero >= 1, recebido '${BURST}'"

    if [[ -n "${DELAY}" ]]; then
        [[ "${DELAY}" =~ ${DELAY_RE} ]] || erro_arg "--delay invalido '${DELAY}' (ex.: 100ms)"
        [[ "${DELAY}" =~ (ms|us|s)$ ]] || DELAY="${DELAY}ms"
    fi
    if [[ -n "${JITTER}" ]]; then
        [[ -n "${DELAY}" ]] || erro_arg "--jitter exige --delay"
        [[ "${JITTER}" =~ ${DELAY_RE} ]] || erro_arg "--jitter invalido '${JITTER}' (ex.: 50ms)"
        [[ "${JITTER}" =~ (ms|us|s)$ ]] || JITTER="${JITTER}ms"
    fi
    REORD_P=""; REORD_C=""
    if [[ -n "${REORDER}" ]]; then
        [[ -n "${DELAY}" ]] || erro_arg "--reorder exige --delay (o netem so reordena pacotes atrasados)"
        REORD_P="${REORDER%%,*}"
        [[ "${REORDER}" == *,* ]] && REORD_C="${REORDER#*,}"
        pct_ok "${REORD_P}" || erro_arg "--reorder: percentual deve estar em 0..100, recebido '${REORD_P}'"
        [[ -z "${REORD_C}" ]] || pct_ok "${REORD_C}" || erro_arg "--reorder: correlacao deve estar em 0..100, recebido '${REORD_C}'"
    fi

    # Perda efetiva por sentido (entrada herda --loss se --loss-in ausente).
    LOSS_OUT="${LOSS:-0}"
    LOSS_INN="${LOSS_IN:-${LOSS:-0}}"

    # Peers
    if [[ "${PEERS_SET}" -eq 1 ]]; then
        [[ -n "${PEERS_ARG}" ]] || erro_arg "--peers vazio"
        IFS=',' read -r -a PARES <<<"${PEERS_ARG}"
        for p in "${PARES[@]}"; do
            [[ "${p}" =~ ${NOME_RE} ]] || erro_arg "nome de no invalido em --peers: '${p}'"
            no_em_nodes "${p}" || erro_arg "par '${p}' de --peers nao esta em --nodes (${NODES})"
            [[ "${p}" != "${TARGET}" ]] || erro_arg "--peers nao pode conter o proprio alvo '${TARGET}'"
        done
    else
        PARES=()
        for n in "${TODOS[@]}"; do [[ "${n}" == "${TARGET}" ]] || PARES+=("${n}"); done
    fi
    [[ "${#PARES[@]}" -ge 1 ]] || erro_arg "nenhum par a degradar"
fi

# netem_args <perda>: parametros do netem para uma perda (sentido) dada.
netem_args() {
    local l="$1" r p a=()
    if positivo "${l}"; then
        if [[ "${LOSS_MODEL}" == "gemodel" ]]; then
            r="$(awk -v b="${BURST}" 'BEGIN { print 100 / b }')"
            p="$(awk -v r="${r}" -v x="${l}" 'BEGIN { print r * x / (100 - x) }')"
            a+=(loss gemodel "$(fmt "${p}")%" "$(fmt "${r}")%" 100% 0%)
        else
            a+=(loss "${l}%")
            [[ -z "${LOSS_CORR}" ]] || a+=("${LOSS_CORR}%")
        fi
    fi
    if [[ -n "${DELAY}" ]]; then
        a+=(delay "${DELAY}")
        [[ -z "${JITTER}" ]] || a+=("${JITTER}" distribution normal)
    fi
    [[ -z "${DUPLICATE}" ]] || a+=(duplicate "${DUPLICATE}%")
    if [[ -n "${REORDER}" ]]; then
        a+=(reorder "${REORD_P}%")
        [[ -z "${REORD_C}" ]] || a+=("${REORD_C}%")
    fi
    [[ -z "${CORRUPT}" ]] || a+=(corrupt "${CORRUPT}%")
    echo "${a[*]:-}"
}
tem_efeito() { # tem_efeito <perda>: ha algum efeito netem para esse sentido?
    positivo "$1" || [[ -n "${DELAY}${DUPLICATE}${REORDER}${CORRUPT}" ]]
}

log() { echo "[${TIMESTAMP}] ${NOME}: $*"; }

# ----------------------------------------------- entradas (no x destinos)
# Cada entrada = um no (netns) que recebe um netem e filtros por IP de destino:
#   saida  : no = alvo, destinos = pares, perda = LOSS_OUT
#   entrada: no = cada par, destino = alvo, perda = LOSS_INN
E_NODE=(); E_DST=(); E_LOSS=(); E_ROLE=(); E_ARGS=()
if [[ "${HEAL}" -eq 0 ]]; then
    if [[ "${DIRECAO}" != "in" ]] && tem_efeito "${LOSS_OUT}"; then
        E_NODE+=("${TARGET}"); E_DST+=("${PARES[*]}"); E_LOSS+=("${LOSS_OUT}")
        E_ROLE+=("saida"); E_ARGS+=("$(netem_args "${LOSS_OUT}")")
    fi
    if [[ "${DIRECAO}" != "out" ]] && tem_efeito "${LOSS_INN}"; then
        for p in "${PARES[@]}"; do
            E_NODE+=("${p}"); E_DST+=("${TARGET}"); E_LOSS+=("${LOSS_INN}")
            E_ROLE+=("entrada"); E_ARGS+=("$(netem_args "${LOSS_INN}")")
        done
    fi
    [[ "${#E_NODE[@]}" -ge 1 ]] || erro_arg "nenhum efeito a aplicar nesta direcao (informe --loss, --delay, --duplicate, --reorder ou --corrupt)"

    # gemodel: p = r*X/(100-X) deve caber em 0..100 para cada perda usada.
    if [[ "${LOSS_MODEL}" == "gemodel" ]]; then
        usou_perda=0
        for i in "${!E_NODE[@]}"; do
            x="${E_LOSS[$i]}"
            positivo "${x}" || continue
            usou_perda=1
            if ! awk -v b="${BURST}" -v x="${x}" 'BEGIN { exit !(x < 100 && (100 / b) * x / (100 - x) <= 100) }'; then
                erro_arg "gemodel com perda media ${x}% e rajada ${BURST}: a probabilidade de entrar no estado ruim passaria de 100% (reduza --loss ou aumente --burst)"
            fi
        done
        [[ "${usou_perda}" -eq 1 ]] || erro_arg "--loss-model gemodel exige perda > 0"
    fi
fi

# Nos efetivamente tocados (sem repeticao) e nos envolvidos (alvo + pares).
TOCADOS=()
for i in "${!E_NODE[@]}"; do TOCADOS+=("${E_NODE[$i]}"); done
ENVOLVIDOS=()
if [[ "${HEAL}" -eq 0 ]]; then ENVOLVIDOS=("${TARGET}" "${PARES[@]}"); fi

# perda esperada no fio de um ping do alvo a um par: ida (saida do alvo) e
# volta (entrada no alvo) -> 1-(1-po)(1-pi). So fracao 0..1.
PO="0"; PI="0"
if [[ "${HEAL}" -eq 0 ]]; then
    [[ "${DIRECAO}" == "in" ]]  || PO="${LOSS_OUT}"
    [[ "${DIRECAO}" == "out" ]] || PI="${LOSS_INN}"
fi
ESPERADA="$(awk -v a="${PO}" -v b="${PI}" 'BEGIN { printf "%.6f", 1 - (1 - a / 100) * (1 - b / 100) }')"

PING_APLICA=0
MOTIVO_PING=""
if [[ "${HEAL}" -eq 0 ]]; then
    if [[ "${PING_COUNT}" -eq 0 ]]; then
        MOTIVO_PING="desligada (--ping-count 0)"
    elif [[ "${LOSS_MODEL}" != "random" ]]; then
        MOTIVO_PING="nao se aplica ao modelo gemodel (perda em rajada, variancia maior que a binomial)"
    elif [[ -n "${CORRUPT}" ]]; then
        MOTIVO_PING="nao se aplica com --corrupt (pacote corrompido tambem e descartado no destino)"
    elif ! positivo "${ESPERADA}"; then
        MOTIVO_PING="nao se aplica sem perda no caminho do ping (so atraso, duplicacao ou reordenacao)"
    else
        PING_APLICA=1
    fi
fi

# ------------------------------------------------------------------ avisos
if [[ "${HEAL}" -eq 0 ]]; then
    if [[ "${DURACAO}" -lt "${DURACAO_AVISO}" ]]; then
        echo "Aviso: --duration ${DURACAO}s < ${DURACAO_AVISO}s e menor que a deteccao do FD_ALL3 (~40 s):" >&2
        echo "       o efeito sobre a vista do cluster pode nem aparecer (atraso severo, nao degradacao sustentada)." >&2
    fi
    for i in "${!E_NODE[@]}"; do
        if awk -v a="${E_LOSS[$i]}" 'BEGIN { exit !(a + 0 >= 99) }'; then
            echo "Aviso: perda >= 99% (${E_LOSS[$i]}%) equivale a particao; use inject-partition.sh para a comparacao controlada." >&2
            break
        fi
    done
fi

# ------------------------------------------------------------- dry-run
FMT_PID="'{{.State.Pid}}'"
FMT_IP="'{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}'"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    log "Plano: TARGET=${TARGET} PARES=${PARES[*]} DIRECAO=${DIRECAO} NODES=${NODES} IFACE=${IFACE} DURACAO=${DURACAO}s MARKER=${MARKER:-<nenhum>} MODELO=${LOSS_MODEL}${SEVERITY:+ SEVERIDADE=${SEVERITY}} DRY_RUN=1"
    [[ -z "${SEVERITY}" ]] || log "  severidade ${SEVERITY} -> perda ${LOSS}%"
    log "Comandos previstos (prio + netem + u32 por IP de destino, via nsenter a partir do host):"
    for n in "${ENVOLVIDOS[@]}"; do
        log "  ip(${n})=\$(${PODMAN} inspect -f ${FMT_IP} ${n})"
    done
    for n in "${TOCADOS[@]}"; do
        log "  pid=\$(${PODMAN} inspect -f ${FMT_PID} ${n})   # no ${n}"
        log "  flock ${LOCK_DIR}/inject-netdegrade-${n}.lock e ${LOCK_DIR}/inject-partition-${n}.lock (exclusao com F2; exit 7 se F2 ativa)"
    done
    for n in "${TOCADOS[@]}"; do
        log "  sudo nsenter -t <pid> -n tc qdisc show dev ${IFACE}   # no ${n}: raiz alheia (F3) ou clsact (F2) -> exit 7"
        log "  sudo nsenter -t <pid> -n tc qdisc del dev ${IFACE} root   # pre-limpeza, no ${n}: so se for a prio 1: do injetor (idempotente)"
        log "  sondagem em ${n}: prio + netem com os parametros reais + filtro u32 em ${PROBE_IP}/32 (TEST-NET-1) + remocao da raiz (exit 5 se rejeitado)"
    done
    for i in "${!E_NODE[@]}"; do
        n="${E_NODE[$i]}"
        log "  sudo nsenter -t <pid> -n tc qdisc add dev ${IFACE} root handle 1: prio ${PRIO_ARGS}   # no ${n}"
        log "  sudo nsenter -t <pid> -n tc qdisc add dev ${IFACE} parent 1:3 handle 30: netem ${E_ARGS[$i]}   # no ${n} (${E_ROLE[$i]})"
        for d in ${E_DST[$i]}; do
            if [[ "${E_ROLE[$i]}" == "saida" ]]; then
                log "  sudo nsenter -t <pid> -n tc filter add dev ${IFACE} parent 1: protocol ip prio 1 u32 match ip dst <ip-par>/32 flowid 1:3   # no ${n} par ${d}"
            else
                log "  sudo nsenter -t <pid> -n tc filter add dev ${IFACE} parent 1: protocol ip prio 1 u32 match ip dst <ip-alvo>/32 flowid 1:3   # no ${n} alvo ${d}"
            fi
        done
    done
    [[ -z "${MARKER}" ]] || log "  marcador start -> ${MARKER}   (fault=F4 node=${TARGET} event=start t_epoch_ns=<n>)"
    if [[ "${PING_APLICA}" -eq 1 ]]; then
        for p in "${PARES[@]}"; do
            log "  sudo nsenter -t <pid> -n ping -n -q -c ${PING_COUNT} -i 0.2 -W 5 <ip-par>   # perda esperada ${ESPERADA} (fracao), tolerancia binomial (max(3 sigma, 2/n)); par ${p}"
        done
    else
        log "  verificacao de taxa de perda por ping: ${MOTIVO_PING}"
    fi
    log "  sleep ${DURACAO}   (descontado o tempo gasto nos pings)"
    for n in "${TOCADOS[@]}"; do
        log "  sudo nsenter -t <pid> -n tc -s qdisc show dev ${IFACE}   # contadores Sent/dropped da banda 3, no ${n}"
    done
    for n in "${TOCADOS[@]}"; do
        log "  sudo nsenter -t <pid> -n tc qdisc del dev ${IFACE} root   # limpeza, no ${n}"
    done
    [[ -z "${MARKER}" ]] || log "  marcador end -> ${MARKER}   (event=end)"
    log "dry-run: nada executado (sem podman/nsenter/sudo/tc)"
    exit 0
fi

# ---------------------------------------------------------- dependencias
# ${PODMAN} sem aspas de proposito: aceita "sudo podman" (verifica ambos).
if ! command -v ${PODMAN} >/dev/null 2>&1; then
    echo "Erro: '${PODMAN}' nao encontrado no PATH" >&2; exit 3
fi
for dep in nsenter sudo; do
    if ! command -v "${dep}" >/dev/null 2>&1; then
        echo "Erro: '${dep}' nao encontrado no PATH do host (nsenter -n exige privilegio)" >&2
        exit 3
    fi
done
if [[ "${HEAL}" -eq 0 ]]; then
    if ! command -v flock >/dev/null 2>&1; then
        echo "Erro: 'flock' nao encontrado no PATH do host" >&2; exit 3
    fi
    if [[ "${PING_APLICA}" -eq 1 ]] && ! command -v ping >/dev/null 2>&1; then
        echo "Erro: 'ping' nao encontrado no PATH do host (use --ping-count 0 para dispensar)" >&2; exit 3
    fi
fi

# Resolve o PID de um no. Imprime o PID; retorna 1 se ausente/parado.
resolver_pid() {
    local no="$1" estado pid
    ${PODMAN} inspect "${no}" >/dev/null 2>&1 || return 1
    estado="$(${PODMAN} inspect -f '{{.State.Status}}' "${no}" 2>/dev/null || true)"
    [[ "${estado}" == "running" ]] || return 1
    pid="$(${PODMAN} inspect -f '{{.State.Pid}}' "${no}" 2>/dev/null || true)"
    [[ -n "${pid}" && "${pid}" != "0" ]] || return 1
    echo "${pid}"
}

# tc no netns do PID: nstc <pid> <args...>  (unica forma de chamar tc aqui)
nstc() {
    local pid="$1"; shift
    sudo nsenter -t "${pid}" -n tc "$@"
}

# eh_nosso <pid>: a raiz do netns e a prio 1: deste injetor?
eh_nosso() {
    local saida
    saida="$(nstc "$1" qdisc show dev "${IFACE}" 2>/dev/null || true)"
    grep -q '^qdisc prio 1: ' <<<"${saida}"
}

remove_se_nosso() { # remove_se_nosso <pid>
    if eh_nosso "$1"; then
        nstc "$1" qdisc del dev "${IFACE}" root >/dev/null 2>&1 || true
    fi
}

# ------------------------------------------------------------------ heal
if [[ "${HEAL}" -eq 1 ]]; then
    log "Heal: removendo a qdisc prio 1: do injetor em ${TODOS[*]} (iface ${IFACE}); raiz alheia nao e tocada"
    for m in "${TODOS[@]}"; do
        if pid="$(resolver_pid "${m}")"; then
            if eh_nosso "${pid}"; then
                nstc "${pid}" qdisc del dev "${IFACE}" root >/dev/null 2>&1 || true
                log "  ${m} (pid ${pid}): prio 1: removida"
            else
                log "  ${m} (pid ${pid}): sem qdisc do injetor (nada a fazer)"
            fi
        else
            log "  ${m}: container ausente/parado; netns some junto com a regra (nada a fazer)"
        fi
    done
    exit 0
fi

# ------------------------------------------------- resolucao (antes de tudo)
declare -A PID_DE IP_DE
for n in "${ENVOLVIDOS[@]}"; do
    if ! PID_DE["${n}"]="$(resolver_pid "${n}")"; then
        echo "Erro: container '${n}' nao encontrado, nao esta running ou sem PID" >&2
        exit 4
    fi
    ip="$(${PODMAN} inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "${n}" 2>/dev/null | awk '{print $1}' || true)"
    if ! [[ "${ip}" =~ ${IPV4_RE} ]]; then
        echo "Erro: nao foi possivel resolver o IPv4 de '${n}' (obtido '${ip}')" >&2
        exit 4
    fi
    IP_DE["${n}"]="${ip}"
    log "no ${n}: pid ${PID_DE[${n}]} ip ${ip}"
done

# ----------------------------------------------------------- cleanup/trap
CLEANED=0
SLEEP_PID=""
PING_PIDS=()
TMPD=""
START_ESCRITO=0
END_ESCRITO=0
BLOQUEADOS=()    # nos cujo lock temos: so neles o cleanup pode apagar a prio 1:

marcar() { # marcar <start|end>
    [[ -n "${MARKER}" ]] || return 0
    mkdir -p "$(dirname "${MARKER}")" 2>/dev/null || true
    echo "fault=F4 node=${TARGET} event=$1 t_epoch_ns=$(date +%s%N)" >>"${MARKER}" 2>/dev/null || true
}

# Remove a prio 1: do injetor dos nos sob lock. Idempotente (|| true). Nunca
# toca raiz alheia (F3): eh_nosso so e verdadeiro para 'qdisc prio 1:'.
remover_degradacao() {
    local n
    for n in "${BLOQUEADOS[@]:-}"; do
        [[ -n "${n}" ]] || continue
        remove_se_nosso "${PID_DE[${n}]}"
    done
}

cleanup() {
    [[ "${CLEANED}" -eq 0 ]] || return 0
    CLEANED=1
    if [[ -n "${SLEEP_PID}" ]]; then kill "${SLEEP_PID}" 2>/dev/null || true; fi
    local pp
    for pp in "${PING_PIDS[@]:-}"; do
        [[ -z "${pp}" ]] || kill "${pp}" 2>/dev/null || true
    done
    remover_degradacao
    [[ -z "${TMPD}" ]] || rm -rf "${TMPD}" 2>/dev/null || true
    if [[ "${START_ESCRITO}" -eq 1 && "${END_ESCRITO}" -eq 0 ]]; then
        END_ESCRITO=1
        marcar end
    fi
}
# O trap e registrado ANTES de qualquer alteracao. Sinais viram 'exit' para
# disparar o trap de EXIT com o codigo 128+sinal ('wait' e interrompido).
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# ------------------------------------------------------------------ lock
# Dois locks por no tocado: o proprio (duas injecoes F4 no mesmo no -> exit 1)
# e o de F2 (inject-partition-<no>.lock; F2 em andamento -> exit 7). Tomar o
# lock de F2 faz o inject-partition.sh recusar enquanto F4 estiver ativa.
mkdir -p "${LOCK_DIR}" 2>/dev/null || true
for n in "${TOCADOS[@]}"; do
    arq="${LOCK_DIR}/inject-netdegrade-${n}.lock"
    exec {fd}>"${arq}"
    if ! flock -n "${fd}"; then
        echo "Erro: ja ha uma injecao de degradacao em andamento em '${n}' (lock ${arq})." >&2
        echo "      Se for resto de execucao morta, rode: ${NOME} --heal" >&2
        exit 1
    fi
    arq2="${LOCK_DIR}/inject-partition-${n}.lock"
    exec {fd2}>"${arq2}"
    if ! flock -n "${fd2}"; then
        echo "Erro: F2 (inject-partition.sh) esta em andamento em '${n}' (lock ${arq2}); F2 e F4 nao se combinam no mesmo no." >&2
        exit 7
    fi
    BLOQUEADOS+=("${n}")
done

# ---------------------------------------------------------- pre-limpeza
log "pre-limpeza idempotente (qdisc prio 1: remanescente de execucao anterior)"
for n in "${TOCADOS[@]}"; do remove_se_nosso "${PID_DE[${n}]}"; done

# --------------------------------------------------- raiz ocupada (exit 7)
for n in "${TOCADOS[@]}"; do
    saida="$(nstc "${PID_DE[${n}]}" qdisc show dev "${IFACE}" 2>/dev/null || true)"
    if grep -q '^qdisc clsact ' <<<"${saida}"; then
        echo "Erro: ha clsact (F2, inject-partition.sh) em '${n}' (${IFACE}); F2 e F4 nao se combinam no mesmo no." >&2
        echo "      Rode 'inject-partition.sh --heal' se for resto de execucao morta. Nada foi alterado." >&2
        exit 7
    fi
    alheia="$(grep -E '^qdisc [a-z_]+ [0-9a-f]*: root( |$)' <<<"${saida}" | grep -vE "^qdisc (${RAIZ_BENIGNA}) " || true)"
    if [[ -n "${alheia}" ]]; then
        echo "Erro: a raiz de '${n}' (${IFACE}) esta ocupada por outra qdisc (provavel F3, inject-jitter.sh):" >&2
        echo "      ${alheia}" >&2
        echo "      F3 e F4 sao mutuamente exclusivos no mesmo no (use nos diferentes). Nada foi alterado." >&2
        exit 7
    fi
done

# ---------------------------------------------------------------- sonda
# Aplica e remove prio + netem (COM os parametros reais) + um filtro u32 em IP
# de TEST-NET-1. Se o kernel nao tem sch_prio/sch_netem/cls_u32 (ou rejeita um
# parametro, p.ex. gemodel) falha aqui com diagnostico (exit 5). O trap remove
# qualquer residuo da sonda.
for i in "${!E_NODE[@]}"; do
    n="${E_NODE[$i]}"; pid="${PID_DE[${n}]}"
    log "sondando prio/netem/u32 em ${n} (pid ${pid}, ${IFACE}) via nsenter"
    sonda_falhou() {
        echo "Erro: sonda prio/netem/u32 falhou em '${n}' (pid ${pid}, ${IFACE})." >&2
        echo "      Saida: $1" >&2
        echo "      Provavel sch_prio/sch_netem/cls_u32 ausente no kernel do HOST, ou parametro nao aceito (gate L-0)." >&2
        exit 5
    }
    # shellcheck disable=SC2086
    S_OUT="$(nstc "${pid}" qdisc add dev "${IFACE}" root handle 1: prio ${PRIO_ARGS} 2>&1)" || sonda_falhou "${S_OUT}"
    # shellcheck disable=SC2086
    S_OUT="$(nstc "${pid}" qdisc add dev "${IFACE}" parent 1:3 handle 30: netem ${E_ARGS[$i]} 2>&1)" || sonda_falhou "${S_OUT}"
    S_OUT="$(nstc "${pid}" filter add dev "${IFACE}" parent 1: protocol ip prio 1 \
        u32 match ip dst "${PROBE_IP}/32" flowid 1:3 2>&1)" || sonda_falhou "${S_OUT}"
    S_OUT="$(nstc "${pid}" qdisc del dev "${IFACE}" root 2>&1)" || sonda_falhou "${S_OUT}"
done

# -------------------------------------------------------------- aplicar
aplicar_falhou() {
    echo "Erro: kernel rejeitou a degradacao em '$1' (pid $2, ${IFACE})." >&2
    echo "      Saida: $3" >&2
    echo "      (o trap de cleanup removera qualquer regra parcial)" >&2
    exit 5
}
for i in "${!E_NODE[@]}"; do
    n="${E_NODE[$i]}"; pid="${PID_DE[${n}]}"
    log "sudo nsenter -t ${pid} -n tc qdisc add dev ${IFACE} root handle 1: prio ${PRIO_ARGS}   # ${n}"
    # shellcheck disable=SC2086
    A_OUT="$(nstc "${pid}" qdisc add dev "${IFACE}" root handle 1: prio ${PRIO_ARGS} 2>&1)" || aplicar_falhou "${n}" "${pid}" "${A_OUT}"
    log "${n} (${E_ROLE[$i]}): netem ${E_ARGS[$i]}"
    # shellcheck disable=SC2086
    A_OUT="$(nstc "${pid}" qdisc add dev "${IFACE}" parent 1:3 handle 30: netem ${E_ARGS[$i]} 2>&1)" || aplicar_falhou "${n}" "${pid}" "${A_OUT}"
    for d in ${E_DST[$i]}; do
        ip="${IP_DE[${d}]}"
        log "${n} -> ${d} (${ip}): filtro u32 dst -> banda 3 (netem)"
        A_OUT="$(nstc "${pid}" filter add dev "${IFACE}" parent 1: protocol ip prio 1 \
            u32 match ip dst "${ip}/32" flowid 1:3 2>&1)" || aplicar_falhou "${n}" "${pid}" "${A_OUT}"
    done
done
# A falha comeca aqui; o marcador de inicio nao espera a verificacao.
T_INICIO=$SECONDS
START_ESCRITO=1
marcar start
log "degradacao aplicada: alvo ${TARGET}, pares ${PARES[*]}, direcao ${DIRECAO}"

# ---------------------------------------------------- verificar (taxa)
# Perda no FIO: ping do netns do alvo a cada par (ida pela regra de saida do
# alvo, volta pela de entrada). Compara com a perda esperada dentro de 3
# desvios binomiais (piso 2/n). Mede o ICMP, nao o TCP: a taxa efetiva do TCP
# difere (retransmissoes tambem sofrem perda).
if [[ "${PING_APLICA}" -eq 1 ]]; then
    TMPD="$(mktemp -d)"
    pid_alvo="${PID_DE[${TARGET}]}"
    log "verificando a taxa de perda no fio: ${PING_COUNT} pings por par (esperada ${ESPERADA} em fracao)"
    for p in "${PARES[@]}"; do
        sudo nsenter -t "${pid_alvo}" -n ping -n -q -c "${PING_COUNT}" -i 0.2 -W 5 "${IP_DE[${p}]}" \
            >"${TMPD}/ping.${p}" 2>&1 &
        PING_PIDS+=("$!")
    done
    for pp in "${PING_PIDS[@]}"; do wait "${pp}" || true; done
    PING_PIDS=()
    for p in "${PARES[@]}"; do
        obs="$(sed -nE 's/.* ([0-9.]+)% packet loss.*/\1/p' "${TMPD}/ping.${p}" | head -n1)"
        if [[ -z "${obs}" ]]; then
            echo "Erro: a degradacao nao se efetivou ou nao foi mensuravel: sem resumo de ping para ${p}:" >&2
            sed 's/^/    /' "${TMPD}/ping.${p}" >&2
            exit 6
        fi
        log "perda observada ${TARGET} -> ${p}: ${obs}% (esperada $(awk -v e="${ESPERADA}" 'BEGIN { printf "%.2f", e * 100 }')%, n=${PING_COUNT})"
        if ! awk -v o="${obs}" -v e="${ESPERADA}" -v n="${PING_COUNT}" 'BEGIN {
                o = o / 100; s = sqrt(e * (1 - e) / n); t = 3 * s; if (t < 2 / n) t = 2 / n;
                d = o - e; if (d < 0) d = -d; exit !(d <= t) }'; then
            echo "Erro: a degradacao nao se efetivou como configurada: perda observada ${obs}% contra $(awk -v e="${ESPERADA}" 'BEGIN { printf "%.2f", e * 100 }')% esperada (fora de 3 sigma binomial, n=${PING_COUNT}) em ${TARGET} -> ${p}." >&2
            exit 6
        fi
    done
    rm -rf "${TMPD}"; TMPD=""
else
    log "verificacao de taxa de perda por ping: ${MOTIVO_PING}"
fi

# ------------------------------------------------------------- janela
RESTO=$((DURACAO - (SECONDS - T_INICIO)))
if [[ "${RESTO}" -gt 0 ]]; then
    log "aguardando ${RESTO}s com F4 ativa (janela de ${DURACAO}s)"
    sleep "${RESTO}" &
    SLEEP_PID=$!
    wait "${SLEEP_PID}" || true
    SLEEP_PID=""
fi

# ----------------------------------------------- contadores (antes de remover)
# Prova de que o trafego CASOU o filtro e de que o netem agiu. Sent+dropped zerado =
# o filtro u32 nao casou nada (exit 6). Perda configurada sem nenhum descarte
# so e erro quando se esperavam >= 10 descartes (P(0) < 5e-5); com amostra
# pequena e apenas aviso.
for i in "${!E_NODE[@]}"; do
    n="${E_NODE[$i]}"; pid="${PID_DE[${n}]}"
    SAIDA_S="$(nstc "${pid}" -s qdisc show dev "${IFACE}" 2>/dev/null || true)"
    log "contadores de ${n} (${E_ROLE[$i]}):"
    sed 's/^/    /' <<<"${SAIDA_S}"
    LINHA="$(awk '/^qdisc netem 30:/ { f = 1; next } f && /Sent/ { print $4, $7; exit }' <<<"${SAIDA_S}")"
    SENT="${LINHA%% *}"; DROP="${LINHA##* }"; DROP="${DROP%,}"
    if ! [[ "${SENT}" =~ ^[0-9]+$ && "${DROP}" =~ ^[0-9]+$ ]]; then
        echo "Erro: a degradacao nao se efetivou: nao foi possivel ler os contadores do netem em ${n} (saida de 'tc -s qdisc' inesperada)." >&2
        exit 6
    fi
    # 'Sent' conta so o que PASSOU; com perda ~100% e normal ser 0. Nada casou o
    # filtro somente se Sent + dropped == 0.
    if [[ "$((SENT + DROP))" -eq 0 ]]; then
        echo "Erro: a degradacao nao se efetivou: contadores 'Sent' e 'dropped' zerados em ${n} apos ${DURACAO}s (o filtro u32 nao casou nenhum pacote)." >&2
        exit 6
    fi
    if positivo "${E_LOSS[$i]}" && [[ "${DROP}" -eq 0 ]]; then
        if awk -v s="${SENT}" -v l="${E_LOSS[$i]}" 'BEGIN { exit !(s * l / 100 >= 10) }'; then
            echo "Erro: a degradacao nao se efetivou: ${SENT} pacotes na banda 3 de ${n} com perda ${E_LOSS[$i]}% e nenhum descarte ('dropped' 0)." >&2
            exit 6
        fi
        echo "Aviso: 'dropped' 0 em ${n} com ${SENT} pacotes e perda ${E_LOSS[$i]}% (amostra pequena: < 10 descartes esperados)." >&2
    fi
done

# -------------------------------------------------------------- remover
for n in "${TOCADOS[@]}"; do
    log "sudo nsenter -t ${PID_DE[${n}]} -n tc qdisc del dev ${IFACE} root   # ${n}"
done
cleanup

# Verificacao final: nenhuma prio 1: residual (netns que sumiu conta como limpo).
RESIDUO=0
for n in "${TOCADOS[@]}"; do
    if eh_nosso "${PID_DE[${n}]}"; then
        echo "Erro: ainda ha qdisc prio 1: residual em ${n} (${IFACE}); rode '${NOME} --heal'." >&2
        RESIDUO=1
    fi
done
[[ "${RESIDUO}" -eq 0 ]] || exit 1
log "OK: F4 (degradacao parcial) injetada por ${DURACAO}s em ${TARGET} (direcao ${DIRECAO}) e removida; sem qdisc residual"
