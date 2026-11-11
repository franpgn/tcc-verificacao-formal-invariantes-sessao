#!/bin/bash
# W-2a item 9, I-1 e I-2: carga S3 de 120 s com o controlador (active ou shadow = controle sem acao) e uma queda
# de um no do cluster w2a no meio. kill = podman kill + podman start (F1); pause = podman pause + unpause (I-2).
# Uso: queda.sh ROTULO kill|pause comum|coord sync|async active|shadow SEMENTE T_FALHA_S DUR_FALHA_S [DURACAO_S]
# Warm-up de 15 s (WARMUP): a falha entra depois de o controlador comecar a avaliar janelas.
set -u
. /c/Users/Francesco/Development/tcc/wt-w2a/runs/b44-2/scripts/lib-w2a.sh
rot=$1 tipo=$2 qual=$3 rep=$4 modo=$5 semente=$6 tfalha=$7 dfalha=$8 dur=${9:-120}
D=$I/$rot; rm -rf $D; mkdir -p $D
recriar > $D/subir.txt 2>&1 || { echo "subir falhou"; exit 1; }
coord=$(coordenador)
if [ "$qual" = coord ]; then alvo=$coord; else
  alvo=isn3; [ "$coord" = isn3 ] && alvo=isn2
fi
printf 'rotulo=%s tipo=%s qual=%s alvo=%s coordenador=%s replicacao=%s modo=%s semente=%s t_falha=%s dur_falha=%s duracao=%s warmup=%s\n' \
  $rot $tipo $qual $alvo $coord $rep $modo $semente $tfalha $dfalha $dur ${WARMUP:-15} | tee $D/parametros.txt
rebal $D antes
fase $D "carga-inicio"
carga $D $rep $semente $dur $modo
# amostrador do harness: rebalancing_enabled e entradas por no do cache de sessoes, a cada 3 s
( while [ ! -f $D/exit.txt ]; do
    rebal $D amostra
    curl -s -m 2 --digest -u $AUTH "http://127.0.0.1:12501/rest/v2/caches/sessions$([ $rep = async ] && echo -async)?action=distribution" \
      | python -c "import json,sys,time;d=json.load(sys.stdin);print('%d\t%s' % (time.time_ns(), ';'.join('%s=%s' % (x['node_name'], x['memory_entries']) for x in d)))" \
      >> $D/distribuicao.tsv 2>/dev/null
    sleep 3
  done ) &
sleep $tfalha
fase $D "falha-inicio $tipo $alvo"; marcador $D start $alvo
if [ $tipo = kill ]; then podman kill w2a-$alvo > /dev/null; else podman pause w2a-$alvo > /dev/null; fi
fase $D "falha-aplicada $tipo $alvo"
sleep $dfalha
fase $D "falha-fim $tipo $alvo"
if [ $tipo = kill ]; then podman start w2a-$alvo > /dev/null; else podman unpause w2a-$alvo > /dev/null; fi
fase $D "falha-desfeita $tipo $alvo"
p=$(porta $alvo)
for i in $(seq 1 600); do
  c=$(curl -s -m 1 -o /dev/null -w '%{http_code}' http://127.0.0.1:$p/rest/v2/container/health/status 2>/dev/null)
  [ "$c" = 200 ] && break; sleep 0.1
done
fase $D "rest-volta $alvo http=$c"; marcador $D end $alvo
esperar_carga $D
fase $D "carga-fim"
rebal $D depois
logs $D; estado $D fim
echo "pronto $rot"
