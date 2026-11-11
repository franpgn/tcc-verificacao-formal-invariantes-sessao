#!/bin/bash
# W-2a item 9, I-3 e I-4: AC-5 forcado pelo SmokeAtuador (o P1 nao dispara sem o W-4; V-W2a secao 11), carga S3
# sem controlador (--controller off, para nenhuma politica reagir a parada externa).
#  I-3: corte do alvo por nftables (particao-w2a.sh) em T_CORTE; AC-5 5 s depois (com) ou nada (sem); cura no fim.
#  I-4: sem corte; escritor REST no alvo de T_ESC-5 s ate a recusa; AC-5 em T_ESC (com) ou nada (sem); depois, GET
#       das chaves confirmadas nos sobreviventes.
# Uso: parada.sh ROTULO corte|semcorte sync|async com|sem SEMENTE
set -u
. /c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2/scripts/lib-w2a.sh
rot=$1 cenario=$2 rep=$3 acao=$4 semente=$5
D=$I/$rot; rm -rf $D; mkdir -p $D
recriar > $D/subir.txt 2>&1 || { echo "subir falhou"; exit 1; }
coord=$(coordenador); alvo=isn3; [ "$coord" = isn3 ] && alvo=isn2
outros=$(for n in isn1 isn2 isn3; do [ $n != $alvo ] && echo -n "$n "; done)
pa=$(porta $alvo)
cache=sessions$([ $rep = async ] && echo -async)
printf 'rotulo=%s cenario=%s replicacao=%s acao=%s alvo=%s coordenador=%s semente=%s\n' $rot $cenario $rep $acao $alvo $coord $semente \
  | tee $D/parametros.txt
W=$(cygpath -w /c/Users/Francesco/Development/tcc/wt-w2a)
CP="$W\\workload\\target\\classes;$(cat /c/Users/FRANCE~1/AppData/Local/Temp/claude/98c9abffb040f610/0786b06c-748d-4829-8e8b-152396a994ac/scratchpad/cp-w2a.txt)"
smoke() { "$JAVA" -cp "$CP" "$W\\runs\\b44-2\\scripts\\SmokeAtuador.java" "$@" 2>&1 | grep --line-buffered -v SLF4J; }
# vigia do harness: saude do alvo a cada 100 ms (transicoes) e estado do conteiner a cada 0,5 s
vigia() {
  ( ultimo=""; while [ ! -f $D/exit.txt ]; do
      c=$(curl -s -m 0.5 -o /dev/null -w '%{http_code}' http://127.0.0.1:$pa/rest/v2/container/health/status 2>/dev/null)
      [ "$c" != "$ultimo" ] && printf '%s\t%s\n' "$(agora_ns)" "saude=$c" >> $D/vigia.tsv; ultimo=$c; sleep 0.1
    done ) &
  ( ultimo=""; while [ ! -f $D/exit.txt ]; do
      e=$(podman inspect w2a-$alvo --format '{{.State.Status}}' 2>/dev/null)
      [ "$e" != "$ultimo" ] && printf '%s\t%s\n' "$(agora_ns)" "conteiner=$e" >> $D/vigia.tsv; ultimo=$e; sleep 0.5
    done ) &
}
fase $D "carga-inicio"
carga $D $rep $semente 120 off
vigia
if [ $cenario = corte ]; then
  sleep 30
  fase $D "pre-corte saude-alvo=$(curl -s -m 1 http://127.0.0.1:$pa/rest/v2/container/health/status)"
  marcador $D start $alvo
  bash $R/scripts/particao-w2a.sh cortar $alvo $outros > $D/corte.txt 2>&1
  fase $D "corte $alvo"
  sleep 5
  fase $D "pre-acao saude-alvo=$(curl -s -m 1 http://127.0.0.1:$pa/rest/v2/container/health/status) (N-11)"
  if [ $acao = com ]; then
    fase $D "ac5-pedido"; smoke ac5 sessions$([ $rep = async ] && echo -async) counters$([ $rep = async ] && echo -async) 127.0.0.1:$pa > $D/smoke.txt; fase $D "ac5-retorno"
  fi
  sleep 60
  if [ "$(podman inspect w2a-$alvo --format '{{.State.Status}}')" = running ]; then
    bash $R/scripts/particao-w2a.sh curar $alvo $outros > $D/cura.txt 2>&1; fase $D "cura $alvo"
  else
    fase $D "cura-dispensada ($alvo parado)"
    # P-41: com o no parado ate o fim, a leitura final por replica da carga (rodadaUnica) trava sem prazo.
    # O harness religa o no depois da janela medida, so para a carga conseguir terminar e gravar os CSV.
    podman start w2a-$alvo > /dev/null; fase $D "religado-para-a-leitura-final $alvo"
  fi
  marcador $D end $alvo
else
  sleep 35
  ate=$(( $(agora_ns) + 15000000000 ))
  fase $D "escritor-inicio $alvo"
  python -I $R/scripts/escritor_i4.py escrever $pa $cache w2a-i4-$rot $D/escritas.tsv $ate &
  esc=$!
  sleep 5
  if [ $acao = com ]; then
    marcador $D start $alvo
    fase $D "ac5-pedido"; smoke ac5 sessions$([ $rep = async ] && echo -async) counters$([ $rep = async ] && echo -async) 127.0.0.1:$pa > $D/smoke.txt; fase $D "ac5-retorno"
    marcador $D end $alvo
  fi
  wait $esc; fase $D "escritor-fim"
  sleep 5
  portas=$(for n in $outros; do echo -n "$(porta $n) "; done)
  python -I $R/scripts/escritor_i4.py verificar $D/escritas.tsv $cache $portas > $D/verificacao.txt 2>&1
  fase $D "verificacao $(head -1 $D/verificacao.txt)"
  if [ "$(podman inspect w2a-$alvo --format '{{.State.Status}}')" != running ]; then
    podman start w2a-$alvo > /dev/null; fase $D "religado-para-a-leitura-final $alvo (P-41)"
  fi
fi
esperar_carga $D
fase $D "carga-fim"
logs $D; estado $D fim
echo "pronto $rot"
