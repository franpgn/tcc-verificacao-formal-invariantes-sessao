#!/usr/bin/env python3
"""B-26.2: confere os criterios do smoke local da sonda de topologia a partir do stdout JSONL da CLI.

Uso: python analise.py [dir_do_smoke]   (padrao: o diretorio deste arquivo)
Grava analise.json ao lado e sai com 0 se P1, P2 e P3b passam (P3a e reportado; ver o relatorio).

Smoke local: tempos nao sao evidencia de deteccao na VM.
"""
import json
import os
import re
import sys

PERIODO_NS = 2_000_000_000
PRAZO_NS = 1_500_000_000
NO_PARADO = 2  # b262-isn3, terceiro de --servers
NOS_ESPERADOS = 3


def ler_jsonl(caminho):
    amostras, resumo = [], None
    with open(caminho, encoding="utf-8") as f:
        for linha in f:
            linha = linha.strip()
            if not linha:
                continue
            o = json.loads(linha)
            if "resumo" in o:
                resumo = o["resumo"]
            else:
                amostras.append(o)
    return amostras, resumo


def ler_custo(stderr_txt):
    m = re.search(r"custo curl_lancados=(\d+) rodadas=(\d+) cpu_jvm_ms=(-?\d+)", stderr_txt)
    if not m:
        return None
    return {"curl_lancados": int(m.group(1)), "rodadas": int(m.group(2)), "cpu_jvm_ms": int(m.group(3))}


def criterios_comuns(amostras, resumo, custo):
    r = {}
    r["amostras"] = len(amostras)
    r["resumo_presente"] = resumo is not None
    r["rodadas_igual_amostras"] = resumo is not None and resumo["rodadas"] == len(amostras)
    r["puladas_zero"] = resumo is not None and resumo["puladas"] == 0
    r["anomalas_igual_resumo"] = resumo is not None and resumo["anomalas"] == sum(a["anomala"] for a in amostras)
    r["tempos_crescentes"] = all(amostras[i]["t_epoch_ns"] <= amostras[i + 1]["t_epoch_ns"]
                                 for i in range(len(amostras) - 1))
    if custo is not None:
        n, rod = custo["curl_lancados"], custo["rodadas"]
        # 3 por rodada; a rodada interrompida pelo fim da duracao pode ter lancado e nao entregue
        r["curl_por_rodada"] = round(n / rod, 3) if rod else None
        r["curl_3_por_rodada"] = n in (NOS_ESPERADOS * rod, NOS_ESPERADOS * (rod + 1))
        r["cpu_jvm_ms"] = custo["cpu_jvm_ms"]
        r["cpu_jvm_ms_por_rodada"] = round(custo["cpu_jvm_ms"] / rod, 1) if rod else None
    return r


def analisar_estavel(amostras, resumo, custo):
    """P1/P2: cluster HEALTHY, nenhuma amostra anomala."""
    r = criterios_comuns(amostras, resumo, custo)
    r["zero_anomalas"] = len(amostras) > 0 and not any(a["anomala"] for a in amostras)
    r["todos_ok"] = all(p["motivo"] == "ok" for a in amostras for p in a["por_no"])
    chaves = ["resumo_presente", "rodadas_igual_amostras", "puladas_zero", "anomalas_igual_resumo",
              "tempos_crescentes", "zero_anomalas", "todos_ok"]
    if custo is not None:
        chaves.append("curl_3_por_rodada")
    r["passou"] = all(r[k] for k in chaves)
    return r


