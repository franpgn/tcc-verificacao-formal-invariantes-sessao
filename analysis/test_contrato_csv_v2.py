#!/usr/bin/env python3
"""
analysis/test_contrato_csv_v2.py
================================

B-43.4 (R4): contrato v2 da coluna `return_code` do CSV de eventos.

No contrato 1 o workload gravava `VALID` para todo Validate bem-sucedido (inclusive de
sessao INVALID) e `NONE` para chave ausente (mascaramento C4). No contrato 2 grava o
estado real (`VALID`/`INVALID`) e `NOT_FOUND`. Os dois sao desfechos de negocio, nao
erros. Um CSV do contrato 1 tem de continuar sumarizando exatamente como antes.

    python -m unittest discover -s analysis          (da raiz do repositorio)
"""
from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))

import percentis  # noqa: E402

FIXTURES = AQUI / "fixtures"
CABECALHO = "op_id,operation,start_ns,end_ns,replica,return_code,key"


def _escrever(dp: Path, nome: str, linhas: list[tuple[str, str]]) -> Path:
    corpo = [f"{i},{op},1000,{1500 + i},isn1,{rc},k{i}" for i, (op, rc) in enumerate(linhas)]
    p = dp / nome
    p.write_text(CABECALHO + "\n" + "\n".join(corpo) + "\n", newline="\n")
    return p


class DesfechosDeNegocio(unittest.TestCase):

    def test_invalid_e_not_found_sao_sucesso(self):
        self.assertIn("INVALID", percentis.SUCESSOS)
        self.assertIn("NOT_FOUND", percentis.SUCESSOS)
        # o contrato 1 continua aceito
        self.assertIn("VALID", percentis.SUCESSOS)
        self.assertIn("NONE", percentis.SUCESSOS)
        self.assertEqual(set(), percentis.SUCESSOS & percentis.ERROS_CONHECIDOS)

    def test_invalid_e_not_found_nao_contam_como_erro(self):
        est = percentis.calcular_estatisticas(
            [(500, "VALID"), (500, "INVALID"), (500, "NOT_FOUND"), (500, "NONE")])
        self.assertEqual(0.0, est["taxa_erro"])

    def test_csv_v2_le_e_so_conta_erros_reais(self):
        with tempfile.TemporaryDirectory() as d:
            dp = Path(d)
            _escrever(dp, "rep-001.csv", [
                ("Validate", "VALID"), ("Validate", "INVALID"), ("Validate", "NOT_FOUND"),
                ("Validate", "ERROR_TRANSPORT"), ("Logout", "NOT_FOUND"), ("Logout", "NONE"),
                ("ResetFailures", "NOT_FOUND"), ("Unblock", "NOT_FOUND")])
            por_op = percentis.carregar_csvs(dp)
            self.assertAlmostEqual(0.25, percentis.calcular_estatisticas(por_op["Validate"])["taxa_erro"])
            for op in ("Logout", "ResetFailures", "Unblock"):
                self.assertEqual(0.0, percentis.calcular_estatisticas(por_op[op])["taxa_erro"], op)


class Contrato1Regressao(unittest.TestCase):
    """Recortes (2 000 eventos) de CSV reais do contrato 1; o esperado foi gerado pelo
    percentis.py de 8ee2deb, antes de B-43.4 (`contrato1-sumarios-8ee2deb.json`)."""

    def test_csv_contrato1_da_o_mesmo_sumario(self):
        esperado = json.loads((FIXTURES / "contrato1-sumarios-8ee2deb.json").read_text())
        self.assertEqual(2, len(esperado))
        for nome, por_op_esperado in esperado.items():
            with tempfile.TemporaryDirectory() as d:
                shutil.copy(FIXTURES / nome, Path(d) / "rep-001.csv")
                por_op = percentis.carregar_csvs(Path(d))
                obtido = {op: percentis.calcular_estatisticas(a) for op, a in sorted(por_op.items())}
                self.assertEqual(por_op_esperado, json.loads(json.dumps(obtido)), nome)


class VersaoDoContrato(unittest.TestCase):

    def test_sem_meta_e_contrato_1(self):
        with tempfile.TemporaryDirectory() as d:
            p = _escrever(Path(d), "rep-001.csv", [("Validate", "VALID")])
            self.assertEqual(1, percentis.versao_contrato_csv(p))

    def test_com_meta_e_contrato_2(self):
        with tempfile.TemporaryDirectory() as d:
            dp = Path(d)
            p = _escrever(dp, "rep-003.csv", [("Validate", "INVALID")])
            (dp / "meta-csv-rep-003.json").write_text('{"contrato_csv":2}')
            self.assertEqual(2, percentis.versao_contrato_csv(p))
            # o meta nao e CSV de eventos
            self.assertEqual([p], percentis.csvs_de_eventos(dp))


if __name__ == "__main__":
    unittest.main()
