# Validação W-h4v: amostrador de infraestrutura e leitura do critério h4 (2026-11-05)

> Senior, 2026-11-05. Branch `feature/h4-amostrador-infra`, ponta `f15bcfc` sobre `19d45b9`. Especificação:
> `docs/prompt-worker-h4-amostrador-2026-11-04.md` e `docs/pre-registro-rebaseline-complemento-3-2026-11-04.md` (C3),
> na branch `docs/plano-implementacao-controlador`, e a DT-018. Relatório do Worker:
> `docs/relatorio-h4-amostrador-2026-11-04.md`, com D1 a D10 aceitos pelo coordenador.
>
> **Validação feita só por leitura.** A VM rodava o ensaio do F1 com o host em H0 estrito, inclusive de disco. Não rodei
> suíte, Maven nem `git archive`. Os únicos cálculos foram SHA-256 de *blobs* lidos com `git show` e da lista montada em
> memória. O que ficou para depois do ensaio está na seção 6.

## Veredito

**APROVADO COM RESSALVAS.** O código faz o que o C3 pede. As ressalvas R1 e R2 são da especificação (minhas) e pedem
decisão do Gestor antes do piloto. As outras são de procedimento ou ficam para uma v2. Nenhuma pede mudança nos três
arquivos que vão para a VM.

**O `tar` pode ir à VM antes do piloto**, depois do fim do ensaio (cópia e `scp` contam como atividade de host no H0),
com o SHA-256 `45d1d589…` reproduzido no host (seção 6, item 1), com a autorização do Gestor e seguido do *smoke* na VM
(seção 6, item 4), que é condição para o piloto. Os três arquivos nunca rodaram em Linux com systemd de verdade, só com
*shims* no Git Bash.

## 1. Bundle congelado e hashes

| Conferência | Resultado |
|---|---|
| `git diff --name-status 19d45b9 f15bcfc` | só `A` (arquivos novos) e `M docs/testes.md` |
| `git diff 19d45b9 f15bcfc -- scripts/run-rebaseline.sh analysis/rebaseline.py scripts/rebaseline_matriz.py scripts/inject-*.sh runs/rebaseline/matriz.tsv workload/` | vazio |
| SHA-256 dos 6 arquivos versionados da lista em `f15bcfc` (`git show \| sha256sum`) | iguais à tabela 6 do `relatorio-f1-silencioso-2026-10-27.md` (`3005f49a…`, `8654900c…`, `5b4d5719…`, `7e68962b…`, `3531916a…`, `d95305fe…`) |
| Lista de 7 linhas no formato `sha256sum` (com o jar `5eced136…`, fora do git e fora do diff) | `003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4` |
| SHA-256 dos 3 arquivos da VM em `83434b7`, `aec1812` e `f15bcfc` | iguais nos três *commits* e iguais aos do relatório |
| Lista dos 3 arquivos | `e9c4481989f147fc683d5923aef76213826ac2a69e15f5990336e560a57664a6` (igual) |
| Modos em `aec1812` | `.sh` 100755, `h4.py` 100644 |
| `tar` `45d1d589…` | **não reproduzido** (exige `git archive`); pendente, seção 6 |

Os três `test:` depois do `feat:` não tocam em nenhum dos três arquivos, como o Worker declara.

## 2. Laço do amostrador (`scripts/amostrador-infra.sh`)

- **Sem processo por amostra.** O trecho `#<laco>`…`#</laco>` (`ler_psi`, `ler_disco`, `registrar_erro`, `amostrar`,
  `fim_laco`, `laco_principal`) só tem `read -r`, `[[ ]]`, aritmética, `printf` e `printf -v`, com `>>` no arquivo. Não
  há `$( )`, crase, *pipe* nem `sleep`. O único processo externo do `_laco` é o `sha256sum` do cabeçalho, uma vez. O
  teste A1 com `PATH=""` mais a busca estática no trecho cobrem os dois lados.
- **Espera** por `read -t` num descritor aberto uma vez (`exec {fd}<> <(:)`), com teste de 0,01 s logo depois. Se ele
  não bloquear, o laço usa o FIFO `.relogio` no diretório da coleta (D7). No Linux, `<>` sobre o *pipe* do `<(:)` mantém
  as duas pontas abertas e o `read` só sai por tempo ou sinal, então o FIFO não deve ser criado. O *smoke* da seção 6
  confere isso.
- **Cadência.** Prazo absoluto `t0 + k·1 s`. Atrasou, `k` vai para o próximo prazo futuro, sem rajada. É o que o A3
  mostra (2,63 s e depois 0,36 s de realinhamento).
- **Destino.** `/run/tccdiag/<id>/amostras.txt`, criado por `sudo -n install -d -m 0755`. As duas units são de sistema
  (`sudo -n systemd-run`, sem `--user`, `--scope` ou `nohup`), com `StandardOutput=null` e `/usr/bin/bash` com o caminho
  absoluto do *script*. O arquivo sai 0644 (`UMask` padrão 0022), legível pelo vigia como usuário.
