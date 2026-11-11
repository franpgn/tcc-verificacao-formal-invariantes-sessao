"""A1 smoke: resume cada execucao (A1 ou controle) e monta a tabela A1 x controle.

Uso: python -I analisar_a1.py <dir-execucao> [<dir-execucao> ...] > analise.txt
     (com --json <arquivo>, grava tambem o resumo em JSON)

So le os brutos de cada execucao (fases.tsv, marcadores.txt, rep-001.csv, violacoes-rep-001.csv,
varredura-rep-001.csv, stdout.log, config-*.json e servidores/isnN.log) e imprime texto. Conta:
- I1 REAL da varredura final (perda permanente) e os outros achados REAL;
- I1 da sonda de replay (exposicao), por fase (antes/durante/recuperacao), em linhas e em sids distintas;
- I1 online (origem vazia), por fase;
- linhas da politica nos logs dos servidores: A1-POLITICA (carga por no), A1-FORMATO, A1-FUSAO (por resultado,
  vencedora e igual_pnn) e A1-SAIDA-SEGURANCA (por motivo);
- ISPN000466 (excecao na resolucao de conflitos), ISPN100012/13 (inicio e fim da CR), linhas ERROR e linhas WARN
  que citam a politica, a fusao ou conflitos;
- cruzamentos: cada I1 REAL e cada decisao em que a A1 escolheu diferente do PREFERRED_NON_NULL contra o Logout
  confirmado no isn3 (no do Logout e instante relativo ao corte).
"""
import csv
import json
import re
import sys
from collections import Counter
from pathlib import Path

NOS = {"127.0.0.1:12801": "isn1", "127.0.0.1:12802": "isn2", "127.0.0.1:12803": "isn3"}


