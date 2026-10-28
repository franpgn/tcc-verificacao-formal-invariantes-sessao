# Roteiro do S4 (REP=4R), 2026-10-28

Preparado pelo Worker. Não foi executado na VM. Referências: `decisoes-senior-2026-10-16.md` §5.1 e §5.5, `docs/validacao-v-s1-2026-10-27.md` §5.2 (REP=4R), `runs/vm-s3-2026-10-28/V-S3.md` (seção "O que vem no S4" e `04-*`).

## Decisões do Gestor (2026-10-28) aplicadas

1. O cluster é recriado com `runs/gate-b29/recriar.sh` logo antes do S4. Depois vêm o portão 5.1 e o S4. L-0 e L-0c não são refeitos.
2. **Desvio da §5.5, registrado:** a referência de hashes é o bundle v4c (`902654b`), e não o "bundle igual ao de S0". `inject-partition.sh` e `g-lib.sh` continuam iguais aos de S0. `g1.sh`, `g1b.sh`, `g56-lib.sh`, `recriar.sh` e `portao51.sh` são iguais ao v4c. A tabela fica embutida no `s4.sh` e o `s4.test.sh` (T-s4-0) a confere contra o repositório.
3. **Registro da R-3 da V-S3.** No S3, o invólucro do Gestor foi `echo "rc=${PIPESTATUS[0]} fim=$(date -Is)" | tee -a $D/l0c.log`, com `D=runs/vm-s3-2026-10-28`. Ele foi digitado na linha seguinte a `bash -x runs/gate-netdegrade/l0c.sh 2>&1 | tee $D/l0c.log`. Leitura do Worker, a validar pelo Senior: numa linha nova do shell interativo, `PIPESTATUS` ainda se refere ao último pipeline em primeiro plano, que é o `bash -x ... | tee` da linha anterior. Então o `rc=0` é o do `l0c.sh`, desde que nenhum `PROMPT_COMMAND` tenha rodado um pipeline entre as duas linhas (isso não foi conferido na VM). Os 56 s entre o fim do script e a linha `rc=0` são compatíveis com o tempo de digitar ou colar a segunda linha. No S4, o `s4.sh` lê o `PIPESTATUS[0]` na mesma linha do pipeline e grava em `rc.txt`.

## O que o `s4.sh` faz

| Passo | Ação | Arquivo em `vm-<UTC>/` | Se falhar |
|---|---|---|---|
| 0 | Pré-condições, só leitura: `TCC_DIR=$HOME/tcc-gate` (exporta se vazio, aborta se diferente), tmux, lock OCUPADO, `sudo -n`, `uname -r` = 6.8.0-147-generic, `boot_id` = `5d7119b8…10df` (boot de 2026-10-28 06:39 UTC; até a tentativa 1 era `b7df4b03…e65b`), HEAD, 0 arquivos versionados modificados, sha256 da tabela, ambiente sem `G1_*`/`REP`/`COLETA`/..., java e jar, `g1-rep4` inexistente, 0 timers, `go.log` e `podman ps` | `00-pre.log` | rc 1, nada tocado |
| 1 | `inspect`, `.State.Status` e `logs` dos 3 contêineres antigos, a última vista e o journal do boot anterior (`journalctl -b -1`). Uma falha de leitura vira AVISO no log. Depois pede `RECRIAR` | `01-pre-recriacao.log`, `pre-recriacao/` (com `journal-boot-anterior.txt`) | rc 1 |
| 2 | `recriar.sh` (down/up, ESTÁVEL). Não tem go próprio | `02-recriacao.log`, `recriacao/` | rc 2 |
| 2b | IDs, criação, `StartedAt`, vista e topology id do cluster novo | `03-pos-recriacao.log` | (registro) |
| 3 | Portão 5.1 (`portao51.sh`) com `.log` e `.csv`. A duração é ≥ 130 s e o fim fica ≥ up + 345 s, para que a regra de "0 mudanças de vista em 5 min" valha. A avaliação é automática | `04-portao51-pre.{log,csv,avaliacao}` | rc 3: **S4 não roda** |
| 4 | `g1b` (REP=4 COLETA=minima) na janela tmux `s4-g1b`, sem lock próprio. Em seguida `REP=4 G1_DUR=90 G1_LOG_SAUDE_S=2 G1_LOG_POS_S=30 g1.sh`. **O GO é digitado pelo Gestor no prompt do `g1.sh`** | `05-pre-g1.log`, `05-g1.log`, `05-g1b.log` | rc 4. Sem `event=start`, o g1b é encerrado. Com o injetor já iniciado, o dead-man cura em 180 s |
| 5 | Espera o g1b e registra marcadores, `t_merge.txt`, `go.log` e sha256 de `g1-rep4/`. Depois roda o portão 5.1 pós, com fim ≥ fim do g1 + 345 s | `06-pos-g1.log`, `go.log`, `g1-rep4.sha256`, `07-portao51-pos.*` | rc 5 se `t_merge_s` > 60 s (PARAR, não seguir para o S5). rc 6 se o portão pós falhar |

