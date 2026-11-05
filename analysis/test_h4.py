"""Testes do analysis/h4.py (prompt W-h4 §4.1; C3 = complemento 3 do pre-registro do rebaseline).

Rodar: python -I -m unittest discover -s analysis -p "test_h4.py"
So biblioteca padrao. Os arquivos de amostras sao sinteticos, menos os tres do teste 7 (testdata/h4/), que sao
copias dos brutos do gate S7 e passam por um conversor que existe SO aqui.
"""
import contextlib
import hashlib
import io
import json
import os
import re
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import h4  # noqa: E402

US = h4.US
ANC_MS = 1793840000000          # ancora sintetica (2026-11-05T05:33:20Z)
ANC = ANC_MS * 1000
AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent
DADOS = AQUI / "testdata" / "h4"


# ----------------------------------------------------------------------------- utilitarios dos testes

def cabecalho(boot="boot-1", ident="noite1-20261105T000000Z", pid="100", disco="sda"):
    return ("# tcc-amostrador-infra v1 id=%s inicio=2026-11-05T00:00:00+00:00 boot_id=%s host=vm disco=%s "
            "periodo_s=1 pid=%s versao_script_sha256=%s\n" % (ident, boot, disco, pid, "0" * 64))


def linha(t, up, full, disco="sda"):
    return "%s %s io 7 %d cpu 3 - mem 0 0 %s %s\n" % (h4.seg(t), h4.seg(up), full, disco, " ".join(["1"] * 17))


def amostras(tempos, degraus=None, saltos=None, full0=10 * US):
    """[(t, up, full)] nos tempos dados (us). degraus {t: us de io full somados naquela amostra};
    saltos {t: us subtraidos do uptime a partir daquela amostra} (relogio de parede pula para a frente)."""
    degraus, saltos = degraus or {}, saltos or {}
    out, full, desvio = [], full0, 0
    for t in sorted(tempos):
        full += degraus.get(t, 0)
        desvio += saltos.get(t, 0)
        out.append((t, t - tempos[0] + 1000 * US - desvio, full))
    return out


def grade(ini_s=-20, fim_s=330, off_s=0.0, anc=ANC, passo_us=US):
    """tempos de anc+ini_s a anc+fim_s, passo de 1 s, deslocados de off_s."""
    t, out = anc + int(round(ini_s * US)) + int(round(off_s * US)), []
    while t <= anc + int(round(fim_s * US)):
        out.append(t)
        t += passo_us
    return out


def escrever(path, am, cab=None, extra=""):
    Path(path).write_text((cab or cabecalho()) + "".join(linha(*x) for x in am) + extra, encoding="ascii",
                          newline="\n")
    return str(path)


def rel(s):
    return ANC + int(round(s * US))


def iso(us):
    s, r = divmod(us, US)
    return h4.utc(s * US + (r // 1000) * 1000)[:23] + "Z"   # formato do runner: milissegundos


MATRIZ_CAB = "i\tfase\tnoite\trodada\tposicao\tcelula\tcenario\tmodo\tfalha\tseed\n"


def matriz(saida, linhas):
    """linhas [(i, noite, celula)] -> <saida>/matriz.tsv (fase campanha ou piloto conforme a noite)."""
    txt = MATRIZ_CAB
    for pos, (i, noite, cel) in enumerate(linhas):
        cen, modo, falha = cel.split("-")
        fs = "piloto" if noite == "piloto" else "campanha"
        txt += "%d\t%s\t%s\t1\t%d\t%s\t%s\t%s\t%s\t%d\n" % (i, fs, noite, pos, cel, cen, modo, falha, 50000 + 100 * i)
    p = Path(saida) / "matriz.tsv"
    p.write_text(txt, encoding="ascii", newline="\n")
    return str(p)


def execucao(saida, i, celula, fase_campo="campanha", anc_ms=ANC_MS, fim_s=305.0, lanc_ms=None, rein="ausente",
             sem_fim=False, sem_ancora=False):
    d = Path(saida) / ("exec-%04d-%s" % (i, celula))
    d.mkdir(parents=True, exist_ok=True)
    falha = celula.split("-")[2]
    ex = {"i": i, "celula": celula, "fase": fase_campo, "falha": falha,
          "lancamento_ms": lanc_ms if lanc_ms is not None else anc_ms - 12000,
          "lancamento_utc": iso((anc_ms - 12000) * 1000),
          "ancora_ms": None if sem_ancora else anc_ms, "ancora_utc": None if sem_ancora else iso(anc_ms * 1000),
          "fim_utc": None if sem_fim else iso(anc_ms * 1000 + int(fim_s * US)), "falha_efetiva": None}
    if falha == "F1" and rein != "ausente":
        ex["falha_efetiva"] = {"f1_parado_desde_ancora_s": 49.1, "f1_reiniciado_desde_ancora_s": rein}
    (d / "execucao.json").write_text(json.dumps(ex), encoding="utf-8")
    return d


def checagem(saida, i, celula, veredito, fase="campanha"):
    d = Path(saida) / ("exec-%04d-%s" % (i, celula))
    d.mkdir(parents=True, exist_ok=True)
    (d / "checagem.json").write_text(json.dumps({"i": i, "celula": celula, "fase": fase, "veredito": veredito}),
                                     encoding="utf-8")


def rodar(*args):
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        rc = h4.main([str(a) for a in args])
    return rc, out.getvalue(), err.getvalue()


def tsv_fase(saida, fase):
    return h4.ler_tsv(Path(saida) / "h4" / ("h4-%s.tsv" % fase))


def arvore_sha(raiz):
    out = []
    for r, _d, arqs in os.walk(raiz):
        for a in sorted(arqs):
            p = Path(r) / a
            out.append((str(p.relative_to(raiz)), hashlib.sha256(p.read_bytes()).hexdigest()))
    return sorted(out)


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="h4-"))
        self.saida = self.tmp / "saida"
        self.saida.mkdir()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def uma_execucao(self, am, celula="S1-sync-none", fim_s=305.0, rein="ausente", fase="noite1"):
        """Uma execucao de campanha na noite1 com as amostras dadas; devolve (rc, linha do TSV, stdout)."""
        matriz(self.saida, [(1, "noite1", celula)])
        execucao(self.saida, 1, celula, fim_s=fim_s, rein=rein)
        arq = escrever(self.tmp / "amostras.txt", am)
        rc, out, err = rodar("fase", "--amostras", arq, "--saida", self.saida, "--fase", fase)
        self.assertIn(rc, (0, 30), err)
        return rc, tsv_fase(self.saida, fase)[0], out


