#!/usr/bin/env bash
# B-35 / B-35.1: roda o TLC sobre MCPerda.tla (variante "perda de mensagens no canal", docs/decisoes-senior-2026-10-16.md
# SS2) verificando UMA propriedade por vez. DERIVADO de spec/run-tlc-relogio.sh @e53752a (cabecalho, parametros, parsing do
# resumo e classificacao VIOLADA/SEM_VIOLACAO/... sao os mesmos). Diferencas: modulo MCPerda; SPEC = SpecPerda{0,1}{Semeada,Livre};
# constante MaxPerdas (env MAXPERDAS, default 1); INVARIANT PerdasOK em TODO cfg (alem da propriedade); saida em
# runs/tlc/perda-*; defaults MaxCanal=2, MaxRelogio=3 (os da base medida); coluna extra no resumo com a cobertura D:T
# das acoes Perder* (so' se COVERAGE definido); opcao MUTACAO (controle dinamico).
# SessionStore.tla / MCSession.tla / MCPerda.tla NAO sao alterados; a mutacao opera numa COPIA em diretorio temporario.
#
# Uso (a partir de qualquer diretorio):
#   spec/run-tlc-perda.sh MODO SABOR FALHA CARD [PROP ...]
#     MODO   semeado | livre            (semeado = SpecPerda*Semeada; livre = SpecPerda*Livre)
#     SABOR  p0 | p1                    (p0 = PerderQualquer, limite superior; p1 = PerderNaVista)
#     FALHA  sem | queda | particao     (restricao SemFalha | SoQueda | SoParticao)
#     CARD   r2 | r3
#     PROP   default: I5 I6 I1P I1F I4F I2Fa I1M I4M I2Fb   (I5, I6 e I2Fb sao INVARIANTS; as demais PROPERTY)
#
# Variaveis de ambiente: TLA_TOOLS, JAVA, TIMEOUT_SECS (600), WORKERS (4), XMX (3g), MAXCANAL (2), MAXRELOGIO (3),
# MAXEVENTOSFALHA (2), LIMITE (2), MAXPERDAS (1), SIMULATE, DEPTH, SEM_SIMETRIA, COVERAGE, TAG, OUTDIR, mais:
#   MUTACAO   remover-nulo : copia de spec/ em que RemoverMsg deixa o canal inalterado (canal' = canal), mas o orcamento
#             ainda e' consumido. Controle dinamico: o controle positivo de I5 (P0, r2, sem falha) deve DEIXAR de violar.
#             Saida em runs/tlc/perda-...-mut[-TAG].
set -uo pipefail

MODO="${1:-}"; SABOR="${2:-}"; FALHA="${3:-}"; CARD="${4:-}"; shift 4 2>/dev/null || true
PROPS=("$@"); [[ ${#PROPS[@]} -eq 0 ]] && PROPS=(I5 I6 I1P I1F I4F I2Fa I1M I4M I2Fb)

case "$SABOR" in p0) PN=0 ;; p1) PN=1 ;; *) echo "SABOR deve ser p0|p1" >&2; exit 2 ;; esac
case "$MODO"  in semeado) SPEC="SpecPerda${PN}Semeada" ;; livre) SPEC="SpecPerda${PN}Livre" ;;
  *) echo "MODO deve ser semeado|livre" >&2; exit 2 ;; esac
case "$FALHA" in sem) RESTR=SemFalha ;; queda) RESTR=SoQueda ;; particao) RESTR=SoParticao ;;
  *) echo "FALHA deve ser sem|queda|particao" >&2; exit 2 ;; esac
case "$CARD"  in r2) REPS='{r1, r2}' ;; r3) REPS='{r1, r2, r3}' ;;
  *) echo "CARD deve ser r2|r3" >&2; exit 2 ;; esac
[[ -z "${MUTACAO:-}" || "$MUTACAO" == remover-nulo ]] || { echo "MUTACAO deve ser remover-nulo" >&2; exit 2; }

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TLA_TOOLS="${TLA_TOOLS:-./tla2tools.jar}"
JAVA="${JAVA:-java}"
TIMEOUT_SECS="${TIMEOUT_SECS:-600}"
WORKERS="${WORKERS:-4}"; XMX="${XMX:-3g}"
MAXCANAL="${MAXCANAL:-2}"; MAXRELOGIO="${MAXRELOGIO:-3}"
MAXEVENTOSFALHA="${MAXEVENTOSFALHA:-2}"; LIMITE="${LIMITE:-2}"; MAXPERDAS="${MAXPERDAS:-1}"
[[ -f "$TLA_TOOLS" ]] || { echo "tla2tools.jar nao encontrado em $TLA_TOOLS (defina TLA_TOOLS)" >&2; exit 4; }

