"""Testes da matriz fatorial 2x2 (complemento 1 do benchmark refeito, DT-027) em scripts/campanha_s3_matriz.py.

DT-027 secao 1: na celula de teste (ASYNC F2) cada bloco tem quatro bracos com a mesma semente, em ordem sorteada
dentro do bloco: PO (PREFERRED_NON_NULL + shadow), PL (PREFERRED_NON_NULL + active), AO (A1 + shadow) e AL (A1 +
active). Secao 3: as celulas descritivas tem dois bracos (shadow x active) com a regra de fusao atual. Secao 5, item 1:
a variante da regra de fusao vai numa coluna nova da matriz (aqui "fusao", com pnn ou a1); item 4: pareamento
conferido para os dois formatos. As matrizes de calibracao e de avaliacao (B-40) continuam no formato de 13 colunas,
byte a byte (a da recalibracao X-19 sai com o mesmo SHA-256 de antes).

Uso: python -I scripts/test_campanha_s3_matriz_fatorial.py   (de qualquer diretorio)
"""
import hashlib
import json
import random
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import campanha_s3_matriz as cm  # noqa: E402

# desenho da DT-027 (secoes 1 e 3): nucleo = F2 2x2 x 12 + sem falha x 4 + F3 x 4; extras = F1 x 4 + F4 x 4
NUCLEO = "ASYNC-F2:teste:12,ASYNC-none:descritiva:4,ASYNC-F3:descritiva:4"
EXTRAS = "ASYNC-F1:descritiva:4,ASYNC-F4:descritiva:4"
FATORIAL = {"PO": ("shadow", "pnn"), "PL": ("active", "pnn"), "AO": ("shadow", "a1"), "AL": ("active", "a1")}


