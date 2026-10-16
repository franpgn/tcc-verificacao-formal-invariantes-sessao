#!/usr/bin/env bash
# runs/gate-netdegrade/l-gate.test.sh -- testes SEM VM de l-lib.sh, l0.sh, l1.sh (verbatim do roteiro) e l0c.sh (novo).
# Shims de podman/nsenter/tc/systemd-run/systemctl/journalctl e injetor falso (ver ../gate-b29/gate-test-lib-v4.sh).
# NAO provam: o kernel, o systemd real (AccuracySec), o formato real de `tc -s`/journal, nem o efeito do netem. Isso e o gate na VM.
# Rodar:  bash runs/gate-netdegrade/l-gate.test.sh        Exit 0 se tudo passar.
# L_DIR (opcional) aponta para uma COPIA dos scripts (usado por l-gate.mutacao.sh para testar mutantes).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$AQUI/../gate-b29/gate-test-lib-v4.sh"
L_DIR="${L_DIR:-$AQUI}"
L0="$L_DIR/l0.sh"; L1="$L_DIR/l1.sh"; L0C="$L_DIR/l0c.sh"; LLIB="$L_DIR/l-lib.sh"
VERB="$L_DIR/verbatim.sh"
DOC="${DOC_ROTEIRO:-$AQUI/../../docs/relatorio-b29-2-implementacao-2026-10-16.md}"
RL="$TCC/runs/gate-netdegrade"
chamadas_perigosas() { grep -cE '^(nsenter|systemd-run|systemctl|tc|ip|INJ)|^podman' "$SHIM_LOG" || true; }
nenhuma_rede() { grep -cE '^(nsenter|systemd-run|systemctl|tc|ip|INJ)' "$SHIM_LOG" || true; }

echo "== T-l-0: sintaxe e modo de execucao =="
for f in l-lib.sh l0.sh l1.sh l0c.sh verbatim.sh; do
  bash -n "$L_DIR/$f" 2>/dev/null; ok "T-l-0 bash -n $f" "0" "$?"
done
bash -n "$AQUI/l-gate.test.sh" 2>/dev/null; ok "T-l-0 bash -n l-gate.test.sh" "0" "$?"
if [[ "$L_DIR" == "$AQUI" ]] && git -C "$AQUI" rev-parse --git-dir >/dev/null 2>&1; then
  for f in l0.sh l1.sh l0c.sh verbatim.sh l-gate.test.sh; do
    m="$(git -C "$AQUI" ls-files -s -- "$f" | cut -d' ' -f1)"
    [[ -n "$m" ]] && ok "T-l-0 modo git 100755 em $f" "100755" "$m" || echo "SKIP: $f ainda nao esta no indice (modo nao verificavel)"
  done
fi
ok "T-l-0 nenhum CR nos scripts" "0" "$(cat "$L_DIR"/{l-lib,l0,l1,l0c,verbatim}.sh | tr -cd '\r' | wc -c | tr -d ' ')"

echo "== T-l-1: verbatim (diff do doc x arquivos) e mutacoes da propria prova =="
SAIDA="$(bash "$VERB" verificar "$DOC" 2>&1)"; RC=$?
ok "T-l-1 verificar rc 0" "0" "$RC"
contem "T-l-1 l-lib identico" "IDENTICO l-lib.sh" "$SAIDA"; contem "T-l-1 l0 identico" "IDENTICO l0.sh" "$SAIDA"; contem "T-l-1 l1 identico" "IDENTICO l1.sh" "$SAIDA"
# a prova tem de MORDER: arquivo adulterado, doc em CRLF (aceito), bloco ausente (rejeitado)
V="$TMP/verb"; mkdir -p "$V"; cp "$L_DIR"/{l-lib,l0,l1,verbatim}.sh "$V/"
sed -i 's/+ 90 ))s/+ 60 ))s/' "$V/l-lib.sh"
SAIDA="$(bash "$V/verbatim.sh" verificar "$DOC" 2>&1)"; RC=$?
ok "T-l-1m l-lib adulterado (90->60): verificar falha" "1" "$RC"; contem "T-l-1m aponta DIFERE l-lib.sh" "DIFERE   l-lib.sh" "$SAIDA"
cp "$L_DIR/l-lib.sh" "$V/"; sed -i '/qdisc del dev eth0 root/d' "$V/l0.sh"
SAIDA="$(bash "$V/verbatim.sh" verificar "$DOC" 2>&1)"; RC=$?
ok "T-l-1m l0 sem o tc del: verificar falha" "1" "$RC"; contem "T-l-1m aponta DIFERE l0.sh" "DIFERE   l0.sh" "$SAIDA"
cp "$L_DIR/l0.sh" "$V/"; echo "# extra" >> "$V/l1.sh"
SAIDA="$(bash "$V/verbatim.sh" verificar "$DOC" 2>&1)"; RC=$?
ok "T-l-1m l1 com linha extra: verificar falha" "1" "$RC"
cp "$L_DIR/l1.sh" "$V/"; sed 's/$/\r/' "$DOC" > "$V/doc-crlf.md"
SAIDA="$(bash "$V/verbatim.sh" verificar "$V/doc-crlf.md" 2>&1)"; RC=$?
ok "T-l-1 doc em CRLF (checkout Windows): ainda identico" "0" "$RC"
sed 's/^### L-0:.*/### Outro titulo/' "$DOC" > "$V/doc-sem-l0.md"
SAIDA="$(bash "$V/verbatim.sh" verificar "$V/doc-sem-l0.md" 2>&1)"; RC=$?
ok "T-l-1m doc sem o titulo de L-0: verificar falha" "1" "$RC"; contem "T-l-1m FALHA extracao" "FALHA extracao: l0.sh" "$SAIDA"

