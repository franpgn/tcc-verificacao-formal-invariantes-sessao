#!/usr/bin/env bash
# runs/vm-s7-2026-11-04/s7.sh -- involucro do S7 (G-5b-B: o dead-man disparando sozinho depois de um split real) na VM.
# NAO injeta, NAO cura, NAO desarma e NAO digita o GO: quem arma o dead-man (margem curta, mesmo corpo), injeta e da o
# kill -KILL no injetor e o runs/gate-b29/g5b.sh B (v4c, 902654b), que nao e editado; a cura e SO do dead-man. Este script
# so le e registra.
#
# Uso (dentro do tmux aberto sob flock, em ~/tcc-gate):
#   bash runs/vm-s7-2026-11-04/s7.sh --boot-esperado <uuid> --bloco-anterior <dir vm-UTC do S6>
# Nenhum boot_id fica no codigo (licao do s4.sh): o esperado vem por argumento e tem de ser igual ao atual e ao "boot_id fim"
# do bloco anterior; kernel, IDs e StartedAt dos 3 conteineres tambem tem de ser os do bloco anterior.
#   0. pre-condicoes (so leitura) e estado do cluster;           1. g5b.sh B --dry-run e conferencia dos parametros;
#   2. portao 5.1 pre (se falhar, PARA);                         3. coletor em segundo plano + g5b.sh B neste terminal (GO do
#      Gestor no prompt do proprio g5b.sh);                      4. copias, journal da unit e do sudo, timers, tc, vistas;
#   5. portao 5.1 pos (fim >= fim do g5b.sh + 345 s);            6. boot_id do fim;   7. avaliacao e MANIFEST.sha256.
# Saidas: runs/vm-s7-2026-11-04/vm-<UTC>/ (console em s7-console.log; rc e fim de cada passo em rc.txt).
# Saida: 0 valido; 1 pre-condicao; 2 argumentos; 3 portao pre; 4 g5b.sh rc != 0; 5 portao pos; 6 avaliacao INVALIDO;
# 130 Ctrl-C.
# Outros modos: --dry-run (plano, sai 0 sem tocar em nada), --hashes (tabela), --avaliar-portao <log>,
# --avaliar <dir vm-UTC> (refaz a avaliacao, tambem no host, Git Bash).
# NAO curar a mao: qualquer --heal manual invalida o bloco (§5.7). Cura manual, SO se o proprio g5b.sh travar (o bloco fica
# registrado como falha do dead-man), em ~/tcc-gate: PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3
# S7_TESTE=1 so nos testes com shims (s7.test.sh): permite trocar limites, tempos, arquivo do boot_id e a tabela de hashes.
# Decisoes pendentes do Gestor (docs/roteiro-vm-2026-11-04.md, secao 9) marcadas com "Q-nn (pendente do Gestor)".
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="$AQUI/s7.sh"
TESTE="${S7_TESTE:-0}"

BLOCO=S7; MODO=B
BOOT_ID_FILE=/proc/sys/kernel/random/boot_id
LOCK=/var/lock/tcc-vm.lock
INJ_LOCK=/tmp/inject-partition-isn3.lock   # lock por no do inject-partition.sh (LOCK_DIR padrao /tmp)
COMMIT_REF=902654b                          # v4c: o g5b.sh conferido na VM no S3
PORTAO_MIN_S=130; PORTAO_FOLGA_S=345; PORTAO_BOA_S=120
SAUDE_S=2                 # intervalo nominal do saude-log
TC_S=2                    # intervalo da serie de tc qdisc show de isn3
ESPERA_GO_S=900           # o coletor espera a linha nova de GO ate 15 min
COLETOR_EXTRA_S=900       # o coletor para no maximo ESPERA_GO_S + COLETOR_EXTRA_S s depois de comecar
ESPERA_COLETOR_S=60       # o involucro espera o coletor no maximo 60 s depois do g5b.sh
DUR=120; T_KILL_S=70; MARGEM_S=25; PERSISTE_S=15   # parametros efetivos do g5b.sh B (conferidos no --dry-run)
TOL_S=10; G5B_FOLGA=30    # g5b.sh B: disparo ate prazo + 10 s; desiste em prazo + 10 + 30 s (e o trap dele cura)
D2_FOLGA_S=5; D2_PRE_FIRE_S=2   # D-2: amostras de t_start + 5 s a t_fire - 2 s e a partir de t_fire + 5 s
POS_FALHA_EXTRA_S=60; POS_FALHA_AMOSTRA_S=5   # 3.3: espera ate prazo do dead-man + 60 s, amostra a cada 5 s
# Referencia de hashes: v4c (902654b). g5b.sh = VM no S3 (runs/vm-s3-2026-10-28/02-pos-l0c-leitura.log:9); g56-lib.sh = S0
# (relatorio do gate v4); g-lib.sh = S3 (V-S3.md, item 3); inject-partition.sh = S3 (04-s4-pre-hashes-e-cluster.log:1);
# portao51.sh = S2, S3, S4.
HASHES_ESPERADOS='6f50e3e36b2d93aba59e13a7765265935c83d5ccd20bc4c636531d7b1b260378  runs/gate-b29/g5b.sh
4f750fe3cf59f299585c12f66b3020973976e0e979474fde6354bf6998a18e2a  runs/gate-b29/g56-lib.sh
af3436d5ede54f5f2047ba48620130a803b4bbd8929e2cbc242fbbe0ebb922dd  runs/gate-b29/g-lib.sh
59dd2beee5b003d85f96cae71866227d7bca1c87a9ceeab034180ce4254d94e1  scripts/inject-partition.sh
2598b37d4969b1bd717ddbe514110fb6a0077e1332e38c1b76c5caea61802338  runs/vm-s2-2026-10-27/portao51.sh'
if [ "${1:-}" = --hashes ]; then printf '%s\n' "$HASHES_ESPERADOS"; exit 0; fi
if [ "$TESTE" = 1 ]; then
  BOOT_ID_FILE="${S7_BOOT_ID_FILE:-$BOOT_ID_FILE}"; INJ_LOCK="${S7_INJ_LOCK:-$INJ_LOCK}"; COMMIT_REF="${S7_COMMIT_REF:-$COMMIT_REF}"
  PORTAO_MIN_S="${S7_PORTAO_MIN_S:-$PORTAO_MIN_S}"; PORTAO_FOLGA_S="${S7_PORTAO_FOLGA_S:-$PORTAO_FOLGA_S}"
  PORTAO_BOA_S="${S7_PORTAO_BOA_S:-$PORTAO_BOA_S}"
  SAUDE_S="${S7_SAUDE_S:-$SAUDE_S}"; TC_S="${S7_TC_S:-$TC_S}"; ESPERA_GO_S="${S7_ESPERA_GO_S:-$ESPERA_GO_S}"
  COLETOR_EXTRA_S="${S7_COLETOR_EXTRA_S:-$COLETOR_EXTRA_S}"; ESPERA_COLETOR_S="${S7_ESPERA_COLETOR_S:-$ESPERA_COLETOR_S}"
  DUR="${S7_DUR:-$DUR}"; T_KILL_S="${S7_T_KILL_S:-$T_KILL_S}"; MARGEM_S="${S7_MARGEM_S:-$MARGEM_S}"
  PERSISTE_S="${S7_PERSISTE_S:-$PERSISTE_S}"; TOL_S="${S7_TOL_S:-$TOL_S}"; G5B_FOLGA="${S7_G5B_FOLGA:-$G5B_FOLGA}"
  D2_FOLGA_S="${S7_D2_FOLGA_S:-$D2_FOLGA_S}"; D2_PRE_FIRE_S="${S7_D2_PRE_FIRE_S:-$D2_PRE_FIRE_S}"
  POS_FALHA_EXTRA_S="${S7_POS_FALHA_EXTRA_S:-$POS_FALHA_EXTRA_S}"; POS_FALHA_AMOSTRA_S="${S7_POS_FALHA_AMOSTRA_S:-$POS_FALHA_AMOSTRA_S}"
  [ -z "${S7_HASHES:-}" ] || HASHES_ESPERADOS="$(cat "$S7_HASHES")"
fi
PRAZO_S=$(( DUR + MARGEM_S ))
CURA="PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3"

# ---- avaliacao do .log do portao51.sh (criterios da §5.1; o lock deve estar OCUPADO por este flock). Igual a do s5.sh.
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
  grep -qx 'lock: OCUPADO' "$f" || falha "lock nao esta OCUPADO (o s7.sh deve rodar dentro do flock)"
  n=$(grep -cE '^isn[123] mudancas de vista nos ultimos 5 min \(.*\): 0$' "$f"); [ "$n" = 3 ] || falha "nos sem mudanca de vista em 5 min: $n de 3"
  grep -q '^fim ' "$f" || falha "portao nao chegou ao fim"
  [ "$e" = 0 ] && echo "PORTAO 5.1: OK (amostras_ruins=0, maior_sequencia_boa_s=$boa)"
  return "$e"
}
if [ "${1:-}" = --avaliar-portao ]; then avaliar_portao "$2"; exit $?; fi

