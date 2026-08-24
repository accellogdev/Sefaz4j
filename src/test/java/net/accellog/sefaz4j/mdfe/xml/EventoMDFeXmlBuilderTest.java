package net.accellog.sefaz4j.mdfe.xml;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.Assert.assertEquals;

public class EventoMDFeXmlBuilderTest {

    @Test
    public void montaEventoMDFeComIdNoFormatoCorretoENenhumVerEventoSeparado() {
        String detEvento = "<detEvento versaoEvento=\"3.00\">" +
            "<evCancMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\">" +
            "<descEvento>Cancelamento</descEvento>" +
            "<nProt>135250000000001</nProt>" +
            "<xJust>Justificativa de teste com quinze ou mais caracteres</xJust>" +
            "</evCancMDFe>" +
            "</detEvento>";

        Document documento = EventoMDFeXmlBuilder.montar(
            "35", "2", "12345678000195", "35250812345678000195580010000001231123456789",
            "110111", 1, "3.00", detEvento
        );

        Element raiz = documento.getDocumentElement();
        assertEquals("eventoMDFe", raiz.getLocalName());
        assertEquals("3.00", raiz.getAttribute("versao"));

        NodeList infEventoList = raiz.getElementsByTagNameNS("http://www.portalfiscal.inf.br/mdfe", "infEvento");
        assertEquals(1, infEventoList.getLength());
        Element infEvento = (Element) infEventoList.item(0);
        // nSeqEvento é zero-preenchido com 2 dígitos no MDF-e (igual ao NFe, diferente do CT-e que
        // usa 3) — confirmado em ACBrMDFe.EnvEvento.pas (Format('%.2d', ...)).
        assertEquals("ID11011135250812345678000195580010000001231123456789" + "01", infEvento.getAttribute("Id"));

        NodeList chMDFeList = raiz.getElementsByTagNameNS("http://www.portalfiscal.inf.br/mdfe", "chMDFe");
        assertEquals("35250812345678000195580010000001231123456789", chMDFeList.item(0).getTextContent());

        NodeList verEventoList = raiz.getElementsByTagNameNS("*", "verEvento");
        assertEquals(0, verEventoList.getLength());
    }
}
