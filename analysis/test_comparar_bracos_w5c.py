"""Testes do W-5c: V com a sonda de replay (W-10) e a varredura final no comparar_bracos.py.

Especificacao: rascunho do pre-registro do benchmark refeito (docs/pre-registro-benchmark-refeito-2026-11-06.md,
branch docs/pre-registro-benchmark-refeito, commit a5ddac9), secao 2 (metodo de observacao) e secao 8.

Nao regressao (classe NaoRegressaoGolden): o cenario sem sonda e sem varredura de cenario_golden() foi rodado
com o comparar_bracos.py da base daa18a2 (feature/controlador-integracao), antes de qualquer mudanca do W-5c, e
gravado em analysis/fixtures/w5c/golden-sem-sonda-daa18a2.json (python -E -P analysis/test_comparar_bracos_w5c.py
--gerar-golden). O teste exige que cada chave do golden exista no resultado atual com o mesmo valor; chaves novas
sao permitidas. Os caminhos (manifesto, execucoes) e o SHA-256 do script ficam fora da comparacao.

W-5d (V-W5c, docs/validacao-w5c-2026-11-06.md, secao 5): varredura com erro pelo tratamento simetrico (classes
TotaisSemVarredura e VarreduraSimetrica).

Uso: python -E -P -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio; numpy e scipy no
site do usuario, decisao G2 de 28/10)
"""
import csv
import json
import sys
import tempfile
import time
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402

GOLDEN = AQUI / "fixtures" / "w5c" / "golden-sem-sonda-daa18a2.json"
S = 1_000_000_000
MS = 1_000_000
T0 = 1_761_566_400 * S          # 2025-10-27T12:00:00Z
INI, FIM = T0 + 10 * S, T0 + 30 * S
CAB_S3 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns"
CAB_EV = "op_id,operation,start_ns,end_ns,replica,return_code,key"
CAB_VIOL = "instante,invariante,chave,mensagem,fase,classificacao"
SHA_PARAM = "a" * 64
ATUADOR = "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)"


def iso(t_ns):
    seg, frac = divmod(t_ns, S)
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(seg)) + (".%09dZ" % frac if frac else "Z")


def _trilha(modo):
    meta = {"tipo": "meta", "esquema": "decisoes-controlador/1.2", "versao_programa": "teste", "rep": 1,
            "modo": modo, "cenario": "S3", "replicacao": "async", "nos_esperados": 3, "parametros": {"w_ns": 5 * S},
            "param_sha256": SHA_PARAM, "atuador": ATUADOR, "politica": "por-assinatura"}
    dec = {"tipo": "decisao", "t_fim_ns": INI + 5 * S, "t_decisao_ns": INI + 5 * S + 1, "modo": modo,
           "perfil": "CONSERVADOR", "transicao": True, "regra": "R1", "motivos": ["VIOLACAO"],
           "severidade": "ALTA", "sinais": {"taxa_erro": 0.0}, "param_sha256": SHA_PARAM}
    acao = {"tipo": "acao", "operacao": "APLICAR", "executada": modo == "active", "modo": modo, "atuador": "teste",
            "acao": {"tipo": "AC-2", "caches": ["sessions"], "alvo": None, "valor": None}, "origem": "POLITICA",
            "regra": "R1", "t_fim_janela_ns": INI + 5 * S, "t_pedido_ns": INI + 5 * S + 2,
            "t_inicio_ns": INI + 5 * S + 3, "duracao_ns": 10 * MS,
            "resultado": "APLICADA" if modo == "active" else "PREVISTA", "codigo_http": None, "lido": None,
            "erro": None, "reversao": None}
    fim = {"tipo": "fim", "decisoes": 1, "acoes_pedidas": 1, "linhas_perdidas": 0}
    return "".join(json.dumps(o) + "\n" for o in (meta, dec, acao, fim))


