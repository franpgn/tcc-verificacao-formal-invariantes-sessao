#!/usr/bin/env bash
# B-45 (P-16, desempate de `ts`): roteiro RETOMAVEL da rodada TLC antes x depois.
#
# Le o plano declarativo spec/b45-plano.tsv (uma execucao do TLC por linha) e roda, em ordem, as
# linhas cujo id casa com o filtro (regex estendida; default: todas). Cada linha diz QUAL revisao do
# git da especificacao usar (ANTES = 20805b0, sem desempate; DEPOIS = commit do fix; FIFO = commit
# que introduziu spec/MCFifo.tla). Os .tla de cada revisao sao extraidos com `git show` para
# spec/states/b45/snap-<rev>/ (ignorado pelo git), entao editar spec/ durante a rodada nao afeta nada.
#
# Retomada (queda de energia, pausa):
#   - estado por execucao em runs/tlc/b45-estado.tsv: id, status (FEITO|EM_CURSO|PAUSADO), segundos
#     acumulados, rc, data. FEITO e' pulado.
#   - TLC sempre com `-checkpoint 10` e -metadir proprio (spec/states/b45/meta/<id>). Se a execucao
#     anterior ficou EM_CURSO/PAUSADO e existe checkpoint no metadir, retoma com `-recover`; senao
#     recomeca do zero (log anterior renomeado para .out.parcial-N).
#   - timebox por execucao (coluna timeout) conta o tempo ACUMULADO entre retomadas: a retomada
#     recebe so' o saldo. O trabalho entre o ultimo checkpoint e a queda conta no tempo e e' perdido
#     (o timebox efetivo fica menor, nunca maior).
#   - Pausa (janela de teste na VM): `touch runs/tlc/b45-PAUSAR`. A execucao corrente e' encerrada
#     (marcada PAUSADO) e o roteiro sai. Para retomar: `rm runs/tlc/b45-PAUSAR` e rodar de novo.
#
# Uso (de qualquer diretorio):
#   REV_DEPOIS=<sha> REV_FIFO=<sha> spec/run-tlc-b45.sh [FILTRO_REGEX]
#   (REV_* ficam gravadas em runs/tlc/b45-revs.tsv na primeira vez; nas retomadas sao lidas de la)
# Variaveis: TLA_TOOLS (jar), JAVA (default jdk-17.0.2), XMX (default 3g).
# Restricao de host (Gestor, 04/11): no maximo 4 workers; linhas com mais sao recusadas.
set -uo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/.." && pwd)"
PLANO="${B45_PLANO:-$AQUI/b45-plano.tsv}"
RUNS="${B45_RUNS:-$RAIZ/runs/tlc}"
ESTADO="$RUNS/b45-estado.tsv"
REVS="$RUNS/b45-revs.tsv"
PAUSAR="$RUNS/b45-PAUSAR"
WORK="$AQUI/states/b45"
FILTRO="${1:-.}"
TLA_TOOLS="${TLA_TOOLS:-$RAIZ/../_tools/tla2tools.jar}"
JAVA="${JAVA:-/c/Program Files/Java/jdk-17.0.2/bin/java.exe}"
XMX="${XMX:-3g}"
MAXWORKERS=4

[[ -f "$TLA_TOOLS" ]] || { echo "tla2tools.jar nao encontrado em $TLA_TOOLS" >&2; exit 4; }
[[ -f "$PLANO" ]] || { echo "plano nao encontrado: $PLANO" >&2; exit 4; }
mkdir -p "$RUNS" "$WORK"
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

# --- revisoes -------------------------------------------------------------------------------
REV_ANTES=20805b0
if [[ -f "$REVS" ]]; then
  # shellcheck disable=SC1090
  while IFS=$'\t' read -r k v; do
    case "$k" in DEPOIS) : "${REV_DEPOIS:=$v}" ;; FIFO) : "${REV_FIFO:=$v}" ;; esac
  done < "$REVS"
fi
: "${REV_DEPOIS:?defina REV_DEPOIS (commit do fix)}"
: "${REV_FIFO:?defina REV_FIFO (commit que introduziu spec/MCFifo.tla)}"
printf 'ANTES\t%s\nDEPOIS\t%s\nFIFO\t%s\n' "$REV_ANTES" "$REV_DEPOIS" "$REV_FIFO" > "$REVS"

resolve_rev() { case "$1" in ANTES) echo "$REV_ANTES" ;; DEPOIS) echo "$REV_DEPOIS" ;; FIFO) echo "$REV_FIFO" ;;
                 *) echo "rev desconhecida: $1" >&2; return 1 ;; esac; }

