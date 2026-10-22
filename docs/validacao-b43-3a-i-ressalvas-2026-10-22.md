# Validação V-B43.3a-i-R: fechamento das ressalvas do verificador de sanidade (2026-10-22)

> Senior, 2026-10-22. Revalidação curta da branch `fix/b43-3-guarda-sanidade-mistura` (worktree
> `wt-b43-3`), commits `9e8eadc..a8f5549` sobre `6ce301a`, seção 10 de
> `docs/relatorio-b43-3a-i-2026-10-22.md`. Responde às ressalvas R1/R2 e à nota do D5 de
> `validacao-b43-3a-i-2026-10-22.md`, e à decisão do Gestor sobre a questão 1 (operação fora da
> mistura ⇒ saída 2). Nada foi alterado em `wt-b43-3`. Todas as medições rodaram em cópias
> `git archive` no *scratchpad*, apagadas no fim. Sem *cluster*, sem VM, sem push.

## 1. Veredito

**APROVADA.** R1 e R2 estão fechadas, as duas regras novas fazem o que a decisão do Gestor pede, e
todas as alegações da seção 10 conferem. Dos 5 mutantes do Worker, 5 morrem. Dos 5 meus, 4 morrem e
1 é equivalente na prática (seção 3). Os 20 JSON versionados continuam válidos sem regeneração. O
merge de ensaio sobre `feature/controlador-integracao` (`9618252`) não tem conflito e fica verde
(seção 5).

## 2. Medido × alegado

| # | Alegação (seção 10) | Medido pelo Senior | Confere? |
|---|---|---|---|
| 1 | `9e8eadc` 340/0/0/1, 45 arquivos | `git archive` + `mvn clean test`: exit 0, 340/0/0/1, 45 | sim |
| 2 | `2b1b208` 350/0/0/1, 46 arquivos | exit 0, 350/0/0/1, 46 | sim |
| 3 | `196ab17` 353/0/0/1, 46 arquivos | exit 0, 353/0/0/1, 46 | sim |
| 4 | `a8f5549` não toca `workload/` | `git diff --quiet 196ab17 a8f5549 -- workload` sai com 0. Ponta: 353/0/0/1, 46 | sim |
| 5 | 353/0/0/0 com `-Dsanidade.csvJunho` | Na ponta, com `…/tcc-desenvolvimento/runs/S1-F1/rep-001.csv`: exit 0, 353/0/0/0, 46 | sim |
| 6 | Nenhum teste existente perde asserção | Diff de `SanidadeRegressaoJunhoTest` e `SanidadeUsoTest`: só linhas `+` (constante `SHA_RECORTE`, uma asserção e 3 métodos novos) | sim |
| 7 | `runs/` e recursos intocados | `git diff --stat 6ce301a..a8f5549 -- runs/ workload/src/test/resources Scenario.java`: vazio. `MANIFEST.sha256 -c`: 21 OK | sim |
| 8 | 5 CSVs saem com 4 e geram JSON byte a byte idêntico | Rodei os 5 citados e mais 2 escolhidos por mim (`S1-none/rep-001`, `S2-F1/rep-002`) a partir de `tcc/`: **7/7 com saída 4**, e o `cmp` com o JSON versionado dá **7/7 idêntico** | sim |
| 9 | Contraprova: S1 verificado como S2 ⇒ 2 | `S1-F3/rep-003 --scenario S2`: saída **2**, `linha 79: operacao 'ResetFailures' fora da mistura do cenario S2 […]`, sem JSON | sim |
| 10 | Nenhum CSV S2 tem ResetFailures | Nos 9 JSON S2 versionados, `contagens.fora` não tem `ResetFailures` | sim |
| 11 | M3, M4, M9, M10 e M11 mortos | Reproduzidos sobre `a8f5549`, com placares idênticos: M3 353/4, M4 353/3, M9 353/8, M10 353/2, M11 353/2 | sim |

Observação sobre a contraprova inversa. Um CSV S2 verificado como S1 (`S2-none/rep-002 --scenario S1`)
sai com **4**, e não com 2. As quatro operações de S2 estão contidas nas cinco de S1, então nenhuma é
"fora da mistura". A troca é pega por `MISTURA_DESVIO` (5 motivos, Validate em torno de 95 % contra 50 %
esperados). É o comportamento correto: a regra de operação fora da mistura recusa o arquivo, e a regra
de mistura reprova o veredito. O cenário trocado nunca passa como OK.

## 3. Mutantes do Senior

Cada um foi aplicado numa cópia `git archive a8f5549` e testado com o Surefire completo.

| ID | Mutação | Resultado | Teste que mata |
|---|---|---|---|
| N1 | Operação comparada sem diferenciar maiúsculas (`equalsIgnoreCase`): aceita `login` | **morto**, 353/1/0/1 | `operacaoForaDaMisturaDeS1Da2[3]` (`login`) |
| N2 | Checagem só na região "fora" (pula `durante*`/`recuperacao`) | **morto**, 353/1/0/1 | `operacaoForaDaMisturaNaRegiaoDentroTambemDa2` |
| N3 | Mistura sempre de S1 (`operacoesDe(Scenario.S1)`), ignorando o cenário declarado | **morto**, 353/1/0/1 | `resetFailuresEmS2Da2` |
| N4 | Linha vazia só recusada se `br.ready()` (pularia a linha vazia final) | **sobrevive**, 353/0/0/1. **Equivalente na prática**: empacotei o mutante e rodei com linha vazia final em LF, em CRLF, dupla no fim e no meio. Saída 2 nos quatro casos, igual à ponta. Neste JDK/Windows, `ready()` devolve `true` também no fim do arquivo | n/a |
| N5 | Exceção para nomes iniciados por `Un` (deixa passar `Unblock`) | **morto**, 353/2/0/1 | `operacaoForaDaMisturaDeS1Da2[2]`, `unblockVerificadoComoS1Da2` |

