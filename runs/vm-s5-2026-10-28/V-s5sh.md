# V-s5sh: revisão independente do invólucro do S5 antes da VM, 2026-10-28

Validador: Senior. Objeto: os commits `2390f7f` (test), `bb67bbe` (feat) e `9c18ec8` (docs) da branch `test/b29-gate-v4`, worktree `wt-gate-v4`, isto é, `s5.sh`, `s5.test.sh` e `ROTEIRO-S5.md` deste diretório. A especificação é a seção "O que o S5 precisa" de `runs/vm-s4-2026-10-28/V-S4.md` (linhas 160-171) e a §5.6 de `docs/decisoes-senior-2026-10-16.md` (linhas 456-470, lida na cópia de `wt-integracao/docs/`). O modelo é o `runs/vm-s4-2026-10-28/s4.sh`.

Não acessei a VM, não rodei `mvn`, não houve push e não editei `s5.sh` nem `s5.test.sh`. Os testes e os mutantes rodaram em cópias `git archive` no scratchpad, nunca no worktree.

## Veredito

**APROVADO para ir à VM, com ressalvas. Nenhuma ressalva bloqueia o S5.** O `s5.sh` não toca em `tc` por conta própria, só lê. Tudo o que ele põe em segundo plano termina sozinho num prazo limitado, e a degradação de rede continua protegida pelos mecanismos que já existiam e não mudaram (traps do injetor, dead-man do `l-lib.sh`, `--heal` manual). A avaliação automática não encontrei como dar VÁLIDO falso nos pontos que olhei. Os erros possíveis vão para o lado conservador (INVÁLIDO quando talvez fosse válido), e o Senior relê na V-S5. Duas ressalvas pedem uma ação do Gestor na VM, sem mudar código: R-4 (conferir a sonda do ssh antes do GO) e R-5 (digitar o GO em até 15 min).

## O que conferi por conta própria

| Item | Como | Resultado |
|---|---|---|
| Ponta e bundle | `git log`, `git bundle list-heads gate-v4f.bundle`, `sha256sum gate-v4f.bundle` | o v4f aponta para `9c18ec8`; o sha256 do bundle bate com `gate-v4f.bundle.sha256` (`d761ca7c…4750`) |
| sha256 do `s5.sh` | no blob de `9c18ec8` e na cópia `git archive` | `8bce87a54add989daaf05358f5689369e43b538453c077e6e1e865bb9e90da08`, igual ao declarado e ao do `ROTEIRO-S5.md`; 0 CR |
| `s5.test.sh` na ponta | cópia `git archive`, rodando sozinho | **356 PASS, 0 FAIL**, 605 s |
| `s4.test.sh` na ponta | mesma cópia | **216 PASS, 0 FAIL** |
| Vermelho em `2390f7f` | cópia `git archive` de `2390f7f` | `RESUMO: 0 PASS, 1 FAIL` (o `s5.sh` não existe). É um vermelho por ausência, o mínimo para um arquivo novo. A força do teste fica medida pelos mutantes abaixo |
| Scripts existentes | `git diff --stat f4ac24d 9c18ec8 --` em `l1.sh`, `l-lib.sh`, `inject-netdegrade.sh`, `portao51.sh`, `runs/gate-b29/g*.sh`, `s4.sh` | diff vazio. O diff inteiro de `f4ac24d` a `9c18ec8` tem só `.gitattributes`, o journal do P-5 e os 3 arquivos novos |
| Tabela de hashes | `sha256sum` na cópia `git archive` | os 4 arquivos batem com `HASHES_ESPERADOS` (`s5.sh:51-54`). O diff de `902654b` (v4c) até a ponta é vazio para os 4 |
| Porta de isn3 | `cluster/podman-compose.yml` | isn1 `11222:11222` (linha 32), isn2 `11223:11222` (54), isn3 `11224:11222` (78). O comentário de `l1.sh:10` (11222) continua intacto |
| Formato real do `tc` | `runs/vm-s3-2026-10-28/vm/l0c/qdisc-durante.txt` | o filtro real aparece como `*flowid 1:3` e `match c0000201/ffffffff at 16`. Os `grep -o` de `s5.sh:157-158` casam com esse formato |
| `curl` contra banner SSH | servidor Python local em 127.0.0.1:22022 que manda `SSH-2.0-OpenSSH_9.6p1…`, curl 8.22 do Git Bash | `rc=1` (o curl recusa a resposta) e `time_connect` impresso (`0.001237`). Confirma a premissa de `tcp_s` neste curl, não no da VM (R-4) |
| Ctrl-C durante a janela | experimento meu sobre a ponta, com os shims do `s5.test.sh`: `set -m`, `kill -INT` ao grupo do `s5.sh` 4 s depois do `event=start` | `s5.sh` sai com rc 130, `rc.txt` registra `interrompido`, o coletor registra `coletor interrompido`, a sonda da janela grava `remove-final` nos 2 caches, e a sonda e o saude-log param de crescer (mesma contagem 3 s e 5 s depois) |

