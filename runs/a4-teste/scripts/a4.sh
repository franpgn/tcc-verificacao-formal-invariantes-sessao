#!/bin/bash
# A4 (06/11/2026): mede POST /rest/v2/server/ignored-caches/{cache} mandado so ao no suspeito (alvo), contra a
# parada (POST /rest/v2/server?action=stop), no cluster local a4 (portas 12901-12903), com carga S3 ASYNC sem
# controlador (--controller off: so o mecanismo, nenhuma politica reage). Uma encarnacao do cluster por execucao.
#
# Uso: a4.sh ROTULO CASO SEMENTE
#   CASO = corte-ignorar    corte do alvo em o+30 s (o = linha "S3:" da carga); POST ignored-caches dos dois caches
#                           ASYNC ao alvo em o+35 s; cura em o+120 s; espera a fusao (tamanho 3 no alvo e no isn1);
#                           15 s depois, DELETE dos dois caches so no alvo; 20 s depois, fim das sondas.
#          corte-sessoes    igual ao corte-ignorar, mas so com o sessions-async (um pedido POST e um DELETE). Criado
#                           depois da corte-ignorar-r1: sob corte, o segundo pedido simultaneo so teve efeito na deteccao.
#          corte-sequencial igual ao corte-ignorar, mas os pedidos dos dois caches saem em sequencia (o segundo so
#                           depois da resposta do primeiro), no POST e no DELETE.
#          corte-seq-detec  como o corte-sequencial, mas o DELETE (em sequencia, no alvo) sai assim que o alvo se ve
#                           sozinho (get-availability do sessions-async = DEGRADED_MODE no alvo), antes da cura; depois
#                           da fusao, nenhum DELETE. Regra de desfazer 1 (criado depois da corte-sequencial-r1).
#          corte-seq-maioria como o corte-sequencial, mas o desfazer e UM DELETE do sessions-async mandado a um no da
#                           maioria (o primeiro de OUTROS) depois da fusao. Regra de desfazer 2.
#          corte-parar      corte em o+30 s; POST ?action=stop ao alvo em o+35 s; cura em o+120 s (se o conteiner
#                           ainda estiver de pe); fim das sondas 10 s depois.
#          corte-controle   corte em o+30 s, nenhuma acao; cura em o+120 s; espera a fusao; fim 15 s depois.
#          semcorte-sessoes igual ao semcorte-ignorar, so com o sessions-async (criado depois da semcorte-ignorar-r1:
#                           os dois pedidos simultaneos responderam 204, mas so um cache ficou na lista).
#          semcorte-ignorar sem corte; POST ignored-caches ao alvo em o+35 s; DELETE so no alvo em o+55 s; fim em o+75 s.
# Alvo: isn3, ou isn2 se o isn3 for o coordenador (regra do parada.sh do W-2a).
# Sondas Hot Rod pinadas (SondaA4.java) nos tres nos: 25 ms no alvo, 100 ms nos outros, put e get nos dois caches.
set -u
source /c/Users/Francesco/Development/tcc/wt-a4/runs/a4-teste/scripts/lib-a4.sh
ROT=$1 CASO=$2 SEMENTE=$3
case $CASO in
  corte-ignorar|corte-sessoes|corte-sequencial|corte-seq-detec|corte-seq-maioria) DUR=270;; corte-parar) DUR=150;; corte-controle) DUR=240;;
  semcorte-ignorar|semcorte-sessoes) DUR=110;;
  *) echo "caso desconhecido: $CASO"; exit 2;;
