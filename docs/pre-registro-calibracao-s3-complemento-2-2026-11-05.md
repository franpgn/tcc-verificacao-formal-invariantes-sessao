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
3. Uma recriação por portão. Se o *swap* continuar ≥ 1 GiB depois dela, a execução roda com o aviso, e as leituras
   ficam em `swap_portao.leituras_kb`. **A fase não para por *swap*.**
4. Linha de comando: `--swap-limite-kb 1048576`. Com a opção, o pré-voo exige `/proc/meminfo` legível (exit 4).
5. Fica em aberto, para decisão do Gestor, o caso do item 3 em que a segunda leitura repete a primeira porque o *swap*
   é de outro processo (P-56 do W-6c: manter o aviso, acrescentar `swapoff -a && swapon -a` no portão, ou parar a fase).
   Até essa decisão, vale o item 3. Uma mudança depois de haver dados da calibração vira entrada nova.

## O que não muda

O limiar de 1 GiB e a sua origem (§5 do pré-registro, C-10), o registro do *swap* por execução, e as paradas da fase
listadas na §5 (K1, nó do F1 que não volta, *cluster* sem 3 HEALTHY, disco, `boot_id`, h4). O B-40 (DT-021, §9) remete
a esta regra e herda o mesmo efeito.
