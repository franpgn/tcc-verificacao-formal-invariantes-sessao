#!/usr/bin/env bash
# runs/gate-netdegrade/l0c.sh -- L-0c (S3 da sequencia do Senior): o dead-man do inject-netdegrade DISPARA de verdade?
# Lacuna que fecha: a R3 de V-B3 so provou o corpo inerte (container inexistente); L-0 desarmou o dead-man. Aqui ele NAO e
# desarmado: o systemd-run real executa `scripts/inject-netdegrade.sh --heal` e a prio some SEM intervencao.
# Sem impacto no cluster: prio + netem delay 1ms em isn3 com filtro u32 so para 192.0.2.1 (TEST-NET, nada do cluster casa).
# Usa as funcoes VERBATIM de l-lib.sh (mesmo `armar`, mesmo corpo da unit da injecao real). Como o `armar` do roteiro soma 90 s
# fixos, o prazo curto vem de uma duracao NEGATIVA: armar $(( PRAZO - 90 )) agenda --on-active=PRAZO s (so muda o prazo).
# Uso:  bash runs/gate-netdegrade/l0c.sh [--dry-run]      (--dry-run imprime o plano e sai sem tocar em nada)
# PRECISAO DO TIMER (achado de S0): o systemd-run cria timers com AccuracySec=1min; medido em WSL (systemd 249), um --on-active=30s
# disparou com +3 s, +3 s, +52 s e +41 s de atraso (coalescencia). Sem corrigir isso a janela [30,40] s nao e verificavel. Por isso
# este bloco redefine `armar` IGUAL ao de l-lib.sh, acrescentando so `--timer-property=AccuracySec=1s` (o corpo da unit nao muda).
# L0C_ACC=0 usa o `armar` verbatim de l-lib.sh (entao alargar a janela: L0C_MAX=100).
# Variaveis (testes com shims): L0C_PRAZO (30), L0C_MIN (30), L0C_MAX (40), L0C_LIMITE (90), L0C_ACC (1), TCC_DIR, LOCK_DIR.
set -euo pipefail
PRAZO="${L0C_PRAZO:-30}"; MIN="${L0C_MIN:-30}"; MAX="${L0C_MAX:-40}"; LIMITE="${L0C_LIMITE:-90}"
if [ "${1:-}" = "--dry-run" ]; then
  cat <<PLANO
L-0c (dry-run; nada executado): dead-man dispara sozinho em ${PRAZO} s; janela de validade [${MIN}, ${MAX}] s; limite de espera ${LIMITE} s
  0. exigir_go "L-0c"; pid_de isn3 (guarda de netns); sem prio/netem em isn1/isn2/isn3; tc qdisc show de isn3 (antes)
  1. armar (verbatim, dur=$(( PRAZO - 90 )) => on-active=${PRAZO}s) ANTES de qualquer tc; registrar systemctl cat da unit
  2. isn3: tc qdisc add root handle 1: prio; parent 1:3 netem delay 1ms; filter u32 match ip dst 192.0.2.1/32 flowid 1:3
  3. NAO desarmar. Sondar a cada 1 s ate a prio sumir (limite ${LIMITE} s); carimbar o instante
  4. conferir: journal da unit (heal executado, sem falha), tc qdisc show de isn3 identico ao de antes (sem intervencao),
     isn1/isn2 nunca tocados, 0 timers tcc-heal, delta armar->limpeza em [${MIN}, ${MAX}] s
  5. falha/estouro: --heal manual (rede de seguranca) e o bloco e registrado como FALHA do dead-man
PLANO
  exit 0
fi
source "$(dirname "$0")/l-lib.sh"
if [ "${L0C_ACC:-1}" = 1 ]; then
armar()    { DMS="tcc-heal-$$-$RANDOM"
             sudo systemd-run --quiet --unit="$DMS" --on-active="$(( $1 + 90 ))s" --timer-property=AccuracySec=1s \
               --setenv=PODMAN="sudo podman" --working-directory="$PWD" bash scripts/inject-netdegrade.sh --heal; }
fi
exigir_go "L-0c"
OUT="$LOG/l0c"; mkdir -p "$OUT"; R="$OUT/resultado.txt"; : > "$R"
PID=$(pid_de isn3)                 # a guarda ABORTA (exit 1) antes de qualquer armar/tc
T() { sudo nsenter -t "$PID" -n "$@"; }
ns() { date +%s%N; }
# captura antes do grep: `cmd | grep -q` com pipefail daria 141 (SIGPIPE) se o grep sair cedo
tem_prio() { local o; o=$(T tc qdisc show dev eth0); grep -Eq 'prio|netem' <<<"$o"; }
LIMPO=0
abortar() { local rc=$?
  [ "$LIMPO" = 1 ] && return 0
  echo "ABORTANDO (rc=$rc): --heal manual (rede de seguranca); o bloco conta como FALHA" >&2
  $INJ --heal >> "$OUT/abort-heal.log" 2>&1 || true
  echo "ABORTADO rc=$rc $(date -Is)" >> "$R"
}
ok()    { echo "PASS $*" | tee -a "$R"; }
falha() { echo "FAIL $*" | tee -a "$R" >&2; exit 1; }

# 0. estado limpo e baseline de isn3
for n in isn1 isn2 isn3; do sem_prio "$n"; done | tee "$OUT/pre-sem-prio.txt"
T tc qdisc show dev eth0 > "$OUT/qdisc-antes.txt"
cat "$OUT/qdisc-antes.txt"

