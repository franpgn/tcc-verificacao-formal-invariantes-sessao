"""Testes de analysis/poder.py (B-39.4, W-5; plano de alinhamento 3.3, item 2).

Aceite pedido no Prompt 2: poder ~ 0 com reducao 0 e ~ 1 com reducao de 100 % e q = 1. Acrescimo: com
reducao de 100 %, o poder e P(k >= 6) para k ~ Binomial(8, q), o que confere a ilustracao do plano 3.3
(0,96 para q = 0,9 e 0,14 para q = 0,5).

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from scipy import stats

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
import poder  # noqa: E402

SCRIPT = AQUI / "poder.py"
TAXAS = [0.5, 1.2, 2.0, 3.1, 0.8, 4.4, 1.9, 2.6]


class Lote(unittest.TestCase):

    def test_igual_a_permutacao_exata(self):
        rng = np.random.default_rng(11)
        d = rng.normal(0, 1, (60, 6))
        d[::7, :2] = 0.0
        d[5] = 0.0
        d[9] = [0.3] * 6
        for linha, p in zip(d, poder.p_troca_de_sinal_lote(d)):
            self.assertEqual(cb.permutacao_exata(linha), p)

    def test_lotes_maiores_que_o_bloco_interno(self):
        rng = np.random.default_rng(12)
        d = rng.normal(-0.2, 1, (poder.LOTE * 2 + 3, 4))
        p = poder.p_troca_de_sinal_lote(d)
        self.assertEqual(cb.permutacao_exata(d[-1]), p[-1])
        self.assertEqual(cb.permutacao_exata(d[poder.LOTE]), p[poder.LOTE])

    def test_n_maior_que_16(self):
        with self.assertRaises(poder.ErroUso):
            poder.p_troca_de_sinal_lote(np.zeros((2, 17)))


class Poder(unittest.TestCase):

    def test_reducao_zero_perto_de_zero(self):
        self.assertLessEqual(poder.poder_troca_de_sinal(TAXAS, 0.0, 8, 4000, 1), 0.06)

    def test_reducao_total_com_q_1(self):
        self.assertEqual(1.0, poder.poder_troca_de_sinal(TAXAS, 1.0, 8, 500, 2))

    def test_reducao_total_e_binomial_de_k(self):
        for q, taxas in ((0.9, [1.0] * 9 + [0.0]), (0.5, [1.0, 0.0])):
            with self.subTest(q=q):
                esperado = float(stats.binom.sf(5, 8, q))          # P(k >= 6)
                obtido = poder.poder_troca_de_sinal(taxas, 1.0, 8, 20000, 3)
                self.assertLess(abs(obtido - esperado), 0.015)
        self.assertAlmostEqual(0.9619, float(stats.binom.sf(5, 8, 0.9)), places=4)
        self.assertAlmostEqual(37 / 256, float(stats.binom.sf(5, 8, 0.5)), places=12)

    def test_braco_a_sem_violacao_nao_rejeita(self):
        self.assertEqual(0.0, poder.poder_troca_de_sinal([0.0] * 5, 1.0, 8, 200, 4))

    def test_alfa_de_holm_corta_k_6(self):
        # q = 1 e reducao total: k = 8 sempre; com 6 blocos p = 2/64 = 0,031 > 0,025
        self.assertEqual(0.0, poder.poder_troca_de_sinal(TAXAS, 1.0, 6, 200, 5, alfa=0.025))
        self.assertEqual(1.0, poder.poder_troca_de_sinal(TAXAS, 1.0, 7, 200, 5, alfa=0.025))

    def test_reprodutivel(self):
        a = poder.poder_troca_de_sinal(TAXAS, 0.4, 8, 300, 77)
        self.assertEqual(a, poder.poder_troca_de_sinal(TAXAS, 0.4, 8, 300, 77))

    def test_validacao(self):
        for args in (([], 0.5, 8, 10), ([-1.0], 0.5, 8, 10), (TAXAS, 1.5, 8, 10), (TAXAS, 0.5, 17, 10),
                     (TAXAS, 0.5, 0, 10), (TAXAS, 0.5, 8, 0), ([float("nan")], 0.5, 8, 10)):
            with self.subTest(args=args):
                with self.assertRaises(poder.ErroUso):
                    poder.poder_troca_de_sinal(*args, semente=1)
        with self.assertRaises(poder.ErroUso):
            poder.poder_troca_de_sinal(TAXAS, 0.5, 8, 10, 1, alfa=0.0)


class CorrelacaoPerfeita(unittest.TestCase):
    """R-6/P-7 da V-W5: y_B = (1 - r) y_A no mesmo sorteio e limite superior do poder (melhor caso)."""

    def test_k_minimo(self):
        self.assertEqual(6, poder.k_minimo(0.05))
        self.assertEqual(7, poder.k_minimo(0.025))
        self.assertEqual(7, poder.k_minimo(0.05 / 3))
        self.assertEqual(8, poder.k_minimo(0.0078125))

    def test_e_a_binomial_de_k(self):
        # todo d_b nao nulo tem o sinal da reducao: poder = P(K >= k_min), K ~ Binomial(n, q)
        for q, taxas in ((0.5, [1.0, 0.0]), (0.9, [2.0] * 9 + [0.0])):
            with self.subTest(q=q):
                obtido = poder.poder_troca_de_sinal(taxas, 0.3, 8, 20000, 6, modelo="correlacao-perfeita")
                esperado = float(stats.binom.sf(poder.k_minimo(0.05) - 1, 8, q))
                self.assertLess(abs(obtido - esperado), 0.015)

    def test_extremos(self):
        self.assertEqual(0.0, poder.poder_troca_de_sinal(TAXAS, 0.0, 8, 200, 7, modelo="correlacao-perfeita"))
        self.assertEqual(1.0, poder.poder_troca_de_sinal(TAXAS, 0.5, 8, 200, 7, modelo="correlacao-perfeita"))

    def test_modelo_invalido(self):
        with self.assertRaises(poder.ErroUso):
            poder.poder_troca_de_sinal(TAXAS, 0.5, 8, 10, 1, modelo="outro")


class Curva(unittest.TestCase):

    def test_estrutura_e_extremos(self):
        c = poder.curva_poder(TAXAS, 200, 9, m_celulas=2)
        self.assertEqual([8, 10, 12], c["blocos"])
        self.assertEqual([round(0.1 * i, 1) for i in range(11)], c["reducoes"])
        self.assertEqual(1.0, c["q_hat"])
        self.assertEqual(2, c["m_celulas"])
        self.assertEqual(0.025, c["alfa"])                      # alfa do primeiro passo do Holm (P-7)
        self.assertEqual({"independente", "correlacao-perfeita"}, set(c["poder"]))
        for modelo in ("independente", "correlacao-perfeita"):
            for n in ("8", "10", "12"):
                with self.subTest(modelo=modelo, n=n):
                    v = c["poder"][modelo][n]
                    self.assertEqual(11, len(v))
                    self.assertTrue(all(0.0 <= x <= 1.0 for x in v))
                    self.assertEqual(1.0, v[-1])
        for n in ("8", "10", "12"):
            self.assertEqual(1.0, c["poder"]["correlacao-perfeita"][n][1])     # q = 1, k = n >= 7

    def test_m_celulas_invalido(self):
        for m in (0, -1, 1.5):
            with self.subTest(m=m):
                with self.assertRaises(poder.ErroUso):
                    poder.curva_poder(TAXAS, 10, 1, m_celulas=m)


class Cli(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)
        self.taxas = self.d / "taxas.txt"
        self.taxas.write_text("# calibracao\n" + "\n".join(str(x) for x in TAXAS) + "\n\n", encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def rodar(self, *args):
        r = subprocess.run([sys.executable, "-E", "-P", str(SCRIPT)] + [str(a) for a in args], capture_output=True,
                           text=True)
        return r.returncode, r.stderr

    def test_grava(self):
        saida = self.d / "curva.json"
        rc, err = self.rodar("--taxas-a", self.taxas, "--semente", 1, "--n-sim", 100, "--saida", saida,
                             "--blocos", "8", "--m-celulas", 2)
        self.assertEqual(0, rc, err)
        j = json.loads(saida.read_text(encoding="utf-8"))
        self.assertEqual("poder-troca-de-sinal/2", j["esquema"])
        self.assertEqual([8], j["blocos"])
        self.assertEqual(len(TAXAS), j["n_taxas"])
        self.assertEqual(64, len(j["taxas_a"]["sha256"]))
        self.assertEqual(0.025, j["alfa"])
        self.assertEqual({"independente", "correlacao-perfeita"}, set(j["poder"]))

    def test_recusas(self):
        saida = self.d / "curva.json"
        saida.write_text("x", encoding="utf-8")
        rc, _ = self.rodar("--taxas-a", self.taxas, "--semente", 1, "--n-sim", 100, "--saida", saida,
                           "--m-celulas", 2)
        self.assertEqual(2, rc)
        self.assertEqual("x", saida.read_text(encoding="utf-8"))
        ruim = self.d / "ruim.txt"
        ruim.write_text("1.0\nabc\n", encoding="utf-8")
        rc, _ = self.rodar("--taxas-a", ruim, "--semente", 1, "--n-sim", 100, "--saida", self.d / "b.json",
                           "--m-celulas", 2)
        self.assertEqual(2, rc)
        self.assertFalse((self.d / "b.json").exists())
        # m do pre-registro e obrigatorio e sem padrao (P-7: o alfa e o do primeiro passo do Holm)
        rc, _ = self.rodar("--taxas-a", self.taxas, "--semente", 1, "--n-sim", 100, "--saida", self.d / "c.json")
        self.assertEqual(2, rc)
        self.assertFalse((self.d / "c.json").exists())


if __name__ == "__main__":
    unittest.main()
