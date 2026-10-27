"""Proxy de energia por execucao e por fase (B-39.2; plano de 22/10, secoes 5.2 e 5.3).

Uso (da raiz do repositorio):
  python -I analysis/energia_proxy.py --execucao DIR --rep N --cenario S1|S2|S3
      [--marcadores ARQ --w-recovery-s 60 [--convergencia ARQ.json]]
      [--p-max-w W --razoes-ocioso 0.3,0.5,0.7] --saida energia-rep-NNN.json

Entradas em DIR (contratos copiados dos prompts do B-37 e do B-27):
  recursos-rep-NNN.csv + recursos-meta-rep-NNN.json   contrato recursos/1
  rep-NNN.csv + meta-csv-rep-NNN.json                 contrato CSV 2 (S1/S2: operacoes uteis)
  s3-acoes-rep-NNN.csv                                contrato S3 v2, 10 colunas (S3)

Medido: CPU-s por fonte e fase (interpolacao linear do contador acumulado nas fronteiras, nunca
extrapolada), utilizacao, CPU-s por 1000 operacoes uteis, steal da execucao (exclusao se > 5 %),
retrabalho S3. Estimado (so com --p-max-w): energia dinamica e total pelo modelo linear declarado
P(u) = P_ocioso + (P_max - P_ocioso) * u. Nada aqui e medicao de energia (plano 5.3).

Janela da execucao [t0, t1): S1/S2 do menor start_ns ao maior end_ns (+1 ns) dos eventos; S3 do menor
t_ns ao maior t_fim_ns (+1 ns) das acoes. Evento e acao entram na fase do seu inicio (start_ns, t_ns),
como a coluna phase do EventCsvWriter (:126). A fase "total" e a janela inteira.

Exit: 0 ok, 2 uso/contrato/saida existente. So biblioteca padrao.
"""
import argparse
import bisect
import csv
import hashlib
import json
import os
import sys
from fractions import Fraction
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # fases e referencia_p99 (python -I)

import fases  # noqa: E402
from referencia_p99 import VALIDOS, caminho_meta  # noqa: E402,F401  (VALIDOS = copia de rebaseline)

ESQUEMA = "energia-proxy/1"
CABECALHO_RECURSOS = "t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado"
CONTRATO_RECURSOS = "recursos/1"
CABECALHO_S3_V2 = ("t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,"
                   "t_fim_ns,espera_portao_ns")
CABECALHO_S3_V1 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota"
CABECALHO_EVENTOS = "op_id,operation,start_ns,end_ns,replica,return_code,key"
LIMIAR_STEAL = Fraction(5, 100)          # plano 5.3, item 5: excluida se steal > 5 % (estrito)
RAZOES_OCIOSO_PADRAO = (0.3, 0.5, 0.7)
FONTE_WORKLOAD = "pid:workload"
FONTE_VM = "vm"
FONTE_COLETOR = "coletor"
AGREGADOS = ("vm", "soma_cg", FONTE_WORKLOAD)
TOTAL = "total"
MODELO = "P(u) = P_ocioso + (P_max - P_ocioso) * u, u = cpu_s / (duracao_s * nproc), P_ocioso = razao * P_max"


class ErroUso(Exception):
    """Uso invalido, contrato violado ou saida existente: exit 2."""


# ============================================================================= recursos/1

def ler_meta_recursos(caminho):
    """Meta do coletor; exige contrato recursos/1 e clk_tck, nproc, intervalo_ms inteiros > 0."""
    p = Path(caminho)
    if not p.is_file():
        raise ErroUso("meta de recursos ausente: %s" % p)
    try:
        m = json.loads(p.read_text(encoding="utf-8"))
    except ValueError:
        raise ErroUso("meta de recursos ilegivel: %s" % p) from None
    if m.get("contrato") != CONTRATO_RECURSOS:
        raise ErroUso("contrato %r em %s (exigido %s)" % (m.get("contrato"), p, CONTRATO_RECURSOS))
    for k in ("clk_tck", "nproc", "intervalo_ms"):
        v = m.get(k)
        if not isinstance(v, int) or isinstance(v, bool) or v <= 0:
            raise ErroUso("%s invalido em %s: %r" % (k, p, v))
    return m


def _inteiro_ou_vazio(txt, nome, p):
    if txt == "":
        return None
    try:
        return int(txt)
    except ValueError:
        raise ErroUso("%s nao inteiro em %s: %r" % (nome, p, txt)) from None


