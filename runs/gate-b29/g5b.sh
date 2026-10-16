#!/usr/bin/env bash
# runs/gate-b29/g5b.sh -- G-5b (S6/S7 da sequencia do Senior): o que o G-5 de 2026-10-16 nao provou.
#   O G-5 usou janela de 40 s (MENOR que a deteccao, ~40-46 s) e desarmou o dead-man em todas as fases. Faltou provar
#   (a) a cura (--heal) DEPOIS DE UM SPLIT REAL e (b) o dead-man DISPARANDO de fato no caminho que toca tc.
#   Modo A: split real confirmado nos logs -> kill -KILL do injetor -> o corte PERSISTE >= G5B_PERSISTE_S -> `--heal` manual ->
#           MergeView de 3 membros -> HEALTHY x3 -> so entao desarma o dead-man (armado com dur + 90 s: rede de seguranca).
#   Modo B: mesma injecao e mesmo KILL, mas SEM --heal manual e SEM desarmar. O dead-man (armado pela mesma funcao `armar`, so com
#           margem curta: dur + 25 s) e o UNICO agente de cura; precisa disparar entre dur+margem e dur+margem+G5B_TOL_S.
# AVISO DE COMPARABILIDADE: estas particoes NAO entram no conjunto do G-4; os tempos sao dado de seguranca, rotulados como tal.
# PROPOSTA: NAO executar sem o "go" do Gestor (digitado num terminal, com o Gestor presente). Um modo por go; A antes de B, com o
# cluster restabelecido pelo portao entre eles. Qualquer intervencao manual no modo B invalida o bloco.
# Uso:  bash runs/gate-b29/g5b.sh A|B [--dry-run]       (--dry-run imprime o plano e sai sem tocar em nada)
# Variaveis (testes com shims): G5B_DUR (120), G5B_T_KILL (70), G5B_MARGEM_A (90), G5B_MARGEM_B (25), G5B_ESPERA_START (40),
#   G5B_ESPERA_SPLIT (100), G5B_PERSISTE_S (15), G5B_LIMITE_S (150), G5B_TOL_S (10), G5B_FOLGA_S (30), G5B_ACC (1), TCC_DIR, LOCK_DIR.
# PRECISAO DO TIMER (achado de S0): o systemd-run cria timers com AccuracySec=1min (disparo pode atrasar ate ~1 min por
# coalescencia; medido em WSL: +3, +3, +52, +41 s num --on-active=30s). Sem corrigir, a janela do modo B nao e verificavel. Por
# isso este script redefine `armar` IGUAL ao de g-lib.sh + `--timer-property=AccuracySec=1s` (o corpo da unit nao muda).
# G5B_ACC=0 usa o `armar` de g-lib.sh sem a propriedade (entao alargar G5B_TOL_S ate ~60).
set -euo pipefail
MODO="${1:-}"
case "$MODO" in A|B) ;; *) echo "uso: $0 A|B [--dry-run]" >&2; exit 2;; esac
DUR="${G5B_DUR:-120}"; T_KILL="${G5B_T_KILL:-70}"; MARGEM_A="${G5B_MARGEM_A:-90}"; MARGEM_B="${G5B_MARGEM_B:-25}"
ESPERA_START="${G5B_ESPERA_START:-40}"; ESPERA_SPLIT="${G5B_ESPERA_SPLIT:-100}"; PERSISTE="${G5B_PERSISTE_S:-15}"
LIM="${G5B_LIMITE_S:-150}"; TOL="${G5B_TOL_S:-10}"; ACC="${G5B_ACC:-1}"; FOLGA="${G5B_FOLGA_S:-30}"
if [ "$MODO" = A ]; then MARGEM="$MARGEM_A"; else MARGEM="$MARGEM_B"; fi
if [ "${2:-}" = "--dry-run" ]; then
  cat <<PLANO
