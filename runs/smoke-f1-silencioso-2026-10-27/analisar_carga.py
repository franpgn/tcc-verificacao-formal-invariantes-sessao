"""Ensaios com carga do smoke do F1: contagens por return_code e latencia (max, p99) por operacao na janela
[instante da injecao, 3 nos HEALTHY de volta] e na execucao inteira. Uso: python analisar_carga.py <dir-ensaio>
Le resumo.json (de analisar_smoke.py) para os instantes. So para avaliar o risco de K3; nao e dado de campanha."""
import csv, json, math, sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path


def p99(v):
    v = sorted(v)
    return v[max(0, math.ceil(0.99 * len(v)) - 1)] if v else None


def main():
    d = Path(sys.argv[1])
    res = {r["rotulo"]: r for r in json.loads((d.parent / "resumo.json").read_text(encoding="utf-8"))}[d.name]
    t_inj = datetime.fromisoformat(res["t_injecao_utc"]).timestamp()
    t_fim = t_inj + (res.get("healthy_desde_start_s") or 0) + 60 + 1   # ~ start + volta
    jan = (int(t_inj * 1e9), int(t_fim * 1e9))
    rc_jan, rc_tot = Counter(), Counter()
    lat_jan, lat_tot = defaultdict(list), defaultdict(list)
    arq = d / "csv" / "rep-001.csv"
    with open(arq, newline="") as fh:
        for row in csv.DictReader(fh):
            s, e = int(row["start_ns"]), int(row["end_ns"])
            op, rc = row["operation"], row["return_code"]
            ms = (e - s) / 1e6
            rc_tot[(op, rc)] += 1
            lat_tot[op].append(ms)
            if jan[0] <= s <= jan[1]:
                rc_jan[(op, rc)] += 1
                lat_jan[op].append(ms)
    out = {"janela_utc": [datetime.utcfromtimestamp(jan[0] / 1e9).isoformat() + "Z",
                          datetime.utcfromtimestamp(jan[1] / 1e9).isoformat() + "Z"],
           "return_code_janela": {"%s|%s" % k: n for k, n in sorted(rc_jan.items())},
           "return_code_total": {"%s|%s" % k: n for k, n in sorted(rc_tot.items())},
           "latencia_ms_janela": {op: {"n": len(v), "max": round(max(v), 1), "p99": round(p99(v), 2)}
                                  for op, v in sorted(lat_jan.items())},
           "latencia_ms_total": {op: {"n": len(v), "max": round(max(v), 1), "p99": round(p99(v), 2)}
                                 for op, v in sorted(lat_tot.items())}}
    san = d / "sanidade.json"
    if san.exists():
        s = json.loads(san.read_text(encoding="utf-8"))
        out["sanidade"] = {"veredito": s.get("veredito"), "motivos": s.get("motivos")}
    (d / "carga-resumo.json").write_text(json.dumps(out, indent=1, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(out, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    main()
