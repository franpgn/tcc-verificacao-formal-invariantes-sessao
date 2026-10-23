"""B-43.7b: smoke local do token antigo por sorteio de Bernoulli semeado (DT-008).

Uso: python -I analise.py [analise.json do B-43.7] [analise.json do B-43.8]   (neste diretorio).
Grava analise.json e imprime o resumo. Os dois argumentos sao opcionais e so alimentam a tabela
documental do excesso INVALID - token antigo (corrida Validate x Logout); nao entram em criterio.

Adaptado da parte S1/S2 de runs/b43-8-smoke/analise.py (selado, nao editado). C1, C2 e C4..C9 com as
MESMAS definicoes; o criterio B438 (invalidacao_perdida = conflito_esgotado = 0) tambem e mantido.
Muda so o C3 (DT-008): com n = validate_isolados (stdout), k = token_antigo (stdout), p0 = 0,05 e
z = 3,2905 (bilateral, 99,9 %), C3 passa se |k/n - p0| <= z * sqrt(p0 (1 - p0) / n) e
|n - n_csv| <= threads (DT-007). Se n * p0 * (1 - p0) < 100, C3 e FALHA de sanidade ("n insuficiente
para o IC").
"""
import csv
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path

AQUI = Path(__file__).resolve().parent
P0 = 0.05
Z = 3.2905
THREADS = 4
EXECS = {
    "R1": ("S1", "sync", 120), "R2": ("S1", "async", 120),
    "R3": ("S2", "sync", 120), "R4": ("S2", "async", 120),
    "R5": ("S1", "sync", 600),
}
# execucoes equivalentes dos smokes anteriores (so para a tabela documental do excesso)
EQUIV_B43_7 = {"R1": "Q1", "R2": "Q3", "R3": "Q2", "R4": "Q4", "R5": "L1"}
EQUIV_B43_8 = {"R1": "P1", "R2": "P2", "R5": "P5"}


def c3_ic(k, n, n_csv, threads, p0=P0, z=Z):
    """C3 de DT-008: IC binomial (aproximacao normal) e fronteira stdout x CSV de DT-007."""
    r = {"k": k, "n": n, "n_csv": n_csv, "z": z, "p0": p0, "dif_stdout_csv": n - n_csv,
         "margem_fronteira": threads}
    if n <= 0 or n * p0 * (1 - p0) < 100:
        r.update({"sanidade": "n insuficiente para o IC", "ok_ic": False, "ok_csv": False, "ok": False,
                  "frac_pct": None, "meia_largura_pp": None, "desvio_pp": None})
        return r
    meia = z * math.sqrt(p0 * (1 - p0) / n)
    frac = k / n
    ok_ic = abs(frac - p0) <= meia
    ok_csv = abs(n - n_csv) <= threads
    r.update({"sanidade": None, "frac_pct": 100.0 * frac, "meia_largura_pp": 100.0 * meia,
              "desvio_pp": 100.0 * (frac - p0), "banda_pct": [100.0 * (p0 - meia), 100.0 * (p0 + meia)],
              "ok_ic": ok_ic, "ok_csv": ok_csv, "ok": ok_ic and ok_csv})
    return r


def pares(arq):
    p = AQUI / arq
    if not p.exists():
        return {}
    return dict(l.split() for l in p.read_text().split("\n") if l.strip())


def num(rx, txt, grupo=1, tipo=int):
    m = re.search(rx, txt, re.M)
    return tipo(m.group(grupo)) if m else None


def histograma(out, nome):
    m = re.search(r"^" + re.escape(nome) + r"\s+n=(\d+) \(descartados=(\d+)\) p50=(\d+)ns p95=(\d+)ns "
                  r"p99=(\d+)ns", out, re.M)
    if not m:
        return {"n": 0, "descartados": 0}
    return {"n": int(m.group(1)), "descartados": int(m.group(2)), "p50_ns": int(m.group(3)),
            "p95_ns": int(m.group(4)), "p99_ns": int(m.group(5))}


def fatias(starts_inv, t0, t1, n):
    tot = [0] * n
    inv = [0] * n
    larg = (t1 - t0) / n
    for s, i in starts_inv:
        k = min(int((s - t0) / larg), n - 1)
        tot[k] += 1
        inv[k] += i
    return [{"validate_isolados": a, "invalid": b, "frac_pct": round(100.0 * b / a, 4) if a else None}
            for a, b in zip(tot, inv)]


def ler_csv_s1(d):
    ops = Counter()
    iso = []
    logout_sid = Counter()
    tmin = tmax = None
    linhas = 0
    with (d / "rep-001.csv").open(newline="") as fh:
        for row in csv.DictReader(fh):
            linhas += 1
            op, rc, key = row["operation"], row["return_code"], row["key"]
            s = int(row["start_ns"])
            tmin = s if tmin is None or s < tmin else tmin
            tmax = s if tmax is None or s > tmax else tmax
            ops[op + " " + rc] += 1
            if key.startswith("sid-"):
                if op == "Validate":
                    iso.append((s, 1 if rc == "INVALID" else 0))
                elif op == "Logout":
                    logout_sid[rc] += 1
    return ops, iso, logout_sid, tmin, tmax, linhas


