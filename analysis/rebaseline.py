"""Analise do rebaseline S1/S2 v3 em DIST_SYNC e DIST_ASYNC (DT-009, DT-010, DT-011).

Pre-registro vinculante: docs/pre-registro-rebaseline-aprovado-2026-10-23.md (PR, secoes citadas como
"PR §n"), com o instante de injecao e a tolerancia de K6 substituidos pela DT-011. Nada aqui e parametro
livre: os valores do PR entram como constantes nomeadas.

Subcomandos:
  checar  --exec <dir> --matriz <tsv>                avalia K1..K10 de UMA execucao; grava checagem.json.
                                                     Exit 0 ACEITA, 10 RECUSADA, 20 PARADA, 2 uso/arquivo.
  estado  --saida <dir> --matriz <tsv> [--listar-reposicoes]
                                                     paradas (a)..(d) e R-S3 da campanha; grava estado.json.
                                                     Exit 0 segue, 20 parada, 2 uso.
  tabelas --saida <dir> --matriz <tsv> --destino <dir>   (numpy/scipy; roda no host)
  piloto  --saida <dir> --matriz <tsv>                   (roda no host)

checar e estado usam so a biblioteca padrao (rodam na VM, com python3 -I). As definicoes de C1..C9 sao
COPIADAS de runs/b43-9-smoke/analise.py (parte S1/S2; o arquivo selado nao e importado nem editado),
com as mudancas do PR §6: R7 (denominador de C1/C2 com falha), K5 so reportado com falha, violacoes do
CSV com I2 REAL.
"""
import argparse
import csv
import json
import math
import re
import sys
from collections import Counter, defaultdict
from fractions import Fraction
from pathlib import Path

# ----------------------------------------------------------------------------- constantes do PR
P0 = 0.05                 # DT-008 (C3: p = 0,05)
Z = 3.2905                # DT-008 (C3: z bilateral 99,9 %)
THREADS = 4               # PR §2 (--threads 4); margem de fronteira de DT-007
S0 = 50000                # PR §4 (R-S1)
PASSO_SEMENTE = 100       # PR §4
REP = 1                   # PR §2 (--rep 1)
BOOT_SEED = 231024        # PR §7
N_BOOT = 10000            # PR §7
N_MIN_P99 = 1000          # PR §7
N_MIN_P999 = 10000        # PR §7
MIN_EXEC_CONTRASTE = 8    # PR §7
ALFA = 0.05               # PR §7 (Mann-Whitney)
INJ_OFFSET = 49           # DT-011 (substitui os +35 s do PR §2): segundos da ancora "Warm-up ate"
INJ_TOL = (48.0, 51.0)    # DT-011 (P3): disparo entre -1 e +2 s do previsto; fora disso K6 recusa
DURACAO = 300             # PR §2
C1_TERCOS_PP = 1.0        # DT-004 (C1)
C1_DECIMOS_PP = 2.0       # DT-004 (C1, so em execucoes >= 600 s)
C2_FAIXA_PCT = (4.5, 6.5)  # DT-004 (C2)
C4_LOGIN_OK_MIN = 0.99    # PR §6 (K5/C4)
CARGA_ENTRADAS = 200000   # PR §6 (C8)
MAX_RECUSAS = 6           # PR §6, parada (b): mais de 6 recusas no total
MAX_RECUSAS_CELULA = 1    # PR §6, parada (a): duas recusas na mesma celula
MAX_REPROV_C3 = 1         # PR §4 (R-S4) e §6 (d): duas reprovacoes de C3 param
ISPN006010_MAX = 1        # PR §6 (K8)
NOS = ("isn1", "isn2", "isn3")
FASES_CAMPANHA = ("campanha", "reposicao")

# PR §7: operacoes e desfechos que entram nos percentis
VALIDOS = {"Login": ("OK",), "Validate": ("VALID", "INVALID"), "Logout": ("NONE",),
           "IncrementFailure": ("COUNTED",), "ResetFailures": ("NONE",)}
OPERACOES_S1 = ("Login", "Validate", "Logout", "IncrementFailure", "ResetFailures")
OPERACOES_S2 = ("Login", "Validate", "Logout", "IncrementFailure")
SUBDIVIDIDAS = ("Validate", "Logout")   # tambem separadas em isolado (sid-) e triade
# PR §6: violacoes contadas do CSV; I2 so com rotulo exato "I2" e classificacao REAL
INVARIANTES = ("I1", "I2", "I3", "I4", "I5", "I6")


class ErroUso(Exception):
    """Uso invalido ou arquivo obrigatorio ausente: exit 2."""


# ============================================================================= utilitarios

