#!/usr/bin/env bash
# runs/gate-b29/g1.sh -- G-1..G-4 numa janela real de 90 s em isn3. PROPOSTA: NAO executar sem o "go" do Gestor.
set -euo pipefail
source "$(dirname "$0")/g-lib.sh"
[ -t 0 ] || { echo "ABORTAR: sem terminal (o go do Gestor e interativo)" >&2; exit 1; }
read -rp "G-1: go do Gestor registrado no relatorio? [digite GO] " r; [ "$r" = GO ] || { echo "ABORTAR: sem go" >&2; exit 1; }
echo "go $(date -Is) vm=$(hostname -I | awk '{print $1}') sha=$(git rev-parse HEAD)" | tee -a "$LOG/go.log"
N=${REP:-1}; OUT="$LOG/g1-rep$N"; mkdir -p "$OUT"
PID3=$(pid_de isn3); PID1=$(pid_de isn1)
IP1=$($PODMAN inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' isn1)
IP2=$($PODMAN inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' isn2)
health | tee "$OUT/saude-antes.txt"; grep -c 'number_of_nodes":3' "$OUT/saude-antes.txt" | grep -qx 3 || { echo "ABORTAR: cluster fora de size=3" >&2; exit 1; }
sem_clsact isn3
armar 90            # dead-man: --heal em 90+90 = 180 s, por injecao
T3() { sudo nsenter -t "$PID3" -n "$@"; }
# contadores por filtro (o 'show ingress' e o 'show egress' listam OS DOIS filtros neste kernel; separar por offset: at 12 = src/ingress, at 16 = dst/egress)
cont_filtros() { T3 tc -s filter show dev eth0 ingress | awk '/match .* at 12/{d="ingress(src)"} /match .* at 16/{d="egress(dst)"} /Sent .* pkt/{print d, $4"pkt"}' ; }
# extrair_logs: log-completo-* (tudo desde t0-5) e log-vista-* (filtro de vista); tambem chamada nos caminhos de abort
extrair_logs() {
  for n in isn1 isn2 isn3; do
    $PODMAN logs --since "$(date -d @$((t0-5)) -Iseconds)" "$n" > "$OUT/log-completo-$n.txt" 2>&1 || true
    grep -iE 'view|ISPN000094|ISPN000093|merge|partition|DEGRADED' "$OUT/log-completo-$n.txt" > "$OUT/log-vista-$n.txt" || true
  done
}
t0=$(date +%s)
set +e; $INJ --minority isn3 --duration 90 --marker "$OUT/marcadores.txt" > "$OUT/inj.log" 2>&1 & P=$!; set -e
for t in 8 25 45 65 82; do
  while [ $(( $(date +%s) - t0 )) -lt $t ]; do sleep 1; done
  { echo "=== t=+${t}s $(date -Is)"
    echo "-- contadores por filtro"; cont_filtros || true
    echo "-- isn3 -> isn1:7800 (esperado rc=124/1, falha)"; T3 timeout 3 bash -c "exec 3<>/dev/tcp/$IP1/7800"; echo "rc=$?"
    echo "-- isn1 -> isn2:7800 (esperado rc=0)"; sudo nsenter -t "$PID1" -n timeout 3 bash -c "exec 3<>/dev/tcp/$IP2/7800"; echo "rc=$?"
    echo "-- host -> isn3 Hot Rod/REST :11224 (esperado conectar; erro de aplicacao, nao timeout)"
    curl -s -m 5 -o /dev/null -w 'http=%{http_code} t=%{time_total}s\n' --digest -u admin:infinispan http://localhost:11224/rest/v2/cache-managers/default/health || echo "curl rc=$?"
    echo "-- saude 11222/11223/11224"; health
    echo "-- ss -tn isn3 (IPv4/IPv6)"; T3 ss -tn | head -12
  } >> "$OUT/janela.log" 2>&1 || true
done
set +e; wait $P; RC=$?; set -e
echo "injetor rc=$RC (esperado 0)" | tee -a "$OUT/inj.log"
[ "$RC" = 0 ] || { echo "ABORTAR: rc=$RC; dead-man $DMS continua armado" >&2; extrair_logs; exit 1; }
sem_clsact isn3 | tee -a "$OUT/inj.log"; desarmar
# G-4: t_merge = ate size=3 e HEALTHY nos 3 nos, a partir do 'end' do marcador
END_NS=$(awk -F't_epoch_ns=' '/event=end/{print $2}' "$OUT/marcadores.txt" | tail -1)
until [ "$(health | grep -c 'HEALTHY","number_of_nodes":3')" = 3 ]; do sleep 2; [ $(( $(date +%s) - t0 )) -lt 600 ] || { echo "ABORTAR: nao reformou em 600 s" >&2; extrair_logs; exit 1; }; done
echo "t_merge_s=$(( ($(date +%s%N) - END_NS) / 1000000000 )) (desde event=end)" | tee "$OUT/t_merge.txt"
health | tee "$OUT/saude-depois.txt"
# G-3 (A4 de V-B): os logs de vista sao extraidos DEPOIS da fusao (t_merge), nao antes:
# antes dela o merge ainda nao tinha ocorrido e a vista final nao estaria nos logs.
# log-completo-* guarda tudo desde t0-5; log-vista-* e o filtro de mensagens de vista.
extrair_logs
