#!/usr/bin/env bash
# Roda o TLC sobre MCSession.tla (harness de SessionStore.tla) verificando
# CADA invariante isoladamente, em uma das cardinalidades da Tabela 1 (T21),
# com reducao por simetria e limite de canal.
#
# Motivacao: o TLC para no PRIMEIRO invariante violado. Para obter o veredito
# individual de cada invariante (I1..I6, TypeOK) e preciso verifica-los um a um.
#
# Uso:
#   ./run-tlc-invariantes.sh small   MaxCanal   # 2 ids, 3 sids, 2 replicas
#   ./run-tlc-invariantes.sh medium  MaxCanal   # 3 ids, 3 sids, 2 replicas
#   ./run-tlc-invariantes.sh full    MaxCanal   # 3 ids, 3 sids, 3 replicas
#   ./run-tlc-invariantes.sh tiny    MaxCanal   # 1 id,  2 sids, 2 replicas (fecha exaustivo)
#
# MaxCanal default = 2 (suficiente para expor reordenacao em I5/I6). Valores
# maiores explodem o espaco dos invariantes que sempre valem (nao fecham).
#
# Requisitos: tla2tools.jar em ./ (ou $TLA_TOOLS) e Java 11+.
set -uo pipefail

SIZE="${1:-small}"
MAXCANAL="${2:-2}"
TLA_TOOLS="${TLA_TOOLS:-./tla2tools.jar}"

case "$SIZE" in
  tiny)   IDS='{u1}';        SIDS='{s1, s2}';     REPS='{r1, r2}';     LIM=2 ;;
  small)  IDS='{u1, u2}';    SIDS='{s1, s2, s3}'; REPS='{r1, r2}';     LIM=3 ;;
  medium) IDS='{u1, u2, u3}'; SIDS='{s1, s2, s3}'; REPS='{r1, r2}';     LIM=3 ;;
  full)   IDS='{u1, u2, u3}'; SIDS='{s1, s2, s3}'; REPS='{r1, r2, r3}'; LIM=3 ;;
  *) echo "Uso: $0 {tiny|small|medium|full} [MaxCanal]" >&2; exit 2 ;;
esac

[[ -f "$TLA_TOOLS" ]] || { echo "tla2tools.jar nao encontrado em $TLA_TOOLS" >&2; exit 4; }

OUTDIR="../runs/tlc/inv-${SIZE}"
mkdir -p "$OUTDIR"
echo "Config: $SIZE  (IDS=$IDS SIDS=$SIDS REPLICAS=$REPS LIMITE=$LIM MaxCanal=$MAXCANAL)"
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
    MaxCanal = $MAXCANAL
SYMMETRY Simetria
CONSTRAINT LimiteCanal
INVARIANTS
    $INV
EOF
  LOG="$OUTDIR/${SIZE}-${INV}.out"
  java -XX:+UseParallelGC -cp "$TLA_TOOLS" tlc2.TLC \
       -config "$CFG" -workers auto -cleanup MCSession.tla > "$LOG" 2>&1
  if grep -q "is violated" "$LOG"; then
    DEPTH=$(grep -cE '^State [0-9]+:' "$LOG")
    printf "  %-7s VIOLADO   (contraexemplo com %s estados)\n" "$INV" "$DEPTH"
  elif grep -q "No error has been found" "$LOG"; then
    N=$(grep -oE '[0-9]+ distinct states found' "$LOG" | grep -oE '^[0-9]+')
    printf "  %-7s OK exaustivo (%s estados distintos)\n" "$INV" "${N:-?}"
  else
    printf "  %-7s inconclusivo (ver %s)\n" "$INV" "$LOG"
  fi
done
