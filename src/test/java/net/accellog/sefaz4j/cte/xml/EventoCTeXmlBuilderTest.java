package net.accellog.sefaz4j.cte.xml;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EventoCTeXmlBuilderTest {

    @Test
    public void montaEventoCTeComIdNoFormatoCorretoENenhumVerEventoSeparado() {
        String detEvento = "<detEvento versaoEvento=\"4.00\">" +
            "<evCancCTe xmlns=\"http://www.portalfiscal.inf.br/cte\">" +
            "<descEvento>Cancelamento</descEvento>" +
            "<nProt>135250000000001</nProt>" +
            "<xJust>Justificativa de teste com quinze ou mais caracteres</xJust>" +
            "</evCancCTe>" +
            "</detEvento>";

        Document documento = EventoCTeXmlBuilder.montar(
            "35", "2", "12345678000195", "35250812345678000195570010000001231123456789",
            "110111", 1, "4.00", detEvento
        );

        Element raiz = documento.getDocumentElement();
        assertEquals("eventoCTe", raiz.getLocalName());
        assertEquals("4.00", raiz.getAttribute("versao"));

        NodeList infEventoList = raiz.getElementsByTagNameNS("http://www.portalfiscal.inf.br/cte", "infEvento");
        assertEquals(1, infEventoList.getLength());
        Element infEvento = (Element) infEventoList.item(0);
        // nSeqEvento é zero-preenchido com 3 dígitos no CT-e (diferente do NFe, que usa 2) — ver
        // eventoCTeTiposBasico_v4.00.xsd: infEvento/@Id casa "ID[0-9]{12}[A-Z0-9]{12}[0-9]{29}"
        // (53 dígitos após "ID"), e tpEvento(6) + chCTe(44) só fecham 53 com nSeqEvento de 3 dígitos.
        assertEquals("ID11011135250812345678000195570010000001231123456789" + "001", infEvento.getAttribute("Id"));

        NodeList chCTeList = raiz.getElementsByTagNameNS("http://www.portalfiscal.inf.br/cte", "chCTe");
        assertEquals("35250812345678000195570010000001231123456789", chCTeList.item(0).getTextContent());

        // CTe não tem elemento <verEvento> separado dentro de infEvento (diferença confirmada em
        // relação ao NFe) — só o atributo versaoEvento em detEvento e versao em eventoCTe.
        NodeList verEventoList = raiz.getElementsByTagNameNS("*", "verEvento");
        assertEquals(0, verEventoList.getLength());
    }
}
