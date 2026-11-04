#!/usr/bin/env bash
# runs/vm-s5-2026-10-28/s5.sh -- involucro do S5 (L-1 e L-2) na VM. NAO injeta nada por conta propria e NAO digita o GO.
#
# Uso (dentro do tmux aberto sob flock, em ~/tcc-gate):   bash runs/vm-s5-2026-10-28/s5.sh --boot-esperado <uuid>
# Nenhum boot_id fica no codigo (Q-11, 2026-11-04, como no s6.sh/s7.sh): o esperado vem por argumento (o boot_id lido pelo
# coordenador na abertura) e tem de ser igual ao atual no inicio e no fim. A queda de energia de 2026-11-04 reiniciou a VM e o
# cluster foi recriado (runs/gate-b29/recriar.sh, 19:14:06Z): o S5 nao roda mais no cluster do S4.
# Envolve runs/gate-netdegrade/l1.sh, que e VERBATIM do roteiro e nao e editado. As coletas que no l1.sh sao so comentario
# (l1.sh:8-13) sao feitas aqui, como pede decisoes-senior-2026-10-16 §5.6 e a V-S4 ("O que o S5 precisa"):
#   0. pre-condicoes (TCC_DIR, tmux, terminal, lock OCUPADO, sudo -n, uname -r, boot_id = --boot-esperado, HEAD, arvore
#      limpa (so runs/gate-b29/go.log com linhas 'go ' acrescentadas, Q-12), hashes,
#      ambiente sem LOCK_DIR/PODMAN/INJ/..., java e jar do S4, L1.log e marcadores.txt inexistentes, 0 timers e 0 units
#      tcc-heal, IPs e PID de isn3, guarda de netns, JGroups sem IPv6, NIC da VM sem prio/netem/clsact) e estado do cluster
#      atual (IDs, StartedAt, IPs e ultima vista so registrados; nenhuma vista e exigida);
#   1. linha de base SEM F4 (BASE_S s): REST /health nas 3 portas e conexao TCP ao ssh da VM a cada SAUDE_S s, ping do host
#      a isn3 e a sonda Hot Rod pinada em isn3 (127.0.0.1:11224), que remove a chave ao fim (remove-final);
#   2. portao 5.1 pre (portao51.sh, .log/.csv/avaliacao automatica); se falhar, PARA;
#   3. TcpRetransSegs dos 3 netns; coletor em segundo plano (espera o event=start do injetor); l1.sh neste terminal: o prompt
#      do GO e o do proprio l1.sh (exigir_go), digitado pelo Gestor. Durante a janela o coletor grava >= 2 amostras de
#      tc -s qdisc e tc -s filter de isn3, ping do host a isn3, saude/latencia a cada SAUDE_S s e a sonda Hot Rod;
#   4. depois do L-1: TcpRetransSegs, NIC, timers/units, vistas dos 3 nos desde o inicio do L-1, copias de L1.log,
#      marcadores.txt e go.log com sha256; portao 5.1 pos (fim >= fim do L-1 + 345 s); boot_id do fim; avaliacao.
# Porta: o REST e o Hot Rod de isn3 sao 127.0.0.1:11224. O comentario de l1.sh:10 diz 11222, que e isn1 (controle aqui).
# Dead-man do L-1: armar 60 => --on-active=150 s, mas o timer usa AccuracySec=1min: a cura automatica pode vir ~210 s
# depois de armar. Cura manual (em ~/tcc-gate): PODMAN='sudo podman' bash scripts/inject-netdegrade.sh --heal
# Saidas: runs/vm-s5-2026-10-28/vm-<UTC>/ (console em s5-console.log; rc e fim de cada passo em rc.txt).
# Saida: 0 valido; 1 argumento ou pre-condicao; 2 linha de base; 3 portao pre; 4 l1.sh (ver o aviso do dead-man); 5 portao pos;
# 6 avaliacao INVALIDO (ver 08-avaliacao.txt).
# Outros modos: --hashes (tabela), --avaliar-portao <log>, --avaliar <dir vm-UTC> (refaz a avaliacao, tambem no host).
# S5_TESTE=1 so nos testes com shims (s5.test.sh): permite trocar limites, tempos, o arquivo do boot_id, jar e a tabela de
# hashes (o boot_id esperado vem sempre do argumento).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="$AQUI/s5.sh"
TESTE="${S5_TESTE:-0}"

KERNEL_ESPERADO=6.8.0-147-generic
BOOT_ID_FILE=/proc/sys/kernel/random/boot_id
LOCK=/var/lock/tcc-vm.lock
JAR_ESPERADO=3594c690cfacd2be1f88e58d2a65a4cd944a06688abcb9ae5b4d3df2f7dc7bee   # o jar da REP=3 e do S4 (V-S4)
PORTA_ISN3=11224          # REST e Hot Rod de isn3 (endpoint unico do Infinispan 15)
PORTA_CONTROLE=11222      # isn1: controle
PORTAO_MIN_S=130; PORTAO_FOLGA_S=345; PORTAO_BOA_S=120
BASE_S=60                 # linha de base sem F4
SAUDE_S=2                 # intervalo nominal do saude-log (inteiro: tambem e o --intervalo-s da sonda)
PING_BASE_N=300           # ping -i 0.2: 60 s
PING_JANELA_N=250         # 50 s, dentro da janela de ~60 s
TC_AMOSTRAS="10 30 50"    # s desde o event=start
ESPERA_START_S=900        # o coletor espera o GO ate 15 min
POS_FIM_S=30              # o coletor segue 30 s depois do event=end
L1_ESPERA_COLETOR_S=120
PING_N=300; PERDA_MIN=39; PERDA_MAX=81; LOSS=0.20   # §5.6: 39 a 81 perdas em 300 (3 sigma de 20 %)
# Limiares da regra de latencia (docs/rastreabilidade-limiares-s5-s7-2026-11-04.md, 4.1 e 4.2; decisao do Gestor, 2026-11-04):
FOLGA_LAT_S=0             # mediana durante <= p95 da base (§5.6: "latencia dentro da dispersao da linha de base")
BASE_MIN_N=20             # amostras minimas por fonte na base: com n < 20 o p95 por posto mais proximo (stats) e o maximo
# Referencia de hashes: v4c (902654b), como no S4. l-lib.sh e inject-netdegrade.sh = VM no S2
# (runs/vm-s2-2026-10-27/16-hashes-boot-pos-l0.log); l1.sh = VM no S1 (runs/vm-s1-2026-10-27/04-hashes-scripts.log).
HASHES_ESPERADOS='4ebdd8a34a7d5c1e6981138272a4f0b90a67c44dd8626e9521884e185297abb1  scripts/inject-netdegrade.sh
9bdd1faeca0399d11886ee5c77189825c18f5bf69d78797c579c1fd71e9d73fd  runs/gate-netdegrade/l-lib.sh
9f3deacc6c408d2e70125ede52d409d87b876202cd33b370277b6330775e1253  runs/gate-netdegrade/l1.sh
2598b37d4969b1bd717ddbe514110fb6a0077e1332e38c1b76c5caea61802338  runs/vm-s2-2026-10-27/portao51.sh'
if [ "${1:-}" = --hashes ]; then printf '%s\n' "$HASHES_ESPERADOS"; exit 0; fi
if [ "$TESTE" = 1 ]; then
  BOOT_ID_FILE="${S5_BOOT_ID_FILE:-$BOOT_ID_FILE}"
  JAR_ESPERADO="${S5_JAR_ESPERADO:-$JAR_ESPERADO}"
  PORTAO_MIN_S="${S5_PORTAO_MIN_S:-$PORTAO_MIN_S}"; PORTAO_FOLGA_S="${S5_PORTAO_FOLGA_S:-$PORTAO_FOLGA_S}"
  PORTAO_BOA_S="${S5_PORTAO_BOA_S:-$PORTAO_BOA_S}"
  BASE_S="${S5_BASE_S:-$BASE_S}"; SAUDE_S="${S5_SAUDE_S:-$SAUDE_S}"
  PING_BASE_N="${S5_PING_BASE_N:-$PING_BASE_N}"; PING_JANELA_N="${S5_PING_JANELA_N:-$PING_JANELA_N}"
  TC_AMOSTRAS="${S5_TC_AMOSTRAS:-$TC_AMOSTRAS}"; ESPERA_START_S="${S5_ESPERA_START_S:-$ESPERA_START_S}"
  POS_FIM_S="${S5_POS_FIM_S:-$POS_FIM_S}"; L1_ESPERA_COLETOR_S="${S5_L1_ESPERA_COLETOR_S:-$L1_ESPERA_COLETOR_S}"
  [ -z "${S5_HASHES:-}" ] || HASHES_ESPERADOS="$(cat "$S5_HASHES")"
  BASE_MIN_N="${S5_BASE_MIN_N:-$BASE_MIN_N}"   # so o teste (base de 7 s); o --avaliar le o valor de parametros.txt
