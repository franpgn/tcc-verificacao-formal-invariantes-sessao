# Traco prop-particao-w1 / r3-I2Fa

Fonte bruta: `runs/tlc/prop-particao-w1/r3-I2Fa.out` (4 estados, incluindo o inicial). Acoes: `Init>Block>Particionar>Mesclar`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** Block em r1 (identidade BLOCKED, sessoes ADMIN_BLOCK) ainda nao entregue; Particionar; Mesclar com rf sem o ID_BLOQUEIA: r1 volta a OPEN e as sessoes a VALID. Violacao de I2Fa (e de I1F/I1M: sessao INVALID volta a VALID).

```
State 1 <Init>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:1 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 2 <Block>
   sessoes      ( r1:( s1:I/AB@2 ; s2:I/AB@2 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:B@2 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r3:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:2 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <Particionar>
   sessoes      ( r1:( s1:I/AB@2 ; s2:I/AB@2 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:B@2 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r3:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:2 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
   particaoAtiva TRUE
State 4 <Mesclar>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r3:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:1 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
```
