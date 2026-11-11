"""W-2a I-4 (risco 1 da secao 3.1): as escritas confirmadas pelo no parado chegam aos sobreviventes?

escrever PORTA CACHE PREFIXO ARQ ATE_EPOCH_NS: PUT text/plain de chaves PREFIXO-n no no PORTA (Digest admin) ate o
instante dado ou ate 20 falhas seguidas; grava "chave\\tt_resposta_epoch_ns\\thttp" em ARQ.
verificar ARQ CACHE PORTA [PORTA...]: GET de cada chave confirmada (2xx) em cada porta; imprime o resumo e grava
ARQ.verificacao.tsv.
Uso: python -I escritor_i4.py escrever|verificar ...
"""
import sys
import time
import urllib.error
import urllib.request

AUTH = ('admin', 'infinispan')


def abridor():
    pm = urllib.request.HTTPPasswordMgrWithDefaultRealm()
    pm.add_password(None, 'http://127.0.0.1/', *AUTH)
    for p in range(12501, 12504):
        pm.add_password(None, 'http://127.0.0.1:%d/' % p, *AUTH)
    return urllib.request.build_opener(urllib.request.HTTPDigestAuthHandler(pm))


def escrever(porta, cache, prefixo, arq, ate_ns):
    op = abridor()
    n = 0
    falhas = 0
    with open(arq, 'w', encoding='utf-8') as f:
        while time.time_ns() < ate_ns and falhas < 20:
            k = '%s-%d' % (prefixo, n)
            req = urllib.request.Request('http://127.0.0.1:%d/rest/v2/caches/%s/%s' % (porta, cache, k),
                                         data=('v%d' % n).encode(), method='PUT',
                                         headers={'Content-Type': 'text/plain'})
            try:
                with op.open(req, timeout=1.0) as r:
                    code = r.status
            except urllib.error.HTTPError as e:
                code = e.code
            except Exception as e:  # conexao recusada, desconexao, prazo
                code = type(e).__name__
            t = time.time_ns()
            f.write('%s\t%d\t%s\n' % (k, t, code))
            f.flush()
            ok = isinstance(code, int) and 200 <= code < 300
            falhas = 0 if ok else falhas + 1
            n += 1
            time.sleep(0.02)


def verificar(arq, cache, portas):
    op = abridor()
    conf = []
    for l in open(arq, encoding='utf-8'):
        k, t, code = l.rstrip('\n').split('\t')
        if code.isdigit() and 200 <= int(code) < 300:
            conf.append((k, int(t)))
    linhas = []
    falta = {p: [] for p in portas}
    for k, t in conf:
        for p in portas:
            try:
                with op.open('http://127.0.0.1:%d/rest/v2/caches/%s/%s' % (p, cache, k), timeout=2.0) as r:
                    code = r.status
            except urllib.error.HTTPError as e:
                code = e.code
            except Exception as e:
                code = type(e).__name__
            linhas.append('%s\t%d\t%d\t%s' % (k, t, p, code))
            if code != 200:
                falta[p].append((k, t))
    with open(arq + '.verificacao.tsv', 'w', encoding='utf-8') as f:
        f.write('\n'.join(linhas) + '\n')
    print('confirmadas=%d' % len(conf))
    for p in portas:
        print('porta %d: ausentes=%d %s' % (p, len(falta[p]), [k for k, _ in falta[p]][:20]))
    return conf, falta


if __name__ == '__main__':
    if sys.argv[1] == 'escrever':
        escrever(int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5], int(sys.argv[6]))
    else:
        verificar(sys.argv[2], sys.argv[3], [int(x) for x in sys.argv[4:]])
