# Validação da análise oficial da avaliação fatorial (2026-11-11)

**Objeto:** a análise oficial de 08/11 em
`dados-vm/cs3-2026-11/fat-20261108T051402Z/` (`ENTREGA-ANALISE.txt` e `analise-oficial/`).

**Quem validou:** o coordenador, que também rodou a análise oficial. O Senior não pôde fazer a validação
porque o limite de uso da conta interrompeu os subagentes em 08/11. O Gestor autorizou em 11/11 a validação
pelo coordenador, desde que verificada contra os dados brutos.

Para não conferir o próprio trabalho com o mesmo instrumento, os desfechos foram recalculados com código
novo (`validacao-analise-oficial-2026-11-11/valida_fatorial.py`). Esse código não importa nenhum módulo do
projeto e reimplementa as regras a partir da leitura de `analysis/fases.py`, `analysis/apuracao_v.py` e
`analysis/comparar_bracos.py` (tag `int/2026-11-06-aval`, 4b96798).

**Veredito: APROVADA, com uma ressalva descritiva sobre CPU e energia (seção 4) que não muda nenhuma
conclusão.**

## 1. Integridade e protocolo

| Item | Esperado (ENTREGA) | Conferido nos brutos |
|---|---|---|
| Pacotes da VM (`fat-20261108T051402Z.sha256`) | 2 OK | `sha256sum -c`: 2 OK |
| `MANIFEST.sha256` por execução | 86 OK, 4 sem (puladas) | 86 OK, 0 falha; sem MANIFEST só 65, 71, 73, 79 (as 4 PULADA) |
| Vereditos (`checagem.json`) | 80 ACEITA, 6 DESCARTADA-INFRA, 4 PULADA | 80 / 6 / 4 |
| Blocos completos | F2 12, F3 4, sem falha 4, F4 4, F1 3 | iguais; perdidos F1 20, 23, 24, 26, 31 e F4 27 |
| `manifesto-bracos.tsv` | só blocos completos | igual à lista recalculada dos `checagem.json` |
| Identificação (`execucao.json`) | um bundle, um jar, um controlador, um conjunto de parâmetros | 1 valor de cada em 86 execuções (`c5a6825f…`, `6fda9330…`, `163b88b0…`, `5959a349…`, referência `dec07fb7…`) |
| Boots | núcleo num boot, extra e reposições noutro | núcleo 64 em `f9e98b74…`; extra 12 e reposições 10 em `0c1b7856…` |
| Arquivos oficiais (`LEIAME.txt`) | 4 SHA-256 | os 4 conferem |

## 2. Recálculo independente dos desfechos

Foram recalculadas, por execução, as 78 execuções dos blocos completos: ações S3 nas fases durante e
recuperação (W = 191 s), p99 com e sem espera, vazão útil, falhas percebidas, taxa de erro, p99 do Validate,
violações (pares distintos chave × invariante das três origens, com a atribuição de fase do replay tardio e da
varredura pela invalidação confirmada), perda permanente, exposição na janela e divergência persistente.

| Comparação com o oficial | Resultado |
|---|---|
| Valores por execução da F2 (48 execuções × 10 desfechos) | idênticos (diferença 0 ou ≤ 2e−16, arredondamento) |
| Diferenças por bloco das células F1, F3, F4 e sem falha (10 desfechos) | idênticas; número de ações por braço igual |
| Efeitos A1, A4 e interação da F2 (estimativa, IC t, p de permutação exata) | idênticos nos 10 desfechos |
| Regra interseção-união | A4: p_IU 0,0012, Holm 0,0025, **melhoria**; A1: p_IU 1,0, **sem melhoria** (igual) |
| Holm dos 11 secundários por efeito | iguais (A1 perda permanente 0,0430; A4 exposição 0,0215; A4 falhas percebidas e taxa de erro 0,0684) |
| Ações do controlador | AC-7 aplicado e revertido 12 vezes em PL e 12 em AL; nenhuma ação em PO, AO e nas células F1, F3, F4 e sem falha |

Totais da F2 recalculados (12 blocos por braço):

| Braço | Pares de violação (V) | Ações S3 | V por 1000 ações (média dos blocos) | Perda permanente (sessões) | Exposição na janela (sessões) |
|---|---|---|---|---|---|
| PO (regra atual, controlador observando) | 37 | 97 675 | 0,378 | 11 | 37 |
| PL (regra atual, controlador ativo) | 16 | 95 898 | 0,167 | 9 | 16 |
| AO (regra A1, controlador observando) | 36 | 96 741 | 0,374 | 0 | 36 |
| AL (regra A1, controlador ativo) | 20 | 95 780 | 0,208 | 0 | 19 |

As células F1, F3, F4 e sem falha têm **zero** pares de violação nos dois braços, num total de 25 960 a
49 143 ações por braço.