# ---- utilitarios de avaliacao (so awk/sed/sort/date: rodam tambem no Git Bash do host). stats e copia do s5.sh.
stats() { # stats <arquivo, um numero por linha> -> "n mediana p95 max"
  sort -g "$1" | awk '{v[NR]=$1} END { if (NR == 0) { print "0 - - -"; exit }
    k = int(0.95 * NR); if (k < 0.95 * NR) k++; if (k < 1) k = 1
    printf "%d %.6f %.6f %.6f\n", NR, v[int((NR + 1) / 2)], v[k], v[NR] }'; }
num() { [[ "${1:-}" =~ ^[0-9]+$ ]]; }
seg() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.3f", (b - a) / 1e9 }'; }   # seg <ini_ns> <fim_ns>: segundos
log_ns() { # log_ns <linha de log do Infinispan "AAAA-MM-DD HH:MM:SS,mmm ..."> -> ns (UTC), vazio se ilegivel
  local x; x=$(printf '%s' "$1" | sed -n 's/^\([0-9]\{4\}-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]\),\([0-9]\{3\}\).*/\1.\2/p')
  [ -n "$x" ] && date -u -d "$x UTC" +%s%N 2>/dev/null; }
iso_ns() { date -d "$1" +%s%N 2>/dev/null; }   # iso_ns <instante ISO 8601 do journal (-o short-iso-precise)>
span_s() { # span_s <timespan do systemd, ex. "3min 30s" ou "210s"> -> segundos, ou "?"
  awk -v x="$1" 'BEGIN { n = split(x, p, " "); t = 0; ok = (n > 0)
    for (i = 1; i <= n; i++) { if (!match(p[i], /^[0-9.]+/)) { ok = 0; break }
      v = substr(p[i], 1, RLENGTH); u = substr(p[i], RLENGTH + 1)
      if (u == "h") t += v * 3600; else if (u == "min") t += v * 60; else if (u == "s" || u == "") t += v
      else if (u == "ms") t += v / 1000; else if (u == "us") t += v / 1e6; else { ok = 0; break } }
    if (ok) printf "%g", t; else print "?" }'; }
secao_no() { # secao_no <05-vistas.log> <no>: linhas do bloco "## <no> ..."
  awk -v n="$2" '/^## / { s = ($2 == n); next } s' "$1" 2>/dev/null; }
