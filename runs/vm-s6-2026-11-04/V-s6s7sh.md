# V-s6s7sh: revisão independente dos invólucros do S6 e do S7 antes da VM, 2026-11-04

Validador: Senior. Objeto: os commits `0ee7a26` a `4d88948` da branch `test/b29-gate-v4` (worktree `wt-gate-v4`), isto é, `s6.sh`, `s6.test.sh`, `ROTEIRO-S6.md`, `s7.sh`, `s7.test.sh` e `ROTEIRO-S7.md`, por cima de `38e4909` e `99c1d7e`. Especificação: `docs/prompt-worker-s6-s7-g5b-2026-11-04.md` e a §5.7 de `wt-plano-ctrl/docs/decisoes-senior-2026-10-16.md` (linhas 472-489).

Não acessei a VM e não houve push. Não editei `s6.sh`, `s7.sh` nem os testes. Testes e mutantes rodaram num clone próprio no scratchpad (`TMPDIR` também no scratchpad), um de cada vez.

## Veredito

**APROVADO COM RESSALVAS.** Uma ressalva precisa de decisão do Gestor **antes do S6**: R-1, o critério A6/B1-A6 de persistência ≥ 15 s. Nas condições medidas da VM ele dá INVÁLIDO falso em cerca de 10 a 15% das execuções corretas de cada bloco. As outras ressalvas não bloqueiam.

O invólucro não escreve em `tc`, não cura e não desarma. Ele só lê. O que põe em segundo plano tem prazo e é encerrado no Ctrl-C. A partição continua protegida pelo que já existia: trap do injetor, trap do `g5b.sh` e dead-man. Não achei caminho que dê VÁLIDO falso.

## O que conferi por conta própria

| Item | Como | Resultado |
|---|---|---|
| Executores e blocos anteriores intactos | `git diff --name-status 9c18ec8..4d88948` | só arquivos **A** (novos): 3 docs do Senior, `V-s5sh.md` e os 6 arquivos do S6/S7. Diff vazio em `g5b.sh`, `g56-lib.sh`, `g-lib.sh`, `inject-partition.sh`, `portao51.sh`, `s1..s5`, `s4.sh`, `s5.sh` e testes. Por isso não rodei de novo `s5.test.sh`, `s4.test.sh` e `g5b.test.sh`: as entradas deles são idênticas às de `9c18ec8` |
| Cópias verbatim do `s5.sh` (4.5) | extraí `avaliar_portao`, `stats`, `tcp_s`, `saude_loop` e `portao` dos três arquivos e comparei com `diff` | `stats`, `tcp_s`, `saude_loop` e `portao` são idênticas byte a byte. `avaliar_portao` difere só na mensagem `o s5.sh` → `o s6.sh`/`o s7.sh`, o que o prompt permite. O laço do intervalo do saúde-log é o do `s5.sh` com um arquivo só (`janela`) |
| Modo e CR | `git ls-files -s`, `grep -c $'\r'` | `s6.sh`, `s7.sh` e os testes estão em 100755, com 0 CR |
| sha256 | clone da ponta | `s6.sh` `c01d8c79888e6afa20a7766aac355f7aec12b3c53bddaf14cb2b5877aa0e2ef8` e `s7.sh` `29de1bf0bd57241ea6a9b2e586aff38283f4a8052e23b81e494c252941a4489e`, iguais aos dos roteiros. `g5b.sh` continua `6f50e3e3…0378` |
| `s6.test.sh` inteiro, sozinho | clone da ponta | **588 PASS, 0 FAIL**, 874 s |
| `s7.test.sh` inteiro, sozinho | mesmo clone, depois do anterior | **652 PASS, 0 FAIL**, 1191 s |
| rc na mesma linha (R-3) | `grep` de todo `tee "$OUT/`/`tee -a "$OUT/` sem `PIPESTATUS` | sobram só `echo … \| tee -a rc.txt` e o `exec > >(tee …)` do console, que não têm rc para ler |
| Formato real da unit (B5, D-3) | `runs/vm-s3-2026-10-28/vm/l0c/unit-armada.txt`, capturado na VM em 28/10 pelo mesmo `systemd-run` | o parser do B5 aceita o formato real do systemd 255: `ExecStart=` vazio de reset, `ExecStart="/usr/bin/bash" "scripts/…"`, `Environment="PODMAN=sudo podman"`, `Description=` igual no `.timer`, `RemainAfterElapse=no`. O `span_s` aceita `2min 25s` |
| Origem do `sudo` dentro da unit (D-1 do S7) | `runs/vm-s3-2026-10-28/vm/l0c/journal-unit.txt:3-25` e `02-pos-l0c-leitura.log:67` | o `sudo` chamado por root dentro da unit do dead-man **é** registrado (`sudo[…]: root : PWD=/home/server1/tcc-gate ; USER=root ; COMMAND=/usr/bin/nsenter … tc qdisc del dev eth0 root`) e aparece em `journalctl -u <unit>`, ou seja, com `_SYSTEMD_UNIT` = a unit. Isso responde à dúvida do prompt (3.4) e serve à dúvida 5 |
| Bundle v4g | repositório vazio + `git fetch` só de `1266c12` (sem objetos posteriores), depois `git pull --ff-only gate-v4f.bundle` e `git pull --ff-only gate-v4g.bundle` | sem o v4f, `git bundle verify gate-v4g.bundle` falha ("lacks prerequisite 9c18ec8"), o controle negativo. Com o v4f aplicado (ponta `9c18ec8`), o v4g verifica e avança para `4d88948`, árvore limpa, com os hashes acima. sha256 do bundle `c7de36e2…809e8e`, igual ao `.sha256`. O v4g não toca em `runs/gate-b29/go.log`, então o `--ff-only` passa com o `go.log` modificado da VM |