**Uma observação sobre o teste.** Na primeira rodada pus a ponta e 8 mutantes em paralelo. A ponta deu 341 PASS e 15 FAIL, todos por rc 2 (linha de base incompleta). Sozinha deu 356/0. Com `S5_BASE_S=7` e `S5_SAUDE_S=2` sobram 3 ou 4 iterações do saude-log, e a carga faz faltar a terceira. Isso é fragilidade do teste, não do `s5.sh`, porque na VM a base tem 60 s e cerca de 30 amostras. Os mutantes eu refiz em sequência, com o teste reduzido às seções que importam para cada um (R-1).

## Mutantes

Apliquei cada mutante numa cópia `git archive` e rodei o `s5.test.sh` reduzido às seções indicadas.

| # | Ponto protegido | Mutação | Seções | Resultado |
|---|---|---|---|---|
| M1 | aviso do dead-man e cura antes do GO | `s5.sh:435`, `echo ">>> AVISO…"` removido | T-s5-2 | **morto**, 2 FAIL (`aviso do dead-man (~210 s) antes do GO`) |
| M2 | guarda do lock | `s5.sh:341`, lock LIVRE deixa de ser `nok` | T-s5-4 | **morto**, 6 FAIL (`lock livre: rc 1`, `sonda nao chamada`…) |
| M3 | remoção da chave da sonda no fim | `s5.sh:275`, `exec java` vira `java` | T-s5-2 | **morto**, 1 FAIL (`sonda da janela fez remove-final`). O mutante deixou no meu host um shim `java` da janela rodando sem fim depois do teste, e tive de matá-lo à mão. É exatamente o órfão que o `exec` evita: na VM seria uma JVM gravando `sonda-isn3` em isn3 indefinidamente |
| M4 | rc do `l1.sh` na mesma linha | `s5.sh:437`, `RC=${PIPESTATUS[0]}` vira `RC=$?` | T-s5-0, T-s5-7 | **morto só pela verificação estática** (T-s5-0). T-s5-7 passa, porque com `set -o pipefail` (`s5.sh:26`) o `$?` do pipeline já é o rc do `l1.sh`. Mutante equivalente no comportamento |
| M5 | perdas 39..81 | `PERDA_MAX=81` vira 82 | T-s5-2, T-s5-11 | **morto**, 3 FAIL (`82 perdas (27.3333%)`) |
| M6 | `remove-final` da base | `s5.sh:416`, a conferência de 2 de 2 vira `true` | T-s5-5 | **morto**, 3 FAIL (`rc 2`, `sem portao`, `l1 nao chamado`) |
| M7 | rc da avaliação do portão | `s5.sh:426`, `rc=${PIPESTATUS[0]}` vira `rc=$?` | T-s5-6 | **sobrevive**. Também é equivalente sob `pipefail`. A verificação estática de T-s5-0 procura `tee "$OUT/[0-9]` e não vê a linha de `portao()`, que usa `tee "$OUT/$1.log"` (R-3) |
| M8 | Ctrl-C para o coletor | `s5.sh:329`, o trap INT deixa de matar o coletor | T-s5-2, T-s5-7 | **sobrevive**. Nenhum teste manda SIGINT (R-2). O experimento de Ctrl-C acima, sobre a ponta sem mutação, mostra o comportamento correto |