O console inteiro vai para `s4-console.log` e o rc de cada passo para `rc.txt`.

## Diferenças declaradas em relação à REP=3

- Idade do cluster no `start`: cerca de 6 a 7 min. Na REP=3 foi 121 s. A diferença vem do portão 5.1 completo, que exige 5 min sem mudança de vista e não existia em 16/10.
- Kernel 6.8.0-147, contra 6.8.0-124 nas REP=1..3 (V-S1, R-3). Esta é a primeira partição real no kernel novo.
- O GO é digitado pelo Gestor. Na REP=3 foi por `tmux send-keys` com autorização.

## Duração estimada

Cerca de 18 min do comando ao fim. O prompt do GO aparece por volta do minuto 8 (pré 1 a 2 min, recriação cerca de 1,5 min, portão pré até up + 345 s). A janela e o merge levam cerca de 3 min (96 s de injeção, cerca de 46 s de merge e mais 30 s de log), e o portão pós leva cerca de 6 min. O Gestor precisa ficar presente do GO até o fim do merge. Nada de TLC, `mvn` ou carga pesada no host, do início do portão pré até o fim do portão pós.

## Tentativa 1 abortada (2026-10-28)

- O Gestor rodou o `s4.sh` às 06:35:54 UTC. O passo 0 passou.
- Por volta das 06:36:24 UTC o host Windows caiu com BSOD 0x154, logo no início do passo 1. `pre-recriacao/inspect-isn1.json` ficou com 0 bytes e `01-pre-recriacao.log` ficou vazio.
- Não rodaram o prompt RECRIAR, o `recriar.sh`, o portão 5.1, o g1/g1b nem o GO. O `go.log` não mudou. Nada foi injetado e o cluster não foi recriado.
- A VM reiniciou às 06:39:35 UTC. O boot_id novo é `5d7119b8-3b50-435d-bd0d-cbdb6bd410df` (antes `b7df4b03-6652-4808-9617-11096693e65b`, o de S2 e S3). O kernel continua 6.8.0-147-generic. O IP da VM agora é 172.31.251.37.
- `sudo podman ps -a` mostra isn1, isn2 e isn3 em `Created`, criados em 2026-10-27 18:48:5x UTC. Não há timers tcc.
- A saída parcial ficou só na VM, em `~/tcc-gate/runs/vm-s4-2026-10-28/vm-20261028T063554Z/` (`00-pre.log`, `s4-console.log`, `rc.txt` vazio, `01-pre-recriacao.log` vazio, `pre-recriacao/inspect-isn1.json` vazio). O host não tem cópia.

Ajustes no `s4.sh` para a tentativa 2: `BOOT_ESPERADO` passa a ser o boot novo, de modo que o antigo aborta no passo 0. O passo 1 registra o `.State.Status` de cada contêiner e grava `pre-recriacao/journal-boot-anterior.txt`, que reúne `journalctl --list-boots`, `journalctl -b -1 -o short-iso-precise CONTAINER_NAME=isnN` para cada nó e as linhas de podman/conmon do boot anterior que citam isn1..3. Isso é necessário porque, com os contêineres em `Created`, o `podman logs` pode sair vazio ou trazer só o boot atual. Uma falha nessa leitura vira AVISO e não aborta. O prompt RECRIAR e o restante da sequência não mudaram, e os 7 scripts da tabela de hashes também não. O sha256 do `s4.sh` novo é `e9e46bf11a1ddecb8754efc656bd0c219beb8a94becc10da6959ee41e7869367`.

**Antes da tentativa 2, o Gestor deve fazer na VM** (por analogia à DT-014, sem apagar nada), depois de levar o `s4.sh` novo para a VM (o caminho depende da aprovação do push da branch):

```bash
cd ~/tcc-gate/runs/vm-s4-2026-10-28
mv vm-20261028T063554Z vm-20261028T063554Z.abortada-host-crash
sha256sum s4.sh   # esperado e9e46bf11a1ddecb8754efc656bd0c219beb8a94becc10da6959ee41e7869367
```

O `s4.sh` não lê saídas anteriores e cria um `vm-<UTC>` novo. A renomeação só serve para que a tentativa 1 não se confunda com a 2. Se o `journalctl -b -1` não tiver o boot anterior (journal não persistente), o passo 1 registra o AVISO e segue até o prompt RECRIAR.

## Teste

`bash runs/vm-s4-2026-10-28/s4.test.sh`: 216 PASS, 0 FAIL (Git Bash, ~3 min; eram 181 antes dos casos da tentativa 1). Ele não prova podman, tmux, flock, journalctl nem Infinispan reais.
