# Estado da frente de desenvolvimento prático — TCC-I (snapshot 2026-05-27)

> Documento de retomada. Resume o estado do repositório `tcc-desenvolvimento`, das atividades do cronograma e do ambiente de execução **no fim da sessão de 27/05/2026**. Usar como ponto de partida para qualquer sessão futura.

## 1. Repositório `tcc-desenvolvimento`

- URL: `https://github.com/franpgn/tcc-desenvolvimento.git`
- *Checkout* local: `/workspace/tcc-desenvolvimento/`
- *Branch* corrente (HEAD): `feature/analysis-python`
- *Branches* publicadas no GitHub: `main`, `feature/cluster-infinispan`, `feature/cluster-async`, `feature/spec-completa`, `feature/workload-bootstrap`, `feature/mc-resultados-iniciais`, `feature/mc-segunda-rodada`, `feature/analysis-python`

### Histórico (último para o primeiro, *feature/analysis-python*):

```
27591ed  feat: pipeline de análise estatística em Python (B-16, B-17, B-18)
75fedbf  docs: padroniza "autor" e remove terminologia interna dos documentos
78d8bcc  chore: bootstrap inicial do repositório
```

### Pull Requests abertos (7, aguardando *merge*):

| # | URL | Bloco | Atividade do cronograma |
|---|---|---|---|
| 1 | https://github.com/franpgn/tcc-desenvolvimento/pull/1 | B-02 | Sem 7-8: ambiente experimental |
| 2 | https://github.com/franpgn/tcc-desenvolvimento/pull/2 | B-03 | Sem 7-8: variante DIST_ASYNC |
| 3 | https://github.com/franpgn/tcc-desenvolvimento/pull/3 | B-14 | Sem 7-8: spec TLA+ completa |
| 4 | https://github.com/franpgn/tcc-desenvolvimento/pull/4 | B-04 | Sem 7-8: workload esqueleto |
| 5 | https://github.com/franpgn/tcc-desenvolvimento/pull/5 | MC rodada 1 | Sem 9-10: model checking inicial |
| 6 | https://github.com/franpgn/tcc-desenvolvimento/pull/6 | MC rodadas 2-5 | Sem 9-10: model checking + sondas |
| 7 | https://github.com/franpgn/tcc-desenvolvimento/pull/7 | B-16, B-17, B-18 | Sem 9-10: análise estatística |

Decisão 014 (D-006): autorização de *push* já cumprida; PRs prontos para *merge* sequencial. Ordem sugerida: #1 → #2 → #3 → #5 → #6 → #4 → #7.

## 2. Estado das quatro atividades do cronograma Sem 7-10

| Atividade | Status | Evidência |
|---|---|---|
| **Implementar *workload* e checagem de invariantes** (Sem 7-8) | ~25 % — esqueleto migrado, implementação efetiva pendente (B-05 a B-10) | PR #4 |
| **Modelo formal com transições e canal assíncrono** (Sem 7-8) | **~100 % — spec completa, executada** | PR #3 |
| ***Model checking* e análise de contraexemplos** (Sem 9-10) | **100 % — 5 rodadas executadas com dados reais; I1, I2, I5, I6 violados; I3, I4 OK em 91 M estados** | PRs #5 e #6 |
| ***Baseline* experimental com F1/F3 e p95/p99** (Sem 9-10) | 0 % — depende do *workload* real e do ambiente Podman | — |
| **Pipeline de análise estatística** (suporte ao *baseline*) | **100 % — três *scripts* implementados e validados em 750 000 ops sintéticas** | PR #7 |

## 3. Ambiente de execução já configurado no *sandbox*

| Ferramenta | Versão | Localização | Como usar |
|---|---|---|---|
| OpenJDK Temurin | 17.0.9+9 | `~/tools/jdk-17.0.9+9` | `source ~/tools/env/tcc-dev.sh` (carrega tudo) |
| Apache Maven | 3.9.9 | `~/tools/apache-maven-3.9.9` | idem (PATH inclui `bin/`) |
| TLA+ tools (TLC) | v1.8.0 (rev 4ba7d88) | `~/tools/tla2tools.jar` | `TLA_TOOLS` exportado pelo helper |
| Python | 3.11.2 (stdlib) | `/usr/bin/python3` | uso direto |
| Python (venv com numpy/scipy) | numpy 2.4, scipy 1.17 | `~/tools/venv/bin/python` | uso direto sem ativar venv |
| Helper de ambiente | — | `~/tools/env/tcc-dev.sh` | `source ~/tools/env/tcc-dev.sh` (substitui exports manuais) |

