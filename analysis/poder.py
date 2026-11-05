"""Poder do teste exato por troca de sinal sobre a diferenca de taxas de violacao (B-39.4, W-5).

Especificacao: docs/plano-alinhamento-objetivos-2026-11-04.md, secao 3.3, item 2, com as correcoes da V-W5
(docs/validacao-w5-2026-11-05.md, ponto 10; decisao P-7 do coordenador em nome do Gestor). Simula blocos
reamostrando execucoes observadas do braco A (calibracao, so braco A) e aplicando ao braco B uma reducao
hipotetica; devolve a fracao de simulacoes em que H_V seria rejeitada pela mesma regra de
comparar_bracos.regra_iu: p bilateral da troca de sinal <= alfa, media de d_b = y_B - y_A < 0 e braco A
com alguma violacao (senao H_V e nao avaliavel e nao conta como rejeicao).

Dois modelos (declarados; nenhum e expectativa de efeito):
  - "independente" (curva principal): y_A de cada bloco e uma taxa sorteada com reposicao entre as
    observadas; y_B e (1 - reducao) x OUTRA taxa sorteada de forma independente. Com reducao = 0 os dois
    bracos tem a mesma distribuicao (H0 com ruido), e a rejeicao estima o erro tipo I efetivo, <= alfa. E o
    pior caso entre correlacoes dentro do bloco nao negativas (rho >= 0, mesma semente nos dois bracos): com
    rho >= 0 o poder real nao e menor que o simulado. Com rho < 0 isso nao vale.
  - "correlacao-perfeita" (limite superior): y_B = (1 - reducao) x a MESMA taxa de y_A. Todo d_b nao nulo tem
    o sinal da reducao, e o poder e P(K >= k_min), K ~ Binomial(n, q_hat), k_min = menor k com 2/2^k <= alfa
    (a conta do plano 3.3). E o melhor caso.
As duas curvas lado a lado dao a faixa para escolher 8, 10 ou 12 blocos.

alfa da curva = 0,05 / m, o do primeiro passo do Holm entre as m celulas de teste do pre-registro (0,025 com
m = 2, 0,0167 com m = 3; plano de execucao 5.2). m e obrigatorio e sem padrao.

Uso (da raiz do repositorio; numpy esta no site do usuario, por isso -E -P, decisao G2 de 28/10):
  python -E -P analysis/poder.py --taxas-a taxas.txt --semente S --n-sim N --m-celulas M --saida curva.json
      [--blocos 8,10,12] [--reducoes 0,0.1,...,1]

taxas.txt: uma taxa por linha (por mil acoes, como taxa_violacoes_por_1000), linhas vazias e com # sao
ignoradas. Padroes com origem: blocos 8, 10 e 12 e reducoes de 0 a 100 % em passos de 10 % (plano 3.3,
item 2); alfa 0,05 (Metodologia.tex:229).

Gerador: numpy default_rng(SeedSequence([semente, n_blocos, round(reducao x 10^6)])), um fluxo por ponto
da curva, como no restante da analise (o mesmo fluxo nos dois modelos). Exit: 0 ok, 2 uso/entrada/saida
existente.
"""
import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))   # comparar_bracos

import comparar_bracos as _cb  # noqa: E402

ESQUEMA = "poder-troca-de-sinal/2"
ALFA = _cb.ALFA
BLOCOS_PADRAO = (8, 10, 12)
REDUCOES_PADRAO = tuple(round(0.1 * i, 1) for i in range(11))
MODELOS = ("independente", "correlacao-perfeita")
LOTE = 512


class ErroUso(Exception):
    """Uso invalido ou entrada invalida: exit 2."""


def _sinais(n):
    return (((np.arange(2 ** n)[:, None] >> np.arange(n)) & 1) * 2 - 1).astype(float)


