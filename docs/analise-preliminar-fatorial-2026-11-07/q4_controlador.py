import json,collections,sys,statistics as st,re
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
ORD=[('ASYNC-F2',b) for b in ('PO','PL','AO','AL')]+[('ASYNC-F3','A'),('ASYNC-F3','B'),('ASYNC-none','A'),('ASYNC-none','B')]
def rs_(v): 
    v=[x for x in v if x is not None]
    return (len(v),round(min(v),2),round(st.median(v),2),round(max(v),2)) if v else None
for cel,br in ORD:
    rs=sorted([(k,r) for k,r in d.items() if r['celula']==cel and r['braco']==br])
    print('==',cel,br)
    reg=collections.Counter(); pol=collections.Counter(); acs=collections.Counter(); ent=[]; mot=collections.Counter(); ntr=[]; jan=[]; pf=collections.Counter()
    ap=[]; rv=[]; ativo=[]; eff=[]; guard=collections.Counter(); rota=collections.Counter(); desc=[]; gdes=[]; http=collections.Counter(); apdur=[]
    for k,r in rs:
        t=r['trilha_ctrl']
        for x,v in t['decisao_regra'].items(): reg[x]+=v
        for x,v in t['politicas'].items(): pol[x]+=v
        ntr.append(len(t['transicoes'])); jan.append(t['janelas_conservador']); pf[t['perfil_final']]+=1
        if t['primeira_conservador_s'] is not None: ent.append(t['primeira_conservador_s']); mot[tuple(t['motivos_entrada'])]+=1
        a_t=None
        for a in t['acoes']:
            acs[(a['tipo'],a['operacao'],a['origem'],a['regra'],a['resultado'])]+=1
            if a['operacao']=='APLICAR':
                ap.append(a['t_pedido_s']); a_t=a['t_pedido_s']
                if a['lido']:
                    m=re.search(r'efeito_ms=(\d+)',a['lido']); eff.append(int(m.group(1)) if m else None)
                    m=re.search(r'guarda=([a-z]+)',a['lido']); guard[m.group(1) if m else a['lido'][:40]]+=1
                apdur.append(a['duracao_ms'])
            if a['operacao']=='REVERTER':
                rv.append(a['t_pedido_s'])
                if a_t is not None: ativo.append(a['t_pedido_s']-a_t)
                if a['lido']:
                    m=re.search(r'rota=([a-z-]+)',a['lido']); rota[m.group(1) if m else a['lido'][:60]]+=1
                http[a['codigo_http']]+=1
        of=r['observaveis_fim'] or {}
        desc.append(of.get('eventos_descontados_sob_acao')); gdes.append(of.get('guarda_ac7_desfazeres'))
    print(' transicoes por execucao',ntr,'perfil final',dict(pf))
    print(' 1a entrada CONSERVADOR (s desde start ou origem)',rs_(ent),'motivos',dict(mot))
    print(' janelas CONSERVADOR por exec',rs_(jan),'decisoes por regra',dict(reg))
    print(' politicas',dict(pol))
    print(' acoes',dict(acs))
    print(' APLICAR t',rs_(ap),'dur ms',rs_(apdur),'efeito_ms',rs_(eff),'guarda',dict(guard))
    print(' REVERTER t',rs_(rv),'tempo ativo',rs_(ativo),'rota',dict(rota),'http',dict(http))
    print(' descontados sob acao',desc,'guarda desfazeres',gdes)
