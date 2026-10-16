# Relatorio B-35.4: variante `MCPerda` (perda de mensagens no canal, so no harness), 2026-10-16

**Status: ILUSTRATIVO.** Nada neste documento entra no catalogo como evidencia do sistema. Branch `feature/b35-spec-perda-canal`; nenhum push, nenhuma VM; `SessionStore.tla`, `MCSession.tla` e `MCRelogio.tla` nao foram alterados.

> **Ressalva (secao 2.6 das decisoes):** a variante nao tem retransmissao, transferencia de estado nem resolucao de conflito, que e o que o sistema real usa para reparar. Uma violacao de `I5`/`I6` com perda e, portanto, **por construcao** e nao diz nada sobre perda de dados no Infinispan. Perda leve de pacote nao vira perda de mensagem no sistema real (TCP mais `UNICAST3`/`NAKACK2` a escondem como atraso e retransmissao). P0 e apenas limite superior ilustrativo; P1 e a forma defensavel (perda ligada a mudanca de vista), e so modela perda **por destino**.

Convencao: **medido** esta nos `.out`/`.tsv` brutos em `runs/tlc/`; **inferido** vem de leitura do codigo TLA+. Detalhes e reproducao em `docs/resultados-b35-1-2-2026-10-16.md`.

## 1. Leitura obrigatoria antes da tabela

Com a especificacao corrente, I5 e I6 ja sao violadas sem nenhuma perda de mensagem, por operacoes concorrentes de mesmo ts (por exemplo Logout e Block), em traco de 5 estados, em todas as colunas de falha. A perda encurta o contraexemplo (3 a 5 estados), mas nao e a causa da divergencia. Os resultados de I5/I6 em MCPerda nao permitem afirmar que a convergencia depende da entrega.

Evidencia (medido): `runs/tlc/perda-base-controle/semeado-{sem,queda,particao}/r2-I{5,6}.out`, `SpecSemeada` sem `MCPerda`, 5 estados em todas; em sem falha, `Logout(s1)` em r1 e `Block` em r2, depois duas entregas, terminam com `s1` em `INVALID/LOGOUT` (r1) e `INVALID/ADMIN_BLOCK` (r2), canais vazios. Em `MCPerda` o controle com `MaxPerdas=0` reproduz o mesmo veredito.

## 2. O que a variante e e os controles

- Modulo `spec/MCPerda.tla` estende `MCSession`; variavel `perdas` declarada no modulo novo; `PerdasOK` como `INVARIANT` em todo cfg.
- P0 (`PerderQualquer`): qualquer mensagem de qualquer canal, ate `MaxPerdas`. P1 (`PerderNaVista`): so mensagem destinada a replica que nao pode agir (caida ou de lado minoritario).
- Fidelidade ao `SessionStore.tla`: `spec/check-variante-perda.sh` (OK) e `spec/check-variante-perda.test.sh` (27 PASS, 0 FAIL). SANY limpo.
- **Controle de regressao `MaxPerdas=0` (medido), P0 e P1:** r2 sem falha 1.988 distintos / 38.643 gerados; r2 queda 15.690 / 195.239; r3 sem falha 1.627.731 / 53.541.786 (`WORKERS=1`). Todos identicos a base, mesmos vereditos, `Perder*` 0:0.
- **Controle positivo de I5 (medido, lido no traco):** P0 r2 sem falha, `MaxPerdas=1`: `VIOLADA` em 3 estados (`Initial`, `Logout(s1)` em r1, `PerderQualquer` que descarta a mensagem de `canal[r2]`). Mutacao dinamica (`RemoverMsg` nulo): o traco de 3 estados some e volta o traco de 5 estados sem passo `Perder*`. Como I5 ja falha na base, o criterio e o traco curto com `Perder*`, nao o desaparecimento da violacao.
- **Vacuidade (medido):** P1 sem falha tem `PerderNaVista` 0:0 e 1.988 distintos, iguais a base, com `MaxPerdas` 1 e 2.

## 3. Resultados

