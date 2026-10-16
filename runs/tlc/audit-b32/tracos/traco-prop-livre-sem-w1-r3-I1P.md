# Traco prop-livre-sem-w1 / r3-I1P

Fonte bruta: `runs/tlc/prop-livre-sem-w1/r3-I1P.out` (4 estados, incluindo o inicial). Acoes: `Init>Login>EntregaMensagem>Logout`.

**Depende do artefato D-4 (P-26)?** SIM. Passo local rejeitado pelo gate de ts: estado 4 (Logout).

**Leitura:** ARTEFATO D-4: Login em r1 (ts 1), entrega a r2 (relogio[r2] continua 0), Logout em r2 calcula novoTS = 1, nao supera ts local (1): escrita local rejeitada mas mensagem transmitida e descartada pelas demais. I1P violada sem falha.

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
State 3 <EntregaMensagem>
   sessoes      ( r1:( s1:V/-@1 ; s2:N/-@0 ) ; r2:( s1:V/-@1 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <Logout>
   sessoes      ( r1:( s1:V/-@1 ; s2:N/-@0 ) ; r2:( s1:V/-@1 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<< {SESSAO_INVALIDA s1 sess=I/LO@1 id=O@0 c0 af={}} >> ; r2:<<>> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}}, {SESSAO_INVALIDA s1 sess=I/LO@1 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:1 ; r3:0)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
```