## Aderência ao §5.7 e ao prompt

| Critério | Origem | Implementação | Observação |
|---|---|---|---|
| A1/B1-A1 | V-S4 P-2 | `s6.sh:134-136`, `s7.sh:140-142` | ok |
| A2 | §5.7 "Validade" (rc, sem FAIL) | `s6.sh:139-142` | ok |
| A3 | §5.7 "split observado nos logs antes do KILL" | `s6.sh:145-147` | ok |
| A4 | parâmetro do plano ("aprox. +70 s") | `s6.sh:150-152` | ok |
| A5 | §5.7 "`rc_injetor=137`; ausência de `end`" | `s6.sh:155-158` | ok |
| A6 | §5.7 "persiste por >= 15 s (sem `end`, `clsact` presente, vistas divididas)" | `s6.sh:161-177` | **R-1**. Além do prompt, exige amostras de `persistencia.txt` dentro do intervalo e nenhuma MergeView com instante entre `t_kill` e `t_persistencia_ok`. Ambas são deriváveis do §5.7 |
| A7 | §5.7 "`--heal` rc 0; sem `clsact` após o heal" | `s6.sh:180-185` | ok |
| A8 | §5.7 "MergeView de 3 membros; HEALTHY x3" | `s6.sh:188-197` | ok, conferido na cópia e em `05-vistas.log` depois de `t_heal` |
| A9 | §5.7 "desarmado só depois de saudável; list-timers armado durante e removido ao fim" | `s6.sh:200-207` | ok |
| A10 | §5.7 "ordem split → KILL → heal → merge" | `s6.sh:210-213` | ok |
| A11 | §5.1 | `s6.sh:216-218` | ok |
| A12 | V-S4 P-2 | `s6.sh:221-226` | ok, cópia verbatim |
| B2 | §5.7 "nenhum `--heal` manual" | `s7.sh:186-190` | ok |
| B3 | §5.7 "disparo entre dur+margem e dur+margem+10 s" | `s7.sh:193-199` | ok. Recalcula a partir da cronologia e exige `tempos.txt` = cronologia |
| B4 | §5.7 "journal da unit (execução, rc 0)" | `s7.sh:202-207` | ok, na cópia e em `05-journal-unit.txt` |
| B5 | §5.7 "`systemctl cat` (corpo idêntico ao de produção)" | `s7.sh:210-229` | ok, conferido contra o formato real (tabela acima). Ver dúvida 4 |
| B6 | §5.7 "`clsact` ausente, MergeView, HEALTHY x3; estado final" | `s7.sh:232-246` | ok. Também procura `systemctl stop` no journal do sudo, o que reforça "sem desarmar" |
| B7 | §5.7 "carimbos armar/KILL/disparo" | `s7.sh:249-252` | ok |
| D-1 S6/S7 | Q-13, prompt 3.4 | `s6.sh:231-242`, `s7.sh:256-268` | ok. Ver dúvidas 3 e 5 e R-4 |
| D-2 S6/S7 | Q-13 | `s6.sh:245-253`, `s7.sh:271-277` | ok. Ver dúvida 2 |
| D-3 | Q-13 | `s6.sh:256-261` | ok |
| D-4 (ID/StartedAt) | Q-13 | `s6.sh:509-510` | ok |
| D-5 (espera na falha) | Q-13, R-6 da V-s5sh | `s6.sh:400-423`, `568-572` | ok |
| Q-15 (disparo depois do heal manual) | recomendação do Senior | `s6.sh:264-270` | ok. Disparo **antes** do heal manual vira FALHA. É derivação direta da recomendação ("válido desde que o heal manual seja anterior") |

