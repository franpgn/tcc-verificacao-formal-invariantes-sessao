# Validação do W-1 (B-27.3): retirada de K1..K4 e do portão de emissão (2026-11-04)

Validação independente do Senior. Branch `refactor/w1-remover-acoes-cliente`, commits `b62c410` (test),
`544d7c1` (refactor) e `0f8b351` (docs), sobre `20805b0`. Pedido: prompt W-1 de
`docs/plano-execucao-2026-11-04.md` e seção 5 de `plano-alinhamento-objetivos-2026-11-04.md`
(branch `docs/plano-implementacao-controlador`). Sem push.

**Parecer: APROVADO COM RESSALVAS.** Nenhuma ressalva bloqueia a integração nem o início do W-3.

## 1. O que foi conferido

| Item | Como | Resultado |
|---|---|---|
| Diff inteiro | `git diff 20805b0..HEAD`, por commit | 28 arquivos. O commit de código toca só `session/{AcaoS3,AplicacaoS3,ContextoS3,UsuarioVirtual,WorkloadMain}`, um comentário em `controle/AgregadorDeJanela` e apaga os 5 tipos do portão. Histórico linear, 3 commits com o *trailer* `Co-Authored-By` |
| Equivalência com `PortaoDeEmissao.livre()` | leitura de `PortaoDeEmissao.java` em `20805b0` | Equivalente. No modo livre, `entrar()` e `aguardarAdministrativa()` devolvem 0 sem bloquear e sem lançar `InterruptedException`, `sair()` não faz nada, `pausaAposOperacaoNs()` vale 0, `aplicarPensar(x)` devolve `x` e `esperaRetentativaNs(..., base, ...)` devolve `base`. Os ramos removidos (`break` na interrupção do K1, `parkNanos` com pausa > 0) eram inalcançáveis com o portão livre |
| Uso real do portão em produção na base | `git grep "new PortaoDeEmissao\|PerfilAtual\|ParametrosKnobs" 20805b0 -- workload/src/main` | Nenhum uso fora dos próprios tipos. Em `20805b0` a produção só usava `livre()`, então o W-1 não muda nenhum comportamento observável em execução |
| Golden R-C3 gerada sobre a base | worktree temporária em `20805b0` com `GoldenRodarClienteTest` e um *dump* de `sequenciaAtual()` | **Reproduzida.** Na base, o teste passa e o *dump* tem 20.512 linhas com SHA-256 `7b9df59f1e91a31d6da5a8679a12e77d1ef8800486a5a6751256bf12b4a25d26`, idêntico ao arquivo versionado. Mesmo teste verde na ponta do W-1. Worktree temporária removida |
| Testes alterados fora dos 9 arquivos | `git diff --name-status 20805b0..HEAD -- workload/src/test analysis scripts` | Só os 9 apagados (D) e os 3 novos mais a golden (A). Nenhum M. `S3SimulacaoTest`, `GoldenS1S2Test`, `golden-8e2e815/`, `ConsultaSaudeRestCliTest` e `Controlador.java` sem diff |
| Contagem de testes apagados | `@Test` por arquivo em `20805b0` | 1+6+2+6+5+5+5+8+4 = 42, como declarado. Novos: 1+1+2 = 4 |
| Surefire com `clean` | `mvn -q clean test`, soma dos `TEST-*.xml` | **825/0/0/1 em 116 arquivos** (122 − 9 + 3), na segunda execução. A primeira deu 825/1/0/1 (ver ressalva 1) |
| Python | `python -m unittest discover -s analysis` | 187 OK, 1 *skip* |
| *Grep* de aceite | `grep -rn "K1\|K2\|K3\|K4\|Knobs\|PortaoDeEmissao" workload/src` (fora a golden) | Só comentários históricos (`WorkloadMain.java:776`, `AgregadorDeJanela.java:42`) e os nomes dos tipos retirados dentro dos testes R-C1 e R-C3 |
| R-C1 | leitura do teste e varredura de `session/` | Ver seção 2 |