def ler_recursos(caminho):
    """{fonte: [(t_epoch_ns, cpu_usec|None, steal_usec|None, ok)]} ordenado por t.

    Cabecalho exato do contrato recursos/1. Linha "ok" exige cpu_usec_acum; linha "erro:<motivo>"
    exige numericos vazios (o contrato proibe 0 no lugar de vazio).
    """
    p = Path(caminho)
    if not p.is_file():
        raise ErroUso("CSV de recursos ausente: %s" % p)
    out = {}
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        cab = ",".join(next(r, []))
        if cab != CABECALHO_RECURSOS:
            raise ErroUso("cabecalho de recursos inesperado em %s: %s" % (p, cab))
        for x in r:
            if not x:
                continue
            if len(x) != 6:
                raise ErroUso("linha de recursos com %d colunas em %s" % (len(x), p))
            t = _inteiro_ou_vazio(x[0], "t_epoch_ns", p)
            if t is None:
                raise ErroUso("t_epoch_ns vazio em %s" % p)
            fonte, estado = x[1], x[5]
            cpu = _inteiro_ou_vazio(x[2], "cpu_usec_acum", p)
            _inteiro_ou_vazio(x[3], "mem_bytes", p)
            steal = _inteiro_ou_vazio(x[4], "steal_usec_acum", p)
            if estado == "ok":
                if cpu is None:
                    raise ErroUso("linha ok sem cpu_usec_acum em %s (t=%d, %s)" % (p, t, fonte))
                ok = True
            elif estado.startswith("erro:"):
                if any(v != "" for v in x[2:5]):
                    raise ErroUso("linha com erro e numericos preenchidos em %s (t=%d, %s)" % (p, t, fonte))
                ok = False
            else:
                raise ErroUso("estado invalido em %s: %r" % (p, estado))
            out.setdefault(fonte, []).append((t, cpu, steal, ok))
    for fonte in out:
        out[fonte].sort(key=lambda a: a[0])
    return out


def contador_no_intervalo(amostras, a, b, intervalo_ms, campo="cpu"):
    """(delta Fraction|None, marca) do contador acumulado entre os instantes a e b (a <= b).

    Interpolacao LINEAR entre as duas amostras validas vizinhas de cada fronteira, em aritmetica exata.
    Amostra com estado erro (ou sem o campo) e ignorada. Fronteira antes da primeira ou depois da
    ultima amostra valida: (None, "fora-das-amostras"), nunca extrapola. Trecho entre amostras validas
    consecutivas maior que 2 x intervalo_ms que toque (a, b): (None, "lacuna").
    """
    idx = 1 if campo == "cpu" else 2
    validas = [(s[0], s[idx]) for s in amostras if s[3] and s[idx] is not None]
    if not validas:
        return None, "fora-das-amostras"
    ts = [t for t, _ in validas]
    if a < ts[0] or b > ts[-1]:
        return None, "fora-das-amostras"
    limite = 2 * intervalo_ms * 1_000_000
    for (t1, _), (t2, _) in zip(validas, validas[1:]):
        if t2 > a and t1 < b and t2 - t1 > limite:
            return None, "lacuna"

    def valor(t):
        i = bisect.bisect_right(ts, t)
        if ts[i - 1] == t:
            return Fraction(validas[i - 1][1])
        (ta, va), (tb, vb) = validas[i - 1], validas[i]
        return va + Fraction(vb - va) * (t - ta) / (tb - ta)

    return valor(b) - valor(a), "ok"


def cpu_por_fase(recursos_csv, intervalos, intervalo_ms):
    """{fonte: {fase: {"cpu_s": float|None, "marca": "ok"|"fora-das-amostras"|"lacuna"}}}.

    intervalos: [(rotulo, t_ini, t_fim)] meio-abertos; rotulo repetido soma os trechos (marca do
    pior trecho). A fonte "coletor" entra como as outras (o agregado a ignora).
    """
    rec = ler_recursos(recursos_csv) if not isinstance(recursos_csv, dict) else recursos_csv
    out = {}
    for fonte, amostras in sorted(rec.items()):
        por = {}
        for rot, a, b in intervalos:
            d, marca = contador_no_intervalo(amostras, a, b, intervalo_ms)
            if rot in por:
                ant = por[rot]
                if ant["_d"] is None or d is None:
                    marca = marca if d is None else ant["marca"]
                    por[rot] = {"_d": None, "marca": marca}
                else:
                    por[rot] = {"_d": ant["_d"] + d, "marca": "ok"}
            else:
                por[rot] = {"_d": d, "marca": marca}
        out[fonte] = {rot: {"cpu_s": None if v["_d"] is None else float(v["_d"] / 1_000_000), "marca": v["marca"]}
                      for rot, v in por.items()}
    return out