- **Formato v1** e linhas `# erro` e `# fim` como no prompt. O cabeçalho por *builtins* (`printf '%(…)T'`,
  `$HOSTNAME`) dá o mesmo texto do `date -Is` (`+00:00`). Aceito (D7).
- **Espelho.** `dd` com `skip_bytes,count_bytes` a partir do *offset* em `/run/tccdiag/<id>/espelho.offset`, em segundo
  plano, sem *lock* e sem nada compartilhado com o laço. Se o disco travar, só o `dd` espera. No TERM, faz a última cópia
  ou sai se a cópia em curso estiver bloqueada. O `dd` órfão é levado pelo `KillMode` da unit.
- **`parar`.** Para as duas units, espera até 10 s, faz a cópia final com `install -m 0644 -o <usuário>`, compara os
  SHA-256 e grava o `.sha256`. Não apaga o `/run/tccdiag/<id>`.

## 3. Regras h4a, h4b e h4c (`analysis/h4.py`) contra o C3 §3

| Regra | C3 | Código | Conferido |
|---|---|---|---|
| Janela de 10 s | `j` = amostra mais antiga com `t_i − t_j ≤ 10 s`; valor = Δ`full_total` de io | `Serie.__init__`, ponteiro `j` avança enquanto `t_i − t_j > JANELA_US` | sim |
| Janelas que entram | amostra final dentro de `[âncora + 44, min(âncora + 300, fim_utc)]` | `bisect_left(ini)`…`bisect_right(fim)` | sim |
| h4a | alguma janela ≥ 1,5 s | `pior >= LIMIAR_H4A_US` | sim, fronteira 1,5 s inclusiva |
| h4b | intervalo ≥ 3,0 s ou salto ≥ 1 s | intervalos que terminam em `a`..`b`, inclusive os que atravessam as bordas (D8); salto = \|Δparede − Δuptime\| | sim |
| h4c | falta de amostra por **mais** de 3 s no início ou no fim | `t[a] − ini > 3 s` ou `fim − t[b−1] > 3 s`, ou sem amostras | sim (3,0 s exato é completa, D8) |
| Execução sem âncora, em curso, abortada | `sem-ancora`; fora; fora | `sem-ancora`, `sem-janela` (D8), fora | sim |

A aritmética é toda em microssegundos inteiros, e os testes de fronteira (1,499999/1,5; 2,999/3,0; 0,999/1,0;
43,9/44,0/300,1) cobrem as bordas. A seleção da fase por `campanha` + `noite` da `<SAIDA>/matriz.tsv` (D1) bate com a
coluna 2 da matriz (`piloto` 12, `campanha` 120) e com o `E_FASE` do *runner* (`run-rebaseline.sh:709`), que na fase
de reposições lê `reposicao` do `reposicoes.tsv`. Os campos usados existem no `execucao.json` do *runner*:
`lancamento_ms`, `ancora_ms`, `fim_utc` (formato `iso_ms`, que o `iso_para_us` aceita), `falha` (`F1` literal) e
`falha_efetiva.f1_reiniciado_desde_ancora_s` (número ou `null`, `run-rebaseline.sh:686`).

## 4. Parada C3-1, segmento F1 C3-2 e teto C3-3

- **C3-1.** `avaliar_parada` recebe os vereditos ordenados por `lancamento_ms` (`_chave_lancamento`). A regra 1 olha
  toda janela de 12 execuções seguidas (≥ 5 h4) e toda sequência de 4 h4. A regra 2 dispara com qualquer `h4c`. A regra
  3 é do vigia, que checa `systemctl is-active` a cada ciclo e grava `PARADA-H4` (D6). `sem-ancora` e `sem-janela` não
  são h4 e quebram a sequência. O vigia não tem `kill`, `pkill`, `killall` nem `systemctl stop`.
- **C3-2.** Só nas execuções com `falha == "F1"`. Segmento `[t_reinicio − 2 s, t_reinicio + 60 s]`, fechado, com
  `t_reinicio = âncora + f1_reiniciado_desde_ancora_s`, ou `+ 109 s` se nulo. As janelas com amostra final no segmento
  saem do h4a e vão para `pior_janela_seg_f1_s`. h4b e h4c valem na janela inteira. Conforme.
- **C3-3.** `repor` recusa com exit 3 se as já feitas mais as novas passarem de 30 (tudo ou nada, D4). Semente
  `S0 + 100·i`, próximo i livre pela mesma conta do *runner* (`run-rebaseline.sh:441-449`), `posicao` a partir do
  número de linhas, como o *runner*. A condição de 3,5 episódios por hora não é automática: o JSON da fase traz
  `episodios.n`, `horas_observadas` e `por_hora`, e a decisão é do Senior e do Gestor, como o C3 §6 prevê.
- As constantes do bloco 3.1 estão no código, sem opção de linha de comando, e um teste confere `S0`, `PASSO_SEMENTE`,
  120 e 1000 contra o *runner* e o `rebaseline.py` lidos como texto.

## 5. Desvios D1 a D10

