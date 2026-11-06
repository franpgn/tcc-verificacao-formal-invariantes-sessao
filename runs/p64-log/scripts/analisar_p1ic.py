"""P1-IC (06/11/2026): resume uma execucao do smoke.sh (controlador active com o AC-7).

Corte: instantes em segundos relativos ao inicio do corte (fases.tsv). Le a trilha do controlador
(decisoes-rep-001.jsonl: linhas politica e acao do AC-7), as sondas Hot Rod (sonda-isnN.tsv.gz), o vigia das listas
(vigia-ignorados.tsv), o fim do observaveis (eventos_descontados_sob_acao), o stdout da carga (violacoes, varredura,
replay) e as vistas dos logs dos servidores. Falso alarme: instantes relativos ao envio do AC-7 pelo SmokeAc7
(smoke-ac7.tsv).
Uso: python -I analisar_p1ic.py DIR > DIR/resumo.json
"""
import gzip
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

d = Path(sys.argv[1])
F = []
for l in (d / 'fases.tsv').read_text(encoding='utf-8').splitlines():
    ns, utc, txt = l.split('\t', 2)
    F.append((int(ns), txt))


def fase(prefixo):
    return next((ns for ns, txt in F if txt.startswith(prefixo)), None)


caso = re.search(r'caso=(\S+)', F[0][1]).group(1)
alvo = re.search(r'alvo=(isn\d)', next(t for _, t in F if t.startswith('parametros'))).group(1)
porta = {'isn1': '13001', 'isn2': '13002', 'isn3': '13003'}
rot_alvo = '127.0.0.1:' + porta[alvo]


def jsonl(nome):
    p = d / nome
    if not p.exists():
        return []
    return [json.loads(x) for x in p.read_text(encoding='utf-8').splitlines() if x.strip()]


dec = jsonl('decisoes-rep-001.jsonl')
obs = jsonl('observaveis-rep-001.jsonl')

t0 = None
if caso in ('corte', 'curto'):
    t0 = fase('corte-inicio')
else:
    sm = (d / 'smoke-ac7.tsv').read_text(encoding='utf-8').splitlines() if (d / 'smoke-ac7.tsv').exists() else []
    for l in sm:
        if l.startswith('APLICAR'):
            t0 = int(re.search(r't_ini_epoch_ns=(\d+)', l).group(1))


def rel(ns):
    return None if ns is None or t0 is None else round((ns - t0) / 1e9, 3)


out = {'execucao': d.name, 'caso': caso, 'alvo': alvo, 'rotulo_alvo': rot_alvo,
       'fases_rel_s': [(rel(ns), txt[:90]) for ns, txt in F]}

# ---- trilha do controlador
pol = [x for x in dec if x.get('tipo') == 'politica']
acoes = [x for x in dec if x.get('tipo') == 'acao']
out['politica_nao_P0_ou_desfazer'] = [
    {'t_decisao_s': rel(x['t_decisao_ns']), 'perfil': x['perfil'], 'politica': x['politica'], 'motivo': x['motivo'],
     'no': x['no'], 'aplicar': x['aplicar'], 'reverter': x['reverter'], 'usadas_p1': x.get('usadas_p1')}
    for x in pol if x['politica'] != 'P0' or x['reverter'] or x['motivo'].startswith('desfazer')
    or x['motivo'] == 'suspeita-desfeita']
out['acoes'] = [{'operacao': x['operacao'], 'tipo': x['acao']['tipo'], 'alvo': x['acao']['alvo'],
                 'caches': x['acao']['caches'], 'origem': x['origem'], 'resultado': x['resultado'],
                 'executada': x['executada'], 't_pedido_s': rel(x['t_pedido_ns']),
                 't_inicio_s': rel(x.get('t_inicio_ns')),
                 'duracao_ms': None if x.get('duracao_ns') is None else round(x['duracao_ns'] / 1e6, 1),
                 'codigo_http': x.get('codigo_http'), 'lido': x.get('lido'), 'erro': x.get('erro')} for x in acoes]
