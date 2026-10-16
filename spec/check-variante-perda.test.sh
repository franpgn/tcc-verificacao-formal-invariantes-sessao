#!/usr/bin/env bash
# Teste do verificador de fidelidade (B-35.1): o original passa; mutacoes de MCPerda.tla (e de SessionStore.tla) devem ser recusadas.
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d "${TMPDIR:-/tmp}/chk-perda-XXXXXX")"; trap 'rm -rf "$T"' EXIT
pass=0; fail=0
ok()  { echo "PASS: $1"; pass=$((pass+1)); }
bad() { echo "FAIL: $1"; fail=$((fail+1)); }
chk() { GIT_CHECK=0 "$@" bash "$AQUI/check-variante-perda.sh" >/dev/null 2>&1; }
chk env && ok "T-p-1 modulo real aceito" || bad "T-p-1 modulo real aceito"
mut() { # $1 id/descricao  $2 expressao sed sobre MCPerda.tla
  sed "$2" "$AQUI/MCPerda.tla" > "$T/m.tla"
  cmp -s "$T/m.tla" "$AQUI/MCPerda.tla" && { bad "$1 (mutacao nao alterou o arquivo)"; return; }
  chk env VAR="$T/m.tla" && bad "$1 recusado" || ok "$1 recusado"; }
mut "T-p-2 RemoverMsg com indice errado (i-1 -> i)" 's/SubSeq(@, 1, i-1)/SubSeq(@, 1, i)/'
mut "T-p-3 UNCHANGED sem vivo" 's/relogio, vivo, quedas/relogio, quedas/'
mut "T-p-4 UNCHANGED incluindo canal" 's/UNCHANGED <<sessoes/UNCHANGED <<canal, sessoes/'
mut "T-p-5 P1 sem guard ~PodeAgir" 's/~PodeAgir(r)/TRUE/'
mut "T-p-6 P0 com guard PodeAgir (deixa de ser o limite superior)" 's/: RemoverMsg(r, i)/: PodeAgir(r) RemoverMsg(r, i)/'
mut "T-p-7 orcamento nao consumido" "s/perdas + 1/perdas/"
mut "T-p-8 sem teto de orcamento" 's/perdas < MaxPerdas/TRUE/'
mut "T-p-9 NextP0 alterado" 's/NextP0 == (Next/NextP0 == (Next2/'
# SessionStore alterado (canal' de EntregaMensagem): a copia deixa de ser fiel
sed "0,/SubSeq(@, 1, i-1)/s//SubSeq(@, 1, i)/" "$AQUI/SessionStore.tla" > "$T/s.tla"
chk env ORIG="$T/s.tla" && bad "T-p-10 SessionStore divergente recusado" || ok "T-p-10 SessionStore divergente recusado"
# vars de SessionStore ganhando uma variavel: UNCHANGED da variante fica incompleto
sed 's/particaoAtiva, ladoDe, particoes >>/particaoAtiva, ladoDe, particoes, extra >>/' "$AQUI/SessionStore.tla" > "$T/v.tla"
chk env ORIG="$T/v.tla" && bad "T-p-11 vars novo em SessionStore recusado" || ok "T-p-11 vars novo em SessionStore recusado"
echo "==== RESULTADO: $pass PASS, $fail FAIL ===="; [[ $fail -eq 0 ]]
