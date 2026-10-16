#!/usr/bin/env bash
# B-32: roda o TLC sobre MCSession.tla verificando, UMA POR VEZ, as propriedades
# operacionais por transicao (I1F, I4F, I2Fa, I2Fb, I1M, I4M, mais os achados
# extras I1P e MesclarPerdeChave) sob um dos regimes de falha da matriz do plano
# (docs/plano-b29-particao-real-e-contraexemplos-2026-10-09.md SS5.2-5.3).
# SessionStore.tla NAO e' alterado; tudo vive em MCSession.tla e nos .cfg gerados.
#
# Uso (a partir de qualquer diretorio):
#   spec/run-tlc-propriedades.sh MODO FALHA CARD [PROP ...]
#     MODO  semeado | livre     (semeado = SpecSemeada, Login desabilitado;
#                                livre   = Spec, Login habilitado)
#     FALHA sem | queda | particao   (restricao SemFalha | SoQueda | SoParticao)
#     CARD  r2 | r3             (2 ou 3 replicas; IDS={u1}, SIDS={s1,s2})
#     PROP  default: I1F I4F I2Fa I2Fb I1M I4M
#
# Variaveis de ambiente (todas opcionais):
#   TLA_TOOLS   caminho do tla2tools.jar (default ./tla2tools.jar; NAO esta no repo)
#   JAVA        executavel java (default: java do PATH)
#   TIMEOUT_SECS  timebox por propriedade (default 600)
#   WORKERS     threads do TLC (default 4)       XMX  heap (default 3g)
#   MAXCANAL (3)  MAXRELOGIO (5)  MAXEVENTOSFALHA (2)  LIMITE (2)
#   SIMULATE    se definido (ex.: 20000), usa `-simulate num=$SIMULATE -depth ${DEPTH:-80}`
#               em vez de busca exaustiva (serve para ACHAR traco, nunca para afirmar ausencia)
#   SEM_SIMETRIA se definido (qualquer valor), omite a linha `SYMMETRY Simetria` do .cfg (T-A/A.3:
#               controle sem reducao por simetria; recomenda-se WORKERS=1)
#   COVERAGE    se definido (minutos, ex.: 1), acrescenta `-coverage $COVERAGE` (T-A/A.4: contagem por acao)
#   TAG         sufixo do diretorio de saida (ex.: TAG=lim6 -> runs/tlc/prop-particao-lim6)
#   OUTDIR      default runs/tlc/prop-<falha> (semeado) | prop-livre-<falha> (livre);
#               com SIMULATE, sufixo -sim
set -uo pipefail

MODO="${1:-}"; FALHA="${2:-}"; CARD="${3:-}"; shift 3 2>/dev/null || true
PROPS=("$@"); [[ ${#PROPS[@]} -eq 0 ]] && PROPS=(I1F I4F I2Fa I2Fb I1M I4M)

case "$MODO"  in semeado) SPEC=SpecSemeada ;; livre) SPEC=Spec ;;
  *) echo "MODO deve ser semeado|livre" >&2; exit 2 ;; esac
case "$FALHA" in sem) RESTR=SemFalha ;; queda) RESTR=SoQueda ;; particao) RESTR=SoParticao ;;
  *) echo "FALHA deve ser sem|queda|particao" >&2; exit 2 ;; esac
case "$CARD"  in r2) REPS='{r1, r2}' ;; r3) REPS='{r1, r2, r3}' ;;
  *) echo "CARD deve ser r2|r3" >&2; exit 2 ;; esac

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TLA_TOOLS="${TLA_TOOLS:-./tla2tools.jar}"
JAVA="${JAVA:-java}"
TIMEOUT_SECS="${TIMEOUT_SECS:-600}"
WORKERS="${WORKERS:-4}"; XMX="${XMX:-3g}"
MAXCANAL="${MAXCANAL:-3}"; MAXRELOGIO="${MAXRELOGIO:-5}"
MAXEVENTOSFALHA="${MAXEVENTOSFALHA:-2}"; LIMITE="${LIMITE:-2}"
[[ -f "$TLA_TOOLS" ]] || { echo "tla2tools.jar nao encontrado em $TLA_TOOLS (defina TLA_TOOLS)" >&2; exit 4; }