G-5b-$MODO (dry-run; nada executado): duracao=${DUR}s, KILL em +${T_KILL}s do start (e so com split confirmado), margem do dead-man=${MARGEM}s
  0. exigir_go; preflight: size=3 HEALTHY x3, sem clsact/netem em isn1/isn2/isn3, 0 timers tcc-heal
  1. armar dead-man (${DUR}+${MARGEM} = $(( DUR + MARGEM )) s, AccuracySec=1s); registrar systemctl cat da unit
  2. inject-partition.sh --minority isn3 --duration ${DUR} --marker g5b-$MODO/marcadores.txt &; espera event=start
  3. esperar o split CONFIRMADO nos logs (ate ${ESPERA_SPLIT} s do start): isn1 e isn2 ISPN000094 com 2 membros; isn3 com 1 membro e
     DEGRADED_MODE. Sem split confirmado: FAIL (repeticao do G-5, nao vale) e o abort aciona --heal
  4. kill -KILL do injetor (a partir de +${T_KILL}s): esperado rc=137, sem event=end, clsact FICA; persiste por >= ${PERSISTE} s
     (clsact presente, sem marcador end, sem MergeView nos logs desde o KILL)
PLANO
  if [ "$MODO" = A ]; then cat <<PLANO
  5A. inject-partition.sh --heal --minority isn3 (rc 0); sem clsact; MergeView de 3 membros nos 3 logs; HEALTHY x3 (limite ${LIM} s)
  6A. SO ENTAO desarmar o dead-man (0 timers); cronologia split < KILL < heal < MergeView
PLANO
  else cat <<PLANO
  5B. NENHUM --heal e NENHUM desarmar: esperar o dead-man. Disparo entre ${DUR}+${MARGEM}=$(( DUR + MARGEM )) s e +${TOL} s da armacao
  6B. journal da unit (heal executado, sem falha); sem clsact; MergeView x3; HEALTHY x3; 0 timers; nenhum --heal manual registrado
PLANO
  fi
  echo "  7. falha inesperada em qualquer ponto: trap EXIT aciona --heal e deixa o dead-man armado (no modo B isso invalida o bloco)"
  exit 0
fi
source "$(dirname "$0")/g56-lib.sh"
if [ "$ACC" = 1 ]; then
armar()    { DMS="tcc-heal-$$-$RANDOM"
             sudo systemd-run --quiet --unit="$DMS" --on-active="$(( $1 + ${2:-90} ))s" --timer-property=AccuracySec=1s \
               --setenv=PODMAN="sudo podman" --working-directory="$PWD" \
               bash scripts/inject-partition.sh --heal --minority isn3
             echo "dead-man armado: $DMS em $(( $1 + ${2:-90} ))s"; }
fi
exigir_go "G-5b-$MODO (split real + kill -KILL do injetor em isn3, ${DUR} s, dead-man ${MARGEM} s)"
OUT="$LOG/g5b-$MODO"; mkdir -p "$OUT"; R="$OUT/resultado.txt"; : > "$R"; : > "$OUT/cronologia.txt"; : > "$OUT/chamadas-injetor.txt"
M="$OUT/marcadores.txt"; : > "$M"
PID3=$(pid_de isn3)
LIMPO=0; P=; UNIT=
ns() { date +%s%N; }
carimbo() { echo "$1=$(ns)" >> "$OUT/cronologia.txt"; }
cron() { sed -n "s/^$1=//p" "$OUT/cronologia.txt" | tail -1; }
inj() { echo "$(date -Is) $*" >> "$OUT/chamadas-injetor.txt"; $INJ "$@"; }   # toda chamada ao injetor fica registrada
abortar() { local rc=$?
  [ "$LIMPO" = 1 ] && return 0
  echo "ABORTANDO (rc=$rc): acionando --heal em isn3; dead-man permanece armado" >&2
  [ -z "$P" ] || kill -KILL "$P" 2>/dev/null || true
  inj --heal --minority isn3 >> "$OUT/abort-heal.log" 2>&1 || true
  sem_clsact isn3 >> "$OUT/abort-heal.log" 2>&1 || true
  echo "ABORTADO rc=$rc $(date -Is)" >> "$R"
}
tem_clsact() { local o; o=$(T "$PID3" tc qdisc show dev eth0); echo "$o" > "$OUT/.qdisc"; grep -q clsact "$OUT/.qdisc"; }
ok()   { echo "PASS $*" | tee -a "$R"; }
falha(){ echo "FAIL $*" | tee -a "$R" >&2; exit 1; }
aviso(){ echo "AVISO $*" | tee -a "$R"; }

