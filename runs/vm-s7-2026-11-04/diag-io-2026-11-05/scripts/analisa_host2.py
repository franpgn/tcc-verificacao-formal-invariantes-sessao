import csv, sys

S = sys.argv[1]
r = list(csv.reader(open(S + '/host-io-2.csv', encoding='utf-8-sig')))
h, data = r[0], r[1:]
print("amostras host:", len(data), "de", data[0][0], "a", data[-1][0])
BS = chr(92)


def col(sub):
    return [i for i, x in enumerate(h) if all(s.lower() in x.lower() for s in sub)]


grupos = [
    (["Virtual Storage Device", BS + "Latency"], lambda x: x > 500000),
    (["Virtual Storage Device", "Queue Length"], lambda x: x > 5),
    (["PhysicalDisk", "sec/Write"], lambda x: x > 0.5),
    (["PhysicalDisk", "sec/Read"], lambda x: x > 0.5),
    (["PhysicalDisk", "Current Disk Queue"], lambda x: x > 5),
    (["Virtual Storage Device", "Error Count"], lambda x: False),
    (["MsMpEng", "Processor"], lambda x: x > 50),
    (["Guest Run Time"], lambda x: False),
]
for sub, alto_f in grupos:
    for c in col(sub):
        v = []
        for d in data:
            try:
                v.append((float(d[c]), d[0]))
            except ValueError:
                pass
        if not v:
            continue
        v2 = sorted(x for x, _ in v)
        mx = max(v)
        altos = [t[11:19] for x, t in v if alto_f(x)]
        nome = BS.join(h[c].split(BS)[-2:])[-80:]
        print(f"{nome}: max={mx[0]:.4g} ({mx[1][11:19]}) p99={v2[int(.99 * len(v2)) - 1]:.4g} "
              f"med={v2[len(v2) // 2]:.4g} picos={len(altos)} {altos[:6]}")
