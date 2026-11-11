import json,collections,sys,statistics as st
from scipy.stats import beta
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
def cp(x,n): return (0.0 if x==0 else beta.ppf(.025,x,n-x+1), 1.0 if x==n else beta.ppf(.975,x+1,n-x))
for br in ('PO','PL','AO','AL'):
    rs=[r for r in d.values() if r['celula']=='ASYNC-F2' and r['braco']==br]
    f1=[]; f1_perda=0; perda_nao_f1=0; tf1=[]; tf2=[]; antes_ac7=0; depois_ac7=0
    for r in rs:
        st0=r['marcadores']['inicio']; perda=set(r['fusao_w5e']['sids_perda'])
        ap=[a['t_pedido_s'] for a in r['trilha_ctrl']['acoes'] if a['operacao']=='APLICAR']
        ap=ap[0] if ap else None
        for p in r['v_detalhe']['pares_lista_compacta']:
            if not p['conta'] or p['classe']!='I1': continue
            # instante do logout: t_atribuido das deteccoes por invalidacao, ou t da imediata menos ~2 s (nao exato)
            tl=[x['t_atribuido_ns'] for x in p['deteccoes'] if x['atribuicao']=='invalidacao']
            ti=[x['t_ns'] for x in p['deteccoes'] if x['tipo']=='imediata']
            t=((min(tl)-st0)/1e9) if tl else (((min(ti)-st0)/1e9) if ti else None)
            if p['forma']=='forma1':
                f1.append(p['chave']); tf1.append(t)
                if p['chave'] in perda: f1_perda+=1
                if ap is not None and t is not None:
                    if t<ap: antes_ac7+=1
                    else: depois_ac7+=1
            elif p['forma']=='forma2': tf2.append(t)
        perda_nao_f1+=len(perda-set(f1))
    tf1=[x for x in tf1 if x is not None]; tf2=[x for x in tf2 if x is not None]
    print(br,'forma1',len(f1),'-> perda',f1_perda,'perda fora da forma1',perda_nao_f1,'| t forma1 (s desde start) min/med/max',round(min(tf1),1),round(st.median(tf1),1),round(max(tf1),1),'| forma1 antes/depois do AC-7 (bracos ativos):',antes_ac7,depois_ac7)
    print('    t forma2 (deteccao imediata, s desde start): min/med/max',round(min(tf2),1),round(st.median(tf2),1),round(max(tf2),1), 'n',len(tf2),'n>=42s',sum(1 for x in tf2 if x>=42))
print('CP 20/21',cp(20,21),'0/26',cp(0,26),'1/24',cp(1,24),'0/16',cp(0,16),'0/8',cp(0,8),'8/8',cp(8,8),'2/4',cp(2,4),'0/4',cp(0,4),'1/48',cp(1,48),'0/24',cp(0,24))
