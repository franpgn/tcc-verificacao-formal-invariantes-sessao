"""Testes do comparar_bracos.py para o AC-7 (P1-IC; decisoes V-2 e V-4 do coordenador, 06/11).

A acao do P1 passou da parada (AC-5) para o AC-7 (o no suspeito ignora o cache de sessoes; reversivel), com tres
motivos de desfazer da politica (desfazer-alvo-sozinho, desfazer-apos-fusao, suspeita-desfeita) e a guarda continua
(origem GUARDA, sem decisao: ela le a lista dos outros nos, nao uma janela). A trilha que traz isso e a
decisoes-controlador/1.3. Aqui:

- descritivos: resumo do AC-7 por execucao (aplicada, alvo, efeito, desfazeres com origem e motivo, ativa no fim) ao
  lado do ac5_aplicada, que fica para a comparacao;
- rastreabilidade (a mesma funcao que as guardas dos bracos ativos do fatorial, PL e AL, usam): cada APLICAR do AC-7 da
  politica tem a linha politica P1 da mesma janela; cada REVERTER da politica tem a linha politica com um motivo de
  desfazer; GUARDA so reverte um AC-7 aplicado antes; AC-7 e GUARDA so em trilha 1.3; usadas_p1 nunca passa da cota;
  AC-7 executado tem reversao executada com sucesso ate o fim (senao o alvo terminou a carga ignorando o cache).

Uso: python -m unittest discover -s analysis -p "test_comparar_bracos_ac7.py"   (da raiz do repositorio)
"""
import sys
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
from test_desfechos_coprincipais import (INI, S, T0, Base, acao, dec, escrever_exec,  # noqa: E402
                                         eventos_ok, trilha)

ESQ13 = "decisoes-controlador/1.3"
ALVO = "127.0.0.1:11224"
P = S // 10


def pol(t, politica, motivo, aplicar=(), reverter=(), no=ALVO, usadas=1):
    return {"tipo": "politica", "t_fim_janela_ns": t, "t_decisao_ns": t + S // 10, "modo": "active",
            "perfil": "CONSERVADOR", "regra": "R3", "politica": politica, "motivo": motivo, "no": no,
            "reverter": list(reverter), "aplicar": list(aplicar), "usadas_p1": usadas}


def ac7(op, res, t, origem="POLITICA", regra="R3", janela=None, lido=None):
    a = acao(op, "AC-7", res, t, origem=origem, regra=regra, t_fim_janela=janela, t_inicio=t, duracao=P,
             caches=("sessions-async",), alvo=ALVO)
    a["lido"] = lido
    return a


def t13(meio, cota=1, **kw):
    linhas = trilha("active", meio, **kw)
    linhas[0]["esquema"] = ESQ13
    linhas[0]["parametros_politica"] = {"k_lag": 2, "cota_ac5": cota, "p2_habilitada": False, "acao_p1": "AC-7"}
    return linhas


def cenario_corte():
    """P1 na janela de +15 s, desfazer-alvo-sozinho na de +50 s (como o corte-r1/corte-r2 do smoke)."""
    j1, j2 = T0 + 15 * S, T0 + 50 * S
    return [dec(j1, "CONSERVADOR", True, "R2"),
            pol(j1, "P1", "isolar-suspeito", aplicar=["AC-7"]),
            ac7("APLICAR", "APLICADA", j1 + P, regra="R2", janela=j1,
                lido="efeito_ms=35;guarda=limpa_43x_1010ms;post=pendente"),
            dec(j2, "CONSERVADOR", False, "R3"),
            pol(j2, "P0", "desfazer-alvo-sozinho", reverter=["AC-7"]),
            ac7("REVERTER", "APLICADA", j2 + P, janela=j2, lido="rota=alvo-sozinho"),
            dec(T0 + 55 * S, "CONSERVADOR")]


