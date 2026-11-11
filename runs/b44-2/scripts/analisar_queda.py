"""W-2a item 9 (I-1, I-2): resume uma execucao do queda.sh.

Le fases.tsv (epoch-ns do harness), decisoes-rep-001.jsonl e observaveis-rep-001.jsonl do controlador,
rebal.tsv, distribuicao.tsv e os logs dos servidores. Imprime, em segundos relativos ao inicio da falha:
transicoes, linhas politica (P0/P1/P2, motivo, no), linhas acao (APLICAR/REVERTER, resultado, inicio e fim da
chamada), primeira amostra da sonda com o no de volta, vistas (ISPN000093/ISPN000094) e rebalanceamentos
(ISPN100002) por fase, e os intervalos que o I-2 compara: REST de volta -> reversao e REST de volta -> fusao.
Uso: python -I analisar_queda.py DIR
"""
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

d = Path(sys.argv[1])


def fases():
    r = []
    for l in (d / 'fases.tsv').read_text(encoding='utf-8').splitlines():
        ns, utc, txt = l.split('\t')
        r.append((int(ns), txt))
    return r


F = fases()


def t_de(prefixo):
    for ns, txt in F:
        if txt.startswith(prefixo):
            return ns
    return None


t0 = t_de('falha-inicio')
t_volta = t_de('rest-volta')
t_desfeita = t_de('falha-desfeita')
par = (d / 'parametros.txt').read_text(encoding='utf-8').strip()
alvo = re.search(r'alvo=(isn\d)', par).group(1)


def rel(ns):
    return None if ns is None else round((ns - t0) / 1e9, 3)


print('#', par)
print('fases:', [(rel(ns), txt) for ns, txt in F])
dec = [json.loads(l) for l in (d / 'decisoes-rep-001.jsonl').read_text(encoding='utf-8').splitlines()]
obs = [json.loads(l) for l in (d / 'observaveis-rep-001.jsonl').read_text(encoding='utf-8').splitlines()]
meta = dec[0]
print('meta: esquema=%s modo=%s atuador=%s politica=%s parametros_politica=%s' % (
    meta['esquema'], meta['modo'], meta['atuador'], meta['politica'],
    json.dumps({k: v for k, v in (meta.get('parametros_politica') or {}).items() if k != 'origem'})))
for n in dec:
    if n['tipo'] == 'decisao' and n['transicao']:
        print('transicao t_fim=%s regra=%s perfil=%s severidade=%s' % (rel(n['t_fim_ns']), n['regra'], n['perfil'],
                                                                        n['severidade']))
pol = [n for n in dec if n['tipo'] == 'politica']
ultimo = None
for n in pol:
    chave = (n['politica'], n['motivo'], n.get('no'))
    if chave != ultimo:
        print('politica t_fim=%s %s:%s no=%s reverter=%s aplicar=%s' % (rel(n['t_fim_janela_ns']), n['politica'],
                                                                       n['motivo'], n.get('no'), n['reverter'], n['aplicar']))
    ultimo = chave
acoes = [n for n in dec if n['tipo'] == 'acao']
t_aplic = t_rev = None
for n in acoes:
    ini = n.get('t_inicio_ns')
    fim = None if ini is None else ini + n['duracao_ns']
    print('acao %s %s origem=%s resultado=%s executada=%s http=%s pedido=%s inicio=%s fim=%s reversao=%s lido=%s erro=%s' % (
        n['operacao'], n['acao']['tipo'], n['origem'], n['resultado'], n['executada'], n['codigo_http'],
        rel(n['t_pedido_ns']), rel(ini), rel(fim), n.get('reversao'), (n.get('lido') or '')[:90], n.get('erro')))
    if n['operacao'] == 'APLICAR' and t_aplic is None and n['resultado'] == 'APLICADA':
        t_aplic = fim
    if n['operacao'] == 'REVERTER' and t_rev is None and fim is not None and n['resultado'] != 'FALHOU':
        t_rev = fim
fimln = dec[-1]
print('fim: acoes_pedidas=%s executadas=%s falhas=%s reversoes=%s reversoes_falhas=%s executor_no_prazo=%s' % (
    fimln.get('acoes_pedidas'), fimln.get('acoes_executadas'), fimln.get('acoes_falhas'), fimln.get('reversoes'),
    fimln.get('reversoes_falhas'), fimln.get('executor_no_prazo')))
motivos = {}
for n in pol:
    motivos[n['motivo']] = motivos.get(n['motivo'], 0) + 1
print('motivos (janelas):', motivos)

# sonda: primeira amostra depois da falha com o alvo respondendo
rot_alvo = None
for n in obs:
    if n['tipo'] == 'topologia':
        for r in n.get('por_no', []):
            pass
