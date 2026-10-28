# Relatório do Worker: F1 como *crash* silencioso (2026-10-28)

> Worker, 2026-10-28. Execução do prompt da seção 7 de `docs/prompt-f1-kill-2026-10-27.md` (branch
> `docs/plano-implementacao-controlador`), variante da **opção B**, com as decisões do Gestor de
> 2026-10-28: Q1 = B, Q2..Q6 = Sim. Branch `fix/f1-crash-silencioso`, *worktree* `../wt-f1-kill`, base
> `c055399`. Commits só locais, sem *push*, sem merge, sem VM, sem `tc`/`netem`, `workload/` e `cluster/`
> intocados, sem jar novo.

## 1. Commits

| SHA | Tipo | Conteúdo |
|---|---|---|
| `2a82d42` | `test:` | `scripts/inject-crash.test.sh` novo (C1..C15) e classe `A9bK6F1Silencioso` em `analysis/test_rebaseline.py` (só casos novos). Vermelhos |
| `0d955c0` | `test:` | edições autorizadas (Q2) em testes existentes; ajustes do `inject-crash.test.sh` novo (ordem do `kill`, casamento por prefixo, modo executável) |
| `0e83ec3` | `fix:` | `scripts/inject-crash.sh` (modos `silencioso`/`kill`/`stop`, guarda de *netns*, *trap* por fase, carimbo por linha, exit 5 no `start`) e `k6` de `analysis/rebaseline.py` |
| este | `docs:` | `docs/implementacao.md`, `docs/testes.md`, `README.md`, `docs/configuracao-infinispan.md`, este relatório e os brutos versionáveis do *smoke* |

Histórico linear sobre `c055399`, sem *amend*. `git diff --name-status c055399 HEAD -- workload/ cluster/`: vazio.

## 2. Vermelhos (TDD)

Contra o `inject-crash.sh` e o `rebaseline.py` de `c055399`:

- `bash scripts/inject-crash.test.sh` (versão final do arquivo, a de `0d955c0`): **56 PASS / 57 FAIL**
  (a versão de `2a82d42` também deu 56/57). Os 56 PASS são asserções que o injetor antigo cumpre por
  acaso (exit 4 de alvo ausente, exit 2 de `--duration 0`, "nenhum kill" quando não há kill etc.).
- `python -I analysis/test_rebaseline.py A9bK6F1Silencioso`: 6 testes, **3 falhas e 2 erros**
  (`KeyError: 'log_exit137'`; log completo recusado; log de junho e `stop` no lugar de `kill` aceitos).
- `bash scripts/run-rebaseline.test.sh R3 R4 R8` com as edições de `0d955c0`: **38 PASS / 4 FAIL**
  (`R3 F1 anterior rodou (podman kill)`, `R4 podman kill do injetor nunca antes de N + 48 s`,
  `R8 F1 em curso (podman kill isn2)`, `R8 runner terminou com erro`).

Falhas do `inject-crash.test.sh` contra `c055399`:

