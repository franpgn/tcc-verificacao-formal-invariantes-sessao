#!/usr/bin/env bash
# B-36 / R.2: roda o TLC sobre MCRelogio.tla (variante "max na entrega", Q-5) verificando UMA propriedade
# por vez. DERIVADO de spec/run-tlc-propriedades.sh @03ebce7 (cabecalho, parametros, parsing do resumo e
# classificacao VIOLADA/SEM_VIOLACAO/... sao os mesmos; o script auditado nao foi editado). Diferencas:
# modulo MCRelogio, SPEC = SpecRelogio | SpecRelogioSemeada, saida em runs/tlc/relogio-*, propriedade
# extra RelogioDomina (INVARIANT), e a opcao MUTACAO.
# SessionStore.tla / MCSession.tla / MCRelogio.tla NAO sao alterados; a mutacao opera numa COPIA em
# diretorio temporario (docs/decisoes-senior-2026-10-16.md SS1.4, controles 2 e 3).
#
# Uso (a partir de qualquer diretorio):
#   spec/run-tlc-relogio.sh MODO FALHA CARD [PROP ...]
#     MODO  semeado | livre     (semeado = SpecRelogioSemeada; livre = SpecRelogio)
#     FALHA sem | queda | particao   (restricao SemFalha | SoQueda | SoParticao)
#     CARD  r2 | r3
#     PROP  default: I1P I1F I4F I2Fb RelogioDomina   (RelogioDomina e I2Fb sao INVARIANTS; outras PROPERTY;
#           qualquer outra formula de MCSession.tla tambem vale, ex.: I2Fa I1M I4M I4Mp I5 I6)
#
# Variaveis de ambiente: as de run-tlc-propriedades.sh (TLA_TOOLS, JAVA, TIMEOUT_SECS, WORKERS, XMX, MAXCANAL,
# MAXRELOGIO, MAXEVENTOSFALHA, LIMITE, SIMULATE, DEPTH, SEM_SIMETRIA, COVERAGE, TAG, OUTDIR) mais:
#   MUTACAO     se definido (qualquer valor), roda numa copia de spec/ em que a linha de relogio da entrega
#               passa de "Max(@, TsMsg(m))" para "@" (relogio inalterado = modelo original). A saida vai
#               para runs/tlc/relogio-<modo>-<falha>-mut[-TAG]. Controle negativo/de regressao.
#   OUTDIR      default runs/tlc/relogio-<modo>-<falha>[-mut][-TAG][-sim]
set -uo pipefail

MODO="${1:-}"; FALHA="${2:-}"; CARD="${3:-}"; shift 3 2>/dev/null || true
PROPS=("$@"); [[ ${#PROPS[@]} -eq 0 ]] && PROPS=(I1P I1F I4F I2Fb RelogioDomina)

case "$MODO"  in semeado) SPEC=SpecRelogioSemeada ;; livre) SPEC=SpecRelogio ;;
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

SUF="${MUTACAO:+-mut}${TAG:+-$TAG}"; [[ -n "${SIMULATE:-}" ]] && SUF="${SUF}-sim"
BASE="relogio-${MODO}-${FALHA}${SUF}"
OUTDIR="${OUTDIR:-$AQUI/../runs/tlc/$BASE}"
mkdir -p "$OUTDIR"
RESUMO="$OUTDIR/resumo-${CARD}.tsv"
echo "Config: ${MUTACAO:+MUTACAO(relogio inalterado) }modo=$MODO falha=$FALHA card=$CARD REPLICAS=$REPS MaxCanal=$MAXCANAL MaxRelogio=$MAXRELOGIO MaxEventosFalha=$MAXEVENTOSFALHA timebox=${TIMEOUT_SECS}s workers=$WORKERS ${SIMULATE:+simulate=$SIMULATE}"

SYMLINE="SYMMETRY Simetria
"; [[ -n "${SEM_SIMETRIA:-}" ]] && SYMLINE=""
COVOPT=(); [[ -n "${COVERAGE:-}" ]] && COVOPT=(-coverage "$COVERAGE")
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

SPECDIR="$AQUI"
if [[ -n "${MUTACAO:-}" ]]; then
  SPECDIR="$(mktemp -d "${TMPDIR:-/tmp}/tlc-mut-XXXXXX")"
  cp "$AQUI/SessionStore.tla" "$AQUI/MCSession.tla" "$SPECDIR/"
  sed -E "s/(relogio' = \[relogio EXCEPT !\[r\] = )Max\(@, TsMsg\(m\)\)\]$/\1@]/" "$AQUI/MCRelogio.tla" > "$SPECDIR/MCRelogio.tla"
  grep -qE "^ +[/][\] relogio' = \[relogio EXCEPT !\[r\] = @\]$" "$SPECDIR/MCRelogio.tla" \
    || { echo "mutacao nao aplicada" >&2; exit 5; }
  echo "Mutacao: $(diff <(tr -d '\r' < "$AQUI/MCRelogio.tla") <(tr -d '\r' < "$SPECDIR/MCRelogio.tla") | grep -c '^[<>]') linhas diferem (esperado 2)"
  trap 'rm -rf "$SPECDIR"' EXIT
fi
cd "$SPECDIR"
for P in "${PROPS[@]}"; do
  if [[ "$P" == I2Fb || "$P" == RelogioDomina ]]; then SEC="INVARIANTS"; else SEC="PROPERTY"; fi
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
      -config "$CFG" "${MODOTLC[@]}" "${COVOPT[@]}" -metadir "$META" -cleanup MCRelogio.tla > "$LOG" 2>&1
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

