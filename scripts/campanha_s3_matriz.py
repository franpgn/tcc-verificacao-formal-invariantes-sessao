"""Matriz das campanhas S3 do controlador (W-6 / B-46): calibracao e avaliacao.

Especificacao: docs/plano-execucao-2026-11-04.md (branch docs/plano-implementacao-controlador), secao 9, prompt
W-6, item 1, e secao 5.2. So biblioteca padrao; roda com python3 -I.

Subcomandos:
  gerar --modo calibracao --celulas <spec> --ordem-seed N --s0 N --passo-semente N --i0 N --saida <tsv>
  gerar --modo avaliacao --nucleo <spec> [--extras <spec>] --ordem-seed N --s0 N --passo-semente N
        --i0 N --b0 N --saida <tsv>
  gerar --modo fatorial --nucleo <spec> [--extras <spec>] --ordem-seed N --s0 N --passo-semente N
        --i0 N --b0 N --saida <tsv>
  repor --matriz <tsv> --saida <dir> --ordem-seed N --s0 N --passo-semente N --teto-h4 N [--fase F] [--dry-run]

<spec>: lista separada por virgula de CELULA:PAPEL:N, CELULA = <SYNC|ASYNC>-<none|F1|F2|F3|F4> (F3 desde o W-6c, para
a celula ASYNC F3 descritiva do B-40; uma falha por execucao: nao ha celula com duas falhas). Na calibracao o papel
e sempre "calibracao" e N e o numero de execucoes (so braco A, shadow). Na avaliacao o papel e "teste",
"descritiva" ou "referencia" (tipos de celula do comparar_bracos.py) e N e o numero de blocos A/B.

Nada aqui tem valor padrao: a semente de ordem, S0, o passo da semente, o primeiro i e o primeiro bloco vem do
pre-registro (regra do autor). Desenho, com as fontes:
  - rodadas de blocos completos sorteados (plano 5.2; mesmo desenho do rebaseline, PR secao 3): a rodada r tem uma
    execucao (calibracao) ou um bloco (avaliacao) de cada celula que ainda tem execucoes ou blocos, na ordem de
    random.Random(ordem_seed * 1000 + r).shuffle sobre a lista na ordem dada;
  - nucleo antes das extras (plano 5.2): as rodadas das extras vem depois de todas as do nucleo;
  - bloco: braco A = shadow, braco B = active, mesma semente (plano 5.2, "bloco = par A e B com a mesma semente"),
    ordem dos bracos por random.Random(ordem_seed * 1000000 + bloco).shuffle(["A", "B"]);
  - semente: calibracao s0 + passo * i; avaliacao s0 + passo * bloco (R-S1 do rebaseline: semente pelo indice);
  - reposicao (repor): calibracao, cada execucao perdida com o proximo i livre; avaliacao, o bloco com execucao
    perdida refeito inteiro, com o proximo bloco livre (plano 5.2). Uma semente usada nunca volta. Perdida = tem
    checagem.json com veredito diferente de ACEITA (RECUSADA ou DESCARTADA-INFRA, inclusive h4 e DT-014 depois
    do lancamento). O teto de reposicoes h4 (C3 secao 4.4 no rebaseline) e argumento obrigatorio.

Avaliacao fatorial 2x2 (complemento 1 do benchmark refeito, DT-027): --modo fatorial gera o TSV com uma coluna a mais
no fim, "fusao" (pnn = PREFERRED_NON_NULL, a regra atual do sessions-async; a1 = PoliticaFusaoSessoes, a regra nova;
DT-027 secao 5, item 1). Na celula de papel "teste" cada bloco tem quatro bracos com a mesma semente (DT-027 secao 1):
PO (pnn, shadow), PL (pnn, active), AO (a1, shadow) e AL (a1, active), em ordem sorteada por
random.Random(ordem_seed * 1000000 + bloco).shuffle(["PO", "PL", "AO", "AL"]); nas de papel "descritiva" ou
"referencia", dois (A shadow, B active), com a regra atual (DT-027 secao 3) e o mesmo sorteio de antes. Rodadas,
nucleo antes das extras e semente s0 + passo * bloco como na avaliacao. Os modos calibracao e avaliacao continuam no
formato de 13 colunas, byte a byte; a leitura (repor) aceita os dois, e a de 13 colunas vale como regra atual. O repor
de um bloco fatorial perdido refaz os quatro bracos, com o proximo bloco livre.

Saida de gerar: o TSV (cabecalho + linhas na ordem de execucao) e o SHA-256 na saida padrao.
Saida de repor: <saida>/reposicoes/reposicoes.tsv (mesmas colunas, fase "reposicoes", repoe = i ou bloco de
origem), acrescentado por troca atomica, e o SHA-256.
Exit: 0 ok; 2 uso; 3 recusa (execucao da matriz sem checagem, teto h4).
"""
import argparse
import hashlib
import json
import os
import random
import re
import sys
from pathlib import Path

