"""Testes do gerador da matriz das campanhas S3 (scripts/campanha_s3_matriz.py), W-6 / B-46.

Plano de execucao de 04/11, secao 9 (W-6, item 1) e secao 5.2: calibracao so com o braco A; avaliacao em blocos
A/B com a mesma semente, ordem dos bracos sorteada dentro do bloco, nucleo antes das extras; semente de ordem e S0
por argumento obrigatorio (o pre-registro fixa); TSV com SHA-256. Reposicoes: na calibracao, cada execucao perdida
com o proximo i livre; na avaliacao, o bloco com execucao perdida e refeito inteiro, com o proximo bloco livre
(plano 5.2); uma semente que ja alimentou o workload nunca volta (R-S1 do rebaseline).

Uso: python -I scripts/test_campanha_s3_matriz.py   (de qualquer diretorio)
"""
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import campanha_s3_matriz as cm  # noqa: E402

NUCLEO = "ASYNC-F1:teste:8,ASYNC-F2:teste:8,ASYNC-none:descritiva:4,SYNC-F2:referencia:4"
EXTRAS = "ASYNC-F4:teste:8,SYNC-F1:referencia:4,SYNC-F4:referencia:4"
CALIB = "ASYNC-none:calibracao:5,ASYNC-F1:calibracao:5,ASYNC-F2:calibracao:5,SYNC-F2:calibracao:3"


