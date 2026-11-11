--------------------------- MODULE MCSessionJoin ---------------------------
(***************************************************************************)
(* Variante MesclarJoin: fusao das SESSOES pelo maximo de conteudo.        *)
(*                                                                         *)
(* Nao altera SessionStore.tla nem MCSession.tla (modelo publicado): so os *)
(* estende. A unica diferenca de comportamento e' a acao de fusao.         *)
(*                                                                         *)
(* Origem: docs/alternativas-tratamento-cluster-2026-11-06.md (branch      *)
(* docs/plano-implementacao-controlador), alternativa A1: politica de      *)
(* fusao propria no cache de sessoes do Infinispan, em que a resolucao de  *)
(* conflitos devolve o MAXIMO das copias numa ordem de conteudo, em vez da *)
(* copia do lado preferido (PREFERRED_NON_NULL). Os postos sao os do T2 do *)
(* B-45 (RankEstado, RankMotivo, RankIdent em SessionStore.tla), mas aqui  *)
(* o CONTEUDO vem antes da versao:                                         *)
(*                                                                         *)
(*   ChaveFusao(x) = << estado, motivo, ts, identidade >>                  *)
(*     estado: NONE 0 < VALID 1 < INVALID 2                                *)
(*     motivo: NONE 0 < LOGOUT 1 < EXPIRADA 2 < ADMIN_BLOCK 3              *)
(*                                                                         *)
(* A tupla contem todos os campos de SessaoRec, entao a ordem e' total e   *)
(* o maximo de um conjunto de registros e' unico (MaxFusao).               *)
(*                                                                         *)
(* O que MesclarJoin faz (comparar com Mesclar, SessionStore.tla:577-586): *)
(*   - sessoes: para cada sid, o maximo das copias de TODAS as replicas    *)
(*     (os dois lados), escrito em TODAS as replicas. E' o que a resolucao *)
(*     de conflitos do Infinispan faz: le as copias de todos os donos (o   *)
(*     hash da CR e' a uniao dos hashes das particoes) e escreve o valor   *)
(*     devolvido pela politica em todos os donos (DefaultConflictManager,  *)
(*     linhas 296-350 do 15.0.21). A copia preferida e' ignorada.          *)
(*   - identidades: copia do registro inteiro de `rf` para o lado perdedor,*)
(*     como em Mesclar (o cache de contadores fica com PREFERRED_NON_NULL  *)
(*     na primeira rodada; decisao D-2 em aberto).                         *)
(*   - relogio: o perdedor fica com o MAIOR entre o seu e o de `rf`. Em    *)
(*     Mesclar ele copia o de `rf`, porque todos os registros do perdedor  *)
(*     vinham de `rf`. Com a juncao o perdedor pode manter um registro     *)
(*     proprio com `ts` acima de relogio[rf]; copiar relogio[rf] faria o   *)
(*     relogio voltar para baixo de um registro que a replica ainda guarda,*)
(*     exatamente o que o comentario de Mesclar quer evitar. O vencedor    *)
(*     mantem o seu relogio, como em Mesclar (o registro fundido pode ter  *)
(*     `ts` acima dele, a mesma situacao de EntregaMensagem, decisao D-4). *)
(*   - particaoAtiva volta a FALSE; canal, vivo, quedas, ladoDe, particoes *)
(*     ficam como estao, igual a Mesclar.                                  *)
(*                                                                         *)
(* Parametro OrdemFusao:                                                   *)
(*   "conteudo"  = INVALID > VALID (a politica proposta);                  *)
(*   "invertida" = VALID > INVALID (controle negativo: tem de violar I1,   *)
(*                 o que mostra que as propriedades discriminam a ordem).  *)
(*                                                                         *)
(* NextJoin repete os disjuntos de Next (SessionStore.tla:649-660) com     *)
(* Mesclar trocado por MesclarJoin. spec/test-mcsessionjoin.sh confere que *)
(* as duas listas continuam iguais fora dessa troca.                       *)
(*                                                                         *)
(* Propriedades: I1F e I4Fp/CorpoI4F de MCSession valem como estao (nao    *)
(* citam a acao de fusao). As que restringem so' passos de fusao foram     *)
(* reescritas sobre MesclarJoin (sufixo J): com Mesclar no antecedente,    *)
(* nenhum passo de NextJoin satisfaria o antecedente e a propriedade seria *)
(* vazia.                                                                  *)
(***************************************************************************)
EXTENDS MCSession

CONSTANT OrdemFusao   \* "conteudo" | "invertida"

ASSUME OrdemFusao \in {"conteudo", "invertida"}

RankEstadoFusao(e) ==
  IF OrdemFusao = "conteudo"
    THEN RankEstado(e)
    ELSE CASE e = "NONE" -> 0 [] e = "INVALID" -> 1 [] e = "VALID" -> 2

ChaveFusao(x) ==
  << RankEstadoFusao(x.estado), RankMotivo(x.motivo), x.ts, RankIdent(x.identidade) >>

\* "a vence b" na ordem da fusao (estrita)
VenceFusao(a, b) == LexMaior(ChaveFusao(a), ChaveFusao(b))

\* maximo de um conjunto nao vazio de SessaoRec (unico: a ordem e' total)
MaxFusao(S) == CHOOSE x \in S : \A y \in S : ~VenceFusao(y, x)

SessaoFundida(s) == MaxFusao({sessoes[r][s] : r \in REPLICAS})

Max2(a, b) == IF a >= b THEN a ELSE b

MesclarJoin ==
  /\ particaoAtiva
  /\ \E ladoVencedor \in Vencedor(Lado(1), Lado(2)) :
     \E rf \in ladoVencedor :
       LET perdedores == REPLICAS \ ladoVencedor IN
       /\ sessoes'     = [r \in REPLICAS |-> [s \in SIDS |-> SessaoFundida(s)]]
       /\ identidades' = [r \in REPLICAS |-> IF r \in perdedores THEN identidades[rf] ELSE identidades[r]]
       /\ relogio'     = [r \in REPLICAS |-> IF r \in perdedores THEN Max2(relogio[r], relogio[rf])
                                                                ELSE relogio[r]]
       /\ particaoAtiva' = FALSE
       /\ UNCHANGED <<canal, vivo, quedas, ladoDe, particoes>>

NextJoin ==
  \/ \E r \in REPLICAS, id \in IDS, s \in SIDS: Login(r, id, s)
  \/ \E r \in REPLICAS, s \in SIDS:             Logout(r, s)
  \/ \E r \in REPLICAS, id \in IDS:             IncrementFailure(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             ResetFailures(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Block(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Unblock(r, id)
  \/ \E r \in REPLICAS:                          EntregaMensagem(r)
  \/ \E r \in REPLICAS:                          Cair(r)
  \/ \E r \in REPLICAS:                          Reviver(r)
  \/ Particionar
  \/ MesclarJoin

SpecJoin        == Init        /\ [][NextJoin]_vars
SpecSemeadaJoin == InitSemeado /\ [][NextJoin]_vars

(***************************************************************************)
(* Propriedades de fusao sobre MesclarJoin (mesmos corpos de MCSession).   *)
(***************************************************************************)
I1MJ  == [][MesclarJoin => CorpoI1F]_vars
I4MJ  == [][MesclarJoin => CorpoI4F]_vars
I4MpJ == [][MesclarJoin => CorpoI4Fp]_vars

CorpoI2FaJ ==
  MesclarJoin => \A r \in REPLICAS, id \in IDS :
                   identidades[r][id].status = "BLOCKED" => identidades'[r][id].status = "BLOCKED"
I2FaJ == [][CorpoI2FaJ]_vars

MesclarPerdeChaveJ ==
  [][MesclarJoin => \A r \in REPLICAS, s \in SIDS :
        sessoes[r][s].estado # "NONE" => sessoes'[r][s].estado # "NONE"]_vars

=============================================================================
