"""B-43.5: smoke local da reprodutibilidade por --seed da sequencia de intencoes de cada thread.

Uso: python -I analise.py   (neste diretorio). Grava analise.json e imprime o resumo.
SMOKE LOCAL, NAO E A T-F NEM O REBASELINE; AS LATENCIAS NAO SAO EVIDENCIA.

Execucoes (todas --mix v3 --reset-state on --ops 0 --rep 1 --duration 120 --warmup-min-sec 10):
  P1 S1 sync  --threads 1 --seed 44500
  P2 S1 sync  --threads 1 --seed 44500   (MESMA semente de P1, de proposito: teste de reprodutibilidade)
  P3 S1 sync  --threads 1 --seed 44600   (controle negativo)
  P4 S1 sync  --threads 4 --seed 44700
  P5 S2 async --threads 4 --seed 44800

Reprodutibilidade (P1, P2, P3): CSV ordenado por op_id; cada evento vira (operation, key), com key
trocada por "*" quando for UUID (sid da triade). A janela de medicao e definida por tempo, entao cada
CSV e uma janela contigua da mesma sequencia infinita de intencoes da thread. Criterio P1 x P2: os 256
primeiros eventos de uma ocorrem na outra, e a sobreposicao a partir dali tem >= 50 000 eventos e e
100 % igual. Criterio P1 x P3: nenhuma janela de 256 eventos de P1 ocorre em P3.

S1/S2 v3 (P1..P5): a parte S1/S2 de runs/b43-9-smoke/analise.py (selado, nao editado) copiada com as
MESMAS definicoes de C1..C9 (DT-004), C3 pelo IC de DT-008 (recusado se n*p*(1-p) < 100), margem de
fronteira de C3/C5 = numero de threads da execucao (DT-007; 1 em P1..P3) e o criterio B438
(invalidacao_perdida = conflito_esgotado = 0). "Auditor I2:" toda zero e revertido_por_bloqueio = 0
como criterio so em SYNC (P1..P4; sem_marco = 0 em SYNC, DT-010); em P5 (ASYNC) so relatados.
Aceite do B-43.5: P1 x P2 e P1 x P3 como acima; C1..C9 e B438 em P4 e P5; exit 0 em todas.
Os criterios de P1..P3 sao relatados com as mesmas definicoes.

Sanidade R-S3: le seed= e rep= da linha "Configuracao:" de cada execucao e recusa o conjunto se dois
pares (semente, repeticao) coincidirem, com UMA isencao explicita: o par P1/P2.
Global: ISPN006010 no maximo 1 por no.
"""
import csv
import json
import math
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

AQUI = Path(__file__).resolve().parent
P0 = 0.05
Z = 3.2905
K_JANELA = 256
MIN_SOBREPOSICAO = 50_000
EXECS = {  # cenario, replicacao, duracao, threads
    "P1": ("S1", "sync", 120, 1), "P2": ("S1", "sync", 120, 1), "P3": ("S1", "sync", 120, 1),
    "P4": ("S1", "sync", 120, 4), "P5": ("S2", "async", 120, 4),
}
# sementes pre-registradas (R-S2, S0 = 44 500): P2 repete P1 de proposito
SEMENTES = {"P1": 44500, "P2": 44500, "P3": 44600, "P4": 44700, "P5": 44800}
ISENCAO_R_S3 = frozenset({"P1", "P2"})
ACEITE_C = ("P4", "P5")

UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


# ----------------------------------------------------------------------------- reprodutibilidade

def mascarar(key):
    """key trocada por '*' so quando for um UUID inteiro (sid da triade); 'sid-...' fica."""
    return "*" if UUID.fullmatch(key or "") else key


def eventos(caminho):
    """Lista de (operation, key mascarada) do CSV, em ordem de op_id."""
    with Path(caminho).open(newline="") as fh:
        linhas = [(int(r["op_id"]), r["operation"], r["key"]) for r in csv.DictReader(fh)]
    linhas.sort()
    return [(op, mascarar(k)) for _, op, k in linhas]


_MOD = (1 << 61) - 1
_BASE = 1_000_003


def _ids(*seqs):
    tab = {}
    return [[tab.setdefault(e, len(tab) + 1) for e in s] for s in seqs]


