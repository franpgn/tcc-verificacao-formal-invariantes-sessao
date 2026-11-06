# V-W5c: validação independente de V com a sonda de *replay* e a varredura no `comparar_bracos.py` (2026-11-06)

> Senior, 2026-11-06. Alvo: `wt-w5c`, branch `feature/w5c-v-com-sonda`, base `daa18a2`, commits `9093e6f`..`ae22b9c`
> (`analysis/comparar_bracos.py` e `analysis/test_comparar_bracos_w5c.py`). Relatório do Worker:
> `docs/relatorio-w5c-2026-11-06.md`. Regra: rascunho do pré-registro do benchmark refeito, §2
> (`wt-bench/docs/pre-registro-benchmark-refeito-2026-11-06.md`). O código do W-10 que gera os dados foi conferido em
> `wt-w10` (`7c94cfb`). Só Python; o código do Worker não foi alterado.

## 0. Veredito

**Aprovado com ressalvas.** O código implementa a §2: V por par distinto (chave, classe) das três origens, I2-PERDIDO
na classe I2, fases pela detecção (online e *replay* imediato) e pela invalidação confirmada (tardio e varredura), as
duas formas de I1 e a sensibilidade `:sem_forma2`. O `ReplayValidate` fica fora de latência, vazão útil, taxa de erro
e denominador. Sem sonda e sem varredura, o resultado é o da base. A suíte passa, e as 7 mutações que fiz foram pegas.

**O que muda:**
1. **Código (W-5d):** varredura com erro passa a ter o tratamento simétrico proposto pelo coordenador (seção 5). Hoje
   o bloco sai do teste.
2. **Pré-registro:** regra escrita para as ambiguidades 1, 3, 4, 5, 6, 7, 8 e 10, e uma nota para a 14 (seção 4). A
   regra escrita só fixa o que o código já faz, salvo a 10.

## 1. V por par distinto, fases e formas

| Ponto | Situação |
|---|---|
| Par (chave, classe), três origens | confere: `apurar_violacoes_execucao` junta as detecções por (chave, classe); online e sonda pelo `violacoes-rep`, a varredura pelo `varredura-rep`, só `REAL`. I2 *online* e I2 administrativo da varredura têm a sid como chave e caem no mesmo par. **I2-PERDIDO vai à classe `I2_REAL` com a identidade como chave** e fica num par próprio |
| Um par conta se alguma detecção cai nas fases pedidas | confere com a 2.3 ("ao menos uma detecção") somada à 2.4 (fases) |
| Fases | confere: online e imediata pelo instante da detecção. A detecção da sonda depois da última `end_ns` da carga (`t1 − 1`, calculado sem `ReplayValidate`) é tardia. Tardia e varredura vão pelo `end_ns` do `Logout NONE` mais antigo da sid (I1 e I4 com motivo `LOGOUT`) ou do último `Block NONE` da identidade (I2 e I2-PERDIDO). Sem invalidação localizada, a detecção conta sem filtro e entra em `sem_invalidacao_localizada` |
| Forma 1 | confere com a regra de classificação da 2.5: I1 cujo `Logout` foi confirmado pela réplica do nó da falha (coluna `replica`, nó tirado do `node=` do `start` e do `--servers`) |
| Forma 2 | confere: `Logout` confirmado por outra réplica e leitura reveladora (o `Validate`/`Logout` *online* ou o `ReplayValidate` imediato, casado até 1 s) servida pela réplica do nó da falha na janela `start`..`end` |
| Sensibilidade `:sem_forma2` | confere: família sensibilidade, escala diferença, sem Holm; ausente com motivo quando as formas não são avaliáveis e há I1 |

## 2. `ReplayValidate`

