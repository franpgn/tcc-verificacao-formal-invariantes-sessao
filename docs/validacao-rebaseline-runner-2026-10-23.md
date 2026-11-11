# Validação V-RB: *runner*, matriz e análise do *rebaseline* (2026-10-23)

> Senior, 2026-10-23. Entrega validada: branch `feature/rebaseline-runner-analise` (worktree
> `wt-rebaseline`), base `9ddd5fe`, ponta `599ea5e`, 9 commits. Spec: `docs/prompt-rebaseline-runner-analise-2026-10-23.md`
> com a adenda da DT-011; `docs/pre-registro-rebaseline-aprovado-2026-10-23.md` (PR); DT-009..DT-012.
> Relatório do Worker: `wt-rebaseline/docs/relatorio-rebaseline-runner-2026-10-23.md`.
> Sem push, sem integração, nada na VM, nenhum `tc`/`netem` no *host*. Os mutantes foram reaplicados
> numa cópia descartável no *scratchpad*, apagada no fim.

## 1. Veredito

**Aprovado com correções antes do piloto.** A matriz, a análise e o *runner* fazem o que o PR e a DT-011
pedem, e o que conferi de forma independente bate (SHA-256 da matriz, ordem congelada, comando `java`,
+49 s da âncora, 251 s, K6 em +48..+51 s, dead-man antes do netem, sem `|| true`, R7, I2 só REAL,
*bootstrap* por execução). Cinco coisas precisam mudar antes do piloto, todas pequenas e na mesma branch:

| # | O que | Por quê | Tipo |
|---|---|---|---|
| C1 | K8 por vida do processo do nó (P1) | com o texto atual, a primeira F1 dá PARADA | exige DT-013 (rascunho registrado, aguarda Gestor) |
| C2 | gate `go f3` **antes** de criar o `exec-*` e antes do K1 (P5/D8) | reproduzi: um `go f3` digitado errado deixa `exec-<i>` só com `portao.json`, e a nova tentativa sai com exit 4 ("execução interrompida") até alguém mexer nos brutos à mão | defeito do *runner* |
| C3 | mapa de exits do injetor (P2/D4): 1 e 4 recusa K6; 2, 3, 5 e o resto exit 6 | 2, 3 e 5 são erros determinísticos de ambiente (uso, dependência, `sch_netem`): repetiriam em toda F e gastariam recusas da campanha | refinamento |
| C4 | `null` em `por_no_desde_ultimo_start` não pode passar em silêncio; `remocao_extra = true` conta como falha de K6 | o primeiro é pré-requisito do C1; o segundo é o texto do PR ("netem ausente depois" do injetor) | refinamento |
| C5 | registrar, só para relatório, o instante efetivo da falha (F1: `FinishedAt`/`StartedAt` de `isn2`; F3: amostra de netem a ≈ +51 s) | K6 mede o lançamento do injetor, não a ação; os *logs* dos injetores têm um só carimbo, do início (seção 2.1) | refinamento, sem mudar critério |

## 2. Números medidos por mim

