"""Leitura do criterio h4 do rebaseline: travamento de I/O ou pausa da VM (DT-018).

Pre-registro vinculante: docs/pre-registro-rebaseline-complemento-3-2026-11-04.md (C3, secoes citadas como
"C3 §n"). Os limiares sao constantes nomeadas abaixo e NAO opcoes de linha de comando. O h4 olha so a
infraestrutura (o arquivo do scripts/amostrador-infra.sh e o execucao.json do runner); nao le latencia nem
nenhum desfecho do workload.

Subcomandos:
  fase  --amostras <arq> [--amostras <arq2> ...] --saida <SAIDA> --fase piloto|noite1|noite2|reposicoes
        veredito h4 de cada execucao terminada da fase (C3 §3), parada h4 (C3 §4.3, regras 1 e 2) e
        episodios da fase (C3 §6). Grava <SAIDA>/h4/h4-<fase>.tsv e .json.
        Exit 0 segue, 30 parada h4, 2 uso, 4 arquivo de amostras ilegivel.
  repor --saida <SAIDA> --matriz <matriz.tsv> [--dry-run]
        acrescenta a <SAIDA>/reposicoes/reposicoes.tsv uma linha por execucao h4 ACEITA ainda nao reposta
        (C3 §4.1), com o proximo i livre e a semente S0 + 100*i. Exit 0, 2 uso, 3 recusa (teto, ordem).
  visao --saida <SAIDA> --destino <dir novo> [--excluir <arq com um i por linha>]
        arvore com hard links das execucoes que nao sao h4 (para rebaseline.py tabelas). Nunca escreve
        em <SAIDA>. Exit 0, 2 uso, 4 copia que nao confere.

So biblioteca padrao; roda com python3 -I (na VM e no host).
"""
import argparse
import bisect
import csv
import hashlib
import json
import os
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

# ----------------------------------------------------------------------------- constantes (pre-registro)
VERSAO = "h4/1"
JANELA_S = 10.0
LIMIAR_H4A_S = 1.5
LIMIAR_H4B_S = 3.0
LIMIAR_SALTO_S = 1.0
MEDICAO_INI_S = 44.0          # PR §2 / DT-011: ancora + 44 s
MEDICAO_FIM_S = 300.0         # ancora + 300 s, ou fim_utc se antes
SEG_F1_ANTES_S = 2.0
SEG_F1_DEPOIS_S = 60.0
F1_REINICIO_PADRAO_S = 109.0  # se falha_efetiva.f1_reiniciado_desde_ancora_s for null
COBERTURA_FOLGA_S = 3.0       # h4c: falta de amostra no inicio ou no fim da janela h4
PARADA_JANELA_EXEC = 12
PARADA_MIN_H4 = 5
PARADA_SEGUIDAS = 4
TETO_REPOSICOES_H4 = 30
S0 = 50000; PASSO_SEMENTE = 100; I_REPOSICAO_INICIAL = 120; I_PILOTO = 1000

# Toda a aritmetica de tempo e em microssegundos inteiros (sem erro de ponto flutuante nas fronteiras).
US = 1000000


def _us(seg):
    return int(round(seg * US))


JANELA_US = _us(JANELA_S)
LIMIAR_H4A_US = _us(LIMIAR_H4A_S)
LIMIAR_H4B_US = _us(LIMIAR_H4B_S)
LIMIAR_SALTO_US = _us(LIMIAR_SALTO_S)
MEDICAO_INI_US = _us(MEDICAO_INI_S)
MEDICAO_FIM_US = _us(MEDICAO_FIM_S)
SEG_F1_ANTES_US = _us(SEG_F1_ANTES_S)
SEG_F1_DEPOIS_US = _us(SEG_F1_DEPOIS_S)
COBERTURA_FOLGA_US = _us(COBERTURA_FOLGA_S)

FASES = ("piloto", "noite1", "noite2", "reposicoes")
FASES_REPOR = ("noite1", "noite2", "reposicoes")   # o piloto nunca e reposto (C3 §4.1)
# fase do execucao.json (coluna "fase" da matriz que o runner le) para cada fase do runner
CAMPO_FASE = {"piloto": "piloto", "noite1": "campanha", "noite2": "campanha", "reposicoes": "reposicao"}

COLUNAS = ("i", "celula", "falha", "lancamento_utc", "ancora_utc", "janela_ini_utc", "janela_fim_utc",
           "n_amostras", "pior_janela_s", "pior_janela_fim_utc", "pior_janela_seg_f1_s", "seg_f1_ini_utc",
           "seg_f1_fim_utc", "maior_intervalo_s", "maior_intervalo_utc", "maior_salto_s", "cobertura", "veredito")
