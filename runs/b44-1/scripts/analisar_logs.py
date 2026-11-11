"""B-44.1: conta, por fase (fases.tsv) e por no, os marcadores de reinicio e de rebalanceamento.

Marcadores (stdout do servidor, nivel INFO/WARN, relogio UTC do container):
  ISPN000599 <cache>  : o cache <cache> foi (re)iniciado nesse no (aviso emitido no start do cache);
  ISPN100002 <cache>  : inicio de rebalanceamento do cache <cache> (linhas CLUSTER e LIFECYCLE; conta-se CLUSTER/LIFECYCLE juntas);
  ISPN000094          : nova visao JGroups;
  ISPN000500/000961   : rejeicao de configuracao.
Uso: python -I scripts/analisar_logs.py fases.tsv servidores/inc1 servidores/inc2 > logs-por-fase.tsv
"""
import datetime
import re
import sys
from collections import Counter

CACHES = ("sessions", "counters", "sessions-async", "counters-async")
RX_TS = re.compile(r"(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d{3}) +(INFO|WARN|ERROR|FATAL|DEBUG)")


def ts_log(s):
    return datetime.datetime.strptime(s, "%Y-%m-%d %H:%M:%S,%f").replace(tzinfo=datetime.timezone.utc)


def fases(caminho):
    out = []
    for linha in open(caminho, encoding="utf-8"):
        t, txt = linha.rstrip("\n").split("\t", 1)
        out.append((datetime.datetime.fromisoformat(t.replace("Z", "+00:00")), txt))
    return out


def marcadores(linha):
    m = []
    for c in CACHES:
        if f"ISPN000599: Configuration for cache '{c}'" in linha:
            m.append(f"start:{c}")
        if f"[Context={c}]" in linha and "ISPN100002" in linha:
            m.append(f"rebal:{c}")
    if "ISPN000094" in linha:
        m.append("visao")
    if "ISPN000500" in linha and "ERROR" in linha:
        m.append("rej500")
    if "ISPN000961" in linha and "Caused" not in linha:
        m.append("rej961")
    return m


def main(fases_tsv, *dirs):
    fs = fases(fases_tsv)
    cont = Counter()
    for d in dirs:
        for no in ("isn1", "isn2", "isn3"):
            try:
                linhas = open(f"{d}/{no}-stdout.log", encoding="utf-8", errors="replace")
            except FileNotFoundError:
                continue
            for linha in linhas:
                m = RX_TS.search(linha)
                if not m:
                    continue
                t = ts_log(m.group(1))
                fase = "(antes da 1a fase)"
                for tf, txt in fs:
                    if tf <= t:
                        fase = txt
                for k in marcadores(linha):
                    cont[(fase, no, k)] += 1
    print("fase\tno\tmarcador\tn")
    ordem = {txt: i for i, (_, txt) in enumerate(fs)}
    for (fase, no, k), n in sorted(cont.items(), key=lambda x: (ordem.get(x[0][0], -1), x[0][1], x[0][2])):
        print(f"{fase}\t{no}\t{k}\t{n}")


if __name__ == "__main__":
    main(*sys.argv[1:])
