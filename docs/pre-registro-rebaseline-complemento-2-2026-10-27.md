# Complemento 2 ao pré-registro do *rebaseline*: F1 como *crash* silencioso (2026-10-27)

> **Aprovado pelo Gestor em 2026-10-28** (decisões Q1 = B, Q2..Q6 = Sim de
> `docs/prompt-f1-kill-2026-10-27.md` §9, transmitidas ao Senior pelo coordenador). Vale a **opção B**.
> Os trechos marcados "[A]" descrevem a alternativa rejeitada na Q1 e ficam só como registro; não
> valem para o piloto nem para as noites. A seção 6 entrou em `docs/decisoes-tecnicas.md` como
> **DT-017**, e a seção 7 entrou como adenda da DT-009 parte 2, com a ponta, os SHA-256 novos de
> `scripts/inject-crash.sh` e `analysis/rebaseline.py` e o SHA-256 da lista **a preencher após a
> entrega do Worker** (`fix/f1-crash-silencioso`) e a integração. O *smoke* local da seção 4 (data
> "preencher") também sai dessa entrega. Tudo antes do piloto e antes de qualquer dado do *rebaseline*.
>
> Complementa `docs/pre-registro-rebaseline-aprovado-2026-10-23.md` (PR) e
> `docs/pre-registro-rebaseline-complemento-1-2026-10-27.md` (C1). **Nenhum dos dois é editado.**
> Nenhum dado do piloto ou da campanha existia quando este texto foi escrito.

## 1. Motivo

O F1 do PR é `inject-crash.sh --node isn2 --duration 60`, que faz `podman stop` e `podman start`
(PR §2, linha da matriz e linha do tempo; DT-009). `podman stop` manda SIGTERM: o Infinispan sai de
forma ordenada, com *leave* dos *caches* e do JGroups, grava o estado global (`ISPN000390`) e a JVM
termina com 143 (C1 §1.4 item 2). Isso não é o F1 da Metodologia, que define "*crash* silencioso de
nó", "sem aviso prévio" (`Metodologia.tex:87`), detectado pelo FD_ALL3 com *timeout* de 40 s (T16,
T17). O próprio PR §2 justifica os 60 s com "60 s > FD_ALL3 de 40 s (T16/T17)", o que só faz
sentido para um nó que some sem avisar. A bateria de junho e os *gates* mediram, sob o rótulo F1,
uma parada ordenada.

A pilha JGroups do *cluster* (`cluster/jgroups-tcp.xml`) tem FD_SOCK2 além do FD_ALL3. O FD_SOCK2
suspeita de um membro assim que a conexão TCP com ele fecha (JGroups 5.3.16,
`doc/design/FD_SOCK2.txt:27-28`). Um SIGKILL fecha as conexões do processo morto (FIN/RST), então
`podman kill` sozinho seria detectado em cerca de 1 a 3 s pelo FD_SOCK2, não pelo FD_ALL3. Para o nó
sumir sem aviso, a interface dele precisa parar de transmitir antes da morte do processo.

## 2. Regra nova do F1 (complementa PR §2 e DT-009, "Matriz" e "Linha do tempo")

- **F1** = `inject-crash.sh --node isn2 --duration 60` com o modo padrão `silencioso`, disparado a
  +49 s da âncora (DT-011, inalterado):
  1. guarda de *netns* (PID de `isn2` > 1 e *netns* dele diferente do *netns* do PID 1);
  2. `sudo nsenter -t <pid> -n ip link set dev eth0 down` no *netns* de `isn2`, com conferência de
     que a interface caiu;
  3. logo em seguida `podman kill --signal KILL isn2`, com conferência de `State.ExitCode = 137`;
  4. 60 s depois, `podman start isn2`. O contêiner é o mesmo (nunca `podman rm`); o *netns* é novo e
     a interface volta de pé.
- **Linha do tempo esperada:** `isn2` cala e morre a ≈ +49 s; os pares instalam a vista sem `isn2`
  entre ≈ +89 e ≈ +98 s (FD_ALL3 com *timeout* 40 s e verificação a cada 8 s, mais o VERIFY_SUSPECT2
  de 1 s); `isn2` volta a ≈ +109 s, com *join* e *state transfer*. Esses instantes são esperados, não
  critério.
