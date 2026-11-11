# Validação curta V-B26.1-R: fechamento das ressalvas do B-26.1 (2026-10-22)

> Senior, 2026-10-22. Rito do item 5 da seção 8 de `docs/validacao-b26-1-2026-10-22.md`. Objeto:
> branch `feature/b26-1-observador-janela` (worktree `../wt-b26-1`), 6 commits `917b9ec..a841509`
> sobre `c2139f8`, seção 9 do relatório `docs/relatorio-b26-1-2026-10-22.md`. Decisões do Gestor
> aplicadas: violação de segurança atrasada promovida à próxima janela; API pura do *tick*
> (`avancarRelogio`, `seqRegistro`, `FonteDeTempo`) com CA-T1..T3; `L` padrão de 100 ms; p99 por
> janela calculado offline; L1 e L2 só registrados. A branch do Worker não foi alterada: mutantes e
> compilação por commit rodaram sobre cópias `git archive` no *scratchpad* do Senior. Sem push.

## 1. Veredito

**APROVADA.** A branch pode entrar na integração local. Todas as alegações se confirmaram. Os 5
sobreviventes da V-B26.1 morreram. Os 6 mutantes do Worker (P1..P3, TM1..TM3) morreram. Da amostra
própria de 10 mutantes, 8 morreram, 1 é equivalente e 1 sobreviveu. O sobrevivente (Q10) e duas
observações não bloqueiam e viram contrato de B-28.2 (seção 5). Nenhum teste existente foi
enfraquecido (seção 4).

## 2. Medido × alegado

| Alegação | Medido | Como | Confere |
|---|---|---|---|
| Surefire 443 → **462/0/0/0**, pacote `controle` 168 → 187 | **462/0/0/0**, `controle` **187** | `mvn -q -o -f workload/pom.xml test` na worktree em `a841509`, `surefire-reports` apagado antes, soma dos `TEST-*.xml` | sim |
| `test-compile` verde por commit | código 0 em `917b9ec`, `92b64aa`, `8da8e91`, `320aa58`, `6134d82` e `a841509` | `git archive <sha> workload` em diretório limpo + `mvn -q -o test-compile` | sim |
| S4, S5, S12, S13 e S15 mortos | os 5 **mortos**. S4 por `violacoesAtrasadasZeramPorJanela`. S5 por `encerrarDescartaTodasAsClasses` e pelas 3 repetições da concorrência. S12 por `violacoesSeparadas` (`expected <2> but was <4>`). S13 por `marcaNaoDecresce`, `tickNaoRecua` e `promovidaNoEncerramento`. S15 por `rejeitaInvalidos` | mesmo *script* de mutação da V-B26.1, reaplicado em `a841509` | sim |
| P1..P3 e TM1..TM3 mortos | os 6 **mortos**, com as mensagens do relatório. P1: `NCC` × `NNN`. P2: 6 falhas. P3: `expected <1> but was <0>`. TM1: 2 falhas. TM2: `expected <2> but was <1>`. TM3: `IllegalStateException` não lançada | idem | sim |
| Recontagem dos 12 CSVs: 2.004 eventos fora de ordem e máximo de 9.427.900 ns | iguais aos números da V-B26.1 §2, linha 6 (2.004 em S1-F1 rep-001; 9.427.900 ns em S2-none rep-001) | Python com inteiros exatos sobre `runs/` | sim |
| `L` padrão de 100 ms | `TOLERANCIA_PADRAO_NS = 100_000_000L`, que `construtorComParametros` exige | leitura e teste | sim |
| Sem push | `git ls-remote --heads origin` sem *head* `b26` | git | sim |

O código de produção alterado foi lido inteiro no diff `c2139f8..a841509`: `AgregadorDeJanela`,
`JanelaObservada`, `FonteDeTempo` (novo) e o comentário de `EventoOperacao`. A promoção soma a
`violacoesSeguranca` da próxima janela emitida e zera o pendente na emissão. Na janela observada, as
promovidas ficam limitadas a `violacoesSeguranca` (validação no construtor). O *tick* reutiliza
`avancar`, de modo que vale a mesma regra `marca − L ≥ fim` e a marca não recua. `seqRegistro` só
conta `registrar` e `registrarViolacao`. Isso confere com a V-B26.1 §5.5 e §5.9.

## 3. Amostra própria contra a promoção e o *tick* (10 mutantes, nenhum igual aos do Worker)

