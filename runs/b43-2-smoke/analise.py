"""Criterios do diagnostico 4.3 (B-43.2, item 4) sobre os brutos do smoke. Uso: analise.py <dir>"""
import csv, json, re, sys, collections, pathlib
R = pathlib.Path(sys.argv[1])
out = {}
for q in ["Q1", "Q2", "Q3", "Q4", "Q5"]:
    d = R / q
    cont = collections.Counter()
    with open(d / "rep-001.csv", newline="", encoding="utf-8") as f:
        rd = csv.DictReader(f)
        for row in rd:
            cont[(row["operation"], row["return_code"])] += 1
    so = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
    tot = int(re.search(r"Total de operacoes: (\d+)", so).group(1))
    reg, desc = map(int, re.search(r"registrados=(\d+), descartados_warmup=(\d+)", so).groups())
    reset = re.search(r"Reset: .*", so).group(0)
    carga = re.search(r"Carga: .*", so)
    desf = re.search(r"Desfechos Validate: valid=(\d+) invalid=(\d+) not_found=(\d+) erro=(\d+)", so)
    viol = int(re.search(r"Violacoes detectadas: (\d+)", so).group(1))
    login = sum(v for (o, _), v in cont.items() if o == "Login")
    login_ok = cont[("Login", "OK")]
    val = sum(v for (o, _), v in cont.items() if o == "Validate")
    val_valid = cont[("Validate", "VALID")]
    val_none = cont[("Validate", "NONE")]
    ops_csv = sorted({o for (o, _) in cont})
    ident = reg + desc - tot
    a, b, c, e = map(int, desf.groups())
    r = {
        "contagens": {f"{o} {rc}": v for (o, rc), v in sorted(cont.items())},
        "operacoes_no_csv": ops_csv,
        "total_operacoes": tot, "registrados": reg, "descartados_warmup": desc,
        "validate_valid_csv": val_valid,
        "login_ok_sobre_login": login_ok / login if login else None,
        "validate_none_sobre_validate": val_none / val if val else None,
        "eventos_sobre_operacoes": (reg + desc) / tot,
        "identidade_eventos_menos_ops": ident, "logins_ok_total_pela_identidade": ident / 2,
        "reset": reset, "carga": carga.group(0) if carga else None,
        "desfechos_validate": {"valid": a, "invalid": b, "not_found": c, "erro": e,
                               "fracao_invalid": b / (a + b + c + e) if (a + b + c + e) else None},
        "desfechos_soma_vs_validate_csv": [a + b + c + e, val],
        "violacoes": viol,
    }
    if q != "Q5":
        r["criterios"] = {
            "validate_valid_maior_0": val_valid > 0,
            "login_ok_ge_0_99": r["login_ok_sobre_login"] >= 0.99,
            "validate_none_le_0_01": r["validate_none_sobre_validate"] <= 0.01,
            "eventos_sobre_ops_gt_1": r["eventos_sobre_operacoes"] > 1,
            "identidade_gt_0": ident > 0,
            "so_cinco_operacoes_da_mistura": set(ops_csv) <= {"Login", "Validate", "Logout", "IncrementFailure", "ResetFailures"},
        }
    else:
        r["criterios"] = {"login_ok_igual_0": login_ok == 0}
    cj = d / "carga-rep-001.json"
    if cj.exists():
        r["carga_json"] = json.loads(cj.read_text())
    r["estado_json"] = json.loads((d / "estado-rep-001.json").read_text())
    out[q] = r
json.dump(out, open(R / "analise.json", "w", encoding="utf-8"), indent=1, ensure_ascii=False)
for q, r in out.items():
    print(q, r["criterios"], "L.OK/L=%.4f" % (r["login_ok_sobre_login"] or 0), "V.NONE/V=%.4f" % r["validate_none_sobre_validate"],
          "ev/ops=%.3f" % r["eventos_sobre_operacoes"], "ident=%d" % r["identidade_eventos_menos_ops"], "VALID=%d" % r["validate_valid_csv"],
          r["desfechos_validate"], r["desfechos_soma_vs_validate_csv"], r["operacoes_no_csv"])
