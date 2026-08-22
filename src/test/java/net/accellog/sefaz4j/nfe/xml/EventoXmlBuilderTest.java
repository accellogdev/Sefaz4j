package net.accellog.sefaz4j.nfe.xml;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.Assert.assertEquals;

public class EventoXmlBuilderTest {

    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String CHAVE = "35250812345678000195550010000001231123456789";

    @Test
    public void montaInfEventoComIdNoFormatoExigidoPeloSchemaDeCancelamento() {
        String detEvento = "<detEvento versao=\"1.00\"><descEvento>Cancelamento</descEvento>" +
            "<nProt>135250000000001</nProt>" +
            "<xJust>Justificativa de teste com quinze ou mais caracteres</xJust></detEvento>";

        Document documento = EventoXmlBuilder.montar(
            "35", "1", "12345678000195", CHAVE, "110111", 1, "1.00", detEvento
        );

        NodeList infEventoList = documento.getElementsByTagNameNS(NFE_NS, "infEvento");
        assertEquals(1, infEventoList.getLength());
        Element infEvento = (Element) infEventoList.item(0);
        assertEquals("ID110111" + CHAVE + "01", infEvento.getAttribute("Id"));

        assertEquals("35", textoDoFilho(infEvento, "cOrgao"));
        assertEquals("1", textoDoFilho(infEvento, "tpAmb"));
        assertEquals("12345678000195", textoDoFilho(infEvento, "CNPJ"));
        assertEquals(CHAVE, textoDoFilho(infEvento, "chNFe"));
        assertEquals("110111", textoDoFilho(infEvento, "tpEvento"));
        assertEquals("1", textoDoFilho(infEvento, "nSeqEvento"));
        assertEquals("1.00", textoDoFilho(infEvento, "verEvento"));
        assertEquals("Cancelamento", textoDoFilho(infEvento, "descEvento"));
    }

    @Test
    public void formataNSeqEventoComDoisDigitosNoIdParaSequenciaDoisDigitos() {
        String detEvento = "<detEvento versao=\"1.00\"><descEvento>Carta de Correção</descEvento>" +
            "<xCorrecao>Correção de teste com quinze ou mais caracteres</xCorrecao>" +
            "<xCondUso>texto fixo</xCondUso></detEvento>";

        Document documento = EventoXmlBuilder.montar(
            "35", "1", "12345678000195", CHAVE, "110110", 12, "1.00", detEvento
        );

        Element infEvento = (Element) documento.getElementsByTagNameNS(NFE_NS, "infEvento").item(0);
        assertEquals("ID110110" + CHAVE + "12", infEvento.getAttribute("Id"));
        assertEquals("12", textoDoFilho(infEvento, "nSeqEvento"));
    }

    private static String textoDoFilho(Element pai, String nomeLocal) {
        return ((Element) pai.getElementsByTagNameNS(NFE_NS, nomeLocal).item(0)).getTextContent();
    }
}
