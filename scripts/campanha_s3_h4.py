"""Criterio h4 (travamento de I/O ou pausa da VM) nas campanhas S3 do controlador (W-6 / B-46, requisito 6).

Os criterios do complemento 3 do pre-registro do rebaseline (C3) e da DT-019 valem para as campanhas S3 (decisao do
coordenador de 05/11). Este adaptador NAO reimplementa o h4: importa analysis/h4.py, sem edita-lo (a lista
e9c44819 da adenda da DT-018 fica como esta), e usa dele h4.Serie (leitura do amostrador e janelas de 10 s),
h4.avaliar_execucao (h4a, h4b, h4c e o segmento do F1), h4.avaliar_parada (C3 secao 4.3, regras 1 e 2) e
Serie.episodios (C3 secao 6, base da DT-019). A unica diferenca e a janela de cada execucao:

  - rebaseline: a janela de medicao do PR, ancora + 44 s a ancora + 300 s (C3 secao 3.1), constantes do h4.py;
  - S3: a janela de medicao da propria execucao, que o orquestrador le da linha
    "Warm-up ate +Xs; medicao a partir de +Ys; total Zs" e grava em execucao.json, campo janela_h4
    {ini_desde_ancora_s: Y, fim_desde_ancora_s: Z, f1_reinicio_padrao_desde_ancora_s: inj_offset + f1_dur}.

A janela entra pelas globais MEDICAO_INI_US/MEDICAO_FIM_US/MEDICAO_INI_S/MEDICAO_FIM_S e F1_REINICIO_PADRAO_S do
modulo h4, trocadas durante a avaliacao de cada execucao e restauradas depois (o h4.avaliar_execucao as le na hora
da chamada). Com a janela 44..300 e o F1 em +109 s, o veredito e identico ao do h4.py (teste).

Uso:
  python3 -I scripts/campanha_s3_h4.py fase --amostras <arq> [--amostras <arq2>] --saida <SAIDA> --fase <fase>
      [--exec <dir de uma execucao>]
  Avalia as execucoes terminadas da fase (execucao.json com "fase" = <fase> e fim_utc), na ordem de lancamento,
  grava <SAIDA>/h4/h4-<fase>.tsv e .json (formato do h4.py) e, com --exec, imprime "VEREDITO <i> <veredito>".
  Exit 0 segue, 30 parada h4 (C3 secao 4.3, regras 1 e 2), 2 uso, 4 arquivo de amostras ilegivel.
  A regra 3 do C3 secao 4.3 (unit do amostrador inativa) e do orquestrador.
"""
import argparse
import contextlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI.parent / "analysis"))
import h4  # noqa: E402

VERSAO = "campanha-s3-h4/1 sobre %s" % h4.VERSAO
GLOBAIS = ("MEDICAO_INI_US", "MEDICAO_FIM_US", "MEDICAO_INI_S", "MEDICAO_FIM_S", "F1_REINICIO_PADRAO_S")


@contextlib.contextmanager
def janela(ini_s, fim_s, f1_padrao_s):
    antes = {k: getattr(h4, k) for k in GLOBAIS}
    try:
        h4.MEDICAO_INI_S, h4.MEDICAO_FIM_S = float(ini_s), float(fim_s)
        h4.MEDICAO_INI_US, h4.MEDICAO_FIM_US = h4._us(ini_s), h4._us(fim_s)
        if f1_padrao_s is not None:
            h4.F1_REINICIO_PADRAO_S = float(f1_padrao_s)
        yield
    finally:
        for k, v in antes.items():
            setattr(h4, k, v)


def janela_da_execucao(d, ex):
    j = ex.get("janela_h4")
    if not isinstance(j, dict):
        raise h4.ErroUso("%s/execucao.json sem janela_h4 (o orquestrador grava a janela de medicao)" % d.name)
    try:
        ini, fim = float(j["ini_desde_ancora_s"]), float(j["fim_desde_ancora_s"])
        f1 = j.get("f1_reinicio_padrao_desde_ancora_s")
        f1 = None if f1 is None else float(f1)
    except (KeyError, TypeError, ValueError):
        raise h4.ErroUso("%s/execucao.json com janela_h4 invalida: %r" % (d.name, j))
    if not 0 <= ini < fim:
        raise h4.ErroUso("%s: janela_h4 com inicio %s e fim %s" % (d.name, ini, fim))
    return ini, fim, f1


def execucoes_da_fase(saida, fase):
    """[(dir, execucao.json)] das execucoes terminadas da fase (com fim_utc), fora as .abortada-*."""
    execs, em_curso = [], []
    for d in sorted(Path(saida).glob("exec-*")):
        if not d.is_dir() or ".abortada-" in d.name:
            continue
        ex = h4.ler_json(d / "execucao.json")
        if ex is None:
            em_curso.append(d.name)
            continue
        if ex.get("fase") != fase:
            continue
        if not ex.get("fim_utc"):
            em_curso.append(d.name)
            continue
        execs.append((d, ex))
    execs.sort(key=lambda par: (par[1].get("lancamento_ms") is None, par[1].get("lancamento_ms") or 0, par[0].name))
    return execs, em_curso


