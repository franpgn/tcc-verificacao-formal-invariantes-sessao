"""Referencia de p99 por operacao para o controlador (B-39.1).

Uso (da raiz do repositorio):
  python -I analysis/referencia_p99.py --cenario S1|S2|S3 --perfil sync|async --saida ref.json
      [--n-min 1000] CSV [CSV ...]

Populacao (decisao D2 do Gestor, 2026-10-27): TODOS os eventos de cada valor de `operation`, com
qualquer return_code, de todos os CSVs juntos. E a mesma populacao que AgregadorDeJanela conta como
elegivel (AgregadorDeJanela.java:126-129), para que a taxa de excedencia sem falha seja <= 1 %. Difere
de proposito do p99 do rebaseline (DT-009, so desfechos validos).

p99 por posto mais proximo: ordenado[ceil(p*n) - 1], aritmetica exata, copia de
analysis/rebaseline.py:75-83 (o rebaseline e do bundle congelado e nao e importado aqui; um teste de
paridade compara as duas). Operacao com n < n_min fica fora de p99_ns e vai para "omitidas".

Exit: 0 ok, 2 uso/guarda (nada e escrito). So biblioteca padrao.
"""
import argparse
import csv
import hashlib
import json
import os
import re
import sys
from collections import defaultdict
from fractions import Fraction
from pathlib import Path

ESQUEMA = "referencia-p99/1"
METODO = "posto-mais-proximo"
POPULACAO = "todos-os-eventos-por-operation"
CENARIOS = ("S1", "S2", "S3")
PERFIS = ("sync", "async")
N_MIN_PADRAO = 1000
CABECALHO = ("op_id", "operation", "start_ns", "end_ns", "replica", "return_code", "key")

# Copia de analysis/rebaseline.py:60-61 (PR §7). Usada pelo energia_proxy (operacoes uteis).
VALIDOS = {"Login": ("OK",), "Validate": ("VALID", "INVALID"), "Logout": ("NONE",),
           "IncrementFailure": ("COUNTED",), "ResetFailures": ("NONE",)}

# W-10: operacoes de instrumentacao no rep-NNN.csv (sonda de replay do S3, --s3-replay-probe; ver
# docs/contrato-csv-v2.md). Nao sao carga: ficam fora da referencia de p99 e, pelo energia_proxy, dos
# percentis por operacao, da taxa de erro, das fronteiras da execucao e da vazao util do comparar_bracos.
OPERACOES_INSTRUMENTACAO = ("ReplayValidate",)


class ErroUso(Exception):
    """Uso invalido, contrato violado ou guarda: exit 2."""


