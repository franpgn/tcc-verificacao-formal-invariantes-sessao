# Trechos retirados dos Caps. 1 a 3 (prática / mudanças em relação ao plano)

Retirados em 01/11/2026, a pedido do autor: a parte teórica e metodológica não descreve a implementação atual nem o que mudou em relação ao plano. Cada trecho está aqui em LaTeX, com o destino sugerido, para ser reaproveitado no Desenvolvimento (Cap. 4) ou nas Considerações (Cap. 5). Nada disto está no texto agora.

## INT-03 (Introducao.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) / Revisões do planejamento (reunir no Cap. 4 ou 5)

```latex
Esse recorte, planejado com três classes, foi modificado no modelo de falhas (Seção~\ref{sec-falhas}), que passou a considerar quatro classes: além da queda de um nó seguida de \emph{failover} e da partição de rede, a terceira é o atraso com \emph{jitter} no canal entre os nós, e a quarta é a degradação parcial da rede, com perda, duplicação ou reordenação de mensagens sem corte completo da comunicação. O rebalanceamento e o \emph{state transfer}, que alteram o mapeamento entre chaves e nós \citep{infinispan_docs_stable}, aparecem como consequência da reentrada de um nó e da fusão de partições.
```

## INT-04 (Introducao.tex)

**Destino sugerido:** Descartado (pedido do autor ou sem uso) (pedido do autor)

```latex
Na versão atual do trabalho, a replicação entre localidades distintas, documentada para o Infinispan como replicação \emph{cross-site}, fica fora do recorte.
```

## INT-05 (Introducao.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (estimativa de energia)

```latex
Como o ambiente experimental deste trabalho é uma máquina virtual, sem acesso a contadores de energia do processador, o custo energético é estimado a partir do tempo de CPU consumido por \emph{container} e de um modelo de potência declarado \citep{fan_power_provisioning_2007, kansal_vm_power_2010}, conforme a Seção~\ref{sec-energia}.
```

## INT-08 (Introducao.tex)

**Destino sugerido:** Descartado (pedido do autor ou sem uso) (pedido do autor)

```latex
No projeto do controlador, essas políticas foram concretizadas como perfis de operação.
```

## INT-13 (Introducao.tex)

**Destino sugerido:** Cap. 5 (reflexões / próximas etapas, mudanças em relação ao plano) (objetivo 3 ajustado)

```latex
nos modos de replicação síncrono e assíncrono e com injeção de queda de nó, partição, atraso e degradação parcial da rede,
```

## INT-14 (Introducao.tex)

**Destino sugerido:** Descartado (pedido do autor ou sem uso) (pedido do autor); conteúdo cabe em 4.4 (controlador)

```latex
; no projeto atual, os sinais observáveis são de latência, erro, topologia e violação, as políticas são perfis de operação e as ações ajustam parâmetros do cliente e, em segunda etapa, da configuração do \emph{cluster}
```

## INT-15 (Introducao.tex)

**Destino sugerido:** Cap. 5 (reflexões / próximas etapas, mudanças em relação ao plano) (objetivo 5 ampliado com energia)

```latex
, além do tempo de CPU por operação útil, com a estimativa de energia correspondente
```

## INT-16 (Introducao.tex)

**Destino sugerido:** Cap. 5 (reflexões / próximas etapas, mudanças em relação ao plano) / Revisões do planejamento (reunir no Cap. 4 ou 5) (pedido do autor)

```latex
Três pontos desses objetivos diferem da Proposta original. A renovação de sessão constava do objetivo da Proposta, mas não entrou nas sete operações (O1 a O7, Seção~\ref{sec-operacoes}) definidas no recorte do Trabalho de Conclusão de Curso~I. Ela está fora do escopo desde o início, e não há operação de renovação no modelo formal nem no programa de carga. No modelo de falhas (Seção~\ref{sec-falhas}), o rótulo F4, antes atribuído à mudança de topologia com \emph{state transfer}, passou a designar a degradação parcial da rede. O objetivo (4) foi dividido em duas etapas: a primeira atua apenas sobre parâmetros do cliente, e a atuação sobre a configuração do \emph{cluster} é uma segunda etapa planejada, posterior à avaliação da primeira. A Seção~\ref{sec-revisoes} registra essas e as demais revisões do plano, com os respectivos motivos.
```

## INT-17 (Introducao.tex)

