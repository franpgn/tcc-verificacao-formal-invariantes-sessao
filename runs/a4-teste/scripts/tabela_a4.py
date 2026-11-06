"""A4 (06/11/2026): junta os resumo.json das execucoes validas numa tabela TSV por execucao (tabela-a4.tsv).

Colunas (segundos relativos ao pedido da acao; vazio = nao se aplica ou nao ocorreu):
  execucao, caso, alvo, pedidos (cache:status:duracao), deteccao_alvo (get-availability do sessions-async do alvo em
  DEGRADED_MODE, vigia de 500 ms), por cache no alvo: primeira CacheUnavailable, primeiro put recusado, puts
  confirmados iniciados depois do pedido e o ultimo deles; na maioria: CacheUnavailable vistas (contagem); depois da
  fusao: resultado das operacoes no alvo ate o desfazer; desfazer: duracao dos DELETE e primeiro put servido no alvo
  depois do DELETE; carga S3: erros do servidor de aplicacao do alvo entre o pedido e o desfazer (ou a cura).
Uso: python -I tabela_a4.py RAIZ EXEC [EXEC...] > tabela-a4.tsv
"""
import json
import sys
from pathlib import Path

raiz = Path(sys.argv[1])
cab = ['execucao', 'caso', 'alvo', 'corte_s', 'pedidos', 'desfazer_pedidos', 'deteccao_alvo_s', 'vista1_alvo_s', 'vista2_maioria_s',
       'cura_s', 'fusao_ISPN000093_s', 'fusao_vista_s',
       'cu_sessions_alvo_s', 'put_recusado_sessions_alvo_s', 'puts_ok_sessions_alvo_apos_pedido', 'ultimo_put_ok_sessions_alvo_s',
       'cu_counters_alvo_s', 'put_recusado_counters_alvo_s', 'puts_ok_counters_alvo_apos_pedido', 'ultimo_put_ok_counters_alvo_s',
       'cu_na_maioria', 'cu_por_no_s', 'alvo_fusao_ao_desfazer', 'desfazer_put_ok_alvo_s', 'desfazer_put_ok_outros_s',
       'carga_alvo_janela', 'carga_outros_janela', 'violacoes_carga', 'ISPN005003']
