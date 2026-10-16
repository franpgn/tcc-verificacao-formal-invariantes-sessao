#!/usr/bin/env bash
# runs/gate-netdegrade/verbatim.sh -- prova de que l-lib.sh, l0.sh e l1.sh sao copia VERBATIM dos blocos do roteiro
# (docs/relatorio-b29-2-implementacao-2026-10-16.md, secao 5). Nao toca rede, VM nem privilegio.
#   bash verbatim.sh extrair <doc> <regex-do-titulo>   imprime o bloco ```bash que segue o titulo "### ..." (LF)
#   bash verbatim.sh verificar [doc]                   diff doc x arquivo para os tres; exit 0 so se os tres forem identicos
# O doc pode estar em CRLF no checkout do Windows (core.autocrlf); o CR e removido antes de comparar (os .sh sao LF).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOC_PADRAO="$DIR/../../docs/relatorio-b29-2-implementacao-2026-10-16.md"

extrair() { # extrair <doc> <regex>
  tr -d '\r' < "$1" | awk -v re="$2" '
    st==0 && /^### / && $0 ~ re { st=1; next }
    st==1 && /^```/            { st=2; next }
    st==1 && /^### /           { exit 3 }          # outro titulo antes de achar o bloco
    st==2 && /^```/            { st=3; next }       # sem exit: consome o resto da entrada (exit cedo faz o `tr` morrer por SIGPIPE, 141, sob pipefail no Linux)
    st==2                      { print }
    END { if (st!=3 && st!=2) exit 3; if (st==2) exit 4 }'   # 3: bloco ausente; 4: bloco sem fechamento
}

verificar() {
  local doc="${1:-$DOC_PADRAO}" rc=0 par arq re tmp
  tmp="$(mktemp)"; trap 'rm -f "$tmp"' RETURN
  for par in 'l-lib.sh|^### Biblioteca `l-lib.sh`' 'l0.sh|^### L-0:' 'l1.sh|^### L-1 e L-2'; do
    arq="${par%%|*}"; re="${par#*|}"
    if ! extrair "$doc" "$re" > "$tmp"; then echo "FALHA extracao: $arq (titulo '$re')"; rc=1; continue; fi
    [ -s "$tmp" ] || { echo "FALHA: bloco vazio para $arq"; rc=1; continue; }
    if diff -u "$tmp" "$DIR/$arq" > "$tmp.diff"; then
      echo "IDENTICO $arq ($(wc -l < "$DIR/$arq" | tr -d ' ') linhas, sha256 $(sha256sum "$DIR/$arq" | cut -c1-16))"
    else
      echo "DIFERE   $arq"; cat "$tmp.diff"; rc=1
    fi
    rm -f "$tmp.diff"
  done
  return $rc
}

case "${1:-}" in
  extrair)   [ $# -eq 3 ] || { echo "uso: $0 extrair <doc> <regex>" >&2; exit 2; }; extrair "$2" "$3" ;;
  verificar) verificar "${2:-}" ;;
  *) echo "uso: $0 extrair <doc> <regex> | verificar [doc]" >&2; exit 2 ;;
esac
