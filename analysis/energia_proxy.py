"""Proxy de energia por execucao e por fase (B-39.2; plano de 22/10, secoes 5.2 e 5.3).

Uso (da raiz do repositorio):
  python -I analysis/energia_proxy.py --execucao DIR --rep N --cenario S1|S2|S3
      [--marcadores ARQ --w-recovery-s 60 [--convergencia ARQ.json]]
      [--p-max-w W --razoes-ocioso 0.3,0.5,0.7] --saida energia-rep-NNN.json

Entradas em DIR (contratos copiados dos prompts do B-37 e do B-27):
  recursos-rep-NNN.csv + recursos-meta-rep-NNN.json   contrato recursos/1
  rep-NNN.csv + meta-csv-rep-NNN.json                 contrato CSV 2 (S1/S2: operacoes uteis)
  s3-acoes-rep-NNN.csv                                contrato S3 v2, 10 colunas (S3)

Medido: CPU-s por fonte e fase (interpolacao linear do contador acumulado nas fronteiras, nunca
extrapolada), utilizacao, CPU-s por 1000 operacoes uteis, steal da execucao (exclusao se > 5 %),
retrabalho S3. Estimado (so com --p-max-w): energia dinamica e total pelo modelo linear declarado
P(u) = P_ocioso + (P_max - P_ocioso) * u. Nada aqui e medicao de energia (plano 5.3).

Exit: 0 ok, 2 uso/contrato/saida existente, 3 nao implementado. So biblioteca padrao.
"""
import sys
from fractions import Fraction
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # fases e referencia_p99 (python -I)

ESQUEMA = "energia-proxy/1"
CABECALHO_RECURSOS = "t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado"
CONTRATO_RECURSOS = "recursos/1"
CABECALHO_S3_V2 = ("t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,"
                   "t_fim_ns,espera_portao_ns")
CABECALHO_S3_V1 = "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota"
LIMIAR_STEAL = Fraction(5, 100)          # plano 5.3, item 5: excluida se steal > 5 % (estrito)
RAZOES_OCIOSO_PADRAO = (0.3, 0.5, 0.7)
FONTE_WORKLOAD = "pid:workload"


class ErroUso(Exception):
    """Uso invalido, contrato violado ou saida existente: exit 2."""


def ler_meta_recursos(caminho):
    """Meta do coletor; exige contrato recursos/1 e clk_tck, nproc, intervalo_ms inteiros."""
    raise NotImplementedError("ler_meta_recursos")


def ler_recursos(caminho):
    """{fonte: [(t_epoch_ns, cpu_usec|None, steal_usec|None, ok)]} ordenado por t."""
    raise NotImplementedError("ler_recursos")


def contador_no_intervalo(amostras, a, b, intervalo_ms, campo="cpu"):
    """(delta Fraction|None, marca) do contador acumulado entre a e b, por interpolacao linear."""
    raise NotImplementedError("contador_no_intervalo")


def cpu_por_fase(recursos_csv, intervalos, intervalo_ms):
    """{fonte: {fase: {"cpu_s": float|None, "marca": "ok"|"fora-das-amostras"|"lacuna"}}}."""
    raise NotImplementedError("cpu_por_fase")


def steal_frac(recursos_csv, t0, t1, nproc, intervalo_ms):
    """Delta steal / (delta t * nproc) da fonte vm em [t0, t1) como Fraction, ou None."""
    raise NotImplementedError("steal_frac")


def excluida_por_steal(frac):
    """True se frac > 0,05 (estrito); None se o steal nao pode ser medido."""
    raise NotImplementedError("excluida_por_steal")


def ler_acoes_s3(caminho):
    """Acoes do s3-acoes-rep-NNN.csv no contrato S3 v2; ErroUso no cabecalho de 8 colunas."""
    raise NotImplementedError("ler_acoes_s3")


def e_util_evento(operation, return_code):
    """S1/S2: return_code em VALIDOS da operation (copia de rebaseline.VALIDOS)."""
    raise NotImplementedError("e_util_evento")


def energia_fase(cpu_s, duracao_s, nproc, p_max_w, razao_ocioso):
    """(E_total, E_dinamica) em joules de uma fase pelo modelo linear declarado."""
    raise NotImplementedError("energia_fase")


def analisar_execucao(execucao, rep, cenario, marcadores=None, w_recovery_s=60, convergencia=None,
                      p_max_w=None, razoes_ocioso=RAZOES_OCIOSO_PADRAO):
    """Dicionario do esquema energia-proxy/1 de uma execucao."""
    raise NotImplementedError("analisar_execucao")


def main(argv=None):
    print("nao implementado", file=sys.stderr)
    return 3


if __name__ == "__main__":
    sys.exit(main())
