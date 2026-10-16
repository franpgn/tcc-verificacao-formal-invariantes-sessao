# Traco prop-particao-lim6-w1 / r3-I2Fb

Fonte bruta: `runs/tlc/prop-particao-lim6-w1/r3-I2Fb.out` (4 estados, incluindo o inicial). Acoes: `Init>Logout>Block>EntregaMensagem`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** LIMITE=6 (auto-bloqueio inalcancavel). Logout(s1) e Block em r1; sessoesAfetadas do Block = {s2} (s1 ja INVALID em r1). ID_BLOQUEIA entregue a r2 antes do Logout(s1): r2 fica BLOCKED com s1 VALID. Mecanismo (b) do P-27: cascata calculada na origem aplicada a replica que ainda ve a sessao VALID (artefato do modelo de copias locais).

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
   sessoes      ( r1:( s1:I/LO@2 ; s2:I/AB@3 ) ; r2:( s1:V/-@1 ; s2:V/-@1 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:B@3 c0) ; r2:(u1:O@1 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@3 id=B@3 c0 af={s2}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@3 id=B@3 c0 af={s2}} >> )
   relogio      (r1:3 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
State 4 <EntregaMensagem>
   sessoes      ( r1:( s1:I/LO@2 ; s2:I/AB@3 ) ; r2:( s1:V/-@1 ; s2:I/AB@3 ) ; r3:( s1:V/-@1 ; s2:V/-@1 ) )
   identidades  ( r1:(u1:B@3 c0) ; r2:(u1:B@3 c0) ; r3:(u1:O@1 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_INVALIDA s1 sess=I/LO@2 id=O@0 c0 af={}}, {ID_BLOQUEIA u1 sess=I/AB@3 id=B@3 c0 af={s2}} >> )
   relogio      (r1:3 ; r2:1 ; r3:1)
   ladoDe       (r1:1 ; r2:1 ; r3:1)
```
