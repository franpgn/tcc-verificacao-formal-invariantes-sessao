import json,collections,sys,math,itertools,statistics as st
from scipy import stats
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
F2=[r for r in d.values() if r['celula']=='ASYNC-F2']
blocos=sorted(set(r['bloco'] for r in F2),key=int)
def get(b,br): return [r for r in F2 if r['bloco']==b and r['braco']==br][0]
def contr(y,ef):
    if ef=='A1': return .5*((y['AO']-y['PO'])+(y['AL']-y['PL']))
    if ef=='A4': return .5*((y['PL']-y['PO'])+(y['AL']-y['AO']))
    return (y['AL']-y['AO'])-(y['PL']-y['PO'])
def signflip(ds):
    n=len(ds); obs=abs(sum(ds)); c=0; tot=0
    for s in itertools.product((1,-1),repeat=n):
        tot+=1; c+= abs(sum(a*b for a,b in zip(s,ds)))>=obs-1e-12
    return c/tot
def ict(ds):
    n=len(ds); mu=st.mean(ds); se=st.stdev(ds)/math.sqrt(n); q=stats.t.ppf(.975,n-1); return mu,(mu-q*se,mu+q*se)
def analisa(nome,f,log=False,excluir=()):
    out=[]
    for ef in ('A1','A4','int'):
        ds=[]
        for b in blocos:
            if b in excluir: continue
            y={br:f(get(b,br)) for br in ('PO','PL','AO','AL')}
            if any(v is None for v in y.values()): continue
            if log: y={k:math.log(v) for k,v in y.items()}
            ds.append(contr(y,ef))
        mu,ic=ict(ds); p=signflip(ds)
        if log: mu,ic=math.exp(mu),(math.exp(ic[0]),math.exp(ic[1]))
        out.append('%s %.4f [%.4f; %.4f] p=%.4f n=%d'%(ef,mu,ic[0],ic[1],p,len(ds)))
    print(nome,'excl',excluir,' | '.join(out))
D=lambda n: (lambda r: r['desfechos'][n]['y'])
analisa('V',D('taxa_violacoes_por_1000'))
analisa('V',D('taxa_violacoes_por_1000'),excluir=('13',))
analisa('p99',D('acao_s3_p99_com_espera'),log=True)
analisa('p99',D('acao_s3_p99_com_espera'),log=True,excluir=('13',))
analisa('taxa_erro',D('taxa_erro'),excluir=('13',))
analisa('falhas',D('falhas_percebidas_por_1000'),excluir=('13',))
analisa('vazao',D('vazao_util'),log=True,excluir=('13',))
analisa('exposicao',D('exposicao_janela_sids'),excluir=('13',))
analisa('perda',D('perda_permanente_sids'),excluir=('13',))
# V por instrumento: so online+varredura (instrumentos antigos), so sonda
def v_sub(tipos):
    def f(r):
        c=0
        for p in r['v_detalhe']['pares_lista_compacta']:
            if any(x['conta'] and x['tipo'] in tipos for x in p['deteccoes']): c+=1
        return 1000*c/r['n_acoes_sel']
    return f
analisa('V online+varredura',v_sub(('online','varredura')))
analisa('V so sonda (imediata+tardia)',v_sub(('imediata','tardia')))
analisa('V so forma1 (pares)',lambda r: sum(1 for p in r['v_detalhe']['pares_lista_compacta'] if p['conta'] and p['forma']=='forma1'))
analisa('V so forma2 (pares)',lambda r: sum(1 for p in r['v_detalhe']['pares_lista_compacta'] if p['conta'] and p['forma']=='forma2'))
# CPU e energia
def cpu_pu(r):
    c=r['energia']['cpu_fontes']['vm']; u=r['energia']['uteis']
    return 1000*(c['durante']+c['recuperacao'])/(u['durante']+u['recuperacao'])
def e_pu(r):
    e=r['energia']['e_vm']['0.3']; u=r['energia']['uteis']
    return 1000*(e['durante']['e_total_j']+e['recuperacao']['e_total_j'])/(u['durante']+u['recuperacao'])
def e_pu5(r):
    e=r['energia']['e_vm']['0.5']; u=r['energia']['uteis']
    return 1000*(e['durante']['e_total_j']+e['recuperacao']['e_total_j'])/(u['durante']+u['recuperacao'])
def cpu_abs(r):
    c=r['energia']['cpu_fontes']['vm']; return c['durante']+c['recuperacao']
def cpu_isn3(r):
    c=r['energia']['cpu_fontes']['cg:isn3']; return c['durante']+c['recuperacao']
analisa('cpu vm /1000 uteis',cpu_pu,log=True)
analisa('cpu vm absoluta',cpu_abs,log=True)
analisa('cpu isn3 absoluta',cpu_isn3,log=True)
analisa('energia 0.3 J/1000 uteis',e_pu,log=True)
analisa('energia 0.5 J/1000 uteis',e_pu5,log=True)
# erros na janela 0-42 s
analisa('erros 0-42s',lambda r: r['eventos_por_fase'].get('falha_0_42s',{}).get('err',0))
analisa('erros durante',lambda r: r['eventos_por_fase'].get('durante',{}).get('err',0))
analisa('erros recuperacao',lambda r: r['eventos_por_fase'].get('recuperacao',{}).get('err',0))
