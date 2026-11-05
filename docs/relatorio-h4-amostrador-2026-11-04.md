# Relatório do W-h4: amostrador de infraestrutura e leitura do critério h4 (2026-11-04)

> Worker, 2026-11-05 (dia de trabalho 04/11). Prompt `docs/prompt-worker-h4-amostrador-2026-11-04.md` (Senior,
> branch `docs/plano-implementacao-controlador`, `fbdb050`), que implementa o complemento 3 do pré-registro do
> *rebaseline* (C3) e a DT-018. Pontos C3-1 a C3-4 aprovados pelo coordenador em nome do Gestor (2026-11-05).
> Desvios D1 a D10 aceitos pelo coordenador (2026-11-05) e registrados na seção 4.

## 1. Branch e commits

Worktree `C:\Users\Francesco\Development\tcc\wt-h4`, branch `feature/h4-amostrador-infra`, criada de
`feature/controlador-integracao` em `19d45b9`. Sem *push*.

| Commit | Tipo | Conteúdo |
|---|---|---|
| `123fbb6` | test | as quatro suítes e `analysis/testdata/h4/` (escritas antes do código) |
| `83434b7` | feat | `scripts/amostrador-infra.sh`, `scripts/vigia-h4.sh`, `analysis/h4.py` |
| `da96b8d` | test | `LEIAME.txt` do testdata lido como UTF-8 (tem "§") |
| `baa3034` | test | `amostrador-infra.test.sh`: `wait` fora de `$( )`, FIFO do A3 bloqueando uma vez só, asserção que não passa no vazio |
| `aec1812` | test | `h4-reposicoes.test.sh`: extração de `criar_shims` até o `}` certo |
| (este) | docs | este relatório e `docs/testes.md` |

Os três `test:` depois do `feat:` corrigem defeitos **das suítes**, e não do código: nenhum dos três arquivos que
vão para a VM mudou depois de `83434b7`. Detalhe na seção 6.

## 2. O que foi feito

- **`scripts/amostrador-infra.sh`.** `iniciar` confere as pré-condições, faz `install -d -m 0755 /run/tccdiag/<id>`
  e sobe duas units de sistema por `sudo -n systemd-run` (sem `--user`, `--scope` ou `nohup`), a principal (`_laco`)
  e a do espelho (`_espelho`). Depois espera até 3 s pela primeira amostra. O `_laco` usa só *builtins*: `read -r`
  de `/proc/uptime`, de `/proc/pressure/{io,cpu,memory}` e de `/proc/diskstats`, `printf >>` e `$EPOCHREALTIME`.
  Dorme com `read -t` num descritor aberto uma vez e segue prazo absoluto de `t0 + k·1 s`, sem recuperar atraso.
  Grava o formato v1 do prompt, com `# erro` e `# fim`. O `_espelho` copia o delta a cada 300 s com `dd`
  (`skip_bytes,count_bytes`), guarda o *offset* em `/run/tccdiag/<id>/espelho.offset` e não usa *lock*. O `parar`
  para as duas units, espera até 10 s, faz a cópia final com `install -m 0644 -o <usuário>`, compara os SHA-256 e
  grava `amostras-final.txt.sha256`. O `estado` é só leitura.
- **`scripts/vigia-h4.sh`.** Chama `python3 -I analysis/h4.py fase` a cada período. O exit 30 grava `PARADA-H4`.
  Dois exits 4 seguidos gravam `ALERTA-H4C`. A unit inativa (regra 3) grava `PARADA-H4`. Em todos os casos continua
  vigiando. Não tem `kill`, `pkill`, `killall` nem `systemctl stop`.
- **`analysis/h4.py`.** Só biblioteca padrão, roda com `-I`. Os limiares são constantes do bloco 3.1 do prompt, sem
  opção de linha de comando, e as contas usam microssegundos inteiros, sem erro de ponto flutuante nas fronteiras.
  - `fase`: janela de 10 s, intervalo e salto de relógio do C3 §3.2; h4a, h4b e h4c do C3 §3.3; segmento do F1 do
    C3 §4.2; parada pelas regras 1 e 2 do C3 §4.3, na ordem de `lancamento_ms`; episódios por hora do C3 §6.
    Grava `h4-<fase>.tsv` com as 18 colunas do prompt e o `.json`.
  - `repor`: próximo i livre pela conta do *runner*, semente `S0 + 100·i`, idempotente por `h4/reposicoes-h4.tsv`
    e teto de 30.
  - `visao`: *hard links* com cópia conferida por SHA-256 quando o *link* falha, `VISAO-H4.txt`, e nunca escreve em
    `<SAIDA>`.