| Verificação | Resultado |
|---|---|
| Arquivos tocados `9ddd5fe..599ea5e` | só `A` (7 arquivos novos); `git diff --name-status --diff-filter=MDR` vazio; `run-baseline.sh`, `inject-*.sh`, `percentis.py`, `compare_scenarios.py`, `bootstrap_ic.py` e `workload/` intactos |
| Linearidade | 9 commits, cada um com um único pai, em cadeia sobre `9ddd5fe`. Reflog: dois `--amend` locais, antes de existir commit em cima: `1301802 → 07a93d3` (`test:`, troca do `-I` no `rodar`, D3, declarado) e `1a40e5b → c501739` (`feat:`, só `chmod +x` de `run-rebaseline.sh` e `.test.sh`, **não declarado**, inócuo). Nada publicado foi reescrito; aceito |
| Matriz, testes | `python -I scripts/test_rebaseline_matriz.py`: **8/8 OK** |
| Matriz, SHA-256 independente | gerador meu, escrito a partir do PR §2..§4, CPython 3.12.6: `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35`, igual ao do Worker (3.12.10) e ao do `rebaseline_matriz.py gerar` |
| Ordem congelada (M3) | as 11 rodadas do meu gerador batem posição a posição com a tabela da seção 4 do relatório e com `ORDEM_CONGELADA` |
| R-S2 | 528 sementes de *thread* (132 execuções × 4 *threads*, `rep` 1), nenhuma repetida |
| Análise | `python analysis/test_rebaseline.py` com `REBASELINE_ORACULO_DIR=.../wt-b43-9/runs/b43-9-smoke`: **55/55 OK**, A12 executado (ver seção 3) |
| *Runner* | `bash scripts/run-rebaseline.test.sh`, duas execuções completas: 1ª **92 PASS / 3 FAIL** (9 min 39 s), as 3 no caso `r3b` (exit 10 com 3 *workloads* em vez de 4); 2ª, com o *host* parado, **94 PASS / 1 FAIL** (9 min 5 s): R4 "`podman stop` dentro de +48..+51 s" com o `stop` a +129,9 s (o disparo do *runner* estava certo, +49,48 s). R3 sozinho: 19/19. **Não reproduzi os 95/95 do Worker**; as falhas são de tempo no *harness* (seção 2.1), não do *runner* |
| Mutantes reaplicados | X2, X4 e X8 mortos (seção 4) |
| Rebase sobre `642d81f` | `git merge-tree --write-tree 642d81f 599ea5e`: sem conflito; conjuntos de arquivos disjuntos (seção 6) |

### 2.1 As falhas intermitentes da suíte do *runner*

- **`r3b` (1ª execução).** Quatro execuções com o relógio do *shim* a ×50 (180 s de espera do F1 = 3,6 s
  reais; 10 min de K1 = 12 s reais). Não se repetiu no R3 isolado nem na 2ª execução completa. Leitura
  mais provável: custo de *fork* do Git Bash sob carga (eu rodava leituras em paralelo), o mesmo efeito
  que o Worker descreveu em D2.
- **R4 (2ª execução).** Relógio a ×2; o *runner* disparou a +49,48 s, mas o `podman stop` do
  `inject-crash.sh` real (contra os *shims*) só saiu a +129,9 s simulados, isto é, o injetor ficou
  ≈ 40 s reais parado antes do `stop`. Uma pausa dessas no Windows é do ambiente (antivírus, *fork*),
  não do *runner*.
- **Consequência para a VM (achado C5).** A segunda falha mostra uma coisa real: K6 mede o instante em
  que o *runner* lança o injetor, não o instante em que o injetor age. Na VM um atraso entre os dois é
  improvável mas possível (por exemplo um `sudo` lento), e K6 passaria do mesmo jeito. Os *logs* dos
  injetores não ajudam: `TIMESTAMP` é calculado uma vez no início do script (`inject-crash.sh:32`,
  `inject-jitter.sh:65`) e repete em todas as linhas. Proposta sem mudar critério pré-registrado:
  o *runner* registra, só para relatório, o instante efetivo da falha (F1: `.State.FinishedAt` e
  `.State.StartedAt` de `isn2` depois do injetor; F3: uma amostra extra de `tc qdisc show` logo depois
  do disparo, a ≈ +51 s), e o parecer do piloto confere esses instantes. Se o piloto mostrar atraso, a
  mudança de K6 vira DT nova antes da noite 1.
- **Recomendação de teste:** casos com várias execuções a ×10/×20, e a asserção do `podman stop` de R4
  tolerante a pausa do *host* (ou medida pelo *shim* com o relógio real), para a suíte ficar
  determinística no Git Bash.

## 3. A12 (regressão contra o *smoke* B-43.9)

Executado, não pulado. Para Q3 (S1 sync), Q4 (S1 async) e Q5 (S2 sync), as funções novas reproduzem o
`analise.json` selado: Q3 C1 dif 0,0273 p.p., C2 5,1086 %, C3 k = 5 835 / n = 119 230; Q4 C1 0,0367,
C2 5,0349 %, C3 7 609 / 156 469; Q5 C1 0,0020, C2 5,0633 %, C3 23 253 / 463 653; C5 ok nos três.
Os números são os do relatório do Worker e saíram iguais na minha execução.

