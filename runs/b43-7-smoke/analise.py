"""B-43.7: criterios C1..C9 do smoke da mistura v3 sobre os brutos (e o contraste L0, v2).

Uso: python -I analise.py (neste diretorio). Grava analise.json e imprime um resumo.

Definicoes (DT-004, pre-registro): "Validate isolado" = Validate com chave comecando em "sid-";
"fracao INVALID" = INVALID / Validate isolados no CSV (contrato v2, so a janela de medicao). Tercos e
decimos sao intervalos iguais de start_ns entre o menor e o maior start_ns do CSV; cada Validate
isolado cai no intervalo do seu start_ns.

Tolerancias (DT-004, nao ajustaveis): C1 |terco1 - terco3| <= 1,0 p.p. (e, em >= 600 s, decimos
max - min <= 2,0 p.p.); C2 fracao em [4,5 %; 6,5 %]; C3 token_antigo / validate_isolados (stdout) =
5,00 % +- 0,10 p.p.
"""
import csv
import json
import re
import sys
from collections import Counter
from pathlib import Path

AQUI = Path(__file__).resolve().parent
EXECS = {
    "Q1": ("S1", "sync", "v3", 120), "Q2": ("S2", "sync", "v3", 120),
    "Q3": ("S1", "async", "v3", 120), "Q4": ("S2", "async", "v3", 120),
    "L1": ("S1", "sync", "v3", 600), "L2": ("S1", "async", "v3", 600),
    "L0": ("S1", "sync", "v2", 600),
}
EXIGE_RESET_CHEIO = {"Q2", "Q4", "L1", "L2"}


def pares(arq):
    p = AQUI / arq
    if not p.exists():
        return {}
    return dict(l.split() for l in p.read_text().split("\n") if l.strip())


def num(rx, txt, grupo=1, tipo=int):
    m = re.search(rx, txt)
    return tipo(m.group(grupo)) if m else None


def histograma(out, nome):
    m = re.search(r"^" + re.escape(nome) + r"\s+n=(\d+) \(descartados=(\d+)\)", out, re.M)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


def fatias(starts_inv, t0, t1, n):
    """starts_inv: lista de (start_ns, invalid?) dos Validate isolados; n fatias iguais de [t0, t1]."""
    tot = [0] * n
    inv = [0] * n
    larg = (t1 - t0) / n
    for s, i in starts_inv:
        k = min(int((s - t0) / larg), n - 1)
        tot[k] += 1
        inv[k] += i
    return [{"validate_isolados": a, "invalid": b, "frac_pct": round(100.0 * b / a, 4) if a else None}
            for a, b in zip(tot, inv)]


