#!/usr/bin/env bash
# T-A / A.1: wrapper do recalculo (ver scripts/audit_tlc_runs.py).
# Uso: scripts/audit-tlc-runs.sh [dir_runs_tlc] [saida_dir]
#   ALEGACOES=<tsv> PADRAO=<glob de diretorios, default prop-*> PYTHON=<python>
set -euo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNS="${1:-$AQUI/../runs/tlc}"
OUT="${2:-$RUNS/audit-b32}"
mkdir -p "$OUT"
PY="${PYTHON:-python3}"
command -v "$PY" >/dev/null 2>&1 || PY=python
"$PY" "$AQUI/audit_tlc_runs.py" "$RUNS" "$OUT/tabela-recalculada.tsv" "${ALEGACOES:-$OUT/alegacoes-relatorio.tsv}" "${PADRAO:-prop-*}"