fi

# ---- avaliacao do .log do portao51.sh (criterios da §5.1; o lock deve estar OCUPADO por este flock). Igual a do s4.sh.
avaliar_portao() { # avaliar_portao <log>
  local f="$1" e=0 l ruins boa n
  falha() { echo "FALHA portao: $*"; e=1; }
  l=$(grep -m1 '^amostras=' "$f") || { echo "FALHA portao: sem a linha amostras="; return 1; }
  ruins=$(sed -n 's/.*amostras_ruins=\([0-9]*\).*/\1/p' <<<"$l"); boa=$(sed -n 's/.*maior_sequencia_boa_s=\([0-9]*\).*/\1/p' <<<"$l")
  [ "$ruins" = 0 ] || falha "amostras_ruins=${ruins:-?}"
  [ -n "$boa" ] && [ "$boa" -ge "$PORTAO_BOA_S" ] || falha "maior_sequencia_boa_s=${boa:-?} < $PORTAO_BOA_S"
  grep -q 'ultima_sequencia_boa_desde=NENHUMA' <<<"$l" && falha "a ultima amostra nao foi boa"
  n=$(grep -cE '^size (sessions|counters) porta 1122[234]: 0$' "$f"); [ "$n" = 6 ] || falha "caches com tamanho 0: $n de 6"
  grep -qx 'timers tcc-heal: 0 units tcc-heal: 0' "$f" || falha "timers/units tcc-heal diferentes de 0"
  n=$(grep -cE '^isn[123] proibidos\(clsact\|prio\|netem\|ingress\)=0 eth0_noqueue=1$' "$f"); [ "$n" = 3 ] || falha "netns limpos (noqueue, sem clsact/prio/netem/ingress): $n de 3"
  n=$(grep -cE '^isn[123] tc filter eth0: \[\]$' "$f"); [ "$n" = 3 ] || falha "netns sem filtros: $n de 3"
  grep -qx 'lock: OCUPADO' "$f" || falha "lock nao esta OCUPADO (o s5.sh deve rodar dentro do flock)"
  n=$(grep -cE '^isn[123] mudancas de vista nos ultimos 5 min \(.*\): 0$' "$f"); [ "$n" = 3 ] || falha "nos sem mudanca de vista em 5 min: $n de 3"
  grep -q '^fim ' "$f" || falha "portao nao chegou ao fim"
  [ "$e" = 0 ] && echo "PORTAO 5.1: OK (amostras_ruins=0, maior_sequencia_boa_s=$boa)"
  return "$e"
}
if [ "${1:-}" = --avaliar-portao ]; then avaliar_portao "$2"; exit $?; fi

# ---- utilitarios de avaliacao (so awk/sed/sort: rodam tambem no Git Bash do host)
stats() { # stats <arquivo, um numero por linha> -> "n mediana p95 max"
  sort -g "$1" | awk '{v[NR]=$1} END { if (NR == 0) { print "0 - - -"; exit }
    k = int(0.95 * NR); if (k < 0.95 * NR) k++; if (k < 1) k = 1
    printf "%d %.6f %.6f %.6f\n", NR, v[int((NR + 1) / 2)], v[k], v[NR] }'; }
col() { # col <csv> <fonte> <campo> <ini_ns> <fim_ns>: valores do campo nas linhas da fonte com ini <= t <= fim
  awk -F, -v f="$2" -v c="$3" -v a="$4" -v b="$5" 'NR > 1 && $2 == f && $1 + 0 >= a + 0 && $1 + 0 <= b + 0 { print $c }' "$1" 2>/dev/null; }
hexip() { local IFS=.; set -- $1; printf '%02x%02x%02x%02x' "$1" "$2" "$3" "$4"; }
rtts() { grep -o 'time=[0-9.]*' "$1" 2>/dev/null | cut -d= -f2 | awk '{ printf "%.6f\n", $1 / 1000 }'; }
pingtr() { sed -n 's/^\([0-9]*\) packets transmitted, \([0-9]*\) received.*/\1 \2/p' "$1" 2>/dev/null | head -1; }

