"""B-43.7b: casos de borda de C3 pelo IC binomial de DT-008 (analise.c3_ic).

Uso (neste diretorio ou de qualquer lugar): python -I test_analise_c3.py
"""
import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analise  # noqa: E402

P0 = 0.05
Z = 3.2905


def meia(n):
    return Z * math.sqrt(P0 * (1 - P0) / n)


class C3PorIc(unittest.TestCase):

    def test_constantes(self):
        self.assertEqual(analise.P0, 0.05)
        self.assertEqual(analise.Z, 3.2905)

    def test_dentro(self):
        n = 245_204
        r = analise.c3_ic(round(n * P0), n, n, 4)
        self.assertTrue(r["ok"])
        self.assertTrue(r["ok_ic"])
        self.assertTrue(r["ok_csv"])
        self.assertIsNone(r["sanidade"])
        self.assertEqual(r["k"], round(n * P0))
        self.assertEqual(r["n"], n)
        self.assertEqual(r["z"], Z)
        self.assertEqual(r["dif_stdout_csv"], 0)

    def test_fora_por_cima(self):
        n = 1_000_000
        k = math.ceil(n * (P0 + meia(n))) + 1
        r = analise.c3_ic(k, n, n, 4)
        self.assertFalse(r["ok_ic"])
        self.assertFalse(r["ok"])
        self.assertGreater(r["desvio_pp"], r["meia_largura_pp"])

    def test_fora_por_baixo(self):
        n = 1_000_000
        k = math.floor(n * (P0 - meia(n))) - 1
        r = analise.c3_ic(k, n, n, 4)
        self.assertFalse(r["ok_ic"])
        self.assertFalse(r["ok"])
        self.assertLess(r["desvio_pp"], -r["meia_largura_pp"])

    def test_na_borda_dentro(self):
        n = 1_000_000
        k = math.floor(n * (P0 + meia(n)))
        self.assertTrue(analise.c3_ic(k, n, n, 4)["ok"])
        k = math.ceil(n * (P0 - meia(n)))
        self.assertTrue(analise.c3_ic(k, n, n, 4)["ok"])

    def test_n_insuficiente(self):
        n = 2000  # n * 0,05 * 0,95 = 95 < 100
        r = analise.c3_ic(100, n, n, 4)
        self.assertFalse(r["ok"])
        self.assertEqual(r["sanidade"], "n insuficiente para o IC")
        self.assertFalse(analise.c3_ic(0, 0, 0, 4)["ok"])
        n = 2106  # 100,04 >= 100
        self.assertIsNone(analise.c3_ic(round(n * P0), n, n, 4)["sanidade"])

    def test_meia_largura_n_245204(self):
        r = analise.c3_ic(12_260, 245_204, 245_204, 4)
        self.assertAlmostEqual(r["meia_largura_pp"], 0.145, delta=0.0005)
        self.assertAlmostEqual(r["meia_largura_pp"], 100 * meia(245_204), places=9)

    def test_fronteira_stdout_csv(self):
        n = 245_204
        k = round(n * P0)
        self.assertTrue(analise.c3_ic(k, n, n - 4, 4)["ok"])
        self.assertTrue(analise.c3_ic(k, n, n + 4, 4)["ok"])
        r = analise.c3_ic(k, n, n - 5, 4)
        self.assertFalse(r["ok_csv"])
        self.assertFalse(r["ok"])
        self.assertEqual(r["dif_stdout_csv"], 5)


if __name__ == "__main__":
    unittest.main()