COLUNAS = ("i", "fase", "rodada", "posicao", "celula", "modo", "falha", "papel", "bloco", "braco", "controlador",
           "seed", "repoe")
# DT-027 secao 5, item 1: a variante da regra de fusao do sessions-async, no fim (as posicoes antigas nao mudam)
COLUNAS_FATORIAL = COLUNAS + ("fusao",)
MODOS = {"SYNC": "sync", "ASYNC": "async"}
FALHAS = ("none", "F1", "F2", "F3", "F4")
PAPEIS_AVALIACAO = ("teste", "descritiva", "referencia")
CONTROLADOR = {"A": "shadow", "B": "active", "PO": "shadow", "PL": "active", "AO": "shadow", "AL": "active"}
# DT-027 secao 1: P = PREFERRED_NON_NULL (pnn), A = A1 (a1); O = controlador desligado (shadow), L = ligado (active)
FUSAO = {"A": "pnn", "B": "pnn", "PO": "pnn", "PL": "pnn", "AO": "a1", "AL": "a1"}
BRACOS_AB = ("A", "B")
BRACOS_FATORIAL = ("PO", "PL", "AO", "AL")
CELULA_RE = re.compile(r"^(SYNC|ASYNC)-(none|F1|F2|F3|F4)$")


class ErroUso(Exception):
    pass


class Recusa(Exception):
    pass


def ler_celulas(spec, modo):
    """[(celula, papel, n)] na ordem dada; ErroUso se invalida."""
    out, vistas = [], set()
    for parte in (spec or "").split(","):
        campos = parte.strip().split(":")
        if len(campos) != 3:
            raise ErroUso("celula deve ser CELULA:PAPEL:N: %r" % parte)
        cel, papel, n = campos
        if not CELULA_RE.match(cel):
            raise ErroUso("celula invalida: %r (use SYNC|ASYNC-none|F1|F2|F3|F4)" % cel)
        if modo == "calibracao" and papel != "calibracao":
            raise ErroUso("na calibracao o papel e 'calibracao': %r" % parte)
        if modo == "avaliacao" and papel not in PAPEIS_AVALIACAO:
            raise ErroUso("papel invalido na avaliacao: %r (use %s)" % (papel, ", ".join(PAPEIS_AVALIACAO)))
        if not re.match(r"^[1-9][0-9]*$", n):
            raise ErroUso("numero de execucoes/blocos deve ser inteiro > 0: %r" % parte)
        if cel in vistas:
            raise ErroUso("celula repetida: %s" % cel)
        vistas.add(cel)
        out.append((cel, papel, int(n)))
    if not out:
        raise ErroUso("lista de celulas vazia")
    return out


def ordem_rodada(ordem_seed, r, celulas):
    lista = list(celulas)
    random.Random(ordem_seed * 1000 + r).shuffle(lista)
    return lista


def ordem_bracos(ordem_seed, bloco, bracos=BRACOS_AB):
    b = list(bracos)
    random.Random(ordem_seed * 1000000 + bloco).shuffle(b)
    return b


def bracos_do_bloco(papel, fatorial):
    """DT-027: quatro bracos so na celula de teste da matriz fatorial; nas outras (e na avaliacao do B-40), A e B."""
    return BRACOS_FATORIAL if fatorial and papel == "teste" else BRACOS_AB


