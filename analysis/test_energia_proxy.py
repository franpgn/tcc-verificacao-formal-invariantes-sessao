"""Testes de analysis/energia_proxy.py (B-39.2): E-a..E-f.

Contratos de entrada (texto dos prompts do B-37 e do B-27): recursos/1 e S3 v2. Fixtures geradas no
proprio teste em diretorios temporarios. A CLI roda com python -I.

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import json
import subprocess
import sys
import tempfile
import unittest
from fractions import Fraction
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import energia_proxy as ep  # noqa: E402
import rebaseline  # noqa: E402  (so para conferir a copia de VALIDOS)

SCRIPT = AQUI / "energia_proxy.py"
S = 1_000_000_000
T0 = 1_700_000_000 * S
CAB_REC = "t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado"
CAB_EV = "op_id,operation,start_ns,end_ns,replica,return_code,key"
CAB_S3 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns"


def escrever_recursos(d, rep, series, k_max=10, erros=(), nproc=4, intervalo_ms=1000, contrato="recursos/1",
                      cabecalho=CAB_REC, passo_ns=S):
    """series: {fonte: f(k) -> (cpu_usec, steal_usec|None)} amostrada em T0 + k*passo_ns, k = 0..k_max.
    erros: conjunto de (fonte, k) gravados com estado erro e numericos vazios."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    linhas = [cabecalho]
    for k in range(k_max + 1):
        t = T0 + k * passo_ns
        for fonte, f in series.items():
            if (fonte, k) in erros:
                linhas.append("%d,%s,,,,erro:leitura" % (t, fonte))
                continue
            cpu, steal = f(k)
            linhas.append("%d,%s,%d,%d,%s,ok" % (t, fonte, cpu, 123456, "" if steal is None else "%d" % steal))
    (d / ("recursos-rep-%03d.csv" % rep)).write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    meta = {"contrato": contrato, "versao_coletor": "teste", "sha256_script": "0" * 64, "uname_r": "teste",
            "clk_tck": 100, "nproc": nproc, "intervalo_ms": intervalo_ms, "podman_stats_s": 10,
            "fontes": [], "inicio_epoch_ns": T0, "fim_epoch_ns": T0 + k_max * passo_ns,
            "amostras": k_max + 1, "motivo_fim": "duracao", "atraso_max_ns": 0}
    (d / ("recursos-meta-rep-%03d.json" % rep)).write_text(json.dumps(meta), encoding="utf-8")


def escrever_eventos(d, rep, eventos, meta=2):
    """eventos: (operation, return_code, start_ns, end_ns)."""
    d = Path(d)
    linhas = [CAB_EV] + ["%d,%s,%d,%d,isn1,%s,k%d" % (i, op, a, b, rc, i)
                         for i, (op, rc, a, b) in enumerate(eventos)]
    (d / ("rep-%03d.csv" % rep)).write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    if meta is not None:
        (d / ("meta-csv-rep-%03d.json" % rep)).write_text(json.dumps({"contrato_csv": meta}), encoding="utf-8")


def escrever_s3(d, rep, acoes, oito_colunas=False):
    """acoes: (t_ns, t_fim_ns, espera_ns, tentativas, falha_percebida)."""
    d = Path(d)
    if oito_colunas:
        linhas = [CAB_S3.rsplit(",", 2)[0]] + ["%d,u%d,Validate,s,OK_VALIDATE:VALID,%d,%s," % (
            t, i, tent, "true" if f else "false") for i, (t, _, _, tent, f) in enumerate(acoes)]
    else:
        linhas = [CAB_S3] + ["%d,u%d,Validate,s,%s,%d,%s,,%d,%d" % (
            t, i, "ERROR_TRANSPORT" if f else "OK_VALIDATE:VALID", tent, "true" if f else "false", tf, w)
            for i, (t, tf, w, tent, f) in enumerate(acoes)]
    (d / ("s3-acoes-rep-%03d.csv" % rep)).write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))


def linear(usec_por_s, steal_por_s=None):
    return lambda k: (usec_por_s * k, None if steal_por_s is None else steal_por_s * k)


