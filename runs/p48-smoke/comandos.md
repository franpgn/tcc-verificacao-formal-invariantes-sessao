Smoke do P-48 (06/11/2026, 05:11 a 05:15Z, pedido do coordenador depois da calibracao X-18): topologia do rcm
principal do programa de carga no S3 antes e depois de um F1, e efeito nas leituras por replica do laco de convergencia.
Ponta do codigo: 1f65a02 (a da calibracao; worktree wt-p48 destacada nela durante o smoke). Maquina Podman do WSL
(podman-machine-default, rootful), sem VM. Nenhum codigo do programa mudou: a sonda e harness.

    cd workload && mvn -o -q -DskipTests package           (jar da 1f65a02, sha256 fc06395d..., nao reprodutivel byte a byte)
    bash runs/p48-smoke/scripts/p48.sh > runs/p48-smoke/p48.lote.out 2>&1   (rc 0)

- cluster/: podman-compose.p48.yml = cluster/podman-compose-sem-caches.yml da 1f65a02 com nomes p48-*, rede p48-net e
  portas 12901-12903 (via a copia do smoke do W-6); infinispan-sem-caches.xml e jgroups-tcp.xml com o SHA-256 da ponta
  (fase "inicio" de execucao/fases.tsv). Seis caches pelo scripts/cria-caches.sh da ponta, com --conferir nos tres nos.
- scripts/SondaP48.java: o rcm principal com a configuracao do WorkloadMain.executarRepeticao (todos os servidores,
  inteligencia padrao), clear e 2000 chaves em sessions-async e counters-async (o reset e a carga inicial do programa),
  e depois nenhuma operacao nele, como no S3 (a carga vai pelos clientes BASIC do S3Clientes). Le getCacheTopologyInfo()
  (T0 antes, T1 depois do F1, sem operacao), compara com um cliente novo (T2, a topologia de verdade), faz as leituras
  por replica do laco (um cliente BASIC por no, entrySet(segmentos do no)) com a topologia usada e a real, e confere se
  um get no rcm principal atualiza a topologia dele (T3). Depois, o mesmo para uma F2 sem reinicio (T4, T5).
- scripts/p48.sh: F1 = podman kill p48-isn2, 60 s, podman start (f1_dur_s = 60 da calibracao), saude de volta;
  F2 = isn3 isolado por nftables (scripts/particao-p48.sh, copia do particao-w2b.sh) por 40 s, curado, fusao.
- execucao/: fases.tsv, saude.tsv, sonda.out (resultado), sonda.err (log do cliente Hot Rod), cria-caches.tsv/.err;
  fora do git (.gitignore runs/**/*.txt e *.log), mas no MANIFEST.sha256: compose-*.txt, f1-*.txt, f2-*.txt (regras e
  contadores do nftables) e servidores/isn*.log.

Resultado no P-48 de docs/registro-de-problemas.md.