- **[A]** F1 = `podman kill --signal KILL isn2`, 60 s, `podman start isn2`, sem isolamento. Detecção
  esperada pelo FD_SOCK2 em poucos segundos. A justificativa "60 s > FD_ALL3" do PR §2 deixa de
  descrever o caminho de detecção e é declarada como tal.

## 3. K6 de F1 (complementa PR §6, K6)

Onde o PR diz "F1: *log* do injetor com `stop` e `start`", passa a valer: *log* do injetor com
`modo=silencioso`, `eth0 down`, `kill isn2`, `ExitCode=137` e `start isn2`; injetor com exit 0;
disparo na tolerância de DT-011; *cluster* de volta a 3 nós HEALTHY antes da execução seguinte. Um
*log* com `stop isn2` e sem `kill isn2` é recusa K6. **[A]** `modo=kill`, `kill isn2`,
`ExitCode=137` e `start isn2`.

Os códigos de saída do injetor seguem o contrato C2 da V-RB: 1 e 4 dão recusa K6; 2, 3 e 5 dão
exit 6 do *runner* (infraestrutura).

## 4. O que fica igual e o que é só esclarecido

- **K8 / DT-013:** igual. O SIGKILL também começa uma vida nova do processo (`ISPN080001`); a
  justificativa da DT-013 ("o F1 reinicia o processo") vale sem mudança.
- **C5 (`falha_efetiva`):** igual no código. `f1_parado_desde_ancora_s` passa a ser o instante da
  morte, sem os segundos de saída ordenada. O instante do isolamento sai do `injetor.log`, que passa a
  ter carimbo por linha com milissegundos.
- **DT-014, paradas (a) a (g), parada (e) com 180 s:** iguais.
- **C1 §1.4 item 2** ("O F1 é `podman stop` ... A JVM sai com 143"): o raciocínio sobre a política de
  reinício continua (nenhum `restart:` no *compose*); a descrição do F1 e o código 143 ficam
  substituídos por este complemento. C1 não é editado.
- **C1 §1.3, parecer da fase, h3:** acrescenta-se uma conferência. Na lista de eventos da fase
  (`podman events ... --filter event=died`), cada `died` de `isn2` cai dentro de uma execução F1 e tem
  código de saída 137. Um `died` com 143 dentro de uma execução F1 é recusa K6 dessa execução (o F1
  não foi o pré-registrado); fora de execução F1 já é (h).
- **Estado global do Infinispan:** um `___global.lck` deixado pelo SIGKILL não impede a subida na
  15.0.21 (o *lock* é do sistema operacional e morre com o processo; `unclean-shutdown-action=FAIL`
  só é consultado se o *lock* estiver em uso). Confirmado no *smoke* local de 2026-10-27/28
  (`runs/smoke-f1-silencioso-2026-10-27/`; data preenchida em 2026-10-28 com a adenda da DT-009 parte 2).
- **Jar:** não muda (`5eced136…`). **`run-rebaseline.sh`:** não muda (`d95305fe…`). **Matriz,
  ordem, sementes, análise do PR §7:** não mudam.

## 5. Interpretação dos dados anteriores (complementa PR §11)

- Todo F1 medido antes deste complemento (bateria de junho, *gates*, *smokes*) foi parada ordenada
  com *leave*. Esses resultados são declarados assim no texto e não são comparados com o F1 do
  *rebaseline* como se fossem a mesma falha.
- A conclusão de junho "F1 quase não perturba a cauda" vale para saída ordenada. O F1 novo inclui um
  intervalo de ≈ 40 s em que o *cluster* ainda conta com `isn2`; o efeito esperado na cauda é maior,
  e não é previsto aqui.
- K3 sob o F1 novo pode recusar por razões que não são defeito (operações presas até o *timeout* do
  cliente, escritas SYNC com *timeout*). Se o ensaio ou o piloto mostrar isso de forma sistemática,
  vale o PR §8: entrada nova aprovada antes da noite 1. O mecanismo do F1 não é trocado por causa do
  desfecho.

## 6. Texto da DT-017 (para `docs/decisoes-tecnicas.md` depois da aprovação)

