import re, sys, datetime as dt

rows = []
for linha in open(sys.argv[1]):
    t = float(linha.split()[0])
    m = re.search(r'full avg10=\S+ avg60=\S+ avg300=\S+ total=(\d+)', linha)
    if m:
        rows.append((t, int(m.group(1))))


def hhmmss(t):
    return dt.datetime.fromtimestamp(t, dt.timezone.utc).strftime('%H:%M:%S')


gaps = [(rows[i][0] - rows[i - 1][0], rows[i][0]) for i in range(1, len(rows)) if rows[i][0] - rows[i - 1][0] > 2]
print("VM amostras:", len(rows), "de", hhmmss(rows[0][0]), "a", hhmmss(rows[-1][0]))
print("lacunas > 2 s:", len(gaps), [(round(g, 1), hhmmss(t)) for g, t in gaps][:10])
janelas = []
j = 0
for i in range(len(rows)):
    while rows[i][0] - rows[j][0] > 10:
        j += 1
    janelas.append(((rows[i][1] - rows[j][1]) / 1e6, rows[i][0]))
pior = sorted(janelas, reverse=True)[:5]
print("piores janelas de 10 s, io full (s):", [(round(a, 2), hhmmss(t)) for a, t in pior])
print("janelas > 1,5 s:", sum(1 for a, _ in janelas if a > 1.5), "| > 0,8 s:", sum(1 for a, _ in janelas if a > 0.8))
print("io full total no período (s):", round((rows[-1][1] - rows[0][1]) / 1e6, 2))
