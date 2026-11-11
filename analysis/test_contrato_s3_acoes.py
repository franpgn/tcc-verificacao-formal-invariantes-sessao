#!/usr/bin/env python3
"""
analysis/test_contrato_s3_acoes.py
==================================

Teste de contrato (B-27) do CSV de acoes do cenario S3, versao 2
(`docs/contrato-s3-acoes-v2.md`): `s3-acoes-rep-NNN.csv` ganhou `t_fim_ns` e
`espera_portao_ns` no fim; as 8 primeiras colunas nao mudaram.

So biblioteca padrao. Roda com:

    python -m unittest discover -s analysis -p "test_*.py"     (da raiz do repositorio)

Cobre: (1) o cabecalho declarado em AcaoS3.java e o do contrato; (2) o
validador aceita linhas boas e recusa cada violacao (colunas, ordem dos
instantes, espera negativa, latencia sem espera negativa, int64); (3) a fixture
recortada do smoke local do B-27 (`fixtures/s3-acoes-v2-exemplo.csv`) cumpre o
contrato.
"""
from __future__ import annotations

import csv
import io
import re
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent
ACAO_S3 = RAIZ / "workload" / "src" / "main" / "java" / "br" / "unipampa" / "tcc" / "session" / "AcaoS3.java"
FIXTURE = AQUI / "fixtures" / "s3-acoes-v2-exemplo.csv"

CABECALHO = [
    "t_ns", "ator", "operacao", "alvo", "resultado", "tentativas", "falha_percebida", "nota",
    "t_fim_ns", "espera_portao_ns",
]
CABECALHO_V1 = CABECALHO[:8]
INT64_MAX = 2 ** 63 - 1
INT64_MIN = -(2 ** 63)


def _int64(texto: str, coluna: str, n: int) -> int:
    if not re.fullmatch(r"-?[0-9]+", texto):
        raise ValueError(f"linha {n}: {coluna} nao e inteiro: {texto!r}")
    v = int(texto)
    if not INT64_MIN <= v <= INT64_MAX:
        raise ValueError(f"linha {n}: {coluna} fora de int64: {v}")
    return v


def validar(texto: str) -> int:
    """Valida um CSV de acoes S3 v2; devolve o numero de linhas de dados ou levanta ValueError."""
    leitor = csv.reader(io.StringIO(texto))
    linhas = list(leitor)
    if not linhas or linhas[0] != CABECALHO:
        raise ValueError(f"cabecalho diferente do contrato: {linhas[0] if linhas else None}")
    for n, campos in enumerate(linhas[1:], start=2):
        if len(campos) != len(CABECALHO):
            raise ValueError(f"linha {n}: {len(campos)} colunas, esperadas {len(CABECALHO)}")
        t = _int64(campos[0], "t_ns", n)
        _int64(campos[5], "tentativas", n)
        if campos[6] not in ("true", "false"):
            raise ValueError(f"linha {n}: falha_percebida invalida: {campos[6]!r}")
        fim = _int64(campos[8], "t_fim_ns", n)
        espera = _int64(campos[9], "espera_portao_ns", n)
        if fim < t:
            raise ValueError(f"linha {n}: t_fim_ns < t_ns")
        if espera < 0:
            raise ValueError(f"linha {n}: espera_portao_ns negativa")
        if fim - t - espera < 0:
            raise ValueError(f"linha {n}: latencia sem espera negativa")
    return len(linhas) - 1


class TestContratoS3AcoesV2(unittest.TestCase):

    def test_cabecalho_no_java(self):
        fonte = ACAO_S3.read_text(encoding="utf-8")
        m = re.search(r'HEADER\s*=\s*"([^"]+)"', fonte)
        self.assertIsNotNone(m, "HEADER nao encontrado em AcaoS3.java")
        self.assertEqual(m.group(1).split(","), CABECALHO)
        self.assertEqual(CABECALHO[:8], CABECALHO_V1, "as 8 primeiras colunas sao as do contrato 1")

    def test_validador_aceita_linhas_boas(self):
        texto = ",".join(CABECALHO) + "\n" \
            + "1800000000000000000,admin,Block,s3-u-001,OK_STATUS,1,false,,1800000030500000000,30000000000\n" \
            + "1800000000000000001,s3-u-000,Validate,sid,OK_VALIDATE:VALID,1,false,,1800000000000000001,0\n"
        self.assertEqual(validar(texto), 2)

    def test_validador_recusa_violacoes(self):
        base = ",".join(CABECALHO) + "\n"
        ruins = {
            "colunas": "1,a,b,c,d,1,false,,2\n",
            "ordem": "10,a,b,c,d,1,false,,9,0\n",
            "espera negativa": "10,a,b,c,d,1,false,,20,-1\n",
            "latencia sem espera": "10,a,b,c,d,1,false,,20,11\n",
            "int64": f"10,a,b,c,d,1,false,,{INT64_MAX + 1},0\n",
            "inteiro": "10,a,b,c,d,1,false,,2.5,0\n",
            "booleano": "10,a,b,c,d,1,talvez,,20,0\n",
        }
        for nome, linha in ruins.items():
            with self.subTest(nome):
                with self.assertRaises(ValueError):
                    validar(base + linha)
        with self.assertRaises(ValueError):
            validar(",".join(CABECALHO_V1) + "\n10,a,b,c,d,1,false,\n")

    @unittest.skipUnless(FIXTURE.exists(), "fixture do smoke B-27 ainda nao gravada (commit 6)")
    def test_fixture_do_smoke(self):
        texto = FIXTURE.read_text(encoding="utf-8")
        n = validar(texto)
        self.assertGreater(n, 0)
        self.assertLessEqual(n, 200)
        linhas = list(csv.reader(io.StringIO(texto)))[1:]
        # smoke com o portao livre: nenhuma espera; e a acao dura (t_fim > t_ns em quase todas)
        self.assertTrue(all(c[9] == "0" for c in linhas), "portao livre no smoke: espera 0")
        self.assertTrue(any(int(c[8]) > int(c[0]) for c in linhas), "t_fim_ns registra o fim real")


if __name__ == "__main__":
    unittest.main()
