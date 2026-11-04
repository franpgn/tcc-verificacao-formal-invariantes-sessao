#!/usr/bin/env python3
"""B-45: consolida a rodada TLC antes x depois a partir SO dos brutos (runs/tlc/b45-*/<id>.out e .cfg)
e do plano (spec/b45-plano.tsv). Nao usa o resumo.tsv do roteiro para o veredito (recalcula do .out);
compara com ele e acusa divergencia.

Uso: b45_tabela.py RAIZ_DO_REPO SAIDA_TSV [DIR_TRACOS]
  SAIDA_TSV  uma linha por execucao do plano (inclusive as que ainda nao rodaram: status PENDENTE)
  DIR_TRACOS se dado, grava <id>.txt com o traco compacto de toda execucao VIOLADA
"""
import csv, os, re, sys

COLS = ['id', 'grupo', 'rev', 'modulo', 'spec', 'card', 'limite', 'maxcanal', 'maxrelogio', 'maxeventosfalha',
        'restricao', 'propriedade', 'simetria', 'workers', 'timeout_s']


def num(s):
    return int(s.replace(',', ''))


def parse_out(path):
    t = open(path, encoding='utf-8', errors='replace').read()
    r = {}
    r['violada'] = bool(re.search(r'^Error: .*violated', t, re.M))
    r['fechou'] = 'Model checking completed. No error has been found' in t
    r['recover'] = 'Starting recovery from checkpoint' in t
    fin = re.findall(r'^(\d+) states generated, (\d+) distinct states found, (\d+) states left on queue\.', t, re.M)
    prog = re.findall(r'^Progress\(\d+\) at [^:]+:\d+:\d+: ([\d,]+) states generated.*?, ([\d,]+) distinct states found'
                      r'.*?, ([\d,]+) states left on queue', t, re.M)
    if fin:
        g, d, q = map(int, fin[-1]); fonte = 'linha_final'
    elif prog:
        g, d, q = (num(x) for x in prog[-1]); fonte = 'ultimo_Progress'
    else:
        g = d = q = ''; fonte = 'nenhuma'
    r.update(gerados=g, distintos=d, fila=q, fonte=fonte)
    m = re.findall(r'^Finished in (.+?) at', t, re.M)
    r['finished'] = m[-1] if m else ''
    m = re.search(r'with (\d+) workers?', t)
    r['workers_out'] = m.group(1) if m else '?'
    acoes = [re.sub(r' line .*', '', a) for a in re.findall(r'^State \d+: <(.*?)>', t, re.M)]
    r['traco'] = len(acoes) if r['violada'] else ''
    r['acoes'] = ' > '.join(acoes[1:]) if r['violada'] else ''
    r['mesclar'] = ('SIM' if 'Mesclar' in acoes else 'NAO') if r['violada'] else ''
    if r['violada']:
        r['resultado'] = 'VIOLADA'
    elif r['fechou'] and q == 0:
        r['resultado'] = 'FECHOU'
    elif r['fechou']:
        r['resultado'] = 'INCONSISTENTE'
    elif re.search(r'Parsing or semantic analysis failed|Semantic errors|Error:', t):
        r['resultado'] = 'ERRO'
    elif fonte == 'nenhuma':
        r['resultado'] = 'ERRO'
    else:
        r['resultado'] = 'SEM_VIOLACAO_ATE_AQUI'
    r['texto'] = t
    return r


def abrev(flat):
    f = flat.replace('estado |-> ', '').replace('motivo |-> ', 'm=').replace('identidade |-> ', 'id=')
    f = re.sub(r'\[ *(\w+), m=(\w+), id=(\w+), ts \|-> (\d+) *\]',
               lambda m: '%s/%s/%s@%s' % (m.group(1)[0], m.group(2).replace('NONE', '-').replace('LOGOUT', 'LO')
                                          .replace('ADMIN_BLOCK', 'AB').replace('EXPIRADA', 'EX'),
                                          m.group(3).replace('NONE', '-'), m.group(4)), f)
    f = re.sub(r'\[ *ts \|-> (\d+), contador \|-> (\d+), status \|-> (\w+) *\]',
               lambda m: '%s@%s c%s' % (m.group(3)[0], m.group(1), m.group(2)), f)
    f = re.sub(r'\[ *(?:origem \|-> (\w+), )?tipo \|-> (\w+), chave \|-> (\w+), sessao \|-> (\S+), ident \|-> ([^,]*?),'
               r' sessoesAfetadas \|-> (\{[^}]*\})(?:, origem \|-> (\w+))? *\]',
               lambda m: '{%s %s s=%s i=%s af=%s%s}' % (m.group(2), m.group(3), m.group(4), m.group(5), m.group(6),
                                                        ' de ' + (m.group(1) or m.group(7)) if (m.group(1) or m.group(7)) else ''), f)
    return f.replace(' :> ', ':').replace(' @@ ', ' ; ')