# ============================================================================= 1. janela de 10 s

class T1Janela(Base):
    def test_valor_exato(self):
        # 0,2 s de io full por amostra entre +100 e +109: a janela que termina em +109 soma os 10 degraus (j = +99)
        t = grade()
        am = amostras(t, degraus={rel(k): 200000 for k in range(100, 110)})
        s = h4.Serie([escrever(self.tmp / "a.txt", am)])
        i = t.index(rel(109))
        self.assertEqual(s.janela[i], 2000000)            # degraus de +100..+109, j = +99
        self.assertEqual(s.janela[t.index(rel(110))], 1800000)   # j = +100: o degrau de +100 ja nao entra
        self.assertEqual(max(s.janela), 2000000)

    def test_borda_menor_ou_igual_a_10s(self):
        # amostras em 0, 10,000000 e 10,000001 s: t_i - t_j = 10 s exato entra; 10,000001 nao
        t = [rel(0), rel(5), rel(10)]
        am = amostras(t, degraus={rel(5): 700000, rel(10): 0})
        s = h4.Serie([escrever(self.tmp / "a.txt", am)])
        self.assertEqual(s.janela[2], 700000)              # j = 0 (t_i - t_j = 10 s)
        t2 = [rel(0), rel(0) + 1, rel(10) + 1]
        am2 = amostras(t2, degraus={rel(0) + 1: 900000})
        s2 = h4.Serie([escrever(self.tmp / "b.txt", am2)])
        self.assertEqual(s2.janela[2], 0)                  # 10,000001 - 0 > 10 s e 10 s - 0,000001 entra: j = 1

    def test_janela_que_atravessa_buraco(self):
        # buraco de 15 s: a janela da amostra depois do buraco so tem ela mesma (valor 0), mesmo com io full
        t = [rel(k) for k in range(0, 20)] + [rel(k) for k in range(35, 50)]
        am = amostras(t, degraus={rel(35): 5 * US, rel(36): 100000})
        s = h4.Serie([escrever(self.tmp / "a.txt", am)])
        self.assertEqual(s.janela[t.index(rel(35))], 0)
        self.assertEqual(s.janela[t.index(rel(36))], 100000)
        self.assertEqual(s.intervalo[t.index(rel(35))], 16 * US)


# ============================================================================= 2. fronteiras dos limiares

class T2Limiares(Base):
    def test_h4a_1499999_ok_e_1500000_h4a(self):
        t = grade()
        rc, l, _ = self.uma_execucao(amostras(t, degraus={rel(150): 1499999}))
        self.assertEqual(l["veredito"], "ok")
        self.assertEqual(l["pior_janela_s"], "1.499999")
        self.tearDown(); self.setUp()
        rc, l, _ = self.uma_execucao(amostras(t, degraus={rel(150): 1500000}))
        self.assertEqual(l["veredito"], "h4a")
        self.assertEqual(l["pior_janela_s"], "1.500000")

    def test_intervalo_2999_ok_e_3000_h4b(self):
        base = [x for x in grade() if not rel(150) < x < rel(153)]
        for buraco_us, esperado in ((2999000, "ok"), (3000000, "h4b")):
            t = sorted(set([x for x in base if x <= rel(150)] + [rel(150) + buraco_us]
                           + [x + buraco_us - 3 * US for x in base if x > rel(150)]))
            rc, l, _ = self.uma_execucao(amostras(t))
            self.assertEqual(l["veredito"], esperado, l)
            self.assertEqual(l["maior_intervalo_s"], h4.seg(buraco_us))
            self.tearDown(); self.setUp()

    def test_salto_0999_ok_e_1000_h4b(self):
        t = grade()
        for salto_us, esperado in ((999000, "ok"), (1000000, "h4b")):
            rc, l, _ = self.uma_execucao(amostras(t, saltos={rel(200): salto_us}))
            self.assertEqual(l["veredito"], esperado, l)
            self.assertEqual(l["maior_salto_s"], h4.seg(salto_us))
            self.tearDown(); self.setUp()


# ============================================================================= 3. janela h4

