#!/usr/bin/env bash
# runs/gate-b29/g5b.test.sh -- testes SEM VM de g5b.sh (G-5b-A: cura manual apos split real; G-5b-B: dead-man disparando sozinho),
# com shims de podman/nsenter/tc/systemd-run/systemctl/journalctl e injetor falso (ver gate-test-lib-v4.sh).
# NAO provam: o kernel, o JGroups/Infinispan reais, o formato real dos logs, o AccuracySec real do systemd. Isso e o gate na VM.
# Rodar:  bash runs/gate-b29/g5b.test.sh      Exit 0 se tudo passar.
# G5B_DIR (opcional) aponta para uma COPIA dos scripts (usado por g5b.mutacao.sh para testar mutantes).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$AQUI/gate-test-lib-v4.sh"
G5B_DIR="${G5B_DIR:-$AQUI}"
G5B="$G5B_DIR/g5b.sh"
RA="$TCC/runs/gate-b29/g5b-A"; RB="$TCC/runs/gate-b29/g5b-B"
nenhuma_acao() { grep -cE '^(nsenter|systemd-run|systemctl|tc)|^INJ' "$SHIM_LOG" || true; }
# parametros de teste: janela curta, mesmas relacoes do real (KILL depois do split, persistencia, margem curta no B)
cenario_curto() { export G5B_DUR=8 G5B_T_KILL=3 G5B_ESPERA_START=4 G5B_ESPERA_SPLIT=7 G5B_PERSISTE_S=2 G5B_LIMITE_S=20 G5B_MARGEM_B=6 G5B_TOL_S=5 G5B_FOLGA_S=3; }
nao_zero() { [ "$1" != 0 ] && echo 1 || echo 0; }
# espera_cond <s> <cmd...>: repete cmd a cada 0,2 s por NO MAXIMO <s> segundos (rc 1 se esgotar). Os vigias de segundo plano dos
# casos T-b-5/6d/8e/8f eram `until ...; do sleep 0.2; done` SEM LIMITE: se o g5b.sh abortava antes de escrever o arquivo esperado
# (ex.: sem event=start dentro de G5B_ESPERA_START=4 s numa maquina carregada), o vigia girava para sempre e o `wait` pendurava o teste.
espera_cond() { local t="$1" i=0; shift; until "$@" >/dev/null 2>&1; do i=$((i+1)); [ "$i" -lt $((t*5)) ] || return 1; sleep 0.2; done; }

echo "== T-b-0: sintaxe, uso e dry-run =="
bash -n "$G5B" 2>/dev/null; ok "T-b-0 bash -n g5b.sh" "0" "$?"
if [[ "$G5B_DIR" == "$AQUI" ]] && git -C "$AQUI" rev-parse --git-dir >/dev/null 2>&1; then
  for f in g5b.sh g5b.test.sh; do m="$(git -C "$AQUI" ls-files -s -- "$f" | cut -d' ' -f1)"
    [[ -n "$m" ]] && ok "T-b-0 modo git 100755 em $f" "100755" "$m" || echo "SKIP: $f ainda nao esta no indice"; done
fi
ok "T-b-0 nenhum CR" "0" "$(cat "$G5B" | tr -cd '\r' | wc -c | tr -d ' ')"
reset_shims_v4; SAIDA="$(PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$G5B" 2>&1 </dev/null)"; RC=$?
ok "T-b-0 sem modo: rc 2" "2" "$RC"; contem "T-b-0 uso" "uso:" "$SAIDA"
SAIDA="$(PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$G5B" C 2>&1 </dev/null)"; RC=$?
ok "T-b-0 modo invalido: rc 2" "2" "$RC"
for m in A B; do
  reset_shims_v4; SAIDA="$(PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$G5B" $m --dry-run 2>&1)"; RC=$?
  ok "T-b-0 --dry-run $m rc 0" "0" "$RC"; contem "T-b-0 --dry-run $m plano" "G-5b-$m (dry-run; nada executado)" "$SAIDA"
  contem "T-b-0 --dry-run $m exige split confirmado" "split CONFIRMADO nos logs" "$SAIDA"
  ok "T-b-0 --dry-run $m sem nenhuma chamada" "0" "$(wc -l < "$SHIM_LOG" | tr -d ' ')"
