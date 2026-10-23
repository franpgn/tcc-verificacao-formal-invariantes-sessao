"""Testes da analise do rebaseline (analysis/rebaseline.py): A1..A10 e A12 (checar/estado).

Pre-registro: docs/pre-registro-rebaseline-aprovado-2026-10-23.md (PR), DT-009, DT-010 e DT-011.
As fixtures sao geradas no proprio teste em diretorios temporarios (nenhuma versionada).
A12 (regressao contra o smoke B-43.9) e opt-in: le REBASELINE_ORACULO_DIR e pula com aviso se ausente.

Uso: python analysis/test_rebaseline.py   (de qualquer diretorio; sem -I, para enxergar numpy/scipy do usuario)
"""
import csv
import json
import math
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent
sys.path.insert(0, str(AQUI))
import rebaseline as rb  # noqa: E402

P0 = 0.05
Z = 3.2905
S0 = 50000
SCRIPT = AQUI / "rebaseline.py"


# ============================================================================ fixtures

def gerar_matriz(destino):
    """Matriz real do PR (scripts/rebaseline_matriz.py); devolve {i: linha}."""
    r = subprocess.run([sys.executable, "-I", str(RAIZ / "scripts" / "rebaseline_matriz.py"), "gerar",
                        "--ordem-seed", "231023", "--s0", "50000", "--saida", str(destino)],
                       capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    with open(destino, newline="") as fh:
        return {int(x["i"]): x for x in csv.DictReader(fh, delimiter="\t")}


def escrever_tsv(destino, linhas):
    cols = ["i", "fase", "noite", "rodada", "posicao", "celula", "cenario", "modo", "falha", "seed"]
    txt = "\t".join(cols) + "\n" + "".join("\t".join(str(l[c]) for c in cols) + "\n" for l in linhas)
    Path(destino).write_bytes(txt.encode("ascii"))


def linhas_csv(cenario, n_iso=3000, inv_cada=20, n_login=120, n_logout_iso=60, n_inc=60, n_reset=30,
               erros_iso=0, erros_iso_terco=0, extras=(), lat_base=100_000):
    """Eventos sinteticos: Validates isolados uniformes no tempo (INVALID a cada inv_cada), demais
    operacoes intercaladas. erros_iso Validates isolados ERROR_TRANSPORT no terco erros_iso_terco.
    extras: tuplas (operation, return_code, key, latencia_ns) acrescentadas no meio."""
    outros = []
    for j in range(n_login):
        outros += [("Login", "OK", "user-%d-%d" % (j % 4, j)), ("Validate", "VALID", "tri-%06d" % j),
                   ("Logout", "NONE", "tri-%06d" % j)]
    outros += [("Logout", "NONE", "sid-%06d~1" % j) for j in range(n_logout_iso)]
    outros += [("IncrementFailure", "COUNTED", "lim-%d" % j) for j in range(n_inc)]
    if cenario == "S1":
        outros += [("ResetFailures", "NONE", "lim-%d" % j) for j in range(n_reset)]
    outros += list(extras)
    iso = [("Validate", "INVALID" if j % inv_cada == 0 else "VALID", "sid-%06d~1" % j) for j in range(n_iso)]
    seq = []
    passo = max(1, len(iso) // max(1, len(outros)))
    k = 0
    for j, ev in enumerate(iso):
        seq.append(ev)
        if j % passo == 0 and k < len(outros):
            seq.append(outros[k])
            k += 1
    seq += outros[k:]
    if erros_iso:
        terco = len(seq) // 3
        ini = terco * erros_iso_terco + 1
        err = [("Validate", "ERROR_TRANSPORT", "sid-%06d~1" % j) for j in range(erros_iso)]
        seq = seq[:ini] + err + seq[ini:]
    rows = []
    t = 1_800_000_000_000_000_000
    for n, ev in enumerate(seq):
        op, rc, key = ev[0], ev[1], ev[2]
        lat = ev[3] if len(ev) > 3 else lat_base + (n % 97) * 1000
        rows.append({"op_id": n, "operation": op, "start_ns": t + n * 1_000_000, "end_ns": t + n * 1_000_000 + lat,
                     "replica": "127.0.0.1:11222", "return_code": rc, "key": key})
    return rows


def escrever_csv(caminho, rows):
    cols = ["op_id", "operation", "start_ns", "end_ns", "replica", "return_code", "key"]
    with open(caminho, "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        for r in rows:
            w.writerow(r)


def stdout_sintetico(cenario, modo, seed, rows, rep=1, k_token=None, sem_marco=0, perdida=0, revertido=0,
                     violacoes_detectadas=0, reset_depois="0,0", sem_linha=()):
    n_iso = sum(1 for r in rows if r["operation"] == "Validate" and r["key"].startswith("sid-"))
    lo_iso = sum(1 for r in rows if r["operation"] == "Logout" and r["key"].startswith("sid-"))
    n_login_ok = sum(1 for r in rows if r["operation"] == "Login" and r["return_code"] == "OK")
    reg = len(rows)
    k = round(n_iso * P0) if k_token is None else k_token
    caches = "sessions,counters" if modo == "sync" else "sessions-async,counters-async"
    linhas = [
        "Configuracao: Cli{scenario=%s, duracao=300s, ops=0, rep=%d, threads=4, seed=%d, csvDir=x, warmupMin=30s, "
        "dryRun=false}" % (cenario, rep, seed),
        "Replicacao: replicacao=%s caches=%s" % (modo, caches),
        "Mistura: mix=v3 q=0.05",
        "Contrato CSV: v2",
        "== Repeticao 1 / 1 ==",
        "Reset: reset_state=on caches=%s antes=10,3 depois=%s ms=5" % (caches, reset_depois),
        "Carga: mix=v3 cache=sessions slots=100000 entradas=200000 lotes=200 ms=2000",
        "Warm-up ate +30s; medicao a partir de +44s; total 300s",
        "Total de operacoes: %d" % (reg - 2 * n_login_ok),
        "Violacoes detectadas: %d" % violacoes_detectadas,
        "Auditor: i1_concorrentes=3 i4_concorrentes=0",
        "Invalidacao: perdida=%d conflito_esgotado=0" % perdida,
        "Auditor I2: concorrentes=0 janela_cascata=0 cascata_incompleta=0 sem_marco=%d" % sem_marco,
        "Auditor total: i1_concorrentes=4 i4_concorrentes=0 i2_concorrentes=0",
        "Login: revertido_por_bloqueio=%d" % revertido,
        "Mistura v3: validate_isolados=%d token_antigo=%d logout_isolados=%d reposicoes=%d reposicao_omitida=0 "
        "reposicao_erro=0" % (n_iso, k, lo_iso, lo_iso),
        "login_ok               n=%d (descartados=0) p50=1ns p95=1ns p99=1ns p999=1ns" % n_login_ok,
        "Eventos gravados em x (registrados=%d, descartados_warmup=0)" % reg,
        "Violacoes gravadas em y (total=0)",
    ]
    linhas = [l for l in linhas if not any(l.startswith(s) for s in sem_linha)]
    return "\n".join(linhas) + "\n"


def montar_exec(raiz, linha, *, seed=None, rep=1, rows=None, stdout_kw=None, execucao=None, violacoes=(),
                viol_cols=("instante", "invariante", "chave", "mensagem"), ispn=(1, 1, 1), sanidade="OK",
                verificador_exit=0, workload_exit=0, portao_ok=True, injetor_log=None, sem=()):
    """Diretorio exec-<iiii>-<celula> com todos os brutos de uma execucao ACEITAVEL por padrao."""
    i = int(linha["i"])
    cen, modo, falha = linha["cenario"], linha["modo"], linha["falha"]
    d = Path(raiz) / ("exec-%04d-%s" % (i, linha["celula"]))
    d.mkdir(parents=True)
    rows = linhas_csv(cen) if rows is None else rows
    escrever_csv(d / "rep-001.csv", rows)
    seed = int(linha["seed"]) if seed is None else seed
    (d / "stdout.log").write_text(stdout_sintetico(cen, modo, seed, rows, rep=rep, **(stdout_kw or {})),
                                  encoding="utf-8")
    with open(d / "violacoes-rep-001.csv", "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(viol_cols)
        for v in violacoes:
            w.writerow(v)
    (d / "carga-rep-001.json").write_text(json.dumps({"tamanho_final": 200000, "veredito": "OK", "duracao_ms": 2000}))
    (d / "estado-rep-001.json").write_text(json.dumps({"veredito": "OK", "tamanhos_depois": [0, 0]}))
    (d / "meta-csv-rep-001.json").write_text(json.dumps({"contrato_csv": 2}))
    (d / "sanidade.json").write_text(json.dumps({"veredito": sanidade, "motivos": []}))
    (d / "ispn006010.json").write_text(json.dumps({"por_no": dict(zip(("isn1", "isn2", "isn3"), ispn))}))
    (d / "portao.json").write_text(json.dumps({"ok": portao_ok, "amostras": []}))
    ex = {"i": i, "celula": linha["celula"], "fase": linha["fase"], "cenario": cen, "modo": modo, "falha": falha,
          "seed": int(linha["seed"]), "workload_exit": workload_exit, "verificador_exit": verificador_exit,
          "injetor_exit": None, "lancamento_utc": "2026-10-24T01:00:00.000Z",
          "ancora_utc": "2026-10-24T01:00:15.000Z", "disparo_utc": None, "fim_utc": "2026-10-24T01:05:30.000Z",
          "inj_inicio_offset_s": None, "netem_amostras": [], "netem_pos": None, "cluster_pos_f1": None,
          "recusa_k6": None}
    if falha == "F1":
        ex.update({"injetor_exit": 0, "inj_inicio_offset_s": 49.012, "disparo_utc": "2026-10-24T01:01:04.012Z",
                   "cluster_pos_f1": {"ok": True, "espera_s": 35.0}})
        if injetor_log is None:
            injetor_log = ("[2026-10-24T01:01:04Z] inject-crash.sh: sudo podman stop isn2\n"
                           "[2026-10-24T01:01:04Z] inject-crash.sh: aguardando 60s\n"
                           "[2026-10-24T01:01:04Z] inject-crash.sh: sudo podman start isn2\n"
                           "[2026-10-24T01:01:04Z] inject-crash.sh: OK: F1 (crash) injetada\n")
    elif falha == "F3":
        ex.update({"injetor_exit": 0, "inj_inicio_offset_s": 49.005, "disparo_utc": "2026-10-24T01:01:04.005Z",
                   "netem_amostras": [{"alvo_s": 70, "offset_s": 70.1, "netem": True,
                                       "saida": "qdisc netem 8001: root refcnt 2 limit 1000 delay 20ms  13ms"},
                                      {"alvo_s": 280, "offset_s": 280.2, "netem": True, "saida": "qdisc netem"}],
                   "netem_pos": {"netem": False, "remocao_extra": False}})
        if injetor_log is None:
            injetor_log = "[x] inject-jitter.sh: OK: F3 (jitter) injetada por 251s e removida; sem netem residual\n"
    ex.update(execucao or {})
    (d / "execucao.json").write_text(json.dumps(ex))
    if injetor_log is not None:
        (d / "injetor.log").write_text(injetor_log)
    for arq in sem:
        (d / arq).unlink()
    return d


def rodar(*args):
    # checar/estado rodam na VM com python3 -I (so biblioteca padrao); tabelas/piloto rodam no host, onde
    # numpy/scipy podem estar no site-packages do usuario, que o -I desliga.
    iso = ["-I"] if args and args[0] in ("checar", "estado") else []
    r = subprocess.run([sys.executable] + iso + [str(SCRIPT)] + [str(a) for a in args], capture_output=True,
                       text=True)
    return r.returncode, r.stdout, r.stderr


def checagem(d):
    return json.loads((Path(d) / "checagem.json").read_text(encoding="utf-8"))


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.raiz = Path(self.tmp.name)
        self.saida = self.raiz / "rebaseline"
        self.saida.mkdir()
        self.tsv = self.raiz / "matriz.tsv"
        self.mat = gerar_matriz(self.tsv)

    def tearDown(self):
        self.tmp.cleanup()

    def linha(self, celula, fase="campanha", n=0):
        """n-esima linha (por i) da matriz com essa celula e fase."""
        ls = sorted((l for l in self.mat.values() if l["celula"] == celula and l["fase"] == fase),
                    key=lambda x: int(x["i"]))
        return ls[n]

    def checar(self, d, tsv=None):
        rc, out, err = rodar("checar", "--exec", d, "--matriz", tsv or self.tsv)
        self.assertIn(rc, (0, 10, 20), err)
        return rc, checagem(d)


# ============================================================================ A1, A2

class A1Percentil(unittest.TestCase):

    def test_posto_mais_proximo(self):
        self.assertEqual(rb.percentil(list(range(1, 101)), 0.99), 99)
        self.assertEqual(rb.percentil(list(range(1, 101)), 0.50), 50)
        self.assertEqual(rb.percentil([5, 1, 3], 0.50), 3)
        self.assertEqual(rb.percentil([10, 20, 30, 40], 0.95), 40)
        self.assertEqual(rb.percentil(list(range(1, 1001)), 0.99), 990)
        self.assertEqual(rb.percentil(list(range(1, 1001)), 0.999), 999)
        self.assertEqual(rb.percentil(list(range(1000, 0, -1)), 0.99), 990)
        # caso em que a interpolacao linear daria valor fora da amostra
        self.assertEqual(rb.percentil([0, 1000] + [1] * 98, 0.99), 1)
        self.assertEqual(rb.percentil(list(range(0, 2000, 2)), 0.99), 1978)
        self.assertIsNone(rb.percentil([], 0.99))

    def test_minimo_de_amostras(self):
        self.assertEqual(rb.N_MIN_P99, 1000)
        self.assertEqual(rb.N_MIN_P999, 10000)
        self.assertIsNone(rb.p99(list(range(999))))
        self.assertEqual(rb.p99(list(range(1, 1001))), 990)
        self.assertIsNone(rb.p999(list(range(9999))))
        self.assertEqual(rb.p999(list(range(1, 10001))), 9990)


class A2Desfechos(Base):

    def test_so_validos_nos_percentis(self):
        extras = ([("Login", "BLOCKED", "user-9-%d" % j, 10**10) for j in range(50)]
                  + [("Validate", "NOT_FOUND", "sid-9%05d~1" % j, 10**10) for j in range(40)]
                  + [("Validate", "ERROR_TRANSPORT", "tri-9%05d" % j, 10**10) for j in range(30)])
        rows = linhas_csv("S1", n_login=1100, extras=extras)
        p = self.raiz / "a2.csv"
        escrever_csv(p, rows)
        st = rb.estatisticas_por_operacao(p, "S1")
        login = st["Login"]
        self.assertEqual(login["n"], 1100)
        self.assertLess(login["p99"], 10**9)
        self.assertEqual(login["contagens"], {"OK": 1100, "BLOCKED": 50})
        self.assertAlmostEqual(login["taxas"]["BLOCKED"], 50 / 1150)
        val = st["Validate"]
        n_val_validos = sum(1 for r in rows if r["operation"] == "Validate" and r["return_code"] in ("VALID", "INVALID"))
        self.assertEqual(val["n"], n_val_validos)
        self.assertLess(val["p99"], 10**9)
        self.assertEqual(val["contagens"]["NOT_FOUND"], 40)
        self.assertEqual(val["contagens"]["ERROR_TRANSPORT"], 30)
        self.assertEqual(st["Validate-isolado"]["contagens"]["NOT_FOUND"], 40)
        self.assertEqual(st["Validate-triade"]["contagens"]["ERROR_TRANSPORT"], 30)
        lat_login = sorted(r["end_ns"] - r["start_ns"] for r in rows if r["operation"] == "Login"
                           and r["return_code"] == "OK")
        self.assertEqual(login["p99"], lat_login[math.ceil(0.99 * len(lat_login)) - 1])
        self.assertIn("ResetFailures", st)
        self.assertNotIn("ResetFailures", rb.estatisticas_por_operacao(p, "S2"))
        janela = (rows[-1]["start_ns"] - rows[0]["start_ns"]) / 1e9
        self.assertAlmostEqual(login["vazao_por_s"], 1100 / janela)
        self.assertIsNone(st["IncrementFailure"]["p99"])  # n = 60 < 1000


# ============================================================================ A3: R7

def c1_c2_antigo(rows):
    """Definicao antiga (runs/b43-9-smoke/analise.py): todos os Validate isolados no denominador."""
    starts = [r["start_ns"] for r in rows]
    t0, t1 = min(starts), max(starts)
    iso = [(r["start_ns"], 1 if r["return_code"] == "INVALID" else 0) for r in rows
           if r["operation"] == "Validate" and r["key"].startswith("sid-")]
    tot, inv = [0] * 3, [0] * 3
    for s, x in iso:
        k = min(int((s - t0) / ((t1 - t0) / 3)), 2)
        tot[k] += 1
        inv[k] += x
    fr = [round(100.0 * b / a, 4) for a, b in zip(tot, inv)]
    return round(abs(fr[0] - fr[2]), 4), round(100.0 * sum(x for _, x in iso) / len(iso), 4)


class A3DenominadorR7(Base):

    def test_f1_com_erros(self):
        lin = self.linha("S1-sync-F1")
        rows = linhas_csv("S1", erros_iso=500, erros_iso_terco=0)
        d = montar_exec(self.saida, lin, rows=rows)
        rc, ch = self.checar(d)
        m = ch["metricas"]
        self.assertTrue(m["C1"]["ok"], m["C1"])
        self.assertTrue(m["C2"]["ok"], m["C2"])
        self.assertEqual(m["C2"]["denominador"], 3000)
        self.assertEqual(m["validate_isolados_outros"], {"ERROR_TRANSPORT": 500})
        dif_antigo, frac_antiga = c1_c2_antigo(rows)
        self.assertGreater(dif_antigo, 1.0)   # sem R7 o C1 reprovaria
        self.assertLess(frac_antiga, 4.5)     # e o C2 tambem
        self.assertEqual(ch["veredito"], "ACEITA", ch["motivos"])
        self.assertEqual(rc, 0)

    def test_sem_erros_coincide_com_a_antiga(self):
        rows = linhas_csv("S1")
        dif_antigo, frac_antiga = c1_c2_antigo(rows)
        for cel in ("S1-sync-F1", "S1-sync-none"):
            d = montar_exec(self.saida, self.linha(cel), rows=rows)
            _, ch = self.checar(d)
            self.assertEqual(ch["metricas"]["C1"]["dif_1_3_pp"], dif_antigo, cel)
            self.assertEqual(ch["metricas"]["C2"]["frac_pct"], frac_antiga, cel)

    def test_none_usa_a_definicao_antiga(self):
        rows = linhas_csv("S1", erros_iso=500, erros_iso_terco=0)
        d = montar_exec(self.saida, self.linha("S1-sync-none"), rows=rows)
        rc, ch = self.checar(d)
        dif_antigo, frac_antiga = c1_c2_antigo(rows)
        self.assertEqual(ch["metricas"]["C1"]["dif_1_3_pp"], dif_antigo)
        self.assertEqual(ch["metricas"]["C2"]["frac_pct"], frac_antiga)
        self.assertEqual(ch["veredito"], "RECUSADA")
        self.assertEqual(rc, 10)


# ============================================================================ A4: C3

def meia(n):
    return Z * math.sqrt(P0 * (1 - P0) / n)


class A4C3(unittest.TestCase):

    def test_constantes(self):
        self.assertEqual((rb.P0, rb.Z, rb.THREADS, rb.S0), (0.05, 3.2905, 4, 50000))
        self.assertEqual((rb.BOOT_SEED, rb.N_BOOT, rb.MIN_EXEC_CONTRASTE, rb.ALFA), (231024, 10000, 8, 0.05))
        self.assertEqual(rb.INJ_OFFSET, 49)
        self.assertEqual(rb.INJ_TOL, (48.0, 51.0))

    def test_dentro(self):
        n = 245_204
        r = rb.c3_ic(round(n * P0), n, n, 4)
        self.assertTrue(r["ok"] and r["ok_ic"] and r["ok_csv"])
        self.assertIsNone(r["sanidade"])

    def test_fora_por_cima_e_por_baixo(self):
        n = 1_000_000
        r = rb.c3_ic(math.ceil(n * (P0 + meia(n))) + 1, n, n, 4)
        self.assertFalse(r["ok"])
        self.assertGreater(r["desvio_pp"], r["meia_largura_pp"])
        r = rb.c3_ic(math.floor(n * (P0 - meia(n))) - 1, n, n, 4)
        self.assertFalse(r["ok"])
        self.assertLess(r["desvio_pp"], -r["meia_largura_pp"])
        self.assertTrue(rb.c3_ic(math.floor(n * (P0 + meia(n))), n, n, 4)["ok"])

    def test_n_insuficiente(self):
        r = rb.c3_ic(100, 2000, 2000, 4)
        self.assertFalse(r["ok"])
        self.assertEqual(r["sanidade"], "n insuficiente para o IC")
        self.assertFalse(rb.c3_ic(0, 0, 0, 4)["ok"])
        self.assertIsNone(rb.c3_ic(round(2106 * P0), 2106, 2106, 4)["sanidade"])

    def test_meia_largura_245204(self):
        r = rb.c3_ic(12_260, 245_204, 245_204, 4)
        self.assertAlmostEqual(r["meia_largura_pp"], 0.145, delta=0.0005)

    def test_fronteira(self):
        n = 245_204
        self.assertTrue(rb.c3_ic(round(n * P0), n, n - 4, 4)["ok"])
        self.assertFalse(rb.c3_ic(round(n * P0), n, n - 5, 4)["ok"])


# ============================================================================ A5: K5

class A5K5(Base):

    def _nf(self):
        return linhas_csv("S1", extras=[("Validate", "NOT_FOUND", "sid-8%05d~1" % j) for j in range(3)])

    def test_none_recusa(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"), rows=self._nf())
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
        self.assertFalse(ch["criterios"]["K5"]["ok"])
        self.assertEqual(ch["criterios"]["K5"]["papel"], "recusa")
        self.assertTrue(any("K5" in m for m in ch["motivos"]))

    def test_none_b438_recusa(self):
        d = montar_exec(self.saida, self.linha("S2-async-none"), stdout_kw={"perdida": 1})
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))

    def test_f1_f3_so_reportam(self):
        for cel in ("S1-sync-F1", "S1-async-F3"):
            d = montar_exec(self.saida, self.linha(cel), rows=self._nf(), stdout_kw={"perdida": 2})
            rc, ch = self.checar(d)
            self.assertEqual((rc, ch["veredito"]), (0, "ACEITA"), (cel, ch["motivos"]))
            self.assertFalse(ch["criterios"]["K5"]["ok"])
            self.assertEqual(ch["criterios"]["K5"]["papel"], "reportado")


# ============================================================================ A6: K7 / R-S3

class A6K7(Base):

    def test_semente_errada_recusa(self):
        lin = self.linha("S1-sync-none")
        d = montar_exec(self.saida, lin, seed=int(lin["seed"]) + 100)
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
        self.assertFalse(ch["criterios"]["K7"]["ok"])

    def test_rep_errado_recusa(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"), rep=2)
        rc, ch = self.checar(d)
        self.assertEqual(ch["veredito"], "RECUSADA")

    def test_matriz_com_semente_fora_da_regra(self):
        lin = dict(self.linha("S2-sync-none"))
        lin["seed"] = str(int(lin["seed"]) + 1)
        tsv = self.raiz / "adulterada.tsv"
        escrever_tsv(tsv, [lin])
        d = montar_exec(self.saida, lin)
        rc, ch = self.checar(d, tsv)
        self.assertEqual(ch["veredito"], "RECUSADA")
        self.assertFalse(ch["criterios"]["K7"]["ok"])

    def test_par_repetido_para_o_estado(self):
        a = self.linha("S1-sync-none")
        b = self.linha("S2-sync-none")
        da = montar_exec(self.saida, a)
        db = montar_exec(self.saida, b, seed=int(a["seed"]))
        self.assertEqual(self.checar(da)[0], 0)
        self.assertEqual(self.checar(db)[0], 10)
        rc, out, err = rodar("estado", "--saida", self.saida, "--matriz", self.tsv)
        self.assertEqual(rc, 20, err)
        est = json.loads((self.saida / "estado.json").read_text())
        self.assertFalse(est["paradas"]["R-S3"]["ok"])


# ============================================================================ A7: K9 / K10

class A7K9(Base):

    def test_sync_none_sem_marco_para(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"), stdout_kw={"sem_marco": 1})
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (20, "PARADA"))
        self.assertEqual(ch["criterios"]["K9"]["papel"], "parada")

    def test_sync_none_revertido_para(self):
        d = montar_exec(self.saida, self.linha("S2-sync-none"), stdout_kw={"revertido": 1})
        self.assertEqual(self.checar(d)[0], 20)

    def test_sync_none_i2_qualquer_classe_para(self):
        d = montar_exec(self.saida, self.linha("S2-sync-none"),
                        violacoes=[("2026-10-24T01:02:00Z", "I2_JANELA_CASCATA", "user-1", "x")])
        self.assertEqual(self.checar(d)[0], 20)

    def test_sync_none_sem_linha_auditor_para(self):
        d = montar_exec(self.saida, self.linha("S2-sync-none"), stdout_kw={"sem_linha": ("Auditor I2:",)})
        self.assertEqual(self.checar(d)[0], 20)

    def test_async_none_sem_marco_reporta(self):
        d = montar_exec(self.saida, self.linha("S1-async-none"), stdout_kw={"sem_marco": 1})
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (0, "ACEITA"), ch["motivos"])
        self.assertEqual(ch["criterios"]["K10"]["papel"], "reportado")
        self.assertEqual(ch["criterios"]["K10"]["valor"]["sem_marco"], 1)

    def test_sync_f1_i1_reporta(self):
        d = montar_exec(self.saida, self.linha("S1-sync-F1"),
                        violacoes=[("2026-10-24T01:02:00Z", "I1", "sid-000001~1", "x")])
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (0, "ACEITA"), ch["motivos"])
        self.assertFalse(ch["criterios"]["K9"]["ok"])
        self.assertEqual(ch["criterios"]["K9"]["papel"], "reportado")
        self.assertEqual(ch["criterios"]["K9"]["valor"]["I1"], 1)

    def test_k8_ispn_para(self):
        d = montar_exec(self.saida, self.linha("S1-async-F3"), ispn=(1, 2, 1))
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (20, "PARADA"))
        self.assertFalse(ch["criterios"]["K8"]["ok"])


# ============================================================================ A8: violacoes

class A8Violacoes(Base):

    def test_contagem(self):
        p = self.raiz / "v.csv"
        with open(p, "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["instante", "invariante", "chave", "mensagem", "classificacao"])
            w.writerow(["2026-10-24T01:02:00Z", "I2_JANELA_CASCATA", "u", "m", "REAL"])
            w.writerow(["2026-10-24T01:02:00Z", "I2", "u", "m", "ESPERADA-POR-DESENHO"])
            w.writerow(["2026-10-24T01:02:00Z", "I2_CASCATA_INCOMPLETA", "u", "m", ""])
        v = rb.contar_violacoes(p)
        self.assertEqual(v["violacoes"]["I2"], 0)
        self.assertEqual(v["total"], 0)
        self.assertEqual(v["por_invariante"]["I2_JANELA_CASCATA"], 1)
        with open(p, "a", newline="") as fh:
            csv.writer(fh).writerow(["2026-10-24T01:02:01Z", "I2", "u", "m", "REAL"])
        v = rb.contar_violacoes(p)
        self.assertEqual((v["violacoes"]["I2"], v["total"]), (1, 1))

    def test_stdout_nao_e_usado(self):
        d = montar_exec(self.saida, self.linha("S1-async-none"), stdout_kw={"violacoes_detectadas": 7})
        rc, ch = self.checar(d)
        self.assertEqual(ch["metricas"]["violacoes"]["total"], 0)
        self.assertEqual(ch["metricas"]["violacoes_stdout_informativo"], 7)
        self.assertEqual(rc, 0)


# ============================================================================ A9: K6

class A9K6(Base):

    def _k6(self, cel, **kw):
        d = montar_exec(self.saida, self.linha(cel, n=kw.pop("n", 0)), **kw)
        rc, ch = self.checar(d)
        return rc, ch

    def test_offsets(self):
        casos = [(47.9, 10), (51.1, 10), (49.2, 0), (48.0, 0), (51.0, 0)]
        for n, (off, esperado) in enumerate(casos):
            for cel in ("S1-sync-F1", "S2-async-F3"):
                rc, ch = self._k6(cel, n=n, execucao={"inj_inicio_offset_s": off})
                self.assertEqual(rc, esperado, (cel, off, ch["motivos"]))
                self.assertEqual(ch["criterios"]["K6"]["ok"], esperado == 0)
                self.assertEqual(ch["criterios"]["K6"]["papel"], "recusa")

    def test_f3_sem_netem_na_amostra(self):
        am = [{"alvo_s": 70, "offset_s": 70.1, "netem": False, "saida": "qdisc noqueue 0: root"},
              {"alvo_s": 280, "offset_s": 280.1, "netem": True, "saida": "qdisc netem"}]
        rc, ch = self._k6("S1-sync-F3", execucao={"netem_amostras": am})
        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
        rc, ch = self._k6("S1-sync-F3", n=1, execucao={"netem_amostras": am[1:]})
        self.assertEqual(rc, 10)

    def test_f3_netem_depois(self):
        rc, ch = self._k6("S1-sync-F3", execucao={"netem_pos": {"netem": True, "remocao_extra": True}})
        self.assertEqual(rc, 10)

    def test_f1_injetor_exit_4(self):
        rc, ch = self._k6("S1-sync-F1", execucao={"injetor_exit": 4})
        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
        self.assertFalse(ch["criterios"]["K6"]["ok"])

    def test_f1_log_sem_start(self):
        rc, ch = self._k6("S1-sync-F1", injetor_log="[x] inject-crash.sh: sudo podman stop isn2\n")
        self.assertEqual(rc, 10)

    def test_sem_ancora(self):
        rc, ch = self._k6("S1-sync-none", execucao={"ancora_utc": None, "recusa_k6": "sem ancora",
                                                     "workload_exit": 137})
        self.assertEqual(ch["veredito"], "RECUSADA")
        self.assertFalse(ch["criterios"]["K6"]["ok"])


# ============================================================================ A10: estado

class A10Estado(Base):

    def estado(self):
        rc, out, err = rodar("estado", "--saida", self.saida, "--matriz", self.tsv)
        self.assertIn(rc, (0, 20), err)
        return rc, json.loads((self.saida / "estado.json").read_text()), out

    def recusar(self, lin, **kw):
        d = montar_exec(self.saida, lin, sanidade="FALHA", **kw)
        self.assertEqual(self.checar(d)[0], 10)

    def aceitar(self, lin, **kw):
        d = montar_exec(self.saida, lin, **kw)
        self.assertEqual(self.checar(d)[0], 0, checagem(d)["motivos"])

    def test_tudo_aceito(self):
        self.aceitar(self.linha("S1-sync-none"))
        self.assertEqual(self.estado()[0], 0)

    def test_a_duas_recusas_na_mesma_celula(self):
        self.recusar(self.linha("S1-sync-none", n=0))
        rc, est, _ = self.estado()
        self.assertEqual(rc, 0)
        self.recusar(self.linha("S1-sync-none", n=1))
        rc, est, _ = self.estado()
        self.assertEqual(rc, 20)
        self.assertFalse(est["paradas"]["a"]["ok"])

    def test_b_mais_de_seis_recusas(self):
        cels = sorted({l["celula"] for l in self.mat.values()})
        for c in cels[:6]:
            self.recusar(self.linha(c))
        self.assertEqual(self.estado()[0], 0)
        self.recusar(self.linha(cels[6]))
        rc, est, _ = self.estado()
        self.assertEqual(rc, 20)
        self.assertFalse(est["paradas"]["b"]["ok"])
        self.assertTrue(est["paradas"]["a"]["ok"])

    def test_c_parada_de_execucao(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"), stdout_kw={"sem_marco": 1})
        self.assertEqual(self.checar(d)[0], 20)
        rc, est, _ = self.estado()
        self.assertEqual(rc, 20)
        self.assertFalse(est["paradas"]["c"]["ok"])

    def test_d_duas_reprovacoes_de_c3(self):
        self.recusar(self.linha("S1-sync-none"))  # recusa sem C3
        self.recusar_c3(self.linha("S2-sync-none"))
        self.assertEqual(self.estado()[0], 0)
        self.recusar_c3(self.linha("S1-async-none"))
        rc, est, _ = self.estado()
        self.assertEqual(rc, 20)
        self.assertFalse(est["paradas"]["d"]["ok"])

    def recusar_c3(self, lin):
        d = montar_exec(self.saida, lin, stdout_kw={"k_token": 300})  # 10 % de 3000
        rc, ch = self.checar(d)
        self.assertEqual(rc, 10)
        self.assertFalse(ch["metricas"]["C3"]["ok"])

    def test_d_agregado_fora(self):
        cels = sorted({l["celula"] for l in self.mat.values()})
        for c in cels[:10]:
            self.aceitar(self.linha(c), stdout_kw={"k_token": 180})  # 6 %: dentro por execucao
        rc, est, _ = self.estado()
        self.assertEqual(rc, 20)
        self.assertFalse(est["paradas"]["d"]["ok"])
        self.assertFalse(est["c3_agregado"]["ok_ic"])

    def test_piloto_nunca_conta(self):
        for c in ("S1-sync-none", "S1-sync-F1", "S2-sync-none", "S2-sync-F1", "S1-async-none", "S1-async-F1",
                  "S2-async-none"):
            self.recusar(self.linha(c, fase="piloto"))
        d = montar_exec(self.saida, self.linha("S1-sync-F3", fase="piloto"), stdout_kw={"sem_marco": 1})
        self.checar(d)
        rc, est, _ = self.estado()
        self.assertEqual(rc, 0)
        self.assertEqual(est["total_recusas"], 0)

    def test_listar_reposicoes(self):
        a = self.linha("S2-async-F1", n=0)
        b = self.linha("S1-sync-none", n=0)
        self.recusar(a)
        self.recusar(b)
        rc, out, err = rodar("estado", "--saida", self.saida, "--matriz", self.tsv, "--listar-reposicoes")
        self.assertEqual(rc, 0, err)
        esperado = sorted([(int(a["i"]), a["celula"]), (int(b["i"]), b["celula"])])
        self.assertEqual([l.split("\t") for l in out.strip().split("\n")],
                         [[c, str(i)] for i, c in esperado])
        # reposicao ja feita sai da lista
        rep = dict(a, i="120", fase="reposicao", noite="reposicoes", rodada="-", posicao="0",
                   seed=str(S0 + 100 * 120))
        (self.saida / "reposicoes").mkdir()
        escrever_tsv(self.saida / "reposicoes" / "reposicoes.tsv", [rep])
        d = montar_exec(self.saida, rep)
        self.assertEqual(self.checar(d, self.saida / "reposicoes" / "reposicoes.tsv")[0], 0)
        rc, out, err = rodar("estado", "--saida", self.saida, "--matriz", self.tsv, "--listar-reposicoes")
        self.assertEqual(out.strip().split("\n"), ["%s\t%s" % (b["celula"], b["i"])])


# ============================================================================ uso e arquivos

class Uso(Base):

    def test_arquivo_obrigatorio_ausente(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"), sem=("execucao.json",))
        rc, out, err = rodar("checar", "--exec", d, "--matriz", self.tsv)
        self.assertEqual(rc, 2)

    def test_uso_invalido(self):
        self.assertEqual(rodar()[0], 2)
        self.assertEqual(rodar("checar")[0], 2)
        self.assertEqual(rodar("estado", "--saida", self.raiz / "nao-existe", "--matriz", self.tsv)[0], 2)

    def test_csv_ausente_e_recusa_nao_uso(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"), workload_exit=5, sem=("rep-001.csv",))
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
        self.assertFalse(ch["criterios"]["K2"]["ok"])

    def test_k1_portao_e_reset(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"), portao_ok=False)
        self.assertEqual(self.checar(d)[0], 10)
        d = montar_exec(self.saida, self.linha("S2-sync-none"), stdout_kw={"reset_depois": "0,3"})
        rc, ch = self.checar(d)
        self.assertEqual(rc, 10)
        self.assertFalse(ch["criterios"]["K1"]["ok"])

    def test_aceita_tem_todos_os_criterios(self):
        d = montar_exec(self.saida, self.linha("S1-sync-none"))
        rc, ch = self.checar(d)
        self.assertEqual((rc, ch["veredito"]), (0, "ACEITA"), ch["motivos"])
        self.assertEqual(sorted(ch["criterios"]), ["K%d" % k for k in sorted(range(1, 11), key=str)])
        for k, v in ch["criterios"].items():
            self.assertIn(v["papel"], ("recusa", "parada", "reportado"), k)
            self.assertTrue({"valor", "limite", "ok", "papel"} <= set(v), k)
        self.assertEqual(ch["seed_lido"], int(self.linha("S1-sync-none")["seed"]))


# ============================================================================ A11: bootstrap

def exec_heterogenea(rng, centro, n=1000):
    """Latencias de uma execucao com p99 perto de 'centro' (ns)."""
    return [int(centro * (0.5 + 0.5 * rng.random())) for _ in range(n)]


class A11Bootstrap(unittest.TestCase):

    def setUp(self):
        try:
            import numpy  # noqa: F401
            import scipy  # noqa: F401
        except ImportError:
            self.skipTest("numpy/scipy ausentes (tabelas roda no host)")

    def dados(self):
        a = {("S1", "sync", "none", "Login"): [1.0 + 0.1 * j for j in range(10)],
             ("S1", "sync", "F1", "Login"): [2.0 + 0.1 * j for j in range(10)],
             ("S1", "sync", "F3", "Login"): [1.5 + 0.2 * j for j in range(9)],
             ("S1", "async", "none", "Login"): [0.9 + 0.1 * j for j in range(8)],
             ("S1", "async", "F1", "Login"): [1.9 + 0.1 * j for j in range(7)],
             ("S2", "sync", "none", "Validate"): [0.3 + 0.01 * j for j in range(10)],
             ("S2", "sync", "F1", "Validate"): [0.31 + 0.01 * j for j in range(10)]}
        return a

    def test_determinismo_e_ordem(self):
        d = self.dados()
        r1 = rb.calcular_contrastes(d)
        r2 = rb.calcular_contrastes(d)
        self.assertEqual(r1, r2)
        import random
        rotulos = sorted(r1)
        random.Random(7).shuffle(rotulos)
        r3 = rb.calcular_contrastes(d, ordem=rotulos)
        self.assertEqual(r1, r3)
        self.assertEqual(rb.rotulos_contrastes(d), sorted(r1))

    def test_rotulos_e_indices(self):
        r = rb.calcular_contrastes(self.dados())
        self.assertIn("F1-none|S1|sync|Login", r)
        self.assertIn("async-sync|S1|none|Login", r)
        for k, rot in enumerate(sorted(r)):
            self.assertEqual(r[rot]["k"], k)

    def test_nao_estimado_com_7(self):
        r = rb.calcular_contrastes(self.dados())
        self.assertEqual(r["F1-none|S1|async|Login"]["estimado"], False)
        self.assertEqual(r["F1-none|S1|async|Login"]["motivo"], "não estimado (n insuficiente)")
        self.assertIsNone(r["F1-none|S1|async|Login"]["dif"])
        self.assertTrue(r["F1-none|S1|sync|Login"]["estimado"])
        self.assertTrue(r["async-sync|S1|none|Login"]["estimado"])   # 8 x 10
        self.assertFalse(r["async-sync|S1|F1|Login"]["estimado"])    # 7 x 10

    def test_diferenca_conhecida_no_ic(self):
        r = rb.calcular_contrastes(self.dados())["F1-none|S1|sync|Login"]
        self.assertAlmostEqual(r["dif"], 1.0)
        self.assertLessEqual(r["ic95"][0], 1.0)
        self.assertGreaterEqual(r["ic95"][1], 1.0)
        self.assertLess(r["p_mann_whitney"], 0.05)
        r = rb.calcular_contrastes(self.dados())["F1-none|S2|sync|Validate"]
        self.assertGreater(r["p_mann_whitney"], 0.05)

    def test_reamostra_execucoes_e_nao_eventos(self):
        import numpy as np
        g = np.random.default_rng(11)
        centros_a = [1e6 * (1 + j) for j in range(8)]     # execucoes muito diferentes entre si
        centros_b = [1e6 * (1.5 + j) for j in range(8)]
        a = [exec_heterogenea(g, c) for c in centros_a]
        b = [exec_heterogenea(g, c) for c in centros_b]
        dif, lo, hi = rb.bootstrap_diferenca(a, b, 0)
        pa = [rb.p99(x) for x in a]
        pb = [rb.p99(x) for x in b]
        self.assertAlmostEqual(dif, float(np.mean(pb) - np.mean(pa)))
        sd = math.sqrt(np.var(pa, ddof=1) / 8 + np.var(pb, ddof=1) / 8)
        # IC por execucoes: largura da ordem de 3,9 sd; por eventos ficaria muito mais estreito
        self.assertGreater(hi - lo, 2.0 * sd)
        self.assertLess(hi - lo, 6.0 * sd)
        self.assertEqual(rb.bootstrap_diferenca(pa, pb, 0), (dif, lo, hi))


# ============================================================================ A13: tabelas

class A13Tabelas(Base):

    def setUp(self):
        super().setUp()
        try:
            import numpy  # noqa: F401
            import scipy  # noqa: F401
        except ImportError:
            self.skipTest("numpy/scipy ausentes (tabelas roda no host)")

    def montar(self, cel, n, desloc_login):
        lin = self.linha(cel, n=n)
        rows = linhas_csv("S1", n_iso=3000, n_login=1100, lat_base=1_000_000 + 7_000 * n)
        for r in rows:
            if r["operation"] == "Login":
                r["end_ns"] += desloc_login
        d = montar_exec(self.saida, lin, rows=rows,
                        execucao={"jar_sha256": "ab" * 32, "matriz_sha256": "cd" * 32})
        gravar = {"i": int(lin["i"]), "celula": cel, "fase": "campanha", "cenario": "S1", "modo": "sync",
                  "falha": lin["falha"], "veredito": "ACEITA", "motivos": []}
        (d / "checagem.json").write_text(json.dumps(gravar))

    def test_tabela5(self):
        for n in range(8):
            self.montar("S1-sync-none", n, 0)
            self.montar("S1-sync-F1", n, 5_000_000)
        # uma execucao recusada nao entra
        lin = self.linha("S1-sync-F1", n=9)
        d = montar_exec(self.saida, lin, rows=linhas_csv("S1", n_login=1100, lat_base=10**9))
        (d / "checagem.json").write_text(json.dumps({"i": int(lin["i"]), "celula": "S1-sync-F1", "fase": "campanha",
                                                     "veredito": "RECUSADA", "motivos": ["x"]}))
        dest = self.raiz / "tabelas"
        rc, out, err = rodar("tabelas", "--saida", self.saida, "--matriz", self.tsv, "--destino", dest)
        self.assertEqual(rc, 0, err)
        for f in ("rebaseline-resultados.json", "por-execucao.csv", "contrastes.csv", "tabela5-f1.tex",
                  "tabela6-f3.tex", "tabela-async-sync.tex"):
            self.assertTrue((dest / f).exists(), f)
        tex = (dest / "tabela5-f1.tex").read_text(encoding="utf-8")
        self.assertIn("SHA-256", tex)
        self.assertIn("ab" * 32, tex)
        self.assertIn("{,}", tex)
        linhas = {(l.split("&")[0].strip(), l.split("&")[1].strip()): l for l in tex.splitlines()
                  if l.count("&") >= 6}
        self.assertIn(("S1", "Login"), linhas)
        self.assertIn(("S2", "IncrementFailure"), linhas)
        self.assertNotIn(("S2", "ResetFailures"), linhas)
        login = linhas[("S1", "Login")]
        self.assertNotIn("(n.s.)", login)
        self.assertRegex(login, r"\+5\{,\}\d\d \[")
        self.assertIn("(n.s.)", linhas[("S1", "Validate")])
        self.assertIn("—", linhas[("S1", "IncrementFailure")])
        self.assertIn("—", linhas[("S1", "ResetFailures")])
        cols = [c.strip() for c in login.rstrip("\\ ").split("&")]
        self.assertEqual(cols[-3:], ["—", "—", "—"])   # ASYNC sem execucoes
        self.assertNotRegex(tex, r"\d\.\d")             # nenhum ponto decimal
        res = json.loads((dest / "rebaseline-resultados.json").read_text(encoding="utf-8"))
        self.assertEqual(res["execucoes_aceitas"], 16)
        c = res["contrastes"]["F1-none|S1|sync|Login"]
        self.assertTrue(c["estimado"])
        self.assertAlmostEqual(c["dif"] / 1e6, 5.0, delta=0.01)
        cel = res["celulas"]["S1-sync-F1"]["Login"]
        self.assertEqual(cel["n_execucoes_com_p99"], 8)


class Piloto(Base):

    def test_piloto(self):
        for cel in ("S1-sync-none", "S2-async-F3"):
            d = montar_exec(self.saida, self.linha(cel, fase="piloto"))
            self.checar(d)
        montar_exec(self.saida, self.linha("S1-sync-none", n=0))   # campanha nao entra no piloto
        rc, out, err = rodar("piloto", "--saida", self.saida, "--matriz", self.tsv)
        self.assertEqual(rc, 0, err)
        p = json.loads((self.saida / "piloto.json").read_text(encoding="utf-8"))
        self.assertEqual(sorted(p["celulas"]), ["S1-sync-none", "S2-async-F3"])
        c = p["celulas"]["S1-sync-none"]
        self.assertEqual(c["veredito"], "ACEITA")
        self.assertEqual(sum(c["validate_isolados_por_terco"]), 3000)
        self.assertGreater(c["chance_falsa_recusa_c1"], 0.0)
        self.assertLess(c["chance_falsa_recusa_c1"], 1.0)
        self.assertIn("IncrementFailure", c["operacoes_sem_p99"])
        self.assertAlmostEqual(c["duracao_real_s"], 330.0)
        self.assertTrue((self.saida / "piloto.md").exists())


# ============================================================================ A12: regressao opt-in

class A12Oraculo(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        d = os.environ.get("REBASELINE_ORACULO_DIR")
        if not d:
            raise unittest.SkipTest("A12 pulado: REBASELINE_ORACULO_DIR ausente (brutos do smoke B-43.9 fora do git)")
        cls.dir = Path(d)
        cls.ref = json.loads((cls.dir / "analise.json").read_text(encoding="utf-8"))

    def test_q3_q4_q5(self):
        for q, cen in (("Q3", "S1"), ("Q4", "S1"), ("Q5", "S2")):
            d = self.dir / q
            out = (d / "stdout.log").read_text(encoding="utf-8", errors="replace")
            m = rb.criterios_mistura(out, rb.ler_csv_eventos(d / "rep-001.csv"), "none")
            ref = self.ref[q]["criterios"]
            for k in ("terco1_pct", "terco2_pct", "terco3_pct"):
                self.assertAlmostEqual(m["C1"][k], ref["C1"][k], places=4, msg=(q, k))
            self.assertAlmostEqual(m["C1"]["dif_1_3_pp"], ref["C1"]["dif_1_3_pp"], places=4)
            self.assertEqual(m["C1"]["ok"], ref["C1"]["ok"])
            self.assertAlmostEqual(m["C2"]["frac_pct"], ref["C2"]["frac_pct"], places=4)
            self.assertEqual(m["C2"]["denominador"], self.ref[q]["validate_isolados_csv"])
            self.assertEqual(m["C2"]["ok"], ref["C2"]["ok"])
            for k in ("k", "n", "n_csv", "dif_stdout_csv", "ok_ic", "ok_csv", "ok"):
                self.assertEqual(m["C3"][k], ref["C3"][k], (q, k))
            self.assertAlmostEqual(m["C3"]["frac_pct"], ref["C3"]["frac_pct"], places=4)
            for k in ("logout_isolados", "reposicoes", "reposicao_omitida", "reposicao_erro", "logout_isolados_csv",
                      "dif_stdout_csv", "logout_noop_janela", "logout_isolados_not_found_csv", "ok"):
                self.assertEqual(m["C5"][k], ref["C5"][k], (q, k))
            print("A12 %s: C1 dif=%.4f C2=%.4f%% C3 k=%d n=%d C5 ok=%s (confere com analise.json)" % (
                q, m["C1"]["dif_1_3_pp"], m["C2"]["frac_pct"], m["C3"]["k"], m["C3"]["n"], m["C5"]["ok"]))


if __name__ == "__main__":
    unittest.main()
