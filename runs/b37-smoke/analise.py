"""Analise do smoke do coletor de recursos (B-37). Smoke local: nao e medida de energia.

Uso: python -I runs/b37-smoke/analise.py runs/b37-smoke > runs/b37-smoke/analise.json

Le S1/, S2/ e C0/ (recursos-rep-001.csv, meta, jsonl do podman stats), os logs do
coletor (S*-coletor.log) e as leituras laterais de cgroup (S*-cg-lateral-*.txt,
usage_usec do /sys/fs/cgroup/unified e /proc/stat), e avalia os criterios do
prompt do B-37. Na maquina WSL o cgroup e hibrido v1: as linhas cg: saem
erro:leitura (sem memory.current), entao a CPU por conteiner vem das fontes
pid: (processo java de cada conteiner) e, como evidencia lateral, do
usage_usec do cgroup lido antes e depois da janela.
"""
import csv
import json
import os
import re
import sys

BASE = sys.argv[1]
CLK_TCK_MAQ = 100  # getconf CLK_TCK dentro da maquina (ambiente-maquina.txt)


def ler_csv(caminho):
    with open(caminho, encoding="utf-8", newline="") as f:
        linhas = list(csv.reader(f))
    return linhas[0], linhas[1:]


def cpu_proc_stat(linha):
    c = linha.split()
    us, ni, sy, _id, _io, irq, sirq = (int(x) for x in c[1:8])
    return (us + ni + sy + irq + sirq) * 1_000_000 // CLK_TCK_MAQ


def lateral(caminho):
    d = {"cg": {}}
    for ln in open(caminho, encoding="utf-8"):
        ln = ln.strip()
        if ln.startswith("t_epoch_ns="):
            d["t"] = int(ln.split("=")[1])
        elif ln.startswith("cpu "):
            d["vm_cpu_usec"] = cpu_proc_stat(ln)
        elif ln.startswith("b37-"):
            nome = ln.split()[0]
            kv = dict(x.split("=") for x in ln.split()[1:])
            d["cg"][nome] = {k: int(v) for k, v in kv.items() if v}
    return d


def pct(x):
    return round(100.0 * x, 3)