print('\t'.join(cab))
for nome in sys.argv[2:]:
    d = json.loads((raiz / nome / 'resumo.json').read_text(encoding='utf-8'))
    alvo = d['alvo']
    so = d['sondas']
    ped = ['%s:%s:%.3f' % (a['url'].rsplit('/', 1)[-1].split('?')[0], a['status'], a['duracao_s'])
           for a in d['acoes'] if a['rotulo'] in ('ignorar', 'parar')]
    des = ['%s:%s:%.3f' % (a['url'].rsplit('/', 1)[-1], a['status'], a['duracao_s'])
           for a in d['acoes'] if a['rotulo'] == 'desfazer']
    det = next((c['ini_s'] for c in d['cluster_transicoes'] if c['no'] == alvo and 'sessions-async=DEGRADED' in c['estado']
                and c['ini_s'] is not None and c['ini_s'] > -10), None)
    vs = d.get('vistas', [])
    v1 = sorted(x['t_s'] for x in vs if x['no'] == alvo and x['cod'] == 'ISPN000094' and x['membros'] == '1' and x['t_s'] > 0)
    v2 = sorted(x['t_s'] for x in vs if x['no'] != alvo and x['cod'] == 'ISPN000094' and x['membros'] == '2' and x['t_s'] > 0)
    fu = sorted(x['t_s'] for x in vs if x['cod'] == 'ISPN000093' and x['t_s'] > 0)
    lin = {'execucao': nome, 'caso': d['caso'], 'alvo': alvo, 'corte_s': d['instantes_rel_pedido_s'].get('corte'),
           'vista1_alvo_s': v1[0] if v1 else None, 'vista2_maioria_s': v2[0] if v2 else None,
           'cura_s': d['instantes_rel_pedido_s'].get('cura'), 'fusao_ISPN000093_s': fu[0] if fu else None,
           'pedidos': ' '.join(ped), 'desfazer_pedidos': ' '.join(des),
           'deteccao_alvo_s': det, 'fusao_vista_s': d['instantes_rel_pedido_s'].get('fusao_vista_harness')}
    for cache, k in (('sessions-async', 'sessions'), ('counters-async', 'counters')):
        r = so.get('%s/%s' % (alvo, cache), {})
        lin['cu_%s_alvo_s' % k] = (r.get('primeira_cache_indisponivel') or {}).get('fim_s')
        lin['put_recusado_%s_alvo_s' % k] = (r.get('primeiro_put_recusado') or {}).get('fim_s')
        lin['puts_ok_%s_alvo_apos_pedido' % k] = r.get('puts_confirmados_iniciados_apos_pedido')
        lin['ultimo_put_ok_%s_alvo_s' % k] = r.get('ultimo_put_confirmado_iniciado_apos_pedido_fim_s')
    cu_m = 0
    cu_no = []
    for chave, r in so.items():
        no = chave.split('/')[0]
        n = sum(v for kk, v in (r.get('resultados_pedido_ate_fim_da_janela') or {}).items() if 'CacheUnavailable' in kk)
        if no != alvo:
            cu_m += n
        p = (r.get('primeira_cache_indisponivel') or {}).get('fim_s')
        if p is not None:
            cu_no.append('%s:%s' % (chave.replace('-async', ''), p))
    lin['cu_na_maioria'] = cu_m
    lin['cu_por_no_s'] = ' '.join(cu_no)
    fa = []
    for cache in ('sessions-async', 'counters-async'):
        r = so.get('%s/%s' % (alvo, cache), {})
        if r.get('resultados_da_fusao_ao_desfazer'):
            fa.append('%s=%s' % (cache.replace('-async', ''), json.dumps(r['resultados_da_fusao_ao_desfazer'], separators=(',', ':'))))
    lin['alvo_fusao_ao_desfazer'] = ' '.join(fa)
    lin['desfazer_put_ok_alvo_s'] = ' '.join('%s:%s' % (c.replace('-async', ''), so.get('%s/%s' % (alvo, c), {}).get('desfazer_primeiro_put_servido_fim_s_rel_desfazer'))
                                           for c in ('sessions-async', 'counters-async') if 'desfazer_primeiro_put_servido_fim_s_rel_desfazer' in so.get('%s/%s' % (alvo, c), {}))
    lin['desfazer_put_ok_outros_s'] = ' '.join('%s:%s' % (k.replace('-async', ''), r.get('desfazer_primeiro_put_servido_fim_s_rel_desfazer'))
                                             for k, r in so.items() if not k.startswith(alvo) and 'desfazer_primeiro_put_servido_fim_s_rel_desfazer' in r)
    cj = d.get('carga_por_janela') or {}
    jn = 'pedido_ao_desfazer' if 'pedido_ao_desfazer' in cj else ('pedido_a_cura' if 'pedido_a_cura' in cj else 'pedido_ao_fim')
    j = cj.get(jn, {})
    def resumir(m):
        tot = sum(m.values())
        err = sum(v for kk, v in m.items() if kk.startswith('ERROR'))
        return '%d/%d' % (err, tot)
    lin['carga_alvo_janela'] = '%s %s' % (jn, resumir(j.get(alvo, {})))
    lin['carga_outros_janela'] = ' '.join('%s:%s' % (n, resumir(j.get(n, {}))) for n in ('isn1', 'isn2', 'isn3') if n != alvo)
    lin['violacoes_carga'] = next((x.split(':')[1].strip() for x in d.get('carga_stdout', []) if x.startswith('Violacoes detectadas')), None)
    lin['ISPN005003'] = json.dumps(d.get('servidor_ISPN005003_por_no', {}), separators=(',', ':'))
    print('\t'.join('' if lin.get(c) is None else str(lin.get(c)) for c in cab))
