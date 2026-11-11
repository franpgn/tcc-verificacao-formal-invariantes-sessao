# 9. Rastreabilidade e reprodução

**Situação.** *Commits*, *tags* e *branches* conferidos com `git cat-file`, `git worktree list`, `git for-each-ref` e
`git ls-remote publico` em 11/11. SHA-256 conferidos com `sha256sum` nos arquivos do *host* ou copiados dos arquivos
`.sha256` e do `LEIAME.txt` que os acompanham (marcado). Caminhos relativos a `C:\Users\Francesco\Development\tcc\`.

## 9.1 Onde está o código

| Item | Situação em 11/11 |
|---|---|
| repositório local | `tcc-desenvolvimento/` (git), com 60 *worktrees* `wt-*` na raiz `tcc/` |
| ramo principal `main` | `14de422` (16/06/2026), local e em `origin`; **não contém nada depois de junho** |
| ramo de integração | `feature/controlador-integracao` = `4b96798` (*worktree* `wt-ctrl-integ`), **só local** |
| remoto `origin` | `github.com/franpgn/tcc-desenvolvimento`, até junho (`main` = `14de422`, `feature/f3-baseline` = `d1b82ae`) |
| remoto `publico` | `github.com/franpgn/tcc-verificacao-formal-invariantes-sessao`, backup de 04 e 05/11: `feature/controlador-integracao` = `19d45b9` (*tag* `int/2026-11-04-w3`), `monografia` = `921c556`, `docs/plano-implementacao-controlador` = `87efde3` |
| *tag* da avaliação | `int/2026-11-06-aval` = `4b96798`, **só local**; nenhum ramo remoto a contém |

O envio ao `publico` foi aprovado pelo Gestor em 08/11 (crônica do coordenador) e ainda não foi feito. Pela política
do projeto, nada vai a `origin/*` ou ao `publico` sem aprovação explícita a cada envio; o coordenador decide.

## 9.2 *Tags* e *branches* por artefato

| Artefato | *Tag* ou *branch* (*commit*) | Onde |
|---|---|---|
| código da avaliação fatorial (programa de carga, controlador, extensão A1, orquestrador, análise, injetores, *cluster*) | *tag* `int/2026-11-06-aval` (`4b96798`, 06/11 21:23 −03:00), ramo `feature/controlador-integracao` | `wt-ctrl-integ`; cópia extraída em `dados-vm/cs3-2026-11/bundle-fat-4b96798/arv/cs3-4b96798/` |
| código da recalibração X-19 | *tag* `int/2026-11-06-bench` (`cc2b71c`) | idem, *bundle* `dados-vm/cs3-2026-11/bundle-rc-cc2b71c/` |
| código do ensaio X-17 e da calibração X-18 | *tag* `int/2026-11-05-w6` (`1f65a02`) | *bundle* `dados-vm/cs3-2026-11/bundle-1f65a02/` |
| A1 integrada | *tag* `int/2026-11-06-a1` (`fbf8882`); desenvolvimento em `feature/merge-policy-semirreticulado` (`288b01c`, `wt-a1`) | |
| estudo A4 (lista de ignorados no *cluster* local) | `test/a4-ignored-caches` (`290ff40`, `wt-a4`) | `docs/relatorio-a4-2026-11-06.md` |
| AC-7 no controlador | `feature/p1-ignorar-cache` (`6e7a13c`, `wt-p1ic`); guarda contínua `78a151d` | `docs/relatorio-p1-ignorar-cache-2026-11-06.md` |
| conferidor e análise com o AC-7, *logs* (P-64) | `feature/p1ic-conferidor-analise` (`e672051`, `wt-p1ic-ca`) | |
| orquestrador fatorial | `feature/orquestrador-fatorial` (`b56b1ae`, `wt-fat`); integrado em `9cf777a` | `docs/relatorio-orquestrador-fatorial-2026-11-06.md` |
| análise 2×2 (`comparar_bracos.py`) | `feature/w5e-fatorial` (`cb79822`, `wt-w5e`) | |
| sonda de *replay* | `feature/s3-sonda-replay` (`7c94cfb`, `wt-w10`) | |
| derivação dos limiares e conferências | `feature/w9-calibracao-s3` (`ddc7700`, `wt-w9`); conferências `0c6b7c0` (X-18) e `ddc7700` (X-19) | `docs/validacao-derivacao-*.md` em `wt-w9` |
| TLC da fusão por máximo (`MesclarJoin`) | `feature/tla-mesclar-join` (`4abd157`, `wt-tla-join`) | `docs/relatorio-tla-mesclar-join-2026-11-06.md` |
| pré-registros DT-018 a DT-028 | `docs/pre-registro-benchmark-refeito` (`2a97073`, `wt-bench`); *commits* por DT na tabela 6.1 | `wt-bench/docs/decisoes-tecnicas.md` e `pre-registro-*.md` |
| roteiros da VM, pré-análise, decisões, validação, este relatório | `docs/plano-implementacao-controlador` (`wt-plano-ctrl`): roteiro `299ee6c`…`2390fbb`, pré-análise `51cf460`, decisões `1732023`, validação `da28b0c`, capítulo 5 `fac6b59` | `wt-plano-ctrl/docs/` |
| nova medição de referência (*jar* congelado) | *tag* `int/2026-10-28-20805b0`; *jar* `5eced136…` | `dados-vm/rebaseline-2026-10/jar/` |
| critério h4 | *tag* `int/2026-11-05-h4` (`e869a31`); lista `e9c44819…` | DT-018 |

## 9.3 Dados brutos, análises e SHA-256 principais

| Artefato | Caminho | SHA-256 |
|---|---|---|
| *bundle* da avaliação (`git archive` da *tag*) | `dados-vm/cs3-2026-11/bundle-fat-4b96798/cs3-4b96798.tar` | `e880fd6eb4f1be0df9939e75e3ae4a009fda403f2c6f88baf22b276614596b8b` |
| *jar* do programa de carga | `…/bundle-fat-4b96798/session-workload-0.1.0-SNAPSHOT.jar` | `6fda93306421d0d4e3cc5dabaa8f5d97296b3d3c4ce6795ccd4db09c8ca2e54c` |
| *jar* da extensão A1 | `…/bundle-fat-4b96798/tcc-extensao-servidor.jar` | `8358812783305339f779e7fea559321a18c20c59f09bdde2d48f255e75279751` |
| identidade do *bundle* gravada pelo orquestrador | campo `bundle_sha256` em todas as execuções | `c5a6825f1219cf70c953e3e8a65bf963a1a7ffda88a1838e7f30866399eea010` |
| parâmetros do controlador | `fat-20261108T051402Z/entrada-fat/controlador-avaliacao-s3.json` | `163b88b01a6d049eab86cdebe4357e2ea17f738166f1710f9ce561f3c52ff032` |
| parâmetros da campanha | `…/entrada-fat/campanha-s3-avaliacao.json` | `5959a3495ca8398667135f60d968f992c382354c7ce4b79829d720ee078c1e8d` |
| matriz fatorial | `…/entrada-fat/matriz-fatorial.tsv` | `406a845cd785cb291a097afd7d3341d3116517d42e0e4af368cf76c789b5d726` |
| referência de p99 ASYNC e SYNC | `…/entrada-fat/referencia-S3-async.json`, `-sync.json` | `dec07fb76352e85ea014def9f8c52f5b0b416663ddda4f3c60f4676c660fd396`, `32bb1c60d13611eed70b4ffae3a276e0e318dbee9f3d2393a3545a653e6ad741` |
| derivação da recalibração (DT-028) | `dados-vm/cs3-2026-11/derivacao-recal-20261106T145106Z/calibracao-s3-derivacao.json` | `b183527a4c88476a1feda51bfd6e7fedb832949344d04ad59c80606b25126121` |
| derivação da calibração cega (DT-025) | `dados-vm/cs3-2026-11/derivacao-20261106T050404Z/calibracao-s3-derivacao.json` | `e904b0149a779ae01cbb4506e05f5789935229d4cec0ab344cbf10ddc79f8eed` |
| brutos da avaliação (pacote da VM) | `dados-vm/cs3-2026-11/fat-20261108T051402Z/fat-brutos-20261108T051402Z.tar` (252 MB) | `1bd09cafbc77ab7d55558adc2791c18b166432f84e73bc872d58dc60c6e405d6` (arquivo `.sha256`) |
| infraestrutura da avaliação (pacote da VM) | `…/fat-infra-20261108T051402Z.tar` | `e770539f1ec5f6384e7b7ace4d343e7435d50e0c1721ed7db4837a108eeaa062` (arquivo `.sha256`) |
| brutos extraídos | `…/campanha-s3-fatorial/` (90 pastas; `MANIFEST.sha256` em 86) | por execução |
| vereditos finais | `…/fat-infra/execucoes-final.tsv` | `1430f00e84b03fe4575b6dd60ad572339508083a45015da0ada3fdfb65a294c4` |
| manifesto de braços (blocos completos) | `…/campanha-s3-fatorial/manifesto-bracos.tsv` | `843a2a6f0e782685c0318b0f7a0340fd18768c46f818838513bfc8c3354775ea` |
| reposições | `…/fat-infra/repor.txt` (lista `reposicoes.tsv` `1d2cbcc0…` na VM) | `8cc787ce8b6664c9320d3ad384ffa0dcf9719a935a4f3683ab3c70dfde0532dc` |
| entrega à análise | `…/ENTREGA-ANALISE.txt` | `37a2770c56d5a101df2f90f9a83e98ee798a6f2c5142d3a54ce3c04bc2cdf3af` |
| análise oficial | `…/analise-oficial/comparacao-principal.json`, `.md`, `comparacao-semfalha.json`, `.md` | `335208e5…`, `a9a1611c…`, `6a14899a…`, `158ca7a6…` (`LEIAME.txt`) |
| manifestos da análise | `…/analise-oficial/trabalho/campanha-s3-fatorial/manifesto-principal.tsv`, `manifesto-semfalha.tsv` | `93221466…`, `936445cd…` (`LEIAME.txt`) |
| recalibração X-19 | `dados-vm/cs3-2026-11/rc-20261106T144744Z/rc-brutos-20261106T144744Z.tar`, `rc-infra-…tar` | `89a11974854ea67bd7c09b7d3862ae593ce0e04ae4393f2aa1813079710d6e85`, `b9c2e63b8d69fb6fa610a2b1face1d209073c1ad04b9d4eea161730329656624` |
| calibração X-18 | `dados-vm/cs3-2026-11/x18-20261106T050100Z/x18-brutos-…tar`, `x18-infra-…tar` | `48dfac81e33ed73cc56d16d5acdf7f44d416599667b230d7e598a513fa4ac323`, `269badc60012d2edccdb547d67979f361b53e4b757265ad6ccb3f0bfc04a6600` |
| piloto de violações | `dados-vm/piloto-2026-11-06/piloto-20261106T081543Z.tar` | `80a792654443294271b7edec2d403030925106a8c1af5535954b43646b86664a` |
| nova medição, noite 1 | `dados-vm/rebaseline-2026-11/noite1-completa-20261105/` | por execução (`MANIFEST`) |
| bateria de junho | `dados-vm/baseline-2026-06-23/` | `sha256-vm.txt` por arquivo |
| séries do *host* (`typeperf`) | `dados-vm/cs3-2026-11/host-proc-io-fat.csv` (núcleo, até a queda) e `host-proc-io-fat-extra-20261107T150858Z.csv` | não calculado (33,5 e 65,4 MB) |
| prévia de 07/11 (não oficial) | `dados-vm/cs3-2026-11/fat-nucleo-previa-20261107T150934Z/` | `nucleo-previa-20261107T150934Z.sha256` |
| pré-análise (consultas) | `wt-plano-ctrl/docs/analise-preliminar-fatorial-2026-11-07/` | versionado (`51cf460`) |
| validação (código independente) | `wt-plano-ctrl/docs/validacao-analise-oficial-2026-11-11/` | versionado (`da28b0c`) |
| recontagem do controlador (este relatório) | `wt-plano-ctrl/docs/relatorio-tecnico-final/scripts/trilhas_controlador.py` e a saída | versionados neste *commit* (o Git normaliza o fim de linha da saída, então o SHA do arquivo no disco do Windows, `463a73a3…`, difere do do *blob*) |

Os *jars* não são reprodutíveis byte a byte (carimbos de tempo do Maven); vale o SHA do arquivo levado à VM, gravado no
roteiro §2.1 e em cada `execucao.json` (`jar_sha256`, `extensao_sha256`).

## 9.4 Como reproduzir

Requisitos: Python 3.12.6, numpy 2.2.0, scipy 1.16.0; Git Bash no Windows ou Linux. Sempre `python -E -P` (a
recontagem e o recálculo independente rodam também com `-I`). Nunca gravar nos brutos.

```bash
cd /c/Users/Francesco/Development/tcc/dados-vm/cs3-2026-11/fat-20261108T051402Z

# 1. integridade dos pacotes e das execuções
sha256sum -c fat-20261108T051402Z.sha256
for d in campanha-s3-fatorial/exec-*; do [ -f "$d/MANIFEST.sha256" ] && (cd "$d" && sha256sum -c --quiet MANIFEST.sha256); done

# 2. código da tag (a árvore já extraída serve; para refazer a partir do Git, sem CRLF):
#    git -C ../../../tcc-desenvolvimento -c core.autocrlf=false archive 4b96798 | tar -x -C <pasta-nova>
ARV=../bundle-fat-4b96798/arv/cs3-4b96798

# 3. cópia de trabalho (os energia-rep-001.json vão nela, nunca nos brutos)
mkdir -p /tmp/repro && cp -r campanha-s3-fatorial /tmp/repro/
#    manifestos da análise: os de analise-oficial/trabalho/campanha-s3-fatorial/

# 4. energia por execução (células com falha: com marcadores e convergência; sem falha: sem eles)
python -E -P $ARV/analysis/energia_proxy.py --execucao /tmp/repro/campanha-s3-fatorial/exec-0002-ASYNC-F2-PL \
  --rep 1 --cenario S3 --w-recovery-s 191 --p-max-w 181 --razoes-ocioso 0.3,0.5 \
  --marcadores /tmp/repro/campanha-s3-fatorial/exec-0002-ASYNC-F2-PL/marcadores.txt \
  --convergencia /tmp/repro/campanha-s3-fatorial/exec-0002-ASYNC-F2-PL/convergencia-resumo-rep-001.json \
  --saida /tmp/repro/campanha-s3-fatorial/exec-0002-ASYNC-F2-PL/energia-rep-001.json
#    (repetir para cada execução do manifesto)

# 5. comparação oficial (principal e sem falha)
python -E -P $ARV/analysis/comparar_bracos.py --manifesto /tmp/repro/campanha-s3-fatorial/manifesto-principal.tsv \
  --semente-boot 20261108 --delta-latencia 0.10 --delta 0.10 --w-analise-s 4 --w-recovery-s 191 \
  --celulas-teste ASYNC-F2 --celulas-descritivas ASYNC-F3,ASYNC-F1,ASYNC-F4 \
  --p-max-w 181 --razao-ocioso-central 0.3 --saida /tmp/repro/comparacao-principal.json
python -E -P $ARV/analysis/comparar_bracos.py --manifesto /tmp/repro/campanha-s3-fatorial/manifesto-semfalha.tsv \
  --semente-boot 20261108 --delta-latencia 0.10 --delta 0.10 --w-analise-s 4 --w-recovery-s 191 \
  --celulas-descritivas ASYNC-none --fases antes \
  --p-max-w 181 --razao-ocioso-central 0.3 --saida /tmp/repro/comparacao-semfalha.json

# 6. conferir com os SHA-256 do LEIAME (a validação de 11/11 obteve igualdade byte a byte)
sha256sum /tmp/repro/comparacao-*.json analise-oficial/comparacao-*.json

# 7. recálculo independente e diagnósticos (wt-plano-ctrl/docs/validacao-analise-oficial-2026-11-11/)
V=/c/Users/Francesco/Development/tcc/wt-plano-ctrl/docs/validacao-analise-oficial-2026-11-11
python -I $V/valida_fatorial.py campanha-s3-fatorial analise-oficial/comparacao-principal.json \
  analise-oficial/comparacao-semfalha.json /tmp/repro/recalculo.json
python -I $V/compara.py /tmp/repro/recalculo.json analise-oficial/comparacao-principal.json analise-oficial/comparacao-semfalha.json
python -I $V/steal.py analise-oficial/trabalho/campanha-s3-fatorial
python -I $V/sens_energia.py analise-oficial/trabalho/campanha-s3-fatorial analise-oficial/comparacao-principal.json

# 8. comportamento do controlador (capítulo 4 deste relatório)
python -E -P /c/Users/Francesco/Development/tcc/wt-plano-ctrl/docs/relatorio-tecnico-final/scripts/trilhas_controlador.py campanha-s3-fatorial
```

Os comandos de 4 e 5 são os do `analise-oficial/LEIAME.txt`; o uso dos *scripts* do passo 7 está no cabeçalho de cada
um. Não rodei de novo os passos 4 a 7 para este relatório: a reprodução byte a byte foi feita na validação de 11/11
(`validacao-analise-oficial-2026-11-11.md` §3). Rodei o passo 8 (saída em `scripts/trilhas_controlador-saida.txt`).

**Reproduzir as execuções na VM** exige o roteiro `docs/roteiro-avaliacao-fatorial-2026-11-06.md` (passos 2.1 a 7.3,
com a retomada do §5.0), a VM Hyper-V (`docs/runbook-vm-hyperv.md`) e o *bundle* acima; a ordem e as sementes saem da
matriz `406a845c…`. A intercalação entre *threads*, as respostas do *grid* e o instante exato da falha dentro da janela
não são reprodutíveis, porque dependem do tempo (`Metodologia.tex:318`, sobre a semente).

## 9.5 Tabelas para a monografia

### T9.1 Artefatos e identificação (NOVA, em apêndice, por exemplo "Artefatos da avaliação")

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Artefatos da avaliação do controlador e sua identificação}
\label{qd-artefatos-avaliacao}
\centering
\footnotesize
\begin{tabular}{p{4.8cm}p{4.4cm}p{4.6cm}}
\hline
\textbf{Artefato} & \textbf{Identificação} & \textbf{Conteúdo} \\
\hline
Código da avaliação & \emph{tag} \texttt{int/2026-11-06-aval}, \emph{commit} \texttt{4b96798} & programa de carga, controlador, extensão do servidor, orquestrador e análise \\
Programa de carga & SHA-256 \texttt{6fda9330\ldots} & \emph{jar} executado nas 90 execuções \\
Extensão do servidor & SHA-256 \texttt{83588127\ldots} & política de fusão A1 \\
Parâmetros do controlador & SHA-256 \texttt{163b88b0\ldots} & limiares da recalibração \\
Parâmetros da campanha & SHA-256 \texttt{5959a349\ldots} & falhas, carga S3 e tetos \\
Matriz de execução & SHA-256 \texttt{406a845c\ldots} & ordem, braços e sementes \\
Dados brutos & SHA-256 \texttt{1bd09caf\ldots} & 90 execuções \\
Análise oficial & SHA-256 \texttt{335208e5\ldots} & comparação entre braços \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

Se o repositório público receber a *tag* antes da entrega, o texto pode citar o endereço; hoje ele não a contém
(seção 9.1).

## 9.6 Já está na monografia × novo ou mudou

| Arquivo:linha | O que o texto diz | Situação |
|---|---|---|
| `Desenvolvimento.tex:96-132` (`sec-estado-artefatos`) | artefatos "ao final do Trabalho de Conclusão de Curso I", com acréscimos "até outubro de 2026" | **atualizar** com a *tag* da avaliação, a extensão do servidor e as suítes de 07/11 (capítulo 4, §4.10) |
| `Desenvolvimento.tex:138` | suíte de testes, "jar congelado para a nova medição" | **atualizar**: dois *jars* (o da nova medição, `5eced136…`, e o da avaliação, `6fda9330…`) e as contagens da *tag* |
| `ApPlanoTCC2.tex:66-70` (APX-09) | "O código desenvolvido, hoje em versões locais, é integrado à versão principal do repositório" | **continua verdadeiro**: não foi integrado; decidir se entra como pendência ou se o envio será feito antes da entrega |
| não existe | identificação por SHA-256, caminhos dos dados, como reproduzir | **novo** (apêndice) |

## 9.7 Fontes bibliográficas necessárias

Nenhuma nova. Se o texto citar a ferramenta de controle de versão ou o Podman: `podman_docs` existe; Git não tem
entrada no `.bib` (o Bibliotecário decide se é preciso).

## 9.8 Pendências e o que não pôde ser verificado

- **Envio ao repositório público:** aprovado em 08/11, não feito; a *tag* `int/2026-11-06-aval` e os ramos de 05 a
  11/11 estão só no repositório local.
- **Integração em `main`:** `main` está em `14de422` (16/06); a integração (`feature/controlador-integracao`) não foi
  levada a `main`.
- **SHA-256 das séries do `typeperf`** e dos pacotes da nova medição: não calculados para este relatório.
- **Passos 4 a 7 da reprodução** não foram refeitos por mim; a garantia é a da validação de 11/11.
- **Dados fora do Git:** `dados-vm/` não é versionado; a única cópia dos brutos é o disco do *host*, que teve SSD
  degradado em 07/11. Recomendo cópia em outro meio antes da entrega.
