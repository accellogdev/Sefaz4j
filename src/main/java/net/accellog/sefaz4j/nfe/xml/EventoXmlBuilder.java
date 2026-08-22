package net.accellog.sefaz4j.nfe.xml;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Monta o {@link Document} de um evento (cancelamento, carta de correção
 * etc.) — o conteúdo específico do tipo de evento (elemento
 * {@code detEvento}, já serializado) é responsabilidade do chamador; esta
 * classe só monta os campos fixos de {@code infEvento} comuns a todo
 * evento e o {@code Id} no formato exigido pelo schema
 * ("ID" + tpEvento + chave da NF-e + nSeqEvento com 2 dígitos). Não assina
 * (ver {@code AssinadorXml.assinarEvento}) nem valida contra XSD.
 */
public final class EventoXmlBuilder {

    private EventoXmlBuilder() {
    }

    public static Document montar(
        String cOrgao,
        String tpAmb,
        String cnpjAutor,
        String chNFe,
        String tpEvento,
        int nSeqEvento,
        String verEvento,
        String detEventoXmlFragmento
    ) {
        String id = "ID" + tpEvento + chNFe + String.format("%02d", nSeqEvento);
        String dhEvento = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String xml = "<evento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"" + verEvento + "\">" +
            "<infEvento Id=\"" + id + "\">" +
            "<cOrgao>" + cOrgao + "</cOrgao>" +
            "<tpAmb>" + tpAmb + "</tpAmb>" +
            "<CNPJ>" + cnpjAutor + "</CNPJ>" +
            "<chNFe>" + chNFe + "</chNFe>" +
            "<dhEvento>" + dhEvento + "</dhEvento>" +
            "<tpEvento>" + tpEvento + "</tpEvento>" +
            "<nSeqEvento>" + nSeqEvento + "</nSeqEvento>" +
            "<verEvento>" + verEvento + "</verEvento>" +
            detEventoXmlFragmento +
            "</infEvento>" +
            "</evento>";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML do evento", e);
        }
    }
}
