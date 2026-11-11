#!/bin/bash
# Envia ao tmux "rebaseline" (janela 0) o GO exato pedido pelo run-rebaseline.sh da noite 2 (fase noite2), uma vez por
# texto. Copia do goloop-gen.sh da noite 1 (01a4ddbb...), mudando so a fase. Coordenador, sob a autorizacao do Gestor
# de 2026-11-06 (noite 2a). So 'go noite2 <AAAA-MM-DD>' e 'go f3 <i>'. Uso: goloop-n2.sh <log>
L=${1:-/run/tccdiag/goloop-n2a.log}
feito=""
for t in $(seq 1 10000); do
  p=$(tmux capture-pane -p -S -40 -t rebaseline:0 2>/dev/null) || { echo "$(date -u +%T) sessao sumiu" >> "$L"; break; }
  g=$(printf '%s\n' "$p" | grep -oE '^Digite exatamente: (go noite2 [0-9]{4}-[0-9]{2}-[0-9]{2}|go f3 [0-9]+)$' | tail -1 | sed 's/^Digite exatamente: //')
  if [ -n "$g" ] && ! printf '%s' "$feito" | grep -qF "|$g|"; then
    tmux send-keys -t rebaseline:0 "$g" Enter
    feito="$feito|$g|"
    echo "$(date -u +%T) enviado: $g" >> "$L"
  fi
  if printf '%s\n' "$p" | grep -qE 'run-rebaseline\.sh: FIM exit=|^RUNNER_RC='; then
    echo "$(date -u +%T) FIM detectado: $(printf '%s\n' "$p" | grep -E 'FIM exit=|^RUNNER_RC=' | tail -2 | tr '\n' ' ')" >> "$L"
    break
  fi
  sleep 3
done