# ---- logs de vista (sinais de split/merge); "desde" e um instante ISO para `podman logs --since`
logs_desde() { $PODMAN logs --since "$1" "$2" 2>&1 || true; }   # logs_desde <iso> <no>
split_confirmado() { # isn1 e isn2 com vista de 2 membros; isn3 com vista de 1 membro e DEGRADED_MODE
  local l1 l2 l3; l1=$(logs_desde "$SINCE" isn1); l2=$(logs_desde "$SINCE" isn2); l3=$(logs_desde "$SINCE" isn3)
  grep -Eq 'ISPN000094.*\] \(2\) \[' <<<"$l1" && grep -Eq 'ISPN000094.*\] \(2\) \[' <<<"$l2" \
    && grep -Eq 'ISPN000094.*\] \(1\) \[' <<<"$l3" && grep -q 'DEGRADED_MODE' <<<"$l3"
}
merge_ok() { # MergeView de 3 membros nos tres nos
  local n l; for n in isn1 isn2 isn3; do l=$(logs_desde "$SINCE" "$n"); grep -Eq 'ISPN000093.*MergeView::\[[^]]*\] \(3\)' <<<"$l" || return 1; done
}
sem_merge_desde() { # nenhum ISPN000093 nos tres nos desde <iso>
  local n l; for n in isn1 isn2 isn3; do l=$(logs_desde "$1" "$n"); ! grep -q 'ISPN000093' <<<"$l" || return 1; done
}
espera_merge() { # espera_merge <base-epoch-s> : MergeView x3 em ate LIM s
  until merge_ok; do
    [ $(( $(date +%s) - $1 )) -lt "$LIM" ] || return 1; sleep 2
  done
}

# ---- fase comum: preflight, dead-man, injecao, split confirmado, KILL, persistencia do corte
preflight "$OUT/saude-antes.txt"
# o trap de seguranca so vale depois do preflight: um preflight que falha (cluster sujo) sai sem tocar em nada
trap abortar EXIT
T0S=$(date +%s); SINCE=$(date -d @$((T0S-5)) -Iseconds); echo "$(date -Is)" > "$OUT/t0.txt"
T0N=$(ns); armar "$DUR" "$MARGEM"; T1N=$(ns)
echo "t_antes_armar=$T0N" >> "$OUT/cronologia.txt"; echo "t_depois_armar=$T1N" >> "$OUT/cronologia.txt"
UNIT="$DMS"
{ echo "unit=$UNIT modo=$MODO dur=$DUR margem=$MARGEM prazo_s=$(( DUR + MARGEM ))"
  echo "--- systemctl cat ${UNIT}.service"; sudo systemctl cat "${UNIT}.service" 2>&1 || true
  echo "--- systemctl cat ${UNIT}.timer";   sudo systemctl cat "${UNIT}.timer" 2>&1 || true
  echo "--- AccuracySec"; sudo systemctl show "${UNIT}.timer" -p AccuracySec -p OnActiveSec 2>&1 || true
  echo "--- list-timers"; systemctl list-timers --all 2>&1 | grep tcc-heal || echo "(nenhum)"; } > "$OUT/unit-armada.txt"
