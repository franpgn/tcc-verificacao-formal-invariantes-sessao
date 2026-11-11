# Prompt do Worker: amostrador de infraestrutura e leitura do critério h4 (2026-11-04)

> Senior, 2026-11-05 ~02:00Z. Implementa o complemento 3 do pré-registro do *rebaseline*
> (`docs/pre-registro-rebaseline-complemento-3-2026-11-04.md`, doravante C3) e a DT-018. **Leia o C3 inteiro antes de
> começar.** Em conflito entre este prompt e o C3, vale o C3, e você para e pergunta.

## 0. Regras

- Worktree nova `C:\Users\Francesco\Development\tcc\wt-h4`, branch **`feature/h4-amostrador-infra`**, criada a partir
  de `feature/controlador-integracao` (hoje `19d45b9`). Commits `feat:`/`test:`/`docs:` terminando com
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. **Sem push.** Sem *force-push*, sem *rebase* de nada que
  não seja seu.
- **Não tocar** em `scripts/run-rebaseline.sh`, `analysis/rebaseline.py`, `scripts/rebaseline_matriz.py`,
  `scripts/inject-*.sh`, `runs/rebaseline/matriz.tsv`, `workload/` nem em nenhum arquivo da lista `003814e9…`. Um
  `git diff 19d45b9 -- <esses caminhos>` vazio é critério de aceite.
- **Nada na VM.** Tudo roda no host, com *shims* e arquivos sintéticos. A VM só recebe o resultado por `tar` conferido
  por *hash*, depois da validação do Senior e da autorização do Gestor.
- Antes de qualquer suíte, leia `C:\Users\Francesco\Development\tcc\HOST-PERFIL.txt`. Em H0, não rode nada (espere). Em
  H1, rode só a suíte Python. Em H2, tudo.
- Só biblioteca padrão no Python (3.12 no host, 3.12 na VM). Tudo tem de rodar com `python -I`. Bash ≥ 5.1 na VM
  (Ubuntu 24.04). No Git Bash do host, o que depender de `/proc` e do `systemd` é testado por *shims* e variáveis de
  caminho.

## 1. `scripts/amostrador-infra.sh`

### 1.1 Interface

```
amostrador-infra.sh iniciar --id <fase>-<UTC> [--disco sda] [--espelho-dir <dir>]   # como usuario, usa sudo
amostrador-infra.sh parar   --id <fase>-<UTC>                                         # para as duas units e faz a copia final
amostrador-infra.sh estado  [--id <fase>-<UTC>]                                       # so leitura
amostrador-infra.sh _laco   --id ... --disco ...        # corpo da unit principal (nao chamar a mao)
amostrador-infra.sh _espelho --id ... --espelho-dir ... # corpo da unit do espelho (nao chamar a mao)
```

`--id` tem de casar com `^(piloto|noite1|noite2|reposicoes|ensaio|diag)-[0-9]{8}T[0-9]{6}Z$`. O `--espelho-dir`
padrão é `~/rebaseline-infra/<id>` do usuário que chamou.

Exits: 0 OK; 2 uso; 3 pré-condição (unit já ativa, `/proc/pressure/io` ausente, disco ausente em `/proc/diskstats`,
`/run` com menos de 64 MiB livres, bash sem `EPOCHREALTIME`, `sudo -n` negado); 4 falha ao iniciar a unit ou ao
copiar ou conferir.

### 1.2 `iniciar`

1. Pré-condições acima, só leitura.
2. `sudo -n install -d -m 0755 /run/tccdiag/<id>`.
3. Unit principal, de sistema e como root:
   `sudo -n systemd-run --unit=tcc-amostrador-<id> --description="tcc amostrador infra <id>" --property=Type=simple
   --property=StandardOutput=null --property=StandardError=journal --property=Restart=no /usr/bin/bash <caminho
   absoluto>/amostrador-infra.sh _laco --id <id> --disco <disco>`.
   **Não** use `--user`, `--scope` nem `nohup`. A unit é do sistema para não depender da sessão do usuário (logind).
4. Unit do espelho, do mesmo jeito: `tcc-amostrador-espelho-<id>`, `_espelho`.
5. Espera até 3 s pela primeira linha de dados em `/run/tccdiag/<id>/amostras.txt`. Imprime as duas units, o caminho e
   a primeira linha. Sem linha em 3 s, sai com 4.

### 1.3 `_laco` (o amostrador)

