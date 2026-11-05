"""Comparacao pareada A (shadow) x B (active) por blocos (B-39.3; plano de 22/10, 8.3 e 8.4).

Uso (da raiz do repositorio; numpy e scipy, roda no host):
  python analysis/comparar_bracos.py --manifesto M.tsv --semente-boot S --primario DESFECHO
      --saida comparacao.json [--fases durante,recuperacao] [--rep 1] [--w-recovery-s 60]
      [--n-boot 10000] [--n-hier 2000] [--delta 0.10] [--marcadores-nome marcadores.txt]

Manifesto TSV com cabecalho exato `bloco<TAB>celula<TAB>braco<TAB>execucao`, braco em {A, B}, um par
A/B por (celula, bloco); `execucao` e um diretorio (relativo ao manifesto, se nao for absoluto) com:
  rep-NNN.csv + meta-csv-rep-NNN.json (contrato 2)      desfechos de eventos
  s3-acoes-rep-NNN.csv (contrato S3 v2, 10 colunas)     desfechos S3 (8 colunas: exit 2)
  marcadores.txt, convergencia-resumo-rep-NNN.json       fases (ausentes: tudo "antes")
  energia-rep-NNN.json (energia_proxy.py)                cpu_por_1000_uteis (opcional)
  violacoes-rep-NNN.csv (InvariantAuditor.dumpCsv)       violacoes (opcional)

Por celula e desfecho: d_b = ln(y_B / y_A); estimativa exp(media d_b); IC t (primario, decisao D3 do
Gestor), IC bootstrap percentil sobre blocos e IC hierarquico (sensibilidade); permutacao exata por troca
de sinal; Holm nos secundarios (o primario e a vazao util, que tem teste de nao-inferioridade, ficam
fora); violacoes com regra de tres. Grava o JSON e uma tabela Markdown (mesmo nome, .md); nunca
sobrescreve.

Exit: 0 ok, 2 uso/contrato/saida existente.
"""
import argparse
import calendar
import csv
import hashlib
import json
import math
import os
import re
import sys
import time
from fractions import Fraction
from pathlib import Path

import numpy as np
from scipy import stats

sys.path.insert(0, str(Path(__file__).resolve().parent))   # fases, referencia_p99, energia_proxy

import energia_proxy  # noqa: E402
import fases as _fases  # noqa: E402
import referencia_p99  # noqa: E402
from referencia_p99 import percentil  # noqa: E402  (posto mais proximo, copia de rebaseline)

ESQUEMA = "comparar-bracos/1"
CABECALHO_MANIFESTO = ("bloco", "celula", "braco", "execucao")
BRACOS = ("A", "B")
FASES_PADRAO = ("durante", "recuperacao")
N_BOOT_PADRAO = 10000
N_HIER_PADRAO = 2000
DELTA_PADRAO = 0.10
N_MIN_P99 = 1000
N_MAX_PERMUTACAO = 16
TOL_PERMUTACAO = 1e-12
DESFECHO_NI = "vazao_util"
DESFECHO_CPU = "cpu_por_1000_uteis"
ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")
INVARIANTES_VIOL = ("I1", "I2_REAL", "I4")


class ErroUso(Exception):
    """Uso invalido, manifesto incompleto, contrato violado ou saida existente: exit 2."""


ERROS_USO = (ErroUso, energia_proxy.ErroUso, referencia_p99.ErroUso)


# ============================================================================= manifesto

def _chave_bloco(b):
    return (0, int(b), b) if b.isdigit() else (1, 0, b)


def ler_manifesto(caminho):
    """{celula: {bloco: {"A": Path, "B": Path}}}; ErroUso com bloco incompleto, duplicado ou repetido."""
    p = Path(caminho)
    if not p.is_file():
        raise ErroUso("manifesto ausente: %s" % p)
    linhas = [x.rstrip("\r") for x in p.read_text(encoding="utf-8").split("\n")]
    linhas = [x for x in linhas if x.strip()]
    if not linhas or tuple(linhas[0].split("\t")) != CABECALHO_MANIFESTO:
        raise ErroUso("cabecalho do manifesto deve ser exatamente %r" % "\t".join(CABECALHO_MANIFESTO))
    pares, vistas = {}, set()
    for i, linha in enumerate(linhas[1:], start=2):
        campos = linha.split("\t")
        if len(campos) != 4 or any(not c.strip() for c in campos):
            raise ErroUso("linha %d do manifesto com campos invalidos: %r" % (i, linha))
        bloco, celula, braco, execucao = (c.strip() for c in campos)
        if braco not in BRACOS:
            raise ErroUso("braco invalido na linha %d: %r (use A ou B)" % (i, braco))
        ex = Path(execucao)
        if not ex.is_absolute():
            ex = p.parent / ex
        ex_norm = os.path.normcase(str(ex.resolve()))
        if ex_norm in vistas:
            raise ErroUso("execucao repetida no manifesto (linha %d): %s" % (i, execucao))
        vistas.add(ex_norm)
        par = pares.setdefault((celula, bloco), {})
        if braco in par:
            raise ErroUso("bloco %s da celula %s com braco %s duplicado" % (bloco, celula, braco))
        par[braco] = ex
    out = {}
    for (celula, bloco), par in pares.items():
        if set(par) != set(BRACOS):
            raise ErroUso("bloco %s da celula %s incompleto (bracos: %s)" % (bloco, celula, ",".join(sorted(par))))
        out.setdefault(celula, {})[bloco] = par
    return {c: dict(sorted(bs.items(), key=lambda kv: _chave_bloco(kv[0]))) for c, bs in sorted(out.items())}


