# Relatório do Worker: W-6 e W-6b, orquestrador das campanhas S3 (2026-11-05)

> Nota da integração (05/11, merge do W-6 em `feature/controlador-integracao`): os números de problema e de decisão
> técnica do W-6 foram deslocados para depois dos do W-2b (TD-014 → TD-015, P-45..P-53 → P-47..P-55). Este
> documento já usa os números da integração; onde o texto descreve a própria renumeração, ficam os dois.

Branch `feature/w6-orquestrador-s3`, worktree `wt-w6`, sobre a integração `17f2bd4` (`int/2026-11-05-fix`). Nada foi
enviado a `origin` e nada foi integrado. Este relatório cobre os 12 pontos da validação V-W6
(`docs/validacao-w6-2026-11-05.md` §4), as ressalvas do W-6b (§5 e a adenda da §11), os comandos e as contagens.

Os 12 pontos seguem a lista da V-W6: os 8 requisitos do coordenador mais 4 itens do pedido de validação.

## 1. Os 12 pontos

| # | Ponto | Onde está | Testes | Estado depois do W-6b |
|---|---|---|---|---|
| 1 | *Cluster* sem *caches*; os seis criados pela REST com leitura de volta nos 3 nós; recusa se divergir | `recriar_cluster`, `camada3` (`run-campanha-s3.sh`); `cluster/infinispan-sem-caches.xml`, `cluster/podman-compose-sem-caches.yml`, `cluster/caches/*.json` | S4, S12; `ClusterSemCachesXmlTest`, `CachesCargaJsonTest` | aprovado pela V-W6; sem mudança |
| 2 | `timeout -k K` com K > `T_enc` pior caso + folga; SIGTERM antes de SIGKILL | `montar_cmd`, `parar_carga` | S1, S4, S7 (agora exige o SIGKILL depois de 2300 ms reais, ressalva S5) | aprovado; S5 feita |
| 3 | Braço B recusado com atuador `nulo`, política `nenhuma`, sem `politica` ou outro `param_sha256`; `p2_habilitada = false` | `conferir_controlador`, `conferir_meta`; `campanha_s3_conferir.py controlador/meta` | S2, S6, S18; S1 (o `--dry-run` agora confere o controlador, ressalva S1) | aprovado; S1 feita; I-1 fica para o *merge* |
| 4 | Religamento depois do AC-5; `t_religamento_ns`; `convergencia_estado` | `tratar_sinal_fim`, `religar_parados`; `Religamento.java`, `ConvergenciaLoop` | S5, S21, S23 (sem a linha de fim, ressalva S3); `ReligamentoTest`, `ConvergenciaEstadoTest` e outras | aprovado; S3 feita |
| 5 | Saúde depois do F1 pela REST, não pelo exit do injetor (P-35) | `amostra_volta_f1` | S8 | aprovado; sem mudança |
| 6 | h4 como no *rebaseline* | `avaliar_h4`, `campanha_s3_h4.py` | S13; `test_campanha_s3_h4.py` | aprovado; decisão do P-47: `--id diag-<UTC>`, sem mexer no amostrador |
| 7 | F1, F2 e F4 com marcadores `start`/`end` (B-25.1) | `disparar_injecao`, `concluir_injecao`; `inject-crash.sh --marker` | C16..C21, S4, S16; S22 (B1) | aprovado; B1 feito |
| 8 | GO por tty com texto exato | `ler_gate` | S3 | aprovado; o `go rede` fica até o X-17 |
| 9 | Pedidos depois do I-3b (religamento antes do teto, pareamento, `NUL`, `sem_rodada_valida`) | `tratar_sinal_fim`, `conferir_pareamento`, `tirar_nul` | S5, S19, S20, S21 | aprovado; sem mudança |
| 10 | Congelados intactos | — | seção 4 | aprovado; conferido de novo |
| 11 | Parâmetros com origem | `campanha_s3_conferir.py parametros`; constantes do orquestrador | `test_campanha_s3_conferir.py` | aprovado; M1 feita (seção 2.4) |
| 12 | Testes e números declarados | seção 3 | — | repetidos na ponta do W-6b |

## 2. W-6b: o que mudou

Commits (TDD, `test:` vermelho antes da implementação): `98149ca` (testes de B1, S1..S6, M1), `4dec3df` (testes de B2,
M1 alinhada ao pré-registro, folga do S22), `9c8a8b5` (S25) e os commits de implementação e documentação desta entrega. O commit
`a34aa36` é do Senior (adenda da V-W6) e foi preservado.

### 2.1 B1: injetor que sai porque o AC-5 parou o nó da falha (P-53)

