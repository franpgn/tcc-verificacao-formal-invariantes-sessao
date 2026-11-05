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


def rodar(*args):
    r = subprocess.run([sys.executable, "-I", str(AQUI / "campanha_s3_conferir.py")] + list(args),
                       capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def parametros_validos():
    v = {"duracao_s": 300, "warmup_min_s": 30, "inj_offset_s": 49, "f1_no": "isn2", "f1_dur_s": 60,
         "f2_no": "isn3", "f2_dur_s": 90, "f4_no": "isn3", "f4_severidade": "N3", "f4_direcao": "both",
         "f4_dur_s": 90, "hotrod_timeout_ms": 2000, "convergencia_teto_s": 600, "convergencia_periodo_s": 5,
         "w_recovery_s": 161, "religamento_prazo_s": 180, "exec_teto_s": 1200, "encerramento_folga_s": 2,
         "volume_exec_mb": 200}
    return {"esquema": "campanha-s3/1", "versao": "teste",
            "parametros": {k: {"valor": x, "origem": "teste"} for k, x in v.items()}}


def escrever(d, nome, obj):
    p = Path(d) / nome
    p.write_text(obj if isinstance(obj, str) else json.dumps(obj), encoding="utf-8")
    return p


def meta(modo="active", atuador="por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)", politica="por-assinatura",
         pp=True, sha=SHA, esquema="decisoes-controlador/1.2"):
    return {"tipo": "meta", "esquema": esquema, "modo": modo, "param_sha256": sha, "atuador": atuador,
            "politica": politica,
            "parametros_politica": {"k_lag": 2, "teto_lag_ms": 2000, "t_reb_ns": 60000000000, "cota_ac5": 1,
                                    "origem": {}} if pp else None}


def acao(tipo, resultado, alvo, executada=True, operacao="APLICAR"):
    return {"tipo": "acao", "operacao": operacao, "executada": executada, "modo": "active", "atuador": "x",
            "acao": {"tipo": tipo, "caches": ["sessions-async", "counters-async"], "alvo": alvo, "valor": None},
            "resultado": resultado}


def trilha(d, *linhas, fim=True):
    corpo = [meta()] + list(linhas) + ([{"tipo": "fim"}] if fim else [])
    return escrever(d, "decisoes-rep-001.jsonl", "".join(json.dumps(x) + "\n" for x in corpo))


class Parametros(unittest.TestCase):

    def test_validos_imprimem_nome_valor(self):
        with tempfile.TemporaryDirectory() as d:
            p = escrever(d, "p.json", parametros_validos())
            rc, out, err = rodar("parametros", "--arquivo", str(p))
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
                rc, _, err = rodar("parametros", "--arquivo", str(escrever(d, "p.json", obj)))
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
                rc, _, err = rodar("parametros", "--arquivo", str(escrever(d, "p.json", obj)))
                self.assertEqual(2, rc, nome)

    def test_coerencia_das_janelas(self):
        """A falha tem de acabar antes do fim da carga, e o teto da execucao cobre carga + religamento + convergencia."""
        casos = {"f2 passa do fim": ("f2_dur_s", 300), "f1 passa do fim": ("f1_dur_s", 260),
                 "teto curto": ("exec_teto_s", 1000)}
        for nome, (k, x) in casos.items():
            with self.subTest(caso=nome), tempfile.TemporaryDirectory() as d:
                obj = parametros_validos()
                obj["parametros"][k]["valor"] = x
                rc, _, err = rodar("parametros", "--arquivo", str(escrever(d, "p.json", obj)))
                self.assertEqual(2, rc, err)

    def test_json_invalido(self):
        with tempfile.TemporaryDirectory() as d:
            rc, _, _ = rodar("parametros", "--arquivo", str(escrever(d, "p.json", "{nao e json")))
            self.assertEqual(2, rc)


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


if __name__ == "__main__":
    unittest.main()
