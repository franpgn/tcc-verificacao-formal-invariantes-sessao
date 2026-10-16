# runs/gate-b29/g56-lib.sh -- definicoes de G-5/G-6 (so funcoes; o arquivo que carrega faz set -euo pipefail).
# Carrega g-lib.sh (pid_de com guarda de netns, armar/desarmar do dead-man, sem_clsact, health).
source "$(dirname "${BASH_SOURCE[0]}")/g-lib.sh"
T() { local pid="$1"; shift; sudo nsenter -t "$pid" -n "$@"; }   # T <pid> cmd...

# go do Gestor: interativo (terminal) e registrado em go.log. G_TESTE=1 so nos testes com shims.
exigir_go() { # exigir_go <rotulo>
  [ -t 0 ] || [ "${G_TESTE:-}" = 1 ] || { echo "ABORTAR: sem terminal (o go do Gestor e interativo)" >&2; exit 1; }
  local r; read -rp "$1: go do Gestor registrado no relatorio? [digite GO] " r
  [ "$r" = GO ] || { echo "ABORTAR: sem go" >&2; exit 1; }
  echo "go $1 $(date -Is) vm=$(hostname -I 2>/dev/null | awk '{print $1}') sha=$(git rev-parse HEAD)" | tee -a "$LOG/go.log"
}

# size=3 HEALTHY nas tres portas, com limite de tempo (retorna 1 se estourar)
espera_saudavel() { # espera_saudavel [limite-s=150]
  local lim="${1:-150}" t0; t0=$(date +%s)
  until [ "$(health | grep -c 'HEALTHY","number_of_nodes":3')" = 3 ]; do
    [ $(( $(date +%s) - t0 )) -lt "$lim" ] || { echo "TIMEOUT: nao reformou size=3 HEALTHY em ${lim} s" >&2; return 1; }
    sleep 2
  done
  echo "size=3 HEALTHY apos $(( $(date +%s) - t0 )) s"
}

sem_netem() { local p out; p=$(pid_de "$1"); out=$(T "$p" tc qdisc show dev eth0)
  if printf "%s\n" "$out" | grep -q netem; then echo "RESIDUO netem em $1" >&2; return 1; fi
  echo "ok sem netem em $1: $(echo $out)"; }
sem_timers() { local n; n=$(systemctl list-timers --all 2>/dev/null | grep -c tcc-heal || true)
  [ "$n" = 0 ] || { echo "ha $n timer(s) tcc-heal armado(s)" >&2; return 1; }; echo "ok 0 timers tcc-heal"; }

# estado limpo antes de cada execucao: size=3 HEALTHY x3, sem clsact/netem nos 3 nos, 0 timers
preflight() { # preflight <arquivo-saude>
  health | tee "$1"
  [ "$(grep -c 'HEALTHY","number_of_nodes":3' "$1")" = 3 ] || { echo "ABORTAR: cluster fora de size=3 HEALTHY" >&2; return 1; }
  local n; for n in isn1 isn2 isn3; do sem_clsact "$n"; sem_netem "$n"; done
  sem_timers
}

# dead-man do netem (F3) por injecao: root via systemd-run, com a mesma guarda de netns (nunca no netns do host)
# uso: armar_netem <no> <duracao-s> [margem-s=90]; desarmar_netem so ao fim limpo
armar_netem() { DMN="tcc-heal-netem-$$-$RANDOM"
  sudo systemd-run --quiet --unit="$DMN" --on-active="$(( $2 + ${3:-90} ))s" \
    bash -c 'p=$(podman inspect -f "{{.State.Pid}}" "$1"); a=$(readlink /proc/1/ns/net); b=$(readlink /proc/$p/ns/net); [ -n "$p" ] && [ "$p" != 0 ] && [ -n "$b" ] && [ "$b" != "$a" ] && nsenter -t "$p" -n tc qdisc del dev eth0 root' _ "$1"
  echo "dead-man netem armado: $DMN ($1) em $(( $2 + ${3:-90} ))s"; }
desarmar_netem() { sudo systemctl stop "$DMN.timer" 2>/dev/null || true; echo "dead-man netem desarmado: $DMN"; DMN=; }

# espera um marcador (event=start|end) ate <limite-s>; retorna 1 se nao vier
espera_marcador() { # espera_marcador <arquivo> <start|end> <limite-s>
  local t0; t0=$(date +%s)
  until grep -q "event=$2" "$1" 2>/dev/null; do
    [ $(( $(date +%s) - t0 )) -lt "$3" ] || return 1; sleep 1
  done
}
# contadores por filtro (offset 12 = src/ingress, 16 = dst/egress): o kernel lista os dois filtros em ambos os "show"
cont_filtros_pid() { # cont_filtros_pid <pid>
  T "$1" tc -s filter show dev eth0 ingress | awk '/match .* at 12/{d="ingress(src)"} /match .* at 16/{d="egress(dst)"} /Sent .* pkt/{print d, $4"pkt"}'; }