Com F2/F4, exit 1 ou 6 e o nó-alvo fora de `running` (ou já religado na linha de fim), a recusa fica pendente
(`injetor_alvo_parado` no `execucao.json`). Na pós-checagem, se a linha de fim e a trilha põem o AC-5 APLICADA nesse
nó e, na F2, o `injetor.log` tem `OK: <no> nao conecta a nenhum par`, a execução segue com o aviso "falha efetiva ate a
parada do no pelo AC-5; contadores do injetor nao lidos". Em qualquer outro caso, a recusa de antes, com o que faltou.
Os dublês de F2 e F4 passaram a sair como os injetores reais (1 e 6) quando o alvo não está `running` no fim da janela.
O S22 cobre: positivo F2, positivo F4, F2 sem a linha OK (RECUSADA), exit 1 com o nó de pé (RECUSADA) e nó parado sem
AC-5 (recusa do injetor mais o religamento do O8, `DESCARTADA-INFRA`).

### 2.2 B2: uma referência de p99 por perfil (P-54)

`--referencia-async` e `--referencia-sync` (cada uma com o seu SHA-256) no lugar de `--referencia`, que agora sai com 2
e diz o que usar. A referência de cada execução vem do modo da linha da matriz. A de cada perfil presente na fila é
obrigatória e conferida no pré-voo e no `--dry-run` pelo `campanha_s3_conferir.py referencia` (esquema
`referencia-p99/1`, cenário S3, perfil). As duas entram no *bundle*, o `execucao.json` registra o `referencia_sha256`
usado, e o pré-voo 4 roda o `--dry-run` do programa em cada perfil e *controller* da fase. O dublê de `java` recusa
referência de outro perfil, como o `LigacaoControlador.java:204`. O S24 roda uma fase com SYNC e ASYNC.

### 2.3 S1 a S6

| # | Correção | Teste |
|---|---|---|
| S1 | o `--dry-run` roda `conferir controlador` e imprime `controlador conferido: sha256=… politica=… p2_habilitada=…` | S1: bloco `politica` ausente com braço B, `tetoLagMs` diferente e `p2Habilitada = true` saem com 4 sem chamar nada |
| S2 | o pré-voo recusa (exit 4) *timer* `tcc-heal-cs-*` de outra invocação | S2 |
| S3 | sem a linha de fim, a pós-checagem religa os nós parados (como o O8) e a execução vira `DESCARTADA-INFRA`, sem parar a fase | S23 |
| S4 | `collect-resources.sh` (o coletor usado) no `bundle.sha256` | S4 |
| S5 | o S7 exige o SIGKILL depois de 2300 ms reais (K = 23 s virtuais na escala 10) | S7 |
| S6 | o leitor do stdout espera o `cat` que grava o `stdout.log` antes de mandar o fim | S4: `sha256sum -c` do `MANIFEST` de cada execução |

### 2.4 M1: coerência do teto do processo

`exec_teto_s ≥ duracao_s + T_enc + religamento_prazo_s + convergencia_teto_s + convergencia_periodo_s`, com `T_enc`
= 21 s passado pelo orquestrador (`--t-enc-pior-s`, obrigatório). O período a mais cobre a volta do laço de
convergência, que dorme antes de olhar o teto (`ConvergenciaLoop.executar`). O tempo até a âncora (`t_pre` ≤ 2,4 s) e
o fechamento depois da convergência (`t_pos` = 13,5 s) são medidos, não constantes, e o pré-registro os soma com
folga. **Com os números do pré-registro da calibração (§4.1), o código aceita `exec_teto_s` = 1005 s: o mínimo é
300 + 21 + 60 + 600 + 5 = 986 s** (teste `test_valores_do_pre_registro_da_calibracao_aceitam_1005`). A primeira versão
do W-6b somava os 120 s do prazo da âncora (`ANCORA_TIMEOUT_S`) e recusaria os 1005 s (mínimo 1106 s); troquei depois
da adenda da V-W6, que corrigiu o tempo até a âncora.

### 2.5 Achado novo: veredito do O8 (P-55)

Ao escrever o caso "nó parado sem AC-5" do B1, vi que as mensagens de religamento (`E_INFRAS`: nó religado pelo O8,
`podman start` que falhou, alvo fora de `--admin`) entravam nos motivos sem o prefixo `infra`, que é o que o veredito
`DESCARTADA-INFRA` procura. Uma execução com só um desses desvios terminava ACEITA com motivos. Nenhum caso exercitava
o O8 sozinho. Agora elas entram com `infra: `.

Isso expôs, na primeira rodada da suíte completa, um segundo defeito que o prefixo escondia: com o disparo atrasado no
Git Bash, o F1 do S4 ainda estava em curso quando a linha de fim chegou, e o O8 religou o nó que o `inject-crash.sh` ia
religar. O O8 agora pula o nó de um F1 em curso (o injetor o religa e grava o `end`). Caso S25, com commit `test:`
vermelho antes da correção. Na VM o F1 acaba em +109 s numa carga de 300 s, então o caso só aparece se o injetor
travar.

## 3. Comandos e contagens

Rodados no *host* (Git Bash), sem Podman, sem VM. Antes das 22:00Z só suítes com *shims*; Maven depois.

