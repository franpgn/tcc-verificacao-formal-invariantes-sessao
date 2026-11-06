"""Apuracao de V por pares distintos (W-5c), sem numpy, para chamadores em python -I (calibracao_s3, W-9).

Copia literal (texto extraido por ast.get_source_segment) das funcoes e constantes de apuracao de V do
analysis/comparar_bracos.py do W-5c (branch feature/w5c-v-com-sonda, commit bc9cfdd, com a W-5d): pares distintos
(chave, classe) por execucao, somando o auditor online, a sonda de replay do W-10 (origem = sonda) e a varredura final
(so REAL), com as fases do replay tardio e da varredura pelo instante da invalidacao confirmada e as formas de I1
(pre-registro do benchmark refeito, rascunho a5ddac9, 2.3 a 2.5). O comparar_bracos importa numpy e nao roda em -I;
por isso o modulo sem numpy.

Fonte unica desde a integracao (06/11, feature/controlador-integracao): o comparar_bracos importa daqui as funcoes de
COPIADAS, os nomes de CONSTANTES e o ErroUso, e nao tem mais copia. test_calibracao_s3.Paridade confere que cada nome
e o mesmo objeto nos dois modulos e que o comparar_bracos nao os define. Os nomes COPIADAS e CONSTANTES ficam como a
lista do que o comparar_bracos importa.
"""
import calendar
import csv
import json
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # fases

import fases as _fases  # noqa: E402

COPIADAS = (
    '_int_ou_none', 'instante_ns', '_classe_v', 'ler_violacoes', 'ler_varredura', 'ler_eventos_por_chave',
    'no_da_falha', 'servidores_da_execucao', 'replica_do_no', '_instante_invalidacao', '_leitura_da_deteccao',
    '_forma_do_par', 'apurar_violacoes_execucao')
CONSTANTES = (
    'INVARIANTES_VIOL', 'ORIGEM_ONLINE', 'ORIGEM_SONDA', 'ORIGEM_VARREDURA', 'ORIGENS', 'TIPO_ONLINE',
    'TIPO_IMEDIATA', 'TIPO_TARDIA', 'TIPO_VARREDURA', 'ATRIB_DETECCAO', 'ATRIB_INVALIDACAO', 'ATRIB_SEM',
    'ATRIBUICOES', 'FORMA_1', 'FORMA_2', 'FORMA_OUTRA', 'FORMA_INDET', 'FORMAS_I1', 'OP_REPLAY', 'RC_CONFIRMADO',
    'COLUNAS_VARREDURA', 'TOL_CASAMENTO_NS', 'REGRA_V', '_RX_INSTANTE', '_RX_MOTIVO', '_RX_IDENTIDADE')


class ErroUso(Exception):
    """Contrato violado nos CSV de violacoes ou de varredura (o chamador converte no seu ErroUso)."""


