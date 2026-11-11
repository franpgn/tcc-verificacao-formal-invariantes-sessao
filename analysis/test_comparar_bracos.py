"""Testes de analysis/comparar_bracos.py (B-39.3): C1..C6, E1..E3, violacoes e guardas.

Fixtures sinteticas geradas no proprio teste (execucoes em diretorios temporarios) e uma fixture
versionada, analysis/fixtures/b39/violacoes-rep-001.csv, no formato do auditor atual
(InvariantAuditor.dumpCsv, InvariantAuditor.java:251-278, com a coluna fase e a coluna extra
classificacao de WorkloadMain.java:460-467).

E1 roda 1 000 simulacoes sempre (cabe no orcamento de tempo); B39_LONGO=1 sobe para 5 000.
A CLI roda sem -I: o modulo usa numpy/scipy, que neste host estao no site do usuario (o mesmo arranjo
de `rebaseline.py tabelas`, test_rebaseline.py:191-193).

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import hashlib
import json
import math
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
from referencia_p99 import percentil  # noqa: E402

SCRIPT = AQUI / "comparar_bracos.py"
FIXTURE_VIOL = AQUI / "fixtures" / "b39" / "violacoes-rep-001.csv"
S = 1_000_000_000
T0 = 1_761_566_400 * S          # 2025-10-27T12:00:00Z
CAB_S3 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns"
CAB_EV = "op_id,operation,start_ns,end_ns,replica,return_code,key"
LONGO = os.environ.get("B39_LONGO") == "1"
COBERTURA = {}


def iso(t_ns):
    seg, frac = divmod(t_ns, S)
    import time
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(seg)) + (".%09dZ" % frac if frac else "Z")


def escrever_exec(d, acoes=None, eventos=None, marcadores=(T0 - S, T0 + 100_000 * S), energia=None,
                  violacoes=None, s3_oito=False):
    """acoes: [(t, t_fim, espera, tentativas, falha)]; eventos: [(op, rc, ini, fim)];
    energia: dict minimo do esquema energia-proxy/1; violacoes: [(t_ns, invariante, classificacao)]."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    if acoes is not None:
        if s3_oito:
            linhas = [CAB_S3.rsplit(",", 2)[0]] + ["%d,u,Validate,s,OK,%d,%s," % (t, n, "true" if f else "false")
                                                   for t, _, _, n, f in acoes]
        else:
            linhas = [CAB_S3] + ["%d,u%d,Validate,s,%s,%d,%s,,%d,%d" % (
                t, i, "ERROR_TRANSPORT" if f else "OK_VALIDATE:VALID", n, "true" if f else "false", tf, w)
                for i, (t, tf, w, n, f) in enumerate(acoes)]
        (d / "s3-acoes-rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    if eventos is not None:
        linhas = [CAB_EV] + ["%d,%s,%d,%d,isn1,%s,k%d" % (i, op, a, b, rc, i) for i, (op, rc, a, b) in enumerate(eventos)]
        (d / "rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
        (d / "meta-csv-rep-001.json").write_text('{"contrato_csv": 2}', encoding="utf-8")
    if marcadores is not None:
        (d / "marcadores.txt").write_text("fault=F2 node=isn3 event=start t_epoch_ns=%d\n"
                                          "fault=F2 node=isn3 event=end t_epoch_ns=%d\n" % marcadores,
                                          encoding="utf-8")
    if energia is not None:
        (d / "energia-rep-001.json").write_text(json.dumps(energia), encoding="utf-8")
    if violacoes is not None:
        linhas = ["instante,invariante,chave,mensagem,fase,classificacao"] + [
            "%s,%s,sid-%d,msg,durante,%s" % (iso(t), inv, i, cl) for i, (t, inv, cl) in enumerate(violacoes)]
        (d / "violacoes-rep-001.csv").write_text("\n".join(linhas) + "\n", encoding="utf-8")


def energia_min(cpu_s, uteis, excluida=False):
    return {"esquema": "energia-proxy/1", "fases": [{"rotulo": "durante", "t_ini_ns": T0, "t_fim_ns": T0 + S,
                                                     "duracao_s": 1.0}],
            "agregados": {"vm": {"durante": {"cpu_s": cpu_s, "marca": "ok"}, "total": {"cpu_s": cpu_s, "marca": "ok"}}},
            "uteis": {"durante": uteis, "total": uteis}, "steal": {"frac": 0.0, "limiar": 0.05, "excluida": excluida},
            # B39-R1 (G4 de 28/10): janela e parametros coerentes com os marcadores padrao de escrever_exec
            "janela": {"t0_ns": T0, "t1_ns": T0 + S, "origem": "acoes-s3"},
            "parametros": {"marcadores": "marcadores.txt", "w_recovery_s": 60.0, "convergencia": None}}


# argumentos obrigatorios do comparar-bracos/2 (W-5 e V-W5 P-5): os testes de CLI abaixo continuam
# conferindo a mesma recusa (manifesto, S3 de 8 colunas, n > 16, saida existente), agora com a chamada
# valida; o 60 do W_recovery e valor de teste, nao padrao
ARGS_V2 = ("--delta-latencia", 0.10, "--w-analise-s", 5, "--w-recovery-s", 60, "--celulas-teste", "C1")

# desfechos que o /2 analisa em diferenca, listados aqui (e nao importados do modulo testado)
DIFERENCA_ESPERADA = {"taxa_violacoes_por_1000", "delta_rec_s", "delta_rec_metodologia_s", "delta_conv_s",
                      "divergencia_persistente", "taxa_erro", "falhas_percebidas_por_1000"}


def escrever_trilha(d, braco):
    """decisoes-rep-001.jsonl minimo e integro (A-3 da V-W5: trilha obrigatoria nos dois bracos do teste)."""
    meta = {"tipo": "meta", "esquema": "decisoes-controlador/1.2", "rep": 1,
            "modo": "shadow" if braco == "A" else "active", "parametros": {"w_ns": 5 * S},
            "param_sha256": "a" * 64, "atuador": "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)",
            "politica": "por-assinatura"}
    dec = {"tipo": "decisao", "t_fim_ns": T0 + 5 * S, "t_decisao_ns": T0 + 5 * S + 1, "modo": meta["modo"],
           "perfil": "NORMAL", "transicao": False, "regra": "NENHUMA", "motivos": ["ESTAVEL"],
           "severidade": "NENHUMA", "sinais": {"taxa_erro": 0.0}, "param_sha256": "a" * 64}
    fim = {"tipo": "fim", "decisoes": 1, "acoes_pedidas": 0, "linhas_perdidas": 0}
    (Path(d) / "decisoes-rep-001.jsonl").write_text("".join(json.dumps(o) + "\n" for o in (meta, dec, fim)),
                                                    encoding="utf-8")


def acoes_sinteticas(rng, n, mu, sigma=0.5, espera_frac=0.0, p_falha=0.02, passo=10_000_000):
    lat = rng.lognormal(mu, sigma, n).astype(np.int64) + 1
    falha = rng.random(n) < p_falha
    tent = 1 + (rng.random(n) < 0.1)
    out = []
    for i in range(n):
        t = T0 + i * passo
        w = int(lat[i] * espera_frac)
        out.append((t, t + int(lat[i]) + w, w, int(tent[i]), bool(falha[i])))
    return out


def gerar_celula(raiz, celula, n_blocos, efeito, seed, n_acoes=1200, com_eventos=False, com_energia=False,
                 com_violacoes=False):
    """Escreve n_blocos pares A/B em raiz/celula e devolve as linhas do manifesto."""
    rng = np.random.default_rng(seed)
    linhas = []
    for b in range(1, n_blocos + 1):
        for br in ("A", "B"):
            mu = math.log(2e6) + rng.normal(0, 0.05) + (math.log(efeito) if br == "B" else 0.0)
            d = Path(raiz) / celula / ("b%02d%s" % (b, br))
            acoes = acoes_sinteticas(rng, n_acoes, mu, espera_frac=0.1 if br == "B" else 0.0)
            ev = None
            if com_eventos:
                ev = []
                for i in range(1100):
                    a = T0 + i * 5_000_000
                    rc = "ERROR_TRANSPORT" if rng.random() < (0.01 if br == "A" else 0.02) else "OK"
                    ev.append(("Login", rc, a, a + int(rng.lognormal(mu, 0.4))))
            en = energia_min(30.0 + rng.normal(0, 1) - (3 if br == "B" else 0), 1000) if com_energia else None
            viol = None
            if com_violacoes:
                viol = [(T0 + 5 * S, "I1", "")] if br == "B" and b == 1 else []
                viol += [(T0 + 6 * S, "I4", "")] * (1 + b % 2)
            escrever_exec(d, acoes=acoes, eventos=ev, energia=en, violacoes=viol)
            escrever_trilha(d, br)
            linhas.append("%d\t%s\t%s\t%s" % (b, celula, br, Path(celula) / d.name))
    return linhas


def escrever_manifesto(raiz, linhas, nome="manifesto.tsv"):
    p = Path(raiz) / nome
    p.write_text("bloco\tcelula\tbraco\texecucao\n" + "\n".join(linhas) + "\n", encoding="utf-8")
    return p


def rodar_cli(*args, cwd=None):
    r = subprocess.run([sys.executable, str(SCRIPT)] + [str(a) for a in args], capture_output=True, text=True,
                       cwd=cwd)
    return r.returncode, r.stdout, r.stderr


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()


# ============================================================================= C1..C4

class C1Permutacao(unittest.TestCase):

    def test_n4_enumeracao_manual(self):
        # |soma| >= 0,55 so na identidade, no oposto e nos dois arranjos que trocam so o sinal de 0,05
        self.assertEqual(0.25, cb.permutacao_exata([0.1, 0.2, -0.05, 0.3]))

    def test_n8_todos_iguais(self):
        self.assertEqual(2 / 256, cb.permutacao_exata([0.2] * 8))

    def test_bilateral_e_simetrico(self):
        d = [0.3, -0.1, 0.25, 0.05, 0.12, -0.02]
        self.assertEqual(cb.permutacao_exata(d), cb.permutacao_exata([-x for x in d]))

    def test_nulo_da_um(self):
        self.assertEqual(1.0, cb.permutacao_exata([0.1, -0.1, 0.2, -0.2]))

    def test_n_maior_que_16(self):
        self.assertEqual(2 / 2 ** 16, cb.permutacao_exata([0.1] * 16))
        with self.assertRaises(cb.ErroUso):
            cb.permutacao_exata([0.1] * 17)


class C2Holm(unittest.TestCase):

    def test_holm_step_down(self):
        for obtido, esperado in zip(cb.holm([0.01, 0.02, 0.03, 0.5]), [0.04, 0.06, 0.06, 0.5]):
            self.assertAlmostEqual(esperado, obtido, places=12)

    def test_ordem_de_entrada(self):
        for obtido, esperado in zip(cb.holm([0.5, 0.03, 0.01, 0.02]), [0.5, 0.06, 0.04, 0.06]):
            self.assertAlmostEqual(esperado, obtido, places=12)

    def test_difere_de_bonferroni_e_hochberg(self):
        ps = [0.02, 0.03, 0.04]
        h, b, hb = cb.holm(ps), cb.bonferroni(ps), cb.hochberg(ps)
        for x, y in zip(h, [0.06, 0.06, 0.06]):
            self.assertAlmostEqual(y, x, places=12)
        for x, y in zip(b, [0.06, 0.09, 0.12]):
            self.assertAlmostEqual(y, x, places=12)
        for x, y in zip(hb, [0.04, 0.04, 0.04]):
            self.assertAlmostEqual(y, x, places=12)
        self.assertNotEqual([round(x, 9) for x in h], [round(x, 9) for x in b])
        self.assertNotEqual([round(x, 9) for x in h], [round(x, 9) for x in hb])

    def test_teto_um(self):
        self.assertEqual([1.0, 1.0], cb.holm([0.6, 0.9]))


class C3RegraDeTres(unittest.TestCase):

    def test_n1000(self):
        lim, rot = cb.regra_de_tres(1000)
        self.assertEqual(0.00299125, round(lim, 8))
        self.assertIn("≈ 3/N", rot)

    def test_n30(self):
        lim, _ = cb.regra_de_tres(30)
        self.assertEqual(0.0950339, round(lim, 7))


class C4NaoInferioridade(unittest.TestCase):

    def analisar(self, razao, ruido):
        d = [math.log(razao) + x for x in ruido]
        return cb.analisar_desfecho([1.0] * len(d), [math.exp(x) for x in d], 1, 0, 2000)

    def test_095_pouca_variabilidade(self):
        an = self.analisar(0.95, [0.01, -0.01, 0.005, -0.005, 0.0, 0.008, -0.008, 0.0])
        self.assertGreaterEqual(an["ic_t"][0], 0.9)
        self.assertEqual("nao-inferior", cb.nao_inferioridade(an, 0.10))

    def test_085(self):
        an = self.analisar(0.85, [0.01, -0.01, 0.005, -0.005, 0.0, 0.008, -0.008, 0.0])
        self.assertEqual("inconclusivo", cb.nao_inferioridade(an, 0.10))

    def test_usa_limite_inferior_e_nao_a_media(self):
        # media 0,92 >= 0,90, mas o limite inferior do IC t fica abaixo de 0,90
        an = self.analisar(0.92, [0.1, -0.1, 0.12, -0.12, 0.05, -0.05, 0.15, -0.15])
        self.assertAlmostEqual(0.92, an["estimativa"], places=9)
        self.assertLess(an["ic_t"][0], 0.9)
        self.assertEqual("inconclusivo", cb.nao_inferioridade(an, 0.10))


# ============================================================================= C5, C6 e guardas

class C5Pareamento(Base):

    def base(self):
        for b in (1, 2):
            for br in "AB":
                escrever_exec(self.d / ("e%d%s" % (b, br)), acoes=[(T0 + i, T0 + i + 10, 0, 1, False) for i in range(5)])

    def cli(self, man):
        saida = self.d / "c.json"
        rc, out, err = rodar_cli("--manifesto", man, "--semente-boot", 1, *ARGS_V2,
                                 "--saida", saida, "--n-boot", 200, "--n-hier", 200)
        return rc, saida, err

    def test_bloco_sem_b(self):
        self.base()
        man = escrever_manifesto(self.d, ["1\tC1\tA\te1A", "1\tC1\tB\te1B", "2\tC1\tA\te2A"])
        rc, saida, err = self.cli(man)
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())
        with self.assertRaises(cb.ErroUso):
            cb.ler_manifesto(man)

    def test_blocos_trocados_de_celula(self):
        self.base()
        man = escrever_manifesto(self.d, ["1\tC1\tA\te1A", "1\tC2\tB\te1B", "2\tC1\tA\te2A", "2\tC1\tB\te2B"])
        rc, saida, err = self.cli(man)
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())

    def test_duplicado_e_braco_invalido_e_cabecalho(self):
        self.base()
        for linhas in (["1\tC1\tA\te1A", "1\tC1\tA\te2A", "1\tC1\tB\te1B"],
                       ["1\tC1\tA\te1A", "1\tC1\tC\te1B"],
                       ["1\tC1\tA\te1A", "1\tC1\tB\te1A"]):
            with self.subTest(linhas=linhas):
                with self.assertRaises(cb.ErroUso):
                    cb.ler_manifesto(escrever_manifesto(self.d, linhas))
        p = self.d / "m2.tsv"
        p.write_text("bloco\tcelula\tbraco\texec\n1\tC1\tA\te1A\n1\tC1\tB\te1B\n", encoding="utf-8")
        with self.assertRaises(cb.ErroUso):
            cb.ler_manifesto(p)

    def test_manifesto_valido(self):
        self.base()
        man = escrever_manifesto(self.d, ["2\tC1\tA\te2A", "2\tC1\tB\te2B", "1\tC1\tB\te1B", "1\tC1\tA\te1A"])
        m = cb.ler_manifesto(man)
        self.assertEqual(["1", "2"], list(m["C1"]))
        self.assertEqual((self.d / "e1A").resolve(), m["C1"]["1"]["A"].resolve())


