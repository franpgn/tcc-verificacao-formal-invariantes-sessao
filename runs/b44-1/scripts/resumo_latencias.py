"""B-44.1: resume chamadas.tsv (latencia por tipo de chamada administrativa, REST e CLI).

Uso: python -I scripts/resumo_latencias.py chamadas.tsv > latencias.txt
Colunas de chamadas.tsv: utc, rotulo, metodo, url|comando, http|exit, time_total_s, auth, corpo.
"""
import re
import statistics
import sys
from collections import defaultdict


def tipo(metodo, url):
    if metodo == "CLI":
        m = re.search(r": (\w+(?: \w+)?)", url)
        return "CLI " + (m.group(1) if m else "?")
    m = re.search(r"action=([\w-]+)", url)
    if m:
        return f"{metodo} action={m.group(1)}"
    if "/logging/" in url:
        return f"{metodo} logging"
    return f"{metodo} {re.sub(r'http://127.0.0.1:\d+', '', url).split('?')[0][:40]}"


def main(caminho):
    grupos = defaultdict(list)
    for linha in open(caminho, encoding="utf-8"):
        c = linha.rstrip("\n").split("\t")
        if len(c) < 6:
            continue
        try:
            t = float(c[5])
        except ValueError:
            continue
        if c[4] in ("000",):
            continue  # chamada nao enviada (no parado ou erro local)
        grupos[(tipo(c[2], c[3]), c[4])].append(t * 1000.0)
    print("tipo\tresultado\tn\tmediana_ms\tmin_ms\tmax_ms")
    for (k, r), v in sorted(grupos.items()):
        print(f"{k}\t{r}\t{len(v)}\t{statistics.median(v):.1f}\t{min(v):.1f}\t{max(v):.1f}")


if __name__ == "__main__":
    main(sys.argv[1])
