"""Derivacao da calibracao S3 (W-9): esqueleto do vermelho (a implementacao vem no commit feat)."""
import sys

ESQUEMA = "calibracao-s3/1"


class ErroUso(Exception):
    """Uso invalido, contrato violado ou saida existente: exit 2."""


def _vazio(*_a, **_k):
    return {"valor": None, "N": None, "motivo": "nao implementado"}


limiar_entrada = limiar_saida = m_reentrada = klag = t_estavel = t_hold_viol = _vazio
teto_convergencia = calcular_w = viabilidade = exec_teto = _vazio


def resumo_lag(_rodadas):
    return None


def clopper_pearson(_x, _n, confianca=0.95):
    return None, None


def derivar(_campanha, _parametros):
    return {"esquema": None, "parametros": {}, "execucoes": {"usadas": [], "excluidas": [], "por_celula": {}}}


def main(argv=None):
    return 1


if __name__ == "__main__":
    sys.exit(main())
