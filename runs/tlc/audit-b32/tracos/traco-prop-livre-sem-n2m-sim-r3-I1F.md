# Traco prop-livre-sem-n2m-sim / r3-I1F

Fonte bruta: `runs/tlc/prop-livre-sem-n2m-sim/r3-I1F.out` (10 estados, incluindo o inicial). Acoes: `Init>Login>IncrementFailure>EntregaMensagem>EntregaMensagem>EntregaMensagem>Login>Login>Logout>EntregaMensagem`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** (sem texto pre-definido para esta sequencia)

```
State 1 <Init>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 2 <Login>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <IncrementFailure>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> )
   relogio      (r1:2 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <EntregaMensagem>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@2 c1) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> )
   relogio      (r1:2 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 5 <EntregaMensagem>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@2 c1) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {CONTADOR_INC u1 sess=N/-@0 id=O@2 c1 af={}} >> )
   relogio      (r1:2 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 6 <EntregaMensagem>
   sessoes      ( r1:( s1:N/-@0 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@2 c1) ; r3:(u1:O@2 c1) )
   canal        ( r1:<<>> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 7 <Login>
   sessoes      ( r1:( s1:V/-@3 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@2 c1) ; r3:(u1:O@2 c1) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@3 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s1 sess=V/-@3 id=O@0 c0 af={}} >> )
   relogio      (r1:3 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 8 <Login>
   sessoes      ( r1:( s1:V/-@3 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@2 c1) ; r3:(u1:O@2 c1) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@3 id=O@0 c0 af={}}, {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s1 sess=V/-@3 id=O@0 c0 af={}} >> )
   relogio      (r1:3 ; r2:0 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 9 <Logout>
   sessoes      ( r1:( s1:V/-@3 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:I/LO@2 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@2 c1) ; r3:(u1:O@2 c1) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@3 id=O@0 c0 af={}}, {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s1 sess=V/-@3 id=O@0 c0 af={}} >> )
   relogio      (r1:3 ; r2:0 ; r3:2)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 10 <EntregaMensagem>
   sessoes      ( r1:( s1:V/-@3 ; s2:V/-@1 ) ; r2:( s1:N/-@0 ; s2:V/-@1 ) ; r3:( s1:V/-@3 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@2 c1) ; r2:(u1:O@2 c1) ; r3:(u1:O@2 c1) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@3 id=O@0 c0 af={}}, {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s2 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:3 ; r2:0 ; r3:2)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
```
