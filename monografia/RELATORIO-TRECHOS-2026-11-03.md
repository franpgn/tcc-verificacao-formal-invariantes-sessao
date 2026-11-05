# Relatório: trechos retirados dos Caps. 1 a 3 levados aos Caps. 4 e 5 (03/11/2026)

Entrada: `TRECHOS-PARA-DESENVOLVIMENTO.md` (56 trechos). Arquivos editados: só `Desenvolvimento.tex` e `ConclusoesParciais.tex`. Backup prévio: `_backups/monografia-atual-20261103-pre-trechos/`.
IDs novos: DES-39 a DES-56 (Cap. 4) e CON-23 a CON-25 (Cap. 5). Nenhum ID existente foi reutilizado. Nenhuma citação nova: todas as chaves usadas já estavam no `.bib` (`fan_power_provisioning_2007`, `kansal_vm_power_2010`, `bailis_kingsbury_network_2014`, `nosek_preregistration_2018`, `infinispan_docs_stable`).
Bloco novo sem número: `\subsection*{Revisões em relação ao planejamento}` com `\label{sec-revisoes}`, no fim da Seção 4.5 (p. 79 a 82). Os `\ref{sec-revisoes}` dos trechos originais que apontavam para o próprio bloco foram retirados. As remissões do Cap. 5 apontam para ele e saem como "Seção 4.5".

Páginas = numeração impressa (lidas do `.aux`; conferidas no PDF: PDF p. 80 = impressa p. 79).

## Tabela por trecho (ordem do arquivo de entrada)

