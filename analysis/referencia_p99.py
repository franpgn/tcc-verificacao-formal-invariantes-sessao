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

Exit: 0 ok, 2 uso/guarda (nada e escrito), 3 nao implementado. So biblioteca padrao.
"""
import sys

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


class ErroUso(Exception):
    """Uso invalido, contrato violado ou guarda: exit 2."""


def percentil(valores, p):
    """Posto mais proximo: ordenado[ceil(p*n) - 1]; copia de rebaseline.percentil."""
    raise NotImplementedError("percentil")


def caminho_meta(csv_path):
    """meta-csv-rep-NNN.json ao lado de rep-NNN.csv."""
    raise NotImplementedError("caminho_meta")


def ler_eventos(csv_path, exigir_contrato2=True, exigir_antes=True):
    """(latencias por operation, eventos) de um rep-NNN.csv do contrato 2."""
    raise NotImplementedError("ler_eventos")


def gerar_referencia(csvs, cenario, perfil, n_min=N_MIN_PADRAO):
    """Dicionario do esquema referencia-p99/1; ErroUso nas guardas."""
    raise NotImplementedError("gerar_referencia")


def excedencia(csv_path, ref):
    """{op: (elegiveis, excedencias)} com a regra do agregador (lat > p99_ref; fora da ref nao conta)."""
    raise NotImplementedError("excedencia")


def main(argv=None):
    print("nao implementado", file=sys.stderr)
    return 3


if __name__ == "__main__":
    sys.exit(main())