**Destino sugerido:** Descartado (pedido do autor ou sem uso) (pedido do autor)

```latex
Na versão parcial do Trabalho de Conclusão de Curso~II, esses capítulos foram ampliados. O Capítulo~\ref{cap-metodologia} passa a incluir o modelo de falhas, os invariantes, a carga, os protocolos de medição, o desenho de avaliação do controlador e as revisões do plano ocorridas ao longo do trabalho. O Capítulo~\ref{cap-desenvolvimento} passa a reunir a especificação formal com falhas e os resultados do TLC, a arquitetura experimental, o diagnóstico da primeira bateria de medição, as verificações do injetor de partição e o projeto e a implementação parcial do controlador. O Capítulo~\ref{cap-conclusao} sintetiza o estado atual, as limitações e as ameaças à validade, e o Apêndice~\ref{ap-plano-tcc2} detalha o plano de trabalho restante.
```

## INT-20 (Introducao.tex)

**Destino sugerido:** Descartado (pedido do autor ou sem uso) (pedido do autor)

```latex
cuja mensuração e reporte sistemáticos um editorial da \emph{Nature Computational Science} defende \citep{turner_carbon_footprint_2023}.
```

## FUN-18 (FundamentacaoTeorica.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (controlador, leitura MAPE-K)

```latex
Planejado assim no TCC I, o controlador foi projetado e implementado na etapa seguinte, e as configurações pré-definidas tomaram a forma de perfis entre os quais ele alterna. Na terminologia MAPE-K, o monitoramento agrega eventos do cliente e a saúde do \emph{cluster} em janelas de tempo, a análise compara esses sinais com limiares, o planejamento é a máquina de estados que escolhe o perfil, e a execução aplica o perfil aos parâmetros do cliente. O projeto aparece na Seção~\ref{sec-controlador}.
```

## FUN-26 (FundamentacaoTeorica.tex)

**Destino sugerido:** Descartado (pedido do autor ou sem uso) (frase solta, pedido do autor)

```latex
Previsto assim no TCC I, o controlador foi depois projetado e implementado (Seção~\ref{sec-controlador}).
```

## FUN-27 (FundamentacaoTeorica.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (controlador lê a saúde pela API REST)

```latex
Na implementação feita depois, o controlador lê a saúde dos nós pela API REST do servidor (Seção~\ref{sec-controlador}).
```

## FUN-15 (FundamentacaoTeorica.tex)

**Destino sugerido:** Metodologia (pré-registro já descrito nos blocos de protocolo)

```latex
Este trabalho adota o pré-registro para a nova medição de referência e para a avaliação do controlador (Seções~\ref{sec-protocolo-rebaseline} e \ref{sec-avaliacao-controlador}).
```

## MET-01 (Metodologia.tex)

**Destino sugerido:** Cap. 5 (reflexões / próximas etapas, mudanças em relação ao plano)

```latex
O presente texto é uma versão parcial da segunda etapa e reporta o protocolo e a execução inicial do mapeamento, o modelo formal com falhas e seus resultados, o ambiente experimental, o diagnóstico da primeira bateria de medição, o projeto e a implementação parcial do controlador e os protocolos de medição e de avaliação ainda não executados. A Seção~\ref{sec-revisoes} registra o que mudou em relação ao plano original e por quê.
```

## MET-03 (Metodologia.tex)

**Destino sugerido:** Cap. 5 (reflexões / próximas etapas, mudanças em relação ao plano) (atividade restante do mapeamento)

```latex
A complementação por execução manual via Portal de Periódicos CAPES nas bases originais (ACM Digital Library, IEEE Xplore, Scopus e SpringerLink) está entre as atividades restantes do Trabalho de Conclusão de Curso~II.
```

## MET-05 (Metodologia.tex)

**Destino sugerido:** Cap. 5 (reflexões / próximas etapas, mudanças em relação ao plano) (mapeamento não concluído)

```latex
O plano previa avaliar cada estudo integralmente nas semanas 5 e 6 do Trabalho de Conclusão de Curso~I, classificá-lo nas oito dimensões definidas e integrá-lo à síntese descritiva. Essa etapa não foi concluída: a leitura integral concentrou-se nas referências que sustentam a fundamentação e o desenho experimental, e a classificação dos 165 estudos, o fluxograma definitivo de seleção e a complementação da busca nas bases originais permanecem entre as atividades restantes (Apêndice~\ref{ap-plano-tcc2}). O mapeamento sistemático se fecha com esses três produtos.
```

