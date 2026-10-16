#!/usr/bin/env bash
# runs/gate-b29/g1.test.sh -- testes SEM VM do log de saude a cada 2 s (g-lib.sh: saude_amostra/saude_log_loop) e do g1.sh
# com shims e injetor falso (gate-test-lib.sh). T-g1-1..6.
# Rodar:  bash runs/gate-b29/g1.test.sh     Exit 0 se tudo passar.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/gate-test-lib.sh"
G1="$GATE_DIR/g1.sh"
R1="$TCC/runs/gate-b29/g1-rep3"
linhas() { [ -f "$1" ] && wc -l < "$1" | tr -d ' ' || echo 0; }
# executa uma funcao da g-lib num subshell com shims
lib() { ( export PATH="$PATH_SHIMS" TCC_DIR="$TCC"; set -uo pipefail; source "$GATE_DIR/g-lib.sh"; "$@" ) 2>&1; }

echo "== T-g1-1: saude_amostra (uma linha por porta: ts,porta,http,status,size) =="
reset_shims
L="$(lib saude_amostra 11224)"
ok "T-g1-1 formato com no saudavel" "1" "$(grep -cE '^[0-9]{19},11224,200,HEALTHY,3$' <<<"$L")"
echo rebal > "$SHIM_STATE/health.mode"; L="$(lib saude_amostra 11222)"
ok "T-g1-1 HEALTHY_REBALANCING e preservado (o health() antigo o descartava)" "1" "$(grep -cE '^[0-9]{19},11222,200,HEALTHY_REBALANCING,3$' <<<"$L")"
echo nodes2 > "$SHIM_STATE/health.mode"; L="$(lib saude_amostra 11223)"
ok "T-g1-1 size=2 (no fora da vista)" "1" "$(grep -cE '^[0-9]{19},11223,200,HEALTHY,2$' <<<"$L")"
export SHIM_DOWN_PORTS="11224"; echo ok > "$SHIM_STATE/health.mode"; L="$(lib saude_amostra 11224)"
ok "T-g1-1 no sem resposta: http=000, status e size vazios" "1" "$(grep -cE '^[0-9]{19},11224,000,,$' <<<"$L")"
unset SHIM_DOWN_PORTS

echo "== T-g1-2: saude_log_loop (3 portas por ciclo, cabecalho, mudanca de estado, ordem no tempo) =="
reset_shims; F="$TMP/saude.csv"
( export PATH="$PATH_SHIMS" TCC_DIR="$TCC"; source "$GATE_DIR/g-lib.sh"; iniciar_log_saude "$F" 1 >/dev/null; sleep 2.5
  echo rebal > "$SHIM_STATE/health.mode"; sleep 2.5
  echo 11224 > "$SHIM_STATE/down.ports"; sleep 2.5; parar_log_saude ) >/dev/null 2>&1
ok "T-g1-2 cabecalho" "t_epoch_ns,porta,http,status,size" "$(head -1 "$F")"
for p in 11222 11223 11224; do
  n=$(grep -c ",$p," "$F"); ok "T-g1-2 porta $p com >= 5 linhas (1 s, ~7 s)" "1" "$([ "$n" -ge 5 ] && echo 1 || echo 0)"
done
ok "T-g1-2 estado HEALTHY visto" "1" "$([ "$(grep -c ',200,HEALTHY,3$' "$F")" -ge 3 ] && echo 1 || echo 0)"
ok "T-g1-2 HEALTHY_REBALANCING visto" "1" "$([ "$(grep -c ',200,HEALTHY_REBALANCING,3$' "$F")" -ge 3 ] && echo 1 || echo 0)"
ok "T-g1-2 no sem resposta (11224) visto como http=000" "1" "$([ "$(grep -c ',11224,000,,$' "$F")" -ge 1 ] && echo 1 || echo 0)"
ok "T-g1-2 so 11224 cai (as outras portas seguem 200)" "0" "$(grep -c ',1122[23],000,' "$F")"
# timestamps por porta nao decrescem; ciclos de ~1 s
ok "T-g1-2 timestamps por porta em ordem crescente" "0" "$(for p in 11222 11224; do grep ",$p," "$F" | cut -d, -f1 | sort -c 2>&1; done | wc -l | tr -d ' ')"
a=$(linhas "$F"); sleep 2; b=$(linhas "$F")
ok "T-g1-2 parar_log_saude: o arquivo para de crescer" "$a" "$b"

