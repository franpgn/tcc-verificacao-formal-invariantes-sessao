# Traco relogio-livre-particao-n1b / r2-I2Fb

Fonte bruta: `runs/tlc/relogio-livre-particao-n1b/r2-I2Fb.out` (4 estados, incluindo o inicial). Acoes: `Init>Login>IncrementFailure>IncrementFailure`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** Modelo livre: auto-bloqueio por limite (mecanismo (a) do P-27). Independente de D-4.

```
State 1 <Init>
   sessoes      ( r1:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) )
   identidades  ( r1:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        (r1:<<>> ; r2:<<>>)
   relogio      (r1:0 ; r2:0)
   ladoDe       (r1:1 ; r2:1)
State 2 <Login>
   sessoes      ( r1:( s1:[ ts |-> 1, VALID, m=NONE, id=u1 ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) )
   identidades  ( r1:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<<>> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:1 ; r2:0)
   ladoDe       (r1:1 ; r2:1)
State 3 <IncrementFailure>
   sessoes      ( r1:( s1:[ ts |-> 1, VALID, m=NONE, id=u1 ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) )
   identidades  ( r1:(u1:[ts |-> 2, status |-> OPEN, contador |-> 1]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<<>> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 0, NONE, m=NONE, id=NONE ], ident |-> [ts |-> 2, status |-> OPEN, contador |-> 1], tipo |-> CONTADOR_INC, chave |-> u1, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:2 ; r2:0)
   ladoDe       (r1:1 ; r2:1)
State 4 <IncrementFailure>
   sessoes      ( r1:( s1:[ ts |-> 1, VALID, m=NONE, id=u1 ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) )
   identidades  ( r1:(u1:[ts |-> 3, status |-> BLOCKED, contador |-> 2]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<<>> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 0, NONE, m=NONE, id=NONE ], ident |-> [ts |-> 2, status |-> OPEN, contador |-> 1], tipo |-> CONTADOR_INC, chave |-> u1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 0, NONE, m=NONE, id=NONE ], ident |-> [ts |-> 3, status |-> BLOCKED, contador |-> 2], tipo |-> CONTADOR_INC, chave |-> u1, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:3 ; r2:0)
   ladoDe       (r1:1 ; r2:1)
```
