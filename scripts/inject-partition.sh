#!/usr/bin/env bash
#
# inject-partition.sh
#
# Injeta a falha F2 (particao de rede REAL) no cluster Infinispan: um
# subconjunto minoritario de nos e isolado dos demais NOS DOIS SENTIDOS, mas
# continua rodando e continua alcancavel pelo cliente Hot Rod (portas
# publicadas). Conforme Cap. 3 secao 3.3.2 (modelo de falhas, F2) e
# docs/plano-b29-particao-real-e-contraexemplos-2026-10-09.md (secoes 3.2-3.5,
# mecanismo (a2) aprovado).
#
# Mecanismo: disciplina 'clsact' do tc (ganchos de entrada e de saida, NAO
# ocupa a fila raiz usada pelo netem do inject-jitter.sh) com um filtro 'u32'
# por IP de par e acao 'drop':
#     tc qdisc  add dev <if> clsact
#     tc filter add dev <if> ingress protocol ip prio 1 u32 match ip src <par>/32 action drop
#     tc filter add dev <if> egress  protocol ip prio 1 u32 match ip dst <par>/32 action drop
# executado a partir do HOST, dentro do netns do container, via
# 'sudo nsenter -t <pid> -n tc ...' (NAO via 'podman exec': a imagem nao tem tc
# nem NET_ADMIN). So o no isolado e tocado; a simetria vem de filtrar entrada
# E saida nele. O trafego Hot Rod do cliente (origem no gateway da bridge, nao
# em IP de par) nao e afetado, entao a minoria responde "indisponivel" em vez
# de simplesmente sumir. IPs dos pares sao resolvidos por 'podman inspect',
# nunca fixos.
#
# Robustez (requisitos de aceite 3.5/3.8 do plano): trap registrado ANTES de
# qualquer alteracao; pre-limpeza idempotente no inicio (sobra de execucao
# morta); flock por no (duas injecoes simultaneas no mesmo no sao recusadas);
# sonda do kernel (clsact/cls_u32/act_gact) antes de aplicar; verificacao de
# efetividade (conexao TCP ao par:7800 deve falhar; contadores de descarte
# nos dois sentidos); verificacao final de ausencia de residuo; '--heal' para
# o caso de SIGKILL, que nenhum trap captura.
#
# Dependencias (execucao real): podman, nsenter (util-linux), tc (iproute2),
# sudo, flock e timeout (util-linux/coreutils) NO HOST; modulos sch_clsact,
# cls_u32 e act_gact. O '--dry-run' e o '--help' nao requerem nada e rodam em
# qualquer maquina. Recomenda-se sudo sem senha para a bateria.
#
# Exit codes:
#   0  sucesso (particao aplicada, verificada, aguardada e removida)
#   1  erro generico (inclui lock ocupado e residuo de clsact apos a limpeza)
#   2  argumento invalido ou --help
#   3  dependencia ausente (podman, nsenter, sudo, flock, timeout)
#   4  no (isolado ou par) nao encontrado, nao-running, sem PID ou sem IP, ou
#      netns igual ao do PID 1 / ilegivel (guarda de netns; --heal tambem)
#   5  kernel rejeitou clsact/u32/drop (modulo ausente)
#   6  a particao NAO se efetivou (conexao ainda possivel a um par, ou
#      contadores de descarte zerados ao fim da janela)
#
# Cobertura: F2 (particao de rede) -- B-29.
set -euo pipefail

NOME="$(basename "$0")"
# Binario do podman. Cluster ROOTFUL (VM Hyper-V): PODMAN="sudo podman".
PODMAN="${PODMAN:-podman}"
LOCK_DIR="${LOCK_DIR:-/tmp}"
NODES_PADRAO="isn1,isn2,isn3"
IFACE_PADRAO="eth0"
DURACAO_PADRAO=90
# Abaixo disto a janela e menor que a deteccao do FD_ALL3 (~40 s + 1 s de
# VERIFY_SUSPECT2): o experimento seria "atraso severo", nao particao.
DURACAO_AVISO=45
PORTA_JGROUPS=7800
PROBE_IP="192.0.2.1"   # TEST-NET-1 (RFC 5737): nunca roteado, so para a sonda
TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

