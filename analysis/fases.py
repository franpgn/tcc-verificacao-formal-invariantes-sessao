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
import json
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
    """Rotulo da fase do instante t_ns (epoch ns), igual a Marcadores.faseDe (:137-155)."""
    if inicio is None or t_ns < inicio:
        return ANTES
    if fim is None or t_ns <= fim:
        if split is None:
            return DURANTE
        return DURANTE_PRE_SPLIT if t_ns < split else DURANTE_POS_SPLIT
    if divergencia_persistente:
        return RECUPERACAO
    limite = fim + w_recovery_ns
    if convergencia_abs_ns is not None:
        limite = max(limite, convergencia_abs_ns)
    return RECUPERACAO if t_ns <= limite else POS_CONVERGENCIA


def ler_marcadores(caminho):
    """{"inicio","split","fim"} do arquivo de marcadores, como Marcadores.ler/parse (:71-117).

    Arquivo inexistente: tudo None. Linhas vazias e iniciadas por # sao ignoradas. Primeiro start e
    primeiro split valem; o ultimo end vale. Linha malformada: ValueError.
    """
    out = {"inicio": None, "split": None, "fim": None}
    p = Path(caminho)
    if not p.exists():
        return out
    for bruta in p.read_text(encoding="utf-8").splitlines():
        linha = bruta.strip()
        if not linha or linha.startswith("#"):
            continue
        evento, t = None, None
        for tok in linha.split():
            eq = tok.find("=")
            if eq <= 0:
                raise ValueError("linha de marcador invalida: " + linha)
            k, v = tok[:eq], tok[eq + 1:]
            if k == "event":
                evento = v
            elif k == "t_epoch_ns":
                try:
                    t = int(v)
                except ValueError:
                    raise ValueError("t_epoch_ns invalido: " + linha) from None
        if evento is None or t is None:
            raise ValueError("marcador sem event/t_epoch_ns: " + linha)
        if evento == "start":
            if out["inicio"] is None:
                out["inicio"] = t
        elif evento == "split":
            if out["split"] is None:
                out["split"] = t
        elif evento == "end":
            out["fim"] = t
        else:
            raise ValueError("evento de marcador desconhecido: " + linha)
    return out


def ler_convergencia(caminho):
    """(t_convergencia_abs_ns, divergencia_persistente) do JSON de ConvergenciaLoop.escreverResumo.

    Arquivo inexistente: (None, False), isto e, a recuperacao vai ate fim + W.
    """
    p = Path(caminho)
    if not p.exists():
        return None, False
    j = json.loads(p.read_text(encoding="utf-8"))
    t = j.get("t_convergencia_abs_ns")
    return (None if t is None else int(t)), bool(j.get("divergencia_persistente", False))


def intervalos_de_fase(t0, t1, inicio, split, fim, w_recovery_ns=0, convergencia_abs_ns=None,
                       divergencia_persistente=False):
    """Lista ordenada de (rotulo, t_ini, t_fim), meio-abertos [t_ini, t_fim), cobrindo [t0, t1).

    As fronteiras sao as de fase_de em ns inteiros: durante = [inicio, fim + 1), recuperacao =
    [fim + 1, limite + 1). Cada trecho entre fronteiras consecutivas tem fase constante; trechos
    vizinhos com o mesmo rotulo sao fundidos.
    """
    if t1 <= t0:
        raise ValueError("janela vazia: [%r, %r)" % (t0, t1))
    cortes = set()
    if inicio is not None:
        cortes.add(inicio)
        if split is not None:
            cortes.add(split)
        if fim is not None:
            cortes.add(fim + 1)
            if not divergencia_persistente:
                limite = fim + w_recovery_ns
                if convergencia_abs_ns is not None:
                    limite = max(limite, convergencia_abs_ns)
                cortes.add(limite + 1)
    pontos = [t0] + sorted(c for c in cortes if t0 < c < t1) + [t1]
    out = []
    for a, b in zip(pontos, pontos[1:]):
        rot = fase_de(a, inicio, split, fim, w_recovery_ns, convergencia_abs_ns, divergencia_persistente)
        if out and out[-1][0] == rot:
            out[-1] = (rot, out[-1][1], b)
        else:
            out.append((rot, a, b))
    return out


def expandir_fases(nomes):
    """Conjunto de rotulos de uma lista de fases pedida ("durante" inclui pre/pos split)."""
    out = set()
    for n in nomes:
        n = n.strip()
        if n not in ROTULOS:
            raise ValueError("fase desconhecida: %r" % n)
        out.update(FAMILIAS.get(n, (n,)))
    return out
