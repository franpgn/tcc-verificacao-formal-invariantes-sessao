"""B-43.6: resumo do smoke R1..R4 a partir dos brutos (stdout.log, rep-001.csv, violacoes-rep-001.csv).

Uso: python -I resumo.py  (no diretorio runs/b43-6-smoke). Grava resumo.json.
"""
import csv
import json
import os
import re
from collections import Counter

AQUI = os.path.dirname(os.path.abspath(__file__))


def ler(p):
    with open(p, encoding="utf-8", errors="replace") as f:
        return f.read()


def resumo(run):
    d = os.path.join(AQUI, run)
    out = ler(os.path.join(d, "stdout.log"))
    r = {"run": run}
    for chave, rx in [
        ("violacoes_detectadas", r"Violacoes detectadas: (\d+)"),
        ("total_operacoes", r"Total de operacoes: (\d+)"),
        ("registrados", r"registrados=(\d+)"),
        ("descartados_warmup", r"descartados_warmup=(\d+)"),
    ]:
        m = re.search(rx, out)
        r[chave] = int(m.group(1)) if m else None
    m = re.search(r"Auditor: i1_concorrentes=(\d+) i4_concorrentes=(\d+)", out)
    r["linha_auditor"] = m.group(0) if m else None
    r["i1_concorrentes"] = int(m.group(1)) if m else None
    r["i4_concorrentes"] = int(m.group(2)) if m else None
    m = re.search(r"Desfechos Validate: .*", out)
    r["linha_desfechos"] = m.group(0).strip() if m else None
    m = re.search(r"Replicacao: .*", out)
    r["replicacao"] = m.group(0).strip() if m else None
    r["error_no_stdout"] = len(re.findall(r" ERROR ", out))
    with open(os.path.join(d, "violacoes-rep-001.csv"), encoding="utf-8") as f:
        linhas = list(csv.reader(f))
    cab, corpo = linhas[0], linhas[1:]
    i_inv = cab.index("invariante") if "invariante" in cab else 1
    r["violacoes_csv"] = len(corpo)
    r["violacoes_por_invariante"] = dict(Counter(l[i_inv] for l in corpo))
    linhas_csv = 0
    with open(os.path.join(d, "rep-001.csv"), encoding="utf-8") as f:
        for _ in f:
            linhas_csv += 1
    r["linhas_csv_sem_cabecalho"] = linhas_csv - 1
    return r


def main():
    exits = {}
    for l in ler(os.path.join(AQUI, "exits.txt")).splitlines():
        p = l.split()
        if p:
            exits[p[0]] = int(p[1])
    runs = [resumo(x) for x in sorted(exits)]
    for x in runs:
        x["exit"] = exits[x["run"]]
    with open(os.path.join(AQUI, "resumo.json"), "w", encoding="utf-8") as f:
        json.dump(runs, f, indent=1, ensure_ascii=False)
    for x in runs:
        print(x)


if __name__ == "__main__":
    main()
