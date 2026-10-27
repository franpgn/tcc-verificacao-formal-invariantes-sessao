#!/usr/bin/env python3
"""B-27, smoke local: confere o CSV de acoes S3 v2 (Q1) e o stdout de Q1/Q2. So biblioteca padrao.

Uso (da raiz do repositorio):
    python runs/b27-smoke/analise.py

Criterios (prompt B-27, secao 3, SMOKE LOCAL): HEADER novo; t_fim_ns >= t_ns em 100 %;
espera_portao_ns = 0 em 100 % (portao livre); "Violacoes detectadas: 0" em Q1 e Q2.
Grava runs/b27-smoke/analise.json. Smoke local: latencias nao sao evidencia.
"""
import csv
import hashlib
import json
import re
from pathlib import Path

AQUI = Path(__file__).resolve().parent
CABECALHO = ["t_ns", "ator", "operacao", "alvo", "resultado", "tentativas", "falha_percebida", "nota",
             "t_fim_ns", "espera_portao_ns"]


def sha256(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def acoes(p: Path) -> dict:
    with p.open(encoding="utf-8", newline="") as f:
        linhas = list(csv.reader(f))
    cab, dados = linhas[0], linhas[1:]
    dur = sorted(int(c[8]) - int(c[0]) for c in dados)
    por_op = {}
    for c in dados:
        por_op[c[2]] = por_op.get(c[2], 0) + 1
    return {
        "arquivo": str(p.relative_to(AQUI)).replace("\\", "/"),
        "sha256": sha256(p),
        "cabecalho_ok": cab == CABECALHO,
        "linhas": len(dados),
        "colunas_10": all(len(c) == 10 for c in dados),
        "t_fim_ge_t_ns": sum(1 for c in dados if int(c[8]) >= int(c[0])),
        "espera_zero": sum(1 for c in dados if c[9] == "0"),
        "t_fim_gt_t_ns": sum(1 for c in dados if int(c[8]) > int(c[0])),
        "duracao_ns_min": dur[0] if dur else None,
        "duracao_ns_max": dur[-1] if dur else None,
        "acoes_por_operacao": dict(sorted(por_op.items())),
    }


def stdout(p: Path) -> dict:
    s = p.read_text(encoding="utf-8", errors="replace")

    def achar(rx):
        m = re.search(rx, s)
        return m.group(0) if m else None

    return {
        "arquivo": str(p.relative_to(AQUI)).replace("\\", "/"),
        "violacoes": achar(r"Violacoes detectadas: \d+"),
        "auditor": achar(r"Auditor: i1_concorrentes=\d+ i4_concorrentes=\d+"),
        "auditor_total": achar(r"Auditor total: [^\n]*"),
        "total_operacoes": achar(r"Total de operacoes: \d+"),
    }


def main():
    q1 = acoes(AQUI / "Q1" / "s3-acoes-rep-001.csv")
    out = {
        "Q1_acoes_s3": q1,
        "Q1_stdout": stdout(AQUI / "Q1" / "stdout.log"),
        "Q2_stdout": stdout(AQUI / "Q2" / "stdout.log"),
        "criterios": {
            "header_novo": q1["cabecalho_ok"],
            "t_fim_ge_t_ns_100pct": q1["t_fim_ge_t_ns"] == q1["linhas"],
            "espera_zero_100pct": q1["espera_zero"] == q1["linhas"],
            "violacoes_zero_q1_q2": all(x["violacoes"] == "Violacoes detectadas: 0"
                                         for x in (stdout(AQUI / "Q1" / "stdout.log"),
                                                   stdout(AQUI / "Q2" / "stdout.log"))),
        },
        "nota": "Smoke local: latencias nao sao evidencia.",
    }
    (AQUI / "analise.json").write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(out["criterios"]))


if __name__ == "__main__":
    main()
