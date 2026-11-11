"""Recalculo independente da avaliacao fatorial a partir dos brutos (sem importar o codigo do projeto).

Uso: python -I valida_fatorial.py <campanha-s3-fatorial> <comparacao-principal.json> <comparacao-semfalha.json> <saida.json>
Regras reimplementadas a partir da leitura de analysis/fases.py, apuracao_v.py e comparar_bracos.py (tag 4b96798).
"""
import calendar
import csv
import json
import math
import re
import sys
import time
from collections import Counter, defaultdict
from fractions import Fraction
from pathlib import Path

import numpy as np
from scipy import stats

RAIZ = Path(sys.argv[1])
OFICIAL = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
OFICIAL_SF = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))
SAIDA = Path(sys.argv[4])
W_NS = 191 * 10**9
ERROS = {"ERROR_TRANSPORT", "ERROR_UNAVAILABLE"}
INSTR = {"ReplayValidate"}
RX_MOT = re.compile(r"confirmad[ao] \(?([A-Z_]+)\)?")
RX_ID = re.compile(r"de identidade (\S+) BLOCKED")
RX_T = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$")


def t_ns(txt):
    m = RX_T.match(txt.strip())
    return calendar.timegm(time.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S")) * 10**9 + int((m.group(2) or "").ljust(9, "0"))


def p_rank(vals, p):
    s = sorted(vals)
    n = len(s)
    fp = Fraction(str(p))
    k = -((-fp.numerator * n) // fp.denominator)
    return s[min(n - 1, max(0, k - 1))]


def execucao(d, todas_fases):
    ej = json.loads((d / "execucao.json").read_text(encoding="utf-8"))
    ini = fim = no = None
    mk = d / "marcadores.txt"
    if mk.exists():
        for ln in mk.read_text(encoding="utf-8").splitlines():
            ln = ln.strip()
            if not ln or ln.startswith("#"):
                continue
            kv = dict(t.split("=", 1) for t in ln.split())
            if kv["event"] == "start" and ini is None:
                ini, no = int(kv["t_epoch_ns"]), kv.get("node")
            elif kv["event"] == "end":
                fim = int(kv["t_epoch_ns"])
    rc = d / "convergencia-resumo-rep-001.json"
    res = json.loads(rc.read_text(encoding="utf-8")) if rc.exists() else {}
    conv = res.get("t_convergencia_abs_ns")
    conv = None if conv is None else int(conv)
    div = bool(res.get("divergencia_persistente", False))
    lim = None
    if fim is not None:
        lim = fim + W_NS if conv is None else max(fim + W_NS, conv)

    def sel(t):
        if todas_fases:
            return True
        if ini is None or t < ini:
            return False
        if t <= fim:
            return True
        return True if div else t <= lim

    ev, por_chave = [], defaultdict(list)
    with open(d / "rep-001.csv", newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r)
        for x in r:
            if not x:
                continue
            a, b = int(x[2]), int(x[3])
            por_chave[x[6]].append((x[1], a, b, x[4], x[5]))
            if x[1] not in INSTR:
                ev.append((a, b, x[1], x[5]))
    ac = []
    with open(d / "s3-acoes-rep-001.csv", newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r)
        for x in r:
            if x:
                ac.append((int(x[0]), int(x[8]), int(x[9]), x[6] == "true"))
    t0 = min([e[0] for e in ev] + [a[0] for a in ac])
    t1 = max([e[1] for e in ev] + [a[1] for a in ac]) + 1
    if todas_fases:
        dur = (t1 - t0) / 1e9
    else:
        fim_sel = t1 if div else lim + 1
        dur = max(0, min(t1, fim_sel) - max(t0, ini)) / 1e9
    acs = [a for a in ac if sel(a[0])]
    evs = [e for e in ev if sel(e[0])]
    out = {"n_acoes": len(acs)}
    out["p99_com_espera"] = p_rank([b - a for a, b, w, f in acs], 0.99) / 1e9 if len(acs) >= 1000 else None
    out["p99_sem_espera"] = p_rank([b - a - w for a, b, w, f in acs], 0.99) / 1e9 if len(acs) >= 1000 else None
    out["falhas_por_1000"] = 1000.0 * sum(1 for a in acs if a[3]) / len(acs)
    out["vazao_util"] = sum(1 for a in acs if not a[3]) / dur
    out["taxa_erro"] = sum(1 for e in evs if e[3] in ERROS) / len(evs)
    val = [b - a for a, b, op, c in evs if op == "Validate"]
    out["p99_validate"] = p_rank(val, 0.99) / 1e9 if len(val) >= 1000 else None

    # V: tres origens, pares distintos (chave, classe)
    dets = []
    with open(d / "violacoes-rep-001.csv", newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            inv, cl = (x.get("invariante") or "").strip(), (x.get("classificacao") or "").strip()
            classe = "I2_REAL" if inv == "I2" and cl == "REAL" else (inv if inv in ("I1", "I4") else None)
            if classe is None:
                continue
            m = RX_MOT.search(x.get("mensagem") or "")
            dets.append({"orig": "sonda" if (x.get("origem") or "").strip() else "online", "classe": classe,
                         "chave": x["chave"].strip(), "t": t_ns(x["instante"]), "mot": m.group(1) if m else None,
                         "id": None})
    varr_erro = (d / "varredura-rep-001.erro.txt").exists()
    vf = d / "varredura-rep-001.csv"
    if vf.exists():
        with open(vf, newline="", encoding="utf-8") as fh:
            r = csv.reader(fh)
            next(r)
            for x in r:
                if not x or len(x) < 3:
                    continue
                inv, ch, cl, det = x[0].strip(), x[1].strip(), x[2].strip(), ",".join(x[3:])
                if cl != "REAL" or inv not in ("I1", "I4", "I2", "I2-PERDIDO"):
                    continue
                if inv in ("I1", "I4"):
                    m = RX_MOT.search(det)
                    dets.append({"orig": "varredura", "classe": inv, "chave": ch, "t": None,
                                 "mot": m.group(1) if m else None, "id": None})
                else:
                    m = RX_ID.search(det)
                    dets.append({"orig": "varredura", "classe": "I2_REAL", "chave": ch, "t": None, "mot": None,
                                 "id": ch if inv == "I2-PERDIDO" else (m.group(1) if m else None)})
    tfc = t1 - 1
    pares = defaultdict(list)
    for x in dets:
        if x["orig"] == "online":
            tipo = "online"
        elif x["orig"] == "sonda":
            tipo = "tardia" if x["t"] > tfc else "imediata"
        else:
            tipo = "varredura"
        if tipo in ("online", "imediata"):
            t_at = x["t"]
        elif x["classe"] in ("I1", "I4"):
            ts = [e[2] for e in por_chave.get(x["chave"], ()) if e[0] == "Logout" and e[4] == "NONE"]
            t_at = min(ts) if (ts and x["mot"] in (None, "LOGOUT")) else None
        else:
            ts = [e[2] for e in por_chave.get(x["id"], ()) if e[0] == "Block" and e[4] == "NONE"] if x["id"] else []
            t_at = max(ts) if ts else None
        conta = t_at is None or sel(t_at)
        pares[(x["chave"], x["classe"])].append((tipo, x["t"], conta))
    contam = {k: v for k, v in pares.items() if any(c for _, _, c in v)}
    v_pares = len(contam)
    out["V_pares"] = None if varr_erro else v_pares
    out["V_por_1000"] = None if varr_erro else 1000.0 * v_pares / len(acs)
    out["V_por_classe"] = dict(Counter(k[1] for k in contam))
    out["V_por_origem"] = dict(Counter(t for v in contam.values() for t, _, c in v if c))
    tf = ej.get("t_fusao_ns")
    if "t_fusao_ns" in ej and tf is not None:
        tf = int(tf)
        perda, expo = set(), set()
        for (ch, cl), v in pares.items():
            if cl != "I1":
                continue
            for tipo, t, c in v:
                if not c:
                    continue
                if tipo in ("varredura", "tardia") or (tipo == "online" and t >= tf):
                    perda.add(ch)
                if tipo in ("imediata", "online") and t < tf:
                    expo.add(ch)
        out["perda_permanente"] = len(perda)
        out["exposicao"] = len(expo)
    out["divergencia"] = 1.0 if div else 0.0
    # controlador
    acoes = Counter()
    tl = d / "decisoes-rep-001.jsonl"
    if tl.exists():
        for ln in tl.read_text(encoding="utf-8").splitlines():
            o = json.loads(ln)
            if o.get("tipo") == "acao" and o.get("executada"):
                acoes["%s %s" % (o.get("operacao"), (o.get("acao") or {}).get("tipo"))] += 1
    out["controlador_acoes_executadas"] = dict(acoes)
    out["no_falha"] = no
    return out


# ---------------------------------------------------------------- integridade e protocolo
chec = {}
for p in sorted(RAIZ.glob("exec-*/checagem.json")):
    j = json.loads(p.read_text(encoding="utf-8"))
    chec[p.parent.name] = j
ver = Counter(j["veredito"] for j in chec.values())
bracos_esperados = {"ASYNC-F2": {"PO", "PL", "AO", "AL"}}
aceitos = defaultdict(set)
todos = defaultdict(set)
for nome, j in chec.items():
    todos[(j["celula"], j["bloco"])].add(j["braco"])
    if j["veredito"] == "ACEITA":
        aceitos[(j["celula"], j["bloco"])].add(j["braco"])
completos = defaultdict(list)
perdidos = defaultdict(list)
for (cel, bl), br in todos.items():
    esp = bracos_esperados.get(cel, {"A", "B"})
    (completos if aceitos[(cel, bl)] >= esp else perdidos)[cel].append(int(bl))
ident = defaultdict(Counter)
for p in sorted(RAIZ.glob("exec-*/execucao.json")):
    j = json.loads(p.read_text(encoding="utf-8"))
    for k in ("bundle_sha256", "jar_sha256", "controlador_sha256", "parametros_sha256", "referencia_sha256"):
        ident[k][j.get(k)] += 1
    ident["boot_por_fase"][(j.get("fase"), j.get("boot_id"))] += 1
manif = defaultdict(dict)
with open(RAIZ / "manifesto-bracos.tsv", newline="", encoding="utf-8") as fh:
    for x in csv.DictReader(fh, delimiter="\t"):
        manif[x["celula"]].setdefault(x["bloco"], {})[x["braco"]] = x["execucao"]
protocolo = {
    "vereditos": dict(ver),
    "blocos_completos": {c: sorted(v) for c, v in completos.items()},
    "blocos_perdidos": {c: sorted(v) for c, v in perdidos.items()},
    "manifesto_blocos": {c: sorted(int(b) for b in v) for c, v in manif.items()},
    "identificacao": {k: {str(kk): vv for kk, vv in v.items()} for k, v in ident.items()},
}

# ---------------------------------------------------------------- desfechos por execucao
valores = {}
for cel, bls in manif.items():
    for bl, br in bls.items():
        for b, ex in br.items():
            valores[ex] = execucao(RAIZ / ex, cel == "ASYNC-none")


# ---------------------------------------------------------------- estatistica
def perm(d):
    d = np.asarray(d, float)
    n = d.size
    s = ((np.arange(2 ** n)[:, None] >> np.arange(n)) & 1) * 2 - 1
    return int(np.count_nonzero(np.abs(s @ d / n) >= abs(d.mean()) - 1e-12)) / 2 ** n


def ict(d, log):
    d = np.asarray(d, float)
    m, s = d.mean(), d.std(ddof=1)
    h = stats.t.ppf(0.975, d.size - 1) * s / math.sqrt(d.size)
    return [math.exp(m - h), math.exp(m + h)] if log else [m - h, m + h]


def p_ni(d, lim):
    d = np.asarray(d, float)
    m, s = d.mean(), d.std(ddof=1)
    if s == 0:
        return 0.0 if m <= lim else 1.0
    return min(1.0, 2 * stats.t.cdf((m - lim) / (s / math.sqrt(d.size)), d.size - 1))


def holm(ps):
    m = len(ps)
    o = sorted(range(m), key=lambda i: ps[i])
    out, c = [0.0] * m, 0.0
    for pos, i in enumerate(o):
        c = max(c, min(1.0, (m - pos) * ps[i]))
        out[i] = c
    return out


DESF = {  # nome oficial -> (campo meu, escala log?)
    "taxa_violacoes_por_1000": ("V_por_1000", False),
    "acao_s3_p99_com_espera": ("p99_com_espera", True),
    "acao_s3_p99_sem_espera": ("p99_sem_espera", True),
    "vazao_util": ("vazao_util", True),
    "falhas_percebidas_por_1000": ("falhas_por_1000", False),
    "taxa_erro": ("taxa_erro", False),
    "exposicao_janela_sids": ("exposicao", False),
    "perda_permanente_sids": ("perda_permanente", False),
    "divergencia_persistente": ("divergencia", False),
    "evento_p99:Validate": ("p99_validate", True),
}
resultado = {"protocolo": protocolo, "por_execucao": valores, "f2": {}, "comparacao_por_bloco": {}, "descritivas": {}}

# comparacao por bloco/braco com o y_por_bloco oficial
dif_max = {}
for fonte in (OFICIAL, OFICIAL_SF):
    for cel, c in fonte["celulas"].items():
        for nome, (campo, _) in DESF.items():
            yb = (c["desfechos"].get(nome) or {}).get("y_por_bloco")
            if not yb:
                continue
            pior = 0.0
            n = 0
            for bl, porb in yb.items():
                for br, y in porb.items():
                    ex = manif[cel][bl][br]
                    mine = valores[ex].get(campo)
                    if y is None and mine is None:
                        continue
                    if y is None or mine is None:
                        pior = float("inf")
                        continue
                    rel = abs(mine - y) / max(abs(y), 1e-12) if y != 0 else abs(mine)
                    pior = max(pior, rel)
                    n += 1
            dif_max["%s|%s" % (cel, nome)] = {"n": n, "maior_dif_relativa": pior}
resultado["comparacao_por_bloco"] = dif_max

# F2 fatorial
blocos = sorted(manif["ASYNC-F2"], key=int)
efeitos = {}
for nome, (campo, log) in DESF.items():
    y = {br: [] for br in ("PO", "PL", "AO", "AL")}
    ok = True
    for bl in blocos:
        for br in y:
            v = valores[manif["ASYNC-F2"][bl][br]].get(campo)
            if v is None:
                ok = False
            y[br].append(v)
    if not ok:
        continue
    f = (lambda v: math.log(v)) if log else (lambda v: v)
    Y = {br: np.array([f(v) for v in vs]) for br, vs in y.items()}
    cont = {"A1": 0.5 * ((Y["AO"] - Y["PO"]) + (Y["AL"] - Y["PL"])),
            "A4": 0.5 * ((Y["PL"] - Y["PO"]) + (Y["AL"] - Y["AO"])),
            "interacao": (Y["AL"] - Y["AO"]) - (Y["PL"] - Y["PO"])}
    efeitos[nome] = {}
    for ef, d in cont.items():
        est = math.exp(d.mean()) if log else float(d.mean())
        efeitos[nome][ef] = {"estimativa": est, "ic_t": ict(d, log), "p_perm": perm(d), "media_d": float(d.mean()),
                             "d": d.tolist(),
                             "medias_bracos": {br: float(np.mean(vs)) for br, vs in y.items()}}
    if nome == "acao_s3_p99_com_espera":
        for ef in ("A1", "A4"):
            efeitos[nome][ef]["p_ni"] = p_ni(cont[ef], math.log(1.10))
    if nome == "vazao_util":
        for ef in ("A1", "A4"):
            efeitos[nome][ef]["limite_inferior"] = ict(cont[ef], True)[0]
iu = {}
for ef in ("A1", "A4"):
    v = efeitos["taxa_violacoes_por_1000"][ef]
    pv = v["p_perm"] if v["media_d"] < 0 else 1.0
    pl = efeitos["acao_s3_p99_com_espera"][ef]["p_ni"]
    hv = v["p_perm"] <= 0.05 and v["media_d"] < 0
    hl = efeitos["acao_s3_p99_com_espera"][ef]["ic_t"][1] <= 1.10
    vz = efeitos["vazao_util"][ef]["limite_inferior"] >= 0.90
    iu[ef] = {"p_V": v["p_perm"], "p_L": pl, "p_IU": max(pv, pl), "H_V": hv, "H_L": hl, "vazao_ni": vz}
ph = holm([iu["A1"]["p_IU"], iu["A4"]["p_IU"]])
iu["A1"]["p_IU_holm"], iu["A4"]["p_IU_holm"] = ph
for ef in ("A1", "A4"):
    iu[ef]["resultado"] = "melhoria" if (iu[ef]["H_V"] and iu[ef]["H_L"] and iu[ef]["vazao_ni"]
                                         and iu[ef]["p_IU_holm"] <= 0.05) else "sem-melhoria"
# Holm dos 11 secundarios: os que recalculo + cpu, energia 0.3, delta_rec e delta_conv do oficial
sec_meus = ["acao_s3_p99_sem_espera", "divergencia_persistente", "exposicao_janela_sids", "falhas_percebidas_por_1000",
            "perda_permanente_sids", "taxa_erro", "evento_p99:Validate"]
sec_of = ["cpu_por_1000_uteis", "energia_total_j_por_1000_uteis:0.3", "delta_rec_s", "delta_conv_s"]
holm_sec = {}
for ef in ("A1", "A4"):
    nomes = sec_meus + sec_of
    ps = [efeitos[n][ef]["p_perm"] for n in sec_meus] + \
         [OFICIAL["celulas"]["ASYNC-F2"]["desfechos"][n]["efeitos"][ef]["p_permutacao"] for n in sec_of]
    holm_sec[ef] = dict(zip(nomes, holm(ps)))
resultado["f2"] = {"efeitos": efeitos, "regra_iu": iu, "holm_secundarios": holm_sec}

# celulas descritivas
for cel in ("ASYNC-F1", "ASYNC-F3", "ASYNC-F4", "ASYNC-none"):
    r = {"V_pares": {}, "n_acoes": {}, "acoes_controlador": {}, "p99_razao_media": None}
    for br in ("A", "B"):
        exs = [manif[cel][bl][br] for bl in sorted(manif[cel], key=int)]
        r["V_pares"][br] = sum(valores[e]["V_pares"] or 0 for e in exs)
        r["n_acoes"][br] = sum(valores[e]["n_acoes"] for e in exs)
        tot = Counter()
        for e in exs:
            tot.update(valores[e]["controlador_acoes_executadas"])
        r["acoes_controlador"][br] = dict(tot)
    d = [math.log(valores[manif[cel][bl]["B"]]["p99_com_espera"] / valores[manif[cel][bl]["A"]]["p99_com_espera"])
         for bl in sorted(manif[cel], key=int)]
    r["p99_razao_media"] = math.exp(float(np.mean(d)))
    r["p99_ic_t"] = ict(d, True) if len(d) > 1 else None
    resultado["descritivas"][cel] = r
# acoes do controlador na F2 por braco
f2c = {}
for br in ("PO", "PL", "AO", "AL"):
    tot = Counter()
    for bl in blocos:
        tot.update(valores[manif["ASYNC-F2"][bl][br]]["controlador_acoes_executadas"])
    f2c[br] = dict(tot)
resultado["f2"]["acoes_controlador"] = f2c
SAIDA.write_text(json.dumps(resultado, indent=1, ensure_ascii=False, default=str), encoding="utf-8")
print("ok", SAIDA)
