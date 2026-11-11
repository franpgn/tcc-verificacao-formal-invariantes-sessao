#!/usr/bin/env bash
# runs/gate-b29/g6.test.sh -- T-g6-1..8: testes SEM VM de g6.sh (G-6, F3 jitter em isn2 + F2 particao em isn3 ao mesmo
# tempo) e do corpo do dead-man do netem (g56-lib.sh), com shims e injetores falsos (ver gate-test-lib.sh).
# Rodar:  bash runs/gate-b29/g6.test.sh     Exit 0 se tudo passar.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/gate-test-lib.sh"
G6="$GATE_DIR/g6.sh"
R6="$TCC/runs/gate-b29/g6"
# escala de teste: F2=6 s, F3=10 s, amostras em +1/+3/+4 s, margem de 1 s
cenario() { reset_shims; export G6_F2=6 G6_F3=10 G6_AMOSTRAS="1 3 4" G6_MARGEM=1 G6_LIMITE_S=20 G6_ESPERA_NETEM=5; }
nenhum_estado() { ls "$SHIM_STATE"/clsact.* "$SHIM_STATE"/netem.* 2>/dev/null | wc -l | tr -d ' '; }

echo "== T-g6-1/3: execucao completa (coexistencia PASS) =="
cenario; roda "GO" "$G6"
RES="$(cat "$R6/resultado.txt" 2>/dev/null)"
ok "execucao completa: rc 0" "0" "$RC"
contem "G-6 concluido" "G-6 concluido" "$SAIDA"
nao_contem "sem erro de variavel nao definida" "unbound variable" "$SAIDA"
nao_contem "resultado sem FAIL" "FAIL" "$RES"
# T-g6-1: os dois dead-men antes de qualquer injecao; F3 antes de F2
ordem "T-g6-1 ordem: dead-man, dead-man, jitter (F3), particao (F2)" "$SHIM_LOG" \
  "systemd-run" "systemd-run" "INJ jitter --node isn2" "INJ partition --minority isn3"
ok "T-g6-1 dois dead-men armados" "2" "$(conta 'systemd-run' "$SHIM_LOG")"
contem "T-g6-1 um dead-man e o --heal da F2" "--heal --minority isn3" "$(grep systemd-run "$SHIM_LOG")"
contem "T-g6-1 o outro dead-man e o do netem (tc qdisc del ... root)" "tcc-heal-netem-" "$(grep systemd-run "$SHIM_LOG")"
# T-g6-3: cada injecao so no netns do proprio no
ok "T-g6-3 clsact adicionado so em isn3 (pid 4003)" "1" "$(grep -c '^nsenter -t 4003 -n tc qdisc add dev eth0 clsact' "$SHIM_LOG")"
ok "T-g6-3 nenhum clsact add em isn2/isn1" "0" "$(grep -cE '^nsenter -t 400[12] -n tc qdisc add dev eth0 clsact' "$SHIM_LOG" || true)"
ok "T-g6-3 netem root adicionado so em isn2 (pid 4002)" "1" "$(grep -c '^nsenter -t 4002 -n tc qdisc add dev eth0 root netem' "$SHIM_LOG")"
ok "T-g6-3 nenhum netem add em isn3/isn1" "0" "$(grep -cE '^nsenter -t 400[13] -n tc qdisc add dev eth0 root' "$SHIM_LOG" || true)"
ok "T-g6-3 del clsact so em isn3" "0" "$(grep -E '^nsenter -t [0-9]+ -n tc qdisc del dev eth0 clsact' "$SHIM_LOG" | grep -vc 'nsenter -t 4003' || true)"
ok "T-g6-3 del root so em isn2" "0" "$(grep -E '^nsenter -t [0-9]+ -n tc qdisc del dev eth0 root' "$SHIM_LOG" | grep -vc 'nsenter -t 4002' || true)"
ok "T-g6-3 nenhum add/del em isn1 (pid 4001)" "0" "$(grep -cE '^nsenter -t 4001 -n tc qdisc (add|del)' "$SHIM_LOG" || true)"
contem "T-g6-3 isn2 so netem / isn3 so clsact nas amostras" "PASS durante a janela: isn2 so netem, isn3 so clsact" "$RES"
contem "T-g6-3 limpeza da F2 nao removeu o netem" "PASS a limpeza da F2 NAO removeu o netem de isn2" "$RES"
contem "T-g6-3 jitter nao tocou isn3" "PASS F3: a limpeza do jitter nao tocou em isn3" "$RES"
ok "T-g6-3 estado final limpo (sem clsact/netem)" "0" "$(nenhum_estado)"
ok "T-g6-3 timers desarmados" "0" "$(estado_timers)"
# 4.2.6: o tempo de convergencia e contado a partir do fim da F2
contem "4.2.6 espera_saudavel conta desde o fim da F2" "desde o fim da F2" "$RES"
contem "4.2.6 tempos.txt registra t_f2_fim_s" "t_f2_fim_s=" "$(cat "$R6/tempos.txt")"
# 4.2.2: nenhuma checagem de estado por pipe com grep -q (pipefail + formas negadas)
ok "4.2.2 g6.sh sem 'qd ... | grep'" "0" "$(grep -vE '^[[:space:]]*#' "$G6" | grep -cE '(^|[^a-z_])qd +"?[$]PID[0-9][^|]*[|] *grep' || true)"