## 3. Testes e contagens

Todos rodados no host (Git Bash, Python 3.12), em 2026-11-05, com a VM fora de bloco de medição.

| Suíte | Comando | Resultado | Tempo |
|---|---|---|---|
| 4.1 | `python -I -m unittest discover -s analysis -p "test_h4.py"` | **48 OK** | 2 s |
| `analysis` inteira | `python -m unittest discover -s analysis` | **235 OK, 1 *skip*** (= 187 existentes + 48 novos) | 92 s |
| 4.2 | `bash scripts/amostrador-infra.test.sh` | **83 PASS, 0 FAIL** | 63 s |
| 4.3 | `bash scripts/vigia-h4.test.sh` | **21 PASS, 0 FAIL** | 15 s |
| 4.4 | `bash scripts/h4-reposicoes.test.sh` | **29 PASS, 0 FAIL** | ~40 s |
| *runner* | `bash scripts/run-rebaseline.test.sh` | **130 PASS, 0 FAIL** (igual ao último registro, `relatorio-f1-silencioso-2026-10-27.md`) | 21 min 26 s |

- A suíte `analysis` antiga continua com 187 OK. O padrão do `discover` também pega `test_h4.py`, daí os 235.
  Com `-I`, três módulos antigos não importam `numpy` (o `site-packages` do usuário fica fora), e por isso a suíte
  inteira roda sem `-I`, como no `docs/testes.md`.
- **Recalibração (4.1.7) reproduzida:**

  | Arquivo | Pior janela | Maior intervalo |
  |---|---|---|
  | V-S7r | 9,32 s | 5,737 s |
  | S7t | 5,17 s | 1,006 s |
  | diagnóstico 2 | 1,73 s | 1,014 s |

  As tolerâncias são ±0,01 s para a janela e ±0,001 s para o intervalo. O conversor fica só no teste e respeita a
  ordem dos blocos: `cpu|io|memory`, `io|cpu` e `io|diskstats`.
- **Sem processo por amostra (4.2.1):** o `_laco` com `PATH=""` por 5 s gravou ≥ 4 amostras sem erro. Uma
  conferência estática do trecho `#<laco>`…`#</laco>` mostra que não há `$( )` (que o `PATH` vazio não pegaria),
  crase, *pipe* nem `sleep`.
- **Cadência (4.2.3):** com a leitura bloqueada uma vez, os intervalos medidos foram 1,01, 1,00, **2,63**, 0,36,
  1,00, 1,00 e 1,00 s. O 0,36 s é o realinhamento ao próximo prazo, e não há rajada.
- **Sequência de reposições (4.4) com o *runner* real e congelado:** o *runner* repõe a recusa de A (i = 120); o
  `h4.py repor` acrescenta A (i = 121) e B (i = 122); o *runner* de novo pula o 120 ("já executada e checada;
  pulando"), roda o 121 e o 122 com as sementes 62100 e 62200 (K7 conferido com o `ler_matriz`, o `semente_e_rep`
  e as constantes do `rebaseline.py` real), não acrescenta linha ao TSV e não repete nada. O `rebaseline.py estado`
  real conta 1 recusa (`{'S1-sync-none': 1}`), sem as h4. Uma terceira rodada do *runner* não lança nenhum
  *workload*. **O C3 §5 vale sem mudar o *runner*.**

## 4. Desvios do prompt (aceitos pelo coordenador em 2026-11-05)

- **D1. Seleção da fase no `h4.py fase`.** O `execucao.json` grava `fase` com a coluna 2 da matriz que o *runner*
  lê: `piloto`, `campanha` ou `reposicao`. Não há execução com `fase = noite1`. Por isso:
  - `--fase piloto` pega `fase = piloto`;
  - `--fase noite1` e `--fase noite2` pegam `fase = campanha` com a noite do i tirada de `<SAIDA>/matriz.tsv`;
  - `--fase reposicoes` pega `fase = reposicao`.
