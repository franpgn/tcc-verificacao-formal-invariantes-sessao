Smoke da correcao do P-48 (06/11/2026, 05:58 a 06:09Z), branch fix/p48-topologia-convergencia (ponta 3c57447 no
momento das rodadas). Maquina Podman do WSL, sem VM. Mesmo cluster local p48 do runs/p48-smoke (portas 12901-12903,
cluster/infinispan-sem-caches.xml e jgroups-tcp.xml da ponta, seis caches pelo scripts/cria-caches.sh da ponta).

    cd workload && mvn -o -q -DskipTests package                            (jar da correcao, sha256 31ef29ed...)
    bash runs/p48-fix-smoke/scripts/p48fix.sh > runs/p48-fix-smoke/p48fix.lote.out 2>&1         (partes A e B, rc 0)
    jar da 1f65a02 (git archive 1f65a02 workload; mvn -o -q -DskipTests package; sha256 3a7d1896..., fora do repositorio)
    bash runs/p48-fix-smoke/scripts/p48fix-c.sh <jar da 1f65a02> > runs/p48-fix-smoke/p48fix-c.lote.out 2>&1   (parte C, rc 0)

- Parte A (execucao/sonda.out): scripts/SondaP48Fix.java repete o smoke de antes com o jar da correcao: rcm principal
  com a configuracao do WorkloadMain, clear e 2000 chaves, nenhuma operacao durante um F1 de 60 s (podman kill/start do
  isn2). ANTES: leituras por replica com a topologia velha contra a de um cliente novo. CORRECAO:
  AtualizacaoTopologia.doPrincipal(rcm, caches, ...).atualizar(), a chamada do laco de convergencia. DEPOIS: as mesmas
  leituras com a topologia do principal atualizada.
- Parte B (execucao/programa/, execucao/b-linhas.txt): o programa de carga de verdade com o jar da correcao, S3 ASYNC
  de 90 s, --convergence (teto 120 s, periodo 5 s), sem controlador, F1 do isn2 de +25 a +55 s da ancora (marcadores
  start/end no formato do inject-crash.sh).
- Parte C (execucao-c/): o programa SEM a correcao (jar da 1f65a02) no mesmo cenario, com scripts/SondaDivergencia.java
  ao lado: cliente aberto antes do F1, sem operacao, e depois do programa a contagem de chaves divergentes (a
  comparacao do InvariantAuditor) nos mesmos dados com a topologia velha e com a atualizada.
- Fora do git (.gitignore runs/**/*.txt, *.log e *.csv), mas no MANIFEST.sha256: compose-*.txt, *-f1-*.txt, b-linhas.txt,
  linhas-programa.txt, stdout.log/stderr.log e os CSV do programa (rep-001, convergencia, violacoes) e servidores/isn*.log.

Resultado no P-48 de docs/registro-de-problemas.md e em docs/testes.md.
