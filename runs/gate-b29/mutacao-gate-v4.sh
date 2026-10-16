#!/usr/bin/env bash
# runs/gate-b29/mutacao-gate-v4.sh -- checagem por MUTACAO das suites de l0c.sh / l-lib.sh / l0.sh / l1.sh e g5b.sh.
# Cada mutante altera UMA ocorrencia literal (awk index, sem regex) numa COPIA dos scripts e roda a suite inteira apontando
# L_DIR / G5B_DIR para a copia. MORTO = a suite falhou (exit != 0); SOBREVIVEU = a suite continuou verde (lacuna de teste).
# Nenhum arquivo do repositorio e alterado. Nao toca VM, rede nem privilegio.
# Uso:  bash runs/gate-b29/mutacao-gate-v4.sh l|g5b|todas [paralelismo=4]
#       (o mutante "baseline" nao altera nada e TEM de passar: valida a propria harness)
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ND="$AQUI/../gate-netdegrade"
ALVO="${1:-todas}"; PAR="${2:-4}"; shift 2 2>/dev/null || true; ONLY=" $* "   # ids opcionais apos o paralelismo
RES="$(mktemp -d)"; trap 'rm -rf "$RES"' EXIT
declare -a IDS FILES OLDS NEWS DESCS SUITES NTHS

mut() { # mut <suite> <id> <arquivo> <literal-antigo> <literal-novo> <descricao> [n-esima-linha-com-o-literal=1]
  SUITES+=("$1"); IDS+=("$2"); FILES+=("$3"); OLDS+=("$4"); NEWS+=("$5"); DESCS+=("$6"); NTHS+=("${7:-1}"); }
# aplica <arquivo> <antigo> <novo> <n>: troca o literal na n-esima LINHA que o contem. O literal tambem aparece em comentarios e no
# --dry-run (um mutante ali seria equivalente: sobrevive sem ser lacuna); o n faz o mutante cair no CODIGO executavel.
aplica() {
  OLD="$2" NEW="$3" N="$4" awk '{ if ((i = index($0, ENVIRON["OLD"])) && ++c == ENVIRON["N"]) { $0 = substr($0, 1, i-1) ENVIRON["NEW"] substr($0, i + length(ENVIRON["OLD"])) } print }' "$1" > "$1.mut" \
    && mv "$1.mut" "$1"; }