def traco_compacto(t):
    blocks = re.split(r'^(State \d+: .*)$', t, flags=re.M)
    linhas = []
    for i in range(1, len(blocks), 2):
        hdr, body = blocks[i], blocks[i + 1].split('\n\n')[0]
        flat = re.sub(r'\s+', ' ', body).replace('"', '')
        linhas.append(re.sub(r' line \d+, col \d+ to line \d+, col \d+ of module \w+', '', hdr))
        for var in ['sessoes', 'identidades', 'canal', 'relogio', 'vivo', 'particaoAtiva', 'ladoDe']:
            m = re.search(r'/\\ ' + var + r' = (.*?)(?= /\\ |$)', flat)
            if not m:
                continue
            s = abrev(m.group(1))
            if var == 'vivo' and 'FALSE' not in s:
                continue
            linhas.append('   %-13s %s' % (var, s))
    return '\n'.join(linhas) + '\n'


def main():
    raiz, saida = sys.argv[1], sys.argv[2]
    dtr = sys.argv[3] if len(sys.argv) > 3 else None
    plano = [l.rstrip('\n').split('\t') for l in open(os.path.join(raiz, 'spec', 'b45-plano.tsv'), encoding='utf-8')
             if l.strip() and not l.startswith('#')]
    resumo = {}
    for l in plano:
        p = os.path.join(raiz, 'runs', 'tlc', 'b45-' + l[1], 'resumo.tsv')
        if os.path.exists(p):
            for x in open(p, encoding='utf-8'):
                f = x.rstrip('\n').split('\t')
                resumo[f[0]] = f
    out = []
    ndiv = 0
    for l in plano:
        d = dict(zip(COLS, l))
        o = os.path.join(raiz, 'runs', 'tlc', 'b45-' + d['grupo'], d['id'] + '.out')
        row = dict(d)
        if not os.path.exists(o) or d['id'] not in resumo:
            row['resultado'] = 'PENDENTE'
            out.append(row)
            continue
        r = parse_out(o)
        rs = resumo[d['id']]
        row.update({k: r[k] for k in ('resultado', 'gerados', 'distintos', 'fila', 'fonte', 'finished', 'workers_out',
                                      'traco', 'mesclar', 'acoes', 'recover')})
        row['wall_s'] = rs[-1].rstrip('s')
        row['commit'] = rs[2]
        res_tsv = rs[10]
        row['diverge_resumo'] = 'nao' if res_tsv == r['resultado'] else 'SIM(%s)' % res_tsv
        ndiv += row['diverge_resumo'] != 'nao'
        if dtr and r['violada']:
            os.makedirs(dtr, exist_ok=True)
            with open(os.path.join(dtr, d['id'] + '.txt'), 'w', encoding='utf-8', newline='\n') as g:
                g.write('# %s (%s, %s) %s\n' % (d['id'], d['rev'], rs[2], o.replace(raiz + os.sep, '')))
                g.write(traco_compacto(r['texto']))
        out.append(row)
    cols = COLS + ['commit', 'resultado', 'distintos', 'gerados', 'fila', 'fonte', 'finished', 'wall_s', 'workers_out',
                   'traco', 'mesclar', 'acoes', 'recover', 'diverge_resumo']
    with open(saida, 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=cols, delimiter='\t', lineterminator='\n', extrasaction='ignore')
        w.writeheader()
        w.writerows(out)
    feitos = sum(1 for x in out if x['resultado'] != 'PENDENTE')
    print('%d linhas do plano, %d com bruto; divergencias com resumo.tsv: %d -> %s' % (len(out), feitos, ndiv, saida))


main()