# ---- avaliacao automatica do S5 (§5.6 + V-S4: boot_id e intervalo do saude-log). exit 0 = VALIDO
avaliar() { # avaliar <dir vm-UTC>
  local D="$1" NF=0 NOK=0 TMPA
  ok_() { echo "OK: $*"; NOK=$((NOK + 1)); }
  fa()  { echo "FALHA: $*"; NF=$((NF + 1)); }
  res() { echo "RESULTADO: $*"; }
  lei() { echo "LEITURA HUMANA: $*"; }
  [ -s "$D/parametros.txt" ] || { echo "FALHA: sem $D/parametros.txt"; echo "S5: INVALIDO (1 falha)"; return 1; }
  pv() { sed -n "s/^$1=//p" "$D/parametros.txt" | head -1; }
  local SS PN PMIN PMAX LO FOLGA MINB BOOT IP1 IP2 S_NS E_NS ns ne bi bf lp par pct q sd sent drop nv f t qq m fl esp
  SS=$(pv SAUDE_S); PN=$(pv PING_N); PMIN=$(pv PERDA_MIN); PMAX=$(pv PERDA_MAX); LO=$(pv LOSS); FOLGA=$(pv FOLGA_LAT_S); MINB=$(pv BASE_MIN_N)
  BOOT=$(pv BOOT_ESPERADO); IP1=$(pv IP_isn1); IP2=$(pv IP_isn2)
  TMPA=$(mktemp -d)
  echo "== avaliacao do S5 $(date -Is) sobre $D"

  [ -n "$MINB" ] || { fa "parametros.txt sem BASE_MIN_N (minimo de amostras da base)"; MINB=999999999; }
  # 1. boot_id no inicio e no fim
  bi=$(sed -n 's/^boot_id: //p' "$D/00-pre.log" 2>/dev/null | head -1); bf=$(sed -n 's/^boot_id fim: \([^ ]*\).*/\1/p' "$D/07-boot-fim.txt" 2>/dev/null | head -1)
  if [ -n "$bi" ] && [ "$bi" = "$BOOT" ] && [ "$bf" = "$BOOT" ]; then ok_ "boot_id igual no inicio e no fim ($bi)"
  else fa "boot_id inicio=${bi:-?} fim=${bf:-?} esperado=$BOOT (reboot da VM ou do host: o bloco nao vale)"; fi

  # 2. rc do l1.sh e do injetor
  grep -q '^l1 rc=0 ' "$D/rc.txt" 2>/dev/null && ok_ "l1.sh rc=0 (rc.txt)" || fa "l1.sh sem rc=0 em rc.txt"
  grep -qx 'rc=0 (esperado 0)' "$D/l1/L1.log" 2>/dev/null && ok_ "injetor rc=0 (L1.log)" || fa "injetor sem 'rc=0 (esperado 0)' em l1/L1.log"

  # 3. marcadores: um start e um end
  ns=$(grep -c 'fault=F4 node=isn3 event=start' "$D/l1/marcadores.txt" 2>/dev/null); ne=$(grep -c 'fault=F4 node=isn3 event=end' "$D/l1/marcadores.txt" 2>/dev/null)
  S_NS=$(sed -n 's/.*event=start t_epoch_ns=\([0-9]*\).*/\1/p' "$D/l1/marcadores.txt" 2>/dev/null | head -1)
  E_NS=$(sed -n 's/.*event=end t_epoch_ns=\([0-9]*\).*/\1/p' "$D/l1/marcadores.txt" 2>/dev/null | head -1)
  if [ "${ns:-0}" = 1 ] && [ "${ne:-0}" = 1 ] && [ -n "$S_NS" ] && [ -n "$E_NS" ] && [ "$E_NS" -gt "$S_NS" ]; then
    ok_ "marcadores: 1 start e 1 end; janela de $(awk -v a="$S_NS" -v b="$E_NS" 'BEGIN { printf "%.3f", (b - a) / 1e9 }') s"
  else fa "marcadores: start=${ns:-0} end=${ne:-0} (esperado 1 e 1, end > start)"; S_NS=${S_NS:-0}; E_NS=${E_NS:-0}; fi

  # 4. perda observada pelo ping do injetor (2 pares, n = PING_N): perdas = pct * n / 100
  lp=$(sed -n 's/.*perda observada isn3 -> \(isn[0-9]*\): \([0-9.]*\)%.*/\1 \2/p' "$D/l1/L1.log" 2>/dev/null)
  [ "$(printf '%s\n' "$lp" | grep -c .)" = 2 ] || fa "perda observada: esperadas 2 linhas (isn1, isn2) em l1/L1.log, obtidas $(printf '%s\n' "$lp" | grep -c .)"
  while read -r par pct; do
    [ -n "${par:-}" ] || continue
    q=$(awk -v p="$pct" -v n="$PN" 'BEGIN { printf "%d", p * n / 100 + 0.5 }')
    if [ "$q" -ge "$PMIN" ] && [ "$q" -le "$PMAX" ]; then ok_ "perda observada isn3 -> $par: $pct% = $q perdas em $PN (aceito $PMIN a $PMAX)"
    else fa "perda observada isn3 -> $par: $pct% = $q perdas em $PN (fora de $PMIN a $PMAX)"; fi
  done <<<"$lp"

  # 5. contadores do netem em isn3 no fim da janela (L1.log): dropped > 0 e razao dentro de 3 sigma binomial (piso 2/n)
  sd=$(awk '/contadores de isn3/ { c = 1 } c && /qdisc netem 30:/ { f = 1; next } f && /Sent/ { gsub(",", "", $7); print $4, $7; exit }' "$D/l1/L1.log" 2>/dev/null)
  sent=${sd% *}; drop=${sd#* }
  if [[ "$sent" =~ ^[0-9]+$ && "$drop" =~ ^[0-9]+$ ]] && [ "$drop" -gt 0 ] && awk -v s="$sent" -v d="$drop" -v e="$LO" 'BEGIN {
        n = s + d; r = d / n; t = 3 * sqrt(e * (1 - e) / n); if (t < 2 / n) t = 2 / n; x = r - e; if (x < 0) x = -x; exit !(x <= t) }'; then
    ok_ "contadores de isn3: dropped/(Sent+dropped) = $drop/$((sent + drop)) = $(awk -v s="$sent" -v d="$drop" 'BEGIN { printf "%.4f", d / (s + d) }') (perto de $LO, 3 sigma)"
  else fa "contadores de isn3: Sent=${sent:-?} dropped=${drop:-?} (dropped > 0 e razao a 3 sigma de $LO)"; fi

  # 6. amostras de tc -s qdisc/filter durante a janela: netem 30 com trafego e so os filtros u32 -> 1:3 dos 2 pares
  esp=$(printf '%s\n' "$(hexip "$IP1")" "$(hexip "$IP2")" | sort -u | paste -sd' ')
  nv=0
  for f in "$D"/janela/tc-*.txt; do
    [ -e "$f" ] || continue
    t=$(sed -n 's/^t_epoch_ns=\([0-9]*\).*/\1/p' "$f" | head -1)
    qq=$(awk '/^## tc -s qdisc/ { s = 1; next } /^## / { s = 0 } s && /^qdisc netem 30:/ { g = 1; next } s && g && /Sent/ { gsub(",", "", $7); print $4 + $7; exit }' "$f")
    m=$(awk '/^## tc -s filter/ { s = 1; next } s' "$f" | grep -o 'match [0-9a-f]*/ffffffff at 16' | awk '{ print $2 }' | cut -d/ -f1 | sort -u | paste -sd' ')
    fl=$(awk '/^## tc -s filter/ { s = 1; next } s' "$f" | grep -o 'flowid [0-9a-f:]*' | sort | uniq -c | awk '{ printf "%s=%s ", $3, $1 }')
    if [ -n "$t" ] && awk -v t="$t" -v a="$S_NS" -v b="$E_NS" 'BEGIN { exit !(t + 0 >= a + 0 && t + 0 <= b + 0) }' \
       && [ "${qq:-0}" -gt 0 ] && [ "$m" = "$esp" ] && [ "$fl" = "1:3=2 " ]; then nv=$((nv + 1))
      echo "  amostra $(basename "$f"): netem 30 Sent+dropped=$qq, filtros u32 $m -> $fl"
    else echo "  amostra $(basename "$f") recusada: t=${t:-?} dentro da janela? netem Sent+dropped=${qq:-?} match=[$m] (esperado [$esp]) flowid=[$fl]"; fi
  done
  [ "$nv" -ge 2 ] && ok_ "$nv amostras de tc -s qdisc/filter validas durante a janela" || fa "$nv amostras de tc -s qdisc/filter validas durante a janela (minimo 2)"

  # 7. latencias: mediana durante <= p95 da base + FOLGA (REST de isn3, porta Hot Rod de isn3, REST de controle, ssh, ping)
  lat() { # lat <rotulo> <arq-base> <arq-durante>
    local nb mb pb xb nd md pd xd
    read -r nb mb pb xb <<<"$(stats "$2")"; read -r nd md pd xd <<<"$(stats "$3")"
    if [ "$nb" -ge "$MINB" ] && [ "$nd" -ge 1 ] && awk -v d="$md" -v p="$pb" -v f="$FOLGA" 'BEGIN { exit !(d <= p + f) }'; then
      ok_ "$1: mediana durante $md s <= p95 da base $pb s + $FOLGA s (n base=$nb, durante=$nd; max durante $xd s)"
    else fa "$1: mediana durante ${md} s > p95 da base ${pb} s + $FOLGA s, ou amostras insuficientes (n base=$nb >= $MINB, durante=$nd >= 1)"; fi; }
  serie() { # serie <fonte> <campo> <saida>: base inteira e janela [start, end]
    col "$D/base/saude.csv" "$1" "$2" 0 9e30 | grep -E '^[0-9.]+$' > "$TMPA/$3.b"
    col "$D/janela/saude.csv" "$1" "$2" "$S_NS" "$E_NS" | grep -E '^[0-9.]+$' > "$TMPA/$3.d"; }
  serie "$(pv PORTA_ISN3)" 5 rest3;   lat "REST de isn3 ($(pv PORTA_ISN3)) time_total" "$TMPA/rest3.b" "$TMPA/rest3.d"
  serie "$(pv PORTA_ISN3)" 4 hr3;     lat "conexao TCP a porta Hot Rod de isn3 ($(pv PORTA_ISN3)) time_connect" "$TMPA/hr3.b" "$TMPA/hr3.d"
  serie "$(pv PORTA_CONTROLE)" 5 rc1; lat "REST de controle isn1 ($(pv PORTA_CONTROLE)) time_total" "$TMPA/rc1.b" "$TMPA/rc1.d"
  serie ssh22 4 ssh;                  lat "ssh (VM:22) tempo de conexao TCP" "$TMPA/ssh.b" "$TMPA/ssh.d"
  q=$(col "$D/janela/saude.csv" "$(pv PORTA_ISN3)" 3 "$S_NS" "$E_NS" | grep -vc '^200$')
  [ "$q" = 0 ] && [ -s "$TMPA/rest3.d" ] && ok_ "REST de isn3 ($(pv PORTA_ISN3)): HTTP 200 em todas as amostras da janela" \
    || fa "REST de isn3 ($(pv PORTA_ISN3)) com HTTP diferente de 200 em $q amostra(s) da janela (ou nenhuma amostra)"
  q=$(col "$D/base/saude.csv" "$(pv PORTA_ISN3)" 3 0 9e30 | grep -vc '^200$')
  [ "$q" = 0 ] || fa "REST de isn3 na linha de base com HTTP diferente de 200 em $q amostra(s)"
  q=$(col "$D/janela/saude.csv" ssh22 4 "$S_NS" "$E_NS" | grep -c 'ERRO')
  [ "$q" = 0 ] && ok_ "ssh (VM:22): todas as conexoes TCP da janela aceitas" || fa "ssh (VM:22) sem conexao em $q amostra(s) da janela"
  # ping do host a isn3: base e janela sem perda; latencia pela mesma regra
  read -r t q <<<"$(pingtr "$D/base/ping.txt")"
  [ -n "${t:-}" ] && [ "$t" -gt 0 ] && [ "$t" = "$q" ] && ok_ "ping do host a isn3 na linha de base: $q/$t recebidos" \
    || fa "ping do host a isn3 na linha de base: ${q:-?}/${t:-?} recebidos (esperado sem perda)"
  read -r t q <<<"$(pingtr "$D/janela/ping.txt")"
  [ -n "${t:-}" ] && [ "$t" -gt 0 ] && [ "$t" = "$q" ] && ok_ "ping do host a isn3 durante a janela: $q/$t recebidos" \
    || fa "ping do host a isn3 durante a janela: ${q:-?}/${t:-?} recebidos (esperado sem perda: o filtro nao deve pegar o host)"
  rtts "$D/base/ping.txt" > "$TMPA/ping.b"; rtts "$D/janela/ping.txt" > "$TMPA/ping.d"
  lat "ping do host a isn3 (rtt)" "$TMPA/ping.b" "$TMPA/ping.d"

  # 8. intervalo do saude-log <= 2x o nominal (pausa do host ou da VM invalida o bloco)
  for f in "$D/base/saude.csv" "$D/janela/saude.csv"; do
    q=$(awk -F, 'NR > 1 { if ($2 in u) { d = $1 - u[$2]; if (d > m) m = d } u[$2] = $1 } END { printf "%.3f", m / 1e9 }' "$f" 2>/dev/null)
    if [ -s "$f" ] && awk -v g="${q:-999}" -v s="$SS" 'BEGIN { exit !(g <= 2 * s) }'; then
      ok_ "intervalo maximo do saude-log em ${f#"$D"/}: $q s <= 2 x $SS s"
    else fa "intervalo maximo do saude-log em ${f#"$D"/}: ${q:-?} s > 2 x $SS s (pausa do host/VM ou coleta ausente)"; fi
  done

  # 9. L-2 e residuos
  grep -qx 'ok sem prio em isn3' "$D/04-l1.log" 2>/dev/null && ok_ "L-2: sem_prio isn3 ok (04-l1.log)" || fa "L-2: sem 'ok sem prio em isn3' em 04-l1.log"
  grep -qE '^nic [^ ]+ proibidos\(prio\|netem\|clsact\)=0' "$D/05-pos-l1.log" 2>/dev/null && ok_ "NIC da VM sem prio/netem/clsact depois do L-1" \
    || fa "NIC da VM com prio/netem/clsact (ou sem registro) depois do L-1 (05-pos-l1.log)"
  grep -qx 'timers tcc-heal: 0 units tcc-heal: 0' "$D/05-pos-l1.log" 2>/dev/null && ok_ "0 timers e 0 units tcc-heal depois do L-1 (dead-man desarmado)" \
    || fa "timers/units tcc-heal depois do L-1 diferentes de 0 (05-pos-l1.log)"
  head -1 "$D/06-portao51-pos.avaliacao" 2>/dev/null | grep -q '^PORTAO 5.1: OK' \
    && ok_ "portao 5.1 pos: size=3 HEALTHY, caches vazios, 3 netns sem prio/netem/clsact e sem filtros, 0 timers" \
    || fa "portao 5.1 pos nao passou (ver 06-portao51-pos.avaliacao)"

  # resultados do experimento (nao invalidam)
  q=$(grep -cE 'ISPN000094|ISPN000093|ISPN100000|ISPN100001|MergeView' "$D/05-vistas.log" 2>/dev/null)
  if [ "${q:-0}" = 0 ]; then res "nenhuma mudanca de vista nos 3 nos desde o inicio do L-1 (05-vistas.log)"
  else res "$q linha(s) de mudanca de vista desde o inicio do L-1 (05-vistas.log): resultado do experimento (§5.6), nao falha do gate; bloqueia o S6 ate um portao 5.1 e muda a posicao da REP=4B; registrar o tempo de reforma (leitura humana)"; fi
  q=$(grep -c 'DEGRADED_MODE' "$D/05-vistas.log" 2>/dev/null); [ "${q:-0}" = 0 ] || res "$q linha(s) DEGRADED_MODE nos logs dos nos desde o inicio do L-1"
  q=$(awk -F, -v a="$S_NS" -v b="$E_NS" 'NR > 1 && $2 ~ /^1122/ && $1 + 0 >= a + 0 && $1 + 0 <= b + 0 && !($6 == "HEALTHY" && $7 == "3")' "$D/janela/saude.csv" 2>/dev/null | wc -l | tr -d ' ')
  if [ "$q" = 0 ]; then res "todas as amostras do saude-log na janela em HEALTHY/3"
  else res "$q amostra(s) do saude-log na janela fora de HEALTHY/3 (ver janela/saude.csv)"; fi
  for n in isn1 isn2 isn3; do
    t=$(sed -n "s/^$n TcpRetransSegs=\([0-9]*\).*/\1/p" "$D/03-pre-l1.log" 2>/dev/null | head -1)
    q=$(sed -n "s/^$n TcpRetransSegs=\([0-9]*\).*/\1/p" "$D/05-pos-l1.log" 2>/dev/null | head -1)
    if [ -n "$t" ] && [ -n "$q" ]; then res "TcpRetransSegs $n: $t -> $q (+$((q - t)))$([ "$n" = isn3 ] && echo '; H-L espera aumento em isn3')"
    else res "TcpRetransSegs $n: antes=${t:-?} depois=${q:-?} (sem leitura)"; fi
  done
  # sonda Hot Rod: so leitura humana (put/get em isn3 replicam para o outro dono pelo enlace degradado, por desenho)
  for fs in base janela; do
    f="$D/$fs/sonda-isn3.csv"
    if [ "$fs" = base ]; then awk -F, 'NR > 1' "$f" 2>/dev/null > "$TMPA/s"; else awk -F, -v a="$S_NS" -v b="$E_NS" 'NR > 1 && $1 + 0 >= a + 0 && $1 + 0 <= b + 0' "$f" 2>/dev/null > "$TMPA/s"; fi
    for op in put get; do awk -F, -v o="$op" '$4 == o && $6 == "OK" { printf "%.6f\n", $5 / 1e9 }' "$TMPA/s" > "$TMPA/s.$op"; done
    lei "sonda Hot Rod isn3 ($fs): put n/mediana/p95/max = $(stats "$TMPA/s.put"); get = $(stats "$TMPA/s.get"); ERRO = $(awk -F, '$6 == "ERRO"' "$TMPA/s" | wc -l | tr -d ' '); remove-final OK = $(grep -c 'remove-final,[0-9]*,OK' "$f" 2>/dev/null)"
  done
  lei "a latencia de operacao da sonda inclui a replicacao para o outro dono, que passa pelo enlace degradado de proposito; o criterio automatico de Hot Rod e a conexao TCP a porta $(pv PORTA_ISN3). Comparar put/get base x janela acima"
  lei "ssh do terminal do Gestor (Windows -> VM): o s5.sh so mede a conexao TCP ao sshd de dentro da VM e a NIC sem prio/netem; atraso percebido no terminal durante a janela deve ser anotado pelo Gestor"
  lei "tc -s filter: confira em janela/tc-*.txt que os 2 filtros u32 (dst isn1, isn2 -> 1:3) acumulam 'success' entre as amostras"
  lei "05-vistas.log e logs de isn3 se houver mudanca de vista; perdas calculadas do percentual do L1.log (o injetor nao guarda as linhas 'transmitted/received')"
  rm -rf "$TMPA"
  if [ "$NF" = 0 ]; then echo "S5: VALIDO ($NOK criterios OK)"; return 0; fi
  echo "S5: INVALIDO ($NF falha(s), $NOK OK)"; return 1
}
if [ "${1:-}" = --avaliar ]; then avaliar "$2"; exit $?; fi