echo "== T-l-2: sem terminal / sem GO: nenhuma chamada a rede =="
for S in "$L0" "$L1" "$L0C"; do
  b=$(basename "$S")
  reset_shims_v4; roda_notty "$S"
  ok "T-l-2 $b sem terminal: rc 1" "1" "$RC"; contem "T-l-2 $b mensagem sem terminal" "sem terminal" "$SAIDA"
  ok "T-l-2 $b sem terminal: 0 chamadas a podman/tc/nsenter/systemd-run" "0" "$(chamadas_perigosas)"
  reset_shims_v4; roda_tty "NAO" "$S"
  ok "T-l-2 $b resposta != GO: rc 1" "1" "$RC"; contem "T-l-2 $b mensagem sem go" "sem go" "$SAIDA"
  ok "T-l-2 $b resposta != GO: 0 chamadas a podman/tc/nsenter/systemd-run" "0" "$(chamadas_perigosas)"
  ok "T-l-2 $b sem go.log" "nao" "$([ -s "$RL/go.log" ] && echo sim || echo nao)"
done

echo "== T-l-3: guarda de netns aborta ANTES de armar/tc (l0, l1, l0c) =="
for S in "$L0" "$L1" "$L0C"; do
  b=$(basename "$S")
  for cen in "SHIM_HOSTNET=isn3" "SHIM_NO_NS=isn3" "SHIM_MISSING=isn3"; do
    reset_shims_v4; export "$cen"; roda_tty "GO" "$S"; unset SHIM_HOSTNET SHIM_NO_NS SHIM_MISSING
    ok "T-l-3 $b $cen: rc 1" "1" "$RC"
    ok "T-l-3 $b $cen: nenhum armar/tc/nsenter/injetor" "0" "$(nenhuma_rede)"
    ok "T-l-3 $b $cen: nenhum timer" "0" "$(estado_timers)"
  done
done
reset_shims_v4; export SHIM_HOSTNET="isn3"; roda_tty "GO" "$L0"; unset SHIM_HOSTNET
contem "T-l-3 mensagem da guarda" "ABORTAR: PID invalido ou netns do host" "$SAIDA"

echo "== T-l-4: l0.sh caminho feliz =="
reset_shims_v4; roda_tty "GO" "$L0"
ok "T-l-4 rc 0" "0" "$RC"; contem "T-l-4 L-0 concluido" "L-0 concluido sem residuo" "$SAIDA"
ordem "T-l-4 ordem: dead-man armado ANTES do primeiro tc add; stop so apos o del" "$SHIM_LOG" \
  "systemd-run" "tc qdisc add dev eth0 root handle 1: prio" "tc qdisc add dev eth0 parent 1:3 handle 30: netem" \
  "tc qdisc change dev eth0 parent 1:3 handle 30: netem loss gemodel" "tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32" \
  "tc qdisc del dev eth0 root" "systemctl stop" "INJ netdegrade --target isn3 --severity N3 --direction both --dry-run"
ok "T-l-4 dead-man de 210 s (120+90)" "1" "$(grep -c -- '--on-active=210s' "$SHIM_LOG")"
ok "T-l-4 todo tc passa por nsenter -t 4003 (isn3) e por nenhum outro pid" "0" "$(grep '^nsenter' "$SHIM_LOG" | grep -vc 'nsenter -t 4003 -n')"
ok "T-l-4 0 timers ao fim; sem prio residual" "0" "$(estado_timers)"
ok "T-l-4 sem prio residual" "0" "$(ls "$SHIM_STATE"/prio.* 2>/dev/null | wc -l | tr -d ' ')"
contem "T-l-4 go.log registrou L-0" "bloco=L-0" "$(cat "$RL/go.log" 2>/dev/null)"
nao_contem "T-l-4 sem variavel nao definida" "unbound variable" "$SAIDA"

