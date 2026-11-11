package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.8: contrato de {@link ArmazemVersionado} (no duble {@link FakeVersionado}) e guarda textual do
 * adaptador de producao (W6, parte do adaptador): ele chama {@code getWithMetadata} e
 * {@code replaceWithVersion} do Hot Rod. O {@code RemoteCache} nao pode ser dublado por
 * {@code Proxy} (ver {@link Armazem}), por isso a guarda e no fonte sem comentarios.
 */
class ArmazemVersionadoTest {

    static String fonteSemComentarios(String arquivo) throws Exception {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/session/" + arquivo);
        return DetectorFonte.semComentarios(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    @Test
    @DisplayName("W6: o adaptador de producao usa getWithMetadata e replaceWithVersion (fonte sem comentarios)")
    void adaptadorUsaHotRodVersionado() throws Exception {
        String s = fonteSemComentarios("ArmazemVersionado.java");
        assertTrue(s.contains("cache.getWithMetadata(chave)"), "lerComVersao sem getWithMetadata");
        assertTrue(s.contains("m.getVersion()"), "versao nao vem do MetadataValue");
        assertTrue(s.contains("return cache.replaceWithVersion(chave, novo, versao);"),
                "substituirSeVersao sem replaceWithVersion");
        assertTrue(s.contains("static <V> ArmazemVersionado<V> de(RemoteCache<String, V> cache)"));
    }

    @Test
    @DisplayName("duble: versao muda a cada escrita; escrita condicional com versao velha falha sem escrever")
    void contratoDoDuble() {
        FakeVersionado<String> a = new FakeVersionado<>();
        assertNull(a.lerComVersao("k"));
        assertFalse(a.substituirSeVersao("k", "x", 1), "chave ausente");
        a.put("k", "v1");
        ArmazemVersionado.Versionado<String> l1 = a.lerComVersao("k");
        assertEquals("v1", l1.valor());
        a.put("k", "v2");
        ArmazemVersionado.Versionado<String> l2 = a.lerComVersao("k");
        assertNotEquals(l1.versao(), l2.versao());
        assertFalse(a.substituirSeVersao("k", "velha", l1.versao()));
        assertEquals("v2", a.get("k"));
        assertTrue(a.substituirSeVersao("k", "v3", l2.versao()));
        assertEquals("v3", a.get("k"));
        assertFalse(a.substituirSeVersao("k", "v4", l2.versao()), "a versao usada ja foi consumida");
        assertEquals(1, a.escritasCondicionaisAplicadas.get());
        assertEquals(4, a.tentativasCondicionais.get());
    }

    @Test
    @DisplayName("duble W5: sempreConflita muda a versao sem mudar o valor")
    void sempreConflita() {
        FakeVersionado<String> a = new FakeVersionado<>();
        a.semear("k", "v");
        a.sempreConflita = true;
        long v0 = a.lerComVersao("k").versao();
        assertFalse(a.substituirSeVersao("k", "x", v0));
        assertEquals("v", a.get("k"));
        assertNotEquals(v0, a.lerComVersao("k").versao());
    }
}
