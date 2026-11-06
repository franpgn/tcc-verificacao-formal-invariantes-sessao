"""A4 (06/11/2026): resume uma execucao do a4.sh (ignored-caches ou parada mandados ao alvo).

Instantes em segundos relativos ao pedido da acao (t_pedido = t_ini do primeiro pedido POST em acoes.tsv; no
corte-controle, o instante "sem-acao" de fases.tsv). Le: fases.tsv, acoes.tsv, sonda-isnN.tsv.gz, vigia-ignorados.tsv,
vigia-cluster.tsv, rep-001.csv (carga S3) e servidores/isnN.log. Escreve um JSON (stdout) com:
  - acoes: cada pedido REST (status e duracao);
  - sondas: por no e cache, a primeira operacao recusada depois do pedido, o ultimo put confirmado que comecou depois
    do pedido, as contagens por resultado nas janelas e, no desfazer, a primeira operacao servida;
  - ignorados: transicoes da lista de caches ignorados em cada no (vigia a cada 100 ms);
  - cluster: transicoes de tamanho e disponibilidade (vigia a cada 500 ms);
  - carga: erros da carga S3 por servidor de aplicacao, por janela;
  - vistas: ISPN000094/ISPN000093 dos logs dos servidores.
Uso: python -I analisar_a4.py DIR
"""
import csv
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


par = next((txt for ns, txt in F if txt.startswith('parametros ')), '')
alvo = re.search(r'alvo=(isn\d)', par).group(1)
caso = re.search(r'caso=(\S+)', F[0][1]).group(1)
portas = {'12901': 'isn1', '12902': 'isn2', '12903': 'isn3'}

acoes = []
if (d / 'acoes.tsv').exists():
    with open(d / 'acoes.tsv', encoding='utf-8') as f:
        for row in csv.DictReader(f, delimiter='\t'):
            acoes.append(row)
ped = [a for a in acoes if a['rotulo'] in ('ignorar', 'parar')]
des = [a for a in acoes if a['rotulo'] == 'desfazer']
t_ped = min(int(a['t_ini_epoch_ns']) for a in ped) if ped else fase('sem-acao')
t_des = min(int(a['t_ini_epoch_ns']) for a in des) if des else None
t_corte = fase('corte-inicio')
t_cura = fase('cura-inicio')
t_fusao = fase('fusao vista')


def rel(ns):
    return None if ns is None or t_ped is None else round((ns - t_ped) / 1e9, 3)


out = {'execucao': d.name, 'caso': caso, 'alvo': alvo,
       'instantes_rel_pedido_s': {'corte': rel(t_corte), 'pedido': 0.0 if t_ped else None, 'cura': rel(t_cura),
                                  'fusao_vista_harness': rel(t_fusao), 'desfazer': rel(t_des)},
       'acoes': [{'rotulo': a['rotulo'], 'metodo': a['metodo'], 'url': a['url'].split('/rest/')[1],
                  'ini_s': rel(int(a['t_ini_epoch_ns'])), 'duracao_s': round((int(a['t_fim_epoch_ns']) - int(a['t_ini_epoch_ns'])) / 1e9, 3),
                  'status': a['status'], 'corpo': a['corpo'][:120]} for a in acoes]}