def linha(i, fase, rodada, posicao, cel, papel, bloco, braco, seed, repoe="-"):
    modo, falha = cel.split("-")
    return {"i": i, "fase": fase, "rodada": rodada, "posicao": posicao, "celula": cel, "modo": MODOS[modo],
            "falha": falha, "papel": papel, "bloco": bloco, "braco": braco, "controlador": CONTROLADOR[braco],
            "seed": seed, "repoe": repoe, "fusao": FUSAO[braco]}


def gerar_calibracao(celulas, ordem_seed, s0, passo, i0):
    out, i = [], i0
    rodadas = max(n for _c, _p, n in celulas)
    for r in range(1, rodadas + 1):
        vivas = [(c, p) for c, p, n in celulas if n >= r]
        for pos, (c, p) in enumerate(ordem_rodada(ordem_seed, r, vivas)):
            out.append(linha(i, "calibracao", r, "%d.0" % pos, c, p, "-", "A", s0 + passo * i))
            i += 1
    return out


def gerar_avaliacao(nucleo, extras, ordem_seed, s0, passo, i0, b0, fatorial=False):
    out, i, b, r = [], i0, b0, 0
    for fase, celulas in (("nucleo", nucleo), ("extra", extras)):
        if not celulas:
            continue
        for k in range(1, max(n for _c, _p, n in celulas) + 1):
            r += 1
            vivas = [(c, p) for c, p, n in celulas if n >= k]
            for pos, (c, p) in enumerate(ordem_rodada(ordem_seed, r, vivas)):
                for j, braco in enumerate(ordem_bracos(ordem_seed, b, bracos_do_bloco(p, fatorial))):
                    out.append(linha(i, fase, r, "%d.%d" % (pos, j), c, p, b, braco, s0 + passo * b))
                    i += 1
                b += 1
    return out


def para_tsv(registros, colunas=COLUNAS):
    txt = "\t".join(colunas) + "\n"
    for x in registros:
        txt += "\t".join(str(x[c]) for c in colunas) + "\n"
    return txt


def gravar_atomico(p, dados):
    p = Path(p)
    tmp = p.with_name(p.name + ".tmp")
    tmp.write_bytes(dados)
    os.replace(tmp, p)
    return hashlib.sha256(dados).hexdigest()


def colunas_de(p):
    """COLUNAS ou COLUNAS_FATORIAL, pelo cabecalho; ErroUso com outro."""
    p = Path(p)
    if not p.is_file():
        raise ErroUso("TSV ausente: %s" % p)
    cab = tuple(p.read_text(encoding="ascii").split("\n", 1)[0].split("\t"))
    if cab not in (COLUNAS, COLUNAS_FATORIAL):
        raise ErroUso("cabecalho inesperado em %s (esperado %s, ou com a coluna fusao no fim)"
                      % (p, "\t".join(COLUNAS)))
    return cab


def ler_tsv(p):
    """Linhas da matriz como dicionarios; na de 13 colunas, fusao = pnn (a regra atual, DT-027 secao 3)."""
    colunas = colunas_de(p)
    linhas = Path(p).read_text(encoding="ascii").split("\n")
    out = []
    for n, l in enumerate(linhas[1:], start=2):
        if not l:
            continue
        campos = l.split("\t")
        if len(campos) != len(colunas):
            raise ErroUso("%s:%d com %d campos" % (p, n, len(campos)))
        x = dict(zip(colunas, campos))
        x.setdefault("fusao", "pnn")
        out.append(x)
    return out


def dir_exec(saida, x):
    return Path(saida) / ("exec-%04d-%s-%s" % (int(x["i"]), x["celula"], x["braco"]))


def ler_checagem(saida, x):
    c = dir_exec(saida, x) / "checagem.json"
    if not c.is_file():
        return None
    try:
        return json.loads(c.read_text(encoding="utf-8"))
    except ValueError:
        raise ErroUso("checagem ilegivel: %s" % c)


