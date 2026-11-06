"""A4 (06/11/2026): manda pedidos REST de administracao em paralelo e grava os instantes.

Cada URL vira uma thread com o mesmo METODO; grava em SAIDA (TSV, acrescenta): rotulo, metodo, url, t_ini_epoch_ns
(antes do primeiro envio, que recebe o 401 do Digest), t_fim_epoch_ns, status HTTP (ou nome da excecao), corpo (200
caracteres). Prazo de PRAZO_S por pedido (padrao 60 s), para ver a resposta tardia sob corte. Senha so pela variavel
ACAO_SENHA.
Uso: python -I acao_a4.py SAIDA ROTULO METODO URL [URL...]
"""
import os
import sys
import threading
import time
import urllib.error
import urllib.request

saida, rotulo, metodo, urls = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
prazo = float(os.environ.get('PRAZO_S', '60'))
senha = os.environ['ACAO_SENHA']
trava = threading.Lock()


def um(url):
    pm = urllib.request.HTTPPasswordMgrWithDefaultRealm()
    pm.add_password(None, url.split('/rest/')[0] + '/', 'admin', senha)
    op = urllib.request.build_opener(urllib.request.HTTPDigestAuthHandler(pm))
    req = urllib.request.Request(url, data=b'' if metodo == 'POST' else None, method=metodo)
    t0 = time.time_ns()
    try:
        with op.open(req, timeout=prazo) as r:
            st, corpo = r.status, r.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        st, corpo = e.code, e.read().decode('utf-8', 'replace') if e.fp else ''
    except Exception as e:  # prazo, recusa, desconexao
        st, corpo = type(e).__name__, str(e)
    t1 = time.time_ns()
    corpo = corpo.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')[:200]
    with trava:
        with open(saida, 'a', encoding='utf-8') as f:
            f.write('%s\t%s\t%s\t%d\t%d\t%s\t%s\n' % (rotulo, metodo, url, t0, t1, st, corpo))
    print('%s %s %s -> %s em %.3f s' % (rotulo, metodo, url, st, (t1 - t0) / 1e9), flush=True)


if not os.path.exists(saida):
    with open(saida, 'w', encoding='utf-8') as f:
        f.write('rotulo\tmetodo\turl\tt_ini_epoch_ns\tt_fim_epoch_ns\tstatus\tcorpo\n')
ts = [threading.Thread(target=um, args=(u,)) for u in urls]
for t in ts:
    t.start()
for t in ts:
    t.join()
