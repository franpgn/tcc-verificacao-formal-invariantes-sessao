#!/usr/bin/env bash
# T-A / A.3 e A.4: rodadas de controle do B-32 (1 worker, SEM SYMMETRY; cobertura por acao).
# Reproduz exatamente os comandos usados na auditoria. Saidas em runs/tlc/audit-b32/<ctrl|cov>-*/.
# Uso: scripts/run-controle-sem-simetria-b32.sh ETAPA     (ETAPA = violacoes | extras | fechamentos | cobertura | tudo)
# Requer TLA_TOOLS (jar fora do repo) e JAVA (JDK 17).
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN="$AQUI/../spec/run-tlc-propriedades.sh"
AUD="$AQUI/../runs/tlc/audit-b32"
ETAPA="${1:-tudo}"
export WORKERS=1
nosym()  { SEM_SIMETRIA=1 "$@"; }

etapa_violacoes() {   # A.3(a): as 8 propriedades violadas sob particao, limites do plano; sem simetria e (para I4Mp) com simetria
  OUTDIR="$AUD/ctrl-nosym-particao" TIMEOUT_SECS=900 nosym "$RUN" semeado particao r3 I1F I4F I2Fa I2Fb I1M I4M I4Fp I4Mp
  OUTDIR="$AUD/ctrl-sym-particao-w1" TIMEOUT_SECS=900 "$RUN" semeado particao r3 I4Mp
}
etapa_extras() {      # A.3(b): I2Fb, I4F, I4Fp em sem falha e queda (+ LIMITE=6 para o mecanismo (b) de I2Fb), e o modelo livre (P-26)
  for F in sem queda; do
    OUTDIR="$AUD/ctrl-nosym-$F" TIMEOUT_SECS=900 nosym "$RUN" semeado "$F" r3 I2Fb I4F I4Fp
  done
  for F in sem queda particao; do
    OUTDIR="$AUD/ctrl-nosym-lim6-$F" LIMITE=6 TIMEOUT_SECS=900 nosym "$RUN" semeado "$F" r3 I2Fb
  done
  for F in sem queda; do
    OUTDIR="$AUD/ctrl-nosym-livre-$F" TIMEOUT_SECS=900 nosym "$RUN" livre "$F" r3 I1P I1F
  done
  OUTDIR="$AUD/ctrl-nosym-livre-particao" TIMEOUT_SECS=900 nosym "$RUN" livre particao r3 I1P I1F I2Fa I1M MesclarPerdeChave
}
etapa_fechamentos() { # A.3(c): fechamentos exaustivos reprodutiveis sem simetria (tres jobs em paralelo, 1 worker cada)
  OUTDIR="$AUD/ctrl-nosym-queda-c2r3" MAXCANAL=2 MAXRELOGIO=3 TIMEOUT_SECS=3600 nosym "$RUN" semeado queda r2 I1F
  OUTDIR="$AUD/ctrl-nosym-sem-c2r3-I1P" MAXCANAL=2 MAXRELOGIO=3 TIMEOUT_SECS=14400 XMX=1500m nosym "$RUN" semeado sem r3 I1P &
  OUTDIR="$AUD/ctrl-nosym-sem-c2r3-I1F" MAXCANAL=2 MAXRELOGIO=3 TIMEOUT_SECS=14400 XMX=1500m nosym "$RUN" semeado sem r3 I1F &
  OUTDIR="$AUD/ctrl-nosym-sem-r2" TIMEOUT_SECS=14400 XMX=1500m nosym "$RUN" semeado sem r2 I1F &
  wait
}
etapa_cobertura() {   # A.4: configuracao reduzida que fecha (r2, MaxCanal=2, MaxRelogio=3); -coverage 1; contagem de Mesclar
  export MAXCANAL=2 MAXRELOGIO=3 COVERAGE=1
  for F in sem queda particao; do
    OUTDIR="$AUD/cov-$F-r2" TIMEOUT_SECS=900 "$RUN" semeado "$F" r2 I2Fa I1M I4M I4Mp
  done
}
case "$ETAPA" in
  violacoes) etapa_violacoes ;; extras) etapa_extras ;; fechamentos) etapa_fechamentos ;; cobertura) etapa_cobertura ;;
  tudo) etapa_violacoes; etapa_extras; etapa_cobertura; etapa_fechamentos ;;
  *) echo "ETAPA invalida" >&2; exit 2 ;;
esac