Todos conferidos no código, todos coerentes com o C3, nenhum muda limiar. D1 é necessário (não existe `fase = noite1` no
`execucao.json`). D3 protege a ordem do C3 §5: sem ele, um `h4.py repor` antes do *runner* faria o *runner* pular a
recusa da célula. D2, D4, D5, D6, D9 e D10 são conservadores ou só de teste. D7 já foi comentado na seção 2. Sobre o D8,
ver R3. Não achei desvio sem declaração.

## Ressalvas

- **R1 (especificação, pede decisão do Gestor antes do piloto).** O C3 §6 conta episódios "no arquivo inteiro", e o
  `h4.py` faz isso literalmente, inclusive dentro dos segmentos do F1, que o C3 §4.2 tira do h4a justamente porque o
  `podman start` pode gerar io `full` ≥ 1,5 s. O piloto tem 4 execuções F1 em ~1,4 h. Se cada `podman start` gerar um
  episódio, isso sozinho dá ~2,9 episódios/h, perto do limite de 3,5/h da noite 1. Proposta: registrar antes do piloto
  que, para a condição da noite 1, não entram os episódios formados só por janelas h4a com amostra final dentro de um
  segmento F1 (sem intervalo ≥ 3 s). A conta sai do JSON (`episodios.lista` com os instantes) cruzado com o TSV
  (`seg_f1_ini_utc`, `seg_f1_fim_utc`), sem mudar o código. As duas contagens são reportadas.
- **R2 (especificação, pede decisão do Gestor).** Não há subcomando para o ensaio, que não é fase do *runner*. Para
  contar os episódios de uma coleta `ensaio-<UTC>`, o caminho é
  `h4.py fase --amostras <arquivo> --saida <diretório novo e vazio, fora de runs/rebaseline> --fase piloto`, que dá 0
  execuções e o bloco `episodios`. Além disso, o `tar` ainda não está na VM, então o ensaio do F1 que roda agora não tem
  este amostrador. Se não houver outra coleta de PSI no ensaio, a base do C3 §6 ("ensaio e piloto juntos") fica só com
  o piloto, e a leitura do C3 §4.2 sobre o io do `podman start` fica sem dado próprio. O Gestor decide se aceita a base
  menor ou se pede um bloco curto de F1 com o amostrador antes do piloto.
- **R3 (procedimento).** Pelo D8, arquivo ilegível na leitura final dá exit 4, e não veredito h4c. No parecer de fase,
  exit 4 na leitura final (com a cópia final e o espelho) vale como h4c de toda execução não coberta por outro arquivo
  legível, e aciona a regra 2.
- **R4 (código, menor, para uma v2).** Se o relógio de parede voltar X s, o `laco_principal` dorme X + 1 s, porque o
  prazo é calculado pela parede e não há teto para a espera. Isso não esconde nada: o salto sai como h4b, e um X grande
  vira falta de cobertura (h4c). Corrigir agora mudaria o *hash* sem ganho real. Fica para uma v2, limitando a espera a
  1 s.
- **R5 (covariável).** A cada 300 s, o vigia grava `<SAIDA>/h4/h4-<fase>.tsv` e `.json` (alguns kB) no disco da VM, que
  é o disco medido. O peso é desprezível e o `rebaseline.py` não varre diretórios fora de `exec-*`, mas isso entra nas
  covariáveis da fase.
- **R6 (nota).** Um intervalo entre duas vidas do amostrador entra no h4b da execução, mas não na contagem de
  episódios. É coerente, porque a queda do amostrador já é parada pela regra 3.

## 6. Depois do ensaio

Em ordem, com o host liberado (H1 para o item 2, H2 para os demais):

1. `git archive --format=tar aec1812 scripts/amostrador-infra.sh scripts/vigia-h4.sh analysis/h4.py | sha256sum`
   tem de dar `45d1d5896f9d657dee5a88f03c53b40a153c6b8894f7e7a889ddec981e8af0fd`. Gravar com `-o
   h4-amostrador-aec1812.tar` e conferir o arquivo.
2. `python -I -m unittest discover -s analysis -p "test_h4.py"`: 48 OK.
3. `bash scripts/amostrador-infra.test.sh` (83/0), `bash scripts/vigia-h4.test.sh` (21/0) e
   `bash scripts/h4-reposicoes.test.sh` (29/0). O `run-rebaseline.test.sh` (21 min) é dispensável, porque o *runner* não
   mudou (diff vazio na seção 1).
4. **Smoke na VM**, com autorização do Gestor, antes do piloto. Conferir o SHA-256 do `tar`, extrair e rodar
   `sha256sum -c` com a lista. Depois, `iniciar --id diag-<UTC>`, 3 a 5 min, `estado`, um ciclo do vigia sobre uma
   `<SAIDA>` vazia e `parar`. Conferir: units de sistema ativas; nenhum `.relogio` em `/run/tccdiag/<id>/`;
   `versao_script_sha256` do cabeçalho igual a `e4cc5c79…`; intervalos de ~1 s; `# fim` com `amostras` coerente;
   `parar` com exit 0 e SHA-256 da cópia final igual; espelho com o mesmo conteúdo do original até o último ciclo;
   `h4.py fase` sobre a cópia final com exit 0.
