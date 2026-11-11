"""Integracao passo 6: criterios do smoke S1 sync / S2 async sobre os brutos.

Uso: python -I analise.py (neste diretorio). Grava analise.json. Confere, por execucao:
exit; linhas 'Contrato CSV: v2' e 'Auditor:'; I1 = I4 = 0 (violacoes-rep-001.csv e linha
Auditor); CSV x 'Desfechos Validate' (valid = Validate VALID, invalid = Validate INVALID);
registrados = linhas do CSV; leitor-csv-eventos.json (LeitorCsvEventos) com o mesmo total;
verificador de sanidade com OK e exit 0.
"""
import csv, json, re
from collections import Counter
from pathlib import Path

AQUI = Path(__file__).resolve().parent
exits = dict(l.split() for l in (AQUI / "exits.txt").read_text().split("\n") if l.strip())
vexits = dict(l.split() for l in (AQUI / "verificador" / "exits.txt").read_text().split("\n") if l.strip())
leitor = json.loads((AQUI / "leitor-csv-eventos.json").read_text())
saida = {}
for q in ("Q1", "Q2"):
    c = Counter()
    with (AQUI / q / "rep-001.csv").open(newline="") as fh:
        for row in csv.DictReader(fh):
            c[row["operation"] + " " + row["return_code"]] += 1
    out = (AQUI / q / "stdout.log").read_text(encoding="utf-8", errors="replace")
    m = re.search(r"Desfechos Validate: valid=(\d+) invalid=(\d+) not_found=(\d+) erro=(\d+)", out)
    d = dict(zip(("valid", "invalid", "not_found", "erro"), map(int, m.groups())))
    a = re.search(r"Auditor: i1_concorrentes=(\d+) i4_concorrentes=(\d+)", out)
    ct = re.search(r"Contrato CSV: (v\d+)", out)
    with (AQUI / q / "violacoes-rep-001.csv").open(newline="") as fh:
        viol = Counter(r["invariante"] for r in csv.DictReader(fh))
    reg = int(re.search(r"registrados=(\d+)", out).group(1))
    erro_csv = sum(v for k, v in c.items() if k.startswith("Validate ERROR_"))
    san = json.loads((AQUI / "verificador" / f"{q}.sanidade.json").read_text(encoding="utf-8"))
    lq = leitor[f"{q}/rep-001.csv"]
    r = {
        "exit": int(exits[q]),
        "contrato_stdout": ct.group(1) if ct else None,
        "meta": json.loads((AQUI / q / "meta-csv-rep-001.json").read_text()),
        "linha_auditor": a.group(0) if a else None,
        "violacoes_detectadas_stdout": int(re.search(r"Violacoes detectadas: (\d+)", out).group(1)),
        "violacoes_por_invariante": dict(viol),
        "I1": viol["I1"], "I4": viol["I4"],
        "linhas_csv": sum(c.values()), "registrados_stdout": reg,
        "desfechos_stdout": d,
        "csv_operation_return_code": dict(sorted(c.items())),
        "dif_valid": c["Validate VALID"] - d["valid"],
        "dif_invalid": c["Validate INVALID"] - d["invalid"],
        "dif_not_found": c["Validate NOT_FOUND"] - d["not_found"],
        "dif_erro": erro_csv - d["erro"],
        "leitor_status": lq["status"], "leitor_eventos": lq["eventos"],
        "verificador_exit": int(vexits[q]), "verificador_veredito": san.get("veredito"),
    }
    r["criterios_ok"] = (r["exit"] == 0 and r["contrato_stdout"] == "v2" and a is not None
                         and r["I1"] == 0 and r["I4"] == 0
                         and (int(a.group(1)), int(a.group(2))) == (0, 0)
                         and r["dif_valid"] == 0 and r["dif_invalid"] == 0
                         and r["linhas_csv"] == reg == r["leitor_eventos"]
                         and r["leitor_status"] == "OK"
                         and r["verificador_exit"] == 0 and r["verificador_veredito"] == "OK")
    saida[q] = r
json.dump(saida, open(AQUI / "analise.json", "w", newline="\n"), indent=1)
print(json.dumps(saida, indent=1))