Sobre o dead-man: o dead-man está no `l-lib.sh`, que não mudou e entra na tabela de hashes. No `s5.sh` o que cabe a ele é o aviso (M1), a pré-condição de 0 timers e 0 units `tcc-heal` (T-s5-4, casos `timer` e `unit`), a conferência de 0 timers depois do L-1 (T-s5-11) e a instrução de cura em `05-pos-falha.log` (T-s5-7, T-s5-8). Esses três últimos já têm testes que falham quando o critério some.

## Foco de risco

### (a) O que fica rodando na VM se o `s5.sh` abortar, e quem limpa

O `s5.sh` só lê o `tc` (`amostra_tc`, `nic_linha`, `retrans`). Quem escreve `tc` é o injetor, chamado pelo `l1.sh` verbatim.

| Passo em que aborta | Em segundo plano | Como termina | Quem limpa o `tc` |
|---|---|---|---|
| 0 pré-condições | nada | | nada a limpar |
| 1 linha de base | sonda (`--duracao-s 60`), `ping -c 300` | sozinhos, em até ~62 s. Num Ctrl-C eles não recebem o SIGINT (job assíncrono de shell não interativo ignora SIGINT), então terminam no prazo e a sonda faz `remove-final` | nada a limpar |
| 2 portão pré | nada (o `portao51.sh` roda em primeiro plano) | | nada a limpar |
| 3 GO / janela | coletor, com sonda sem duração, `ping -c 250` e saude-log | (i) trap INT do `s5.sh` manda TERM ao coletor, que manda TERM à sonda (experimento acima); (ii) se o trap falhar, o próprio coletor encerra 30 s depois do `event=end`, e o injetor escreve `event=end` no seu `cleanup` mesmo quando recebe INT, TERM ou HUP (`inject-netdegrade.sh:634-654`); (iii) sem `event=end` nenhum (injetor morto com SIGKILL), o coletor desiste em `ESPERA_START_S + 600` = 25 min (`s5.sh:305`) e só então para a sonda; (iv) o `s5.sh` principal espera o coletor no máximo 120 s depois do `l1.sh` e então manda TERM (`s5.sh:442-443`). A JVM sai em até ~8 s depois do TERM (`SondaHotrod.java:256-264`, `join(8000)`) | o trap do injetor (INT/TERM/HUP); o dead-man por `systemd-run` (SIGKILL ou queda do SSH), em até ~210 s; o `--heal` manual se o `l1.sh` sair no L-2 com `RESIDUO` (dead-man já desarmado) |
| 4 e 5, depois do L-1 | nada | | |
| tmux fechado (SIGHUP ao grupo) | coletor e filhos recebem HUP | o coletor morre; a JVM trata HUP como shutdown e faz `remove-final`; o injetor sai pelo `trap … HUP` com cleanup | o trap do injetor |

O ponto fraco é o caso (iii). Se o `s5.sh` e o injetor morrerem juntos com SIGKILL, a sonda pode ficar até 25 min gravando em isn3. Não é realista numa sessão acompanhada, porque ninguém manda SIGKILL ao grupo, e o portão pré do bloco seguinte recusa `size` diferente de 0. Quando o `l1.sh` falha (rc 4), o `s5.sh` grava `05-pos-falha.log` mas não espera a cura do dead-man nem confere que ela veio (R-6).

### (b) A sonda em isn3 contamina o L-1 ou o portão pós?

- **Portão pré.** Não contamina. A sonda da base termina antes do portão, e o passo 1 exige `remove-final OK` nos 2 caches (M6 morto). O portão pré confere `size=0` nos 6 pares de cache e porta.
- **Portão pós.** Não contamina no caminho normal. A sonda da janela recebe TERM 30 s depois do `event=end`, com o enlace já curado, antes do portão pós. Se a chave sobrar, o portão pós falha (`size` diferente de 0) e o S5 sai INVÁLIDO, nunca VÁLIDO.
- **L-1.** Contamina um pouco, e isso deve ser registrado. A §5.6 diz "cluster **ocioso**". A sonda faz `put` e `get` a cada 2 s nos 2 caches, e o `put` em isn3 replica (SYNC, `owners=2`) pelo enlace degradado. Isso põe tráfego JGroups a mais na banda 3. A razão `dropped/(Sent+dropped)` não muda, porque a perda do netem é independente por pacote. O `TcpRetransSegs` de isn3 passa a incluir retransmissões da sonda, e ele já é só `RESULTADO`. A chance de a sonda provocar uma mudança de vista é desprezível, já que o FD é por heartbeat e são ~2 operações por segundo. Mesmo assim é uma diferença declarada em relação ao "ocioso" da §5.6, e o Gestor decide (P-1).

