# Validacao V-R3: R.3 e R.4 da variante MCRelogio (B-36), 2026-10-16

Senior. Base: relatorio do Worker B (`wt-b36`, `test/b36-relogio-max`, `1c4a4f3`), tratado como alegacao. Conferido nos `.out` e `.cfg` brutos, em `scripts/audit_tracos.py` refeito e na leitura do codigo Java. Nada enviado a `origin`; VM nao tocada; nenhum `.tla` existente e nenhum `runs/tlc` existente alterado. O unico material novo de execucao esta em `runs/tlc/vr3-sid-livre/` (experimento de scratch do Senior, secao 5).

## Veredito

| Item | Veredito |
|---|---|
| (a) contagens e vereditos das celulas; "limite" nunca verde | **PASS** |
| (b) cfgs: `SEM_SIMETRIA`, cotas, so a linha `SPECIFICATION` difere | **PASS** |
| (c) auditoria dos tracos e mecanismo; `depende_D4 = NAO` | **PASS, com correcao de leitura** (o mecanismo de `I4F` nao e o de `I1F`; secao 3) |
| (d) `I2Fb` violada por P-27, independente do relogio | **PASS** |
| (e) timebox por propriedade | **ACEITO**, com rotulo (secao 4) |
| (f) Worker A inflou tempos? | **Nao afeta nenhuma conclusao** (secao 6) |
| R.3 / R.4 | **PASS com ressalvas** (secao 8) |

## 1. (a) Celulas, contagens e vereditos

Reli os `resumo-*.tsv` e os `.out` das 7 pastas (`relogio-livre-{sem,queda,particao}-{n1,n1b}`, `relogio-livre-sem-n2`). Os numeros do relatorio batem com os `.out` em todas as celulas (gerados / distintos, profundidade, tracos de 4/5/7/8 estados).

- 4 celulas com "No error has been found" e `0 states left on queue` (as unicas verdes): `I1P` e `RelogioDomina` em `r2`, cotas `2/3/2`, colunas `sem` (4.505.457 / 201.783, ambas) e `queda` (22.930.847 / 1.614.264, ambas). Dois checadores diferentes (propriedade de acao e invariante) fecham com a mesma contagem, o que e coerente.
- 10 celulas `SEM_VIOLACAO_ATE_AQUI`: `particao-n1` (`I1P`, `RelogioDomina`), as tres colunas do `n1b` (6) e `sem-n2` (2). **Nenhum** desses `.out` contem "No error has been found" (conferido por `grep -c`: 0) e terminam na ultima linha `Progress(...)`, sem `Finished`, isto e, morte por `timeout`. Em nenhuma tabela do relatorio elas aparecem como verde.
- 25 `VIOLADA` no tsv de `runs/tlc/relogio-livre-*`: 20 do R.3 (`I1F`, `I4F`, `I2Fb` em 6 celulas, mais `I1F`, `I4F` em `sem-n2`) e 5 do `sem-mut-w1` (R.2, controle com mutacao).
- Inventario: 4 `SEM_VIOLACAO` + 10 `SEM_VIOLACAO_ATE_AQUI` + 25 `VIOLADA` = 39, igual aos arquivos de resumo.

## 2. (b) cfgs

- Nenhuma cfg do R.3 tem `SYMMETRY` (`grep -l SYMMETRY` acusa so as 5 do `sem-mut-w1`, que e o controle R.2 com simetria, como o `prop-livre-sem-w1`). `SEM_SIMETRIA` foi aplicado.
- Cotas: `n1` e `n2` = `MaxCanal=2`, `MaxRelogio=3`, `MaxEventosFalha=2`; `n1b` = `3/5/2`; `LIMITE=2`; `REPLICAS` = {r1,r2} (n1, n1b) e {r1,r2,r3} (n2); constraint `SemFalha`/`SoQueda`/`SoParticao` conforme a coluna. Corretas.
- `diff` contra a base (`prop-livre-sem-n2m-sim/r3-*.cfg`, `prop-livre-queda-w1`, `prop-livre-particao-w1`), normalizando `SpecRelogio` para `Spec`: diferem apenas nas cotas e na `SYMMETRY` pedidas pelo plano e, onde cabe, em `REPLICAS`. **A linha `SPECIFICATION` e a unica diferenca estrutural** (`Spec` para `SpecRelogio`). Entre as proprias cfgs do R.3 as diferencas sao exatamente as da coluna/nivel.