| # | Origem | Destino final | Ação | Pág. |
|---|---|---|---|---|
| 1 | INT-03 | Desenvolvimento.tex, 4.5 Revisões, DES-55 | fundido: as "três classes" são as da Introdução; a frase do *state transfer* foi unida à equivalente de MET-18 | 80 |
| 2 | INT-04 | (nenhum) | descartado (pedido do autor) | |
| 3 | INT-05 | Desenvolvimento.tex, 4.4, DES-47 | fundido com MET-27 e MET-42 num parágrafo sobre coletores e energia | 73 |
| 4 | INT-08 | (nenhum) | descartado (pedido do autor) | |
| 5 | INT-13 | ConclusoesParciais.tex, 5.2, CON-25 | inserido (fragmento transformado em frase sobre o objetivo 3) | 90 |
| 6 | INT-14 | (nenhum) | descartado (pedido do autor) | |
| 7 | INT-15 | ConclusoesParciais.tex, 5.2, CON-25 | inserido (frase sobre o objetivo 5) | 90 |
| 8 | INT-16 | Desenvolvimento.tex, 4.5 Revisões, DES-52 | inserido, unido ao 1º parágrafo de MET-44 (renovação de sessão dita uma vez só) | 79 |
| 9 | INT-17 | (nenhum) | descartado (pedido do autor) | |
| 10 | INT-20 | (nenhum) | descartado (pedido do autor) | |
| 11 | FUN-18 | Desenvolvimento.tex, 4.4 Controlador, DES-48 | inserido; tirada a autorreferência "O projeto aparece na Seção sec-controlador"; remissão a sec-fund-adapt | 74 |
| 12 | FUN-26 | (nenhum) | descartado (pedido do autor) | |
| 13 | FUN-27 | (nenhum) | descartado por redundância com DES-25 ("apenas lê a saúde dos nós pela API REST") | |
| 14 | FUN-15 | (nenhum) | não aplicado: o destino é a Metodologia; o pré-registro já aparece em MET-41 e MET-43 (Cap. 3) | |
| 15 | MET-01 | ConclusoesParciais.tex, Cap. 5 (síntese), CON-23 | fundido: a lista do que o texto reporta repete CON-02 e saiu; ficou a remissão às revisões | 83 |
| 16 | MET-03 | ConclusoesParciais.tex, 5.1, CON-24 | fundido (nomes das bases e Portal CAPES) | 87 |
| 17 | MET-05 | ConclusoesParciais.tex, 5.1, CON-24 | fundido: o plano das semanas 5 e 6 já estava no parágrafo; entraram o foco da leitura integral e os três produtos que fecham o mapeamento | 87-88 |
| 18 | MET-06 | Desenvolvimento.tex, 4.5, DES-49 | inserido; a parte do controlador já estava em DES-28 e ficou só lá | 78 |
| 19 | MET-07 | (nenhum) | descartado por redundância com DES-04 (filas por réplica, entrega a qualquer momento) | |
| 20 | MET-08 | (nenhum) | descartado por redundância com DES-08 (configurações maiores interrompidas pelo tempo-limite; só `tiny` exaustiva) | |
| 21 | MET-09 | Desenvolvimento.tex, 4.2, DES-39 | fundido com MET-45; "as duas ferramentas" = Apalache e Must (Seção 3.2, escolha do ferramental) | 61 |
| 22 | MET-10 | Desenvolvimento.tex, 4.2, DES-40 | inserido (função `dono`) | 62 |
| 23 | MET-11 | Desenvolvimento.tex, 4.2, DES-40 | inserido (O1 no modelo) | 62 |
| 24 | MET-12 | Desenvolvimento.tex, 4.2, DES-40 | inserido (O4 sem cascata; só O6 tem cascata) | 62 |
| 25 | MET-14 | Desenvolvimento.tex, 4.4, DES-46 | fundido: "as quatro têm injetor" já está em DES-21; entrou a correspondência com o modelo formal | 73 |
| 26 | MET-16 | (nenhum) | descartado por redundância com DES-16 e DES-17 (verificação sem carga; exercício sob carga na campanha S3) | |
| 27 | MET-17 | Desenvolvimento.tex, 4.5 Revisões, DES-55 | inserido | 80 |
| 28 | MET-18 | Desenvolvimento.tex, 4.5 Revisões, DES-55 | inserido | 80-81 |
| 29 | MET-19 | (nenhum) | descartado (pedido do autor) | |
| 30 | MET-20 | Desenvolvimento.tex, 4.2, DES-41 | fundido: o 4.2 já definia a quiescência como "canal vazio"; ficou a forma com filas e a declaração sobre `dono` | 63 |
| 31 | MET-21 | Desenvolvimento.tex, 4.2, DES-41 | fundido: a violação esperada já estava em DES-08; entrou "I2 avaliado para O6" | 63 |
| 32 | MET-22 | Desenvolvimento.tex, 4.2, DES-41 | inserido (fórmula de I5 ampliada) | 63 |
| 33 | MET-23 | Desenvolvimento.tex, 4.2, DES-41 | inserido (fórmula de I6 ampliada, quebrada em duas linhas para caber na margem) | 63 |
| 34 | MET-24 | Desenvolvimento.tex, 4.2, DES-41 | fundido: as formas de faixa e de tipo já estão em DES-08 (remissão); mantidos o registrador com `dono`/marca, I4 temporal réplica a réplica e I3 temporal não verificada | 63 |
| 35 | MET-25 | Desenvolvimento.tex, 4.4, DES-45 | fundido: as rodadas a cada 5 s já estão em DES-03 e DES-19; entraram o motivo (quiescência), o tempo-limite e a remissão a sec-carga | 72 |
| 36 | MET-26 | Desenvolvimento.tex, 4.4, DES-45 | inserido (medição do tempo de convergência, teto de 600 s, comparação de `dono`) | 72 |
| 37 | MET-27 | Desenvolvimento.tex, 4.4, DES-47 | inserido | 73 |
| 38 | MET-29 | Desenvolvimento.tex, 4.5 Revisões, DES-56 | fundido com o trecho de MET-44 sobre análise estatística | 81-82 |
| 39 | MET-32 | Desenvolvimento.tex, 4.5 Revisões, DES-55 | fundido ("a lognormal do plano (T18) deu lugar à normal na execução") | 80 |
| 40 | MET-38 | Desenvolvimento.tex, 4.5 Revisões, DES-54 | fundido: o achado do DIST_SYNC já está em DES-12 e DES-35; ficaram o T1 como alvo e a justificativa de 10^6/30 | 79-80 |
| 41 | MET-39 (diagnóstico) | Desenvolvimento.tex, 4.3, DES-43 | fundido em DES-13: as três causas já estavam lá; entraram o limiar 5, O5 com a marca preservada, a ausência de O7, ~130/500 operações e as probabilidades; o comentário de fonte foi mantido | 68 |
| 42 | MET-39 ("Cada thread t passa a usar") | (nenhum) | descartado por redundância com DES-13 e DES-19 (e com a Seção 3.2, carga) | |
| 43 | MET-39 ("As chaves lidas... passam a existir") | (nenhum) | descartado por redundância com DES-13 e DES-19 | |
| 44 | MET-39 (limiares) | Desenvolvimento.tex, 4.3, DES-44 | inserido | 68-69 |
| 45 | MET-40 (S3 em out/2026) | Desenvolvimento.tex, 4.5 Revisões, DES-54 | fundido | 80 |
| 46 | MET-40 (bloqueio por limite) | Desenvolvimento.tex, 4.2, DES-40 | fundido com MET-12 | 62 |
| 47 | MET-41 (substitui a bateria de junho) | (nenhum) | descartado por redundância com DES-13 e DES-14 | |
| 48 | MET-41 (pré-registro em out/2026) | Desenvolvimento.tex, 4.5, DES-51 | inserido | 78 |
| 49 | MET-41 (piloto e campanha não executados) | Desenvolvimento.tex, 4.5, DES-51 | fundido: DES-30 já diz que a medição não foi executada; entrou o piloto | 78 |
| 50 | MET-41 (F1 com podman kill) | Desenvolvimento.tex, 4.3, DES-42 + 4.5 Revisões, DES-54 | fundido e dividido, sem duplicar: o mecanismo do SIGTERM ficou no 4.3 e a troca de outubro, com o ensaio local, nas Revisões; o ensaio na VM converge com DES-17 | 67 / 79 |
| 51 | MET-41 (desvios em relação à Tabela 3) | Desenvolvimento.tex, 4.5 Revisões, DES-54 (2º parágrafo) | inserido; a frase da distribuição normal em F3 foi para DES-55, para não repetir | 80 |
| 52 | MET-42 | Desenvolvimento.tex, 4.4, DES-47 | inserido | 74 |
| 53 | MET-43 | Desenvolvimento.tex, 4.5, DES-50 | fundido: DES-25 já lista o pré-registro e a avaliação como pendentes; ficou a frase de estado | 78 |
| 54 | MET-44 (bloco inteiro) | Desenvolvimento.tex, 4.5 Revisões, DES-52 a DES-56 | fundido: §1 com INT-16; §2 resumido (os detalhes de setembro e outubro estão em DES-05, com remissão à 4.2); §3 resumido (DES-12, DES-13 e DES-16 já cobrem modo, carga e F2/F4); §4 resumido (a restrição ao cliente já está em DES-25 e em sec-controlador-cluster) | 79-82 |
| 55 | MET-45 | Desenvolvimento.tex, 4.2, DES-39 | fundido com MET-09 | 61 |
| 56 | MET-47 | Desenvolvimento.tex, 4.4 Controlador, DES-48 | inserido; referente corrigido (ver dúvida 1) | 75 |

