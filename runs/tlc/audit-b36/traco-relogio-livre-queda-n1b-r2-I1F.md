# Traco relogio-livre-queda-n1b / r2-I1F

Fonte bruta: `runs/tlc/relogio-livre-queda-n1b/r2-I1F.out` (7 estados, incluindo o inicial). Acoes: `Init>Login>Login>Logout>Login>Logout>EntregaMensagemMax`.

**Depende do artefato D-4 (P-26)?** NAO. Passo local rejeitado pelo gate de ts: nenhum.

**Leitura:** (sem texto pre-definido para esta sequencia)

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
State 3 <Login>
   sessoes      ( r1:( s1:[ ts |-> 1, VALID, m=NONE, id=u1 ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 1, VALID, m=NONE, id=u1 ] ) )
   identidades  ( r1:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s2, sessoesAfetadas |-> {} ] >> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:1 ; r2:1)
   ladoDe       (r1:1 ; r2:1)
State 4 <Logout>
   sessoes      ( r1:( s1:[ ts |-> 2, INVALID, m=LOGOUT, id=u1 ] ; s2:[ ts |-> 0, NONE, m=NONE, id=NONE ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 1, VALID, m=NONE, id=u1 ] ) )
   identidades  ( r1:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s2, sessoesAfetadas |-> {} ] >> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 2, INVALID, m=LOGOUT, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_INVALIDA, chave |-> s1, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:2 ; r2:1)
   ladoDe       (r1:1 ; r2:1)
State 5 <Login>
   sessoes      ( r1:( s1:[ ts |-> 2, INVALID, m=LOGOUT, id=u1 ] ; s2:[ ts |-> 3, VALID, m=NONE, id=u1 ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 1, VALID, m=NONE, id=u1 ] ) )
   identidades  ( r1:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s2, sessoesAfetadas |-> {} ] >> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 2, INVALID, m=LOGOUT, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_INVALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 3, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s2, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:3 ; r2:1)
   ladoDe       (r1:1 ; r2:1)
State 6 <Logout>
   sessoes      ( r1:( s1:[ ts |-> 2, INVALID, m=LOGOUT, id=u1 ] ; s2:[ ts |-> 3, VALID, m=NONE, id=u1 ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 2, INVALID, m=LOGOUT, id=u1 ] ) )
   identidades  ( r1:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s2, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 2, INVALID, m=LOGOUT, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_INVALIDA, chave |-> s2, sessoesAfetadas |-> {} ] >> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 2, INVALID, m=LOGOUT, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_INVALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 3, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s2, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:3 ; r2:2)
   ladoDe       (r1:1 ; r2:1)
State 7 <EntregaMensagemMax>
   sessoes      ( r1:( s1:[ ts |-> 2, INVALID, m=LOGOUT, id=u1 ] ; s2:[ ts |-> 3, VALID, m=NONE, id=u1 ] ) ; r2:( s1:[ ts |-> 0, NONE, m=NONE, id=NONE ] ; s2:[ ts |-> 3, VALID, m=NONE, id=u1 ] ) )
   identidades  ( r1:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) ; r2:(u1:[ts |-> 0, status |-> OPEN, contador |-> 0]) )
   canal        ( r1:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s2, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 2, INVALID, m=LOGOUT, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_INVALIDA, chave |-> s2, sessoesAfetadas |-> {} ] >> ; r2:<< [ sessao |-> [ ts |-> 1, VALID, m=NONE, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_VALIDA, chave |-> s1, sessoesAfetadas |-> {} ], [ sessao |-> [ ts |-> 2, INVALID, m=LOGOUT, id=u1 ], ident |-> [ts |-> 0, status |-> OPEN, contador |-> 0], tipo |-> SESSAO_INVALIDA, chave |-> s1, sessoesAfetadas |-> {} ] >> )
   relogio      (r1:3 ; r2:3)
   ladoDe       (r1:1 ; r2:1)
```