INVARIANTES_VIOL = ("I1", "I2_REAL", "I4")
# W-5c: metodo de observacao do benchmark refeito (rascunho do pre-registro, docs/pre-registro-benchmark-refeito-
# 2026-11-06.md, commit a5ddac9, secao 2). V soma tres origens: o auditor online (coluna origem vazia), a sonda de
# replay do W-10 (origem = sonda, docs/contrato-csv-v2.md secao 7) e a varredura final (varredura-rep-NNN.csv,
# VarreduraFinal.escreverAchados, so REAL). Numerador = pares distintos (chave, invariante) por execucao (2.3).
ORIGEM_ONLINE, ORIGEM_SONDA, ORIGEM_VARREDURA = "online", "sonda", "varredura"
ORIGENS = (ORIGEM_ONLINE, ORIGEM_SONDA, ORIGEM_VARREDURA)
TIPO_ONLINE, TIPO_IMEDIATA, TIPO_TARDIA, TIPO_VARREDURA = "online", "imediata", "tardia", "varredura"
ATRIB_DETECCAO, ATRIB_INVALIDACAO, ATRIB_SEM = "deteccao", "invalidacao", "sem_invalidacao_localizada"
ATRIBUICOES = (ATRIB_DETECCAO, ATRIB_INVALIDACAO, ATRIB_SEM)
FORMA_1, FORMA_2, FORMA_OUTRA, FORMA_INDET = "forma1", "forma2", "outra", "indeterminada"
FORMAS_I1 = (FORMA_1, FORMA_2, FORMA_OUTRA, FORMA_INDET)
OP_REPLAY = "ReplayValidate"          # SessionOps.OP_REPLAY_VALIDATE; referencia_p99.OPERACOES_INSTRUMENTACAO
RC_CONFIRMADO = "NONE"                # OK_NOOP: Logout e Block confirmados (contrato 2, docs/contrato-csv-v2.md)
COLUNAS_VARREDURA = ("invariante", "chave", "classificacao", "detalhe")
# Tolerancia para casar uma violacao (instante de Instant.now(), InvariantAuditor) com a leitura que a revelou no
# rep-NNN.csv (EpochClock, ancorado uma vez no inicio): no smoke F2 do W-10 o instante ficou de 0 a 7,4 ms depois do
# fim da leitura nas 6 violacoes da sonda. 1 s fica duas ordens acima desse desvio; entre os candidatos da mesma sid
# (Validate/Logout na online, ReplayValidate na imediata) casa-se o mais proximo, e acima de 1 s nenhum (leitura
# fora do CSV, como a imediata no warm-up): sem leitura casada, a deteccao nao e forma 2.
TOL_CASAMENTO_NS = 1_000_000_000
REGRA_V = ("pares distintos (chave, invariante) com ao menos uma deteccao nas fases selecionadas, por execucao, "
           "somando as origens online, sonda (W-10) e varredura (so REAL); I1, I4 e I2 REAL (varredura: I2 "
           "administrativo e I2-PERDIDO REAL); linhas e contagem por origem so descritivas; online e replay "
           "imediato pelo instante da deteccao, replay tardio e varredura pelo instante da invalidacao confirmada "
           "(Logout NONE da sid; I2 e I2-PERDIDO: ultimo Block NONE da identidade); sem invalidacao localizada no "
           "CSV de eventos, conta sem filtro de fase (pre-registro do benchmark refeito, 2.3 e 2.4)")
_RX_INSTANTE = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$")
_RX_MOTIVO = re.compile(r"confirmad[ao] \(?([A-Z_]+)\)?")
_RX_IDENTIDADE = re.compile(r"de identidade (\S+) BLOCKED")


def _int_ou_none(x):
    return x if isinstance(x, int) and not isinstance(x, bool) else None


def instante_ns(txt):
    """Epoch ns de um java.time.Instant.toString() (UTC, fracao de 0 a 9 digitos)."""
    m = _RX_INSTANTE.match(txt.strip())
    if not m:
        raise ErroUso("instante invalido no CSV de violacoes: %r" % txt)
    seg = calendar.timegm(time.strptime(m.group(1), "%Y-%m-%dT%H:%M:%S"))
    return seg * 1_000_000_000 + int((m.group(2) or "").ljust(9, "0"))


def _classe_v(invariante, classificacao):
    """Classe de V da linha (DT-009; DT-001/DT-002) ou None: I1 e I4 exatos, I2 so com classificacao REAL."""
    if invariante == "I2":
        return "I2_REAL" if classificacao == "REAL" else None
    if invariante in ("I1", "I4"):
        return invariante
    return None


def ler_violacoes(caminho):
    """(deteccoes de V, sonda_ligada) do violacoes-rep-NNN.csv, ou None se o arquivo nao existe.

    Formato: InvariantAuditor.dumpCsv (:251-278), cabecalho instante,invariante,chave,mensagem[,fase]
    [,classificacao][,origem]. A coluna origem so existe com a sonda de replay (W-10): vazia = auditor online,
    "sonda" = sonda; outro valor: ErroUso. I1 e I4 com rotulo exato; I2 so com rotulo exato "I2" e
    classificacao "REAL"; I2_JANELA_CASCATA, I2_CASCATA_INCOMPLETA, I2 de outra classe, I3, I5, I6 e os
    contadores *_concorrentes (so no stdout, SessionOps.java:246-260) ficam fora (DT-001/DT-002). Cada deteccao:
    {"origem", "classe", "rotulo", "chave", "t_ns", "motivo" (o motivo confirmado lido da mensagem), "identidade"}.
    """
    p = Path(caminho)
    if not p.exists():
        return None
    dets = []
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.DictReader(fh)
        sonda = "origem" in (r.fieldnames or ())
        for x in r:
            orig = (x.get("origem") or "").strip()
            if orig not in ("", ORIGEM_SONDA):
                raise ErroUso("origem desconhecida no CSV de violacoes %s: %r" % (p, orig))
            classe = _classe_v((x.get("invariante") or "").strip(), (x.get("classificacao") or "").strip())
            if classe is None:
                continue
            m = _RX_MOTIVO.search(x.get("mensagem") or "")
            dets.append({"origem": ORIGEM_SONDA if orig else ORIGEM_ONLINE, "classe": classe,
                         "rotulo": (x.get("invariante") or "").strip(), "chave": (x.get("chave") or "").strip(),
                         "t_ns": instante_ns(x.get("instante") or ""), "motivo": m.group(1) if m else None,
                         "identidade": None})
    return dets, sonda


