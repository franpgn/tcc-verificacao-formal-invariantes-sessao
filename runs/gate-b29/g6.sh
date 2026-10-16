#!/usr/bin/env bash
# runs/gate-b29/g6.sh -- G-6 (coexistencia): F3 (jitter netem, egress) em isn2 e F2 (particao clsact) em isn3 AO MESMO TEMPO.
# PROPOSTA: NAO executar sem o "go" do Gestor. Execucao unica por go. `--dry-run` imprime o plano e sai sem tocar em nada.
# Esperado: cada injecao so mexe no netns do proprio no (isn2: qdisc root netem; isn3: qdisc clsact + 2 filtros u32);
# a limpeza da particao NAO remove o netem de isn2 (que dura mais) e a do jitter NAO toca em isn3.
# inject-jitter.sh nao tem --direction: o netem de raiz e sempre de saida (egress), que e o que o roteiro pede ("out").
# Variaveis (teste com shims): G6_F2 (90), G6_F3 (130), G6_AMOSTRAS ("10 45 80"), G6_LIMITE_S (150), TCC_DIR, LOCK_DIR.
set -euo pipefail
F2="${G6_F2:-90}"; F3="${G6_F3:-130}"; AMOSTRAS="${G6_AMOSTRAS:-10 45 80}"; LIM="${G6_LIMITE_S:-150}"
if [ "${1:-}" = "--dry-run" ]; then
  cat <<PLANO