transicoes = [x for x in dec if x.get('tipo') == 'decisao' and x.get('transicao')]
out['transicoes'] = [{'t_s': rel(x['t_fim_ns']), 'perfil': x['perfil'], 'regra': x['regra']} for x in transicoes]
fim_dec = next((x for x in reversed(dec) if x.get('tipo') == 'fim'), {})
fim_obs = next((x for x in reversed(obs) if x.get('tipo') == 'fim'), {})
out['fim_decisoes'] = {k: fim_dec.get(k) for k in ('acoes_pedidas', 'acoes_executadas', 'acoes_falhas', 'reversoes',
                                                   'reversoes_falhas', 'executor_no_prazo', 'prazo_encerramento_ns',
                                                   'linhas_perdidas', 'perfil_final')}
out['fim_observaveis'] = {k: fim_obs.get(k) for k in ('eventos_descontados_sob_acao', 'guarda_ac7_desfazeres', 'eventos_fora_do_contrato',
                                                      'linhas_perdidas')}
meta = dec[0] if dec else {}
out['meta'] = {'esquema': meta.get('esquema'), 'atuador': meta.get('atuador'), 'modo': meta.get('modo'),
               'acao_p1': (meta.get('parametros_politica') or {}).get('acao_p1')}

aplicar = next((x for x in acoes if x['operacao'] == 'APLICAR' and x['acao']['tipo'] == 'AC-7'), None)
reverter = [x for x in acoes if x['operacao'] == 'REVERTER' and x['acao']['tipo'] == 'AC-7']
t_aplicar = aplicar.get('t_inicio_ns') if aplicar else None
t_reverter = reverter[0].get('t_inicio_ns') if reverter else None
if caso == 'falso':
    t_aplicar = t0
    for l in (d / 'smoke-ac7.tsv').read_text(encoding='utf-8').splitlines():
        if l.startswith('REVERTER'):
            t_reverter = int(re.search(r't_ini_epoch_ns=(\d+)', l).group(1))
    out['smoke_ac7'] = (d / 'smoke-ac7.tsv').read_text(encoding='utf-8').splitlines()

# ---- sondas
sondas = {}
for no in ('isn1', 'isn2', 'isn3'):
    p = d / ('sonda-%s.tsv.gz' % no)
    if not p.exists():
        continue
    L = []
    with gzip.open(p, 'rt', encoding='utf-8') as f:
        next(f)
        for l in f:
            c = l.rstrip('\n').split('\t')
            if len(c) >= 6 and c[0].isdigit():
                L.append((int(c[0]), int(c[1]), c[3], c[4], c[5], c[6] if len(c) > 6 else ''))
    for cache in ('sessions-async', 'counters-async'):
        X = sorted(x for x in L if x[2] == cache)
        r = {}
        cu = [x for x in X if 'CacheUnavailable' in x[5]]
        r['recusas_cache_ignorado'] = len(cu)
        r['primeira_cache_ignorado_s'] = rel(cu[0][1]) if cu else None
        r['ultima_cache_ignorado_s'] = rel(cu[-1][1]) if cu else None
        if t_aplicar:
            r['primeira_cache_ignorado_desde_acao_ms'] = round((cu[0][1] - t_aplicar) / 1e6, 1) if cu and cu[0][1] > t_aplicar else None
            P = [x for x in X if x[3] == 'put']
            okp = [x for x in P if x[0] >= t_aplicar and x[4] == 'OK']
            ok_ate_recusa = [x for x in okp if not cu or x[0] < cu[0][0]]
            r['puts_ok_iniciados_apos_acao_antes_da_recusa'] = len(ok_ate_recusa)
            av = [x for x in X if x[1] > t_aplicar and 'AvailabilityException' in x[5]]
            r['primeira_availability_s'] = rel(av[0][1]) if av else None
        if t_reverter:
            okd = next((x for x in X if x[0] >= t_reverter and x[3] == 'put' and x[4] == 'OK'), None)
            r['primeiro_put_ok_desde_reverter_ms'] = None if okd is None else round((okd[1] - t_reverter) / 1e6, 1)
            r['primeiro_put_ok_apos_reverter_s'] = None if okd is None else rel(okd[1])
        cont = {}
        for x in X:
            if t_aplicar and x[0] >= t_aplicar:
                k = x[3] + ':' + ('OK' if x[4] == 'OK' else 'CacheUnavailable' if 'CacheUnavailable' in x[5]
                                  else 'Availability' if 'AvailabilityException' in x[5] else x[4])
                cont[k] = cont.get(k, 0) + 1
        r['resultados_desde_acao'] = dict(sorted(cont.items()))
        sondas['%s/%s' % (no, cache)] = r