```
FAIL: C1 ordem: readlink alvo, readlink 1, ip link show, set down, -o show, kill, ExitCode, sleep, start
FAIL: C1 nsenter no PID de isn2 (nao encontrou 'nsenter -t 4002 -n ip link set dev eth0 down')
FAIL: C1 kill chamado uma vez (esperado='1' obtido='0')
FAIL: C1 linha final OK com o modo (nao encontrou 'OK: F1 (modo=silencioso)')
FAIL: C2 silencioso sem stop (esperado='0' obtido='1')
FAIL: C2 kill exit 0 (esperado='0' obtido='2')
FAIL: C3a netns do alvo = netns do PID 1 -> exit 4 (esperado='4' obtido='0')
FAIL: C3a diagnostico (nao encontrou 'netns')
FAIL: C3b PID '0' -> exit 4 (esperado='4' obtido='0')
FAIL: C3b PID '' -> exit 4 (esperado='4' obtido='0')
FAIL: C3b PID 'abc' -> exit 4 (esperado='4' obtido='0')
FAIL: C3b PID '1' -> exit 4 (esperado='4' obtido='0')
FAIL: C4 exit 4 (esperado='4' obtido='2')
FAIL: C5 exit 1 (esperado='1' obtido='0')
FAIL: C5 tentativa de up (esperado='1' obtido='0')
FAIL: C5 nenhum start (nada foi derrubado) (esperado='0' obtido='1')
FAIL: C6 exit 1 (esperado='1' obtido='0')
FAIL: C6 linha ExitCode=143 (nao encontrou 'isn2 ExitCode=143')
FAIL: C6 sem linha OK (encontrou 'OK: F1' indevidamente)
FAIL: C7 exit 5 (esperado='5' obtido='0')
FAIL: C7 nao esperou a duracao (sem sleep 60) (esperado='0' obtido='1')
FAIL: C7 nao deu start (esperado='0' obtido='1')
FAIL: C7 consultou o estado varias vezes (poll)
FAIL: C8 exit 5 (esperado='5' obtido='125')
FAIL: C9 chegou a verificacao do isolamento
FAIL: C9 interface ja esta DOWN (esperado='1' obtido='0')
FAIL: C9 exit 143 (esperado='143' obtido='0')
FAIL: C9 kill chamado pelo trap
FAIL: C9 sem espera da duracao (esperado='0' obtido='1')
FAIL: C10 start chamado uma vez (esperado='1' obtido='0')
FAIL: C10 kill chamado uma vez (ja estava morto) (esperado='1' obtido='0')
FAIL: C10 isn2 de volta a running (esperado='running' obtido='exited')
FAIL: C10 trap registrou o que fez (nao encontrou 'trap')
FAIL: C11 carimbo da espera no formato com ms ('2026-10-28T03:32:19Z')
FAIL: C11 carimbo do start no formato com ms ('2026-10-28T03:32:19Z')
FAIL: C11 carimbos diferentes antes e depois da espera real de 2 s
FAIL: C11 todas as linhas com carimbo no formato (esperado='0' obtido='5')
FAIL: C12 plano com isolamento (nao encontrou 'nsenter -t <pid> -n ip link set dev eth0 down')
FAIL: C12 plano com kill (nao encontrou 'kill isn2 --signal KILL')
FAIL: C12 plano com modo (nao encontrou 'modo=silencioso')
FAIL: C12b --modo kill --dry-run exit 0 (esperado='0' obtido='2')
FAIL: C12b modo kill com kill (nao encontrou 'kill isn2 --signal KILL')
FAIL: C12c --help documenta --modo (nao encontrou '--modo')
FAIL: C12c --help documenta --iface (nao encontrou '--iface')
FAIL: C12c --help documenta FD_SOCK2 (nao encontrou 'FD_SOCK2')
FAIL: C13a modo kill exit 0 (esperado='0' obtido='2')
FAIL: C13a ordem kill, ExitCode, sleep, start
FAIL: C13a linha modo=kill (nao encontrou 'modo=kill')
FAIL: C13b modo stop exit 0 (esperado='0' obtido='2')
FAIL: C13b ordem stop, sleep, start
FAIL: C13b linha modo=stop (nao encontrou 'modo=stop')
FAIL: C15 log casa \bmodo=silencioso\b
FAIL: C15 log casa \beth0 down\b
FAIL: C15 log casa \bkill isn2\b
FAIL: C15 log casa \bExitCode=137\b
FAIL: C15 primeira linha e o plano (nao encontrou 'Plano: NODE=isn2 MODO=silencioso DURACAO=60s IFACE=eth0 DRY_RUN=0')
FAIL: C15 linha 'modo=silencioso' isolada
```

## 3. Diff das edições autorizadas (Q2)

`git diff c055399 HEAD -- analysis/test_rebaseline.py scripts/run-rebaseline.test.sh`, inteiro. A
classe `A9bK6F1Silencioso` é acréscimo (item 4). Fora dela, as únicas mudanças são as do item 5: a
*fixture* de F1 (as 4 linhas antigas viram as 5 linhas de K6, o que desloca as linhas seguintes em +1),
o caso `test_f1_log_sem_start`, o *shim* do `podman` (`kill` e `{{.State.ExitCode}}`), o *shim* novo de
`ip` em `criar_shims` e as asserções de R3, R4 e R8, com os mesmos limites. No R4, os dois comentários
das linhas 423-424, que estão dentro do trecho 420-428, também trocam "stop" por "kill".

