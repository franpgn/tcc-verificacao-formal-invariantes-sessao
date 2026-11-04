# V-diff-v4h: revisão do Senior do diff 2ac9ec8..f6483c5 (2026-11-04)

Branch `test/b29-gate-v4`. Commits revisados: b843530, fa229d6, 8f98042, 3f43ead, 93c5fb9, 4673a3b, e855c35, f6483c5 (171b74a fora).
Bundle levado à VM: `gate-v4h.bundle` (1266c12..f6483c5). Esta nota não entra no bundle e não muda nenhum hash conferido na VM.

**Veredito: APROVADO para a VM.**

## Conferido

| Item decidido | Onde | Situação |
|---|---|---|
| `--boot-esperado` obrigatório, UUID minúsculo, sem boot_id no código (Q-11) | s5.sh, laço de argumentos antes de qualquer saída; passo 0 | ok. Testado em linha: sem argumento, UUID maiúsculo, valor ausente e argumento desconhecido dão rc 1 sem criar `vm-<UTC>/` |
| Regra do prefixo do `go.log` (Q-12) | s5.sh, passo 0 | ok (só ` M runs/gate-b29/go.log`; HEAD prefixo exato; linhas novas começando com `go `) |
| Passo 0 sem exigir a vista do S4 | s5.sh | ok (só registro) |
| `FOLGA_LAT_S=0`, `BASE_MIN_N=20` | s5.sh, em `parametros.txt` e no passo 1 | ok. Base de 60 s a 2 s dá ~30 amostras por fonte; ping da base tem 300. Se faltar amostra, o passo 1 para com rc 2 (não há VÁLIDO silencioso) |
| R-1: FALHA abaixo de 14 s, RESULTADO + LEITURA HUMANA em [14, 15) | s6.sh A6, s7.sh B1/A6 | ok. Conferido no g5b.sh: `t_kill` vem antes do `T_P0=$(date +%s)` e o laço só sai com diferença inteira >= 15, logo `t_persistencia_ok - t_kill` > 14 s sempre que o g5b.sh passa |
| D-2: 0 s depois de `t_start`, 1 s depois de `t_heal`/`t_fire` | s6.sh, s7.sh | ok. `carimbo t_start` vem depois de `espera_marcador ... start` (g5b.sh:116-117); `t_heal` é carimbado antes do `--heal`; `t_fire` é a primeira sondagem sem clsact |
| D-1 do S7: FALHA com 0 dels da unit entre `t_kill` e `t_fire + 1 s` | s7.sh | ok (`nuni_j`; `t_fire` ausente também cai em FALHA) |
| `g5b.sh` intocado | — | ok (`git diff --quiet` no intervalo) |
| Limite de 150 s pré-registrado; GO do coordenador "autorizado pelo Gestor em 2026-11-04" | ROTEIRO-S6/S7, ROTEIRO-S5, roteiro da VM | ok |
| sha256 | s5.sh 28766575…, s6.sh 33b8fcc5…, s7.sh 2385609e… | iguais aos informados |

## Pontos deixados pelo Worker

1. **rc 1 no erro de argumento do S5.** Aceito. No s5.sh o rc 2 já é "linha de base", então não dá para copiar o rc 2 do s6/s7 sem renumerar a saída. O cabeçalho documenta isso ("1 argumento ou pré-condição"), e a mensagem `S5 ABORTADO` em stderr, sem `vm-<UTC>/`, distingue os dois casos. A diferença em relação ao s6/s7 fica registrada aqui. Não mexer agora.
2. **`D2_PRE_FIRE_S=2` mantido.** Aceito. O disparo cai entre a sondagem anterior (até ~1 s + duração do `tc` antes de `t_fire`) e `t_fire`. Com 2 s, a janela clsact=1 não pega a transição.

## VM religada às 21:49Z (IP 172.27.114.62, boot f9e98b74-…)

Não exige commit antes do S5. Nenhum dos três scripts tem IP ou boot_id no código (o IP vem de `hostname -I`, o boot de `--boot-esperado`). Basta o coordenador rodar
`bash runs/vm-s5-2026-10-28/s5.sh --boot-esperado f9e98b74-bf1f-4192-9770-b40285877292`
e registrar no log do bloco o IP novo, o boot novo e a recriação (`runs/recriacao-pos-shutdown-2026-11-04/vm-20261104T214940Z`, XML 8792dea3…).
Os valores antigos dos roteiros (172.27.127.98, b088606d…, recriação das 19:14) são só texto. O coordenador **não** deve comparar o boot_id contra a linha 26 do ROTEIRO-S5 nem contra o roteiro da VM. Esses textos serão atualizados num `docs:` depois do bloco, só nos `.md`. O comentário do cabeçalho do s5.sh, que cita a recriação das 19:14, fica como está, porque editá-lo mudaria o sha256.