## 3. (c) Auditoria dos tracos e mecanismo

- Refiz `scripts/audit_tracos.py` sobre `relogio-livre-*` em scratch: o `classificacao-d4.tsv` dos 20 tracos do R.3 e **identico** ao commitado (a menos de CRLF). O mesmo script classifica os 3 tracos do controle com mutacao (`sem-mut-w1`) como `SIM`: o classificador discrimina (controle positivo).
- Li o traco de `I1F` de `sem-n1b` linha a linha: `Login(r1,s1)`, `Login(r2,s2)`, `Logout(r1,s1)`, **`Login(r1,s2)`** (r1 ainda ve `s2` como `NONE`, ts 3), `Logout(r2,s2)` (ts 2, aceito), entrega a r2 do `SESSAO_VALIDA s2 ts=3`, que reescreve `INVALID ts=2` por `VALID ts=3`. O `Login` de `s2` ocorre em **duas replicas, concorrentemente**, porque o guard do `Login` e `sessoes[r][s].estado = "NONE"`, local. Todo `ts` local supera o do registro substituido: nenhum passo de gate rejeitado, `depende_D4 = NAO`. **Confirmado.**
- Li o traco de `I4F` de `sem-n1b` e o de `sem-n1`: ha dois `Login` da mesma `s1` em replicas distintas, **mas isso nao e necessario**. Hipotese minha, testada (secao 5): `Login(s1)`, `Login(s2)`, entrega de `s1` a r2, `Logout(r1,s1)` (ts 3), `Block(r2,u1)` (ts 2, `ADMIN_BLOCK`), entrega do `Logout` a r2 (ts 3 > 2) troca o motivo. E o mecanismo de `I4Fp` ja catalogado no semeado (`VIOLADA-POR-CONSTRUCAO`, 5 estados). **Correcao ao relatorio do Worker:** a leitura "login concorrente da mesma sid" vale para `I1F` (ressurreicao `INVALID` para `VALID`), nao para `I4F`.
- `particao` (`I1F`, `I4F`, 5 estados: `Login`, `Logout`, `Particionar`, `Mesclar`): a familia `Mesclar` ja catalogada (copia do registro do vencedor), independente do relogio.
- Observacao de fidelidade: a entrega do modelo escolhe qualquer indice do canal (nao e FIFO). No traco de `I1F` a mensagem entregue e a terceira da fila de r2; entregar as duas anteriores antes (mensagens de outra chave) produz o mesmo estado final para `s2`, entao o resultado nao depende da reordenacao.

## 4. (e) Timebox por propriedade

**Aceito.** Pedi "10 min por coluna"; o Worker aplicou 600 s por (coluna, propriedade) e 1800 s por propriedade no nivel 2, e o declarou. Efeito: so deu mais tempo; nenhuma celula fechou ou violou por causa disso que nao o faria com menos, e nenhuma "limite" virou verde. O custo foi de ~2h26 de parede, aceitavel. Rotulo para o catalogo: "limite de 600 s (1800 s no nivel 2) **por propriedade**".

## 5. Experimento de scratch do Senior: `sid` globalmente livre

Para testar a hipotese de que `I1F` depende de `Login` concorrente da mesma `sid`, criei `MCRelogioSL.tla` (so um modulo novo que `EXTENDS MCRelogio`; `Login` so dispara se a `sid` esta `NONE` em todas as replicas e nao ha mensagem em voo sobre ela). Nao toca nenhum `.tla` existente. Saidas em `runs/tlc/vr3-sid-livre/` (`.tla`, `.cfg`, `.out`). `r2`, cotas `2/3/2`, sem simetria, 4 workers.