def steal_frac(recursos_csv, t0, t1, nproc, intervalo_ms):
    """Delta steal / (delta t * nproc) da fonte vm em [t0, t1), como Fraction; None sem medida."""
    rec = ler_recursos(recursos_csv) if not isinstance(recursos_csv, dict) else recursos_csv
    vm = rec.get(FONTE_VM)
    if not vm or t1 <= t0:
        return None
    d, marca = contador_no_intervalo(vm, t0, t1, intervalo_ms, campo="steal")
    if d is None:
        return None
    return d * 1000 / ((t1 - t0) * nproc)      # usec / (ns / 1000)


def excluida_por_steal(frac):
    """True se frac > 0,05 (estrito); None se o steal nao pode ser medido."""
    if frac is None:
        return None
    return Fraction(frac) > LIMIAR_STEAL


# ============================================================================= eventos e acoes

def ler_acoes_s3(caminho):
    """[(t_ns, t_fim_ns, espera_portao_ns, tentativas, falha_percebida)] do contrato S3 v2.

    Cabecalho de 8 colunas (antes do B-27) e qualquer outro diferente do v2: ErroUso.
    """
    p = Path(caminho)
    if not p.is_file():
        raise ErroUso("CSV S3 ausente: %s" % p)
    out = []
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        cab = ",".join(next(r, []))
        if cab == CABECALHO_S3_V1:
            raise ErroUso("CSV S3 com 8 colunas (anterior ao B-27, sem t_fim_ns): %s" % p)
        if cab != CABECALHO_S3_V2:
            raise ErroUso("cabecalho S3 inesperado em %s: %s" % (p, cab))
        for x in r:
            if not x:
                continue
            if len(x) != 10:
                raise ErroUso("linha S3 com %d colunas em %s" % (len(x), p))
            if x[6] not in ("true", "false"):
                raise ErroUso("falha_percebida invalida em %s: %r" % (p, x[6]))
            try:
                out.append((int(x[0]), int(x[8]), int(x[9]), int(x[5]), x[6] == "true"))
            except ValueError:
                raise ErroUso("campo inteiro invalido em %s: %s" % (p, x)) from None
    return out


def ler_eventos_inicio(csv_path):
    """[(start_ns, end_ns, operation, return_code)] de um rep-NNN.csv do contrato 2."""
    p = Path(csv_path)
    if not p.is_file():
        raise ErroUso("CSV de eventos ausente: %s" % p)
    meta = caminho_meta(p)
    try:
        versao = json.loads(meta.read_text(encoding="utf-8")).get("contrato_csv") if meta.exists() else None
    except ValueError:
        versao = None
    if versao != 2:
        raise ErroUso("CSV sem meta-csv de contrato 2: %s" % p)
    out = []
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        cab = ",".join(next(r, []))
        if cab not in (CABECALHO_EVENTOS, CABECALHO_EVENTOS + ",phase"):
            raise ErroUso("cabecalho de eventos inesperado em %s: %s" % (p, cab))
        for x in r:
            if not x:
                continue
            try:
                out.append((int(x[2]), int(x[3]), x[1], x[5]))
            except (ValueError, IndexError):
                raise ErroUso("linha de eventos invalida em %s: %s" % (p, x)) from None
    return out


def e_util_evento(operation, return_code):
    """S1/S2: return_code em VALIDOS da operation (copia de rebaseline.VALIDOS; DT-009)."""
    return return_code in VALIDOS.get(operation, ())


def energia_fase(cpu_s, duracao_s, nproc, p_max_w, razao_ocioso):
    """(E_total, E_dinamica) em J de uma fase, pelo modelo linear declarado.

    E_total = P_ocioso * dt + (P_max - P_ocioso) * cpu_s / nproc; E_dinamica = o segundo termo.
    """
    p_ocioso = razao_ocioso * p_max_w
    din = (p_max_w - p_ocioso) * cpu_s / nproc
    return p_ocioso * duracao_s + din, din


# ============================================================================= execucao

def _sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def _relativo(p):
    try:
        return Path(os.path.relpath(Path(p).resolve(), Path.cwd().resolve())).as_posix()
    except ValueError:
        return Path(p).resolve().as_posix()