- **D2.** O `iniciar` também sai com 3 se `/run/tccdiag/<id>/amostras.txt` ou `<espelho-dir>/amostras.txt` já
  existirem. Um id é uma coleta só. Se o amostrador cair, sobe-se outro com id novo, e o `h4.py fase` aceita vários
  `--amostras`.
- **D3.** O `repor` recusa com exit 3 se alguma recusa do código na campanha ainda não estiver no `reposicoes.tsv`.
  Sem essa trava, rodar o `h4.py repor` antes do *runner* faria o *runner* ver a célula no TSV e não repor a recusa
  (`run-rebaseline.sh`, laço da fase `reposicoes`). Ele também confere a `<SAIDA>/matriz.tsv` contra `--matriz` e
  verifica se a célula está na matriz.
- **D4.** O teto é tudo ou nada: se as reposições já feitas mais as novas passarem de 30, nada é gravado.
- **D5.** O vigia tem `--ciclos N` (0 = sem fim, o padrão), usado pelos testes. Não é limiar.
- **D6.** Unit inativa (regra 3) grava `PARADA-H4`, com o motivo, em vez de um arquivo próprio.
- **D7.** O cabeçalho é montado com `printf '%(…)T'` e `$HOSTNAME` (*builtins*). Só o `sha256sum` é externo, com
  `/usr/bin/sha256sum` como alternativa se o `PATH` não o tiver. O descritor de espera é o `<(:)` do prompt. Se ele
  não bloquear (caso do Git Bash, onde o `read -t` volta com EOF), o laço usa um FIFO `.relogio` no diretório da
  coleta, criado uma vez. Na VM (Linux), o `<(:)` bloqueia e o FIFO não é criado.
- **D8. Regras de borda.**
  - O h4b avalia todo intervalo que toca a janela h4, inclusive o que atravessa o início ou o fim.
  - O h4c exige falta **maior** que 3 s (3,0 s exato é cobertura completa).
  - A última linha sem `\n` é ignorada, porque o amostrador pode estar escrevendo nela.
  - Arquivos com o mesmo cabeçalho (espelho e cópia final) são o mesmo segmento, e fica o mais longo.
  - O buraco entre segmentos diferentes não conta como episódio, e as horas observadas são a soma dos segmentos.
  - `fim_utc ≤ âncora + 44 s` dá o veredito `sem-janela`, que não é h4, assim como `sem-ancora`.
  - Arquivo ausente, malformado ou com dois `boot_id` sai com exit 4 (prompt §3.2), e não com veredito h4c.
- **D9.** A `visao` recusa destino dentro de `<SAIDA>`, mantém as `.abortada-*` (por *link*) e não copia os
  diretórios de fase nem `h4/` (os SHA-256 dos `h4-*.tsv` vão para o `VISAO-H4.txt`).
- **D10.** O `parar` aceita `--espelho-dir` opcional. O exit 3 por "bash sem EPOCHREALTIME" está implementado, mas
  não é testado (não há como provocá-lo sem editar o *script*).

## 5. O que vai para a VM

Lista SHA-256 no formato do `sha256sum` (`<hash> *<caminho>`, nesta ordem), conferida também nos arquivos
extraídos do `tar`:

```
e4cc5c794312afbfa7e4743ca556f37033924c51b0246f8f211718a6ee0a8780 *scripts/amostrador-infra.sh
1e7462f46923020abc82c2759b7d77937887c60b91a7af0f2dfc971b8cd4391c *scripts/vigia-h4.sh
53d7e7cb831856a58dc9df1a717df559437857aae5b57d159d42bbd84feb8b30 *analysis/h4.py
```

SHA-256 da lista: `e9c4481989f147fc683d5923aef76213826ac2a69e15f5990336e560a57664a6`.

`tar`, gerado da ponta do código (`aec1812`; o *commit* `docs:` não toca nenhum dos três arquivos):

```
git archive --format=tar -o h4-amostrador-aec1812.tar aec1812 scripts/amostrador-infra.sh scripts/vigia-h4.sh analysis/h4.py
```

