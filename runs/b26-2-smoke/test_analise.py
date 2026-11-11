#!/usr/bin/env python3
"""B-26.2: testes de analise.py com amostras sinteticas (python -m unittest test_analise)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analise  # noqa: E402

S = 1_000_000_000


def no(nome, motivo, respondeu=True, n=3):
    return {"no": nome, "respondeu": respondeu, "numero_de_nos": n if respondeu else -1, "motivo": motivo,
            "anomalo": motivo != "ok"}


def amostra(t, motivos):
    por_no = []
    for i, m in enumerate(motivos):
        respondeu = not (m == "timeout" or m.startswith("falha:"))
        n = 2 if m.startswith("nos:2") else 3
        por_no.append(no("n%d" % i, m, respondeu, n))
    resp = [p["numero_de_nos"] for p in por_no if p["respondeu"]]
    return {"t_epoch_ns": t, "anomala": any(p["anomalo"] for p in por_no), "nos_min": min(resp) if resp else 0,
            "por_no": por_no}


OK = ["ok", "ok", "ok"]
QUEDA = ["nos:2<3", "nos:2<3", "timeout"]


def serie(t0, passos):
    return [amostra(t0 + i * 2 * S, m) for i, m in enumerate(passos)]


def resumo(am, puladas=0):
    return {"rodadas": len(am), "puladas": puladas, "anomalas": sum(a["anomala"] for a in am)}


class Estavel(unittest.TestCase):
    def test_tudo_ok_passa(self):
        am = serie(0, [OK] * 15)
        r = analise.analisar_estavel(am, resumo(am), {"curl_lancados": 48, "rodadas": 15, "cpu_jvm_ms": 600})
        self.assertTrue(r["passou"], r)
        self.assertTrue(r["curl_3_por_rodada"])

    def test_uma_anomala_reprova(self):
        am = serie(0, [OK] * 5 + [["ok", "cache:sessions=DEGRADED", "ok"]] + [OK] * 5)
        r = analise.analisar_estavel(am, resumo(am), None)
        self.assertFalse(r["passou"])
        self.assertFalse(r["zero_anomalas"])

    def test_curl_a_mais_reprova(self):
        am = serie(0, [OK] * 10)
        r = analise.analisar_estavel(am, resumo(am), {"curl_lancados": 40, "rodadas": 10, "cpu_jvm_ms": 1})
        self.assertFalse(r["curl_3_por_rodada"])
        self.assertFalse(r["passou"])

    def test_pulada_reprova(self):
        am = serie(0, [OK] * 10)
        self.assertFalse(analise.analisar_estavel(am, resumo(am, puladas=1), None)["passou"])


class Queda(unittest.TestCase):
    T_STOP = 30 * S
    T_START = 62 * S

    def caso(self, passos, t_start_fim=None):
        am = serie(0, passos)
        return analise.analisar_queda(am, resumo(am), None, self.T_STOP, self.T_START,
                                      t_start_fim or self.T_START + S)

    def test_queda_e_volta_passam(self):
        # 0..28 ok (15), 30 ok (stop em 30, ainda sem efeito), 32..60 queda (15), 62..70 queda (5), 72.. ok
        passos = [OK] * 16 + [QUEDA] * 15 + [QUEDA] * 5 + [OK] * 10
        r = self.caso(passos)
        self.assertTrue(r["passou"], r["falhou"])
        self.assertEqual(2.0, r["deteccao_s"])
        self.assertEqual(10.0, r["volta_s_desde_start_inicio"])
        self.assertEqual(0, r["amostras_healthy_rebalancing"])

    def test_deteccao_tardia_reprova(self):
        passos = [OK] * 19 + [QUEDA] * 12 + [OK] * 10  # primeira anomalia em 38 s = stop + 8 s > 5,5 s
        r = self.caso(passos)
        self.assertIn("deteccao_no_limite", r["falhou"])

    def test_sem_volta_reprova(self):
        passos = [OK] * 16 + [QUEDA] * 30
        r = self.caso(passos)
        self.assertIn("volta_observada", r["falhou"])
        self.assertIsNone(r["volta_s_desde_start_inicio"])

    def test_no_parado_respondendo_reprova(self):
        passos = [OK] * 16 + [["nos:2<3", "nos:2<3", "nos:2<3"]] * 15 + [OK] * 10
        r = self.caso(passos)
        self.assertIn("no_parado_timeout_ou_falha", r["falhou"])

    def test_outros_sem_nos_2_reprova(self):
        passos = [OK] * 16 + [["ok", "ok", "timeout"]] * 15 + [OK] * 10
        r = self.caso(passos)
        self.assertIn("outros_nos_2_de_3", r["falhou"])

    def test_outros_sem_resposta_sao_contados_mas_nao_reprovam(self):
        passos = [OK] * 16 + [QUEDA] * 7 + [["timeout", "timeout", "falha:IOException"]] + [QUEDA] * 7 + [OK] * 10
        r = self.caso(passos)
        self.assertTrue(r["passou"], r["falhou"])
        self.assertEqual(2, r["outros_sem_resposta"])
        self.assertEqual(["timeout"], r["outros_motivos_sem_resposta"])

    def test_rebalancing_medido(self):
        reb = ["ok", "ok", "cache:sessions-async=HEALTHY_REBALANCING"]
        passos = [OK] * 16 + [QUEDA] * 16 + [reb] * 3 + [OK] * 10
        r = self.caso(passos)
        self.assertEqual(3, r["amostras_healthy_rebalancing"])
        self.assertEqual(4.0, r["healthy_rebalancing_s"])

    def test_custo_le_stderr(self):
        c = analise.ler_custo("x\ncusto curl_lancados=183 rodadas=60 cpu_jvm_ms=1500 erros_ouvinte=0\n")
        self.assertEqual({"curl_lancados": 183, "rodadas": 60, "cpu_jvm_ms": 1500}, c)


if __name__ == "__main__":
    unittest.main()