- **Nenhum processo novo por amostra.** O laço usa só *builtins* do bash: `read -r` de `/proc/pressure/io`,
  `/proc/pressure/cpu`, `/proc/pressure/memory`, `/proc/uptime` e `/proc/diskstats` (procurando a linha do disco),
  `printf` com `>>` no arquivo, e `$EPOCHREALTIME`. Para dormir, use `read -t <fração> -u <fd>` sobre um descritor
  aberto uma vez no início (por exemplo `exec {fd}<> <(:)`), e não `sleep`.
- **Cadência.** Prazo absoluto: a amostra k sai em `t0 + k·1 s`. Se uma volta atrasar, o laço não tenta recuperar:
  pula para o próximo prazo futuro e grava o instante real.
- **Formato** de `/run/tccdiag/<id>/amostras.txt`, versão 1. A primeira linha é um cabeçalho, e depois vem uma linha por
  amostra, separada por espaços:

  ```
  # tcc-amostrador-infra v1 id=<id> inicio=<date -Is> boot_id=<uuid> host=<hostname> disco=<disco> periodo_s=1 pid=<pid> versao_script_sha256=<sha256 do proprio arquivo>
  <t_real> <uptime> io <some_total> <full_total> cpu <some_total> <full_total> mem <some_total> <full_total> <disco> <campos 4..20 de /proc/diskstats>
  ```

  `t_real` é o `$EPOCHREALTIME` (segundos com 6 casas). `uptime` é o primeiro campo de `/proc/uptime`. Os `*_total` são
  os `total=` em microssegundos. Se `cpu` não tiver linha `full` (kernel antigo), grave `-`. Disco ausente numa volta:
  `<disco> -`. O cabeçalho é a única linha montada com processos externos (`date`, `sha256sum`, `hostname`, leitura do
  `boot_id`), e só no início.
- **Sem saída** na saída padrão nem no journal por amostra. Erros de leitura de `/proc` contam e entram numa linha
  `# erro t_real=<...> <motivo>`.
- **SIGTERM**: grava `# fim t_real=<...> amostras=<n> atrasos_ge_1_5s=<n>` e sai com 0.

### 1.4 `_espelho`

A cada 300 s, acrescenta a `<espelho-dir>/amostras.txt` os bytes novos de `/run/tccdiag/<id>/amostras.txt` desde o
último *offset* copiado (guardado em `/run/tccdiag/<id>/espelho.offset`), e faz `chown` para o usuário dono do
`<espelho-dir>`. Pode usar processos externos (`tail -c`, `dd`). Se a escrita bloquear, só o espelho espera: o espelho
**não** pode usar *lock* nem nada que o amostrador também use. No SIGTERM, faz uma última cópia e sai.

### 1.5 `parar`

1. `sudo -n systemctl stop tcc-amostrador-<id>` e depois o do espelho. Espera até 10 s pelas duas inativas.
2. Cópia final: `sudo -n install -m 0644 -o <usuario> /run/tccdiag/<id>/amostras.txt <espelho-dir>/amostras-final.txt`,
   `sha256sum` das duas e comparação. Iguais: grava `<espelho-dir>/amostras-final.txt.sha256`. Diferentes: exit 4.
3. Imprime: amostras, primeira e última, maior intervalo entre amostras, contagem de linhas `# erro`. Não apaga
   `/run/tccdiag/<id>`. Ele some no próximo *boot*.

### 1.6 `estado`

Só leitura: units `tcc-amostrador-*` ativas, tamanho e última linha de cada `amostras.txt` em `/run/tccdiag/`,
`df -B1 /run`.

## 2. `scripts/vigia-h4.sh`

`vigia-h4.sh --id <id> --saida <SAIDA do runner> --fase <fase> [--periodo 300]`, rodando numa janela `tmux` à parte.
A cada período, roda `python3 -I <repo>/analysis/h4.py fase --amostras /run/tccdiag/<id>/amostras.txt --saida <SAIDA>
--fase <fase>`. Com exit 30: grava `~/rebaseline-infra/<id>/PARADA-H4` com a saída, imprime um aviso grande e **continua
vigiando** (não para o *runner*, não manda sinal a ninguém). Com exit 4 por 2 ciclos seguidos (arquivo ilegível),
grava `ALERTA-H4C` e avisa. Também avisa se a unit `tcc-amostrador-<id>` estiver inativa (parada h4, regra 3 do C3
§4.3).

## 3. `analysis/h4.py`

### 3.1 Constantes (pré-registro: no código, sem opção de linha de comando para mudá-las)