def cmd_fase(a):
    saida = Path(a.saida)
    if not saida.is_dir():
        raise h4.ErroUso("--saida inexistente: %s" % saida)
    serie = h4.Serie(a.amostras)
    execs, em_curso = execucoes_da_fase(saida, a.fase)
    linhas, janelas = [], []
    for d, ex in execs:
        if ex.get("ancora_ms") is None:
            # sem ancora nao ha janela; o h4.py devolve "sem-ancora", que nao e h4 (C3 secao 3.1)
            linhas.append(h4.avaliar_execucao(serie, ex))
            janelas.append({"exec": d.name, "ini_desde_ancora_s": None, "fim_desde_ancora_s": None,
                            "f1_reinicio_padrao_desde_ancora_s": None})
            continue
        ini, fim, f1 = janela_da_execucao(d, ex)
        with janela(ini, fim, f1):
            linhas.append(h4.avaliar_execucao(serie, ex))
        janelas.append({"exec": d.name, "ini_desde_ancora_s": ini, "fim_desde_ancora_s": fim,
                        "f1_reinicio_padrao_desde_ancora_s": f1})
    parada, motivos = h4.avaliar_parada([x["veredito"] for x in linhas])
    eps = serie.episodios()
    horas = serie.horas()
    por_hora = (len(eps) / horas) if horas > 0 else None

    destino = saida / "h4"
    destino.mkdir(exist_ok=True)
    tsv = "\t".join(h4.COLUNAS) + "\n" + "".join("\t".join(x[c] for c in h4.COLUNAS) + "\n" for x in linhas)
    h4.gravar_atomico(destino / ("h4-%s.tsv" % a.fase), tsv)
    constantes = {k: getattr(h4, k) for k in (
        "JANELA_S", "LIMIAR_H4A_S", "LIMIAR_H4B_S", "LIMIAR_SALTO_S", "SEG_F1_ANTES_S", "SEG_F1_DEPOIS_S",
        "COBERTURA_FOLGA_S", "PARADA_JANELA_EXEC", "PARADA_MIN_H4", "PARADA_SEGUIDAS")}
    h4s = [x for x in linhas if h4.e_h4(x["veredito"])]
    js = {"versao": VERSAO, "fase": a.fase,
          "gerado_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
          "constantes": constantes, "janelas": janelas, "arquivos": serie.arquivos,
          "segmentos": [{k: (h4.utc(v) if k.startswith("t_") else v) for k, v in s.items()} for s in serie.segmentos],
          "execucoes": linhas, "execucoes_em_curso_ou_sem_execucao_json": em_curso,
          "contagem": {"execucoes": len(linhas), "h4": len(h4s)},
          "episodios": {"n": len(eps), "horas_observadas": round(horas, 6),
                        "por_hora": None if por_hora is None else round(por_hora, 6), "lista": eps},
          "parada": {"parada": parada, "motivos": motivos,
                     "nota": "regras 1 e 2 do C3 secao 4.3; a regra 3 (unit inativa) e do orquestrador"}}
    h4.gravar_atomico(destino / ("h4-%s.json" % a.fase), json.dumps(js, indent=1, ensure_ascii=False) + "\n")

    for x in h4s:
        print("h4 i=%s %s falha=%s veredito=%s pior_janela=%ss maior_intervalo=%ss cobertura=%s"
              % (x["i"], x["celula"], x["falha"], x["veredito"], x["pior_janela_s"], x["maior_intervalo_s"],
                 x["cobertura"]))
    if a.exec:
        alvo = Path(a.exec).resolve()
        achou = [x for (d, _e), x in zip(execs, linhas) if d.resolve() == alvo]
        if not achou:
            raise h4.ErroUso("--exec %s nao esta entre as execucoes terminadas da fase %s" % (a.exec, a.fase))
        print("VEREDITO %s %s" % (achou[0]["i"], achou[0]["veredito"]))
    for m in motivos:
        print("PARADA H4: " + m)
    print("h4: %d de %d execucoes; episodios/h=%s; parada=%s"
          % (len(h4s), len(linhas), "-" if por_hora is None else "%.3f" % por_hora, "sim" if parada else "nao"))
    return h4.EXIT_PARADA if parada else h4.EXIT_OK


def main(argv):
    ap = argparse.ArgumentParser(prog="campanha_s3_h4.py")
    sub = ap.add_subparsers(dest="cmd")
    f = sub.add_parser("fase")
    f.add_argument("--amostras", action="append", required=True)
    f.add_argument("--saida", required=True)
    f.add_argument("--fase", required=True)
    f.add_argument("--exec")
    try:
        a = ap.parse_args(argv)
    except SystemExit as ex:
        return h4.EXIT_USO if ex.code else h4.EXIT_OK
    try:
        if a.cmd == "fase":
            return cmd_fase(a)
        ap.print_usage(sys.stderr)
        return h4.EXIT_USO
    except h4.ErroUso as ex:
        print("erro de uso: %s" % ex, file=sys.stderr)
        return h4.EXIT_USO
    except h4.ErroArquivo as ex:
        print("erro: %s" % ex, file=sys.stderr)
        return h4.EXIT_ARQUIVO


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
