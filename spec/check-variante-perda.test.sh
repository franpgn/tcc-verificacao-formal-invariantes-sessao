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
  sed "$2" "$AQUI/MCPerda.tla" > "$T/m.tla" || { bad "$1 (sed da mutacao falhou)"; return; }
  [[ -s "$T/m.tla" ]] || { bad "$1 (mutante vazio)"; return; }
  cmp -s "$T/m.tla" "$AQUI/MCPerda.tla" && { bad "$1 (mutacao nao alterou o arquivo)"; return; }
  chk env VAR="$T/m.tla" && bad "$1 recusado" || ok "$1 recusado"; }
mut "T-p-2 RemoverMsg com indice errado (i-1 -> i)" 's/SubSeq(@, 1, i-1)/SubSeq(@, 1, i)/'
mut "T-p-3 UNCHANGED sem vivo" 's/relogio, vivo, quedas/relogio, quedas/'
mut "T-p-4 UNCHANGED incluindo canal" 's/UNCHANGED <<sessoes/UNCHANGED <<canal, sessoes/'
mut "T-p-5 P1 sem guard ~PodeAgir" 's/~PodeAgir(r)/TRUE/'
mut "T-p-6 P0 com guard PodeAgir (TLA+ bem formado, deixa de ser o limite superior)" 's|: RemoverMsg(r, i)|: PodeAgir(r) /\ RemoverMsg(r, i)|'
mut "T-p-7 orcamento nao consumido" "s/perdas + 1/perdas/"
mut "T-p-8 sem teto de orcamento" 's/perdas < MaxPerdas/TRUE/'
mut "T-p-9 NextP0 alterado" 's/NextP0 == (Next/NextP0 == (Next2/'
mut "T-p-12 P1 invertido (~PodeAgir -> PodeAgir)" 's/: ~PodeAgir(r)/: PodeAgir(r)/'
mut "T-p-13 off-by-one no teto (< -> =<)" 's/perdas < MaxPerdas/perdas =< MaxPerdas/'
mut "T-p-14 orcamento consome 2 (+ 1 -> + 2)" "s/perdas + 1/perdas + 2/"
mut "T-p-15 SpecPerda0Semeada usa NextP1" '/^SpecPerda0Semeada/s/NextP0/NextP1/'
mut "T-p-16 SpecPerda1Semeada usa NextP0" '/^SpecPerda1Semeada/s/NextP1/NextP0/'
mut "T-p-17 SpecPerda0Livre usa InitSemeadoP" 's/\(SpecPerda0Livre   == \)InitP /\1InitSemeadoP /'
mut "T-p-18 SpecPerda1Semeada usa InitP (perde a semente)" 's/SpecPerda1Semeada == InitSemeadoP/SpecPerda1Semeada == InitP/'
mut "T-p-19 InitP sem perdas = 0" '/^InitP /s| .. perdas = 0||'
mut "T-p-20 InitSemeadoP sem perdas = 0" '/^InitSemeadoP /s| .. perdas = 0||'
mut "T-p-21 indice fora do canal (1..Len+1) em P0" '0,/1\.\.Len(canal\[r\])/s//1..Len(canal[r]) + 1/'
mut "T-p-22 PerdasOK com teto errado" '/^PerdasOK/s/MaxPerdas/MaxPerdas + 1/'
# cfgs gerados pelo runner (com um "java" falso: o runner so' precisa gerar o cfg): cada combinacao deve ter SPECIFICATION certo,
# MaxPerdas e PerdasOK em INVARIANTS
cfgs() { # $1 = runner; imprime 0 se todos os cfgs gerados estao corretos
  local R="$1" D n=0 bad=0 modo sab fal
  D="$(mktemp -d "${TMPDIR:-/tmp}/cfg-perda-XXXXXX")"
  for modo in semeado livre; do for sab in p0 p1; do for fal in sem queda particao; do
    OUTDIR="$D/$modo-$sab-$fal" JAVA=true TLA_TOOLS="$R" TIMEOUT_SECS=5 MAXPERDAS=2 bash "$R" $modo $sab $fal r2 I5 I1F >/dev/null 2>&1
    N=${sab#p}; M=$([[ $modo == semeado ]] && echo Semeada || echo Livre)
    for P in I5 I1F; do f="$D/$modo-$sab-$fal/r2-$P.cfg"; n=$((n+1))
      [[ -f "$f" ]] && grep -qx "SPECIFICATION SpecPerda$N$M" "$f" && grep -qE "^ +MaxPerdas += 2$" "$f" \
        && awk '/^INVARIANTS/{d=1;next} /^PROPERTY/{d=0} d&&$1=="PerdasOK"{ok=1} END{exit !ok}' "$f" || bad=$((bad+1))
    done
  done; done; done
  rm -rf "$D"; [[ $n -eq 24 && $bad -eq 0 ]]; }
cfgs "$AQUI/run-tlc-perda.sh" && ok "T-p-23 cfgs gerados corretos (24 cfgs: SPEC, MaxPerdas, PerdasOK em INVARIANTS)" || bad "T-p-23 cfgs gerados corretos"
mutrun() { # $1 id  $2 sed sobre run-tlc-perda.sh ; o runner mutado fica em $AQUI para achar MCPerda.tla? (nao precisa: JAVA=true)
  sed "$2" "$AQUI/run-tlc-perda.sh" > "$T/run.sh" || { bad "$1 (sed da mutacao falhou)"; return; }
  cmp -s "$T/run.sh" "$AQUI/run-tlc-perda.sh" && { bad "$1 (mutacao nao alterou o runner)"; return; }
  cfgs "$T/run.sh" && bad "$1 recusado" || ok "$1 recusado"; }
mutrun "T-p-24 runner sem PerdasOK em INVARIANTS" '/^    PerdasOK$/d; s/INVS="    PerdasOK$/INVS="/; s/else INVS="    PerdasOK"; /else INVS=""; /'
mutrun "T-p-25 runner sem MaxPerdas no cfg" '/MaxPerdas  = /d'
mutrun "T-p-26 runner com SPEC trocado (p0 usa SpecPerda1*)" 's/p0) PN=0/p0) PN=1/'
# MUTACAO=remover-nulo precisa efetivamente alterar a copia (2 linhas diferem) e deixar canal' = canal
D27="$(mktemp -d "${TMPDIR:-/tmp}/mut-perda-XXXXXX")"
M27="$(OUTDIR="$D27" MUTACAO=remover-nulo JAVA=true TLA_TOOLS="$AQUI/run-tlc-perda.sh" TIMEOUT_SECS=5 bash "$AQUI/run-tlc-perda.sh" semeado p0 sem r2 I5 2>&1)"
grep -q "Mutacao: 2 linhas diferem" <<<"$M27" && ! grep -q "mutacao nao aplicada" <<<"$M27" && ok "T-p-27 MUTACAO=remover-nulo altera exatamente 2 linhas" || bad "T-p-27 MUTACAO=remover-nulo altera exatamente 2 linhas"
rm -rf "$D27"
# SessionStore alterado (canal' de EntregaMensagem): a copia deixa de ser fiel
sed "0,/SubSeq(@, 1, i-1)/s//SubSeq(@, 1, i)/" "$AQUI/SessionStore.tla" > "$T/s.tla"
chk env ORIG="$T/s.tla" && bad "T-p-10 SessionStore divergente recusado" || ok "T-p-10 SessionStore divergente recusado"
# vars de SessionStore ganhando uma variavel: UNCHANGED da variante fica incompleto
sed 's/particaoAtiva, ladoDe, particoes >>/particaoAtiva, ladoDe, particoes, extra >>/' "$AQUI/SessionStore.tla" > "$T/v.tla"
chk env ORIG="$T/v.tla" && bad "T-p-11 vars novo em SessionStore recusado" || ok "T-p-11 vars novo em SessionStore recusado"
echo "==== RESULTADO: $pass PASS, $fail FAIL ===="; [[ $fail -eq 0 ]]
