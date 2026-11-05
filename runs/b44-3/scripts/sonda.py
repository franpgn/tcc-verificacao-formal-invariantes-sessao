"""B-44.3: sonda REST de vista e disponibilidade, uma thread por no, relogio de parede do host em ms.

Uso: python sonda.py SAIDA.tsv DURACAO_S [periodo_s=0.2]
Colunas: t_ini_ms t_fim_ms no http cluster_size coord node_address membros av_sessions av_sessions_async
"""
import json
import sys
import threading
import time
import urllib.request

PORTAS = {"isn1": 12444, "isn2": 12445, "isn3": 12446}


def abridor(porta):
    mgr = urllib.request.HTTPPasswordMgrWithDefaultRealm()
    mgr.add_password(None, "http://127.0.0.1:%d/" % porta, "admin", "infinispan")
    return urllib.request.build_opener(urllib.request.HTTPDigestAuthHandler(mgr))


def get(op, url):
    try:
        with op.open(url, timeout=3) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, ""
    except Exception as e:  # noqa: BLE001
        return 0, type(e).__name__


def laco(no, saida, fim, periodo, trava):
    porta = PORTAS[no]
    op = abridor(porta)
    base = "http://127.0.0.1:%d/rest/v2" % porta
    while time.time() < fim:
        t0 = int(time.time() * 1000)
        st, corpo = get(op, base + "/container")
        tam = coord = end = membros = "?"
        if st == 200:
            try:
                d = json.loads(corpo)
                tam = d.get("cluster_size")
                coord = d.get("coordinator_address")
                end = d.get("node_address")
                membros = ",".join(d.get("cluster_members") or [])
            except ValueError:
                pass
        _, a1 = get(op, base + "/caches/sessions?action=get-availability")
        _, a2 = get(op, base + "/caches/sessions-async?action=get-availability")
        t1 = int(time.time() * 1000)
        with trava:
            saida.write("\t".join(map(str, [t0, t1, no, st, tam, coord, end, membros, a1.strip()[:20], a2.strip()[:20]])) + "\n")
            saida.flush()
        time.sleep(periodo)


def main():
    arq, dur = sys.argv[1], float(sys.argv[2])
    periodo = float(sys.argv[3]) if len(sys.argv) > 3 else 0.2
    fim = time.time() + dur
    trava = threading.Lock()
    with open(arq, "a", encoding="utf-8", newline="\n") as saida:
        ts = [threading.Thread(target=laco, args=(n, saida, fim, periodo, trava)) for n in PORTAS]
        for t in ts:
            t.start()
        for t in ts:
            t.join()


if __name__ == "__main__":
    main()
