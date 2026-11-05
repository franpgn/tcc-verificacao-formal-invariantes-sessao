"""Poder do teste exato por troca de sinal (B-39.4, W-5): ESQUELETO do commit test:.

A implementacao entra no commit feat: seguinte. Aqui so os nomes que os testes importam.
"""
import sys

ESQUEMA = "poder-troca-de-sinal/1"
ALFA = 0.05
BLOCOS_PADRAO = (8, 10, 12)
REDUCOES_PADRAO = tuple(round(0.1 * i, 1) for i in range(11))
LOTE = 512


class ErroUso(Exception):
    """Uso invalido ou entrada invalida: exit 2."""


def p_troca_de_sinal_lote(d):
    raise NotImplementedError("esqueleto")


def poder_troca_de_sinal(taxas_a, reducao, n_blocos, n_sim, semente, alfa=ALFA):
    raise NotImplementedError("esqueleto")


def curva_poder(taxas_a, n_sim, semente, blocos=BLOCOS_PADRAO, reducoes=REDUCOES_PADRAO, alfa=ALFA):
    raise NotImplementedError("esqueleto")


def main(argv=None):
    raise NotImplementedError("esqueleto")


if __name__ == "__main__":
    sys.exit(main())