usage() {
    cat <<EOF
Uso: ${NOME} --minority <no[,no...]> [--nodes isn1,isn2,isn3] [--iface eth0]
            [--duration <segundos>] [--marker <arquivo>] [--dry-run]
${NOME} --heal [--minority <no[,no...]>] [--nodes ...] [--iface eth0] [--dry-run]
${NOME} --help

Injeta a particao de rede F2: isola o(s) no(s) de --minority dos demais nos
de --nodes, nos dois sentidos, via 'sudo nsenter -t <pid> -n tc' com a
disciplina clsact + filtros u32 por IP de par + acao drop. O trafego Hot Rod
do cliente NAO e afetado. Tudo e removido ao fim da janela, em sinal (INT,
TERM, HUP) ou erro.

Opcoes:
  --minority <no[,no]>  Obrigatorio (exceto com --heal). No(s) isolado(s). O
                        conjunto isolado deve ser ESTRITAMENTE MENOR que a
                        metade de --nodes (3 nos -> so 1 no). Isolar a maioria
                        e erro de argumento (exit 2).
  --nodes <lista>       Nos do cluster, separados por virgula. Padrao: ${NODES_PADRAO}
  --iface <if>          Interface dentro do container. Padrao: ${IFACE_PADRAO}
  --duration <seg>      Janela da particao, em segundos. Padrao: ${DURACAO_PADRAO} (acima dos
                        ~40 s do FD_ALL3). Menos de ${DURACAO_AVISO}: aviso de que e "atraso
                        severo", nao particao.
  --marker <arquivo>    Acrescenta marcadores 'fault=F2 node=<no> event=start|end
                        t_epoch_ns=<n>' (formato esperado por B-25).
  --heal                So remove regras remanescentes de uma execucao anterior
                        (ex.: apos SIGKILL) e sai. Sem --minority, limpa todos
                        os nos de --nodes. Nao toma o lock.
  --dry-run             Imprime o plano e sai 0, sem podman/sudo/nsenter/tc.
  --help                Mostra esta mensagem.

Variaveis de ambiente:
  PODMAN    Binario do podman. Padrao 'podman'. Cluster ROOTFUL:
              PODMAN="sudo podman" ${NOME} --minority isn3
  LOCK_DIR  Diretorio dos arquivos de lock (flock). Padrao /tmp.

Codigos de saida: 0 ok; 1 generico/lock ocupado/residuo; 2 argumento;
3 dependencia ausente; 4 no ausente ou parado; 5 kernel rejeitou
clsact/u32/drop; 6 a particao nao se efetivou.

Exemplos:
  ${NOME} --minority isn3 --duration 90 --marker runs/marcadores.txt
  PODMAN="sudo podman" ${NOME} --minority isn3
  ${NOME} --heal --minority isn3
  ${NOME} --minority isn3 --dry-run
EOF
}

