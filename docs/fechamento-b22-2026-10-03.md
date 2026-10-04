# Fechamento de B-22 e recomendação de próximo passo

> Elaborado pelo Senior em 2026-10-03, validação final de encerramento (não uma nova
> rodada de verificação técnica profunda) sobre `8e2e815` (checkpoint TLC 600s,
> `feature/b22-f2-particao`). Lido numa *worktree* isolada, removida ao final. **Só
> validação e recomendação — nenhum `spec/*.tla` foi alterado.**

---

## 1. A correção do Worker está certa — e muda a avaliação, mas não a decisão

Conferi diretamente a série completa de `Progress` (10 pontos, não só os 4 que
motivaram o teste de 600s) em `runs/tlc/inv-small/small-TypeOK.out` e
`runs/tlc/inv-medium/medium-TypeOK.out`, e os pares 300s→600s para
`small-I3`/`medium-I4` (amostra adicional, não só `TypeOK`):

- **Fila:** cresce **monotonicamente** do início ao fim em `small` (147.401 →
  9.510.590) e em `medium` (110.728 → 10.589.528) — sem platô em nenhum dos dois.
  Confirmado, lendo os números eu mesmo.
- **Taxa de descoberta (`ds/min`):** em `small`, a série é
  4.315.308 → 3.656.173 → 3.339.777 → 3.161.465 → 3.210.212 → 2.091.031 → 2.682.710 →
  3.263.544 → 2.883.093 — cai nos primeiros quatro pontos (o que motivou minha leitura
  anterior, baseada só nesses quatro) e depois **oscila sem tendência clara**, inclusive
  subindo. Em `medium` a oscilação é mais branda, mas também não é monotonicamente
  decrescente.
- **Crescimento 300s→600s:** recalculei a partir dos `.out` — `small`/`TypeOK`:
  distintos ×1,970, fila ×1,651; `medium`/`TypeOK`: distintos ×2,011, fila ×1,817;
  `small`/`I3`: distintos ×1,997, fila ×1,660; `medium`/`I4`: distintos ×2,116, fila
  ×1,949. **Aproximadamente linear no tempo** (dobrar o tempo ~dobra os estados
  distintos) — exatamente o padrão que o Worker descreveu, confirmado com números
  reais, não só a caracterização textual do commit.

**Isso não é o que eu esperava quando recomendei o teste.** Crescimento linear
sustentado (sem desaceleração líquida) é a assinatura de uma busca que está longe de
se aproximar da exaustão, não de uma busca "quase terminando" — minha leitura anterior
(baseada em só 4 pontos de uma janela mais curta) estava **incompleta**, e o Worker
tem razão em corrigi-la.

**Isso muda a avaliação, mas não a decisão.** Meu raciocínio para recomendar o teste
de 600s tinha duas pernas: (a) o sinal parecia indicar convergência — **isto estava
errado, corrigido agora**; (b) mesmo que não convergisse, o catálogo não depende de
`TypeOK`/`I3`/`I4` fecharem exaustivamente em `small`/`medium`, porque I1/I2/I4/I5/I6
(o que o catálogo precisa) já são obtidos de forma rasa e independente disso — **esta
perna continua válida**, e é ela que sustenta a decisão de aceitar agora, não a
primeira. Não deveria ter sido pedido mais um teste (1200s, por exemplo): dado o
crescimento aproximadamente linear já observado em 600s, outro teste só replicaria o
mesmo padrão a um custo maior, sem mudar a decisão. **Aceitar como limitação
documentada, agora, é a decisão correta** — não por "a busca estava quase
convergindo", mas porque a tratabilidade de `TypeOK`/`I3`/`I4` em `small`/`medium`
nunca foi, de fato, necessária para o que esta rodada precisava entregar.

---

## 2. Checagem final de B-22 contra os critérios de aceite do plano original

Item a item, `docs/plano-b22-falhas-explicitas-2026-10-03.md` §5:

| # | Critério | Status |
|---|---|---|
| 1 | Decisão sobre F1 (`Cair`/`Reviver`) confirmada | **Satisfeito** — testado, não presumido; sem violação nova (`docs/validacao-b22-f1-2026-10-03.md`) |
| 2 | Desenho de `Particionar`/`Mesclar` confirmado (`Vencedor` sem `topologyId`, `full` como referência) | **Satisfeito** — implementado fielmente, produz o primeiro contraexemplo real confirmado (I5/I6, `docs/validacao-b22-f2-2026-10-03.md`) |
| 3 | Opção A vs. B para P-16 | **Satisfeito** — Opção A confirmada pelo Gestor, `canal`/`Broadcast`/`EntregaMensagem` preservados, P-16 continua documentado, não resolvido (decisão deliberada) |
| 4 | `MaxEventosFalha` e valor inicial | **Satisfeito, e revisado** — `1` aceito, depois estendido para `quedas + particoes` (P-25), com resultado real registrado |
| 5 | Esboço de `inject-partition.sh` como referência | **Satisfeito** — esboçado (§3 do plano), não implementado (nunca foi esse o critério) |
| 6 | F1/F2 mutuamente exclusivos como escopo | **Satisfeito, e corrigido** — P-24 fechou a lacuna unidirecional, exclusão agora bidirecional de fato |