def _por_1000(x, uteis):
    return None if x is None or not uteis else x * 1000.0 / uteis


def analisar_execucao(execucao, rep, cenario, marcadores=None, w_recovery_s=60, convergencia=None,
                      p_max_w=None, razoes_ocioso=RAZOES_OCIOSO_PADRAO):
    """Dicionario do esquema energia-proxy/1 de uma execucao (ErroUso em contrato violado)."""
    if cenario not in ("S1", "S2", "S3"):
        raise ErroUso("cenario invalido: %r" % (cenario,))
    d = Path(execucao)
    nnn = "%03d" % int(rep)
    meta = ler_meta_recursos(d / ("recursos-meta-rep-%s.json" % nnn))
    rec_path = d / ("recursos-rep-%s.csv" % nnn)
    rec = ler_recursos(rec_path)
    nproc, intervalo_ms = meta["nproc"], meta["intervalo_ms"]

    acoes = None
    if cenario == "S3":
        acoes = ler_acoes_s3(d / ("s3-acoes-rep-%s.csv" % nnn))
        if not acoes:
            raise ErroUso("CSV S3 sem acoes")
        itens = [(t, not falha) for t, _, _, _, falha in acoes]     # (inicio, util?)
        t0, t1 = min(a[0] for a in acoes), max(a[1] for a in acoes) + 1
        origem_janela = "acoes-s3"
    else:
        ev = ler_eventos_inicio(d / ("rep-%s.csv" % nnn))
        if not ev:
            raise ErroUso("CSV de eventos vazio")
        itens = [(a, e_util_evento(op, rc)) for a, _, op, rc in ev]
        t0, t1 = min(e[0] for e in ev), max(e[1] for e in ev) + 1
        origem_janela = "eventos"

    mk = {"inicio": None, "split": None, "fim": None}
    conv, div = None, False
    if marcadores is not None:
        try:
            mk = fases.ler_marcadores(marcadores)
        except ValueError as e:
            raise ErroUso(str(e)) from None
        if convergencia is not None:
            conv, div = fases.ler_convergencia(convergencia)
    w_ns = int(round(float(w_recovery_s) * 1e9))
    args = (mk["inicio"], mk["split"], mk["fim"], w_ns, conv, div)
    ivs = fases.intervalos_de_fase(t0, t1, *args)
    rotulos = [r for r, _, _ in ivs]
    dur = {r: (b - a) / 1e9 for r, a, b in ivs}
    dur[TOTAL] = (t1 - t0) / 1e9

    uteis = {r: 0 for r in rotulos}
    for t, util in itens:
        if util:
            uteis[fases.fase_de(t, *args)] += 1
    uteis[TOTAL] = sum(uteis[r] for r in rotulos)

    trechos = list(ivs) + [(TOTAL, t0, t1)]
    cpu = cpu_por_fase(rec, trechos, intervalo_ms)

    def agregado(nome):
        if nome == "soma_cg":
            fontes = [f for f in cpu if f.startswith("cg:")]
        else:
            fontes = [nome] if nome in cpu else []
        out = {}
        for r in rotulos + [TOTAL]:
            if not fontes:
                v, marca = None, "ausente"
            else:
                vals = [cpu[f][r] for f in fontes]
                ruins = [x["marca"] for x in vals if x["cpu_s"] is None]
                if ruins:
                    v, marca = None, ("lacuna" if "lacuna" in ruins else ruins[0])
                else:
                    v, marca = sum(x["cpu_s"] for x in vals), "ok"
            out[r] = {"cpu_s": v, "marca": marca,
                      "utilizacao": None if v is None or dur[r] <= 0 else v / (dur[r] * nproc),
                      "cpu_s_por_1000_uteis": _por_1000(v, uteis[r])}
        return out

    agregados = {nome: agregado(nome) for nome in AGREGADOS}

    sf = steal_frac(rec, t0, t1, nproc, intervalo_ms)
    steal = {"frac": None if sf is None else float(sf), "limiar": float(LIMIAR_STEAL),
             "excluida": excluida_por_steal(sf)}

    retrabalho = None
    if acoes is not None:
        n_util = sum(1 for a in acoes if not a[4])
        ret = sum(a[3] - 1 for a in acoes)
        falhas = sum(1 for a in acoes if a[4])
        retrabalho = {"acoes": len(acoes), "acoes_uteis": n_util, "acoes_com_falha": falhas,
                      "retentativas": ret,
                      "retentativas_por_util": None if not n_util else ret / n_util,
                      "falhas_por_util": None if not n_util else falhas / n_util}

    if p_max_w is None:
        energia = {"calculada": False, "motivo": "sem --p-max-w: so CPU (energia nao estimada)"}
    else:
        por_razao = {}
        for razao in razoes_ocioso:
            pr = {}
            for nome, ag in agregados.items():
                pf = {}
                for r, x in ag.items():
                    if x["cpu_s"] is None:
                        pf[r] = {"e_total_j": None, "e_dinamica_j": None, "e_total_j_por_1000_uteis": None,
                                 "e_dinamica_j_por_1000_uteis": None}
                        continue
                    et, ed = energia_fase(x["cpu_s"], dur[r], nproc, float(p_max_w), float(razao))
                    pf[r] = {"e_total_j": et, "e_dinamica_j": ed,
                             "e_total_j_por_1000_uteis": _por_1000(et, uteis[r]),
                             "e_dinamica_j_por_1000_uteis": _por_1000(ed, uteis[r])}
                pr[nome] = pf
            por_razao[repr(float(razao))] = pr
        energia = {"calculada": True, "modelo": MODELO, "p_max_w": p_max_w,
                   "razoes_ocioso": [float(x) for x in razoes_ocioso], "por_razao": por_razao,
                   "nota": "estimativa por modelo declarado; so comparacoes relativas entre bracos (plano 5.2)"}

    script = Path(__file__).resolve()
    return {"esquema": ESQUEMA, "cenario": cenario, "rep": int(rep), "execucao": _relativo(d),
            "janela": {"t0_ns": t0, "t1_ns": t1, "origem": origem_janela},
            "fases": [{"rotulo": r, "t_ini_ns": a, "t_fim_ns": b, "duracao_s": (b - a) / 1e9} for r, a, b in ivs],
            "recursos": {"nproc": nproc, "clk_tck": meta["clk_tck"], "intervalo_ms": intervalo_ms,
                         "csv_sha256": _sha256(rec_path)},
            "cpu_s": cpu, "agregados": agregados, "uteis": uteis, "steal": steal,
            "retrabalho": retrabalho, "energia": energia,
            "parametros": {"marcadores": None if marcadores is None else _relativo(marcadores),
                           "w_recovery_s": float(w_recovery_s),
                           "convergencia": None if convergencia is None else _relativo(convergencia)},
            "gerado_por": {"script": "analysis/energia_proxy.py", "sha256": _sha256(script)}}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Proxy de energia por execucao e fase (B-39.2).")
    ap.add_argument("--execucao", required=True)
    ap.add_argument("--rep", type=int, required=True)
    ap.add_argument("--cenario", required=True)
    ap.add_argument("--marcadores")
    ap.add_argument("--w-recovery-s", type=float, default=60.0)
    ap.add_argument("--convergencia")
    ap.add_argument("--p-max-w", type=float)
    ap.add_argument("--razoes-ocioso", default=",".join(str(x) for x in RAZOES_OCIOSO_PADRAO))
    ap.add_argument("--saida", required=True)
    a = ap.parse_args(argv)
    try:
        if Path(a.saida).exists():
            raise ErroUso("saida ja existe (nunca sobrescreve): %s" % a.saida)
        if a.convergencia and not a.marcadores:
            raise ErroUso("--convergencia exige --marcadores")
        try:
            razoes = tuple(float(x) for x in a.razoes_ocioso.split(","))
        except ValueError:
            raise ErroUso("--razoes-ocioso invalido: %r" % a.razoes_ocioso) from None
        if any(not 0 <= r < 1 for r in razoes):
            raise ErroUso("razoes de ociosidade fora de [0, 1)")
        if a.p_max_w is not None and a.p_max_w <= 0:
            raise ErroUso("--p-max-w deve ser > 0")
        p_max = None if a.p_max_w is None else (int(a.p_max_w) if a.p_max_w == int(a.p_max_w) else a.p_max_w)
        r = analisar_execucao(a.execucao, a.rep, a.cenario, a.marcadores, a.w_recovery_s, a.convergencia,
                              p_max, razoes)
        dados = (json.dumps(r, indent=1, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8")
        with open(a.saida, "xb") as fh:
            fh.write(dados)
    except (ErroUso, FileExistsError) as e:
        print("ERRO: %s" % e, file=sys.stderr)
        return 2
    print("energia gravada em %s (steal excluida=%s)" % (a.saida, r["steal"]["excluida"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
