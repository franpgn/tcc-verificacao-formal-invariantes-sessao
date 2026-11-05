"""B-44.3: resumo por execucao (carga, violacoes, vistas, disponibilidade, probe, logs).

Uso: python -I analisa.py RAIZ_runs_b44-3 > resumo.tsv  (e grava eventos-<ROT>.tsv em RAIZ/analise/)

Fontes por execucao ROT:
  fases.tsv                       instantes (ms do host) de CORTE, ACAO, CURA, T0
  carga/ROT/rep-001.csv           eventos medidos (start_ns/end_ns em ns epoch, replica, return_code)
  carga/ROT/violacoes-rep-001.csv violacoes do auditor (instante ISO UTC)
  sonda/ROT.tsv                   vista e disponibilidade por no (REST, ~5 Hz)
  probe/acao-ROT.tsv              pedidos ao probe (t_envio_ms, t_resposta_ms)
  servidores/ROT/isnN.log         ISPN000093/94 (vista), ISPN100002/100010 (rebalanceamento), resolucao de conflitos
"""
import csv
import datetime as dt
import os
import re
import statistics
import sys
from collections import Counter, defaultdict

RAIZ = sys.argv[1] if len(sys.argv) > 1 else "."
PORTA_NO = {"127.0.0.1:12444": "isn1", "127.0.0.1:12445": "isn2", "127.0.0.1:12446": "isn3"}
JANELA_MS = 5000
# execucoes invalidas (fora das tabelas): V3-async-acao2 teve a maquina Podman parada entre corte e probe
INVALIDAS = {"V3-async-acao2"}


def iso_ms(s):
    s = s.strip().rstrip("Z")
    if "." in s:
        a, b = s.split(".")
        b = (b + "000000")[:6]
        s = a + "." + b
    return int(dt.datetime.fromisoformat(s).replace(tzinfo=dt.timezone.utc).timestamp() * 1000)


def fases():
    por = defaultdict(dict)
    atual = None
    for ln in open(os.path.join(RAIZ, "fases.tsv"), encoding="utf-8"):
        c = ln.rstrip("\n").split("\t")
        if len(c) < 3:
            continue
        t, txt = int(c[1]), c[2]
        m = re.match(r"EXEC (\S+) inicio", txt)
        if m:
            atual = m.group(1)
            por[atual]["inicio"] = t
            por[atual]["desc"] = txt
            continue
        if atual is None:
            continue
        if txt.startswith("CORTE"):
            por[atual]["t0"] = int(re.search(r"aplicado=(\d+)", txt).group(1))
            por[atual]["corte_pedido"] = int(re.search(r"pedido=(\d+)", txt).group(1))
        elif txt.startswith("T0 (sem corte)"):
            por[atual]["t0"] = int(txt.split()[-1])
        elif txt.startswith("ACAO") and txt.endswith("inicio"):
            por[atual]["acao_ini"] = t
        elif txt.startswith("ACAO") and txt.endswith("fim"):
            por[atual]["acao_fim"] = t
        elif txt.startswith("SEM ACAO"):
            por[atual]["t_nominal"] = int(txt.split("=")[-1])
        elif txt.startswith("CURA"):
            por[atual]["cura"] = int(re.search(r"aplicado=(\d+)", txt).group(1))
        elif re.match(r"EXEC \S+ fim", txt):
            por[atual]["fim"] = t
            atual = None
    return por


def probe(rot):
    p = os.path.join(RAIZ, "probe", "acao-%s.tsv" % rot)
    if not os.path.exists(p):
        return []
    out = []
    for ln in open(p, encoding="utf-8"):
        c = ln.rstrip("\n").split("\t")
        if len(c) >= 6:
            out.append({"alvo": c[0], "ini": int(c[1]), "fim": int(c[2]), "dur": int(c[3]), "status": c[5],
                        "resp": c[6] if len(c) > 6 else ""})
    return out


def sonda(rot):
    p = os.path.join(RAIZ, "sonda", "%s.tsv" % rot)
    linhas = []
    if os.path.exists(p):
        for ln in open(p, encoding="utf-8"):
            c = ln.rstrip("\n").split("\t")
            if len(c) >= 10:
                linhas.append(c)
    return linhas


def primeiro(linhas, no, cond, depois):
    for c in linhas:
        if c[2] == no and int(c[0]) >= depois and cond(c):
            return int(c[1])  # fim da amostra: limite superior do instante observado
    return None


def carga(rot):
    d = os.path.join(RAIZ, "carga", rot)
    ev = []
    p = os.path.join(d, "rep-001.csv")
    if os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            for r in csv.DictReader(f):
                a, b = int(r["start_ns"]), int(r["end_ns"])
                ev.append((a // 1_000_000, b // 1_000_000, r["operation"],
                           PORTA_NO.get(r["replica"], r["replica"]), r["return_code"], r["key"], (b - a) / 1e6))
    vi = []
    p = os.path.join(d, "violacoes-rep-001.csv")
    if os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            for r in csv.DictReader(f):
                vi.append((iso_ms(r["instante"]), r["invariante"], r["chave"]))
    return ev, vi


RE_LOG = re.compile(r"^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d{3})")