echo "== T-l-5: l0.sh com o kernel recusando um parametro: aborta, dead-man FICA armado =="
for trecho in "gemodel" "reorder 25% 50%" "u32" "netem loss 20%"; do
  reset_shims_v4; export SHIM_TC_FAIL="$trecho"; roda_tty "GO" "$L0"; unset SHIM_TC_FAIL
  ok "T-l-5 recusa de '$trecho': rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
  ok "T-l-5 '$trecho': dead-man continua armado (1 timer)" "1" "$(estado_timers)"
  ok "T-l-5 '$trecho': nenhum systemctl stop" "0" "$(conta 'systemctl stop' "$SHIM_LOG")"
  nao_contem "T-l-5 '$trecho': nao imprime conclusao" "L-0 concluido sem residuo" "$SAIDA"
done
reset_shims_v4; export SHIM_TC_FAIL="gemodel"; roda_tty "GO" "$L0"; unset SHIM_TC_FAIL
ok "T-l-5 recusa de gemodel: a prio ficou (residuo real do abort; o dead-man e quem limpa)" "1" "$(ls "$SHIM_STATE"/prio.* 2>/dev/null | wc -l | tr -d ' ')"

echo "== T-l-6: l1.sh =="
reset_shims_v4; export STUB_DUR=1; roda_tty "GO" "$L1"; unset STUB_DUR
ok "T-l-6 caminho feliz: rc 0" "0" "$RC"
ordem "T-l-6 ordem: dead-man, injetor, stop do dead-man, (sem_prio) tc qdisc show" "$SHIM_LOG" \
  "systemd-run" "INJ netdegrade --target isn3 --loss 20 --duration 60 --ping-count 300 --marker runs/gate-netdegrade/marcadores.txt" "systemctl stop" "tc qdisc show"
ok "T-l-6 dead-man de 150 s (60+90)" "1" "$(grep -c -- '--on-active=150s' "$SHIM_LOG")"
ok "T-l-6 0 timers ao fim" "0" "$(estado_timers)"
ok "T-l-6 marcadores start+end" "2" "$(grep -c 'event=' "$RL/marcadores.txt")"
contem "T-l-6 sem_prio isn3 ok" "ok sem prio em isn3" "$SAIDA"
contem "T-l-6 L1.log com rc" "rc=0 (esperado 0)" "$(cat "$RL/L1.log")"
reset_shims_v4; export STUB_DUR=1 STUB_RC=6; roda_tty "GO" "$L1"; unset STUB_DUR STUB_RC
ok "T-l-6 injetor rc 6 != 0: rc 1" "1" "$RC"; contem "T-l-6 ABORTAR rc" "ABORTAR: rc=6 != 0" "$SAIDA"
ok "T-l-6 injetor rc 6: dead-man continua armado" "1" "$(estado_timers)"; ok "T-l-6 injetor rc 6: nenhum stop" "0" "$(conta 'systemctl stop' "$SHIM_LOG")"
reset_shims_v4; export STUB_DUR=1 STUB_NO_CLEAN=1; roda_tty "GO" "$L1"; unset STUB_DUR STUB_NO_CLEAN
ok "T-l-6 L-2 pega residuo de prio: rc 1" "1" "$RC"; contem "T-l-6 RESIDUO em isn3" "RESIDUO em isn3" "$SAIDA"

echo "== T-l-7: l0c.sh --dry-run =="
reset_shims_v4; PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$L0C" --dry-run > "$TMP/saida.txt" 2>&1 </dev/null; RC=$?; SAIDA="$(cat "$TMP/saida.txt")"
ok "T-l-7 rc 0" "0" "$RC"; contem "T-l-7 plano" "L-0c (dry-run; nada executado)" "$SAIDA"
contem "T-l-7 prazo derivado do armar verbatim (30-90 = -60)" "dur=-60 => on-active=30s" "$SAIDA"
ok "T-l-7 nenhuma chamada" "0" "$(wc -l < "$SHIM_LOG" | tr -d ' ')"

