#!/bin/bash
# Supervisor da noite 2a do rebaseline (coordenador): fase noite2, i = 60..95, com parada planejada no portao K1 da
# i = 96 (O-6 de validacao-noite1-2026-11-05.md §10.11; DT-014 item 1). A noite 2b e a retomada da mesma fase noite2.
# Base: ~/rebaseline-infra/supervisor-n1s2b.sh (c8f9e5b2...), com as decisoes do Gestor de 2026-11-05 que continuam:
#  - "pode retomar, diminua o timeout para 600s": workload com mais de 600 s e encerrado (thread dump e kill); a PARADA
#    (c) por falta de dados dessa execucao vira recusa (mesma regra da G-2 opcao A) e a fase e retomada;
#  - G-3(b) estendida / C-10: swap usado >= 1 GiB no portao K1 de qualquer execucao -> Ctrl-C no K1 (DT-014 item 1),
#    recriar o cluster e retomar.
# Novo na noite 2a:
#  - parada planejada: no portao K1 da i = 96, Ctrl-C e NAO relancar; o fim com exit 130 grava FIM-N2A-PARADA-OK;
#  - h4 (C3 §4.3): com ~/rebaseline-infra/<id>/PARADA-H4 gravado pelo vigia-h4.sh, Ctrl-C no portao K1 seguinte e NAO
#    relancar (ALERTA-H4-N2A); a retomada pede decisao do Gestor.
# Qualquer outro fim (FIM exit != 0, ou exit 0) grava ALERTA-SUPERVISOR-N2A e para, sem retomar. Nunca usa pkill; nao
# mata a sessao rebaseline. Uso (numa janela tmux da sessao rebaseline): bash supervisor-n2a.sh <id do amostrador>
I=~/rebaseline-infra; RB=~/rb/rb-20805b0; S=$RB/runs/rebaseline; L=$S/noite2/runner.log
LOG=$I/supervisor-n2a.log; WD=600; LIM=$((1024*1024)); PARADA_I=96; ID=${1:?id do amostrador}
log(){ echo "$(date -u +%FT%TZ) $*" >> "$LOG"; }
mortos="|"; ultimo_k1=""; marco=$(date +%s); parada=""   # so considera FIM gravado depois do marco

retomar(){
  local motivo="$1" t ts CA n
  for t in $(seq 1 200); do tmux capture-pane -p -S -8 -t rebaseline:0 | grep -q '^RUNNER_RC=' && break; sleep 3; done
  ts=$(date -u +%Y%m%dT%H%M%SZ)
  for n in isn1 isn2 isn3; do sudo podman logs "$n" > "$I/logs-pre-recriacao-$ts-$n.txt" 2>&1; done
  CA=$(date -u +%FT%TZ); echo "cluster_criado_apos=$CA" > "$I/cluster-criado-apos-$ts.txt"; sleep 1
  flock -n /var/lock/tcc-vm.lock bash "$I/recriar-rb-20805b0.sh" "$I/recriacao-$ts" >> "$LOG" 2>&1
  if ! grep -q 'ESTAVEL size=3' "$I/recriacao-$ts/validacao.txt" 2>/dev/null; then
    log "RECRIACAO FALHOU ($motivo): supervisor para"; touch "$I/ALERTA-SUPERVISOR-N2A"; exit 1; fi
  log "recriado ($motivo) CRIADO_APOS=$CA swap_MB=$(free -m | awk '/^Swap/{print $3}')"
  tmux kill-session -t goloop-n2a 2>/dev/null
  tmux send-keys -t rebaseline:0 "clear" Enter; sleep 1; tmux clear-history -t rebaseline:0
  tmux new -d -s goloop-n2a "bash $I/goloop-n2.sh /run/tccdiag/goloop-n2a-$ts.log; sleep 86400"
  sleep 1
  tmux send-keys -t rebaseline:0 "export CRIADO_APOS=$CA; bash scripts/run-rebaseline.sh --matriz \"\$MATRIZ\" --matriz-sha256 \"\$MATRIZ_SHA\" --jar \"\$JAR\" --jar-sha256 \"\$JAR_SHA\" --fase noite2 --cluster-criado-apos \"\$CRIADO_APOS\" --saida \"\$SAIDA\"; echo \"RUNNER_RC=\$? \$(date -u +%FT%TZ)\"" Enter
  log "runner relancado ($motivo)"
  ultimo_k1=""; marco=$(date +%s)
}