grep -q 'inject-partition.sh --heal --minority isn3' "$OUT/unit-armada.txt" && ok "unit armada executa inject-partition.sh --heal --minority isn3" \
  || falha "corpo da unit nao chama inject-partition.sh --heal --minority isn3"
# o injetor roda DIRETO em segundo plano (nao por funcao): $! tem de ser o pid do proprio bash do injetor, que e quem recebe o KILL
echo "$(date -Is) --minority isn3 --duration $DUR --marker $M" >> "$OUT/chamadas-injetor.txt"
set +e; $INJ --minority isn3 --duration "$DUR" --marker "$M" > "$OUT/inj.log" 2>&1 & P=$!; set -e
espera_marcador "$M" start "$ESPERA_START" || falha "sem event=start em ${ESPERA_START} s"
carimbo t_start; T_START_S=$(date +%s)
until split_confirmado; do
  [ $(( $(date +%s) - T_START_S )) -lt "$ESPERA_SPLIT" ] \
    || falha "split NAO confirmado nos logs em ${ESPERA_SPLIT} s do start (sem KILL: seria repeticao do G-5 original, nao vale)"
  kill -0 "$P" 2>/dev/null || falha "injetor terminou antes de o split ser confirmado"
  sleep 2
done
carimbo t_split_confirmado; ok "split real confirmado nos logs ANTES do KILL (isn1/isn2: vista de 2; isn3: vista de 1 e DEGRADED_MODE)"
T_START_N=$(cron t_start)   # ns: a granularidade de date +%s deixaria o KILL sair ate 1 s antes de T_KILL
while [ $(( ($(ns) - T_START_N) / 1000000000 )) -lt "$T_KILL" ]; do sleep 0.5; done
kill -0 "$P" 2>/dev/null || falha "injetor terminou antes do KILL (janela de ${DUR} s esgotada?)"
{ echo "=== antes do kill -KILL $(date -Is)"; T "$PID3" tc qdisc show dev eth0
  echo "-- contadores por filtro"; cont_filtros_pid "$PID3" || true
  echo "-- ps do injetor"; ps -o pid,ppid,stat,cmd --ppid "$P" 2>/dev/null || true
  echo "-- list-timers (dead-man armado)"; systemctl list-timers --all 2>&1 | grep tcc-heal || echo "(nenhum)"; } >> "$OUT/estado.txt" 2>&1
tem_clsact || falha "clsact ausente ANTES do KILL (injecao nao ativa)"
SPID=$(pgrep -P "$P" 2>/dev/null | tr '\n' ' ' || true); echo "$SPID" > "$OUT/filhos.txt"
KILL_ISO=$(date -Iseconds); carimbo t_kill; echo "t_kill_ns=$(cron t_kill) sinal=KILL" >> "$OUT/estado.txt"
kill -KILL "$P" 2>/dev/null || echo "aviso: KILL nao entregue (injetor ja havia terminado)" | tee -a "$OUT/estado.txt"
set +e; wait "$P"; RC=$?; set -e; P=
echo "rc_injetor=$RC" | tee -a "$OUT/estado.txt"
[ "$RC" = 137 ] && ok "injetor saiu com rc=137 (128+SIGKILL)" || falha "rc do injetor=$RC (esperado 137)"
# o corte tem de PERSISTIR depois do KILL (nenhum trap captura SIGKILL): clsact presente, sem end, vistas ainda divididas
T_P0=$(date +%s); : > "$OUT/persistencia.txt"
while [ $(( $(date +%s) - T_P0 )) -lt "$PERSISTE" ]; do
  tem_clsact || falha "clsact SUMIU $(( $(date +%s) - T_P0 )) s apos o KILL (o corte nao persistiu; cura espontanea?)"
  ! grep -q 'event=end' "$M" || falha "marcador end apareceu apos o KILL"
  echo "$(ns) clsact presente, sem end" >> "$OUT/persistencia.txt"; sleep 2
