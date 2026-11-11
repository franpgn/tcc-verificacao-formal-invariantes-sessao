# 5. Resultados da avaliação do controlador

**Situação dos números.** Todos os números deste capítulo são **oficiais**. Vêm da análise oficial de 08/11
(`dados-vm/cs3-2026-11/fat-20261108T051402Z/analise-oficial/`, semente 20261108), validada em 11/11 por
recálculo independente a partir dos brutos (`docs/validacao-analise-oficial-2026-11-11.md`). Os complementos
marcados como **descritivo** ou **sensibilidade** foram aprovados pelo Gestor em 07/11 (seção 10 da pré-análise)
e calculados dos brutos em 11/11 (`docs/validacao-analise-oficial-2026-11-11/complementos.py`, saída em
`complementos-saida.txt`). Os números da pré-análise de 07/11 só aparecem aqui quando foram reconferidos.

Este capítulo traz os resultados e sua leitura. O protocolo (pré-registros, fatorial, definição dos desfechos,
critério h4) está no capítulo 6, o controlador no 4 e os desvios e ameaças no 8.

## 5.1 Resumo

Na partição de rede, o controlador ativo reduziu à metade a taxa de violações dos invariantes de sessão:
de 0,376 para 0,187 pares por mil ações S3, em média dos dois braços de cada lado. Isso atendeu à regra de
sucesso pré-registrada (interseção-união, valor-p ajustado por Holm de 0,0025). A latência p99 não piorou e a
vazão útil ficou dentro da margem de não inferioridade.

O custo foi mais erro visível ao cliente, cerca de 12 % a mais de ações com falha percebida. Esse custo não
foi significativo depois da correção de Holm (0,055), mas passa a ser quando se retira o bloco 13, cuja
execução AL teve uma anomalia (seção 5.10).

A política de fusão A1 não reduziu a taxa de violações e por isso não atendeu à regra principal. Ela
eliminou, porém, a perda permanente de invalidações: 20 sessões perdidas de vez sem a A1, nenhuma com ela
(Holm 0,035). As duas intervenções agem em momentos diferentes do mesmo problema e não interagem.

Na queda de nó, no *jitter*, na degradação de rede com 20 % de perda e na execução sem falha, nenhuma violação
foi observada em nenhum braço. O controlador não executou nenhuma ação nessas células. A energia estimada e o
tempo de CPU não mudaram de forma detectável.

## 5.2 O que foi executado

As 90 execuções rodaram de 06/11 a 07/11 na VM, em três fases: núcleo, extra e reposições.

| Célula | Falha injetada | Papel | Braços | Blocos completos | Execuções aceitas / descartadas / puladas | Ações S3 analisadas por braço |
|---|---|---|---|---|---|---|
| Partição (F2) | `isn3` isolado por 96,0 s | teste (fatorial 2×2) | PO, PL, AO, AL | 12 de 12 | 48 / 0 / 0 | PO 97 675; PL 95 898; AO 96 741; AL 95 780 |
| *Jitter* (F3) | atraso no `isn2` por 251 s | descritiva | A, B | 4 de 4 | 8 / 0 / 0 | A 39 004; B 38 823 |
| Degradação de rede (F4) | 20 % de perda no `isn3` por 89,6 s | descritiva | A, B | 4 (1 reposto) | 8 / 1 / 1 | A 38 778; B 39 599 |
| Queda de nó (F1) | queda silenciosa do `isn2` por 60,5 s | descritiva | A, B | 3 (de 4 planejados + 4 reposições) | 8 / 5 / 3 | A 25 960; B 26 137 |
| Sem falha | nenhuma | descritiva | A, B | 4 de 4 | 8 / 0 / 0 | A 49 143; B 48 668 |

Fontes: `checagem.json` de cada execução; durações entre os marcadores `start` e `end` de cada execução (mediana;
F2 de 96,04 a 96,07 s); ações S3 nas fases durante a falha e recuperação (W = 191 s), na célula sem falha na
execução inteira.

**Braços.** Na partição, o desenho cruza dois fatores por bloco:
- **regra de fusão:** a atual do Infinispan (`PREFERRED_NON_NULL`, P) ou a A1 (A);
- **controlador:** observando, sem atuar (O, modo *shadow*), ou ativo (L, modo *active*).

Os quatro braços são PO, PL, AO e AL. O **efeito A4** é o do controlador ativo, a média de PL − PO e AL − AO. O
**efeito A1** é o da regra de fusão, a média de AO − PO e AL − PL. Nas células descritivas, A é o controlador
observando e B o ativo, ambos com a regra atual. As 6 execuções descartadas são todas por interferência do
host (critério h4a: travamento de I/O da VM causado pelo SSD do host, capítulo 8). Os blocos perdidos foram
F1 20, 23, 24, 26 e 31 e F4 27.

## 5.3 Teste principal: regra de sucesso na partição

A regra pré-registrada exige quatro condições:
- redução da taxa de violações (troca de sinal exata, p ≤ 0,05 e média negativa);
- p99 não pior que +10 % (limite superior do IC t 95 % ≤ 1,10);
- vazão útil não inferior (limite inferior ≥ 0,90);
- rastreabilidade integral.

