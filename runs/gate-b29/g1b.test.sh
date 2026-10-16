#!/usr/bin/env bash
# runs/gate-b29/g1b.test.sh -- testes do coletor g1b.sh SEM VM: sintaxe, LF, ausencia de comandos de
# injecao e execucao funcional com java/curl falsos. Uso: bash runs/gate-b29/g1b.test.sh
set -uo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
G1B="$AQUI/g1b.sh"
falhas=0
ok()   { echo "ok   - $1"; }
nok()  { echo "FAIL - $1"; falhas=$((falhas+1)); }
checa() { if eval "$2"; then ok "$1"; else nok "$1"; fi; }

checa "bash -n" 'bash -n "$G1B"'
checa "arquivo em LF (sem CR)" '! grep -q $'"'"'\r'"'"' "$G1B"'
checa "nao inicia injecao nem toca em tc/netns" \
  '! grep -vE "^\s*#" "$G1B" | grep -qE "inject-partition|inject-netdegrade|tc (qdisc|filter)|nsenter|--heal|systemd-run"'
checa "exige REP" 'out=$(env -u REP bash "$G1B" 2>&1); [ $? -ne 0 ] && echo "$out" | grep -q REP'

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/tcc/runs/gate-b29/g1-rep2" "$T/tcc/workload/target"
echo jar > "$T/tcc/workload/target/session-workload-0.1.0-SNAPSHOT.jar"
cat > "$T/bin/java" <<'EOS'
#!/usr/bin/env bash
if [ "${1:-}" = "-version" ]; then echo 'openjdk version "17.0.0" (falso)' >&2; exit 0; fi
echo "t_epoch_ns,no,cache,operacao,duracao_ns,resultado,excecao_classe,mensagem,causas"
echo "$*" >&2
trap 'exit 0' TERM
while true; do sleep 1; done
EOS
cat > "$T/bin/curl" <<'EOS'
#!/usr/bin/env bash
# falso: ecoa um JSON e o codigo no -w, respeitando -o
out=""; fmt=""; url=""
while [ $# -gt 0 ]; do case "$1" in -o) out="$2"; shift;; -w) fmt="$2"; shift;; http*) url="$1";; esac; shift; done
body="{\"url\":\"$url\"}"
if [ -n "$out" ] && [ "$out" != /dev/null ]; then echo "$body" > "$out"; else echo "$body"; fi
[ -n "$fmt" ] && printf '%s' "${fmt//%\{http_code\}/200}" | sed 's/%{time_total}/0.004/'
exit 0
EOS
chmod +x "$T/bin/java" "$T/bin/curl"
M="$T/tcc/runs/gate-b29/g1-rep2/marcadores.txt"
printf 'fault=F2 node=isn3 event=start t_epoch_ns=%s\n' "$(date +%s%N)" > "$M"
printf 'fault=F2 node=isn3 event=end t_epoch_ns=%s\n' "$(( $(date +%s%N) + 1000000000 ))" >> "$M"

PATH="$T/bin:$PATH" TCC_DIR="$T/tcc" REP=2 INTERVALO_S=1 POS_FIM_S=1 ESPERA_START_S=5 \
  bash "$G1B" > "$T/saida.txt" 2>&1
rc=$?
H="$T/tcc/runs/gate-b29/g1-rep2/hotrod"
checa "execucao termina com rc=0" '[ "$rc" = 0 ]'
checa "CSV da sonda de isn3 e de isn1 criados" '[ -s "$H/sonda-isn3.csv" ] && [ -s "$H/sonda-isn1.csv" ]'
checa "sonda recebeu --no do no certo" 'grep -q -- "--no isn3=127.0.0.1:11224" "$H/sonda-isn3.err" && grep -q -- "--no isn1=127.0.0.1:11222" "$H/sonda-isn1.err"'
checa "JSON de saude das tres portas salvo" 'ls "$H"/saude-11222-*.json "$H"/saude-11223-*.json "$H"/saude-11224-*.json >/dev/null 2>&1'
checa "JSON dos caches salvo (lista e stats)" 'ls "$H"/caches-11224-*.json "$H"/stats-sessions-11224-*.json >/dev/null 2>&1'
checa "indice.csv com cabecalho e http" 'head -1 "$H/indice.csv" | grep -q "^t_epoch_ns,porta,recurso,http,tempo_s,arquivo" && grep -q ",200," "$H/indice.csv"'
checa "resumo.txt registra jar, java e marcadores" 'grep -q "jar_sha256" "$H/resumo.txt" && grep -q "start_ns" "$H/resumo.txt" && grep -q "end_ns" "$H/resumo.txt"'
checa "sem sonda orfa" '! pgrep -f "SondaHotrod" >/dev/null 2>&1 || true'

# sem event=start ate o prazo: aborta sem iniciar sonda
rm -f "$M"; rm -rf "$H"
PATH="$T/bin:$PATH" TCC_DIR="$T/tcc" REP=2 INTERVALO_S=1 POS_FIM_S=1 ESPERA_START_S=2 bash "$G1B" > "$T/saida2.txt" 2>&1
rc2=$?
checa "sem event=start aborta (rc!=0) e nao cria CSV" '[ "$rc2" != 0 ] && [ ! -e "$H/sonda-isn3.csv" ]'

echo "falhas=$falhas"; [ "$falhas" = 0 ]