| Suíte | Comando | Resultado |
|---|---|---|
| Orquestrador (S1..S25, só *shims*) | `bash scripts/run-campanha-s3.test.sh` | **269 PASS, 0 FAIL** (2373 s; antes 193/0 em S1..S21) |
| Conferências | `python -I scripts/test_campanha_s3_conferir.py` | **30 OK** (21 + M1 4 + B2 5) |
| Matriz | `python -I scripts/test_campanha_s3_matriz.py` | **15 OK** |
| h4 do S3 | `python -I scripts/test_campanha_s3_h4.py` | **11 OK** |
| `inject-crash.sh` | `bash scripts/inject-crash.test.sh` | **141 PASS, 0 FAIL** (injetor sem mudança) |
| Surefire | `cd workload && mvn -o -q -T1 test` | não rodado no W-6b (Maven só depois das 22:00Z): `workload/` não muda desde `474b1ce`, cujo Surefire deu **1100/0/0/1**; repetir na ponta integrada |
| `analysis` | `python -E -P -m unittest discover -s analysis` | **370 OK, 1 *skip*** (sem mudança) |

Vermelho conferido antes de cada implementação: `test_campanha_s3_conferir.py` com 6 falhas (M1) e depois 11 (M1
revista e B2); suíte do orquestrador com 13 falhas no S1 antes do M1/S1 e o S1 saindo com 2 antes do B2.

Na primeira rodada do S22, duas sub-rodadas falharam por tempo, não por lógica: no Git Bash o laço do orquestrador
atrasa o disparo da falha em 6 a 20 s virtuais (escala 20), e com 60 s de carga a janela da F2 terminava depois do
religamento. O S22 passou a usar carga de 120 s. Isso também mostrou um caso real que a correção do B1 cobre: o injetor
pode sair depois que o orquestrador já religou o nó pela linha de fim (pelo AC-5 ou pelo O8), e o alvo religado conta
como parado.

## 4. Congelados

`git diff 449e757 -- scripts/run-rebaseline.sh scripts/inject-*.sh scripts/amostrador-infra.sh scripts/vigia-h4.sh
analysis/h4.py scripts/cria-caches.sh scripts/collect-resources.sh workload/ cluster/` é vazio. SHA-256:
`run-rebaseline.sh` `d95305fe…`, `inject-netdegrade.sh` `4ebdd8a3…` (o do S5 do *gate*), `inject-crash.sh`
`531a98a1…` (o do B-25.1, já conferido pela V-W6). Vale a regra da V-W6 §2: nenhum *bundle* do *rebaseline* é regerado
de `int/*`; retomadas usam o `rb-20805b0.tar`.

## 5. Parâmetros novos e origem

Nenhum limiar numérico novo no W-6b. `--t-enc-pior-s` recebe o `T_ENC_PIOR_S` = 21 s que já existia (contrato JSONL
v1.2, P-42). As duas referências são arquivos com SHA-256 dado pelo operador, como os outros. A carga de 120 s do S22
é só da suíte.

## 6. F3 na avaliação (pedido condicional)

Não fiz: não é barato, e o coordenador já o separou num W-6c. Custo estimado: 2 a 2,5 h com testes. O que entra:
`F3` em `CELULA_RE` e `FALHAS` da matriz; quatro ou cinco parâmetros com origem (`f3_no`, `f3_delay_ms`,
`f3_jitter_ms`, `f3_dur_s`, distribuição), o que muda o esquema para `campanha-s3/2` e os arquivos dos
pré-registros; no orquestrador, o ramo F3 com dead-man `nsenter -t <PID> -n tc qdisc del dev eth0 root` (como o runner
do *rebaseline*, 341 s), `inject-jitter.sh --node --iface --delay --jitter --distribution --duration`, conferência de
`netem` residual, exits que seguem {0, 1, 4} e o equivalente do B1 (o AC-5 pode parar o `isn2` sob *jitter*); dublê
ou *shim* de `tc` para o `inject-jitter.sh`; caso novo na suíte. Ponto que pede decisão: o `inject-jitter.sh` não
grava marcadores e os injetores não podem mudar, então o orquestrador gravaria `fault=F3 … start/end` em volta do
processo do injetor. A janela fica mais larga que a do `netem` pela guarda de *netns* e pela sonda do injetor (menos
de 1 s). O `Marcadores.parse` aceita qualquer nome de falha, então o programa não muda.

## 7. O que fica para depois

- **I-1** (repetição da leitura da `meta` com exit 4 até 5 s): no *merge* com o W-2b, como decidido.
- **Renumeração** (TD-014 → TD-015, P-45..P-53 → P-47..P-55): no *merge*. Não renumerei na branch; a integração renumerou e trocou os números deste relatório.
- **P-48** (topologia do `rcm` principal no S3): conferir no *smoke* local antes da calibração.
- **`go rede`**: fica até o X-17.
