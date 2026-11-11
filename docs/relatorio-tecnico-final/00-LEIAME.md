# Relatório técnico final da parte prática (2026-11-11)

**Para quem:** o Escritor, que vai redigir a monografia em LaTeX/ABNT a partir daqui, e o autor, que revisa.
O relatório reúne tudo o que foi feito na parte prática, de maio a novembro de 2026. Assim, a escrita não
precisa garimpar os planos, decisões, relatórios e logs espalhados pelo repositório.

**Situação.** A coleta terminou em 07/11. A análise oficial rodou em 08/11. Em 11/11 ela foi validada por
recálculo independente a partir dos brutos (`docs/validacao-analise-oficial-2026-11-11.md`) e o relatório foi
escrito. Os números de resultado do capítulo 5 são os oficiais. Os demais capítulos foram conferidos nos
arquivos citados em cada linha. Quando algo não pôde ser conferido, o capítulo diz.

**Autoria.**
- Capítulos 1, 2, 3 e 10: Senior da parte A.
- Capítulos 4, 6, 7, 8 e 9: Senior da parte B.
- Capítulo 5 e este índice: coordenador.

Commits locais na branch `docs/plano-implementacao-controlador`: `fac6b59`, `585b615`, `c1d925f`, `2c247d8`,
`93e01c8` e o deste índice.

## 1. Mapa dos capítulos

| Capítulo | Conteúdo | Use para |
|---|---|---|
| [01 Objetivos e perguntas](01-objetivos-e-perguntas.md) | objetivo, perguntas, hipóteses como foram executadas, regra de decisão, mudanças desde o TCC I com data e motivo | Introdução, Metodologia (desenho), "Revisões em relação ao planejamento" |
| [02 Arquitetura e ambiente](02-arquitetura-e-ambiente.md) | Infinispan 15.0.21 com cliente Hot Rod 15.0.0, *caches* criados pela REST, carga S3, extensão A1, coletor, injetores, *host* e VM, diagramas | Desenvolvimento (arquitetura), tabela de parâmetros |
| [03 Verificação formal](03-verificacao-formal.md) | especificações TLA+, I1 a I6, resultados reais do TLC (traços mínimos, desempate, fusão), modelo × experimento | Desenvolvimento (especificação e *model checking*), discussão |
| [04 Controlador](04-controlador.md) | sinais, regras, perfis, P0/P1/P2, AC-7, parâmetros finais, implementação e testes, comportamento observado nas 90 trilhas | Metodologia (políticas), Desenvolvimento (controlador) |
| [05 Resultados](05-resultados.md) | números oficiais: regra de sucesso, braços, secundários, mecanismo exposição × perda, custo, energia, células descritivas, sensibilidades, casos notáveis | capítulo ou seção nova de resultados |
| [06 Protocolo e instrumentação](06-protocolo-e-instrumentacao.md) | pré-registros, fatorial 2×2, células, sementes, definição de V e dos desfechos, energia, h4, parada e reposição, convergência | Metodologia (desenho da avaliação, métricas) |
| [07 Histórico e decisões](07-historico-e-decisoes.md) | linha do tempo de maio a novembro, campanhas, problemas do ambiente e soluções, decisões do autor com data | Conclusões, cronograma executado |
| [08 Desvios, ameaças e limitações](08-desvios-ameacas-limitacoes.md) | desvios D1 a D13, ameaças à validade, limitações, trabalho futuro | Limitações e ameaças, Conclusões |
| [09 Rastreabilidade](09-rastreabilidade.md) | commits, tags e branches por artefato, caminhos dos dados, SHA-256, como reproduzir | Apêndice |
| [10 Glossário](10-glossario.md) | todas as siglas e códigos internos em linguagem simples, códigos reutilizados com sentidos diferentes | consulta permanente; lista de abreviaturas |

Apoio:
- `scripts/trilhas_controlador.py`: recontagem das trilhas do controlador;
- `../validacao-analise-oficial-2026-11-11/`: recálculo, complementos e sensibilidades.

