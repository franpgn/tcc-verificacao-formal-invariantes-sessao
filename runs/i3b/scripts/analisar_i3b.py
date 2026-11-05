"""I-3b: instantes e conferencias de uma execucao (P1 de ponta a ponta na versao integrada 17f2bd4).

Uso: python -I analisar_i3b.py <dir_execucao>   -> JSON em stdout (o harness grava em <dir>/resumo.json)
Instantes em ns no relogio dos end_ns (EpochClock da carga) e, os do harness, em time.time_ns(); diferencas em s desde
o fim do comando de corte (fase corte-fim).
"""
import collections
import csv
import datetime
import json
import os
import re
import sys

TETO = 2000.0
ISN3 = "127.0.0.1:12613"


def jsonl(p):
    with open(p, encoding="utf-8") as f:
        return [json.loads(x) for x in f if x.strip()]


def fases(d):
    r = {}
    for l in open(os.path.join(d, "fases.tsv"), encoding="utf-8"):
        ns, _, texto = l.rstrip("\n").split("\t", 2)
        r.setdefault(texto.split(" ")[0], (int(ns), texto))
    return r


def iso_ns(s):
    m = re.match(r"(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(\.\d+)?(Z|[+-]\d\d:\d\d)", s or "")
    if not m:
        return None
    frac = (m.group(2) or ".0")[1:]
    t = datetime.datetime.fromisoformat(m.group(1) + ("+00:00" if m.group(3) == "Z" else m.group(3)))
    return int(t.timestamp()) * 10**9 + int((frac + "000000000")[:9])


