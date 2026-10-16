#!/usr/bin/env bash
# runs/gate-b29/g5.sh -- G-5 (sinais): kill -TERM e depois kill -KILL do inject-partition.sh em isn3, janela curta.
# PROPOSTA: NAO executar sem o "go" do Gestor. Execucao unica por go. `--dry-run` imprime o plano e sai sem tocar em nada.
# Esperado: TERM -> o trap remove a regra e ha marcador end; KILL -> a regra FICA ate o --heal manual; o --heal a remove.
# Variaveis (teste com shims): G5_DUR (40), G5_T_KILL (15), G5_LIMITE_S (150), G5_ESPERA_START (40), TCC_DIR, LOCK_DIR.
set -euo pipefail
DUR="${G5_DUR:-40}"; T_KILL="${G5_T_KILL:-15}"; LIM="${G5_LIMITE_S:-150}"; ESPERA_START="${G5_ESPERA_START:-40}"
if [ "${1:-}" = "--dry-run" ]; then
  cat <<PLANO
G-5 (dry-run; nada executado): duracao=${DUR}s kill em +${T_KILL}s, limite de reforma=${LIM}s
  0. exigir_go; preflight: size=3 HEALTHY x3, sem clsact/netem em isn1/isn2/isn3, 0 timers tcc-heal
  1. fase TERM: armar dead-man (--heal, ${DUR}+90 s); inject-partition.sh --minority isn3 --duration ${DUR} --marker g5/marcadores-term.txt &
     espera event=start; +${T_KILL}s: clsact presente; kill -TERM; esperado rc=143, sem clsact, marcador end; desarmar; espera size=3 HEALTHY
  2. fase KILL: preflight; armar dead-man (cobre o KILL); mesma injecao; +${T_KILL}s: kill -KILL; esperado rc=137, clsact AINDA presente,
     sem marcador end; inject-partition.sh --heal --minority isn3 (rc 0); sem clsact; desarmar; espera size=3 HEALTHY
  3. falha inesperada em qualquer ponto: trap EXIT aciona --heal e deixa o dead-man armado
PLANO
  exit 0
fi
source "$(dirname "$0")/g56-lib.sh"
exigir_go "G-5 (kill -TERM e kill -KILL do injetor em isn3, ${DUR} s)"
OUT="$LOG/g5"; mkdir -p "$OUT"; R="$OUT/resultado.txt"; : > "$R"
PID3=$(pid_de isn3)
LIMPO=0; P=
abortar() { local rc=$?
  [ "$LIMPO" = 1 ] && return 0
  echo "ABORTANDO (rc=$rc): acionando --heal em isn3; dead-man permanece armado" >&2
  [ -z "$P" ] || kill -KILL "$P" 2>/dev/null || true
  $INJ --heal --minority isn3 >> "$OUT/abort-heal.log" 2>&1 || true
  sem_clsact isn3 >> "$OUT/abort-heal.log" 2>&1 || true
  echo "ABORTADO rc=$rc $(date -Is)" >> "$R"
}
trap abortar EXIT
tem_clsact() { local o; o=$(T "$PID3" tc qdisc show dev eth0); echo "$o" > "$OUT/.qdisc"; grep -q clsact "$OUT/.qdisc"; }
ok()   { echo "PASS $*" | tee -a "$R"; }
falha(){ echo "FAIL $*" | tee -a "$R" >&2; exit 1; }

# fase <term|kill>: injeta, mata o injetor com o sinal, registra o estado
fase() {
  local sig="$1" M="$OUT/marcadores-$1.txt" RC SPID
  preflight "$OUT/saude-antes-$sig.txt"
  date -Is > "$OUT/t0-$sig.txt"; date +%s > "$OUT/t0s-$sig.txt"
  armar "$DUR"
  set +e; $INJ --minority isn3 --duration "$DUR" --marker "$M" > "$OUT/inj-$sig.log" 2>&1 & P=$!; set -e
  espera_marcador "$M" start "$ESPERA_START" || falha "$sig: sem event=start em ${ESPERA_START} s"
  sleep "$T_KILL"
  { echo "=== antes do kill -${sig^^} $(date -Is)"; T "$PID3" tc qdisc show dev eth0
    echo "-- contadores por filtro"; cont_filtros_pid "$PID3" || true
    echo "-- ps do injetor"; ps -o pid,ppid,stat,cmd --ppid "$P" 2>/dev/null || true; } >> "$OUT/estado-$sig.txt" 2>&1
  tem_clsact || falha "$sig: clsact ausente ANTES do sinal (injecao nao ativa)"
  SPID=$(pgrep -P "$P" 2>/dev/null | tr '\n' ' ' || true); echo "filhos do injetor: ${SPID:-<nenhum>}" >> "$OUT/estado-$sig.txt"
  echo "t_sinal_ns=$(date +%s%N) sinal=$sig" | tee -a "$OUT/estado-$sig.txt"
  # se o injetor ja terminou por conta propria, o sinal nao e entregue: segue para o rc real (falha na checagem)
  kill -"${sig^^}" "$P" 2>/dev/null || echo "aviso: sinal ${sig^^} nao entregue (injetor ja havia terminado)" | tee -a "$OUT/estado-$sig.txt"
  set +e; wait "$P"; RC=$?; set -e; P=
  echo "rc_injetor=$RC" | tee -a "$OUT/estado-$sig.txt"
  sleep 2
  { echo "=== apos o sinal $(date -Is)"; T "$PID3" tc qdisc show dev eth0
    echo "-- marcadores"; cat "$M"
    echo "-- lock isn3 (rc 0 = livre; 1 = ocupado por filho orfao)"; set +e; flock -n "${LOCK_DIR}/inject-partition-isn3.lock" true; echo "rc=$?"; set -e; } >> "$OUT/estado-$sig.txt" 2>&1
  echo "$RC" > "$OUT/rc-$sig.txt"; echo "$SPID" > "$OUT/filhos-$sig.txt"
}

