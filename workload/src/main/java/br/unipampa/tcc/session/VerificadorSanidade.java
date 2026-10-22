package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B-43.3a (parte i): verificador OFFLINE de sanidade da mistura, modo strict.
 *
 * <pre>
 * VerificadorSanidade --csv &lt;arquivo&gt; --scenario S1|S2 [--saida &lt;json&gt;]
 * </pre>
 *
 * <p>Le o CSV de eventos em streaming (uma passada, calculando o SHA-256 junto),
 * localiza as colunas pelo NOME no cabecalho ({@code operation} e
 * {@code return_code} obrigatorias, {@code phase} opcional), acumula as
 * contagens em {@link Sanidade} e grava um JSON com {@code arquivo},
 * {@code sha256}, {@code cenario}, {@code limiares}, {@code contagens},
 * {@code metricas}, {@code veredito}, {@code motivos} e {@code versao}. Sem
 * {@code --saida}, o JSON vai para a saida padrao.</p>
 *
 * <p>Codigos de saida: <b>0</b> = OK; <b>4</b> = FALHA (qualquer motivo, nao ha
 * modo leniente); <b>2</b> = uso ou arquivo invalido (argumento ausente ou
 * desconhecido, cenario diferente de S1/S2, arquivo inexistente ou vazio,
 * cabecalho sem {@code operation}/{@code return_code}, linha com numero de
 * colunas diferente do cabecalho, rotulo de fase desconhecido, {@code operation}
 * fora da mistura do cenario ({@link Sanidade#operacoesDe(Scenario)}; decisao do
 * Gestor de 2026-10-22, sem diluir as fracoes), falha de E/S). {@code return_code}
 * e livre.
 * S3 tem guarda propria na parte ii.</p>
 *
 * <p>Nao esta ligado ao {@code WorkloadMain} nem ao {@code percentis.py}: isso e
 * a parte ii, depois de B-43.2.</p>
 */
public final class VerificadorSanidade {

    public static final int SAIDA_OK = 0;
    public static final int SAIDA_USO = 2;
    public static final int SAIDA_FALHA = 4;

    private VerificadorSanidade() {
    }

    public static void main(String[] args) {
        System.exit(executar(args, System.out));
    }

    /** Erro de uso ou de arquivo: saida 2. */
    static final class UsoInvalido extends Exception {
        UsoInvalido(String msg) {
            super(msg);
        }
    }

    record Argumentos(Path csv, Scenario cenario, Path saida) {
    }

    static Argumentos parse(String[] args) throws UsoInvalido {
        String csv = null;
        String cen = null;
        String saida = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.equals("--csv") && !a.equals("--scenario") && !a.equals("--saida")) {
                throw new UsoInvalido("argumento desconhecido: " + a);
            }
            if (i + 1 >= args.length) {
                throw new UsoInvalido("falta valor para " + a);
            }
            String v = args[++i];
            switch (a) {
                case "--csv" -> csv = v;
                case "--scenario" -> cen = v;
                default -> saida = v;
            }
        }
        if (csv == null || cen == null) {
            throw new UsoInvalido("uso: --csv <arquivo> --scenario S1|S2 [--saida <json>]");
        }
        Scenario s;
        switch (cen) {
            case "S1" -> s = Scenario.S1;
            case "S2" -> s = Scenario.S2;
            case "S3" -> throw new UsoInvalido("S3: guarda propria na parte ii");
            default -> throw new UsoInvalido("cenario invalido: '" + cen + "' (S1|S2)");
        }
        return new Argumentos(Paths.get(csv), s, saida == null ? null : Paths.get(saida));
    }

    /** Ponto de entrada testavel. Mensagens de erro vao para {@code out}. */
    public static int executar(String[] args, PrintStream out) {
        try {
            Argumentos a = parse(args);
            Sanidade san = new Sanidade();
            String sha = ler(a.csv(), a.cenario(), san);
            Sanidade.Limiares lim = Sanidade.Limiares.provisorios();
            Sanidade.Resultado r = san.avaliar(a.cenario(), lim);

            Map<String, Object> json = montarJson(a, sha, lim, r);
            ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            String texto = om.writeValueAsString(json) + "\n";
            if (a.saida() != null) {
                Path pai = a.saida().toAbsolutePath().getParent();
                if (pai != null) {
                    Files.createDirectories(pai);
                }
                Files.writeString(a.saida(), texto, StandardCharsets.UTF_8);
            } else {
                out.print(texto);
            }
            List<String> codigos = new ArrayList<>();
            r.motivos().forEach(m -> codigos.add(m.codigo()));
            out.println("Sanidade: veredito=" + r.veredito() + " cenario=" + a.cenario()
                    + " motivos=" + String.join(",", codigos));
            return r.veredito() == Sanidade.Veredito.OK ? SAIDA_OK : SAIDA_FALHA;
        } catch (UsoInvalido e) {
            out.println("Sanidade: ERRO de uso/arquivo: " + e.getMessage());
            return SAIDA_USO;
        } catch (IOException e) {
            out.println("Sanidade: ERRO de E/S: " + e);
            return SAIDA_USO;
        }
    }

    /**
     * Le o CSV em streaming, alimenta {@code san} e devolve o SHA-256 do arquivo.
     * Uma {@code operation} fora de {@link Sanidade#operacoesDe(Scenario)} torna o
     * arquivo invalido para o cenario ({@link UsoInvalido}, saida 2).
     */
    static String ler(Path csv, Scenario cenario, Sanidade san) throws IOException, UsoInvalido {
        java.util.Set<String> permitidas = Sanidade.operacoesDe(cenario);
        if (!Files.isRegularFile(csv)) {
            throw new UsoInvalido("arquivo inexistente: " + csv);
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = new DigestInputStream(Files.newInputStream(csv), md);
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            String cab = br.readLine();
            if (cab == null) {
                throw new UsoInvalido("arquivo vazio (sem cabecalho): " + csv);
            }
            if (cab.startsWith("﻿")) {
                cab = cab.substring(1);
            }
            List<String> cols = Arrays.asList(cab.split(",", -1));
            int iOp = cols.indexOf("operation");
            int iRc = cols.indexOf("return_code");
            int iFase = cols.indexOf("phase");
            if (iOp < 0 || iRc < 0) {
                throw new UsoInvalido("cabecalho sem operation/return_code: " + cab);
            }
            String linha;
            long nLinha = 1;
            while ((linha = br.readLine()) != null) {
                nLinha++;
                if (linha.isEmpty()) {
                    continue;
                }
                String[] c = linha.split(",", -1);
                if (c.length != cols.size()) {
                    throw new UsoInvalido("linha " + nLinha + " com " + c.length
                            + " colunas, cabecalho tem " + cols.size());
                }
                if (!permitidas.contains(c[iOp])) {
                    throw new UsoInvalido("linha " + nLinha + ": operacao '" + c[iOp]
                            + "' fora da mistura do cenario " + cenario.name() + " " + permitidas);
                }
                try {
                    san.registrar(c[iOp], c[iRc], iFase >= 0 ? c[iFase] : null);
                } catch (IllegalArgumentException e) {
                    throw new UsoInvalido("linha " + nLinha + ": " + e.getMessage());
                }
            }
            // Consome o restante (nada, normalmente) para o digest cobrir o arquivo todo.
            byte[] resto = new byte[8192];
            while (in.read(resto) >= 0) {
                // nada
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    static Map<String, Object> montarJson(Argumentos a, String sha, Sanidade.Limiares lim,
                                          Sanidade.Resultado r) {
        Map<String, Object> limiares = new LinkedHashMap<>();
        limiares.put("login_ok_min", lim.loginOkMin());
        limiares.put("validate_valid_min_exclusivo", 0);
        limiares.put("validate_ausente_max", lim.validNotFoundMax());
        limiares.put("mistura_tolerancia_pp", lim.misturaToleranciaPp());
        limiares.put("status", "PROVISORIOS (diagnostico 2026-10-22; aceitos pelo Gestor como provisorios)");

        List<Map<String, String>> motivos = new ArrayList<>();
        for (Sanidade.Motivo m : r.motivos()) {
            Map<String, String> mm = new LinkedHashMap<>();
            mm.put("codigo", m.codigo());
            mm.put("texto", m.texto());
            motivos.add(mm);
        }

        Map<String, Object> j = new LinkedHashMap<>();
        j.put("arquivo", a.csv().toString().replace('\\', '/'));
        j.put("sha256", sha);
        j.put("cenario", a.cenario().name());
        j.put("modo", "strict");
        j.put("limiares", limiares);
        j.put("contagens", r.contagens());
        j.put("metricas", r.metricas());
        j.put("veredito", r.veredito().name());
        j.put("motivos", motivos);
        j.put("versao", Sanidade.VERSAO);
        return j;
    }
}
