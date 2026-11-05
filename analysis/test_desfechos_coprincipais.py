"""Testes do B-39.4 (W-5): comparar-bracos/2.

Especificacao: docs/plano-alinhamento-objetivos-2026-11-04.md, secoes 3.1 a 3.5 e Prompt 2 (testes
pedidos: Delta_rec com ultimo erro conhecido, sem erro e censurado; Delta_conv com e sem t_fim_carga_ns;
taxa com zeros nos dois bracos; p minimo 2/2^k por enumeracao para k = 5..8; regra IU nos quatro casos;
Holm entre celulas; nao avaliavel; mutantes: diferenca trocada por razao, censura esquecida, t_fim_ns
no Delta_conv) e docs/plano-execucao-2026-11-04.md, secao 9, W-5 (descritivos do controlador, tipos de
celula, k e p minimo, Holm entre celulas). Os testes de poder e da referencia de erro estao em
test_poder.py e test_referencia_erro.py.

Dados sinteticos com efeito conhecido, gerados em diretorios temporarios. Este arquivo nao edita nem
depende de test_comparar_bracos.py (os testes do esquema /1 que precisam mudar foram listados para
autorizacao do coordenador, como manda o Prompt 2, item 6). A CLI roda com python -E -P (G2 de 28/10).

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import itertools
import json
import math
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

import numpy as np
from scipy import stats

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
import energia_proxy as ep  # noqa: E402
import referencia_erro as rerr  # noqa: E402

SCRIPT = AQUI / "comparar_bracos.py"
S = 1_000_000_000
MS = 1_000_000
T0 = 1_761_566_400 * S          # 2025-10-27T12:00:00Z
INI, FIM = T0 + 10 * S, T0 + 30 * S
CAB_S3 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns"
CAB_EV = "op_id,operation,start_ns,end_ns,replica,return_code,key"
CAB_REC = "t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado"
FASES = ["durante", "recuperacao"]
W5 = 5 * S
RESUMO = {"t_fim_ns": FIM, "convergiu": True, "divergencia_persistente": False,
          "t_convergencia_abs_ns": T0 + 112 * S, "t_fim_carga_ns": T0 + 100 * S}


def iso(t_ns):
    seg, frac = divmod(t_ns, S)
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(seg)) + (".%09dZ" % frac if frac else "Z")


def escrever_exec(d, acoes=None, eventos=None, marcadores=(INI, FIM), violacoes=None, resumo=None, trilha=None):
    """acoes: [(t, t_fim, falha)]; eventos: [(op, rc, ini, fim)]; marcadores: (start, end), (start, None) ou
    None; violacoes: [(t, invariante, classificacao)]; resumo: dict; trilha: lista de linhas ou texto."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    if acoes is not None:
        linhas = [CAB_S3] + ["%d,u%d,Validate,s,%s,1,%s,,%d,0" % (
            t, i, "ERROR_TRANSPORT" if f else "OK_VALIDATE:VALID", "true" if f else "false", tf)
            for i, (t, tf, f) in enumerate(acoes)]
        (d / "s3-acoes-rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    if eventos is not None:
        linhas = [CAB_EV] + ["%d,%s,%d,%d,isn1,%s,k%d" % (i, op, a, b, rc, i) for i, (op, rc, a, b) in enumerate(eventos)]
        (d / "rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
        (d / "meta-csv-rep-001.json").write_text('{"contrato_csv": 2}', encoding="utf-8")
    if marcadores is not None:
        txt = "fault=F2 node=isn3 event=start t_epoch_ns=%d\n" % marcadores[0]
        if marcadores[1] is not None:
            txt += "fault=F2 node=isn3 event=end t_epoch_ns=%d\n" % marcadores[1]
        (d / "marcadores.txt").write_text(txt, encoding="utf-8")
    if violacoes is not None:
        linhas = ["instante,invariante,chave,mensagem,fase,classificacao"] + [
            "%s,%s,sid-%d,msg,durante,%s" % (iso(t), inv, i, cl) for i, (t, inv, cl) in enumerate(violacoes)]
        (d / "violacoes-rep-001.csv").write_text("\n".join(linhas) + "\n", encoding="utf-8")
    if resumo is not None:
        (d / "convergencia-resumo-rep-001.json").write_text(json.dumps(resumo), encoding="utf-8")
    if trilha is not None:
        texto = trilha if isinstance(trilha, str) else "".join(json.dumps(o) + "\n" for o in trilha)
        (d / "decisoes-rep-001.jsonl").write_bytes(texto.encode("utf-8"))


def acoes_regulares(n=1100, ini=INI, passo=20 * MS, fator=1.0, falhas=()):
    """n acoes a cada passo desde ini; latencia (2,00 a 2,99 ms) x fator."""
    out = []
    for i in range(n):
        t = ini + i * passo
        out.append((t, t + int((2 * MS + (i % 100) * 10_000) * fator), i in falhas))
    return out


def eventos_ok(n=100):
    return [("Login", "OK", T0 + i * S, T0 + i * S + MS) for i in range(n)]


def dec(t, perfil="NORMAL", trans=False, regra="NENHUMA", modo="active", sinais=True):
    return {"tipo": "decisao", "t_fim_ns": t, "t_decisao_ns": t + S // 10, "modo": modo, "perfil": perfil,
            "transicao": trans, "regra": regra, "motivos": ["ESTAVEL"], "severidade": "NENHUMA",
            "sinais": {"taxa_erro": 0.0} if sinais else {}, "param_sha256": "0" * 64}


def acao(op, tipo, res, t_pedido, origem="POLITICA", regra="R2", t_fim_janela=None, t_inicio=None, duracao=None,
         modo="active", caches=("sessions",), alvo=None):
    return {"tipo": "acao", "operacao": op, "executada": res in ("APLICADA", "FALHOU"), "modo": modo,
            "atuador": "teste", "acao": {"tipo": tipo, "caches": list(caches), "alvo": alvo, "valor": None},
            "origem": origem, "regra": regra, "t_fim_janela_ns": t_fim_janela, "t_pedido_ns": t_pedido,
            "t_inicio_ns": t_inicio, "duracao_ns": duracao, "resultado": res, "codigo_http": None, "lido": None,
            "erro": None, "reversao": None}


ATUADOR = "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)"
SHA_PARAM = "a" * 64


def trilha(modo, meio, w_ns=5 * S, fim=True, linhas_perdidas=0, acoes_pedidas=None, atuador=ATUADOR,
           politica="por-assinatura", param_sha256=SHA_PARAM):
    meta = {"tipo": "meta", "esquema": "decisoes-controlador/1.2", "versao_programa": "teste", "rep": 1,
            "modo": modo, "cenario": "S3", "replicacao": "async", "nos_esperados": 3, "parametros": {"w_ns": w_ns},
            "param_sha256": param_sha256, "atuador": atuador, "politica": politica}
    out = [meta] + list(meio)
    if fim:
        ped = (sum(1 for o in meio if o.get("tipo") == "acao" and o.get("operacao") == "APLICAR")
               if acoes_pedidas is None else acoes_pedidas)
        out.append({"tipo": "fim", "decisoes": sum(1 for o in meio if o.get("tipo") == "decisao"),
                    "acoes_pedidas": ped, "linhas_perdidas": linhas_perdidas})
    return out


def trilha_minima(modo):
    return trilha(modo, [dec(T0 + 15 * S, modo=modo)])


def escrever_manifesto(raiz, linhas, nome="manifesto.tsv"):
    p = Path(raiz) / nome
    p.write_text("bloco\tcelula\tbraco\texecucao\n" + "\n".join(linhas) + "\n", encoding="utf-8")
    return p


def gerar_celula(raiz, celula, viol_a, viol_b, fator_l=0.95, ruido=0.004, n_acoes=1100, trilha_b=None,
                 trilha_a=None):
    """Um par A/B por bloco; viol_a/viol_b: contagens de I1 por bloco (instantes durante a falha)."""
    linhas = []
    for b in range(1, len(viol_a) + 1):
        base = 1.0 + 0.01 * b
        for br in "AB":
            f = base if br == "A" else base * fator_l * (1 + (ruido if b % 2 else -ruido))
            cont = viol_a[b - 1] if br == "A" else viol_b[b - 1]
            viol = [(INI + 5 * S + j, "I1", "") for j in range(cont)]
            res = {"t_fim_ns": FIM, "t_fim_carga_ns": T0 + 40 * S, "convergiu": True,
                   "divergencia_persistente": False, "t_convergencia_abs_ns": T0 + 45 * S}
            tr = trilha_minima(cb.MODO_ESPERADO[br])
            if br == "B" and trilha_b is not None:
                tr = trilha_b
            if br == "A" and trilha_a is not None:
                tr = trilha_a
            d = Path(raiz) / celula / ("b%02d%s" % (b, br))
            escrever_exec(d, acoes=acoes_regulares(n_acoes, fator=f), violacoes=viol, resumo=res, trilha=tr)
            linhas.append("%d\t%s\t%s\t%s" % (b, celula, br, Path(celula) / d.name))
    return linhas


def rodar_cli(*args, cwd=None):
    r = subprocess.run([sys.executable, "-E", "-P", str(SCRIPT)] + [str(a) for a in args], capture_output=True,
                       text=True, cwd=cwd)
    return r.returncode, r.stdout, r.stderr


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def desf(self, d, **kw):
        kw.setdefault("w_analise_ns", W5)
        return cb.desfechos_execucao(d, 1, FASES, 60 * S, **kw)


# ============================================================================= taxa de violacoes

class TaxaViolacoes(Base):

    def test_regra_de_contagem_e_denominador(self):
        # I1 x 3 e I2 REAL dentro das fases; I2 esperada e I4 antes do start ficam fora
        viol = [(INI + 5 * S, "I1", ""), (INI + 6 * S, "I1", ""), (INI + 7 * S, "I1", ""),
                (INI + 8 * S, "I2", "REAL"), (INI + 9 * S, "I2", "ESPERADA-POR-DESENHO"), (INI - 5 * S, "I4", "")]
        escrever_exec(self.d, acoes=acoes_regulares(), violacoes=viol)
        out, info = self.desf(self.d)
        v = out[cb.DESFECHO_V]
        self.assertEqual(4, v["contagem"])
        self.assertEqual(1100, v["n"])
        self.assertAlmostEqual(4000.0 / 1100, v["y"], places=12)

    def test_sem_arquivo_de_violacoes(self):
        escrever_exec(self.d, acoes=acoes_regulares(50))
        out, _ = self.desf(self.d)
        self.assertIsNone(out[cb.DESFECHO_V]["y"])
        self.assertIn("violacoes", out[cb.DESFECHO_V]["motivo"])

    def test_diferenca_e_nao_razao(self):
        # mutante "diferenca trocada por razao": com razao os dois blocos sairiam (zero num braco)
        linhas = gerar_celula(self.d, "C", viol_a=[0, 2], viol_b=[1, 0], n_acoes=100)
        man = escrever_manifesto(self.d, linhas)
        r = cb.comparar(man, 3, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C": "teste"},
                        n_boot=200, n_hier=200)
        e = r["celulas"]["C"]["desfechos"][cb.DESFECHO_V]
        self.assertEqual("diferenca", e["escala"])
        self.assertEqual(2, e["n_blocos"])
        self.assertEqual([], e["blocos_excluidos"])
        self.assertAlmostEqual(1000.0 / 100, e["d_b"]["1"], places=9)
        self.assertAlmostEqual(-2000.0 / 100, e["d_b"]["2"], places=9)
        self.assertAlmostEqual((10.0 - 20.0) / 2, e["estimativa"], places=9)
        self.assertEqual(2, e["k_nao_nulos"])


# ============================================================================= Delta_rec

class DeltaRec(Base):
    """Instante do evento anomalo = inicio da operacao (P-2 da V-W5: regra de fase do projeto,
    EventCsvWriter.java:157; o fim incluiria a duracao, que o braco B altera)."""

    def rec(self, erros=(), acoes=None, marcadores=(INI, FIM), resumo=RESUMO, **kw):
        ev = eventos_ok() + [("Login", rc, a, b) for rc, a, b in erros]
        escrever_exec(self.d, eventos=ev, acoes=acoes, marcadores=marcadores, resumo=resumo)
        out, info = self.desf(self.d, **kw)
        return out[cb.DESFECHO_DREC], info

    def test_ultimo_erro_conhecido(self):
        r, info = self.rec([("ERROR_TRANSPORT", T0 + 20 * S, T0 + 20 * S + MS),
                            ("ERROR_TRANSPORT", T0 + 40 * S, T0 + 41 * S)])
        self.assertAlmostEqual(10.0, r["y"], places=9)
        self.assertFalse(r["censurada"])
        self.assertEqual("t_fim_carga_ns", info["fim_medicao_origem"])

    def test_sem_erro_depois_do_fim_e_zero(self):
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 20 * S, T0 + 20 * S + MS)])
        self.assertEqual(0.0, r["y"])
        self.assertFalse(r["censurada"])

    def test_censurado(self):
        # mutante "censura esquecida": daria 66 s e censurada = False
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 96 * S, T0 + 97 * S)])
        self.assertTrue(r["censurada"])
        self.assertAlmostEqual(70.0, r["y"], places=9)

    def test_fronteira_da_censura(self):
        # inicio exatamente em fim - W_c nao e censurado (t > fim - W_c); 1 ns depois e
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 95 * S, T0 + 96 * S)])
        self.assertFalse(r["censurada"])
        self.assertAlmostEqual(65.0, r["y"], places=9)
        self.tearDown()
        self.setUp()
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 95 * S + 1, T0 + 96 * S)])
        self.assertTrue(r["censurada"])

    def test_falha_percebida_s3_e_unavailable(self):
        r, _ = self.rec(acoes=[(T0 + 45 * S, T0 + 46 * S, True), (T0 + 47 * S, T0 + 47 * S + MS, False)])
        self.assertAlmostEqual(15.0, r["y"], places=9)
        self.tearDown()
        self.setUp()
        r, _ = self.rec([("ERROR_UNAVAILABLE", T0 + 42 * S, T0 + 43 * S)])
        self.assertAlmostEqual(12.0, r["y"], places=9)

    def test_instante_e_o_inicio_da_operacao(self):
        # mutante "fim da operacao": comecou antes do fim da falha e voltou com erro depois; daria 1 s
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 29 * S, T0 + 31 * S)])
        self.assertEqual(0.0, r["y"])
        self.tearDown()
        self.setUp()
        r, _ = self.rec(acoes=[(T0 + 29 * S, T0 + 31 * S, True)])
        self.assertEqual(0.0, r["y"])

    def test_sem_marcador_end(self):
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 40 * S, T0 + 41 * S)], marcadores=(INI, None))
        self.assertIsNone(r["y"])
        self.assertIn("end", r["motivo"])

    def test_sem_w_analise(self):
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 40 * S, T0 + 41 * S)], w_analise_ns=None)
        self.assertIsNone(r["y"])
        self.assertIn("w_analise", r["motivo"])

    def test_sem_t_fim_carga_fica_ausente(self):
        # P-3 da V-W5: sem fim de medicao de reserva; o fim e o do protocolo (t_fim_carga_ns)
        r, info = self.rec([("ERROR_TRANSPORT", T0 + 40 * S, T0 + 41 * S)], resumo=None)
        self.assertIsNone(r["y"])
        self.assertIsNone(r["censurada"])
        self.assertIn("t_fim_carga_ns", r["motivo"])
        self.assertIsNone(info["fim_medicao_ns"])
        self.assertIsNone(info["fim_medicao_origem"])
        self.tearDown()
        self.setUp()
        sem = dict(RESUMO)
        del sem["t_fim_carga_ns"]
        r, _ = self.rec([("ERROR_TRANSPORT", T0 + 40 * S, T0 + 41 * S)], resumo=sem)
        self.assertIsNone(r["y"])

    def test_sensibilidade_tambem_ausente_sem_t_fim_carga(self):
        escrever_exec(self.d, eventos=eventos_ok())
        out, _ = self.desf(self.d, referencia_erro={"p_ref_sup": 0.01})
        self.assertIsNone(out[cb.DESFECHO_DREC_MET]["y"])
        self.assertIn("t_fim_carga_ns", out[cb.DESFECHO_DREC_MET]["motivo"])

    def test_funcao_pura(self):
        self.assertEqual((0.0, False, None), cb.delta_rec([], FIM, FIM + 10 * S, W5))
        self.assertEqual((None, None, "fim da medicao nao passa do fim da falha"),
                         cb.delta_rec([FIM + S], FIM, FIM, W5))
        y, cens, motivo = cb.delta_rec([FIM + S], FIM, None, W5)
        self.assertEqual((None, None), (y, cens))
        self.assertIn("t_fim_carga_ns", motivo)


