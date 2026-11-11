# X-11: validação do piloto do *rebaseline* e liberação da campanha (Senior, 2026-11-05)

> Parecer do Senior sobre a rodada 0 (i = 1000..1011), feito só por leitura dos brutos no host, durante um bloco H2 sem
> VM (o host rodava testes, Maven e Podman de outros Workers). Referências: PR §6 e §8
> (`pre-registro-rebaseline-aprovado-2026-10-23.md`), complemento 1 §1.3 e §2, complemento 3 (h4), DT-013 a DT-019,
> roteiro `roteiro-piloto-2026-11-05.md` §3 e roteiro da VM (`wt-gate-v4/docs/roteiro-vm-2026-11-04.md`) §7.5.
> Nenhum arquivo congelado foi alterado. As contas independentes foram feitas no *scratchpad*, sobre cópia dos brutos.

## 0. Veredito

**APROVADO COM RESSALVAS.** O piloto cumpre a condição de liberação do PR §8: 12 de 12 execuções ACEITAS, sem recusa,
sem parada (a) a (h) e sem nenhuma execução h4. O *runner*, os critérios e o amostrador funcionaram como especificado.

As ressalvas (seção 9) não vêm do *grid* nem do código de medição. Vêm das covariáveis do host (C3 §7), da margem de
disco da VM para a noite 1 e de um defeito cosmético do *runner* congelado no `MANIFEST.sha256`. Pela A-7, com
ressalvas, **a liberação da noite 1 volta ao Gestor**. O coordenador não libera sozinho. A recomendação do Senior é
liberar a noite 1 nas condições da seção 10, com o roteiro `docs/roteiro-noite1-2026-11-05.md`.

## 1. Brutos lidos e integridade

| Item | Valor |
|---|---|
| Pacote no host | `dados-vm/rebaseline-2026-11/piloto-20261105/piloto-pacote.tar`, SHA-256 `e3955c27546b648c…` (re-hasheado pelo Senior, confere) |
| `piloto/MANIFEST.sha256` | 163 linhas, sendo 162 de arquivos que existem (todas OK) e 1 que aponta para o próprio `.tmp` (seção 8). Conjunto listado = conjunto em disco, tirando o próprio manifesto |
| Matriz | `matriz.tsv` `8654900c…` em `rebaseline/` e em `rebaseline-ensaio/` (cópia do próprio *runner*) |
| Listas de *hash* na VM | `lista-003814e9.sha256` e `lista-e9c44819.sha256` reproduzem `003814e9…` e `e9c44819…` |
| `rebaseline.py` usado na conta independente | *blob* de `wt-ctrl-integ`, `3005f49a…` (igual à lista) |
| Amostrador | `piloto-20261105T040314Z/amostras-final.txt`, SHA-256 `bf112f4b…` (confere com o `.sha256` e com o `h4-piloto.json`); espelho idêntico byte a byte |
| `recriar-rb-20805b0.sh` | `618861b1…`, igual na infra e na pasta da recriação |

## 2. Pré-voo, recriação e ensaio

- **Recriação** (`recriacao-pre-piloto-2026-11-05/vm-20261105T040108Z/`): `down rc=0`, `up rc=0`, `ESTAVEL size=3 HEALTHY`
  em 27 s, 0 *timers* `tcc-heal`, `qdisc noqueue` nos três netns. `cluster_criado_apos=2026-11-05T04:01:07Z`. Os
  `Created` dos três contêineres (04:01:24 a 04:01:27Z) são posteriores, e o `ambiente.md` do *runner* registra o mesmo.
- **Dry-run**: `SHA-256 confere`, 12 execuções, `primeira_F3=1005`, `DRY-RUN: nada executado`.
- **Pré-voo sem injeção** (`rebaseline-ensaio/`): `go recusado (noite)`, `FIM exit=5 motivo=go nao dado (noite)`, nenhum
  `exec-*`. O aviso "MANIFEST.sha256 nao gravado" que o roteiro esperava não apareceu, porque o *runner* copia a matriz
  para a saída quando ela não existe (`run-rebaseline.sh:427-431`). O manifesto do ensaio já tem a linha do `.tmp`.
- **GOs**: `go piloto 2026-11-05` às 04:03:18.198Z e `go f3 1005` às 04:35:27.083Z, ambos aceitos (`gates.json`), pelo
  laço `goloop-rb.sh` sob a A-1. Nenhum outro *gate*.

## 3. K1..K10 por execução

