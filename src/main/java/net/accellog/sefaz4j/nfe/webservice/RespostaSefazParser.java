package net.accellog.sefaz4j.nfe.webservice;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class RespostaSefazParser {

    private RespostaSefazParser() {
    }

    public static RespostaSefaz parsear(String respostaXml) {
        try {
            // respostaXml é a resposta HTTP crua da SEFAZ (entrada remota) —
            // desabilita DOCTYPE/entidades externas para não expor a
            // biblioteca (que também manipula material de chave privada) a
            // XXE/expansão de entidades vindo de um servidor comprometido
            // ou de um MITM.
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document documento = dbf.newDocumentBuilder().parse(
                new ByteArrayInputStream(respostaXml.getBytes(StandardCharsets.UTF_8))
            );

            String cStat = textoDoPrimeiro(documento, "cStat");
            String xMotivo = textoDoPrimeiro(documento, "xMotivo");
            String nRec = textoDoPrimeiro(documento, "nRec");
            String chNFe = textoDoPrimeiro(documento, "chNFe");

            NodeList protNFeList = documento.getElementsByTagNameNS("*", "protNFe");
            String protocoloXml = protNFeList.getLength() > 0 ? serializar((Element) protNFeList.item(0)) : null;

            return new RespostaSefaz(cStat, xMotivo, nRec, chNFe, protocoloXml);
        } catch (Exception e) {
            throw new ComunicacaoException("Falha ao interpretar a resposta da SEFAZ", e);
        }
    }

    private static String textoDoPrimeiro(Document documento, String nomeLocal) {
        NodeList lista = documento.getElementsByTagNameNS("*", nomeLocal);
        return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
    }

    private static String serializar(Element elemento) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(elemento), new StreamResult(writer));
        return writer.toString();
    }
}