class T3JanelaH4(Base):
    def test_episodio_43_9_fora(self):
        # degrau em +34,9: as janelas que o contem terminam em +34,9..+43,9, todas antes de +44
        t = grade(off_s=0.9)
        rc, l, _ = self.uma_execucao(amostras(t, degraus={rel(34.9): 2 * US}))
        self.assertEqual(l["veredito"], "ok")
        self.assertEqual(l["janela_ini_utc"], h4.utc(rel(44)))

    def test_episodio_44_0_dentro(self):
        t = grade(off_s=0.0)
        rc, l, _ = self.uma_execucao(amostras(t, degraus={rel(35): 2 * US}))
        self.assertEqual(l["veredito"], "h4a")
        self.assertEqual(l["pior_janela_fim_utc"], h4.utc(rel(44)))

    def test_episodio_300_1_fora(self):
        t = grade(off_s=0.1)
        rc, l, _ = self.uma_execucao(amostras(t, degraus={rel(300.1): 2 * US}))
        self.assertEqual(l["veredito"], "ok")
        self.assertEqual(l["janela_fim_utc"], h4.utc(rel(300)))

    def test_fim_utc_antes_de_300(self):
        t = grade()
        rc, l, _ = self.uma_execucao(amostras(t, degraus={rel(250): 2 * US}), fim_s=200.0)
        self.assertEqual(l["janela_fim_utc"], h4.utc(rel(200)))
        self.assertEqual(l["veredito"], "ok")
        self.tearDown(); self.setUp()
        rc, l, _ = self.uma_execucao(amostras(t, degraus={rel(150): 2 * US}), fim_s=200.0)
        self.assertEqual(l["veredito"], "h4a")

    def test_sem_ancora_em_curso_abortada_e_selecao_da_fase(self):
        matriz(self.saida, [(1, "noite1", "S1-sync-none"), (2, "noite1", "S2-sync-none"),
                            (3, "noite1", "S1-async-none"), (61, "noite2", "S2-async-none"),
                            (1000, "piloto", "S1-sync-none")])
        execucao(self.saida, 1, "S1-sync-none", sem_ancora=True)
        execucao(self.saida, 2, "S2-sync-none", sem_fim=True)
        d = execucao(self.saida, 3, "S1-async-none")
        d.rename(self.saida / "exec-0003-S1-async-none.abortada-20261105T000000Z")   # abortada: fora
        (self.saida / "exec-0003-S1-async-none").mkdir()                     # em curso, sem execucao.json
        execucao(self.saida, 61, "S2-async-none")
        execucao(self.saida, 1000, "S1-sync-none", fase_campo="piloto")
        execucao(self.saida, 120, "S1-sync-none", fase_campo="reposicao")
        arq = escrever(self.tmp / "a.txt", amostras(grade()))
        rc, out, err = rodar("fase", "--amostras", arq, "--saida", self.saida, "--fase", "noite1")
        self.assertEqual(rc, 0, err)
        ls = tsv_fase(self.saida, "noite1")
        self.assertEqual([(x["i"], x["veredito"]) for x in ls], [("1", "sem-ancora")])
        js = json.loads((self.saida / "h4" / "h4-noite1.json").read_text(encoding="utf-8"))
        self.assertIn("exec-0002-S2-sync-none", js["execucoes_em_curso_ou_sem_execucao_json"])
        self.assertIn("exec-0003-S1-async-none", js["execucoes_em_curso_ou_sem_execucao_json"])
        for fase, i in (("noite2", "61"), ("piloto", "1000"), ("reposicoes", "120")):
            rc, out, err = rodar("fase", "--amostras", arq, "--saida", self.saida, "--fase", fase)
            self.assertEqual(rc, 0, err)
            self.assertEqual([x["i"] for x in tsv_fase(self.saida, fase)], [i])

    def test_saidas_tsv_json_e_resumo(self):
        rc, l, out = self.uma_execucao(amostras(grade(), degraus={rel(150): 2 * US}))
        self.assertEqual(list(l.keys()), list(h4.COLUNAS))
        js = json.loads((self.saida / "h4" / "h4-noite1.json").read_text(encoding="utf-8"))
        self.assertEqual(js["versao"], "h4/1")
        self.assertEqual(js["constantes"]["LIMIAR_H4A_S"], 1.5)
        self.assertEqual(js["arquivos"][0]["sha256"],
                         hashlib.sha256((self.tmp / "amostras.txt").read_bytes()).hexdigest())
        self.assertEqual(js["execucoes"][0]["veredito"], "h4a")
        self.assertIn("h4: 1 de 1 execucoes; episodios/h=", out)
        self.assertIn("parada=nao", out)
        self.assertRegex(out, r"(?m)^h4 i=1 S1-sync-none falha=none veredito=h4a ")

    def test_episodios_no_arquivo_inteiro(self):
        # dois episodios, um fora de qualquer janela h4 (antes da ancora) e um buraco de 4 s; 1 h de arquivo
        t = [x for x in grade(ini_s=-3300, fim_s=300) if not rel(-1000) < x < rel(-996)]
        am = amostras(t, degraus={rel(-2000): 2 * US})
        s = h4.Serie([escrever(self.tmp / "a.txt", am)])
        eps = s.episodios()
        self.assertEqual(len(eps), 2)
        self.assertEqual(eps[0]["ini_utc"], h4.utc(rel(-2000)))
        self.assertEqual(eps[0]["fim_utc"], h4.utc(rel(-1991)))
        self.assertEqual(eps[1]["maior_intervalo_s"], "4.000000")
        self.assertAlmostEqual(s.horas(), 1.0, places=6)

    def test_linha_incompleta_no_fim_e_ignorada(self):
        am = amostras(grade())
        arq = escrever(self.tmp / "a.txt", am, extra="1793840400.000000 1400.0 io 7")
        s = h4.Serie([arq])
        self.assertEqual(s.n, len(am))


# ============================================================================= 4. segmento do F1