def p_troca_de_sinal_lote(d):
    """p bilaterais exatos por troca de sinal de cada linha de d (m x n), mesma regra de permutacao_exata.

    p = #{arranjos com |media| >= |media observada| - 1e-12} / 2^n, com a identidade incluida.
    """
    d = np.asarray(d, dtype=float)
    if d.ndim != 2 or d.shape[1] == 0:
        raise ErroUso("d deve ser matriz m x n com n >= 1")
    n = d.shape[1]
    if n > _cb.N_MAX_PERMUTACAO:
        raise ErroUso("troca de sinal exata limitada a n <= %d blocos (n = %d)" % (_cb.N_MAX_PERMUTACAO, n))
    s = _sinais(n)
    out = np.empty(d.shape[0], dtype=float)
    for ini in range(0, d.shape[0], LOTE):
        bloco = d[ini:ini + LOTE]
        medias = bloco @ s.T / n
        obs = np.abs(bloco.mean(axis=1))[:, None]
        out[ini:ini + LOTE] = np.count_nonzero(np.abs(medias) >= obs - _cb.TOL_PERMUTACAO, axis=1) / 2 ** n
    return out


def k_minimo(alfa):
    """Menor numero de blocos nao nulos com p minimo 2/2^k <= alfa (plano 3.3)."""
    if not 0.0 < float(alfa) < 1.0:
        raise ErroUso("alfa fora de (0, 1): %r" % (alfa,))
    k = 1
    while _cb.p_minimo_atingivel(k) > alfa:
        k += 1
    return k


def _validar(taxas_a, reducao, n_blocos, n_sim, alfa):
    t = np.asarray(list(taxas_a), dtype=float)
    if t.size == 0:
        raise ErroUso("nenhuma taxa do braco A")
    if not np.all(np.isfinite(t)) or np.any(t < 0):
        raise ErroUso("taxas devem ser finitas e >= 0")
    if not 0.0 <= float(reducao) <= 1.0:
        raise ErroUso("reducao fora de [0, 1]: %r" % (reducao,))
    if int(n_blocos) != n_blocos or not 1 <= int(n_blocos) <= _cb.N_MAX_PERMUTACAO:
        raise ErroUso("n_blocos deve ser inteiro em [1, %d]: %r" % (_cb.N_MAX_PERMUTACAO, n_blocos))
    if int(n_sim) != n_sim or int(n_sim) < 1:
        raise ErroUso("n_sim deve ser inteiro >= 1: %r" % (n_sim,))
    if not 0.0 < float(alfa) < 1.0:
        raise ErroUso("alfa fora de (0, 1): %r" % (alfa,))
    return t


def poder_troca_de_sinal(taxas_a, reducao, n_blocos, n_sim, semente, alfa=ALFA, modelo="independente"):
    """Fracao de n_sim simulacoes de n_blocos blocos em que H_V e rejeitada (modelos no cabecalho)."""
    if modelo not in MODELOS:
        raise ErroUso("modelo desconhecido: %r (use %s)" % (modelo, " ou ".join(MODELOS)))
    t = _validar(taxas_a, reducao, n_blocos, n_sim, alfa)
    n_blocos, n_sim = int(n_blocos), int(n_sim)
    rng = _cb._rng(semente, n_blocos, int(round(float(reducao) * 1e6)))
    ia = rng.integers(0, t.size, size=(n_sim, n_blocos))
    ib = rng.integers(0, t.size, size=(n_sim, n_blocos))
    ya = t[ia]
    yb = (1.0 - float(reducao)) * (ya if modelo == "correlacao-perfeita" else t[ib])
    d = yb - ya
    p = p_troca_de_sinal_lote(d)
    avaliavel = np.any(ya > 0, axis=1)
    rejeita = (p <= alfa) & (d.mean(axis=1) < 0) & avaliavel
    return float(np.count_nonzero(rejeita)) / n_sim


