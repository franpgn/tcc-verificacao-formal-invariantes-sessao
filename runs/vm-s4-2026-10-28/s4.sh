#!/usr/bin/env bash
# runs/vm-s4-2026-10-28/s4.sh -- involucro do S4 (REP=4R) na VM. NAO injeta nada por conta propria e NAO digita o GO.
#
# Uso (dentro do tmux aberto sob flock, em ~/tcc-gate):   bash runs/vm-s4-2026-10-28/s4.sh
# Sequencia (decisoes do Gestor de 2026-10-28; decisoes-senior-2026-10-16 §5.5 com a redefinicao REP=4R da V-S1 §5.2):
#   0. pre-condicoes (TCC_DIR, tmux, lock ocupado por este flock, sudo -n, uname -r, boot_id, HEAD, hashes dos scripts,
#      java/jar, g1-rep4 inexistente, 0 timers, ambiente sem G1_*/REP/COLETA...) e vista atual; aborta antes de tocar no cluster;
#   1. copia de leitura do cluster antigo (inspect, .State.Status, logs e journal do boot anterior; falha de leitura
#      vira AVISO) e confirmacao do Gestor: digitar RECRIAR;
#   2. recriacao com runs/gate-b29/recriar.sh (down/up; sem go proprio);
#   3. portao 5.1 com runs/vm-s2-2026-10-27/portao51.sh (.log gravado), duracao >= 130 s e fim >= up+345 s (5 min sem
#      mudanca de vista + folga); se falhar, PARA (S4 nao roda);
#   4. g1b (REP=4 COLETA=minima) numa janela tmux propria, sem pegar o lock; g1.sh REP=4 (90 s em isn3, saude a 2 s,
#      G1_LOG_POS_S=30) neste terminal: o prompt do GO e o do proprio g1.sh (exigir_go), digitado pelo Gestor;
#   5. espera o g1b, registra t_merge, hashes de g1-rep4 e go.log; portao 5.1 pos (fim >= fim do g1 + 345 s).
# Saidas: runs/vm-s4-2026-10-28/vm-<UTC>/ (console inteiro em s4-console.log; rc e fim de cada passo em rc.txt).
# Saida: 0 ok; 1 pre-condicao/confirmacao; 2 recriacao; 3 portao pre; 4 g1 (ver o aviso do dead-man); 5 t_merge > 60 s
# (PARAR: nao seguir para o S5); 6 portao pos.
# S4_TESTE=1 so nos testes com shims (s4.test.sh): permite trocar os limites, o boot_id e a tabela de hashes.
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="$AQUI/s4.sh"
TESTE="${S4_TESTE:-0}"