dels_sudo() { # dels_sudo <05-sudo-journal.txt (journalctl -o json)> <unit>: "t_us origem _SYSTEMD_UNIT" de cada tc qdisc del ... clsact
  # Q-13 (pendente do Gestor), D-1: origem = a unit do dead-man ou a sessao (qualquer outra _SYSTEMD_UNIT conta como sessao)
  grep -E '"MESSAGE":"[^"]*COMMAND=[^"]*nsenter[^"]*tc qdisc del dev eth0 clsact' "$1" 2>/dev/null | awk -v u="$2.service" '{
    t = "?"; s = "-"
    if (match($0, /"__REALTIME_TIMESTAMP":"[0-9]+"/)) t = substr($0, RSTART + 24, RLENGTH - 25)
    if (match($0, /"_SYSTEMD_UNIT":"[^"]*"/)) s = substr($0, RSTART + 17, RLENGTH - 18)
    print t, (s == u ? "unit" : "sessao"), s }'; }

# ---- avaliacao automatica do S7 (decisoes-senior-2026-10-16 §5.7, G-5b-B; V-S4 P-2; Q-13). exit 0 = VALIDO
corpo_unit() { # corpo_unit <unit-armada.txt> <service|timer>: linhas da secao "--- systemctl cat X.<tipo>", sem comentarios e vazias
  awk -v t="$2" '/^--- / { s = ($0 ~ ("^--- systemctl cat .*\\." t "$")); next } s && !/^#/ && NF' "$1" 2>/dev/null; }
avaliar() { # avaliar <dir vm-UTC>
  local D="$1" NF=0 NOK=0 TMPA
  ok_() { echo "OK: $*"; NOK=$((NOK + 1)); }
  fa()  { echo "FALHA: $*"; NF=$((NF + 1)); }
  res() { echo "RESULTADO: $*"; }
  lei() { echo "LEITURA HUMANA: $*"; }
  [ -s "$D/parametros.txt" ] || { echo "FALHA: sem $D/parametros.txt"; echo "S7: INVALIDO (1 falha)"; return 1; }
  pv() { sed -n "s/^$1=//p" "$D/parametros.txt" | head -1; }
  local C="$D/g5b-B-copia" SS="$SAUDE_S" BOOT UNIT bi bf k v prev okord f q n l t u s ts tsp tk tpo tf tmv tsa ta0 ta1
  local nses nuni pri d0 d1 j0 j1 c1 c0 b1 b0 x sv tm
  cr() { sed -n "s/^$1=\([0-9]*\)\$/\1/p" "$C/cronologia.txt" 2>/dev/null | tail -1; }
  BOOT=$(pv BOOT_ESPERADO)
  UNIT=$(sed -n 's/^unit=\([^ ]*\).*/\1/p' "$C/unit-armada.txt" 2>/dev/null | head -1)
  TMPA=$(mktemp -d)
  echo "== avaliacao do S7 $(date -Is) sobre $D"
  echo "   limites: dur=${DUR}s kill>=+${T_KILL_S}s persistencia>=${PERSISTE_S}s prazo do dead-man=${PRAZO_S}s tolerancia=${TOL_S}s saude=${SS}s folga D-2=${D2_FOLGA_S}s/-${D2_PRE_FIRE_S}s unit=${UNIT:-?}"
  ts=$(cr t_start); tsp=$(cr t_split_confirmado); tk=$(cr t_kill); tpo=$(cr t_persistencia_ok); tf=$(cr t_fire)
  tmv=$(cr t_mergeview); tsa=$(cr t_saudavel); ta0=$(cr t_antes_armar); ta1=$(cr t_depois_armar)

  # B1 = A1, A2, A3, A4, A5, A6, A11 e A12 do S6, com a mesma regra (§5.7, V-S4 P-2)
  bi=$(sed -n 's/^boot_id: //p' "$D/00-pre.log" 2>/dev/null | head -1); bf=$(sed -n 's/^boot_id fim: \([^ ]*\).*/\1/p' "$D/07-boot-fim.txt" 2>/dev/null | head -1)
  if [ -n "$bi" ] && [ "$bi" = "$BOOT" ] && [ "$bf" = "$BOOT" ]; then ok_ "B1/A1 boot_id igual ao esperado no inicio e no fim ($bi) [V-S4 P-2]"
  else fa "B1/A1 boot_id inicio=${bi:-?} fim=${bf:-?} esperado=${BOOT:-?} (reboot da VM durante o bloco: o bloco nao vale) [V-S4 P-2]"; fi
  grep -q '^g5b rc=0 ' "$D/rc.txt" 2>/dev/null && ok_ "B1/A2 g5b.sh rc=0 (rc.txt) [§5.7]" || fa "B1/A2 g5b.sh sem rc=0 em rc.txt [§5.7]"
  if [ -s "$C/resultado.txt" ] && ! grep -q '^FAIL' "$C/resultado.txt" && ! grep -q 'ABORTADO' "$C/resultado.txt"; then
    ok_ "B1/A2 resultado.txt do g5b.sh sem FAIL e sem ABORTADO [§5.7]"
  else fa "B1/A2 resultado.txt do g5b.sh ausente, com FAIL ou com ABORTADO [§5.7]"; fi
  if num "$ts" && num "$tsp" && num "$tk" && [ "$ts" -lt "$tsp" ] && [ "$tsp" -lt "$tk" ]; then
    ok_ "B1/A3 split confirmado antes do KILL: t_start < t_split_confirmado (+$(seg "$ts" "$tsp") s) < t_kill (+$(seg "$ts" "$tk") s) [§5.7]"
  else fa "B1/A3 split nao confirmado antes do KILL (t_start=${ts:-?} t_split_confirmado=${tsp:-?} t_kill=${tk:-?}): repeticao do G-5, nao vale [§5.7]"; fi
  if num "$ts" && num "$tk" && [ $(( tk - ts )) -ge $(( T_KILL_S * 1000000000 )) ]; then
    ok_ "B1/A4 KILL em +$(seg "$ts" "$tk") s do start (>= ${T_KILL_S} s) [g5b.sh]"
  else fa "B1/A4 KILL antes de +${T_KILL_S} s do start (t_kill - t_start = $( num "$ts" && num "$tk" && seg "$ts" "$tk" || echo ?) s) [g5b.sh]"; fi
  ns=$(grep -c 'event=start' "$C/marcadores.txt" 2>/dev/null); ne=$(grep -c 'event=end' "$C/marcadores.txt" 2>/dev/null)
  if grep -qx 'rc_injetor=137' "$C/estado.txt" 2>/dev/null && [ "${ns:-0}" = 1 ] && [ "${ne:-1}" = 0 ]; then
    ok_ "B1/A5 rc_injetor=137 (128+SIGKILL), marcadores com 1 start e 0 end [§5.7]"
  else fa "B1/A5 $(grep -m1 '^rc_injetor=' "$C/estado.txt" 2>/dev/null || echo 'rc_injetor=?') (esperado 137), marcadores start=${ns:-?} end=${ne:-?} (esperado 1 e 0) [§5.7]"; fi
  q=0
  awk '/^=== antes do kill/ { s = 1 } /^t_kill_ns=/ { s = 0 } s' "$C/estado.txt" 2>/dev/null | grep -q clsact || { q=1; fa "B1/A6 estado.txt sem clsact antes do KILL [§5.7]"; }
  grep -q clsact "$C/qdisc-apos-kill.txt" 2>/dev/null || { q=1; fa "B1/A6 qdisc-apos-kill.txt sem clsact (o corte nao persistiu depois do KILL) [§5.7]"; }
  if num "$tk" && num "$tpo" && [ $(( tpo - tk )) -ge $(( PERSISTE_S * 1000000000 )) ]; then :
  else q=1; fa "B1/A6 t_persistencia_ok - t_kill = $(num "$tk" && num "$tpo" && seg "$tk" "$tpo" || echo ?) s < ${PERSISTE_S} s [§5.7]"; fi
  n=$(grep -c . "$C/persistencia.txt" 2>/dev/null); x=0
  while read -r t _; do num "$t" && num "$tk" && num "$tpo" && [ "$t" -ge "$tk" ] && [ "$t" -le "$tpo" ] || x=$((x + 1)); done < <(cat "$C/persistencia.txt" 2>/dev/null)
  [ "${n:-0}" -ge 1 ] && [ "$x" = 0 ] || { q=1; fa "B1/A6 persistencia.txt: ${n:-0} amostra(s), $x fora de [t_kill, t_persistencia_ok] (minimo 1, todas dentro) [§5.7]"; }
  grep -q '^PASS corte PERSISTIU' "$C/resultado.txt" 2>/dev/null || { q=1; fa "B1/A6 resultado.txt sem 'PASS corte PERSISTIU' [§5.7]"; }
  x=0; n=0
  while IFS= read -r l; do
    t=$(log_ns "$l"); n=$((n + 1))
    if [ -z "$t" ]; then x=$((x + 1)); echo "  MergeView com instante ilegivel em 05-vistas.log: ${l:0:120}"
    elif num "$tk" && num "$tpo" && [ "$t" -ge "$tk" ] && [ "$t" -le "$tpo" ]; then x=$((x + 1)); echo "  MergeView dentro de [t_kill, t_persistencia_ok]: ${l:0:120}"; fi
  done < <(grep -E 'ISPN000093|MergeView' "$D/05-vistas.log" 2>/dev/null)
  [ -s "$D/05-vistas.log" ] && [ "$x" = 0 ] || { q=1; fa "B1/A6 05-vistas.log ausente ou com MergeView entre t_kill e t_persistencia_ok (ou instante ilegivel): $x linha(s) [§5.7]"; }
  [ "$q" = 0 ] && ok_ "B1/A6 clsact antes e depois do KILL; corte persistiu $(seg "$tk" "$tpo") s (>= ${PERSISTE_S} s) sem MergeView no intervalo [§5.7]"
  head -1 "$D/06-portao51-pos.avaliacao" 2>/dev/null | grep -q '^PORTAO 5.1: OK' \
    && ok_ "B1/A11 portao 5.1 pos: size=3 HEALTHY, caches vazios, 3 netns limpos, sem filtros, 0 timers, sem mudanca de vista em 5 min [§5.1]" \
    || fa "B1/A11 portao 5.1 pos nao passou (ver 06-portao51-pos.avaliacao) [§5.1]"
  # B1/A12. intervalo do saude-log da janela <= 2x o nominal (V-S4 P-2); corpo do laco igual ao do s5.sh (la havia tambem a base)
  for f in "$D/janela/saude.csv"; do
    q=$(awk -F, 'NR > 1 { if ($2 in u) { d = $1 - u[$2]; if (d > m) m = d } u[$2] = $1 } END { printf "%.3f", m / 1e9 }' "$f" 2>/dev/null)
    if [ -s "$f" ] && awk -v g="${q:-999}" -v s="$SS" 'BEGIN { exit !(g <= 2 * s) }'; then
      ok_ "intervalo maximo do saude-log em ${f#"$D"/}: $q s <= 2 x $SS s"
    else fa "intervalo maximo do saude-log em ${f#"$D"/}: ${q:-?} s > 2 x $SS s (pausa do host/VM ou coleta ausente)"; fi
  done

  # B2. nenhum --heal manual: chamadas-injetor.txt com 1 linha (a injecao) e 0 --heal (§5.7)
  n=$(grep -c . "$C/chamadas-injetor.txt" 2>/dev/null); k=$(grep -c -- '--minority isn3 --duration ' "$C/chamadas-injetor.txt" 2>/dev/null)
  v=$(grep -c -- '--heal' "$C/chamadas-injetor.txt" 2>/dev/null)
  if [ "${n:-0}" = 1 ] && [ "${k:-0}" = 1 ] && [ "${v:-1}" = 0 ] && grep -q '^PASS nenhum --heal manual' "$C/resultado.txt" 2>/dev/null; then
    ok_ "B2 nenhum --heal manual: chamadas-injetor.txt so com a injecao; resultado.txt com 'PASS nenhum --heal manual' [§5.7]"
  else fa "B2 chamadas-injetor: ${n:-0} linha(s), ${k:-0} injecao(oes), ${v:-?} --heal (esperado 1, 1, 0) ou resultado.txt sem 'PASS nenhum --heal manual' [§5.7]"; fi

  # B3. disparo entre dur + margem e dur + margem + TOL_S desde a armacao (tempos.txt, recalculado da cronologia) (§5.7)
  d0=$(sed -n 's/.*delta_desde_antes_de_armar_ms=\([0-9]*\).*/\1/p' "$C/tempos.txt" 2>/dev/null | head -1)
  d1=$(sed -n 's/.*delta_desde_depois_de_armar_ms=\([0-9]*\).*/\1/p' "$C/tempos.txt" 2>/dev/null | head -1)
  if num "$tf" && num "$ta0" && num "$ta1"; then j0=$(( (tf - ta0) / 1000000 )); j1=$(( (tf - ta1) / 1000000 )); else j0=; j1=; fi
  if num "$d0" && num "$d1" && [ "$d0" = "$j0" ] && [ "$d1" = "$j1" ] && [ "$d0" -ge $(( PRAZO_S * 1000 )) ] && [ "$d1" -le $(( (PRAZO_S + TOL_S) * 1000 )) ]; then
    ok_ "B3 disparo em $d0 ms desde antes de armar (>= ${PRAZO_S} s) e $d1 ms desde depois de armar (<= ${PRAZO_S}+${TOL_S} s); tempos.txt = cronologia [§5.7]"
  else fa "B3 disparo fora de [${PRAZO_S}, ${PRAZO_S}+${TOL_S}] s ou tempos.txt diferente da cronologia: tempos.txt ${d0:-?}/${d1:-?} ms, cronologia ${j0:-?}/${j1:-?} ms [§5.7]"; fi

  # B4. journal da unit: Heal: removendo, Deactivated successfully|Succeeded, nenhum 'Failed with result'; unit nao failed (§5.7)
  q=0
  for f in "$C/journal-unit.txt" "$D/05-journal-unit.txt"; do
    if grep -q 'Heal: removendo' "$f" 2>/dev/null && grep -Eq 'Deactivated successfully|Succeeded' "$f" && ! grep -q 'Failed with result' "$f"; then :
    else q=1; fa "B4 ${f#"$D"/}: sem 'Heal: removendo', sem 'Deactivated successfully'/'Succeeded' ou com 'Failed with result' [§5.7]"; fi
  done
  [ -s "$C/estado-systemd.txt" ] && ! grep -q '^--- is-failed: failed' "$C/estado-systemd.txt" || { q=1; fa "B4 unit em estado failed (ou estado-systemd.txt ausente) [§5.7]"; }
  [ "$q" = 0 ] && ok_ "B4 journal da unit (copia e 05-journal-unit.txt): heal executado, unit encerrou com sucesso, nao failed [§5.7]"

  # B5. corpo da unit igual ao de producao (armar de g-lib.sh); o .timer difere so em AccuracySec=1s e no prazo (§5.7)
  q=0
  corpo_unit "$C/unit-armada.txt" service > "$TMPA/sv"; corpo_unit "$C/unit-armada.txt" timer > "$TMPA/tm"
  x=$(grep '^ExecStart=.' "$TMPA/sv" | sed 's/^ExecStart=//; s/"//g; s|^/usr/bin/bash |bash |')
  [ "$(grep -c '^ExecStart=.' "$TMPA/sv")" = 1 ] && [ "$x" = "bash scripts/inject-partition.sh --heal --minority isn3" ] \
    || { q=1; fa "B5 ExecStart da unit diferente de 'bash scripts/inject-partition.sh --heal --minority isn3' ('$x') [§5.7]"; }
  x=$(grep '^Environment=' "$TMPA/sv" | sed 's/"//g'); [ "$x" = "Environment=PODMAN=sudo podman" ] || { q=1; fa "B5 Environment da unit '$x' (esperado PODMAN=sudo podman) [§5.7]"; }
  x=$(sed -n 's/^WorkingDirectory=//p' "$TMPA/sv"); [ "$x" = "$(pv TCC_DIR)" ] || { q=1; fa "B5 WorkingDirectory da unit '$x' (esperado $(pv TCC_DIR)) [§5.7]"; }
  x=$(grep -vE '^\[(Unit|Service)\]$|^Description=|^ExecStart=|^Environment=|^WorkingDirectory=' "$TMPA/sv" | paste -sd'|')
  [ -z "$x" ] || { q=1; fa "B5 .service com diretiva a mais em relacao ao armar de producao: $x [§5.7]"; }
  sv=$(grep '^Description=' "$TMPA/sv"); tm=$(grep '^Description=' "$TMPA/tm")
  [ "$sv" = "$tm" ] || { q=1; fa "B5 Description do .timer ('$tm') diferente da do .service ('$sv') [§5.7]"; }
  v=$(sed -n 's/^OnActiveSec=//p' "$TMPA/tm" | head -1)
  [ "$(grep -c '^OnActiveSec=' "$TMPA/tm")" = 1 ] && [ "$(span_s "${v:-?}")" = "$PRAZO_S" ] || { q=1; fa "B5 .timer com OnActiveSec='${v:-?}' (esperado ${PRAZO_S} s) [§5.7]"; }
  [ "$(grep -cx 'AccuracySec=1s' "$TMPA/tm")" = 1 ] || { q=1; fa "B5 .timer sem 'AccuracySec=1s' [§5.7]"; }
  x=$(grep -vE '^\[(Unit|Timer)\]$|^Description=|^OnActiveSec=|^AccuracySec=1s$|^RemainAfterElapse=no$' "$TMPA/tm" | paste -sd'|')
  [ -z "$x" ] || { q=1; fa "B5 .timer com diretiva alem de OnActiveSec/AccuracySec=1s (e RemainAfterElapse=no do systemd-run): $x [§5.7]"; }
  [ "$q" = 0 ] && ok_ "B5 corpo da unit = armar de producao (bash scripts/inject-partition.sh --heal --minority isn3, PODMAN=sudo podman, WorkingDirectory=$(pv TCC_DIR)); .timer so com OnActiveSec=$v e AccuracySec=1s [§5.7]"

  # B6. sem clsact no disparo; MergeView de 3 membros nos 3 logs depois do disparo; HEALTHY x3; 0 timers ao fim SEM systemctl stop (§5.7)
  q=0
  [ -e "$C/qdisc-no-disparo.txt" ] && ! grep -q clsact "$C/qdisc-no-disparo.txt" || { q=1; fa "B6 qdisc-no-disparo.txt ausente ou com clsact [§5.7]"; }
  for n in isn1 isn2 isn3; do
    grep -Eq 'ISPN000093.*MergeView::\[[^]]*\] \(3\)' "$C/log-vista-$n.txt" 2>/dev/null || { q=1; fa "B6 log-vista-$n.txt sem MergeView de 3 membros [§5.7]"; continue; }
    x=0
    while IFS= read -r l; do t=$(log_ns "$l"); num "$t" && num "$tf" && [ "$t" -ge "$tf" ] && x=1; done < <(secao_no "$D/05-vistas.log" "$n" | grep -E 'ISPN000093.*MergeView::\[[^]]*\] \(3\)')
    [ "$x" = 1 ] || { q=1; fa "B6 05-vistas.log: $n sem MergeView de 3 membros depois de t_fire [§5.7]"; }
  done
  n=$(grep -c 'HEALTHY","number_of_nodes":3' "$C/saude-depois.txt" 2>/dev/null)
  [ "${n:-0}" = 3 ] && grep -q 'size=3 HEALTHY apos' "$C/resultado.txt" 2>/dev/null || { q=1; fa "B6 saude-depois.txt com ${n:-0} de 3 portas HEALTHY/3 ou resultado.txt sem 'size=3 HEALTHY apos' [§5.7]"; }
  grep -q '(nenhum)' "$C/estado-systemd.txt" 2>/dev/null && grep -qx 'timers tcc-heal: 0 units tcc-heal: 0' "$D/05-pos-g5b.log" 2>/dev/null \
    || { q=1; fa "B6 timer tcc-heal ao fim (estado-systemd.txt ou 05-pos-g5b.log) [§5.7]"; }
  x=$(grep -cE '"MESSAGE":"[^"]*COMMAND=[^"]*systemctl stop' "$D/05-sudo-journal.txt" 2>/dev/null)
  [ -s "$D/05-sudo-journal.txt" ] && [ "${x:-0}" = 0 ] || { q=1; fa "B6 ${x:-?} 'systemctl stop' no journal do sudo desde o passo 3 (o dead-man foi desarmado?) [§5.7]"; }
  [ "$q" = 0 ] && ok_ "B6 isn3 sem clsact no disparo; MergeView de 3 membros nos 3 nos depois do disparo; HEALTHY x3; 0 timers ao fim sem systemctl stop [§5.7]"

  # B7. cronologia completa e em ordem (§5.7)
  prev=0; okord=1; for k in t_start t_split_confirmado t_kill t_persistencia_ok t_fire t_mergeview t_saudavel; do
    v=$(cr "$k"); num "$v" && [ "$v" -ge "$prev" ] || okord=0; prev=${v:-0}; done
  [ "$okord" = 1 ] && ok_ "B7 cronologia completa e em ordem: t_start t_split_confirmado t_kill t_persistencia_ok t_fire t_mergeview t_saudavel [§5.7]" \
    || fa "B7 cronologia incompleta ou fora de ordem (ver g5b-B-copia/cronologia.txt) [§5.7]"

  # D-1. detector do sudo: 0 tc qdisc del ... clsact da SESSAO entre t_kill e o fim do passo 4; os da unit sao leitura humana (3.4)
  # Q-13 (pendente do Gestor)
  dels_sudo "$D/05-sudo-journal.txt" "${UNIT:-?}" > "$TMPA/dels"
  nses=0; nuni=0; pri=
  while read -r t s u; do
    echo "  sudo tc qdisc del dev eth0 clsact: t_us=$t origem=$s ($u)"
    num "$t" && num "$tk" && [ $(( t * 1000 )) -ge "$tk" ] || continue
    if [ "$s" = sessao ]; then nses=$((nses + 1)); else nuni=$((nuni + 1)); [ -n "$pri" ] || pri=$(( t * 1000 )); fi
  done < "$TMPA/dels"
  if [ -s "$D/05-sudo-journal.txt" ] && [ "$nses" = 0 ]; then ok_ "D-1 detector do sudo: 0 tc qdisc del ... clsact da sessao desde t_kill (nenhuma cura manual) [Q-13]"
  else fa "D-1 detector do sudo: $nses tc qdisc del ... clsact da sessao desde t_kill (cura manual: o bloco nao vale) ou journal do sudo ausente [Q-13]"; fi
  if [ "$nuni" -ge 1 ] && num "$tf" && [ "$pri" -le $(( tf + 5000000000 )) ]; then
    lei "D-1 $nuni tc qdisc del ... clsact da unit do dead-man desde t_kill; o primeiro $(seg "$tf" "$pri") s depois de t_fire (esperado entre t_kill e t_fire + 5 s) [Q-13, 3.4]"
  else lei "D-1 $nuni tc qdisc del ... clsact da unit do dead-man desde t_kill (esperado >= 1, o primeiro ate t_fire + 5 s): nada provou ainda que o sudo de root dentro da unit e registrado [Q-13, 3.4]"; fi

  # D-2. serie do coletor: clsact=1 de t_start + D2_FOLGA_S ate t_fire - D2_PRE_FIRE_S; clsact=0 de t_fire + D2_FOLGA_S em diante (Q-13)
  if num "$ts" && num "$tf"; then
    read -r b1 c1 b0 c0 <<<"$(awk -F, -v a="$ts" -v h="$tf" -v g="$D2_FOLGA_S" -v p="$D2_PRE_FIRE_S" 'NR > 1 {
        if ($1 + 0 >= a + g * 1e9 && $1 + 0 <= h - p * 1e9) { n1++; if ($2 != "1") r1++ }
        if ($1 + 0 >= h + g * 1e9) { n0++; if ($2 != "0") r0++ } }
      END { printf "%d %d %d %d\n", n1, r1, n0, r0 }' "$D/janela/tc-isn3.csv" 2>/dev/null)"
  fi
  if [ "${b1:-0}" -ge 1 ] && [ "${c1:-1}" = 0 ] && [ "${b0:-0}" -ge 1 ] && [ "${c0:-1}" = 0 ]; then
    ok_ "D-2 serie tc de isn3: clsact=1 nas $b1 amostras de t_start+${D2_FOLGA_S}s a t_fire-${D2_PRE_FIRE_S}s e clsact=0 nas $b0 a partir de t_fire+${D2_FOLGA_S}s [Q-13]"
  else fa "D-2 serie tc de isn3 incoerente: ${b1:-0} amostras de t_start+${D2_FOLGA_S}s a t_fire-${D2_PRE_FIRE_S}s (${c1:-?} sem clsact), ${b0:-0} a partir de t_fire+${D2_FOLGA_S}s (${c0:-?} com clsact); minimo 1 em cada [Q-13]"; fi

  # D-3. timer armado com AccuracyUSec=1s e prazo PRAZO_S (Q-13; V-S0/V-S3: precisao do timer)
  k=$(sed -n '1s/.* prazo_s=\([0-9]*\).*/\1/p' "$C/unit-armada.txt" 2>/dev/null)
  v=$(awk '/^--- systemctl cat .*\.timer/ { s = 1; next } /^--- / { s = 0 } s' "$C/unit-armada.txt" 2>/dev/null | sed -n 's/^OnActiveSec=//p' | head -1)
  x=$(span_s "${v:-?}")
  if grep -qx 'AccuracyUSec=1s' "$C/unit-armada.txt" 2>/dev/null && [ "$k" = "$PRAZO_S" ] && [ "$x" = "$PRAZO_S" ]; then
    ok_ "D-3 timer com AccuracyUSec=1s e prazo ${PRAZO_S} s (prazo_s=$k, OnActiveSec=$v) [Q-13]"
  else fa "D-3 timer: $(grep -m1 '^AccuracyUSec=' "$C/unit-armada.txt" 2>/dev/null || echo 'AccuracyUSec=?'), prazo_s=${k:-?}, OnActiveSec=${v:-?} (esperado 1s e ${PRAZO_S} s) [Q-13]"; fi

  # resultados (nao invalidam)
  num "$ts" && num "$tsp" && res "t_split_confirmado - t_start = $(seg "$ts" "$tsp") s"
  num "$tf" && num "$tmv" && res "t_mergeview - t_fire = $(seg "$tf" "$tmv") s (dado de seguranca, NAO e t_merge; §5.7)"
  num "$tf" && num "$tsa" && res "t_saudavel - t_fire = $(seg "$tf" "$tsa") s (dado de seguranca, NAO e t_merge; §5.7)"
  num "$ts" && num "$tf" && res "tempo total de corte (t_start a t_fire) = $(seg "$ts" "$tf") s"
  # Q-14 (pendente do Gestor): timeouts de resolucao de conflito sao resultado, com leitura humana
  q=$(cat "$C"/log-completo-isn*.txt 2>/dev/null | grep -cE 'ISPN000524|ISPN100014|ISPN000638')
  if [ "${q:-0}" = 0 ]; then res "sem ISPN000524/ISPN100014/ISPN000638 nos log-completo-isn*.txt [Q-14]"
  else res "$q linha(s) ISPN000524/ISPN100014/ISPN000638 nos log-completo-isn*.txt [Q-14]"; lei "ler os timeouts de resolucao de conflito nos log-completo-isn*.txt [Q-14]"; fi
  q=$(grep -c 'DEGRADED_MODE' "$D/05-vistas.log" 2>/dev/null); res "${q:-0} linha(s) DEGRADED_MODE nos 3 nos desde o inicio do passo 3 (05-vistas.log)"
  l=$(grep -m1 "Started ${UNIT:-?}.service" "$D/05-journal-unit.txt" 2>/dev/null); t=$(iso_ns "${l%% *}")
  if [ -n "$l" ] && num "$t" && num "$tf"; then lei "inicio da unit no journal (${l%% *}) ate o t_fire medido por polling: $(seg "$t" "$tf") s (espera-se <= 2 s)"
  else lei "inicio da unit ('Started ${UNIT:-?}.service') nao encontrado ou ilegivel em 05-journal-unit.txt: comparar a mao com o t_fire"; fi
  lei "a unit do S7 tem AccuracySec=1s e a de producao nao: o S7 prova o CORPO do dead-man de producao, nao a precisao do timer de producao (pode atrasar ate 60 s, R-3 da V-S2)"
  awk -F, 'NR > 1 && $2 == "11224" && $5 ~ /^[0-9.]+$/ { print $5 }' "$D/janela/saude.csv" 2>/dev/null > "$TMPA/rest3"
  lei "REST de isn3 (11224) time_total na janela: n/mediana/p95/max = $(stats "$TMPA/rest3") (isn3 fica cortado de isn1/isn2, nao do host)"
  lei "refazer os instantes de ISPN000094/DEGRADED_MODE/ISPN000093 nos log-completo-isn*.txt da copia [§5.7]"
  q=$(grep -E '"MESSAGE":"[^"]*COMMAND=[^"]*tc (qdisc|filter) (add|del|change|replace)' "$D/05-sudo-journal.txt" 2>/dev/null | grep -vcE 'tc qdisc del dev eth0 clsact')
  lei "${q:-0} outro(s) comando(s) tc de escrita no journal do sudo desde o passo 3 (injecao, pre-limpeza e sonda do injetor): conferir a origem em 05-sudo-journal.txt"
  rm -rf "$TMPA"
  if [ "$NF" = 0 ]; then echo "S7: VALIDO ($NOK criterios OK)"; return 0; fi
  echo "S7: INVALIDO ($NF falha(s), $NOK OK)"; return 1
}
if [ "${1:-}" = --avaliar ]; then avaliar "$2"; exit $?; fi

