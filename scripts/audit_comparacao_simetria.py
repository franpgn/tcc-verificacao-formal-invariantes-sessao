#!/usr/bin/env python3
"""T-A / A.3: tabela 'com simetria x sem simetria'. Une as linhas de runs/tlc/audit-b32/controle/tabela-recalculada.tsv
(controles, SYMMETRY=NAO) com as linhas equivalentes de runs/tlc/audit-b32/tabela-recalculada.tsv (SYMMETRY=SIM, mesma
configuracao: modo/restricao/card/limites/propriedade). Prefere a referencia de 1 worker (traco minimo); para fechamentos
usa qualquer referencia que fechou. Uso: audit_comparacao_simetria.py TABELA_PROP TABELA_CTRL SAIDA_TSV"""
import csv, sys

prop = list(csv.DictReader(open(sys.argv[1]), delimiter='\t'))
ctrl = list(csv.DictReader(open(sys.argv[2]), delimiter='\t'))


def chave(r, modo):
    return (modo, r['restricao'], r['card'], r['prop'], r['MaxCanal'], r['MaxRelogio'], r['MaxEventosFalha'], r['LIMITE'])


def modo_de(r):
    return 'livre' if r['dir'].startswith('prop-livre') or 'livre' in r['dir'] else 'semeado'


def spec_de(path_dir, arquivo, base):
    cfg = open('%s/%s/%s.cfg' % (base, path_dir, arquivo[:-4])).read()
    return 'livre' if 'SPECIFICATION Spec\n' in cfg else 'semeado'


base_prop = sys.argv[1].rsplit('/', 2)[0]   # runs/tlc
base_ctrl = sys.argv[2].rsplit('/', 2)[0]   # runs/tlc/audit-b32
ref = {}
for r, b in [(x, base_prop) for x in prop] + [(x, base_ctrl) for x in ctrl if x['SYMMETRY'] == 'SIM']:
    if r['SYMMETRY'] != 'SIM' or r['workers'] == '?' or r['dir'].endswith('-sim') or 'n2m-sim' in r['dir']:
        continue
    k = chave(r, spec_de(r['dir'], r['arquivo'], b))
    ref.setdefault(k, []).append(r)
ctrl = [r for r in ctrl if r['SYMMETRY'] == 'NAO']

cols = ['controle_dir', 'prop', 'restricao', 'card', 'MaxCanal/MaxRelogio/Ev/LIMITE', 'ref_com_simetria', 'ref_workers',
        'resultado_com_sim', 'traco_com_sim', 'distintos_com_sim', 'resultado_sem_sim', 'traco_sem_sim', 'distintos_sem_sim',
        'razao_distintos', 'igual_veredito', 'igual_traco']
out = []
for r in ctrl:
    k = chave(r, spec_de(r['dir'], r['arquivo'], base_ctrl))
    cand = ref.get(k, [])
    if r['resultado_do_out'] == 'VIOLADA':
        cand = sorted(cand, key=lambda x: (x['workers'] != '1', not x['dir'].endswith('-w1') and 'ctrl-sym' not in x['dir'], int(x['estados_do_traco'] or 99)))
    else:
        cand = sorted(cand, key=lambda x: (x['resultado_do_out'] != r['resultado_do_out'], x['workers'] != '1'))
    c = cand[0] if cand else None
    razao = ''
    if c and c['distintos'] and r['distintos'] and c['resultado_do_out'] in ('FECHOU', 'VIOLADA'):
        razao = '%.2f' % (int(r['distintos']) / int(c['distintos'])) if c['resultado_do_out'] == 'FECHOU' else ''
    out.append([r['dir'], r['prop'], r['restricao'], r['card'], '/'.join([r['MaxCanal'], r['MaxRelogio'], r['MaxEventosFalha'], r['LIMITE']]),
                (c['dir'] + '/' + c['arquivo']) if c else '(sem referencia)', c['workers'] if c else '',
                c['resultado_do_out'] if c else '', c['estados_do_traco'] if c else '', c['distintos'] if c else '',
                r['resultado_do_out'], r['estados_do_traco'], r['distintos'], razao,
                ('SIM' if c and c['resultado_do_out'] == r['resultado_do_out'] else 'NAO') if c else '',
                ('SIM' if c and c['estados_do_traco'] == r['estados_do_traco'] else 'NAO') if c and r['resultado_do_out'] == 'VIOLADA' else ''])
with open(sys.argv[3], 'w', newline='') as f:
    w = csv.writer(f, delimiter='\t', lineterminator='\n')
    w.writerow(cols)
    w.writerows(out)
print('%d linhas de controle comparadas' % len(out))
