#!/bin/bash
# V-5 (falso positivo, sem corte) e V-5b (um lado so, sob corte, ASYNC)
source /c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3/scripts/lib.sh
nomes
execucao V5-async-ctl   async 443501 nao nenhuma
execucao V5-async-acao  async 443501 nao ambos
execucao V5-sync-acao   sync  443502 nao ambos
execucao V5-sync-ctl    sync  443502 nao nenhuma
execucao V5b-async-ctl      async 443551 sim nenhuma
execucao V5b-async-maioria  async 443551 sim maioria
execucao V5b-async-isolado  async 443551 sim isolado
