"""Comparacao pareada A (shadow) x B (active) por blocos (B-39.3; B-39.4 / W-5, esquema /2).

Especificacao do /2: docs/plano-alinhamento-objetivos-2026-11-04.md, secoes 3.1 a 3.5 e Prompt 2, com a
regra de intersecao-uniao fixa (decisao D-1 do Gestor) e os acrescimos do W-5 de
docs/plano-execucao-2026-11-04.md, secao 9 (desfechos descritivos do controlador, tipos de celula,
k e p minimo, Holm entre celulas).

W-5c (rascunho do pre-registro do benchmark refeito, a5ddac9, secao 2): V = pares distintos (chave, invariante)
por execucao, somando o auditor online, a sonda de replay do W-10 (origem = sonda) e a varredura final (REAL);
replay tardio e varredura atribuidos as fases pelo instante da invalidacao confirmada; formas 1 e 2 de I1 e a
sensibilidade sem a forma 2; ReplayValidate fora de latencia, vazao, taxa de erro e denominador (ver
apurar_violacoes_execucao e docs/relatorio-w5c-2026-11-06.md). W-5d (V-W5c, secao 5): bloco com varredura com
erro em qualquer braco usa V e :sem_forma2 sem a varredura nos dois bracos e fica no teste; sensibilidade
descritiva sem esses blocos em violacoes.detalhe.varredura_com_erro.

Correcoes da V-W5 (docs/validacao-w5-2026-11-05.md; decisoes do coordenador em nome do Gestor, 05/11):
instante do evento anomalo = inicio da operacao (P-2); sem t_fim_carga_ns o Delta_rec fica ausente (P-3);
taxa_erro e falhas_percebidas em diferenca (P-4, G3 de 28/10); --w-recovery-s sem padrao (P-5); Delta_conv
a partir do religamento do no parado pelo AC-5 (P-6); celula sem evento nas fases recusada (A-2); trilha
obrigatoria nos dois bracos das celulas de teste (A-3).

Uso (da raiz do repositorio; numpy e scipy estao no site do usuario, por isso -E -P e nao -I, decisao
G2 de 28/10):
  python -E -P analysis/comparar_bracos.py --manifesto M.tsv --semente-boot S --saida comparacao.json
      --delta-latencia DL --w-analise-s W --w-recovery-s WR
      [--celulas-teste C1,C2] [--celulas-descritivas C3] [--celulas-referencia C4]
      [--referencia-erro CELULA=referencia-erro.json ...] [--p-max-w P --razao-ocioso-central R]
      [--fases durante,recuperacao] [--rep 1] [--n-boot 10000] [--n-hier 2000]
      [--delta 0.10] [--marcadores-nome marcadores.txt]

Parametros SEM valor padrao (regra do autor: limiar sem origem no codigo fica obrigatorio; o pre-registro
B-40 passa o valor): --delta-latencia (delta_L da nao inferioridade do p99; a Proposta da 10 %,
Metodologia.tex:229), --w-analise-s (W_c da censura do Delta_rec e janela do Delta_rec de sensibilidade;
derivada da vazao da calibracao, plano 3.5) e --w-recovery-s (W_recovery das fases; o B-40 deriva do
protocolo do W-6: intervalo entre o fim da falha e o fim da carga; o 60 s antigo era "valor de partida" do
B-33, nunca fixado). Cada celula do manifesto tem de estar em exatamente uma das listas
--celulas-teste/--celulas-descritivas/--celulas-referencia (exit 2 se nao). A celula sem falha roda em
chamada propria com --fases antes: sem marcadores tudo e "antes", e uma celula sem nenhum evento nas fases
pedidas e recusada (exit 2). Celula de teste: no maximo 16 blocos (permutacao exata), trilha
decisoes-rep-NNN.jsonl nos dois bracos, braco B com atuador != nulo e politica != nenhuma, o mesmo
param_sha256 em A e B de cada bloco e os mesmos instrumentos de observacao (sonda de replay e varredura; W-5c)
nos dois bracos de cada bloco (exit 2 se nao).

Manifesto TSV com cabecalho exato `bloco<TAB>celula<TAB>braco<TAB>execucao`, braco em {A, B}, um par
A/B por (celula, bloco); `execucao` e um diretorio (relativo ao manifesto, se nao for absoluto) com:
  rep-NNN.csv + meta-csv-rep-NNN.json (contrato 2)      desfechos de eventos, Delta_rec
  s3-acoes-rep-NNN.csv (contrato S3 v2, 10 colunas)     desfechos S3 (8 colunas: exit 2)
  marcadores.txt                                         fases, t_fim-falha (Delta_rec)
  convergencia-resumo-rep-NNN.json                       fases, fim da medicao e Delta_conv (t_fim_carga_ns;
                                                         t_religamento_ns quando o AC-5 parou um no: instante
                                                         em que a carga viu os tres nos de volta),
                                                         divergencia
  energia-rep-NNN.json (energia_proxy.py)                CPU e energia (opcional; B39-R1 confere W e fases)
  violacoes-rep-NNN.csv (InvariantAuditor.dumpCsv)       taxa de violacoes (sem ele: desfecho ausente); coluna
                                                         origem = sonda com a sonda de replay (W-10)
  varredura-rep-NNN.csv (VarreduraFinal; --sweep)        achados REAL somados a V (opcional; com
                                                         varredura-rep-NNN.erro.txt, V ausente na execucao
                                                         isolada e, no bloco, V sem a varredura nos dois
                                                         bracos: W-5d)
  execucao.json (orquestrador)                           --servers (isn1, isn2, isn3): replica do no da falha,
                                                         para as formas de I1 (opcional)
  s3-resumo-rep-NNN.json                                 sonda_tardia_max (teto do replay tardio; opcional)
  decisoes-rep-NNN.jsonl (controlador, contrato v1.x)    descritivos do controlador e rastreabilidade

Desfechos por execucao e escala da estatistica por bloco:
  taxa_violacoes_por_1000   pares distintos (chave, invariante) de I1, I4 e I2 REAL das tres
                            origens x 1000 / acoes S3, fases selecionadas (W-5c)         diferenca
  taxa_violacoes_por_1000:sem_forma2  o mesmo sem os pares de I1 da forma 2 (sensibilidade; so
                            com sonda ou varredura)                                      diferenca
  acao_s3_p99_com_espera    p99 da acao S3 (n >= 1000)                                   log da razao
  vazao_util                acoes uteis por s nas fases selecionadas                     log da razao
  delta_rec_s               ultima operacao iniciada depois do fim da falha que terminou
                            em anomalia, com censura; sem t_fim_carga_ns: ausente         diferenca
  delta_rec_metodologia_s   primeira janela de W s na linha de base (sensibilidade)      diferenca
  delta_conv_s              t_convergencia_abs_ns - t_religamento_ns (AC-5 parou um no) ou
                            - t_fim_carga_ns (sem no parado); nunca t_fim_ns             diferenca
  divergencia_persistente   0/1 do resumo de convergencia                                diferenca
  taxa_erro, falhas_percebidas_por_1000   taxas com zeros (G3 de 28/10)                  diferenca
  <taxa>:razao              as mesmas duas, razao com exclusao dos zeros (sensibilidade) log da razao
  energia_total_j_por_1000_uteis:<r>  energia estimada (P_max e r do energia_proxy)      log da razao
  demais (evento_p99:*, acao_s3_p99_sem_espera, cpu_por_1000_uteis)                      log da razao

Familias: co-principal-V (taxa de violacoes), co-principal-L (p99 da acao S3), nao-inferioridade (vazao
util), secundario (Holm dentro da celula) e sensibilidade (sem Holm: Delta_rec da Metodologia, <taxa>:razao,
taxa de violacoes sem a forma 2 e energia com r diferente do central).

Energia (so com --p-max-w e --razao-ocioso-central, juntos): o P_max de cada energia-rep-NNN.json tem de ser
o passado (exit 2 se nao) e a razao central tem de existir no JSON. Valores da campanha (decisao do Gestor
de 05/11, Barroso e Holzle p. 36/34, conferencia do Bibliotecario): --p-max-w 181 (Maximum Turbo Power do i5-14600KF,
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
import hashlib
import json
import math
import os
import sys
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
import apuracao_v  # noqa: E402,F401
# W-5c: apuracao de V por pares distintos (chave, invariante), das origens online, sonda de replay (W-10) e
# varredura final (so REAL), pelo pre-registro do benchmark refeito (rascunho a5ddac9, 2.3 a 2.5). Fonte unica em
# apuracao_v.py, que roda em python3 -I sem numpy para o calibracao_s3 do W-9: daqui saem as funcoes de
# apuracao_v.COPIADAS (14, com a decompor_i1_pela_fusao do W-5e), as 26 constantes e o ErroUso (o mesmo objeto, para que o erro de contrato dos leitores de violacoes e de varredura
# continue saindo com exit 2 por ERROS_USO). As origens de V e a tolerancia de casamento estao comentadas la.
from apuracao_v import (  # noqa: E402
    ErroUso,
    _int_ou_none, instante_ns, _classe_v, ler_violacoes, ler_varredura, ler_eventos_por_chave, no_da_falha,
    servidores_da_execucao, replica_do_no, _instante_invalidacao, _leitura_da_deteccao, _forma_do_par,
    apurar_violacoes_execucao, INVARIANTES_VIOL, ORIGEM_ONLINE, ORIGEM_SONDA, ORIGEM_VARREDURA, ORIGENS, TIPO_ONLINE,
    TIPO_IMEDIATA, TIPO_TARDIA, TIPO_VARREDURA, ATRIB_DETECCAO, ATRIB_INVALIDACAO, ATRIB_SEM, ATRIBUICOES, FORMA_1,
    FORMA_2, FORMA_OUTRA, FORMA_INDET, FORMAS_I1, OP_REPLAY, RC_CONFIRMADO, COLUNAS_VARREDURA, TOL_CASAMENTO_NS,
    REGRA_V, _RX_INSTANTE, _RX_MOTIVO, _RX_IDENTIDADE, decompor_i1_pela_fusao)

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
DESFECHO_ERRO = "taxa_erro"
DESFECHO_FALHAS = "falhas_percebidas_por_1000"
# P-4 da V-W5 (G3 de 28/10): as duas taxas com zeros vao em diferenca; a razao com exclusao dos zeros e
# sensibilidade, com o nome <taxa> + SUFIXO_RAZAO
ESCALA_DIFERENCA = frozenset((DESFECHO_V, DESFECHO_DREC, DESFECHO_DREC_MET, DESFECHO_DCONV, DESFECHO_DIV,
                              DESFECHO_ERRO, DESFECHO_FALHAS))
SUFIXO_RAZAO = ":razao"
# P-6 da V-W5 (achado A-1, opcao a; desenho fixado pelo coordenador em 05/11): campo do resumo de
# convergencia, gravado pelo programa de carga. Ao fim da carga, se houve AC-5, o programa sinaliza o fim, o
# orquestrador (W-6) faz o `podman start` do no parado, e o programa espera a topologia completa (com prazo)
# antes de iniciar o laco de convergencia; t_religamento_ns e o instante em que ele viu os tres nos de volta.
CAMPO_RELIGAMENTO = "t_religamento_ns"
TIPO_AC5 = "AC-5"
ATUADOR_NULO = "nulo"
POLITICA_NENHUMA = "nenhuma"
FAMILIA_V = "co-principal-V"
FAMILIA_L = "co-principal-L"
FAMILIA_NI = "nao-inferioridade"
FAMILIA_SEC = "secundario"
FAMILIA_SENS = "sensibilidade"
TIPOS_CELULA = ("teste", "descritiva", "referencia")
MODO_ESPERADO = {"A": "shadow", "B": "active"}
ERROS = ("ERROR_TRANSPORT", "ERROR_UNAVAILABLE")
# Resultados de APLICAR que poem a acao na pilha (real ou virtual): ExecutorDeAcoes.java:436-440 (tudo
# menos FALHOU) e o shadow (PREVISTA); DESCARTADA e JA_ATIVA nao abrem intervalo.
RESULTADOS_ATIVA = ("APLICADA", "PREVISTA", "NAO_EXECUTADA")
# Origens de linha acao que vem de uma decisao (regra e t_fim_janela_ns obrigatorios): LacoDeControle
# (:313, :324, :327) e ExecutorDeAcoes (:454). ENCERRAMENTO e GANCHO_JVM saem com null (contrato v1.1).
ORIGENS_COM_DECISAO = ("POLITICA", "R4", "FALHA_NA_APLICACAO")
DESFECHO_V_SEM_F2 = DESFECHO_V + ":sem_forma2"
# a sensibilidade sem a forma 2 vai em diferenca, como V; fica fora de ESCALA_DIFERENCA para que o
# parametros.escala_diferenca das execucoes sem sonda e sem varredura continue o da base (nao regressao)
ESCALA_DIFERENCA_SENS_V = frozenset((DESFECHO_V_SEM_F2,))
# W-5e: fatorial 2x2 por bloco na celula de teste (DT-027, complemento 1 do pre-registro do benchmark refeito,
# 4fa4546): regra de fusao do sessions-async (P = PREFERRED_NON_NULL, A = A1) x controlador (O = shadow, L = active
# com a A4). O braco sai do execucao.json do orquestrador: CAMPO_FUSAO (pnn|a1; o nome fica combinado com o
# orquestrador fatorial, wt-fat; ainda nao existia em 06/11, entao o leitor usa "fusao"), "controller" (shadow|active,
# o campo de sempre) e "bloco". O instante da fusao (primeira vista de fusao, ISPN000093, depois do end; DT-027 2.2 e
# 5.3) vem de CAMPO_T_FUSAO no mesmo arquivo: ausente = medida nao emitida; null = sem fusao (medida ausente).
BRACOS_FATORIAL = ("PO", "PL", "AO", "AL")
CAMPO_FUSAO = "fusao"
CAMPO_CONTROLLER = "controller"
CAMPO_T_FUSAO = "t_fusao_ns"
FUSAO_DO_BRACO = {"PO": "pnn", "PL": "pnn", "AO": "a1", "AL": "a1"}
MODO_FATORIAL = {"PO": "shadow", "PL": "active", "AO": "shadow", "AL": "active"}
BRACOS_ATIVOS_FATORIAL = ("PL", "AL")          # rastreabilidade integral exigida na IU dos dois efeitos
EFEITOS = ("A1", "A4", "interacao")
EFEITOS_PRINCIPAIS = ("A1", "A4")
# efeito simples: (braco, braco de referencia), y = y_braco - y_referencia (DT-027, secao 1: so IC, sem teste)
EFEITOS_SIMPLES = {"A1_controlador_desligado": ("AO", "PO"), "A1_controlador_ligado": ("AL", "PL"),
                   "A4_regra_atual": ("PL", "PO"), "A4_regra_a1": ("AL", "AO")}
# H_V nao avaliavel quando os bracos de referencia do efeito nao tem violacao em nenhum bloco (DT-021, por efeito)
REFERENCIA_EFEITO = {"A1": ("PO", "PL"), "A4": ("PO", "AO")}
DESFECHO_PERDA = "perda_permanente_sids"
DESFECHO_EXPOSICAO = "exposicao_janela_sids"
ESCALA_DIFERENCA_W5E = frozenset((DESFECHO_PERDA, DESFECHO_EXPOSICAO))
REGRA_FATORIAL = ("fatorial 2x2 por bloco (DT-027): bracos PO, PL, AO, AL com a mesma semente; contrastes por bloco "
                  "d_A1 = 1/2[(y_AO - y_PO) + (y_AL - y_PL)], d_A4 = 1/2[(y_PL - y_PO) + (y_AL - y_AO)], d_int = "
                  "(y_AL - y_AO) - (y_PL - y_PO), em diferenca (V e taxas) ou no log (p99); troca de sinal exata por "
                  "contraste; IU por efeito principal (H_V: V; H_L: p99 com delta_L; vazao util nao inferior; "
                  "rastreabilidade dos bracos PL e AL); Holm sobre as hipoteses de teste (os dois efeitos de cada "
                  "celula fatorial, m = 2 com uma celula); interacao com IC e p, sem Holm e fora da IU; efeitos "
                  "simples so com IC; secundarios com Holm por efeito; varredura com erro em qualquer braco: V e "
                  ":sem_forma2 dos quatro sem a varredura e perda permanente do bloco nao avaliavel")
REGRA_PERDA_EXPOSICAO = ("perda_permanente_sids: sids distintas com I1 que conta em V vista pela varredura (REAL), "
                         "pelo replay tardio ou pelo auditor online em ou depois de t_fusao_ns; exposicao_janela_sids: "
                         "sids distintas com I1 que conta em V vista pelo replay imediato ou pelo auditor online antes "
                         "de t_fusao_ns (DT-027, 2.2)")
REGRA_VARREDURA_ERRO = ("varredura com erro (varredura-rep-NNN.erro.txt) em qualquer braco do bloco: V e "
                        ":sem_forma2 dos dois bracos sem as deteccoes da varredura (online e sonda), bloco no teste; "
                        "subestima V nos dois bracos desses blocos; sensibilidade descritiva sem esses blocos, sem p e "
                        "sem Holm (V-W5c, secao 5; W-5d)")
REGRA_V_SEM_F2 = ("sensibilidade sem a forma 2 de I1 (Logout confirmado por outro no e releitura VALID servida pelo "
                  "no da falha entre o start e o end); so com sonda ou varredura (pre-registro 2.5)")


ERROS_USO = (ErroUso, energia_proxy.ErroUso, referencia_p99.ErroUso, referencia_erro.ErroUso)


# ============================================================================= manifesto

def _chave_bloco(b):
    return (0, int(b), b) if b.isdigit() else (1, 0, b)


def ler_manifesto(caminho):
    """{celula: {bloco: {braco: Path}}}; ErroUso com bloco incompleto, duplicado ou repetido.

    Bloco de 2 bracos (A, B) ou do fatorial 2x2 (PO, PL, AO, AL; W-5e); uma celula nao mistura os dois.
    """
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
        if braco not in BRACOS and braco not in BRACOS_FATORIAL:
            raise ErroUso("braco invalido na linha %d: %r (use A ou B; no fatorial 2x2, PO, PL, AO ou AL)"
                          % (i, braco))
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
        if set(par) != set(BRACOS) and set(par) != set(BRACOS_FATORIAL):
            raise ErroUso("bloco %s da celula %s incompleto (bracos: %s)" % (bloco, celula, ",".join(sorted(par))))
        out.setdefault(celula, {})[bloco] = par
    for celula, bs in out.items():
        if len({len(par) for par in bs.values()}) > 1:
            raise ErroUso("celula %s mistura blocos de 2 bracos (A, B) e do fatorial 2x2 (PO, PL, AO, AL)" % celula)
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

    anomalos_ns: instante de INICIO de cada operacao que terminou em anomalia (P-2 da V-W5: regra de fase do
    projeto, EventCsvWriter.java:157; o fim incluiria a duracao, que o braco B altera). Delta_rec = ultimo
    inicio > t_fim-falha menos t_fim-falha; 0 sem anomalia iniciada depois do fim. Censura: ultimo dentro dos
    ultimos W_c da medicao (t > fim_medicao - W_c) => censurada, valor = fim_medicao - t_fim-falha.
    fim_medicao_ns e o t_fim_carga_ns do protocolo; sem ele, ausente (P-3 da V-W5).
    """
    if t_fim_falha_ns is None:
        return None, None, "sem marcador end (t_fim-falha)"
    if w_c_ns is None:
        return None, None, "w_analise nao informado"
    if fim_medicao_ns is None:
        return None, None, "sem t_fim_carga_ns (o fim da medicao e o do protocolo)"
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

    eventos: [(t_ns de inicio da operacao, erro)]. Janelas [t_fim + jW, t_fim + (j+1)W) inteiras ate
    fim_medicao (t_fim_carga_ns; sem ele, ausente). A janela j esta na linha de base se tem eventos e a
    contagem de erros nao passa do quantil de 95 % de Binomial(n_j, p_ref+); Delta = j W da primeira.
    Nenhuma: censurada, valor = fim_medicao - t_fim.
    """
    if t_fim_falha_ns is None:
        return None, None, "sem marcador end (t_fim-falha)"
    if w_ns is None:
        return None, None, "w_analise nao informado"
    if p_ref_sup is None:
        return None, None, "sem referencia de erro"
    if fim_medicao_ns is None:
        return None, None, "sem t_fim_carga_ns (o fim da medicao e o do protocolo)"
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


def ac5_aplicada(trilha):
    """True se a trilha tem uma aplicacao do AC-5 executada (APLICADA): um no ficou parado na execucao.

    PREVISTA (shadow), NAO_EXECUTADA, FALHOU e DESCARTADA nao param no nenhum (contrato v1.2, linha acao).
    """
    if trilha is None:
        return False
    for o in trilha["linhas"]:
        ac = o.get("acao") if isinstance(o.get("acao"), dict) else {}
        if (o["tipo"] == "acao" and o.get("operacao") == "APLICAR" and ac.get("tipo") == TIPO_AC5
                and o.get("resultado") == "APLICADA"):
            return True
    return False


def descrever_controlador(trilha, inicio_falha_ns, fim_medicao_ns=None):
    """Desfechos descritivos do controlador numa execucao (sem teste; W-5 item 1, plano 3.1).

    Transicoes, entradas em CONSERVADOR, tempo em CONSERVADOR (janelas x W), atraso da primeira entrada e
    da primeira acao de politica em relacao ao marcador start, acoes por tipo e resultado (executadas:
    APLICADA; previstas: PREVISTA), tempo ativo por tipo (do instante efetivo da aplicacao ate a reversao
    com sucesso) e reversoes com sucesso ou falha, tambem por origem. Acao ainda ativa no fim (o AC-5 nunca e
    revertido na execucao) conta ate fim_medicao_ns, o t_fim_carga_ns do protocolo (R-5 da V-W5); sem ele, o
    tempo ativo desse tipo fica None, com ativa_no_fim. Sem marcador start, toda entrada e toda acao conta
    como "antes da falha" (acionamento sem falha em curso).
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
    rev_origem = {}
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
            ro = rev_origem.setdefault(str(a.get("origem")), {"sucesso": 0, "falha": 0})
            if res == "FALHOU":
                rev["falha"] += 1
                ro["falha"] += 1
            else:
                rev["sucesso"] += 1
                ro["sucesso"] += 1
                if chave in ativas and t_ef is not None:
                    pt["tempo_ativo_s"] += max(0, t_ef - ativas.pop(chave)) / 1e9
    for chave, t_ini in ativas.items():
        pt = por_tipo[chave[0]]
        pt["ativa_no_fim"] = True
        if fim_medicao_ns is None or pt["tempo_ativo_s"] is None:
            pt["tempo_ativo_s"] = None
        else:
            pt["tempo_ativo_s"] += max(0, fim_medicao_ns - t_ini) / 1e9
    politicas = {}
    for o in pols:
        k = str(o.get("politica"))
        politicas[k] = politicas.get(k, 0) + 1
    return {"modo": meta.get("modo"), "esquema": meta.get("esquema"), "completo": trilha["completo"],
            "atuador": meta.get("atuador"), "politica": meta.get("politica"), "param_sha256": meta.get("param_sha256"),
            "ac5_aplicada": ac5_aplicada(trilha), "reversoes_por_origem": rev_origem,
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

def contar_violacoes(caminho, filtro=None):
    """{"I1","I2_REAL","I4"}: pares distintos (chave, classe) do violacoes-rep-NNN.csv (W-5c, pre-registro 2.3).

    Mesma selecao de linhas da DT-009 (ler_violacoes), todas as origens do arquivo; filtro(t_ns) -> bool sobre o
    instante da deteccao. Nao atribui o replay tardio pela invalidacao nem le a varredura: a contagem de V de uma
    execucao e a de apurar_violacoes_execucao. Sem o arquivo: None.
    """
    lido = ler_violacoes(caminho)
    if lido is None:
        return None
    pares = {k: set() for k in INVARIANTES_VIOL}
    for x in lido[0]:
        if filtro is None or filtro(x["t_ns"]):
            pares[x["classe"]].add(x["chave"])
    return {k: len(v) for k, v in pares.items()}


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


def ler_execucao_json(execucao):
    """Dicionario do execucao.json do orquestrador (W-6) ou None se ausente; ilegivel ou nao objeto: ErroUso."""
    p = Path(execucao) / "execucao.json"
    if not p.exists():
        return None
    try:
        j = json.loads(p.read_text(encoding="utf-8"))
    except ValueError:
        raise ErroUso("execucao.json ilegivel: %s" % p) from None
    if not isinstance(j, dict):
        raise ErroUso("execucao.json nao e objeto: %s" % p)
    return j


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
    # W-5c (pre-registro do benchmark refeito, 2.3 a 2.6): pares distintos das tres origens; os ReplayValidate
    # ficam fora do denominador (acoes S3), da latencia, da vazao e da taxa de erro (ler_eventos_inicio os descarta)
    apur = apurar_violacoes_execucao(d, nnn, ev_path, t1 - 1, dentro, args, marcadores_nome)
    varredura_erro = apur is not None and apur["varredura"] == "erro"
    viol = None if apur is None or varredura_erro else apur["pares"]
    if acoes is not None:
        if apur is None:
            out[DESFECHO_V] = dict(_ausente("sem violacoes-rep (contagem desconhecida)", n_acoes), contagem=None)
        elif varredura_erro:
            out[DESFECHO_V] = dict(_ausente("varredura com erro (varredura-rep-%s.erro.txt): contagem incompleta"
                                            % nnn, n_acoes), contagem=None)
        else:
            c = apur["total_pares"]
            out[DESFECHO_V] = dict(_taxa(c, n_acoes, 1000.0), contagem=c, linhas=apur["total_linhas"])
        if apur is not None and apur["instrumentos"]:
            if varredura_erro:
                out[DESFECHO_V_SEM_F2] = dict(out[DESFECHO_V])
            elif apur["total_pares_sem_forma2"] is None:
                out[DESFECHO_V_SEM_F2] = dict(_ausente(apur["formas_motivo"], n_acoes), contagem=None)
            else:
                c2 = apur["total_pares_sem_forma2"]
                out[DESFECHO_V_SEM_F2] = dict(_taxa(c2, n_acoes, 1000.0), contagem=c2)

    # W-5e (DT-027, 2.2): perda permanente e exposicao na janela, so quando o execucao.json traz t_fusao_ns
    ej = ler_execucao_json(d)
    fusao = None
    if ej is not None and CAMPO_T_FUSAO in ej:
        bruto = ej[CAMPO_T_FUSAO]
        t_fus = _int_ou_none(bruto)
        if bruto is not None and t_fus is None:
            raise ErroUso("%s invalido no execucao.json de %s: %r" % (CAMPO_T_FUSAO, d, bruto))
        n_px = n_acoes if n_acoes is not None else 0
        if apur is None:
            for nome in (DESFECHO_PERDA, DESFECHO_EXPOSICAO):
                out[nome] = dict(_ausente("sem violacoes-rep (contagem desconhecida)", n_px), contagem=None)
        elif t_fus is None:
            for nome in (DESFECHO_PERDA, DESFECHO_EXPOSICAO):
                out[nome] = dict(_ausente("sem instante da fusao (%s nulo: a fusao nao veio)" % CAMPO_T_FUSAO, n_px),
                                 contagem=None)
        else:
            perda, expo = decompor_i1_pela_fusao(apur, t_fus)
            fusao = {"t_fusao_ns": t_fus, "sids_perda": perda, "sids_exposicao": expo}
            out[DESFECHO_EXPOSICAO] = {"y": float(len(expo)), "n": n_px, "motivo": None, "eventos": None,
                                       "contagem": len(expo)}
            out[DESFECHO_PERDA] = (dict(_ausente("varredura com erro: perda permanente nao avaliavel", n_px),
                                        contagem=None) if varredura_erro else
                                   {"y": float(len(perda)), "n": n_px, "motivo": None, "eventos": None,
                                    "contagem": len(perda)})

    # P-4 da V-W5 (G3 de 28/10): as taxas com zeros vao em diferenca; a razao com exclusao e sensibilidade
    for nome in (DESFECHO_ERRO, DESFECHO_FALHAS):
        if nome in out:
            out[nome + SUFIXO_RAZAO] = dict(out[nome])

    # recuperacao (plano 3.4): anomalo = ERROR_TRANSPORT/ERROR_UNAVAILABLE ou acao com falha percebida, no
    # INICIO da operacao (P-2 da V-W5); fim da medicao = t_fim_carga_ns do protocolo, sem reserva (P-3)
    t_carga = _int_ou_none(resumo.get("t_fim_carga_ns")) if resumo is not None else None
    fim_med, origem_fim = (t_carga, "t_fim_carga_ns") if t_carga is not None else (None, None)
    anomalos = [e[0] for e in eventos if e[3] in ERROS] + [a[0] for a in (acoes or []) if a[4]]
    y, cens, motivo = delta_rec(anomalos, mk["fim"], fim_med, w_analise_ns)
    out[DESFECHO_DREC] = dict(_ausente(motivo) if y is None else {"y": y, "n": 1, "motivo": None, "eventos": None},
                              censurada=cens)
    if referencia_erro is not None:
        if not eventos:
            out[DESFECHO_DREC_MET] = dict(_ausente("sem CSV de eventos"), censurada=None)
        else:
            y, cens, motivo = delta_rec_metodologia([(e[0], e[3] in ERROS) for e in eventos], mk["fim"], fim_med,
                                                    w_analise_ns, referencia_erro["p_ref_sup"])
            out[DESFECHO_DREC_MET] = dict(_ausente(motivo) if y is None else
                                          {"y": y, "n": 1, "motivo": None, "eventos": None}, censurada=cens)

    # convergencia (plano 3.4; P-6 da V-W5): Delta_conv a partir de t_religamento_ns (topologia completa de volta
    # depois do religamento do no parado pelo AC-5) ou, sem no parado, do fim da carga; nunca t_fim_ns. AC-5
    # aplicado sem o registro: o laco
    # nunca ve todos os nos (ConvergenciaLoop.java:20-24), entao Delta_conv e divergencia nao sao avaliaveis.
    trilha = ler_trilha_decisoes(d / ("decisoes-rep-%s.jsonl" % nnn))
    com_ac5 = ac5_aplicada(trilha)
    origem_conv = None
    if resumo is None:
        out[DESFECHO_DCONV] = dict(_ausente("sem resumo de convergencia"), origem=None)
        out[DESFECHO_DIV] = _ausente("sem resumo de convergencia")
    else:
        dv = resumo.get("divergencia_persistente")
        t_conv = _int_ou_none(resumo.get("t_convergencia_abs_ns"))
        bruto_relig = resumo.get(CAMPO_RELIGAMENTO)
        t_relig = _int_ou_none(bruto_relig)
        if bruto_relig is not None and t_relig is None:
            motivo = "%s invalido no resumo: %r" % (CAMPO_RELIGAMENTO, bruto_relig)
            out[DESFECHO_DCONV] = dict(_ausente(motivo), origem=None)
            out[DESFECHO_DIV] = _ausente(motivo)
        elif com_ac5 and t_relig is None:
            motivo = ("AC-5 parou um no e o resumo nao tem %s (religamento do W-6): nao avaliavel"
                      % CAMPO_RELIGAMENTO)
            out[DESFECHO_DCONV] = dict(_ausente(motivo), origem=None)
            out[DESFECHO_DIV] = _ausente(motivo)
        else:
            out[DESFECHO_DIV] = (_ausente("sem divergencia_persistente no resumo") if not isinstance(dv, bool)
                                 else {"y": 1.0 if dv else 0.0, "n": 1, "motivo": None, "eventos": None})
            origem, t_origem = (CAMPO_RELIGAMENTO, t_relig) if t_relig is not None else ("t_fim_carga_ns", t_carga)
            if dv is True:
                motivo = "divergencia persistente (entra no desfecho binario)"
            elif t_carga is None:
                motivo = "sem t_fim_carga_ns no resumo (t_fim_ns nao e usado)"
            elif t_relig is not None and t_relig < t_carga:
                motivo = "%s antes de t_fim_carga_ns" % CAMPO_RELIGAMENTO
            elif t_conv is None:
                motivo = "sem t_convergencia_abs_ns no resumo"
            elif t_conv < t_origem:
                motivo = "t_convergencia_abs_ns antes da origem (%s)" % origem
            else:
                motivo = None
            if motivo is None:
                origem_conv = origem
                out[DESFECHO_DCONV] = {"y": (t_conv - t_origem) / 1e9, "n": 1, "motivo": None, "eventos": None,
                                       "origem": origem}
            else:
                out[DESFECHO_DCONV] = dict(_ausente(motivo), origem=None)

    info = {"n_eventos": len(ev_sel), "n_acoes": n_acoes, "steal_excluida": excluida,
            "energia_presente": en_path.exists(), "violacoes": viol, "violacoes_detalhe": apur,
            "n_violacoes_base": n_acoes if n_acoes is not None else len(ev_sel),
            "fim_medicao_ns": fim_med, "fim_medicao_origem": origem_fim,
            "t_inicio_falha_ns": mk["inicio"], "t_fim_falha_ns": mk["fim"],
            "ac5_aplicada": com_ac5, "delta_conv_origem": origem_conv,
            "execucao_json": ej, "fusao": fusao,
            "controlador": None if trilha is None else descrever_controlador(trilha, mk["inicio"], fim_med),
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


def _escala(nome):
    """Escala de d_b do desfecho: diferenca (taxas com zeros, V, Delta_rec, Delta_conv, divergencia, a sensibilidade
    de V e as contagens de sids do W-5e) ou log da razao."""
    if nome in ESCALA_DIFERENCA or nome in ESCALA_DIFERENCA_SENS_V or nome in ESCALA_DIFERENCA_W5E:
        return ESCALA_DIF
    return ESCALA_LOG


def familia_de(nome, razao_central=None):
    """Familia do desfecho no /2 (plano de alinhamento 3.1; W-5)."""
    if nome == DESFECHO_V:
        return FAMILIA_V
    if nome == DESFECHO_L:
        return FAMILIA_L
    if nome == DESFECHO_NI:
        return FAMILIA_NI
    if nome == DESFECHO_DREC_MET or nome == DESFECHO_V_SEM_F2 or nome.endswith(SUFIXO_RAZAO):
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


def _instrumentos(det):
    """(sonda ligada, varredura pedida) de uma execucao; None sem violacoes-rep."""
    if det is None:
        return None
    return bool(det["sonda"]), det["varredura"] != "ausente"


def _instrumentos_divergentes(blocos, infos, bracos=BRACOS):
    """Blocos em que os bracos tem instrumentos de observacao diferentes (pre-registro do benchmark refeito, 2.1)."""
    out = []
    for b in blocos:
        vals = [x for x in (_instrumentos(infos[b][br]["violacoes_detalhe"]) for br in bracos) if x is not None]
        if len(set(vals)) > 1:
            out.append(b)
    return out


def _detalhe_v_celula(blocos, infos, divergentes, afetados=(), sens_varredura=None, bracos=BRACOS):
    """Descritivas de V por braco (W-5c): linhas, pares por origem, atribuicao, formas de I1, custo da sonda.

    Sem teste: a contagem de V (pares distintos) ja entra no desfecho e no co_principal_v; aqui ficam as somas por
    braco e o detalhe de cada execucao (por_execucao, com a lista dos pares).
    """
    totais = {}
    for br in bracos:
        ds = [infos[b][br]["violacoes_detalhe"] for b in blocos if infos[b][br]["violacoes_detalhe"] is not None]
        if not ds:
            totais[br] = None
            continue
        t = {"execucoes": len(ds),
             "execucoes_com_sonda": sum(1 for x in ds if x["sonda"]),
             "execucoes_com_varredura": sum(1 for x in ds if x["varredura"] == "presente"),
             "execucoes_varredura_com_erro": sum(1 for x in ds if x["varredura"] == "erro"),
             "execucoes_tardio_no_teto": sum(1 for x in ds if x["sonda_tardia_no_teto"]),
             "total_pares": sum(x["total_pares"] for x in ds),
             "total_linhas": sum(x["total_linhas"] for x in ds),
             "pares": {k: sum(x["pares"][k] for x in ds) for k in INVARIANTES_VIOL},
             "linhas": {k: sum(x["linhas"][k] for x in ds) for k in INVARIANTES_VIOL},
             "por_origem": {o: {m: sum(x["por_origem"][o][m] for x in ds) for m in ("linhas", "pares")}
                            for o in ORIGENS},
             "pares_multiplas_origens": sum(x["pares_multiplas_origens"] for x in ds),
             "atribuicao": {a: sum(x["atribuicao"][a] for x in ds) for a in ATRIBUICOES},
             "formas_i1": {f: sum(x["formas_i1"][f] for x in ds) for f in FORMAS_I1},
             "total_pares_sem_forma2": (None if any(x["total_pares_sem_forma2"] is None for x in ds)
                                        else sum(x["total_pares_sem_forma2"] for x in ds)),
             "varredura_fora_de_v": sum(x["varredura_fora_de_v"] for x in ds),
             "replay_validate": {k: sum(x["replay_validate"][k] for x in ds)
                                 for k in ("eventos", "imediatos", "tardios", "sids_tardios")}}
        totais[br] = t
    por_exec = {b: {br: infos[b][br]["violacoes_detalhe"] for br in bracos} for b in blocos}
    vc = {"regra": REGRA_VARREDURA_ERRO, "blocos": len(afetados),
          "lista": list(afetados), "sensibilidade_blocos_excluidos": sens_varredura}
    if tuple(bracos) == BRACOS:
        vc.update({"so_A": sum(1 for x in afetados if x["bracos_com_erro"] == ["A"]),
                   "so_B": sum(1 for x in afetados if x["bracos_com_erro"] == ["B"]),
                   "nos_dois": sum(1 for x in afetados if len(x["bracos_com_erro"]) == 2)})
    else:
        vc["por_braco"] = {br: sum(1 for x in afetados if br in x["bracos_com_erro"]) for br in bracos}
    return {"regra": REGRA_V, "totais": totais, "instrumentos_divergentes": list(divergentes),
            "varredura_com_erro": vc, "por_execucao": por_exec}


def _aplicar_varredura_simetrica(blocos, res, infos, bracos=BRACOS):
    """W-5d (V-W5c, secao 5): no bloco em que a varredura falhou em algum braco, V e :sem_forma2 dos DOIS bracos
    passam aos totais sem a varredura (online e sonda); o bloco fica no teste. Troca tambem as contagens por
    invariante (info["violacoes"]). Devolve a lista dos blocos afetados, com o braco da falha e os dois V de cada
    braco (com a varredura, None no braco em que ela falhou; sem ela). W-5e: vale para os quatro bracos do bloco do
    fatorial, e a perda permanente do bloco fica nao avaliavel em todos os bracos (DT-027, 5.3)."""
    afetados = []
    for b in blocos:
        dets = {br: infos[b][br]["violacoes_detalhe"] for br in bracos}
        erro = [br for br in bracos if dets[br] is not None and dets[br]["varredura"] == "erro"]
        if not erro:
            continue
        v = {}
        for br in bracos:
            if DESFECHO_PERDA in res[b][br]:
                res[b][br][DESFECHO_PERDA] = dict(_ausente("varredura com erro no bloco (bracos %s): perda permanente "
                                                           "nao avaliavel" % ",".join(erro),
                                                           res[b][br][DESFECHO_PERDA]["n"]), contagem=None)
            det, r, n = dets[br], res[b][br].get(DESFECHO_V), infos[b][br]["n_acoes"]
            com, sem = (None if r is None else r["y"]), None
            if det is not None and r is not None and n is not None:
                c = det["total_pares_sem_varredura"]
                res[b][br][DESFECHO_V] = dict(_taxa(c, n, 1000.0), contagem=c, linhas=det["total_linhas_sem_varredura"])
                sem = res[b][br][DESFECHO_V]["y"]
                if DESFECHO_V_SEM_F2 in res[b][br]:
                    c2 = det["total_pares_sem_forma2_sem_varredura"]
                    res[b][br][DESFECHO_V_SEM_F2] = (dict(_ausente(det["formas_motivo"], n), contagem=None)
                                                     if c2 is None else dict(_taxa(c2, n, 1000.0), contagem=c2))
                infos[b][br]["violacoes"] = dict(det["pares_sem_varredura"])
            v[br] = {"com_varredura": com, "sem_varredura": sem}
        afetados.append({"bloco": b, "bracos_com_erro": erro, "v": v})
    return afetados


def _sensibilidade_sem_blocos_varredura_erro(afetados, usados_v, res, semente, indice, n_boot):
    """Sensibilidade descritiva de V sem os blocos com varredura com erro (o comportamento do W-5c): estimativa,
    IC t e IC bootstrap de blocos na escala diferenca, sem p e sem Holm. None sem bloco afetado."""
    if not afetados:
        return None
    excl = [x["bloco"] for x in afetados]
    bl = [b for b in usados_v if b not in excl]
    an = analisar_desfecho([res[b]["A"][DESFECHO_V]["y"] for b in bl], [res[b]["B"][DESFECHO_V]["y"] for b in bl],
                           semente, indice, n_boot, escala=ESCALA_DIF, permutar=False)
    return {"desfecho": DESFECHO_V, "familia": FAMILIA_SENS, "escala": ESCALA_DIF, "indice_semente": indice,
            "blocos": bl, "blocos_excluidos": excl, "n_blocos": an["n_blocos"], "estimativa": an["estimativa"],
            "ic_t": an["ic_t"], "ic_boot_blocos": an["ic_boot_blocos"], "p_permutacao": None, "p_holm": None,
            "d_b": dict(zip(bl, an["d_b"])), "nota": "descritiva: sem p e sem Holm"}


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
        # R-7 da V-W5: com menos de dois blocos o IC fica None, como em ic_boot_blocos
        ic = [float(x) for x in np.percentile(razoes, [2.5, 97.5])] if m >= 2 else None
        sens["a_razao_exclusao_zero_a"] = {"blocos": m, "estimativa": float(est), "ic_boot_blocos": ic}
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


def _resumo_controlador(blocos, infos, bracos=BRACOS):
    """Descritivos do controlador por bloco e somas por braco (sem teste)."""
    por_bloco = {b: {br: infos[b][br]["controlador"] for br in bracos} for b in blocos}
    totais = {}
    for br in bracos:
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
             "execucoes_com_ac5_aplicada": sum(1 for x in ds if x["ac5_aplicada"]),
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
                # tempo ativo None (acao ativa no fim sem t_fim_carga_ns) deixa a soma None
                pt["tempo_ativo_s"] = (None if pt["tempo_ativo_s"] is None or v["tempo_ativo_s"] is None
                                       else pt["tempo_ativo_s"] + v["tempo_ativo_s"])
                pt["execucoes_ativa_no_fim"] += 1 if v["ativa_no_fim"] else 0
        t["acoes_por_tipo"] = por_tipo
        for nome in ("atrasos_entrada_s", "atrasos_primeira_acao_s"):
            vals = [v for v in t[nome] if v is not None]
            t[nome.replace("atrasos_", "mediana_atraso_")] = float(np.median(vals)) if vals else None
        totais[br] = t
    return {"por_bloco": por_bloco, "totais": totais}


def _rastreabilidade_celula(blocos, infos, bracos=BRACOS):
    out = {}
    for br in bracos:
        sem = [b for b in blocos if infos[b][br]["rastreabilidade"] is None]
        probs = {b: infos[b][br]["rastreabilidade"]["problemas"] for b in blocos
                 if infos[b][br]["rastreabilidade"] is not None and not infos[b][br]["rastreabilidade"]["integral"]}
        out[br] = {"integral": not sem and not probs, "execucoes_sem_trilha": sem, "problemas": probs}
    return out


def _conferir_trilhas_do_teste(celula, blocos, infos):
    """A-3 da V-W5: na celula de teste, trilha nos dois bracos de cada bloco; braco B que atua de fato
    (atuador != nulo, politica != nenhuma, contrato v1.2, linha meta); mesmo param_sha256 em A e B."""
    for b in blocos:
        ctl = {br: infos[b][br]["controlador"] for br in BRACOS}
        for br in BRACOS:
            if ctl[br] is None:
                raise ErroUso("celula de teste %s, bloco %s, braco %s sem decisoes-rep (trilha obrigatoria nos "
                              "dois bracos; execucao interrompida e refeita pela DT-014)" % (celula, b, br))
        atuador, politica = ctl["B"]["atuador"], ctl["B"]["politica"]
        if not atuador or atuador == ATUADOR_NULO or not politica or politica == POLITICA_NENHUMA:
            raise ErroUso("celula de teste %s, bloco %s: braco B com atuador %r e politica %r nao atua (na pratica "
                          "e um braco trocado)" % (celula, b, atuador, politica))
        sha_a, sha_b = ctl["A"]["param_sha256"], ctl["B"]["param_sha256"]
        if not sha_a or sha_a != sha_b:
            raise ErroUso("celula de teste %s, bloco %s: param_sha256 diferente entre A (%r) e B (%r)"
                          % (celula, b, sha_a, sha_b))


# ============================================================================= fatorial 2x2 (W-5e)

def _contraste(y, efeito):
    """Contraste por bloco da DT-027, secao 1, sobre {braco: y} (y ja no log para o p99)."""
    if efeito == "A1":
        return 0.5 * ((y["AO"] - y["PO"]) + (y["AL"] - y["PL"]))
    if efeito == "A4":
        return 0.5 * ((y["PL"] - y["PO"]) + (y["AL"] - y["AO"]))
    if efeito == "interacao":
        return (y["AL"] - y["AO"]) - (y["PL"] - y["PO"])
    x, ref = EFEITOS_SIMPLES[efeito]
    return y[x] - y[ref]


def _analisar_contraste(d, semente, indice, n_boot, log, permutar):
    """Estimativa (media de d; exp no log), IC t, IC bootstrap de blocos e p da troca de sinal de um contraste."""
    n = len(d)
    media = sum(d) / n if n else None
    k = k_nao_nulos(d)
    return {"n_blocos": n, "indice_semente": indice,
            "estimativa": None if media is None else (math.exp(media) if log else media),
            "ic_t": ic_t(d, exp=log), "ic_boot_blocos": ic_boot_blocos(d, semente, indice, n_boot, exp=log),
            "p_permutacao": permutacao_exata(d) if (permutar and n) else None, "p_holm": None,
            "k_nao_nulos": k, "p_minimo_atingivel": p_minimo_atingivel(k) if n else None}


def _conferir_braco_fatorial(celula, b, br, execucao, info):
    """O braco do manifesto tem de ser o do execucao.json (fusao e controller; bloco, se houver) e o da trilha."""
    ej = info["execucao_json"]
    onde = "celula fatorial %s, bloco %s, braco %s (%s)" % (celula, b, br, execucao)
    if ej is None:
        raise ErroUso("%s: sem execucao.json (o braco sai dos campos %s e %s)" % (onde, CAMPO_FUSAO, CAMPO_CONTROLLER))
    fus, ctl = ej.get(CAMPO_FUSAO), ej.get(CAMPO_CONTROLLER)
    if fus != FUSAO_DO_BRACO[br]:
        raise ErroUso("%s: %s = %r no execucao.json (esperado %r)" % (onde, CAMPO_FUSAO, fus, FUSAO_DO_BRACO[br]))
    if ctl != MODO_FATORIAL[br]:
        raise ErroUso("%s: %s = %r no execucao.json (esperado %r)" % (onde, CAMPO_CONTROLLER, ctl, MODO_FATORIAL[br]))
    if ej.get("bloco") is not None and str(ej.get("bloco")) != str(b):
        raise ErroUso("%s: bloco %r no execucao.json" % (onde, ej.get("bloco")))
    tr = info["controlador"]
    if tr is not None and tr["modo"] != MODO_FATORIAL[br]:
        raise ErroUso("%s: trilha em modo %r (esperado %s)" % (onde, tr["modo"], MODO_FATORIAL[br]))


def _conferir_trilhas_do_teste_fatorial(celula, blocos, infos):
    """A-3 da V-W5 nos quatro bracos: trilha em todos; PL e AL atuam de fato; o mesmo param_sha256 nos quatro."""
    for b in blocos:
        ctl = {br: infos[b][br]["controlador"] for br in BRACOS_FATORIAL}
        for br in BRACOS_FATORIAL:
            if ctl[br] is None:
                raise ErroUso("celula de teste %s, bloco %s, braco %s sem decisoes-rep (trilha obrigatoria nos "
                              "quatro bracos; execucao interrompida e refeita pela DT-014)" % (celula, b, br))
        for br in BRACOS_ATIVOS_FATORIAL:
            atuador, politica = ctl[br]["atuador"], ctl[br]["politica"]
            if not atuador or atuador == ATUADOR_NULO or not politica or politica == POLITICA_NENHUMA:
                raise ErroUso("celula de teste %s, bloco %s: braco %s com atuador %r e politica %r nao atua"
                              % (celula, b, br, atuador, politica))
        shas = {br: ctl[br]["param_sha256"] for br in BRACOS_FATORIAL}
        if not shas["PO"] or len(set(shas.values())) != 1:
            raise ErroUso("celula de teste %s, bloco %s: param_sha256 diferente entre os bracos: %r" % (celula, b, shas))


def _co_principal_v_fatorial(efeito, ent, res, usados):
    """Bloco co_principal_v de um efeito principal: contagens por braco, avaliabilidade pelos bracos de
    referencia (sem violacao em nenhum bloco: H_V nao avaliavel), regra de tres, k e p minimo."""
    ref = REFERENCIA_EFEITO[efeito]
    out = {"desfecho": DESFECHO_V, "efeito": efeito, "bracos_de_referencia": list(ref), "n_blocos": len(usados)}
    if not usados:
        out.update({"avaliavel": False, "motivo": "nenhum bloco com a taxa nos quatro bracos"})
        return out
    c = {br: int(sum(res[b][br][DESFECHO_V]["contagem"] for b in usados)) for br in BRACOS_FATORIAL}
    n = {br: int(sum(res[b][br][DESFECHO_V]["n"] for b in usados)) for br in BRACOS_FATORIAL}
    out.update({"contagem": c, "n_acoes": n, "k_nao_nulos": ent["k_nao_nulos"],
                "p_minimo_atingivel": ent["p_minimo_atingivel"],
                "binomial_wilson_por_1000": {br: _wilson_por_1000(c[br], n[br]) for br in BRACOS_FATORIAL},
                "nota_independencia": "uma fusao produz varias violacoes de uma vez; a unidade e a execucao"})
    if sum(c[br] for br in ref) == 0:
        out["avaliavel"] = False
        out["motivo"] = ("bracos de referencia (%s) sem violacao em nenhum bloco: H_V nao avaliavel (nao e 'sem "
                         "efeito')" % ", ".join(ref))
    else:
        out["avaliavel"], out["motivo"] = True, None
    out["regra_de_tres"] = {}
    for br in BRACOS_FATORIAL:
        if c[br] == 0:
            lim, rot = regra_de_tres(n[br])
            out["regra_de_tres"][br] = {"limite_superior_95": lim, "rotulo": rot,
                                        "texto": "nao observado em %d acoes" % n[br]}
    return out


def _sensibilidade_fatorial_sem_blocos_varredura_erro(afetados, ent_v, semente, indice_base, n_boot):
    """Contrastes de V sem os blocos com varredura com erro (descritivos: sem p e sem Holm); None sem bloco afetado."""
    if not afetados or ent_v is None:
        return None
    excl = [x["bloco"] for x in afetados]
    bl = [b for b in ent_v["y_por_bloco"] if b not in excl]
    efeitos = {}
    for j, ef in enumerate(EFEITOS):
        d = [_contraste(ent_v["y_por_bloco"][b], ef) for b in bl]
        an = _analisar_contraste(d, semente, indice_base + j, n_boot, False, False)
        an["d_b"] = dict(zip(bl, d))
        efeitos[ef] = an
    return {"desfecho": DESFECHO_V, "familia": FAMILIA_SENS, "escala": ESCALA_DIF, "blocos": bl,
            "blocos_excluidos": excl, "efeitos": efeitos, "nota": "descritiva: sem p e sem Holm"}


def _comparar_celula_fatorial(celula, blocos, tipo, *, semente, rep, fases_sel, w_ns, w_an_ns, marcadores_nome, ref,
                              energia, delta_latencia, delta, n_boot, razao_c):
    """(celula, {"<celula>:A1": regra_iu, "<celula>:A4": regra_iu}) do fatorial 2x2 da DT-027.

    Le as quatro execucoes de cada bloco, confere o braco pelo execucao.json e pela trilha, aplica o tratamento
    simetrico da varredura com erro aos quatro bracos e analisa cada desfecho pelos contrastes da secao 1 (efeitos
    A1 e A4, interacao e os quatro efeitos simples). Indice de semente do contraste j do desfecho i: 10 i + j (A1,
    A4, interacao, depois os simples); a sensibilidade sem os blocos com varredura com erro usa 10 x (numero de
    desfechos) + j. Celula de teste: p da troca de sinal nos tres contrastes, Holm dos secundarios por efeito, IU
    por efeito principal; descritiva ou de referencia: so IC.
    """
    teste = tipo == "teste"
    res, infos = {}, {}
    for b, grupo in blocos.items():
        res[b], infos[b] = {}, {}
        for br in BRACOS_FATORIAL:
            res[b][br], infos[b][br] = desfechos_execucao(grupo[br], rep, fases_sel, w_ns, marcadores_nome,
                                                          w_analise_ns=w_an_ns, referencia_erro=ref, energia=energia)
    if all(infos[b][br]["n_eventos"] == 0 and not infos[b][br]["n_acoes"] for b in blocos for br in BRACOS_FATORIAL):
        raise ErroUso("celula %s sem nenhum evento nem acao nas fases pedidas (%s); celula sem falha: chamada "
                      "propria com --fases antes (A-2 da V-W5)" % (celula, ",".join(fases_sel)))
    for b, grupo in blocos.items():
        for br in BRACOS_FATORIAL:
            _conferir_braco_fatorial(celula, b, br, grupo[br], infos[b][br])
    if teste:
        _conferir_trilhas_do_teste_fatorial(celula, blocos, infos)
    divergentes = _instrumentos_divergentes(blocos, infos, BRACOS_FATORIAL)
    if teste and divergentes:
        raise ErroUso("celula de teste %s, bloco(s) %s: instrumentos de observacao diferentes entre os quatro bracos "
                      "(sonda de replay ou varredura; pre-registro do benchmark refeito, 2.1)"
                      % (celula, ",".join(divergentes)))
    afetados = _aplicar_varredura_simetrica(list(blocos), res, infos, BRACOS_FATORIAL)
    nomes = sorted({n for b in blocos for br in BRACOS_FATORIAL for n in res[b][br]})
    desf = {}
    for i, nome in enumerate(nomes):
        escala = _escala(nome)
        log = escala == ESCALA_LOG
        excl, ys = [], {}
        for b in blocos:
            motivo = None
            if nome == DESFECHO_CPU or nome.startswith(PREFIXO_ENERGIA):
                st = [infos[b][br]["steal_excluida"] for br in BRACOS_FATORIAL]
                motivo = "steal>5%" if True in st else ("steal-indisponivel" if None in st else None)
            if motivo is None:
                for br in BRACOS_FATORIAL:
                    r = res[b][br].get(nome)
                    if r is None:
                        motivo = "%s: desfecho ausente" % br
                    elif r["y"] is None:
                        motivo = "%s: %s" % (br, r["motivo"])
                    elif log and r["y"] <= 0:
                        motivo = "%s: valor zero (log indefinido)" % br
                    if motivo:
                        break
            if motivo:
                excl.append({"bloco": b, "motivo": motivo})
                continue
            ys[b] = {br: res[b][br][nome]["y"] for br in BRACOS_FATORIAL}
        usados = list(ys)
        efeitos, simples = {}, {}
        for j, ef in enumerate(list(EFEITOS) + list(EFEITOS_SIMPLES)):
            d = [_contraste({br: (math.log(v) if log else v) for br, v in ys[b].items()}, ef) for b in usados]
            an = _analisar_contraste(d, semente, 10 * i + j, n_boot, log, teste and ef in EFEITOS)
            an["d_b"] = dict(zip(usados, d))
            (efeitos if ef in EFEITOS else simples)[ef] = an
        if nome == DESFECHO_L:
            lim = math.log(1.0 + float(delta_latencia))
            for ef in EFEITOS_PRINCIPAIS:
                e = efeitos[ef]
                e["nao_inferioridade"] = {"delta": float(delta_latencia), "limite": 1.0 + float(delta_latencia),
                                          "limite_superior_ic_t": e["ic_t"][1] if e["ic_t"] else None,
                                          "p": p_nao_inferioridade(list(e["d_b"].values()), lim) if teste else None}
        if nome == DESFECHO_NI:
            for ef in EFEITOS_PRINCIPAIS:
                e = efeitos[ef]
                e["nao_inferioridade"] = {"delta": delta, "limite": 1.0 - delta,
                                          "limite_inferior_ic_t": e["ic_t"][0] if e["ic_t"] else None,
                                          "resultado": nao_inferioridade(e, delta)}
        ent = {"familia": familia_de(nome, razao_c), "escala": escala, "indice_semente": i, "n_blocos": len(usados),
               "blocos_excluidos": excl, "y_por_bloco": ys, "efeitos": efeitos, "efeitos_simples": simples}
        if nome in (DESFECHO_DREC, DESFECHO_DREC_MET):
            ent["censuradas"] = {br: sum(1 for b in blocos if (res[b][br].get(nome) or {}).get("censurada") is True)
                                 for br in BRACOS_FATORIAL}
        desf[nome] = ent
    rast = _rastreabilidade_celula(list(blocos), infos, BRACOS_FATORIAL)
    efp, regras = {}, {}
    for ef in EFEITOS_PRINCIPAIS:
        sec = ([n for n in nomes if desf[n]["familia"] == FAMILIA_SEC and desf[n]["efeitos"][ef]["p_permutacao"]
                is not None] if teste else [])
        for n, ph in zip(sec, holm([desf[n]["efeitos"][ef]["p_permutacao"] for n in sec])):
            desf[n]["efeitos"][ef]["p_holm"] = ph
        ev, el = desf.get(DESFECHO_V), desf.get(DESFECHO_L)
        cpv = (_co_principal_v_fatorial(ef, ev["efeitos"][ef], res, list(ev["y_por_bloco"])) if ev else
               {"desfecho": DESFECHO_V, "efeito": ef, "avaliavel": False, "motivo": "sem acoes S3"})
        efp[ef] = {"n_secundarios_holm": len(sec), "co_principal_v": cpv}
        if teste:
            evf = ev["efeitos"][ef] if ev else None
            elf = el["efeitos"][ef] if el else None
            vz = (desf.get(DESFECHO_NI, {}).get("efeitos", {}).get(ef, {}).get("nao_inferioridade", {})
                  .get("resultado", "inconclusivo"))
            rg = regra_iu(evf["p_permutacao"] if evf else None, evf["estimativa"] if evf else None,
                          cpv.get("avaliavel", False), elf["ic_t"] if elf else None,
                          elf["nao_inferioridade"]["p"] if elf else None, delta_latencia, vz,
                          all(rast[br]["integral"] for br in BRACOS_ATIVOS_FATORIAL))
            rg["bracos_rastreabilidade"] = list(BRACOS_ATIVOS_FATORIAL)
            efp[ef]["regra_iu"] = rg
            regras["%s:%s" % (celula, ef)] = rg
    sens = _sensibilidade_fatorial_sem_blocos_varredura_erro(afetados, desf.get(DESFECHO_V), semente,
                                                             10 * len(nomes), n_boot)
    excl_steal = sorted({b for b in blocos if True in [infos[b][br]["steal_excluida"] for br in BRACOS_FATORIAL]},
                        key=_chave_bloco)
    c = {"tipo": tipo, "teste": teste, "fatorial": True, "bracos": list(BRACOS_FATORIAL), "blocos": list(blocos),
         "regra": REGRA_FATORIAL,
         "execucoes": {b: {br: _rel(blocos[b][br]) for br in BRACOS_FATORIAL} for b in blocos},
         "blocos_excluidos_por_steal": excl_steal,
         "desfechos": desf,
         "efeitos_principais": efp,
         "violacoes": {"detalhe": _detalhe_v_celula(list(blocos), infos, divergentes, afetados, sens,
                                                    BRACOS_FATORIAL)},
         "fusao": {b: {br: infos[b][br]["fusao"] for br in BRACOS_FATORIAL} for b in blocos},
         "controlador": _resumo_controlador(list(blocos), infos, BRACOS_FATORIAL),
         "rastreabilidade": rast,
         "recuperacao": {"fim_medicao_origem": {b: {br: infos[b][br]["fim_medicao_origem"] for br in BRACOS_FATORIAL}
                                                for b in blocos},
                         "delta_conv_origem": {b: {br: infos[b][br]["delta_conv_origem"] for br in BRACOS_FATORIAL}
                                               for b in blocos},
                         "ac5_aplicada": {b: {br: infos[b][br]["ac5_aplicada"] for br in BRACOS_FATORIAL}
                                          for b in blocos}}}
    return c, regras


def comparar(manifesto, semente, *, delta_latencia, w_analise_s, w_recovery_s, tipos_celula, fases=FASES_PADRAO,
             rep=1, n_boot=N_BOOT_PADRAO, n_hier=N_HIER_PADRAO, delta=DELTA_PADRAO,
             marcadores_nome="marcadores.txt", referencias_erro=None, energia=None):
    """Dicionario do esquema comparar-bracos/2.

    delta_latencia, w_analise_s e w_recovery_s sao obrigatorios e sem padrao (pre-registro; P-5 da V-W5 para
    o W_recovery). tipos_celula: {celula: "teste"|"descritiva"|"referencia"} cobrindo exatamente as celulas
    do manifesto. referencias_erro: {celula: caminho de referencia-erro/1}. energia: {"p_max_w",
    "razao_central"} ou None.
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
    if (isinstance(w_recovery_s, bool) or not isinstance(w_recovery_s, (int, float)) or not math.isfinite(w_recovery_s)
            or w_recovery_s < 0):
        raise ErroUso("w_recovery_s deve ser numero >= 0 (sem valor padrao; o B-40 deriva do protocolo): %r"
                      % (w_recovery_s,))
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
        if tipos[celula] == "teste" and len(blocos) > N_MAX_PERMUTACAO:
            raise ErroUso("celula de teste %s com %d blocos: permutacao exata limitada a n <= %d blocos"
                          % (celula, len(blocos), N_MAX_PERMUTACAO))
    for celula, blocos in man.items():
        tipo = tipos[celula]
        teste = tipo == "teste"
        if set(next(iter(blocos.values()))) == set(BRACOS_FATORIAL):
            celulas[celula], rg = _comparar_celula_fatorial(
                celula, blocos, tipo, semente=semente, rep=rep, fases_sel=fases_sel, w_ns=w_ns, w_an_ns=w_an_ns,
                marcadores_nome=marcadores_nome, ref=refs.get(celula), energia=energia,
                delta_latencia=delta_latencia, delta=delta, n_boot=n_boot, razao_c=razao_c)
            regras.update(rg)
            continue
        res, infos = {}, {}
        # le a celula inteira antes das guardas, para que o erro de contrato de uma execucao apareca como tal
        for b, par in blocos.items():
            res[b], infos[b] = {}, {}
            for br in BRACOS:
                res[b][br], infos[b][br] = desfechos_execucao(par[br], rep, fases_sel, w_ns, marcadores_nome,
                                                              w_analise_ns=w_an_ns, referencia_erro=refs.get(celula),
                                                              energia=energia)
        if all(infos[b][br]["n_eventos"] == 0 and not infos[b][br]["n_acoes"] for b in blocos for br in BRACOS):
            raise ErroUso("celula %s sem nenhum evento nem acao nas fases pedidas (%s); celula sem falha: chamada "
                          "propria com --fases antes (A-2 da V-W5)" % (celula, ",".join(fases_sel)))
        for b, par in blocos.items():
            for br in BRACOS:
                ctl = infos[b][br]["controlador"]
                if ctl is not None and ctl["modo"] != MODO_ESPERADO[br]:
                    raise ErroUso("braco %s do bloco %s da celula %s com trilha em modo %r (esperado %s): %s"
                                  % (br, b, celula, ctl["modo"], MODO_ESPERADO[br], par[br]))
        if teste:
            _conferir_trilhas_do_teste(celula, blocos, infos)
        divergentes = _instrumentos_divergentes(blocos, infos)
        if teste and divergentes:
            raise ErroUso("celula de teste %s, bloco(s) %s: instrumentos de observacao diferentes entre A e B (sonda "
                          "de replay ou varredura; pre-registro do benchmark refeito, 2.1)" % (celula, ",".join(divergentes)))
        afetados_varr = _aplicar_varredura_simetrica(list(blocos), res, infos)
        nomes = sorted({n for b in blocos for br in BRACOS for n in res[b][br]})
        desf, usados_por = {}, {}
        for indice, nome in enumerate(nomes):
            escala = _escala(nome)
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
                                                    for b in blocos},
                             "delta_conv_origem": {b: {br: infos[b][br]["delta_conv_origem"] for br in BRACOS}
                                                   for b in blocos},
                             "ac5_aplicada": {b: {br: infos[b][br]["ac5_aplicada"] for br in BRACOS}
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
        sens_varr = _sensibilidade_sem_blocos_varredura_erro(afetados_varr, usados_por.get(DESFECHO_V, []), res,
                                                             semente, len(nomes) + len(INVARIANTES_VIOL) + 1, n_boot)
        c["violacoes"]["detalhe"] = _detalhe_v_celula(list(blocos), infos, divergentes, afetados_varr, sens_varr)
        celulas[celula] = c
    script = Path(__file__).resolve()
    return _f({"esquema": ESQUEMA,
               "parametros": {"semente_boot": int(semente), "n_boot": n_boot, "n_hier": n_hier,
                              "fases": fases_sel, "delta": delta, "rep": rep,
                              "w_recovery_s": float(w_recovery_s), "marcadores_nome": marcadores_nome,
                              "delta_latencia": float(delta_latencia), "w_analise_s": float(w_analise_s),
                              "alfa": ALFA, "quantil_linha_de_base": QUANTIL_LINHA_DE_BASE,
                              "co_principal_v": DESFECHO_V, "co_principal_l": DESFECHO_L,
                              "regra_v": REGRA_V, "v_sem_forma2": REGRA_V_SEM_F2,
                              "varredura_com_erro": REGRA_VARREDURA_ERRO,
                              "fatorial": {"regra": REGRA_FATORIAL, "bracos": list(BRACOS_FATORIAL),
                                           "fusao_do_braco": FUSAO_DO_BRACO, "controller_do_braco": MODO_FATORIAL,
                                           "campos_execucao_json": [CAMPO_FUSAO, CAMPO_CONTROLLER, "bloco",
                                                                    CAMPO_T_FUSAO],
                                           "perda_e_exposicao": REGRA_PERDA_EXPOSICAO},
                              "tipos_celula": dict(sorted(tipos.items())),
                              "referencias_erro": refs,
                              "energia": (energia if energia is None else
                                          {"p_max_w": energia["p_max_w"], "razao_central": energia["razao_central"]}),
                              "regra": "intersecao-uniao (Berger 1982; D-1); Holm entre celulas de teste sobre p_IU",
                              "delta_rec": "ultima operacao iniciada depois do fim da falha que terminou em "
                                           "anomalia (ERROR_TRANSPORT, ERROR_UNAVAILABLE, falha percebida); censura "
                                           "nos ultimos W_c = w_analise_s antes de t_fim_carga_ns; sem t_fim_carga_ns, "
                                           "ausente (plano 3.4; P-2 e P-3 da V-W5)",
                              "delta_conv": "t_convergencia_abs_ns - %s (topologia completa de volta depois do "
                                            "religamento do no parado pelo AC-5) ou "
                                            "- t_fim_carga_ns (sem no parado); AC-5 sem registro do religamento: "
                                            "nao avaliavel (plano 3.4; P-6 da V-W5)" % CAMPO_RELIGAMENTO,
                              "escala_diferenca": sorted(ESCALA_DIFERENCA),
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


def _markdown_fatorial(celula, c):
    """Linhas da tabela Markdown de uma celula do fatorial 2x2 (W-5e)."""
    linhas = ["## Celula %s (%s, fatorial 2x2, %d blocos)" % (celula, c["tipo"], len(c["blocos"])), "",
              "| Desfecho | Familia | Escala | Efeito | n blocos | Estimativa | IC t 95 % | IC boot blocos "
              "| p (permutacao) | p Holm | k != 0 | p minimo | Excluidos |",
              "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for nome, e in c["desfechos"].items():
        for ef in EFEITOS:
            x = e["efeitos"][ef]
            linhas.append("| `%s` | %s | %s | %s | %d | %s | %s | %s | %s | %s | %d | %s | %d |" % (
                nome, e["familia"], e["escala"], ef, x["n_blocos"], _fmt(x["estimativa"]), _fmt_ic(x["ic_t"]),
                _fmt_ic(x["ic_boot_blocos"]), _fmt(x["p_permutacao"]), _fmt(x["p_holm"]), x["k_nao_nulos"],
                _fmt(x["p_minimo_atingivel"]), len(e["blocos_excluidos"])))
    linhas += ["", "Contrastes por bloco (DT-027, secao 1): A1 = 1/2[(AO - PO) + (AL - PL)], A4 = 1/2[(PL - PO) + "
               "(AL - AO)], interacao = (AL - AO) - (PL - PO); estimativa em razao (exp) na escala log-razao. A "
               "interacao vai com IC e p, sem Holm e fora da intersecao-uniao."]
    if not c["teste"]:
        linhas.append("Celula %s (sem teste): IC descritivo, sem correcao de multiplicidade." % c["tipo"])
    for ef in EFEITOS_PRINCIPAIS:
        ep = c["efeitos_principais"][ef]
        r = ep.get("regra_iu")
        if r:
            linhas.append("- Efeito %s, intersecao-uniao: H_V %s (p = %s), H_L %s (limite superior do IC t = %s), "
                          "vazao %s, rastreabilidade de PL e AL %s -> p_IU = %s, **%s**%s." % (
                              ef, {True: "rejeitada", False: "nao rejeitada", None: "nao avaliavel"}[r["h_v"]["rejeitada"]],
                              _fmt(r["h_v"]["p"]),
                              {True: "rejeitada", False: "nao rejeitada", None: "nao avaliavel"}[r["h_l"]["rejeitada"]],
                              _fmt(r["h_l"]["limite_superior_ic_t"]), r["vazao"],
                              "integral" if r["rastreabilidade_integral"] else "incompleta", _fmt(r["p_iu"]),
                              r["resultado"], (" (" + "; ".join(r["motivos"]) + ")") if r["motivos"] else ""))
        cpv = ep["co_principal_v"]
        if cpv.get("avaliavel") is False and cpv.get("motivo"):
            linhas.append("- Efeito %s, taxa de violacoes: %s." % (ef, cpv["motivo"]))
        linhas.append("- Efeito %s: %d secundarios com Holm." % (ef, ep["n_secundarios_holm"]))
    for nome in (DESFECHO_V, DESFECHO_L):
        e = c["desfechos"].get(nome)
        if e:
            linhas.append("- `%s`, efeitos simples (so IC, sem teste): %s." % (nome, "; ".join(
                "%s %s %s" % (ef, _fmt(x["estimativa"]), _fmt_ic(x["ic_t"])) for ef, x in e["efeitos_simples"].items())))
    vc = c["violacoes"]["detalhe"]["varredura_com_erro"]
    txt = ("- Blocos com varredura com erro: %d bloco(s) (por braco da falha: %s); V e :sem_forma2 dos quatro bracos "
           "sem a varredura, perda permanente do bloco nao avaliavel" % (
               vc["blocos"], ", ".join("%s %d" % kv for kv in vc["por_braco"].items())))
    sv = vc.get("sensibilidade_blocos_excluidos")
    if sv:
        txt += "; sem esses blocos (descritiva): %s" % "; ".join(
            "%s %s" % (ef, _fmt(x["estimativa"])) for ef, x in sv["efeitos"].items())
    linhas += [txt + ".", ""]
    return linhas


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
        cab = ("Holm entre as hipoteses de teste: celulas de 2 bracos e efeitos principais do fatorial"
               if any(":" in k for k in g["celulas"]) else "Holm entre celulas de teste")
        linhas += ["## Conclusao global (%s, m = %d)" % (cab, g["m"]), "",
                   "| Celula | Resultado da celula | p_IU | p_IU Holm | Melhoria (FWER %s) |" % _fmt(g["alfa"], 2),
                   "|---|---|---|---|---|"]
        for c, x in g["celulas"].items():
            linhas.append("| %s | %s | %s | %s | %s |" % (c, x["resultado_celula"], _fmt(x["p_iu"]),
                                                         _fmt(x["p_iu_holm"]), "sim" if x["melhoria"] else "nao"))
        linhas.append("")
    for celula, c in resultado["celulas"].items():
        if c.get("fatorial"):
            linhas += _markdown_fatorial(celula, c)
            continue
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
        if not c["teste"]:
            # R-8 da V-W5
            linhas.append("Celula %s (sem teste): IC descritivo, sem correcao de multiplicidade; um IC que exclui 1 "
                          "(razao) ou 0 (diferenca) nao e resultado de teste." % c["tipo"])
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
        dv = vi.get("detalhe") or {}
        if dv.get("totais"):
            linhas.append("- Regra de V (pre-registro do benchmark refeito, 2.3): pares distintos (chave, invariante) "
                          "por execucao, somando online, sonda e varredura REAL; as linhas sao so descritivas.")
            for br in BRACOS:
                t = dv["totais"].get(br)
                if not t:
                    continue
                po, fm = t["por_origem"], t["formas_i1"]
                linhas.append(
                    "- V, braco %s: %d pares em %d linhas (pares/linhas: online %d/%d, sonda %d/%d, varredura %d/%d; "
                    "%d pares em mais de uma origem); I1 por forma: forma 1 = %d, forma 2 = %d, outra = %d, "
                    "indeterminada = %d; sem a forma 2: %s pares; sonda em %d de %d execucoes, varredura em %d (%d "
                    "com erro); ReplayValidate: %d eventos (%d tardios)%s." % (
                        br, t["total_pares"], t["total_linhas"], po["online"]["pares"], po["online"]["linhas"],
                        po["sonda"]["pares"], po["sonda"]["linhas"], po["varredura"]["pares"],
                        po["varredura"]["linhas"], t["pares_multiplas_origens"], fm[FORMA_1], fm[FORMA_2],
                        fm[FORMA_OUTRA], fm[FORMA_INDET],
                        "—" if t["total_pares_sem_forma2"] is None else "%d" % t["total_pares_sem_forma2"],
                        t["execucoes_com_sonda"], t["execucoes"], t["execucoes_com_varredura"],
                        t["execucoes_varredura_com_erro"], t["replay_validate"]["eventos"],
                        t["replay_validate"]["tardios"],
                        ("; replay tardio no teto em %d execucao(oes)" % t["execucoes_tardio_no_teto"])
                        if t["execucoes_tardio_no_teto"] else ""))
            vc = dv.get("varredura_com_erro") or {}
            if vc:
                txt = ("- Blocos com varredura com erro: %d bloco(s) (so A: %d, so B: %d, nos dois: %d); V e "
                       ":sem_forma2 dos dois bracos sem a varredura, bloco no teste" % (
                           vc["blocos"], vc["so_A"], vc["so_B"], vc["nos_dois"]))
                sv = vc.get("sensibilidade_blocos_excluidos")
                if sv:
                    txt += ("; sensibilidade sem esses blocos (descritiva, sem Holm): %d blocos, estimativa %s, IC t %s"
                            % (sv["n_blocos"], _fmt(sv["estimativa"]), _fmt_ic(sv["ic_t"])))
                linhas.append(txt + ".")
            if dv.get("instrumentos_divergentes"):
                linhas.append("- Instrumentos diferentes entre A e B nos blocos %s."
                              % ", ".join(dv["instrumentos_divergentes"]))
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
    ap.add_argument("--w-recovery-s", type=float, required=True)   # P-5 da V-W5: sem padrao
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
