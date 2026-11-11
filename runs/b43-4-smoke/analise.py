"""B-43.4: contagem operation x return_code dos CSV do smoke e conferencia com a linha
'Desfechos Validate' do stdout (valid = Validate VALID, invalid = Validate INVALID)."""
import csv, json, re, sys
from collections import Counter
from pathlib import Path

AQUI = Path(__file__).resolve().parent
saida = {}
for q in ("Q1", "Q2"):
    c = Counter()
    with (AQUI / q / "rep-001.csv").open(newline="") as fh:
        for row in csv.DictReader(fh):
            c[row["operation"] + " " + row["return_code"]] += 1
    out = (AQUI / q / "stdout.log").read_text(encoding="utf-8", errors="replace")
    m = re.search(r"Desfechos Validate: valid=(\d+) invalid=(\d+) not_found=(\d+) erro=(\d+)", out)
    d = dict(zip(("valid", "invalid", "not_found", "erro"), map(int, m.groups())))
    reg = int(re.search(r"registrados=(\d+)", out).group(1))
    erro_csv = sum(v for k, v in c.items() if k.startswith("Validate ERROR_"))
    saida[q] = {
        "contrato_stdout": re.search(r"Contrato CSV: (v\d+)", out).group(1),
        "meta": json.loads((AQUI / q / "meta-csv-rep-001.json").read_text()),
        "linhas_csv": sum(c.values()), "registrados_stdout": reg,
        "desfechos_stdout": d,
        "csv_operation_return_code": dict(sorted(c.items())),
        "dif_valid": c["Validate VALID"] - d["valid"],
        "dif_invalid": c["Validate INVALID"] - d["invalid"],
        "dif_not_found": c["Validate NOT_FOUND"] - d["not_found"],
        "dif_erro": erro_csv - d["erro"],
        "validate_none": c["Validate NONE"],
    }
json.dump(saida, open(AQUI / "analise.json", "w", newline="\n"), indent=1)
print(json.dumps(saida, indent=1))