mut l baseline l0c.sh '' '' 'sem alteracao (a harness e valida se isto passar)'
# ---- l0c.sh
mut l L1 l0c.sh 'armar $(( PRAZO - 90 ))' ': #sem armar' 'dead-man nunca armado' 2
mut l L2 l0c.sh 'T1=$(ns)' 'T1=$(ns); desarmar' 'desarma logo apos armar'
mut l L3 l0c.sh '-ge $(( MIN * 1000 ))' '-ge 0' 'sem limite inferior do disparo'
mut l L4 l0c.sh '-le $(( MAX * 1000 ))' '-le 999999999999' 'sem limite superior do disparo'
mut l L5 l0c.sh "grep -q 'Heal: removendo' \"\$OUT/journal-unit.txt\"" 'true' 'nao exige o heal no journal'
mut l L6 l0c.sh 'trap abortar EXIT' ': #sem trap' 'sem rede de seguranca (trap)'
mut l L7 l0c.sh 'for n in isn1 isn2 isn3; do sem_prio "$n"; done | tee "$OUT/pre-sem-prio.txt"' ': #sem pre-check' 'sem pre-check de residuo'
mut l L8 l0c.sh '192.0.2.1/32 flowid' '10.89.0.1/32 flowid' 'filtro u32 casa um IP do cluster' 2
mut l L9 l0c.sh 'exigir_go "L-0c"' ': #sem go' 'sem exigir_go' 2
mut l L10 l0c.sh 'PID=$(pid_de isn3)' 'PID=$(sudo podman inspect -f "{{.State.Pid}}" isn3)' 'sem guarda de netns'
mut l L11 l0c.sh 'L0C_ACC:-1' 'L0C_ACC:-0' 'AccuracySec nao aplicado por padrao'
mut l L12 l0c.sh 'LIMPO=1' 'desarmar; LIMPO=1' 'desarma ao fim (apagaria a evidencia)'
mut l L13 l0c.sh 'if ! tem_prio; then T_LIMPEZA=$agora; break; fi' 'T_LIMPEZA=$agora; break' 'aceita limpeza sem esperar o dead-man'
# ---- verbatim: qualquer adulteracao dos tres blocos do roteiro
mut l V1 l0.sh 'armar 120' 'armar 12' 'l0: prazo do dead-man alterado'
mut l V2 l0.sh 'desarmar' ': #' 'l0: nunca desarma'
mut l V3 l1.sh 'rodar_inj 60 0' 'rodar_inj 60 1' 'l1: rc esperado trocado'
mut l V4 l-lib.sh '+ 90 ))s' '+ 30 ))s' 'l-lib: margem do dead-man'
mut l V5 l-lib.sh '[ -t 0 ] ||' 'true ||' 'l-lib: exigir_go aceita sem terminal'
mut l V6 l-lib.sh '[ "$nsn" != "$ns1" ]' 'true' 'l-lib: guarda aceita netns do host'
# ---- g5b.sh
mut g5b baseline g5b.sh '' '' 'sem alteracao (a harness e valida se isto passar)'
mut g5b G1 g5b.sh 'until split_confirmado; do' 'until true; do' 'nao exige split confirmado'
mut g5b G2 g5b.sh 'kill -KILL "$P" 2>/dev/null || echo "aviso: KILL' 'kill -TERM "$P" 2>/dev/null || echo "aviso: KILL' 'envia TERM em vez de KILL'
mut g5b G3 g5b.sh 'tem_clsact || falha "clsact SUMIU' 'tem_clsact || : "clsact SUMIU' 'nao verifica persistencia do corte'
mut g5b G5 g5b.sh 'carimbo t_heal; T_HEAL_S=$(date +%s)' 'desarmar; carimbo t_heal; T_HEAL_S=$(date +%s)' 'A: desarma antes da cura'
mut g5b G6 g5b.sh '-ge $(( PRAZO * 1000 ))' '-ge 0' 'B: sem limite inferior'
mut g5b G7 g5b.sh '-le $(( (PRAZO + TOL) * 1000 ))' '-le 999999999999' 'B: sem limite superior'
mut g5b G8 g5b.sh 'while [ $(( ($(ns) - T_START_N) / 1000000000 )) -lt "$T_KILL" ]; do sleep 0.5; done' ':' 'KILL sem esperar T_KILL'
mut g5b G9 g5b.sh 'trap abortar EXIT' ': #sem trap' 'sem rede de seguranca (trap)'
mut g5b G10 g5b.sh 'MARGEM="$MARGEM_B"' 'MARGEM="$MARGEM_A"' 'B usa a margem longa'
mut g5b G11 g5b.sh 'ACC:-1' 'ACC:-0' 'AccuracySec nao aplicado por padrao'
mut g5b G12 g5b.sh 'exigir_go "G-5b-' ': "G-5b-' 'sem exigir_go'
mut g5b G13 g5b.sh "MergeView::\\[[^]]*\\] \\(3\\)'" "MergeView::\\[[^]]*\\] \\([0-9]\\)'" 'MergeView de qualquer tamanho'
mut g5b G14 g5b.sh "grep -Eq 'ISPN000094.*\\] \\(2\\) \\[' <<<\"\$l1\" && grep -Eq 'ISPN000094.*\\] \\(2\\) \\[' <<<\"\$l2\" \\" "true \\" 'split sem exigir a vista de 2 em isn1/isn2'
mut g5b G15 g5b.sh 'sem_merge_desde "$KILL_ISO" || falha' 'true || falha' 'nao checa MergeView durante o corte'
mut g5b G16 g5b.sh 'espera_saudavel "$LIM" "$T_HEAL_S" "t_heal" | tee -a "$R" || falha' 'true || falha' 'A: nao espera HEALTHY'
mut g5b G19 g5b.sh 'espera_saudavel "$LIM" "$T_FIRE_S" "t_fire" | tee -a "$R" || falha' 'true || falha' 'B: nao espera HEALTHY'
mut g5b G17 g5b.sh 'grep -Eq '"'"'Deactivated successfully|Succeeded'"'"' "$OUT/journal-unit.txt" && ok "journal: unit encerrou com sucesso (rc 0)" || falha' 'true && ok "x" || falha' 'B: nao exige sucesso da unit'