class Ac7(Base):

    def ler(self, linhas):
        escrever_exec(self.d, eventos=eventos_ok(5), trilha=linhas)
        return cb.ler_trilha_decisoes(self.d / "decisoes-rep-001.jsonl")

    # ------------------------------------------------------------------ descritivos

    def test_resumo_do_ac7(self):
        x = cb.descrever_controlador(self.ler(t13(cenario_corte())), INI, T0 + 60 * S)
        r = x["ac7"]
        self.assertTrue(r["aplicada"])
        self.assertEqual(ALVO, r["alvo"])
        self.assertEqual(35, r["efeito_ms"])
        self.assertEqual([{"origem": "POLITICA", "motivo": "desfazer-alvo-sozinho", "resultado": "APLICADA"}],
                         r["desfazer"])
        self.assertFalse(r["ativa_no_fim"])
        self.assertEqual(0, r["falhou_executada"])
        self.assertFalse(x["ac5_aplicada"], "a parada nao aconteceu")
        self.assertAlmostEqual(35.0, x["acoes_por_tipo"]["AC-7"]["tempo_ativo_s"], places=6)

    def test_guarda_e_falso_alarme(self):
        j1 = T0 + 15 * S
        meio = [dec(j1, "CONSERVADOR", True, "R2"), pol(j1, "P1", "isolar-suspeito", aplicar=["AC-7"]),
                ac7("APLICAR", "APLICADA", j1 + P, regra="R2", janela=j1),
                ac7("REVERTER", "APLICADA", T0 + 34 * S, origem="GUARDA", regra=None, janela=None)]
        r = cb.descrever_controlador(self.ler(t13(meio)), INI, T0 + 60 * S)["ac7"]
        self.assertEqual([{"origem": "GUARDA", "motivo": None, "resultado": "APLICADA"}], r["desfazer"])
        self.assertFalse(r["ativa_no_fim"])
        meio = [dec(j1, "CONSERVADOR", True, "R2"), pol(j1, "P1", "isolar-suspeito", aplicar=["AC-7"]),
                ac7("APLICAR", "FALHOU", j1 + P, regra="R2", janela=j1),
                ac7("REVERTER", "APLICADA", j1 + 2 * P, origem="FALHA_NA_APLICACAO", regra="R2", janela=j1)]
        r = cb.descrever_controlador(self.ler(t13(meio)), INI, T0 + 60 * S)["ac7"]
        self.assertFalse(r["aplicada"])
        self.assertEqual(1, r["falhou_executada"])
        self.assertFalse(r["ativa_no_fim"])

    def test_info_da_execucao_traz_ac7_aplicada(self):
        escrever_exec(self.d, eventos=eventos_ok(5), trilha=t13(cenario_corte()))
        _, info = self.desf(self.d)
        self.assertTrue(info["ac7_aplicada"])
        self.assertFalse(info["ac5_aplicada"])

    # ------------------------------------------------------------------ rastreabilidade

    def test_rastreabilidade_integral(self):
        r = cb.conferir_rastreabilidade(self.ler(t13(cenario_corte())))
        self.assertTrue(r["integral"], r["problemas"])

    def test_guarda_sem_decisao_e_aceita(self):
        j1 = T0 + 15 * S
        meio = [dec(j1, "CONSERVADOR", True, "R2"), pol(j1, "P1", "isolar-suspeito", aplicar=["AC-7"]),
                ac7("APLICAR", "APLICADA", j1 + P, regra="R2", janela=j1),
                ac7("REVERTER", "APLICADA", T0 + 34 * S, origem="GUARDA", regra=None, janela=None)]
        r = cb.conferir_rastreabilidade(self.ler(t13(meio)))
        self.assertTrue(r["integral"], r["problemas"])

    def test_rastreabilidade_quebrada(self):
        j1, j2 = T0 + 15 * S, T0 + 50 * S
        base = cenario_corte()
        sem_p1 = [x for x in base if not (x["tipo"] == "politica" and x["politica"] == "P1")]
        sem_motivo = [x for x in base if not (x["tipo"] == "politica" and x["motivo"].startswith("desfazer"))]
        motivo_errado = [dict(x, motivo="sem-assinatura") if x["tipo"] == "politica" and x["politica"] == "P0" else x
                         for x in base]
        guarda_aplicar = base[:3] + [ac7("APLICAR", "APLICADA", j1 + 5 * S, origem="GUARDA", regra=None)]
        guarda_sem_acao = [dec(j1), ac7("REVERTER", "APLICADA", j1 + P, origem="GUARDA", regra=None)]
        guarda_ac2 = base[:3] + [acao("REVERTER", "AC-2", "APLICADA", j2, origem="GUARDA", regra=None)]
        sem_reversao = base[:3] + [dec(j2)]
        reversao_falhou = base[:3] + [ac7("REVERTER", "FALHOU", j2, origem="ENCERRAMENTO", regra=None)]
        # (trilha, trecho que o problema da regra tem de trazer)
        casos = {
            "APLICAR sem politica P1": (t13(sem_p1), "sem linha politica P1"),
            "REVERTER sem politica de desfazer": (t13(sem_motivo), "sem linha politica de desfazer"),
            "REVERTER com motivo que nao e de desfazer": (t13(motivo_errado), "sem linha politica de desfazer"),
            "GUARDA aplicando": (t13(guarda_aplicar), "GUARDA so reverte"),
            "GUARDA sem AC-7 aplicado antes": (t13(guarda_sem_acao), "GUARDA sem AC-7 aplicado antes"),
            "GUARDA em outro tipo": (t13(guarda_ac2), "GUARDA so reverte"),
            "AC-7 ativo no fim": (t13(sem_reversao), "ativo no fim"),
            "reversao que falhou deixa ativo": (t13(reversao_falhou), "ativo no fim"),
            "usadas_p1 acima da cota": (t13([dict(x, usadas_p1=2) if x["tipo"] == "politica" else x for x in base]),
                                        "usadas_p1"),
            "AC-7 em trilha 1.2": (trilha("active", base), "decisoes-controlador/1.3"),
        }
        for nome, (linhas, trecho) in casos.items():
            with self.subTest(caso=nome):
                self.tearDown()
                self.setUp()
                r = cb.conferir_rastreabilidade(self.ler(linhas))
                self.assertFalse(r["integral"], nome)
                self.assertTrue(any(trecho in p for p in r["problemas"]), (trecho, r["problemas"]))

    def test_shadow_previsto_e_rastreavel(self):
        j1 = T0 + 15 * S
        meio = [dec(j1, "CONSERVADOR", True, "R2", modo="shadow"),
                dict(pol(j1, "P1", "isolar-suspeito", aplicar=["AC-7"]), modo="shadow"),
                dict(ac7("APLICAR", "PREVISTA", j1 + P, regra="R2", janela=j1), modo="shadow", executada=False,
                     t_inicio_ns=None, duracao_ns=None),
                dict(ac7("REVERTER", "PREVISTA", T0 + 60 * S, origem="ENCERRAMENTO", regra=None), modo="shadow",
                     executada=False, t_inicio_ns=None, duracao_ns=None)]
        linhas = t13(meio)
        linhas[0]["modo"] = "shadow"
        t = self.ler(linhas)
        self.assertTrue(cb.conferir_rastreabilidade(t)["integral"], cb.conferir_rastreabilidade(t)["problemas"])
        r = cb.descrever_controlador(t, INI, T0 + 60 * S)["ac7"]
        self.assertFalse(r["aplicada"], "PREVISTA nao e acao executada")
        self.assertFalse(r["ativa_no_fim"])

    def test_trilha_12_sem_ac7_continua_valida(self):
        # a regra da versao so pesa quando ha AC-7 ou GUARDA: trilhas antigas (calibracao) seguem legiveis
        r = cb.conferir_rastreabilidade(self.ler(trilha("active", [dec(T0 + 15 * S)])))
        self.assertTrue(r["integral"], r["problemas"])


if __name__ == "__main__":
    unittest.main()