def _hashes(v, k):
    """Hash polinomial de cada janela de tamanho k de v (lista de inteiros)."""
    if len(v) < k:
        return []
    pk = pow(_BASE, k, _MOD)
    h = 0
    for x in v[:k]:
        h = (h * _BASE + x) % _MOD
    out = [h]
    for i in range(k, len(v)):
        h = (h * _BASE + v[i] - v[i - k] * pk) % _MOD
        out.append(h)
    return out


def primeira_ocorrencia(agulha, palheiro):
    """Indice da primeira ocorrencia da lista agulha (contigua) em palheiro, ou None."""
    a, p = _ids(agulha, palheiro)
    k = len(a)
    if k == 0 or len(p) < k:
        return None
    alvo = _hashes(a, k)[0]
    for i, h in enumerate(_hashes(p, k)):
        if h == alvo and p[i:i + k] == a:
            return i
    return None


def sobreposicao(a, b, k=K_JANELA, minimo=MIN_SOBREPOSICAO):
    """Acha os k primeiros eventos de a em b (ou de b em a) e compara a sobreposicao a partir dali.

    ok = existe, a sobreposicao tem >= minimo eventos e e 100 % igual."""
    r = {"k": k, "minimo": minimo, "len_a": len(a), "len_b": len(b)}
    i = primeira_ocorrencia(a[:k], b) if len(a) >= k else None
    if i is not None:
        r.update({"direcao": "inicio de a encontrado em b", "deslocamento": i})
        x, y = a, b[i:]
    else:
        j = primeira_ocorrencia(b[:k], a) if len(b) >= k else None
        if j is None:
            r.update({"direcao": None, "deslocamento": None, "sobreposicao": 0, "iguais": 0,
                      "primeira_diferenca": None, "ok": False})
            return r
        r.update({"direcao": "inicio de b encontrado em a", "deslocamento": j})
        x, y = a[j:], b
    n = min(len(x), len(y))
    dif = [t for t in range(n) if x[t] != y[t]]
    r.update({"sobreposicao": n, "iguais": n - len(dif), "diferentes": len(dif),
              "primeira_diferenca": dif[0] if dif else None,
              "ok": n >= minimo and not dif})
    return r


def janelas_comuns(a, b, k=K_JANELA):
    """Numero de janelas de k eventos de a que ocorrem (conferidas exatamente) em b."""
    va, vb = _ids(a, b)
    if len(va) < k or len(vb) < k:
        return 0
    por_hash = defaultdict(list)
    for i, h in enumerate(_hashes(vb, k)):
        por_hash[h].append(i)
    n = 0
    for i, h in enumerate(_hashes(va, k)):
        if any(vb[j:j + k] == va[i:i + k] for j in por_hash.get(h, ())):
            n += 1
    return n


# ----------------------------------------------------------------------------- sanidade de sementes

def semente_e_rep(linha_config):
    """(seed, rep) da linha Configuracao: do stdout; ValueError se faltar algum."""
    s = re.search(r"\bseed=(-?\d+)", linha_config or "")
    r = re.search(r"\brep=(\d+)", linha_config or "")
    if not s or not r:
        raise ValueError("linha Configuracao sem seed= ou rep=: %r" % linha_config)
    return int(s.group(1)), int(r.group(1))


def checar_sementes(configs, isencao=ISENCAO_R_S3):
    """configs: {execucao: linha Configuracao}. ok False se dois pares (semente, repeticao)
    coincidirem, salvo se o grupo repetido for exatamente o conjunto isento; ou se faltar seed=/rep=."""
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
    isentos = {c: qs for c, qs in repetidos.items() if frozenset(qs) == isencao}
    nao_isentos = {c: qs for c, qs in repetidos.items() if c not in isentos}
    return {"ok": not erros and not nao_isentos, "pares": {q: list(p) for q, p in pares.items()},
            "repetidos": repetidos, "repetidos_isentos": isentos, "erros": erros}


# ----------------------------------------------------------------------------- utilitarios (b43-9)

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
              "desvio_pp": 100.0 * (frac - p0), "npq": n * p0 * (1 - p0),
              "banda_pct": [100.0 * (p0 - meia), 100.0 * (p0 + meia)],
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


def linhas_b439(out):
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


# ----------------------------------------------------------------------------- S1/S2 v3

