#!/bin/bash
# A4 (06/11/2026): lote das execucoes, intercaladas por caso; cada uma seguida da analise (resumo.json).
# Antes de cada execucao, registra os conteineres de outros projetos e a memoria da maquina do Podman.
S=/c/Users/Francesco/Development/tcc/wt-a4/runs/a4-teste/scripts
O=/c/Users/Francesco/Development/tcc/wt-a4/runs/a4-teste
cd /c/Users/FRANCE~1/AppData/Local/Temp/claude/98c9abffb040f610/0786b06c-748d-4829-8e8b-152396a994ac/scratchpad
run() {
  echo "$(date -u +%FT%TZ) antes de $1: conteineres=[$(podman ps --format '{{.Names}}' | tr '\n' ' ')] $(podman machine ssh 'free -m' 2>/dev/null | awk '/Mem:/{print "mem_usada_mb="$3" disponivel_mb="$7}')"
  bash $S/a4.sh "$@" > $O/$1.out 2>&1
  python -I $S/analisar_a4.py $O/$1 > $O/$1/resumo.json 2> $O/$1/resumo.err
  echo "$(date -u +%FT%TZ) $1 pronto ($(tail -1 $O/$1/fases.tsv | cut -f3))"
}
for par in "${@}"; do run $par; done
echo "lote pronto"