**Além dos critérios originais, dois achados adicionais foram devidamente tratados:**
P-23 (recaracterização: o achado real é I5/I6, não I1/I2) e a atualização do catálogo
operação×falha×invariante (`docs/plano-controlador-heuristico-2026-09-24.md` §3.2,
commit `5450cd7`) com o mecanismo preciso.

**Veredito: todos os 6 critérios de aceite do plano original estão satisfeitos. B-22
está encerrado nesta rodada — confirmado.** `TypeOK`/`I3`/`I4` inconclusivo em
`small`/`medium` é uma limitação real e agora bem caracterizada (crescimento linear,
não um mistério), documentada no mesmo padrão já aceito para `full`, e nunca foi um
critério de aceite à parte.

---

## 3. Recomendação de próximo passo: `scripts/inject-partition.sh` (B-29), antes do controlador

Avaliando os três candidatos:

**(a) Combinar F1+F2 no modelo:** extensão teórica legítima (um membro da maioria cai
durante uma partição, por exemplo), mas sem nenhum requisito normativo
(`Metodologia.tex`/`ApPlanoTCC2.tex`) que a exija, e sem um mecanismo de risco
claramente novo a descobrir — a minoria já fica congelada por F2 isoladamente, de
forma semelhante a cair por F1. Valor incerto frente ao custo de mais uma dimensão de
estado. **Baixa prioridade.**

**(b) `scripts/inject-partition.sh` (B-29):** conecta o achado formal real (F2×I5/I6,
confirmado pelo TLC nesta rodada) a um dado experimental real — exatamente o que a
Atividade 1 (alinhamento formal-experimental, `docs/plano-controlador-heuristico-2026-09-24.md`
§3) sempre definiu como objetivo, e que `docs/validacao-b22-f2-2026-10-03.md` §4 já
propôs como entrada de catálogo **formal**, ainda sem contraparte real. Sem isso, não
se sabe se a divergência permanente que o TLC encontrou (escolha de `rf`
não-convergido no `Mesclar`) tem *algum* correspondente observável no Infinispan real,
ou se é um artefato específico da abstração do modelo (ex.: talvez o Infinispan real
sempre espere convergência intra-partição antes de permitir o *merge*, o que o modelo
não assume). **Essa é exatamente a pergunta que só um injetor real pode responder.**

**(c) Controlador heurístico (B-26+):** era o objetivo original que motivou priorizar
B-22 — e a motivação específica ("sem F2, o controlador não teria sinal genuíno para
reagir") **está, sim, superada**: agora existe um mecanismo de violação real e
confirmado (F2×I5/I6). Mas o gatilho primário do controlador (`taxa_violacao`, texto do
cronograma) precisa de dado **real** de violação, não só de um contraexemplo do TLC —
e esse dado real só existe depois de (b). Construir o controlador agora significa
testá-lo contra a mesma lacuna que motivou toda esta rodada: um sinal formal sem
contraparte experimental.

**Recomendação: (b) antes de (c), e (a) fica para depois, se necessário.**
`scripts/inject-partition.sh` é o item que transforma o achado desta rodada (formal,
confirmado pelo TLC) no primeiro dado real de F2 para o catálogo e, por consequência,
no primeiro sinal genuíno e real que o controlador poderia de fato usar. Pular direto
para o controlador repetiria, com F2, o mesmo problema que esta rodada inteira
resolveu para F1/F3: construir o mecanismo de reação antes de ter o que reagir.

---

## 4. Veredito final

| Pergunta | Resposta |
|---|---|
| 1. Correção do Worker certa, muda algo? | **Correção certa** (crescimento linear, não convergente) — muda a leitura do *porquê*, não a decisão de aceitar. Não deveria ter sido pedido mais um teste. |
| 2. B-22 encerrado, critérios satisfeitos? | **Sim**, confirmado item a item (§2) — 6 de 6 critérios do plano original satisfeitos. |
| 3. Próximo passo | **`scripts/inject-partition.sh` (B-29)** antes do controlador (B-26+); combinação F1+F2 (a) fica para depois, sem urgência. |

---

## Pontos que precisam de validação do Gestor

1. Confirmar o encerramento de B-22 nesta rodada — aprovado pelo Senior.
2. Confirmar a recomendação de priorizar `inject-partition.sh` (B-29) antes do
   controlador heurístico.
3. Como sempre: nada vai a `origin/*` sem aprovação explícita.

## Próxima tarefa sugerida para o Worker

Se o Gestor confirmar o ponto 2: iniciar B-29 a partir do esboço já registrado em
`docs/plano-b22-falhas-explicitas-2026-10-03.md` §3 (parâmetros, mecanismo via `podman
network disconnect`/`connect`, semântica `DENY_READ_WRITES`) — **mas antes disso, o
Senior deve produzir um plano técnico dedicado de B-29**, no mesmo padrão dos planos
anteriores (B-21, B-22), já que o esboço atual é só uma referência inicial, não um
plano de implementação completo. Recomendo que essa seja a próxima tarefa do Senior,
não do Worker diretamente.