## Contagem por ação

- Inserido: 19
- Fundido: 22
- Descartado por redundância com texto do Cap. 4: 7 (FUN-27, MET-07, MET-08, MET-16, MET-39 nº 42 e 43, MET-41 nº 47)
- Descartado por pedido do autor: 7 (INT-04, INT-08, INT-14, INT-17, INT-20, FUN-26, MET-19)
- Não aplicado: 1 (FUN-15)
- Total: 56

## Dúvidas para o autor

1. **MET-47.** O destino sugerido dizia "Nguyen orienta carga e controlador". O texto original (`tcc-escrita/FundamentacaoTeorica.tex`) mostra que o sujeito de "orientam" eram os contraexemplos a priori do TLC, não Nguyen et al. Usei os contraexemplos. Confirme que eles de fato orientaram a escrita do programa de carga.
2. **MET-27.** O coletor das métricas secundárias aparece sem o nome do script. A Seção 4.4 descreve `collect-metrics.sh` (T19/T20), e o Cap. 5 (l. 21) diz que o "coletor de métricas OpenMetrics" está implementado. Se for o mesmo script, vale nomeá-lo em DES-47 e ressalvar no Cap. 5 que ele não foi integrado nem usado.
3. **Texto antigo do Cap. 4 que segue em tensão com as atualizações.** Não foi alterado, porque está fora do escopo dos trechos. Exemplos: l. 23 ("a especificação inicial não modela falhas"), o parágrafo da bateria plena ("F2 e F4 seguem reservados", corrigido logo depois por DES-16), a política de partição ("injeção controlada de F2 está reservada") e a 2ª limitação da 4.5 ("não modela ainda ... partições", corrigida por DES-31).
4. **CON-25.** A redação "o objetivo (3) passou a prever a arquitetura experimental nos modos..." foi montada a partir de um fragmento. Confirme se a ampliação dos objetivos 3 e 5 deve ficar no Cap. 5 ou ir para o bloco de Revisões.
5. O bloco de Revisões ocupa cerca de 3,5 páginas, em 7 parágrafos. Se quiser um texto mais curto, o candidato natural a corte é o 2º parágrafo (verificação formal), que resume a 4.2.

