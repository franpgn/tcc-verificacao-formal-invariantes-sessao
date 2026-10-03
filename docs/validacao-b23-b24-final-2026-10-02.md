# Validação final de B-23/B-24 (P-19 resolvido, P-21, P-22) e recomendação de próximo passo

> Elaborado pelo Senior em 2026-10-03 (sessão iniciada 2026-10-02), validando os 4
> commits finais de `feature/b23-auditoria-i1-i4` (`26fcff9`, `cc23f03`, `cadc90d`,
> `dd6a5e4`), que aplicam as correções recomendadas em
> `docs/validacao-b23-b24-2026-10-02.md`. Toda afirmação abaixo foi cruzada contra o
> `diff` real dos 4 commits (lido linha a linha, não só o relato), contra os `.csv`
> brutos das baterias reais, e contra uma suíte JUnit rodada por mim mesmo nesta
> sessão. **Só validação — nenhum arquivo de código/`spec/*.tla` foi alterado.**

---

## 1. A correção de P-21 está logicamente correta (li o diff, não só o relato)

Reli `InvariantAuditor.verificarI5`/`verificarI6` (commit `cadc90d`) linha a linha.
Estrutura da correção, para cada chave `k`:

```java
SessionState ref = null;
for (snapshot em todos os leitores) {
    s = snapshot.get(k);
    if (s == null) continue;          // nao-owner deste segmento -- ignora, nao e divergencia
    if (ref == null) { ref = s; continue; }  // primeira ocorrencia nao-nula vira a referencia
    if (s difere de ref em estado/motivo/identidade) { registrar violacao; break; }
}
```

Pontos que verifiquei especificamente, não presumidos:

- **Correção do bug relatado:** se só 1 dos 3 leitores tem a chave (o padrão normal
  com `owners=2` de 3), o `for` só encontra UM valor não-nulo, nunca entra no ramo de
  comparação (`ref==null` na única iteração não-nula) — nenhuma violação é registrada.
  Isso é exatamente o comportamento que o `entrySet()` sem filtro nunca exercitava (lá,
  todo leitor sempre tinha todas as chaves) e que `entrySet(IntSet)` (correção de P-19)
  passou a exercitar sempre — confirma a causa-raiz do Worker.
- **Não há perda de detecção real:** comparar cada leitor subsequente só contra o
  **primeiro** valor não-nulo encontrado (`ref`), em vez de contra todos os pares, é
  matematicamente suficiente — por transitividade da igualdade, se todo leitor bate
  com `ref`, todos batem entre si; se um só diverge de `ref`, a violação é
  detectada de qualquer forma. (O código original, pré-P21, já usava esse mesmo padrão
  de "comparar contra o primeiro" — não é uma mudança de técnica, só de o que conta
  como "ausente".)
- **Os 3 testes novos** (`rodadaUnicaNaoRegistraI5ParaChaveAusenteEmApenasUmaReplica`,
  o equivalente para I6, e `rodadaUnicaDetectaI5ComTresReplicasEUmaNaoOwner`) cobrem
  exatamente os dois lados do bug: ausência por não-posse não é mais falso-positivo
  (dois primeiros testes), e divergência real entre dois leitores que **de fato**
  possuem a chave continua detectada mesmo com um terceiro leitor não-*owner* no meio
  (terceiro teste — garante que a correção não "superdescartou" detecção real junto
  com os falsos-positivos). Rodei a suíte eu mesmo nesta sessão
  (`mvn -o test` em `feature/b23-auditoria-i1-i4`, HEAD `dd6a5e4`): `Tests run: 58,
  Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS` — confirma de forma independente.

**Veredito: correção logicamente correta, bem testada, sem lacuna identificada.**

---

## 2. Extensão de I5 (estado+motivo+identidade) — upgrade correto, da mesma categoria já validada para I6, não uma mudança nova a parte

Reconfirmei `spec/SessionStore.tla` (lido em detalhe na validação de 02/10):

```
I5 ==
  Quiescent =>
    \A s \in SIDS, r1, r2 \in REPLICAS:
      /\ sessoes[r1][s].estado     = sessoes[r2][s].estado
      /\ sessoes[r1][s].motivo     = sessoes[r2][s].motivo
      /\ sessoes[r1][s].identidade = sessoes[r2][s].identidade
```

A definição formal **sempre** comparou os três campos (confirmado nas duas versões,
antes e depois da revisão do Achado E — mesmo padrão que já tinha confirmado para I6 na
validação anterior). A extensão do `InvariantAuditor.verificarI5` para comparar os
três campos é **a mesma categoria de correção** que já validei e aprovei para I6
(`docs/validacao-b23-b24-2026-10-02.md`, que por sua vez endossou o fix de `0094cab`) —
não é uma mudança de desenho nova, é "fazer o Java bater com o TLA+ que já existia",
exatamente como antes. **Não precisava de validação separada — é uma correção da mesma
natureza já aprovada.**