KERNEL_ESPERADO=6.8.0-147-generic
# boot de 2026-10-28 06:39:35 UTC: a VM reiniciou depois do crash do host (BSOD 0x154 as 06:36 UTC, na tentativa 1
# do S4). Deixa de ser o boot de S2 e S3 (b7df4b03-6652-4808-9617-11096693e65b, V-S3 criterio 16).
BOOT_ESPERADO=5d7119b8-3b50-435d-bd0d-cbdb6bd410df
BOOT_ID_FILE=/proc/sys/kernel/random/boot_id
LOCK=/var/lock/tcc-vm.lock
PORTAO_MIN_S=130          # duracao minima do portao (S2/S3 usaram 130)
PORTAO_FOLGA_S=345        # o portao termina >= base + 345 s: a regra "0 mudancas de vista em 5 min" vale com folga
PORTAO_BOA_S=120          # §5.1: >= 120 s de HEALTHY/3
G1B_ESPERA_RC_S=600       # espera maxima pelo fim do g1b depois do g1
# Referencia de hashes: bundle v4c (902654b), aceita pelo Gestor em 2026-10-28 no lugar do "bundle igual ao de S0" (§5.5).
# inject-partition.sh e g-lib.sh sao iguais aos de S0 (V-S3, pre-requisito 3).
HASHES_ESPERADOS='59dd2beee5b003d85f96cae71866227d7bca1c87a9ceeab034180ce4254d94e1  scripts/inject-partition.sh
af3436d5ede54f5f2047ba48620130a803b4bbd8929e2cbc242fbbe0ebb922dd  runs/gate-b29/g-lib.sh
4f750fe3cf59f299585c12f66b3020973976e0e979474fde6354bf6998a18e2a  runs/gate-b29/g56-lib.sh
4645159b454b3fd412c1178c1ec15ce97b673d52c5adaa570c21c985fdbb8990  runs/gate-b29/g1.sh
546aa6b09a1a500650aa46a35600ca4f83f02b643d580f2b0b49ece8c0f26fd8  runs/gate-b29/g1b.sh
5b1cca41a2d059acf76f0f1511053dde290a883f983cc266ad79ff9360480766  runs/gate-b29/recriar.sh
2598b37d4969b1bd717ddbe514110fb6a0077e1332e38c1b76c5caea61802338  runs/vm-s2-2026-10-27/portao51.sh'
if [ "${1:-}" = --hashes ]; then printf '%s\n' "$HASHES_ESPERADOS"; exit 0; fi
if [ "$TESTE" = 1 ]; then
  BOOT_ESPERADO="${S4_BOOT_ESPERADO:-$BOOT_ESPERADO}"; BOOT_ID_FILE="${S4_BOOT_ID_FILE:-$BOOT_ID_FILE}"
  PORTAO_MIN_S="${S4_PORTAO_MIN_S:-$PORTAO_MIN_S}"; PORTAO_FOLGA_S="${S4_PORTAO_FOLGA_S:-$PORTAO_FOLGA_S}"
  PORTAO_BOA_S="${S4_PORTAO_BOA_S:-$PORTAO_BOA_S}"; G1B_ESPERA_RC_S="${S4_G1B_ESPERA_RC_S:-$G1B_ESPERA_RC_S}"
  [ -z "${S4_HASHES:-}" ] || HASHES_ESPERADOS="$(cat "$S4_HASHES")"
fi

# ---- avaliacao do .log do portao51.sh (criterios da §5.1; o lock deve estar OCUPADO por este flock)
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
  grep -qx 'lock: OCUPADO' "$f" || falha "lock nao esta OCUPADO (o s4.sh deve rodar dentro do flock)"
  n=$(grep -cE '^isn[123] mudancas de vista nos ultimos 5 min \(.*\): 0$' "$f"); [ "$n" = 3 ] || falha "nos sem mudanca de vista em 5 min: $n de 3"
  grep -q '^fim ' "$f" || falha "portao nao chegou ao fim"
  [ "$e" = 0 ] && echo "PORTAO 5.1: OK (amostras_ruins=0, maior_sequencia_boa_s=$boa)"
  return "$e"
}
if [ "${1:-}" = --avaliar-portao ]; then avaliar_portao "$2"; exit $?; fi

# ---- modo interno: g1b na janela tmux propria (sem lock proprio), REP=4 COLETA=minima, como na REP=3
if [ "${1:-}" = --g1b ]; then
  OUT="$2"; export TCC_DIR="$HOME/tcc-gate"; cd "$TCC_DIR" || exit 1
  echo "$$" > "$OUT/g1b.pid"
  echo "g1b (S4): REP=4 COLETA=minima TCC_DIR=$TCC_DIR inicio $(date -Is)" | tee "$OUT/05-g1b.log"
  REP=4 COLETA=minima bash runs/gate-b29/g1b.sh 2>&1 | tee -a "$OUT/05-g1b.log"; rc=${PIPESTATUS[0]}
  echo "g1b rc=$rc fim=$(date -Is)" | tee -a "$OUT/05-g1b.log"; echo "$rc" > "$OUT/g1b.rc"
  exit "$rc"
fi

