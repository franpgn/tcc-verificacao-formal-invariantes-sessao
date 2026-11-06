package br.unipampa.tcc.servidor;

import org.jboss.logging.Logger;

/**
 * A1: para onde vao as decisoes da politica. No servidor, o log do Infinispan (jboss-logging, categoria
 * {@code br.unipampa.tcc.servidor.PoliticaFusaoSessoes}): decisoes em INFO, saida de seguranca em WARN. Nos testes,
 * um registro em memoria.
 */
interface RegistroFusao {

    /** Uma linha por chave em conflito. */
    void registrar(Decisao d);

    /** Formato do primeiro valor recebido (classe e bytes), uma vez por instancia da politica. */
    default void formato(String linha) { }

    /** Politica instanciada pelo parser do servidor (uma linha por no e por criacao do cache). */
    default void carregada(String linha) { }

    /** Registro do servidor. */
    static RegistroFusao log() {
        return new RegistroFusao() {
            private final Logger log = Logger.getLogger(PoliticaFusaoSessoes.class.getName());

            @Override
            public void registrar(Decisao d) {
                if (d.resultado() == Decisao.Resultado.SAIDA_SEGURANCA) {
                    log.warn(d.linha());
                } else {
                    log.info(d.linha());
                }
            }

            @Override
            public void formato(String linha) {
                log.info(linha);
            }

            @Override
            public void carregada(String linha) {
                log.info(linha);
            }
        };
    }
}