class C6Determinismo(Base):

    @classmethod
    def setUpClass(cls):
        cls.tmpc = tempfile.TemporaryDirectory()
        cls.r = Path(cls.tmpc.name)
        linhas = gerar_celula(cls.r, "C1", 8, 0.9, 11, com_eventos=True, com_energia=True, com_violacoes=True)
        cls.man = escrever_manifesto(cls.r, linhas)

        def cli(semente, nome):
            rc, out, err = rodar_cli("--manifesto", cls.man, "--semente-boot", semente, *ARGS_V2,
                                     "--saida", cls.r / nome, "--n-boot", 2000, "--n-hier", 400, cwd=cls.r)
            assert rc == 0, err
            return json.loads((cls.r / nome).read_text(encoding="utf-8"))

        cls.j1 = cli(123, "s1a.json")
        cls.j2 = cli(123, "s1b.json")
        cls.j3 = cli(456, "s2.json")

    @classmethod
    def tearDownClass(cls):
        cls.tmpc.cleanup()

    def test_mesma_semente_mesmos_bytes(self):
        self.assertEqual((self.r / "s1a.json").read_bytes(), (self.r / "s1b.json").read_bytes())
        self.assertEqual((self.r / "s1a.md").read_bytes(), (self.r / "s1b.md").read_bytes())

    def test_outra_semente(self):
        a = self.j1["celulas"]["C1"]["desfechos"]["acao_s3_p99_com_espera"]
        b = self.j3["celulas"]["C1"]["desfechos"]["acao_s3_p99_com_espera"]
        self.assertEqual(a["ic_t"], b["ic_t"])
        self.assertNotEqual(a["ic_boot_blocos"], b["ic_boot_blocos"])
        self.assertNotEqual(a["ic_hierarquico"], b["ic_hierarquico"])

    def test_boot_de_blocos_so_depende_de_d_b(self):
        for nome, e in self.j1["celulas"]["C1"]["desfechos"].items():
            if e["n_blocos"] < 2:
                continue
            with self.subTest(nome=nome):
                d = [e["d_b"][b] for b in self.j1["celulas"]["C1"]["blocos"] if b in e["d_b"]]
                # /2 (W-5, V-W5 P-4): escala esperada pelo nome, lista explicita deste teste
                esperada = "diferenca" if nome in DIFERENCA_ESPERADA else "log-razao"
                self.assertEqual(esperada, e["escala"])
                exp = esperada == "log-razao"
                self.assertEqual(cb.ic_boot_blocos(d, 123, e["indice_semente"], 2000, exp=exp), e["ic_boot_blocos"])
                m = sum(d) / len(d)
                self.assertAlmostEqual(math.exp(m) if exp else m, e["estimativa"], places=12)

    def test_esquema_e_familias(self):
        j = self.j1
        self.assertEqual("comparar-bracos/2", j["esquema"])
        c = j["celulas"]["C1"]
        des = c["desfechos"]
        self.assertEqual({"acao_s3_p99_com_espera", "acao_s3_p99_sem_espera", "evento_p99:Login", "taxa_erro",
                          "falhas_percebidas_por_1000", "vazao_util", "cpu_por_1000_uteis",
                          "taxa_violacoes_por_1000", "delta_rec_s", "delta_conv_s", "divergencia_persistente",
                          "taxa_erro:razao", "falhas_percebidas_por_1000:razao"},
                         set(des))
        self.assertEqual("sensibilidade", des["taxa_erro:razao"]["familia"])
        self.assertEqual("sensibilidade", des["falhas_percebidas_por_1000:razao"]["familia"])
        self.assertEqual("co-principal-L", des["acao_s3_p99_com_espera"]["familia"])
        self.assertIsNone(des["acao_s3_p99_com_espera"]["p_holm"])
        self.assertEqual("nao-inferioridade", des["vazao_util"]["familia"])
        self.assertIsNone(des["vazao_util"]["p_holm"])
        self.assertIn(des["vazao_util"]["nao_inferioridade"]["resultado"], ("nao-inferior", "inconclusivo"))
        # /2: os unicos secundarios sem p sao os que o fixture nao calcula (marcador end alem da medicao e sem
        # resumo de convergencia); todo outro secundario tem p e entra no Holm
        sem_p = {n for n, e in des.items() if e["familia"] == "secundario" and e["p_permutacao"] is None}
        self.assertEqual({"delta_rec_s", "delta_conv_s", "divergencia_persistente"}, sem_p)
        sec = sorted(n for n, e in des.items() if e["familia"] == "secundario" and n not in sem_p)
        ajust = cb.holm([des[n]["p_permutacao"] for n in sec])
        for n, pa in zip(sec, ajust):
            self.assertAlmostEqual(pa, des[n]["p_holm"], places=12)
        for n in ("acao_s3_p99_com_espera", "acao_s3_p99_sem_espera", "evento_p99:Login"):
            self.assertIsNotNone(des[n]["ic_hierarquico"], n)
        for n in ("taxa_erro", "vazao_util", "cpu_por_1000_uteis", "falhas_percebidas_por_1000"):
            self.assertIsNone(des[n]["ic_hierarquico"], n)
        for k in ("estimativa", "ic_t", "ic_boot_blocos", "ic_hierarquico", "p_permutacao", "p_holm", "n_blocos",
                  "blocos_excluidos"):
            self.assertIn(k, des["acao_s3_p99_sem_espera"])

    def test_violacoes(self):
        v = self.j1["celulas"]["C1"]["violacoes"]
        self.assertTrue(v["presente"])
        # I1 so no braco B do bloco 1 => zero em A: regra de tres sobre as acoes de A
        self.assertEqual({"A": 0, "B": 1}, v["I1"]["contagem"])
        n_a = v["I1"]["n_base"]["A"]
        self.assertEqual(8 * 1200, n_a)
        self.assertAlmostEqual(1 - 0.05 ** (1 / n_a), v["I1"]["regra_de_tres"]["A"]["limite_superior_95"], places=15)
        self.assertNotIn("B", v["I1"]["regra_de_tres"])
        # I4 nos dois bracos => razao de taxas com IC de blocos
        self.assertIn("razao_de_taxas", v["I4"])
        self.assertAlmostEqual(1.0, v["I4"]["razao_de_taxas"]["estimativa"], places=12)
        # I2 REAL zero nos dois
        self.assertEqual({"A", "B"}, set(v["I2_REAL"]["regra_de_tres"]))