## Compilação

`pdflatex` → `bibtex` → `pdflatex` ×2 em `monografia-atual/`, com `Main-Monografia.tex` (MiKTeX 25.12):
- 0 erros (`^!`), 0 referências ou citações indefinidas, bibtex sem avisos.
- 104 páginas (antes: 98; linha de base recompilada numa cópia no scratchpad, sem alterações).
- 4 avisos *overfull*, os mesmos da linha de base. A fórmula nova de I6, que passava 60 pt da margem, foi quebrada com `aligned`.
- Sem travessão `---` nos dois arquivos. Nenhum ID `revini` duplicado.

## Rodada 2 (03/11/2026, limpeza aprovada pelo autor)

Parti do arquivo com os ajustes de redação do Revisor. IDs novos: DES-57 a DES-71 e CON-26 a CON-32. Frase da banca alterada recebe ID novo. IDs de rodadas anteriores (DES-01 a DES-38, CON-01 a CON-22) foram alterados com ID novo aninhado. Os IDs da rodada 1 (DES-39 a DES-56, CON-23 a CON-24) foram editados no lugar. Páginas na numeração impressa.

### Frases da banca (CORREÇÃO / ATUALIZAÇÃO)

| ID | Tipo | O quê | Pág. |
|---|---|---|---|
| DES-57 | ATUALIZAÇÃO | 4.2: Must "ficaram, no planejamento, como alternativa ... caso ... exigisse" (passado); a situação atual vai para DES-39 | 61 |
| DES-58 | ATUALIZAÇÃO | 4.2: "a especificação inicial não modelava falhas" | 63 |
| DES-59 | ATUALIZAÇÃO | 4.2: refinamento com falhas "estava planejado para o TCC II, e a inclusão ... foi feita nessa etapa" | 63 |
| DES-60 | ATUALIZAÇÃO | 4.3: "Essas eram as configurações do TCC I, depois acrescidas da configuração `tiny`" (D5) | 64 |
| DES-64 | ATUALIZAÇÃO | 4.3: F2 e F4 "ficaram reservados, no planejamento, para o TCC II" | 69 |
| DES-66 | ATUALIZAÇÃO | 4.4: injeção de F2 "estava reservada, no planejamento"; o injetor foi implementado depois (remissão a sec-verificacao-injetores) | 71 |
| DES-68 | ATUALIZAÇÃO | 4.4: spec "acompanhada, no TCC I, de três configurações" (D5) | 72 |
| DES-69 | CORREÇÃO | 4.4: `collect-metrics.sh` na lista de scripts: "(escrito, mas não integrado, como registrado adiante)" (D2) | 73 |
| DES-70 | ATUALIZAÇÃO | 4.4: "cinco rodadas ... nas três configurações (T21), depois acrescidas da configuração `tiny`" (D5) | 74 |
| DES-71 | ATUALIZAÇÃO | 4.5, 2ª limitação: "no TCC I, capturava ... não modelava perdas permanentes nem partições" | 78 |
| CON-27 | ATUALIZAÇÃO | 5.1: F2 e F4 "ficaram reservados, no planejamento do TCC I, ao TCC II" | 87 |
| CON-28 | ATUALIZAÇÃO | Cap. 5, limitações: "três configurações (T21), depois acrescidas da configuração `tiny`" (D5) | 84 |
| CON-29 | ATUALIZAÇÃO | CON-02: "menor configuração, `tiny`, acrescentada depois das três do TCC I" (D5) | 82 |
| CON-30 | ATUALIZAÇÃO | Cap. 5: parágrafo da abstração do canal no passado ("No TCC I ... capturava ... era proposital ... integrava o plano") | 84 |
| CON-31 | ATUALIZAÇÃO | 5.2, "Em síntese": as quatro evoluções no passado ("era"), com o estado atual de cada uma e remissão às Revisões e a CON-13 | 89 |
| CON-32 | CORREÇÃO | Cap. 5, l.21: o coletor OpenMetrics saiu da lista de "implementados"; agora "foi escrito, mas não foi integrado ao código principal" (D2) | 84 |