esac
D=$R/$ROT
rm -rf $D; mkdir -p $D
# o "podman machine ssh" pode gravar um arquivo NUL no diretorio corrente: roda fora da worktree
cd /c/Users/FRANCE~1/AppData/Local/Temp/claude/98c9abffb040f610/0786b06c-748d-4829-8e8b-152396a994ac/scratchpad
fase $D "inicio $ROT caso=$CASO semente=$SEMENTE duracao_carga=$DUR base=$(git -C $W rev-parse --short HEAD) jar_sha256=$(sha256sum $JAR | cut -c1-64) xml_sha256=$(sha256sum $C/infinispan-sem-caches.xml | cut -c1-64) compose_sha256=$(sha256sum $C/podman-compose.a4.yml | cut -c1-64) sonda_sha256=$(sha256sum $R/scripts/SondaA4.java | cut -c1-64)"
descer $D/compose-down-antes.txt
subir $D || { fase $D "falha: cluster nao subiu"; descer $D/compose-down.txt; exit 1; }
estado $D subida
criar_caches $D || { fase $D "falha: cria-caches"; descer $D/compose-down.txt; exit 1; }
ALVO=isn3; [ "$(eh_coordenador isn3)" = True ] && ALVO=isn2
OUTROS=$(for n in isn1 isn2 isn3; do [ $n != $ALVO ] && echo -n "$n "; done)
PA=$(porta $ALVO)
fase $D "parametros alvo=$ALVO outros=$OUTROS coordenador_isn1=$(eh_coordenador isn1)"
ignorados_agora $D antes
vigia $D
carga $D $SEMENTE $DUR
O=$(origem $D) || { fase $D "falha: carga sem linha S3"; touch $D/fim-vigia; descer $D/compose-down.txt; exit 1; }
fase $D "origem linha S3 o_ns=$O"
for n in isn1 isn2 isn3; do
  if [ $n = $ALVO ]; then sonda $D $n 25 1200; else sonda $D $n 100 1200; fi
done