## 2. Regras para a escrita

1. **Fonte em cada número.** Todo número que entrar no texto deve estar num capítulo deste relatório com a fonte.
   Número que não estiver aqui não entra sem conferência nos brutos (regra do autor).
2. **Nada de código interno sem explicação.** PO/PL/AO/AL, AC-7, R1 a R8, h4, B-xx, DT-0xx, P-xx, W-x e
   semelhantes não vão para o texto como código solto. O capítulo 10 diz o que escrever no lugar, e a tabela
   `qd-notacao-avaliacao` fixa a notação que pode aparecer.
3. **Metodologia descreve o executado.** A Metodologia descreve só o desenho executado, sem narrativa de
   mudança (regra de 03/11). As mudanças vão para "Revisões em relação ao planejamento", com data e motivo
   (capítulo 1, seção 1.5, e `qd-revisoes-desenho`).
4. **Resultados com as ressalvas.** O controlador tem melhoria, e a A1 não tem na regra principal mas zera a
   perda permanente. Isso sempre vem junto com:
   - o mecanismo (a taxa de violações mede exposição);
   - a dependência do instrumento (sem a sonda de *replay*, as conclusões se invertem);
   - o custo em erro visível.

   Proibido redefinir a taxa ou trocar o desfecho principal (decisão do autor de 07/11).
5. **Fontes.** Só citar o que estiver em `monografia-atual/bibliografia.bib`. Os pedidos ao Bibliotecário estão
   na seção 6.
6. **Estilo.** Sem travessão no texto em português, regra do Revisor.

## 3. Resultado em cinco linhas (detalhes no capítulo 5)

- **Controlador na partição:** a taxa de violações caiu à metade (−0,189 por mil ações, p = 0,0005). A regra de
  sucesso foi atendida (interseção-união com Holm entre os dois efeitos, p = 0,0025). O p99 e a vazão útil
  ficaram não inferiores.
- **Custo do controlador:** +12 % de falhas percebidas (Holm 0,055 com a família pré-registrada de 9; p = 0,001
  sem o bloco 13). CPU e energia sem diferença detectável, com custo energético de no máximo cerca de +5 %.
- **Regra de fusão A1:** sem efeito na taxa de violações. Zera a perda permanente (20 sessões → 0; Holm 0,035).
  Não interage com o controlador.
- **Demais células:** queda de nó, *jitter*, 20 % de perda e sem falha, com zero violações e nenhuma ação do
  controlador.
- **Formal:** com o desempate pelo conteúdo, todo contraexemplo de I5 e I6 passa por partição e fusão. O
  traço central do modelo (*logout* no lado isolado desfeito na fusão) foi visto no sistema real, e a A1 usa a
  mesma ordem da variante do modelo que o elimina (capítulo 3).

## 4. O que muda na monografia (versão de 05/11)

A tabela consolida as correções obrigatórias dos capítulos. A coluna "Rel." diz onde estão a evidência e o
texto sugerido.

### 4.1 Metodologia.tex

