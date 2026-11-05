"""Comparacao pareada A (shadow) x B (active) por blocos (B-39.3; B-39.4 / W-5, esquema /2).

Especificacao do /2: docs/plano-alinhamento-objetivos-2026-11-04.md, secoes 3.1 a 3.5 e Prompt 2, com a
regra de intersecao-uniao fixa (decisao D-1 do Gestor) e os acrescimos do W-5 de
docs/plano-execucao-2026-11-04.md, secao 9 (desfechos descritivos do controlador, tipos de celula,
k e p minimo, Holm entre celulas).

Uso (da raiz do repositorio; numpy e scipy estao no site do usuario, por isso -E -P e nao -I, decisao
G2 de 28/10):
  python -E -P analysis/comparar_bracos.py --manifesto M.tsv --semente-boot S --saida comparacao.json
      --delta-latencia DL --w-analise-s W
      [--celulas-teste C1,C2] [--celulas-descritivas C3] [--celulas-referencia C4]
      [--referencia-erro CELULA=referencia-erro.json ...] [--p-max-w P --razao-ocioso-central R]
      [--fases durante,recuperacao] [--rep 1] [--w-recovery-s 60] [--n-boot 10000] [--n-hier 2000]
      [--delta 0.10] [--marcadores-nome marcadores.txt]

Parametros SEM valor padrao (regra do autor: limiar sem origem no codigo fica obrigatorio; o pre-registro
B-40 passa o valor): --delta-latencia (delta_L da nao inferioridade do p99; a Proposta da 10 %,
Metodologia.tex:229) e --w-analise-s (W_c da censura do Delta_rec e janela do Delta_rec de
sensibilidade; derivada da vazao da calibracao, plano 3.5). Cada celula do manifesto tem de estar em
exatamente uma das listas --celulas-teste/--celulas-descritivas/--celulas-referencia (exit 2 se nao).

Manifesto TSV com cabecalho exato `bloco<TAB>celula<TAB>braco<TAB>execucao`, braco em {A, B}, um par
A/B por (celula, bloco); `execucao` e um diretorio (relativo ao manifesto, se nao for absoluto) com:
  rep-NNN.csv + meta-csv-rep-NNN.json (contrato 2)      desfechos de eventos, Delta_rec
  s3-acoes-rep-NNN.csv (contrato S3 v2, 10 colunas)     desfechos S3 (8 colunas: exit 2)
  marcadores.txt                                         fases, t_fim-falha (Delta_rec)
  convergencia-resumo-rep-NNN.json                       fases, Delta_conv (t_fim_carga_ns), divergencia
  energia-rep-NNN.json (energia_proxy.py)                CPU e energia (opcional; B39-R1 confere W e fases)
  violacoes-rep-NNN.csv (InvariantAuditor.dumpCsv)       taxa de violacoes (sem ele: desfecho ausente)
  decisoes-rep-NNN.jsonl (controlador, contrato v1.x)    descritivos do controlador e rastreabilidade

Desfechos por execucao e escala da estatistica por bloco:
  taxa_violacoes_por_1000   (I1 + I4 + I2 REAL) x 1000 / acoes S3, fases selecionadas    diferenca
  acao_s3_p99_com_espera    p99 da acao S3 (n >= 1000)                                   log da razao
  vazao_util                acoes uteis por s nas fases selecionadas                     log da razao
  delta_rec_s               ultimo evento anomalo depois do fim da falha, com censura    diferenca
  delta_rec_metodologia_s   primeira janela de W s na linha de base (sensibilidade)      diferenca
  delta_conv_s              t_convergencia_abs_ns - t_fim_carga_ns (nunca t_fim_ns)      diferenca
  divergencia_persistente   0/1 do resumo de convergencia                                diferenca
  energia_total_j_por_1000_uteis:<r>  energia estimada (P_max e r do energia_proxy)      log da razao
  demais (evento_p99:*, taxa_erro, acao_s3_p99_sem_espera, falhas_percebidas_por_1000,
  cpu_por_1000_uteis)                                                                    log da razao

Familias: co-principal-V (taxa de violacoes), co-principal-L (p99 da acao S3), nao-inferioridade (vazao
util), secundario (Holm dentro da celula) e sensibilidade (sem Holm: Delta_rec da Metodologia e energia
com r diferente do central).

Energia (so com --p-max-w e --razao-ocioso-central, juntos): o P_max de cada energia-rep-NNN.json tem de ser
o passado (exit 2 se nao) e a razao central tem de existir no JSON. Valores da campanha (decisao do
coordenador de 05/11, conferencia do Bibliotecario): --p-max-w 181 (Maximum Turbo Power do i5-14600KF,
`intel_i5_14600kf`) e --razao-ocioso-central 0.3 (Barroso e Holzle 2007, p. 36: processador com menos de
um terco do pico em atividade muito baixa); 0,5 (p. 34, servidor inteiro) fica como sensibilidade; 0,7
saiu por nao ter fonte. P_max cancela nas razoes entre bracos; so escala os joules absolutos.

Regra de sucesso por celula de teste (intersecao-uniao, Berger 1982; D-1): H_V rejeitada se o p bilateral
da troca de sinal sobre d_b = y_B - y_A for <= alfa e a media de d_b for < 0; H_L rejeitada se o limite
superior do IC t 95 % de exp(media ln(p99_B / p99_A)) for <= 1 + delta_L. p_L e o p do teste t
unilateral correspondente, em escala bilateral (p_L = 2 F_t), de modo que p_L <= alfa <=> H_L
rejeitada; com a media de d_b >= 0, p_V entra na IU como 1. p_IU = max(p_V, p_L). "melhoria" exige H_V
e H_L rejeitadas, vazao util nao inferior e rastreabilidade integral da trilha do braco B. Conclusao
global: Holm sobre os p_IU das celulas de teste (celula nao avaliavel entra com p = 1). H_V nao
avaliavel quando o braco A nao tem violacao em nenhum bloco (regra de tres por braco).

Grava o JSON e uma tabela Markdown (mesmo nome, .md); nunca sobrescreve.

Exit: 0 ok, 2 uso/contrato/saida existente.
"""
import argparse
import calendar
import csv
import hashlib
import json
import math
import os
import re
import sys
import time
from fractions import Fraction
from pathlib import Path

import numpy as np
from scipy import stats

sys.path.insert(0, str(Path(__file__).resolve().parent))   # fases, referencia_p99, energia_proxy, referencia_erro

import energia_proxy  # noqa: E402
import fases as _fases  # noqa: E402
import referencia_erro  # noqa: E402
import referencia_p99  # noqa: E402
from referencia_p99 import percentil  # noqa: E402  (posto mais proximo, copia de rebaseline)

ESQUEMA = "comparar-bracos/2"
CABECALHO_MANIFESTO = ("bloco", "celula", "braco", "execucao")
BRACOS = ("A", "B")
FASES_PADRAO = ("durante", "recuperacao")
N_BOOT_PADRAO = 10000
N_HIER_PADRAO = 2000
DELTA_PADRAO = 0.10          # vazao util: decisao do Gestor de 22/10 (plano de alinhamento 3.6)
N_MIN_P99 = 1000
N_MAX_PERMUTACAO = 16
TOL_PERMUTACAO = 1e-12
ALFA = 0.05                  # Metodologia.tex:229, :326 (plano de alinhamento 3.6: rastreavel)
CONFIANCA = 1.0 - ALFA       # IC 95 %
QUANTIL_LINHA_DE_BASE = 0.95  # plano de alinhamento 3.4: quantil de 95 % de Binomial(n_j, p_ref+)
DESFECHO_NI = "vazao_util"
DESFECHO_CPU = "cpu_por_1000_uteis"
DESFECHO_V = "taxa_violacoes_por_1000"
DESFECHO_L = "acao_s3_p99_com_espera"
DESFECHO_DREC = "delta_rec_s"
DESFECHO_DREC_MET = "delta_rec_metodologia_s"
DESFECHO_DCONV = "delta_conv_s"
DESFECHO_DIV = "divergencia_persistente"
PREFIXO_ENERGIA = "energia_total_j_por_1000_uteis:"
ESCALA_LOG = "log-razao"
ESCALA_DIF = "diferenca"
ESCALA_DIFERENCA = frozenset((DESFECHO_V, DESFECHO_DREC, DESFECHO_DREC_MET, DESFECHO_DCONV, DESFECHO_DIV))
FAMILIA_V = "co-principal-V"
FAMILIA_L = "co-principal-L"
FAMILIA_NI = "nao-inferioridade"
FAMILIA_SEC = "secundario"
FAMILIA_SENS = "sensibilidade"
TIPOS_CELULA = ("teste", "descritiva", "referencia")
MODO_ESPERADO = {"A": "shadow", "B": "active"}
ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")
INVARIANTES_VIOL = ("I1", "I2_REAL", "I4")
# Resultados de APLICAR que poem a acao na pilha (real ou virtual): ExecutorDeAcoes.java:436-440 (tudo
# menos FALHOU) e o shadow (PREVISTA); DESCARTADA e JA_ATIVA nao abrem intervalo.
RESULTADOS_ATIVA = ("APLICADA", "PREVISTA", "NAO_EXECUTADA")
# Origens de linha acao que vem de uma decisao (regra e t_fim_janela_ns obrigatorios): LacoDeControle
# (:313, :324, :327) e ExecutorDeAcoes (:454). ENCERRAMENTO e GANCHO_JVM saem com null (contrato v1.1).
ORIGENS_COM_DECISAO = ("POLITICA", "R4", "FALHA_NA_APLICACAO")


class ErroUso(Exception):
    """Uso invalido, manifesto incompleto, contrato violado ou saida existente: exit 2."""


ERROS_USO = (ErroUso, energia_proxy.ErroUso, referencia_p99.ErroUso, referencia_erro.ErroUso)


# ============================================================================= manifesto

def _chave_bloco(b):
    return (0, int(b), b) if b.isdigit() else (1, 0, b)


def ler_manifesto(caminho):
    """{celula: {bloco: {"A": Path, "B": Path}}}; ErroUso com bloco incompleto, duplicado ou repetido."""
    p = Path(caminho)
    if not p.is_file():
        raise ErroUso("manifesto ausente: %s" % p)
    linhas = [x.rstrip("\r") for x in p.read_text(encoding="utf-8").split("\n")]
    linhas = [x for x in linhas if x.strip()]
    if not linhas or tuple(linhas[0].split("\t")) != CABECALHO_MANIFESTO:
        raise ErroUso("cabecalho do manifesto deve ser exatamente %r" % "\t".join(CABECALHO_MANIFESTO))
    pares, vistas = {}, set()
    for i, linha in enumerate(linhas[1:], start=2):
        campos = linha.split("\t")
        if len(campos) != 4 or any(not c.strip() for c in campos):
            raise ErroUso("linha %d do manifesto com campos invalidos: %r" % (i, linha))
        bloco, celula, braco, execucao = (c.strip() for c in campos)
        if braco not in BRACOS:
            raise ErroUso("braco invalido na linha %d: %r (use A ou B)" % (i, braco))
        ex = Path(execucao)
        if not ex.is_absolute():
            ex = p.parent / ex
        ex_norm = os.path.normcase(str(ex.resolve()))
        if ex_norm in vistas:
            raise ErroUso("execucao repetida no manifesto (linha %d): %s" % (i, execucao))
        vistas.add(ex_norm)
        par = pares.setdefault((celula, bloco), {})
        if braco in par:
            raise ErroUso("bloco %s da celula %s com braco %s duplicado" % (bloco, celula, braco))
        par[braco] = ex
    out = {}
    for (celula, bloco), par in pares.items():
        if set(par) != set(BRACOS):
            raise ErroUso("bloco %s da celula %s incompleto (bracos: %s)" % (bloco, celula, ",".join(sorted(par))))
        out.setdefault(celula, {})[bloco] = par
    return {c: dict(sorted(bs.items(), key=lambda kv: _chave_bloco(kv[0]))) for c, bs in sorted(out.items())}


