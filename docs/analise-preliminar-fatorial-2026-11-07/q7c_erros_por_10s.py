import csv,sys,collections
B='C:/Users/Francesco/Development/tcc/dados-vm/cs3-2026-11/fat-nucleo-previa-20261107T150934Z/campanha-s3-fatorial'
for ex in sys.argv[1:]:
    d=B+'/'+ex
    mk={}
    for l in open(d+'/marcadores.txt'):
        t=dict(x.split('=',1) for x in l.split()); mk[t['event']]=int(t['t_epoch_ns'])
    s=mk['start']
    c=collections.defaultdict(collections.Counter)
    for x in csv.reader(open(d+'/rep-001.csv',encoding='utf-8')):
        if x[0]=='op_id' or x[1]=='ReplayValidate': continue
        t=(int(x[2])-s)/1e9
        if -5<t<200:
            b=int(t//10)*10
            c[b]['ev_'+x[4][-5:]]+=1
            if x[5].startswith('ERROR'): c[b]['err_'+x[4][-5:]+'_'+x[5][6:9]]+=1
    print(ex,'end at',(mk['end']-s)/1e9)
    for b in sorted(c): print('  ',b,dict(sorted(c[b].items())))