# ---- sondas
fim_janela = t_des or t_cura or (t_ped + 60_000_000_000 if t_ped else None)
sondas = {}
for no in ('isn1', 'isn2', 'isn3'):
    p = d / ('sonda-%s.tsv.gz' % no)
    if not p.exists():
        continue
    linhas = []
    with gzip.open(p, 'rt', encoding='utf-8') as f:
        next(f)
        for l in f:
            c = l.rstrip('\n').split('\t')
            if len(c) < 6 or not c[0].isdigit():
                continue
            linhas.append((int(c[0]), int(c[1]), c[3], c[4], c[5], c[6] if len(c) > 6 else ''))
    for cache in ('sessions-async', 'counters-async'):
        L = sorted(x for x in linhas if x[2] == cache and x[3] in ('put', 'get'))
        r = {}
        if t_ped:
            antes = [x for x in L if x[1] <= t_ped]
            r['ops_antes_do_pedido'] = len(antes)
            r['recusas_nos_30s_antes'] = sum(1 for x in antes if x[1] > t_ped - 30_000_000_000 and x[4] != 'OK')
            lim = t_des or 1 << 62
            # A4: primeira CacheUnavailableException (o efeito do ignored-caches)
            cu = next((x for x in L if x[1] > t_ped and 'CacheUnavailable' in x[5]), None)
            r['primeira_cache_indisponivel'] = None if cu is None else {
                'fim_s': rel(cu[1]), 'ini_s': rel(cu[0]), 'op': cu[3]}
            # escritas: put e o sinal de "confirma escrita" (sob corte em ASYNC, o put no no isolado e confirmado;
            # o get de chave de outro dono espera o prazo de 1 s, por isso nao entra aqui)
            P = [x for x in L if x[3] == 'put']
            pf = next((x for x in P if x[1] > t_ped and x[4] != 'OK'), None)
            r['primeiro_put_recusado'] = None if pf is None else {
                'fim_s': rel(pf[1]), 'ini_s': rel(pf[0]), 'resultado': pf[4], 'mensagem': pf[5][:200]}
            ok_put = [x for x in P if x[0] >= t_ped and x[4] == 'OK' and x[0] < lim]
            r['puts_confirmados_iniciados_apos_pedido'] = len(ok_put)
            r['ultimo_put_confirmado_iniciado_apos_pedido_fim_s'] = rel(ok_put[-1][1]) if ok_put else None
            ok_any = [x for x in L if x[0] >= t_ped and x[4] == 'OK' and x[0] < lim]
            r['ultima_op_servida_iniciada_apos_pedido_fim_s'] = rel(ok_any[-1][1]) if ok_any else None
            jan = [x for x in L if t_ped <= x[0] < (fim_janela or 1 << 62)]
            cont = {}
            for x in jan:
                k = x[3] + ':' + ('OK' if x[4] == 'OK' else ('CacheUnavailable' if 'CacheUnavailable' in x[5] else x[4]))
                cont[k] = cont.get(k, 0) + 1
            r['resultados_pedido_ate_fim_da_janela'] = dict(sorted(cont.items()))
            msgs = {}
            for x in jan:
                if x[4] != 'OK':
                    k = x[4] + ': ' + re.sub(r"[0-9a-f-]{8,}|a4-isn\d-\S+|value=\S+", '*', x[5])[:150]
                    msgs[k] = msgs.get(k, 0) + 1
            r['mensagens_de_recusa'] = dict(sorted(msgs.items(), key=lambda kv: -kv[1])[:4])
        if t_des:
            ok = next((x for x in L if x[0] >= t_des and x[4] == 'OK'), None)
            okp = next((x for x in L if x[0] >= t_des and x[4] == 'OK' and x[3] == 'put'), None)
            r['desfazer_primeiro_put_servido_fim_s_rel_desfazer'] = None if okp is None else round((okp[1] - t_des) / 1e9, 3)
            ult_rec = [x for x in L if x[1] > t_des and x[4] != 'OK']
            r['desfazer_primeira_op_servida_fim_s_rel_desfazer'] = None if ok is None else round((ok[1] - t_des) / 1e9, 3)
            r['desfazer_ultima_recusa_fim_s_rel_desfazer'] = round((ult_rec[-1][1] - t_des) / 1e9, 3) if ult_rec else None
            dep = [x for x in L if x[0] >= t_des]
            cont = {}
            for x in dep:
                k = x[3] + ':' + ('OK' if x[4] == 'OK' else ('CacheUnavailable' if 'CacheUnavailable' in x[5] else x[4]))
                cont[k] = cont.get(k, 0) + 1
            r['resultados_apos_desfazer'] = dict(sorted(cont.items()))
        if t_cura and t_ped:
            # depois da cura e antes do desfazer: o alvo continua recusando?
            pc = [x for x in L if x[0] >= (t_fusao or t_cura) and x[0] < (t_des or 1 << 62)]
            cont = {}
            for x in pc:
                k = x[3] + ':' + ('OK' if x[4] == 'OK' else ('CacheUnavailable' if 'CacheUnavailable' in x[5] else x[4]))
                cont[k] = cont.get(k, 0) + 1
            r['resultados_da_fusao_ao_desfazer'] = dict(sorted(cont.items()))
        if t_fusao and t_des and t_des < t_fusao:
            # desfazer antes da fusao (caso corte-sessoes-detec): o que o alvo faz entre o desfazer e a fusao, e quando
            # volta a confirmar escrita depois da fusao
            pc = [x for x in L if t_des <= x[0] < t_fusao]
            cont = {}
            for x in pc:
                k = x[3] + ':' + ('OK' if x[4] == 'OK' else ('CacheUnavailable' if 'CacheUnavailable' in x[5]
                                                         else ('Availability' if 'AvailabilityException' in x[5] else x[4])))
                cont[k] = cont.get(k, 0) + 1
            r['resultados_do_desfazer_a_fusao'] = dict(sorted(cont.items()))
            okf = next((x for x in L if x[0] >= t_des and x[3] == 'put' and x[4] == 'OK'), None)
            r['primeiro_put_ok_apos_desfazer_rel_fusao_s'] = None if okf is None else round((okf[1] - t_fusao) / 1e9, 3)
        sondas['%s/%s' % (no, cache)] = r
out['sondas'] = sondas

# ---- vigia: ignorados e cluster
ign = []
if (d / 'vigia-ignorados.tsv').exists():
    for l in (d / 'vigia-ignorados.tsv').read_text(encoding='utf-8').splitlines()[1:]:
        t0, t1, no, tipo, v = l.split('\t')
        if tipo == 'transicao':
            ign.append({'no': no, 'ini_s': rel(int(t0)), 'fim_s': rel(int(t1)), 'ignorados': v})
