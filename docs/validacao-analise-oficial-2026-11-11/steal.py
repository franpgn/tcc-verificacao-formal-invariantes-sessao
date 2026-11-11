"""Por que o steal da VM ficou sem medida (energia-rep-001.json da copia de trabalho da analise oficial)."""
import csv
import json
import sys
from collections import Counter
from pathlib import Path

RAIZ = Path(sys.argv[1])
linhas, tipos = [], Counter()
for f in sorted(RAIZ.glob("exec-*/energia-rep-001.json")):
    d = f.parent
    e = json.loads(f.read_text(encoding="utf-8"))
    t0, t1 = int(e["janela"]["t0_ns"]), int(e["janela"]["t1_ns"])
    iv = (e.get("parametros") or {}).get("intervalo_ms")
    vm, todas = [], []
    with open(d / "recursos-rep-001.csv", newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            if x["fonte"] != "vm":
                continue
            todas.append((int(x["t_epoch_ns"]), x["estado"], x["steal_usec_acum"], x["cpu_usec_acum"]))
            if x["estado"] == "ok" and x["steal_usec_acum"] != "":
                vm.append(int(x["t_epoch_ns"]))
    if not vm:
        mot = "sem amostra valida de steal (%d amostras vm; estados %s; steal vazio em %d)" % (
            len(todas), dict(Counter(s for _, s, _, _ in todas)), sum(1 for *_, st, _ in todas if st == ""))
    elif t0 < vm[0] or t1 > vm[-1]:
        mot = "janela fora das amostras: 1a amostra %.2f s depois do inicio; ultima %.2f s antes do fim" % (
            (vm[0] - t0) / 1e9, (t1 - vm[-1]) / 1e9)
    else:
        gaps = [(b - a) / 1e9 for a, b in zip(vm, vm[1:]) if b > t0 and a < t1]
        mot = "maior intervalo entre amostras na janela %.2f s" % max(gaps)
    st = e["steal"]["frac"]
    cel = d.name.split("-ASYNC-")[1]
    linhas.append((d.name, st, iv, mot))
    tipos[(cel.split("-")[0], st is None, mot.split(":")[0].split(" (")[0] if st is None else "ok")] += 1
for n, st, iv, mot in linhas:
    if st is None:
        print(n, "intervalo_ms=%s" % iv, mot)
print()
for k, v in sorted(tipos.items()):
    print(k, v)
