"""B-44.3 (investigacao da fusao): para cada fusao (1o ISPN000093), o atraso desde a ultima troca de vista
(ISPN000094) de cada coordenador de subgrupo e o resto em relacao a multiplos de 48 s
(check_interval do MERGE3 = 1,6 x max_interval = 1,6 x 30 s; o verificador reinicia a cada troca de vista).
Uso: python -I fusao.py RAIZ"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analisa  # noqa: E402
import marcos  # noqa: E402,F401  (so para reaproveitar linhas())

RAIZ = sys.argv[1] if len(sys.argv) > 1 else "."
analisa.RAIZ = RAIZ
marcos.RAIZ = RAIZ
CK = 48000
print("rot\tfusao_desde_cura_ou_acao_ms\tsubgrupo_coord\tultima_vista_ate_fusao_ms\tk(x48s)\tresto_ms\tlider_da_fusao")
for rot, f in analisa.fases().items():
    if rot in analisa.INVALIDAS or f.get("t0") is None:
        continue
    pr = analisa.probe(rot)
    ref = f.get("cura") or (max(p["fim"] for p in pr) if pr else None)
    if not ref:
        continue
    L = {n: marcos.linhas(rot, n) for n in ("isn1", "isn2", "isn3")}
    fus = [(t, ln) for n in L for t, ln in L[n] if t >= ref and "ISPN000093" in ln]
    if not fus:
        continue
    tf, lf = min(fus)
    lider = re.search(r"MergeView::\[(isn\d)", lf).group(1)
    for n in ("isn1", "isn3"):
        vs = [t for t, ln in L[n] if t < tf and "ISPN000094" in ln and t >= f["t0"]]
        if not vs:
            continue
        d = tf - max(vs)
        k = round(d / CK)
        print("%s\t%d\t%s\t%d\t%d\t%d\t%s" % (rot, tf - ref, n, d, k, d - k * CK, lider))
