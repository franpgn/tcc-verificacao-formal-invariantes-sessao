#!/bin/bash
# W-4 smoke, diagnostico do A7 (depois do bloco A): o primario que o cliente Hot Rod calcula (DiagChave.java) contra o
# que a REST ?action=distribution diz e contra onde o dado fica de fato (entradas em memoria por no). Uma chave por
# cache novo (diag-1..3, mesma configuracao do canario-async), para que as entradas por no mostrem os donos reais.
source /c/Users/Francesco/Development/tcc/wt-w4/runs/w4-smoke/scripts/lib-w4s.sh
D=$R/D
mkdir -p $D/caches
fase $D "diagnostico inicio"
descer $D/compose-down-antes.txt
subir $D || exit 1
for i in 1 2 3; do cp $W/cluster/caches/canario-async.json $D/caches/diag-$i.json; done
CRIA_CACHES_SENHA=$SENHA bash $W/scripts/cria-caches.sh --url http://127.0.0.1:12611 --conferir "$ADMIN" \
  --dir "$D/caches" diag-1 diag-2 diag-3 > $D/cria-caches.tsv 2> $D/cria-caches.err; echo "cria rc=$?"
sleep 15
CH=("lag:127.0.0.1:12611#1" "lag:127.0.0.1:12612" "lag:127.0.0.1:12613")
for i in 1 2 3; do
  k=${CH[$((i - 1))]}
  "$JAVA" -Dsenha=$SENHA -Dgravar=true -cp "$(cygpath -w $JAR)" "$(cygpath -w $R/scripts/DiagChave.java)" diag-$i 5 "$k" \
    > $D/diag-$i-cliente.tsv 2> $D/diag-$i-cliente.err
  hex=$(python -I $R/scripts/chaves.py --hex "$k" | tr -d '\r' | tr A-F a-f)
  for no in isn1 isn2 isn3; do
    curl_adm -w '\n%{http_code}\n' -H 'Key-Content-Type: application/octet-stream; encoding=hex' \
      "http://127.0.0.1:$(porta $no)/rest/v2/caches/diag-$i/0x$hex?action=distribution" > $D/diag-$i-rest-chave-via-$no.txt
  done
  curl_adm -w '\n%{http_code}\n' "http://127.0.0.1:12611/rest/v2/caches/diag-$i?action=distribution" > $D/diag-$i-rest-cache.txt
  echo "== diag-$i ($k)"; cat $D/diag-$i-cliente.tsv; head -1 $D/diag-$i-rest-chave-via-isn1.txt; head -1 $D/diag-$i-rest-cache.txt
done
logs $D
descer $D/compose-down.txt
fase $D "diagnostico fim"
