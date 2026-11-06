"""Testes do W-5e: analise do fatorial 2x2 (regra de fusao x controlador) no comparar_bracos.py.

Especificacao: complemento 1 do pre-registro do benchmark refeito, DT-027 (docs/pre-registro-benchmark-refeito-
complemento-1-2026-11-06.md, branch docs/pre-registro-benchmark-refeito, commit 4fa4546), secoes 1, 2 e 5.

Nao regressao da analise de 2 bracos (classe NaoRegressao2Bracos): o cenario com sonda, varredura e varredura com
erro de cenario_golden_2bracos() foi rodado com o comparar_bracos.py da base edfa8b6, antes de qualquer mudanca do
W-5e, e gravado em analysis/fixtures/w5e/golden-2bracos-com-instrumentos-edfa8b6.json (python -E -P
analysis/test_comparar_bracos_fatorial.py --gerar-golden). O golden sem instrumentos do W-5c continua em
test_comparar_bracos_w5c.NaoRegressaoGolden.

Uso: python -E -P -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
import test_comparar_bracos_w5c as w5c  # noqa: E402  (geradores de execucoes com sonda e varredura)

GOLDEN_2B = AQUI / "fixtures" / "w5e" / "golden-2bracos-com-instrumentos-edfa8b6.json"


def cenario_golden_2bracos(raiz):
    """Celula de teste F2 do W-5d (6 blocos; varredura com erro so em A, so em B e nos dois) e celula descritiva D
    do W-5c (2 blocos com forma 2 da sonda), no mesmo manifesto."""
    raiz = Path(raiz)
    m1 = w5c.gerar_celula_varr(raiz, "F2")
    m2 = w5c.gerar_celula_inst(raiz, "D", 2, forma2_a=[1, 2], outras_a=[1, 0], outras_b=[0, 1])
    linhas = ["bloco\tcelula\tbraco\texecucao"]
    for m in (m1, m2):
        linhas += [x for x in m.read_text(encoding="utf-8").splitlines()[1:] if x.strip()]
    man = raiz / "manifesto-golden.tsv"
    man.write_text("\n".join(linhas) + "\n", encoding="utf-8")
    return man


def rodar_golden_2bracos(raiz):
    man = cenario_golden_2bracos(raiz)
    r = cb.comparar(man, 7, delta_latencia=0.10, w_analise_s=5, w_recovery_s=60,
                    tipos_celula={"F2": "teste", "D": "descritiva"}, n_boot=200, n_hier=200)
    r = json.loads(json.dumps(r, sort_keys=True, allow_nan=False))
    r.pop("manifesto", None)
    r.pop("gerado_por", None)
    for c in r["celulas"].values():
        c.pop("execucoes", None)
    return r


class NaoRegressao2Bracos(unittest.TestCase):
    """A analise de 2 bracos (com sonda, varredura e varredura com erro) e a da base edfa8b6."""

    def test_igual_a_base(self):
        golden = json.loads(GOLDEN_2B.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            r = rodar_golden_2bracos(tmp)
        difs = w5c.diferencas_subconjunto(golden, r)
        self.assertEqual([], difs[:20], "%d diferenca(s) em relacao ao golden da base" % len(difs))

    def test_golden_exercita_varredura_com_erro_e_forma2(self):
        golden = json.loads(GOLDEN_2B.read_text(encoding="utf-8"))
        f2 = golden["celulas"]["F2"]
        self.assertEqual(3, f2["violacoes"]["detalhe"]["varredura_com_erro"]["blocos"])
        self.assertIn("taxa_violacoes_por_1000:sem_forma2", golden["celulas"]["D"]["desfechos"])
        self.assertIn(f2["regra_iu"]["resultado"], ("melhoria", "sem-melhoria"))


if __name__ == "__main__":
    if "--gerar-golden" in sys.argv:
        if GOLDEN_2B.exists():
            sys.exit("golden ja existe (nunca sobrescreve): %s" % GOLDEN_2B)
        GOLDEN_2B.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            res = rodar_golden_2bracos(tmp)
        with open(GOLDEN_2B, "x", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(res, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n")
        print("golden gravado em %s" % GOLDEN_2B)
    else:
        unittest.main()