def analisar(rot):
    d = os.path.join(BASE, rot)
    cab, linhas = ler_csv(os.path.join(d, "recursos-rep-001.csv"))
    meta = json.load(open(os.path.join(d, "recursos-meta-rep-001.json"), encoding="utf-8"))
    log = open(os.path.join(BASE, f"{rot}-coletor.log"), encoding="utf-8").read()
    exit_m = re.findall(r"exit=(\d+)", log)
    amostras = [r for r in linhas if r[1] != "coletor"]
    coletor = [r for r in linhas if r[1] == "coletor"]
    por_fonte = {}
    for r in amostras:
        por_fonte.setdefault(r[1], []).append(r)
    estados = {f: sorted({r[5] for r in rs}) for f, rs in por_fonte.items()}
    instantes = sorted({int(r[0]) for r in amostras})

    monot = {}
    delta = {}
    for f, rs in por_fonte.items():
        ok = [r for r in rs if r[5] == "ok"]
        if not ok:
            monot[f] = None
            continue
        cpus = [int(r[2]) for r in ok]
        m = all(b >= a for a, b in zip(cpus, cpus[1:]))
        if f == "vm":
            st = [int(r[4]) for r in ok]
            m = m and all(b >= a for a, b in zip(st, st[1:]))
        monot[f] = m
        dt_s = (int(ok[-1][0]) - int(ok[0][0])) / 1e9
        delta[f] = {"cpu_s": (cpus[-1] - cpus[0]) / 1e6, "janela_s": dt_s,
                    "nucleos": round((cpus[-1] - cpus[0]) / 1e6 / dt_s, 4) if dt_s else None,
                    "mem_bytes_ultima": int(ok[-1][3])}
    pids = [f for f in delta if f.startswith("pid:")]
    soma_pid = sum(delta[f]["cpu_s"] for f in pids)
    vm_cpu = delta["vm"]["cpu_s"]
    vm_steal = (int(por_fonte["vm"][-1][4]) - int(por_fonte["vm"][0][4])) / 1e6

    la = lateral(os.path.join(BASE, f"{rot}-cg-lateral-antes.txt"))
    ld = lateral(os.path.join(BASE, f"{rot}-cg-lateral-depois.txt"))
    jan_lat = (ld["t"] - la["t"]) / 1e9
    cg_lat = {n: (ld["cg"][n]["usage_usec"] - la["cg"][n]["usage_usec"]) / 1e6 for n in la["cg"]}
    soma_cg_lat = sum(cg_lat.values())
    vm_lat = (ld["vm_cpu_usec"] - la["vm_cpu_usec"]) / 1e6

    # podman stats: cpu_time acumulado (delta entre a 1a e a ultima linha ok) e
    # media do cpu_percent informado
    cj = os.path.join(d, "recursos-podman-rep-001.jsonl")
    stats = [json.loads(l) for l in open(cj, encoding="utf-8")] if os.path.exists(cj) else []
    ok_stats = [s for s in stats if s["ok"]]

    def seg(txt):
        m = re.match(r"([0-9.]+)(ms|s|m|h)?$", txt)
        v = float(m.group(1))
        return v * {"ms": 1e-3, "s": 1, "m": 60, "h": 3600, None: 1}[m.group(2)]

    comp = {}
    if len(ok_stats) >= 2:
        t0, t1 = ok_stats[0]["t_epoch_ns"], ok_stats[-1]["t_epoch_ns"]
        for c in ok_stats[0]["stats"]:
            n = c["name"]
            c1 = next(x for x in ok_stats[-1]["stats"] if x["name"] == n)
            dcpu = seg(c1["cpu_time"]) - seg(c["cpu_time"])
            # CPU do pid: no mesmo intervalo [t0, t1] (amostras do CSV com esses t)
            rs = {int(r[0]): int(r[2]) for r in por_fonte.get("pid:" + n.replace("b37-", ""), []) if r[5] == "ok"}
            dpid = (rs[t1] - rs[t0]) / 1e6 if t0 in rs and t1 in rs else None
            comp[n] = {
                "intervalo_s": (t1 - t0) / 1e9,
                "podman_cpu_time_delta_s": round(dcpu, 4),
                "pid_cpu_delta_s": dpid,
                "razao_pid_sobre_podman": round(dpid / dcpu, 4) if dpid and dcpu else None,
                "podman_cpu_percent_medio": round(sum(float(next(x for x in s["stats"] if x["name"] == n)["cpu_percent"].rstrip("%")) for s in ok_stats) / len(ok_stats), 3),
                "cg_lateral_nucleos_pct": pct(cg_lat[n] / jan_lat),
            }

    dur_col = (meta["fim_epoch_ns"] - meta["inicio_epoch_ns"]) / 1e9
    col_cpu_s = int(coletor[0][2]) / 1e6 if coletor and coletor[0][5] == "ok" else None
    return {
        "exit": exit_m,
        "cabecalho_ok": cab == ["t_epoch_ns", "fonte", "cpu_usec_acum", "mem_bytes", "steal_usec_acum", "estado"],
        "amostras_meta": meta["amostras"],
        "instantes_csv": len(instantes),
        "motivo_fim": meta["motivo_fim"],
        "atraso_max_ns": meta["atraso_max_ns"],
        "passo_entre_amostras_ns": {"min": min(b - a for a, b in zip(instantes, instantes[1:])),
                                    "max": max(b - a for a, b in zip(instantes, instantes[1:]))},
        "estados_por_fonte": estados,
        "monotonico_por_fonte_ok": monot,
        "delta_por_fonte": delta,
        "soma_pid_cpu_s": round(soma_pid, 4),
        "vm_cpu_s": vm_cpu,
        "razao_soma_pid_sobre_vm": round(soma_pid / vm_cpu, 4) if vm_cpu else None,
        "vm_steal_s": vm_steal,
        "lateral": {"janela_s": round(jan_lat, 3), "cg_cpu_s": cg_lat, "soma_cg_cpu_s": round(soma_cg_lat, 4),
                    "vm_cpu_s": vm_lat, "razao_soma_cg_sobre_vm": round(soma_cg_lat / vm_lat, 4) if vm_lat else None},
        "podman_stats": {"linhas": len(stats), "ok": len(ok_stats), "comparacao": comp},
        "coletor": {"cpu_s": col_cpu_s, "duracao_s": dur_col,
                    "pct_de_um_nucleo": pct(col_cpu_s / dur_col) if col_cpu_s is not None else None,
                    "rss_bytes": int(coletor[0][3]) if coletor and coletor[0][5] == "ok" else None},
    }


res = {"aviso": "Smoke local: nao e medida de energia.", "S1": analisar("S1"), "S2": analisar("S2"),
       "C0_sem_podman_stats": analisar("C0")}
s1, s2 = res["S1"], res["S2"]
crit = {
    "exit_0": s1["exit"] == ["0"] and s2["exit"] == ["0"],
    "amostras_60_mais_menos_2": all(58 <= x["amostras_meta"] <= 62 for x in (s1, s2)),
    "monotonicos": all(v is not False for x in (s1, s2) for v in x["monotonico_por_fonte_ok"].values()),
    "cpu_conteineres_S2_maior_que_S1_pid": s2["soma_pid_cpu_s"] > s1["soma_pid_cpu_s"],
    "cpu_conteineres_S2_maior_que_S1_cg_lateral": s2["lateral"]["soma_cg_cpu_s"] > s1["lateral"]["soma_cg_cpu_s"],
    "soma_pid_menor_igual_vm_mais_5pct": all(x["soma_pid_cpu_s"] <= 1.05 * x["vm_cpu_s"] for x in (s1, s2)),
    "soma_cg_lateral_menor_igual_vm_mais_5pct": all(x["lateral"]["soma_cg_cpu_s"] <= 1.05 * x["lateral"]["vm_cpu_s"] for x in (s1, s2)),
    "atraso_max_menor_200ms": all(x["atraso_max_ns"] < 200_000_000 for x in (s1, s2)),
    "C0_exit_0_e_60_amostras": res["C0_sem_podman_stats"]["exit"] == ["0"] and 58 <= res["C0_sem_podman_stats"]["amostras_meta"] <= 62,
    "cg_contrato_v2_legivel": False,  # cgroup v1 hibrido na maquina WSL: ver estados_por_fonte
}
res["criterios"] = crit
json.dump(res, sys.stdout, ensure_ascii=False, indent=1, sort_keys=True)
print()