class DeltaRecMetodologia(unittest.TestCase):
    """Binomial(100; 0,01): P(X <= 2) = 0,9206 e P(X <= 3) = 0,9816, entao o quantil de 95 % e 3."""

    def eventos(self, erros_por_janela, n=100, vazias=()):
        out = []
        for j, e in enumerate(erros_por_janela):
            if j in vazias:
                continue
            for k in range(n):
                out.append((FIM + j * W5 + k * (W5 // n), k < e))
        return out

    def test_primeira_janela_na_linha_de_base(self):
        ev = self.eventos([50, 30, 1, 0, 0])
        self.assertEqual((10.0, False, None), cb.delta_rec_metodologia(ev, FIM, FIM + 25 * S, W5, 0.01))

    def test_quantil_95_exato(self):
        self.assertEqual(3, int(stats.binom.ppf(0.95, 100, 0.01)))
        ev = self.eventos([4, 3, 0])
        self.assertEqual((5.0, False, None), cb.delta_rec_metodologia(ev, FIM, FIM + 15 * S, W5, 0.01))

    def test_nunca_volta_e_censurado(self):
        ev = self.eventos([50] * 5)
        self.assertEqual((25.0, True, None), cb.delta_rec_metodologia(ev, FIM, FIM + 25 * S, W5, 0.01))

    def test_janela_vazia_nao_e_linha_de_base(self):
        ev = self.eventos([0, 0, 0], vazias=(0,))
        self.assertEqual((5.0, False, None), cb.delta_rec_metodologia(ev, FIM, FIM + 15 * S, W5, 0.01))

    def test_janela_incompleta_no_fim_nao_entra(self):
        ev = self.eventos([50, 0])
        # so 1 janela inteira em 9 s: a segunda (que estaria na linha de base) fica de fora
        self.assertEqual((9.0, True, None), cb.delta_rec_metodologia(ev, FIM, FIM + 9 * S, W5, 0.01))

    def test_sem_referencia(self):
        self.assertEqual((None, None, "sem referencia de erro"),
                         cb.delta_rec_metodologia([], FIM, FIM + 25 * S, W5, None))

    def test_ligacao_em_desfechos_execucao(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            # instante = inicio da operacao (P-2): cada evento comeca em t e termina 1 ms depois
            ev = [("Login", "ERROR_TRANSPORT" if erro else "OK", t, t + MS) for t, erro in self.eventos([50, 0, 0])]
            escrever_exec(d, eventos=eventos_ok(30) + ev,
                          resumo=dict(RESUMO, t_fim_carga_ns=FIM + 15 * S))
            out, _ = cb.desfechos_execucao(d, 1, FASES, 60 * S, w_analise_ns=W5, referencia_erro={"p_ref_sup": 0.01})
            self.assertEqual(5.0, out[cb.DESFECHO_DREC_MET]["y"])
            self.assertFalse(out[cb.DESFECHO_DREC_MET]["censurada"])
            out2, _ = cb.desfechos_execucao(d, 1, FASES, 60 * S, w_analise_ns=W5)
            self.assertNotIn(cb.DESFECHO_DREC_MET, out2)


# ============================================================================= Delta_conv e divergencia

class DeltaConv(Base):

    def conv(self, resumo):
        escrever_exec(self.d, eventos=eventos_ok(), resumo=resumo)
        out, _ = self.desf(self.d)
        return out[cb.DESFECHO_DCONV], out[cb.DESFECHO_DIV]

    def test_a_partir_do_fim_da_carga(self):
        # mutante "t_fim_ns no Delta_conv": daria 82 s
        dc, dv = self.conv(RESUMO)
        self.assertAlmostEqual(12.0, dc["y"], places=9)
        self.assertEqual(0.0, dv["y"])

    def test_sem_t_fim_carga_ns_fica_ausente(self):
        r = dict(RESUMO)
        del r["t_fim_carga_ns"]
        dc, dv = self.conv(r)
        self.assertIsNone(dc["y"])
        self.assertIn("t_fim_carga_ns", dc["motivo"])
        self.assertEqual(0.0, dv["y"])

    def test_divergencia_persistente(self):
        dc, dv = self.conv(dict(RESUMO, divergencia_persistente=True, convergiu=False, t_convergencia_abs_ns=None))
        self.assertEqual(1.0, dv["y"])
        self.assertIsNone(dc["y"])
        self.assertIn("divergencia", dc["motivo"])

    def test_sem_resumo(self):
        escrever_exec(self.d, eventos=eventos_ok())
        out, _ = self.desf(self.d)
        self.assertIsNone(out[cb.DESFECHO_DCONV]["y"])
        self.assertIsNone(out[cb.DESFECHO_DIV]["y"])


# ============================================================================= estatistica

class PMinimoAtingivel(unittest.TestCase):

    @staticmethod
    def enumerar(d):
        n = len(d)
        obs = abs(sum(d) / n)
        conta = sum(1 for s in itertools.product((-1, 1), repeat=n)
                    if abs(sum(si * di for si, di in zip(s, d)) / n) >= obs - 1e-12)
        return conta / 2 ** n

    def test_k_5_a_8_por_enumeracao(self):
        for k in range(5, 9):
            with self.subTest(k=k):
                for d in ([0.3] * k + [0.0] * (8 - k), [0.1 * (i + 1) for i in range(k)] + [0.0] * (8 - k)):
                    self.assertEqual(2 / 2 ** k, self.enumerar(d))
                    self.assertEqual(2 / 2 ** k, cb.permutacao_exata(d))
                self.assertEqual(2 / 2 ** k, cb.p_minimo_atingivel(k))

    def test_k_pequeno(self):
        self.assertEqual(1.0, cb.p_minimo_atingivel(0))
        self.assertEqual(1.0, cb.p_minimo_atingivel(1))
        self.assertEqual(0.5, cb.p_minimo_atingivel(2))
        self.assertEqual(3, cb.k_nao_nulos([0.0, -1.0, 2.0, 0.0, 0.5]))

    def test_zeros_nos_dois_bracos(self):
        an = cb.analisar_desfecho([0.0, 0.0, 0.0], [0.0, 0.0, 0.0], 1, 0, 200, escala=cb.ESCALA_DIF)
        self.assertEqual([0.0, 0.0, 0.0], an["d_b"])
        self.assertEqual(0.0, an["estimativa"])
        self.assertEqual(1.0, an["p_permutacao"])
        self.assertEqual([0.0, 0.0], an["ic_t"])


class NaoInferioridadeLatencia(unittest.TestCase):

    def test_p_l_equivale_ao_limite_do_ic(self):
        rng = np.random.default_rng(20261105)
        lim = math.log(1.10)
        conferidos = 0
        for _ in range(400):
            d = rng.normal(math.log(1.10) + rng.normal(0, 0.05), 0.04, 8).tolist()
            ic = cb.ic_t(d)
            if abs(ic[1] - 1.10) < 1e-9:
                continue
            self.assertEqual(ic[1] <= 1.10, cb.p_nao_inferioridade(d, lim) <= 0.05)
            conferidos += 1
        self.assertGreater(conferidos, 390)

    def test_variancia_zero_e_n_pequeno(self):
        self.assertEqual(0.0, cb.p_nao_inferioridade([0.01] * 4, math.log(1.1)))
        self.assertEqual(1.0, cb.p_nao_inferioridade([0.2] * 4, math.log(1.1)))
        self.assertIsNone(cb.p_nao_inferioridade([0.01], math.log(1.1)))

    def test_ic_na_escala_da_diferenca(self):
        d = [1.0, 2.0, 3.0, 4.0]
        lo, hi = cb.ic_t(d, exp=False)
        meia = stats.t.ppf(0.975, 3) * np.std(d, ddof=1) / 2
        self.assertAlmostEqual(2.5 - meia, lo, places=12)
        self.assertAlmostEqual(2.5 + meia, hi, places=12)


class RegraIU(unittest.TestCase):

    def iu(self, p_v=2 / 256, media_v=-1.5, aval=True, ic_l=(0.93, 0.97), p_l=0.001, vazao="nao-inferior",
           rast=True):
        return cb.regra_iu(p_v, media_v, aval, list(ic_l) if ic_l else None, p_l, 0.10, vazao, rast)

    def test_hv_e_hl_rejeitadas(self):
        r = self.iu()
        self.assertTrue(r["h_v"]["rejeitada"])
        self.assertTrue(r["h_l"]["rejeitada"])
        self.assertEqual("melhoria", r["resultado"])
        self.assertEqual(2 / 256, r["p_iu"])
        self.assertEqual([], r["motivos"])

    def test_so_hv_rejeitada(self):
        r = self.iu(ic_l=(0.95, 1.15), p_l=0.2)
        self.assertTrue(r["h_v"]["rejeitada"])
        self.assertFalse(r["h_l"]["rejeitada"])
        self.assertEqual("sem-melhoria", r["resultado"])
        self.assertEqual(0.2, r["p_iu"])
        self.assertIn("H_L nao rejeitada", r["motivos"])

    def test_so_hl_rejeitada(self):
        r = self.iu(p_v=0.3)
        self.assertFalse(r["h_v"]["rejeitada"])
        self.assertTrue(r["h_l"]["rejeitada"])
        self.assertEqual("sem-melhoria", r["resultado"])
        self.assertEqual(0.3, r["p_iu"])

    def test_nenhuma_rejeitada(self):
        r = self.iu(p_v=0.3, ic_l=(0.95, 1.15), p_l=0.2)
        self.assertFalse(r["h_v"]["rejeitada"])
        self.assertFalse(r["h_l"]["rejeitada"])
        self.assertEqual("sem-melhoria", r["resultado"])
        self.assertEqual(0.3, r["p_iu"])

    def test_aumento_de_violacoes_nao_rejeita(self):
        r = self.iu(media_v=+1.5)
        self.assertFalse(r["h_v"]["rejeitada"])
        self.assertEqual(1.0, r["p_iu"])

    def test_nao_avaliavel(self):
        r = self.iu(aval=False)
        self.assertIsNone(r["h_v"]["rejeitada"])
        self.assertIsNone(r["p_iu"])
        self.assertEqual("nao-avaliavel", r["resultado"])

    def test_vazao_e_rastreabilidade(self):
        self.assertEqual("sem-melhoria", self.iu(vazao="inconclusivo")["resultado"])
        r = self.iu(rast=False)
        self.assertEqual("sem-melhoria", r["resultado"])
        self.assertIn("rastreabilidade do braco B incompleta", r["motivos"])


class HolmEntreCelulas(unittest.TestCase):

    def test_tres_celulas_com_uma_nao_avaliavel(self):
        regras = {"C1": {"p_iu": 2 / 256, "resultado": "melhoria"},
                  "C2": {"p_iu": 2 / 128, "resultado": "melhoria"},
                  "C3": {"p_iu": None, "resultado": "nao-avaliavel"}}
        g = cb.conclusao_global(regras)
        self.assertEqual(3, g["m"])
        self.assertAlmostEqual(3 * 2 / 256, g["celulas"]["C1"]["p_iu_holm"], places=12)
        self.assertAlmostEqual(2 * 2 / 128, g["celulas"]["C2"]["p_iu_holm"], places=12)
        self.assertEqual(1.0, g["celulas"]["C3"]["p_iu_holm"])
        self.assertEqual(1.0, g["celulas"]["C3"]["p_iu_usado"])
        self.assertTrue(g["celulas"]["C1"]["melhoria"])
        self.assertTrue(g["celulas"]["C2"]["melhoria"])
        self.assertFalse(g["celulas"]["C3"]["melhoria"])

    def test_holm_corta(self):
        regras = {"C1": {"p_iu": 0.03, "resultado": "melhoria"}, "C2": {"p_iu": 0.04, "resultado": "melhoria"}}
        g = cb.conclusao_global(regras)
        self.assertFalse(g["celulas"]["C1"]["melhoria"])   # 2 x 0,03 = 0,06 > 0,05
        self.assertFalse(g["celulas"]["C2"]["melhoria"])

    def test_sem_celulas_de_teste(self):
        self.assertEqual(0, cb.conclusao_global({})["m"])


class Familias(unittest.TestCase):

    def test_familia_de(self):
        self.assertEqual("co-principal-V", cb.familia_de("taxa_violacoes_por_1000"))
        self.assertEqual("co-principal-L", cb.familia_de("acao_s3_p99_com_espera"))
        self.assertEqual("nao-inferioridade", cb.familia_de("vazao_util"))
        self.assertEqual("sensibilidade", cb.familia_de("delta_rec_metodologia_s"))
        for n in ("delta_rec_s", "delta_conv_s", "divergencia_persistente", "taxa_erro", "evento_p99:Login",
                  "cpu_por_1000_uteis", "acao_s3_p99_sem_espera"):
            self.assertEqual("secundario", cb.familia_de(n), n)
        self.assertEqual("secundario", cb.familia_de("energia_total_j_por_1000_uteis:0.3", 0.3))
        self.assertEqual("sensibilidade", cb.familia_de("energia_total_j_por_1000_uteis:0.5", 0.3))
        self.assertEqual("sensibilidade", cb.familia_de("energia_total_j_por_1000_uteis:0.3"))


# ============================================================================= comparacao completa

class ComparacaoCompleta(unittest.TestCase):
    """C1 teste (8 blocos: B sem violacao, p99 B/A ~ 0,95), C2 teste nao avaliavel, C3 descritiva,
    C4 teste com sensibilidades conhecidas."""

    VA4, VB4 = [3, 2, 0, 4], [1, 0, 0, 1]

    @classmethod
    def setUpClass(cls):
        cls.tmpc = tempfile.TemporaryDirectory()
        r = Path(cls.tmpc.name)
        cls.raiz = r
        linhas = gerar_celula(r, "C1", [2, 3, 4, 2, 3, 4, 2, 3], [0] * 8)
        linhas += gerar_celula(r, "C2", [0, 0], [0, 0], n_acoes=200)
        linhas += gerar_celula(r, "C3", [1, 0], [0, 0], n_acoes=200)
        linhas += gerar_celula(r, "C4", cls.VA4, cls.VB4)
        cls.man = escrever_manifesto(r, linhas)
        cls.tipos = {"C1": "teste", "C2": "teste", "C3": "descritiva", "C4": "teste"}
        cls.j = cb.comparar(cls.man, 7, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula=cls.tipos,
                            n_boot=500, n_hier=200)

    @classmethod
    def tearDownClass(cls):
        cls.tmpc.cleanup()

    def test_esquema(self):
        self.assertEqual("comparar-bracos/2", self.j["esquema"])
        self.assertEqual(0.10, self.j["parametros"]["delta_latencia"])
        self.assertEqual(5.0, self.j["parametros"]["w_analise_s"])
        self.assertEqual(self.tipos, self.j["parametros"]["tipos_celula"])

    def test_melhoria_na_celula_1(self):
        c = self.j["celulas"]["C1"]
        r = c["regra_iu"]
        self.assertEqual(2 / 256, r["h_v"]["p"])
        self.assertTrue(r["h_v"]["rejeitada"])
        self.assertTrue(r["h_l"]["rejeitada"])
        self.assertLess(r["h_l"]["limite_superior_ic_t"], 1.10)
        self.assertEqual("nao-inferior", r["vazao"])
        self.assertTrue(r["rastreabilidade_integral"])
        self.assertEqual("melhoria", r["resultado"])
        self.assertEqual(8, c["co_principal_v"]["k_nao_nulos"])
        self.assertEqual(2 / 256, c["co_principal_v"]["p_minimo_atingivel"])

    def test_familias_e_holm_dentro_da_celula(self):
        des = self.j["celulas"]["C1"]["desfechos"]
        self.assertEqual("co-principal-V", des[cb.DESFECHO_V]["familia"])
        self.assertEqual("co-principal-L", des[cb.DESFECHO_L]["familia"])
        self.assertEqual("log-razao", des[cb.DESFECHO_L]["escala"])
        self.assertEqual("nao-inferioridade", des[cb.DESFECHO_NI]["familia"])
        for n in (cb.DESFECHO_V, cb.DESFECHO_L, cb.DESFECHO_NI):
            self.assertIsNone(des[n]["p_holm"], n)
        for n in (cb.DESFECHO_DREC, cb.DESFECHO_DCONV, cb.DESFECHO_DIV, "falhas_percebidas_por_1000"):
            self.assertEqual("secundario", des[n]["familia"], n)
            self.assertEqual("diferenca", des[n]["escala"], n)
        self.assertEqual("sensibilidade", des["falhas_percebidas_por_1000" + cb.SUFIXO_RAZAO]["familia"])
        self.assertIsNone(des["falhas_percebidas_por_1000" + cb.SUFIXO_RAZAO]["p_holm"])
        sec = sorted(n for n, e in des.items() if e["familia"] == "secundario" and e["p_permutacao"] is not None)
        for n, pa in zip(sec, cb.holm([des[n]["p_permutacao"] for n in sec])):
            self.assertAlmostEqual(pa, des[n]["p_holm"], places=12)
        self.assertEqual(len(sec), self.j["celulas"]["C1"]["n_secundarios_holm"])
        self.assertEqual(0.0, des[cb.DESFECHO_DCONV]["d_b"]["1"])     # 5 s nos dois bracos
        self.assertEqual(8, des[cb.DESFECHO_DCONV]["n_blocos"])
        self.assertEqual({"A": 0, "B": 0}, des[cb.DESFECHO_DREC]["censuradas"])

    def test_nao_avaliavel_e_zeros_nos_dois_bracos(self):
        c = self.j["celulas"]["C2"]
        e = c["desfechos"][cb.DESFECHO_V]
        self.assertEqual({"1": 0.0, "2": 0.0}, e["d_b"])
        self.assertEqual(0.0, e["estimativa"])
        self.assertEqual(0, e["k_nao_nulos"])
        self.assertEqual(1.0, e["p_minimo_atingivel"])
        v = c["co_principal_v"]
        self.assertFalse(v["avaliavel"])
        self.assertEqual({"A", "B"}, set(v["regra_de_tres"]))
        self.assertAlmostEqual(1 - 0.05 ** (1 / 400), v["regra_de_tres"]["A"]["limite_superior_95"], places=15)
        self.assertEqual("nao-avaliavel", c["regra_iu"]["resultado"])
        self.assertIsNone(c["regra_iu"]["p_iu"])

    def test_celula_descritiva_sem_teste(self):
        c = self.j["celulas"]["C3"]
        self.assertEqual("descritiva", c["tipo"])
        self.assertNotIn("regra_iu", c)
        self.assertNotIn("sensibilidades", c["co_principal_v"])
        for n, e in c["desfechos"].items():
            self.assertIsNone(e["p_permutacao"], n)
            self.assertIsNone(e["p_holm"], n)
        self.assertEqual(0, c["n_secundarios_holm"])
        self.assertNotIn("C3", self.j["conclusao_global"]["celulas"])

    def test_holm_entre_celulas(self):
        g = self.j["conclusao_global"]
        self.assertEqual(3, g["m"])
        nomes = sorted(g["celulas"])
        self.assertEqual(["C1", "C2", "C4"], nomes)
        usados = [g["celulas"][c]["p_iu_usado"] for c in nomes]
        self.assertEqual(1.0, usados[1])
        for c, pa in zip(nomes, cb.holm(usados)):
            self.assertAlmostEqual(pa, g["celulas"][c]["p_iu_holm"], places=12)
        self.assertAlmostEqual(3 * 2 / 256, g["celulas"]["C1"]["p_iu_holm"], places=12)
        self.assertTrue(g["celulas"]["C1"]["melhoria"])
        self.assertFalse(g["celulas"]["C2"]["melhoria"])
        self.assertFalse(g["celulas"]["C4"]["melhoria"])

    def test_sensibilidades_da_taxa(self):
        c = self.j["celulas"]["C4"]
        v = c["co_principal_v"]
        e = c["desfechos"][cb.DESFECHO_V]
        self.assertEqual(3, v["k_nao_nulos"])
        self.assertEqual(0.25, v["p_minimo_atingivel"])
        self.assertEqual(0.25, e["p_permutacao"])
        s = v["sensibilidades"]
        self.assertEqual(3, s["a_razao_exclusao_zero_a"]["blocos"])
        self.assertAlmostEqual(2.0 / 9.0, s["a_razao_exclusao_zero_a"]["estimativa"], places=12)
        self.assertAlmostEqual(stats.binomtest(2, 11, 0.5).pvalue, s["b_poisson_condicional"]["p"], places=12)
        self.assertIn("anticonservador", s["b_poisson_condicional"]["rotulo"])
        self.assertEqual(e["ic_boot_blocos"], s["c_boot_blocos_diferenca"])
        ci = stats.binomtest(9, 4400).proportion_ci(confidence_level=0.95, method="wilson")
        self.assertAlmostEqual(1000 * ci.low, v["binomial_wilson_por_1000"]["A"][0], places=9)
        self.assertAlmostEqual(1000 * ci.high, v["binomial_wilson_por_1000"]["A"][1], places=9)
        self.assertEqual("sem-melhoria", c["regra_iu"]["resultado"])

    def test_violacoes_por_invariante_mantidas(self):
        v = self.j["celulas"]["C1"]["violacoes"]
        self.assertTrue(v["presente"])
        self.assertEqual({"A": 23, "B": 0}, v["I1"]["contagem"])

    def test_controlador_e_rastreabilidade(self):
        c = self.j["celulas"]["C1"]
        self.assertTrue(c["rastreabilidade"]["B"]["integral"])
        self.assertTrue(c["rastreabilidade"]["A"]["integral"])
        t = c["controlador"]["totais"]
        self.assertEqual(8, t["A"]["execucoes_com_trilha"])
        self.assertEqual(0, t["B"]["transicoes"])

    def test_markdown(self):
        md = cb.tabela_markdown(self.j)
        for trecho in ("comparar-bracos/2", "co-principal-V", "co-principal-L", "Intersecao-uniao",
                       "Conclusao global", "| C1 | melhoria |", "descritiva"):
            self.assertIn(trecho, md)


class RastreabilidadeQuebradaNaComparacao(Base):

    def test_acao_sem_decisao_no_braco_b_impede_melhoria(self):
        quebrada = trilha("active", [dec(T0 + 15 * S),
                                     acao("APLICAR", "AC-2", "APLICADA", T0 + 16 * S, t_fim_janela=T0 + 17 * S)])
        linhas = gerar_celula(self.d, "C", [2, 3, 4, 2, 3, 4, 2, 3], [0] * 8, trilha_b=quebrada)
        man = escrever_manifesto(self.d, linhas)
        r = cb.comparar(man, 7, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C": "teste"},
                        n_boot=200, n_hier=200)
        c = r["celulas"]["C"]
        self.assertFalse(c["rastreabilidade"]["B"]["integral"])
        self.assertEqual("sem-melhoria", c["regra_iu"]["resultado"])
        self.assertIn("rastreabilidade do braco B incompleta", c["regra_iu"]["motivos"])
        self.assertFalse(r["conclusao_global"]["celulas"]["C"]["melhoria"])

    def test_bracos_trocados_pela_trilha(self):
        linhas = gerar_celula(self.d, "C", [1], [0], n_acoes=50, trilha_a=trilha_minima("active"))
        man = escrever_manifesto(self.d, linhas)
        with self.assertRaises(cb.ErroUso):
            cb.comparar(man, 7, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C": "teste"},
                        n_boot=200, n_hier=200)


class TiposEParametrosDaComparacao(Base):

    def setUp(self):
        super().setUp()
        self.man = escrever_manifesto(self.d, gerar_celula(self.d, "C", [1], [0], n_acoes=50))

    def comparar(self, **kw):
        base = dict(delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C": "teste"}, n_boot=200,
                    n_hier=200)
        base.update(kw)
        return cb.comparar(self.man, 1, **base)

    def test_celula_sem_tipo_ou_tipo_invalido(self):
        for tipos in ({}, {"C": "teste", "X": "teste"}, {"C": "exploratoria"}):
            with self.subTest(tipos=tipos):
                with self.assertRaises(cb.ErroUso):
                    self.comparar(tipos_celula=tipos)

    def test_parametros_obrigatorios_sem_padrao(self):
        # cada chamada omite um so dos tres (P-5 da V-W5: --w-recovery-s tambem sem padrao)
        completos = dict(delta_latencia=0.1, w_analise_s=5, w_recovery_s=60)
        for falta in completos:
            with self.subTest(falta=falta):
                kw = {k: v for k, v in completos.items() if k != falta}
                with self.assertRaises(TypeError):
                    cb.comparar(self.man, 1, tipos_celula={"C": "teste"}, **kw)
        for kw in ({"delta_latencia": 0}, {"w_analise_s": -1}, {"delta_latencia": float("nan")},
                   {"w_recovery_s": -1}, {"w_recovery_s": float("inf")}):
            with self.subTest(kw=kw):
                with self.assertRaises(cb.ErroUso):
                    self.comparar(**kw)


class ReferenciaErroNaComparacao(Base):

    def test_delta_rec_metodologia_por_celula(self):
        sem_falha = self.d / "ref-exec"
        escrever_exec(sem_falha, eventos=eventos_ok(200), marcadores=None)
        ref = self.d / "ref.json"
        ref.write_text(json.dumps(rerr.gerar([sem_falha / "rep-001.csv"])), encoding="utf-8")
        linhas = []
        for celula in ("C1", "C2"):
            for br in "AB":
                d = self.d / celula / br
                ev = eventos_ok() + [("Login", "ERROR_TRANSPORT", FIM + k * MS, FIM + k * MS + 1)
                                     for k in range(50 if br == "A" else 0)]
                escrever_exec(d, eventos=ev, resumo=RESUMO, trilha=trilha_minima(cb.MODO_ESPERADO[br]))
                linhas.append("1\t%s\t%s\t%s" % (celula, br, Path(celula) / br))
        man = escrever_manifesto(self.d, linhas)
        r = cb.comparar(man, 1, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C1": "teste", "C2": "teste"},
                        n_boot=200, n_hier=200, referencias_erro={"C1": ref})
        e = r["celulas"]["C1"]["desfechos"][cb.DESFECHO_DREC_MET]
        self.assertEqual("sensibilidade", e["familia"])
        self.assertIsNone(e["p_holm"])
        # A: 50 erros na primeira janela de 5 s (n_j = 55, p_ref+ = 1 - 0,05^(1/200)); B: nenhum
        self.assertAlmostEqual(-5.0, e["d_b"]["1"], places=9)
        self.assertNotIn(cb.DESFECHO_DREC_MET, r["celulas"]["C2"]["desfechos"])
        self.assertAlmostEqual(1 - 0.05 ** (1 / 200), r["parametros"]["referencias_erro"]["C1"]["p_ref_sup"],
                               places=15)
        with self.assertRaises(cb.ErroUso):
            cb.comparar(man, 1, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C1": "teste", "C2": "teste"},
                        n_boot=200, n_hier=200, referencias_erro={"C9": ref})


# ============================================================================= descritivos do controlador

class Controlador(Base):
    """Janelas de 5 s; start em T0 + 10 s. Ver o cenario em trilha_cenario."""

    @staticmethod
    def trilha_cenario():
        p = S // 10
        meio = [dec(T0 + 5 * S), dec(T0 + 10 * S),
                dec(T0 + 15 * S, "CONSERVADOR", True, "R2"),
                acao("APLICAR", "AC-2", "APLICADA", T0 + 15 * S + p, t_fim_janela=T0 + 15 * S,
                     t_inicio=T0 + 15 * S + 2 * p, duracao=3 * p),
                dec(T0 + 20 * S, "CONSERVADOR"),
                acao("APLICAR", "AC-5", "APLICADA", T0 + 20 * S + p, t_fim_janela=T0 + 20 * S,
                     t_inicio=T0 + 20 * S + p, duracao=31 * p, alvo="isn3:11222"),
                dec(T0 + 25 * S, "CONSERVADOR"),
                acao("APLICAR", "AC-2", "FALHOU", T0 + 25 * S + p, t_fim_janela=T0 + 25 * S,
                     t_inicio=T0 + 25 * S + p, duracao=20 * p, caches=("counters",)),
                acao("REVERTER", "AC-2", "APLICADA", T0 + 27 * S + p, origem="FALHA_NA_APLICACAO",
                     t_fim_janela=T0 + 25 * S, t_inicio=T0 + 27 * S + p, duracao=p, caches=("counters",)),
                dec(T0 + 30 * S, "NORMAL", True, "R4"),
                acao("REVERTER", "AC-2", "APLICADA", T0 + 30 * S + p, origem="R4", regra="R4",
                     t_fim_janela=T0 + 30 * S, t_inicio=T0 + 30 * S + p, duracao=4 * p),
                dec(T0 + 35 * S)]
        return trilha("active", meio)

    def ler(self, linhas):
        escrever_exec(self.d, eventos=eventos_ok(5), trilha=linhas)
        return cb.ler_trilha_decisoes(self.d / "decisoes-rep-001.jsonl")

    def test_descritivos(self):
        t = self.ler(self.trilha_cenario())
        x = cb.descrever_controlador(t, INI, T0 + 40 * S)
        self.assertEqual("active", x["modo"])
        self.assertEqual(ATUADOR, x["atuador"])
        self.assertEqual("por-assinatura", x["politica"])
        self.assertEqual(SHA_PARAM, x["param_sha256"])
        self.assertTrue(x["completo"])
        self.assertEqual(7, x["decisoes"])
        self.assertEqual(2, x["transicoes"])
        self.assertEqual(1, x["entradas_conservador"])
        self.assertEqual(0, x["entradas_antes_da_falha"])
        self.assertEqual(3, x["janelas_conservador"])
        self.assertAlmostEqual(15.0, x["tempo_conservador_s"], places=9)
        self.assertAlmostEqual(5.0, x["atraso_entrada_conservador_s"], places=9)
        self.assertAlmostEqual(5.1, x["atraso_primeira_acao_s"], places=9)
        self.assertEqual(0, x["acoes_politica_antes_da_falha"])
        ac2, ac5 = x["acoes_por_tipo"]["AC-2"], x["acoes_por_tipo"]["AC-5"]
        self.assertEqual({"APLICADA": 1, "FALHOU": 1}, ac2["aplicar"])
        self.assertEqual({"APLICADA": 2}, ac2["reverter"])
        self.assertEqual({"APLICADA": 1}, ac5["aplicar"])
        self.assertEqual({}, ac5["reverter"])
        self.assertAlmostEqual(15.0, ac2["tempo_ativo_s"], places=9)        # 15,5 s a 30,5 s
        self.assertFalse(ac2["ativa_no_fim"])
        # R-5 da V-W5: acao ativa no fim conta ate o fim da medicao (t_fim_carga_ns), nao ate a ultima linha
        self.assertAlmostEqual(16.8, ac5["tempo_ativo_s"], places=9)        # 23,2 s a 40 s
        self.assertTrue(ac5["ativa_no_fim"])
        self.assertEqual({"sucesso": 2, "falha": 0}, x["reversoes"])
        self.assertEqual({"FALHA_NA_APLICACAO": {"sucesso": 1, "falha": 0}, "R4": {"sucesso": 1, "falha": 0}},
                         x["reversoes_por_origem"])
        self.assertEqual(T0 + 35 * S + S // 10, x["t_ultimo_ns"])

    def test_ativa_no_fim_sem_fim_da_medicao(self):
        x = cb.descrever_controlador(self.ler(self.trilha_cenario()), INI, None)
        self.assertIsNone(x["acoes_por_tipo"]["AC-5"]["tempo_ativo_s"])
        self.assertTrue(x["acoes_por_tipo"]["AC-5"]["ativa_no_fim"])
        self.assertAlmostEqual(15.0, x["acoes_por_tipo"]["AC-2"]["tempo_ativo_s"], places=9)

    def test_shadow_previstas(self):
        p = S // 10
        meio = [dec(T0 + 15 * S, "CONSERVADOR", True, "R2", modo="shadow"),
                acao("APLICAR", "AC-2", "PREVISTA", T0 + 15 * S + p, t_fim_janela=T0 + 15 * S, modo="shadow"),
                dec(T0 + 30 * S, "NORMAL", True, "R4", modo="shadow"),
                acao("REVERTER", "AC-2", "PREVISTA", T0 + 30 * S + p, origem="R4", regra="R4",
                     t_fim_janela=T0 + 30 * S, modo="shadow")]
        x = cb.descrever_controlador(self.ler(trilha("shadow", meio)), INI, T0 + 40 * S)
        self.assertEqual({"PREVISTA": 1}, x["acoes_por_tipo"]["AC-2"]["aplicar"])
        self.assertAlmostEqual(15.0, x["acoes_por_tipo"]["AC-2"]["tempo_ativo_s"], places=9)
        self.assertEqual({"sucesso": 1, "falha": 0}, x["reversoes"])

    def test_reversao_falha_mantem_ativa(self):
        p = S // 10
        meio = [dec(T0 + 15 * S, "CONSERVADOR", True, "R2"),
                acao("APLICAR", "AC-2", "APLICADA", T0 + 15 * S, t_fim_janela=T0 + 15 * S,
                     t_inicio=T0 + 15 * S, duracao=5 * p),
                dec(T0 + 30 * S, "NORMAL", True, "R4"),
                acao("REVERTER", "AC-2", "FALHOU", T0 + 30 * S, origem="R4", regra="R4", t_fim_janela=T0 + 30 * S,
                     t_inicio=T0 + 30 * S, duracao=p),
                dec(T0 + 35 * S, "CONSERVADOR", True, "R2"),
                acao("APLICAR", "AC-2", "JA_ATIVA", T0 + 35 * S, t_fim_janela=T0 + 35 * S),
                dec(T0 + 40 * S, "CONSERVADOR")]
        x = cb.descrever_controlador(self.ler(trilha("active", meio)), INI, T0 + 45 * S)
        ac2 = x["acoes_por_tipo"]["AC-2"]
        self.assertTrue(ac2["ativa_no_fim"])
        self.assertAlmostEqual(45.0 - 15.5, ac2["tempo_ativo_s"], places=9)
        self.assertEqual({"sucesso": 0, "falha": 1}, x["reversoes"])
        self.assertEqual({"APLICADA": 1, "JA_ATIVA": 1}, ac2["aplicar"])

    def test_sem_falha_tudo_conta_como_antes(self):
        p = S // 10
        meio = [dec(T0 + 15 * S, "CONSERVADOR", True, "R2"),
                acao("APLICAR", "AC-2", "APLICADA", T0 + 15 * S + p, t_fim_janela=T0 + 15 * S)]
        x = cb.descrever_controlador(self.ler(trilha("active", meio)), None, T0 + 40 * S)
        self.assertEqual(1, x["entradas_antes_da_falha"])
        self.assertEqual(1, x["acoes_politica_antes_da_falha"])
        self.assertIsNone(x["atraso_primeira_acao_s"])
        self.assertIsNone(x["atraso_entrada_conservador_s"])

    def test_rastreabilidade_integral(self):
        r = cb.conferir_rastreabilidade(self.ler(self.trilha_cenario()))
        self.assertTrue(r["integral"], r["problemas"])

    def test_rastreabilidade_quebrada(self):
        casos = {
            "acao sem decisao": trilha("active", [dec(T0 + 15 * S),
                                                  acao("APLICAR", "AC-2", "APLICADA", T0 + 16 * S,
                                                       t_fim_janela=T0 + 17 * S)]),
            "acao sem regra": trilha("active", [dec(T0 + 15 * S),
                                                acao("APLICAR", "AC-2", "APLICADA", T0 + 16 * S, regra=None,
                                                     t_fim_janela=T0 + 15 * S)]),
            "decisao sem sinais": trilha("active", [dec(T0 + 15 * S, sinais=False)]),
            "sem fim": trilha("active", [dec(T0 + 15 * S)], fim=False),
            "linhas perdidas": trilha("active", [dec(T0 + 15 * S)], linhas_perdidas=1),
            "conservacao": trilha("active", [dec(T0 + 15 * S)], acoes_pedidas=1),
        }
        for nome, linhas in casos.items():
            with self.subTest(caso=nome):
                self.tearDown()
                self.setUp()
                r = cb.conferir_rastreabilidade(self.ler(linhas))
                self.assertFalse(r["integral"])
                self.assertGreaterEqual(r["n_problemas"], 1)

    def test_encerramento_sem_regra_e_aceito(self):
        meio = [dec(T0 + 15 * S, "CONSERVADOR", True, "R2"),
                acao("APLICAR", "AC-2", "APLICADA", T0 + 16 * S, t_fim_janela=T0 + 15 * S),
                acao("REVERTER", "AC-2", "APLICADA", T0 + 40 * S, origem="ENCERRAMENTO", regra=None)]
        r = cb.conferir_rastreabilidade(self.ler(trilha("active", meio)))
        self.assertTrue(r["integral"], r["problemas"])

    def test_ultima_linha_truncada_e_linha_ruim_no_meio(self):
        texto = "".join(json.dumps(o) + "\n" for o in trilha("active", [dec(T0 + 15 * S)], fim=False))
        t = self.ler(texto + '{"tipo": "acao", "oper')
        self.assertFalse(t["completo"])
        self.assertTrue(any("truncada" in p for p in t["problemas"]))
        self.tearDown()
        self.setUp()
        with self.assertRaises(cb.ErroUso):
            self.ler(texto.replace('"tipo": "decisao"', '"tipo": "decisao"\x00{', 1))

    def test_sem_meta_ou_outro_esquema(self):
        with self.assertRaises(cb.ErroUso):
            self.ler([dec(T0 + 15 * S)])
        self.tearDown()
        self.setUp()
        linhas = trilha_minima("active")
        linhas[0]["esquema"] = "observaveis-controlador/1.1"
        with self.assertRaises(cb.ErroUso):
            self.ler(linhas)

    def test_trilha_ausente(self):
        escrever_exec(self.d, eventos=eventos_ok(5))
        self.assertIsNone(cb.ler_trilha_decisoes(self.d / "decisoes-rep-001.jsonl"))
        _, info = self.desf(self.d)
        self.assertIsNone(info["controlador"])
        self.assertIsNone(info["rastreabilidade"])


# ============================================================================= energia

def escrever_recursos(d, k_max=40):
    linhas = [CAB_REC] + ["%d,vm,%d,1,0,ok" % (T0 + k * S, 2_000_000 * k) for k in range(k_max + 1)]
    (Path(d) / "recursos-rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    meta = {"contrato": "recursos/1", "clk_tck": 100, "nproc": 4, "intervalo_ms": 1000}
    (Path(d) / "recursos-meta-rep-001.json").write_text(json.dumps(meta), encoding="utf-8")


class Energia(Base):
    """Modelo da decisao do Gestor de 05/11 (conferencia do Bibliotecario): P_max = 181 W; razao ociosa central
    0,3 (Barroso e Holzle 2007, p. 36) e sensibilidade 0,5 (p. 34); 0,7 saiu por nao ter fonte."""

    ENERGIA = {"p_max_w": 181, "razao_central": 0.3}

    def exec_energia(self, d, p_max_w=181, fator=1.0, braco=None):
        tr = None if braco is None else trilha_minima(cb.MODO_ESPERADO[braco])
        escrever_exec(d, acoes=acoes_regulares(200, fator=fator), violacoes=[], trilha=tr)
        escrever_recursos(d)
        j = ep.analisar_execucao(d, 1, "S3", Path(d) / "marcadores.txt", 60, None, p_max_w)
        (Path(d) / "energia-rep-001.json").write_text(json.dumps(j), encoding="utf-8")
        return j

    def test_razoes_padrao_do_modelo(self):
        self.assertEqual((0.3, 0.5), ep.RAZOES_OCIOSO_PADRAO)
        j = self.exec_energia(self.d)
        self.assertEqual({"0.3", "0.5"}, set(j["energia"]["por_razao"]))
        self.assertEqual(181, j["energia"]["p_max_w"])

    def test_valor_pelas_fases_selecionadas(self):
        j = self.exec_energia(self.d)
        out, _ = self.desf(self.d, energia=self.ENERGIA)
        rot = [f["rotulo"] for f in j["fases"] if f["rotulo"] in ("durante", "recuperacao")]
        for r in ("0.3", "0.5"):
            esperado = 1000.0 * sum(j["energia"]["por_razao"][r]["vm"][x]["e_total_j"] for x in rot) / \
                sum(j["uteis"][x] for x in rot)
            self.assertAlmostEqual(esperado, out[cb.PREFIXO_ENERGIA + r]["y"], places=9)
        self.assertNotIn(cb.PREFIXO_ENERGIA + "0.7", out)

    def test_sem_configuracao_nao_entra(self):
        self.exec_energia(self.d)
        out, _ = self.desf(self.d)
        self.assertFalse(any(n.startswith(cb.PREFIXO_ENERGIA) for n in out))
        self.assertIn(cb.DESFECHO_CPU, out)

    def test_p_max_divergente(self):
        self.exec_energia(self.d, p_max_w=181)
        with self.assertRaises(cb.ErroUso) as cm:
            self.desf(self.d, energia={"p_max_w": 125, "razao_central": 0.3})
        self.assertIn("P_max", str(cm.exception))

    def test_razao_central_ausente(self):
        # 0,7 saiu do modelo: pedir 0,7 como central e erro de uso, nao desfecho vazio
        self.exec_energia(self.d)
        with self.assertRaises(cb.ErroUso):
            self.desf(self.d, energia={"p_max_w": 181, "razao_central": 0.7})

    def test_energia_nao_calculada(self):
        self.exec_energia(self.d, p_max_w=None)
        out, _ = self.desf(self.d, energia=self.ENERGIA)
        e = out[cb.PREFIXO_ENERGIA + "0.3"]
        self.assertIsNone(e["y"])
        self.assertIn("p-max-w", e["motivo"])

    def test_familias_na_comparacao(self):
        linhas = []
        for b in (1, 2):
            for br in "AB":
                d = self.d / ("e%d%s" % (b, br))
                self.exec_energia(d, fator=1.0 if br == "A" else 0.9, braco=br)
                linhas.append("%d\tC\t%s\t%s" % (b, br, d.name))
        man = escrever_manifesto(self.d, linhas)
        r = cb.comparar(man, 1, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula={"C": "teste"},
                        n_boot=200, n_hier=200, energia=self.ENERGIA)
        des = r["celulas"]["C"]["desfechos"]
        self.assertEqual("secundario", des[cb.PREFIXO_ENERGIA + "0.3"]["familia"])
        self.assertIsNotNone(des[cb.PREFIXO_ENERGIA + "0.3"]["p_holm"])
        self.assertEqual("sensibilidade", des[cb.PREFIXO_ENERGIA + "0.5"]["familia"])
        self.assertIsNone(des[cb.PREFIXO_ENERGIA + "0.5"]["p_holm"])
        self.assertNotIn(cb.PREFIXO_ENERGIA + "0.7", des)
        self.assertEqual(self.ENERGIA, r["parametros"]["energia"])


# ============================================================================= CLI

class Cli(Base):

    def setUp(self):
        super().setUp()
        linhas = gerar_celula(self.d, "C1", [1, 2], [0, 0], n_acoes=50)
        linhas += gerar_celula(self.d, "C2", [0, 1], [0, 0], n_acoes=50)
        self.man = escrever_manifesto(self.d, linhas)

    def cli(self, *extra, nome="c.json", base=True):
        args = ["--manifesto", self.man, "--semente-boot", 5, "--saida", self.d / nome, "--n-boot", 200,
                "--n-hier", 200]
        if base:
            args += ["--delta-latencia", 0.10, "--w-analise-s", 5, "--w-recovery-s", 60, "--celulas-teste", "C1",
                     "--celulas-descritivas", "C2"]
        rc, out, err = rodar_cli(*(args + list(extra)), cwd=self.d)
        return rc, self.d / nome, err

    def test_execucao_e_determinismo(self):
        rc, saida, err = self.cli(nome="a.json")
        self.assertEqual(0, rc, err)
        rc2, saida2, err2 = self.cli(nome="b.json")
        self.assertEqual(0, rc2, err2)
        self.assertEqual(saida.read_bytes(), saida2.read_bytes())
        self.assertEqual(saida.with_suffix(".md").read_bytes(), saida2.with_suffix(".md").read_bytes())
        j = json.loads(saida.read_text(encoding="utf-8"))
        self.assertEqual("comparar-bracos/2", j["esquema"])
        self.assertEqual({"C1": "teste", "C2": "descritiva"}, j["parametros"]["tipos_celula"])
        md = saida.with_suffix(".md").read_text(encoding="utf-8")
        self.assertIn("co-principal-V", md)
        self.assertIn("Intersecao-uniao", md)

    def assert_recusa(self, *extra, base=True):
        rc, saida, err = self.cli(*extra, base=base)
        self.assertEqual(2, rc, err)
        self.assertFalse(saida.exists())
        self.assertFalse(saida.with_suffix(".md").exists())

    def test_sem_parametros_obrigatorios(self):
        tipos = ("--celulas-teste", "C1", "--celulas-descritivas", "C2")
        self.assert_recusa("--w-analise-s", 5, "--w-recovery-s", 60, *tipos, base=False)
        self.assert_recusa("--delta-latencia", 0.1, "--w-recovery-s", 60, *tipos, base=False)
        # P-5 da V-W5: --w-recovery-s obrigatorio e sem padrao
        self.assert_recusa("--delta-latencia", 0.1, "--w-analise-s", 5, *tipos, base=False)

    def test_opcoes_retiradas(self):
        self.assert_recusa("--regra", "holm2")
        self.assert_recusa("--primario", "acao_s3_p99_com_espera")

    def test_tipos_de_celula(self):
        # C2 sem tipo; celula tipada fora do manifesto; celula em duas listas
        self.assert_recusa("--delta-latencia", 0.1, "--w-analise-s", 5, "--w-recovery-s", 60, "--celulas-teste", "C1",
                           base=False)
        self.assert_recusa("--celulas-referencia", "C9")
        self.assert_recusa("--celulas-referencia", "C1")

    def test_valores_invalidos(self):
        self.assert_recusa("--delta-latencia", 0, "--w-analise-s", 5, "--w-recovery-s", 60, "--celulas-teste", "C1,C2",
                           base=False)
        self.assert_recusa("--delta-latencia", 0.1, "--w-analise-s", 0, "--w-recovery-s", 60, "--celulas-teste",
                           "C1,C2", base=False)
        self.assert_recusa("--w-recovery-s", -1)
        self.assert_recusa("--referencia-erro", "C1")
        self.assert_recusa("--referencia-erro", "C1=nao-existe.json")
        self.assert_recusa("--p-max-w", 181)
        self.assert_recusa("--razao-ocioso-central", 0.3)


# ============================================================================= correcoes da V-W5

def comparar_padrao(man, tipos, **kw):
    base = dict(delta_latencia=0.10, w_analise_s=5, w_recovery_s=60, tipos_celula=tipos, n_boot=200, n_hier=200)
    base.update(kw)
    return cb.comparar(man, 1, **base)


class ErroEFalhasEmDiferenca(Base):
    """P-4 da V-W5 (G3 de 28/10): taxa_erro e falhas_percebidas_por_1000 em diferenca; a razao com exclusao
    dos zeros fica como sensibilidade (`<nome>:razao`, sem Holm)."""

    def test_sensibilidade_em_desfechos_execucao(self):
        ev = eventos_ok(20) + [("Login", "ERROR_TRANSPORT", INI + S, INI + S + MS)]
        escrever_exec(self.d, eventos=ev, acoes=acoes_regulares(50, falhas=(3,)))
        out, _ = self.desf(self.d)
        for n in ("taxa_erro", "falhas_percebidas_por_1000"):
            with self.subTest(n=n):
                self.assertEqual(out[n]["y"], out[n + cb.SUFIXO_RAZAO]["y"])
                self.assertIsNotNone(out[n]["y"])

    def test_escalas_e_familias_na_comparacao(self):
        linhas = []
        falhas = {("1", "A"): (0, 1), ("1", "B"): (), ("2", "A"): (), ("2", "B"): (5,)}
        for b in ("1", "2"):
            for br in "AB":
                d = self.d / ("e%s%s" % (b, br))
                escrever_exec(d, acoes=acoes_regulares(100, falhas=falhas[(b, br)]), violacoes=[], resumo=RESUMO,
                              trilha=trilha_minima(cb.MODO_ESPERADO[br]))
                linhas.append("%s\tC\t%s\t%s" % (b, br, d.name))
        r = comparar_padrao(escrever_manifesto(self.d, linhas), {"C": "teste"})
        des = r["celulas"]["C"]["desfechos"]
        e = des["falhas_percebidas_por_1000"]
        self.assertEqual("diferenca", e["escala"])
        self.assertEqual("secundario", e["familia"])
        self.assertEqual(2, e["n_blocos"])                        # zeros num braco nao excluem
        self.assertAlmostEqual(-20.0, e["d_b"]["1"], places=9)    # 0 - 2/100 x 1000
        self.assertAlmostEqual(10.0, e["d_b"]["2"], places=9)
        self.assertIsNotNone(e["p_holm"])
        s = des["falhas_percebidas_por_1000" + cb.SUFIXO_RAZAO]
        self.assertEqual("log-razao", s["escala"])
        self.assertEqual("sensibilidade", s["familia"])
        self.assertEqual(0, s["n_blocos"])                        # razao com exclusao: os dois blocos tem zero
        self.assertIsNone(s["p_holm"])
        self.assertEqual("secundario", cb.familia_de("taxa_erro"))
        self.assertEqual("sensibilidade", cb.familia_de("taxa_erro" + cb.SUFIXO_RAZAO))
        self.assertIn("taxa_erro", cb.ESCALA_DIFERENCA)
        self.assertIn("falhas_percebidas_por_1000", cb.ESCALA_DIFERENCA)


class Religamento(Base):
    """P-6 da V-W5 (achado A-1, opcao a): o W-6 religa o no parado pelo AC-5 no fim da carga, antes do laco de
    convergencia, e grava o instante; o Delta_conv conta desse religamento. Sem o registro, nao avaliavel."""

    P = S // 10

    def trilha_ac5(self, resultado="APLICADA", modo="active", tipo="AC-5"):
        meio = [dec(T0 + 15 * S, "CONSERVADOR", True, "R2", modo=modo),
                acao("APLICAR", tipo, resultado, T0 + 15 * S + self.P, t_fim_janela=T0 + 15 * S, modo=modo,
                     alvo="isn3:11222" if tipo == "AC-5" else None)]
        return trilha(modo, meio)

    def conv(self, resumo, tr):
        escrever_exec(self.d, eventos=eventos_ok(), resumo=resumo, trilha=tr)
        out, info = self.desf(self.d)
        return out[cb.DESFECHO_DCONV], out[cb.DESFECHO_DIV], info

    def test_ac5_com_religamento(self):
        dc, dv, info = self.conv(dict(RESUMO, **{cb.CAMPO_RELIGAMENTO: T0 + 101 * S}), self.trilha_ac5())
        self.assertAlmostEqual(11.0, dc["y"], places=9)               # 112 s - 101 s
        self.assertEqual(cb.CAMPO_RELIGAMENTO, dc["origem"])
        self.assertEqual(cb.CAMPO_RELIGAMENTO, info["delta_conv_origem"])
        self.assertEqual(0.0, dv["y"])
        self.assertTrue(info["ac5_aplicada"])

    def test_ac5_sem_registro_do_religamento(self):
        dc, dv, info = self.conv(RESUMO, self.trilha_ac5())
        self.assertIsNone(dc["y"])
        self.assertIn("religamento", dc["motivo"])
        self.assertIsNone(dv["y"])
        self.assertIn("religamento", dv["motivo"])
        self.assertIsNone(info["delta_conv_origem"])

    def test_sem_ac5_conta_do_fim_da_carga(self):
        for tr in (self.trilha_ac5(tipo="AC-2"), self.trilha_ac5("PREVISTA", "shadow"), self.trilha_ac5("FALHOU"),
                   None):
            with self.subTest(tr=None if tr is None else tr[2]["resultado"] + tr[2]["acao"]["tipo"]):
                self.tearDown()
                self.setUp()
                dc, dv, info = self.conv(RESUMO, tr)
                self.assertAlmostEqual(12.0, dc["y"], places=9)
                self.assertEqual("t_fim_carga_ns", dc["origem"])
                self.assertEqual(0.0, dv["y"])

    def test_religamento_registrado_e_a_origem(self):
        dc, _, _ = self.conv(dict(RESUMO, **{cb.CAMPO_RELIGAMENTO: T0 + 101 * S}), None)
        self.assertAlmostEqual(11.0, dc["y"], places=9)
        self.assertEqual(cb.CAMPO_RELIGAMENTO, dc["origem"])

    def test_religamento_antes_do_fim_da_carga_e_invalido(self):
        dc, _, _ = self.conv(dict(RESUMO, **{cb.CAMPO_RELIGAMENTO: T0 + 99 * S}), self.trilha_ac5())
        self.assertIsNone(dc["y"])
        self.assertIn(cb.CAMPO_RELIGAMENTO, dc["motivo"])

    def test_origem_na_celula(self):
        linhas = []
        for br in "AB":
            d = self.d / br
            res = dict(RESUMO, **({cb.CAMPO_RELIGAMENTO: T0 + 101 * S} if br == "B" else {}))
            tr = trilha_minima("shadow") if br == "A" else self.trilha_ac5()
            escrever_exec(d, acoes=acoes_regulares(50), violacoes=[], resumo=res, trilha=tr)
            linhas.append("1\tC\t%s\t%s" % (br, br))
        r = comparar_padrao(escrever_manifesto(self.d, linhas), {"C": "teste"})
        c = r["celulas"]["C"]
        self.assertEqual({"A": "t_fim_carga_ns", "B": cb.CAMPO_RELIGAMENTO}, c["recuperacao"]["delta_conv_origem"]["1"])
        self.assertAlmostEqual(11.0 - 12.0, c["desfechos"][cb.DESFECHO_DCONV]["d_b"]["1"], places=9)


class CelulaSemEventoNasFases(Base):
    """A-2 da V-W5: celula sem falha nas fases durante/recuperacao sai vazia; recusa, e a chamada propria com
    --fases antes funciona."""

    def montar(self):
        linhas = []
        for br in "AB":
            d = self.d / br
            escrever_exec(d, acoes=acoes_regulares(50), violacoes=[], marcadores=None, resumo=RESUMO)
            linhas.append("1\tN\t%s\t%s" % (br, br))
        return escrever_manifesto(self.d, linhas)

    def test_recusa_fases_vazias(self):
        with self.assertRaises(cb.ErroUso) as cm:
            comparar_padrao(self.montar(), {"N": "descritiva"})
        self.assertIn("antes", str(cm.exception))

    def test_chamada_propria_com_antes(self):
        r = comparar_padrao(self.montar(), {"N": "descritiva"}, fases=["antes"])
        e = r["celulas"]["N"]["desfechos"][cb.DESFECHO_V]
        self.assertEqual(1, e["n_blocos"])
        self.assertEqual(50, e["n_por_bloco"]["1"]["A"])


class TrilhaObrigatoriaNoTeste(Base):
    """A-3 da V-W5: nas celulas de teste, trilha nos dois bracos; no B, atuador != nulo e politica != nenhuma;
    param_sha256 igual entre A e B do mesmo bloco."""

    def montar(self, trilha_a="padrao", trilha_b="padrao"):
        linhas = []
        for br, tr in (("A", trilha_a), ("B", trilha_b)):
            if tr == "padrao":
                tr = trilha_minima(cb.MODO_ESPERADO[br])
            d = self.d / br
            escrever_exec(d, acoes=acoes_regulares(50), violacoes=[], resumo=RESUMO, trilha=tr)
            linhas.append("1\tC\t%s\t%s" % (br, br))
        return escrever_manifesto(self.d, linhas)

    def test_coerente_passa(self):
        r = comparar_padrao(self.montar(), {"C": "teste"})
        self.assertIn("regra_iu", r["celulas"]["C"])

    def test_recusas(self):
        casos = {
            "A sem trilha": dict(trilha_a=None),
            "B sem trilha": dict(trilha_b=None),
            "B com atuador nulo": dict(trilha_b=trilha("active", [dec(T0 + 15 * S)], atuador="nulo")),
            "B com politica nenhuma": dict(trilha_b=trilha("active", [dec(T0 + 15 * S)], politica="nenhuma")),
            "param_sha256 diferente": dict(trilha_b=trilha("active", [dec(T0 + 15 * S)], param_sha256="b" * 64)),
        }
        for nome, kw in casos.items():
            with self.subTest(caso=nome):
                self.tearDown()
                self.setUp()
                with self.assertRaises(cb.ErroUso):
                    comparar_padrao(self.montar(**kw), {"C": "teste"})

    def test_descritiva_nao_exige_trilha(self):
        r = comparar_padrao(self.montar(trilha_a=None, trilha_b=None), {"C": "descritiva"})
        self.assertFalse(r["celulas"]["C"]["teste"])


class MaisDe16BlocosAntecipado(Base):

    def test_recusa_antes_de_ler_as_execucoes(self):
        # 17 blocos, sem trilha: a recusa tem de ser a do limite da permutacao exata, nao a da trilha
        linhas = []
        for b in range(1, 18):
            for br in "AB":
                escrever_exec(self.d / ("e%d%s" % (b, br)), acoes=acoes_regulares(3))
                linhas.append("%d\tC\t%s\te%d%s" % (b, br, b, br))
        with self.assertRaises(cb.ErroUso) as cm:
            comparar_padrao(escrever_manifesto(self.d, linhas), {"C": "teste"})
        self.assertIn("16", str(cm.exception))


class SensibilidadeAComUmBloco(Base):

    def test_ic_none(self):
        # R-7 da V-W5: so um bloco com violacao em A -> estimativa existe, IC None (como ic_boot_blocos)
        r = comparar_padrao(escrever_manifesto(self.d, gerar_celula(self.d, "C", [2, 0], [1, 0], n_acoes=100)),
                            {"C": "teste"})
        a = r["celulas"]["C"]["co_principal_v"]["sensibilidades"]["a_razao_exclusao_zero_a"]
        self.assertEqual(1, a["blocos"])
        self.assertAlmostEqual(0.5, a["estimativa"], places=12)
        self.assertIsNone(a["ic_boot_blocos"])


class MarkdownCelulaSemTeste(Base):

    def test_ic_descritivo_rotulado(self):
        linhas = gerar_celula(self.d, "T", [1, 2], [0, 0], n_acoes=50)
        linhas += gerar_celula(self.d, "D", [1, 0], [0, 0], n_acoes=50)
        r = comparar_padrao(escrever_manifesto(self.d, linhas), {"T": "teste", "D": "descritiva"})
        md = cb.tabela_markdown(r)
        secao_d = md[md.index("## Celula D"):md.index("## Celula T")]
        self.assertIn("IC descritivo", secao_d)
        self.assertIn("nao e resultado de teste", secao_d)
        self.assertNotIn("IC descritivo", md[md.index("## Celula T"):])


if __name__ == "__main__":
    unittest.main()