**Nuance a registrar (não bloqueante):** `tcc-escrita/Metodologia.tex` (linha 123-126)
define I5 como `σ_{r1}(s) = σ_{r2}(s)` — igualdade do "estado lógico" como um valor
único, não três campos separados. Pela notação de I4 no mesmo texto (`σ_t(s) =
Invalid(m)`, onde `σ(s)` já é um valor "tagueado" que carrega o motivo `m` dentro de
si), é plausível que `σ(s)` no texto já represente o par (estado, motivo) como um valor
composto — nesse caso, a extensão para `motivo` é consistente com a notação, só
tornada explícita. Não encontrei a mesma garantia para `identidade` (não há notação
equivalente que deixe claro se `σ(s)` também carrega a identidade do usuário). Diferente
do caso de I6 (onde o texto é inequívoco: só `β.counter`, sem menção a `β.marca`), aqui
a leitura é mais ambígua, não claramente contraditória — registro para completude, não
como um novo bloqueio.

---

## 3. Leitura sobre os 41s de `counters` — raciocínio procede; corroborado por um teste de consistência lógica, não por reprodução em vivo

**Não reproduzi este experimento especificamente com uma terceira sonda ao vivo** nesta
sessão (decisão de orçamento de tempo, não omissão) — mas fiz uma verificação de
consistência lógica que corrobora a explicação do Worker, em vez de só aceitá-la por
plausibilidade:

**O teste que fiz:** se a explicação do Worker estivesse errada e os ~41s fossem, na
verdade, um atraso estrutural do cluster (ex.: ligado ao ciclo do `MERGE3` do JGroups,
que roda a cada 10-30s — `jgroups-tcp.xml`, confirmado na investigação do Achado E),
esse atraso afetaria a visão de **qualquer** *cache* igualmente, porque o canal JGroups
subjacente é o mesmo para `sessions` e `counters`. Mas `sessions` convergiu em apenas
**4,1s** na mesma rodada, mesmo evento de *crash*, mesmo canal. **Essa diferença entre
as duas *caches* é incompatível com uma explicação de atraso estrutural do cluster** (que
teria de afetar as duas igualmente) **e é exatamente o que a explicação do Worker
prevê** (o artefato depende da taxa de escrita e cardinalidade específicas de
`counters` — 4-8 chaves sob escrita contínua muito frequente — não de algo do cluster
em si). Isso não é uma reprodução independente, mas é uma corroboração real, não só
aceitação por plausibilidade.

**Avaliação do raciocínio em si:** a lógica "leitura sequencial não-atômica (`local`
depois `referência`, com milissegundos de intervalo) sob escrita contínua de centenas
de incrementos/segundo em 4-8 chaves faz `referência` quase sempre capturar uma versão
maior só pelo tempo decorrido" é um artefato de medição bem conhecido (*race* entre
duas leituras sequenciais contra um valor em movimento rápido) e plausível na ordem de
grandeza certa. **Aceito o raciocínio como correto.**

**Acréscimo meu, não do Worker:** a causa-raiz deste artefato é, na prática, a mesma
razão pela qual o plano de 24/09 (§3.1) já havia identificado que `Quiescent` do TLA+
("canal vazio") precisa ser **operacionalizado por aproximação** em *runtime*, e que a
aproximação óbvia ("nenhuma escrita recente") tem uma lacuna. A medição de convergência
para calibrar `W_quiesc` deveria, ela mesma, **pausar a geração de novas escritas**
antes de comparar `local` vs. `referência` — não continuar escrevendo durante a medição.
Isso não é só "uma correção de metodologia de medição", é a aplicação direta do próprio
conceito de quiescência que o controlador vai precisar — uma medição de convergência
feita sob escrita contínua está, por construção, medindo a coisa errada. Recomendo essa
reformulação explícita (pausar escrita, então medir) para a remedição futura, não só
"uma leitura pareada/atômica" como o Worker sugeriu (que ajudaria, mas não elimina o
problema de fundo enquanto a carga continuar escrevendo).

---

## 4. ~4,1s é um primeiro dado real válido, mas cedo para fixar `W_quiesc` — um único cenário, carga ad-hoc

**Avaliação:** o número (~4,1s, dos quais ~3,2s são *boot* de contêiner e <1s é
*catch-up* de replicação real) é um dado genuíno e valioso — a primeira vez que o
projeto tem uma medição real, não um valor ancorado por analogia (o `W_quiesc=80s`
provisório do plano de 24/09 era "2×T16", uma convenção, não uma medição). Isso já
mostra que 80s é quase certamente uma sobrestimativa grande para o componente de
*catch-up* de replicação em si — mas:

