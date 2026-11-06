#!/usr/bin/env bash
# Testes estruturais da variante MesclarJoin (spec/MCSessionJoin.tla). Sem TLC: conferem que a variante
# continua sendo "o modelo publicado com a fusao das sessoes trocada", e nada alem disso.
#
#   T1  SessionStore.tla e MCSession.tla sem diferenca em relacao a base (BASE, default fix/b45-desempate-ts).
#   T2  NextJoin tem os mesmos disjuntos de Next, na mesma ordem, com Mesclar trocado por MesclarJoin.
#   T3  A linha de identidades' de MesclarJoin e' a mesma de Mesclar (identidades ficam com a fusao atual).
#   T4  MesclarJoin deixa inalteradas as mesmas variaveis que Mesclar (canal, vivo, quedas, ladoDe, particoes).
#   T5  Toda propriedade de fusao de MCSession (I1M, I4M, I4Mp, I2Fa, MesclarPerdeChave) tem a versao J
#       sobre MesclarJoin, e nenhuma versao J cita Mesclar sozinho.
#   T6  (se TLA_TOOLS existir) o SANY analisa MCSessionJoin.tla sem erro.
#
# Uso: spec/test-mcsessionjoin.sh      Saida: PASS/FAIL por teste; rc = numero de FAIL.
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/.." && pwd)"
BASE="${BASE:-fix/b45-desempate-ts}"
SS="$AQUI/SessionStore.tla"; MC="$AQUI/MCSession.tla"; MJ="$AQUI/MCSessionJoin.tla"
TLA_TOOLS="${TLA_TOOLS:-$RAIZ/../_tools/tla2tools.jar}"
JAVA="${JAVA:-/c/Program Files/Java/jdk-17.0.2/bin/java.exe}"
FALHAS=0
ok()  { echo "PASS $1"; }
nok() { echo "FAIL $1: $2"; FALHAS=$((FALHAS + 1)); }

# Corpo de uma definicao "NOME ==": da linha que a abre ate a linha em branco, o inicio de outra definicao
# (X == ou X(a) ==) ou um comentario (* *). Sem comentarios \*, espacos normalizados, linhas vazias fora.
bloco() {
  awk -v n="$2" '
    on && ($0 ~ /^[[:space:]]*$/ || $0 ~ /^[A-Za-z_][A-Za-z0-9_]*(\([^)]*\))?[[:space:]]*==/ || $0 ~ /^\(\*/) { exit }
    $0 ~ ("^" n "[[:space:]]*==") { on = 1 }
    on { print }' "$1" \
  | sed -E 's/\\\*.*$//; s/[[:space:]]+/ /g; s/^ //; s/ $//' | grep -v '^$'
}
disjuntos() {  # bloco sem o "NOME ==" da primeira linha
  bloco "$1" "$2" | sed -E "1s/^$2 ?==//" | sed -E 's/^ //' | grep -v '^$'
}

# T1
if git -C "$RAIZ" diff --quiet "$BASE" -- spec/SessionStore.tla spec/MCSession.tla; then
  ok "T1 SessionStore.tla e MCSession.tla iguais a $BASE"
else
  nok T1 "SessionStore.tla ou MCSession.tla mudou em relacao a $BASE"
fi

# T2
A="$(disjuntos "$SS" Next)"
B="$(disjuntos "$MJ" NextJoin | sed -E 's/MesclarJoin$/Mesclar/')"
if [[ -n "$A" && "$A" == "$B" ]]; then ok "T2 NextJoin = Next com Mesclar -> MesclarJoin ($(wc -l <<<"$A") disjuntos)"
else nok T2 "listas diferentes"; diff <(echo "$A") <(echo "$B"); fi

# T3
LA="$(bloco "$SS" Mesclar | grep "identidades' =")"
LB="$(bloco "$MJ" MesclarJoin | grep "identidades' =")"
if [[ -n "$LA" && "$LA" == "$LB" ]]; then ok "T3 identidades' de MesclarJoin igual a de Mesclar"
else nok T3 "Mesclar: [$LA] MesclarJoin: [$LB]"; fi

# T4
UA="$(bloco "$SS" Mesclar | grep -o 'UNCHANGED <<[^>]*>>')"
UB="$(bloco "$MJ" MesclarJoin | grep -o 'UNCHANGED <<[^>]*>>')"
if [[ -n "$UA" && "$UA" == "$UB" ]]; then ok "T4 mesmo UNCHANGED de Mesclar: $UA"
else nok T4 "Mesclar: [$UA] MesclarJoin: [$UB]"; fi

# T5
for p in I1M I4M I4Mp I2Fa MesclarPerdeChave; do
  [[ -n "$(bloco "$MC" "$p")$(bloco "$MC" "Corpo$p")" ]] || { nok T5 "$p nao existe em MCSession.tla"; continue; }
  if [[ "$p" == I2Fa ]]; then CORPO="$(bloco "$MJ" CorpoI2FaJ)"; else CORPO="$(bloco "$MJ" "${p}J")"; fi
  if [[ -z "$CORPO" ]]; then nok T5 "${p}J ausente"
  elif grep -qE '(^|[^A-Za-z])Mesclar([^A-Za-z]|$)' <<<"$CORPO"; then nok T5 "${p}J cita Mesclar"
  elif ! grep -q 'MesclarJoin =>' <<<"$CORPO"; then nok T5 "${p}J sem MesclarJoin no antecedente"
  else ok "T5 ${p}J sobre MesclarJoin"; fi
done

# T6
if [[ -f "$TLA_TOOLS" && -x "$JAVA" ]]; then
  JTMP="$(mktemp -d)"
  SAIDA="$(cd "$AQUI" && "$JAVA" -Djava.io.tmpdir="$JTMP" -cp "$TLA_TOOLS" tla2sany.SANY MCSessionJoin.tla 2>&1)"; RC=$?
  rm -rf "$JTMP"
  if [[ $RC -eq 0 ]] && ! grep -qiE '\*\*\* Errors|error' <<<"$SAIDA"; then ok "T6 SANY sem erro"
  else nok T6 "SANY rc=$RC"; echo "$SAIDA" | tail -20; fi
else
  echo "SKIP T6 (tla2tools.jar ou java ausente)"
fi

echo "falhas: $FALHAS"
exit "$FALHAS"
