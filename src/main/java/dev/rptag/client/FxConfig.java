package dev.rptag.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * (4.2.0) Config do CLIENTE: config/rptag/cliente.properties.
 * fxVideo=true|false (padrao true) — false = avisos sempre no FX procedural
 * (PC fraco). Ausente -> cria com o padrao; ilegivel -> padrao (nunca crasha).
 * Java puro: quem passa o caminho e loga e o FxComando.
 */
public final class FxConfig {
    private static Path arquivo;
    private static boolean video = true;
    private static String ultimoAviso;

    private FxConfig() {}

    public static boolean carregado() { return arquivo != null; }
    public static boolean video() { return video; }

    /** Problema da ultima leitura/gravacao (pra log), ou null. Le e limpa. */
    public static String consumirAviso() {
        String a = ultimoAviso;
        ultimoAviso = null;
        return a;
    }

    public static void carregar(Path arq) {
        arquivo = arq;
        video = true;
        if (!Files.isRegularFile(arq)) {
            salvar();
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(arq)) {
            p.load(in);
            String v = p.getProperty("fxVideo", "true").trim();
            if (v.equalsIgnoreCase("false")) video = false;
            else if (!v.equalsIgnoreCase("true")) ultimoAviso = "fxVideo invalido (" + v + "), usando true";
        } catch (IOException | RuntimeException e) {
            video = true;
            ultimoAviso = "cliente.properties ilegivel, usando fxVideo=true: " + e;
        }
    }

    public static void definirVideo(boolean on) {
        video = on;
        salvar();
    }

    private static void salvar() {
        if (arquivo == null) return;
        Properties p = new Properties();
        p.setProperty("fxVideo", Boolean.toString(video));
        try {
            if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
            try (Writer w = Files.newBufferedWriter(arquivo)) {
                p.store(w, "RP Tag - config do cliente (fxVideo=false: avisos sem video)");
            }
        } catch (IOException e) {
            ultimoAviso = "nao consegui gravar cliente.properties: " + e;
        }
    }
}