# ---- 0. TCC_DIR, diretorio de saida e console
if [ -z "${TCC_DIR:-}" ]; then export TCC_DIR="$HOME/tcc-gate"; TCC_ORIGEM="exportado pelo s4.sh"
elif [ "$TCC_DIR" = "$HOME/tcc-gate" ]; then export TCC_DIR; TCC_ORIGEM="ja exportado"
else echo "S4 ABORTADO: TCC_DIR=$TCC_DIR (precisa ser $HOME/tcc-gate)" >&2; exit 1; fi
cd "$TCC_DIR" || { echo "S4 ABORTADO: sem $TCC_DIR" >&2; exit 1; }
[ "$AQUI" = "$TCC_DIR/runs/vm-s4-2026-10-28" ] || { echo "S4 ABORTADO: s4.sh fora de $TCC_DIR ($AQUI)" >&2; exit 1; }
OUT="$AQUI/vm-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT" || exit 1
exec > >(tee -a "$OUT/s4-console.log") 2>&1
TEE_PID=$!
trap 'exec 1>&- 2>&-; wait "$TEE_PID" 2>/dev/null' EXIT
passo_rc() { echo "$1 rc=$2 fim=$(date -Is)" | tee -a "$OUT/rc.txt"; }
abortar() { local rc="$1"; shift; echo "S4 ABORTADO (rc=$rc): $*" >&2; echo "abortado rc=$rc: $* $(date -Is)" >> "$OUT/rc.txt"; exit "$rc"; }
trap 'echo "S4 interrompido (Ctrl-C) $(date -Is); se o injetor ja tinha iniciado, o dead-man cura em 180 s" >&2; echo "interrompido $(date -Is)" >> "$OUT/rc.txt"; exit 130' INT
echo "S4 (REP=4R) inicio $(date -Is) saida=$OUT"
echo "TCC_DIR=$TCC_DIR ($TCC_ORIGEM)"

# ---- 0. pre-condicoes (so leitura); qualquer falha aborta antes de tocar no cluster
{
  E=0; nok() { echo "NAO OK: $*"; E=1; }
  echo "== pre-condicoes $(date -Is)"
  echo "hostname=$(hostname) ip=$(hostname -I 2>/dev/null | awk '{print $1}') usuario=$(id -un)"
  [ -n "${TMUX:-}" ] && echo "tmux: $TMUX" || nok "fora do tmux (TMUX vazio)"
  if flock -n "$LOCK" true; then nok "lock $LOCK LIVRE: rode dentro de 'flock $LOCK tmux new -s s4'"; else echo "lock $LOCK: OCUPADO (este flock)"; fi
  sudo -n true && echo "sudo -n: ok" || nok "sudo -n true falhou"
  K=$(uname -r); echo "uname -r: $K"; [ "$K" = "$KERNEL_ESPERADO" ] || nok "kernel $K (esperado $KERNEL_ESPERADO)"
  B=$(cat "$BOOT_ID_FILE" 2>/dev/null); echo "boot_id: $B"; [ "$B" = "$BOOT_ESPERADO" ] || nok "boot_id $B (esperado $BOOT_ESPERADO: houve reboot?)"
  echo "HEAD: $(git rev-parse HEAD 2>/dev/null) ($(git log -1 --format=%s 2>/dev/null))"
  git merge-base --is-ancestor 902654b HEAD 2>/dev/null && echo "contem 902654b (v4c): sim" || echo "contem 902654b (v4c): nao"
  N=$(git status --porcelain --untracked-files=no 2>/dev/null | wc -l); echo "arquivos versionados modificados: $N"; [ "$N" = 0 ] || nok "$N arquivos versionados modificados"
  echo "-- sha256 (referencia v4c, aceita pelo Gestor em 2026-10-28)"
  if printf '%s\n' "$HASHES_ESPERADOS" | sha256sum -c -; then echo "hashes: iguais a referencia"; else nok "hash diferente da referencia"; fi
  echo "s4.sh: $(sha256sum "$SELF" | cut -d' ' -f1)"
  V=$(env | grep -E '^(G1_[A-Z_]*|REP|COLETA|INTERVALO_S|POS_FIM_S|MAX_S|ESPERA_START_S|SONDA_[A-Z_]*|G_TESTE|LOCK_DIR|PODMAN|INJ)=' | cut -d= -f1 | paste -sd' ')
  [ -z "$V" ] && echo "ambiente: sem G1_*/REP/COLETA/... herdados" || nok "variaveis herdadas que mudariam o g1/g1b: $V"
  command -v java >/dev/null && echo "java: $(java -version 2>&1 | head -1)" || nok "java ausente (o g1b precisa)"
  J=$(ls workload/target/session-workload-*.jar 2>/dev/null | head -1)
  [ -n "$J" ] && echo "jar: $J $(sha256sum "$J" | cut -d' ' -f1)" || nok "jar workload/target/session-workload-*.jar ausente"
  [ ! -e runs/gate-b29/g1-rep4 ] && echo "runs/gate-b29/g1-rep4: inexistente" || nok "runs/gate-b29/g1-rep4 ja existe (nao misturar com uma REP anterior)"
  T=$(systemctl list-timers --all --no-legend 2>/dev/null | grep -c tcc-heal); echo "timers tcc-heal: $T"; [ "$T" = 0 ] || nok "$T timers tcc-heal armados"
  echo "-- runs/gate-b29/go.log (particoes anteriores registradas)"; cat runs/gate-b29/go.log 2>/dev/null || echo "(sem go.log)"
  echo "-- conteineres (antes da recriacao)"
  sudo podman ps -a --format '{{.Names}} {{.ID}} {{.Status}} criado={{.CreatedAt}}' 2>&1
  exit "$E"
} 2>&1 | tee "$OUT/00-pre.log"
RC=${PIPESTATUS[0]}; passo_rc pre "$RC"
[ "$RC" = 0 ] || abortar 1 "pre-condicoes (ver $OUT/00-pre.log)"