### (c) A avaliação automática pode dar VÁLIDO falso?

Não encontrei caminho para isso nos critérios que protegem a VM ou o resultado.

- **Perdas.** O injetor só sai com rc 0 se cada par ficar a no máximo 3 σ (`inject-netdegrade.sh:774-779`). Com n = 300 isso dá |k − 60| ≤ 20,78, ou seja, **40 a 80 perdas**. O intervalo 39..81 do `s5.sh` é mais frouxo e não acrescenta risco: ele nunca aceita o que o injetor recusou, porque o rc do injetor também é critério (`s5.sh:122`). O percentual vem do `ping` do iputils (`%g`, 6 algarismos significativos), então `pct × 3` reproduz o k inteiro para n = 300.
- **Contadores, amostras de `tc` e marcadores.** As regras são estritas: exatamente 2 filtros `flowid 1:3` com `match` igual aos IPs de isn1 e isn2, amostra com carimbo dentro da janela, 1 `start` e 1 `end`.
- **Latência.** "Mediana durante ≤ p95 da base + 1 ms" não pega degradação só na cauda (P-2). Não é VÁLIDO falso sobre o filtro, porque um filtro que pegasse o tráfego do host atrasaria a maioria das requisições, mas pode deixar passar uma degradação parcial. O `max durante` sai impresso na linha `OK:` e eu leio na V-S5.
- **Ssh.** O critério do ssh é estrutural. O `curl` de dentro da VM para `VMIP:22` não passa pelo netns de isn3 nem pela qdisc da NIC, então não consegue ver o filtro u32 pegando o SSH. Quem garante isso é o filtro morar só no netns de isn3, mais a NIC conferida sem `prio`/`netem`/`clsact` antes e depois (P-4).
- **"Hot Rod".** O critério automático de Hot Rod (`s5.sh:177`) é o `time_connect` da **mesma** requisição REST a 11224 (campo 4 do `saude.csv`). Como o Infinispan 15 usa endpoint único, é a mesma conexão TCP, e na prática o critério repete o REST. Não está errado, mas na V-S5 eu vou chamá-lo de "conexão TCP ao endpoint único 11224" (P-1).
- **Do lado conservador**, dois casos dão INVÁLIDO onde a §5.6 diria "resultado". Uma mudança de vista tardia (mais de ~45 s depois do fim do L-1) cai nos 5 min do portão pós e o reprova (R-7). Uma requisição de saúde lenta (acima de 2 s) estoura o intervalo de 2 × 2 s e é rotulada como "pausa do host/VM". Nos dois casos o Senior reclassifica à mão.

### (d) A porta 11224 contra o comentário de `l1.sh:10`

Está correto. 11224 é isn3 (`podman-compose.yml:78`), 11222 é isn1 e fica como controle. O `l1.sh` não foi editado: o hash é igual ao do S1 e T-s5-0 confere que a linha 10 ainda diz 11222. A sonda usa `ClientIntelligence.BASIC` com um só servidor (`SondaHotrod.java:125`), então fica de fato presa a isn3 e não segue a topologia até isn1 ou isn2. O tráfego do host chega a isn3 por DNAT com origem no gateway do bridge (10.89.1.1 na G-1, `docs/relatorio-tb-gate-g1-2026-10-16.md:27`), que não está no `match` do u32.

## Ressalvas