def e_h4(ch):
    return any(str(m).startswith("h4") for m in (ch.get("motivos") or []))


def repor(matriz, saida, ordem_seed, s0, passo, teto_h4, fase=None, dry_run=False):
    linhas = ler_tsv(matriz)
    colunas = colunas_de(matriz)
    calib = all(x["braco"] == "A" and x["bloco"] == "-" for x in linhas)
    for x in linhas:
        esperado = s0 + passo * int(x["i"] if calib else x["bloco"])
        if int(x["seed"]) != esperado:
            raise ErroUso("semente da matriz (i=%s, %s) nao e s0 + passo * %s = %d: confira --s0 e --passo-semente"
                          % (x["i"], x["seed"], "i" if calib else "bloco", esperado))
    rep_tsv = Path(saida) / "reposicoes" / "reposicoes.tsv"
    reps = ler_tsv(rep_tsv) if rep_tsv.exists() else []
    alvo = [x for x in linhas if fase is None or x["fase"] == fase]
    if fase is not None and not alvo:
        raise ErroUso("nenhuma linha da fase %r na matriz" % fase)
    pendentes = [x["i"] for x in alvo if ler_checagem(saida, x) is None]
    if pendentes:
        raise Recusa("execucoes da matriz sem checagem.json (pendentes): i=%s; termine a fase antes de repor"
                     % ",".join(pendentes))
    ja = {x["repoe"] for x in reps}
    todas = linhas + reps

    def chave(x):
        return x["i"] if calib else x["bloco"]

    def perdeu_por_h4(k):
        return any(e_h4(ler_checagem(saida, y) or {}) for y in todas if chave(y) == k)

    # reposicoes de reposicoes: as linhas de reposicoes.tsv que descendem da fase (todas, sem --fase)
    if fase is None:
        derivadas = list(reps)
    else:
        incl, derivadas, mudou = {chave(x) for x in alvo}, [], True
        while mudou:
            mudou = False
            for x in reps:
                if x not in derivadas and x["repoe"] in incl:
                    derivadas.append(x)
                    incl.add(chave(x))
                    mudou = True
    perdidas = []
    for x in alvo + derivadas:
        ch = ler_checagem(saida, x)
        if ch is None or ch.get("veredito") == "ACEITA":
            continue
        k = chave(x)
        if k in ja or k in [p[0] for p in perdidas]:
            continue
        perdidas.append((k, x))
    h4_antes = sum(1 for k in sorted(ja) if perdeu_por_h4(k))
    h4_novas = sum(1 for k, _x in perdidas if perdeu_por_h4(k))
    if h4_antes + h4_novas > teto_h4:
        raise Recusa("teto de reposicoes h4 estourado: %d ja feitas + %d novas > %d" % (h4_antes, h4_novas, teto_h4))
    prox_i = max(int(x["i"]) for x in todas) + 1
    blocos = [int(x["bloco"]) for x in todas if x["bloco"] != "-"]
    prox_b = (max(blocos) + 1) if blocos else None
    usadas = {x["seed"] for x in todas}
    novas, pos = [], len(reps)
    for k_orig, x in perdidas:
        if calib:
            seed = s0 + passo * prox_i
            if str(seed) in usadas:
                raise Recusa("semente %d ja usada" % seed)
            novas.append(linha(prox_i, "reposicoes", "-", "%d.0" % pos, x["celula"], x["papel"], "-", "A", seed,
                               repoe=k_orig))
            prox_i += 1
        else:
            seed = s0 + passo * prox_b
            if str(seed) in usadas:
                raise Recusa("semente %d ja usada" % seed)
            bracos = BRACOS_FATORIAL if x["braco"] in BRACOS_FATORIAL else BRACOS_AB
            for j, braco in enumerate(ordem_bracos(ordem_seed, prox_b, bracos)):
                novas.append(linha(prox_i, "reposicoes", "-", "%d.%d" % (pos, j), x["celula"], x["papel"], prox_b,
                                   braco, seed, repoe=k_orig))
                prox_i += 1
            prox_b += 1
        usadas.add(str(seed))
        pos += 1
    for x in novas:
        print("reposicao: %s %s bloco=%s braco=%s i=%s seed=%s (repoe %s)"
              % (x["celula"], x["papel"], x["bloco"], x["braco"], x["i"], x["seed"], x["repoe"]))
    if dry_run or not novas:
        print("repor: %d linha(s) nova(s)%s" % (len(novas), " (dry-run, nada gravado)" if dry_run and novas else ""))
        return None
    rep_tsv.parent.mkdir(parents=True, exist_ok=True)
    dados = para_tsv(reps + novas, colunas).encode("ascii")
    sha = gravar_atomico(rep_tsv, dados)
    print("%s  %s" % (sha, rep_tsv))
    return sha