class T4SegmentoF1(Base):
    def test_episodio_dentro_do_segmento(self):
        rc, l, _ = self.uma_execucao(amostras(grade(), degraus={rel(120): 2 * US}), celula="S1-sync-F1", rein=109.0)
        self.assertEqual(l["veredito"], "ok")
        self.assertEqual(l["pior_janela_seg_f1_s"], "2.000000")
        self.assertEqual(l["seg_f1_ini_utc"], h4.utc(rel(107)))
        self.assertEqual(l["seg_f1_fim_utc"], h4.utc(rel(169)))

    def test_borda_do_segmento(self):
        # degrau em +160: janelas +160..+169, todas no segmento [107, 169]
        rc, l, _ = self.uma_execucao(amostras(grade(), degraus={rel(160): 2 * US}), celula="S1-sync-F1", rein=109.0)
        self.assertEqual(l["veredito"], "ok")
        self.tearDown(); self.setUp()
        # degrau em +161: a janela de +170 fica fora do segmento e ve o degrau
        rc, l, _ = self.uma_execucao(amostras(grade(), degraus={rel(161): 2 * US}), celula="S1-sync-F1", rein=109.0)
        self.assertEqual(l["veredito"], "h4a")
        self.assertEqual(l["pior_janela_fim_utc"], h4.utc(rel(170)))
        self.tearDown(); self.setUp()
        # degrau em +98: janelas +98..+107; a de +107 esta no segmento, as de +98..+106 nao
        rc, l, _ = self.uma_execucao(amostras(grade(), degraus={rel(98): 2 * US}), celula="S1-sync-F1", rein=109.0)
        self.assertEqual(l["veredito"], "h4a")
        self.assertEqual(l["pior_janela_seg_f1_s"], "2.000000")

    def test_fora_do_segmento(self):
        rc, l, _ = self.uma_execucao(amostras(grade(), degraus={rel(250): 2 * US}), celula="S1-sync-F1", rein=109.0)
        self.assertEqual(l["veredito"], "h4a")

    def test_reinicio_nulo_usa_109(self):
        for rein in (None, "ausente"):
            rc, l, _ = self.uma_execucao(amostras(grade(), degraus={rel(120): 2 * US}), celula="S2-async-F1",
                                         rein=rein)
            self.assertEqual(l["seg_f1_ini_utc"], h4.utc(rel(107)), rein)
            self.assertEqual(l["veredito"], "ok")
            self.tearDown(); self.setUp()

    def test_reinicio_medido(self):
        rc, l, _ = self.uma_execucao(amostras(grade()), celula="S1-sync-F1", rein=112.345)
        self.assertEqual(l["seg_f1_ini_utc"], h4.utc(rel(110.345)))
        self.assertEqual(l["seg_f1_fim_utc"], h4.utc(rel(172.345)))

    def test_h4b_dentro_do_segmento_continua_h4(self):
        t = [x for x in grade() if not rel(130) < x < rel(134)]
        rc, l, _ = self.uma_execucao(amostras(t), celula="S1-sync-F1", rein=109.0)
        self.assertEqual(l["veredito"], "h4b")

    def test_sem_falha_nao_tem_segmento(self):
        rc, l, _ = self.uma_execucao(amostras(grade(), degraus={rel(120): 2 * US}))
        self.assertEqual((l["seg_f1_ini_utc"], l["pior_janela_seg_f1_s"], l["veredito"]), ("-", "-", "h4a"))


# ============================================================================= 5. h4c

class T5Cobertura(Base):
    def test_comeca_3_1_s_depois(self):
        rc, l, _ = self.uma_execucao(amostras(grade(ini_s=47.1, fim_s=330)))
        self.assertEqual(l["veredito"], "h4c")
        self.assertEqual(l["cobertura"], "falta-inicio=3.100000s")
        self.assertEqual(rc, 30)

    def test_comeca_3_0_s_depois_ok(self):
        rc, l, _ = self.uma_execucao(amostras(grade(ini_s=47, fim_s=330)))
        self.assertEqual((l["veredito"], l["cobertura"], rc), ("ok", "completa", 0))

    def test_termina_antes_do_fim(self):
        rc, l, _ = self.uma_execucao(amostras(grade(ini_s=-20, fim_s=290)))
        self.assertEqual(l["veredito"], "h4c")
        self.assertEqual(l["cobertura"], "falta-fim=10.000000s")

    def test_sem_amostras_na_janela(self):
        rc, l, _ = self.uma_execucao(amostras(grade(ini_s=-200, fim_s=-100)))
        self.assertEqual((l["veredito"], l["cobertura"], l["n_amostras"]), ("h4c", "sem-amostras", "0"))

    def test_arquivo_ausente(self):
        matriz(self.saida, [(1, "noite1", "S1-sync-none")])
        execucao(self.saida, 1, "S1-sync-none")
        rc, out, err = rodar("fase", "--amostras", self.tmp / "nao-existe.txt", "--saida", self.saida, "--fase", "noite1")
        self.assertEqual(rc, 4)
        self.assertIn("ausente", err)

    def test_malformado_com_numero_da_linha(self):
        matriz(self.saida, [(1, "noite1", "S1-sync-none")])
        execucao(self.saida, 1, "S1-sync-none")
        am = amostras(grade())
        txt = cabecalho() + "".join(linha(*x) for x in am[:5]) + "1793840000.000000 12.0 io x 0\n" \
            + "".join(linha(*x) for x in am[5:])
        (self.tmp / "m.txt").write_text(txt, encoding="ascii", newline="\n")
        rc, out, err = rodar("fase", "--amostras", self.tmp / "m.txt", "--saida", self.saida, "--fase", "noite1")
        self.assertEqual(rc, 4)
        self.assertIn("m.txt:7:", err)

    def test_sem_cabecalho_e_versao_desconhecida(self):
        matriz(self.saida, [(1, "noite1", "S1-sync-none")])
        am = amostras(grade())
        for cab, msg in (("", "linha 1"), (cabecalho().replace(" v1 ", " v2 "), "versao")):
            (self.tmp / "c.txt").write_text(cab + "".join(linha(*x) for x in am), encoding="ascii", newline="\n")
            rc, out, err = rodar("fase", "--amostras", self.tmp / "c.txt", "--saida", self.saida, "--fase", "noite1")
            self.assertEqual(rc, 4)
            self.assertIn(msg, err)

    def test_dois_boot_id(self):
        matriz(self.saida, [(1, "noite1", "S1-sync-none")])
        am = amostras(grade())
        a = escrever(self.tmp / "a.txt", am[:100], cab=cabecalho(boot="b1"))
        b = escrever(self.tmp / "b.txt", am[100:], cab=cabecalho(boot="b2", pid="200"))
        rc, out, err = rodar("fase", "--amostras", a, "--amostras", b, "--saida", self.saida, "--fase", "noite1")
        self.assertEqual(rc, 4)
        self.assertIn("boot", err)

    def test_espelho_e_final_do_mesmo_segmento(self):
        # o espelho e um prefixo da copia final (mesmo cabecalho): conta uma vez, fica o mais longo
        matriz(self.saida, [(1, "noite1", "S1-sync-none")])
        execucao(self.saida, 1, "S1-sync-none")
        am = amostras(grade())
        a = escrever(self.tmp / "espelho.txt", am[:200])
        b = escrever(self.tmp / "final.txt", am)
        rc, out, err = rodar("fase", "--amostras", a, "--amostras", b, "--saida", self.saida, "--fase", "noite1")
        self.assertEqual(rc, 0, err)
        self.assertEqual(tsv_fase(self.saida, "noite1")[0]["veredito"], "ok")


