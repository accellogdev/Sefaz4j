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

public final class ValidadorXsd {

    private static final Schema SCHEMA = carregarSchema();

    private ValidadorXsd() {
    }

    public static void validar(String xml) {
        List<String> violacoes = new ArrayList<>();
        Validator validator = SCHEMA.newValidator();
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

    private static Schema carregarSchema() {
        URL url = ValidadorXsd.class.getResource("/schemas/nfe/nfe_v4.00.xsd");
        if (url == null) {
            throw new IllegalStateException("Recurso /schemas/nfe/nfe_v4.00.xsd não encontrado no classpath");
        }
        try (InputStream in = url.openStream()) {
            StreamSource source = new StreamSource(in);
            source.setSystemId(url.toExternalForm());
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return factory.newSchema(source);
        } catch (SAXException e) {
            throw new IllegalStateException("Falha ao compilar o schema NFe 4.00", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
