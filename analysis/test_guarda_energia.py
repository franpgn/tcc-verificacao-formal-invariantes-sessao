"""Testes da guarda G4 de 28/10 (B39-R1 e B39-R2), antes do B-39.4.

B39-R1 (docs/validacao-b37-b39-2026-10-27.md, secao 3.6): comparar_bracos.desfechos_execucao le o
energia-rep-NNN.json e soma cpu_s das fases do proprio JSON. Sem conferir W, marcadores e fases, o
cpu_por_1000_uteis seria medido sobre fronteiras diferentes das dos outros desfechos, sem aviso. A
guarda da exit 2 (ErroUso) quando w_recovery_s, a presenca de marcadores ou as fases divergem.

B39-R2 (mesma secao): uma fonte com todas as linhas em erro saia com a marca "fora-das-amostras";
passa a sair com a marca propria "sem-amostra-valida".

As energias destes testes sao geradas pelo proprio energia_proxy.analisar_execucao (o mesmo codigo que
grava o JSON real), e nao escritas a mao.

Uso: python -m unittest discover -s analysis -p "test_*.py"   (da raiz do repositorio)
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import comparar_bracos as cb  # noqa: E402
import energia_proxy as ep  # noqa: E402

S = 1_000_000_000
T0 = 1_700_000_000 * S
CAB_REC = "t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado"
CAB_S3 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns"
FASES = ["durante", "recuperacao"]


def escrever_recursos(d, fontes, k_max=12, erros=()):
    """fontes: {fonte: usec por s}; amostra a cada 1 s em T0 + k s; erros: {(fonte, k)} com estado erro."""
    d = Path(d)
    d.mkdir(parents=True, exist_ok=True)
    linhas = [CAB_REC]
    for k in range(k_max + 1):
        for fonte, taxa in fontes.items():
            if (fonte, k) in erros:
                linhas.append("%d,%s,,,,erro:leitura" % (T0 + k * S, fonte))
            else:
                linhas.append("%d,%s,%d,1,0,ok" % (T0 + k * S, fonte, taxa * k))
    (d / "recursos-rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))
    meta = {"contrato": "recursos/1", "clk_tck": 100, "nproc": 4, "intervalo_ms": 1000}
    (d / "recursos-meta-rep-001.json").write_text(json.dumps(meta), encoding="utf-8")


def escrever_s3(d, n=100):
    """n acoes uteis em [T0 + 1 s, T0 + 11 s), 1 ms cada."""
    linhas = [CAB_S3]
    for i in range(n):
        t = T0 + S + i * (10 * S // n)
        linhas.append("%d,u%d,Validate,s,OK_VALIDATE:VALID,1,false,,%d,0" % (t, i, t + 1_000_000))
    (Path(d) / "s3-acoes-rep-001.csv").write_bytes(("\n".join(linhas) + "\n").encode("utf-8"))


def escrever_marcadores(caminho, ini, fim):
    Path(caminho).write_text("fault=F2 node=isn3 event=start t_epoch_ns=%d\n"
                             "fault=F2 node=isn3 event=end t_epoch_ns=%d\n" % (ini, fim), encoding="utf-8")


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name) / "exec"
        escrever_recursos(self.d, {"vm": 2_000_000})
        escrever_s3(self.d)
        self.mk = self.d / "marcadores.txt"
        escrever_marcadores(self.mk, T0 + 3 * S, T0 + 5 * S)

    def tearDown(self):
        self.tmp.cleanup()

    def gravar_energia(self, j):
        (self.d / "energia-rep-001.json").write_text(json.dumps(j), encoding="utf-8")

    def energia(self, marcadores="padrao", w_recovery_s=2, convergencia=None):
        mk = self.mk if marcadores == "padrao" else marcadores
        return ep.analisar_execucao(self.d, 1, "S3", mk, w_recovery_s, convergencia)


class B39R1Guarda(Base):

    def test_coerente_passa(self):
        self.gravar_energia(self.energia())
        out, _ = cb.desfechos_execucao(self.d, 1, FASES, 2 * S)
        self.assertIsNotNone(out["cpu_por_1000_uteis"]["y"])

    def test_sem_marcadores_dos_dois_lados_passa(self):
        self.mk.unlink()
        self.gravar_energia(self.energia(marcadores=None))
        out, _ = cb.desfechos_execucao(self.d, 1, ["antes"], 2 * S)
        self.assertIsNotNone(out["cpu_por_1000_uteis"]["y"])

    def test_w_recovery_divergente(self):
        self.gravar_energia(self.energia(w_recovery_s=2))
        with self.assertRaises(cb.ErroUso) as cm:
            cb.desfechos_execucao(self.d, 1, FASES, 60 * S)
        self.assertIn("w_recovery_s", str(cm.exception))

    def test_energia_sem_marcadores_e_comparacao_com(self):
        self.gravar_energia(self.energia(marcadores=None))
        with self.assertRaises(cb.ErroUso) as cm:
            cb.desfechos_execucao(self.d, 1, FASES, 2 * S)
        self.assertIn("marcadores", str(cm.exception))

    def test_energia_com_marcadores_e_comparacao_sem(self):
        self.gravar_energia(self.energia())
        self.mk.unlink()
        with self.assertRaises(cb.ErroUso) as cm:
            cb.desfechos_execucao(self.d, 1, ["antes"], 2 * S)
        self.assertIn("marcadores", str(cm.exception))

    def test_marcadores_de_outro_arquivo(self):
        # mesmo W, marcadores presentes nos dois lados, mas com outros instantes: as fases divergem
        outro = Path(self.tmp.name) / "outro-marcadores.txt"
        escrever_marcadores(outro, T0 + 4 * S, T0 + 6 * S)
        self.gravar_energia(self.energia(marcadores=outro))
        with self.assertRaises(cb.ErroUso) as cm:
            cb.desfechos_execucao(self.d, 1, FASES, 2 * S)
        self.assertIn("fases", str(cm.exception))

    def test_convergencia_nao_passada_a_energia(self):
        # o resumo de convergencia estende a recuperacao ate T0 + 9 s (> fim + W = T0 + 7 s); a energia
        # foi calculada sem --convergencia, entao a fronteira recuperacao/pos-convergencia diverge
        (self.d / "convergencia-resumo-rep-001.json").write_text(json.dumps(
            {"t_fim_ns": T0 + 5 * S, "convergiu": True, "divergencia_persistente": False,
             "t_convergencia_abs_ns": T0 + 9 * S}), encoding="utf-8")
        self.gravar_energia(self.energia())
        with self.assertRaises(cb.ErroUso) as cm:
            cb.desfechos_execucao(self.d, 1, FASES, 2 * S)
        self.assertIn("fases", str(cm.exception))

    def test_convergencia_passada_a_energia_passa(self):
        conv = self.d / "convergencia-resumo-rep-001.json"
        conv.write_text(json.dumps({"t_fim_ns": T0 + 5 * S, "convergiu": True, "divergencia_persistente": False,
                                    "t_convergencia_abs_ns": T0 + 9 * S}), encoding="utf-8")
        self.gravar_energia(self.energia(convergencia=conv))
        out, _ = cb.desfechos_execucao(self.d, 1, FASES, 2 * S)
        self.assertIsNotNone(out["cpu_por_1000_uteis"]["y"])

    def test_energia_sem_parametros_ou_janela(self):
        for falta in ("parametros", "janela", "fases"):
            with self.subTest(falta=falta):
                j = self.energia()
                del j[falta]
                self.gravar_energia(j)
                with self.assertRaises(cb.ErroUso):
                    cb.desfechos_execucao(self.d, 1, FASES, 2 * S)


class B39R2Marca(Base):

    def test_contador_so_com_erros(self):
        amostras = [(T0, None, None, False), (T0 + S, None, None, False)]
        self.assertEqual((None, "sem-amostra-valida"), ep.contador_no_intervalo(amostras, T0, T0 + S, 1000))

    def test_fonte_toda_em_erro(self):
        escrever_recursos(self.d, {"vm": 2_000_000, "cg:isn1": 1_000_000},
                          erros={("cg:isn1", k) for k in range(13)})
        r = ep.cpu_por_fase(self.d / "recursos-rep-001.csv", [("durante", T0 + 2 * S, T0 + 4 * S)], 1000)
        self.assertIsNone(r["cg:isn1"]["durante"]["cpu_s"])
        self.assertEqual("sem-amostra-valida", r["cg:isn1"]["durante"]["marca"])
        self.assertEqual("ok", r["vm"]["durante"]["marca"])

    def test_fora_das_amostras_continua(self):
        escrever_recursos(self.d, {"vm": 2_000_000})
        r = ep.cpu_por_fase(self.d / "recursos-rep-001.csv", [("antes", T0 - S, T0 + S)], 1000)
        self.assertEqual("fora-das-amostras", r["vm"]["antes"]["marca"])


if __name__ == "__main__":
    unittest.main()