Cotas comuns das linhas r2: semeado, `r2` (`{r1,r2}`), `IDS={u1}`, `SIDS={s1,s2}`, `MaxCanal=2`, `MaxRelogio=3`, `LIMITE=2`, `MaxEventosFalha=2`, `SYMMETRY`, `TIMEOUT_SECS=600`, `WORKERS=2`, `-coverage 1`. Legenda: `OK(n)` = `SEM_VIOLACAO` exaustivo dentro dessas cotas, `n` estados distintos; `Vn` = `VIOLADA`, traco de `n` estados; `TO(n)` = `SEM_VIOLACAO_ATE_AQUI`, **nao e resultado verde**, `n` = distintos alcancados aos 600 s.

### 3.1 r2 semeado (cotas acima)

| sabor | MaxPerdas | falha | I5 | I6 | I1P | I1F | I4F | I2Fa | I1M | I4M | I2Fb |
|---|---|---|---|---|---|---|---|---|---|---|---|
| p0 | 1 | sem | V3 | V3 | OK(4.474) | OK(4.474) | V5 | OK(4.474) | OK(4.474) | OK(4.474) | V3 |
| p0 | 2 | sem | V3 | V3 | OK(5.874) | OK(5.874) | V5 | OK(5.874) | OK(5.874) | OK(5.874) | V3 |
| p0 | 1 | queda | V3 | V3 | OK(35.452) | OK(35.452) | V5 | OK(35.452) | OK(35.452) | OK(35.452) | V3 |
| p0 | 2 | queda | V3 | V3 | OK(46.522) | OK(46.522) | V5 | OK(46.522) | OK(46.522) | OK(46.522) | V3 |
| p0 | 1 | particao | V3 | V3 | TO(8.699.414) | V4 | V4 | V4 | V4 | V4 | V3 |
| p1 | 1 | sem | V5 | V5 | OK(1.988) | OK(1.988) | V5 | OK(1.988) | OK(1.988) | OK(1.988) | V3 |
| p1 | 2 | sem | V5 | V5 | OK(1.988) | OK(1.988) | V5 | OK(1.988) | OK(1.988) | OK(1.988) | V3 |
| p1 | 1 | queda | V4 | V4 | OK(30.827) | OK(30.827) | V5 | OK(30.827) | OK(30.827) | OK(30.827) | V4 |
| p1 | 2 | queda | V4 | V4 | OK(37.446) | OK(37.446) | V5 | OK(37.446) | OK(37.446) | OK(37.446) | V3 |
| p1 | 1 | particao | V5 | V5 | TO(7.424.423) | V4 | V4 | V4 | V4 | V4 | V3 |

Base sem perda, mesmas cotas (`perda-base-controle/`): sem `I1P I1F I2Fa I1M I4M` OK(1.988), `I4F` V5, `I2Fb` V3, `I5`/`I6` V5; queda idem com OK(15.690); particao `I1P` OK(8.850.588), `I1F I4F I1M I4M` V4, `I2Fa` V5, `I2Fb` V3, `I5`/`I6` V5.

Leitura do que as seis propriedades de seguranca por replica (`I1P I1F I4F I2Fa I1M I4M`) mostram (medido): em sem falha e queda, P0 e P1, `MaxPerdas` 1 e 2, todo veredito da base se repete. Nenhuma propriedade preservada na base passou a ser violada nessas colunas. Em particao, as violadas na base continuam violadas. Isso vale dentro das cotas acima, em r2.

### 3.2 r3, P0, sem falha, `MaxPerdas=1` (B-35.3 reduzido)

Cotas: semeado, `r3`, mesmas constantes, `TIMEOUT_SECS=1800`, `WORKERS=2`, `-coverage 1`. Fonte: `runs/tlc/perda-semeado-p0-sem-mp1/r3-*`.

| propriedade | resultado | distintos / gerados | tempo | `PerderQualquer` D:T |
|---|---|---|---|---|
| I1F | OK, exaustivo, dentro das cotas | 7.090.745 / 242.208.967 | 969 s | 2.042.563 : 6.321.857 |
| I1P | OK, exaustivo, dentro das cotas | 7.090.745 / 242.208.967 | 1.089 s | 2.056.071 : 6.321.857 |

