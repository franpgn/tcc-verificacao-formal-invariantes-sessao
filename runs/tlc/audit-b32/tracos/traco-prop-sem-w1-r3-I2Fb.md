# Traco prop-sem-w1 / r3-I2Fb

Fonte bruta: `runs/tlc/prop-sem-w1/r3-I2Fb.out` (3 estados, incluindo o inicial). Acoes: `Init>IncrementFailure>IncrementFailure`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** Auto-bloqueio por limite (LIMITE=2): a segunda IncrementFailure torna a identidade BLOCKED em r1 sem invalidar sessoes VALID da mesma replica. I2Fb (invariante de estado) violada sem falha. Mecanismo (a) do P-27; o codigo real faz o mesmo (IdentityState.incrementar nao invalida sessoes).

```
State 1 <Init>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:1 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 2 <IncrementFailure>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> ; r3:<< {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> )
   relogio      (r1:2 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <IncrementFailure>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@1 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:B@3 c2) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=B@3 c2 af={}} >> ; r3:<< {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=B@3 c2 af={}} >> )
   relogio      (r1:3 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
```
