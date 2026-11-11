#!/usr/bin/env bash
# runs/gate-b29/g5.test.sh -- T-g5-1..7: testes SEM VM de g5.sh (G-5, kill -TERM / kill -KILL do injetor em isn3),
# com shims de podman/nsenter/tc/systemd-run/systemctl e injetor falso (ver gate-test-lib.sh).
# Rodar:  bash runs/gate-b29/g5.test.sh     Exit 0 se tudo passar.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/gate-test-lib.sh"
G5="$GATE_DIR/g5.sh"
R5="$TCC/runs/gate-b29/g5"
export G5_DUR=5 G5_T_KILL=2 G5_LIMITE_S=20 G5_ESPERA_START=3

echo "== T-g5-1/2/6: execucao completa (TERM e depois KILL) =="
reset_shims; export G5_DUR=5 G5_T_KILL=2 G5_LIMITE_S=20 G5_ESPERA_START=3
roda "GO" "$G5"
RES="$(cat "$R5/resultado.txt" 2>/dev/null)"
ok "execucao completa: rc 0" "0" "$RC"
contem "T-g5-1 TERM: rc=143 via trap" "PASS TERM: injetor saiu com rc=143" "$RES"
contem "T-g5-1 TERM: sem clsact apos o trap" "PASS TERM: sem clsact apos o trap" "$RES"
contem "T-g5-1 TERM: marcadores start e end" "PASS TERM: marcadores start e end presentes" "$RES"
nao_contem "T-g5-1 TERM: sem FAIL" "FAIL" "$RES"
ok "T-g5-1 marcadores do TERM: 1 start + 1 end" "2" "$(grep -c 'event=' "$R5/marcadores-term.txt")"
contem "T-g5-2 KILL: rc=137" "PASS KILL: injetor saiu com rc=137" "$RES"
contem "T-g5-2 KILL: clsact ficou" "PASS KILL: clsact FICOU" "$RES"
contem "T-g5-2 KILL: sem marcador end" "PASS KILL: sem marcador end" "$RES"
contem "T-g5-2 KILL: --heal rc=0" "PASS KILL: --heal rc=0" "$RES"
contem "T-g5-2 KILL: sem clsact apos --heal" "PASS KILL: sem clsact apos --heal" "$RES"
ok "T-g5-2 KILL: marcador sem end" "0" "$(grep -c 'event=end' "$R5/marcadores-kill.txt")"
ok "T-g5-2 estado final: nenhum clsact residual" "0" "$(ls "$SHIM_STATE"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
ok "T-g5-2 estado final: 0 timers (desarmados)" "0" "$(estado_timers)"
contem "G-5 concluido" "G-5 concluido" "$SAIDA"
nao_contem "sem erro de variavel nao definida" "unbound variable" "$SAIDA"
ok "T-g5-2 abort-heal.log nao existe no caminho feliz" "nao" "$([ -e "$R5/abort-heal.log" ] && echo sim || echo nao)"
grep -q "go G-5" "$TCC/runs/gate-b29/go.log" 2>/dev/null && echo "PASS: go.log registrou o go" && PASS=$((PASS+1)) || { echo "FAIL: go.log sem registro"; FAIL=$((FAIL+1)); }
# T-g5-6: ordem das chamadas (dead-man ANTES do injetor nas duas fases; stop so apos o sucesso de cada fase)
ordem "T-g5-6 ordem: dead-man, injetor, stop, dead-man, injetor, heal, stop" "$SHIM_LOG" \
  "systemd-run" "INJ partition --minority isn3" "systemctl stop" \
  "systemd-run" "INJ partition --minority isn3" "INJ partition --heal --minority isn3" "systemctl stop"
ok "T-g5-6 dois dead-men armados" "2" "$(conta 'systemd-run' "$SHIM_LOG")"
ok "T-g5-6 dois stops" "2" "$(conta 'systemctl stop' "$SHIM_LOG")"
contem "T-g5-6 dead-man usa --heal do injetor" "--heal --minority isn3" "$(grep systemd-run "$SHIM_LOG" | head -1)"