O valor-p de interseção-união é o maior dos dois primeiros. A correção de Holm entre os dois efeitos dá m = 2.

| Efeito | Δ violações por mil ações [IC t 95 %]; p | Razão do p99 [IC t 95 %]; p de não inferioridade | Razão da vazão útil [IC t 95 %] | Rastreabilidade | p de interseção-união | p ajustado (Holm) | Resultado |
|---|---|---|---|---|---|---|---|
| Controlador ativo (A4) | −0,189 [−0,268; −0,109]; p = 0,0005 | 0,972 [0,912; 1,035]; p = 0,0012 | 0,982 [0,957; 1,009] | integral | 0,0012 | **0,0025** | **melhoria** |
| Regra de fusão A1 | +0,019 [−0,095; 0,132]; p = 0,717 | 0,972 [0,912; 1,035]; p = 0,0012 | 0,995 [0,977; 1,013] | integral | 1,000 | 1,000 | sem melhoria |
| Interação (sem teste) | +0,045 [−0,174; 0,265]; p = 0,660 | 0,944 [0,832; 1,072] | 1,009 [0,963; 1,057] | | | | nula |

Fontes: `comparacao-principal.md`, célula ASYNC-F2 e conclusão global; reproduzidos na validação. Com 12 blocos,
o menor valor-p possível da troca de sinal é 2/2¹² ≈ 0,0005, atingido pelo controlador: os 12 blocos têm
diferença negativa. O IC *bootstrap* por blocos, análise de sensibilidade pré-registrada, dá
[−0,258; −0,122] para o controlador e [−0,085; 0,110] para a A1.

### Desfechos por braço (médias dos 12 blocos)

| Braço | Violações por mil ações | Pares de violação (total) | Sessões expostas na janela (por execução) | Sessões perdidas de vez (por execução) | Falhas percebidas por mil ações | Taxa de erro | p99 da ação S3 (s) | Vazão útil (ações/s) |
|---|---|---|---|---|---|---|---|---|
| PO (regra atual, observando) | 0,378 | 37 | 3,08 | 0,92 | 22,7 | 6,55 % | 1,504 | 31,8 |
| PL (regra atual, ativo) | 0,167 | 16 | 1,33 | 0,75 | 26,5 | 7,59 % | 1,504 | 31,1 |
| AO (A1, observando) | 0,374 | 36 | 3,00 | 0 | 22,4 | 6,45 % | 1,504 | 31,5 |
| AL (A1, ativo) | 0,208 | 20 | 1,58 | 0 | 25,2 | 7,20 % | 1,442 | 31,1 |

Fonte: `recalculo.json` da validação (médias por bloco dos valores por execução, idênticos aos do oficial).
O p99 médio de AL é menor só por causa do bloco 13 (0,755 s na `exec-0037`). Nas outras 47 execuções da F2 o
p99 ficou entre 1,503 e 1,505 s (seção 5.6).

## 5.4 Desfechos secundários na partição

**Família de Holm.** O pré-registro fixa 9 secundários por efeito: os 7 da DT-021 §5.3 (Δ_rec, Δ_conv,
divergência persistente, taxa de erro, falhas percebidas, CPU e energia com ociosidade 0,3) mais os 2 da
DT-027 §2.2 (perda permanente e exposição na janela). O p99 por operação fica **descritivo**, "para a família
não crescer" (`wt-bench/docs/pre-registro-b40-avaliacao-2026-11-05.md`, seção 5.3).

O `comparar_bracos.py` da análise oficial pôs na família todo secundário com valor-p e chegou a 11,
incluindo o p99 sem espera e o p99 do Validate. Esse é um **desvio da ferramenta**, conservador (capítulo 8).
A tabela dá o p ajustado pela família pré-registrada de 9 e, entre parênteses, o do script, com 11.
Nenhuma conclusão muda. O Holm de 9 foi recalculado em 11/11 a partir dos valores-p oficiais, e o desvio foi
apontado pelo Senior da parte A do relatório.

"Diferença" é a média de y(ativo) − y(observando), ou y(A1) − y(regra atual). "Razão" é a média geométrica da
razão.

