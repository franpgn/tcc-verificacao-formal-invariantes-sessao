# Traco ctrl-nosym-livre-particao / r3-MesclarPerdeChave

Fonte bruta: `runs/tlc/ctrl-nosym-livre-particao/r3-MesclarPerdeChave.out` (4 estados, incluindo o inicial). Acoes: `Init>Login>Particionar>Mesclar`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** MesclarPerdeChave (modelo): Login em r1 ainda nao propagado, Particionar, Mesclar com rf sem a chave: r1 passa de VALID a NONE. Evidencia sobre o modelo, nada sobre o Infinispan real.

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
State 3 <Particionar>
   sessoes      ( r1:( s1:V/-@1 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:1 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
   particaoAtiva TRUE
State 4 <Mesclar>
   sessoes      ( r1:( s1:N/-@0 ; s2:N/-@0 ) ; r2:( s1:N/-@0 ; s2:N/-@0 ) ; r3:( s1:N/-@0 ; s2:N/-@0 ) )
   identidades  ( r1:(u1:O@0 c0) ; r2:(u1:O@0 c0) ; r3:(u1:O@0 c0) )
   canal        ( r1:<<>> ; r2:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> ; r3:<< {SESSAO_VALIDA s1 sess=V/-@1 id=O@0 c0 af={}} >> )
   relogio      (r1:0 ; r2:0 ; r3:0)
   ladoDe       (r1:1 ; r2:2 ; r3:2)
```
