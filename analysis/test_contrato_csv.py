#!/usr/bin/env python3
"""
analysis/test_contrato_csv.py
=============================

Teste de contrato (B-33, Q-9) entre o workload Java e o pipeline analysis/.

Roda com a biblioteca padrao:

    python -m unittest discover -s analysis -p "test_*.py"     (da raiz do repositorio)
    python -m unittest test_contrato_csv                        (de dentro de analysis/)

Cobre: (1) todo `return_code` que o Java pode emitir (lido do codigo-fonte de
OpResult.java) e sucesso ou erro conhecido; (2) ERROR_UNAVAILABLE e contado
como erro e discriminado em `taxa_indisponivel` sem alterar o resumo de
execucoes sem particao; (3) o cabecalho do CSV de eventos e o esperado e a coluna
`phase` (opcional) nao quebra a leitura; (4) CSVs auxiliares (violacoes,
convergencia, varredura) nao contam como execucoes.
"""
from __future__ import annotations

import re
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))

import percentis  # noqa: E402

FONTES = AQUI.parent / "workload" / "src" / "main" / "java" / "br" / "unipampa" / "tcc" / "session"
CABECALHO = "op_id,operation,start_ns,end_ns,replica,return_code,key"


def _linha(i: int, op: str, rc: str, inicio: int = 1000, dur: int = 500) -> str:
    return f"{i},{op},{inicio},{inicio + dur},isn1,{rc},k{i}"


def _escrever(dirpath: Path, nome: str, linhas: list[str], cab: str = CABECALHO) -> None:
    (dirpath / nome).write_text(cab + "\n" + "\n".join(linhas) + "\n")


class ContratoComJava(unittest.TestCase):

    def test_codigos_de_retorno_do_java_sao_conhecidos(self):
        fonte = (FONTES / "OpResult.java").read_text(encoding="utf-8")
        corpo = fonte[fonte.index("public String returnCode()"):]
        codigos = set(re.findall(r'return "([A-Z_]+)";', corpo))
        self.assertIn("ERROR_UNAVAILABLE", codigos)
        desconhecidos = codigos - percentis.SUCESSOS - percentis.ERROS_CONHECIDOS
        self.assertEqual(set(), desconhecidos, "return_code novo sem tratamento no analysis/")
        # exatamente os de erro estao fora de SUCESSOS
        self.assertEqual(percentis.ERROS_CONHECIDOS, codigos - percentis.SUCESSOS)

    def test_cabecalho_do_csv_de_eventos(self):
        fonte = (FONTES / "EventCsvWriter.java").read_text(encoding="utf-8")
        header = re.search(r'HEADER =\s*"([^"]+)"', fonte).group(1)
        self.assertEqual(CABECALHO, header)
        com_fase = re.search(r'HEADER_COM_FASE = HEADER \+ "([^"]+)"', fonte).group(1)
        self.assertEqual(",phase", com_fase)
        self.assertTrue(percentis.COLUNAS_EVENTO <= set(header.split(",")))

    def test_nomes_de_operacao_novos(self):
        fonte = (FONTES / "SessionOps.java").read_text(encoding="utf-8")
        self.assertIn('OP_BLOCK = "Block"', fonte)
        self.assertIn('OP_UNBLOCK = "Unblock"', fonte)


class EstatisticasComIndisponibilidade(unittest.TestCase):

    def test_error_unavailable_conta_como_erro_e_e_discriminado(self):
        amostras = [(500, "VALID")] * 6 + [(500, "ERROR_TRANSPORT")] * 2 + [(500, "ERROR_UNAVAILABLE")] * 2
        est = percentis.calcular_estatisticas(amostras)
        self.assertAlmostEqual(0.4, est["taxa_erro"])
        self.assertAlmostEqual(0.2, est["taxa_indisponivel"])

    def test_sem_indisponibilidade_o_resumo_nao_muda(self):
        est = percentis.calcular_estatisticas([(500, "VALID"), (600, "ERROR_TRANSPORT")])
        self.assertNotIn("taxa_indisponivel", est)
        self.assertEqual(
            {"n", "media_ns", "p50_ns", "p95_ns", "p99_ns", "p99_9_ns", "taxa_erro"}, set(est))

    def test_block_e_unblock_sao_operacoes_comuns(self):
        with tempfile.TemporaryDirectory() as d:
            dp = Path(d)
            _escrever(dp, "rep-001.csv", [
                _linha(0, "Block", "NONE"), _linha(1, "Block", "ERROR_UNAVAILABLE"),
                _linha(2, "Unblock", "NONE")])
            por_op = percentis.carregar_csvs(dp)
            self.assertEqual({"Block", "Unblock"}, set(por_op))
            est = percentis.calcular_estatisticas(por_op["Block"])
            self.assertAlmostEqual(0.5, est["taxa_erro"])


class LeituraDeCsv(unittest.TestCase):

    def test_coluna_phase_extra_e_aceita(self):
        with tempfile.TemporaryDirectory() as d:
            dp = Path(d)
            _escrever(dp, "rep-001.csv",
                      [_linha(0, "Validate", "VALID") + ",antes",
                       _linha(1, "Validate", "ERROR_UNAVAILABLE") + ",durante-pos-split"],
                      cab=CABECALHO + ",phase")
            por_op = percentis.carregar_csvs(dp)
            self.assertEqual(2, len(por_op["Validate"]))

    def test_csvs_auxiliares_nao_contam_como_execucoes(self):
        with tempfile.TemporaryDirectory() as d:
            dp = Path(d)
            _escrever(dp, "rep-001.csv", [_linha(0, "Validate", "VALID")])
            _escrever(dp, "rep-002.csv", [_linha(0, "Validate", "VALID")])
            _escrever(dp, "violacoes-rep-001.csv", ["2026-10-15T12:00:00Z,I1,s1,x"],
                      cab="instante,invariante,chave,mensagem")
            _escrever(dp, "convergencia-rep-001.csv", ["5,true,0,0,"],
                      cab="instante_ns,valida,chaves_divergentes_i5,chaves_divergentes_i6,motivo_invalida")
            _escrever(dp, "varredura-rep-001.csv", ["I1,s1,REAL,x"],
                      cab="invariante,chave,classificacao,detalhe")
            self.assertEqual(["rep-001.csv", "rep-002.csv"],
                             [p.name for p in percentis.csvs_de_eventos(dp)])

    def test_diretorio_so_com_auxiliares_e_erro(self):
        with tempfile.TemporaryDirectory() as d:
            dp = Path(d)
            _escrever(dp, "violacoes-rep-001.csv", ["2026-10-15T12:00:00Z,I1,s1,x"],
                      cab="instante,invariante,chave,mensagem")
            with self.assertRaises(FileNotFoundError):
                percentis.carregar_csvs(dp)


if __name__ == "__main__":
    unittest.main()