# ---- argumentos (secao 2.1 do prompt): nada e lido alem deles antes de valida-los
uso() { echo "S7 ABORTADO (rc=2): $*" >&2
  echo "uso: bash runs/vm-s7-2026-11-04/s7.sh --boot-esperado <uuid> --bloco-anterior <dir vm-UTC do S6> | --dry-run | --hashes | --avaliar-portao <log> | --avaliar <dir>" >&2; exit 2; }
BOOT_ARG=; ANT_ARG=; DRY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --boot-esperado) [ $# -ge 2 ] || uso "falta o valor de --boot-esperado"; BOOT_ARG="$2"; shift 2;;
    --bloco-anterior) [ $# -ge 2 ] || uso "falta o valor de --bloco-anterior"; ANT_ARG="$2"; shift 2;;
    --dry-run) DRY=1; shift;;
    *) uso "argumento desconhecido: $1";;
  esac
done
if [ "$DRY" = 1 ]; then
  cat <<PLANO
S7 (G-5b-B) dry-run: nada executado. --boot-esperado=${BOOT_ARG:-?} --bloco-anterior=${ANT_ARG:-?}
  0. pre-condicoes, so leitura: TCC_DIR, tmux, terminal, lock $LOCK OCUPADO, $INJ_LOCK livre, sudo -n, boot_id atual =
     --boot-esperado = boot_id fim do bloco anterior, kernel/IDs/StartedAt = os do bloco anterior, HEAD contem $COMMIT_REF,
     arvore limpa (so runs/gate-b29/go.log com linhas 'go ' acrescentadas), hashes, ambiente, g5b-B inexistente, g5b-A/resultado.txt existente (o S6 rodou), 0 timers
  1. bash runs/gate-b29/g5b.sh B --dry-run: duracao=${DUR}s, KILL em +${T_KILL_S}s, margem do dead-man=${MARGEM_S}s
  2. portao 5.1 pre (${PORTAO_MIN_S} s); se falhar, PARA (rc 3)
  3. coletor (saude a cada ${SAUDE_S} s e tc qdisc de isn3 a cada ${TC_S} s, so leitura, a partir do GO; espera o GO ate ${ESPERA_GO_S} s)
     e bash runs/gate-b29/g5b.sh A neste terminal: o GO e digitado pelo Gestor no prompt do g5b.sh
  4. copias de g5b-B e go.log, journal da unit e do sudo, timers, tc dos 3 netns, NIC, vistas
  5. portao 5.1 pos (fim >= fim do g5b.sh + ${PORTAO_FOLGA_S} s)   6. boot_id do fim   7. avaliacao e MANIFEST.sha256