## 3. Reprodução da análise oficial

- `comparar_bracos.py` rodado de novo com os comandos do `LEIAME.txt` (semente 20261108), numa pasta à parte:
  os quatro arquivos (`comparacao-principal.json/.md`, `comparacao-semfalha.json/.md`) saíram **idênticos
  byte a byte** aos oficiais, o que confirma também os ICs de *bootstrap*.
- `energia_proxy.py` regenerado a partir dos **brutos originais** (não da cópia de trabalho) para
  `exec-0000` (steal sem medida) e `exec-0002` (steal medido): idêntico ao usado na análise, exceto os
  caminhos dos arquivos de entrada.

## 4. Ressalva: CPU e energia da F2 com 4 dos 12 blocos

**O que aconteceu.** Na F2, 8 blocos saíram dos desfechos de CPU e energia com motivo
`steal-indisponivel`. A regra é conservadora e está documentada (`docs/relatorio-b39-2026-10-27.md`,
item 8): execução sem medida de steal tira o bloco inteiro de CPU e energia. O resultado oficial está correto
por essa regra.

**Causa (`steal.py`).** A janela do steal vai da primeira operação da carga ao fim da execução, e o cálculo
não extrapola antes da primeira amostra do coletor de recursos. Em 48 das 86 execuções com arquivo de energia
(23 das 48 da F2), a primeira amostra da VM chegou de 0,01 s a 0,13 s **depois** da primeira operação. Não
há lacuna nem falta de dados no meio da execução (no mínimo 299 amostras por janela nas execuções da
comparação principal).

**Steal real.** Medido do primeiro ao último ponto válido de cada janela, o steal é **zero em todas as
execuções** (o Hyper-V não expôs steal à VM nesse período).

**Sensibilidade com os 12 blocos (não pré-registrada; `sens_energia.py`).** Nos 4 blocos, o recálculo
reproduz exatamente o oficial.

| Desfecho (razão B/A) | Efeito | 4 blocos (oficial) | 12 blocos (sensibilidade) |
|---|---|---|---|
| CPU-s por 1000 ações úteis | controlador (A4) | 1,038 [0,961; 1,121], p 0,375 | 1,028 [0,997; 1,059], p 0,075 |
| CPU-s por 1000 ações úteis | regra A1 | 1,003 [0,957; 1,051], p 0,875 | 1,006 [0,987; 1,026], p 0,503 |
| Energia por 1000 ações úteis (ocioso 0,3) | controlador (A4) | 1,024 [0,945; 1,109], p 0,375 | 1,019 [0,993; 1,047], p 0,142 |
| Energia por 1000 ações úteis (ocioso 0,3) | regra A1 | 1,000 [0,950; 1,053], p 1,000 | 1,005 [0,987; 1,024], p 0,527 |
| Energia por 1000 ações úteis (ocioso 0,5) | controlador (A4) | 1,023 [0,943; 1,109], p 0,375 | 1,019 [0,992; 1,046], p 0,156 |

A conclusão não muda: não há efeito detectável do controlador nem da A1 em CPU ou energia. Com os 12 blocos
o intervalo fica mais estreito: o custo energético do controlador é de no máximo cerca de +5 % (limite
superior 1,047). Médias por braço com 12 blocos: energia 1934 (PO), 1981 (PL), 1955 (AO) e 1984 (AL)
J por 1000 ações úteis; CPU 7,18, 7,41, 7,26 e 7,43 s por 1000 ações úteis.

**Para o texto:** o resultado oficial é o de 4 blocos. A sensibilidade de 12 blocos pode ser citada como
complemento descritivo, sempre identificada como não pré-registrada. Para o futuro, o coletor de recursos
deve começar antes da carga, ou a janela do steal deve começar na primeira amostra.

## 5. O que não foi recalculado de forma independente

- ICs de *bootstrap* e hierárquicos: reproduzidos pela nova execução do código oficial (seção 3), não por
  código próprio.
- Δ_rec e Δ_conv: tomados do oficial (secundários sem efeito; entram no Holm com p = 1,0 após correção).
- Modelo de energia (proxy linear com P_max = 181 W): regenerado com o código do projeto (seção 3), não
  reimplementado.
- Caso `exec-0089` (sonda tardia no teto de 306): célula descritiva F4, sem efeito no teste.

## 6. Arquivos

`docs/validacao-analise-oficial-2026-11-11/`:
- `valida_fatorial.py`: recálculo independente; saída em `recalculo.json`;
- `compara.py`: comparação com o oficial; saída em `compara-saida.txt`;
- `steal.py`: diagnóstico do steal;
- `sens_energia.py`: sensibilidade com 12 blocos; saída em `sens-energia-saida.txt`.

Os scripts rodam com `python -E -P` (Python 3.12.6, numpy 2.2.0, scipy 1.16.0).