def rodar(*args):
    r = subprocess.run([sys.executable, "-I", str(AQUI / "campanha_s3_matriz.py")] + list(args),
                       capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def ler_tsv(p):
    linhas = Path(p).read_text(encoding="ascii").split("\n")
    assert linhas[-1] == ""
    cab = linhas[0].split("\t")
    return cab, [dict(zip(cab, l.split("\t"))) for l in linhas[1:-1]]


def gerar_avaliacao(d, nome="m.tsv", ordem=51107, s0=70000, i0=0, b0=1, extras=EXTRAS):
    saida = Path(d) / nome
    rc, out, err = rodar("gerar", "--modo", "avaliacao", "--nucleo", NUCLEO, "--extras", extras,
                         "--ordem-seed", str(ordem), "--s0", str(s0), "--passo-semente", "100",
                         "--i0", str(i0), "--b0", str(b0), "--saida", str(saida))
    assert rc == 0, err
    return saida, out


def gerar_calibracao(d, nome="c.tsv", ordem=51106, s0=60000, i0=0):
    saida = Path(d) / nome
    rc, out, err = rodar("gerar", "--modo", "calibracao", "--celulas", CALIB, "--ordem-seed", str(ordem),
                         "--s0", str(s0), "--passo-semente", "100", "--i0", str(i0), "--saida", str(saida))
    assert rc == 0, err
    return saida, out


def checagem(saida, linha, veredito, motivos=None):
    d = Path(saida) / ("exec-%04d-%s-%s" % (int(linha["i"]), linha["celula"], linha["braco"]))
    d.mkdir(parents=True, exist_ok=True)
    (d / "checagem.json").write_text(json.dumps({"i": int(linha["i"]), "veredito": veredito,
                                                 "motivos": motivos or []}), encoding="utf-8")


class Gerar(unittest.TestCase):

    def test_colunas_e_sha(self):
        with tempfile.TemporaryDirectory() as d:
            saida, out = gerar_avaliacao(d)
            cab, _ = ler_tsv(saida)
            self.assertEqual(list(cm.COLUNAS), cab)
            sha = hashlib.sha256(saida.read_bytes()).hexdigest()
            self.assertIn(sha, out)

    def test_avaliacao_blocos_pareados(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_avaliacao(d)[0])
            blocos = {}
            for x in linhas:
                blocos.setdefault(x["bloco"], []).append(x)
            n = sum(int(c.split(":")[2]) for c in (NUCLEO + "," + EXTRAS).split(","))
            self.assertEqual(n, len(blocos))
            for b, xs in blocos.items():
                self.assertEqual(2, len(xs), b)
                self.assertEqual({"A", "B"}, {x["braco"] for x in xs})
                self.assertEqual({"shadow", "active"}, {x["controlador"] for x in xs})
                self.assertEqual({"A": "shadow", "B": "active"}, {x["braco"]: x["controlador"] for x in xs})
                self.assertEqual(1, len({x["seed"] for x in xs}), "mesma semente no bloco")
                self.assertEqual(1, len({x["celula"] for x in xs}))
                self.assertEqual(int(xs[0]["seed"]), 70000 + 100 * int(b))
                self.assertEqual(1, abs(int(xs[0]["i"]) - int(xs[1]["i"])), "bracos seguidos")

    def test_nucleo_antes_das_extras(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_avaliacao(d)[0])
            fases = [x["fase"] for x in linhas]
            self.assertEqual(sorted(fases, key=lambda f: 0 if f == "nucleo" else 1), fases)
            nuc = {x["celula"] for x in linhas if x["fase"] == "nucleo"}
            ext = {x["celula"] for x in linhas if x["fase"] == "extra"}
            self.assertEqual({"ASYNC-F1", "ASYNC-F2", "ASYNC-none", "SYNC-F2"}, nuc)
            self.assertEqual({"ASYNC-F4", "SYNC-F1", "SYNC-F4"}, ext)

    def test_rodadas_completas(self):
        """Cada rodada tem um bloco de cada celula que ainda tem blocos (blocos completos sorteados)."""
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_avaliacao(d)[0])
            por_rodada = {}
            for x in linhas:
                if x["braco"] == "A":
                    por_rodada.setdefault(int(x["rodada"]), []).append(x["celula"])
            self.assertEqual(sorted(["ASYNC-F1", "ASYNC-F2", "ASYNC-none", "SYNC-F2"]), sorted(por_rodada[1]))
            self.assertEqual(sorted(["ASYNC-F1", "ASYNC-F2"]), sorted(por_rodada[5]))
            self.assertEqual(sorted(["ASYNC-F4", "SYNC-F1", "SYNC-F4"]), sorted(por_rodada[9]))

    def test_reprodutivel_e_depende_da_semente(self):
        with tempfile.TemporaryDirectory() as d:
            a, _ = gerar_avaliacao(d, "a.tsv")
            b, _ = gerar_avaliacao(d, "b.tsv")
            c, _ = gerar_avaliacao(d, "c.tsv", ordem=51108)
            self.assertEqual(a.read_bytes(), b.read_bytes())
            self.assertNotEqual(a.read_bytes(), c.read_bytes())

    def test_sementes_e_indices_unicos(self):
        with tempfile.TemporaryDirectory() as d:
            _, av = ler_tsv(gerar_avaliacao(d)[0])
            self.assertEqual(len(av), len({x["i"] for x in av}))
            self.assertEqual(len(av) // 2, len({x["seed"] for x in av}))
            self.assertEqual(list(range(0, len(av))), [int(x["i"]) for x in av])
            _, ca = ler_tsv(gerar_calibracao(d)[0])
            self.assertEqual(len(ca), len({x["seed"] for x in ca}))
            for x in ca:
                self.assertEqual(60000 + 100 * int(x["i"]), int(x["seed"]))

    def test_ordem_dos_bracos_varia_entre_blocos(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_avaliacao(d)[0])
            primeiros = [x["braco"] for x in linhas if x["posicao"].endswith(".0")]
            self.assertIn("A", primeiros)
            self.assertIn("B", primeiros)

    def test_calibracao_so_braco_a(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_calibracao(d)[0])
            self.assertEqual(18, len(linhas))
            self.assertEqual({"A"}, {x["braco"] for x in linhas})
            self.assertEqual({"shadow"}, {x["controlador"] for x in linhas})
            self.assertEqual({"calibracao"}, {x["fase"] for x in linhas})
            self.assertEqual({"-"}, {x["bloco"] for x in linhas})
            cont = {}
            for x in linhas:
                cont[x["celula"]] = cont.get(x["celula"], 0) + 1
            self.assertEqual({"ASYNC-none": 5, "ASYNC-F1": 5, "ASYNC-F2": 5, "SYNC-F2": 3}, cont)
            self.assertEqual({"async", "sync"}, {x["modo"] for x in linhas})

    def test_argumentos_obrigatorios(self):
        with tempfile.TemporaryDirectory() as d:
            base = ["gerar", "--modo", "avaliacao", "--nucleo", NUCLEO, "--extras", EXTRAS,
                    "--passo-semente", "100", "--i0", "0", "--b0", "1", "--saida", str(Path(d) / "x.tsv")]
            self.assertEqual(2, rodar(*base, "--s0", "1")[0], "sem --ordem-seed")
            self.assertEqual(2, rodar(*base, "--ordem-seed", "1")[0], "sem --s0")
            sem_b0 = [a for a in base if a not in ("--b0", "1")]
            self.assertEqual(2, rodar(*sem_b0, "--ordem-seed", "1", "--s0", "1")[0], "sem --b0")

    def test_celulas_invalidas(self):
        # W-6c: F3 passou a ser falha da matriz; F5 e combinacoes de falhas continuam invalidas (uma falha por execucao)
        for ruim in ("ASYNC-F5:teste:8", "ASYNC-F3+F4:teste:8", "ASYNC-F2F3:teste:8", "ASYNC-F1:outro:8",
                     "ASYNC-F1:teste:0", "ASYNC-F1:teste", "XSYNC-F1:teste:8", "ASYNC-F1:teste:8,ASYNC-F1:teste:8"):
            with self.subTest(ruim=ruim):
                with self.assertRaises(cm.ErroUso):
                    cm.ler_celulas(ruim, "avaliacao")
        with self.assertRaises(cm.ErroUso):
            cm.ler_celulas("ASYNC-F1:teste:8", "calibracao")
        with self.assertRaises(cm.ErroUso):
            cm.ler_celulas("ASYNC-F1:calibracao:5", "avaliacao")

    def test_celula_f3_descritiva_nas_extras(self):
        """W-6c: a celula ASYNC F3 descritiva de 4 blocos nas extras (B-40, secao 4 e B-1): F3 na coluna falha, uma
        falha por linha, blocos A/B pareados como os outros."""
        with tempfile.TemporaryDirectory() as d:
            saida, _ = gerar_avaliacao(d, extras=EXTRAS + ",ASYNC-F3:descritiva:4")
            _, linhas = ler_tsv(saida)
            f3 = [x for x in linhas if x["celula"] == "ASYNC-F3"]
            self.assertEqual(8, len(f3))
            self.assertEqual({"F3"}, {x["falha"] for x in f3})
            self.assertEqual({"extra"}, {x["fase"] for x in f3})
            self.assertEqual({"descritiva"}, {x["papel"] for x in f3})
            por_bloco = {}
            for x in f3:
                por_bloco.setdefault(x["bloco"], []).append(x)
            self.assertEqual(4, len(por_bloco))
            for b in por_bloco.values():
                self.assertEqual(["A", "B"], sorted(x["braco"] for x in b))
                self.assertEqual(1, len({x["seed"] for x in b}))
            self.assertEqual(set(cm.FALHAS), {"none", "F1", "F2", "F3", "F4"})


class Repor(unittest.TestCase):

    def test_calibracao_repoe_execucao_perdida(self):
        with tempfile.TemporaryDirectory() as d:
            m, _ = gerar_calibracao(d)
            _, linhas = ler_tsv(m)
            saida = Path(d) / "saida"
            for x in linhas:
                checagem(saida, x, "ACEITA")
            checagem(saida, linhas[3], "RECUSADA", ["sem ancora"])
            checagem(saida, linhas[7], "DESCARTADA-INFRA", ["h4: h4a"])
            rc, out, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "51106",
                                 "--s0", "60000", "--passo-semente", "100", "--teto-h4", "5")
            self.assertEqual(0, rc, err)
            _, rep = ler_tsv(saida / "reposicoes" / "reposicoes.tsv")
            self.assertEqual(2, len(rep))
            self.assertEqual([linhas[3]["i"], linhas[7]["i"]], [x["repoe"] for x in rep])
            self.assertEqual(["18", "19"], [x["i"] for x in rep])
            self.assertEqual([linhas[3]["celula"], linhas[7]["celula"]], [x["celula"] for x in rep])
            self.assertEqual({"reposicoes"}, {x["fase"] for x in rep})
            for x in rep:
                self.assertEqual(60000 + 100 * int(x["i"]), int(x["seed"]))
            # rodar de novo nao duplica
            rc, _, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "51106",
                               "--s0", "60000", "--passo-semente", "100", "--teto-h4", "5")
            self.assertEqual(0, rc, err)
            self.assertEqual(2, len(ler_tsv(saida / "reposicoes" / "reposicoes.tsv")[1]))

    def test_avaliacao_refaz_o_bloco_inteiro(self):
        with tempfile.TemporaryDirectory() as d:
            m, _ = gerar_avaliacao(d, extras="SYNC-F1:referencia:1")
            _, linhas = ler_tsv(m)
            saida = Path(d) / "saida"
            for x in linhas:
                checagem(saida, x, "ACEITA")
            perdida = linhas[5]
            checagem(saida, perdida, "RECUSADA", ["interrompida (DT-014)"])
            rc, out, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "51107",
                                 "--s0", "70000", "--passo-semente", "100", "--teto-h4", "5")
            self.assertEqual(0, rc, err)
            _, rep = ler_tsv(saida / "reposicoes" / "reposicoes.tsv")
            self.assertEqual(2, len(rep), "os dois bracos do bloco")
            self.assertEqual({"A", "B"}, {x["braco"] for x in rep})
            novo = max(int(x["bloco"]) for x in linhas) + 1
            self.assertEqual({str(novo)}, {x["bloco"] for x in rep})
            self.assertEqual({perdida["bloco"]}, {x["repoe"] for x in rep})
            self.assertEqual({str(70000 + 100 * novo)}, {x["seed"] for x in rep})
            self.assertEqual({perdida["celula"]}, {x["celula"] for x in rep})
            usadas = {x["seed"] for x in linhas}
            self.assertFalse(usadas & {x["seed"] for x in rep}, "semente nova")

    def test_execucao_pendente_recusa(self):
        with tempfile.TemporaryDirectory() as d:
            m, _ = gerar_calibracao(d)
            _, linhas = ler_tsv(m)
            saida = Path(d) / "saida"
            for x in linhas[:-1]:
                checagem(saida, x, "ACEITA")
            rc, _, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "51106",
                               "--s0", "60000", "--passo-semente", "100", "--teto-h4", "5")
            self.assertEqual(3, rc)
            self.assertIn("pendente", err)

    def test_teto_h4(self):
        with tempfile.TemporaryDirectory() as d:
            m, _ = gerar_calibracao(d)
            _, linhas = ler_tsv(m)
            saida = Path(d) / "saida"
            for x in linhas:
                checagem(saida, x, "ACEITA")
            for x in linhas[:3]:
                checagem(saida, x, "DESCARTADA-INFRA", ["h4: h4b"])
            rc, _, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "51106",
                               "--s0", "60000", "--passo-semente", "100", "--teto-h4", "2")
            self.assertEqual(3, rc)
            self.assertIn("teto", err)
            self.assertFalse((saida / "reposicoes" / "reposicoes.tsv").exists(), "nada gravado")

    def test_semente_incoerente_recusa(self):
        with tempfile.TemporaryDirectory() as d:
            m, _ = gerar_calibracao(d)
            saida = Path(d) / "saida"
            saida.mkdir()
            rc, _, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "51106",
                               "--s0", "60001", "--passo-semente", "100", "--teto-h4", "5")
            self.assertEqual(2, rc)


if __name__ == "__main__":
    unittest.main()
