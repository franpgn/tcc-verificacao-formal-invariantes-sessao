# 8. Desvios, ameaças à validade, limitações e trabalho futuro

**Situação dos números.** Os desvios vêm do registro de entrega (`dados-vm/cs3-2026-11/fat-20261108T051402Z/ENTREGA-ANALISE.txt`,
itens 8 e 9, gravado antes da análise oficial), do registro de execução do roteiro
(`docs/roteiro-avaliacao-fatorial-2026-11-06.md` §12) e da validação de 11/11. Números conferidos por mim nos brutos
estão marcados com a fonte; os resultados da comparação ficam no capítulo 5.

## 8.1 Desvios declarados

Desvio é o que a execução fez diferente do roteiro ou do pré-registro. **Nenhum mudou regra, desfecho, matriz,
semente, *jar* ou parâmetro da análise** (ENTREGA itens 8 e 9: "sem mudança de protocolo").

| # | Desvio | O que aconteceu | Por que não invalida a comparação | Fonte |
|---|---|---|---|---|
| D1 | queda de energia entre as fases | o *host* caiu de ~07:54Z a 14:06Z de 07/11, 13 min depois do fim do núcleo (64 aceitas); a extra e as reposições rodaram em outro boot da VM (`0c1b7856…` contra `f9e98b74…`), com outro amostrador h4, outro IP e outra série do `typeperf`; pausa de 7 h 37 min; o *swap* no portão passou de 38 MB, constante no núcleo, a 0 | cada bloco fica inteiro numa fase e o pareamento é dentro do bloco; as células da extra (F1, F4) não têm execução no núcleo; cada execução recria o *cluster* | ENTREGA item 8 |
| D2 | SSD do *host* degradado depois da queda | escritas acima de 100 ms 8,5 por hora na tarde de 07/11 contra 0,7 por hora na madrugada do núcleo (acima de 50 ms, 20,4 contra 3,5; máximo 496 ms), em episódios de 14 s a ~3 min sem uso interativo | explica os 6 h4a; descartes h4 não consultam desfecho; blocos refeitos | ENTREGA item 9a |
| D3 | pausa manual | arquivo `PARAR` criado às 16:11:01Z para parar antes da i = 76; ReTrim do C: e ~88 GB liberados (49,9 → 138,5 GB livres); teste de disco fora de execução | parada entre execuções, prevista no orquestrador | ENTREGA item 9b |
| D4 | parada pela regra h4 | código 10 depois da i = 78 (5 h4 em 12); o Gestor decidiu tentar de novo (~18:10Z) | regra pré-registrada aplicada; retomada por decisão registrada | ENTREGA item 9c; DT-018 |
| D5 | extra em três invocações | 15:18 a 16:17Z, 16:20 a 16:40Z, 18:16Z; a retomada salta execuções já checadas | nenhuma execução repetida | ENTREGA item 9d |
| D6 | perfil do *host* diferente entre fases | núcleo com Edge e Spotify abertos; extra com Brave aberto até ~18:00Z; uso interativo às 16:02Z, dentro da i = 72 (descartada por h4) | covariável registrada; os contrastes da célula de teste estão todos no núcleo | ENTREGA item 9e |
| D7 | linhas `A1-FUSAO` perdidas | a extensão escreve cada escolha no *log* do servidor, apagado na recriação do *cluster* | descritivos da A1 (DT-027 §2.2) indisponíveis; desfechos não dependem delas | ENTREGA item 9f |
| D8 | supervisor do núcleo sem marca de fim | `supn2` não gravou `FIM-nucleo-OK` (não viu `ORQ_RC=`; causa não determinada); fim conferido e registrado à mão | o orquestrador gravou `FIM exit=0` e o MANIFEST confere | ENTREGA item 9g |
| D9 | conferência dos JSON na cópia do *tar* | na *worktree* do Windows os 8 JSON tinham CRLF (`core.autocrlf=true`) e davam SHA diferente; a conferência passou à cópia LF do *tar* | os arquivos levados à VM são os do *tar*, com os SHA registrados | roteiro §12 (`69eab57`) |
| D10 | limite de progresso do supervisor de 600 s | instrução do coordenador, no lugar de `exec_teto_s` + 90 s | só gera alerta, sem ação | roteiro §12 (`d101090`) |
| D11 | sonda tardia no teto | a `exec-0089` (F4, bloco 32) teve 309 candidatas, acima do teto de 306; as excedentes não foram sondadas | célula descritiva; nenhuma violação na F4 | ENTREGA item 6 |
| D12 | CPU e energia com 4 de 12 blocos na F2 | a regra que tira o bloco sem medida de *steal* excluiu 8 blocos porque o coletor começa 0,01 a 0,13 s depois da primeira operação; *steal* real zero | regra pré-registrada aplicada; sensibilidade de 12 blocos, não pré-registrada, com a mesma conclusão | validação de 11/11 §4 |

