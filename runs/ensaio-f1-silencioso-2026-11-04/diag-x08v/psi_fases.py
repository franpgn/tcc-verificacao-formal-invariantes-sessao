import sys, re, datetime as dt
psi = sys.argv[1]
T = []
for ln in open(psi):
    p = ln.split()
    t = float(p[0])
    tots = re.findall(r'total=(\d+)', ln)
    T.append((t, int(tots[1]), int(tots[0]), int(tots[2])))  # t, io_full, io_some, cpu_some
def utc(t): return dt.datetime.fromtimestamp(t, dt.timezone.utc).strftime('%H:%M:%S')
# janelas de 10 s (def. C3 3.2)
W = []
j = 0
for i in range(len(T)):
    while T[i][0] - T[j][0] > 10: j += 1
    W.append((T[i][0], (T[i][1] - T[j][1]) / 1e6))
gaps = max(T[i][0]-T[i-1][0] for i in range(1, len(T)))
print('amostras', len(T), utc(T[0][0]), utc(T[-1][0]), 'maior intervalo %.3f' % gaps)
print('io full total %.2f s' % ((T[-1][1]-T[0][1])/1e6))
print('janelas>=1.5', sum(1 for w in W if w[1] >= 1.5), '>1.5', sum(1 for w in W if w[1] > 1.5), '>0.8', sum(1 for w in W if w[1] > 0.8))
pk = max(W, key=lambda w: w[1]); print('pico %.2f em %s' % (pk[1], utc(pk[0])))
# fases (ms epoch)
ex = {
 1: dict(disp=1793847786352, start=1793847846673, volta=1793847846673+5208, fim=dt.datetime(2026,11,5,3,4,13,tzinfo=dt.timezone.utc).timestamp()*1000, go=dt.datetime(2026,11,5,3,2,3,tzinfo=dt.timezone.utc).timestamp()*1000),
 2: dict(disp=1793847920551, start=1793847981108, volta=1793847981108+5358, fim=dt.datetime(2026,11,5,3,6,28,tzinfo=dt.timezone.utc).timestamp()*1000, go=dt.datetime(2026,11,5,3,4,18,tzinfo=dt.timezone.utc).timestamp()*1000),
 3: dict(go=dt.datetime(2026,11,5,3,6,33,tzinfo=dt.timezone.utc).timestamp()*1000, lanc=1793848056509, anc=1793848060811, disp=1793848109820, start=1793848170156, volta=1793848170156+13594, fim=dt.datetime(2026,11,5,3,13,0,tzinfo=dt.timezone.utc).timestamp()*1000),
 4: dict(go=dt.datetime(2026,11,5,3,13,4,tzinfo=dt.timezone.utc).timestamp()*1000, lanc=1793848447384, anc=1793848450889, disp=1793848499893, start=1793848560481, volta=1793848560481+10454, fim=dt.datetime(2026,11,5,3,19,36,tzinfo=dt.timezone.utc).timestamp()*1000),
}
def io(a, b):
    xs = [x for x in T if a <= x[0] <= b]
    return (xs[-1][1]-xs[0][1])/1e6 if len(xs) > 1 else 0
def wins(a, b, excl=None):
    ws = [w for w in W if a <= w[0] <= b and not (excl and excl[0] <= w[0] <= excl[1])]
    if not ws: return (0, 0, None)
    m = max(ws, key=lambda w: w[1])
    return (sum(1 for w in ws if w[1] >= 1.5), m[1], utc(m[0]))
for n, e in ex.items():
    for k in e: e[k] /= 1000
    print('\n== n=%d' % n, {k: utc(v) for k, v in e.items()})
    if 'anc' in e:
        a0, a1 = e['anc']+44, e['anc']+300
        print(' janela h4 %s..%s' % (utc(a0), utc(a1)))
        seg = (e['start']-2, e['start']+60)
        print(' io full na janela h4 %.2f s; janelas>=1.5 / pior: %s' % (io(a0, a1), wins(a0, a1)))
        print(' fora do segmento F1 [%s..%s]: %s' % (utc(seg[0]), utc(seg[1]), wins(a0, a1, seg)))
        print(' dentro do segmento: %s' % (wins(*seg),))
        # h4b: maior intervalo na janela
        xs = [x for x in T if a0 <= x[0] <= a1]
        print(' maior intervalo na janela h4 %.3f' % max(xs[i][0]-xs[i-1][0] for i in range(1, len(xs))))
        fases = [('go..lanc', e['go'], e['lanc']), ('lanc..anc', e['lanc'], e['anc']), ('anc..anc+44', e['anc'], e['anc']+44),
                 ('anc+44..disp', e['anc']+44, e['disp']), ('disp..start', e['disp'], e['start']), ('start..volta', e['start'], e['volta']),
                 ('volta..anc+300', e['volta'], e['anc']+300), ('anc+300..fim (flush csv, verif, awk, c1c3, gzip)', e['anc']+300, e['fim'])]
    else:
        fases = [('go..disp', e['go'], e['disp']), ('disp..start', e['disp'], e['start']), ('start..volta', e['start'], e['volta']), ('volta..fim', e['volta'], e['fim'])]
    for nm, a, b in fases:
        c, m, tm = wins(a, b)
        print('  %-50s %s..%s io_full=%.2f s  jan>=1.5=%d pior=%.2f@%s' % (nm, utc(a), utc(b), io(a, b), c, m, tm))
# lista de episodios (trechos continuos >=1.5)
print('\nepisodios (janela >= 1.5 s):')
ep = None
for t, v in W:
    if v >= 1.5:
        if ep is None: ep = [t, t, v]
        else: ep[1] = t; ep[2] = max(ep[2], v)
    elif ep:
        print('  %s..%s pico %.2f' % (utc(ep[0]), utc(ep[1]), ep[2])); ep = None
if ep: print('  %s..%s pico %.2f' % (utc(ep[0]), utc(ep[1]), ep[2]))
# io full por segundo > 0.3
print('\nsegundos com io full > 0.3 s:')
for i in range(1, len(T)):
    d = (T[i][1]-T[i-1][1])/1e6
    if d > 0.3: print('  %s %.2f' % (utc(T[i][0]), d))
