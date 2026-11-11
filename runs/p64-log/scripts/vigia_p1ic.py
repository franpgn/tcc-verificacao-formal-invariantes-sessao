"""P1-IC (06/11/2026): copia do vigia_a4.py do A4 (wt-a4/runs/a4-teste/scripts), sem mudanca de logica: vigia REST dos tres nos.

Por no, duas threads:
  - lista de caches ignorados (GET /rest/v2/server/ignored-caches) a cada 100 ms; grava so as transicoes
    (e um batimento a cada 5 s) em DIR/vigia-ignorados.tsv;
  - a cada 500 ms, get-availability do sessions-async e do counters-async (leitura local do no); grava as transicoes
    em DIR/vigia-cluster.tsv.
Sem GET /rest/v2/container: no no isolado, esse pedido espera uma resposta do cluster (CacheManagerInfo.
isRebalancingEnabled -> CompletableFuture.get) DENTRO de uma thread "non-blocking" da REST e trava o laco de eventos
que tambem atende os pedidos de ignored-caches (despejo de threads da corte-ignorar-r2, 06/11). A corte-ignorar-r1 e
a corte-ignorar-r2 rodaram com a versao anterior, que fazia esse GET.
Para quando ARQ_FIM existe. Senha so pela variavel VIGIA_SENHA.
Uso: python -I vigia_a4.py DIR ARQ_FIM PORTA_ISN1 PORTA_ISN2 PORTA_ISN3
"""
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

d, fim = sys.argv[1], sys.argv[2]
portas = {'isn1': int(sys.argv[3]), 'isn2': int(sys.argv[4]), 'isn3': int(sys.argv[5])}
senha = os.environ['VIGIA_SENHA']
trava = threading.Lock()


def abridor():
    pm = urllib.request.HTTPPasswordMgrWithDefaultRealm()
    for p in portas.values():
        pm.add_password(None, 'http://127.0.0.1:%d/' % p, 'admin', senha)
    return urllib.request.build_opener(urllib.request.HTTPDigestAuthHandler(pm))


def get(op, url, prazo=1.0):
    try:
        with op.open(url, timeout=prazo) as r:
            return r.status, r.read().decode('utf-8', 'replace').strip()
    except urllib.error.HTTPError as e:
        return e.code, ''
    except Exception as e:  # recusa, prazo, desconexao
        return type(e).__name__, ''


def gravar(arq, campos):
    with trava:
        with open(arq, 'a', encoding='utf-8') as f:
            f.write('\t'.join(str(c) for c in campos) + '\n')


def ignorados(no, porta):
    op = abridor()
    arq = os.path.join(d, 'vigia-ignorados.tsv')
    ultimo, bat = None, 0
    while not os.path.exists(fim):
        t0 = time.time_ns()
        st, corpo = get(op, 'http://127.0.0.1:%d/rest/v2/server/ignored-caches' % porta)
        t1 = time.time_ns()
        if st == 200:
            try:
                v = ','.join(sorted(json.loads(corpo))) or '-'
            except ValueError:
                v = 'json-invalido'
        else:
            v = 'erro:%s' % st
        if v != ultimo or t1 - bat > 5_000_000_000:
            gravar(arq, (t0, t1, no, 'transicao' if v != ultimo else 'batimento', v))
            bat = t1
        ultimo = v
        time.sleep(0.1)


def cluster(no, porta):
    op = abridor()
    arq = os.path.join(d, 'vigia-cluster.tsv')
    ultimo = None
    while not os.path.exists(fim):
        t0 = time.time_ns()
        av = []
        for cache in ('sessions-async', 'counters-async'):
            s2, c2 = get(op, 'http://127.0.0.1:%d/rest/v2/caches/%s?action=get-availability' % (porta, cache))
            av.append('%s=%s' % (cache, c2 if s2 == 200 else 'erro:%s' % s2))
        v = ' '.join(av)
        t1 = time.time_ns()
        if v != ultimo:
            gravar(arq, (t0, t1, no, v))
        ultimo = v
        time.sleep(0.5)


gravar(os.path.join(d, 'vigia-ignorados.tsv'), ('t_ini_ns', 't_fim_ns', 'no', 'tipo', 'ignorados'))
gravar(os.path.join(d, 'vigia-cluster.tsv'), ('t_ini_ns', 't_fim_ns', 'no', 'estado'))
ts = []
for no, porta in portas.items():
    for alvo in (ignorados, cluster):
        t = threading.Thread(target=alvo, args=(no, porta), daemon=True)
        t.start()
        ts.append(t)
for t in ts:
    t.join()
