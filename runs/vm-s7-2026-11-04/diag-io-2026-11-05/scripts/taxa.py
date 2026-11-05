import sys,random
sys.path.insert(0,sys.argv[1])
from h4 import load,wins
import math
def bad_windows(rows,L,thr=1.5,gap=3.0):
    w=wins(rows); tot=bad=0
    for i in range(len(rows)):
        t0=rows[i][0]
        if t0+L>rows[-1][0]: break
        tot+=1; ruim=False
        for k in range(i,len(rows)):
            if rows[k][0]>t0+L: break
            if w[k][0]>=thr: ruim=True;break
            if k>i and rows[k][0]-rows[k-1][0]>=gap: ruim=True;break
        bad+=ruim
    return bad,tot
ds=[(sys.argv[2],0),(sys.argv[3],0),(sys.argv[4],0)]
for L in (256,300):
    B=T=0
    for p,s in ds:
        b,t=bad_windows(load(p,s),L); B+=b;T+=t; print(L,p[-40:],b,t)
    print('pooled',L,B,T,round(B/T,3))
# modelo de Poisson
for ev,obs in ((1,71),(2,71)):
    lam=ev/obs
    for d in (0.4,):
        print('ev',ev,'p256=',round(1-math.exp(-lam*(256/60+d)),3))
# simulacao das regras de parada
def sim(p,n=60,R=20000,rule='A'):
    stop=0
    for _ in range(R):
        x=[random.random()<p for _ in range(n)]
        s=False
        if rule=='A': s=sum(x)>=3
        else:
            k,c=(4,3) if rule=='B' else (5,4)
            run=0
            for j in range(n):
                run=run+1 if x[j] else 0
                if run>=c: s=True;break
                if j>=11 and sum(x[j-11:j+1])>=k: s=True;break
        stop+=s
    return stop/R
random.seed(1)
for p in (0.06,0.07,0.12,0.15,0.25,0.6):
    print('p',p,{r:round(sim(p,rule=r),3) for r in 'ABC'})
