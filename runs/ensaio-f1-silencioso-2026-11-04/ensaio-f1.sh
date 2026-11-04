#!/usr/bin/env bash
# runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh -- ensaio do F1 silencioso na VM (prompt-f1-kill-2026-10-27.md §4.2;
# prompt do Worker: docs/prompt-worker-ensaio-f1-2026-11-04.md). 4 execucoes de F1 (2 sem carga, 2 com S1 sync/async).
#
# Uso (dentro do tmux aberto sob flock /var/lock/tcc-vm.lock, em ~/tcc-gate, com RB_PASSWORD lido antes por read -rs):
#   bash runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh --rb-dir <git archive 20805b0 + jar congelado> \
#        --boot-esperado <uuid> --bloco-anterior <dir vm-UTC do S7> [--so 1|2|3|4]
#   ... --dry-run --rb-dir <dir>      plano (constantes lidas do run-rebaseline.sh do --rb-dir, injetor --dry-run); nada tocado
#   ... --hashes                      tabela de hashes embutida (adenda da DT-009 parte 2)
#   ... --avaliar <dir vm-UTC>        refaz a avaliacao (tambem no host, Git Bash, sobre a copia)
#
# O ensaio NAO injeta nada por conta propria: quem isola, mata e religa isn2 e o inject-crash.sh do --rb-dir (DT-017), com
# PODMAN='sudo podman'. Este script so le (podman inspect/logs/events, nsenter ... tc qdisc show / ip -br link, curl) e
# lanca o workload e o VerificadorSanidade do jar do --rb-dir. As constantes do PR (DURACAO, WARMUP_MIN, THREADS, OPS,
# REP, MIX, RESET_STATE, S0, PASSO_SEMENTE, INJ_OFFSET, F1_DUR, K1_*, F1_VOLTA_*, ANCORA_TIMEOUT_S, CLASSE_VERIFICADOR),
# SERVERS/USERNAME e o corpo de montar_cmd sao lidos do run-rebaseline.sh do --rb-dir (hash conferido), nao redigitados.
# Passos: 0 pre-condicoes (so leitura) -> 1 injetor --dry-run -> 2 portao K1 -> 3..6 execucoes (GO por execucao, portao K1,
# injecao, volta, coletas) -> 7 estado final -> 8 avaliacao e MANIFEST.sha256. Saida em runs/ensaio-f1-silencioso-.../vm-<UTC>/.
# Saida: 0 todas validas; 1 pre-condicao; 2 argumentos; 3 portao (ou GO ausente/errado: nada injetado); 4 parada (Δdet < 30 s,
# cluster que nao volta em 180 s do start, injetor com exit != 0; o script NAO conserta); 6 avaliacao com execucao invalida;
# 130 Ctrl-C. ENSAIO_TESTE=1 so nos testes com shims (ensaio-f1.test.sh).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="$AQUI/ensaio-f1.sh"
TESTE="${ENSAIO_TESTE:-0}"

BOOT_ID_FILE=/proc/sys/kernel/random/boot_id
LOCK=/var/lock/tcc-vm.lock
UUID_RE='^[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$'
NOS="isn1 isn2 isn3"
PORTAS="11222 11223 11224"
CACHES="sessions counters sessions-async counters-async"
SAUDE_S=2                 # intervalo nominal do saude-log (o saude_loop e copia verbatim do s5.sh)
ESPERA_GO_S=900           # o GO de cada execucao tem de vir em ate 15 min do prompt
DDET_MIN_S=30             # §4.1: Δdet < 30 s PARA o ensaio
DDET_FAIXA="32 49"        # validacao-f1-silencioso item 6: so RESULTADO (nao e criterio)
FOLGA_RECRIACAO_S=600     # StartedAt - Created <= 10 min: o conteiner nao reiniciou desde a recriacao
INJ_ESPERA_TRAP_S=150     # Ctrl-C: espera o trap do injetor devolver isn2 (o podman start levou 51 s no WSL)
ANCORA_RE='^Warm-up ate \+'   # = ANCORA_RE de run-rebaseline.sh (o teste confere a igualdade)
XML_DESTINO=/opt/infinispan/server/conf/infinispan.xml
XML_SHA=6d52413b1c3de47b63179430bfadb02dc29d74ba90b919c584c66ff25437778c   # cluster/infinispan-cluster.xml de 20805b0
LISTA_SHA=003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4  # lista da adenda da DT-009 parte 2
# Adenda da DT-009 parte 2 (wt-plano-ctrl/docs/decisoes-tecnicas.md:541), formato sha256sum -b, na ordem da tabela
HASHES_RB='3005f49ae5696dcd9ace324b08295187100fcde39c111115a89c517bc4e3c7cd *analysis/rebaseline.py
8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35 *runs/rebaseline/matriz.tsv
5b4d571986256d96c73405ac887eca55c783787ad0bc0a0c5dec558856ddc0cf *scripts/inject-crash.sh
7e68962b1b7f508ee84ca7561a20c0bba36c43ea4597d7390e7c8091cd24e529 *scripts/inject-jitter.sh
3531916a863832fd5aea050910111c69f0c9130c0bd325c3ad3f238e44a3533a *scripts/rebaseline_matriz.py
d95305fee402b1f882d387a40e81d4f83df72d7fe4b6daf810516e0627267366 *scripts/run-rebaseline.sh
5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf *workload/target/session-workload-0.1.0-SNAPSHOT.jar'
# E-3 (pendente do Gestor): i = 2000..2003, semente = S0 + PASSO_SEMENTE * i (fora da campanha, do piloto e da R-S2)
# E-4 (pendente do Gestor): as 2 sem carga sao so o injetor, depois do portao, sem workload
# n i carga replicacao
EXECUCOES_TAB='1 2000 sem -
2 2001 sem -
3 2002 S1 sync
4 2003 S1 async'
# Linhas do injetor (inject-crash.sh de 20805b0) usadas como carimbo
RE_ISO='inject-crash\.sh: isolando isn2: ip link set dev eth0 down'
RE_KILL='inject-crash\.sh: .*podman kill isn2 \(--signal KILL\)'
RE_START='inject-crash\.sh: .*podman start isn2$'
RE_OK='inject-crash\.sh: OK: F1 \(modo=silencioso\)'

if [ "$TESTE" = 1 ]; then
  BOOT_ID_FILE="${ENSAIO_BOOT_ID_FILE:-$BOOT_ID_FILE}"; SAUDE_S="${ENSAIO_SAUDE_S:-$SAUDE_S}"
  ESPERA_GO_S="${ENSAIO_ESPERA_GO_S:-$ESPERA_GO_S}"; INJ_ESPERA_TRAP_S="${ENSAIO_INJ_ESPERA_TRAP_S:-$INJ_ESPERA_TRAP_S}"
  [ -z "${ENSAIO_HASHES:-}" ] || HASHES_RB="$(cat "$ENSAIO_HASHES")"
  LISTA_SHA="${ENSAIO_LISTA_SHA:-$LISTA_SHA}"; XML_SHA="${ENSAIO_XML_SHA:-$XML_SHA}"
fi

uso() { sed -n '2,23p' "$SELF" | sed 's/^# \{0,1\}//'; }
erro_uso() { echo "ensaio-f1.sh: erro de uso: $*" >&2; exit 2; }

# ---- argumentos
MODO=executar; RB=; BOOT_ARG=; ANT=; SO=; AVAL_DIR=
while [ $# -gt 0 ]; do
  case "$1" in
    --rb-dir|--boot-esperado|--bloco-anterior|--so|--avaliar)
      [ $# -ge 2 ] || erro_uso "$1 exige valor"
      case "$1" in --rb-dir) RB="$2";; --boot-esperado) BOOT_ARG="$2";; --bloco-anterior) ANT="$2";; --so) SO="$2";;
        --avaliar) MODO=avaliar; AVAL_DIR="$2";; esac
      shift 2;;
    --dry-run) MODO=dry; shift;;
    --hashes) MODO=hashes; shift;;
    -h|--help) uso; exit 0;;
    *) erro_uso "argumento desconhecido '$1'";;
  esac
done

if [ "$MODO" = hashes ]; then
  printf '%s\n' "$HASHES_RB"; echo "lista (sha256 das 7 linhas acima): $LISTA_SHA"
  echo "cluster/infinispan-cluster.xml em uso: $XML_SHA"; exit 0
fi