1. **Um único cenário.** Medido 2x, mas na mesma bateria/configuração (*crash* de 20s,
   *smoke test* de 120s, 8 *threads*, cenário S1) — não varia duração do *crash*, não
   varia carga, não usa a bateria real de 3×90s (a que vale para o Cap. 5).
2. **`sessions` com baixa cardinalidade de escrita por chave** (cada sessão é escrita
   poucas vezes no seu ciclo de vida O1→O2*→O3) é estruturalmente diferente de
   `counters` (poucas chaves, escrita repetida e intensa) — exatamente a característica
   que, por §3, já se mostrou produzir resultados muito diferentes entre as duas
   *caches*. Não há garantia de que 4,1s generalize para outro perfil de carga.
3. **Não teve a correção metodológica de §3 aplicada** (pausar escrita antes de medir)
   — mesmo que o resultado de `sessions` pareça mais confiável que o de `counters`
   (a diferença entre as duas já é evidência disso), não foi confirmado que o mesmo
   artefato de §3 não influencia `sessions` em menor grau (só em grau menor porque a
   taxa de escrita por chave é menor, não porque a medição em si seja diferente).

**Recomendação: cedo para fixar um valor final.** Mas o dado já é suficientemente forte
para **revisar a ORDEM DE GRANDEZA** do provisório: em vez de "80s, ancorado em T16
por convenção", propor algo como "a confirmar entre 5-15s para o componente de
*catch-up*, mais o tempo de detecção da falha em si (que T16/T17 já cobrem
separadamente)" — mas isso ainda **precisa** da remedição com a correção metodológica
de §3, contra as *caches* reais, com a carga real da bateria, antes de qualquer valor
entrar no plano do controlador como definitivo.

---

## 5. Próximo passo recomendado: B-22 (falhas explícitas no TLA+), não o controlador ainda

Com B-23/B-24 agora genuinamente fechados (P-19 e P-21 resolvidos, verificados por mim
de forma independente), a pergunta é se avançar para o controlador heurístico (B-26+)
ou voltar para B-22 (modelar F1/F2 explicitamente no TLA+, Etapas 3-4). **Recomendo
B-22 primeiro.** Razão central, que a própria bateria desta rodada acabou de
demonstrar:

- **O controlador existe para reagir a `taxa_violacao` acima de um limiar** (texto do
  cronograma, `ApPlanoTCC2.tex`). Mas a bateria F1 real desta rodada — a primeira com o
  mecanismo de leitura genuinamente correto (pós P-19 e P-21) — deu **0 violações**,
  exatamente como o Achado E já previa: F1 (1 de 3 réplicas cai, `owners=2`) **nunca**
  ativa `DEGRADED`/`merge-policy`, não há mecanismo nenhum, nessa falha, que produza
  uma violação de I1-I4, e a convergência de I5/I6 é rápida (§4). **F3 (jitter) não
  deve se comportar diferente nesse aspecto** (não é uma falha de particionamento).
  Ou seja: com o conjunto de falhas disponível hoje (F1, F3), é esperado que o sistema
  **nunca** produza uma violação real — o controlador não teria nenhum sinal genuíno
  para reagir, só a "proposta-ponte" de p99 de latência (que o plano de 24/09 já
  advertia que muda a semântica do que o texto promete, e que o Gestor nunca aprovou
  explicitamente).
- **Só F2 (partição) tem, pelo próprio Achado E, um mecanismo real que pode produzir
  violações** (`PREFERRED_NON_NULL`/`PREFERRED_ALWAYS`, maioria vence por registro
  inteiro — Achado E, item A.1-A.3). F2 não tem modelo formal (B-22, Fases 4-5 da
  revisão do Achado E, ainda não feitas) nem injetor real (`inject-partition.sh` não
  existe em nenhuma branch, confirmado repetidamente desde o plano de 24/09).
- Construir o controlador agora significa testá-lo contra um sistema que, pelas
  falhas disponíveis, não tem como mostrar o fenômeno que o controlador deveria
  detectar — um desenvolvimento "as escuras", sem dado real para validar que o
  controlador funciona para o que o texto promete.
- B-22 (Fases 4-5 da revisão do Achado E, já desenhadas em
  `docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md` §C.2 — ação de partição
  `Particionar`/`Mesclar`, maioria vence por registro inteiro, sem *timestamp*) é a
  continuação natural do trabalho já feito (Fases 1-3), não um retrocesso — e é o que
  preenche o catálogo operação×falha×invariante (plano de 24/09 §3.2), hoje
  inteiramente `PENDENTE`.

