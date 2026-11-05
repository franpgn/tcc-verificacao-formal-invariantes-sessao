#!/bin/bash
source /c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3/scripts/lib.sh
nomes
execucao V3-async-ctl1  async 443201 sim nenhuma
execucao V3-async-acao1 async 443201 sim ambos
execucao V3-async-acao2 async 443202 sim ambos
execucao V3-async-ctl2  async 443202 sim nenhuma