| Desfecho | Escala | Controlador ativo (A4): estimativa [IC t 95 %]; p; p Holm | Regra A1: estimativa [IC t 95 %]; p; p Holm |
|---|---|---|---|
| Sessões expostas na janela da falha | diferença | −1,58 [−2,24; −0,92]; 0,0020; **0,018** (0,022) | +0,08 [−0,85; 1,02]; 0,92; 1,0 |
| Sessões com invalidação perdida de vez | diferença | −0,08 [−0,44; 0,27]; 0,81; 1,0 | −0,83 [−1,27; −0,40]; 0,0039; **0,035** (0,043) |
| Falhas percebidas por mil ações | diferença | +3,30 [1,32; 5,28]; 0,0068; 0,055 (0,068) | −0,85 [−2,51; 0,82]; 0,33; 1,0 |
| Taxa de erro | diferença | +0,89 pp [0,34; 1,44]; 0,0068; 0,055 (0,068) | −0,24 pp [−0,72; 0,23]; 0,32; 1,0 |
| p99 da ação S3 sem a espera do portão (descritivo) | razão | 0,972 [0,912; 1,035]; sem teste na família | 0,972 [0,912; 1,035]; sem teste na família |
| p99 da validação, Validate (descritivo) | razão | 0,868 [0,409; 1,842]; sem teste na família | 0,854 [0,403; 1,809]; sem teste na família |
| Tempo de recuperação Δ_rec (s) | diferença | −1,36 [−5,44; 2,72]; 0,77; 1,0 | −1,47 [−5,76; 2,81]; 0,81; 1,0 |
| Tempo de convergência Δ_conv (s), 11 blocos | diferença | −0,0002 [−0,0016; 0,0013]; 0,83; 1,0 | +0,0006 [−0,0005; 0,0018]; 0,26; 1,0 |
| Divergência persistente (I5/I6) | diferença | −0,04 [−0,13; 0,05]; 1,0; 1,0 | −0,04 [−0,13; 0,05]; 1,0; 1,0 |
| CPU por mil ações úteis, 4 blocos | razão | 1,038 [0,961; 1,121]; 0,38; 1,0 | 1,003 [0,957; 1,051]; 0,88; 1,0 |
| Energia por mil ações úteis, ociosidade 0,3, 4 blocos | razão | 1,024 [0,945; 1,109]; 0,38; 1,0 | 1,000 [0,950; 1,053]; 1,0; 1,0 |

Fonte: `comparacao-principal.md` (célula ASYNC-F2), validado.

Notas da tabela:
- **Falhas percebidas, como razão** (sensibilidade): 1,123 [1,007; 1,253] para o controlador, isto é, cerca de
  +12 %.
- **Δ_rec:** de 43,6 a 43,8 s em PO, PL e AO, e 40,7 s em AL.
- **Δ_conv:** cerca de 0,012 s em todos os braços. O bloco 12 sai porque a `exec-0034` (PO) não convergiu
  (divergência persistente, único caso na F2).
- **p99 por operação:** só o Validate tem pelo menos 1000 eventos por execução. As demais operações saem pela
  regra de n mínimo.

## 5.5 Por que o controlador reduz violações e a A1 reduz perda

**Composição das violações.** Na F2 houve 109 pares de violação: 37 em PO, 16 em PL, 36 em AO e 20 em AL.
- **Invariantes:** todos são de I1 (sessão encerrada por *logout* volta a ser aceita), exceto 1 de I2 real em
  AL, sem relação com a A1. Nenhuma violação de I4 foi observada.
- **Instrumentos:** a sonda de *replay* (releitura da sessão 1 a 3 s depois do *logout* confirmado e depois do
  fim da carga) aparece em 104 dos 109 pares. A varredura final aparece em 11 pares (PO) e 9 (PL), exatamente
  as sessões perdidas de vez. O auditor online aparece em 3, 0, 3 e 2 pares.
- **Exposição antes da perda:** toda sessão perdida de vez foi também exposta antes, durante a falha (0 casos
  de perda sem exposição nos quatro braços).

Fonte: `complementos-saida.txt`, itens 4 e 3.

**Mecanismo (explicação aprovada pelo Gestor em 07/11).**
1. Durante a partição, o usuário faz *logout* pelo nó isolado `isn3`, que registra a sessão como encerrada. Os
   outros dois nós continuam com a sessão aberta.
2. Segundos depois, a sonda relê a sessão por outro nó e recebe `VALID`. O par (sessão, I1) passa a contar
   naquele momento.
3. Quando a partição termina, a regra atual de fusão muitas vezes fica com a versão aberta, e o *logout* se
   perde de vez. A A1 fica com a versão mais avançada, com o *logout*, e o estado volta a ser consistente.

Como a taxa conta **pares distintos**, o mesmo problema que persiste depois da fusão não conta de novo. Na
prática, a taxa mede a **exposição durante a falha**, e é nela que o controlador age: ao tirar o *cache* de
sessões de serviço no nó isolado (ação AC-7), ele encurta essa exposição. A A1 age **depois da fusão**, sobre a
perda permanente, que a taxa não separa.

| Braço | Pares de violação | Sessões expostas na janela | Sessões perdidas de vez |
|---|---|---|---|
| PO (regra atual, observando) | 37 | 37 | 11 |
| PL (regra atual, ativo) | 16 | 16 | 9 |
| AO (A1, observando) | 36 | 36 | 0 |
| AL (A1, ativo) | 20 (19 de I1 e 1 de I2 real) | 19 | 0 |

**Dependência do instrumento (ressalva obrigatória).** A sonda de *replay* foi incluída antes dos dados, então a
definição oficial vale. Sem ela, contando só o auditor online e a varredura final, as conclusões se invertem.
O cálculo abaixo é **descritivo** e foi reconferido dos brutos.

| Contagem | Controlador ativo (A4) | Regra A1 |
|---|---|---|
| Oficial, com a sonda | −0,189 por mil [−0,268; −0,109]; p = 0,0005 | +0,019 [−0,095; 0,132]; p = 0,72 |
| Só auditor online e varredura final | −0,029 [−0,068; 0,011]; p = 0,17 | −0,092 [−0,144; −0,039]; p = 0,0068 |