out['sondas'] = sondas

# ---- vigia
ign = []
if (d / 'vigia-ignorados.tsv').exists():
    for l in (d / 'vigia-ignorados.tsv').read_text(encoding='utf-8').splitlines()[1:]:
        a, b, no, tipo, v = l.split('\t')
        if tipo == 'transicao' and not v.startswith('erro'):
            ign.append((no, rel(int(b)), v))
out['ignorados_transicoes'] = ign
clu = []
if (d / 'vigia-cluster.tsv').exists():
    for l in (d / 'vigia-cluster.tsv').read_text(encoding='utf-8').splitlines()[1:]:
        a, b, no, v = l.split('\t')
        clu.append((no, rel(int(a)), v))
out['disponibilidade_transicoes'] = clu

# ---- carga
so = (d / 'stdout.log').read_text(encoding='utf-8', errors='replace') if (d / 'stdout.log').exists() else ''
out['carga_stdout'] = [l.strip()[:200] for l in so.splitlines() if re.search(
    r'^Violacoes detectadas|^Total de operacoes|Varredura|varredura|Replay|replay|^Controlador:|^Fim da carga|Convergencia|^Auditor', l)][:30]
ex = d / 'exit.txt'
out['carga_exit'] = ex.read_text().strip() if ex.exists() else None
vio = d / 'violacoes-rep-001.csv'
if vio.exists():
    cont = {}
    for l in vio.read_text(encoding='utf-8').splitlines()[1:]:
        inv = l.split(',')[1]
        cont[inv] = cont.get(inv, 0) + 1
    out['violacoes_auditor'] = cont
var = d / 'varredura-rep-001.csv'
if var.exists():
    linhas = var.read_text(encoding='utf-8').splitlines()
    out['varredura_cabecalho'] = linhas[0] if linhas else None
    out['varredura_linhas'] = len(linhas) - 1


# ---- vistas
def ts_log(linha):
    mm = re.search(r'(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d),(\d{3})', linha)
    if not mm:
        return None
    dt = datetime.strptime(mm.group(1), '%Y-%m-%d %H:%M:%S').replace(tzinfo=timezone.utc)
    return int(dt.timestamp()) * 1_000_000_000 + int(mm.group(2)) * 1_000_000


vistas = []
n5003 = {}
for no in ('isn1', 'isn2', 'isn3'):
    p = d / 'servidores' / (no + '.log.gz')
    if not p.exists():
        continue
    with gzip.open(p, 'rt', encoding='utf-8', errors='replace') as f:
        for l in f:
            if 'ISPN005003' in l:
                n5003[no] = n5003.get(no, 0) + 1
                continue
            m = re.search(r'(ISPN00009[34])[^\[]*\[([^\]]*)\] \((\d+)\)', l)
            t = ts_log(l)
            if m and t and t0 and t > t0 - 5_000_000_000:
                vistas.append((no, rel(t), m.group(1), m.group(3)))
out['vistas'] = vistas
out['servidor_ISPN005003_por_no'] = n5003
print(json.dumps(out, ensure_ascii=False, indent=1))