Confere. A exclusão já vem da base: `energia_proxy.ler_eventos_inicio` descarta o tipo (`OPERACOES_INSTRUMENTACAO`, o
mesmo de `6307230`, presente em `daa18a2`). O `evento_p99:*`, a `taxa_erro`, o Δ_rec e as fronteiras `t0`/`t1` não o
veem. Latência da ação S3, vazão útil, falhas percebidas e denominador de V vêm do `s3-acoes`, onde a sonda não grava.
O leitor novo `ler_eventos_por_chave` lê o tipo só para casar a forma 2 e para o custo da sonda. O teste
`ReplayValidateFora` compara uma execução com e sem sonda e acha os mesmos `y` e `n` em latência, vazão, erro, falhas,
Δ_rec e denominador.

## 3. Não regressão

Conferi com código meu, sem confiar no teste:
- extraí `analysis/` de `daa18a2` e rodei o cenário do golden (`rodar_golden`) com o `comparar_bracos.py` da base: a
  saída é **idêntica, chave a chave**, ao `golden-sem-sonda-daa18a2.json`;
- com o código novo, o golden está contido na saída. As únicas chaves novas são `parametros.regra_v`,
  `parametros.v_sem_forma2` e `violacoes.detalhe`.

O golden não mudou desde `9093e6f`, e esse commit não toca o `comparar_bracos.py`. A suíte completa passou
(`python -E -P -m unittest discover -s analysis`: 396 testes, OK, 1 pulado). `test_comparar_bracos.py` e
`test_desfechos_coprincipais.py` também passam, sem edição.

**Mutações** (cópias de `analysis/`, rodando `test_comparar_bracos*.py`): as 7 foram pegas.

| # | Mutação | Resultado |
|---|---|---|
| 0 | V por linha em vez de par | morta (7 testes) |
| 1 | tardio pelo instante da própria detecção | morta (3) |
| 2 | sem invalidação localizada não conta | morta (2) |
| 3 | forma 2 sem a janela da falha | morta (1) |
| 4 | varredura INCERTA entra em V | morta (3) |
| 5 | I2-PERDIDO pelo primeiro `Block` | morta (2) |
| 6 | forma 1 vira "outra" | morta (2) |

## 4. Ambiguidades (o relatório lista 14)

| # | Escolha do Worker | Decisão |
|---|---|---|
| 1 | par distinto em toda execução, também sem instrumentos | **aceitar e escrever.** É a 2.3, e o golden mostra que nada muda com chaves distintas. A regra substitui a contagem por linhas da DT-021; o texto deve dizer isso |
| 2 | `:sem_forma2` só com instrumentos (índice de semente) | aceitar; detalhe de implementação, porque no benchmark refeito toda execução tem os instrumentos |
| 3 | tardia = detecção da sonda depois da última `end_ns` da carga | **aceitar e escrever** (uma linha na 2.4). A imediata em voo no fim da carga cai na mesma fase pelos dois critérios |
| 4 | instante da invalidação = `end_ns` (confirmação) | **aceitar e escrever.** É o instante em que o cliente soube da confirmação, o mesmo corte do *replay* tardio do W-10. Precisa ficar explícito, porque a fase dos eventos anômalos (P-2) usa o início |
| 5 | cascata (motivo `ADMIN_BLOCK`) sem evento pela sid conta sem filtro | **aceitar e escrever.** A 2.4 cita "a cascata", mas a regra "pela chave" não acha o `Block`, cuja chave é a identidade. A reserva "conta sem filtro" é simétrica e conservadora; o texto deve dizer que a cascata cai nela e que a contagem vai ao relatório (`sem_invalidacao_localizada`) |
| 6 | I2 e I2-PERDIDO pelo último `Block NONE` da identidade | **aceitar e escrever** (meia linha na 2.4) |
| 7 | janela da forma 2 = `start` a `end` | **aceitar e escrever na 2.5.** O instante da detecção da falha pelo *cluster* não está nos CSV, e com `DENY_READ_WRITES` o isolado recusa leitura depois dela |
| 8 | par é forma 2 só se todas as detecções contadas forem forma 2 | **aceitar e escrever.** Se a varredura achou o par `REAL`, o estado final ficou errado, e a exposição não foi só transitória |
| 9 | réplica do nó pelo `--servers` do `execucao.json` | aceitar; resolvido pela §8 do pré-registro (campanhas pelo orquestrador). Sem ele, a sensibilidade fica ausente com motivo |
| 10 | varredura com erro: V ausente, bloco fora | **mudar** (seção 5) **e escrever** |
| 11 | tolerância de casamento de 1 s | aceitar; é parâmetro de alinhamento de relógio, com origem medida (7,4 ms no *smoke*). Basta citar na 2.5 |
| 12 | instrumentos diferentes entre A e B na célula de teste: exit 2 | aceitar; segue a 2.1 |
| 13 | marca de teto do tardio com `sids ≥ teto` | aceitar; o `stdout` (`tardias_candidatas`) é a fonte exata |
| 14 | o mesmo `Block` perdido como I2-PERDIDO (identidade) e I1 `ADMIN_BLOCK` (sids) | aceitar como a 2.3 define (pares diferentes). **Nota no texto**, para quem ler os resultados não tomar isso por dupla contagem escondida |

