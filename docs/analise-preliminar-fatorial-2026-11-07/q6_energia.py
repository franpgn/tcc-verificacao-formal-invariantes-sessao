import json,collections,sys,statistics as st
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
ORD=[('ASYNC-F2',b) for b in ('PO','PL','AO','AL')]+[('ASYNC-F3','A'),('ASYNC-F3','B'),('ASYNC-none','A'),('ASYNC-none','B')]
def m(v): v=[x for x in v if x is not None]; return (round(st.mean(v),2), len(v)) if v else (None,0)
erros=[k for k,r in d.items() if 'erro' in r['energia']]; print('erros energia',erros)
for cel,br in ORD:
    rs=[r for r in d.values() if r['celula']==cel and r['braco']==br]
    sel=('antes',) if cel=='ASYNC-none' else ('durante','recuperacao')
    out={}
    for f in ('cg:isn1','cg:isn2','cg:isn3','pid:workload','vm'):
        v=[]
        for r in rs:
            c=r['energia']['cpu_fontes'].get(f,{})
            x=[c.get(s) for s in sel if s in c]
            v.append(None if any(z is None for z in x) else sum(x))
        out[f]=m(v)
    dur=m([sum(fz['duracao_s'] for fz in r['energia']['fases'] if fz['rotulo'] in sel) for r in rs])
    uteis=m([sum(r['energia']['uteis'].get(s,0) for s in sel) for r in rs])
    e03=m([None if any(r['energia']['e_vm']['0.3'].get(s,{}).get('e_total_j') is None for s in sel if s in r['energia']['e_vm']['0.3']) else sum(r['energia']['e_vm']['0.3'][s]['e_total_j'] for s in sel if s in r['energia']['e_vm']['0.3']) for r in rs])
    e05=m([None if any(r['energia']['e_vm']['0.5'].get(s,{}).get('e_total_j') is None for s in sel if s in r['energia']['e_vm']['0.5']) else sum(r['energia']['e_vm']['0.5'][s]['e_total_j'] for s in sel if s in r['energia']['e_vm']['0.5']) for r in rs])
    jpu=m([None if any(r['energia']['e_vm']['0.3'].get(s,{}).get('e_total_j') is None for s in sel if s in r['energia']['e_vm']['0.3']) else 1000*sum(r['energia']['e_vm']['0.3'][s]['e_total_j'] for s in sel if s in r['energia']['e_vm']['0.3'])/max(1,sum(r['energia']['uteis'].get(s,0) for s in sel)) for r in rs])
    cpu_pu=m([None if out and any(r['energia']['cpu_fontes']['vm'].get(s) is None for s in sel if s in r['energia']['cpu_fontes']['vm']) else 1000*sum(r['energia']['cpu_fontes']['vm'][s] for s in sel if s in r['energia']['cpu_fontes']['vm'])/max(1,sum(r['energia']['uteis'].get(s,0) for s in sel)) for r in rs])
    stl=[r['energia']['steal']['frac'] for r in rs]; exc=[r['energia']['steal']['excluida'] for r in rs]
    lac=collections.Counter((f,s) for r in rs for f,c in r['energia']['cpu_fontes'].items() for s,v in c.items() if v is None and f!='coletor')
    print('==',cel,br,'fases',sel,'dur',dur,'uteis',uteis)
    print('  CPU-s (media por exec):',{k:v[0] for k,v in out.items()},'n validos',{k:v[1] for k,v in out.items()})
    print('  vm CPU-s/1000 uteis',cpu_pu,'E_total vm 0.3 J',e03,'0.5 J',e05,'J/1000 uteis (0.3)',jpu)
    print('  steal max',max(x for x in stl if x is not None) if any(x is not None for x in stl) else None,'excluidas',collections.Counter(exc),'lacunas',dict(lac))