## 4. Mutantes reaplicados (cópia descartável, suíte do teste que deveria matar)

| Mutante | Mudança aplicada por mim | Resultado |
|---|---|---|
| X2 | `alvo = E_LANC_MS + 49 s` (lançamento do processo, não a âncora) | **morto** por R4: disparo a +36,6 s da âncora (âncora do *java* falso a 12 s); 3 FAIL (disparo, `podman stop`, `inj_inicio_offset_s`) |
| X4 | `inject-jitter.sh` lançado antes do `systemd-run` | **morto** por R6c ("sem dead-man, nenhum netem": 4 `qdisc add` em vez de 0); R6a passa com o mutante, como o Worker anotou |
| X8 | *bootstrap* que junta os eventos das execuções e reamostra eventos (p99 de cada reamostra) | **morto** por A11 `test_reamostra_execucoes_e_nao_eventos` (1 FAIL em 55) |

## 5. Conferências de conteúdo

- **Comando `java`** (`montar_cmd`, linhas 266-270): `-jar <jar> --scenario <S1|S2> --mix v3 --replication
  <sync|async> --reset-state on --duration 300 --warmup-min-sec 30 --threads 4 --ops 0 --rep 1 --seed
  <S0+100·i> --servers 127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224 --username admin --password <...>
  --csv-dir <dir>`: idêntico ao PR §2, na mesma ordem; sem `--marker`, `--convergence`, `--sweep`,
  `--stop-at-fault-end`. O B-43.5 não criou `--controller`, então nada a acrescentar.
- **Âncora:** `Warm-up ate +` é impresso em `WorkloadMain.java:377-381` logo depois de `inicioMs` (que vem
  depois do *reset* e da carga de 200 000 entradas) e antes de as *threads* começarem; `System.out` tem
  *autoflush* em quebra de linha, então a linha chega ao `stdout.log` no instante certo. O *runner* faz
  *polling* de 200 ms sem *fork*.
- **Injeção:** `alvo = âncora + 49 000 ms`; F1 `inject-crash.sh --node isn2 --duration 60`; F3
  `inject-jitter.sh --node isn2 --iface eth0 --delay 20ms --jitter 13ms --distribution normal --duration
  251`; dead-man `sudo systemd-run --unit tcc-heal-rb-<i> --on-active=341s <nsenter> -t <pid> -n <tc>
  qdisc del dev eth0 root` **antes** do injetor, com caminhos absolutos; sem dead-man armado, nenhum
  netem (R6c). Amostras de `tc qdisc show` em +70 e +280 s. K6 com `INJ_TOL = (48.0, 51.0)`.
- **Gates:** leitura só de `/dev/tty` fora do modo de teste; `RB_MODO_TESTE=1` exige `PODMAN` *shim* do
  diretório marcado (R11). Texto exato `go <fase> <date +%F>` e `go f3 <i>`; timeout de 1 h dá exit 5.
- **Sem `|| true`** no *runner*, na análise e no gerador (grep vazio). Os exits obrigatórios são
  capturados com `rc=0; cmd || rc=$?`.
- **K1..K10:** K1 portão + `Reset: depois=0,0`; K2 exit 0 e CSV; K3 exit 0 e veredito OK; K4 C1, C2, C3,
  C8; K5 só recusa em `none`; K6 pela DT-011; K7 seed = matriz = S0 + 100·i e rep = 1; K8 parada; K9
  parada só em SYNC `none` (inclui `sem_marco` e `revertido_por_bloqueio`, DT-010); K10 reportado.
- **R7:** com falha, C1/C2 usam só Validate isolados VALID|INVALID, os outros desfechos vão para
  `validate_isolados_outros`; sem falha, a definição antiga (A3 prova a igualdade).
