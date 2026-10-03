# Validação de P-24/P-25 e decisão sobre `small`/`medium` — fechamento de B-22 nesta rodada

> Elaborado pelo Senior em 2026-10-03, validando `96fcf3c` (P-24), `b7d8e0a`
> (`particoes`, P-25) e `32af7f8` (checkpoint TLC) na branch `feature/b22-f2-particao`.
> Lido numa *worktree* isolada (`_worktrees/senior-validacao-b22`, removida ao final),
> adotando a recomendação do próprio Worker após a colisão de checkout da rodada
> anterior. **Só validação e decisão de próximo passo — nenhum `spec/*.tla` foi
> alterado.**

---

## 1. P-24 — confirmado, fiel à recomendação

Diff de `96fcf3c` lido linha a linha: `Cair(r)` ganhou exatamente
`/\ ~particaoAtiva`, uma linha, no mesmo padrão que `Particionar` já usava na direção
oposta (`\A r: vivo[r] = TRUE`). `Reviver` não foi alterado — correto, consistente com
minha própria justificativa anterior (reverter um `Cair` já ocorrido não envolve
nenhuma leitura nova de `rf`, não cria a mesma ambiguidade que motivou a correção).
Comentários no código e no cabeçalho do módulo atualizados para descrever a exclusão
mútua como bidirecional de fato, não só na intenção. **Aprovado, sem ressalva.**

---

## 2. P-25 — números confirmados de forma independente, recomendação de próximo passo abaixo

Li os `.out` reais (não só os números do commit) nas duas versões — antes (`dc64154`)
e depois (`32af7f8`) de `particoes`:

| Cardinalidade | Antes (distintos / fila, corte 300s) | Depois (distintos / fila) | Redução |
|---|---|---|---|
| `tiny` | inconclusivo (~36,6M, nunca medi eu mesmo o valor exato do corte anterior — o commit já registrava isso) | **570.089 / 0** — fecha exaustivo em 7s | Recupera fechamento total |
| `small` | 22.250.191 / 13.327.354 | 14.634.296 / 5.759.450 | ~34,2% menos distintos; ~56,8% menos na fila |
| `medium` | 13.894.296 / 9.270.857 | 10.467.191 / 5.831.269 | ~24,7% menos distintos; ~37,1% menos na fila |

Todos os quatro números "depois" e os dois pares "antes" (`small`/`medium`) foram lidos
por mim diretamente dos arquivos `runs/tlc/inv-*/`, não copiados do relato — conferem
exatamente com as proporções citadas pelo Worker. Confirmei também, no mesmo
*checkpoint*, que `I1`/`I2`/`I5`/`I6` mantêm o mesmo veredito de antes (mesma violação,
contagens de estado ligeiramente diferentes por não-determinismo de busca, nenhuma
delas envolvendo `Cair`/`Reviver`/`Particionar`/`Mesclar` nos casos de `I1`/`I5` que
reli) — consistente com "sem mudança".

**A hipótese do Senior está parcialmente confirmada, com um padrão informativo:** a
fração de redução na **fila** (o que falta explorar) é consistentemente maior que a
fração de redução nos **distintos** (o que já foi explorado) — 57% vs. 34% em `small`,
37% vs. 25% em `medium`. Isso é coerente com a hipótese de que limitar `particoes`
elimina especificamente os ramos de busca mais *profundos* (vários ciclos
`Particionar`→`Mesclar` em sequência, que geram muito mais trabalho pendente do que
estados novos de fato distintos) — exatamente o mecanismo que eu tinha hipotetizado,
não apenas uma redução genérica de tamanho.

---

## 3. Decisão sobre `small`/`medium`: tentar um *timeout* maior primeiro (barato), depois aceitar — não reduzir `MaxCanal`/`MaxRelogio`

Avaliando as três opções levantadas:

**Rejeito a opção 2 (reduzir `MaxCanal`/`MaxRelogio` para essas cardinalidades).**
Isso reduziria a fidelidade exatamente do mecanismo que o modelo existe para verificar
(reordenação de mensagens, gate por `ts`) — e `tiny` já demonstra, nos parâmetros
**atuais** (`MaxCanal=1`, `MaxRelogio=3`), que o mecanismo fecha exaustivamente com
`particoes` limitado. Não há necessidade identificada de sacrificar fidelidade só para
`small`/`medium` fecharem mais rápido, quando já existe uma cardinalidade que fecha sem
esse sacrifício.

**Recomendo tentar a opção 1 primeiro, com um valor moderado (600s, não mais) — e só
então, se não fechar, aceitar como na opção 3.** Razão para tentar: o padrão observado
(fila encolhendo mais que os distintos, taxa de descoberta de estados novos por minuto
caindo monotonicamente ao longo da execução — `ds/min` de `small` passou de 4,37M para
3,17M nos quatro intervalos registrados) é consistente com uma busca que está
**convergindo**, não estagnada — vale a pena confirmar com mais tempo antes de
desistir, e dobrar o *timeout* (300s → 600s) é uma tentativa de custo baixo (minutos de
execução, nenhuma mudança de código). Razão para **não** insistir além disso se 600s
não bastar: o catálogo (o que realmente importa desta rodada) não depende de
`TypeOK`/`I3`/`I4` fecharem exaustivamente em `small`/`medium` — depende de `I1`/`I2`/
`I4` (ainda `PENDENTE`, mas rasos e rápidos de obter, independente de `TypeOK` fechar)
e de `I5`/`I6` (já `CONFIRMADO`, também raso). `tiny` já fornece a confirmação
exaustiva de que `TypeOK`/`I3`/`I4` não regridem com F1+F2 — ter essa mesma confirmação
também em `small`/`medium` é desejável, mas não crítico, exatamente como já se aceita
para `full` desde antes de B-22 existir.