SUF="${TAG:+-$TAG}"; [[ -n "${SIMULATE:-}" ]] && SUF="${SUF}-sim"
if [[ "$MODO" == semeado ]]; then BASE="prop-${FALHA}${SUF}"; else BASE="prop-livre-${FALHA}${SUF}"; fi
OUTDIR="${OUTDIR:-$AQUI/../runs/tlc/$BASE}"
mkdir -p "$OUTDIR"
RESUMO="$OUTDIR/resumo-${CARD}.tsv"
echo "Config: modo=$MODO falha=$FALHA card=$CARD REPLICAS=$REPS MaxCanal=$MAXCANAL MaxRelogio=$MAXRELOGIO MaxEventosFalha=$MAXEVENTOSFALHA timebox=${TIMEOUT_SECS}s workers=$WORKERS ${SIMULATE:+simulate=$SIMULATE}"

SYMLINE="SYMMETRY Simetria
"; [[ -n "${SEM_SIMETRIA:-}" ]] && SYMLINE=""
COVOPT=(); [[ -n "${COVERAGE:-}" ]] && COVOPT=(-coverage "$COVERAGE")
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

cd "$AQUI"
for P in "${PROPS[@]}"; do
  if [[ "$P" == I2Fb ]]; then SEC="INVARIANTS"; else SEC="PROPERTY"; fi
  CFG="$OUTDIR/${CARD}-${P}.cfg"; LOG="$OUTDIR/${CARD}-${P}.out"
  cat > "$CFG" <<CFGEOF
SPECIFICATION $SPEC
CONSTANTS
    IDS      = {u1}
    SIDS     = {s1, s2}
    REPLICAS = $REPS
    LIMITE   = $LIMITE
    MaxCanal   = $MAXCANAL
    MaxRelogio = $MAXRELOGIO
    MaxEventosFalha = $MAXEVENTOSFALHA
${SYMLINE}CONSTRAINT
    LimiteCanal
    LimiteRelogio
    LimiteEventosFalha
    $RESTR
$SEC
    $P
CFGEOF
  MODOTLC=(-workers "$WORKERS")
  [[ -n "${SIMULATE:-}" ]] && MODOTLC=(-simulate "num=$SIMULATE" -depth "${DEPTH:-80}")
  META="$(mktemp -d "${TMPDIR:-/tmp}/tlc-meta-XXXXXX")"   # metadir proprio: evita colisao entre execucoes paralelas
  # T-A/A.5: o TLC extrai TLC.tla/Naturals.tla/... para java.io.tmpdir; execucoes simultaneas
  # colidiam ali (ERRO rc=150 "Module-Table lookup failure"). Diretorio temporario proprio por execucao.
  JTMP="$(mktemp -d "${TMPDIR:-/tmp}/tlc-jtmp-XXXXXX")"
  T0=$SECONDS
  timeout "${TIMEOUT_SECS}s" "$JAVA" -Xmx"$XMX" -XX:+UseParallelGC -Djava.io.tmpdir="$(winpath "$JTMP")" -cp "$TLA_TOOLS" tlc2.TLC \
      -config "$CFG" "${MODOTLC[@]}" "${COVOPT[@]}" -metadir "$META" -cleanup MCSession.tla > "$LOG" 2>&1
  RC=$?; DT=$((SECONDS - T0)); rm -rf "$META" "$JTMP"
  SEMVIRG="$(tr -d ',' < "$LOG")"   # Progress(...) imprime numeros com separador de milhar
  GERADOS=$(grep -oE '[0-9]+ states generated' <<<"$SEMVIRG" | tail -1 | grep -oE '^[0-9]+')
  DIST=$(grep -oE '[0-9]+ distinct states found' <<<"$SEMVIRG" | tail -1 | grep -oE '^[0-9]+')
  if grep -qE "is violated|Invariant .* is violated" "$LOG"; then
    N=$(grep -cE '^State [0-9]+:' "$LOG"); RES="VIOLADA"; DET="traco_de_${N}_estados"
  elif [[ -n "${SIMULATE:-}" && $RC -eq 0 ]]; then
    RES="NAO_ENCONTRADA"; DET="simulacao_sem_prova"
  elif grep -q "No error has been found" "$LOG" && [[ -z "${SIMULATE:-}" ]]; then
    RES="SEM_VIOLACAO"; DET="exaustivo"
  elif [[ $RC -eq 124 ]]; then
    RES="SEM_VIOLACAO_ATE_AQUI"; DET="limite_de_tempo_${TIMEOUT_SECS}s"
  else
    RES="ERRO"; DET="rc=$RC"   # rc validos: 0 (sem erro), 12 (violacao), 124 (timeout); qualquer outro e ERRO
  fi
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$MODO" "$FALHA" "$CARD" "$P" "$RES" "$DET" "${GERADOS:-?}" "${DIST:-?}" "${DT}s" | tee -a "$RESUMO"
done