t_sonda_volta = None
for n in obs:
    if n['tipo'] != 'topologia' or n['t_ns'] < (t_desfeita or 0):
        continue
    nos = n.get('por_no') or n.get('nos') or []
    porta = {'isn1': '12501', 'isn2': '12502', 'isn3': '12503'}[alvo]
    for r in nos:
        if str(r.get('no', '')).endswith(porta) and r.get('respondeu'):
            t_sonda_volta = n['t_ns']
            break
    if t_sonda_volta:
        break
print('sonda: primeira amostra com %s de volta: %s' % (alvo, rel(t_sonda_volta)))


# logs dos servidores
def ts_log(linha):
    # instante do proprio servidor (UTC, ms): "2026-11-05 08:41:57,542"
    m = re.search(r'(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d),(\d{3})', linha)
    if not m:
        return None
    dt = datetime.strptime(m.group(1), '%Y-%m-%d %H:%M:%S').replace(tzinfo=timezone.utc)
    return int(dt.timestamp()) * 1_000_000_000 + int(m.group(2)) * 1_000_000


vistas = []
rebal = []
for f in sorted(d.glob('servidor-isn*.log')):
    no = f.stem.split('-')[1]
    for l in f.read_text(encoding='utf-8', errors='replace').splitlines():
        if 'ISPN000093' in l or 'ISPN000094' in l:
            m = re.search(r'(ISPN00009[34]): [^\[]*\[([^\]]*)\] \((\d+)\) \[([^\]]*)\]', l)
            vistas.append((ts_log(l), no, m.group(1) if m else '?',
                           ('%s (%s) %s' % (m.group(2), m.group(3), m.group(4))) if m else l[-80:]))
        if 'ISPN100002' in l:
            m = re.search(r"Context=([^\]]+)\]", l)
            tp = re.search(r"topology id (\d+)", l)
            rebal.append((ts_log(l), no, m.group(1) if m else '?', tp.group(1) if tp else '?'))
for t, no, cod, vis in sorted(vistas):
    if t and t >= t0 - 2_000_000_000:
        print('vista %s %s %s %s' % (rel(t), no, cod, vis))
fusoes = [t for t, no, cod, vis in vistas if cod == 'ISPN000093' and t and t_desfeita and t >= t_desfeita]
t_fusao = min(fusoes) if fusoes else None
fase_de = lambda t: 'antes' if t < t0 else ('falha' if t_desfeita is None or t < t_desfeita else 'depois')
DADOS = {'sessions', 'counters', 'sessions-async', 'counters-async'}
cont = {}
vistos = set()
for t, no, c, tp in sorted(x for x in rebal if x[0]):
    if c in DADOS and (c, tp) not in vistos:
        vistos.add((c, tp))
        k = fase_de(t)
        cont.setdefault(k, []).append('%s@%s' % (c, rel(t)))
print('rebalanceamentos dos caches de dados (ISPN100002, um por cache e topologia) por fase:',
      {k: len(v) for k, v in cont.items()}, {k: v for k, v in cont.items() if k != 'antes'})
print('INTERVALOS: aplicacao_AC2=%s rest_volta=%s sonda_volta=%s reversao_AC2=%s fusao=%s | volta->reversao=%s s volta->fusao=%s s fusao_antes_da_reversao=%s' % (
    rel(t_aplic), rel(t_volta), rel(t_sonda_volta), rel(t_rev), rel(t_fusao),
    None if (t_rev is None or t_volta is None) else round((t_rev - t_volta) / 1e9, 3),
    None if (t_fusao is None or t_volta is None) else round((t_fusao - t_volta) / 1e9, 3),
    None if (t_fusao is None or t_rev is None) else t_fusao < t_rev))
# rebalancing_enabled lido pelo harness
lin = [l.split('\t') for l in (d / 'rebal.tsv').read_text(encoding='utf-8').splitlines()]
falsos = [l for l in lin if l[3].startswith('False')]
print('rebal.tsv: %d leituras, %d com rebalancing_enabled=False; nos com False: %s' % (
    len(lin), len(falsos), sorted(set(l[2] for l in falsos))))
print('rebal.tsv depois da carga:', [(l[2], l[3]) for l in lin if l[1] == 'depois'])
tog = {}
for f in sorted(d.glob('servidor-isn*.log')):
    for l in f.read_text(encoding='utf-8', errors='replace').splitlines():
        for cod in ('ISPN000309', 'ISPN000308'):
            if cod in l:
                tog[cod] = tog.get(cod, 0) + 1
print('linhas de log Rebalancing suspended (ISPN000309) / enabled (ISPN000308), somadas nos 3 nos:', tog)
falhas = [n for n in acoes if n['resultado'] == 'FALHOU']
print('acoes FALHOU: %d (%s)' % (len(falhas), sorted(set((n['operacao'], (n.get('erro') or '')[:40]) for n in falhas))))
# violacoes e resumo da carga
so = (d / 'stdout.log').read_text(encoding='utf-8', errors='replace')
for l in so.splitlines():
    if re.search(r'viola|Controlador:|I1=|I2=|erro', l, re.I):
        print('stdout:', l.strip()[:200])