done
contem "T-b-0 plano A tem a cura manual" "5A. inject-partition.sh --heal" "$(PATH="$PATH_SHIMS" bash "$G5B" A --dry-run)"
contem "T-b-0 plano B diz NENHUM --heal" "NENHUM --heal e NENHUM desarmar" "$(PATH="$PATH_SHIMS" bash "$G5B" B --dry-run)"

echo "== T-b-1: sem terminal / sem GO / guarda de netns / preflight sujo: nada e tocado =="
reset_shims_v4; PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$G5B" A </dev/null > "$TMP/saida.txt" 2>&1; RC=$?; SAIDA="$(cat "$TMP/saida.txt")"
ok "T-b-1 sem terminal e sem G_TESTE: rc 1" "1" "$RC"; contem "T-b-1 mensagem" "sem terminal" "$SAIDA"; ok "T-b-1 0 acoes" "0" "$(nenhuma_acao)"
reset_shims_v4; roda "NAO" "$G5B" A
ok "T-b-1 resposta != GO: rc 1" "1" "$RC"; contem "T-b-1 sem go" "sem go" "$SAIDA"; ok "T-b-1 0 acoes e 0 podman" "0" "$(( $(nenhuma_acao) + $(grep -c '^podman' "$SHIM_LOG" || true) ))"
reset_shims_v4; export SHIM_HOSTNET=isn3; roda "GO" "$G5B" A; unset SHIM_HOSTNET
ok "T-b-1 netns do host: rc 1" "1" "$RC"; contem "T-b-1 guarda" "ABORTAR: PID invalido ou netns do host" "$SAIDA"; ok "T-b-1 0 acoes" "0" "$(nenhuma_acao)"
reset_shims_v4; mkdir -p "$SHIM_STATE"; echo nodes2 > "$SHIM_STATE/health.mode"; roda "GO" "$G5B" A
ok "T-b-1 cluster size=2: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-1 preflight recusa" "ABORTAR: cluster fora de size=3 HEALTHY" "$SAIDA"
ok "T-b-1 preflight sujo: nenhum armar/injetor/heal (nao mexe no que nao e seu)" "0" "$(nenhuma_acao)"
reset_shims_v4; mkdir -p "$SHIM_STATE"; : > "$SHIM_STATE/clsact.4003"; roda "GO" "$G5B" B
ok "T-b-1 clsact residual previo: rc != 0, nada armado" "0" "$(( $(nenhuma_acao) - $(grep -c '^nsenter' "$SHIM_LOG" || true) ))"