# ---- coletas (rodam na VM)
tcp_s() { # tcp_s <ip> <porta>: segundos ate a conexao TCP ser aceita (time_connect medido pelo proprio curl), ou ERRO
  # O sshd responde com o banner SSH, o curl recusa a resposta (nao e HTTP) e sai na hora; o time_connect ja foi medido.
  # Cronometrar um processo (timeout/bash) mediria o fork, que varia com a carga da VM e daria falso positivo.
  local c; c=$(curl -s -o /dev/null --connect-timeout 2 -m 2 -w '%{time_connect}' "http://$1:$2/" 2>/dev/null)
  if awk -v c="${c:-0}" 'BEGIN { exit !(c + 0 > 0) }'; then printf '%s' "$c"; else echo ERRO; fi; }
saude_loop() { # saude_loop <csv> <arquivo-de-parada> <max-s>: /health nas 3 portas e ssh22 a cada SAUDE_S s (periodo fixo)
  local csv="$1" stop="$2" max="$3" t0 ti p r body st nn ts c rest
  echo "t_epoch_ns,fonte,http,t_connect_s,t_total_s,status,nodes" > "$csv"; t0=$(date +%s)
  while [ ! -e "$stop" ] && [ $(( $(date +%s) - t0 )) -lt "$max" ]; do
    ti=$(date +%s%N)
    for p in 11222 11223 11224; do
      ts=$(date +%s%N)
      r=$(curl -s -m 3 --digest -u admin:infinispan -o "$csv.corpo" -w '%{http_code} %{time_connect} %{time_total}' "http://127.0.0.1:$p/rest/v2/cache-managers/default/health" 2>/dev/null) || true
      [ -n "$r" ] || r="000 - -"
      body=$(tr -d ' \n' < "$csv.corpo" 2>/dev/null); : > "$csv.corpo"
      st=$(printf '%s' "$body" | grep -o '"cluster_health":{[^}]*' | grep -o '"health_status":"[A-Z_]*"' | cut -d'"' -f4)
      nn=$(printf '%s' "$body" | grep -o '"number_of_nodes":[0-9]*' | head -1 | cut -d: -f2)
      echo "$ts,$p,${r// /,},${st:--},${nn:--}" >> "$csv"
    done
    ts=$(date +%s%N); c=$(tcp_s "$VMIP" 22); echo "$ts,ssh22,-,$c,$c,-,-" >> "$csv"
    rest=$(awk -v a="$ti" -v b="$(date +%s%N)" -v s="$SAUDE_S" 'BEGIN { r = s - (b - a) / 1e9; if (r < 0) r = 0; printf "%.3f", r }')
    sleep "$rest"
  done
  rm -f "$csv.corpo"; }