echo "== T-l-8: l0c.sh caminho feliz (prazo 3 s) =="
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=9 L0C_LIMITE=20; roda_tty "GO" "$L0C"
RES="$(cat "$RL/l0c/resultado.txt" 2>/dev/null)"
ok "T-l-8 rc 0" "0" "$RC"; contem "T-l-8 concluido" "L-0c concluido" "$SAIDA"; nao_contem "T-l-8 sem FAIL" "FAIL" "$RES"
contem "T-l-8 unit executa --heal" "PASS unit armada executa inject-netdegrade.sh --heal" "$RES"
contem "T-l-8 prio instalada" "PASS prio/netem presentes em isn3" "$RES"
contem "T-l-8 disparo >= minimo" "PASS disparo nao antes de 3 s" "$RES"; contem "T-l-8 disparo <= maximo" "PASS disparo ate 9 s" "$RES"
contem "T-l-8 journal: heal executado" "PASS journal: a unit executou o --heal" "$RES"
contem "T-l-8 journal: sucesso" "PASS journal: unit encerrou com sucesso" "$RES"
contem "T-l-8 qdisc identico" "PASS tc qdisc show de isn3 identico" "$RES"
contem "T-l-8 0 timers sem desarmar" "PASS 0 timers tcc-heal ao fim (sem desarmar)" "$RES"
ordem "T-l-8 ordem: dead-man ANTES de qualquer tc add; heal pela unit; nenhum stop" "$SHIM_LOG" \
  "systemd-run" "tc qdisc add dev eth0 root handle 1: prio" "tc qdisc add dev eth0 parent 1:3 handle 30: netem delay 1ms" \
  "tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32" "INJ netdegrade --heal" "tc qdisc del dev eth0 root"
ok "T-l-8 on-active = 3 s (3 - 90 + 90)" "1" "$(grep -c -- '--on-active=3s' "$SHIM_LOG")"
ok "T-l-8 NUNCA desarma (0 systemctl stop)" "0" "$(conta 'systemctl stop' "$SHIM_LOG")"
ok "T-l-8 --heal chamado UMA vez (pela unit; sem heal manual do abort)" "1" "$(conta 'INJ netdegrade-heal' "$SHIM_LOG")"
ok "T-l-8 nenhum nsenter fora de isn1/isn2/isn3 (4001..4003)" "0" "$(grep '^nsenter' "$SHIM_LOG" | grep -vcE 'nsenter -t 400[123] -n')"
ok "T-l-8 filtro so para 192.0.2.1 (TEST-NET)" "0" "$(grep 'tc filter add' "$SHIM_LOG" | grep -vc '192.0.2.1/32')"
ok "T-l-8 tempos gravados" "1" "$(grep -c 't_limpeza_ns=' "$RL/l0c/tempos.txt")"
contem "T-l-8 unit-armada registra AccuracySec" "AccuracySec=" "$(cat "$RL/l0c/unit-armada.txt")"
contem "T-l-8 unit-armada tem o corpo --heal" "inject-netdegrade.sh --heal" "$(cat "$RL/l0c/unit-armada.txt")"
contem "T-l-8 timer com AccuracySec=1s (sem isso o disparo atrasa ate 1 min)" "AccuracySec=1s" "$(grep systemd-run "$SHIM_LOG")"
contem "T-l-8 unit-armada mostra AccuracySec=1s" "AccuracySec=1s" "$(cat "$RL/l0c/unit-armada.txt")"
# o armar redefinido em l0c.sh e o de l-lib.sh + SO a propriedade (corpo da unit identico)
norm() { awk '/^armar\(\)/,/--heal; }/' "$1" | tr -d '\\n' | tr -s ' ' | sed 's/^armar() *//; s/ --timer-property=AccuracySec=1s//'; }
ok "T-l-8 armar de l0c = armar de l-lib + AccuracySec" "$(norm "$LLIB")" "$(norm "$L0C")"
contem "T-l-8 go.log registrou L-0c" "bloco=L-0c" "$(cat "$RL/go.log")"

echo "== T-l-8b: L0C_ACC=0 usa o armar verbatim (sem --timer-property) =="
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=9 L0C_LIMITE=20 L0C_ACC=0; roda_tty "GO" "$L0C"; unset L0C_ACC
ok "T-l-8b rc 0" "0" "$RC"; nao_contem "T-l-8b sem timer-property" "timer-property" "$(grep systemd-run "$SHIM_LOG")"
contem "T-l-8b AccuracySec default 1min registrado" "AccuracySec=1min" "$(cat "$RL/l0c/unit-armada.txt")"