def comum(q, out):
    r = {"exit": int(pares("exits.txt").get(q, -1)),
         "violacoes_stdout": num(r"Violacoes detectadas: (\d+)", out),
         "linha_auditor": num(r"(Auditor: .*)", out, tipo=str),
         "linha_invalidacao": num(r"(Invalidacao: .*)", out, tipo=str),
         "linha_reset": num(r"(Reset: .*)", out, tipo=str)}
    m = re.search(r"Invalidacao: perdida=(\d+) conflito_esgotado=(\d+)", out)
    r["invalidacao_perdida"] = int(m.group(1)) if m else None
    r["conflito_esgotado"] = int(m.group(2)) if m else None
    a = re.search(r"Auditor: i1_concorrentes=(\d+) i4_concorrentes=(\d+)", out)
    r["i1_concorrentes"] = int(a.group(1)) if a else None
    r["i4_concorrentes"] = int(a.group(2)) if a else None
    return r


def analisar(q):
    cen, rep, dur = EXECS[q]
    d = AQUI / q
    out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
    ops, iso, logout_sid, tmin, tmax, linhas = ler_csv_s1(d)
    n_iso = len(iso)
    n_inv = sum(i for _, i in iso)
    frac = 100.0 * n_inv / n_iso
    tercos = fatias(iso, tmin, tmax, 3)
    decimos = fatias(iso, tmin, tmax, 10)
    r = {"cenario": cen, "replicacao": rep, "mix": "v3", "duracao_s": dur, "linhas_csv": linhas,
         "csv_operation_return_code": dict(sorted(ops.items())),
         "validate_isolados_csv": n_iso, "invalid_isolados_csv": n_inv,
         "frac_invalid_isolados_pct": round(frac, 4), "tercos": tercos, "decimos": decimos,
         "logout_isolados_csv_por_codigo": dict(logout_sid)}
    r.update(comum(q, out))
    reg = num(r"registrados=(\d+)", out)
    desc = num(r"descartados_warmup=(\d+)", out)
    total_ops = num(r"Total de operacoes: (\d+)", out)
    h = {k: histograma(out, k) for k in ("login_ok", "login_blocked", "login_err", "logout_noop", "logout_ok")}
    login_ok_total = h["login_ok"]["n"] + h["login_ok"]["descartados"]
    login_total = sum(h[k]["n"] + h[k]["descartados"] for k in ("login_ok", "login_blocked", "login_err"))
    r.update({"registrados": reg, "descartados_warmup": desc, "total_operacoes": total_ops,
              "histogramas": h, "linha_mistura": num(r"(Mistura v3: .*)", out, tipo=str),
              "linha_desfechos": num(r"(Desfechos Validate: .*)", out, tipo=str),
              "linha_carga": num(r"(Carga: .*)", out, tipo=str),
              "linha_q": num(r"^(.*q=0\.05.*)$", out, tipo=str)})
    m3 = re.search(r"Mistura v3: validate_isolados=(\d+) token_antigo=(\d+) logout_isolados=(\d+) "
                   r"reposicoes=(\d+) reposicao_omitida=(\d+) reposicao_erro=(\d+)", out)
    v3 = dict(zip(("validate_isolados", "token_antigo", "logout_isolados", "reposicoes",
                   "reposicao_omitida", "reposicao_erro"), map(int, m3.groups())))
    r["mistura_v3_stdout"] = v3
    with (d / "violacoes-rep-001.csv").open(newline="") as fh:
        viol = Counter(x["invariante"] for x in csv.DictReader(fh))
    r["violacoes_por_invariante"] = dict(viol)
    carga = json.loads((d / "carga-rep-001.json").read_text())
    estado = json.loads((d / "estado-rep-001.json").read_text())
    r["carga"], r["estado"] = carga, estado
    san = json.loads((AQUI / "verificador" / f"{q}.sanidade.json").read_text(encoding="utf-8"))
    r["verificador_exit"] = int(pares("verificador/exits.txt").get(q, -1))
    r["verificador_veredito"] = san.get("veredito")
    r["leitor"] = json.loads((AQUI / "leitor-csv-eventos.json").read_text())[f"{q}/rep-001.csv"]

    c = {}
    dif13 = abs(tercos[0]["frac_pct"] - tercos[2]["frac_pct"])
    c1 = {"terco1_pct": tercos[0]["frac_pct"], "terco2_pct": tercos[1]["frac_pct"],
          "terco3_pct": tercos[2]["frac_pct"], "dif_1_3_pp": round(dif13, 4)}
    ok1 = dif13 <= 1.0
    if dur >= 600:
        fr = [x["frac_pct"] for x in decimos]
        c1["decimos_max_menos_min_pp"] = round(max(fr) - min(fr), 4)
        ok1 = ok1 and (max(fr) - min(fr)) <= 2.0
    c1["ok"] = ok1
    c["C1"] = c1
    c["C2"] = {"frac_pct": r["frac_invalid_isolados_pct"], "ok": 4.5 <= frac <= 6.5}
    c["C3"] = c3_ic(v3["token_antigo"], v3["validate_isolados"], n_iso, THREADS)
    vnf = ops["Validate NOT_FOUND"]
    lratio = login_ok_total / login_total if login_total else 0
    ident = (reg + desc) - total_ops
    c["C4"] = {"validate_not_found": vnf, "login_ok_frac": round(lratio, 6),
               "eventos_menos_operacoes": ident, "dois_x_logins_ok": 2 * login_ok_total,
               "ok": vnf == 0 and lratio >= 0.99 and ident == 2 * login_ok_total}
    lo_csv = sum(logout_sid.values())
    c["C5"] = {"logout_isolados": v3["logout_isolados"], "reposicoes": v3["reposicoes"],
               "reposicao_omitida": v3["reposicao_omitida"], "reposicao_erro": v3["reposicao_erro"],
               "logout_isolados_csv": lo_csv, "dif_stdout_csv": v3["logout_isolados"] - lo_csv,
               "logout_noop_janela": h["logout_noop"]["n"],
               "logout_isolados_not_found_csv": logout_sid.get("NOT_FOUND", 0),
               "ok": v3["reposicao_erro"] == 0 and h["logout_noop"]["n"] == 0
                     and v3["logout_isolados"] == v3["reposicoes"]
                     and abs(v3["logout_isolados"] - lo_csv) <= THREADS
                     and logout_sid.get("NOT_FOUND", 0) == 0}
    c["C6"] = {"I1": viol.get("I1", 0), "I4": viol.get("I4", 0),
               "ok": viol.get("I1", 0) == 0 and viol.get("I4", 0) == 0 and r["linha_auditor"] is not None}
    c["C7"] = {"exit": r["verificador_exit"], "veredito": r["verificador_veredito"],
               "ok": r["verificador_exit"] == 0 and r["verificador_veredito"] == "OK"}
    c["C8"] = {"tamanho_final": carga.get("tamanho_final"), "carga_ms": carga.get("duracao_ms"),
               "ok": carga.get("tamanho_final") == 200000 and carga.get("veredito") == "OK"}
    c["C9"] = {"exit": r["exit"], "ok": r["exit"] == 0}
    c["B438"] = {"invalidacao_perdida": r["invalidacao_perdida"], "conflito_esgotado": r["conflito_esgotado"],
                 "ok": r["invalidacao_perdida"] == 0 and r["conflito_esgotado"] == 0}
    r["criterios"] = c
    r["todos_ok"] = all(v["ok"] for v in c.values())
    # documental: a parte da fracao INVALID que nao e token antigo (corrida Validate x Logout)
    r["excesso_invalid_menos_token_pp"] = round(frac - 100.0 * v3["token_antigo"] / v3["validate_isolados"], 4)
    return r


