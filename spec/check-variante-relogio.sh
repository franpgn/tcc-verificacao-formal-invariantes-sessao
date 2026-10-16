#!/usr/bin/env bash
# B-36 / R.1: fidelidade da copia. Extrai o corpo de EntregaMensagem(r) de SessionStore.tla e o de
# EntregaMensagemMax(r) de MCRelogio.tla, normaliza o nome da acao e o fim de linha, e exige que as
# duas unicas diferencas autorizadas sejam exatamente as previstas em docs/decisoes-senior-2026-10-16.md
# SS1.4: (a) a linha "UNCHANGED <<relogio, vivo, ...>>" vira "relogio' = [... Max(@, TsMsg(m))]" mais um
# UNCHANGED igual ao original sem "relogio, "; (b) nada mais. Depois disso o diff do restante tem de ser vazio.
# Uso: spec/check-variante-relogio.sh   (qualquer diretorio; sai com 0 se fiel, 1 se diverge)
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIG="${ORIG:-$AQUI/SessionStore.tla}"; VAR="${VAR:-$AQUI/MCRelogio.tla}"

# corpo: da linha "NOME(r) ==" ate a ultima linha indentada/vazia antes da proxima linha de coluna 0
corpo() { # $1 arquivo  $2 nome
  tr -d '\r' < "$1" | awk -v n="$2(r) ==" '
    $0==n {dentro=1; print; next}
    dentro && /^[^ \t]/ {exit}
    dentro {print}' | sed -e :a -e '/^[[:space:]]*$/{$d;N;ba' -e '}'
}
A="$(corpo "$ORIG" EntregaMensagem)"; B="$(corpo "$VAR" EntregaMensagemMax)"
[[ -n "$A" && -n "$B" ]] || { echo "FALHA: corpo nao encontrado (orig=${#A} var=${#B} bytes)" >&2; exit 1; }

# casamento por texto fixo apos remover a indentacao (sem regex: evita o escape de "/\\")
achar() { # $1 texto fixo (sem indentacao)  $2 corpo ; imprime as linhas que, sem indentacao, sao iguais a $1
  T="$1" awk '{l=$0; sub(/^[ 	]+/, "", l); if (l==ENVIRON["T"]) print}' <<<"$2"; }
UNC_ORIG="$(achar '/\ UNCHANGED <<relogio, vivo, quedas, particaoAtiva, ladoDe, particoes>>' "$A")"
REL_VAR="$(achar "/\ relogio' = [relogio EXCEPT ![r] = Max(@, TsMsg(m))]" "$B")"
UNC_VAR="$(achar '/\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>' "$B")"
for x in "$UNC_ORIG" "$REL_VAR" "$UNC_VAR"; do
  [[ -n "$x" && "$(wc -l <<<"$x")" -eq 1 ]] || { echo "FALHA: linha autorizada ausente ou duplicada (relogio/UNCHANGED)" >&2; exit 1; }
done
# o UNCHANGED da variante tem de ser exatamente o original sem "relogio, "
[[ "${UNC_ORIG/relogio, /}" == "$UNC_VAR" ]] || { echo "FALHA: UNCHANGED da variante nao e o original sem relogio" >&2; exit 1; }

# normaliza: troca o nome e remove as linhas autorizadas dos dois lados
NA="$(grep -vxF "$UNC_ORIG" <<<"$A" | sed 's/^EntregaMensagem(r) ==$/EntregaMensagem(r) ==/')"
NB="$(grep -vxF -e "$REL_VAR" -e "$UNC_VAR" <<<"$B" | sed 's/^EntregaMensagemMax(r) ==$/EntregaMensagem(r) ==/')"
if D="$(diff <(printf '%s\n' "$NA") <(printf '%s\n' "$NB"))"; then
  echo "OK: corpo de EntregaMensagemMax identico ao de EntregaMensagem (linhas: orig=$(wc -l <<<"$A") var=$(wc -l <<<"$B"); diferencas autorizadas: 1 linha -> 2 linhas); diff vazio"
else
  echo "FALHA: diff nao vazio:"; echo "$D"; exit 1
fi
