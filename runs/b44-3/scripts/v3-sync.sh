#!/bin/bash
source /c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3/scripts/lib.sh
nomes
execucao V3-sync-ctl1  sync 443101 sim nenhuma
execucao V3-sync-acao1 sync 443101 sim ambos
execucao V3-sync-acao2 sync 443102 sim ambos
execucao V3-sync-ctl2  sync 443102 sim nenhuma
