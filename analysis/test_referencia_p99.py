"""Testes de analysis/referencia_p99.py (B-39.1): R1..R7.

Fixtures geradas no proprio teste em diretorios temporarios. O import de rebaseline.py existe SO aqui
(R1, paridade); o modulo testado nao o importa. A CLI roda com python -I, como em producao.

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import hashlib
import json
import math
import random
import subprocess
import sys
import tempfile
import unittest
from fractions import Fraction
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent
sys.path.insert(0, str(AQUI))
import referencia_p99 as ref  # noqa: E402
import rebaseline  # noqa: E402  (so para a paridade R1)

SCRIPT = AQUI / "referencia_p99.py"
CAB = "op_id,operation,start_ns,end_ns,replica,return_code,key"
T0 = 1_700_000_000_000_000_000


def escrever_csv(d, rep, eventos, meta=2, phase=None):
    """eventos: lista de (operation, latencia_ns, return_code). meta: 2, 1 ou None (sem arquivo)."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    linhas = [CAB + (",phase" if phase is not None else "")]
    for i, (op, lat, rc) in enumerate(eventos):
        ini = T0 + i * 1000
        linha = "%d,%s,%d,%d,isn1,%s,k%d" % (i, op, ini, ini + lat, rc, i)
        if phase is not None:
            linha += "," + (phase(i) if callable(phase) else phase)
        linhas.append(linha)
    p = d / ("rep-%03d.csv" % rep)
    p.write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    if meta is not None:
        (d / ("meta-csv-rep-%03d.json" % rep)).write_text(json.dumps({"contrato_csv": meta}), encoding="utf-8")
    return p


def rodar_cli(*args, cwd=None):
    r = subprocess.run([sys.executable, "-I", str(SCRIPT)] + [str(a) for a in args],
                       capture_output=True, text=True, cwd=cwd)
    return r.returncode, r.stdout, r.stderr


def nearest(vals, p):
    s = sorted(vals)
    return s[math.ceil(Fraction(str(p)) * len(s)) - 1]


class R1Paridade(unittest.TestCase):

    def test_percentil_igual_ao_rebaseline(self):
        rng = random.Random(3901)
        ps = (0.5, 0.95, 0.99, 0.999)
        for i in range(10000):
            n = rng.randint(1, 5000)
            alcance = rng.choice((5, 1000, 10 ** 9))       # com e sem empates
            vals = [rng.randrange(alcance) for _ in range(n)]
            p = ps[i % 4]
            self.assertEqual(rebaseline.percentil(vals, p), ref.percentil(vals, p), (n, p))

    def test_percentil_vazio(self):
        self.assertIsNone(ref.percentil([], 0.99))

    def test_validos_iguais(self):
        self.assertEqual(rebaseline.VALIDOS, ref.VALIDOS)


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()


class R2Populacao(Base):

    def test_erros_lentos_entram(self):
        ev = [("Login", 1_000_000 + i, "OK") for i in range(1900)]
        ev += [("Login", 900_000_000 + i, "ERROR_TRANSPORT") for i in range(100)]
        csv = escrever_csv(self.d, 1, ev)
        todos = [lat for _, lat, _ in ev]
        validos = [lat for _, lat, rc in ev if rc in rebaseline.VALIDOS["Login"]]
        p_todos, p_validos = nearest(todos, 0.99), nearest(validos, 0.99)
        self.assertGreater(p_todos, p_validos)
        r = ref.gerar_referencia([csv], "S1", "sync")
        self.assertEqual(p_todos, r["p99_ns"]["Login"])
        self.assertEqual(2000, r["n"]["Login"])

    def test_blocked_e_operacoes_fora_do_validos_tambem(self):
        ev = [("Login", 10 + i, "OK") for i in range(990)] + [("Login", 10 ** 9, "BLOCKED")] * 10
        ev += [("Block", 5 + i, "NONE") for i in range(1000)]
        r = ref.gerar_referencia([escrever_csv(self.d, 1, ev)], "S2", "async")
        self.assertEqual(1000, r["n"]["Login"])
        self.assertEqual(nearest([lat for _, lat, _ in ev[:1000]], 0.99), r["p99_ns"]["Login"])
        self.assertIn("Block", r["p99_ns"])


class R3PostoMaisProximo(Base):

    def test_posto_e_nao_lower(self):
        # Em n = 1 001 os dois metodos coincidem para p = 0,99 (floor(0,99*1000) = 990 =
        # ceil(0,99*1001) - 1); o menor n >= 1 000 em que diferem e 1 002 (ver relatorio).
        self.assertEqual(math.floor(Fraction(99, 100) * 1000), math.ceil(Fraction(99, 100) * 1001) - 1)
        n = 1002
        lower = math.floor(Fraction(99, 100) * (n - 1))
        posto = math.ceil(Fraction(99, 100) * n) - 1
        self.assertNotEqual(lower, posto)
        lat = [1000 * (i + 1) for i in range(n)]
        random.Random(7).shuffle(lat)
        r = ref.gerar_referencia([escrever_csv(self.d, 1, [("Validate", x, "VALID") for x in lat])], "S1", "sync")
        self.assertEqual(1000 * (posto + 1), r["p99_ns"]["Validate"])
        self.assertNotEqual(1000 * (lower + 1), r["p99_ns"]["Validate"])
        self.assertEqual("posto-mais-proximo", r["metodo"])


