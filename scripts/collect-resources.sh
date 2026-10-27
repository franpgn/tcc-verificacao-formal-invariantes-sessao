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

# ------------------------------------------------------------------ ESQUELETO
# Commit 1 (refactor): so argumentos, --help, --dry-run e exit 2. A resolucao
# de cgroups, as leituras, o laco, os sinais e o meta entram no commit "feat".

if [[ ${DRY_RUN} -eq 1 ]]; then
    echo "dry-run: coletor ${VERSAO_COLETOR} (contrato ${CONTRATO})"
    echo "podman: ${PODMAN}"
    echo "raiz cgroup: ${RAIZ_CGROUP}"
    echo "raiz proc: ${RAIZ_PROC}"
    for c in "${CONTAINERS[@]}"; do
        echo "fonte cg:${c} cgroup=erro:nao-implementado"
    done
    for i in "${!PID_VALORES[@]}"; do
        echo "fonte pid:${PID_ROTULOS[i]} pid=${PID_VALORES[i]}"
    done
    echo "intervalo: ${INTERVALO_MS} ms; podman stats a cada ${PODMAN_STATS_S} s"
    echo "escreveria: ${CSV}"
    echo "escreveria: ${META}"
    echo "dry-run: nada lido nem escrito"
    exit 0
fi

# Leitores: preenchem R_CPU, R_MEM, R_STEAL e R_ESTADO.
ler_cgroup()    { R_CPU=""; R_MEM=""; R_STEAL=""; R_ESTADO="erro:nao-implementado"; }
ler_pid_bruto() { R_CPU=""; R_MEM=""; R_STEAL=""; R_ESTADO="erro:nao-implementado"; }
ler_pid()       { ler_pid_bruto "${RAIZ_PROC}" "$1" 0; }
ler_vm()        { R_CPU=""; R_MEM=""; R_STEAL=""; R_ESTADO="erro:nao-implementado"; }

echo "${NOME}: coleta erro:nao-implementado (esqueleto)" >&2
exit 1