echo "== T-b-2: modo A caminho feliz =="
reset_shims_v4; cenario_curto; roda "GO" "$G5B" A
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"; ES="$(cat "$RA/estado.txt" 2>/dev/null)"
ok "T-b-2 rc 0" "0" "$RC"; contem "T-b-2 concluido" "G-5b-A concluido" "$SAIDA"; nao_contem "T-b-2 sem FAIL" "FAIL" "$RES"; nao_contem "T-b-2 sem ABORTADO" "ABORTADO" "$RES"
contem "T-b-2 split confirmado antes do KILL" "PASS split real confirmado nos logs ANTES do KILL" "$RES"
contem "T-b-2 rc 137" "PASS injetor saiu com rc=137" "$RES"
contem "T-b-2 corte persistiu" "PASS corte PERSISTIU" "$RES"
contem "T-b-2 --heal rc 0" "PASS --heal rc=0" "$RES"; contem "T-b-2 sem clsact apos heal" "PASS sem clsact apos --heal" "$RES"
contem "T-b-2 MergeView x3" "PASS MergeView de 3 membros nos 3 logs apos o heal" "$RES"
contem "T-b-2 desarmado so depois de saudavel" "PASS dead-man desarmado SO depois" "$RES"
contem "T-b-2 HEALTHY x3 apos o heal" "size=3 HEALTHY apos" "$RES"
contem "T-b-2 cronologia" "PASS cronologia coerente: t_start t_split_confirmado t_kill t_persistencia_ok t_heal t_mergeview t_saudavel t_desarmado" "$RES"
contem "T-b-2 sem ISPN000524..." "PASS sem ISPN000524/ISPN100014/ISPN000638" "$RES"
contem "T-b-2 estado: rc_injetor=137" "rc_injetor=137" "$ES"
tk=$(sed -n 's/^t_kill=//p' "$RA/cronologia.txt"); ts=$(sed -n 's/^t_start=//p' "$RA/cronologia.txt")
ok "T-b-2 KILL so a partir de T_KILL=3 s do start" "1" "$([ $(( (tk - ts) / 1000000 )) -ge 3000 ] && echo 1 || echo 0)"
ordem "T-b-2 ordem das chamadas: dead-man, injetor, heal manual, stop" "$SHIM_LOG" "systemd-run" "INJ partition --minority isn3" "INJ heal" "systemctl stop"
ok "T-b-2 dead-man de 98 s (8+90)" "1" "$(grep -c -- '--on-active=98s' "$SHIM_LOG")"
contem "T-b-2 timer com AccuracySec=1s" "AccuracySec=1s" "$(grep systemd-run "$SHIM_LOG")"
ok "T-b-2 1 injecao e 1 heal manual" "1 1" "$(conta 'INJ partition --minority' "$SHIM_LOG") $(conta 'INJ heal' "$SHIM_LOG")"
ok "T-b-2 1 stop (desarmar) e 0 timers ao fim" "1 0" "$(conta 'systemctl stop' "$SHIM_LOG") $(estado_timers)"
ok "T-b-2 sem clsact residual" "0" "$(ls "$SHIM_STATE"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
ok "T-b-2 marcador: 1 start, 0 end (KILL)" "1 0" "$(grep -c event=start "$RA/marcadores.txt") $(grep -c event=end "$RA/marcadores.txt")"
ok "T-b-2 chamadas-injetor registra a injecao e o heal" "2" "$(wc -l < "$RA/chamadas-injetor.txt" | tr -d ' ')"
ok "T-b-2 artefatos presentes" "ok" "$(for f in cronologia.txt unit-armada.txt estado.txt persistencia.txt qdisc-apos-kill.txt qdisc-pos-heal.txt saude-antes.txt saude-depois.txt log-completo-isn1.txt log-vista-isn3.txt heal.log; do [ -e "$RA/$f" ] || { echo "falta $f"; exit; }; done; echo ok)"
contem "T-b-2 go.log registrou G-5b-A" "go G-5b-A" "$(cat "$TCC/runs/gate-b29/go.log")"
nao_contem "T-b-2 sem variavel nao definida" "unbound variable" "$SAIDA"

echo "== T-b-3: modo A: split NAO aparece nos logs -> nao ha KILL (seria repeticao do G-5) =="
reset_shims_v4; cenario_curto; export SHIM_LOGS_SEM_SPLIT=1; roda "GO" "$G5B" A; unset SHIM_LOGS_SEM_SPLIT
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-3 rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-3 FAIL split nao confirmado" "FAIL split NAO confirmado nos logs" "$RES"
nao_contem "T-b-3 nenhum sinal enviado (sem t_kill)" "sinal=KILL" "$(cat "$RA/estado.txt" 2>/dev/null)"
ok "T-b-3 abort chamou --heal" "1" "$(conta 'INJ heal' "$SHIM_LOG")"; ok "T-b-3 dead-man continua armado e nao foi desarmado" "1 0" "$(estado_timers) $(conta 'systemctl stop' "$SHIM_LOG")"
ok "T-b-3 sem clsact apos o abort" "0" "$(ls "$SHIM_STATE"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"

echo "== T-b-3b: modo A: split visto so em isn3 (isn1/isn2 sem a vista de 2): nao confirma =="
reset_shims_v4; cenario_curto; export SHIM_SPLIT_SO_ISN3=1; roda "GO" "$G5B" A; unset SHIM_SPLIT_SO_ISN3
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-3b rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-3b FAIL split nao confirmado" "FAIL split NAO confirmado nos logs" "$RES"

