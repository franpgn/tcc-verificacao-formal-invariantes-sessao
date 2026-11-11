"""Recontagem do comportamento do controlador na avaliacao fatorial, direto dos brutos (relatorio tecnico, cap. 4).

Uso: python -E -P trilhas_controlador.py <campanha-s3-fatorial>
     (a pasta com as exec-NNNN-*; so leitura; so biblioteca padrao)

Le, por execucao: checagem.json, execucao.json, marcadores.txt, decisoes-rep-001.jsonl (trilha do controlador),
observaveis-rep-001.jsonl (linha fim), stdout.log (linha 'Hot Rod ISPN004005:') e rep-001.csv (so na secao 5).
Imprime: (1) vereditos; (2) regras do nucleo, transicoes e primeira entrada em CONSERVADOR por celula e braco;
(3) linhas 'politica' por celula e braco; (4) AC-7 nos bracos ativos e P1 previsto nos shadow da F2; (5) eventos do
isn3 (127.0.0.1:11224) de +50 s ate t_fusao_ns em cada F2. Instantes em segundos desde o marcador start da falha.
"""
import collections
import csv
import json
import os
import re
import statistics as st
import sys

BASE = sys.argv[1]
ALVO = "127.0.0.1:11224"


def carregar(p, nome):
    f = os.path.join(p, nome)
    return json.load(open(f, encoding="utf-8")) if os.path.exists(f) else None


def faixa(xs):
    if not xs:
        return "sem dados"
    return "min %.2f; mediana %.2f; max %.2f; n %d" % (min(xs), st.median(xs), max(xs), len(xs))


execs = []
for d in sorted(os.listdir(BASE)):
    if not d.startswith("exec-"):
        continue
    p = os.path.join(BASE, d)
    ch = carregar(p, "checagem.json") or {}
    ej = carregar(p, "execucao.json") or {}
    r = {"dir": d, "p": p, "i": ej.get("i"), "celula": ej.get("celula"), "braco": ej.get("braco"),
         "veredito": ch.get("veredito"), "ej": ej, "start": None, "end": None}
    mf = os.path.join(p, "marcadores.txt")
    if os.path.exists(mf):
        for linha in open(mf, encoding="utf-8"):
            m = re.search(r"event=(\w+) t_epoch_ns=(\d+)", linha)
            if m and m.group(1) == "start" and r["start"] is None:
                r["start"] = int(m.group(2))
            if m and m.group(1) == "end":
                r["end"] = int(m.group(2))
    r["dec"] = []
    r["pol"] = []
    r["acao"] = []
    r["meta"] = None
    f = os.path.join(p, "decisoes-rep-001.jsonl")
    if os.path.exists(f):
        for linha in open(f, encoding="utf-8"):
            o = json.loads(linha)
            t = o.get("tipo")
            if t == "decisao":
                r["dec"].append(o)
            elif t == "politica":
                r["pol"].append(o)
            elif t == "acao":
                r["acao"].append(o)
            elif t == "meta":
                r["meta"] = o
    r["fim_obs"] = None
    f = os.path.join(p, "observaveis-rep-001.jsonl")
    if os.path.exists(f):
        ultima = None
        for linha in open(f, encoding="utf-8"):
            ultima = linha
        try:
            r["fim_obs"] = json.loads(ultima)
        except (TypeError, ValueError):
            pass
    r["det306"] = None
    f = os.path.join(p, "stdout.log")
    if os.path.exists(f):
        for linha in open(f, encoding="utf-8", errors="replace"):
            if linha.startswith("Hot Rod ISPN004005:"):
                m = re.search(r"motivo=ISPN000306 n=\d+ t_primeira_ns=(\d+)", linha)
                if m:
                    r["det306"] = int(m.group(1))
    execs.append(r)

print("== 1. Vereditos")
c = collections.Counter((r["celula"], r["braco"], r["veredito"]) for r in execs)
for k in sorted(c, key=str):
    print("  ", k, c[k])
aceitas = [r for r in execs if r["veredito"] == "ACEITA"]
print("   ACEITA:", len(aceitas), "de", len(execs))

print("\n== 2. Nucleo: regras, transicoes e primeira entrada em CONSERVADOR (ACEITA)")
grupos = collections.defaultdict(list)
for r in aceitas:
    grupos[(r["celula"], r["braco"])].append(r)
for k in sorted(grupos):
    regras = collections.Counter()
    trans = []
    entradas = []
    motivos = collections.Counter()
    janelas_cons = []
    for r in grupos[k]:
        n_tr = 0
        primeira = None
        nc = 0
        for o in r["dec"]:
            regras[o["regra"]] += 1
            if o["perfil"] == "CONSERVADOR":
                nc += 1
            if o.get("transicao"):
                n_tr += 1
                if primeira is None:
                    primeira = o
        trans.append(n_tr)
        janelas_cons.append(nc)
        if primeira is not None:
            ref = r["start"] if r["start"] else r["meta"]["origem_ns"]
            entradas.append((primeira["t_fim_ns"] - ref) / 1e9)
            motivos["+".join(primeira["motivos"])] += 1
    print("  ", k, "execucoes", len(grupos[k]), "| regras", dict(regras), "| transicoes", trans)
    print("      entrada (s desde o start; sem falha: desde a origem do controlador):", faixa(entradas),
          "| motivos", dict(motivos), "| janelas em CONSERVADOR", min(janelas_cons), "a", max(janelas_cons))

