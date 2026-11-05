"""Testes de analysis/referencia_erro.py (B-39.4, W-5; plano de alinhamento 3.4, Delta_rec de sensibilidade).

p_ref+ = limite superior unilateral de 95 % de Clopper-Pearson; com zero erros, o mesmo numero da regra de
tres de comparar_bracos (1 - 0,05^(1/n)).

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scipy import stats

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
import referencia_erro as rerr  # noqa: E402

SCRIPT = AQUI / "referencia_erro.py"
S = 1_000_000_000
T0 = 1_761_566_400 * S
CAB_EV = "op_id,operation,start_ns,end_ns,replica,return_code,key"


def escrever_eventos(d, codigos, meta=2):
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    linhas = [CAB_EV] + ["%d,Login,%d,%d,isn1,%s,k%d" % (i, T0 + i * S, T0 + i * S + 1000, rc, i)
                         for i, rc in enumerate(codigos)]
    (d / "rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    if meta is not None:
        (d / "meta-csv-rep-001.json").write_text(json.dumps({"contrato_csv": meta}), encoding="utf-8")
    return d / "rep-001.csv"


class Limite(unittest.TestCase):

    def test_zero_erros_e_a_regra_de_tres(self):
        for n in (30, 1000, 123456):
            with self.subTest(n=n):
                self.assertAlmostEqual(cb.regra_de_tres(n)[0], rerr.limite_superior(0, n), places=15)

    def test_clopper_pearson(self):
        for x, n in ((1, 100), (5, 1000), (37, 20000)):
            with self.subTest(x=x, n=n):
                p = rerr.limite_superior(x, n)
                self.assertAlmostEqual(0.05, float(stats.binom.cdf(x, n, p)), places=9)
                self.assertGreater(p, x / n)

    def test_extremos(self):
        self.assertEqual(1.0, rerr.limite_superior(10, 10))
        for x, n in ((0, 0), (11, 10), (-1, 10)):
            with self.subTest(x=x, n=n):
                with self.assertRaises(rerr.ErroUso):
                    rerr.limite_superior(x, n)


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()


class Gerar(Base):

    def test_duas_execucoes(self):
        c1 = escrever_eventos(self.d / "e1", ["OK"] * 96 + ["ERROR_TRANSPORT"] * 2 + ["ERROR_UNAVAILABLE", "BLOCKED"])
        c2 = escrever_eventos(self.d / "e2", ["OK"] * 50)
        r = rerr.gerar([c1, c2])
        self.assertEqual("referencia-erro/1", r["esquema"])
        self.assertEqual(150, r["n_eventos"])
        self.assertEqual(3, r["n_erros"])
        self.assertEqual(rerr.limite_superior(3, 150), r["p_ref_sup"])
        self.assertEqual([100, 50], [e["n_eventos"] for e in r["execucoes"]])
        self.assertEqual([3, 0], [e["n_erros"] for e in r["execucoes"]])
        self.assertTrue(all(len(e["sha256"]) == 64 for e in r["execucoes"]))
        self.assertEqual(0.95, r["confianca"])

    def test_recusa_execucao_com_falha(self):
        c1 = escrever_eventos(self.d / "e1", ["OK"] * 10)
        (self.d / "e1" / "marcadores.txt").write_text("fault=F2 node=isn3 event=start t_epoch_ns=%d\n" % T0,
                                                      encoding="utf-8")
        with self.assertRaises(rerr.ErroUso):
            rerr.gerar([c1])

    def test_recusas_de_contrato(self):
        c1 = escrever_eventos(self.d / "e1", ["OK"] * 10)
        with self.assertRaises(rerr.ErroUso):
            rerr.gerar([c1, c1])
        with self.assertRaises(rerr.ErroUso):
            rerr.gerar([escrever_eventos(self.d / "e2", ["OK"] * 10, meta=None)])
        with self.assertRaises(rerr.ErroUso):
            rerr.gerar([])


class Ler(Base):

    def test_valida_e_invalidas(self):
        p = self.d / "ref.json"
        p.write_text(json.dumps(rerr.gerar([escrever_eventos(self.d / "e1", ["OK"] * 20)])), encoding="utf-8")
        r = rerr.ler(p)
        self.assertAlmostEqual(1 - 0.05 ** (1 / 20), r["p_ref_sup"], places=15)
        self.assertEqual(64, len(r["sha256"]))
        for j in ({"esquema": "referencia-p99/1", "p_ref_sup": 0.1}, {"esquema": "referencia-erro/1", "p_ref_sup": 0},
                  {"esquema": "referencia-erro/1", "p_ref_sup": 1.5}, {"esquema": "referencia-erro/1"}, [1, 2]):
            with self.subTest(j=j):
                p.write_text(json.dumps(j), encoding="utf-8")
                with self.assertRaises(rerr.ErroUso):
                    rerr.ler(p)
        with self.assertRaises(rerr.ErroUso):
            rerr.ler(self.d / "nao-existe.json")


class Cli(Base):

    def rodar(self, *args):
        r = subprocess.run([sys.executable, "-E", "-P", str(SCRIPT)] + [str(a) for a in args], capture_output=True,
                           text=True)
        return r.returncode, r.stderr

    def test_grava_e_nunca_sobrescreve(self):
        c1 = escrever_eventos(self.d / "e1", ["OK"] * 30 + ["ERROR_TRANSPORT"])
        saida = self.d / "ref.json"
        rc, err = self.rodar("--saida", saida, c1)
        self.assertEqual(0, rc, err)
        j = json.loads(saida.read_text(encoding="utf-8"))
        self.assertEqual(1, j["n_erros"])
        antes = saida.read_bytes()
        rc, _ = self.rodar("--saida", saida, c1)
        self.assertEqual(2, rc)
        self.assertEqual(antes, saida.read_bytes())

    def test_recusa_falha_sem_gravar(self):
        c1 = escrever_eventos(self.d / "e1", ["OK"] * 10)
        (self.d / "e1" / "marcadores.txt").write_text("fault=F1 node=isn2 event=start t_epoch_ns=%d\n" % T0,
                                                      encoding="utf-8")
        saida = self.d / "ref.json"
        rc, _ = self.rodar("--saida", saida, c1)
        self.assertEqual(2, rc)
        self.assertFalse(saida.exists())


if __name__ == "__main__":
    unittest.main()
