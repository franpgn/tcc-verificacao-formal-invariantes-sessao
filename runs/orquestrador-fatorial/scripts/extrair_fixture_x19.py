"""Extrai dos brutos do X-19 a fixture da regra do instante da fusao pela trilha de observaveis (P-61, DT-027 2.2).

Uso: python -I runs/orquestrador-fatorial/scripts/extrair_fixture_x19.py <campanha-s3-recalibracao> <destino>

Para cada execucao F2 da recalibracao X-19 (dados-vm/cs3-2026-11/rc-20261106T144744Z/campanha-s3-recalibracao), copia o
marcadores.txt inteiro e, do observaveis-rep-001.jsonl, a linha meta e as linhas "topologia" de end - 6 s a end + 56 s
(a sonda de topologia do controlador amostra os tres nos a cada ~2 s). Grava <destino>/ORIGEM.txt com o SHA-256 dos
dois arquivos originais de cada execucao e a regra da extracao. So leitura dos brutos.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

JANELA = (-6.0, 56.0)


def main(argv):
    origem, destino = Path(argv[0]), Path(argv[1])
    destino.mkdir(parents=True, exist_ok=True)
    linhas_origem = ["Fixture da regra do instante da fusao pela trilha de observaveis (P-61), extraida de",
                     "dados-vm/cs3-2026-11/rc-20261106T144744Z/campanha-s3-recalibracao (recalibracao X-19, 06/11) por",
                     "runs/orquestrador-fatorial/scripts/extrair_fixture_x19.py: marcadores.txt inteiro; do",
                     "observaveis-rep-001.jsonl, a linha meta e as linhas topologia de end - 6 s a end + 56 s.",
                     "", "execucao\tsha256 observaveis-rep-001.jsonl (original)\tsha256 marcadores.txt\tlinhas extraidas"]
    for d in sorted(origem.glob("exec-*-F2-A")):
        mk = (d / "marcadores.txt").read_bytes()
        obs = (d / "observaveis-rep-001.jsonl").read_bytes()
        end = int(re.search(rb"event=end t_epoch_ns=(\d+)", mk).group(1))
        out = []
        for l in obs.decode("utf-8").splitlines():
            o = json.loads(l)
            if o.get("tipo") == "meta":
                out.append(l)
            elif o.get("tipo") == "topologia" and JANELA[0] <= (o["t_ns"] - end) / 1e9 <= JANELA[1]:
                out.append(l)
        alvo = destino / d.name
        alvo.mkdir(exist_ok=True)
        (alvo / "marcadores.txt").write_bytes(mk)
        (alvo / "observaveis-rep-001.jsonl").write_bytes(("\n".join(out) + "\n").encode("utf-8"))
        linhas_origem.append("%s\t%s\t%s\t%d" % (d.name, hashlib.sha256(obs).hexdigest(), hashlib.sha256(mk).hexdigest(),
                                                 len(out)))
    (destino / "ORIGEM.txt").write_text("\n".join(linhas_origem) + "\n", encoding="utf-8")
    print("\n".join(linhas_origem))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
