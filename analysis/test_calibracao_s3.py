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
JAVA_CONTROLE = AQUI.parent / "workload" / "src" / "main" / "java" / "br" / "unipampa" / "tcc" / "controle"
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
                      t_pos_s=13.0, previstas=0, extra_bytes=0, nome_dir=None, replay=False, varredura=None,
                      varredura_erro=False):
    """Grava uma execucao sintetica e devolve o diretorio.

    Eventos: a cada 100 ms, uma operacao de cada nome de `ops` (latencia 1 ms; 10/s por operacao) e um Logout a
    cada 2 s (fica fora da referencia: n < 1000). lentas/erros/lacunas: {janela de 5 s: n} aplicados aos primeiros
    eventos elegiveis da janela (latencia 2 ms / ERROR_TRANSPORT nos ultimos / removidos). Topologia a cada 2 s
    (t = -9,5 + 2q s). top_anomala: intervalos [a, b) em s em que o isn3 informa 2 membros. morto = (no, a, b, v):
    o no nao responde em [a, b) e os outros informam 2 membros a partir de v. Defasagem: uma rodada a cada 5 s no
    inicio da janela + atraso (1 ms padrao), lag de no 1,5/1,2/1,0 ms (suspeito unico isn1); lag_teto = {rodada:
    no no teto de 2000 ms}. violacoes = [(t_s, invariante, classificacao[, origem])]; com origem em alguma, o CSV
    ganha a coluna origem (W-10). replay: eventos ReplayValidate da sonda do W-10 (contrato CSV v2 §7) a cada 200 ms,
    lentos (50 ms), com VALID/INVALID/NOT_FOUND/ERROR_TRANSPORT, mais um replay tardio depois do fim da carga.
    O quinto item de uma violacao e a chave (padrao u-1). varredura = [(invariante, chave, classificacao, detalhe)]
    grava o varredura-rep-001.csv (VarreduraFinal.escreverAchados); varredura_erro grava o .erro.txt dela.
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
    if replay:
        rcs = ("VALID", "INVALID", "NOT_FOUND", "ERROR_TRANSPORT")
        for n, q in enumerate(range(-100, dur * 10, 2)):
            evs.append(["ReplayValidate", T0 + q * 100 * MS + 95 * MS, 50 * MS, rcs[n % 4]])
        for n in range(40):                                   # replay tardio, depois do fim da carga
            evs.append(["ReplayValidate", t_fim_carga + 5 * S + n * 10 * MS, 3 * MS, rcs[n % 2]])
    por_j = {}
    for e in evs:
        if e[0] in ops:
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
    com_origem = any(len(v) > 3 for v in violacoes)
    vl = ["instante,invariante,chave,mensagem,fase,classificacao" + (",origem" if com_origem else "")]
    for v in violacoes:
        ts, inv, cl = v[:3]
        tn = T0 + int(round(ts * S))
        vl.append('%s,%s,%s,"mensagem, com virgula",%s,%s' % (iso(tn), inv, v[4] if len(v) > 4 else "u-1",
                                                               fase_de(tn), cl)
                  + ("," + (v[3] if len(v) > 3 else "") if com_origem else ""))
    (d / "violacoes-rep-001.csv").write_text("\n".join(vl) + "\n", encoding="utf-8")

    if varredura is not None:
        (d / "varredura-rep-001.csv").write_text(
            "invariante,chave,classificacao,detalhe\n" + "".join("%s,%s,%s,%s\n" % x for x in varredura),
            encoding="utf-8")
    if varredura_erro:
        (d / "varredura-rep-001.erro.txt").write_text("falha na varredura\n", encoding="utf-8")

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
        self.assertEqual({"exec-0011-ASYNC-none-A": "RECUSADA", "exec-0012-ASYNC-F2-A": "DESCARTADA-INFRA",
                          "exec-0013-ASYNC-none-A.abortada-20261105T000000Z": "ABORTADA (DT-014)"}, exc)
        pc = self.r["execucoes"]["por_celula"]
        self.assertEqual({"aceitas": 3, "planejadas": 5}, pc["ASYNC-none"])
        self.assertEqual({"aceitas": 1, "planejadas": 3}, pc["SYNC-F2"])

    def test_cada_parametro_tem_regra_valor_dados_e_incerteza(self):
        for nome, x in self.P.items():
            for campo in ("regra", "valor", "dados", "incerteza", "motivo", "leituras"):
                self.assertIn(campo, x, "%s sem %s" % (nome, campo))
            self.assertIn("execucoes", x["dados"], nome)
            self.assertIn("n_execucoes", x["dados"], nome)
        regras = {x["regra"] for x in self.P.values()}
        self.assertEqual({"7.%d" % k for k in range(1, 19)}, regras)

    def test_leituras_do_complemento_1(self):
        lt = {x["id"]: x for x in self.r["leituras"]}
        self.assertEqual({"A-%d" % k for k in range(1, 18)}, set(lt))
        for i, x in lt.items():
            self.assertIn("complemento 1", x["situacao"], i)
            self.assertNotIn("aberta", x["situacao"], i)
        for i in ["A-2"] + ["A-%d" % k for k in range(6, 17)]:
            self.assertTrue(lt[i]["situacao"].startswith("confirmada"), i)
        self.assertIn("DT-022", self.r["pre_registro"])

    def test_emendas_e_texto_do_m(self):
        # W-9c: o texto do m descreve a leitura cortada no end (R-3), e as emendas listam a R-4 e o 3a
        t = self.P["m"]["texto"]
        self.assertNotIn("comeca em ou depois", t)
        self.assertIn("cortado no end", t)
        em = {x["id"]: x for x in self.r["emendas_vw9"]}
        self.assertEqual(["R-1", "R-2", "R-3", "R-4", "3a"], [x["id"] for x in self.r["emendas_vw9"]])
        for i, x in em.items():
            self.assertIn("DT-024", x["situacao"], i)
        self.assertIn("639966c", em["3a"]["situacao"])
        self.assertIn("N = 0", em["R-2"]["leitura"])
        self.assertIn("menor double acima de 1", em["R-2"]["leitura"])
        self.assertIn("5/W", em["R-4"]["leitura"])
        for x in ("DT-024", "3a"):
            self.assertIn(x, self.r["pre_registro"])

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
        self.assertEqual(5, x["dados"]["w_formula_s"])
        self.assertEqual(5, x["dados"]["termo_vazao_s"])
        self.assertEqual(3, x["dados"]["w_min_s"])          # floor(2 + 0,04) + 1
        self.assertFalse(x["dados"]["reserva_aplicada"])
        self.assertIsNone(x["dados"]["subestimacao_w_menor_que_5_s"])     # W = 5: sem a R-4
        self.assertEqual(40 * MS, x["dados"]["atraso_max_ns"])
        self.assertEqual(11, x["dados"]["n_execucoes"])

    def test_7_4_viavel(self):
        x = self.P["viabilidade_ac5"]["valor"]
        self.assertTrue(x["viavel"])
        self.assertEqual([], x["motivos"])
        self.assertFalse(x["reserva_aplicada"])
        self.assertEqual([{"W_s": 5, "kLag_min": 2, "viavel": True}], x["candidatos"])
        cob = self.P["viabilidade_ac5"]["dados"]["cobertura_latencia"]
        self.assertEqual(0.0, cob["fracao_indeterminada"])

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

    def test_7_8_e_7_12_saida_pelo_menor_valor(self):
        # complemento 1 §2: menor c em {0} U {observados abaixo do limiar} sem trecho intermediario maior que m
        lat = self.P["theta_lat_out"]
        self.assertEqual(0.0, lat["valor"])
        self.assertIn("A-3", lat["leituras"])
        self.assertNotIn("alternativa_menor_valor", lat["dados"])
        self.assertEqual(0.0, self.P["theta_err_out"]["valor"])
        # janelas sem falha em 1,5 ms: com lag_out < 1,5 todas ficariam na banda (20 seguidas > m)
        self.assertEqual(1.5, self.P["lag_out_ms"]["valor"])
        self.assertEqual(5, self.P["lag_out_ms"]["dados"]["m"])

    def test_7_8_m_e_cooldown(self):
        x = self.P["m"]
        self.assertEqual(5, x["valor"])
        self.assertEqual(4, x["dados"]["maior_trecho"])
        self.assertEqual(["ASYNC-F1", "ASYNC-F2", "ASYNC-F4"], x["dados"]["celulas"])
        self.assertEqual(25, self.P["T_cooldown_s"]["valor"])
        # complemento 1 §3: m nao usa theta_out
        self.assertEqual({"theta_lat_in", "theta_err_in", "lag_in"}, set(x["dados"]["limiares_usados"]))

    # ---- 7.10
    def test_7_10_teto_convergencia(self):
        x = self.P["teto_convergencia_s"]
        self.assertEqual(600, x["valor"])
        self.assertAlmostEqual(35.3, x["dados"]["maior_delta_conv_s"])
        self.assertEqual(5, x["dados"]["n_convergiram"])
        self.assertIn("exec-0008-ASYNC-F2-A", [e["dir"] for e in x["dados"]["fora"]])
        self.assertEqual(1, x["dados"]["n_fora"])

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
        self.assertIn("calibracao", x["dados"]["ciclo_completo"]["por_fase"])

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
        for trecho in ("python -E -P", "--semente 20261105", "--n-sim 10000", "--m-celulas 2", "--blocos 12"):
            self.assertIn(trecho, cmd)

    def test_so_braco_a(self):
        self.assertEqual({"A"}, {e["braco"] for e in self.r["execucoes"]["usadas"]})
        self.assertEqual({"shadow"}, {e["controller"] for e in self.r["execucoes"]["usadas"]})

    def test_valores_b40_carregam_no_java(self):
        b40 = self.r["valores_b40_parte2"]
        self.assertEqual([], b40["carga"]["problemas"])
        self.assertTrue(b40["carga"]["carrega_no_controlador"])
        # o JSON gravado (floats por repr, inteiros como inteiros) tambem carrega
        j = json.loads(cal.serializar(self.r).decode("utf-8"))["valores_b40_parte2"]
        self.assertEqual([], cal.problemas_de_carga(j, 2000))
        for campo in ("wNs", "nLat", "nErr", "k", "m", "cooldownNs", "estavelNs", "holdViolNs", "politica.kLag"):
            self.assertIs(type(j[campo]), int, campo)


class Bordas(unittest.TestCase):
    """Variacoes da campanha base: limite do kLag, zero ocorrencias, nenhuma janela valida, lambda5."""

    def derivar(self, mud, tudo=False):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            montar_campanha(base, mud)
            r = cal.derivar(base, parametros_campanha(tmp))
            return r if tudo else r["parametros"]

    def test_reserva_de_w_aplicada(self):
        # persistencia 5 em W = 5: kLag 6, 6 x 5 + 13,3 >= 39,8. Em W' = 4 a janela [36, 40) fica sem rodada e e
        # pulada: persistencia 5, kLag 6, 6 x 4 + 13,3 = 37,3 < 39,8 -> W = 4 (complemento 1 §4.3)
        P = self.derivar({"n0": dict(lag_teto={j: ISN3 for j in range(4, 9)})})
        self.assertEqual(4, P["W_s"]["valor"])
        self.assertEqual(5, P["W_s"]["dados"]["w_formula_s"])
        self.assertTrue(P["W_s"]["dados"]["reserva_aplicada"])
        self.assertEqual("reserva", P["W_s"]["dados"]["subestimacao_w_menor_que_5_s"]["caso"])
        x = P["kLag"]
        self.assertEqual(6, x["valor"])
        self.assertTrue(x["dados"]["dentro_do_limite"])
        v = P["viabilidade_ac5"]["valor"]
        self.assertTrue(v["viavel"])
        self.assertTrue(v["reserva_aplicada"])
        self.assertEqual([{"W_s": 5, "kLag_min": 6, "viavel": False}, {"W_s": 4, "kLag_min": 6, "viavel": True}],
                         v["candidatos"])
        cob = P["viabilidade_ac5"]["dados"]["cobertura_latencia"]
        self.assertEqual(1.0, cob["fracao_indeterminada"])     # 80 elegiveis por janela de 4 s < n_lat
        self.assertTrue(cob["passa_de_5_por_cento"])
        self.assertIn("perdeu cobertura", cob["declaracao"])
        # R-2 da V-W9: cobertura nula -> theta_in "sem cobertura" (> 1, inalcancavel) e theta_out = 1; m e
        # T_cooldown derivam. Com 80 + 2 eventos por janela de 4 s, o erro tambem fica sem cobertura
        for sinal in ("lat", "err"):
            x = P["theta_%s_in" % sinal]
            self.assertEqual(cal.THETA_SEM_COBERTURA, x["valor"])
            self.assertEqual((0, 0.0), (x["dados"]["janelas_determinadas"], x["dados"]["fracao_determinada"]))
            self.assertGreater(x["valor"], 1.0)
            self.assertTrue(x["dados"]["sem_cobertura"])
            self.assertIsNone(x["motivo"])
            self.assertEqual(1.0, P["theta_%s_out" % sinal]["valor"])
            self.assertTrue(P["theta_%s_out" % sinal]["dados"]["sem_cobertura"])
        self.assertIsNotNone(P["m"]["valor"])
        self.assertEqual(4 * P["m"]["valor"], P["T_cooldown_s"]["valor"])

    def test_reserva_sem_cobertura_carrega_no_java(self):
        r = self.derivar({"n0": dict(lag_teto={j: ISN3 for j in range(4, 9)})}, tudo=True)
        b40 = r["valores_b40_parte2"]
        self.assertEqual([], b40["carga"]["problemas"])
        self.assertTrue(b40["carga"]["carrega_no_controlador"])
        self.assertEqual([], cal.problemas_de_carga(b40, 2000))
        self.assertEqual(cal.THETA_SEM_COBERTURA, b40["thetaLatIn"])

    def test_nem_sempre_sem_cobertura(self):
        # cobertura parcial: theta_in sai da 7.7 normal (sem_cobertura falso)
        P = self.derivar({})
        self.assertFalse(P["theta_lat_in"]["dados"]["sem_cobertura"])
        self.assertEqual((60, 1.0), (P["theta_lat_in"]["dados"]["janelas_determinadas"],
                                     P["theta_lat_in"]["dados"]["fracao_determinada"]))
        self.assertFalse(P["theta_lat_out"]["dados"]["sem_cobertura"])

    def test_reserva_de_w_com_cobertura_parcial(self):
        # 3 operacoes (30/s) e 3 janelas de 5 s com 100 elegiveis: lambda5 = 20/s, W da 7.3 = 5. Em W' = 4, so as 3
        # janelas de 4 s com a lacuna ficam abaixo de n_lat: 3 de 75 (4 %); theta_lat_in, m e T_cooldown derivam
        tres = ("Login", "Validate", "IncrementFailure")
        mud = {n: dict(ops=tres) for n in ("n0", "n1", "n2", "s0", "s1", "f1a", "f1b", "f2a", "f2b", "sf2", "f4",
                                         "rec", "dsc")}
        mud["n0"].update(lag_teto={j: ISN3 for j in range(4, 9)}, lacunas={2: 50, 5: 50})
        mud["n1"].update(lacunas={9: 50})
        P = self.derivar(mud)
        self.assertEqual(20.0, P["lambda5_por_s"]["valor"])
        self.assertEqual(5, P["W_s"]["dados"]["w_formula_s"])
        self.assertEqual(4, P["W_s"]["valor"])
        cob = P["viabilidade_ac5"]["dados"]["cobertura_latencia"]
        self.assertEqual((75, 3), (cob["janelas_sem_falha"], cob["latencia_indeterminada"]))
        self.assertAlmostEqual(0.04, cob["fracao_indeterminada"])
        self.assertFalse(cob["passa_de_5_por_cento"])
        self.assertIsNotNone(cob["declaracao"])
        self.assertIsNotNone(P["theta_lat_in"]["valor"])
        self.assertEqual(4 * P["m"]["valor"], P["T_cooldown_s"]["valor"])

    def test_ac5_inviavel_sem_w_viavel(self):
        # persistencia 10 em qualquer W' de W_min = 3 a 5: kLag 11 nao cabe
        P = self.derivar({"n0": dict(lag_teto={j: ISN3 for j in range(4, 14)})})
        v = P["viabilidade_ac5"]["valor"]
        self.assertFalse(v["viavel"])
        self.assertFalse(v["reserva_aplicada"])
        self.assertEqual([5, 4, 3], [c["W_s"] for c in v["candidatos"]])
        self.assertEqual({11}, {c["kLag_min"] for c in v["candidatos"]})
        self.assertTrue(any("nenhum W" in m for m in v["motivos"]))
        self.assertEqual(5, P["W_s"]["valor"])
        self.assertEqual(11, P["kLag"]["valor"])
        self.assertFalse(P["kLag"]["dados"]["dentro_do_limite"])

    def test_janela_sem_rodada_pulada(self):
        # 3 operacoes: 30 elegiveis/s -> W = 4 < 5 s. Rodadas no teto em 15 s e 20 s caem nas janelas 3 e 5; a 4
        # ([16, 20) s) nao tem rodada e e pulada: persistencia 2, kLag 3 (sem pular, 1 e kLag 2)
        tres = ("Login", "Validate", "IncrementFailure")
        mud = {n: dict(ops=tres) for n in ("n0", "n1", "n2", "s0", "s1", "f1a", "f1b", "f2a", "f2b", "sf2", "f4",
                                         "rec", "dsc")}
        mud["n0"]["lag_teto"] = {3: ISN3, 4: ISN3}
        P = self.derivar(mud)
        self.assertEqual(4, P["W_s"]["valor"])
        self.assertFalse(P["W_s"]["dados"]["reserva_aplicada"])
        self.assertEqual(3, P["kLag"]["valor"])
        self.assertGreater(P["kLag"]["dados"]["janelas_sem_rodada"], 0)
        # R-4 (complemento 3 §4): W final < 5 s subestima kLag, m e o trecho do lag_out em ~5/W; declarado no JSON
        d = P["W_s"]["dados"]["subestimacao_w_menor_que_5_s"]
        self.assertEqual(1.25, d["fator"])
        self.assertEqual("7.3", d["caso"])
        self.assertEqual(["kLag", "m", "lag_out"], d["afeta"])
        self.assertIn("subestima", d["declaracao"])

    def test_klag_cinco_ainda_cabe(self):
        P = self.derivar({"n0": dict(lag_teto={j: ISN3 for j in range(4, 8)})})   # persistencia 4
        self.assertEqual(5, P["kLag"]["valor"])
        self.assertTrue(P["kLag"]["dados"]["dentro_do_limite"])
        self.assertTrue(P["viabilidade_ac5"]["valor"]["viavel"])
        self.assertFalse(P["viabilidade_ac5"]["valor"]["reserva_aplicada"])

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

    def test_celulas_da_regra_sem_execucao_nao_derivam(self):
        # sem nenhuma execucao com falha aceita, m e T_estavel nao sao "sem recaida": nao ha dado
        P = self.derivar({n: dict(omitir=True) for n in ("f1a", "f1b", "f2a", "f2b", "sf2", "f4")})
        for nome in ("m", "T_cooldown_s", "T_estavel_s", "teto_convergencia_s"):
            self.assertIsNone(P[nome]["valor"], nome)
            self.assertIsNotNone(P[nome]["motivo"], nome)
        self.assertIn("nenhuma execucao", P["m"]["motivo"])
        self.assertIn("nenhuma execucao", P["T_estavel_s"]["motivo"])
        self.assertEqual(0, P["q_hat"]["valor"]["ASYNC-F2"]["n"])
        self.assertIsNone(P["q_hat"]["valor"]["ASYNC-F2"]["q_hat"])
        self.assertEqual(2, P["kLag"]["valor"])            # so ASYNC sem falha: a exec 0 tem a rodada 6 no teto

    def test_celula_vazia_entra_com_o_que_tem(self):
        P = self.derivar({"sf2": dict(omitir=True)})
        self.assertEqual(600, P["teto_convergencia_s"]["valor"])
        self.assertEqual(4, P["teto_convergencia_s"]["dados"]["n_convergiram"])


class SondaReplay(unittest.TestCase):
    """W-10 (--s3-replay-probe): os eventos ReplayValidate e as violacoes com origem = sonda nao entram em nenhum sinal
    recalculado (contrato CSV v2 §7; InvariantAuditor: a violacao da sonda nao vai ao laco de controle)."""

    TODOS = ("n0", "n1", "n2", "s0", "s1", "f1a", "f1b", "f2a", "f2b", "sf2", "f4", "rec", "dsc")

    def derivar(self, mud):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            montar_campanha(base, mud)
            return cal.derivar(base, parametros_campanha(tmp))

    @staticmethod
    def sem_bytes(r):
        """Os parametros sem o que depende dos bytes dos arquivos (SHA-256 dos CSV na referencia, volume)."""
        P = json.loads(cal.serializar(r).decode("utf-8"))["parametros"]
        for perf in ("async", "sync"):
            P["referencia_p99_%s" % perf]["valor"].pop("sha256")
        P.pop("volume_exec_mb")
        return P

    def test_replay_validate_nao_muda_nenhum_valor(self):
        sem = self.derivar({})
        com = self.derivar({n: dict(replay=True) for n in self.TODOS})
        a, b = self.sem_bytes(sem), self.sem_bytes(com)
        self.assertEqual(sorted(a), sorted(b))
        for nome in a:
            self.assertEqual(a[nome]["valor"], b[nome]["valor"], nome)
            self.assertEqual(a[nome]["incerteza"], b[nome]["incerteza"], nome)
        self.assertNotIn("ReplayValidate", b["referencia_p99_async"]["valor"]["omitidas"])
        self.assertNotIn("ReplayValidate", b["referencia_p99_async"]["valor"]["p99_ns"])
        for k in ("thetaLatIn", "thetaErrIn", "wNs", "m", "politica.kLag", "defasagem.lagInMs"):
            self.assertEqual(sem["valores_b40_parte2"][k], com["valores_b40_parte2"][k], k)

    def test_violacao_da_sonda_fora_dos_sinais(self):
        # f4 com I1 da sonda em 20 s e 80 s: contadas, m iria a 6 (trecho 11..15 seguido da janela 16) e T_hold_viol
        # a 60 s; o controlador nao as recebe, entao m = 5 e T_hold_viol = 26,5 s como sem elas
        r = self.derivar({"f4": dict(violacoes=[(20.0, "I1", "", "sonda"), (80.0, "I1", "", "sonda")])})
        P = r["parametros"]
        self.assertEqual(5, P["m"]["valor"])
        self.assertAlmostEqual(26.5, P["T_hold_viol_s"]["valor"])
        # o q-chapeu (7.18, descritivo) segue o comparar_bracos.contar_violacoes, que conta a origem sonda (W-10,
        # decisao do Gestor pendente)
        self.assertEqual(1, P["q_hat"]["valor"]["ASYNC-F4"]["com_violacao"])

    def test_violacao_sem_origem_continua_contando(self):
        r = self.derivar({"f4": dict(violacoes=[(20.0, "I1", "", ""), (80.0, "I1", "", "")])})
        self.assertEqual(6, r["parametros"]["m"]["valor"])
        self.assertAlmostEqual(60.0, r["parametros"]["T_hold_viol_s"]["valor"])


class VPorPares(unittest.TestCase):
    """7.18 pela regra de V do W-5c (comparar_bracos.apurar_violacoes_execucao, feature/w5c-v-com-sonda, bc9cfdd):
    pares distintos (chave, classe) por execucao, somando auditor online, sonda (origem = sonda) e varredura REAL."""

    def derivar(self, mud):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "c"
            base.mkdir()
            montar_campanha(base, mud)
            return cal.derivar(base, parametros_campanha(tmp))["parametros"]

    @staticmethod
    def por_dir(P, cel):
        return {e["dir"]: e for e in P["q_hat"]["valor"][cel]["execucoes"]}

    def test_q_hat_e_taxa_por_par_distinto(self):
        # f2a: I1 de u-1 tres vezes (uma pela sonda), I4 e I2 REAL de u-1, e a varredura com I1 REAL de u-1 de novo:
        # 7 linhas, 3 pares (I1, I4, I2_REAL de u-1); taxa = 3 x 1000 / 320 acoes
        viol = [(30.0, "I1", ""), (31.0, "I1", ""), (32.0, "I1", "", "sonda"), (30.5, "I4", "", ""),
                (57.0, "I2", "REAL", ""), (90.0, "I2_JANELA_CASCATA", "", "")]
        P = self.derivar({"f2a": dict(violacoes=viol, varredura=[("I1", "u-1", "REAL", "confirmada (LOGOUT)")])})
        x = self.por_dir(P, "ASYNC-F2")["exec-0007-ASYNC-F2-A"]
        self.assertEqual(3, x["violacoes"])
        self.assertEqual(6, x["linhas"])
        self.assertAlmostEqual(3 * 1000 / 320, x["taxa_violacoes_por_1000"])
        self.assertEqual("presente", x["varredura"])
        self.assertIn("pares distintos", P["q_hat"]["dados"]["regra_v"])

    def test_chave_diferente_e_outro_par(self):
        viol = [(30.0, "I1", "", "", "u-1"), (31.0, "I1", "", "", "u-2")]
        P = self.derivar({"f2a": dict(violacoes=viol)})
        self.assertEqual(2, self.por_dir(P, "ASYNC-F2")["exec-0007-ASYNC-F2-A"]["violacoes"])

    def test_varredura_real_conta_no_q_hat(self):
        # F4 sem nenhuma linha no CSV do auditor e uma I1 REAL na varredura (sem Logout da sid no CSV de eventos:
        # conta sem filtro de fase): q-chapeu 1/1, e nao 0/1 como na contagem por linha
        P = self.derivar({"f4": dict(varredura=[("I1", "s-9", "REAL", "confirmada (LOGOUT)"),
                                                ("I1", "s-8", "INCERTA", "x")])})
        q = P["q_hat"]["valor"]["ASYNC-F4"]
        self.assertEqual((1, 1), (q["com_violacao"], q["n"]))
        self.assertEqual(1, self.por_dir(P, "ASYNC-F4")["exec-0010-ASYNC-F4-A"]["varredura_fora_de_v"])

    def test_varredura_com_erro_fica_fora_do_q_hat(self):
        P = self.derivar({"f2b": dict(varredura_erro=True)})
        q = P["q_hat"]["valor"]["ASYNC-F2"]
        self.assertEqual(1, q["n"])                    # so a f2a entra
        x = self.por_dir(P, "ASYNC-F2")["exec-0008-ASYNC-F2-A"]
        self.assertIsNone(x["taxa_violacoes_por_1000"])
        self.assertIn("varredura", x["motivo"])
        self.assertNotIn(None, P["curva_de_poder"]["valor"]["taxas_a"]["ASYNC-F2"])
        self.assertEqual(1, len(P["curva_de_poder"]["valor"]["taxas_a"]["ASYNC-F2"]))

    def test_apuracao_v_sem_numpy(self):
        # a copia da apuracao roda em python3 -I: nada de numpy nem scipy no modulo
        import apuracao_v
        fonte = Path(apuracao_v.__file__).read_text(encoding="utf-8")
        self.assertNotIn("import numpy", fonte)
        self.assertNotIn("scipy", fonte)
        self.assertTrue(callable(apuracao_v.apurar_violacoes_execucao))


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
        self.assertEqual(0.05, r["valor"])
        self.assertNotIn("alternativa_menor_valor", r)
        r = cal.limiar_saida([[0.01, 0.05, 0.05, 0.0, 0.02]], 0.1, 2)
        self.assertEqual(0.01, r["valor"])   # (0,01; 0,1) deixa 2 seguidas em 0,05: cabe em m
        # indeterminada conta como calma e quebra o trecho
        self.assertEqual(0.0, cal.limiar_saida([[0.05, 0.05, None, 0.05, 0.05]], 0.1, 2)["valor"])
        self.assertIsNone(cal.limiar_saida([[0.1]], None, 2)["valor"])

    def test_m_reentrada(self):
        # complemento 1 §3: trecho maximo sem alto que comeca em ou depois do end e termina numa janela alta
        r = cal.m_reentrada([("HHHNNHNNNNHNNN", 2), ("NHN", 0)])
        self.assertEqual(5, r["valor"])
        self.assertEqual(4, r["maior_trecho"])
        # R-3 da V-W9: o trecho 0..3 atravessa o end (indice 2) e conta so a parte depois dele (2 e 3): L = 2
        r = cal.m_reentrada([("NNNNHNH", 2)])
        self.assertEqual(3, r["valor"])
        self.assertEqual(1, r["trechos_cortados_no_end"])
        # trecho longo desde o comeco da carga (F4): so a janela 7 conta, nao as 8 (nem zero, como na leitura antiga)
        r = cal.m_reentrada([("NNNNNNNNHN", 7)])
        self.assertEqual((2, 1), (r["valor"], r["maior_trecho"]))
        self.assertEqual(0, cal.m_reentrada([("HHNNH", 2)])["trechos_cortados_no_end"])
        self.assertEqual(3, cal.m_reentrada([("NNH", 0)])["valor"])
        r = cal.m_reentrada([("HNNNN", 0)])       # nenhum trecho termina em alta
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
        # R-1: kLag = 1 em W = 14 conta como max(k, kLag) = 2: 41,3 s, fora; nenhum kLag cabe
        um = [("e", [J(2000.0, "a"), J(1.0, "b")])]
        r = cal.klag(um, 2000, 14)
        self.assertEqual(2, r["valor"])
        self.assertFalse(r["dentro_do_limite"])
        self.assertEqual(0, r["k_max_no_limite"])
        r = cal.klag([("e", [J(1.0, "b")])], 2000, 14)     # kLag = 1
        self.assertFalse(r["dentro_do_limite"])
        self.assertTrue(cal.klag([("e", [J(1.0, "b")])], 2000, 13)["dentro_do_limite"])

    def test_klag_pula_janela_sem_rodada(self):
        J = lambda lag, sus: {"lag_max": lag, "suspeito": sus, "vista_completa": True, "lag_rodadas": 1,
                              "respondeu_em_todas": {"a", "b", "c"}}
        sem = {"lag_max": None, "suspeito": None, "vista_completa": True, "lag_rodadas": 0,
               "respondeu_em_todas": {"a", "b", "c"}}
        r = cal.klag([("e", [J(2000.0, "a"), sem, J(2000.0, "a")])], 2000, 4)
        self.assertEqual(3, r["valor"])        # a janela sem rodada nao quebra a persistencia
        self.assertEqual(2, r["N"])
        self.assertEqual(1, r["janelas_puladas"])

    def test_t_estavel_puro(self):
        a = [(T0 + 1 * S, True), (T0 + 3 * S, False), (T0 + 5 * S, False), (T0 + 7 * S, True), (T0 + 9 * S, False)]
        self.assertEqual(4.0, cal.t_estavel([("e", T0, a)], 2)["valor"])        # 3..5 s + P_sonda
        r = cal.t_estavel([("e", T0, [(T0 + 1 * S, True), (T0 + 3 * S, False)])], 2)
        self.assertEqual(2, r["valor"])
        self.assertIn("recaida", r["motivo"])

    def test_sem_execucao_nao_deriva(self):
        self.assertIsNone(cal.m_reentrada([])["valor"])
        self.assertIsNone(cal.m_reentrada([("", 0), ("HH", 2)])["valor"])    # nenhuma janela depois do end
        self.assertIsNone(cal.t_estavel([], 2)["valor"])
        self.assertIsNone(cal.t_estavel([("e", T0, [(T0 - S, False)])], 2)["valor"])   # nenhuma amostra depois do end
        self.assertIsNone(cal.t_hold_viol([], 5)["valor"])

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
        self.assertEqual((5, 5, 3), (r["valor"], r["termo_vazao_s"], r["w_min_s"]))
        r = cal.calcular_w(Fraction(40), 1500 * MS, 2000)       # W_min domina: floor(3,5) + 1 = 4 > ceil(2,5) = 3
        self.assertEqual((4, 3, 4), (r["valor"], r["termo_vazao_s"], r["w_min_s"]))
        r = cal.calcular_w(Fraction(40), 1000 * MS, 2000)       # 2 + 1 = 3 exato: W_min estrito = 4
        self.assertEqual(4, r["w_min_s"])
        r = cal.calcular_w(Fraction(200), 0, 2000)               # W_min = 3 > prazo de 2 s
        self.assertEqual((3, 1, 3), (r["valor"], r["termo_vazao_s"], r["w_min_s"]))
        self.assertTrue(r["aceito_pelo_codigo"])
        self.assertIsNone(cal.calcular_w(Fraction(0), 0, 2000)["valor"])

    def test_reserva_w(self):
        kl = {5: 6, 4: 6, 3: 6}
        r = cal.reserva_w(5, 3, kl.get)
        self.assertEqual((4, 6, True, True), (r["W_s"], r["kLag"], r["viavel"], r["reserva_aplicada"]))
        r = cal.reserva_w(5, 3, {5: 2}.get)
        self.assertEqual((5, 2, True, False), (r["W_s"], r["kLag"], r["viavel"], r["reserva_aplicada"]))
        self.assertEqual(1, len(r["candidatos"]))
        r = cal.reserva_w(5, 3, {5: 11, 4: 11, 3: 11}.get)
        self.assertEqual((5, 11, False, False), (r["W_s"], r["kLag"], r["viavel"], r["reserva_aplicada"]))
        self.assertEqual(3, len(r["candidatos"]))
        # R-1 da V-W9: o P1 so age depois de max(k, kLag) janelas; com k = 2, kLag = 1 conta como 2
        r = cal.reserva_w(30, 3, lambda w: 1)                     # 2 x 13 + 13,3 = 39,3 < 39,8; 2 x 14 = 41,3
        self.assertEqual((13, 1, True), (r["W_s"], r["kLag"], r["reserva_aplicada"]))
        r = cal.reserva_w(14, 3, lambda w: 1)
        self.assertEqual([(14, False), (13, True)], [(c["W_s"], c["viavel"]) for c in r["candidatos"]])
        self.assertIn("max(k, kLag)", r["motivos"][0])
        r = cal.reserva_w(5, 3, lambda w: None)                   # kLag nao derivado: nao avaliavel
        self.assertIsNone(r["viavel"])
        self.assertEqual(5, r["W_s"])

    def test_ciclo_completo(self):
        r = cal.ciclo_completo([("a", "calibracao", 1000), ("b", "calibracao", 481000), ("c", "reposicoes", 5000),
                                ("d", "calibracao", 1021000), ("e", "calibracao", None)])
        self.assertEqual([480.0, 540.0], [x["s"] for x in r["por_fase"]["calibracao"]])
        self.assertEqual([], r["por_fase"]["reposicoes"])
        self.assertEqual(540.0, r["maximo_s"])
        self.assertEqual(510.0, r["mediana_s"])

    def test_viavel_em(self):
        # R-1 da V-W9: max(K_NUCLEO, kLag) W + 13,3 < 39,8, com K_NUCLEO = 2 (a persistencia do nucleo)
        self.assertEqual(2, cal.K_NUCLEO)
        self.assertTrue(cal.viavel_em(1, 13))       # 2 x 13 + 13,3 = 39,3
        self.assertFalse(cal.viavel_em(1, 14))      # 2 x 14 + 13,3 = 41,3 (antes da R-1, 1 x 14 cabia)
        self.assertTrue(cal.viavel_em(2, 13))
        self.assertTrue(cal.viavel_em(5, 5))        # 38,3
        self.assertFalse(cal.viavel_em(6, 5))       # 43,3
        self.assertFalse(hasattr(cal, "viabilidade"))   # R-5: a funcao sem uso saiu

    def test_theta_sem_cobertura_do_complemento_3(self):
        # complemento 3 §2 e 3a §3: o menor double acima de 1, nao so "acima de 1"
        self.assertEqual(math.nextafter(1.0, math.inf), cal.THETA_SEM_COBERTURA)
        self.assertEqual(1.0, cal.THETA_OUT_SEM_COBERTURA)

    def test_sem_cobertura_quando_n_e_zero(self):
        # complemento 3a §2: sem cobertura sempre que a 7.7 tiver N = 0, mesmo com janelas determinadas
        r = cal.limiar_entrada_com_cobertura([[0.01, None, 0.02, None], [None, 0.5]], 2)
        self.assertTrue(r["sem_cobertura"])
        self.assertEqual(cal.THETA_SEM_COBERTURA, r["valor"])
        self.assertEqual((0, 3, 6), (r["N"], r["janelas_determinadas"], r["n_janelas_total"]))
        self.assertEqual(0.5, r["fracao_determinada"])
        self.assertIsNone(r["motivo"])
        r = cal.limiar_entrada_com_cobertura([[None, None]], 2)
        self.assertTrue(r["sem_cobertura"])
        self.assertEqual(0.0, r["fracao_determinada"])
        r = cal.limiar_entrada_com_cobertura([[]], 2)            # nenhuma janela: nao e "sem cobertura", nao deriva
        self.assertFalse(r["sem_cobertura"])
        self.assertIsNone(r["valor"])
        self.assertIsNone(r["fracao_determinada"])
        r = cal.limiar_entrada_com_cobertura([[0.01, 0.04, None]], 2)
        self.assertFalse(r["sem_cobertura"])
        self.assertEqual(math.nextafter(0.01, math.inf), r["valor"])
        self.assertAlmostEqual(2 / 3, r["fracao_determinada"])

    def test_problemas_de_carga(self):
        ok = {"wNs": 5 * S, "nLat": 100, "nErr": 100, "thetaLatIn": 0.05, "thetaLatOut": 0.0, "thetaErrIn": 5e-324,
              "thetaErrOut": 0.0, "k": 2, "m": 5, "cooldownNs": 25 * S, "estavelNs": 34 * S, "holdViolNs": 26 * S,
              "politica.kLag": 2, "defasagem.lagInMs": 2000.0, "defasagem.lagOutMs": 1.5}
        self.assertEqual([], cal.problemas_de_carga(ok, 2000))
        ruins = [("thetaLatOut", 0.05), ("thetaErrOut", 1e-3), ("thetaLatIn", math.inf), ("thetaLatIn", None),
                 ("m", None), ("m", 0), ("k", 0), ("wNs", 5e9), ("wNs", 2 * S), ("cooldownNs", None),
                 ("estavelNs", -1), ("holdViolNs", 1.5), ("politica.kLag", 0), ("defasagem.lagOutMs", 2000.0),
                 ("defasagem.lagInMs", 0.0), ("defasagem.lagInMs", 2500.0), ("nErr", -1)]
        for campo, v in ruins:
            x = dict(ok)
            x[campo] = v
            self.assertTrue(cal.problemas_de_carga(x, 2000), "%s = %r passou" % (campo, v))

    @unittest.skipUnless(JAVA_CONTROLE.is_dir(), "codigo Java fora desta arvore (copia so de analysis/)")
    def test_guardas_lidos_do_codigo_java(self):
        # cada guarda que problemas_de_carga espelha existe no codigo do controlador (se o Java mudar, isto falha)
        raiz = JAVA_CONTROLE
        self.assertTrue(cal.GUARDAS_JAVA)
        for arquivo, trecho in cal.GUARDAS_JAVA:
            fonte = (raiz / arquivo).read_text(encoding="utf-8")
            self.assertIn(trecho, fonte, "%s sem o guarda %r" % (arquivo, trecho))

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

    def test_apuracao_v_igual_a_do_comparar_bracos(self):
        # depois da integracao com o W-5c: o codigo-fonte de cada funcao copiada e o mesmo, e o q-chapeu bate
        import inspect
        import apuracao_v
        import comparar_bracos as cb
        if not hasattr(cb, "apurar_violacoes_execucao"):
            self.skipTest("comparar_bracos desta arvore ainda sem a apuracao do W-5c")
        for nome in apuracao_v.COPIADAS:
            self.assertEqual(inspect.getsource(getattr(cb, nome)), inspect.getsource(getattr(apuracao_v, nome)), nome)
        for nome in apuracao_v.CONSTANTES:
            self.assertEqual(getattr(cb, nome), getattr(apuracao_v, nome), nome)

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