- **I2:** só `invariante == "I2"` e `classificacao == "REAL"`; `Violacoes detectadas:` só informativo (A8).
- **Bootstrap:** `default_rng(SeedSequence([231024, k]))`, k fixo pela lista ordenada de 63 rótulos,
  reamostra **execuções** com reposição nas duas células; Mann-Whitney sobre os p99 por execução; "não
  estimado" com menos de 8 execuções.
- **Paradas (a)..(d)** e R-S3 em `estado`; o piloto não conta. (e) K1 em 10 min e cluster em 180 s, (f)
  netem residual e (g) exit inesperado estão no *runner*.

## 6. Rebase sobre `642d81f` (B-43.5 integrado)

Basta *rebase* linear: os 6 commits de `9ddd5fe..642d81f` tocam `workload/src/main` (`Cli.java` só na
descrição de `--seed`; `MisturaV3`, `Scenario`, `WorkloadMain` na origem do `Random` da mistura), testes
Java e `runs/b43-5-smoke/`; a entrega do *runner* só cria arquivos em `scripts/`, `analysis/` e `docs/`.
Nada no *runner* ou na análise depende do comportamento do B-43.5:

- o B-43.5 não muda nenhuma opção nem nenhuma linha de stdout (diff sem `println`/`printf`), logo o comando
  `java`, o `--dry-run`, a âncora e o *parse* de `Configuracao:`, `Mistura v3:`, `Auditor I2:` ficam iguais;
- K7 lê `seed=`/`rep=` (inalterados); C1..C3 medem a mistura e valem com qualquer gerador semeado;
- A12 usa o *smoke* B-43.9 (anterior ao B-43.5) como oráculo de funções sobre dados fixos;
- a reprodutibilidade por `--seed` (DT-012) é propriedade do *jar*; o *runner* só confere o SHA-256 do jar,
  que no complemento parte 2 tem de ser o do *jar* construído da ponta **depois** do *rebase*.

Depois do *rebase*, o `mvn -q -o -f workload/pom.xml test` esperado é o do B-43.5 (761/0/0/1), não os
746 do relatório. Recomendo refazer a suíte do *runner* e da análise depois do *rebase*, por disciplina.

## 7. Recomendações sobre os pontos do Worker

| Ponto | Recomendação |
|---|---|
| **P1** (K8 × F1) | **Mudar K8 por DT nova antes do piloto: rascunho DT-013 registrado.** O aviso é de primeira ocorrência por JVM (1 por nó nos três *smokes*; nos logs do B-43.9, 1 `ISPN080001` e 1 `ISPN006010` por nó). Regra proposta: por nó, ≤ 1 na vida atual (`--since StartedAt`) **e** total ≤ número de vidas (linhas `ISPN080001`). Só a vida atual não basta: um segundo aviso na vida antiga de `isn2` nos primeiros 49 s da F1 seguinte escaparia. Leitura `null` vira exit 2/6. Não recomendo ensaiar o F1 na VM para "ver se repete": a regra literal pararia de qualquer forma, e o ensaio é uma injeção a mais |
| **P2** (exit do injetor, D4) | Separar por natureza: `0` normal; `1` (genérico) e `4` (nó não encontrado ou parado) recusa K6, porque podem ser transitórios; `2` (uso), `3` (dependência) e `5` (`sch_netem` rejeitado) são determinísticos e repetiriam em toda F, então exit 6 (para a fase), como qualquer outro código. A9 continua valendo (exit 4 recusa) |
| **P3** (retomada, D5; netem residual, D6) | D6: **aceito** como está (o dead-man fica armado se o netem não sai; exit 6). D5: **aceito** o princípio (nunca sobrescrever brutos; o *runner* não decide sozinho), com a correção C2 para tirar o caso mais provável (`go f3` errado) desse caminho. Para os casos que restarem (queda da VM, `kill` do *tmux*), proponho deixar a regra escrita agora, para não decidir depois de ver dados: execução interrompida **antes** do lançamento do *workload* (sem `stdout.log`) tem o diretório renomeado para `exec-<i>-<celula>.abortada-<UTC>` e o mesmo i roda de novo (a semente nunca mediu nada); interrompida **depois** do lançamento conta como RECUSADA ("interrompida"), entra em (a)/(b) e é reposta no fim com o próximo i livre. Pergunta ao Gestor (Q3) |
| **P4** (só a VM confirma) | Da VM (Ubuntu 24.04, kernel 6.8, podman 4.9.3 *rootful*, `relatorio-tb-gate-g0-2026-10-16.md:14`): `curl --digest` no `/health`, `systemd-run --on-active` como dead-man e `systemctl list-timers` já foram usados nos gates TB/V3. `flock -F -E` (util-linux 2.39 do 24.04), `podman inspect {{.Created.Unix}}`/`{{.State.StartedAt.Unix}}` (campos `time.Time` no podman 4.9) e `podman logs --since <unix>` devem funcionar, mas nunca rodaram lá com esta sintaxe. O ponto de risco real é outro: `desarmar_deadman` exige saída **vazia** de `systemctl list-timers --all --no-legend 'tcc-heal-rb-*'`; se o systemd 255 imprimir algo com a lista vazia, a primeira F3 termina com exit 6. Tudo isso pode ser conferido sem injeção no ensaio de pré-voo (passo novo na checklist, seção 8) |
| **P5** (gate f3 depois do K1, D8) | **Inverter** (C2): o `go f3` vem antes de criar o diretório da execução e antes do K1. Assim o K1 fica imediatamente antes do *workload* mesmo que o Gestor demore, e um `go f3` errado sai com exit 5 sem deixar diretório. O custo é o Gestor esperar os ≥ 60 s do K1 depois de digitar |