# ---- utilitarios de avaliacao (so awk/sed/sort/date: rodam tambem no Git Bash do host). stats e tcp_s: verbatim do s5.sh
stats() { # stats <arquivo, um numero por linha> -> "n mediana p95 max"
  sort -g "$1" | awk '{v[NR]=$1} END { if (NR == 0) { print "0 - - -"; exit }
    k = int(0.95 * NR); if (k < 0.95 * NR) k++; if (k < 1) k = 1
    printf "%d %.6f %.6f %.6f\n", NR, v[int((NR + 1) / 2)], v[k], v[NR] }'; }
agora_ms() { date +%s%3N; }
iso_de_ms() { date -u -d "@$(( $1 / 1000 )).$(printf '%03d' $(( $1 % 1000 )))" +%Y-%m-%dT%H:%M:%S.%3NZ; }
s_de_ms() { awk -v v="$1" 'BEGIN { printf "%.3f", v / 1000 }'; }
semcor() { sed 's/\x1b\[[0-9;]*m//g'; }
inj_ms() { # inj_ms <dir-exec> <regex>: carimbo (ms) da primeira linha do injetor.log que casa
  local l; l=$(grep -m1 -E "$2" "$1/injetor.log" 2>/dev/null | sed -n 's/^\[\([^]]*\)\].*/\1/p')
  [ -n "$l" ] && date -u -d "$l" +%s%3N 2>/dev/null; }
linha_ms() { date -u -d "${1%% *}" +%s%3N 2>/dev/null; }  # carimbo do podman logs -t (1o campo)
vista_ms() { # vista_ms <dir-exec> <no> <sem|com> <desde_ms>: 1a ISPN000094 sem (ou com) isn2 no log do no, a partir de desde_ms
  local l v m
  while IFS= read -r l; do
    v=$(linha_ms "$l") || continue; [ -n "$v" ] && [ "$v" -ge "$4" ] || continue
    m=$(printf '%s' "$l" | sed -n 's/.*ISPN000094:.*([0-9]*) \[\([^]]*\)\].*/\1/p'); [ -n "$m" ] || continue
    case "$m" in *isn2*) [ "$3" = com ] || continue;; *) [ "$3" = sem ] || continue;; esac
    echo "$v"; return 0
  done < <(grep 'ISPN000094' "$1/logs-$2.txt" 2>/dev/null | semcor)
  return 1; }
ddet_ms() { # ddet_ms <dir-exec> <no>: ms da linha de isolamento do injetor.log ate a 1a vista sem isn2 no no
  local t0 v; t0=$(inj_ms "$1" "$RE_ISO") || return 1; v=$(vista_ms "$1" "$2" sem "$t0") || return 1; echo $((v - t0)); }
vida_isn2() { # vida_isn2 <dir-exec> <antiga|nova>: linhas do log de isn2 antes/depois do 'podman start isn2' do injetor
  local ts l v; ts=$(inj_ms "$1" "$RE_START") || return 1
  while IFS= read -r l; do v=$(linha_ms "$l") || continue; [ -n "$v" ] || continue
    if [ "$2" = antiga ]; then [ "$v" -lt "$ts" ] && printf '%s\n' "$l"; else [ "$v" -ge "$ts" ] && printf '%s\n' "$l"; fi
  done < <(semcor < "$1/logs-isn2.txt" 2>/dev/null)
  return 0; }
ev() { sed -n "s/^$2=//p" "$1/execucao.txt" 2>/dev/null | tail -1; }   # ev <dir-exec> <chave>

