"""Derivacao da calibracao S3 (W-9): regras 7.1 a 7.18 do pre-registro da calibracao, so braco A.

Fonte das regras: docs/pre-registro-calibracao-s3-2026-11-05.md (branch docs/pre-registro-calibracao-s3, DT-020),
secao 7, com as decisoes do Gestor de 05/11 transmitidas pelo coordenador:
  - criterio de falso acionamento C-8: zero ocorrencias nas janelas das execucoes sem falha; ao lado de cada limiar,
    N e o limite superior de 95 % 3/N (com o exato 1 - 0,05^(1/N) ao lado, o mesmo de comparar_bracos.regra_de_tres);
  - regra 7.13 (kLag): conta so as janelas em que vale o predicado da politica P1,
    top.vistaCompleta() && suspeito != null && persistencia >= kLag && top.respondeuEmTodas(suspeito)
    (workload/.../controle/PoliticaPorAssinatura.java:158); a assinatura sem resposta REST (F1, no morto) e contada a
    parte e nao entra no criterio; kLag*W + 13,3 < 39,8, senao o AC-5 e declarado inviavel (7.4);
  - nenhuma regra olha diferenca entre bracos nem efeito do controlador: execucao de braco B ou em active recusa.

Entradas (formato real do orquestrador S3, scripts/run-campanha-s3.sh do W-6): o diretorio --saida da campanha, com
um exec-NNNN-<celula>-A por execucao (execucao.json, checagem.json, rep-001.csv + meta-csv-rep-001.json,
observaveis-rep-001.jsonl, decisoes-rep-001.jsonl, violacoes-rep-001.csv, s3-acoes-rep-001.csv,
convergencia-resumo-rep-001.json e marcadores.txt nas celulas com falha), e o arquivo campanha-s3/1 da fase. So
execucoes com veredito ACEITA entram; as abortadas (DT-014) e as nao aceitas vao para a lista de excluidas.

Reaproveita (sem duplicar): referencia_p99 (gerar_referencia e percentil por posto mais proximo, 7.1, 7.2 e 7.11),
energia_proxy (leitura do CSV de eventos e do CSV S3) e fases (fase_de/expandir_fases, 7.18). Do comparar_bracos.py
(que importa numpy e nao roda em -I) ha copias curtas com teste de paridade (test_calibracao_s3.Paridade, em
python -E -P): instante do CSV de violacoes, classe de seguranca I1 + I4 + I2 REAL, Delta_conv e taxa por mil acoes.

Uso (da raiz do repositorio; so biblioteca padrao):
  python3 -I analysis/calibracao_s3.py --campanha <saida da campanha> --parametros <campanha-s3-1.json>
      --saida <derivacao.json> [--referencias-dir <dir>]

Saida: JSON deterministico (chaves ordenadas, sem relogio) com cada parametro, o valor, a regra aplicada, os dados
usados (celulas, execucoes, N) e a incerteza; as leituras adotadas onde a regra e ambigua vao em "ambiguidades".
Nunca sobrescreve. Com --referencias-dir grava referencia-S3-async.json e referencia-S3-sync.json (esquema
referencia-p99/1, os mesmos bytes do referencia_p99.gravar_json) com o SHA-256 que esta no JSON.
Exit: 0 ok, 2 uso/contrato/saida existente (nada e escrito).
"""
import argparse
import calendar
import csv
import hashlib
import json
import math
import re
import sys
import time
from collections import Counter
from fractions import Fraction
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # referencia_p99, energia_proxy, fases

import energia_proxy  # noqa: E402
import fases as _fases  # noqa: E402
import referencia_p99  # noqa: E402
from referencia_p99 import percentil  # noqa: E402  (posto mais proximo; copia de rebaseline.py:75-83)

ESQUEMA = "calibracao-s3/1"
PRE_REGISTRO = "docs/pre-registro-calibracao-s3-2026-11-05.md, secao 7 (DT-020)"
S = 1_000_000_000
MS = 1_000_000

# ------------------------------------------------------------------ constantes (cada uma com a origem)
N_LAT = 100                    # 7.3 e 7.9: n_lat = 100 (plano de 22/10 §3.2); n_err = n_lat (C-9)
K_NUCLEO = 2                   # 7.6: k = 2 (derivacao binomial do plano de 22/10 §3.2; C-9)
N_MIN_REF = 1000               # 7.1: --n-min 1000 (decisao D2 de 27/10)
JANELA_LAMBDA_S = 5            # 7.2: janelas de 5 s da fase de medicao
QUANTIL_LAMBDA = 0.05          # 7.2: quantil de 5 %
QUANTIL_LAG_IN = 0.99          # 7.11: quantil de 99 % (plano de 04/11 §4.6)
D_S = 256                      # notacao da secao 7: D = +44 a +300 s da ancora
ALFA = Fraction(5, 100)        # notacao da secao 7: alvo a = 0,05 W / D (plano de alinhamento §3.6)
T_PARADA = Fraction(133, 10)   # 7.4/7.13: t_parada = 13,3 s (P-42; I-3 do B-44.2)
T_DET_MIN = Fraction(398, 10)  # 7.4/7.13: menor T_det da VM, 39,8 s (V-S7r.md:45)
P_SONDA_S = 2                  # 7.14: periodo da sonda de topologia (B-26.2)
TETO_CONV_BASE_S = 600         # 7.10
FATOR = Fraction(3, 2)         # 7.10 e 7.17: fator 1,5 (plano de 04/11 §9, W-6 item 4)
T_ENC_S = 21                   # 7.17 (formula de 4.1): T_enc no pior caso (contrato JSONL 1.2, P-42)
EXEC_TETO_MIN_S = 1010         # 7.17: nunca abaixo de 1010 s (C-6)
N_BLOCOS = 12                  # 7.16: n = 12, fixo (decisao do Gestor, 05/11; B-40 §7)
CONFIANCA_Q = 0.95             # secao 6: q-chapeu com intervalo de Clopper-Pearson de 95 % (Decisao 022, REF-098)
FASES_Q = ("durante", "recuperacao")   # secao 6: violacoes das fases durante + recuperacao
M_CELULAS_TESTE = 2            # 7.18: m das celulas de teste do B-40 (ASYNC F2 e F4)

# secao 3: celulas e tamanhos planejados (26 execucoes)
CELULAS_PLANEJADAS = {"ASYNC-none": 5, "ASYNC-F1": 5, "ASYNC-F2": 5, "SYNC-F2": 3, "SYNC-none": 3, "ASYNC-F4": 5}
CEL_SEM_FALHA_ASYNC = ("ASYNC-none",)
CEL_SEM_FALHA_SYNC = ("SYNC-none",)
CEL_M = ("ASYNC-F1", "ASYNC-F2", "ASYNC-F4")                  # 7.8 (m): ASYNC (ultimo paragrafo da secao 7; A-6)
CEL_FALHA = ("ASYNC-F1", "ASYNC-F2", "SYNC-F2", "ASYNC-F4")    # 7.10 e 7.14: "F1, F2, F4" (A-6)
CEL_KLAG = ("ASYNC-none", "ASYNC-F1")                          # 7.13: ASYNC sem falha e ASYNC F1
CEL_TESTE = ("ASYNC-F2", "ASYNC-F4")                           # 7.18: ASYNC F2 e F4 (m = 2)

ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")    # EventoOperacao.erro() (os mesmos de comparar_bracos)
ESQ_OBS = "observaveis-controlador/1.2"
ESQ_DEC = "decisoes-controlador/"

