import json,collections,sys,statistics as st,re
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
p1=collections.defaultdict(list); det=collections.defaultdict(list); alvo=collections.Counter(); gap=collections.defaultdict(list)
motivos4005=collections.Counter()
for k,r in sorted(d.items()):
    if r['celula']!='ASYNC-F2': continue
    t=r['trilha_ctrl']; st0=r['marcadores']['inicio']; br=r['braco']
    for p in t['politicas_lista']:
        if p['politica']=='P1': alvo[p['no']]+=1
    ap=[a for a in t['acoes'] if a['operacao']=='APLICAR']
    rv=[a for a in t['acoes'] if a['operacao']=='REVERTER']
    p1[br].append(ap[0]['t_pedido_s'])
    gap[br].append(ap[0]['t_pedido_s']-t['primeira_conservador_s'])
    l=r['stdout'].get('hotrod_4005','')
    for m in re.finditer(r'motivo=(\S+) n=(\d+) t_primeira_ns=(\d+)',l):
        motivos4005[(br,m.group(1))]+=int(m.group(2))
        if m.group(1)=='ISPN000306': det[br].append((int(m.group(3))-st0)/1e9)
    if any('rota=maioria' in (a['lido'] or '') for a in rv) or any(x['regra']=='R1' for x in rv):
        print('ESPECIAL',k,[(a['operacao'],a['regra'],a['origem'],round(a['t_pedido_s'],2),a['lido']) for a in t['acoes']])
        print('   politicas',t['politicas_lista'])
for br in ('PO','PL','AO','AL'):
    v=sorted(round(x,2) for x in p1[br]); print(br,'P1 t',v)
    print('   entrada->P1',sorted(round(x,2) for x in gap[br]))
    v=sorted(round(x,1) for x in det[br]); print('   1o ISPN000306 (cliente) desde start',v)
print('alvo P1',alvo)
print(motivos4005)