Não há contradição: sem a sonda, a taxa mediria quase só a perda permanente, que é o que a A1 resolve.

**O que o controlador fez.** Nos 24 braços ativos da F2 (PL e AL), a ação AC-7 foi aplicada 24 vezes e
desfeita 24 vezes. Nenhuma execução terminou com a ação ativa, e nos braços observando nenhuma chamada chegou
ao *cluster* (fontes: `decisoes-rep-001.jsonl` de cada execução, `recalculo.json`; ENTREGA item 6). Quando o
controlador agiu e quanto antes da detecção do *cluster* estão no capítulo 4.

## 5.6 Custo do controlador e latência

- **Custo.** O controlador troca exposição por erro visível. O cliente do nó isolado passa a receber recusa em
  vez de dado inconsistente. Resultado: +3,3 falhas percebidas por mil ações (+12 %) e +0,9 ponto percentual
  na taxa de erro, ambos com p de 0,0068 sem correção e 0,055 com Holm.
- **p99 preso no *backoff*.** Em 47 das 48 execuções da F2, o p99 co-principal ficou entre 1,503 e 1,505 s, o
  patamar das retentativas do cliente com espera. Por isso a razão do p99 fica perto de 1 e quase não
  discrimina os braços. A não inferioridade da latência foi demonstrada (limite superior 1,035), mas a cauda
  medida é a do mecanismo de retentativa.
- **p99 só das ações bem-sucedidas** (descritivo, não pré-registrado). Médias por braço: PO 0,520 s, PL 0,752 s,
  AO 0,505 s e AL 0,536 s. Em PL o valor fica sempre perto de 0,75 s (de 0,751 a 0,753 s), o tempo de uma
  retentativa. Nos outros três braços ele varia entre blocos, de 0,005 a 0,753 s. Razão do controlador: 2,04 [0,53; 7,88], p = 0,28,
  sem diferença detectável. A razão da A1, 0,47 [0,21; 1,04], p = 0,080, é descritiva.

## 5.7 Energia e CPU

**Resultado oficial.** Pela regra conservadora documentada, um bloco sai de CPU e energia quando falta a medida
de *steal* em alguma das suas execuções. Na F2, isso deixou 4 dos 12 blocos. Neles o controlador mudou a CPU
por ação útil em ×1,038 [0,961; 1,121] e a energia estimada em ×1,024 [0,945; 1,109]. A A1 mudou ×1,003 e
×1,000. Nada disso é significativo.

**Sensibilidade com os 12 blocos** (não pré-registrada; validação, seção 4). A medida faltou porque o coletor
de recursos começa de 0,01 a 0,13 s depois da primeira operação da carga. O *steal* medido no restante da
janela é zero em todas as execuções. Com os 12 blocos:
- CPU: ×1,028 [0,997; 1,059], p = 0,075;
- energia (ociosidade 0,3): ×1,019 [0,993; 1,047], p = 0,14;
- energia (ociosidade 0,5): ×1,019 [0,992; 1,046], p = 0,16.

A conclusão não muda, e o custo energético do controlador fica limitado a cerca de +5 %. Médias por braço
(12 blocos): 1934, 1981, 1955 e 1984 J por mil ações úteis em PO, PL, AO e AL; 7,18, 7,41, 7,26 e 7,43
CPU-s por mil ações úteis.

**Limites da estimativa.** A energia é por proxy, com a potência máxima de 181 W do processador do host, e só
vale entre braços, nunca em joules absolutos (Metodologia, seção de energia). Nas células descritivas há
poucos blocos com *steal* medido, e os números de CPU e energia dessas células não devem ser usados.

## 5.8 Células descritivas: queda de nó, *jitter*, degradação e sem falha

| Célula | Blocos | Violações A / B | Limite superior 95 % por mil ações (A / B) | Razão do p99 B/A [IC t 95 %] | Razão da vazão útil B/A [IC t 95 %] | Δ falhas percebidas por mil | Ações do controlador (B) | Entradas no perfil conservador (A / B) |
|---|---|---|---|---|---|---|---|---|
| Queda de nó (F1) | 3 | 0 / 0 | 0,115 / 0,115 | 1,000 [1,000; 1,000] | 1,008 [0,960; 1,059] | −0,41 [−1,68; 0,86] | 0 | 3 / 3 |
| *Jitter* (F3) | 4 | 0 / 0 | 0,077 / 0,077 | 0,981 [0,889; 1,083] | 0,995 [0,976; 1,014] | 0 | 0 | 4 / 4 |
| Degradação, 20 % de perda (F4) | 4 | 0 / 0 | 0,077 / 0,076 | 0,624 [0,381; 1,023] | 1,022 [0,962; 1,085] | 0 | 0 | 4 / 4 |
| Sem falha | 4 | 0 / 0 | 0,061 / 0,062 | 0,979 [0,951; 1,008] | 0,990 [0,976; 1,005] | 0 | 0 | 0 / 2 |

Fontes: `comparacao-principal.md` (F1, F3, F4) e `comparacao-semfalha.md`. O limite superior para zero eventos
em N ações vem da regra de três (≈ 3/N; Hanley e Lippman-Hand, 1983). Células descritivas não têm teste, e um
IC que exclui 1 nelas não é resultado de teste.

