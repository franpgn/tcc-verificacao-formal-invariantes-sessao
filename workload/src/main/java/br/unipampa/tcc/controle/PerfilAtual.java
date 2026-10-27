package br.unipampa.tcc.controle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * B-27: o perfil em vigor no cliente (escrito pelo laco de controle do B-28.2, lido pelo
 * {@link PortaoDeEmissao}). Comeca em {@link Perfil#NORMAL}. Os ouvintes sao chamados na thread
 * de quem definiu, depois da escrita e fora de qualquer lock (o portao toma o proprio lock dentro
 * do seu ouvinte), uma vez por mudanca efetiva.
 */
public final class PerfilAtual {

    private final AtomicReference<Perfil> atual = new AtomicReference<>(Perfil.NORMAL);
    private final List<Runnable> ouvintes = new CopyOnWriteArrayList<>();

    public Perfil atual() {
        return atual.get();
    }

    /** Define o perfil; notifica os ouvintes so se mudou. */
    public void definir(Perfil novo) {
        if (novo == null) {
            throw new IllegalArgumentException("perfil nulo");
        }
        Perfil antes = atual.getAndSet(novo);
        if (antes != novo) {
            for (Runnable r : ouvintes) {
                r.run();
            }
        }
    }

    /** Registra um ouvinte de mudanca de perfil. */
    public void registrar(Runnable ouvinte) {
        if (ouvinte == null) {
            throw new IllegalArgumentException("ouvinte nulo");
        }
        ouvintes.add(ouvinte);
    }
}
