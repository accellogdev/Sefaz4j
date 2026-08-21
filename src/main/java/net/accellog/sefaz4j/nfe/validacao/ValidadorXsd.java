package net.accellog.sefaz4j.nfe.validacao;

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
