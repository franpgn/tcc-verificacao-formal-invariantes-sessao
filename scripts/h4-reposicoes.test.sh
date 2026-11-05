#!/usr/bin/env bash
#
# h4-reposicoes.test.sh
#
# Teste da sequencia de reposicoes do C3 §5 (prompt W-h4 §4.4) com o run-rebaseline.sh REAL e congelado, sem
# copia nem edicao: runner --fase reposicoes (repoe a recusa do codigo), h4.py repor (acrescenta as h4 ACEITAS),
# runner --fase reposicoes de novo (nao repete o i = 120, roda o 121 e o 122).
#
# Os shims sao os do scripts/run-rebaseline.test.sh, lidos dele em tempo de execucao (a funcao criar_shims e
# extraida com sed e avaliada; o arquivo nao e copiado nem editado). So o python3 de shim e trocado: o
# 'rebaseline.py checar' de shim usa o ler_matriz, o semente_e_rep e as constantes do rebaseline.py real para
# conferir o K7 (semente = matriz = S0 + 100*i), e o 'rebaseline.py estado' e o REAL.
#
# Uso: bash scripts/h4-reposicoes.test.sh   Exit 0 se todos passarem.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "${DIR}/.." && pwd)"
RUNNER="${DIR}/run-rebaseline.sh"
H4PY="${RAIZ}/analysis/h4.py"
REAL_PY="$(command -v python3)"
REAL_DATE="$(command -v date)"
REAL_SLEEP="$(command -v sleep)"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

PASS=0
FAIL=0
ok() {
    if [[ "$2" == "$3" ]]; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1)); fi
}
contem() {
    if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1)); fi
}

# ----------------------------------------------------------------------------- shims do runner (reuso)
# vai ate o '}' que fecha a funcao (logo depois do chmod); ha '}' na coluna 0 dentro dos heredocs dos shims
FUNC="$(awk '/^criar_shims\(\) \{/ {p = 1} p {print} p && /^}$/ && ant ~ /chmod \+x/ {exit} p {ant = $0}' "${DIR}/run-rebaseline.test.sh")"
[[ -n "${FUNC}" ]] || { echo "FAIL: criar_shims nao encontrada em run-rebaseline.test.sh"; exit 1; }
eval "${FUNC}"
agora_real_ms() { local r="${EPOCHREALTIME//[.,]/}"; echo "$((10#${r:0:13}))"; }

CASO="${TMP}/seq"; SH="${CASO}/shims"; ST="${CASO}/estado"; OUT="${CASO}/saida"
mkdir -p "${ST}" "${OUT}"
criar_shims "${SH}"
: > "${ST}/chamadas.log"
cat > "${CASO}/checar_shim.py" <<'PY'
import json, sys
from pathlib import Path
raiz, args = sys.argv[1], sys.argv[2:]
sys.path.insert(0, str(Path(raiz) / "analysis"))
import rebaseline as rb
d = Path(args[args.index("--exec") + 1])
mat = rb.ler_matriz([args[args.index("--matriz") + 1]])
ex = json.loads((d / "execucao.json").read_text(encoding="utf-8"))
i = int(ex["i"])
lin = mat[i]
out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
try:
    seed, rep = rb.semente_e_rep(rb.num(r"(Configuracao: .*)", out, tipo=str))
except ValueError:
    seed = rep = None
k7 = seed == int(lin["seed"]) == rb.S0 + rb.PASSO_SEMENTE * i and rep == rb.REP
ch = {"i": i, "celula": lin["celula"], "fase": lin["fase"], "noite": lin.get("noite"), "cenario": lin["cenario"],
      "modo": lin["modo"], "falha": lin["falha"], "seed": int(lin["seed"]), "seed_lido": seed, "rep_lido": rep,
      "veredito": "ACEITA" if k7 else "RECUSADA", "motivos": [] if k7 else ["K7 (recusa)"],
      "criterios": {"K7": {"ok": k7, "valor": {"seed_lido": seed, "seed_matriz": int(lin["seed"]),
                                               "seed_regra": rb.S0 + rb.PASSO_SEMENTE * i}}},
      "metricas": {"C3": {"ok": True, "k": 500, "n": 10000, "n_csv": 10000}},
      "shim": "h4-reposicoes.test.sh: so o K7 e avaliado (com o rebaseline.py real)"}
rb.gravar_json(d / "checagem.json", ch)
print("checar (shim K7) i=%d %s: %s" % (i, ch["celula"], ch["veredito"]))
sys.exit(0 if k7 else 10)
PY
cat > "${SH}/python3" <<EOF
#!/usr/bin/env bash
source "\${RB_SHIM_DIR}/_comum.sh"
if [[ "\$*" == *rebaseline.py\ checar* ]]; then
    registrar python3 "\$@"
    shift; shift   # -I <rebaseline.py>
    exec "${REAL_PY}" -I "${CASO}/checar_shim.py" "${RAIZ}" "\$@"
