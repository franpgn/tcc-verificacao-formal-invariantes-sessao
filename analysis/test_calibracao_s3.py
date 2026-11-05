"""Testes de analysis/calibracao_s3.py (W-9): regras 7.1 a 7.18 do pre-registro da calibracao S3.

Fonte das regras: docs/pre-registro-calibracao-s3-2026-11-05.md, secao 7 (branch docs/pre-registro-calibracao-s3,
DT-020), com as decisoes do Gestor de 05/11: criterio C-8 (zero ocorrencias, N e 3/N ao lado de cada limiar), regra
7.13 com o predicado da politica P1 (PoliticaPorAssinatura.java:158) e a assinatura sem resposta REST a parte, e
nenhuma regra olhando diferenca entre bracos (so braco A).

Dados sinteticos no formato real do orquestrador S3 (scripts/run-campanha-s3.sh, W-6): um diretorio
exec-NNNN-<celula>-A por execucao com execucao.json, checagem.json, rep-001.csv + meta-csv-rep-001.json,
observaveis-rep-001.jsonl (contrato JSONL 1.2: meta, topologia, defasagem, fim), decisoes-rep-001.jsonl,
violacoes-rep-001.csv, s3-acoes-rep-001.csv, convergencia-resumo-rep-001.json e marcadores.txt (celulas com falha).

Uso (da raiz do repositorio): python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"
Os testes de paridade com comparar_bracos.py e scipy rodam so com numpy/scipy (python -E -P); em -I sao pulados.
"""
import hashlib
import json
import math
import subprocess
import sys
import tempfile
import time
import unittest
from fractions import Fraction
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import calibracao_s3 as cal  # noqa: E402

SCRIPT = AQUI / "calibracao_s3.py"
T0 = 1_800_000_000_000_000_000          # origem_ns sintetica (inicio da medicao)
S = 1_000_000_000
MS = 1_000_000
ISN1, ISN2, ISN3 = "127.0.0.1:11222", "127.0.0.1:11223", "127.0.0.1:11224"
NOS = (ISN1, ISN2, ISN3)
RC_OK = {"Login": "OK", "Validate": "VALID", "Logout": "NONE"}

try:
    import numpy  # noqa: F401
    import scipy  # noqa: F401
    TEM_NUMPY = True
except ImportError:
    TEM_NUMPY = False


def iso(ns):
    """java.time.Instant.toString com 9 casas (o regex do comparar_bracos aceita de 0 a 9)."""
    seg, frac = divmod(ns, S)
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(seg)) + ".%09dZ" % frac


def iso_ms(ms):
    seg, frac = divmod(ms, 1000)
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(seg)) + ".%03dZ" % frac


def jl(linhas):
    return "".join(json.dumps(x, separators=(",", ":")) + "\n" for x in linhas)


def parametros_campanha(d, **mud):
    v = {"duracao_s": 300, "warmup_min_s": 30, "inj_offset_s": 49, "f1_no": "isn2", "f1_dur_s": 60,
         "f2_no": "isn3", "f2_dur_s": 90, "f4_no": "isn3", "f4_severidade": "N3", "f4_direcao": "out",
         "f4_dur_s": 90, "hotrod_timeout_ms": 2000, "convergencia_teto_s": 600, "convergencia_periodo_s": 5,
         "w_recovery_s": 191, "religamento_prazo_s": 60, "exec_teto_s": 1010, "encerramento_folga_s": 2,
         "volume_exec_mb": 14}
    v.update(mud)
    p = Path(d) / "campanha-s3-1.json"
    p.write_text(json.dumps({"esquema": "campanha-s3/1", "versao": "teste",
                             "parametros": {k: {"valor": x, "origem": "teste"} for k, x in v.items()}}),
                 encoding="utf-8")
    return p


