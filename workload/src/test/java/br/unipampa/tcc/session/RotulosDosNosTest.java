package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * W-2a (B-44.2), decisao do Gestor de 05/11 (item 4): o rotulo de um no e o mesmo em toda peca do controlador,
 * {@code host:porta} da URL de {@code --controller-admin}. A sonda de topologia ({@link ConsultaSaudeRest}) e o
 * atuador ({@link AtuadorRestInfinispan}) tem de concordar, senao o AC-5 nunca acha o alvo e o AC-2 nunca acha um
 * no que respondeu. <b>Requisito do W-4:</b> o {@code noSuspeito} do S_lag usa este mesmo rotulo
 * ({@link #rotuloDoNo(String)}); um teste do W-4 deve chamar esta funcao ou comparar com ela.
 */
class RotulosDosNosTest {

    /** O rotulo canonico: a URL normalizada por {@code LigacaoControlador.urlsAdmin}, sem o esquema. */
    static String rotuloDoNo(String urlAdmin) {
        return AtuadorRestInfinispan.rotulo(urlAdmin);
    }

    @Test
    @DisplayName("sonda e atuador dao o mesmo rotulo para cada forma aceita de --controller-admin")
    void sondaEAtuadorConcordam() {
        for (String admin : new String[] {
                "127.0.0.1:12501,127.0.0.1:12502,127.0.0.1:12503",
                "http://127.0.0.1:12501/, http://127.0.0.1:12502 ,127.0.0.1:12503",
                "isn1:11222,isn2:11222,isn3:11222",
                "http://localhost:11222,http://localhost:11223,http://localhost:11224/"}) {
            List<String> urls = LigacaoControlador.urlsAdmin(admin);
            ConsultaSaudeRest sonda = new ConsultaSaudeRest(urls, "admin", "x");
            AtuadorRestInfinispan atuador = new AtuadorRestInfinispan(urls, new ClienteAdminRest("admin", "x"),
                    Set::of, 1);
            List<String> daSonda = new ArrayList<>();
            List<String> canonicos = new ArrayList<>();
            for (int i = 0; i < sonda.nos(); i++) {
                daSonda.add(sonda.rotulo(i));
                canonicos.add(rotuloDoNo(urls.get(i)));
            }
            assertEquals(daSonda, atuador.rotulos(), "sonda x atuador para " + admin);
            assertEquals(daSonda, canonicos, "sonda x rotulo canonico (W-4) para " + admin);
        }
    }

    @Test
    @DisplayName("o rotulo e host:porta, sem esquema nem barra")
    void forma() {
        assertEquals("127.0.0.1:12503", rotuloDoNo("http://127.0.0.1:12503"));
        assertEquals(List.of("127.0.0.1:12501"), new AtuadorRestInfinispan(List.of("http://127.0.0.1:12501/"),
                new ClienteAdminRest("a", "b"), Set::of, 1).rotulos());
    }
}
