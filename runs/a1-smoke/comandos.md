Smoke do A1 (06/11/2026): politica de fusao propria do sessions-async (br.unipampa.tcc.servidor.PoliticaFusaoSessoes)
contra PREFERRED_NON_NULL numa particao F2 do isn3, no cluster local a1 (portas 12801-12803, projeto Podman proprio a1,
rede a1-net), maquina Podman do WSL (podman-machine-default), sem VM. Harness no estilo de runs/w10-smoke/ e
runs/w2b-smoke/. Ao mesmo tempo, o Worker da A4 usava o cluster a4 (portas 12901-12903) na mesma maquina; memoria livre
conferida antes (13,5 GB disponiveis de 15,9 GB).

Codigo: ponta 04719ee de feature/merge-policy-semirreticulado (base int/2026-11-06-bench = cc2b71c).
  - JAR da extensao: mvn -f extensao-servidor/pom.xml package; copia congelada em cluster/tcc-extensao-servidor.jar
    (sha256 6f1ed746f7142bed8ed2596eacfa1f8db9cc335bb448b40c72efbc37d49253b4, tambem na linha "inicio" de fases.tsv).
    Montado nas DUAS condicoes.
  - programa de carga: o jar do bundle da recalibracao (dados-vm/cs3-2026-11/bundle-rc-cc2b71c/,
    sha256 7d5fc2fee76e84606384fe96dafa1e75ed8507baf83396dc00361fdc6d224707, o mesmo do execucao.json da
    recalibracao); o codigo principal do workload nao mudou nesta branch (git diff cc2b71c -- workload/src/main vazio).
  - cluster/podman-compose.a1.yml = cluster/podman-compose-sem-caches.yml da ponta com nomes a1-*, rede a1-net, portas
    12801-12803 e o JAR por ./tcc-extensao-servidor.jar; infinispan-sem-caches.xml e jgroups-tcp.xml copiados da ponta
    (SHA-256 iguais, na linha "inicio" de fases.tsv).
  - caches-a1/ = cluster/caches da ponta (sessions-async com a classe da A1); caches-controle/ = cluster/caches da tag
    int/2026-11-06-bench (PREFERRED_NON_NULL). So o sessions-async.json difere entre os dois diretorios.
    Os seis caches pelo scripts/cria-caches.sh da ponta com --dir e --conferir (leitura de volta byte a byte nos tres
    nos); config-sessions-async-isnN.json e config-counters-async-isnN.json guardam a leitura de cada no.

Execucoes com carga (bash runs/a1-smoke/scripts/a1.sh NOME CONDICAO SEMENTE JAR; saida do harness em NOME.lote.out):
  preparo-a1-s700400-d300  a1        700400  --duration 300 (fora da comparacao; ver abaixo)
  a1-s700400               a1        700400  --duration 480
  controle-s700400         controle  700400  --duration 480
  a1-s701300               a1        701300  --duration 480
  controle-s701300         controle  701300  --duration 480
  Sementes das execucoes ASYNC-F2 i=4 e i=13 da recalibracao (X-19). Carga S3 ASYNC L+ (DT-026): --s3-users 40
  --s3-think-ms 500 --s3-session-median-sec 10 --s3-logout-prob 0.9 --s3-admin-mean-sec 5 --s3-replay-late-max 268
  --s3-replay-probe --sweep; --warmup-min-sec 30, --w-recovery-sec 191, --convergence (teto 600 s, periodo 5 s),
  --hotrod-timeout-ms 2000, como no execucao.json da recalibracao. Diferencas declaradas: --controller off (a
  recalibracao usou shadow, que nao age), as portas locais e --duration 480 em vez de 300 (o programa escala o
  warm-up: medicao a partir de +70 s; corte em +80 s). Comando exato em NOME/comando.txt.
  Corte do isn3 por nftables no netns (scripts/particao-a1.sh, copia do particao-w10.sh; a maquina WSL nao tem tc),
  10 s depois do inicio da medicao, por 90 s; marcadores no formato do injetor (start antes do nft, end depois da
  cura). corte.txt e cura.txt trazem o relogio da maquina do Podman em volta do nft (nft-aplicado, nft-removido).

Por que 480 s (preparo-a1-s700400-d300): com 300 s, a cura levou 38 s no "podman machine ssh" (maquina dividida com
o Worker da A4) e a fusao nao aconteceu antes da varredura (nenhum ISPN100012 do sessions-async nos logs). O laco de
convergencia so confere I5/I6 (contadores), nao a vista do cluster, entao a varredura rodou com o isn3 ainda fora.
Nas quatro execucoes de 480 s a CR de fusao do sessions-async aconteceu 38 a 49 s depois da cura, bem antes do fim
da carga (analise.out, campo janela).

Teste dirigido do mecanismo (bash runs/a1-smoke/scripts/mecanismo.sh NOME CONDICAO JAR [NSESS=60] [CORTE=90]):
  mecanismo-a1                     a1        60 sessoes
  mecanismo-controle-falha-infra   controle  abortou na criacao dos caches (ISPN000476 no canario, 15 s sem
                                             resposta do isn2, enquanto o cluster a4 reiniciava); nada medido
  mecanismo-controle-r1            controle  60 sessoes (repeticao do anterior)
  Cria NSESS sessoes VALID pelo isn1, espera 5 s, corta o isn3 e faz o logout de cada uma PELO isn3 com a mesma
  sequencia do programa de carga (getWithMetadata + replaceWithVersion; o isn3 so confirma as de que e dono
  primario), cura depois de 90 s, espera o ISPN100013 do sessions-async e le as confirmadas pelos tres nos.
  Programa: scripts/MecanismoA1.java, com o jar do programa de carga no classpath (mesmo SessionState, cliente 15.0.0 e
  configuracao de S3Clientes.base).

Formato real do valor: scripts/LerFormato.java desserializa, com as classes e o marshaller do cliente, o hexadecimal
que a politica registrou no servidor (linha A1-FORMATO); saida em formato-decodificado.out.

Analise: python -I runs/a1-smoke/scripts/analisar_a1.py runs/a1-smoke/<execucoes> --json runs/a1-smoke/analise.json
         > runs/a1-smoke/analise.out

O "podman machine ssh" grava um arquivo NUL (known_hosts) no diretorio corrente; o harness roda dentro do diretorio da
execucao e o apaga no fim. Fora do git (.gitignore: runs/**/*.csv, *.txt, *.log, *.jar), mas no MANIFEST.sha256: os
CSV, o stdout/stderr, os logs dos servidores, os .txt do harness e o JAR da extensao.
linhas-politica.out: todas as linhas A1-* e ISPN100012/13 do sessions-async dos logs dos servidores (os .log ficam
fora do git).
