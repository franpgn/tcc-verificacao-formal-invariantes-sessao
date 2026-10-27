"""Fases de uma execucao com falha (B-39.2): porte de Marcadores.faseDe para Python.

Fonte de verdade: workload/src/main/java/br/unipampa/tcc/session/Marcadores.java (parse :78-117,
faseDe :137-155). Rotulos e fronteiras sao os mesmos:

  antes              t < inicio, ou sem marcador de inicio
  durante            inicio <= t <= fim (fechado nas duas pontas), sem marcador de split
  durante-pre-split  inicio <= t < split  (e t <= fim)
  durante-pos-split  split <= t <= fim
  recuperacao        fim < t <= max(fim + W, convergencia); ate o fim com divergencia persistente
  pos-convergencia   depois disso

Sem marcador de fim (so start), tudo a partir do inicio e "durante" (Marcadores.java:141).
Modulo puro, so biblioteca padrao; roda com python -I.
"""
from pathlib import Path

ANTES = "antes"
DURANTE = "durante"
DURANTE_PRE_SPLIT = "durante-pre-split"
DURANTE_POS_SPLIT = "durante-pos-split"
RECUPERACAO = "recuperacao"
POS_CONVERGENCIA = "pos-convergencia"
ROTULOS = (ANTES, DURANTE, DURANTE_PRE_SPLIT, DURANTE_POS_SPLIT, RECUPERACAO, POS_CONVERGENCIA)
# "durante" pedido numa lista de fases abrange as duas metades quando ha marcador de split.
FAMILIAS = {DURANTE: (DURANTE, DURANTE_PRE_SPLIT, DURANTE_POS_SPLIT)}


def fase_de(t_ns, inicio, split, fim, w_recovery_ns=0, convergencia_abs_ns=None,
            divergencia_persistente=False):
    """Rotulo da fase do instante t_ns (epoch ns), igual a Marcadores.faseDe."""
    raise NotImplementedError("fase_de")


def ler_marcadores(caminho):
    """{"inicio","split","fim"} do arquivo de marcadores (None quando ausente), como Marcadores.ler."""
    raise NotImplementedError("ler_marcadores")


def ler_convergencia(caminho):
    """(t_convergencia_abs_ns, divergencia_persistente) de convergencia-resumo-rep-NNN.json."""
    raise NotImplementedError("ler_convergencia")


def intervalos_de_fase(t0, t1, inicio, split, fim, w_recovery_ns=0, convergencia_abs_ns=None,
                       divergencia_persistente=False):
    """Lista ordenada de (rotulo, t_ini, t_fim), meio-abertos, cobrindo [t0, t1) sem lacuna."""
    raise NotImplementedError("intervalos_de_fase")


def expandir_fases(nomes):
    """Conjunto de rotulos de uma lista de fases pedida ("durante" inclui pre/pos split)."""
    raise NotImplementedError("expandir_fases")
