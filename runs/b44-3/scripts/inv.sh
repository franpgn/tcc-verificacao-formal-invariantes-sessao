#!/bin/bash
# Investigacao dos dois achados (janela H2): (1) ERROR_TRANSPORT na maioria em SYNC depois da exclusao;
# (2) fusao ~10 s mais lenta depois da AC-6 (hipotese: fase do ViewConsistencyChecker do MERGE3, 48 s).
source /c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3/scripts/lib.sh
fase "INV inicio"
# coordenador isn1, como no V-3 original (a fusao pode passar o papel ao isn3)
garante_isn1() { nomes; case "$CO" in isn1-*) ;; *) desce podman-compose.b443-diag.yml; sobe podman-compose.b443-diag.yml isn1 isn2 isn3;; esac; }
garante_isn1
HOOK="amostra_rede 60 '15 30 45' isn1 isn2" execucao INV-sync-rede1 sync 443701 sim ambos
garante_isn1
HOOK="amostra_rede 60 '15 30 45' isn1 isn2" execucao INV-sync-rede-ctl sync 443701 sim nenhuma
garante_isn1
HOOK="amostra_rede 60 '15 30 45' isn1 isn2" execucao INV-sync-rede2 sync 443702 sim ambos
garante_isn1
CURA_S=40 execucao INV-async-cura40 async 443703 sim ambos
fase "INV fim"