| ID | Alteração | Resultado | Teste que matou / observação |
|---|---|---|---|
| Q1 | a promovida também soma em `violacoesAtrasadasTotal` (contagem dupla) | morto | concorrência (conservação) e `violacaoSegurancaAtrasadaPromovida` |
| Q2 | `JanelaObservada` recebe `promovidas = 0`, embora a `Janela` some as promovidas | morto | 7 falhas, entre elas a integração R1 |
| Q3 | promove também I2 esperada e I2 fora do gatilho (`!= FORA_DO_GATILHO`) | morto | `violacaoAtrasada[I2_ESPERADA]`, `violacoesAtrasadasZeramPorJanela`, concorrência |
| Q4 | `encerrar` descarta a promovida pendente **antes** de emitir as janelas completas | morto | `promovidaNoEncerramento` |
| Q5 | o *tick* avança no máximo uma janela por chamada | morto | `tickSemEventosEmiteVazias`, `tickFechaEDepoisEventoAtrasado` |
| Q6 | o *tick* incrementa `seqRegistro` | morto | `seqRegistro`, `propriedadeComESemTicks` |
| Q7 | `avancarRelogio(FonteDeTempo)` ignora a fonte | morto | `fonteInjetada` |
| Q8 | não acumula `violacoesSegurancaPromovidasTotal` | morto | concorrência, `violacaoSegurancaAtrasadaPromovida` |
| Q9 | a promoção chama `avancar(fim + W)` | **equivalente** | se a violação é atrasada, `fim < início(proxima)`, logo `fim + W < fim(proxima) ≤ fim(proxima) + L`. Nunca fecha janela nem muda a marca útil |
| Q10 | tira `synchronized` de `avancarRelogio(long)` | **sobreviveu** | `concorrenciaComTick` só exercita a sobrecarga `avancarRelogio(FonteDeTempo)` |

## 4. Nenhum teste existente foi enfraquecido

As linhas removidas de testes em `c2139f8..a841509` foram conferidas uma a uma:

- **`violacaoAtrasada`:** a asserção "SEGURANCA atrasada é descartada" saiu porque a **regra mudou**
  por decisão do Gestor. Ela foi substituída por um laço sobre as 3 classes que continuam
  descartadas, com asserções novas (janela igual à esperada, `promovidas = 0`) e mais 3 testes de
  promoção. O teste ficou mais forte.
- **Concorrência:** antes conservava só SEGURANCA. Agora sorteia as 4 classes, exige que todas
  apareçam, confere a conservação da soma, exige `nas janelas ≤ enviadas` por classe e confere o
  total de promovidas. A conservação exata por classe não é possível porque `violacoesAtrasadas` e
  o descarte no encerramento não são separados por classe. Mesmo assim a mudança é ganho líquido: os
  mutantes S5, Q1, Q3 e Q8 morrem por esse teste.
- **`violacoesSeparadas`:** o esperado de `violacoesForaDoGatilho` passou de 2 para 4 porque o
  teste ganhou duas entradas (I6 e `I2-PERDIDO`). Com isso as contagens ficaram assimétricas, o que
  mata S12.
- **`construtorComParametros`:** passou a exigir 100 ms, por decisão do Gestor.
- **`janelaObservadaValida`:** a aridade mudou e entraram duas asserções novas (promovidas >
  segurança é rejeitado).
- **`ArquiteturaControleTest`:** só acrescentou `FonteDeTempo` à lista exigida.
- **`agregaAmostraReal`:** ver o desvio 3 abaixo; nenhuma asserção foi perdida.

## 5. Desvios 1 a 3 do relatório

1. **T1 usa I2 esperada.** **Correto.** Depois da promoção, uma SEGURANCA atrasada não passa mais
   por `violacoesAtrasadas`, e T1 testaria outra coisa. O zeramento do contador de promovidas tem
   teste próprio (P2 morto por 6 falhas).
2. **Promovida pendente sem janela conta no descarte do encerramento.** **Aceito.** É coerente com
   a decisão do ponto 6: a janela parcial, com a SEGURANCA que estiver nela, também é descartada.
   Ressalva para B-28.2: a linha final de resumo do JSONL deve separar
   `violacoes_seguranca_descartadas_encerramento` (no prazo e promovidas) das demais classes, para
   que uma violação de segurança perdida no fim apareça por nome e não só somada às outras.