out['ignorados_transicoes'] = ign
clu = []
if (d / 'vigia-cluster.tsv').exists():
    for l in (d / 'vigia-cluster.tsv').read_text(encoding='utf-8').splitlines()[1:]:
        t0, t1, no, v = l.split('\t')
        clu.append({'no': no, 'ini_s': rel(int(t0)), 'estado': v})
out['cluster_transicoes'] = clu

# ---- carga S3: erros por servidor de aplicacao e janela
ev = d / 'rep-001.csv'
if ev.exists() and t_ped:
    jan = {'30s_antes_do_pedido': (t_ped - 30_000_000_000, t_ped)}
    if t_des:
        jan['pedido_ao_desfazer'] = (t_ped, t_des)
        jan['30s_apos_desfazer'] = (t_des, t_des + 30_000_000_000)
    if t_cura:
        jan['pedido_a_cura'] = (t_ped, t_cura)
        jan['cura_ao_fim'] = (t_cura, 1 << 62)
    else:
        jan['pedido_ao_fim'] = (t_ped, 1 << 62)
    res = {k: {} for k in jan}
    with open(ev, encoding='utf-8') as f:
        for row in csv.DictReader(f):
            t = int(row['start_ns'])
            rp = portas.get(row['replica'].split(':')[-1], row['replica'])
            for k, (a, b) in jan.items():
                if a <= t < b:
                    dd = res[k].setdefault(rp, {})
                    dd[row['return_code']] = dd.get(row['return_code'], 0) + 1
    out['carga_por_janela'] = res
so = (d / 'stdout.log').read_text(encoding='utf-8', errors='replace') if (d / 'stdout.log').exists() else ''
out['carga_stdout'] = [l.strip()[:160] for l in so.splitlines()
                       if re.search(r'^Violacoes detectadas|^Total de operacoes|ERROR_TRANSPORT|ERROR_UNAVAILABLE|Hot Rod ISPN', l)][:30]
ex = d / 'exit.txt'
out['carga_exit'] = ex.read_text().strip() if ex.exists() else None


# ---- vistas e mensagens relevantes dos servidores
def ts_log(linha):
    # instante proprio do servidor (UTC, ms), depois do prefixo do "podman logs -t" (segundos, hora local)
    mm = re.search(r'(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d),(\d{3})', linha)
    if not mm:
        return None
    dt = datetime.strptime(mm.group(1), '%Y-%m-%d %H:%M:%S').replace(tzinfo=timezone.utc)
    return int(dt.timestamp()) * 1_000_000_000 + int(mm.group(2)) * 1_000_000


vistas = []
outros = []
recusas_log = {}
def linhas_log(f):
    # servidores/isnN.log, ou isnN.log.gz depois da compressao final
    if f.suffix == '.gz':
        with gzip.open(f, 'rt', encoding='utf-8', errors='replace') as g:
            return g.read().splitlines()
    return f.read_text(encoding='utf-8', errors='replace').splitlines()


arqs_log = {}
if (d / 'servidores').exists():
    for f in sorted((d / 'servidores').glob('isn*.log*')):
        arqs_log.setdefault(f.name.split('.')[0], f)
for no, f in sorted(arqs_log.items()):
    for l in linhas_log(f):
        if 'ISPN005003' in l:
            recusas_log[no] = recusas_log.get(no, 0) + 1
            continue
        t = ts_log(l)
        if t is None or (t_ped and t < t_ped - 40_000_000_000):
            continue
        m = re.search(r'(ISPN00009[34])[^\[]*\[([^\]]*)\] \((\d+)\)', l)
        if m:
            vistas.append({'no': no, 't_s': rel(t), 'cod': m.group(1), 'membros': m.group(3)})
        elif re.search(r'ISPN0004(76|77)|ISPN000136|ISPN080002|ISPN080003|ISPN000080|ISPN100012|ISPN100013|ISPN000310|ISPN000311|ignor|ISPN100000|ISPN100001|ISPN000476', l):
            outros.append({'no': no, 't_s': rel(t), 'linha': re.sub(r'^\S+\s+', '', l)[:200]})
out['vistas'] = vistas
out['servidor_ISPN005003_por_no'] = recusas_log
# sem as repeticoes do ISPN000136 (erro de comando sob corte, uma por operacao)
out['servidor_ISPN000136_por_no'] = {}
for o in outros:
    if 'ISPN000136' in o['linha']:
        out['servidor_ISPN000136_por_no'][o['no']] = out['servidor_ISPN000136_por_no'].get(o['no'], 0) + 1
out['servidor_mensagens'] = [o for o in outros if 'ISPN000136' not in o['linha']][:60]
print(json.dumps(out, ensure_ascii=False, indent=1))
