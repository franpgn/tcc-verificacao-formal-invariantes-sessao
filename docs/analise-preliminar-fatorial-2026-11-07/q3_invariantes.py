import json,collections,sys
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
ORD=[('ASYNC-F2',b) for b in ('PO','PL','AO','AL')]+[('ASYNC-F3','A'),('ASYNC-F3','B'),('ASYNC-none','A'),('ASYNC-none','B')]
for cel,br in ORD:
    rs=[r for r in d.values() if r['celula']==cel and r['braco']==br]
    lin=collections.Counter(); keys=collections.Counter(); kf=collections.Counter()
    for r in rs:
        for k,v in r['viol_linhas'].items():
            inv,fase,orig,cl=k.split('|'); lin[(inv,fase,orig)]+=v
        for k,v in r['viol_chaves'].items(): keys[k]+=v
        for k,v in r['viol_chaves_fase'].items(): kf[k]+=v
    print('==',cel,br,'n',len(rs))
    inv_tot=collections.Counter()
    for (inv,fase,orig),v in lin.items(): inv_tot[inv]+=v
    print(' linhas por invariante',dict(inv_tot))
    print(' chaves distintas por invariante (soma das execucoes)',dict(keys))
    print(' linhas por (inv,fase,origem)',sorted(lin.items()))
    print(' chaves por (inv,fase)',sorted(kf.items()))
    # V
    tp=collections.Counter(); fo=collections.Counter(); po=collections.Counter(); mult=0; tl=0; sem_f2=0; atr=collections.Counter()
    fase_par=collections.Counter(); tipo_par=collections.Counter()
    for r in rs:
        v=r['v_detalhe']
        for k,x in v['pares'].items(): tp[k]+=x
        for k,x in v['formas_i1'].items(): fo[k]+=x
        for k,x in v['por_origem'].items(): po[k+'_pares']+=x['pares']; po[k+'_linhas']+=x['linhas']
        mult+=v['pares_multiplas_origens']; tl+=v['total_linhas']; sem_f2+=(v['total_pares_sem_forma2'] or 0)
        for k,x in v['atribuicao'].items(): atr[k]+=x
        for p in v['pares_lista_compacta']:
            if not p['conta']: 
                fase_par['NAO_CONTA']+=1; continue
            fs=sorted(set(x['fase_atribuida'] or 'sem' for x in p['deteccoes'] if x['conta']))
            fase_par['/'.join(fs)]+=1
            tipo_par['+'.join(sorted(set(x['tipo'] for x in p['deteccoes'] if x['conta'])))]+=1
    print(' V pares',dict(tp),'total',sum(tp.values()),'linhas',tl,'sem_forma2',sem_f2,'formas',dict(fo))
    print(' por origem',dict(po),'pares em >1 origem',mult,'atribuicao',dict(atr))
    print(' fase dos pares (deteccoes que contam)',dict(fase_par))
    print(' instrumentos por par',dict(tipo_par))
    vr=collections.Counter()
    for r in rs:
        for k,x in r['varredura'].items(): vr[k]+=x
    print(' varredura',dict(vr))
    if cel=='ASYNC-F2':
        pp=sum(len(r['fusao_w5e']['sids_perda']) for r in rs); ex=sum(len(r['fusao_w5e']['sids_exposicao']) for r in rs)
        both=sum(len(set(r['fusao_w5e']['sids_perda'])&set(r['fusao_w5e']['sids_exposicao'])) for r in rs)
        print(' perda',pp,'exposicao',ex,'em ambas',both)
    print(' n_acoes_sel',sum(r['n_acoes_sel'] for r in rs))