Papel: (r) recusa, (p) parada, (rep) reportado. "ok" quer dizer que o critério passou. Em K5 das células com falha, o
papel é reportado (DT-006). K9 é parada só em SYNC `none`.

| i | Célula | Veredito | K1 | K2 | K3 | K4 | K5 | K6 | K7 | K8 (p) | K9 | K10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1000 | S2-sync-F1 | ACEITA | ok | ok | ok | ok | falso (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1001 | S2-async-none | ACEITA | ok | ok | ok | ok | ok (r) | n/a | ok | ok | ok (rep) | ok (rep) |
| 1002 | S1-sync-F1 | ACEITA | ok | ok | ok | ok | falso (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1003 | S1-async-none | ACEITA | ok | ok | ok | ok | ok (r) | n/a | ok | ok | ok (rep) | ok (rep) |
| 1004 | S2-async-F1 | ACEITA | ok | ok | ok | ok | falso (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1005 | S1-async-F3 | ACEITA | ok | ok | ok | ok | ok (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1006 | S2-async-F3 | ACEITA | ok | ok | ok | ok | ok (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1007 | S2-sync-F3 | ACEITA | ok | ok | ok | ok | ok (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1008 | S1-async-F1 | ACEITA | ok | ok | ok | ok | falso (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1009 | S1-sync-F3 | ACEITA | ok | ok | ok | ok | ok (rep) | ok | ok | ok | ok (rep) | ok (rep) |
| 1010 | S2-sync-none | ACEITA | ok | ok | ok | ok | ok (r) | n/a | ok | ok | **ok (p)** | ok (rep) |
| 1011 | S1-sync-none | ACEITA | ok | ok | ok | ok | ok (r) | n/a | ok | ok | **ok (p)** | ok (rep) |

Detalhes conferidos:

- **K1.** Portão de 63,4 a 65,1 s com 3 nós HEALTHY e `number_of_nodes=3` nos três *endpoints*, sem `netem`; `Reset:
  depois=0,0` em todas.
- **K2/K3.** `workload_exit=0`; verificador com exit 0 e veredito OK. Em todos os 12 `sanidade.json`, `versao =
  b43-3a-i/1` e limiares 0,95, 0,05 e 2,0 (DT-015; o campo `status` diz "PROVISORIOS", como a DT-015 prevê).
- **K4.** C1 (diferença entre o 1º e o 3º terço) de 0,011 a 0,505 p.p., todas ≤ 1,0. C2 de 5,00 a 5,25 %. C3 dentro do
  IC em todas, com `dif_stdout_csv = 0`. C8 com 200 000 entradas e veredito OK. C3 agregado do piloto: k = 506 437,
  n = 10 120 853, 5,004 % ± 0,023 p.p.
- **K5 nas F1 (reportado).** 1000: `reposicao_erro=1`, `logout_noop=2`, `invalidacao_perdida=1`. 1002: `reposicao_erro=2`,
  `logout_noop=9`. 1004: Validate NOT_FOUND 155 (C4), `logout_noop=20`, 2 Logout isolados NOT_FOUND. 1008: Validate
  NOT_FOUND 94, `logout_noop=69`, 11 Logout isolados NOT_FOUND, `invalidacao_perdida=1`. Nas células `none` e F3, K5
  passou inteiro.
- **K6 F1.** Disparo a +49,003..+49,004 s, `modo=silencioso`, `eth0 down`, `kill isn2`, `ExitCode=137`, `start isn2`,
  injetor com exit 0. `isn2` parado a +49,21..+49,30 s e reiniciado a +109,54..+109,72 s; *cluster* de volta a 3 nós em
  10,1 a 10,2 s depois do fim da execução.
- **K6 F3.** Disparo a +49,016..+49,036 s, `netem` efetivo a +51,01 s, presente nas amostras de +70 e +280 s, ausente
  depois, `remocao_extra=false`, *dead-man* armado (341 s). *Timers* `tcc-heal-rb-*` vazios ao fim (dado do
  coordenador).
- **K7.** `seed_lido = seed_matriz = S0 + 100·i`, `rep = 1` nas 12; R-S3 sem pares repetidos.
- **K8 (DT-013).** `isn1` e `isn3` com 1 aviso em 1 vida; `isn2` com o total igual ao número de vidas depois de cada F1
  (2, 3, 4 e 5) e 1 na vida atual.
- **K9.** I1 = I4 = 0, nenhum I2 de qualquer classe, `Auditor I2` toda em zero com `sem_marco = 0`,
  `revertido_por_bloqueio = 0` nas 12, inclusive nas duas SYNC `none`, onde o papel é parada.
- **K10 (ASYNC, reportado).** I1 = I4 = 0, nenhum I2, `sem_marco = 0` nas 6 ASYNC. Fora da lista do K10, a 1008
  (S1-async-F1) registrou **1 violação I5** (`sid-005801~1`, réplicas com INVALID/LOGOUT contra VALID/NONE, a
  05:00:31.7Z, no fim da janela). É dado de ASYNC sob F1, reportado, sem efeito em critério.
- **Erros fora dos percentis.** Só nas F1: 1000 com 13 `ERROR_TRANSPORT`; 1002 com 22; 1004 com 2 `ERROR_TRANSPORT` e
  173 NOT_FOUND (155 Validate, 18 Logout); 1008 com 11 e 148 (94 e 54). Nas `none` e F3, nenhum desfecho fora dos
  válidos.

## 4. n por operação, terços de C1 e operações sem p99 (PR §8)

Números do subcomando `piloto` do próprio `rebaseline.py` congelado, rodado sobre os brutos com a escrita desviada
para o *scratchpad*, e conferidos por uma contagem independente do CSV. As duas dão o mesmo resultado.

| i | Célula | Login | Validate | Logout | IncrementFailure | ResetFailures | Validate isolados por terço | Falsa recusa C1 (aprox.) | Sem p99 |
|---|---|---|---|---|---|---|---|---|---|
| 1000 | S2-sync-F1 | 46 874 | 1 526 868 | 62 482 | 15 616 | — | 198 611 / 644 431 / 636 952 | < 10⁻⁴ | — |
| 1001 | S2-async-none | 68 039 | 2 225 872 | 90 690 | 22 831 | — | 713 509 / 721 997 / 722 327 | < 10⁻⁴ | — |
| 1002 | S1-sync-F1 | 218 623 | 658 370 | 306 362 | 87 959 | 44 060 | 47 227 / 200 672 / 191 848 | < 10⁻⁴ | — |
| 1003 | S1-async-none | 354 386 | 1 062 403 | 495 179 | 141 432 | 70 607 | 228 086 / 239 399 / 240 531 | < 10⁻⁴ | — |
| 1004 | S2-async-F1 | 53 273 | 1 744 296 | 70 780 | 17 495 | — | 308 299 / 691 708 / 691 033 | < 10⁻⁴ | — |
| 1005 | S1-async-F3 | 16 211 | 48 270 | 22 634 | 6 466 | 3 151 | 22 045 / 4 933 / 5 079 | **0,0035** | — |
| 1006 | S2-async-F3 | 2 862 | 91 757 | 3 820 | **876** | — | 60 782 / 14 011 / 14 103 | < 10⁻⁴ | IncrementFailure; Logout isolado (959) |
| 1007 | S2-sync-F3 | 2 681 | 87 618 | 3 614 | **908** | — | 56 816 / 13 738 / 14 383 | < 10⁻⁴ | IncrementFailure; Logout isolado (932) |
| 1008 | S1-async-F1 | 271 135 | 811 706 | 379 039 | 108 031 | 54 019 | 90 512 / 225 429 / 224 683 | < 10⁻⁴ | — |
| 1009 | S1-sync-F3 | 10 351 | 31 198 | 14 494 | 4 039 | 2 042 | 13 136 / 3 983 / 3 729 | **0,0151** | — |
| 1010 | S2-sync-none | 60 358 | 1 971 296 | 80 233 | 20 327 | — | 621 972 / 619 469 / 669 497 | < 10⁻⁴ | — |
| 1011 | S1-sync-none | 482 177 | 1 447 921 | 674 966 | 192 922 | 96 478 | 321 208 / 325 838 / 318 698 | < 10⁻⁴ | — |

Leitura (sem olhar latência; os percentis do piloto não foram calculados neste parecer):

- **Sem p99.** O caso previsto pelo PR §8 aconteceu: IncrementFailure em S2 sob F3, nos dois modos (876 e 908, abaixo de
  1 000). Também fica sem p99 a linha secundária de Logout isolado em S2-F3. Na campanha, com n perto do limite, a
  maioria das execuções S2-F3 deve ficar sem p99 de IncrementFailure, e os contrastes F3 − `none` dessa operação em S2
  devem sair "não estimado (n insuficiente)" pelo PR §7. Isso é consequência do laço fechado (C1 §3) e já está
  pré-registrado; não pede DT.
- **C1 sob F3.** O 1º terço concentra 69 % (1005), 68 % (1006), 67 % (1007) e 63 % (1009) dos Validate isolados, porque os
  5 s de +44 a +49 s, antes do `netem`, rodam na vazão sem falha. A chance aproximada de falsa recusa por execução é de
  0,35 % em S1-async-F3 e de 1,5 % em S1-sync-F3. Nas 10 execuções de S1-sync-F3 da campanha, isso dá ~0,15 recusa
  esperada e ~1 % de chance de duas recusas na célula (parada (a)). É aceitável e é uma propriedade da linha do tempo
  da DT-011, não um defeito. Fica registrado para o parecer de cada noite.
- **C1 sob F1.** O 1º terço é o menor (a detecção do FD_ALL3 trava a vazão logo depois de +49 s). A chance de falsa
  recusa continua desprezível.

## 5. Tempos

| Trecho | Medido no piloto |
|---|---|
| Portão K1 | 63,4 a 65,1 s |
| Lançamento → âncora `Warm-up ate` | 2,3 a 4,1 s |
| Lançamento → fim do *workload* | 307,8 a 320,3 s (F3 ~308 s; F1 e `none` 310 a 320 s) |
| Pós-execução (brutos, verificador, `checar`) | 2,0 a 9,1 s |
| Ciclo completo por execução | 374,5 a 392,1 s; média **382,8 s (6,38 min)** |
| Fase (GO → FIM) | 04:03:18 → 05:19:51Z, 76 min 33 s |

O *runner* estima 420 s por execução (`EST_EXEC_S`), então o `fim_estimado` que ele imprime fica ~9 % acima do real.
**Noite 1** (60 execuções): ~6 h 23 min depois do `go noite1`, mais a espera do `go f3 4`. Está abaixo da faixa de 6,5 a
8 h do PR §8. **Noite 2** com até 6 reposições de recusa: ~7 h; com as reposições h4 da C3 §6, mais ~6,4 min por
reposição.

## 6. Tamanho real por execução e decisão da O-6

Tamanho do diretório `exec-*` no host (bytes, todos os arquivos):

| Classe | Execuções (MB) | Média | Taxa de CSV por segundo de janela | Previsão do roteiro da VM §7.2 (central) |
|---|---|---|---|---|
| `none` | 224,5 (S2-async), 207,6 (S1-async), 198,6 (S2-sync), 283,4 (S1-sync) | 228,5 MB | 0,89 MB/s | 210 MB |
| F1 | 153,6, 128,1, 175,6, 158,6 | 154,0 MB (0,67 × `none`) | | 184 MB (0,88 × `none`) |
| F3 | 9,3, 9,1, 8,7, 6,0 | 8,3 MB (0,036 × `none`) | | 11 MB |
| **Rodada** | **1 563 MB = 1,563 GB (1,456 GiB)** | | | 1,62 GB |

- **Cenário: central.** A rodada medida (1,56 GB) fica no cenário central do §7.2 (1,62 GB), longe do alto (2,86 GB).
  Pela regra do §7.5, a O-6 entra só no cenário alto. **Decisão: a O-6 não se aplica. A noite 1 fica com 5 rodadas**,
  como no PR §8.
- **Volume da noite 1**: 5 × 1,563 = **7,82 GB (7,28 GiB)**. O `df` da VM foi de 13 G para 11 G livres durante o piloto,
  coerente com 1,46 GiB de brutos mais *log* de contêiner e *journal*.
- **Regra de GO do §7.5 na VM** (livre ≥ 1,5 × volume da noite): 1,5 × 7,82 GB = **11,73 GB (10,92 GiB)**. A VM tem
  "11G" livres pelo `df -h` (GiB, arredondado), ou seja, entre ~10,5 e ~11,5 GiB. **A margem é nula ou negativa**, dentro
  do arredondamento. A conferência tem de ser em bytes (`df -B1 --output=avail /` ≥ 11 730 000 000) na abertura da
  noite. Ressalva R-2 e ponto G-3.
- **Host** (regra 2.5 do plano de execução contínua): livre em `C:` ≥ (42,9 GB − AVHDX) + cópias + 3 GB. Mesmo com o
  AVHDX em zero, dá 47,9 GB contra ~91 GB livres. Passa com folga.
- **Noite 2**: depois da noite 1 a VM fica com ~3,5 GiB livres. A O-3 da noite 1 (X-13) é obrigatória antes da noite 2.

## 7. Paradas (h) e h4

### 7.1 h1, h2 e h3 (C1 §1.3)

| Verificação | Abertura (04:02:47Z) | Fim (05:21:30Z) | Resultado |
|---|---|---|---|
| `boot_id` | `f9e98b74-bf1f-4192-9770-b40285877292` | igual; `journalctl --list-boots` sem *boot* novo | **h1 ok** (o fim do ensaio, segundo o coordenador, tem o mesmo `boot_id`) |
| `isn1` `StartedAt` | 04:01:24.765810852 | igual | **h2 ok**, nenhum evento de `isn1` |
| `isn3` `StartedAt` | 04:01:27.745419488 | igual | **h2 ok**, nenhum evento de `isn3` |
| `isn2` | 04:01:26.009218629 | 04:57:07.454886045 | **h3 ok** (abaixo) |

Eventos de `isn2` na fase: 4 pares `died`/`start`, cada um dentro de uma execução F1. 1000: 04:05:15.285 e 04:06:15.743
(disparo 04:05:15.054). 1002: 04:18:00.411 e 04:19:00.750. 1004: 04:30:56.792 e 04:31:57.218. 1008: 04:56:07.063 e
04:57:07.466. O `StartedAt` final é o do último F1 (1008). O `podman events` do bloco não mostra o código de saída; o
`ExitCode=137` dos quatro `kill` está no `injetor.log` de cada execução (K6). Para a noite 1, o roteiro acrescenta uma
leitura de eventos em JSON com `containerExitCode` (DT-017).

Covariáveis da VM (C1 §2): kernel `6.8.0-147-generic`, podman 4.9.3, os mesmos no `ambiente.md`, na abertura e no fim;
pacotes do kernel segurados; `apt-daily-upgrade.timer` e `unattended-upgrades` desabilitados e inativos. Memória: 6,7 GB
disponíveis na abertura e 2,1 GB no fim (os JVMs dos nós cresceram); *swap* de 6 MB; PSI de memória acumulou 0,21 s na
fase toda. Não preocupa, mas o `free -m` dos blocos da noite 1 deve ser lido.

### 7.2 h4 (`h4-piloto.tsv` e `.json`; DT-018)

- **Veredito h4: 0 de 12.** Cobertura completa (256 amostras por janela), maior intervalo entre amostras de 1,0007 a
  1,0021 s, maior salto de relógio 0,0021 s, pior janela de 10 s de 0,049 a 0,444 s. Parada h4: não. `h4.py fase` com
  rc 0.
- **Conta independente** sobre `amostras-final.txt` (4 698 amostras, 04:03:14 a 05:21:31Z, 1,3047 h): pior janela de 10 s
  da fase inteira **0,501 s** (05:19:47Z, depois da janela da 1011, durante o `checar`), maior intervalo **1,0030 s**,
  maior salto **0,0031 s**, nenhuma janela ≥ 1,1 s, nenhum intervalo ≥ 1,5 s. io `full` total na fase: 31,9 s.
- **Segmento do F1** (C3 §4.2): pior janela dentro do segmento de 0,033 a 0,074 s. O `podman start` nesta VM quase não
  gera io `full`.
- **Conferência do 1,1 s** (C3 §3.4): as 4 `none` (nenhuma h4) tiveram pior janela de 0,097 (1001), 0,049 (1003), 0,085
  (1010) e 0,119 s (1011). Nenhuma entre 1,1 e 1,5 s, então **o limiar de 1,5 s fica**.

### 7.3 Contagem de episódios da DT-019

Base: só o amostrador do piloto (cópia final), 1,304722 h observadas (`episodios.horas_observadas`).

| Contagem | Episódios | Por hora |
|---|---|---|
| Pela letra (trechos de `episodios.lista`) | 0 | 0 |
| Com fusão a < 60 s | 0 | 0 |
| **Com fusão e exclusão R1 (decide)** | **0** | **0** |

A lista está vazia, e a recontagem independente confirma (nenhuma amostra com janela ≥ 1,5 s nem intervalo ≥ 3 s). Fica
abaixo da barreira de 3,5 por hora do C3 §6: **a condição da noite 1 está satisfeita.** Pela R2 da DT-019, o evento
físico de 66 s do ensaio do F1 entra só como contexto.

## 8. `MANIFEST.sha256` com a linha do próprio `.tmp`

**É defeito do *runner* congelado** (`scripts/run-rebaseline.sh` `d95305fe…`, função `gravar_manifesto`, linhas 173-183):

```bash
( cd "${SAIDA}" && find "${alvos[@]}" -type f ! -path "${FASE}/MANIFEST.sha256" -print0 | LC_ALL=C sort -z \
    | xargs -0 sha256sum ) > "${FASE_DIR}/MANIFEST.sha256.tmp" || rc=$?
if [[ "${rc}" -eq 0 ]]; then mv -f "${FASE_DIR}/MANIFEST.sha256.tmp" "${FASE_DIR}/MANIFEST.sha256"
```

O *shell* abre o redirecionamento, e com isso cria `MANIFEST.sha256.tmp`, **antes** de o `find` rodar. O `find` exclui só
`${FASE}/MANIFEST.sha256`, então lista o `.tmp`. O `sha256sum` faz o *hash* do `.tmp` enquanto ele ainda está sendo escrito,
e o `mv` renomeia o arquivo em seguida. A linha passa a apontar para um nome que não existe. O ensaio do passo 6 já tinha a
mesma linha. A noite 1, a noite 2 e as reposições vão ter também (`noite1/MANIFEST.sha256.tmp` e assim por diante).

**Efeito:** `sha256sum -c` sai sempre com rc 1 por causa dessa linha. A integridade dos demais arquivos não é afetada, e o
conjunto coberto é o certo: os 162 arquivos em disco (`piloto/`, `matriz.tsv` e os 12 `exec-*`) estão todos listados e
conferem. Não afeta veredito, K, h4, sementes nem a análise. O `estado.json` da raiz e o `h4/` ficam fora do manifesto
por desenho (um é do `estado`, o outro é do `h4.py`).

**Tratamento:** não corrigir agora. Corrigir mudaria o *hash* da lista `003814e9…`, pediria DT nova e revalidação. Na
conferência, usar `grep -v ' <fase>/MANIFEST.sha256.tmp$' <fase>/MANIFEST.sha256 | sha256sum -c --quiet` (rc 0 no host para
o piloto) e conferir que a linha excluída é exatamente uma. O roteiro da noite 1 já faz isso e também grava o SHA-256 do
`h4/`. Registrar como problema novo em `registro-de-problemas.md` e corrigir depois da campanha, junto da D15/G5 (excluir o
`.tmp` no `find`, ou gerar o `.tmp` fora de `${SAIDA}`).

## 9. Ressalvas

**R-1. Covariáveis do host (C3 §7 e G-3 da X-08v).**
- O roteiro do piloto pedia H0 estrito, em que o `typeperf` e o vigia do coordenador são as únicas atividades no host.
  O `typeperf` por processo mostra o processo `VALORANT-Win64-Shipping` (um jogo) **ativo de ~03:53 a 05:13Z**, com
  pausa de ~04:17 a 04:20Z e E/S de processo de 1 a 2 GB/min. Pela taxa de leitura do `C:` no mesmo período (~10 MB/min),
  essa E/S quase toda não é de disco, mas o jogo é carga de CPU e GPU que o contador não mede.
- O log `Microsoft-Windows-TaskScheduler/Operational` estava desligado na abertura e foi ligado **durante a fase** (o
  primeiro evento é de 04:16:51Z).
- **Não há bloco de fim do host** (só `piloto-host-inicio-20261105T040247Z.txt`). Os itens de C3 §7 que exigem PowerShell
  elevado (exclusão do Defender, *checkpoint*) não constam dos brutos que recebi.
- Efeito no piloto: nenhum observável. O amostrador ficou limpo, com pior janela de 0,50 s. Os dados do piloto nunca
  entram em resultado.
- Efeito na campanha: a noite 1 vai rodar, como deve, em H0 estrito (sem jogo) e com o log já ligado. São duas covariáveis
  com valor diferente do da abertura do piloto, e o C3 §7 manda levar isso ao Gestor antes da fase seguinte.

**R-2. Disco da VM para a noite 1.** Pela regra do §7.5, a noite precisa de ≥ 11,73 GB livres, e a VM tem ~11 GiB (seção 6).
Sem liberar espaço, o GO pode falhar por algumas centenas de MB. A O-6 não resolve porque não é o caso dela (cenário
central). A saída proporcional é a O-3 do próprio piloto na VM: `gzip -1` dos 12 `rep-001.csv` do piloto, conferidos contra
o manifesto, libera ~1,2 GB. A cópia de referência já está no host, com pacote `e3955c27…` e manifesto 162/162. É escrita
na VM sobre brutos e pede autorização (G-3).

**R-3. `MANIFEST.sha256` (seção 8).** Defeito cosmético do *runner* congelado, com contorno no roteiro e correção depois da
campanha.

Observações sem efeito em decisão:

- O `inject-jitter.sh` grava todas as linhas do `injetor.log` com o carimbo do início (04:37:21Z), inclusive a remoção
  feita 251 s depois. O K6 não depende disso (usa as amostras do *runner*). O carimbo por linha no injetor de F3 já está
  adiado para depois da campanha (D4/Q3 da DT-017).
- No `h4-piloto.json`, a `nota` da parada traz "Â§" no lugar de "§" (dupla codificação). Cosmético.
- O pedido fala em `--fase campanha`. O *runner*, o vigia e o `h4.py` só aceitam `noite1`, `noite2` e `reposicoes`
  (`run-rebaseline.sh:21`, `vigia-h4.sh:55`, `h4.py:70`). A matriz chama a fase de "campanha" e a noite de `noite1`.
  O GO pedido será `go noite1 <data>`. O roteiro usa `noite1`.

## 10. Liberação da campanha

Pela A-7, a liberação **não** é do coordenador, porque o parecer tem ressalvas. A recomendação do Senior ao Gestor é
**liberar a noite 1**, condicionada a:

1. **H0 estrito** do `iniciar` do amostrador até o `parar`, sem jogo e sem aplicativo do usuário (além do `typeperf` e do
   vigia), sem Maven, testes, Podman local nem cópia no host. O estado do host é declarado no bloco de abertura do host.
2. **Covariáveis do host declaradas na abertura e no fim** da noite 1, inclusive o log do TaskScheduler (recomendo mantê-lo
   ligado até o fim da campanha) e o estado do serviço Windows Search (G-2).
3. **Disco:** `df -B1` da VM ≥ 11 730 000 000 bytes no início, depois da O-3 do piloto se o Gestor a autorizar (G-3).
4. **Fase `noite1`**, com o mesmo *cluster* (`--cluster-criado-apos 2026-11-05T04:01:07Z`), `boot_id` `f9e98b74-…` e
   `StartedAt` de `isn1`/`isn2`/`isn3` iguais aos do fim do piloto (h1 entre fases). Qualquer diferença, a noite não começa.

## 11. Pontos para o Gestor

| # | Pergunta | Recomendação |
|---|---|---|
| G-1 | Libera a noite 1 com as condições da seção 10? | **Sim.** O piloto passou em tudo o que mede o *grid* e o *runner*; as ressalvas são de host e de disco e têm resposta antes do GO |
| G-2 | Covariáveis do host a partir da noite 1: (a) H0 estrito sem jogo; (b) log do TaskScheduler **ligado** até o fim da campanha; (c) Windows Search: parar o serviço `WSearch` durante as noites (PowerShell elevado: `Stop-Service WSearch`; voltar com `Start-Service WSearch` de dia)? | (a) sim; (b) sim, porque é diagnóstico e não pesa; (c) **sim**. O `SearchIndexer` escreveu ~4 GB em 40 s às 05:07:50Z, durante a 1010, e foi o único processo que levou o `C:` a 0,5 % ocioso na fase. A VM não sentiu (janela de 10 s de no máximo 0,22 s nesse trecho), mas é o candidato mais forte a interferência de disco. Todas as três ficam declaradas como covariável |
| G-3 | Autoriza a O-3 do piloto na VM antes da noite 1 (`gzip -1` dos 12 `rep-001.csv`, conferidos contra o manifesto antes e depois, e `fstrim`)? | **Sim**, e fazer sempre, não só se o `df` falhar. Dá ~1,2 GB de folga numa regra que hoje passa no limite. O `rebaseline.py estado` lê só o `checagem.json` do piloto (R-S3 inclusive), então o *runner* da noite 1 não é afetado |
| G-4 | Aceita registrar o defeito do `MANIFEST.sha256.tmp` como problema novo, com o contorno do roteiro durante a campanha e a correção depois dela? | **Sim** |
| G-5 | Aceita o bloco de fim do host do piloto como ausente e declarado (log ligado às 04:16:51Z pelo primeiro evento, `C:` com ~91 GB livres), sem refazer o piloto? | **Sim.** O piloto não entra em resultado, e a falta não muda nenhuma leitura deste parecer |

## 12. O `typeperf` por processo e a tarefa periódica de ~27 min

Arquivo `diag-io/host-proc-io.csv` (559 contadores a 2 s, 03:53 a 05:31Z; 2 307 amostras na fase), cruzado com o log do
TaskScheduler (ligado a partir de 04:16:51Z) e com o amostrador da VM.

- **Travamento do disco do host durante o piloto: não houve.** Ociosidade do `C:` com mediana de 97,1 %. Latência de escrita
  com mediana de 0,16 ms e máximo de 147 ms (uma amostra às 04:42Z, no lançamento da 1006); 7 amostras acima de 50 ms e
  nenhuma acima de 200 ms. O `C:` ficou abaixo de 10 % ocioso em uma única amostra (05:08:25Z, 0,45 %). Maior lacuna do
  próprio `typeperf` na fase: 3,84 s (no ensaio, foram 29 s). Nada parecido com o episódio do ensaio (66 s a 0 %, escritas
  de 78 a 210 ms).
- **Picos que aparecem e o que são:**
  - Escritas de 130 a 280 MB ao fim de cada execução `none` e F1 (04:09, 04:15-16, 04:22, 04:28, 04:35, 05:00, 05:13,
    05:19Z) são o *flush* do CSV da própria VM. Os tamanhos batem com os `rep-001.csv`.
  - O `SearchIndexer` escreveu ~4 GB de 05:07:50 a 05:08:29Z (até 362 MB/s, latência ≤ 8,8 ms). Não há tarefa agendada
    nesse instante. É o serviço de indexação, e o caso está na G-2.
  - As quedas de ociosidade de 04:16, 04:26 e 05:13Z coincidem com disparos do `MSI Task Host - AI Engine OSD` (11 na
    fase, em horários irregulares e, ao que parece, ligados a abrir e fechar o jogo: o processo do jogo some às 04:17 e às
    05:13Z). Não têm período.
  - Depois das 05:21Z, a cópia do pacote para o host e a indexação dos arquivos copiados derrubaram o `C:` a 0 % com latência
    de até 623 ms. Isso já estava fora do H0 e é mais um motivo para a G-2(c).
- **Tarefa periódica de ~27 min: não identificada, e não se manifestou.** Projetando o período de ~27 min do ensaio
  (23:58, 00:25, 00:52, ~03:08Z), os próximos episódios cairiam por volta de 03:36, 04:03, 04:30, 04:57 e 05:24Z. Em 04:03 e
  04:30Z o `C:` estava 81 % e 90 % ocioso. Em 04:57Z houve só uma subida leve de escrita (ociosidade mínima de 61 %), que
  coincide com o reinício do `isn2` da 1008 e com o `QueueReporting` do WER às 04:57:38Z. Entre 04:16 e 05:21Z, as únicas
  tarefas com período fixo no log são as `SoftLanding*DeferralTask` (a cada 15 min, sem efeito no disco). Os demais
  disparos são isolados: `OneSettings\RefreshCache`, os atualizadores do Edge, do Google e do Brave e o WER. Em 78 min sem
  nenhum episódio, a hipótese de uma tarefa de 27 min que satura o disco perde força, mas não fica refutada, porque o log só
  cobre 65 min da fase. O log ligado na noite 1 dá a próxima chance de cruzar.

## Análise do Senior (formato da frente prática)

### Contexto identificado no TCC
O *rebaseline* substitui as Tabelas 5 e 6 da monografia (p99 por operação sob F1 e F3, em DIST_SYNC e DIST_ASYNC). O
piloto só valida *runner*, critérios, tempos e disco. Nunca entra em resultado (PR §8).

### Requisitos práticos identificados
Os da PR §8: K1..K10, n por operação, tempos e tamanho. Mais o C1 §1.3 (h1..h3), o C3 (h4, 1,1 s, episódios da DT-019),
a O-6 pela 7.5 do roteiro da VM e a A-7.

### Planejamento proposto
A noite 1 pelo roteiro `docs/roteiro-noite1-2026-11-05.md`, depois das decisões G-1..G-5. A O-3 da noite 1 e o parecer
da fase seguem como X-13.

### Branches sugeridas
Nenhuma agora. Depois da campanha: `fix/runner-manifest-tmp` (seção 8) e `fix/inject-jitter-carimbo` (D4).

### Critérios de aceite
Os desta X-11 estão cumpridos com as ressalvas R-1 a R-3. Para a noite 1: a linha `FIM exit=0 motivo=fase noite1
concluida`, o manifesto com só a linha do `.tmp` falhando, o bloco de fim da VM e o do host, e o `h4.py fase --fase
noite1` com rc 0 ou 30 lido pelo Senior no X-13.

### Pontos que precisam de validação do gestor
G-1 a G-5 (seção 11).

### Próxima tarefa sugerida para o Worker
Nenhuma antes da noite 1, porque os arquivos do *bundle* estão congelados. Depois da campanha, `fix/runner-manifest-tmp`:
excluir `${FASE}/MANIFEST.sha256.tmp` no `find` de `gravar_manifesto`, com um teste no `run-rebaseline.test.sh` que rode
`sha256sum -c` sobre o manifesto e exija rc 0.
