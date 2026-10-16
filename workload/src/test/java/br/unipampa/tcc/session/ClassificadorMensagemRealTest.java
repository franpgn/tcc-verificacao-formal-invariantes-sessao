package br.unipampa.tcc.session;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * ESQUELETO (C-2 da validacao V-E1; item rastreavel RAST-G3-1 no relatorio B-33, parte E2).
 *
 * <p>As marcas de {@link ClassificadorDeErro#MARCAS_DE_INDISPONIBILIDADE} e os nomes de campo
 * lidos por {@link HealthRest} vem da documentacao do Infinispan, nao de captura real; os testes
 * atuais usam mensagens fabricadas. No gate G-3 (T-B), com um no isolado em DEGRADED, capturar:
 * (1) a excecao exata (classe, mensagem e cadeia de causas) que o cliente Hot Rod devolve a um
 * {@code get}/{@code put}/{@code entrySet}; (2) o JSON real de saude do REST. Entao: preencher as
 * constantes abaixo, remover o {@code @Disabled} e ajustar a lista de marcas e o parser. Sem este
 * teste preenchido, T-F nao pode alegar H-L7 (distincao entre recusa por indisponibilidade e
 * timeout).</p>
 */
class ClassificadorMensagemRealTest {

    /** A PREENCHER NO G-3: classe e mensagem capturadas, e a cadeia de causas (classe: mensagem). */
    static final String CLASSE_CAPTURADA = "<preencher: classe da excecao no cliente>";
    static final String MENSAGEM_CAPTURADA = "<preencher: mensagem real>";

    @Test
    @Disabled("RAST-G3-1: preencher com a excecao real capturada no gate G-3 (T-B) e habilitar")
    void excecaoRealDeNoDegradedViraErrorUnavailable() {
        // Esqueleto: construir a excecao com a classe/mensagem/causas capturadas e afirmar
        // ClassificadorDeErro.classificar(e).code() == OpResult.Code.ERROR_UNAVAILABLE; e uma
        // excecao real de timeout (SocketTimeout) NAO pode virar ERROR_UNAVAILABLE.
        org.junit.jupiter.api.Assertions.fail("esqueleto nao preenchido: " + CLASSE_CAPTURADA + " / " + MENSAGEM_CAPTURADA);
    }

    @Test
    @Disabled("RAST-G3-1: preencher com o JSON real de saude do REST capturado no gate G-3 e habilitar")
    void jsonRealDeSaudeEhInterpretadoPeloHealthRest() {
        org.junit.jupiter.api.Assertions.fail("esqueleto nao preenchido: colar o JSON real do REST de saude em DEGRADED");
    }
}
