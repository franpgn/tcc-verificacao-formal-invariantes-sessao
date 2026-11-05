#!/bin/bash
# V-4: o V4-async-ctl rodou com isn1 coordenador (a fusao da acao anterior passou o papel ao isn1);
# este controle refaz a condicao do V-4 (isn3 coordenador) logo depois.
source /c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3/scripts/lib.sh
sobe podman-compose.b443-diag.yml isn3 isn1 isn2
execucao V4-async-ctl2 async 443401 sim nenhuma
desce podman-compose.b443-diag.yml
fase "B-44.3 fim (2): cluster b443 derrubado"
