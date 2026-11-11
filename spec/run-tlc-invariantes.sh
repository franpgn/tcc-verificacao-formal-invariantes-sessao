#!/usr/bin/env bash
# Roda o TLC sobre MCSession.tla (harness de SessionStore.tla) verificando
# CADA invariante isoladamente, em uma das cardinalidades da Tabela 1 (T21),
# com reducao por simetria, limite de canal e limite de relogio logico.
#
# Motivacao: o TLC para no PRIMEIRO invariante violado. Para obter o veredito
# individual de cada invariante (I1..I6, TypeOK) e preciso verifica-los um a um.
#
# Uso:
#   ./run-tlc-invariantes.sh small   MaxCanal MaxRelogio MaxEventosFalha  # 2 ids, 3 sids, 2 replicas
#   ./run-tlc-invariantes.sh medium  MaxCanal MaxRelogio MaxEventosFalha  # 3 ids, 3 sids, 2 replicas
#   ./run-tlc-invariantes.sh full    MaxCanal MaxRelogio MaxEventosFalha  # 3 ids, 3 sids, 3 replicas
#   ./run-tlc-invariantes.sh tiny    MaxCanal MaxRelogio MaxEventosFalha  # 1 id,  2 sids, 2 replicas (fecha exaustivo)
#
# MaxCanal default = 2 (suficiente para expor reordenacao em I5/I6). Valores
# maiores explodem o espaco dos invariantes que sempre valem (nao fecham).
# MaxRelogio default = 4 (teto do relogio logico de Lamport por replica).
# MaxEventosFalha default = 1 (B-22, F1 -- teto do total de eventos Cair por
#   comportamento; ver docs/plano-b22-falhas-explicitas-2026-10-03.md SS4.2).
#
# TIMEOUT_SECS (variavel de ambiente, opcional; default vazio = sem timeout):
#   se definida, cada chamada do TLC e envolvida em `timeout "${TIMEOUT_SECS}s"`.
#   Uso: TIMEOUT_SECS=1200 ./run-tlc-invariantes.sh medium 2 4 1
#
# Requisitos: tla2tools.jar em ./ (ou $TLA_TOOLS) e Java 11+.
set -uo pipefail

SIZE="${1:-small}"
MAXCANAL="${2:-2}"
MAXRELOGIO="${3:-4}"
MAXEVENTOSFALHA="${4:-1}"
TLA_TOOLS="${TLA_TOOLS:-./tla2tools.jar}"
TIMEOUT_SECS="${TIMEOUT_SECS:-}"

case "$SIZE" in
  tiny)   IDS='{u1}';        SIDS='{s1, s2}';     REPS='{r1, r2}';     LIM=2 ;;
  small)  IDS='{u1, u2}';    SIDS='{s1, s2, s3}'; REPS='{r1, r2}';     LIM=3 ;;
  medium) IDS='{u1, u2, u3}'; SIDS='{s1, s2, s3}'; REPS='{r1, r2}';     LIM=3 ;;
  full)   IDS='{u1, u2, u3}'; SIDS='{s1, s2, s3}'; REPS='{r1, r2, r3}'; LIM=3 ;;
  *) echo "Uso: $0 {tiny|small|medium|full} [MaxCanal] [MaxRelogio] [MaxEventosFalha]" >&2; exit 2 ;;
esac

[[ -f "$TLA_TOOLS" ]] || { echo "tla2tools.jar nao encontrado em $TLA_TOOLS" >&2; exit 4; }

OUTDIR="../runs/tlc/inv-${SIZE}"
mkdir -p "$OUTDIR"
echo "Config: $SIZE  (IDS=$IDS SIDS=$SIDS REPLICAS=$REPS LIMITE=$LIM MaxCanal=$MAXCANAL MaxRelogio=$MAXRELOGIO MaxEventosFalha=$MAXEVENTOSFALHA)"
if [[ -n "$TIMEOUT_SECS" ]]; then
  echo "Timebox: ${TIMEOUT_SECS}s por invariante"
fi
echo "-------------------------------------------------------------------------"

for INV in TypeOK I1 I2 I3 I4 I5 I6; do
  CFG="$OUTDIR/${SIZE}-${INV}.cfg"
  cat > "$CFG" <<EOF
SPECIFICATION Spec
CONSTANTS
    IDS      = $IDS
    SIDS     = $SIDS
    REPLICAS = $REPS
    LIMITE   = $LIM
    MaxCanal   = $MAXCANAL
    MaxRelogio = $MAXRELOGIO
    MaxEventosFalha = $MAXEVENTOSFALHA
SYMMETRY Simetria
CONSTRAINT
    LimiteCanal
    LimiteRelogio
    LimiteEventosFalha
INVARIANTS
    $INV
EOF
  LOG="$OUTDIR/${SIZE}-${INV}.out"
  if [[ -n "$TIMEOUT_SECS" ]]; then
    timeout "${TIMEOUT_SECS}s" java -XX:+UseParallelGC -cp "$TLA_TOOLS" tlc2.TLC \
         -config "$CFG" -workers auto -cleanup MCSession.tla > "$LOG" 2>&1
  else
    java -XX:+UseParallelGC -cp "$TLA_TOOLS" tlc2.TLC \
         -config "$CFG" -workers auto -cleanup MCSession.tla > "$LOG" 2>&1
  fi
  if grep -q "is violated" "$LOG"; then
    DEPTH=$(grep -cE '^State [0-9]+:' "$LOG")
    printf "  %-7s VIOLADO   (contraexemplo com %s estados)\n" "$INV" "$DEPTH"
  elif grep -q "No error has been found" "$LOG"; then
    N=$(grep -oE '[0-9]+ distinct states found' "$LOG" | grep -oE '^[0-9]+')
    printf "  %-7s OK exaustivo (%s estados distintos)\n" "$INV" "${N:-?}"
  else
    PROGRESS=$(grep -E "^Progress\(" "$LOG" | tail -1)
    printf "  %-7s inconclusivo (ver %s)\n" "$INV" "$LOG"
    [[ -n "$PROGRESS" ]] && printf "          ultimo progresso: %s\n" "$PROGRESS"
  fi
done