## 2. Leitura do R-C1

Está correta. O texto literal do prompt ("nenhuma classe de `session` importa `controle`") já falharia na
base, porque `ConsultaSaudeRest` e `SondaTopologiaCli` (B-26.2, sem diff nesta branch) importam
`FonteDeSaude`, `LeituraSaude`, `AmostraTopologia`, `FonteDeTempo`, `ResultadoNo` e `SondaTopologia`.
Esses tipos são sensores, não atuam sobre a emissão, e a decisão de 04/11 retira só as ações de cliente.
O teste olha os nomes qualificados `br.unipampa.tcc.controle.X` depois de tirar os comentários, e por isso
pega *imports* simples, *imports* com `*` (o `*` não está na lista), *imports* estáticos e nomes
qualificados no código. `session/` não tem subpacotes, de modo que `Files.list` cobre tudo. Hoje só os
dois adaptadores usam `controle`.

## 3. Ressalvas

1. **Teste instável fora do escopo do W-1.** `ConsultaSaudeRestCliTest.saidaJsonl` falhou na primeira
   execução completa (`expected: <3> but was: <0>` em `:144`, `nos_min`) e em 2 de 9 execuções isoladas
   do mesmo teste. Depois passou em 10/10 na ponta do W-1 e em 10/10 em `20805b0`. Nem o teste nem
   `SondaTopologiaCli` mudaram nesta branch. A causa provável é o `--prazo-ms 100` com o lançador falso
   quando a máquina está carregada (havia outros builds em paralelo). Não bloqueia o W-1, mas o
   "825/0/0/1" depende da carga da máquina. Ação: abrir um item de dívida técnica para o B-26.2
   (aumentar o prazo no teste ou trocar o relógio de parede por um falso) e, até lá, repetir antes de
   tratar uma falha nesse teste como regressão.
2. **A golden mascara todos os UUIDs.** São 4.504 das 20.512 linhas. Por isso ela não distingue qual sid é
   validado (o atual ou um token antigo da v3); fixa só a sequência de operações e os argumentos que não
   são UUID. Para o W-1 isso basta, porque a mudança em `rodarCliente` só tira chamadas sem efeito ao
   portão, mas a golden não deve ser citada como prova de equivalência dos sids. Se uma tarefa futura
   mexer no sorteio de identidades, convém trocar a máscara por uma numeração estável (o primeiro UUID
   vira `u0`, o segundo `u1` e assim por diante).
3. **Gerador da golden fora do repositório.** A golden é a saída de `GoldenRodarClienteTest.sequenciaAtual()`
   rodado na base, como reproduzi aqui, então o gerador fica de fato no próprio teste. O relatório do Worker
   devia dizer isso em vez de "gerador temporário não versionado". É só um ajuste de texto, sem pendência
   técnica.
4. **A lista do R-C1 é por tipo, não por classe.** Uma classe nova de `session` poderia importar, por
   exemplo, `FonteDeTempo` (genérico) sem que o teste acusasse. Hoje não é um risco, porque uma ação de
   cliente precisaria de tipos de `controle` fora da lista, e esses o teste reprova. Quando o W-3 preencher
   `LACO_AUTORIZADO`, vale passar a lista para pares (classe, tipo).

## 4. Decisões do coordenador

Não vejo erro técnico em nenhuma das três:

- leitura do R-C1: correta (seção 2);
- remoção de `PerfilAtual`: em `20805b0` só o portão e `PerfilAtualTest` a usavam, e `Controlador.java`
  não a referencia; o W-3 pode recriá-la sem conflito;
- nome da branch `refactor/w1-remover-acoes-cliente`: segue a convenção `refactor/<nome>`.

Observação: `wt-w3` (`feature/w3-laco-controlador`) está em `0f8b351`. Este parecer acrescenta um commit
`docs:` em cima e não muda código, então o W-3 pode seguir dessa base ou da nova ponta.