Não achei critério inventado sem origem. Os acréscimos (MergeView no intervalo, `systemctl stop` no B6, `tempos.txt` = cronologia) tornam a avaliação mais estrita sem sair do §5.7.

## Mutantes meus (diferentes de M1..M9)

Cada mutante foi aplicado com `sed` numa cópia do clone, e o teste rodou reduzido às seções indicadas, um mutante por vez.

| # | Ponto protegido | Mutação | Seções | Resultado |
|---|---|---|---|---|
| MS-1 | `tee` do `04-g5b.log` ignora SIGINT (o trap do `g5b.sh` precisa ter onde escrever no Ctrl-C) | `s6.sh:556`, `( trap '' INT; exec tee … )` vira `tee …` | T-9 (inclui T-9b) | **sobrevive**, 19/0. Nenhum teste confere que o trap do `g5b.sh` chega ao `--heal` num Ctrl-C ao grupo. O Git Bash não reproduz a semântica de grupo de processos e SIGPIPE do Linux, então o teste não conseguiria matar este mutante com fidelidade. Ver R-3 |
| MS-2 | 3.3: esperar o dead-man depois de uma falha | `s6.sh:414`, `[ "$nt" != 0 ] \|\| break` vira `break` | T-6 | **morto**, 2 FAIL (`T-6 parou em 0 timers`, `T-6b prazo esgotado`) |
| MS-3 | D-1 do S7: cura manual depois do disparo | `s7.sh:257`, só conta dels até `t_fire` | T-2, T-7 | **morto**, 4 FAIL, mas por efeito colateral: o del da unit do fixture vem depois de `t_fire` e some da contagem. Não existe caso com del **da sessão** depois de `t_fire`. Mutante morto, lacuna real de teste (R-5) |
| MS-4 | A6: ao menos 1 amostra em `persistencia.txt` | `s6.sh:168`, `-ge 1` vira `-ge 0` | T-2, T-7 | **sobrevive**, 241/0. Nenhum caso esvazia `persistencia.txt`. É quase equivalente: o `g5b.sh` real sempre grava ao menos uma amostra quando `PERSISTE ≥ 1`. Lacuna pequena (R-5) |
| MS-5 | D-2: ao menos 1 amostra depois de `t_heal + 5 s` | `s6.sh:251`, `b0 -ge 1` vira `-ge 0` | T-2, T-7 | **sobrevive** na segunda rodada, sozinho: 241/0, 366 s. O caso "D-2 serie vazia" mata pelo `b1`, e nenhum caso trunca a série só depois do heal. Lacuna de teste (R-5) |

**Fragilidade do teste observada (R-6).** Na primeira rodada do MS-5, o T-2 falhou pelo A12 (`intervalo maximo do saude-log 4.926 s > 2 x 2 s`), o que nada tem a ver com o mutante. Havia outra carga no host. A suíte inteira, sozinha, tinha passado 588/0 pouco antes. É a mesma classe da R-1 da V-s5sh: com `S6_SAUDE_S=2` e o host carregado, uma iteração do `saude_loop` passa de 4 s. Na VM o nominal é o mesmo (2 s), mas a VM não roda teste em paralelo. O risco ali é outro (pausa real da VM), e é exatamente o que o A12 quer pegar.