--- bash runs/gate-b29/g5b.sh B --dry-run
PLANO
  bash "$(dirname "$AQUI")/gate-b29/g5b.sh" "$MODO" --dry-run; exit $?
fi
[[ "$BOOT_ARG" =~ ^[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$ ]] || uso "--boot-esperado ausente ou fora do formato UUID ('$BOOT_ARG')"
[ -n "$ANT_ARG" ] || uso "--bloco-anterior ausente (o dir vm-<UTC>/ do S6)"
sem_brutos() { echo "S7 ABORTADO (rc=1): bloco anterior sem brutos: $*" >&2; exit 1; }
[ -d "$ANT_ARG" ] || sem_brutos "$ANT_ARG nao e um diretorio"
ANT="$(cd "$ANT_ARG" && pwd)" || sem_brutos "$ANT_ARG ilegivel"
[ -r "$ANT/07-boot-fim.txt" ] || sem_brutos "sem $ANT/07-boot-fim.txt"
[ -r "$ANT/00-pre.log" ] || sem_brutos "sem $ANT/00-pre.log"
ANT_BOOT=$(sed -n 's/^boot_id fim: \([^ ]*\).*/\1/p' "$ANT/07-boot-fim.txt" | head -1)
[ -n "$ANT_BOOT" ] || sem_brutos "$ANT/07-boot-fim.txt sem a linha 'boot_id fim: <uuid>'"
ANT_K=$(sed -n 's/^uname -r: //p' "$ANT/00-pre.log" | head -1)
[ -n "$ANT_K" ] || sem_brutos "$ANT/00-pre.log sem 'uname -r:'"
declare -A ANT_ID ANT_ST
for n in isn1 isn2 isn3; do
  ANT_ID[$n]=$(sed -n "s/^$n ip=[^ ]* id=\([^ ]*\) StartedAt=.*/\1/p" "$ANT/00-pre.log" | head -1)
  ANT_ST[$n]=$(sed -n "s/^$n ip=[^ ]* id=[^ ]* StartedAt=\(.*\)\$/\1/p" "$ANT/00-pre.log" | head -1)
  [ -n "${ANT_ID[$n]}" ] && [ -n "${ANT_ST[$n]}" ] || sem_brutos "$ANT/00-pre.log sem 'id=' e 'StartedAt=' de $n"
done

# ---- coletas (rodam na VM; so leem)
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
nlinhas_go() { local n; n=$(grep -c "^go G-5b-$MODO " "$1" 2>/dev/null); echo "${n:-0}"; }
serie_tc() { # serie_tc <csv> <arquivo-de-parada> <max-s>: tc qdisc show dev eth0 de isn3 a cada TC_S s (SO LEITURA)
  local csv="$1" stop="$2" max="$3" t0 ti o c rest
  echo "t_epoch_ns,clsact,linha" > "$csv"; t0=$(date +%s)
  while [ ! -e "$stop" ] && [ $(( $(date +%s) - t0 )) -lt "$max" ]; do
    ti=$(date +%s%N)
    o=$(sudo nsenter -t "$PID3" -n tc qdisc show dev eth0 2>&1 | tr '\n,' ';;')
    case "$o" in *clsact*) c=1;; *) c=0;; esac
    echo "$ti,$c,$o" >> "$csv"
    rest=$(awk -v a="$ti" -v b="$(date +%s%N)" -v s="$TC_S" 'BEGIN { r = s - (b - a) / 1e9; if (r < 0) r = 0; printf "%.3f", r }')
    sleep "$rest"
  done; }
