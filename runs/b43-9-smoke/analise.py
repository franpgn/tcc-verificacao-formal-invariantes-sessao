"""B-43.9: smoke local da I2 por marcos (DT-002), da dupla checagem no login e da deduplicacao I1/I4.

Uso: python -I analise.py [analise.json do B-43.7b]   (neste diretorio). Grava analise.json e imprime o
resumo. O argumento opcional so alimenta a tabela documental de login_ok p50/p99 (Q3/Q4 ao lado de
R1/R2 do B-43.7b); nao entra em criterio. SMOKE LOCAL, NAO E A T-F; AS LATENCIAS NAO SAO EVIDENCIA.

S1/S2 v3 (Q3..Q6): a parte S1/S2 de runs/b43-7b-smoke/analise.py (selado, nao editado) com as MESMAS
definicoes de C1..C9 (DT-004), C3 pelo IC de DT-008 (recusado se n*p*(1-p) < 100) e o criterio B438
(invalidacao_perdida = conflito_esgotado = 0). Novo (B-43.9): revertido_por_bloqueio = 0 e a linha
"Auditor I2:" toda em zero (I2 nao ocorre em S1/S2).

S3 (Q1, Q2): a parte S3 de runs/b43-8-smoke/analise.py (selado) e, novo: sem_marco = 0; em SYNC,
I1 = I4 = 0 (inline e varredura) e I2 inline classificada REAL = 0; em ASYNC, I2 REAL so relatada
(H-A0). Por fase (warm-up/descarte, medicao, depois) a partir do instante de cada registro do CSV de
violacoes contra a janela do CSV de eventos (que so tem a medicao): I2, I2_JANELA_CASCATA,
I2_CASCATA_INCOMPLETA. Os contadores do stdout ("Auditor I2:" e "Login:") sao da medicao;
"Auditor total:" e sem janela.

Sanidade nova (R-S3 da V-B43.7b): le seed= e rep= da linha "Configuracao:" de cada execucao e recusa o
conjunto se dois pares (semente, repeticao) coincidirem. Global: ISPN006010 no maximo 1 por no.
"""
import csv
import json
import math
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

AQUI = Path(__file__).resolve().parent
P0 = 0.05
Z = 3.2905
THREADS = 4
EXECS = {
    "Q1": ("S3", "sync", 300), "Q2": ("S3", "async", 300),
    "Q3": ("S1", "sync", 120), "Q4": ("S1", "async", 120),
    "Q5": ("S2", "sync", 120), "Q6": ("S1", "sync", 600),
}
# sementes pre-registradas (R-S2): 43900 + 100*i, i na ordem Q1..Q6
SEMENTES = {q: 43900 + 100 * i for i, q in enumerate(EXECS)}
EQUIV_B43_7B = {"Q3": "R1", "Q4": "R2"}


# ----------------------------------------------------------------------------- sanidade de sementes

def semente_e_rep(linha_config):
    """(seed, rep) da linha Configuracao: do stdout; ValueError se faltar algum."""
    s = re.search(r"\bseed=(-?\d+)", linha_config or "")
    r = re.search(r"\brep=(\d+)", linha_config or "")
    if not s or not r:
        raise ValueError("linha Configuracao sem seed= ou rep=: %r" % linha_config)
    return int(s.group(1)), int(r.group(1))


def checar_sementes(configs):
    """configs: {execucao: linha Configuracao}. Devolve {ok, pares, repetidos}; ok False se dois pares
    (semente, repeticao) coincidirem ou se alguma linha nao tiver seed=/rep=."""
    pares = {}
    erros = []
    for q, linha in configs.items():
        try:
            pares[q] = semente_e_rep(linha)
        except ValueError as e:
            erros.append(str(e))
    por_par = defaultdict(list)
    for q, p in pares.items():
        por_par[p].append(q)
    repetidos = {"%d,%d" % p: qs for p, qs in por_par.items() if len(qs) > 1}
    return {"ok": not erros and not repetidos, "pares": {q: list(p) for q, p in pares.items()},
            "repetidos": repetidos, "erros": erros}


# ----------------------------------------------------------------------------- utilitarios

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


def pct(lst, p):
    if not lst:
        return None
    s = sorted(lst)
    return s[min(len(s) - 1, max(0, int(math.ceil(p / 100.0 * len(s))) - 1))]


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


