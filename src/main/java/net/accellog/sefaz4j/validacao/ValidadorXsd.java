package net.accellog.sefaz4j.validacao;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ValidadorXsd {

    private static final String XSD_NFE = "/schemas/nfe/nfe_v4.00.xsd";
    private static final ConcurrentHashMap<String, Schema> SCHEMAS_CACHE = new ConcurrentHashMap<>();

    static {
        // mdfeTiposBasico_v3.00.xsd (bundled, matching the official layout) declares
        // maxOccurs="20000" on infDoc/infMunDescarga/infCTe, above the JDK's default
        // jdk.xml.maxOccurLimit=5000 guard on *compiling* a schema (unrelated to validating an XML
        // instance against it — this only governs how large a maxOccurs the schema document itself
        // may declare). Without this, SchemaFactory.newSchema(...) below throws on the very first
        // MDF-e validation call in ANY downstream JVM that hasn't separately raised the limit via a
        // launch flag — which a library consumer has no reason to know it needs to do. Setting it
        // here, once, before compiling any schema, makes this class self-contained regardless of the
        // caller's JVM flags. Only setProperty when absent so an explicit caller-provided value (via
        // -D on the command line) is never silently overridden.
        if (System.getProperty("jdk.xml.maxOccurLimit") == null) {
            System.setProperty("jdk.xml.maxOccurLimit", "0");
        }
    }

    private ValidadorXsd() {
    }

    public static void validar(String xml) {
        validar(xml, XSD_NFE);
    }

    public static void validar(String xml, String caminhoXsdRaiz) {
        Schema schema = SCHEMAS_CACHE.computeIfAbsent(caminhoXsdRaiz, ValidadorXsd::carregarSchema);

        List<String> violacoes = new ArrayList<>();
        Validator validator = schema.newValidator();
        validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
            }

            @Override
            public void error(SAXParseException exception) {
                violacoes.add("linha " + exception.getLineNumber() + ", coluna " + exception.getColumnNumber() + ": " + exception.getMessage());
            }

            @Override
            public void fatalError(SAXParseException exception) {
                violacoes.add("linha " + exception.getLineNumber() + ", coluna " + exception.getColumnNumber() + ": " + exception.getMessage());
            }
        });

        try {
            validator.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        } catch (SAXException | IOException e) {
            violacoes.add(e.getMessage());
        }

        if (!violacoes.isEmpty()) {
            throw new ValidacaoXsdException(violacoes);
        }
    }

    private static Schema carregarSchema(String caminhoXsdRaiz) {
        URL url = ValidadorXsd.class.getResource(caminhoXsdRaiz);
        if (url == null) {
            throw new IllegalStateException("Recurso " + caminhoXsdRaiz + " não encontrado no classpath");
        }
        try (InputStream in = url.openStream()) {
            StreamSource source = new StreamSource(in);
            source.setSystemId(url.toExternalForm());
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return factory.newSchema(source);
        } catch (SAXException e) {
            throw new IllegalStateException("Falha ao compilar o schema " + caminhoXsdRaiz, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
