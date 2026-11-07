import csv,json,re,sys,collections
B='C:/Users/Francesco/Development/tcc/dados-vm/cs3-2026-11/fat-nucleo-previa-20261107T150934Z/campanha-s3-fatorial'
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
tot=collections.defaultdict(collections.Counter); por_exec={}
for k,r in sorted(d.items()):
    if r['celula']!='ASYNC-F2': continue
    st0=r['marcadores']['inicio']; end=r['marcadores']['fim']; fus=r['t_fusao_ns']
    m=re.search(r'ISPN000306 n=\d+ t_primeira_ns=(\d+)',r['stdout'].get('hotrod_4005',''))
    det=int(m.group(1))
    ap=[a for a in r['trilha_ctrl']['acoes'] if a['operacao']=='APLICAR'][0]['t_pedido_ns']
    rv=[a for a in r['trilha_ctrl']['acoes'] if a['operacao']=='REVERTER'][0]['t_pedido_ns']
    c=collections.Counter()
    for x in csv.reader(open(B+'/'+k+'/rep-001.csv',encoding='utf-8')):
        if len(x)<7 or x[0]=='op_id' or x[1]=='ReplayValidate' or not x[4].endswith('11224'): continue
        t=int(x[2])
        if t<st0: continue
        w='a_0_P1' if t<ap else ('b_P1_det' if t<det else ('c_det_end' if t<=end else ('d_end_fus' if t<fus else None)))
        if w is None: continue
        ok = not x[5].startswith('ERROR')
        c[(w,x[1],'ok' if ok else 'err')]+=1
    por_exec[k]=c
    for kk,v in c.items(): tot[r['braco']][kk]+=v
for br in ('PO','PL','AO','AL'):
    print('==',br)
    for w in ('a_0_P1','b_P1_det','c_det_end','d_end_fus'):
        sub={(op,s):v for (ww,op,s),v in tot[br].items() if ww==w}
        okops={op:v for (op,s),v in sub.items() if s=='ok'}
        print('  ',w,'ok',sum(okops.values()),'err',sum(v for (op,s),v in sub.items() if s=='err'),'ok por op',okops)
lo=[(k[5:9],d[k]['braco'],c.get(('c_det_end','Logout','ok'),0),c.get(('d_end_fus','Logout','ok'),0)) for k,c in por_exec.items() if c.get(('c_det_end','Logout','ok'),0) or c.get(('d_end_fus','Logout','ok'),0)]
print('execucoes com Logout ok no isn3 depois da deteccao:',len(lo),lo)
