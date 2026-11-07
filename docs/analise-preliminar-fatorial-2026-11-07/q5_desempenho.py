import json,collections,sys,statistics as st
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
ORD=[('ASYNC-F2',b) for b in ('PO','PL','AO','AL')]+[('ASYNC-F3','A'),('ASYNC-F3','B'),('ASYNC-none','A'),('ASYNC-none','B')]
def m(v): v=[x for x in v if x is not None]; return round(st.mean(v),4) if v else None
for cel,br in ORD:
    rs=[r for r in d.values() if r['celula']==cel and r['braco']==br]
    y=lambda n: [r['desfechos'][n]['y'] for r in rs if n in r['desfechos']]
    ev=lambda f,k: [r['eventos_por_fase'].get(f,{}).get(k,0) for r in rs]
    acf=lambda f,k: [r['acoes_por_fase'].get(f,{}).get(k,0) for r in rs]
    print('==',cel,br,'n',len(rs))
    print('  media y: V',m(y('taxa_violacoes_por_1000')),'p99',m(y('acao_s3_p99_com_espera')),'vazao',m(y('vazao_util')),'taxa_erro',m(y('taxa_erro')),'falhas/1000',m(y('falhas_percebidas_por_1000')),'drec',m(y('delta_rec_s')),'dconv',m(y('delta_conv_s')))
    print('  acoes sel (soma)',sum(r['n_sel'] for r in rs),'acoes >=1,4 s (soma)',sum(r['n_lat_ge_1400'] for r in rs), 'p99 por exec', sorted(round(r['lat_sel']['p99']) for r in rs))
    for f in ('antes','durante','falha_0_42s','ac7_ativo','recuperacao','pos-convergencia'):
        e=ev(f,'err'); n=ev(f,'ev'); e3=ev(f,'err|127.0.0.1:11224'); n3=ev(f,'ev|127.0.0.1:11224')
        if sum(n): print('  eventos %-12s soma ev %6d err %5d (isn3 %5d de %5d ev) media err/exec %.1f'%(f,sum(n),sum(e),sum(e3),sum(n3),st.mean(e)))
    for f in ('antes','durante','recuperacao','pos-convergencia','ac7_ativo'):
        a=acf(f,'acoes'); fp=acf(f,'falha_percebida')
        if sum(a): print('  acoes %-12s soma %6d falha_percebida %5d'%(f,sum(a),sum(fp)))
