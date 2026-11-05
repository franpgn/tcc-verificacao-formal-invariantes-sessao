"""W-4 smoke: chaves da sonda e prova REST por ?action=distribution (V-W4 R2).

Uso:
  python -I chaves.py <observaveis-rep-001.jsonl>   -> "no<TAB>chave" de defasagem_chaves (linha fim)
  python -I chaves.py --hex <chave>                 -> bytes Java da String (ObjectOutputStream) em hexadecimal
  python -I chaves.py --resumir-prova <dir>         -> uma linha por chave e no consultado
"""
import json
import os
import sys

PORTA_DO_NO = {"isn1": "12611", "isn2": "12612", "isn3": "12613"}


def chaves(observaveis):
    with open(observaveis, encoding="utf-8") as f:
        linhas = [json.loads(x) for x in f if x.strip()]
    fim = [x for x in linhas if x.get("tipo") == "fim"]
    if not fim or fim[-1].get("defasagem_chaves") is None:
        raise SystemExit("sem defasagem_chaves no fim de " + observaveis)
    for k in fim[-1]["defasagem_chaves"]:
        print(k["no"] + "\t" + k["chave"])


def hexa(chave):
    # java.io.ObjectOutputStream.writeObject(String): STREAM_MAGIC ACED, STREAM_VERSION 0005, TC_STRING 74,
    # comprimento (UTF modificado, 2 bytes big-endian) e os bytes; as chaves sao ASCII
    b = chave.encode("ascii")
    return "ACED000574" + "%04X" % len(b) + b.hex().upper()


def resumir(d):
    pares = [l.rstrip("\n").split("\t") for l in open(os.path.join(d, "chaves.tsv"), encoding="utf-8") if l.strip()]
    print("no_da_chave\tchave\tvia\thttp\tcontains_key\tprimario\tprimario_rotulo\tconfere")
    for no, chave in pares:
        for via in ("isn1", "isn2", "isn3"):
            arq = os.path.join(d, "dist-%s-via-%s.txt" % (no.replace(":", "_").replace(".", "_"), via))
            txt = open(arq, encoding="utf-8").read().rstrip("\n").split("\n")
            codigo = txt[-1] if txt else ""
            corpo = "\n".join(txt[:-1])
            try:
                j = json.loads(corpo)
            except ValueError:
                print("\t".join([no, chave, via, codigo, "?", "?", "?", "nao"]))
                continue
            prim = [o.get("node_name", "") for o in j.get("owners", []) if o.get("primary")]
            nome = prim[0] if prim else ""
            rot = "127.0.0.1:" + PORTA_DO_NO.get(nome.split("-")[0], "?") if nome else ""
            ok = j.get("contains_key") is True and rot == no
            print("\t".join([no, chave, via, codigo, str(j.get("contains_key")), nome, rot, "sim" if ok else "nao"]))


if __name__ == "__main__":
    if sys.argv[1] == "--hex":
        print(hexa(sys.argv[2]))
    elif sys.argv[1] == "--resumir-prova":
        resumir(sys.argv[2])
    else:
        chaves(sys.argv[1])
