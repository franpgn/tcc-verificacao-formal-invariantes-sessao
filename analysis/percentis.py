#!/usr/bin/env python3
"""
analysis/percentis.py
=====================

Realiza B-16 do backlog: calcula percentis de latência por operação
sobre os CSVs produzidos pelo programa de carga.

Formato CSV esperado por execução (uma linha por operação):

    op_id,operation,start_ns,end_ns,replica,return_code,key

A latência por operação é (end_ns - start_ns), em nanossegundos.

Linha T13 da Tabela 1 do Cap. 3 §3.3.5: reportar p50, p95, p99 como
obrigatórios; p99,9 quando a contagem total da operação alcançar
1 000 000 de amostras (regra estatística: pelo menos 10^3 observações
na cauda para um percentil estável).

Saída: imprime um resumo em stdout e grava runs/<cenário>/summary.json
com a estrutura:

    {
      "cenario": "<nome>",
      "execucoes": <int, número de CSVs lidos>,
      "operacoes": {
          "<nome_operacao>": {
              "n": <int>,
              "media_ns": <float>,
              "p50_ns": <int>,
              "p95_ns": <int>,
              "p99_ns": <int>,
              "p99_9_ns": <int|null>,
              "taxa_erro": <float entre 0 e 1>,
              "taxa_indisponivel": <float, so se houver ERROR_UNAVAILABLE (B-33)>
          },
          ...
      }
    }

Uso:
    python3 percentis.py runs/<cenário>
    python3 percentis.py runs/S1-sem-falha
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path
from collections import defaultdict

import numpy as np


# B-43.4: contrato CSV v2. INVALID (Validate de sessao invalidada) e NOT_FOUND (chave ou
# identidade ausente) sao desfechos de negocio, nao erros. VALID e NONE continuam, de modo que um
# CSV do contrato 1 (OK_VALIDATE sempre VALID, ausente como NONE) sumariza exatamente como antes.
# Ver docs/contrato-csv-v2.md.
SUCESSOS = {"OK", "VALID", "INVALID", "COUNTED", "ALREADY_INVALID", "BLOCKED",
            "ALREADY_BLOCKED", "NOT_BLOCKED", "NONE", "NOT_FOUND"}

# B-33 (Q-9): dois codigos de erro conhecidos. ERROR_UNAVAILABLE e a recusa por
# indisponibilidade (cache DEGRADED sob DENY_READ_WRITES); ERROR_TRANSPORT e a
# falha de transporte (timeout, conexao). Ambos entram em taxa_erro, pois nao
# estao em SUCESSOS; execucoes sem falha de particao nunca emitem o primeiro.
ERROS_CONHECIDOS = {"ERROR_TRANSPORT", "ERROR_UNAVAILABLE"}
CODIGO_INDISPONIVEL = "ERROR_UNAVAILABLE"

# Colunas minimas do CSV de eventos. O workload acrescenta a coluna `phase`
# quando recebe --marker (B-33); colunas extras sao aceitas.
COLUNAS_EVENTO = {"operation", "start_ns", "end_ns", "return_code"}


def csvs_de_eventos(diretorio: Path) -> list[Path]:
    """CSVs do diretorio que sao de eventos (cabecalho com as colunas minimas).

    Outros CSVs gravados junto (violacoes-rep-*, convergencia-rep-*,
    varredura-*) ficam de fora e nao inflam `execucoes`.
    """
    escolhidos = []
    for csvfile in sorted(diretorio.glob("*.csv")):
        with csvfile.open() as fh:
            cabecalho = fh.readline().strip().split(",")
        if COLUNAS_EVENTO <= set(cabecalho):
            escolhidos.append(csvfile)
    return escolhidos


def versao_contrato_csv(csvfile: Path) -> int:
    """Versao do contrato da coluna `return_code` de um `rep-NNN.csv` (B-43.4).

    2 se existir `meta-csv-rep-NNN.json` ao lado com `contrato_csv`; 1 caso contrario (CSV
    anterior a B-43.4). Os dois contratos sao lidos pelo mesmo `SUCESSOS`.
    """
    meta = csvfile.with_name("meta-csv-" + csvfile.stem + ".json")
    if not meta.exists():
        return 1
    return int(json.loads(meta.read_text()).get("contrato_csv", 1))


def carregar_csvs(diretorio: Path) -> dict[str, list[tuple[float, str]]]:
    """Lê todos os CSVs do diretório e agrega por operação.

    Retorna: dict { operacao -> lista de (latencia_ns, return_code) }.
    """
    if not diretorio.exists():
        raise FileNotFoundError(f"diretório não encontrado: {diretorio}")

    csvs = csvs_de_eventos(diretorio)
    if not csvs:
        raise FileNotFoundError(f"nenhum CSV de eventos em {diretorio}")

    por_operacao: dict[str, list[tuple[float, str]]] = defaultdict(list)

    for csvfile in csvs:
        with csvfile.open() as fh:
            reader = csv.DictReader(fh)
            for row in reader:
                try:
                    inicio = int(row["start_ns"])
                    fim = int(row["end_ns"])
                    latencia = fim - inicio
                    if latencia < 0:
                        continue
                    por_operacao[row["operation"]].append((latencia, row["return_code"]))
                except (KeyError, ValueError):
                    continue

    return por_operacao


def calcular_estatisticas(amostras: list[tuple[float, str]]) -> dict:
    if not amostras:
        return {}
    latencias = np.array([a[0] for a in amostras], dtype=np.int64)
    codigos = [a[1] for a in amostras]
    n = len(latencias)
    erros = sum(1 for c in codigos if c not in SUCESSOS and not c.startswith("UNKNOWN"))

    resultado = {
        "n": int(n),
        "media_ns": float(np.mean(latencias)),
        "p50_ns": int(np.percentile(latencias, 50, method="lower")),
        "p95_ns": int(np.percentile(latencias, 95, method="lower")),
        "p99_ns": int(np.percentile(latencias, 99, method="lower")),
        "p99_9_ns": int(np.percentile(latencias, 99.9, method="lower")) if n >= 1_000_000 else None,
        "taxa_erro": erros / n,
    }
    # B-33: so aparece quando ha recusas por indisponibilidade, de modo que o
    # summary.json de execucoes sem particao e identico ao anterior.
    indisponiveis = sum(1 for c in codigos if c == CODIGO_INDISPONIVEL)
    if indisponiveis:
        resultado["taxa_indisponivel"] = indisponiveis / n
    return resultado


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Calcula percentis de latência por operação para um cenário."
    )
    parser.add_argument(
        "diretorio",
        type=Path,
        help="Diretório com os CSVs de uma execução (ex.: runs/S1-sem-falha)",
    )
    parser.add_argument(
        "--cenario",
        type=str,
        default=None,
        help="Nome do cenário (default: nome do diretório)",
    )
    args = parser.parse_args()

    cenario = args.cenario or args.diretorio.name
    por_operacao = carregar_csvs(args.diretorio)

    saida = {
        "cenario": cenario,
        "execucoes": len(csvs_de_eventos(args.diretorio)),
        "operacoes": {},
    }

    for op_nome, amostras in sorted(por_operacao.items()):
        saida["operacoes"][op_nome] = calcular_estatisticas(amostras)

    summary_path = args.diretorio / "summary.json"
    summary_path.write_text(json.dumps(saida, indent=2, ensure_ascii=False) + "\n")

    print(f"Cenário: {cenario}")
    print(f"CSVs lidos: {saida['execucoes']}")
    versoes = sorted({versao_contrato_csv(p) for p in csvs_de_eventos(args.diretorio)})
    print("Contrato CSV: " + ", ".join(f"v{v}" for v in versoes))
    print(f"Operações com amostras: {len(saida['operacoes'])}")
    for op, stats in saida["operacoes"].items():
        p999 = stats["p99_9_ns"]
        p999_str = "—" if p999 is None else f"{p999/1e6:.3f}ms"
        print(
            f"  {op:<20} n={stats['n']:>10,d}  "
            f"p50={stats['p50_ns']/1e6:.3f}ms  "
            f"p95={stats['p95_ns']/1e6:.3f}ms  "
            f"p99={stats['p99_ns']/1e6:.3f}ms  "
            f"p99.9={p999_str:<10}  "
            f"erros={stats['taxa_erro']*100:.2f}%"
        )

    print(f"\nResumo gravado em {summary_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
