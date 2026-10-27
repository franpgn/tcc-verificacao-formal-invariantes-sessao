#!/usr/bin/env bash
# runs/gate-b29/recriar.test.sh -- teste SEM VM de recriar.sh (copia verbatim de ~/recriar.sh da VM, sha256 5b1cca41...0766),
# com shims de sudo/podman-compose/podman/nsenter/curl/systemctl num HOME temporario.
# Prova: ordem down -> up, sempre com -f cluster/podman-compose.yml a partir de ~/tcc-gate; o unico tc e 'tc qdisc show'
# (so leitura); o caminho ESTAVEL grava validacao.txt e imprime a linha de resultado.
# NAO prova: podman-compose real, Infinispan real, tempos reais. Isso e o preambulo do S2 na VM.
# Rodar:  bash runs/gate-b29/recriar.test.sh      Exit 0 se tudo passar. Leva ~25 s (o laco real exige 20 s de HEALTHY).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REC="$AQUI/recriar.sh"
P=0; F=0
ok()     { if [ "$2" = "$3" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (esperado '$2', obtido '$3')"; F=$((F+1)); fi; }
contem() { if printf '%s' "$3" | grep -qF -- "$2"; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (sem '$2')"; F=$((F+1)); fi; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$T/home/tcc-gate/cluster" "$T/bin"; LOGS="$T/shim.log"; : > "$LOGS"
# sudo: registra o diretorio corrente e os argumentos e despacha
cat > "$T/bin/sudo" <<'EOF'
#!/usr/bin/env bash
echo "sudo cwd=$PWD $*" >> "$SHIM_LOG"
case "$1 $2" in
  "podman-compose -f") echo "fake compose $*";;
  "podman images") echo "quay.io/infinispan/server  15.0.21.Final  da9c871bb5b4  13 months ago  615 MB";;
  "podman inspect") case "$*" in *Pid*) echo 4242;; *) echo "2026-10-27 19:00:00.000000000 +0000 UTC";; esac;;
  "podman logs") echo "ISPN000094: Received new cluster view for channel cluster-tcc: [isn1-1|2] (3)";;
  nsenter*) echo "qdisc noqueue 0: root refcnt 2";;
  *) echo "shim sudo: inesperado: $*" >&2; exit 99;;
esac
EOF
cat > "$T/bin/curl" <<'EOF'
#!/usr/bin/env bash
echo "curl $*" >> "$SHIM_LOG"
echo '{"health_status":"HEALTHY","number_of_nodes":3}'
EOF
cat > "$T/bin/systemctl" <<'EOF'
#!/usr/bin/env bash
echo "systemctl $*" >> "$SHIM_LOG"
EOF
chmod +x "$T/bin/"*

echo "== T-rec-0: arquivo =="
bash -n "$REC"; ok "T-rec-0 bash -n" 0 $?
ok "T-rec-0 nenhum CR" 0 "$(tr -cd '\r' < "$REC" | wc -c | tr -d ' ')"
ok "T-rec-0 sha256 verbatim da VM" "5b1cca41a2d059acf76f0f1511053dde290a883f983cc266ad79ff9360480766" "$(sha256sum "$REC" | cut -d' ' -f1)"

echo "== T-rec-1: caminho ESTAVEL com shims =="
SAIDA="$(HOME="$T/home" SHIM_LOG="$LOGS" PATH="$T/bin:$PATH" bash "$REC" "$T/saida" 2>&1)"; RC=$?
ok "T-rec-1 rc 0" 0 "$RC"
contem "T-rec-1 down rc=0" "down rc=0" "$SAIDA"
contem "T-rec-1 up rc=0" "up rc=0" "$SAIDA"
contem "T-rec-1 ESTAVEL" "ESTAVEL size=3 HEALTHY" "$SAIDA"
contem "T-rec-1 timers 0" "timers tcc-heal: 0" "$SAIDA"
ok "T-rec-1 validacao.txt existe" 1 "$([ -s "$T/saida/validacao.txt" ] && echo 1 || echo 0)"
ok "T-rec-1 down.log e up.log" 1 "$([ -s "$T/saida/down.log" ] && [ -s "$T/saida/up.log" ] && echo 1 || echo 0)"
L_DOWN=$(grep -n "podman-compose -f cluster/podman-compose.yml down" "$LOGS" | head -1 | cut -d: -f1)
L_UP=$(grep -n "podman-compose -f cluster/podman-compose.yml up -d" "$LOGS" | head -1 | cut -d: -f1)
ok "T-rec-1 down antes de up" 1 "$([ -n "$L_DOWN" ] && [ -n "$L_UP" ] && [ "$L_DOWN" -lt "$L_UP" ] && echo 1 || echo 0)"
ok "T-rec-1 compose so a partir de ~/tcc-gate" 2 "$(grep -c "sudo cwd=$T/home/tcc-gate podman-compose -f cluster/podman-compose.yml" "$LOGS")"
ok "T-rec-1 nenhum tc de escrita" 0 "$(grep -E 'nsenter' "$LOGS" | grep -cvE ' -n tc qdisc show dev eth0$')"
ok "T-rec-1 tres tc qdisc show" 3 "$(grep -cE 'nsenter -t 4242 -n tc qdisc show dev eth0$' "$LOGS")"
ok "T-rec-1 nenhuma chamada inesperada" 0 "$(printf '%s' "$SAIDA" | grep -c 'inesperado')"

echo "RESUMO: $P PASS, $F FAIL"
[ "$F" = 0 ]