def percentil(valores, p):
    """Posto mais proximo (PR §7): ordenado[ceil(p*n) - 1], com p em fracao; aritmetica exata."""
    if not valores:
        return None
    s = sorted(valores)
    n = len(s)
    fp = Fraction(str(p))
    k = -((-fp.numerator * n) // fp.denominator)   # ceil(p*n) exato
    return s[min(n - 1, max(0, k - 1))]


def p99(valores):
    """p99 so com n >= N_MIN_P99 (PR §7); senao None."""
    return percentil(valores, 0.99) if len(valores) >= N_MIN_P99 else None


def p999(valores):
    """p99,9 so com n >= N_MIN_P999 (PR §7); senao None."""
    return percentil(valores, 0.999) if len(valores) >= N_MIN_P999 else None


def num(rx, txt, grupo=1, tipo=int):
    m = re.search(rx, txt or "", re.M)
    return tipo(m.group(grupo)) if m else None


def ler_json(p, padrao=None):
    p = Path(p)
    if not p.exists():
        return padrao
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except ValueError:
        return padrao


def gravar_json(p, obj):
    Path(p).write_bytes((json.dumps(obj, indent=1, ensure_ascii=False, sort_keys=False) + "\n").encode("utf-8"))


def ler_matriz(caminhos):
    """{i: linha} de um ou mais TSV da matriz (colunas de scripts/rebaseline_matriz.py)."""
    out = {}
    for c in caminhos:
        with open(c, newline="", encoding="ascii") as fh:
            for x in csv.DictReader(fh, delimiter="\t"):
                out[int(x["i"])] = x
    return out


# ============================================================================= copiado de analise.py (B-43.9)

def semente_e_rep(linha_config):
    """(seed, rep) da linha Configuracao: do stdout; ValueError se faltar algum (R-S3)."""
    s = re.search(r"\bseed=(-?\d+)", linha_config or "")
    r = re.search(r"\brep=(\d+)", linha_config or "")
    if not s or not r:
        raise ValueError("linha Configuracao sem seed= ou rep=: %r" % linha_config)
    return int(s.group(1)), int(r.group(1))


def c3_ic(k, n, n_csv, threads, p0=P0, z=Z):
    """C3 de DT-008: IC binomial (aproximacao normal) e fronteira stdout x CSV de DT-007."""
    r = {"k": k, "n": n, "n_csv": n_csv, "z": z, "p0": p0, "dif_stdout_csv": None if n is None or n_csv is None
         else n - n_csv, "margem_fronteira": threads}
    if not n or n <= 0 or n * p0 * (1 - p0) < 100 or k is None or n_csv is None:
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


def histograma(out, nome):
    m = re.search(r"^" + re.escape(nome) + r"\s+n=(\d+) \(descartados=(\d+)\) p50=(\d+)ns p95=(\d+)ns "
                  r"p99=(\d+)ns", out or "", re.M)
    if not m:
        return {"n": 0, "descartados": 0}
    return {"n": int(m.group(1)), "descartados": int(m.group(2)), "p50_ns": int(m.group(3)),
            "p95_ns": int(m.group(4)), "p99_ns": int(m.group(5))}


def fatias(starts_inv, t0, t1, n):
    tot = [0] * n
    inv = [0] * n
    larg = (t1 - t0) / n
    for s, i in starts_inv:
        k = min(int((s - t0) / larg), n - 1) if larg > 0 else 0
        tot[k] += 1
        inv[k] += i
    return [{"validate_isolados": a, "invalid": b, "frac_pct": round(100.0 * b / a, 4) if a else None}
            for a, b in zip(tot, inv)]


def linhas_b439(out):
    r = {}
    m = re.search(r"Auditor I2: concorrentes=(\d+) janela_cascata=(\d+) cascata_incompleta=(\d+) sem_marco=(\d+)",
                  out or "")
    r["auditor_i2"] = dict(zip(("concorrentes", "janela_cascata", "cascata_incompleta", "sem_marco"),
                               map(int, m.groups()))) if m else None
    t = re.search(r"Auditor total: i1_concorrentes=(\d+) i4_concorrentes=(\d+) i2_concorrentes=(\d+)", out or "")
    r["auditor_total"] = dict(zip(("i1_concorrentes", "i4_concorrentes", "i2_concorrentes"),
                                  map(int, t.groups()))) if t else None
    r["revertido_por_bloqueio"] = num(r"Login: revertido_por_bloqueio=(\d+)", out)
    return r


# ============================================================================= leitura dos brutos

def ler_csv_eventos(caminho, latencias=False, cenario=None):
    """Uma passada no rep-001.csv (contrato v2). Com latencias=True guarda, por rotulo de operacao, as
    latencias (end_ns - start_ns) dos desfechos validos (PR §7)."""
    ops = Counter()
    iso = []                       # (start_ns, return_code) dos Validate isolados (chave sid-)
    logout_sid = Counter()
    rc_por_rotulo = defaultdict(Counter)
    lat = defaultdict(list)
    tmin = tmax = None
    linhas = 0
    with open(caminho, newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            linhas += 1
            op, rc, key = row["operation"], row["return_code"], row["key"]
            s = int(row["start_ns"])
            tmin = s if tmin is None or s < tmin else tmin
            tmax = s if tmax is None or s > tmax else tmax
            ops[op + " " + rc] += 1
            isolado = key.startswith("sid-")
            if isolado:
                if op == "Validate":
                    iso.append((s, rc))
                elif op == "Logout":
                    logout_sid[rc] += 1
            rotulos = [op]
            if op in SUBDIVIDIDAS:
                rotulos.append(op + ("-isolado" if isolado else "-triade"))
            for r in rotulos:
                rc_por_rotulo[r][rc] += 1
            if latencias and rc in VALIDOS.get(op, ()):
                d = int(row["end_ns"]) - s
                for r in rotulos:
                    lat[r].append(d)
    return {"ops": ops, "iso": iso, "logout_sid": logout_sid, "tmin": tmin, "tmax": tmax, "linhas": linhas,
            "rc_por_rotulo": rc_por_rotulo, "lat": lat if latencias else None}


def rotulos_de(cenario):
    base = OPERACOES_S1 if cenario == "S1" else OPERACOES_S2
    out = []
    for op in base:
        out.append(op)
        if op in SUBDIVIDIDAS:
            out += [op + "-isolado", op + "-triade"]
    return out


def estatisticas_por_operacao(caminho_ou_leitura, cenario):
    """PR §7, por execucao e operacao: n (validos), p50, p95, p99 (None se n < 1000), p99,9 (n >= 10 000),
    contagens e taxas por return_code (todos os desfechos) e vazao (validos / janela do primeiro ao ultimo
    start_ns da execucao)."""
    lt = caminho_ou_leitura if isinstance(caminho_ou_leitura, dict) else \
        ler_csv_eventos(caminho_ou_leitura, latencias=True)
    janela_s = (lt["tmax"] - lt["tmin"]) / 1e9 if lt["tmin"] is not None else 0.0
    out = {}
    for rot in rotulos_de(cenario):
        vals = lt["lat"].get(rot, [])
        cont = dict(lt["rc_por_rotulo"].get(rot, {}))
        tot = sum(cont.values())
        out[rot] = {"n": len(vals),
                    "p50": percentil(vals, 0.50), "p95": percentil(vals, 0.95), "p99": p99(vals), "p999": p999(vals),
                    "contagens": cont, "taxas": {k: v / tot for k, v in cont.items()} if tot else {},
                    "vazao_por_s": len(vals) / janela_s if janela_s > 0 else None}
    return out


def contar_violacoes(caminho):
    """PR §6: contagens do violacoes-rep-NNN.csv. I2 so com invariante == "I2" e classificacao == "REAL";
    os rotulos de janela/cascata e *_concorrentes sao reportados a parte, nunca somados."""
    por_inv = Counter()
    por_inv_classe = Counter()
    i2_real = 0
    existe = Path(caminho).exists()
    if existe:
        with open(caminho, newline="", encoding="utf-8") as fh:
            for x in csv.DictReader(fh):
                inv = (x.get("invariante") or "").strip()
                cl = (x.get("classificacao") or "").strip()
                por_inv[inv] += 1
                por_inv_classe[inv + (":" + cl if cl else "")] += 1
                if inv == "I2" and cl == "REAL":
                    i2_real += 1
    viol = {k: por_inv.get(k, 0) for k in INVARIANTES if k != "I2"}
    viol["I2"] = i2_real
    return {"arquivo_presente": existe, "violacoes": viol, "total": sum(viol.values()),
            "por_invariante": dict(por_inv), "por_invariante_e_classe": dict(por_inv_classe),
            "i2_qualquer_classe": sum(v for k, v in por_inv.items() if k.startswith("I2"))}


# ============================================================================= C1..C5, B438 (mistura v3)

def criterios_mistura(out, lt, falha, duracao=DURACAO):
    """C1..C5 e B438 (definicoes de runs/b43-9-smoke/analise.py) com R7: com falha (F1/F3), o denominador de
    C1/C2 e o numero de Validate isolados com VALID ou INVALID; os demais desfechos sao reportados a parte.
    Sem falha a definicao e a antiga (todos os Validate isolados)."""
    c = {}
    iso_todos = lt["iso"] if lt else []
    outros = Counter(rc for _, rc in iso_todos if rc not in ("VALID", "INVALID"))
    if falha == "none":
        iso = [(s, 1 if rc == "INVALID" else 0) for s, rc in iso_todos]
    else:
        iso = [(s, 1 if rc == "INVALID" else 0) for s, rc in iso_todos if rc in ("VALID", "INVALID")]
    c["validate_isolados_outros"] = dict(outros)
    n_iso = len(iso)
    n_inv = sum(i for _, i in iso)
    if n_iso and lt["tmin"] is not None:
        tercos = fatias(iso, lt["tmin"], lt["tmax"], 3)
        decimos = fatias(iso, lt["tmin"], lt["tmax"], 10)
        if all(t["frac_pct"] is not None for t in (tercos[0], tercos[2])):
            dif13 = abs(tercos[0]["frac_pct"] - tercos[2]["frac_pct"])
            c1 = {"terco1_pct": tercos[0]["frac_pct"], "terco2_pct": tercos[1]["frac_pct"],
                  "terco3_pct": tercos[2]["frac_pct"], "dif_1_3_pp": round(dif13, 4), "denominador": n_iso,
                  "tercos": tercos}
            ok1 = dif13 <= C1_TERCOS_PP
            if duracao >= 600:
                fr = [x["frac_pct"] for x in decimos if x["frac_pct"] is not None]
                c1["decimos_max_menos_min_pp"] = round(max(fr) - min(fr), 4)
                ok1 = ok1 and (max(fr) - min(fr)) <= C1_DECIMOS_PP
            c1["ok"] = ok1
        else:
            c1 = {"ok": False, "motivo": "terco sem Validate isolado", "denominador": n_iso, "tercos": tercos}
        frac = 100.0 * n_inv / n_iso
        c["C1"] = c1
        c["C2"] = {"frac_pct": round(frac, 4), "denominador": n_iso, "invalid": n_inv,
                   "ok": C2_FAIXA_PCT[0] <= frac <= C2_FAIXA_PCT[1]}
    else:
        c["C1"] = {"ok": False, "motivo": "sem Validate isolado no CSV", "denominador": 0}
        c["C2"] = {"ok": False, "frac_pct": None, "denominador": 0}
    m3 = re.search(r"Mistura v3: validate_isolados=(\d+) token_antigo=(\d+) logout_isolados=(\d+) "
                   r"reposicoes=(\d+) reposicao_omitida=(\d+) reposicao_erro=(\d+)", out or "")
    v3 = dict(zip(("validate_isolados", "token_antigo", "logout_isolados", "reposicoes",
                   "reposicao_omitida", "reposicao_erro"), map(int, m3.groups()))) if m3 else None
    c["mistura_v3_stdout"] = v3
    n_csv_iso = len(iso_todos) if lt else None
    c["C3"] = c3_ic(v3 and v3["token_antigo"], v3 and v3["validate_isolados"], n_csv_iso, THREADS)
    # C4
    ops = lt["ops"] if lt else Counter()
    h = {k: histograma(out, k) for k in ("login_ok", "login_blocked", "login_err", "logout_noop", "logout_ok")}
    reg = num(r"registrados=(\d+)", out)
    desc = num(r"descartados_warmup=(\d+)", out)
    total_ops = num(r"Total de operacoes: (\d+)", out)
    login_ok_total = h["login_ok"]["n"] + h["login_ok"]["descartados"]
    login_total = sum(h[k]["n"] + h[k]["descartados"] for k in ("login_ok", "login_blocked", "login_err"))
    vnf = ops["Validate NOT_FOUND"]
    lratio = login_ok_total / login_total if login_total else 0
    ident = (reg + desc) - total_ops if None not in (reg, desc, total_ops) else None
    c["C4"] = {"validate_not_found": vnf, "login_ok_frac": round(lratio, 6),
               "eventos_menos_operacoes": ident, "dois_x_logins_ok": 2 * login_ok_total,
               "ok": bool(lt) and vnf == 0 and lratio >= C4_LOGIN_OK_MIN and ident == 2 * login_ok_total}
    # C5
    logout_sid = lt["logout_sid"] if lt else Counter()
    lo_csv = sum(logout_sid.values())
    if v3:
        c["C5"] = {"logout_isolados": v3["logout_isolados"], "reposicoes": v3["reposicoes"],
                   "reposicao_omitida": v3["reposicao_omitida"], "reposicao_erro": v3["reposicao_erro"],
                   "logout_isolados_csv": lo_csv, "dif_stdout_csv": v3["logout_isolados"] - lo_csv,
                   "logout_noop_janela": h["logout_noop"]["n"],
                   "logout_isolados_not_found_csv": logout_sid.get("NOT_FOUND", 0),
                   "ok": bool(lt) and v3["reposicao_erro"] == 0 and h["logout_noop"]["n"] == 0
                         and v3["logout_isolados"] == v3["reposicoes"]
                         and abs(v3["logout_isolados"] - lo_csv) <= THREADS
                         and logout_sid.get("NOT_FOUND", 0) == 0}
    else:
        c["C5"] = {"ok": False, "motivo": "linha Mistura v3 ausente"}
    m = re.search(r"Invalidacao: perdida=(\d+) conflito_esgotado=(\d+)", out or "")
    perd, esg = (int(m.group(1)), int(m.group(2))) if m else (None, None)
    c["B438"] = {"invalidacao_perdida": perd, "conflito_esgotado": esg, "ok": perd == 0 and esg == 0}
    return c


# ============================================================================= checar

def _crit(valor, limite, ok, papel):
    return {"valor": valor, "limite": limite, "ok": bool(ok), "papel": papel}


def checar_execucao(d, matriz):
    """K1..K10 do PR §6 (com K6 da DT-011) para o diretorio de uma execucao. Devolve o dict de checagem."""
    d = Path(d)
    for obrig in ("execucao.json", "portao.json", "ispn006010.json"):
        if not (d / obrig).exists():
            raise ErroUso("arquivo obrigatorio ausente: %s" % (d / obrig))
    ex = ler_json(d / "execucao.json")
    if ex is None:
        raise ErroUso("execucao.json ilegivel em %s" % d)
    i = int(ex["i"])
    if i not in matriz:
        raise ErroUso("i=%d nao esta na matriz" % i)
    lin = matriz[i]
    cen, modo, falha = lin["cenario"], lin["modo"], lin["falha"]
    out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace") if (d / "stdout.log").exists() else ""
    lt = ler_csv_eventos(d / "rep-001.csv") if (d / "rep-001.csv").exists() else None
    com_falha = falha != "none"
    crit = {}
    motivos = []

    # K1: portao antes + Reset: depois=0,0 (PR §6)
    portao = ler_json(d / "portao.json", {})
    m = re.search(r"^Reset: .*depois=(\d+),(\d+)", out, re.M)
    reset = [int(m.group(1)), int(m.group(2))] if m else None
    crit["K1"] = _crit({"portao_ok": portao.get("ok"), "reset_depois": reset},
                       "portao 3 nos HEALTHY >= 60 s sem netem; Reset depois=0,0",
                       portao.get("ok") is True and reset == [0, 0], "recusa")
    # K2: exit do workload
    crit["K2"] = _crit({"workload_exit": ex.get("workload_exit"), "csv": lt is not None}, "exit 0 e rep-001.csv",
                       ex.get("workload_exit") == 0 and lt is not None, "recusa")
    # K3: VerificadorSanidade
    san = ler_json(d / "sanidade.json", {}) or {}
    crit["K3"] = _crit({"verificador_exit": ex.get("verificador_exit"), "veredito": san.get("veredito")},
                       "exit 0 e veredito OK", ex.get("verificador_exit") == 0 and san.get("veredito") == "OK",
                       "recusa")
    # K4 / K5: mistura v3
    cm = criterios_mistura(out, lt, falha)
    carga = ler_json(d / "carga-rep-001.json", {}) or {}
    c8 = {"tamanho_final": carga.get("tamanho_final"), "veredito": carga.get("veredito"),
          "ok": carga.get("tamanho_final") == CARGA_ENTRADAS and carga.get("veredito") == "OK"}
    k4 = {"C1": cm["C1"]["ok"], "C2": cm["C2"]["ok"], "C3": cm["C3"]["ok"], "C8": c8["ok"]}
    crit["K4"] = _crit(k4, "C1, C2, C3 (DT-008), C8", all(k4.values()), "recusa")
    k5 = {"C4": cm["C4"]["ok"], "C5": cm["C5"]["ok"], "B438": cm["B438"]["ok"]}
    crit["K5"] = _crit(k5, "C4, C5, invalidacao_perdida = conflito_esgotado = 0", all(k5.values()),
                       "reportado" if com_falha else "recusa")
    # K6: a falha aconteceu como previsto (DT-011)
    crit["K6"] = _crit(*k6(ex, d, falha), "recusa")
    # K7: R-S3 contra a matriz
    try:
        seed_lido, rep_lido = semente_e_rep(num(r"(Configuracao: .*)", out, tipo=str))
    except ValueError:
        seed_lido = rep_lido = None
    seed_mat = int(lin["seed"])
    k7 = {"seed_lido": seed_lido, "rep_lido": rep_lido, "seed_matriz": seed_mat, "seed_regra": S0 + PASSO_SEMENTE * i}
    crit["K7"] = _crit(k7, "seed = matriz = S0 + 100*i; rep = 1",
                       seed_lido == seed_mat == S0 + PASSO_SEMENTE * i and rep_lido == REP, "recusa")
    # K8: ISPN006010 por no desde a criacao do cluster
    isp = ler_json(d / "ispn006010.json", {}) or {}
    por_no = isp.get("por_no") or {}
    crit["K8"] = _crit(por_no, "<= %d por no" % ISPN006010_MAX,
                       all(isinstance(por_no.get(n), int) and por_no[n] <= ISPN006010_MAX for n in NOS), "parada")
    # K9 / K10: auditor e violacoes
    viol = contar_violacoes(d / "violacoes-rep-001.csv")
    b = linhas_b439(out)
    a = b["auditor_i2"]
    k9v = {"I1": viol["violacoes"]["I1"], "I4": viol["violacoes"]["I4"], "I2_qualquer_classe": viol["i2_qualquer_classe"],
           "auditor_i2": a, "revertido_por_bloqueio": b["revertido_por_bloqueio"],
           "violacoes_csv_presente": viol["arquivo_presente"]}
    k9ok = (viol["arquivo_presente"] and k9v["I1"] == 0 and k9v["I4"] == 0 and k9v["I2_qualquer_classe"] == 0
            and a is not None and all(v == 0 for v in a.values()) and b["revertido_por_bloqueio"] == 0)
    if modo == "sync":
        crit["K9"] = _crit(k9v, "SYNC none: I1 = I4 = 0, nenhum I2, Auditor I2 zerada (DT-010), revertido = 0",
                           k9ok, "reportado" if com_falha else "parada")
    else:
        crit["K9"] = _crit(k9v, "so SYNC (em ASYNC ver K10)", True, "reportado")
    k10v = {"I1": viol["violacoes"]["I1"], "I4": viol["violacoes"]["I4"],
            "I2_por_classe": {k: v for k, v in viol["por_invariante_e_classe"].items() if k.startswith("I2")},
            "sem_marco": a and a["sem_marco"]}
    crit["K10"] = _crit(k10v, "ASYNC: reportado (H-A0)", True, "reportado")

    for k, v in crit.items():
        if not v["ok"] and v["papel"] in ("recusa", "parada"):
            motivos.append("%s (%s): %s" % (k, v["papel"], json.dumps(v["valor"], ensure_ascii=False)[:300]))
    if any(not v["ok"] and v["papel"] == "parada" for v in crit.values()):
        veredito = "PARADA"
    elif any(not v["ok"] and v["papel"] == "recusa" for v in crit.values()):
        veredito = "RECUSADA"
    else:
        veredito = "ACEITA"
    cfg_lida = {"cenario": num(r"Configuracao: Cli\{scenario=(\w+)", out, tipo=str),
                "replicacao": num(r"^Replicacao: replicacao=(\w+)", out, tipo=str),
                "mix": num(r"^Mistura: mix=(\w+)", out, tipo=str),
                "duracao_s": num(r"duracao=(\d+)s", out), "threads": num(r"threads=(\d+)", out)}
    cfg_confere = cfg_lida == {"cenario": cen, "replicacao": modo, "mix": "v3", "duracao_s": DURACAO,
                               "threads": THREADS}
    if not cfg_confere:
        motivos.append("aviso (reportado): configuracao lida difere da matriz: %s" % cfg_lida)
    metricas = {k: cm[k] for k in ("C1", "C2", "C3", "C4", "C5", "B438", "validate_isolados_outros",
                                   "mistura_v3_stdout")}
    metricas["C8"] = c8
    metricas["violacoes"] = viol
    metricas["violacoes_stdout_informativo"] = num(r"Violacoes detectadas: (\d+)", out)
    metricas["linhas_b439"] = b
    metricas["configuracao_lida"] = cfg_lida
    metricas["configuracao_confere"] = cfg_confere
    return {"i": i, "celula": lin["celula"], "fase": lin["fase"], "noite": lin.get("noite"), "cenario": cen,
            "modo": modo, "falha": falha, "seed": seed_mat, "seed_lido": seed_lido, "rep_lido": rep_lido,
            "veredito": veredito, "motivos": motivos, "criterios": crit, "metricas": metricas}


def k6(ex, d, falha):
    """(valor, limite, ok) de K6 (PR §6 com DT-011)."""
    if ex.get("recusa_k6") or ex.get("ancora_utc") is None:
        return ({"recusa_k6": ex.get("recusa_k6") or "sem ancora"}, "ancora 'Warm-up ate' no stdout", False)
    if falha == "none":
        return ("n/a", "n/a (sem falha)", True)
    off = ex.get("inj_inicio_offset_s")
    ok_off = isinstance(off, (int, float)) and INJ_TOL[0] <= off <= INJ_TOL[1]
    log = (Path(d) / "injetor.log").read_text(encoding="utf-8", errors="replace") \
        if (Path(d) / "injetor.log").exists() else ""
    v = {"inj_inicio_offset_s": off, "injetor_exit": ex.get("injetor_exit")}
    lim = "disparo em [%.1f, %.1f] s da ancora; injetor exit 0" % INJ_TOL
    ok = ok_off and ex.get("injetor_exit") == 0
    if falha == "F1":
        stop = re.search(r"\bstop isn2\b", log) is not None
        start = re.search(r"\bstart isn2\b", log) is not None
        volta = (ex.get("cluster_pos_f1") or {}).get("ok") is True
        v.update({"log_stop": stop, "log_start": start, "cluster_de_volta": volta})
        return (v, lim + "; log com stop e start de isn2; cluster de volta a 3 nos HEALTHY",
                ok and stop and start and volta)
    am = {a.get("alvo_s"): a.get("netem") for a in (ex.get("netem_amostras") or [])}
    pos = (ex.get("netem_pos") or {}).get("netem")
    v.update({"netem_amostras": am, "netem_depois": pos})
    return (v, lim + "; netem presente em +70 s e +280 s e ausente depois",
            ok and am.get(70) is True and am.get(280) is True and pos is False)


# ============================================================================= estado

def ler_checagens(saida):
    out = []
    for p in sorted(Path(saida).glob("exec-*/checagem.json")):
        c = ler_json(p)
        if c is not None:
            c["_dir"] = p.parent.name
            out.append(c)
    return out


def avaliar_conjunto(chs):
    """Paradas (a), (b), (c) e (d) do PR §6 sobre um conjunto de checagens."""
    rec = [c for c in chs if c["veredito"] == "RECUSADA"]
    por_cel = Counter(c["celula"] for c in rec)
    paradas = {}
    paradas["a"] = {"valor": {k: v for k, v in por_cel.items() if v > MAX_RECUSAS_CELULA},
                    "limite": "no maximo %d recusa por celula" % MAX_RECUSAS_CELULA,
                    "ok": all(v <= MAX_RECUSAS_CELULA for v in por_cel.values())}
    paradas["b"] = {"valor": len(rec), "limite": "no maximo %d recusas" % MAX_RECUSAS, "ok": len(rec) <= MAX_RECUSAS}
    par = [c["i"] for c in chs if c["veredito"] == "PARADA"]
    paradas["c"] = {"valor": par, "limite": "nenhuma PARADA (K8, K9)", "ok": not par}
    reprov = [c["i"] for c in chs if not ((c.get("metricas") or {}).get("C3") or {}).get("ok", False)
              and c["veredito"] != "PARADA" and (c.get("metricas") or {}).get("C3", {}).get("n") is not None]
    aceitas = [c for c in chs if c["veredito"] == "ACEITA"]
    sk = sum(c["metricas"]["C3"]["k"] for c in aceitas)
    sn = sum(c["metricas"]["C3"]["n"] for c in aceitas)
    sn_csv = sum(c["metricas"]["C3"]["n_csv"] for c in aceitas)
    agg = c3_ic(sk, sn, sn_csv, THREADS * max(1, len(aceitas))) if aceitas else None
    agg_ok = agg is None or agg["sanidade"] is not None or agg["ok_ic"]
    paradas["d"] = {"valor": {"reprovacoes_c3": reprov, "agregado": agg and {k: agg[k] for k in (
        "k", "n", "frac_pct", "meia_largura_pp", "ok_ic", "sanidade")}},
                    "limite": "no maximo %d reprovacao de C3; C3 agregado das aceitas no IC" % MAX_REPROV_C3,
                    "ok": len(reprov) <= MAX_REPROV_C3 and agg_ok}
    return paradas, rec, por_cel, agg


def rs3(chs, matriz):
    """R-S3 (PR §4) na matriz inteira: nenhum par (seed, rep) repetido entre execucoes; nenhuma semente
    repetida na matriz (e nas reposicoes)."""
    por_par = defaultdict(list)
    for c in chs:
        if c.get("seed_lido") is not None:
            por_par["%s,%s" % (c["seed_lido"], c.get("rep_lido"))].append(c["i"])
    rep = {k: v for k, v in por_par.items() if len(v) > 1}
    sem = Counter(int(x["seed"]) for x in matriz.values())
    rep_mat = sorted(s for s, n in sem.items() if n > 1)
    return {"valor": {"pares_repetidos": rep, "sementes_repetidas_na_matriz": rep_mat},
            "limite": "pares (seed, rep) unicos", "ok": not rep and not rep_mat}


def estado(saida, matriz):
    chs = ler_checagens(saida)
    camp = [c for c in chs if c.get("fase") in FASES_CAMPANHA]
    pil = [c for c in chs if c.get("fase") == "piloto"]
    paradas, rec, por_cel, agg = avaliar_conjunto(camp)
    paradas["R-S3"] = rs3(chs, matriz)
    parada = not all(v["ok"] for v in paradas.values())
    motivos = ["parada (%s): %s" % (k, json.dumps(v["valor"], ensure_ascii=False)[:300])
               for k, v in paradas.items() if not v["ok"]]
    pil_paradas = avaliar_conjunto(pil)[0] if pil else None
    return {"execucoes": len(chs), "execucoes_campanha": len(camp), "aceitas_campanha": sum(
        1 for c in camp if c["veredito"] == "ACEITA"), "total_recusas": len(rec),
        "recusas_por_celula": dict(por_cel), "c3_agregado": agg, "paradas": paradas, "parada": parada,
        "motivos": motivos,
        "piloto_informativo": {"execucoes": len(pil), "paradas": pil_paradas,
                               "nota": "o piloto nunca conta para a campanha (PR §8); avaliado a parte"}}


def reposicoes_pendentes(saida):
    """Celulas com recusa isolada na campanha ainda nao reposta (no maximo uma por celula), na ordem do i."""
    chs = ler_checagens(saida)
    repostas = {c["celula"] for c in chs if c.get("fase") == "reposicao"}
    ja_feitas = set(repostas)
    exec_rep = {p.name.split("-", 2)[2] for p in Path(saida).glob("exec-*") if p.is_dir()
                and int(p.name.split("-")[1]) >= 120 and int(p.name.split("-")[1]) < 1000}
    ja_feitas |= exec_rep
    rec = sorted((c["i"], c["celula"]) for c in chs if c.get("fase") == "campanha" and c["veredito"] == "RECUSADA")
    out = []
    vistos = set()
    for i, cel in rec:
        if cel in ja_feitas or cel in vistos:
            continue
        vistos.add(cel)
        out.append((cel, i))
    return out


def matrizes(saida, tsv):
    caminhos = [tsv]
    extra = Path(saida) / "reposicoes" / "reposicoes.tsv"
    if extra.exists():
        caminhos.append(extra)
    return ler_matriz(caminhos)


# ============================================================================= CLI

def main(argv):
    ap = argparse.ArgumentParser(prog="rebaseline.py")
    sub = ap.add_subparsers(dest="cmd")
    c = sub.add_parser("checar")
    c.add_argument("--exec", dest="exec_dir", required=True)
    c.add_argument("--matriz", required=True)
    e = sub.add_parser("estado")
    e.add_argument("--saida", required=True)
    e.add_argument("--matriz", required=True)
    e.add_argument("--listar-reposicoes", action="store_true")
    t = sub.add_parser("tabelas")
    t.add_argument("--saida", required=True)
    t.add_argument("--matriz", required=True)
    t.add_argument("--destino", required=True)
    p = sub.add_parser("piloto")
    p.add_argument("--saida", required=True)
    p.add_argument("--matriz", required=True)
    try:
        a = ap.parse_args(argv)
    except SystemExit as ex:
        return 2 if ex.code else 0
    try:
        if a.cmd == "checar":
            if not Path(a.exec_dir).is_dir() or not Path(a.matriz).is_file():
                raise ErroUso("--exec ou --matriz inexistente")
            r = checar_execucao(a.exec_dir, ler_matriz([a.matriz]))
            gravar_json(Path(a.exec_dir) / "checagem.json", r)
            print("checar i=%d %s: %s" % (r["i"], r["celula"], r["veredito"]))
            for m in r["motivos"]:
                print("  " + m)
            return {"ACEITA": 0, "RECUSADA": 10, "PARADA": 20}[r["veredito"]]
        if a.cmd == "estado":
            if not Path(a.saida).is_dir() or not Path(a.matriz).is_file():
                raise ErroUso("--saida ou --matriz inexistente")
            r = estado(a.saida, matrizes(a.saida, a.matriz))
            gravar_json(Path(a.saida) / "estado.json", r)
            if a.listar_reposicoes:
                for cel, i in reposicoes_pendentes(a.saida):
                    print("%s\t%d" % (cel, i))
            else:
                print("estado: %s (%d recusas, %d execucoes de campanha)" % (
                    "PARADA" if r["parada"] else "segue", r["total_recusas"], r["execucoes_campanha"]))
                for m in r["motivos"]:
                    print("  " + m)
            return 20 if r["parada"] else 0
        if a.cmd in ("tabelas", "piloto"):
            raise ErroUso("subcomando %s ainda nao implementado" % a.cmd)
        ap.print_usage(sys.stderr)
        return 2
    except ErroUso as ex:
        print("erro: %s" % ex, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