echo "== T-l-9: l0c.sh mutantes de comportamento do dead-man =="
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=9 L0C_LIMITE=7 SHIM_DM_NOFIRE=1; roda_tty "GO" "$L0C"
RES="$(cat "$RL/l0c/resultado.txt" 2>/dev/null)"
ok "T-l-9a dead-man que NAO dispara: rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-l-9a FAIL nao disparou" "FAIL dead-man NAO disparou" "$RES"
ok "T-l-9a rede de seguranca: --heal manual chamado" "1" "$(conta 'INJ netdegrade-heal' "$SHIM_LOG")"
ok "T-l-9a ficou sem prio apos o abort" "0" "$(ls "$SHIM_STATE"/prio.* 2>/dev/null | wc -l | tr -d ' ')"
contem "T-l-9a registrou ABORTADO" "ABORTADO" "$RES"
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=5 L0C_LIMITE=20 SHIM_DM_ATRASO=6; roda_tty "GO" "$L0C"
RES="$(cat "$RL/l0c/resultado.txt" 2>/dev/null)"
ok "T-l-9b disparo tardio (3+6 s > 5): rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"; contem "T-l-9b FAIL DEPOIS" "FAIL disparo DEPOIS de 5 s" "$RES"
# 9c: o shim dispara 1 s apos armar e a janela exige >= MIN=12 s. A MARGEM (12 s contra ~3,4 s medidos no Git Bash: setup ~1,3 s + --heal ~2 s)
# e o que torna o teste independente do relogio: l0c mede quando a SONDA ve a prio sumir, e no Git Bash isso nunca ocorre antes de ~3 s
# (com MIN=3 o overhead de fork do shim "atrasava" o disparo de 1 s para >= 3 s e o mutante passava).
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=12 L0C_MAX=20 L0C_LIMITE=20 SHIM_DM_PRAZO=1; roda_tty "GO" "$L0C"
RES="$(cat "$RL/l0c/resultado.txt" 2>/dev/null)"
ok "T-l-9c disparo antecipado (1 s < 12): rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"; contem "T-l-9c FAIL ANTES" "FAIL disparo ANTES de 12 s" "$RES"
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=9 L0C_LIMITE=20 STUB_HEAL_SILENT=1; roda_tty "GO" "$L0C"
RES="$(cat "$RL/l0c/resultado.txt" 2>/dev/null)"
ok "T-l-9h unit que limpa mas nao executa o --heal do injetor (sem a mensagem): rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-l-9h FAIL journal sem Heal" "FAIL journal sem 'Heal: removendo'" "$RES"
unset STUB_HEAL_SILENT
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=9 L0C_LIMITE=20 STUB_HEAL_RC=1; roda_tty "GO" "$L0C"
RES="$(cat "$RL/l0c/resultado.txt" 2>/dev/null)"
ok "T-l-9d unit que limpa mas termina com rc != 0: rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"
contem "T-l-9d FAIL por falha da unit" "FAIL journal" "$RES"
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=9 L0C_LIMITE=7 STUB_HEAL_NOREMOVE=1; roda_tty "GO" "$L0C"
RES="$(cat "$RL/l0c/resultado.txt" 2>/dev/null)"
ok "T-l-9e heal que roda mas nao remove a prio: rc != 0" "1" "$([ "$RC" != 0 ] && echo 1 || echo 0)"; contem "T-l-9e FAIL nao disparou/limpou" "FAIL dead-man NAO disparou" "$RES"
reset_shims_v4; export L0C_PRAZO=3 L0C_MIN=3 L0C_MAX=9 L0C_LIMITE=20 SHIM_TC_FAIL="delay 1ms"; roda_tty "GO" "$L0C"
ok "T-l-9f tc recusado depois de armar: rc != 0 e heal de seguranca" "1" "$([ "$RC" != 0 ] && [ "$(conta 'INJ netdegrade-heal' "$SHIM_LOG")" -ge 1 ] && echo 1 || echo 0)"
reset_shims_v4; mkdir -p "$SHIM_STATE"; : > "$SHIM_STATE/prio.4001"; export L0C_PRAZO=3; roda_tty "GO" "$L0C"
ok "T-l-9g residuo previo em isn1: aborta antes de armar" "1" "$RC"; ok "T-l-9g residuo previo: nenhum systemd-run" "0" "$(conta 'systemd-run' "$SHIM_LOG")"
ok "T-l-9g residuo previo: pre-check nao aciona heal (nao mexe no que nao e seu)" "0" "$(conta 'INJ netdegrade-heal' "$SHIM_LOG")"
unset L0C_PRAZO L0C_MIN L0C_MAX L0C_LIMITE SHIM_DM_NOFIRE SHIM_DM_ATRASO SHIM_DM_PRAZO STUB_HEAL_RC STUB_HEAL_NOREMOVE SHIM_TC_FAIL

matar_timers
resumo_final