# ============================================================================= 6. parada

class T6Parada(Base):
    def test_regras_em_sequencia(self):
        O, H = "ok", "h4a"
        casos = [
            ([H, H, H], False),
            ([H, H, H, H], True),
            ([H, O, H, H, H, O], False),
            ([H, O, H, O, H, O, H, O, H], True),                     # 5 em 9
            ([H, O, O, H, O, O, H, O, O, H, O, O, H], False),        # 5 em 13: nunca 5 em 12
            ([H, O, O, H, O, O, H, O, O, H, O, H], True),            # 5 em 12
            ([O] * 30 + [H, "h4b", "h4a+h4b", H], True),               # 4 seguidas, codigos misturados
            ([H, "sem-ancora", H, H, H], False),                     # sem-ancora nao e h4 e quebra a sequencia
            (["h4c"], True),
            ([O, O, "h4a+h4c"], True),
            ([], False),
        ]
        for seq, esperado in casos:
            self.assertEqual(h4.avaliar_parada(seq)[0], esperado, seq)

    def _campanha(self, h4_por_i, ordem_lancamento):
        """execucoes i=1..n, ancoras espacadas de 400 s; h4 (degrau de 2 s em +100) nas do conjunto;
        lancamento_ms na ordem dada."""
        n = len(ordem_lancamento)
        cels = ["S1-sync-none"] * n
        matriz(self.saida, [(i, "noite1", cels[i - 1]) for i in range(1, n + 1)])
        tempos, degraus = [], {}
        for pos, i in enumerate(ordem_lancamento):
            anc_ms = ANC_MS + pos * 400000
            execucao(self.saida, i, cels[i - 1], anc_ms=anc_ms, lanc_ms=anc_ms - 12000)
            if i in h4_por_i:
                degraus[anc_ms * 1000 + 100 * US] = 2 * US
        tempos = grade(ini_s=-20, fim_s=n * 400 + 10)
        return escrever(self.tmp / "a.txt", amostras(tempos, degraus=degraus))

    def test_ordem_de_lancamento_e_nao_dos_diretorios(self):
        # por i (ordem dos diretorios) seriam 4 h4 seguidas (1..4); na ordem de lancamento nao ha 4 seguidas
        arq = self._campanha({1, 2, 3, 4}, [1, 5, 2, 6, 3, 4])
        rc, out, err = rodar("fase", "--amostras", arq, "--saida", self.saida, "--fase", "noite1")
        self.assertEqual(rc, 0, out + err)
        self.assertEqual([x["i"] for x in tsv_fase(self.saida, "noite1")], ["1", "5", "2", "6", "3", "4"])
        self.tearDown(); self.setUp()
        # por i nao ha 4 seguidas (1, 3, 5, 6 com 2 e 4 ok); na ordem de lancamento 1, 3, 5, 6 vem seguidas
        arq = self._campanha({1, 3, 5, 6}, [1, 3, 5, 6, 2, 4])
        rc, out, err = rodar("fase", "--amostras", arq, "--saida", self.saida, "--fase", "noite1")
        self.assertEqual(rc, 30, out + err)
        self.assertIn("PARADA H4: regra 1: 4 execucoes h4 seguidas", out)
        self.assertIn("parada=sim", out)
        js = json.loads((self.saida / "h4" / "h4-noite1.json").read_text(encoding="utf-8"))
        self.assertTrue(js["parada"]["parada"])


# ============================================================================= 7. recalibracao (C3 §3.4)

def converter(origem, destino, ordem):
    """Conversor SO DE TESTE: arquivo bruto do gate S7 (blocos separados por '|', na ordem dada) -> formato v1.
    O uptime e sintetico (acompanha o relogio de parede, sem salto); cpu/memoria/disco ausentes viram 0/'-'."""
    out = [cabecalho(ident="diag-20261105T000000Z")]
    t0 = None
    with open(origem, encoding="ascii") as fh:
        for l in fh:
            partes = l.rstrip("\n").split("|")
            ts, primeiro = partes[0].split(None, 1)
            blocos = dict(zip(ordem, [primeiro] + partes[1:]))

            def psi(nome):
                b = blocos.get(nome)
                if b is None:
                    return 0, 0
                return (int(re.search(r"some avg10=\S+ avg60=\S+ avg300=\S+ total=(\d+)", b).group(1)),
                        int(re.search(r"full avg10=\S+ avg60=\S+ avg300=\S+ total=(\d+)", b).group(1)))
            t = h4.dec_para_us(ts)
            t0 = t if t0 is None else t0
            io_s, io_f = psi("io")
            cpu_s, cpu_f = psi("cpu")
            mem_s, mem_f = psi("memory")
            if "diskstats" in blocos:
                c = blocos["diskstats"].split()
                disco = " ".join(c[3:20])
            else:
                disco = "-"
            out.append("%s %s io %d %d cpu %d %d mem %d %d sda %s\n" % (
                h4.seg(t), h4.seg(t - t0 + 100 * US), io_s, io_f, cpu_s, cpu_f, mem_s, mem_f, disco))
    Path(destino).write_text("".join(out), encoding="ascii", newline="\n")
    return str(destino)


