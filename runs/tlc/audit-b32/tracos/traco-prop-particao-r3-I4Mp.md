# Traco prop-particao / r3-I4Mp

Fonte bruta: `runs/tlc/prop-particao/r3-I4Mp.out` (5 estados, incluindo o inicial). Acoes: `Init>Logout>Block>Particionar>Mesclar`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** Logout(s1) em r1 e Block em r2 concorrentes; Particionar; Mesclar com rf=r2 troca em r1 o motivo LOGOUT por ADMIN_BLOCK mantendo INVALID. I4Mp violada no passo Mesclar; a pre-condicao (dois motivos concorrentes) existe sem falha (ver I4Fp).

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
State 3 <Block>
   sessoes      ( r1:( s1:I/LO@2 ; s2:V/-@1 ) ; r2:( s1:I/AB@2 ; s2:I/AB@2 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:B@2 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:2 ; r2:2 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <Particionar>
   sessoes      ( r1:( s1:I/LO@2 ; s2:V/-@1 ) ; r2:( s1:I/AB@2 ; s2:I/AB@2 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:O@1 c0) ; r2:(u1:B@2 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:2 ; r2:2 ; r3:1)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
   particaoAtiva TRUE
State 5 <Mesclar>
   sessoes      ( r1:( s1:I/AB@2 ; s2:I/AB@2 ) ; r2:( s1:I/AB@2 ; s2:I/AB@2 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:B@2 c0) ; r2:(u1:B@2 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<< {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@2 id=B@2 c0 af={s1, s2}} >> )
   relogio      (r1:2 ; r2:2 ; r3:1)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
```
