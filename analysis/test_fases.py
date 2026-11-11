"""Testes de analysis/fases.py (B-39.2): F1 paridade com Marcadores.faseDe e F2 cobertura dos intervalos.

Os casos de F1 sao copiados de workload/src/test/java/br/unipampa/tcc/session/MarcadoresTest.java (linha
citada em cada caso); "so start" nao tem caso de faseDe no teste Java e vem do codigo
(Marcadores.java:141, fimNs == null => DURANTE).

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import json
import random
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import fases  # noqa: E402

S = 1_000_000_000
# MarcadoresTest.java:22-28 (completo): start, split e end, W = 60 s
INI, SPL, FIM, W = 1_000_000_000_000, 1_040_000_000_000, 1_090_000_000_000, 60 * S


def completo(t, conv=None, div=False):
    return fases.fase_de(t, INI, SPL, FIM, W, conv, div)


class F1ParidadeMarcadores(unittest.TestCase):

    def test_completo_em_cada_intervalo(self):
        casos = [  # (t, esperado, linha de MarcadoresTest.java)
            (999_999_999_999, "antes", 58),
            (1_000_000_000_000, "durante-pre-split", 59),   # t == start
            (1_039_999_999_999, "durante-pre-split", 60),
            (1_040_000_000_000, "durante-pos-split", 61),   # t == split
            (1_090_000_000_000, "durante-pos-split", 62),   # t == end (durante fechado)
            (1_090_000_000_001, "recuperacao", 63),
            (1_150_000_000_000, "recuperacao", 64),         # t == fim + W
            (1_150_000_000_001, "pos-convergencia", 65),
        ]
        for t, esperado, linha in casos:
            with self.subTest(t=t, linha="MarcadoresTest.java:%d" % linha):
                self.assertEqual(esperado, completo(t))

    def test_sem_split(self):
        # MarcadoresTest.java:81-84: start 100, end 200, W 10 => 150 e "durante"
        self.assertEqual("durante", fases.fase_de(150, 100, None, 200, 10))
        self.assertEqual("durante", fases.fase_de(200, 100, None, 200, 10))      # fechado em end
        self.assertEqual("recuperacao", fases.fase_de(210, 100, None, 200, 10))  # end + W
        self.assertEqual("pos-convergencia", fases.fase_de(211, 100, None, 200, 10))

    def test_convergencia_depois_de_fim_mais_w(self):
        # MarcadoresTest.java:91-93
        self.assertEqual("recuperacao", completo(1_199_000_000_000, conv=1_200_000_000_000))
        self.assertEqual("recuperacao", completo(1_200_000_000_000, conv=1_200_000_000_000))
        self.assertEqual("pos-convergencia", completo(1_200_000_000_001, conv=1_200_000_000_000))

    def test_convergencia_antes_de_fim_mais_w_nao_encurta(self):
        # Marcadores.java:150-153: limite = max(fim + W, convergencia)
        self.assertEqual("recuperacao", completo(1_150_000_000_000, conv=1_100_000_000_000))

    def test_divergencia_persistente(self):
        # MarcadoresTest.java:99-100
        self.assertEqual("recuperacao", completo(9_000_000_000_000, div=True))

    def test_sem_start(self):
        # MarcadoresTest.java:106 (Marcadores.vazio())
        self.assertEqual("antes", fases.fase_de(123, None, None, None, 0))

    def test_so_start(self):
        # Marcadores.java:141 (fimNs == null => DURANTE); MarcadoresTest.java:42-45 so le o parse
        self.assertEqual("antes", fases.fase_de(4, 5, None, None, 0))
        self.assertEqual("durante", fases.fase_de(5, 5, None, None, 0))
        self.assertEqual("durante", fases.fase_de(10 ** 18, 5, None, None, 0))

    def test_rotulos(self):
        # MarcadoresTest.java:71-75 e :85
        self.assertEqual(("antes", "durante", "durante-pre-split", "durante-pos-split", "recuperacao",
                          "pos-convergencia"), fases.ROTULOS)


class LeituraMarcadores(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def escrever(self, texto):
        p = self.d / "marcadores.txt"
        p.write_text(texto, encoding="utf-8", newline="\n")
        return p

    def test_tres_eventos(self):
        # MarcadoresTest.java:24-26, 33-36
        p = self.escrever("fault=F2 node=isn3 event=start t_epoch_ns=1000000000000\n"
                          "fault=F2 node=isn3 event=split t_epoch_ns=1040000000000\n"
                          "fault=F2 node=isn3 event=end t_epoch_ns=1090000000000\n")
        self.assertEqual({"inicio": INI, "split": SPL, "fim": FIM}, fases.ler_marcadores(p))

    def test_vazias_comentarios_e_malformadas(self):
        # MarcadoresTest.java:42-51
        p = self.escrever("\n# comentario\nfault=F2 node=isn3 event=start t_epoch_ns=5\n")
        self.assertEqual({"inicio": 5, "split": None, "fim": None}, fases.ler_marcadores(p))
        for ruim in ("lixo sem pares\n", "fault=F2 event=start t_epoch_ns=abc\n",
                     "fault=F2 event=explodiu t_epoch_ns=1\n", "fault=F2 node=isn1 event=start\n"):
            with self.subTest(linha=ruim):
                with self.assertRaises(ValueError):
                    fases.ler_marcadores(self.escrever(ruim))

    def test_primeiro_start_ultimo_end(self):
        # Marcadores.java:110-112
        p = self.escrever("event=start t_epoch_ns=10\nevent=start t_epoch_ns=20\n"
                          "event=end t_epoch_ns=30\nevent=end t_epoch_ns=40\n")
        self.assertEqual({"inicio": 10, "split": None, "fim": 40}, fases.ler_marcadores(p))

    def test_arquivo_ausente(self):
        # MarcadoresTest.java:121-123
        self.assertEqual({"inicio": None, "split": None, "fim": None},
                         fases.ler_marcadores(self.d / "nao-existe.txt"))

    def test_convergencia(self):
        # ConvergenciaLoop.escreverResumo (:187-201): t_convergencia_abs_ns e divergencia_persistente
        p = self.d / "convergencia-resumo-rep-001.json"
        p.write_text(json.dumps({"t_fim_ns": 1, "convergiu": True, "divergencia_persistente": False,
                                 "t_convergencia_ns": 5, "t_convergencia_abs_ns": 1_200_000_000_000,
                                 "rodadas": 3, "rodadas_validas": 3}), encoding="utf-8")
        self.assertEqual((1_200_000_000_000, False), fases.ler_convergencia(p))
        p.write_text(json.dumps({"convergiu": False, "divergencia_persistente": True,
                                 "t_convergencia_ns": None, "t_convergencia_abs_ns": None}), encoding="utf-8")
        self.assertEqual((None, True), fases.ler_convergencia(p))
        self.assertEqual((None, False), fases.ler_convergencia(self.d / "ausente.json"))


class F2Intervalos(unittest.TestCase):

    def conferir_cobertura(self, ivs, t0, t1, args):
        self.assertTrue(ivs, "lista vazia")
        self.assertEqual(t0, ivs[0][1])
        self.assertEqual(t1, ivs[-1][2])
        for (_, a, b), (_, c, _) in zip(ivs, ivs[1:]):
            self.assertEqual(b, c, "lacuna ou sobreposicao entre intervalos")
        for rot, a, b in ivs:
            self.assertLess(a, b, "intervalo vazio")
            self.assertIn(rot, fases.ROTULOS)
            self.assertEqual(rot, fases.fase_de(a, *args), (rot, a, b))
            self.assertEqual(rot, fases.fase_de(b - 1, *args), (rot, a, b))
        for (r1, _, _), (r2, _, _) in zip(ivs, ivs[1:]):
            self.assertNotEqual(r1, r2, "intervalos vizinhos com o mesmo rotulo nao foram fundidos")

    def test_completo_esperado(self):
        t0, t1 = 990 * S, 1300 * S
        ivs = fases.intervalos_de_fase(t0, t1, INI, SPL, FIM, W)
        self.assertEqual([("antes", t0, INI), ("durante-pre-split", INI, SPL),
                          ("durante-pos-split", SPL, FIM + 1), ("recuperacao", FIM + 1, FIM + W + 1),
                          ("pos-convergencia", FIM + W + 1, t1)], ivs)
        self.conferir_cobertura(ivs, t0, t1, (INI, SPL, FIM, W, None, False))

    def test_varias_configuracoes(self):
        configs = [
            (None, None, None, W, None, False),
            (INI, None, None, W, None, False),
            (INI, None, FIM, W, None, False),
            (INI, SPL, FIM, W, 1_200_000_000_000, False),
            (INI, SPL, FIM, W, 1_100_000_000_000, False),
            (INI, SPL, FIM, W, None, True),
            (INI, INI - 5, FIM, W, None, False),      # split antes do inicio: pre-split vazio
            (INI, FIM + 5, FIM, W, None, False),      # split depois do fim: pos-split vazio
        ]
        janelas = [(990 * S, 1300 * S), (1_010 * S, 1_020 * S), (1_095 * S, 1_160 * S),
                   (900 * S, 950 * S), (FIM, FIM + 1), (INI, FIM + W + 2)]
        rng = random.Random(39)
        for args in configs:
            for t0, t1 in janelas:
                with self.subTest(args=args, t0=t0, t1=t1):
                    ivs = fases.intervalos_de_fase(t0, t1, *args)
                    self.conferir_cobertura(ivs, t0, t1, args)
                    for _ in range(200):
                        t = rng.randrange(t0, t1)
                        rot = [r for r, a, b in ivs if a <= t < b]
                        self.assertEqual(1, len(rot))
                        self.assertEqual(fases.fase_de(t, *args), rot[0])

    def test_janela_vazia_e_erro(self):
        with self.assertRaises(ValueError):
            fases.intervalos_de_fase(10, 10, None, None, None, 0)


class ExpandirFases(unittest.TestCase):

    def test_durante_inclui_metades(self):
        self.assertEqual({"durante", "durante-pre-split", "durante-pos-split", "recuperacao"},
                         fases.expandir_fases(["durante", "recuperacao"]))
        self.assertEqual({"antes"}, fases.expandir_fases(["antes"]))
        with self.assertRaises(ValueError):
            fases.expandir_fases(["durantee"])


if __name__ == "__main__":
    unittest.main()
