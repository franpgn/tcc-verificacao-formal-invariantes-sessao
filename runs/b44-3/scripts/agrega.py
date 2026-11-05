"""B-44.3: medianas e extremos de t_probe e t_vista por grupo de execucoes (de analise/marcos.tsv).
t_vista_maioria = max(vista2_isn1, vista2_isn2); t_vista_isn3 = vista1_isn3; ms desde o envio do 1o pedido.
Uso: python -I agrega.py RAIZ"""
import csv
import os
import statistics
import sys

RAIZ = sys.argv[1] if len(sys.argv) > 1 else "."
GRUPOS = {
    "V-3 + INV, corte, coordenador fora do isn3 (isn1)": ["V3-sync-acao1", "V3-sync-acao2", "V3-async-acao1",
                                                          "V3-async-acao2r", "INV-sync-rede1", "INV-sync-rede2",
                                                          "INV-async-cura40"],
    "V-4, corte, isn3 coordenador": ["V4-async-acao"],
    "V-5, sem corte (falso positivo), isn3 coordenador": ["V5-async-acao", "V5-sync-acao"],
}
linhas = {r["rot"]: r for r in csv.DictReader(open(os.path.join(RAIZ, "analise", "marcos.tsv"), encoding="utf-8"),
                                             delimiter="\t")}


def num(x):
    return int(x) if x not in ("", None) else None


print("grupo\tn\tmedida\tmediana_ms\tmin_ms\tmax_ms")
for g, rots in GRUPOS.items():
    rs = [linhas[r] for r in rots if r in linhas]
    med = {
        "t_probe": [num(r["probe_dur_ms(max)"]) for r in rs],
        "t_vista_maioria": [max(num(r["vista2_isn1"]) or 0, num(r["vista2_isn2"]) or 0) for r in rs],
        "t_vista_isn3": [num(r["vista1_isn3"]) for r in rs],
        "degraded_isn3(max dos 2 caches)": [max(num(r["degraded_isn3_sessions"]) or 0,
                                               num(r["degraded_isn3_sessions_async"]) or 0) for r in rs],
        "1a_recusa_isn3": [num(r["1a_recusa_hotrod_isn3(ISPN000306)"]) for r in rs],
        "fim_rebal_maioria": [num(r["fim_rebal_maioria(sessions,sessions-async)"]) for r in rs],
    }
    for k, v in med.items():
        v = [x for x in v if x is not None]
        if v:
            print("%s\t%d\t%s\t%s\t%d\t%d" % (g, len(v), k, statistics.median(v), min(v), max(v)))
