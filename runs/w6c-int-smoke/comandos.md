Integracao do W-6c (06/11/2026, pedido do coordenador), na ponta 494ccce de feature/controlador-integracao (merge do W-6c
sobre int/2026-11-05-w6). Suites rodadas depois que o host saiu do silencio (05:00:43Z), uma vez cada; a do orquestrador
sem outra suite em paralelo. Sem VM e sem cluster.

1. --dry-run do orquestrador com F3 e F4 no braco B (05:05Z, rc 0): comando exato em dry-run/comando.out, SHA-256 das
   entradas em dry-run/entradas.sha256, saida em dry-run/dry-run.out.
       matriz:     python3 -I scripts/campanha_s3_matriz.py gerar --modo avaliacao --nucleo "ASYNC-none:descritiva:1" \
                     --extras "ASYNC-F4:teste:1,ASYNC-F3:descritiva:1" --ordem-seed 20261106 --s0 400000 \
                     --passo-semente 100 --i0 0 --b0 1 --saida runs/w6c-int-smoke/dry-run/matriz.tsv
                   (fase extra: F4 A e B com a semente 400200, F3 A e B com a semente 400300; o pareamento exige A e B
                   por bloco, entao nao ha matriz so com o braco B)
       parametros: dry-run/parametros-campanha-s3-2.json (esquema campanha-s3/2): os 19 valores do pre-registro da
                   calibracao 4.1 (branch docs/pre-registro-calibracao-s3, 2c8b7b8) e os cinco do F3 do B-40 4.3
                   (branch docs/b40-pre-registro-avaliacao, bae665d), provisorios para este teste
       controlador: cluster/controlador/controlador-calibracao-s3.json
       referencias provisorias: runs/w2b-smoke/config/referencia-S3-async.json e runs/b44-2/integracao/referencia-S3-sync.json
       opcao: --swap-limite-kb 1048576

2. Saida da suite: suite-s3.out (run-campanha-s3.test.sh completo, S1..S29, 05:12:42Z a 06:55:08Z: 431 PASS, 0 FAIL;
   nenhum caso precisou de rodada isolada).
