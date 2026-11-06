#!/usr/bin/env bash
# Rodada TLC da variante MesclarJoin (spec/MCSessionJoin.tla) e do controle com o Mesclar publicado.
#
# Le o plano declarativo spec/join-plano.tsv (uma execucao do TLC por linha) e roda, em ordem, as linhas
# cujo id casa com o filtro (regex estendida; default: todas). Os .tla sao extraidos da revisao REV
# (default HEAD) com `git show` para spec/states/join/snap-<rev>/ (ignorado pelo git): editar spec/ durante
# a rodada nao muda nada, e o .meta de cada execucao guarda a revisao e o SHA-256 dos .tla usados.
#
# Estado em runs/tlc/join-estado.tsv (id, status FEITO|EM_CURSO|PAUSADO|ERRO, segundos, rc, data). FEITO e'
# pulado. Execucoes curtas: sem -recover; uma execucao interrompida recomeca do zero (o log anterior vira
# .out.parcial-N). Pausa: `touch runs/tlc/join-PAUSAR` encerra a execucao corrente (PAUSADO) e o roteiro sai.
#
# Coluna propriedade: uma ou varias separadas por virgula (ex.: I1F,I1MJ), checadas na MESMA exploracao do TLC
# (o grafo de estados nao depende da propriedade; o TLC para na primeira violacao).
#
# Uso (de qualquer diretorio):  spec/run-tlc-join.sh [FILTRO_REGEX]
# Variaveis: REV (default HEAD), TLA_TOOLS (jar), JAVA (default jdk-17.0.2), XMX (default 3g).
# Restricao de host (Gestor): no maximo 4 workers; linhas com mais sao recusadas.
set -uo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/.." && pwd)"
PLANO="${JOIN_PLANO:-$AQUI/join-plano.tsv}"
RUNS="${JOIN_RUNS:-$RAIZ/runs/tlc}"
ESTADO="$RUNS/join-estado.tsv"
PAUSAR="$RUNS/join-PAUSAR"
WORK="$AQUI/states/join"
FILTRO="${1:-.}"
TLA_TOOLS="${TLA_TOOLS:-$RAIZ/../_tools/tla2tools.jar}"
JAVA="${JAVA:-/c/Program Files/Java/jdk-17.0.2/bin/java.exe}"
XMX="${XMX:-3g}"
MAXWORKERS=4

[[ -f "$TLA_TOOLS" ]] || { echo "tla2tools.jar nao encontrado em $TLA_TOOLS" >&2; exit 4; }
[[ -f "$PLANO" ]] || { echo "plano nao encontrado: $PLANO" >&2; exit 4; }
mkdir -p "$RUNS" "$WORK"
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

