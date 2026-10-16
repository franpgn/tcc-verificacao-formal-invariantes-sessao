#!/usr/bin/env bash
# Teste do verificador de fidelidade (B-36 / R.1): o original passa; tres mutacoes da copia devem ser recusadas.
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d "${TMPDIR:-/tmp}/chk-var-XXXXXX")"; trap 'rm -rf "$T"' EXIT
pass=0; fail=0
ok()  { echo "PASS: $1"; pass=$((pass+1)); }
bad() { echo "FAIL: $1"; fail=$((fail+1)); }
bash "$AQUI/check-variante-relogio.sh" >/dev/null 2>&1 && ok "T-v-1 variante real aceita" || bad "T-v-1 variante real aceita"
sed '0,/m.sessao.ts > sessoes/s//m.sessao.ts >= sessoes/' "$AQUI/MCRelogio.tla" > "$T/a.tla"
VAR="$T/a.tla" bash "$AQUI/check-variante-relogio.sh" >/dev/null 2>&1 && bad "T-v-2 gate alterado recusado" || ok "T-v-2 gate alterado recusado"
sed 's/Max(@, TsMsg(m))/@/' "$AQUI/MCRelogio.tla" > "$T/b.tla"
VAR="$T/b.tla" bash "$AQUI/check-variante-relogio.sh" >/dev/null 2>&1 && bad "T-v-3 relogio sem max recusado" || ok "T-v-3 relogio sem max recusado"
sed 's/UNCHANGED <<vivo, quedas/UNCHANGED <<vivo/' "$AQUI/MCRelogio.tla" > "$T/c.tla"
VAR="$T/c.tla" bash "$AQUI/check-variante-relogio.sh" >/dev/null 2>&1 && bad "T-v-4 UNCHANGED incompleto recusado" || ok "T-v-4 UNCHANGED incompleto recusado"
echo "==== RESULTADO: $pass PASS, $fail FAIL ===="; [[ $fail -eq 0 ]]
