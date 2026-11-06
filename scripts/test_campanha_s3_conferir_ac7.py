"""Testes do conferidor do orquestrador S3 para o AC-7 (P1-IC; decisoes V-2 a V-4 do coordenador, 06/11).

- Trilha 1.3: o esquema da trilha de decisoes passa a decisoes-controlador/1.3 (V-2: a mudanca da acao do P1 fica
  explicita); a meta 1.2 e recusada.
- acaoP1 explicito (V-3): na avaliacao, o --controller-config traz "acaoP1": "AC-7" no bloco politica; com
  --exigir-acao-p1 AC-7, o subcomando controlador recusa o bloco sem o campo ou com outro valor, e o subcomando meta
  recusa parametros_politica.acao_p1 diferente (nos dois bracos: o arquivo e o mesmo no bloco).
- trilha: alem do AC-5 (que continua, para a comparacao), imprime trilha_ac7 (alvo do AC-7 APLICADA executado),
  ac7_falhou_executada, ac7_falhou_erros, ac7_desfazer (origem:resultado de cada reversao executada do AC-7, na ordem)
  e ac7_ativa_no_fim (sim se uma aplicacao executada, APLICADA ou FALHOU, ficou sem reversao executada com sucesso
  depois dela: o alvo pode ter terminado a carga ignorando o cache de sessoes, o que vicia a varredura final). O exit
  nao muda por causa do AC-7: o veredito e do orquestrador.

Uso: python -I scripts/test_campanha_s3_conferir_ac7.py
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import campanha_s3_conferir as cc  # noqa: E402
from test_campanha_s3_conferir import SHA, escrever, rodar  # noqa: E402

ESQ = "decisoes-controlador/1.3"
ATU = "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan,AC-7=rest-ignored-caches)"
ALVO = "127.0.0.1:11224"


def meta(modo="active", acao_p1="AC-7", esquema=ESQ, p2=False):
    pol = {"k_lag": 2, "teto_lag_ms": 2000, "t_reb_ns": 60000000000, "cota_ac5": 1, "p2_habilitada": p2,
           "origem": {}}
    if acao_p1 is not None:
        pol["acao_p1"] = acao_p1
    return {"tipo": "meta", "esquema": esquema, "modo": modo, "param_sha256": SHA, "atuador": ATU,
            "politica": "por-assinatura", "parametros_politica": pol}


def acao(op, res, origem="POLITICA", executada=True, tipo="AC-7", alvo=ALVO, erro=None, modo="active"):
    return {"tipo": "acao", "operacao": op, "executada": executada, "modo": modo, "atuador": ATU,
            "acao": {"tipo": tipo, "caches": ["sessions-async"], "alvo": alvo, "valor": None},
            "origem": origem, "resultado": res, "erro": erro}


def trilha(d, *linhas, m=None):
    corpo = [m or meta()] + list(linhas) + [{"tipo": "fim", "linhas_perdidas": 0}]
    return escrever(d, "decisoes-rep-001.jsonl", "".join(json.dumps(x) + "\n" for x in corpo))


def campos(out):
    return dict(l.split("\t", 1) for l in out.strip().split("\n"))


def controlador(d, acao_p1="AC-7", origem=True):
    pol = {"kLag": 2, "tetoLagMs": 2000, "tRebNs": 60000000000, "cotaAc5": 1, "p2Habilitada": False,
           "origem": {"kLag": "t", "tetoLagMs": "t", "tRebNs": "t", "cotaAc5": "t", "p2Habilitada": "t"}}
    if acao_p1 is not None:
        pol["acaoP1"] = acao_p1
        if origem:
            pol["origem"]["acaoP1"] = "G-1 do coordenador (06/11): o AC-7 substitui a parada no P1"
    return escrever(d, "controlador.json", {"versao": "t", "wNs": 5000000000, "politica": pol})


class EsquemaDaTrilha(unittest.TestCase):

    def test_constante(self):
        self.assertEqual(ESQ, cc.ESQUEMA_DECISOES)

    def test_meta_13_aceita_e_12_recusada(self):
        with tempfile.TemporaryDirectory() as d:
            p = trilha(d)
            rc, out, err = rodar("meta", "--decisoes", str(p), "--braco", "B", "--param-sha256", SHA)
            self.assertEqual(0, rc, out + err)
            p = trilha(d, m=meta(esquema="decisoes-controlador/1.2"))
            rc, out, err = rodar("meta", "--decisoes", str(p), "--braco", "B", "--param-sha256", SHA)
            self.assertEqual(5, rc)
            self.assertIn("decisoes-controlador/1.3", out + err)


class AcaoP1Explicita(unittest.TestCase):

    def test_controlador_exige_ac7_explicito(self):
        with tempfile.TemporaryDirectory() as d:
            rc, out, err = rodar("controlador", "--arquivo", str(controlador(d)), "--hotrod-timeout-ms", "2000",
                                 "--exigir-politica", "--exigir-acao-p1", "AC-7")
            self.assertEqual(0, rc, out + err)
            self.assertEqual("AC-7", campos(out)["acao_p1"])
            for nome, arq, motivo in (("sem o campo", controlador(d, acao_p1=None), "acaoP1"),
                                      ("AC-5", controlador(d, acao_p1="AC-5"), "AC-5"),
                                      ("sem origem", controlador(d, origem=False), "origem")):
                with self.subTest(caso=nome):
                    rc, out, err = rodar("controlador", "--arquivo", str(arq), "--hotrod-timeout-ms", "2000",
                                         "--exigir-politica", "--exigir-acao-p1", "AC-7")
                    self.assertEqual(5, rc, out + err)
                    self.assertIn(motivo, out + err)

    def test_controlador_sem_a_exigencia_imprime_o_campo(self):
        with tempfile.TemporaryDirectory() as d:
            rc, out, err = rodar("controlador", "--arquivo", str(controlador(d, acao_p1=None)), "--hotrod-timeout-ms",
                                 "2000")
            self.assertEqual(0, rc, out + err)
            self.assertEqual("-", campos(out)["acao_p1"], "sem o campo no arquivo: o programa usa o padrao (AC-7)")

    def test_meta_exige_acao_p1_nos_dois_bracos(self):
        with tempfile.TemporaryDirectory() as d:
            for braco, modo in (("A", "shadow"), ("B", "active")):
                with self.subTest(braco=braco):
                    ok = trilha(d, m=meta(modo=modo))
                    rc, out, err = rodar("meta", "--decisoes", str(ok), "--braco", braco, "--param-sha256", SHA,
                                         "--exigir-acao-p1", "AC-7")
                    self.assertEqual(0, rc, out + err)
                    self.assertEqual("AC-7", campos(out)["acao_p1"])
                    for valor in ("AC-5", None):
                        ruim = trilha(d, m=meta(modo=modo, acao_p1=valor))
                        rc, out, err = rodar("meta", "--decisoes", str(ruim), "--braco", braco, "--param-sha256",
                                             SHA, "--exigir-acao-p1", "AC-7")
                        self.assertEqual(5, rc, "acao_p1 = %r" % valor)
                        self.assertIn("acao_p1", out + err)


class TrilhaDoAc7(unittest.TestCase):

    def conferir(self, *linhas, m=None):
        with tempfile.TemporaryDirectory() as d:
            p = trilha(d, *linhas, m=m)
            rc, out, err = rodar("trilha", "--decisoes", str(p), "--sinal-alvo", "-")
            self.assertEqual(0, rc, out + err)
            return campos(out)

    def test_corte_desfeito_pela_politica(self):
        c = self.conferir(acao("APLICAR", "APLICADA"), acao("REVERTER", "APLICADA"))
        self.assertEqual(ALVO, c["trilha_ac7"])
        self.assertEqual("nao", c["ac7_ativa_no_fim"])
        self.assertEqual("POLITICA:APLICADA", c["ac7_desfazer"])
        self.assertEqual("0", c["ac7_falhou_executada"])
        self.assertEqual("-", c["trilha_ac5"], "o AC-5 continua conferido, e nao houve")

    def test_guarda_e_encerramento_contam_como_desfazer(self):
        c = self.conferir(acao("APLICAR", "APLICADA"), acao("REVERTER", "APLICADA", origem="GUARDA"))
        self.assertEqual("GUARDA:APLICADA", c["ac7_desfazer"])
        self.assertEqual("nao", c["ac7_ativa_no_fim"])
        c = self.conferir(acao("APLICAR", "APLICADA"), acao("REVERTER", "APLICADA", origem="ENCERRAMENTO"))
        self.assertEqual("nao", c["ac7_ativa_no_fim"])

    def test_sem_reversao_ou_reversao_que_falhou_fica_ativa(self):
        self.assertEqual("sim", self.conferir(acao("APLICAR", "APLICADA"))["ac7_ativa_no_fim"])
        c = self.conferir(acao("APLICAR", "APLICADA"), acao("REVERTER", "FALHOU", origem="ENCERRAMENTO",
                                                            erro="desfazer-nao-confirmado"))
        self.assertEqual("sim", c["ac7_ativa_no_fim"])
        self.assertEqual("ENCERRAMENTO:FALHOU", c["ac7_desfazer"])

    def test_falso_alarme_desfeito(self):
        c = self.conferir(acao("APLICAR", "FALHOU", erro="falso-alarme-desfeito"),
                          acao("REVERTER", "APLICADA", origem="FALHA_NA_APLICACAO"))
        self.assertEqual("-", c["trilha_ac7"])
        self.assertEqual("1", c["ac7_falhou_executada"])
        self.assertEqual("falso-alarme-desfeito", c["ac7_falhou_erros"])
        self.assertEqual("nao", c["ac7_ativa_no_fim"], "o FALHOU executado foi revertido em seguida")
        c = self.conferir(acao("APLICAR", "FALHOU", erro="falso-alarme-nao-desfeito:x"))
        self.assertEqual("sim", c["ac7_ativa_no_fim"], "FALHOU executado sem reversao: o alvo pode estar ignorando")

    def test_shadow_e_nao_executada_nao_contam(self):
        c = self.conferir(acao("APLICAR", "PREVISTA", executada=False, modo="shadow"),
                          m=meta(modo="shadow"))
        self.assertEqual("-", c["trilha_ac7"])
        self.assertEqual("nao", c["ac7_ativa_no_fim"])
        self.assertEqual("-", c["ac7_desfazer"])
        c = self.conferir(acao("APLICAR", "FALHOU", executada=False, erro="alvo-sem-resposta-na-ultima-janela"))
        self.assertEqual("0", c["ac7_falhou_executada"])
        self.assertEqual("nao", c["ac7_ativa_no_fim"])


if __name__ == "__main__":
    unittest.main()