REV="$(git -C "$RAIZ" rev-parse --short=7 "${REV:-HEAD}")" || exit 2
SNAP="$WORK/snap-$REV"
if [[ ! -f "$SNAP/.ok" ]]; then
  mkdir -p "$SNAP"
  for f in SessionStore.tla MCSession.tla MCSessionJoin.tla; do
    git -C "$RAIZ" show "$REV:spec/$f" > "$SNAP/$f" || { echo "falha ao extrair $f de $REV" >&2; exit 2; }
  done
  ( cd "$SNAP" && sha256sum ./*.tla ) | sed "s|^|$REV\t|" >> "$RUNS/join-snapshots.tsv"
  touch "$SNAP/.ok"
fi

estado_get() { [[ -f "$ESTADO" ]] && awk -F'\t' -v id="$1" '$1==id {l=$0} END {print l}' "$ESTADO"; }
estado_set() {  # id status segundos rc
  local tmp="$ESTADO.tmp"
  { [[ -f "$ESTADO" ]] && awk -F'\t' -v id="$1" '$1!=id' "$ESTADO"
    printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$(date '+%Y-%m-%dT%H:%M:%S')"; } > "$tmp"
  mv "$tmp" "$ESTADO"
}

replicas() { case "$1" in r2) echo '{r1, r2}' ;; r3) echo '{r1, r2, r3}' ;; *) return 1 ;; esac; }

classifica() {  # log rc -> RESULTADO<TAB>DETALHE<TAB>GERADOS<TAB>DISTINTOS<TAB>FILA<TAB>ACOES
  local log="$1" rc="$2" sv g d q res det acoes=-
  sv="$(tr -d ',' < "$log")"
  g=$(grep -oE '[0-9]+ states generated' <<<"$sv" | tail -1 | grep -oE '^[0-9]+')
  d=$(grep -oE '[0-9]+ distinct states found' <<<"$sv" | tail -1 | grep -oE '^[0-9]+')
  q=$(grep -oE '[0-9]+ states left on queue' <<<"$sv" | tail -1 | grep -oE '^[0-9]+')
  if grep -qE '^Error: .*(is violated|violated)' "$log"; then
    res=VIOLADA; det="traco_de_$(grep -cE '^State [0-9]+:' "$log")_estados"
    acoes="$(grep -oE '^State [0-9]+: <[A-Za-z]+' "$log" | sed -E 's/^State [0-9]+: <//' | paste -sd, -)"
  elif grep -q 'Model checking completed. No error has been found' "$log"; then
    res=FECHOU; det=exaustivo
  elif [[ "$rc" == 124 ]]; then
    res=SEM_VIOLACAO_ATE_AQUI; det=limite_de_tempo
  else
    res=ERRO; det="rc=$rc"
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s' "$res" "$det" "${g:-?}" "${d:-?}" "${q:-?}" "$acoes"
}

LINHAS=()
while IFS= read -r l; do LINHAS+=("$l"); done < <(grep -vE '^(#|$)' "$PLANO")
for LINHA in "${LINHAS[@]}"; do
  IFS=$'\t' read -r ID GRUPO MODULO SPEC CARD LIMITE CANAL RELOGIO EV RESTR PROP ORDEM SIM WORKERS TIMEOUT COV <<<"$LINHA"
  [[ "$ID" =~ $FILTRO ]] || continue
  [[ -f "$PAUSAR" ]] && { echo "PAUSAR presente: saindo antes de $ID"; exit 3; }
  ST="$(estado_get "$ID" | cut -f2)"
  [[ "$ST" == FEITO ]] && continue
  (( WORKERS <= MAXWORKERS )) || { echo "$ID: $WORKERS workers > $MAXWORKERS, recusado" >&2; continue; }
  [[ -f "$SNAP/$MODULO.tla" ]] || { echo "$ID: $MODULO.tla nao existe em $REV" >&2; exit 2; }
  REPS="$(replicas "$CARD")" || { echo "$ID: card $CARD?" >&2; exit 2; }
  OUTDIR="$RUNS/join-$GRUPO"; mkdir -p "$OUTDIR"
  CFG="$OUTDIR/$ID.cfg"; LOG="$OUTDIR/$ID.out"; META="$WORK/meta/$ID"; JTMP="$WORK/jtmp/$ID"
  ORDLINE=""; [[ "$ORDEM" != - ]] && ORDLINE="    OrdemFusao = \"$ORDEM\""
  SYMLINE=""; [[ "$SIM" == S ]] && SYMLINE="SYMMETRY Simetria"
  RESTRLINE=""; [[ "$RESTR" != - ]] && RESTRLINE="    $RESTR"
  cat > "$CFG" <<CFGEOF
SPECIFICATION $SPEC
CONSTANTS
    IDS      = {u1}
    SIDS     = {s1, s2}
    REPLICAS = $REPS
    LIMITE   = $LIMITE
    MaxCanal   = $CANAL
    MaxRelogio = $RELOGIO
    MaxEventosFalha = $EV
$ORDLINE
$SYMLINE
CONSTRAINT
    LimiteCanal
    LimiteRelogio
    LimiteEventosFalha
$RESTRLINE
PROPERTY
$(tr ',' '
' <<<"$PROP" | sed 's/^/    /')
CFGEOF
  if [[ -f "$LOG" ]]; then n=1; while [[ -f "$LOG.parcial-$n" ]]; do n=$((n+1)); done; mv "$LOG" "$LOG.parcial-$n"; fi
  rm -rf "$META" "$JTMP"; mkdir -p "$META" "$JTMP"
  COVOPT=(); [[ "$COV" != - ]] && COVOPT=(-coverage "$COV")
  {
    printf 'id\t%s\nrev\t%s\nmodulo\t%s\nspec\t%s\nordem\t%s\nworkers\t%s\ntimeout\t%s\ncoverage\t%s\njava\t%s\ninicio\t%s\n' \
      "$ID" "$REV" "$MODULO" "$SPEC" "$ORDEM" "$WORKERS" "$TIMEOUT" "$COV" "$JAVA" "$(date '+%Y-%m-%dT%H:%M:%S')"
    ( cd "$SNAP" && sha256sum ./*.tla )
  } > "$OUTDIR/$ID.meta"
  echo "== $ID [$REV] $MODULO/$SPEC card=$CARD $CANAL/$RELOGIO/$EV $RESTR $PROP ordem=$ORDEM sim=$SIM w=$WORKERS timeout=${TIMEOUT}s cov=$COV"
  estado_set "$ID" EM_CURSO 0 -
  T0=$SECONDS
  ( cd "$SNAP" && exec timeout "${TIMEOUT}s" "$JAVA" -Xmx"$XMX" -XX:+UseParallelGC \
      -Djava.io.tmpdir="$(winpath "$JTMP")" -cp "$(winpath "$TLA_TOOLS")" tlc2.TLC \
      -config "$(winpath "$CFG")" -workers "$WORKERS" -metadir "$(winpath "$META")" "${COVOPT[@]}" \
      "$MODULO.tla" ) > "$LOG" 2>&1 &
  PID=$!; PAUSOU=0
  while kill -0 "$PID" 2>/dev/null; do
    sleep 1
    if [[ -f "$PAUSAR" && $PAUSOU -eq 0 ]]; then PAUSOU=1; kill -TERM "$PID" 2>/dev/null; fi
  done
  wait "$PID"; RC=$?
  DT=$(( SECONDS - T0 ))
  rm -rf "$META" "$JTMP"
  if (( PAUSOU )); then estado_set "$ID" PAUSADO "$DT" "$RC"; echo "== $ID pausado apos ${DT}s (rc=$RC)"; exit 3; fi
  printf 'fim\t%s\nrc\t%s\nsegundos\t%s\n' "$(date '+%Y-%m-%dT%H:%M:%S')" "$RC" "$DT" >> "$OUTDIR/$ID.meta"
  RES="$(classifica "$LOG" "$RC")"
  if [[ "$(cut -f1 <<<"$RES")" == ERRO ]]; then estado_set "$ID" ERRO "$DT" "$RC"; else estado_set "$ID" FEITO "$DT" "$RC"; fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s/%s/%s\t%s\t%s\t%s\t%s\t%s\t%ss\n' "$ID" "$REV" "$MODULO" "$SPEC" "$CARD" "$RESTR" \
    "$CANAL" "$RELOGIO" "$EV" "$PROP" "$ORDEM" "$SIM" "$WORKERS" "$RES" "$DT" | tee -a "$OUTDIR/resumo.tsv"
done
echo "fim do filtro '$FILTRO'"