# ---- 1. copia de leitura do cluster antigo e vista atual
# Depois de um reboot da VM (2026-10-28) os conteineres ficam em Created: podman logs pode sair vazio ou so com o boot
# atual. Por isso o journal do boot anterior (-b -1) tambem e copiado. Toda falha de leitura aqui e AVISO, nao aborta.
mkdir -p "$OUT/pre-recriacao"
JB="$OUT/pre-recriacao/journal-boot-anterior.txt"
{
  echo "== cluster antigo $(date -Is)"
  for n in isn1 isn2 isn3; do
    sudo podman inspect "$n" > "$OUT/pre-recriacao/inspect-$n.json" 2>&1 || echo "AVISO: podman inspect $n falhou"
    sudo podman logs "$n" > "$OUT/pre-recriacao/logs-$n.txt" 2>&1 || echo "AVISO: podman logs $n falhou"
    echo "$n State.Status=$(sudo podman inspect -f '{{.State.Status}}' "$n" 2>&1) StartedAt=$(sudo podman inspect -f '{{.State.StartedAt}}' "$n" 2>&1) logs=$(wc -l < "$OUT/pre-recriacao/logs-$n.txt") linhas"
    v=$(grep -E 'ISPN000094|ISPN000093' "$OUT/pre-recriacao/logs-$n.txt" | tail -1 | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-200)
    echo "$n ultima vista: ${v:-nenhuma em podman logs (ver journal-boot-anterior.txt)}"
  done
  echo "-- journal do boot anterior (-b -1) -> pre-recriacao/journal-boot-anterior.txt"
  JAV=""
  { echo "## journalctl --list-boots (ultimos 3)"; sudo journalctl --list-boots --no-pager 2>&1 | tail -3; } > "$JB"
  for n in isn1 isn2 isn3; do
    echo "## journalctl -b -1 --no-pager -o short-iso-precise CONTAINER_NAME=$n" >> "$JB"
    sudo journalctl -b -1 --no-pager -o short-iso-precise CONTAINER_NAME="$n" > "$JB.$n" 2>&1; r=$?
    cat "$JB.$n" >> "$JB"; echo "## rc=$r" >> "$JB"; [ "$r" = 0 ] || JAV="$JAV $n:rc=$r"
    v=$(grep -E 'ISPN000094|ISPN000093' "$JB.$n" | tail -1 | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-200); rm -f "$JB.$n"
    echo "$n ultima vista (journal -b -1): ${v:-nenhuma}"
  done
  echo "## journalctl -b -1 --no-pager -o short-iso-precise SYSLOG_IDENTIFIER=podman SYSLOG_IDENTIFIER=conmon | grep isn[123]" >> "$JB"
  sudo journalctl -b -1 --no-pager -o short-iso-precise SYSLOG_IDENTIFIER=podman SYSLOG_IDENTIFIER=conmon 2>>"$JB" | grep -E 'isn[123]' >> "$JB"
  r=${PIPESTATUS[0]}; echo "## rc=$r" >> "$JB"; [ "$r" = 0 ] || JAV="$JAV podman/conmon:rc=$r"
  if [ -n "$JAV" ]; then echo "AVISO: journalctl -b -1 falhou ($JAV); a copia segue sem ele (ver journal-boot-anterior.txt)"
  else echo "journal do boot anterior: $(wc -l < "$JB") linhas"; fi
} 2>&1 | tee "$OUT/01-pre-recriacao.log"
passo_rc copia-cluster-antigo "${PIPESTATUS[0]}"
[ -t 0 ] || [ "$TESTE" = 1 ] || abortar 1 "sem terminal (a confirmacao e interativa)"
read -rp "Recriar o cluster agora (podman-compose down/up)? [digite RECRIAR] " R
echo "resposta: $R $(date -Is)" >> "$OUT/rc.txt"
[ "$R" = RECRIAR ] || abortar 1 "recriacao nao confirmada"