| Local | O texto diz | O correto | Rel. |
|---|---|---|---|
| :219, :349 | taxa = soma das violações de I1, I4 e I2 reais | pares distintos (sessão ou identidade, invariante) com ao menos uma detecção nas fases durante e recuperação, somando auditor online, sonda de *replay* e varredura final, por mil ações S3 | 05, 06 |
| :227 | métricas expostas em OpenMetrics | o OpenMetrics não foi usado | 02 |
| :236-285 (`tab-parametros-baseline`) | T4 = 256 segmentos; T5 = *payload* de 1 KB; T1, T16, T19, T20, T21 | 64 segmentos; sem enchimento (o valor é o `SessionState` serializado); demais linhas na T2-01 | 02 |
| :318 | nova medição de referência com 120 execuções | só o piloto e a noite 1 rodaram (60 execuções, 56 aceitas); a noite 2 não rodou (**decisão pendente**, seção 5) | 02, 07, 08 |
| :335 | exclusão de CPU só com *steal* acima de 5 % | também quando falta a medida de *steal*: na partição sobraram 4 de 12 blocos (sensibilidade com 12) | 05, 06 |
| :340, :342 | P1 para o nó (`server?action=stop`); P2 suspende o rebalanceamento | P1 = AC-7: o nó suspeito passa a ignorar o *cache* de sessões, de forma reversível; P2 desabilitada na avaliação | 04 |
| :347 | dois braços em quatro células, oito blocos, células `DIST_SYNC` de referência, cerca de 80 execuções | fatorial 2×2 na partição (regra de fusão × controlador) com 12 blocos, mais quatro células descritivas de 4 blocos (F1, F3, F4, sem falha); sem `DIST_SYNC`; 90 execuções | 05, 06 |
| :349 | 2⁸ arranjos, k ≥ 6 | 12 blocos, 2¹² arranjos, menor p possível de 0,0005 | 06 |
| :351 | Holm entre três células; p99 por operação e cada invariante como secundários | Holm m = 2 entre os dois efeitos na única célula de teste; secundários em família de 9 por efeito; p99 por operação e cada invariante descritivos; a ferramenta usou 11 (desvio D13) | 05, 06, 08 |
| :353 (MET-73) | P1 reduz a vazão "até o fim da execução", troca declarada "como expectativa" | as duas afirmações estão erradas para o AC-7 (vazão não inferior, 0,982) | 04, 05 |

### 4.2 Desenvolvimento.tex

| Local | O texto diz | O correto | Rel. |
|---|---|---|---|
| :21 | o canal aplica a mensagem se `ts` for maior | na revisão final vence o registro maior na ordem da tupla (desempate); a tag da avaliação contém a especificação sem o desempate | 03 |
| :50, :138 | configuração `tiny` de setembro | rodada de 09/07 (versionada em 16/09) | 03 |
| :54 | tamanhos de traço | mínimos com 1 *worker*: I1 com 4 estados e I2 com 3, nas quatro configurações | 03 |
| :60, :64 | verificação com o desempate não terminou | terminou em 06/11 (resultado no capítulo 3) | 03 |
| :79, :83, :219 | nova medição não executada | parcial (ver Metodologia :318) | 02 |
| :99-101 | *caches* no XML | os seis *caches* são criados pela REST a cada recriação, incluindo os canários e a variante A1 | 02 |
| :136 | nenhum consumo medido em campanha | o coletor rodou na calibração, na recalibração e na avaliação | 02, 04 |
| :145, :173, :217 | controlador em implementação; avaliação não pré-registrada nem executada | implementado, pré-registrado, executado em 06 e 07/11 e analisado | 04, 05 |
| :147, :169 | janela de 5 s, seis janelas calmas, valores provisórios | valores finais da DT-028: W 4 s, m 35, intervalo mínimo de 140 s, limiares de latência e defasagem | 04 |
| :162 (quadro de sinais) | mínimo de 50 eventos | 100, e qualquer erro já conta como alto | 04 |
| :171, :210 | P1 para o nó | AC-7 | 04 |
| :180-204 (`qd-reconfig-infinispan`) | parada do servidor como P1; JGroups em verificação; sem a política de fusão própria | atualizar pela T2-05 (capítulo 2) e pela coluna "Uso" do capítulo 4 | 02, 04 |
| :208 | extensão do servidor como trabalho futuro | a A1 é uma extensão instalada nos três nós e é fator da avaliação | 02, 04 |
| :212 | exclusão pela vista do JGroups em avaliação | verificada e não adotada | 02, 04 |
| (ausente) | cliente e servidor na mesma versão | cliente Hot Rod 15.0.0.Final, servidor 15.0.21.Final | 02 |
| :214-219 (resultados parciais) | sem resultados da avaliação | resultados no capítulo 5; seção ou capítulo novo | 05 |

### 4.3 ConclusoesParciais.tex e ApPlanoTCC2.tex