```diff
diff --git a/analysis/test_rebaseline.py b/analysis/test_rebaseline.py
index 8786c3a..cdfed2a 100644
--- a/analysis/test_rebaseline.py
+++ b/analysis/test_rebaseline.py
@@ -166,10 +166,11 @@ def montar_exec(raiz, linha, *, seed=None, rep=1, rows=None, stdout_kw=None, exe
         ex.update({"injetor_exit": 0, "inj_inicio_offset_s": 49.012, "disparo_utc": "2026-10-24T01:01:04.012Z",
                    "cluster_pos_f1": {"ok": True, "espera_s": 35.0}})
         if injetor_log is None:
-            injetor_log = ("[2026-10-24T01:01:04Z] inject-crash.sh: sudo podman stop isn2\n"
-                           "[2026-10-24T01:01:04Z] inject-crash.sh: aguardando 60s\n"
-                           "[2026-10-24T01:01:04Z] inject-crash.sh: sudo podman start isn2\n"
-                           "[2026-10-24T01:01:04Z] inject-crash.sh: OK: F1 (crash) injetada\n")
+            injetor_log = ("[2026-10-24T01:01:04.021Z] inject-crash.sh: modo=silencioso\n"
+                           "[2026-10-24T01:01:04.180Z] inject-crash.sh: isolando isn2: ip link set dev eth0 down (pid 4242)\n"
+                           "[2026-10-24T01:01:04.260Z] inject-crash.sh: sudo podman kill isn2 (--signal KILL)\n"
+                           "[2026-10-24T01:01:04.610Z] inject-crash.sh: isn2 ExitCode=137\n"
+                           "[2026-10-24T01:02:04.640Z] inject-crash.sh: sudo podman start isn2\n")
     elif falha == "F3":
         ex.update({"injetor_exit": 0, "inj_inicio_offset_s": 49.005, "disparo_utc": "2026-10-24T01:01:04.005Z",
                    "netem_amostras": [{"alvo_s": 70, "offset_s": 70.1, "netem": True,
@@ -608,7 +609,7 @@ class A9K6(Base):
         self.assertFalse(ch["criterios"]["K6"]["ok"])
 
     def test_f1_log_sem_start(self):
-        rc, ch = self._k6("S1-sync-F1", injetor_log="[x] inject-crash.sh: sudo podman stop isn2\n")
+        rc, ch = self._k6("S1-sync-F1", injetor_log="[x] inject-crash.sh: sudo podman kill isn2 (--signal KILL)\n")
         self.assertEqual(rc, 10)
 
     def test_sem_ancora(self):
@@ -618,6 +619,78 @@ class A9K6(Base):
         self.assertFalse(ch["criterios"]["K6"]["ok"])
 
 
+class A9bK6F1Silencioso(Base):
+    """K6 de F1 como crash silencioso (opcao B, docs/prompt-f1-kill-2026-10-27.md, item 4 do prompt)."""
+
+    LINHAS = (
+        "[2026-10-24T01:01:04.020Z] inject-crash.sh: Plano: NODE=isn2 MODO=silencioso DURACAO=60s IFACE=eth0 DRY_RUN=0",
+        "[2026-10-24T01:01:04.021Z] inject-crash.sh: modo=silencioso",
+        "[2026-10-24T01:01:04.180Z] inject-crash.sh: isolando isn2: ip link set dev eth0 down (pid 4242)",
+        "[2026-10-24T01:01:04.260Z] inject-crash.sh: sudo podman kill isn2 (--signal KILL)",
+        "[2026-10-24T01:01:04.610Z] inject-crash.sh: isn2 ExitCode=137",
+        "[2026-10-24T01:01:04.611Z] inject-crash.sh: aguardando 60s",
+        "[2026-10-24T01:02:04.640Z] inject-crash.sh: sudo podman start isn2",
+        "[2026-10-24T01:02:05.100Z] inject-crash.sh: OK: F1 (modo=silencioso) injetada e reentrada de isn2 acionada",
+    )
+    # indice da linha que carrega cada um dos 5 sinais exigidos
+    CHAVES = {"log_modo": 1, "log_isolamento": 2, "log_kill": 3, "log_exit137": 4, "log_start": 6}
+
+    def _k6(self, log, n=0, **kw):
+        d = montar_exec(self.saida, self.linha("S1-sync-F1", n=n), injetor_log=log, **kw)
+        return self.checar(d)
+
+    def test_log_completo_aceita(self):
+        rc, ch = self._k6("\n".join(self.LINHAS) + "\n")
+        self.assertEqual((rc, ch["veredito"]), (0, "ACEITA"), ch["motivos"])
+        v = ch["criterios"]["K6"]["valor"]
+        for chave in list(self.CHAVES) + ["cluster_de_volta"]:
+            self.assertIs(v[chave], True, chave)
+        self.assertNotIn("log_stop", v)
+        self.assertIn("kill", ch["criterios"]["K6"]["limite"])
+
+    def test_cada_linha_ausente_recusa(self):
+        for n, (chave, idx) in enumerate(sorted(self.CHAVES.items())):
+            linhas = [l for j, l in enumerate(self.LINHAS) if j != idx]
+            if chave == "log_modo":   # a linha OK final tambem diz modo=silencioso
+                linhas = [l.replace("(modo=silencioso)", "") for l in linhas]
+            rc, ch = self._k6("\n".join(linhas) + "\n", n=n)
+            self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"), chave)
+            self.assertIs(ch["criterios"]["K6"]["valor"][chave], False, chave)
+            self.assertFalse(ch["criterios"]["K6"]["ok"], chave)
+
+    def test_stop_no_lugar_de_kill_recusa(self):
+        linhas = list(self.LINHAS)
+        linhas[3] = "[2026-10-24T01:01:04.260Z] inject-crash.sh: sudo podman stop isn2"
+        rc, ch = self._k6("\n".join(linhas) + "\n")
+        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
+        self.assertIs(ch["criterios"]["K6"]["valor"]["log_kill"], False)
+
+    def test_log_de_junho_recusa(self):
+        junho = ("[2026-06-20T01:01:04Z] inject-crash.sh: Plano: NODE=isn2 DURACAO=60s DRY_RUN=0\n"
+                 "[2026-06-20T01:01:04Z] inject-crash.sh: sudo podman stop isn2\n"
+                 "[2026-06-20T01:01:04Z] inject-crash.sh: aguardando 60s\n"
+                 "[2026-06-20T01:01:04Z] inject-crash.sh: sudo podman start isn2\n"
+                 "[2026-06-20T01:01:04Z] inject-crash.sh: OK: F1 (crash) injetada e reentrada de isn2 acionada\n")
+        rc, ch = self._k6(junho)
+        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
+        v = ch["criterios"]["K6"]["valor"]
+        self.assertIs(v["log_kill"], False)
+        self.assertIs(v["log_modo"], False)
+
+    def test_exitcode_143_recusa(self):
+        linhas = list(self.LINHAS)
+        linhas[4] = "[2026-10-24T01:01:04.610Z] inject-crash.sh: isn2 ExitCode=143"
+        rc, ch = self._k6("\n".join(linhas) + "\n")
+        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
+        self.assertIs(ch["criterios"]["K6"]["valor"]["log_exit137"], False)
+
+    def test_modo_kill_recusa(self):
+        """O modo kill (sem isolamento) nao e o F1 do rebaseline."""
+        linhas = [l.replace("silencioso", "kill") for j, l in enumerate(self.LINHAS) if j != 2]
+        rc, ch = self._k6("\n".join(linhas) + "\n")
+        self.assertEqual((rc, ch["veredito"]), (10, "RECUSADA"))
+
+
 # ============================================================================ A10: estado
 
 class A10Estado(Base):
diff --git a/scripts/run-rebaseline.test.sh b/scripts/run-rebaseline.test.sh
index dec457b..5c0a5e6 100755
--- a/scripts/run-rebaseline.test.sh
+++ b/scripts/run-rebaseline.test.sh
@@ -124,9 +124,11 @@ case "${cmd}" in
             *StartedAt.UnixMilli*) echo "$(( $(cat "${ST}/iniciado-${no}" 2>/dev/null || echo 1792700000) * 1000 ))" ;;
             *StartedAt.Unix*) cat "${ST}/iniciado-${no}" 2>/dev/null || echo 1792700000 ;;
             *FinishedAt.UnixMilli*) cat "${ST}/parado-ms-${no}" 2>/dev/null || echo -62135596800000 ;;
+            *State.ExitCode*) cat "${ST}/exitcode-${no}" 2>/dev/null || echo 0 ;;
             *) echo '[{}]' ;;
         esac ;;
     stop) echo stopped > "${ST}/status-$1"; echo parou >> "${ST}/parou-$1"; vms > "${ST}/parado-ms-$1"; echo "$1" ;;
+    kill) no="$1"; echo exited > "${ST}/status-${no}"; echo parou >> "${ST}/parou-${no}"; vms > "${ST}/parado-ms-${no}"; echo 137 > "${ST}/exitcode-${no}"; echo "${no}" ;;
     start)
         if [[ "$(cat "${ST}/status-$1" 2>/dev/null || echo running)" != running ]]; then
             # nova vida do processo: ISPN080001 na subida e o aviso ISPN006010 da JVM nova (DT-013)
@@ -194,6 +196,26 @@ case "$*" in
         if [[ -f "${f}" ]]; then echo "qdisc netem 8001: root refcnt 2 limit 1000 delay 20ms  13ms"
         else echo "qdisc noqueue 0: root refcnt 2"; fi ;;
 esac
+EOF
+    cat > "${SH}/ip" <<'EOF'
+#!/usr/bin/env bash
+source "${RB_SHIM_DIR}/_comum.sh"
+registrar ip "pid=${RB_NETNS_PID:-?}" "$@"
+ST="${RB_ESTADO}"
+f="${ST}/link-${RB_NETNS_PID:-0}"
+mostrar() {
+    if [[ "$(cat "${f}" 2>/dev/null || echo up)" == down ]]; then
+        echo "2: eth0@if7: <BROADCAST,MULTICAST> mtu 1500 qdisc noqueue state DOWN mode DEFAULT group default qlen 1000"
+    else
+        echo "2: eth0@if7: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT group default qlen 1000"
+    fi
+}
+case "$*" in
+    "link show dev eth0"|"-o link show dev eth0") mostrar ;;
+    "link set dev eth0 down") echo down > "${f}" ;;
+    "link set dev eth0 up") echo up > "${f}" ;;
+    *) echo "Device does not exist." >&2; exit 1 ;;
+esac
 EOF
     cat > "${SH}/curl" <<'EOF'
 #!/usr/bin/env bash
@@ -364,7 +386,7 @@ echo "go piloto 1999-01-01" > "${RB_TTY_ARQUIVO}"
 rodar_runner piloto
 ok "R3 gate da noite errado -> exit 5" "5" "${RC}"
 ok "R3 sem java" "0" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
-ok "R3 nenhum injetor (podman stop)" "0" "$(grep -c 'podman|stop' "${ST}/chamadas.log")"
+ok "R3 nenhum injetor (podman kill)" "0" "$(grep -c 'podman|kill' "${ST}/chamadas.log")"
 ok "R3 nenhum netem" "0" "$(grep -c 'tc|.*add' "${ST}/chamadas.log")"
 
 novo_caso r3b
@@ -393,7 +415,7 @@ matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1" "1001 S1-sync-F3"
 printf 'go piloto %s\ngo f3 9999\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
 rodar_runner piloto
 ok "R3 gate f3 errado -> exit 5" "5" "${RC}"
-ok "R3 F1 anterior rodou (podman stop)" "1" "$(grep -c ' podman|stop|isn2' "${ST}/chamadas.log")"
+ok "R3 F1 anterior rodou (podman kill)" "1" "$(grep -c ' podman|kill|isn2' "${ST}/chamadas.log")"
 ok "R3 nenhum netem sem o gate f3" "0" "$(grep -c 'tc|.*|qdisc|add' "${ST}/chamadas.log")"
 ok "R3 workload da F3 nao lancado" "1" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
 verdade "R3 (C2) go f3 errado nao deixa exec-1001" test -z "$(ls -d "${OUT}"/exec-1001-* 2>/dev/null)"
@@ -417,15 +439,15 @@ ok "R4 exit 0" "0" "${RC}"
 D="$(exec_dir 1000)"
 ANC="$(cat "${ST}/ancora-150000")"
 DISP="$(json "${D}/execucao.json" 'd["disparo_ms"]')"
-STOP="$(grep 'podman|stop|isn2' "${ST}/chamadas.log" | head -1 | cut -d' ' -f1)"
-echo "   (ancora do java ${ANC}; disparo do runner +$(( DISP - ANC )) ms; podman stop +$(( STOP - ANC )) ms)"
+STOP="$(grep 'podman|kill|isn2' "${ST}/chamadas.log" | head -1 | cut -d' ' -f1)"
+echo "   (ancora do java ${ANC}; disparo do runner +$(( DISP - ANC )) ms; podman kill +$(( STOP - ANC )) ms)"
 verdade "R4 disparo em N + 49 s (+-1 s)" test "$(( DISP - ANC ))" -ge 48000 -a "$(( DISP - ANC ))" -le 50000
-# O instante do podman stop depende tambem do proprio injetor real e do host (no Git Bash ja se viu uma
-# pausa de ~40 s reais antes do stop, V-RB 2.1). K6 mede o disparo do runner (acima, +-1 s); aqui so se
+# O instante do podman kill depende tambem do proprio injetor real e do host (no Git Bash ja se viu uma
+# pausa de ~40 s reais antes do kill, V-RB 2.1). K6 mede o disparo do runner (acima, +-1 s); aqui so se
 # exige que o injetor nunca aja ANTES de N + 48 s; acima de N + 51 s e aviso, nao falha (o instante
 # efetivo vai para falha_efetiva, C5, e e conferido no parecer do piloto).
-verdade "R4 podman stop do injetor nunca antes de N + 48 s" test "$(( STOP - ANC ))" -ge 48000 -a "${STOP}" -ge "${DISP}"
-if [[ "$(( STOP - ANC ))" -gt 51000 ]]; then echo "   AVISO R4: podman stop a +$(( STOP - ANC )) ms (pausa do host ou do injetor; nao e falha do runner)"; fi
+verdade "R4 podman kill do injetor nunca antes de N + 48 s" test "$(( STOP - ANC ))" -ge 48000 -a "${STOP}" -ge "${DISP}"
+if [[ "$(( STOP - ANC ))" -gt 51000 ]]; then echo "   AVISO R4: podman kill a +$(( STOP - ANC )) ms (pausa do host ou do injetor; nao e falha do runner)"; fi
 OFF="$(json "${D}/execucao.json" 'd["inj_inicio_offset_s"]')"
 verdade "R4 inj_inicio_offset_s em [48,50]" "${REAL_PY}" -c "import sys; sys.exit(0 if 48 <= ${OFF} <= 50 else 1)"
 PAR="$(json "${D}/execucao.json" 'd["falha_efetiva"]["f1_parado_desde_ancora_s"]')"
@@ -442,7 +464,7 @@ D="$(exec_dir 1000)"
 ok "R4 sem ancora: segue (exit 0)" "0" "${RC}"
 ok "R4 sem ancora: workload morto" "1" "$(cat "${ST}"/java-morto-150000 2>/dev/null | grep -c morto)"
 ok "R4 sem ancora: recusa K6 registrada" "sem ancora" "$(json "${D}/execucao.json" 'd["recusa_k6"]')"
-ok "R4 sem ancora: nenhum injetor" "0" "$(grep -c 'podman|stop' "${ST}/chamadas.log")"
+ok "R4 sem ancora: nenhum injetor" "0" "$(grep -c 'podman|kill' "${ST}/chamadas.log")"
 fi
 
 # ============================================================================= R5
@@ -530,12 +552,12 @@ printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
 PATH="${PATH_TESTE}" bash "${RUNNER}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --jar "${JAR}" --jar-sha256 "${JAR_SHA}" \
     --fase piloto --saida "${OUT}" --cluster-criado-apos 2026-10-23T00:00:00Z > "${CASO}/out.txt" 2>&1 &
 PID=$!
-verdade "R8 F1 em curso (podman stop isn2)" esperar_por 'podman|stop|isn2' 120
+verdade "R8 F1 em curso (podman kill isn2)" esperar_por 'podman|kill|isn2' 120
 "${REAL_SLEEP}" 0.5
 kill -TERM "${PID}"; wait "${PID}"; RC=$?
 verdade "R8 runner terminou com erro" test "${RC}" -ne 0
-STOP="$(linha_de 'podman|stop|isn2')"; START="$(linha_de 'podman|start|isn2')"
-verdade "R8 podman start isn2 depois do stop" test "${START}" -gt "${STOP}"
+STOP="$(linha_de 'podman|kill|isn2')"; START="$(linha_de 'podman|start|isn2')"
+verdade "R8 podman start isn2 depois do kill" test "${START}" -gt "${STOP}"
 ok "R8 isn2 running" "running" "$(cat "${ST}/status-isn2")"
 verdade "R8 brutos preservados" test -s "$(exec_dir 1000)/stdout.log"
 
```

