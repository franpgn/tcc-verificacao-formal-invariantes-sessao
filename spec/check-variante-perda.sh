#!/usr/bin/env bash
# B-35 / B-35.1: fidelidade do modulo MCPerda.tla ao SessionStore.tla (docs/decisoes-senior-2026-10-16.md SS2.2).
# Verifica, sem TLC:
#  (1) a expressao canal' de RemoverMsg(r, i) e' IDENTICA (sem indentacao/CR) a de EntregaMensagem(r);
#  (2) a lista UNCHANGED de PerderQualquer e de PerderNaVista e' exatamente `vars` de SessionStore.tla
#      menos `canal` (mesmo conjunto, sem repeticao), e cada acao tem `perdas' = perdas + 1` e `perdas < MaxPerdas`;
#  (3) P1 tem o guard `~PodeAgir(r)` e P0 NAO tem (distincao dos sabores);
#  (4) NextP0/NextP1 sao Next com UNCHANGED perdas, mais a respectiva acao de perda e nada mais;
#  (4b/4c) forma exata dos quantificadores, Init com perdas = 0, varsP, PerdasOK e das quatro Spec*;
#  (5) SessionStore.tla e MCSession.tla sem diferenca no git (se estiver num repo e GIT_CHECK != 0).
# Uso: spec/check-variante-perda.sh   (sai com 0 se fiel, 1 se diverge)
# Variaveis: ORIG (SessionStore.tla), VAR (MCPerda.tla), GIT_CHECK=0 desliga (5).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIG="${ORIG:-$AQUI/SessionStore.tla}"; VAR="${VAR:-$AQUI/MCPerda.tla}"
falha() { echo "FALHA: $*" >&2; exit 1; }
sem() { tr -d '\r' < "$1" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'; }   # sem CR e indentacao
corpo() { # $1 arquivo $2 cabecalho exato ("Nome(r) ==" ou "Nome ==") : corpo ate a proxima linha de coluna 0
  tr -d '\r' < "$1" | awk -v n="$2" '
    $0==n || index($0, n " ")==1 {dentro=1; print; next}
    dentro && /^[^ \t]/ {exit}
    dentro {print}'; }

# (1)
CA="$(corpo "$ORIG" 'EntregaMensagem(r) ==' | sed -e 's/^[[:space:]]*//' | grep -F "canal' = [canal EXCEPT" || true)"
CB="$(corpo "$VAR" "RemoverMsg(r, i) ==" | sed -e 's/^[[:space:]]*//' | grep -F "canal' = [canal EXCEPT" || true)"
CA="$(sed 's|^/[^ ]* *||' <<<"$CA")"; [[ "$(wc -l <<<"$CA")" -eq 1 && -n "$CA" ]] || falha "canal' de EntregaMensagem ausente ou duplicado"
[[ "$(wc -l <<<"$CB")" -eq 1 && -n "$CB" ]] || falha "canal' de RemoverMsg ausente ou duplicado"
[[ "$CA" == "$CB" ]] || falha "canal' de RemoverMsg difere do de EntregaMensagem:
  orig: $CA
  var : $CB"

# (2) conjunto esperado do UNCHANGED = vars sem canal
VARS="$(tr -d '\r' < "$ORIG" | awk '/^vars ==/ {d=1} d {print} d && />>/ {exit}' | tr -d '\n' | sed -e 's/.*<<//' -e 's/>>.*//' | tr ',' '\n' | sed 's/[[:space:]]//g' | grep -v '^$')"
[[ -n "$VARS" ]] || falha "vars nao encontrado em $ORIG"
ESP="$(grep -vx canal <<<"$VARS" | sort)"; [[ "$(wc -l <<<"$ESP")" -eq "$(( $(wc -l <<<"$VARS") - 1 ))" ]] || falha "vars nao contem canal"
for A in PerderQualquer PerderNaVista; do
  C="$(corpo "$VAR" "$A ==")"; [[ -n "$C" ]] || falha "$A nao encontrado"
  U="$(grep -F 'UNCHANGED <<' <<<"$C" || true)"; [[ "$(wc -l <<<"$U")" -eq 1 && -n "$U" ]] || falha "$A: UNCHANGED ausente ou duplicado"
  L="$(sed -e 's/.*<<//' -e 's/>>.*//' <<<"$U" | tr ',' '\n' | sed 's/[[:space:]]//g' | sort)"
  [[ "$L" == "$ESP" ]] || falha "$A: UNCHANGED difere de vars sem canal:
  esperado: $(tr '\n' ' ' <<<"$ESP")
  obtido  : $(tr '\n' ' ' <<<"$L")"
  [[ "$(grep -cxF -e "/\ perdas < MaxPerdas" <<<"$(sem /dev/stdin <<<"$C")")" -eq 1 ]] || falha "$A: guard perdas < MaxPerdas ausente"
  [[ "$(grep -cxF -e "/\ perdas' = perdas + 1" <<<"$(sem /dev/stdin <<<"$C")")" -eq 1 ]] || falha "$A: perdas' = perdas + 1 ausente"
  [[ "$(grep -cF 'RemoverMsg(r, i)' <<<"$C")" -eq 1 ]] || falha "$A: deve usar RemoverMsg(r, i) exatamente uma vez"
done
# (3)
corpo "$VAR" "PerderNaVista ==" | grep -qF '~PodeAgir(r)' || falha "PerderNaVista sem guard ~PodeAgir(r)"
corpo "$VAR" "PerderQualquer ==" | grep -qF 'PodeAgir' && falha "PerderQualquer nao deve filtrar por PodeAgir (e' o limite superior P0)"
# (4)
for P in "NextP0 PerderQualquer" "NextP1 PerderNaVista"; do set -- $P
  N="$(corpo "$VAR" "$1 ==" | tr -d '\n' | sed -e 's/[[:space:]]\+/ /g')"
  [[ "$N" == "$1 == (Next /\ UNCHANGED perdas) \/ $2" ]] || falha "$1 inesperado: '$N'"
done
# (4b) forma exata do quantificador (B-35.1 validacao SS2: P1 invertido, indice fora, filtro em P0)
colapsa() { tr -d '\r' | sed -E -e 's/^[[:space:]]+//' -e 's/[[:space:]]+/ /g' -e 's/[[:space:]]$//'; }
Q0='/\ \E r \in REPLICAS : \E i \in 1..Len(canal[r]) : RemoverMsg(r, i)'
Q1='/\ \E r \in REPLICAS : \E i \in 1..Len(canal[r]) : ~PodeAgir(r) /\ RemoverMsg(r, i)'
[[ "$(corpo "$VAR" 'PerderQualquer ==' | colapsa | grep -cxF -e "$Q0")" -eq 1 ]] || falha "PerderQualquer: quantificador diferente de: $Q0"
[[ "$(corpo "$VAR" 'PerderNaVista ==' | colapsa | grep -cxF -e "$Q1")" -eq 1 ]] || falha "PerderNaVista: quantificador diferente de: $Q1"
# (4c) Init com perdas = 0, varsP, PerdasOK e as quatro Spec* (cada uma com o Init e o Next certos)
linha() { tr -d '\r' < "$VAR" | grep -E "^$1 *==" | colapsa; }
declare -A ESPERADO=(
  [varsP]='varsP == << vars, perdas >>'
  [PerdasOK]='PerdasOK == perdas \in 0..MaxPerdas'
  [InitP]='InitP == Init /\ perdas = 0'
  [InitSemeadoP]='InitSemeadoP == InitSemeado /\ perdas = 0'
  [SpecPerda0Semeada]='SpecPerda0Semeada == InitSemeadoP /\ [][NextP0]_varsP'
  [SpecPerda1Semeada]='SpecPerda1Semeada == InitSemeadoP /\ [][NextP1]_varsP'
  [SpecPerda0Livre]='SpecPerda0Livre == InitP /\ [][NextP0]_varsP'
  [SpecPerda1Livre]='SpecPerda1Livre == InitP /\ [][NextP1]_varsP' )
for K in "${!ESPERADO[@]}"; do
  [[ "$(linha "$K")" == "${ESPERADO[$K]}" ]] || falha "$K inesperado: '$(linha "$K")' (esperado '${ESPERADO[$K]}')"
done
# (5)
if [[ "${GIT_CHECK:-1}" != 0 ]] && git -C "$AQUI" rev-parse --git-dir >/dev/null 2>&1; then
  BASE="${BASE_REF:-test/b36-relogio-max}"
  if git -C "$AQUI" rev-parse --verify -q "$BASE" >/dev/null; then
    git -C "$AQUI" diff --quiet "$BASE" -- SessionStore.tla MCSession.tla || falha "SessionStore.tla/MCSession.tla diferem de $BASE"
  fi
fi
echo "OK: RemoverMsg identico ao canal' de EntregaMensagem; UNCHANGED = vars sem canal ($(tr '\n' ' ' <<<"$ESP")); P1 filtra ~PodeAgir, P0 nao; NextP0/NextP1 = Next + perda; base inalterada"
