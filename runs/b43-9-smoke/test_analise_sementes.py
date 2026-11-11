"""B-43.9: checagem de sanidade de sementes (R-S3 da V-B43.7b) em analise.checar_sementes.

Uso (de qualquer lugar): python test_analise_sementes.py
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analise  # noqa: E402


def cfg(seed, rep=1, cen="S1"):
    return ("Configuracao: Cli{scenario=%s, duracao=120s, ops=0, rep=%d, threads=4, seed=%d, "
            "csvDir=C:\\x, warmupMin=10s, dryRun=false}" % (cen, rep, seed))


class Sementes(unittest.TestCase):

    def test_pre_registro(self):
        self.assertEqual(analise.SEMENTES, {"Q1": 43900, "Q2": 44000, "Q3": 44100, "Q4": 44200,
                                            "Q5": 44300, "Q6": 44400})

    def test_le_seed_e_rep(self):
        self.assertEqual(analise.semente_e_rep(cfg(44100, 1)), (44100, 1))
        self.assertEqual(analise.semente_e_rep(cfg(-7, 3)), (-7, 3))

    def test_pares_distintos_passam(self):
        r = analise.checar_sementes({q: cfg(s) for q, s in analise.SEMENTES.items()})
        self.assertTrue(r["ok"])
        self.assertEqual(r["repetidos"], {})

    def test_mesma_semente_mesma_rep_falha(self):
        r = analise.checar_sementes({"Q3": cfg(42), "Q4": cfg(42), "Q5": cfg(44300)})
        self.assertFalse(r["ok"])
        self.assertEqual(r["repetidos"], {"42,1": ["Q3", "Q4"]})

    def test_mesma_semente_rep_diferente_passa(self):
        r = analise.checar_sementes({"Q3": cfg(42, 1), "Q4": cfg(42, 2)})
        self.assertTrue(r["ok"])

    def test_linha_sem_seed_falha(self):
        r = analise.checar_sementes({"Q3": "Configuracao: Cli{scenario=S1, rep=1}", "Q4": cfg(1)})
        self.assertFalse(r["ok"])
        self.assertEqual(len(r["erros"]), 1)
        r = analise.checar_sementes({"Q3": None})
        self.assertFalse(r["ok"])

    def test_nao_confunde_rep_com_outra_chave(self):
        # "rep=" nao pode casar dentro de outra palavra (por exemplo "csvDir=...rep=9")
        linha = cfg(5, 2).replace("csvDir=C:\\x", "csvDir=C:\\rep-001")
        self.assertEqual(analise.semente_e_rep(linha), (5, 2))


if __name__ == "__main__":
    unittest.main()
