#!/bin/bash
# B-44.3: funcoes do experimento da AC-6 (GMS.suspect pelo probe do JGroups) no cluster local b443.
# Projeto Podman b443; REST/Hot Rod 12444..12446; diag (probe TCP) 12447..12449 -> 7500 de cada no.
# Relogio: host Windows, epoch em ms (date +%s%3N). Linhas de fase em $R/fases.tsv.
R=/c/Users/Francesco/Development/tcc/wt-b44-3/runs/b44-3
AUTH="admin:infinispan"
JAR=/c/Users/Francesco/Development/tcc/wt-b44-1/workload/target/session-workload-0.1.0-SNAPSHOT.jar
SRV=127.0.0.1:12444,127.0.0.1:12445,127.0.0.1:12446
PY=python
porta() { case $1 in isn1) echo 12444;; isn2) echo 12445;; isn3) echo 12446;; esac; }
pdiag() { case $1 in isn1) echo 12447;; isn2) echo 12448;; isn3) echo 12449;; esac; }
ms() { date +%s%3N; }
utc() { date -u +%FT%T.%3NZ; }
fase() { printf '%s\t%s\t%s\n' "$(utc)" "$(ms)" "$*" >> $R/fases.tsv; echo "== $(utc) $*"; }

# rest ROTULO NO CAMINHO -> corpo em corpos/ROTULO.txt
rest() { local out=$R/corpos/$1.txt
  curl -s -m 5 --digest -u $AUTH -o "$out" -w '%{http_code}' "http://127.0.0.1:$(porta $2)$3"; }

# nome NO: nome logico JGroups (node_address)
nome() { curl -s -m 5 --digest -u $AUTH "http://127.0.0.1:$(porta $1)/rest/v2/container" | $PY -c 'import json,sys; print(json.load(sys.stdin)["node_address"])'; }
coord() { curl -s -m 5 --digest -u $AUTH "http://127.0.0.1:$(porta $1)/rest/v2/container" | $PY -c 'import json,sys; print(json.load(sys.stdin)["coordinator_address"])'; }
tamanho() { curl -s -m 3 --digest -u $AUTH "http://127.0.0.1:$(porta $1)/rest/v2/container" 2>/dev/null | $PY -c 'import json,sys
try: print(json.load(sys.stdin)["cluster_size"])
except Exception: print("X")'; }
avail() { curl -s -m 3 --digest -u $AUTH "http://127.0.0.1:$(porta $1)/rest/v2/caches/$2?action=get-availability"; }

# saudavel: os 3 nos com cluster_size 3 e os 4 caches AVAILABLE; espera ate N s
saudavel() { local lim=${1:-180} t=0
  while [ $t -lt $lim ]; do local ok=1
    for n in isn1 isn2 isn3; do [ "$(tamanho $n)" = 3 ] || ok=0
      for c in sessions sessions-async; do [ "$(avail $n $c)" = AVAILABLE ] || ok=0; done; done
    [ $ok = 1 ] && { echo "saudavel apos ${t}s"; return 0; }
    sleep 2; t=$((t+2)); done; echo "NAO saudavel apos ${lim}s"; return 1; }

# sobe COMPOSE ORDEM...: sobe os nos um a um na ordem dada (o primeiro vira coordenador)
sobe() { local f=$1; shift; fase "sobe $f ordem $*"
  for n in "$@"; do (cd $R/cluster && podman-compose -p b443 -f $f up -d $n >> $R/compose.log 2>&1)
    for i in $(seq 1 60); do [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' --digest -u $AUTH http://127.0.0.1:$(porta $n)/rest/v2/container)" = 200 ] && break; sleep 2; done
  done
  saudavel 180; nomes; }
desce() { local f=${1:-podman-compose.b443-diag.yml}; fase "desce $f"
  (cd $R/cluster && podman-compose -p b443 -f $f down >> $R/compose.log 2>&1); }

# nomes: le e grava os nomes logicos, o coordenador, o PID e o IP de cada no (para o corte)
nomes() { N1=$(nome isn1); N2=$(nome isn2); N3=$(nome isn3); CO=$(coord isn1)
  for n in isn1 isn2 isn3; do
    eval "PID_$n=$(podman machine ssh "podman inspect -f '{{.State.Pid}}' b443-$n" 2>/dev/null | tr -d '\r')"
    eval "IP_$n=$(podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' b443-$n)"
  done
  fase "nomes isn1=$N1 isn2=$N2 isn3=$N3 coordenador=$CO pid=$PID_isn1/$PID_isn2/$PID_isn3 ip=$IP_isn1/$IP_isn2/$IP_isn3"; }

# corte do isn3 por nftables no netns do container (mesmo efeito do inject-partition.sh: descarte nos
# dois sentidos so para os IPs dos pares; REST/Hot Rod do host continuam). Uma unica chamada ssh.
cortar() { local t1=$(ms)
  podman machine ssh "nsenter -t $PID_isn3 -n nft add table inet b443 && \
    nsenter -t $PID_isn3 -n nft add chain inet b443 ent '{ type filter hook input priority 0; }' && \
    nsenter -t $PID_isn3 -n nft add chain inet b443 sai '{ type filter hook output priority 0; }' && \
    nsenter -t $PID_isn3 -n nft add rule inet b443 ent ip saddr '{ $IP_isn1, $IP_isn2 }' counter drop && \
    nsenter -t $PID_isn3 -n nft add rule inet b443 sai ip daddr '{ $IP_isn1, $IP_isn2 }' counter drop" 2>&1 | grep -v cgroups
  local t2=$(ms); T0=$t2; fase "CORTE isn3 pedido=$t1 aplicado=$t2"; }
curar() { local t1=$(ms)
  podman machine ssh "nsenter -t $PID_isn3 -n nft list table inet b443; nsenter -t $PID_isn3 -n nft delete table inet b443" 2>&1 | grep -v cgroups > $R/corpos/nft-$ROT.txt
  local t2=$(ms); fase "CURA isn3 pedido=$t1 aplicado=$t2"; }

# probe ROT ARGS...: chama o ProbeTcp e grava em probe/ROT.tsv
probe() { local rot=$1; shift; mkdir -p $R/probe
  "$JAVA_HOME/bin/java" -cp "$(cygpath -w $R/scripts/classes)" ProbeTcp "$@" | tee -a $R/probe/$rot.tsv; }
# acao QUAIS: quatro pedidos (ambos), so maioria, so isolado
acao() { local q=$1 a=()
  case $q in ambos|maioria) a+=("127.0.0.1:12447=op=GMS.suspect[$N3]" "127.0.0.1:12448=op=GMS.suspect[$N3]");; esac
  case $q in ambos|isolado) a+=("127.0.0.1:12449=op=GMS.suspect[$N1]" "127.0.0.1:12449=op=GMS.suspect[$N2]");; esac
  fase "ACAO $q inicio"; probe acao-$ROT ${PASSARGS:-} "${a[@]}"; fase "ACAO $q fim"; }