G-6 (dry-run; nada executado): F3 jitter em isn2 ${F3}s (20ms 13ms normal, egress), F2 particao em isn3 ${F2}s; amostras em +${AMOSTRAS// /s +}s
  0. exigir_go; preflight: size=3 HEALTHY x3, sem clsact/netem em isn1/isn2/isn3, 0 timers; guarda de netns (isn2, isn3 distintos entre si e do host)
  1. armar dead-man F2 (--heal isn3, ${F2}+90 s) e dead-man F3 (tc qdisc del root em isn2, ${F3}+90 s) ANTES de injetar
  2. inject-jitter.sh --node isn2 --duration ${F3} &  (espera netem em isn2); depois inject-partition.sh --minority isn3 --duration ${F2} --marker g6/marcadores.txt &
  3. amostras: isn2 so netem, isn3 so clsact, contadores, saude; ao fim da particao: isn3 limpo e isn2 AINDA com netem; ao fim do jitter: isn2 limpo
  4. desarma os dois dead-man, espera size=3 HEALTHY (limite ${LIM} s); falha inesperada: trap EXIT --heal isn3 + tc del root isn2
PLANO
  exit 0
fi
source "$(dirname "$0")/g56-lib.sh"
exigir_go "G-6 (F3 jitter em isn2 + F2 particao em isn3, ${F2}/${F3} s)"
OUT="$LOG/g6"; mkdir -p "$OUT"; R="$OUT/resultado.txt"; : > "$R"
PID2=$(pid_de isn2); PID3=$(pid_de isn3); PID1=$(pid_de isn1)
NS2=$(sudo readlink "/proc/$PID2/ns/net"); NS3=$(sudo readlink "/proc/$PID3/ns/net")
[ "$NS2" != "$NS3" ] || { echo "ABORTAR: isn2 e isn3 no mesmo netns" >&2; exit 1; }
LIMPO=0; PJ=; PP=
abortar() { local rc=$?
  [ "$LIMPO" = 1 ] && return 0
  echo "ABORTANDO (rc=$rc): --heal em isn3 e remocao do netem em isn2; dead-men permanecem armados" >&2
  [ -z "$PP" ] || kill -KILL "$PP" 2>/dev/null || true
  [ -z "$PJ" ] || kill -TERM "$PJ" 2>/dev/null || true
  $INJ --heal --minority isn3 >> "$OUT/abort-heal.log" 2>&1 || true
  T "$PID2" tc qdisc del dev eth0 root >> "$OUT/abort-heal.log" 2>&1 || true
  sem_clsact isn3 >> "$OUT/abort-heal.log" 2>&1 || true; sem_netem isn2 >> "$OUT/abort-heal.log" 2>&1 || true
  echo "ABORTADO rc=$rc $(date -Is)" >> "$R"
}
trap abortar EXIT
ok()   { echo "PASS $*" | tee -a "$R"; }
falha(){ echo "FAIL $*" | tee -a "$R" >&2; exit 1; }
qd() { local o; o=$(T "$1" tc qdisc show dev eth0); echo "$o"; }   # qd <pid>

preflight "$OUT/saude-antes.txt"
date +%s > "$OUT/t0s.txt"
armar "$F2"                 # F2: --heal isn3
armar_netem isn2 "$F3"      # F3: tc qdisc del root em isn2
# F3 primeiro (precisa estar ativa quando a F2 comecar)
set +e; bash scripts/inject-jitter.sh --node isn2 --duration "$F3" > "$OUT/jitter.log" 2>&1 & PJ=$!; set -e
t=0; until qd "$PID2" | grep -q netem; do t=$((t+1)); [ "$t" -lt 30 ] || falha "netem nao apareceu em isn2 em 30 s"; sleep 1; done
echo "t_f3_on_ns=$(date +%s%N)" | tee "$OUT/tempos.txt"
set +e; $INJ --minority isn3 --duration "$F2" --marker "$OUT/marcadores.txt" > "$OUT/inj.log" 2>&1 & PP=$!; set -e
espera_marcador "$OUT/marcadores.txt" start 40 || falha "sem event=start da particao em 40 s"
S0=$(date +%s)
for t in $AMOSTRAS; do
  while [ $(( $(date +%s) - S0 )) -lt "$t" ]; do sleep 1; done
  { echo "=== t=+${t}s (desde o start da F2) $(date -Is)"
    echo "-- isn2 (esperado: so netem)"; qd "$PID2"; T "$PID2" tc -s qdisc show dev eth0 | grep -E 'Sent|netem' || true
    echo "-- isn3 (esperado: so clsact)"; qd "$PID3"
    echo "-- contadores por filtro isn3"; cont_filtros_pid "$PID3" || true
    echo "-- isn3 -> isn1:7800 (esperado falha rc=124)"; IP1=$($PODMAN inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' isn1)
    T "$PID3" timeout 3 bash -c "exec 3<>/dev/tcp/$IP1/7800"; echo "rc=$?"
    echo "-- saude 11222/11223/11224"; health
  } >> "$OUT/janela.log" 2>&1 || true
  qd "$PID2" | grep -q netem  && ! qd "$PID2" | grep -q clsact || falha "t=+${t}s: isn2 sem netem ou com clsact"
  qd "$PID3" | grep -q clsact && ! qd "$PID3" | grep -q netem || falha "t=+${t}s: isn3 sem clsact ou com netem"
done
ok "durante a janela: isn2 so netem, isn3 so clsact (amostras +${AMOSTRAS// /s +}s)"
set +e; wait "$PP"; RC=$?; set -e; PP=
echo "t_f2_fim_ns=$(date +%s%N) rc_particao=$RC" | tee -a "$OUT/tempos.txt"
[ "$RC" = 0 ] && ok "F2: injetor rc=0" || falha "F2: injetor rc=$RC"
{ echo "=== logo apos o fim da F2 $(date -Is)"; echo "-- isn3"; qd "$PID3"; echo "-- isn2"; qd "$PID2"; } | tee "$OUT/estado-pos-f2.txt"
! qd "$PID3" | grep -q clsact && ok "F2: isn3 sem clsact apos a limpeza da particao" || falha "F2: clsact residual em isn3"
kill -0 "$PJ" 2>/dev/null && qd "$PID2" | grep -q netem \
  && ok "a limpeza da F2 NAO removeu o netem de isn2 (F3 ainda ativa)" || falha "F3 acabou antes da F2 ou a limpeza da F2 removeu o netem de isn2"
set +e; wait "$PJ"; RJ=$?; set -e; PJ=
echo "t_f3_fim_ns=$(date +%s%N) rc_jitter=$RJ" | tee -a "$OUT/tempos.txt"
[ "$RJ" = 0 ] && ok "F3: injetor rc=0" || falha "F3: injetor rc=$RJ"
{ echo "=== apos o fim da F3 $(date -Is)"; echo "-- isn3"; qd "$PID3"; echo "-- isn2"; qd "$PID2"; echo "-- isn1"; qd "$PID1"; } | tee "$OUT/estado-pos-f3.txt"
! qd "$PID2" | grep -q netem && ok "F3: isn2 sem netem apos a limpeza do jitter" || falha "F3: netem residual em isn2"
! qd "$PID3" | grep -q -E 'clsact|netem' && ok "F3: a limpeza do jitter nao tocou em isn3 (continua limpo)" || falha "isn3 com regra apos o fim"
desarmar; desarmar_netem
espera_saudavel "$LIM" | tee -a "$R" || falha "cluster nao voltou a size=3 HEALTHY em ${LIM} s"
preflight "$OUT/saude-depois.txt"
T0=$(cat "$OUT/t0s.txt")
for n in isn1 isn2 isn3; do $PODMAN logs --since "$(date -d @$((T0-5)) -Iseconds)" "$n" > "$OUT/log-completo-$n.txt" 2>&1 || true
  grep -iE 'view|ISPN000094|ISPN000093|merge|partition|DEGRADED' "$OUT/log-completo-$n.txt" > "$OUT/log-vista-$n.txt" || true; done
LIMPO=1
echo "G-6 concluido: ver $R"
