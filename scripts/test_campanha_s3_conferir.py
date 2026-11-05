"""Testes das conferencias do orquestrador S3 (scripts/campanha_s3_conferir.py), W-6 / B-46.

- parametros: arquivo da campanha (esquema campanha-s3/1) com cada numero e a sua origem; sem origem, recusa
  (regra do autor: limiar sem origem fica obrigatorio e sem padrao). Chave desconhecida tambem recusa.
- controlador: o --controller-config do braco B tem o bloco politica (A-3 da V-W5, O10 da V-W2a) e o
  tetoLagMs igual ao --hotrod-timeout-ms (R7 da V-W2a).
- meta: a linha meta de decisoes-rep-NNN.jsonl. Braco A em shadow; braco B em active com atuador != nulo,
  politica != nenhuma e parametros_politica nao nulo; param_sha256 igual ao SHA-256 do arquivo (o mesmo nos dois
  bracos do bloco).
- trilha: o alvo do AC-5 APLICADA na trilha (regra de ac5_aplicada do comparar_bracos.py) bate com o da linha de
  fim da carga.
- referencia (B2 da V-W6): a referencia de p99 de cada modo de replicacao e do cenario S3 e do perfil daquele modo.
- W-6c: esquema campanha-s3/2 = campanha-s3/1 mais os cinco numeros do F3 (f3_no, f3_delay_ms, f3_jitter_ms,
  f3_distribuicao, f3_dur_s), cada um com origem; o /1 continua valido (calibracao sem F3). Trilha completa: linha
  fim presente e linhas_perdidas = 0 (contrato JSONL 1.2, linha fim de decisoes); o orquestrador marca
  trilha_incompleta e, com --exigir-trilha-completa, descarta como infraestrutura (B-7 do B-40).

Uso: python -I scripts/test_campanha_s3_conferir.py
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import campanha_s3_conferir as cc  # noqa: E402

SHA = "a" * 64
# M1 da V-W6: o teto conta o T_enc do pior caso, que e constante do orquestrador
EXTRA = ("--t-enc-pior-s", "21")


def rodar(*args):
    r = subprocess.run([sys.executable, "-I", str(AQUI / "campanha_s3_conferir.py")] + list(args),
                       capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def parametros_validos():
    v = {"duracao_s": 300, "warmup_min_s": 30, "inj_offset_s": 49, "f1_no": "isn2", "f1_dur_s": 60,
         "f2_no": "isn3", "f2_dur_s": 90, "f4_no": "isn3", "f4_severidade": "N3", "f4_direcao": "both",
         "f4_dur_s": 90, "hotrod_timeout_ms": 2000, "convergencia_teto_s": 600, "convergencia_periodo_s": 5,
         "w_recovery_s": 161, "religamento_prazo_s": 180, "exec_teto_s": 1300, "encerramento_folga_s": 2,
         "volume_exec_mb": 200}
    return {"esquema": "campanha-s3/1", "versao": "teste",
            "parametros": {k: {"valor": x, "origem": "teste"} for k, x in v.items()}}


F3_REBASELINE = {"f3_no": "isn2", "f3_delay_ms": 20, "f3_jitter_ms": 13, "f3_distribuicao": "normal",
                 "f3_dur_s": 251}


def parametros_v2(**f3):
    """campanha-s3/2: os de campanha-s3/1 mais os do F3 (W-6c; B-40 secao 4.3, valores do rebaseline, PR secao 2 e
    DT-011)."""
    obj = parametros_validos()
    obj["esquema"] = "campanha-s3/2"
    v = dict(F3_REBASELINE)
    v.update(f3)
    obj["parametros"].update({k: {"valor": x, "origem": "teste"} for k, x in v.items()})
    return obj


def escrever(d, nome, obj):
    p = Path(d) / nome
    p.write_text(obj if isinstance(obj, str) else json.dumps(obj), encoding="utf-8")
    return p


def meta(modo="active", atuador="por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)", politica="por-assinatura",
         pp=True, sha=SHA, esquema="decisoes-controlador/1.2", p2="ausente"):
    pol = {"k_lag": 2, "teto_lag_ms": 2000, "t_reb_ns": 60000000000, "cota_ac5": 1, "origem": {}}
    if p2 != "ausente":
        pol["p2_habilitada"] = p2
    return {"tipo": "meta", "esquema": esquema, "modo": modo, "param_sha256": sha, "atuador": atuador,
            "politica": politica, "parametros_politica": pol if pp else None}


def acao(tipo, resultado, alvo, executada=True, operacao="APLICAR"):
    return {"tipo": "acao", "operacao": operacao, "executada": executada, "modo": "active", "atuador": "x",
            "acao": {"tipo": tipo, "caches": ["sessions-async", "counters-async"], "alvo": alvo, "valor": None},
            "resultado": resultado}


def trilha(d, *linhas, fim=True, perdidas=0):
    """Trilha de decisoes; o fim traz linhas_perdidas (contrato 1.1+), ou nao traz o campo com perdidas="sem-campo"."""
    f = {"tipo": "fim"}
    if perdidas != "sem-campo":
        f["linhas_perdidas"] = perdidas
    corpo = [meta()] + list(linhas) + ([f] if fim else [])
    return escrever(d, "decisoes-rep-001.jsonl", "".join(json.dumps(x) + "\n" for x in corpo))


class Parametros(unittest.TestCase):

    def test_validos_imprimem_nome_valor(self):
        with tempfile.TemporaryDirectory() as d:
            p = escrever(d, "p.json", parametros_validos())
            rc, out, err = rodar("parametros", *EXTRA, "--arquivo", str(p))
            self.assertEqual(0, rc, err)
            linhas = dict(l.split("\t") for l in out.strip().split("\n"))
            self.assertEqual("300", linhas["duracao_s"])
            self.assertEqual("isn3", linhas["f2_no"])
            self.assertEqual(set(cc.PARAMETROS), set(linhas))

    def test_sem_origem_recusa(self):
        for origem in ("", "   ", None):
            with self.subTest(origem=origem), tempfile.TemporaryDirectory() as d:
                obj = parametros_validos()
                obj["parametros"]["inj_offset_s"]["origem"] = origem
                rc, _, err = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", obj)))
                self.assertEqual(2, rc)
                self.assertIn("inj_offset_s", err)

    def test_ausente_desconhecido_e_tipo(self):
        casos = {"ausente": lambda o: o["parametros"].pop("f2_dur_s"),
                 "desconhecido": lambda o: o["parametros"].update({"f3_dur_s": {"valor": 1, "origem": "x"}}),
                 "tipo": lambda o: o["parametros"]["duracao_s"].update({"valor": "300"}),
                 "no": lambda o: o["parametros"]["f2_no"].update({"valor": "isn4"}),
                 "severidade": lambda o: o["parametros"]["f4_severidade"].update({"valor": "N9"}),
                 "direcao": lambda o: o["parametros"]["f4_direcao"].update({"valor": "lados"}),
                 "esquema": lambda o: o.update({"esquema": "campanha-s3/2"}),
                 "booleano": lambda o: o["parametros"]["f1_dur_s"].update({"valor": True})}
        for nome, mudar in casos.items():
            with self.subTest(caso=nome), tempfile.TemporaryDirectory() as d:
                obj = parametros_validos()
                mudar(obj)
                rc, _, err = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", obj)))
                self.assertEqual(2, rc, nome)

    def test_coerencia_das_janelas(self):
        """A falha tem de acabar antes do fim da carga, e o teto da execucao cobre carga + religamento + convergencia."""
        casos = {"f2 passa do fim": ("f2_dur_s", 300), "f1 passa do fim": ("f1_dur_s", 260),
                 "teto curto": ("exec_teto_s", 1000)}
        for nome, (k, x) in casos.items():
            with self.subTest(caso=nome), tempfile.TemporaryDirectory() as d:
                obj = parametros_validos()
                obj["parametros"][k]["valor"] = x
                rc, _, err = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", obj)))
                self.assertEqual(2, rc, err)

    def test_json_invalido(self):
        with tempfile.TemporaryDirectory() as d:
            rc, _, _ = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", "{nao e json")))
            self.assertEqual(2, rc)


class TetoComParcelas(unittest.TestCase):
    """M1 da V-W6: exec_teto_s >= duracao_s + t_enc_pior_s + religamento_prazo_s + convergencia_teto_s +
    convergencia_periodo_s. O T_enc (ate 21 s entre o fim da carga e a linha de fim, contrato JSONL 1.2) vem do
    orquestrador; o ultimo periodo cobre a volta do laco de convergencia que dorme antes de olhar o teto
    (ConvergenciaLoop.executar). O tempo ate a ancora (t_pre <= 2,4 s no I-3b) e o fechamento depois da convergencia
    (t_pos = 13,5 s) sao medidos, nao constantes: entram com folga no pre-registro (adenda da V-W6, secao 11; pre-registro
    da calibracao 4.1), nao aqui. Com os parametros validos: 300 + 21 + 180 + 600 + 5 = 1106."""

    def teto(self, d, valor, *extra, **outros):
        obj = parametros_validos()
        obj["parametros"]["exec_teto_s"]["valor"] = valor
        for k, x in outros.items():
            obj["parametros"][k]["valor"] = x
        return rodar("parametros", *extra, "--arquivo", str(escrever(d, "p.json", obj)))

    def test_no_minimo_passa_e_um_abaixo_recusa(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(0, self.teto(d, 1106, *EXTRA)[0])
            rc, _, err = self.teto(d, 1105, *EXTRA)
            self.assertEqual(2, rc)
            for parcela in ("t_enc_pior_s (21)", "religamento_prazo_s (180)", "convergencia_periodo_s (5)"):
                self.assertIn(parcela, err)

    def test_o_t_enc_do_orquestrador_muda_o_minimo(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(2, self.teto(d, 1106, "--t-enc-pior-s", "22")[0])
            self.assertEqual(0, self.teto(d, 1106, "--t-enc-pior-s", "20")[0])

    def test_t_enc_obrigatorio_e_nao_negativo(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(2, self.teto(d, 2000)[0], "sem --t-enc-pior-s")
            self.assertEqual(2, self.teto(d, 2000, "--t-enc-pior-s", "-1")[0])

    def test_valores_do_pre_registro_da_calibracao_aceitam_1005(self):
        """Pre-registro da calibracao 4.1: 300 + 21 + 60 + 600 + 1,5 x (2,4 + 13,5) = 1004,9 -> 1005 s; o minimo do
        codigo com esses numeros e 300 + 21 + 60 + 600 + 5 = 986."""
        pr = dict(duracao_s=300, religamento_prazo_s=60, convergencia_teto_s=600, convergencia_periodo_s=5,
                  inj_offset_s=49, f1_dur_s=60, f2_dur_s=90, f4_dur_s=90, w_recovery_s=191)
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(0, self.teto(d, 1005, *EXTRA, **pr)[0])
            self.assertEqual(0, self.teto(d, 986, *EXTRA, **pr)[0])
            self.assertEqual(2, self.teto(d, 985, *EXTRA, **pr)[0])


class Referencia(unittest.TestCase):
    """B2 da V-W6: o programa recusa referencia de outro perfil (LigacaoControlador.java:204), entao o orquestrador
    passa uma por modo de replicacao e confere cada uma antes (pre-voo e --dry-run)."""

    @staticmethod
    def ref(perfil="sync", cenario="S3", esquema="referencia-p99/1"):
        return {"esquema": esquema, "cenario": cenario, "perfil": perfil, "p99_ns": {"Login": 10}}

    def test_perfil_certo_passa_e_imprime(self):
        with tempfile.TemporaryDirectory() as d:
            for perfil in ("sync", "async"):
                p = escrever(d, "r.json", self.ref(perfil))
                rc, out, _ = rodar("referencia", "--arquivo", str(p), "--perfil", perfil)
                self.assertEqual(0, rc)
                self.assertIn("perfil\t%s" % perfil, out)
                self.assertIn("sha256\t%s" % cc.sha256(p), out)

    def test_perfil_trocado_recusa(self):
        with tempfile.TemporaryDirectory() as d:
            rc, _, err = rodar("referencia", "--arquivo", str(escrever(d, "r.json", self.ref("async"))), "--perfil", "sync")
            self.assertEqual(5, rc)
            self.assertIn("perfil", err)

    def test_cenario_diferente_de_s3_recusa(self):
        with tempfile.TemporaryDirectory() as d:
            rc, _, _ = rodar("referencia", "--arquivo", str(escrever(d, "r.json", self.ref(cenario="S1"))), "--perfil", "sync")
            self.assertEqual(5, rc)

    def test_esquema_errado_ou_sem_p99_e_uso(self):
        with tempfile.TemporaryDirectory() as d:
            rc, _, _ = rodar("referencia", "--arquivo", str(escrever(d, "r.json", self.ref(esquema="x/1"))), "--perfil", "sync")
            self.assertEqual(2, rc)
            sem = self.ref()
            del sem["p99_ns"]
            rc, _, _ = rodar("referencia", "--arquivo", str(escrever(d, "r.json", sem)), "--perfil", "sync")
            self.assertEqual(2, rc)

    def test_perfil_fora_de_sync_async_e_uso_e_arquivo_ausente_e_4(self):
        with tempfile.TemporaryDirectory() as d:
            p = escrever(d, "r.json", self.ref())
            self.assertEqual(2, rodar("referencia", "--arquivo", str(p), "--perfil", "SYNC")[0])
            self.assertEqual(4, rodar("referencia", "--arquivo", str(Path(d) / "nao.json"), "--perfil", "sync")[0])


class Controlador(unittest.TestCase):

    def base(self, politica=True, teto=2000):
        c = {"versao": "t", "wNs": 5000000000}
        if politica:
            c["politica"] = {"kLag": 2, "tetoLagMs": teto, "tRebNs": 60000000000, "cotaAc5": 1, "origem": {}}
        c["defasagem"] = {"lagInMs": 2000, "lagOutMs": 1000, "passoLeituraMs": 20, "origem": {}}
        return c

    def test_com_politica(self):
        with tempfile.TemporaryDirectory() as d:
            p = escrever(d, "c.json", self.base())
            rc, out, err = rodar("controlador", "--arquivo", str(p), "--hotrod-timeout-ms", "2000", "--exigir-politica")
            self.assertEqual(0, rc, err)
            self.assertIn("politica\tsim", out)
            self.assertIn("defasagem\tsim", out)
            self.assertIn("sha256\t", out)

    def test_sem_politica_recusa_quando_exigida(self):
        with tempfile.TemporaryDirectory() as d:
            p = escrever(d, "c.json", self.base(politica=False))
            rc, _, err = rodar("controlador", "--arquivo", str(p), "--hotrod-timeout-ms", "2000", "--exigir-politica")
            self.assertEqual(5, rc)
            self.assertIn("politica", err)
            rc, out, _ = rodar("controlador", "--arquivo", str(p), "--hotrod-timeout-ms", "2000")
            self.assertEqual(0, rc)
            self.assertIn("politica\tnao", out)

    def test_politica_nula_e_teto_diferente(self):
        with tempfile.TemporaryDirectory() as d:
            c = self.base()
            c["politica"] = None
            rc, _, _ = rodar("controlador", "--arquivo", str(escrever(d, "c.json", c)), "--hotrod-timeout-ms", "2000",
                             "--exigir-politica")
            self.assertEqual(5, rc)
            rc, _, err = rodar("controlador", "--arquivo", str(escrever(d, "d.json", self.base(teto=1500))),
                               "--hotrod-timeout-ms", "2000", "--exigir-politica")
            self.assertEqual(5, rc)
            self.assertIn("tetoLagMs", err)


class Meta(unittest.TestCase):

    def test_braco_b_valido(self):
        with tempfile.TemporaryDirectory() as d:
            t = trilha(d)
            rc, _, err = rodar("meta", "--decisoes", str(t), "--braco", "B", "--param-sha256", SHA)
            self.assertEqual(0, rc, err)

    def test_braco_b_que_nao_atua(self):
        casos = {"atuador nulo": meta(atuador="nulo"), "politica nenhuma": meta(politica="nenhuma"),
                 "sem bloco politica": meta(pp=False), "sha diferente do A": meta(sha="b" * 64),
                 "modo shadow no B": meta(modo="shadow"), "esquema velho": meta(esquema="decisoes-controlador/1.1")}
        for nome, m in casos.items():
            with self.subTest(caso=nome), tempfile.TemporaryDirectory() as d:
                t = escrever(d, "dec.jsonl", json.dumps(m) + "\n")
                rc, _, err = rodar("meta", "--decisoes", str(t), "--braco", "B", "--param-sha256", SHA)
                self.assertEqual(5, rc, nome)

    def test_braco_a(self):
        with tempfile.TemporaryDirectory() as d:
            ok = escrever(d, "a.jsonl", json.dumps(meta(modo="shadow")) + "\n")
            self.assertEqual(0, rodar("meta", "--decisoes", str(ok), "--braco", "A", "--param-sha256", SHA)[0])
            trocado = escrever(d, "b.jsonl", json.dumps(meta(modo="active")) + "\n")
            self.assertEqual(5, rodar("meta", "--decisoes", str(trocado), "--braco", "A", "--param-sha256", SHA)[0])
            outro = escrever(d, "c.jsonl", json.dumps(meta(modo="shadow", sha="c" * 64)) + "\n")
            self.assertEqual(5, rodar("meta", "--decisoes", str(outro), "--braco", "A", "--param-sha256", SHA)[0])

    def test_ausente_ou_ilegivel(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(4, rodar("meta", "--decisoes", str(Path(d) / "nao.jsonl"), "--braco", "A",
                                      "--param-sha256", SHA)[0])
            ruim = escrever(d, "r.jsonl", "{meio")
            self.assertEqual(4, rodar("meta", "--decisoes", str(ruim), "--braco", "A", "--param-sha256", SHA)[0])
            nao_meta = escrever(d, "n.jsonl", json.dumps({"tipo": "decisao"}) + "\n")
            self.assertEqual(4, rodar("meta", "--decisoes", str(nao_meta), "--braco", "A", "--param-sha256", SHA)[0])


class P2ForaDoBracoB(unittest.TestCase):
    """D-B do coordenador (05/11; contrato JSONL 1.2): a P2 (AC-2) e exploratoria e fica fora do braco B. Na calibracao
    e na avaliacao (todas as fases do orquestrador) o bloco politica tem p2Habilitada = false e a meta das decisoes,
    parametros_politica.p2_habilitada = false; qualquer outro valor (true, ausente, nao booleano) e recusa."""

    def ctrl(self, d, p2="ausente", politica=True):
        c = {"versao": "t", "wNs": 5000000000}
        if politica:
            c["politica"] = {"kLag": 2, "tetoLagMs": 2000, "tRebNs": 60000000000, "cotaAc5": 1, "origem": {}}
            if p2 != "ausente":
                c["politica"]["p2Habilitada"] = p2
        return escrever(d, "c.json", c)

    def test_controlador(self):
        casos = {"false": (False, 0), "true": (True, 5), "ausente": ("ausente", 5), "texto": ("false", 5)}
        for nome, (p2, esperado) in casos.items():
            with self.subTest(caso=nome), tempfile.TemporaryDirectory() as d:
                rc, out, err = rodar("controlador", "--arquivo", str(self.ctrl(d, p2)), "--hotrod-timeout-ms", "2000",
                                     "--exigir-p2-desabilitada")
                self.assertEqual(esperado, rc, err)
                if esperado == 5:
                    self.assertIn("p2Habilitada", err)
                else:
                    self.assertIn("p2_habilitada\tfalse", out)

    def test_controlador_sem_bloco(self):
        with tempfile.TemporaryDirectory() as d:
            rc, _, err = rodar("controlador", "--arquivo", str(self.ctrl(d, politica=False)), "--hotrod-timeout-ms",
                               "2000", "--exigir-p2-desabilitada")
            self.assertEqual(5, rc)

    def test_sem_a_opcao_nao_exige(self):
        with tempfile.TemporaryDirectory() as d:
            rc, out, _ = rodar("controlador", "--arquivo", str(self.ctrl(d, True)), "--hotrod-timeout-ms", "2000")
            self.assertEqual(0, rc)
            self.assertIn("p2_habilitada\ttrue", out)

    def test_meta_nos_dois_bracos(self):
        for braco, modo in (("A", "shadow"), ("B", "active")):
            casos = {"false": (False, 0), "true": (True, 5), "ausente": ("ausente", 5), "nulo": (None, 5)}
            for nome, (p2, esperado) in casos.items():
                with self.subTest(braco=braco, caso=nome), tempfile.TemporaryDirectory() as d:
                    t = escrever(d, "m.jsonl", json.dumps(meta(modo=modo, p2=p2)) + "\n")
                    rc, _, err = rodar("meta", "--decisoes", str(t), "--braco", braco, "--param-sha256", SHA,
                                       "--exigir-p2-desabilitada")
                    self.assertEqual(esperado, rc, (braco, nome, err))

    def test_meta_sem_parametros_politica(self):
        with tempfile.TemporaryDirectory() as d:
            t = escrever(d, "m.jsonl", json.dumps(meta(modo="shadow", pp=False)) + "\n")
            self.assertEqual(5, rodar("meta", "--decisoes", str(t), "--braco", "A", "--param-sha256", SHA,
                                      "--exigir-p2-desabilitada")[0])


class Trilha(unittest.TestCase):

    def test_consistente(self):
        with tempfile.TemporaryDirectory() as d:
            t = trilha(d, acao("AC-2", "APLICADA", None), acao("AC-5", "APLICADA", "127.0.0.1:11224"))
            rc, out, err = rodar("trilha", "--decisoes", str(t), "--sinal-alvo", "127.0.0.1:11224")
            self.assertEqual(0, rc, err)
            self.assertIn("trilha_ac5\t127.0.0.1:11224", out)
            t2 = trilha(d, acao("AC-5", "PREVISTA", "127.0.0.1:11224", executada=False))
            self.assertEqual(0, rodar("trilha", "--decisoes", str(t2), "--sinal-alvo", "-")[0])

    def test_inconsistente(self):
        with tempfile.TemporaryDirectory() as d:
            t = trilha(d, acao("AC-5", "APLICADA", "127.0.0.1:11224"))
            self.assertEqual(5, rodar("trilha", "--decisoes", str(t), "--sinal-alvo", "-")[0])
            self.assertEqual(5, rodar("trilha", "--decisoes", str(t), "--sinal-alvo", "127.0.0.1:11223")[0])
            t2 = trilha(d, acao("AC-5", "FALHOU", "127.0.0.1:11224"))
            self.assertEqual(5, rodar("trilha", "--decisoes", str(t2), "--sinal-alvo", "127.0.0.1:11224")[0])

    def test_ac5_falhou_executada_conta(self):
        """O8 da V-W2a: um AC-5 FALHOU executado pode ter parado o no; a contagem vai ao orquestrador."""
        with tempfile.TemporaryDirectory() as d:
            t = trilha(d, acao("AC-5", "FALHOU", "127.0.0.1:11224"))
            rc, out, _ = rodar("trilha", "--decisoes", str(t), "--sinal-alvo", "-")
            self.assertEqual(0, rc)
            self.assertIn("ac5_falhou_executada\t1", out)
            self.assertIn("ac5_falhou_alvos\t127.0.0.1:11224", out)

    def test_ultima_linha_truncada(self):
        with tempfile.TemporaryDirectory() as d:
            p = escrever(d, "t.jsonl", json.dumps(meta()) + "\n" + json.dumps(acao("AC-5", "APLICADA", "h:1")) + "\n{tru")
            rc, out, _ = rodar("trilha", "--decisoes", str(p), "--sinal-alvo", "h:1")
            self.assertEqual(0, rc)
            self.assertIn("trilha_completa\tnao", out)


class ParametrosF3(unittest.TestCase):
    """W-6c: F3 na avaliacao (celula ASYNC F3 descritiva do B-40). Esquema campanha-s3/2 com os cinco numeros do F3,
    cada um com origem; campanha-s3/1 continua aceito (a calibracao nao roda F3, B-2 do B-40)."""

    def test_v2_valido_imprime_os_do_f3(self):
        with tempfile.TemporaryDirectory() as d:
            rc, out, err = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", parametros_v2())))
            self.assertEqual(0, rc, err)
            linhas = dict(l.split("\t") for l in out.strip().split("\n"))
            self.assertEqual(("isn2", "20", "13", "normal", "251"),
                             tuple(linhas[k] for k in ("f3_no", "f3_delay_ms", "f3_jitter_ms", "f3_distribuicao",
                                                       "f3_dur_s")))
            self.assertEqual(set(cc.PARAMETROS) | set(cc.PARAMETROS_F3), set(linhas))

    def test_v1_continua_valido_e_sem_f3(self):
        with tempfile.TemporaryDirectory() as d:
            rc, out, err = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", parametros_validos())))
            self.assertEqual(0, rc, err)
            linhas = dict(l.split("\t") for l in out.strip().split("\n"))
            self.assertEqual(set(cc.PARAMETROS), set(linhas))

    def test_os_do_rebaseline_acabam_no_fim_da_carga(self):
        """+49 s da ancora por 251 s = +300 s, o fim da carga de 300 s (DT-011): no limite, aceito."""
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(0, rodar("parametros", *EXTRA, "--arquivo",
                                      str(escrever(d, "p.json", parametros_v2())))[0])
            rc, _, err = rodar("parametros", *EXTRA, "--arquivo",
                               str(escrever(d, "p.json", parametros_v2(f3_dur_s=252))))
            self.assertEqual(2, rc)
            self.assertIn("F3 acaba depois do fim da carga", err)

    def test_recusas(self):
        def v1_com_f3():
            obj = parametros_validos()
            obj["parametros"]["f3_dur_s"] = {"valor": 251, "origem": "x"}
            return obj

        def v2_sem(k):
            obj = parametros_v2()
            obj["parametros"].pop(k)
            return obj

        casos = {"v1 com f3": v1_com_f3,
                 "v2 sem f3_dur_s": lambda: v2_sem("f3_dur_s"),
                 "v2 sem f3_no": lambda: v2_sem("f3_no"),
                 "no invalido": lambda: parametros_v2(f3_no="isn4"),
                 "distribuicao fora do injetor": lambda: parametros_v2(f3_distribuicao="uniforme"),
                 "delay zero": lambda: parametros_v2(f3_delay_ms=0),
                 "jitter negativo": lambda: parametros_v2(f3_jitter_ms=-1),
                 "delay texto": lambda: parametros_v2(f3_delay_ms="20ms"),
                 "delay booleano": lambda: parametros_v2(f3_delay_ms=True),
                 "duracao zero": lambda: parametros_v2(f3_dur_s=0),
                 "esquema 3": lambda: dict(parametros_v2(), esquema="campanha-s3/3")}
        for nome, fazer in casos.items():
            with self.subTest(caso=nome), tempfile.TemporaryDirectory() as d:
                rc, _, err = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", fazer())))
                self.assertEqual(2, rc, nome)

    def test_f3_sem_origem_recusa(self):
        with tempfile.TemporaryDirectory() as d:
            obj = parametros_v2()
            obj["parametros"]["f3_jitter_ms"]["origem"] = " "
            rc, _, err = rodar("parametros", *EXTRA, "--arquivo", str(escrever(d, "p.json", obj)))
            self.assertEqual(2, rc)
            self.assertIn("f3_jitter_ms", err)

    def test_jitter_zero_aceito(self):
        """tc netem aceita 'delay 20ms 0ms'; o injetor aceita '0ms' (TEMPO_RE)."""
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(0, rodar("parametros", *EXTRA, "--arquivo",
                                      str(escrever(d, "p.json", parametros_v2(f3_jitter_ms=0))))[0])


class TrilhaCompleta(unittest.TestCase):
    """W-6c, item 3 (B-7 do B-40): a trilha de decisoes so e completa com a linha fim e linhas_perdidas = 0 nela
    (contrato JSONL 1.2: linhas_perdidas no fim de decisoes desde a v1.1; sem fim = execucao interrompida). A
    conferencia so marca (exit 0); quem decide o veredito e o orquestrador."""

    def saida(self, t, alvo="-"):
        rc, out, err = rodar("trilha", "--decisoes", str(t), "--sinal-alvo", alvo)
        return rc, dict(l.split("\t", 1) for l in out.strip().split("\n")), err

    def test_completa(self):
        with tempfile.TemporaryDirectory() as d:
            rc, o, err = self.saida(trilha(d))
            self.assertEqual(0, rc, err)
            self.assertEqual(("sim", "0", "nao", "-"),
                             (o["fim"], o["linhas_perdidas"], o["trilha_incompleta"], o["trilha_incompleta_motivo"]))

    def test_linhas_perdidas(self):
        with tempfile.TemporaryDirectory() as d:
            rc, o, _ = self.saida(trilha(d, perdidas=3))
            self.assertEqual(0, rc, "so marca; a decisao e do orquestrador")
            self.assertEqual(("sim", "3", "sim"), (o["fim"], o["linhas_perdidas"], o["trilha_incompleta"]))
            self.assertIn("linhas_perdidas = 3", o["trilha_incompleta_motivo"])

    def test_sem_fim(self):
        with tempfile.TemporaryDirectory() as d:
            rc, o, _ = self.saida(trilha(d, fim=False))
            self.assertEqual(0, rc)
            self.assertEqual(("nao", "-", "sim"), (o["fim"], o["linhas_perdidas"], o["trilha_incompleta"]))
            self.assertIn("sem a linha fim", o["trilha_incompleta_motivo"])

    def test_ultima_linha_truncada_sem_fim(self):
        with tempfile.TemporaryDirectory() as d:
            p = escrever(d, "t.jsonl", json.dumps(meta()) + "\n" + json.dumps(acao("AC-5", "APLICADA", "h:1"))
                         + "\n{tru")
            rc, o, _ = self.saida(p, "h:1")
            self.assertEqual(0, rc)
            self.assertEqual(("nao", "sim"), (o["fim"], o["trilha_incompleta"]))

    def test_fim_sem_o_campo(self):
        """Fim sem linhas_perdidas (trilha anterior a v1.1): nao da para afirmar que nada se perdeu."""
        with tempfile.TemporaryDirectory() as d:
            rc, o, _ = self.saida(trilha(d, perdidas="sem-campo"))
            self.assertEqual(0, rc)
            self.assertEqual(("sim", "-", "sim"), (o["fim"], o["linhas_perdidas"], o["trilha_incompleta"]))
            self.assertIn("sem linhas_perdidas", o["trilha_incompleta_motivo"])

    def test_campo_invalido(self):
        for ruim in (None, "0", -1, 1.0, True):
            with self.subTest(ruim=ruim), tempfile.TemporaryDirectory() as d:
                rc, o, _ = self.saida(trilha(d, perdidas=ruim))
                self.assertEqual(0, rc)
                self.assertEqual("sim", o["trilha_incompleta"], ruim)

    def test_tambem_sai_na_recusa_do_ac5(self):
        """Com a trilha e a linha de fim discordando (exit 5), as linhas da completude ja foram impressas."""
        with tempfile.TemporaryDirectory() as d:
            rc, o, _ = self.saida(trilha(d, acao("AC-5", "APLICADA", "127.0.0.1:11224"), perdidas=2), "-")
            self.assertEqual(5, rc)
            self.assertEqual("sim", o["trilha_incompleta"])


if __name__ == "__main__":
    unittest.main()