```markdown
## DT-017: F1 do *rebaseline* é *crash* silencioso (isolamento da interface e SIGKILL), PRÉ-REGISTRO (<data>)

> **Aprovada pelo Gestor em <data>.** Registrada antes de qualquer execução do piloto. Texto completo
> em `docs/pre-registro-rebaseline-complemento-2-2026-10-27.md`.

- **Origem:** decisão do Gestor de 2026-10-27 (F1 deixa de ser `podman stop`); levantamento e opções
  em `docs/prompt-f1-kill-2026-10-27.md`; respostas Q1..Q6.
- **Motivo:** `podman stop` é saída ordenada (SIGTERM, *leave*, `ISPN000390`, exit 143), não o
  "*crash* silencioso" de `Metodologia.tex` (F1, T16, T17). Com FD_SOCK2 na pilha, um SIGKILL
  sozinho é detectado pelo fechamento das conexões em segundos, não pelo FD_ALL3.
- **Decisão:** F1 = guarda de *netns*, `ip link set dev eth0 down` no *netns* de `isn2`,
  `podman kill --signal KILL isn2` (ExitCode 137), 60 s, `podman start isn2`, pelo modo padrão
  `silencioso` de `inject-crash.sh`. K6 de F1 exige no `injetor.log` `modo=silencioso`, `eth0 down`,
  `kill isn2`, `ExitCode=137` e `start isn2`. Parecer da fase confere `died` de `isn2` com 137.
  [A: `podman kill` sem isolamento; K6 com `modo=kill`, `kill isn2`, `ExitCode=137`, `start isn2`.]
- **Rejeitado:** manter `podman stop` (não é *crash*); retirar o FD_SOCK2 da pilha (muda um fixo do
  PR §2); `podman pause` (não mata a JVM, a volta é *merge*, não *crash-recovery*). [Com B:
  `podman kill` sozinho, rejeitado por não ser silencioso.]
- **Efeito:** muda `scripts/inject-crash.sh`, a função `k6` de `analysis/rebaseline.py` e os testes
  (edições autorizadas na Q2). Jar, `run-rebaseline.sh`, matriz, sementes, K1..K5, K7..K10, paradas
  e análise não mudam. *Bundle* novo registrado na adenda da DT-009 parte 2. F1 de junho e dos
  *gates* passa a ser descrito como parada ordenada no texto acadêmico.
```

## 7. Adenda da DT-009 parte 2 (modelo, preencher depois da integração)

```markdown
### Adenda da DT-009 parte 2: ponta e *bundle* depois da DT-017 (<data>)

- **Origem:** DT-017; integração de `fix/f1-crash-silencioso` em `feature/controlador-integracao`
  (validação `docs/validacao-f1-silencioso-<data>.md`). São identificadores, não escolhas.
  Registrada antes de qualquer execução do piloto. Também registra que a ponta `988fd3c` da parte 2
  foi seguida por `3c9db71` e `c055399` (matriz no git, G1, G3, G4). Nesses dois *commits* o único
  arquivo do *bundle* tocado foi `runs/rebaseline/matriz.tsv`, que entrou no git com o SHA-256 já
  registrado; os demais *blobs* em `c055399` têm os SHA-256 da parte 2 (conferido pelo Senior em
  2026-10-27 para `inject-crash.sh`, `rebaseline.py` e `run-rebaseline.sh`).
- **Ponta:** `<SHA de 40 caracteres>`, base `c055399`.
- **Jar:** inalterado, `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf` (cópia G2).
- ***Bundle* da VM** (`git archive` da ponta, LF):

  | Arquivo | SHA-256 |
  |---|---|
  | `analysis/rebaseline.py` | `<novo>` |
  | `runs/rebaseline/matriz.tsv` | `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35` |
  | `scripts/inject-crash.sh` | `<novo>` |
  | `scripts/inject-jitter.sh` | `7e68962b1b7f508ee84ca7561a20c0bba36c43ea4597d7390e7c8091cd24e529` |
  | `scripts/rebaseline_matriz.py` | `3531916a863832fd5aea050910111c69f0c9130c0bd325c3ad3f238e44a3533a` |
  | `scripts/run-rebaseline.sh` | `d95305fee402b1f882d387a40e81d4f83df72d7fe4b6daf810516e0627267366` |
  | `workload/target/session-workload-0.1.0-SNAPSHOT.jar` | `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf` |

  SHA-256 da lista no formato do `sha256sum`: `<novo>` (substitui `e304ba4b…` para o piloto e as
  noites). Na VM, `sha256sum -c` arquivo a arquivo.
- **Dependências na VM** para o F1 novo: `nsenter` (util-linux) e `ip` (iproute2), as mesmas do F3;
  conferidas pelo pré-voo do *runner* (`nsenter`, `tc`) e por `command -v ip` no bloco de abertura
  da fase.
```
