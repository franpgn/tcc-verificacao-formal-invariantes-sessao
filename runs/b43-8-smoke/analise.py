"""B-43.8: smoke local da invalidacao condicional (DT-003).

Uso: python -I analise.py [dir-smoke-b43-7]   (neste diretorio). Grava analise.json e imprime resumo.

S1 v3 (P1, P2, P5): tolerancias de DT-004 e C4..C9 do B-43.7 (mesmas definicoes de
runs/b43-7-smoke/analise.py, reaproveitado), mais: invalidacao_perdida = conflito_esgotado = 0;
NOT_FOUND = 0 em Logout isolado e Validate; logout_noop n = 0; logout_isolados = reposicoes; latencias de
Logout (stdout logout_ok; CSV Logout isolado) contra o smoke do B-43.7 (Q1 para P1, Q3 para P2, L1 para
P5), so documental.

S3 (P3, P4): exit 0, I4 = 0 (inline e varredura), conflito_esgotado = 0, invalidacao_perdida registrada,
I1 = 0 em SYNC; I1/I2 em ASYNC so registrados. Diagnostico do TOCTOU login x block: Logins OK (CSV) cuja
janela [start, end] intersecta a de um Block da mesma identidade (condicao necessaria do TOCTOU), e as
sessoes VALID sob identidade BLOCKED da varredura (I2 global) com a classe do rastro.
"""
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

AQUI = Path(__file__).resolve().parent
REF = Path(sys.argv[1]) if len(sys.argv) > 1 else None
EXECS = {
    "P1": ("S1", "sync", "v3", 120, "Q1"), "P2": ("S1", "async", "v3", 120, "Q3"),
    "P5": ("S1", "sync", "v3", 600, "L1"),
    "P3": ("S3", "sync", None, 300, None), "P4": ("S3", "async", None, 300, None),
}


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


