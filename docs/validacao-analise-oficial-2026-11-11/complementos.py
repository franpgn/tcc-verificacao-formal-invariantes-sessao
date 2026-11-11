"""Complementos do capitulo de resultados (descritivos e sensibilidades aprovadas pelo Gestor em 07/11).

Uso: python -E -P complementos.py <campanha-s3-fatorial> <comparacao-principal.json> <comparacao-semfalha.json>
1. sensibilidade sem o bloco 13 (exec-0037): efeitos, IU e Holm m = 2;
2. p99 so das acoes bem-sucedidas (sem falha percebida), por braco e efeito (nao pre-registrado);
3. V so com auditor online + varredura (sem a sonda de replay), por efeito (Q5b da pre-analise);
4. pares de V por invariante e por origem, por braco; perda permanente contida na exposicao?;
5. divergencia persistente, delta_rec e delta_conv por braco (do oficial).
Reusa as regras de valida_fatorial.py (mesma pasta).
"""
import json
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

sys.argv = [sys.argv[0], sys.argv[1], sys.argv[2], sys.argv[3], str(Path(__file__).with_name("_lixo.json"))]
SRC = Path(__file__).with_name("valida_fatorial.py").read_text(encoding="utf-8")
corte = SRC.index("# ---------------------------------------------------------------- integridade e protocolo")
g = {"__name__": "valida", "__file__": __file__}
exec(compile(SRC[:corte], "valida_fatorial", "exec"), g)          # so as definicoes (execucao, t_ns, p_rank...)
exec(compile(SRC[SRC.index("def perm(d):"):SRC.index("DESF = {")], "valida_fatorial_est", "exec"), g)
RAIZ, OF, SF = g["RAIZ"], g["OFICIAL"], g["OFICIAL_SF"]
perm, ict, p_ni, holm, p_rank = g["perm"], g["ict"], g["p_ni"], g["holm"], g["p_rank"]

import csv  # noqa: E402

manif = defaultdict(dict)
with open(RAIZ / "manifesto-bracos.tsv", newline="", encoding="utf-8") as fh:
    for x in csv.DictReader(fh, delimiter="\t"):
        manif[x["celula"]].setdefault(x["bloco"], {})[x["braco"]] = x["execucao"]
F2 = manif["ASYNC-F2"]
BL = sorted(F2, key=int)
BR = ("PO", "PL", "AO", "AL")