# ---- avaliacao automatica (seção 5 do prompt). exit 0 = todas validas
avaliar() { # avaliar <dir vm-UTC>
  local D="$1" NV=0 NE=0 G=0 linha n i carga modo E NF NOK
  [ -s "$D/parametros.txt" ] || { echo "FALHA: sem $D/parametros.txt"; echo "ENSAIO: 0 de ? VALIDAS"; return 1; }
  pv() { sed -n "s/^$1=//p" "$D/parametros.txt" | head -1; }
  local BOOT INJ_OFF DMIN VMAX SS FAIXA EXECS bi bf q f x a b c p iso kill st t1 t3 v1 v3 nd n137 ok vazios
  BOOT=$(pv BOOT_ESPERADO); INJ_OFF=$(pv INJ_OFFSET); DMIN=$(pv DDET_MIN_S); VMAX=$(pv F1_VOLTA_TIMEOUT_S); SS=$(pv SAUDE_S)
  FAIXA=$(pv DDET_FAIXA); EXECS=$(pv EXECUCOES)
  echo "== avaliacao do ensaio do F1 silencioso $(date -Is) sobre $D"
  echo "-- geral"
  # F-9 (geral): boot_id no inicio e no fim do ensaio
  bi=$(sed -n 's/^boot_id: //p' "$D/00-pre.log" 2>/dev/null | head -1)
  bf=$(sed -n 's/^boot_id fim: \([^ ]*\).*/\1/p' "$D/07-boot-fim.txt" 2>/dev/null | head -1)
  if [ -n "$bi" ] && [ "$bi" = "$BOOT" ] && [ "$bf" = "$BOOT" ]; then echo "OK: F-9 boot_id igual no inicio e no fim do ensaio ($bi)"
  else echo "FALHA: F-9 boot_id inicio=${bi:-?} fim=${bf:-?} esperado=$BOOT (reboot da VM ou do host: nenhuma execucao vale)"; G=1; fi
  # F-10 (geral): estado final. D-1 (pendente do Gestor): "caches vazios" so para os caches que nenhuma execucao com carga
  # desta invocacao usou (o workload S1 deixa dados nos caches dele; o reset e no inicio da execucao seguinte)
  vazios="$CACHES"
  for linha in $EXECS; do IFS=: read -r n i carga modo <<<"$linha"
    case "$modo" in sync) vazios=$(printf '%s\n' $vazios | grep -vxE 'sessions|counters' | paste -sd' ');;
      async) vazios=$(printf '%s\n' $vazios | grep -vxE 'sessions-async|counters-async' | paste -sd' ');; esac; done
  f="$D/07-estado-final.log"; ok=1
  grep -q '^PORTAO K1: OK' "$f" 2>/dev/null || { echo "FALHA: F-10 portao K1 final nao passou (07-estado-final.log)"; ok=0; }
  grep -qx 'timers tcc-heal: 0' "$f" 2>/dev/null || { echo "FALHA: F-10 timers tcc-heal no fim diferentes de 0"; ok=0; }
  for c in $vazios; do for p in $PORTAS; do
    grep -qx "size $c porta $p: 0" "$f" 2>/dev/null || { echo "FALHA: F-10 cache $c (porta $p) nao vazio no fim"; ok=0; }; done; done
  if [ "$ok" = 1 ]; then echo "OK: F-10 estado final: portao K1 OK, 0 timers tcc-heal, vazios os caches nao usados por carga (${vazios:-nenhum})"; else G=1; fi
  grep '^size ' "$f" 2>/dev/null | sed 's/^/RESULTADO: tamanho final: /'

  for linha in $EXECS; do
    IFS=: read -r n i carga modo <<<"$linha"; NE=$((NE + 1)); NF=0; NOK=0
    E="$D/exec-$n-i$i"
    ok_() { echo "OK: [n=$n] $*"; NOK=$((NOK + 1)); }
    fa()  { echo "FALHA: [n=$n] $*"; NF=$((NF + 1)); }
    res() { echo "RESULTADO: [n=$n] $*"; }
    lei() { echo "LEITURA HUMANA: [n=$n] $*"; }
    echo "-- execucao n=$n i=$i carga=$carga${modo:+ replicacao=$modo}"
    if [ ! -d "$E" ]; then fa "diretorio $E ausente (execucao nao rodou)"; echo "EXECUCAO n=$n: INVALIDA"; continue; fi
    # F-1: K6 do injetor (DT-017, regex de analysis/rebaseline.py k6)
    x=$(ev "$E" injetor_exit); q=""
    for p in '\bmodo=silencioso\b' '\beth0 down\b' '\bkill isn2\b' '\bExitCode=137\b' '\bstart isn2\b'; do
      grep -qE "$p" "$E/injetor.log" 2>/dev/null || q="$q $p"; done
    if [ -z "$q" ] && [ "$x" = 0 ]; then ok_ "F-1 injetor.log com modo=silencioso, eth0 down, kill isn2, ExitCode=137, start isn2; injetor exit 0"
    else fa "F-1 injetor exit=${x:-?}; ausentes no injetor.log:${q:- nenhum}"; fi
    # F-2: todo died de isn2 com 137 (podman events) e ExitCode lido antes do start = 137
    nd=$(grep -c '"Status":"died"' "$E/events-isn2.json" 2>/dev/null); nd=${nd:-0}
    n137=$(grep '"Status":"died"' "$E/events-isn2.json" 2>/dev/null | grep -c '"ContainerExitCode":137[,}]')
    x=$(sed -n 's/.*inject-crash\.sh: isn2 ExitCode=\([0-9]*\).*/\1/p' "$E/injetor.log" 2>/dev/null | head -1)
    if [ "$nd" -ge 1 ] && [ "$nd" = "$n137" ] && [ "$x" = 137 ]; then ok_ "F-2 $nd died de isn2, todos com 137; .State.ExitCode antes do start = 137"
    else fa "F-2 died de isn2: $nd evento(s), $n137 com 137 (todo died tem de ser 137, ao menos 1); ExitCode antes do start=${x:-?}"; fi
    # F-3: disparo entre INJ_OFFSET-1 e INJ_OFFSET+2 s da ancora (so com carga)
    if [ "$carga" != sem ]; then
      a=$(ev "$E" anc_ms); b=$(ev "$E" disp_ms)
      if [ -n "$a" ] && [ -n "$b" ]; then x=$(s_de_ms $((b - a)))
        if awk -v o="$x" -v m="$INJ_OFF" 'BEGIN { exit !(o >= m - 1 && o <= m + 2) }'; then ok_ "F-3 disparo a +$x s da ancora (aceito $((INJ_OFF - 1)) a $((INJ_OFF + 2)) s)"
        else fa "F-3 disparo a +$x s da ancora (fora de $((INJ_OFF - 1)) a $((INJ_OFF + 2)) s)"; fi
      else fa "F-3 sem ancora ou sem disparo (anc_ms=${a:-?} disp_ms=${b:-?})"; fi
    fi
    # F-4: Δdet >= DMIN em isn1 e isn3
    v1=$(ddet_ms "$E" isn1); v3=$(ddet_ms "$E" isn3)
    if [ -n "$v1" ] && [ -n "$v3" ] && [ "$v1" -ge $((DMIN * 1000)) ] && [ "$v3" -ge $((DMIN * 1000)) ]; then
      ok_ "F-4 Δdet isn1=$(s_de_ms "$v1") s, isn3=$(s_de_ms "$v3") s (>= $DMIN s)"
    else fa "F-4 Δdet isn1=$([ -n "$v1" ] && s_de_ms "$v1" || echo '?') s, isn3=$([ -n "$v3" ] && s_de_ms "$v3" || echo '?') s (minimo $DMIN s em isn1 e isn3; ? = sem ISPN000094 sem isn2 depois do isolamento)"; fi
    # F-5 e F-6: vidas de isn2
    if inj_ms "$E" "$RE_START" >/dev/null && [ -s "$E/logs-isn2.txt" ]; then
      a=$(vida_isn2 "$E" antiga | grep -cE 'ISPN000390|ISPN080002')
      [ "$a" = 0 ] && ok_ "F-5 nenhuma ISPN000390/ISPN080002 na vida antiga de isn2" || fa "F-5 $a linha(s) ISPN000390/ISPN080002 na vida antiga de isn2 (saida ordenada)"
      a=$(vida_isn2 "$E" nova | grep -c 'ISPN000389'); b=$(vida_isn2 "$E" nova | grep -c 'ISPN080001'); c=$(vida_isn2 "$E" nova | grep -c 'ISPN000693')
      if [ "$a" -ge 1 ] && [ "$b" -ge 1 ] && [ "$c" = 0 ]; then ok_ "F-6 vida nova de isn2 com ISPN000389 e ISPN080001, sem ISPN000693"
      else fa "F-6 vida nova de isn2: ISPN000389=$a ISPN080001=$b ISPN000693=$c (esperado >=1, >=1, 0)"; fi
    else fa "F-5 sem linha 'podman start isn2' no injetor.log ou sem logs-isn2.txt"; fa "F-6 idem (vidas de isn2 ilegiveis)"; fi
    # F-7: volta a 3 HEALTHY sem REBALANCING em < VMAX s do start
    x=$(ev "$E" volta_ok); a=$(ev "$E" volta_s)
    if [ "$x" = 1 ] && [ -n "$a" ] && awk -v v="$a" -v m="$VMAX" 'BEGIN { exit !(v < m) }'; then ok_ "F-7 3 nos HEALTHY sem REBALANCING a $a s do start (< $VMAX s)"
    else fa "F-7 cluster nao voltou a 3 HEALTHY sem REBALANCING em < $VMAX s do start (volta_ok=${x:-?} volta_s=${a:-?})"; fi
    # F-8: eth0 UP no netns novo de isn2
    if grep -qE '^eth0(@[^ ]*)? +UP( |$)' "$E/ip-br-link-isn2.txt" 2>/dev/null; then ok_ "F-8 eth0 UP no netns novo de isn2 (pid $(ev "$E" pid_depois))"
    else fa "F-8 eth0 nao esta UP no netns novo de isn2 (ip-br-link-isn2.txt)"; fi
    # F-9 (por execucao): intervalo do saude-log <= 2x o nominal (mesma regra do s5.sh)
    f="$E/saude.csv"
    q=$(awk -F, 'NR > 1 { if ($2 in u) { d = $1 - u[$2]; if (d > m) m = d } u[$2] = $1 } END { printf "%.3f", m / 1e9 }' "$f" 2>/dev/null)
    if [ -s "$f" ] && awk -v g="${q:-999}" -v s="$SS" 'BEGIN { exit !(g <= 2 * s) }'; then ok_ "F-9 intervalo maximo do saude-log: $q s <= 2 x $SS s"
    else fa "F-9 intervalo maximo do saude-log: ${q:-?} s > 2 x $SS s (pausa do host/VM ou coleta ausente)"; fi
    # RESULTADO (o que o ensaio veio medir; nao invalida)
    for x in isn1 isn3; do a=$( [ "$x" = isn1 ] && echo "${v1:-}" || echo "${v3:-}" )
      if [ -n "$a" ]; then res "Δdet $x = $(s_de_ms "$a") s ($(awk -v v="$a" -v f="$FAIXA" 'BEGIN { split(f, r, " "); print (v >= r[1] * 1000 && v <= r[2] * 1000) ? "dentro" : "fora" }') de [${FAIXA// /, }] s, faixa explicada pelo FD_ALL3)"
      else res "Δdet $x sem leitura"; fi; done
    res "volta: $(ev "$E" volta_s) s do start (amostras em volta.log)"
    a=$(ev "$E" ip_antes); b=$(ev "$E" ip_depois)
    res "IP de isn2: antes=${a:-?} depois=${b:-?} ($([ -n "$a" ] && [ "$a" = "$b" ] && echo igual || echo diferente))"
    if [ "$carga" != sem ]; then
      x=$(ev "$E" vexit); a=$(sed -n 's/.*"veredito" *: *"\([A-Z_]*\)".*/\1/p' "$E/sanidade.json" 2>/dev/null | head -1)
      res "VerificadorSanidade: exit=${x:-?} veredito=${a:-?}; K3 do PR $([ "$x" = 0 ] && [ "$a" = OK ] && echo ACEITARIA || echo RECUSARIA) (E-5: so RESULTADO)"
      res "workload exit=$(ev "$E" wexit); C1..C3 (rebaseline.criterios_mistura, falha=F1): $(tr -d '\n' < "$E/c1-c3.json" 2>/dev/null | cut -c1-400)"
      grep -E '^(linhas|erro|janela)' "$E/csv-resumo.txt" 2>/dev/null | sed "s/^/RESULTADO: [n=$n] csv: /"
      grep -E '^desfecho' "$E/csv-resumo.txt" 2>/dev/null | sed "s/^/RESULTADO: [n=$n] csv: /"
      res "sha256 do rep-001.csv antes do gzip: $(cut -d' ' -f1 "$E/rep-001.csv.sha256" 2>/dev/null)"
    fi
    iso=$(inj_ms "$E" "$RE_ISO"); kill=$(inj_ms "$E" "$RE_KILL"); st=$(inj_ms "$E" "$RE_START"); x=$(inj_ms "$E" "$RE_OK")
    t1=$( [ -n "$iso" ] && vista_ms "$E" isn1 sem "$iso"); t3=$( [ -n "$st" ] && vista_ms "$E" isn1 com "$st")
    lei "ordem nos carimbos: isolamento=$( [ -n "$iso" ] && iso_de_ms "$iso") kill=$( [ -n "$kill" ] && iso_de_ms "$kill") vista-sem-isn2(isn1)=$( [ -n "$t1" ] && iso_de_ms "$t1") start=$( [ -n "$st" ] && iso_de_ms "$st") vista-com-isn2(isn1)=$( [ -n "$t3" ] && iso_de_ms "$t3")"
    lei "duracao do podman start (linha 'podman start' ate 'OK: F1' do injetor): $( [ -n "$st" ] && [ -n "$x" ] && s_de_ms $((x - st)) || echo '?') s (no WSL deu 51 s)"
    if [ "$NF" = 0 ] && [ "$G" = 0 ]; then echo "EXECUCAO n=$n: VALIDA ($NOK criterios OK)"; NV=$((NV + 1))
    else echo "EXECUCAO n=$n: INVALIDA ($NF falha(s) da execucao$([ "$G" = 1 ] && echo '; criterio geral F-9/F-10 falhou'))"; fi
  done
  echo "ENSAIO: $NV de $NE VALIDAS"
  [ "$NV" = "$NE" ] && [ "$NE" -gt 0 ]
}
if [ "$MODO" = avaliar ]; then avaliar "$AVAL_DIR"; R=$?; [ "$R" = 0 ] || exit 6; exit 0; fi

