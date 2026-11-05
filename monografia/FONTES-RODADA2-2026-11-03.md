# Fontes, rodada 2 (Cap. 4, versão oficial): DES-43, DES-54 e DES-55

Bibliotecário, 03/11/2026. Apenas leitura de `Desenvolvimento.tex`, que não foi editado. Consultas externas foram feitas sem e-mail (R-EMAIL): o manual do JGroups, o OpenAlex e as páginas da USENIX. Pendência aberta: P-035. Candidatas registradas: CAND-085 e CAND-086 (`tcc-escrita/memoria-tcc/05-referencias-candidatas.md`, Eixo M).

---

## 1. DES-55, 2º parágrafo: "Falhas de rede reais raramente cortam toda a comunicação"

**Veredito: a fonte não sustenta o advérbio. A frase precisa ser reescrita, mas não é preciso fonte nova.**

O artigo de Bailis e Kingsbury é um levantamento informal ("An informal survey of real-world communications failures", p. 48). Ele não traz nenhuma contagem que compare falhas parciais com cortes totais. A ênfase do texto é a oposta: as partições completas existem e são frequentes. Exemplos: "Partitions deserve serious consideration" (p. 55) e "communication-isolating network failures are a real risk" (p. 55). O único "rarely" sobre partições aparece num relato de firmas financeiras cujas redes "rarely, if ever, exhibit partition behavior" (p. 55), e isso fala da raridade da própria falha, não de falhas parciais.

O artigo relata, ao lado das partições completas, falhas que não cortam toda a comunicação. Texto lido em `tcc/leituras/REF-034-Bailis-Kingsbury-NetworkReliable-2014.txt` (CACM 57(9), com as páginas conferidas pelos cabeçalhos):

| p. | Trecho literal |
|---|---|
| 50 | Jeff Dean, primeiro ano de um *cluster* Google: "Five racks go wonky (40–80 machines seeing 50% packet loss)" |
| 51 | "Large-scale virtualized environments are notorious for transient latency, dropped packets, and full-blown network partitions" |
| 51 | "Network disruptions in EC2 can affect only certain groups of nodes" |
| 52 | Freistil IT: "50%–100% packet loss localized to a specific datacenter" |
| 53 | NIC Broadcom BCM5709 "dropped inbound but not outbound packets" (falha assimétrica: o primário seguia enviando *heartbeats*) |

**Redação sugerida** (substitui só a frase com `\citep{bailis_kingsbury_network_2014}`):

> Falhas de rede reais nem sempre cortam toda a comunicação: ao lado de partições completas, o levantamento de \citeonline[p.~50--53]{bailis_kingsbury_network_2014} relata perdas de 50\,\% a 100\,\% dos pacotes, perturbações que afetam só alguns grupos de nós e uma placa de rede que descartava os pacotes recebidos, mas não os enviados.

(`\citeonline[p.~50--53]{...}` gera "Bailis e Kingsbury (2014, p. 50-53)". Na versão curta abaixo, `\cite[...]` gera "(BAILIS; KINGSBURY, 2014, p. 50-53)".)

Versão curta, se o parágrafo precisar ficar enxuto:

> Além de partições completas, falhas de rede reais incluem perda parcial de pacotes e falhas assimétricas \cite[p.~50--53]{bailis_kingsbury_network_2014}.

**Fonte nova (opcional, não citável ainda):** a CAND-086 é Alfatafta et al., OSDI 2020, p. 351-368, que define partição parcial ("disrupt the communication between some but not all nodes in a cluster", resumo). Ela também não dá frequência relativa, portanto não salva o "raramente". Só vale incluí-la se o Gestor quiser uma fonte específica para a definição de F4.

---

## 2. DES-54, FD_SOCK2: "substituiu o `podman stop` [...] por causa do detector FD_SOCK2"

**Veredito: o mecanismo está atribuído ao lugar errado.** Com `podman stop` (SIGTERM), o nó sai de forma graciosa. O servidor fecha o canal JGroups, o membro envia o pedido de saída (LEAVE) ao coordenador, e o GMS instala uma nova visão sem o nó. **Nenhum detector de falhas atua**, nem o FD_SOCK2 nem o FD_ALL3. O FD_SOCK2 explica outra coisa: por que um SIGKILL isolado também não basta e a interface precisa ser desativada antes. Com o processo morto, o sistema operacional fecha os *sockets* TCP, o FD_SOCK2 suspeita do nó em cerca de 1 s, e só sem a interface a detecção passa a depender do *timeout* do FD_ALL3. A Metodologia (MET-15 e linha 320) já descreve isso corretamente. Só a frase de DES-54 mistura as duas coisas.

