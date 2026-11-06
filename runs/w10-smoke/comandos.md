Smoke do W-10 (06/11/2026): sonda de replay do S3 numa particao F2 do isn3, no cluster local w10 (portas 12901-12903,
projeto Podman proprio), maquina Podman do WSL (podman-machine-default), sem VM. Harness no estilo de runs/w2b-smoke/
e runs/w6-int-smoke/. Codigo na ponta 6307230 de feature/s3-sonda-replay (jar 19f30b45..., copiado para o scratchpad
antes da execucao para nao mudar sob os testes).

Comando (06/11 06:09:31Z a 06:14Z, rc 0):
    bash runs/w10-smoke/scripts/w10.sh f2-sonda 610600 <jar> 200 15 80 > runs/w10-smoke/f2-sonda.lote.out 2>&1
  - cluster/podman-compose.w10.yml = cluster/podman-compose-sem-caches.yml com nomes w10-*, rede w10-net e portas
    12901-12903; cluster/infinispan-sem-caches.xml e jgroups-tcp.xml copiados da ponta (SHA-256 iguais, na linha
    "inicio" de f2-sonda/fases.tsv); seis caches pelo scripts/cria-caches.sh da ponta com --conferir.
  - carga: f2-sonda/comando.txt (S3 ASYNC, 200 s, warm-up 30 s, --controller off, --convergence, --w-recovery-sec 60,
    30 usuarios, sessao mediana de 8 s, logout sempre, --s3-replay-probe --sweep). Parametros de smoke, nao os da
    calibracao: mais logouts na janela.
  - corte do isn3 por nftables no netns (scripts/particao-w10.sh, copia do particao-w2b.sh; a maquina WSL nao tem tc),
    15 s depois do inicio da medicao, por 80 s; marcadores gravados pelo harness no formato do injetor
    (start antes do nft, end depois da cura).

Analise: python -I runs/w10-smoke/scripts/analisar_w10.py runs/w10-smoke/f2-sonda > runs/w10-smoke/f2-sonda-analise.out

O "podman machine ssh" gravou um arquivo NUL (99 bytes, known_hosts) na raiz do worktree; foi removido, fora dos
manifestos. Fora do git (.gitignore: runs/**/*.csv, *.txt, *.log), mas no MANIFEST.sha256: os CSV, o stdout/stderr e
os logs dos servidores.