# ---- constantes e montar_cmd do run-rebaseline.sh do --rb-dir (hash conferido no passo 0; nao redigitadas)
carregar_rb() { # carregar_rb <rb-dir>: le e avalia as linhas de constantes e o corpo de montar_cmd; 1 se nao achar
  local f="$1/scripts/run-rebaseline.sh" v l k mc sv re='^readonly( [A-Z][A-Z0-9_]*=[A-Za-z0-9_.]+)+ *(#.*)?$'
  [ -f "$f" ] || { echo "sem $f"; return 1; }
  for v in DURACAO S0 INJ_OFFSET F1_DUR K1_JANELA_S F1_VOLTA_TIMEOUT_S ANCORA_TIMEOUT_S CLASSE_VERIFICADOR; do
    k=$(grep -cE "^readonly $v=" "$f"); [ "$k" = 1 ] || { echo "linha 'readonly $v=' aparece $k vez(es) em $f"; return 1; }
    l=$(grep -E "^readonly $v=" "$f"); [[ "$l" =~ $re ]] || { echo "linha fora do formato esperado: $l"; return 1; }
    eval "${l#readonly }" || { echo "linha nao avaliada: $l"; return 1; }   # sem readonly: idempotente
  done
  for v in DURACAO WARMUP_MIN THREADS OPS REP MIX RESET_STATE S0 PASSO_SEMENTE INJ_OFFSET F1_DUR K1_JANELA_S K1_PERIODO_S \
           K1_TIMEOUT_S F1_VOLTA_TIMEOUT_S F1_VOLTA_PERIODO_S ANCORA_TIMEOUT_S ANCORA_POLL_S CLASSE_VERIFICADOR; do
    [ -n "${!v:-}" ] || { echo "constante $v ausente nas linhas lidas de $f"; return 1; }; done
  k=$(grep -cE '^CRIADO_APOS=""; SERVERS="[0-9.:,]+"; USERNAME="[A-Za-z0-9_]+"$' "$f")
  [ "$k" = 1 ] || { echo "linha dos padroes SERVERS/USERNAME aparece $k vez(es) em $f"; return 1; }
  sv=$(sed -n 's/^CRIADO_APOS=""; SERVERS="\([0-9.:,]*\)"; USERNAME="\([A-Za-z0-9_]*\)"$/\1 \2/p' "$f")
  SERVERS=${sv% *}; USERNAME=${sv#* }
  k=$(grep -c '^montar_cmd() {' "$f"); [ "$k" = 1 ] || { echo "'montar_cmd() {' aparece $k vez(es) em $f"; return 1; }
  mc=$(awk '/^montar_cmd\(\) \{/ { f = 1 } f { print } f && /^\}$/ { exit }' "$f")
  [ "$(printf '%s\n' "$mc" | tail -1)" = "}" ] || { echo "corpo de montar_cmd sem '}' na coluna 0"; return 1; }
  printf '%s' "$mc" | grep -qF -- '--password "${PASSWORD}"' && printf '%s' "$mc" | grep -qF 'CMD_MASC[$((k + 1))]="***"' \
    || { echo "montar_cmd sem --password/CMD_MASC (a senha nao seria mascarada)"; return 1; }
  eval "$mc" || { echo "montar_cmd nao avaliado"; return 1; }
  RB_MONTAR_CMD="$mc"; return 0
}
JAVA_CMD=(java)

if [ "$MODO" = dry ]; then
  [ -n "$RB" ] && [ -d "$RB" ] || erro_uso "--dry-run exige --rb-dir <dir>"
  m=$(carregar_rb "$RB" 2>&1) || { echo "DRY-RUN: $m" >&2; exit 1; }; carregar_rb "$RB" >/dev/null
  JAR="$RB/workload/target/session-workload-0.1.0-SNAPSHOT.jar"; PASSWORD='***'
  echo "DRY-RUN ensaio do F1 silencioso: rb-dir=$RB"
  echo "constantes lidas de $RB/scripts/run-rebaseline.sh: duracao=$DURACAO warmup_min=$WARMUP_MIN threads=$THREADS ops=$OPS rep=$REP mix=$MIX reset_state=$RESET_STATE s0=$S0 passo_semente=$PASSO_SEMENTE injecao=+$INJ_OFFSET s F1=$F1_DUR s K1=$K1_JANELA_S/$K1_PERIODO_S/$K1_TIMEOUT_S s volta<=$F1_VOLTA_TIMEOUT_S s servers=$SERVERS username=$USERNAME verificador=$CLASSE_VERIFICADOR"
  while read -r n i carga modo; do
    if [ "$carga" = sem ]; then echo "PLANO n=$n i=$i sem carga: GO 'GO F1 n=$n i=$i carga=sem'; portao K1; inject-crash.sh --node isn2 --duration $F1_DUR"
    else montar_cmd "$carga" "$modo" "$((S0 + PASSO_SEMENTE * i))" "<vm-UTC>/exec-$n-i$i"
      echo "PLANO n=$n i=$i $carga $modo semente=$((S0 + PASSO_SEMENTE * i)): GO 'GO F1 n=$n i=$i carga=$carga-$modo'; portao K1; workload; inject-crash.sh a +$INJ_OFFSET s da ancora; $CLASSE_VERIFICADOR"
      echo "      ${CMD_MASC[*]}"; fi
  done <<<"$EXECUCOES_TAB"
  PODMAN='sudo podman' bash "$RB/scripts/inject-crash.sh" --dry-run --node isn2 --duration "$F1_DUR"; R=$?
  echo "DRY-RUN: nada executado (injetor --dry-run rc=$R)"; exit "$R"
fi

# ---- execucao: validacao dos argumentos (nada lido alem deles)
[ -n "$RB" ] || erro_uso "--rb-dir e obrigatorio"
[[ "$BOOT_ARG" =~ $UUID_RE ]] || erro_uso "--boot-esperado ausente ou nao e UUID ('$BOOT_ARG')"
[ -n "$ANT" ] || erro_uso "--bloco-anterior e obrigatorio"
case "$SO" in ""|1|2|3|4) ;; *) erro_uso "--so deve ser 1, 2, 3 ou 4";; esac
SEL=$(awk -v s="$SO" 's == "" || $1 == s' <<<"$EXECUCOES_TAB")

# ---- 0. TCC_DIR, diretorio de saida e console
if [ -z "${TCC_DIR:-}" ]; then export TCC_DIR="$HOME/tcc-gate"; TCC_ORIGEM="exportado pelo ensaio-f1.sh"
elif [ "$TCC_DIR" = "$HOME/tcc-gate" ]; then export TCC_DIR; TCC_ORIGEM="ja exportado"
else echo "ENSAIO ABORTADO: TCC_DIR=$TCC_DIR (precisa ser $HOME/tcc-gate)" >&2; exit 1; fi
cd "$TCC_DIR" || { echo "ENSAIO ABORTADO: sem $TCC_DIR" >&2; exit 1; }
[ "$(dirname "$AQUI")" = "$TCC_DIR/runs" ] || { echo "ENSAIO ABORTADO: ensaio-f1.sh fora de $TCC_DIR/runs ($AQUI)" >&2; exit 1; }
OUT="$AQUI/vm-$(date -u +%Y%m%dT%H%M%SZ)"
[ ! -e "$OUT" ] || { echo "ENSAIO ABORTADO: $OUT ja existe (diretorio de saida tem de ser novo)" >&2; exit 1; }
mkdir -p "$OUT" || exit 1
exec > >(tee -a "$OUT/ensaio-console.log") 2>&1
TEE_PID=$!
trap 'exec 1>&- 2>&-; wait "$TEE_PID" 2>/dev/null' EXIT
WPID=; IPID=; SPID=; EVPID=; E=
passo_rc() { echo "$1 rc=$2 fim=$(date -Is)" >> "$OUT/rc.txt"; echo "$1 rc=$2 fim=$(date -Is)"; }
limpar_filhos() { # workload, saude e events (o injetor e tratado a parte: o trap dele devolve isn2)
  if [ -n "$WPID" ]; then kill -TERM "$WPID" 2>/dev/null; wait "$WPID" 2>/dev/null; WPID=; fi
  if [ -n "$SPID" ]; then [ -z "$E" ] || touch "$E/.parar-saude"; kill -TERM "$SPID" 2>/dev/null; wait "$SPID" 2>/dev/null; SPID=; fi
  if [ -n "$EVPID" ]; then kill -TERM "$EVPID" 2>/dev/null; wait "$EVPID" 2>/dev/null; EVPID=; fi
  [ -z "$E" ] || rm -f "$E/.parar-saude"; }
