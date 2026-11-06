"""Confere que a saida do orquestrador fatorial (DT-027) e lida pelo comparar_bracos.py do W-5e (wt-w5e, d1bb887).

Uso (de qualquer diretorio):
  python -E -P runs/orquestrador-fatorial/scripts/conferir_w5e.py <saida do orquestrador> <wt-w5e>/analysis <trabalho>

<saida do orquestrador> e uma saida de scripts/run-campanha-s3.sh com a matriz fatorial (o caso S36 de
scripts/run-campanha-s3.test.sh, bloco 2x2 com F2 e t_fusao_ns pela trilha, guardado com CS_TESTE_GUARDAR; o S34, sem
falha, nao tem t_fusao_ns). Ela e copiada para <trabalho>/saida; o que vem do
orquestrador e mantido: o manifesto-bracos.tsv (bracos PO, PL, AO e AL) e o execucao.json de cada braco (fusao,
controller, bloco, t_fusao_ns, comando). Os arquivos de dados de cada execucao (eventos, acoes S3, violacoes,
varredura, convergencia, marcadores e trilha) sao trocados pelos dados sinteticos dos geradores de teste do W-5c/W-5e
(test_comparar_bracos_w5c.escrever_inst e _trilha, test_comparar_bracos_fatorial.acoes_lat), porque o duble de java
da suite do orquestrador nao grava dados de carga. A trilha sintetica segue o "controller" do execucao.json.

Roda a CLI do comparar_bracos.py sobre o manifesto do orquestrador (a celula do bloco fatorial como teste) e depois
duas vezes com um execucao.json adulterado (fusao trocada num braco; t_fusao_ns invalido), que o W-5e tem de recusar
(exit 2). Imprime o resultado de cada chamada e sai com 0 se as tres sairam como esperado.
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path


def rodar_cli(analysis, manifesto, saida_json, celulas_teste):
    cmd = [sys.executable, "-E", "-P", str(Path(analysis) / "comparar_bracos.py"), "--manifesto", str(manifesto),
           "--semente-boot", "5", "--saida", str(saida_json), "--delta-latencia", "0.10", "--w-analise-s", "5",
           "--w-recovery-s", "60", "--celulas-teste", ",".join(celulas_teste), "--n-boot", "200", "--n-hier", "200"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def mostrar(texto, trabalho):
    """Imprime sem o caminho absoluto do diretorio de trabalho (o registro vai ao repositorio)."""
    for forma in {str(trabalho), str(trabalho.resolve()), str(trabalho).replace("\\", "\\\\"),
                  str(trabalho.resolve()).replace("\\", "\\\\")}:
        texto = texto.replace(forma, "<trabalho>")
    print(texto)


def main(argv):
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    origem, analysis, trabalho = Path(argv[0]), Path(argv[1]), Path(argv[2])
    sys.path.insert(0, str(analysis))
    import test_comparar_bracos_w5c as w5c  # noqa: E402
    import test_comparar_bracos_fatorial as fat  # noqa: E402

    saida = trabalho / "saida"
    if saida.exists():
        shutil.rmtree(saida)
    for velho in trabalho.glob("comparacao*"):   # o comparar_bracos.py nunca sobrescreve a saida (.json e .md)
        velho.unlink()
    shutil.copytree(origem, saida)
    man = saida / "manifesto-bracos.tsv"
    linhas = [l.split("\t") for l in man.read_text(encoding="utf-8").splitlines()[1:] if l.strip()]
    mostrar("manifesto do orquestrador: %s" % man, trabalho)
    print("  " + man.read_text(encoding="utf-8").replace("\n", "\n  ").rstrip())
    celulas = sorted({c for _b, c, br, _e in linhas if br in fat.ARMS})
    for bloco, celula, braco, execucao in linhas:
        d = saida / execucao
        ej = json.loads((d / "execucao.json").read_text(encoding="utf-8"))
        print("  %s: fusao=%s controller=%s bloco=%s t_fusao_ns=%s" % (execucao, ej.get("fusao"), ej.get("controller"),
                                                                       ej.get("bloco"), ej.get("t_fusao_ns", "AUSENTE")))
        # dados sinteticos (uma I1 online antes da fusao e uma I1 REAL so na varredura); execucao.json intacto
        sid_e, sid_p = "ex-%s" % braco, "pp-%s" % braco
        ev = [("Logout", "NONE", w5c.INI + 1 * w5c.S, w5c.INI + 1 * w5c.S + w5c.MS, "h:1", sid_e),
              ("Logout", "NONE", w5c.INI + 2 * w5c.S, w5c.INI + 2 * w5c.S + w5c.MS, "h:1", sid_p)]
        viol = [(w5c.INI + 12 * w5c.S, "I1", sid_e, w5c.MSG_I1 % "LOGOUT", "", "")]
        varr = [("I1", sid_p, "REAL", w5c.DET_I1 % "LOGOUT")]
        w5c.escrever_inst(d, eventos=ev, violacoes=viol, varredura=varr, servidores=None, acoes=fat.acoes_lat(),
                          trilha=w5c._trilha(ej["controller"]))
    ok = True

    rc, out, err = rodar_cli(analysis, man, trabalho / "comparacao.json", celulas)
    print("\n1) comparar_bracos.py sobre a saida do orquestrador: exit %d" % rc)
    if rc != 0:
        ok = False
        mostrar(err[-2000:], trabalho)
    else:
        r = json.loads((trabalho / "comparacao.json").read_text(encoding="utf-8"))
        for c in celulas:
            cel = r["celulas"][c]
            des = cel.get("desfechos", {})
            v = des.get("taxa_violacoes_por_1000", {})
            print("  celula %s: bracos=%s blocos=%s efeitos de V=%s efeitos_principais=%s perda_permanente=%s "
                  "exposicao=%s" % (c, cel.get("bracos"), sorted(v.get("y_por_bloco", {})),
                                    sorted(v.get("efeitos", {})), sorted(cel.get("efeitos_principais", {})),
                                    "perda_permanente_sids" in des, "exposicao_janela_sids" in des))
            print("  fusao (W-5e): %s" % json.dumps(cel.get("fusao"), ensure_ascii=True)[:400])

    # 2) braco adulterado: fusao trocada no PO (o W-5e confere o braco do manifesto contra o execucao.json)
    po = next(saida / e for _b, _c, br, e in linhas if br == "PO")
    ej = json.loads((po / "execucao.json").read_text(encoding="utf-8"))
    bruto = (po / "execucao.json").read_bytes()
    (po / "execucao.json").write_text(json.dumps(dict(ej, fusao="a1")), encoding="utf-8")
    rc2, _o, err2 = rodar_cli(analysis, man, trabalho / "comparacao-adulterada.json", celulas)
    print("\n2) PO com fusao=a1 no execucao.json: exit %d (esperado 2): %s" % (rc2, (err2.strip().splitlines() or [""])[-1]))
    ok = ok and rc2 == 2
    # 3) t_fusao_ns invalido (texto)
    (po / "execucao.json").write_text(json.dumps(dict(ej, t_fusao_ns="x")), encoding="utf-8")
    rc3, _o, err3 = rodar_cli(analysis, man, trabalho / "comparacao-adulterada2.json", celulas)
    mostrar("3) PO com t_fusao_ns='x': exit %d (esperado 2): %s" % (rc3, (err3.strip().splitlines() or [""])[-1]), trabalho)
    ok = ok and rc3 == 2
    (po / "execucao.json").write_bytes(bruto)
    print("\nRESULTADO: %s" % ("OK" if ok else "FALHOU"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