def rodar(*args):
    r = subprocess.run([sys.executable, "-I", str(AQUI / "campanha_s3_matriz.py")] + list(args),
                       capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def ler_tsv(p):
    linhas = Path(p).read_text(encoding="ascii").split("\n")
    assert linhas[-1] == ""
    cab = linhas[0].split("\t")
    return cab, [dict(zip(cab, l.split("\t"))) for l in linhas[1:-1]]


def gerar_fatorial(d, nome="f.tsv", ordem=20261106, s0=800000, i0=0, b0=0, nucleo=NUCLEO, extras=EXTRAS):
    saida = Path(d) / nome
    args = ["gerar", "--modo", "fatorial", "--nucleo", nucleo, "--ordem-seed", str(ordem), "--s0", str(s0),
            "--passo-semente", "100", "--i0", str(i0), "--b0", str(b0), "--saida", str(saida)]
    if extras:
        args += ["--extras", extras]
    rc, out, err = rodar(*args)
    assert rc == 0, err
    return saida, out


def checagem(saida, linha, veredito, motivos=None):
    d = Path(saida) / ("exec-%04d-%s-%s" % (int(linha["i"]), linha["celula"], linha["braco"]))
    d.mkdir(parents=True, exist_ok=True)
    (d / "checagem.json").write_text(json.dumps({"i": int(linha["i"]), "veredito": veredito,
                                                 "motivos": motivos or []}), encoding="utf-8")


def por_bloco(linhas):
    b = {}
    for x in linhas:
        b.setdefault(x["bloco"], []).append(x)
    return b


class GerarFatorial(unittest.TestCase):

    def test_colunas_fatorial_e_sha(self):
        self.assertEqual(cm.COLUNAS + ("fusao",), cm.COLUNAS_FATORIAL)
        with tempfile.TemporaryDirectory() as d:
            saida, out = gerar_fatorial(d)
            cab, _ = ler_tsv(saida)
            self.assertEqual(list(cm.COLUNAS_FATORIAL), cab, "a coluna nova vai no fim (posicoes antigas iguais)")
            self.assertIn(hashlib.sha256(saida.read_bytes()).hexdigest(), out)

    def test_desenho_da_dt027_80_execucoes(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_fatorial(d)[0])
            self.assertEqual(80, len(linhas))
            self.assertEqual(64, sum(1 for x in linhas if x["fase"] == "nucleo"))
            self.assertEqual(16, sum(1 for x in linhas if x["fase"] == "extra"))
            f2 = [x for x in linhas if x["celula"] == "ASYNC-F2"]
            self.assertEqual(48, len(f2))
            self.assertEqual(12, len(por_bloco(f2)))
            self.assertEqual(list(range(80)), [int(x["i"]) for x in linhas])

    def test_bloco_de_teste_tem_os_quatro_bracos(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_fatorial(d)[0])
            for b, xs in por_bloco([x for x in linhas if x["papel"] == "teste"]).items():
                self.assertEqual(4, len(xs), b)
                self.assertEqual(set(FATORIAL), {x["braco"] for x in xs}, b)
                for x in xs:
                    self.assertEqual(FATORIAL[x["braco"]], (x["controlador"], x["fusao"]), (b, x["braco"]))
                self.assertEqual(1, len({x["seed"] for x in xs}), "mesma semente no bloco")
                self.assertEqual(1, len({x["celula"] for x in xs}))
                self.assertEqual(800000 + 100 * int(b), int(xs[0]["seed"]))
                i = sorted(int(x["i"]) for x in xs)
                self.assertEqual(list(range(i[0], i[0] + 4)), i, "bracos seguidos")
                self.assertEqual(["%s.%d" % (xs[0]["posicao"].split(".")[0], k) for k in range(4)],
                                 [x["posicao"] for x in xs])

    def test_descritivas_dois_bracos_com_a_regra_atual(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_fatorial(d)[0])
            desc = [x for x in linhas if x["papel"] == "descritiva"]
            self.assertEqual(32, len(desc))
            self.assertEqual({"pnn"}, {x["fusao"] for x in desc}, "DT-027 secao 3: a regra fica a atual")
            for b, xs in por_bloco(desc).items():
                self.assertEqual({"A": "shadow", "B": "active"}, {x["braco"]: x["controlador"] for x in xs}, b)
                self.assertEqual(1, len({x["seed"] for x in xs}))

    def test_ordem_dos_bracos_sorteada_por_bloco_e_reprodutivel(self):
        with tempfile.TemporaryDirectory() as d:
            a, _ = gerar_fatorial(d, "a.tsv")
            b, _ = gerar_fatorial(d, "b.tsv")
            c, _ = gerar_fatorial(d, "c.tsv", ordem=20261107)
            self.assertEqual(a.read_bytes(), b.read_bytes())
            self.assertNotEqual(a.read_bytes(), c.read_bytes())
            _, linhas = ler_tsv(a)
            blocos = por_bloco([x for x in linhas if x["papel"] == "teste"])
            for bl, xs in blocos.items():
                esperada = ["PO", "PL", "AO", "AL"]
                random.Random(20261106 * 1000000 + int(bl)).shuffle(esperada)
                self.assertEqual(esperada, [x["braco"] for x in xs], bl)
            primeiros = {xs[0]["braco"] for xs in blocos.values()}
            self.assertGreaterEqual(len(primeiros), 2, "a ordem varia entre os blocos")

    def test_nucleo_antes_das_extras_e_rodadas_completas(self):
        with tempfile.TemporaryDirectory() as d:
            _, linhas = ler_tsv(gerar_fatorial(d)[0])
            fases = [x["fase"] for x in linhas]
            self.assertEqual(sorted(fases, key=lambda f: 0 if f == "nucleo" else 1), fases)
            rod = {}
            for x in linhas:
                if x["posicao"].endswith(".0"):
                    rod.setdefault(int(x["rodada"]), []).append(x["celula"])
            self.assertEqual(sorted(["ASYNC-F2", "ASYNC-none", "ASYNC-F3"]), sorted(rod[1]))
            self.assertEqual(["ASYNC-F2"], rod[5])
            self.assertEqual(sorted(["ASYNC-F1", "ASYNC-F4"]), sorted(rod[13]))

    def test_argumentos(self):
        with tempfile.TemporaryDirectory() as d:
            base = ["gerar", "--modo", "fatorial", "--ordem-seed", "1", "--s0", "1", "--passo-semente", "100",
                    "--i0", "0", "--saida", str(Path(d) / "x.tsv")]
            self.assertEqual(2, rodar(*base, "--nucleo", NUCLEO)[0], "sem --b0")
            self.assertEqual(2, rodar(*base, "--b0", "0")[0], "sem --nucleo")
            self.assertEqual(2, rodar(*base, "--b0", "0", "--nucleo", NUCLEO, "--celulas", "ASYNC-F2:teste:1")[0])
            self.assertEqual(2, rodar(*base, "--b0", "0", "--nucleo", "ASYNC-F2:calibracao:2")[0], "papel invalido")


class FormatosAntigos(unittest.TestCase):

    def test_recalibracao_x19_mesmo_sha(self):
        """A matriz da recalibracao (roteiro X-19, 2.4) sai igual: 3f20219d... (13 colunas, sem a coluna fusao)."""
        with tempfile.TemporaryDirectory() as d:
            saida = Path(d) / "mrc.tsv"
            rc, out, err = rodar("gerar", "--modo", "calibracao", "--celulas",
                                 "ASYNC-none:calibracao:5,ASYNC-F1:calibracao:5,ASYNC-F2:calibracao:5,"
                                 "SYNC-F2:calibracao:3,SYNC-none:calibracao:3,ASYNC-F4:calibracao:5",
                                 "--ordem-seed", "20261106", "--s0", "700000", "--passo-semente", "100", "--i0", "0",
                                 "--saida", str(saida))
            self.assertEqual(0, rc, err)
            self.assertEqual("3f20219d2e8f01ded10fea80d51f2d7c87f4c7361abc45a51f8a122baa7c0351",
                             hashlib.sha256(saida.read_bytes()).hexdigest())

    def test_avaliacao_continua_com_13_colunas(self):
        with tempfile.TemporaryDirectory() as d:
            saida = Path(d) / "av.tsv"
            rc, _, err = rodar("gerar", "--modo", "avaliacao", "--nucleo", "ASYNC-F2:teste:2", "--ordem-seed", "7",
                               "--s0", "70000", "--passo-semente", "100", "--i0", "0", "--b0", "1", "--saida",
                               str(saida))
            self.assertEqual(0, rc, err)
            self.assertEqual(list(cm.COLUNAS), ler_tsv(saida)[0])

    def test_ler_tsv_aceita_os_dois_formatos(self):
        with tempfile.TemporaryDirectory() as d:
            f, _ = gerar_fatorial(d)
            antigo = Path(d) / "av.tsv"
            rc, _, err = rodar("gerar", "--modo", "avaliacao", "--nucleo", "ASYNC-F2:teste:1", "--ordem-seed", "7",
                               "--s0", "70000", "--passo-semente", "100", "--i0", "0", "--b0", "1", "--saida",
                               str(antigo))
            self.assertEqual(0, rc, err)
            self.assertEqual({"pnn", "a1"}, {x["fusao"] for x in cm.ler_tsv(f)})
            self.assertEqual({"pnn"}, {x["fusao"] for x in cm.ler_tsv(antigo)}, "13 colunas: a regra atual")
            ruim = Path(d) / "ruim.tsv"
            ruim.write_text("i\tfase\n0\tnucleo\n", encoding="ascii")
            with self.assertRaises(cm.ErroUso):
                cm.ler_tsv(ruim)


class ReporFatorial(unittest.TestCase):

    def test_bloco_de_teste_perdido_refeito_com_os_quatro_bracos(self):
        with tempfile.TemporaryDirectory() as d:
            m, _ = gerar_fatorial(d, nucleo="ASYNC-F2:teste:2,ASYNC-none:descritiva:1", extras=None)
            _, linhas = ler_tsv(m)
            saida = Path(d) / "saida"
            for x in linhas:
                checagem(saida, x, "ACEITA")
            perdida = [x for x in linhas if x["papel"] == "teste"][2]
            checagem(saida, perdida, "DESCARTADA-INFRA", ["infra: fusao: politica lida difere"])
            rc, out, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "20261106",
                                 "--s0", "800000", "--passo-semente", "100", "--teto-h4", "6")
            self.assertEqual(0, rc, err)
            cab, rep = ler_tsv(saida / "reposicoes" / "reposicoes.tsv")
            self.assertEqual(list(cm.COLUNAS_FATORIAL), cab)
            self.assertEqual(4, len(rep), "o bloco inteiro: os quatro bracos")
            self.assertEqual(set(FATORIAL), {x["braco"] for x in rep})
            for x in rep:
                self.assertEqual(FATORIAL[x["braco"]], (x["controlador"], x["fusao"]))
            novo = max(int(x["bloco"]) for x in linhas) + 1
            self.assertEqual({str(novo)}, {x["bloco"] for x in rep})
            self.assertEqual({perdida["bloco"]}, {x["repoe"] for x in rep})
            self.assertEqual({str(800000 + 100 * novo)}, {x["seed"] for x in rep})
            self.assertEqual(list(range(len(linhas), len(linhas) + 4)), [int(x["i"]) for x in rep])
            esperada = ["PO", "PL", "AO", "AL"]
            random.Random(20261106 * 1000000 + novo).shuffle(esperada)
            self.assertEqual(esperada, [x["braco"] for x in rep])

    def test_bloco_descritivo_perdido_refeito_com_dois_bracos_pnn(self):
        with tempfile.TemporaryDirectory() as d:
            m, _ = gerar_fatorial(d, nucleo="ASYNC-F2:teste:1,ASYNC-none:descritiva:1", extras=None)
            _, linhas = ler_tsv(m)
            saida = Path(d) / "saida"
            for x in linhas:
                checagem(saida, x, "ACEITA")
            perdida = [x for x in linhas if x["papel"] == "descritiva"][0]
            checagem(saida, perdida, "RECUSADA", ["sem ancora"])
            rc, _, err = rodar("repor", "--matriz", str(m), "--saida", str(saida), "--ordem-seed", "20261106",
                               "--s0", "800000", "--passo-semente", "100", "--teto-h4", "6")
            self.assertEqual(0, rc, err)
            cab, rep = ler_tsv(saida / "reposicoes" / "reposicoes.tsv")
            self.assertEqual(list(cm.COLUNAS_FATORIAL), cab)
            self.assertEqual({"A", "B"}, {x["braco"] for x in rep})
            self.assertEqual({"pnn"}, {x["fusao"] for x in rep})


if __name__ == "__main__":
    unittest.main()
