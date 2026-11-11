"""Testes do criterio h4 nas campanhas S3 (scripts/campanha_s3_h4.py), W-6 / B-46, requisito 6.

O adaptador NAO reimplementa o h4: importa analysis/h4.py (lista e9c44819 da adenda da DT-018 intacta) e chama
h4.avaliar_execucao e h4.avaliar_parada. A unica diferenca e a janela: no rebaseline ela e a janela de medicao do
PR (ancora + 44 s a ancora + 300 s, C3 secao 3.1); no S3 e a janela de medicao da propria execucao, lida da linha
"Warm-up ate +Xs; medicao a partir de +Ys; total Zs" pelo orquestrador e gravada em execucao.json (janela_h4). O
segmento do F1 (C3 secao 4.2) usa f1_reiniciado_desde_ancora_s ou, sem ele, o reinicio previsto pelo protocolo
(inj_offset + f1_dur), no lugar dos 109 s do rebaseline. Limiares, parada (C3 secao 4.3) e formato de saida sao os
do h4.py.

Uso: python -I scripts/test_campanha_s3_h4.py
"""
import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
sys.path.insert(0, str(AQUI.parent / "analysis"))
import campanha_s3_h4 as ch  # noqa: E402
import h4  # noqa: E402

US = h4.US
ANC_MS = 1793900000000
ANC = ANC_MS * 1000


def cabecalho():
    return ("# tcc-amostrador-infra v1 id=calibracao-20261107T120000Z inicio=2026-11-07T12:00:00+00:00 boot_id=b "
            "host=vm disco=sda periodo_s=1 pid=1 versao_script_sha256=%s\n" % ("0" * 64))


def linha(t, up, full):
    return "%s %s io 7 %d cpu 3 - mem 0 0 sda %s\n" % (h4.seg(t), h4.seg(up), full, " ".join(["1"] * 17))


def escrever_amostras(path, ini_s=-20, fim_s=430, degraus=None, anc=ANC):
    """Uma amostra por segundo; degraus {segundo relativo a ancora: segundos de io full somados ali}."""
    degraus = degraus or {}
    full, linhas = 10 * US, []
    for k, s in enumerate(range(ini_s, fim_s + 1)):
        t = anc + s * US
        full += int(degraus.get(s, 0) * US)
        linhas.append(linha(t, 1000 * US + k * US, full))
    Path(path).write_text(cabecalho() + "".join(linhas), encoding="ascii", newline="\n")
    return str(path)