**Recomendação:** priorizar B-22 (modelo formal de F2 + `inject-partition.sh`) antes de
B-26+ (controlador). Isso é uma recomendação de sequenciamento, não uma decisão
unilateral — precisa confirmação do Gestor (ver §7), inclusive porque reordena
etapas do cronograma escrito (Etapas 3-4 "depois" das 7-8 na prática real do projeto,
já discutido e aceito implicitamente nas rodadas anteriores).

---

## 6. Outras inconsistências encontradas

Nenhuma de peso, além das já registradas (§2, nuance sobre `identidade` em I5 vs.
`Metodologia.tex`). Dois registros menores:

- A terceira bateria F1 real (`S1-F1-b24fix-smoke3`) teve ~3% menos operações
  (2.898.767) que as outras duas (2.980.721/2.968.590) — plausível (a janela de
  *crash* de 20s reduz o throughput agregado durante a falha; variação de ~3% é
  pequena, não é um sinal de problema). Confirmado real via `rep-001.csv` com 2,4-2,5M
  linhas em cada um dos 3 diretórios (`runs/S1-F1-b24fix-smoke{,2,3}/`), todos
  corretamente *gitignored* (`runs/**/*.csv`), `git status` limpo.
- Nenhum vestígio da sonda `ConvergenceProbe` do Worker ficou no repositório (mesmo
  padrão que os meus próprios probes da rodada anterior) — confirmado.

---

## 7. Veredito final

| Pergunta | Resposta |
|---|---|
| 1. P-21 logicamente correta? | **Sim** — lida linha a linha, matematicamente sólida (comparação contra referência única é suficiente por transitividade), testes novos cobrem os dois lados do bug, suíte 58/58 reconfirmada por mim. |
| 2. Extensão de I5 correta/esperada? | **Sim**, mesma categoria já aprovada para I6 — bate com `spec/SessionStore.tla`, não precisava de validação separada. Nuance sobre `identidade` vs. `Metodologia.tex` registrada, não bloqueante. |
| 3. Leitura dos 41s de `counters` procede? | **Sim** — corroborada por um teste de consistência lógica (diferencial `sessions` vs. `counters`, incompatível com explicação de atraso estrutural do cluster); não reproduzida ao vivo nesta sessão (orçamento de tempo). Acréscimo: a remedição futura deveria pausar a escrita antes de medir, não só usar leitura pareada. |
| 4. ~4,1s suficiente para `W_quiesc`? | **Ainda cedo** — um só cenário, carga ad-hoc, sem a correção metodológica de §3 aplicada. Já é evidência de que 80s é sobrestimativa, mas não deve virar valor final sem remedição contra as *caches*/carga reais. |
| 5. Próximo passo? | **B-22** (modelo formal de F2 + injetor real) antes do controlador — a bateria desta própria rodada confirma que F1/F3 não produzem violação real, por desenho (Achado E), então o controlador não teria sinal genuíno para reagir sem F2. |
| 6. Outras inconsistências? | Nenhuma de peso. |

**B-23/B-24 estão aprovados como estão — prontos, sem necessidade de correção
adicional.** A recomendação de próximo passo (B-22 antes do controlador) é uma
recomendação de sequenciamento para o Gestor decidir, não um bloqueio ao trabalho já
feito.

---

## Pontos que precisam de validação do Gestor

1. Confirmar B-23/B-24 como concluídos — aprovado pelo Senior, sem bloqueio.
2. Confirmar a recomendação de priorizar B-22 (F2 no TLA+ + `inject-partition.sh`)
   antes do controlador heurístico (B-26+) — recomendação de sequenciamento, não
   decidida unilateralmente.
3. Decidir se vale uma remedição futura de `W_quiesc` (pausando escrita antes de medir,
   contra as *caches* reais, com a carga real da bateria) antes de qualquer valor
   entrar no desenho do controlador.
4. Como sempre: nada vai a `origin/*` sem aprovação explícita.

## Próxima tarefa sugerida para o Worker

Se o Gestor confirmar o ponto 2: iniciar B-22 a partir de
`docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md` §C.2 Fases 4-5 (ação de
partição `Particionar`/`Mesclar` em `spec/SessionStore.tla`, maioria vence por registro
inteiro sem *timestamp*; depois, `scripts/inject-partition.sh`, que não existe em
nenhuma branch hoje). Se o Gestor preferir o controlador mesmo assim, registrar
explicitamente a decisão de usar a "proposta-ponte" de p99 de latência como gatilho
provisório (plano de 24/09 §5.1), já que não há, hoje, nenhuma falha disponível que
produza violação real para o gatilho primário reagir.
