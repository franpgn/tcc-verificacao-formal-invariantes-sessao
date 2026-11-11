# Traco prop-livre-queda-n2m-sim / r3-I1P

Fonte bruta: `runs/tlc/prop-livre-queda-n2m-sim/r3-I1P.out` (6 estados, incluindo o inicial). Acoes: `Init>Cair>Login>EntregaMensagem>IncrementFailure>Logout`.

**Depende do artefato D-4 (P-26)?** SIM. Passo local rejeitado pelo gate de ts: estado 6 (Logout).

**Leitura:** (sem texto pre-definido para esta sequencia)

```
State 1 <Init>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 2 <Cair>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <Login>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <EntregaMensagem>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 5 <IncrementFailure>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> )
   relogio      (r1:2 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 6 <Logout>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_INVALIDA s2 sess=I/LO@1 id=O@0 c0 af={}} >> ; r2:<< {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}}, {SESSAO_INVALIDA s2 sess=I/LO@1 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:1 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
```