class T7Recalibracao(unittest.TestCase):
    CASOS = (("vs7r-psi-cpu-io-mem.txt", ("cpu", "io", "memory"), 9.32, 5.737),
             ("s7t-psi-io-cpu.txt", ("io", "cpu"), 5.17, 1.006),
             ("diag2-psi-io-diskstats.txt", ("io", "diskstats"), 1.73, 1.014))

    def test_copias_conferem_com_o_leiame(self):
        lista = {}
        for l in (DADOS / "LEIAME.txt").read_text(encoding="ascii").splitlines():
            m = re.match(r"^([0-9a-f]{64})  (\S+)  ", l)
            if m:
                lista[m.group(2)] = m.group(1)
        self.assertEqual(len(lista), 3)
        for nome, sha in lista.items():
            self.assertEqual(hashlib.sha256((DADOS / nome).read_bytes()).hexdigest(), sha, nome)

    def test_piores_janelas_e_maiores_intervalos(self):
        tmp = Path(tempfile.mkdtemp(prefix="h4-recal-"))
        try:
            for nome, ordem, pj, mi in self.CASOS:
                s = h4.Serie([converter(DADOS / nome, tmp / (nome + ".v1"), ordem)])
                r = h4.analisar_serie(s)
                self.assertAlmostEqual(r["pior_janela_us"] / US, pj, delta=0.01, msg=nome)
                self.assertAlmostEqual(r["maior_intervalo_us"] / US, mi, delta=0.001, msg=nome)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


# ============================================================================= 8. repor

