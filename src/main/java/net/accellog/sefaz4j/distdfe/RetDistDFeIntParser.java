package net.accellog.sefaz4j.distdfe;

import net.accellog.sefaz4j.webservice.ComunicacaoException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class RetDistDFeIntParser {

    private RetDistDFeIntParser() {
    }

    public static ResultadoDistribuicaoDFe parsear(String respostaHttpBruta) {
        try {
            Element retDistDFeInt = localizarRetDistDFeInt(respostaHttpBruta);

            String cStat = textoDoPrimeiroFilho(retDistDFeInt, "cStat");
            String xMotivo = textoDoPrimeiroFilho(retDistDFeInt, "xMotivo");
            String ultNSU = textoDoPrimeiroFilho(retDistDFeInt, "ultNSU");
            String maxNSU = textoDoPrimeiroFilho(retDistDFeInt, "maxNSU");

            List<DocumentoDistDfe> documentos = new ArrayList<>();
            NodeList docZipList = retDistDFeInt.getElementsByTagNameNS("*", "docZip");
            for (int i = 0; i < docZipList.getLength(); i++) {
                Element docZip = (Element) docZipList.item(i);
                String nsu = docZip.getAttribute("NSU");
                String schema = docZip.getAttribute("schema");
                String conteudoDescompactado = descompactar(docZip.getTextContent());
                documentos.add(new DocumentoDistDfe(nsu, schema, conteudoDescompactado));
            }

            boolean ok = "137".equals(cStat) || "138".equals(cStat);
            return new ResultadoDistribuicaoDFe(ok, cStat, xMotivo, ultNSU, maxNSU, documentos);
        } catch (ComunicacaoException e) {
            throw e;
        } catch (Exception e) {
            throw new ComunicacaoException("Falha ao interpretar a resposta de Distribuicao de DFe", e);
        }
    }

    private static Element localizarRetDistDFeInt(String respostaHttpBruta) throws Exception {
        Document documento = parsearXml(respostaHttpBruta);
        NodeList candidatos = documento.getElementsByTagNameNS("*", "retDistDFeInt");

        if (candidatos.getLength() > 0) {
            return (Element) candidatos.item(0);
        }

        NodeList resultados = documento.getElementsByTagNameNS("*", "nfeDistDFeInteresseResult");
        if (resultados.getLength() == 0) {
            resultados = documento.getElementsByTagNameNS("*", "cteDistDFeInteresseResult");
        }
        if (resultados.getLength() == 0) {
            throw new IllegalStateException("Elemento retDistDFeInt nao encontrado na resposta");
        }

        Node resultNode = resultados.item(0);
        String textoEscapado = resultNode.getTextContent();
        Document reparsed = parsearXml(textoEscapado);
        NodeList reencontrados = reparsed.getElementsByTagNameNS("*", "retDistDFeInt");
        if (reencontrados.getLength() == 0) {
            throw new IllegalStateException("Elemento retDistDFeInt nao encontrado apos reparse do texto escapado");
        }
        return (Element) reencontrados.item(0);
    }

    private static Document parsearXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String textoDoPrimeiroFilho(Element pai, String nomeLocal) {
        NodeList lista = pai.getElementsByTagNameNS("*", nomeLocal);
        return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
    }

    private static String descompactar(String base64GzipContent) throws Exception {
        byte[] comprimido = Base64.getMimeDecoder().decode(base64GzipContent.trim());
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(comprimido))) {
            gz.transferTo(saida);
        }
        return saida.toString(StandardCharsets.UTF_8);
    }
}