AMBIGUIDADES = [
    {"id": "A-1", "regras": ["7.3", "7.12", "7.13"],
     "texto": "Na calibracao a sonda S_lag roda com o W provisorio (5 s, uma rodada por janela); as regras agrupam as "
              "rodadas pela janela W da 7.3. Se W != 5 s, ha janelas com 2 rodadas (W > 5) ou sem rodada (W < 5).",
     "leitura": "agrupamento pela W da 7.3, como a 7.13 manda; lag da janela = maior lag de cada no entre as rodadas "
                "(SondaDefasagem.resumir); janela sem rodada = S_lag indeterminado e persistencia zerada. Contagem de "
                "janelas sem rodada no dados da 7.13."},
    {"id": "A-2", "regras": ["7.7"],
     "texto": "'menor valor tal que k janelas seguidas >= theta_in tem zero ocorrencias' nao tem minimo nos reais (o "
              "conjunto e aberto: theta > maior minimo de k janelas seguidas).",
     "leitura": "theta_in = menor double acima do maior minimo (math.nextafter), que e exatamente o limiar da "
                "comparacao 'taxa >= theta' que o Controlador faz em double."},
    {"id": "A-3", "regras": ["7.8", "7.12"],
     "texto": "'theta_out = maior valor tal que nenhuma janela fica entre theta_out e theta_in por mais de m janelas "
              "seguidas' e degenerado: o maior theta_out < theta_in deixa a banda vazia e satisfaz sempre (sem "
              "histerese). O mesmo vale para lag_out.",
     "leitura": "valor = leitura literal (maior double abaixo do limiar de entrada). Ao lado, em "
                "dados.alternativa_menor_valor, o menor valor que satisfaz a condicao (provavel intencao). Decisao "
                "do Senior/Gestor."},
    {"id": "A-4", "regras": ["7.8"],
     "texto": "m depende das janelas calmas (que dependem de theta_out) e theta_out depende de m.",
     "leitura": "com a leitura literal de theta_out (A-3) nao ha circularidade: m e calculado com ela; a "
                "alternativa_menor_valor de theta_out e lag_out usa esse m."},
    {"id": "A-5", "regras": ["7.8"],
     "texto": "'janelas calmas seguidas que foi seguido de nova janela alta depois do marcador end': nao diz se "
              "'seguido' e imediato nem se 'alta' e um sinal alto numa janela ou o disparo de R1/R2 (R2 exige k "
              "janelas para latencia, erro e defasagem).",
     "leitura": "trecho calmo imediatamente seguido de janela alta (uma intermediaria no meio nao conta); alta = "
                "algumAlto do Controlador (violacao, topologia anomala, latencia, erro ou defasagem altos); janelas "
                "com inicio em ou depois do end; sem nenhuma recaida, m = 1 + 0."},
    {"id": "A-6", "regras": ["7.3", "7.8", "7.10", "7.14", "7.15"],
     "texto": "A coluna Dados diz 'F1, F2, F4' (7.8, 7.10, 7.14) e 'todas' (7.3, 7.15), mas o ultimo paragrafo da "
              "secao 7 diz que W, theta, k, m, lag_in e kLag saem das execucoes ASYNC.",
     "leitura": "m (7.8) so de ASYNC F1/F2/F4 (o paragrafo nomeia m); 7.10 e 7.14 com a SYNC F2 (tabela); atraso "
                "maximo da 7.3 e 7.15 de todas as aceitas (tabela; R4 da V-W4 fala do maior atraso medido na "
                "calibracao); lambda5 so de ASYNC sem falha (7.2)."},
    {"id": "A-7", "regras": ["7.7", "7.8", "7.12", "7.13"],
     "texto": "'N = numero de janelas usadas' nao diz se conta as janelas em que o criterio nao pode ocorrer.",
     "leitura": "N = janelas avaliadas pelo criterio: 7.7, posicoes com k janelas seguidas determinadas; 7.13, "
                "janelas com vista completa (onde o P1 poderia agir); 7.8/7.12, todas as janelas. O total de "
                "janelas vai ao lado (n_janelas_total)."},
    {"id": "A-8", "regras": ["7.2", "7.5", "7.7", "7.13"],
     "texto": "Fim da medicao e janelas parciais nao estao fixados (D = 256 s e nominal).",
     "leitura": "janelas [origem_ns + jW, origem_ns + (j+1)W) completas ate t_fim_carga_ns do resumo de "
                "convergencia (fim da medicao do protocolo); eventos pela janela do end_ns (AgregadorDeJanela); D = "
                "256 s so no alvo a."},
    {"id": "A-9", "regras": ["7.14"],
     "texto": "'duracao de um trecho com 3 nos HEALTHY' nao diz de onde a onde.",
     "leitura": "amostras de topologia com t_ns > end; trecho = amostras seguidas com anomala = false; duracao = "
                "t da ultima - t da primeira do trecho; conta se a amostra seguinte e anomala."},
    {"id": "A-10", "regras": ["7.15"],
     "texto": "'violacoes' nao diz a classe nem a fase.",
     "leitura": "I1 + I4 + I2 REAL (a classe de seguranca do gatilho R1, a mesma do hold_viol), todas as do "
                "violacoes-rep-001.csv da execucao, pelo instante."},
    {"id": "A-11", "regras": ["7.17"],
     "texto": "convergencia_periodo_s da formula, t_pre, t_pos e unidade do volume nao estao definidos para o "
              "orquestrador S3.",
     "leitura": "convergencia_periodo_s = W da 7.3 (secao 4.1 em 2c8b7b8: 'na avaliacao, acompanha o W da regra "
                "7.3'); t_pre = ancora_ms - lancamento_ms; t_pos = fim_utc - t_convergencia_abs_ns (fim_utc inclui "
                "as esperas do orquestrador depois da carga: superestima); T_enc = 21 s; volume = soma dos arquivos "
                "do diretorio da execucao em MiB (o df -Pm do orquestrador), arredondado para cima; teto "
                "arredondado para cima (como 1009,85 -> 1010 em 4.1)."},
    {"id": "A-12", "regras": ["7.16"],
     "texto": "T_exec nao esta definido.",
     "leitura": "fim_utc - recriacao_ms do execucao.json (recriacao, K1, carga e esperas; sem a pos-checagem e o "
                "h4)."},
    {"id": "A-13", "regras": ["7.18"],
     "texto": "A curva de poder sai do poder.py, que importa numpy e nao roda em python3 -I.",
     "leitura": "o script calcula q-chapeu, o Clopper-Pearson e as taxas por execucao e grava o comando do "
                "poder.py por celula de teste; a semente e o n-sim nao estao no pre-registro (ficam <S> e <N>)."},
    {"id": "A-14", "regras": ["7.2", "7.11"],
     "texto": "O metodo do quantil nao esta escrito.",
     "leitura": "posto mais proximo (referencia_p99.percentil), a mesma logica do p99 de referencia (B-39.1), que "
                "o contrato JSONL cita para o lagInMs."},
    {"id": "A-15", "regras": ["7.8", "7.13", "7.14"],
     "texto": "A atribuicao das amostras de topologia e das rodadas de defasagem as janelas nao esta escrita.",
     "leitura": "a do codigo (SondaTopologia.resumirComNosAte e SondaDefasagem.resumirAte): a janela j leva o que "
                "tem instante < fim_j e ainda nao foi consumido (a primeira leva tambem o aquecimento da "
                "topologia); offline, sem atraso de entrega."},
    {"id": "A-16", "regras": ["7.10"],
     "texto": "'execucoes que convergiram' e o Delta_conv nao estao definidos aqui.",
     "leitura": "Delta_conv do comparar_bracos (t_convergencia_abs_ns - t_religamento_ns ou t_fim_carga_ns; sem "
                "divergencia persistente); convergencia_estado diferente de 'convergiu' fica fora (secao 5)."},
    {"id": "A-17", "regras": ["7.3"],
     "texto": "A 7.3 pede W >= teto + atraso; o codigo exige prazo < W estrito (ParametrosDefasagem.validarContra).",
     "leitura": "W pela formula da 7.3; dados.aceito_pelo_codigo diz se o carregamento aceitaria."},
]


class ErroUso(Exception):
    """Uso invalido, contrato violado ou saida existente: exit 2."""


# ============================================================================= utilitarios

def _sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def _sha256(p):
    return _sha256_bytes(Path(p).read_bytes())