# ============================================================================= estatistica

def d_blocos(y_a, y_b):
    """Lista de ln(y_B / y_A) por bloco (y > 0)."""
    if len(y_a) != len(y_b):
        raise ValueError("y_a e y_b com tamanhos diferentes")
    out = []
    for a, b in zip(y_a, y_b):
        if not (a > 0 and b > 0):
            raise ValueError("y deve ser > 0 para o log da razao: %r, %r" % (a, b))
        out.append(math.log(b / a))
    return out


def permutacao_exata(d):
    """p bilateral exato por troca de sinal sobre d_b, com a identidade incluida.

    p = #{arranjos com |media| >= |media observada| - 1e-12} / 2^n. n > 16: ErroUso (sem Monte Carlo).
    """
    d = np.asarray(d, dtype=float)
    n = d.size
    if n == 0:
        return None
    if n > N_MAX_PERMUTACAO:
        raise ErroUso("permutacao exata limitada a n <= %d blocos (n = %d)" % (N_MAX_PERMUTACAO, n))
    sinais = ((np.arange(2 ** n)[:, None] >> np.arange(n)) & 1) * 2 - 1
    medias = sinais @ d / n
    obs = abs(d.mean())
    return int(np.count_nonzero(np.abs(medias) >= obs - TOL_PERMUTACAO)) / 2 ** n


def holm(ps):
    """p ajustados por Holm (step-down), na ordem de entrada."""
    m = len(ps)
    ordem = sorted(range(m), key=lambda i: ps[i])
    out = [0.0] * m
    corrente = 0.0
    for pos, i in enumerate(ordem):
        corrente = max(corrente, min(1.0, (m - pos) * ps[i]))
        out[i] = corrente
    return out


def bonferroni(ps):
    m = len(ps)
    return [min(1.0, m * p) for p in ps]


def hochberg(ps):
    """p ajustados por Hochberg (step-up), na ordem de entrada."""
    m = len(ps)
    ordem = sorted(range(m), key=lambda i: ps[i])
    out = [0.0] * m
    corrente = 1.0
    for pos in range(m - 1, -1, -1):
        i = ordem[pos]
        corrente = min(corrente, (m - pos) * ps[i])
        out[i] = min(1.0, corrente)
    return out


def regra_de_tres(n_acoes):
    """(limite superior unilateral 95 % = 1 - 0,05^(1/N), rotulo "≈ 3/N") para zero eventos em N."""
    if n_acoes <= 0:
        return None, "sem acoes"
    return 1.0 - 0.05 ** (1.0 / n_acoes), "≈ 3/N = %.6g (N = %d)" % (3.0 / n_acoes, n_acoes)


def ic_t(d, confianca=0.95):
    """IC t sobre d_b (media ± t_{0,975; n-1} s/sqrt(n)), em escala de razao (exp)."""
    d = np.asarray(d, dtype=float)
    n = d.size
    if n < 2:
        return None
    m, s = d.mean(), d.std(ddof=1)
    meia = stats.t.ppf(0.5 + confianca / 2, n - 1) * s / math.sqrt(n)
    return [math.exp(m - meia), math.exp(m + meia)]


def _rng(semente, *indices):
    return np.random.default_rng(np.random.SeedSequence([int(semente)] + [int(i) for i in indices]))


def ic_boot_blocos(d, semente, indice, n_boot=N_BOOT_PADRAO):
    """IC bootstrap percentil sobre BLOCOS (reamostra os d_b), em escala de razao.

    Gerador: numpy default_rng(SeedSequence([semente, indice_do_desfecho])).
    """
    d = np.asarray(d, dtype=float)
    n = d.size
    if n < 2:
        return None
    rng = _rng(semente, indice)
    medias = d[rng.integers(0, n, size=(n_boot, n))].mean(axis=1)
    lo, hi = np.percentile(medias, [2.5, 97.5])
    return [math.exp(lo), math.exp(hi)]