## 4. Mutantes

Cada mutante numa cópia (rodada com `CRASH_SCRIPT=<cópia>`; M7 numa cópia de `analysis/` e de
`scripts/rebaseline_matriz.py`). Nenhum sobreviveu.

| Mutante | Mudança | PASS/FAIL | Casos que matam |
|---|---|---|---|
| M1 | `podman stop` no lugar do `kill` | 95/18 | C1 (ordem, kill 1x, linha OK), C2 (stop chamado), C7, C10, C11, C13a, C15 |
| M2 | sem `ip link set down` | 86/27 | C1 (exit 1, ordem, nsenter), C2, C6, C7, C8, C9, C10, C11, C15 |
| M3 | isolamento depois do `kill` | 110/3 | C1 (ordem), C5 (kill chamado), C9 (kill antes da verificação) |
| M4 | sem a comparação de *netns* com o PID 1 | 108/5 | C3a (exit 0, `ip`/`nsenter`/`kill` chamados) |
| M5 | sem *trap* | 108/5 | C9 (kill e start pelo trap), C10 (start, isn2 running, log do trap) |
| M6 | sem checagem do ExitCode | 111/2 | C6 (exit 0 e linha OK com ExitCode=143) |
| M7 | K6 com `\b(kill\|stop) isn2\b` | 11/2 (testes) | `test_stop_no_lugar_de_kill_recusa`, `test_log_de_junho_recusa` |
| M8 | `TIMESTAMP` fixo (o de `c055399`) | 109/4 | C11 (formato e carimbos iguais) |
| M9 | erro do `start` ignorado | 111/2 | C8 (exit 0 e linha OK) |
| M10 | padrão de `--modo` = `stop` | 77/36 | C1, C2, C3a, C3b, C4, C5, C6, C7, C9, C10, C12, C15 |
| M11 | `sleep` em primeiro plano | 112/1 | C10 (não saiu em < 10 s) |