| Propriedade | sem | queda | particao |
|---|---|---|---|
| `I1F` | **sem violacao, exaustivo**, 2.956.061 / 137.412 | **sem violacao, exaustivo**, 15.055.125 / 1.099.296 | VIOLADA, 5 estados (`Mesclar`) |
| `I1P` | sem violacao, exaustivo (mesmas contagens) | idem | nao fechou (300 s, `SEM_VIOLACAO_ATE_AQUI`) |
| `I4F` | VIOLADA, 7 estados | VIOLADA, 7 | VIOLADA, 5 |
| `I4Fp` | VIOLADA, 7 | VIOLADA, 7 | VIOLADA |

Leitura: com `sid` unica por login, `I1F` fecha **verde** em `sem` e `queda` (nestas cotas), o que atribui a ressurreicao a `Login` concorrente da mesma `sid`. `I4F`/`I4Fp` seguem violadas pelo traco Logout x Block (mecanismo diferente, ja catalogado). Coerente com o semeado auditado, onde nao ha `Login` e `I1F` e preservada em sem falha e queda. **Escopo:** `r2`, uma identidade, duas `sid`, cotas pequenas; e uma variante de modelo, nao o sistema.

## 6. (f) Worker A inflou tempos?

O relatorio declara atividade de Worker A (bash: `mutacao-gate-v4`, `l-gate.test.sh`, `l0c.sh`) e nenhum `java` concorrente; por isso `WORKERS=1`. Efeito possivel: so em tempo de parede e, logo, so em quais celulas atingiram o limite. As contagens de estados nao dependem disso, e todas as celulas exaustivas e todas as violadas sao independentes do tempo. As taxas de progresso do mesmo tipo de celula ficam estaveis ao longo das 2h26 (~10 M gerados/min em `n1b`/`sem-n2`, ~6 a 8 M/min em `particao`), sem salto que indique privacao de CPU alem de ~40% de variacao. **Nenhuma conclusao depende disso**: o que ficou "nao fechou" continua so "nao fechou"; nao se afirma nada sobre o que fecharia com mais CPU.

## 7. Decisoes

### 7.1 P-26: permanece `ABERTO-ACEITO` (nao reclassificar), com nota de escopo

A linha 1 da tabela 1.6 exigia `I1P`, `I1F` e `I4F` sem violacao e `RelogioDomina` verde; `I1F`/`I4F` violam, e a linha 3 vale fora de `r2`/cotas `2/3/2`. Portanto **nao** ha `FECHADO-POR-VARIANTE`. Nota de escopo que **pode** entrar no registro (texto sustentado): "com a variante `MCRelogio` (relogio acompanha o `ts` recebido), `I1P` e `RelogioDomina` fecham exaustivamente sem violacao em `r2`, cotas `2/3/2`, colunas sem falha e queda (`runs/tlc/relogio-livre-{sem,queda}-n1/`); nas demais celulas nao fecharam em 600 s (1800 s em `r3`). O defeito D-4 explica `I1P` **nestas cotas**; nao e demonstrado em geral."

### 7.2 P-28: abrir, com escopo estreito (`I1F` apenas)

**P-28 — `I1F` violada no modelo livre por `Login` concorrente da mesma `sid` em replicas distintas (independente de D-4).** Mecanismo e evidencia: secoes 3 e 5. Classificacao proposta: `CONFIRMADO-NO-MODELO`, **causa `artefato do modelo`** (o guard de `Login` e local e o espaco de `sid` e finito, entao duas replicas "criam" a mesma `sid`). Base para a classificacao `artefato` sem esperar o Gestor: o codigo nao tem esse mecanismo (secao 9). O Gestor ainda precisa confirmar que o protocolo (O1: "cria nova sessao") nao admite `sid` fornecida pelo cliente. `I4F` **nao** gera problema novo: no livre, sem falha e queda, passa a ser a mesma linha `VIOLADA-POR-CONSTRUCAO` do semeado (`I4Fp`); atualizar a linha da secao 5 do catalogo em T-G (coluna "D-4" passa a "nao" nas celulas da variante, status coincide com o semeado). A linha de partição segue como esta.

### 7.3 Nivel 3 e mais tempo

