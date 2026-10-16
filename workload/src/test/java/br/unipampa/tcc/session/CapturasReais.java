package br.unipampa.tcc.session;

import java.util.List;

import br.unipampa.tcc.session.OpResult.Code;

/**
 * Fixtures REAIS do C-2 (RAST-G3-1): linhas de erro da sonda Hot Rod ({@link SondaHotrod}) em isn3,
 * copiadas sem alteracao das colunas {@code excecao_classe}, {@code mensagem} e {@code causas} do CSV
 * {@code sonda-isn3.csv}. A coluna {@code linha} e o numero da linha no arquivo (1 = cabecalho) e
 * {@code offset} os segundos desde a primeira linha do arquivo.
 *
 * <p>Fontes: {@code REP3} = {@code runs/gate-b29/g1-rep3/hotrod/sonda-isn3.csv} (wt-gate-v3, 2026-10-16,
 * Infinispan 15.0.0.Final); {@code REP2} = {@code runs/gate-b29/g1-rep2/hotrod/sonda-isn3.csv} (wt-tb,
 * 2026-10-16). Cada fixture e a primeira ocorrencia de (cache, operacao, tipo) no arquivo.
 * {@code esperado} e a decisao de classificacao (nao vem da captura): so as excecoes cuja mensagem do
 * servidor e {@code AvailabilityException} sao {@code ERROR_UNAVAILABLE}.</p>
 */
final class CapturasReais {

    record Captura(String rep, String fase, int linha, String offset, String cache, String operacao,
                   String classe, String mensagem, String causas, Code esperado) {
        @Override
        public String toString() {
            return rep + "/" + fase + "/" + cache + "/" + operacao + " (linha " + linha + ")";
        }
    }

    static final List<Captura> TODAS = List.of(
        new Captura("REP3", "pre-deteccao", 2, "0.0", "sessions", "put",
                "org.infinispan.client.hotrod.exceptions.TransportException",
                "java.net.SocketTimeoutException: PutOperation{sessions, key=sonda-isn3, value=[B0xACED00057372002462722E756E697061..[525], flags=6, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: PutOperation{sessions, key=sonda-isn3, value=[B0xACED00057372002462722E756E697061..[525], flags=6, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "pre-deteccao", 3, "4.0", "sessions", "get",
                "org.infinispan.client.hotrod.exceptions.TransportException",
                "java.net.SocketTimeoutException: GetOperation{sessions, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: GetOperation{sessions, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "pre-deteccao", 4, "8.0", "sessions", "entrySet",
                "java.lang.RuntimeException",
                "java.net.SocketTimeoutException: IterationNextOperation{sessions, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: IterationNextOperation{sessions, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "pre-deteccao", 5, "10.1", "sessions", "remove",
                "org.infinispan.client.hotrod.exceptions.TransportException",
                "java.net.SocketTimeoutException: RemoveOperation{sessions, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: RemoveOperation{sessions, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "pre-deteccao", 6, "14.1", "counters", "put",
                "org.infinispan.client.hotrod.exceptions.TransportException",
                "java.net.SocketTimeoutException: PutOperation{counters, key=sonda-isn3, value=[B0xACED00057372002562722E756E697061..[380], flags=6, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: PutOperation{counters, key=sonda-isn3, value=[B0xACED00057372002562722E756E697061..[380], flags=6, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "pre-deteccao", 7, "18.1", "counters", "get",
                "org.infinispan.client.hotrod.exceptions.TransportException",
                "java.net.SocketTimeoutException: GetOperation{counters, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: GetOperation{counters, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "pre-deteccao", 8, "22.2", "counters", "entrySet",
                "java.lang.RuntimeException",
                "java.net.SocketTimeoutException: IterationNextOperation{counters, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: IterationNextOperation{counters, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "pre-deteccao", 9, "24.2", "counters", "remove",
                "org.infinispan.client.hotrod.exceptions.TransportException",
                "java.net.SocketTimeoutException: RemoveOperation{counters, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: RemoveOperation{counters, key=sonda-isn3, flags=0, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP3", "DEGRADED", 13, "38.2", "sessions", "remove",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005\\t000A\\s\\o\\n\\d\\a\\-\\i\\s\\n\\3 (17 bytes)]' is not available. Not all owners are in this partition",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP3", "DEGRADED", 14, "39.0", "counters", "put",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005\\t000A\\s\\o\\n\\d\\a\\-\\i\\s\\n\\3 (17 bytes)]' is not available. Not all owners are in this partition",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP3", "DEGRADED", 15, "39.0", "counters", "get",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005\\t000A\\s\\o\\n\\d\\a\\-\\i\\s\\n\\3 (17 bytes)]' is not available. Not all owners are in this partition",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP3", "DEGRADED", 16, "39.0", "counters", "entrySet",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000305: Cluster is operating in degraded mode because of node failures.",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP3", "DEGRADED", 17, "39.0", "counters", "remove",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005\\t000A\\s\\o\\n\\d\\a\\-\\i\\s\\n\\3 (17 bytes)]' is not available. Not all owners are in this partition",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP3", "DEGRADED", 18, "39.0", "sessions", "put",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005\\t000A\\s\\o\\n\\d\\a\\-\\i\\s\\n\\3 (17 bytes)]' is not available. Not all owners are in this partition",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP3", "DEGRADED", 19, "39.0", "sessions", "get",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005\\t000A\\s\\o\\n\\d\\a\\-\\i\\s\\n\\3 (17 bytes)]' is not available. Not all owners are in this partition",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP3", "DEGRADED", 20, "39.0", "sessions", "entrySet",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000305: Cluster is operating in degraded mode because of node failures.",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP2", "pre-deteccao", 6, "16.1", "counters", "put",
                "org.infinispan.client.hotrod.exceptions.TransportException",
                "java.net.SocketTimeoutException: PutOperation{counters, key=sonda-isn3, value=[B0xACED00057372002562722E756E697061..[380], flags=6, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                "java.net.SocketTimeoutException: PutOperation{counters, key=sonda-isn3, value=[B0xACED00057372002562722E756E697061..[380], flags=6, connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms",
                Code.ERROR_TRANSPORT),
        new Captura("REP2", "DEGRADED", 19, "58.2", "sessions", "get",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005\\t000A\\s\\o\\n\\d\\a\\-\\i\\s\\n\\3 (17 bytes)]' is not available. Not all owners are in this partition",
                "",
                Code.ERROR_UNAVAILABLE),
        new Captura("REP2", "encerramento", 97, "204.7", "counters", "remove",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "java.lang.InterruptedException",
                "java.lang.InterruptedException",
                Code.ERROR_TRANSPORT),
        new Captura("REP2", "encerramento", 98, "208.1", "sessions", "remove-final",
                "org.infinispan.client.hotrod.exceptions.HotRodClientException",
                "java.lang.InterruptedException",
                "java.lang.InterruptedException",
                Code.ERROR_TRANSPORT)
    );

    private CapturasReais() { }
}