class R4NMin(Base):

    def test_999_omitida_1000_entra(self):
        ev = [("Logout", 100 + i, "NONE") for i in range(999)] + [("Login", 100 + i, "OK") for i in range(1000)]
        r = ref.gerar_referencia([escrever_csv(self.d, 1, ev)], "S1", "sync")
        self.assertEqual({"Logout": 999}, r["omitidas"])
        self.assertEqual(["Login"], sorted(r["p99_ns"]))
        self.assertEqual({"Login": 1000}, r["n"])
        self.assertEqual(1000, r["n_min"])

    def test_n_min_da_cli_e_soma_de_csvs(self):
        a = escrever_csv(self.d / "a", 1, [("Login", 5 + i, "OK") for i in range(600)])
        b = escrever_csv(self.d / "b", 2, [("Login", 7 + i, "OK") for i in range(400)])
        r = ref.gerar_referencia([a, b], "S1", "sync")
        self.assertEqual(1000, r["n"]["Login"])
        r2 = ref.gerar_referencia([a], "S1", "sync", n_min=500)
        self.assertEqual(600, r2["n"]["Login"])


class R5Guardas(Base):

    def setUp(self):
        super().setUp()
        self.ok = [("Login", 10 + i, "OK") for i in range(1000)]

    def conferir_recusa(self, args, saida):
        rc, out, err = rodar_cli(*args)
        self.assertEqual(2, rc, (out, err))
        return err

    def test_sem_meta(self):
        csv = escrever_csv(self.d, 1, self.ok, meta=None)
        saida = self.d / "ref.json"
        self.conferir_recusa(["--cenario", "S1", "--perfil", "sync", "--saida", saida, csv], saida)
        self.assertFalse(saida.exists())

    def test_meta_contrato_1(self):
        csv = escrever_csv(self.d, 1, self.ok, meta=1)
        saida = self.d / "ref.json"
        self.conferir_recusa(["--cenario", "S1", "--perfil", "sync", "--saida", saida, csv], saida)
        self.assertFalse(saida.exists())

    def test_phase_durante(self):
        csv = escrever_csv(self.d, 1, self.ok, phase=lambda i: "durante" if i == 500 else "antes")
        saida = self.d / "ref.json"
        self.conferir_recusa(["--cenario", "S1", "--perfil", "sync", "--saida", saida, csv], saida)
        self.assertFalse(saida.exists())

    def test_phase_antes_aceita(self):
        csv = escrever_csv(self.d, 1, self.ok, phase="antes")
        saida = self.d / "ref.json"
        rc, out, err = rodar_cli("--cenario", "S1", "--perfil", "sync", "--saida", saida, csv)
        self.assertEqual(0, rc, err)
        self.assertTrue(saida.exists())

    def test_saida_existente_nao_e_sobrescrita(self):
        csv = escrever_csv(self.d, 1, self.ok)
        saida = self.d / "ref.json"
        saida.write_text("anterior", encoding="utf-8")
        self.conferir_recusa(["--cenario", "S1", "--perfil", "sync", "--saida", saida, csv], saida)
        self.assertEqual("anterior", saida.read_text(encoding="utf-8"))

    def test_cenario_e_perfil_invalidos(self):
        csv = escrever_csv(self.d, 1, self.ok)
        saida = self.d / "ref.json"
        for c, p in (("S4", "sync"), ("s1", "sync"), ("S1", "SYNC"), ("S1", "dist_async")):
            with self.subTest(c=c, p=p):
                self.conferir_recusa(["--cenario", c, "--perfil", p, "--saida", saida, csv], saida)
                self.assertFalse(saida.exists())

    def test_nenhuma_operacao_com_n_min(self):
        csv = escrever_csv(self.d, 1, self.ok[:999])
        saida = self.d / "ref.json"
        self.conferir_recusa(["--cenario", "S1", "--perfil", "sync", "--saida", saida, csv], saida)
        self.assertFalse(saida.exists())

    def test_cabecalho_errado(self):
        p = self.d / "rep-001.csv"
        p.write_text("op_id,operation,start_ns,end_ns,return_code,key\n1,Login,1,2,OK,k\n", encoding="utf-8")
        (self.d / "meta-csv-rep-001.json").write_text('{"contrato_csv": 2}', encoding="utf-8")
        saida = self.d / "ref.json"
        self.conferir_recusa(["--cenario", "S1", "--perfil", "sync", "--saida", saida, p], saida)
        self.assertFalse(saida.exists())

    def test_funcao_levanta_erro_uso(self):
        with self.assertRaises(ref.ErroUso):
            ref.gerar_referencia([escrever_csv(self.d, 1, self.ok, meta=None)], "S1", "sync")


