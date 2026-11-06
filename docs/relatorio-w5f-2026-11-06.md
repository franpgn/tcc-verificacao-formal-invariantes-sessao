# Relatório do W-5f: duas guardas do fatorial (2026-11-06)

Branch `feature/w5e-fatorial` (worktree `wt-w5e`), sobre a V-W5e (`0583455`, aprovado com ressalvas, seção 3). Só
Python, sem *push* e sem integração. Desenho em `docs/implementacao.md` (TD-021, itens 8 e 9).

## O que mudou

1. **Sem fusão registrada conta como varredura com erro** (DT-027, 5.3). Na célula fatorial, uma execução com
   varredura, sem `varredura-rep-NNN.erro.txt` e com `t_fusao_ns` ausente ou nulo tem a varredura tratada como com
   erro (`desfechos_execucao(..., fusao_obrigatoria=True)`, `varredura_erro_origem = "sem_fusao"`). O tratamento
   simétrico do bloco e a perda não avaliável seguem como antes. `varredura_com_erro` ganhou `sem_fusao` por bloco e
   `sem_fusao_por_braco`. Não depende do orquestrador.
   - **Escopo:** só o fatorial. As células de 2 braços (sem falha, F3, F1, F4) não têm fusão a esperar, e lá
     `t_fusao_ns` nulo é o normal; a regra também quebraria o golden, cujos `execucao.json` não têm o campo.
   - Sem varredura não há o que converter.
2. **Mesma semente nos braços do bloco** (`_conferir_sementes`, `seed` do `execucao.json`). No fatorial ela é
   obrigatória nos quatro braços; em 2 braços, só é conferida quando os dois a têm. Divergente ou ausente no fatorial:
   exit 2.

## Commits

| Commit | O quê |
|---|---|
| `e8adfbd` | `test:` 11 testes (7 vermelhos; 4 prendem o que não muda: sem varredura, 2 braços, mesma semente, sem `seed` em 2 braços). O gerador do teste passa a gravar `seed`, como o orquestrador |
| `9065994` | `feat:` as duas guardas |
| (último) | `docs:` TD-021 (itens 8 e 9), `testes.md` e este relatório |

## Testes

`python -E -P -m unittest discover -s analysis`: **528 OK, 1 *skip*** (517 + 11). Os goldens da base, sem e com
instrumentos, continuam iguais. A conversão por falta de fusão dá os mesmos contrastes de V e de `:sem_forma2`, e as
mesmas contagens, que o `.erro.txt` no mesmo braço.