def iso(us):
    s, r = divmod(us, US)
    return h4.utc(s * US + (r // 1000) * 1000)[:23] + "Z"


def execucao(saida, i, celula="ASYNC-F2", braco="A", fase="calibracao", anc_ms=ANC_MS, fim_s=330.0,
             janela=(44.0, 300.0), f1_padrao=None, rein="ausente", lanc_ms=None, sem_janela=False):
    d = Path(saida) / ("exec-%04d-%s-%s" % (i, celula, braco))
    d.mkdir(parents=True, exist_ok=True)
    falha = celula.split("-")[1]
    ex = {"i": i, "celula": celula, "braco": braco, "fase": fase, "falha": falha,
          "lancamento_ms": lanc_ms if lanc_ms is not None else anc_ms - 12000 + i,
          "ancora_ms": anc_ms, "fim_utc": iso(anc_ms * 1000 + int(fim_s * US)), "falha_efetiva": None}
    if not sem_janela:
        ex["janela_h4"] = {"ini_desde_ancora_s": janela[0], "fim_desde_ancora_s": janela[1],
                           "f1_reinicio_padrao_desde_ancora_s": f1_padrao}
    if rein != "ausente":
        ex["falha_efetiva"] = {"f1_reiniciado_desde_ancora_s": rein}
    (d / "execucao.json").write_text(json.dumps(ex), encoding="utf-8")
    return d


def rodar(*args):
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        rc = ch.main([str(a) for a in args])
    return rc, out.getvalue(), err.getvalue()


def vereditos(saida, fase="calibracao"):
    js = json.loads((Path(saida) / "h4" / ("h4-%s.json" % fase)).read_text(encoding="utf-8"))
    return [x["veredito"] for x in js["execucoes"]], js


class Janela(unittest.TestCase):

    def test_igual_ao_h4_com_a_janela_do_rebaseline(self):
        """Com 44..300 s e F1 em +109 s o adaptador da o mesmo veredito que h4.avaliar_execucao, linha a linha."""
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt", degraus={120: 2.0})
            e1 = execucao(d, 0, "ASYNC-F2")
            e2 = execucao(d, 1, "ASYNC-F1", rein=109.0, f1_padrao=109.0)
            rc, _, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
            self.assertEqual(0, rc, err)
            obtidos, js = vereditos(d)
            serie = h4.Serie([am])
            esperados = [h4.avaliar_execucao(serie, json.loads((e / "execucao.json").read_text()))["veredito"]
                         for e in (e1, e2)]
            self.assertEqual(esperados, obtidos)
            self.assertEqual(["h4a", "ok"], obtidos, "as janelas do degrau em +120 (de +120 a +129) caem inteiras no segmento do F1 [+107, +169] so na segunda")
            self.assertEqual(list(h4.COLUNAS), list(js["execucoes"][0].keys()))

    def test_janela_vem_do_execucao_json(self):
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt", degraus={350: 2.0})
            execucao(d, 0, fim_s=420.0, janela=(44.0, 400.0))
            execucao(d, 1, fim_s=420.0, janela=(44.0, 300.0))
            rc, _, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
            self.assertEqual(0, rc, err)
            self.assertEqual(["h4a", "ok"], vereditos(d)[0])

    def test_inicio_da_janela(self):
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt", degraus={30: 2.0})
            execucao(d, 0, janela=(20.0, 300.0))
            execucao(d, 1, janela=(50.0, 300.0))
            rc, _, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
            self.assertEqual(0, rc, err)
            self.assertEqual(["h4a", "ok"], vereditos(d)[0])

    def test_segmento_do_f1_pelo_padrao_do_protocolo(self):
        """Sem f1_reiniciado_desde_ancora_s, o segmento vem de inj_offset + f1_dur (aqui 120 s), nao dos 109 s."""
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt", degraus={150: 2.0})
            execucao(d, 0, "ASYNC-F1", f1_padrao=120.0)
            execucao(d, 1, "ASYNC-F1", f1_padrao=60.0)
            rc, _, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
            self.assertEqual(0, rc, err)
            v, js = vereditos(d)
            self.assertEqual(["ok", "h4a"], v)
            self.assertNotEqual("-", js["execucoes"][0]["pior_janela_seg_f1_s"])

    def test_globais_do_h4_restauradas(self):
        antes = (h4.MEDICAO_INI_US, h4.MEDICAO_FIM_US, h4.MEDICAO_INI_S, h4.MEDICAO_FIM_S, h4.F1_REINICIO_PADRAO_S)
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt")
            execucao(d, 0, janela=(10.0, 200.0))
            rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
        self.assertEqual(antes, (h4.MEDICAO_INI_US, h4.MEDICAO_FIM_US, h4.MEDICAO_INI_S, h4.MEDICAO_FIM_S,
                                 h4.F1_REINICIO_PADRAO_S))


class Fase(unittest.TestCase):

    def test_parada_por_quatro_seguidas(self):
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt", degraus={100: 2.0})
            for i in range(4):
                execucao(d, i)
            rc, out, _ = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
            self.assertEqual(h4.EXIT_PARADA, rc)
            self.assertTrue(vereditos(d)[1]["parada"]["parada"])
            self.assertIn("PARADA H4", out)

    def test_so_a_fase_pedida_e_sem_abortadas(self):
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt")
            execucao(d, 0, fase="calibracao")
            execucao(d, 1, fase="nucleo")
            ab = execucao(d, 2, fase="calibracao")
            ab.rename(ab.with_name(ab.name + ".abortada-20261107T120000Z"))
            rc, _, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
            self.assertEqual(0, rc, err)
            self.assertEqual(1, len(vereditos(d)[0]))

    def test_veredito_da_execucao_pedida(self):
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt", degraus={100: 2.0})
            execucao(d, 0)
            e = execucao(d, 1, janela=(200.0, 300.0))
            rc, out, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao", "--exec", e)
            self.assertEqual(0, rc, err)
            self.assertIn("VEREDITO 1 ok", out)

    def test_sem_janela_h4_e_erro_de_uso(self):
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt")
            execucao(d, 0, sem_janela=True)
            rc, _, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao")
            self.assertEqual(2, rc)
            self.assertIn("janela_h4", err)

    def test_sem_ancora_nao_exige_janela(self):
        """Execucao sem ancora (recusa 'sem ancora'): o orquestrador nao tem janela; o h4.py da 'sem-ancora'."""
        with tempfile.TemporaryDirectory() as d:
            am = escrever_amostras(Path(d) / "a.txt")
            e = execucao(d, 0, sem_janela=True)
            ex = json.loads((e / "execucao.json").read_text())
            ex["ancora_ms"] = None
            (e / "execucao.json").write_text(json.dumps(ex), encoding="utf-8")
            rc, out, err = rodar("fase", "--amostras", am, "--saida", d, "--fase", "calibracao", "--exec", e)
            self.assertEqual(0, rc, err)
            self.assertIn("VEREDITO 0 sem-ancora", out)

    def test_amostras_ilegiveis(self):
        with tempfile.TemporaryDirectory() as d:
            execucao(d, 0)
            ruim = Path(d) / "ruim.txt"
            ruim.write_text("nao e amostra\n", encoding="ascii")
            rc, _, _ = rodar("fase", "--amostras", ruim, "--saida", d, "--fase", "calibracao")
            self.assertEqual(4, rc)


if __name__ == "__main__":
    unittest.main()
