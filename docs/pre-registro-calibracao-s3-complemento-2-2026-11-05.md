# Complemento 2 ao pré-registro da calibração S3: efeito da regra de memória no portão (2026-11-05)

> Senior, 2026-11-05, antes de qualquer dado da calibração (X-18 previsto para ~01:30Z de 06/11). Só documentos.
> Complementa a DT-020 (`docs/pre-registro-calibracao-s3-2026-11-05.md`) e é registrado como **DT-023**. O documento
> original não é editado além de notas de remissão; onde divergirem, vale este.
>
> Origem: o Senior do roteiro apontou que a §5 do pré-registro ainda manda **parar a fase** quando o *swap* passa do
> limite, enquanto a C-10 aprovada (G-3 do Gestor, nota de fechamento da DT-020, `2c8b7b8`) segue a G-3(b): recriar o
> *cluster* e rodar a execução com o mesmo i e a mesma semente, sem contar como recusa
> (`validacao-noite1-2026-11-05.md:647`; DT-014, item 1). O W-6c implementa assim (`wt-w6c`,
> `feature/w6c-f3-memoria`, `docs/implementacao.md` TD item 22 e `scripts/run-campanha-s3.sh:59-63`, opção
> `--swap-limite-kb`; P-56 do W-6c).

## Regra (substitui a frase "*swap* ≥ 1 GiB para a fase (exit 10)" da §5 e a "parada ≥ 1 GiB" do item 3 da §9)

1. No portão de cada execução, depois do K1, o orquestrador lê o *swap* usado (`SwapTotal − SwapFree` de
   `/proc/meminfo`) e grava em `execucao.json` (`swap_usado_kb_k1`, `swap_portao`).
2. Com *swap* ≥ **1 048 576 kB** (1 GiB, C-10, origem medida na §5 do pré-registro), o *cluster* é **recriado de novo**
   pelo mesmo caminho (`down`/`up`, seis *caches*, camada 3, K1), e a execução roda em seguida **com o mesmo i e a mesma
   semente, sem contar como recusa**. A carga ainda não foi lançada, então nenhum dado da execução existe nesse ponto.
3. Uma recriação por portão. ~~Se o *swap* continuar ≥ 1 GiB depois dela, a execução roda com o aviso.~~
   **Substituído pela decisão do P-56 (seção abaixo): se o *swap* continuar ≥ 1 GiB depois da recriação, a fase para,
   com código de saída próprio.** As leituras ficam em `swap_portao.leituras_kb`.
4. Linha de comando: `--swap-limite-kb 1048576`. Com a opção, o pré-voo exige `/proc/meminfo` legível (exit 4).
5. ~~Fica em aberto o caso do item 3 em que a segunda leitura repete a primeira (P-56 do W-6c).~~ **Decidido**: ver a
   seção abaixo.

## Decisão do P-56 (coordenador, 2026-11-05, antes de qualquer dado)

**Se o *swap* continuar ≥ 1 048 576 kB na leitura depois da recriação, a fase para**, com um código de saída próprio do
orquestrador (o W-6c fixa o valor, distinto dos códigos que já existem, e o registra no cabeçalho do *script*), e não
roda com aviso. A execução não é lançada; volta com o mesmo i e a mesma semente quando a fase for retomada (DT-014,
item 1).

**Origem (medida na noite 1 do *rebaseline*):** as quatro recriações do *cluster* baixaram o *swap* para 39 a 51 MB.
Na preparação do segmento 2, de 2 576 MB para 39 MB (`dados-vm/rebaseline-2026-11/noite1-completa-20261105/
rebaseline-infra/prep-seg2-20261105T114805Z.log`, linhas 2 e 55). No segmento 2, para 47, 45 e 51 MB nas recriações
das i = 33, 40 e 50 (`supervisor-n1s2b.log`, linhas 27, 54 e 82, mesmo diretório). *Swap* alto logo depois de recriar,
portanto, indica outra causa que não as JVMs dos servidores, e pede olhar humano antes de seguir. O `swapoff`/`swapon`
no portão não foi adotado.

*Precisão sobre o §5 do pré-registro:* os 2 576 MB acima, lidos às 11:48Z antes da recriação do segmento 2, são o
valor no *host* que corresponde aos "~2,5 GB" informados pelo coordenador para a i = 17. São de depois da parada, não do
instante da falha.

## O que não muda

O limiar de 1 GiB e a sua origem (§5 do pré-registro, C-10), o registro do *swap* por execução, e as demais paradas da
fase listadas na §5 (K1, nó do F1 que não volta, *cluster* sem 3 HEALTHY, disco, `boot_id`, h4). O B-40 (DT-021, §9) remete
a esta regra e herda o mesmo efeito.