N4 não é lacuna: o comportamento observável é idêntico ao da ponta. Não pede teste novo.

## 4. Os dois pontos abertos

**1. "Fora da mistura" = operação com peso 0 no cenário (ResetFailures em S2 ⇒ 2): confirmo.**
- Em S1/S2, o único emissor de ResetFailures é o sorteio (`WorkloadMain`, `case RESET_FAILURES`). O
  outro emissor é `ContaLimiteS3`, que só existe no S3. Com peso 0, `proximaOperacao` nunca sorteia a
  operação. Uma linha ResetFailures num CSV S2 significa, portanto, que o arquivo não é S2 ou que o
  gerador mudou. Nos dois casos, "arquivo inválido para o cenário" é a resposta strict certa. Os 9 CSVs
  S2 reais confirmam: nenhum tem ResetFailures.
- Como `operacoesDe` é derivado de `Scenario.peso`, ele acompanha automaticamente qualquer mudança de
  pesos que B-43.2 fizer. Se B-43.2 passar a emitir ResetFailures em S2 fora do sorteio (por exemplo,
  um reset de identidades no *warm-up*), o verificador vai recusar o arquivo. Isso é desejável, porque
  obriga a decidir de forma explícita se essa linha conta na mistura. **Ponto de atenção para a
  V-B43.2:** conferir se B-43.2 emite alguma operação fora do sorteio na janela medida.
- Uma ressalva de desenho, sem defeito agora. A checagem fica no CLI (`VerificadorSanidade.ler`), e
  não em `Sanidade.registrar`. Usada direto, `Sanidade` ainda dilui. Na parte ii, a guarda *online*
  precisa aplicar o mesmo `operacoesDe`, ou a checagem deve descer para `Sanidade`. Registrar como
  critério de aceite da parte ii.

**2. `versao` continua `b43-3a-i/1`: confirmo.** A versão identifica o critério que produziu um JSON.
As regras novas só recusam arquivos (saída 2, sem JSON). Para todo arquivo que ainda gera JSON, o
conteúdo é o mesmo, como mostram os 7/7 idênticos byte a byte. Subir a versão obrigaria a regenerar 20
JSON e o manifesto sem nenhuma mudança de conteúdo. A versão deve subir (`b43-3a-ii/1` ou `/2`) quando
mudar algo que altera o veredito ou o JSON: limiares calibrados, a regra R3 (Login `ERROR_*`) ou a
região de `recuperacao`.

## 5. Integração em `feature/controlador-integracao`

- `git merge-tree --write-tree 9618252 a8f5549`: **sem conflito**, árvore `8368c2a`.
- Surefire da árvore de ensaio (`git archive 8368c2a`): exit 0, **540/0/0/1, 59 arquivos**. A ponta da
  integração dá 492/0/0/0 (52). A soma fecha: 492 + 48 = 540 e 52 + 7 = 59, e o único *skip* é o S2.
- `fix/b43-3` também não conflita com `fix/b43-1` (`65d237b`) nem com `fix/b43-2` (`cc0497e`).

**Ordem recomendada:**

1. **B-43.3a-i agora**, primeiro. Está aprovada, só acrescenta código (arquivos novos e um acessor de
   leitura em `Scenario`), e o merge de ensaio está verde. Entrando antes, o verificador já fica
   disponível para julgar o *smoke* de B-43.2 e a execução de calibração dos limiares (questão 2 da
   V-B43.3a-i).
2. **B-43.1 e B-43.2 juntas, depois da V-B43.2.** B-43.2 parte de `65d237b`, contém B-43.1 e deve
   abrir com o commit `test:` das ressalvas R1/R2 da V-B43.1. Integrar B-43.1 sozinha agora levaria as
   lacunas de cobertura que esse commit fecha. Integrar a ponta validada de B-43.2 traz as duas de uma
   vez, com histórico linear.
3. **B-43.3a parte ii por último**, a partir da integração que já contém 1 e 2. Ela liga a guarda em
   `WorkloadMain` e `percentis.py`, e com isso depende do comportamento de B-43.2.

Se o Gestor preferir não integrar nada antes de B-43.2, nada se perde: o merge de B-43.3a-i continua
trivial depois. Mas o *smoke* de B-43.2 deve então ser verificado com o *jar* de `a8f5549`.

## 6. Pontos para o Gestor

1. Aprovar a entrega (ressalvas fechadas) e o merge local de `fix/b43-3-guarda-sanidade-mistura` em
   `feature/controlador-integracao`, na ordem da seção 5.
2. Confirmar "peso 0 = fora da mistura" e a manutenção de `versao = b43-3a-i/1`.
3. Registrar dois critérios para a parte ii: a guarda *online* aplica `operacoesDe` (ou a checagem desce
   para `Sanidade`), e a versão sobe quando o veredito mudar.
4. Push continua pendente de aprovação explícita. Nada foi publicado.