coletor() { # em segundo plano: espera a linha NOVA de GO do bloco no go.log e coleta ate a parada (ou o limite)
  local J="$OUT/janela" GOLOG="$TCC_DIR/runs/gate-b29/go.log" t0 n0 lim psaude= ptc=
  mkdir -p "$J"
  clog() { echo "$(date -Is) $*" >> "$OUT/03-coletor.log"; }
  trap 'touch "$J/.parar"; [ -z "$psaude" ] || kill -TERM "$psaude" 2>/dev/null; [ -z "$ptc" ] || kill -TERM "$ptc" 2>/dev/null; clog "coletor interrompido"; exit 143' TERM INT
  n0=$(par GO_N0)
  clog "coletor: esperando a linha nova 'go G-5b-$MODO' em $GOLOG (ja ha $n0; ate ${ESPERA_GO_S} s)"
  t0=$(date +%s)
  until [ "$(nlinhas_go "$GOLOG")" -gt "$n0" ]; do
    [ ! -e "$J/.parar" ] || { clog "coletor: g5b.sh terminou sem GO: nada coletado"; return 2; }
    [ $(( $(date +%s) - t0 )) -lt "$ESPERA_GO_S" ] || { clog "coletor: sem GO em ${ESPERA_GO_S} s: nada coletado"; return 2; }
    sleep 0.2
  done
  lim=$(( ESPERA_GO_S + COLETOR_EXTRA_S - ($(date +%s) - t0) ))
  clog "coletor: GO visto; saude a cada ${SAUDE_S} s e tc qdisc show de isn3 (pid $PID3) a cada ${TC_S} s, ate a parada ou ${lim} s"
  saude_loop "$J/saude.csv" "$J/.parar" "$lim" & psaude=$!
  serie_tc "$J/tc-isn3.csv" "$J/.parar" "$lim" & ptc=$!
  wait "$psaude"; wait "$ptc"
  clog "coletor fim (saude $(grep -c . "$J/saude.csv") linhas, tc $(grep -c . "$J/tc-isn3.csv") linhas)"
}
timers_units() { echo "timers tcc-heal: $(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal) units tcc-heal: $(systemctl list-units --all --no-legend 2>/dev/null | grep -c tcc-heal)"; }
tc_3() { local n p; for n in isn1 isn2 isn3; do p=$(sudo podman inspect -f '{{.State.Pid}}' "$n" 2>/dev/null)
  echo "$n pid=$p tc qdisc show dev eth0: $(sudo nsenter -t "$p" -n tc qdisc show dev eth0 2>&1 | tr '\n' ';')"; done; }
nic_linha() { local q; q=$(tc qdisc show dev "$NIC" 2>&1); echo "nic $NIC proibidos(prio|netem|clsact)=$(printf '%s\n' "$q" | grep -cE 'prio|netem|clsact'): $(echo $q)"; }
estado_dm() { local t; t=$(systemctl list-timers --all --no-legend 2>/dev/null | grep tcc-heal); echo "${t:-nenhum timer tcc-heal armado}"; }
pos_falha() { # 3.3 (Q-13, D-5; R-6 da V-s5sh): registra o estado e ESPERA a cura do dead-man ou o prazo + POS_FALHA_EXTRA_S
  local G="runs/gate-b29/g5b-$MODO" u ta lim nt c
  echo "== estado apos falha do g5b.sh $(date -Is)"
  timers_units; echo "timers: $(estado_dm)"; tc_3
  echo "Cura manual (em ~/tcc-gate): $CURA"
  u=$(sed -n 's/^unit=\([^ ]*\).*/\1/p' "$G/unit-armada.txt" 2>/dev/null | head -1)
  if [ -z "$u" ]; then echo "dead-man nao foi armado (sem $G/unit-armada.txt): nada a esperar"; return 0; fi
  ta=$(sed -n 's/^t_depois_armar=\([0-9]*\)$/\1/p' "$G/cronologia.txt" 2>/dev/null | tail -1)
  if [ -n "$ta" ]; then lim=$(( ta / 1000000000 + PRAZO_S + POS_FALHA_EXTRA_S )); else lim=$(( $(date +%s) + PRAZO_S + POS_FALHA_EXTRA_S )); fi
  echo "dead-man $u (prazo ${PRAZO_S} s): esperando 0 timers tcc-heal ate $(date -Is -d "@$lim") (armacao + ${PRAZO_S} + ${POS_FALHA_EXTRA_S} s); amostra a cada ${POS_FALHA_AMOSTRA_S} s"
  while :; do
    nt=$(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal)
    c=$(sudo nsenter -t "$PID3" -n tc qdisc show dev eth0 2>&1 | grep -c clsact)
    echo "$(date -Is) timers_tcc_heal=$nt clsact_isn3=$c"
    [ "$nt" != 0 ] || break
    [ "$(date +%s)" -lt "$lim" ] || { echo "prazo esgotado com $nt timer(s) tcc-heal: use a cura manual"; break; }
    sleep "$POS_FALHA_AMOSTRA_S"
  done
  sudo journalctl -u "$u.service" -u "$u.timer" -o short-iso-precise --no-pager > "$OUT/05-journal-unit.txt" 2>&1
  c=$(sudo nsenter -t "$PID3" -n tc qdisc show dev eth0 2>&1 | grep -c clsact)
  if grep -q "Started $u.service" "$OUT/05-journal-unit.txt"; then echo "cura: o dead-man $u disparou (05-journal-unit.txt); clsact em isn3 ao fim: $c"
  elif [ -s "$G/abort-heal.log" ]; then echo "cura: o trap do g5b.sh chamou o --heal ($G/abort-heal.log); dead-man sem disparo registrado; clsact em isn3 ao fim: $c"
  else echo "cura: sem registro de cura (nem dead-man nem trap); clsact em isn3 ao fim: $c"; fi
}

# ---- 0. TCC_DIR, diretorio de saida e console
if [ -z "${TCC_DIR:-}" ]; then export TCC_DIR="$HOME/tcc-gate"; TCC_ORIGEM="exportado pelo s7.sh"
elif [ "$TCC_DIR" = "$HOME/tcc-gate" ]; then export TCC_DIR; TCC_ORIGEM="ja exportado"
else echo "S7 ABORTADO: TCC_DIR=$TCC_DIR (precisa ser $HOME/tcc-gate)" >&2; exit 1; fi
cd "$TCC_DIR" || { echo "S7 ABORTADO: sem $TCC_DIR" >&2; exit 1; }
[ "$(dirname "$AQUI")" = "$TCC_DIR/runs" ] || { echo "S7 ABORTADO: s7.sh fora de $TCC_DIR/runs ($AQUI)" >&2; exit 1; }
OUT="$AQUI/vm-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT" || exit 1
exec > >(tee -a "$OUT/s7-console.log") 2>&1
TEE_PID=$!
CPID=; MANIF=1
manifesto() { ( cd "$OUT" && find . -type f ! -name MANIFEST.sha256 | sed 's|^\./||' | sort | while IFS= read -r f; do sha256sum "$f"; done ) > "$OUT/MANIFEST.sha256" 2>/dev/null; }
trap 'exec 1>&- 2>&-; wait "$TEE_PID" 2>/dev/null; [ "$MANIF" != 1 ] || manifesto' EXIT
passo_rc() { echo "$1 rc=$2 fim=$(date -Is)" | tee -a "$OUT/rc.txt"; }
abortar() { local rc="$1"; shift; echo "S7 ABORTADO (rc=$rc): $*" >&2; echo "abortado rc=$rc: $* $(date -Is)" >> "$OUT/rc.txt"; exit "$rc"; }
trap 'MANIF=0; [ -z "$CPID" ] || kill -TERM "$CPID" 2>/dev/null; echo "S7 interrompido (Ctrl-C) $(date -Is). Estado do dead-man: $(estado_dm). Se o g5b.sh ja tinha armado, o trap dele faz o --heal (no modo B isso invalida o bloco) e o dead-man (${PRAZO_S} s) segue armado. Cura manual (em ~/tcc-gate): $CURA" >&2; echo "interrompido $(date -Is)" >> "$OUT/rc.txt"; exit 130' INT
echo "S7 (G-5b-B) inicio $(date -Is) saida=$OUT"
echo "TCC_DIR=$TCC_DIR ($TCC_ORIGEM)"

