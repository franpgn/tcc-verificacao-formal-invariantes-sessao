package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27.3 (W-1, R-C2): sem o portao de emissao, toda acao de S3 grava {@code espera_portao_ns = 0}
 * (coluna reservada do contrato S3 v2, mantida com 10 colunas), inclusive as que retentaram com
 * <i>backoff</i>; e nenhuma classe do cenario S3 recebe ou guarda um tipo de {@code controle}.
 */
class S3SemPortaoTest {

    private static final String CONTROLE = "br.unipampa.tcc.controle.";

    @Test
    @DisplayName("R-C2: S3 com relogio falso e falha injetada: toda acao (e toda linha do CSV) tem espera_portao_ns = 0")
    void esperaPortaoSempreZero() throws Exception {
        MundoS3 m = new MundoS3(31, null);
        m.foraDoAr(2, 100, 160);
        m.rodar(400);
        List<AcaoS3> acoes = m.registro.acoes();
        assertTrue(acoes.size() > 300, "a simulacao produziu acoes: " + acoes.size());
        assertTrue(acoes.stream().anyMatch(a -> a.tentativas() > 1), "houve retentativas (backoff exercitado)");
        Set<String> operacoes = new TreeSet<>();
        for (AcaoS3 a : acoes) {
            assertEquals(0L, a.esperaPortaoNs(), a.toString());
            operacoes.add(a.operacao());
        }
        assertTrue(operacoes.containsAll(Set.of("Block", "Unblock", "IncrementFailure", "ResetFailures")),
                "as administrativas aparecem: " + operacoes);

        Path tmp = Files.createTempFile("s3-acoes", ".csv");
        try {
            m.registro.escreverCsv(tmp, null);
            List<String> linhas = Files.readAllLines(tmp, StandardCharsets.UTF_8);
            assertEquals(AcaoS3.HEADER, linhas.get(0));
            assertEquals(10, linhas.get(0).split(",", -1).length, "contrato S3 v2: 10 colunas");
            for (String l : linhas.subList(1, linhas.size())) {
                String[] c = l.split(",", -1);
                assertEquals(10, c.length, l);
                assertEquals("0", c[9], l);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("R-C2: AplicacaoS3, ContextoS3 e UsuarioVirtual nao tem construtor nem campo de tipo de controle")
    void semTipoDeControleNoCenarioS3() {
        List<String> achados = new ArrayList<>();
        for (Class<?> c : new Class<?>[] {AplicacaoS3.class, ContextoS3.class, UsuarioVirtual.class}) {
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                for (Class<?> t : k.getParameterTypes()) {
                    if (t.getName().startsWith(CONTROLE)) {
                        achados.add(c.getSimpleName() + "(" + t.getSimpleName() + ")");
                    }
                }
            }
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getName().startsWith(CONTROLE)) {
                    achados.add(c.getSimpleName() + "." + f.getName());
                }
            }
        }
        assertEquals(List.of(), achados);
    }
}