| # | Ressalva | Bloqueia o S5? | Ação |
|---|---|---|---|
| R-1 | O `s5.test.sh` é sensível a carga: 341/15 com 9 execuções em paralelo, 356/0 sozinho | não | rodar sempre sozinho; num ciclo futuro, subir `S5_BASE_S` do teste para ~11 s |
| R-2 | Nenhum teste automático cobre Ctrl-C (M8 sobrevive) | não | o experimento manual deste parecer cobre a ponta. Num ciclo futuro, um T-s5 com `set -m` e `kill -INT` ao grupo |
| R-3 | A verificação estática "rc na mesma linha" não vê `portao()` (`tee "$OUT/$1.log"`). M4 e M7 são equivalentes sob `pipefail` | não | ampliar o padrão de T-s5-0 para `tee "$OUT/` num ciclo futuro |
| R-4 | A sonda do ssh supõe que o curl da VM (Ubuntu 24.04, curl 8.5) imprime `time_connect` quando recusa o banner SSH. Confirmei no curl 8.22, não no da VM. Se falhar, toda amostra `ssh22` vira `ERRO`. O passo 1 conta só as linhas e não recusa `ERRO`, então o problema só aparece na avaliação, depois de gastar o L-1 e o GO | não, com a ação ao lado | **antes de rodar o `s5.sh`**, na VM: `curl -s -o /dev/null --connect-timeout 2 -m 2 -w '%{time_connect}\n' http://$(hostname -I \| awk '{print $1}'):22/` deve imprimir um número maior que 0. Se der `0.000000`, não rodar e me avisar. Também dá para conferir a coluna `ssh22` de `base/saude.csv` antes de digitar o GO |
| R-5 | O coletor espera o `event=start` por no máximo 900 s (`ESPERA_START_S`). Um GO dado mais de 15 min depois do prompt roda o L-1 sem coleta da janela, e o bloco sai INVÁLIDO | não, com a ação ao lado | o Gestor digita o GO em até 15 min depois do prompt. Se passar disso, responder qualquer coisa diferente de `GO` (aborta sem armar) e recomeçar |
| R-6 | Depois de rc 4 (injetor falhou com o dead-man armado), o `s5.sh` não espera nem confere a cura | não | o `ROTEIRO-S5.md` já pede que o Gestor fique até a cura. Acrescento o comando: repetir `systemctl list-timers --all --no-legend \| grep -c tcc-heal` até dar 0 e conferir `sudo nsenter -t <pid> -n tc qdisc show dev eth0` nos 3 nós (só `noqueue`) |
| R-7 | Uma mudança de vista tardia reprova o portão pós, e o S5 sai INVÁLIDO, embora a §5.6 a trate como resultado | não | o Senior reclassifica na V-S5. Nesse caso o S6 continua bloqueado até um portão 5.1 limpo, como manda a §5.6 |
| R-8 | Não há receita de limpeza se a chave `sonda-isn3` sobrar, por exemplo depois de SIGKILL ou de uma queda da VM | não | rodar a própria sonda por 2 s, que faz `put`, `get` e `remove-final` só da chave dela: `SONDA_SENHA=infinispan java -cp workload/target/session-workload-*.jar br.unipampa.tcc.session.SondaHotrod --no isn3=127.0.0.1:11224 --caches sessions,counters --duracao-s 2 --usuario admin`, e depois o portão 5.1 |
| R-9 | A sonda deixa o cluster menos "ocioso" do que a §5.6 pede (ver (b)) | não | registrar na V-S5 como diferença declarada (P-1) |

## Perguntas ao Gestor, com a recomendação do Senior

**P-1. Hot Rod: critério automático só pela conexão TCP a 11224, latência das operações para leitura humana?** Recomendo **aceitar**. O risco que a §5.6 quer excluir é o filtro u32 pegar o tráfego do host. O SYN-ACK de isn3 para o host sai pela mesma egress em que está o filtro, então a conexão TCP a 11224 mede exatamente isso, sem depender do protocolo. A latência de `put`/`get` mistura a replicação SYNC pelo enlace degradado de propósito e não serve de critério. Duas condições: (i) na V-S5 eu chamo o critério de "conexão TCP ao endpoint único 11224 (REST e Hot Rod)", porque é a mesma conexão do REST; (ii) o senhor aceita que a sonda rode durante a janela, com o pouco tráfego que ela acrescenta (R-9). A alternativa seria rodar a sonda só na base e no pós, mas a §5.6 pede latência de Hot Rod "na base e durante", então recomendo manter.

