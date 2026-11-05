"""B-44.3 (investigacao): Send-Q das conexoes JGroups (porta 7800) de isn1/isn2 para o isn3, por segundo,
relativo a t0 da execucao; e erros Hot Rod da maioria por segundo. Uso: python -I rede.py RAIZ ROT"""
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analisa  # noqa: E402

RAIZ, ROT = sys.argv[1], sys.argv[2]
analisa.RAIZ = RAIZ
f = analisa.fases()[ROT]
t0 = f["t0"]
pr = analisa.probe(ROT)
tr = min(p["ini"] for p in pr) if pr else f.get("t_nominal")
pid_no = {}
for ln in open(os.path.join(RAIZ, "fases.tsv"), encoding="utf-8"):
    c = ln.rstrip("\n").split("\t")
    if len(c) >= 3 and "pid=" in c[2] and int(c[1]) <= f["inicio"]:
        ps = c[2].split("pid=")[1].split()[0].split("/")
        pid_no = {ps[0]: "isn1", ps[1]: "isn2", ps[2]: "isn3"}
sq = collections.defaultdict(dict)
dumps = []
for ln in open(os.path.join(RAIZ, "rede", ROT + ".txt"), encoding="utf-8"):
    c = ln.split()
    if len(c) >= 2 and c[1] == "DUMP":
        dumps.append((int(c[0]) - t0) / 1000)
    if len(c) < 7 or not c[0].isdigit():
        continue
    t, pid, rq, sqv, loc, peer = int(c[0]), c[1], c[3], int(c[4]), c[5], c[6]
    if "10.89.2.4:" in peer and (loc.endswith(":7800") or peer.endswith(":7800")):
        s = round((t - t0) / 1000)
        sq[s][pid_no.get(pid, pid)] = sq[s].get(pid_no.get(pid, pid), 0) + sqv
ev, _ = analisa.carga(ROT)
err = collections.Counter()
for e in ev:
    if e[4] == "ERROR_TRANSPORT" and e[3] in ("isn1", "isn2"):
        err[round((e[0] - t0) / 1000)] += 1
print("# %s t_ref-t0=%.1f s cura-t0=%s dumps(s desde t0)=%s" % (ROT, (tr - t0) / 1000,
      "%.1f" % ((f["cura"] - t0) / 1000) if f.get("cura") else "-", dumps))
print("s_desde_t0\tsendq_isn1_para_isn3\tsendq_isn2_para_isn3\terros_transport_maioria(inicio no segundo)")
for s in sorted(set(sq) | set(err)):
    print("%d\t%s\t%s\t%d" % (s, sq[s].get("isn1", ""), sq[s].get("isn2", ""), err.get(s, 0)))