# ============================================================================= estatistica

def d_blocos(y_a, y_b):
    """Lista de ln(y_B / y_A) por bloco (y > 0)."""
    if len(y_a) != len(y_b):
        raise ValueError("y_a e y_b com tamanhos diferentes")
    out = []
    for a, b in zip(y_a, y_b):
        if not (a > 0 and b > 0):
            raise ValueError("y deve ser > 0 para o log da razao: %r, %r" % (a, b))
        out.append(math.log(b / a))
    return out


def d_diferencas(y_a, y_b):
    """Lista de y_B - y_A por bloco (zeros permitidos; decisao G3 de 28/10 para taxas com zeros)."""
    if len(y_a) != len(y_b):
        raise ValueError("y_a e y_b com tamanhos diferentes")
    out = []
    for a, b in zip(y_a, y_b):
        a, b = float(a), float(b)
        if not (math.isfinite(a) and math.isfinite(b)):
            raise ValueError("y nao finito: %r, %r" % (a, b))
        out.append(b - a)
    return out


def permutacao_exata(d):
    """p bilateral exato por troca de sinal sobre d_b, com a identidade incluida.

    p = #{arranjos com |media| >= |media observada| - 1e-12} / 2^n. n > 16: ErroUso (sem Monte Carlo).
    """
    d = np.asarray(d, dtype=float)
    n = d.size
    if n == 0:
        return None
    if n > N_MAX_PERMUTACAO:
        raise ErroUso("permutacao exata limitada a n <= %d blocos (n = %d)" % (N_MAX_PERMUTACAO, n))
    sinais = ((np.arange(2 ** n)[:, None] >> np.arange(n)) & 1) * 2 - 1
    medias = sinais @ d / n
    obs = abs(d.mean())
    return int(np.count_nonzero(np.abs(medias) >= obs - TOL_PERMUTACAO)) / 2 ** n


def k_nao_nulos(d):
    """Numero de blocos com d_b diferente de zero (plano de alinhamento 3.3)."""
    return int(sum(1 for x in d if x != 0.0))


def p_minimo_atingivel(k):
    """Menor p bilateral possivel da troca de sinal com k blocos nao nulos: 2/2^k (1 com k <= 1).

    Os blocos com d_b = 0 nao mudam nenhum arranjo (plano de alinhamento 3.3).
    """
    return min(1.0, 2.0 / 2 ** int(k))


def holm(ps):
    """p ajustados por Holm (step-down), na ordem de entrada."""
    m = len(ps)
    ordem = sorted(range(m), key=lambda i: ps[i])
    out = [0.0] * m
    corrente = 0.0
    for pos, i in enumerate(ordem):
        corrente = max(corrente, min(1.0, (m - pos) * ps[i]))
        out[i] = corrente
    return out


def bonferroni(ps):
    m = len(ps)
    return [min(1.0, m * p) for p in ps]


def hochberg(ps):
    """p ajustados por Hochberg (step-up), na ordem de entrada."""
    m = len(ps)
    ordem = sorted(range(m), key=lambda i: ps[i])
    out = [0.0] * m
    corrente = 1.0
    for pos in range(m - 1, -1, -1):
        i = ordem[pos]
        corrente = min(corrente, (m - pos) * ps[i])
        out[i] = min(1.0, corrente)
    return out


def regra_de_tres(n_acoes):
    """(limite superior unilateral 95 % = 1 - 0,05^(1/N), rotulo "≈ 3/N") para zero eventos em N."""
    if n_acoes <= 0:
        return None, "sem acoes"
    return 1.0 - 0.05 ** (1.0 / n_acoes), "≈ 3/N = %.6g (N = %d)" % (3.0 / n_acoes, n_acoes)


def ic_t(d, confianca=0.95, exp=True):
    """IC t sobre d_b (media ± t_{0,975; n-1} s/sqrt(n)); em escala de razao (exp) ou da diferenca."""
    d = np.asarray(d, dtype=float)
    n = d.size
    if n < 2:
        return None
    m, s = d.mean(), d.std(ddof=1)
    meia = stats.t.ppf(0.5 + confianca / 2, n - 1) * s / math.sqrt(n)
    if not exp:
        return [float(m - meia), float(m + meia)]
    return [math.exp(m - meia), math.exp(m + meia)]


def p_nao_inferioridade(d, limite_log):
    """p (escala bilateral) do teste t unilateral H0: media(d) >= limite_log contra media(d) < limite_log.

    p = min(1, 2 F_{t, n-1}((media - limite) / (s / sqrt n))). Com IC t 95 % bilateral, o limite superior
    fica <= limite_log exatamente quando p <= 0,05 (equivalencia conferida em teste). s = 0: 0 se a media
    <= limite_log, senao 1. n < 2: None.
    """
    d = np.asarray(d, dtype=float)
    n = d.size
    if n < 2:
        return None
    m, s = float(d.mean()), float(d.std(ddof=1))
    if s == 0.0:
        return 0.0 if m <= limite_log else 1.0
    t = (m - limite_log) / (s / math.sqrt(n))
    return min(1.0, 2.0 * float(stats.t.cdf(t, n - 1)))


def _rng(semente, *indices):
    return np.random.default_rng(np.random.SeedSequence([int(semente)] + [int(i) for i in indices]))


def ic_boot_blocos(d, semente, indice, n_boot=N_BOOT_PADRAO, exp=True):
    """IC bootstrap percentil sobre BLOCOS (reamostra os d_b), em escala de razao (exp) ou da diferenca.

    Gerador: numpy default_rng(SeedSequence([semente, indice_do_desfecho])).
    """
    d = np.asarray(d, dtype=float)
    n = d.size
    if n < 2:
        return None
    rng = _rng(semente, indice)
    medias = d[rng.integers(0, n, size=(n_boot, n))].mean(axis=1)
    lo, hi = np.percentile(medias, [2.5, 97.5])
    if not exp:
        return [float(lo), float(hi)]
    return [math.exp(lo), math.exp(hi)]