**Desvios de plano fora da avaliação**, que o texto também precisa declarar:

| # | Desvio | Situação | Fonte |
|---|---|---|---|
| P1 | nova medição de referência pela metade | piloto (12) e noite 1 (60 execuções: 56 aceitas, 4 recusadas, conferido nos `checagem.json`) executados; a noite 2 (as outras 60 e as reposições h4) foi suspensa em 06/11 para o piloto de violações e não rodou | `dados-vm/rebaseline-2026-11/noite1-completa-20261105/`; crônica de 06/11 |
| P2 | campanha S3 separada não executada | a campanha da Etapa C do apêndice (`ApPlanoTCC2.tex:51-54`, ~120 execuções com SYNC de referência) foi absorvida pela calibração, pela recalibração e pela avaliação | capítulo 7 |
| P3 | desenho da avaliação revisto três vezes antes dos dados | DT-021 (dois braços, F2 e F4 de teste) → DT-026 (benchmark refeito, F2 única de teste) → DT-027 (fatorial com A1 e AC-7), cada uma motivada por dados de calibração ou de piloto, nenhuma por dados da avaliação | capítulo 6 §6.1 |
| P4 | 64 segmentos em vez de 256 | mantido de todas as baterias, declarado antes da coleta | DT-016 |
| P5 | ação da P1 trocada | da parada do nó (AC-5) para a lista de ignorados (AC-7), antes da avaliação | DT-027, DT-028 |

## 8.2 Ameaças à validade

Classificação usual em engenharia de software experimental (interna, externa, de construto, de conclusão; fonte a
buscar, seção 8.6).

### Validade interna (o efeito é mesmo do tratamento?)

| Ameaça | Situação | Mitigação ou leitura |
|---|---|---|
| ordem dentro do bloco | o braço PO foi o primeiro em 7 dos 12 blocos da F2 (posição média 0,92 contra 2,0 do PL); com sorteio uniforme, P(≥ 7 de 12) = 0,014 (conferido por mim em `execucao.json`) | é a ordem pré-registrada (`random.Random(20261106 · 10⁶ + bloco)`); cada execução recria o *cluster*, mas uma deriva do *host* ao longo do bloco entraria como efeito de período; relatar |
| ambiente do *host* | quedas, SSD degradado, processos de fundo diferentes entre fases (D1, D2, D6) | o núcleo inteiro (todos os blocos da F2) rodou num boot, sem h4; o h4 descarta sem consultar desfecho; as fases não se misturam num bloco |
| seleção pelos descartes | 6 descartes, todos h4a e todos na extra e nas reposições; F1 perdeu 5 blocos | na F1 sobram blocos de quando o disco estava bom; a F1 é descritiva; relatar |
| anomalia da `exec-0037` | um braço AL com o nó isolado servindo sessões depois do desfazer pela maioria (capítulo 4, §4.12) | o bloco fica (excluir condicionaria numa variável posterior ao tratamento); sensibilidade sem ele (capítulo 5, §5.9) |
| controlador no mesmo processo da carga | a CPU do controlador concorre com a da carga | o braço que observa roda o mesmo código, então o custo de observação é igual |
| extensão A1 em todos os braços | o JAR fica montado mesmo nos braços com a política atual | só a `merge-policy` muda entre braços |
| assimetria de registro em disco | antes da P-64, o alvo do AC-7 gravava ~268 MB por execução só nos braços ativos | P-64 igualou os quatro braços (0,09 MB); desvio de ambiente em relação à recalibração, declarado na DT-028 §5 |

### Validade de construto (a medida mede o que se quer?)