def logs(rot):
    out = []
    d = os.path.join(RAIZ, "servidores", rot)
    if not os.path.isdir(d):
        return out
    for n in ("isn1", "isn2", "isn3"):
        p = os.path.join(d, n + ".log")
        if not os.path.exists(p):
            continue
        for ln in open(p, encoding="utf-8", errors="replace"):
            ln = re.sub(r"\[[0-9;]*m", "", ln)
            m = RE_LOG.match(ln)
            if not m:
                continue
            if any(k in ln for k in ("ISPN000093", "ISPN000094", "ISPN100002", "ISPN100010", "ISPN100011",
                                     "ISPN100007", "ISPN100008", "ISPN100009", "ISPN000312", "ISPN100005",
                                     "I'm not the coordinator", "DEGRADED", "ISPN000310", "ISPN000314",
                                     "ISPN000311", "ISPN000094", "onflict", "ISPN100001", "ISPN100000",
                                     "WARN", "ERROR")):
                t = dt.datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S,%f").replace(tzinfo=dt.timezone.utc)
                out.append((int(t.timestamp() * 1000), n, ln.rstrip()[:400]))
    out.sort()
    return out


def pct(v, q):
    if not v:
        return float("nan")
    v = sorted(v)
    k = max(0, min(len(v) - 1, int(round(q * (len(v) - 1)))))
    return v[k]


def main():
    fs = fases()
    os.makedirs(os.path.join(RAIZ, "analise"), exist_ok=True)
    cab = ["rot", "n_ops", "p50_ms", "p99_ms", "n_ok", "erros_por_tipo", "erros_isn3", "viol_total",
           "viol_por_tipo", "viol_antes_t_ref", "viol_janela_5s", "viol_depois_janela", "t_ref_rel_t0_ms",
           "probe_dur_ms", "t_vista2_maioria_ms", "t_vista1_isn3_ms", "t_degraded_isn3_ms",
           "t_1a_recusa_hotrod_isn3_ms", "t_tam2_isn1_sem_acao_ms", "t_fusao_tam3_ms_desde_cura_ou_acao",
           "esc_ok_isn3_t0_tref", "ops_janela_5s"]
    print("\t".join(cab))
    for rot, f in fs.items():
        if rot in INVALIDAS:
            continue
        ev, vi = carga(rot)
        lat = [e[6] for e in ev]
        ok = [e for e in ev if not e[4].startswith("ERROR")]
        err = Counter(e[4] for e in ev if e[4].startswith("ERROR"))
        err3 = Counter(e[4] for e in ev if e[4].startswith("ERROR") and e[3] == "isn3")
        t0 = f.get("t0")
        pr = probe(rot)
        t_ref = f.get("acao_ini") or f.get("t_nominal")
        if pr:
            t_ref = min(p["ini"] for p in pr)
        vt = Counter(v[1] for v in vi)
        antes = sum(1 for v in vi if t_ref and v[0] < t_ref)
        jan = sum(1 for v in vi if t_ref and t_ref <= v[0] <= t_ref + JANELA_MS)
        dep = sum(1 for v in vi if t_ref and v[0] > t_ref + JANELA_MS)
        ops_jan = sum(1 for e in ev if t_ref and e[0] <= t_ref + JANELA_MS and e[1] >= t_ref)
        sd = sonda(rot)
        base = t_ref or t0 or 0
        tv2 = None
        for no in ("isn1", "isn2"):
            x = primeiro(sd, no, lambda c: c[4] == "2", t0 or 0)
            if x is not None:
                tv2 = x if tv2 is None else max(tv2, x)
        tv1 = primeiro(sd, "isn3", lambda c: c[4] == "1", t0 or 0)
        tdg = primeiro(sd, "isn3", lambda c: "DEGRADED" in c[8], t0 or 0)
        rel = lambda x, b=base: "" if x is None or not b else str(x - b)
        recusa = [e for e in ev if e[3] == "isn3" and e[4].startswith("ERROR") and t0 and e[0] >= t0]
        t_rec = rel(recusa[0][0]) + ":" + recusa[0][4] if recusa else ""
        unav = [e for e in recusa if e[4] == "ERROR_UNAVAILABLE"]
        t_rec += "|1a_UNAVAILABLE=" + (rel(unav[0][0]) if unav else "")
        ref_fusao = f.get("cura") or (f.get("acao_fim") if not f.get("cura") else None)
        tfus = None
        if ref_fusao:
            # primeira amostra, depois da referencia, em que os 3 nos estao com tamanho 3
            ult = {}
            for c in sd:
                if int(c[0]) < ref_fusao:
                    continue
                ult[c[2]] = c[4]
                if len(ult) == 3 and all(v == "3" for v in ult.values()) and tfus is None:
                    # exige que antes tenha havido divisao (tamanho != 3) depois de t0
                    tfus = int(c[0])
            divid = any(c[4] != "3" for c in sd if t0 and int(c[0]) >= t0)
            if not divid:
                tfus = None
        esc3 = 0
        if t0 and t_ref:
            esc3 = sum(1 for e in ev if e[3] == "isn3" and t0 <= e[0] < t_ref and e[2] != "Validate"
                       and not e[4].startswith("ERROR"))
        linha = [rot, len(ev), round(pct(lat, .5), 2), round(pct(lat, .99), 2), len(ok),
                 ";".join("%s=%d" % kv for kv in sorted(err.items())),
                 ";".join("%s=%d" % kv for kv in sorted(err3.items())), len(vi),
                 ";".join("%s=%d" % kv for kv in sorted(vt.items())), antes, jan, dep,
                 "" if not (t_ref and t0) else t_ref - t0,
                 ";".join(str(p["dur"]) for p in pr),
                 rel(tv2), rel(tv1), rel(tdg), t_rec,
                 "" if not t0 else ("" if tv2 is None else tv2 - t0),
                 "" if tfus is None else tfus - ref_fusao, esc3, ops_jan]
        print("\t".join(map(str, linha)))
        with open(os.path.join(RAIZ, "analise", "logs-%s.tsv" % rot), "w", encoding="utf-8", newline="\n") as g:
            for t, n, ln in logs(rot):
                g.write("%d\t%s\t%s\n" % (t, n, ln))


if __name__ == "__main__":
    main()