def analisar(q):
    cen, rep, mix, dur = EXECS[q]
    d = AQUI / q
    out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
    ops = Counter()
    iso = []
    logout_sid = Counter()
    tmin, tmax = None, None
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
    n_iso = len(iso)
    n_inv = sum(i for _, i in iso)
    frac = 100.0 * n_inv / n_iso if n_iso else None
    tercos = fatias(iso, tmin, tmax, 3)
    decimos = fatias(iso, tmin, tmax, 10)

    r = {"cenario": cen, "replicacao": rep, "mix": mix, "duracao_s": dur,
         "exit": int(pares("exits.txt").get(q, -1)),
         "linhas_csv": linhas, "csv_operation_return_code": dict(sorted(ops.items())),
         "validate_isolados_csv": n_iso, "invalid_isolados_csv": n_inv,
         "frac_invalid_isolados_pct": round(frac, 4) if frac is not None else None,
         "tercos": tercos, "decimos": decimos,
         "logout_isolados_csv_por_codigo": dict(logout_sid)}

    # stdout
    reg = num(r"registrados=(\d+)", out)
    desc = num(r"descartados_warmup=(\d+)", out)
    total_ops = num(r"Total de operacoes: (\d+)", out)
    lok_n, lok_d = histograma(out, "login_ok")
    lb_n, lb_d = histograma(out, "login_blocked")
    le_n, le_d = histograma(out, "login_err")
    noop_n, noop_d = histograma(out, "logout_noop")
    lgok_n, lgok_d = histograma(out, "logout_ok")
    r.update({"registrados": reg, "descartados_warmup": desc, "total_operacoes": total_ops,
              "login_ok_total": lok_n + lok_d, "login_total": lok_n + lok_d + lb_n + lb_d + le_n + le_d,
              "logout_noop_janela": noop_n, "logout_noop_warmup": noop_d,
              "logout_ok_janela": lgok_n,
              "linha_mistura": num(r"(Mistura: .*)", out, tipo=str),
              "linha_auditor": num(r"(Auditor: .*)", out, tipo=str),
              "linha_desfechos": num(r"(Desfechos Validate: .*)", out, tipo=str),
              "linha_carga": num(r"(Carga: .*)", out, tipo=str),
              "linha_reset": num(r"(Reset: .*)", out, tipo=str),
              "contrato": num(r"Contrato CSV: (v\d+)", out, tipo=str),
              "violacoes_stdout": num(r"Violacoes detectadas: (\d+)", out)})
    m3 = re.search(r"Mistura v3: validate_isolados=(\d+) token_antigo=(\d+) logout_isolados=(\d+) "
                   r"reposicoes=(\d+) reposicao_omitida=(\d+) reposicao_erro=(\d+)", out)
    v3 = dict(zip(("validate_isolados", "token_antigo", "logout_isolados", "reposicoes",
                   "reposicao_omitida", "reposicao_erro"), map(int, m3.groups()))) if m3 else None
    r["mistura_v3_stdout"] = v3
    with (d / "violacoes-rep-001.csv").open(newline="") as fh:
        viol = Counter(x["invariante"] for x in csv.DictReader(fh))
    r["violacoes_por_invariante"] = dict(viol)
    a = re.search(r"Auditor: i1_concorrentes=(\d+) i4_concorrentes=(\d+)", out)
    r["auditor_i1_concorrentes"] = int(a.group(1)) if a else None
    r["auditor_i4_concorrentes"] = int(a.group(2)) if a else None
    carga = json.loads((d / "carga-rep-001.json").read_text())
    estado = json.loads((d / "estado-rep-001.json").read_text())
    r["carga"] = carga
    r["estado"] = estado
    san = json.loads((AQUI / "verificador" / f"{q}.sanidade.json").read_text(encoding="utf-8"))
    r["verificador_exit"] = int(pares("verificador/exits.txt").get(q, -1))
    r["verificador_veredito"] = san.get("veredito")
    r["verificador_motivos"] = san.get("motivos")
    r["verificador_metricas"] = san.get("metricas")
    leitor = json.loads((AQUI / "leitor-csv-eventos.json").read_text())[f"{q}/rep-001.csv"]
    r["leitor"] = leitor

    if mix == "v2":
        ult = decimos[-1]
        r["contraste_v2"] = {"frac_invalid_isolados_pct": r["frac_invalid_isolados_pct"],
                             "ultimo_decimo_pct": ult["frac_pct"],
                             "primeiro_decimo_pct": decimos[0]["frac_pct"],
                             "logout_isolados_noop_ou_nf": {k: v for k, v in logout_sid.items()}}
        return r

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
    c["C3"] = {"token_antigo_pct": round(q_pct, 4), "validate_isolados_stdout": v3["validate_isolados"],
               "validate_isolados_csv": n_iso, "dif_stdout_csv": v3["validate_isolados"] - n_iso,
               "ok": abs(q_pct - 5.0) <= 0.10 and abs(v3["validate_isolados"] - n_iso) <= 4}
    vnf = ops["Validate NOT_FOUND"]
    lratio = (r["login_ok_total"] / r["login_total"]) if r["login_total"] else 0
    ident = (reg + desc) - total_ops
    c["C4"] = {"validate_not_found": vnf, "login_ok_frac": round(lratio, 6),
               "eventos_sobre_operacoes": round((reg + desc) / total_ops, 6),
               "eventos_menos_operacoes": ident, "dois_x_logins_ok": 2 * r["login_ok_total"],
               "ok": vnf == 0 and lratio >= 0.99 and (reg + desc) / total_ops > 1
                     and ident == 2 * r["login_ok_total"]}
    lo_csv = sum(logout_sid.values())
    c["C5"] = {"logout_isolados": v3["logout_isolados"], "reposicoes": v3["reposicoes"],
               "reposicao_omitida": v3["reposicao_omitida"], "reposicao_erro": v3["reposicao_erro"],
               "logout_isolados_csv": lo_csv, "dif_stdout_csv": v3["logout_isolados"] - lo_csv,
               "dif_logout_menos_repos_omit": v3["logout_isolados"] - v3["reposicoes"] - v3["reposicao_omitida"],
               "logout_noop_janela_limite_superior": noop_n,
               "logout_isolados_not_found_csv": logout_sid.get("NOT_FOUND", 0),
               "ok": v3["reposicao_erro"] == 0 and noop_n == 0
                     and abs(v3["logout_isolados"] - v3["reposicoes"] - v3["reposicao_omitida"]) <= 4
                     and abs(v3["logout_isolados"] - lo_csv) <= 4}
    c["C6"] = {"I1": viol.get("I1", 0), "I4": viol.get("I4", 0), "linha_auditor": r["linha_auditor"],
               "ok": viol.get("I1", 0) == 0 and viol.get("I4", 0) == 0 and r["linha_auditor"] is not None}
    c["C7"] = {"exit": r["verificador_exit"], "veredito": r["verificador_veredito"],
               "ok": r["verificador_exit"] == 0 and r["verificador_veredito"] == "OK"}
    antes = (estado.get("tamanhos_antes") or [None])[0]
    c["C8"] = {"tamanho_final": carga.get("tamanho_final"), "carga_ms": carga.get("duracao_ms"),
               "reset_antes_sessoes": antes, "reset_ms": estado.get("duracao_ms"),
               "ok": carga.get("tamanho_final") == 200000 and carga.get("veredito") == "OK"
                     and (q not in EXIGE_RESET_CHEIO or (antes or 0) >= 200000)}
    c["C9"] = {"exit": r["exit"], "ok": r["exit"] == 0}
    r["criterios"] = c
    r["todos_ok"] = all(v["ok"] for v in c.values())
    return r


saida = {}
for q in [x for x in EXECS if (AQUI / x / "rep-001.csv").exists()]:
    saida[q] = analisar(q)
json.dump(saida, open(AQUI / "analise.json", "w", newline="\n"), indent=1)
for q, r in saida.items():
    if "criterios" in r:
        print(q, "todos_ok=" + str(r["todos_ok"]),
              {k: v["ok"] for k, v in r["criterios"].items()}, "frac=", r["frac_invalid_isolados_pct"])
    else:
        print(q, "contraste v2:", r["contraste_v2"])
sys.exit(0)