| Ameaça | Situação | Leitura |
|---|---|---|
| V depende do instrumento | por pares distintos e com a sonda imediata, V mede sobretudo a **exposição** durante a partição; só com auditor e varredura mediria sobretudo a perda permanente e as conclusões se inverteriam | a definição foi fixada antes dos dados (DT-026 P-5); relatar as duas leituras (capítulo 5, §5.9), sem trocar o desfecho |
| forma 2 de I1 medida pela sonda | a releitura pelo nó isolado é feita pela sonda, não por um usuário real | sensibilidade sem a forma 2 |
| sonda imediata com erro | 14 a 17 % das releituras dão erro (pré-análise §7.5), o que subconta a exposição | igual nos braços em expectativa; nos braços ativos o erro é efeito real do AC-7 |
| p99 preso no patamar das retentativas | 1,503 a 1,505 s em 47 das 48 partições: o p99 mede o tempo de três falhas rápidas, não a cauda de sucesso | H_L pouco informativa; p99 só de sucesso como descritivo não pré-registrado (decisão de 07/11) |
| Δ_conv e Δ_rec pouco informativos | Δ_conv ≈ 0,01 s, porque é medido do fim da carga, ~110 s depois da fusão; Δ_rec ≈ tempo até a fusão (~43 s), igual nos braços | não discriminam braços; relatar como tal |
| energia por *proxy* | modelo linear com P_max da ficha e razão ociosa da literatura; utilização da VM ~5,7 %, então o termo ocioso domina | conclusões só relativas; sensibilidade com razão 0,5 |
| falso alarme medido por execução | "nenhuma P1 fora da partição" é contagem por execução, não por par de janelas | 0 nas 24 execuções aceitas das células sem falha, F3 e F1 (a definição de falso alarme da DT-027 §4; limite de cerca de 3/24 ≈ 12,5 % por execução) e 0 nas 8 da F4, reportada à parte; os dois braços contam, porque o que observa também decide a P1 |
| custo concentrado em clientes presos | 13 dos 40 usuários estão presos ao `isn3` sem roteamento para outro nó | com cliente que conhece a topologia o custo seria outro (validade externa) |

### Validade externa (vale fora deste ambiente?)

- Um *host* (i5-14600KF), uma VM Hyper-V de 4 vCPU e 8 GB, três nós num mesmo computador, rede *bridge*.
- Uma versão (Infinispan 15.0.21, JGroups 5.3.16) com detecção padrão (FD_ALL3 de 40 s, MERGE3 de 10 a 30 s), 64
  segmentos, duas cópias, `DENY_READ_WRITES` e caches não transacionais.
- Só `DIST_ASYNC` na avaliação; as referências SYNC saíram do desenho.
- Ponto de operação escolhido para gerar violações (carga L+, partição total e simétrica de um nó por ~96 s); outras
  cargas, durações e tipos de partição (parcial, assimétrica) não foram medidos.
- Clientes `pinned`, um servidor de aplicação por nó.
- Parâmetros do S3 são premissas de modelagem sem fonte empírica (`Metodologia.tex:313`).

### Validade de conclusão (a estatística sustenta a conclusão?)

- 12 blocos na F2: o menor valor-p possível é ~0,0005; a interação tem IC largo e não deve ser lida como prova de
  ausência de sobreposição.
- F1 com 3 blocos (menor valor-p possível 0,25) e F4 com 4 (0,125): só descritivas, sem teste.
- Contagens nulas (V = 0 nas descritivas) relatadas como "não observadas em N ações", com 3/N.
- Holm m = 2 nos co-principais e m = 11 nos secundários; sensibilidades sem correção, marcadas como tais.
- O desenho foi revisto depois de dados de calibração e de piloto (P3 da seção 8.1). As revisões foram registradas
  antes dos dados da avaliação, mas a escolha do ponto de operação e da ação usou informação de execuções anteriores
  no mesmo ambiente; a comparação continua válida para o ponto escolhido, e a generalização fica limitada.

## 8.3 Limitações

| Limitação | Detalhe | Fonte |
|---|---|---|
| F1 com 3 blocos | 5 blocos perdidos por h4a (20, 23, 24, 26 e o reposto 31); a reposição é uma só | ENTREGA item 3 |
| F4 com 4 blocos | o bloco 27 perdido e reposto pelo 32 | idem |
| linhas da A1 não preservadas | não se sabe quantas fusões a A1 decidiu diferente da regra atual nem se a saída de segurança foi usada | D7 |
| p99 preso no patamar das retentativas | ~1,5 s em 47 de 48 partições | capítulo 5, §5.6 |
| energia por *proxy* e só 4 de 12 blocos no oficial | *steal* sem medida no início da janela | D12 |
| ensaio da `exec-0037` não feito | hipótese sem teste; risco do AC-7 sem causa | decisão do Gestor de 11/11 |
| guardas do AC-7 não exercitadas na avaliação | guarda contínua, `suspeita-desfeita` e desfazer pela maioria depois da fusão só têm evidência de *smoke* local (n = 1 a 2) | capítulo 4, §4.8 |
| o AC-7 não alcança o que veio antes dele | os *logouts* confirmados pelo nó isolado antes da P1 (+0 a +6 s) continuam sujeitos à fusão; só a A1 os protege | pré-análise §5.3 |
| a A1 não reduz a exposição | a maioria serve a sessão aberta desde o *logout* no nó isolado até a fusão | relatório A1 §5 |
| `counters-async` fora da A1 e do AC-7 | o estado das identidades não é monótono | TD-020; G-2 |
| troca da política de replicação impossível em tempo de execução | só por código no servidor (E-1), recusado | capítulo 4, §4.7 |
| nova medição pela metade | só a noite 1 (60 de 120) | P1 da seção 8.1 |
| código fora do ramo principal | a *tag* da avaliação está só no repositório local; o remoto público vai até `19d45b9` (04/11) | capítulo 9 |