**Evidência documental:** JGroups Manual 5.x, http://www.jgroups.org/manual5/index.html (o certificado TLS do host é inválido; use `http://`).
- §3.10.13 "Disconnecting from a channel" (`#Disconnecting`): o `disconnect()` "will leave the cluster [...] by sending a leave request to the current coordinator. The latter will subsequently remove the leaving node from the view and install a new view in all remaining members."
- §6.8.7, Tabela 35 "Failure detection behavior" (`#FD_HOST`): "Member N leaves | Since this is a graceful leave, none of the failure detection protocols kick in". E também: "Any member (say O) crashes | FD_SOCK detects this immediately (as the TCP socket was closed)".
- §6.8.6 FD_SOCK2 (`#FD_SOCK2`): "When the client receives a connectionClosed(ping-dest) callback [...] it considers ping-dest to have crashed and emits a SUSPECT event. On a view change that changes ping-dest from P to Q, P will not get suspected".
- §6.8.4 FD_ALL3 (`#FD_ALL3`) e §6.8: o nó é suspeito depois do *timeout* "if neither a heartbeat nor data have been received from it".

**Evidência experimental** (ensaio local WSL de 28/10, não na VM): `wt-f1-kill/runs/smoke-f1-silencioso-2026-10-27/`, resumido em `wt-f1-kill/docs/relatorio-f1-silencioso-2026-10-27.md:397-417`.
- `e4-stop/logs-f1s-isn2.log:2-4`: `ISPN080002: Infinispan Server stopping`, depois `ISPN000390: Persisted state`, depois `ISPN000080: Disconnecting JGroups channel` (05:02:48,869).
- `e4-stop/logs-f1s-isn1.log:25,27`: nova visão `(2) [isn3, isn1]` e `ISPN100001: Node isn2-8161 left the cluster` às 05:02:48,874, 5 ms depois. Não há nenhuma linha de *suspect* nos logs de `e4-stop`.
- Δdet: `stop` 0,2 a 1,2 s (saída graciosa); `kill` sozinho 1,03 s (FD_SOCK2 + VERIFY_SUSPECT2); silencioso (interface desativada e depois `kill`) 36,5 a 41,9 s (FD_ALL3). A pilha está em `wt-f1-kill/cluster/jgroups-tcp.xml:59-61`.

**Chave no `.bib`:** não existe nenhuma chave JGroups em `monografia-atual/bibliografia.bib`. As chaves Infinispan (`infinispan_docs_stable`, `infinispan_configuring_15` etc.) não tratam do protocolo de saída nem dos detectores. Registrei o manual como **CAND-085** (`jgroups_manual_5`, proposta). Ele só pode ser citado depois da aprovação do Gestor.

**Redação sugerida** (substitui "por causa do detector FD\_SOCK2 (Seção~\ref{sec-falhas})."):

Com citação, se o Gestor aprovar a CAND-085:

> [...] substituiu o \texttt{podman stop} usado na bateria de junho e nas verificações intermediárias, porque a parada ordenada não exercita a detecção de falhas: com \texttt{SIGTERM}, o servidor fecha o canal JGroups e pede a saída ao coordenador, que instala a nova visão sem que nenhum detector suspeite do nó \cite[seções 3.10.13 e 6.8.7]{jgroups_manual_5}. A interface de rede é desativada antes do \texttt{SIGKILL} porque, com as conexões TCP fechadas pelo sistema operacional, o FD\_SOCK2 detectaria a queda em cerca de um segundo, e a detecção passaria a não depender do FD\_ALL3 (Seção~\ref{sec-falhas}).

Sem citação, se a CAND-085 for recusada (a frase fica apoiada no ensaio próprio, que DES-54 já menciona):

> [...] porque a parada ordenada não exercita a detecção de falhas: o nó encerrado com \texttt{SIGTERM} sai do \emph{cluster} por saída voluntária, e os demais instalam a nova visão sem suspeitar dele. A interface é desativada antes do \texttt{SIGKILL} para que o FD\_SOCK2 não perceba o fechamento das conexões (Seção~\ref{sec-falhas}).

Os números do ensaio local (cerca de 1 s com `kill`, 36,5 a 41,9 s no modo silencioso) só devem entrar no texto com a ressalva de que vêm do *cluster* local, como DES-54 já faz.

---

## 3. DES-43: "simulação" ou "tempo esperado de absorção"?

**Veredito: o artefato diz "simulação", mas não deixou *script* nem saída. Os números são o valor esperado exato da cadeia, e o texto do capítulo ("tempo esperado de absorção") está correto. O que precisa mudar é o comentário de fonte.**

