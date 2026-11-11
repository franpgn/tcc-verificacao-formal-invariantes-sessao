"""Sensibilidade (nao pre-registrada): CPU e energia da F2 com os 12 blocos.

1) steal medido do primeiro ao ultimo ponto valido dentro da janela (sem extrapolar), em todas as execucoes;
2) cpu_por_1000_uteis e energia (razao 0,3 e 0,5) por execucao a partir do energia-rep-001.json (fases durante e
   recuperacao), contrastes do fatorial em log-razao, IC t e permutacao exata; confere os 4 blocos do oficial.
"""
import csv
import json
import math
import sys
from pathlib import Path

import numpy as np
from scipy import stats

T = Path(sys.argv[1])          # analise-oficial/trabalho/campanha-s3-fatorial
OF = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
SEL = {"durante", "durante-pre-split", "durante-pos-split", "recuperacao"}

manif = {}
with open(T / "manifesto-principal.tsv", newline="", encoding="utf-8") as fh:
    for x in csv.DictReader(fh, delimiter="\t"):
        manif.setdefault(x["celula"], {}).setdefault(x["bloco"], {})[x["braco"]] = Path(x["execucao"]).name


def steal_total(d, t0, t1):
    pts = []
    with open(d / "recursos-rep-001.csv", newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            if x["fonte"] == "vm" and x["estado"] == "ok" and x["steal_usec_acum"] != "":
                t = int(x["t_epoch_ns"])
                if t0 <= t <= t1:
                    pts.append((t, int(x["steal_usec_acum"])))
    return (pts[-1][1] - pts[0][1]) if len(pts) > 1 else None, len(pts)


def por_exec(nome):
    d = T / nome
    e = json.loads((d / "energia-rep-001.json").read_text(encoding="utf-8"))
    rot = [f["rotulo"] for f in e["fases"] if f["rotulo"] in SEL]
    cpu = sum(e["agregados"]["vm"][r]["cpu_s"] for r in rot)
    uteis = sum(e["uteis"][r] for r in rot)
    en = {k: sum(v["vm"][r]["e_total_j"] for r in rot) for k, v in e["energia"]["por_razao"].items()}
    st, n = steal_total(d, int(e["janela"]["t0_ns"]), int(e["janela"]["t1_ns"]))
    return {"cpu": 1000 * cpu / uteis, "en": {k: 1000 * v / uteis for k, v in en.items()}, "steal_usec": st,
            "n_amostras": n, "steal_oficial": e["steal"]["frac"]}


def perm(d):
    d = np.asarray(d, float)
    s = ((np.arange(2 ** d.size)[:, None] >> np.arange(d.size)) & 1) * 2 - 1
    return int(np.count_nonzero(np.abs(s @ d / d.size) >= abs(d.mean()) - 1e-12)) / 2 ** d.size


def ict(d):
    d = np.asarray(d, float)
    h = stats.t.ppf(0.975, d.size - 1) * d.std(ddof=1) / math.sqrt(d.size)
    return math.exp(d.mean() - h), math.exp(d.mean() + h)


vals = {n: por_exec(n) for c in manif.values() for b in c.values() for n in b.values()}
print("steal (usec) do 1o ao ultimo ponto valido na janela, por execucao: valores distintos =",
      sorted({v["steal_usec"] for v in vals.values()}, key=lambda x: (x is None, x)))
print("execucoes com steal oficial None:", sum(1 for v in vals.values() if v["steal_oficial"] is None),
      "| minimo de amostras na janela:", min(v["n_amostras"] for v in vals.values()))
f2 = manif["ASYNC-F2"]
of = OF["celulas"]["ASYNC-F2"]["desfechos"]
blocos_of = sorted(of["cpu_por_1000_uteis"]["efeitos"]["A4"]["d_b"], key=int)
todos = sorted(f2, key=int)
for rotulo, campo in (("cpu_por_1000_uteis", lambda v: v["cpu"]),
                      ("energia_total_j_por_1000_uteis:0.3", lambda v: v["en"]["0.3"]),
                      ("energia_total_j_por_1000_uteis:0.5", lambda v: v["en"]["0.5"])):
    for nome_b, bl in (("4 blocos (oficial)", blocos_of), ("12 blocos (sensibilidade)", todos)):
        Y = {br: np.array([math.log(campo(vals[f2[b][br]])) for b in bl]) for br in ("PO", "PL", "AO", "AL")}
        cont = {"A1": 0.5 * ((Y["AO"] - Y["PO"]) + (Y["AL"] - Y["PL"])),
                "A4": 0.5 * ((Y["PL"] - Y["PO"]) + (Y["AL"] - Y["AO"])),
                "interacao": (Y["AL"] - Y["AO"]) - (Y["PL"] - Y["PO"])}
        for ef, d in cont.items():
            lo, hi = ict(d)
            extra = ""
            if nome_b.startswith("4"):
                o = of[rotulo]["efeitos"][ef]
                extra = " | oficial %.4f p %.4f" % (o["estimativa"], o["p_permutacao"])
            print("%-36s %-26s %-9s razao %.4f IC [%.4f; %.4f] p %.4f%s"
                  % (rotulo, nome_b, ef, math.exp(d.mean()), lo, hi, perm(d), extra))
medias = {br: np.mean([vals[f2[b][br]]["en"]["0.3"] for b in todos]) for br in ("PO", "PL", "AO", "AL")}
print("energia J por 1000 uteis (0,3), media por braco, 12 blocos:", {k: round(v, 1) for k, v in medias.items()})
medias = {br: np.mean([vals[f2[b][br]]["cpu"] for b in todos]) for br in ("PO", "PL", "AO", "AL")}
print("CPU-s por 1000 uteis, media por braco, 12 blocos:", {k: round(v, 3) for k, v in medias.items()})