def linhas_b439(out):
    """Linhas novas do B-43.9 no stdout."""
    r = {"linha_auditor_i2": num(r"(Auditor I2: .*)", out, tipo=str),
         "linha_auditor_total": num(r"(Auditor total: .*)", out, tipo=str),
         "linha_login": num(r"(Login: .*)", out, tipo=str)}
    m = re.search(r"Auditor I2: concorrentes=(\d+) janela_cascata=(\d+) cascata_incompleta=(\d+) sem_marco=(\d+)", out)
    r["auditor_i2"] = dict(zip(("concorrentes", "janela_cascata", "cascata_incompleta", "sem_marco"),
                               map(int, m.groups()))) if m else None
    t = re.search(r"Auditor total: i1_concorrentes=(\d+) i4_concorrentes=(\d+) i2_concorrentes=(\d+)", out)
    r["auditor_total"] = dict(zip(("i1_concorrentes", "i4_concorrentes", "i2_concorrentes"),
                                  map(int, t.groups()))) if t else None
    r["revertido_por_bloqueio"] = num(r"Login: revertido_por_bloqueio=(\d+)", out)
    return r


def comum(q, out):
    r = {"exit": int(pares("exits.txt").get(q, -1)),
         "linha_configuracao": num(r"(Configuracao: .*)", out, tipo=str),
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
    r.update(linhas_b439(out))
    return r


def criterios_b439(r):
    """Comuns a todas: linhas novas presentes e sem_marco = 0."""
    a = r["auditor_i2"]
    return {"linhas_b439": {"ok": a is not None and r["auditor_total"] is not None
                            and r["revertido_por_bloqueio"] is not None},
            "sem_marco_0": {"v": a and a["sem_marco"], "ok": a is not None and a["sem_marco"] == 0}}


# ----------------------------------------------------------------------------- S1/S2 v3

def analisar_s1(q):
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
    c.update(criterios_b439(r))
    a = r["auditor_i2"] or {}
    c["B439_s1s2"] = {"revertido_por_bloqueio": r["revertido_por_bloqueio"], "auditor_i2": a,
                      "I2_qualquer_classe": sum(v for k, v in viol.items() if k.startswith("I2")),
                      "ok": r["revertido_por_bloqueio"] == 0 and bool(a) and all(v == 0 for v in a.values())
                            and not any(k.startswith("I2") for k in viol)}
    r["criterios"] = c
    r["todos_ok"] = all(v["ok"] for v in c.values())
    return r


# ----------------------------------------------------------------------------- S3

def instante_ns(iso):
    """Instant.toString() (UTC) para epoch-ns, com ate 9 casas."""
    m = re.match(r"(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d+))?Z$", iso)
    seg = int(datetime.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc).timestamp())
    frac = (m.group(2) or "").ljust(9, "0")[:9]
    return seg * 1_000_000_000 + int(frac)


