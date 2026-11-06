"""Testes das conferencias do complemento 1 do benchmark refeito (DT-027) em scripts/campanha_s3_conferir.py.

- pareamento --matriz <tsv> (DT-027 secao 5, item 4; plano de execucao 5.2): bloco A/B (shadow x active, regra de
  fusao atual) ou bloco 2x2 (PO, PL, AO, AL), cada braco uma vez, mesma celula, mesmo papel e mesma semente;
  controlador e fusao coerentes com o braco; linha sem bloco so como braco A de calibracao. Aceita a matriz de 13
  colunas (calibracao, avaliacao do B-40; regra atual implicita) e a de 14 (coluna fusao). Recusa (5) bloco incompleto,
  braco repetido, mistura de formatos de bloco e variante desconhecida.
- parametros: fusao_teto_s (teto da espera pela fusao antes da varredura, contado do marcador end; vai ao programa como
  --sweep-merge-timeout-sec) e opcional, so com --sonda-replay (a varredura so roda com ela), inteiro > 0, com origem, e
  a espera tem de vencer antes do fim da carga: inj_offset_s + f2_dur_s + fusao_teto_s <= duracao_s (assim ela nao
  soma ao exec_teto_s mais que uma leitura).

Uso: python -I scripts/test_campanha_s3_conferir_fatorial.py
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

COL13 = ["i", "fase", "rodada", "posicao", "celula", "modo", "falha", "papel", "bloco", "braco", "controlador",
         "seed", "repoe"]
COL14 = COL13 + ["fusao"]
CTL = {"A": "shadow", "B": "active", "PO": "shadow", "PL": "active", "AO": "shadow", "AL": "active"}
FUS = {"A": "pnn", "B": "pnn", "PO": "pnn", "PL": "pnn", "AO": "a1", "AL": "a1"}


def rodar(*args):
    r = subprocess.run([sys.executable, "-I", str(AQUI / "campanha_s3_conferir.py")] + list(args),
                       capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def linha(i, bloco, braco, celula="ASYNC-F2", seed=None, papel="teste", **troca):
    modo, falha = celula.split("-")
    x = {"i": i, "fase": "nucleo", "rodada": 1, "posicao": "0.0", "celula": celula, "modo": modo.lower(),
         "falha": falha, "papel": papel, "bloco": bloco, "braco": braco, "controlador": CTL[braco],
         "seed": seed if seed is not None else (800000 if bloco == "-" else 800000 + 100 * int(bloco)),
         "repoe": "-", "fusao": FUS[braco]}
    x.update(troca)
    return x


def bloco4(b, i0, **troca_por_braco):
    return [linha(i0 + k, b, br, **troca_por_braco.get(br, {})) for k, br in enumerate(("PL", "AO", "PO", "AL"))]


def bloco2(b, i0, celula="ASYNC-none", **troca_por_braco):
    return [linha(i0 + k, b, br, celula=celula, papel="descritiva", **troca_por_braco.get(br, {}))
            for k, br in enumerate(("B", "A"))]


def gravar(d, linhas, colunas=COL14, nome="m.tsv"):
    p = Path(d) / nome
    txt = "\t".join(colunas) + "\n" + "".join("\t".join(str(x[c]) for c in colunas) + "\n" for x in linhas)
    p.write_bytes(txt.encode("ascii"))
    return p


def pareamento(d, linhas, colunas=COL14):
    return rodar("pareamento", "--matriz", str(gravar(d, linhas, colunas)))


class PareamentoAceita(unittest.TestCase):

    def test_fatorial_e_descritivas(self):
        with tempfile.TemporaryDirectory() as d:
            rc, out, err = pareamento(d, bloco4(0, 0) + bloco2(1, 4) + bloco4(2, 6))
            self.assertEqual(0, rc, err)
            self.assertIn("formato\tfatorial", out)
            self.assertIn("blocos_fatorial\t2", out)
            self.assertIn("blocos_ab\t1", out)
            self.assertIn("fusao\tpnn,a1", out)

    def test_matriz_de_13_colunas_continua_valida(self):
        with tempfile.TemporaryDirectory() as d:
            rc, out, err = pareamento(d, bloco2(1, 0) + bloco2(2, 2, celula="ASYNC-F2"), colunas=COL13)
            self.assertEqual(0, rc, err)
            self.assertIn("formato\tcampanha-s3", out)
            self.assertIn("fusao\tpnn", out)

    def test_calibracao_sem_bloco(self):
        with tempfile.TemporaryDirectory() as d:
            ls = [linha(k, "-", "A", celula="ASYNC-F2", papel="calibracao", seed=700000 + 100 * k) for k in range(3)]
            rc, out, err = pareamento(d, ls, colunas=COL13)
            self.assertEqual(0, rc, err)
            self.assertIn("sem_bloco\t3", out)

    def test_cabecalho_desconhecido_e_uso(self):
        with tempfile.TemporaryDirectory() as d:
            rc, _, err = pareamento(d, bloco4(0, 0), colunas=COL14[:-2] + ["fusao"])
            self.assertEqual(2, rc, err)
            self.assertEqual(4, rodar("pareamento", "--matriz", str(Path(d) / "nao-existe.tsv"))[0])


class PareamentoRecusa(unittest.TestCase):

    def recusa(self, linhas, trecho, colunas=COL14):
        with tempfile.TemporaryDirectory() as d:
            rc, _, err = pareamento(d, linhas, colunas)
            self.assertEqual(5, rc, err)
            self.assertIn("pareamento", err)
            self.assertIn(trecho, err)

    def test_bloco_fatorial_incompleto(self):
        self.recusa(bloco4(0, 0)[:3], "bloco 0")

    def test_bloco_ab_incompleto(self):
        self.recusa(bloco2(3, 0)[:1], "bloco 3")

    def test_braco_repetido(self):
        b = bloco4(0, 0)
        b[3] = linha(3, 0, "PO")
        self.recusa(b, "bloco 0")

    def test_mistura_de_formatos_no_bloco(self):
        self.recusa(bloco4(0, 0)[:2] + [linha(2, 0, "A"), linha(3, 0, "B")], "bloco 0")

    def test_semente_diferente(self):
        self.recusa(bloco4(0, 0, AL={"seed": 800001}), "semente")

    def test_celula_diferente(self):
        self.recusa(bloco4(0, 0, AO={"celula": "ASYNC-F4", "falha": "F4"}), "celula")

    def test_papel_diferente(self):
        self.recusa(bloco4(0, 0, PL={"papel": "descritiva"}), "papel")

    def test_controlador_incoerente_com_o_braco(self):
        self.recusa(bloco4(0, 0, AO={"controlador": "active"}), "controlador")
        self.recusa(bloco2(1, 0, A={"controlador": "active"}), "controlador")

    def test_fusao_incoerente_com_o_braco(self):
        self.recusa(bloco4(0, 0, PO={"fusao": "a1"}), "fusao")
        self.recusa(bloco2(1, 0, B={"fusao": "a1"}), "fusao")

    def test_variante_desconhecida(self):
        self.recusa(bloco4(0, 0, AL={"fusao": "a2"}), "a2")

    def test_braco_desconhecido(self):
        self.recusa([linha(0, 0, "A"), dict(linha(1, 0, "B"), braco="C")], "braco C")

    def test_braco_sem_bloco(self):
        self.recusa([linha(0, "-", "B", papel="calibracao")], "sem bloco")
        self.recusa([linha(0, "-", "PO", papel="calibracao")], "sem bloco")

    def test_quatro_bracos_na_matriz_de_13_colunas(self):
        self.recusa([{k: v for k, v in x.items() if k != "fusao"} for x in bloco4(0, 0)], "PO", colunas=COL13)


def parametros(**extra):
    v = {"duracao_s": 300, "warmup_min_s": 30, "inj_offset_s": 49, "f1_no": "isn2", "f1_dur_s": 60,
         "f2_no": "isn3", "f2_dur_s": 90, "f4_no": "isn3", "f4_severidade": "N3", "f4_direcao": "both",
         "f4_dur_s": 90, "hotrod_timeout_ms": 2000, "convergencia_teto_s": 600, "convergencia_periodo_s": 5,
         "w_recovery_s": 161, "religamento_prazo_s": 180, "exec_teto_s": 1300, "encerramento_folga_s": 2,
         "volume_exec_mb": 200}
    v.update(extra)
    return {"esquema": "campanha-s3/1", "versao": "teste",
            "parametros": {k: {"valor": x, "origem": "teste"} for k, x in v.items()}}


class FusaoTeto(unittest.TestCase):

    def conferir(self, obj, *extra):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "p.json"
            p.write_text(json.dumps(obj), encoding="utf-8")
            return rodar("parametros", "--arquivo", str(p), "--t-enc-pior-s", "21", *extra)

    def test_com_a_sonda_sai_depois_dos_s3(self):
        rc, out, err = self.conferir(parametros(fusao_teto_s=72, s3_replay_late_max=268), "--sonda-replay")
        self.assertEqual(0, rc, err)
        nomes = [l.split("\t")[0] for l in out.splitlines()]
        self.assertEqual("fusao_teto_s", nomes[-1])
        self.assertIn("fusao_teto_s\t72", out)
        self.assertLess(nomes.index("s3_replay_late_max"), nomes.index("fusao_teto_s"))

    def test_sem_a_sonda_recusa(self):
        rc, _, err = self.conferir(parametros(fusao_teto_s=72))
        self.assertEqual(2, rc)
        self.assertIn("fusao_teto_s", err)
        self.assertIn("--sonda-replay", err)

    def test_ausente_saida_igual(self):
        rc, out, err = self.conferir(parametros(), "--sonda-replay")
        self.assertEqual(0, rc, err)
        self.assertNotIn("fusao_teto_s", out)

    def test_tipo_faixa_e_origem(self):
        for ruim in (0, -1, 2.5, "72", True):
            with self.subTest(ruim=ruim):
                rc, _, err = self.conferir(parametros(fusao_teto_s=ruim), "--sonda-replay")
                self.assertEqual(2, rc)
                self.assertIn("fusao_teto_s", err)
        obj = parametros(fusao_teto_s=72)
        obj["parametros"]["fusao_teto_s"]["origem"] = " "
        rc, _, err = self.conferir(obj, "--sonda-replay")
        self.assertEqual(2, rc)
        self.assertIn("origem", err)

    def test_espera_vence_antes_do_fim_da_carga(self):
        rc, _, err = self.conferir(parametros(fusao_teto_s=162), "--sonda-replay")
        self.assertEqual(2, rc)
        self.assertIn("fusao_teto_s", err)
        self.assertIn("duracao_s", err)
        rc, _, err = self.conferir(parametros(fusao_teto_s=161), "--sonda-replay")
        self.assertEqual(0, rc, err)

    def test_funcao_direta(self):
        v = cc.conferir_parametros(parametros(fusao_teto_s=72), 21, sonda_replay=True)
        self.assertEqual(72, v["fusao_teto_s"])
        self.assertEqual("fusao_teto_s", list(v)[-1])


END_NS = 1793966568333787308   # marcador end da exec-0004-ASYNC-F2-A da recalibracao X-19 (2026-11-06 12:02:48,333 UTC)


def linha_vista(ts, cor=True):
    txt = ("%s INFO  [o.i.CLUSTER] ISPN000093: Received new, MERGED cluster view for channel cluster-tcc: "
           "MergeView::[isn2-19096|6] (3) [isn2-19096, isn1-47759, isn3-5889], 2 subgroups: [isn3-5889|5] (1) "
           "[isn3-5889], [isn2-19096|5] (2) [isn2-19096, isn1-47759]" % ts)
    return "\x1b[0m" + txt + "\x1b[m" if cor else txt


class FusaoVista(unittest.TestCase):
    """fusao-vista --logs <isnN TAB linha> --marcadores <marcadores.txt> [--log-driver X]: instante da primeira vista de
    fusao (ISPN000093) depois do marcador end em qualquer no (DT-027 secao 2.2), pelo carimbo do servidor (UTC, ms)."""

    def vista(self, linhas, marcadores=("fault=F2 node=isn3 event=start t_epoch_ns=1793966472285958813",
                                         "fault=F2 node=isn3 event=end t_epoch_ns=%d" % END_NS), log_driver=None):
        with tempfile.TemporaryDirectory() as d:
            lg, mk = Path(d) / "logs.txt", Path(d) / "marcadores.txt"
            lg.write_bytes(("".join("%s\t%s\n" % x for x in linhas)).encode("utf-8"))
            if marcadores is not None:
                mk.write_text("".join(m + "\n" for m in marcadores), encoding="utf-8")
            extra = ("--log-driver", log_driver) if log_driver else ()
            rc, out, err = rodar("fusao-vista", "--logs", str(lg), "--marcadores", str(mk), *extra)
            self.assertEqual(0, rc, err)
            return json.loads(out)

    def test_primeira_depois_do_end_em_qualquer_no(self):
        j = self.vista([("isn1", linha_vista("2026-11-06 12:03:32,541")),
                        ("isn3", linha_vista("2026-11-06 12:03:32,537")),
                        ("isn2", linha_vista("2026-11-06 12:03:32,539", cor=False))], log_driver="k8s-file")
        self.assertEqual("isn3", j["no"])
        self.assertEqual(1793966612537, j["primeira_apos_end_ms"])
        self.assertEqual(round((1793966612537 * 10**6 - END_NS) / 1e9, 3), j["desde_end_s"])
        self.assertEqual(END_NS, j["end_ns"])
        self.assertEqual("k8s-file", j["log_driver"])
        self.assertEqual({"isn1", "isn2", "isn3"}, set(j["por_no"]))
        self.assertEqual(1793966612541, j["por_no"]["isn1"]["primeira_apos_end_ms"])
        self.assertIsNone(j["motivo"])
        self.assertTrue(j["fonte"].startswith("ISPN000093"))

    def test_vista_antes_do_end_nao_conta(self):
        j = self.vista([("isn1", linha_vista("2026-11-06 12:01:00,000")),
                        ("isn1", linha_vista("2026-11-06 12:03:33,000"))])
        self.assertEqual(1793966613000, j["primeira_apos_end_ms"])
        self.assertEqual(2, j["por_no"]["isn1"]["linhas"])
        self.assertIsNone(j["por_no"]["isn2"]["primeira_apos_end_ms"])
        self.assertEqual(0, j["por_no"]["isn2"]["linhas"])

    def test_sem_linha_depois_do_end(self):
        j = self.vista([("isn1", linha_vista("2026-11-06 12:01:00,000"))], log_driver="journald")
        self.assertIsNone(j["primeira_apos_end_ms"])
        self.assertIsNone(j["desde_end_s"])
        self.assertIn("ISPN000093", j["motivo"])
        self.assertIn("journald", j["motivo"])

    def test_sem_marcador_end(self):
        j = self.vista([("isn1", linha_vista("2026-11-06 12:03:33,000"))],
                       ("fault=F2 node=isn3 event=start t_epoch_ns=1793966472285958813",))
        self.assertIsNone(j["end_ns"])
        self.assertIsNone(j["primeira_apos_end_ms"])
        self.assertIn("end", j["motivo"])
        j = self.vista([], None)
        self.assertIsNone(j["end_ns"])

    def test_funcao_de_carimbo(self):
        self.assertEqual(1793966612537, cc.carimbo_ms(linha_vista("2026-11-06 12:03:32,537")))
        self.assertIsNone(cc.carimbo_ms("ISPN000093 sem carimbo"))


FIXTURE_X19 = AQUI / "testdata" / "fusao-x19"
# primeira amostra da sonda de topologia com os tres nos em 3 depois do end, nas F2 da recalibracao X-19 (P-65)
ESPERADO_X19 = {"exec-0001-SYNC-F2-A": 44.1, "exec-0004-ASYNC-F2-A": 42.2, "exec-0008-SYNC-F2-A": 44.2,
                "exec-0010-ASYNC-F2-A": 44.2, "exec-0013-ASYNC-F2-A": 44.3, "exec-0016-SYNC-F2-A": 46.2,
                "exec-0018-ASYNC-F2-A": 44.2, "exec-0022-ASYNC-F2-A": 44.2, "exec-0027-ASYNC-F2-A": 46.1}


def amostra(t_ns, ns=(3, 3, 3), resp=(True, True, True)):
    por = [{"no": "127.0.0.1:1122%d" % (k + 2), "respondeu": r, "numero_de_nos": n if r else None,
            "motivo": "ok" if n == 3 else "nos:%d<3" % n, "anomalo": n != 3} for k, (n, r) in enumerate(zip(ns, resp))]
    return json.dumps({"tipo": "topologia", "t_ns": t_ns, "anomala": min(ns) < 3, "nos_min": min(ns), "por_no": por})


META_OBS = json.dumps({"tipo": "meta", "esquema": "observaveis-controlador/1.2", "nos_esperados": 3})
PART = (2, 2, 1)


class FusaoTrilha(unittest.TestCase):
    """fusao-trilha --observaveis <observaveis-rep-NNN.jsonl> --marcadores <marcadores.txt> (P-65, decisao do
    coordenador de 06/11): t_fusao_ns = primeira amostra da sonda de topologia do controlador depois do end em que os
    tres nos respondem com a mesma vista de 3 membros (a trilha so traz number_of_nodes por no: tres nos em 3)."""

    def trilha(self, linhas, marcadores=("fault=F2 node=isn3 event=start t_epoch_ns=1793966472285958813",
                                          "fault=F2 node=isn3 event=end t_epoch_ns=%d" % END_NS), bruto=None):
        with tempfile.TemporaryDirectory() as d:
            ob, mk = Path(d) / "observaveis-rep-001.jsonl", Path(d) / "marcadores.txt"
            ob.write_bytes((bruto if bruto is not None else "".join(l + "\n" for l in linhas)).encode("utf-8"))
            if marcadores is not None:
                mk.write_text("".join(m + "\n" for m in marcadores), encoding="utf-8")
            rc, out, err = rodar("fusao-trilha", "--observaveis", str(ob), "--marcadores", str(mk))
            self.assertEqual(0, rc, err)
            return json.loads(out)

    def test_primeira_amostra_com_os_tres_em_3_depois_do_end(self):
        S = 10**9
        ls = [META_OBS, amostra(END_NS - 50 * S)] + [amostra(END_NS - 4 * S + k * 2 * S, PART) for k in range(24)]
        ls += [amostra(END_NS + 44 * S + 100_000_000), amostra(END_NS + 46 * S + 100_000_000)]
        j = self.trilha(ls)
        self.assertEqual(END_NS + 44 * S + 100_000_000, j["t_fusao_ns"])
        self.assertEqual(44.1, j["desde_end_s"])
        self.assertEqual(END_NS, j["end_ns"])
        self.assertEqual(3, j["nos_esperados"])
        self.assertEqual(23, j["amostras_apos_end"])
        self.assertAlmostEqual(2.0, j["periodo_amostras_s"], places=1)
        self.assertIsNone(j["motivo"])
        self.assertIn("observaveis", j["fonte"])

    def test_no_sem_resposta_ou_vista_incompleta_nao_e_fusao(self):
        S = 10**9
        ls = [META_OBS, amostra(END_NS + 2 * S, (3, 3, 3), (True, True, False)),
              amostra(END_NS + 4 * S, (3, 3, 2)), amostra(END_NS + 6 * S)]
        self.assertEqual(END_NS + 6 * S, self.trilha(ls)["t_fusao_ns"])

    def test_sem_fusao_depois_do_end(self):
        S = 10**9
        j = self.trilha([META_OBS] + [amostra(END_NS + k * 2 * S, PART) for k in range(10)])
        self.assertIsNone(j["t_fusao_ns"])
        self.assertIsNone(j["desde_end_s"])
        self.assertIn("nenhuma amostra", j["motivo"])

    def test_sem_end(self):
        j = self.trilha([META_OBS, amostra(END_NS)], ("fault=F2 node=isn3 event=start t_epoch_ns=1",))
        self.assertIsNone(j["t_fusao_ns"])
        self.assertIn("end", j["motivo"])
        j = self.trilha([META_OBS, amostra(END_NS)], None)
        self.assertIsNone(j["end_ns"])

    def test_sem_trilha_e_linha_truncada(self):
        S = 10**9
        bruto = META_OBS + "\n" + '{"tipo":"janela","t_ns":1}\n' + amostra(END_NS + 2 * S) + "\n" + '{"tipo":"topol'
        self.assertEqual(END_NS + 2 * S, self.trilha(None, bruto=bruto)["t_fusao_ns"])
        with tempfile.TemporaryDirectory() as d:
            mk = Path(d) / "marcadores.txt"
            mk.write_text("fault=F2 node=isn3 event=end t_epoch_ns=%d\n" % END_NS, encoding="utf-8")
            rc, out, err = rodar("fusao-trilha", "--observaveis", str(Path(d) / "nao.jsonl"), "--marcadores", str(mk))
            self.assertEqual(0, rc, err)
            j = json.loads(out)
            self.assertIsNone(j["t_fusao_ns"])
            self.assertIn("observaveis", j["motivo"])

    def test_x19_fusao_44_s_depois_do_end_nas_f2(self):
        """Brutos da recalibracao X-19 (fixture com a origem em testdata/fusao-x19/ORIGEM.txt): a regra da ~44 s."""
        vistos = {}
        for d in sorted(FIXTURE_X19.glob("exec-*")):
            rc, out, err = rodar("fusao-trilha", "--observaveis", str(d / "observaveis-rep-001.jsonl"),
                                 "--marcadores", str(d / "marcadores.txt"))
            self.assertEqual(0, rc, err)
            j = json.loads(out)
            vistos[d.name] = round(j["desde_end_s"], 1)
            self.assertTrue(42.0 <= j["desde_end_s"] <= 48.0, (d.name, j))
            self.assertAlmostEqual(2.0, j["periodo_amostras_s"], delta=0.1)
        self.assertEqual(ESPERADO_X19, vistos)
        asinc = sorted(v for k, v in vistos.items() if "ASYNC" in k)
        self.assertEqual(44.2, asinc[len(asinc) // 2], "mediana das seis ASYNC F2")


if __name__ == "__main__":
    unittest.main()