MINORITY=""
NODES="${NODES_PADRAO}"
IFACE="${IFACE_PADRAO}"
DURACAO="${DURACAO_PADRAO}"
MARKER=""
HEAL=0
DRY_RUN=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --minority)
            [[ $# -ge 2 ]] || { echo "Erro: --minority exige valor" >&2; exit 2; }
            MINORITY="$2"; shift 2 ;;
        --nodes)
            [[ $# -ge 2 ]] || { echo "Erro: --nodes exige valor" >&2; exit 2; }
            NODES="$2"; shift 2 ;;
        --iface)
            [[ $# -ge 2 ]] || { echo "Erro: --iface exige valor" >&2; exit 2; }
            IFACE="$2"; shift 2 ;;
        --duration)
            [[ $# -ge 2 ]] || { echo "Erro: --duration exige valor" >&2; exit 2; }
            DURACAO="$2"; shift 2 ;;
        --marker)
            [[ $# -ge 2 ]] || { echo "Erro: --marker exige valor" >&2; exit 2; }
            MARKER="$2"; shift 2 ;;
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

if ! [[ "${IFACE}" =~ ${IFACE_RE} ]]; then
    echo "Erro: --iface invalida '${IFACE}'" >&2; exit 2
fi
if ! [[ "${DURACAO}" =~ ^[0-9]+$ ]] || [[ "${DURACAO}" -lt 1 ]]; then
    echo "Erro: --duration deve ser inteiro positivo, recebido '${DURACAO}'" >&2
    exit 2
fi

IFS=',' read -r -a TODOS <<<"${NODES}"
if [[ "${#TODOS[@]}" -lt 2 ]]; then
    echo "Erro: --nodes precisa listar ao menos 2 nos, recebido '${NODES}'" >&2
    exit 2
fi
for n in "${TODOS[@]}"; do
    [[ "${n}" =~ ${NOME_RE} ]] || { echo "Erro: nome de no invalido em --nodes: '${n}'" >&2; exit 2; }
done

MIN=()
if [[ -n "${MINORITY}" ]]; then
    IFS=',' read -r -a MIN <<<"${MINORITY}"
    for m in "${MIN[@]}"; do
        [[ "${m}" =~ ${NOME_RE} ]] || { echo "Erro: nome de no invalido em --minority: '${m}'" >&2; exit 2; }
        achou=0
        for n in "${TODOS[@]}"; do [[ "${n}" == "${m}" ]] && achou=1; done
        [[ "${achou}" -eq 1 ]] || { echo "Erro: no '${m}' de --minority nao esta em --nodes (${NODES})" >&2; exit 2; }
    done
    # Estritamente menos da metade: 2*|minoria| < |nos|. Evita isolar a
    # maioria e o empate (sem maioria nenhum lado tem quorum).
    if [[ $((2 * ${#MIN[@]})) -ge "${#TODOS[@]}" ]]; then
        echo "Erro: --minority (${#MIN[@]} no(s)) deve ser estritamente menor que a metade de --nodes (${#TODOS[@]})." >&2
        echo "      Isolar a maioria ou metade nao e o cenario F2 (o lado com quorum seria o isolado)." >&2
        exit 2
    fi
elif [[ "${HEAL}" -eq 0 ]]; then
    echo "Erro: --minority e obrigatorio (exceto com --heal)" >&2
    usage >&2
    exit 2
else
    MIN=("${TODOS[@]}")   # --heal sem --minority: limpa todos os nos
fi

# pares_de <no>: nos de TODOS que nao estao em MIN (lado oposto do corte).
PARES=()
if [[ "${HEAL}" -eq 0 ]]; then
    for n in "${TODOS[@]}"; do
        eh_min=0
        for m in "${MIN[@]}"; do [[ "${n}" == "${m}" ]] && eh_min=1; done
        [[ "${eh_min}" -eq 1 ]] || PARES+=("${n}")
    done
fi

log() {
    echo "[${TIMESTAMP}] ${NOME}: $*"
}

if [[ "${HEAL}" -eq 0 && "${DURACAO}" -lt "${DURACAO_AVISO}" ]]; then
    echo "Aviso: --duration ${DURACAO}s < ${DURACAO_AVISO}s e menor que a deteccao do FD_ALL3 (~40 s):" >&2
    echo "       isto equivale a atraso severo, nao a particao; a vista do cluster pode nem mudar." >&2
fi

# ------------------------------------------------------------- dry-run
FMT_PID="'{{.State.Pid}}'"
FMT_IP="'{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}'"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    if [[ "${HEAL}" -eq 1 ]]; then
        log "Plano (--heal): IFACE=${IFACE} NOS=${MIN[*]} DRY_RUN=1"
        for m in "${MIN[@]}"; do
            log "  pid=\$(${PODMAN} inspect -f ${FMT_PID} ${m})"
            log "  sudo nsenter -t <pid> -n tc qdisc del dev ${IFACE} clsact   # no ${m}"
        done
        log "dry-run: nada executado (sem podman/nsenter/sudo/tc)"
        exit 0
    fi
    log "Plano: MINORITY=${MIN[*]} PARES=${PARES[*]} NODES=${NODES} IFACE=${IFACE} DURACAO=${DURACAO}s MARKER=${MARKER:-<nenhum>} DRY_RUN=1"
    log "Comandos previstos (clsact + u32 + drop nos dois sentidos, via nsenter a partir do host):"
    for p in "${PARES[@]}"; do
        log "  ip-par(${p})=\$(${PODMAN} inspect -f ${FMT_IP} ${p})"
    done
    for m in "${MIN[@]}"; do
        log "  pid=\$(${PODMAN} inspect -f ${FMT_PID} ${m})"
        log "  flock ${LOCK_DIR}/inject-partition-${m}.lock"
        log "  sudo nsenter -t <pid> -n tc qdisc del dev ${IFACE} clsact   # pre-limpeza (idempotente)"
        log "  sondagem: add clsact + filtro u32 drop em ${PROBE_IP}/32 (TEST-NET-1) + del clsact (exit 5 se rejeitado)"
        log "  sudo nsenter -t <pid> -n tc qdisc add dev ${IFACE} clsact   # no ${m}"
        for p in "${PARES[@]}"; do
            log "  sudo nsenter -t <pid> -n tc filter add dev ${IFACE} ingress protocol ip prio 1 u32 match ip src <ip-par>/32 action drop   # no ${m} par ${p}"
            log "  sudo nsenter -t <pid> -n tc filter add dev ${IFACE} egress protocol ip prio 1 u32 match ip dst <ip-par>/32 action drop   # no ${m} par ${p}"
        done
        log "  verificacao: sudo nsenter -t <pid> -n timeout 3 bash -c 'exec 3<>/dev/tcp/<ip-par>/${PORTA_JGROUPS}' deve FALHAR (exit 6 se conectar)"
        log "  sudo nsenter -t <pid> -n tc -s filter show dev ${IFACE} ingress|egress   # contadores de descarte"
    done
    [[ -z "${MARKER}" ]] || log "  marcador start -> ${MARKER}   (fault=F2 node=<no> event=start t_epoch_ns=<n>)"
    log "  sleep ${DURACAO}"
    for m in "${MIN[@]}"; do
        log "  sudo nsenter -t <pid> -n tc qdisc del dev ${IFACE} clsact   # limpeza, no ${m}"
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
    for dep in flock timeout; do
        if ! command -v "${dep}" >/dev/null 2>&1; then
            echo "Erro: '${dep}' nao encontrado no PATH do host" >&2; exit 3
        fi
    done
fi

# Resolve o PID de um no. Imprime o PID; retorna 1 se ausente/parado.
resolver_pid() {
    local no="$1" estado pid
    ${PODMAN} inspect "${no}" >/dev/null 2>&1 || return 1
    estado="$(${PODMAN} inspect -f '{{.State.Status}}' "${no}" 2>/dev/null || true)"
    [[ "${estado}" == "running" ]] || return 1
    pid="$(${PODMAN} inspect -f '{{.State.Pid}}' "${no}" 2>/dev/null || true)"
    [[ -n "${pid}" && "${pid}" != "0" ]] || return 1
    # Guarda de netns (A2 de V-B): um container em rede do host (--network=host)
    # tem o mesmo netns do PID 1; clsact+drop ali cortaria a NIC da VM (e o
    # SSH). Ilegivel tambem recusa. Retorno 2 = recusa por seguranca.
    local ns_alvo ns_host
    ns_alvo="$(sudo readlink "/proc/${pid}/ns/net" 2>/dev/null || true)"
    ns_host="$(sudo readlink /proc/1/ns/net 2>/dev/null || true)"
    if [[ -z "${ns_alvo}" || -z "${ns_host}" ]]; then
        echo "Erro: nao foi possivel ler o netns de '${no}' (pid ${pid}) ou do PID 1; recusando por seguranca" >&2
        return 2
    fi
    if [[ "${ns_alvo}" == "${ns_host}" ]]; then
        echo "Erro: '${no}' (pid ${pid}) compartilha o netns do host (${ns_host}); recusando para nao cortar a NIC da VM" >&2
        return 2
    fi
    echo "${pid}"
}

# tc no netns do PID: nstc <pid> <args...>
nstc() {
    local pid="$1"; shift
    sudo nsenter -t "${pid}" -n tc "$@"
}

# ------------------------------------------------------------------ heal
if [[ "${HEAL}" -eq 1 ]]; then
    log "Heal: removendo clsact em ${MIN[*]} (iface ${IFACE})"
    RECUSADOS=0
    for m in "${MIN[@]}"; do
        rc=0; pid="$(resolver_pid "${m}")" || rc=$?
        if [[ "${rc}" -eq 0 ]]; then
            nstc "${pid}" qdisc del dev "${IFACE}" clsact >/dev/null 2>&1 || true
            log "  ${m} (pid ${pid}): clsact removido (ou ja ausente)"
        elif [[ "${rc}" -eq 2 ]]; then
            log "  ${m}: netns do host ou ilegivel; NAO tocado"
            RECUSADOS=1
        else
            log "  ${m}: container ausente/parado; netns some junto com a regra (nada a fazer)"
        fi
    done
    [[ "${RECUSADOS}" -eq 0 ]] || exit 4
    exit 0
fi

# ------------------------------------------------- resolucao (antes de tudo)
declare -A PID_DE IP_DE
for m in "${MIN[@]}"; do
    rc=0; PID_DE["${m}"]="$(resolver_pid "${m}")" || rc=$?
    if [[ "${rc}" -ne 0 ]]; then
        [[ "${rc}" -eq 2 ]] || echo "Erro: container '${m}' nao encontrado, nao esta running ou sem PID" >&2
        exit 4
    fi
    log "PID do container ${m}: ${PID_DE[${m}]}"
done
for p in "${PARES[@]}"; do
    # Garante par running (um par parado nao particiona nada).
    rc=0; resolver_pid "${p}" >/dev/null || rc=$?
    if [[ "${rc}" -ne 0 ]]; then
        [[ "${rc}" -eq 2 ]] || echo "Erro: par '${p}' nao encontrado ou nao esta running" >&2
        exit 4
    fi
    ip="$(${PODMAN} inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "${p}" 2>/dev/null | awk '{print $1}' || true)"
    if ! [[ "${ip}" =~ ${IPV4_RE} ]]; then
        echo "Erro: nao foi possivel resolver o IPv4 do par '${p}' (obtido '${ip}')" >&2
        exit 4
    fi
    IP_DE["${p}"]="${ip}"
    log "IP do par ${p}: ${ip}"
done

# ----------------------------------------------------------- cleanup/trap
CLEANED=0
SLEEP_PID=""
START_ESCRITO=0
END_ESCRITO=0

marcar() { # marcar <start|end> <no>
    [[ -n "${MARKER}" ]] || return 0
    mkdir -p "$(dirname "${MARKER}")" 2>/dev/null || true
    echo "fault=F2 node=$2 event=$1 t_epoch_ns=$(date +%s%N)" >>"${MARKER}" 2>/dev/null || true
}

# Remove clsact de todos os nos isolados. Idempotente (|| true). Executa uma
# so vez (CLEANED), exceto quando chamado com 'forcar' (pre-limpeza).
remover_particao() {
    local m
    for m in "${MIN[@]}"; do
        nstc "${PID_DE[${m}]}" qdisc del dev "${IFACE}" clsact >/dev/null 2>&1 || true
    done
}

cleanup() {
    [[ "${CLEANED}" -eq 0 ]] || return 0
    CLEANED=1
    if [[ -n "${SLEEP_PID}" ]]; then kill "${SLEEP_PID}" 2>/dev/null || true; fi
    remover_particao
    if [[ "${START_ESCRITO}" -eq 1 && "${END_ESCRITO}" -eq 0 ]]; then
        END_ESCRITO=1
        for m in "${MIN[@]}"; do marcar end "${m}"; done
    fi
}
# O trap e registrado ANTES de qualquer alteracao. Sinais viram 'exit' para
# disparar o trap de EXIT com o codigo 128+sinal (o 'wait' abaixo e
# interrompido pelo sinal; um 'sleep' em primeiro plano nao seria).
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# ------------------------------------------------------------------ lock
declare -A LOCK_FD
for m in "${MIN[@]}"; do
    mkdir -p "${LOCK_DIR}" 2>/dev/null || true
    arq="${LOCK_DIR}/inject-partition-${m}.lock"
    exec {fd}>"${arq}"
    if ! flock -n "${fd}"; then
        echo "Erro: ja ha uma injecao de particao em andamento em '${m}' (lock ${arq})." >&2
        echo "      Se for resto de execucao morta, rode: ${NOME} --heal --minority ${m}" >&2
        exit 1
    fi
    LOCK_FD["${m}"]="${fd}"
done

# ---------------------------------------------------------- pre-limpeza
log "pre-limpeza idempotente (clsact remanescente de execucao anterior)"
remover_particao

# ---------------------------------------------------------------- sonda
# Aplica e remove clsact + um filtro u32/drop (ingress e egress) em IP de
# TEST-NET-1. Se o kernel nao tem sch_clsact/cls_u32/act_gact falha aqui com
# diagnostico (exit 5). O trap remove qualquer residuo da sonda.
for m in "${MIN[@]}"; do
    pid="${PID_DE[${m}]}"
    log "sondando clsact/u32/drop em ${m} (pid ${pid}, ${IFACE}) via nsenter"
    sonda_falhou() {
        echo "Erro: sonda clsact/u32/drop falhou em '${m}' (pid ${pid}, ${IFACE})." >&2
        echo "      Saida: $1" >&2
        echo "      Provavel sch_clsact/cls_u32/act_gact ausente no kernel do HOST (gate G-0)." >&2
        echo "      Plano B documentado: iptables/nftables via nsenter (docs/configuracao-infinispan.md)." >&2
        exit 5
    }
    S_OUT="$(nstc "${pid}" qdisc add dev "${IFACE}" clsact 2>&1)" || sonda_falhou "${S_OUT}"
    S_OUT="$(nstc "${pid}" filter add dev "${IFACE}" ingress protocol ip prio 1 \
        u32 match ip src "${PROBE_IP}/32" action drop 2>&1)" || sonda_falhou "${S_OUT}"
    S_OUT="$(nstc "${pid}" filter add dev "${IFACE}" egress protocol ip prio 1 \
        u32 match ip dst "${PROBE_IP}/32" action drop 2>&1)" || sonda_falhou "${S_OUT}"
    S_OUT="$(nstc "${pid}" qdisc del dev "${IFACE}" clsact 2>&1)" || sonda_falhou "${S_OUT}"
done

# -------------------------------------------------------------- aplicar
aplicar_falhou() {
    echo "Erro: kernel rejeitou a particao em '$1' (pid $2, ${IFACE})." >&2
    echo "      Saida: $3" >&2
    echo "      (o trap de cleanup removera qualquer regra parcial)" >&2
    exit 5
}
for m in "${MIN[@]}"; do
    pid="${PID_DE[${m}]}"
    log "sudo nsenter -t ${pid} -n tc qdisc add dev ${IFACE} clsact"
    A_OUT="$(nstc "${pid}" qdisc add dev "${IFACE}" clsact 2>&1)" || aplicar_falhou "${m}" "${pid}" "${A_OUT}"
    for p in "${PARES[@]}"; do
        ip="${IP_DE[${p}]}"
        log "${m} x ${p} (${ip}): filtro ingress src + egress dst, acao drop"
        A_OUT="$(nstc "${pid}" filter add dev "${IFACE}" ingress protocol ip prio 1 \
            u32 match ip src "${ip}/32" action drop 2>&1)" || aplicar_falhou "${m}" "${pid}" "${A_OUT}"
        A_OUT="$(nstc "${pid}" filter add dev "${IFACE}" egress protocol ip prio 1 \
            u32 match ip dst "${ip}/32" action drop 2>&1)" || aplicar_falhou "${m}" "${pid}" "${A_OUT}"
    done
done
# A falha comeca aqui; o marcador de inicio nao espera a verificacao.
START_ESCRITO=1
for m in "${MIN[@]}"; do marcar start "${m}"; done
log "particao aplicada em ${MIN[*]} contra ${PARES[*]}"

# ---------------------------------------------------- verificar (O-1)
# 1) A conexao TCP de dentro do netns do no isolado ao par:7800 deve FALHAR
#    (SYN descartado; o 'timeout' devolve 124). Conectar significa que o
#    corte nao se efetivou.
for m in "${MIN[@]}"; do
    pid="${PID_DE[${m}]}"
    for p in "${PARES[@]}"; do
        ip="${IP_DE[${p}]}"
        if sudo nsenter -t "${pid}" -n timeout 3 bash -c \
                "exec 3<>/dev/tcp/${ip}/${PORTA_JGROUPS}" >/dev/null 2>&1; then
            echo "Erro: a particao nao se efetivou: ${m} ainda conecta a ${p} (${ip}:${PORTA_JGROUPS})." >&2
            exit 6
        fi
    done
    log "OK: ${m} nao conecta a nenhum par em :${PORTA_JGROUPS}"
done

# soma_pkts <pid> <ingress|egress>: soma dos pacotes descartados nos filtros.
soma_pkts() {
    nstc "$1" -s filter show dev "${IFACE}" "$2" 2>/dev/null \
        | grep -oE 'Sent [0-9]+ bytes [0-9]+ pkt' | awk '{s += $4} END {print s + 0}'
}

log "aguardando ${DURACAO}s com F2 ativa"
sleep "${DURACAO}" &
SLEEP_PID=$!
wait "${SLEEP_PID}" || true
SLEEP_PID=""

# 2) Contadores de descarte ANTES de remover (prova de que "mordeu").
for m in "${MIN[@]}"; do
    pid="${PID_DE[${m}]}"
    ING="$(soma_pkts "${pid}" ingress)"
    EGR="$(soma_pkts "${pid}" egress)"
    log "contadores de descarte em ${m}: ingress=${ING} pkt, egress=${EGR} pkt"
    nstc "${pid}" -s filter show dev "${IFACE}" ingress 2>/dev/null | sed 's/^/    ingress: /' || true
    nstc "${pid}" -s filter show dev "${IFACE}" egress 2>/dev/null | sed 's/^/    egress:  /' || true
    if [[ "${ING}" -eq 0 && "${EGR}" -eq 0 ]]; then
        echo "Erro: a particao nao se efetivou: contadores de descarte zerados em ${m} apos ${DURACAO}s." >&2
        exit 6
    fi
    if [[ "${ING}" -eq 0 || "${EGR}" -eq 0 ]]; then
        echo "Aviso: descarte em um so sentido em ${m} (ingress=${ING}, egress=${EGR}); G-1 espera os dois > 0." >&2
    fi
done

# -------------------------------------------------------------- remover
for m in "${MIN[@]}"; do
    log "sudo nsenter -t ${PID_DE[${m}]} -n tc qdisc del dev ${IFACE} clsact"
done
cleanup

# Verificacao final: nenhum clsact residual (netns que sumiu conta como limpo).
RESIDUO=0
for m in "${MIN[@]}"; do
    if nstc "${PID_DE[${m}]}" qdisc show dev "${IFACE}" 2>/dev/null | grep -q clsact; then
        echo "Erro: ainda ha clsact residual em ${m} (${IFACE}); rode '${NOME} --heal --minority ${m}'." >&2
        RESIDUO=1
    fi
done
[[ "${RESIDUO}" -eq 0 ]] || exit 1
log "OK: F2 (particao) injetada por ${DURACAO}s em ${MIN[*]} e removida; sem clsact residual"
