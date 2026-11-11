# Relatório W-1 (B-27.3): retirada do portão de emissão K1..K4 (2026-11-04)

Prompt W-1 de `docs/plano-execucao-2026-11-04.md` (seção 9, commit `4e43602`), com a seção 5 de
`plano-alinhamento-objetivos-2026-11-04.md`. Decisão do autor de 04/11: o controlador age no *cluster*;
remoções de teste autorizadas (D-6). Branch `refactor/w1-remover-acoes-cliente` (nome dado pelo Gestor;
o plano sugeria `refactor/b27-3-remover-acoes-cliente`), worktree `wt-w1`, base `20805b0`. Sem push, sem VM.

## 1. Tarefa 0: ocorrências e destino

Comando: `grep -rn "PortaoDeEmissao\|ParametrosKnobs\|EventoK3\|PerfilAtual\|Dormidor\|esperaPortao\|aguardarAdministrativa" workload/src`.

| Arquivo | Ocorrências | Destino |
|---|---|---|
| `controle/PortaoDeEmissao.java` | 21 | apagado |
| `controle/ParametrosKnobs.java` | 7 | apagado |
| `controle/EventoK3.java` | 1 | apagado |
| `controle/Dormidor.java` | 2 | apagado (só o K3 o usava) |
| `controle/PerfilAtual.java` | 2 | apagado (só o portão o usava; o W-3 decide se volta) |
| `session/AplicacaoS3.java` | 14 | simplificado: sem portão, `backoff` puro, `esperaPortaoNs = 0` |
| `session/ContextoS3.java` | 6 | simplificado: sem campo `portao` nem construtor de 5 argumentos |
| `session/WorkloadMain.java` | 5 | simplificado: uma só `rodarCliente` (10 parâmetros), laço de antes do B-27 |
| `session/AcaoS3.java` | 5 | mantido; Javadoc de `esperaPortaoNs`: "reservada; 0 desde o B-27.3" |
| `session/UsuarioVirtual.java` | (via `ctx.portao.aplicarPensar`, K2) | simplificado: sorteio do pensar sem ajuste |
| `controle/AgregadorDeJanela.java` | comentário "portao K1" | comentário histórico |
| testes `controle/*Portao*`, `ParametrosKnobsTest`, `PerfilAtualTest`, `session/Portao*` | 184 | apagados (seção 3) |
| `session/AcaoS3FimTest.java` | 2 (só `esperaPortaoNs` do registro) | mantido sem mudança |

Equivalência com `PortaoDeEmissao.livre()`, conferida no código de `20805b0`: `entrar()` e
`aguardarAdministrativa()` devolvem 0 sem esperar; `sair()` não faz nada; `pausaAposOperacaoNs()` é 0
(o `parkNanos` só rodava com pausa > 0); `aplicarPensar(x)` devolve `x`; `esperaRetentativaNs(..., base, ...)`
devolve `base`. O caminho livre era, portanto, o de antes do B-27; o corpo de `rodarCliente` foi
restaurado a partir de `62ceea3^` (último commit antes do B-27).

## 2. Testes novos (TDD)

| Teste | Item | Na base (`b62c410` sobre `20805b0`) | Depois |
|---|---|---|---|
| `session/ArquiteturaSemAcaoNoClienteTest` | R-C1 | vermelho: `AplicacaoS3`, `ContextoS3`, `WorkloadMain` usam `PortaoDeEmissao` | verde |
| `session/S3SemPortaoTest.semTipoDeControleNoCenarioS3` | R-C2 | vermelho: construtores e campo `portao` | verde |
| `session/S3SemPortaoTest.esperaPortaoSempreZero` | R-C2 | verde (o caminho padrão já era o livre) | verde |
| `session/GoldenRodarClienteTest` | R-C3 | verde por construção (referência gerada nessa base) | verde |

R-C1 tem uma leitura a validar: o pacote `session` já usava, em `20805b0`, tipos de `controle` dos
sensores do B-26 (`ConsultaSaudeRest`: `FonteDeSaude`, `LeituraSaude`; `SondaTopologiaCli`:
`AmostraTopologia`, `FonteDeTempo`, `ResultadoNo`, `SondaTopologia`). O teste permite só esses
tipos e mantém vazia a lista de classes autorizadas a usar `controle` para o laço (`LACO_AUTORIZADO`).

R-C3: referência `workload/src/test/resources/golden-20805b0/rodar-cliente-s1-s2.txt` (20.512 linhas,
SHA-256 `7b9df59f1e91a31d6da5a8679a12e77d1ef8800486a5a6751256bf12b4a25d26`), gerada por um gerador
temporário (não versionado) sobre o código de `20805b0`: S1 e S2 × v2 e v3 × threads 0 e 3, 2000
operações cada, sids UUID mascarados. `S3SimulacaoTest`, `GoldenS1S2Test` e `golden-8e2e815/` não
mudaram (`git diff 20805b0` vazio).

## 3. Testes apagados (42 testes em 9 arquivos)

| Arquivo | Testes | Motivo |
|---|---|---|
| `controle/PortaoDeEmissaoK1Test` | 6 | K1 (admissão) retirado |
| `controle/PortaoDeEmissaoK2K4Test` | 5 | K2 (ritmo) e K4 (espera entre retentativas) retirados |
| `controle/PortaoDeEmissaoK3Test` | 5 | K3 (adiamento) retirado (D-6) |
| `controle/ParametrosKnobsTest` | 6 | classe `ParametrosKnobs` apagada |
| `controle/ArquiteturaPortaoTest` | 1 | os 5 tipos do portão não existem mais (substituído por R-C1) |
| `controle/PerfilAtualTest` | 2 | `PerfilAtual` apagado com o portão |
| `session/PortaoS1S2Test` | 5 | sobrecarga de 11 parâmetros de `rodarCliente` apagada (P2 coberto por R-C3) |
| `session/PortaoS3AplicacaoTest` | 8 | portão em `AplicacaoS3` apagado |
| `session/PortaoS3SimulacaoTest` | 4 | portão em S3 apagado (espera 0 coberta por R-C2) |

Nenhum outro teste precisou mudar.

## 4. Contagens

```bash
cd workload && mvn -q clean test     # contagem somada dos target/surefire-reports/TEST-*.xml
cd .. && python -m unittest discover -s analysis
```

| Momento | Surefire | Arquivos | `unittest` analysis |
|---|---|---|---|
| `20805b0` | 863/0/0/1 | 122 | 187 OK, 1 *skip* (medido depois; `analysis/` não muda nesta branch) |
| `544d7c1` (refactor) | 825/0/0/1 | 116 | 187 OK, 1 *skip* |

825 = 863 − 42 + 4. Observação: um `mvn -q test` sem `clean` logo depois de apagar arquivos deixou
XML antigos em `target/surefire-reports` (126 arquivos, 868 testes na soma); a contagem válida é a
do `clean`.

Aceite do `grep -rn "K1\|K2\|K3\|K4\|Knobs\|PortaoDeEmissao" workload/src`: restam só comentários
históricos explícitos (`WorkloadMain.java:776`, `AgregadorDeJanela.java:42`, Javadoc de
`GoldenRodarClienteTest`) e os nomes dos tipos retirados dentro de R-C1, que verifica que eles não existem.
`controle/Controlador.java` sem mudança (`git diff 20805b0` vazio).
