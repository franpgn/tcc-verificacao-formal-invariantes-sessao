"""Conferencias do orquestrador das campanhas S3 (scripts/run-campanha-s3.sh), W-6 / B-46.

So biblioteca padrao; roda com python3 -I. Cada subcomando imprime linhas "nome<TAB>valor" e sai com:
0 ok; 2 uso ou arquivo invalido; 4 arquivo ausente ou ilegivel; 5 recusa (a execucao nao pode seguir).

  parametros  --arquivo <campanha.json> --t-enc-pior-s N
      Arquivo dos numeros da campanha, esquema "campanha-s3/1":
        {"esquema": "campanha-s3/1", "versao": "...",
         "parametros": {"<nome>": {"valor": <v>, "origem": "<texto nao vazio>"}, ...}}
      Todos os nomes de PARAMETROS sao obrigatorios, nenhum tem valor padrao, nenhum nome a mais e aceito, e cada
      um traz a sua origem (regra do autor: limiar sem origem fica obrigatorio e sem padrao; o pre-registro da
      calibracao e o B-40 preenchem). Coerencia: cada falha acaba antes do fim da carga
      (inj_offset_s + f*_dur_s <= duracao_s) e exec_teto_s >= duracao_s + t_enc_pior_s + religamento_prazo_s +
      convergencia_teto_s + convergencia_periodo_s (M1 da V-W6: o teto do processo cobre a carga, o encerramento do
      controlador antes da linha de fim, a espera do religamento e o laco de convergencia, que dorme um periodo antes
      de olhar o teto). O T_enc vem do orquestrador (--t-enc-pior-s = T_ENC_PIOR_S, contrato JSONL 1.2). O tempo do
      lancamento ate a ancora (t_pre <= 2,4 s no I-3b) e o fechamento depois da convergencia (t_pos = 13,5 s) sao
      medidos, nao constantes: o pre-registro os soma com folga acima deste minimo (adenda da V-W6, secao 11;
      pre-registro da calibracao 4.1: 300 + 21 + 60 + 600 + 1,5 x (2,4 + 13,5) -> 1005 s, minimo daqui 986 s).
      W-6c: o esquema "campanha-s3/2" e o /1 mais os cinco numeros do F3 (PARAMETROS_F3: f3_no, f3_delay_ms,
      f3_jitter_ms, f3_distribuicao, f3_dur_s; B-40 secao 4.3, valores do rebaseline: isn2, 20, 13, normal, 251), com
      a mesma regra (todos obrigatorios, com origem) e a mesma coerencia (inj_offset_s + f3_dur_s <= duracao_s). O /1
      continua aceito (a calibracao nao roda F3); o orquestrador recusa F3 na fila sem o /2.
      Imprime "nome<TAB>valor" de cada parametro.

  controlador --arquivo <controlador-*.json> --hotrod-timeout-ms N [--exigir-politica] [--exigir-p2-desabilitada]
      Le o --controller-config. Com --exigir-politica (braco B na avaliacao), recusa (5) sem o bloco "politica"
      (A-3 da V-W5; O10 da V-W2a: sem o bloco o braco B vira o W-3 sem aviso). Com o bloco, recusa se tetoLagMs !=
      --hotrod-timeout-ms (R7 da V-W2a). Com --exigir-p2-desabilitada (calibracao e avaliacao: D-B do coordenador,
      05/11, contrato JSONL 1.2), recusa sem o bloco ou com politica.p2Habilitada diferente de false. Imprime sha256,
      politica (sim/nao), p2_habilitada (true/false/-) e defasagem (sim/nao).

  meta --decisoes <decisoes-rep-NNN.jsonl> --braco A|B --param-sha256 <hex> [--exigir-p2-desabilitada]
      Primeira linha da trilha (contrato decisoes-controlador/1.2). Braco A: modo shadow. Braco B: modo active,
      atuador != "nulo", politica != "nenhuma", parametros_politica nao nulo. Nos dois: param_sha256 igual ao dado
      (o SHA-256 do --controller-config, o mesmo arquivo nos dois bracos do bloco). Com --exigir-p2-desabilitada, nos
      dois bracos, parametros_politica.p2_habilitada tem de ser false (D-B).

  trilha --decisoes <decisoes-rep-NNN.jsonl> --sinal-alvo <host:porta|->
      Alvo do AC-5 APLICADA na trilha (a regra de ac5_aplicada do comparar_bracos.py: APLICAR, AC-5, APLICADA) e o
      da linha "Fim da carga:" do programa tem de ser o mesmo; "-" = nenhum. Um AC-5 FALHOU executado tambem
      recusa quando o sinal o da como aplicado. Imprime trilha_ac5, ac5_falhou_executada, ac5_falhou_alvos (O8 da
      V-W2a: um FALHOU pode ter parado o no mesmo assim; o orquestrador confere os nos pela REST) e
      trilha_completa. W-6c (B-7 do B-40): imprime tambem fim (sim/nao), linhas_perdidas (o valor da linha fim de
      decisoes, contrato 1.1+, ou "-"), trilha_incompleta (sim se faltar o fim ou se linhas_perdidas nao for 0) e
      trilha_incompleta_motivo. So marca: o exit nao muda; o veredito e do orquestrador (--exigir-trilha-completa).

  referencia --arquivo <referencia.json> --perfil sync|async
      Referencia de p99 do controlador (esquema referencia-p99/1, controle/ArquivoReferencia.java). B2 da V-W6: o
      programa recusa referencia de outro perfil (LigacaoControlador.java:204), entao o orquestrador recebe uma por
      modo de replicacao e confere cada uma no pre-voo e no --dry-run. Recusa (5) se cenario != S3 ou perfil !=
      --perfil; 2 se o esquema nao for referencia-p99/1 ou faltar p99_ns. Imprime sha256, cenario e perfil.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ESQUEMA_PARAMETROS = "campanha-s3/1"
ESQUEMA_PARAMETROS_F3 = "campanha-s3/2"   # W-6c: o /1 mais os numeros do F3
NO_RE = re.compile(r"^isn[1-3]$")
INT_POS = ("int", 1)
INT_NAO_NEG = ("int", 0)
# nome -> (tipo, minimo) ou ("enum", conjunto) ou ("no", None)
PARAMETROS = {
    "duracao_s": INT_POS,
    "warmup_min_s": INT_NAO_NEG,
    "inj_offset_s": INT_POS,
    "f1_no": ("no", None),
    "f1_dur_s": INT_POS,
    "f2_no": ("no", None),
    "f2_dur_s": INT_POS,
    "f4_no": ("no", None),
    "f4_severidade": ("enum", ("N1", "N2", "N3", "N4", "N5")),
    "f4_direcao": ("enum", ("out", "in", "both")),
    "f4_dur_s": INT_POS,
    "hotrod_timeout_ms": INT_POS,
    "convergencia_teto_s": INT_POS,
    "convergencia_periodo_s": INT_POS,
    "w_recovery_s": INT_NAO_NEG,
    "religamento_prazo_s": INT_POS,
    "exec_teto_s": INT_POS,
    "encerramento_folga_s": INT_POS,
    "volume_exec_mb": INT_POS,
}
# W-6c (B-40 secao 4.3): F3 pelo inject-jitter.sh, que recebe --delay/--jitter <n>ms e --distribution destas quatro
DISTRIBUICOES_NETEM = ("normal", "lognormal", "pareto", "paretonormal")
PARAMETROS_F3 = {
    "f3_no": ("no", None),
    "f3_delay_ms": INT_POS,
    "f3_jitter_ms": INT_NAO_NEG,
    "f3_distribuicao": ("enum", DISTRIBUICOES_NETEM),
    "f3_dur_s": INT_POS,
}
ESQUEMA_DECISOES = "decisoes-controlador/1.2"
ESQUEMA_REFERENCIA = "referencia-p99/1"   # controle/ArquivoReferencia.java (ESQUEMA)
ATUADOR_NULO = "nulo"
POLITICA_NENHUMA = "nenhuma"
TIPO_AC5 = "AC-5"


class ErroUso(Exception):
    pass


class ErroArquivo(Exception):
    pass


class Recusa(Exception):
    pass


def ler_json(p):
    p = Path(p)
    if not p.is_file():
        raise ErroArquivo("arquivo ausente: %s" % p)
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise ErroUso("JSON invalido em %s: %s" % (p, e))


def sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def conferir_parametros(obj, t_enc_pior_s):
    """{nome: valor} ou ErroUso. t_enc_pior_s e a parcela do teto que vem do orquestrador (M1 da V-W6)."""
    if isinstance(t_enc_pior_s, bool) or not isinstance(t_enc_pior_s, int) or t_enc_pior_s < 0:
        raise ErroUso("--t-enc-pior-s deve ser inteiro >= 0: %r" % (t_enc_pior_s,))
    esquema = obj.get("esquema") if isinstance(obj, dict) else None
    if esquema == ESQUEMA_PARAMETROS:
        nomes = dict(PARAMETROS)
    elif esquema == ESQUEMA_PARAMETROS_F3:
        nomes = dict(PARAMETROS, **PARAMETROS_F3)
    else:
        raise ErroUso("esquema deve ser %r ou %r (com F3)" % (ESQUEMA_PARAMETROS, ESQUEMA_PARAMETROS_F3))
    if not isinstance(obj.get("versao"), str) or not obj["versao"].strip():
        raise ErroUso("versao ausente")
    ps = obj.get("parametros")
    if not isinstance(ps, dict):
        raise ErroUso("bloco parametros ausente")
    faltam = sorted(set(nomes) - set(ps))
    sobram = sorted(set(ps) - set(nomes))
    if faltam:
        raise ErroUso("parametros ausentes (obrigatorios, sem padrao): %s" % ", ".join(faltam))
    if sobram:
        raise ErroUso("parametros desconhecidos: %s" % ", ".join(sobram))
    v = {}
    for nome, (tipo, arg) in nomes.items():
        p = ps[nome]
        if not isinstance(p, dict) or set(p) != {"valor", "origem"}:
            raise ErroUso("%s: use {\"valor\": ..., \"origem\": \"...\"}" % nome)
        origem = p["origem"]
        if not isinstance(origem, str) or not origem.strip():
            raise ErroUso("%s sem origem (regra do autor: numero sem origem nao entra)" % nome)
        x = p["valor"]
        if tipo == "int":
            if isinstance(x, bool) or not isinstance(x, int) or x < arg:
                raise ErroUso("%s deve ser inteiro >= %d: %r" % (nome, arg, x))
        elif tipo == "enum":
            if x not in arg:
                raise ErroUso("%s deve ser um de %s: %r" % (nome, ", ".join(arg), x))
        elif tipo == "no":
            if not isinstance(x, str) or not NO_RE.match(x):
                raise ErroUso("%s deve ser isn1, isn2 ou isn3: %r" % (nome, x))
        v[nome] = x
    for f in ("f1", "f2", "f3", "f4"):
        if f + "_dur_s" not in v:
            continue
        if v["inj_offset_s"] + v[f + "_dur_s"] > v["duracao_s"]:
            raise ErroUso("%s acaba depois do fim da carga: inj_offset_s + %s_dur_s = %d > duracao_s = %d"
                          % (f.upper(), f, v["inj_offset_s"] + v[f + "_dur_s"], v["duracao_s"]))
    minimo = (v["duracao_s"] + t_enc_pior_s + v["religamento_prazo_s"] + v["convergencia_teto_s"]
              + v["convergencia_periodo_s"])
    if v["exec_teto_s"] < minimo:
        raise ErroUso("exec_teto_s = %d < duracao_s (%d) + t_enc_pior_s (%d) + religamento_prazo_s (%d) + "
                      "convergencia_teto_s (%d) + convergencia_periodo_s (%d) = %d (o pre-registro soma ainda o tempo "
                      "ate a ancora e o fechamento, com folga)"
                      % (v["exec_teto_s"], v["duracao_s"], t_enc_pior_s, v["religamento_prazo_s"],
                         v["convergencia_teto_s"], v["convergencia_periodo_s"], minimo))
    return v


def cmd_parametros(a):
    v = conferir_parametros(ler_json(a.arquivo), a.t_enc_pior_s)
    for nome, x in v.items():
        print("%s\t%s" % (nome, x))


def cmd_controlador(a):
    obj = ler_json(a.arquivo)
    if not isinstance(obj, dict):
        raise ErroUso("controlador nao e objeto JSON")
    pol = obj.get("politica")
    tem_pol = isinstance(pol, dict)
    if a.exigir_politica and not tem_pol:
        raise Recusa("braco B sem o bloco politica no --controller-config (A-3 da V-W5, O10 da V-W2a): o atuador "
                     "seria o nulo e a politica 'nenhuma'")
    if tem_pol and pol.get("tetoLagMs") != a.hotrod_timeout_ms:
        raise Recusa("politica.tetoLagMs = %r difere de --hotrod-timeout-ms = %d (R7 da V-W2a)"
                     % (pol.get("tetoLagMs"), a.hotrod_timeout_ms))
    p2 = pol.get("p2Habilitada", "ausente") if tem_pol else "sem bloco politica"
    if a.exigir_p2_desabilitada and p2 is not False:
        raise Recusa("politica.p2Habilitada = %r: na calibracao e na avaliacao tem de ser false (D-B do coordenador, "
                     "05/11: a P2/AC-2 e exploratoria e fica fora do braco B)" % (p2,))
    print("sha256\t%s" % sha256(a.arquivo))
    print("politica\t%s" % ("sim" if tem_pol else "nao"))
    print("p2_habilitada\t%s" % (("true" if p2 else "false") if isinstance(p2, bool) else "-"))
    print("defasagem\t%s" % ("sim" if isinstance(obj.get("defasagem"), dict) else "nao"))


def primeira_linha(p):
    p = Path(p)
    if not p.is_file():
        raise ErroArquivo("trilha ausente: %s" % p)
    try:
        prim = p.read_bytes().decode("utf-8", errors="replace").split("\n", 1)[0]
        o = json.loads(prim)
    except (OSError, ValueError):
        raise ErroArquivo("primeira linha ilegivel em %s" % p)
    if not isinstance(o, dict) or o.get("tipo") != "meta":
        raise ErroArquivo("primeira linha de %s nao e meta" % p)
    return o


def cmd_meta(a):
    m = primeira_linha(a.decisoes)
    problemas = []
    if m.get("esquema") != ESQUEMA_DECISOES:
        problemas.append("esquema %r (esperado %s)" % (m.get("esquema"), ESQUEMA_DECISOES))
    modo = "shadow" if a.braco == "A" else "active"
    if m.get("modo") != modo:
        problemas.append("modo %r no braco %s (esperado %s: braco trocado)" % (m.get("modo"), a.braco, modo))
    if m.get("param_sha256") != a.param_sha256:
        problemas.append("param_sha256 %r difere do --controller-config da campanha (%s)"
                         % (m.get("param_sha256"), a.param_sha256))
    if a.braco == "B":
        if not m.get("atuador") or m.get("atuador") == ATUADOR_NULO:
            problemas.append("atuador %r (braco B com atuador nulo nao atua)" % m.get("atuador"))
        if not m.get("politica") or m.get("politica") == POLITICA_NENHUMA:
            problemas.append("politica %r (braco B sem politica nao atua)" % m.get("politica"))
        if m.get("parametros_politica") is None:
            problemas.append("parametros_politica nulo (sem o bloco politica)")
    pp = m.get("parametros_politica")
    p2 = pp.get("p2_habilitada", "ausente") if isinstance(pp, dict) else "sem parametros_politica"
    if a.exigir_p2_desabilitada and p2 is not False:
        problemas.append("parametros_politica.p2_habilitada = %r (D-B: false na calibracao e na avaliacao)" % (p2,))
    print("modo\t%s" % m.get("modo"))
    print("atuador\t%s" % m.get("atuador"))
    print("politica\t%s" % m.get("politica"))
    print("param_sha256\t%s" % m.get("param_sha256"))
    if problemas:
        raise Recusa("meta do braco %s: %s" % (a.braco, "; ".join(problemas)))


def ler_trilha(p):
    """(linhas, completa); ultima linha truncada e descartada (contrato, regra 1)."""
    p = Path(p)
    if not p.is_file():
        raise ErroArquivo("trilha ausente: %s" % p)
    partes = p.read_bytes().decode("utf-8", errors="replace").split("\n")
    sem_quebra = partes[-1] != ""
    if not sem_quebra:
        partes = partes[:-1]
    linhas, completa = [], True
    for i, bruta in enumerate(partes):
        if not bruta.strip():
            continue
        try:
            o = json.loads(bruta)
        except ValueError:
            if i == len(partes) - 1 and sem_quebra:
                completa = False
                break
            raise ErroArquivo("linha %d ilegivel em %s" % (i + 1, p))
        if isinstance(o, dict):
            linhas.append(o)
    if not linhas or linhas[0].get("tipo") != "meta":
        raise ErroArquivo("trilha sem meta: %s" % p)
    if linhas[-1].get("tipo") != "fim":
        completa = False
    return linhas, completa


def completude(linhas, completa):
    """(fim, linhas_perdidas, motivo ou None) da trilha de decisoes. W-6c, B-7 do B-40: completa so com a linha fim no
    final (regra geral 2 do contrato: sem fim = execucao interrompida) e linhas_perdidas = 0 nela (campo do fim de
    decisoes desde a v1.1: erros de escrita mais linhas recebidas com a trilha fechada)."""
    if not completa:
        return "nao", "-", "sem a linha fim (execucao interrompida; contrato JSONL, regra geral 2)"
    fim = linhas[-1]
    if "linhas_perdidas" not in fim:
        return "sim", "-", "fim sem linhas_perdidas (campo do contrato 1.1+)"
    lp = fim["linhas_perdidas"]
    if isinstance(lp, bool) or not isinstance(lp, int) or lp < 0:
        return "sim", "-", "linhas_perdidas invalido no fim: %r" % (lp,)
    if lp > 0:
        return "sim", str(lp), "linhas_perdidas = %d no fim" % lp
    return "sim", "0", None


def cmd_trilha(a):
    linhas, completa = ler_trilha(a.decisoes)
    fim, perdidas, motivo = completude(linhas, completa)
    aplicada, falhou = None, []
    for o in linhas:
        ac = o.get("acao") if isinstance(o.get("acao"), dict) else {}
        if o.get("tipo") != "acao" or o.get("operacao") != "APLICAR" or ac.get("tipo") != TIPO_AC5:
            continue
        if o.get("resultado") == "APLICADA" and aplicada is None:
            aplicada = ac.get("alvo")
        elif o.get("resultado") == "FALHOU" and o.get("executada") is True:
            falhou.append(str(ac.get("alvo")))
    print("trilha_ac5\t%s" % (aplicada or "-"))
    print("ac5_falhou_executada\t%d" % len(falhou))
    print("ac5_falhou_alvos\t%s" % (",".join(falhou) or "-"))
    print("trilha_completa\t%s" % ("sim" if completa else "nao"))
    print("fim\t%s" % fim)
    print("linhas_perdidas\t%s" % perdidas)
    print("trilha_incompleta\t%s" % ("sim" if motivo else "nao"))
    print("trilha_incompleta_motivo\t%s" % (motivo or "-"))
    if (aplicada or "-") != a.sinal_alvo:
        raise Recusa("AC-5 APLICADA na trilha = %s, na linha de fim da carga = %s" % (aplicada or "-", a.sinal_alvo))


def cmd_referencia(a):
    obj = ler_json(a.arquivo)
    if not isinstance(obj, dict) or obj.get("esquema") != ESQUEMA_REFERENCIA:
        raise ErroUso("referencia %s: esquema deve ser %r" % (a.arquivo, ESQUEMA_REFERENCIA))
    if not isinstance(obj.get("p99_ns"), dict):
        raise ErroUso("referencia %s sem o objeto p99_ns" % a.arquivo)
    cenario, perfil = obj.get("cenario"), obj.get("perfil")
    print("sha256\t%s" % sha256(a.arquivo))
    print("cenario\t%s" % cenario)
    print("perfil\t%s" % perfil)
    if cenario != "S3":
        raise Recusa("referencia %s e do cenario %r, e as campanhas sao S3" % (a.arquivo, cenario))
    if perfil != a.perfil:
        raise Recusa("referencia %s e do perfil %r, mas foi dada como --referencia-%s (B2 da V-W6: o programa a "
                     "recusaria, LigacaoControlador.java:204)" % (a.arquivo, perfil, a.perfil))


def main(argv):
    ap = argparse.ArgumentParser(prog="campanha_s3_conferir.py")
    sub = ap.add_subparsers(dest="cmd")
    p = sub.add_parser("parametros")
    p.add_argument("--arquivo", required=True)
    p.add_argument("--t-enc-pior-s", type=int, required=True)
    c = sub.add_parser("controlador")
    c.add_argument("--arquivo", required=True)
    c.add_argument("--hotrod-timeout-ms", type=int, required=True)
    c.add_argument("--exigir-politica", action="store_true")
    c.add_argument("--exigir-p2-desabilitada", action="store_true")
    m = sub.add_parser("meta")
    m.add_argument("--decisoes", required=True)
    m.add_argument("--braco", required=True, choices=("A", "B"))
    m.add_argument("--param-sha256", required=True)
    m.add_argument("--exigir-p2-desabilitada", action="store_true")
    t = sub.add_parser("trilha")
    t.add_argument("--decisoes", required=True)
    t.add_argument("--sinal-alvo", required=True)
    r = sub.add_parser("referencia")
    r.add_argument("--arquivo", required=True)
    r.add_argument("--perfil", required=True, choices=("sync", "async"))
    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return 2 if e.code else 0
    cmds = {"parametros": cmd_parametros, "controlador": cmd_controlador, "meta": cmd_meta, "trilha": cmd_trilha,
            "referencia": cmd_referencia}
    if a.cmd not in cmds:
        ap.print_usage(sys.stderr)
        return 2
    try:
        cmds[a.cmd](a)
        return 0
    except ErroUso as e:
        print("Erro: %s" % e, file=sys.stderr)
        return 2
    except ErroArquivo as e:
        print("Erro: %s" % e, file=sys.stderr)
        return 4
    except Recusa as e:
        print("Recusa: %s" % e, file=sys.stderr)
        return 5


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