## Dúvida 1 (A6/B1-A6): análise quantitativa

O laço do `g5b.sh` (linhas 140-147): `T_P0=$(date +%s)`, depois `while [ $(( $(date +%s) - T_P0 )) -lt 15 ]`. A cada volta: `tem_clsact` (`sudo nsenter … tc qdisc show`), `grep`, `echo` e `sleep 2`. Na saída: `sem_merge_desde` (3× `sudo podman logs --since`), `cp` e só então `carimbo t_persistencia_ok`.

Notação: `f` é a fração de segundo no instante de `T_P0` (uniforme em [0,1)), `c` o custo de uma volta além do `sleep 2`, `ε` o atraso até a primeira checagem, `δ` o tempo de `t_kill` até `T_P0` (kill, wait, dois `tee`) e `L` o tempo dos 3 `podman logs` mais o `cp`.

- A checagem `j` acontece em `ε + j(2 + c)`. O laço sai na volta 7 se `f + ε + 14 + 7c ≥ 15`, isto é, com probabilidade `p7 = ε + 7c`. Caso contrário sai na volta 8, com `t_persistencia_ok − t_kill ≥ 16 s`, e passa.
- Saindo na volta 7: `t_persistencia_ok − t_kill = 14 + δ + ε + 7c + L`, **independente de `f`**. Dá FALHA se, e só se, `δ + ε + 7c + L < 1 s`.
- Por construção, `t_persistencia_ok − t_kill > 14 s` sempre (sair exige `date +%s ≥ T_P0 + 15`, e `T_P0 > t_kill − 1`). Esta é a garantia que o `g5b.sh` dá de fato.

Calibração com dados reais da VM (`runs/vm-s3-2026-10-28/03-sudo-pre-armar-e-pos.log:7-17`, 28/10): entre `sudo nsenter … tc qdisc show` consecutivos passam ~5 a 7 ms, e um `sudo podman inspect` leva ~35 a 70 ms. Os logs dos contêineres vão para o journald (linhas `server1 isn1[33271]: …` no journal), então `podman logs --since` lê o journal a partir do instante e não o histórico inteiro. Estimativas: `c ≈ 12 a 20 ms` (sudo/nsenter/tc mais 4 a 5 forks), `ε, δ ≈ 10 a 30 ms`, `L ≈ 0,15 a 0,5 s`.

Resultado: `δ + ε + 7c + L ≈ 0,25 a 0,7 s < 1 s`. Então **P(FALHA indevida) ≈ p7 ≈ 0,1 a 0,15 por bloco**, e cerca de 20 a 28% de ao menos um falso INVÁLIDO entre S6 e S7. A estimativa de ~1/3 do Worker supõe `c ≈ 45 ms` por volta, cerca de 3× o custo medido do `sudo` nesta VM. **Ela superestima a taxa, mas o problema é real e tem o tamanho de um bloco refeito a cada 7 a 10.** A taxa só cai a 0 se `L` passar de ~0,85 s, o que não dá para garantir sem medir na VM. A janela 14,3 a 15 s do Worker também está imprecisa: o limite inferior garantido é 14 s, e os valores que falham ficam em `14 + δ + ε + 7c + L`, tipicamente 14,3 a 14,8 s.

**Recomendação: aceitar no invólucro o limite que o `g5b.sh` garante** (FALHA se `t_persistencia_ok − t_kill < 14 s`, e `RESULTADO`/`LEITURA HUMANA` com o valor exato quando cair em [14, 15) s). Não recomendo corrigir o `g5b.sh` agora. Motivos:

