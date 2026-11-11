"""Compara recalculo.json (independente) com comparacao-principal.json e comparacao-semfalha.json (oficial)."""
import json
import math
import sys
from pathlib import Path

R = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
OF = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
SF = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))
V = R["por_execucao"]
DESF = {"taxa_violacoes_por_1000": ("V_por_1000", False, 1), "acao_s3_p99_com_espera": ("p99_com_espera", True, 1e9),
        "acao_s3_p99_sem_espera": ("p99_sem_espera", True, 1e9), "vazao_util": ("vazao_util", True, 1),
        "falhas_percebidas_por_1000": ("falhas_por_1000", False, 1), "taxa_erro": ("taxa_erro", False, 1),
        "exposicao_janela_sids": ("exposicao", False, 1), "perda_permanente_sids": ("perda_permanente", False, 1),
        "divergencia_persistente": ("divergencia", False, 1), "evento_p99:Validate": ("p99_validate", True, 1e9)}


def rel(a, b):
    if a is None or b is None:
        return 0.0 if a is None and b is None else float("inf")
    return abs(a - b) / max(abs(b), 1e-12) if b != 0 else abs(a)


def exec_de(cel, bl, br):
    for e in V:
        if e.endswith("-%s-%s" % (cel, br)):
            pass
    return None


# manifesto: reconstruido do proprio recalculo (execucoes por celula/bloco/braco) via nomes do JSON oficial
print("== 1. Valores por execucao (F2) e diferencas por bloco (celulas de 2 bracos)")
for fonte in (OF, SF):
    for cel, c in fonte["celulas"].items():
        ex = c["execucoes"]
        for nome, (campo, log, esc) in DESF.items():
            dz = c["desfechos"].get(nome)
            if not dz:
                continue
            pior, n = 0.0, 0
            if "y_por_bloco" in dz:
                for bl, porb in dz["y_por_bloco"].items():
                    for br, y in porb.items():
                        mine = V[Path(ex[bl][br]).name].get(campo)
                        mine = None if mine is None else mine * esc
                        pior, n = max(pior, rel(mine, y)), n + 1
            else:
                for bl, d in (dz.get("d_b") or {}).items():
                    a = V[Path(ex[bl]["A"]).name].get(campo)
                    b = V[Path(ex[bl]["B"]).name].get(campo)
                    if a is None or b is None:
                        mine = None
                    else:
                        mine = math.log(b / a) if log else b - a
                    pior, n = max(pior, abs(mine - d) if (mine is not None and d is not None) else
                                  (0.0 if mine is None and d is None else float("inf"))), n + 1
                for bl, nn in (dz.get("n_por_bloco") or {}).items():
                    if nome == "taxa_violacoes_por_1000":
                        for br in ("A", "B"):
                            if V[Path(ex[bl][br]).name]["n_acoes"] != nn[br]:
                                print("   n_acoes difere", cel, bl, br, nn[br], V[Path(ex[bl][br]).name]["n_acoes"])
            marca = "OK" if pior < 1e-9 else "DIFERE"
            print("   %-11s %-28s n=%-3d maior dif=%.3g %s" % (cel, nome, n, pior, marca))

print("\n== 2. Efeitos da F2 (oficial x recalculo)")
of2 = OF["celulas"]["ASYNC-F2"]
f2 = R["f2"]
for nome in DESF:
    if nome not in f2["efeitos"]:
        continue
    for ef in ("A1", "A4", "interacao"):
        o = of2["desfechos"][nome]["efeitos"][ef]
        m = f2["efeitos"][nome][ef]
        d_est = rel(m["estimativa"], o["estimativa"])
        d_ic = max(rel(m["ic_t"][0], o["ic_t"][0]), rel(m["ic_t"][1], o["ic_t"][1]))
        d_p = abs(m["p_perm"] - o["p_permutacao"])
        ok = "OK" if max(d_est, d_ic, d_p) < 1e-9 else "DIFERE"
        print("   %-28s %-9s est %.4f (of %.4f) IC [%.4f; %.4f] p %.4f (of %.4f) %s"
              % (nome, ef, m["estimativa"], o["estimativa"], m["ic_t"][0], m["ic_t"][1], m["p_perm"],
                 o["p_permutacao"], ok))

print("\n== 3. Regra intersecao-uniao e Holm")
for ef in ("A1", "A4"):
    m = f2["regra_iu"][ef]
    o = of2["efeitos_principais"][ef]["regra_iu"]
    cg = OF["conclusao_global"]["celulas"]["ASYNC-F2:" + ef]
    print("   %s: recalculo p_IU %.4f Holm %.4f %s | oficial p_IU %.4f Holm %.4f %s"
          % (ef, m["p_IU"], m["p_IU_holm"], m["resultado"], o["p_iu"], cg["p_iu_holm"], o["resultado"]))
    print("      p_V %.4f p_L(nao-inferioridade p99) %.4g H_V %s H_L %s vazao NI %s"
          % (m["p_V"], m["p_L"], m["H_V"], m["H_L"], m["vazao_ni"]))
print("   Holm dos secundarios (recalculo; cpu, energia, delta_rec e delta_conv com p oficial):")
for ef in ("A1", "A4"):
    for n, ph in R["f2"]["holm_secundarios"][ef].items():
        o = of2["desfechos"][n]["efeitos"][ef].get("p_holm")
        print("      %s %-36s %.4f (oficial %s)" % (ef, n, ph, "—" if o is None else "%.4f" % o))

print("\n== 4. Medias por braco na F2 (recalculo)")
for nome in ("taxa_violacoes_por_1000", "perda_permanente_sids", "exposicao_janela_sids", "falhas_percebidas_por_1000",
             "taxa_erro", "acao_s3_p99_com_espera", "vazao_util"):
    mb = f2["efeitos"][nome]["A4"]["medias_bracos"]
    print("   %-28s PO %.4f PL %.4f AO %.4f AL %.4f" % (nome, mb["PO"], mb["PL"], mb["AO"], mb["AL"]))
print("   acoes do controlador executadas na F2:", f2["acoes_controlador"])
tot = {}
for br in ("PO", "PL", "AO", "AL"):
    s = [e for e in V if e.endswith("-ASYNC-F2-" + br)]
    tot[br] = (sum(V[e]["V_pares"] for e in s), sum(V[e]["n_acoes"] for e in s),
               sum(V[e].get("perda_permanente", 0) for e in s), sum(V[e].get("exposicao", 0) for e in s))
print("   totais F2 (pares V, acoes, perda permanente, exposicao):", tot)

print("\n== 5. Celulas descritivas")
for cel, r in R["descritivas"].items():
    print("   %s: V pares A=%s B=%s; acoes A=%s B=%s; acoes do controlador A=%s B=%s; p99 B/A %.3f IC %s"
          % (cel, r["V_pares"]["A"], r["V_pares"]["B"], r["n_acoes"]["A"], r["n_acoes"]["B"],
             r["acoes_controlador"]["A"], r["acoes_controlador"]["B"], r["p99_razao_media"], r["p99_ic_t"]))