CABECALHO_REPOSICOES = "i\tfase\tnoite\trodada\tposicao\tcelula\tcenario\tmodo\tfalha\tseed\n"   # = runner
COLUNAS_REGISTRO = ("i_h4", "celula", "i_reposicao", "carimbo")

EXIT_OK, EXIT_USO, EXIT_RECUSA, EXIT_ARQUIVO, EXIT_PARADA = 0, 2, 3, 4, 30


class ErroUso(Exception):
    """Uso invalido: exit 2."""


class ErroArquivo(Exception):
    """Arquivo de amostras ilegivel (ausente, sem cabecalho, malformado, boot_id misturado): exit 4."""


class Recusa(Exception):
    """repor recusado (teto de reposicoes h4, recusa do codigo ainda nao reposta): exit 3."""


# ----------------------------------------------------------------------------- utilitarios

def utc(us):
    """microssegundos desde a epoca -> ISO-8601 UTC com 6 casas."""
    if us is None:
        return "-"
    s, r = divmod(int(us), US)
    return datetime.fromtimestamp(s, timezone.utc).replace(microsecond=r).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def seg(us):
    """microssegundos -> segundos com 6 casas (texto), ou '-'."""
    if us is None:
        return "-"
    sinal = "-" if us < 0 else ""
    q, r = divmod(abs(int(us)), US)
    return "%s%d.%06d" % (sinal, q, r)


def iso_para_us(txt):
    """'2026-11-05T01:24:58.123Z' (formato do runner) -> microssegundos."""
    m = re.match(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$", txt or "")
    if not m:
        raise ValueError("instante UTC invalido: %r" % txt)
    base = datetime.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc)
    frac = int((m.group(2) or "0").ljust(6, "0"))
    return int(base.timestamp()) * US + frac


def dec_para_us(txt):
    """'123.45' ou '1793834977.396553' -> microssegundos (ate 6 casas; o resto e truncado)."""
    ip, _, fp = txt.partition(".")
    return int(ip) * US + int((fp[:6]).ljust(6, "0"))


