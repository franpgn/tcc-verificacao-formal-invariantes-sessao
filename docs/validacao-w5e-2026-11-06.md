# V-W5e: validação independente da análise do fatorial 2×2 no `comparar_bracos.py` (2026-11-06)

> Senior, 2026-11-06. Alvo: `wt-w5e`, branch `feature/w5e-fatorial`, diff `edfa8b6..d1bb887`
> (`analysis/comparar_bracos.py`, `analysis/apuracao_v.py`, `analysis/test_comparar_bracos_fatorial.py`). Regra: DT-027,
> complemento 1 do pré-registro do *benchmark* refeito (`wt-bench`, `4fa4546`), seções 1, 2 e 5. Relatório do Worker:
> `docs/relatorio-w5e-2026-11-06.md`. Só Python; o código do Worker não foi alterado.

## 0. Veredito

**Aprovado com ressalvas.** Os contrastes, a regra de sucesso, a decomposição da perda pela fusão, o tratamento
simétrico nos quatro braços e as guardas implementam a DT-027. A análise de 2 braços não mudou, o que conferi com código
meu contra os dois goldens. A suíte passa (`-E -P`: 517 testes, OK, 1 pulado) e pega as mutações (seção 4).

**O que muda:**
1. **Regra escrita no pré-registro** para as escolhas 2, 3 e 4 (seção 5).
2. **Duas guardas pequenas na análise (W-5f), sem bloquear:**
   - `t_fusao_ns = null` tem de vir com a varredura com erro, como manda a DT-027 §5.3;
   - a mesma semente nos quatro braços do bloco (seção 3).
3. **Contrato com o orquestrador (`wt-fat`):** os nomes dos campos (escolha 1).

## 1. Contrastes, regra de sucesso e H_V

| Ponto | Situação |
|---|---|
| Contrastes por bloco | confere: `_contraste` dá A1 = ½[(AO − PO) + (AL − PL)] = média(AO, AL) − média(PO, PL); A4 = ½[(PL − PO) + (AL − AO)] = média(PL, AL) − média(PO, AO); interação = (AL − AO) − (PL − PO). Escala diferença para V e taxas; log para o p99, com a estimativa em exp(média). Os quatro efeitos simples vão só com IC |
| Teste | troca de sinal exata por contraste, só na célula de teste; nas descritivas, IC sem p |
| IU por efeito principal | confere: `regra_iu` por efeito, com H_V (p da troca de sinal, média < 0), H_L (limite superior do IC t do p99 ≤ 1 + δ_L e p de não inferioridade), vazão útil não inferior e rastreabilidade de PL e AL. `p_IU` = max(p_V direcional, p_L) |
| Holm m = 2 | confere: as hipóteses `<célula>:A1` e `<célula>:A4` entram na `conclusao_global`. Com a F2 como única célula de teste, m = 2 |
| Interação | confere: IC e p, `p_holm` nulo, fora da IU e fora do Holm dos secundários (os dois laços só percorrem `EFEITOS_PRINCIPAIS`) |
| Secundários | Holm por efeito principal, com a perda permanente e a exposição dentro da família (DT-027 §2.2) |
| H_V não avaliável | confere: `_co_principal_v_fatorial` marca não avaliável quando os braços de referência do efeito somam zero violação nos blocos usados (A1: PO e PL; A4: PO e AO), e a IU sai "H_V nao avaliavel" |
| Sementes | índice 10·i + j por desfecho e contraste (j ≤ 6) e 10·(número de desfechos) + j na sensibilidade, sem colisão |

## 2. Perda permanente e exposição na janela (`decompor_i1_pela_fusao`)

Confere com a DT-027 §2.2. Só entram pares de I1 com detecções que contam em V. Uma sid pode cair nas duas medidas.
- **Perda permanente:** varredura (I1 REAL), *replay* tardio e I1 *online* com detecção em `t_fusao_ns` ou depois.
- **Exposição na janela:** *replay* imediato e I1 *online* antes de `t_fusao_ns`. A imediata depois da fusão não entra
  em nenhuma das duas, como na 2.2, que não a lista.

As duas medidas são contagens de sids em escala de diferença.
- Ausentes sem o campo `t_fusao_ns` (medida não emitida).
- Não avaliáveis com `t_fusao_ns = null` ("a fusão não veio").
- A perda fica não avaliável na execução com a varredura com erro e, no fatorial, em todos os braços do bloco afetado
  (DT-027 §5.3). A exposição não depende da varredura e fica.

## 3. Tratamento simétrico, não regressão e guardas

- **Tratamento simétrico com 4 braços:** confere. `_aplicar_varredura_simetrica(..., BRACOS_FATORIAL)` troca V e
  `:sem_forma2` dos quatro braços pelos totais sem a varredura quando algum braço falhou, e marca a perda do bloco como
  não avaliável nos quatro. O detalhe traz o número de blocos por braço da falha e a sensibilidade sem esses blocos,
  com os três contrastes, sem p e sem Holm.
