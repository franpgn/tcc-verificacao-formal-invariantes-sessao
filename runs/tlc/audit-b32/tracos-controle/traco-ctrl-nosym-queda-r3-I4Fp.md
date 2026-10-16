# Traco ctrl-nosym-queda / r3-I4Fp

Fonte bruta: `runs/tlc/ctrl-nosym-queda/r3-I4Fp.out` (5 estados, incluindo o inicial). Acoes: `Init>Logout>Logout>Block>EntregaMensagem`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** Logout(s1,s2) em r1 (ts 2,3) e Block em r2 (ts 2) sao concorrentes; r2 aplica o proprio Block (ADMIN_BLOCK ts 2) e depois recebe Logout(s2) ts 3 > 2: motivo ADMIN_BLOCK -> LOGOUT com a sessao permanecendo INVALID. I4F/I4Fp violadas por corrida de canal, sem falha.

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
State 3 <Logout>
   sessoes      ( r1:( s1:I/LO@2 ; s2:I/LO@3 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_INVALIDA s2 sess=I/LO@3 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_INVALIDA s2 sess=I/LO@3 id=O@0 c0 af={}} >> )
   relogio      (r1:3 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <Block>
   sessoes      ( r1:( s1:I/LO@2 ; s2:I/LO@3 ) ; r2:( s1:I/AB@2 ; s2:I/AB@2 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:B@2 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_INVALIDA s2 sess=I/LO@3 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_INVALIDA s2 sess=I/LO@3 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:3 ; r2:2 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 5 <EntregaMensagem>
   sessoes      ( r1:( s1:I/LO@2 ; s2:I/LO@3 ) ; r2:( s1:I/AB@2 ; s2:I/LO@3 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:B@2 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {SESSAO_INVALIDA s2 sess=I/LO@3 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:3 ; r2:2 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
```
