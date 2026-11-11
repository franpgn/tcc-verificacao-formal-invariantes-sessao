# Recorte da regressao de junho (B-43.3a, parte i)

`S1-F1-rep-001-head.csv` = cabecalho + as primeiras 5 000 linhas de dados de
`tcc-desenvolvimento/runs/S1-F1/rep-001.csv` (baseline F1 de 2026-06-22),
obtido com `head -n 5001`. E um prefixo byte a byte do original, inclusive os fins de
linha CRLF com que o original foi gravado.

| Arquivo | Linhas | SHA-256 |
|---|---|---|
| `runs/S1-F1/rep-001.csv` (completo, fora deste diretorio, 56 788 565 bytes) | 642 630 | `5ea464aeb2b0ffad738f63fb5cb30e79a0f447a9195825828659eea0023aa198` |
| `S1-F1-rep-001-head.csv` (recorte) | 5 001 | `3fd92d2c8b47aeb8238ac59a811fce580fb72485d2157d70b408cee41ed794f8` |

Contagens `operation x return_code` do recorte: Login BLOCKED 1 282, Validate NONE 2 504,
Logout NONE 503, IncrementFailure COUNTED 484, ResetFailures NONE 227. Nenhum Login OK e
nenhum Validate VALID, como no arquivo completo (defeitos C1/C2 do diagnostico de 2026-10-22).

Usado por `SanidadeRegressaoJunhoTest.s1F1Rep001DaFalha`. O arquivo completo so entra no
teste `s1F1Rep001CompletoContagensExatas`, ativado por `-Dsanidade.csvJunho=<caminho>`.

O `.gitattributes` deste diretorio marca `*.csv` como `-text`, para o Git nao normalizar
os fins de linha (o SHA-256 acima e o do arquivo exatamente como versionado).