class Guardas(Base):

    def test_s3_oito_colunas(self):
        linhas = []
        for b in (1, 2):
            for br in "AB":
                escrever_exec(self.d / ("e%d%s" % (b, br)), acoes=[(T0 + i, T0 + i + 10, 0, 1, False) for i in range(5)],
                              s3_oito=(b == 2 and br == "B"))
                linhas.append("%d\tC1\t%s\te%d%s" % (b, br, b, br))
        man = escrever_manifesto(self.d, linhas)
        saida = self.d / "c.json"
        rc, _, err = rodar_cli("--manifesto", man, "--semente-boot", 1, *ARGS_V2,
                               "--saida", saida, "--n-boot", 200, "--n-hier", 200)
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())

    def test_n_maior_que_16_blocos(self):
        linhas = []
        for b in range(1, 18):
            for br in "AB":
                escrever_exec(self.d / ("e%d%s" % (b, br)),
                              acoes=[(T0 + i * S // 10, T0 + i * S // 10 + 10, 0, 1, False) for i in range(5 + b)])
                linhas.append("%d\tC1\t%s\te%d%s" % (b, br, b, br))
        man = escrever_manifesto(self.d, linhas)
        saida = self.d / "c.json"
        rc, _, err = rodar_cli("--manifesto", man, "--semente-boot", 1, *ARGS_V2,
                               "--saida", saida, "--n-boot", 200, "--n-hier", 200)
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())

    def test_saida_existente(self):
        linhas = []
        for br in "AB":
            escrever_exec(self.d / ("e1" + br), acoes=[(T0 + i, T0 + i + 10, 0, 1, False) for i in range(5)])
            linhas.append("1\tC1\t%s\te1%s" % (br, br))
        man = escrever_manifesto(self.d, linhas)
        saida = self.d / "c.json"
        (self.d / "c.md").write_text("x", encoding="utf-8")
        rc, _, err = rodar_cli("--manifesto", man, "--semente-boot", 1, *ARGS_V2,
                               "--saida", saida, "--n-boot", 200, "--n-hier", 200)
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())
        self.assertEqual("x", (self.d / "c.md").read_text(encoding="utf-8"))


class ExclusoesERegistro(Base):

    def test_n_menor_que_1000_e_steal(self):
        rng = np.random.default_rng(5)
        linhas = []
        for b in range(1, 5):
            for br in "AB":
                n = 900 if (b == 2 and br == "A") else 1100
                excl = (b == 3 and br == "B")
                escrever_exec(self.d / ("e%d%s" % (b, br)), acoes=acoes_sinteticas(rng, n, math.log(2e6)),
                              energia=energia_min(20.0 + b, 1000, excluida=excl))
                escrever_trilha(self.d / ("e%d%s" % (b, br)), br)
                linhas.append("%d\tC1\t%s\te%d%s" % (b, br, b, br))
        man = escrever_manifesto(self.d, linhas)
        r = cb.comparar(man, 9, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C1": "teste"},
                        n_boot=500, n_hier=200)
        c = r["celulas"]["C1"]
        p99 = c["desfechos"]["acao_s3_p99_com_espera"]
        self.assertEqual(3, p99["n_blocos"])
        self.assertEqual([{"bloco": "2", "motivo": "A: n<1000"}], p99["blocos_excluidos"])
        self.assertEqual(900, p99["n_por_bloco"]["2"]["A"])
        cpu = c["desfechos"]["cpu_por_1000_uteis"]
        self.assertEqual(3, cpu["n_blocos"])
        self.assertEqual([{"bloco": "3", "motivo": "steal>5%"}], cpu["blocos_excluidos"])
        self.assertEqual(["3"], c["blocos_excluidos_por_steal"])
        # o bloco 3 continua nos desfechos que nao sao de CPU
        self.assertIn("3", c["desfechos"]["vazao_util"]["d_b"])
        self.assertIn("3", p99["d_b"])

    def test_fases_selecionadas(self):
        # metade das acoes antes do start: so a outra metade entra em durante,recuperacao
        rng = np.random.default_rng(6)
        linhas = []
        for br in "AB":
            ac = acoes_sinteticas(rng, 2400, math.log(2e6), passo=10_000_000)
            escrever_exec(self.d / ("e1" + br), acoes=ac, marcadores=(T0 + 12 * S, T0 + 30 * S))
            linhas.append("1\tC1\t%s\te1%s" % (br, br))
        out, info = cb.desfechos_execucao(self.d / "e1A", 1, ["durante", "recuperacao"], 60 * S)
        self.assertEqual(1200, info["n_acoes"])
        self.assertEqual(1200, out["acao_s3_p99_com_espera"]["n"])
        out2, info2 = cb.desfechos_execucao(self.d / "e1A", 1, ["antes"], 60 * S)
        self.assertEqual(1200, info2["n_acoes"])


    def test_replay_validate_fora_dos_desfechos(self):
        # W-10: os eventos da sonda de replay (ReplayValidate) nao entram no p99 por operacao, na taxa de
        # erro nem nas fronteiras da execucao (a vazao util nao muda com o replay tardio depois da carga)
        rng = np.random.default_rng(7)
        ac = acoes_sinteticas(rng, 1200, math.log(2e6), passo=10_000_000)
        ev = [("Validate", "VALID", T0 + i * 10_000_000, T0 + i * 10_000_000 + 2_000_000) for i in range(1200)]
        escrever_exec(self.d / "sem", acoes=ac, eventos=ev, marcadores=(T0 + 2 * S, T0 + 6 * S))
        sonda = [("ReplayValidate", "ERROR_TRANSPORT", T0 + 3 * S, T0 + 7 * S),
                 ("ReplayValidate", "VALID", T0 + 60 * S, T0 + 61 * S)]
        escrever_exec(self.d / "com", acoes=ac, eventos=ev + sonda, marcadores=(T0 + 2 * S, T0 + 6 * S))
        o1, _ = cb.desfechos_execucao(self.d / "sem", 1, ["durante", "recuperacao"], 60 * S)
        o2, _ = cb.desfechos_execucao(self.d / "com", 1, ["durante", "recuperacao"], 60 * S)
        self.assertNotIn("evento_p99:ReplayValidate", o2)
        self.assertEqual(o1["taxa_erro"]["y"], o2["taxa_erro"]["y"])
        self.assertEqual(o1["vazao_util"]["y"], o2["vazao_util"]["y"])


# ============================================================================= violacoes (Y16)

class Violacoes(unittest.TestCase):

    def test_fixture_formato_do_auditor(self):
        # So I1, I4 e I2 com classificacao REAL contam (DT-009; DT-001/DT-002): I2 de outra classe,
        # I2_JANELA_CASCATA, I2_CASCATA_INCOMPLETA, I3 e I5 ficam fora.
        self.assertEqual({"I1": 2, "I2_REAL": 1, "I4": 1}, cb.contar_violacoes(FIXTURE_VIOL))

    def test_filtro_por_instante(self):
        corte = cb.instante_ns("2026-10-27T12:00:03Z")
        self.assertEqual({"I1": 2, "I2_REAL": 0, "I4": 0}, cb.contar_violacoes(FIXTURE_VIOL, lambda t: t < corte))

    def test_instante(self):
        base = cb.instante_ns("2026-10-27T12:00:00Z")
        self.assertEqual(base + 100_000_000, cb.instante_ns("2026-10-27T12:00:00.100Z"))
        self.assertEqual(base + 123_456_789, cb.instante_ns("2026-10-27T12:00:00.123456789Z"))
        self.assertEqual(base % S, 0)
        with self.assertRaises(cb.ErroUso):
            cb.instante_ns("27/10/2026")

    def test_arquivo_ausente(self):
        self.assertIsNone(cb.contar_violacoes(AQUI / "fixtures" / "b39" / "nao-existe.csv"))


# ============================================================================= E1..E3 e H0

def sim_pares(rng, n=8, tau=0.15 / math.sqrt(2), n_ev=2000, sigma=0.5, efeito=0.9):
    pares = []
    for _ in range(n):
        ea, eb = rng.normal(0, tau, 2) if tau > 0 else (0.0, 0.0)
        a = np.sort(rng.lognormal(math.log(2e6) + ea, sigma, n_ev))
        b = np.sort(rng.lognormal(math.log(2e6) + eb + math.log(efeito), sigma, n_ev))
        pares.append((a, b))
    return pares


def y_de(pares):
    return ([percentil(a.tolist(), 0.99) for a, _ in pares], [percentil(b.tolist(), 0.99) for _, b in pares])


class E1Cobertura(unittest.TestCase):

    def test_cobertura(self):
        n_sim = 5000 if LONGO else 1000
        rng = np.random.default_rng(20261027)
        dentro = {"t": 0, "percentil_blocos": 0, "hierarquico": 0, "eventos": 0}
        for s in range(n_sim):
            # (a) d_b ~ Normal(ln 0,9; 0,15), n = 8: intervalo t e bootstrap percentil sobre blocos
            d = rng.normal(math.log(0.9), 0.15, 8)
            an = cb.analisar_desfecho([1.0] * 8, np.exp(d).tolist(), 77, s, 10000)
            dentro["t"] += an["ic_t"][0] <= 0.9 <= an["ic_t"][1]
            dentro["percentil_blocos"] += an["ic_boot_blocos"][0] <= 0.9 <= an["ic_boot_blocos"][1]
            # (b) eventos com efeito entre execucoes (sd de d_b ~ 0,16): hierarquico e "eventos juntos"
            pares = sim_pares(rng)
            ya, yb = y_de(pares)
            an = cb.analisar_desfecho(ya, yb, 77, s, 2000, pares=pares, n_hier=2000)
            dentro["hierarquico"] += an["ic_hierarquico"][0] <= 0.9 <= an["ic_hierarquico"][1]
            _, ic = cb.ic_eventos_agrupados(pares, 77 + s, 2000)
            dentro["eventos"] += ic[0] <= 0.9 <= ic[1]
        cob = {k: v / n_sim for k, v in dentro.items()}
        COBERTURA.update(cob, n_sim=n_sim)
        sys.stderr.write("\n[E1] cobertura com %d simulacoes: %s\n" % (n_sim, json.dumps(cob, sort_keys=True)))
        self.assertGreaterEqual(cob["t"], 0.93)
        # documenta a pseudorreplicacao (A8): eventos juntos cobrem visivelmente menos que o t
        self.assertLess(cob["eventos"], cob["t"] - 0.15)


class E2EfeitoConhecido(unittest.TestCase):

    def test_efeito_08(self):
        rng = np.random.default_rng(80)
        pares = sim_pares(rng, tau=0.03, n_ev=5000, efeito=0.8)
        ya, yb = y_de(pares)
        an = cb.analisar_desfecho(ya, yb, 3, 0, 10000)
        self.assertGreaterEqual(an["estimativa"], 0.75)
        self.assertLessEqual(an["estimativa"], 0.85)
        self.assertLess(an["p_permutacao"], 0.05)

    def test_sem_efeito_nivel(self):
        rng = np.random.default_rng(81)
        nao_rejeita = 0
        for s in range(200):
            ya = np.exp(rng.normal(math.log(2e6), 0.15, 8)).tolist()
            yb = np.exp(rng.normal(math.log(2e6), 0.15, 8)).tolist()
            nao_rejeita += cb.analisar_desfecho(ya, yb, 3, s, 200)["p_permutacao"] > 0.05
        self.assertGreaterEqual(nao_rejeita, 180)


class E3Hierarquico(unittest.TestCase):

    def razao_media(self, tau, n_ev, sigma, seeds):
        rs = []
        for s in seeds:
            pares = sim_pares(np.random.default_rng(s), tau=tau, n_ev=n_ev, sigma=sigma, efeito=1.0)
            ya, yb = y_de(pares)
            an = cb.analisar_desfecho(ya, yb, 5, 0, 10000, pares=pares, n_hier=2000)
            larg = lambda ic: math.log(ic[1]) - math.log(ic[0])  # noqa: E731
            rs.append(larg(an["ic_hierarquico"]) / larg(an["ic_boot_blocos"]))
        return sum(rs) / len(rs)

    def test_larguras(self):
        so_dentro = self.razao_media(0.0, 1000, 1.0, range(300, 310))
        so_entre = self.razao_media(0.3, 20000, 0.5, range(400, 410))
        COBERTURA.update(razao_larguras_so_dentro=so_dentro, razao_larguras_so_entre=so_entre)
        sys.stderr.write("\n[E3] largura hierarquico / blocos: so dentro = %.3f, so entre = %.3f\n"
                         % (so_dentro, so_entre))
        self.assertGreater(so_dentro, 1.15)
        self.assertLess(abs(so_entre - 1.0), 0.08)


class H0SorteioDaOrdem(unittest.TestCase):
    """O atalho Beta de quantil_reamostrado tem a mesma distribuicao da reamostragem ingenua."""

    def test_equivalencia(self):
        for n, p in ((50, 0.99), (200, 0.9), (1000, 0.99)):
            with self.subTest(n=n, p=p):
                x = np.arange(n, dtype=float)
                k = math.ceil(round(p * n, 9))
                m = 20000
                rng = np.random.default_rng(n)
                ing = np.sort(rng.integers(0, n, size=(m, n)), axis=1)[:, k - 1]
                beta = cb.quantil_reamostrado([x], p, np.random.default_rng(n + 1), np.zeros(m, dtype=np.int64))
                fi = np.bincount(ing.astype(int), minlength=n) / m
                fb = np.bincount(beta.astype(int), minlength=n) / m
                self.assertLess(np.abs(fi - fb).max(), 0.015)
                self.assertLess(abs(ing.mean() - beta.mean()), 0.02 * max(1.0, ing.std()))


if __name__ == "__main__":
    unittest.main()