## 5. Varredura com erro: proposta do coordenador

**Avaliação: a proposta é a melhor das três opções, e o código deve mudar.**

- **Excluir o bloco (hoje)** condiciona a análise a uma variável posterior ao tratamento. Se o AC-5 parou um nó e o
  religamento falhou, a varredura tende a falhar no braço B, e os blocos excluídos são justamente os em que o
  controlador agiu. A estimativa fica enviesada para o lado que o experimento quer medir.
- **Contar sem a varredura só no braço que falhou** mede A e B com instrumentos diferentes dentro do bloco. A diferença
  do bloco passa a misturar efeito com instrumento.
- **Simétrica (coordenador):** no bloco em que a varredura falhou em qualquer braço, V dos dois braços usa só online e
  sonda. Os dois braços do bloco ficam medidos com o mesmo instrumento, e a diferença do bloco continua comparável.
  Sob a hipótese nula exata (o controlador não muda nada, nem a falha da varredura), a falha não depende do rótulo, e o
  teste por troca de sinal continua exato. O custo é que o estimando passa a misturar blocos com e sem varredura, e o
  V desses blocos fica um pouco subestimado nos dois braços (o tardio da sonda cobre parte do que a varredura veria).
  As duas coisas se resolvem declarando a regra e reportando os blocos.

**O que o código precisa (W-5d), com testes:**
1. `apurar_violacoes_execucao` devolve também os totais sem as detecções da varredura (pares e pares sem a forma 2).
2. Em `comparar`, para cada bloco de cada célula (teste, descritiva e referência) em que algum braço tenha
   `varredura = erro`, V e `:sem_forma2` dos dois braços passam a usar esses totais. O bloco fica no teste, e o
   `co_principal_v` usa as contagens trocadas.
3. `violacoes.detalhe` lista esses blocos com o braço da falha e os dois V (com e sem a varredura, nos braços em que
   ela existiu). A tabela Markdown traz uma linha com o número de blocos.
4. A guarda de instrumentos continua tratando `erro` como varredura pedida (já é assim em `_instrumentos`).
5. Testes: falha só em A, só em B e nos dois; bloco sem falha intacto; diferença do bloco com os mesmos instrumentos;
   golden intacto.

**Texto no pré-registro (2.3/2.4):** a regra simétrica, a declaração da subestimação e o número de blocos afetados por
célula e por braço da falha. Recomendo, além disso, uma sensibilidade descritiva com esses blocos excluídos (o
comportamento atual), sem Holm.

## 6. Nota para a integração

O `calibracao_s3.py` (W-9) tem um teste de paridade que compara a sua taxa de violações por mil, contada por linha, com
a do `comparar_bracos`. Com o W-5c, o `comparar_bracos` passa a contar pares. A paridade continua passando nos dados
sintéticos atuais (as três violações da `f2a` têm invariantes diferentes). Mas, na recalibração do benchmark refeito,
as taxas que o W-9 passa ao `poder.py` precisam seguir a regra nova (pares, três origens), senão o poder sai de outra
métrica. Isso fica como item do W-9 na §8 do pré-registro, não deste item.