## MET-06 (Metodologia.tex)

**Destino sugerido:** 4.5 Resultados parciais e limitações

```latex
Na etapa atual, o modelo formal passou a incluir quedas, recuperações e partições, os injetores das quatro classes de falha foram implementados, a carga foi corrigida depois do diagnóstico da primeira bateria, e o controlador heurístico foi projetado e implementado em parte.
```

## MET-07 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo)

```latex
Na versão atual do modelo, o canal é uma fila de mensagens pendentes por réplica, da qual qualquer mensagem pode ser entregue a qualquer momento (Seção~\ref{sec-revisoes}).
```

## MET-08 (Metodologia.tex)

**Destino sugerido:** 4.3 Execução do model checking e estado do baseline (bateria de junho, diagnóstico)

```latex
Essa tratabilidade vale nas menores configurações: com a inclusão das falhas no modelo, as configurações maiores deixaram de terminar dentro do tempo-limite das execuções (Seção~\ref{sec-mc-resultados}).
```

## MET-09 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo)

```latex
Com as falhas já incluídas no modelo, as duas ferramentas continuam como alternativas para ampliar a cobertura, sem uso até aqui.
```

## MET-10 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (função dono)

```latex
Completa o estado a função auxiliar $\textsf{dono} : \mathcal{S} \to \mathcal{U}$, que associa a cada sessão $s$ a identidade $u$ à qual a sessão pertence. A relação $\textsf{sessions}(u)$, empregada na Seção~\ref{sec-invariantes}, é a inversa de $\textsf{dono}$: $s \in \textsf{sessions}(u)$ se e somente se $\textsf{dono}(s) = u$.
```

## MET-11 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (O1)

```latex
Na versão atual do modelo, o efeito inclui $\textsf{dono}'(s) = u$, e o contador não é alterado (a reposição é a operação O5).
```

## MET-12 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (O4)

```latex
Na versão atual do modelo, por decisão de domínio, o bloqueio por limite não invalida as sessões existentes; só o bloqueio administrativo (O6) tem cascata de invalidação.
```

## MET-14 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (injetores)

```latex
As quatro têm injetor próprio no repositório experimental, e as três primeiras também aparecem como ações do modelo formal (F1 e F2) ou como atraso no canal (F3).
```

## MET-16 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (injetor de partição)

```latex
O injetor foi implementado e verificado na máquina virtual sem carga de aplicação (Seção~\ref{sec-verificacao-injetores}), e ainda não foi exercido sob carga.
```

## MET-17 (Metodologia.tex)

**Destino sugerido:** Revisões do planejamento (reunir no Cap. 4 ou 5) (F3 normal em vez de lognormal)

```latex
O \texttt{tc-netem} traz tabelas prontas apenas para distribuições como a normal e a de Pareto, e uma tabela lognormal exigiria gerar e instalar um arquivo próprio no ambiente. Por isso, as execuções usam a distribuição normal, com média de 20\,ms e desvio de 13\,ms, de modo que o p99 do atraso acrescentado a cada pacote fique perto dos 50\,ms de T18, e a lognormal fica como análise de sensibilidade. Essa calibração é do atraso por pacote, e não da latência das operações, que ainda não foi medida sob F3 com carga válida.
```

## MET-18 (Metodologia.tex)

**Destino sugerido:** Revisões do planejamento (reunir no Cap. 4 ou 5) (F4 redefinido)

```latex
Em outubro de 2026, o rótulo F4 passou a designar a degradação parcial da rede, porque o autor decidiu testar, além da partição total, perda de pacotes e degradação parcial: falhas de rede reais raramente cortam toda a comunicação \citep{bailis_kingsbury_network_2014}. Nessa definição, a comunicação entre o nó alvo e seus pares sofre perda, atraso, duplicação ou reordenação de pacotes, de forma simétrica ou assimétrica, sem corte completo. O tráfego do cliente não é afetado, porque o filtro casa apenas os endereços dos pares do \emph{cluster}. As severidades de perda vão de N1 a N5 (1\,\%, 5\,\%, 20\,\%, 50\,\% e 99,9\,\%), e a mais alta deve aproximar uma partição, hipótese a confirmar experimentalmente. O \emph{state transfer} continua presente como consequência da reentrada do nó em F1 e da fusão em F2, mas não tem injetor próprio.
```

