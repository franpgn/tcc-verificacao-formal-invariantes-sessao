"""Testes M1..M5 do gerador da matriz do rebaseline (scripts/rebaseline_matriz.py).

Pre-registro: docs/pre-registro-rebaseline-aprovado-2026-10-23.md (PR), secoes 2, 3 e 4; DT-009.
Uso: python -I scripts/test_rebaseline_matriz.py   (de qualquer diretorio)
"""
import hashlib
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import rebaseline_matriz as rm  # noqa: E402

CANONICA = [f"{c}-{m}-{f}" for c in ("S1", "S2") for m in ("sync", "async") for f in ("none", "F1", "F3")]

# M3: ordem CONGELADA das rodadas 0..10, calculada uma vez em 2026-10-23 com Python 3.12.10
# (random.Random(231023 * 1000 + r).shuffle da lista canonica). Se uma versao do Python mudar o
# shuffle, este teste falha e a matriz commitada (com o seu SHA-256) continua sendo a referencia.
ORDEM_CONGELADA = {
    0: ["S2-sync-F1", "S2-async-none", "S1-sync-F1", "S1-async-none", "S2-async-F1", "S1-async-F3",
        "S2-async-F3", "S2-sync-F3", "S1-async-F1", "S1-sync-F3", "S2-sync-none", "S1-sync-none"],
    1: ["S2-sync-F1", "S1-sync-F1", "S1-sync-none", "S2-sync-none", "S1-async-F3", "S1-async-none",
        "S2-async-none", "S1-sync-F3", "S2-async-F3", "S1-async-F1", "S2-sync-F3", "S2-async-F1"],
    2: ["S1-async-F3", "S2-sync-F1", "S1-async-none", "S1-sync-none", "S2-async-none", "S2-sync-none",
        "S1-sync-F1", "S2-sync-F3", "S2-async-F1", "S2-async-F3", "S1-async-F1", "S1-sync-F3"],
    3: ["S1-sync-F1", "S2-sync-F3", "S2-sync-none", "S1-sync-F3", "S2-async-none", "S2-async-F1",
        "S1-async-F1", "S2-sync-F1", "S1-async-none", "S1-sync-none", "S2-async-F3", "S1-async-F3"],
    4: ["S1-sync-F3", "S2-async-none", "S1-async-F1", "S1-sync-none", "S2-sync-F1", "S2-async-F3",
        "S1-async-F3", "S1-async-none", "S2-sync-none", "S2-async-F1", "S1-sync-F1", "S2-sync-F3"],
    5: ["S1-sync-none", "S1-async-none", "S2-sync-F1", "S2-async-F3", "S2-async-none", "S1-async-F3",
        "S2-sync-F3", "S2-async-F1", "S1-sync-F3", "S1-async-F1", "S2-sync-none", "S1-sync-F1"],
    6: ["S1-async-none", "S1-sync-F3", "S1-async-F1", "S2-sync-none", "S2-async-none", "S2-async-F3",
        "S2-async-F1", "S2-sync-F1", "S1-async-F3", "S1-sync-F1", "S2-sync-F3", "S1-sync-none"],
    7: ["S1-async-none", "S1-sync-none", "S2-sync-F1", "S1-async-F1", "S2-async-none", "S2-async-F3",
        "S2-sync-F3", "S1-async-F3", "S2-sync-none", "S2-async-F1", "S1-sync-F3", "S1-sync-F1"],
    8: ["S2-sync-F1", "S1-sync-F3", "S2-sync-F3", "S1-async-none", "S2-async-F1", "S1-async-F3",
        "S1-sync-F1", "S2-async-none", "S2-sync-none", "S1-sync-none", "S2-async-F3", "S1-async-F1"],
    9: ["S1-sync-none", "S1-async-none", "S2-sync-F3", "S2-async-none", "S2-async-F1", "S1-async-F3",
        "S1-sync-F1", "S2-sync-none", "S1-async-F1", "S2-async-F3", "S2-sync-F1", "S1-sync-F3"],
    10: ["S1-sync-F1", "S2-async-F1", "S1-async-F3", "S2-sync-F1", "S2-sync-none", "S1-sync-none",
         "S1-sync-F3", "S2-sync-F3", "S2-async-F3", "S2-async-none", "S1-async-F1", "S1-async-none"],
}

COLUNAS = ["i", "fase", "noite", "rodada", "posicao", "celula", "cenario", "modo", "falha", "seed"]


