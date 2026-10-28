"""Resume os brutos do smoke local do F1 (secao 4.1 de docs/prompt-f1-kill-2026-10-27.md).

Uso: python -I analisar_smoke.py <dir-do-smoke>  -> imprime JSON por ensaio e grava resumo.json.
Instante da injecao: linha 'isolando' (silencioso), 'kill <alvo>' (kill) ou 'stop <alvo>' (stop) do
injetor.log. Delta_det: primeira ISPN000094 (vista nova) sem isn2 em isn1 e em isn3 depois desse instante,
pelo carimbo do 'podman logs -t'.
"""
import json, re, sys
from datetime import datetime, timezone
from pathlib import Path

RE_INJ = re.compile(r"^\[(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3})Z\] inject-crash\.sh: (.*)$")
RE_PODMAN_TS = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(\.\d+)?(Z|[+-]\d\d:\d\d) (.*)$")


def t_inj(s):
    return datetime.strptime(s, "%Y-%m-%dT%H:%M:%S.%f").replace(tzinfo=timezone.utc).timestamp()


def t_podman(base, frac, tz):
    frac = (frac or ".0")[:7]
    tz = "+00:00" if tz == "Z" else tz
    return datetime.fromisoformat(base + frac + tz).timestamp()


def linhas_podman(arq):
    out = []
    if not arq.exists():
        return out
    for l in arq.read_text(encoding="utf-8", errors="replace").splitlines():
        m = RE_PODMAN_TS.match(l)
        if m:
            out.append((t_podman(m.group(1), m.group(2), m.group(3)), m.group(4)))
    return out


def ensaio(d):
    r = {"rotulo": d.name}
    txt = (d / "ensaio.txt").read_text(encoding="utf-8") if (d / "ensaio.txt").exists() else ""
    m = re.search(r"modo=(\w+)", txt)
    modo = m.group(1) if m else None
    r["modo"] = modo
    m = re.search(r"injetor_rc=(\d+)", txt)
    r["injetor_rc"] = int(m.group(1)) if m else None
    m = re.search(r"volta \(desde o fim do injetor\): (\d+) ms", txt)
    r["volta_s_desde_fim_injetor"] = int(m.group(1)) / 1000 if m else None
    if "FALHOU" in txt:
        r["volta_falhou"] = True
    ev = {}
    for l in (d / "injetor.log").read_text(encoding="utf-8").splitlines():
        m = RE_INJ.match(l)
        if not m:
            continue
        t, msg = t_inj(m.group(1)), m.group(2)
        if msg.startswith("isolando"):
            ev.setdefault("isolamento", t)
        elif re.search(r"\bkill f1s-isn2\b", msg) and "trap" not in msg:
            ev.setdefault("kill", t)
        elif re.search(r"\bstop f1s-isn2\b", msg):
            ev.setdefault("stop", t)
        elif re.search(r"\bstart f1s-isn2\b", msg) and "trap" not in msg:
            ev.setdefault("start", t)
        elif msg.startswith(("OK: F1", "FALHA: F1")):
            ev.setdefault("fim", t)
        m2 = re.search(r"ExitCode=(\S+)", msg)
        if m2:
            r["exitcode_injetor"] = m2.group(1)
    inj = ev.get("isolamento") or ev.get("kill") or ev.get("stop")
    r["t_injecao_utc"] = datetime.fromtimestamp(inj, timezone.utc).isoformat() if inj else None
    if ev.get("isolamento") and ev.get("kill"):
        r["isolamento_ate_kill_ms"] = round((ev["kill"] - ev["isolamento"]) * 1000, 1)
    est = (d / "estado-parado.txt").read_text(encoding="utf-8").strip() if (d / "estado-parado.txt").exists() else ""
    r["estado_parado"] = est
    det = {}
    for no in ("f1s-isn1", "f1s-isn3"):
        for t, l in linhas_podman(d / ("logs-%s.log" % no)):
            if inj and t >= inj and "ISPN000094" in l:
                vista = l.split("ISPN000094", 1)[1]
                if "isn2" not in vista:
                    det[no] = round(t - inj, 3)
                    break
    r["delta_det_s"] = det
    alvo = linhas_podman(d / "logs-f1s-isn2.log")
    t_start = ev.get("start")
    antiga = [l for t, l in alvo if t_start is None or t < t_start]
    nova = [l for t, l in alvo if t_start is not None and t >= t_start]
    r["vida_antiga"] = {c: any(c in l for l in antiga) for c in ("ISPN000390", "ISPN080002")}
    r["vida_nova"] = {c: any(c in l for l in nova) for c in ("ISPN000693", "ISPN000389", "ISPN080001")}
    if t_start is not None and ev.get("fim") and r["volta_s_desde_fim_injetor"] is not None:
        r["podman_start_retorno_s"] = round(ev["fim"] - t_start, 3)
        r["healthy_desde_start_s"] = round(ev["fim"] - t_start + r["volta_s_desde_fim_injetor"], 3)
    if t_start is not None:
        up = [t for t, l in alvo if t >= t_start and "ISPN080001" in l]
        r["isn2_ispn080001_desde_start_s"] = round(up[0] - t_start, 3) if up else None
    r["lck"] = (d / "lck.txt").read_text(encoding="utf-8").strip().splitlines() if (d / "lck.txt").exists() else None
    def ip(arq):
        if not arq.exists():
            return None
        m = re.search(r"f1s-isn2 ip=(\S+)", arq.read_text(encoding="utf-8"))
        return m.group(1) if m else None
    r["ip_isn2_antes"], r["ip_isn2_depois"] = ip(d / "antes.txt"), ip(d / "depois.txt")
    r["ip_br_link_depois"] = (d / "ip-br-link-depois.txt").read_text(encoding="utf-8").strip() \
        if (d / "ip-br-link-depois.txt").exists() else None
    died = []
    if (d / "events.jsonl").exists():
        for l in (d / "events.jsonl").read_text(encoding="utf-8", errors="replace").splitlines():
            try:
                e = json.loads(l)
            except ValueError:
                continue
            if e.get("Status") == "died":
                died.append(e.get("ContainerExitCode", e.get("Attributes", {}).get("containerExitCode")))
    r["events_died_exitcode"] = died
    return r


def main():
    base = Path(sys.argv[1])
    res = [ensaio(d) for d in sorted(base.iterdir()) if d.is_dir() and (d / "injetor.log").exists()]
    (base / "resumo.json").write_text(json.dumps(res, indent=1, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(res, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    main()