def analisar_s3(q):
    cen, rep, dur = EXECS[q]
    d = AQUI / q
    out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
    r = {"cenario": cen, "replicacao": rep, "duracao_s": dur}
    r.update(comum(q, out))
    r["linhas_s3"] = re.findall(r"^(S3 .*)$", out, re.M)
    r["linha_varredura"] = num(r"^(Varredura final: .*)$", out, tipo=str)
    r["linha_warmup"] = num(r"^(Warm-up .*)$", out, tipo=str)
    ops = Counter()
    lat_login = []
    t_ini = t_fim = None
    with (d / "rep-001.csv").open(newline="") as fh:
        for row in csv.DictReader(fh):
            op, rc = row["operation"], row["return_code"]
            s, e = int(row["start_ns"]), int(row["end_ns"])
            t_ini = s if t_ini is None or s < t_ini else t_ini
            t_fim = e if t_fim is None or e > t_fim else t_fim
            ops[op + " " + rc] += 1
            if op == "Login" and rc == "OK":
                lat_login.append(e - s)
    r["csv_operation_return_code"] = dict(sorted(ops.items()))
    r["janela_csv_ns"] = [t_ini, t_fim]
    h = {k: histograma(out, k) for k in ("login_ok", "login_blocked", "login_err", "block_ok", "block_err")}
    r["histogramas"] = h
    with (d / "violacoes-rep-001.csv").open(newline="") as fh:
        rows = list(csv.DictReader(fh))
    r["violacoes_colunas"] = list(rows[0].keys()) if rows else None
    r["violacoes_por_invariante"] = dict(Counter(x["invariante"] for x in rows))
    por_classe = Counter()
    por_fase = defaultdict(Counter)
    for x in rows:
        t = instante_ns(x["instante"])
        fase = "antes_da_medicao" if t_ini is not None and t < t_ini else (
            "depois_da_medicao" if t_fim is not None and t > t_fim else "medicao")
        chave = x["invariante"] + (":" + x["classificacao"] if x.get("classificacao") else "")
        por_classe[chave] += 1
        por_fase[fase][chave] += 1
    r["violacoes_por_invariante_e_classe"] = dict(por_classe)
    r["violacoes_por_fase"] = {f: dict(v) for f, v in por_fase.items()}
    r["violacoes_amostra"] = rows[:30]
    var = d / "varredura-rep-001.csv"
    if var.exists():
        with var.open(newline="") as fh:
            ach = list(csv.DictReader(fh))
        r["varredura_achados"] = dict(Counter(a["invariante"] + ":" + a["classificacao"] for a in ach))
    else:
        r["varredura_achados"] = None
    res = d / "s3-resumo-rep-001.json"
    r["s3_resumo"] = json.loads(res.read_text(encoding="utf-8")) if res.exists() else None
    viol = r["violacoes_por_invariante"]
    vfin = r["varredura_achados"] or {}
    i2_real = por_classe.get("I2:REAL", 0)
    r["i2_real_inline"] = i2_real
    c = {"exit": {"v": r["exit"], "ok": r["exit"] == 0},
         "I4_inline": {"v": viol.get("I4", 0), "ok": viol.get("I4", 0) == 0 or rep != "sync"},
         "varredura_executada": {"ok": r["varredura_achados"] is not None},
         "conflito_esgotado": {"v": r["conflito_esgotado"], "ok": r["conflito_esgotado"] == 0 or rep != "sync"}}
    c.update(criterios_b439(r))
    if rep == "sync":
        c["I1_I4_sync"] = {"inline": viol.get("I1", 0) + viol.get("I4", 0),
                           "varredura": sum(v for k, v in vfin.items() if k.startswith(("I1:", "I4:"))),
                           "ok": viol.get("I1", 0) == 0 and viol.get("I4", 0) == 0
                                 and not any(k.startswith(("I1:", "I4:")) for k in vfin)}
        c["I2_REAL_sync"] = {"v": i2_real, "ok": i2_real == 0}
    r["criterios"] = c
    r["todos_ok"] = all(v["ok"] for v in c.values())
    return r


# ----------------------------------------------------------------------------- global

def ispn006010():
    out = {}
    for n in ("isn1", "isn2", "isn3"):
        p = AQUI / "servidores" / f"{n}.log"
        out[n] = p.read_text(encoding="utf-8", errors="replace").count("ISPN006010") if p.exists() else None
    return {"por_no": out, "ok": all(v is not None and v <= 1 for v in out.values())}


def login_ref(arq, q):
    if arq is None or q not in EQUIV_B43_7B:
        return None
    x = json.loads(Path(arq).read_text(encoding="utf-8")).get(EQUIV_B43_7B[q])
    if not x:
        return None
    return {"execucao": EQUIV_B43_7B[q], "login_ok": x["histogramas"]["login_ok"]}


def main(argv):
    ref = argv[1] if len(argv) > 1 else None
    saida = {}
    for q in [x for x in EXECS if (AQUI / x / "stdout.log").exists()]:
        r = analisar_s1(q) if EXECS[q][0] != "S3" else analisar_s3(q)
        r["semente_pre_registrada"] = SEMENTES[q]
        if q in EQUIV_B43_7B:
            r["login_ok_referencia_b43_7b"] = login_ref(ref, q)
        saida[q] = r
    glob = {"sementes": checar_sementes({q: r["linha_configuracao"] for q, r in saida.items()}),
            "ISPN006010": ispn006010()}
    glob["sementes"]["conferem_pre_registro"] = all(
        glob["sementes"]["pares"].get(q, [None])[0] == SEMENTES[q] for q in saida)
    glob["sementes"]["ok"] = glob["sementes"]["ok"] and glob["sementes"]["conferem_pre_registro"]
    saida["_global"] = glob
    json.dump(saida, open(AQUI / "analise.json", "w", newline="\n"), indent=1)
    for q, r in saida.items():
        if q == "_global":
            continue
        print(q, "todos_ok=" + str(r["todos_ok"]), {k: v["ok"] for k, v in r["criterios"].items()})
    print("global", {k: v["ok"] for k, v in glob.items()})
    ok = all(r["todos_ok"] for q, r in saida.items() if q != "_global") and all(v["ok"] for v in glob.values())
    return 0 if len(saida) > 1 and ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
