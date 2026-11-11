#!/usr/bin/env bash
# T-A / A.4: extrai das saidas -coverage do TLC (runs/tlc/audit-b32/cov-*/*.out) a contagem das acoes
# Particionar e Mesclar. Formato do TLC: "<Acao ...>: D:T" onde D = estados distintos novos gerados
# pela acao e T = vezes em que a acao gerou sucessor (total). Mesclar "0:0" = a acao nunca foi habilitada.
# Uso: scripts/audit-cobertura.sh [dir_audit] > cobertura-mesclar.tsv
set -uo pipefail
AUD="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../runs/tlc/audit-b32}"
printf "dir\tarquivo\tresultado_tsv\tparticionar_D:T\tmesclar_D:T\tcobertura_impressa\n"
for f in "$AUD"/cov-*/*.out; do
  d="$(basename "$(dirname "$f")")"; n="$(basename "$f")"; base="${n%.out}"
  card="${n%%-*}"
  res="$(awk -F'\t' -v p="${base#*-}" '$4==p {r=$5":"$6} END{print r}' "$(dirname "$f")/resumo-$card.tsv")"
  part="$(grep -E '^<Particionar ' "$f" | tail -1 | sed -E 's/.*>: //')"
  mesc="$(grep -E '^<Mesclar ' "$f" | tail -1 | sed -E 's/.*>: //')"
  cov=nao; grep -q "coverage statistics" "$f" && cov=sim
  printf "%s\t%s\t%s\t%s\t%s\t%s\n" "$d" "$n" "$res" "${part:--}" "${mesc:--}" "$cov"
done
