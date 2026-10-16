# Traco prop-livre-queda-w1 / r3-I4F

Fonte bruta: `runs/tlc/prop-livre-queda-w1/r3-I4F.out` (7 estados, incluindo o inicial). Acoes: `Init>Login>Login>EntregaMensagem>Logout>EntregaMensagem>EntregaMensagem`.

**Depende do artefato D-4 (P-26)?** SIM. Passo local rejeitado pelo gate de ts: estado 5 (Logout).

**Leitura:** ARTEFATO D-4 (reordenacao): Login(s2) em r1 ts 2 entregue a r2 sem atualizar relogio[r2]; Logout(s2) em r2 sai com ts 1 (rejeitado local), r3 recebe o Logout (ts 1) antes do Login (ts 2) e o Login ressuscita a sessao. I1F/I4F violadas sem falha no modelo livre.

```
State 1 <Init>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        (r1:<<>> ; r2:<<>> ; r3:<<>>)
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 2 <Login>
   sessoes      ( r1:( s1:V/-@1 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 3 <Login>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@2 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@2 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <EntregaMensagem>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@2 ) ; r2:( s1:N/-@0 ; s2:V/-@2 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@2 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 5 <Logout>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@2 ) ; r2:( s1:N/-@0 ; s2:V/-@2 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_INVALIDA s2 sess=I/LO@1 id=O@0 c0 af={}} >> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@2 id=O@0 c0 af={}}, {SESSAO_INVALIDA s2 sess=I/LO@1 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:1 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 6 <EntregaMensagem>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@2 ) ; r2:( s1:N/-@0 ; s2:V/-@2 ) ; r3:( s1:N/-@0 ; s2:I/LO@1 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_INVALIDA s2 sess=I/LO@1 id=O@0 c0 af={}} >> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_VALIDA s2 sess=V/-@2 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:1 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 7 <EntregaMensagem>
   sessoes      ( r1:( s1:V/-@1 ; s2:V/-@2 ) ; r2:( s1:N/-@0 ; s2:V/-@2 ) ; r3:( s1:N/-@0 ; s2:V/-@2 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_INVALIDA s2 sess=I/LO@1 id=O@0 c0 af={}} >> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:2 ; r2:1 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
```