abortar() { local rc="$1"; shift; limpar_filhos; echo "ENSAIO ABORTADO (rc=$rc): $*" >&2; echo "abortado rc=$rc: $* $(date -Is)" >> "$OUT/rc.txt"; exit "$rc"; }
interromper() {
  trap '' INT
  echo "ENSAIO interrompido (Ctrl-C) $(date -Is)" >&2; echo "interrompido $(date -Is)" >> "$OUT/rc.txt"
  if [ -n "$IPID" ] && kill -0 "$IPID" 2>/dev/null; then
    echo "injetor em curso (pid $IPID): TERM; o trap do inject-crash.sh devolve isn2 (espera ate ${INJ_ESPERA_TRAP_S} s)" >&2
    kill -TERM "$IPID" 2>/dev/null
    for _ in $(seq 1 "$INJ_ESPERA_TRAP_S"); do kill -0 "$IPID" 2>/dev/null || break; sleep 1; done
    wait "$IPID" 2>/dev/null; echo "injetor saiu com $? depois do TERM" >> "$OUT/rc.txt"; IPID=
  fi
  limpar_filhos
  st=$(sudo podman inspect -f '{{.State.Status}}' isn2 2>/dev/null)
  echo "isn2 estado depois do Ctrl-C: ${st:-?}" | tee -a "$OUT/rc.txt" >&2; RC=${PIPESTATUS[0]}
  [ "$st" = running ] || echo "isn2 NAO esta running: o ensaio nao conserta. Decisao do Gestor: sudo podman start isn2" >&2
  exit 130
}
trap interromper INT
echo "ENSAIO do F1 silencioso inicio $(date -Is) saida=$OUT execucoes=$(cut -d' ' -f1 <<<"$SEL" | paste -sd,)"
echo "TCC_DIR=$TCC_DIR ($TCC_ORIGEM)"