| Desvio | Recomendação |
|---|---|
| D1 | aceito (é a DT-011) |
| D2 | aceito; acrescentar: casos com várias execuções a ×10/×20, ver a falha intermitente do `r3b` |
| D3 | aceito (`-I` só em `checar`/`estado`, como na VM); o `--amend` foi local e antes do commit seguinte |
| D4 | ver P2 |
| D5, D6 | ver P3 |
| D7 | aceito (data local da VM; 1 h de espera) |
| D8 | ver P5: inverter |
| D9 | aceito (sem âncora o *workload* é morto e K2 recusaria de qualquer forma) |
| D10 | aceito; o parecer do piloto confere `configuracao_confere = true` nas 12 |
| D11 | aceito (isolado × tríade por execução, em `por-execucao.csv`) |
| D12 | aceito; registro que a senha ainda aparece no `ps` (argumento do `java` e `curl -u` a cada 5 s). VM de um usuário, aceitável; o `curl -K -` do B-33 evitaria o segundo, sem urgência |
| D13 | ver P1 |
| D14 | aceito |

Achados meus além dos pontos do Worker: o C2 (reproduzido numa cópia: `go f3 9999` → exit 5, nova
tentativa com o texto certo → `FIM exit=4 motivo=pre-voo: .../exec-1001-S1-sync-F3 existe sem
checagem.json`, diretório só com `portao.json`), o C4, o C5 e a intermitência da suíte no Git Bash.

## 8. Checklist do Gestor (seção 8 do relatório): segurança e clareza

A checklist está correta na ordem e não manda o Gestor injetar nada fora do piloto. Ajustes:

1. **Passo 4, acrescentar** (só leitura, sem injeção): `podman --version`; `flock --version`;
   `sudo podman inspect -f '{{.Created.Unix}} {{.State.StartedAt.Unix}}' isn1`;
   `sudo podman logs --since $(sudo podman inspect -f '{{.State.StartedAt.Unix}}' isn1) isn1 | grep -c ISPN080001`
   (esperado 1); `systemctl list-timers --all --no-legend 'tcc-heal-rb-*' | wc -c` (esperado 0). Se o
   último não der 0, parar e avisar o Senior antes do piloto.
2. **Passo 6 (ensaio de pré-voo):** rodar com `--saida runs/rebaseline-ensaio`, não com a saída real, para
   que `gates.json`, `runner.log` e `ambiente.md` do ensaio não se misturem aos brutos do piloto (e o
   ensaio não copie `matriz.tsv` para a pasta real).