## MET-19 (Metodologia.tex)

**Destino sugerido:** Descartado (pedido do autor ou sem uso) (mesmo teor do INT-04, pedido do autor)

```latex
Na etapa atual, a replicação entre localidades ficou fora do recorte.
```

## MET-20 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo)

```latex
Os invariantes também se declaram sobre a função $\textsf{dono}$ definida na mesma seção. No modelo formal, quiescência é o estado com todas as filas de mensagens vazias.
```

## MET-21 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (I2 só para O6)

```latex
Na análise, I2 é avaliado para o bloqueio administrativo (O6). O bloqueio por limite não invalida sessões por decisão de domínio, e a violação correspondente é esperada por desenho.
```

## MET-22 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (I5)

```latex
Na versão atual, I5 abrange também a identidade associada a cada sessão, ou seja, a convergência do estado lógico e do dono da sessão:
\[
\textsf{quiescent}(Q) \Rightarrow \forall s, \forall r_1, r_2 \in \mathcal{R}: \, \sigma_{r_1}(s) = \sigma_{r_2}(s) \, \land \, \textsf{dono}_{r_1}(s) = \textsf{dono}_{r_2}(s).
\]
```

## MET-23 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (I6)

```latex
Na versão atual, I6 abrange também a marca de bloqueio, ou seja, a convergência do contador e da marca de bloqueio:
\[
\textsf{quiescent}(Q) \Rightarrow \forall u, \forall r_1, r_2 \in \mathcal{R}: \, \beta_{r_1}(u).\textsf{counter} = \beta_{r_2}(u).\textsf{counter} \, \land \, \beta_{r_1}(u).\textsf{marca} = \beta_{r_2}(u).\textsf{marca}.
\]
```

## MET-24 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (I1-I4 no modelo atual)

```latex
Na versão atual, o registrador também modela $\textsf{dono}$ e $\beta.\textsf{marca}$. As fórmulas de I1 e I2 acima tratam do estado lógico da sessão. No modelo com várias réplicas, a verificação as complementa com versões avaliadas réplica a réplica, descritas na Seção~\ref{sec-mc-resultados}. No modelo TLA+, I3 e I4 estão declarados em formas de domínio mais fracas, contador dentro da faixa de 0 ao limite e motivo dentro do conjunto declarado, que são consequência da tipagem; a forma temporal de I4 é verificada réplica a réplica, e a de I3 não foi verificada.
```

## MET-25 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (laço de convergência)

```latex
Na implementação, as violações de convergência (I5 e I6) exigem quiescência e por isso são verificadas por um laço de convergência executado depois que a carga cessa, com rodadas a cada 5\,s que comparam o estado das réplicas até a convergência ou até um tempo-limite. As regras de classificação e de contagem adotadas na nova medição de referência estão na Seção~\ref{sec-carga}.
```

## MET-26 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (medição do tempo de convergência)

```latex
Na implementação, o intervalo é medido entre o fim da carga e a primeira de três rodadas consecutivas do laço de convergência, a cada 5\,s, sem divergência entre réplicas, com teto de 600\,s, e a comparação inclui também $\textsf{dono}_{r_1} = \textsf{dono}_{r_2}$.
```

## MET-27 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (coletor de métricas)

```latex
O coletor dessas métricas foi escrito em junho, em uma versão do código ainda não integrada à atual, e não foi usado nas baterias. O uso de CPU é medido por \emph{container}, fora do Infinispan, para a estimativa de energia da Seção~\ref{sec-energia}.
```

## MET-29 (Metodologia.tex)

**Destino sugerido:** Revisões do planejamento (reunir no Cap. 4 ou 5) (critérios de avaliação do controlador)

```latex
O desenho aprovado em outubro de 2026 mantém a margem de 10\,\% e a rastreabilidade, mas substitui o teste e a unidade de análise, porque o teste aplicado a eventos trata como independentes observações de uma mesma execução e é sensível a diferenças no corpo da distribuição, não só na cauda. A Seção~\ref{sec-avaliacao-controlador} descreve o desenho vigente.
```

## MET-32 (Metodologia.tex)

**Destino sugerido:** Revisões do planejamento (reunir no Cap. 4 ou 5) (T18: normal na execução)