class T8Repor(Base):
    CELS = [(1, "noite1", "S1-sync-none"), (2, "noite1", "S2-async-none"), (3, "noite1", "S1-sync-F1"),
            (4, "noite1", "S2-sync-F3"), (61, "noite2", "S1-async-none")]

    def preparar(self, h4_linhas, checagens, fase="noite1"):
        self.mat = matriz(self.saida, self.CELS)
        (self.saida / "h4").mkdir(exist_ok=True)
        txt = "\t".join(h4.COLUNAS) + "\n"
        for i, cel, v in h4_linhas:
            l = {c: "-" for c in h4.COLUNAS}
            l.update({"i": str(i), "celula": cel, "veredito": v})
            txt += "\t".join(l[c] for c in h4.COLUNAS) + "\n"
        (self.saida / "h4" / ("h4-%s.tsv" % fase)).write_text(txt, encoding="ascii", newline="\n")
        for i, cel, v, *fs in checagens:
            checagem(self.saida, i, cel, v, *(fs or ["campanha"]))

    def tsv_rep(self):
        return (self.saida / "reposicoes" / "reposicoes.tsv").read_text(encoding="ascii")

    def test_proximo_i_livre_semente_e_formato(self):
        self.preparar([(2, "S2-async-none", "h4a"), (3, "S1-sync-F1", "h4b"), (4, "S2-sync-F3", "ok")],
                      [(1, "S1-sync-none", "RECUSADA"), (2, "S2-async-none", "ACEITA"), (3, "S1-sync-F1", "ACEITA"),
                       (4, "S2-sync-F3", "ACEITA"), (120, "S1-sync-none", "ACEITA", "reposicao")])
        rep = self.saida / "reposicoes"
        rep.mkdir()
        (rep / "reposicoes.tsv").write_text(h4.CABECALHO_REPOSICOES
                                            + "120\treposicao\treposicoes\t-\t0\tS1-sync-none\tS1\tsync\tnone\t62000\n"
                                            + "121\treposicao\treposicoes\t-\t1\tS1-async-none\tS1\tasync\tnone\t62100\n",
                                            encoding="ascii", newline="\n")
        (self.saida / "exec-0125-S1-async-none").mkdir()                  # dir sem linha no TSV: i = 126
        (self.saida / "exec-0130-S1-async-none.abortada-20261105T000000Z").mkdir()   # abortada nao conta
        (self.saida / "exec-1000-S1-sync-none").mkdir()                    # piloto nao conta
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 0, err)
        linhas = self.tsv_rep().splitlines()
        self.assertEqual(linhas[3], "126\treposicao\treposicoes\t-\t2\tS2-async-none\tS2\tasync\tnone\t62600")
        self.assertEqual(linhas[4], "127\treposicao\treposicoes\t-\t3\tS1-sync-F1\tS1\tsync\tF1\t62700")
        self.assertEqual(len(linhas), 5)
        sha = hashlib.sha256((rep / "reposicoes.tsv").read_bytes()).hexdigest()
        self.assertIn("reposicoes.tsv SHA-256 %s" % sha, out)
        reg = h4.ler_tsv(self.saida / "h4" / "reposicoes-h4.tsv")
        self.assertEqual([(x["i_h4"], x["celula"], x["i_reposicao"]) for x in reg],
                         [("2", "S2-async-none", "126"), ("3", "S1-sync-F1", "127")])

    def test_idempotente(self):
        self.preparar([(2, "S2-async-none", "h4a")], [(2, "S2-async-none", "ACEITA")])
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 0, err)
        antes = self.tsv_rep()
        self.assertEqual(antes, h4.CABECALHO_REPOSICOES + "120\treposicao\treposicoes\t-\t0\tS2-async-none\tS2\tasync\tnone\t62000\n")
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 0, err)
        self.assertEqual(self.tsv_rep(), antes)
        self.assertIn("nenhuma execucao h4 ACEITA a repor", out)
        self.assertEqual(len(h4.ler_tsv(self.saida / "h4" / "reposicoes-h4.tsv")), 1)

    def test_recusada_nao_e_reposta_pelo_h4(self):
        self.preparar([(1, "S1-sync-none", "h4a"), (2, "S2-async-none", "h4a")],
                      [(1, "S1-sync-none", "RECUSADA"), (2, "S2-async-none", "ACEITA")])
        rep = self.saida / "reposicoes"
        rep.mkdir()
        (rep / "reposicoes.tsv").write_text(h4.CABECALHO_REPOSICOES
                                            + "120\treposicao\treposicoes\t-\t0\tS1-sync-none\tS1\tsync\tnone\t62000\n",
                                            encoding="ascii", newline="\n")
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 0, err)
        self.assertIn("nao reposta pelo h4.py: i=1 S1-sync-none veredito do codigo=RECUSADA", out)
        linhas = self.tsv_rep().splitlines()
        self.assertEqual(linhas[1:], ["120\treposicao\treposicoes\t-\t0\tS1-sync-none\tS1\tsync\tnone\t62000",
                                      "121\treposicao\treposicoes\t-\t1\tS2-async-none\tS2\tasync\tnone\t62100"])

    def test_recusa_ainda_nao_reposta_pelo_runner(self):
        self.preparar([(2, "S2-async-none", "h4a")], [(1, "S1-sync-none", "RECUSADA"), (2, "S2-async-none", "ACEITA")])
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 3)
        self.assertIn("S1-sync-none (i=1)", err)
        self.assertFalse((self.saida / "reposicoes" / "reposicoes.tsv").exists())

    def test_teto_de_30(self):
        self.preparar([(2, "S2-async-none", "h4a"), (3, "S1-sync-F1", "h4a")],
                      [(2, "S2-async-none", "ACEITA"), (3, "S1-sync-F1", "ACEITA")])
        reg = self.saida / "h4" / "reposicoes-h4.tsv"
        reg.write_text("\t".join(h4.COLUNAS_REGISTRO) + "\n"
                       + "".join("%d\tS1-sync-none\t%d\t2026-11-05T00:00:00Z\n" % (900 + k, 120 + k) for k in range(29)),
                       encoding="ascii", newline="\n")
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 3)
        self.assertIn("teto de 30", err)
        self.assertFalse((self.saida / "reposicoes" / "reposicoes.tsv").exists())
        # com 28 ja feitas, as 2 novas dao exatamente 30: aceito
        reg.write_text("\t".join(h4.COLUNAS_REGISTRO) + "\n"
                       + "".join("%d\tS1-sync-none\t%d\t2026-11-05T00:00:00Z\n" % (900 + k, 120 + k) for k in range(28)),
                       encoding="ascii", newline="\n")
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 0, err)

    def test_piloto_nunca_e_dry_run_nao_grava(self):
        self.preparar([(1000, "S1-sync-none", "h4a")], [(1000, "S1-sync-none", "ACEITA", "piloto")], fase="piloto")
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 0, err)
        self.assertFalse((self.saida / "reposicoes" / "reposicoes.tsv").exists())
        self.tearDown(); self.setUp()
        self.preparar([(2, "S2-async-none", "h4a")], [(2, "S2-async-none", "ACEITA")])
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat, "--dry-run")
        self.assertEqual(rc, 0, err)
        self.assertIn("DRY-RUN 120\treposicao", out)
        self.assertFalse((self.saida / "reposicoes").exists())
        self.assertFalse((self.saida / "h4" / "reposicoes-h4.tsv").exists())

    def test_reposicao_h4_que_cai_de_novo_em_h4(self):
        self.preparar([(120, "S2-async-none", "h4b")], [(120, "S2-async-none", "ACEITA", "reposicao")],
                      fase="reposicoes")
        rep = self.saida / "reposicoes"
        rep.mkdir()
        (rep / "reposicoes.tsv").write_text(h4.CABECALHO_REPOSICOES
                                            + "120\treposicao\treposicoes\t-\t0\tS2-async-none\tS2\tasync\tnone\t62000\n",
                                            encoding="ascii", newline="\n")
        rc, out, err = rodar("repor", "--saida", self.saida, "--matriz", self.mat)
        self.assertEqual(rc, 0, err)
        self.assertTrue(self.tsv_rep().endswith("121\treposicao\treposicoes\t-\t1\tS2-async-none\tS2\tasync\tnone\t62100\n"))


# ============================================================================= 9. visao