## 5. Suítes (Git Bash, ponta `0e83ec3`)

| Suíte | Resultado | Tempo |
|---|---|---|
| `bash scripts/inject-crash.test.sh` | 113 PASS, 0 FAIL | 41 s |
| `bash scripts/inject-jitter.test.sh` | 35 PASS, 0 FAIL | 5 s |
| `bash scripts/inject-netdegrade.test.sh` | 298 PASS, 0 FAIL, 1 SKIP | 309 s |
| `bash scripts/inject-partition.test.sh` | 125 PASS, 0 FAIL, 2 SKIP | 110 s |
| `bash scripts/run-rebaseline.test.sh`, rodada 1 | 130 PASS, 0 FAIL | 1217 s |
| `bash scripts/run-rebaseline.test.sh`, rodada 2 | 130 PASS, 0 FAIL | 1194 s |
| `bash scripts/run-rebaseline.test.sh`, rodada 3 | 130 PASS, 0 FAIL | 1164 s |
| `python -I analysis/test_rebaseline.py` | 69 OK (7 *skipped*) | 17 s |
| `python analysis/test_rebaseline.py` (sem `-I`, com numpy/scipy) | 69 OK (1 *skipped*) | 18 s |
| `python -I scripts/test_rebaseline_matriz.py` | 8 OK | 1 s |
| `python analysis/test_contrato_csv.py` e `_v2.py` | 9 OK e 6 OK | < 1 s |