done
sem_merge_desde "$KILL_ISO" || falha "MergeView apareceu nos logs desde o KILL (vista nao ficou dividida)"
cp "$OUT/.qdisc" "$OUT/qdisc-apos-kill.txt"; carimbo t_persistencia_ok
ok "corte PERSISTIU por >= ${PERSISTE} s apos o KILL (clsact presente, sem marcador end, sem MergeView)"
[ "$(grep -c 'event=end' "$M")" = 0 ] && ok "sem marcador end (esperado)" || falha "marcador end presente"

if [ "$MODO" = A ]; then
  # ---- A: cura manual depois do split real
  carimbo t_heal; T_HEAL_S=$(date +%s)
  set +e; inj --heal --minority isn3 > "$OUT/heal.log" 2>&1; HRC=$?; set -e
  [ "$HRC" = 0 ] && ok "--heal rc=0" || falha "--heal rc=$HRC"
  tem_clsact && falha "clsact RESIDUAL apos --heal" || ok "sem clsact apos --heal"
  cp "$OUT/.qdisc" "$OUT/qdisc-pos-heal.txt"
  espera_merge "$T_HEAL_S" || falha "MergeView de 3 membros nao apareceu nos 3 logs em ${LIM} s do heal"
  carimbo t_mergeview; ok "MergeView de 3 membros nos 3 logs apos o heal"
  espera_saudavel "$LIM" "$T_HEAL_S" "t_heal" | tee -a "$R" || falha "cluster nao voltou a size=3 HEALTHY em ${LIM} s"
  carimbo t_saudavel
  desarmar; carimbo t_desarmado
  sem_timers | tee -a "$R" || falha "ha timer tcc-heal depois de desarmar"
  ok "dead-man desarmado SO depois de o cluster estar saudavel"
else
  # ---- B: o dead-man e o unico agente de cura
  PRAZO=$(( DUR + MARGEM )); LIMITE_B=$(( PRAZO + TOL + FOLGA ))
  [ "$(grep -c -- '--heal' "$OUT/chamadas-injetor.txt")" = 0 ] || falha "houve --heal manual antes do disparo (bloco invalido)"
  T_FIRE=
  while :; do
    agora=$(ns)
    if ! tem_clsact; then T_FIRE=$agora; break; fi
    [ $(( (agora - T1N) / 1000000000 )) -lt "$LIMITE_B" ] || break
    sleep 1
  done
  [ -n "$T_FIRE" ] || falha "dead-man NAO disparou em ${LIMITE_B} s da armacao (clsact ainda presente)"
  echo "t_fire=$T_FIRE" >> "$OUT/cronologia.txt"; T_FIRE_S=$(date +%s)
  D0=$(( (T_FIRE - T0N) / 1000000 )); D1=$(( (T_FIRE - T1N) / 1000000 ))
  echo "delta_desde_antes_de_armar_ms=$D0 delta_desde_depois_de_armar_ms=$D1 prazo_s=$PRAZO tolerancia_s=$TOL" | tee "$OUT/tempos.txt"
  [ "$D0" -ge $(( PRAZO * 1000 )) ] && ok "disparo nao antes de ${PRAZO} s (delta=${D0} ms desde antes de armar)" || falha "disparo ANTES de ${PRAZO} s (delta=${D0} ms)"
  [ "$D1" -le $(( (PRAZO + TOL) * 1000 )) ] && ok "disparo ate ${PRAZO}+${TOL} s (delta=${D1} ms desde depois de armar)" \
    || falha "disparo DEPOIS de ${PRAZO}+${TOL} s (delta=${D1} ms; ver AccuracySec em unit-armada.txt)"
  [ "$(grep -c -- '--heal' "$OUT/chamadas-injetor.txt")" = 0 ] && ok "nenhum --heal manual registrado: o dead-man foi o UNICO agente de cura" \
    || falha "houve --heal manual (bloco invalido)"
  sleep 2
  { sudo journalctl -u "${UNIT}.service" --no-pager -o short-iso 2>&1 || true; } > "$OUT/journal-unit.txt"
  { echo "--- is-failed: $(systemctl is-failed "${UNIT}.service" 2>&1 || true)"
    echo "--- list-timers"; systemctl list-timers --all 2>&1 | grep tcc-heal || echo "(nenhum)"; } > "$OUT/estado-systemd.txt"
  cat "$OUT/journal-unit.txt" "$OUT/estado-systemd.txt"
  grep -q 'Heal: removendo' "$OUT/journal-unit.txt" && ok "journal: a unit executou o --heal do injetor de particao" || falha "journal sem 'Heal: removendo'"
  grep -Eq 'Deactivated successfully|Succeeded' "$OUT/journal-unit.txt" && ok "journal: unit encerrou com sucesso (rc 0)" || falha "journal sem 'Deactivated successfully'/'Succeeded'"
  ! grep -q 'Failed with result' "$OUT/journal-unit.txt" && ok "journal sem 'Failed with result'" || falha "journal registra falha da unit"
  ! grep -q '^--- is-failed: failed' "$OUT/estado-systemd.txt" && ok "unit nao esta failed" || falha "unit em estado failed"
  grep -q '(nenhum)' "$OUT/estado-systemd.txt" && ok "0 timers tcc-heal ao fim (sem desarmar)" || falha "ha timer tcc-heal armado ao fim"
  cp "$OUT/.qdisc" "$OUT/qdisc-no-disparo.txt"
  espera_merge "$T_FIRE_S" || falha "MergeView de 3 membros nao apareceu nos 3 logs em ${LIM} s do disparo"
  carimbo t_mergeview; ok "MergeView de 3 membros nos 3 logs apos o disparo"
  espera_saudavel "$LIM" "$T_FIRE_S" "t_fire" | tee -a "$R" || falha "cluster nao voltou a size=3 HEALTHY em ${LIM} s"
  carimbo t_saudavel