class T9Visao(Base):
    def montar(self):
        matriz(self.saida, [(1, "noite1", "S1-sync-none"), (2, "noite1", "S2-async-none"),
                            (3, "noite1", "S1-sync-F1")])
        for i, cel in ((1, "S1-sync-none"), (2, "S2-async-none"), (3, "S1-sync-F1")):
            d = execucao(self.saida, i, cel)
            checagem(self.saida, i, cel, "ACEITA")
            (d / "rep-001.csv").write_text("op_id\n%d\n" % i, encoding="ascii")
            (d / "sub").mkdir()
            (d / "sub" / "x.txt").write_text("x", encoding="ascii")
        (self.saida / "exec-0004-S2-sync-F3.abortada-20261105T000000Z").mkdir()
        (self.saida / "reposicoes").mkdir()
        (self.saida / "reposicoes" / "reposicoes.tsv").write_text(h4.CABECALHO_REPOSICOES, encoding="ascii")
        (self.saida / "noite1").mkdir()
        (self.saida / "noite1" / "runner.log").write_text("log", encoding="ascii")
        (self.saida / "h4").mkdir()
        txt = "\t".join(h4.COLUNAS) + "\n"
        for i, cel, v in ((1, "S1-sync-none", "ok"), (2, "S2-async-none", "h4a+h4b"), (3, "S1-sync-F1", "ok")):
            l = {c: "-" for c in h4.COLUNAS}
            l.update({"i": str(i), "celula": cel, "veredito": v})
            txt += "\t".join(l[c] for c in h4.COLUNAS) + "\n"
        (self.saida / "h4" / "h4-noite1.tsv").write_text(txt, encoding="ascii", newline="\n")
        exc = self.tmp / "excluir.txt"
        exc.write_text("# descartada-infra (h)\n3\n", encoding="ascii")
        return exc

    def test_visao(self):
        exc = self.montar()
        antes = arvore_sha(self.saida)
        dest = self.tmp / "visao"
        rc, out, err = rodar("visao", "--saida", self.saida, "--destino", dest, "--excluir", exc)
        self.assertEqual(rc, 0, err)
        self.assertEqual(arvore_sha(self.saida), antes)
        nomes = sorted(p.name for p in dest.iterdir())
        self.assertEqual(nomes, ["VISAO-H4.txt", "exec-0001-S1-sync-none",
                                 "exec-0004-S2-sync-F3.abortada-20261105T000000Z", "matriz.tsv", "reposicoes"])
        orig = self.saida / "exec-0001-S1-sync-none" / "rep-001.csv"
        link = dest / "exec-0001-S1-sync-none" / "rep-001.csv"
        self.assertTrue(os.path.samefile(orig, link))
        self.assertTrue((dest / "exec-0001-S1-sync-none" / "sub" / "x.txt").is_file())
        v = (dest / "VISAO-H4.txt").read_text(encoding="utf-8")
        self.assertIn("exec-0002-S2-async-none  descartada-infra (h4) h4a+h4b em h4-noite1.tsv", v)
        self.assertIn("exec-0003-S1-sync-F1  descartada-infra (h) (--excluir excluir.txt)", v)
        self.assertIn(hashlib.sha256((self.saida / "h4" / "h4-noite1.tsv").read_bytes()).hexdigest(), v)
        self.assertIn("versao h4/1", v)

    def test_recusa_destino_existente_ou_dentro_da_saida(self):
        self.montar()
        (self.tmp / "ja").mkdir()
        antes = arvore_sha(self.saida)
        rc, out, err = rodar("visao", "--saida", self.saida, "--destino", self.tmp / "ja")
        self.assertEqual(rc, 2)
        rc, out, err = rodar("visao", "--saida", self.saida, "--destino", self.saida / "visao")
        self.assertEqual(rc, 2)
        self.assertEqual(arvore_sha(self.saida), antes)


# ============================================================================= 10. constantes cruzadas

class T10Constantes(unittest.TestCase):
    def test_bloco_do_prompt(self):
        self.assertEqual((h4.VERSAO, h4.JANELA_S, h4.LIMIAR_H4A_S, h4.LIMIAR_H4B_S, h4.LIMIAR_SALTO_S),
                         ("h4/1", 10.0, 1.5, 3.0, 1.0))
        self.assertEqual((h4.MEDICAO_INI_S, h4.MEDICAO_FIM_S, h4.SEG_F1_ANTES_S, h4.SEG_F1_DEPOIS_S,
                          h4.F1_REINICIO_PADRAO_S, h4.COBERTURA_FOLGA_S), (44.0, 300.0, 2.0, 60.0, 109.0, 3.0))
        self.assertEqual((h4.PARADA_JANELA_EXEC, h4.PARADA_MIN_H4, h4.PARADA_SEGUIDAS, h4.TETO_REPOSICOES_H4),
                         (12, 5, 4, 30))

    def test_runner(self):
        l49 = (RAIZ / "scripts" / "run-rebaseline.sh").read_text(encoding="utf-8").splitlines()[48]
        m = re.match(r"^readonly S0=(\d+) PASSO_SEMENTE=(\d+) I_REPOSICAO_INICIAL=(\d+) I_PILOTO=(\d+)\b", l49)
        self.assertIsNotNone(m, l49)
        self.assertEqual(tuple(map(int, m.groups())), (h4.S0, h4.PASSO_SEMENTE, h4.I_REPOSICAO_INICIAL, h4.I_PILOTO))

    def test_rebaseline_py(self):
        ls = (AQUI / "rebaseline.py").read_text(encoding="utf-8").splitlines()
        self.assertRegex(ls[34], r"^S0 = %d\b" % h4.S0)
        self.assertRegex(ls[35], r"^PASSO_SEMENTE = %d\b" % h4.PASSO_SEMENTE)
        self.assertIn(">= %d" % h4.I_REPOSICAO_INICIAL, ls[625])
        self.assertIn("< %d" % h4.I_PILOTO, ls[625])

    def test_cabecalho_do_reposicoes_igual_ao_do_runner(self):
        txt = (RAIZ / "scripts" / "run-rebaseline.sh").read_text(encoding="utf-8")
        self.assertIn("printf '%s'" % h4.CABECALHO_REPOSICOES.replace("\t", "\\t").replace("\n", "\\n"), txt)

    def test_nenhum_limiar_por_linha_de_comando(self):
        for sub in ("fase", "repor", "visao"):
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                rc = h4.main([sub, "--help"])
            self.assertEqual(rc, 0)
            opcoes = set(re.findall(r"--[a-z][a-z0-9-]*", out.getvalue()))
            self.assertLessEqual(opcoes, {"--help", "--amostras", "--saida", "--fase", "--matriz", "--dry-run",
                                          "--destino", "--excluir"}, sub)
        rc, out, err = rodar("fase", "--amostras", "x", "--saida", "y", "--fase", "noite1", "--limiar-h4a", "2")
        self.assertEqual(rc, 2)


if __name__ == "__main__":
    unittest.main()