Esses caminhos persistem em `~/tools/` (volume do usuário, sobrevive entre sessões).

## 4. Ferramentas que ainda **não estão disponíveis** no *sandbox*

| Ferramenta | Por que falta | Impacto |
|---|---|---|
| Podman / podman-compose | *seccomp* do *sandbox* bloqueia `unshare(CLONE_NEWUSER)` (`Seccomp=2`, 1 filtro ativo); sem `newuidmap`/`newgidmap`; sem `sudo` para configurar | impede subir o *cluster* Infinispan ou qualquer container |
| `tc-netem` com `NET_ADMIN` | `CapEff=0`; bounding set inclui `cap_net_admin` mas não está concedido ao processo; sem `sudo` para `setcap`; `unshare -Urn` também bloqueado | impede injeção de F3 (atraso/jitter) |
| Imagem `quay.io/infinispan/server:15.0` | depende de Podman | impede o *cluster* |

Diagnóstico (2026-05-27, segunda sessão do dia): tentativa de instalação realizada. `apt-get` sem repositórios — contornado com `sources.list` local em `~/apt/etc/`, mas isso não desbloqueia *root*/`setcap`/seccomp. Maven **foi** instalado (tarball oficial Apache); Podman e `tc-netem` permanecem bloqueados por restrição de *kernel*/sandbox, não por falta de binário.

Sem Podman e sem `tc-netem`, as atividades **#1** (workload real, parte que sobe contra cluster) e **#4** (baseline experimental) continuam restritas à máquina do autor. **A compilação do `workload/` (`mvn package`) agora roda aqui**, o que permite revisar dependências, rodar testes JUnit que não exijam Infinispan e produzir o *jar*.

## 5. Decisões pendentes do autor

Acumuladas ao longo da frente de desenvolvimento:

1. **Múltiplas comparações em `compare_scenarios.py`:** aplicar Bonferroni/Benjamini-Hochberg em 84 testes (12 cenários × 7 ops) ou reportar p-valor bruto.
2. **Modelo formal — assimetria do `Block`:** caminho A (aceitar contraexemplos como achados legítimos do protocolo otimista) ou caminho B (refinar a spec para gerar `SESSAO_INVALIDA` derivadas).
3. **TD-001 (`numSegments` = 64 vs 256):** manter o valor herdado do esqueleto ou alinhar com T4 da Tabela 1.
4. **TD-002 (credenciais Infinispan):** parametrizar via variável de ambiente em B-05.
5. **TD-003 (configuração do `RemoteCacheManager`):** manter lista completa de nós ou usar *server discovery* nativo.
6. **Nome do *cache* assíncrono:** `sessions-async` (atual) ou `sessions_async`.
7. **`MC-full.cfg`:** aceitar `MC-medium` (3-3-2) como teto se 3-3-3 estourar (não estourou nesta sessão).
8. **`runs/` versionamento:** mantida a regra atual (CSVs ignorados, JSONs versionados, `states/` do TLC ignorado).
9. **Estratégia de execução para Sem 9-10 real:** quando rodar o *baseline*, ordem de cenários e número de repetições.

## 6. Próximos passos do *backlog* (não executados)

Ordem de dependência:

- **B-05** — implementação efetiva de O1-O7 com Hot Rod no *workload*. *Branch* sugerida: `feature/workload-operations`.
- **B-06** — `LatencyRegistry` com HdrHistogram.
- **B-07** — `InvariantAuditor` com checagem imediata (I1-I4) e auditoria periódica (I5/I6).
- **B-08** — Gerador de chaves Zipfian ρ=0,99, 100 000 chaves, *payload* de 1 KB.
- **B-09** — *Warm-up* parametrizado + descarte das 5 % iniciais.
- **B-10** — CLI parametrizada (`--cache`, `--scenario`, `--duration`, `--ops`, `--rep`).
- **B-11** — `scripts/inject-crash.sh` (F1 via `podman stop` por 60 s).
- **B-12** — `scripts/inject-jitter.sh` (F3 via `tc-netem` lognormal +50 ms p99).
- **B-13** — `scripts/collect-metrics.sh` (OpenMetrics).
- **B-19** — `scripts/run-baseline.sh` (pipeline completo).

