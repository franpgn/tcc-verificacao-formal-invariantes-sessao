#!/usr/bin/env python3
"""T-A / A.1: recalcula, so a partir dos .out e .cfg brutos de runs/tlc/<dir>/, o resultado de cada
execucao do TLC (FECHOU/VIOLADA/TIMEOUT/ABORTADA/SIMULACAO_SEM_ACHADO), numeros e tamanho do traco, e compara
com (a) o resumo-*.tsv do runner e (b) as alegacoes do relatorio B-32 (arquivo de alegacoes opcional).

Uso: audit_tlc_runs.py RUNS_TLC_DIR OUT_TSV [ALEGACOES_TSV] [GLOB_DIR=prop-*]

Nao interpreta nada alem das linhas finais do TLC (protocolo 4.1, P-1..P-4)."""
import re, sys, glob, os, csv


def num(s):
    return int(s.replace(',', ''))


def parse_out(path):
    t = open(path, encoding='utf-8', errors='replace').read()
    r = {}
    m = re.search(r'with (\d+) worker', t)
    r['workers'] = m.group(1) if m else '?'
    r['sim'] = 'Simulation using seed' in t or 'Running randomized simulation' in t
    viol = re.search(r'^Error: .*?violated', t, re.M)
    r['traco'] = len(re.findall(r'^State \d+:', t, re.M))
    final = re.findall(r'^(\d+) states generated, (\d+) distinct states found, (\d+) states left on queue\.', t, re.M)
    prog = re.findall(r'^Progress\(\d+\) at [^:]+:\d+:\d+: ([\d,]+) states generated.*?, ([\d,]+) distinct states found.*?, ([\d,]+) states left on queue', t, re.M)
    m = re.search(r'^Finished in (.+?) at', t, re.M)
    r['finished'] = m.group(1) if m else ''
    r['noerr'] = 'Model checking completed. No error has been found' in t
    r['semantic_fail'] = bool(re.search(r'Parsing or semantic analysis failed|Fatal errors while parsing|Semantic errors', t))
    r['inicial'] = 'Finished computing initial states' in t
    if final:
        g, d, q = map(int, final[-1])
        r['fonte_num'] = 'linha_final'
    elif prog:
        g, d, q = (num(x) for x in prog[-1])
        r['fonte_num'] = 'ultimo_Progress'
    elif r['sim']:
        m = re.search(r'The number of states generated: (\d+)', t)
        g, d, q = (int(m.group(1)) if m else '', '', '')
        r['fonte_num'] = 'simulate'
    else:
        g = d = q = ''
        r['fonte_num'] = 'nenhuma'
    r['gerados'], r['distintos'], r['fila'] = g, d, q
    if viol:
        res = 'VIOLADA'
    elif r['sim']:
        res = 'SIMULACAO_SEM_ACHADO' if r['finished'] else 'ABORTADA'
    elif r['noerr'] and q == 0:
        res = 'FECHOU'
    elif r['noerr']:
        res = 'INCONSISTENTE'
    elif r['semantic_fail'] or not (r['inicial'] or prog):
        res = 'ABORTADA'
    else:
        res = 'TIMEOUT'
    r['resultado'] = res
    return r


def parse_cfg(path):
    c = open(path).read() if os.path.exists(path) else ''

    def g(k):
        m = re.search(k + r'\s*=\s*(\S.*?)\s*$', c, re.M)
        return m.group(1) if m else '?'
    nrep = len(re.findall(r'r\d', g('REPLICAS')))
    restr = next((x for x in ('SemFalha', 'SoQueda', 'SoParticao') if re.search(r'^\s*' + x + r'\s*$', c, re.M)), 'nenhuma')
    sec = re.search(r'^(PROPERTY|INVARIANTS?)\s*\n\s*(\S+)', c, re.M)
    sp = re.search(r'SPECIFICATION\s+(\S+)', c)
    return dict(spec=sp.group(1) if sp else '?', nrep=nrep, canal=g('MaxCanal'), relogio=g('MaxRelogio'),
                ev=g('MaxEventosFalha'), limite=g('LIMITE'),
                symmetry='SIM' if re.search(r'^SYMMETRY', c, re.M) else 'NAO', restr=restr,
                secao=sec.group(1) if sec else '?', prop=sec.group(2) if sec else '?')


def tsv_rows(d, card):
    p = os.path.join(d, 'resumo-%s.tsv' % card)
    rows = []
    if os.path.exists(p):
        for l in open(p):
            f = l.rstrip('\n').split('\t')
            if len(f) >= 9 and f[2] == card:
                rows.append(f)
    return rows