echo "== T-b-4: modo A: injetor morre antes do KILL =="
reset_shims_v4; cenario_curto; export STUB_DIE_RC=7 STUB_DIE_CALL=1; roda "GO" "$G5B" A; unset STUB_DIE_RC STUB_DIE_CALL
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-4 rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-4 FAIL" "FAIL injetor terminou antes" "$RES"
ok "T-b-4 abort chamou --heal; dead-man armado" "1 1" "$(conta 'INJ heal' "$SHIM_LOG") $(estado_timers)"

echo "== T-b-5: modo A: o corte NAO persiste (clsact some sozinho apos o KILL) =="
reset_shims_v4; cenario_curto; export G5B_PERSISTE_S=6
( espera_cond 90 grep -q 'rc_injetor=' "$RA/estado.txt" && rm -f "$SHIM_STATE"/clsact.4003 ) & VIGIA=$!
roda "GO" "$G5B" A; wait "$VIGIA"
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-5 rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-5 FAIL clsact sumiu" "FAIL clsact SUMIU" "$RES"
nao_contem "T-b-5 nao chegou ao --heal manual de A" "PASS --heal rc=0" "$RES"
echo "== T-b-5b: modo A: vistas se juntam sozinhas durante o corte (MergeView antes do heal) =="
reset_shims_v4; cenario_curto; export SHIM_MERGE_PREMATURE=1; roda "GO" "$G5B" A; unset SHIM_MERGE_PREMATURE
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-5b rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-5b FAIL MergeView" "FAIL MergeView apareceu nos logs desde o KILL" "$RES"

echo "== T-b-6: modo A: heal manual falha / MergeView nao vem / MergeView incompleto =="
reset_shims_v4; cenario_curto; export STUB_HEAL_RC=1; roda "GO" "$G5B" A; unset STUB_HEAL_RC
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-6a --heal rc=1: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-6a FAIL --heal" "FAIL --heal rc=1" "$RES"; ok "T-b-6a nao desarmou" "0" "$(conta 'systemctl stop' "$SHIM_LOG")"
reset_shims_v4; cenario_curto; export SHIM_LOGS_SEM_MERGE=1 G5B_LIMITE_S=6; roda "GO" "$G5B" A; unset SHIM_LOGS_SEM_MERGE
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-6b sem MergeView: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-6b FAIL MergeView" "FAIL MergeView de 3 membros nao apareceu" "$RES"
ok "T-b-6b nao desarmou (dead-man segue armado)" "0 1" "$(conta 'systemctl stop' "$SHIM_LOG") $(estado_timers)"

reset_shims_v4; cenario_curto; export SHIM_MERGE_PARCIAL=1 G5B_LIMITE_S=6; roda "GO" "$G5B" A; unset SHIM_MERGE_PARCIAL
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-6c MergeView de 2 membros (nao 3): rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-6c FAIL MergeView de 3" "FAIL MergeView de 3 membros nao apareceu" "$RES"

reset_shims_v4; cenario_curto; export G5B_LIMITE_S=6
# o cluster fica em size=2 DESDE o KILL (antes de qualquer consulta de saude do g5b.sh): gatilhar em heal.log/journal-unit.txt era
# corrida (no Linux o 1o `health` do espera_saudavel sai ~30 ms depois do heal, antes do vigia de 0,2 s escrever nodes2 -> o g5b.sh via
# HEALTHY e dava PASS). Nada consulta `health` entre o KILL e o heal/disparo, entao o efeito e o mesmo, sem depender de velocidade.
( espera_cond 90 grep -q "rc_injetor=" "$RA/estado.txt" && echo nodes2 > "$SHIM_STATE/health.mode" ) & VIGIA=$!
roda "GO" "$G5B" A; wait "$VIGIA"
RES="$(cat "$RA/resultado.txt" 2>/dev/null)"
ok "T-b-6d cluster nao volta a size=3 depois do heal: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-6d FAIL cluster nao voltou" "FAIL cluster nao voltou a size=3 HEALTHY" "$RES"
ok "T-b-6d nao desarmou (dead-man segue armado)" "0 1" "$(conta 'systemctl stop' "$SHIM_LOG") $(estado_timers)"