def percentil(valores, p):
    """Posto mais proximo: ordenado[ceil(p*n) - 1], com p em fracao; aritmetica exata.

    Copia literal de analysis/rebaseline.py:75-83 (PR §7, DT-009); paridade em test_referencia_p99 R1.
    """
    if not valores:
        return None
    s = sorted(valores)
    n = len(s)
    fp = Fraction(str(p))
    k = -((-fp.numerator * n) // fp.denominator)   # ceil(p*n) exato
    return s[min(n - 1, max(0, k - 1))]


def caminho_meta(csv_path):
    """meta-csv-rep-NNN.json ao lado de rep-NNN.csv (MetaCsv.gravar)."""
    p = Path(csv_path)
    m = re.fullmatch(r"rep-(\d+)\.csv", p.name)
    if not m:
        raise ErroUso("nome de CSV fora do padrao rep-NNN.csv: %s" % p)
    return p.with_name("meta-csv-rep-%s.json" % m.group(1))


def _exigir_contrato2(csv_path):
    meta = caminho_meta(csv_path)
    if not meta.exists():
        raise ErroUso("CSV sem %s (contrato 2 exigido): %s" % (meta.name, csv_path))
    try:
        versao = json.loads(meta.read_text(encoding="utf-8")).get("contrato_csv")
    except ValueError:
        raise ErroUso("meta ilegivel: %s" % meta) from None
    if versao != 2:
        raise ErroUso("contrato_csv = %r em %s (exigido 2)" % (versao, meta))


def ler_eventos(csv_path, exigir_contrato2=True, exigir_antes=True):
    """(latencias por operation, numero de linhas de dados) de um rep-NNN.csv.

    Cabecalho exato op_id,operation,start_ns,end_ns,replica,return_code,key[,phase]. Com exigir_antes,
    qualquer phase diferente de "antes" e recusada (execucao com falha nao serve de referencia). As
    operacoes de OPERACOES_INSTRUMENTACAO (W-10) sao puladas e nao contam nas linhas.
    Cada valor e a tupla (latencia_ns, return_code); latencia = end_ns - start_ns.
    """
    p = Path(csv_path)
    if not p.is_file():
        raise ErroUso("CSV ausente: %s" % p)
    if exigir_contrato2:
        _exigir_contrato2(p)
    por_op = defaultdict(list)
    linhas = 0
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        cab = tuple(next(r, ()))
        if cab not in (CABECALHO, CABECALHO + ("phase",)):
            raise ErroUso("cabecalho inesperado em %s: %s" % (p, ",".join(cab)))
        com_fase = len(cab) == len(CABECALHO) + 1
        for x in r:
            if not x:
                continue
            if len(x) != len(cab):
                raise ErroUso("linha com %d colunas em %s (esperado %d)" % (len(x), p, len(cab)))
            if x[1] in OPERACOES_INSTRUMENTACAO:
                continue  # W-10: sonda de replay, fora da referencia
            if exigir_antes and com_fase and x[7] != "antes":
                raise ErroUso("phase %r em %s: execucao com falha nao serve de referencia" % (x[7], p))
            try:
                lat = int(x[3]) - int(x[2])
            except ValueError:
                raise ErroUso("start_ns/end_ns nao inteiros em %s: %s" % (p, x)) from None
            por_op[x[1]].append((lat, x[5]))
            linhas += 1
    return dict(por_op), linhas


def _sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def _relativo(p):
    """Caminho relativo ao diretorio corrente, com /; absoluto se estiver em outro volume."""
    try:
        return Path(os.path.relpath(Path(p).resolve(), Path.cwd().resolve())).as_posix()
    except ValueError:
        return Path(p).resolve().as_posix()


def gerar_referencia(csvs, cenario, perfil, n_min=N_MIN_PADRAO):
    """Dicionario do esquema referencia-p99/1 (ErroUso nas guardas)."""
    if cenario not in CENARIOS:
        raise ErroUso("cenario invalido: %r (use %s)" % (cenario, "|".join(CENARIOS)))
    if perfil not in PERFIS:
        raise ErroUso("perfil invalido: %r (use %s)" % (perfil, "|".join(PERFIS)))
    if not isinstance(n_min, int) or n_min < 1:
        raise ErroUso("n_min invalido: %r" % (n_min,))
    if not csvs:
        raise ErroUso("nenhum CSV")
    lat = defaultdict(list)
    origem = []
    for c in csvs:
        por_op, linhas = ler_eventos(c)
        for op, vs in por_op.items():
            lat[op].extend(v for v, _ in vs)
        origem.append({"arquivo": _relativo(c), "sha256": _sha256(c), "linhas": linhas})
    p99_ns, n, omitidas = {}, {}, {}
    for op in sorted(lat):
        vs = lat[op]
        if len(vs) >= n_min:
            p99_ns[op] = int(percentil(vs, 0.99))
            n[op] = len(vs)
        else:
            omitidas[op] = len(vs)
    if not p99_ns:
        raise ErroUso("nenhuma operacao com n >= %d (omitidas: %s)" % (n_min, omitidas))
    script = Path(__file__).resolve()
    return {"esquema": ESQUEMA, "cenario": cenario, "perfil": perfil, "metodo": METODO,
            "populacao": POPULACAO, "n_min": n_min, "p99_ns": p99_ns, "n": n, "omitidas": omitidas,
            "origem": origem,
            "gerado_por": {"script": "analysis/referencia_p99.py", "sha256": _sha256(script)}}


def excedencia(csv_path, ref):
    """{op: (elegiveis, excedencias)} com a regra do AgregadorDeJanela (:126-129).

    Elegivel = evento cuja operation esta em ref["p99_ns"], qualquer return_code; excedencia =
    latencia > p99_ref (estrito). Operacoes fora da referencia nao contam; as da referencia sem
    evento no CSV aparecem com (0, 0). Nao exige contrato nem phase (serve ao replay do B-38).
    """
    p99 = ref["p99_ns"]
    por_op, _ = ler_eventos(csv_path, exigir_contrato2=False, exigir_antes=False)
    out = {}
    for op in sorted(p99):
        vs = por_op.get(op, ())
        out[op] = (len(vs), sum(1 for v, _ in vs if v > p99[op]))
    return out


def gravar_json(destino, obj):
    """JSON deterministico: chaves ordenadas, indentacao 1, UTF-8, LF; falha se o destino existe."""
    dados = (json.dumps(obj, indent=1, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8")
    with open(destino, "xb") as fh:
        fh.write(dados)


def main(argv=None):
    ap = argparse.ArgumentParser(description="Referencia de p99 por operacao (B-39.1).")
    ap.add_argument("--cenario", required=True)
    ap.add_argument("--perfil", required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--n-min", type=int, default=N_MIN_PADRAO)
    ap.add_argument("csvs", nargs="+")
    a = ap.parse_args(argv)
    try:
        if Path(a.saida).exists():
            raise ErroUso("saida ja existe (nunca sobrescreve): %s" % a.saida)
        r = gerar_referencia(a.csvs, a.cenario, a.perfil, a.n_min)
        gravar_json(a.saida, r)
    except ErroUso as e:
        print("ERRO: %s" % e, file=sys.stderr)
        return 2
    except FileExistsError:
        print("ERRO: saida ja existe (nunca sobrescreve): %s" % a.saida, file=sys.stderr)
        return 2
    print("referencia gravada em %s: %s" % (a.saida, ", ".join("%s=%d" % kv for kv in r["p99_ns"].items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