SUF="${MUTACAO:+-mut}${TAG:+-$TAG}"; [[ -n "${SIMULATE:-}" ]] && SUF="${SUF}-sim"
BASE="perda-${MODO}-${SABOR}-${FALHA}-mp${MAXPERDAS}${SUF}"
OUTDIR="${OUTDIR:-$AQUI/../runs/tlc/$BASE}"
mkdir -p "$OUTDIR"
RESUMO="$OUTDIR/resumo-${CARD}.tsv"
echo "Config: ${MUTACAO:+MUTACAO($MUTACAO) }modo=$MODO sabor=$SABOR falha=$FALHA card=$CARD REPLICAS=$REPS MaxPerdas=$MAXPERDAS MaxCanal=$MAXCANAL MaxRelogio=$MAXRELOGIO MaxEventosFalha=$MAXEVENTOSFALHA LIMITE=$LIMITE timebox=${TIMEOUT_SECS}s workers=$WORKERS ${SIMULATE:+simulate=$SIMULATE}"

SYMLINE="SYMMETRY Simetria
"; [[ -n "${SEM_SIMETRIA:-}" ]] && SYMLINE=""
COVOPT=(); [[ -n "${COVERAGE:-}" ]] && COVOPT=(-coverage "$COVERAGE")
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

SPECDIR="$AQUI"
if [[ -n "${MUTACAO:-}" ]]; then
  SPECDIR="$(mktemp -d "${TMPDIR:-/tmp}/tlc-mut-XXXXXX")"
  cp "$AQUI/SessionStore.tla" "$AQUI/MCSession.tla" "$SPECDIR/"
  sed -E "s/^( *)canal' = \[canal EXCEPT !\[r\] = SubSeq\(@, 1, i-1\) \\o SubSeq\(@, i\+1, Len\(@\)\)\]$/\1canal' = canal/" "$AQUI/MCPerda.tla" > "$SPECDIR/MCPerda.tla"
  grep -qE "^ +canal' = canal$" "$SPECDIR/MCPerda.tla" \
    || { echo "mutacao nao aplicada" >&2; exit 5; }
  echo "Mutacao: $(diff <(tr -d '\r' < "$AQUI/MCPerda.tla") <(tr -d '\r' < "$SPECDIR/MCPerda.tla") | grep -c '^[<>]') linhas diferem (esperado 2)"
  trap 'rm -rf "$SPECDIR"' EXIT
fi
cd "$SPECDIR"
for P in "${PROPS[@]}"; do
  if [[ "$P" == I5 || "$P" == I6 || "$P" == I2Fb ]]; then INVS="    PerdasOK
    $P"; PROPSEC=""; else INVS="    PerdasOK"; PROPSEC="PROPERTY
    $P
"; fi
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
    MaxPerdas  = $MAXPERDAS
${SYMLINE}CONSTRAINT
    LimiteCanal
    LimiteRelogio
    LimiteEventosFalha
    $RESTR
INVARIANTS
$INVS
${PROPSEC}
CFGEOF
  MODOTLC=(-workers "$WORKERS")
  [[ -n "${SIMULATE:-}" ]] && MODOTLC=(-simulate "num=$SIMULATE" -depth "${DEPTH:-80}")
  META="$(mktemp -d "${TMPDIR:-/tmp}/tlc-meta-XXXXXX")"   # metadir proprio
  JTMP="$(mktemp -d "${TMPDIR:-/tmp}/tlc-jtmp-XXXXXX")"    # java.io.tmpdir proprio (rc=150 em execucoes paralelas, T-A/A.5)
  T0=$SECONDS
  timeout "${TIMEOUT_SECS}s" "$JAVA" -Xmx"$XMX" -XX:+UseParallelGC -Djava.io.tmpdir="$(winpath "$JTMP")" -cp "$TLA_TOOLS" tlc2.TLC \
      -config "$CFG" "${MODOTLC[@]}" "${COVOPT[@]}" -metadir "$META" -cleanup MCPerda.tla > "$LOG" 2>&1
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
  COV="-"
  if [[ -n "${COVERAGE:-}" ]]; then   # ultima contagem D:T de cada acao Perder* (so' aparece se o TLC imprimiu cobertura)
    COV="$(grep -E '^<Perder(Qualquer|NaVista) ' "$LOG" | tail -1 | sed -E 's/^<(Perder[A-Za-z]+) .*>: ([0-9]+:[0-9]+).*/\1=\2/')"
    [[ -n "$COV" ]] || COV="sem_cobertura_impressa"
  fi
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$MODO" "$SABOR" "mp$MAXPERDAS" "$FALHA" "$CARD" "$P" "$RES" "$DET" "${GERADOS:-?}" "${DIST:-?}" "${DT}s" "$COV" | tee -a "$RESUMO"
done