1. **Comparabilidade.** Os dados das REPs não dependem dessa escolha: as REPs não usam o `g5b.sh`, e o §5.7 já põe o G-5b fora do conjunto do G-4. O que muda é a cadeia de verificação do executor. O `g5b.sh` `6f50e3e3…0378` é o que a V-S0 validou e o que foi conferido na própria VM no S3 (`02-pos-l0c-leitura.log:9`). Aceitar 14 s preserva essa cadeia. Corrigir o `g5b.sh` (trocar `T_P0` por ns, como a linha 125 já faz para o KILL) muda o executor: hash novo, `g5b.test.sh` de novo, fixtures regeneradas, tabela de hashes do `s6.sh` e do `s7.sh`, nova V e novo bundle. E o S6/S7 deixaria de rodar o executor conferido no S3.
2. **Fidelidade ao propósito do §5.7.** O critério serve para mostrar que o corte não se cura sozinho depois do SIGKILL. 14 s ou 15 s não muda essa conclusão. A persistência tem prova independente no D-2 (`clsact=1` em toda a série do coletor até `t_heal`). No S7 o corte dura até `t_fire`, uns 75 s depois do KILL.
3. **Coerência.** O invólucro não deve ser mais estrito que o `PASS corte PERSISTIU` do próprio executor num ponto em que o executor está certo pela regra dele.

Custo: uma linha em cada invólucro (`PERSISTE_S` da avaliação passa a ser 14, e a mensagem cita a granularidade), os casos T-7 correspondentes e novo sha256 de `s6.sh`/`s7.sh`. É um desvio explícito do texto "≥ 15 s" do §5.7 e precisa da decisão do Gestor. Se o Gestor preferir o texto literal, a alternativa correta é corrigir o `g5b.sh`, com todo o custo acima, e não manter o invólucro como está.

## Demais dúvidas do Worker

2. **D-2 com ao menos 1 amostra por intervalo:** concordo. Sem o mínimo, o critério fica vazio quando a série some. Na VM sobram ~40 amostras antes de `t_heal` e, depois de `t_heal + 5 s`, o tempo até a MergeView (~46 s) e até HEALTHY. O mínimo depois do heal está no código, mas não tem teste próprio (MS-5 sobrevive, R-5).
3. **D-1 do S6, del da unit depois de `t_heal` como RESULTADO:** correto. É a recomendação da Q-15. Um del da unit **antes** de `t_heal` continua FALHA (testado).
4. **B5:** bem feito. Ele compara diretiva por diretiva contra o `armar` de produção, aceita só `AccuracySec=1s`, `OnActiveSec` e `RemainAfterElapse=no` a mais no `.timer` e recusa qualquer outra diretiva. Bate com o formato real capturado no S3.
5. **Dels da unit no S7 como critério:** a dúvida do prompt ("nada provou que o sudo de root dentro da unit é registrado") **já tem resposta nos brutos do S3** (tabela acima). Recomendo promover a FALHA "≥ 1 del da unit entre `t_kill` e `t_fire + 5 s`" (Q-13, Gestor). Custa uma linha e um caso de teste, e fecha o detector pelos dois lados. Não é obrigatório: o B4 já prova a execução da unit.
6. **`flock -n` cria o arquivo de lock:** benigno. Se o arquivo não existir, o `flock` o cria como `server1`, 0644. O injetor também roda como `server1` (o `g5b.sh` o chama sem sudo) e criaria o mesmo arquivo com o mesmo dono. O `--heal` do dead-man (root) não toma o lock (`inject-partition.sh:93`), então o `fs.protected_regular` do Ubuntu não entra em jogo.
7. **Desvios:** `-o json` foi permitido no prompt (3.4), e o parser casa com o formato real do `sudo`. O `tee` que ignora SIGINT é correto e necessário (ver R-3). "HEAD contém `902654b`" como NÃO OK está de acordo com o prompt, onde a falha do passo 0 é exit 1. O MANIFEST no trap EXIT está correto: inclui o console e não é gerado no Ctrl-C (`MANIF=0`).

## Riscos para a VM (item 5)