snapshot() {   # extrai os .tla da revisao; imprime o diretorio
  local rev; rev="$(git -C "$RAIZ" rev-parse --short=7 "$1")" || return 1
  local d="$WORK/snap-$rev"
  if [[ ! -f "$d/.ok" ]]; then
    mkdir -p "$d"
    for f in SessionStore.tla MCSession.tla MCFifo.tla; do
      git -C "$RAIZ" show "$rev:spec/$f" > "$d/$f" 2>/dev/null || rm -f "$d/$f"
    done
    ( cd "$d" && sha256sum ./*.tla ) | sed "s|^|$rev\t|" >> "$RUNS/b45-snapshots.tsv"
    touch "$d/.ok"
  fi
  echo "$d"
}

# --- estado -------------------------------------------------------------------------------
estado_get() { [[ -f "$ESTADO" ]] && awk -F'\t' -v id="$1" '$1==id {l=$0} END {print l}' "$ESTADO"; }
estado_set() {  # id status segundos rc
  local tmp="$ESTADO.tmp"
  { [[ -f "$ESTADO" ]] && awk -F'\t' -v id="$1" '$1!=id' "$ESTADO"
    printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$(date '+%Y-%m-%dT%H:%M:%S')"; } > "$tmp"
  mv "$tmp" "$ESTADO"
}

cardinalidade() {  # card -> IDS SIDS REPS
  case "$1" in
    tiny)   echo '{u1}|{s1, s2}|{r1, r2}' ;;
    small)  echo '{u1, u2}|{s1, s2, s3}|{r1, r2}' ;;
    medium) echo '{u1, u2, u3}|{s1, s2, s3}|{r1, r2}' ;;
    full)   echo '{u1, u2, u3}|{s1, s2, s3}|{r1, r2, r3}' ;;
    r2)     echo '{u1}|{s1, s2}|{r1, r2}' ;;
    r3)     echo '{u1}|{s1, s2}|{r1, r2, r3}' ;;
    *) return 1 ;;
  esac
}

classifica() {  # log rc -> RESULTADO<TAB>DETALHE<TAB>GERADOS<TAB>DISTINTOS<TAB>FILA
  local log="$1" rc="$2" sv g d q res det
  sv="$(tr -d ',' < "$log")"
  g=$(grep -oE '[0-9]+ states generated' <<<"$sv" | tail -1 | grep -oE '^[0-9]+')
  d=$(grep -oE '[0-9]+ distinct states found' <<<"$sv" | tail -1 | grep -oE '^[0-9]+')
  q=$(grep -oE '[0-9]+ states left on queue' <<<"$sv" | tail -1 | grep -oE '^[0-9]+')
  if grep -qE '^Error: .*(is violated|violated)' "$log"; then
    res=VIOLADA; det="traco_de_$(grep -cE '^State [0-9]+:' "$log")_estados"
  elif grep -q 'Model checking completed. No error has been found' "$log"; then
    res=FECHOU; det=exaustivo
  elif [[ "$rc" == 124 ]]; then
    res=SEM_VIOLACAO_ATE_AQUI; det=limite_de_tempo
  else
    res=ERRO; det="rc=$rc"
  fi
  printf '%s\t%s\t%s\t%s\t%s' "$res" "$det" "${g:-?}" "${d:-?}" "${q:-?}"
}

# --- laco principal -------------------------------------------------------------------------
LINHAS=()
while IFS= read -r l; do LINHAS+=("$l"); done < <(grep -vE '^(#|$)' "$PLANO")
for LINHA in "${LINHAS[@]}"; do
  IFS=$'\t' read -r ID GRUPO REVTOK MODULO SPEC CARD LIMITE CANAL RELOGIO EV RESTR PROP SIM WORKERS TIMEOUT <<<"$LINHA"
  [[ "$ID" =~ $FILTRO ]] || continue
  [[ -f "$PAUSAR" ]] && { echo "PAUSAR presente: saindo antes de $ID"; exit 3; }
  ANT="$(estado_get "$ID")"; ST="$(cut -f2 <<<"$ANT")"; PREV="$(cut -f3 <<<"$ANT")"; PREV="${PREV:-0}"
  [[ "$ST" == FEITO ]] && continue
  (( WORKERS <= MAXWORKERS )) || { echo "$ID: $WORKERS workers > $MAXWORKERS, recusado" >&2; continue; }
  REV="$(resolve_rev "$REVTOK")" || exit 2
  SNAP="$(snapshot "$REV")" || { echo "$ID: falha ao extrair $REV" >&2; exit 2; }
  [[ -f "$SNAP/$MODULO.tla" ]] || { echo "$ID: $MODULO.tla nao existe em $REV" >&2; exit 2; }
  IFS='|' read -r IDS SIDS REPS <<<"$(cardinalidade "$CARD")" || { echo "$ID: card $CARD?" >&2; exit 2; }
  OUTDIR="$RUNS/b45-$GRUPO"; mkdir -p "$OUTDIR"
  CFG="$OUTDIR/$ID.cfg"; LOG="$OUTDIR/$ID.out"; META="$WORK/meta/$ID"; JTMP="$WORK/jtmp/$ID"
  case "$PROP" in TypeOK|I1|I2|I3|I4|I5|I6|I2Fb) SEC=INVARIANTS ;; *) SEC=PROPERTY ;; esac
  SYMLINE=""; [[ "$SIM" == S ]] && SYMLINE="SYMMETRY Simetria"
  RESTRLINE=""; [[ "$RESTR" != - ]] && RESTRLINE="    $RESTR"
  cat > "$CFG" <<CFGEOF
SPECIFICATION $SPEC
CONSTANTS
    IDS      = $IDS
    SIDS     = $SIDS
    REPLICAS = $REPS
    LIMITE   = $LIMITE
    MaxCanal   = $CANAL
    MaxRelogio = $RELOGIO
    MaxEventosFalha = $EV
$SYMLINE
CONSTRAINT
    LimiteCanal
    LimiteRelogio
    LimiteEventosFalha
$RESTRLINE
$SEC
    $PROP
CFGEOF
  RESTANTE=$(( TIMEOUT - PREV ))
  if (( RESTANTE <= 0 )); then   # timebox ja gasto antes da queda
    RES="$(classifica "$LOG" 124)"
    estado_set "$ID" FEITO "$PREV" 124
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%ss\n' "$ID" "$REVTOK" "$REV" "$MODULO" "$SPEC" "$CARD" "$RESTR" "$PROP" "$SIM" "$WORKERS" "$RES" "$PREV" >> "$OUTDIR/resumo.tsv"
    continue
  fi
  # TLC grava em <metadir>/<carimbo>/; o checkpoint e' o .chkpt mais recente la dentro
  CHK="$(ls -1t "$META"/*/*.chkpt 2>/dev/null | head -1)"
  MODOMETA=(-metadir "$(winpath "$META")")
  if [[ -n "$ST" && -n "$CHK" ]]; then
    MODOMETA=(-recover "$(winpath "$(dirname "$CHK")")")
    echo "== $ID: retomando de $(dirname "$CHK") (saldo ${RESTANTE}s)" | tee -a "$LOG"
  else
    if [[ -f "$LOG" ]]; then n=1; while [[ -f "$LOG.parcial-$n" ]]; do n=$((n+1)); done; mv "$LOG" "$LOG.parcial-$n"; fi
    rm -rf "$META"; PREV=0; RESTANTE=$TIMEOUT
  fi
  mkdir -p "$META" "$JTMP"
  {
    printf 'id\t%s\nrev\t%s (%s)\nmodulo\t%s\nspec\t%s\nworkers\t%s\ntimeout\t%s\njava\t%s\ninicio\t%s\n' \
      "$ID" "$REV" "$REVTOK" "$MODULO" "$SPEC" "$WORKERS" "$TIMEOUT" "$JAVA" "$(date '+%Y-%m-%dT%H:%M:%S')"
    ( cd "$SNAP" && sha256sum ./*.tla )
  } >> "$OUTDIR/$ID.meta"
  echo "== $ID [$REVTOK $REV] $MODULO/$SPEC card=$CARD $RESTR $PROP sim=$SIM w=$WORKERS saldo=${RESTANTE}s"
  estado_set "$ID" EM_CURSO "$PREV" -
  T0=$SECONDS
  ( cd "$SNAP" && exec timeout "${RESTANTE}s" "$JAVA" -Xmx"$XMX" -XX:+UseParallelGC \
      -Djava.io.tmpdir="$(winpath "$JTMP")" -cp "$(winpath "$TLA_TOOLS")" tlc2.TLC \
      -config "$(winpath "$CFG")" -workers "$WORKERS" -checkpoint "${CHECKPOINT_MIN:-10}" "${MODOMETA[@]}" \
      "$MODULO.tla" ) >> "$LOG" 2>&1 &
  PID=$!; PAUSOU=0
  TICK=0
  while kill -0 "$PID" 2>/dev/null; do
    sleep 1; TICK=$((TICK + 1))
    (( TICK % 10 == 0 )) && estado_set "$ID" EM_CURSO "$(( PREV + SECONDS - T0 ))" -
    if [[ -f "$PAUSAR" && $PAUSOU -eq 0 ]]; then PAUSOU=1; kill -TERM "$PID" 2>/dev/null; fi
  done
  wait "$PID"; RC=$?
  DT=$(( PREV + SECONDS - T0 ))
  if (( PAUSOU )); then
    estado_set "$ID" PAUSADO "$DT" "$RC"; echo "== $ID pausado apos ${DT}s (rc=$RC); rode de novo para retomar"; exit 3
  fi
  RES="$(classifica "$LOG" "$RC")"
  if [[ "$(cut -f1 <<<"$RES")" == ERRO ]]; then
    estado_set "$ID" ERRO "$DT" "$RC"
  else
    estado_set "$ID" FEITO "$DT" "$RC"; rm -rf "$META"
  fi
  rm -rf "$JTMP"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%ss\n' "$ID" "$REVTOK" "$REV" "$MODULO" "$SPEC" "$CARD" "$RESTR" "$PROP" "$SIM" "$WORKERS" "$RES" "$DT" | tee -a "$OUTDIR/resumo.tsv"
done
echo "fim do filtro '$FILTRO'"
