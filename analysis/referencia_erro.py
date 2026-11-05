"""Referencia de taxa de erro sem falha para o Delta_rec de sensibilidade (B-39.4, W-5).

Especificacao: docs/plano-alinhamento-objetivos-2026-11-04.md, secao 3.4 ("Delta_rec, definicao da
Metodologia.tex:223 (sensibilidade)"): uma janela pos-falha esta "na linha de base" se a contagem de
erros nao excede o quantil de 95 % de Binomial(n_j, p_ref+), com p_ref+ = limite superior de 95 % da
taxa de erro das execucoes sem falha (regra de tres se a contagem for zero). Este script calcula p_ref+.

Uso (da raiz do repositorio; scipy esta no site do usuario, por isso -E -P, decisao G2 de 28/10):
  python -E -P analysis/referencia_erro.py --saida referencia-erro.json [--marcadores-nome marcadores.txt]
      CSV [CSV ...]

Cada CSV e um rep-NNN.csv do contrato 2 (meta-csv-rep-NNN.json ao lado). Populacao: todos os eventos
de todos os CSVs juntos; erro = ERROR_TRANSPORT ou ERROR_UNAVAILABLE (os mesmos de comparar_bracos e
do agregador do controlador). Guarda: um CSV cujo diretorio tem marcador de falha com `event=start`
e recusado (a referencia e so de execucoes sem falha). Quais execucoes entram e decisao do
pre-registro (B-40), nao deste script.

p_ref+ = limite superior UNILATERAL de 95 % de Clopper-Pearson, Beta(0,95; x + 1, n - x). Com x = 0
ele e 1 - 0,05^(1/n), o mesmo numero da regra de tres de comparar_bracos.regra_de_tres
(`hanley_lippmanhand_zero_1983`). A ressalva de independencia do plano 3.3 vale aqui: eventos da
mesma execucao nao sao independentes.

Exit: 0 ok, 2 uso/contrato/guarda/saida existente (nada e escrito).
"""
import argparse
import hashlib
import json
import math
import os
import sys
from pathlib import Path

from scipy import stats

sys.path.insert(0, str(Path(__file__).resolve().parent))   # energia_proxy, fases

import energia_proxy  # noqa: E402
import fases as _fases  # noqa: E402

ESQUEMA = "referencia-erro/1"
CONFIANCA = 0.95          # plano de alinhamento 3.4 ("limite superior de 95 %")
ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")
METODO = "Clopper-Pearson unilateral superior; x = 0: 1 - 0,05^(1/n) (regra de tres)"


class ErroUso(Exception):
    """Uso invalido, contrato violado, guarda ou saida existente: exit 2."""


def limite_superior(n_erros, n_eventos, confianca=CONFIANCA):
    """Limite superior unilateral de Clopper-Pearson da proporcao n_erros / n_eventos."""
    x, n = int(n_erros), int(n_eventos)
    if n <= 0:
        raise ErroUso("sem eventos: limite superior indefinido")
    if not 0 <= x <= n:
        raise ErroUso("contagem de erros fora de [0, n]: %d de %d" % (x, n))
    if x == n:
        return 1.0
    if x == 0:
        return 1.0 - (1.0 - confianca) ** (1.0 / n)
    return float(stats.beta.ppf(confianca, x + 1, n - x))


def _sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def _rel(p):
    try:
        return Path(os.path.relpath(Path(p).resolve(), Path.cwd().resolve())).as_posix()
    except ValueError:
        return Path(p).resolve().as_posix()


def gerar(csvs, marcadores_nome="marcadores.txt"):
    """Dicionario do esquema referencia-erro/1 a partir de CSVs de execucoes sem falha."""
    if not csvs:
        raise ErroUso("nenhum CSV")
    vistos, execucoes = set(), []
    n_tot, x_tot = 0, 0
    for c in csvs:
        p = Path(c)
        chave = os.path.normcase(str(p.resolve()))
        if chave in vistos:
            raise ErroUso("CSV repetido: %s" % p)
        vistos.add(chave)
        try:
            mk = _fases.ler_marcadores(p.parent / marcadores_nome)
        except ValueError as e:
            raise ErroUso("%s: %s" % (p.parent / marcadores_nome, e)) from None
        if mk["inicio"] is not None:
            raise ErroUso("execucao com marcador de falha (start) nao entra na referencia sem falha: %s" % p)
        try:
            ev = energia_proxy.ler_eventos_inicio(p)
        except energia_proxy.ErroUso as e:
            raise ErroUso(str(e)) from None
        n = len(ev)
        x = sum(1 for e in ev if e[3] in ERROS)
        execucoes.append({"csv": _rel(p), "sha256": _sha256(p), "n_eventos": n, "n_erros": x})
        n_tot += n
        x_tot += x
    if n_tot == 0:
        raise ErroUso("CSVs sem eventos")
    script = Path(__file__).resolve()
    return {"esquema": ESQUEMA, "n_eventos": n_tot, "n_erros": x_tot,
            "taxa_observada": x_tot / n_tot, "p_ref_sup": limite_superior(x_tot, n_tot),
            "confianca": CONFIANCA, "metodo": METODO, "erros": list(ERROS),
            "nota": "eventos da mesma execucao nao sao independentes (plano de alinhamento 3.3)",
            "execucoes": execucoes, "marcadores_nome": marcadores_nome,
            "gerado_por": {"script": "analysis/referencia_erro.py", "sha256": _sha256(script)}}


def ler(caminho):
    """{"arquivo","sha256","p_ref_sup","n_eventos","n_erros"} de um referencia-erro/1 (ErroUso se invalido)."""
    p = Path(caminho)
    if not p.is_file():
        raise ErroUso("referencia de erro ausente: %s" % p)
    try:
        j = json.loads(p.read_text(encoding="utf-8"))
    except ValueError:
        raise ErroUso("referencia de erro ilegivel: %s" % p) from None
    if not isinstance(j, dict) or j.get("esquema") != ESQUEMA:
        raise ErroUso("esquema inesperado em %s: %r (exigido %s)"
                      % (p, j.get("esquema") if isinstance(j, dict) else None, ESQUEMA))
    v = j.get("p_ref_sup")
    if isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) or not 0 < v <= 1:
        raise ErroUso("p_ref_sup invalido em %s: %r" % (p, v))
    return {"arquivo": _rel(p), "sha256": _sha256(p), "p_ref_sup": float(v),
            "n_eventos": j.get("n_eventos"), "n_erros": j.get("n_erros")}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Referencia de taxa de erro sem falha (B-39.4).")
    ap.add_argument("--saida", required=True)
    ap.add_argument("--marcadores-nome", default="marcadores.txt")
    ap.add_argument("csvs", nargs="+")
    a = ap.parse_args(argv)
    try:
        saida = Path(a.saida)
        if saida.exists():
            raise ErroUso("saida ja existe (nunca sobrescreve): %s" % saida)
        r = gerar(a.csvs, a.marcadores_nome)
        dados = (json.dumps(r, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")
        with open(saida, "xb") as fh:
            fh.write(dados)
    except (ErroUso, FileExistsError) as e:
        print("ERRO: %s" % e, file=sys.stderr)
        return 2
    print("referencia de erro gravada em %s (p_ref_sup = %.6g; %d erros em %d eventos)"
          % (saida, r["p_ref_sup"], r["n_erros"], r["n_eventos"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