echo "== T-b-7: modo B caminho feliz (dead-man e o unico agente de cura) =="
reset_shims_v4; cenario_curto; roda "GO" "$G5B" B
RES="$(cat "$RB/resultado.txt" 2>/dev/null)"; TEMPOS="$(cat "$RB/tempos.txt" 2>/dev/null)"
ok "T-b-7 rc 0" "0" "$RC"; contem "T-b-7 concluido" "G-5b-B concluido" "$SAIDA"; nao_contem "T-b-7 sem FAIL" "FAIL" "$RES"; nao_contem "T-b-7 sem ABORTADO" "ABORTADO" "$RES"
contem "T-b-7 split confirmado" "PASS split real confirmado nos logs ANTES do KILL" "$RES"
contem "T-b-7 rc 137" "PASS injetor saiu com rc=137" "$RES"; contem "T-b-7 persistiu" "PASS corte PERSISTIU" "$RES"
contem "T-b-7 disparo >= prazo" "PASS disparo nao antes de 14 s" "$RES"; contem "T-b-7 disparo <= prazo+tol" "PASS disparo ate 14+5 s" "$RES"
contem "T-b-7 unico agente de cura" "PASS nenhum --heal manual registrado: o dead-man foi o UNICO agente de cura" "$RES"
contem "T-b-7 journal heal" "PASS journal: a unit executou o --heal" "$RES"; contem "T-b-7 journal sucesso" "PASS journal: unit encerrou com sucesso" "$RES"
contem "T-b-7 0 timers sem desarmar" "PASS 0 timers tcc-heal ao fim (sem desarmar)" "$RES"
contem "T-b-7 MergeView apos o disparo" "PASS MergeView de 3 membros nos 3 logs apos o disparo" "$RES"
contem "T-b-7 HEALTHY x3 apos o disparo" "size=3 HEALTHY apos" "$RES"
contem "T-b-7 cronologia" "PASS cronologia coerente: t_start t_split_confirmado t_kill t_persistencia_ok t_fire t_mergeview t_saudavel" "$RES"
contem "T-b-7 tempos gravados" "prazo_s=14" "$TEMPOS"
ok "T-b-7 NENHUM systemctl stop (nunca desarma)" "0" "$(conta 'systemctl stop' "$SHIM_LOG")"
ok "T-b-7 o --heal vem so da unit (1 chamada) e nao do script" "1" "$(conta 'INJ heal' "$SHIM_LOG")"
ok "T-b-7 chamadas-injetor: so a injecao (0 --heal)" "0" "$(grep -c -- '--heal' "$RB/chamadas-injetor.txt" || true)"
ok "T-b-7 dead-man de 14 s (8+6)" "1" "$(grep -c -- '--on-active=14s' "$SHIM_LOG")"
ordem "T-b-7 ordem: dead-man antes do injetor; heal da unit depois" "$SHIM_LOG" "systemd-run" "INJ partition --minority isn3" "INJ heal"
ok "T-b-7 sem clsact residual e 0 timers" "0 0" "$(ls "$SHIM_STATE"/clsact.* 2>/dev/null | wc -l | tr -d ' ') $(estado_timers)"
contem "T-b-7 unit-armada corpo --heal --minority isn3" "inject-partition.sh --heal --minority isn3" "$(cat "$RB/unit-armada.txt")"
contem "T-b-7 journal-unit gravado" "Heal: removendo" "$(cat "$RB/journal-unit.txt")"