log "supervisor iniciado WD=${WD}s LIM_swap_kB=$LIM PARADA_I=$PARADA_I id=$ID"
for t in $(seq 1 40000); do
  linha=$(tail -n 1 "$L" 2>/dev/null)
  # 1) watchdog do workload
  P=$(pgrep -f "java -jar $RB/workload/target/session-workload" | head -1)
  if [ -n "$P" ]; then
    e=$(ps -o etimes= -p "$P" 2>/dev/null | tr -d ' ')
    if [ -n "$e" ] && [ "$e" -gt "$WD" ]; then
      i=$(grep -o 'exec i=[0-9]*: lancando workload' "$L" | tail -1 | grep -o '[0-9]*')
      log "WATCHDOG i=$i pid=$P etimes=${e}s > ${WD}s: thread dump e kill"
      kill -3 "$P" 2>/dev/null; sleep 3; kill -9 "$P" 2>/dev/null
      mortos="$mortos$i|"
    fi
  fi
  # 2) portao K1: parada planejada, parada h4 ou swap (nessa ordem)
  k=$(printf '%s' "$linha" | grep -o 'exec i=[0-9]* [^:]*: portao K1' | grep -o 'i=[0-9]*' | grep -o '[0-9]*')
  if [ -n "$k" ] && [ "$k" != "$ultimo_k1" ] && [ -z "$parada" ]; then
    ultimo_k1="$k"
    usado=$(awk '/^SwapTotal/{a=$2} /^SwapFree/{b=$2} END{print a-b}' /proc/meminfo)
    log "K1 i=$k swap_usado_kB=$usado"
    if [ "$k" -ge "$PARADA_I" ]; then
      tmux send-keys -t rebaseline:0 C-c; parada="planejada"
      log "PARADA PLANEJADA no K1 da i=$k (O-6, noite 2a): Ctrl-C, sem relancar"; continue
    fi
    if [ -f "$I/$ID/PARADA-H4" ]; then
      tmux send-keys -t rebaseline:0 C-c; parada="h4"
      log "PARADA-H4 do vigia: Ctrl-C no K1 da i=$k (C3 §4.3), sem relancar"; touch "$I/ALERTA-H4-N2A"; continue
    fi
    if [ "$usado" -ge "$LIM" ]; then
      tmux send-keys -t rebaseline:0 C-c
      log "SWAP >= 1 GiB no K1 da i=$k: Ctrl-C (G-3b, C-10)"; touch "$I/ALERTA-SWAP-N2A-i$k"
      retomar "swap i=$k"; continue
    fi
  fi
  # 3) fim do runner
  if printf '%s' "$linha" | grep -q 'run-rebaseline\.sh: FIM exit=' && [ "$(stat -c %Y "$L")" -gt "$marco" ]; then
    if [ "$parada" = planejada ] && printf '%s' "$linha" | grep -q 'FIM exit=130 '; then
      log "FIM da noite 2a pela parada planejada: $linha"; touch "$I/FIM-N2A-PARADA-OK"; break; fi
    if [ "$parada" = h4 ]; then log "FIM depois da PARADA-H4: $linha"; break; fi
    pi=$(printf '%s' "$linha" | grep -o 'checar PARADA em i=[0-9]*' | grep -o '[0-9]*$')
    if printf '%s' "$linha" | grep -q 'FIM exit=10 ' && [ -n "$pi" ] && printf '%s' "$mortos" | grep -q "|$pi|"; then
      d=$(ls -d "$S"/exec-$(printf '%04d' "$pi")-* | head -1)
      mv -n "$d/checagem.json" "$d/checagem-parada-k9-original.json"
      printf '{"utc":"%s","motivo":"workload encerrado pelo watchdog de %s s (decisao do Gestor 2026-11-05); PARADA (c) por K9 sem dados convertida em recusa (mesma regra da G-2 opcao A)"}\n' "$(date -u +%FT%T.%3NZ)" "$WD" > "$d/interrompida.json"
      log "PARADA da i=$pi por falta de dados apos watchdog: convertida em recusa; retomando"
      retomar "watchdog i=$pi"; continue
    fi
    log "FIM inesperado, supervisor para: $linha"; touch "$I/ALERTA-SUPERVISOR-N2A"; break
  fi
  sleep 3
done
log "supervisor sai"