As três rodadas do *runner* foram seguidas, sem outra suíte em paralelo. Nas três, R4 deu o aviso
(não falha) "podman kill a +54138 / +53736 / +53464 ms": no modo silencioso há seis processos a mais
antes do `kill` (`inspect` do PID, dois `readlink`, três `ip`), e a escala ×2 do relógio do teste
multiplica o custo de *fork* do Git Bash. A asserção (nunca antes de N + 48 s) passou e o disparo do
*runner* continuou em ±1 s. No *smoke* real o isolamento saiu ~0,1 s depois da primeira linha do
injetor e o `kill` 17 a 35 ms depois do isolamento.

`python -I` em `test_contrato_csv*.py` sai com `ModuleNotFoundError: numpy` (o `-I` desliga o
*site-packages* do usuário); por isso rodaram sem `-I`, como nos relatórios anteriores.

## 6. *Bundle* (*blobs* do git, LF, por `git archive` de `0e83ec3`)

| Arquivo | SHA-256 |
|---|---|
| `analysis/rebaseline.py` | `3005f49ae5696dcd9ace324b08295187100fcde39c111115a89c517bc4e3c7cd` (antes `fe5c7c74…`) |
| `runs/rebaseline/matriz.tsv` | `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35` |
| `scripts/inject-crash.sh` | `5b4d571986256d96c73405ac887eca55c783787ad0bc0a0c5dec558856ddc0cf` (antes `1c9bf01b…`) |
| `scripts/inject-jitter.sh` | `7e68962b1b7f508ee84ca7561a20c0bba36c43ea4597d7390e7c8091cd24e529` |
| `scripts/rebaseline_matriz.py` | `3531916a863832fd5aea050910111c69f0c9130c0bd325c3ad3f238e44a3533a` |
| `scripts/run-rebaseline.sh` | `d95305fee402b1f882d387a40e81d4f83df72d7fe4b6daf810516e0627267366` (igual) |
| `workload/target/session-workload-0.1.0-SNAPSHOT.jar` | `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf` (congelado, igual) |

SHA-256 da lista no formato do `sha256sum` (`<hash> *<caminho>`, nesta ordem):
`003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4` (antes `e304ba4b…`; o mesmo método
com os dois *hashes* antigos reproduz `e304ba4b…`). `git show HEAD:scripts/run-rebaseline.sh | sha256sum`
dá `d95305fe…7366`. O *commit* `docs:` não toca nenhum arquivo do *bundle*.

