import csv,sys,collections
B='C:/Users/Francesco/Development/tcc/dados-vm/cs3-2026-11/fat-nucleo-previa-20261107T150934Z/campanha-s3-fatorial'
for ex in sys.argv[1:]:
    d=B+'/'+ex
    mk={}
    for l in open(d+'/marcadores.txt'):
        t=dict(x.split('=',1) for x in l.split()); mk[t['event']]=int(t['t_epoch_ns'])
    s=mk['start']
    lo=collections.Counter(); rv=collections.Counter(); sids3=set(); rvs=collections.Counter()
    rows=list(csv.reader(open(d+'/rep-001.csv',encoding='utf-8')))[1:]
    for x in rows:
        t=(int(x[2])-s)/1e9
        if x[1]=='Logout' and 50<t<140:
            lo[(x[4][-5:],x[5])]+=1
            if x[4].endswith('11224') and x[5]=='NONE': sids3.add(x[6])
    for x in rows:
        if x[1]=='ReplayValidate' and x[6] in sids3:
            t=(int(x[2])-s)/1e9
            rvs[(x[4][-5:],x[5],'tardia' if t>240 else 'imediata')]+=1
    print(ex,'Logouts 50-140s por (replica,rc)',dict(lo)); print('   replay das sids com logout no isn3',dict(rvs))