# ---- versao instrumentada da apuracao: pares com origens, p99 das bem-sucedidas, perda x exposicao
def detalhe(nome):
    d = RAIZ / nome
    base = g["execucao"](d, False)
    # repete a leitura para guardar pares e origens (mesmas regras de valida_fatorial.execucao)
    ej = json.loads((d / "execucao.json").read_text(encoding="utf-8"))
    ini = fim = None
    for ln in (d / "marcadores.txt").read_text(encoding="utf-8").splitlines():
        ln = ln.strip()
        if ln and not ln.startswith("#"):
            kv = dict(t.split("=", 1) for t in ln.split())
            if kv["event"] == "start" and ini is None:
                ini = int(kv["t_epoch_ns"])
            elif kv["event"] == "end":
                fim = int(kv["t_epoch_ns"])
    res = json.loads((d / "convergencia-resumo-rep-001.json").read_text(encoding="utf-8"))
    conv = res.get("t_convergencia_abs_ns")
    div = bool(res.get("divergencia_persistente", False))
    lim = fim + g["W_NS"] if conv is None else max(fim + g["W_NS"], int(conv))

    def sel(t):
        return t >= ini and (t <= fim or div or t <= lim)

    acs, por_chave, fins = [], defaultdict(list), []
    with open(d / "s3-acoes-rep-001.csv", newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r)
        for x in r:
            if x:
                fins.append(int(x[8]))
                if sel(int(x[0])):
                    acs.append((int(x[0]), int(x[8]), x[6] == "true"))
    with open(d / "rep-001.csv", newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r)
        for x in r:
            if x:
                por_chave[x[6]].append((x[1], int(x[2]), int(x[3]), x[4], x[5]))
                if x[1] not in g["INSTR"]:
                    fins.append(int(x[3]))
    tfc = max(fins)
    ok = [b - a for a, b, f in acs if not f]
    out = {"p99_sucesso": p_rank(ok, 0.99) / 1e9, "n_sucesso": len(ok), "n_acoes": len(acs)}
    dets = []
    with open(d / "violacoes-rep-001.csv", newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            inv, cl = (x.get("invariante") or "").strip(), (x.get("classificacao") or "").strip()
            classe = "I2_REAL" if inv == "I2" and cl == "REAL" else (inv if inv in ("I1", "I4") else None)
            if classe:
                m = g["RX_MOT"].search(x.get("mensagem") or "")
                dets.append(("sonda" if (x.get("origem") or "").strip() else "online", classe, x["chave"].strip(),
                             g["t_ns"](x["instante"]), m.group(1) if m else None, None))
    with open(d / "varredura-rep-001.csv", newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r)
        for x in r:
            if not x or len(x) < 3:
                continue
            inv, ch, cl, det = x[0].strip(), x[1].strip(), x[2].strip(), ",".join(x[3:])
            if cl != "REAL" or inv not in ("I1", "I4", "I2", "I2-PERDIDO"):
                continue
            if inv in ("I1", "I4"):
                m = g["RX_MOT"].search(det)
                dets.append(("varredura", inv, ch, None, m.group(1) if m else None, None))
            else:
                m = g["RX_ID"].search(det)
                dets.append(("varredura", "I2_REAL", ch, None, None,
                             ch if inv == "I2-PERDIDO" else (m.group(1) if m else None)))
    pares = defaultdict(list)
    for orig, classe, ch, t, mot, ident in dets:
        tipo = orig if orig != "sonda" else ("tardia" if t > tfc else "imediata")
        if tipo in ("online", "imediata"):
            t_at = t
        elif classe in ("I1", "I4"):
            ts = [e[2] for e in por_chave.get(ch, ()) if e[0] == "Logout" and e[4] == "NONE"]
            t_at = min(ts) if (ts and mot in (None, "LOGOUT")) else None
        else:
            ts = [e[2] for e in por_chave.get(ident, ()) if e[0] == "Block" and e[4] == "NONE"] if ident else []
            t_at = max(ts) if ts else None
        if t_at is None or sel(t_at):
            pares[(ch, classe)].append((tipo, t))
    out["pares"] = len(pares)
    assert out["pares"] == base["V_pares"], (nome, out["pares"], base["V_pares"])
    out["por_classe"] = Counter(c for _, c in pares)
    out["por_origem"] = Counter()
    for v in pares.values():
        for o in {("sonda" if t in ("imediata", "tardia") else t) for t, _ in v}:
            out["por_origem"][o] += 1
    out["so_sonda"] = sum(1 for v in pares.values() if all(t in ("imediata", "tardia") for t, _ in v))
    out["pares_sem_sonda"] = sum(1 for v in pares.values() if any(t in ("online", "varredura") for t, _ in v))
    tf = int(ej["t_fusao_ns"])
    perda = {ch for (ch, c), v in pares.items() if c == "I1" and any(
        t in ("varredura", "tardia") or (t == "online" and tt >= tf) for t, tt in v)}
    expo = {ch for (ch, c), v in pares.items() if c == "I1" and any(
        t in ("imediata", "online") and tt < tf for t, tt in v)}
    assert len(perda) == base["perda_permanente"] and len(expo) == base["exposicao"]
    out["perda_fora_da_exposicao"] = len(perda - expo)
    out["V_sem_sonda_por_1000"] = 1000.0 * out["pares_sem_sonda"] / len(acs)
    out["V_por_1000"] = base["V_por_1000"]
    out["p99"] = base["p99_com_espera"]
    out["vazao"] = base["vazao_util"]
    out["falhas"] = base["falhas_por_1000"]
    out["taxa_erro"] = base["taxa_erro"]
    out["perda"] = base["perda_permanente"]
    out["expo"] = base["exposicao"]
    out["div"] = base["divergencia"]
    return out


VAL = {F2[b][br]: detalhe(F2[b][br]) for b in BL for br in BR}


def efeitos(campo, log, blocos):
    f = (lambda v: math.log(v)) if log else (lambda v: v)
    Y = {br: np.array([f(VAL[F2[b][br]][campo]) for b in blocos]) for br in BR}
    return {"A1": 0.5 * ((Y["AO"] - Y["PO"]) + (Y["AL"] - Y["PL"])),
            "A4": 0.5 * ((Y["PL"] - Y["PO"]) + (Y["AL"] - Y["AO"])),
            "interacao": (Y["AL"] - Y["AO"]) - (Y["PL"] - Y["PO"])}


def fmt(d, log):
    lo, hi = ict(d, log)
    est = math.exp(d.mean()) if log else d.mean()
    return "%.4f [%.4f; %.4f] p %.4f" % (est, lo, hi, perm(d))


print("== 1. Sensibilidade sem o bloco 13 (11 blocos)")
b11 = [b for b in BL if b != "13"]
for campo, log, nome in (("V_por_1000", False, "V por 1000"), ("p99", True, "p99 com espera"),
                         ("vazao", True, "vazao util"), ("falhas", False, "falhas percebidas/1000"),
                         ("taxa_erro", False, "taxa de erro"), ("expo", False, "exposicao"),
                         ("perda", False, "perda permanente")):
    e = efeitos(campo, log, b11)
    print("   %-24s A1 %s | A4 %s | inter %s" % (nome, fmt(e["A1"], log), fmt(e["A4"], log), fmt(e["interacao"], log)))
iu = {}
eV, eL = efeitos("V_por_1000", False, b11), efeitos("p99", True, b11)
for ef in ("A1", "A4"):
    pv = perm(eV[ef]) if eV[ef].mean() < 0 else 1.0
    iu[ef] = max(pv, p_ni(eL[ef], math.log(1.10)))
hh = holm([iu["A1"], iu["A4"]])
print("   IU sem bloco 13: A1 p_IU %.4f Holm %.4f | A4 p_IU %.4f Holm %.4f; limite sup. p99 A4 %.4f"
      % (iu["A1"], hh[0], iu["A4"], hh[1], ict(eL["A4"], True)[1]))

print("\n== 2. p99 so das acoes bem-sucedidas (descritivo, nao pre-registrado)")
for br in BR:
    v = [VAL[F2[b][br]]["p99_sucesso"] for b in BL]
    w = [VAL[F2[b][br]]["p99"] for b in BL]
    print("   %s: p99 sucesso media %.3f s (min %.3f, max %.3f) | p99 co-principal media %.3f s (min %.3f, max %.3f)"
          % (br, np.mean(v), min(v), max(v), np.mean(w), min(w), max(w)))
e = efeitos("p99_sucesso", True, BL)
print("   efeitos (razao): A1 %s | A4 %s | inter %s" % (fmt(e["A1"], True), fmt(e["A4"], True), fmt(e["interacao"], True)))
patamar = sum(1 for b in BL for br in BR if 1.50 <= VAL[F2[b][br]]["p99"] <= 1.51)
print("   execucoes F2 com p99 co-principal entre 1,50 e 1,51 s: %d de 48" % patamar)

print("\n== 3. V sem a sonda de replay (so auditor online + varredura; descritivo)")
e = efeitos("V_sem_sonda_por_1000", False, BL)
print("   A1 %s | A4 %s | inter %s" % (fmt(e["A1"], False), fmt(e["A4"], False), fmt(e["interacao"], False)))

print("\n== 4. Pares de V por invariante e por origem, por braco (12 blocos)")
for br in BR:
    cl, orig, so_s, perda_fora = Counter(), Counter(), 0, 0
    for b in BL:
        v = VAL[F2[b][br]]
        cl.update(v["por_classe"])
        orig.update(v["por_origem"])
        so_s += v["so_sonda"]
        perda_fora += v["perda_fora_da_exposicao"]
    print("   %s: por invariante %s | pares com deteccao por origem %s | pares so pela sonda %d | "
          "sessoes perdidas sem exposicao antes %d" % (br, dict(cl), dict(orig), so_s, perda_fora))

print("\n== 5. Divergencia persistente, delta_rec e delta_conv (oficial, y_por_bloco)")
for br in BR:
    dv = [b for b in BL if VAL[F2[b][br]]["div"]]
    print("   %s: blocos com divergencia persistente %s" % (br, [(b, F2[b][br]) for b in dv]))
of = OF["celulas"]["ASYNC-F2"]["desfechos"]
for nome in ("delta_rec_s", "delta_conv_s"):
    yb = of[nome]["y_por_bloco"]
    med = {br: np.nanmean([np.nan if yb[b].get(br) is None else yb[b][br] for b in yb]) for br in BR}
    print("   %s media por braco: %s" % (nome, {k: round(float(v), 3) for k, v in med.items()}))
for cel, src in (("ASYNC-F1", OF), ("ASYNC-F3", OF), ("ASYNC-F4", OF), ("ASYNC-none", SF)):
    c = src["celulas"][cel]
    print("   %s divergencia persistente d_b: %s" % (cel, c["desfechos"]["divergencia_persistente"]["d_b"]))
    for e2 in sorted(manif[cel]):
        for br in ("A", "B"):
            n = manif[cel][e2][br]
            rr = json.loads((RAIZ / n / "convergencia-resumo-rep-001.json").read_text(encoding="utf-8"))
            if rr.get("divergencia_persistente"):
                print("      divergencia persistente em", n)