## 7. *Smoke* local (seção 4.1 do plano)

Máquina Podman local (`podman-machine-default`, WSL2, *kernel* 5.15.153.1, Fedora 42, Podman 5.7.0,
*rootful*, como root), injetor da branch rodando dentro da máquina (`podman machine ssh`). *Cluster*
próprio do *smoke*: `f1s-isn1..3`, com *hostname* e *alias* de rede `isn1..3`, rede `f1smoke-net`,
`cluster/` de `c055399` montado; os *containers* `isn1..3` antigos não foram tocados. Alvo `f1s-isn2`,
`--duration 60`. Brutos em `runs/smoke-f1-silencioso-2026-10-27/` (scripts `smoke-f1.sh`,
`carga-f1.sh`, `analisar_smoke.py`, `analisar_carga.py`; `resumo.json`; `MANIFEST.sha256` com 154
arquivos). Os `*.log`, `*.txt` e `*.csv` ficam fora do git pelo `.gitignore` (D-005) e estão só no
*worktree*. Ao fim, `smoke-f1.sh teardown` removeu `f1s-isn1..3` e a rede.

Δdet = primeira `ISPN000094` sem `isn2` em `isn1`/`isn3` (carimbo do `podman logs -t`) menos o instante
da injeção (linha de isolamento, de `kill` ou de `stop` do `injetor.log`, com ms).

| Ensaio | Modo | Δdet isn1 / isn3 (s) | ExitCode (inspect / `died`) | `ISPN000390` e `080002` na vida antiga | `.lck` parado | `ISPN000693` / `000389` / `080001` na vida nova | `start` até HEALTHY (s) | IP antes / depois | `eth0` depois |
|---|---|---|---|---|---|---|---|---|---|
| e1 | stop | 1,209 / 1,207 | 0 / 0 | sim / sim | ausente | não / sim / sim | 56,6 (o `podman start` levou 51,0) | .3 / .5 | UP |
| e4 | stop | 0,207 / 0,205 | 0 / 0 | sim / sim | ausente | não / sim / sim | 6,8 | .7 / .8 | UP |
| e2 | kill | 1,034 / 1,031 | 137 / 137 | não / não | 0 byte | não / sim / sim | 4,7 | .5 / .6 | UP |
| e5 | kill | 1,030 / 1,028 | 137 / 137 | não / não | 0 byte | não / sim / sim | 3,3 | .8 / .9 | UP |
| e3 | silencioso | 40,691 / 40,682 | 137 / 137 | não / não | 0 byte | não / sim / sim | 4,7 | .6 / .7 | UP |
| e6 | silencioso | 36,532 / 36,528 | 137 / 137 | não / não | 0 byte | não / sim / sim | 3,3 | .9 / .10 | UP |
| c1 (carga) | silencioso | 41,915 / 41,308 | 137 / 137 | não / não | 0 byte | não / sim / sim | 7,8 | .11 / .12 | UP |
| c2 (carga) | kill | 1,905 / 1,340 | 137 / 137 | não / não | 0 byte | não / sim / sim | 6,6 | .12 / .13 | UP |

IPs em `10.89.2.0/24`; `isn2` volta **sempre com outro IP** e o TCPPING resolve pelo nome. Isolamento
até `kill`: 19, 17 e 35 ms. `___global.state` (148 bytes) presente em todos. Injetor com exit 0 em todos.

A previsão da seção 1 do plano se confirma. `kill` sozinho é detectado em ~1 s (FD_SOCK2 +
VERIFY_SUSPECT2), como o `stop` (*leave*); com o isolamento antes do `kill`, a detecção vai para 36,5 a
41,9 s (FD_ALL3). Nenhuma condição de parada disparou: silencioso ≥ 30 s, kill ≤ 30 s, *cluster* de
volta em < 180 s. O `.lck` sujo deixado pelo SIGKILL não impediu a subida (`ISPN000693` ausente,
`ISPN000389` "Loaded global state" presente), como a leitura da fonte previa.

Com carga: jar congelado `5eced136…`, S1, sync, 180 s, 4 *threads*, sementes 990001 e 990101 (fora da
faixa da matriz), injeção a +49 s da âncora. Janela = da injeção até ~3 nós HEALTHY (≈ 69 s):

| Ensaio | Ops na janela | `ERROR_TRANSPORT` na janela | Latência máx. na janela (ms): Increment / Login / Logout / Reset / Validate | p99 na janela (ms), mesma ordem | VerificadorSanidade |
|---|---|---|---|---|---|
| c1 silencioso | 48 355 | 12 (Increment 4, Login 2, Logout 3, Reset 3); 0 fora da janela | 10 046 / 8 042 / 10 063 / 10 934 / 4 058 | 5,81 / 6,71 / 5,47 / 4,90 / 3,46 | OK |
| c2 kill | 159 535 | 0 | 2 221 / 2 710 / 4 919 / 2 058 / 859 | 4,09 / 5,16 / 3,77 / 4,21 / 2,90 | OK |

No silencioso, a vazão na janela cai a ~30 % da do `kill` (operações presas até o FD_ALL3 decidir;
`ISPN000476` "Timed out waiting for responses ... after 17.5 seconds" no *stdout*), com 12 erros de
transporte, e o `VerificadorSanidade` deu OK. É o fenômeno que a Metodologia quer medir e é o dado para
o risco 1 (K3) da seção 6 do plano. Um ensaio por modo, sem valor estatístico.

