# Traco prop-livre-particao-w1 / r3-I2Fa

Fonte bruta: `runs/tlc/prop-livre-particao-w1/r3-I2Fa.out` (4 estados, incluindo o inicial). Acoes: `Init>Block>Particionar>Mesclar`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** Block em r1 (identidade BLOCKED, sessoes ADMIN_BLOCK) ainda nao entregue; Particionar; Mesclar com rf sem o ID_BLOQUEIA: r1 volta a OPEN e as sessoes a VALID. Violacao de I2Fa (e de I1F/I1M: sessao INVALID volta a VALID).

```
State 1 <Init>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 2 <Block>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:B@1 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {ID_BLOQUEIA u1 sess=I/AB@1 id=B@1 c0 af={}} >> ; r3:<< {ID_BLOQUEIA u1 sess=I/AB@1 id=B@1 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <Particionar>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:B@1 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {ID_BLOQUEIA u1 sess=I/AB@1 id=B@1 c0 af={}} >> ; r3:<< {ID_BLOQUEIA u1 sess=I/AB@1 id=B@1 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
   particaoAtiva TRUE
State 4 <Mesclar>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {ID_BLOQUEIA u1 sess=I/AB@1 id=B@1 c0 af={}} >> ; r3:<< {ID_BLOQUEIA u1 sess=I/AB@1 id=B@1 c0 af={}} >> )
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
```
