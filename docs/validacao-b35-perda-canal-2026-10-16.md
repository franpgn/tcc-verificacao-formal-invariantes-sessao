# Validacao V-I de B-35.3 e B-35.4 (Senior), 2026-10-16

Escopo: commits 71125ce e f252a51, branch `feature/b35-spec-perda-canal`. Relato do Worker tratado como alegacao; conferido contra brutos.

| Item | Veredito | Evidencia |
|---|---|---|
| 1. B-35.3 brutos | PASS | `r3-I1F.out` e `r3-I1P.out`: "Model checking completed. No error has been found.", 0 left on queue, 242208967 gerados / 7090745 distintos nos dois. Tempos do `.out`: 16min08s e 18min08s (968 s e 1088 s); tsv registra 969 s e 1089 s (relogio de parede, diferenca de 1 s, aceitavel). `PerderQualquer` D:T 2042563:6321857 (I1F) e 2056071:6321857 (I1P), nao vacuo. cfg: `SpecPerda0Semeada`, r3, `MaxPerdas=1`, `SemFalha`, `SYMMETRY Simetria`, mesmas constantes de r2. Ambiente: `perda-meta/ambiente-2026-10-16.log` tem registro antes de B-35.3 (java nenhum; `vmwp.exe` presente). |
| 2. Relatorio | PASS com ressalvas | Ressalva 2.6 no topo, status ILUSTRATIVO, secao 1 antes das tabelas, nenhuma frase proibida (grep), cotas declaradas para r2 e r3 e legenda de `TO`. Registro I1P particao: parciais 8.699.414 (P0) e 7.424.423 (P1) como TO; nocov 8.850.588 em **599 s no tsv** (o `.out` diz 9min58s = 598 s; ambos corretos, o relatorio ja explica); base 486 s. P1 r3 "determinado (nao medido)" esta honesto: diz expressamente que nao foi rodado e que e inferencia da vacuidade medida em r2. A secao 4 "Pode" e aceitavel por ser condicionada a cotas e ILUSTRATIVO, mas e a frase mais proxima do limite: era levemente generalizante ao tratar r3 como se cobrisse as seis propriedades (r3 so tem I1F e I1P). Corrigido. A "redacao obrigatoria literal" nao esta transcrita em documento unico; a ressalva usa os elementos da secao 2.6 (sem reparo, por construcao, nao diz nada sobre Infinispan, P0 limite superior, P1 defensavel). |
| 3. Amostragem r2 (>= 10 celulas) | PASS | 30+ celulas conferidas contra tsv: p0 sem mp1 (I5 V3, I4F V5, I2Fb V3, OK 4.474), p0 sem mp2 (OK 5.874), p0 queda mp1 (OK 35.452) e mp2 (OK 46.522), p1 queda mp1 (I5/I6 V4, I2Fb V4, OK 30.827) e mp2 (OK 37.446, I2Fb V3), p1 sem mp1/mp2 (I5/I6 V5, OK 1.988), p0/p1 particao (I1P TO, demais V4, I2Fb V3, I2Fa V4, I5/I6 V3 p0 e V5 p1), base sem/queda/particao (1.988; 15.690; 8.850.588; I5/I6 com 5 estados nos `.out`). Controle r3 `MaxPerdas=0`: 1.627.731 / 53.541.786. Sem divergencia. |
| 4. Isolamento | PASS | `git diff` entre o pai de B-35.1 e HEAD vazio para `SessionStore.tla`, `MCSession.tla`, `MCRelogio.tla`. Arquivos fora de `runs/tlc/perda*` tocados na branch: so `docs/relatorio-b35-*`, `docs/resultados-b35-1-2-*`, `spec/check-variante-perda*.sh`, `spec/run-tlc-perda.sh`. Catalogo e `registro-de-problemas.md` nao tocados. |

## Correcoes de texto no relatorio (commit `docs:`)

1. Secao 4, primeiro item de "Pode": acrescentado que em r3 so `I1F` e `I1P` foram avaliadas.
2. Secao 5: acrescentado limite da secao 2.6 das decisoes (sem duplicacao, corrupcao, perda assimetrica, particao nao transitiva), omitido antes.

Nenhum numero do relatorio precisou ser alterado.

## Ressalvas remanescentes

- Tempo 969/1089 s (tsv) vs 968/1088 s (`.out`): diferenca de arredondamento de parede; nao e erro.
- A celula nocov de 599 s fecha sem folga; o relatorio ja a classifica como nao reproduzivel.
- Sem push, sem VM.
