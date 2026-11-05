"""B-44.3: erros Hot Rod e violacoes por fase (antes de t0, t0..t_ref, t_ref..t_ref+5 s, t_ref+5 s..cura,
depois da cura) e por no. Uso: python -I erros_fase.py RAIZ > analise/erros-fase.tsv"""
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analisa  # noqa: E402

RAIZ = sys.argv[1] if len(sys.argv) > 1 else "."
analisa.RAIZ = RAIZ


def fase(t, t0, tr, cura):
    if t < t0:
        return "1_antes_t0"
    if t < tr:
        return "2_t0_tref"
    if t <= tr + 5000:
        return "3_tref_tref+5s"
    if cura is None or t < cura:
        return "4_tref+5s_cura_ou_fim"
    return "5_pos_cura"


print("rot\tfase\tno\ttipo\tn")
for rot, f in analisa.fases().items():
    t0 = f.get("t0")
    if t0 is None:
        continue
    pr = analisa.probe(rot)
    tr = min(p["ini"] for p in pr) if pr else (f.get("t_nominal") or t0 + 5000)
    ev, vi = analisa.carga(rot)
    c = collections.Counter()
    for e in ev:
        if e[4].startswith("ERROR"):
            c[(fase(e[0], t0, tr, f.get("cura")), e[3], e[4])] += 1
    for v in vi:
        c[(fase(v[0], t0, tr, f.get("cura")), "-", "VIOL_" + v[1])] += 1
    for (fa, no, ti), n in sorted(c.items()):
        print("\t".join([rot, fa, no, ti, str(n)]))
