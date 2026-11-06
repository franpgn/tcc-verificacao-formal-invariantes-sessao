"""W-10 smoke: cruza cada I1 da sonda com o Logout (no, instante), o replay (no, instante, tipo) e a varredura final.

Uso: python -I analisar_w10.py <dir-da-execucao>. So le os brutos; imprime tabelas em texto.
"""
import csv
import sys
from pathlib import Path

d = Path(sys.argv[1])
NOS = {"127.0.0.1:12901": "isn1", "127.0.0.1:12902": "isn2", "127.0.0.1:12903": "isn3"}
mk = {}
for l in (d / "marcadores.txt").read_text().split("\n"):
    if l.strip():
        kv = dict(x.split("=", 1) for x in l.split())
        mk[kv["event"]] = int(kv["t_epoch_ns"])
ini, fim = mk["start"], mk.get("end")
rel = lambda t: (t - ini) / 1e9
ev = list(csv.DictReader(open(d / "rep-001.csv", encoding="utf-8")))
logout = {}
for e in ev:
    if e["operation"] == "Logout" and not e["return_code"].startswith("ERROR"):
        logout[e["key"]] = e  # ultima tentativa sem erro
rep = [e for e in ev if e["operation"] == "ReplayValidate"]
viol = list(csv.DictReader(open(d / "violacoes-rep-001.csv", encoding="utf-8")))
sonda = [v for v in viol if v.get("origem") == "sonda"]
print("marcadores: start=%d end=%s (end-start=%.1f s)" % (ini, fim, rel(fim) if fim else float("nan")))
print("eventos: %d; ReplayValidate: %d; Logout sem erro: %d" % (len(ev), len(rep), len(logout)))
print("ReplayValidate por fase e codigo:")
cont = {}
for e in rep:
    k = (e.get("phase", ""), e["return_code"])
    cont[k] = cont.get(k, 0) + 1
for k in sorted(cont):
    print("  %-20s %-18s %d" % (k[0], k[1], cont[k]))
print("I1 da sonda (%d):" % len(sonda))
print("  %-8s %-10s %-12s %-10s %-26s %s" % ("sid", "logout_no", "logout_t_s", "replay_no", "replays (t_s:codigo)", "fase_viol"))
for v in sonda:
    sid = v["chave"]
    lo = logout.get(sid)
    rs = [e for e in rep if e["key"] == sid]
    print("  %-8s %-10s %-12s %-10s %-26s %s" % (
        sid[:8], NOS.get(lo["replica"], lo["replica"]) if lo else "?",
        "%.2f" % rel(int(lo["end_ns"])) if lo else "?",
        ",".join(sorted({NOS.get(e["replica"], e["replica"]) for e in rs})),
        " ".join("%.2f:%s" % (rel(int(e["start_ns"])), e["return_code"]) for e in rs), v["fase"]))
var = list(csv.DictReader(open(d / "varredura-rep-001.csv", encoding="utf-8"))) if (d / "varredura-rep-001.csv").exists() else []
print("varredura (%d achados):" % len(var))
for x in var:
    print("  " + ", ".join("%s=%s" % (k, (v[:8] if k in ("chave", "sid") else v)) for k, v in x.items()))
sids_sonda = {v["chave"] for v in sonda}
print("sids distintas com I1 da sonda: %d" % len(sids_sonda))
