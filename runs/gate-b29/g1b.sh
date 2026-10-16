#!/usr/bin/env bash
# runs/gate-b29/g1b.sh -- coleta Hot Rod + saude/caches COMPLETOS durante a particao da REP=N do G-4.
#
# NAO INJETA NADA e NAO EXECUTA SEM O GO DO GESTOR. Roda num SEGUNDO terminal (tmux) da VM,
# enquanto o g1.sh (que pede o go e inicia o injetor) corre no primeiro:
#   terminal A:  REP=2 bash runs/gate-b29/g1.sh        (go do Gestor, injecao de 90 s em isn3)
#   terminal B:  REP=2 bash runs/gate-b29/g1b.sh       (este; pode ser iniciado antes ou depois do go)
# Espera o marcador 'event=start' em runs/gate-b29/g1-rep$REP/marcadores.txt, entao:
#   - roda a sonda SondaHotrod contra isn3 (127.0.0.1:11224) e isn1 (127.0.0.1:11222), uma JVM por no;
#   - a cada INTERVALO_S (5) s salva o JSON INTEGRAL de /rest/v2/cache-managers/default/health e
#     dos caches nas tres portas (curl --digest), mais um indice com o codigo HTTP e o tempo;
#   - para POS_FIM_S (75) s depois de 'event=end' (cobre a fusao, ~46-48 s) ou em MAX_S, o que vier antes.
# Saidas: runs/gate-b29/g1-rep$REP/hotrod/ (sonda-<no>.csv, sonda-<no>.err, saude-*.json, caches-*.json,
# stats-*.json, avail-*.json, indice.csv, resumo.txt).
# COLETA=minima (REP=3, V-B2 3.2): SO o /health nas tres portas (curl --digest) + as duas sondas; NAO pede /caches,
# ?action=stats nem ?action=get-availability (remove H2: carga REST extra sobre isn3 durante a particao). Padrao: COLETA=completa
# (estrato da REP=2). O resumo.txt registra coleta=<modo>.
# Variaveis: COLETA (completa|minima), REP (obrigatoria), TCC_DIR (~/tcc-gate), SONDA_JAR, INTERVALO_S, POS_FIM_S, MAX_S,
# ESPERA_START_S (600), SONDA_USUARIO/SONDA_SENHA (admin/infinispan, credencial do laboratorio).
set -uo pipefail

REP="${REP:?defina REP (ex.: REP=2); a pasta g1-rep\$REP deve ser a do g1.sh em curso}"
cd "${TCC_DIR:-$HOME/tcc-gate}" || { echo "ABORTAR: TCC_DIR invalido" >&2; exit 1; }
INTERVALO_S="${INTERVALO_S:-5}"
POS_FIM_S="${POS_FIM_S:-75}"
MAX_S="${MAX_S:-400}"
ESPERA_START_S="${ESPERA_START_S:-600}"
COLETA="${COLETA:-completa}"
case "$COLETA" in completa|minima) ;; *) echo "ABORTAR: COLETA='$COLETA' invalida (use completa ou minima)" >&2; exit 1;; esac
USUARIO="${SONDA_USUARIO:-admin}"
export SONDA_SENHA="${SONDA_SENHA:-infinispan}"
OUT="runs/gate-b29/g1-rep$REP"
MARC="$OUT/marcadores.txt"
HOT="$OUT/hotrod"
JAR="${SONDA_JAR:-$(ls workload/target/session-workload-*.jar 2>/dev/null | head -1)}"
BASE="http://localhost"

command -v java >/dev/null 2>&1 || { echo "ABORTAR: java nao encontrado na VM (precisa de JDK/JRE 17)" >&2; exit 1; }
[ -n "$JAR" ] && [ -f "$JAR" ] || { echo "ABORTAR: jar nao encontrado (SONDA_JAR ou workload/target/session-workload-*.jar)" >&2; exit 1; }
mkdir -p "$HOT"

# curl --digest com a credencial lida de stdin (-K -): nao aparece em 'ps'
curl_auth() { printf 'user = "%s:%s"\n' "$USUARIO" "$SONDA_SENHA" | curl -sS --digest -K - -m 4 "$@"; }

marca() { awk -F't_epoch_ns=' -v ev="event=$1" 'index($0, ev) {print $2}' "$MARC" 2>/dev/null | tail -1; }