class R6Origem(Base):

    def test_origem_e_determinismo(self):
        a = escrever_csv(self.d, 1, [("Login", 10 + i, "OK") for i in range(1200)])
        b = escrever_csv(self.d, 2, [("Login", 20 + i, "ERROR_UNAVAILABLE") for i in range(300)])
        rc, _, err = rodar_cli("--cenario", "S3", "--perfil", "async", "--saida", "r1.json",
                               "rep-001.csv", "rep-002.csv", cwd=self.d)
        self.assertEqual(0, rc, err)
        rc, _, err = rodar_cli("--cenario", "S3", "--perfil", "async", "--saida", "r2.json",
                               "rep-001.csv", "rep-002.csv", cwd=self.d)
        self.assertEqual(0, rc, err)
        b1, b2 = (self.d / "r1.json").read_bytes(), (self.d / "r2.json").read_bytes()
        self.assertEqual(b1, b2)
        j = json.loads(b1)
        self.assertEqual({"esquema", "cenario", "perfil", "metodo", "populacao", "n_min", "p99_ns", "n",
                          "omitidas", "origem", "gerado_por"}, set(j))
        self.assertEqual("referencia-p99/1", j["esquema"])
        self.assertEqual("todos-os-eventos-por-operation", j["populacao"])
        self.assertEqual(["S3", "async"], [j["cenario"], j["perfil"]])
        self.assertEqual([{"arquivo": "rep-001.csv", "sha256": hashlib.sha256(a.read_bytes()).hexdigest(),
                           "linhas": 1200},
                          {"arquivo": "rep-002.csv", "sha256": hashlib.sha256(b.read_bytes()).hexdigest(),
                           "linhas": 300}], j["origem"])
        self.assertEqual({"script": "analysis/referencia_p99.py",
                          "sha256": hashlib.sha256(SCRIPT.read_bytes()).hexdigest()}, j["gerado_por"])
        self.assertIsInstance(j["p99_ns"]["Login"], int)
        self.assertIsInstance(j["n"]["Login"], int)
        self.assertEqual(1500, j["n"]["Login"])
        # chaves ordenadas
        self.assertEqual(sorted(j), list(j))


class R7Calibracao(Base):

    OPS = ("Login", "Validate", "Logout", "IncrementFailure", "ResetFailures")

    def test_excedencia_sob_h0(self):
        rng = random.Random(20261027)
        params = {op: (math.log(2e6) + 0.3 * k, 0.4 + 0.15 * k) for k, op in enumerate(self.OPS)}
        csvs = []
        for rep in range(1, 21):
            ev = []
            for _ in range(1000):
                for op in self.OPS:
                    mu, s = params[op]
                    ev.append((op, int(rng.lognormvariate(mu, s)), "OK"))
            csvs.append(escrever_csv(self.d / ("r%02d" % rep), rep, ev))
        origem, teste = csvs[:10], csvs[10:]
        r = ref.gerar_referencia(origem, "S1", "sync")
        self.assertEqual(set(self.OPS), set(r["p99_ns"]))

        def somar(lista):
            tot = {op: [0, 0] for op in self.OPS}
            for c in lista:
                for op, (el, ex) in ref.excedencia(c, r).items():
                    tot[op][0] += el
                    tot[op][1] += ex
            return tot

        fora = somar(teste)
        dentro = somar(origem)
        for op in self.OPS:
            el, ex = fora[op]
            limite = 0.01 + 3.29 * math.sqrt(0.01 * 0.99 / el)
            with self.subTest(op=op, conjunto="validacao"):
                self.assertLessEqual(ex / el, limite, (ex, el))
            el, ex = dentro[op]
            with self.subTest(op=op, conjunto="origem"):
                self.assertEqual(10000, el)
                self.assertLessEqual(ex / el, 0.01, (ex, el))
        R7Calibracao.resultado = {"validacao": fora, "origem": dentro}

    def test_excedencia_estrita_e_fora_da_referencia(self):
        csv = escrever_csv(self.d, 1, [("Login", 100, "OK"), ("Login", 101, "ERROR_TRANSPORT"),
                                       ("Login", 99, "OK"), ("Block", 10 ** 9, "NONE")])
        r = {"p99_ns": {"Login": 100, "Logout": 5}}
        self.assertEqual({"Login": (3, 1), "Logout": (0, 0)}, ref.excedencia(csv, r))


if __name__ == "__main__":
    unittest.main()