def main(argv):
    ap = argparse.ArgumentParser(prog="campanha_s3_matriz.py")
    sub = ap.add_subparsers(dest="cmd")
    g = sub.add_parser("gerar")
    g.add_argument("--modo", required=True, choices=("calibracao", "avaliacao", "fatorial"))
    g.add_argument("--celulas")
    g.add_argument("--nucleo")
    g.add_argument("--extras")
    g.add_argument("--ordem-seed", type=int, required=True)
    g.add_argument("--s0", type=int, required=True)
    g.add_argument("--passo-semente", type=int, required=True)
    g.add_argument("--i0", type=int, required=True)
    g.add_argument("--b0", type=int)
    g.add_argument("--saida", required=True)
    r = sub.add_parser("repor")
    r.add_argument("--matriz", required=True)
    r.add_argument("--saida", required=True)
    r.add_argument("--ordem-seed", type=int, required=True)
    r.add_argument("--s0", type=int, required=True)
    r.add_argument("--passo-semente", type=int, required=True)
    r.add_argument("--teto-h4", type=int, required=True)
    r.add_argument("--fase")
    r.add_argument("--dry-run", action="store_true")
    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return 2 if e.code else 0
    try:
        if a.cmd == "gerar":
            if a.passo_semente <= 0 or a.i0 < 0:
                raise ErroUso("--passo-semente deve ser > 0 e --i0 >= 0")
            if a.modo == "calibracao":
                if not a.celulas or a.nucleo or a.extras or a.b0 is not None:
                    raise ErroUso("calibracao: so --celulas (sem --nucleo, --extras, --b0)")
                reg = gerar_calibracao(ler_celulas(a.celulas, "calibracao"), a.ordem_seed, a.s0, a.passo_semente,
                                       a.i0)
            else:
                if not a.nucleo or a.celulas or a.b0 is None or a.b0 < 0:
                    raise ErroUso("%s: --nucleo e --b0 >= 0 obrigatorios (sem --celulas)" % a.modo)
                nuc = ler_celulas(a.nucleo, "avaliacao")
                ext = ler_celulas(a.extras, "avaliacao") if a.extras else []
                comuns = {c for c, _p, _n in nuc} & {c for c, _p, _n in ext}
                if comuns:
                    raise ErroUso("celula no nucleo e nas extras: %s" % ", ".join(sorted(comuns)))
                reg = gerar_avaliacao(nuc, ext, a.ordem_seed, a.s0, a.passo_semente, a.i0, a.b0,
                                      fatorial=a.modo == "fatorial")
            dados = para_tsv(reg, COLUNAS_FATORIAL if a.modo == "fatorial" else COLUNAS).encode("ascii")
            sha = gravar_atomico(a.saida, dados)
            print("%s  %s" % (sha, a.saida))
            return 0
        if a.cmd == "repor":
            if a.teto_h4 < 0 or a.passo_semente <= 0:
                raise ErroUso("--teto-h4 >= 0 e --passo-semente > 0")
            repor(a.matriz, a.saida, a.ordem_seed, a.s0, a.passo_semente, a.teto_h4, a.fase, a.dry_run)
            return 0
        ap.print_usage(sys.stderr)
        return 2
    except ErroUso as e:
        print("Erro: %s" % e, file=sys.stderr)
        return 2
    except Recusa as e:
        print("Recusa: %s" % e, file=sys.stderr)
        return 3


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
