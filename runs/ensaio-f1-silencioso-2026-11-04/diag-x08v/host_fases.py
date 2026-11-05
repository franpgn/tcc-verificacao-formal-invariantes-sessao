import sys, csv, datetime as dt
f = sys.argv[1]
rows = list(csv.reader(open(f, encoding='utf-8', errors='replace')))
h = rows[0]
def col(sub):
    return [i for i, c in enumerate(h) if sub in c]
iLat = col('avhdx)\\Latency')[0]; iQ = col('avhdx)\\Queue Length')[0]; iW = col('avhdx)\\Write Count')[0]; iF = col('avhdx)\\Flush Count')[0]
iPW = col('PhysicalDisk(0 C:)\\Avg. Disk sec/Write')[0]; iPR = col('PhysicalDisk(0 C:)\\Avg. Disk sec/Read')[0]
iPQ = col('PhysicalDisk(0 C:)\\Current Disk Queue')[0]; iPI = col('PhysicalDisk(0 C:)\\% Idle')[0]
iMs = col('MsMpEng)\\% Processor')[0]; iMb = col('MsMpEng)\\IO Data')[0]; iG = col('_Total)\\% Guest Run')[0]
def fl(x):
    try: return float(x)
    except: return None
R = []
for r in rows[1:]:
    try: t = dt.datetime.strptime(r[0], '%m/%d/%Y %H:%M:%S.%f') + dt.timedelta(hours=3)
    except: continue
    R.append((t, r))
print('host: %s .. %s UTC, %d amostras' % (R[0][0].time(), R[-1][0].time(), len(R)))
def show(a, b):
    print('\n%s..%s  t  lat_avhdx_ms Q_avhdx dWrites dFlush | C: ms/w ms/r Q idle%% | MsMpEng%% MsMpEngMB/s' % (a, b))
    prev = None
    for t, r in R:
        s = t.strftime('%H:%M:%S')
        if a <= s <= b:
            w = fl(r[iW]); fz = fl(r[iF])
            dw = (w - prev[0]) if prev and w is not None and prev[0] is not None else None
            df = (fz - prev[1]) if prev and fz is not None and prev[1] is not None else None
            pw = fl(r[iPW]); pr = fl(r[iPR]); mb = fl(r[iMb])
            print('  %s %6s %4s %6s %4s | %7s %7s %3s %5s | %5s %6s' % (s, r[iLat], r[iQ], dw, df,
                  '%.1f' % (pw*1000) if pw is not None else '-', '%.1f' % (pr*1000) if pr is not None else '-',
                  r[iPQ], (r[iPI] or '')[:5], (r[iMs] or '')[:5], '%.1f' % (mb/1e6) if mb is not None else '-'))
        prev = (fl(r[iW]), fl(r[iF]))
def summ(a, b, nome):
    xs = [(t, r) for t, r in R if a <= t.strftime('%H:%M:%S') <= b]
    lat = [fl(r[iLat]) for t, r in xs if fl(r[iLat]) is not None]
    pw = [fl(r[iPW]) for t, r in xs if fl(r[iPW]) is not None]
    idle = [fl(r[iPI]) for t, r in xs if fl(r[iPI]) is not None]
    print('%-34s %s..%s n=%d lat_avhdx max=%s med=%.1f | C: ms/w max=%.1f | idle min=%.1f' % (nome, a, b, len(xs),
          max(lat) if lat else '-', sorted(lat)[len(lat)//2] if lat else -1, max(pw)*1000 if pw else -1, min(idle) if idle else -1))
for a, b, n in [('03:02:03', '03:06:28', 'n=1,2 sem carga'), ('03:07:40', '03:08:58', 'n=3 anc..antes do episodio'),
                ('03:08:59', '03:09:21', 'n=3 EPISODIO (psi)'), ('03:09:22', '03:12:40', 'n=3 resto da janela'),
                ('03:12:40', '03:13:00', 'n=3 flush+gzip'), ('03:14:10', '03:19:10', 'n=4 anc..anc+300'),
                ('03:19:10', '03:19:36', 'n=4 flush+gzip'), ('03:20:05', '03:21:20', 'pos-ensaio (episodios psi)')]:
    summ(a, b, n)
for a, b in sys.argv[2:]: pass
show('03:08:50', '03:09:30')
show('03:09:48', '03:10:06')
show('03:19:24', '03:19:38')
show('03:20:05', '03:20:33')