def rodar_cli(*args):
    r = subprocess.run([sys.executable, "-I", str(SCRIPT)] + [str(a) for a in args], capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def rec(self):
        return self.d / "recursos-rep-001.csv"


class EaInterpolacao(Base):

    def test_contador_linear_exato(self):
        escrever_recursos(self.d, 1, {"cg:isn1": linear(500_000), "vm": linear(2_000_000, 0)})
        ivs = [("antes", T0 + 13 * S // 10, T0 + 47 * S // 10), ("durante", T0 + 47 * S // 10, T0 + 9 * S)]
        r = ep.cpu_por_fase(self.rec(), ivs, 1000)
        self.assertAlmostEqual(1.7, r["cg:isn1"]["antes"]["cpu_s"], delta=1e-6)
        self.assertAlmostEqual(0.5 * 4.3, r["cg:isn1"]["durante"]["cpu_s"], delta=1e-6)
        self.assertAlmostEqual(2.0 * 3.4, r["vm"]["antes"]["cpu_s"], delta=1e-6)
        self.assertEqual("ok", r["vm"]["antes"]["marca"])

    def test_fronteira_entre_amostras_interpola(self):
        cum = [0, 1_000_000, 1_500_000, 3_500_000, 4_000_000]
        escrever_recursos(self.d, 1, {"vm": lambda k: (cum[k], 0)}, k_max=4)
        r = ep.cpu_por_fase(self.rec(), [("antes", T0 + S // 2, T0 + 9 * S // 4)], 1000)
        # 0,5 s -> 500 000; 2,25 s -> 1 500 000 + 0,25 * 2 000 000 = 2 000 000
        self.assertAlmostEqual(1.5, r["vm"]["antes"]["cpu_s"], delta=1e-6)

    def test_fronteira_fora_das_amostras(self):
        escrever_recursos(self.d, 1, {"vm": linear(1_000_000, 0)}, k_max=5)
        r = ep.cpu_por_fase(self.rec(), [("antes", T0 - S // 2, T0 + 2 * S), ("durante", T0 + 2 * S, T0 + 5 * S),
                                         ("recuperacao", T0 + 5 * S, T0 + 5 * S + 1)], 1000)
        self.assertIsNone(r["vm"]["antes"]["cpu_s"])
        self.assertEqual("fora-das-amostras", r["vm"]["antes"]["marca"])
        self.assertAlmostEqual(3.0, r["vm"]["durante"]["cpu_s"], delta=1e-6)
        self.assertIsNone(r["vm"]["recuperacao"]["cpu_s"])
        self.assertEqual("fora-das-amostras", r["vm"]["recuperacao"]["marca"])

    def test_contador_no_intervalo_fracao_exata(self):
        amostras = [(0, 0, None, True), (1000, 999, None, True)]
        delta, marca = ep.contador_no_intervalo(amostras, 1, 2, 1000)
        self.assertEqual(("ok", Fraction(999, 1000)), (marca, delta))


class EbLacuna(Base):

    def test_tres_erros_seguidos(self):
        erros = {("vm", 4), ("vm", 5), ("vm", 6)}
        escrever_recursos(self.d, 1, {"vm": linear(1_000_000, 0), "cg:isn1": linear(100_000)}, erros=erros)
        ivs = [("antes", T0, T0 + 3 * S), ("durante", T0 + 3 * S, T0 + 8 * S), ("recuperacao", T0 + 8 * S, T0 + 10 * S)]
        r = ep.cpu_por_fase(self.rec(), ivs, 1000)
        self.assertEqual("ok", r["vm"]["antes"]["marca"])
        self.assertIsNone(r["vm"]["durante"]["cpu_s"])
        self.assertEqual("lacuna", r["vm"]["durante"]["marca"])
        self.assertEqual("ok", r["vm"]["recuperacao"]["marca"])
        self.assertEqual("ok", r["cg:isn1"]["durante"]["marca"])   # outra fonte nao e afetada

    def test_fase_inteira_dentro_da_lacuna(self):
        escrever_recursos(self.d, 1, {"vm": linear(1_000_000, 0)}, erros={("vm", 4), ("vm", 5), ("vm", 6)})
        r = ep.cpu_por_fase(self.rec(), [("durante", T0 + 45 * S // 10, T0 + 49 * S // 10)], 1000)
        self.assertEqual("lacuna", r["vm"]["durante"]["marca"])

    def test_um_erro_nao_e_lacuna(self):
        # 2 s sem amostra valida = 2 x intervalo_ms: nao passa de 2 x, entao interpola
        escrever_recursos(self.d, 1, {"vm": linear(1_000_000, 0)}, erros={("vm", 5)})
        r = ep.cpu_por_fase(self.rec(), [("durante", T0 + 3 * S, T0 + 8 * S)], 1000)
        self.assertEqual("ok", r["vm"]["durante"]["marca"])
        self.assertAlmostEqual(5.0, r["vm"]["durante"]["cpu_s"], delta=1e-6)


class EcSteal(Base):

    def caso(self, steal_total_usec):
        # 100 s, nproc 4: denominador 400 000 000 us
        escrever_recursos(self.d, 1, {"vm": lambda k: (0, steal_total_usec * k // 100)}, k_max=100)
        return ep.steal_frac(self.rec(), T0, T0 + 100 * S, 4, 1000)

    def test_quatro_por_cento(self):
        f = self.caso(16_000_000)
        self.assertEqual(Fraction(4, 100), f)
        self.assertFalse(ep.excluida_por_steal(f))

    def test_cinco_por_cento_exato_nao_exclui(self):
        f = self.caso(20_000_000)
        self.assertEqual(Fraction(5, 100), f)
        self.assertFalse(ep.excluida_por_steal(f))

    def test_cinco_virgula_zero_um_exclui(self):
        f = self.caso(20_040_000)
        self.assertEqual(Fraction(501, 10000), f)
        self.assertTrue(ep.excluida_por_steal(f))

    def test_sem_vm_e_indeterminado(self):
        escrever_recursos(self.d, 1, {"cg:isn1": linear(1)})
        self.assertIsNone(ep.steal_frac(self.rec(), T0, T0 + S, 4, 1000))
        self.assertIsNone(ep.excluida_por_steal(None))


class EdUteis(Base):

    def test_validos_e_copia(self):
        self.assertEqual(rebaseline.VALIDOS, ep.VALIDOS)

    def test_s1_so_validos(self):
        casos = [("Login", "OK", True), ("Login", "BLOCKED", False), ("Validate", "VALID", True),
                 ("Validate", "INVALID", True), ("Validate", "NOT_FOUND", False), ("Logout", "NONE", True),
                 ("Logout", "NOT_FOUND", False), ("IncrementFailure", "COUNTED", True),
                 ("ResetFailures", "NONE", True), ("Login", "ERROR_TRANSPORT", False),
                 ("Validate", "ERROR_UNAVAILABLE", False), ("Block", "NONE", False)]
        for op, rc, util in casos:
            with self.subTest(op=op, rc=rc):
                self.assertEqual(util, ep.e_util_evento(op, rc))

    def test_s1_execucao(self):
        escrever_recursos(self.d, 1, {"vm": linear(1_000_000, 0), "cg:isn1": linear(500_000),
                                      "pid:workload": linear(250_000)})
        ev = []
        for i in range(8):
            a = T0 + S + i * S // 2
            ev += [("Login", "OK", a, a + 10), ("Login", "BLOCKED", a, a + 10),
                   ("Validate", "ERROR_TRANSPORT", a, a + 10), ("Validate", "INVALID", a, a + 10)]
        escrever_eventos(self.d, 1, ev)
        j = ep.analisar_execucao(self.d, 1, "S1")
        self.assertEqual(16, j["uteis"]["total"])
        self.assertIsNone(j["retrabalho"])

    def test_s3_so_sem_falha_percebida(self):
        escrever_recursos(self.d, 1, {"vm": linear(1_000_000, 0)})
        acoes = [(T0 + S + i * S // 10, T0 + S + i * S // 10 + 5000, 0, 1 + (i % 3), i % 4 == 0) for i in range(40)]
        escrever_s3(self.d, 1, acoes)
        j = ep.analisar_execucao(self.d, 1, "S3")
        self.assertEqual(30, j["uteis"]["total"])
        rt = j["retrabalho"]
        self.assertEqual(40, rt["acoes"])
        self.assertEqual(30, rt["acoes_uteis"])
        self.assertEqual(10, rt["acoes_com_falha"])
        self.assertEqual(sum(t - 1 for _, _, _, t, _ in acoes), rt["retentativas"])
        self.assertAlmostEqual(rt["retentativas"] / 30, rt["retentativas_por_util"])
        self.assertAlmostEqual(10 / 30, rt["falhas_por_util"])


class EdbInstrumentacao(Base):
    """W-10: o ReplayValidate da sonda de replay do S3 nao entra nos eventos lidos (nem em t0/t1)."""

    def test_replay_validate_fora_dos_eventos(self):
        ev = [("Validate", "VALID", T0 + i, T0 + i + 10) for i in range(5)]
        ev += [("ReplayValidate", "ERROR_TRANSPORT", T0 + 100 * S, T0 + 104 * S)]
        escrever_eventos(self.d, 1, ev)
        lidos = ep.ler_eventos_inicio(self.d / "rep-001.csv")
        self.assertEqual(5, len(lidos))
        self.assertTrue(all(op == "Validate" for _, _, op, _ in lidos))
        self.assertEqual(T0 + 14, max(b for _, b, _, _ in lidos))


class EeModelo(Base):

    def test_energia_fase_a_mao(self):
        # P_max 100 W, nproc 4, fase de 10 s com 20 CPU-s (u = 0,5)
        for razao, total, din in ((0.3, 650.0, 350.0), (0.5, 750.0, 250.0), (0.7, 850.0, 150.0)):
            with self.subTest(razao=razao):
                et, ed = ep.energia_fase(20.0, 10.0, 4, 100.0, razao)
                self.assertAlmostEqual(total, et, places=9)
                self.assertAlmostEqual(din, ed, places=9)

    def preparar(self):
        # vm: 2 CPU-s por s (u = 0,5 com nproc 4); 500 uteis em [T0 + 1 s, T0 + 11 s)
        escrever_recursos(self.d, 1, {"vm": linear(2_000_000, 0), "cg:isn1": linear(1_000_000),
                                      "cg:isn2": linear(500_000), "pid:workload": linear(250_000)}, k_max=12)
        ev = [("Login", "OK", T0 + S + i * (10 * S // 500), T0 + S + i * (10 * S // 500) + 1) for i in range(500)]
        ev[-1] = ("Login", "OK", T0 + 11 * S - 2, T0 + 11 * S - 1)     # janela [T0 + 1 s, T0 + 11 s)
        escrever_eventos(self.d, 1, ev)

    def test_cli_com_energia(self):
        self.preparar()
        saida = self.d / "energia-rep-001.json"
        rc, out, err = rodar_cli("--execucao", self.d, "--rep", 1, "--cenario", "S1", "--p-max-w", 100,
                                 "--razoes-ocioso", "0.3,0.5,0.7", "--saida", saida)
        self.assertEqual(0, rc, err)
        j = json.loads(saida.read_text(encoding="utf-8"))
        self.assertEqual("energia-proxy/1", j["esquema"])
        self.assertTrue(j["energia"]["calculada"])
        self.assertEqual(100, j["energia"]["p_max_w"])
        self.assertEqual([0.3, 0.5, 0.7], j["energia"]["razoes_ocioso"])
        tot = j["agregados"]["vm"]["total"]
        self.assertAlmostEqual(20.0, tot["cpu_s"], delta=1e-5)
        self.assertAlmostEqual(0.5, tot["utilizacao"], delta=1e-6)
        self.assertAlmostEqual(40.0, tot["cpu_s_por_1000_uteis"], delta=1e-4)
        self.assertAlmostEqual(15.0, j["agregados"]["soma_cg"]["total"]["cpu_s"], delta=1e-5)
        self.assertAlmostEqual(15.0 / 40, j["agregados"]["soma_cg"]["total"]["utilizacao"], delta=1e-6)
        self.assertAlmostEqual(5.0, j["agregados"]["pid:workload"]["total"]["cpu_s_por_1000_uteis"], delta=1e-4)
        for razao, total, din in (("0.3", 650.0, 350.0), ("0.5", 750.0, 250.0), ("0.7", 850.0, 150.0)):
            e = j["energia"]["por_razao"][razao]["vm"]["total"]
            self.assertAlmostEqual(total, e["e_total_j"], delta=1e-3)
            self.assertAlmostEqual(din, e["e_dinamica_j"], delta=1e-3)
            self.assertAlmostEqual(total * 2, e["e_total_j_por_1000_uteis"], delta=1e-2)
            self.assertAlmostEqual(din * 2, e["e_dinamica_j_por_1000_uteis"], delta=1e-2)

    def test_cli_sem_p_max(self):
        self.preparar()
        saida = self.d / "energia-rep-001.json"
        rc, out, err = rodar_cli("--execucao", self.d, "--rep", 1, "--cenario", "S1", "--saida", saida)
        self.assertEqual(0, rc, err)
        j = json.loads(saida.read_text(encoding="utf-8"))
        self.assertFalse(j["energia"]["calculada"])
        self.assertIn("p-max-w", j["energia"]["motivo"])
        self.assertNotIn("por_razao", j["energia"])
        self.assertAlmostEqual(20.0, j["agregados"]["vm"]["total"]["cpu_s"], delta=1e-5)

    def test_cli_com_marcadores_e_fases(self):
        self.preparar()
        (self.d / "marcadores.txt").write_text(
            "fault=F2 node=isn3 event=start t_epoch_ns=%d\nfault=F2 node=isn3 event=end t_epoch_ns=%d\n"
            % (T0 + 3 * S, T0 + 5 * S), encoding="utf-8")
        saida = self.d / "energia-rep-001.json"
        rc, _, err = rodar_cli("--execucao", self.d, "--rep", 1, "--cenario", "S1", "--marcadores",
                               self.d / "marcadores.txt", "--w-recovery-s", 2, "--saida", saida)
        self.assertEqual(0, rc, err)
        j = json.loads(saida.read_text(encoding="utf-8"))
        self.assertEqual(["antes", "durante", "recuperacao", "pos-convergencia"], [f["rotulo"] for f in j["fases"]])
        self.assertAlmostEqual(4.0, j["agregados"]["vm"]["durante"]["cpu_s"], delta=1e-5)
        self.assertEqual(sum(j["uteis"][f["rotulo"]] for f in j["fases"]), j["uteis"]["total"])
        self.assertEqual(j["steal"]["excluida"], False)


class EfContrato(Base):

    def cli(self, cenario="S1"):
        saida = self.d / "energia-rep-001.json"
        rc, out, err = rodar_cli("--execucao", self.d, "--rep", 1, "--cenario", cenario, "--saida", saida)
        return rc, saida, err

    def eventos(self):
        escrever_eventos(self.d, 1, [("Login", "OK", T0 + S + i, T0 + S + i + 1) for i in range(10)])

    def test_cabecalho_recursos_diferente(self):
        escrever_recursos(self.d, 1, {"vm": linear(1, 0)},
                          cabecalho="t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_ticks,estado")
        self.eventos()
        rc, saida, err = self.cli()
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())

    def test_meta_outro_contrato(self):
        escrever_recursos(self.d, 1, {"vm": linear(1, 0)}, contrato="recursos/2")
        self.eventos()
        rc, saida, err = self.cli()
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())

    def test_s3_oito_colunas(self):
        escrever_recursos(self.d, 1, {"vm": linear(1, 0)})
        escrever_s3(self.d, 1, [(T0 + S, T0 + S + 5, 0, 1, False)], oito_colunas=True)
        rc, saida, err = self.cli("S3")
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())

    def test_csv_eventos_sem_meta_contrato_2(self):
        escrever_recursos(self.d, 1, {"vm": linear(1, 0)})
        escrever_eventos(self.d, 1, [("Login", "OK", T0 + S, T0 + S + 1)], meta=None)
        rc, saida, err = self.cli()
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())

    def test_saida_existente(self):
        escrever_recursos(self.d, 1, {"vm": linear(1, 0)})
        self.eventos()
        saida = self.d / "energia-rep-001.json"
        saida.write_text("x", encoding="utf-8")
        rc, _, err = self.cli()
        self.assertEqual(2, rc, err)
        self.assertEqual("x", saida.read_text(encoding="utf-8"))

    def test_erro_com_numerico_preenchido(self):
        self.eventos()
        (self.d / "recursos-rep-001.csv").write_text(CAB_REC + "\n%d,vm,0,1,0,ok\n%d,vm,5,1,0,erro:leitura\n"
                                                     % (T0, T0 + S), encoding="utf-8")
        escrever_recursos(self.d / "x", 1, {"vm": linear(1, 0)})
        (self.d / "recursos-meta-rep-001.json").write_bytes((self.d / "x" / "recursos-meta-rep-001.json").read_bytes())
        rc, saida, err = self.cli()
        self.assertEqual(2, rc, err)


if __name__ == "__main__":
    unittest.main()
