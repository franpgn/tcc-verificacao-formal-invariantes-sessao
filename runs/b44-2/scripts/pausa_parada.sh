#!/bin/bash
# W-2a item 9, I-5 (risco 3 da secao 3.1): pedido de parada a um no PAUSADO, com o prazo de 2 s da secao 8; depois
# retoma o no e ve se ele para sozinho. Sem carga. O SmokeAtuador pula de proposito a guarda "respondeu na ultima
# janela"; o caminho de producao recusaria o pedido sem enviar nada (AtuadorRestInfinispanTest.ac5NoSemResposta).
#  frio:     cliente novo, sem desafio Digest guardado (o primeiro POST vai sem credencial);
#  aquecido: o cliente faz um GET autenticado ao alvo antes da pausa e manda o POST com o nonce guardado;
#  sem:      controle, pausa e retomada sem pedido.
# Uso: pausa_parada.sh ROTULO frio|aquecido|sem
set -u
. /c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2/scripts/lib-w2a.sh
rot=$1 modo=$2
D=$I/$rot; rm -rf $D; mkdir -p $D
recriar > $D/subir.txt 2>&1 || { echo "subir falhou"; exit 1; }
coord=$(coordenador); alvo=isn3; [ "$coord" = isn3 ] && alvo=isn2
pa=$(porta $alvo)
printf 'rotulo=%s modo=%s alvo=%s coordenador=%s prazo_ms=2000\n' $rot $modo $alvo $coord | tee $D/parametros.txt
W=$(cygpath -w /c/Users/Francesco/Development/tcc/wt-w2a)
CP="$W\\workload\\target\\classes;$(cat /c/Users/FRANCE~1/AppData/Local/Temp/claude/98c9abffb040f610/0786b06c-748d-4829-8e8b-152396a994ac/scratchpad/cp-w2a.txt)"
smoke() { "$JAVA" -Dprazo.ms=2000 -cp "$CP" "$W\\runs\\b44-2\\scripts\\SmokeAtuador.java" "$@" 2>&1 | grep --line-buffered -v SLF4J; }
if [ $modo = aquecido ]; then
  smoke ac5-aquecido sessions counters 127.0.0.1:$pa 8 > $D/smoke.txt &
  sm=$!
  for i in $(seq 1 100); do grep -q aquecimento $D/smoke.txt 2>/dev/null && break; sleep 0.1; done
  fase $D "aquecido: $(grep aquecimento $D/smoke.txt)"
  podman pause w2a-$alvo > /dev/null; fase $D "pausa $alvo"
  wait $sm; fase $D "ac5-retorno (aquecido)"
else
  podman pause w2a-$alvo > /dev/null; fase $D "pausa $alvo"
  sleep 2
  if [ $modo = frio ]; then fase $D "ac5-pedido (frio)"; smoke ac5 sessions counters 127.0.0.1:$pa > $D/smoke.txt; fase $D "ac5-retorno (frio)"; fi
fi
sleep 8
podman unpause w2a-$alvo > /dev/null; fase $D "retomada $alvo"
parou=nao
for i in $(seq 1 40); do
  e=$(podman inspect w2a-$alvo --format '{{.State.Status}}' 2>/dev/null)
  c=$(curl -s -m 0.5 -o /dev/null -w '%{http_code}' http://127.0.0.1:$pa/rest/v2/container/health/status 2>/dev/null)
  printf '%s\t%s\t%s\n' "$(agora_ns)" "conteiner=$e" "saude=$c" >> $D/vigia.tsv
  if [ "$e" != running ]; then parou=sim; fase $D "alvo-parou ($e)"; break; fi
  sleep 0.5
done
fase $D "fim parou=$parou"
logs $D; estado $D fim
echo "pronto $rot parou=$parou"