3. **Passo 8, senha:** em vez de `export RB_PASSWORD=<senha>` digitado (fica no histórico do *shell*),
   `read -rs RB_PASSWORD && export RB_PASSWORD`.
4. **Antes do passo 8:** abrir a sessão com `tmux new -s rebaseline` e rodar o *runner* dentro dela (o
   *runner* pega o `flock` sozinho; não envolver o `tmux` em outro `flock` do mesmo arquivo, senão o
   *runner* sai com exit 4).
5. **`--cluster-criado-apos`:** anotar o instante UTC **antes** de subir o *cluster* depois do S7 (o
   *runner* exige `.Created` ≥ esse instante).
6. **Silêncio no *host*** (PR §5): nada de TLC, `mvn test` ou carga no Windows durante a noite. Vale pôr na
   checklist.
7. **Passo 10:** com o C2, a ordem passa a ser: o *runner* pede `go f3 1005`, o Gestor digita, o *runner*
   faz o K1 (≥ 60 s) e só então lança o *workload*. Escrever isso, para o Gestor não estranhar a espera.
8. **Passo 12 (netem residual), comandos explícitos:** conferir com
   `sudo nsenter -t $(sudo podman inspect -f '{{.State.Pid}}' isn2) -n tc qdisc show dev eth0`; se houver
   `netem`, `... -n tc qdisc del dev eth0 root`, conferir de novo e só então
   `sudo systemctl stop tcc-heal-rb-<i>.timer`. Nunca desarmar o dead-man antes de o netem sair.
9. **Passo 13:** o diretório `exec-*` sem `checagem.json` segue a regra de P3 decidida pelo Gestor, não um
   julgamento na hora.

## 9. Próxima tarefa do Worker (depois das respostas do Gestor)

Na mesma branch, commits novos (sem `--amend` do que já existe): C1 (se a DT-013 for aprovada), C2, C3,
C4, C5, a regra de P3 que o Gestor escolher, e os casos multi-execução da suíte a ×10/×20, com testes
vermelhos antes e mutantes para C1 (K8 só pela vida atual) e C2 (gate depois do `mkdir`). Depois, *rebase*
sobre `642d81f`, suíte completa, e o Senior valida a diferença antes da integração.

## 10. Perguntas ao Gestor

| # | Pergunta | Opção recomendada |
|---|---|---|
| Q1 | Aprovar a DT-013 (K8 por vida do processo: ≤ 1 na vida atual e total ≤ número de vidas pelo `ISPN080001`), registrada antes do piloto? | **Sim** |
| Q2 | Aplicar C2 (gate `go f3` antes do diretório e do K1), C3 (exits 1 e 4 recusa K6; 2, 3, 5 e outros exit 6), C4 e C5 na mesma branch antes da integração? | **Sim, todos** |
| Q3 | Regra para execução interrompida que sobrar depois do C2: antes do lançamento do *workload* o diretório é renomeado `.abortada-<UTC>` e o mesmo i roda de novo; depois do lançamento conta como RECUSADA "interrompida" e é reposta no fim. Registrar como DT antes do piloto? | **Sim** (alternativa: manter o exit 4 e decidir caso a caso, o que é decidir depois de ver dados) |
| Q4 | Integração: *rebase* linear da branch sobre `642d81f` e *fast-forward* de `feature/controlador-integracao`, depois das correções e de uma nova validação curta minha? | **Sim**, sem push |
| Q5 | Aceitar as mudanças da checklist (seção 8), em especial o ensaio de pré-voo com `--saida runs/rebaseline-ensaio` e a conferência de `systemctl list-timers` vazio antes do piloto? | **Sim** |

## 11. SHAs

| Item | SHA |
|---|---|
| Base da entrega | `9ddd5fe` |
| Ponta da entrega | `599ea5e` |
| Commits | `48ba062`, `4f2735b`, `efd01bd`, `bfb77f8`, `07a93d3`, `bfc3a0a`, `a60cd77`, `c501739`, `599ea5e` |
| `feature/controlador-integracao` | `642d81f` (B-43.5 integrado) |
| SHA-256 de `matriz.tsv` | `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35` |
