#!/bin/bash
# V-4 (isn3 coordenador) e V-6 (passcode), janela H2.
source /c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3/scripts/lib.sh
fase "V-4 inicio: recria o cluster na ordem isn3, isn1, isn2"
desce podman-compose.b443-diag.yml
sobe podman-compose.b443-diag.yml isn3 isn1 isn2
probe V4-vista "127.0.0.1:12447=jmx=GMS.view" "127.0.0.1:12449=jmx=GMS.view" | cut -f1,7 | sed 's/.*GMS=/GMS=/'
execucao V4-async-acao async 443401 sim ambos
execucao V4-async-ctl  async 443401 sim nenhuma
fase "V-4 fim"
fase "V-6 inicio: cluster com diag.passcode"
desce podman-compose.b443-diag.yml
sobe podman-compose.b443-pass.yml isn1 isn2 isn3
P=127.0.0.1:12447
fase "V-6 a: sem resumo";            probe V6 "$P=jmx=GMS.view"
fase "V-6 b: resumo certo";          probe V6 --passcode b443-lab-passcode "$P=jmx=GMS.view"
fase "V-6 c: passcode errado";       probe V6 --passcode b443-lab-passcode --passcode-errado "$P=jmx=GMS.view"
fase "V-6 d: t1 antigo (repeticao)"; probe V6 --passcode b443-lab-passcode --t1 946684800000 "$P=jmx=GMS.view"
fase "V-6 e: op com resumo nos 3";   probe V6 --passcode b443-lab-passcode "127.0.0.1:12447=jmx=FD_ALL3.timeout" "127.0.0.1:12448=jmx=FD_ALL3.timeout" "127.0.0.1:12449=jmx=FD_ALL3.timeout"
fase "V-6 f: op sem resumo nos 3";   probe V6 "127.0.0.1:12447=op=GMS.suspect[$N3]" "127.0.0.1:12448=op=GMS.suspect[$N3]" "127.0.0.1:12449=op=GMS.suspect[$N1]"
sleep 3; for n in isn1 isn2 isn3; do echo "$n tamanho=$(tamanho $n)"; done
salvalogs V6 "$(date -u -d '-3 min' +%FT%T.%3NZ)"
fase "V-6 fim"
desce podman-compose.b443-pass.yml
fase "B-44.3 fim: cluster b443 derrubado"