def _ceil_fr(x):
    """Teto de um Fraction/int como int."""
    x = Fraction(x)
    return -((-x.numerator) // x.denominator)


def _fr(x):
    if isinstance(x, Fraction):
        return x
    if isinstance(x, int):
        return Fraction(x)
    return Fraction(repr(float(x)))


def _f(x):
    """Numero para o JSON (Fraction -> float)."""
    return float(x) if isinstance(x, Fraction) else x


_RX_INSTANTE = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$")


def instante_ns(txt):
    """Epoch ns de um Instant.toString (copia de comparar_bracos.instante_ns, :792-801)."""
    m = _RX_INSTANTE.match((txt or "").strip())
    if not m:
        raise ErroUso("instante invalido: %r" % txt)
    seg = calendar.timegm(time.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S"))
    return seg * 1_000_000_000 + int((m.group(2) or "").ljust(9, "0"))


def _taxa(num, den, minimo):
    """num/den, ou None (indeterminado) se den == 0 ou den < minimo (Controlador.taxa)."""
    if den == 0 or den < minimo:
        return None
    return num / den


def incerteza_zero(n, w_s=None):
    """C-8: N e o limite superior de 95 % (3/N, regra de tres; exato 1 - 0,05^(1/N)); alvo a = 0,05 W / D."""
    return {"criterio": "C-8: zero ocorrencias", "N": n,
            "limite_95_3_sobre_n": (3.0 / n) if n else None,
            "limite_95_exato": (1.0 - 0.05 ** (1.0 / n)) if n else None,
            "alvo_a": float(ALFA * w_s / D_S) if w_s else None}


def clopper_pearson(x, n, confianca=CONFIANCA_Q):
    """Intervalo bilateral de Clopper-Pearson de x em n (bissecao exata sobre a binomial; so biblioteca padrao)."""
    x, n = int(x), int(n)
    if n <= 0 or not 0 <= x <= n:
        raise ErroUso("Clopper-Pearson com x = %r, n = %r" % (x, n))
    a = (1.0 - confianca) / 2.0

    def cdf(k, p):     # P(X <= k)
        return sum(math.comb(n, i) * p ** i * (1.0 - p) ** (n - i) for i in range(0, k + 1))

    def raiz(f):       # f crescente em p, f(0) < 0 < f(1)
        lo, hi = 0.0, 1.0
        for _ in range(200):
            mid = (lo + hi) / 2.0
            if f(mid) < 0:
                lo = mid
            else:
                hi = mid
            if hi - lo < 1e-16:
                break
        return (lo + hi) / 2.0

    if x == 0:
        lower = 0.0
    elif x == n:
        lower = a ** (1.0 / n)
    else:
        lower = raiz(lambda p: (1.0 - cdf(x - 1, p)) - a)     # P(X >= x; p) = a
    if x == n:
        upper = 1.0
    elif x == 0:
        upper = 1.0 - a ** (1.0 / n)
    else:
        upper = raiz(lambda p: a - cdf(x, p))                 # P(X <= x; p) = a
    return lower, upper


# ============================================================================= regras puras

def calcular_w(lambda5, atraso_max_ns, teto_ms, n_lat=N_LAT):
    """7.3: W = max(ceil(n_lat / lambda5), ceil(tetoLag + atraso_max)) em segundos inteiros."""
    if lambda5 is None or Fraction(lambda5) <= 0:
        return {"valor": None, "termo_vazao_s": None, "termo_leitura_s": None, "aceito_pelo_codigo": None,
                "motivo": "lambda5 ausente ou zero (7.2)"}
    if atraso_max_ns is None:
        return {"valor": None, "termo_vazao_s": None, "termo_leitura_s": None, "aceito_pelo_codigo": None,
                "motivo": "sem linha defasagem: atraso maximo de inicio desconhecido"}
    tv = _ceil_fr(Fraction(n_lat) / Fraction(lambda5))
    tl = _ceil_fr(Fraction(int(teto_ms) * MS + int(atraso_max_ns), S))
    w = max(tv, tl)
    return {"valor": w, "termo_vazao_s": tv, "termo_leitura_s": tl,
            "aceito_pelo_codigo": int(teto_ms) * MS < w * S, "motivo": None}


def _cabe(k, w_s):
    """kLag*W + t_parada < T_det (7.4/7.13), aritmetica exata."""
    return k * Fraction(w_s) + T_PARADA < T_DET_MIN


def viabilidade(w_s, klag_valor):
    """7.4 (W >= 26,5 s) e o limite da 7.13 (kLag*W + 13,3 >= 39,8): AC-5 inviavel antes da avaliacao."""
    if w_s is None:
        return {"viavel": None, "motivos": ["W indeterminado (7.3): viabilidade nao avaliavel"]}
    motivos = []
    if Fraction(w_s) >= T_DET_MIN - T_PARADA:
        motivos.append("7.4: W = %s s >= 26,5 s (kLag = 1 ja nao cabe em 39,8 - 13,3)" % w_s)
    if klag_valor is not None and not _cabe(klag_valor, w_s):
        motivos.append("7.13: kLag*W + 13,3 = %s s >= 39,8 s (kLag = %d, W = %s s)"
                       % (float(klag_valor * Fraction(w_s) + T_PARADA), klag_valor, w_s))
    return {"viavel": not motivos, "motivos": motivos}


def limiar_entrada(series, k):
    """7.7: menor theta com zero ocorrencias de 'k janelas seguidas >= theta' (A-2: nextafter do maior minimo).

    series: uma lista por execucao, uma taxa por janela (None = indeterminada). N = posicoes com k janelas seguidas
    determinadas (A-7).
    """
    maior, n = None, 0
    mins = []
    for s in series:
        for i in range(k - 1, len(s)):
            trecho = s[i - k + 1:i + 1]
            if any(v is None for v in trecho):
                continue
            n += 1
            v = min(trecho)
            mins.append(v)
            maior = v if maior is None else max(maior, v)
    if n == 0:
        return {"valor": None, "maximo_dos_minimos": None, "N": 0, "ocorrencias": None,
                "motivo": "nenhuma janela valida (nenhuma sequencia de %d janelas determinadas)" % k}
    theta = math.nextafter(maior, math.inf)
    return {"valor": theta, "maximo_dos_minimos": maior, "N": n, "ocorrencias": sum(1 for v in mins if v >= theta),
            "motivo": None}


def _maior_trecho_na_banda(series, baixo, alto):
    maior = 0
    for s in series:
        run = 0
        for v in s:
            if v is not None and baixo < v < alto:
                run += 1
                maior = max(maior, run)
            else:
                run = 0
    return maior


def limiar_saida(series, limiar_in, m):
    """7.8/7.12: theta_out literal (maior valor: A-3) e a alternativa do menor valor que satisfaz a condicao.

    Banda = (theta_out, limiar_in) aberta: calma <= theta_out, alta >= limiar_in, indeterminada conta como calma.
    """
    n_tot = sum(len(s) for s in series)
    if limiar_in is None or m is None:
        return {"valor": None, "alternativa_menor_valor": None, "N": n_tot, "maior_trecho_na_banda": None,
                "maior_trecho_na_banda_alternativa": None,
                "motivo": "depende do limiar de entrada e de m (nao derivados)"}
    literal = math.nextafter(limiar_in, -math.inf)
    cands = sorted({0.0} | {v for s in series for v in s if v is not None and v < limiar_in})
    alt = None
    for c in cands:
        if _maior_trecho_na_banda(series, c, limiar_in) <= m:
            alt = c
            break
    return {"valor": literal, "alternativa_menor_valor": alt, "N": n_tot,
            "maior_trecho_na_banda": _maior_trecho_na_banda(series, literal, limiar_in),
            "maior_trecho_na_banda_alternativa": None if alt is None else _maior_trecho_na_banda(series, alt, limiar_in),
            "motivo": None}


def m_reentrada(sequencias):
    """7.8: m = 1 + maior trecho de janelas calmas imediatamente seguido de janela alta (A-5).

    sequencias: por execucao, a classe de cada janela depois do end: "C" calma, "I" intermediaria, "H" alta.
    """
    maior, trechos, n = None, 0, 0
    for seq in sequencias:
        run = 0
        for c in seq:
            n += 1
            if c == "C":
                run += 1
                continue
            if c == "H" and run > 0:
                trechos += 1
                maior = run if maior is None else max(maior, run)
            run = 0
    motivo = None
    if maior is None:
        motivo = "nenhum trecho calmo seguido de janela alta depois do end (sem recaida): m = 1 + 0"
    return {"valor": 1 + (maior or 0), "maior_trecho": maior or 0, "trechos_seguidos_de_alta": trechos, "N": n,
            "motivo": motivo}


def resumo_lag(rodadas):
    """(lag_max, suspeito) de uma janela: SondaDefasagem.resumir (:200-218) e RodadaDefasagem.maximo/unicoMaximo."""
    if not rodadas:
        return None, None
    por_no = {}
    for r in rodadas:
        for x in r.get("lag_por_no") or ():
            no, v = x["no"], float(x["lag_ms"])
            por_no[no] = v if no not in por_no else max(por_no[no], v)
    mx = 0.0
    for v in por_no.values():
        mx = max(mx, v)
    melhor, maximo, empate = None, -math.inf, False
    for no, v in por_no.items():
        if v > maximo:
            maximo, melhor, empate = v, no, False
        elif v == maximo:
            empate = True
    return mx, (None if empate else melhor)


def k_max_no_limite(w_s):
    """Maior k >= 0 com k*W + 13,3 < 39,8."""
    k = 0
    while _cabe(k + 1, w_s):
        k += 1
    return k


def klag(execucoes, teto_ms, w_s):
    """7.13: menor k >= 1 com zero ocorrencias do predicado do P1, e a assinatura sem resposta a parte.

    execucoes: [(nome, [janela...])], janela = {"lag_max", "suspeito", "vista_completa", "respondeu_em_todas" (set),
    e opcional "todos_responderam"}. Persistencia como PoliticaPorAssinatura.atualizarPersistencia (:198-214), com a
    sequencia zerada abaixo do teto ou sem suspeito; janelas contiguas (offline nao ha janela do R4).
    """
    contadas, sem = Counter(), Counter()
    maior_c, maior_s, n_vc, n_tot, n_sem = 0, 0, 0, 0, 0
    por_exec_sem, por_exec = [], []
    for nome, janelas in execucoes:
        sus, pers = None, 0
        mot, sem_e, cont_e, maior_e = Counter(), 0, 0, 0
        for jn in janelas:
            n_tot += 1
            lag, s = jn.get("lag_max"), jn.get("suspeito")
            if lag is None or s is None or lag < teto_ms:
                sus, pers = None, 0
            elif s == sus:
                pers += 1
            else:
                sus, pers = s, 1
            vc = bool(jn.get("vista_completa"))
            if vc:
                n_vc += 1
            if pers < 1:
                continue
            if vc and sus in jn.get("respondeu_em_todas", ()):
                cont_e += 1
                maior_c = max(maior_c, pers)
                for k in range(1, pers + 1):
                    contadas[k] += 1
            else:
                sem_e += 1
                maior_e = max(maior_e, pers)
                for k in range(1, pers + 1):
                    sem[k] += 1
                if sus not in jn.get("respondeu_em_todas", ()):
                    mot["suspeito_sem_resposta"] += 1
                elif jn.get("todos_responderam") is False:
                    mot["outro_no_sem_resposta"] += 1
                else:
                    mot["vista_incompleta"] += 1
        n_sem += sem_e
        maior_s = max(maior_s, maior_e)
        por_exec.append({"dir": nome, "janelas_contadas": cont_e})
        if sem_e:
            por_exec_sem.append({"dir": nome, "janelas": sem_e, "maior_persistencia": maior_e,
                                 "motivos": dict(sorted(mot.items()))})
    sem_resposta = {"janelas": n_sem, "maior_persistencia": maior_s,
                    "por_k": {str(k): v for k, v in sorted(sem.items())}, "execucoes": por_exec_sem,
                    "nota": "janelas com S_lag no teto e o mesmo suspeito (condicao 1) em que a vista nao e completa "
                            "ou o suspeito nao respondeu a REST em todas as amostras (condicoes 2 ou 3): a politica "
                            "nao age nelas e elas nao entram no criterio"}
    base = {"maior_persistencia_contada": maior_c,
            "ocorrencias_contadas_por_k": {str(k): v for k, v in sorted(contadas.items())},
            "sem_resposta": sem_resposta, "N": n_vc, "n_janelas_total": n_tot, "por_execucao": por_exec,
            "k_max_no_limite": None if w_s is None else k_max_no_limite(w_s)}
    if n_vc == 0:
        return dict(base, valor=None, dentro_do_limite=None,
                    motivo="nenhuma janela valida (nenhuma janela com vista completa nas execucoes da regra)")
    valor = maior_c + 1
    dentro = None if w_s is None else _cabe(valor, w_s)
    return dict(base, valor=valor, dentro_do_limite=dentro, motivo=None)


def t_estavel(execucoes, p_sonda_s=P_SONDA_S):
    """7.14: maior trecho com 3 nos HEALTHY depois do end que terminou em amostra anomala, + P_sonda (A-9).

    execucoes: [(nome, t_end_ns, [(t_ns, anomala)])].
    """
    maior, recaidas, por = None, 0, []
    for nome, t_end, amostras in execucoes:
        ini = ult = None
        maior_e = None
        for t, an in sorted(x for x in amostras if x[0] > t_end):
            if not an:
                if ini is None:
                    ini = t
                ult = t
                continue
            if ini is not None:
                d = ult - ini
                recaidas += 1
                maior_e = d if maior_e is None else max(maior_e, d)
            ini = ult = None
        por.append({"dir": nome, "maior_trecho_s": None if maior_e is None else maior_e / S})
        if maior_e is not None:
            maior = maior_e if maior is None else max(maior, maior_e)
    if maior is None:
        return {"valor": p_sonda_s, "maior_trecho_s": None, "recaidas": 0, "por_execucao": por,
                "motivo": "nenhuma recaida depois do end: T_estavel = P_sonda"}
    return {"valor": maior / S + p_sonda_s, "maior_trecho_s": maior / S, "recaidas": recaidas, "por_execucao": por,
            "motivo": None}


def t_hold_viol(execucoes, w_s):
    """7.15: maior intervalo entre duas violacoes seguidas na mesma execucao; sem execucao com duas, W."""
    maior, por = None, []
    for nome, ts in execucoes:
        ts = sorted(ts)
        m = max((b - a for a, b in zip(ts, ts[1:])), default=None)
        por.append({"dir": nome, "violacoes": len(ts), "maior_intervalo_s": None if m is None else m / S})
        if m is not None:
            maior = m if maior is None else max(maior, m)
    if maior is None:
        return {"valor": w_s, "maior_intervalo_s": None, "por_execucao": por,
                "motivo": "nenhuma execucao com duas violacoes: T_hold_viol = W"
                          + ("" if w_s is not None else " (W indeterminado)")}
    return {"valor": maior / S, "maior_intervalo_s": maior / S, "por_execucao": por, "motivo": None}


def teto_convergencia(deltas):
    """7.10: 600 s se o maior Delta_conv < 400 s; senao 1,5 x o maior. deltas = [(nome, delta_s)]."""
    if not deltas:
        return {"valor": None, "maior_delta_conv_s": None, "n": 0,
                "motivo": "nenhuma execucao das celulas da regra convergiu (Delta_conv indisponivel)"}
    mx = max(d for _, d in deltas)
    lim = Fraction(TETO_CONV_BASE_S) / FATOR
    valor = TETO_CONV_BASE_S if _fr(mx) < lim else float(FATOR * _fr(mx))
    return {"valor": valor, "maior_delta_conv_s": mx, "n": len(deltas), "motivo": None}


def exec_teto(duracao_s, religamento_prazo_s, teto_conv_s, periodo_s, t_pre_s, t_pos_s):
    """7.17: duracao + T_enc + religamento + teto da convergencia + periodo + 1,5 (t_pre + t_pos), >= 1010 s."""
    if any(x is None for x in (duracao_s, religamento_prazo_s, teto_conv_s, periodo_s, t_pre_s, t_pos_s)):
        return {"valor": None, "formula_s": None, "motivo": "termo indisponivel (teto da 7.10, W, t_pre ou t_pos)"}
    f = (_fr(duracao_s) + T_ENC_S + _fr(religamento_prazo_s) + _fr(teto_conv_s) + _fr(periodo_s)
         + FATOR * (_fr(t_pre_s) + _fr(t_pos_s)))
    return {"valor": max(_ceil_fr(f), EXEC_TETO_MIN_S), "formula_s": float(f), "motivo": None}


# ============================================================================= leitura das execucoes

def _json(p):
    try:
        return json.loads(Path(p).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raise ErroUso("JSON ausente ou ilegivel: %s" % p) from None


def ler_jsonl(p, prefixo_esquema):
    """Linhas de uma trilha JSONL; ultima linha truncada (queda) descartada; meta obrigatoria no inicio."""
    p = Path(p)
    if not p.is_file():
        raise ErroUso("trilha ausente: %s" % p)
    partes = p.read_bytes().decode("utf-8", errors="replace").split("\n")
    sem_quebra = partes[-1] != ""
    if not sem_quebra:
        partes = partes[:-1]
    out = []
    for i, bruta in enumerate(partes):
        if not bruta.strip():
            continue
        try:
            o = json.loads(bruta)
        except ValueError:
            if i == len(partes) - 1 and sem_quebra:
                break
            raise ErroUso("linha %d ilegivel em %s" % (i + 1, p)) from None
        if not isinstance(o, dict) or not isinstance(o.get("tipo"), str):
            raise ErroUso("linha %d sem tipo em %s" % (i + 1, p))
        out.append(o)
    if not out or out[0]["tipo"] != "meta" or not str(out[0].get("esquema", "")).startswith(prefixo_esquema):
        raise ErroUso("trilha sem meta %s no inicio: %s" % (prefixo_esquema, p))
    return out


def ler_violacoes_seguranca(p):
    """Instantes (ns) das violacoes I1, I4 e I2 REAL (regra de comparar_bracos.contar_violacoes, :804-832)."""
    p = Path(p)
    if not p.exists():
        raise ErroUso("violacoes ausente: %s" % p)
    out = []
    with open(p, newline="", encoding="utf-8") as fh:
        for x in csv.DictReader(fh):
            inv = (x.get("invariante") or "").strip()
            cl = (x.get("classificacao") or "").strip()
            if inv in ("I1", "I4") or (inv == "I2" and cl == "REAL"):
                out.append(instante_ns(x.get("instante") or ""))
    return out


class Execucao:
    """Uma execucao ACEITA do braco A, com leitura preguicosa dos brutos."""

    def __init__(self, d, ex, ch):
        self.dir = Path(d)
        self.nome = self.dir.name
        self.ex, self.ch = ex, ch
        self.celula, self.modo, self.falha, self.i = ex["celula"], ex["modo"], ex["falha"], ex["i"]
        self._c = {}

    def arq(self, nome, obrigatorio=True):
        p = self.dir / nome
        if obrigatorio and not p.is_file():
            raise ErroUso("%s sem %s" % (self.nome, nome))
        return p

    def _cache(self, k, f):
        if k not in self._c:
            self._c[k] = f()
        return self._c[k]

    @property
    def eventos(self):
        def f():
            try:
                return energia_proxy.ler_eventos_inicio(self.arq("rep-001.csv"))
            except energia_proxy.ErroUso as e:
                raise ErroUso(str(e)) from None
        return self._cache("ev", f)

    @property
    def acoes(self):
        def f():
            try:
                return energia_proxy.ler_acoes_s3(self.arq("s3-acoes-rep-001.csv"))
            except energia_proxy.ErroUso as e:
                raise ErroUso(str(e)) from None
        return self._cache("ac", f)

    @property
    def obs(self):
        def f():
            linhas = ler_jsonl(self.arq("observaveis-rep-001.jsonl"), ESQ_OBS)
            meta = linhas[0]
            top = [o for o in linhas if o["tipo"] == "topologia"]
            lag = [o for o in linhas if o["tipo"] == "defasagem"]
            if not top:
                raise ErroUso("%s: observaveis sem linha topologia (sonda de topologia desligada?)" % self.nome)
            nos = [x["no"] for x in top[0]["por_no"]]
            if len(nos) != meta.get("nos_esperados"):
                raise ErroUso("%s: %d nos na topologia e nos_esperados = %r" % (self.nome, len(nos),
                                                                                 meta.get("nos_esperados")))
            pd = meta.get("parametros_defasagem") or {}
            if not isinstance(meta.get("origem_ns"), int) or not isinstance(pd.get("periodo_ns"), int):
                raise ErroUso("%s: meta de observaveis sem origem_ns ou parametros_defasagem.periodo_ns" % self.nome)
            return {"meta": meta, "topologia": top, "defasagem": lag, "nos": nos}
        return self._cache("obs", f)

    @property
    def trilha(self):
        return self._cache("dec", lambda: ler_jsonl(self.arq("decisoes-rep-001.jsonl"), ESQ_DEC))

    @property
    def resumo(self):
        return self._cache("res", lambda: _json(self.arq("convergencia-resumo-rep-001.json")))

    @property
    def marcadores(self):
        def f():
            try:
                return _fases.ler_marcadores(self.dir / "marcadores.txt")
            except ValueError as e:
                raise ErroUso("%s: %s" % (self.nome, e)) from None
        return self._cache("mk", f)

    @property
    def viol(self):
        return self._cache("vi", lambda: ler_violacoes_seguranca(self.arq("violacoes-rep-001.csv")))

    @property
    def origem(self):
        return self.obs["meta"]["origem_ns"]

    @property
    def t_fim_carga(self):
        t = self.resumo.get("t_fim_carga_ns")
        if not isinstance(t, int) or isinstance(t, bool):
            raise ErroUso("%s: resumo de convergencia sem t_fim_carga_ns (fim da medicao, A-8)" % self.nome)
        return t

    @property
    def t_end(self):
        t = self.marcadores["fim"]
        if t is None or self.marcadores["inicio"] is None:
            raise ErroUso("%s: celula com falha sem marcadores start/end" % self.nome)
        return t

    def publico(self):
        return {"dir": self.nome, "i": self.i, "celula": self.celula, "modo": self.modo, "falha": self.falha,
                "braco": self.ex.get("braco"), "controller": self.ex.get("controller"), "seed": self.ex.get("seed"),
                "fase": self.ex.get("fase")}


def ler_execucoes(campanha):
    """(usadas, excluidas): ACEITA do braco A; as demais e as abortadas (DT-014) so listadas."""
    campanha = Path(campanha)
    if not campanha.is_dir():
        raise ErroUso("campanha ausente: %s" % campanha)
    usadas, excluidas = [], []
    for d in sorted(x for x in campanha.glob("exec-*") if x.is_dir()):
        if ".abortada-" in d.name:
            excluidas.append({"dir": d.name, "veredito": "ABORTADA (DT-014)"})
            continue
        chp = d / "checagem.json"
        if not chp.is_file():
            excluidas.append({"dir": d.name, "veredito": "SEM CHECAGEM"})
            continue
        ch = _json(chp)
        if ch.get("braco") != "A":
            raise ErroUso("%s: braco %r na calibracao (so braco A; nenhuma regra olha diferenca entre bracos)"
                          % (d.name, ch.get("braco")))
        if ch.get("veredito") != "ACEITA":
            excluidas.append({"dir": d.name, "veredito": ch.get("veredito")})
            continue
        ex = _json(d / "execucao.json")
        cel = ex.get("celula")
        if cel not in CELULAS_PLANEJADAS:
            raise ErroUso("%s: celula %r fora da calibracao (secao 3; F3 fica fora, C-1)" % (d.name, cel))
        if ch.get("celula") != cel:
            raise ErroUso("%s: celula do checagem.json (%r) diferente da do execucao.json (%r)"
                          % (d.name, ch.get("celula"), cel))
        modo_txt, falha = cel.split("-")
        if ex.get("braco") != "A" or ex.get("controller") != "shadow":
            raise ErroUso("%s: braco %r / controller %r (a calibracao e so braco A em shadow)"
                          % (d.name, ex.get("braco"), ex.get("controller")))
        if ex.get("modo") != modo_txt.lower() or ex.get("falha") != falha:
            raise ErroUso("%s: modo/falha do execucao.json (%r/%r) nao batem com a celula %s"
                          % (d.name, ex.get("modo"), ex.get("falha"), cel))
        usadas.append(Execucao(d, ex, ch))
    return usadas, excluidas


def ler_parametros(p):
    """Valores do arquivo campanha-s3/1 (scripts/campanha_s3_conferir.py) usados pelas regras."""
    j = _json(p)
    if not isinstance(j, dict) or j.get("esquema") != "campanha-s3/1" or not isinstance(j.get("parametros"), dict):
        raise ErroUso("parametros: esquema campanha-s3/1 exigido em %s" % p)
    out = {}
    for k in ("hotrod_timeout_ms", "duracao_s", "religamento_prazo_s", "w_recovery_s"):
        v = (j["parametros"].get(k) or {}).get("valor")
        if not isinstance(v, int) or isinstance(v, bool) or v < 0:
            raise ErroUso("parametros: %s ausente ou invalido em %s" % (k, p))
        out[k] = v
    return out


def conferir_execucao(e, teto_ms):
    """Contrato minimo de cada execucao usada: shadow nas duas trilhas, S3, modo, teto da defasagem = prazo (R7)."""
    for nome, meta in (("observaveis", e.obs["meta"]), ("decisoes", e.trilha[0])):
        if meta.get("modo") != "shadow":
            raise ErroUso("%s: %s com modo %r (so braco A em shadow)" % (e.nome, nome, meta.get("modo")))
        if meta.get("cenario") not in (None, "S3") or meta.get("replicacao") not in (None, e.modo):
            raise ErroUso("%s: %s com cenario/replicacao %r/%r" % (e.nome, nome, meta.get("cenario"),
                                                                   meta.get("replicacao")))
    pp = e.trilha[0].get("parametros_politica") or {}
    if pp.get("teto_lag_ms") not in (None, teto_ms):
        raise ErroUso("%s: teto_lag_ms %r != hotrod_timeout_ms %d (R7 da V-W2a)" % (e.nome, pp.get("teto_lag_ms"),
                                                                                   teto_ms))
    for r in e.obs["defasagem"]:
        if r.get("teto_ms") != teto_ms:
            raise ErroUso("%s: rodada %r com teto_ms %r != hotrod_timeout_ms %d" % (e.nome, r.get("rodada"),
                                                                                    r.get("teto_ms"), teto_ms))


# ============================================================================= janelas

def indice_consumo(t, origem, w_ns, n_jan):
    """Janela que consome um instante (A-15): t < fim_j e nao consumido; antes da origem, a primeira; None depois."""
    j = 0 if t < origem else (t - origem) // w_ns
    return j if j < n_jan else None


def janelas_execucao(e, w_ns, p99):
    """Uma lista de janelas [origem + jW, origem + (j+1)W) completas ate t_fim_carga_ns, com os sinais brutos."""
    origem = e.origem
    n_jan = max(0, (e.t_fim_carga - origem) // w_ns)
    jan = [{"t_ini_ns": origem + j * w_ns, "t_fim_ns": origem + (j + 1) * w_ns, "elegiveis": 0, "excedencias": 0,
            "total": 0, "erros": 0, "violacoes": 0, "_top": [], "_lag": []} for j in range(n_jan)]
    for st, en, op, rc in e.eventos:                       # AgregadorDeJanela: janela do end_ns
        if en < origem:
            continue
        j = (en - origem) // w_ns
        if j >= n_jan:
            continue
        x = jan[j]
        x["total"] += 1
        if rc in ERROS:
            x["erros"] += 1
        if p99 is not None and op in p99:
            x["elegiveis"] += 1
            if en - st > p99[op]:
                x["excedencias"] += 1
    for t in e.viol:
        if origem <= t < origem + n_jan * w_ns:
            jan[(t - origem) // w_ns]["violacoes"] += 1
    for a in e.obs["topologia"]:
        j = indice_consumo(a["t_ns"], origem, w_ns, n_jan)
        if j is not None:
            jan[j]["_top"].append(a)
    for r in e.obs["defasagem"]:
        j = indice_consumo(r["t_inicio_ns"], origem, w_ns, n_jan)
        if j is not None:
            jan[j]["_lag"].append(r)
    nos = e.obs["nos"]
    for x in jan:
        x["taxa_lat"] = _taxa(x["excedencias"], x["elegiveis"], N_LAT)
        x["taxa_err"] = _taxa(x["erros"], x["total"], N_LAT)       # n_err = n_lat (7.9)
        x.update(estado_topologia(x.pop("_top"), nos))
        rod = x.pop("_lag")
        x["lag_rodadas"] = len(rod)
        x["lag_max"], x["suspeito"] = resumo_lag(rod)
    return jan


def estado_topologia(amostras, nos):
    """EstadoTopologia da janela (SondaTopologia.resumirComNosAte, :383-429; EstadoTopologia.vistaCompleta)."""
    if not amostras:
        return {"top_amostras": 0, "top_anomala": False, "vista_completa": False, "respondeu_em_todas": set(),
                "todos_responderam": False}
    cont = {no: [0, None, None] for no in nos}
    for a in amostras:
        for r in a.get("por_no") or ():
            c = cont.setdefault(r["no"], [0, None, None])
            if r.get("respondeu"):
                n = r["numero_de_nos"]
                c[0] += 1
                c[1] = n if c[1] is None else min(c[1], n)
                c[2] = n if c[2] is None else max(c[2], n)
    k, n_nos = len(amostras), len(cont)
    todas = {no for no, c in cont.items() if c[0] >= k}
    vista = all(c[0] >= k and c[1] == n_nos and c[2] == n_nos for c in cont.values())
    return {"top_amostras": k, "top_anomala": any(a.get("anomala") for a in amostras), "vista_completa": vista,
            "respondeu_em_todas": todas, "todos_responderam": len(todas) == n_nos}


def classe_janela(x, lim):
    """C/I/H como o Controlador (:89-99): alta = algumAlto; calma = sem violacao/topologia e sinais <= out ou
    indeterminados (defasagem indeterminada conta como calma); senao intermediaria."""
    tl, te, lag = x["taxa_lat"], x["taxa_err"], x["lag_max"]
    alta = (x["violacoes"] > 0 or x["top_anomala"] or (tl is not None and tl >= lim["theta_lat_in"])
            or (te is not None and te >= lim["theta_err_in"]) or (lag is not None and lag >= lim["lag_in"]))
    if alta:
        return "H"
    calma = (x["violacoes"] == 0 and not x["top_anomala"] and (tl is None or tl <= lim["theta_lat_out"])
             and (te is None or te <= lim["theta_err_out"]) and (lag is None or lag <= lim["lag_out"]))
    return "C" if calma else "I"


# ============================================================================= Delta_conv e taxa (paridade)

def _int_ou_none(x):
    return x if isinstance(x, int) and not isinstance(x, bool) else None


def delta_conv(resumo):
    """(Delta_conv em s ou None, motivo): regra do comparar_bracos.desfechos_execucao (:1039-1078) para o braco A
    (sem AC-5 aplicado), mais a secao 5: convergencia_estado != 'convergiu' fica fora."""
    if resumo is None:
        return None, "sem resumo de convergencia"
    est = resumo.get("convergencia_estado")
    if est is not None and est != "convergiu":
        return None, "convergencia_estado = %s" % est
    dv = resumo.get("divergencia_persistente")
    t_conv = _int_ou_none(resumo.get("t_convergencia_abs_ns"))
    t_carga = _int_ou_none(resumo.get("t_fim_carga_ns"))
    bruto = resumo.get("t_religamento_ns")
    t_relig = _int_ou_none(bruto)
    if bruto is not None and t_relig is None:
        return None, "t_religamento_ns invalido no resumo: %r" % (bruto,)
    t_origem = t_relig if t_relig is not None else t_carga
    if dv is True:
        return None, "divergencia persistente"
    if t_carga is None:
        return None, "sem t_fim_carga_ns no resumo"
    if t_relig is not None and t_relig < t_carga:
        return None, "t_religamento_ns antes de t_fim_carga_ns"
    if t_conv is None:
        return None, "sem t_convergencia_abs_ns no resumo"
    if t_conv < t_origem:
        return None, "t_convergencia_abs_ns antes da origem"
    return (t_conv - t_origem) / 1e9, None


def violacoes_por_mil(e, w_recovery_ns):
    """(contagem I1 + I4 + I2 REAL, acoes S3, taxa por mil) nas fases durante + recuperacao (como o comparar_bracos)."""
    mk = e.marcadores
    res = e.resumo
    t_c = res.get("t_convergencia_abs_ns")
    conv = None if t_c is None else int(t_c)
    div = bool(res.get("divergencia_persistente", False))
    args = (mk["inicio"], mk["split"], mk["fim"], w_recovery_ns, conv, div)
    sel = _fases.expandir_fases(FASES_Q)

    def dentro(t):
        return _fases.fase_de(t, *args) in sel

    c = sum(1 for t in e.viol if dentro(t))
    n = sum(1 for a in e.acoes if dentro(a[0]))
    return c, n, (1000.0 * c / n if n > 0 else None)


# ============================================================================= derivacao

def _entrada(regra, valor, texto, *, execs=(), celulas=(), unidade=None, dados=None, incerteza=None, motivo=None,
             ambiguidades=()):
    d = {"celulas": list(celulas), "execucoes": [e.nome for e in execs], "n_execucoes": len(execs)}
    d.update(dados or {})
    return {"regra": regra, "valor": valor, "unidade": unidade, "texto": texto, "dados": d, "incerteza": incerteza,
            "motivo": motivo, "ambiguidades": list(ambiguidades)}


def _de(execs, celulas):
    return [e for e in execs if e.celula in celulas]


def _referencia(execs, perfil):
    """(dicionario para o JSON, bytes do arquivo referencia-p99/1 ou None, motivo)."""
    if not execs:
        return None, None, "nenhuma execucao sem falha %s aceita" % perfil.upper()
    try:
        ref = referencia_p99.gerar_referencia([e.arq("rep-001.csv") for e in execs], "S3", perfil, N_MIN_REF)
    except referencia_p99.ErroUso as x:
        return None, None, str(x)
    dados = (json.dumps(ref, indent=1, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8")
    return ({"perfil": perfil, "p99_ns": ref["p99_ns"], "n": ref["n"], "omitidas": ref["omitidas"],
             "sha256": _sha256_bytes(dados), "arquivo": "referencia-S3-%s.json" % perfil}, dados, None)


def _parse_iso_ms(txt):
    m = re.match(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,3}))?Z$", str(txt or ""))
    if not m:
        return None
    return calendar.timegm(time.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S")) * 1000 + int((m.group(2) or "0").ljust(3, "0"))


def derivar(campanha, parametros):
    """Dicionario do esquema calibracao-s3/1 (ErroUso em contrato violado)."""
    campanha = Path(campanha)
    par = ler_parametros(parametros)
    teto_ms = par["hotrod_timeout_ms"]
    usadas, excluidas = ler_execucoes(campanha)
    for e in usadas:
        conferir_execucao(e, teto_ms)
    P = {}

    # ---- 7.1 referencias de p99 por perfil
    refs = {}
    for perf, cels in (("async", CEL_SEM_FALHA_ASYNC), ("sync", CEL_SEM_FALHA_SYNC)):
        ex = _de(usadas, cels)
        v, b, mot = _referencia(ex, perf)
        refs[perf] = (v, b)
        P["referencia_p99_%s" % perf] = _entrada(
            "7.1", v, "referencia_p99.py --cenario S3 --perfil %s --n-min 1000 sobre os CSV das execucoes sem falha "
            "do perfil (todos os eventos de cada operacao, D2 de 27/10)" % perf, execs=ex, celulas=cels,
            incerteza=None if v is None else {"n_por_operacao": v["n"]}, motivo=mot)
    p99_async = refs["async"][0]["p99_ns"] if refs["async"][0] else None
    p99_sync = refs["sync"][0]["p99_ns"] if refs["sync"][0] else None

    # ---- 7.2 lambda5
    sf = _de(usadas, CEL_SEM_FALHA_ASYNC)
    lambdas = []
    if p99_async is not None:
        w5 = JANELA_LAMBDA_S * S
        for e in sf:
            n5 = max(0, (e.t_fim_carga - e.origem) // w5)
            c = [0] * n5
            for _st, en, op, _rc in e.eventos:
                if en >= e.origem and op in p99_async:
                    j = (en - e.origem) // w5
                    if j < n5:
                        c[j] += 1
            lambdas += [Fraction(x, JANELA_LAMBDA_S) for x in c]
    lam5 = percentil(lambdas, QUANTIL_LAMBDA) if lambdas else None
    P["lambda5_por_s"] = _entrada(
        "7.2", _f(lam5), "quantil de 5 % do numero de eventos elegiveis a latencia por segundo, em janelas de 5 s da "
        "fase de medicao", execs=sf, celulas=CEL_SEM_FALHA_ASYNC, unidade="eventos/s",
        dados={"N": len(lambdas), "menor": _f(min(lambdas)) if lambdas else None,
               "maior": _f(max(lambdas)) if lambdas else None},
        incerteza={"N": len(lambdas), "posto": None if not lambdas else _ceil_fr(Fraction(5, 100) * len(lambdas))},
        motivo=None if lam5 is not None else "sem referencia ASYNC (7.1) ou sem janela de 5 s",
        ambiguidades=["A-8", "A-14"])

    # ---- 7.3 W
    atrasos = []
    for e in usadas:
        per = e.obs["meta"]["parametros_defasagem"]["periodo_ns"]
        for r in e.obs["defasagem"]:
            t = r["t_inicio_ns"]
            atrasos.append((t - (e.origem + ((t - e.origem) // per) * per), e.nome))
    atraso_max = max(atrasos)[0] if atrasos else None
    cw = calcular_w(lam5, atraso_max, teto_ms)
    w_s = cw["valor"]
    w_ns = None if w_s is None else w_s * S
    P["W_s"] = _entrada(
        "7.3", w_s, "W = max(ceil(n_lat/lambda5), ceil(tetoLag + atraso_max)), n_lat = 100, atraso = t_inicio_ns - "
        "inicio da janela nas linhas defasagem", execs=usadas, celulas=sorted({e.celula for e in usadas}),
        unidade="s", dados={"termo_vazao_s": cw["termo_vazao_s"], "termo_leitura_s": cw["termo_leitura_s"],
                            "atraso_max_ns": atraso_max, "atraso_max_execucao": max(atrasos)[1] if atrasos else None,
                            "rodadas": len(atrasos), "teto_lag_ms": teto_ms, "n_lat": N_LAT,
                            "aceito_pelo_codigo": cw["aceito_pelo_codigo"]},
        incerteza={"lambda5_N": len(lambdas), "rodadas_defasagem": len(atrasos)}, motivo=cw["motivo"],
        ambiguidades=["A-6", "A-17"])

    # ---- janelas W de cada execucao (7.5 em diante)
    jan = {}
    if w_ns is not None:
        for e in usadas:
            jan[e.nome] = janelas_execucao(e, w_ns, p99_async if e.modo == "async" else p99_sync)
    sem_w = "depende de W (7.3), nao derivado"

    # ---- 7.5 excedencia por janela
    P["excedencia_por_janela"] = _entrada(
        "7.5", None if w_ns is None else {"execucoes": [
            {"dir": e.nome, "celula": e.celula, "perfil": e.modo,
             "janelas": [{k: x[k] for k in ("t_ini_ns", "elegiveis", "excedencias", "total", "erros", "taxa_lat",
                                            "taxa_err")} for x in jan[e.nome]]} for e in usadas]},
        "recalculada do CSV de eventos com a referencia 7.1 (do perfil da execucao) e a janela 7.3",
        execs=usadas, celulas=sorted({e.celula for e in usadas}), motivo=None if w_ns else sem_w,
        ambiguidades=["A-8"])

    # ---- 7.6 k e 7.9 n_err
    P["k"] = _entrada("7.6", K_NUCLEO, "k = 2 (derivacao binomial do plano de 22/10 §3.2); theta derivado para esse k")
    P["n_err"] = _entrada("7.9", N_LAT, "n_err = n_lat (100): com W escolhido para ter >= n_lat eventos por janela, "
                                         "o sinal de erro fica determinado onde o de latencia fica")

    # ---- 7.7 theta_in
    def serie(execs, campo):
        return [[x[campo] for x in jan[e.nome]] for e in execs] if w_ns is not None else []

    lim = {}
    for nome, campo in (("theta_lat_in", "taxa_lat"), ("theta_err_in", "taxa_err")):
        r = limiar_entrada(serie(sf, campo), K_NUCLEO) if w_ns is not None else \
            {"valor": None, "maximo_dos_minimos": None, "N": 0, "ocorrencias": None, "motivo": sem_w}
        lim[nome] = r["valor"]
        P[nome] = _entrada(
            "7.7", r["valor"], "menor valor tal que 'k janelas seguidas >= theta_in' tem zero ocorrencias nas janelas "
            "das execucoes ASYNC sem falha (C-8)", execs=sf, celulas=CEL_SEM_FALHA_ASYNC,
            dados={"maximo_dos_minimos": r["maximo_dos_minimos"], "ocorrencias": r["ocorrencias"], "k": K_NUCLEO,
                   "n_janelas_total": sum(len(s) for s in serie(sf, campo))},
            incerteza=incerteza_zero(r["N"], w_s), motivo=r["motivo"], ambiguidades=["A-2", "A-7", "A-8"])

    # ---- 7.11 lag_in
    amostras_lag = [float(r["lag_max_ms"]) for e in sf for r in e.obs["defasagem"]]
    lag_in = percentil(amostras_lag, QUANTIL_LAG_IN) if amostras_lag else None
    lim["lag_in"] = lag_in
    P["lag_in_ms"] = _entrada(
        "7.11", lag_in, "quantil de 99 % das amostras de defasagem (lag_max_ms de cada rodada) das execucoes ASYNC "
        "sem falha", execs=sf, celulas=CEL_SEM_FALHA_ASYNC, unidade="ms",
        dados={"N": len(amostras_lag), "teto_ms": teto_ms},
        incerteza={"N": len(amostras_lag),
                   "amostras_acima_ou_no_limiar": None if lag_in is None else sum(1 for v in amostras_lag if v >= lag_in)},
        motivo=None if lag_in is not None else "nenhuma rodada de defasagem nas execucoes ASYNC sem falha",
        ambiguidades=["A-14"])

    # ---- 7.8 m, theta_out (literal), T_cooldown; 7.12 lag_out
    for nome_in, nome_out in (("theta_lat_in", "theta_lat_out"), ("theta_err_in", "theta_err_out")):
        lim[nome_out] = None if lim[nome_in] is None else math.nextafter(lim[nome_in], -math.inf)
    lim["lag_out"] = None if lag_in is None else math.nextafter(lag_in, -math.inf)
    fm = _de(usadas, CEL_M)
    seqs, motivo_m = [], None
    if w_ns is None or any(v is None for v in lim.values()):
        motivo_m = "depende de W, theta_in, theta_out, lag_in e lag_out (algum nao derivado)"
        rm = {"valor": None, "maior_trecho": None, "trechos_seguidos_de_alta": None, "N": 0}
    else:
        for e in fm:
            t_end = e.t_end
            seqs.append([classe_janela(x, lim) for x in jan[e.nome] if x["t_ini_ns"] >= t_end])
        rm = m_reentrada(seqs)
        motivo_m = rm["motivo"]
    m = rm["valor"]
    P["m"] = _entrada(
        "7.8", m, "m = 1 + o maior numero de janelas calmas seguidas que foi seguido de nova janela alta depois do "
        "marcador end, nas execucoes com falha", execs=fm, celulas=CEL_M,
        dados={"maior_trecho": rm["maior_trecho"], "trechos_seguidos_de_alta": rm["trechos_seguidos_de_alta"],
               "limiares_usados": {k: lim[k] for k in sorted(lim)},
               "sequencias": [{"dir": e.nome, "classes": "".join(s)} for e, s in zip(fm, seqs)]},
        incerteza={"N": rm["N"]}, motivo=motivo_m, ambiguidades=["A-4", "A-5", "A-6", "A-15"])
    P["T_cooldown_s"] = _entrada("7.8", None if m is None or w_s is None else m * w_s, "T_cooldown = m W",
                                 unidade="s", motivo=None if m is not None and w_s is not None else "depende de m e W")
    for nome_out, nome_in, campo, regra, texto in (
            ("theta_lat_out", "theta_lat_in", "taxa_lat", "7.8", "theta_out = maior valor tal que nenhuma janela das "
             "execucoes sem falha fica entre theta_out e theta_in por mais de m janelas seguidas"),
            ("theta_err_out", "theta_err_in", "taxa_err", "7.8", "idem, sobre a taxa de erro"),
            ("lag_out_ms", "lag_in", None, "7.12", "lag_out: como theta_out (7.8), sobre as amostras de defasagem")):
        if campo is None:
            ser = [[x["lag_max"] for x in jan[e.nome]] for e in sf] if w_ns is not None else []
            lin = lag_in
        else:
            ser, lin = serie(sf, campo), lim[nome_in]
        r = limiar_saida(ser, lin, m)
        P[nome_out] = _entrada(
            regra, r["valor"], texto, execs=sf, celulas=CEL_SEM_FALHA_ASYNC, unidade="ms" if campo is None else None,
            dados={"alternativa_menor_valor": r["alternativa_menor_valor"], "m": m, "limiar_de_entrada": lin,
                   "maior_trecho_na_banda": r["maior_trecho_na_banda"],
                   "maior_trecho_na_banda_alternativa": r["maior_trecho_na_banda_alternativa"]},
            incerteza=incerteza_zero(r["N"], w_s), motivo=r["motivo"],
            ambiguidades=["A-3", "A-4", "A-7"] + (["A-1"] if campo is None else []))

    # ---- 7.10 teto da convergencia
    fc = _de(usadas, CEL_FALHA)
    dl, det, fora = [], [], []
    for e in fc:
        d, mot = delta_conv(e.resumo)
        det.append({"dir": e.nome, "delta_conv_s": d, "motivo": mot})
        if d is None:
            fora.append({"dir": e.nome, "motivo": mot})
        else:
            dl.append((e.nome, d))
    rt = teto_convergencia(dl)
    P["teto_convergencia_s"] = _entrada(
        "7.10", rt["valor"], "600 s se o maior Delta_conv das execucoes que convergiram for < 400 s; senao 1,5 x esse "
        "maior", execs=fc, celulas=CEL_FALHA, unidade="s",
        dados={"maior_delta_conv_s": rt["maior_delta_conv_s"], "n_convergiram": rt["n"], "execucoes": det,
               "fora": fora}, incerteza={"n_convergiram": rt["n"]}, motivo=rt["motivo"],
        ambiguidades=["A-6", "A-16"])

    # ---- 7.13 kLag
    fk = _de(usadas, CEL_KLAG)
    if w_ns is None:
        rk = {"valor": None, "N": 0, "motivo": sem_w, "dentro_do_limite": None, "k_max_no_limite": None,
              "maior_persistencia_contada": None, "ocorrencias_contadas_por_k": None, "sem_resposta": None,
              "n_janelas_total": 0, "por_execucao": []}
    else:
        rk = klag([(e.nome, jan[e.nome]) for e in fk], teto_ms, w_s)
    previstas = []
    for e in fk:
        n = sum(1 for o in e.trilha if o["tipo"] == "acao" and o.get("operacao") == "APLICAR"
                and (o.get("acao") or {}).get("tipo") == "AC-5" and o.get("resultado") == "PREVISTA")
        if n:
            previstas.append({"dir": e.nome, "linhas": n})
    sem_rodada = 0 if w_ns is None else sum(1 for e in fk for x in jan[e.nome] if x["lag_rodadas"] == 0)
    P["kLag"] = _entrada(
        "7.13", rk["valor"], "menor k >= 1 com zero ocorrencias (C-8) do predicado do P1 (vistaCompleta && suspeito "
        "!= null && persistencia >= k && respondeuEmTodas(suspeito), PoliticaPorAssinatura.java:158), nas execucoes "
        "sem particao; limitado por kLag W + 13,3 < 39,8", execs=fk, celulas=CEL_KLAG,
        dados={"maior_persistencia_contada": rk["maior_persistencia_contada"],
               "ocorrencias_contadas_por_k": rk["ocorrencias_contadas_por_k"], "sem_resposta": rk["sem_resposta"],
               "dentro_do_limite": rk["dentro_do_limite"], "k_max_no_limite": rk["k_max_no_limite"],
               "n_janelas_total": rk["n_janelas_total"], "janelas_sem_rodada": sem_rodada, "teto_lag_ms": teto_ms,
               "por_execucao": rk["por_execucao"],
               "perfil_conservador": "nao exigido na contagem (depende de theta e lag_in derivados juntos; ignora-lo "
                                     "so torna a contagem mais conservadora)",
               "conferencia_acao_prevista": {"linhas_ac5_prevista": sum(x["linhas"] for x in previstas),
                                             "execucoes": previstas,
                                             "nota": "linhas acao PREVISTA do braco A gravadas com o kLag provisorio "
                                                     "(2) e o W provisorio: so conferencia"}},
        incerteza=incerteza_zero(rk["N"], w_s), motivo=rk["motivo"], ambiguidades=["A-1", "A-7", "A-8", "A-15"])

    # ---- 7.4 viabilidade (com o kLag da 7.13)
    vb = viabilidade(w_s, rk["valor"])
    P["viabilidade_ac5"] = _entrada(
        "7.4", vb, "se W >= 26,5 s, ou se o menor kLag da 7.13 passar de kLag W + 13,3 < 39,8, o AC-5 e declarado "
        "inviavel antes da avaliacao", dados={"W_s": w_s, "kLag": rk["valor"], "t_parada_s": float(T_PARADA),
                                               "t_det_min_s": float(T_DET_MIN)})

    # ---- 7.14 T_estavel
    te = t_estavel([(e.nome, e.t_end, [(a["t_ns"], bool(a.get("anomala"))) for a in e.obs["topologia"]])
                    for e in fc], P_SONDA_S)
    P["T_estavel_s"] = _entrada(
        "7.14", te["valor"], "maior duracao de um trecho com 3 nos HEALTHY que terminou em nova amostra anomala "
        "depois do marcador end, mais P_sonda (2 s); sem recaida, P_sonda", execs=fc, celulas=CEL_FALHA, unidade="s",
        dados={"maior_trecho_s": te["maior_trecho_s"], "recaidas": te["recaidas"], "p_sonda_s": P_SONDA_S,
               "por_execucao": te["por_execucao"]}, incerteza={"recaidas": te["recaidas"]}, motivo=te["motivo"],
        ambiguidades=["A-6", "A-9", "A-15"])

    # ---- 7.15 T_hold_viol
    th = t_hold_viol([(e.nome, e.viol) for e in usadas], w_s)
    P["T_hold_viol_s"] = _entrada(
        "7.15", th["valor"], "maior intervalo entre duas violacoes seguidas na mesma execucao; sem execucao com duas "
        "violacoes, W", execs=usadas, celulas=sorted({e.celula for e in usadas}), unidade="s",
        dados={"maior_intervalo_s": th["maior_intervalo_s"], "por_execucao": th["por_execucao"]},
        motivo=th["motivo"], ambiguidades=["A-6", "A-10"])

    # ---- 7.16 blocos e T_exec; 7.17 volume e exec_teto
    texec, tpre, tpos, vols = [], [], [], []
    for e in usadas:
        fim_ms = _parse_iso_ms(e.ex.get("fim_utc"))
        rec, lanc, anc = (_int_ou_none(e.ex.get(k)) for k in ("recriacao_ms", "lancamento_ms", "ancora_ms"))
        texec.append({"dir": e.nome, "t_exec_s": None if fim_ms is None or rec is None else (fim_ms - rec) / 1000})
        if lanc is not None and anc is not None:
            tpre.append((Fraction(anc - lanc, 1000), e.nome))
        tc = _int_ou_none(e.resumo.get("t_convergencia_abs_ns"))
        if fim_ms is not None and tc is not None:
            tpos.append((Fraction(fim_ms * MS - tc, S), e.nome))
        vols.append((sum(p.stat().st_size for p in e.dir.rglob("*") if p.is_file()), e.nome))
    vals = [x["t_exec_s"] for x in texec if x["t_exec_s"] is not None]
    P["n_blocos"] = _entrada(
        "7.16", N_BLOCOS, "n = 12, fixo (decisao do Gestor, 05/11, B-40 §7); a calibracao so mede T_exec",
        execs=usadas, dados={"t_exec_s": texec, "t_exec_max_s": max(vals) if vals else None,
                             "t_exec_medio_s": sum(vals) / len(vals) if vals else None},
        ambiguidades=["A-12"])
    vmax = max(vols) if vols else None
    P["volume_exec_mb"] = _entrada(
        "7.17", None if vmax is None else _ceil_fr(Fraction(vmax[0], 2 ** 20)), "maior volume medido", execs=usadas,
        unidade="MiB", dados={"maior_bytes": None if vmax is None else vmax[0],
                              "maior_execucao": None if vmax is None else vmax[1],
                              "maior_mib": None if vmax is None else vmax[0] / 2 ** 20},
        motivo=None if vmax else "nenhuma execucao aceita", ambiguidades=["A-11"])
    pre_max = max(tpre) if tpre else None
    pos_max = max(tpos) if tpos else None
    et = exec_teto(par["duracao_s"], par["religamento_prazo_s"], rt["valor"], w_s,
                   None if pre_max is None else pre_max[0], None if pos_max is None else pos_max[0])
    P["exec_teto_s"] = _entrada(
        "7.17", et["valor"], "formula de 4.1 com o convergencia_periodo_s, o teto da 7.10 e o maior t_pre e t_pos "
        "medidos; nunca abaixo de 1010 s", execs=usadas, unidade="s",
        dados={"formula_s": et["formula_s"], "duracao_s": par["duracao_s"], "t_enc_s": T_ENC_S,
               "religamento_prazo_s": par["religamento_prazo_s"], "teto_convergencia_s": rt["valor"],
               "convergencia_periodo_s": w_s, "t_pre_max_s": None if pre_max is None else float(pre_max[0]),
               "t_pre_execucao": None if pre_max is None else pre_max[1],
               "t_pos_max_s": None if pos_max is None else float(pos_max[0]),
               "t_pos_execucao": None if pos_max is None else pos_max[1], "fator": float(FATOR),
               "minimo_s": EXEC_TETO_MIN_S}, motivo=et["motivo"], ambiguidades=["A-11"])

    # ---- 7.18 q-chapeu e curva de poder (so informativo)
    wr = par["w_recovery_s"] * S
    qh, taxas_cmd = {}, {}
    ft = _de(usadas, CEL_TESTE)
    for cel in CEL_TESTE:
        ex = _de(ft, (cel,))
        lst = []
        for e in ex:
            c, n, tx = violacoes_por_mil(e, wr)
            lst.append({"dir": e.nome, "violacoes": c, "acoes_s3": n, "taxa_violacoes_por_1000": tx})
        if not ex:
            qh[cel] = {"n": 0, "com_violacao": 0, "q_hat": None, "clopper_pearson_95": None, "execucoes": []}
            continue
        k = sum(1 for x in lst if x["violacoes"] > 0)
        qh[cel] = {"n": len(ex), "com_violacao": k, "q_hat": k / len(ex),
                   "clopper_pearson_95": list(clopper_pearson(k, len(ex))), "execucoes": lst}
        taxas_cmd[cel] = [x["taxa_violacoes_por_1000"] for x in lst if x["taxa_violacoes_por_1000"] is not None]
    P["q_hat"] = _entrada(
        "7.18", qh, "q-chapeu = fracao de execucoes aceitas com ao menos uma violacao (I1 + I4 + I2 REAL, fases "
        "durante + recuperacao), com Clopper-Pearson de 95 % (secao 6)", execs=ft, celulas=CEL_TESTE,
        dados={"fases": list(FASES_Q), "w_recovery_s": par["w_recovery_s"]}, incerteza={"metodo": "Clopper-Pearson "
                                                                                                 "bilateral 95 %"},
        ambiguidades=["A-13"])
    P["curva_de_poder"] = _entrada(
        "7.18", {"executado": False,
                 "comando": "python -E -P analysis/poder.py --taxas-a taxas-<celula>.txt --semente <S> --n-sim <N> "
                            "--m-celulas %d --blocos %d --saida poder-<celula>.json" % (M_CELULAS_TESTE, N_BLOCOS),
                 "taxas_a": taxas_cmd},
        "poder.py (W-5) com as taxas do braco A por celula de teste, 12 blocos (fixo), reducoes de 0 a 100 %, m das "
        "celulas de teste do B-40. So informativo", execs=ft, celulas=CEL_TESTE,
        motivo="poder.py importa numpy: fora do python3 -I; rodar o comando com as taxas_a de cada celula",
        ambiguidades=["A-13"])

    # ---- contagens por celula e valores para o B-40 parte 2 (leitura literal)
    por_cel = {c: {"aceitas": sum(1 for e in usadas if e.celula == c), "planejadas": n}
               for c, n in CELULAS_PLANEJADAS.items()}
    v = {k: P[k]["valor"] for k in P}
    b40 = {"leitura": "literal (theta_out e lag_out pela A-3; ver ambiguidades)",
           "wNs": None if w_s is None else w_s * S, "nLat": N_LAT, "nErr": v["n_err"], "k": v["k"], "m": v["m"],
           "thetaLatIn": v["theta_lat_in"], "thetaLatOut": v["theta_lat_out"], "thetaErrIn": v["theta_err_in"],
           "thetaErrOut": v["theta_err_out"],
           "cooldownNs": None if v["T_cooldown_s"] is None else v["T_cooldown_s"] * S,
           "estavelNs": None if v["T_estavel_s"] is None else int(round(_fr(v["T_estavel_s"]) * S)),
           "holdViolNs": None if v["T_hold_viol_s"] is None else int(round(_fr(v["T_hold_viol_s"]) * S)),
           "politica.kLag": v["kLag"], "defasagem.lagInMs": v["lag_in_ms"], "defasagem.lagOutMs": v["lag_out_ms"],
           "ac5_viavel": vb["viavel"], "convergencia_teto_s": v["teto_convergencia_s"],
           "convergencia_periodo_s": w_s, "exec_teto_s": v["exec_teto_s"], "volume_exec_mb": v["volume_exec_mb"],
           "referencia_async_sha256": (v["referencia_p99_async"] or {}).get("sha256"),
           "referencia_sync_sha256": (v["referencia_p99_sync"] or {}).get("sha256")}
    regras = {}
    for nome, x in P.items():
        regras.setdefault(x["regra"], []).append(nome)
    script = Path(__file__).resolve()
    return {"esquema": ESQUEMA, "pre_registro": PRE_REGISTRO,
            "entradas": {"campanha": campanha.name, "parametros": {"arquivo": Path(parametros).name,
                                                                    "sha256": _sha256(parametros)},
                         "valores_da_campanha": par},
            "execucoes": {"usadas": [e.publico() for e in usadas], "excluidas": excluidas, "por_celula": por_cel},
            "constantes": {"n_lat": N_LAT, "k": K_NUCLEO, "n_min_referencia": N_MIN_REF,
                           "janela_lambda_s": JANELA_LAMBDA_S, "quantil_lambda": QUANTIL_LAMBDA,
                           "quantil_lag_in": QUANTIL_LAG_IN, "D_s": D_S, "alfa": float(ALFA),
                           "t_parada_s": float(T_PARADA), "t_det_min_s": float(T_DET_MIN), "p_sonda_s": P_SONDA_S,
                           "teto_conv_base_s": TETO_CONV_BASE_S, "fator": float(FATOR), "t_enc_s": T_ENC_S,
                           "exec_teto_min_s": EXEC_TETO_MIN_S, "n_blocos": N_BLOCOS},
            "parametros": P, "regras": {k: sorted(x) for k, x in sorted(regras.items())},
            "valores_b40_parte2": b40, "ambiguidades": AMBIGUIDADES,
            "_referencias_bytes": {k: b for k, (_v, b) in refs.items()},
            "gerado_por": {"script": "analysis/calibracao_s3.py", "sha256": _sha256(script)}}


def _limpo(o):
    """Para o JSON: sets viram listas ordenadas, Fractions viram float."""
    if isinstance(o, dict):
        return {k: _limpo(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [_limpo(v) for v in o]
    if isinstance(o, (set, frozenset)):
        return sorted(o)
    if isinstance(o, Fraction):
        return float(o)
    return o


def serializar(r):
    r = dict(r)
    r.pop("_referencias_bytes", None)
    return (json.dumps(_limpo(r), indent=1, sort_keys=True, ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Derivacao da calibracao S3 (W-9; pre-registro, secao 7).")
    ap.add_argument("--campanha", required=True)
    ap.add_argument("--parametros", required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--referencias-dir")
    a = ap.parse_args(argv)
    try:
        saida = Path(a.saida)
        if saida.exists():
            raise ErroUso("saida ja existe (nunca sobrescreve): %s" % saida)
        r = derivar(a.campanha, a.parametros)
        dados = serializar(r)
        refs = []
        if a.referencias_dir:
            rd = Path(a.referencias_dir)
            for perf, b in sorted(r["_referencias_bytes"].items()):
                if b is None:
                    continue
                f = rd / ("referencia-S3-%s.json" % perf)
                if f.exists():
                    raise ErroUso("referencia ja existe (nunca sobrescreve): %s" % f)
                refs.append((f, b))
            rd.mkdir(parents=True, exist_ok=True)
        with open(saida, "xb") as fh:
            fh.write(dados)
        for f, b in refs:
            with open(f, "xb") as fh:
                fh.write(b)
    except (ErroUso, FileExistsError) as e:
        print("ERRO: %s" % e, file=sys.stderr)
        return 2
    P = r["parametros"]
    print("derivacao gravada em %s: W = %s s, kLag = %s, AC-5 viavel = %s, %d execucoes usadas"
          % (saida, P["W_s"]["valor"], P["kLag"]["valor"], P["viabilidade_ac5"]["valor"]["viavel"],
             len(r["execucoes"]["usadas"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