def sha256_arquivo(p):
    h = hashlib.sha256()
    with open(p, "rb") as fh:
        for b in iter(lambda: fh.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def gravar_atomico(p, texto):
    p = Path(p)
    tmp = p.with_name(p.name + ".tmp")
    tmp.write_bytes(texto.encode("utf-8"))
    os.replace(tmp, p)


def ler_json(p):
    try:
        return json.loads(Path(p).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


# ----------------------------------------------------------------------------- arquivo do amostrador (v1)

CAB_RE = re.compile(r"^# tcc-amostrador-infra v(\d+)(?: (.*))?$")
CHAVES_CAB = ("id", "inicio", "boot_id", "host", "disco", "periodo_s", "pid", "versao_script_sha256")
T_RE = re.compile(r"^\d+\.\d{6}$")
UP_RE = re.compile(r"^\d+\.\d{1,9}$")
INT_RE = re.compile(r"^\d+$")


def _linha_valida(toks, disco):
    if len(toks) < 13 or toks[2] != "io" or toks[5] != "cpu" or toks[8] != "mem":
        return False
    if not T_RE.match(toks[0]) or not UP_RE.match(toks[1]):
        return False
    if not (INT_RE.match(toks[3]) and INT_RE.match(toks[4]) and INT_RE.match(toks[6])
            and (INT_RE.match(toks[7]) or toks[7] == "-") and INT_RE.match(toks[9]) and INT_RE.match(toks[10])):
        return False
    if toks[11] != disco:
        return False
    resto = toks[12:]
    if resto == ["-"]:
        return True
    return 11 <= len(resto) <= 17 and all(INT_RE.match(x) for x in resto)


def ler_arquivo(caminho):
    """Um arquivo v1 -> (cabecalho dict, linha do cabecalho, [(t_us, up_us, io_full_us)], sha256).
    A ultima linha sem '\\n' (o amostrador esta escrevendo) e ignorada. ErroArquivo se ilegivel."""
    p = Path(caminho)
    try:
        dados = p.read_bytes()
    except OSError as ex:
        raise ErroArquivo("arquivo de amostras ausente ou ilegivel: %s (%s)" % (p, ex.strerror or ex))
    sha = hashlib.sha256(dados).hexdigest()
    try:
        txt = dados.decode("ascii")
    except UnicodeDecodeError:
        raise ErroArquivo("%s: bytes nao ASCII" % p)
    linhas = txt.split("\n")
    linhas.pop()   # o que vem depois do ultimo '\n': vazio, ou linha incompleta em escrita
    if not linhas:
        raise ErroArquivo("%s: sem cabecalho (arquivo vazio)" % p)
    m = CAB_RE.match(linhas[0])
    if not m:
        raise ErroArquivo("%s: linha 1 nao e o cabecalho 'tcc-amostrador-infra'" % p)
    if m.group(1) != "1":
        raise ErroArquivo("%s: versao do formato desconhecida (v%s)" % (p, m.group(1)))
    cab = {}
    for par in (m.group(2) or "").split(" "):
        k, igual, v = par.partition("=")
        if igual:
            cab[k] = v
    faltam = [k for k in CHAVES_CAB if not cab.get(k)]
    if faltam:
        raise ErroArquivo("%s: cabecalho sem %s" % (p, ", ".join(faltam)))
    amostras = []
    for n, l in enumerate(linhas[1:], start=2):
        if l.startswith("#"):
            if CAB_RE.match(l):
                raise ErroArquivo("%s:%d: segundo cabecalho no mesmo arquivo" % (p, n))
            continue
        toks = l.split(" ")
        if not _linha_valida(toks, cab["disco"]):
            raise ErroArquivo("%s:%d: linha malformada: %r" % (p, n, l[:120]))
        amostras.append((dec_para_us(toks[0]), dec_para_us(toks[1]), int(toks[4])))
    return cab, linhas[0], amostras, sha


class Serie:
    """Amostras de um ou mais arquivos do mesmo boot. Cada cabecalho distinto e um segmento (uma vida do
    amostrador); dois arquivos com o mesmo cabecalho (espelho e copia final) sao o mesmo segmento, e fica
    o mais longo. Os segmentos sao concatenados na ordem da primeira amostra."""

    def __init__(self, caminhos):
        if not caminhos:
            raise ErroUso("--amostras e obrigatorio")
        self.arquivos = []
        por_cab = {}
        boots = set()
        for c in caminhos:
            cab, linha_cab, am, sha = ler_arquivo(c)
            boots.add(cab["boot_id"])
            self.arquivos.append({"caminho": str(c), "sha256": sha, "cabecalho": linha_cab, "amostras": len(am)})
            if linha_cab not in por_cab or len(am) > len(por_cab[linha_cab][1]):
                por_cab[linha_cab] = (cab, am)
        if len(boots) > 1:
            raise ErroArquivo("arquivos de boots diferentes (boot_id %s)" % ", ".join(sorted(boots)))
        segs = sorted(por_cab.values(), key=lambda x: x[1][0][0] if x[1] else 0)
        self.t, self.up, self.full, self.seg = [], [], [], []
        self.segmentos = []
        for k, (cab, am) in enumerate(segs):
            self.segmentos.append({"id": cab["id"], "pid": cab["pid"], "inicio": cab["inicio"], "amostras": len(am),
                                   "t_ini_us": am[0][0] if am else None, "t_fim_us": am[-1][0] if am else None})
            for t, u, f in am:
                self.t.append(t)
                self.up.append(u)
                self.full.append(f)
                self.seg.append(k)
        self.n = len(self.t)
        # janela de 10 s terminando em cada amostra (C3 §3.2): j = amostra mais antiga com t_i - t_j <= 10 s
        self.janela = [0] * self.n
        j = 0
        for i in range(self.n):
            while j < i and self.t[i] - self.t[j] > JANELA_US:
                j += 1
            self.janela[i] = self.full[i] - self.full[j]
        # intervalo e salto de relogio que terminam em cada amostra (None na primeira)
        self.intervalo = [None] * self.n
        self.salto = [None] * self.n
        for i in range(1, self.n):
            dt = self.t[i] - self.t[i - 1]
            self.intervalo[i] = dt
            self.salto[i] = abs(dt - (self.up[i] - self.up[i - 1]))

    def horas(self):
        return sum((s["t_fim_us"] - s["t_ini_us"]) for s in self.segmentos if s["amostras"]) / (3600.0 * US)

    def episodios(self):
        """C3 §6: trechos continuos de amostras com janela >= 1,5 s ou intervalo >= 3 s, no arquivo inteiro.
        Intervalo entre segmentos (vidas diferentes do amostrador) nao conta."""
        eps = []
        atual = None
        for i in range(self.n):
            iv = self.intervalo[i] if i > 0 and self.seg[i] == self.seg[i - 1] else None
            marca = self.janela[i] >= LIMIAR_H4A_US or (iv is not None and iv >= LIMIAR_H4B_US)
            continua = atual is not None and i > 0 and self.seg[i] == self.seg[i - 1] and atual["ult"] == i - 1
            if marca:
                if not continua:
                    atual = {"pri": i, "ult": i, "pior_janela": 0, "maior_intervalo": 0}
                    eps.append(atual)
                atual["ult"] = i
                atual["pior_janela"] = max(atual["pior_janela"], self.janela[i])
                atual["maior_intervalo"] = max(atual["maior_intervalo"], iv or 0)
            else:
                atual = None
        return [{"ini_utc": utc(self.t[e["pri"]]), "fim_utc": utc(self.t[e["ult"]]),
                 "pior_janela_s": seg(e["pior_janela"]), "maior_intervalo_s": seg(e["maior_intervalo"])} for e in eps]


def analisar_serie(serie):
    """Resumo do arquivo inteiro (usado na recalibracao, C3 §3.4)."""
    pj = max(serie.janela) if serie.n else None
    mi = max((x for x in serie.intervalo if x is not None), default=None)
    return {"n": serie.n, "pior_janela_us": pj, "maior_intervalo_us": mi,
            "janelas_ge_h4a": sum(1 for x in serie.janela if x >= LIMIAR_H4A_US)}


# ----------------------------------------------------------------------------- uma execucao

def avaliar_execucao(serie, ex):
    """Veredito h4 de uma execucao (dict do execucao.json). Devolve a linha (valores ja formatados) e o
    veredito. 'sem-ancora' e 'sem-janela' nao sao h4."""
    lin = {c: "-" for c in COLUNAS}
    lin["i"] = str(ex.get("i"))
    lin["celula"] = ex.get("celula") or "-"
    falha = ex.get("falha") or "-"
    lin["falha"] = falha
    if ex.get("lancamento_ms") is not None:
        lin["lancamento_utc"] = utc(int(ex["lancamento_ms"]) * 1000)
    if ex.get("ancora_ms") is None:
        lin["n_amostras"] = "0"
        lin["veredito"] = "sem-ancora"
        lin["cobertura"] = "-"
        return lin
    anc = int(ex["ancora_ms"]) * 1000
    lin["ancora_utc"] = utc(anc)
    ini = anc + MEDICAO_INI_US
    fim = min(anc + MEDICAO_FIM_US, iso_para_us(ex["fim_utc"]))
    lin["janela_ini_utc"] = utc(ini)
    lin["janela_fim_utc"] = utc(fim)
    if fim <= ini:
        lin["n_amostras"] = "0"
        lin["veredito"] = "sem-janela"
        lin["cobertura"] = "workload terminou antes de ancora + %g s" % MEDICAO_INI_S
        return lin
    t = serie.t
    a = bisect.bisect_left(t, ini)
    b = bisect.bisect_right(t, fim)
    lin["n_amostras"] = str(max(0, b - a))
    codigos = []

    # h4c (C3 §3.3): falta amostra por mais de 3 s no inicio ou no fim da janela
    if b <= a:
        cob = "sem-amostras"
    else:
        partes = []
        if t[a] - ini > COBERTURA_FOLGA_US:
            partes.append("falta-inicio=%ss" % seg(t[a] - ini))
        if fim - t[b - 1] > COBERTURA_FOLGA_US:
            partes.append("falta-fim=%ss" % seg(fim - t[b - 1]))
        cob = ";".join(partes) if partes else "completa"
    lin["cobertura"] = cob
    if cob != "completa":
        codigos.append("h4c")

    # h4a com o segmento do F1 (C3 §4.2)
    seg_ini = seg_fim = None
    if falha == "F1":
        rein = ((ex.get("falha_efetiva") or {}).get("f1_reiniciado_desde_ancora_s"))
        rein_us = anc + (_us(float(rein)) if rein is not None else _us(F1_REINICIO_PADRAO_S))
        seg_ini, seg_fim = rein_us - SEG_F1_ANTES_US, rein_us + SEG_F1_DEPOIS_US
        lin["seg_f1_ini_utc"] = utc(seg_ini)
        lin["seg_f1_fim_utc"] = utc(seg_fim)
    pior = pior_t = pior_seg = None
    for i in range(a, b):
        v = serie.janela[i]
        if seg_ini is not None and seg_ini <= t[i] <= seg_fim:
            if pior_seg is None or v > pior_seg:
                pior_seg = v
        elif pior is None or v > pior:
            pior, pior_t = v, t[i]
    lin["pior_janela_s"] = seg(pior)
    lin["pior_janela_fim_utc"] = utc(pior_t)
    if falha == "F1":
        lin["pior_janela_seg_f1_s"] = seg(pior_seg)
    if pior is not None and pior >= LIMIAR_H4A_US:
        codigos.append("h4a")

    # h4b: intervalos (t_{i-1}, t_i] que tocam a janela, inclusive o que atravessa o inicio e o fim
    mi = mi_t = ms = None
    for i in range(max(a, 1), min(b, serie.n - 1) + 1):
        if i >= serie.n:
            break
        iv, sl = serie.intervalo[i], serie.salto[i]
        if mi is None or iv > mi:
            mi, mi_t = iv, t[i]
        if ms is None or sl > ms:
            ms = sl
    lin["maior_intervalo_s"] = seg(mi)
    lin["maior_intervalo_utc"] = utc(mi_t)
    lin["maior_salto_s"] = seg(ms)
    if (mi is not None and mi >= LIMIAR_H4B_US) or (ms is not None and ms >= LIMIAR_SALTO_US):
        codigos.append("h4b")

    lin["veredito"] = "+".join(sorted(codigos)) if codigos else "ok"
    return lin


def e_h4(veredito):
    return "h4" in (veredito or "")


def avaliar_parada(vereditos):
    """C3 §4.3, regras 1 e 2, sobre os vereditos NA ORDEM DE LANCAMENTO. Devolve (parada, motivos)."""
    motivos = []
    h = [e_h4(v) for v in vereditos]
    for k in range(len(h)):
        janela = h[max(0, k - PARADA_JANELA_EXEC + 1):k + 1]
        if sum(janela) >= PARADA_MIN_H4:
            motivos.append("regra 1: %d execucoes h4 entre as ultimas %d (ate a %d-esima da fase)"
                           % (sum(janela), len(janela), k + 1))
            break
    seguidas = 0
    for k, x in enumerate(h):
        seguidas = seguidas + 1 if x else 0
        if seguidas >= PARADA_SEGUIDAS:
            motivos.append("regra 1: %d execucoes h4 seguidas (ate a %d-esima da fase)" % (seguidas, k + 1))
            break
    c = [k + 1 for k, v in enumerate(vereditos) if "h4c" in (v or "")]
    if c:
        motivos.append("regra 2: h4c (cobertura incompleta) na(s) execucao(oes) %s da fase" % ",".join(map(str, c)))
    return bool(motivos), motivos


# ----------------------------------------------------------------------------- fase

def ler_tsv(p):
    with open(p, newline="", encoding="ascii") as fh:
        return list(csv.DictReader(fh, delimiter="\t"))


def execucoes_da_fase(saida, fase):
    """[(dir, execucao.json)] das execucoes TERMINADAS da fase (com fim_utc), fora as .abortada-*."""
    saida = Path(saida)
    campo = CAMPO_FASE[fase]
    noite = None
    if fase in ("noite1", "noite2"):
        mp = saida / "matriz.tsv"
        if not mp.is_file():
            raise ErroUso("%s ausente (a noite de cada i vem da matriz)" % mp)
        noite = {int(x["i"]): x["noite"] for x in ler_tsv(mp)}
    out = []
    em_curso = []
    for d in sorted(saida.glob("exec-*")):
        if not d.is_dir() or ".abortada-" in d.name:
            continue
        ex = ler_json(d / "execucao.json")
        if not isinstance(ex, dict):
            em_curso.append(d.name)   # sem execucao.json (em curso ou interrompida) ou em escrita
            continue
        if ex.get("fase") != campo:
            continue
        if noite is not None and noite.get(int(ex.get("i", -1))) != fase:
            continue
        if not ex.get("fim_utc"):
            em_curso.append(d.name)
            continue
        try:
            iso_para_us(ex["fim_utc"])
            if ex.get("ancora_ms") is not None:
                int(ex["ancora_ms"])
        except (TypeError, ValueError):
            raise ErroUso("%s/execucao.json com fim_utc ou ancora_ms invalido" % d.name)
        out.append((d, ex))
    return out, em_curso


def _chave_lancamento(par):
    ex = par[1]
    lm = ex.get("lancamento_ms")
    return (0, int(lm), int(ex["i"])) if lm is not None else (1, 0, int(ex["i"]))


def cmd_fase(a):
    if a.fase not in FASES:
        raise ErroUso("--fase deve ser piloto, noite1, noite2 ou reposicoes")
    saida = Path(a.saida)
    if not saida.is_dir():
        raise ErroUso("--saida inexistente: %s" % saida)
    serie = Serie(a.amostras)
    execs, em_curso = execucoes_da_fase(saida, a.fase)
    execs.sort(key=_chave_lancamento)
    linhas = [avaliar_execucao(serie, ex) for _d, ex in execs]
    parada, motivos = avaliar_parada([x["veredito"] for x in linhas])
    eps = serie.episodios()
    horas = serie.horas()
    por_hora = (len(eps) / horas) if horas > 0 else None

    destino = saida / "h4"
    destino.mkdir(exist_ok=True)
    tsv = "\t".join(COLUNAS) + "\n" + "".join("\t".join(x[c] for c in COLUNAS) + "\n" for x in linhas)
    gravar_atomico(destino / ("h4-%s.tsv" % a.fase), tsv)
    constantes = {k: globals()[k] for k in (
        "JANELA_S", "LIMIAR_H4A_S", "LIMIAR_H4B_S", "LIMIAR_SALTO_S", "MEDICAO_INI_S", "MEDICAO_FIM_S",
        "SEG_F1_ANTES_S", "SEG_F1_DEPOIS_S", "F1_REINICIO_PADRAO_S", "COBERTURA_FOLGA_S", "PARADA_JANELA_EXEC",
        "PARADA_MIN_H4", "PARADA_SEGUIDAS", "TETO_REPOSICOES_H4", "S0", "PASSO_SEMENTE", "I_REPOSICAO_INICIAL",
        "I_PILOTO")}
    h4s = [x for x in linhas if e_h4(x["veredito"])]
    js = {"versao": VERSAO, "fase": a.fase, "gerado_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
          "constantes": constantes, "arquivos": serie.arquivos,
          "segmentos": [{k: (utc(v) if k.startswith("t_") else v) for k, v in s.items()} for s in serie.segmentos],
          "execucoes": linhas, "execucoes_em_curso_ou_sem_execucao_json": em_curso,
          "contagem": {"execucoes": len(linhas), "h4": len(h4s)},
          "episodios": {"n": len(eps), "horas_observadas": round(horas, 6),
                        "por_hora": None if por_hora is None else round(por_hora, 6), "lista": eps},
          "parada": {"parada": parada, "motivos": motivos,
                     "nota": "regras 1 e 2 do C3 §4.3; a regra 3 (unit inativa) e do vigia-h4.sh"}}
    gravar_atomico(destino / ("h4-%s.json" % a.fase), json.dumps(js, indent=1, ensure_ascii=False) + "\n")

    for x in h4s:
        print("h4 i=%s %s falha=%s veredito=%s pior_janela=%ss maior_intervalo=%ss maior_salto=%ss cobertura=%s"
              % (x["i"], x["celula"], x["falha"], x["veredito"], x["pior_janela_s"], x["maior_intervalo_s"],
                 x["maior_salto_s"], x["cobertura"]))
    for m in motivos:
        print("PARADA H4: " + m)
    print("h4: %d de %d execucoes; episodios/h=%s; parada=%s"
          % (len(h4s), len(linhas), "-" if por_hora is None else "%.3f" % por_hora, "sim" if parada else "nao"))
    return EXIT_PARADA if parada else EXIT_OK


# ----------------------------------------------------------------------------- repor

def dir_exec(saida, i, celula):
    return Path(saida) / ("exec-%04d-%s" % (int(i), celula))


def proximo_i_livre(saida, linhas_tsv):
    """Mesma conta do runner (run-rebaseline.sh, fase reposicoes): 1 + o maior i entre as linhas do
    reposicoes.tsv e os exec-* (fora as .abortada-*) com 120 <= i < 1000; no minimo 120."""
    prox = I_REPOSICAO_INICIAL
    for d in Path(saida).glob("exec-*"):
        if not d.is_dir() or ".abortada-" in d.name:
            continue
        m = re.match(r"^exec-(\d+)-", d.name)
        if m:
            n = int(m.group(1))
            if I_REPOSICAO_INICIAL <= n < I_PILOTO and n >= prox:
                prox = n + 1
    for x in linhas_tsv:
        if re.match(r"^\d+$", x.get("i") or "") and int(x["i"]) >= prox:
            prox = int(x["i"]) + 1
    return prox


def cmd_repor(a):
    saida = Path(a.saida)
    if not saida.is_dir() or not Path(a.matriz).is_file():
        raise ErroUso("--saida ou --matriz inexistente")
    sm = saida / "matriz.tsv"
    if sm.is_file() and sha256_arquivo(sm) != sha256_arquivo(a.matriz):
        raise ErroUso("%s difere da matriz dada" % sm)
    celulas = {x["celula"] for x in ler_tsv(a.matriz)}
    rep_tsv = saida / "reposicoes" / "reposicoes.tsv"
    reg = saida / "h4" / "reposicoes-h4.tsv"
    linhas_tsv = ler_tsv(rep_tsv) if rep_tsv.is_file() else []
    registro = ler_tsv(reg) if reg.is_file() else []
    ja = {int(x["i_h4"]) for x in registro}

    # ordem do C3 §5: as recusas do codigo sao repostas pelo runner ANTES do h4.py repor (senao o runner
    # veria a celula no reposicoes.tsv e nao reporia a recusa)
    na_tsv = {x["celula"] for x in linhas_tsv}
    pend = []
    for c in sorted(saida.glob("exec-*/checagem.json")):
        if ".abortada-" in c.parent.name:
            continue
        ch = ler_json(c) or {}
        if ch.get("fase") == "campanha" and ch.get("veredito") == "RECUSADA" and ch.get("celula") not in na_tsv:
            pend.append("%s (i=%s)" % (ch.get("celula"), ch.get("i")))
    if pend:
        raise Recusa("recusa do codigo ainda nao reposta pelo runner: %s; rode antes "
                     "'run-rebaseline.sh --fase reposicoes' (C3 §5)" % ", ".join(pend))

    cands = []
    for fase in FASES_REPOR:
        p = saida / "h4" / ("h4-%s.tsv" % fase)
        if not p.is_file():
            continue
        for x in ler_tsv(p):
            if not e_h4(x["veredito"]):
                continue
            i, cel = int(x["i"]), x["celula"]
            if i in ja:
                continue
            ch = ler_json(dir_exec(saida, i, cel) / "checagem.json") or {}
            v = ch.get("veredito")
            if v != "ACEITA":
                print("nao reposta pelo h4.py: i=%d %s veredito do codigo=%s (%s)"
                      % (i, cel, v or "sem checagem", "reposicao do runner, DT-014" if v == "RECUSADA" else "fora"))
                continue
            if cel not in celulas:
                raise ErroUso("celula %s (i=%d) fora da matriz" % (cel, i))
            cands.append((i, cel, x["veredito"]))
    cands = sorted(set(cands))
    if len(ja) + len(cands) > TETO_REPOSICOES_H4:
        raise Recusa("teto de %d reposicoes h4 na campanha: %d ja feitas + %d novas (C3 §4.4); o Gestor decide"
                     % (TETO_REPOSICOES_H4, len(ja), len(cands)))
    prox = proximo_i_livre(saida, linhas_tsv)
    pos = len(linhas_tsv)
    novas, novas_reg = [], []
    carimbo = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    for i, cel, v in cands:
        cen, modo, falha = cel.split("-")
        novas.append("%d\treposicao\treposicoes\t-\t%d\t%s\t%s\t%s\t%s\t%d\n"
                     % (prox, pos, cel, cen, modo, falha, S0 + PASSO_SEMENTE * prox))
        novas_reg.append("%d\t%s\t%d\t%s\n" % (i, cel, prox, carimbo))
        print("reposicao h4: %s (i=%d, %s) -> i=%d seed=%d" % (cel, i, v, prox, S0 + PASSO_SEMENTE * prox))
        prox += 1
        pos += 1
    if not novas:
        print("repor: nenhuma execucao h4 ACEITA a repor")
    if a.dry_run:
        for l in novas:
            print("DRY-RUN " + l.rstrip("\n"))
        print("DRY-RUN: nada gravado")
        return EXIT_OK
    if novas:
        rep_tsv.parent.mkdir(exist_ok=True)
        if not rep_tsv.is_file():
            rep_tsv.write_bytes(CABECALHO_REPOSICOES.encode("ascii"))
        atual = rep_tsv.read_bytes()
        with open(rep_tsv, "ab") as fh:
            if atual and not atual.endswith(b"\n"):
                fh.write(b"\n")
            fh.write("".join(novas).encode("ascii"))
        reg.parent.mkdir(exist_ok=True)
        if not reg.is_file():
            reg.write_bytes(("\t".join(COLUNAS_REGISTRO) + "\n").encode("ascii"))
        with open(reg, "ab") as fh:
            fh.write("".join(novas_reg).encode("ascii"))
    if rep_tsv.is_file():
        print("reposicoes.tsv SHA-256 %s" % sha256_arquivo(rep_tsv))
    return EXIT_OK


# ----------------------------------------------------------------------------- visao

def cmd_visao(a):
    saida = Path(a.saida).resolve()
    destino = Path(a.destino).resolve()
    if not saida.is_dir():
        raise ErroUso("--saida inexistente: %s" % saida)
    if destino.exists():
        raise ErroUso("--destino ja existe: %s" % destino)
    if destino == saida or saida in destino.parents:
        raise ErroUso("--destino nao pode ficar dentro de --saida (a visao nunca escreve em <SAIDA>)")
    excluir = {}
    if a.excluir:
        for n, l in enumerate(Path(a.excluir).read_text(encoding="utf-8").splitlines(), start=1):
            l = l.strip()
            if not l or l.startswith("#"):
                continue
            if not re.match(r"^\d+$", l):
                raise ErroUso("%s:%d: esperado um i por linha, obtido %r" % (a.excluir, n, l))
            excluir[int(l)] = "descartada-infra (h) (--excluir %s)" % Path(a.excluir).name
    h4 = {}
    usados = []
    for p in sorted((saida / "h4").glob("h4-*.tsv")):
        usados.append((p.name, sha256_arquivo(p)))
        for x in ler_tsv(p):
            if e_h4(x["veredito"]):
                h4[(int(x["i"]), x["celula"])] = "descartada-infra (h4) %s em %s" % (x["veredito"], p.name)
    destino.mkdir(parents=True)
    for f in sorted(saida.iterdir()):
        if f.is_file():
            shutil.copy2(f, destino / f.name)
    if (saida / "reposicoes").is_dir():
        shutil.copytree(saida / "reposicoes", destino / "reposicoes")
    excluidos = []
    links = copias = 0
    for d in sorted(saida.glob("exec-*")):
        if not d.is_dir():
            continue
        m = re.match(r"^exec-(\d+)-(.+?)(\.abortada-.*)?$", d.name)
        i, cel = (int(m.group(1)), m.group(2)) if m else (None, None)
        motivo = None
        if m and not m.group(3):
            motivo = h4.get((i, cel)) or excluir.get(i)
        elif m and i in excluir:
            motivo = excluir[i]
        if motivo:
            excluidos.append((d.name, motivo))
            continue
        for raiz, _dirs, arqs in os.walk(d):
            rel = Path(raiz).relative_to(saida)
            (destino / rel).mkdir(parents=True, exist_ok=True)
            for nome in arqs:
                orig, alvo = Path(raiz) / nome, destino / rel / nome
                try:
                    os.link(orig, alvo)
                    links += 1
                except OSError:
                    shutil.copy2(orig, alvo)
                    if sha256_arquivo(orig) != sha256_arquivo(alvo):
                        print("erro: copia de %s nao confere" % orig, file=sys.stderr)
                        return EXIT_ARQUIVO
                    copias += 1
    txt = ["# Visao h4 de %s" % saida, "versao %s" % VERSAO,
           "gerado_utc %s" % datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "",
           "## h4-*.tsv usados (sha256  arquivo)"]
    txt += ["%s  %s" % (s, n) for n, s in usados] or ["(nenhum)"]
    txt += ["", "## exec-* excluidos (diretorio  motivo)"]
    txt += ["%s  %s" % e for e in excluidos] or ["(nenhum)"]
    txt += ["", "hard links: %d; copias conferidas: %d" % (links, copias)]
    (destino / "VISAO-H4.txt").write_bytes(("\n".join(txt) + "\n").encode("utf-8"))
    print("visao: %d exec-* excluidos, %d hard links, %d copias -> %s" % (len(excluidos), links, copias, destino))
    return EXIT_OK


# ----------------------------------------------------------------------------- main

def main(argv):
    ap = argparse.ArgumentParser(prog="h4.py", description="criterio h4 do rebaseline (DT-018); limiares fixos")
    sub = ap.add_subparsers(dest="cmd")
    f = sub.add_parser("fase")
    f.add_argument("--amostras", action="append", required=True)
    f.add_argument("--saida", required=True)
    f.add_argument("--fase", required=True)
    r = sub.add_parser("repor")
    r.add_argument("--saida", required=True)
    r.add_argument("--matriz", required=True)
    r.add_argument("--dry-run", action="store_true")
    v = sub.add_parser("visao")
    v.add_argument("--saida", required=True)
    v.add_argument("--destino", required=True)
    v.add_argument("--excluir")
    try:
        a = ap.parse_args(argv)
    except SystemExit as ex:
        return EXIT_USO if ex.code else EXIT_OK
    try:
        if a.cmd == "fase":
            return cmd_fase(a)
        if a.cmd == "repor":
            return cmd_repor(a)
        if a.cmd == "visao":
            return cmd_visao(a)
        ap.print_usage(sys.stderr)
        return EXIT_USO
    except ErroUso as ex:
        print("erro de uso: %s" % ex, file=sys.stderr)
        return EXIT_USO
    except ErroArquivo as ex:
        print("erro: %s" % ex, file=sys.stderr)
        return EXIT_ARQUIVO
    except Recusa as ex:
        print("recusado: %s" % ex, file=sys.stderr)
        return EXIT_RECUSA


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
