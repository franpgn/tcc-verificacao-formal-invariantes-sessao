"""W-2b smoke: stderr, trilha JSONL e linhas perdidas de uma execucao, no mesmo formato para o smoke do W-2b e para o
I-3b (wt-w4/runs/i3b/, so leitura).

Uso: python -I comparar_stderr_trilha.py <dir_execucao> [<dir_execucao> ...]   -> JSON em stdout
stderr.log ou stderr.log.gz; os bytes sao os do arquivo descomprimido, com os fins de linha originais.
"""
import collections
import gzip
import json
import os
import re
import sys

CAB = re.compile(r"^[A-Z][a-z]{2} \d{2}, \d{4} \d{1,2}:\d{2}:\d{2} [AP]M (\S+) (\S+)")


def ler_stderr(d):
    for nome, abrir in (("stderr.log", open), ("stderr.log.gz", gzip.open)):
        p = os.path.join(d, nome)
        if os.path.exists(p):
            with abrir(p, "rb") as f:
                return f.read()
    return None


def stderr(d):
    b = ler_stderr(d)
    if b is None:
        return None
    t = b.decode("utf-8", errors="replace")
    linhas = t.splitlines()
    msgs = [l for l in linhas if re.match(r"^(SEVERE|ERROR|WARN|WARNING|INFO): ", l)]
    cod = collections.Counter()
    for l in msgs:
        m = re.match(r"^\w+: (ISPN\d{6})( \[W-2b\])?", l)
        if m:
            cod[m.group(1) + (" aviso" if m.group(2) else "")] += 1
    pilha = sum(1 for l in linhas if l.startswith("\tat ") or l.startswith("Caused by:") or l.startswith("\t..."))
    bytes_por_fonte = collections.Counter()
    fonte = None
    for l in t.splitlines(keepends=True):
        m = CAB.match(l)
        if m:
            fonte = m.group(1).rsplit(".", 1)[-1]
        bytes_por_fonte[fonte or "(antes do primeiro registro)"] += len(l.encode("utf-8"))
    return {"bytes": len(b), "linhas": len(linhas), "linhas_de_pilha": pilha, "mensagens_por_codigo": dict(cod),
            "bytes_por_fonte": dict(bytes_por_fonte.most_common(6))}


def jsonl(p):
    if not os.path.exists(p):
        return None
    with open(p, encoding="utf-8") as f:
        return [json.loads(x) for x in f if x.strip()]


def trilha(d):
    r = {}
    for nome in ("observaveis", "decisoes"):
        l = jsonl(os.path.join(d, nome + "-rep-001.jsonl"))
        if l is None:
            r[nome] = None
            continue
        fim = l[-1] if l and l[-1].get("tipo") == "fim" else None
        r[nome] = {"linhas": len(l), "por_tipo": dict(collections.Counter(x.get("tipo") for x in l)),
                   "tem_fim": fim is not None, "linhas_perdidas_no_fim": None if fim is None else fim.get("linhas_perdidas"),
                   "curl_lancados": None if fim is None else fim.get("curl_lancados", "ausente")}
    return r


def stdout(d):
    p = os.path.join(d, "stdout.log")
    if not os.path.exists(p):
        return None
    out = open(p, encoding="utf-8", errors="replace").read().splitlines()
    ctl = [l for l in out if l.startswith("Controlador:")]
    perdidas = None
    if ctl:
        m = re.search(r"linhas_jsonl_perdidas=(\d+)", ctl[0])
        perdidas = int(m.group(1)) if m else None
    return {"controlador": ctl, "linhas_jsonl_perdidas": perdidas,
            "hot_rod": [l for l in out if l.startswith("Hot Rod ISPN")],
            "configuracao": [l for l in out if l.startswith("Configuracao:")]}


def main(dirs):
    return {os.path.basename(d.rstrip("/\\")) + " (" + os.path.basename(os.path.dirname(d.rstrip("/\\"))) + ")":
            {"stderr": stderr(d), "trilha": trilha(d), "stdout": stdout(d)} for d in dirs}


if __name__ == "__main__":
    print(json.dumps(main(sys.argv[1:]), indent=1, ensure_ascii=False))
