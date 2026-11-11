#!/usr/bin/env bash
# B-26.2: smoke local da sonda de topologia (Podman local, NAO a VM).
# Uso: JAR=<caminho do jar> SONDA_SENHA=<senha> ./rodar.sh P1|P2|P3 [dir_saida] [duracao_s_P3]
# P3a = P3 com 120 s (protocolo do prompt); P3b = P3 com 180 s (a volta do isn3 nao coube em 120 s
# porque o podman start local levou ~26 s; ver o relatorio).
# Pre-condicao: cluster b262 de pe (cluster/podman-compose.b262.yml, projeto b262, portas 12262..12264).
# Smoke local: tempos nao sao evidencia de deteccao na VM.
# Os .txt gerados aqui (stderr, marcos t-*.txt, saida do podman) ficam fora do git (D-005,
# runs/**/*.txt); foram consolidados em stderr.json, marcos.json e podman-estado.json.
set -euo pipefail
cd "$(dirname "$0")"
: "${JAR:?defina JAR}"
: "${SONDA_SENHA:?defina SONDA_SENHA}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
SERVERS=127.0.0.1:12262,127.0.0.1:12263,127.0.0.1:12264
CLI=br.unipampa.tcc.session.SondaTopologiaCli

caso="$1"
out="${2:-$1}"
dur3="${3:-120}"
mkdir -p "$out"
case "$caso" in
  P1) "$JAVA" -cp "$JAR" "$CLI" --servers "$SERVERS" --replication sync --duracao-s 30 \
        > "$out"/amostras.jsonl 2> "$out"/stderr.txt ;;
  P2) "$JAVA" -cp "$JAR" "$CLI" --servers "$SERVERS" --replication async --duracao-s 30 \
        > "$out"/amostras.jsonl 2> "$out"/stderr.txt ;;
  P3)
    date +%s%N > "$out"/t-inicio-cli.txt
    "$JAVA" -cp "$JAR" "$CLI" --servers "$SERVERS" --replication async --duracao-s "$dur3" \
        > "$out"/amostras.jsonl 2> "$out"/stderr.txt &
    pid=$!
    sleep 30
    date +%s%N > "$out"/t-stop-inicio.txt
    podman stop b262-isn3 > "$out"/podman-stop.txt 2>&1
    date +%s%N > "$out"/t-stop-fim.txt
    sleep 30
    date +%s%N > "$out"/t-start-inicio.txt
    podman start b262-isn3 > "$out"/podman-start.txt 2>&1
    date +%s%N > "$out"/t-start-fim.txt
    wait "$pid"
    ;;
  *) echo "caso desconhecido: $caso" >&2; exit 2 ;;
esac