# 1) espera o inicio da janela (marcador do injetor); se nao vier, aborta sem tocar em nada
t_ini=$(date +%s)
START_NS=""
while [ -z "$START_NS" ]; do
  START_NS=$(marca start)
  [ -n "$START_NS" ] && break
  [ $(( $(date +%s) - t_ini )) -lt "$ESPERA_START_S" ] || { echo "ABORTAR: sem event=start em $MARC apos ${ESPERA_START_S}s" >&2; exit 2; }
  sleep 1
done
echo "g1b: janela iniciada (start_ns=$START_NS); sondas e coleta ($COLETA) ligadas $(date -Is)"

# 2) sondas: uma JVM por no (cliente pinado em UM no cada)
PIDS=()
sonda() { # rotulo host:porta
  java -cp "$JAR" br.unipampa.tcc.session.SondaHotrod --no "$1=$2" --caches sessions,counters \
       --intervalo-s "$INTERVALO_S" --usuario "$USUARIO" > "$HOT/sonda-$1.csv" 2> "$HOT/sonda-$1.err" &
  PIDS+=("$!")
}
sonda isn3 127.0.0.1:11224
sonda isn1 127.0.0.1:11222

parar_sondas() {
  local p
  for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill -TERM "$p" 2>/dev/null; done
  for p in "${PIDS[@]:-}"; do [ -n "$p" ] && wait "$p" 2>/dev/null; done
}
trap 'parar_sondas' EXIT INT TERM

echo "t_epoch_ns,porta,recurso,http,tempo_s,arquivo" > "$HOT/indice.csv"

# 3) coleta por porta: salva o corpo integral e registra codigo HTTP/tempo (falha de curl nao aborta)
busca() { # porta recurso url arquivo
  local ts r
  ts=$(date +%s%N)
  r=$(curl_auth -o "$HOT/$4-$ts.json" -w '%{http_code},%{time_total}' "$3" 2>/dev/null) || r="000,0"
  # o arquivo leva o timestamp do inicio da coleta; o indice o aponta
  echo "$ts,$1,$2,${r%%,*},${r##*,},$4-$ts.json" >> "$HOT/indice.csv"
}
coleta_porta() {
  local p="$1" c
  busca "$p" health "$BASE:$p/rest/v2/cache-managers/default/health" "saude-$p"
  [ "$COLETA" = minima ] && return 0     # minima: so o /health acima
  busca "$p" caches "$BASE:$p/rest/v2/cache-managers/default/caches" "caches-$p"
  for c in sessions counters; do
    busca "$p" "stats-$c" "$BASE:$p/rest/v2/caches/$c?action=stats" "stats-$c-$p"
    busca "$p" "avail-$c" "$BASE:$p/rest/v2/caches/$c?action=get-availability" "avail-$c-$p"
  done
}

fim_visto=""; n=0
while :; do
  prox=$(( $(date +%s) + INTERVALO_S ))
  for p in 11222 11223 11224; do coleta_porta "$p" & done
  wait $(jobs -p | grep -v -F -x -f <(printf '%s\n' "${PIDS[@]}") 2>/dev/null) 2>/dev/null || true
  n=$((n+1))
  agora=$(date +%s)
  if [ -z "$fim_visto" ] && [ -n "$(marca end)" ]; then fim_visto=$agora; echo "g1b: event=end visto; mais ${POS_FIM_S}s de coleta"; fi
  [ -z "$fim_visto" ] || [ $(( agora - fim_visto )) -lt "$POS_FIM_S" ] || break
  [ $(( agora - t_ini )) -lt "$MAX_S" ] || { echo "g1b: MAX_S=$MAX_S atingido; encerrando" >&2; break; }
  while [ "$(date +%s)" -lt "$prox" ]; do sleep 1; done
done

# 4) encerra sondas (remove-final da chave de teste) e resume
parar_sondas
trap - EXIT INT TERM
{
  echo "rep=$REP"
  echo "coleta=$COLETA"
  echo "start_ns=$START_NS"
  echo "end_ns=$(marca end)"
  echo "amostras_de_saude=$n intervalo_s=$INTERVALO_S pos_fim_s=$POS_FIM_S"
  echo "jar=$JAR"
  echo "jar_sha256=$( (sha256sum "$JAR" 2>/dev/null || shasum -a 256 "$JAR") | awk '{print $1}')"
  echo "java=$(java -version 2>&1 | head -1)"
  for no in isn3 isn1; do
    f="$HOT/sonda-$no.csv"
    echo "sonda-$no: linhas=$(($(wc -l < "$f") - 1)) erros=$(grep -c ',ERRO,' "$f" || true)"
  done
} | tee "$HOT/resumo.txt"
echo "g1b: pronto. Copie $HOT para o host (ver docs/relatorio-sonda-hotrod-2026-10-16.md)."
