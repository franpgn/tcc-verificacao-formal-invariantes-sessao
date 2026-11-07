import json,collections,sys
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
for k,r in sorted(d.items()):
    if r['celula']!='ASYNC-F2': continue
    v=r['v_detalhe']; st=r['marcadores']['inicio']
    for p in v['pares_lista_compacta']:
        if p['classe']!='I1' or p['forma'] in ('outra',) or any(x['tipo']=='online' for x in p['deteccoes']) or p['classe']!='I1':
            dets=[(x['tipo'],x['forma'],round((x['t_ns']-st)/1e9,1) if x['t_ns'] else None, x['fase_atribuida'], x['replica_leitura']) for x in p['deteccoes']]
            print(k,r['bloco'],p['classe'],p['forma'],p['conta'],p['chave'][:8],dets[:6], len(dets))
    for p in v['pares_lista_compacta']:
        if p['classe']!='I1':
            print('  NAO-I1',k,p)
