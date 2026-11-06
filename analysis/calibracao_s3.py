"""Derivacao da calibracao S3 (W-9): regras 7.1 a 7.18 do pre-registro da calibracao, so braco A.

Fonte das regras: docs/pre-registro-calibracao-s3-2026-11-05.md (branch docs/pre-registro-calibracao-s3, DT-020),
secao 7, com o complemento 1 (docs/pre-registro-calibracao-s3-complemento-1-2026-11-05.md, DT-022, ponta 744e6e8),
que prevalece onde divergem, e as decisoes do Gestor de 05/11 transmitidas pelo coordenador:
  - criterio de falso acionamento C-8: zero ocorrencias nas janelas das execucoes sem falha; ao lado de cada limiar,
    N e o limite superior de 95 % 3/N (com o exato 1 - 0,05^(1/N) ao lado, o mesmo de comparar_bracos.regra_de_tres);
  - regra 7.13 (kLag): conta so as janelas em que vale o predicado da politica P1,
    top.vistaCompleta() && suspeito != null && persistencia >= kLag && top.respondeuEmTodas(suspeito)
    (workload/.../controle/PoliticaPorAssinatura.java:158); a assinatura sem resposta REST (F1, no morto) e contada a
    parte e nao entra no criterio;
  - regra de reserva de W (complemento 1 §4.3): se kLag_min(W) W + 13,3 >= 39,8, W passa ao maior inteiro viavel
    entre W_min e W - 1, com a perda de cobertura do sinal de latencia declarada; sem W viavel, o AC-5 e inviavel
    (7.4);
  - nenhuma regra olha diferenca entre bracos nem efeito do controlador: execucao de braco B ou em active recusa.

Ordem de calculo (complemento 1 §1): referencias de p99 (7.1, que so definem as operacoes elegiveis do lambda5) ->
lambda5 e W (7.2, 7.3) -> kLag e reserva de W (7.13, 7.4) -> excedencia (7.5) -> theta_in e lag_in (7.7, 7.11) -> m
(7.8, sem theta_out) -> theta_out e lag_out (7.8, 7.12) -> T_cooldown, T_estavel, T_hold_viol, teto, volume,
exec_teto, T_exec e q-chapeu. Todas as regras usam a grade do W final.

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
usados (celulas, execucoes, N) e a incerteza; as leituras A-1..A-17, com a situacao de cada uma no complemento 1, vao
em "leituras". Nunca sobrescreve. Com --referencias-dir grava referencia-S3-async.json e referencia-S3-sync.json
(esquema referencia-p99/1, os mesmos bytes do referencia_p99.gravar_json) com o SHA-256 que esta no JSON.
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
PRE_REGISTRO = ("docs/pre-registro-calibracao-s3-2026-11-05.md, secao 7 (DT-020), com o complemento 1 "
                "(docs/pre-registro-calibracao-s3-complemento-1-2026-11-05.md, DT-022, 744e6e8), o complemento 3 "
                "(docs/pre-registro-calibracao-s3-complemento-3-2026-11-06.md, DT-024, 45e7800) e o 3a "
                "(docs/pre-registro-calibracao-s3-complemento-3a-2026-11-06.md, 639966c)")
C1 = "complemento 1 (DT-022)"
S = 1_000_000_000
MS = 1_000_000

# ------------------------------------------------------------------ constantes (cada uma com a origem)
N_LAT = 100                    # 7.3 e 7.9: n_lat = 100 (plano de 22/10 §3.2); n_err = n_lat (C-9)
K_NUCLEO = 2                   # 7.6: k = 2 (derivacao binomial do plano de 22/10 §3.2; C-9)
N_MIN_REF = 1000               # 7.1: --n-min 1000 (decisao D2 de 27/10)
JANELA_LAMBDA_S = 5            # 7.2: janelas de 5 s da fase de medicao
QUANTIL_LAMBDA = 0.05          # 7.2: quantil de 5 %
QUANTIL_LAG_IN = 0.99          # 7.11: quantil de 99 % (plano de 04/11 §4.6)
COBERTURA_MAX = 0.05           # complemento 1 §4.3: fracao de janelas sem falha com latencia indeterminada (95 % de 7.2)
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
SEMENTE_PODER = 20261105       # complemento 1 §5 (A-13): a mesma regra da --ordem-seed
# R-2 da V-W9 (complemento 3 §2, DT-024, com o 3a): sinal de latencia (ou erro) "sem cobertura" no W final: theta_in =
# o menor double acima de 1 (complemento 3 §2 e 3a §3; inalcancavel, a taxa e <= 1; mesma convencao do A-2) e
# theta_out = 1
THETA_SEM_COBERTURA = math.nextafter(1.0, math.inf)
THETA_OUT_SEM_COBERTURA = 1.0
N_SIM_PODER = 10000            # complemento 1 §5 (A-13): as 10 000 reamostragens da analise do rebaseline (DT-009)

# secao 3: celulas e tamanhos planejados (26 execucoes)
CELULAS_PLANEJADAS = {"ASYNC-none": 5, "ASYNC-F1": 5, "ASYNC-F2": 5, "SYNC-F2": 3, "SYNC-none": 3, "ASYNC-F4": 5}
CEL_SEM_FALHA_ASYNC = ("ASYNC-none",)
CEL_SEM_FALHA_SYNC = ("SYNC-none",)
CEL_M = ("ASYNC-F1", "ASYNC-F2", "ASYNC-F4")                  # 7.8 (m): so ASYNC (A-6, complemento 1 §3)
CEL_FALHA = ("ASYNC-F1", "ASYNC-F2", "SYNC-F2", "ASYNC-F4")    # 7.10 e 7.14: todas as aceitas com falha (A-6)
CEL_KLAG = ("ASYNC-none", "ASYNC-F1")                          # 7.13: ASYNC sem falha e ASYNC F1
CEL_TESTE = ("ASYNC-F2", "ASYNC-F4")                           # 7.18: ASYNC F2 e F4 (m = 2)

ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")    # EventoOperacao.erro() (os mesmos de comparar_bracos)
ESQ_OBS = "observaveis-controlador/1.2"
ESQ_DEC = "decisoes-controlador/"


def _l(i, regras, situacao, leitura):
    return {"id": i, "regras": regras, "situacao": situacao, "leitura": leitura}


LEITURAS = [
    _l("A-1", ["7.3", "7.8", "7.12", "7.13"], "emendada pelo %s, secao 4.1" % C1,
       "todas as regras usam a grade do W final, alinhada em origem_ns; defasagem da janela = SondaDefasagem.resumir "
       "das rodadas que comecam nela; janela sem rodada e pulada (nao quebra nem soma persistencia e trechos) no m, "
       "no lag_out e no kLag; contagem em dados.janelas_sem_rodada"),
    _l("A-2", ["7.7"], "confirmada pelo %s, secao 5" % C1,
       "theta_in = menor double acima do maior minimo de k janelas seguidas (math.nextafter); com zero erro sem "
       "falha, theta_err_in = 5e-324 e qualquer erro em k janelas seguidas e alto"),
    _l("A-3", ["7.8", "7.12"], "emendada pelo %s, secao 2" % C1,
       "theta_out (latencia e erro) e lag_out = menor c em {0} U {valores observados abaixo do limiar de entrada} tal "
       "que, nas ASYNC sem falha, nenhum trecho de janelas seguidas com c < sinal < limiar passa de m; indeterminada "
       "conta como calma e quebra o trecho; igualdade com c e calma"),
    _l("A-4", ["7.8"], "resolvida pelo %s, secoes 1 e 3" % C1,
       "m nao usa theta_out; theta_out e lag_out sao calculados depois de m"),
    _l("A-5", ["7.8"], "emendada pelo %s, secao 3, e pela R-3 da V-W9 (complemento 3, DT-024)" % C1,
       "m = 1 + L, L = maior trecho de janelas sem sinal alto (algumAlto do Controlador) depois do end que termina "
       "imediatamente numa janela alta, nas ASYNC F1, F2 e F4; trechos que chegam ao fim das janelas nao contam; sem "
       "nenhum, m = 1; sem janela depois do end, nao derivado. O trecho que atravessa o end e cortado no end e conta so "
       "a parte depois dele (R-3 da V-W9, ver emendas_vw9)"),
    _l("A-6", ["7.3", "7.8", "7.10", "7.14", "7.15"], "confirmada pelo %s, secao 5" % C1,
       "W, theta, k, m, lag_in, lag_out e kLag so de execucoes ASYNC; atraso maximo (7.3), 7.10, 7.14 e 7.15 de "
       "todas as aceitas, inclusive SYNC"),
    _l("A-7", ["7.7", "7.8", "7.12", "7.13"], "confirmada pelo %s, secao 5" % C1,
       "N = janelas (ou posicoes de k janelas seguidas) em que o criterio pode ocorrer; o total vai ao lado"),
    _l("A-8", ["7.2", "7.3", "7.5", "7.7", "7.11", "7.13"], "confirmada pelo %s, secao 5" % C1,
       "janelas completas de origem_ns ate t_fim_carga_ns; eventos pela janela do end_ns (AgregadorDeJanela); D = 256 s "
       "so no alvo a; a 7.11 e o atraso da 7.3 usam todas as linhas defasagem (so existem depois da origem)"),
    _l("A-9", ["7.14"], "confirmada pelo %s, secao 5" % C1,
       "duracao do trecho saudavel = da primeira a ultima amostra saudavel depois do end, quando a seguinte e anomala; "
       "mais P_sonda"),
    _l("A-10", ["7.15"], "confirmada pelo %s, secao 5" % C1,
       "I1 + I4 + I2 REAL, todas as do violacoes-rep-001.csv da execucao"),
    _l("A-11", ["7.17"], "confirmada pelo %s, secao 5" % C1,
       "periodo = W final; t_pre = ancora_ms - lancamento_ms; t_pos = fim_utc - t_convergencia_abs_ns, so nas que "
       "convergiram (superestima); T_enc = 21 s; volume em MiB arredondado para cima; teto arredondado para cima"),
    _l("A-12", ["7.16"], "confirmada com acrescimo pelo %s, secao 5" % C1,
       "T_exec = fim_utc - recriacao_ms; ao lado, o ciclo completo = intervalo entre recriacao_ms de execucoes "
       "seguidas da mesma fase (inclui pos-checagem e h4; uma retomada aparece como intervalo longo)"),
    _l("A-13", ["7.18"], "confirmada com acrescimo pelo %s, secao 5" % C1,
       "q-chapeu, Clopper-Pearson e taxas aqui; a curva do poder.py roda depois com python -E -P, --semente 20261105, "
       "--n-sim 10000, --m-celulas 2 e --blocos 12; so informativa"),
    _l("A-14", ["7.2", "7.11"], "confirmada pelo %s, secao 5" % C1,
       "quantil por posto mais proximo (referencia_p99.percentil)"),
    _l("A-15", ["7.8", "7.13", "7.14"], "confirmada pelo %s, secao 5" % C1,
       "amostras de topologia e rodadas de defasagem pela regra de consumo do codigo (a janela j leva o que tem "
       "instante < fim_j e ainda nao foi consumido; a primeira leva o aquecimento da topologia)"),
    _l("A-16", ["7.10"], "confirmada com acrescimo pelo %s, secao 5" % C1,
       "Delta_conv do comparar_bracos (desde t_fim_carga_ns no braco A); quem nao tem convergencia_estado = convergiu "
       "fica fora do maximo e e contado em dados.n_fora"),
    _l("A-17", ["7.3"], "corrigida pelo %s, secao 4.2" % C1,
       "W = max(ceil(n_lat/lambda5), W_min), W_min = floor(tetoLag + atraso_max) + 1 (estrito: prazo < W e "
       "atraso <= W - teto)"),
]

# Ajustes da V-W9 (docs/validacao-w9-2026-11-05.md, 8d6ebfc), aprovados pelo coordenador (W-9b); o Senior escreve o
# complemento 3 (DT-024) com o mesmo conteudo
EMENDAS_VW9 = [
    {"id": "R-1", "regras": ["7.4", "7.13", "C1 §4.3"], "situacao": "complemento 3 (DT-024, 45e7800), secao 1",
     "leitura": "viavel em W' = max(k, kLag_min(W')) W' + 13,3 < 39,8, k = 2 (o P1 so age em CONSERVADOR, que pede k "
                "janelas de lag ALTA)"},
    {"id": "R-2", "regras": ["7.7", "7.8", "C1 §4.3"],
     "situacao": "complemento 3 (DT-024, 45e7800), secao 2, com o 3a",
     "leitura": "sinal de latencia (ou erro) sem cobertura sempre que a 7.7 tiver N = 0 nas ASYNC sem falha no W final "
                "(nenhuma posicao de k janelas determinadas seguidas, inclusive sem janela determinada): theta_in = o "
                "menor double acima de 1 (%r) e theta_out = %s; m e T_cooldown derivam com eles"
                % (THETA_SEM_COBERTURA, THETA_OUT_SEM_COBERTURA)},
    {"id": "R-3", "regras": ["7.8", "C1 §3"], "situacao": "complemento 3 (DT-024, 45e7800), secao 3",
     "leitura": "no m, o trecho sem alto que atravessa o end e cortado no end e conta so a parte depois dele"},
    {"id": "R-4", "regras": ["7.8", "7.12", "7.13", "C1 §4.1"], "situacao": "complemento 3 (DT-024, 45e7800), secao 4",
     "leitura": "com W final abaixo de 5 s (periodo da sonda na calibracao), pular as janelas sem rodada subestima kLag, "
                "m e o trecho do lag_out em cerca de 5/W; a regra de pular fica e a subestimacao e declarada em "
                "W_s.dados.subestimacao_w_menor_que_5_s"},
    {"id": "3a", "regras": ["7.7", "C3 §2"], "situacao": "complemento 3a (nota a DT-024, 639966c)",
     "leitura": "a condicao da R-2 e N = 0 na 7.7, o que inclui janelas determinadas sem nenhum par seguido; o JSON da "
                "o numero e a fracao de janelas determinadas; theta_in sem cobertura = o menor double acima de 1"},
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
    """7.3 com o complemento 1 §4.2: W = max(ceil(n_lat / lambda5), W_min), W_min = floor(tetoLag + atraso_max) + 1.

    W_min e o menor inteiro estritamente acima de tetoLag + atraso_max: cumpre prazo < W (ParametrosDefasagem) e
    atraso <= W - teto (SondaDefasagem.cabeNaJanela).
    """
    if lambda5 is None or Fraction(lambda5) <= 0:
        return {"valor": None, "termo_vazao_s": None, "w_min_s": None, "aceito_pelo_codigo": None,
                "motivo": "lambda5 ausente ou zero (7.2)"}
    if atraso_max_ns is None:
        return {"valor": None, "termo_vazao_s": None, "w_min_s": None, "aceito_pelo_codigo": None,
                "motivo": "sem linha defasagem: atraso maximo de inicio desconhecido"}
    tv = _ceil_fr(Fraction(n_lat) / Fraction(lambda5))
    w_min = (int(teto_ms) * MS + int(atraso_max_ns)) // S + 1
    w = max(tv, w_min)
    return {"valor": w, "termo_vazao_s": tv, "w_min_s": w_min,
            "aceito_pelo_codigo": int(teto_ms) * MS < w * S and int(atraso_max_ns) <= w * S - int(teto_ms) * MS,
            "motivo": None}


def _cabe(k, w_s):
    """kLag*W + t_parada < T_det (7.4/7.13), aritmetica exata."""
    return k * Fraction(w_s) + T_PARADA < T_DET_MIN


def viavel_em(klag_valor, w_s):
    """R-1 da V-W9 (aprovada pelo coordenador; complemento 3, DT-024): o P1 so age em CONSERVADOR
    (PoliticaPorAssinatura.java:152), e no cenario do P1 o perfil so chega la depois de k = 2 janelas de lag ALTA (R2
    do Controlador). O AC-5 age no fim da janela max(k, kLag): viavel = max(K_NUCLEO, kLag) W + 13,3 < 39,8."""
    return _cabe(max(K_NUCLEO, klag_valor), w_s)


def reserva_w(w_s, w_min_s, klag_min):
    """Regra de reserva de W (complemento 1 §4.3).

    klag_min(W') = menor k >= 1 com zero ocorrencias da 7.13 na grade W' (None se nao derivavel). Viavel em W' =
    max(k, klag_min(W')) W' + 13,3 < 39,8 (viavel_em, R-1 da V-W9). Tenta W, W - 1, ..., W_min e para no primeiro
    viavel (o maior); sem nenhum, o AC-5
    e inviavel e W e kLag ficam os da 7.3. Devolve W_s, kLag, viavel (None se kLag nao derivavel em W),
    reserva_aplicada, candidatos e motivos.
    """
    cands = []
    k0 = klag_min(w_s)
    if k0 is None:
        return {"W_s": w_s, "kLag": None, "viavel": None, "reserva_aplicada": False,
                "candidatos": [{"W_s": w_s, "kLag_min": None, "viavel": None}],
                "motivos": ["kLag nao derivado em W = %s s (7.13): viabilidade nao avaliavel" % w_s]}
    for w in range(w_s, w_min_s - 1, -1):
        k = k0 if w == w_s else klag_min(w)
        ok = k is not None and viavel_em(k, w)
        cands.append({"W_s": w, "kLag_min": k, "viavel": ok})
        if ok:
            motivos = [] if w == w_s else [
                "reserva de W (complemento 1 §4.3, R-1 da V-W9): max(k, kLag) = %d em W = %d nao cabe (%s s >= 39,8 s); "
                "W = %d, kLag = %d" % (max(K_NUCLEO, k0), w_s, float(max(K_NUCLEO, k0) * Fraction(w_s) + T_PARADA), w, k)]
            return {"W_s": w, "kLag": k, "viavel": True, "reserva_aplicada": w != w_s, "candidatos": cands,
                    "motivos": motivos}
    return {"W_s": w_s, "kLag": k0, "viavel": False, "reserva_aplicada": False, "candidatos": cands,
            "motivos": ["7.4 (complemento 1 §4.3, R-1 da V-W9): nenhum W' em [%d, %d] com max(k, kLag_min(W')) W' + "
                        "13,3 < 39,8: AC-5 "
                        "inviavel antes da avaliacao (%s)"
                        % (w_min_s, w_s, "; ".join("W'=%d kLag=%s" % (c["W_s"], c["kLag_min"]) for c in cands))]}


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


def limiar_entrada_com_cobertura(series, k):
    """7.7 com a R-2 (complemento 3 §2) e o complemento 3a §2: se ha janelas mas a 7.7 tem N = 0 (nenhuma posicao de
    k janelas determinadas seguidas, inclusive sem nenhuma janela determinada), o sinal fica sem cobertura: theta_in =
    THETA_SEM_COBERTURA. Sem nenhuma janela, nao deriva. Acrescenta sem_cobertura, janelas_determinadas,
    n_janelas_total e fracao_determinada ao resultado de limiar_entrada.
    """
    n_tot = sum(len(x) for x in series)
    n_det = sum(1 for x in series for t in x if t is not None)
    r = dict(limiar_entrada(series, k))
    sem = n_tot > 0 and r["N"] == 0
    if sem:
        r.update(valor=THETA_SEM_COBERTURA, ocorrencias=0, motivo=None)
    r.update(sem_cobertura=sem, janelas_determinadas=n_det, n_janelas_total=n_tot,
             fracao_determinada=None if n_tot == 0 else n_det / n_tot)
    return r


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
    """7.8/7.12 com o complemento 1 §2: menor c em {0} U {valores observados abaixo do limiar de entrada} tal que
    nenhum trecho de janelas seguidas com c < sinal < limiar_in passa de m janelas.

    Indeterminada (None) conta como calma e quebra o trecho; igualdade com c conta como calma (<= theta_out). Para a
    defasagem, quem chama ja tirou as janelas sem rodada (puladas, complemento 1 §4.1).
    """
    n_tot = sum(len(s) for s in series)
    if limiar_in is None or m is None:
        return {"valor": None, "N": n_tot, "maior_trecho_na_banda": None, "candidatos": None,
                "motivo": "depende do limiar de entrada e de m (nao derivados)"}
    cands = sorted({0.0} | {v for s in series for v in s if v is not None and v < limiar_in})
    for c in cands:
        t = _maior_trecho_na_banda(series, c, limiar_in)
        if t <= m:
            return {"valor": c, "N": n_tot, "maior_trecho_na_banda": t, "candidatos": len(cands), "motivo": None}
    return {"valor": None, "N": n_tot, "maior_trecho_na_banda": None, "candidatos": len(cands),
            "motivo": "nenhum candidato cumpre a condicao"}    # inalcancavel: o maior candidato deixa a banda vazia


def m_reentrada(execucoes):
    """7.8 com o complemento 1 §3: m = 1 + L.

    execucoes: [(classes, inicio)], classes = "H" (algum sinal alto) ou outra letra (sem alto) para cada janela da
    execucao, sem as janelas puladas; inicio = indice da primeira janela com inicio em ou depois do end. L = maior
    trecho maximo sem alto, contado nas janelas depois do end, que termina numa janela alta (o que chega ao fim nao
    conta). R-3 da V-W9: o trecho que atravessa o end e cortado no end e conta so a parte depois dele (como a 7.14 corta
    o trecho saudavel no end). Sem nenhuma janela depois do end: nao derivado.
    """
    maior, trechos, n, cortados = None, 0, 0, 0
    for classes, inicio in execucoes:
        classes = list(classes)
        n += max(0, len(classes) - inicio)
        i = 0
        while i < len(classes):
            if classes[i] == "H":
                i += 1
                continue
            a = i
            while i < len(classes) and classes[i] != "H":
                i += 1
            if i > inicio and i < len(classes):           # trecho [a, i) seguido da alta em i, com parte depois do end
                c = max(a, inicio)
                cortados += c > a
                trechos += 1
                maior = i - c if maior is None else max(maior, i - c)
    if n == 0:
        return {"valor": None, "maior_trecho": None, "trechos_seguidos_de_alta": 0, "trechos_cortados_no_end": 0,
                "N": 0, "motivo": "nenhuma execucao com janela depois do end nas celulas da regra: nao derivavel"}
    motivo = None
    if maior is None:
        motivo = "nenhum trecho sem alto depois do end terminou em janela alta (sem recaida): m = 1 + 0"
    return {"valor": 1 + (maior or 0), "maior_trecho": maior or 0, "trechos_seguidos_de_alta": trechos,
            "trechos_cortados_no_end": cortados, "N": n, "motivo": motivo}


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
    """Maior kLag >= 0 com max(k, kLag) W + 13,3 < 39,8 (0 se nem kLag = 1 cabe)."""
    k = 0
    while viavel_em(k + 1, w_s):
        k += 1
    return k


def klag(execucoes, teto_ms, w_s):
    """7.13: menor k >= 1 com zero ocorrencias do predicado do P1, e a assinatura sem resposta a parte.

    execucoes: [(nome, [janela...])], janela = {"lag_max", "suspeito", "vista_completa", "respondeu_em_todas" (set),
    e opcionais "todos_responderam" e "lag_rodadas"}. Persistencia como PoliticaPorAssinatura.atualizarPersistencia
    (:198-214), zerada abaixo do teto ou sem suspeito; janela sem rodada (lag_rodadas = 0) e pulada: nao quebra nem
    soma persistencia e nao entra em N (complemento 1 §4.1).
    """
    contadas, sem = Counter(), Counter()
    maior_c, maior_s, n_vc, n_tot, n_sem, puladas = 0, 0, 0, 0, 0, 0
    por_exec_sem, por_exec = [], []
    for nome, janelas in execucoes:
        sus, pers = None, 0
        mot, sem_e, cont_e, maior_e = Counter(), 0, 0, 0
        for jn in janelas:
            n_tot += 1
            if jn.get("lag_rodadas", 1) == 0:
                puladas += 1
                continue
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
            "sem_resposta": sem_resposta, "N": n_vc, "n_janelas_total": n_tot, "janelas_puladas": puladas,
            "por_execucao": por_exec, "k_max_no_limite": None if w_s is None else k_max_no_limite(w_s)}
    if n_vc == 0:
        return dict(base, valor=None, dentro_do_limite=None,
                    motivo="nenhuma janela valida (nenhuma janela com vista completa nas execucoes da regra)")
    valor = maior_c + 1
    dentro = None if w_s is None else viavel_em(valor, w_s)
    return dict(base, valor=valor, dentro_do_limite=dentro, motivo=None)


# Guardas que o controlador aplica ao carregar o --controller-config (codigo em 17f2bd4, igual na integracao fdcf0ae):
# ParametrosControlador.java:69-94 (construtor), :174/:182/:190 (inteiro longo, inteiro, numero no deJson) e :211
# (limiar finito e nao negativo); ParametrosPolitica.java:59 e :145; ParametrosDefasagem.java:57-63, :110 e :118
# (validarContra com o prazo de leitura). O teste test_guardas_lidos_do_codigo_java confere que cada trecho existe.
GUARDAS_JAVA = [
    ("ParametrosControlador.java", "if (wNs <= 0) {"),
    ("ParametrosControlador.java", 'naoNegativo("nLat", nLat);'),
    ("ParametrosControlador.java", 'naoNegativo("nErr", nErr);'),
    ("ParametrosControlador.java", "if (!Double.isFinite(v) || v < 0) {"),
    ("ParametrosControlador.java", "if (thetaLatOut >= thetaLatIn) {"),
    ("ParametrosControlador.java", "if (thetaErrOut >= thetaErrIn) {"),
    ("ParametrosControlador.java", "if (k < 1) {"),
    ("ParametrosControlador.java", "if (m < 1) {"),
    ("ParametrosControlador.java", 'naoNegativo("cooldownNs", cooldownNs);'),
    ("ParametrosControlador.java", 'naoNegativo("estavelNs", estavelNs);'),
    ("ParametrosControlador.java", 'naoNegativo("holdViolNs", holdViolNs);'),
    ("ParametrosControlador.java", "if (!n.isIntegralNumber() || !n.canConvertToLong()) {"),
    ("ParametrosControlador.java", "if (!n.isIntegralNumber() || !n.canConvertToInt()) {"),
    ("ParametrosControlador.java", "if (!n.isNumber()) {"),
    ("ParametrosPolitica.java", "if (kLag < 1) {"),
    ("ParametrosPolitica.java", "if (!n.isIntegralNumber() || (cabeEmInt ? !n.canConvertToInt() : !n.canConvertToLong())) {"),
    ("ParametrosDefasagem.java", "if (!Double.isFinite(lagInMs) || lagInMs <= 0) {"),
    ("ParametrosDefasagem.java", "if (!Double.isFinite(lagOutMs) || lagOutMs < 0) {"),
    ("ParametrosDefasagem.java", "if (lagOutMs >= lagInMs) {"),
    ("ParametrosDefasagem.java", "prazoLeituraMs * MS >= wNs"),
    ("ParametrosDefasagem.java", "if (lagInMs > prazoLeituraMs) {"),
]
_INT_MAX = 2 ** 31 - 1
_LONG_MAX = 2 ** 63 - 1


def problemas_de_carga(v, teto_ms):
    """Lista do que impediria o controlador de carregar os valores (vazia = carrega). Espelho de GUARDAS_JAVA.

    v: os valores do B-40 parte 2 (chaves do --controller-config; politica.kLag e defasagem.lagInMs/lagOutMs pelo
    nome com ponto). teto_ms: o --hotrod-timeout-ms (= tetoLagMs, R7), contra o qual a sonda confere W e lagInMs.
    """
    out = []

    def inteiro(campo, maximo, minimo, texto):
        x = v.get(campo)
        if isinstance(x, bool) or not isinstance(x, int) or not -maximo - 1 <= x <= maximo:
            out.append("%s = %r: inteiro exigido (isIntegralNumber)" % (campo, x))
            return None
        if x < minimo:
            out.append("%s = %r: %s" % (campo, x, texto))
        return x

    def real(campo):
        x = v.get(campo)
        if isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) or x < 0:
            out.append("%s = %r: limiar finito e nao negativo exigido" % (campo, x))
            return None
        return float(x)

    w = inteiro("wNs", _LONG_MAX, 1, "wNs deve ser positivo")
    inteiro("nLat", _INT_MAX, 0, "negativo")
    inteiro("nErr", _INT_MAX, 0, "negativo")
    for a, b in (("thetaLatIn", "thetaLatOut"), ("thetaErrIn", "thetaErrOut")):
        ti, to = real(a), real(b)
        if ti is not None and to is not None and to >= ti:
            out.append("%s = %r >= %s = %r: histerese exige out < in" % (b, to, a, ti))
    inteiro("k", _INT_MAX, 1, "k deve ser >= 1")
    inteiro("m", _INT_MAX, 1, "m deve ser >= 1")
    for c in ("cooldownNs", "estavelNs", "holdViolNs"):
        inteiro(c, _LONG_MAX, 0, "negativo")
    inteiro("politica.kLag", _INT_MAX, 1, "kLag deve ser >= 1")
    li, lo = v.get("defasagem.lagInMs"), v.get("defasagem.lagOutMs")
    ok_li = not isinstance(li, bool) and isinstance(li, (int, float)) and math.isfinite(li) and li > 0
    ok_lo = not isinstance(lo, bool) and isinstance(lo, (int, float)) and math.isfinite(lo) and lo >= 0
    if not ok_li:
        out.append("defasagem.lagInMs = %r: finito e > 0 exigido" % (li,))
    if not ok_lo:
        out.append("defasagem.lagOutMs = %r: finito e >= 0 exigido" % (lo,))
    if ok_li and ok_lo and lo >= li:
        out.append("defasagem.lagOutMs = %r >= lagInMs = %r: histerese exige out < in" % (lo, li))
    if ok_li and li > teto_ms:
        out.append("defasagem.lagInMs = %r acima do prazo de leitura %d ms" % (li, teto_ms))
    if w is not None and teto_ms * MS >= w:
        out.append("prazo de leitura %d ms nao cabe em wNs = %d" % (teto_ms, w))
    return out


def ciclo_completo(registros):
    """A-12 com o acrescimo do complemento 1: intervalo entre recriacao_ms de execucoes seguidas da mesma fase.

    registros: [(nome, fase, recriacao_ms ou None)]. Inclui pos-checagem e h4; uma retomada da fase aparece como um
    intervalo longo (a mediana vai ao lado por isso).
    """
    por = {}
    for nome, fase, rec in registros:
        por.setdefault(fase, [])
        if rec is not None:
            por[fase].append((rec, nome))
    out, todos = {}, []
    for fase in sorted(por):
        xs = sorted(por[fase])
        out[fase] = [{"de": a[1], "para": b[1], "s": (b[0] - a[0]) / 1000} for a, b in zip(xs, xs[1:])]
        todos += [x["s"] for x in out[fase]]
    todos.sort()
    med = None
    if todos:
        h = len(todos) // 2
        med = todos[h] if len(todos) % 2 else (todos[h - 1] + todos[h]) / 2
    return {"por_fase": out, "maximo_s": max(todos) if todos else None, "mediana_s": med}


def t_estavel(execucoes, p_sonda_s=P_SONDA_S):
    """7.14: maior trecho com 3 nos HEALTHY depois do end que terminou em amostra anomala, + P_sonda (A-9).

    execucoes: [(nome, t_end_ns, [(t_ns, anomala)])].
    """
    maior, recaidas, por, n_depois = None, 0, [], 0
    for nome, t_end, amostras in execucoes:
        ini = ult = None
        maior_e = None
        depois = sorted(x for x in amostras if x[0] > t_end)
        n_depois += len(depois)
        for t, an in depois:
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
    if n_depois == 0:
        return {"valor": None, "maior_trecho_s": None, "recaidas": 0, "por_execucao": por,
                "motivo": "nenhuma execucao com amostra de topologia depois do end nas celulas da regra: nao derivavel"}
    if maior is None:
        return {"valor": p_sonda_s, "maior_trecho_s": None, "recaidas": 0, "por_execucao": por,
                "motivo": "nenhuma recaida depois do end: T_estavel = P_sonda"}
    return {"valor": maior / S + p_sonda_s, "maior_trecho_s": maior / S, "recaidas": recaidas, "por_execucao": por,
            "motivo": None}


def t_hold_viol(execucoes, w_s):
    """7.15: maior intervalo entre duas violacoes seguidas na mesma execucao; sem execucao com duas, W."""
    if not execucoes:
        return {"valor": None, "maior_intervalo_s": None, "por_execucao": [],
                "motivo": "nenhuma execucao aceita: nao derivavel"}
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
    """(usadas, excluidas, tempos): ACEITA do braco A; as demais e as abortadas (DT-014) so listadas; tempos =
    (nome, fase, recriacao_ms) de toda execucao nao abortada com execucao.json legivel (ciclo completo, A-12)."""
    campanha = Path(campanha)
    if not campanha.is_dir():
        raise ErroUso("campanha ausente: %s" % campanha)
    usadas, excluidas, tempos = [], [], []
    for d in sorted(x for x in campanha.glob("exec-*") if x.is_dir()):
        if ".abortada-" in d.name:
            excluidas.append({"dir": d.name, "veredito": "ABORTADA (DT-014)"})
            continue
        try:
            exj = json.loads((d / "execucao.json").read_text(encoding="utf-8"))
            if isinstance(exj, dict):
                tempos.append((d.name, str(exj.get("fase")), _int_ou_none(exj.get("recriacao_ms"))))
        except (OSError, ValueError):
            pass
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
    return usadas, excluidas, tempos


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


def janelas_execucao(e, w_ns, p99, com_eventos=True):
    """Janelas [origem + jW, origem + (j+1)W) completas ate t_fim_carga_ns, com os sinais brutos (complemento 1 §4.1).

    com_eventos=False pula o CSV de eventos (a contagem do kLag da reserva de W so usa topologia e defasagem).
    """
    origem = e.origem
    n_jan = max(0, (e.t_fim_carga - origem) // w_ns)
    jan = [{"t_ini_ns": origem + j * w_ns, "t_fim_ns": origem + (j + 1) * w_ns, "elegiveis": 0, "excedencias": 0,
            "total": 0, "erros": 0, "violacoes": 0, "_top": [], "_lag": []} for j in range(n_jan)]
    for st, en, op, rc in (e.eventos if com_eventos else ()):      # AgregadorDeJanela: janela do end_ns
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


def alta_janela(x, lim):
    """algumAlto do Controlador (:98): violacao, topologia anomala, latencia >= theta_lat_in, erro >= theta_err_in ou
    defasagem ALTA (lag >= lag_in). Complemento 1 §3: m so usa isto (nao usa theta_out)."""
    tl, te, lag = x["taxa_lat"], x["taxa_err"], x["lag_max"]
    return bool(x["violacoes"] > 0 or x["top_anomala"] or (tl is not None and tl >= lim["theta_lat_in"])
                or (te is not None and te >= lim["theta_err_in"]) or (lag is not None and lag >= lim["lag_in"]))


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
             leituras=()):
    d = {"celulas": list(celulas), "execucoes": [e.nome for e in execs], "n_execucoes": len(execs)}
    d.update(dados or {})
    return {"regra": regra, "valor": valor, "unidade": unidade, "texto": texto, "dados": d, "incerteza": incerteza,
            "motivo": motivo, "leituras": list(leituras)}


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


_KLAG_VAZIO = {"valor": None, "N": 0, "dentro_do_limite": None, "k_max_no_limite": None,
               "maior_persistencia_contada": None, "ocorrencias_contadas_por_k": None, "sem_resposta": None,
               "n_janelas_total": 0, "janelas_puladas": 0, "por_execucao": []}


def derivar(campanha, parametros):
    """Dicionario do esquema calibracao-s3/1 (ErroUso em contrato violado). Ordem do complemento 1 §1."""
    campanha = Path(campanha)
    par = ler_parametros(parametros)
    teto_ms = par["hotrod_timeout_ms"]
    usadas, excluidas, tempos = ler_execucoes(campanha)
    for e in usadas:
        conferir_execucao(e, teto_ms)
    P = {}
    sem_w = "depende de W (7.3), nao derivado"

    # ---- 7.1 referencias de p99 por perfil (definem as operacoes elegiveis do lambda5; nao dependem de W)
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
        leituras=["A-8", "A-14"])

    # ---- 7.3 W pela formula (complemento 1 §4.2)
    atrasos = []
    for e in usadas:
        per = e.obs["meta"]["parametros_defasagem"]["periodo_ns"]
        for r in e.obs["defasagem"]:
            t = r["t_inicio_ns"]
            atrasos.append((t - (e.origem + ((t - e.origem) // per) * per), e.nome))
    atraso_max = max(atrasos)[0] if atrasos else None
    cw = calcular_w(lam5, atraso_max, teto_ms)
    w_formula = cw["valor"]

    # ---- 7.13 kLag e a regra de reserva de W (complemento 1 §4.3)
    fk = _de(usadas, CEL_KLAG)
    cache_k = {}

    def klag_em(w):
        if w not in cache_k:
            jw = [(e.nome, janelas_execucao(e, w * S, None, com_eventos=False)) for e in fk]
            cache_k[w] = klag(jw, teto_ms, w)
        return cache_k[w]["valor"]

    if w_formula is None:
        rw = None
        w_s, rk = None, dict(_KLAG_VAZIO, motivo=sem_w)
    else:
        rw = reserva_w(w_formula, cw["w_min_s"], klag_em)
        w_s = rw["W_s"]
        rk = cache_k[w_s]
    w_ns = None if w_s is None else w_s * S
    reserva = bool(rw and rw["reserva_aplicada"])

    # ---- janelas do W final de cada execucao (todas as regras usam esta grade, complemento 1 §4.1)
    jan = {}
    if w_ns is not None:
        for e in usadas:
            jan[e.nome] = janelas_execucao(e, w_ns, p99_async if e.modo == "async" else p99_sync)
    n_sf = sum(len(jan.get(e.nome, ())) for e in sf)
    n_ind = sum(1 for e in sf for x in jan.get(e.nome, ()) if x["taxa_lat"] is None)
    frac = None if not n_sf else n_ind / n_sf
    cobertura = {"janelas_sem_falha": n_sf, "latencia_indeterminada": n_ind, "fracao_indeterminada": frac,
                 "passa_de_5_por_cento": None if frac is None else frac > COBERTURA_MAX,
                 "declaracao": None if not reserva else (
                     "o sinal de latencia perdeu cobertura para o AC-5 caber no tempo de deteccao: em W = %d s (W da "
                     "7.3 = %d s), %.1f %% das janelas ASYNC sem falha tem menos de n_lat = %d eventos elegiveis "
                     "(latencia indeterminada)" % (w_s, w_formula, 100 * frac if frac is not None else float("nan"),
                                                   N_LAT))}

    # R-4 (complemento 3 §4): com W final abaixo do periodo da sonda na calibracao (5 s), as janelas sem rodada sao
    # puladas e persistencia e trechos ficam contados em rodadas de 5 s: kLag, m e o trecho do lag_out subestimados
    per_cal = max((e.obs["meta"]["parametros_defasagem"]["periodo_ns"] for e in usadas), default=None)
    subest = None
    if w_ns is not None and per_cal is not None and w_ns < per_cal:
        fator = Fraction(per_cal, w_ns)
        subest = {"fator": float(fator), "W_s": w_s, "periodo_sonda_calibracao_s": per_cal / S,
                  "caso": "reserva" if reserva else "7.3", "afeta": ["kLag", "m", "lag_out"],
                  "declaracao": "W final = %d s < %s s (periodo da sonda na calibracao): com as janelas sem rodada "
                                "puladas, persistencia e trechos ficam contados em rodadas de %s s, o que subestima "
                                "kLag, m e o trecho do lag_out em cerca de %.3g vezes (complemento 3 §4, R-4)"
                                % (w_s, per_cal / S, per_cal / S, float(fator))}
    P["W_s"] = _entrada(
        "7.3", w_s, "W = max(ceil(n_lat/lambda5), W_min), W_min = floor(tetoLag + atraso_max) + 1 (complemento 1 "
        "§4.2), com a reserva de W do §4.3", execs=usadas, celulas=sorted({e.celula for e in usadas}), unidade="s",
        dados={"w_formula_s": w_formula, "termo_vazao_s": cw["termo_vazao_s"], "w_min_s": cw["w_min_s"],
               "reserva_aplicada": reserva, "atraso_max_ns": atraso_max,
               "atraso_max_execucao": max(atrasos)[1] if atrasos else None, "rodadas": len(atrasos),
               "teto_lag_ms": teto_ms, "n_lat": N_LAT, "subestimacao_w_menor_que_5_s": subest,
               "aceito_pelo_codigo": None if w_s is None else
               (teto_ms * MS < w_ns and (atraso_max or 0) <= w_ns - teto_ms * MS)},
        incerteza={"lambda5_N": len(lambdas), "rodadas_defasagem": len(atrasos)}, motivo=cw["motivo"],
        leituras=["A-1", "A-6", "A-17"])

    previstas = []
    for e in fk:
        n = sum(1 for o in e.trilha if o["tipo"] == "acao" and o.get("operacao") == "APLICAR"
                and (o.get("acao") or {}).get("tipo") == "AC-5" and o.get("resultado") == "PREVISTA")
        if n:
            previstas.append({"dir": e.nome, "linhas": n})
    P["kLag"] = _entrada(
        "7.13", rk["valor"], "menor k >= 1 com zero ocorrencias (C-8) do predicado do P1 (vistaCompleta && suspeito "
        "!= null && persistencia >= k && respondeuEmTodas(suspeito), PoliticaPorAssinatura.java:158), nas execucoes "
        "sem particao, na grade do W final; max(k, kLag) W + 13,3 < 39,8 (R-1 da V-W9) com a reserva de W",
        execs=fk, celulas=CEL_KLAG,
        dados={"maior_persistencia_contada": rk["maior_persistencia_contada"],
               "ocorrencias_contadas_por_k": rk["ocorrencias_contadas_por_k"], "sem_resposta": rk["sem_resposta"],
               "dentro_do_limite": rk["dentro_do_limite"], "k_max_no_limite": rk["k_max_no_limite"],
               "n_janelas_total": rk["n_janelas_total"], "janelas_sem_rodada": rk["janelas_puladas"],
               "W_s": w_s, "teto_lag_ms": teto_ms, "por_execucao": rk["por_execucao"],
               "perfil_conservador": "nao exigido na contagem (depende de theta e lag_in derivados juntos; ignora-lo "
                                     "so torna a contagem mais conservadora)",
               "conferencia_acao_prevista": {"linhas_ac5_prevista": sum(x["linhas"] for x in previstas),
                                             "execucoes": previstas,
                                             "nota": "linhas acao PREVISTA do braco A gravadas com o kLag provisorio "
                                                     "(2) e o W provisorio: so conferencia"}},
        incerteza=incerteza_zero(rk["N"], w_s), motivo=rk.get("motivo"), leituras=["A-1", "A-7", "A-8", "A-15"])

    # ---- 7.4 viabilidade do AC-5 (com a reserva de W)
    if rw is None:
        vb = {"viavel": None, "motivos": ["W indeterminado (7.3): viabilidade nao avaliavel"], "reserva_aplicada": False,
              "candidatos": []}
    else:
        vb = {"viavel": rw["viavel"], "motivos": rw["motivos"], "reserva_aplicada": rw["reserva_aplicada"],
              "candidatos": rw["candidatos"]}
    P["viabilidade_ac5"] = _entrada(
        "7.4", vb, "AC-5 viavel em W' se max(k, kLag_min(W')) W' + 13,3 < 39,8 (R-1 da V-W9); se nao for viavel no "
        "W da 7.3, W passa ao "
        "maior W' viavel entre W_min e W - 1; sem nenhum, o AC-5 e declarado inviavel antes da avaliacao "
        "(complemento 1 §4.3)", execs=fk, celulas=CEL_KLAG,
        dados={"W_s": w_s, "w_formula_s": w_formula, "w_min_s": cw["w_min_s"], "kLag": rk["valor"],
               "cobertura_latencia": cobertura, "t_parada_s": float(T_PARADA), "t_det_min_s": float(T_DET_MIN)})

    # ---- 7.5 excedencia por janela
    P["excedencia_por_janela"] = _entrada(
        "7.5", None if w_ns is None else {"execucoes": [
            {"dir": e.nome, "celula": e.celula, "perfil": e.modo,
             "janelas": [{k: x[k] for k in ("t_ini_ns", "elegiveis", "excedencias", "total", "erros", "taxa_lat",
                                            "taxa_err")} for x in jan[e.nome]]} for e in usadas]},
        "recalculada do CSV de eventos com a referencia 7.1 (do perfil da execucao) e a janela 7.3",
        execs=usadas, celulas=sorted({e.celula for e in usadas}), motivo=None if w_ns else sem_w,
        leituras=["A-8"])

    # ---- 7.6 k e 7.9 n_err
    P["k"] = _entrada("7.6", K_NUCLEO, "k = 2 (derivacao binomial do plano de 22/10 §3.2); theta derivado para esse k")
    P["n_err"] = _entrada("7.9", N_LAT, "n_err = n_lat (100): com W escolhido para ter >= n_lat eventos por janela, "
                                         "o sinal de erro fica determinado onde o de latencia fica")

    # ---- 7.7 theta_in
    def serie(execs, campo):
        return [[x[campo] for x in jan[e.nome]] for e in execs] if w_ns is not None else []

    lim, sem_cob = {}, {}
    for nome, campo in (("theta_lat_in", "taxa_lat"), ("theta_err_in", "taxa_err")):
        if w_ns is not None:
            r = limiar_entrada_com_cobertura(serie(sf, campo), K_NUCLEO)     # R-2 com o complemento 3a §2
        else:
            r = {"valor": None, "maximo_dos_minimos": None, "N": 0, "ocorrencias": None, "motivo": sem_w,
                 "sem_cobertura": False, "janelas_determinadas": 0, "n_janelas_total": 0, "fracao_determinada": None}
        sem_cob[nome] = r["sem_cobertura"]
        lim[nome] = r["valor"]
        P[nome] = _entrada(
            "7.7", r["valor"], "menor valor tal que 'k janelas seguidas >= theta_in' tem zero ocorrencias nas janelas "
            "das execucoes ASYNC sem falha (C-8); com N = 0 (nenhuma posicao de k janelas determinadas seguidas), o "
            "sinal fica sem cobertura e theta_in = o menor double acima de 1 (complemento 3 §2 e 3a)",
            execs=sf, celulas=CEL_SEM_FALHA_ASYNC,
            dados={"maximo_dos_minimos": r["maximo_dos_minimos"], "ocorrencias": r["ocorrencias"], "k": K_NUCLEO,
                   "n_janelas_total": r["n_janelas_total"], "janelas_determinadas": r["janelas_determinadas"],
                   "fracao_determinada": r["fracao_determinada"], "sem_cobertura": r["sem_cobertura"]},
            incerteza=incerteza_zero(r["N"], w_s), motivo=r["motivo"], leituras=["A-2", "A-7", "A-8"])

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
        leituras=["A-8", "A-14"])

    # ---- 7.8 m (so theta_in e lag_in; complemento 1 §3), T_cooldown
    fm = _de(usadas, CEL_M)
    seqs, nomes_seq = [], []
    if w_ns is None or any(v is None for v in lim.values()):
        rm = {"valor": None, "maior_trecho": None, "trechos_seguidos_de_alta": None, "trechos_cortados_no_end": None,
              "N": 0,
              "motivo": "depende de W, theta_lat_in, theta_err_in e lag_in (algum nao derivado)"}
    else:
        for e in fm:
            t_end = e.t_end
            js = [x for x in jan[e.nome] if x["lag_rodadas"] > 0]          # janela sem rodada: pulada (§4.1)
            classes = "".join("H" if alta_janela(x, lim) else "N" for x in js)
            inicio = next((i for i, x in enumerate(js) if x["t_ini_ns"] >= t_end), len(js))
            seqs.append((classes, inicio))
            nomes_seq.append((e.nome, len(jan[e.nome]) - len(js)))
        rm = m_reentrada(seqs)
    m = rm["valor"]
    P["m"] = _entrada(
        "7.8", m, "m = 1 + L, L = maior trecho de janelas sem sinal alto depois do end que termina imediatamente "
        "numa janela alta, nas ASYNC F1, F2 e F4 (complemento 1 §3); o trecho que atravessa o end e cortado no end e "
        "conta so a parte depois dele (complemento 3 §3, R-3)", execs=fm, celulas=CEL_M,
        dados={"maior_trecho": rm["maior_trecho"], "trechos_seguidos_de_alta": rm["trechos_seguidos_de_alta"],
               "trechos_cortados_no_end": rm.get("trechos_cortados_no_end"),
               "limiares_usados": {k: lim[k] for k in sorted(lim)},
               "sequencias": [{"dir": n, "classes": c, "inicio_depois_do_end": i, "janelas_puladas": p}
                              for (n, p), (c, i) in zip(nomes_seq, seqs)]},
        incerteza={"N": rm["N"]}, motivo=rm["motivo"], leituras=["A-1", "A-4", "A-5", "A-6", "A-15"])
    P["T_cooldown_s"] = _entrada("7.8", None if m is None or w_s is None else m * w_s, "T_cooldown = m W",
                                 unidade="s", motivo=None if m is not None and w_s is not None else "depende de m e W")

    # ---- 7.8 theta_out e 7.12 lag_out pelo menor valor (complemento 1 §2), depois de m
    for nome_out, nome_in, campo, regra, texto in (
            ("theta_lat_out", "theta_lat_in", "taxa_lat", "7.8", "theta_out = menor c em {0} U {observados abaixo "
             "de theta_in} tal que nenhum trecho de janelas ASYNC sem falha com c < taxa < theta_in passa de m "
             "(complemento 1 §2)"),
            ("theta_err_out", "theta_err_in", "taxa_err", "7.8", "idem, sobre a taxa de erro"),
            ("lag_out_ms", "lag_in", None, "7.12", "lag_out: a mesma regra sobre o lagMaxMs da janela, com lag_in "
             "(complemento 1 §2); janela sem rodada pulada (§4.1)")):
        puladas = 0
        if campo is None:
            ser = []
            for e in (sf if w_ns is not None else ()):
                ser.append([x["lag_max"] for x in jan[e.nome] if x["lag_rodadas"] > 0])
                puladas += sum(1 for x in jan[e.nome] if x["lag_rodadas"] == 0)
            lin = lag_in
        else:
            ser, lin = serie(sf, campo), lim[nome_in]
        if campo is not None and sem_cob[nome_in]:
            r = {"valor": THETA_OUT_SEM_COBERTURA, "N": sum(len(x) for x in ser), "maior_trecho_na_banda": 0,
                 "candidatos": None, "motivo": None}
        else:
            r = limiar_saida(ser, lin, m)
        dados = {"m": m, "limiar_de_entrada": lin, "maior_trecho_na_banda": r["maior_trecho_na_banda"],
                 "candidatos": r["candidatos"]}
        if campo is None:
            dados["janelas_sem_rodada_puladas"] = puladas
        else:
            dados["sem_cobertura"] = sem_cob[nome_in]
        P[nome_out] = _entrada(
            regra, r["valor"], texto, execs=sf, celulas=CEL_SEM_FALHA_ASYNC, unidade="ms" if campo is None else None,
            dados=dados, incerteza=incerteza_zero(r["N"], w_s), motivo=r["motivo"],
            leituras=["A-3", "A-4", "A-7"] + (["A-1"] if campo is None else []))

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
               "fora": fora, "n_fora": len(fora)}, incerteza={"n_convergiram": rt["n"]}, motivo=rt["motivo"],
        leituras=["A-6", "A-16"])

    # ---- 7.14 T_estavel
    te = t_estavel([(e.nome, e.t_end, [(a["t_ns"], bool(a.get("anomala"))) for a in e.obs["topologia"]])
                    for e in fc], P_SONDA_S)
    P["T_estavel_s"] = _entrada(
        "7.14", te["valor"], "maior duracao de um trecho com 3 nos HEALTHY que terminou em nova amostra anomala "
        "depois do marcador end, mais P_sonda (2 s); sem recaida, P_sonda", execs=fc, celulas=CEL_FALHA, unidade="s",
        dados={"maior_trecho_s": te["maior_trecho_s"], "recaidas": te["recaidas"], "p_sonda_s": P_SONDA_S,
               "por_execucao": te["por_execucao"]}, incerteza={"recaidas": te["recaidas"]}, motivo=te["motivo"],
        leituras=["A-6", "A-9", "A-15"])

    # ---- 7.15 T_hold_viol
    th = t_hold_viol([(e.nome, e.viol) for e in usadas], w_s)
    P["T_hold_viol_s"] = _entrada(
        "7.15", th["valor"], "maior intervalo entre duas violacoes seguidas na mesma execucao; sem execucao com duas "
        "violacoes, W", execs=usadas, celulas=sorted({e.celula for e in usadas}), unidade="s",
        dados={"maior_intervalo_s": th["maior_intervalo_s"], "por_execucao": th["por_execucao"]},
        motivo=th["motivo"], leituras=["A-6", "A-10"])

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
                             "t_exec_medio_s": sum(vals) / len(vals) if vals else None,
                             "ciclo_completo": ciclo_completo(tempos)},
        leituras=["A-12"])
    vmax = max(vols) if vols else None
    P["volume_exec_mb"] = _entrada(
        "7.17", None if vmax is None else _ceil_fr(Fraction(vmax[0], 2 ** 20)), "maior volume medido", execs=usadas,
        unidade="MiB", dados={"maior_bytes": None if vmax is None else vmax[0],
                              "maior_execucao": None if vmax is None else vmax[1],
                              "maior_mib": None if vmax is None else vmax[0] / 2 ** 20},
        motivo=None if vmax else "nenhuma execucao aceita", leituras=["A-11"])
    pre_max = max(tpre) if tpre else None
    pos_max = max(tpos) if tpos else None
    et = exec_teto(par["duracao_s"], par["religamento_prazo_s"], rt["valor"], w_s,
                   None if pre_max is None else pre_max[0], None if pos_max is None else pos_max[0])
    P["exec_teto_s"] = _entrada(
        "7.17", et["valor"], "formula de 4.1 com o convergencia_periodo_s (= W final), o teto da 7.10 e o maior t_pre e "
        "t_pos medidos; nunca abaixo de 1010 s", execs=usadas, unidade="s",
        dados={"formula_s": et["formula_s"], "duracao_s": par["duracao_s"], "t_enc_s": T_ENC_S,
               "religamento_prazo_s": par["religamento_prazo_s"], "teto_convergencia_s": rt["valor"],
               "convergencia_periodo_s": w_s, "t_pre_max_s": None if pre_max is None else float(pre_max[0]),
               "t_pre_execucao": None if pre_max is None else pre_max[1],
               "t_pos_max_s": None if pos_max is None else float(pos_max[0]),
               "t_pos_execucao": None if pos_max is None else pos_max[1], "fator": float(FATOR),
               "minimo_s": EXEC_TETO_MIN_S}, motivo=et["motivo"], leituras=["A-11"])

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
        leituras=["A-13"])
    P["curva_de_poder"] = _entrada(
        "7.18", {"executado": False,
                 "comando": "python -E -P analysis/poder.py --taxas-a taxas-<celula>.txt --semente %d --n-sim %d "
                            "--m-celulas %d --blocos %d --saida poder-<celula>.json"
                            % (SEMENTE_PODER, N_SIM_PODER, M_CELULAS_TESTE, N_BLOCOS),
                 "taxas_a": taxas_cmd},
        "poder.py (W-5) com as taxas do braco A por celula de teste, 12 blocos (fixo), reducoes de 0 a 100 %, m das "
        "celulas de teste do B-40. So informativo", execs=ft, celulas=CEL_TESTE,
        motivo="poder.py importa numpy: fora do python3 -I; rodar o comando (python -E -P, decisao G2) com as taxas_a "
               "de cada celula", leituras=["A-13"])

    # ---- contagens por celula e valores para o B-40 parte 2
    por_cel = {c: {"aceitas": sum(1 for e in usadas if e.celula == c), "planejadas": n}
               for c, n in CELULAS_PLANEJADAS.items()}
    v = {k: P[k]["valor"] for k in P}
    b40 = {"leitura": "regras da secao 7 com o %s" % C1,
           "wNs": None if w_s is None else w_s * S, "nLat": N_LAT, "nErr": v["n_err"], "k": v["k"], "m": v["m"],
           "thetaLatIn": v["theta_lat_in"], "thetaLatOut": v["theta_lat_out"], "thetaErrIn": v["theta_err_in"],
           "thetaErrOut": v["theta_err_out"],
           "cooldownNs": None if v["T_cooldown_s"] is None else v["T_cooldown_s"] * S,
           "estavelNs": None if v["T_estavel_s"] is None else int(round(_fr(v["T_estavel_s"]) * S)),
           "holdViolNs": None if v["T_hold_viol_s"] is None else int(round(_fr(v["T_hold_viol_s"]) * S)),
           "politica.kLag": v["kLag"], "defasagem.lagInMs": v["lag_in_ms"], "defasagem.lagOutMs": v["lag_out_ms"],
           "ac5_viavel": vb["viavel"], "reserva_de_w_aplicada": vb["reserva_aplicada"],
           "convergencia_teto_s": v["teto_convergencia_s"],
           "convergencia_periodo_s": w_s, "exec_teto_s": v["exec_teto_s"], "volume_exec_mb": v["volume_exec_mb"],
           "referencia_async_sha256": (v["referencia_p99_async"] or {}).get("sha256"),
           "referencia_sync_sha256": (v["referencia_p99_sync"] or {}).get("sha256")}
    pc = problemas_de_carga(b40, teto_ms)
    b40["carga"] = {"carrega_no_controlador": not pc, "problemas": pc,
                    "guardas": "ParametrosControlador.java:69-94/:174-190/:211, ParametrosPolitica.java:59/:145, "
                               "ParametrosDefasagem.java:57-63/:110/:118 (GUARDAS_JAVA)"}
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
                           "quantil_lag_in": QUANTIL_LAG_IN, "cobertura_max": COBERTURA_MAX, "D_s": D_S,
                           "alfa": float(ALFA), "t_parada_s": float(T_PARADA), "t_det_min_s": float(T_DET_MIN),
                           "p_sonda_s": P_SONDA_S, "teto_conv_base_s": TETO_CONV_BASE_S, "fator": float(FATOR),
                           "t_enc_s": T_ENC_S, "exec_teto_min_s": EXEC_TETO_MIN_S, "n_blocos": N_BLOCOS,
                           "semente_poder": SEMENTE_PODER, "n_sim_poder": N_SIM_PODER},
            "parametros": P, "regras": {k: sorted(x) for k, x in sorted(regras.items())},
            "valores_b40_parte2": b40, "leituras": LEITURAS, "emendas_vw9": EMENDAS_VW9,
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