## 8.4 Trabalho futuro

1. **Ensaio da `exec-0037`:** aplicar a lista de ignorados, esperar a detecção, desfazer pela maioria e pelo alvo e
   ler o `get-availability` e as listas do nó isolado antes e depois, para confirmar ou descartar a divisão em dois
   lados ativos; gravar o valor do `get-availability` na trilha.
2. **Preservar os registros do servidor** de cada execução antes da recriação (linhas `A1-FUSAO`).
3. **Coletor de recursos antes da carga**, ou janela do *steal* a partir da primeira amostra.
4. **Medida de energia real** (medidor externo ou contadores do processador fora da VM).
5. **p99 das ações bem-sucedidas pré-registrado** e Δ_conv contado do fim da falha.
6. **Interceptador de replicação síncrona** no servidor (E-1), para trocar a política de replicação por operação.
7. **Política de fusão para as identidades** (variante do TLC), sem desfazer desbloqueios legítimos.
8. **Exclusão pela vista do JGroups (AC-6)** com canal de diagnóstico protegido, e detector de falhas mais rápido
   configurado na partida.
9. **Cliente que conhece a topologia**, para medir o custo do AC-7 com roteamento para a maioria.
10. **Mais blocos e outras condições**: F1 e F4 com 12 blocos, SYNC de referência, partições parciais e assimétricas,
    corte curto (exercitar `suspeita-desfeita`), outros *hosts*.
11. **Completar a nova medição de referência** (noite 2).
12. **Integrar o código ao ramo principal** e publicar a *tag* da avaliação.

## 8.5 Tabelas para a monografia

### T8.1 Desvios da execução da avaliação (NOVA, no capítulo de resultados ou nas limitações)

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Desvios na execução da avaliação do controlador}
\label{qd-desvios-avaliacao}
\centering
\footnotesize
\begin{tabular}{p{3.6cm}p{5.4cm}p{4.8cm}}
\hline
\textbf{Desvio} & \textbf{O que aconteceu} & \textbf{Tratamento} \\
\hline
Queda de energia entre fases & o hospedeiro ficou desligado de cerca de 07:54 a 14:06 UTC de 7/11, depois do fim da fase principal & nenhum bloco atravessa as fases; o \emph{cluster} é recriado antes de cada execução \\
Disco do hospedeiro lento & escritas lentas depois da queda & seis execuções descartadas pelo critério h4 e blocos refeitos \\
Parada pela regra h4 & cinco descartes em doze execuções na fase extra & retomada por decisão do autor \\
Registros da política de fusão perdidos & o registro do servidor é apagado na recriação do \emph{cluster} & descritivos da política de fusão indisponíveis \\
Sonda tardia no teto & uma execução da degradação com 309 candidatas para 306 & célula descritiva, sem violação \\
CPU e energia com 4 de 12 blocos & medida de \emph{steal} ausente no início da janela & resultado oficial com 4 blocos e sensibilidade com 12 \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