```latex
(lognormal no plano; normal na execução)
```

## MET-38 (Metodologia.tex)

**Destino sugerido:** 4.3 Execução do model checking e estado do baseline (bateria de junho, diagnóstico) / Revisões do planejamento (reunir no Cap. 4 ou 5) (DIST_SYNC até out/2026)

```latex
A justificativa de $10^6$ operações por execução e de 30 repetições vale para o desenho original da tabela; a nova medição de referência altera esses valores, com os desvios declarados na Seção~\ref{sec-protocolo-rebaseline}. A linha T1 fixa \texttt{DIST\_ASYNC} como alvo, mas até outubro de 2026 todas as execuções na máquina virtual usaram \texttt{DIST\_SYNC}, porque a configuração do \emph{cluster} continha apenas \emph{caches} síncronos. Os \emph{caches} assíncronos foram acrescentados depois desse achado, e o modo assíncrono é objeto da nova medição de referência (Seção~\ref{sec-revisoes}).
```

## MET-39 (Metodologia.tex)

**Destino sugerido:** 4.3 Execução do model checking e estado do baseline (bateria de junho, diagnóstico) (diagnóstico da bateria de junho)

```latex
A primeira bateria executada com essa carga (Seção~\ref{sec-baseline-estado}) expôs dois defeitos de desenho da mistura e um de higiene de estado. Cada \emph{thread} usava uma única identidade para o \emph{login} e para as operações O4 e O5. Com limiar de bloqueio igual a cinco, com O5 preservando a marca de bloqueio e sem O7 na mistura, essa identidade ficava bloqueada de forma permanente depois de, em média, cerca de 130 operações da \emph{thread} em S1 e 500 em S2, frações de segundo no ritmo medido, segundo o tempo esperado de absorção da cadeia do contador (incremento com probabilidade 0,10 e reposição com 0,05 em S1; incremento com 0,01, sem reposição, em S2).
% Fonte: wt-plano-ctrl/docs/diagnostico-login-blocked-2026-10-22.md, secao 2.3 (simulacao: S1 p_inc = 0,10 e p_reset = 0,05, aprox. 131 operacoes; S2 p_inc = 0,01 sem reset, aprox. 500).
As validações e os \emph{logouts} isolados liam chaves sintéticas que nenhuma operação gravava. O \emph{cluster}, por fim, conservava o estado de execuções anteriores. A carga foi corrigida nos três pontos antes da nova medição.
```

## MET-39 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (carga)

```latex
Cada \emph{thread} $t$ passa a usar
```

## MET-39 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (carga)

```latex
As chaves lidas pelas operações isoladas passam a existir no \emph{cache}
```

## MET-39 (Metodologia.tex)

**Destino sugerido:** 4.3 Execução do model checking e estado do baseline (bateria de junho, diagnóstico) (limiares definidos no diagnóstico)

```latex
Esses limiares, definidos no diagnóstico da bateria de junho, foram adotados para a nova medição e fixados no programa de carga congelado antes do piloto, de modo que não foram ajustados depois de observados os dados.
```

## MET-40 (Metodologia.tex)

**Destino sugerido:** Revisões do planejamento (reunir no Cap. 4 ou 5) (S3 acrescentado em out/2026)

```latex
, acrescentado em outubro de 2026,
```

## MET-40 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo) (bloqueio por limite não invalida sessões)

```latex
, porque o bloqueio por limite, por decisão de domínio, não invalida as sessões existentes
```

## MET-41 (Metodologia.tex)

**Destino sugerido:** 4.3 Execução do model checking e estado do baseline (bateria de junho, diagnóstico) (nova medição substitui a de junho)

```latex
substitui a bateria de junho, cujo diagnóstico está na Seção~\ref{sec-baseline-estado}, e mede
```

## MET-41 (Metodologia.tex)

**Destino sugerido:** 4.5 Resultados parciais e limitações (estado do pré-registro)

```latex
O protocolo foi pré-registrado \citep{nosek_preregistration_2018} em outubro de 2026, com dois complementos, antes de qualquer execução, e não admite ajuste depois de observados os dados.
```

## MET-41 (Metodologia.tex)

**Destino sugerido:** 4.5 Resultados parciais e limitações (piloto e campanha não executados)

```latex
Até a data desta versão, nem o piloto nem a campanha foram executados, e o texto a seguir descreve o que está previsto.
```