def _k_posto(n, p):
    fp = Fraction(str(p))
    return -((-fp.numerator * n) // fp.denominator)


def quantil_reamostrado(ordenados, p, rng, indices):
    """Percentil (posto mais proximo) de uma reamostra com reposicao de cada execucao indicada.

    ordenados: lista de arrays ordenados; indices: array inteiro (qualquer forma) de execucoes. Cada
    entrada recebe o p-percentil de uma reamostra de tamanho n da execucao, sorteado sem materializar a
    reamostra: o k-esimo menor de n indices uniformes ceil(n*V_i) e ceil(n*V_(k)), com V_(k) ~ Beta(k,
    n - k + 1) (a funcao ceil(n*.) e monotona), entao o valor e ordenado[ceil(n*V_(k)) - 1]. A
    equivalencia com a reamostragem ingenua e conferida em test_comparar_bracos (H0).
    """
    tam = np.array([len(o) for o in ordenados])
    ks = np.array([_k_posto(int(n), p) for n in tam])
    desloc = np.concatenate([[0], np.cumsum(tam)[:-1]])
    todos = np.concatenate(ordenados)
    n_sel, k_sel = tam[indices], ks[indices]
    v = rng.beta(k_sel, n_sel - k_sel + 1)
    pos = np.clip(np.ceil(v * n_sel).astype(np.int64), 1, n_sel)
    return todos[desloc[indices] + pos - 1]


def ic_hierarquico(pares, semente, indice, n_hier=N_HIER_PADRAO, p=0.99):
    """IC hierarquico: reamostra blocos e, dentro de cada execucao, os eventos (recalcula o p99).

    pares = [(eventos_A_ordenados, eventos_B_ordenados)] por bloco. Gerador:
    SeedSequence([semente, indice_do_desfecho, 1]) (fluxo separado do bootstrap de blocos).
    """
    n = len(pares)
    if n < 2:
        return None
    rng = _rng(semente, indice, 1)
    a = [np.asarray(x, dtype=float) for x, _ in pares]
    b = [np.asarray(y, dtype=float) for _, y in pares]
    blocos = rng.integers(0, n, size=(n_hier, n))
    qa = quantil_reamostrado(a, p, rng, blocos)
    qb = quantil_reamostrado(b, p, rng, blocos)
    medias = np.log(qb / qa).mean(axis=1)
    lo, hi = np.percentile(medias, [2.5, 97.5])
    return [math.exp(lo), math.exp(hi)]


def ic_eventos_agrupados(pares, semente, n_boot=N_BOOT_PADRAO, p=0.99):
    """(estimativa, IC) da razao p99(B)/p99(A) reamostrando eventos de todas as execucoes juntas.

    E o procedimento do bootstrap_ic.py (eventos como unidade). Ignora a variabilidade entre execucoes
    (pseudorreplicacao, ameaca A8); existe so para documentar a sub-cobertura (teste E1). Nao entra no
    JSON da comparacao.
    """
    a = np.sort(np.concatenate([np.asarray(x, dtype=float) for x, _ in pares]))
    b = np.sort(np.concatenate([np.asarray(y, dtype=float) for _, y in pares]))
    rng = _rng(semente, 2 ** 31)
    idx = np.zeros(n_boot, dtype=np.int64)
    qa = quantil_reamostrado([a], p, rng, idx)
    qb = quantil_reamostrado([b], p, rng, idx)
    lo, hi = np.percentile(qb / qa, [2.5, 97.5])
    return float(percentil(b.tolist(), p) / percentil(a.tolist(), p)), [float(lo), float(hi)]


def nao_inferioridade(analise, delta=DELTA_PADRAO):
    """"nao-inferior" se o limite INFERIOR do IC t da razao B/A >= 1 - delta; senao "inconclusivo"."""
    ic = analise.get("ic_t") if analise else None
    if not ic:
        return "inconclusivo"
    return "nao-inferior" if ic[0] >= 1.0 - delta else "inconclusivo"


def analisar_desfecho(y_a, y_b, semente, indice, n_boot=N_BOOT_PADRAO, pares=None, n_hier=N_HIER_PADRAO,
                      escala=ESCALA_LOG, permutar=True):
    """Estimativa, IC t, IC bootstrap de blocos, IC hierarquico (com pares, so no log) e p de permutacao.

    escala "log-razao": d_b = ln(y_B/y_A), estimativa e IC em razao (exp). "diferenca": d_b = y_B - y_A,
    estimativa e IC na escala de y. permutar=False (celula sem teste): p_permutacao None.
    """
    if escala not in (ESCALA_LOG, ESCALA_DIF):
        raise ValueError("escala desconhecida: %r" % (escala,))
    log = escala == ESCALA_LOG
    d = d_blocos(y_a, y_b) if log else d_diferencas(y_a, y_b)
    n = len(d)
    media = sum(d) / n if n else None
    return {"n_blocos": n,
            "estimativa": None if media is None else (math.exp(media) if log else media),
            "ic_t": ic_t(d, exp=log),
            "ic_boot_blocos": ic_boot_blocos(d, semente, indice, n_boot, exp=log),
            "ic_hierarquico": ic_hierarquico(pares, semente, indice, n_hier) if (pares and log) else None,
            "p_permutacao": permutacao_exata(d) if permutar else None,
            "d_b": d}


# ============================================================================= regra de sucesso

def regra_iu(p_v, media_v, avaliavel_v, ic_t_l, p_l, delta_latencia, vazao, rastreavel, alfa=ALFA):
    """Intersecao-uniao (Berger 1982; D-1) de uma celula de teste.

    p_v, media_v: p bilateral da troca de sinal e media de d_b (y_B - y_A) da taxa de violacoes;
    avaliavel_v: False quando o braco A nao tem violacao em nenhum bloco. ic_t_l, p_l: IC t (razao) e p de
    nao inferioridade do p99. vazao: resultado de nao_inferioridade; rastreavel: trilha do braco B integral.
    """
    limite = 1.0 + float(delta_latencia)
    hv = {"avaliavel": bool(avaliavel_v), "p": p_v, "media_d": media_v, "rejeitada": None,
          "teste": "troca de sinal exata, bilateral, sobre d_b = y_B - y_A; rejeita com p <= alfa e media < 0"}
    p_v_dir = None
    if avaliavel_v and p_v is not None and media_v is not None:
        hv["rejeitada"] = bool(p_v <= alfa and media_v < 0)
        p_v_dir = float(p_v) if media_v < 0 else 1.0
    hv["p_na_iu"] = p_v_dir
    hl = {"delta": float(delta_latencia), "limite": limite, "limite_superior_ic_t": ic_t_l[1] if ic_t_l else None,
          "p": p_l, "rejeitada": None,
          "teste": "nao inferioridade: limite superior do IC t 95 % de exp(media ln(p99_B/p99_A)) <= 1 + delta"}
    if ic_t_l:
        hl["rejeitada"] = bool(ic_t_l[1] <= limite)
    p_iu = max(p_v_dir, p_l) if (p_v_dir is not None and p_l is not None) else None
    motivos = []
    if hv["rejeitada"] is None:
        motivos.append("H_V nao avaliavel")
    elif not hv["rejeitada"]:
        motivos.append("H_V nao rejeitada")
    if hl["rejeitada"] is None:
        motivos.append("H_L nao avaliavel")
    elif not hl["rejeitada"]:
        motivos.append("H_L nao rejeitada")
    if vazao != "nao-inferior":
        motivos.append("vazao util sem nao inferioridade demonstrada")
    if not rastreavel:
        motivos.append("rastreabilidade do braco B incompleta")
    if hv["rejeitada"] is None or hl["rejeitada"] is None:
        resultado = "nao-avaliavel"
    elif not motivos:
        resultado = "melhoria"
    else:
        resultado = "sem-melhoria"
    return {"alfa": alfa, "h_v": hv, "h_l": hl, "vazao": vazao, "rastreabilidade_integral": bool(rastreavel),
            "p_iu": p_iu, "resultado": resultado, "motivos": motivos}


def conclusao_global(regras, alfa=ALFA):
    """Holm (holm_sequential_1979) sobre os p_IU das celulas de teste; nao avaliavel entra com p = 1."""
    nomes = sorted(regras)
    ps = [1.0 if regras[c]["p_iu"] is None else float(regras[c]["p_iu"]) for c in nomes]
    adj = holm(ps) if nomes else []
    cel = {}
    for c, p_usado, pa in zip(nomes, ps, adj):
        cel[c] = {"p_iu": regras[c]["p_iu"], "p_iu_usado": p_usado, "p_iu_holm": pa,
                  "resultado_celula": regras[c]["resultado"],
                  "melhoria": bool(regras[c]["resultado"] == "melhoria" and pa <= alfa)}
    return {"metodo": "Holm sobre p_IU das celulas de teste; celula nao avaliavel entra com p = 1 "
                      "(a familia pre-registrada nao encolhe com o dado)",
            "alfa": alfa, "m": len(nomes), "celulas": cel}


# ============================================================================= recuperacao

def delta_rec(anomalos_ns, t_fim_falha_ns, fim_medicao_ns, w_c_ns):
    """(Delta_rec em s, censurada, motivo) pela definicao principal do plano de alinhamento 3.4.

    anomalos_ns: instantes em que cada evento anomalo foi observado (fim da operacao). Delta_rec = ultimo
    instante > t_fim-falha menos t_fim-falha; 0 sem anomalia depois do fim. Censura: ultimo anomalo dentro
    dos ultimos W_c da medicao (t > fim_medicao - W_c) => censurada, valor = fim_medicao - t_fim-falha.
    """
    if t_fim_falha_ns is None:
        return None, None, "sem marcador end (t_fim-falha)"
    if w_c_ns is None:
        return None, None, "w_analise nao informado"
    if fim_medicao_ns <= t_fim_falha_ns:
        return None, None, "fim da medicao nao passa do fim da falha"
    depois = [t for t in anomalos_ns if t > t_fim_falha_ns]
    if not depois:
        return 0.0, False, None
    ultimo = max(depois)
    if ultimo > fim_medicao_ns - w_c_ns:
        return (fim_medicao_ns - t_fim_falha_ns) / 1e9, True, None
    return (ultimo - t_fim_falha_ns) / 1e9, False, None


def delta_rec_metodologia(eventos, t_fim_falha_ns, fim_medicao_ns, w_ns, p_ref_sup, quantil=QUANTIL_LINHA_DE_BASE):
    """(Delta_rec em s, censurada, motivo) pela definicao da Metodologia.tex:223 (sensibilidade, plano 3.4).

    eventos: [(t_ns de observacao, erro)]. Janelas [t_fim + jW, t_fim + (j+1)W) inteiras ate fim_medicao.
    A janela j esta na linha de base se tem eventos e a contagem de erros nao passa do quantil de 95 % de
    Binomial(n_j, p_ref+); Delta = j W da primeira. Nenhuma: censurada, valor = fim_medicao - t_fim.
    """
    if t_fim_falha_ns is None:
        return None, None, "sem marcador end (t_fim-falha)"
    if w_ns is None:
        return None, None, "w_analise nao informado"
    if p_ref_sup is None:
        return None, None, "sem referencia de erro"
    n_jan = (fim_medicao_ns - t_fim_falha_ns) // w_ns
    if n_jan < 1:
        return None, None, "nenhuma janela inteira entre o fim da falha e o fim da medicao"
    tot = np.zeros(n_jan, dtype=np.int64)
    err = np.zeros(n_jan, dtype=np.int64)
    for t, erro in eventos:
        if t < t_fim_falha_ns:
            continue
        j = (t - t_fim_falha_ns) // w_ns
        if j < n_jan:
            tot[j] += 1
            err[j] += 1 if erro else 0
    for j in range(n_jan):
        if tot[j] > 0 and err[j] <= stats.binom.ppf(quantil, int(tot[j]), p_ref_sup):
            return j * w_ns / 1e9, False, None
    return (fim_medicao_ns - t_fim_falha_ns) / 1e9, True, None


def ler_resumo_convergencia(caminho):
    """Dicionario do convergencia-resumo-rep-NNN.json (ConvergenciaLoop.escreverResumo) ou None."""
    p = Path(caminho)
    if not p.exists():
        return None
    try:
        j = json.loads(p.read_text(encoding="utf-8"))
    except ValueError:
        raise ErroUso("resumo de convergencia ilegivel: %s" % p) from None
    if not isinstance(j, dict):
        raise ErroUso("resumo de convergencia nao e objeto: %s" % p)
    return j


def _int_ou_none(x):
    return x if isinstance(x, int) and not isinstance(x, bool) else None


# ============================================================================= trilha do controlador

def ler_trilha_decisoes(caminho):
    """Linhas do decisoes-rep-NNN.jsonl (contrato v1.x) ou None se ausente.

    Ultima linha truncada (queda do processo; contrato, regra 1) e descartada e marca a trilha como
    incompleta; linha ilegivel no meio: ErroUso. Primeira linha precisa ser meta com esquema
    decisoes-controlador/*; sem linha fim no final: incompleta (regra 2).
    """
    p = Path(caminho)
    if not p.exists():
        return None
    partes = p.read_bytes().decode("utf-8", errors="replace").split("\n")
    sem_quebra_final = partes[-1] != ""
    if not sem_quebra_final:
        partes = partes[:-1]
    linhas, problemas, completo = [], [], True
    for i, bruta in enumerate(partes):
        if not bruta.strip():
            continue
        try:
            o = json.loads(bruta)
        except ValueError:
            if i == len(partes) - 1 and sem_quebra_final:
                completo = False
                problemas.append("ultima linha truncada (execucao interrompida)")
                break
            raise ErroUso("linha %d ilegivel em %s" % (i + 1, p)) from None
        if not isinstance(o, dict) or not isinstance(o.get("tipo"), str):
            raise ErroUso("linha %d sem campo tipo em %s" % (i + 1, p))
        linhas.append(o)
    if not linhas or linhas[0]["tipo"] != "meta":
        raise ErroUso("trilha sem linha meta no inicio: %s" % p)
    esquema = linhas[0].get("esquema")
    if not isinstance(esquema, str) or not esquema.startswith("decisoes-controlador/"):
        raise ErroUso("esquema inesperado em %s: %r" % (p, esquema))
    fim = linhas[-1] if linhas[-1]["tipo"] == "fim" else None
    if fim is None:
        completo = False
        problemas.append("sem linha fim (execucao interrompida)")
    return {"arquivo": _rel(p), "linhas": linhas, "meta": linhas[0], "fim": fim, "completo": completo,
            "problemas": problemas}


def _instante_efetivo(a):
    """Instante em que uma linha acao vale: fim da chamada (t_inicio + duracao) ou o pedido, sem chamada."""
    ini, dur = _int_ou_none(a.get("t_inicio_ns")), _int_ou_none(a.get("duracao_ns"))
    if ini is not None and dur is not None:
        return ini + dur
    return _int_ou_none(a.get("t_pedido_ns"))


def descrever_controlador(trilha, inicio_falha_ns):
    """Desfechos descritivos do controlador numa execucao (sem teste; W-5 item 1, plano 3.1).

    Transicoes, entradas em CONSERVADOR, tempo em CONSERVADOR (janelas x W), atraso da primeira entrada e
    da primeira acao de politica em relacao ao marcador start, acoes por tipo e resultado (executadas:
    APLICADA; previstas: PREVISTA), tempo ativo por tipo (do instante efetivo da aplicacao ate a reversao
    com sucesso; sem reversao, ate o ultimo instante da trilha, com ativa_no_fim) e reversoes com sucesso
    ou falha. Sem marcador start, toda entrada e toda acao conta como "antes da falha" (acionamento sem
    falha em curso).
    """
    linhas = trilha["linhas"]
    meta = trilha["meta"]
    w_ns = _int_ou_none((meta.get("parametros") or {}).get("w_ns"))
    decs = [o for o in linhas if o["tipo"] == "decisao"]
    acoes = [o for o in linhas if o["tipo"] == "acao"]
    pols = [o for o in linhas if o["tipo"] == "politica"]

    def antes(t):
        return inicio_falha_ns is None or (t is not None and t < inicio_falha_ns)

    instantes = []
    for o in decs + pols:
        instantes += [x for x in (_int_ou_none(o.get("t_fim_ns")), _int_ou_none(o.get("t_decisao_ns"))) if x is not None]
    for a in acoes:
        instantes += [x for x in (_int_ou_none(a.get("t_pedido_ns")), _instante_efetivo(a)) if x is not None]
    t_ultimo = max(instantes) if instantes else None

    entradas = [o for o in decs if o.get("transicao") is True and o.get("perfil") == "CONSERVADOR"]
    n_cons = sum(1 for o in decs if o.get("perfil") == "CONSERVADOR")
    atraso_entrada = None
    if inicio_falha_ns is not None:
        pos = [t for t in (_int_ou_none(o.get("t_fim_ns")) for o in entradas) if t is not None and t >= inicio_falha_ns]
        if pos:
            atraso_entrada = (min(pos) - inicio_falha_ns) / 1e9

    por_tipo, ativas = {}, {}
    rev = {"sucesso": 0, "falha": 0}
    primeira, n_antes = None, 0
    for a in acoes:
        ac = a.get("acao") if isinstance(a.get("acao"), dict) else {}
        tipo = str(ac.get("tipo"))
        chave = (tipo, tuple(ac.get("caches") or ()), ac.get("alvo"))
        op, res = a.get("operacao"), str(a.get("resultado"))
        pt = por_tipo.setdefault(tipo, {"aplicar": {}, "reverter": {}, "tempo_ativo_s": 0.0, "ativa_no_fim": False})
        lado = "aplicar" if op == "APLICAR" else "reverter"
        pt[lado][res] = pt[lado].get(res, 0) + 1
        t_ef = _instante_efetivo(a)
        if op == "APLICAR":
            tp = _int_ou_none(a.get("t_pedido_ns"))
            if a.get("origem") == "POLITICA" and tp is not None:
                if antes(tp):
                    n_antes += 1
                elif primeira is None or tp < primeira:
                    primeira = tp
            if res in RESULTADOS_ATIVA and chave not in ativas and t_ef is not None:
                ativas[chave] = t_ef
        elif op == "REVERTER":
            if res == "FALHOU":
                rev["falha"] += 1
            else:
                rev["sucesso"] += 1
                if chave in ativas and t_ef is not None:
                    pt["tempo_ativo_s"] += max(0, t_ef - ativas.pop(chave)) / 1e9
    for chave, t_ini in ativas.items():
        pt = por_tipo[chave[0]]
        pt["ativa_no_fim"] = True
        if t_ultimo is not None:
            pt["tempo_ativo_s"] += max(0, t_ultimo - t_ini) / 1e9
    politicas = {}
    for o in pols:
        k = str(o.get("politica"))
        politicas[k] = politicas.get(k, 0) + 1
    return {"modo": meta.get("modo"), "esquema": meta.get("esquema"), "completo": trilha["completo"],
            "decisoes": len(decs), "transicoes": sum(1 for o in decs if o.get("transicao") is True),
            "entradas_conservador": len(entradas),
            "entradas_antes_da_falha": sum(1 for o in entradas if antes(_int_ou_none(o.get("t_fim_ns")))),
            "janelas_conservador": n_cons,
            "tempo_conservador_s": None if not w_ns or w_ns <= 0 else n_cons * w_ns / 1e9,
            "atraso_entrada_conservador_s": atraso_entrada,
            "acoes_por_tipo": por_tipo, "reversoes": rev,
            "acoes_politica_antes_da_falha": n_antes,
            "atraso_primeira_acao_s": None if primeira is None else (primeira - inicio_falha_ns) / 1e9,
            "politicas": politicas, "t_ultimo_ns": t_ultimo}


def conferir_rastreabilidade(trilha):
    """{"integral", "problemas", "n_problemas"}: cada acao com regra e decisao (com sinais) correspondente.

    Problemas: trilha incompleta (sem fim ou truncada); decisao sem regra ou sem sinais; acao de origem
    POLITICA, R4 ou FALHA_NA_APLICACAO sem regra ou com t_fim_janela_ns que nao e o t_fim_ns de nenhuma
    decisao; linhas_perdidas diferente de 0; acoes_pedidas do fim diferente do numero de linhas APLICAR
    (conservacao do contrato v1.1).
    """
    problemas = list(trilha["problemas"])
    linhas = trilha["linhas"]
    decs = [o for o in linhas if o["tipo"] == "decisao"]
    fins = {o.get("t_fim_ns") for o in decs}
    fins.discard(None)
    for o in decs:
        if not o.get("regra") or not isinstance(o.get("sinais"), dict) or not o["sinais"]:
            problemas.append("decisao sem regra ou sinais (t_fim_ns = %r)" % (o.get("t_fim_ns"),))
    n_aplicar = 0
    for a in linhas:
        if a["tipo"] != "acao":
            continue
        if a.get("operacao") == "APLICAR":
            n_aplicar += 1
        if a.get("origem") in ORIGENS_COM_DECISAO and (a.get("regra") is None or a.get("t_fim_janela_ns") not in fins):
            tipo = a.get("acao", {}).get("tipo") if isinstance(a.get("acao"), dict) else None
            problemas.append("acao %s %s (%s) sem decisao correspondente (t_fim_janela_ns = %r)"
                             % (a.get("operacao"), tipo, a.get("origem"), a.get("t_fim_janela_ns")))
    fim = trilha["fim"]
    if fim is not None:
        if fim.get("linhas_perdidas") != 0:
            problemas.append("linhas_perdidas = %r" % (fim.get("linhas_perdidas"),))
        if fim.get("acoes_pedidas") != n_aplicar:
            problemas.append("acoes_pedidas = %r, linhas APLICAR = %d" % (fim.get("acoes_pedidas"), n_aplicar))
    return {"integral": not problemas, "problemas": problemas[:20], "n_problemas": len(problemas)}


# ============================================================================= leitura de uma execucao

_RX_INSTANTE = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$")


def instante_ns(txt):
    """Epoch ns de um java.time.Instant.toString() (UTC, fracao de 0 a 9 digitos)."""
    m = _RX_INSTANTE.match(txt.strip())
    if not m:
        raise ErroUso("instante invalido no CSV de violacoes: %r" % txt)
    seg = calendar.timegm(time.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S"))
    return seg * 1_000_000_000 + int((m.group(2) or "").ljust(9, "0"))


def contar_violacoes(caminho, filtro=None):
    """{"I1","I2_REAL","I4"} do violacoes-rep-NNN.csv, com a regra da DT-009 (rebaseline.py:256-276).

    Formato: InvariantAuditor.dumpCsv (:251-278), cabecalho instante,invariante,chave,mensagem[,fase]
    [,classificacao]. I1 e I4 com rotulo exato; I2 so com rotulo exato "I2" e classificacao "REAL".
    I2_JANELA_CASCATA, I2_CASCATA_INCOMPLETA, I2 de outra classe e os contadores *_concorrentes (que so
    existem no stdout, SessionOps.java:246-260) nao contam (DT-001/DT-002). filtro(t_ns) -> bool
    restringe pelas fases.
    """
    out = {k: 0 for k in INVARIANTES_VIOL}
    p = Path(caminho)
    if not p.exists():
        return None
    with open(p, newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            inv = (x.get("invariante") or "").strip()
            cl = (x.get("classificacao") or "").strip()
            if inv == "I2":
                chave = "I2_REAL" if cl == "REAL" else None
            elif inv in ("I1", "I4"):
                chave = inv
            else:
                chave = None
            if chave is None:
                continue
            if filtro is not None and not filtro(instante_ns(x.get("instante") or "")):
                continue
            out[chave] += 1
    return out


def _p99_desfecho(valores):
    n = len(valores)
    if n < N_MIN_P99:
        return {"y": None, "n": n, "motivo": "n<%d" % N_MIN_P99, "eventos": None}
    arr = np.sort(np.asarray(valores, dtype=float))
    return {"y": float(percentil(valores, 0.99)), "n": n, "motivo": None, "eventos": arr}


def _taxa(num, den, escala=1.0):
    if den <= 0:
        return {"y": None, "n": den, "motivo": "denominador zero", "eventos": None}
    return {"y": escala * num / den, "n": den, "motivo": None, "eventos": None}


def _ausente(motivo, n=0):
    return {"y": None, "n": n, "motivo": motivo, "eventos": None}


def conferir_parametros_energia(en, en_path, w_recovery_ns, tem_marcadores, args_fase):
    """B39-R1 (G4 de 28/10): ErroUso se a energia foi calculada com outras fronteiras de fase.

    Confere (1) parametros.w_recovery_s igual ao W da comparacao; (2) marcadores presentes nos dois
    lados ou ausentes nos dois; (3) as fases gravadas no JSON iguais as que a comparacao calcula sobre a
    janela do proprio JSON (pega marcadores de outro arquivo e convergencia nao passada ao energia_proxy).
    """
    par, jan, fs = en.get("parametros"), en.get("janela"), en.get("fases")
    if not isinstance(par, dict) or not isinstance(jan, dict) or not isinstance(fs, list):
        raise ErroUso("energia sem parametros, janela ou fases (energia-proxy/1 incompleto): %s" % en_path)
    w_en = par.get("w_recovery_s")
    try:
        w_en_ns = int(round(float(w_en) * 1e9))
    except (TypeError, ValueError):
        raise ErroUso("w_recovery_s invalido na energia %s: %r" % (en_path, w_en)) from None
    if w_en_ns != w_recovery_ns:
        raise ErroUso("B39-R1: w_recovery_s da energia (%r s) difere do da comparacao (%r s): %s"
                      % (w_en, w_recovery_ns / 1e9, en_path))
    if (par.get("marcadores") is not None) != tem_marcadores:
        raise ErroUso("B39-R1: marcadores divergem (energia: %s; comparacao: %s): %s"
                      % ("com" if par.get("marcadores") is not None else "sem",
                         "com" if tem_marcadores else "sem", en_path))
    try:
        esperado = [tuple(x) for x in _fases.intervalos_de_fase(int(jan["t0_ns"]), int(jan["t1_ns"]), *args_fase)]
        obtido = [(f["rotulo"], int(f["t_ini_ns"]), int(f["t_fim_ns"])) for f in fs]
    except (KeyError, TypeError, ValueError):
        raise ErroUso("janela ou fases ilegiveis na energia %s" % en_path) from None
    if esperado != obtido:
        raise ErroUso("B39-R1: fases da energia divergem das da comparacao (W, marcadores ou convergencia): %s"
                      % en_path)


def _desfechos_energia(en, en_path, rot, n_uteis, energia):
    """{energia_total_j_por_1000_uteis:<r>: desfecho} das razoes do JSON (vm, fases selecionadas).

    energia = {"p_max_w", "razao_central"}: o P_max do JSON tem de ser o da comparacao e a razao central
    tem de existir no JSON (ErroUso). Energia nao calculada (energia_proxy sem --p-max-w): desfecho da
    razao central ausente, com motivo.
    """
    out = {}
    chave_c = repr(float(energia["razao_central"]))
    e = en.get("energia") if isinstance(en.get("energia"), dict) else {}
    if not e.get("calculada"):
        out[PREFIXO_ENERGIA + chave_c] = _ausente("energia nao calculada no energia_proxy (sem --p-max-w)")
        return out
    try:
        p_json = float(e.get("p_max_w"))
    except (TypeError, ValueError):
        raise ErroUso("p_max_w invalido em %s: %r" % (en_path, e.get("p_max_w"))) from None
    if p_json != float(energia["p_max_w"]):
        raise ErroUso("P_max da energia (%r W) difere do da comparacao (%r W): %s"
                      % (e.get("p_max_w"), energia["p_max_w"], en_path))
    por = e.get("por_razao") if isinstance(e.get("por_razao"), dict) else {}
    if chave_c not in por:
        raise ErroUso("razao de ociosidade central %s ausente em %s (razoes: %s)"
                      % (chave_c, en_path, ",".join(sorted(por))))
    for chave in sorted(por):
        vm = (por[chave] or {}).get("vm", {})
        vals = [(vm.get(r) or {}).get("e_total_j") for r in rot]
        nome = PREFIXO_ENERGIA + chave
        if not rot:
            out[nome] = _ausente("fases ausentes na energia")
        elif any(v is None for v in vals):
            out[nome] = _ausente("energia sem valor (lacuna/fora)", n_uteis)
        else:
            out[nome] = _taxa(sum(vals), n_uteis, 1000.0)
    return out


def desfechos_execucao(execucao, rep, fases_sel, w_recovery_ns, marcadores_nome="marcadores.txt", *,
                       w_analise_ns=None, referencia_erro=None, energia=None):
    """({desfecho: {"y","n","motivo","eventos"}}, info) de uma execucao, nas fases pedidas.

    w_analise_ns: W_c e janela do Delta_rec de sensibilidade (None: Delta_rec ausente, com motivo; a CLI
    exige o valor). referencia_erro: dicionario de referencia_erro.ler (None: Delta_rec de sensibilidade
    ausente). energia: {"p_max_w", "razao_central"} (None: energia estimada nao entra).
    """
    d = Path(execucao)
    if not d.is_dir():
        raise ErroUso("execucao ausente: %s" % d)
    nnn = "%03d" % int(rep)
    try:
        mk = _fases.ler_marcadores(d / marcadores_nome)
    except ValueError as e:
        raise ErroUso("%s: %s" % (d / marcadores_nome, e)) from None
    resumo = ler_resumo_convergencia(d / ("convergencia-resumo-rep-%s.json" % nnn))
    # mesma leitura de fases.ler_convergencia (t_convergencia_abs_ns e divergencia_persistente)
    t_c = None if resumo is None else resumo.get("t_convergencia_abs_ns")
    try:
        conv = None if t_c is None else int(t_c)
    except (TypeError, ValueError):
        raise ErroUso("t_convergencia_abs_ns invalido no resumo de convergencia de %s: %r" % (d, t_c)) from None
    div = bool(resumo.get("divergencia_persistente", False)) if resumo is not None else False
    ev_path, s3_path = d / ("rep-%s.csv" % nnn), d / ("s3-acoes-rep-%s.csv" % nnn)
    eventos = energia_proxy.ler_eventos_inicio(ev_path) if ev_path.exists() else []
    acoes = energia_proxy.ler_acoes_s3(s3_path) if s3_path.exists() else None
    inicios = [e[0] for e in eventos] + [a[0] for a in (acoes or [])]
    fins = [e[1] for e in eventos] + [a[1] for a in (acoes or [])]
    if not inicios:
        raise ErroUso("execucao sem eventos nem acoes S3: %s" % d)
    t0, t1 = min(inicios), max(fins) + 1
    args = (mk["inicio"], mk["split"], mk["fim"], w_recovery_ns, conv, div)
    sel = _fases.expandir_fases(fases_sel)
    ivs = _fases.intervalos_de_fase(t0, t1, *args)
    dur_sel = sum(b - a for r, a, b in ivs if r in sel) / 1e9

    def dentro(t):
        return _fases.fase_de(t, *args) in sel

    out = {}
    ev_sel = [e for e in eventos if dentro(e[0])]
    if eventos:
        por_op = {}
        for a, b, op, _ in ev_sel:
            por_op.setdefault(op, []).append(b - a)
        for op in sorted(por_op):
            out["evento_p99:" + op] = _p99_desfecho(por_op[op])
        out["taxa_erro"] = _taxa(sum(1 for e in ev_sel if e[3] in ERROS), len(ev_sel))
    n_acoes = None
    if acoes is not None:
        ac_sel = [a for a in acoes if dentro(a[0])]
        n_acoes = len(ac_sel)
        out[DESFECHO_L] = _p99_desfecho([tf - t for t, tf, _, _, _ in ac_sel])
        out["acao_s3_p99_sem_espera"] = _p99_desfecho([tf - t - w for t, tf, w, _, _ in ac_sel])
        out["falhas_percebidas_por_1000"] = _taxa(sum(1 for a in ac_sel if a[4]), n_acoes, 1000.0)
        uteis = sum(1 for a in ac_sel if not a[4])
        out[DESFECHO_NI] = ({"y": uteis / dur_sel, "n": n_acoes, "motivo": None, "eventos": None}
                            if dur_sel > 0 else {"y": None, "n": 0, "motivo": "fase sem duracao", "eventos": None})
    excluida = None
    en_path = d / ("energia-rep-%s.json" % nnn)
    if en_path.exists():
        try:
            en = json.loads(en_path.read_text(encoding="utf-8"))
        except ValueError:
            raise ErroUso("energia ilegivel: %s" % en_path) from None
        if en.get("esquema") != energia_proxy.ESQUEMA:
            raise ErroUso("esquema de energia inesperado em %s: %r" % (en_path, en.get("esquema")))
        conferir_parametros_energia(en, en_path, w_recovery_ns, (d / marcadores_nome).exists(), args)
        excluida = en.get("steal", {}).get("excluida")
        rot = [f["rotulo"] for f in en.get("fases", []) if f["rotulo"] in sel]
        vm = en.get("agregados", {}).get("vm", {})
        cpus = [vm.get(r, {}).get("cpu_s") for r in rot]
        n_uteis = sum(en.get("uteis", {}).get(r, 0) for r in rot)
        if not rot:
            out[DESFECHO_CPU] = {"y": None, "n": 0, "motivo": "fases ausentes na energia", "eventos": None}
        elif any(c is None for c in cpus):
            out[DESFECHO_CPU] = {"y": None, "n": n_uteis, "motivo": "cpu sem valor (lacuna/fora)", "eventos": None}
        else:
            out[DESFECHO_CPU] = _taxa(sum(cpus), n_uteis, 1000.0)
        if energia is not None:
            out.update(_desfechos_energia(en, en_path, rot, n_uteis, energia))
    viol = contar_violacoes(d / ("violacoes-rep-%s.csv" % nnn), dentro)
    if acoes is not None:
        if viol is None:
            out[DESFECHO_V] = dict(_ausente("sem violacoes-rep (contagem desconhecida)", n_acoes), contagem=None)
        else:
            c = sum(viol[k] for k in INVARIANTES_VIOL)
            out[DESFECHO_V] = dict(_taxa(c, n_acoes, 1000.0), contagem=c)

    # recuperacao (plano 3.4): anomalo = ERROR_TRANSPORT/ERROR_UNAVAILABLE ou acao com falha percebida,
    # no instante em que foi observado (end_ns do evento, t_fim_ns da acao)
    t_carga = _int_ou_none(resumo.get("t_fim_carga_ns")) if resumo is not None else None
    fim_med, origem_fim = (t_carga, "t_fim_carga_ns") if t_carga is not None else (t1 - 1, "ultimo-instante-observado")
    anomalos = [e[1] for e in eventos if e[3] in ERROS] + [a[1] for a in (acoes or []) if a[4]]
    y, cens, motivo = delta_rec(anomalos, mk["fim"], fim_med, w_analise_ns)
    out[DESFECHO_DREC] = dict(_ausente(motivo) if y is None else {"y": y, "n": 1, "motivo": None, "eventos": None},
                              censurada=cens)
    if referencia_erro is not None:
        if not eventos:
            out[DESFECHO_DREC_MET] = dict(_ausente("sem CSV de eventos"), censurada=None)
        else:
            y, cens, motivo = delta_rec_metodologia([(e[1], e[3] in ERROS) for e in eventos], mk["fim"], fim_med,
                                                    w_analise_ns, referencia_erro["p_ref_sup"])
            out[DESFECHO_DREC_MET] = dict(_ausente(motivo) if y is None else
                                          {"y": y, "n": 1, "motivo": None, "eventos": None}, censurada=cens)

    # convergencia (plano 3.4): Delta_conv a partir do fim da carga; nunca t_fim_ns
    if resumo is None:
        out[DESFECHO_DCONV] = _ausente("sem resumo de convergencia")
        out[DESFECHO_DIV] = _ausente("sem resumo de convergencia")
    else:
        dv = resumo.get("divergencia_persistente")
        out[DESFECHO_DIV] = (_ausente("sem divergencia_persistente no resumo") if not isinstance(dv, bool)
                             else {"y": 1.0 if dv else 0.0, "n": 1, "motivo": None, "eventos": None})
        t_conv = _int_ou_none(resumo.get("t_convergencia_abs_ns"))
        if dv is True:
            out[DESFECHO_DCONV] = _ausente("divergencia persistente (entra no desfecho binario)")
        elif t_carga is None:
            out[DESFECHO_DCONV] = _ausente("sem t_fim_carga_ns no resumo (t_fim_ns nao e usado)")
        elif t_conv is None:
            out[DESFECHO_DCONV] = _ausente("sem t_convergencia_abs_ns no resumo")
        elif t_conv < t_carga:
            out[DESFECHO_DCONV] = _ausente("t_convergencia_abs_ns antes de t_fim_carga_ns")
        else:
            out[DESFECHO_DCONV] = {"y": (t_conv - t_carga) / 1e9, "n": 1, "motivo": None, "eventos": None}

    trilha = ler_trilha_decisoes(d / ("decisoes-rep-%s.jsonl" % nnn))
    info = {"n_eventos": len(ev_sel), "n_acoes": n_acoes, "steal_excluida": excluida,
            "energia_presente": en_path.exists(), "violacoes": viol,
            "n_violacoes_base": n_acoes if n_acoes is not None else len(ev_sel),
            "fim_medicao_ns": fim_med, "fim_medicao_origem": origem_fim,
            "t_inicio_falha_ns": mk["inicio"], "t_fim_falha_ns": mk["fim"],
            "controlador": None if trilha is None else descrever_controlador(trilha, mk["inicio"]),
            "rastreabilidade": None if trilha is None else conferir_rastreabilidade(trilha)}
    return out, info


# ============================================================================= comparacao

def _f(x):
    if x is None:
        return None
    if isinstance(x, (list, tuple)):
        return [_f(v) for v in x]
    if isinstance(x, dict):
        return {k: _f(v) for k, v in x.items()}
    if isinstance(x, np.bool_):
        return bool(x)
    if isinstance(x, (np.floating, float)):
        return float(x)
    if isinstance(x, (np.integer,)):
        return int(x)
    return x


def familia_de(nome, razao_central=None):
    """Familia do desfecho no /2 (plano de alinhamento 3.1; W-5)."""
    if nome == DESFECHO_V:
        return FAMILIA_V
    if nome == DESFECHO_L:
        return FAMILIA_L
    if nome == DESFECHO_NI:
        return FAMILIA_NI
    if nome == DESFECHO_DREC_MET:
        return FAMILIA_SENS
    if nome.startswith(PREFIXO_ENERGIA):
        central = razao_central is not None and nome == PREFIXO_ENERGIA + repr(float(razao_central))
        return FAMILIA_SEC if central else FAMILIA_SENS
    return FAMILIA_SEC


def _violacoes_celula(blocos, infos, semente, indice_base, n_boot):
    out = {}
    tot = {br: {k: 0 for k in INVARIANTES_VIOL} for br in BRACOS}
    base = {br: 0 for br in BRACOS}
    por_bloco = []
    for b in blocos:
        ia, ib = infos[b]["A"], infos[b]["B"]
        if ia["violacoes"] is None or ib["violacoes"] is None:
            continue
        por_bloco.append((ia, ib))
        for br, inf in (("A", ia), ("B", ib)):
            base[br] += inf["n_violacoes_base"]
            for k in INVARIANTES_VIOL:
                tot[br][k] += inf["violacoes"][k]
    if not por_bloco:
        return {"presente": False}
    for j, k in enumerate(INVARIANTES_VIOL):
        r = {"contagem": {br: tot[br][k] for br in BRACOS}, "n_base": dict(base)}
        if tot["A"][k] == 0 or tot["B"][k] == 0:
            r["regra_de_tres"] = {}
            for br in BRACOS:
                if tot[br][k] == 0:
                    lim, rot = regra_de_tres(base[br])
                    r["regra_de_tres"][br] = {"limite_superior_95": lim, "rotulo": rot}
        else:
            ca = np.array([ia["violacoes"][k] for ia, _ in por_bloco], dtype=float)
            cb = np.array([ib["violacoes"][k] for _, ib in por_bloco], dtype=float)
            na = np.array([ia["n_violacoes_base"] for ia, _ in por_bloco], dtype=float)
            nb = np.array([ib["n_violacoes_base"] for _, ib in por_bloco], dtype=float)
            est = (cb.sum() / nb.sum()) / (ca.sum() / na.sum())
            rng = _rng(semente, indice_base + j)
            idx = rng.integers(0, len(por_bloco), size=(n_boot, len(por_bloco)))
            sa, sb = ca[idx].sum(axis=1), cb[idx].sum(axis=1)
            ok = (sa > 0) & (nb[idx].sum(axis=1) > 0)
            razoes = (sb[ok] / nb[idx].sum(axis=1)[ok]) / (sa[ok] / na[idx].sum(axis=1)[ok])
            ic = np.percentile(razoes, [2.5, 97.5]).tolist() if ok.any() else None
            r["razao_de_taxas"] = {"estimativa": est, "ic_boot_blocos": ic,
                                   "reamostras_indefinidas": int((~ok).sum())}
        out[k] = r
    out["presente"] = True
    out["blocos_com_arquivo"] = len(por_bloco)
    return out


def _wilson_por_1000(c, n):
    """IC 95 % de Wilson (brown_cai_dasgupta_binomial_2001) da taxa c/n, x 1000; None se c > n ou n = 0."""
    if n <= 0 or c > n:
        return None
    ci = stats.binomtest(int(c), int(n)).proportion_ci(confidence_level=CONFIANCA, method="wilson")
    return [1000.0 * float(ci.low), 1000.0 * float(ci.high)]


def _co_principal_v(ent, res, usados, semente, indice, n_boot, teste):
    """Bloco co_principal_v da celula: avaliabilidade, regra de tres, k, p minimo e sensibilidades.

    (a) razao de taxas agregada sobre os blocos com violacao no braco A (G3 de 28/10), IC bootstrap de
    blocos; (b) teste exato condicional de duas taxas de Poisson sobre as contagens somadas (anticonservador:
    eventos da mesma execucao nao sao independentes); (c) e o IC bootstrap de blocos da diferenca (entrada
    do desfecho). Intervalo de Wilson por braco, descritivo.
    """
    out = {"desfecho": DESFECHO_V, "n_blocos": len(usados)}
    if not usados:
        out.update({"avaliavel": False, "motivo": "nenhum bloco com a taxa nos dois bracos"})
        return out
    c = {br: np.array([res[b][br][DESFECHO_V]["contagem"] for b in usados], dtype=float) for br in BRACOS}
    n = {br: np.array([res[b][br][DESFECHO_V]["n"] for b in usados], dtype=float) for br in BRACOS}
    tot_c = {br: int(c[br].sum()) for br in BRACOS}
    tot_n = {br: int(n[br].sum()) for br in BRACOS}
    out["contagem"] = tot_c
    out["n_acoes"] = tot_n
    out["k_nao_nulos"] = ent["k_nao_nulos"]
    out["p_minimo_atingivel"] = ent["p_minimo_atingivel"]
    out["binomial_wilson_por_1000"] = {br: _wilson_por_1000(tot_c[br], tot_n[br]) for br in BRACOS}
    out["nota_independencia"] = "uma fusao produz varias violacoes de uma vez; a unidade e a execucao"
    if tot_c["A"] == 0:
        out["avaliavel"] = False
        out["motivo"] = "braco A sem violacao em nenhum bloco: H_V nao avaliavel (nao e 'sem efeito')"
    else:
        out["avaliavel"] = True
        out["motivo"] = None
    out["regra_de_tres"] = {}
    for br in BRACOS:
        if tot_c[br] == 0:
            lim, rot = regra_de_tres(tot_n[br])
            out["regra_de_tres"][br] = {"limite_superior_95": lim, "rotulo": rot,
                                        "texto": "nao observado em %d acoes" % tot_n[br]}
    if not teste:
        return out
    sens = {}
    ok_a = c["A"] > 0
    if ok_a.any():
        ca, cb_, na, nb = c["A"][ok_a], c["B"][ok_a], n["A"][ok_a], n["B"][ok_a]
        est = (cb_.sum() / nb.sum()) / (ca.sum() / na.sum())
        m = int(ok_a.sum())
        rng = _rng(semente, indice)
        idx = rng.integers(0, m, size=(n_boot, m))
        razoes = (cb_[idx].sum(axis=1) / nb[idx].sum(axis=1)) / (ca[idx].sum(axis=1) / na[idx].sum(axis=1))
        sens["a_razao_exclusao_zero_a"] = {"blocos": m, "estimativa": float(est),
                                           "ic_boot_blocos": [float(x) for x in np.percentile(razoes, [2.5, 97.5])]}
    else:
        sens["a_razao_exclusao_zero_a"] = None
    soma = tot_c["A"] + tot_c["B"]
    if soma > 0:
        p0 = tot_n["B"] / (tot_n["A"] + tot_n["B"])
        sens["b_poisson_condicional"] = {
            "p": float(stats.binomtest(tot_c["B"], soma, p0).pvalue),
            "razao_de_taxas": None if tot_c["A"] == 0 else (tot_c["B"] / tot_n["B"]) / (tot_c["A"] / tot_n["A"]),
            "rotulo": "anticonservador (eventos da mesma execucao nao sao independentes)"}
    else:
        sens["b_poisson_condicional"] = None
    sens["c_boot_blocos_diferenca"] = ent["ic_boot_blocos"]
    out["sensibilidades"] = sens
    return out


def _resumo_controlador(blocos, infos):
    """Descritivos do controlador por bloco e somas por braco (sem teste)."""
    por_bloco = {b: {br: infos[b][br]["controlador"] for br in BRACOS} for b in blocos}
    totais = {}
    for br in BRACOS:
        ds = [por_bloco[b][br] for b in blocos if por_bloco[b][br] is not None]
        if not ds:
            totais[br] = None
            continue
        t = {"execucoes_com_trilha": len(ds),
             "transicoes": sum(x["transicoes"] for x in ds),
             "entradas_conservador": sum(x["entradas_conservador"] for x in ds),
             "entradas_antes_da_falha": sum(x["entradas_antes_da_falha"] for x in ds),
             "acoes_politica_antes_da_falha": sum(x["acoes_politica_antes_da_falha"] for x in ds),
             "tempo_conservador_s": (None if any(x["tempo_conservador_s"] is None for x in ds)
                                     else sum(x["tempo_conservador_s"] for x in ds)),
             "reversoes": {k: sum(x["reversoes"][k] for x in ds) for k in ("sucesso", "falha")},
             "atrasos_entrada_s": [x["atraso_entrada_conservador_s"] for x in ds],
             "atrasos_primeira_acao_s": [x["atraso_primeira_acao_s"] for x in ds]}
        por_tipo = {}
        for x in ds:
            for tipo, v in x["acoes_por_tipo"].items():
                pt = por_tipo.setdefault(tipo, {"aplicar": {}, "reverter": {}, "tempo_ativo_s": 0.0,
                                                "execucoes_ativa_no_fim": 0})
                for lado in ("aplicar", "reverter"):
                    for res, k in v[lado].items():
                        pt[lado][res] = pt[lado].get(res, 0) + k
                pt["tempo_ativo_s"] += v["tempo_ativo_s"]
                pt["execucoes_ativa_no_fim"] += 1 if v["ativa_no_fim"] else 0
        t["acoes_por_tipo"] = por_tipo
        for nome in ("atrasos_entrada_s", "atrasos_primeira_acao_s"):
            vals = [v for v in t[nome] if v is not None]
            t[nome.replace("atrasos_", "mediana_atraso_")] = float(np.median(vals)) if vals else None
        totais[br] = t
    return {"por_bloco": por_bloco, "totais": totais}


def _rastreabilidade_celula(blocos, infos):
    out = {}
    for br in BRACOS:
        sem = [b for b in blocos if infos[b][br]["rastreabilidade"] is None]
        probs = {b: infos[b][br]["rastreabilidade"]["problemas"] for b in blocos
                 if infos[b][br]["rastreabilidade"] is not None and not infos[b][br]["rastreabilidade"]["integral"]}
        out[br] = {"integral": not sem and not probs, "execucoes_sem_trilha": sem, "problemas": probs}
    return out


def comparar(manifesto, semente, *, delta_latencia, w_analise_s, tipos_celula, fases=FASES_PADRAO, rep=1,
             w_recovery_s=60, n_boot=N_BOOT_PADRAO, n_hier=N_HIER_PADRAO, delta=DELTA_PADRAO,
             marcadores_nome="marcadores.txt", referencias_erro=None, energia=None):
    """Dicionario do esquema comparar-bracos/2.

    delta_latencia e w_analise_s sao obrigatorios e sem padrao (pre-registro). tipos_celula: {celula:
    "teste"|"descritiva"|"referencia"} cobrindo exatamente as celulas do manifesto. referencias_erro:
    {celula: caminho de referencia-erro/1}. energia: {"p_max_w", "razao_central"} ou None.
    """
    man = ler_manifesto(manifesto)
    fases_sel = list(fases)
    try:
        _fases.expandir_fases(fases_sel)
    except ValueError as e:
        raise ErroUso(str(e)) from None
    for nome, v in (("delta_latencia", delta_latencia), ("w_analise_s", w_analise_s)):
        if isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) or v <= 0:
            raise ErroUso("%s deve ser numero > 0 (sem valor padrao; o pre-registro fixa): %r" % (nome, v))
    tipos = dict(tipos_celula or {})
    ruins = {c: t for c, t in tipos.items() if t not in TIPOS_CELULA}
    if ruins:
        raise ErroUso("tipo de celula invalido: %r (use %s)" % (ruins, "/".join(TIPOS_CELULA)))
    sem_tipo = sorted(set(man) - set(tipos))
    fora = sorted(set(tipos) - set(man))
    if sem_tipo:
        raise ErroUso("celula sem tipo (teste, descritiva ou referencia): %s" % ",".join(sem_tipo))
    if fora:
        raise ErroUso("celula tipada ausente do manifesto: %s" % ",".join(fora))
    refs = {}
    for c, caminho in (referencias_erro or {}).items():
        if c not in man:
            raise ErroUso("referencia de erro para celula ausente do manifesto: %s" % c)
        refs[c] = referencia_erro.ler(caminho)
    if energia is not None:
        for k in ("p_max_w", "razao_central"):
            v = energia.get(k)
            if isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v):
                raise ErroUso("energia.%s invalido: %r" % (k, v))
        if energia["p_max_w"] <= 0 or not 0 <= energia["razao_central"] < 1:
            raise ErroUso("energia: p_max_w > 0 e razao_central em [0, 1) (recebido %r)" % (energia,))
    w_ns = int(round(float(w_recovery_s) * 1e9))
    w_an_ns = int(round(float(w_analise_s) * 1e9))
    razao_c = None if energia is None else energia["razao_central"]
    celulas, regras = {}, {}
    for celula, blocos in man.items():
        tipo = tipos[celula]
        teste = tipo == "teste"
        res, infos = {}, {}
        for b, par in blocos.items():
            res[b], infos[b] = {}, {}
            for br in BRACOS:
                res[b][br], infos[b][br] = desfechos_execucao(par[br], rep, fases_sel, w_ns, marcadores_nome,
                                                              w_analise_ns=w_an_ns, referencia_erro=refs.get(celula),
                                                              energia=energia)
                ctl = infos[b][br]["controlador"]
                if ctl is not None and ctl["modo"] != MODO_ESPERADO[br]:
                    raise ErroUso("braco %s do bloco %s da celula %s com trilha em modo %r (esperado %s): %s"
                                  % (br, b, celula, ctl["modo"], MODO_ESPERADO[br], par[br]))
        nomes = sorted({n for b in blocos for br in BRACOS for n in res[b][br]})
        desf, usados_por = {}, {}
        for indice, nome in enumerate(nomes):
            escala = ESCALA_DIF if nome in ESCALA_DIFERENCA else ESCALA_LOG
            y_a, y_b, pares, excl, ns, usados = [], [], [], [], {}, []
            for b in blocos:
                ra, rb = res[b]["A"].get(nome), res[b]["B"].get(nome)
                ns[b] = {"A": None if ra is None else ra["n"], "B": None if rb is None else rb["n"]}
                motivo = None
                if nome == DESFECHO_CPU or nome.startswith(PREFIXO_ENERGIA):
                    st = (infos[b]["A"]["steal_excluida"], infos[b]["B"]["steal_excluida"])
                    if True in st:
                        motivo = "steal>5%"
                    elif None in st:
                        motivo = "steal-indisponivel"
                if motivo is None:
                    for br, r in (("A", ra), ("B", rb)):
                        if r is None:
                            motivo = "%s: desfecho ausente" % br
                            break
                        if r["y"] is None:
                            motivo = "%s: %s" % (br, r["motivo"])
                            break
                        if escala == ESCALA_LOG and r["y"] <= 0:
                            motivo = "%s: valor zero (log indefinido)" % br
                            break
                if motivo:
                    excl.append({"bloco": b, "motivo": motivo})
                    continue
                y_a.append(ra["y"])
                y_b.append(rb["y"])
                usados.append(b)
                if ra["eventos"] is not None and rb["eventos"] is not None:
                    pares.append((ra["eventos"], rb["eventos"]))
            pares = pares if len(pares) == len(usados) and pares else None
            if y_a:
                an = analisar_desfecho(y_a, y_b, semente, indice, n_boot, pares, n_hier, escala=escala, permutar=teste)
            else:
                an = {"n_blocos": 0, "estimativa": None, "ic_t": None, "ic_boot_blocos": None,
                      "ic_hierarquico": None, "p_permutacao": None, "d_b": []}
            k = k_nao_nulos(an["d_b"])
            ent = {"familia": familia_de(nome, razao_c), "escala": escala, "indice_semente": indice,
                   "n_blocos": an["n_blocos"], "estimativa": an["estimativa"], "ic_t": an["ic_t"],
                   "ic_boot_blocos": an["ic_boot_blocos"], "ic_hierarquico": an["ic_hierarquico"],
                   "p_permutacao": an["p_permutacao"], "p_holm": None,
                   "k_nao_nulos": k, "p_minimo_atingivel": p_minimo_atingivel(k) if an["n_blocos"] else None,
                   "d_b": dict(zip(usados, an["d_b"])), "n_por_bloco": ns, "blocos_excluidos": excl}
            if nome == DESFECHO_NI:
                ent["nao_inferioridade"] = {"delta": delta, "limite": 1.0 - delta,
                                            "limite_inferior_ic_t": an["ic_t"][0] if an["ic_t"] else None,
                                            "resultado": nao_inferioridade(an, delta)}
            if nome == DESFECHO_L:
                ent["nao_inferioridade"] = {"delta": float(delta_latencia), "limite": 1.0 + float(delta_latencia),
                                            "limite_superior_ic_t": an["ic_t"][1] if an["ic_t"] else None,
                                            "p": (p_nao_inferioridade(an["d_b"], math.log(1.0 + float(delta_latencia)))
                                                  if teste else None)}
            if nome in (DESFECHO_DREC, DESFECHO_DREC_MET):
                ent["censuradas"] = {br: sum(1 for b in blocos if (res[b][br].get(nome) or {}).get("censurada") is True)
                                     for br in BRACOS}
            desf[nome] = ent
            usados_por[nome] = usados
        if teste:
            sec = [n for n in nomes if desf[n]["familia"] == FAMILIA_SEC and desf[n]["p_permutacao"] is not None]
            for n, ph in zip(sec, holm([desf[n]["p_permutacao"] for n in sec])):
                desf[n]["p_holm"] = ph
        else:
            sec = []
        excl_steal = sorted({b for b in blocos if True in (infos[b]["A"]["steal_excluida"],
                                                            infos[b]["B"]["steal_excluida"])},
                            key=_chave_bloco)
        rast = _rastreabilidade_celula(list(blocos), infos)
        c = {"tipo": tipo, "teste": teste, "blocos": list(blocos),
             "execucoes": {b: {br: _rel(blocos[b][br]) for br in BRACOS} for b in blocos},
             "n_secundarios_holm": len(sec),
             "blocos_excluidos_por_steal": excl_steal,
             "desfechos": desf,
             "violacoes": _violacoes_celula(list(blocos), infos, semente, len(nomes), n_boot),
             "controlador": _resumo_controlador(list(blocos), infos),
             "rastreabilidade": rast,
             "recuperacao": {"fim_medicao_origem": {b: {br: infos[b][br]["fim_medicao_origem"] for br in BRACOS}
                                                    for b in blocos}}}
        if DESFECHO_V in desf:
            c["co_principal_v"] = _co_principal_v(desf[DESFECHO_V], res, usados_por[DESFECHO_V], semente,
                                                  len(nomes) + len(INVARIANTES_VIOL), n_boot, teste)
        else:
            c["co_principal_v"] = {"desfecho": DESFECHO_V, "avaliavel": False, "motivo": "sem acoes S3"}
        if teste:
            ev, el = desf.get(DESFECHO_V), desf.get(DESFECHO_L)
            vz = desf.get(DESFECHO_NI, {}).get("nao_inferioridade", {}).get("resultado", "inconclusivo")
            media_v = ev["estimativa"] if ev else None
            regras[celula] = regra_iu(ev["p_permutacao"] if ev else None, media_v,
                                      c["co_principal_v"].get("avaliavel", False),
                                      el["ic_t"] if el else None, el["nao_inferioridade"]["p"] if el else None,
                                      delta_latencia, vz, rast["B"]["integral"])
            c["regra_iu"] = regras[celula]
        celulas[celula] = c
    script = Path(__file__).resolve()
    return _f({"esquema": ESQUEMA,
               "parametros": {"semente_boot": int(semente), "n_boot": n_boot, "n_hier": n_hier,
                              "fases": fases_sel, "delta": delta, "rep": rep,
                              "w_recovery_s": float(w_recovery_s), "marcadores_nome": marcadores_nome,
                              "delta_latencia": float(delta_latencia), "w_analise_s": float(w_analise_s),
                              "alfa": ALFA, "quantil_linha_de_base": QUANTIL_LINHA_DE_BASE,
                              "co_principal_v": DESFECHO_V, "co_principal_l": DESFECHO_L,
                              "tipos_celula": dict(sorted(tipos.items())),
                              "referencias_erro": refs,
                              "energia": (energia if energia is None else
                                          {"p_max_w": energia["p_max_w"], "razao_central": energia["razao_central"]}),
                              "regra": "intersecao-uniao (Berger 1982; D-1); Holm entre celulas de teste sobre p_IU",
                              "delta_rec": "ultimo evento anomalo (ERROR_TRANSPORT, ERROR_UNAVAILABLE, falha "
                                           "percebida) observado depois do fim da falha; censura nos ultimos "
                                           "W_c = w_analise_s da medicao (plano 3.4)",
                              "delta_conv": "t_convergencia_abs_ns - t_fim_carga_ns (plano 3.4)",
                              "ic_primario": "t (decisao D3)", "p99": "posto-mais-proximo, n >= %d" % N_MIN_P99,
                              "permutacao": "exata, troca de sinal, bilateral, identidade incluida"},
               "manifesto": {"arquivo": _rel(manifesto), "sha256": hashlib.sha256(Path(manifesto).read_bytes()).hexdigest()},
               "celulas": celulas,
               "conclusao_global": conclusao_global(regras),
               "gerado_por": {"script": "analysis/comparar_bracos.py",
                              "sha256": hashlib.sha256(script.read_bytes()).hexdigest()}})


def _rel(p):
    try:
        return Path(os.path.relpath(Path(p).resolve(), Path.cwd().resolve())).as_posix()
    except ValueError:
        return Path(p).resolve().as_posix()


def _fmt(x, casas=4):
    return "—" if x is None else ("%.*f" % (casas, x))


def _fmt_ic(ic):
    return "—" if not ic else "[%s; %s]" % (_fmt(ic[0]), _fmt(ic[1]))


def tabela_markdown(resultado):
    par = resultado["parametros"]
    linhas = ["# Comparacao pareada A (shadow) x B (active), %s" % resultado["esquema"], "",
              "Fases: %s. Co-principais: `%s` (diferenca B - A) e `%s` (razao B/A). Regra: %s; alfa = %s; "
              "delta_L = %s; W_c = %s s. IC primario: %s. Semente: %d; %d reamostragens de blocos, %d "
              "hierarquicas." % (",".join(par["fases"]), par["co_principal_v"], par["co_principal_l"], par["regra"],
                                 _fmt(par["alfa"], 2), _fmt(par["delta_latencia"], 2), _fmt(par["w_analise_s"], 3),
                                 par["ic_primario"], par["semente_boot"], par["n_boot"], par["n_hier"]), ""]
    g = resultado["conclusao_global"]
    if g["celulas"]:
        linhas += ["## Conclusao global (Holm entre celulas de teste, m = %d)" % g["m"], "",
                   "| Celula | Resultado da celula | p_IU | p_IU Holm | Melhoria (FWER %s) |" % _fmt(g["alfa"], 2),
                   "|---|---|---|---|---|"]
        for c, x in g["celulas"].items():
            linhas.append("| %s | %s | %s | %s | %s |" % (c, x["resultado_celula"], _fmt(x["p_iu"]),
                                                         _fmt(x["p_iu_holm"]), "sim" if x["melhoria"] else "nao"))
        linhas.append("")
    for celula, c in resultado["celulas"].items():
        linhas += ["## Celula %s (%s, %d blocos)" % (celula, c["tipo"], len(c["blocos"])), "",
                   "| Desfecho | Familia | Escala | n blocos | Estimativa | IC t 95 % | IC boot blocos | IC hierarquico "
                   "| p (permutacao) | p Holm | k != 0 | p minimo | Excluidos |",
                   "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
        for nome, e in c["desfechos"].items():
            linhas.append("| `%s` | %s | %s | %d | %s | %s | %s | %s | %s | %s | %d | %s | %d |" % (
                nome, e["familia"], e["escala"], e["n_blocos"], _fmt(e["estimativa"]), _fmt_ic(e["ic_t"]),
                _fmt_ic(e["ic_boot_blocos"]), _fmt_ic(e["ic_hierarquico"]), _fmt(e["p_permutacao"]),
                _fmt(e["p_holm"]), e["k_nao_nulos"], _fmt(e["p_minimo_atingivel"]), len(e["blocos_excluidos"])))
        linhas.append("")
        linhas.append("Estimativa: razao B/A na escala log-razao; media de y_B - y_A na escala diferenca.")
        r = c.get("regra_iu")
        if r:
            linhas.append("Intersecao-uniao: H_V %s (p = %s, media d_b = %s), H_L %s (limite superior do IC t = %s, "
                          "limite %s, p = %s), vazao %s, rastreabilidade %s -> p_IU = %s, **%s**%s."
                          % ({True: "rejeitada", False: "nao rejeitada", None: "nao avaliavel"}[r["h_v"]["rejeitada"]],
                             _fmt(r["h_v"]["p"]), _fmt(r["h_v"]["media_d"]),
                             {True: "rejeitada", False: "nao rejeitada", None: "nao avaliavel"}[r["h_l"]["rejeitada"]],
                             _fmt(r["h_l"]["limite_superior_ic_t"]), _fmt(r["h_l"]["limite"]), _fmt(r["h_l"]["p"]),
                             r["vazao"], "integral" if r["rastreabilidade_integral"] else "incompleta",
                             _fmt(r["p_iu"]), r["resultado"],
                             (" (" + "; ".join(r["motivos"]) + ")") if r["motivos"] else ""))
        v = c.get("co_principal_v", {})
        if v.get("avaliavel") is False and v.get("motivo"):
            linhas.append("Taxa de violacoes: %s." % v["motivo"])
        for br, x in (v.get("regra_de_tres") or {}).items():
            linhas.append("- %s: %s, limite superior 95 %% = %s por acao (%s)."
                          % (br, x["texto"], _fmt(x["limite_superior_95"], 6), x["rotulo"]))
        ni = c["desfechos"].get(DESFECHO_NI, {}).get("nao_inferioridade")
        if ni:
            linhas.append("Nao-inferioridade da vazao util (delta = %s): limite inferior do IC t = %s -> **%s**."
                          % (_fmt(ni["delta"], 2), _fmt(ni["limite_inferior_ic_t"]), ni["resultado"]))
        if c["blocos_excluidos_por_steal"]:
            linhas.append("Blocos fora dos desfechos de CPU e energia por steal > 5 %%: %s."
                          % ", ".join(c["blocos_excluidos_por_steal"]))
        vi = c["violacoes"]
        if vi.get("presente"):
            for k in INVARIANTES_VIOL:
                rk = vi[k]
                txt = "%s: A = %d, B = %d (N: A = %d, B = %d)" % (k, rk["contagem"]["A"], rk["contagem"]["B"],
                                                                  rk["n_base"]["A"], rk["n_base"]["B"])
                for br, x in rk.get("regra_de_tres", {}).items():
                    txt += "; %s nao observado, limite superior 95 %% = %s (%s)" % (
                        br, _fmt(x["limite_superior_95"], 6), x["rotulo"])
                if "razao_de_taxas" in rk:
                    txt += "; razao de taxas B/A = %s, IC %s" % (_fmt(rk["razao_de_taxas"]["estimativa"]),
                                                                 _fmt_ic(rk["razao_de_taxas"]["ic_boot_blocos"]))
                linhas.append("- " + txt)
        tot = c["controlador"]["totais"]
        for br in BRACOS:
            t = tot.get(br)
            if t:
                linhas.append("- Controlador, braco %s (%d trilhas): %d transicoes, %d entradas em CONSERVADOR "
                              "(%d antes da falha), tempo em CONSERVADOR %s s, reversoes %d ok / %d falha, mediana "
                              "do atraso da primeira acao %s s." % (
                                  br, t["execucoes_com_trilha"], t["transicoes"], t["entradas_conservador"],
                                  t["entradas_antes_da_falha"], _fmt(t["tempo_conservador_s"], 1),
                                  t["reversoes"]["sucesso"], t["reversoes"]["falha"],
                                  _fmt(t["mediana_atraso_primeira_acao_s"], 2)))
        linhas.append("")
    return "\n".join(linhas) + "\n"


def _lista(txt):
    return [x.strip() for x in (txt or "").split(",") if x.strip()]


def main(argv=None):
    ap = argparse.ArgumentParser(description="Comparacao pareada A x B por blocos (B-39.4, comparar-bracos/2).")
    ap.add_argument("--manifesto", required=True)
    ap.add_argument("--semente-boot", type=int, required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--delta-latencia", type=float, required=True)
    ap.add_argument("--w-analise-s", type=float, required=True)
    ap.add_argument("--celulas-teste", default="")
    ap.add_argument("--celulas-descritivas", default="")
    ap.add_argument("--celulas-referencia", default="")
    ap.add_argument("--referencia-erro", action="append", default=[], metavar="CELULA=ARQ")
    ap.add_argument("--p-max-w", type=float)
    ap.add_argument("--razao-ocioso-central", type=float)
    ap.add_argument("--fases", default=",".join(FASES_PADRAO))
    ap.add_argument("--rep", type=int, default=1)
    ap.add_argument("--w-recovery-s", type=float, default=60.0)
    ap.add_argument("--n-boot", type=int, default=N_BOOT_PADRAO)
    ap.add_argument("--n-hier", type=int, default=N_HIER_PADRAO)
    ap.add_argument("--delta", type=float, default=DELTA_PADRAO)
    ap.add_argument("--marcadores-nome", default="marcadores.txt")
    a = ap.parse_args(argv)
    saida = Path(a.saida)
    md = saida.with_suffix(".md")
    try:
        for p in (saida, md):
            if p.exists():
                raise ErroUso("saida ja existe (nunca sobrescreve): %s" % p)
        if saida.suffix != ".json":
            raise ErroUso("--saida deve terminar em .json")
        if a.n_boot < 100 or a.n_hier < 100:
            raise ErroUso("--n-boot e --n-hier devem ser >= 100")
        if not 0 < a.delta < 1:
            raise ErroUso("--delta fora de (0, 1)")
        if not (math.isfinite(a.delta_latencia) and a.delta_latencia > 0):
            raise ErroUso("--delta-latencia deve ser > 0")
        if not (math.isfinite(a.w_analise_s) and a.w_analise_s > 0):
            raise ErroUso("--w-analise-s deve ser > 0")
        tipos = {}
        for tipo, txt in (("teste", a.celulas_teste), ("descritiva", a.celulas_descritivas),
                          ("referencia", a.celulas_referencia)):
            for c in _lista(txt):
                if c in tipos:
                    raise ErroUso("celula %s em mais de uma lista de tipo (%s e %s)" % (c, tipos[c], tipo))
                tipos[c] = tipo
        refs = {}
        for item in a.referencia_erro:
            c, sep, arq = item.partition("=")
            if not sep or not c.strip() or not arq.strip():
                raise ErroUso("--referencia-erro espera CELULA=ARQ: %r" % item)
            if c.strip() in refs:
                raise ErroUso("--referencia-erro repetido para a celula %s" % c.strip())
            refs[c.strip()] = arq.strip()
        if (a.p_max_w is None) != (a.razao_ocioso_central is None):
            raise ErroUso("--p-max-w e --razao-ocioso-central vao juntos (energia estimada)")
        energia = None
        if a.p_max_w is not None:
            p_max = int(a.p_max_w) if a.p_max_w == int(a.p_max_w) else a.p_max_w
            energia = {"p_max_w": p_max, "razao_central": a.razao_ocioso_central}
        r = comparar(a.manifesto, a.semente_boot, delta_latencia=a.delta_latencia, w_analise_s=a.w_analise_s,
                     tipos_celula=tipos, fases=[x for x in a.fases.split(",") if x], rep=a.rep,
                     w_recovery_s=a.w_recovery_s, n_boot=a.n_boot, n_hier=a.n_hier, delta=a.delta,
                     marcadores_nome=a.marcadores_nome, referencias_erro=refs, energia=energia)
        dados = (json.dumps(r, indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")
        tab = tabela_markdown(r).encode("utf-8")
        with open(saida, "xb") as fh:
            fh.write(dados)
        with open(md, "xb") as fh:
            fh.write(tab)
    except ERROS_USO as e:
        print("ERRO: %s" % e, file=sys.stderr)
        return 2
    except FileExistsError as e:
        print("ERRO: saida ja existe: %s" % e, file=sys.stderr)
        return 2
    print("comparacao gravada em %s e %s" % (saida, md))
    return 0


if __name__ == "__main__":
    sys.exit(main())
