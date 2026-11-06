"""P-64: tamanho do log de cada no e recusas vistas pela sonda, numa execucao do smoke (antes e depois do ajuste).

Le <execucao>/servidores/isn{1,2,3}.log.gz (texto de `podman logs -t`) e <execucao>/sonda-isn{1,2,3}.tsv.gz. Imprime
um objeto JSON: por no, bytes e linhas do log, linhas ISPN005003 e ISPN000136, linhas de pilha ("\tat "), e as
respostas da sonda por resultado (OK, recusa do cache ignorado, AvailabilityException, outros), para mostrar que o
servidor continua recusando igual (o que muda e so o que vai ao log).

Uso: python -I scripts/medir_log.py <dir-da-execucao> [<dir> ...]
"""
import gzip
import json
import sys
from pathlib import Path


def log_do_no(p):
    out = {"bytes": 0, "linhas": 0, "ISPN005003": 0, "ISPN000136": 0, "linhas_de_pilha": 0}
    with gzip.open(p, "rt", encoding="utf-8", errors="replace") as f:
        for linha in f:
            out["bytes"] += len(linha.encode("utf-8"))
            out["linhas"] += 1
            if "ISPN005003" in linha:
                out["ISPN005003"] += 1
            if "ISPN000136" in linha:
                out["ISPN000136"] += 1
            if "\tat " in linha:
                out["linhas_de_pilha"] += 1
    return out


def sonda(p):
    c = {"OK": 0, "cache_ignorado": 0, "availability": 0, "outro": 0}
    with gzip.open(p, "rt", encoding="utf-8", errors="replace") as f:
        next(f, None)
        for linha in f:
            campos = linha.rstrip("\n").split("\t")
            if len(campos) < 6:
                continue
            res, msg = campos[5], "\t".join(campos[6:])
            if res == "OK":
                c["OK"] += 1
            elif "CacheUnavailable" in msg or "ignored" in msg.lower() or res == "HotRodClientException" and "Unavailable" in msg:
                c["cache_ignorado"] += 1
            elif "Availability" in msg or "Availability" in res:
                c["availability"] += 1
            else:
                c["outro"] += 1
    return c


def main(dirs):
    saida = {}
    for d in map(Path, dirs):
        e = {}
        for n in ("isn1", "isn2", "isn3"):
            x = {}
            lg = d / "servidores" / ("%s.log.gz" % n)
            if lg.exists():
                x["log"] = log_do_no(lg)
            sd = d / ("sonda-%s.tsv.gz" % n)
            if sd.exists():
                x["sonda"] = sonda(sd)
            e[n] = x
        saida[d.name] = e
    print(json.dumps(saida, indent=1, ensure_ascii=True))


if __name__ == "__main__":
    main(sys.argv[1:])