roda_um() { # roda_um <indice>   (escreve $RES/<id>.res)
  local i="$1" id="${IDS[$i]}" suite="${SUITES[$i]}" d rc
  d="$(mktemp -d)"
  if [ "$suite" = l ]; then cp "$ND"/{l-lib,l0,l1,l0c,verbatim}.sh "$d/"
  else cp "$AQUI"/{g5b,g56-lib,g-lib}.sh "$d/"; fi
  if [ "${IDS[$i]}" != baseline ]; then
    cp "$d/${FILES[$i]}" "$d/orig"; aplica "$d/${FILES[$i]}" "${OLDS[$i]}" "${NEWS[$i]}" "${NTHS[$i]}"
    cmp -s "$d/orig" "$d/${FILES[$i]}" && { echo "NAO_APLICADO" > "$RES/$suite-$id.res"; rm -rf "$d"; return; }
    rm -f "$d/orig"
  fi
  if [ "$suite" = l ]; then L_DIR="$d" bash "$ND/l-gate.test.sh" > "$RES/$suite-$id.log" 2>&1; rc=$?
  else G5B_DIR="$d" bash "$AQUI/g5b.test.sh" > "$RES/$suite-$id.log" 2>&1; rc=$?; fi
  if [ "$id" = baseline ]; then [ "$rc" = 0 ] && echo "PASSOU" || echo "FALHOU"; else [ "$rc" != 0 ] && echo "MORTO" || echo "SOBREVIVEU"; fi > "$RES/$suite-$id.res"
  grep -m3 '^FAIL' "$RES/$suite-$id.log" | cut -c1-110 | tr '\n' '|' >> "$RES/$suite-$id.res"
  rm -rf "$d"
}

sel=(); for i in "${!IDS[@]}"; do [ "$ONLY" = "  " ] || [[ "$ONLY" == *" ${IDS[$i]} "* ]] || continue; case "$ALVO" in todas) sel+=("$i");; l) [ "${SUITES[$i]}" = l ] && sel+=("$i");; g5b) [ "${SUITES[$i]}" = g5b ] && sel+=("$i");; esac; done
n=0; for i in "${sel[@]}"; do
  roda_um "$i" &
  n=$((n+1)); if [ $((n % PAR)) = 0 ]; then wait; fi
done; wait
mortos=0; vivos=0; tot=0; baseline_ok=1
printf '%-6s %-9s %-12s %s\n' SUITE MUTANTE RESULTADO DESCRICAO
for i in "${sel[@]}"; do
  id="${IDS[$i]}"; s="${SUITES[$i]}"; r="$(head -1 "$RES/$s-$id.res" 2>/dev/null)"
  printf '%-6s %-9s %-11s %s\n' "$s" "$id" "${r%%|*}" "${DESCS[$i]}  [$(sed -n 2p "$RES/$s-$id.res" | cut -d'|' -f1 | cut -c1-90)]"
  case "$r" in
    MORTO*) mortos=$((mortos+1)); tot=$((tot+1));;
    SOBREVIVEU*) vivos=$((vivos+1)); tot=$((tot+1)); echo "       -> sobreviveu: nenhum teste detectou"; ;;
    PASSOU*) ;;
    *) baseline_ok=0; echo "       -> $r";;
  esac
done
echo "== mutantes: $tot, mortos: $mortos, sobreviventes: $vivos; baselines ok: $baseline_ok =="
[ "$vivos" = 0 ] && [ "$baseline_ok" = 1 ]