CACHES_ACAO="sessions-async counters-async"
case $CASO in corte-sessoes|semcorte-sessoes) CACHES_ACAO="sessions-async";; esac
SEQ=0; case $CASO in corte-sequencial|corte-seq-detec|corte-seq-maioria) SEQ=1;; esac
ignorar() {  # METODO ROTULO: os caches de CACHES_ACAO no alvo, em paralelo (SEQ=0) ou em sequencia (SEQ=1)
  local c
  if [ $SEQ = 1 ]; then
    for c in $CACHES_ACAO; do
      python -I $R/scripts/acao_a4.py $D/acoes.tsv $2 $1 http://127.0.0.1:$PA/rest/v2/server/ignored-caches/$c >> $D/acoes.out 2>&1
    done
  else
    python -I $R/scripts/acao_a4.py $D/acoes.tsv $2 $1 \
      $(for c in $CACHES_ACAO; do echo http://127.0.0.1:$PA/rest/v2/server/ignored-caches/$c; done) >> $D/acoes.out 2>&1
  fi
}
# despejo de threads do alvo pela REST (local ao no), para explicar pedidos presos sob corte
despejo() { curl_adm -m 15 http://127.0.0.1:$PA/rest/v2/server/threads > $D/threads-alvo-$1.txt 2>&1; gzip -n -9 -f $D/threads-alvo-$1.txt; }
esperar_fusao() {
  local t
  for t in $(seq 1 240); do
    [ "$(nos_vistos $ALVO)" = 3 ] && [ "$(nos_vistos isn1)" = 3 ] && { fase $D "fusao vista (tamanho 3 no $ALVO e no isn1)"; return 0; }
    sleep 1
  done
  fase $D "fusao NAO vista em 240 s"; return 1
}

if [ $CASO = semcorte-ignorar ] || [ $CASO = semcorte-sessoes ]; then
  dormir_ate_ns $((O + 35000000000))
  fase $D "ignorar-pedido $ALVO caches=$CACHES_ACAO"
  ignorar POST ignorar & PID_ACAO=$!
  sleep 3; ignorados_agora $D pos-ignorar
  dormir_ate_ns $((O + 55000000000))
  wait $PID_ACAO
  fase $D "desfazer-pedido $ALVO"
  ignorar DELETE desfazer
  fase $D "desfazer-retorno"
  sleep 3; ignorados_agora $D pos-desfazer
  dormir_ate_ns $((O + 75000000000))
else
  dormir_ate_ns $((O + 30000000000))
  marcador $D start $ALVO
  fase $D "corte-inicio $ALVO"
  cortar $D $ALVO $OUTROS
  fase $D "corte-fim rc=$?"
  dormir_ate_ns $((O + 35000000000))
  case $CASO in
    corte-ignorar|corte-sessoes|corte-sequencial|corte-seq-detec|corte-seq-maioria) fase $D "ignorar-pedido $ALVO caches=$CACHES_ACAO"; ignorar POST ignorar & PID_ACAO=$!
      sleep 3; fase $D "despejo-threads-inicio"; despejo mais3s; fase $D "despejo-threads-fim";;
    corte-parar) fase $D "parar-pedido $ALVO"
      python -I $R/scripts/acao_a4.py $D/acoes.tsv parar POST "http://127.0.0.1:$PA/rest/v2/server?action=stop" >> $D/acoes.out 2>&1 &
      PID_ACAO=$! ;;
    corte-controle) fase $D "sem-acao"; PID_ACAO="";;
  esac
  sleep 5; ignorados_agora $D pos-acao
  if [ $CASO = corte-seq-detec ]; then
    # espera o alvo se ver sozinho (leitura local da disponibilidade) e desfaz no proprio alvo, antes da cura
    while [ $(agora_ns) -lt $((O + 118000000000)) ]; do
      [ "$(curl_adm -m 1 "http://127.0.0.1:$PA/rest/v2/caches/sessions-async?action=get-availability" 2>/dev/null)" = DEGRADED_MODE ] && break
      sleep 0.2
    done
    fase $D "deteccao-vista-no-alvo (get-availability)"
    [ -n "$PID_ACAO" ] && wait $PID_ACAO; PID_ACAO=""
    fase $D "desfazer-pedido $ALVO (na deteccao)"
    ignorar DELETE desfazer
    fase $D "desfazer-retorno"
    sleep 3; ignorados_agora $D pos-desfazer-deteccao
  fi
  dormir_ate_ns $((O + 120000000000))
  if [ "$(podman inspect a4-$ALVO --format '{{.State.Status}}')" = running ]; then
    fase $D "cura-inicio $ALVO"; curar $D $ALVO $OUTROS; fase $D "cura-fim rc=$?"
  else
    fase $D "cura-dispensada ($ALVO parado)"
  fi
  marcador $D end $ALVO
  [ -n "$PID_ACAO" ] && wait $PID_ACAO
  if [ $CASO = corte-parar ]; then
    sleep 10
  else
    esperar_fusao
    sleep 15
    ignorados_agora $D pos-fusao
    fase $D "pos-fusao +15 s"
    if [ $CASO = corte-seq-maioria ]; then
      MAI=${OUTROS%% *}
      fase $D "desfazer-pedido $MAI (um DELETE do sessions-async a um no da maioria)"
      python -I $R/scripts/acao_a4.py $D/acoes.tsv desfazer DELETE http://127.0.0.1:$(porta $MAI)/rest/v2/server/ignored-caches/sessions-async >> $D/acoes.out 2>&1
      fase $D "desfazer-retorno"
      sleep 3; ignorados_agora $D pos-desfazer
      sleep 17
    elif [ $CASO = corte-seq-detec ]; then
      sleep 20
    elif [ $CASO != corte-controle ]; then
      fase $D "desfazer-pedido $ALVO"
      ignorar DELETE desfazer
      fase $D "desfazer-retorno"
      sleep 3; ignorados_agora $D pos-desfazer
      sleep 17
    fi
  fi
fi
fase $D "fim-sondas"
touch $D/fim-sondas
sleep 3
# guarda do P-41: se a carga nao terminar em DUR + 120 s depois da origem e o alvo estiver parado, religa o alvo so
# para a leitura final da carga (como o parada.sh do W-2a); registrado em fases.tsv
while [ ! -f $D/exit.txt ]; do
  if [ $(agora_ns) -gt $((O + (DUR + 120) * 1000000000)) ] && [ "$(podman inspect a4-$ALVO --format '{{.State.Status}}')" != running ]; then
    podman start a4-$ALVO > /dev/null 2>&1; fase $D "religado-para-a-leitura-final $ALVO (P-41)"; break
  fi
  sleep 5
done
fase $D "carga $(esperar_carga $D)"
touch $D/fim-vigia
estado $D fim
podman inspect a4-$ALVO --format '{{json .State}}' > $D/alvo-state.json 2>&1
logs $D
descer $D/compose-down.txt
wait
for n in isn1 isn2 isn3; do gzip -n -9 -f $D/sonda-$n.tsv $D/sonda-$n.out; done
gzip -n -9 -f $D/stderr.log
rm -f $D/pid
fase $D "fim $ROT"
