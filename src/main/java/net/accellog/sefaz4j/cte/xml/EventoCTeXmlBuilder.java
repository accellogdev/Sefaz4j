package net.accellog.sefaz4j.cte.xml;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Monta o {@link Document} de um evento de CT-e (cancelamento, carta de
 * correção etc.) — o conteúdo específico do tipo de evento (elemento
 * {@code detEvento}, já serializado) é responsabilidade do chamador; esta
 * classe só monta os campos fixos de {@code infEvento} comuns a todo
 * evento e o {@code Id} no formato exigido pelo schema
 * ("ID" + tpEvento + chave do CT-e + nSeqEvento com 2 dígitos). Diferente
 * de {@code net.accellog.sefaz4j.nfe.xml.EventoXmlBuilder}, o
 * {@code infEvento} do CT-e não tem um elemento {@code verEvento} separado
 * — só o atributo {@code versao} do {@code eventoCTe} e o
 * {@code versaoEvento} do {@code detEvento} fornecido pelo chamador
 * carregam informação de versão. Não assina nem valida contra XSD.
 */
public final class EventoCTeXmlBuilder {

    private EventoCTeXmlBuilder() {
    }

    public static Document montar(
        String cOrgao,
        String tpAmb,
        String cnpjAutor,
        String chCTe,
        String tpEvento,
        int nSeqEvento,
        String versaoEvento,
        String detEventoXmlFragmento
    ) {
        String id = "ID" + tpEvento + chCTe + String.format("%02d", nSeqEvento);
        // Mesmo cuidado de fuso do EventoXmlBuilder do NFe: "xxx" (minúsculo) sempre emite "+HH:mm"/
        // "-HH:mm", nunca colapsa para "Z" em hosts UTC — TDateTimeUTC não aceita "Z".
        String dhEvento = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));

        String xml = "<eventoCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"" + versaoEvento + "\">" +
            "<infEvento Id=\"" + id + "\">" +
            "<cOrgao>" + cOrgao + "</cOrgao>" +
            "<tpAmb>" + tpAmb + "</tpAmb>" +
            "<CNPJ>" + cnpjAutor + "</CNPJ>" +
            "<chCTe>" + chCTe + "</chCTe>" +
            "<dhEvento>" + dhEvento + "</dhEvento>" +
            "<tpEvento>" + tpEvento + "</tpEvento>" +
            "<nSeqEvento>" + nSeqEvento + "</nSeqEvento>" +
            detEventoXmlFragmento +
            "</infEvento>" +
            "</eventoCTe>";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML do evento CT-e", e);
        }
    }
}