sonda() { # sonda <csv> [--duracao-s N] &   SO em segundo plano: o exec faz o PID do job ser o da JVM, entao o kill -TERM
  # chega a JVM e o shutdown hook faz o remove-final (sem exec, o TERM mataria so o subshell e a JVM ficaria orfa, com a chave)
  # Hot Rod pinada em isn3 (como no g1b); senha do laboratorio pela variavel de ambiente
  local o="$1"; shift
  export SONDA_SENHA=infinispan; exec java -cp "$JAR" br.unipampa.tcc.session.SondaHotrod --no "isn3=127.0.0.1:$PORTA_ISN3" --caches sessions,counters \
    --intervalo-s "$SAUDE_S" --usuario admin "$@" > "$o" 2> "${o%.csv}.err"; }
amostra_tc() { # amostra_tc <arquivo> <off>
  { echo "t_epoch_ns=$(date +%s%N) off=+$2s $(date -Is)"
    echo "## tc -s qdisc show dev eth0 (isn3, pid $PID3)"; sudo nsenter -t "$PID3" -n tc -s qdisc show dev eth0 2>&1
    echo "## tc -s filter show dev eth0 (isn3, pid $PID3)"; sudo nsenter -t "$PID3" -n tc -s filter show dev eth0 2>&1; } > "$1"; }
retrans() { local n p; for n in isn1 isn2 isn3; do p=$(sudo podman inspect -f '{{.State.Pid}}' "$n" 2>/dev/null)
  echo "$n TcpRetransSegs=$(sudo nsenter -t "$p" -n cat /proc/net/snmp 2>/dev/null | awk '/^Tcp:/ { if (!h) { for (i = 1; i <= NF; i++) if ($i == "RetransSegs") c = i; h = 1; next } print $c; exit }') pid=$p"; done; }
nic_linha() { local q; q=$(tc qdisc show dev "$NIC" 2>&1); echo "nic $NIC proibidos(prio|netem|clsact)=$(printf '%s\n' "$q" | grep -cE 'prio|netem|clsact'): $(echo $q)"; }
coletor() { # em segundo plano: espera o event=start do injetor e coleta ate end + POS_FIM_S
  local J="$OUT/janela" MARC="$TCC_DIR/runs/gate-netdegrade/marcadores.txt" t0 s_ns off pping= psonda= psaude=
  mkdir -p "$J"
  clog() { echo "$(date -Is) $*" >> "$OUT/04-coletor.log"; }
  trap 'touch "$J/.parar"; [ -z "$psonda" ] || kill -TERM "$psonda" 2>/dev/null; [ -z "$pping" ] || kill "$pping" 2>/dev/null; clog "coletor interrompido"; exit 143' TERM
  clog "coletor: esperando event=start em $MARC (ate ${ESPERA_START_S} s)"
  t0=$(date +%s)
  until grep -q 'event=start' "$MARC" 2>/dev/null; do
    [ $(( $(date +%s) - t0 )) -lt "$ESPERA_START_S" ] || { clog "coletor: sem event=start em ${ESPERA_START_S} s"; return 2; }
    sleep 0.2
  done
  s_ns=$(sed -n 's/.*event=start t_epoch_ns=\([0-9]*\).*/\1/p' "$MARC" | head -1)
  clog "coletor: start_ns=$s_ns; ping a $IP3, sonda Hot Rod e saude a cada ${SAUDE_S} s; tc em +${TC_AMOSTRAS// /,+} s"
  ping -n -c "$PING_JANELA_N" -i 0.2 -W 2 "$IP3" > "$J/ping.txt" 2>&1 & pping=$!
  sonda "$J/sonda-isn3.csv" & psonda=$!
  saude_loop "$J/saude.csv" "$J/.parar" 100000 & psaude=$!
  for off in $TC_AMOSTRAS; do
    while [ $(( ($(date +%s%N) - s_ns) / 1000000000 )) -lt "$off" ]; do grep -q 'event=end' "$MARC" && break 2; sleep 0.2; done
    grep -q 'event=end' "$MARC" && break
    amostra_tc "$J/tc-$off.txt" "$off"; clog "coletor: amostra tc +${off}s"
  done
  until grep -q 'event=end' "$MARC"; do [ $(( $(date +%s) - t0 )) -lt $(( ESPERA_START_S + 600 )) ] || break; sleep 0.2; done
  clog "coletor: event=end visto; mais ${POS_FIM_S} s"
  sleep "$POS_FIM_S"
  touch "$J/.parar"; kill -TERM "$psonda" 2>/dev/null; wait "$psonda" 2>/dev/null; wait "$psaude" 2>/dev/null
  kill -0 "$pping" 2>/dev/null && kill "$pping" 2>/dev/null; wait "$pping" 2>/dev/null; rm -f "$J/.parar"
  clog "coletor fim (ping $(pingtr "$J/ping.txt" | tr ' ' '/'), sonda $(grep -c . "$J/sonda-isn3.csv") linhas, saude $(grep -c . "$J/saude.csv") linhas)"
}