### Correções factuais em IDs anteriores

| ID | Tipo | O quê | Pág. |
|---|---|---|---|
| DES-61 (em DES-08) | CORREÇÃO | I2 por réplica "vale para qualquer tipo de bloqueio e é violada em traços curtos, de 3 a 6 estados conforme o cenário"; os 28 contraexemplos vêm do bloqueio por limite (O4), por exemplo no traço *login* seguido de dois incrementos de tentativas falhas (D4) | 65 |
| DES-41 | CORREÇÃO | retirada a frase "I2 é avaliado para o bloqueio administrativo (O6)" (D4) | 63 |
| DES-47 | CORREÇÃO | o coletor tem nome (`scripts/collect-metrics.sh`), foi escrito em junho de 2026, existe só em ramos separados e nunca foi integrado ao ramo principal nem à versão atual (D2) | 73 |
| DES-48 | CORREÇÃO | "projetado e implementado em parte" (D3); a última frase volta à forma do autor: "orientam a escrita do programa de carga e o desenho do controlador" (D1) | 74 |

### Duplicações resolvidas

| ID | O quê | Pág. |
|---|---|---|
| DES-40 / DES-61 / DES-56 | bloqueio por limite sem cascata: ocorrência única em DES-40; em DES-08 ficou a causa com remissão à 4.2; em DES-56 ficou o motivo com remissão a sec-mc-resultados; em DES-41 a frase saiu | 62-81 |
| DES-62 (em DES-03), DES-67 (em DES-19), DES-45 | laço de convergência: DES-03 virou remissão à 4.4; o item de DES-19 diz "descrito a seguir"; DES-45 traz as rodadas de 5 s e diz que a nova medição não usa o laço | 61, 72 |
| DES-65 (em DES-16), DES-52, DES-55 | F4 redefinida e F2 com injetor: definição única em DES-55 §2 (a de MET-18: perda, atraso, duplicação ou reordenação, simétrica ou assimétrica); em DES-55 §1 ficou "definida adiante"; DES-16 virou remissão às Revisões; o ponto F4 saiu de DES-52 | 69, 79-80 |
| CON-26 (em CON-11) | F2, F3 e F4 trocados por "o conjunto de falhas foi revisto (Seção 4.5)" | 87 |
| DES-55 | calibração de F3 (20/13 ms, p99 de ~50 ms) retirada; ficou a remissão à Seção 4.3 (l.71 da banca) | 80 |
| DES-54 | "F1 silencioso só no cluster local" retirado; ficou a remissão a sec-verificacao-injetores (DES-17) | 79 |
| DES-54 | modo síncrono e *caches* assíncronos reduzidos a uma frase, com remissão às Seções 4.3 e 4.4 | 79 |
| DES-53, DES-63 (em DES-05) | o 2º parágrafo das Revisões virou remissão às Seções 4.2 e 4.3; o que só ele tinha (apoio no código-fonte, número de versão único, fidelidade ao sistema medido) foi para DES-05 como DES-63 | 63, 79 |
| DES-48 | frase MAPE-K reescrita como correspondência entre fases e componentes ("descritos a seguir"), sem repetir janelas e consultas da l.145 | 74 |
| CON-24 | ficaram só a concentração da leitura integral e a execução manual via CAPES nas bases nomeadas; a classificação e o fluxograma já estão na frase anterior e em CON-13 | 87 |
| CON-25 → DES-52 | D6: CON-25 saiu do Cap. 5; as ampliações dos objetivos (3) e (5), com a energia, foram para DES-52, e a frase de energia saiu de DES-56 | 79 |