# ---- 0. pre-condicoes (so leitura); qualquer falha aborta antes de qualquer injecao
{
  E=0; nok() { echo "NAO OK: $*"; E=1; }
  PAR="$OUT/parametros.txt"
  echo "== pre-condicoes $(date -Is)"
  echo "hostname=$(hostname) ip=$(hostname -I 2>/dev/null | awk '{print $1}') usuario=$(id -un)"
  [ -n "${TMUX:-}" ] && echo "tmux: $TMUX" || nok "fora do tmux (TMUX vazio)"
  [ -t 0 ] || [ "$TESTE" = 1 ] && echo "terminal: ok (o GO de cada execucao e interativo)" || nok "sem terminal (o GO e interativo)"
  if flock -n "$LOCK" true; then nok "lock $LOCK LIVRE: rode dentro de 'flock $LOCK tmux new -s ensaio'"; else echo "lock $LOCK: OCUPADO (este flock)"; fi
  # o inject-crash.sh nao tem lock proprio (conferido no arquivo de 20805b0): a guarda equivalente e nenhum em execucao
  p=$(pgrep -f 'inject-crash\.sh' 2>/dev/null | paste -sd' '); [ -z "$p" ] && echo "inject-crash.sh: nenhum em execucao (o injetor nao usa lock proprio)" \
    || nok "inject-crash.sh ja em execucao (pid $p)"
  SUDO=0; sudo -n true && { echo "sudo -n: ok"; SUDO=1; } || nok "sudo -n true falhou"
  # seção 2: boot_id e kernel sem valor fixo; bloco anterior = S7
  BA=$(sed -n 's/^boot_id fim: \([^ ]*\).*/\1/p' "$ANT/07-boot-fim.txt" 2>/dev/null | head -1)
  FIM_ANT=$(sed -n 's/^boot_id fim: [^ ]* \(.*\)$/\1/p' "$ANT/07-boot-fim.txt" 2>/dev/null | head -1)
  KA=$(sed -n 's/^uname -r: //p' "$ANT/00-pre.log" 2>/dev/null | head -1)
  FIM_ANT_S=$(date -u -d "$FIM_ANT" +%s 2>/dev/null)
  if [ -z "$BA" ] || [ -z "$KA" ] || [ -z "$FIM_ANT_S" ]; then nok "bloco anterior sem brutos ($ANT: 07-boot-fim.txt e 00-pre.log com boot_id fim, instante e uname -r)"
  else echo "bloco anterior: $ANT boot_id fim=$BA fim=$FIM_ANT uname -r=$KA"; fi
  B=$(cat "$BOOT_ID_FILE" 2>/dev/null); echo "boot_id: $B"
  [ "$B" = "$BOOT_ARG" ] && [ "$BOOT_ARG" = "$BA" ] || nok "boot_id atual=$B --boot-esperado=$BOOT_ARG bloco anterior=${BA:-?}: reboot da VM entre blocos ou referencia errada: parar e chamar o Senior"
  K=$(uname -r); echo "uname -r: $K"; [ "$K" = "$KA" ] || nok "kernel $K diferente do bloco anterior (${KA:-?})"
  # hashes do --rb-dir contra a adenda da DT-009 parte 2: arquivo a arquivo e a lista
  echo "-- sha256 do --rb-dir ($RB) contra a adenda da DT-009 parte 2"
  if [ -d "$RB" ] && ( cd "$RB" && printf '%s\n' "$HASHES_RB" | sha256sum -c - ); then echo "hashes do --rb-dir: iguais a adenda"
  else nok "hash do --rb-dir diferente da adenda (ou --rb-dir ausente)"; fi
  L=$(cd "$RB" 2>/dev/null && printf '%s\n' "$HASHES_RB" | sed 's/^[0-9a-f]* \*//' | xargs sha256sum -b 2>/dev/null | sha256sum | cut -d' ' -f1)
  [ "$L" = "$LISTA_SHA" ] && echo "lista: $L (= adenda)" || nok "lista dos 7 arquivos do --rb-dir = ${L:-?} (esperado $LISTA_SHA)"
  echo "ensaio-f1.sh: $(sha256sum "$SELF" | cut -d' ' -f1)"
  echo "cluster/infinispan-cluster.xml do --rb-dir: $(sha256sum "$RB/cluster/infinispan-cluster.xml" 2>/dev/null | cut -d' ' -f1)"
  if M=$(carregar_rb "$RB" 2>&1); then carregar_rb "$RB" >/dev/null; echo "constantes e montar_cmd lidos de $RB/scripts/run-rebaseline.sh"
  else nok "run-rebaseline.sh do --rb-dir: $M (parar e perguntar; nao redigitar)"; fi
  V=$(env | grep -E '^(LOCK_DIR|PODMAN|JAVA|INJ|LOG|DMS|UNIT|REP|COLETA|G_TESTE|G5B_[A-Z_]*|G1_[A-Z_]*|SONDA_[A-Z_]*|L0C_[A-Z_]*|RB_[A-Z_]*|TCC_[A-Z_]*|S[0-9]_TESTE|ENSAIO_[A-Z_]*)=' \
      | cut -d= -f1 | grep -vxE 'RB_PASSWORD|TCC_DIR' | { if [ "$TESTE" = 1 ]; then grep -v '^ENSAIO_'; else cat; fi; } | paste -sd' ')
  [ -z "$V" ] && echo "ambiente: sem PODMAN/LOCK_DIR/RB_*/G_TESTE/... herdados (RB_PASSWORD e TCC_DIR permitidos)" || nok "variaveis herdadas que mudariam o injetor/workload: $V"
  [ -n "${RB_PASSWORD:-}" ] && echo "RB_PASSWORD: presente (nao gravado)" || nok "RB_PASSWORD ausente (ler antes com: read -rs RB_PASSWORD; export RB_PASSWORD)"
  T=$(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal); echo "timers tcc-heal: $T"; [ "$T" = 0 ] || nok "$T timers tcc-heal armados"
  command -v java >/dev/null && echo "java: $(java -version 2>&1 | head -1)" || nok "java ausente"
  VMIP=$(hostname -I 2>/dev/null | awk '{print $1}'); [ -n "$VMIP" ] || nok "IP da VM vazio (hostname -I)"
  {
    echo "BOOT_ESPERADO=$BOOT_ARG"; echo "BLOCO_ANTERIOR=$ANT"; echo "FIM_BLOCO_ANTERIOR=$FIM_ANT"; echo "RB_DIR=$RB"
    echo "SAUDE_S=$SAUDE_S"; echo "DDET_MIN_S=$DDET_MIN_S"; echo "DDET_FAIXA=$DDET_FAIXA"; echo "VMIP=$VMIP"
    echo "EXECUCOES=$(awk '{ printf "%s%s:%s:%s:%s", (NR > 1 ? " " : ""), $1, $2, $3, ($4 == "-" ? "" : $4) }' <<<"$SEL")"
  } > "$PAR"
  [ -z "${INJ_OFFSET:-}" ] || { echo "INJ_OFFSET=$INJ_OFFSET"; echo "F1_DUR=$F1_DUR"; echo "F1_VOLTA_TIMEOUT_S=$F1_VOLTA_TIMEOUT_S"
    echo "K1=$K1_JANELA_S/$K1_PERIODO_S/$K1_TIMEOUT_S"; echo "DURACAO=$DURACAO"; echo "THREADS=$THREADS"; } >> "$PAR"
  if [ "$SUDO" = 1 ]; then
    echo "-- conteineres e cluster atual"
    sudo podman ps -a --format '{{.Names}} {{.ID}} {{.Status}} criado={{.CreatedAt}}' 2>&1
    for n in $NOS; do
      st=$(sudo podman inspect -f '{{.State.Status}}' "$n" 2>/dev/null)
      read -r cr sa <<<"$(sudo podman inspect -f '{{.Created.Unix}} {{.State.StartedAt.Unix}}' "$n" 2>/dev/null)"
      ip=$(sudo podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$n" 2>/dev/null | awk '{print $1}')
      echo "$n status=$st ip=$ip id=$(sudo podman inspect -f '{{.Id}}' "$n" 2>&1 | cut -c1-12) Created=${cr:-?} StartedAt=${sa:-?}"
      [ "$st" = running ] || nok "$n nao esta running ('$st')"
      if [[ "${cr:-}" =~ ^[0-9]+$ && "${sa:-}" =~ ^[0-9]+$ ]]; then
        [ -z "$FIM_ANT_S" ] || [ "$cr" -gt "$FIM_ANT_S" ] || nok "$n criado em $cr, antes do fim do bloco anterior ($FIM_ANT_S): o cluster nao foi recriado depois do S7 (E-2)"
        # E-2 (pendente do Gestor): cluster recriado com o XML do rebaseline so para o ensaio; nenhum reinicio desde a recriacao.
        # Com --so 2..4, isn2 ja foi reiniciado pelo F1 das execucoes anteriores do ensaio: so registrado (D-3, pendente).
        if [ "$n" = isn2 ] && [ -n "$SO" ] && [ "$SO" != 1 ]; then echo "isn2 StartedAt-Created=$((sa - cr)) s (--so $SO: reinicio pelo F1 anterior do ensaio, so registrado)"
        elif [ $((sa - cr)) -lt 0 ] || [ $((sa - cr)) -gt "$FOLGA_RECRIACAO_S" ]; then nok "$n StartedAt-Created=$((sa - cr)) s (> $FOLGA_RECRIACAO_S s: o conteiner reiniciou depois da recriacao)"; fi
      else nok "Created/StartedAt de $n ilegiveis ('${cr:-}' '${sa:-}')"; fi
      echo "STARTED_$n=${sa:-}" >> "$PAR"; echo "IP_$n=$ip" >> "$PAR"
      x=$(sudo podman inspect -f "{{range .Mounts}}{{if eq .Destination \"$XML_DESTINO\"}}{{.Source}}{{end}}{{end}}" "$n" 2>/dev/null)
      h=$( [ -n "$x" ] && sudo sha256sum "$x" 2>/dev/null | cut -d' ' -f1)
      echo "$n XML em uso: ${x:-?} ${h:-?}"; [ "$h" = "$XML_SHA" ] || nok "XML em uso por $n (${h:-?}) diferente do de 20805b0 ($XML_SHA)"
    done
    for c in $CACHES; do for p in $PORTAS; do
      r=$(curl -s -m 5 --digest -u "admin:${RB_PASSWORD:-}" -o "$OUT/.corpo" -w '%{http_code}' "http://127.0.0.1:$p/rest/v2/caches/$c?action=size" 2>/dev/null)
      b=$(tr -d ' \n' < "$OUT/.corpo" 2>/dev/null); rm -f "$OUT/.corpo"
      echo "size $c porta $p: http=${r:-?} ${b:-?}"
      [ "$r" = 200 ] || nok "cache $c inexistente ou ilegivel na porta $p (http ${r:-?})"; [ "$r" != 200 ] || [ "$b" = 0 ] || nok "cache $c nao vazio na porta $p ($b)"
    done; done
    P2=$(sudo podman inspect -f '{{.State.Pid}}' isn2 2>/dev/null); echo "PID_isn2=$P2"
    if ! [[ "$P2" =~ ^[0-9]+$ ]] || [ "$P2" -le 1 ]; then nok "PID de isn2 invalido ('$P2')"
    else
      ns1=$(sudo readlink /proc/1/ns/net 2>/dev/null); ns2=$(sudo readlink "/proc/$P2/ns/net" 2>/dev/null)
      if [ -n "$ns1" ] && [ -n "$ns2" ] && [ "$ns1" != "$ns2" ]; then echo "isn2 netns: diferente do host ($ns2 != $ns1)"
      else nok "netns de isn2 igual ao do host ou ilegivel ('$ns2' / '$ns1')"; fi
      l=$(sudo nsenter -t "$P2" -n ip -br link 2>&1); echo "isn2 ip -br link:"; printf '%s\n' "$l"
      printf '%s\n' "$l" | grep -qE '^eth0(@[^ ]*)? +UP( |$)' || nok "eth0 de isn2 nao esta UP"
    fi
  fi
  exit "$E"
} 2>&1 | tee "$OUT/00-pre.log"; RC=${PIPESTATUS[0]}; passo_rc pre "$RC"
[ "$RC" = 0 ] || abortar 1 "pre-condicoes (ver $OUT/00-pre.log)"
par() { sed -n "s/^$1=//p" "$OUT/parametros.txt" | head -1; }
carregar_rb "$RB" >/dev/null || abortar 1 "run-rebaseline.sh do --rb-dir ilegivel"
VMIP=$(par VMIP); JAR="$RB/workload/target/session-workload-0.1.0-SNAPSHOT.jar"; INJ="$RB/scripts/inject-crash.sh"
declare -A STARTED; for n in $NOS; do STARTED[$n]=$(par "STARTED_$n"); done

# ---- coletas e portoes (so leitura). saude_loop e tcp_s: verbatim do s5.sh
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
ip_de() { sudo podman inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$1" 2>/dev/null | awk '{print $1}'; }
pid_de() { local p; p=$(sudo podman inspect -f '{{.State.Pid}}' "$1" 2>/dev/null); [[ "$p" =~ ^[0-9]+$ ]] && [ "$p" -gt 1 ] && echo "$p"; }
amostra_k1() { # amostra_k1 [netem]: AM_OK=1 se as 3 portas dao cluster HEALTHY, number_of_nodes=3 e nada REBALANCING
  local ok=1 txt="" p body st nn rb n pid q
  for p in $PORTAS; do
    body=$(curl -s -m 5 --digest -u "$USERNAME:$RB_PASSWORD" "http://127.0.0.1:$p/rest/v2/cache-managers/default/health" 2>/dev/null | tr -d ' \n')
    st=$(printf '%s' "$body" | grep -o '"cluster_health":{[^}]*' | grep -o '"health_status":"[A-Z_]*"' | cut -d'"' -f4)
    nn=$(printf '%s' "$body" | grep -o '"number_of_nodes":[0-9]*' | head -1 | cut -d: -f2)
    rb=0; case "$body" in *REBALANCING*) rb=1;; esac
    { [ "$st" = HEALTHY ] && [ "$nn" = 3 ] && [ "$rb" = 0 ]; } || ok=0
    txt+=" $p=${st:--}/${nn:--}$([ "$rb" = 1 ] && echo /REBALANCING)"
  done
  if [ "${1:-}" = netem ]; then
    for n in $NOS; do
      if pid=$(pid_de "$n"); then q=$(sudo nsenter -t "$pid" -n tc qdisc show dev eth0 2>&1)
        case "$q" in *netem*) ok=0; txt+=" $n=NETEM";; esac
      else ok=0; txt+=" $n=sem-pid"; fi
    done
  fi
  AM_OK=$ok; AM_TXT="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ) ok=$ok$txt"; }