```python
VERSAO = "h4/1"
JANELA_S = 10.0
LIMIAR_H4A_S = 1.5
LIMIAR_H4B_S = 3.0
LIMIAR_SALTO_S = 1.0
MEDICAO_INI_S = 44.0          # PR §2 / DT-011: ancora + 44 s
MEDICAO_FIM_S = 300.0         # ancora + 300 s, ou fim_utc se antes
SEG_F1_ANTES_S = 2.0
SEG_F1_DEPOIS_S = 60.0
F1_REINICIO_PADRAO_S = 109.0  # se falha_efetiva.f1_reiniciado_desde_ancora_s for null
COBERTURA_FOLGA_S = 3.0       # h4c: falta de amostra no inicio ou no fim da janela h4
PARADA_JANELA_EXEC = 12
PARADA_MIN_H4 = 5
PARADA_SEGUIDAS = 4
TETO_REPOSICOES_H4 = 30
S0 = 50000; PASSO_SEMENTE = 100; I_REPOSICAO_INICIAL = 120; I_PILOTO = 1000
```

A última linha tem de bater com `run-rebaseline.sh:49` (`S0`, `PASSO_SEMENTE`, `I_REPOSICAO_INICIAL`, `I_PILOTO`) e com `rebaseline.py` (`S0` e `PASSO_SEMENTE` nas linhas 35-36; 120 e 1000 aparecem como literais em `reposicoes_pendentes`, linha 626). Um teste lê os dois arquivos como texto e confere.

### 3.2 `fase`

`h4.py fase --amostras <arquivo> [--amostras <arquivo2> ...] --saida <SAIDA> --fase <piloto|noite1|noite2|reposicoes>`

- Lê o cabeçalho v1 e recusa (exit 4) arquivo sem cabeçalho, de versão desconhecida ou com dois `boot_id` diferentes
  entre os arquivos. Ignora linhas `#` que não sejam o cabeçalho. Linha malformada é exit 4, com o número da linha.
- Para cada `exec-*/execucao.json` com `fase` igual à pedida (fora os `.abortada-*`): janela h4 = `[ancora_ms/1000 +
  44, min(ancora_ms/1000 + 300, fim_utc)]`. Sem `ancora_ms`: veredito `sem-ancora` (não é h4, o K6 já recusa).
  Execução sem `fim_utc` (em curso) fica de fora.
- Janelas de 10 s, intervalos e saltos exatamente como no C3 §3.2. Segmento do F1 como no C3 §4.2.
- Veredito por execução: `ok`, ou a lista ordenada dos códigos (`h4a`, `h4b`, `h4c`), por exemplo `h4a+h4b`.
- **Parada** (C3 §4.3, regras 1 e 2), avaliada na ordem de `lancamento_ms`. A regra 3 (unit inativa) é do vigia.
- **Saídas.** `<SAIDA>/h4/h4-<fase>.tsv` com uma linha por execução e as colunas `i, celula, falha, lancamento_utc,
  ancora_utc, janela_ini_utc, janela_fim_utc, n_amostras, pior_janela_s, pior_janela_fim_utc, pior_janela_seg_f1_s,
  seg_f1_ini_utc, seg_f1_fim_utc, maior_intervalo_s, maior_intervalo_utc, maior_salto_s, cobertura, veredito`.
  `<SAIDA>/h4/h4-<fase>.json` com o mesmo conteúdo, mais `versao`, as constantes, o SHA-256 de cada arquivo de amostras
  lido, a contagem de episódios da fase (C3 §6: trechos contínuos com janela ≥ 1,5 s ou intervalo ≥ 3 s, sobre
  **todo** o arquivo e não só sobre as janelas h4), as horas observadas e os episódios por hora.
- Exit 0 (segue), 30 (parada h4), 2 (uso), 4 (arquivo ilegível).
- Saída padrão: uma linha por execução h4 e um resumo com `h4: <k> de <n> execucoes; episodios/h=<x>; parada=<sim|nao>`.

### 3.3 `repor`

`h4.py repor --saida <SAIDA> --matriz <matriz.tsv> [--dry-run]`

- Usa os `h4-*.tsv` das fases `noite1`, `noite2` e `reposicoes` (o piloto nunca). Junta com os `checagem.json`
  (`veredito`) dos mesmos `exec-*`.
- Para cada execução h4 com `veredito == ACEITA` ainda sem reposição h4 registrada, acrescenta uma linha a
  `<SAIDA>/reposicoes/reposicoes.tsv`. Se o arquivo não existir, cria com o cabeçalho do *runner*
  (`i fase noite rodada posicao celula cenario modo falha seed`, separado por tabulação). Formato igual ao que o
  *runner* escreve: `fase=reposicao`, `noite=reposicoes`, `rodada=-`, posição contínua, `seed = S0 + 100·i`.