# ---- fase TERM
fase term
rc=$(cat "$OUT/rc-term.txt")
[ "$rc" = 143 ] && ok "TERM: injetor saiu com rc=143 (128+SIGTERM, via trap)" || falha "TERM: rc=$rc (esperado 143)"
tem_clsact && falha "TERM: clsact RESIDUAL apos o trap" || ok "TERM: sem clsact apos o trap"
[ "$(grep -c 'event=start' "$OUT/marcadores-term.txt")" = 1 ] && [ "$(grep -c 'event=end' "$OUT/marcadores-term.txt")" = 1 ] \
  && ok "TERM: marcadores start e end presentes" || falha "TERM: marcadores incompletos ($(tr '\n' ';' < "$OUT/marcadores-term.txt"))"
desarmar
espera_saudavel "$LIM" | tee -a "$R" || falha "TERM: cluster nao voltou a size=3 HEALTHY em ${LIM} s"

# ---- fase KILL (o dead-man armado dentro de fase cobre este caso)
fase kill
rc=$(cat "$OUT/rc-kill.txt")
[ "$rc" = 137 ] && ok "KILL: injetor saiu com rc=137 (128+SIGKILL)" || falha "KILL: rc=$rc (esperado 137)"
tem_clsact && ok "KILL: clsact FICOU (esperado: nenhum trap captura SIGKILL)" || falha "KILL: clsact sumiu (inesperado)"
[ "$(grep -c 'event=end' "$OUT/marcadores-kill.txt")" = 0 ] && ok "KILL: sem marcador end (esperado)" || falha "KILL: marcador end presente (inesperado)"
echo "t_heal_ns=$(date +%s%N)" | tee -a "$OUT/estado-kill.txt"
set +e; $INJ --heal --minority isn3 > "$OUT/heal.log" 2>&1; HRC=$?; set -e
[ "$HRC" = 0 ] && ok "KILL: --heal rc=0" || falha "KILL: --heal rc=$HRC"
tem_clsact || true; cp "$OUT/.qdisc" "$OUT/qdisc-pos-heal.txt"; ! grep -q clsact "$OUT/qdisc-pos-heal.txt" || falha "KILL: clsact RESIDUAL apos --heal"; ok "KILL: sem clsact apos --heal"
desarmar
# filho orfao (sleep da janela) termina sozinho; espera para nao deixar processo solto
for c in $(cat "$OUT/filhos-kill.txt"); do w=0; while kill -0 "$c" 2>/dev/null && [ "$w" -lt 60 ]; do sleep 1; w=$((w+1)); done; done
espera_saudavel "$LIM" | tee -a "$R" || falha "KILL: cluster nao voltou a size=3 HEALTHY em ${LIM} s"
preflight "$OUT/saude-depois.txt" | tee -a "$OUT/estado-final.txt"
# logs de vista do periodo (sem corte: a janela curta pode nem ter mudado a vista; isso tambem e dado)
T0=$(cat "$OUT/t0s-term.txt")
for n in isn1 isn2 isn3; do $PODMAN logs --since "$(date -d @$((T0-5)) -Iseconds)" "$n" > "$OUT/log-completo-$n.txt" 2>&1 || true
  grep -iE 'view|ISPN000094|ISPN000093|merge|partition|DEGRADED' "$OUT/log-completo-$n.txt" > "$OUT/log-vista-$n.txt" || true; done
rm -f "$OUT/.qdisc"; LIMPO=1
echo "G-5 concluido: ver $R"