echo "== T-g5-3: sem event=start -> FAIL e abortar chama --heal =="
reset_shims; export G5_DUR=5 G5_T_KILL=2 G5_LIMITE_S=20 G5_ESPERA_START=3; export STUB_SEM_START=1
roda "GO" "$G5"
RES="$(cat "$R5/resultado.txt" 2>/dev/null)"
ok "T-g5-3 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g5-3 FAIL sem event=start" "FAIL term: sem event=start" "$RES"
contem "T-g5-3 abortar registrado" "ABORTADO" "$RES"
ok "T-g5-3 --heal chamado pelo abort" "1" "$(conta 'INJ heal' "$SHIM_LOG")"
ok "T-g5-6 abort NAO desarma (0 systemctl stop)" "0" "$(conta 'systemctl stop' "$SHIM_LOG")"
ok "T-g5-6 dead-man permanece armado (1 timer)" "1" "$(estado_timers)"
ok "T-g5-3 so a fase TERM foi tentada" "1" "$(conta 'INJ partition --minority' "$SHIM_LOG")"

echo "== T-g5-4: clsact ausente antes do sinal -> FAIL e heal =="
reset_shims; export G5_DUR=5 G5_T_KILL=2 G5_LIMITE_S=20 G5_ESPERA_START=3; export STUB_SEM_CLSACT=1
roda "GO" "$G5"
RES="$(cat "$R5/resultado.txt" 2>/dev/null)"
ok "T-g5-4 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g5-4 FAIL clsact ausente" "FAIL term: clsact ausente ANTES do sinal" "$RES"
ok "T-g5-4 --heal chamado" "1" "$(conta 'INJ heal' "$SHIM_LOG")"
ok "T-g5-4 nenhum sinal enviado ao injetor (sem rc_injetor)" "0" "$(grep -c rc_injetor "$R5/estado-term.txt" 2>/dev/null || true)"
ok "T-g5-4 dead-man permanece armado" "1" "$(estado_timers)"

echo "== T-g5-5: rc != 137 na fase KILL -> FAIL =="
reset_shims; export G5_DUR=5 G5_T_KILL=2 G5_LIMITE_S=20 G5_ESPERA_START=3; export STUB_DIE_RC=7 STUB_DIE_CALL=2
roda "GO" "$G5"
RES="$(cat "$R5/resultado.txt" 2>/dev/null)"
contem "T-g5-5 fase TERM passou" "PASS TERM: injetor saiu com rc=143" "$RES"
contem "T-g5-5 FAIL KILL com rc=7" "FAIL KILL: rc=7 (esperado 137)" "$RES"
ok "T-g5-5 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
ok "T-g5-5 abort chamou --heal" "1" "$(conta 'INJ heal' "$SHIM_LOG")"
ok "T-g5-5 clsact removido pelo abort" "0" "$(ls "$SHIM_STATE"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
ok "T-g5-5 dead-man da fase KILL permanece armado (1 timer)" "1" "$(estado_timers)"

echo "== T-g5-7: sem GO nenhuma chamada a tc/nsenter/systemd-run =="
reset_shims; export G5_DUR=5 G5_T_KILL=2 G5_LIMITE_S=20 G5_ESPERA_START=3
roda "NAO" "$G5"
ok "T-g5-7 resposta != GO: rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g5-7 mensagem sem go" "sem go" "$SAIDA"
ok "T-g5-7 nenhuma chamada a tc/nsenter/systemd-run/inject" "0" "$(grep -cE '^(nsenter|systemd-run|systemctl|tc)|^INJ' "$SHIM_LOG" || true)"
ok "T-g5-7 nenhuma chamada a podman (nem o pid foi resolvido)" "0" "$(grep -c '^podman' "$SHIM_LOG" || true)"
reset_shims
SAIDA="$(PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$G5" </dev/null 2>&1)"; RC=$?
ok "T-g5-7b sem terminal e sem G_TESTE: aborta" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g5-7b mensagem sem terminal" "sem terminal" "$SAIDA"
ok "T-g5-7b nenhuma chamada a tc/nsenter/systemd-run" "0" "$(grep -cE '^(nsenter|systemd-run|systemctl|tc)|^INJ' "$SHIM_LOG" || true)"
reset_shims
SAIDA="$(PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$G5" --dry-run 2>&1)"; RC=$?
ok "T-g5-7c --dry-run rc 0" "0" "$RC"
contem "T-g5-7c --dry-run imprime o plano" "G-5 (dry-run; nada executado)" "$SAIDA"
ok "T-g5-7c --dry-run sem nenhuma chamada" "0" "$(wc -l < "$SHIM_LOG" | tr -d ' ')"

resumo_final