Leitura da tabela:
- **Sem violação, sem ação.** Com zero violações também no braço observando, a redução de violações é
  **não avaliável** nessas células. Isso não quer dizer que o efeito não exista. O controlador entrou no perfil
  conservador em todas as execuções com falha, nos dois braços, mas não aplicou ação nenhuma. Por que as
  políticas P1 e P2 não dispararam nessas falhas está no capítulo 4.
- **Sem falha.** O braço ativo entrou no perfil conservador em 2 das 4 execuções, por 468 s no total, sem falha
  injetada e sem nenhuma ação. Não houve alarme falso com ação.
- **Degradação.** A razão do p99 de 0,62 não pode ser atribuída ao controlador, que não agiu. Com 4 blocos, o
  IC t inclui 1.
- **Queda de nó.** A célula tem só 3 blocos, porque 5 se perderam por interferência do host. A `exec-0081`
  (braço B, bloco 28) não convergiu até o teto de 600 s (divergência persistente).

## 5.9 Sensibilidades

| Análise | Controlador ativo: Δ violações por mil [IC]; p | Controlador: regra de sucesso | A1: Δ violações; p | A1: Δ perda permanente; p |
|---|---|---|---|---|
| Oficial (12 blocos) | −0,189 [−0,268; −0,109]; 0,0005 | melhoria (Holm 0,0025) | +0,019; 0,72 | −0,83 [−1,27; −0,40]; 0,0039 (Holm 0,035) |
| *Bootstrap* por blocos (pré-registrada) | IC [−0,258; −0,122] | | IC [−0,085; 0,110] | |
| Sem a forma 2 de I1 (pré-registrada) | −0,071 [−0,130; −0,011]; 0,031 | | +0,032; 0,28 | |
| Sem o bloco 13 (aprovada em 07/11) | −0,187 [−0,275; −0,098]; 0,0010 | melhoria (Holm 0,0020) | +0,002; 0,95 | −0,91 [−1,36; −0,46]; 0,0039 |
| Sem a sonda de *replay* (descritiva) | −0,029 [−0,068; 0,011]; 0,17 | | −0,092; 0,0068 | |

A **forma 2** de I1 é a violação revelada por uma leitura feita no próprio nó isolado durante a falha, com o
*logout* confirmado em outro nó. A sensibilidade sem ela mostra que a redução não depende desse tipo de leitura.

**Sem o bloco 13**, as conclusões ficam iguais, mas o custo e o p99 mudam:
- falhas percebidas: +4,05 por mil [2,85; 5,26], p = 0,001;
- taxa de erro: +1,10 pp [0,78; 1,42], p = 0,001;
- razão do p99 do controlador: 1,0001 [1,0001; 1,0002];
- exposição na janela: −1,59 [−2,32; −0,86], p = 0,0039.

Os p dos secundários sem o bloco 13 não têm correção de Holm. Fontes: `comparacao-principal.md` e
`complementos-saida.txt`, item 1.

## 5.10 Casos notáveis

- **`exec-0037` (bloco 13, AL).** É a única das 24 execuções ativas em que o AC-7 foi desfeito pela rota da
  maioria, e não no próprio nó isolado. Depois disso, o nó isolado voltou a servir sessões sem erro até a fusão,
  e o p99 dessa execução caiu para 0,755 s. A hipótese é um cérebro dividido provocado por esse desfazer. Ela
  vem da pré-análise e não foi testada, porque o Gestor descartou o ensaio em 11/11 (risco do AC-7 e trabalho
  futuro; capítulos 4 e 8). Pela regra pré-registrada, o bloco fica na análise. A sensibilidade sem ele está na
  seção 5.9.
- **`exec-0034` (bloco 12, PO).** Não convergiu: divergência persistente por I5, segundo a pré-análise.
- **`exec-0081` (bloco 28, F1, B).** Divergência até o teto de 600 s do laço de convergência.
- **`exec-0089` (bloco 32, F4, B).** 309 sessões candidatas à sonda tardia, acima do teto de 306. As
  excedentes não foram sondadas. É célula descritiva, sem efeito no teste.
- **`exec-0059` (F2, PO).** A pré-análise registrou recusas `DENY_READ_WRITES` que vazaram para o cliente. Não
  foi reconferido nos brutos.

## 5.11 O que os resultados respondem

| Pergunta | Resposta | Base |
|---|---|---|
| O controlador do *cluster* reduz violações sob partição sem piorar a cauda? | Sim, cerca de −50 % nos pares de violação, com p99 e vazão não inferiores | seção 5.3, regra de sucesso pré-registrada |
| A que custo? | Mais erro visível: +12 % de falhas percebidas (Holm 0,055; p = 0,001 sem o bloco 13); energia e CPU sem diferença detectável (no máximo cerca de +5 %) | seções 5.4, 5.6 e 5.7 |
| A regra de fusão A1 reduz violações? | Não na regra principal; elimina a perda permanente (20 → 0 sessões) | seções 5.3 a 5.5 |
| Os dois se combinam? | Sim, sem interação; AL tem menos exposição e zero perda | seções 5.3 e 5.5 |
| O controlador dispara à toa? | Nenhuma ação fora da partição, nas 30 execuções descritivas dos blocos completos; 2 entradas no perfil conservador sem falha, sem ação | seção 5.8 |
| Queda de nó, *jitter* e degradação de 20 % produzem violações em `DIST_ASYNC` com S3? | Nenhuma observada (limite superior ≈ 0,06 a 0,12 por mil ações) | seção 5.8 |

