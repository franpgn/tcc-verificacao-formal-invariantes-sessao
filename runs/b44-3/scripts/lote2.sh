#!/bin/bash
# Janela H2 (2026-11-05): refaz o par final do V-3 ASYNC (a acao2 anterior ficou invalida) e roda V-5/V-5b.
source /c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3/scripts/lib.sh
fase "LOTE2 inicio (janela H2): cluster recriado"
desce podman-compose.b443-diag.yml
sobe podman-compose.b443-diag.yml isn1 isn2 isn3
execucao V3-async-acao2r async 443202 sim ambos
execucao V3-async-ctl2   async 443202 sim nenhuma
execucao V5-async-ctl   async 443501 nao nenhuma
execucao V5-async-acao  async 443501 nao ambos
execucao V5-sync-acao   sync  443502 nao ambos
execucao V5-sync-ctl    sync  443502 nao nenhuma
execucao V5b-async-ctl      async 443551 sim nenhuma
execucao V5b-async-maioria  async 443551 sim maioria
execucao V5b-async-isolado  async 443551 sim isolado
fase "LOTE2 fim"