# 1. dead-man PRIMEIRO (mesma funcao `armar` do roteiro; so o prazo e curto). O trap de seguranca so vale daqui em diante:
#    falha no pre-check (residuo, guarda) sai sem mexer em nada.
trap abortar EXIT
T0=$(ns)
armar $(( PRAZO - 90 ))
T1=$(ns)
UNIT="$DMS"
{ echo "unit=$UNIT t_antes_armar_ns=$T0 t_depois_armar_ns=$T1 prazo_s=$PRAZO"
  echo "--- systemctl cat ${UNIT}.service"; sudo systemctl cat "${UNIT}.service" 2>&1 || true
  echo "--- systemctl cat ${UNIT}.timer";   sudo systemctl cat "${UNIT}.timer" 2>&1 || true
  echo "--- AccuracyUSec (systemd 255: AccuracySec so na escrita; default 1min: o disparo pode atrasar ate esse valor)"
  sudo systemctl show "${UNIT}.timer" -p AccuracyUSec -p OnActiveSec -p NextElapseUSecMonotonic 2>&1 || true
  echo "--- list-timers"; systemctl list-timers --all 2>&1 | grep tcc-heal || echo "(nenhum)"; } > "$OUT/unit-armada.txt"
cat "$OUT/unit-armada.txt"
grep -q 'inject-netdegrade.sh --heal' "$OUT/unit-armada.txt" && ok "unit armada executa inject-netdegrade.sh --heal" \
  || falha "corpo da unit nao chama inject-netdegrade.sh --heal"

# 2. degradacao inerte: so o filtro para 192.0.2.1 (TEST-NET) casa; nada do cluster passa pela banda 1:3
T tc qdisc add dev eth0 root handle 1: prio bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
T tc qdisc add dev eth0 parent 1:3 handle 30: netem delay 1ms
T tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32 flowid 1:3
{ T tc qdisc show dev eth0; T tc filter show dev eth0; } > "$OUT/qdisc-durante.txt" 2>&1
tem_prio && ok "prio/netem presentes em isn3 (degradacao inerte instalada)" \
  || falha "prio/netem nao instalados (nada a limpar: teste invalido)"

# 3. NAO desarmar: sondar a cada 1 s ate a limpeza
T_LIMPEZA=
while :; do
  agora=$(ns)
  if ! tem_prio; then T_LIMPEZA=$agora; break; fi
  [ $(( (agora - T1) / 1000000000 )) -lt "$LIMITE" ] || break
  sleep 1
done
[ -n "$T_LIMPEZA" ] || falha "dead-man NAO disparou em ${LIMITE} s (prio ainda presente)"
sleep 2
D0=$(( (T_LIMPEZA - T0) / 1000000 )); D1=$(( (T_LIMPEZA - T1) / 1000000 ))
echo "t_limpeza_ns=$T_LIMPEZA delta_desde_antes_de_armar_ms=$D0 delta_desde_depois_de_armar_ms=$D1" | tee "$OUT/tempos.txt"
# o timer conta a partir da ativacao (entre T0 e T1); a sonda tem granularidade de 1 s (+ custo do tc)
[ "$D0" -ge $(( MIN * 1000 )) ] && ok "disparo nao antes de ${MIN} s (delta=${D0} ms desde antes de armar)" \
  || falha "disparo ANTES de ${MIN} s (delta=${D0} ms)"
[ "$D1" -le $(( MAX * 1000 )) ] && ok "disparo ate ${MAX} s (delta=${D1} ms desde depois de armar)" \
  || falha "disparo DEPOIS de ${MAX} s (delta=${D1} ms; ver AccuracyUSec em unit-armada.txt)"

# 4. evidencias do systemd e do estado final
{ sudo journalctl -u "${UNIT}.service" --no-pager -o short-iso 2>&1 || true; } > "$OUT/journal-unit.txt"
{ echo "--- is-failed: $(systemctl is-failed "${UNIT}.service" 2>&1 || true)"
  echo "--- list-timers"; systemctl list-timers --all 2>&1 | grep tcc-heal || echo "(nenhum)"; } > "$OUT/estado-systemd.txt"
cat "$OUT/journal-unit.txt" "$OUT/estado-systemd.txt"
grep -q 'Heal: removendo' "$OUT/journal-unit.txt" && ok "journal: a unit executou o --heal do injetor" \
  || falha "journal sem 'Heal: removendo' (a unit nao executou o heal?)"
grep -Eq 'Deactivated successfully|Succeeded' "$OUT/journal-unit.txt" && ok "journal: unit encerrou com sucesso" \
  || falha "journal sem 'Deactivated successfully'/'Succeeded'"
grep -q 'Failed with result' "$OUT/journal-unit.txt" && falha "journal registra falha da unit" || ok "journal sem 'Failed with result'"
grep -q '^--- is-failed: failed' "$OUT/estado-systemd.txt" && falha "unit em estado failed" || ok "unit nao esta failed"
grep -q '(nenhum)' "$OUT/estado-systemd.txt" && ok "0 timers tcc-heal ao fim (sem desarmar)" || falha "ha timer tcc-heal armado ao fim"
T tc qdisc show dev eth0 > "$OUT/qdisc-depois.txt"
diff "$OUT/qdisc-antes.txt" "$OUT/qdisc-depois.txt" > "$OUT/qdisc-diff.txt" && ok "tc qdisc show de isn3 identico ao de antes (cura sem intervencao)" \
  || falha "qdisc de isn3 difere do estado inicial ($(tr '\n' ';' < "$OUT/qdisc-diff.txt"))"
T tc filter show dev eth0 > "$OUT/filter-depois.txt" 2>&1 || true
for n in isn1 isn2 isn3; do sem_prio "$n"; done | tee "$OUT/pos-sem-prio.txt"
ok "isn1, isn2, isn3 sem prio/netem ao fim (antes e depois: isn1/isn2 sem alteracao observavel)"
LIMPO=1
echo "L-0c concluido: ver $R"
