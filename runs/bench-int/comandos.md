Integracao do lote do benchmark (06/11/2026): W-10, P-48, W-9, carga S3 parametrizada, W-5c/W-5d, fonte unica da
apuracao de V e o D-1 do roteiro da recalibracao, em feature/controlador-integracao. Sem VM e sem cluster.

1. Subconjunto do orquestrador (pedido do coordenador; a suite completa roda depois da recalibracao e antes da
   avaliacao), na ponta acd079d, sozinho no host, 10:23:04Z a 11:20:11Z:
       bash scripts/run-campanha-s3.test.sh S1 S4 S11 S22 S24 S25 S26 S27 S29 S30 S31 S32
   saida em suite-s3-subconjunto.out: 338 PASS, 0 FAIL.
2. D-1: s32-vermelho.out (antes do feat: 15 PASS, 11 FAIL) e s32-verde.out (26 PASS, 0 FAIL).
3. --dry-run do D-1 (dry-run/): matriz de calibracao com uma linha ASYNC-F2 (semente 700000), parametros
   parametros-recalibracao-d1.json (19 numeros do pre-registro da calibracao 4.1, os cinco s3_* da L+ do piloto e
   s3_replay_late_max 268 com a origem da DT-026; provisorio), --swap-limite-kb 1048576. Comandos em
   comando-com-sonda.out e comando-sem-sonda.out, saidas em dry-run-com-sonda.out (rc 0, --s3-replay-late-max 268
   na linha do programa) e dry-run-sem-sonda.out (rc 4, campo recusado sem --sonda-replay), SHA-256 das entradas em
   entradas.sha256. programa-dry-run.out: a linha do programa do plano com o jar construido da ponta e --dry-run do
   proprio programa (rc 0).
