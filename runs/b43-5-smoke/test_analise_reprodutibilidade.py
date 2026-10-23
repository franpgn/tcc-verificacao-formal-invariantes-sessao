"""B-43.5: testes (unittest) das funcoes de reprodutibilidade e da R-S3 com isencao de analise.py.

Uso: python -I test_analise_reprodutibilidade.py -v   (o arquivo poe o proprio diretorio no sys.path).
"""
import random
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analise  # noqa: E402

UUID_A = "885243ad-824e-43ab-ade7-fa2f3ca37710"


def sequencia(n, semente):
    """Sequencia sintetica de (operation, key) com chaves sid- e UUID mascarados."""
    r = random.Random(semente)
    ops = ["Validate", "Logout", "Login", "IncrementFailure"]
    out = []
    for _ in range(n):
        op = r.choice(ops)
        if op == "Login":
            out.append((op, "user-0-%d" % r.randrange(8)))
        elif op == "IncrementFailure":
            out.append((op, "lim-0"))
        else:
            out.append((op, "sid-%06d~%d" % (r.randrange(100_000), r.randrange(3))))
    return out


class Mascara(unittest.TestCase):

    def test_uuid_mascarado(self):
        self.assertEqual("*", analise.mascarar(UUID_A))

    def test_sid_nao_mascarado(self):
        self.assertEqual("sid-002472~2", analise.mascarar("sid-002472~2"))
        self.assertEqual("sid-" + UUID_A, analise.mascarar("sid-" + UUID_A))
        self.assertEqual("user-0-2", analise.mascarar("user-0-2"))
        self.assertEqual("", analise.mascarar(""))


class Sobreposicao(unittest.TestCase):

    def setUp(self):
        self.base = sequencia(60_000 + 900, 1)

    def test_sobreposicao_igual_b_comeca_antes(self):
        a = self.base[700:]
        b = self.base[:60_500]
        r = analise.sobreposicao(a, b, k=256, minimo=50_000)
        self.assertTrue(r["ok"], r)
        self.assertEqual(700, r["deslocamento"])
        self.assertEqual(60_500 - 700, r["sobreposicao"])

    def test_sobreposicao_igual_a_comeca_antes(self):
        a = self.base[:60_000]
        b = self.base[300:]
        r = analise.sobreposicao(a, b, k=256, minimo=50_000)
        self.assertTrue(r["ok"], r)
        self.assertEqual("inicio de b encontrado em a", r["direcao"])
        self.assertEqual(300, r["deslocamento"])
        self.assertEqual(60_000 - 300, r["sobreposicao"])

    def test_um_evento_trocado_falha(self):
        a = list(self.base[700:])
        b = list(self.base[:60_500])
        b[30_000] = ("Validate", "sid-999999~9")
        r = analise.sobreposicao(a, b, k=256, minimo=50_000)
        self.assertFalse(r["ok"])
        self.assertEqual(1, r["diferentes"])
        self.assertEqual(30_000 - 700, r["primeira_diferenca"])

    def test_sem_sobreposicao_falha(self):
        a = sequencia(60_000, 2)
        b = sequencia(60_000, 3)
        r = analise.sobreposicao(a, b, k=256, minimo=50_000)
        self.assertFalse(r["ok"])
        self.assertIsNone(r["deslocamento"])
        self.assertEqual(0, analise.janelas_comuns(a, b, k=256))

    def test_sobreposicao_curta_falha(self):
        a = self.base[:30_000]
        b = self.base[100:]
        r = analise.sobreposicao(a, b, k=256, minimo=50_000)
        self.assertFalse(r["ok"])
        self.assertEqual(0, r["diferentes"])

    def test_janelas_comuns_conta_janela_compartilhada(self):
        a = sequencia(5_000, 4)
        b = sequencia(5_000, 5)
        b[1000:1300] = a[2000:2300]
        self.assertEqual(300 - 256 + 1, analise.janelas_comuns(a, b, k=256))


class SementesComIsencao(unittest.TestCase):

    @staticmethod
    def cfg(seed, rep=1):
        return "Configuracao: Cli{scenario=S1, rep=%d, threads=1, seed=%d}" % (rep, seed)

    def test_par_isento_aceito(self):
        r = analise.checar_sementes({"P1": self.cfg(44500), "P2": self.cfg(44500), "P3": self.cfg(44600)})
        self.assertTrue(r["ok"], r)
        self.assertIn("44500,1", r["repetidos_isentos"])

    def test_outro_repetido_recusado(self):
        r = analise.checar_sementes({"P1": self.cfg(44500), "P2": self.cfg(44500), "P3": self.cfg(44700),
                                     "P4": self.cfg(44700)})
        self.assertFalse(r["ok"])

    def test_terceiro_no_grupo_isento_recusado(self):
        r = analise.checar_sementes({"P1": self.cfg(44500), "P2": self.cfg(44500), "P3": self.cfg(44500)})
        self.assertFalse(r["ok"])


if __name__ == "__main__":
    unittest.main()
