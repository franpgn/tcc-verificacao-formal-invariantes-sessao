#!/bin/bash
# P-64 (06/11/2026): copia do smoke.sh do P1-IC com o ajuste de log (SEM_AJUSTE_LOG=1 desliga) e o driver no fases.tsv.
# P1-IC (06/11/2026): smoke local tipo I-3b do controlador com o AC-7 (ignorar o cache de sessoes no suspeito), na
# ponta da feature/p1-ignorar-cache. Cluster local p1ic (portas 13001-13003), carga S3 ASYNC L+ com varredura e sonda
# de replay, controlador active com o arquivo da calibracao (P1 = AC-7, padrao do G-1). Uma encarnacao por execucao.
#
# Uso: smoke.sh ROTULO CASO SEMENTE
#   CASO = corte   corte do alvo (isn3, ou isn2 se o isn3 for o coordenador) de o+60 s a o+150 s (o = origem das
#                  janelas, meta do observaveis), como no I-3b; o P1 decide e manda o AC-7; carga de 300 s.
#          curto   corte de 25 s (o+60 s a o+85 s), curado antes da deteccao (~45 s): o P1 dispara sob o corte, a guarda
#                  nao ve nada (a gravacao nao alcanca a maioria) e a cura chega com o POST ainda pendente; mede o que
#                  acontece com as listas e a regra suspeita-desfeita; carga de 200 s.
#          falso   sem corte; em o+60 s, o AC-7 e mandado ao isn3 pelo adaptador de producao (SmokeAc7.java), como o
#                  executor mandaria: a guarda tem de ver o cache na lista da maioria e desfazer na hora; carga de 150 s.
# Sondas Hot Rod pinadas (SondaA4.java do A4) nos tres nos: 25 ms no alvo, 100 ms nos outros; vigia das listas de
# ignorados (vigia_p1ic.py, so leituras locais).
set -u
source /c/Users/Francesco/Development/tcc/wt-p1ic-ca/runs/p64-log/scripts/lib-p1ic.sh
ROT=$1 CASO=$2 SEMENTE=$3
case $CASO in corte) DUR=300;; curto) DUR=200;; falso) DUR=150;; *) echo "caso desconhecido: $CASO"; exit 2;; esac
D=$R/$ROT
rm -rf $D; mkdir -p $D
cd /c/Users/FRANCE~1/AppData/Local/Temp/claude/98c9abffb040f610/0786b06c-748d-4829-8e8b-152396a994ac/scratchpad
fase $D "inicio $ROT caso=$CASO semente=$SEMENTE duracao=$DUR base=$(git -C $W rev-parse --short HEAD) jar_sha256=$(sha256sum $JAR | cut -c1-64) cfg_sha256=$(sha256sum $CFG | cut -c1-64) compose_sha256=$(sha256sum $C/podman-compose.p1ic.yml | cut -c1-64)"
descer $D/compose-down-antes.txt
subir $D > $D/subir.txt 2>&1 || { fase $D "falha: cluster nao subiu"; descer $D/compose-down.txt; exit 1; }
estado $D subida
criar_caches $D || { fase $D "falha: cria-caches"; descer $D/compose-down.txt; exit 1; }
if [ "${SEM_AJUSTE_LOG:-0}" != 1 ]; then ajustar_log $D || { fase $D "falha: ajusta-log"; descer $D/compose-down.txt; exit 1; }; fi
fase $D "log-driver $(podman inspect p1ic-isn1 --format '{{.HostConfig.LogConfig.Type}} {{.HostConfig.LogConfig.Size}}')"
ALVO=isn3; [ "$(eh_coordenador isn3)" = True ] && ALVO=isn2
OUTROS=$(for n in isn1 isn2 isn3; do [ $n != $ALVO ] && echo -n "$n "; done)
fase $D "parametros alvo=$ALVO outros=$OUTROS"
vigia $D
carga $D $SEMENTE $DUR active
O=$(origem $D) || { fase $D "falha: sem origem"; touch $D/fim-vigia; descer $D/compose-down.txt; exit 1; }
fase $D "origem o_ns=$O"
for n in isn1 isn2 isn3; do if [ $n = $ALVO ]; then sonda $D $n 25; else sonda $D $n 100; fi; done
if [ $CASO = curto ]; then
  dormir_ate_ns $((O + 60000000000))
  marcador $D start $ALVO
  fase $D "corte-inicio $ALVO"; cortar $D $ALVO $OUTROS; fase $D "corte-fim rc=$?"
  dormir_ate_ns $((O + 85000000000))
  fase $D "cura-inicio $ALVO"; curar $D $ALVO $OUTROS; fase $D "cura-fim rc=$?"
  marcador $D end $ALVO
  dormir_ate_ns $((O + 150000000000))
elif [ $CASO = corte ]; then
  dormir_ate_ns $((O + 60000000000))
  marcador $D start $ALVO
  fase $D "corte-inicio $ALVO"; cortar $D $ALVO $OUTROS; fase $D "corte-fim rc=$?"
  dormir_ate_ns $((O + 150000000000))
  fase $D "cura-inicio $ALVO"; curar $D $ALVO $OUTROS; fase $D "cura-fim rc=$?"
  marcador $D end $ALVO
  dormir_ate_ns $((O + 250000000000))
else
  dormir_ate_ns $((O + 60000000000))
  fase $D "falso-alarme-inicio AC-7 ao $ALVO pelo adaptador"
  "$JAVA" -cp "$(cygpath -w $JAR)" "$(cygpath -w $R/scripts/SmokeAc7.java)" "$ADMIN" 127.0.0.1:$(porta $ALVO) sessions-async \
      > $D/smoke-ac7.tsv 2> $D/smoke-ac7.err
  fase $D "falso-alarme-fim rc=$?"
  dormir_ate_ns $((O + 100000000000))
fi
fase $D "fim-sondas"; touch $D/fim-sondas
fase $D "carga $(esperar_carga $D)"
touch $D/fim-vigia
estado $D fim
logs $D
descer $D/compose-down.txt
wait
for n in isn1 isn2 isn3; do gzip -n -9 -f $D/sonda-$n.tsv $D/sonda-$n.out $D/servidores/$n.log; done
gzip -n -9 -f $D/stderr.log
rm -f $D/pid
fase $D "fim $ROT"