fi
if [[ "\$*" == *rebaseline.py\ estado* ]]; then registrar python3 "\$@"; fi
exec "${REAL_PY}" "\$@"
EOF
chmod +x "${SH}/python3"
JAR="${CASO}/workload.jar"; echo "jar falso" > "${JAR}"; JAR_SHA="$(sha256sum "${JAR}" | cut -d' ' -f1)"
export RB_SHIM_DIR="${SH}" RB_ESTADO="${ST}" RB_ESCALA=50 RB_T0_MS="$(agora_real_ms)"
export RB_MODO_TESTE=1 RB_TTY_ARQUIVO="${ST}/tty" RB_PASSWORD="segredo" PODMAN="sudo podman" JAVA="java"
HOJE="$("${REAL_DATE}" +%F)"
printf 'go reposicoes %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"

# ----------------------------------------------------------------------------- campanha sintetica (noite1)
A=S1-sync-none; B=S2-async-none; C=S2-sync-none
MAT="${CASO}/matriz.tsv"
printf 'i\tfase\tnoite\trodada\tposicao\tcelula\tcenario\tmodo\tfalha\tseed\n' > "${MAT}"
pos=0
for x in "1 ${A}" "2 ${A}" "3 ${B}" "4 ${C}"; do
    i="${x%% *}"; cel="${x#* }"; IFS=- read -r cen modo falha <<< "${cel}"
    printf '%s\tcampanha\tnoite1\t1\t%s\t%s\t%s\t%s\t%s\t%s\n' "${i}" "${pos}" "${cel}" "${cen}" "${modo}" "${falha}" "$((50000 + 100 * i))" >> "${MAT}"
    pos=$((pos + 1))
done
MAT_SHA="$(sha256sum "${MAT}" | cut -d' ' -f1)"
cp "${MAT}" "${OUT}/matriz.tsv"
"${REAL_PY}" -I - "${OUT}" "${CASO}/amostras.txt" "${RAIZ}/analysis" <<'PY'
import json, sys
from pathlib import Path
saida, arq = Path(sys.argv[1]), Path(sys.argv[2])
sys.path.insert(0, sys.argv[3])
import h4
base_ms = 1793840000000
execs = [(1, "S1-sync-none", "RECUSADA", False), (2, "S1-sync-none", "ACEITA", True),
         (3, "S2-async-none", "ACEITA", True), (4, "S2-sync-none", "ACEITA", False)]
degraus = {}
for k, (i, cel, v, e_h4) in enumerate(execs):
    anc = base_ms + k * 400000
    d = saida / ("exec-%04d-%s" % (i, cel))
    d.mkdir()
    fim = h4.utc((anc + 305000) * 1000)[:23] + "Z"
    (d / "execucao.json").write_text(json.dumps({"i": i, "celula": cel, "fase": "campanha", "falha": "none",
                                                 "lancamento_ms": anc - 12000, "ancora_ms": anc, "fim_utc": fim,
                                                 "falha_efetiva": None}), encoding="utf-8")
    (d / "checagem.json").write_text(json.dumps({
        "i": i, "celula": cel, "fase": "campanha", "noite": "noite1", "seed": 50000 + 100 * i,
        "seed_lido": 50000 + 100 * i, "rep_lido": 1, "veredito": v, "motivos": [] if v == "ACEITA" else ["K2 (sintetica)"],
        "metricas": {"C3": {"ok": True, "k": 500, "n": 10000, "n_csv": 10000}}}), encoding="utf-8")
    if e_h4:
        degraus[anc * 1000 + 100 * h4.US] = 2 * h4.US
t, fim_t, full, linhas = base_ms * 1000 - 30 * h4.US, base_ms * 1000 + 4 * 400 * h4.US, 0, []
while t <= fim_t:
    full += degraus.get(t, 0)
    linhas.append("%s %s io 1 %d cpu 1 1 mem 1 1 sda -\n" % (h4.seg(t), h4.seg(t - base_ms * 1000 + 500 * h4.US), full))
    t += h4.US
arq.write_text("# tcc-amostrador-infra v1 id=noite1-20261105T000000Z inicio=2026-11-05T00:00:00+00:00 boot_id=b "
               "host=vm disco=sda periodo_s=1 pid=1 versao_script_sha256=-\n" + "".join(linhas), encoding="ascii",
               newline="\n")
PY

rodar_runner() {
    SAIDA_TXT="$(PATH="${SH}:${PATH}" bash "${RUNNER}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" \
        --jar "${JAR}" --jar-sha256 "${JAR_SHA}" --fase reposicoes --saida "${OUT}" \
        --cluster-criado-apos 2026-10-23T00:00:00Z 2>&1)"; RC=$?
}
java_seed() { grep -c " java|-jar|.*|--seed|$1|" "${ST}/chamadas.log"; }
LOG="${OUT}/reposicoes/runner.log"

echo "== passo 1: h4.py fase noite1 marca i=2 (${A}) e i=3 (${B}) como h4 =="
OUT_H4="$("${REAL_PY}" -I "${H4PY}" fase --amostras "${CASO}/amostras.txt" --saida "${OUT}" --fase noite1 2>&1)"; RC=$?
ok "1 h4.py fase exit 0 (2 h4 em 4, sem parada)" "0" "${RC}"
ok "1 vereditos" "1:ok 2:h4a 3:h4a 4:ok" "$(awk -F'\t' 'NR > 1 {printf "%s%s:%s", (NR > 2 ? " " : ""), $1, $18}' "${OUT}/h4/h4-noite1.tsv")"