echo "== T-g1-3: g1.sh completo (REP=3) com injetor falso: log de saude cobre antes, janela e fusao =="
reset_shims
export REP=3 G1_DUR=6 G1_AMOSTRAS="1 3 5" G1_MERGE_LIM=30 G1_LOG_SAUDE_S=1 STUB_POS_FUSAO=3
roda "GO" "$G1"
ok "T-g1-3 rc 0" "0" "$RC"
contem "T-g1-3 mensagem final" "G-1 concluido (REP=3)" "$SAIDA"
S="$R1/saude-log.csv"
ok "T-g1-3 saude-log.csv existe com cabecalho" "t_epoch_ns,porta,http,status,size" "$(head -1 "$S" 2>/dev/null)"
ST=$(awk -F't_epoch_ns=' '/event=start/{print $2}' "$R1/marcadores.txt"); EN=$(awk -F't_epoch_ns=' '/event=end/{print $2}' "$R1/marcadores.txt")
ok "T-g1-3 ha linhas ANTES do event=start (linha de base)" "1" "$([ "$(awk -F, -v s="$ST" 'NR>1 && $1<s' "$S" | wc -l)" -ge 3 ] && echo 1 || echo 0)"
ok "T-g1-3 ha linhas DURANTE a janela (start..end)" "1" "$([ "$(awk -F, -v s="$ST" -v e="$EN" 'NR>1 && $1>=s && $1<=e' "$S" | wc -l)" -ge 9 ] && echo 1 || echo 0)"
ok "T-g1-3 ha linhas DEPOIS do event=end (fusao)" "1" "$([ "$(awk -F, -v e="$EN" 'NR>1 && $1>e' "$S" | wc -l)" -ge 1 ] && echo 1 || echo 0)"
ok "T-g1-3 apos o end ha linhas com size=2 (fusao em curso) e depois size=3" "1" "$([ "$(awk -F, -v e="$EN" 'NR>1 && $1>e && $5==2' "$S" | wc -l)" -ge 1 ] && [ "$(tail -3 "$S" | grep -c ',HEALTHY,3$')" -ge 1 ] && echo 1 || echo 0)"
for p in 11222 11223 11224; do ok "T-g1-3 porta $p presente" "1" "$([ "$(grep -c ",$p," "$S")" -ge 5 ] && echo 1 || echo 0)"; done
contem "T-g1-3 t_merge registrado" "t_merge_s=" "$(cat "$R1/t_merge.txt")"
ok "T-g1-3 logs de vista extraidos (log-completo-isn3)" "sim" "$([ -e "$R1/log-completo-isn3.txt" ] && echo sim || echo nao)"
ok "T-g1-3 dead-man desarmado (0 timers)" "0" "$(estado_timers)"
ok "T-g1-3 sem clsact residual" "0" "$(ls "$SHIM_STATE"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
ordem "T-g1-3 ordem: dead-man antes do injetor; stop do dead-man depois" "$SHIM_LOG" "systemd-run" "INJ partition --minority isn3 --duration 6" "systemctl stop"
a=$(linhas "$S"); sleep 2.5; b=$(linhas "$S")
ok "T-g1-3 o log de saude parou junto com o g1.sh (nao cresce)" "$a" "$b"
ok "T-g1-3 go registrado" "1" "$(grep -c 'go G-1' "$TCC/runs/gate-b29/go.log")"

echo "== T-g1-4: abort (injetor rc != 0) -> dead-man armado, logs extraidos e log de saude parado =="
reset_shims
export REP=3 G1_DUR=6 G1_AMOSTRAS="1 3 5" G1_MERGE_LIM=30 G1_LOG_SAUDE_S=1 STUB_DIE_RC=5
roda "GO" "$G1"
S="$R1/saude-log.csv"
ok "T-g1-4 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g1-4 mensagem de abort" "ABORTAR: rc=5" "$SAIDA"
ok "T-g1-4 dead-man permanece armado" "1" "$(estado_timers)"
ok "T-g1-4 logs extraidos no abort" "sim" "$([ -e "$R1/log-completo-isn1.txt" ] && echo sim || echo nao)"
ok "T-g1-4 saude-log.csv existe (coletado ate o abort)" "1" "$([ "$(linhas "$S")" -ge 2 ] && echo 1 || echo 0)"
a=$(linhas "$S"); sleep 2.5; b=$(linhas "$S")
ok "T-g1-4 o log de saude parou no abort (trap EXIT)" "$a" "$b"

echo "== T-g1-5: sem GO nenhuma acao (tc/nsenter/systemd-run/injetor/log de saude) =="
reset_shims; export REP=3
roda "NAO" "$G1"
ok "T-g1-5 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g1-5 sem go" "sem go" "$SAIDA"
ok "T-g1-5 nenhuma chamada a tc/nsenter/systemd-run/injetor/curl" "0" "$(grep -cE '^(nsenter|systemd-run|systemctl|tc|curl|INJ)' "$SHIM_LOG" || true)"
ok "T-g1-5 sem saude-log.csv" "nao" "$([ -e "$R1/saude-log.csv" ] && echo sim || echo nao)"

echo "== T-g1-6: cluster fora de size=3 -> aborta antes de armar/injetar =="
reset_shims; export REP=3; echo nodes2 > "$SHIM_STATE/health.mode"
roda "GO" "$G1"
ok "T-g1-6 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g1-6 mensagem" "cluster fora de size=3" "$SAIDA"
ok "T-g1-6 nada armado nem injetado" "0" "$(grep -cE '^(systemd-run|INJ)|qdisc add' "$SHIM_LOG" || true)"

echo "== T-g1-7: G1_LOG_SAUDE_S=0 desliga o log (REP=4 sem coleta extra) =="
reset_shims
export REP=3 G1_DUR=4 G1_AMOSTRAS="1 3" G1_MERGE_LIM=30 G1_LOG_SAUDE_S=0
roda "GO" "$G1"
ok "T-g1-7 rc 0" "0" "$RC"
ok "T-g1-7 sem saude-log.csv" "nao" "$([ -e "$R1/saude-log.csv" ] && echo sim || echo nao)"
contem "T-g1-7 mensagem final" "G-1 concluido (REP=3)" "$SAIDA"
nao_contem "T-g1-7 sem mensagem de log de saude" "log de saude" "$SAIDA"

resumo_final