def excesso_ref(arq, equiv, q):
    if arq is None or q not in equiv:
        return None
    d = json.loads(Path(arq).read_text(encoding="utf-8"))
    x = d.get(equiv[q])
    if not x or "mistura_v3_stdout" not in x:
        return None
    v = x["mistura_v3_stdout"]
    return {"execucao": equiv[q], "frac_invalid_pct": x["frac_invalid_isolados_pct"],
            "token_pct": round(100.0 * v["token_antigo"] / v["validate_isolados"], 4),
            "excesso_pp": round(x["frac_invalid_isolados_pct"] - 100.0 * v["token_antigo"] / v["validate_isolados"], 4)}


def main(argv):
    ref7 = argv[1] if len(argv) > 1 else None
    ref8 = argv[2] if len(argv) > 2 else None
    saida = {}
    for q in [x for x in EXECS if (AQUI / x / "stdout.log").exists()]:
        r = analisar(q)
        r["excesso_referencia"] = {"b43_7": excesso_ref(ref7, EQUIV_B43_7, q),
                                   "b43_8": excesso_ref(ref8, EQUIV_B43_8, q)}
        saida[q] = r
    json.dump(saida, open(AQUI / "analise.json", "w", newline="\n"), indent=1)
    for q, r in saida.items():
        c3 = r["criterios"]["C3"]
        print(q, "todos_ok=" + str(r["todos_ok"]), {k: v["ok"] for k, v in r["criterios"].items()},
              "C3: k=%d n=%d frac=%.4f%% meia=%.4f p.p. desvio=%+.4f p.p. stdout-csv=%d"
              % (c3["k"], c3["n"], c3["frac_pct"] or 0, c3["meia_largura_pp"] or 0, c3["desvio_pp"] or 0,
                 c3["dif_stdout_csv"]))
    return 0 if saida and all(r["todos_ok"] for r in saida.values()) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
