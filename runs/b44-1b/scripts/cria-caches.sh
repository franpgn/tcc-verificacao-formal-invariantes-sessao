#!/bin/bash
# B-44.1b contorno (b) do P-30: cria pela REST os 4 caches do XML da base num cluster subido com
# infinispan-sem-caches.xml. JSON sem o envoltorio do nome (o nome "counters" colide com o elemento
# <counters> do parser, ISPN029520). remote-timeout explicito nos SYNC (o XML da base resulta em 17500;
# criado pela REST sem o atributo, o valor e o padrao 15000).
# Uso: cria-caches.sh http://127.0.0.1:12441 admin:infinispan
U=${1:-http://127.0.0.1:11222}; A=${2:-admin:infinispan}
for par in sessions:SYNC:17500 counters:SYNC:17500 sessions-async:ASYNC: counters-async:ASYNC:; do
  IFS=: read -r nome modo rt <<< "$par"
  corpo="{\"distributed-cache\":{\"owners\":\"2\",\"segments\":\"64\",\"mode\":\"$modo\",${rt:+\"remote-timeout\":\"$rt\",}\"statistics\":true,\"locking\":{\"concurrency-level\":\"1000\",\"acquire-timeout\":\"15000\",\"striping\":false},\"state-transfer\":{\"enabled\":true,\"timeout\":\"60000\"},\"partition-handling\":{\"when-split\":\"DENY_READ_WRITES\",\"merge-policy\":\"PREFERRED_NON_NULL\"}}}"
  code=$(curl -s -o /dev/null -w '%{http_code}' --digest -u "$A" -X POST -H 'Content-Type: application/json' --data "$corpo" "$U/rest/v2/caches/$nome")
  echo "$nome $code"; [ "$code" = 200 ] || exit 1
done