# ---- 2. recriacao (recriar.sh, sem go proprio; ja estamos dentro do flock, entao sem flock -n aninhado)
bash runs/gate-b29/recriar.sh "$OUT/recriacao" 2>&1 | tee "$OUT/02-recriacao.log"
RC=${PIPESTATUS[0]}; passo_rc recriacao "$RC"
grep -q '^down rc=0$' "$OUT/02-recriacao.log" && grep -q '^up rc=0$' "$OUT/02-recriacao.log" \
  && grep -q '^ESTAVEL size=3 HEALTHY' "$OUT/02-recriacao.log" || abortar 2 "recriacao sem down rc=0/up rc=0/ESTAVEL (ver 02-recriacao.log)"
T0_UP=$(sed -n 's/^t0_up_epoch=//p' "$OUT/recriacao/validacao.txt" | head -1)
[ -n "$T0_UP" ] || abortar 2 "sem t0_up_epoch em recriacao/validacao.txt"
{
  echo "== cluster novo $(date -Is) (t0_up_epoch=$T0_UP $(date -Is -d @"$T0_UP"))"
  for n in isn1 isn2 isn3; do
    echo "$n id=$(sudo podman inspect -f '{{.Id}}' "$n" 2>&1 | cut -c1-12) criado=$(sudo podman inspect -f '{{.Created}}' "$n" 2>&1) StartedAt=$(sudo podman inspect -f '{{.State.StartedAt}}' "$n" 2>&1)"
  done
  for n in isn1 isn2 isn3; do
    echo "-- $n vista e rebalance (topology id)"
    sudo podman logs "$n" 2>&1 | grep -E 'ISPN000094|ISPN000093|ISPN100010' | sed 's/\x1b\[[0-9;]*m//g' | tail -8 | cut -c1-220
  done
} 2>&1 | tee "$OUT/03-pos-recriacao.log"
passo_rc pos-recriacao "${PIPESTATUS[0]}"

# ---- 3. portao 5.1 (com .log gravado); se falhar, PARA
portao() { # portao <nome> <base-epoch>: fim >= base + PORTAO_FOLGA_S e duracao >= PORTAO_MIN_S
  local dur rc
  dur=$(( $2 + PORTAO_FOLGA_S - $(date +%s) )); [ "$dur" -ge "$PORTAO_MIN_S" ] || dur=$PORTAO_MIN_S
  echo "portao $1: ${dur} s a partir de $(date -Is) (base $(date -Is -d @"$2") + ${PORTAO_FOLGA_S} s)"
  bash runs/vm-s2-2026-10-27/portao51.sh "$dur" "$OUT/$1.csv" 2>&1 | tee "$OUT/$1.log"
  rc=${PIPESTATUS[0]}; passo_rc "$1" "$rc"
  avaliar_portao "$OUT/$1.log" | tee "$OUT/$1.avaliacao"; rc=${PIPESTATUS[0]}
  passo_rc "$1-avaliacao" "$rc"; return "$rc"
}
portao 04-portao51-pre "$T0_UP" || abortar 3 "portao 5.1 pre falhou: S4 NAO roda (ver 04-portao51-pre.log/.avaliacao)"

