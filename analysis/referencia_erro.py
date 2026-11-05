"""Referencia de taxa de erro sem falha (B-39.4, W-5): ESQUELETO do commit test:.

A implementacao entra no commit feat: seguinte. Aqui so os nomes que os testes importam.
"""
import sys

ESQUEMA = "referencia-erro/1"
CONFIANCA = 0.95
ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")


class ErroUso(Exception):
    """Uso invalido, contrato violado, guarda ou saida existente: exit 2."""


def limite_superior(n_erros, n_eventos, confianca=CONFIANCA):
    raise NotImplementedError("esqueleto")


def gerar(csvs, marcadores_nome="marcadores.txt"):
    raise NotImplementedError("esqueleto")


def ler(caminho):
    raise NotImplementedError("esqueleto")


def main(argv=None):
    raise NotImplementedError("esqueleto")


if __name__ == "__main__":
    sys.exit(main())