def ler_csv(p):
    if not p.exists():
        return []
    with open(p, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def marcadores(d):
    mk = {}
    p = d / "marcadores.txt"
    if p.exists():
        for l in p.read_text(encoding="utf-8").splitlines():
            if l.strip():
                kv = dict(x.split("=", 1) for x in l.split())
                mk[kv["event"]] = int(kv["t_epoch_ns"])
    return mk


def inicio(d):
    for l in (d / "fases.tsv").read_text(encoding="utf-8").splitlines():
        c = l.split("\t")
        if len(c) >= 3 and c[2].startswith("inicio "):
            return dict(x.split("=", 1) for x in c[2].split()[2:] if "=" in x)
    return {}


def linha_stdout(d, prefixo):
    p = d / "stdout.log"
    if not p.exists():
        return None
    for l in p.read_text(encoding="utf-8", errors="replace").splitlines():
        if l.startswith(prefixo):
            return l
    return None


def ns_iso(txt):
    """'2026-11-06T16:50:19.613123456Z' (date -u +%FT%T.%NZ) -> epoch ns."""
    import calendar
    import time
    base, frac = txt.rstrip("Z").split(".")
    seg = calendar.timegm(time.strptime(base, "%Y-%m-%dT%H:%M:%S"))
    return seg * 10**9 + int(frac.ljust(9, "0")[:9])


def ns_log(txt):
    """'2026-11-06 16:51:02,397' (log do Infinispan, UTC no conteiner) -> epoch ns."""
    import calendar
    import time
    base, ms = txt.split(",")
    return calendar.timegm(time.strptime(base, "%Y-%m-%d %H:%M:%S")) * 10**9 + int(ms) * 10**6


def instantes_nft(d):
    r = {}
    for arq, chaves in (("corte.txt", ("aplicado",)), ("cura.txt", ("removido",))):
        p = d / arq
        if p.exists():
            for l in p.read_text(encoding="utf-8", errors="replace").splitlines():
                for k in chaves:
                    if l.startswith("nft-" + k + " "):
                        r[k] = ns_iso(l.split()[1])
    return r


def fase_ns(d, prefixo):
    for l in (d / "fases.tsv").read_text(encoding="utf-8").splitlines():
        c = l.split("\t")
        if len(c) >= 3 and c[2].startswith(prefixo):
            return int(c[0])
    return None


KV = re.compile(r"(\w+)=(\[[^\]]*\]|\S+)")


def linhas_politica(texto, prefixo):
    out = []
    for l in texto.splitlines():
        i = l.find(prefixo + " ")
        if i >= 0:
            out.append(dict(KV.findall(l[i + len(prefixo) + 1:])))
    return out


def servidores(d):
    r = {"por_no": {}, "fusao": [], "saida": [], "formato": [], "cr": [], "ispn000466": 0, "erros": [],
         "erros_por_codigo": Counter(), "avisos_fusao": [], "degradado_isn3": None}
    for no in ("isn1", "isn2", "isn3"):
        p = d / "servidores" / (no + ".log")
        if not p.exists():
            continue
        t = p.read_text(encoding="utf-8", errors="replace")
        fus = linhas_politica(t, "A1-FUSAO")
        sai = linhas_politica(t, "A1-SAIDA-SEGURANCA")
        for x in fus:
            x["no"] = no
        for x in sai:
            x["no"] = no
        r["fusao"] += fus
        r["saida"] += sai
        r["por_no"][no] = {
            "politica_carregada": t.count("A1-POLITICA carregada"),
            "fusao": len(fus),
            "saida_seguranca": len(sai),
        }
        for l in t.splitlines():
            if "A1-FORMATO" in l:
                m = re.search(r"classe=(\S+)(?: bytes=(\d+) hex=([0-9a-f]+))?", l)
                hx = m.group(3) or ""
                r["formato"].append({
                    "no": no, "classe": m.group(1), "bytes": int(m.group(2) or 0),
                    "cabecalho_java": hx.startswith("aced0005"),
                    "classe_no_fluxo": bytes.fromhex("".join(
                        "%02x" % ord(c) for c in "br.unipampa.tcc.session.SessionState")).hex() in hx,
                })
            if "ISPN100012" in l or "ISPN100013" in l:
                m = re.search(r"\[Context=([\w-]+)\](ISPN10001[23])", l)
                ts = re.search(r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d{3}", l)
                r["cr"].append({"no": no, "codigo": m.group(2) if m else "?", "cache": m.group(1) if m else "?",
                                "ts": ts.group(0) if ts else "?"})
            if no == "isn3" and r["degradado_isn3"] is None and "[Context=sessions-async]" in l \
                    and "ISPN100011" in l and "DEGRADED" in l:
                ts = re.search(r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d{3}", l)
                r["degradado_isn3"] = ns_log(ts.group(0)) if ts else None
            if "ISPN000466" in l:
                r["ispn000466"] += 1
            if re.search(r"\sERROR\s", l):
                m = re.search(r"(ISPN\d{6})", l)
                r["erros_por_codigo"][m.group(1) if m else "sem-codigo"] += 1
                if re.search(r"PoliticaFusao|EntryMergePolicy|merge|conflict", l, re.I):
                    r["erros"].append(no + ": " + l[:300])
            if re.search(r"\sWARN\s", l) and re.search(r"PoliticaFusao|EntryMergePolicy|merge polic|conflict", l, re.I) \
                    and "A1-SAIDA-SEGURANCA" not in l:
                r["avisos_fusao"].append(no + ": " + l[:300])
    return r


def resumir(d):
    d = Path(d)
    ini = inicio(d)
    mk = marcadores(d)
    t0 = mk.get("start")
    rel = (lambda t: (t - t0) / 1e9) if t0 else (lambda t: float("nan"))
    ev = ler_csv(d / "rep-001.csv")
    logout_ok = {}
    for e in ev:
        if e["operation"] == "Logout" and not e["return_code"].startswith("ERROR"):
            logout_ok[e["key"]] = e
    viol = ler_csv(d / "violacoes-rep-001.csv")
    var = ler_csv(d / "varredura-rep-001.csv")
    srv = servidores(d)

    def contar_i1(origem):
        linhas = [v for v in viol if v["invariante"] == "I1" and (v.get("origem") or "") == origem]
        por_fase = Counter(v["fase"] for v in linhas)
        sids = {v["chave"] for v in linhas}
        sids_fase = {f: len({v["chave"] for v in linhas if v["fase"] == f}) for f in por_fase}
        return {"linhas": len(linhas), "sids": len(sids), "linhas_por_fase": dict(por_fase),
                "sids_por_fase": sids_fase, "lista_sids": sorted(sids)}

    real = Counter((x["invariante"], x["classificacao"]) for x in var)
    i1_real = sorted(x["chave"] for x in var if x["invariante"] == "I1" and x["classificacao"] == "REAL")
    fus = srv["fusao"]
    diferente = [x for x in fus if x.get("igual_pnn") == "nao"]

    def logout_de(sid):
        lo = logout_ok.get(sid)
        if not lo:
            return None
        return {"no": NOS.get(lo["replica"], lo["replica"]), "t_rel_corte_s": round(rel(int(lo["end_ns"])), 2)}

    cfg = {}
    for no in ("isn1", "isn2", "isn3"):
        p = d / ("config-sessions-async-%s.json" % no)
        if p.exists():
            m = re.search(r'"merge-policy":"([^"]*)"', p.read_text(encoding="utf-8"))
            cfg[no] = m.group(1) if m else "?"
    cfgc = {}
    for no in ("isn1", "isn2", "isn3"):
        p = d / ("config-counters-async-%s.json" % no)
        if p.exists():
            m = re.search(r'"merge-policy":"([^"]*)"', p.read_text(encoding="utf-8"))
            cfgc[no] = m.group(1) if m else "?"
    conv = linha_stdout(d, "Convergencia:")
    nft = instantes_nft(d)
    t_fim_carga = fase_ns(d, "carga ")
    # CR da fusao = a primeira ISPN100012 do sessions-async depois da cura; antes dela pode haver uma CR so entre os
    # nos da maioria, quando o isn3 era o coordenador e a maioria elege outro (troca de coordenador, ISPN100007)
    cr_todas = sorted((x for x in srv["cr"] if x["cache"] == "sessions-async" and x["codigo"] == "ISPN100012"),
                      key=lambda x: ns_log(x["ts"]))
    cr_sa = [x for x in cr_todas if not nft.get("removido") or ns_log(x["ts"]) > nft["removido"]]
    t_cr = ns_log(cr_sa[0]["ts"]) if cr_sa else None
    t_deg = srv["degradado_isn3"]
    ini_jan = nft.get("aplicado") or t0
    # candidatos a I1: Logout confirmado pelo isn3 depois de as regras entrarem e antes de o isn3 ficar DEGRADED
    cand = sorted(sid for sid, lo in logout_ok.items() if NOS.get(lo["replica"]) == "isn3" and ini_jan and t_deg
                  and ini_jan <= int(lo["end_ns"]) <= t_deg)
    primeiro_ev = {}
    for e in ev:
        t = int(e["end_ns"])
        if e["key"] not in primeiro_ev or t < primeiro_ev[e["key"]]:
            primeiro_ev[e["key"]] = t
    destino = {}
    for sid in cand:
        dec = next((x for x in fus if x.get("chave") == sid), None)
        sai = next((x for x in srv["saida"] if x.get("chave") == sid), None)
        destino[sid] = {"logout": logout_de(sid), "i1_real": sid in i1_real,
                        # primeiro evento da sid antes das regras: a sessao ja existia (e a maioria tinha copia)
                        "existia_antes_do_corte": bool(ini_jan and primeiro_ev.get(sid, ini_jan) < ini_jan),
                        "i1_online": sid in set(contar_i1("")["lista_sids"]),
                        "a1": ({"escolhida": dec.get("escolhida"), "pnn": dec.get("pnn"),
                                "igual_pnn": dec.get("igual_pnn")} if dec else None),
                        "saida_seguranca": sai.get("motivo") if sai else None}
    mecanismo = None
    pc = d / "sids.txt.confirmados"
    if pc.exists():
        conf = [l.strip() for l in pc.read_text(encoding="utf-8").splitlines() if l.strip()]
        leituras = {}
        for no in ("isn1", "isn2", "isn3"):
            pl = d / ("ler-%s.txt" % no)
            if pl.exists():
                m = re.search(r"RESUMO .*", pl.read_text(encoding="utf-8", errors="replace"))
                leituras[no] = m.group(0) if m else None
        decs = [x for x in fus if x.get("chave") in set(conf)]
        mecanismo = {
            "criadas": sum(1 for l in (d / "sids.txt").read_text(encoding="utf-8").splitlines() if l.strip()),
            "logout_confirmado_pelo_isn3": len(conf),
            "leitura_depois_da_fusao": leituras,
            "a1_decisoes_nas_confirmadas": len(decs),
            "a1_invalid_sobre_valid_nas_confirmadas": sum(
                1 for x in decs if x.get("igual_pnn") == "nao" and (x.get("escolhida") or "").startswith("INVALID")
                and (x.get("pnn") or "").startswith("VALID")),
            "saida_nas_confirmadas": sum(1 for x in srv["saida"] if x.get("chave") in set(conf)),
        }
    return {
        "mecanismo": mecanismo,
        "janela": {
            "nft_aplicado_rel_marcador_s": round((nft["aplicado"] - t0) / 1e9, 2) if nft.get("aplicado") and t0 else None,
            "isn3_degradado_rel_corte_s": round((t_deg - ini_jan) / 1e9, 2) if t_deg and ini_jan else None,
            "corte_efetivo_s": round((nft["removido"] - nft["aplicado"]) / 1e9, 1)
            if nft.get("removido") and nft.get("aplicado") else None,
            "cr_sessions_async_depois_da_cura_s": round((t_cr - nft["removido"]) / 1e9, 1)
            if t_cr and nft.get("removido") else None,
            "cr_antes_do_fim_da_carga": (t_cr is not None and t_fim_carga is not None and t_cr < t_fim_carga),
            "cr_no": cr_sa[0]["no"] if cr_sa else None,
            "cr_antes_da_cura": len(cr_todas) - len(cr_sa),
        },
        "candidatos_i1": len(cand),
        "candidatos_i1_preexistentes": sum(1 for x in destino.values() if x["existia_antes_do_corte"]),
        "candidatos_i1_destino": destino,
        "execucao": d.name,
        "condicao": ini.get("condicao"),
        "semente": ini.get("semente"),
        "exit": (d / "exit.txt").read_text().strip() if (d / "exit.txt").exists() else None,
        "corte_s": round((mk["end"] - mk["start"]) / 1e9, 1) if "end" in mk and "start" in mk else None,
        "merge_policy_sessions_async": cfg,
        "merge_policy_counters_async": cfgc,
        "varredura_linha": linha_stdout(d, "Varredura final:"),
        "convergencia_linha": conv,
        "sonda_linha": linha_stdout(d, "Sonda violacoes:"),
        "i1_real_varredura": len(i1_real),
        "i1_real_sids": i1_real,
        "i1_real_detalhe": [{"sid": s, "logout": logout_de(s),
                             "a1": next((x for x in fus if x.get("chave") == s), None)} for s in i1_real],
        "achados_varredura": {"%s/%s" % k: v for k, v in sorted(real.items())},
        "i1_sonda": contar_i1("sonda"),
        "i1_online": contar_i1(""),
        "logouts_confirmados": len(logout_ok),
        "logouts_confirmados_isn3_na_janela": sum(
            1 for lo in logout_ok.values() if NOS.get(lo["replica"]) == "isn3" and t0 and "end" in mk
            and t0 <= int(lo["end_ns"]) <= mk["end"]),
        "politica": {
            "por_no": srv["por_no"],
            "fusao_total": len(fus),
            "fusao_por_resultado": dict(Counter(x.get("resultado") for x in fus)),
            "fusao_vencedora": dict(Counter(x.get("vencedora") for x in fus)),
            "fusao_igual_pnn": dict(Counter(x.get("igual_pnn") for x in fus)),
            "diferente_do_pnn": [{"sid": x.get("chave"), "escolhida": x.get("escolhida"), "pnn": x.get("pnn"),
                                  "logout": logout_de(x.get("chave"))} for x in diferente],
            "invalid_sobre_valid": sum(1 for x in diferente if (x.get("escolhida") or "").startswith("INVALID")
                                       and (x.get("pnn") or "").startswith("VALID")),
            "saida_seguranca_total": len(srv["saida"]),
            "saida_por_motivo": dict(Counter(x.get("motivo") for x in srv["saida"])),
            "saida_detalhe": [{"sid": x.get("chave"), "motivo": x.get("motivo"), "detalhe": x.get("detalhe"),
                               "candidatos": x.get("candidatos")} for x in srv["saida"]],
            "formato": srv["formato"],
        },
        "cr_linhas": srv["cr"],
        "ispn000466": srv["ispn000466"],
        "erros_servidor": srv["erros"],
        "erros_por_codigo": dict(srv["erros_por_codigo"]),
        "avisos_fusao": srv["avisos_fusao"],
    }


def main(argv):
    js = None
    if "--json" in argv:
        i = argv.index("--json")
        js = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    res = [resumir(a) for a in argv]
    for r in res:
        print("=" * 100)
        print("%s  condicao=%s semente=%s %s corte=%ss" % (r["execucao"], r["condicao"], r["semente"], r["exit"],
                                                          r["corte_s"]))
        if r["mecanismo"]:
            print("  TESTE DIRIGIDO: %s" % r["mecanismo"])
        print("  janela: %s" % r["janela"])
        print("  candidatos a I1 (Logout confirmado pelo isn3 isolado, antes do DEGRADED): %d" % r["candidatos_i1"])
        for sid, x in r["candidatos_i1_destino"].items():
            print("    %s %s" % (sid, x))
        print("  merge-policy sessions-async por no: %s" % r["merge_policy_sessions_async"])
        print("  merge-policy counters-async por no: %s" % r["merge_policy_counters_async"])
        print("  %s" % r["varredura_linha"])
        print("  %s" % r["convergencia_linha"])
        print("  %s" % r["sonda_linha"])
        print("  I1 REAL da varredura: %d %s" % (r["i1_real_varredura"], r["i1_real_detalhe"]))
        print("  achados da varredura: %s" % r["achados_varredura"])
        print("  I1 sonda: %s" % {k: v for k, v in r["i1_sonda"].items() if k != "lista_sids"})
        print("  I1 online: %s" % {k: v for k, v in r["i1_online"].items() if k != "lista_sids"})
        print("  logouts confirmados: %d (no isn3 durante o corte: %d)" % (r["logouts_confirmados"],
                                                                           r["logouts_confirmados_isn3_na_janela"]))
        p = r["politica"]
        print("  politica por no: %s" % p["por_no"])
        print("  A1-FUSAO: total=%d resultado=%s vencedora=%s igual_pnn=%s invalid_sobre_valid=%d" % (
            p["fusao_total"], p["fusao_por_resultado"], p["fusao_vencedora"], p["fusao_igual_pnn"],
            p["invalid_sobre_valid"]))
        for x in p["diferente_do_pnn"]:
            print("    diferente do PNN: %s" % x)
        print("  A1-SAIDA-SEGURANCA: total=%d por motivo=%s" % (p["saida_seguranca_total"], p["saida_por_motivo"]))
        for x in p["saida_detalhe"]:
            print("    saida: %s" % x)
        print("  A1-FORMATO: %s" % p["formato"])
        print("  CR (ISPN100012/13): %d linhas" % len(r["cr_linhas"]))
        print("  CR do sessions-async: %s" % [x for x in r["cr_linhas"] if x["cache"] == "sessions-async"])
        print("  ISPN000466: %d; linhas ERROR por codigo: %s; ERROR que citam fusao/conflito: %d; WARN de fusao/"
              "conflito: %d" % (r["ispn000466"], r["erros_por_codigo"], len(r["erros_servidor"]),
                                len(r["avisos_fusao"])))
        for l in r["erros_servidor"][:20]:
            print("    ERROR %s" % l)
        for l in r["avisos_fusao"][:20]:
            print("    WARN %s" % l)
    print("=" * 100)
    print("Tabela A1 x controle")
    cab = ("execucao", "cond", "semente", "CR antes do fim", "candidatos I1 (pre-exist.)", "I1 REAL var.", "I1 sonda (sids)",
           "durante", "recuperacao", "I1 online", "A1-FUSAO", "INVALID>VALID", "saida seg.", "ISPN000466")
    print(" | ".join(cab))
    for r in res:
        s = r["i1_sonda"]
        print(" | ".join(str(x) for x in (
            r["execucao"], r["condicao"], r["semente"], r["janela"]["cr_antes_do_fim_da_carga"],
            "%d (%d)" % (r["candidatos_i1"], r["candidatos_i1_preexistentes"]),
            r["i1_real_varredura"],
            "%d (%d)" % (s["linhas"], s["sids"]), s["sids_por_fase"].get("durante", 0),
            s["sids_por_fase"].get("recuperacao", 0), r["i1_online"]["linhas"], r["politica"]["fusao_total"],
            r["politica"]["invalid_sobre_valid"], r["politica"]["saida_seguranca_total"], r["ispn000466"])))
    if js:
        Path(js).write_text(json.dumps(res, indent=1, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1:])