**P-2. A regra "mediana durante ≤ p95 da base + 1 ms".** Recomendo **aceitar sem mudar o código antes do S5**. Ela responde bem à pergunta da §5.6 (o filtro pega o host?), porque um RTO de 200 ms em 20 % dos pacotes leva a mediana muito além de 1 ms. Ela não vê degradação só na cauda. Para isso eu leio, na V-S5, o `max durante` que já sai impresso e conto as amostras da janela acima do p95 da base (espera-se ~5 %). Para os próximos blocos, sugiro acrescentar como critério "no máximo 20 % das amostras da janela acima do p95 da base + 1 ms". Com n ≈ 30 e 5 % esperados, o risco de reprovar à toa é pequeno.

**P-3. Perdas pelo percentual do L1.log, supondo 300 transmitidos.** Recomendo **aceitar**. O `ping -c 300` sem `-w` só transmite menos de 300 se for morto, e então o injetor não sai com rc 0. O `%g` do iputils dá o k exato para n = 300. O rc 0 do injetor já garante 40 a 80 perdas por par, e esse rc também é critério do `s5.sh`. A falta da linha `transmitted/received` fica registrada na V-S5 como limite do injetor, que é verbatim e não se edita.

**P-4. Ssh medido só do lado da VM, com ~100 linhas de banner inválido no `auth.log`.** Recomendo **aceitar**, com a pré-checagem da R-4. A medida de dentro da VM não passa pelo netns de isn3, e é por isso que ela não consegue degradar. O que protege o SSH é estrutural (filtro só no netns de isn3, NIC conferida). O ruído no `auth.log` é inofensivo: a VM é Ubuntu 24.04 (kernel 6.8), com OpenSSH 9.6, que ainda não tem `PerSourcePenalties`, e a origem das conexões é o próprio IP da VM, não o do host Windows. Peço ao senhor só duas coisas: anotar se sentiu atraso no terminal durante a janela, e confirmar que não há `fail2ban` ativo na VM (`systemctl is-active fail2ban`). Mesmo que houvesse, o banimento cairia sobre o IP da VM e não sobre o seu.

**P-5. Nome do diretório `vm-s5-2026-10-28`.** Recomendo **manter**. É a data da preparação, como em `vm-s4-2026-10-28`. O subdiretório `vm-<UTC>/` guarda o instante real da execução. Mudar o nome mudaria caminhos no roteiro, o sha256 de referência e o bundle v4f já gerado.

**P-6. Estender `-text` aos 26 arquivos de `runs/gate-b29/` em CRLF na cópia de trabalho.** Recomendo **sim, mas depois do S5 e num commit `chore:` separado**, não neste ciclo. Conferi com `git ls-files --eol` que os 26 estão em LF no índice e CRLF só no checkout (`core.autocrlf=true`). Não há manifesto sha256 em `runs/gate-b29/`, então nada quebra hoje. O que justifica o `-text` é o mesmo motivo da P-4 da V-S4: um manifesto futuro ou uma comparação byte a byte no host. A regra precisa vir com a exceção `runs/gate-b29/*.sh text eol=lf` depois dela, porque a última que casa vence. Assim um `.sh` editado no Windows não entra no git em CRLF. Não convém fazer agora porque mudaria a ponta depois do bundle v4f revisado, sem benefício para o S5.

## Próximos passos

1. Gestor: decidir P-1 a P-6 (recomendo aceitar P-1 a P-5 e adiar P-6).
2. Na VM, com o bundle v4f já gerado (`9c18ec8`): conferir `sha256sum runs/vm-s5-2026-10-28/s5.sh` = `8bce87a5…0da08`, fazer a pré-checagem da R-4 e rodar conforme o `ROTEIRO-S5.md`, com o GO digitado pelo Gestor em até 15 min depois do prompt (R-5).
3. Depois do S5: cópia dos brutos para o host com manifesto sha256, e V-S5 pelo Senior com as leituras humanas de P-1, P-2 e R-7.
4. Num ciclo futuro, sem pressa e antes do S6: R-1, R-2 e R-3 no `s5.test.sh`, e o critério de cauda da P-2, para os próximos invólucros.