- **Artefato:** `wt-plano-ctrl/docs/diagnostico-login-blocked-2026-10-22.md:112-116` (§2.3): "Simulação da cadeia do contador [...] S1 (p_inc = 0,10, p_reset = 0,05) leva em média ≈ 131 operações [...] S2 (p_inc = 0,01, sem reset) leva ≈ 500 operações". Procurei *script* ou saída da simulação em `wt-plano-ctrl`, `wt-ctrl-integ`, `wt-b35` e no restante de `tcc/` e não encontrei nada. O `MCPerda` do B-35 é um modelo TLA+ de perda de mensagens, sem relação com este cálculo, e a pesquisa MTBS também não trata dele.
- **Parâmetros contra o código que gerou os dados** (`git show 51a31f0`, em `wt-plano-ctrl`):
  - `Scenario.java`: `S1(50, 25, 10, 10, 5)` e `S2(95, 3, 1, 1, 0)` (pesos de Validate, Login, Logout, Increment e Reset). Isso dá p_inc = 0,10 e p_reset = 0,05 em S1, e p_inc = 0,01 sem reposição em S2.
  - `SessionOps.java:24`: `LIMITE_TENTATIVAS = 5`.
  - `IdentityState.java` `incrementar`/`resetar`: o bloqueio ocorre com o contador ≥ 5, e o `resetar` zera o contador mantendo o `status` (O5).
  - `WorkloadMain.java` `rodarCliente`: identidade `user-<tid>` única para login, O4 e O5, e cada iteração do laço conta uma operação.
- **Recálculo** (cadeia 0..4 transientes, 5 absorvente; script em `C:\Users\FRANCE~1\AppData\Local\Temp\claude\C--Users-Francesco-Development-tcc\041075e7-7048-4ed6-bcba-1d86566f672a\scratchpad\absorb.py`):
  - S1: E[T] = 1055/8 = **131,875** exato (Monte Carlo, 10^5 réplicas: 132,2).
  - S2: E[T] = 5/0,01 = **500** exato (média da binomial negativa; Monte Carlo 501,0).
  - Os valores "cerca de 130" e "500" do texto conferem. Se quiser mais precisão, use "cerca de 132".
- **Ritmo medido** (brutos da VM, `dados-vm/baseline-2026-06-23/runs/S{1,2}-none/rep-00{1,2,3}.stdout.log`, linha "Total de operacoes", 8 *threads*, 90 s): S1 de 3 131 a 3 229 op/s por *thread*, S2 de 4 790 a 4 921. Isso dá cerca de 0,04 s (S1) e 0,10 s (S2) até o bloqueio. "Frações de segundo" continua válido. Atenção: o diagnóstico fala em "≈ 1 260 op/s por thread", "≈ 0,10 s" e "≈ 0,28 s", e esses valores **não conferem** com os brutos da VM. Não use esses números no texto.
- Também conferidos contra os brutos (`S1-none/summary.json`, `S2-none/summary.json`): 1 291 332 *logins* em S1 sem falha; *login* e validação somam 75,0 % (S1) e 98,0 % (S2).

**Redação sugerida.** O texto pode ficar como está. Opcionalmente, deixe o método explícito:

> [...] o tempo esperado de absorção da cadeia do contador, calculado de forma exata, indica bloqueio permanente depois de cerca de 130 operações da \emph{thread} em S1 e 500 em S2, em média, o que corresponde a frações de segundo no ritmo medido.

**Comentário de fonte** (substitui a linha `% Fonte:`):

> % Fonte: wt-plano-ctrl/docs/diagnostico-login-blocked-2026-10-22.md, secao 2.3 (o documento diz "simulacao", sem script preservado). Valor esperado exato da cadeia, conferido pelo Bibliotecario em 03/11/2026: S1 p_inc = 0,10, p_reset = 0,05 -> 1055/8 = 131,875 operacoes; S2 p_inc = 0,01 sem reset -> 500. Pesos de Scenario.java e limite 5 de SessionOps.java no commit 51a31f0.

---

## Ações

| Quem | O quê |
|---|---|
| Escritor | Reescrever a frase de DES-55 (seção 1). Corrigir o motivo em DES-54 (seção 2), em uma das duas versões, conforme a decisão sobre a CAND-085. Trocar o comentário de fonte de DES-43. |
| Gestor | Aprovar ou recusar a CAND-085 (manual JGroups 5.x) e a CAND-086 (opcional). |
| Bibliotecário | Incluir a entrada no `.bib` depois da aprovação (P-035). |