fi
# ordem temporal declarada (carimbos em ns)
if [ "$MODO" = A ]; then SEQ="t_start t_split_confirmado t_kill t_persistencia_ok t_heal t_mergeview t_saudavel t_desarmado"
else SEQ="t_start t_split_confirmado t_kill t_persistencia_ok t_fire t_mergeview t_saudavel"; fi
prev=0; okord=1; for k in $SEQ; do v=$(cron "$k"); [ -n "$v" ] && [ "$v" -ge "$prev" ] || okord=0; prev=${v:-0}; done
[ "$okord" = 1 ] && ok "cronologia coerente: $SEQ" || falha "cronologia fora de ordem ($SEQ)"
# orfaos do injetor (sleep da janela) terminam sozinhos; espera para nao deixar processo solto
for c in $(cat "$OUT/filhos.txt"); do w=0; while kill -0 "$c" 2>/dev/null && [ "$w" -lt 60 ]; do sleep 1; w=$((w+1)); done; done
preflight "$OUT/saude-depois.txt" | tee -a "$OUT/estado-final.txt"
for n in isn1 isn2 isn3; do $PODMAN logs --since "$SINCE" "$n" > "$OUT/log-completo-$n.txt" 2>&1 || true
  grep -iE 'view|ISPN000094|ISPN000093|merge|partition|DEGRADED' "$OUT/log-completo-$n.txt" > "$OUT/log-vista-$n.txt" || true; done
if cat "$OUT"/log-completo-isn*.txt | grep -Eq 'ISPN000524|ISPN100014|ISPN000638'; then
  aviso "timeout de resolucao de conflito nos logs (ISPN000524/ISPN100014/ISPN000638): ler os brutos"
else ok "sem ISPN000524/ISPN100014/ISPN000638 nos logs"; fi
rm -f "$OUT/.qdisc"; LIMPO=1
echo "G-5b-$MODO concluido: ver $R"
