# Relatório do W-5e: análise do fatorial 2×2 no `comparar_bracos.py` (2026-11-06)

Branch `feature/w5e-fatorial` (worktree `wt-w5e`), a partir de `edfa8b6`. Especificação: DT-027, complemento 1 do
pré-registro do *benchmark* refeito (`4fa4546`), seções 1, 2 e 5. Só Python (`analysis/`), sem *push* e sem
integração. Desenho em `docs/implementacao.md` (TD-021).

## O que foi feito

- **Manifesto e braço.** O bloco tem 2 braços (A, B) ou 4 (PO, PL, AO, AL), sem misturar na célula. Na célula
  fatorial, o braço do manifesto é conferido contra o `execucao.json` (`fusao` = `pnn`|`a1`, `controller` =
  `shadow`|`active`, `bloco`) e contra o modo da trilha.
- **Contrastes por bloco.** A1, A4 e interação, mais os quatro efeitos simples (só IC). Escala de diferença para V e
  as taxas, log para o p99. A troca de sinal exata vale nos três contrastes e só na célula de teste.
- **Regra de sucesso.** IU por efeito principal, com V, p99, vazão útil e a rastreabilidade de PL e AL. Holm global
  sobre as hipóteses de teste (`<celula>:A1`, `<celula>:A4`; m = 2 com só a F2). A interação tem IC e p, fica fora do
  Holm e da IU.
- **Secundários.** Holm por efeito, incluindo as duas medidas novas.
- **Perda permanente e exposição na janela.** Calculadas em `apuracao_v.decompor_i1_pela_fusao`, a fonte única. Usam só
  as detecções de I1 que contam em V, separadas pelo `t_fusao_ns` do `execucao.json`.
- **Varredura com erro.** O tratamento simétrico do W-5d vale nos quatro braços. No bloco afetado, a perda permanente
  fica não avaliável, e o detalhe traz o número de blocos por braço da falha e a sensibilidade sem esses blocos.
- **Células descritivas de 2 braços.** Sem mudança.

## Commits

| Commit | O quê |
|---|---|
| `1c819d6` | `test:` golden da análise de 2 braços com sonda, varredura e varredura com erro, gerado na base (não regressão) |
| `ed6a364` | `test:` 29 testes do fatorial (18 vermelhos) |
| `bb319f8` | `feat:` análise do fatorial, perda e exposição, varredura com erro nos quatro braços |
| (último) | `docs:` TD-021, `testes.md` e este relatório |

## Testes

`python -E -P -m unittest discover -s analysis`: **517 OK, 1 *skip*** (486 da base + 31). Os goldens da base (sem
instrumentos, do W-5c, e com instrumentos, novo) continuam iguais, e a célula de 2 braços dá o mesmo resultado
sozinha ou junto de uma célula fatorial no manifesto. Detalhe por classe em `docs/testes.md`.

## Escolhas que o pré-registro não fixa

1. **Nome dos campos.** O orquestrador fatorial (`wt-fat`) ainda estava em `edfa8b6`, sem campo novo. Usei `fusao`
   (`pnn`|`a1`) e `t_fusao_ns` no `execucao.json`, mais o `controller` e o `bloco` que já existem. O complemento chama a
   coluna da matriz de `regra`; se o orquestrador gravar outro nome, troca-se `CAMPO_FUSAO` ou `CAMPO_T_FUSAO`.
2. **Perda e exposição só com as detecções que contam em V** (fases pedidas, a mesma atribuição do W-5c), para serem
   decomposições de V, como diz a 2.1. Uma detecção online exatamente em `t_fusao_ns` vai para a perda. O *replay*
   imediato depois da fusão não entra em nenhuma das duas, porque a 2.2 não o lista.
3. **H_V não avaliável por efeito** quando os braços de referência não têm violação em nenhum bloco: PO e PL para a
   A1, PO e AO para a A4.
4. **Rastreabilidade integral** dos braços ativos (PL e AL) exigida nos dois efeitos.
5. **Sem IC hierárquico** do p99 no fatorial. Os outros IC (t e *bootstrap* de blocos) estão lá.
6. **Perda e exposição também nas células de 2 braços** quando o `execucao.json` trouxer `t_fusao_ns`. Elas entram
   como secundárias, e sem o campo nada muda.

## Ponto fora do repositório

Por engano, criei o arquivo `C:\FRANCE~1\placeholder.txt` (conteúdo "x") e a pasta `C:\FRANCE~1`, fora do
*scratchpad*. A verificação de segurança barrou a remoção da pasta. Os dois podem ser apagados à mão.