# carga ROT sync|async SEMENTE DURACAO (fundo)
carga() { local d=$R/carga/$1; mkdir -p $d
  ( "$JAVA_HOME/bin/java" -jar "$JAR" --scenario S3 --replication $2 --reset-state on --rep 1 \
      --duration $4 --seed $3 --servers $SRV --rest-url http://127.0.0.1:12444 --warmup-min-sec 5 \
      --csv-dir "$(cygpath -w $d)" > $d/stdout.log 2> $d/stderr.log; echo "exit=$?" > $d/exit.txt ) &
  CPID=$!; echo $CPID > $d/pid; }

salvalogs() { mkdir -p $R/servidores/$1
  for n in isn1 isn2 isn3; do podman logs --since "$2" b443-$n > $R/servidores/$1/$n.log 2>&1; done; }

# espera_ate MS_EPOCH
espera_ate() { local agora=$(ms); local d=$(( $1 - agora )); [ $d -gt 0 ] && sleep $(awk "BEGIN{print $d/1000}"); }

# execucao ROT MODO SEMENTE CORTE(sim|nao) ACAO(nenhuma|ambos|maioria|isolado) [DUR=130]
execucao() { ROT=$1; local modo=$2 sem=$3 corte=$4 ac=$5 dur=${6:-130}
  local inicio=$(date -u +%FT%T.%3NZ)
  fase "EXEC $ROT inicio modo=$modo corte=$corte acao=$ac semente=$sem dur=$dur"
  mkdir -p $R/sonda; $PY $R/scripts/sonda.py "$(cygpath -w $R/sonda/$ROT.tsv)" $((dur+60)) 0.2 &
  local SPID=$!
  carga $ROT $modo $sem $dur
  sleep 25
  if [ $corte = sim ]; then cortar; else T0=$(ms); fase "T0 (sem corte) $T0"; fi
  espera_ate $((T0+5000))
  if [ $ac != nenhuma ]; then acao $ac; else fase "SEM ACAO (controle) t_nominal=$(ms)"; fi
  if [ $corte = sim ]; then espera_ate $((T0+60000)); curar; fi
  wait $CPID; fase "CARGA $ROT fim $(cat $R/carga/$ROT/exit.txt)"
  saudavel 120
  kill $SPID 2>/dev/null; wait $SPID 2>/dev/null
  salvalogs $ROT "$inicio"
  fase "EXEC $ROT fim"; }