echo "== T-g6-2: netns de isn2 = isn3 (ou do host) -> aborta sem injecao =="
cenario; export SHIM_SAMENS="isn2 isn3"; roda "GO" "$G6"
ok "T-g6-2 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g6-2 mensagem" "mesmo netns" "$SAIDA"
ok "T-g6-2 nenhum injetor, dead-man ou tc add" "0" "$(grep -cE '^(INJ|systemd-run)|qdisc add' "$SHIM_LOG" || true)"
ok "T-g6-2 nenhum timer armado" "0" "$(estado_timers)"
cenario; export SHIM_HOSTNET="isn2"; roda "GO" "$G6"
ok "T-g6-2b isn2 no netns do host: rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g6-2b mensagem da guarda" "netns do host" "$SAIDA"
ok "T-g6-2b nenhum injetor, dead-man ou tc add" "0" "$(grep -cE '^(INJ|systemd-run)|qdisc add' "$SHIM_LOG" || true)"

echo "== T-g6-4: F3 termina antes da F2 -> FAIL e abort limpa os dois =="
cenario; export G6_F3=3; roda "GO" "$G6"
RES="$(cat "$R6/resultado.txt" 2>/dev/null)"
ok "T-g6-4 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-g6-4 FAIL registrado" "FAIL" "$RES"
contem "T-g6-4 ABORTADO registrado" "ABORTADO" "$RES"
ok "T-g6-4 --heal da F2 chamado" "1" "$(conta 'INJ heal' "$SHIM_LOG")"
ok "T-g6-4 tc qdisc del root em isn2 chamado" "1" "$([ "$(grep -c '^nsenter -t 4002 -n tc qdisc del dev eth0 root' "$SHIM_LOG")" -ge 1 ] && echo 1 || echo 0)"
ok "T-g6-4 estado final limpo (sem clsact/netem)" "0" "$(nenhum_estado)"
ok "T-g6-4 abort NAO desarma os dead-men (2 timers)" "2" "$(estado_timers)"
ok "T-g6-4 abort nao chama systemctl stop" "0" "$(conta 'systemctl stop' "$SHIM_LOG")"

echo "== T-g6-5: amostra tardia (saude lenta) nao gera falso FAIL =="
cenario; export G6_F2=8 G6_F3=14 G6_AMOSTRAS="1 3 5 7" SHIM_HEALTH_SLOW=1
roda "GO" "$G6"
RES="$(cat "$R6/resultado.txt" 2>/dev/null)"
ok "T-g6-5 rc 0" "0" "$RC"
nao_contem "T-g6-5 sem FAIL" "FAIL" "$RES"
contem "T-g6-5 amostras tardias PULADAS (nao FAIL)" "PULADA" "$RES"
contem "T-g6-5 amostras validas registradas" "PASS durante a janela" "$RES"
ok "T-g6-5 abort nao acionado" "0" "$(grep -c ABORTADO "$R6/resultado.txt")"

echo "== T-g6-6: abort antes de PID2 definido -> sem erro de variavel nao definida =="
cenario; export SHIM_MISSING="isn2"; roda "GO" "$G6"
ok "T-g6-6 rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
nao_contem "T-g6-6 sem 'unbound variable'" "unbound variable" "$SAIDA"
nao_contem "T-g6-6 sem 'nao definida'" "nao definida" "$SAIDA"
ok "T-g6-6 nenhum tc/injetor/dead-man tocado" "0" "$(grep -cE '^(INJ|systemd-run|nsenter)' "$SHIM_LOG" || true)"
contem "T-g6-6 abort informa que nada foi armado" "nada armado" "$SAIDA"
cenario; export SHIM_MISSING="isn3"; roda "GO" "$G6"
nao_contem "T-g6-6b (isn3 ausente) sem 'unbound variable'" "unbound variable" "$SAIDA"
ok "T-g6-6b nenhum injetor/dead-man" "0" "$(grep -cE '^(INJ|systemd-run)' "$SHIM_LOG" || true)"