**Se 600s não for suficiente:** aceitar `small`/`medium` `TypeOK`/`I3`/`I4`
inconclusivo como limitação documentada desta rodada, no mesmo padrão já aceito para
`full`, sem mais investimento de redução de constantes — registrar a decisão e seguir.

---

## 4. `feature/b22-f2-particao` está pronta para ser considerada o fechamento de B-22 nesta rodada

Avaliando o que B-22 (conforme `docs/plano-b22-falhas-explicitas-2026-10-03.md`) se
propunha a entregar:

| Item do plano | Status |
|---|---|
| F1 (`Cair`/`Reviver`) — ação mínima, testada, sem violação nova | **Completo**, validado (`docs/validacao-b22-f1-2026-10-03.md`) |
| F2 (`Particionar`/`Mesclar`) — maioria vence, registro inteiro, sem *timestamp* | **Completo**, validado, produz contraexemplo real confirmado (I5/I6) |
| Exclusão mútua F1/F2 bidirecional | **Completo** (P-24 corrigido) |
| Catálogo operação×falha×invariante atualizado com o achado real | **Completo** (`docs/plano-controlador-heuristico-2026-09-24.md` §3.2, commit `5450cd7`) |
| `scripts/inject-partition.sh` (B-29) | Esboçado como referência (§3 do plano) — **nunca esteve no critério de aceite de B-22 em si**, explicitamente "rodada futura" |
| Combinação F1+F2 | Explicitamente fora de escopo (§2.5 do plano) — não um requisito de fechamento |
| `TypeOK`/`I3`/`I4` exaustivo em TODAS as cardinalidades | Parcial — `tiny`/`full`(histórico) aceitos; `small`/`medium` com melhora substancial mas inconclusivos; não era, no plano original, um critério de aceite explícito à parte (o plano falava em "reverificação", não em "fechar todas as cardinalidades") |

**Avaliação: sim, pronta para ser considerada o fechamento de B-22 nesta rodada**,
contanto que a tentativa de 600s (§3) seja feita primeiro (barata, poucos minutos) e o
resultado — fechou ou não — seja registrado antes de declarar B-22 encerrado. Nenhum
item de escopo do plano original ficou sem endereçamento: o que não fechou
exaustivamente (`small`/`medium` para invariantes que valem sempre) é uma limitação de
tratabilidade já esperada desde o início (`docs/plano-b22-falhas-explicitas-2026-10-03.md`
§4.2, risco "Médio" explicitamente reconhecido), não uma lacuna de desenho ou
implementação.

---

## 5. Veredito final

| Pergunta | Resposta |
|---|---|
| Aprovar P-24? | **Sim**, sem ressalva — uma linha, fiel à recomendação. |
| Aprovar P-25? | **Sim** — números confirmados de forma independente; resultado misto mas real e informativo (confirma a hipótese nos termos certos: reduz a busca substancialmente, especialmente a fila pendente, sem fechar `small`/`medium`). |
| Decisão `small`/`medium` | Tentar 600s primeiro (barato); se não fechar, aceitar como limitação documentada — **não** reduzir `MaxCanal`/`MaxRelogio`. |
| B-22 pronto para fechamento nesta rodada? | **Sim**, após o teste de 600s (resultado a registrar de qualquer forma). |

---

## Pontos que precisam de validação do Gestor

1. Confirmar a tentativa de 600s para `small`/`medium` antes de declarar B-22 encerrado
   — barato, não deveria ser um bloqueio real ao fechamento.
2. Confirmar que `small`/`medium` `TypeOK`/`I3`/`I4` inconclusivo (se persistir a 600s)
   é aceitável como limitação documentada, no mesmo padrão já aceito para `full`.
3. Confirmar o fechamento de B-22 nesta rodada, com a ressalva acima registrada no
   Cap. 5 se for o caso.
4. Nota de processo: adoção de *worktrees* isoladas por agente concorrente (já em uso
   nesta validação) — sem necessidade de aprovação adicional, é prática operacional,
   não decisão de arquitetura do TCC.
5. Como sempre: nada vai a `origin/*` sem aprovação explícita.

## Próxima tarefa sugerida para o Worker

1. Rodar `small`/`medium` `TypeOK`/`I3`/`I4` com `TIMEOUT_SECS=600` (mesmos demais
   parâmetros); reportar se fecha ou não, sem presumir.
2. Se fechar: ótimo, registrar e fechar B-22 sem ressalva.
3. Se não fechar: registrar como limitação documentada (mesmo padrão de `full`) e
   fechar B-22 com essa ressalva no Cap. 5.
4. Reportar ao Gestor e ao Senior antes de qualquer *push* para `origin/*`.
