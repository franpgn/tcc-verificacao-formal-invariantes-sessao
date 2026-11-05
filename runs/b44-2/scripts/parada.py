# W-2a tarefa zero (b): POST de parada ao proprio no, instantes de resposta, da ultima saude respondida e da
# primeira recusa (saude e Hot Rod usam o mesmo conector na porta 11222), e saude dos outros dois.
import sys, time, json, urllib.request, subprocess, socket
porta, caminho = int(sys.argv[1]), sys.argv[2]
outros = [int(x) for x in sys.argv[3].split(',')]
def ms(): return time.time() * 1000
def saude(p, to=1.0):
    try:
        with urllib.request.urlopen('http://127.0.0.1:%d/rest/v2/container/health/status' % p, timeout=to) as r:
            return r.status, r.read().decode()
    except Exception as e:
        return None, type(e).__name__
t0 = ms()
r = subprocess.run(['curl', '-s', '-o', '-', '-w', '\n%{http_code} %{time_total}', '--digest', '-u', 'admin:infinispan',
                    '-X', 'POST', 'http://127.0.0.1:%d%s' % (porta, caminho)], capture_output=True, text=True)
t1 = ms()
print('pedido POST %s -> %s (t=%.0f ms)' % (caminho, r.stdout.strip().replace('\n', ' | '), t1 - t0))
ultima = None
while ms() - t0 < 15000:
    c, b = saude(porta, 0.5)
    if c is None:
        print('primeira recusa da saude: +%.0f ms depois do pedido (%s); ultima resposta +%s ms' % (ms() - t0, b,
              'nenhuma' if ultima is None else '%.0f' % (ultima - t0)))
        # recusa TCP direta na porta (o mesmo conector do Hot Rod)
        s = socket.socket(); s.settimeout(0.5)
        try:
            s.connect(('127.0.0.1', porta)); print('connect TCP na porta: aceito (o encaminhamento do podman aceita?)')
        except Exception as e:
            print('connect TCP na porta: %s' % type(e).__name__)
        finally:
            s.close()
        break
    ultima = ms()
    time.sleep(0.05)
else:
    print('o no continuou respondendo por 15 s')
for p in outros:
    print('saude porta %d: %s' % (p, saude(p)))