- **Nivel 3 (semeado + `max`): nao pedir.** O semeado ja tem traços independentes de D-4 (A.6) e o controle 3 do R.2 comparou contagens; o ganho seria so redundante.
- **Mais tempo/CPU: nao pedir.** O que a tese precisa (D-4 explica `I1P`; `I1F`/`I4F` tem causa propria) ja esta sustentado. Opcional, nao bloqueante: `particao-n1` (`I1P`, `RelogioDomina`) com `WORKERS=4` e 30 min, so se o Gestor quiser a coluna de particao fechada; `r3` nao vale o custo.

## 8. O que **nao** e sustentavel concluir

1. Nada sobre o sistema real a partir de `I1F`/`I4F` do modelo livre.
2. Que `I1P` esteja fechada em geral, ou `RelogioDomina` verde em `r3`/cotas `3/5/2`/particao: nao fechou.
3. Que D-4 "explique" `I1F`/`I4F` do livre: nas celulas fechadas (`RelogioDomina` verde) nenhum gate rejeita, logo as violacoes la sao D-4-livres; em celulas abertas nao se sabe.
4. Que `max` na entrega (Lamport) seja a formula do codigo: o codigo usa `versao + 1` do registro lido; so `RelogioDomina` e a propriedade comum (leitura, nao execucao).
5. Que o experimento de `sid` livre prove ausencia de `I1F` fora de `r2`/duas `sid`/uma identidade.
6. Que o resultado do scratch (secao 5) seja evidencia do sistema: e variante de modelo.
7. Que o smoke anterior ("`I1P` some em `r3`") esteja confirmado: aqui nao fechou em 1800 s.

Ressalvas do R.3/R.4: o relatorio do Worker acerta nos numeros e e honesto sobre limites; erra so na leitura do mecanismo de `I4F` (generalizou `Login` concorrente) e nao leu os demais tracos linha a linha (declarado).

## 9. Existe `Login` concorrente da mesma `sid` no codigo? (verificado sem o Gestor)

**Nao, por construcao.** Leitura: `SessionOps.login` (`workload/.../SessionOps.java`, linha 247 em `wt-b36`; linha 51 em `tcc-desenvolvimento/main`; linha 50 em `prototipo/`) faz `String sid = UUID.randomUUID().toString(); sessions.put(sid, SessionState.criar(...))`. Todos os caminhos de `Login` (`UsuarioVirtual.login`, `ContaLimiteS3`, `WorkloadMain:487`) passam por `ops.login`; nenhum recebe `sid` externa, e `UsuarioVirtual` so guarda a `sid` devolvida. Logo duas replicas/servidores de aplicacao nunca executam `Login` da **mesma** `sid` (colisao de UUID v4 desprezivel). O mecanismo de `I1F` do modelo livre, `INVALID` para `VALID` por segundo `Login` da mesma `sid`, **nao existe no S1/S2/S3**: a unica escrita `VALID` de uma `sid` e a criacao inicial, e `invalidar` e idempotente (retorna `this` se ja `INVALID`).

Ressalva: e leitura de codigo, nao evidencia de execucao; a evidencia de execucao e o auditor `InvariantAuditor`/B-33, que ja mede `I1` no sistema real. Ressalva 2: o caminho `Logout x Block` **existe** no codigo (`logout` e `block` sao read-then-put, sem CAS nem versao na escrita): se manifesta no cluster depende de o Infinispan serializar no dono primario e de o workload (logout de usuario e `block` administrativo na mesma identidade) coincidir; isso e do auditor, nao do modelo. Verificacao sem Gestor: consultar nos CSVs de `runs/` (campos de `I4`/`registrarViolacao`) se algum `I4` real ocorreu e comparar com a linha `I4Fp` do catalogo; nao fiz aqui.

## 10. Proximos passos (para o Senior/T-G, sem implementar aqui)

- T-G: registrar P-28 (secao 7.2) e a nota de escopo de P-26 (7.1); atualizar a linha da secao 5 do catalogo; incluir o rotulo do timebox (secao 4).
- Gestor: confirmar que O1 nao admite `sid` externa (uma frase); aprovar o texto de P-26/P-28.
- Nenhuma tarefa nova para o Worker B alem de manter `wt-b36` como fonte dos brutos; o merge dos brutos fica na ordem local ja decidida.