def analisar_s1(q):
    cen, rep, dur, threads = EXECS[q]
    d = AQUI / q
    out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
    ops, iso, logout_sid, tmin, tmax, linhas = ler_csv_s1(d)
    n_iso = len(iso)
    n_inv = sum(i for _, i in iso)
    frac = 100.0 * n_inv / n_iso
    tercos = fatias(iso, tmin, tmax, 3)
    decimos = fatias(iso, tmin, tmax, 10)
    r = {"cenario": cen, "replicacao": rep, "mix": "v3", "duracao_s": dur, "threads": threads,
         "linhas_csv": linhas, "csv_operation_return_code": dict(sorted(ops.items())),
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
              "linha_warmup": num(r"^(Warm-up .*)$", out, tipo=str)})
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
    c["C3"] = c3_ic(v3["token_antigo"], v3["validate_isolados"], n_iso, threads)
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
               "margem_fronteira": threads,
               "logout_noop_janela": h["logout_noop"]["n"],
               "logout_isolados_not_found_csv": logout_sid.get("NOT_FOUND", 0),
               "ok": v3["reposicao_erro"] == 0 and h["logout_noop"]["n"] == 0
                     and v3["logout_isolados"] == v3["reposicoes"]
                     and abs(v3["logout_isolados"] - lo_csv) <= threads
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
    a = r["auditor_i2"] or {}
    i2 = {"revertido_por_bloqueio": r["revertido_por_bloqueio"], "auditor_i2": a,
          "sem_marco": a.get("sem_marco"),
          "I2_qualquer_classe": sum(v for k, v in viol.items() if k.startswith("I2")),
          "linhas_b439": a != {} and r["auditor_total"] is not None and r["revertido_por_bloqueio"] is not None}
    if rep == "sync":
        i2["ok"] = (i2["linhas_b439"] and r["revertido_por_bloqueio"] == 0 and all(v == 0 for v in a.values())
                    and not any(k.startswith("I2") for k in viol))
        c["I2_sync"] = i2
    else:
        r["I2_async_relatado"] = i2
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


def reprodutibilidade():
    ev = {q: eventos(AQUI / q / "rep-001.csv") for q in ("P1", "P2", "P3")}
    r12 = sobreposicao(ev["P1"], ev["P2"])
    n13 = janelas_comuns(ev["P1"], ev["P3"])
    r13 = sobreposicao(ev["P1"], ev["P3"])
    return {"eventos": {q: len(v) for q, v in ev.items()},
            "P1_x_P2": r12,
            "P1_x_P3": {"janelas_de_%d_em_comum" % K_JANELA: n13, "sobreposicao_tentativa": r13,
                        "ok": n13 == 0 and not r13["ok"]},
            "ok": r12["ok"] and n13 == 0}


def main(argv):
    saida = {}
    for q in [x for x in EXECS if (AQUI / x / "stdout.log").exists()]:
        r = analisar_s1(q)
        r["semente_pre_registrada"] = SEMENTES[q]
        saida[q] = r
    glob = {"sementes": checar_sementes({q: r["linha_configuracao"] for q, r in saida.items()}),
            "ISPN006010": ispn006010(),
            "exit_todas": {"v": {q: r["exit"] for q, r in saida.items()},
                           "ok": len(saida) == len(EXECS) and all(r["exit"] == 0 for r in saida.values())},
            "reprodutibilidade": reprodutibilidade(),
            "C_P4_P5": {"ok": all(saida[q]["todos_ok"] for q in ACEITE_C if q in saida)
                        and all(q in saida for q in ACEITE_C)}}
    glob["sementes"]["conferem_pre_registro"] = all(
        glob["sementes"]["pares"].get(q, [None])[0] == SEMENTES[q] for q in saida)
    glob["sementes"]["ok"] = glob["sementes"]["ok"] and glob["sementes"]["conferem_pre_registro"]
    saida["_global"] = glob
    json.dump(saida, open(AQUI / "analise.json", "w", newline="\n"), indent=1)
    for q, r in saida.items():
        if q == "_global":
            continue
        print(q, "todos_ok=" + str(r["todos_ok"]), {k: v["ok"] for k, v in r["criterios"].items()})
    rp = glob["reprodutibilidade"]
    print("P1 x P2:", {k: rp["P1_x_P2"].get(k) for k in ("direcao", "deslocamento", "sobreposicao",
                                                           "iguais", "diferentes", "ok")})
    print("P1 x P3:", {k: v for k, v in rp["P1_x_P3"].items() if k != "sobreposicao_tentativa"})
    print("global", {k: v["ok"] for k, v in glob.items()})
    return 0 if all(v["ok"] for v in glob.values()) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