portao_k1() { # portao_k1 <arquivo>: K1 do PR §6 (3 nos HEALTHY, sem REBALANCING e sem netem por K1_JANELA_S s; ate K1_TIMEOUT_S)
  local f="$1" t0 bom= agora
  t0=$(agora_ms); echo "portao K1 (janela ${K1_JANELA_S} s, periodo ${K1_PERIODO_S} s, prazo ${K1_TIMEOUT_S} s) $(date -Is)" > "$f"
  while :; do
    amostra_k1 netem; echo "$AM_TXT" >> "$f"; agora=$(agora_ms)
    if [ "$AM_OK" = 1 ]; then [ -n "$bom" ] || bom=$agora
      if [ $((agora - bom)) -ge $((K1_JANELA_S * 1000)) ]; then echo "PORTAO K1: OK ($(s_de_ms $((agora - bom))) s bons)" >> "$f"; tail -1 "$f"; return 0; fi
    else bom=; fi
    if [ $((agora - t0)) -ge $((K1_TIMEOUT_S * 1000)) ]; then echo "PORTAO K1: FALHA (sem ${K1_JANELA_S} s bons em ${K1_TIMEOUT_S} s)" >> "$f"; tail -1 "$f"; return 1; fi
    sleep "$K1_PERIODO_S"
  done; }
conferir_started() { # conferir_started <arquivo>: nenhum no reiniciou fora do injetor do ensaio
  local f="$1" n s e=0
  for n in $NOS; do s=$(sudo podman inspect -f '{{.State.StartedAt.Unix}}' "$n" 2>/dev/null)
    echo "$n StartedAt=$s esperado=${STARTED[$n]}" >> "$f"; [ "$s" = "${STARTED[$n]}" ] || e=1; done
  return "$e"; }
dormir_ate() { local r; r=$(( $1 - $(agora_ms) )); [ "$r" -le 0 ] || { sleep "$(s_de_ms "$r")" & wait $!; }; }
coletar_logs() { local n; for n in $NOS; do sudo podman logs -t --since "$GO_S" "$n" > "$E/logs-$n.txt" 2>&1; done; }
pedir_go() { # pedir_go <rotulo>: 0 so se o Gestor digitar exatamente "GO <rotulo>" em ate ESPERA_GO_S s (E-1)
  local r=""
  printf '>>> %s: digite exatamente "GO %s" em ate %s s (qualquer outra coisa aborta sem injetar): ' "$1" "$1" "$ESPERA_GO_S"
  IFS= read -r -t "$ESPERA_GO_S" r || r=""
  echo
  if [ "$r" = "GO $1" ]; then echo "go $(date -u +%Y-%m-%dT%H:%M:%S.%3NZ) vm=$VMIP bloco=ensaio-f1 $1" >> "$AQUI/go.log"; tail -1 "$AQUI/go.log" > "$E/go.txt"; return 0; fi
  echo "GO recusado para '$1' $(date -Is)" > "$E/go.txt"; return 1; }
parar() { # parar <motivo>: parada automatica (exit 4). Grava o estado e NAO conserta nada
  local st pid
  limpar_filhos
  echo "PARADA: $* $(date -Is)" >> "$OUT/rc.txt"
  echo "PARADA: $*" >&2
  {
    echo "== PARADA $(date -Is): $*"
    sudo podman ps -a --format '{{.Names}} {{.ID}} {{.Status}}' 2>&1
    st=$(sudo podman inspect -f '{{.State.Status}}' isn2 2>/dev/null); echo "isn2 status=$st"
    if pid=$(pid_de isn2); then echo "isn2 pid=$pid ip -br link:"; sudo nsenter -t "$pid" -n ip -br link 2>&1; fi
    echo "timers tcc-heal: $(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal)"
    if [ "$st" != running ]; then echo "isn2 NAO esta running. O ensaio nao conserta: decisao do Gestor, depois de o Senior ler os brutos: sudo podman start isn2"; fi
    echo "Retomada (E-7): --so <n> com GO novo, depois da leitura do Senior."
  } > "$OUT/parada.txt" 2>&1
  cat "$OUT/parada.txt"
  fim_ensaio 4; }
fim_ensaio() { # fim_ensaio <rc>: boot_id do fim, avaliacao e MANIFEST.sha256
  local rc="$1" r
  echo "boot_id fim: $(cat "$BOOT_ID_FILE" 2>/dev/null) $(date -Is)" > "$OUT/07-boot-fim.txt"; cat "$OUT/07-boot-fim.txt"
  avaliar "$OUT" | tee "$OUT/08-avaliacao.txt"; r=${PIPESTATUS[0]}; passo_rc avaliacao "$r"
  [ "$rc" != 0 ] || [ "$r" = 0 ] || rc=6
  echo "ENSAIO fim $(date -Is) rc=$rc. Saia do tmux com exit (libera o lock)." >> "$OUT/rc.txt"
  # o ensaio-console.log fica fora: o tee ainda escreve nele depois do manifesto
  ( cd "$OUT" && find . -type f ! -name MANIFEST.sha256 ! -name MANIFEST.sha256.tmp ! -name ensaio-console.log -print0 | LC_ALL=C sort -z | xargs -0 sha256sum ) > "$OUT/MANIFEST.sha256.tmp" \
    && mv -f "$OUT/MANIFEST.sha256.tmp" "$OUT/MANIFEST.sha256"
  echo "ENSAIO fim rc=$rc"; exit "$rc"; }

# ---- 1. injetor --dry-run
PODMAN='sudo podman' bash "$INJ" --dry-run --node isn2 --duration "$F1_DUR" 2>&1 | tee "$OUT/01-injetor-dry-run.log"; RC=${PIPESTATUS[0]}; passo_rc injetor-dry-run "$RC"
[ "$RC" = 0 ] && grep -q 'modo=silencioso' "$OUT/01-injetor-dry-run.log" && grep -q 'dry-run: nada executado' "$OUT/01-injetor-dry-run.log" \
  || abortar 1 "inject-crash.sh --dry-run (ver 01-injetor-dry-run.log)"

# ---- 2. portao geral
portao_k1 "$OUT/02-portao.log"; RC=$?; passo_rc portao "$RC"
[ "$RC" = 0 ] || abortar 3 "portao K1 nao passou: o ensaio NAO roda (ver 02-portao.log)"

