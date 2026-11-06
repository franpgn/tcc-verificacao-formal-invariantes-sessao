"""Testes do W-5c: V com a sonda de replay (W-10) e a varredura final no comparar_bracos.py.

Especificacao: rascunho do pre-registro do benchmark refeito (docs/pre-registro-benchmark-refeito-2026-11-06.md,
branch docs/pre-registro-benchmark-refeito, commit a5ddac9), secao 2 (metodo de observacao) e secao 8.

Nao regressao (classe NaoRegressaoGolden): o cenario sem sonda e sem varredura de cenario_golden() foi rodado
com o comparar_bracos.py da base daa18a2 (feature/controlador-integracao), antes de qualquer mudanca do W-5c, e
gravado em analysis/fixtures/w5c/golden-sem-sonda-daa18a2.json (python -E -P analysis/test_comparar_bracos_w5c.py
--gerar-golden). O teste exige que cada chave do golden exista no resultado atual com o mesmo valor; chaves novas
sao permitidas. Os caminhos (manifesto, execucoes) e o SHA-256 do script ficam fora da comparacao.

Uso: python -E -P -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio; numpy e scipy no
site do usuario, decisao G2 de 28/10)
"""
import json
import sys
import tempfile
import time
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402

GOLDEN = AQUI / "fixtures" / "w5c" / "golden-sem-sonda-daa18a2.json"
S = 1_000_000_000
MS = 1_000_000
T0 = 1_761_566_400 * S          # 2025-10-27T12:00:00Z
INI, FIM = T0 + 10 * S, T0 + 30 * S
CAB_S3 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns"
CAB_EV = "op_id,operation,start_ns,end_ns,replica,return_code,key"
CAB_VIOL = "instante,invariante,chave,mensagem,fase,classificacao"
SHA_PARAM = "a" * 64
ATUADOR = "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)"


def iso(t_ns):
    seg, frac = divmod(t_ns, S)
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(seg)) + (".%09dZ" % frac if frac else "Z")


def _trilha(modo):
    meta = {"tipo": "meta", "esquema": "decisoes-controlador/1.2", "versao_programa": "teste", "rep": 1,
            "modo": modo, "cenario": "S3", "replicacao": "async", "nos_esperados": 3, "parametros": {"w_ns": 5 * S},
            "param_sha256": SHA_PARAM, "atuador": ATUADOR, "politica": "por-assinatura"}
    dec = {"tipo": "decisao", "t_fim_ns": INI + 5 * S, "t_decisao_ns": INI + 5 * S + 1, "modo": modo,
           "perfil": "CONSERVADOR", "transicao": True, "regra": "R1", "motivos": ["VIOLACAO"],
           "severidade": "ALTA", "sinais": {"taxa_erro": 0.0}, "param_sha256": SHA_PARAM}
    acao = {"tipo": "acao", "operacao": "APLICAR", "executada": modo == "active", "modo": modo, "atuador": "teste",
            "acao": {"tipo": "AC-2", "caches": ["sessions"], "alvo": None, "valor": None}, "origem": "POLITICA",
            "regra": "R1", "t_fim_janela_ns": INI + 5 * S, "t_pedido_ns": INI + 5 * S + 2,
            "t_inicio_ns": INI + 5 * S + 3, "duracao_ns": 10 * MS,
            "resultado": "APLICADA" if modo == "active" else "PREVISTA", "codigo_http": None, "lido": None,
            "erro": None, "reversao": None}
    fim = {"tipo": "fim", "decisoes": 1, "acoes_pedidas": 1, "linhas_perdidas": 0}
    return "".join(json.dumps(o) + "\n" for o in (meta, dec, acao, fim))