# ---- 4. g1b (janela tmux propria) e g1.sh (GO digitado pelo Gestor no prompt do g1.sh)
WID=$(tmux new-window -d -P -F '#{window_id}' -n s4-g1b "bash '$SELF' --g1b '$OUT'") || abortar 4 "tmux new-window do g1b falhou"
echo "g1b iniciado na janela tmux $WID (s4-g1b) $(date -Is)" | tee -a "$OUT/rc.txt"
for _ in $(seq 1 20); do [ -s "$OUT/g1b.pid" ] && break; sleep 0.5; done
[ -s "$OUT/g1b.pid" ] || { tmux kill-window -t "$WID" 2>/dev/null; abortar 4 "g1b nao subiu em 10 s"; }
{
  echo "== antes do g1 $(date -Is)"
  for n in isn1 isn2 isn3; do s=$(sudo podman inspect -f '{{.State.StartedAt}}' "$n" 2>/dev/null)
    echo "$n StartedAt=$s idade_s=$(( $(date +%s) - $(date +%s -d "${s% UTC}" 2>/dev/null || echo 0) ))"; done
} 2>&1 | tee "$OUT/05-pre-g1.log"
echo ">>> G-1: confira no prompt 'G-1 (REP=4, janela de 90 s em isn3)' e digite GO (qualquer outra coisa aborta)."
REP=4 G1_DUR=90 G1_LOG_SAUDE_S=2 G1_LOG_POS_S=30 bash runs/gate-b29/g1.sh 2>&1 | tee "$OUT/05-g1.log"
RC=${PIPESTATUS[0]}; T_G1_FIM=$(date +%s); passo_rc g1 "$RC"
R4=runs/gate-b29/g1-rep4
if [ "$RC" != 0 ]; then
  if ! grep -q 'event=start' "$R4/marcadores.txt" 2>/dev/null; then
    tmux kill-window -t "$WID" 2>/dev/null; echo "g1b encerrado (g1 sem event=start) $(date -Is)" | tee -a "$OUT/rc.txt"
  fi
  { echo "== estado apos falha do g1 $(date -Is)"
    echo "timers: $(systemctl list-timers --all --no-legend 2>/dev/null | grep tcc-heal || echo nenhum)"
    p=$(sudo podman inspect -f '{{.State.Pid}}' isn3 2>/dev/null); echo "isn3 pid=$p tc: $(sudo nsenter -t "$p" -n tc qdisc show dev eth0 2>&1 | tr '\n' ';')"
  } 2>&1 | tee "$OUT/06-pos-falha.log"
  echo "Se o injetor iniciou, o dead-man cura em 180 s. Cura manual (em ~/tcc-gate): PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3"
  abortar 4 "g1.sh rc=$RC"
fi

# ---- 5. fim do g1b, registros e portao pos
for _ in $(seq 1 "$G1B_ESPERA_RC_S"); do [ -s "$OUT/g1b.rc" ] && break; sleep 1; done
passo_rc g1b "$(cat "$OUT/g1b.rc" 2>/dev/null || echo ausente)"
{
  echo "== depois do g1 $(date -Is)"
  cat "$R4/marcadores.txt"; cat "$R4/t_merge.txt"
  cp runs/gate-b29/go.log "$OUT/go.log"; tail -2 "$OUT/go.log"
  find "$R4" -type f | sort | xargs sha256sum > "$OUT/g1-rep4.sha256"; echo "arquivos em $R4: $(wc -l < "$OUT/g1-rep4.sha256")"
} 2>&1 | tee "$OUT/06-pos-g1.log"
TM=$(sed -n 's/^t_merge_s=\([0-9]*\).*/\1/p' "$R4/t_merge.txt" | head -1)
FIM=0
if [ -z "$TM" ] || [ "$TM" -gt 60 ]; then
  echo "PARAR: t_merge_s=${TM:-ausente} (> 60 s ou ausente). Nao seguir para o S5; preservar os logs e chamar o Senior." | tee -a "$OUT/rc.txt"; FIM=5
fi
portao 07-portao51-pos "$T_G1_FIM" || { [ "$FIM" != 0 ] || FIM=6; }
echo "S4 fim $(date -Is) rc=$FIM. Saia do tmux com exit (libera o lock)." | tee -a "$OUT/rc.txt"
exit "$FIM"