### Fluxo

| ID | O quê | Pág. |
|---|---|---|
| DES-39 | movida para o fim do parágrafo de abertura da 4.2 e reescrita: o modelo com falhas foi escrito no próprio TLA+; Must e Apalache seguem sem uso (Must aparece uma vez com plano e outra com estado) | 62 |
| DES-46 | saiu do meio da lista de scripts de DES-21 e foi para o fim do parágrafo | 73 |
| DES-54 | a frase do S3 foi para junto da revisão da carga | 79 |
| DES-54 / DES-56 | Mann-Whitney: em DES-54, "na nova medição ... como análise secundária, sobre os p99 por execução"; em DES-56, a avaliação do controlador troca o Mann-Whitney (sec-metricas) pelo desenho em blocos pareados, e o Mann-Whitney fica só na medição de referência | 80-81 |
| DES-56 | "nenhum erro foi registrado" passou a "nas paradas de nó da bateria de junho ..., observação de valor apenas indicativo, porque essa bateria não criou sessões, mas que sugere que a detecção dependa de latência e topologia" | 81 |
| DES-49 | o trecho do modelo virou remissão ("além da inclusão de falhas no modelo formal (Seção 4.2)"), para não repetir DES-31 | 78 |

### Não tratado (aguarda o Bibliotecário)
FD_SOCK2 em DES-54; "simulação" ou "tempo de absorção" em DES-43; "raramente" de Bailis e Kingsbury em DES-55.

### Observação
A Seção de falhas da Metodologia (3.2) ainda define F4 como mudança de topologia com *state transfer* e F3 como lognormal. A definição atual de F4 está só em DES-55 (texto de MET-18) e o Cap. 3 não foi tocado. As remissões "Seção 3.2" em DES-52 e DES-54 levam o leitor à definição planejada, e isso é coerente com o padrão "planejado assim, modificado depois".

### Compilação da Rodada 2
pdflatex → bibtex → pdflatex ×2 (MiKTeX): 0 erros, 0 referências ou citações indefinidas, bibtex sem avisos, 4 avisos *overfull* (os mesmos da linha de base), **103 páginas** (104 depois da rodada 1). Sem travessão e sem ID duplicado. CON-25 não existe mais no texto.

## Rodada 3 (03/11/2026, correções de fonte do Bibliotecário, `FONTES-RODADA2-2026-11-03.md`)