## MET-41 (Metodologia.tex)

**Destino sugerido:** 4.3 Execução do model checking e estado do baseline (bateria de junho, diagnóstico) / Revisões do planejamento (reunir no Cap. 4 ou 5) (F1: podman stop em junho, podman kill na nova medição)

```latex
Esse modo de F1 substituiu, em outubro de 2026 e antes de qualquer execução da campanha, o \texttt{podman stop} usado na bateria de junho e nas verificações intermediárias, que envia \texttt{SIGTERM} e provoca a saída ordenada do nó, com aviso aos demais membros. Essas execuções anteriores são descritas como parada ordenada, e não como \emph{crash} silencioso. O novo modo foi ensaiado apenas no \emph{cluster} local, e seu ensaio na máquina virtual é pré-requisito do piloto.
```

## MET-41 (Metodologia.tex)

**Destino sugerido:** Revisões do planejamento (reunir no Cap. 4 ou 5) (desvios do protocolo em relação à Tabela de parâmetros)

```latex
O protocolo se afasta da Tabela~\ref{tab-parametros-baseline} em pontos declarados. A bateria de junho e as verificações intermediárias usaram \texttt{numSegments} igual a 64, e não 256 como em T4, e a nova medição mantém esse valor. Ele veio da configuração inicial do \emph{cluster}, sem fonte que o justifique, e foi mantido para preservar a comparabilidade entre as baterias. As dez repetições por célula substituem as 30 de T10, por limite de tempo de máquina, com a execução como unidade de análise. A redução de 30 para 10 repetições diminui o poder estatístico dos contrastes entre células, de modo que diferenças pequenas entre condições podem não ser detectadas. A duração fixa de 300\,s substitui o volume de $10^6$ operações de T9 e as durações de T14. O aquecimento mínimo passa a 30\,s (T11). A carga não segue o alvo de 75\,\% do pico de T12: o programa de carga opera em laço fechado, com quatro \emph{threads} que emitem a operação seguinte assim que a anterior termina, sem limitação de vazão. A vazão de cada execução é medida e reportada, e não imposta. Os afastamentos de T4 e de T12 foram declarados em complemento ao registro do protocolo, antes da coleta. F3 usa distribuição normal, e não a lognormal indicada em T18 e na Seção~\ref{sec-falhas}. A análise de T22 passa a reamostrar execuções no \emph{bootstrap} e a aplicar o teste de Mann-Whitney sobre os p99 por execução, e não sobre eventos agrupados de várias execuções.
```

## MET-42 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (coletor de energia)

```latex
O coletor e o \emph{script} de análise estão implementados e testados com dados simulados, mas nenhum consumo foi medido em campanha, e a nova medição de referência não coleta uso de CPU.
```

## MET-43 (Metodologia.tex)

**Destino sugerido:** 4.5 Resultados parciais e limitações (avaliação não executada)

```latex
A avaliação do controlador heurístico (Seção~\ref{sec-controlador}) está desenhada, mas não foi executada nem pré-registrada; o pré-registro, com os valores finais, precede qualquer execução.
```

## MET-44 (Metodologia.tex)

**Destino sugerido:** Revisões do planejamento (reunir no Cap. 4 ou 5) (bloco inteiro)

