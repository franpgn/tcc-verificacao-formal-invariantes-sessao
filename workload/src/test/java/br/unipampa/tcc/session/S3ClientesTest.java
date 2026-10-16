package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B-33 (E2): configuracao dos clientes Hot Rod do S3 (pinados, timeouts curtos, console). */
class S3ClientesTest {

    private static final List<S3Clientes.HostPort> TRES =
            S3Clientes.servidores("10.0.0.1:11222,10.0.0.2:11222,10.0.0.3:11222");

    @Test
    @DisplayName("pinado: um unico servidor, ClientIntelligence.BASIC, timeouts de 2 s e 1 retentativa")
    void pinado() {
        Configuration c = S3Clientes.configuracaoPinned(TRES.get(1), "admin", "x", new S3Parametros());
        assertEquals(1, c.servers().size());
        assertEquals("10.0.0.2", c.servers().get(0).host());
        assertEquals(ClientIntelligence.BASIC, c.clientIntelligence());
        assertEquals(2000, c.socketTimeout());
        assertEquals(2000, c.connectionTimeout());
        assertEquals(1, c.maxRetries());
    }

    @Test
    @DisplayName("timeouts e retentativas Hot Rod sao parametros; max_retries do S3 difere do padrao da biblioteca")
    void timeoutsParametrizados() {
        S3Parametros p = new S3Parametros();
        p.hotrodTimeoutMs = 750;
        p.hotrodRetentativas = 3;
        Configuration c = S3Clientes.configuracaoPinned(TRES.get(0), "admin", "x", p);
        assertEquals(750, c.socketTimeout());
        assertEquals(3, c.maxRetries());
        Configuration padrao = new org.infinispan.client.hotrod.configuration.ConfigurationBuilder()
                .addServer().host("h").port(1).build();
        // conferido contra a biblioteca (infinispan-client-hotrod 15.0.0.Final): socket e connect
        // timeout ja sao 2000 ms por padrao; o que o S3 muda de fato e max_retries
        System.out.println("PADROES-HOTROD socketTimeout=" + padrao.socketTimeout()
                + " connectionTimeout=" + padrao.connectionTimeout() + " maxRetries=" + padrao.maxRetries());
        assertNotEquals(padrao.maxRetries(), new S3Parametros().hotrodRetentativas,
                "padrao da biblioteca: " + padrao.maxRetries());
    }

    @Test
    @DisplayName("modo topology: todos os servidores, cliente topology-aware (contraste), mesmos timeouts")
    void topology() {
        Configuration c = S3Clientes.configuracaoTopology(TRES, "admin", "x", new S3Parametros());
        assertEquals(3, c.servers().size());
        assertNotEquals(ClientIntelligence.BASIC, c.clientIntelligence());
        assertEquals(2000, c.socketTimeout());
    }

    @Test
    @DisplayName("--admin-node: isnK escolhe o K-esimo de --servers; host:porta literal; invalido e erro")
    void noAdmin() {
        assertEquals(TRES.get(0), S3Clientes.resolverNoAdmin("isn1", TRES));
        assertEquals(TRES.get(2), S3Clientes.resolverNoAdmin("isn3", TRES));
        assertEquals(new S3Clientes.HostPort("1.2.3.4", 11224), S3Clientes.resolverNoAdmin("1.2.3.4:11224", TRES));
        assertThrows(IllegalArgumentException.class, () -> S3Clientes.resolverNoAdmin("isn4", TRES));
        assertThrows(IllegalArgumentException.class, () -> S3Clientes.resolverNoAdmin("isn0", TRES));
        assertThrows(IllegalArgumentException.class, () -> S3Clientes.resolverNoAdmin("isn", TRES));
        assertThrows(IllegalArgumentException.class, () -> S3Clientes.servidores("semporta"));
    }
}
