# runs/gate-b29/g-lib.sh -- so definicoes (T-B). set -euo pipefail fica no arquivo que carrega.
cd "${TCC_DIR:-$HOME/tcc-gate}"
export PODMAN="sudo podman"
LOG=runs/gate-b29
INJ="bash scripts/inject-partition.sh"
LOCK_DIR="${LOCK_DIR:-/tmp}"
mkdir -p "$LOG"
# guarda de netns: recusa PID vazio/0, readlink vazio ou netns igual ao do PID 1
guarda() {
  local pid="$1" ns1 nsn
  [ -n "$pid" ] && [ "$pid" != 0 ] || return 1
  ns1=$(sudo readlink /proc/1/ns/net) || return 1
  nsn=$(sudo readlink "/proc/$pid/ns/net") || return 1
  [ -n "$ns1" ] && [ -n "$nsn" ] && [ "$nsn" != "$ns1" ]
}
pid_de() {
  local p; p=$($PODMAN inspect -f "{{.State.Pid}}" "$1") || exit 1
  guarda "$p" || { echo "ABORTAR: PID invalido ou netns do host para $1 (pid=$p)" >&2; exit 1; }
  echo "$p"
}
# dead-man switch por injecao: duracao + margem, via systemd-run (sobrevive a queda do SSH)
# uso: armar <duracao-s> [margem-s=90]; desarmar so ao fim limpo
armar()    { DMS="tcc-heal-$$-$RANDOM"
             sudo systemd-run --quiet --unit="$DMS" --on-active="$(( $1 + ${2:-90} ))s" \
               --setenv=PODMAN="sudo podman" --working-directory="$PWD" \
               bash scripts/inject-partition.sh --heal --minority isn3
             echo "dead-man armado: $DMS em $(( $1 + ${2:-90} ))s"; }
desarmar() { sudo systemctl stop "$DMS.timer" 2>/dev/null || true; echo "dead-man desarmado: $DMS"; DMS=; }
sem_clsact() { local p out; p=$(pid_de "$1")
  out=$(sudo nsenter -t "$p" -n tc qdisc show dev eth0)
  if grep -q clsact <<<"$out"; then echo "RESIDUO clsact em $1" >&2; return 1; fi
  echo "ok sem clsact em $1: $(echo $out)"; }
health() { local p; for p in 11222 11223 11224; do
  curl -s -m 5 --digest -u admin:infinispan "http://localhost:$p/rest/v2/cache-managers/default/health" \
   | tr -d " \n" | grep -o "\"health_status\":\"[A-Z]*\",\"number_of_nodes\":[0-9]*" | head -1; done; }

# ---- log de saude a cada INTERVALO s (G-4 REP=3): uma linha por porta, com timestamp, http, status e size.
# t_epoch_ns,porta,http,status,size   (status/size vazios se o no nao respondeu; http=000 se o curl falhou/estourou)
# O health() acima descarta tudo isto; aqui nada e descartado, inclusive HEALTHY_REBALANCING.
saude_amostra() { # saude_amostra <porta>
  local p="$1" ts raw http body st sz nl=$'\n'
  ts=$(date +%s%N)
  raw=$(curl -s -m "${SAUDE_TIMEOUT_S:-2}" --digest -u admin:infinispan -w "${nl}%{http_code}" \
        "http://localhost:$p/rest/v2/cache-managers/default/health" 2>/dev/null) || true
  http="${raw##*"$nl"}"; [ "$raw" != "$http" ] || http=
  body=$(printf '%s' "${raw%"$nl"*}" | tr -d " $nl")
  st=$(printf '%s' "$body" | grep -o '"health_status":"[A-Z_]*"' | head -1 | cut -d'"' -f4) || true
  sz=$(printf '%s' "$body" | grep -o '"number_of_nodes":[0-9]*' | head -1 | cut -d: -f2) || true
  echo "$ts,$p,${http:-000},${st:-},${sz:-}"
}
saude_log_loop() { # saude_log_loop <arquivo> [intervalo-s=2]  (corre em subshell em segundo plano)
  local f="$1" iv="${2:-2}" prox p
  [ -s "$f" ] || echo "t_epoch_ns,porta,http,status,size" > "$f"
  while :; do
    prox=$(( $(date +%s) + iv ))
    for p in 11222 11223 11224; do saude_amostra "$p" >> "$f" & done
    wait
    while [ "$(date +%s)" -lt "$prox" ]; do sleep 1; done
  done
}
iniciar_log_saude() { # iniciar_log_saude <arquivo> [intervalo-s=2]
  ( saude_log_loop "$1" "${2:-2}" ) & LOGSAUDE_PID=$!
  echo "log de saude: $1 a cada ${2:-2} s (pid $LOGSAUDE_PID)"; }
parar_log_saude() {
  [ -z "${LOGSAUDE_PID:-}" ] && return 0
  pkill -P "$LOGSAUDE_PID" 2>/dev/null || true
  kill "$LOGSAUDE_PID" 2>/dev/null || true; wait "$LOGSAUDE_PID" 2>/dev/null || true
  LOGSAUDE_PID=
}