# ---- argumentos (Q-11): nada e lido alem deles antes de valida-los; erro de argumento = rc 1, sem vm-<UTC>/
uso() { echo "S5 ABORTADO (rc=1): $*" >&2
  echo "uso: bash runs/vm-s5-2026-10-28/s5.sh --boot-esperado <uuid> | --hashes | --avaliar-portao <log> | --avaliar <dir>" >&2; exit 1; }
BOOT_ARG=
while [ $# -gt 0 ]; do
  case "$1" in
    --boot-esperado) [ $# -ge 2 ] || uso "falta o valor de --boot-esperado"; BOOT_ARG="$2"; shift 2;;
    *) uso "argumento desconhecido: $1";;
  esac
done
[[ "$BOOT_ARG" =~ ^[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$ ]] || uso "--boot-esperado ausente ou fora do formato UUID ('$BOOT_ARG')"
BOOT_ESPERADO="$BOOT_ARG"

# ---- 0. TCC_DIR, diretorio de saida e console
if [ -z "${TCC_DIR:-}" ]; then export TCC_DIR="$HOME/tcc-gate"; TCC_ORIGEM="exportado pelo s5.sh"
elif [ "$TCC_DIR" = "$HOME/tcc-gate" ]; then export TCC_DIR; TCC_ORIGEM="ja exportado"
else echo "S5 ABORTADO: TCC_DIR=$TCC_DIR (precisa ser $HOME/tcc-gate)" >&2; exit 1; fi
cd "$TCC_DIR" || { echo "S5 ABORTADO: sem $TCC_DIR" >&2; exit 1; }
[ "$(dirname "$AQUI")" = "$TCC_DIR/runs" ] || { echo "S5 ABORTADO: s5.sh fora de $TCC_DIR/runs ($AQUI)" >&2; exit 1; }
OUT="$AQUI/vm-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT" || exit 1
exec > >(tee -a "$OUT/s5-console.log") 2>&1
TEE_PID=$!
CPID=
trap 'exec 1>&- 2>&-; wait "$TEE_PID" 2>/dev/null' EXIT
passo_rc() { echo "$1 rc=$2 fim=$(date -Is)" | tee -a "$OUT/rc.txt"; }
abortar() { local rc="$1"; shift; echo "S5 ABORTADO (rc=$rc): $*" >&2; echo "abortado rc=$rc: $* $(date -Is)" >> "$OUT/rc.txt"; exit "$rc"; }
CURA="PODMAN='sudo podman' bash scripts/inject-netdegrade.sh --heal"
AVISO_DM="o dead-man do L-1 (armar 60 => --on-active=150 s) tem AccuracySec=1min: a cura automatica pode vir so ~210 s depois de armar. Cura manual (em ~/tcc-gate): $CURA"
trap '[ -z "$CPID" ] || kill -TERM "$CPID" 2>/dev/null; echo "S5 interrompido (Ctrl-C) $(date -Is); se o injetor ja tinha iniciado: $AVISO_DM" >&2; echo "interrompido $(date -Is)" >> "$OUT/rc.txt"; exit 130' INT
echo "S5 (L-1 e L-2) inicio $(date -Is) saida=$OUT"
echo "TCC_DIR=$TCC_DIR ($TCC_ORIGEM)"

# ---- 0. pre-condicoes (so leitura); qualquer falha aborta antes da linha de base
{
  E=0; nok() { echo "NAO OK: $*"; E=1; }
  PAR="$OUT/parametros.txt"
  echo "== pre-condicoes $(date -Is)"
  echo "hostname=$(hostname) ip=$(hostname -I 2>/dev/null | awk '{print $1}') usuario=$(id -un)"
  echo "argumentos: --boot-esperado $BOOT_ESPERADO"
  [ -n "${TMUX:-}" ] && echo "tmux: $TMUX" || nok "fora do tmux (TMUX vazio)"
  [ -t 0 ] || [ "$TESTE" = 1 ] && echo "terminal: ok (o GO do l1.sh e interativo)" || nok "sem terminal (o GO do l1.sh e interativo)"
  if flock -n "$LOCK" true; then nok "lock $LOCK LIVRE: rode dentro de 'flock $LOCK tmux new -s s5'"; else echo "lock $LOCK: OCUPADO (este flock)"; fi
  SUDO=0; sudo -n true && { echo "sudo -n: ok"; SUDO=1; } || nok "sudo -n true falhou"
  K=$(uname -r); echo "uname -r: $K"; [ "$K" = "$KERNEL_ESPERADO" ] || nok "kernel $K (esperado $KERNEL_ESPERADO)"
  B=$(cat "$BOOT_ID_FILE" 2>/dev/null); echo "boot_id: $B"; echo "boot_id esperado (--boot-esperado): $BOOT_ESPERADO"
  # Q-11: qualquer diferenca e NAO OK, nunca aviso
  [ "$B" = "$BOOT_ESPERADO" ] || nok "boot_id atual $B diferente de --boot-esperado $BOOT_ESPERADO: reboot da VM depois da leitura do coordenador ou referencia errada: parar e chamar o Senior"
  echo "HEAD: $(git rev-parse HEAD 2>/dev/null) ($(git log -1 --format=%s 2>/dev/null))"
  git merge-base --is-ancestor 902654b HEAD 2>/dev/null && echo "contem 902654b (v4c): sim" || echo "contem 902654b (v4c): nao"
  # Q-12 (aprovada em 2026-11-04; a mesma regra do s6.sh/s7.sh): o unico versionado que pode estar modificado e
  # runs/gate-b29/go.log, com HEAD como prefixo exato e cada linha a mais comecando com "go " (o exigir_go acrescenta uma)
  GL=runs/gate-b29/go.log
  M=$(git status --porcelain --untracked-files=no 2>/dev/null); N=$(printf '%s' "$M" | grep -c .)
  echo "arquivos versionados modificados: $N"; [ -z "$M" ] || printf '%s\n' "$M"
  OUTROS=$(printf '%s\n' "$M" | grep -v "^ M $GL\$" | grep -c .)
  [ "$OUTROS" = 0 ] || nok "$OUTROS arquivo(s) versionado(s) modificado(s) alem de $GL (ou $GL em estado diferente de ' M')"
  if printf '%s\n' "$M" | grep -qx " M $GL"; then
    git show "HEAD:$GL" > "$OUT/go.log.HEAD" 2>/dev/null
    TAM=$(wc -c < "$OUT/go.log.HEAD" | tr -d ' ')
    if [ "$TAM" != 0 ] && [ "$(tail -c 1 "$OUT/go.log.HEAD" | od -An -c | tr -d ' ')" != '\n' ]; then nok "$GL no HEAD nao termina em fim de linha"
    elif ! head -c "$TAM" "$GL" | cmp -s - "$OUT/go.log.HEAD"; then nok "$GL: o conteudo do HEAD nao e prefixo exato do arquivo (linha antiga alterada)"
    elif tail -c +"$((TAM + 1))" "$GL" | grep -vq '^go '; then nok "$GL: linha acrescentada que nao comeca com 'go '"
    else echo "$GL: HEAD e prefixo exato; $(tail -c +"$((TAM + 1))" "$GL" | grep -c .) linha(s) 'go ' acrescentada(s) (aceito, Q-12)"; fi
    rm -f "$OUT/go.log.HEAD"
  fi
  echo "-- $GL (versionado)"; cat "$GL" 2>/dev/null || echo "(sem $GL)"
  echo "-- sha256 (referencia v4c; l1.sh e o verbatim, nao editado)"
  if printf '%s\n' "$HASHES_ESPERADOS" | sha256sum -c -; then echo "hashes: iguais a referencia"; else nok "hash diferente da referencia"; fi
  echo "s5.sh: $(sha256sum "$SELF" | cut -d' ' -f1)"
  V=$(env | grep -E '^(LOCK_DIR|PODMAN|INJ|LOG|DMS|L0C_[A-Z_]*|G1_[A-Z_]*|REP|COLETA|SONDA_[A-Z_]*|G_TESTE|INTERVALO_S|POS_FIM_S|MAX_S|ESPERA_START_S)=' | cut -d= -f1 | paste -sd' ')
  [ -z "$V" ] && echo "ambiente: sem LOCK_DIR/PODMAN/INJ/SONDA_*/... herdados" || nok "variaveis herdadas que mudariam o l1/injetor: $V"
  command -v java >/dev/null && echo "java: $(java -version 2>&1 | head -1)" || nok "java ausente (a sonda Hot Rod precisa)"
  J=$(ls workload/target/session-workload-*.jar 2>/dev/null | head -1)
  if [ -z "$J" ]; then nok "jar workload/target/session-workload-*.jar ausente"
  else JH=$(sha256sum "$J" | cut -d' ' -f1); echo "jar: $J $JH"
       [ "$JH" = "$JAR_ESPERADO" ] && echo "jar: igual ao do S4" || nok "jar diferente do S4 ($JAR_ESPERADO)"; fi
  for f in L1.log marcadores.txt; do
    [ ! -e "runs/gate-netdegrade/$f" ] && echo "runs/gate-netdegrade/$f: inexistente" \
      || nok "runs/gate-netdegrade/$f ja existe (execucao anterior do L-1? mover para fora antes, sem apagar)"; done
  T=$(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal); U=$(systemctl list-units --all --no-legend 2>/dev/null | grep -c tcc-heal)
  echo "timers tcc-heal: $T units tcc-heal: $U"; [ "$T" = 0 ] || nok "$T timers tcc-heal armados"; [ "$U" = 0 ] || nok "$U units tcc-heal carregadas"
  echo "-- runs/gate-netdegrade/go.log (blocos anteriores)"; cat runs/gate-netdegrade/go.log 2>/dev/null || echo "(sem go.log)"
  {
    echo "SAUDE_S=$SAUDE_S"; echo "PING_N=$PING_N"; echo "PERDA_MIN=$PERDA_MIN"; echo "PERDA_MAX=$PERDA_MAX"; echo "LOSS=$LOSS"
    echo "FOLGA_LAT_S=$FOLGA_LAT_S"; echo "BASE_MIN_N=$BASE_MIN_N"; echo "BOOT_ESPERADO=$BOOT_ESPERADO"; echo "PORTA_ISN3=$PORTA_ISN3"; echo "PORTA_CONTROLE=$PORTA_CONTROLE"
    echo "JAR=$J"
  } > "$PAR"
  VMIP=$(hostname -I 2>/dev/null | awk '{print $1}'); echo "VMIP=$VMIP" >> "$PAR"; [ -n "$VMIP" ] || nok "IP da VM vazio (hostname -I)"
  NIC=$(ip route show default 2>/dev/null | awk '{ for (i = 1; i < NF; i++) if ($i == "dev") { print $(i + 1); exit } }'); echo "NIC=$NIC" >> "$PAR"
  if [ -z "$NIC" ]; then nok "sem rota padrao (NIC da VM desconhecida)"
  else NL=$(nic_linha); echo "$NL"; case "$NL" in "nic $NIC proibidos(prio|netem|clsact)=0:"*) ;; *) nok "NIC da VM ($NIC) com prio/netem/clsact";; esac; fi
  if [ "$SUDO" = 1 ]; then
    echo "-- conteineres e cluster atual (so registro: IDs, StartedAt, IPs e ultima vista; nenhuma vista e exigida, o cluster do S4 foi recriado)"
    sudo podman ps -a --format '{{.Names}} {{.ID}} {{.Status}} criado={{.CreatedAt}}' 2>&1
    for n in isn1 isn2 isn3; do
      ip=$(sudo podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$n" 2>/dev/null | awk '{print $1}')
      echo "$n ip=$ip id=$(sudo podman inspect -f '{{.Id}}' "$n" 2>&1 | cut -c1-12) StartedAt=$(sudo podman inspect -f '{{.State.StartedAt}}' "$n" 2>&1)"
      [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || nok "IP de $n invalido ('$ip')"; echo "IP_$n=$ip" >> "$PAR"
      v=$(sudo podman logs "$n" 2>&1 | grep -E 'ISPN000094|ISPN000093' | tail -1 | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-220)
      echo "$n ultima vista: ${v:-nenhuma}"
    done
    P3=$(sudo podman inspect -f '{{.State.Pid}}' isn3 2>/dev/null); echo "PID_isn3=$P3" >> "$PAR"
    if [ -z "$P3" ] || [ "$P3" = 0 ]; then nok "PID de isn3 invalido ('$P3')"
    else
      ns1=$(sudo readlink /proc/1/ns/net 2>/dev/null); ns3=$(sudo readlink "/proc/$P3/ns/net" 2>/dev/null)
      if [ -n "$ns1" ] && [ -n "$ns3" ] && [ "$ns1" != "$ns3" ]; then echo "isn3 netns: diferente do host ($ns3 != $ns1)"
      else nok "netns de isn3 igual ao do host ou ilegivel ('$ns3' / '$ns1')"; fi
      S=$(sudo nsenter -t "$P3" -n ss -tn 2>&1 | grep ':7800')
      n4=$(printf '%s\n' "$S" | grep -c '^[A-Z-]* .*[0-9]\.[0-9]*\.[0-9]*\.[0-9]*:7800'); n6=$(printf '%s\n' "$S" | grep -c '\[')
      echo "isn3 JGroups 7800: ipv4=$n4 ipv6=$n6"; [ "$n6" = 0 ] || nok "IPv6 no TCP do JGroups em isn3 (parada obrigatoria da §5.6)"
      [ "$n4" -gt 0 ] || echo "AVISO: nenhuma conexao IPv4 :7800 em isn3 (o filtro u32 nao casaria nada)"
    fi
  fi
  exit "$E"
} 2>&1 | tee "$OUT/00-pre.log"; RC=${PIPESTATUS[0]}; passo_rc pre "$RC"
[ "$RC" = 0 ] || abortar 1 "pre-condicoes (ver $OUT/00-pre.log)"
par() { sed -n "s/^$1=//p" "$OUT/parametros.txt" | head -1; }
IP3=$(par IP_isn3); PID3=$(par PID_isn3); VMIP=$(par VMIP); NIC=$(par NIC); JAR=$(par JAR)

# ---- 1. linha de base sem F4
mkdir -p "$OUT/base"
{
  E=0
  echo "== linha de base sem F4 $(date -Is): ${BASE_S} s; /health 11222/11223/11224 e ssh $VMIP:22 a cada ${SAUDE_S} s; ping a isn3 $IP3; sonda Hot Rod isn3:$PORTA_ISN3"
  sonda "$OUT/base/sonda-isn3.csv" --duracao-s "$BASE_S" & PS=$!
  ping -n -c "$PING_BASE_N" -i 0.2 -W 2 "$IP3" > "$OUT/base/ping.txt" 2>&1 & PP=$!
  saude_loop "$OUT/base/saude.csv" "$OUT/base/.nunca" "$BASE_S"
  wait "$PP"; echo "ping rc=$? $(pingtr "$OUT/base/ping.txt" | awk '{print $2 "/" $1 " recebidos"}')"
  wait "$PS"; echo "sonda rc=$?"
  for p in 11222 11223 11224 ssh22; do echo "saude $p: $(awk -F, -v p="$p" 'NR > 1 && $2 == p' "$OUT/base/saude.csv" | wc -l | tr -d ' ') amostras"
    [ "$(awk -F, -v p="$p" 'NR > 1 && $2 == p' "$OUT/base/saude.csv" | wc -l)" -ge "$BASE_MIN_N" ] || { echo "NAO OK: menos de $BASE_MIN_N amostras de $p"; E=1; }; done
  [ -n "$(pingtr "$OUT/base/ping.txt")" ] || { echo "NAO OK: ping sem resumo"; E=1; }
  n=$(grep -cE ',(sessions|counters),remove-final,[0-9]+,OK' "$OUT/base/sonda-isn3.csv")
  echo "sonda: $(grep -c ',OK,' "$OUT/base/sonda-isn3.csv") OK, $(grep -c ',ERRO,' "$OUT/base/sonda-isn3.csv") ERRO, remove-final OK em $n de 2 caches"
  [ "$n" = 2 ] || { echo "NAO OK: sonda sem remove-final OK nos 2 caches (a chave sonda-isn3 pode ter ficado; o portao pede size=0)"; E=1; }
  exit "$E"
} 2>&1 | tee "$OUT/01-base.log"; RC=${PIPESTATUS[0]}; passo_rc base "$RC"
[ "$RC" = 0 ] || abortar 2 "linha de base incompleta (ver 01-base.log)"

# ---- 2. portao 5.1 pre (com .log gravado); se falhar, PARA
portao() { # portao <nome> <duracao-s>
  local rc
  echo "portao $1: $2 s a partir de $(date -Is)"
  bash runs/vm-s2-2026-10-27/portao51.sh "$2" "$OUT/$1.csv" 2>&1 | tee "$OUT/$1.log"; rc=${PIPESTATUS[0]}; passo_rc "$1" "$rc"
  avaliar_portao "$OUT/$1.log" | tee "$OUT/$1.avaliacao"; rc=${PIPESTATUS[0]}; passo_rc "$1-avaliacao" "$rc"; return "$rc"
}
portao 02-portao51-pre "$PORTAO_MIN_S" || abortar 3 "portao 5.1 pre falhou: S5 NAO roda (ver 02-portao51-pre.log/.avaliacao)"

# ---- 3. TcpRetransSegs antes, coletor e l1.sh (GO digitado pelo Gestor no prompt do l1.sh)
T_ANTES_L1=$(date -Is)
{ echo "== antes do L-1 $T_ANTES_L1"; retrans; nic_linha; } 2>&1 | tee "$OUT/03-pre-l1.log"; RC=${PIPESTATUS[0]}; passo_rc pre-l1 "$RC"
coletor & CPID=$!
echo "coletor em segundo plano pid=$CPID $(date -Is)" | tee -a "$OUT/rc.txt"
echo ">>> AVISO: $AVISO_DM"
echo ">>> L-1: 20 % de perda em isn3 por 60 s (cluster ocioso). Digite GO no prompt do l1.sh (qualquer outra coisa aborta)."
bash runs/gate-netdegrade/l1.sh 2>&1 | tee "$OUT/04-l1.log"; RC=${PIPESTATUS[0]}; T_L1_FIM=$(date +%s); passo_rc l1 "$RC"
MARC=runs/gate-netdegrade/marcadores.txt
if ! grep -q 'event=start' "$MARC" 2>/dev/null; then
  kill -TERM "$CPID" 2>/dev/null; wait "$CPID" 2>/dev/null; echo "coletor encerrado (l1 sem event=start) $(date -Is)" | tee -a "$OUT/rc.txt"
else
  for _ in $(seq 1 "$L1_ESPERA_COLETOR_S"); do kill -0 "$CPID" 2>/dev/null || break; sleep 1; done
  kill -0 "$CPID" 2>/dev/null && { kill -TERM "$CPID" 2>/dev/null; echo "coletor nao terminou em ${L1_ESPERA_COLETOR_S} s: encerrado" | tee -a "$OUT/rc.txt"; }
  wait "$CPID"; passo_rc coletor "$?"
fi
CPID=

# ---- 4. depois do L-1: copias, TcpRetransSegs, NIC, timers, vistas
mkdir -p "$OUT/l1"
for f in L1.log marcadores.txt go.log; do cp "runs/gate-netdegrade/$f" "$OUT/l1/$f" 2>/dev/null || echo "AVISO: runs/gate-netdegrade/$f ausente"; done
( cd "$OUT" && find l1 -type f | sort | xargs sha256sum ) > "$OUT/l1.sha256" 2>/dev/null
if [ "$RC" != 0 ]; then
  { echo "== estado apos falha do l1.sh $(date -Is)"
    echo "timers: $(systemctl list-timers --all --no-legend 2>/dev/null | grep tcc-heal || echo nenhum)"
    for n in isn1 isn2 isn3; do p=$(sudo podman inspect -f '{{.State.Pid}}' "$n" 2>/dev/null)
      echo "$n pid=$p tc: $(sudo nsenter -t "$p" -n tc qdisc show dev eth0 2>&1 | tr '\n' ';')"; done
    echo "Se o injetor iniciou e o l1.sh abortou antes de desarmar: $AVISO_DM"
    echo "Se o l1.sh saiu no L-2 (RESIDUO): o dead-man ja foi desarmado; rode a cura manual."
  } 2>&1 | tee "$OUT/05-pos-falha.log"; passo_rc pos-falha "${PIPESTATUS[0]}"
  abortar 4 "l1.sh rc=$RC"
fi
{
  echo "== depois do L-1 $(date -Is)"
  retrans; nic_linha
  echo "timers tcc-heal: $(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal) units tcc-heal: $(systemctl list-units --all --no-legend 2>/dev/null | grep -c tcc-heal)"
  cat "$OUT/l1/marcadores.txt"; tail -1 "$OUT/l1/go.log"
  for n in isn1 isn2 isn3; do
    echo "## $n podman logs desde $T_ANTES_L1"
    sudo podman logs --since "$T_ANTES_L1" "$n" 2>&1 | grep -E 'ISPN000094|ISPN000093|ISPN100000|ISPN100001|MergeView|DEGRADED_MODE' | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-260
  done > "$OUT/05-vistas.log"
  echo "linhas de vista/DEGRADED desde o inicio do L-1: $(grep -vc '^## ' "$OUT/05-vistas.log")"
} 2>&1 | tee "$OUT/05-pos-l1.log"; RC=${PIPESTATUS[0]}; passo_rc pos-l1 "$RC"

# ---- 5. portao pos (fim >= fim do L-1 + PORTAO_FOLGA_S), boot_id do fim e avaliacao
DUR=$(( T_L1_FIM + PORTAO_FOLGA_S - $(date +%s) )); [ "$DUR" -ge "$PORTAO_MIN_S" ] || DUR=$PORTAO_MIN_S
FIM=0
portao 06-portao51-pos "$DUR" || FIM=5
echo "boot_id fim: $(cat "$BOOT_ID_FILE" 2>/dev/null) $(date -Is)" | tee "$OUT/07-boot-fim.txt"; RC=${PIPESTATUS[0]}; passo_rc boot-fim "$RC"
avaliar "$OUT" | tee "$OUT/08-avaliacao.txt"; RC=${PIPESTATUS[0]}; passo_rc avaliacao "$RC"
[ "$RC" = 0 ] || [ "$FIM" != 0 ] || FIM=6
echo "S5 fim $(date -Is) rc=$FIM. Saia do tmux com exit (libera o lock)." | tee -a "$OUT/rc.txt"
exit "$FIM"
