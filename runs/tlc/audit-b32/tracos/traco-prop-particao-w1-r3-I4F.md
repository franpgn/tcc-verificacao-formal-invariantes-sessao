# Traco prop-particao-w1 / r3-I4F

Fonte bruta: `runs/tlc/prop-particao-w1/r3-I4F.out` (4 estados, incluindo o inicial). Acoes: `Init>Logout>Particionar>Mesclar`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** Logout em r1 (ts 2) ainda nao entregue a r2/r3; Particionar {r1}|{r2,r3}; Mesclar copia o registro de rf (lado vencedor) que ainda ve VALID ts 1: o logout confirmado em r1 e revertido. Violacao do passo Mesclar (I1F, I1M; I4F/I4M porque o motivo vira NONE).

```
State 1 <Init>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:1 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 2 <Logout>
   sessoes      ( r1:( s1:I/LO@2 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <Particionar>
   sessoes      ( r1:( s1:I/LO@2 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
   particaoAtiva TRUE
State 4 <Mesclar>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
```