SHA-256 do `tar`: **`45d1d5896f9d657dee5a88f03c53b40a153c6b8894f7e7a889ddec981e8af0fd`**. O `git archive` grava o
*commit* no cabeçalho, então o mesmo comando no mesmo *commit* reproduz o *hash* (conferido duas vezes). Os
arquivos `.sh` vão com modo 0775.

## 6. Problemas encontrados

1. **No Git Bash, `<(:)` não serve de descritor de espera.** O `read -t` volta na hora com EOF. Isso levou ao
   alternativo de FIFO (D7).
2. **`wait` dentro de `$( )` devolve 127** (o filho é de outro *shell*). O `parar_pid` da suíte passou a definir
   `RC_FIM` no *shell* do teste.
3. **O alimentador do A3 dormia 0,3 s** depois da volta bloqueada e criava um segundo atraso que não era do
   amostrador. Além disso, `mv` sobre um FIFO falha no Git Bash. Agora o FIFO é trocado por arquivo comum
   (`rm` + `mv`) depois da volta bloqueada, e o teste exige ≥ 3 intervalos depois do longo, para não passar no vazio.
4. **O `criar_shims` do `run-rebaseline.test.sh` tem `}` na coluna 0** dentro dos *heredocs* dos *shims*. A
   extração vai até o `}` que vem depois do `chmod +x`. O arquivo não é copiado nem editado.

## 7. Como usar na VM

Em ordem, para cada fase (`<fase>` = `piloto`, `noite1`, `noite2` ou `reposicoes`; `ID=<fase>-$(date -u +%Y%m%dT%H%M%SZ)`):

1. **Antes do `go` da fase**, na janela `tmux` 1:
   `scripts/amostrador-infra.sh iniciar --id "$ID"`. O exit tem de ser 0, e ele imprime as duas units e a primeira
   amostra.
2. **Vigia**, na janela `tmux` 2:
   `scripts/vigia-h4.sh --id "$ID" --saida <SAIDA do runner> --fase <fase>`. Ele roda a cada 300 s. `PARADA-H4` ou
   `ALERTA-H4C` em `~/rebaseline-infra/$ID/` pedem ação do coordenador (C3 §4.3: Ctrl-C no portão K1 da execução
   seguinte). O vigia não interrompe nada.
3. Rodar a fase normalmente (`run-rebaseline.sh`). `scripts/amostrador-infra.sh estado --id "$ID"` pode ser usado a
   qualquer momento (só leitura).
4. **Depois do bloco de fim de fase do C1 §1.3:** encerrar o vigia (Ctrl-C na janela 2) e rodar
   `scripts/amostrador-infra.sh parar --id "$ID"`. O exit tem de ser 0, com a cópia final conferida e o resumo de
   amostras, maior intervalo e linhas de erro.
5. **Leitura final:**
   `python3 -I analysis/h4.py fase --amostras ~/rebaseline-infra/$ID/amostras-final.txt --saida <SAIDA> --fase <fase>`
   Se `/run` se perdeu por *reboot*, usar o espelho `~/rebaseline-infra/$ID/amostras.txt`. Vale o exit 0 ou 30, e o
   parecer da fase traz `h4/h4-<fase>.tsv`.
6. **Ao fim da noite 2:** `run-rebaseline.sh --fase reposicoes`, depois
   `python3 -I analysis/h4.py repor --saida <SAIDA> --matriz runs/rebaseline/matriz.tsv` (antes com `--dry-run`),
   depois `run-rebaseline.sh --fase reposicoes` de novo (C3 §5).
7. **Cópia para o host:** levar `~/rebaseline-infra/$ID/` junto com os brutos da fase e conferir no host com
   `sha256sum -c amostras-final.txt.sha256` dentro do diretório copiado.
8. **Análise final:** `python3 -I analysis/h4.py visao --saida <SAIDA> --destino <dir novo> [--excluir <lista (h)>]`
   e `rebaseline.py tabelas` sobre o destino.

Levar à VM: o `tar` da seção 5. Na VM, conferir o SHA-256 do `tar` antes de extrair, e depois
`sha256sum -c` com a lista da seção 5.
