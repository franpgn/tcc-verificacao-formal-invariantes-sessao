# Análise da justificativa do trabalho (Revisor, 2026-06-14)

> Leitura crítica do texto consolidado após a rodada de reescrita de 2026-06-14 (itens E-01 a E-12 aplicados). Cobre Resumo, Introdução, Fundamentação Teórica, Metodologia, Desenvolvimento e Conclusões Parciais. O foco é identificar onde a justificativa do trabalho se sustenta e qual ideia recebe mais peso argumentativo.

## 1. O que a Seção 1.3 (Justificativa) declara

A Seção 1.3 enumera quatro vetores justificativos:

1. **Correção observada pelo usuário sob replicação assíncrona** (Dynamo, CRDTs): o desacoplamento entre latência de escrita e RTT vem com o custo de réplicas defasadas que podem reverter decisões de segurança.
2. **Resultados teóricos clássicos** (CAP, FLP, detectores de falha) e empíricos (Bailis & Kingsbury): falhas e partições são eventos esperados, não exceção.
3. **Foco em percentis altos** em vez da média (Dean & Barroso; Google SRE): a cauda da distribuição tem impacto desproporcional em sistemas *fan-out*.
4. **Domínio normativo** (NIST SP 800-63B, OWASP): as três propriedades-alvo têm respaldo direto em fontes normativas.

## 2. A justificativa que mais se evidencia no texto consolidado

A justificativa que mais se evidencia ao longo do trabalho **não é nenhuma das quatro isoladamente**, mas uma quinta que emerge da composição:

> **A literatura raramente trata a taxa de violação de invariantes como métrica primária, e raramente aplica especificação formal a invariantes específicos do domínio de sessão. A combinação proposta, invariantes normativos, especificação formal a montante, validação experimental em IMDG de uso corrente e controlador heurístico orientado a invariantes e a SLO, constitui um recorte que o mapeamento sistemático mostrou ausente.**

## 3. Onde o texto sustenta essa justificativa

Quatro pontos do texto consolidado reforçam essa leitura:

1. **Introdução §1.3 (parágrafo final):** "A combinação proposta, em um mesmo trabalho, de especificação formal, verificação por *model checking*, implementação experimental em um *data grid* de uso corrente e um controlador heurístico orientado a invariantes e a SLO constitui a contribuição pretendida."
2. **Fundamentação §2.7 (Trabalhos relacionados, reescrita 2026-06-14):** o único trabalho identificado como próximo, Nguyen et al. (2019), usa monitoramento reativo da camada de dados, e não verificação formal a montante. O posicionamento contrastivo é explícito.
3. **Desenvolvimento §4.5 (reescrita 2026-06-14):** "A síntese inicial do mapeamento, com 165 estudos selecionados para leitura completa, indica que a literatura raramente reporta a taxa de violação de invariantes como métrica primária, o que sustenta a contribuição pretendida do trabalho."
4. **Conclusões §5.1 (reescrita 2026-06-14):** "A escolha do par de métricas latência de cauda e taxa de violação como contribuição principal é sustentada pela síntese inicial do mapeamento, que indica baixa incidência da segunda métrica na literatura, e pelos contraexemplos do TLC, que fornecem o catálogo inicial de padrões de divergência a serem mitigados pelo controlador heurístico previsto para a etapa seguinte."

## 4. Justificativas que aparecem com peso menor

- **Sustentabilidade computacional** (Introdução §1.1, último parágrafo): citada via Turner 2023 e Morán 2024, mas reaparece apenas uma vez. Funciona como moldura do problema, não como fio condutor.
- **Resultados teóricos CAP/FLP** (§1.3 + §2.1): são pré-requisito do argumento, não a justificativa nuclear.
- **Cauda de latência** (Dean & Barroso, SRE): contribui para a métrica, não para a originalidade do recorte.

## 5. Diagnóstico de leitura

A justificativa do trabalho **não é** "queremos verificar formalmente um sistema de sessão", nem **é** "queremos controlar latência sob falha". É:

> a literatura trata cada peça desse problema isoladamente, invariantes normativos em segurança, métodos formais em sistemas distribuídos, métricas de cauda em SRE, controladores autoadaptativos em nuvem, e a contribuição é juntar as quatro peças no mesmo objeto experimental, com a taxa de violação de invariantes como métrica primária ao lado da latência de cauda.

## 6. Recomendações ao Gestor

- **R-J1:** o parágrafo final de §1.3 já contém essa síntese, mas está dissolvido. Sugere-se destacá-lo como sentença-tese explícita, talvez como abertura da Seção 1.3 ou como pergunta retórica. Não é mudança aprovada unilateralmente; depende de decisão do Gestor sobre estilo (manter o tom impessoal monolítico ou destacar tese).
- **R-J2:** se houver dúvida na banca sobre originalidade, a frase a citar é "a literatura raramente reporta a taxa de violação de invariantes como métrica primária". Ela aparece duas vezes (§4.5 e §5.1) e é a frase com maior densidade justificativa do texto consolidado.

## 7. Status

Análise produzida pelo Revisor em 2026-06-14, após a rodada de reescrita E-01 a E-12 da mesma data. Pendente decisão do Gestor sobre R-J1.