| ID | Tipo | O quê | Pág. |
|---|---|---|---|
| DES-72 (em DES-54) | CORREÇÃO | O motivo da troca de F1 deixou de ser "por causa do detector FD_SOCK2". Agora: a parada ordenada não exercita a detecção, porque com SIGTERM o servidor fecha o canal JGroups e pede a saída ao coordenador, que instala a nova visão sem que nenhum detector suspeite do nó `\cite[seções 3.10.13 e 6.8.7]{jgroups_manual_5}`. O FD_SOCK2 explica só por que a interface é desativada antes do SIGKILL. É a versão com citação (CAND-085 aprovada, Decisão 020); a chave já estava no `.bib` | 80 |
| DES-73 (em DES-55) | CORREÇÃO | "raramente cortam" deu lugar a "nem sempre cortam toda a comunicação: ao lado de partições completas, o levantamento de `\citeonline[p.~50--53]{bailis_kingsbury_network_2014}` relata perdas de 50 % a 100 % dos pacotes, perturbações que afetam só alguns grupos de nós e uma placa de rede que descartava os pacotes recebidos, mas não os enviados" (redação longa sugerida) | 81 |
| DES-74 (em DES-43) | CORREÇÃO (só comentário) | A linha `% Fonte:` foi trocada pela do Bibliotecário: o documento diz "simulação" sem *script*; o valor exato é 1055/8 = 131,875 (S1) e 500 (S2), conforme o commit 51a31f0. Dentro de comentário a macro não funciona, então a marca é o texto `[DES-74]` no fim da linha. O texto impresso não mudou, e o ritmo de 1 260 op/s não entrou | 68 (comentário, não impresso) |

**Compilação da Rodada 3:** pdflatex → bibtex → pdflatex ×2 (MiKTeX, caminho completo): 0 erros, 0 referências ou citações indefinidas, bibtex sem avisos, 4 avisos *overfull* (os mesmos da linha de base), **104 páginas** (103 na rodada 2). `jgroups_manual_5` aparece nas Referências. No texto impresso, as citações saem como "(BAN, 2026, seções 3.10.13 e 6.8.7)" e "Bailis e Kingsbury (2014, p. 50–53)".

## Rodada 4 (03/11/2026, passada final do Revisor)

Parti da versão atual, já editada pelo Revisor.

| ID | Tipo | O quê | Pág. |
|---|---|---|---|
| CON-33 (em CON-08) | CORREÇÃO | A remissão das ações de queda, recuperação, partição e fusão passou de `sec-mc-resultados` para `sec-spec-formal` (DES-05) | 85 |
| DES-75 (em DES-42) | ATUALIZAÇÃO | O mecanismo do SIGTERM saiu da DES-42 e ficou só a remissão curta "mecanismo explicado na Seção 4.5" (sec-revisoes, DES-72, que tem a citação `jgroups_manual_5`) | 67 |
| DES-76 (em DES-54) | CORREÇÃO | A frase obscura ficou explícita: "A justificativa de 10^6 operações por execução e de 30 repetições, dada na tabela, refere-se ao desenho original e não se aplica ao protocolo pré-registrado em outubro de 2026" | 80 |
| DES-77 (em DES-60) | CORREÇÃO | "As cinco rodadas, de maio a julho de 2026, usaram só essas três configurações; a configuração `tiny` foi acrescentada depois, nas verificações de setembro de 2026, com o módulo `MCSession.tla`" | 64 |
| DES-78 (em DES-70) | CORREÇÃO | "cinco rodadas do TLC nas três configurações (T21); a configuração `tiny` foi acrescentada depois, nas verificações de setembro de 2026" | 74 |
| CON-34 (em CON-29) | CORREÇÃO | "`tiny`, acrescentada nas verificações de setembro de 2026, depois das cinco rodadas do TCC I" | 83 |
| CON-35 (em CON-28) | CORREÇÃO | "cinco rodadas ... nas três configurações (T21); a configuração `tiny` foi acrescentada depois, nas verificações de setembro de 2026" | 85 |
| DES-79 | CORREÇÃO (texto da banca) | Des. l.136: retirados "com 49 casos" e "com tempo de execução total inferior a meio segundo"; ficou "dispõe de suíte de testes JUnit \citep{junit5}, com seleção determinística da *seed* ...". A contagem só entra na versão final | 74 |

**Compilação da Rodada 4:** pdflatex → bibtex → pdflatex ×2 (MiKTeX, caminho completo): 0 erros, 0 referências ou citações indefinidas, bibtex sem avisos, 4 avisos *overfull* (os mesmos da linha de base), **104 páginas**. Sem ID duplicado.