## 7. Estado da redação (`/workspace/*.tex`)

| Arquivo | Status |
|---|---|
| `Main-Monografia.tex` | esqueleto; pré-textuais com *placeholders* (P-019 aberta) |
| `Introducao.tex` | v0.2; revisado; termos em inglês traduzidos |
| `FundamentacaoTeorica.tex` | §2.1 reestruturado (sistemas distribuídos com narrativa de pontes); termos em inglês traduzidos |
| `Metodologia.tex` | §3.3.5 ratificada (Decisão 013); termos em inglês traduzidos; `exatidão` → `acurácia` |
| `Desenvolvimento.tex` | §4.1-§4.5 completas; precisa atualizar §4.4 para referenciar o repositório `tcc-desenvolvimento` em vez de `/workspace/prototipo/` |
| `ConclusoesParciais.tex` | esqueleto; pode ganhar resultados parciais de MC para a Sem 9-10 |
| `ApProtocoloMS.tex` | completo; termos em inglês traduzidos |
| `ApPlanoTCC2.tex` | completo; termos em inglês traduzidos; `Gestor` → `autor` |
| `bibliografia.bib` | 44 entradas, 33 REFs efetivas |

## 8. Memória interna do projeto

Outros documentos relevantes em `/workspace/memoria-tcc/`:

- `04-biblioteca-oficial.md` — REF-001 a REF-046
- `06-decisoes-do-gestor.md` — Decisões 001 a 014
- `07-pendencias.md` — P-001 a P-025 (P-017 e P-025 resolvidas)
- `13-planejamento-tcc1.md` — cronograma Sem 1-12
- `19-metodologia-testes-sem7-9.md` — análise bibliográfica que gerou a Tabela 1
- `20-agentes-desenvolvimento.md` — definição dos agentes Senior e Worker

## 9. Apresentação parcial preparada

- Arquivo: `/workspace/apresentacao-tcc1.txt` — 6 slides para 5 minutos com fala sugerida.
- Slide 3 (Justificativa) recebeu versão melhorada (entregue por *chat*, não persistida no `.txt`).

## 10. Comandos típicos para a próxima sessão

Sandbox operacional para TLC, Python e agora compilação Maven. Para rodar:

```bash
# Ambiente completo (Java + Maven + TLC)
source ~/tools/env/tcc-dev.sh
mvn -v        # 3.9.9 / Temurin 17
java -version

# TLC
cd /workspace/tcc-desenvolvimento/spec
./run-tlc.sh {small|medium|full}

# Ambiente Python (já instalado)
~/tools/venv/bin/python /workspace/tcc-desenvolvimento/analysis/percentis.py <diretorio>

# Workload Maven (após merge de feature/workload-bootstrap)
cd /workspace/tcc-desenvolvimento/workload
mvn -q package    # compila + empacota; cluster ainda não sobe aqui

# Verificação rápida do estado do repo
cd /workspace/tcc-desenvolvimento
git status
gh pr list

# Reanexar autenticação do gh (se a sessão tiver expirado)
gh auth status   # se não autenticado, o autor roda: ! gh auth login -h github.com -p https -w
```

## 11. Ponto exato em que paramos

Última ação efetiva: PR #7 (`feat: Python analysis pipeline (B-16, B-17, B-18)`) aberto e aprovado pelo autor. Na sessão de 2026-05-27 (segunda passagem), o autor pediu para tentar instalar o que faltava. **Resultado: Maven instalado**; Podman e `tc-netem` continuam fora do sandbox por restrição de kernel/seccomp, não por falta de binário. Diagnóstico técnico registrado nas seções 3 e 4 acima.

Próximos passos viáveis aqui (sem cluster):
- Merge sequencial dos PRs #1–#7 (autor já aprovou) na ordem #1 → #2 → #3 → #5 → #6 → #4 → #7.
- Avanço do *backlog* B-05 a B-10 (workload em Java puro, com testes JUnit sem cluster) — agora compilável via `mvn` neste sandbox.
- B-11/B-12/B-13 (scripts de injeção e coleta) podem ser **escritos e revisados** aqui, mas só executam na máquina do autor.

Próximos passos restritos à máquina do autor:
- Subida do *cluster* Infinispan (Podman), execução do *workload* contra o cluster, injeção F1/F3 via `tc-netem`, baseline experimental.