```latex
O plano da Proposta e do Trabalho de Conclusão de Curso~I sofreu revisões durante a execução. Esta seção as reúne, com o que foi planejado, o que mudou e o motivo, e remete às seções com os detalhes técnicos. A renovação de sessão não constitui revisão, porque constava do objetivo da Proposta, mas não entrou nas operações O1 a O7 do recorte do Trabalho de Conclusão de Curso~I, de modo que está fora do escopo desde o início.

Na verificação formal, o plano previa um modelo sem falhas no Trabalho de Conclusão de Curso~I e a inclusão de falhas no Trabalho de Conclusão de Curso~II. As duas etapas foram cumpridas, mas o modelo passou por revisões de estrutura no caminho. Em setembro de 2026, a entrega das mensagens foi corrigida para difusão a todas as réplicas, com aplicação condicionada a uma marca de tempo, e a cascata de invalidação do bloqueio foi refeita. Em outubro, uma revisão do mecanismo de conflito, apoiada no código-fonte do Infinispan e do programa de carga, mostrou que cada sessão e cada identidade são gravadas como um registro único, com um único número de versão. O modelo, que decompunha esse registro em variáveis independentes, passou a usar um registro por chave com uma única marca de tempo, por fidelidade ao sistema medido. A retirada do desempate entre réplicas que acompanhou essa mudança tornou possível o empate de marcas de tempo, causa de parte das violações de I5 e I6 descritas na Seção~\ref{sec-mc-resultados}, e as demais vêm da fusão de partições. Por fim, as fórmulas globais de I1 e I2 se mostraram violadas por construção em um modelo com várias réplicas, o que motivou propriedades equivalentes avaliadas réplica a réplica. A conferência do modelo também mostrou que I3 e I4 estão declarados nele em formas de faixa e de tipo, mais fracas que as fórmulas da Seção~\ref{sec-invariantes}, o que restringe o alcance dos resultados sobre esses dois invariantes.

Na vertente experimental, quatro revisões alteram a leitura dos dados. A primeira é o modo de replicação: o alvo declarado em T1 era \texttt{DIST\_ASYNC}, mas a configuração do \emph{cluster} só tinha \emph{caches} síncronos, e todas as execuções na máquina virtual até outubro de 2026 ocorreram em \texttt{DIST\_SYNC}. O achado levou à inclusão de \emph{caches} assíncronos e à medição dos dois modos na mesma campanha. A segunda é a carga: o diagnóstico da bateria de junho mostrou que ela não criou sessões (Seção~\ref{sec-baseline-estado}), e a carga foi corrigida (Seção~\ref{sec-carga}) antes de uma nova medição de referência pré-registrada. A terceira é a falha F1: a bateria de junho e as verificações intermediárias usaram parada ordenada, e o protocolo novo adota a queda silenciosa, por causa do detector FD\_SOCK2. A quarta é o conjunto de falhas: F2 deixou de ser reservada e ganhou injetor, F3 passou a usar a distribuição normal, e F4 foi redefinida como degradação parcial da rede (Seção~\ref{sec-falhas}). O volume previsto em T9 e T10 deu lugar a dez repetições de 300\,s por célula, e o cenário S3 foi acrescentado para exercitar o bloqueio administrativo e a escrita em nó isolado.

No controlador, o plano previa um processo externo que lesse observáveis em arquivo e passasse ao perfil conservador quando a taxa de violação superasse um limiar. O projeto aprovado em outubro de 2026 o executa como \emph{thread} dentro do processo do programa de carga, porque o observável primário é por evento e a serialização para outro processo custaria CPU, que é métrica avaliada. O gatilho primário passou a combinar latência de cauda relativa, taxa de erro e topologia, e as violações ficaram como gatilho secundário, por três motivos. I5 e I6 só são verificáveis sob quiescência, o bloqueio por limite produziria registros de I2 esperados por desenho e, nas quedas já executadas, nenhum erro foi registrado, de modo que a detecção depende de latência e topologia. A Proposta e o objetivo específico (4) preveem que o controlador ajuste também a configuração do \emph{cluster}. Essa atuação foi dividida em duas etapas. A primeira, implementada em parte, fica restrita ao cliente: suas ações apenas escolhem quando emitir operações que o cliente já emitiria, de modo que os comportamentos produzidos continuam entre os admitidos pelo modelo formal. A atuação sobre a configuração do \emph{cluster} é uma segunda etapa planejada (Seção~\ref{sec-controlador-cluster}), posterior à ligação do controlador ao laço de carga e à avaliação da primeira. A análise estatística também mudou: a unidade passou a ser a execução, e não o evento, e o teste de Mann-Whitney deu lugar, na avaliação do controlador, ao desenho da Seção~\ref{sec-avaliacao-controlador}. A estimativa de energia, ausente do plano original, entrou como método na Seção~\ref{sec-energia}.
```

## MET-45 (Metodologia.tex)

**Destino sugerido:** 4.2 Especificação formal inicial (o que mudou no modelo)

```latex
Com as falhas já incluídas no modelo, Must continua como alternativa para ampliar a cobertura, sem uso até aqui.
```

## MET-47 (Metodologia.tex)

**Destino sugerido:** 4.4 Estado dos artefatos (injetores, carga, coletores, controlador) (Nguyen orienta carga e controlador)

```latex
, que orientam tanto a escrita do programa de carga quanto o desenho do controlador
```
