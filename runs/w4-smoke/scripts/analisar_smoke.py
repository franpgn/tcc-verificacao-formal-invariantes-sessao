"""W-4 smoke: criterios A2..A14 da V-W4 secao 9 a partir dos brutos de uma execucao.

Uso: python -I analisar_smoke.py <dir_execucao> [--servidores <dir>] [--chaves-de <observaveis_ref>]
Sem corte (sem "corte-fim" em fases.tsv): A2..A8. Com corte: A9..A13 e os instantes.
Saida: JSON em stdout (o harness grava em <dir>/criterios.json).
"""
import argparse
import datetime
import glob
import json
import os
import re
import statistics

TETO = 2000.0
SUSPEITO = "127.0.0.1:12613"
MAIORIA = ("127.0.0.1:12611", "127.0.0.1:12612")
NOS = MAIORIA + (SUSPEITO,)


def ler_jsonl(caminho):
    with open(caminho, encoding="utf-8") as f:
        return [json.loads(x) for x in f if x.strip()]


def p99(v):
    if not v:
        return None
    s = sorted(v)
    return s[max(0, -(-99 * len(s) // 100) - 1)]  # posto mais proximo


def resumo(v):
    if not v:
        return None
    return {"n": len(v), "min": min(v), "mediana": statistics.median(v), "p99": p99(v), "max": max(v)}


def fases(d):
    f = {}
    caminho = os.path.join(d, "fases.tsv")
    if os.path.exists(caminho):
        for l in open(caminho, encoding="utf-8"):
            ns, _, texto = l.rstrip("\n").split("\t", 2)
            chave = texto.split(" ")[0]
            f.setdefault(chave, int(ns))
    return f


def instante_log(linha):
    m = re.match(r"(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(\.\d+)?(Z|[+-]\d\d:\d\d)", linha)
    if not m:
        return None
    frac = (m.group(2) or ".0")[1:]
    fuso = "+00:00" if m.group(3) == "Z" else m.group(3)
    t = datetime.datetime.fromisoformat(m.group(1) + fuso)
    return int(t.timestamp()) * 10**9 + int((frac + "000000000")[:9])


def vista_nos_logs(servidores, depois_ns):
    """Primeira ISPN000094 com 2 membros no isn1 ou no isn2 depois do instante dado."""
    melhor = None
    for no in ("isn1", "isn2"):
        arq = os.path.join(servidores, no + ".log")
        if not os.path.exists(arq):
            continue
        for l in open(arq, encoding="utf-8", errors="replace"):
            if "ISPN000094" in l and re.search(r"\]\s*\(2\)", l):
                t = instante_log(l)
                if t is not None and t >= depois_ns and (melhor is None or t < melhor[0]):
                    melhor = (t, no, l.strip()[:220])
    return melhor


def analisar(d, servidores, chaves_de):
    obs = ler_jsonl(os.path.join(d, "observaveis-rep-001.jsonl"))
    dec = ler_jsonl(os.path.join(d, "decisoes-rep-001.jsonl"))
    meta, fim = obs[0], obs[-1]
    rod = [x for x in obs if x["tipo"] == "defasagem"]
    jan = [x for x in obs if x["tipo"] == "janela"]
    top = [x for x in obs if x["tipo"] == "topologia"]
    pol = [x for x in dec if x["tipo"] == "politica"]
    aco = [x for x in dec if x["tipo"] == "acao"]
    f = fases(d)
    r = {"execucao": os.path.basename(d.rstrip("/\\")), "rodadas": len(rod), "janelas": len(jan)}

    pd = meta.get("parametros_defasagem") or {}
    r["A2"] = {"esquema": meta.get("esquema"), "sonda_defasagem": meta.get("sonda_defasagem"),
               "lag_in_ms": pd.get("lag_in_ms"), "lag_out_ms": pd.get("lag_out_ms"),
               "passo_leitura_ms": pd.get("passo_leitura_ms"), "origem": pd.get("origem"),
               "ok": meta.get("esquema") == "observaveis-controlador/1.2" and meta.get("sonda_defasagem") is True
               and (pd.get("lag_in_ms"), pd.get("lag_out_ms"), pd.get("passo_leitura_ms")) == (2000.0, 1000.0, 20)}

    chaves = fim.get("defasagem_chaves") or []
    r["A6"] = {"chaves": chaves,
               "ok": len(chaves) == 3 and all(k.get("primario") is True and k.get("motivo") == "primario" for k in chaves)
               and len({k["chave"] for k in chaves}) == 3 and [k["no"] for k in chaves] == list(NOS)}
    contadores = {k: v for k, v in fim.items() if k.startswith("defasagem_") and k != "defasagem_chaves"}
    r["contadores"] = contadores

    leituras = [x["custo"]["leituras"] for x in rod]
    t_corte = f.get("corte-fim")
    t_cura = f.get("cura-fim")
    if t_corte is None:
        pares = [p for x in rod for p in x["pares"]]
        r["A3"] = {"pares_vistos": sum(p["visto"] for p in pares), "pares": len(pares),
                   "rodadas_no_teto": sum(x["lag_max_ms"] >= TETO for x in rod),
                   "lag_max_ms": resumo([x["lag_max_ms"] for x in rod]),
                   "lag_par_ms": resumo([p["lag_ms"] for p in pares]),
                   "janelas_alta_ou_intermediaria": sum(j["lag"]["nivel"] in ("ALTA", "INTERMEDIARIA") for j in jan)}
        r["A3"]["ok"] = (r["A3"]["pares_vistos"] == r["A3"]["pares"] and r["A3"]["rodadas_no_teto"] == 0
                         and (r["A3"]["lag_max_ms"] or {}).get("max", 0) < 1000
                         and r["A3"]["janelas_alta_ou_intermediaria"] == 0)
        sem = [j for j in jan if j["lag"]["amostras"] == 0]
        r["A4"] = {"janelas_sem_rodada": len(sem), "rodadas_atrasadas": contadores.get("defasagem_rodadas_atrasadas"),
                   "ok": len(sem) == 0 and contadores.get("defasagem_rodadas_atrasadas") == 0}
        hist = {}
        for v in leituras:
            hist[v] = hist.get(v, 0) + 1
        r["A5"] = {"escritas": contadores.get("defasagem_escritas"), "rodadas": contadores.get("defasagem_rodadas"),
                   "leituras_por_rodada": resumo(leituras), "histograma": dict(sorted(hist.items())),
                   "erros_escrita": contadores.get("defasagem_erros_escrita"),
                   "erros_leitura": contadores.get("defasagem_erros_leitura")}
        r["A5"]["ok"] = (contadores.get("defasagem_escritas") == 3 * contadores.get("defasagem_rodadas", -1)
                         and min(leituras or [0]) >= 6 and contadores.get("defasagem_erros_escrita") == 0
                         and contadores.get("defasagem_erros_leitura") == 0)
        csvs = [c for c in glob.glob(os.path.join(d, "*.csv"))]
        com_lag = [os.path.basename(c) for c in csvs if "lag:" in open(c, encoding="utf-8", errors="replace").read()]
        stdout = open(os.path.join(d, "stdout.log"), encoding="utf-8", errors="replace").read()
        r["A8"] = {"politica_p1": sum(p["politica"] == "P1" for p in pol), "acao_ac5": sum(a["acao"]["tipo"] == "AC-5" for a in aco),
                   "csv_lidos": sorted(os.path.basename(c) for c in csvs), "csv_com_lag": com_lag,
                   "stdout_menciona_canario": "canario" in stdout.lower()}
        r["A8"]["ok"] = (r["A8"]["politica_p1"] == 0 and r["A8"]["acao_ac5"] == 0 and not com_lag
                         and not r["A8"]["stdout_menciona_canario"])
        return r

    # ---------------------------------------------------------------- com corte
    if chaves_de:
        ref = [x for x in ler_jsonl(chaves_de) if x["tipo"] == "fim"][-1]["defasagem_chaves"]
        r["A9_chaves_iguais_ao_sem_falha"] = [k["chave"] for k in ref] == [k["chave"] for k in chaves]
    t_vista_sonda = None
    for x in top:
        if x["t_ns"] >= t_corte and any(n["respondeu"] and 0 <= n["numero_de_nos"] < 3 for n in x["por_no"]):
            t_vista_sonda = x["t_ns"]
            break
    vlog = vista_nos_logs(servidores, t_corte) if servidores else None
    t_vista_log = vlog[0] if vlog else None
    t_vista = min(t for t in (t_vista_sonda, t_vista_log) if t is not None) if (t_vista_sonda or t_vista_log) else None

    def par(x, e, l):
        return next(p for p in x["pares"] if p["escritor"] == e and p["leitor"] == l)

    no_corte = [x for x in rod if x["t_inicio_ns"] >= t_corte and (t_vista is None or x["t_ns"] < t_vista)]
    detalhe = []
    for x in no_corte:
        maioria = [par(x, MAIORIA[0], MAIORIA[1]), par(x, MAIORIA[1], MAIORIA[0])]
        do_susp = [p for p in x["pares"] if SUSPEITO in (p["escritor"], p["leitor"])]
        detalhe.append({"rodada": x["rodada"], "t_inicio_menos_corte_s": (x["t_inicio_ns"] - t_corte) / 1e9,
                        "lag_max_ms": x["lag_max_ms"], "no_suspeito": x["no_suspeito"],
                        "maioria_vista": all(p["visto"] for p in maioria),
                        "maioria_lag_ms": [p["lag_ms"] for p in maioria],
                        "suspeito_nao_visto": all(not p["visto"] for p in do_susp),
                        "leituras": x["custo"]["leituras"]})
    cumpre = [y for y in detalhe if y["lag_max_ms"] == TETO and y["no_suspeito"] == SUSPEITO and y["maioria_vista"]
              and y["suspeito_nao_visto"]]
    r["A10"] = {"rodadas_no_corte_antes_da_vista": len(detalhe), "rodadas_que_cumprem": len(cumpre),
                "rodadas": detalhe, "ok": bool(detalhe) and len(cumpre) == len(detalhe)}
    p1 = [p for p in pol if p["politica"] == "P1"]
    ac5 = [a for a in aco if a["acao"]["tipo"] == "AC-5"]
    r["A11"] = {"p1": [{"no": p.get("no"), "t_decisao_ns": p["t_decisao_ns"], "motivo": p["motivo"]} for p in p1],
                "ac5": [{"alvo": a["acao"]["alvo"], "resultado": a["resultado"], "t_pedido_ns": a["t_pedido_ns"],
                         "executada": a["executada"]} for a in ac5]}
    r["A11"]["ok"] = (len(p1) >= 1 and p1[0].get("no") == SUSPEITO and len(ac5) >= 1
                      and r["A11"]["ac5"][0]["alvo"] == SUSPEITO and r["A11"]["ac5"][0]["resultado"] == "PREVISTA"
                      and (t_vista is None or p1[0]["t_decisao_ns"] < t_vista))
    lag_maioria = [v for y in detalhe for v in y["maioria_lag_ms"]]
    r["A12"] = {"lag_pares_maioria_ms": resumo(lag_maioria),
                "acima_de_1000": sum(v > 1000 for v in lag_maioria)}
    lc = [y["leituras"] for y in detalhe]
    sem = [j for j in jan if j["t_ini_ns"] >= t_corte and (t_vista is None or j["t_fim_ns"] <= t_vista)
           and j["lag"]["amostras"] == 0]
    r["A13"] = {"leituras_por_rodada_no_corte": resumo(lc), "janelas_sem_rodada_no_corte": len(sem),
                "ok": bool(lc) and max(lc) <= 600 and len(sem) == 0}
    t_lag = next((x["t_inicio_ns"] for x in rod if x["t_inicio_ns"] >= t_corte and x["lag_max_ms"] == TETO
                  and x["no_suspeito"] == SUSPEITO), None)
    t_volta = next((x["t_inicio_ns"] for x in rod if t_cura and x["t_inicio_ns"] >= t_cura
                    and all(p["visto"] for p in x["pares"])), None)
    t_p1 = p1[0]["t_decisao_ns"] if p1 else None

    def dif(t):
        return None if t is None else round((t - t_corte) / 1e9, 3)

    r["instantes"] = {"t_corte_ns": t_corte, "t_cura_ns": t_cura, "t_lag_ns": t_lag, "t_p1_ns": t_p1,
                      "t_vista_sonda_ns": t_vista_sonda, "t_vista_log_ns": t_vista_log,
                      "t_vista_log_linha": vlog[2] if vlog else None, "t_volta_ns": t_volta,
                      "t_lag_menos_corte_s": dif(t_lag), "t_p1_menos_corte_s": dif(t_p1),
                      "t_vista_sonda_menos_corte_s": dif(t_vista_sonda), "t_vista_log_menos_corte_s": dif(t_vista_log),
                      "t_volta_menos_cura_s": None if (t_volta is None or t_cura is None) else round((t_volta - t_cura) / 1e9, 3)}
    return r


if __name__ == "__main__":
    a = argparse.ArgumentParser()
    a.add_argument("dir")
    a.add_argument("--servidores")
    a.add_argument("--chaves-de")
    o = a.parse_args()
    print(json.dumps(analisar(o.dir, o.servidores, o.chaves_de), indent=1, ensure_ascii=False))
