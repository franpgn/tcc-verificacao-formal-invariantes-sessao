"""B-44.3: marcos por execucao tirados dos logs dos servidores (relogio do container, alinhado ao do host
dentro de +-25 ms; ver a nota do alinhamento em docs/b44-3-ac6-jgroups.md).

Uso: python -I marcos.py RAIZ > analise/marcos.tsv
Todos os tempos em ms relativos a t_ref (= envio do 1o pedido do probe; nos controles, o instante
nominal t0 + 5 s), exceto quando indicado. T_det (sem acao) e relativo a t0 (corte aplicado).
"""
import datetime as dt
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analisa  # noqa: E402  (reaproveita fases(), probe(), carga())

RAIZ = sys.argv[1] if len(sys.argv) > 1 else "."
analisa.RAIZ = RAIZ
RE_T = re.compile(r"^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d{3})")
ANSI = re.compile(r"\x1b\[[0-9;]*m")


def linhas(rot, no):
    p = os.path.join(RAIZ, "servidores", rot, no + ".log")
    if not os.path.exists(p):
        return []
    out = []
    for ln in open(p, encoding="utf-8", errors="replace"):
        ln = ANSI.sub("", ln)
        m = RE_T.match(ln)
        if m:
            t = dt.datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S,%f").replace(tzinfo=dt.timezone.utc)
            out.append((int(t.timestamp() * 1000), ln.rstrip()))
    return out


def prim(ls, depois, *padroes):
    for t, ln in ls:
        if t >= depois and all(p in ln for p in padroes):
            return t
    return None


def ult(ls, depois, antes, *padroes):
    r = None
    for t, ln in ls:
        if depois <= t <= antes and all(p in ln for p in padroes):
            r = t
    return r


def main():
    fs = analisa.fases()
    cab = ["rot", "t0_utc", "t_ref_menos_t0", "probe_dur_ms(max)", "vista2_isn1", "vista2_isn2", "vista1_isn3",
           "degraded_isn3_sessions", "degraded_isn3_sessions_async", "1a_recusa_hotrod_isn3(ISPN000306)",
           "fim_rebal_maioria(sessions,sessions-async)", "Tdet_maioria_desde_t0", "Tdet_isn3_desde_t0",
           "fusao_ISPN000093_desde_cura_ou_acao(isn1/isn2/isn3)", "resolucao_conflitos(100012..100013)",
           "isn3_AVAILABLE_apos_fusao_desde_cura_ou_acao", "warn_nao_coord",
           "fusao_menos_divisao_isn1_ms", "erros_isn3_tref_fusao", "coord_antes_t0(sonda)"]
    print("\t".join(cab))
    for rot, f in fs.items():
        t0 = f.get("t0")
        if t0 is None or rot in analisa.INVALIDAS:
            continue
        pr = analisa.probe(rot)
        t_ref = min(p["ini"] for p in pr) if pr else (f.get("t_nominal") or t0 + 5000)
        L = {n: linhas(rot, n) for n in ("isn1", "isn2", "isn3")}
        r = lambda x: "" if x is None else str(x - t_ref)
        v2a = prim(L["isn1"], t0, "ISPN000094", "(2)")
        v2b = prim(L["isn2"], t0, "ISPN000094", "(2)")
        v1 = prim(L["isn3"], t0, "ISPN000094", "(1)")
        if v1 is None:  # V-4: isn3 coordenador -> vista de 1 com ele mesmo
            v1 = prim(L["isn3"], t0, "ISPN000094", "] (1) [")
        dg1 = prim(L["isn3"], t0, "[Context=sessions]ISPN100011: Entering availability mode DEGRADED_MODE")
        dg2 = prim(L["isn3"], t0, "[Context=sessions-async]ISPN100011: Entering availability mode DEGRADED_MODE")
        rec = prim(L["isn3"], t0, "ISPN000306")
        vmaj = max([x for x in (v2a, v2b) if x is not None], default=None)
        reb = None
        if vmaj:
            for n in ("isn1", "isn2"):
                for c in ("sessions", "sessions-async"):
                    x = prim(L[n], vmaj, "[o.i.CLUSTER] [Context=%s]ISPN100010" % c)
                    if x and x - vmaj < 60000:
                        reb = x if reb is None else max(reb, x)
        ref_f = f.get("cura") or (max(p["fim"] for p in pr) if pr else None)
        fus = []
        conf = ""
        av3 = ""
        if ref_f:
            for n in ("isn1", "isn2", "isn3"):
                x = prim(L[n], ref_f, "ISPN000093")
                fus.append("" if x is None else str(x - ref_f))
            ini = min([prim(L[n], ref_f, "ISPN100012") or 10**15 for n in L])
            fim = max([ult(L[n], ref_f, 10**15, "ISPN100013") or 0 for n in L])
            if ini < 10**15:
                conf = "%d..%d" % (ini - ref_f, fim - ref_f)
            a = prim(L["isn3"], ref_f, "[Context=sessions]ISPN100011: Entering availability mode AVAILABLE")
            av3 = "" if a is None else str(a - ref_f)
        div = prim(L["isn1"], t0, "ISPN000094")
        fus1 = prim(L["isn1"], ref_f, "ISPN000093") if ref_f else None
        fmd = "" if (div is None or fus1 is None) else str(fus1 - div)
        ev, _ = analisa.carga(rot)
        lim = fus1 if fus1 else 10**15
        e3 = sum(1 for e in ev if e[3] == "isn3" and e[4].startswith("ERROR") and t_ref <= e[0] <= lim)
        # coordenador antes de t0, pela sonda REST (coordinator_address); a fusao pode trocar o coordenador
        co = ""
        for c in analisa.sonda(rot):
            if int(c[0]) < t0 and c[3] == "200":
                co = c[5]
        warn = sum(1 for n in L for t, ln in L[n] if t >= t0 and "I'm not the coordinator" in ln)
        tdet_maj = "" if (pr or vmaj is None) else str(vmaj - t0)
        tdet_3 = "" if (pr or v1 is None) else str(v1 - t0)
        print("\t".join([rot, dt.datetime.fromtimestamp(t0 / 1000, dt.timezone.utc).strftime("%H:%M:%S.%f")[:-3],
                         str(t_ref - t0), str(max((p["dur"] for p in pr), default="")),
                         r(v2a), r(v2b), r(v1), r(dg1), r(dg2), r(rec), r(reb), tdet_maj, tdet_3,
                         "/".join(fus), conf, av3, str(warn), fmd, str(e3), co]))


if __name__ == "__main__":
    main()