echo "== passo 2: runner --fase reposicoes repoe a recusa de ${A} (i = 120) =="
rodar_runner
ok "2 runner exit 0" "0" "${RC}"
contem "2 reposicao da recusa" "reposicao: ${A} (recusa i=1) -> i=120" "$(cat "${LOG}")"
ok "2 i=120 rodou com a semente 62000" "1" "$(java_seed 62000)"
ok "2 checar (K7 real) aceita i=120" "ACEITA" "$("${REAL_PY}" -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["veredito"])' "${OUT}/exec-0120-${A}/checagem.json")"

echo "== passo 3: h4.py repor acrescenta ${A} (i = 121) e ${B} (i = 122) =="
OUT_R="$("${REAL_PY}" -I "${H4PY}" repor --saida "${OUT}" --matriz "${MAT}" 2>&1)"; RC=$?
ok "3 repor exit 0" "0" "${RC}"
ok "3 reposicoes.tsv" "$(printf '120\treposicao\treposicoes\t-\t0\t%s\tS1\tsync\tnone\t62000\n121\treposicao\treposicoes\t-\t1\t%s\tS1\tsync\tnone\t62100\n122\treposicao\treposicoes\t-\t2\t%s\tS2\tasync\tnone\t62200' "${A}" "${A}" "${B}")" \
    "$(tail -n +2 "${OUT}/reposicoes/reposicoes.tsv")"
contem "3 SHA-256 do TSV impresso" "reposicoes.tsv SHA-256 $(sha256sum "${OUT}/reposicoes/reposicoes.tsv" | cut -d' ' -f1)" "${OUT_R}"

echo "== passo 4: runner --fase reposicoes de novo: pula o 120, roda o 121 e o 122 =="
rodar_runner
ok "4 runner exit 0" "0" "${RC}"
contem "4 i=120 pulado" "exec i=120 ${A}: ja executada e checada; pulando" "$(cat "${LOG}")"
ok "4 i=120 nao relancado" "1" "$(java_seed 62000)"
ok "4 i=121 rodou com a semente 62100" "1" "$(java_seed 62100)"
ok "4 i=122 rodou com a semente 62200" "1" "$(java_seed 62200)"
for x in "121 ${A}" "122 ${B}"; do
    i="${x%% *}"; cel="${x#* }"
    ok "4 checar (K7 real) aceita i=${i} (semente S0 + 100*i)" "ACEITA True $((50000 + 100 * i))" \
        "$("${REAL_PY}" -I -c 'import json,sys; c=json.load(open(sys.argv[1])); print(c["veredito"], c["criterios"]["K7"]["ok"], c["seed_lido"])' "${OUT}/exec-0${i}-${cel}/checagem.json")"
done
ok "4 o runner nao acrescentou linha ao reposicoes.tsv" "3" "$(tail -n +2 "${OUT}/reposicoes/reposicoes.tsv" | grep -c .)"

echo "== passo 5: nada duplicado; estado nao conta as h4 como recusa =="
ok "5 uma reposicao do runner no log inteiro" "1" "$(grep -c 'reposicao: .* -> i=' "${LOG}")"
for i in 0120 0121 0122; do
    ok "5 um diretorio exec-${i}-*" "1" "$(ls -d "${OUT}"/exec-"${i}"-* 2> /dev/null | grep -c .)"
done
ok "5 tres workloads de reposicao no total" "3" "$(grep -c ' java|-jar|' "${ST}/chamadas.log")"
EST="$("${REAL_PY}" -I "${RAIZ}/analysis/rebaseline.py" estado --saida "${OUT}" --matriz "${MAT}" 2>&1)"; RC=$?
ok "5 rebaseline.py estado (real) exit 0" "0" "${RC}"
ok "5 total de recusas = 1 (so a do codigo)" "1 {'S1-sync-none': 1}" \
    "$("${REAL_PY}" -I -c 'import json,sys; e=json.load(open(sys.argv[1])); print(e["total_recusas"], e["recusas_por_celula"])' "${OUT}/estado.json")"
ok "5 nenhuma reposicao pendente" "" "$("${REAL_PY}" -I "${RAIZ}/analysis/rebaseline.py" estado --saida "${OUT}" --matriz "${MAT}" --listar-reposicoes 2>&1)"
OUT_R2="$("${REAL_PY}" -I "${H4PY}" repor --saida "${OUT}" --matriz "${MAT}" 2>&1)"; RC=$?
ok "5 repor de novo: exit 0" "0" "${RC}"
contem "5 repor de novo: nada a repor" "nenhuma execucao h4 ACEITA a repor" "${OUT_R2}"
rodar_runner
ok "5 runner pela terceira vez: exit 0" "0" "${RC}"
ok "5 runner pela terceira vez: nenhum workload novo" "3" "$(grep -c ' java|-jar|' "${ST}/chamadas.log")"

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