def escrever_exec_golden(d, b, braco, trilha):
    """Uma execucao sem sonda e sem varredura: acoes S3, eventos, marcadores, violacoes com chaves distintas,
    resumo de convergencia e (opcional) trilha. Deterministica em (b, braco)."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    fator = (1.0 + 0.01 * b) * (0.95 if braco == "B" else 1.0)
    acoes = []
    for i in range(1100):
        t = INI - 2 * S + i * 25 * MS
        lat = int((2 * MS + (i % 97) * 13_000) * fator)
        falha = (i * 7 + b + (braco == "B")) % 53 == 0
        acoes.append("%d,u%d,Validate,s,%s,1,%s,,%d,%d" % (
            t, i % 40, "ERROR_TRANSPORT" if falha else "OK_VALIDATE:VALID", "true" if falha else "false",
            t + lat, (i % 5) * 10_000))
    (d / "s3-acoes-rep-001.csv").write_bytes(("\n".join([CAB_S3] + acoes) + "\n").encode("utf-8"))
    ev = []
    for i in range(1200):
        a = INI - 2 * S + i * 23 * MS
        op = ("Validate", "Logout", "Login", "Block")[i % 4]
        rc = "ERROR_UNAVAILABLE" if (i + b) % 41 == 0 else {"Validate": "VALID", "Logout": "NONE",
                                                             "Login": "OK", "Block": "NONE"}[op]
        ev.append("%d,%s,%d,%d,isn%d,%s,k%d" % (i, op, a, a + int((1 + i % 7) * MS * fator), 1 + i % 3, rc, i))
    (d / "rep-001.csv").write_bytes(("\n".join([CAB_EV] + ev) + "\n").encode("utf-8"))
    (d / "meta-csv-rep-001.json").write_text('{"contrato_csv": 2}', encoding="utf-8")
    (d / "marcadores.txt").write_text("fault=F2 node=isn3 event=start t_epoch_ns=%d\n"
                                      "fault=F2 node=isn3 event=end t_epoch_ns=%d\n" % (INI, FIM), encoding="utf-8")
    n_i1 = {"A": (3, 1, 4, 2, 5, 3)[(b - 1) % 6], "B": (1, 0, 2, 1, 1, 0)[(b - 1) % 6]}[braco]
    viol = [(INI + (5 + j) * S + b * MS, "I1", "") for j in range(n_i1)]
    viol += [(INI + 2 * S, "I4", ""), (INI + 3 * S, "I2", "REAL"), (INI + 4 * S, "I2", "ESPERADA-POR-DESENHO"),
             (INI + 6 * S, "I2_JANELA_CASCATA", ""), (INI + 7 * S, "I3", ""), (INI - 5 * S, "I1", ""),
             (FIM + 70 * S, "I5", "")] if (b + (braco == "A")) % 2 == 0 else [(FIM + 2 * S, "I4", "")]
    linhas = [CAB_VIOL] + ["%s,%s,sid-%s%d-%d,msg,durante,%s" % (iso(t), inv, braco, b, i, cl)
                           for i, (t, inv, cl) in enumerate(viol)]
    (d / "violacoes-rep-001.csv").write_text("\n".join(linhas) + "\n", encoding="utf-8")
    resumo = {"t_fim_ns": FIM, "convergiu": True, "divergencia_persistente": False,
              "t_convergencia_abs_ns": T0 + 60 * S, "t_fim_carga_ns": T0 + 55 * S}
    (d / "convergencia-resumo-rep-001.json").write_text(json.dumps(resumo), encoding="utf-8")
    if trilha:
        (d / "decisoes-rep-001.jsonl").write_text(_trilha(cb.MODO_ESPERADO[braco]), encoding="utf-8")


def cenario_golden(raiz):
    """Manifesto com uma celula de teste (6 blocos, com trilha) e uma descritiva (2 blocos, sem trilha)."""
    raiz = Path(raiz)
    linhas = []
    for celula, n, trilha in (("T", 6, True), ("D", 2, False)):
        for b in range(1, n + 1):
            for br in "AB":
                d = raiz / celula / ("b%02d%s" % (b, br))
                escrever_exec_golden(d, b, br, trilha)
                linhas.append("%d\t%s\t%s\t%s" % (b, celula, br, Path(celula) / d.name))
    man = raiz / "manifesto.tsv"
    man.write_text("bloco\tcelula\tbraco\texecucao\n" + "\n".join(linhas) + "\n", encoding="utf-8")
    return man


def rodar_golden(raiz):
    """Resultado do cenario golden sem as chaves que dependem do caminho ou do script."""
    man = cenario_golden(raiz)
    r = cb.comparar(man, 11, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60,
                    tipos_celula={"T": "teste", "D": "descritiva"}, n_boot=200, n_hier=200)
    r = json.loads(json.dumps(r, sort_keys=True, allow_nan=False))
    r.pop("manifesto", None)
    r.pop("gerado_por", None)
    for c in r["celulas"].values():
        c.pop("execucoes", None)
    return r


def diferencas_subconjunto(esperado, obtido, caminho="$"):
    """Lista de caminhos em que `esperado` nao esta contido em `obtido` (dicionarios: chaves novas permitidas)."""
    if isinstance(esperado, dict):
        if not isinstance(obtido, dict):
            return ["%s: esperado objeto, obtido %r" % (caminho, type(obtido).__name__)]
        out = []
        for k, v in esperado.items():
            if k not in obtido:
                out.append("%s.%s: ausente" % (caminho, k))
            else:
                out += diferencas_subconjunto(v, obtido[k], "%s.%s" % (caminho, k))
        return out
    if esperado != obtido:
        return ["%s: esperado %r, obtido %r" % (caminho, esperado, obtido)]
    return []


class NaoRegressaoGolden(unittest.TestCase):
    """Sem sonda e sem varredura, o comparar_bracos.py devolve o mesmo resultado da base daa18a2."""

    def test_sem_sonda_sem_varredura_igual_a_base(self):
        golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            r = rodar_golden(tmp)
        difs = diferencas_subconjunto(golden, r)
        self.assertEqual([], difs[:20], "%d diferenca(s) em relacao ao golden da base" % len(difs))

    def test_golden_exercita_v_e_a_regra_iu(self):
        # guarda do proprio golden: a celula de teste tem V nos dois bracos, k > 0 e regra IU avaliada
        golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
        t = golden["celulas"]["T"]
        self.assertTrue(t["co_principal_v"]["avaliavel"])
        self.assertGreater(t["co_principal_v"]["contagem"]["A"], 0)
        self.assertGreater(t["desfechos"]["taxa_violacoes_por_1000"]["k_nao_nulos"], 0)
        self.assertIn(t["regra_iu"]["resultado"], ("melhoria", "sem-melhoria"))
        self.assertIn("D", golden["celulas"])



# ============================================================================= execucoes com sonda e varredura

SRV = ("h:1", "h:2", "h:3")                 # --servers na ordem isn1, isn2, isn3
FASES = ["durante", "recuperacao"]
CAB_VIOL_SONDA = CAB_VIOL + ",origem"
RESUMO_I = {"t_fim_ns": FIM, "convergiu": True, "divergencia_persistente": False,
            "t_convergencia_abs_ns": T0 + 60 * S, "t_fim_carga_ns": T0 + 55 * S}
T_TARDIO = T0 + 52 * S                      # depois da ultima acao S3 (T0 + 50 s)
FIXTURE_SMOKE = AQUI / "fixtures" / "w5c" / "smoke-w10-f2"
MSG_I1 = "invalidacao confirmada (%s) revertida para VALID"
MSG_I4 = "motivo inconsistente: confirmado %s, lido %s"
DET_I1 = "invalidacao confirmada (%s) e a sessao esta VALID"


def acoes_i(n=2100, ini=T0 + 8 * S, passo=20 * MS):
    """2100 acoes de T0 + 8 s a T0 + 50 s; 2000 delas a partir do start (INI = T0 + 10 s)."""
    return [(ini + i * passo, ini + i * passo + 2 * MS + (i % 50) * 10_000, False) for i in range(n)]


def escrever_inst(d, *, eventos=(), violacoes=(), varredura=None, sonda=True, marcadores=(INI, FIM),
                  servidores=SRV, acoes=None, resumo=RESUMO_I, s3_resumo=None, varredura_erro=False, trilha=None):
    """Execucao S3 com os instrumentos do benchmark refeito.

    eventos: [(op, rc, ini, fim, replica, chave)] (somados a uma carga de Validate em chaves proprias);
    violacoes: [(t, invariante, chave, mensagem, classificacao, origem)]; varredura: [(invariante, chave,
    classificacao, detalhe)] ou None (sem --sweep); servidores: None = sem execucao.json."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    acoes = acoes_i() if acoes is None else acoes
    linhas = [CAB_S3] + ["%d,u%d,Validate,s,%s,1,%s,,%d,0" % (
        t, i % 40, "ERROR_TRANSPORT" if f else "OK_VALIDATE:VALID", "true" if f else "false", tf)
        for i, (t, tf, f) in enumerate(acoes)]
    (d / "s3-acoes-rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    carga = [("Validate", "VALID", T0 + 8 * S + i * 100 * MS, T0 + 8 * S + i * 100 * MS + 2 * MS, "h:1", "c%d" % i)
             for i in range(400)]
    todos = sorted(list(eventos) + carga, key=lambda e: e[2])
    linhas = [CAB_EV] + ["%d,%s,%d,%d,%s,%s,%s" % (i, op, a, b, rep, rc, k)
                         for i, (op, rc, a, b, rep, k) in enumerate(todos)]
    (d / "rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    (d / "meta-csv-rep-001.json").write_text('{"contrato_csv": 2}', encoding="utf-8")
    if marcadores is not None:
        txt = "fault=F2 node=isn3 event=start t_epoch_ns=%d\n" % marcadores[0]
        if marcadores[1] is not None:
            txt += "fault=F2 node=isn3 event=end t_epoch_ns=%d\n" % marcadores[1]
        (d / "marcadores.txt").write_text(txt, encoding="utf-8")
    with open(d / "violacoes-rep-001.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow((CAB_VIOL_SONDA if sonda else CAB_VIOL).split(","))
        for t, inv, k, msg, cl, orig in violacoes:
            linha = [iso(t), inv, k, msg, "durante", cl]
            if sonda:
                linha.append(orig)
            elif orig:
                raise ValueError("origem sem a coluna da sonda")
            w.writerow(linha)
    if varredura is not None:
        linhas = ["invariante,chave,classificacao,detalhe"] + [",".join(x) for x in varredura]
        (d / "varredura-rep-001.csv").write_text("\n".join(linhas) + "\n", encoding="utf-8")
    if varredura_erro:
        (d / "varredura-rep-001.erro.txt").write_text("leitor do isn3 falhou\n", encoding="utf-8")
    if resumo is not None:
        (d / "convergencia-resumo-rep-001.json").write_text(json.dumps(resumo), encoding="utf-8")
    if servidores is not None:
        cmd = ["java", "-jar", "w.jar", "--scenario", "S3", "--servers", ",".join(servidores), "--sweep"]
        (d / "execucao.json").write_text(json.dumps({"i": 1, "comando": cmd}), encoding="utf-8")
    if s3_resumo is not None:
        (d / "s3-resumo-rep-001.json").write_text(json.dumps(s3_resumo), encoding="utf-8")
    if trilha is not None:
        (d / "decisoes-rep-001.jsonl").write_text(trilha, encoding="utf-8")


class BaseI(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def desf(self, d, fases=None):
        return cb.desfechos_execucao(d, 1, FASES if fases is None else fases, 60 * S, w_analise_ns=5 * S)


def contados(info):
    return sorted((p["chave"], p["classe"]) for p in info["violacoes_detalhe"]["pares_lista"] if p["conta"])


# ----------------------------------------------------------------------------- regra 2.3: pares distintos

def cenario_sobreposicao():
    """Online, sonda (imediata e tardia) e varredura sobre as mesmas chaves (pre-registro 2.3)."""
    ev = [("Logout", "NONE", INI + 1 * S, INI + 1 * S + MS, "h:1", "sid-A"),
          ("Logout", "NONE", INI + 2 * S, INI + 2 * S + MS, "h:1", "sid-C"),
          ("Block", "NONE", INI + 3 * S, INI + 3 * S + MS, "h:1", "u-1"),
          ("Block", "NONE", INI + 4 * S, INI + 4 * S + MS, "h:1", "u-2"),
          ("ReplayValidate", "VALID", INI + 3 * S, INI + 3 * S + MS, "h:2", "sid-A"),
          ("ReplayValidate", "INVALID", INI + 4 * S, INI + 4 * S + MS, "h:2", "sid-C"),
          ("ReplayValidate", "VALID", T_TARDIO, T_TARDIO + MS, "h:2", "sid-A"),
          ("ReplayValidate", "INVALID", T_TARDIO + 2 * MS, T_TARDIO + 3 * MS, "h:2", "sid-C")]
    viol = [(INI + 5 * S, "I1", "sid-A", MSG_I1 % "LOGOUT", "", ""),
            (INI + 3 * S + MS // 2, "I1", "sid-A", MSG_I1 % "LOGOUT", "", "sonda"),
            (T_TARDIO + MS // 2, "I1", "sid-A", MSG_I1 % "LOGOUT", "", "sonda"),
            (INI + 4 * S + MS // 2, "I4", "sid-C", MSG_I4 % ("LOGOUT", "ADMIN_BLOCK"), "", "sonda")]
    viol += [(INI + (6 + j) * S, "I2", "sid-B", "valida sob bloqueio", "REAL", "") for j in range(11)]
    viol += [(INI + 6 * S, "I2_JANELA_CASCATA", "sid-E", "valida sob bloqueio, dentro da janela da cascata", "", ""),
             (INI + 7 * S, "I2", "sid-G", "valida sob bloqueio", "INCERTA", ""),
             (FIM + 70 * S, "I5", "sid-A", "divergencia entre replicas", "", "")]
    varr = [("I1", "sid-A", "REAL", DET_I1 % "LOGOUT"),
            ("I4", "sid-C", "REAL", "motivo confirmado LOGOUT; lido ADMIN_BLOCK"),
            ("I2", "sid-D", "REAL", "sessao VALID de identidade u-2 BLOCKED (ADMINISTRATIVO)"),
            ("I2", "sid-F", "INCERTA", "sessao VALID de identidade u-5 BLOCKED (BLOQUEIO-INCERTO)"),
            ("I2-PERDIDO", "u-1", "REAL", "Block confirmado e identidade OPEN sem Unblock confirmado posterior"),
            ("I2-PERDIDO", "u-3", "INCERTA", "Block confirmado e identidade OPEN; Unblock posterior com erro")]
    return ev, viol, varr


class ParesDistintos(BaseI):

    def test_sobreposicao_das_tres_origens(self):
        ev, viol, varr = cenario_sobreposicao()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr)
        out, info = self.desf(self.d)
        v = out[cb.DESFECHO_V]
        # pares (sid-A, I1), (sid-B, I2), (sid-C, I4), (sid-D, I2), (u-1, I2); a contagem de linhas daria 19
        self.assertEqual(5, v["contagem"])
        self.assertEqual(19, v["linhas"])
        self.assertEqual(2000, v["n"])
        self.assertAlmostEqual(5 * 1000.0 / 2000, v["y"], places=12)
        self.assertEqual({"I1": 1, "I2_REAL": 3, "I4": 1}, info["violacoes"])
        det = info["violacoes_detalhe"]
        self.assertEqual(5, det["total_pares"])
        self.assertEqual(19, det["total_linhas"])
        self.assertEqual({"I1": 4, "I2_REAL": 13, "I4": 2}, det["linhas"])
        self.assertEqual({"online": {"linhas": 12, "pares": 2}, "sonda": {"linhas": 3, "pares": 2},
                          "varredura": {"linhas": 4, "pares": 4}}, det["por_origem"])
        self.assertEqual(2, det["pares_multiplas_origens"])
        self.assertTrue(det["sonda"])
        self.assertEqual("presente", det["varredura"])
        self.assertEqual(2, det["varredura_fora_de_v"])     # I2 INCERTA e I2-PERDIDO INCERTA
        a = [p for p in det["pares_lista"] if p["chave"] == "sid-A"][0]
        self.assertEqual(["online", "sonda", "varredura"], a["origens"])
        self.assertEqual(["imediata", "online", "tardia", "varredura"], sorted(x["tipo"] for x in a["deteccoes"]))

    def test_mesma_chave_invariantes_diferentes_contam_separado(self):
        ev = [("Logout", "NONE", INI + S, INI + S + MS, "h:1", "sid-A")]
        viol = [(INI + 5 * S, "I1", "sid-A", MSG_I1 % "LOGOUT", "", ""),
                (INI + 6 * S, "I4", "sid-A", MSG_I4 % ("LOGOUT", "ADMIN_BLOCK"), "", ""),
                (INI + 7 * S, "I1", "sid-A", MSG_I1 % "LOGOUT", "", "")]
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=[])
        out, _ = self.desf(self.d)
        self.assertEqual(2, out[cb.DESFECHO_V]["contagem"])
        self.assertEqual(3, out[cb.DESFECHO_V]["linhas"])

    def test_varredura_so_real_conta(self):
        varr = [("I2", "sid-F", "INCERTA", "sessao VALID de identidade u-5 BLOCKED (BLOQUEIO-INCERTO)"),
                ("I2", "sid-H", "ESPERADA-POR-DESENHO", "sessao VALID de identidade u-6 BLOCKED (POR-LIMITE)"),
                ("I2-PERDIDO", "u-3", "INCERTA", "Block confirmado e identidade OPEN; Unblock posterior com erro")]
        escrever_inst(self.d, violacoes=[], varredura=varr)
        out, info = self.desf(self.d)
        self.assertEqual(0, out[cb.DESFECHO_V]["contagem"])
        self.assertEqual(3, info["violacoes_detalhe"]["varredura_fora_de_v"])

    def test_contar_violacoes_por_par_distinto(self):
        p = self.d / "v.csv"
        p.write_text(CAB_VIOL + "\n" + "".join("2026-10-27T12:00:0%dZ,I2,sid-1,valida sob bloqueio,durante,REAL\n" % j
                                               for j in range(5)) +
                     "2026-10-27T12:00:06Z,I1,sid-1,x,durante,\n", encoding="utf-8")
        self.assertEqual({"I1": 1, "I2_REAL": 1, "I4": 0}, cb.contar_violacoes(p))

    def test_origem_desconhecida_recusada(self):
        escrever_inst(self.d, violacoes=[(INI + 5 * S, "I1", "sid-A", MSG_I1 % "LOGOUT", "", "outra")])
        with self.assertRaises(cb.ErroUso):
            self.desf(self.d)


# ----------------------------------------------------------------------------- 2.4: fases do tardio e da varredura

def cenario_fases():
    ev = [("Logout", "NONE", INI - 3 * S, INI - 3 * S + MS, "h:1", "sid-P1"),        # antes
          ("Logout", "NONE", INI + 2 * S, INI + 2 * S + MS, "h:1", "sid-P2"),        # durante
          ("Logout", "NONE", INI - 4 * S, INI - 4 * S + MS, "h:1", "sid-P4"),        # no-op depois da cascata
          ("Block", "NONE", INI - 6 * S, INI - 6 * S + MS, "h:1", "u-9"),
          ("Block", "ERROR_TRANSPORT", INI + 1 * S, INI + 1 * S + MS, "h:1", "u-9"),  # nao confirmado
          ("Block", "NONE", INI - 6 * S, INI - 6 * S + MS, "h:1", "u-8"),
          ("Block", "NONE", INI + 2 * S, INI + 2 * S + MS, "h:1", "u-8"),
          ("Block", "NONE", INI + 3 * S, INI + 3 * S + MS, "h:1", "u-7"),
          ("Logout", "NONE", INI - 2 * S, INI - 2 * S + MS, "h:1", "sid-P7"),
          ("ReplayValidate", "VALID", INI - 1 * S, INI - 1 * S + MS, "h:2", "sid-P7"),
          ("Logout", "ERROR_TRANSPORT", INI - 5 * S, INI - 5 * S + MS, "h:1", "sid-P8"),
          ("Logout", "NONE", INI + 5 * S, INI + 5 * S + MS, "h:1", "sid-P8"),
          ("ReplayValidate", "VALID", T_TARDIO, T_TARDIO + MS, "h:2", "sid-P1"),
          ("ReplayValidate", "VALID", T_TARDIO + 2 * MS, T_TARDIO + 3 * MS, "h:2", "sid-P2"),
          ("ReplayValidate", "VALID", T_TARDIO + 4 * MS, T_TARDIO + 5 * MS, "h:2", "sid-P8")]
    viol = [(T_TARDIO + MS // 2, "I1", "sid-P1", MSG_I1 % "LOGOUT", "", "sonda"),
            (T_TARDIO + 2 * MS + MS // 2, "I1", "sid-P2", MSG_I1 % "LOGOUT", "", "sonda"),
            (INI - 1 * S + MS // 2, "I1", "sid-P7", MSG_I1 % "LOGOUT", "", "sonda"),
            (T_TARDIO + 4 * MS + MS // 2, "I1", "sid-P8", MSG_I1 % "LOGOUT", "", "sonda")]
    varr = [("I1", "sid-P1", "REAL", DET_I1 % "LOGOUT"),
            ("I1", "sid-P3", "REAL", DET_I1 % "LOGOUT"),                  # Logout fora do CSV
            ("I1", "sid-P4", "REAL", DET_I1 % "ADMIN_BLOCK"),             # cascata: sem evento com a chave da sid
            ("I2", "sid-P5", "REAL", "sessao VALID de identidade u-9 BLOCKED (ADMINISTRATIVO)"),
            ("I2", "sid-P6", "REAL", "sessao VALID de identidade u-8 BLOCKED (ADMINISTRATIVO)"),
            ("I2-PERDIDO", "u-7", "REAL", "Block confirmado e identidade OPEN sem Unblock confirmado posterior"),
            ("I2-PERDIDO", "u-6", "REAL", "Block confirmado e identidade ausente sem Unblock confirmado posterior")]
    return ev, viol, varr


class FasesDoTardio(BaseI):

    def test_tardio_e_varredura_pelo_instante_da_invalidacao(self):
        ev, viol, varr = cenario_fases()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr)
        out, info = self.desf(self.d)
        self.assertEqual([("sid-P2", "I1"), ("sid-P3", "I1"), ("sid-P4", "I1"), ("sid-P6", "I2_REAL"),
                          ("sid-P8", "I1"), ("u-6", "I2_REAL"), ("u-7", "I2_REAL")], contados(info))
        self.assertEqual(7, out[cb.DESFECHO_V]["contagem"])
        self.assertEqual({"deteccao": 0, "invalidacao": 4, "sem_invalidacao_localizada": 3},
                         info["violacoes_detalhe"]["atribuicao"])

    def test_tardio_nao_usa_o_instante_da_propria_leitura(self):
        # o tardio de sid-P1 cai na recuperacao pelo instante da leitura, mas o Logout foi antes do start
        ev, viol, _ = cenario_fases()
        escrever_inst(self.d, eventos=ev, violacoes=viol[:1], varredura=[])
        out, info = self.desf(self.d)
        self.assertEqual(0, out[cb.DESFECHO_V]["contagem"])
        p = info["violacoes_detalhe"]["pares_lista"][0]
        self.assertEqual("tardia", p["deteccoes"][0]["tipo"])
        self.assertEqual("invalidacao", p["deteccoes"][0]["atribuicao"])
        self.assertEqual(INI - 3 * S + MS, p["deteccoes"][0]["t_atribuido_ns"])

    def test_fase_antes_conta_as_sem_invalidacao_localizada(self):
        ev, viol, varr = cenario_fases()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr)
        _, info = self.desf(self.d, fases=["antes"])
        self.assertEqual([("sid-P1", "I1"), ("sid-P3", "I1"), ("sid-P4", "I1"), ("sid-P5", "I2_REAL"),
                          ("sid-P7", "I1"), ("u-6", "I2_REAL")], contados(info))

    def test_imediata_pelo_instante_da_deteccao(self):
        ev = [("Logout", "NONE", INI - 2 * S, INI - 2 * S + MS, "h:1", "sid-X"),
              ("ReplayValidate", "VALID", INI + 1 * S, INI + 1 * S + MS, "h:2", "sid-X")]
        viol = [(INI + 1 * S + MS // 2, "I1", "sid-X", MSG_I1 % "LOGOUT", "", "sonda")]
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=[])
        out, info = self.desf(self.d)
        self.assertEqual(1, out[cb.DESFECHO_V]["contagem"])
        x = info["violacoes_detalhe"]["pares_lista"][0]["deteccoes"][0]
        self.assertEqual(("imediata", "deteccao"), (x["tipo"], x["atribuicao"]))


# ----------------------------------------------------------------------------- 2.5: formas de I1 e sensibilidade

def cenario_formas():
    """isn3 (h:3) e o no da falha. M1 forma 1; F2 e F2d forma 2; F2b, O e F2c outras."""
    ev = [("Logout", "NONE", INI + 1 * S, INI + 1 * S + MS, "h:3", "sid-M1"),
          ("ReplayValidate", "VALID", INI + 2 * S, INI + 2 * S + MS, "h:1", "sid-M1"),
          ("ReplayValidate", "VALID", T_TARDIO, T_TARDIO + MS, "h:1", "sid-M1"),
          ("Logout", "NONE", INI + 2 * S, INI + 2 * S + MS, "h:2", "sid-F2"),
          ("ReplayValidate", "VALID", INI + 4 * S, INI + 4 * S + MS, "h:3", "sid-F2"),
          ("Logout", "NONE", FIM - 1 * S, FIM - 1 * S + MS, "h:2", "sid-F2b"),
          ("ReplayValidate", "VALID", FIM + 1 * S, FIM + 1 * S + MS, "h:3", "sid-F2b"),
          ("Logout", "NONE", INI + 3 * S, INI + 3 * S + MS, "h:1", "sid-O"),
          ("Validate", "VALID", INI + 5 * S, INI + 5 * S + MS, "h:1", "sid-O"),
          ("Logout", "NONE", INI + 6 * S, INI + 6 * S + MS, "h:2", "sid-F2c"),
          ("ReplayValidate", "VALID", INI + 8 * S, INI + 8 * S + MS, "h:3", "sid-F2c"),
          ("Logout", "NONE", INI + 9 * S, INI + 9 * S + MS, "h:1", "sid-F2d"),
          ("Validate", "VALID", INI + 10 * S, INI + 10 * S + MS, "h:3", "sid-F2d"),
          ("Validate", "VALID", INI + 9 * S - 400 * MS, INI + 9 * S - 399 * MS, "h:1", "sid-F2d")]
    viol = [(INI + 2 * S + MS // 2, "I1", "sid-M1", MSG_I1 % "LOGOUT", "", "sonda"),
            (T_TARDIO + MS // 2, "I1", "sid-M1", MSG_I1 % "LOGOUT", "", "sonda"),
            # desvio de relogio do smoke do W-10: o instante da violacao ate 7,4 ms depois do fim da leitura
            (INI + 4 * S + MS + 7 * MS, "I1", "sid-F2", MSG_I1 % "LOGOUT", "", "sonda"),
            (FIM + 1 * S + MS // 2, "I1", "sid-F2b", MSG_I1 % "LOGOUT", "", "sonda"),
            (INI + 5 * S + MS // 2, "I1", "sid-O", MSG_I1 % "LOGOUT", "", ""),
            (INI + 8 * S + MS // 2, "I1", "sid-F2c", MSG_I1 % "LOGOUT", "", "sonda"),
            (INI + 10 * S + MS // 2, "I1", "sid-F2d", MSG_I1 % "LOGOUT", "", "")]
    varr = [("I1", "sid-M1", "REAL", DET_I1 % "LOGOUT"), ("I1", "sid-F2c", "REAL", DET_I1 % "LOGOUT")]
    return ev, viol, varr


class FormasDeI1(BaseI):

    def test_formas_e_sensibilidade_sem_forma2(self):
        ev, viol, varr = cenario_formas()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr)
        out, info = self.desf(self.d)
        det = info["violacoes_detalhe"]
        self.assertEqual("isn3", det["no_falha"])
        self.assertEqual("h:3", det["replica_no_falha"])
        self.assertTrue(det["formas_avaliaveis"])
        formas = {p["chave"]: p["forma"] for p in det["pares_lista"] if p["conta"]}
        self.assertEqual({"sid-M1": "forma1", "sid-F2": "forma2", "sid-F2b": "outra", "sid-O": "outra",
                          "sid-F2c": "outra", "sid-F2d": "forma2"}, formas)
        self.assertEqual({"forma1": 1, "forma2": 2, "outra": 3, "indeterminada": 0}, det["formas_i1"])
        self.assertEqual(6, out[cb.DESFECHO_V]["contagem"])
        s = out[cb.DESFECHO_V_SEM_F2]
        self.assertEqual(4, s["contagem"])
        self.assertAlmostEqual(4 * 1000.0 / 2000, s["y"], places=12)
        self.assertEqual(4, det["total_pares_sem_forma2"])
        f2c = [p for p in det["pares_lista"] if p["chave"] == "sid-F2c"][0]
        self.assertEqual(["forma2", "outra"], sorted(x["forma"] for x in f2c["deteccoes"]))

    def test_sem_execucao_json_formas_indeterminadas(self):
        ev, viol, varr = cenario_formas()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr, servidores=None)
        out, info = self.desf(self.d)
        det = info["violacoes_detalhe"]
        self.assertFalse(det["formas_avaliaveis"])
        self.assertEqual({"forma1": 0, "forma2": 0, "outra": 0, "indeterminada": 6}, det["formas_i1"])
        self.assertEqual(6, out[cb.DESFECHO_V]["contagem"])
        self.assertIsNone(out[cb.DESFECHO_V_SEM_F2]["y"])
        self.assertIn("forma", out[cb.DESFECHO_V_SEM_F2]["motivo"])

    def test_sem_falha_nao_ha_forma2(self):
        ev, viol, varr = cenario_formas()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr, marcadores=None)
        out, info = self.desf(self.d, fases=["antes"])
        det = info["violacoes_detalhe"]
        self.assertEqual(6, out[cb.DESFECHO_V]["contagem"])
        self.assertEqual(6, out[cb.DESFECHO_V_SEM_F2]["contagem"])
        self.assertEqual(0, det["formas_i1"]["forma2"])
        self.assertTrue(det["formas_avaliaveis"])

    def test_sensibilidade_so_com_instrumentos(self):
        escrever_inst(self.d, violacoes=[(INI + 5 * S, "I1", "sid-A", MSG_I1 % "LOGOUT", "", "")], sonda=False)
        out, info = self.desf(self.d)
        self.assertNotIn(cb.DESFECHO_V_SEM_F2, out)
        self.assertFalse(info["violacoes_detalhe"]["sonda"])
        self.assertEqual("ausente", info["violacoes_detalhe"]["varredura"])
        self.assertEqual(1, out[cb.DESFECHO_V]["contagem"])


# ----------------------------------------------------------------------------- 2.6: ReplayValidate fora de tudo

class ReplayValidateFora(BaseI):

    def test_fora_de_latencia_vazao_erro_e_denominador(self):
        ev_carga = [("Validate", "ERROR_TRANSPORT" if i % 9 == 0 else "VALID", T0 + 9 * S + i * 30 * MS,
                     T0 + 9 * S + i * 30 * MS + (1 + i % 4) * MS, "h:%d" % (1 + i % 3), "k%d" % i) for i in range(1300)]
        ac = [(t, tf, i % 37 == 0) for i, (t, tf, _) in enumerate(acoes_i())]
        sonda_ev = [("Logout", "NONE", INI + 1 * S, INI + 1 * S + MS, "h:1", "sid-A"),
                    ("ReplayValidate", "ERROR_UNAVAILABLE", INI + 2 * S, INI + 6 * S, "h:3", "sid-A"),
                    ("ReplayValidate", "VALID", INI + 7 * S, INI + 7 * S + 900 * MS, "h:2", "sid-B"),
                    ("ReplayValidate", "VALID", T_TARDIO, T_TARDIO + 3 * S, "h:2", "sid-A")]
        escrever_inst(self.d / "sem", eventos=ev_carga + sonda_ev[:1], acoes=ac, violacoes=[], sonda=False)
        escrever_inst(self.d / "com", eventos=ev_carga + sonda_ev, acoes=ac,
                      violacoes=[(T_TARDIO + MS, "I1", "sid-A", MSG_I1 % "LOGOUT", "", "sonda")],
                      varredura=[("I1", "sid-A", "REAL", DET_I1 % "LOGOUT")])
        o1, i1 = self.desf(self.d / "sem")
        o2, i2 = self.desf(self.d / "com")
        self.assertNotIn("evento_p99:ReplayValidate", o2)
        for nome in ("acao_s3_p99_com_espera", "acao_s3_p99_sem_espera", "vazao_util", "taxa_erro",
                     "falhas_percebidas_por_1000", "evento_p99:Validate", "delta_rec_s"):
            self.assertEqual(o1[nome]["y"], o2[nome]["y"], nome)
            self.assertEqual(o1[nome]["n"], o2[nome]["n"], nome)
        self.assertEqual(o1[cb.DESFECHO_V]["n"], o2[cb.DESFECHO_V]["n"])
        self.assertEqual(i1["n_acoes"], i2["n_acoes"])
        self.assertEqual(i1["n_eventos"], i2["n_eventos"])
        self.assertEqual(1, o2[cb.DESFECHO_V]["contagem"])
        rv = i2["violacoes_detalhe"]["replay_validate"]
        self.assertEqual({"eventos": 3, "imediatos": 2, "tardios": 1, "sids_tardios": 1}, rv)

    def test_teto_do_replay_tardio(self):
        ev = [("ReplayValidate", "INVALID", T_TARDIO + j * MS, T_TARDIO + j * MS + MS // 2, "h:2", "sid-%d" % j)
              for j in range(3)]
        for teto, esperado in ((3, True), (200, False)):
            d = self.d / ("t%d" % teto)
            escrever_inst(d, eventos=ev, violacoes=[], varredura=[],
                          s3_resumo={"parametros": {"sonda_replay": True, "sonda_tardia_max": teto}})
            _, info = self.desf(d)
            self.assertIs(esperado, info["violacoes_detalhe"]["sonda_tardia_no_teto"])
            self.assertEqual(teto, info["violacoes_detalhe"]["sonda_tardia_max"])
        escrever_inst(self.d / "sem-resumo", eventos=ev, violacoes=[], varredura=[])
        _, info = self.desf(self.d / "sem-resumo")
        self.assertIsNone(info["violacoes_detalhe"]["sonda_tardia_no_teto"])


# ----------------------------------------------------------------------------- varredura com erro e guarda

class VarreduraComErro(BaseI):

    def test_varredura_com_erro_deixa_v_ausente(self):
        escrever_inst(self.d, violacoes=[(INI + 5 * S, "I1", "sid-A", MSG_I1 % "LOGOUT", "", "")], varredura_erro=True)
        out, info = self.desf(self.d)
        self.assertIsNone(out[cb.DESFECHO_V]["y"])
        self.assertIn("varredura", out[cb.DESFECHO_V]["motivo"])
        self.assertIsNone(out[cb.DESFECHO_V_SEM_F2]["y"])
        self.assertEqual("erro", info["violacoes_detalhe"]["varredura"])


def gerar_celula_inst(raiz, celula, n_blocos, forma2_a, outras_a, outras_b, instrumentos_b=True):
    """Celula com forma2_a[b] pares de forma 2 e outras_a[b] de I1 outra no braco A; outras_b[b] no B."""
    linhas = []
    for b in range(1, n_blocos + 1):
        for br in "AB":
            ev, viol = [], []
            n_f2 = forma2_a[b - 1] if br == "A" else 0
            n_o = outras_a[b - 1] if br == "A" else outras_b[b - 1]
            for j in range(n_f2):
                sid = "f2-%d" % j
                ev += [("Logout", "NONE", INI + (1 + j) * S, INI + (1 + j) * S + MS, "h:2", sid),
                       ("ReplayValidate", "VALID", INI + (2 + j) * S, INI + (2 + j) * S + MS, "h:3", sid)]
                viol.append((INI + (2 + j) * S + MS // 2, "I1", sid, MSG_I1 % "LOGOUT", "", "sonda"))
            for j in range(n_o):
                sid = "o-%d" % j
                ev.append(("Logout", "NONE", INI + (1 + j) * S, INI + (1 + j) * S + MS, "h:1", sid))
                viol.append((INI + (12 + j) * S, "I1", sid, MSG_I1 % "LOGOUT", "", ""))
            inst = br == "A" or instrumentos_b
            d = Path(raiz) / celula / ("b%02d%s" % (b, br))
            escrever_inst(d, eventos=ev, violacoes=viol, varredura=[] if inst else None, sonda=inst,
                          trilha=_trilha(cb.MODO_ESPERADO[br]))
            linhas.append("%d\t%s\t%s\t%s" % (b, celula, br, Path(celula) / d.name))
    man = Path(raiz) / "manifesto.tsv"
    man.write_text("bloco\tcelula\tbraco\texecucao\n" + "\n".join(linhas) + "\n", encoding="utf-8")
    return man


class ComparacaoComSonda(BaseI):

    def test_sensibilidade_na_celula_de_teste(self):
        oa, ob = [1, 1, 2, 1, 0, 1], [0, 1, 0, 0, 1, 0]
        man = gerar_celula_inst(self.d, "F2", 6, forma2_a=[1, 2, 1, 0, 1, 2], outras_a=oa, outras_b=ob)
        r = cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"F2": "teste"},
                        n_boot=200, n_hier=200)
        c = r["celulas"]["F2"]
        sens = c["desfechos"][cb.DESFECHO_V_SEM_F2]
        self.assertEqual("sensibilidade", sens["familia"])
        self.assertEqual("diferenca", sens["escala"])
        self.assertIsNone(sens["p_holm"])
        self.assertIsNotNone(sens["p_permutacao"])
        # V (co-principal) com a forma 2; a sensibilidade sem ela
        self.assertEqual({"A": 13, "B": 2}, c["co_principal_v"]["contagem"])
        esperado = sum((b - a) * 1000.0 / 2000 for a, b in zip(oa, ob)) / 6
        self.assertAlmostEqual(esperado, sens["estimativa"], places=12)
        det = c["violacoes"]["detalhe"]
        self.assertEqual(7, det["totais"]["A"]["formas_i1"]["forma2"])
        self.assertEqual(6, det["totais"]["A"]["formas_i1"]["outra"])
        self.assertEqual(6, det["totais"]["A"]["execucoes_com_sonda"])
        self.assertEqual(2, det["totais"]["B"]["total_pares_sem_forma2"])
        self.assertEqual("1", sorted(det["por_execucao"])[0])
        secundarios = [n for n, e in c["desfechos"].items() if e["familia"] == "secundario"]
        self.assertNotIn(cb.DESFECHO_V_SEM_F2, secundarios)
        self.assertEqual(len([n for n in secundarios if c["desfechos"][n]["p_permutacao"] is not None]),
                         c["n_secundarios_holm"])

    def test_instrumentos_diferentes_entre_bracos_recusados(self):
        man = gerar_celula_inst(self.d, "F2", 2, forma2_a=[1, 1], outras_a=[1, 1], outras_b=[0, 0],
                                instrumentos_b=False)
        with self.assertRaises(cb.ErroUso):
            cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"F2": "teste"},
                        n_boot=200, n_hier=200)

    def test_markdown_traz_a_regra_e_as_formas(self):
        man = gerar_celula_inst(self.d, "F2", 2, forma2_a=[1, 1], outras_a=[1, 1], outras_b=[0, 1])
        r = cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60,
                        tipos_celula={"F2": "descritiva"}, n_boot=200, n_hier=200)
        md = cb.tabela_markdown(r)
        self.assertIn("pares distintos", md)
        self.assertIn("forma 2", md)
        self.assertIn("parametros", json.dumps(r))
        self.assertIn("pares distintos", r["parametros"]["regra_v"])


# ----------------------------------------------------------------------------- formato real (smoke do W-10)

class SmokeW10(unittest.TestCase):
    """Recorte do smoke F2 do W-10 (runs/w10-smoke/f2-sonda do worktree wt-w10, 06/11, local, sem VM): violacoes,
    varredura, marcadores, resumos e meta copiados; rep-001.csv com as linhas das sids da sonda e da varredura e as 3
    ultimas da carga; s3-acoes-rep-001.csv com as 2 primeiras e as 3 ultimas acoes (so o denominador muda);
    execucao.json sintetizado do comando.txt (--servers). Relatorio do W-10, secao 5: 6 I1 da sonda em 4 sids, 2 do
    M1 (logout no isn3) e 2 da segunda forma (logout no isn2, releitura VALID no isn3); a varredura acusa I1 REAL
    nas duas do M1."""

    def test_pares_formas_e_origens(self):
        out, info = cb.desfechos_execucao(FIXTURE_SMOKE, 1, FASES, 60 * S, w_analise_ns=5 * S)
        det = info["violacoes_detalhe"]
        self.assertEqual(4, det["total_pares"])
        self.assertEqual({"I1": 4, "I2_REAL": 0, "I4": 0}, det["pares"])
        self.assertEqual(8, det["total_linhas"])
        self.assertEqual({"online": {"linhas": 0, "pares": 0}, "sonda": {"linhas": 6, "pares": 4},
                          "varredura": {"linhas": 2, "pares": 2}}, det["por_origem"])
        self.assertEqual(2, det["pares_multiplas_origens"])
        self.assertEqual("127.0.0.1:12903", det["replica_no_falha"])
        formas = {p["chave"][:8]: p["forma"] for p in det["pares_lista"]}
        self.assertEqual({"4a17f363": "forma1", "f487fb15": "forma1", "783c69c6": "forma2", "b08692ed": "forma2"},
                         formas)
        self.assertEqual(2, det["total_pares_sem_forma2"])
        self.assertEqual({"deteccao": 4, "invalidacao": 4, "sem_invalidacao_localizada": 0}, det["atribuicao"])
        self.assertEqual(1, det["varredura_fora_de_v"])
        self.assertEqual(4, out[cb.DESFECHO_V]["contagem"])
        self.assertEqual(2, out[cb.DESFECHO_V_SEM_F2]["contagem"])


# ----------------------------------------------------------------------------- W-5d: varredura com erro, simetrica

class TotaisSemVarredura(BaseI):
    """apurar_violacoes_execucao devolve tambem os totais sem as deteccoes da varredura (V-W5c, secao 5, item 1)."""

    def test_sobreposicao_sem_varredura(self):
        ev, viol, varr = cenario_sobreposicao()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr)
        _, info = self.desf(self.d)
        det = info["violacoes_detalhe"]
        # sid-D (I2) e u-1 (I2-PERDIDO) so a varredura viu
        self.assertEqual(5, det["total_pares"])
        self.assertEqual(3, det["total_pares_sem_varredura"])
        self.assertEqual({"I1": 1, "I2_REAL": 1, "I4": 1}, det["pares_sem_varredura"])
        self.assertEqual(19 - 4, det["total_linhas_sem_varredura"])
        self.assertEqual(3, det["total_pares_sem_forma2_sem_varredura"])

    def test_forma_do_par_sem_varredura(self):
        # sid-F2c: com a varredura e "outra"; sem ela, so a leitura cruzada do isn3 (forma 2)
        ev, viol, varr = cenario_formas()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr)
        _, info = self.desf(self.d)
        det = info["violacoes_detalhe"]
        self.assertEqual(4, det["total_pares_sem_forma2"])
        self.assertEqual(6, det["total_pares_sem_varredura"])
        self.assertEqual(3, det["total_pares_sem_forma2_sem_varredura"])

    def test_formas_indeterminadas_sem_varredura(self):
        ev, viol, varr = cenario_formas()
        escrever_inst(self.d, eventos=ev, violacoes=viol, varredura=varr, servidores=None)
        _, info = self.desf(self.d)
        self.assertIsNone(info["violacoes_detalhe"]["total_pares_sem_forma2_sem_varredura"])


# por bloco: {braco: (I1 online, I1 so da varredura, pares de forma 2 da sonda, varredura com erro)}
ESPEC_VARR = {1: {"A": (2, 1, 0, False), "B": (1, 1, 0, False)},     # intacto: A 3, B 2
              2: {"A": (1, 2, 1, True), "B": (1, 1, 0, False)},      # erro so em A
              3: {"A": (2, 1, 0, False), "B": (0, 2, 0, True)},      # erro so em B
              4: {"A": (1, 1, 0, True), "B": (2, 1, 0, True)},       # erro nos dois
              5: {"A": (3, 0, 0, False), "B": (1, 2, 0, False)},     # intacto: A 3, B 3
              6: {"A": (1, 2, 0, False), "B": (0, 0, 0, False)}}     # intacto: A 3, B 0


def gerar_celula_varr(raiz, celula, espec=ESPEC_VARR):
    linhas = []
    for b in sorted(espec):
        for br in "AB":
            n_on, n_vr, n_f2, erro = espec[b][br]
            ev, viol, varr = [], [], []
            for j in range(n_on):
                sid = "on-%d" % j
                ev.append(("Logout", "NONE", INI + (1 + j) * S, INI + (1 + j) * S + MS, "h:1", sid))
                viol.append((INI + (12 + j) * S, "I1", sid, MSG_I1 % "LOGOUT", "", ""))
            for j in range(n_vr):
                sid = "vr-%d" % j
                ev.append(("Logout", "NONE", INI + (1 + j) * S, INI + (1 + j) * S + MS, "h:1", sid))
                varr.append(("I1", sid, "REAL", DET_I1 % "LOGOUT"))
            for j in range(n_f2):
                sid = "f2-%d" % j
                ev += [("Logout", "NONE", INI + (1 + j) * S, INI + (1 + j) * S + MS, "h:2", sid),
                       ("ReplayValidate", "VALID", INI + (2 + j) * S, INI + (2 + j) * S + MS, "h:3", sid)]
                viol.append((INI + (2 + j) * S + MS // 2, "I1", sid, MSG_I1 % "LOGOUT", "", "sonda"))
            d = Path(raiz) / celula / ("b%02d%s" % (b, br))
            escrever_inst(d, eventos=ev, violacoes=viol, varredura=None if erro else varr, varredura_erro=erro,
                          trilha=_trilha(cb.MODO_ESPERADO[br]))
            linhas.append("%d\t%s\t%s\t%s" % (b, celula, br, Path(celula) / d.name))
    man = Path(raiz) / ("manifesto-%s.tsv" % celula)
    man.write_text("bloco\tcelula\tbraco\texecucao\n" + "\n".join(linhas) + "\n", encoding="utf-8")
    return man


class VarreduraSimetrica(BaseI):
    """V-W5c, secao 5 (W-5d): bloco com varredura com erro em qualquer braco usa V sem a varredura nos dois bracos
    e fica no teste; o bloco sem falha fica intacto."""

    def rodar(self, tipo):
        man = gerar_celula_varr(self.d, "F2")
        return cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"F2": tipo},
                           n_boot=200, n_hier=200)["celulas"]["F2"]

    def test_d_b_por_bloco(self):
        c = self.rodar("teste")
        v = c["desfechos"][cb.DESFECHO_V]
        self.assertEqual(6, v["n_blocos"])
        self.assertEqual([], v["blocos_excluidos"])
        # x 1000 / 2000 acoes: intactos 1, 5 e 6 com a varredura; 2 (A), 3 (B) e 4 (os dois) sem ela, nos dois bracos
        esperado = {"1": (2 - 3) * 0.5, "2": (1 - 2) * 0.5, "3": (0 - 2) * 0.5, "4": (2 - 1) * 0.5, "5": 0.0,
                    "6": (0 - 3) * 0.5}
        for b, d in esperado.items():
            self.assertAlmostEqual(d, v["d_b"][b], places=12, msg="bloco %s" % b)
        self.assertIsNotNone(v["p_permutacao"])
        self.assertEqual({"A": 14, "B": 8}, c["co_principal_v"]["contagem"])
        s = c["desfechos"][cb.DESFECHO_V_SEM_F2]
        self.assertAlmostEqual((1 - 1) * 0.5, s["d_b"]["2"], places=12)      # A sem varredura: 2 pares, 1 de forma 2
        self.assertAlmostEqual(v["d_b"]["3"], s["d_b"]["3"], places=12)

    def test_detalhe_lista_os_blocos(self):
        c = self.rodar("teste")
        vc = c["violacoes"]["detalhe"]["varredura_com_erro"]
        self.assertEqual({"blocos": 3, "so_A": 1, "so_B": 1, "nos_dois": 1},
                         {k: vc[k] for k in ("blocos", "so_A", "so_B", "nos_dois")})
        lista = {x["bloco"]: x for x in vc["lista"]}
        self.assertEqual(["2", "3", "4"], sorted(lista))
        self.assertEqual(["A"], lista["2"]["bracos_com_erro"])
        self.assertEqual(["B"], lista["3"]["bracos_com_erro"])
        self.assertEqual(["A", "B"], lista["4"]["bracos_com_erro"])
        self.assertEqual({"A": {"com_varredura": None, "sem_varredura": 1.0},
                          "B": {"com_varredura": 1.0, "sem_varredura": 0.5}}, lista["2"]["v"])
        self.assertEqual({"A": {"com_varredura": 1.5, "sem_varredura": 1.0},
                          "B": {"com_varredura": None, "sem_varredura": 0.0}}, lista["3"]["v"])
        self.assertEqual({"A": {"com_varredura": None, "sem_varredura": 0.5},
                          "B": {"com_varredura": None, "sem_varredura": 1.0}}, lista["4"]["v"])

    def test_sensibilidade_sem_os_blocos(self):
        c = self.rodar("teste")
        s = c["violacoes"]["detalhe"]["varredura_com_erro"]["sensibilidade_blocos_excluidos"]
        self.assertEqual(["1", "5", "6"], s["blocos"])
        self.assertEqual(["2", "3", "4"], s["blocos_excluidos"])
        self.assertAlmostEqual((-0.5 + 0.0 - 1.5) / 3, s["estimativa"], places=12)
        self.assertEqual("sensibilidade", s["familia"])
        self.assertIsNone(s["p_permutacao"])
        self.assertIsNone(s["p_holm"])
        self.assertIsNotNone(s["ic_t"])

    def test_celula_descritiva_tambem(self):
        c = self.rodar("descritiva")
        v = c["desfechos"][cb.DESFECHO_V]
        self.assertEqual(6, v["n_blocos"])
        self.assertAlmostEqual(-1.0, v["d_b"]["3"], places=12)
        self.assertEqual(3, c["violacoes"]["detalhe"]["varredura_com_erro"]["blocos"])

    def test_bloco_sem_falha_intacto(self):
        # so os blocos sem erro: o resultado e o mesmo de uma celula que nunca teve varredura com erro
        intactos = {b: ESPEC_VARR[b] for b in (1, 5, 6)}
        man = gerar_celula_varr(self.d, "OK", intactos)
        c = cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"OK": "teste"},
                        n_boot=200, n_hier=200)["celulas"]["OK"]
        self.assertEqual({"1": -0.5, "5": 0.0, "6": -1.5}, c["desfechos"][cb.DESFECHO_V]["d_b"])
        vc = c["violacoes"]["detalhe"]["varredura_com_erro"]
        self.assertEqual(0, vc["blocos"])
        self.assertIsNone(vc["sensibilidade_blocos_excluidos"])
        self.assertEqual({"A": 9, "B": 5}, c["co_principal_v"]["contagem"])

    def test_contagens_por_invariante_trocadas(self):
        c = self.rodar("teste")
        self.assertEqual({"A": 14, "B": 8}, c["violacoes"]["I1"]["contagem"])
        self.assertEqual(6, c["violacoes"]["blocos_com_arquivo"])

    def test_markdown_conta_os_blocos(self):
        man = gerar_celula_varr(self.d, "F2")
        r = cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"F2": "teste"},
                        n_boot=200, n_hier=200)
        md = cb.tabela_markdown(r)
        self.assertIn("varredura com erro: 3 bloco(s)", md)
        self.assertIn("so A: 1, so B: 1, nos dois: 1", md)
        self.assertIn("varredura com erro", r["parametros"]["varredura_com_erro"])

    def test_guarda_de_instrumentos_trata_erro_como_varredura_pedida(self):
        # erro num braco e varredura no outro: mesmos instrumentos (nao recusa); sem varredura no outro: recusa
        espec = {1: {"A": (1, 0, 0, True), "B": (1, 1, 0, False)}}
        man = gerar_celula_varr(self.d, "G", espec)
        cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"G": "teste"},
                    n_boot=200, n_hier=200)
        (self.d / "G" / "b01B" / "varredura-rep-001.csv").unlink()
        with self.assertRaises(cb.ErroUso):
            cb.comparar(man, 5, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"G": "teste"},
                        n_boot=200, n_hier=200)


if __name__ == "__main__":
    if "--gerar-golden" in sys.argv:
        if GOLDEN.exists():
            sys.exit("golden ja existe (nunca sobrescreve): %s" % GOLDEN)
        GOLDEN.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            res = rodar_golden(tmp)
        with open(GOLDEN, "x", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(res, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n")
        print("golden gravado em %s" % GOLDEN)
    else:
        unittest.main()