def ler_varredura(caminho):
    """(deteccoes REAL de V, achados fora de V) do varredura-rep-NNN.csv, ou None se o arquivo nao existe.

    Formato: VarreduraFinal.escreverAchados, cabecalho invariante,chave,classificacao,detalhe (virgulas do detalhe
    trocadas por ';'). Contam I1 REAL e I4 REAL (chave = sid), I2 REAL (chave = sid; a identidade sai do detalhe,
    "de identidade X BLOCKED") e I2-PERDIDO REAL (chave = identidade), os dois na classe I2_REAL (pre-registro
    2.2). INCERTA, ESPERADA-POR-DESENHO, INEXPLICADA e rotulos desconhecidos ficam fora e sao contados.
    """
    p = Path(caminho)
    if not p.exists():
        return None
    dets, fora = [], 0
    with open(p, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        cab = next(r, None)
        if cab is None or tuple(c.strip() for c in cab) != COLUNAS_VARREDURA:
            raise ErroUso("cabecalho da varredura inesperado em %s: %r" % (p, cab))
        for x in r:
            if not x or not any(c.strip() for c in x):
                continue
            if len(x) < 3:
                raise ErroUso("linha da varredura invalida em %s: %r" % (p, x))
            inv, chave, cl, detalhe = x[0].strip(), x[1].strip(), x[2].strip(), ",".join(x[3:])
            if cl != "REAL" or inv not in ("I1", "I4", "I2", "I2-PERDIDO"):
                fora += 1
                continue
            if inv in ("I1", "I4"):
                m = _RX_MOTIVO.search(detalhe)
                classe, motivo, ident = inv, (m.group(1) if m else None), None
            else:
                m = _RX_IDENTIDADE.search(detalhe)
                classe, motivo = "I2_REAL", None
                ident = chave if inv == "I2-PERDIDO" else (m.group(1) if m else None)
            dets.append({"origem": ORIGEM_VARREDURA, "classe": classe, "rotulo": inv, "chave": chave, "t_ns": None,
                         "motivo": motivo, "identidade": ident})
    return dets, fora


def ler_eventos_por_chave(caminho, chaves, t_fim_carga_obs_ns):
    """({chave: [(operation, start_ns, end_ns, replica, return_code)]}, custo da sonda) do rep-NNN.csv.

    So guarda as chaves pedidas (sids e identidades das deteccoes). Le tambem os ReplayValidate (que as analises de
    desempenho descartam, energia_proxy.ler_eventos_inicio) e devolve o custo da sonda: eventos, imediatos (inicio
    ate o fim observado da carga), tardios (depois dele) e sids distintas no tardio. O arquivo ja passou pela
    conferencia de contrato de ler_eventos_inicio.
    """
    out = {}
    custo = {"eventos": 0, "imediatos": 0, "tardios": 0, "sids_tardios": 0}
    tardias = set()
    with open(caminho, newline="", encoding="utf-8") as fh:
        r = csv.reader(fh)
        next(r, None)
        for x in r:
            if len(x) < 7:
                continue
            try:
                ini, fim = int(x[2]), int(x[3])
            except ValueError:
                raise ErroUso("linha de eventos invalida em %s: %s" % (caminho, x)) from None
            if x[1] == OP_REPLAY:
                custo["eventos"] += 1
                if ini > t_fim_carga_obs_ns:
                    custo["tardios"] += 1
                    tardias.add(x[6])
                else:
                    custo["imediatos"] += 1
            if x[6] in chaves:
                out.setdefault(x[6], []).append((x[1], ini, fim, x[4], x[5]))
    custo["sids_tardios"] = len(tardias)
    return out, custo


def no_da_falha(caminho_marcadores):
    """node= do primeiro marcador start (fault=F2 node=isn3 event=start t_epoch_ns=..., inject-*.sh) ou None."""
    p = Path(caminho_marcadores)
    if not p.exists():
        return None
    for bruta in p.read_text(encoding="utf-8").splitlines():
        linha = bruta.strip()
        if not linha or linha.startswith("#"):
            continue
        toks = dict(t.split("=", 1) for t in linha.split() if "=" in t)
        if toks.get("event") == "start":
            return toks.get("node") or None
    return None


def servidores_da_execucao(execucao):
    """Lista do --servers do execucao.json do orquestrador (run-campanha-s3.sh: ordem isn1, isn2, isn3) ou None."""
    p = Path(execucao) / "execucao.json"
    if not p.exists():
        return None
    try:
        j = json.loads(p.read_text(encoding="utf-8"))
    except ValueError:
        raise ErroUso("execucao.json ilegivel: %s" % p) from None
    cmd = j.get("comando") if isinstance(j, dict) else None
    if not isinstance(cmd, list):
        return None
    for i, a in enumerate(cmd[:-1]):
        if a == "--servers" and isinstance(cmd[i + 1], str):
            return [s.strip() for s in cmd[i + 1].split(",")]
    return None


def replica_do_no(no, servidores):
    """Rotulo da coluna replica do no isnK (K-esimo item do --servers) ou None."""
    m = re.fullmatch(r"isn(\d+)", no or "")
    if not m or not servidores:
        return None
    k = int(m.group(1)) - 1
    return servidores[k] if 0 <= k < len(servidores) else None


def _instante_invalidacao(det, ev):
    """Instante (end_ns) da invalidacao confirmada da deteccao no CSV de eventos, ou None (pre-registro 2.4).

    I1/I4: o Logout NONE mais antigo da sid, se o motivo confirmado for LOGOUT ou ilegivel (a cascata de uma sid,
    motivo ADMIN_BLOCK, nao tem evento com a chave da sid). I2 e I2-PERDIDO: o ultimo Block NONE da identidade.
    """
    if det["classe"] in ("I1", "I4"):
        if det["motivo"] not in (None, "LOGOUT"):
            return None
        ts = [e[2] for e in ev.get(det["chave"], ()) if e[0] == "Logout" and e[4] == RC_CONFIRMADO]
        return min(ts) if ts else None
    if not det["identidade"]:
        return None
    ts = [e[2] for e in ev.get(det["identidade"], ()) if e[0] == "Block" and e[4] == RC_CONFIRMADO]
    return max(ts) if ts else None


def _leitura_da_deteccao(det, tipo, ev):
    """(replica, start_ns) da leitura que revelou a deteccao (evento mais proximo da mesma sid, ate
    TOL_CASAMENTO_NS) ou (None, None). Online: Validate ou Logout; imediata: ReplayValidate; tardia e varredura
    leem depois da carga e nao tem leitura na janela da falha."""
    if tipo == TIPO_ONLINE:
        ops = ("Validate", "Logout")
    elif tipo == TIPO_IMEDIATA:
        ops = (OP_REPLAY,)
    else:
        return None, None
    t, melhor = det["t_ns"], None
    for e in ev.get(det["chave"], ()):
        if e[0] not in ops:
            continue
        gap = max(0, e[1] - t, t - e[2])
        if gap <= TOL_CASAMENTO_NS and (melhor is None or (gap, -e[1]) < melhor[0]):
            melhor = ((gap, -e[1]), e)
    return (melhor[1][3], melhor[1][1]) if melhor else (None, None)


def _forma_do_par(fs):
    """Forma de um par de I1 pelas formas das suas deteccoes: indeterminada se alguma for; forma 1 se alguma for
    (depende do Logout, comum ao par); forma 2 so se todas forem; senao outra (pre-registro 2.5)."""
    if FORMA_INDET in fs:
        return FORMA_INDET
    if FORMA_1 in fs:
        return FORMA_1
    if all(f == FORMA_2 for f in fs):
        return FORMA_2
    return FORMA_OUTRA


def apurar_violacoes_execucao(d, nnn, ev_path, t_fim_carga_obs_ns, dentro, args_fase, marcadores_nome):
    """Numerador de V de uma execucao pela regra do benchmark refeito (W-5c), ou None sem violacoes-rep.

    Pre-registro (rascunho a5ddac9): 2.3 pares distintos (chave, classe) das tres origens; 2.4 online e replay
    imediato pelo instante da deteccao, replay tardio (deteccao da sonda depois do fim observado da carga, a maior
    end_ns das acoes S3 e dos eventos da carga) e varredura pelo instante da invalidacao confirmada; sem ela no
    CSV, conta sem filtro de fase; 2.5 formas de I1 pelo no que confirmou o Logout (coluna replica) e pela leitura
    que revelou a deteccao. Devolve os totais, as descritivas e a lista dos pares (pares_lista). W-5d: devolve
    tambem os totais sem as deteccoes da varredura (pares_sem_varredura, total_pares_sem_varredura,
    total_linhas_sem_varredura, total_pares_sem_forma2_sem_varredura), que o comparar usa nos dois bracos do bloco
    em que a varredura falhou em algum braco.
    """
    lido = ler_violacoes(d / ("violacoes-rep-%s.csv" % nnn))
    if lido is None:
        return None
    viol, sonda = lido
    lv = ler_varredura(d / ("varredura-rep-%s.csv" % nnn))
    if (d / ("varredura-rep-%s.erro.txt" % nnn)).exists():
        estado_varr = "erro"
    else:
        estado_varr = "presente" if lv is not None else "ausente"
    varr, fora = lv if lv is not None else ([], 0)
    dets = viol + varr
    chaves = {x["chave"] for x in dets} | {x["identidade"] for x in dets if x["identidade"]}
    if ev_path.exists():
        ev, custo = ler_eventos_por_chave(ev_path, chaves, t_fim_carga_obs_ns)
    else:
        ev, custo = {}, {"eventos": 0, "imediatos": 0, "tardios": 0, "sids_tardios": 0}

    no = no_da_falha(d / marcadores_nome)
    replica_falha, avaliaveis, motivo_formas = None, True, None
    if args_fase[0] is None:
        motivo_formas = "sem marcador start: sem janela de falha (nenhuma forma 1 ou 2)"
    elif no is None:
        avaliaveis, motivo_formas = False, "formas nao avaliaveis: marcador start sem node="
    else:
        replica_falha = replica_do_no(no, servidores_da_execucao(d))
        if replica_falha is None:
            avaliaveis = False
            motivo_formas = ("formas nao avaliaveis: sem a replica do no da falha %s (execucao.json com --servers na "
                             "ordem isn1, isn2, isn3)" % no)
    familia_durante = _fases.FAMILIAS[_fases.DURANTE]

    def na_janela_falha(t):
        return _fases.fase_de(t, *args_fase) in familia_durante

    def replica_do_logout(sid):
        lo = [e for e in ev.get(sid, ()) if e[0] == "Logout" and e[4] == RC_CONFIRMADO]
        return min(lo, key=lambda e: e[2])[3] if lo else None

    pares = {}
    for det in dets:
        if det["origem"] == ORIGEM_ONLINE:
            tipo = TIPO_ONLINE
        elif det["origem"] == ORIGEM_SONDA:
            tipo = TIPO_TARDIA if det["t_ns"] > t_fim_carga_obs_ns else TIPO_IMEDIATA
        else:
            tipo = TIPO_VARREDURA
        if tipo in (TIPO_ONLINE, TIPO_IMEDIATA):
            atrib, t_at = ATRIB_DETECCAO, det["t_ns"]
        else:
            t_at = _instante_invalidacao(det, ev)
            atrib = ATRIB_INVALIDACAO if t_at is not None else ATRIB_SEM
        forma, rep_leitura = None, None
        if det["classe"] == "I1":
            rep_leitura, t_leitura = _leitura_da_deteccao(det, tipo, ev)
            if not avaliaveis:
                forma = FORMA_INDET
            elif replica_falha is None:
                forma = FORMA_OUTRA
            else:
                rep_logout = replica_do_logout(det["chave"])
                if rep_logout == replica_falha:
                    forma = FORMA_1
                elif rep_logout is not None and rep_leitura == replica_falha and na_janela_falha(t_leitura):
                    forma = FORMA_2
                else:
                    forma = FORMA_OUTRA
        pares.setdefault((det["chave"], det["classe"]), []).append(
            {"origem": det["origem"], "tipo": tipo, "rotulo": det["rotulo"], "t_ns": det["t_ns"], "atribuicao": atrib,
             "t_atribuido_ns": t_at, "conta": atrib == ATRIB_SEM or dentro(t_at), "forma": forma,
             "replica_leitura": rep_leitura})

    tot_pares = {k: 0 for k in INVARIANTES_VIOL}
    tot_linhas = {k: 0 for k in INVARIANTES_VIOL}
    por_origem = {o: {"linhas": 0, "pares": 0} for o in ORIGENS}
    atribuicao = {a: 0 for a in ATRIBUICOES}
    formas = {f: 0 for f in FORMAS_I1}
    # W-5d (V-W5c, secao 5): os mesmos totais sem as deteccoes da varredura, para o bloco com varredura com erro
    sv_pares = {k: 0 for k in INVARIANTES_VIOL}
    sv_linhas, sv_forma2 = 0, 0
    multiplas = 0
    lista = []
    for (chave, classe), ds in sorted(pares.items(), key=lambda kv: (kv[0][1], kv[0][0])):
        cont = [x for x in ds if x["conta"]]
        base = cont or ds
        origens = [o for o in ORIGENS if any(x["origem"] == o for x in base)]
        forma = _forma_do_par([x["forma"] for x in base]) if classe == "I1" else None
        cont_sv = [x for x in cont if x["origem"] != ORIGEM_VARREDURA]
        if cont_sv:
            sv_pares[classe] += 1
            sv_linhas += len(cont_sv)
            if classe == "I1" and _forma_do_par([x["forma"] for x in cont_sv]) == FORMA_2:
                sv_forma2 += 1
        if cont:
            tot_pares[classe] += 1
            tot_linhas[classe] += len(cont)
            for o in origens:
                por_origem[o]["pares"] += 1
            for x in cont:
                por_origem[x["origem"]]["linhas"] += 1
                atribuicao[x["atribuicao"]] += 1
            multiplas += 1 if len(origens) > 1 else 0
            if forma is not None:
                formas[forma] += 1
        lista.append({"chave": chave, "classe": classe, "conta": bool(cont), "origens": origens, "forma": forma,
                      "deteccoes": sorted(ds, key=lambda x: (x["t_atribuido_ns"] is None, x["t_atribuido_ns"] or 0,
                                                              x["tipo"]))})
    total = sum(tot_pares.values())
    if avaliaveis:
        sem_f2 = total - formas[FORMA_2]
    else:
        sem_f2 = total if tot_pares["I1"] == 0 else None
    total_sv = sum(sv_pares.values())
    if avaliaveis:
        sem_f2_sv = total_sv - sv_forma2
    else:
        sem_f2_sv = total_sv if sv_pares["I1"] == 0 else None
    teto = None
    s3r = d / ("s3-resumo-rep-%s.json" % nnn)
    if s3r.exists():
        try:
            j = json.loads(s3r.read_text(encoding="utf-8"))
        except ValueError:
            raise ErroUso("s3-resumo ilegivel: %s" % s3r) from None
        teto = _int_ou_none(((j.get("parametros") if isinstance(j, dict) else None) or {}).get("sonda_tardia_max"))
    return {"regra": REGRA_V, "sonda": bool(sonda), "varredura": estado_varr,
            "instrumentos": bool(sonda) or estado_varr != "ausente",
            "pares": tot_pares, "total_pares": total, "linhas": tot_linhas, "total_linhas": sum(tot_linhas.values()),
            "por_origem": por_origem, "pares_multiplas_origens": multiplas, "atribuicao": atribuicao,
            "formas_i1": formas, "formas_avaliaveis": avaliaveis, "formas_motivo": motivo_formas,
            "no_falha": no, "replica_no_falha": replica_falha, "total_pares_sem_forma2": sem_f2,
            "pares_sem_varredura": sv_pares, "total_pares_sem_varredura": total_sv,
            "total_linhas_sem_varredura": sv_linhas, "total_pares_sem_forma2_sem_varredura": sem_f2_sv,
            "varredura_fora_de_v": fora, "replay_validate": custo, "sonda_tardia_max": teto,
            "sonda_tardia_no_teto": None if teto is None else custo["sids_tardios"] >= teto,
            "t_fim_carga_obs_ns": t_fim_carga_obs_ns, "pares_lista": lista}