def curva_poder(taxas_a, n_sim, semente, m_celulas, blocos=BLOCOS_PADRAO, reducoes=REDUCOES_PADRAO):
    """{"blocos", "reducoes", "poder": {modelo: {str(n): [poder por reducao]}}, "alfa" = 0,05/m, ...}."""
    if isinstance(m_celulas, bool) or int(m_celulas) != m_celulas or int(m_celulas) < 1:
        raise ErroUso("m_celulas deve ser inteiro >= 1 (numero de celulas de teste do pre-registro): %r"
                      % (m_celulas,))
    m = int(m_celulas)
    alfa = ALFA / m
    t = _validar(taxas_a, 0.0, 1, n_sim, alfa)
    out = {modelo: {str(int(n)): [poder_troca_de_sinal(t, r, n, n_sim, semente, alfa, modelo) for r in reducoes]
                    for n in blocos}
           for modelo in MODELOS}
    return {"blocos": [int(n) for n in blocos], "reducoes": [float(r) for r in reducoes], "poder": out,
            "m_celulas": m, "alfa": alfa, "k_minimo": k_minimo(alfa), "n_sim": int(n_sim), "semente": int(semente),
            "n_taxas": int(t.size), "q_hat": float(np.count_nonzero(t > 0)) / t.size}


def ler_taxas(caminho):
    p = Path(caminho)
    if not p.is_file():
        raise ErroUso("arquivo de taxas ausente: %s" % p)
    out = []
    for i, bruta in enumerate(p.read_text(encoding="utf-8").splitlines(), start=1):
        linha = bruta.strip()
        if not linha or linha.startswith("#"):
            continue
        try:
            v = float(linha)
        except ValueError:
            raise ErroUso("linha %d de %s nao e numero: %r" % (i, p, linha)) from None
        if not math.isfinite(v) or v < 0:
            raise ErroUso("linha %d de %s fora de [0, inf): %r" % (i, p, linha))
        out.append(v)
    if not out:
        raise ErroUso("nenhuma taxa em %s" % p)
    return out


def _numeros(txt, tipo):
    try:
        return tuple(tipo(x) for x in txt.split(",") if x.strip())
    except ValueError:
        raise ErroUso("lista invalida: %r" % txt) from None


def main(argv=None):
    ap = argparse.ArgumentParser(description="Curva de poder da troca de sinal (B-39.4).")
    ap.add_argument("--taxas-a", required=True)
    ap.add_argument("--semente", type=int, required=True)
    ap.add_argument("--n-sim", type=int, required=True)
    ap.add_argument("--m-celulas", type=int, required=True)    # P-7 da V-W5: alfa = 0,05/m, sem padrao
    ap.add_argument("--saida", required=True)
    ap.add_argument("--blocos", default=",".join(str(x) for x in BLOCOS_PADRAO))
    ap.add_argument("--reducoes", default=",".join(repr(x) for x in REDUCOES_PADRAO))
    a = ap.parse_args(argv)
    try:
        saida = Path(a.saida)
        if saida.exists():
            raise ErroUso("saida ja existe (nunca sobrescreve): %s" % saida)
        taxas = ler_taxas(a.taxas_a)
        r = curva_poder(taxas, a.n_sim, a.semente, a.m_celulas, _numeros(a.blocos, int),
                        _numeros(a.reducoes, float))
        script = Path(__file__).resolve()
        r = {"esquema": ESQUEMA, **r,
             "taxas_a": {"arquivo": Path(a.taxas_a).as_posix(),
                         "sha256": hashlib.sha256(Path(a.taxas_a).read_bytes()).hexdigest()},
             "modelos": {"independente": "y_B = (1 - reducao) x taxa sorteada independente (pior caso com rho >= 0)",
                         "correlacao-perfeita": "y_B = (1 - reducao) x y_A do mesmo sorteio (limite superior)",
                         "regra": "rejeita com p <= alfa = 0,05/m, media d_b < 0 e braco A com violacao"},
             "gerado_por": {"script": "analysis/poder.py", "sha256": hashlib.sha256(script.read_bytes()).hexdigest()}}
        dados = (json.dumps(r, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")
        with open(saida, "xb") as fh:
            fh.write(dados)
    except (ErroUso, _cb.ErroUso, FileExistsError) as e:
        print("ERRO: %s" % e, file=sys.stderr)
        return 2
    print("curva de poder gravada em %s" % saida)
    return 0


if __name__ == "__main__":
    sys.exit(main())