- **Não regressão de 2 braços:** conferi com código meu.
  - Extraí `analysis/` de `edfa8b6` e rodei o cenário do golden novo (célula de teste F2 do W-5d e descritiva D do
    W-5c). A saída é **idêntica, chave a chave**, a `golden-2bracos-com-instrumentos-edfa8b6.json`.
  - A base também contém o golden do W-5c.
  - Com o código novo, o golden de 2 braços está contido na saída, e a única chave nova é `parametros.fatorial`.
  - O `test_comparar_bracos_w5c.py` (gerador dos cenários) é igual na base e na ponta.
- **Guardas de braço:** conferem.
  - O manifesto aceita 2 ou 4 braços por bloco, sem misturar na célula.
  - Cada braço é conferido contra o `execucao.json` (`fusao`, `controller`, `bloco`) e contra o modo da trilha.
  - Na célula de teste, a trilha é obrigatória nos quatro braços, e PL e AL têm de atuar (atuador e política não
    nulos).
  - O `param_sha256` é o mesmo nos quatro, e os instrumentos de observação também (exit 2 se não).

**Duas guardas que faltam (W-5f, não bloqueiam a análise):**
1. **`t_fusao_ns = null` sem a varredura com erro.** A DT-027 §5.3 diz que, sem fusão até o teto, a execução é tratada
   como varredura com erro. Hoje a análise só aplica o tratamento simétrico se houver `varredura-rep-NNN.erro.txt`.
   Com `t_fusao_ns = null` e sem esse arquivo, V do bloco usaria uma varredura que pode ter rodado antes da fusão, que
   é justamente o que a §5.3 evita. Proposta: `t_fusao_ns = null` conta como varredura com erro na apuração; ou recusa
   (exit 2) se o `.erro.txt` não existir. Prefiro a primeira, que não depende do orquestrador.
2. **Mesma semente nos quatro braços.** A DT-027 §1 fixa "quatro braços com a mesma semente", e a análise não confere o
   `seed` do `execucao.json`. A §5.4 põe o pareamento no pré-voo do orquestrador, e a guarda na análise custa uma linha.

## 4. Mutações

Rodei `test_comparar_bracos*.py` em cópias de `analysis/`, uma por mutação.

| # | Mutação | Resultado |
|---|---|---|
| 0 | A4 calculada com o contraste da A1 | pega (2 testes) |
| 1 | interação somada em vez de diferença | pega (1) |
| 2 | interação entra no Holm e na IU (m = 3) | pega (15 erros, porque a interação não tem braços de referência; o código quebra antes de chegar às asserções, mas a mutação não passa) |
| 3 | H_V da A4 avaliada pelos braços de referência da A1 | pega (1) |
| 4 | *replay* imediato depois da fusão contado na exposição | pega (1) |
| 5 | I1 *online* depois da fusão fora da perda | pega (1) |
| 6 | varredura com erro trocando só o braço que falhou | pega (7, inclusive o golden) |
| 7 | sem conferir a regra de fusão do braço | pega (2) |

## 5. As 6 escolhas do relatório

| # | Escolha | Decisão |
|---|---|---|
| 1 | nomes `fusao` (`pnn`/`a1`) e `t_fusao_ns` no `execucao.json` | **aceitar.** É contrato com o orquestrador fatorial (`wt-fat`), não regra estatística. Registrar na DT-027 §5 (itens 1 e 3) os nomes e os valores, para o `wt-fat` gravar os mesmos. A matriz pode chamar a coluna de `regra`, desde que o `execucao.json` traga `fusao` |
| 2 | perda e exposição só com as detecções que contam em V; online em `t_fusao_ns` vai à perda; imediata depois da fusão fora das duas | **aceitar e escrever** (uma frase na §2.2). É o que faz delas decomposições de V, como diz a §2.1, e a imediata depois da fusão já fica fora pelo texto |
| 3 | H_V não avaliável por efeito quando os braços de referência (A1: PO e PL; A4: PO e AO) não têm violação em nenhum bloco | **aceitar e escrever** (§2.1). É a regra da DT-021 levada a cada efeito: os braços de referência são os do nível de controle do fator testado |
| 4 | rastreabilidade integral de PL e AL exigida nos dois efeitos | **aceitar e escrever** (§2.1). A §2.1 exige "rastreabilidade integral" sem dizer de quais braços; os dois braços ativos entram nos contrastes dos dois efeitos |
| 5 | sem IC hierárquico do p99 no fatorial | **aceitar.** Na DT-021 a IU do p99 já usa o IC t, e o hierárquico é descritivo. Basta citar no relatório de resultados |
| 6 | perda e exposição também nas células de 2 braços quando houver `t_fusao_ns` | **aceitar.** Sem o campo nada muda (golden). Com ele, as duas entram como secundárias. Nota: entram na lista ordenada dos desfechos e mudam o índice de semente dos que vêm depois, o que só altera os IC por bootstrap, de forma determinística. No *benchmark* refeito as células de 2 braços são descritivas, sem Holm |

## 6. Fora do código

O relatório do Worker aponta um arquivo criado por engano fora do *scratchpad* (`C:\FRANCE~1\placeholder.txt`, conteúdo
"x"). Não toquei nele. Remover à mão.
