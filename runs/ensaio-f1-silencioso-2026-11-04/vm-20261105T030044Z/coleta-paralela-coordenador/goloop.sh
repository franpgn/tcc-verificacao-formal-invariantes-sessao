#!/bin/bash
# Envia ao tmux "ensaio" o GO exato pedido pelo ensaio-f1.sh, uma vez por texto.
# Coordenador, sob autorizacao do Gestor de 2026-11-04. O GO n=1 ja foi aceito (go.log 03:02:03Z).
L=/run/tccdiag/goloop.log
feito="|GO F1 n=1 i=2000 carga=sem|"
for t in $(seq 1 2400); do
  p=$(tmux capture-pane -p -S -60 -t ensaio 2>/dev/null) || { echo "$(date -u +%T) sessao ensaio sumiu" >> "$L"; break; }
  g=$(printf '%s\n' "$p" | grep -o 'digite exatamente "GO F1 n=[0-9] [^"]*"' | tail -1 | sed 's/^digite exatamente "//; s/"$//')
  if [ -n "$g" ] && ! printf '%s' "$feito" | grep -qF "|$g|"; then
    tmux send-keys -t ensaio "$g" Enter
    feito="$feito|$g|"
    echo "$(date -u +%T) enviado: $g" >> "$L"
  fi
  if printf '%s\n' "$p" | grep -qE 'ENSAIO ABORTADO|Saia do tmux'; then
    echo "$(date -u +%T) FIM detectado" >> "$L"
    break
  fi
  sleep 5
done