3. **Caso da fixture parametrizado com `L` = 0 e `L` = 10 ms.** **Aceito, e o teste ficou mais
   forte.** O caso de 10 ms foi mantido com a asserção de 2 janelas antes de `encerrar`. O de `L` = 0
   (4 antes) e o padrão de 100 ms (0 antes) são novos. Nos três, as janelas finais são as mesmas
   (20/18/9, 46/32/2, 60/46/0, 58/45/1, com 16 descartados). Isso confere com o cálculo
   independente da V-B26.1 e com a regra `fim + L ≤ marca` sobre a fixture de cerca de 20 ms.

## 6. Contratos e observações que passam para B-28.2 (não bloqueiam)

- **Q10:** o teste de concorrência com *tick* de B-28.2 (CA-T3 ao vivo) tem de exercitar a mesma
  sobrecarga que a *thread* de *tick* vai usar. Uma alternativa simples é a sobrecarga `long`
  delegar para a de `FonteDeTempo` ou vice-versa, e manter as duas `synchronized`.
- **Violação antes da origem:** uma SEGURANCA com `end_ns < origem` (por exemplo, no *warm-up*) tem
  `j < 0`, é tratada como atrasada e **promovida para a janela 0**. Com isso R1 pode disparar na
  primeira janela por causa de algo do *warm-up*. O adaptador de B-26.3/B-28.2 decide se só
  registra violações depois da origem ou se aceita esse comportamento. Em qualquer caso, precisa de
  um teste que fixe a escolha.
- Os contratos da V-B26.1 §7 continuam valendo (L1 ordem, L2 salto, cabeçalho do JSONL, linha final
  de resumo, parcial marcada, código inválido capturado), somados ao item do desvio 2 acima.

## 7. Ordem de integração local sugerida em `feature/controlador-integracao`

A branch nova parte de `feature/etapa-formal-integracao` (`6ba7e28`), que é a base de B-28.1/B-26.1
e de B-42.1. Os conjuntos de arquivos são disjuntos. B-28.1 e B-26.1 tocam só
`controle/`, `docs/relatorio-b2[68]-1-*`, e B-42.1 toca `session/`, `cluster/`, `docs/configuracao-infinispan.md`
e `runs/b42-1-smoke/`. Por isso a ordem não gera conflito. Ela é escolhida para preservar os SHAs
de quem tem dependentes.

1. **B-42.1 (`8296270`) por `merge --ff-only`.** B-43.1 e B-43.3 partem de `8296270`, então manter
   esse SHA permite que B-43.x entre depois sem reescrever a base. Medida esperada: Surefire **305/0/0/0**
   (número do adendo B-42.1, a reconferir).
2. **B-28.1 + B-26.1 como uma cadeia (`6ba7e28..a841509`, que contém `dfcdb67`)**, por `rebase`
   sobre o passo 1 e `merge --ff-only`. Esperado: **492/0/0/0** (305 + 187: 112 testes de B-28.1 e
   75 de B-26.1, todos em `controle`). Exigências: `test-compile` em cada commit reescrito, a
   tabela SHA antigo → SHA novo no relatório de integração e `ArquiteturaControleTest` verde.
3. **B-43.1 e depois B-43.3, cada um só depois da sua validação**, por `rebase` sobre o topo e `merge
   --ff-only`, com Surefire após cada passo. Os dois não têm arquivo em comum além da base B-42.1, mas
   ambos tocam `Cli`/`WorkloadMain`/`SessionOps`. Entram um de cada vez, na ordem 43.1 → 43.3, e o
   *golden* S1/S2 tem de seguir idêntico.

Se o Gestor preferir não reescrever os SHAs validados de B-28.1/B-26.1, a alternativa é inverter os
passos 1 e 2: a cadeia do controle entra por `ff-only` e B-42.1 é rebaseado. Nesse caso B-43.x passa
a precisar de *rebase* também.

## 8. O que exige aprovação

- **Gestor:** criar `feature/controlador-integracao` e aprovar a ordem da seção 7. Push de qualquer
  branch para `origin` exige aprovação explícita a cada push.
- **Gestor:** decidir a política de violação antes da origem (seção 6), em B-26.3/B-28.2.
- **Coordenador/Gestor:** liberar B-43.1 e B-43.3 para o passo 3 só depois das validações do Senior.
- **Senior (eu):** validar o relatório de integração (contagens 305 → 492, SHAs reescritos,
  compilação por commit) antes de seguir para B-28.2.
