import json,collections,statistics as st,sys
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
def resumo(v):
    v=[x for x in v if x is not None]
    return (len(v), round(min(v),3), round(st.median(v),3), round(max(v),3)) if v else None
for c in ('ASYNC-F2','ASYNC-F3'):
    rs=[r for r in d.values() if r['celula']==c]
    st_off=[(r['marcadores']['inicio']-r['ancora_ms']*10**6)/1e9 for r in rs]
    dur=[(r['marcadores']['fim']-r['marcadores']['inicio'])/1e9 for r in rs]
    print(c,'n',len(rs),'start-ancora',resumo(st_off),'inj_offset',resumo([r['inj_inicio_offset_s'] for r in rs]),'dur marcadores',resumo(dur),
      'injetor saida',resumo([r['injetor_saida_desde_ancora_s'] for r in rs]),'rede_pos',collections.Counter(str(r['rede_pos']) for r in rs))
    print('  falha_efetiva', collections.Counter(json.dumps(r['falha_efetiva'],sort_keys=True)[:200] for r in rs).most_common(3))
    if c=='ASYNC-F2':
        for br in ('PO','PL','AO','AL'):
            rb=[r for r in rs if r['braco']==br]
            print('  ',br,'ISPN93 desde end',resumo([r['fusao_vista_desde_end_s'] for r in rb]),'trilha',resumo([r['fusao_trilha_desde_end_s'] for r in rb]),'dif',resumo([r['fusao_trilha_dif_ispn_s'] for r in rb]),'checagem',resumo([r['fusao_checagem_desde_end_s'] for r in rb]),'no ispn',collections.Counter(r['fusao_vista_no'] for r in rb))
        print('  todos ISPN93',resumo([r['fusao_vista_desde_end_s'] for r in rs]),'trilha',resumo([r['fusao_trilha_desde_end_s'] for r in rs]))
        print('  t_fusao null', [k for k,r in d.items() if r['celula']==c and r['t_fusao_ns'] is None])
# jitter detail
r=[r for r in d.values() if r['celula']=='ASYNC-F3']
print(collections.Counter(json.dumps(x['falha_efetiva']) for x in r))
