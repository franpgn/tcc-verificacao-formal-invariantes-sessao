#!/usr/bin/env bash
# runs/gate-b29/g6.sh -- G-6 (coexistencia): F3 (jitter netem, egress) em isn2 e F2 (particao clsact) em isn3 AO MESMO TEMPO.
# PROPOSTA: NAO executar sem o "go" do Gestor. Execucao unica por go. `--dry-run` imprime o plano e sai sem tocar em nada.
# Esperado: cada injecao so mexe no netns do proprio no (isn2: qdisc root netem; isn3: qdisc clsact + 2 filtros u32);
# a limpeza da particao NAO remove o netem de isn2 (que dura mais) e a do jitter NAO toca em isn3.
# inject-jitter.sh nao tem --direction: o netem de raiz e sempre de saida (egress), que e o que o roteiro pede ("out").
# v3 (V-B2, 4.2.1-4.2.3 e 4.2.6): estado lido ANTES das sondas lentas e guardado em variavel (sem 'qd | grep -q');
# ultima amostra em +70 s; amostra que comecaria perto/depois do fim da F2 e PULADA (nao e FAIL); PID1/2/3 inicializados
# antes do trap; abort so limpa se algo foi armado; o tempo de convergencia conta a partir do FIM DA F2.
# Variaveis (teste com shims): G6_F2 (90), G6_F3 (130), G6_AMOSTRAS ("10 45 70"), G6_LIMITE_S (150), G6_MARGEM (5),
# G6_ESPERA_NETEM (30), G6_HEALTH_TIMEOUT (20), TCC_DIR, LOCK_DIR.
set -euo pipefail
F2="${G6_F2:-90}"; F3="${G6_F3:-130}"; AMOSTRAS="${G6_AMOSTRAS:-10 45 70}"; LIM="${G6_LIMITE_S:-150}"
MARGEM="${G6_MARGEM:-5}"; ESPERA_NETEM="${G6_ESPERA_NETEM:-30}"
if [ "${1:-}" = "--dry-run" ]; then
  cat <<PLANO