echo "== T-g6-7: corpo do dead-man do netem (armar_netem) =="
reset_shims
( export PATH="$PATH_SHIMS" TCC_DIR="$TCC"; source "$GATE_DIR/g56-lib.sh"; armar_netem isn2 5 10 ) > "$TMP/armar.out" 2>&1
contem "T-g6-7 armar_netem informa o prazo (5+10=15 s)" "em 15s" "$(cat "$TMP/armar.out")"
mapfile -d '' ARGS < "$SHIM_STATE/sr.1"
idx=-1; for i in "${!ARGS[@]}"; do [[ "${ARGS[$i]}" == bash ]] && { idx=$i; break; }; done
ok "T-g6-7 systemd-run recebeu 'bash -c <corpo> _ isn2'" "bash -c _ isn2" "${ARGS[$idx]} ${ARGS[$((idx+1))]} ${ARGS[$((idx+3))]} ${ARGS[$((idx+4))]}"
contem "T-g6-7 unidade tcc-heal-netem-*" "--unit=tcc-heal-netem-" "${ARGS[*]}"
contem "T-g6-7 --on-active=15s" "--on-active=15s" "${ARGS[*]}"
corpo() { : > "$SHIM_LOG"; PATH="$PATH_SHIMS" "${ARGS[@]:idx}" > "$TMP/corpo.out" 2>&1; CRC=$?; }
export SHIM_STATE
# (a) netns distinto do host: chama tc qdisc del root no pid do no
: > "$SHIM_STATE/netem.4002"; corpo
ok "T-g6-7a netns distinto: rc 0" "0" "$CRC"
ok "T-g6-7a chamou tc qdisc del root em isn2 (4002)" "1" "$(grep -c '^nsenter -t 4002 -n tc qdisc del dev eth0 root' "$SHIM_LOG")"
ok "T-g6-7a o netem foi removido" "nao" "$([ -e "$SHIM_STATE/netem.4002" ] && echo sim || echo nao)"
# (b) pid no netns do host: NAO chama tc
export SHIM_HOSTNET="isn2"; : > "$SHIM_STATE/netem.4002"; corpo
ok "T-g6-7b netns do host: rc != 0" "1" "$([ "$CRC" != 0 ] && echo 1 || echo 0)"
ok "T-g6-7b NAO chamou nsenter/tc" "0" "$(grep -c '^nsenter' "$SHIM_LOG" || true)"
contem "T-g6-7b registra o motivo" "NAO tocado" "$(cat "$TMP/corpo.out")"
ok "T-g6-7b o netem permaneceu (nada tocado)" "sim" "$([ -e "$SHIM_STATE/netem.4002" ] && echo sim || echo nao)"
unset SHIM_HOSTNET
# (c) container inexistente (podman inspect falha): NAO chama tc
export SHIM_MISSING="isn2"; corpo
ok "T-g6-7c container ausente: rc != 0" "1" "$([ "$CRC" != 0 ] && echo 1 || echo 0)"
ok "T-g6-7c NAO chamou nsenter/tc" "0" "$(grep -c '^nsenter' "$SHIM_LOG" || true)"
unset SHIM_MISSING
# (d) leitura do netns falha: NAO chama tc
export SHIM_NO_NS="isn2"; corpo
ok "T-g6-7d netns ilegivel: rc != 0" "1" "$([ "$CRC" != 0 ] && echo 1 || echo 0)"
ok "T-g6-7d NAO chamou nsenter/tc" "0" "$(grep -c '^nsenter' "$SHIM_LOG" || true)"
unset SHIM_NO_NS

echo "== T-g6-8: espera_saudavel conta a partir de uma base (fim da F2) =="
reset_shims
SAIDA="$( export PATH="$PATH_SHIMS" TCC_DIR="$TCC"; source "$GATE_DIR/g56-lib.sh"; espera_saudavel 30 "$(( $(date +%s) - 20 ))" "o fim da F2" 2>&1 )"; RC=$?
ok "T-g6-8 cluster saudavel: rc 0" "0" "$RC"
contem "T-g6-8 rotulo da base" "desde o fim da F2" "$SAIDA"
n="$(sed -n 's/.*apos \([0-9]*\) s.*/\1/p' <<<"$SAIDA" | head -1)"
ok "T-g6-8 tempo reportado >= 20 s (conta desde a base)" "1" "$([ "${n:-0}" -ge 20 ] && echo 1 || echo 0)"
echo nodes2 > "$SHIM_STATE/health.mode"
SAIDA="$( export PATH="$PATH_SHIMS" TCC_DIR="$TCC"; source "$GATE_DIR/g56-lib.sh"; espera_saudavel 3 "$(( $(date +%s) - 2 ))" "o fim da F2" 2>&1 )"; RC=$?
ok "T-g6-8 limite estourado contado desde a base: rc 1" "1" "$RC"
contem "T-g6-8 TIMEOUT" "TIMEOUT" "$SAIDA"

resumo_final