def pct(lst, p):
    """Percentil por posto mais proximo."""
    if not lst:
        return None
    lst = sorted(lst)
    k = max(0, min(len(lst) - 1, -(-p * len(lst) // 100) - 1))
    return lst[int(k)]


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
    lat_logout_iso = []
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
                    lat_logout_iso.append(int(row["end_ns"]) - s)
    return ops, iso, logout_sid, lat_logout_iso, tmin, tmax, linhas


def latencias_ref(q):
    if REF is None or q is None or not (REF / q / "stdout.log").exists():
        return None
    out = (REF / q / "stdout.log").read_text(encoding="utf-8", errors="replace")
    _, _, _, lat, _, _, _ = ler_csv_s1(REF / q)
    return {"execucao_b43_7": q, "stdout_logout_ok": histograma(out, "logout_ok"),
            "csv_logout_isolado_p50_ns": pct(lat, 50), "csv_logout_isolado_p99_ns": pct(lat, 99),
            "csv_logout_isolado_n": len(lat)}


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


def analisar_s1(q):
    cen, rep, mix, dur, ref = EXECS[q]
    d = AQUI / q
    out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
    ops, iso, logout_sid, lat_iso, tmin, tmax, linhas = ler_csv_s1(d)
    n_iso = len(iso)
    n_inv = sum(i for _, i in iso)
    frac = 100.0 * n_inv / n_iso
    tercos = fatias(iso, tmin, tmax, 3)
    decimos = fatias(iso, tmin, tmax, 10)
    r = {"cenario": cen, "replicacao": rep, "mix": mix, "duracao_s": dur, "linhas_csv": linhas,
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
              "linha_carga": num(r"(Carga: .*)", out, tipo=str)})
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
    q_pct = 100.0 * v3["token_antigo"] / v3["validate_isolados"]
    c["C3"] = {"token_antigo_pct": round(q_pct, 4), "dif_stdout_csv": v3["validate_isolados"] - n_iso,
               "ok": abs(q_pct - 5.0) <= 0.10 and abs(v3["validate_isolados"] - n_iso) <= 4}
    vnf = ops["Validate NOT_FOUND"]
    lratio = login_ok_total / login_total if login_total else 0
    ident = (reg + desc) - total_ops
    c["C4"] = {"validate_not_found": vnf, "login_ok_frac": round(lratio, 6),
               "eventos_menos_operacoes": ident, "dois_x_logins_ok": 2 * login_ok_total,
               "ok": vnf == 0 and lratio >= 0.99 and ident == 2 * login_ok_total}
    lo_csv = sum(logout_sid.values())
    c["C5"] = {"logout_isolados": v3["logout_isolados"], "reposicoes": v3["reposicoes"],
               "reposicao_omitida": v3["reposicao_omitida"], "reposicao_erro": v3["reposicao_erro"],
               "logout_isolados_csv": lo_csv, "logout_noop_janela": h["logout_noop"]["n"],
               "logout_isolados_not_found_csv": logout_sid.get("NOT_FOUND", 0),
               "ok": v3["reposicao_erro"] == 0 and h["logout_noop"]["n"] == 0
                     and v3["logout_isolados"] == v3["reposicoes"]
                     and abs(v3["logout_isolados"] - lo_csv) <= 4
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
    r["latencia_logout"] = {"stdout_logout_ok": h["logout_ok"],
                            "csv_logout_isolado_p50_ns": pct(lat_iso, 50),
                            "csv_logout_isolado_p99_ns": pct(lat_iso, 99),
                            "csv_logout_isolado_n": len(lat_iso),
                            "referencia_b43_7": latencias_ref(ref)}
    return r


def analisar_s3(q):
    cen, rep, _, dur, _ = EXECS[q]
    d = AQUI / q
    out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
    r = {"cenario": cen, "replicacao": rep, "duracao_s": dur}
    r.update(comum(q, out))
    r["linhas_s3"] = re.findall(r"^(S3 .*)$", out, re.M)
    r["linhas_varredura"] = re.findall(r"^(Varredura.*|I[124].*)$", out, re.M)
    ops = Counter()
    logins = defaultdict(list)
    blocks = defaultdict(list)
    lat_logout = []
    with (d / "rep-001.csv").open(newline="") as fh:
        for row in csv.DictReader(fh):
            op, rc, key = row["operation"], row["return_code"], row["key"]
            s, e = int(row["start_ns"]), int(row["end_ns"])
            ops[op + " " + rc] += 1
            if op == "Login" and rc == "OK":  # contrato v2: Login OK = "OK"
                logins[key].append((s, e))
            elif op == "Block":
                blocks[key].append((s, e, rc))
            elif op == "Logout":
                lat_logout.append(e - s)
    r["csv_operation_return_code"] = dict(sorted(ops.items()))
    r["csv_logout_p50_ns"] = pct(lat_logout, 50)
    r["csv_logout_p99_ns"] = pct(lat_logout, 99)
    r["csv_logout_n"] = len(lat_logout)
    sobrepostos = []
    for ident, bl in blocks.items():
        for (bs, be, brc) in bl:
            for (ls, le) in logins.get(ident, []):
                if ls <= be and bs <= le:
                    sobrepostos.append({"identidade": ident, "block": [bs, be, brc], "login": [ls, le]})
    dur_block = sorted(be - bs for bl in blocks.values() for (bs, be, _) in bl)
    dur_login = sorted(le - ls for ll in logins.values() for (ls, le) in ll)
    # Exposicao esperada (aproximacao de chegadas independentes): para cada identidade com Block,
    # n_login * n_block * (duracao media do Block + duracao media do Login) / T, com T = extensao da
    # janela do CSV. E a esperanca do numero de pares Login x Block sobrepostos, condicao necessaria.
    t_ini = min(min(s for s, _ in v) for v in logins.values()) if logins else 0
    t_fim = max(max(e for _, e in v) for v in logins.values()) if logins else 0
    for v in blocks.values():
        for (bs, be, _) in v:
            t_ini, t_fim = min(t_ini, bs), max(t_fim, be)
    media_b = sum(dur_block) / len(dur_block) if dur_block else 0
    media_l = sum(dur_login) / len(dur_login) if dur_login else 0
    expo = sum(len(logins.get(i, [])) * len(v) for i, v in blocks.items()) * (media_b + media_l) / (t_fim - t_ini)         if t_fim > t_ini else None
    r["toctou_exposicao"] = {"janela_ns": t_fim - t_ini, "block_medio_ns": round(media_b), "login_ok_medio_ns": round(media_l),
                             "pares_login_block_mesma_identidade": sum(len(logins.get(i, [])) * len(v) for i, v in blocks.items()),
                             "sobreposicoes_esperadas": expo}
    r["toctou"] = {"blocks": sum(len(v) for v in blocks.values()),
                   "identidades_com_block": len(blocks),
                   "logins_ok": sum(len(v) for v in logins.values()),
                   "logins_ok_de_identidades_com_block": sum(len(logins.get(i, [])) for i in blocks),
                   "block_p50_ns": pct(dur_block, 50), "block_max_ns": dur_block[-1] if dur_block else None,
                   "login_ok_p50_ns": pct(dur_login, 50), "login_ok_max_ns": dur_login[-1] if dur_login else None,
                   "login_sobreposto_a_block_mesma_identidade": len(sobrepostos),
                   "casos": sobrepostos[:20]}
    with (d / "violacoes-rep-001.csv").open(newline="") as fh:
        rows = list(csv.DictReader(fh))
    r["violacoes_colunas"] = list(rows[0].keys()) if rows else None
    r["violacoes_por_invariante"] = dict(Counter(x["invariante"] for x in rows))
    cls = [k for k in (r["violacoes_colunas"] or []) if "class" in k]
    if cls:
        r["violacoes_por_invariante_e_classe"] = dict(Counter(x["invariante"] + ":" + x[cls[0]] for x in rows))
    r["violacoes_amostra"] = rows[:30]
    var = d / "varredura-rep-001.csv"
    if var.exists():
        with var.open(newline="") as fh:
            ach = list(csv.DictReader(fh))
        r["varredura_achados"] = dict(Counter(a["invariante"] + ":" + a["classificacao"] for a in ach))
        r["varredura_i2"] = [a for a in ach if a["invariante"].startswith("I2")]
    else:
        r["varredura_achados"] = None
    res = d / "s3-resumo-rep-001.json"
    r["s3_resumo"] = json.loads(res.read_text(encoding="utf-8")) if res.exists() else None
    viol = r["violacoes_por_invariante"]
    vfin = r["varredura_achados"] or {}
    c = {"exit": {"v": r["exit"], "ok": r["exit"] == 0},
         "I4_inline": {"v": viol.get("I4", 0), "ok": viol.get("I4", 0) == 0},
         "I4_varredura": {"v": sum(v for k, v in vfin.items() if k.startswith("I4:")),
                          "ok": r["varredura_achados"] is not None and not any(k.startswith("I4:") for k in vfin)},
         "conflito_esgotado": {"v": r["conflito_esgotado"], "ok": r["conflito_esgotado"] == 0},
         "invalidacao_perdida_registrada": {"v": r["invalidacao_perdida"], "ok": r["invalidacao_perdida"] is not None}}
    if rep == "sync":
        c["I1_sync"] = {"v": viol.get("I1", 0) + sum(v for k, v in vfin.items() if k.startswith("I1:")),
                        "ok": viol.get("I1", 0) == 0 and not any(k.startswith("I1:") for k in vfin)}
    r["criterios"] = c
    r["todos_ok"] = all(v["ok"] for v in c.values())
    return r


saida = {}
for q in [x for x in EXECS if (AQUI / x / "stdout.log").exists()]:
    saida[q] = analisar_s1(q) if EXECS[q][0] != "S3" else analisar_s3(q)
json.dump(saida, open(AQUI / "analise.json", "w", newline="\n"), indent=1)
for q, r in saida.items():
    print(q, "todos_ok=" + str(r["todos_ok"]), {k: v["ok"] for k, v in r["criterios"].items()})
sys.exit(0)