def escrever_execucao(base, i, celula, *, veredito="ACEITA", braco="A", dur=100, ops=("Login", "Validate"),
                      lentas=None, erros=None, lacunas=None, top_anomala=(), morto=None, lag_teto=None,
                      atrasos=None, violacoes=(), marcadores=None, conv=("convergiu", 10.0), t_pre_ms=2000,
                      t_pos_s=13.0, previstas=0, extra_bytes=0, nome_dir=None):
    """Grava uma execucao sintetica e devolve o diretorio.

    Eventos: a cada 100 ms, uma operacao de cada nome de `ops` (latencia 1 ms; 10/s por operacao) e um Logout a
    cada 2 s (fica fora da referencia: n < 1000). lentas/erros/lacunas: {janela de 5 s: n} aplicados aos primeiros
    eventos elegiveis da janela (latencia 2 ms / ERROR_TRANSPORT nos ultimos / removidos). Topologia a cada 2 s
    (t = -9,5 + 2q s). top_anomala: intervalos [a, b) em s em que o isn3 informa 2 membros. morto = (no, a, b, v):
    o no nao responde em [a, b) e os outros informam 2 membros a partir de v. Defasagem: uma rodada a cada 5 s no
    inicio da janela + atraso (1 ms padrao), lag de no 1,5/1,2/1,0 ms (suspeito unico isn1); lag_teto = {rodada:
    no no teto de 2000 ms}. violacoes = [(t_s, invariante, classificacao)].
    """
    lentas, erros, lacunas = dict(lentas or {}), dict(erros or {}), dict(lacunas or {})
    lag_teto, atrasos = dict(lag_teto or {}), dict(atrasos or {})
    modo_txt, falha = celula.split("-")
    modo = modo_txt.lower()
    d = Path(base) / (nome_dir or ("exec-%04d-%s-%s" % (i, celula, braco)))
    d.mkdir(parents=True)
    t_fim_carga = T0 + dur * S

    def fase_de(t):
        if marcadores is None or t < T0 + int(marcadores[0] * S):
            return "antes"
        return "durante" if t <= T0 + int(marcadores[1] * S) else "recuperacao"

    # ---- eventos (rep-001.csv, contrato 2)
    evs = []
    for q in range(-100, dur * 10):
        bt = T0 + q * 100 * MS
        for k, op in enumerate(ops):
            evs.append([op, bt + (10 + 10 * k) * MS, 1 * MS, RC_OK.get(op, "OK")])
        if q % 20 == 0:
            evs.append(["Logout", bt + 80 * MS, 1 * MS, "NONE"])
    por_j = {}
    for e in evs:
        if e[0] != "Logout":
            por_j.setdefault((e[1] + e[2] - T0) // (5 * S), []).append(e)
    remover = set()
    for j, n in lacunas.items():
        remover.update(id(e) for e in por_j[j][:n])
    for j, n in lentas.items():
        for e in [x for x in por_j[j] if id(x) not in remover][:n]:
            e[2] = 2 * MS
    for j, n in erros.items():
        for e in [x for x in por_j[j] if id(x) not in remover][-n:]:
            e[3] = "ERROR_TRANSPORT"
    linhas = ["op_id,operation,start_ns,end_ns,replica,return_code,key,phase"]
    for n_op, e in enumerate(x for x in evs if id(x) not in remover):
        linhas.append("%d,%s,%d,%d,%s,%s,k%d,%s" % (n_op, e[0], e[1], e[1] + e[2], ISN1, e[3], n_op % 7, fase_de(e[1])))
    (d / "rep-001.csv").write_text("\n".join(linhas) + "\n", encoding="utf-8")
    (d / "meta-csv-rep-001.json").write_text('{"contrato_csv":2}', encoding="utf-8")

    # ---- observaveis-rep-001.jsonl (1.2)
    obs = [{"tipo": "meta", "esquema": "observaveis-controlador/1.2", "versao_programa": "teste", "rep": 1,
            "modo": "shadow" if braco == "A" else "active", "cenario": "S3", "replicacao": modo, "nos_esperados": 3,
            "param_versao": "teste", "param_sha256": "c" * 64,
            "parametros": {"w_ns": 5 * S, "n_lat": 100, "n_err": 50, "theta_lat_in": 0.05, "theta_lat_out": 0.02,
                           "theta_err_in": 0.02, "theta_err_out": 0.005, "k": 2, "m": 6, "cooldown_ns": 30 * S,
                           "estavel_ns": 30 * S, "hold_viol_ns": 120 * S},
            "referencia_arquivo": "ref.json", "referencia_sha256": "d" * 64, "tolerancia_ns": 100 * MS,
            "tick_ns": S, "origem_ns": T0, "atuador": "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)",
            "politica": "por-assinatura", "sonda_topologia": True, "sonda_defasagem": True,
            "parametros_defasagem": {"lag_in_ms": 2000.0, "lag_out_ms": 1000.0, "passo_leitura_ms": 20,
                                     "periodo_ns": 5 * S, "origem": {}}}]
    t = T0 - 9_500 * MS
    while t < t_fim_carga + 2 * S:
        rel = (t - T0) / S
        por_no = []
        for no in NOS:
            resp, n, mot, an = True, 3, "ok", False
            if morto is not None and morto[1] <= rel < morto[2]:
                if no == morto[0]:
                    resp, n, mot, an = False, -1, "timeout", True
                elif rel >= morto[3]:
                    n, mot, an = 2, "nos:2<3", True
            if no == ISN3 and any(a <= rel < b for a, b in top_anomala):
                n, mot, an = 2, "nos:2<3", True
            por_no.append({"no": no, "respondeu": resp, "numero_de_nos": n, "motivo": mot, "anomalo": an})
        resp_n = [x["numero_de_nos"] for x in por_no if x["respondeu"]]
        obs.append({"tipo": "topologia", "t_ns": t, "anomala": any(x["anomalo"] for x in por_no),
                    "nos_min": min(resp_n) if resp_n else 0, "por_no": por_no})
        t += 2 * S
    for j in range(dur // 5):
        ti = T0 + j * 5 * S + atrasos.get(j, 1 * MS)
        lpn = {ISN1: 1.5, ISN2: 1.2, ISN3: 1.0}
        if j in lag_teto:
            lpn = {no: 1.0 for no in NOS}
            lpn[lag_teto[j]] = 2000.0
        mx = max(lpn.values())
        sus = [no for no, v in lpn.items() if v == mx]
        obs.append({"tipo": "defasagem", "rodada": j + 10, "t_inicio_ns": ti, "t_ns": ti + 3 * MS, "teto_ms": 2000,
                    "lag_max_ms": mx, "no_suspeito": sus[0] if len(sus) == 1 else None, "lag_max_par_ms": mx,
                    "lag_por_no": [{"no": no, "lag_ms": v} for no, v in lpn.items()], "pares": [], "escritas": [],
                    "custo": {"escritas": 3, "leituras": 6, "erros_escrita": 0, "erros_leitura": 0}})
    obs.append({"tipo": "fim", "janelas": dur // 5, "linhas_perdidas": 0})
    (d / "observaveis-rep-001.jsonl").write_text(jl(obs), encoding="utf-8")

    # ---- decisoes-rep-001.jsonl
    dec = [{"tipo": "meta", "esquema": "decisoes-controlador/1.2", "modo": "shadow" if braco == "A" else "active",
            "cenario": "S3", "replicacao": modo, "param_sha256": "c" * 64, "origem_ns": T0,
            "atuador": "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)", "politica": "por-assinatura",
            "parametros_politica": {"k_lag": 2, "teto_lag_ms": 2000, "t_reb_ns": 60 * S, "cota_ac5": 1,
                                    "p2_habilitada": False, "origem": {}}}]
    for k in range(previstas):
        dec.append({"tipo": "acao", "operacao": "APLICAR", "executada": False, "modo": "shadow",
                    "acao": {"tipo": "AC-5", "caches": ["sessions-async"], "alvo": ISN3, "valor": None},
                    "origem": "POLITICA", "regra": "R2", "t_fim_janela_ns": T0 + (10 + k) * 5 * S,
                    "t_pedido_ns": T0 + (10 + k) * 5 * S + MS, "t_inicio_ns": None, "duracao_ns": None,
                    "resultado": "PREVISTA", "codigo_http": None, "lido": None, "erro": None,
                    "reversao": "orquestrador"})
    dec.append({"tipo": "fim", "decisoes": dur // 5, "linhas_perdidas": 0})
    (d / "decisoes-rep-001.jsonl").write_text(jl(dec), encoding="utf-8")

    # ---- violacoes-rep-001.csv (InvariantAuditor.dumpCsv com fase e classificacao)
    vl = ["instante,invariante,chave,mensagem,fase,classificacao"]
    for ts, inv, cl in violacoes:
        tn = T0 + int(round(ts * S))
        vl.append('%s,%s,u-1,"mensagem, com virgula",%s,%s' % (iso(tn), inv, fase_de(tn), cl))
    (d / "violacoes-rep-001.csv").write_text("\n".join(vl) + "\n", encoding="utf-8")

    # ---- s3-acoes-rep-001.csv (contrato S3 v2): uma acao a cada 250 ms
    s3 = ["t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns"]
    for q in range(-40, dur * 4):
        ta = T0 + q * 250 * MS + 5 * MS
        s3.append("%d,s3-u-001,Login,s3-u-001,OK_LOGIN,1,false,,%d,0" % (ta, ta + 2 * MS))
    (d / "s3-acoes-rep-001.csv").write_text("\n".join(s3) + "\n", encoding="utf-8")

    # ---- convergencia-resumo-rep-001.json
    estado, delta = conv
    t_conv = None if delta is None else t_fim_carga + int(round(delta * S))
    resumo = {"t_fim_ns": t_fim_carga, "convergiu": estado == "convergiu",
              "divergencia_persistente": estado == "divergente",
              "t_convergencia_ns": None if t_conv is None else t_conv - t_fim_carga,
              "t_convergencia_abs_ns": t_conv, "rodadas": 3, "rodadas_validas": 0 if estado == "sem_rodada_valida" else 3,
              "t_fim_carga_ns": t_fim_carga, "t_fim_controle_ns": t_fim_carga + 5 * MS,
              "convergencia_estado": estado, "t_sinal_fim_carga_ns": t_fim_carga, "religamento_ac5_alvo": None,
              "religamento_prazo_ns": 60 * S, "t_religamento_ns": None, "religamento_esgotado": False,
              "religamento_leituras": 0}
    (d / "convergencia-resumo-rep-001.json").write_text(json.dumps(resumo, indent=2), encoding="utf-8")

    if marcadores is not None:
        (d / "marcadores.txt").write_text("event=start t_epoch_ns=%d\nevent=end t_epoch_ns=%d\n"
                                          % (T0 + int(marcadores[0] * S), T0 + int(marcadores[1] * S)),
                                          encoding="utf-8")

    # ---- execucao.json e checagem.json (orquestrador)
    ancora_ms = T0 // MS - 44_000
    lanc_ms = ancora_ms - t_pre_ms
    fim_ns = (t_conv if t_conv is not None else t_fim_carga + 7 * S) + int(round(t_pos_s * S))
    ex = {"i": i, "fase": "calibracao", "celula": celula, "papel": "calibracao", "bloco": "-", "braco": braco,
          "controller": "shadow" if braco == "A" else "active", "modo": modo, "falha": falha, "seed": 300000 + 100 * i,
          "recriacao_ms": lanc_ms - 90_000, "lancamento_ms": lanc_ms, "lancamento_utc": iso_ms(lanc_ms),
          "ancora_ms": ancora_ms, "ancora_utc": iso_ms(ancora_ms), "fim_utc": iso_ms(fim_ns // MS),
          "workload_exit": 0, "teto_estourado": False}
    (d / "execucao.json").write_text(json.dumps(ex, indent=1), encoding="utf-8")
    (d / "checagem.json").write_text(json.dumps({"i": i, "fase": "calibracao", "celula": celula, "bloco": "-",
                                                 "braco": braco, "veredito": veredito, "motivos": [],
                                                 "avisos": []}), encoding="utf-8")
    if extra_bytes:
        (d / "stdout.log").write_bytes(b"x" * extra_bytes)
    return d


def volume_bytes(d):
    return sum(p.stat().st_size for p in Path(d).rglob("*") if p.is_file())


# Campanha base (pequena, mas com todas as celulas da calibracao):
#   ASYNC-none x3: excedencias (0,03; 0,05) | (0,02; 0,06) | (0,09; 0) -> maior minimo de 2 seguidas = 0,03;
#                  rodada 6 da exec 0 no teto (isn3, vista completa) -> ocorrencia contada com persistencia 1
#   ASYNC-F1 x2:   isn2 morto em [30, 60) s (os outros veem 2 membros a partir de 45 s); rodadas 6..11 no teto (isn2)
#                  -> assinatura sem resposta; na exec 1 a rodada 10 tem o isn3 no teto (o suspeito responde, o isn2
#                  nao: sem resposta) e a rodada 15 no teto com vista completa (contada)
#   ASYNC-F2 x2:   falha [20, 40] s; exec 0 com janelas altas depois do end em 8, 11 (violacao) e 16 -> m = 5;
#                  violacoes I1 30 s, I4 30,5 s, I2 REAL 57 s e I2_JANELA_CASCATA 90 s (nao conta)
#   SYNC-F2 x1, ASYNC-F4 x1, SYNC-none x2
def montar_campanha(base, mud=None):
    """{nome: kwargs} de cada execucao; mud = {nome: kwargs extras} sobrepoe."""
    spec = {
        "n0": (0, "ASYNC-none", dict(lentas={3: 3, 4: 5}, lag_teto={6: ISN3}, atrasos={2: 40 * MS},
                                     t_pre_ms=2400)),
        "n1": (1, "ASYNC-none", dict(lentas={7: 2, 8: 6}, t_pos_s=13.5)),
        "n2": (2, "ASYNC-none", dict(lentas={10: 9})),
        "s0": (3, "SYNC-none", dict()),
        "s1": (4, "SYNC-none", dict(extra_bytes=3 * 1024 * 1024 + 5)),
        "f1a": (5, "ASYNC-F1", dict(marcadores=(30, 60), morto=(ISN2, 30, 60, 45),
                                    lag_teto={j: ISN2 for j in range(6, 12)}, conv=("convergiu", 20.0),
                                    previstas=1)),
        "f1b": (6, "ASYNC-F1", dict(marcadores=(30, 60), morto=(ISN2, 30, 60, 45),
                                    lag_teto={**{j: ISN2 for j in range(6, 10)}, 10: ISN3, 15: ISN3},
                                    conv=("convergiu", 8.0))),
        "f2a": (7, "ASYNC-F2", dict(marcadores=(20, 40), top_anomala=[(20, 46), (80.5, 81)],
                                    violacoes=[(30.0, "I1", ""), (30.5, "I4", ""), (57.0, "I2", "REAL"),
                                               (90.0, "I2_JANELA_CASCATA", "")],
                                    conv=("convergiu", 15.0))),
        "f2b": (8, "ASYNC-F2", dict(marcadores=(20, 40), top_anomala=[(20, 44)], conv=("divergente", None))),
        "sf2": (9, "SYNC-F2", dict(marcadores=(20, 40), top_anomala=[(20, 44)], conv=("convergiu", 35.3))),
        "f4": (10, "ASYNC-F4", dict(marcadores=(20, 40), top_anomala=[(20, 41), (52.5, 53)],
                                    conv=("convergiu", 12.0))),
        # nao aceitas: entram so na lista de excluidas
        "rec": (11, "ASYNC-none", dict(veredito="RECUSADA", lentas={1: 50})),
        "dsc": (12, "ASYNC-F2", dict(veredito="DESCARTADA-INFRA", lag_teto={j: ISN3 for j in range(19)})),
    }
    mud = mud or {}
    dirs = {}
    for nome, (i, cel, kw) in spec.items():
        kw = dict(kw)
        kw.update(mud.get(nome, {}))
        if kw.pop("omitir", False):
            continue
        dirs[nome] = escrever_execucao(base, i, cel, **kw)
    # execucao abortada (DT-014): diretorio ignorado
    ab = Path(base) / "exec-0013-ASYNC-none-A.abortada-20261105T000000Z"
    ab.mkdir()
    (ab / "execucao.json").write_text("{}", encoding="utf-8")
    return dirs


class CampanhaBase(unittest.TestCase):
    """Uma derivacao completa sobre a campanha base."""

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.base = Path(cls.tmp.name) / "campanha"
        cls.base.mkdir()
        cls.dirs = montar_campanha(cls.base)
        cls.par = parametros_campanha(cls.tmp.name)
        cls.r = cal.derivar(cls.base, cls.par)
        cls.P = cls.r["parametros"]

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    # ---- entradas
    def test_esquema_e_execucoes_usadas(self):
        self.assertEqual(cal.ESQUEMA, self.r["esquema"])
        usadas = {e["dir"] for e in self.r["execucoes"]["usadas"]}
        self.assertEqual(11, len(usadas))
        self.assertNotIn("exec-0011-ASYNC-none-A", usadas)
        exc = {e["dir"]: e["veredito"] for e in self.r["execucoes"]["excluidas"]}
        self.assertEqual({"exec-0011-ASYNC-none-A": "RECUSADA", "exec-0012-ASYNC-F2-A": "DESCARTADA-INFRA"}, exc)
        pc = self.r["execucoes"]["por_celula"]
        self.assertEqual({"aceitas": 3, "planejadas": 5}, pc["ASYNC-none"])
        self.assertEqual({"aceitas": 1, "planejadas": 3}, pc["SYNC-F2"])

    def test_cada_parametro_tem_regra_valor_dados_e_incerteza(self):
        for nome, x in self.P.items():
            for campo in ("regra", "valor", "dados", "incerteza", "motivo", "ambiguidades"):
                self.assertIn(campo, x, "%s sem %s" % (nome, campo))
            self.assertIn("execucoes", x["dados"], nome)
            self.assertIn("n_execucoes", x["dados"], nome)
        regras = {x["regra"] for x in self.P.values()}
        self.assertEqual({"7.%d" % k for k in range(1, 19)}, regras)

    # ---- 7.1, 7.2, 7.3, 7.4, 7.5
    def test_7_1_referencias_por_perfil(self):
        ra = self.P["referencia_p99_async"]["valor"]
        self.assertEqual({"Login": MS, "Validate": MS}, ra["p99_ns"])
        self.assertEqual({"Login": 3300, "Validate": 3300}, ra["n"])
        self.assertEqual({"Logout": 165}, ra["omitidas"])
        self.assertEqual(3, self.P["referencia_p99_async"]["dados"]["n_execucoes"])
        rs = self.P["referencia_p99_sync"]["valor"]
        self.assertEqual("sync", rs["perfil"])
        self.assertEqual(2, self.P["referencia_p99_sync"]["dados"]["n_execucoes"])
        self.assertRegex(rs["sha256"], "^[0-9a-f]{64}$")

    def test_7_2_lambda5(self):
        x = self.P["lambda5_por_s"]
        self.assertEqual(20.0, x["valor"])
        self.assertEqual(60, x["dados"]["N"])
        self.assertEqual(["ASYNC-none"], x["dados"]["celulas"])

    def test_7_3_w(self):
        x = self.P["W_s"]
        self.assertEqual(5, x["valor"])
        self.assertEqual(5, x["dados"]["termo_vazao_s"])
        self.assertEqual(3, x["dados"]["termo_leitura_s"])
        self.assertEqual(40 * MS, x["dados"]["atraso_max_ns"])
        self.assertEqual(11, x["dados"]["n_execucoes"])

    def test_7_4_viavel(self):
        x = self.P["viabilidade_ac5"]["valor"]
        self.assertTrue(x["viavel"])
        self.assertEqual([], x["motivos"])

    def test_7_5_excedencia_recalculada(self):
        x = self.P["excedencia_por_janela"]
        por = {e["dir"]: e for e in x["valor"]["execucoes"]}
        n0 = por["exec-0000-ASYNC-none-A"]
        self.assertEqual(20, len(n0["janelas"]))
        self.assertEqual([100, 3], [n0["janelas"][3]["elegiveis"], n0["janelas"][3]["excedencias"]])
        self.assertEqual("sync", por["exec-0003-SYNC-none-A"]["perfil"])

    # ---- 7.6, 7.7, 7.9
    def test_7_6_e_7_9_fixos(self):
        self.assertEqual(2, self.P["k"]["valor"])
        self.assertEqual(100, self.P["n_err"]["valor"])

    def test_7_7_theta_lat_in(self):
        x = self.P["theta_lat_in"]
        self.assertEqual(math.nextafter(0.03, math.inf), x["valor"])
        self.assertEqual(0.03, x["dados"]["maximo_dos_minimos"])
        self.assertEqual(0, x["dados"]["ocorrencias"])
        self.assertEqual(57, x["incerteza"]["N"])
        self.assertAlmostEqual(3 / 57, x["incerteza"]["limite_95_3_sobre_n"])
        self.assertAlmostEqual(0.05 * 5 / 256, x["incerteza"]["alvo_a"])

    def test_7_7_theta_err_in_sem_erro(self):
        x = self.P["theta_err_in"]
        self.assertEqual(math.nextafter(0.0, math.inf), x["valor"])
        self.assertEqual(0.0, x["dados"]["maximo_dos_minimos"])

    # ---- 7.8, 7.11, 7.12
    def test_7_11_lag_in(self):
        x = self.P["lag_in_ms"]
        self.assertEqual(2000.0, x["valor"])
        self.assertEqual(60, x["dados"]["N"])

    def test_7_8_e_7_12_saida_literal_e_alternativa(self):
        lat = self.P["theta_lat_out"]
        self.assertEqual(math.nextafter(math.nextafter(0.03, math.inf), -math.inf), lat["valor"])
        self.assertIn("A-3", lat["ambiguidades"])
        # menor valor tal que nenhuma janela sem falha fica na banda por mais de m = 5 seguidas
        self.assertEqual(0.0, lat["dados"]["alternativa_menor_valor"])
        self.assertEqual(0.0, self.P["theta_err_out"]["valor"])
        lo = self.P["lag_out_ms"]
        self.assertEqual(math.nextafter(2000.0, -math.inf), lo["valor"])
        # janelas sem falha em 1,5 ms: com lag_out < 1,5 todas ficariam na banda (20 seguidas > m)
        self.assertEqual(1.5, lo["dados"]["alternativa_menor_valor"])

    def test_7_8_m_e_cooldown(self):
        x = self.P["m"]
        self.assertEqual(5, x["valor"])
        self.assertEqual(4, x["dados"]["maior_trecho"])
        self.assertEqual(["ASYNC-F1", "ASYNC-F2", "ASYNC-F4"], x["dados"]["celulas"])
        self.assertEqual(25, self.P["T_cooldown_s"]["valor"])

    # ---- 7.10
    def test_7_10_teto_convergencia(self):
        x = self.P["teto_convergencia_s"]
        self.assertEqual(600, x["valor"])
        self.assertAlmostEqual(35.3, x["dados"]["maior_delta_conv_s"])
        self.assertEqual(5, x["dados"]["n_convergiram"])
        self.assertIn("exec-0008-ASYNC-F2-A", [e["dir"] for e in x["dados"]["fora"]])

    # ---- 7.13
    def test_7_13_klag_conta_so_o_predicado_da_politica(self):
        x = self.P["kLag"]
        self.assertEqual(2, x["valor"])
        d = x["dados"]
        self.assertEqual(["ASYNC-F1", "ASYNC-none"], sorted(d["celulas"]))
        self.assertEqual(1, d["maior_persistencia_contada"])
        self.assertEqual({"1": 2}, d["ocorrencias_contadas_por_k"])
        self.assertEqual(88, x["incerteza"]["N"])
        self.assertAlmostEqual(3 / 88, x["incerteza"]["limite_95_3_sobre_n"])
        self.assertTrue(d["dentro_do_limite"])

    def test_7_13_assinatura_sem_resposta_a_parte(self):
        sr = self.P["kLag"]["dados"]["sem_resposta"]
        self.assertEqual(6, sr["maior_persistencia"])
        self.assertEqual(11, sr["janelas"])            # 6 na exec f1a, 5 na f1b (4 do isn2 e 1 do isn3)
        self.assertEqual({"1": 11, "2": 8, "3": 6, "4": 4, "5": 2, "6": 1}, sr["por_k"])
        por = {e["dir"]: e for e in sr["execucoes"]}
        self.assertEqual(6, por["exec-0005-ASYNC-F1-A"]["janelas"])
        self.assertEqual({"suspeito_sem_resposta": 4, "outro_no_sem_resposta": 1},
                         por["exec-0006-ASYNC-F1-A"]["motivos"])
        self.assertEqual({"suspeito_sem_resposta": 6}, por["exec-0005-ASYNC-F1-A"]["motivos"])

    def test_7_13_conferencia_previstas(self):
        conf = self.P["kLag"]["dados"]["conferencia_acao_prevista"]
        self.assertEqual(1, conf["linhas_ac5_prevista"])

    # ---- 7.14, 7.15
    def test_7_14_t_estavel(self):
        x = self.P["T_estavel_s"]
        self.assertEqual(34.0, x["valor"])          # 46,5 .. 78,5 s saudavel na f2a, depois 80,5 anomala
        self.assertEqual(32.0, x["dados"]["maior_trecho_s"])
        self.assertIn("SYNC-F2", x["dados"]["celulas"])

    def test_7_15_t_hold_viol(self):
        x = self.P["T_hold_viol_s"]
        self.assertAlmostEqual(26.5, x["valor"])
        self.assertEqual(11, x["dados"]["n_execucoes"])

    # ---- 7.16, 7.17
    def test_7_16_blocos_e_t_exec(self):
        x = self.P["n_blocos"]
        self.assertEqual(12, x["valor"])
        self.assertEqual(11, len(x["dados"]["t_exec_s"]))

    def test_7_17_volume_e_teto(self):
        v = self.P["volume_exec_mb"]
        maior = max(volume_bytes(d) for n, d in self.dirs.items() if n not in ("rec", "dsc"))
        self.assertEqual(maior, v["dados"]["maior_bytes"])
        self.assertEqual(math.ceil(maior / 2 ** 20), v["valor"])
        t = self.P["exec_teto_s"]
        self.assertEqual(1010, t["valor"])
        self.assertAlmostEqual(2.4, t["dados"]["t_pre_max_s"])
        self.assertAlmostEqual(13.5, t["dados"]["t_pos_max_s"])
        self.assertAlmostEqual(1009.85, t["dados"]["formula_s"])

    # ---- 7.18
    def test_7_18_q_hat(self):
        q = self.P["q_hat"]["valor"]
        self.assertEqual(["ASYNC-F2", "ASYNC-F4"], sorted(q))
        self.assertEqual({"com_violacao": 1, "n": 2, "q_hat": 0.5}, {k: q["ASYNC-F2"][k] for k in ("com_violacao", "n", "q_hat")})
        lo, hi = q["ASYNC-F2"]["clopper_pearson_95"]
        self.assertAlmostEqual(1 - math.sqrt(0.975), lo, places=9)
        self.assertAlmostEqual(math.sqrt(0.975), hi, places=9)
        taxas = {e["dir"]: e["taxa_violacoes_por_1000"] for e in q["ASYNC-F2"]["execucoes"]}
        self.assertAlmostEqual(3 * 1000 / 320, taxas["exec-0007-ASYNC-F2-A"])
        self.assertEqual(0.0, taxas["exec-0008-ASYNC-F2-A"])
        cmd = self.P["curva_de_poder"]["valor"]["comando"]
        self.assertIn("--m-celulas 2", cmd)
        self.assertIn("--blocos 12", cmd)

    def test_so_braco_a(self):
        self.assertEqual({"A"}, {e["braco"] for e in self.r["execucoes"]["usadas"]})
        self.assertEqual({"shadow"}, {e["controller"] for e in self.r["execucoes"]["usadas"]})


class Bordas(unittest.TestCase):
    """Variacoes da campanha base: limite do kLag, zero ocorrencias, nenhuma janela valida, lambda5."""

    def derivar(self, mud):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            montar_campanha(base, mud)
            return cal.derivar(base, parametros_campanha(tmp))["parametros"]

    def test_klag_limite_estourado_declara_ac5_inviavel(self):
        P = self.derivar({"n0": dict(lag_teto={j: ISN3 for j in range(4, 9)})})   # persistencia 5
        x = P["kLag"]
        self.assertEqual(6, x["valor"])
        self.assertFalse(x["dados"]["dentro_do_limite"])
        self.assertEqual(5, x["dados"]["k_max_no_limite"])
        v = P["viabilidade_ac5"]["valor"]
        self.assertFalse(v["viavel"])
        self.assertTrue(any("7.13" in m for m in v["motivos"]))

    def test_klag_cinco_ainda_cabe(self):
        P = self.derivar({"n0": dict(lag_teto={j: ISN3 for j in range(4, 8)})})   # persistencia 4
        self.assertEqual(5, P["kLag"]["valor"])
        self.assertTrue(P["kLag"]["dados"]["dentro_do_limite"])
        self.assertTrue(P["viabilidade_ac5"]["valor"]["viavel"])

    def test_klag_zero_ocorrencias(self):
        P = self.derivar({"n0": dict(lag_teto={}), "f1b": dict(lag_teto={j: ISN2 for j in range(6, 10)})})
        x = P["kLag"]
        self.assertEqual(1, x["valor"])
        self.assertEqual({}, x["dados"]["ocorrencias_contadas_por_k"])
        self.assertEqual(0, x["dados"]["maior_persistencia_contada"])
        self.assertGreater(x["dados"]["sem_resposta"]["janelas"], 0)

    def test_klag_nenhuma_janela_valida(self):
        tudo = [(-100, 1000)]
        P = self.derivar({n: dict(top_anomala=tudo) for n in ("n0", "n1", "n2", "f1a", "f1b")})
        x = P["kLag"]
        self.assertIsNone(x["valor"])
        self.assertEqual(0, x["incerteza"]["N"])
        self.assertIsNone(x["incerteza"]["limite_95_3_sobre_n"])
        self.assertIn("nenhuma janela", x["motivo"])

    def test_f1_sem_resposta_nao_entra_mesmo_longo(self):
        # isn2 no teto e sem resposta por 12 janelas: nao conta (sem a condicao, kLag seria 13 e o AC-5 inviavel)
        P = self.derivar({"f1a": dict(lag_teto={j: ISN2 for j in range(0, 12)}, morto=(ISN2, 0, 60, 45))})
        self.assertEqual(2, P["kLag"]["valor"])
        self.assertEqual(12, P["kLag"]["dados"]["sem_resposta"]["maior_persistencia"])
        self.assertTrue(P["viabilidade_ac5"]["valor"]["viavel"])

    def test_f1_com_resposta_conta(self):
        # depois da volta do isn2 (60 s), tres janelas com o isn2 no teto e vista completa: contam
        P = self.derivar({"f1a": dict(lag_teto={**{j: ISN2 for j in range(6, 12)},
                                               **{j: ISN2 for j in range(13, 16)}})})
        self.assertEqual(4, P["kLag"]["valor"])
        self.assertEqual(3, P["kLag"]["dados"]["maior_persistencia_contada"])

    def test_lambda5_no_quantil(self):
        # 60 janelas de 5 s: o quantil de 5 % por posto e a 3a menor; 3 janelas com 75 elegiveis (15/s) -> W = 7
        P = self.derivar({"n0": dict(lacunas={2: 25, 5: 25}), "n1": dict(lacunas={9: 25})})
        self.assertEqual(15.0, P["lambda5_por_s"]["valor"])
        self.assertEqual(7, P["W_s"]["valor"])
        P = self.derivar({"n0": dict(lacunas={2: 25, 5: 25})})
        self.assertEqual(20.0, P["lambda5_por_s"]["valor"])
        self.assertEqual(5, P["W_s"]["valor"])

    def test_t_hold_viol_sem_duas_violacoes_e_w(self):
        P = self.derivar({"f2a": dict(violacoes=[(30.0, "I1", "")])})
        self.assertEqual(5, P["T_hold_viol_s"]["valor"])
        self.assertIn("W", P["T_hold_viol_s"]["motivo"])

    def test_teto_convergencia_sem_execucao_convergida(self):
        sem = ("sem_rodada_valida", None)
        P = self.derivar({n: dict(conv=sem) for n in ("f1a", "f1b", "f2a", "sf2", "f4")})
        x = P["teto_convergencia_s"]
        self.assertIsNone(x["valor"])
        self.assertIn("nenhuma", x["motivo"])
        self.assertIsNone(P["exec_teto_s"]["valor"])

    def test_celula_vazia_entra_com_o_que_tem(self):
        P = self.derivar({"sf2": dict(omitir=True)})
        self.assertEqual(600, P["teto_convergencia_s"]["valor"])
        self.assertEqual(4, P["teto_convergencia_s"]["dados"]["n_convergiram"])


class Funcoes(unittest.TestCase):
    """Funcoes puras de cada regra, com as bordas."""

    def test_limiar_entrada(self):
        r = cal.limiar_entrada([[0.01, 0.04, 0.05, None, 0.2], [0.03, 0.03]], 2)
        self.assertEqual(0.04, r["maximo_dos_minimos"])
        self.assertEqual(math.nextafter(0.04, math.inf), r["valor"])
        self.assertEqual(3, r["N"])           # pares avaliaveis: (0,01;0,04) (0,04;0,05) (0,03;0,03); com None, fora
        self.assertEqual(0, r["ocorrencias"])

    def test_limiar_entrada_nenhuma_janela_valida(self):
        r = cal.limiar_entrada([[None, 0.5, None], [0.2]], 2)
        self.assertIsNone(r["valor"])
        self.assertEqual(0, r["N"])
        self.assertIn("nenhuma janela", r["motivo"])

    def test_limiar_saida(self):
        # banda (theta_out, 0,1): com m = 2, tres janelas seguidas em 0,05 obrigam theta_out >= 0,05
        r = cal.limiar_saida([[0.01, 0.05, 0.05, 0.05, 0.0, 0.02]], 0.1, 2)
        self.assertEqual(math.nextafter(0.1, -math.inf), r["valor"])
        self.assertEqual(0.05, r["alternativa_menor_valor"])
        r = cal.limiar_saida([[0.01, 0.05, 0.05, 0.0, 0.02]], 0.1, 2)
        self.assertEqual(0.01, r["alternativa_menor_valor"])   # (0,01; 0,1) deixa 2 seguidas em 0,05: cabe em m
        self.assertIsNone(cal.limiar_saida([[0.1]], None, 2)["valor"])

    def test_m_reentrada(self):
        r = cal.m_reentrada([list("HCCHCCCCHCCC"), list("CHC")])
        self.assertEqual(5, r["valor"])
        self.assertEqual(4, r["maior_trecho"])
        r = cal.m_reentrada([list("CCIH")])       # o trecho calmo seguido de intermediaria nao conta (A-5)
        self.assertEqual(1, r["valor"])
        r = cal.m_reentrada([list("HCCCC"), []])   # nenhuma recaida
        self.assertEqual(1, r["valor"])
        self.assertIn("recaida", r["motivo"])

    def test_resumo_lag(self):
        r1 = {"lag_por_no": [{"no": "a", "lag_ms": 1.0}, {"no": "b", "lag_ms": 2000.0}]}
        r2 = {"lag_por_no": [{"no": "a", "lag_ms": 2000.0}, {"no": "b", "lag_ms": 3.0}]}
        self.assertEqual((2000.0, "b"), cal.resumo_lag([r1]))
        self.assertEqual((2000.0, None), cal.resumo_lag([r1, r2]))   # empate entre nos: sem suspeito
        self.assertEqual((None, None), cal.resumo_lag([]))

    def test_klag_puro(self):
        J = lambda lag, sus, vc=True, resp=("a", "b", "c"): {"lag_max": lag, "suspeito": sus, "vista_completa": vc,
                                                              "respondeu_em_todas": set(resp)}
        ex = [("e1", [J(2000.0, "a"), J(2000.0, "a"), J(1.0, "b"), J(2000.0, "a")]),
              ("e2", [J(2000.0, "b", False, ("a", "c")), J(2000.0, "b", False, ("a", "c"))])]
        r = cal.klag(ex, 2000, 5)
        self.assertEqual(3, r["valor"])
        self.assertEqual({"1": 3, "2": 1}, r["ocorrencias_contadas_por_k"])
        self.assertEqual(2, r["sem_resposta"]["maior_persistencia"])
        self.assertEqual(4, r["N"])
        r = cal.klag(ex, 2000, 9)        # 3 x 9 + 13,3 = 40,3 >= 39,8
        self.assertFalse(r["dentro_do_limite"])
        self.assertEqual(2, r["k_max_no_limite"])

    def test_t_estavel_puro(self):
        a = [(T0 + 1 * S, True), (T0 + 3 * S, False), (T0 + 5 * S, False), (T0 + 7 * S, True), (T0 + 9 * S, False)]
        self.assertEqual(4.0, cal.t_estavel([("e", T0, a)], 2)["valor"])        # 3..5 s + P_sonda
        r = cal.t_estavel([("e", T0, [(T0 + 1 * S, True), (T0 + 3 * S, False)])], 2)
        self.assertEqual(2, r["valor"])
        self.assertIn("recaida", r["motivo"])

    def test_t_hold_viol_puro(self):
        r = cal.t_hold_viol([("a", [T0, T0 + 3 * S, T0 + 4 * S]), ("b", [T0 + S])], 5)
        self.assertEqual(3.0, r["valor"])
        r = cal.t_hold_viol([("a", [T0]), ("b", [])], 5)
        self.assertEqual(5, r["valor"])

    def test_teto_convergencia_puro(self):
        self.assertEqual(600, cal.teto_convergencia([("a", 399.9)])["valor"])
        self.assertEqual(600.0, cal.teto_convergencia([("a", 400.0)])["valor"])
        self.assertEqual(675.0, cal.teto_convergencia([("a", 450.0), ("b", 10.0)])["valor"])
        self.assertIsNone(cal.teto_convergencia([])["valor"])

    def test_calcular_w(self):
        r = cal.calcular_w(Fraction(20), 40 * MS, 2000)
        self.assertEqual((5, 5, 3), (r["valor"], r["termo_vazao_s"], r["termo_leitura_s"]))
        r = cal.calcular_w(Fraction(40), 1500 * MS, 2000)       # leitura domina: ceil(3,5) = 4 > ceil(2,5) = 3
        self.assertEqual((4, 3, 4), (r["valor"], r["termo_vazao_s"], r["termo_leitura_s"]))
        r = cal.calcular_w(Fraction(20), 0, 2000)                # atraso 0: W = prazo, recusado pelo codigo (A-17)
        self.assertEqual(5, r["valor"])
        self.assertTrue(r["aceito_pelo_codigo"])
        self.assertIsNone(cal.calcular_w(Fraction(0), 0, 2000)["valor"])
        self.assertFalse(cal.calcular_w(Fraction(200), 0, 2000)["aceito_pelo_codigo"])

    def test_viabilidade(self):
        self.assertTrue(cal.viabilidade(26, 1)["viavel"])
        r = cal.viabilidade(27, 1)
        self.assertFalse(r["viavel"])
        self.assertTrue(any("7.4" in m for m in r["motivos"]))
        self.assertFalse(cal.viabilidade(5, 6)["viavel"])
        self.assertTrue(cal.viabilidade(5, 5)["viavel"])

    def test_exec_teto(self):
        r = cal.exec_teto(300, 60, 600, 5, 2.4, 13.5)
        self.assertAlmostEqual(1009.85, r["formula_s"])
        self.assertEqual(1010, r["valor"])
        self.assertEqual(1010, cal.exec_teto(300, 60, 600, 5, 0.1, 0.1)["valor"])   # nunca abaixo de 1010
        self.assertEqual(1035, cal.exec_teto(300, 60, 600, 5, 2.4, 30.0)["valor"])

    def test_clopper_pearson(self):
        lo, hi = cal.clopper_pearson(0, 5)
        self.assertEqual(0.0, lo)
        self.assertAlmostEqual(1 - 0.025 ** (1 / 5), hi, places=12)
        lo, hi = cal.clopper_pearson(5, 5)
        self.assertAlmostEqual(0.025 ** (1 / 5), lo, places=12)
        self.assertEqual(1.0, hi)
        lo, hi = cal.clopper_pearson(1, 2)
        self.assertAlmostEqual(1 - math.sqrt(0.975), lo, places=10)
        self.assertAlmostEqual(math.sqrt(0.975), hi, places=10)


class Cli(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.base = Path(cls.tmp.name) / "campanha"
        cls.base.mkdir()
        montar_campanha(cls.base)
        cls.par = parametros_campanha(cls.tmp.name)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def rodar(self, *args):
        return subprocess.run([sys.executable, "-I", str(SCRIPT)] + [str(a) for a in args],
                              capture_output=True, text=True, cwd=str(self.tmp.name))

    def test_saida_deterministica_e_nunca_sobrescreve(self):
        s1, s2 = Path(self.tmp.name) / "d1.json", Path(self.tmp.name) / "d2.json"
        r = self.rodar("--campanha", self.base, "--parametros", self.par, "--saida", s1)
        self.assertEqual(0, r.returncode, r.stderr)
        r = self.rodar("--campanha", self.base, "--parametros", self.par, "--saida", s2)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual(s1.read_bytes(), s2.read_bytes())
        j = json.loads(s1.read_text(encoding="utf-8"))
        self.assertEqual(cal.ESQUEMA, j["esquema"])
        r = self.rodar("--campanha", self.base, "--parametros", self.par, "--saida", s1)
        self.assertEqual(2, r.returncode)

    def test_referencias_gravadas_com_o_sha256_do_json(self):
        rd = Path(self.tmp.name) / "refs"
        s = Path(self.tmp.name) / "d3.json"
        r = self.rodar("--campanha", self.base, "--parametros", self.par, "--saida", s, "--referencias-dir", rd)
        self.assertEqual(0, r.returncode, r.stderr)
        j = json.loads(s.read_text(encoding="utf-8"))
        for perf in ("async", "sync"):
            f = rd / ("referencia-S3-%s.json" % perf)
            self.assertEqual(hashlib.sha256(f.read_bytes()).hexdigest(),
                             j["parametros"]["referencia_p99_%s" % perf]["valor"]["sha256"])
            self.assertEqual("referencia-p99/1", json.loads(f.read_text(encoding="utf-8"))["esquema"])

    def test_braco_b_recusado(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            montar_campanha(base)
            escrever_execucao(base, 20, "ASYNC-F2", braco="B", marcadores=(20, 40))
            r = subprocess.run([sys.executable, "-I", str(SCRIPT), "--campanha", str(base), "--parametros",
                                str(parametros_campanha(tmp)), "--saida", str(Path(tmp) / "o.json")],
                               capture_output=True, text=True)
            self.assertEqual(2, r.returncode)
            self.assertIn("braco", r.stderr)
            self.assertFalse((Path(tmp) / "o.json").exists())

    def test_f3_recusada(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            montar_campanha(base)
            escrever_execucao(base, 21, "ASYNC-F3", marcadores=(20, 40))
            with self.assertRaises(cal.ErroUso):
                cal.derivar(base, parametros_campanha(tmp))

    def test_teto_lag_diferente_do_prazo_recusado(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            montar_campanha(base)
            with self.assertRaises(cal.ErroUso):
                cal.derivar(base, parametros_campanha(tmp, hotrod_timeout_ms=1500))


@unittest.skipUnless(TEM_NUMPY, "paridade com comparar_bracos/scipy so com numpy (python -E -P)")
class Paridade(unittest.TestCase):

    def test_delta_conv_e_taxa_iguais_ao_comparar_bracos(self):
        import comparar_bracos as cb
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            dirs = montar_campanha(base)
            P = cal.derivar(base, parametros_campanha(tmp))["parametros"]
            q = {e["dir"]: e for c in P["q_hat"]["valor"].values() for e in c["execucoes"]}
            dc = {e["dir"]: e["delta_conv_s"] for e in P["teto_convergencia_s"]["dados"]["execucoes"]}
            for nome in ("f2a", "f2b", "f4"):
                out, _ = cb.desfechos_execucao(dirs[nome], 1, ("durante", "recuperacao"), 191 * S)
                self.assertAlmostEqual(out["taxa_violacoes_por_1000"]["y"], q[dirs[nome].name]["taxa_violacoes_por_1000"])
            for nome in ("f1a", "f2a", "sf2", "f4"):
                out, _ = cb.desfechos_execucao(dirs[nome], 1, ("durante", "recuperacao"), 191 * S)
                self.assertAlmostEqual(out["delta_conv_s"]["y"], dc[dirs[nome].name])

    def test_clopper_pearson_igual_ao_scipy(self):
        from scipy import stats
        for x, n in ((0, 3), (1, 3), (2, 5), (5, 5), (3, 12), (7, 12)):
            lo, hi = cal.clopper_pearson(x, n)
            elo = 0.0 if x == 0 else stats.beta.ppf(0.025, x, n - x + 1)
            ehi = 1.0 if x == n else stats.beta.ppf(0.975, x + 1, n - x)
            self.assertAlmostEqual(elo, lo, places=9)
            self.assertAlmostEqual(ehi, hi, places=9)


if __name__ == "__main__":
    unittest.main()