echo "== T-b-8: modo B mutantes de comportamento do dead-man =="
reset_shims_v4; cenario_curto; export SHIM_DM_NOFIRE=1; roda "GO" "$G5B" B; unset SHIM_DM_NOFIRE
RES="$(cat "$RB/resultado.txt" 2>/dev/null)"
ok "T-b-8a dead-man NAO dispara: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-8a FAIL nao disparou" "FAIL dead-man NAO disparou" "$RES"
ok "T-b-8a rede de seguranca: 1 --heal (do abort) e sem clsact" "1 0" "$(conta 'INJ heal' "$SHIM_LOG") $(ls "$SHIM_STATE"/clsact.* 2>/dev/null | wc -l | tr -d ' ')"
contem "T-b-8a abort registrado e o bloco falha" "ABORTADO" "$RES"; contem "T-b-8a abort heal anotado em chamadas" "--heal" "$(cat "$RB/chamadas-injetor.txt")"
reset_shims_v4; cenario_curto; export SHIM_DM_ATRASO=6; roda "GO" "$G5B" B; unset SHIM_DM_ATRASO
RES="$(cat "$RB/resultado.txt" 2>/dev/null)"
ok "T-b-8b disparo tardio: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-8b FAIL DEPOIS" "FAIL disparo DEPOIS de 14+5 s" "$RES"
reset_shims_v4; cenario_curto; export SHIM_DM_PRAZO=11; roda "GO" "$G5B" B; unset SHIM_DM_PRAZO
RES="$(cat "$RB/resultado.txt" 2>/dev/null)"
ok "T-b-8c disparo antecipado: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-8c FAIL ANTES" "FAIL disparo ANTES de 14 s" "$RES"
reset_shims_v4; cenario_curto; export STUB_HEAL_RC=1; roda "GO" "$G5B" B; unset STUB_HEAL_RC
RES="$(cat "$RB/resultado.txt" 2>/dev/null)"
ok "T-b-8d unit limpa mas termina rc != 0: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-8d FAIL journal" "FAIL journal" "$RES"
reset_shims_v4; cenario_curto; export G5B_PERSISTE_S=5
( espera_cond 90 grep -q 'rc_injetor=' "$RB/estado.txt" && rm -f "$SHIM_STATE"/clsact.4003 ) & VIGIA=$!
roda "GO" "$G5B" B; wait "$VIGIA"
RES="$(cat "$RB/resultado.txt" 2>/dev/null)"
ok "T-b-8e cura antes do prazo (intervencao externa): rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-8e FAIL clsact sumiu" "FAIL clsact SUMIU" "$RES"

reset_shims_v4; cenario_curto; export G5B_LIMITE_S=6
# idem 6d: size=2 desde o KILL, nao desde o journal (corrida no Linux)
( espera_cond 90 grep -q "rc_injetor=" "$RB/estado.txt" && echo nodes2 > "$SHIM_STATE/health.mode" ) & VIGIA=$!
roda "GO" "$G5B" B; wait "$VIGIA"
RES="$(cat "$RB/resultado.txt" 2>/dev/null)"
ok "T-b-8f cluster nao volta a size=3 depois do disparo: rc != 0" "1" "$(nao_zero "$RC")"; contem "T-b-8f FAIL cluster nao voltou" "FAIL cluster nao voltou a size=3 HEALTHY" "$RES"

echo "== T-b-9: armar redefinido = armar de g-lib.sh + AccuracySec; G5B_ACC=0 usa o de g-lib =="
norm() { awk '/^armar\(\)/,/em \$\(\( \$1 \+ \$\{2:-90\} \)\)s"; }/' "$1" | tr -d '\\\n' | tr -s ' ' | sed 's/^armar() *//; s/ --timer-property=AccuracySec=1s//; s/ *echo "dead-man armado.*//'; }
ok "T-b-9 corpo do armar identico (sem a propriedade)" "$(norm "$AQUI/g-lib.sh")" "$(norm "$G5B")"
reset_shims_v4; cenario_curto; export G5B_ACC=0; roda "GO" "$G5B" A; unset G5B_ACC
ok "T-b-9 ACC=0: rc 0" "0" "$RC"; nao_contem "T-b-9 ACC=0 sem timer-property" "timer-property" "$(grep systemd-run "$SHIM_LOG")"
contem "T-b-9 ACC=0 AccuracySec default 1min" "AccuracySec=1min" "$(cat "$RA/unit-armada.txt")"

matar_timers
resumo_final
