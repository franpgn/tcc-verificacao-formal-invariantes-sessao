# Traco prop-livre-particao-n2m-sim / r3-I4F

Fonte bruta: `runs/tlc/prop-livre-particao-n2m-sim/r3-I4F.out` (6 estados, incluindo o inicial). Acoes: `Init>Login>Logout>Login>Particionar>Mesclar`.

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
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:V/-@1 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:0 ; r2:1 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <Logout>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:I/LO@2 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> )
   relogio      (r1:0 ; r2:2 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <Login>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:I/LO@2 ; s2:V/-@3 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@3 id=O@0 c0 af={}} >> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@3 id=O@0 c0 af={}} >> )
   relogio      (r1:0 ; r2:3 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 5 <Particionar>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:I/LO@2 ; s2:V/-@3 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@3 id=O@0 c0 af={}} >> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@3 id=O@0 c0 af={}} >> )
   relogio      (r1:0 ; r2:3 ; r3:0)
   ladoDe       (r1:2 ; r2:1 ; r3:2)
   particaoAtiva TRUE
State 6 <Mesclar>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@3 id=O@0 c0 af={}} >> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@3 id=O@0 c0 af={}} >> )
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:2 ; r2:1 ; r3:2)
```