def _k_posto(n, p):
    fp = Fraction(str(p))
    return -((-fp.numerator * n) // fp.denominator)


def quantil_reamostrado(ordenados, p, rng, indices):
    """Percentil (posto mais proximo) de uma reamostra com reposicao de cada execucao indicada.

    ordenados: lista de arrays ordenados; indices: array inteiro (qualquer forma) de execucoes. Cada
    entrada recebe o p-percentil de uma reamostra de tamanho n da execucao, sorteado sem materializar a
    reamostra: o k-esimo menor de n indices uniformes ceil(n*V_i) e ceil(n*V_(k)), com V_(k) ~ Beta(k,
    n - k + 1) (a funcao ceil(n*.) e monotona), entao o valor e ordenado[ceil(n*V_(k)) - 1]. A
    equivalencia com a reamostragem ingenua e conferida em test_comparar_bracos (H0).
    """
    tam = np.array([len(o) for o in ordenados])
    ks = np.array([_k_posto(int(n), p) for n in tam])
    desloc = np.concatenate([[0], np.cumsum(tam)[:-1]])
    todos = np.concatenate(ordenados)
    n_sel, k_sel = tam[indices], ks[indices]
    v = rng.beta(k_sel, n_sel - k_sel + 1)
    pos = np.clip(np.ceil(v * n_sel).astype(np.int64), 1, n_sel)
    return todos[desloc[indices] + pos - 1]


def ic_hierarquico(pares, semente, indice, n_hier=N_HIER_PADRAO, p=0.99):
    """IC hierarquico: reamostra blocos e, dentro de cada execucao, os eventos (recalcula o p99).

    pares = [(eventos_A_ordenados, eventos_B_ordenados)] por bloco. Gerador:
    SeedSequence([semente, indice_do_desfecho, 1]) (fluxo separado do bootstrap de blocos).
    """
    n = len(pares)
    if n < 2:
        return None
    rng = _rng(semente, indice, 1)
    a = [np.asarray(x, dtype=float) for x, _ in pares]
    b = [np.asarray(y, dtype=float) for _, y in pares]
    blocos = rng.integers(0, n, size=(n_hier, n))
    qa = quantil_reamostrado(a, p, rng, blocos)
    qb = quantil_reamostrado(b, p, rng, blocos)
    medias = np.log(qb / qa).mean(axis=1)
    lo, hi = np.percentile(medias, [2.5, 97.5])
    return [math.exp(lo), math.exp(hi)]


def ic_eventos_agrupados(pares, semente, n_boot=N_BOOT_PADRAO, p=0.99):
    """(estimativa, IC) da razao p99(B)/p99(A) reamostrando eventos de todas as execucoes juntas.

    E o procedimento do bootstrap_ic.py (eventos como unidade). Ignora a variabilidade entre execucoes
    (pseudorreplicacao, ameaca A8); existe so para documentar a sub-cobertura (teste E1). Nao entra no
    JSON da comparacao.
    """
    a = np.sort(np.concatenate([np.asarray(x, dtype=float) for x, _ in pares]))
    b = np.sort(np.concatenate([np.asarray(y, dtype=float) for _, y in pares]))
    rng = _rng(semente, 2 ** 31)
    idx = np.zeros(n_boot, dtype=np.int64)
    qa = quantil_reamostrado([a], p, rng, idx)
    qb = quantil_reamostrado([b], p, rng, idx)
    lo, hi = np.percentile(qb / qa, [2.5, 97.5])
    return float(percentil(b.tolist(), p) / percentil(a.tolist(), p)), [float(lo), float(hi)]


def nao_inferioridade(analise, delta=DELTA_PADRAO):
    """"nao-inferior" se o limite INFERIOR do IC t da razao B/A >= 1 - delta; senao "inconclusivo"."""
    ic = analise.get("ic_t") if analise else None
    if not ic:
        return "inconclusivo"
    return "nao-inferior" if ic[0] >= 1.0 - delta else "inconclusivo"


def analisar_desfecho(y_a, y_b, semente, indice, n_boot=N_BOOT_PADRAO, pares=None, n_hier=N_HIER_PADRAO):
    """Estimativa, IC t, IC bootstrap de blocos, IC hierarquico (com pares) e p de permutacao."""
    d = d_blocos(y_a, y_b)
    n = len(d)
    return {"n_blocos": n,
            "estimativa": math.exp(sum(d) / n) if n else None,
            "ic_t": ic_t(d),
            "ic_boot_blocos": ic_boot_blocos(d, semente, indice, n_boot),
            "ic_hierarquico": ic_hierarquico(pares, semente, indice, n_hier) if pares else None,
            "p_permutacao": permutacao_exata(d),
            "d_b": d}


# ============================================================================= leitura de uma execucao

_RX_INSTANTE = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$")


def instante_ns(txt):
    """Epoch ns de um java.time.Instant.toString() (UTC, fracao de 0 a 9 digitos)."""
    m = _RX_INSTANTE.match(txt.strip())
    if not m:
        raise ErroUso("instante invalido no CSV de violacoes: %r" % txt)
    seg = calendar.timegm(time.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S"))
    return seg * 1_000_000_000 + int((m.group(2) or "").ljust(9, "0"))


def contar_violacoes(caminho, filtro=None):
    """{"I1","I2_REAL","I4"} do violacoes-rep-NNN.csv, com a regra da DT-009 (rebaseline.py:256-276).

    Formato: InvariantAuditor.dumpCsv (:251-278), cabecalho instante,invariante,chave,mensagem[,fase]
    [,classificacao]. I1 e I4 com rotulo exato; I2 so com rotulo exato "I2" e classificacao "REAL".
    I2_JANELA_CASCATA, I2_CASCATA_INCOMPLETA, I2 de outra classe e os contadores *_concorrentes (que so
    existem no stdout, SessionOps.java:246-260) nao contam (DT-001/DT-002). filtro(t_ns) -> bool
    restringe pelas fases.
    """
    out = {k: 0 for k in INVARIANTES_VIOL}
    p = Path(caminho)
    if not p.exists():
        return None
    with open(p, newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            inv = (x.get("invariante") or "").strip()
            cl = (x.get("classificacao") or "").strip()
            if inv == "I2":
                chave = "I2_REAL" if cl == "REAL" else None
            elif inv in ("I1", "I4"):
                chave = inv
            else:
                chave = None
            if chave is None:
                continue
            if filtro is not None and not filtro(instante_ns(x.get("instante") or "")):
                continue
            out[chave] += 1
    return out


def _p99_desfecho(valores):
    n = len(valores)
    if n < N_MIN_P99:
        return {"y": None, "n": n, "motivo": "n<%d" % N_MIN_P99, "eventos": None}
    arr = np.sort(np.asarray(valores, dtype=float))
    return {"y": float(percentil(valores, 0.99)), "n": n, "motivo": None, "eventos": arr}


def _taxa(num, den, escala=1.0):
    if den <= 0:
        return {"y": None, "n": den, "motivo": "denominador zero", "eventos": None}
    return {"y": escala * num / den, "n": den, "motivo": None, "eventos": None}


def conferir_parametros_energia(en, en_path, w_recovery_ns, tem_marcadores, args_fase):
    """B39-R1 (G4 de 28/10): ErroUso se a energia foi calculada com outras fronteiras de fase.

    Confere (1) parametros.w_recovery_s igual ao W da comparacao; (2) marcadores presentes nos dois
    lados ou ausentes nos dois; (3) as fases gravadas no JSON iguais as que a comparacao calcula sobre a
    janela do proprio JSON (pega marcadores de outro arquivo e convergencia nao passada ao energia_proxy).
    """
    par, jan, fs = en.get("parametros"), en.get("janela"), en.get("fases")
    if not isinstance(par, dict) or not isinstance(jan, dict) or not isinstance(fs, list):
        raise ErroUso("energia sem parametros, janela ou fases (energia-proxy/1 incompleto): %s" % en_path)
    w_en = par.get("w_recovery_s")
    try:
        w_en_ns = int(round(float(w_en) * 1e9))
    except (TypeError, ValueError):
        raise ErroUso("w_recovery_s invalido na energia %s: %r" % (en_path, w_en)) from None
    if w_en_ns != w_recovery_ns:
        raise ErroUso("B39-R1: w_recovery_s da energia (%r s) difere do da comparacao (%r s): %s"
                      % (w_en, w_recovery_ns / 1e9, en_path))
    if (par.get("marcadores") is not None) != tem_marcadores:
        raise ErroUso("B39-R1: marcadores divergem (energia: %s; comparacao: %s): %s"
                      % ("com" if par.get("marcadores") is not None else "sem",
                         "com" if tem_marcadores else "sem", en_path))
    try:
        esperado = [tuple(x) for x in _fases.intervalos_de_fase(int(jan["t0_ns"]), int(jan["t1_ns"]), *args_fase)]
        obtido = [(f["rotulo"], int(f["t_ini_ns"]), int(f["t_fim_ns"])) for f in fs]
    except (KeyError, TypeError, ValueError):
        raise ErroUso("janela ou fases ilegiveis na energia %s" % en_path) from None
    if esperado != obtido:
        raise ErroUso("B39-R1: fases da energia divergem das da comparacao (W, marcadores ou convergencia): %s"
                      % en_path)


def desfechos_execucao(execucao, rep, fases_sel, w_recovery_ns, marcadores_nome="marcadores.txt"):
    """({desfecho: {"y","n","motivo","eventos"}}, info) de uma execucao, nas fases pedidas."""
    d = Path(execucao)
    if not d.is_dir():
        raise ErroUso("execucao ausente: %s" % d)
    nnn = "%03d" % int(rep)
    try:
        mk = _fases.ler_marcadores(d / marcadores_nome)
    except ValueError as e:
        raise ErroUso("%s: %s" % (d / marcadores_nome, e)) from None
    conv, div = _fases.ler_convergencia(d / ("convergencia-resumo-rep-%s.json" % nnn))
    ev_path, s3_path = d / ("rep-%s.csv" % nnn), d / ("s3-acoes-rep-%s.csv" % nnn)
    eventos = energia_proxy.ler_eventos_inicio(ev_path) if ev_path.exists() else []
    acoes = energia_proxy.ler_acoes_s3(s3_path) if s3_path.exists() else None
    inicios = [e[0] for e in eventos] + [a[0] for a in (acoes or [])]
    fins = [e[1] for e in eventos] + [a[1] for a in (acoes or [])]
    if not inicios:
        raise ErroUso("execucao sem eventos nem acoes S3: %s" % d)
    t0, t1 = min(inicios), max(fins) + 1
    args = (mk["inicio"], mk["split"], mk["fim"], w_recovery_ns, conv, div)
    sel = _fases.expandir_fases(fases_sel)
    ivs = _fases.intervalos_de_fase(t0, t1, *args)
    dur_sel = sum(b - a for r, a, b in ivs if r in sel) / 1e9

    def dentro(t):
        return _fases.fase_de(t, *args) in sel

    out = {}
    ev_sel = [e for e in eventos if dentro(e[0])]
    if eventos:
        por_op = {}
        for a, b, op, _ in ev_sel:
            por_op.setdefault(op, []).append(b - a)
        for op in sorted(por_op):
            out["evento_p99:" + op] = _p99_desfecho(por_op[op])
        out["taxa_erro"] = _taxa(sum(1 for e in ev_sel if e[3] in ERROS), len(ev_sel))
    n_acoes = None
    if acoes is not None:
        ac_sel = [a for a in acoes if dentro(a[0])]
        n_acoes = len(ac_sel)
        out["acao_s3_p99_com_espera"] = _p99_desfecho([tf - t for t, tf, _, _, _ in ac_sel])
        out["acao_s3_p99_sem_espera"] = _p99_desfecho([tf - t - w for t, tf, w, _, _ in ac_sel])
        out["falhas_percebidas_por_1000"] = _taxa(sum(1 for a in ac_sel if a[4]), n_acoes, 1000.0)
        uteis = sum(1 for a in ac_sel if not a[4])
        out[DESFECHO_NI] = ({"y": uteis / dur_sel, "n": n_acoes, "motivo": None, "eventos": None}
                            if dur_sel > 0 else {"y": None, "n": 0, "motivo": "fase sem duracao", "eventos": None})
    excluida = None
    en_path = d / ("energia-rep-%s.json" % nnn)
    if en_path.exists():
        try:
            en = json.loads(en_path.read_text(encoding="utf-8"))
        except ValueError:
            raise ErroUso("energia ilegivel: %s" % en_path) from None
        if en.get("esquema") != energia_proxy.ESQUEMA:
            raise ErroUso("esquema de energia inesperado em %s: %r" % (en_path, en.get("esquema")))
        conferir_parametros_energia(en, en_path, w_recovery_ns, (d / marcadores_nome).exists(), args)
        excluida = en.get("steal", {}).get("excluida")
        rot = [f["rotulo"] for f in en.get("fases", []) if f["rotulo"] in sel]
        vm = en.get("agregados", {}).get("vm", {})
        cpus = [vm.get(r, {}).get("cpu_s") for r in rot]
        n_uteis = sum(en.get("uteis", {}).get(r, 0) for r in rot)
        if not rot:
            out[DESFECHO_CPU] = {"y": None, "n": 0, "motivo": "fases ausentes na energia", "eventos": None}
        elif any(c is None for c in cpus):
            out[DESFECHO_CPU] = {"y": None, "n": n_uteis, "motivo": "cpu sem valor (lacuna/fora)", "eventos": None}
        else:
            out[DESFECHO_CPU] = _taxa(sum(cpus), n_uteis, 1000.0)
    viol = contar_violacoes(d / ("violacoes-rep-%s.csv" % nnn), dentro)
    info = {"n_eventos": len(ev_sel), "n_acoes": n_acoes, "steal_excluida": excluida,
            "energia_presente": en_path.exists(), "violacoes": viol,
            "n_violacoes_base": n_acoes if n_acoes is not None else len(ev_sel)}
    return out, info


# ============================================================================= comparacao

def _f(x):
    if x is None:
        return None
    if isinstance(x, (list, tuple)):
        return [_f(v) for v in x]
    if isinstance(x, dict):
        return {k: _f(v) for k, v in x.items()}
    if isinstance(x, (np.floating, float)):
        return float(x)
    if isinstance(x, (np.integer,)):
        return int(x)
    return x


def _violacoes_celula(blocos, infos, semente, indice_base, n_boot):
    out = {}
    tot = {br: {k: 0 for k in INVARIANTES_VIOL} for br in BRACOS}
    base = {br: 0 for br in BRACOS}
    por_bloco = []
    for b in blocos:
        ia, ib = infos[b]["A"], infos[b]["B"]
        if ia["violacoes"] is None or ib["violacoes"] is None:
            continue
        por_bloco.append((ia, ib))
        for br, inf in (("A", ia), ("B", ib)):
            base[br] += inf["n_violacoes_base"]
            for k in INVARIANTES_VIOL:
                tot[br][k] += inf["violacoes"][k]
    if not por_bloco:
        return {"presente": False}
    for j, k in enumerate(INVARIANTES_VIOL):
        r = {"contagem": {br: tot[br][k] for br in BRACOS}, "n_base": dict(base)}
        if tot["A"][k] == 0 or tot["B"][k] == 0:
            r["regra_de_tres"] = {}
            for br in BRACOS:
                if tot[br][k] == 0:
                    lim, rot = regra_de_tres(base[br])
                    r["regra_de_tres"][br] = {"limite_superior_95": lim, "rotulo": rot}
        else:
            ca = np.array([ia["violacoes"][k] for ia, _ in por_bloco], dtype=float)
            cb = np.array([ib["violacoes"][k] for _, ib in por_bloco], dtype=float)
            na = np.array([ia["n_violacoes_base"] for ia, _ in por_bloco], dtype=float)
            nb = np.array([ib["n_violacoes_base"] for _, ib in por_bloco], dtype=float)
            est = (cb.sum() / nb.sum()) / (ca.sum() / na.sum())
            rng = _rng(semente, indice_base + j)
            idx = rng.integers(0, len(por_bloco), size=(n_boot, len(por_bloco)))
            sa, sb = ca[idx].sum(axis=1), cb[idx].sum(axis=1)
            ok = (sa > 0) & (nb[idx].sum(axis=1) > 0)
            razoes = (sb[ok] / nb[idx].sum(axis=1)[ok]) / (sa[ok] / na[idx].sum(axis=1)[ok])
            ic = np.percentile(razoes, [2.5, 97.5]).tolist() if ok.any() else None
            r["razao_de_taxas"] = {"estimativa": est, "ic_boot_blocos": ic,
                                   "reamostras_indefinidas": int((~ok).sum())}
        out[k] = r
    out["presente"] = True
    out["blocos_com_arquivo"] = len(por_bloco)
    return out


def comparar(manifesto, semente, primario, fases=FASES_PADRAO, rep=1, w_recovery_s=60,
             n_boot=N_BOOT_PADRAO, n_hier=N_HIER_PADRAO, delta=DELTA_PADRAO,
             marcadores_nome="marcadores.txt"):
    """Dicionario do esquema comparar-bracos/1."""
    man = ler_manifesto(manifesto)
    fases_sel = list(fases)
    try:
        _fases.expandir_fases(fases_sel)
    except ValueError as e:
        raise ErroUso(str(e)) from None
    w_ns = int(round(float(w_recovery_s) * 1e9))
    celulas = {}
    for celula, blocos in man.items():
        res, infos = {}, {}
        for b, par in blocos.items():
            res[b], infos[b] = {}, {}
            for br in BRACOS:
                res[b][br], infos[b][br] = desfechos_execucao(par[br], rep, fases_sel, w_ns, marcadores_nome)
        nomes = sorted({n for b in blocos for br in BRACOS for n in res[b][br]})
        desf = {}
        for indice, nome in enumerate(nomes):
            y_a, y_b, pares, excl, ns, usados = [], [], [], [], {}, []
            for b in blocos:
                ra, rb = res[b]["A"].get(nome), res[b]["B"].get(nome)
                ns[b] = {"A": None if ra is None else ra["n"], "B": None if rb is None else rb["n"]}
                motivo = None
                if nome == DESFECHO_CPU:
                    st = (infos[b]["A"]["steal_excluida"], infos[b]["B"]["steal_excluida"])
                    if True in st:
                        motivo = "steal>5%"
                    elif None in st:
                        motivo = "steal-indisponivel"
                if motivo is None:
                    for br, r in (("A", ra), ("B", rb)):
                        if r is None:
                            motivo = "%s: desfecho ausente" % br
                            break
                        if r["y"] is None:
                            motivo = "%s: %s" % (br, r["motivo"])
                            break
                        if r["y"] <= 0:
                            motivo = "%s: valor zero (log indefinido)" % br
                            break
                if motivo:
                    excl.append({"bloco": b, "motivo": motivo})
                    continue
                y_a.append(ra["y"])
                y_b.append(rb["y"])
                usados.append(b)
                if ra["eventos"] is not None and rb["eventos"] is not None:
                    pares.append((ra["eventos"], rb["eventos"]))
            pares = pares if len(pares) == len(usados) and pares else None
            if y_a:
                an = analisar_desfecho(y_a, y_b, semente, indice, n_boot, pares, n_hier)
            else:
                an = {"n_blocos": 0, "estimativa": None, "ic_t": None, "ic_boot_blocos": None,
                      "ic_hierarquico": None, "p_permutacao": None, "d_b": []}
            familia = ("primario" if nome == primario else "nao-inferioridade" if nome == DESFECHO_NI
                       else "secundario")
            ent = {"familia": familia, "indice_semente": indice, "n_blocos": an["n_blocos"],
                   "estimativa": an["estimativa"], "ic_t": an["ic_t"], "ic_boot_blocos": an["ic_boot_blocos"],
                   "ic_hierarquico": an["ic_hierarquico"], "p_permutacao": an["p_permutacao"], "p_holm": None,
                   "d_b": dict(zip(usados, an["d_b"])), "n_por_bloco": ns, "blocos_excluidos": excl}
            if nome == DESFECHO_NI:
                ent["nao_inferioridade"] = {"delta": delta, "limite": 1.0 - delta,
                                            "limite_inferior_ic_t": an["ic_t"][0] if an["ic_t"] else None,
                                            "resultado": nao_inferioridade(an, delta)}
            desf[nome] = ent
        sec = [n for n in nomes if desf[n]["familia"] == "secundario" and desf[n]["p_permutacao"] is not None]
        for n, ph in zip(sec, holm([desf[n]["p_permutacao"] for n in sec])):
            desf[n]["p_holm"] = ph
        excl_steal = sorted({b for b in blocos if True in (infos[b]["A"]["steal_excluida"],
                                                            infos[b]["B"]["steal_excluida"])},
                            key=_chave_bloco)
        celulas[celula] = {"blocos": list(blocos), "execucoes": {b: {br: _rel(blocos[b][br]) for br in BRACOS}
                                                                for b in blocos},
                           "primario": primario, "primario_presente": primario in desf,
                           "n_secundarios_holm": len(sec),
                           "blocos_excluidos_por_steal": excl_steal,
                           "desfechos": desf,
                           "violacoes": _violacoes_celula(list(blocos), infos, semente, len(nomes), n_boot)}
    script = Path(__file__).resolve()
    return _f({"esquema": ESQUEMA,
               "parametros": {"semente_boot": int(semente), "n_boot": n_boot, "n_hier": n_hier,
                              "fases": fases_sel, "primario": primario, "delta": delta, "rep": rep,
                              "w_recovery_s": float(w_recovery_s), "marcadores_nome": marcadores_nome,
                              "ic_primario": "t (decisao D3)", "p99": "posto-mais-proximo, n >= %d" % N_MIN_P99,
                              "permutacao": "exata, troca de sinal, bilateral, identidade incluida"},
               "manifesto": {"arquivo": _rel(manifesto), "sha256": hashlib.sha256(Path(manifesto).read_bytes()).hexdigest()},
               "celulas": celulas,
               "gerado_por": {"script": "analysis/comparar_bracos.py",
                              "sha256": hashlib.sha256(script.read_bytes()).hexdigest()}})


def _rel(p):
    try:
        return Path(os.path.relpath(Path(p).resolve(), Path.cwd().resolve())).as_posix()
    except ValueError:
        return Path(p).resolve().as_posix()


def _fmt(x, casas=4):
    return "—" if x is None else ("%.*f" % (casas, x))


def _fmt_ic(ic):
    return "—" if not ic else "[%s; %s]" % (_fmt(ic[0]), _fmt(ic[1]))


def tabela_markdown(resultado):
    par = resultado["parametros"]
    linhas = ["# Comparacao pareada A (shadow) x B (active)", "",
              "Fases: %s. Primario: `%s`. IC primario: %s. Semente: %d; %d reamostragens de blocos, %d "
              "hierarquicas. Razao = B/A." % (",".join(par["fases"]), par["primario"], par["ic_primario"],
                                              par["semente_boot"], par["n_boot"], par["n_hier"]), ""]
    for celula, c in resultado["celulas"].items():
        linhas += ["## Celula %s (%d blocos)" % (celula, len(c["blocos"])), "",
                   "| Desfecho | Familia | n blocos | Razao B/A | IC t 95 % | IC boot blocos | IC hierarquico "
                   "| p (permutacao) | p Holm | Excluidos |",
                   "|---|---|---|---|---|---|---|---|---|---|"]
        for nome, e in c["desfechos"].items():
            linhas.append("| `%s` | %s | %d | %s | %s | %s | %s | %s | %s | %d |" % (
                nome, e["familia"], e["n_blocos"], _fmt(e["estimativa"]), _fmt_ic(e["ic_t"]),
                _fmt_ic(e["ic_boot_blocos"]), _fmt_ic(e["ic_hierarquico"]), _fmt(e["p_permutacao"]),
                _fmt(e["p_holm"]), len(e["blocos_excluidos"])))
        ni = c["desfechos"].get(DESFECHO_NI, {}).get("nao_inferioridade")
        linhas.append("")
        if ni:
            linhas.append("Nao-inferioridade da vazao util (delta = %s): limite inferior do IC t = %s -> **%s**."
                          % (_fmt(ni["delta"], 2), _fmt(ni["limite_inferior_ic_t"]), ni["resultado"]))
        if c["blocos_excluidos_por_steal"]:
            linhas.append("Blocos fora dos desfechos de CPU por steal > 5 %%: %s."
                          % ", ".join(c["blocos_excluidos_por_steal"]))
        v = c["violacoes"]
        if v.get("presente"):
            for k in INVARIANTES_VIOL:
                r = v[k]
                txt = "%s: A = %d, B = %d (N: A = %d, B = %d)" % (k, r["contagem"]["A"], r["contagem"]["B"],
                                                                  r["n_base"]["A"], r["n_base"]["B"])
                for br, x in r.get("regra_de_tres", {}).items():
                    txt += "; %s nao observado, limite superior 95 %% = %s (%s)" % (
                        br, _fmt(x["limite_superior_95"], 6), x["rotulo"])
                if "razao_de_taxas" in r:
                    txt += "; razao de taxas B/A = %s, IC %s" % (_fmt(r["razao_de_taxas"]["estimativa"]),
                                                                 _fmt_ic(r["razao_de_taxas"]["ic_boot_blocos"]))
                linhas.append("- " + txt)
        linhas.append("")
    return "\n".join(linhas) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description="Comparacao pareada A x B por blocos (B-39.3).")
    ap.add_argument("--manifesto", required=True)
    ap.add_argument("--semente-boot", type=int, required=True)
    ap.add_argument("--primario", required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--fases", default=",".join(FASES_PADRAO))
    ap.add_argument("--rep", type=int, default=1)
    ap.add_argument("--w-recovery-s", type=float, default=60.0)
    ap.add_argument("--n-boot", type=int, default=N_BOOT_PADRAO)
    ap.add_argument("--n-hier", type=int, default=N_HIER_PADRAO)
    ap.add_argument("--delta", type=float, default=DELTA_PADRAO)
    ap.add_argument("--marcadores-nome", default="marcadores.txt")
    a = ap.parse_args(argv)
    saida = Path(a.saida)
    md = saida.with_suffix(".md")
    try:
        for p in (saida, md):
            if p.exists():
                raise ErroUso("saida ja existe (nunca sobrescreve): %s" % p)
        if saida.suffix != ".json":
            raise ErroUso("--saida deve terminar em .json")
        if a.n_boot < 100 or a.n_hier < 100:
            raise ErroUso("--n-boot e --n-hier devem ser >= 100")
        if not 0 < a.delta < 1:
            raise ErroUso("--delta fora de (0, 1)")
        r = comparar(a.manifesto, a.semente_boot, a.primario, [x for x in a.fases.split(",") if x], a.rep,
                     a.w_recovery_s, a.n_boot, a.n_hier, a.delta, a.marcadores_nome)
        dados = (json.dumps(r, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")
        tab = tabela_markdown(r).encode("utf-8")
        with open(saida, "xb") as fh:
            fh.write(dados)
        with open(md, "xb") as fh:
            fh.write(tab)
    except ERROS_USO as e:
        print("ERRO: %s" % e, file=sys.stderr)
        return 2
    except FileExistsError as e:
        print("ERRO: saida ja existe: %s" % e, file=sys.stderr)
        return 2
    print("comparacao gravada em %s e %s" % (saida, md))
    return 0


if __name__ == "__main__":
    sys.exit(main())