def escrever_exec_golden(d, b, braco, trilha):
    """Uma execucao sem sonda e sem varredura: acoes S3, eventos, marcadores, violacoes com chaves distintas,
    resumo de convergencia e (opcional) trilha. Deterministica em (b, braco)."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    fator = (1.0 + 0.01 * b) * (0.95 if braco == "B" else 1.0)
    acoes = []
    for i in range(1100):
        t = INI - 2 * S + i * 25 * MS
        lat = int((2 * MS + (i % 97) * 13_000) * fator)
        falha = (i * 7 + b + (braco == "B")) % 53 == 0
        acoes.append("%d,u%d,Validate,s,%s,1,%s,,%d,%d" % (
            t, i % 40, "ERROR_TRANSPORT" if falha else "OK_VALIDATE:VALID", "true" if falha else "false",
            t + lat, (i % 5) * 10_000))
    (d / "s3-acoes-rep-001.csv").write_bytes(("\n".join([CAB_S3] + acoes) + "\n").encode("utf-8"))
    ev = []
    for i in range(1200):
        a = INI - 2 * S + i * 23 * MS
        op = ("Validate", "Logout", "Login", "Block")[i % 4]
        rc = "ERROR_UNAVAILABLE" if (i + b) % 41 == 0 else {"Validate": "VALID", "Logout": "NONE",
                                                             "Login": "OK", "Block": "NONE"}[op]
        ev.append("%d,%s,%d,%d,isn%d,%s,k%d" % (i, op, a, a + int((1 + i % 7) * MS * fator), 1 + i % 3, rc, i))
    (d / "rep-001.csv").write_bytes(("\n".join([CAB_EV] + ev) + "\n").encode("utf-8"))
    (d / "meta-csv-rep-001.json").write_text('{"contrato_csv": 2}', encoding="utf-8")
    (d / "marcadores.txt").write_text("fault=F2 node=isn3 event=start t_epoch_ns=%d\n"
                                      "fault=F2 node=isn3 event=end t_epoch_ns=%d\n" % (INI, FIM), encoding="utf-8")
    n_i1 = {"A": (3, 1, 4, 2, 5, 3)[(b - 1) % 6], "B": (1, 0, 2, 1, 1, 0)[(b - 1) % 6]}[braco]
    viol = [(INI + (5 + j) * S + b * MS, "I1", "") for j in range(n_i1)]
    viol += [(INI + 2 * S, "I4", ""), (INI + 3 * S, "I2", "REAL"), (INI + 4 * S, "I2", "ESPERADA-POR-DESENHO"),
             (INI + 6 * S, "I2_JANELA_CASCATA", ""), (INI + 7 * S, "I3", ""), (INI - 5 * S, "I1", ""),
             (FIM + 70 * S, "I5", "")] if (b + (braco == "A")) % 2 == 0 else [(FIM + 2 * S, "I4", "")]
    linhas = [CAB_VIOL] + ["%s,%s,sid-%s%d-%d,msg,durante,%s" % (iso(t), inv, braco, b, i, cl)
                           for i, (t, inv, cl) in enumerate(viol)]
    (d / "violacoes-rep-001.csv").write_text("\n".join(linhas) + "\n", encoding="utf-8")
    resumo = {"t_fim_ns": FIM, "convergiu": True, "divergencia_persistente": False,
              "t_convergencia_abs_ns": T0 + 60 * S, "t_fim_carga_ns": T0 + 55 * S}
    (d / "convergencia-resumo-rep-001.json").write_text(json.dumps(resumo), encoding="utf-8")
    if trilha:
        (d / "decisoes-rep-001.jsonl").write_text(_trilha(cb.MODO_ESPERADO[braco]), encoding="utf-8")


def cenario_golden(raiz):
    """Manifesto com uma celula de teste (6 blocos, com trilha) e uma descritiva (2 blocos, sem trilha)."""
    raiz = Path(raiz)
    linhas = []
    for celula, n, trilha in (("T", 6, True), ("D", 2, False)):
        for b in range(1, n + 1):
            for br in "AB":
                d = raiz / celula / ("b%02d%s" % (b, br))
                escrever_exec_golden(d, b, br, trilha)
                linhas.append("%d\t%s\t%s\t%s" % (b, celula, br, Path(celula) / d.name))
    man = raiz / "manifesto.tsv"
    man.write_text("bloco\tcelula\tbraco\texecucao\n" + "\n".join(linhas) + "\n", encoding="utf-8")
    return man


def rodar_golden(raiz):
    """Resultado do cenario golden sem as chaves que dependem do caminho ou do script."""
    man = cenario_golden(raiz)
    r = cb.comparar(man, 11, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60,
                    tipos_celula={"T": "teste", "D": "descritiva"}, n_boot=200, n_hier=200)
    r = json.loads(json.dumps(r, sort_keys=True, allow_nan=False))
    r.pop("manifesto", None)
    r.pop("gerado_por", None)
    for c in r["celulas"].values():
        c.pop("execucoes", None)
    return r


def diferencas_subconjunto(esperado, obtido, caminho="$"):
    """Lista de caminhos em que `esperado` nao esta contido em `obtido` (dicionarios: chaves novas permitidas)."""
    if isinstance(esperado, dict):
        if not isinstance(obtido, dict):
            return ["%s: esperado objeto, obtido %r" % (caminho, type(obtido).__name__)]
        out = []
        for k, v in esperado.items():
            if k not in obtido:
                out.append("%s.%s: ausente" % (caminho, k))
            else:
                out += diferencas_subconjunto(v, obtido[k], "%s.%s" % (caminho, k))
        return out
    if esperado != obtido:
        return ["%s: esperado %r, obtido %r" % (caminho, esperado, obtido)]
    return []


class NaoRegressaoGolden(unittest.TestCase):
    """Sem sonda e sem varredura, o comparar_bracos.py devolve o mesmo resultado da base daa18a2."""

    def test_sem_sonda_sem_varredura_igual_a_base(self):
        golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            r = rodar_golden(tmp)
        difs = diferencas_subconjunto(golden, r)
        self.assertEqual([], difs[:20], "%d diferenca(s) em relacao ao golden da base" % len(difs))

    def test_golden_exercita_v_e_a_regra_iu(self):
        # guarda do proprio golden: a celula de teste tem V nos dois bracos, k > 0 e regra IU avaliada
        golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
        t = golden["celulas"]["T"]
        self.assertTrue(t["co_principal_v"]["avaliavel"])
        self.assertGreater(t["co_principal_v"]["contagem"]["A"], 0)
        self.assertGreater(t["desfechos"]["taxa_violacoes_por_1000"]["k_nao_nulos"], 0)
        self.assertIn(t["regra_iu"]["resultado"], ("melhoria", "sem-melhoria"))
        self.assertIn("D", golden["celulas"])


if __name__ == "__main__":
    if "--gerar-golden" in sys.argv:
        if GOLDEN.exists():
            sys.exit("golden ja existe (nunca sobrescreve): %s" % GOLDEN)
        GOLDEN.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            res = rodar_golden(tmp)
        with open(GOLDEN, "x", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(res, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n")
        print("golden gravado em %s" % GOLDEN)
    else:
        unittest.main()
