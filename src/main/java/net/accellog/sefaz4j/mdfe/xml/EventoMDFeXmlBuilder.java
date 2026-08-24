package net.accellog.sefaz4j.mdfe.xml;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Monta o {@link Document} de um evento de MDF-e (cancelamento, encerramento, inclusão de
 * condutor) — o conteúdo específico do tipo de evento ({@code detEvento}, já serializado) é
 * responsabilidade do chamador; esta classe só monta os campos fixos de {@code infEvento} comuns
 * a todo evento e o {@code Id} ("ID" + tpEvento + chave do MDF-e + nSeqEvento com 2 dígitos — igual
 * ao NFe, diferente do CT-e que usa 3). Diferente do {@code EventoXmlBuilder} do NFe, o
 * {@code infEvento} do MDF-e não tem um elemento {@code verEvento} separado — só o atributo
 * {@code versao} do {@code eventoMDFe} e o {@code versaoEvento} do {@code detEvento} fornecido pelo
 * chamador carregam informação de versão (mesma diferença já documentada para
 * {@code EventoCTeXmlBuilder}). Não assina nem valida contra XSD.
 */
public final class EventoMDFeXmlBuilder {

    private EventoMDFeXmlBuilder() {
    }

    public static Document montar(
        String cOrgao,
        String tpAmb,
        String cnpjAutor,
        String chMDFe,
        String tpEvento,
        int nSeqEvento,
        String versaoEvento,
        String detEventoXmlFragmento
    ) {
        String id = "ID" + tpEvento + chMDFe + String.format("%02d", nSeqEvento);
        String dhEvento = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));

        String xml = "<eventoMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"" + versaoEvento + "\">" +
            "<infEvento Id=\"" + id + "\">" +
            "<cOrgao>" + cOrgao + "</cOrgao>" +
            "<tpAmb>" + tpAmb + "</tpAmb>" +
            "<CNPJ>" + cnpjAutor + "</CNPJ>" +
            "<chMDFe>" + chMDFe + "</chMDFe>" +
            "<dhEvento>" + dhEvento + "</dhEvento>" +
            "<tpEvento>" + tpEvento + "</tpEvento>" +
            "<nSeqEvento>" + nSeqEvento + "</nSeqEvento>" +
            detEventoXmlFragmento +
            "</infEvento>" +
            "</eventoMDFe>";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o documento XML do evento MDF-e", e);
        }
    }
}