- **Próximo i livre:** 1 + o maior entre os i das linhas do TSV e os i dos diretórios `exec-*` com 120 ≤ i < 1000
  (a mesma conta do *runner*, `run-rebaseline.sh:441-449`).
- Registro de quais execuções já foram repostas: `<SAIDA>/h4/reposicoes-h4.tsv` (`i_h4, celula, i_reposicao,
  carimbo`). É ele que torna o comando idempotente: rodar duas vezes não duplica nada.
- Recusa (exit 3) se o total de reposições h4 passar de `TETO_REPOSICOES_H4`. Imprime as linhas e o SHA-256 do TSV.
- `--dry-run` só imprime.

### 3.4 `visao`

`h4.py visao --saida <SAIDA> --destino <dir novo> [--excluir <arquivo com um i por linha>]`

- `<destino>` não pode existir. Cria o diretório e copia os arquivos de topo de `<SAIDA>`, com `reposicoes/`. Para
  cada `exec-*` que não seja h4 em nenhum `h4-*.tsv` nem esteja no `--excluir`, cria o diretório e um *hard link*
  (`os.link`) de cada arquivo. Se o *link* falhar (outro volume), copia e confere o SHA-256.
- Grava `<destino>/VISAO-H4.txt` com a lista dos `exec-*` excluídos e o motivo, o SHA-256 de cada `h4-*.tsv` usado e a
  versão.
- Nunca escreve em `<SAIDA>`.

## 4. Testes (todos no host)

### 4.1 `analysis/test_h4.py` (`python -I -m unittest discover -s analysis -p "test_h4.py"`)

1. **Janela de 10 s:** série sintética com `full_total` conhecido, conferindo o valor exato, a borda `≤ 10 s` e uma
   janela que atravessa um buraco.
2. **Fronteiras dos limiares:** 1,499999 s (ok) e 1,5 s (h4a); intervalo de 2,999 s (ok) e 3,0 s (h4b); salto de 0,999
   e de 1,0 s.
3. **Janela h4:** episódio a `âncora + 43,9 s` (fora), a `+ 44,0 s` (dentro) e a `+ 300,1 s` (fora); `fim_utc` antes
   de +300 s.
4. **Segmento do F1:** episódio dentro do segmento (ok, com `pior_janela_seg_f1_s` preenchido), na borda e fora;
   `f1_reiniciado_desde_ancora_s` nulo, usando 109 s; h4b dentro do segmento continua h4.
5. **h4c:** arquivo que começa 3,1 s depois do início da janela; que termina antes do fim; ausente; malformado; dois
   `boot_id`.
6. **Parada:** sequências de vereditos que disparam e que não disparam as regras 5-em-12 e 4-seguidas, na ordem de
   `lancamento_ms`, inclusive com a ordem dos diretórios diferente da ordem de lançamento.
7. **Recalibração reproduzida:** sobre cópias de três arquivos reais de `wt-gate-v4/runs/vm-s7-2026-11-04/`
   (`vm-20261104T232939Z/coleta-paralela-coordenador/psi.txt`, `vm-20261105T012458Z/coleta-paralela-coordenador/psi.txt`
   e `diag-io-2026-11-05/vm-psi-diskstats-depois-mitigacoes.txt`), convertidas para o formato v1 por um conversor que
   fica **só no teste** (atenção à ordem dos blocos separados por `|`: no arquivo da V-S7r é `cpu | io | memory`, no do S7t é `io | cpu` e no do diagnóstico é `io | diskstats`), as piores janelas têm de ser 9,32 s, 5,17 s e 1,73 s (±0,01), e os maiores intervalos 5,737 s,
   1,006 s e 1,014 s (±0,001). Copie os três para `analysis/testdata/h4/` com o SHA-256 de origem no nome de um
   `LEIAME`.
8. **`repor`:** próximo i livre com TSV e diretórios misturados; idempotência; execução h4 RECUSADA não é reposta pelo
   `h4.py`; teto de 30; semente `S0 + 100·i`.
9. **`visao`:** exclui h4 e `--excluir`, *links* criados, `<SAIDA>` intacta (SHA-256 da árvore antes e depois), recusa
   destino existente.
10. **Constantes cruzadas:** as da seção 3.1, conferidas contra `scripts/run-rebaseline.sh` e `analysis/rebaseline.py`
    lidos como texto.

### 4.2 `scripts/amostrador-infra.test.sh`