def main(d):
    f = fases(d)
    tc = f["corte-fim"][0]

    def rel(t):
        return None if t is None else round((t - tc) / 1e9, 3)

    obs = jsonl(os.path.join(d, "observaveis-rep-001.jsonl"))
    dec = jsonl(os.path.join(d, "decisoes-rep-001.jsonl"))
    rod = [x for x in obs if x["tipo"] == "defasagem"]
    top = [x for x in obs if x["tipo"] == "topologia"]
    r = {"execucao": os.path.basename(d.rstrip("/\\")), "fases": {k: v[1] for k, v in f.items()}}

    t_lag = next((x["t_inicio_ns"] for x in rod if x["t_inicio_ns"] >= tc and x["lag_max_ms"] >= TETO
                  and x["no_suspeito"] == ISN3), None)
    p1 = [x for x in dec if x["tipo"] == "politica" and x["politica"] == "P1"]
    ac5 = [x for x in dec if x["tipo"] == "acao" and x["acao"]["tipo"] == "AC-5"]
    r["P1"] = [{"no": x.get("no"), "t_decisao_s": rel(x["t_decisao_ns"])} for x in p1]
    r["AC5"] = [{"operacao": x["operacao"], "executada": x["executada"], "resultado": x["resultado"],
                 "alvo": x["acao"]["alvo"], "codigo_http": x["codigo_http"], "lido": x["lido"], "erro": x["erro"],
                 "reversao": x.get("reversao"), "t_pedido_s": rel(x["t_pedido_ns"]),
                 "t_inicio_s": rel(x["t_inicio_ns"]) if x["t_inicio_ns"] else None,
                 "duracao_s": None if x["duracao_ns"] is None else round(x["duracao_ns"] / 1e9, 3),
                 "t_fim_s": None if x["duracao_ns"] is None else rel(x["t_inicio_ns"] + x["duracao_ns"])} for x in ac5]

    # saude do isn3 pelo harness (sem credencial)
    saude = []
    p = os.path.join(d, "saude-isn3.tsv")
    if os.path.exists(p):
        saude = [(int(a), b) for a, b in (l.rstrip("\n").split("\t") for l in open(p) if l.strip())]
    ref = ac5[0]["t_pedido_ns"] if ac5 else tc
    ultimo_200 = max((t for t, c in saude if c == "200" and t <= ref + 30 * 10**9), default=None)
    primeiro_sem = next((t for t, c in saude if t >= ref and c != "200"), None)
    r["saude_isn3"] = {"amostras": len(saude), "codigos": dict(collections.Counter(c for _, c in saude)),
                       "ultimo_200_s": rel(ultimo_200), "primeiro_sem_200_depois_do_pedido_s": rel(primeiro_sem)}

    # Hot Rod do isn3 pela carga (CSV de eventos)
    eventos = list(csv.DictReader(open(os.path.join(d, "rep-001.csv"), encoding="utf-8")))
    do3 = [e for e in eventos if e["replica"] == ISN3]
    ok3 = [int(e["end_ns"]) for e in do3 if not e["return_code"].startswith("ERROR")]
    depois = [e for e in do3 if int(e["end_ns"]) >= ref]
    r["hotrod_isn3"] = {"eventos": len(do3), "ultimo_sucesso_s": rel(max(ok3) if ok3 else None),
                        "primeiro_erro_depois_do_pedido_s": rel(next((int(e["end_ns"]) for e in sorted(depois, key=lambda e: int(e["end_ns"])) if e["return_code"].startswith("ERROR")), None)),
                        "codigos_depois_do_pedido": dict(collections.Counter(e["return_code"] for e in depois))}

    st = json.load(open(os.path.join(d, "isn3-state.json"), encoding="utf-8")) if os.path.exists(os.path.join(d, "isn3-state.json")) else {}
    r["conteiner_isn3"] = {"status_no_fim": st.get("Status"), "exit_code": st.get("ExitCode"),
                           "finished_at": st.get("FinishedAt"), "started_at": st.get("StartedAt"),
                           "finished_s": rel(iso_ns(st.get("FinishedAt"))) if st.get("FinishedAt", "").startswith("20") else None}

    vista = next((x["t_ns"] for x in top if x["t_ns"] >= tc and any(n["respondeu"] and n["no"] != ISN3
                  and 0 <= n["numero_de_nos"] < 3 for n in x["por_no"])), None)
    r["instantes_s"] = {"t_lag": rel(t_lag), "t_P1": r["P1"][0]["t_decisao_s"] if p1 else None,
                        "t_AC5_pedido": r["AC5"][0]["t_pedido_s"] if ac5 else None,
                        "t_AC5_fim": r["AC5"][0]["t_fim_s"] if ac5 else None,
                        "t_saude_isn3_parou": r["saude_isn3"]["primeiro_sem_200_depois_do_pedido_s"] if ac5 else None,
                        "t_vista_maioria": rel(vista)}
    for k in ("fim-da-carga-visto", "religar-inicio", "religar-fim", "religar-3-nos", "cura-fim"):
        if k in f:
            r["instantes_s"][k] = rel(f[k][0])

    out = open(os.path.join(d, "stdout.log"), encoding="utf-8", errors="replace").read().splitlines()
    pega = lambda prefixo: [l for l in out if l.startswith(prefixo)]
    r["stdout"] = {"leitura_por_replica": pega("Leitura por replica"), "convergencia": pega("Convergencia:"),
                   "controlador": pega("Controlador:"), "violacoes": pega("Violacoes detectadas"),
                   "total_operacoes": pega("Total de operacoes"), "erros_s3": [l for l in out if l.startswith("S3 acao|") and "ERROR" in l]}
    r["exit"] = open(os.path.join(d, "exit.txt")).read().strip() if os.path.exists(os.path.join(d, "exit.txt")) else None

    conv = os.path.join(d, "convergencia-rep-001.csv")
    if os.path.exists(conv):
        linhas = list(csv.DictReader(open(conv, encoding="utf-8")))
        r["convergencia_csv"] = {"rodadas": len(linhas), "validas": sum(l["valida"] == "true" for l in linhas),
                                 "motivos": dict(collections.Counter(l["motivo_invalida"] for l in linhas)),
                                 "primeira_valida_s": rel(next((int(l["instante_ns"]) for l in linhas if l["valida"] == "true"), None)),
                                 "ultima_s": rel(int(linhas[-1]["instante_ns"])) if linhas else None}
    res = os.path.join(d, "convergencia-resumo-rep-001.json")
    if os.path.exists(res):
        r["convergencia_resumo"] = json.load(open(res, encoding="utf-8"))
    r["arquivos"] = {n: os.path.getsize(os.path.join(d, n)) for n in sorted(os.listdir(d))
                     if n.endswith((".csv", ".jsonl", ".json"))}
    r["jsonl_com_fim"] = {"observaveis": obs[-1]["tipo"] == "fim", "decisoes": dec[-1]["tipo"] == "fim"}
    viol = os.path.join(d, "violacoes-rep-001.csv")
    if os.path.exists(viol):
        vl = list(csv.DictReader(open(viol, encoding="utf-8")))
        col = next((c for c in (vl[0].keys() if vl else []) if c in ("invariante", "invariant", "tipo")), None)
        r["violacoes"] = {"total": len(vl), "por_invariante": dict(collections.Counter(v[col] for v in vl)) if col else None}
    return r


if __name__ == "__main__":
    print(json.dumps(main(sys.argv[1]), indent=1, ensure_ascii=False))