A base r3 sem falha tem 1.627.731 distintos; com P0 e `MaxPerdas=1` o espaco foi para 7.090.745. `PerderQualquer` disparou (D > 0), logo o resultado nao e vacuo. P1 r3 sem falha nao foi rodado: pela vacuidade medida em r2 (secao 2) o resultado e determinado (distintos iguais a base); r3 nao foi medido para P1.

### 3.3 Registro de I1P em particao (r2)

| celula | resultado | detalhe |
|---|---|---|
| P0, `MaxPerdas=1`, com coverage | `TO` (`SEM_VIOLACAO_ATE_AQUI`) | 8.699.414 distintos aos 600 s; nao fechou |
| P1, `MaxPerdas=1`, com coverage | `TO` | 7.424.423 distintos aos 600 s; `PerderNaVista` 0:0 |
| P1, `MaxPerdas=1`, sem coverage (`-nocov`) | OK exaustivo | 8.850.588 distintos / 180.269.215 gerados, **599 s do bruto de 600 s**; sem folga, nao usar como cota |
| base sem perda | OK exaustivo | 8.850.588 distintos, 486 s |

(O bruto `perda-semeado-p1-particao-mp1-nocov/resumo-r2.tsv` registra 599 s; o valor reportado verbalmente de 598 s nao bate com o bruto.) A celula P1 sem coverage deve ser lida como "fechou no limite do timebox", e nao como cota reproduzivel. Explicacao do P1 vacuo em particao r2 (**inferida**, nao testada): com uma replica por lado a particao e empate e `Vencedor` devolve os dois lados, entao `PodeAgir` e verdadeiro nos dois e `~PodeAgir(r)` so vale para replica caida.

## 4. O que se pode e o que nao se pode afirmar

Pode (dentro das cotas e do status ILUSTRATIVO):
- Nas colunas sem falha e queda de r2, e em sem falha de r3 para P0 com `MaxPerdas=1` (em r3 foram avaliadas so `I1F` e `I1P`), perder mensagens do canal nao fez nenhuma das propriedades de seguranca por replica avaliadas deixar de valer onde valia na base.
- Com a especificacao corrente, `I5`/`I6` ja nao valem sem perda; ver secao 1.

Nao pode:
- Dizer qualquer coisa sobre o Infinispan real.
- Dizer que a convergencia depende da entrega, nem que a perda e causa de divergencia.

## 5. Limites

- Nada sobre o Infinispan real; o modelo nao tem reparo (retransmissao, transferencia de estado, resolucao de conflito).
- r3 com queda e particao nao foram rodados (> 60 M estados, fora do escopo). r3 so cobre P0, sem falha, `MaxPerdas=1`.
- P1 modela so perda por destino na vista; a perda por origem (no que caiu antes de enviar) nao existe no modelo.
- Tamanho de traco com `WORKERS=2` nao e minimo e nao deve ser comparado entre celulas (em particao, `I2Fa` tem 5 estados na base e 4 sob perda, com traco de 4 estados que existe tambem na base).
- A cobertura `D:T` de celulas `VIOLADA` e parcial (parada na violacao); so celulas exaustivas sustentam afirmacao de disparo ou vacuidade.
- `I1P` em particao: ver secao 3.3.
- O modelo nao cobre duplicacao, corrupcao, perda assimetrica nem particao nao transitiva (secao 2.6 das decisoes).
- Nao ha rodada com `Login` livre (modo `livre`); so o semeado.

## 6. Ambiente

`runs/tlc/perda-meta/ambiente-2026-10-16.log` registra `tasklist` antes de cada rodada. Nenhum `java` ativo antes de nenhuma delas; `vmwp.exe` (PID 22164, servico Hyper-V) presente em todas, estado das VMs nao verificavel por mim. B-35.3 rodou com `WORKERS=2` (nenhum java de outro agente ativo no registro). Catalogo e `registro-de-problemas.md` nao foram alterados.