# ---- 0. pre-condicoes (so leitura); qualquer falha aborta antes do portao e do g5b.sh
{
  E=0; nok() { echo "NAO OK: $*"; E=1; }
  PAR="$OUT/parametros.txt"
  echo "== pre-condicoes $(date -Is)"
  echo "hostname=$(hostname) ip=$(hostname -I 2>/dev/null | awk '{print $1}') usuario=$(id -un)"
  echo "argumentos: --boot-esperado $BOOT_ARG --bloco-anterior $ANT"
  [ -n "${TMUX:-}" ] && echo "tmux: $TMUX" || nok "fora do tmux (TMUX vazio)"
  [ -t 0 ] || [ "$TESTE" = 1 ] && echo "terminal: ok (o GO do g5b.sh e interativo)" || nok "sem terminal (o GO do g5b.sh e interativo)"
  if flock -n "$LOCK" true; then nok "lock $LOCK LIVRE: rode dentro de 'flock $LOCK tmux new -s s7'"; else echo "lock $LOCK: OCUPADO (este flock)"; fi
  if flock -n "$INJ_LOCK" true; then echo "lock do injetor $INJ_LOCK: livre"; else nok "lock do injetor $INJ_LOCK OCUPADO (injecao de particao em andamento?)"; fi
  SUDO=0; sudo -n true && { echo "sudo -n: ok"; SUDO=1; } || nok "sudo -n true falhou"
  K=$(uname -r); echo "uname -r: $K"; echo "uname -r do bloco anterior: $ANT_K"
  [ "$K" = "$ANT_K" ] || nok "kernel $K diferente do bloco anterior ($ANT_K)"
  B=$(cat "$BOOT_ID_FILE" 2>/dev/null); echo "boot_id: $B"; echo "boot_id esperado (--boot-esperado): $BOOT_ARG"; echo "boot_id fim do bloco anterior: $ANT_BOOT"
  # Q-11 (pendente do Gestor): qualquer diferenca e NAO OK, nunca aviso
  [ "$B" = "$BOOT_ARG" ] || nok "boot_id atual $B diferente de --boot-esperado $BOOT_ARG: reboot da VM entre blocos ou referencia errada: parar e chamar o Senior"
  [ "$BOOT_ARG" = "$ANT_BOOT" ] || nok "--boot-esperado $BOOT_ARG diferente do boot_id fim do bloco anterior ($ANT_BOOT): reboot da VM entre blocos ou referencia errada: parar e chamar o Senior"
  echo "bloco anterior: $(tail -1 "$ANT/08-avaliacao.txt" 2>/dev/null || echo 'sem 08-avaliacao.txt')"
  echo "HEAD: $(git rev-parse HEAD 2>/dev/null) ($(git log -1 --format=%s 2>/dev/null))"
  git merge-base --is-ancestor "$COMMIT_REF" HEAD 2>/dev/null && echo "contem $COMMIT_REF (v4c): sim" || nok "HEAD nao contem $COMMIT_REF (v4c)"
  # Q-12 (pendente do Gestor): o unico versionado que pode estar modificado e runs/gate-b29/go.log, com HEAD como prefixo exato
  # e cada linha a mais comecando com "go " (o exigir_go de cada bloco acrescenta uma)
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
  echo "-- sha256 (referencia v4c)"
  if printf '%s\n' "$HASHES_ESPERADOS" | sha256sum -c -; then echo "hashes: iguais a referencia"; else nok "hash diferente da referencia"; fi
  echo "s7.sh: $(sha256sum "$SELF" | cut -d' ' -f1)"
  V=$(env | grep -E '^(LOCK_DIR|PODMAN|INJ|LOG|DMS|UNIT|G_TESTE|G5B_[A-Z0-9_]*|G1_[A-Z0-9_]*|REP|COLETA|SONDA_[A-Z0-9_]*|L0C_[A-Z0-9_]*|TCC_[A-Z0-9_]*)=' | cut -d= -f1 | grep -vx TCC_DIR | paste -sd' ')
  [ -z "$V" ] && echo "ambiente: sem LOCK_DIR/PODMAN/INJ/LOG/DMS/UNIT/G_TESTE/G5B_*/... herdados" || nok "variaveis herdadas que mudariam o g5b.sh/injetor: $V"
  [ ! -e "runs/gate-b29/g5b-$MODO" ] && echo "runs/gate-b29/g5b-$MODO: inexistente" \
    || nok "runs/gate-b29/g5b-$MODO ja existe (execucao anterior do G-5b-$MODO? mover para fora antes, sem apagar)"
  [ -s runs/gate-b29/g5b-A/resultado.txt ] && echo "runs/gate-b29/g5b-A/resultado.txt: existe (o S6 rodou antes): $(tail -1 runs/gate-b29/g5b-A/resultado.txt)" \
    || nok "runs/gate-b29/g5b-A/resultado.txt ausente: o S6 (G-5b-A) tem de rodar antes do S7 (§5.7, A antes de B)"
  T=$(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal); U=$(systemctl list-units --all --no-legend 2>/dev/null | grep -c tcc-heal)
  echo "timers tcc-heal: $T units tcc-heal: $U"; [ "$T" = 0 ] || nok "$T timers tcc-heal armados"; [ "$U" = 0 ] || nok "$U units tcc-heal carregadas"
  echo "-- $GL (blocos anteriores)"; cat "$GL" 2>/dev/null || echo "(sem go.log)"
  {
    echo "BLOCO=$BLOCO"; echo "MODO=$MODO"; echo "BOOT_ESPERADO=$BOOT_ARG"; echo "BLOCO_ANTERIOR=$ANT"; echo "KERNEL=$K"
    echo "TCC_DIR=$TCC_DIR"; echo "SAUDE_S=$SAUDE_S"; echo "TC_S=$TC_S"; echo "DUR=$DUR"; echo "T_KILL_S=$T_KILL_S"
    echo "MARGEM_S=$MARGEM_S"; echo "PRAZO_S=$PRAZO_S"; echo "PERSISTE_S=$PERSISTE_S"; echo "GO_N0=$(nlinhas_go "$GL")"
  } > "$PAR"
  VMIP=$(hostname -I 2>/dev/null | awk '{print $1}'); echo "VMIP=$VMIP" >> "$PAR"; [ -n "$VMIP" ] || nok "IP da VM vazio (hostname -I)"
  NIC=$(ip route show default 2>/dev/null | awk '{ for (i = 1; i < NF; i++) if ($i == "dev") { print $(i + 1); exit } }'); echo "NIC=$NIC" >> "$PAR"
  if [ -z "$NIC" ]; then nok "sem rota padrao (NIC da VM desconhecida)"
  else NL=$(nic_linha); echo "$NL"; case "$NL" in "nic $NIC proibidos(prio|netem|clsact)=0:"*) ;; *) nok "NIC da VM ($NIC) com prio/netem/clsact";; esac; fi
  if [ "$SUDO" = 1 ]; then
    echo "-- conteineres e cluster atual"
    sudo podman ps -a --format '{{.Names}} {{.ID}} {{.Status}} criado={{.CreatedAt}}' 2>&1
    for n in isn1 isn2 isn3; do
      ip=$(sudo podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$n" 2>/dev/null | awk '{print $1}')
      id=$(sudo podman inspect -f '{{.Id}}' "$n" 2>&1 | cut -c1-12); st=$(sudo podman inspect -f '{{.State.StartedAt}}' "$n" 2>&1)
      echo "$n ip=$ip id=$id StartedAt=$st"
      [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || nok "IP de $n invalido ('$ip')"; echo "IP_$n=$ip" >> "$PAR"
      # Q-13 (pendente do Gestor), D-4: conteiner recriado ou reiniciado entre blocos muda o historico do cluster
      [ "$id" = "${ANT_ID[$n]}" ] || nok "id de $n ($id) diferente do bloco anterior (${ANT_ID[$n]}): conteiner recriado entre blocos"
      [ "$st" = "${ANT_ST[$n]}" ] || nok "StartedAt de $n ($st) diferente do bloco anterior (${ANT_ST[$n]}): conteiner reiniciado entre blocos"
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
      echo "isn3 JGroups 7800: ipv4=$n4 ipv6=$n6 (so registro: com IPv6 o split nao acontece e o g5b.sh falha sozinho)"
    fi
  fi
  exit "$E"
} 2>&1 | tee "$OUT/00-pre.log"; RC=${PIPESTATUS[0]}; passo_rc pre "$RC"
[ "$RC" = 0 ] || abortar 1 "pre-condicoes (ver $OUT/00-pre.log)"
par() { sed -n "s/^$1=//p" "$OUT/parametros.txt" | head -1; }
PID3=$(par PID_isn3); VMIP=$(par VMIP); NIC=$(par NIC); GO_N0=$(par GO_N0)

# ---- 1. g5b.sh B --dry-run e parametros efetivos
bash runs/gate-b29/g5b.sh "$MODO" --dry-run 2>&1 | tee "$OUT/01-g5b-dry-run.log"; RC=${PIPESTATUS[0]}; passo_rc g5b-dry-run "$RC"
[ "$RC" = 0 ] || abortar 1 "g5b.sh $MODO --dry-run rc=$RC (ver 01-g5b-dry-run.log)"
for t in "G-5b-$MODO (dry-run; nada executado)" "duracao=${DUR}s" "KILL em +${T_KILL_S}s" "margem do dead-man=${MARGEM_S}s" "${DUR}+${MARGEM_S} = ${PRAZO_S} s, AccuracySec=1s" \
         "Disparo entre ${DUR}+${MARGEM_S}=${PRAZO_S} s e +${TOL_S} s da armacao"; do
  grep -qF -- "$t" "$OUT/01-g5b-dry-run.log" || abortar 1 "g5b.sh $MODO --dry-run sem '$t' (parametros efetivos diferentes do plano)"
done
echo "parametros efetivos do g5b.sh $MODO conferidos: duracao=${DUR}s, KILL em +${T_KILL_S}s, margem do dead-man=${MARGEM_S}s, tolerancia do disparo=${TOL_S}s" | tee -a "$OUT/rc.txt"

# ---- 2. portao 5.1 pre (com .log gravado); se falhar, PARA. portao() e copia do s5.sh
portao() { # portao <nome> <duracao-s>
  local rc
  echo "portao $1: $2 s a partir de $(date -Is)"
  bash runs/vm-s2-2026-10-27/portao51.sh "$2" "$OUT/$1.csv" 2>&1 | tee "$OUT/$1.log"; rc=${PIPESTATUS[0]}; passo_rc "$1" "$rc"
  avaliar_portao "$OUT/$1.log" | tee "$OUT/$1.avaliacao"; rc=${PIPESTATUS[0]}; passo_rc "$1-avaliacao" "$rc"; return "$rc"
}
portao 02-portao51-pre "$PORTAO_MIN_S" || abortar 3 "portao 5.1 pre falhou: S7 NAO roda (ver 02-portao51-pre.log/.avaliacao)"

# ---- 3. coletor e g5b.sh A (GO digitado pelo Gestor no prompt do g5b.sh)
T3_EPOCH=$(date +%s); T3_ISO=$(date -Is -d "@$T3_EPOCH")
echo "T3_EPOCH=$T3_EPOCH" >> "$OUT/parametros.txt"
coletor & CPID=$!
echo "coletor em segundo plano pid=$CPID $(date -Is)" | tee -a "$OUT/rc.txt"
# Q-16 (pendente do Gestor): no S7 nao curar a mao antes de prazo + tolerancia + folga; o g5b.sh cura sozinho se o dead-man falhar
echo ">>> AVISO: o no isn3 fica cortado ate ~$PRAZO_S s depois de armar ($DUR + $MARGEM_S s, AccuracySec=1s); NAO curar a mao: qualquer --heal manual invalida o bloco (§5.7). Se o dead-man nao disparar ate $PRAZO_S + $TOL_S + $G5B_FOLGA = $(( PRAZO_S + TOL_S + G5B_FOLGA )) s, o g5b.sh falha e o trap dele cura; so se o proprio g5b.sh travar use a cura manual, sabendo que o bloco fica registrado como falha do dead-man. Cura manual, em ~/tcc-gate: $CURA"
echo ">>> G-5b-$MODO: digite GO no prompt do g5b.sh em ate $(( ESPERA_GO_S / 60 )) min; depois disso responda qualquer outra coisa (aborta sem armar) e recomece."
# o tee ignora SIGINT: num Ctrl-C o g5b.sh continua com onde escrever e o trap dele (--heal) roda ate o fim
bash runs/gate-b29/g5b.sh "$MODO" 2>&1 | ( trap '' INT; exec tee "$OUT/04-g5b.log" ); RC=${PIPESTATUS[0]}; T_G5B_FIM=$(date +%s); passo_rc g5b "$RC"
mkdir -p "$OUT/janela"; touch "$OUT/janela/.parar"
for _ in $(seq 1 "$ESPERA_COLETOR_S"); do kill -0 "$CPID" 2>/dev/null || break; sleep 1; done
kill -0 "$CPID" 2>/dev/null && { kill -TERM "$CPID" 2>/dev/null; echo "coletor nao terminou em ${ESPERA_COLETOR_S} s: encerrado" | tee -a "$OUT/rc.txt"; }
wait "$CPID"; passo_rc coletor "$?"
CPID=; rm -f "$OUT/janela/.parar"

# ---- 4. depois do g5b.sh: copias, journal da unit e do sudo, timers, tc, vistas
G="runs/gate-b29/g5b-$MODO"
if [ -d "$G" ]; then cp -r "$G" "$OUT/g5b-$MODO-copia"; ( cd "$OUT" && find "g5b-$MODO-copia" -type f | sort | xargs sha256sum ) > "$OUT/g5b-$MODO.sha256" 2>/dev/null
else echo "AVISO: $G ausente"; fi
cp runs/gate-b29/go.log "$OUT/go.log" 2>/dev/null || echo "AVISO: runs/gate-b29/go.log ausente"
if [ "$RC" != 0 ]; then
  if [ "$(nlinhas_go runs/gate-b29/go.log)" -le "$GO_N0" ]; then abortar 4 "g5b.sh rc=$RC sem GO (saiu antes de armar; nada a curar)"; fi
  pos_falha 2>&1 | tee "$OUT/05-pos-falha.log"; passo_rc pos-falha "${PIPESTATUS[0]}"
  abortar 4 "g5b.sh rc=$RC (ver 04-g5b.log e 05-pos-falha.log)"
fi
UNIT=$(sed -n 's/^unit=\([^ ]*\).*/\1/p' "$OUT/g5b-$MODO-copia/unit-armada.txt" 2>/dev/null | head -1)
{
  echo "== depois do g5b.sh $(date -Is)"
  timers_units; echo "timers: $(estado_dm)"; tc_3; nic_linha
  tail -1 "$OUT/go.log"
  echo "unit do dead-man: ${UNIT:-?}"
  sudo journalctl -u "$UNIT.service" -u "$UNIT.timer" -o short-iso-precise --no-pager > "$OUT/05-journal-unit.txt" 2>&1
  echo "journal da unit: $(grep -c . "$OUT/05-journal-unit.txt") linha(s) (05-journal-unit.txt)"
  for n in isn1 isn2 isn3; do
    echo "## $n podman logs desde $T3_ISO"
    sudo podman logs --since "$T3_ISO" "$n" 2>&1 | grep -E 'ISPN000094|ISPN000093|ISPN100000|ISPN100001|MergeView|DEGRADED_MODE' | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-260
  done > "$OUT/05-vistas.log"
  echo "linhas de vista/DEGRADED desde o inicio do passo 3: $(grep -vc '^## ' "$OUT/05-vistas.log")"
  # 3.4: journal do sudo desde o inicio do passo 3 (detector de cura manual; -o json, sem depender de jq)
  sudo journalctl _COMM=sudo --since "@$T3_EPOCH" -o json --no-pager > "$OUT/05-sudo-journal.txt" 2>&1
  echo "journal do sudo desde @$T3_EPOCH: $(grep -c . "$OUT/05-sudo-journal.txt") entrada(s); tc qdisc del ... clsact:"
  dels_sudo "$OUT/05-sudo-journal.txt" "${UNIT:-?}"
} 2>&1 | tee "$OUT/05-pos-g5b.log"; RC=${PIPESTATUS[0]}; passo_rc pos-g5b "$RC"

# ---- 5. portao pos (fim >= fim do g5b.sh + PORTAO_FOLGA_S), 6. boot_id do fim e 7. avaliacao
DUR_P=$(( T_G5B_FIM + PORTAO_FOLGA_S - $(date +%s) )); [ "$DUR_P" -ge "$PORTAO_MIN_S" ] || DUR_P=$PORTAO_MIN_S
FIM=0
portao 06-portao51-pos "$DUR_P" || FIM=5
echo "boot_id fim: $(cat "$BOOT_ID_FILE" 2>/dev/null) $(date -Is)" | tee "$OUT/07-boot-fim.txt"; RC=${PIPESTATUS[0]}; passo_rc boot-fim "$RC"
avaliar "$OUT" | tee "$OUT/08-avaliacao.txt"; RC=${PIPESTATUS[0]}; passo_rc avaliacao "$RC"
[ "$RC" = 0 ] || [ "$FIM" != 0 ] || FIM=6
echo "S7 fim $(date -Is) rc=$FIM. Saia do tmux com exit (libera o lock). MANIFEST.sha256 e gravado ao sair." | tee -a "$OUT/rc.txt"
exit "$FIM"
