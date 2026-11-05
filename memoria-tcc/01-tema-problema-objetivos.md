# Tema, Problema e Objetivos

> Fonte primária: `Main-PPEC.tex` (Proposta de TCC). Alterações a este documento exigem validação do **Gestor**.

## Tema delimitado

Verificação formal de invariantes de sessão em *data grids* (*In-Memory Data Grids* — IMDG), com foco em três condições operacionais: **replicação assíncrona**, ***failover*** (incluindo partições de rede, *rebalancing* e *state transfer*) e **controle orientado a SLO** (objetivos de nível de serviço, em especial percentis de latência p95/p99).

Recorte do objeto:
- **Estado modelado:** estado lógico de sessão (válida / inválida / bloqueada), metadados de versão / *timestamp* lógico, contadores de tentativas falhas, marcas de invalidação e bloqueio.
- **Operações modeladas:** login, validação, renovação, invalidação (*logout*), atualização de contadores e *flags*.
- **Plataforma experimental:** Infinispan em cluster (caches distribuídos; *cross-site replication* quando o cenário incluir múltiplas localidades).
- **Ferramenta de verificação:** *model checking* de sistemas de transições com invariantes (TLC / TLA+ ou alternativa equivalente).

## Problema de pesquisa

Em *data grids* sob replicação assíncrona e *failover*, operações concorrentes e a reconciliação posterior de réplicas divergentes podem violar invariantes de domínio do estado de sessão. O trabalho investiga **como especificar formalmente tais invariantes, como detectá-los (verificação formal + experimentação) e como reduzir sua ocorrência — controlando também a latência sob falhas — por meio de um mecanismo de decisão heurístico**.

Invariantes-alvo (exemplos do arquivo base):
- **Irreversibilidade de invalidação:** após um evento de *logout*, a sessão não deve retornar ao estado "válida".
- **Monotonicidade de contadores:** tentativas falhas não devem diminuir como resultado de reconciliações.
- **Consistência de bloqueio:** sessão / identidade marcada como bloqueada não deve ser validada como ativa.

## Justificativa

> ⚠️ **Lacuna (P-002):** o arquivo base **não possui uma seção de Justificativa explícita** nem revisão de trabalhos relacionados. É necessário redigi-la (Escritor) com fundamentação (Bibliotecário) e aprovação do Gestor.
>
> Elementos disponíveis no arquivo base para compor a justificativa: (i) a externalização de estado de sessão é prática comum em arquiteturas com balanceamento de carga e múltiplas instâncias; (ii) a replicação assíncrona troca latência por consistência, e essa troca tem efeitos observáveis sob falhas; (iii) SLO e latência de cauda são critérios operacionais relevantes; (iv) métodos formais permitem antecipar defeitos de concorrência difíceis de reproduzir por teste; (v) há valor prático em combinar verificação formal com validação experimental num mesmo artefato.

## Objetivo geral

Especificar e verificar invariantes do estado de sessão sob replicação assíncrona e *failover* em *data grids*, e avaliar um mecanismo de decisão heurístico que modifique políticas ou ações operacionais visando reduzir a ocorrência de violações desses invariantes e controlar métricas de latência sob falhas.

## Objetivos específicos

(Transcritos do arquivo base.)

1. Definir um modelo formal do estado de sessão e das operações relevantes (login, validação, renovação, invalidação e atualização de contadores e *flags*), incluindo explicitamente atrasos e reordenação de propagação de atualizações, e eventos de falha ou recuperação.
2. Declarar invariantes de segurança, correção e propriedades de convergência, e verificar essas propriedades via *model checking*, registrando contraexemplos quando existirem.
3. Estabelecer uma arquitetura experimental com Infinispan em cluster, com suporte a alterações de topologia e cenários de replicação, instrumentando um *workload* que gera operações concorrentes e detecta violações de invariantes.
4. Implementar um controlador heurístico que, com base em taxas e parâmetros observáveis, selecione entre políticas e aplique ações de mitigação.
5. Comparar experimentalmente o modelo estático com o modelo acrescido do controlador em cenários com injeção de falhas, quantificando taxa de violações, tempo de recuperação e convergência e estatísticas de latência.

## Escopo

- Modelagem formal do **núcleo** do protocolo de estado de sessão (não de um sistema de autenticação completo).
- Replicação **assíncrona** como caso principal; replicação síncrona aparece como contraste conceitual.
- Falhas modeladas: *crash*/*restart* de nós; partições de rede temporárias; mudanças de topologia que induzem *state transfer*; (multi-site) indisponibilização temporária de um site e posterior retorno com sincronização.
- *Model checking* com invariantes de segurança e propriedades de convergência; análise de contraexemplos.
- Avaliação experimental em Infinispan; métricas: taxa de violações, latência (p95/p99), tempo de recuperação/convergência.
- Controlador heurístico **externo** ao *data grid*, com conjunto **finito** de configurações/ações e regras determinísticas com histerese e limites de taxa de mudança.

## Fora de escopo

> ⚠️ **Lacuna (P-003):** o arquivo base **não declara explicitamente um "fora de escopo"**. Proposta de delimitação (**a validar pelo Gestor**):
- Implementação de um produto de gerência de sessão pronto para produção.
- Prova dedutiva assistida de teoremas (o trabalho usa *model checking*, não prova mecanizada — a confirmar).
- Aprendizado de máquina para a política de decisão (o controlador é heurístico/baseado em regras).
- Avaliação de outros *data grids* além de Infinispan (Hazelcast, Apache Ignite, Redis Cluster etc. ficam como trabalho futuro).
- Análise de segurança criptográfica de tokens/sessões; modelagem de adversário.

## Observações do Gestor

*(Vazio — aguardando entrada do Gestor.)*