## 5.12 Tabelas para a monografia

**Onde entram.** A monografia (versão de 05/11) não tem resultados da avaliação. Hoje o texto tem a seção
"Resultados parciais e limitações conhecidas" (`Desenvolvimento.tex:214`), que ainda diz que a avaliação não
foi executada (`Desenvolvimento.tex:217`). A recomendação é um capítulo ou seção nova, "Resultados da avaliação
do controlador", antes das conclusões, com as tabelas essenciais R1 a R4. R5 e R6 vão como complemento, no
texto ou em apêndice.

O desenho da Metodologia precisa ser atualizado antes, para o executado (seção 5.13). As tabelas usam os nomes
de desfecho da Metodologia (`Metodologia.tex:219-229` e `349-353`). Exposição e perda permanente são
desfechos novos, da DT-027, e precisam ser definidos na Metodologia.

### R1. Execuções e blocos por célula (essencial; nova)

```latex
\begin{table}[!htb]
\caption{Execuções e blocos da avaliação do controlador por célula}
\label{tab-aval-execucoes}
\centering
\footnotesize
\begin{tabular}{p{3.0cm}p{3.6cm}p{1.6cm}p{1.4cm}p{2.0cm}p{2.4cm}}
\hline
\textbf{Célula} & \textbf{Falha injetada} & \textbf{Braços} & \textbf{Blocos completos} & \textbf{Execuções aceitas, descartadas e puladas} & \textbf{Ações S3 por braço} \\
\hline
\raggedright Partição (F2), teste & \texttt{isn3} isolado por 96,0\,s & PO, PL, AO, AL & 12 & 48, 0, 0 & 95\,780 a 97\,675 \\
\raggedright \emph{Jitter} (F3) & atraso no \texttt{isn2} por 251\,s & A, B & 4 & 8, 0, 0 & 38\,823 e 39\,004 \\
\raggedright Degradação (F4) & 20\,\% de perda no \texttt{isn3} por 89,6\,s & A, B & 4 & 8, 1, 1 & 38\,778 e 39\,599 \\
\raggedright Queda de nó (F1) & queda silenciosa do \texttt{isn2} por 60,5\,s & A, B & 3 & 8, 5, 3 & 25\,960 e 26\,137 \\
\raggedright Sem falha & nenhuma & A, B & 4 & 8, 0, 0 & 48\,668 e 49\,143 \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

### R2. Regra de sucesso na partição (essencial; nova)

```latex
\begin{table}[!htb]
\caption{Regra de sucesso pré-registrada na partição de rede, por efeito do desenho fatorial}
\label{tab-aval-regra-sucesso}
\centering
\footnotesize
\begin{tabular}{p{2.4cm}p{3.0cm}p{2.6cm}p{2.0cm}p{1.6cm}p{1.8cm}}
\hline
\textbf{Efeito} & \textbf{Diferença na taxa de violações por mil ações (IC 95\,\%); valor-$p$} & \textbf{Razão do p99 (IC 95\,\%)} & \textbf{Razão da vazão útil (IC 95\,\%)} & \textbf{Valor-$p$ ajustado} & \textbf{Resultado} \\
\hline
\raggedright Controlador ativo & $-0{,}189$ ($-0{,}268$; $-0{,}109$); 0,0005 & 0,972 (0,912; 1,035) & 0,982 (0,957; 1,009) & 0,0025 & melhoria \\
\raggedright Regra de fusão A1 & $+0{,}019$ ($-0{,}095$; 0,132); 0,72 & 0,972 (0,912; 1,035) & 0,995 (0,977; 1,013) & 1,000 & sem melhoria \\
\raggedright Interação & $+0{,}045$ ($-0{,}174$; 0,265); 0,66 & 0,944 (0,832; 1,072) & 1,009 (0,963; 1,057) & não se aplica & nula \\
\hline
\end{tabular}
\fonte{O autor. Intervalos $t$ de 95\,\% sobre 12 blocos; teste por troca de sinal exata; regra de interseção-união \citep{berger_iut_1982}; ajuste de \citet{holm_sequential_1979} entre os dois efeitos}
\end{table}
```

### R3. Desfechos por braço na partição (essencial; nova)

```latex
\begin{table}[!htb]
\caption{Desfechos na partição de rede por braço, médias de 12 blocos}
\label{tab-aval-bracos}
\centering
\footnotesize
\begin{tabular}{p{3.2cm}p{1.6cm}p{1.6cm}p{1.6cm}p{1.6cm}p{1.6cm}p{1.4cm}}
\hline
\textbf{Braço} & \textbf{Violações por mil ações} & \textbf{Sessões expostas} & \textbf{Sessões perdidas} & \textbf{Falhas percebidas por mil} & \textbf{p99 (s)} & \textbf{Vazão útil (ações/s)} \\
\hline
\raggedright Regra atual, controlador observando & 0,378 & 37 & 11 & 22,7 & 1,504 & 31,8 \\
\raggedright Regra atual, controlador ativo & 0,167 & 16 & 9 & 26,5 & 1,504 & 31,1 \\
\raggedright Regra A1, controlador observando & 0,374 & 36 & 0 & 22,4 & 1,504 & 31,5 \\
\raggedright Regra A1, controlador ativo & 0,208 & 19 & 0 & 25,2 & 1,442 & 31,1 \\
\hline
\end{tabular}
\fonte{O autor. Sessões expostas e perdidas somadas nos 12 blocos}
\end{table}
```

### R4. Células descritivas (essencial; nova)

```latex
\begin{table}[!htb]
\caption{Células descritivas da avaliação do controlador}
\label{tab-aval-descritivas}
\centering
\footnotesize
\begin{tabular}{p{3.0cm}p{1.0cm}p{1.6cm}p{2.4cm}p{2.4cm}p{2.2cm}}
\hline
\textbf{Célula} & \textbf{Blocos} & \textbf{Violações (A e B)} & \textbf{Limite superior por mil ações (A e B)} & \textbf{Razão do p99 (IC 95\,\%)} & \textbf{Ações do controlador} \\
\hline
\raggedright Queda de nó (F1) & 3 & 0 e 0 & 0,115 e 0,115 & 1,000 (1,000; 1,000) & nenhuma \\
\raggedright \emph{Jitter} (F3) & 4 & 0 e 0 & 0,077 e 0,077 & 0,981 (0,889; 1,083) & nenhuma \\
\raggedright Degradação (F4) & 4 & 0 e 0 & 0,077 e 0,076 & 0,624 (0,381; 1,023) & nenhuma \\
\raggedright Sem falha & 4 & 0 e 0 & 0,061 e 0,062 & 0,979 (0,951; 1,008) & nenhuma \\
\hline
\end{tabular}
\fonte{O autor. Limite superior unilateral de 95\,\% para zero eventos, próximo de $3/N$ \citep{hanley_lippmanhand_zero_1983}}
\end{table}
```

### R5. Secundários e custos na partição (complemento; nova)

```latex
\begin{table}[!htb]
\caption{Desfechos secundários na partição de rede, por efeito}
\label{tab-aval-secundarios}
\centering
\footnotesize
\begin{tabular}{p{4.2cm}p{4.6cm}p{4.6cm}}
\hline
\textbf{Desfecho} & \textbf{Controlador ativo: estimativa (IC 95\,\%); valor-$p$ ajustado} & \textbf{Regra A1: estimativa (IC 95\,\%); valor-$p$ ajustado} \\
\hline
\raggedright Sessões expostas na janela & $-1{,}58$ ($-2{,}24$; $-0{,}92$); 0,018 & $+0{,}08$ ($-0{,}85$; 1,02); 1,0 \\
\raggedright Sessões com invalidação perdida & $-0{,}08$ ($-0{,}44$; 0,27); 1,0 & $-0{,}83$ ($-1{,}27$; $-0{,}40$); 0,035 \\
\raggedright Falhas percebidas por mil ações & $+3{,}30$ (1,32; 5,28); 0,055 & $-0{,}85$ ($-2{,}51$; 0,82); 1,0 \\
\raggedright Taxa de erro (pontos percentuais) & $+0{,}89$ (0,34; 1,44); 0,055 & $-0{,}24$ ($-0{,}72$; 0,23); 1,0 \\
\raggedright Tempo de recuperação (s) & $-1{,}36$ ($-5{,}44$; 2,72); 1,0 & $-1{,}47$ ($-5{,}76$; 2,81); 1,0 \\
\raggedright CPU por mil ações úteis (razão) & 1,038 (0,961; 1,121); 1,0 & 1,003 (0,957; 1,051); 1,0 \\
\raggedright Energia estimada por mil ações úteis (razão) & 1,024 (0,945; 1,109); 1,0 & 1,000 (0,950; 1,053); 1,0 \\
\hline
\end{tabular}
\fonte{O autor. Ajuste de \citet{holm_sequential_1979} entre os nove desfechos secundários pré-registrados de cada efeito; CPU e energia com 4 dos 12 blocos}
\end{table}
```

### R6. Sensibilidades (complemento; nova)

```latex
\begin{table}[!htb]
\caption{Análises de sensibilidade da taxa de violações na partição de rede}
\label{tab-aval-sensibilidade}
\centering
\footnotesize
\begin{tabular}{p{4.0cm}p{4.4cm}p{4.4cm}}
\hline
\textbf{Análise} & \textbf{Controlador ativo: diferença (IC 95\,\%); valor-$p$} & \textbf{Regra A1: diferença; valor-$p$} \\
\hline
\raggedright Oficial, 12 blocos & $-0{,}189$ ($-0{,}268$; $-0{,}109$); 0,0005 & $+0{,}019$; 0,72 \\
\raggedright Sem a forma 2 de I1 & $-0{,}071$ ($-0{,}130$; $-0{,}011$); 0,031 & $+0{,}032$; 0,28 \\
\raggedright Sem o bloco 13 & $-0{,}187$ ($-0{,}275$; $-0{,}098$); 0,0010 & $+0{,}002$; 0,95 \\
\raggedright Sem a sonda de \emph{replay} & $-0{,}029$ ($-0{,}068$; 0,011); 0,17 & $-0{,}092$; 0,0068 \\
\hline
\end{tabular}
\fonte{O autor. A sensibilidade sem a sonda é descritiva, não pré-registrada}
\end{table}
```

## 5.13 Já está na monografia × novo ou mudou

| Tema | O que o texto atual diz | O que é agora | Ação |
|---|---|---|---|
| Situação da avaliação | "desenhada, mas não foi pré-registrada nem executada" (`Desenvolvimento.tex:217`) | pré-registrada (DT-026/027/028), executada em 06 e 07/11, analisada em 08/11 e validada em 11/11 | **corrigir** |
| Desenho | dois braços A e B em quatro células (sem falha, F2, F4 e F1), oito blocos cada, mais duas células de referência em `DIST_SYNC` com quatro blocos; cerca de 80 execuções (`Metodologia.tex:347`) | fatorial 2×2 na partição (regra de fusão × controlador) com 12 blocos; quatro células descritivas de 4 blocos (F1, F3, F4 e sem falha), F3 incluída; sem células `DIST_SYNC`; 90 execuções | **corrigir** (capítulo 6) |
| Contagem da taxa de violações | "soma das violações de I1, das de I4 e das de I2 classificadas como reais, por mil ações S3" (`Metodologia.tex:219` e `349`) | **pares distintos** (sessão ou identidade, invariante) com ao menos uma detecção nas fases durante e recuperação, somando auditor online, sonda de *replay* e varredura final, por mil ações S3 | **corrigir** e explicar que a taxa mede exposição |
| Holm na regra principal | "aos valores-p de interseção-união das células", três células com falha (`Metodologia.tex:351`) | entre os dois efeitos do fatorial (controlador e A1) na única célula de teste, m = 2 | **corrigir** |
| Secundários | lista sem exposição e perda; p99 por operação e cada invariante como secundários com Holm (`Metodologia.tex:351`) | família de Holm de 9 por efeito: Δ_rec, Δ_conv, divergência, taxa de erro, falhas percebidas, CPU, energia, exposição e perda permanente; p99 por operação e cada invariante só descritivos (DT-021 §5.3, DT-027 §2.2); a ferramenta usou 11, desvio a declarar | **corrigir e acrescentar** |
| Exclusão de CPU por *steal* | só *steal* acima de 5 % (`Metodologia.tex:335`) | também *steal* sem medida tira o bloco; na F2 sobraram 4 de 12 blocos (sensibilidade com 12 na seção 5.7) | **acrescentar** |
| P1 e vazão | P1 para o nó, e a vazão útil pode cair por construção (`Metodologia.tex:342` e `353`) | P1 foi executada pelo AC-7 (o nó isolado ignora o *cache* de sessões, ação desfeita quando ele entra em modo degradado ou depois da fusão), aplicado e desfeito 24 vezes; vazão não inferior (0,982) | **corrigir** (capítulo 4) |
| Resultados | inexistentes | este capítulo | **novo** |

## 5.14 Fontes bibliográficas necessárias

Todas as chaves abaixo já estão em `monografia-atual/bibliografia.bib`. Nenhuma busca nova é necessária para
este capítulo.

| Afirmação | Chave |
|---|---|
| Regra de interseção-união e valor-p como o maior dos dois | `berger_iut_1982` |
| Correção de Holm | `holm_sequential_1979` |
| Teste de permutação por troca de sinal | `fisher_design_experiments_1935`, `good_permutation_tests_2005` |
| Intervalo t | `student_probable_error_1908` |
| *Bootstrap* por blocos (sensibilidade) | `efron_tibshirani_bootstrap_1993` |
| Limite superior para zero eventos (≈ 3/N) | `hanley_lippmanhand_zero_1983` |
| Modelo de energia, potência máxima e ociosidade | `fan_power_provisioning_2007`, `intel_i5_14600kf`, `barroso_holzle_energy_proportional_2007` |
| Energia em VM só por modelo | `kansal_vm_power_2010` |
| Pré-registro | `nosek_preregistration_2018` |

## 5.15 Pendências e o que não foi verificado

- **`exec-0037`:** o mecanismo do desfazer pela maioria não foi testado (ensaio descartado em 11/11). Fica como
  hipótese e risco.
- **Linhas da A1 na fusão:** não foram preservadas. Não se sabe quantas vezes a A1 escolheu diferente da regra
  atual, e os descritivos da DT-027 §2.2 ficam indisponíveis (ENTREGA item 9f).
- **Formas 1 e 2:** as contagens por forma ("20 das 21 formas 1 viraram perda; 0 de 26 com a A1") vêm da
  pré-análise e não foram reconferidas. A sensibilidade sem a forma 2 é a oficial.
- **`exec-0059`:** o vazamento de `DENY_READ_WRITES` e o motivo da divergência da `exec-0034` (I5) vêm da
  pré-análise e não foram reconferidos.
- **Instantes do AC-7:** em relação ao corte e à detecção, ficam no capítulo 4, que os tira da pré-análise.