### T8.2 Ameaças à validade da avaliação (NOVA; substitui a parte experimental de `sec-limitacoes`, `ConclusoesParciais.tex:30-34`)

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Ameaças à validade da avaliação do controlador}
\label{qd-ameacas-validade}
\centering
\footnotesize
\begin{tabular}{p{2.2cm}p{6.2cm}p{5.4cm}}
\hline
\textbf{Tipo} & \textbf{Ameaça} & \textbf{Tratamento} \\
\hline
Interna & ordem dos braços dentro do bloco e deriva do hospedeiro & ordem sorteada pré-registrada, \emph{cluster} recriado, todos os blocos de teste numa só fase \\
Interna & execução anômala num braço ativo & bloco mantido, com análise de sensibilidade sem ele \\
Construto & taxa de violações por pares distintos mede sobretudo a exposição durante a partição & perda permanente como desfecho secundário pré-registrado \\
Construto & p99 limitado pelo patamar das retentativas & p99 das ações bem-sucedidas como descritivo \\
Construto & energia estimada por modelo & conclusões só relativas e sensibilidade da fração ociosa \\
Externa & um hospedeiro, uma versão do Infinispan, três nós, modo assíncrono e um ponto de operação & declarado como recorte \\
Conclusão & 12 blocos na célula de teste e três ou quatro nas descritivas & células descritivas sem teste; contagens nulas com limite superior \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

### T8.3 Limitações e trabalho futuro (opcional; o texto pode ficar em prosa nas conclusões)

Recomendo prosa curta nas conclusões, com a lista da seção 8.4 resumida em cinco itens: ensaio da execução anômala,
energia medida, desfechos de cauda mais informativos, replicação síncrona por operação no servidor e mais condições de
falha.

## 8.6 Já está na monografia × novo ou mudou

| Arquivo:linha | O que o texto diz | Situação | Evidência |
|---|---|---|---|
| `ConclusoesParciais.tex:30` (CON-38) | "nenhum dado de latência ou de violação é ainda utilizável"; F1 só ensaiada | **corrigir** | capítulo 7 |
| `ConclusoesParciais.tex:30` | "dez repetições ... 64 segmentos ... distribuição normal em F3" | **mantém** para a nova medição; acrescentar que ela ficou com 60 de 120 execuções | 8.1 P1 |
| `ConclusoesParciais.tex:32` (CON-39) | limites do controlador; P1 "custa vazão ... até o fim da execução" | **atualizar** com o AC-7 (custo só enquanto a ação vale) e com a A1 (age na perda, não na exposição) | capítulo 4 |
| `ConclusoesParciais.tex:34` (CON-09, CON-40) | 17 desligamentos; h4 | **atualizar** (19 até 07/11; a quinta queda caiu entre as fases da avaliação) e acrescentar D2 (SSD) | log do Windows; ENTREGA |
| `Desenvolvimento.tex:219` | três limitações conhecidas | **atualizar** com as desta seção | 8.3 |
| `Desenvolvimento.tex:221-238` (`sec-revisoes`) | revisões até 05/11 | **acrescentar** P2, P3 e P5 da seção 8.1 | 8.1 |
| não existe | desvios da avaliação, ameaças por tipo, trabalho futuro do controlador | **novo** | 8.1 a 8.4 |

## 8.7 Fontes bibliográficas necessárias

| Afirmação | Chave | Situação |
|---|---|---|
| classificação das ameaças à validade (interna, externa, construto, conclusão) | nenhuma | **Bibliotecário**: Wohlin et al., *Experimentation in Software Engineering* (Springer, 2012), ou Cook e Campbell (1979) |
| liberdade do pesquisador ao escolher condições depois de ver dados preliminares | nenhuma | **Bibliotecário** (opcional): Simmons, Nelson e Simonsohn (2011), ou Gelman e Loken; o pré-registro já é citado por `nosek_preregistration_2018` |
| interferência do ambiente em medições de desempenho e relato | `hoefler_belli_benchmarking_2015` | existe |
| variabilidade de medições na JVM | `georges_jvm_statistics_2007` | existe |
| poucas réplicas reproduzem a maioria das falhas | `yuan_simple_testing_2014` | existe, já citada |
| contagem nula com 3/N | `hanley_lippmanhand_zero_1983` | existe |
| troca entre consistência e disponibilidade | `gilbert_lynch_cap_2002` | existe |
| energia só por modelo em VM | `kansal_vm_power_2010` | existe |

## 8.8 Pendências e o que não pôde ser verificado

- **Noite 2 da nova medição:** falta decisão do Gestor sobre como relatar (campanha encerrada com 60 execuções ou
  metade da campanha planejada).
- **Números do SSD** (escritas lentas por hora, máximo de 496 ms): da ENTREGA; a série do `typeperf` não foi relida
  por mim.
- **Perfil de processos do *host*** (Edge, Spotify, Brave): da ENTREGA e dos arquivos `fat-host-*.txt`, não relidos.
- **Utilização da VM de ~5,7 % e erro da sonda imediata de 14 a 17 %:** da pré-análise; não reconferidos.
- **Classificação das ameaças:** precisa de fonte antes de entrar no texto.