Com variáveis de caminho só para teste (`H4_PROC=<dir>` no lugar de `/proc`, `H4_RUN=<dir>` no lugar de `/run/tccdiag`),
aceitas **só** com `H4_MODO_TESTE=1` e uma marca `.h4-shim` no diretório, como o `RB_MODO_TESTE` do *runner*:

1. `_laco` com `PATH` vazio por 5 s grava ≥ 4 amostras. Isso prova que não há processo externo no laço.
2. Formato v1 linha a linha (regex), cabeçalho com `versao_script_sha256` igual ao do arquivo.
3. Cadência: um `H4_PROC` que bloqueia a leitura por 2,5 s numa volta (FIFO) gera um intervalo de ~2,5 s e depois volta
   a 1 s, sem rajada de recuperação.
4. SIGTERM grava a linha `# fim` e sai com 0.
5. `_espelho` com período de teste curto copia só o delta e é idempotente. Com o destino bloqueado (FIFO sem leitor), o
   `_laco` continua gravando.
6. `iniciar`/`parar`/`estado` com *shims* de `sudo`, `systemd-run` e `systemctl` que registram os argumentos.
   Conferir: unit de sistema (sem `--user`), `StandardOutput=null`, caminho absoluto, `install -d -m 0755`, cópia final
   com `sha256sum` igual; exits 2, 3 e 4.
7. `--id` inválido (exit 2).

### 4.3 `scripts/vigia-h4.test.sh`

Com um `h4.py` de *shim* que devolve 0, 30 e 4: grava `PARADA-H4` no 30, `ALERTA-H4C` depois de 2 ciclos com 4, avisa
com a unit inativa e nunca manda sinal a processo nenhum (o *shim* de `kill` falha o teste se for chamado).

### 4.4 Sequência de reposições com o *runner* congelado

Em `scripts/run-rebaseline.test.sh` **não** se mexe. Crie `scripts/h4-reposicoes.test.sh`, que usa os mesmos *shims*
(`RB_MODO_TESTE=1`, `RB_SHIM_DIR` com `.rb-shim`) e o `run-rebaseline.sh` real, sem cópia nem edição:

1. Campanha sintética com uma recusa (célula A) e duas execuções ACEITAS marcadas h4 (células A e B).
2. `run-rebaseline.sh --fase reposicoes` → repõe a recusa de A (i = 120).
3. `h4.py repor` → acrescenta A (i = 121) e B (i = 122).
4. `run-rebaseline.sh --fase reposicoes` de novo → **não** repete o i = 120, roda o 121 e o 122, e o `checar` aceita as
   sementes.
5. Conferir no `runner.log` e nos `exec-*` que nada foi duplicado, e que `rebaseline.py estado` não conta as h4 como
   recusa.

Se o passo 4 mostrar que o *runner* não se comporta assim, **pare e reporte** com o trecho do código, sem contornar.
O C3 §5 depende disso.

## 5. Documentação

- `docs/testes.md`: as três suítes novas, com as contagens.
- `docs/relatorio-h4-amostrador-<data>.md`: o que foi feito, decisões de implementação, contagens, desvios do prompt
  (cada um com motivo) e a lista SHA-256 dos 3 arquivos que vão para a VM (`scripts/amostrador-infra.sh`,
  `scripts/vigia-h4.sh`, `analysis/h4.py`). Inclua também o comando do `tar` (`git archive <ponta>
  scripts/amostrador-infra.sh scripts/vigia-h4.sh analysis/h4.py`) e o SHA-256 dele.
- Um bloco "Como usar na VM", em ordem: `iniciar` antes do `go` da fase, o vigia na janela `tmux` 2, `parar` depois do
  bloco de fim de fase do C1 §1.3, `h4.py fase` final, cópia para o host com `sha256sum -c`.

## 6. Critérios de aceite

1. As suítes dos itens 4.1 a 4.4 passam, com as contagens no relatório. A suíte `analysis` existente continua com
   187 OK (1 *skip*) e `run-rebaseline.test.sh` sem mudança de resultado.
2. `git diff 19d45b9 --stat` só mostra arquivos novos e `docs/testes.md`.
3. Os números da recalibração (teste 4.1.7) são reproduzidos.
4. O laço do amostrador não cria processo por amostra (teste 4.2.1).
5. Nenhum limiar pode ser mudado por opção de linha de comando.
6. O relatório lista os desvios. Sem desvio não declarado.

## 7. Fora do escopo

Rodar na VM. Mudar o *runner* ou o `rebaseline.py`. Ler latências ou qualquer desfecho do *workload* (o h4 olha só a
infraestrutura). Interromper o *runner* automaticamente. Integrar na `feature/controlador-integracao` (é o
coordenador, depois da validação do Senior).