- **Partição/`tc`:** o invólucro não escreve em `tc` (T-10 e leitura do código). Num Ctrl-C ou numa falha do `g5b.sh`, a cura vem do trap do injetor (antes do KILL), do trap do `g5b.sh` (depois) e, por último, do dead-man (210 s no S6, 145 s no S7). Não achei caminho em que o invólucro impeça qualquer um deles.
- **Unit/timer:** o invólucro nunca desarma. Se o `g5b.sh` falhar e o timer não disparar, o `pos_falha` espera até prazo + 60 s e sai com 4, deixando o timer armado (ele dispara depois, e o heal é idempotente). O passo 0 do próximo bloco recusa rodar com timer `tcc-heal`. Aceitável, porque desarmar à mão seria intervenção.
- **Ctrl-C no Linux (R-3):** no tmux, o SIGINT vai a todo o grupo de primeiro plano, **inclusive ao `tee` do console** (`exec > >(tee -a …)`), que morre. Depois disso: (a) o `tee` do `04-g5b.log`, que ignora INT, recebe SIGPIPE na primeira escrita e morre; (b) qualquer escrita do `g5b.sh` em stdout/stderr depois disso o mata por SIGPIPE. O `abortar` do `g5b.sh` escreve uma linha em stderr **antes** do `inj --heal`. Essa escrita costuma cair no buffer do pipe enquanto o `tee` interno ainda vive, mas é uma corrida. Se perder, a cura fica com o dead-man. Além disso, o trap INT do `s6.sh`/`s7.sh` escreve em stderr **antes** de gravar `interrompido` em `rc.txt`, e pode morrer de SIGPIPE ali, sem rc 130 nem registro. Ele mata o coletor **antes** da escrita, então não sobra órfão. Nenhum desses caminhos deixa a partição sem rede de segurança, mas o roteiro deve dizer: depois de um Ctrl-C, conferir `systemctl list-timers | grep tcc-heal` e `tc qdisc show` de isn3, e esperar o dead-man ou fazer a cura manual. O padrão foi herdado do `s5.sh`. Correção sugerida para a próxima revisão (não bloqueia): no trap INT, gravar `rc.txt` antes de qualquer `echo` e mandar a mensagem para `/dev/tty`.

## Ressalvas

- **R-1 (bloqueia o S6 até a decisão):** A6/B1-A6 ≥ 15 s dá ~10 a 15% de INVÁLIDO falso por bloco. Recomendação acima (≥ 14 s no invólucro).
- **R-2 (cosmético):** o `--dry-run` do `s7.sh` imprime "e bash runs/gate-b29/g5b.sh **A** neste terminal" no texto do plano do passo 3. O comando que roda de fato é `B`. Corrigir junto com a R-1, já que o hash muda de qualquer forma.
- **R-3:** Ctrl-C no Linux (seção anterior). Acrescentar ao roteiro o que conferir depois de um Ctrl-C.
- **R-4:** o D-1 classifica como "sessão" qualquer `_SYSTEMD_UNIT` diferente da unit do dead-man, inclusive ausente ou `user@1000.service` (tmux ≥ 3.4 põe os painéis em `tmux-spawn-*.scope`). É o lado conservador e está correto. Só registro para a leitura da V-S6/V-S7.
- **R-5 (testes):** faltam três casos: um del **da sessão** depois de `t_fire` no S7 (o MS-3 morreu por efeito colateral), `persistencia.txt` vazio (o MS-4 sobrevive, quase equivalente) e a série do coletor truncada logo depois de `t_heal` (o MS-5 sobrevive). Acrescentar junto com a R-1.
- **R-6 (testes):** o T-2 é sensível à carga do host pelo A12 (4,9 s > 4 s numa rodada com outro processo no host). Rodar as suítes sozinhas, como já diz o roteiro.

## Perguntas ao Gestor

1. **A6/B1-A6:** aceitar o limite de 14 s garantido pelo `g5b.sh` (recomendado) ou corrigir o `g5b.sh` (hash novo e nova validação)? Sem essa decisão o S6 roda com ~10 a 15% de chance de INVÁLIDO falso.
2. **Q-13/dúvida 5:** promover a FALHA "≥ 1 del da unit do dead-man entre `t_kill` e `t_fire + 5 s`" no S7, já que o S3 provou que esse registro existe?
3. Com qualquer mudança de código, o Worker refaz os testes, os hashes dos roteiros e o bundle, e o Senior faz uma verificação curta (só o diff).

## Bundle

Este parecer é um commit novo na branch e muda a ponta. **O `gate-v4g.bundle` atual (`c7de36e2…`, `9c18ec8..4d88948`) fica desatualizado e precisa ser gerado de novo** a partir da ponta final, depois da decisão da R-1. Não gerei o bundle.