def ler_tsv(caminho):
    linhas = Path(caminho).read_text(encoding="utf-8").split("\n")
    assert linhas[-1] == "", "TSV deve terminar com \\n"
    cab = linhas[0].split("\t")
    return cab, [dict(zip(cab, l.split("\t"))) for l in linhas[1:-1]]


def gerar_arquivo(dirtmp, nome="matriz.tsv", ordem_seed=231023, s0=50000):
    saida = Path(dirtmp) / nome
    r = subprocess.run([sys.executable, "-I", str(AQUI / "rebaseline_matriz.py"), "gerar",
                        "--ordem-seed", str(ordem_seed), "--s0", str(s0), "--saida", str(saida)],
                       capture_output=True, text=True)
    return r, saida


class Matriz(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.r, self.saida = gerar_arquivo(self.tmp.name)
        self.assertEqual(self.r.returncode, 0, self.r.stderr)
        self.cab, self.linhas = ler_tsv(self.saida)

    def tearDown(self):
        self.tmp.cleanup()

    def test_constantes_do_pr(self):
        self.assertEqual(rm.ORDEM_SEED, 231023)
        self.assertEqual(rm.S0, 50000)
        self.assertEqual(rm.CELULAS, CANONICA)

    def test_m1_permutacoes_e_sementes(self):
        self.assertEqual(self.cab, COLUNAS)
        self.assertEqual(len(self.linhas), 132)
        por_rodada = {}
        for l in self.linhas:
            por_rodada.setdefault(int(l["rodada"]), []).append(l)
        self.assertEqual(sorted(por_rodada), list(range(11)))
        for r, ls in por_rodada.items():
            self.assertEqual(sorted(x["celula"] for x in ls), sorted(CANONICA), "rodada %d" % r)
            self.assertEqual([int(x["posicao"]) for x in ls], list(range(12)))
        idx = [int(l["i"]) for l in self.linhas]
        self.assertEqual(len(set(idx)), 132)
        self.assertEqual(sorted(idx), list(range(120)) + list(range(1000, 1012)))
        for l in self.linhas:
            i, r, p = int(l["i"]), int(l["rodada"]), int(l["posicao"])
            self.assertEqual(int(l["seed"]), 50000 + 100 * i)
            self.assertEqual(l["celula"], "%s-%s-%s" % (l["cenario"], l["modo"], l["falha"]))
            if r == 0:
                self.assertEqual((i, l["fase"], l["noite"]), (1000 + p, "piloto", "piloto"))
            else:
                self.assertEqual((i, l["fase"]), (12 * (r - 1) + p, "campanha"))
                self.assertEqual(l["noite"], "noite1" if r <= 5 else "noite2")
        # ordem de execucao: piloto primeiro, depois rodadas 1..10 em ordem
        self.assertEqual([int(l["rodada"]) for l in self.linhas],
                         [r for r in range(11) for _ in range(12)])

    def test_m1_sha256_impresso(self):
        sha = hashlib.sha256(self.saida.read_bytes()).hexdigest()
        self.assertIn(sha, self.r.stdout)

    def test_m2_determinismo(self):
        r2, s2 = gerar_arquivo(self.tmp.name, "outra.tsv")
        self.assertEqual(r2.returncode, 0)
        self.assertEqual(self.saida.read_bytes(), s2.read_bytes())

    def test_m3_ordem_congelada(self):
        for r in range(11):
            self.assertEqual([l["celula"] for l in self.linhas if int(l["rodada"]) == r], ORDEM_CONGELADA[r],
                             "rodada %d" % r)
        self.assertEqual(rm.ordem_da_rodada(231023, 0), ORDEM_CONGELADA[0])

    def test_m4_outra_ordem_seed(self):
        r2, s2 = gerar_arquivo(self.tmp.name, "outra-seed.tsv", ordem_seed=231024)
        self.assertEqual(r2.returncode, 0)
        _, l2 = ler_tsv(s2)
        difere = any([l["celula"] for l in l2 if int(l["rodada"]) == r] != ORDEM_CONGELADA[r] for r in range(11))
        self.assertTrue(difere)

    def test_m5_sementes_de_thread_unicas(self):
        vistos = {}
        for l in self.linhas:
            for tid in range(4):
                s = int(l["seed"]) + tid + 1 * 1_000_003
                self.assertNotIn(s, vistos, "colide com i=%s" % vistos.get(s))
                vistos[s] = l["i"]
        self.assertEqual(len(vistos), 132 * 4)

    def test_uso_invalido(self):
        r = subprocess.run([sys.executable, "-I", str(AQUI / "rebaseline_matriz.py")], capture_output=True, text=True)
        self.assertEqual(r.returncode, 2)


if __name__ == "__main__":
    unittest.main()