def analisar_queda(amostras, resumo, custo, t_stop, t_start_inicio, t_start_fim):
    """P3: queda de b262-isn3 em t_stop e volta em t_start."""
    r = criterios_comuns(amostras, resumo, custo)
    seg = lambda ns: round(ns / 1e9, 3)
    antes = [a for a in amostras if a["t_epoch_ns"] < t_stop]
    r["sem_anomalia_antes_do_stop"] = len(antes) > 0 and not any(a["anomala"] for a in antes)
    depois = [a for a in amostras if a["t_epoch_ns"] >= t_stop]
    primeira = next((a for a in depois if a["anomala"]), None)
    limite = 2 * PERIODO_NS + PRAZO_NS
    r["deteccao_s"] = seg(primeira["t_epoch_ns"] - t_stop) if primeira else None
    r["deteccao_limite_s"] = seg(limite)
    r["deteccao_no_limite"] = primeira is not None and primeira["t_epoch_ns"] - t_stop <= limite

    # fase de queda: da primeira anomalia ate o inicio do start
    queda = [a for a in depois if primeira and primeira["t_epoch_ns"] <= a["t_epoch_ns"] < t_start_inicio]
    r["amostras_na_queda"] = len(queda)
    r["queda_toda_anomala"] = len(queda) > 0 and all(a["anomala"] for a in queda)
    motivos_parado = [a["por_no"][NO_PARADO]["motivo"] for a in queda]
    r["no_parado_timeout_ou_falha"] = len(queda) > 0 and all(
        m == "timeout" or m.startswith("falha:") for m in motivos_parado)
    r["no_parado_motivos"] = sorted(set(motivos_parado))
    outros = [p for a in queda for i, p in enumerate(a["por_no"]) if i != NO_PARADO]
    respondidos = [p for p in outros if p["respondeu"]]
    r["outros_respondidos"] = len(respondidos)
    r["outros_sem_resposta"] = len(outros) - len(respondidos)
    r["outros_nos_2_de_3"] = len(respondidos) > 0 and all(
        p["motivo"] == "nos:2<3" for p in respondidos)
    r["outros_motivos_sem_resposta"] = sorted(set(p["motivo"] for p in outros if not p["respondeu"]))

    # volta: sufixo final sem anomalia depois do start
    pos = [a for a in amostras if a["t_epoch_ns"] >= t_start_inicio]
    k = len(pos)
    while k > 0 and not pos[k - 1]["anomala"]:
        k -= 1
    sufixo = pos[k:]
    r["volta_observada"] = len(sufixo) > 0
    r["amostras_sem_anomalia_no_fim"] = len(sufixo)
    r["volta_s_desde_start_inicio"] = seg(sufixo[0]["t_epoch_ns"] - t_start_inicio) if sufixo else None
    r["volta_s_desde_start_fim"] = seg(sufixo[0]["t_epoch_ns"] - t_start_fim) if sufixo else None
    r["podman_start_s"] = seg(t_start_fim - t_start_inicio)
    rebal = [a for a in amostras if any("HEALTHY_REBALANCING" in p["motivo"] for p in a["por_no"])]
    r["amostras_healthy_rebalancing"] = len(rebal)
    r["healthy_rebalancing_s"] = seg(rebal[-1]["t_epoch_ns"] - rebal[0]["t_epoch_ns"]) if rebal else 0.0
    chaves = ["resumo_presente", "rodadas_igual_amostras", "puladas_zero", "anomalas_igual_resumo",
              "tempos_crescentes", "sem_anomalia_antes_do_stop", "deteccao_no_limite", "queda_toda_anomala",
              "no_parado_timeout_ou_falha", "outros_nos_2_de_3", "volta_observada"]
    if custo is not None:
        chaves.append("curl_3_por_rodada")
    r["falhou"] = [c for c in chaves if not r[c]]
    r["passou"] = not r["falhou"]
    return r


def ler_json(caminho):
    with open(caminho, encoding="utf-8") as f:
        return json.load(f)


def stderr_de(d):
    """stderr da CLI, guardado como stderr.json (runs/**/*.txt fica fora do git pela D-005)."""
    return "\n".join(ler_json(os.path.join(d, "stderr.json"))["linhas"])


def main(base):
    res = {"aviso": "Smoke local: tempos nao sao evidencia de deteccao na VM."}
    for caso in ("P1", "P2"):
        d = os.path.join(base, caso)
        am, rs = ler_jsonl(os.path.join(d, "amostras.jsonl"))
        res[caso] = analisar_estavel(am, rs, ler_custo(stderr_de(d)))
    for caso in ("P3a", "P3b"):
        d = os.path.join(base, caso)
        am, rs = ler_jsonl(os.path.join(d, "amostras.jsonl"))
        m = ler_json(os.path.join(d, "marcos.json"))
        res[caso] = analisar_queda(am, rs, ler_custo(stderr_de(d)), m["t_stop_inicio_ns"],
                                   m["t_start_inicio_ns"], m["t_start_fim_ns"])
    res["podman_estado_igual"] = ler_json(os.path.join(base, "podman-estado.json"))["igual"]
    with open(os.path.join(base, "analise.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(res, f, indent=1, ensure_ascii=False)
        f.write("\n")
    for caso in ("P1", "P2", "P3a", "P3b"):
        print(caso, "PASSOU" if res[caso]["passou"] else "FALHOU " + str(res[caso].get("falhou", "")))
    return 0 if all(res[c]["passou"] for c in ("P1", "P2", "P3b")) and res["podman_estado_igual"] else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))))
