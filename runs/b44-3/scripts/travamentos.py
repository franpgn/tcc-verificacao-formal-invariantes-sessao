"""B-44.3: procura travamentos do ambiente na sonda REST: segundos em que as amostras dos TRES nos (inclusive o
isolado) levaram > 2,5 s. Um travamento simultaneo no isn3 isolado e na maioria nao vem do JGroups.
Uso: python -I travamentos.py RAIZ"""
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analisa  # noqa: E402

analisa.RAIZ = sys.argv[1] if len(sys.argv) > 1 else "."
print("rot\tinicio_utc_ms\tseg_desde_t0\tduracao_max_ms_por_no")
for rot, f in analisa.fases().items():
    sd = analisa.sonda(rot)
    if not sd or rot in analisa.INVALIDAS:
        continue
    lentas = collections.defaultdict(dict)
    for c in sd:
        d = int(c[1]) - int(c[0])
        if d > 2500:
            lentas[int(c[0]) // 5000][c[2]] = max(d, lentas[int(c[0]) // 5000].get(c[2], 0))
    for b, nos in sorted(lentas.items()):
        if len(nos) == 3:
            t0 = f.get("t0") or 0
            print("%s\t%d\t%.1f\t%s" % (rot, b * 5000, (b * 5000 - t0) / 1000 if t0 else float("nan"), nos))
