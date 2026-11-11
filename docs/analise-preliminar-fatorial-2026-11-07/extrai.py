"""Extrato por execucao do nucleo da avaliacao fatorial (analise preliminar do Senior, 07/11).

Roda com python -E -P (numpy no site do usuario). So leitura na copia do nucleo; grava um JSON no scratchpad.
"""
import collections
import csv
import json
import re
import sys
import time
from pathlib import Path

AN = Path(r"C:\Users\Francesco\Development\tcc\dados-vm\cs3-2026-11\bundle-fat-4b96798\arv\cs3-4b96798\analysis")
sys.path.insert(0, str(AN))
import comparar_bracos as cb  # noqa: E402
import energia_proxy as ep  # noqa: E402
import fases  # noqa: E402

B = Path(r"C:\Users\Francesco\Development\tcc\dados-vm\cs3-2026-11\fat-nucleo-previa-20261107T150934Z\campanha-s3-fatorial")
SAIDA = Path(sys.argv[1])
W_REC_NS = 191 * 10**9
W_AN_NS = 4 * 10**9
ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")


def jload(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def ler_trilha(p, t_ref):
    res = {"decisao_regra": collections.Counter(), "transicoes": [], "politicas": collections.Counter(),
           "politicas_lista": [], "acoes": [], "fim": None, "meta_modo": None, "janelas_conservador": 0,
           "primeira_conservador_s": None, "perfil_final": None, "motivos_entrada": None}
    origem = None
    for linha in open(p, encoding="utf-8"):
        d = json.loads(linha)
        t = d.get("tipo")
        if t == "meta":
            res["meta_modo"] = d.get("modo")
            origem = d.get("origem_ns")
            res["origem_ns"] = origem
            continue
        ref = t_ref if t_ref is not None else origem

        def rel(x):
            return None if x is None or ref is None else (x - ref) / 1e9
        if t == "decisao":
            res["decisao_regra"][d.get("regra")] += 1
            if d.get("perfil") == "CONSERVADOR":
                res["janelas_conservador"] += 1
            if d.get("transicao"):
                res["transicoes"].append({"t_s": rel(d["t_fim_ns"]), "perfil": d["perfil"], "regra": d["regra"],
                                          "motivos": d["motivos"],
                                          "sinais": {k: d["sinais"].get(k) for k in (
                                              "taxa_latencia", "taxa_erro", "topologia_anomala", "nos_minimo",
                                              "defasagem", "persistencia_defasagem", "persistencia_latencia",
                                              "persistencia_erro", "no_suspeito", "lag_max_ms",
                                              "violacoes_seguranca")}})
                if d["perfil"] == "CONSERVADOR" and res["primeira_conservador_s"] is None:
                    res["primeira_conservador_s"] = rel(d["t_fim_ns"])
                    res["motivos_entrada"] = d["motivos"]
            res["perfil_final"] = d.get("perfil")
        elif t == "politica":
            res["politicas"][(d.get("politica"), d.get("motivo"), d.get("regra"))] += 1
            if d.get("motivo") != "sem-assinatura":
                res["politicas_lista"].append({"t_s": rel(d.get("t_fim_janela_ns")), "politica": d.get("politica"),
                                               "motivo": d.get("motivo"), "no": d.get("no"),
                                               "aplicar": d.get("aplicar"), "reverter": d.get("reverter"),
                                               "regra": d.get("regra")})
        elif t == "acao":
            a = d.get("acao") or {}
            res["acoes"].append({"operacao": d.get("operacao"), "tipo": a.get("tipo"), "alvo": a.get("alvo"),
                                 "origem": d.get("origem"), "regra": d.get("regra"), "resultado": d.get("resultado"),
                                 "executada": d.get("executada"), "t_pedido_s": rel(d.get("t_pedido_ns")),
                                 "t_pedido_ns": d.get("t_pedido_ns"),
                                 "duracao_ms": None if d.get("duracao_ns") is None else d["duracao_ns"] / 1e6,
                                 "codigo_http": d.get("codigo_http"), "lido": d.get("lido"), "erro": d.get("erro")})
        elif t == "fim":
            res["fim"] = d
    res["decisao_regra"] = dict(res["decisao_regra"])
    res["politicas"] = {"|".join(str(x) for x in k): v for k, v in res["politicas"].items()}
    return res


def ultima_fim(p):
    fim = None
    with open(p, encoding="utf-8") as fh:
        for linha in fh:
            if linha.startswith('{"tipo":"fim"'):
                fim = json.loads(linha)
    return fim


RX_KV = re.compile(r"(\w+)=([^\s,;]+)")


def ler_stdout(p):
    out = {}
    for linha in open(p, encoding="utf-8", errors="replace"):
        s = linha.strip()
        for pref, nome in (("Sonda replay:", "sonda_replay"), ("Sonda violacoes:", "sonda_violacoes"),
                           ("Varredura final:", "varredura"), ("Controlador:", "controlador"),
                           ("Convergencia:", "convergencia"), ("Fusao antes da varredura:", "fusao"),
                           ("Fim da carga:", "fim_carga"), ("Auditor total:", "auditor_total"),
                           ("Auditor I2:", "auditor_i2")):
            if s.startswith(pref):
                out[nome] = s[len(pref):].strip()
        if s.startswith("Hot Rod ISPN004007:"):
            m = re.search(r"total=(\d+)", s)
            out["hotrod_4007_total"] = int(m.group(1)) if m else None
            out["hotrod_4007"] = s
        if s.startswith("Hot Rod ISPN004005:"):
            m = re.search(r"total=(\d+)", s)
            out["hotrod_4005_total"] = int(m.group(1)) if m else None
            out["hotrod_4005"] = s
    if "sonda_replay" in out:
        out["sonda_replay_kv"] = {k: int(v) for k, v in RX_KV.findall(out["sonda_replay"]) if v.isdigit()}
    return out


def ler_violacoes_csv(p, args):
    lin = collections.Counter()
    chaves = collections.defaultdict(set)
    chaves_fase = collections.defaultdict(set)
    rows = []
    with open(p, newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            inv = x["invariante"]
            fase = x.get("fase") or ""
            orig = (x.get("origem") or "").strip() or "online"
            cl = (x.get("classificacao") or "").strip()
            lin[(inv, fase, orig, cl)] += 1
            chaves[inv].add(x["chave"])
            chaves_fase[(inv, fase)].add(x["chave"])
            rows.append((x["instante"], inv, x["chave"], fase, orig, cl, x["mensagem"][:120]))
    return ({"|".join(k): v for k, v in lin.items()}, {k: len(v) for k, v in chaves.items()},
            {"|".join(k): len(v) for k, v in chaves_fase.items()}, rows)


def ler_varredura_csv(p):
    c = collections.Counter()
    rows = []
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r, None)
        for x in r:
            if not x:
                continue
            c[(x[0], x[2])] += 1
            rows.append(x[:4])
    return {"|".join(k): v for k, v in c.items()}, rows


def quantis(v):
    if not v:
        return None
    v = sorted(v)
    n = len(v)

    def q(p):
        return v[min(n - 1, int(p * n))]
    return {"n": n, "p50": q(.5), "p95": q(.95), "p99": q(.99), "max": v[-1]}


def stats_acoes(p, args, sel, ac7_janelas):
    por_fase = collections.defaultdict(collections.Counter)
    lat_sel = []
    cluster = collections.Counter()
    n_sel = 0
    with open(p, newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            t, tf = int(x["t_ns"]), int(x["t_fim_ns"])
            f = fases.fase_de(t, *args)
            fp = x["falha_percebida"] == "true"
            por_fase[f]["acoes"] += 1
            por_fase[f]["res|" + x["resultado"]] += 1
            if fp:
                por_fase[f]["falha_percebida"] += 1
            if f in sel:
                n_sel += 1
                L = (tf - t) / 1e6
                lat_sel.append(L)
                if L >= 1400:
                    cluster[(x["operacao"], x["resultado"], x["tentativas"], int(round(L / 500.0) * 500))] += 1
            for (a, b) in ac7_janelas:
                if a <= t < b:
                    por_fase["ac7_ativo"]["acoes"] += 1
                    por_fase["ac7_ativo"]["res|" + x["resultado"]] += 1
                    if fp:
                        por_fase["ac7_ativo"]["falha_percebida"] += 1
    return ({k: dict(v) for k, v in por_fase.items()}, quantis(lat_sel),
            {"|".join(str(z) for z in k): v for k, v in cluster.most_common(12)},
            sum(1 for L in lat_sel if L >= 1400), n_sel)


def stats_eventos(p, args, ac7_janelas, t_ini_falha, t_fim_falha):
    """Erros do CSV de eventos por fase e por replica (servidor de aplicacao), sem ReplayValidate."""
    c = collections.defaultdict(collections.Counter)
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r, None)
        for x in r:
            if len(x) < 7 or x[1] == "ReplayValidate":
                continue
            t = int(x[2])
            f = fases.fase_de(t, *args)
            rep = x[4]
            err = x[5] in ERROS
            c[f]["ev"] += 1
            c[f]["ev|" + rep] += 1
            if err:
                c[f]["err"] += 1
                c[f]["err|" + rep] += 1
                c[f]["err|" + x[5]] += 1
            for (a, b) in ac7_janelas:
                if a <= t < b:
                    c["ac7_ativo"]["ev"] += 1
                    c["ac7_ativo"]["ev|" + rep] += 1
                    if err:
                        c["ac7_ativo"]["err"] += 1
                        c["ac7_ativo"]["err|" + rep] += 1
            # trecho da janela da falha antes da deteccao: [start, start+42 s)
            if t_ini_falha is not None and t_ini_falha <= t < t_ini_falha + 42 * 10**9:
                c["falha_0_42s"]["ev"] += 1
                c["falha_0_42s"]["ev|" + rep] += 1
                if err:
                    c["falha_0_42s"]["err"] += 1
                    c["falha_0_42s"]["err|" + rep] += 1
    return {k: dict(v) for k, v in c.items()}


def compacto_apur(apur, args):
    if apur is None:
        return None
    a = {k: v for k, v in apur.items() if k != "pares_lista"}
    pares = []
    for p in apur["pares_lista"]:
        dets = []
        for x in p["deteccoes"]:
            ta = x["t_atribuido_ns"]
            dets.append({"origem": x["origem"], "tipo": x["tipo"], "rotulo": x["rotulo"], "conta": x["conta"],
                         "atribuicao": x["atribuicao"], "forma": x["forma"], "t_ns": x["t_ns"],
                         "t_atribuido_ns": ta, "replica_leitura": x["replica_leitura"],
                         "fase_atribuida": None if ta is None else fases.fase_de(ta, *args)})
        pares.append({"chave": p["chave"], "classe": p["classe"], "conta": p["conta"], "origens": p["origens"],
                      "forma": p["forma"], "deteccoes": dets})
    a["pares"] = apur["pares"]
    a["pares_lista_compacta"] = pares
    return a


def main():
    man = list(csv.DictReader(open(B / "manifesto-bracos.tsv", encoding="utf-8"), delimiter="\t"))
    res = {}
    t0 = time.time()
    lim = int(sys.argv[2]) if len(sys.argv) > 2 else None
    for row in man[:lim]:
        d = B / row["execucao"]
        cel, br = row["celula"], row["braco"]
        ej = jload(d / "execucao.json")
        ch = jload(d / "checagem.json")
        mk = fases.ler_marcadores(d / "marcadores.txt")
        conv = jload(d / "convergencia-resumo-rep-001.json")
        t_c = conv.get("t_convergencia_abs_ns")
        div = bool(conv.get("divergencia_persistente"))
        args = (mk["inicio"], mk["split"], mk["fim"], W_REC_NS, t_c, div)
        sel = ("antes",) if cel == "ASYNC-none" else ("durante", "recuperacao")
        rec = {"execucao": row["execucao"], "bloco": row["bloco"], "celula": cel, "braco": br,
               "i": ej.get("i"), "seed": ej.get("seed"), "fusao": ej.get("fusao"), "controller": ej.get("controller"),
               "veredito": ch.get("veredito"), "motivos": ch.get("motivos"), "avisos": ch.get("avisos"),
               "lancamento_utc": ej.get("lancamento_utc"), "fim_utc": ej.get("fim_utc"),
               "ancora_ms": ej.get("ancora_ms"), "lancamento_ms": ej.get("lancamento_ms"),
               "recriacao_ms": ej.get("recriacao_ms"),
               "inj_inicio_offset_s": ej.get("inj_inicio_offset_s"),
               "fim_nominal_desde_ancora_s": ej.get("fim_nominal_desde_ancora_s"),
               "injetor_saida_desde_ancora_s": ej.get("injetor_saida_desde_ancora_s"),
               "injetor_exit": ej.get("injetor_exit"), "falha_efetiva": ej.get("falha_efetiva"),
               "rede_pos": ej.get("rede_pos"), "teto_estourado": ej.get("teto_estourado"),
               "workload_exit": ej.get("workload_exit"), "swap_usado_kb_k1": ej.get("swap_usado_kb_k1"),
               "trilha": ej.get("trilha"), "fusao_config_ok": (ej.get("fusao_config") or {}).get("ok"),
               "a1_politica_carregada": (ej.get("fusao_config") or {}).get("a1_politica_carregada"),
               "t_fusao_ns": ej.get("t_fusao_ns"),
               "fusao_vista_desde_end_s": (ej.get("fusao_vista") or {}).get("desde_end_s"),
               "fusao_vista_no": (ej.get("fusao_vista") or {}).get("no"),
               "fusao_trilha_desde_end_s": (ej.get("fusao_trilha") or {}).get("desde_end_s"),
               "fusao_trilha_dif_ispn_s": (ej.get("fusao_trilha") or {}).get("dif_ispn000093_s"),
               "fusao_checagem_desde_end_s": (ej.get("fusao_checagem") or {}).get("desde_end_s"),
               "fusao_checagem_fundiu": (ej.get("fusao_checagem") or {}).get("fundiu"),
               "camada3": [(ej.get("camada3_pre") or {}).get("divergencias"),
                           (ej.get("camada3_pos") or {}).get("divergencias")],
               "marcadores": mk, "convergencia": conv,
               "h4": (d / "h4.txt").read_text(encoding="utf-8").strip()}
        # convergencia csv
        rodadas, max5, max6, com_div = 0, 0, 0, 0
        with open(d / "convergencia-rep-001.csv", newline="", encoding="utf-8") as fh:
            for x in csv.DictReader(fh):
                rodadas += 1
                i5, i6 = int(x["chaves_divergentes_i5"] or 0), int(x["chaves_divergentes_i6"] or 0)
                max5, max6 = max(max5, i5), max(max6, i6)
                com_div += 1 if (i5 or i6) else 0
        rec["convergencia_csv"] = {"rodadas": rodadas, "max_i5": max5, "max_i6": max6, "rodadas_com_div": com_div}
        rec["s3_resumo"] = jload(d / "s3-resumo-rep-001.json")
        rec["viol_linhas"], rec["viol_chaves"], rec["viol_chaves_fase"], vrows = ler_violacoes_csv(
            d / "violacoes-rep-001.csv", args)
        rec["viol_rows_fora_cascata"] = [r for r in vrows if not r[1].startswith("I2_")]
        rec["varredura"], rec["varredura_rows"] = ler_varredura_csv(d / "varredura-rep-001.csv")
        rec["stdout"] = ler_stdout(d / "stdout.log")
        tr = ler_trilha(d / "decisoes-rep-001.jsonl", mk["inicio"])
        rec["trilha_ctrl"] = tr
        rec["observaveis_fim"] = ultima_fim(d / "observaveis-rep-001.jsonl")
        # janelas AC-7 (aplicar executada -> reverter executada), em ns
        jan = []
        ap = None
        for a in tr["acoes"]:
            if a["tipo"] != "AC-7":
                continue
            if a["operacao"] == "APLICAR" and a["resultado"] == "APLICADA":
                ap = a["t_pedido_ns"]
            elif a["operacao"] == "REVERTER" and ap is not None:
                jan.append((ap, a["t_pedido_ns"]))
                ap = None
        if ap is not None:
            jan.append((ap, 10**20))
        rec["ac7_janelas_ns"] = jan
        # desfechos pela funcao da analise (mesma do comparar_bracos da tag)
        out, info = cb.desfechos_execucao(d, 1, sel, W_REC_NS, w_analise_ns=W_AN_NS,
                                          fusao_obrigatoria=(cel == "ASYNC-F2"))
        rec["desfechos"] = {k: {kk: vv for kk, vv in v.items() if kk in ("y", "n", "motivo", "contagem", "linhas",
                                                                          "censurada", "origem")}
                            for k, v in out.items()}
        rec["v_detalhe"] = compacto_apur(info.get("violacoes_detalhe"), args)
        rec["fusao_w5e"] = info.get("fusao")
        rec["controlador_desc"] = info.get("controlador")
        rec["rastreabilidade"] = info.get("rastreabilidade")
        rec["n_acoes_sel"] = info.get("n_acoes")
        # energia em memoria (nao grava na copia)
        try:
            en = ep.analisar_execucao(d, 1, "S3", d / "marcadores.txt" if (d / "marcadores.txt").exists() else None,
                                      191, (d / "convergencia-resumo-rep-001.json")
                                      if (d / "marcadores.txt").exists() else None, 181, (0.3, 0.5))
            rec["energia"] = {"fases": en["fases"], "agregados": en["agregados"], "uteis": en["uteis"],
                              "steal": en["steal"], "retrabalho": en["retrabalho"],
                              "cpu_fontes": {f: {r: v["cpu_s"] for r, v in x.items()} for f, x in en["cpu_s"].items()},
                              "e_vm": {k: v["vm"] for k, v in en["energia"]["por_razao"].items()},
                              "nproc": en["recursos"]["nproc"]}
        except Exception as e:  # noqa: BLE001
            rec["energia"] = {"erro": repr(e)}
        rec["acoes_por_fase"], rec["lat_sel"], rec["lat_cluster"], rec["n_lat_ge_1400"], rec["n_sel"] = stats_acoes(
            d / "s3-acoes-rep-001.csv", args, sel, jan)
        rec["eventos_por_fase"] = stats_eventos(d / "rep-001.csv", args, jan, mk["inicio"], mk["fim"])
        res[row["execucao"]] = rec
        print(row["execucao"], "ok", round(time.time() - t0, 1), flush=True)
    SAIDA.write_text(json.dumps(res, ensure_ascii=False, default=str), encoding="utf-8")
    print("gravado", SAIDA, round(time.time() - t0, 1))


if __name__ == "__main__":
    main()