MAPA = {'VIOLADA': 'VIOLADA', 'SEM_VIOLACAO': 'FECHOU', 'SEM_VIOLACAO_ATE_AQUI': 'TIMEOUT',
        'NAO_ENCONTRADA': 'SIMULACAO_SEM_ACHADO', 'ERRO': 'ABORTADA'}


def main():
    base, outp = sys.argv[1], sys.argv[2]
    alegs = sys.argv[3] if len(sys.argv) > 3 else None
    pat = sys.argv[4] if len(sys.argv) > 4 else 'prop-*'
    cols = ['dir', 'arquivo', 'prop', 'workers', 'card', 'MaxCanal', 'MaxRelogio', 'MaxEventosFalha', 'LIMITE', 'restricao',
            'SYMMETRY', 'secao', 'resultado_do_out', 'gerados', 'distintos', 'fila', 'duracao_out', 'fonte_numeros',
            'estados_do_traco', 'tsv_resultado', 'tsv_duracao', 'tsv_n_linhas', 'diverge_tsv', 'detalhe_divergencia']
    out = []
    for d in sorted(glob.glob(os.path.join(base, pat))):
        if not os.path.isdir(d):
            continue
        for o in sorted(glob.glob(os.path.join(d, '*.out'))):
            nome = os.path.basename(o)[:-4]
            card = nome.split('-')[0]
            r = parse_out(o)
            c = parse_cfg(o[:-4] + '.cfg')
            rows = tsv_rows(d, card)
            mine = [x for x in rows if x[3] == c['prop']]
            diverge, det, tres, tdur = 'nao', '', '', ''
            if mine:
                last = mine[-1]
                tres = last[4] + ':' + last[5]
                tdur = last[8]
                esperado = MAPA.get(last[4], '?')
                if esperado != r['resultado']:
                    diverge, det = 'SIM', 'tsv=%s out=%s' % (last[4], r['resultado'])
                else:
                    if last[6] != '?' and str(r['gerados']) != last[6]:
                        diverge, det = 'SIM', 'gerados tsv=%s out=%s' % (last[6], r['gerados'])
                    if last[7] != '?' and str(r['distintos']) != last[7]:
                        diverge, det = 'SIM', det + ' distintos tsv=%s out=%s' % (last[7], r['distintos'])
                    mt = re.match(r'traco_de_(\d+)_estados', last[5])
                    if mt and int(mt.group(1)) != r['traco']:
                        diverge, det = 'SIM', det + ' traco tsv=%s out=%s' % (mt.group(1), r['traco'])
            else:
                diverge, det = 'SEM_LINHA_TSV', 'nenhuma linha do tsv para (card,prop)'
            out.append([os.path.basename(d), nome + '.out', c['prop'], r['workers'], 'r%d' % c['nrep'], c['canal'], c['relogio'],
                        c['ev'], c['limite'], c['restr'], c['symmetry'], c['secao'], r['resultado'], r['gerados'],
                        r['distintos'], r['fila'], r['finished'], r['fonte_num'],
                        r['traco'] if r['resultado'] == 'VIOLADA' else '', tres, tdur, len(mine), diverge, det])
    with open(outp, 'w', newline='') as f:
        w = csv.writer(f, delimiter='\t', lineterminator='\n')
        w.writerow(cols)
        w.writerows(out)
    print('%d .out processados -> %s; divergencias tsv: %d' % (len(out), outp, sum(1 for x in out if x[22] != 'nao')))
    if alegs and os.path.exists(alegs):
        idx = {(x[0], x[1]): x for x in out}
        ndiv = 0
        with open(alegs) as f, open(outp.replace('tabela-recalculada', 'divergencias-relatorio'), 'w') as g:
            g.write('secao\tdir\tarquivo\tcampo\talegado\trecalculado\tstatus\n')
            for l in f:
                if l.startswith('#') or not l.strip():
                    continue
                s, d, a, campo, alegado = l.rstrip('\n').split('\t')
                row = idx.get((d, a))
                if not row:
                    g.write('%s\t%s\t%s\t%s\t%s\t(ausente)\tDIVERGE\n' % (s, d, a, campo, alegado))
                    ndiv += 1
                    continue
                v = {'resultado': row[12], 'distintos': row[14], 'gerados': row[13], 'traco': row[18],
                     'duracao': row[20]}[campo]
                ok = str(v) == alegado
                g.write('%s\t%s\t%s\t%s\t%s\t%s\t%s\n' % (s, d, a, campo, alegado, v, 'OK' if ok else 'DIVERGE'))
                ndiv += (not ok)
        print('alegacoes divergentes: %d' % ndiv)


main()
