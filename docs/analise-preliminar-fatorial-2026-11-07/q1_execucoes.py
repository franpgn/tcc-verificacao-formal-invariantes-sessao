import json,collections,statistics as st,datetime as dt,sys
S=sys.argv[1]
d=json.load(open(S+'/extrato.json',encoding='utf-8'))
def utc(s): return dt.datetime.fromisoformat(s.replace('Z','+00:00'))
c=collections.Counter((r['celula'],r['braco']) for r in d.values()); print(sorted(c.items()))
print('vereditos',collections.Counter(r['veredito'] for r in d.values()), 'avisos', [ (k,r['avisos']) for k,r in d.items() if r['avisos']], 'motivos',[ (k,r['motivos']) for k,r in d.items() if r['motivos']])
dur=collections.defaultdict(list)
for k,r in d.items():
    du=(utc(r['fim_utc'])-utc(r['lancamento_utc'])).total_seconds()
    tot=(utc(r['fim_utc']).timestamp()*1000-r['recriacao_ms'])/1000
    dur[r['celula']].append((du,tot,k))
for c,v in dur.items():
    a=[x[0] for x in v]; b=[x[1] for x in v]
    print(c,'carga->fim med',round(st.median(a),1),'min',round(min(a),1),'max',round(max(a),1),max(v)[2],'| recriacao->fim med',round(st.median(b),1),'max',round(max(b),1))
ks=sorted(d)
print('primeiro lancamento',d[ks[0]]['lancamento_utc'],'ultimo fim',d[ks[-1]]['fim_utc'])
# intervalos entre execucoes
print('swap',collections.Counter(r['swap_usado_kb_k1'] for r in d.values()))
print('h4',collections.Counter(r['h4'].split('\n')[0] for r in d.values()))
print('camada3',collections.Counter(str(r['camada3']) for r in d.values()))
print('workload_exit',collections.Counter(r['workload_exit'] for r in d.values()),'teto',collections.Counter(r['teto_estourado'] for r in d.values()),'injetor',collections.Counter(r['injetor_exit'] for r in d.values()))
print('trilha',collections.Counter((r['trilha']['fim'],r['trilha']['linhas_perdidas']) for r in d.values()))
print('fusao_config_ok',collections.Counter((r['fusao'],r['fusao_config_ok'],str(r['a1_politica_carregada'])) for r in d.values()))
# convergencia
for k,r in sorted(d.items()):
    cv=r['convergencia']
    if not cv['convergiu'] or cv['divergencia_persistente'] or r['convergencia_csv']['rodadas_com_div']:
        print('CONV',k,cv['convergiu'],cv['divergencia_persistente'],cv['rodadas'],r['convergencia_csv'])
rod=collections.Counter((r['celula'],r['convergencia']['rodadas']) for r in d.values()); print(sorted(rod.items()))
# t convergencia relativo ao fim da carga
for c in ('ASYNC-F2','ASYNC-F3','ASYNC-none'):
    v=[(r['convergencia']['t_convergencia_abs_ns']-r['convergencia']['t_fim_carga_ns'])/1e9 for r in d.values() if r['celula']==c and r['convergencia']['t_convergencia_abs_ns']]
    print(c,'dconv med',round(st.median(v),4),'max',round(max(v),4),len(v))
