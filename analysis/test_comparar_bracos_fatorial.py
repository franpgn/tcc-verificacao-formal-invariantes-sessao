"""Testes do W-5e: analise do fatorial 2x2 (regra de fusao x controlador) no comparar_bracos.py.

Especificacao: complemento 1 do pre-registro do benchmark refeito, DT-027 (docs/pre-registro-benchmark-refeito-
complemento-1-2026-11-06.md, branch docs/pre-registro-benchmark-refeito, commit 4fa4546), secoes 1, 2 e 5.

Nao regressao da analise de 2 bracos (classe NaoRegressao2Bracos): o cenario com sonda, varredura e varredura com
erro de cenario_golden_2bracos() foi rodado com o comparar_bracos.py da base edfa8b6, antes de qualquer mudanca do
W-5e, e gravado em analysis/fixtures/w5e/golden-2bracos-com-instrumentos-edfa8b6.json (python -E -P
analysis/test_comparar_bracos_fatorial.py --gerar-golden). O golden sem instrumentos do W-5c continua em
test_comparar_bracos_w5c.NaoRegressaoGolden.

Uso: python -E -P -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
import test_comparar_bracos_w5c as w5c  # noqa: E402  (geradores de execucoes com sonda e varredura)

GOLDEN_2B = AQUI / "fixtures" / "w5e" / "golden-2bracos-com-instrumentos-edfa8b6.json"


def cenario_golden_2bracos(raiz):
    """Celula de teste F2 do W-5d (6 blocos; varredura com erro so em A, so em B e nos dois) e celula descritiva D
    do W-5c (2 blocos com forma 2 da sonda), no mesmo manifesto."""
    raiz = Path(raiz)
    m1 = w5c.gerar_celula_varr(raiz, "F2")
    m2 = w5c.gerar_celula_inst(raiz, "D", 2, forma2_a=[1, 2], outras_a=[1, 0], outras_b=[0, 1])
    linhas = ["bloco\tcelula\tbraco\texecucao"]
    for m in (m1, m2):
        linhas += [x for x in m.read_text(encoding="utf-8").splitlines()[1:] if x.strip()]
    man = raiz / "manifesto-golden.tsv"
    man.write_text("\n".join(linhas) + "\n", encoding="utf-8")
    return man


def rodar_golden_2bracos(raiz):
    man = cenario_golden_2bracos(raiz)
    r = cb.comparar(man, 7, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60,
                    tipos_celula={"F2": "teste", "D": "descritiva"}, n_boot=200, n_hier=200)
    r = json.loads(json.dumps(r, sort_keys=True, allow_nan=False))
    r.pop("manifesto", None)
    r.pop("gerado_por", None)
    for c in r["celulas"].values():
        c.pop("execucoes", None)
    return r


class NaoRegressao2Bracos(unittest.TestCase):
    """A analise de 2 bracos (com sonda, varredura e varredura com erro) e a da base edfa8b6."""

    def test_igual_a_base(self):
        golden = json.loads(GOLDEN_2B.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            r = rodar_golden_2bracos(tmp)
        difs = w5c.diferencas_subconjunto(golden, r)
        self.assertEqual([], difs[:20], "%d diferenca(s) em relacao ao golden da base" % len(difs))

    def test_golden_exercita_varredura_com_erro_e_forma2(self):
        golden = json.loads(GOLDEN_2B.read_text(encoding="utf-8"))
        f2 = golden["celulas"]["F2"]
        self.assertEqual(3, f2["violacoes"]["detalhe"]["varredura_com_erro"]["blocos"])
        self.assertIn("taxa_violacoes_por_1000:sem_forma2", golden["celulas"]["D"]["desfechos"])
        self.assertIn(f2["regra_iu"]["resultado"], ("melhoria", "sem-melhoria"))


# ============================================================================= fatorial 2x2 (DT-027)

S, MS, T0, INI, FIM = w5c.S, w5c.MS, w5c.T0, w5c.INI, w5c.FIM
T_FUSAO = FIM + 5 * S                       # primeira vista de fusao depois do end (DT-027, 2.2)
T_TARDIO = w5c.T_TARDIO                     # replay tardio, depois da fusao e da ultima acao S3
FUSAO = {"PO": "pnn", "PL": "pnn", "AO": "a1", "AL": "a1"}
CTL = {"PO": "shadow", "PL": "active", "AO": "shadow", "AL": "active"}
ARMS = ("PO", "PL", "AO", "AL")
AUSENTE = object()
V = "taxa_violacoes_por_1000"
L = "acao_s3_p99_com_espera"
PERDA, EXPO = "perda_permanente_sids", "exposicao_janela_sids"


def acoes_lat(fator=1.0, n=2100, ini=T0 + 8 * S, passo=20 * MS):
    """2100 acoes de T0 + 8 s a T0 + 50 s (2000 depois do start); latencia (2 ms + ...) x fator."""
    return [(ini + i * passo, ini + i * passo + int((2 * MS + (i % 97) * 13_000) * fator), False) for i in range(n)]


def escrever_execucao_json(d, celula, bloco, braco, fusao=None, controller=None, t_fusao=T_FUSAO, sem=()):
    j = {"i": 1, "celula": celula, "bloco": str(bloco), "braco": braco,
         "controller": CTL.get(braco) if controller is None else controller,
         "fusao": FUSAO.get(braco) if fusao is None else fusao,
         "comando": ["java", "-jar", "w.jar", "--servers", ",".join(w5c.SRV), "--sweep", "--s3-replay-probe"]}
    if t_fusao is not AUSENTE:
        j["t_fusao_ns"] = t_fusao
    for k in sem:
        j.pop(k, None)
    (Path(d) / "execucao.json").write_text(json.dumps(j), encoding="utf-8")


def escrever_braco(d, celula, bloco, braco, exp=0, perda=0, fator=1.0, erro=False, sonda=True, trilha=True,
                   modo=None, **ej):
    """exp: I1 online antes da fusao (V e exposicao); perda: I1 REAL so da varredura (V e perda permanente)."""
    ev, viol, varr = [], [], []
    for j in range(exp):
        sid = "ex-%d" % j
        ev.append(("Logout", "NONE", INI + (1 + j) * S, INI + (1 + j) * S + MS, "h:1", sid))
        viol.append((INI + (12 + j) * S, "I1", sid, w5c.MSG_I1 % "LOGOUT", "", ""))
    for j in range(perda):
        sid = "pp-%d" % j
        ev.append(("Logout", "NONE", INI + (1 + j) * S, INI + (1 + j) * S + MS, "h:1", sid))
        varr.append(("I1", sid, "REAL", w5c.DET_I1 % "LOGOUT"))
    w5c.escrever_inst(d, eventos=ev, violacoes=viol, varredura=None if erro else varr, varredura_erro=erro,
                      sonda=sonda, servidores=None, acoes=acoes_lat(fator),
                      trilha=w5c._trilha(modo or CTL[braco]) if trilha else None)
    escrever_execucao_json(d, celula, bloco, braco, **ej)


def gerar_fatorial(raiz, celula, espec, nome_man=None):
    """espec: {bloco: {braco: kwargs de escrever_braco}}; devolve o manifesto (braco = PO, PL, AO ou AL)."""
    raiz = Path(raiz)
    linhas = []
    for b in sorted(espec):
        for br in ARMS:
            d = raiz / celula / ("b%02d%s" % (b, br))
            escrever_braco(d, celula, b, br, **espec[b][br])
            linhas.append("%d\t%s\t%s\t%s" % (b, celula, br, Path(celula) / d.name))
    man = raiz / (nome_man or ("manifesto-%s.tsv" % celula))
    man.write_text("bloco\tcelula\tbraco\texecucao\n" + "\n".join(linhas) + "\n", encoding="utf-8")
    return man


def comparar(man, tipos, **kw):
    kw.setdefault("n_boot", 200)
    kw.setdefault("n_hier", 200)
    return cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula=tipos, **kw)


# contagens de I1 online antes da fusao por bloco: (PO, PL, AO, AL)
CONT_V = {1: (3, 2, 1, 0), 2: (2, 2, 1, 1), 3: (4, 1, 2, 2), 4: (1, 3, 0, 1), 5: (2, 0, 2, 0), 6: (3, 3, 3, 1)}
FATOR_L = {"PO": 1.00, "PL": 1.03, "AO": 0.98, "AL": 1.05}


def espec_contrastes():
    return {b: {br: {"exp": CONT_V[b][i], "fator": FATOR_L[br] * (1 + 0.01 * b)} for i, br in enumerate(ARMS)}
            for b in CONT_V}


def contrastes(y):
    """(A1, A4, interacao, simples) de um dicionario {braco: y}, pela secao 1 da DT-027."""
    a1 = 0.5 * ((y["AO"] - y["PO"]) + (y["AL"] - y["PL"]))
    a4 = 0.5 * ((y["PL"] - y["PO"]) + (y["AL"] - y["AO"]))
    it = (y["AL"] - y["AO"]) - (y["PL"] - y["PO"])
    return a1, a4, it


class BaseF(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()


class Contrastes(BaseF):

    def test_contrastes_de_v_por_bloco(self):
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        c = comparar(man, {"FAT": "teste"})["celulas"]["FAT"]
        self.assertTrue(c["fatorial"])
        self.assertEqual(list(ARMS), c["bracos"])
        e = c["desfechos"][V]["efeitos"]
        esp = {"A1": {}, "A4": {}, "interacao": {}}
        for b, cont in CONT_V.items():
            a1, a4, it = contrastes({br: cont[i] * 0.5 for i, br in enumerate(ARMS)})
            esp["A1"][str(b)], esp["A4"][str(b)], esp["interacao"][str(b)] = a1, a4, it
        for ef in ("A1", "A4", "interacao"):
            self.assertEqual(set(esp[ef]), set(e[ef]["d_b"]))
            for b, d in esp[ef].items():
                self.assertAlmostEqual(d, e[ef]["d_b"][b], places=12, msg="%s bloco %s" % (ef, b))
            ds = [esp[ef][str(b)] for b in sorted(CONT_V)]
            self.assertAlmostEqual(sum(ds) / len(ds), e[ef]["estimativa"], places=12)
            self.assertAlmostEqual(cb.permutacao_exata(ds), e[ef]["p_permutacao"], places=12)
            self.assertEqual(6, e[ef]["n_blocos"])

    def test_contrastes_do_p99_em_log(self):
        import math
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        c = comparar(man, {"FAT": "teste"})["celulas"]["FAT"]
        e = c["desfechos"][L]["efeitos"]
        ds = []
        for b in sorted(CONT_V):
            y = {br: math.log(cb.desfechos_execucao(self.d / "FAT" / ("b%02d%s" % (b, br)), 1, w5c.FASES, 60 * S,
                                                   w_analise_ns=5 * S)[0][L]["y"]) for br in ARMS}
            a1, _, _ = contrastes(y)
            ds.append(a1)
            self.assertAlmostEqual(a1, e["A1"]["d_b"][str(b)], places=12)
        self.assertAlmostEqual(math.exp(sum(ds) / len(ds)), e["A1"]["estimativa"], places=12)
        self.assertEqual("log-razao", c["desfechos"][L]["escala"])
        self.assertIsNotNone(e["A1"]["nao_inferioridade"]["p"])
        self.assertIsNotNone(e["A4"]["nao_inferioridade"]["limite_superior_ic_t"])

    def test_efeitos_simples_sem_p(self):
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        c = comparar(man, {"FAT": "teste"})["celulas"]["FAT"]
        es = c["desfechos"][V]["efeitos_simples"]
        pares = {"A1_controlador_desligado": ("AO", "PO"), "A1_controlador_ligado": ("AL", "PL"),
                 "A4_regra_atual": ("PL", "PO"), "A4_regra_a1": ("AL", "AO")}
        self.assertEqual(set(pares), set(es))
        for nome, (x, y) in pares.items():
            for b, cont in CONT_V.items():
                yy = {br: cont[i] * 0.5 for i, br in enumerate(ARMS)}
                self.assertAlmostEqual(yy[x] - yy[y], es[nome]["d_b"][str(b)], places=12)
            self.assertIsNone(es[nome]["p_permutacao"])
            self.assertIsNotNone(es[nome]["ic_t"])

    def test_interacao_fora_do_holm_e_da_iu(self):
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        r = comparar(man, {"FAT": "teste"})
        c = r["celulas"]["FAT"]
        self.assertIsNotNone(c["desfechos"][V]["efeitos"]["interacao"]["p_permutacao"])
        self.assertIsNone(c["desfechos"][V]["efeitos"]["interacao"]["p_holm"])
        self.assertEqual(["FAT:A1", "FAT:A4"], sorted(r["conclusao_global"]["celulas"]))
        self.assertEqual(2, r["conclusao_global"]["m"])
        self.assertEqual({"A1", "A4"}, set(c["efeitos_principais"]))


class RegraDeSucesso(BaseF):

    def test_holm_sobre_os_dois_efeitos(self):
        # 7 blocos: a A1 zera V (d_A1 < 0 em todos, p = 2/2^7), a A4 nao muda nada (d_A4 = 0)
        espec = {b: {"PO": {"exp": 2}, "PL": {"exp": 2}, "AO": {"exp": 0}, "AL": {"exp": 0}} for b in range(1, 8)}
        man = gerar_fatorial(self.d, "FAT", espec)
        r = comparar(man, {"FAT": "teste"})
        g = r["conclusao_global"]["celulas"]
        self.assertAlmostEqual(2 / 2 ** 7, g["FAT:A1"]["p_iu"], places=12)
        self.assertAlmostEqual(2 * 2 / 2 ** 7, g["FAT:A1"]["p_iu_holm"], places=12)
        self.assertEqual("melhoria", g["FAT:A1"]["resultado_celula"])
        self.assertTrue(g["FAT:A1"]["melhoria"])
        self.assertEqual(1.0, g["FAT:A4"]["p_iu"])
        self.assertFalse(g["FAT:A4"]["melhoria"])
        ep = r["celulas"]["FAT"]["efeitos_principais"]
        self.assertTrue(ep["A1"]["regra_iu"]["h_v"]["rejeitada"])
        self.assertEqual("nao-inferior", ep["A1"]["regra_iu"]["vazao"])
        self.assertTrue(ep["A1"]["regra_iu"]["rastreabilidade_integral"])
        self.assertEqual({"PO": 14, "PL": 14, "AO": 0, "AL": 0}, ep["A1"]["co_principal_v"]["contagem"])

    def test_h_v_nao_avaliavel_sem_violacao_nos_bracos_de_referencia(self):
        # referencia da A1 = PO e PL; da A4 = PO e AO
        espec = {b: {"PO": {}, "PL": {}, "AO": {"exp": 1}, "AL": {"exp": 1}} for b in range(1, 4)}
        man = gerar_fatorial(self.d, "FAT", espec)
        r = comparar(man, {"FAT": "teste"})
        ep = r["celulas"]["FAT"]["efeitos_principais"]
        self.assertFalse(ep["A1"]["co_principal_v"]["avaliavel"])
        self.assertEqual("nao-avaliavel", ep["A1"]["regra_iu"]["resultado"])
        self.assertTrue(ep["A4"]["co_principal_v"]["avaliavel"])
        self.assertEqual(1.0, r["conclusao_global"]["celulas"]["FAT:A1"]["p_iu_usado"])

    def test_secundarios_com_holm_por_efeito(self):
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        c = comparar(man, {"FAT": "teste"})["celulas"]["FAT"]
        for ef in ("A1", "A4"):
            sec = sorted(n for n, x in c["desfechos"].items()
                         if x["familia"] == "secundario" and x["efeitos"][ef]["p_permutacao"] is not None)
            self.assertIn(PERDA, sec)
            self.assertIn(EXPO, sec)
            esperado = cb.holm([c["desfechos"][n]["efeitos"][ef]["p_permutacao"] for n in sec])
            self.assertEqual(esperado, [c["desfechos"][n]["efeitos"][ef]["p_holm"] for n in sec])
            self.assertEqual(len(sec), c["efeitos_principais"][ef]["n_secundarios_holm"])
        for n, x in c["desfechos"].items():
            if x["familia"] != "secundario":
                for ef in ("A1", "A4", "interacao"):
                    self.assertIsNone(x["efeitos"][ef]["p_holm"], "%s %s" % (n, ef))

    def test_celula_descritiva_sem_p(self):
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        r = comparar(man, {"FAT": "descritiva"})
        c = r["celulas"]["FAT"]
        self.assertIsNone(c["desfechos"][V]["efeitos"]["A1"]["p_permutacao"])
        self.assertNotIn("regra_iu", c["efeitos_principais"]["A1"])
        self.assertEqual({}, r["conclusao_global"]["celulas"])


class PerdaEExposicao(BaseF):

    def escrever(self, d, t_fusao=T_FUSAO, sem=()):
        ev = [("Logout", "NONE", INI + 1 * S, INI + 1 * S + MS, "h:1", "sid-a"),
              ("Logout", "NONE", INI + 2 * S, INI + 2 * S + MS, "h:1", "sid-b"),
              ("Logout", "NONE", INI + 3 * S, INI + 3 * S + MS, "h:1", "sid-c"),
              ("ReplayValidate", "VALID", INI + 4 * S, INI + 4 * S + MS, "h:2", "sid-c"),
              ("Logout", "NONE", INI + 4 * S, INI + 4 * S + MS, "h:1", "sid-d"),
              ("ReplayValidate", "VALID", T_TARDIO, T_TARDIO + MS, "h:2", "sid-d"),
              ("Logout", "NONE", INI + 5 * S, INI + 5 * S + MS, "h:1", "sid-e"),
              ("Logout", "NONE", INI + 6 * S, INI + 6 * S + MS, "h:1", "sid-f"),
              ("Logout", "NONE", FIM + 4 * S, FIM + 4 * S + MS, "h:1", "sid-g"),
              ("ReplayValidate", "VALID", FIM + 6 * S, FIM + 6 * S + MS, "h:2", "sid-g"),
              ("Logout", "NONE", INI - 6 * S, INI - 6 * S + MS, "h:1", "sid-h")]
        viol = [(INI + 12 * S, "I1", "sid-a", w5c.MSG_I1 % "LOGOUT", "", ""),             # online antes da fusao
                (FIM + 8 * S, "I1", "sid-b", w5c.MSG_I1 % "LOGOUT", "", ""),              # online depois da fusao
                (INI + 4 * S + MS // 2, "I1", "sid-c", w5c.MSG_I1 % "LOGOUT", "", "sonda"),  # imediata antes
                (T_TARDIO + MS // 2, "I1", "sid-d", w5c.MSG_I1 % "LOGOUT", "", "sonda"),     # tardia
                (INI + 7 * S, "I4", "sid-f", w5c.MSG_I4 % ("LOGOUT", "ADMIN_BLOCK"), "", ""),  # I4: fora
                (FIM + 6 * S + MS // 2, "I1", "sid-g", w5c.MSG_I1 % "LOGOUT", "", "sonda"),  # imediata depois
                (INI - 5 * S, "I1", "sid-h", w5c.MSG_I1 % "LOGOUT", "", "")]               # antes do start
        varr = [("I1", "sid-c", "REAL", w5c.DET_I1 % "LOGOUT"), ("I1", "sid-d", "REAL", w5c.DET_I1 % "LOGOUT"),
                ("I1", "sid-e", "REAL", w5c.DET_I1 % "LOGOUT")]
        w5c.escrever_inst(d, eventos=ev, violacoes=viol, varredura=varr, servidores=None, acoes=acoes_lat())
        escrever_execucao_json(d, "FAT", 1, "PO", t_fusao=t_fusao, sem=sem)

    def desf(self, d):
        return cb.desfechos_execucao(d, 1, w5c.FASES, 60 * S, w_analise_ns=5 * S)

    def test_uniao_por_sid(self):
        self.escrever(self.d)
        out, info = self.desf(self.d)
        # exposicao: a (online antes) e c (imediata antes); perda: b (online depois), c, d e e (varredura, tardia)
        self.assertEqual(2.0, out[EXPO]["y"])
        self.assertEqual(4.0, out[PERDA]["y"])
        self.assertEqual(["sid-a", "sid-c"], info["fusao"]["sids_exposicao"])
        self.assertEqual(["sid-b", "sid-c", "sid-d", "sid-e"], info["fusao"]["sids_perda"])
        self.assertEqual(T_FUSAO, info["fusao"]["t_fusao_ns"])
        self.assertEqual(7, out[V]["contagem"])        # a, b, c, d, e, g (I1) e f (I4); h fica antes do start

    def test_sem_o_campo_nao_emite(self):
        self.escrever(self.d, t_fusao=AUSENTE)
        out, _ = self.desf(self.d)
        self.assertNotIn(PERDA, out)
        self.assertNotIn(EXPO, out)

    def test_fusao_nula_deixa_ausente(self):
        self.escrever(self.d, t_fusao=None)
        out, _ = self.desf(self.d)
        self.assertIsNone(out[PERDA]["y"])
        self.assertIn("fusao", out[PERDA]["motivo"])
        self.assertIsNone(out[EXPO]["y"])

    def test_varredura_com_erro_na_execucao_isolada(self):
        w5c.escrever_inst(self.d, violacoes=[(INI + 12 * S, "I1", "sid-a", w5c.MSG_I1 % "LOGOUT", "", "")],
                          varredura_erro=True, servidores=None, acoes=acoes_lat())
        escrever_execucao_json(self.d, "FAT", 1, "PO")
        out, _ = self.desf(self.d)
        self.assertIsNone(out[PERDA]["y"])
        self.assertEqual(1.0, out[EXPO]["y"])


class VarreduraComErroFatorial(BaseF):

    # com a varredura: PO 3, PL 2, AO 2, AL 1 pares; sem ela: PO 2, PL 2, AO 1, AL 0
    BRACO = {"PO": {"exp": 2, "perda": 1}, "PL": {"exp": 2}, "AO": {"exp": 1, "perda": 1}, "AL": {"perda": 1}}

    def espec(self, blocos=(1, 2, 3, 4), erro=None):
        out = {b: {br: dict(self.BRACO[br]) for br in ARMS} for b in blocos}
        for b, br in (erro or {}).items():
            out[b][br]["erro"] = True
        return out

    def test_quatro_bracos_sem_a_varredura(self):
        man = gerar_fatorial(self.d, "FAT", self.espec(erro={2: "AL"}))
        c = comparar(man, {"FAT": "teste"})["celulas"]["FAT"]
        e = c["desfechos"][V]
        self.assertEqual({"1": -0.5, "2": -0.75, "3": -0.5, "4": -0.5}, e["efeitos"]["A1"]["d_b"])
        self.assertEqual({"PO": 1.0, "PL": 1.0, "AO": 0.5, "AL": 0.0}, e["y_por_bloco"]["2"])
        self.assertEqual({"PO": 1.5, "PL": 1.0, "AO": 1.0, "AL": 0.5}, e["y_por_bloco"]["1"])
        p = c["desfechos"][PERDA]
        self.assertEqual(["1", "3", "4"], sorted(p["efeitos"]["A1"]["d_b"]))
        self.assertEqual(["2"], [x["bloco"] for x in p["blocos_excluidos"]])
        vc = c["violacoes"]["detalhe"]["varredura_com_erro"]
        self.assertEqual(1, vc["blocos"])
        self.assertEqual({"PO": 0, "PL": 0, "AO": 0, "AL": 1}, vc["por_braco"])
        x = vc["lista"][0]
        self.assertEqual(("2", ["AL"]), (x["bloco"], x["bracos_com_erro"]))
        self.assertEqual({"PO": {"com_varredura": 1.5, "sem_varredura": 1.0},
                          "PL": {"com_varredura": 1.0, "sem_varredura": 1.0},
                          "AO": {"com_varredura": 1.0, "sem_varredura": 0.5},
                          "AL": {"com_varredura": None, "sem_varredura": 0.0}}, x["v"])
        s = vc["sensibilidade_blocos_excluidos"]
        self.assertEqual(["1", "3", "4"], s["blocos"])
        self.assertAlmostEqual(-0.5, s["efeitos"]["A1"]["estimativa"], places=12)
        self.assertIsNone(s["efeitos"]["A1"]["p_permutacao"])

    def test_erro_em_dois_bracos(self):
        man = gerar_fatorial(self.d, "FAT", self.espec(erro={1: "PO", 3: "PL"}))
        c = comparar(man, {"FAT": "teste"})["celulas"]["FAT"]
        self.assertEqual({"1": -0.75, "2": -0.5, "3": -0.75, "4": -0.5}, c["desfechos"][V]["efeitos"]["A1"]["d_b"])
        self.assertEqual({"PO": 1, "PL": 1, "AO": 0, "AL": 0},
                         c["violacoes"]["detalhe"]["varredura_com_erro"]["por_braco"])

    def test_bloco_sem_falha_intacto(self):
        man = gerar_fatorial(self.d / "so-ok", "FAT", self.espec(blocos=(1, 3, 4)))
        ok = comparar(man, {"FAT": "teste"})["celulas"]["FAT"]
        man2 = gerar_fatorial(self.d / "com-erro", "FAT", self.espec(erro={2: "AL"}))
        com = comparar(man2, {"FAT": "teste"})["celulas"]["FAT"]
        for nome in (V, PERDA, EXPO, L):
            for ef in ("A1", "A4", "interacao"):
                for b in ("1", "3", "4"):
                    self.assertEqual(ok["desfechos"][nome]["efeitos"][ef]["d_b"][b],
                                     com["desfechos"][nome]["efeitos"][ef]["d_b"][b], "%s %s %s" % (nome, ef, b))
        self.assertEqual(0, ok["violacoes"]["detalhe"]["varredura_com_erro"]["blocos"])


class IdentificacaoDosBracos(BaseF):

    def base(self, **mut):
        espec = {1: {br: {"exp": 1} for br in ARMS}}
        for br, kw in mut.items():
            espec[1][br].update(kw)
        return gerar_fatorial(self.d, "FAT", espec)

    def recusa(self, man, tipo="teste"):
        with self.assertRaises(cb.ErroUso):
            comparar(man, {"FAT": tipo})

    def test_valido(self):
        comparar(self.base(), {"FAT": "teste"})

    def test_fusao_divergente(self):
        self.recusa(self.base(AO={"fusao": "pnn"}), "descritiva")

    def test_controller_divergente(self):
        self.recusa(self.base(PL={"controller": "shadow"}), "descritiva")

    def test_sem_campo_fusao(self):
        self.recusa(self.base(AL={"sem": ("fusao",)}), "descritiva")

    def test_bloco_divergente(self):
        man = self.base()
        p = self.d / "FAT" / "b01PO" / "execucao.json"
        j = json.loads(p.read_text(encoding="utf-8"))
        j["bloco"] = "9"
        p.write_text(json.dumps(j), encoding="utf-8")
        self.recusa(man, "descritiva")

    def test_sem_execucao_json(self):
        man = self.base()
        (self.d / "FAT" / "b01AO" / "execucao.json").unlink()
        self.recusa(man, "descritiva")

    def test_trilha_em_modo_trocado(self):
        self.recusa(self.base(AL={"modo": "shadow"}), "descritiva")

    def test_trilha_obrigatoria_no_teste(self):
        self.recusa(self.base(PO={"trilha": False}))

    def test_instrumentos_iguais_nos_quatro(self):
        self.recusa(self.base(AO={"sonda": False}))

    def test_manifesto_com_bracos_misturados(self):
        man = self.base()
        txt = man.read_text(encoding="utf-8").replace("\tPO\t", "\tA\t")
        man.write_text(txt, encoding="utf-8")
        self.recusa(man, "descritiva")

    def test_celula_com_blocos_de_2_e_de_4_bracos(self):
        man = self.base()
        extra = w5c.gerar_celula_inst(self.d / "x", "FAT", 1, forma2_a=[0], outras_a=[1], outras_b=[0])
        linhas = man.read_text(encoding="utf-8").splitlines()
        for x in extra.read_text(encoding="utf-8").splitlines()[1:]:
            bl, cel, br, ex = x.split("\t")
            linhas.append("\t".join(("2", cel, br, "x/" + ex)))
        man.write_text("\n".join(linhas) + "\n", encoding="utf-8")
        self.recusa(man, "descritiva")


class NaoRegressaoComFatorial(BaseF):

    def test_celula_de_2_bracos_igual_com_o_fatorial_no_manifesto(self):
        m2 = w5c.gerar_celula_varr(self.d, "F2")
        so = comparar(m2, {"F2": "teste"})
        mf = gerar_fatorial(self.d, "FAT", espec_contrastes())
        linhas = ["bloco\tcelula\tbraco\texecucao"]
        for m in (m2, mf):
            linhas += [x for x in m.read_text(encoding="utf-8").splitlines()[1:] if x.strip()]
        man = self.d / "juntos.tsv"
        man.write_text("\n".join(linhas) + "\n", encoding="utf-8")
        juntos = comparar(man, {"F2": "teste", "FAT": "teste"})
        self.assertEqual(so["celulas"]["F2"], juntos["celulas"]["F2"])
        self.assertEqual(3, juntos["conclusao_global"]["m"])
        self.assertEqual(["F2", "FAT:A1", "FAT:A4"], sorted(juntos["conclusao_global"]["celulas"]))


class SaidaFatorial(BaseF):

    def test_markdown(self):
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        md = cb.tabela_markdown(comparar(man, {"FAT": "teste"}))
        for txt in ("fatorial 2x2", "| `taxa_violacoes_por_1000` | co-principal-V | diferenca | A1 |", "interacao",
                    "Holm entre as hipoteses de teste", "efeitos simples"):
            self.assertIn(txt, md)

    def test_cli(self):
        man = gerar_fatorial(self.d, "FAT", espec_contrastes())
        saida = self.d / "fat.json"
        rc = cb.main(["--manifesto", str(man), "--semente-boot", "3", "--saida", str(saida), "--delta-latencia",
                      "0.10", "--w-analise-s", "5", "--w-recovery-s", "60", "--celulas-teste", "FAT",
                      "--n-boot", "200", "--n-hier", "200"])
        self.assertEqual(0, rc)
        r = json.loads(saida.read_text(encoding="utf-8"))
        self.assertTrue(r["celulas"]["FAT"]["fatorial"])
        self.assertIn("fatorial", r["parametros"])


if __name__ == "__main__":
    if "--gerar-golden" in sys.argv:
        if GOLDEN_2B.exists():
            sys.exit("golden ja existe (nunca sobrescreve): %s" % GOLDEN_2B)
        GOLDEN_2B.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            res = rodar_golden_2bracos(tmp)
        with open(GOLDEN_2B, "x", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(res, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n")
        print("golden gravado em %s" % GOLDEN_2B)
    else:
        unittest.main()
