"""W-2a item 9 (I-3, I-4, I-5): resume uma execucao do parada.sh ou do pausa_parada.sh.

Instantes em segundos relativos ao corte (I-3), ao pedido do AC-5 (I-4 com acao) ou ao inicio do escritor (I-4
sem acao), e a pausa (I-5): pedido e retorno do AC-5 (epoch-ns do SmokeAtuador, EpochClock), 204, ultima saude
respondida e primeira recusa (vigia do harness), saida do conteiner, vistas ISPN000094/ISPN000093 dos nos, e as
violacoes por invariante, separando as que caem em [t_pedido, t_pedido + prazo do tipo].
Uso: python -I analisar_parada.py DIR
"""
import csv
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

d = Path(sys.argv[1])
F = []
for l in (d / 'fases.tsv').read_text(encoding='utf-8').splitlines():
    ns, utc, txt = l.split('\t')
    F.append((int(ns), txt))


def t_de(prefixo):
    for ns, txt in F:
        if txt.startswith(prefixo):
            return ns
    return None


par = (d / 'parametros.txt').read_text(encoding='utf-8').strip()
print('#', par)
smoke = (d / 'smoke.txt').read_text(encoding='utf-8', errors='replace') if (d / 'smoke.txt').exists() else ''
m = re.search(r'AC-5 aplicar.*?: (ResultadoAtuacao\[.*?\]) t_pedido_epoch_ns=(\d+) t_retorno_epoch_ns=(\d+) prazo_ns=(\d+)',
              smoke)
t_ped = int(m.group(2)) if m else None
t_ret = int(m.group(3)) if m else None
prazo = int(m.group(4)) if m else 5_000_000_000
t0 = t_de('corte') or t_ped or t_de('pausa') or t_de('escritor-inicio')


def rel(ns):
    return None if ns is None or t0 is None else round((ns - t0) / 1e9, 3)


print('fases:', [(rel(ns), txt) for ns, txt in F])
if m:
    print('AC-5: %s pedido=%s retorno=%s prazo=%.1f s' % (m.group(1)[:200], rel(t_ped), rel(t_ret), prazo / 1e9))
elif smoke:
    print('smoke:', smoke.strip()[-300:])
vig = []
if (d / 'vigia.tsv').exists():
    for l in (d / 'vigia.tsv').read_text(encoding='utf-8').splitlines():
        ns, txt = l.split('\t', 1)
        vig.append((int(ns), txt))
print('vigia:', [(rel(ns), txt) for ns, txt in vig][:20])
recusa = next((ns for ns, txt in vig if txt.startswith('saude=') and txt != 'saude=200' and (t_ped is None or ns > t_ped)), None)
saida = next((ns for ns, txt in vig if txt.startswith('conteiner=') and txt not in ('conteiner=running', 'conteiner=paused')), None)


def ts_log(linha):
    mm = re.search(r'(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d),(\d{3})', linha)
    if not mm:
        return None
    dt = datetime.strptime(mm.group(1), '%Y-%m-%d %H:%M:%S').replace(tzinfo=timezone.utc)
    return int(dt.timestamp()) * 1_000_000_000 + int(mm.group(2)) * 1_000_000


vistas = []
paradas = []
for f in sorted(d.glob('servidor-isn*.log')):
    no = f.stem.split('-')[1]
    for l in f.read_text(encoding='utf-8', errors='replace').splitlines():
        if 'ISPN000094' in l or 'ISPN000093' in l:
            mm = re.search(r'(ISPN00009[34]): [^\[]*\[([^\]]*)\] \((\d+)\)', l)
            vistas.append((ts_log(l), no, mm.group(1) if mm else '?', mm.group(2) if mm else '?', mm.group(3) if mm else '?'))
        if 'ISPN080002' in l or 'ISPN080003' in l or 'ISPN000080' in l:
            paradas.append((ts_log(l), no, re.search(r'ISPN0\d+', l).group(0)))
for t, no, cod, v, n in sorted(x for x in vistas if x[0]):
    if t0 is None or t >= t0 - 1_000_000_000:
        print('vista %s %s %s [%s] (%s)' % (rel(t), no, cod, v, n))
for t, no, cod in sorted(x for x in paradas if x[0]):
    print('servidor %s %s %s' % (rel(t), no, cod))
alvo = re.search(r'alvo=(isn\d)', par).group(1)
maioria = [t for t, no, cod, v, n in vistas if t and no != alvo and cod == 'ISPN000094' and n == '2' and (t0 is None or t > t0)]
t_vista = min(maioria) if maioria else None
print('INSTANTES: pedido=%s retorno=%s primeira_recusa=%s saida_conteiner=%s vista_2_na_maioria=%s | t_parada(pedido->recusa)=%s s' % (
    rel(t_ped), rel(t_ret), rel(recusa), rel(saida), rel(t_vista),
    None if (recusa is None or t_ped is None) else round((recusa - t_ped) / 1e9, 3)))
vf = d / 'violacoes-rep-001.csv'
if vf.exists():
    cont = {}
    dentro = {}
    with open(vf, encoding='utf-8') as f:
        for row in csv.DictReader(f):
            s = row['instante'].rstrip('Z')
            base, frac = (s.split('.') + ['0'])[:2]
            t = int(datetime.strptime(base, '%Y-%m-%dT%H:%M:%S').replace(tzinfo=timezone.utc).timestamp()) * 10**9 \
                + int((frac + '000000000')[:9])
            inv = row['invariante']
            cont[inv] = cont.get(inv, 0) + 1
            if t_ped is not None and t_ped <= t <= t_ped + prazo:
                dentro[inv] = dentro.get(inv, 0) + 1
    print('violacoes: total=%s dentro_de_[pedido,pedido+prazo]=%s' % (cont, dentro))
if (d / 'verificacao.txt').exists():
    print('I-4 verificacao:', (d / 'verificacao.txt').read_text(encoding='utf-8').strip().replace('\n', ' | ')[:400])
    esc = [l.split('\t') for l in (d / 'escritas.tsv').read_text(encoding='utf-8').splitlines()]
    ok = [e for e in esc if e[2].isdigit() and 200 <= int(e[2]) < 300]
    ult = [e for e in ok if t_ped is not None and int(e[1]) >= t_ped - 5_000_000_000 and int(e[1]) <= t_ped + prazo]
    print('I-4 escritas: tentadas=%d confirmadas=%d confirmadas em [pedido-5 s, pedido+prazo]=%d ultima_confirmada=%s primeira_falha=%s' % (
        len(esc), len(ok), len(ult), rel(int(ok[-1][1])) if ok else None,
        next((rel(int(e[1])) + 0 for e in esc if not (e[2].isdigit() and 200 <= int(e[2]) < 300)), None)))
so = (d / 'stdout.log').read_text(encoding='utf-8', errors='replace') if (d / 'stdout.log').exists() else ''
for l in so.splitlines():
    if re.search(r'Violacoes detectadas|ERROR_TRANSPORT|UNAVAILABLE', l):
        print('stdout:', l.strip()[:160])