| Local | O texto diz | O correto | Rel. |
|---|---|---|---|
| ConclusoesParciais :30, :34 | sem dados utilizáveis; 17 quedas do *host* | dados da avaliação completos; o log do Windows tem 19 eventos de queda até 07/11 | 07 |
| ConclusoesParciais :107, :109 | cronograma por cumprir | cronograma executado (`tab-cronograma-executado`) | 07 |
| ApPlanoTCC2 :64 (APX-15) | desempate pendente | concluído em 06/11 | 03 |

## 5. Decisões pendentes do autor

1. **Especificação TLA+ final no `main`.** A versão com o desempate (commit `a2ce4c9`, branch
   `fix/b45-desempate-ts`) e a variante de fusão `MesclarJoin` (branch `feature/tla-mesclar-join`) estão fora da
   integração. A tag da avaliação contém a especificação sem o desempate. Levar as duas ao `main` no fechamento
   do GitHub? Recomendação: sim. São só especificação e documentação, sem efeito no código avaliado.
2. **Nova medição de referência pela metade** (60 de 120 execuções; a noite 2 foi suspensa em 06/11 e não
   rodou). Relatar como parcial, com o que ela mediu, ou retirar do texto? Recomendação: relatar como parcial
   nas revisões e limitações, sem usá-la como resultado.
3. **Perguntas operacionais** da seção 1.2 do capítulo 1. São formulação do relatório, não de documento
   aprovado. Podem ser usadas no texto?
4. **Cópia de segurança dos brutos.** `dados-vm/` (avaliação final: 252 MB de brutos mais infraestrutura) não
   está no git e fica no SSD que degradou em 07/11. Recomendação: cópia em outro meio (disco externo ou Drive).
   O arquivo passa do limite de 100 MB por arquivo do git; o anexo de uma versão (*release*) no GitHub
   comportaria, mas tornaria os dados públicos.

**Decisões técnicas já tomadas pelo coordenador** (com fonte; só para conhecimento):
- **Holm dos secundários:** usar a família pré-registrada de 9, e declarar que a ferramenta usou 11 (D13).
  Nenhuma conclusão muda.
- **Sensibilidade de CPU e energia:** a de 12 blocos entra como complemento não pré-registrado, e o oficial
  continua com 4 blocos.

## 6. Pedidos ao Bibliotecário (consolidados)

| Para | Fonte | Situação |
|---|---|---|
| ação AC-7 (`ignored-caches`) e REST de *logging* | `infinispan_rest_15` | no `.bib`; indicar as seções |
| política de fusão própria (A1) | `infinispan_configuring_15` | no `.bib`; indicar a seção e conferir a §7.6 (sem cópia local) |
| MERGE3 e VERIFY_SUSPECT2 | `jgroups_manual_5` | no `.bib`; indicar as seções |
| simetria e traço mais curto no TLC | `lamport_specifying_systems_2002`; conferir se `lee_tla_tlc_2021` serve | no `.bib`; indicar as seções |
| desenho fatorial | `fisher_design_experiments_1935` (capítulo do fatorial); ou Montgomery, ou Box, Hunter e Hunter | buscar ou indicar o capítulo |
| classificação das ameaças à validade | Wohlin et al. | buscar |
| cérebro dividido (*split brain*) | a definir | buscar |
| reuso de sessão depois do *logout* | `owasp_session_mgmt_cheatsheet`, `nist_sp_800_63b4_2025` | no `.bib`; indicar as seções |

## 7. Catálogo das tabelas propostas (35)

Todas têm versão em Markdown e LaTeX no capítulo indicado (abntex2, `\caption`, `\label`, `\fonte`). As 18 dos
capítulos 4 e 6 a 9 foram compiladas no MiKTeX pelo Senior da parte B. As do capítulo 5 seguem o mesmo molde.

