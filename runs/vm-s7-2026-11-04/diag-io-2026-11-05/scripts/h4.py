import re, sys, datetime as dt
# uso: h4.py arquivo indice_segmento_io
def load(path, seg):
    rows=[]
    for l in open(path):
        parts=l.split('|')
        t=float(parts[0].split()[0])
        s=parts[seg] if seg>0 else parts[0]
        m=re.search(r'full avg10=\S+ avg60=\S+ avg300=\S+ total=(\d+)', s)
        rows.append((t,int(m.group(1))))
    return rows
def hh(t): return dt.datetime.fromtimestamp(t,dt.timezone.utc).strftime('%H:%M:%S')
def wins(rows):
    out=[];j=0
    for i in range(len(rows)):
        while rows[i][0]-rows[j][0]>10: j+=1
        out.append(((rows[i][1]-rows[j][1])/1e6,rows[i][0]))
    return out
def pct(v,p):
    v=sorted(v); return v[min(len(v)-1,int(p*len(v)))]
def resumo(nome,rows):
    w=wins(rows); vals=[a for a,_ in w]
    gaps=[(rows[i][0]-rows[i-1][0],rows[i][0]) for i in range(1,len(rows))]
    mg=max(gaps)
    dur=rows[-1][0]-rows[0][0]
    print(f"== {nome}: n={len(rows)} {hh(rows[0][0])}-{hh(rows[-1][0])} dur={dur:.0f}s io_full_total={(rows[-1][1]-rows[0][1])/1e6:.2f}s")
    print(f"   maior intervalo entre amostras={mg[0]:.3f}s ({hh(mg[1])}); >=1.5s:{sum(g>=1.5 for g,_ in gaps)} >=3s:{sum(g>=3 for g,_ in gaps)}")
    print(f"   janela10s: max={max(w)[0]:.2f} ({hh(max(w)[1])}) p50={pct(vals,.5):.3f} p90={pct(vals,.9):.3f} p99={pct(vals,.99):.3f}")
    for thr in (0.8,1.0,1.1,1.5,2.0,3.0):
        print(f"   janelas >= {thr}: {sum(a>=thr for a in vals)}",end='')
    print()
    # janelas de 300 s deslizantes: descartada se alguma janela 10s (terminando dentro) >= thr ou intervalo>=3
    for thr in (1.0,1.5,2.0):
        tot=0;bad=0
        for i in range(len(rows)):
            t0=rows[i][0]
            if t0+300>rows[-1][0]: break
            tot+=1
            ruim=False
            for k in range(i,len(rows)):
                if rows[k][0]>t0+300: break
                if w[k][0]>=thr and rows[k][0]-10>=t0-0.01: ruim=True;break
                if k>i and rows[k][0]-rows[k-1][0]>=3: ruim=True;break
            bad+=ruim
        if tot: print(f"   300s deslizantes thr={thr}: {bad}/{tot} = {bad/tot:.1%} descartadas")
    return w
if __name__=='__main__':
    for a in sys.argv[1:]:
        p,seg,nome=a.split('::')
        resumo(nome,load(p,int(seg)))