## 8. Desvios do prompt

1. **Ordem dos argumentos do `kill`.** O injetor chama `podman kill <nó> --signal KILL`, não
   `podman kill --signal KILL <nó>`. As edições autorizadas de R3/R4/R8 procuram `podman|kill|isn2` no
   registro dos *shims*, que grava os argumentos na ordem; com a *flag* antes do nome a sequência seria
   `podman|kill|--signal|KILL|isn2`, e trocar o padrão seria edição de teste fora da lista da Q2. O
   Podman aceita *flags* depois do nome; conferido no Podman 5.7.0 do Windows (`podman kill nao-existe
   --signal KILL` dá "no such container", não erro de *flag*) e no *smoke* (ExitCode 137 em todos). A
   linha de log é `podman kill <nó> (--signal KILL)`, que casa `\bkill isn2\b`. A DT-017 e o
   complemento 2 citam a outra ordem; falta conferir na VM.
2. **Ordem dos *commits*.** `test:` (novos, vermelhos), `test:` (edições autorizadas, também vermelhas
   contra o injetor antigo), `fix:`, `docs:`, em vez de `test:`, `fix:`, `test:`, `docs:`. Assim todo
   *commit* a partir do `fix:` tem as suítes verdes. O `inject-crash.test.sh` mudou no segundo `test:`
   (ordem do `kill` e casamento por prefixo); o vermelho foi refeito com a versão final.
3. **Comportamentos a mais no injetor**, sem mudar o contrato: no exit 5 por `kill` que não derruba o
   *container*, o modo silencioso tenta `ip link set ... up` antes de sair (para não deixar `isn2` vivo e
   mudo); no modo `stop`, erro do `podman stop` dá exit 5 (antes saía com o código do podman pelo
   `set -e`) e o ExitCode é só registrado; `--node` passa por regex de nome (exit 2); `sudo`, `nsenter`
   e `ip` só são exigidos no modo silencioso.
4. **Pequenos, de execução:** a *fixture* de F1 passou de 4 para 5 linhas físicas; `test_contrato_csv*.py`
   rodou sem `-I` (numpy); o *smoke* usou *cluster* próprio (`f1s-isn*`) em vez dos `isn1..3` antigos;
   `run-baseline.sh` (fora da lista de documentos) continua com o comentário "(podman stop)" na linha 11
   e passa a usar o modo padrão novo (Q6); os brutos versionáveis do *smoke* entram no *commit* `docs:`.
5. **Ensaio com carga refeito.** O primeiro `c1` (guardado como `c1-carga-silencioso.invalido-portforward`)
   ficou inválido: depois do `kill`/`start`, o encaminhamento de `localhost:11223` do Windows para a
   máquina WSL não volta (curl dá *timeout* em 11223 e responde em 11222 e 11224), o *workload* ficou
   preso e foi morto (exit 143, sem CSV). É artefato do Podman no Windows, não do injetor: a saúde medida
   dentro da máquina estava HEALTHY. `c1` e `c2` foram refeitos com o *workload* apontando para o IP da
   máquina (`172.30.156.3`, variável `HOSTSRV` de `carga-f1.sh`). Na VM o *runner* usa `127.0.0.1`
   dentro do próprio Linux e não passa por esse encaminhamento.

## 9. Pontos para o Senior e o Gestor

1. Aceitar o desvio 1 (ordem do `kill`) e ajustar o texto da DT-017 e do complemento 2, ou autorizar a
   troca do padrão em R3/R4/R8 para manter a ordem do prompt.
2. **ExitCode do `stop` local = 0, não 143.** O complemento 1 (§1.4 item 2) e a tabela 2.1 do plano dizem
   143. Aqui o Infinispan sai com 0 no SIGTERM. A conferência do complemento 2 ("`died` de `isn2` com
   137, nunca 143") continua válida; só a expectativa do `stop` não se confirmou fora da VM.
3. **Δdet do silencioso de 36,5 s em e6**, abaixo da faixa [38, 55] s que o plano esperava (e acima do
   limite de parada de 30 s). O FD_ALL3 conta 40 s desde a última mensagem recebida de `isn2`, e sem
   carga ela pode ter chegado até um intervalo de *heartbeat* (8 s) antes do isolamento; a faixa
   compatível com o FD_ALL3 fica em ~[32, 49] s. Nada foi ajustado. Sugiro que o ensaio da VM registre a
   faixa observada sem fixar 38 s como mínimo.
4. Um `podman start` de 51 s (e1, primeiro reinício do *cluster* novo) na máquina WSL; os outros sete
   levaram 0,1 a 2,5 s. Na VM, o tempo do `start` entra em `falha_efetiva` (C5) e na parada (e).
5. Risco K3 (seção 6, risco 1): o único ensaio com carga do silencioso passou no `VerificadorSanidade`,
   com 12 erros de transporte e latência máxima de ~11 s. Quem decide é o ensaio da VM (Q4).
6. Adenda da DT-009 parte 2: ponta = SHA do *commit* `docs:` desta branch (ou o da integração); tabela da
   seção 6; lista `003814e9…`.