| Rótulo | Cap. | Tipo | Onde entra |
|---|---|---|---|
| `qd-hipoteses-avaliacao` | 01 | nova | Metodologia, desenho da avaliação (no lugar da prosa de MET-72) |
| `qd-revisoes-desenho` | 01 | nova | Desenvolvimento, "Revisões em relação ao planejamento" |
| `qd-objetivos-execucao` | 01 | nova | considerações finais |
| T2-01 (`tab-parametros-baseline`) | 02 | atualizar | linhas T1, T4, T5, T16, T19, T20, T21 |
| `qd-ambiente-execucao` | 02 | nova | Desenvolvimento, arquitetura |
| `qd-caches` | 02 | nova | Desenvolvimento, estado dos artefatos |
| `qd-injetores` | 02 | nova | Metodologia, modelo de falhas |
| `qd-reconfig-infinispan` | 02 (com a coluna "Uso" do 04) | atualizar | Desenvolvimento :180 |
| `tab-tlc-invariantes`, `qd-tlc-por-replica`, `qd-tlc-fusao-maximo` | 03 | novas | Desenvolvimento, resultados do *model checking* |
| `qd-modelo-experimento` | 03 | nova | discussão (modelo × experimento) |
| `qd-sinais-controlador` | 04 | atualizar | Desenvolvimento :151 |
| `qd-regras-controlador`, `qd-acoes-controlador`, `tab-parametros-controlador`, `qd-cuidados-p1` | 04 | novas | Desenvolvimento, controlador |
| `tab-comportamento-controlador` | 04 | nova | resultados |
| `tab-aval-execucoes`, `tab-aval-regra-sucesso`, `tab-aval-bracos`, `tab-aval-descritivas` | 05 | novas, essenciais | resultados |
| `tab-aval-secundarios`, `tab-aval-sensibilidade` | 05 | novas, complemento | resultados ou apêndice |
| `qd-desenho-avaliacao`, `qd-desfechos-avaliacao`, `tab-parametros-avaliacao`, `qd-criterios-campanha` | 06 | novas | Metodologia, desenho e métricas |
| `tab-aval-fases` | 06 | nova | resultados |
| `qd-prerregistros` | 06 | nova | apêndice |
| `tab-cronograma-executado` | 07 | nova (ao lado de `tab-cronograma-tcc2`) | Conclusões |
| `tab-campanhas` | 07 | nova | resultados ou apêndice |
| `qd-problemas-ambiente` | 07 | nova | limitações |
| `qd-desvios-avaliacao`, `qd-ameacas-validade` | 08 | novas | limitações e ameaças |
| `qd-artefatos-avaliacao` | 09 | nova | apêndice |
| `qd-notacao-avaliacao` e lista de siglas (T10-01) | 10 | nova e atualizar | início do desenho da avaliação; `listofabbrv` em `Main-Monografia.tex:118-144` |

Não é preciso usar as 35. O mínimo para a monografia registrar a avaliação são estas sete:
- `qd-desenho-avaliacao`;
- `qd-desfechos-avaliacao`;
- `qd-acoes-controlador`;
- `tab-aval-execucoes`;
- `tab-aval-regra-sucesso`;
- `tab-aval-bracos`;
- `tab-aval-descritivas`.

Ao lado delas, a T2-01 atualizada e o `qd-reconfig-infinispan` atualizado. O restante é opção do Escritor e do
autor.

## 8. Limitações do próprio relatório

- **`exec-0037`:** a causa da anomalia não foi estabelecida. O ensaio foi descartado pelo autor em 11/11, então
  ela entra só como observação e risco (capítulos 4, 5 e 8).
- **Não conferido pelos Seniors** (cada capítulo lista o seu):
  - padrões do FD_ALL3, MTU e tamanho do VHDX;
  - mecanismo da divergência da `exec-0081`;
  - utilização da VM de cerca de 5,7 % e erro de 14 a 17 % da sonda imediata (vêm da pré-análise);
  - contagens do Surefire por classe (contadas no código, sem rodar o Maven).
- **Revisão da DT-026:** sem registro escrito de que o autor a revisou, como o documento previa.