# ---- 3..6. as execucoes, uma de cada vez
executar() { # executar <n> <i> <carga> <replicacao>
  local n="$1" i="$2" carga="$3" modo="$4" rot sem lanc anc disp irc st volta_ms ok pid a x
  E="$OUT/exec-$n-i$i"; mkdir -p "$E"
  if [ "$carga" = sem ]; then rot="F1 n=$n i=$i carga=sem"; else rot="F1 n=$n i=$i carga=$carga-$modo"; fi
  sem=$([ "$carga" = sem ] || echo $((S0 + PASSO_SEMENTE * i)))
  { echo "n=$n"; echo "i=$i"; echo "carga=$carga"; echo "replicacao=$modo"; echo "semente=$sem"; echo "rotulo=$rot"; } > "$E/execucao.txt"
  echo "== execucao $n: $rot $(date -Is)"
  pedir_go "$rot" || abortar 3 "GO ausente ou diferente de 'GO $rot': execucao $n nao roda, nada injetado"
  GO_S=$(( $(date +%s) - 1 )); echo "go_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$E/execucao.txt"
  sudo podman events --filter container=isn2 --format json > "$E/events-isn2.json" 2> "$E/events-isn2.err" < /dev/null & EVPID=$!
  saude_loop "$E/saude.csv" "$E/.parar-saude" 100000 < /dev/null & SPID=$!
  conferir_started "$E/started.txt" || abortar 3 "conteiner reiniciado fora do injetor do ensaio (ver $E/started.txt)"
  portao_k1 "$E/portao.log" || abortar 3 "portao K1 da execucao $n nao passou (ver $E/portao.log)"
  sudo podman inspect isn2 > "$E/inspect-isn2-antes.json" 2>&1
  echo "ip_antes=$(ip_de isn2)" >> "$E/execucao.txt"; [ -z "$(pid_de isn2)" ] || echo "pid_antes=$(pid_de isn2)" >> "$E/execucao.txt"
  if [ "$carga" != sem ]; then
    PASSWORD="$RB_PASSWORD"; montar_cmd "$carga" "$modo" "$sem" "$E"; PASSWORD=
    echo "comando=${CMD_MASC[*]}" >> "$E/execucao.txt"
    lanc=$(agora_ms); echo "lanc_ms=$lanc" >> "$E/execucao.txt"
    echo "workload: ${CMD_MASC[*]}"
    "${CMD[@]}" > "$E/stdout.log" 2>&1 < /dev/null & WPID=$!
    CMD=()
    anc=
    while :; do
      if grep -qE "$ANCORA_RE" "$E/stdout.log" 2>/dev/null; then anc=$(agora_ms); break; fi
      kill -0 "$WPID" 2>/dev/null || break
      [ $(( $(agora_ms) - lanc )) -lt $((ANCORA_TIMEOUT_S * 1000)) ] || break
      sleep "$ANCORA_POLL_S"
    done
    [ -n "$anc" ] || parar "workload da execucao $n sem a ancora 'Warm-up ate +' em ${ANCORA_TIMEOUT_S} s: nada injetado"
    echo "anc_ms=$anc" >> "$E/execucao.txt"; echo "ancora a +$(s_de_ms $((anc - lanc))) s do lancamento"
    dormir_ate $((anc + INJ_OFFSET * 1000))
  fi
  disp=$(agora_ms); echo "disp_ms=$disp" >> "$E/execucao.txt"
  echo "F1: inject-crash.sh --node isn2 --duration $F1_DUR $(iso_de_ms "$disp")"
  PODMAN='sudo podman' bash "$INJ" --node isn2 --duration "$F1_DUR" > "$E/injetor.log" 2>&1 < /dev/null & IPID=$!
  wait "$IPID"; irc=$?; IPID=
  echo "injetor_exit=$irc" >> "$E/execucao.txt"; cat "$E/injetor.log"
  [ "$irc" = 0 ] || { coletar_logs; parar "injetor da execucao $n saiu com $irc (C2: 1/4 = recusa K6, 2/3/5 = infraestrutura)"; }
  st=$(inj_ms "$E" "$RE_START") || st=$(agora_ms); echo "start_ms=$st" >> "$E/execucao.txt"
  volta_ms=; ok=0; : > "$E/volta.log"
  while :; do
    amostra_k1; echo "$AM_TXT" >> "$E/volta.log"
    if [ "$AM_OK" = 1 ]; then volta_ms=$(agora_ms); ok=1; break; fi
    [ $(( $(agora_ms) - st )) -lt $((F1_VOLTA_TIMEOUT_S * 1000)) ] || { volta_ms=$(agora_ms); break; }
    sleep "$F1_VOLTA_PERIODO_S"
  done
  echo "volta_ok=$ok" >> "$E/execucao.txt"; echo "volta_s=$(s_de_ms $((volta_ms - st)))" >> "$E/execucao.txt"
  echo "volta: ok=$ok a $(s_de_ms $((volta_ms - st))) s do start"
  kill -TERM "$EVPID" 2>/dev/null; wait "$EVPID" 2>/dev/null; EVPID=
  sudo podman inspect isn2 > "$E/inspect-isn2-depois.json" 2>&1
  if pid=$(pid_de isn2); then echo "pid_depois=$pid" >> "$E/execucao.txt"; sudo nsenter -t "$pid" -n ip -br link > "$E/ip-br-link-isn2.txt" 2>&1; fi
  echo "ip_depois=$(ip_de isn2)" >> "$E/execucao.txt"
  STARTED[isn2]=$(sudo podman inspect -f '{{.State.StartedAt.Unix}}' isn2 2>/dev/null)
  coletar_logs
  [ "$ok" = 1 ] || parar "parada (e): cluster nao voltou a 3 nos HEALTHY sem REBALANCING em ${F1_VOLTA_TIMEOUT_S} s do start (execucao $n)"
  for a in isn1 isn3; do x=$(ddet_ms "$E" "$a")
    if [ -n "$x" ]; then echo "Δdet $a = $(s_de_ms "$x") s"; [ "$x" -ge $((DDET_MIN_S * 1000)) ] || parar "Δdet de $a = $(s_de_ms "$x") s < $DDET_MIN_S s na execucao $n (§4.1)"
    else echo "Δdet $a: sem ISPN000094 sem isn2 depois do isolamento (F-4 FALHA na avaliacao)"; fi; done
  if [ "$carga" != sem ]; then
    wait "$WPID"; a=$?; WPID=; echo "wexit=$a" >> "$E/execucao.txt"; echo "workload saiu com $a"
    if [ -f "$E/rep-001.csv" ]; then
      "${JAVA_CMD[@]}" -cp "$JAR" "$CLASSE_VERIFICADOR" --csv "$E/rep-001.csv" --scenario "$carga" --saida "$E/sanidade.json" > "$E/verificador.out" 2>&1
      a=$?; echo "vexit=$a" >> "$E/execucao.txt"; echo "VerificadorSanidade saiu com $a"
      awk -F, -v a="$(ev "$E" disp_ms)000000" -v b="${volta_ms}000000" 'NR == 1 { for (k = 1; k <= NF; k++) c[$k] = k; next }
        { rc = $c["return_code"]; d[rc]++; o[$c["operation"] " " rc]++; if (rc ~ /^ERROR_/) e[rc]++
          s = $c["start_ns"] + 0; if (s >= a + 0 && s <= b + 0) { w++; l = $c["end_ns"] - s; if (l > mx) mx = l } }
        END { print "linhas=" NR - 1; for (k in e) print "erro " k "=" e[k]; for (k in d) print "desfecho " k "=" d[k]
              for (k in o) print "desfecho_op " k "=" o[k]; printf "janela da falha (disparo..volta): ops=%d latencia_max_s=%.6f\n", w, mx / 1e9 }' \
        "$E/rep-001.csv" | LC_ALL=C sort > "$E/csv-resumo.txt"
      python3 -I - "$RB/analysis" "$E/stdout.log" "$E/rep-001.csv" > "$E/c1-c3.json" 2> "$E/c1-c3.err" <<'PY'
import json, sys
sys.path.insert(0, sys.argv[1])
import rebaseline as r
out = open(sys.argv[2], encoding="utf-8", errors="replace").read()
c = r.criterios_mistura(out, r.ler_csv_eventos(sys.argv[3]), "F1")
print(json.dumps({k: {kk: vv for kk, vv in c[k].items() if kk != "tercos"} for k in ("C1", "C2", "C3")}, default=str))
PY
      echo "c1c3_rc=$?" >> "$E/execucao.txt"
      ( cd "$E" && sha256sum rep-001.csv ) > "$E/rep-001.csv.sha256"
      gzip -1 "$E/rep-001.csv"   # E-6 (pendente do Gestor): comprimido depois do verificador; sha256 gravado antes
    else echo "vexit=" >> "$E/execucao.txt"; echo "AVISO: sem rep-001.csv"; fi
    coletar_logs
  fi
  touch "$E/.parar-saude"; wait "$SPID" 2>/dev/null; SPID=; rm -f "$E/.parar-saude"
  echo "fim_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$E/execucao.txt"
}
k=0
# fd 3: o stdin do laco fica livre para o GO (com 'done <<<' o read do GO leria a tabela, nao o terminal)
while read -r n i carga modo <&3; do
  [ -n "$n" ] || continue
  k=$((k + 1)); executar "$n" "$i" "$carga" "$( [ "$modo" = - ] || echo "$modo")"; passo_rc "execucao-$n" 0
done 3<<<"$SEL"
E=

# ---- 7. estado final
{
  portao_k1 "$OUT/07-portao-final.log" >/dev/null; cat "$OUT/07-portao-final.log" | tail -1
  echo "timers tcc-heal: $(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal)"
  for c in $CACHES; do for p in $PORTAS; do
    echo "size $c porta $p: $(curl -s -m 5 --digest -u "admin:$RB_PASSWORD" "http://127.0.0.1:$p/rest/v2/caches/$c?action=size" 2>/dev/null | tr -d ' \n')"; done; done
} 2>&1 | tee "$OUT/07-estado-final.log"; RC=${PIPESTATUS[0]}; passo_rc estado-final "$RC"

# ---- 8. boot_id do fim, avaliacao e manifesto
fim_ensaio 0