print("\n== 3. Linhas 'politica' (ACEITA)")
for k in sorted(grupos):
    pc = collections.Counter()
    for r in grupos[k]:
        for o in r["pol"]:
            pc["%s:%s" % (o["politica"], o["motivo"])] += 1
    print("  ", k, dict(pc))

print("\n== 4. P1 e AC-7 na F2")
for bracos in (("PL", "AL"), ("PO", "AO")):
    p1 = []
    apl = []
    efeito = []
    dur = []
    guarda = collections.Counter()
    post = collections.Counter()
    rev = []
    rev_det = []
    det = []
    lead = []
    ativo = []
    rotas = collections.Counter()
    resultados = collections.Counter()
    desc = []
    gdesf = collections.Counter()
    for r in execs:
        if r["celula"] != "ASYNC-F2" or r["braco"] not in bracos or r["veredito"] != "ACEITA":
            continue
        s = r["start"]
        if r["det306"]:
            det.append((r["det306"] - s) / 1e9)
        for o in r["pol"]:
            if o["politica"] == "P1":
                p1.append((o["t_decisao_ns"] - s) / 1e9)
        t_apl = None
        for a in r["acao"]:
            resultados[(a["operacao"], a["resultado"])] += 1
            if a.get("t_pedido_ns") is None or a.get("duracao_ns") is None:
                continue  # PREVISTA (shadow): so conta
            lido = a.get("lido") or ""
            if a["operacao"] == "APLICAR":
                t_apl = a["t_pedido_ns"]
                apl.append((t_apl - s) / 1e9)
                dur.append(a["duracao_ns"] / 1e6)
                m = re.search(r"efeito_ms=(\d+)", lido)
                if m:
                    efeito.append(int(m.group(1)))
                m = re.search(r"guarda=([a-z-]+)", lido)
                guarda[m.group(1) if m else None] += 1
                if r["det306"]:
                    lead.append((r["det306"] - t_apl) / 1e9)
            elif a["operacao"] == "REVERTER":
                rev.append((a["t_pedido_ns"] - s) / 1e9)
                m = re.search(r"rota=([^;]+)", lido)
                rotas[m.group(1) if m else None] += 1
                m = re.search(r"anterior=POST:(\d+)@(\d+)ms", lido)
                post[(m.group(1), int(m.group(2)) // 1000) if m else None] += 1
                if t_apl:
                    ativo.append((a["t_pedido_ns"] - t_apl) / 1e9)
                if r["det306"]:
                    rev_det.append((a["t_pedido_ns"] - r["det306"]) / 1e9)
        if r["fim_obs"]:
            if r["fim_obs"].get("eventos_descontados_sob_acao") is not None and bracos[0] == "PL":
                desc.append(r["fim_obs"]["eventos_descontados_sob_acao"])
            gdesf[r["fim_obs"].get("guarda_ac7_desfazeres")] += 1
    print("   bracos", bracos)
    print("     acoes (operacao, resultado):", dict(resultados))
    print("     decisao P1:", faixa(p1))
    print("     1a ISPN000306 vista pelo cliente:", faixa(det))
    if apl:
        print("     pedido do AC-7:", faixa(apl))
        print("     1a ISPN000306 menos pedido do AC-7:", faixa(lead))
        print("     efeito_ms:", faixa(efeito), "| guarda:", dict(guarda))
        print("     duracao do aplicar (ms):", faixa(dur))
        print("     reverter:", faixa(rev), "| rotas:", dict(rotas), "| POST anterior (codigo, s):", dict(post))
        print("     reverter menos 1a ISPN000306:", faixa(rev_det))
        print("     tempo ativo (reverter - aplicar, s):", faixa(ativo))
        print("     eventos descontados sob acao:", faixa(desc), "| guarda_ac7_desfazeres:", dict(gdesf))

print("\n== 5. isn3 de +50 s ate t_fusao_ns (F2, eventos do rep-001.csv)")
for r in execs:
    if r["celula"] != "ASYNC-F2" or r["veredito"] != "ACEITA":
        continue
    tf = r["ej"].get("t_fusao_ns")
    t50 = r["start"] + 50_000_000_000
    err = ok = lo = 0
    with open(os.path.join(r["p"], "rep-001.csv"), encoding="utf-8") as fh:
        for e in csv.DictReader(fh):
            if e["replica"] != ALVO:
                continue
            t = int(e["start_ns"])
            if t50 <= t < tf:
                if e["return_code"].startswith("ERROR"):
                    err += 1
                else:
                    ok += 1
                    if e["operation"] == "Logout" and e["return_code"] == "NONE":
                        lo += 1
    print("   %s %s erros %d sucessos %d logouts_confirmados %d fusao_desde_start %.1f s"
          % (r["dir"], r["braco"], err, ok, lo, (tf - r["start"]) / 1e9))
