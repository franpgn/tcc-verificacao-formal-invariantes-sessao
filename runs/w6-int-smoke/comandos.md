Smoke da integracao do W-6 (05/11/2026, pedido do coordenador, item 5), na ponta fdcf0ae de feature/controlador-integracao
(merge 49baddc do W-6 + I-1). Maquina Podman do WSL (podman-machine-default), sem VM. Harness no estilo de runs/w2b-smoke/.

1. Criacao dos caches (06/11 00:11:05Z a 00:12:32Z, rc 0):
       bash runs/w6-int-smoke/scripts/smoke-caches.sh > runs/w6-int-smoke/caches.lote.out 2>&1
   Cluster local w6i (portas 12801-12803): cluster/podman-compose.w6i.yml = cluster/podman-compose-sem-caches.yml da ponta
   com nomes w6i-*, rede w6i-net e portas proprias; cluster/infinispan-sem-caches.xml e jgroups-tcp.xml copiados da ponta
   (SHA-256 iguais, na fase "inicio" de caches/fases.tsv). scripts/cria-caches.sh da ponta com os seis JSON e --conferir nos
   tres nos; depois, releitura no formato da camada 3 do orquestrador (caches/camada3.tsv) e lista de caches de cada no
   antes e depois. O cluster e descido no fim.

2. --dry-run do orquestrador (06/11 00:02:30Z, rc 0): comando exato em dry-run/comando.out, SHA-256 das entradas em
   dry-run/entradas.sha256, saida em dry-run/dry-run.out.
       matriz:     python3 -I scripts/campanha_s3_matriz.py gerar --modo avaliacao --nucleo "ASYNC-F2:teste:1" \
                     --ordem-seed 20261105 --s0 400000 --passo-semente 100 --i0 0 --b0 1 --saida runs/w6-int-smoke/dry-run/matriz.tsv
                   (i=0 braco B active, i=1 braco A shadow, semente 400100)
       parametros: dry-run/parametros-campanha-s3.json, os 19 valores do pre-registro da calibracao 4.1
                   (branch docs/pre-registro-calibracao-s3, 2c8b7b8), provisorios para este teste
       controlador: cluster/controlador/controlador-calibracao-s3.json
       referencias provisorias: runs/w2b-smoke/config/referencia-S3-async.json (a do smoke do W-2b, copia da do I-3b) e
                   runs/b44-2/integracao/referencia-S3-sync.json (integracao do W-2a; o smoke do W-2b e o I-3b so tinham a ASYNC)

3. Registros do I-1 (S26 do run-campanha-s3.test.sh): i1-s26-vermelho.out (sobre 49baddc, antes da correcao),
   i1-s6-s18-s26-depois-do-fix.out (com a correcao, antes da correcao da asserção do podman kill, 70996e4),
   suite-s3-completa-sob-carga.out (suite completa S1..S26 na ponta, com outras suites de outras sessoes rodando no host)
   e s5-s22-isolado.out (S5 e S22 de novo, sozinhos).

O "podman machine ssh" pode gravar um arquivo NUL no diretorio corrente; nenhum foi criado neste smoke.

Fora do git (.gitignore: runs/**/*.txt e *.log), mas no MANIFEST.sha256 e na worktree: caches/compose-*.txt e
caches/servidores/isn*.log (logs dos tres servidores).
