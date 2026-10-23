"""Gerador da matriz do rebaseline S1/S2 v3 (DT-009, DT-011).

Pre-registro: docs/pre-registro-rebaseline-aprovado-2026-10-23.md (PR). Nada aqui e parametro livre:
os valores do PR entram como constantes nomeadas, com a secao no comentario. So biblioteca padrao.

Uso:
  python3 -I scripts/rebaseline_matriz.py gerar --ordem-seed 231023 --s0 50000 --saida <tsv>

Escreve o TSV (cabecalho + 132 linhas, na ordem de execucao: piloto primeiro, depois as rodadas 1..10)
e imprime o SHA-256 do arquivo gerado. Exit 0; 2 em uso invalido. As opcoes --ordem-seed e --s0 existem
para que o comando registrado no complemento da DT-009 seja explicito; o runner confere o SHA-256.
"""
import argparse
import hashlib
import random
import sys
from pathlib import Path

ORDEM_SEED = 231023          # PR secao 3 (sorteio da ordem das rodadas)
S0 = 50000                   # PR secao 4 (R-S1: --seed = S0 + 100*i)
PASSO_SEMENTE = 100          # PR secao 4
RODADAS_CAMPANHA = 10        # PR secao 2 (10 repeticoes por celula)
I_PILOTO = 1000              # PR secao 3 (piloto: i = 1000 + posicao)
CENARIOS = ("S1", "S2")      # PR secao 2
MODOS = ("sync", "async")    # PR secao 2
FALHAS = ("none", "F1", "F3")  # PR secao 2
# PR secao 2: lista canonica, cenario x modo x falha, nessa ordem de aninhamento.
CELULAS = [f"{c}-{m}-{f}" for c in CENARIOS for m in MODOS for f in FALHAS]
COLUNAS = ("i", "fase", "noite", "rodada", "posicao", "celula", "cenario", "modo", "falha", "seed")


def ordem_da_rodada(ordem_seed, r):
    """PR secao 3: random.Random(ORDEM_SEED * 1000 + r).shuffle(lista canonica copiada)."""
    lista = list(CELULAS)
    random.Random(ordem_seed * 1000 + r).shuffle(lista)
    return lista


def noite_da_rodada(r):
    """PR secao 3: rodada 0 = piloto; noite 1 = rodadas 1..5; noite 2 = rodadas 6..10."""
    if r == 0:
        return "piloto"
    return "noite1" if r <= 5 else "noite2"


def indice(r, posicao):
    """PR secao 3: piloto i = 1000 + posicao; campanha i = 12*(r-1) + posicao."""
    return I_PILOTO + posicao if r == 0 else len(CELULAS) * (r - 1) + posicao


def linhas(ordem_seed=ORDEM_SEED, s0=S0):
    out = []
    for r in range(0, RODADAS_CAMPANHA + 1):
        for pos, cel in enumerate(ordem_da_rodada(ordem_seed, r)):
            cen, modo, falha = cel.split("-")
            i = indice(r, pos)
            out.append({"i": i, "fase": "piloto" if r == 0 else "campanha", "noite": noite_da_rodada(r),
                        "rodada": r, "posicao": pos, "celula": cel, "cenario": cen, "modo": modo,
                        "falha": falha, "seed": s0 + PASSO_SEMENTE * i})
    return out


def para_tsv(registros):
    txt = "\t".join(COLUNAS) + "\n"
    for x in registros:
        txt += "\t".join(str(x[c]) for c in COLUNAS) + "\n"
    return txt


def gerar(ordem_seed, s0, saida):
    dados = para_tsv(linhas(ordem_seed, s0)).encode("ascii")
    Path(saida).write_bytes(dados)
    return hashlib.sha256(dados).hexdigest()


def main(argv):
    ap = argparse.ArgumentParser(prog="rebaseline_matriz.py")
    sub = ap.add_subparsers(dest="cmd")
    g = sub.add_parser("gerar")
    g.add_argument("--ordem-seed", type=int, required=True)
    g.add_argument("--s0", type=int, required=True)
    g.add_argument("--saida", required=True)
    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return 2 if e.code else 0
    if a.cmd != "gerar":
        ap.print_usage(sys.stderr)
        return 2
    sha = gerar(a.ordem_seed, a.s0, a.saida)
    print("%s  %s" % (sha, a.saida))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
