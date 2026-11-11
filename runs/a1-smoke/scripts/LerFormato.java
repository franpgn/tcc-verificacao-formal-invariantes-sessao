import br.unipampa.tcc.session.SessionState;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.commons.configuration.ClassAllowList;
import java.util.HexFormat;

/** A1: desserializa, com as classes e o marshaller do cliente, o hex que a politica registrou no servidor (A1-FORMATO). */
public class LerFormato {
    public static void main(String[] a) throws Exception {
        JavaSerializationMarshaller m = new JavaSerializationMarshaller(
                new ClassAllowList(java.util.List.of("br.unipampa.tcc.session.*", "java.time.*")));
        for (String hex : a) {
            Object o = m.objectFromByteBuffer(HexFormat.of().parseHex(hex));
            SessionState s = (SessionState) o;
            System.out.println("ok classe=" + o.getClass().getName() + " id=" + s.id() + " identidade=" + s.identidade()
                    + " estado=" + s.estado() + " motivo=" + s.motivo() + " versao=" + s.versao()
                    + " criadaEm=" + s.criadaEm() + " atualizadaEm=" + s.atualizadaEm());
        }
    }
}
