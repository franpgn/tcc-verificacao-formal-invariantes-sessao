# runs/gate-netdegrade/l-lib.sh  (so definicoes; o set -euo pipefail fica no arquivo que a carrega)
cd "${TCC_DIR:-$HOME/tcc-desenvolvimento}"
export PODMAN="sudo podman"
LOG=runs/gate-netdegrade
INJ="bash scripts/inject-netdegrade.sh"
LOCK_DIR="${LOCK_DIR:-/tmp}"
mkdir -p "$LOG"

exigir_go() {   # parada real: so segue com o texto GO digitado num terminal
  [ -t 0 ] || { echo "ABORTAR: sem terminal; o go do Gestor deve ser dado interativamente" >&2; exit 1; }
  read -rp "$1 -- go do Gestor registrado no relatorio anterior? [digite GO] " r
  [ "$r" = GO ] || { echo "ABORTAR: sem go" >&2; exit 1; }
  echo "go $(date -Is) vm=$(hostname -I | awk '{print $1}') bloco=$1" | tee -a "$LOG/go.log"
}

# guarda de netns (R4): recusa PID vazio/0, readlink vazio ou netns igual ao do PID 1 (--network=host derrubaria o SSH)
guarda() {
  local pid="$1" ns1 nsn
  [ -n "$pid" ] && [ "$pid" != 0 ] || return 1
  ns1=$(sudo readlink /proc/1/ns/net) || return 1
  nsn=$(sudo readlink "/proc/$pid/ns/net") || return 1
  [ -n "$ns1" ] && [ -n "$nsn" ] && [ "$nsn" != "$ns1" ]
}
pid_de() {      # PID do no, ja validado pela guarda; aborta (exit 1) se invalido
  local p; p=$($PODMAN inspect -f '{{.State.Pid}}' "$1") || exit 1
  guarda "$p" || { echo "ABORTAR: PID invalido ou netns do host para $1 (pid='$p')" >&2; exit 1; }
  echo "$p"
}

# dead-man's switch POR INJECAO: duracao + 90 s, via systemd-run (sobrevive a queda do SSH).
# uso: armar <duracao-s> ; ... injecao ... ; desarmar   (desarmar so ao fim LIMPO da injecao)
armar()    { DMS="tcc-heal-$$-$RANDOM"
             sudo systemd-run --quiet --unit="$DMS" --on-active="$(( $1 + 90 ))s" \
               --setenv=PODMAN="sudo podman" --working-directory="$PWD" bash scripts/inject-netdegrade.sh --heal; }
desarmar() { sudo systemctl stop "$DMS.timer" 2>/dev/null || true; DMS=; }

# injecao em primeiro plano: arma, roda, grava log e rc (sem tee: o exit code nao se perde);
# desarma SO se rc == esperado; senao aborta o bloco deixando o switch armado.
# uso: rodar_inj <duracao-s> <rc-esperado> <arquivo-log> <args do injetor...>
rodar_inj() {
  local dur="$1" esp="$2" arq="$3" rc; shift 3
  armar "$dur"
  set +e; $INJ "$@" >"$arq" 2>&1; rc=$?; set -e
  cat "$arq"; echo "rc=$rc (esperado $esp)" | tee -a "$arq"
  [ "$rc" = "$esp" ] || { echo "ABORTAR: rc=$rc != $esp; dead-man's $DMS continua armado" >&2; exit 1; }
  desarmar
}
# contadores da banda 3 de um no: imprime "Sent dropped" (vazio se o no nao tem prio)
cont() { local p; p=$(pid_de "$1") || return 1
         sudo nsenter -t "$p" -n tc -s qdisc show dev eth0 \
           | awk '/^qdisc netem 30:/{f=1;next} f&&/Sent/{gsub(",","",$7); print $4, $7; exit}'; }
# falha (aborta) se ainda houver prio/netem no no
sem_prio() { local p out; p=$(pid_de "$1") || return 1
             out=$(sudo nsenter -t "$p" -n tc qdisc show dev eth0)
             if printf '%s\n' "$out" | grep -Eq 'prio|netem'; then echo "RESIDUO em $1" >&2; return 1; fi
             echo "ok sem prio em $1"; }
# espera os orfaos (sleep/ping herdam o fd do flock do injetor): so retorna com os locks livres
esperar_locks() { local n; for n in isn1 isn2 isn3; do
                    flock -w 200 "$LOCK_DIR/inject-netdegrade-$n.lock" true \
                      || { echo "ABORTAR: lock de $n preso (orfao?)" >&2; return 1; }
                  done; }
# cluster reformado: size=3 e HEALTHY nos tres nos, com limite (s)
espera_saudavel() {
  local lim="${1:-300}" t=0 n ip j ok
  while [ "$t" -lt "$lim" ]; do
    ok=1
    for n in isn1 isn2 isn3; do
      ip=$($PODMAN inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$n")
      j=$(curl -s -m 5 --digest -u admin:infinispan "http://$ip:11222/rest/v2/cache-managers/default/health" | tr -d ' \n' || true)
      case "$j" in
        *'"health_status":"HEALTHY"'*'"number_of_nodes":3'*|*'"number_of_nodes":3'*'"health_status":"HEALTHY"'*) ;;
        *) ok=0 ;;
      esac
    done
    [ "$ok" = 1 ] && { echo "saudavel apos ${t}s"; return 0; }
    sleep 5; t=$(( t + 5 ))
  done
  echo "RESULTADO: cluster nao reformou em ${lim}s (registrar; podman restart isn1 isn2 isn3)" | tee -a "$LOG/espera.log" >&2
  return 1
}