G-6 (dry-run; nada executado): F3 jitter em isn2 ${F3}s (20ms 13ms normal, egress), F2 particao em isn3 ${F2}s; amostras em +${AMOSTRAS// /s +}s
  0. exigir_go; preflight: size=3 HEALTHY x3, sem clsact/netem em isn1/isn2/isn3, 0 timers; guarda de netns (isn2, isn3 distintos entre si e do host)
  1. armar dead-man F2 (--heal isn3, ${F2}+90 s) e dead-man F3 (tc qdisc del root em isn2, ${F3}+90 s, com guarda de netns) ANTES de injetar
  2. inject-jitter.sh --node isn2 --duration ${F3} &  (espera netem em isn2); depois inject-partition.sh --minority isn3 --duration ${F2} --marker g6/marcadores.txt &
  3. amostras: o estado (isn2 so netem, isn3 so clsact) e lido primeiro; depois contadores, sonda TCP e saude (com timeout); amostra
     que comecaria a menos de ${MARGEM} s do fim da F2 (ou com a F2 ja encerrada) e PULADA; ao fim da particao: isn3 limpo e isn2 AINDA
     com netem; ao fim do jitter: isn2 limpo
  4. desarma os dois dead-man, espera size=3 HEALTHY (limite ${LIM} s, contado a partir do FIM DA F2); falha inesperada: trap EXIT
     (--heal isn3 + tc del root isn2, so se algo foi armado; dead-men permanecem armados)
PLANO
  exit 0
fi
source "$(dirname "$0")/g56-lib.sh"
exigir_go "G-6 (F3 jitter em isn2 + F2 particao em isn3, ${F2}/${F3} s)"
OUT="$LOG/g6"; mkdir -p "$OUT"; R="$OUT/resultado.txt"; : > "$R"
# tudo inicializado ANTES do trap: um abort em qualquer ponto nunca referencia variavel nao definida (set -u)
LIMPO=0; PJ=; PP=; PID1=; PID2=; PID3=; INJETOU=0
abortar() { local rc=$?
  [ "$LIMPO" = 1 ] && return 0
  if [ "$INJETOU" != 1 ]; then
    echo "ABORTANDO (rc=$rc): nada armado nem injetado; nada a limpar" >&2
    echo "ABORTADO rc=$rc $(date -Is) (antes de armar)" >> "$R"; return 0
  fi
  echo "ABORTANDO (rc=$rc): --heal em isn3 e remocao do netem em isn2; dead-men permanecem armados" >&2
  [ -z "$PP" ] || kill -KILL "$PP" 2>/dev/null || true
  [ -z "$PJ" ] || kill -TERM "$PJ" 2>/dev/null || true
  $INJ --heal --minority isn3 >> "$OUT/abort-heal.log" 2>&1 || true
  [ -z "$PID2" ] || T "$PID2" tc qdisc del dev eth0 root >> "$OUT/abort-heal.log" 2>&1 || true
  sem_clsact isn3 >> "$OUT/abort-heal.log" 2>&1 || true; sem_netem isn2 >> "$OUT/abort-heal.log" 2>&1 || true
  echo "ABORTADO rc=$rc $(date -Is)" >> "$R"
}
trap abortar EXIT
PID2=$(pid_de isn2); PID3=$(pid_de isn3); PID1=$(pid_de isn1)
NS2=$(sudo readlink "/proc/$PID2/ns/net"); NS3=$(sudo readlink "/proc/$PID3/ns/net")
[ "$NS2" != "$NS3" ] || { echo "ABORTAR: isn2 e isn3 no mesmo netns" >&2; exit 1; }
IP1=$($PODMAN inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' isn1)
ok()   { echo "PASS $*" | tee -a "$R"; }
falha(){ echo "FAIL $*" | tee -a "$R" >&2; exit 1; }
qd() { local o; o=$(T "$1" tc qdisc show dev eth0); echo "$o"; }   # qd <pid>
tem() { grep -q -e "$2" <<<"$1"; }                                # tem <saida-capturada> <padrao>  (sem pipe: nada de pipefail/141)
health_t() { timeout "${G6_HEALTH_TIMEOUT:-20}" bash -c "$(declare -f health); health" || echo "(health excedeu o timeout de ${G6_HEALTH_TIMEOUT:-20} s)"; }

preflight "$OUT/saude-antes.txt"
date +%s > "$OUT/t0s.txt"
INJETOU=1                    # a partir daqui o abort limpa
armar "$F2"                 # F2: --heal isn3
armar_netem isn2 "$F3"      # F3: tc qdisc del root em isn2 (corpo testado em T-g6-7)
# F3 primeiro (precisa estar ativa quando a F2 comecar)
set +e; bash scripts/inject-jitter.sh --node isn2 --duration "$F3" > "$OUT/jitter.log" 2>&1 & PJ=$!; set -e
t=0; while :; do o2=$(qd "$PID2"); tem "$o2" netem && break; t=$((t+1)); [ "$t" -lt "$ESPERA_NETEM" ] || falha "netem nao apareceu em isn2 em ${ESPERA_NETEM} s"; sleep 1; done
echo "t_f3_on_ns=$(date +%s%N)" | tee "$OUT/tempos.txt"
set +e; $INJ --minority isn3 --duration "$F2" --marker "$OUT/marcadores.txt" > "$OUT/inj.log" 2>&1 & PP=$!; set -e
espera_marcador "$OUT/marcadores.txt" start 40 || falha "sem event=start da particao em 40 s"
S0=$(date +%s); NAMOSTRAS=0
for t in $AMOSTRAS; do
  while [ $(( $(date +%s) - S0 )) -lt "$t" ]; do sleep 1; done
  # amostra tardia: a F2 ja acabou (ou acaba em < MARGEM s): checar o estado agora daria falso FAIL; pula e registra
  if ! kill -0 "$PP" 2>/dev/null || [ $(( $(date +%s) - S0 )) -ge $(( F2 - MARGEM )) ]; then
    echo "amostra +${t}s PULADA: F2 ja encerrada ou a menos de ${MARGEM} s do fim (decorrido=$(( $(date +%s) - S0 )) s)" | tee -a "$R"; continue
  fi
  # 1) ESTADO primeiro (barato), antes de qualquer sonda lenta; fica guardado em variavel
  o2=$(qd "$PID2"); o3=$(qd "$PID3"); ts_est=$(date -Is)
  # 2) sondas lentas (so registro; qualquer falha aqui nao decide o resultado)
  { echo "=== t=+${t}s (desde o start da F2) estado lido em $ts_est; sondas ate $(date -Is)"
    echo "-- isn2 (esperado: so netem)"; echo "$o2"; T "$PID2" tc -s qdisc show dev eth0 | grep -E 'Sent|netem' || true
    echo "-- isn3 (esperado: so clsact)"; echo "$o3"
    echo "-- contadores por filtro isn3"; cont_filtros_pid "$PID3" || true
    echo "-- isn3 -> isn1:7800 (esperado falha rc=124)"
    T "$PID3" timeout 3 bash -c "exec 3<>/dev/tcp/$IP1/7800"; echo "rc=$?"
    echo "-- saude 11222/11223/11224"; health_t
  } >> "$OUT/janela.log" 2>&1 || true
  # 3) decisao sobre o estado capturado em (1)
  { tem "$o2" netem && ! tem "$o2" clsact; } || falha "t=+${t}s: isn2 sem netem ou com clsact"
  { tem "$o3" clsact && ! tem "$o3" netem; } || falha "t=+${t}s: isn3 sem clsact ou com netem"
  NAMOSTRAS=$((NAMOSTRAS+1))
done
[ "$NAMOSTRAS" -ge 2 ] || falha "so $NAMOSTRAS amostra(s) valida(s) dentro da janela (minimo 2)"
ok "durante a janela: isn2 so netem, isn3 so clsact (${NAMOSTRAS} amostras validas de: +${AMOSTRAS// /s +}s)"
set +e; wait "$PP"; RC=$?; set -e; PP=
T_F2FIM=$(date +%s)
# estado logo apos o fim da F2, capturado de uma vez (antes de qualquer decisao)
o3=$(qd "$PID3"); o2=$(qd "$PID2"); f3_viva=0; ! kill -0 "$PJ" 2>/dev/null || f3_viva=1
echo "t_f2_fim_ns=$(date +%s%N) t_f2_fim_s=$T_F2FIM rc_particao=$RC" | tee -a "$OUT/tempos.txt"
[ "$RC" = 0 ] && ok "F2: injetor rc=0" || falha "F2: injetor rc=$RC"
{ echo "=== logo apos o fim da F2 $(date -Is)"; echo "-- isn3"; echo "$o3"; echo "-- isn2"; echo "$o2"; } | tee "$OUT/estado-pos-f2.txt"
! tem "$o3" clsact && ok "F2: isn3 sem clsact apos a limpeza da particao" || falha "F2: clsact residual em isn3"
[ "$f3_viva" = 1 ] && tem "$o2" netem \
  && ok "a limpeza da F2 NAO removeu o netem de isn2 (F3 ainda ativa)" || falha "F3 acabou antes da F2 ou a limpeza da F2 removeu o netem de isn2"
set +e; wait "$PJ"; RJ=$?; set -e; PJ=
echo "t_f3_fim_ns=$(date +%s%N) rc_jitter=$RJ" | tee -a "$OUT/tempos.txt"
[ "$RJ" = 0 ] && ok "F3: injetor rc=0" || falha "F3: injetor rc=$RJ"
o3=$(qd "$PID3"); o2=$(qd "$PID2"); o1=$(qd "$PID1")
{ echo "=== apos o fim da F3 $(date -Is)"; echo "-- isn3"; echo "$o3"; echo "-- isn2"; echo "$o2"; echo "-- isn1"; echo "$o1"; } | tee "$OUT/estado-pos-f3.txt"
! tem "$o2" netem && ok "F3: isn2 sem netem apos a limpeza do jitter" || falha "F3: netem residual em isn2"
{ ! tem "$o3" clsact && ! tem "$o3" netem; } && ok "F3: a limpeza do jitter nao tocou em isn3 (continua limpo)" || falha "isn3 com regra apos o fim"
desarmar; desarmar_netem
# o limite e o tempo valem a partir do FIM DA F2 (comparavel entre execucoes); o F3 ainda corria ~40 s depois
espera_saudavel "$LIM" "$T_F2FIM" "o fim da F2" | tee -a "$R" || falha "cluster nao voltou a size=3 HEALTHY em ${LIM} s desde o fim da F2"
preflight "$OUT/saude-depois.txt"
T0=$(cat "$OUT/t0s.txt")
for n in isn1 isn2 isn3; do $PODMAN logs --since "$(date -d @$((T0-5)) -Iseconds)" "$n" > "$OUT/log-completo-$n.txt" 2>&1 || true
  grep -iE 'view|ISPN000094|ISPN000093|merge|partition|DEGRADED' "$OUT/log-completo-$n.txt" > "$OUT/log-vista-$n.txt" || true; done
LIMPO=1
echo "G-6 concluido: ver $R"
