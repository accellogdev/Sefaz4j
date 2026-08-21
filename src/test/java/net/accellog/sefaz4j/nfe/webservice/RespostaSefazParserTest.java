package net.accellog.sefaz4j.nfe.webservice;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RespostaSefazParserTest {

    @Test
    public void parseiaLoteAutorizadoComProtocolo() {
        String xml = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">" +
            "<retEnviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
            "<cStat>104</cStat><xMotivo>Lote processado</xMotivo>" +
            "<protNFe versao=\"4.00\"><infProt>" +
            "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
            "<cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>" +
            "<nProt>135250000000001</nProt>" +
            "</infProt></protNFe>" +
            "</retEnviNFe></nfeResultMsg></soap:Body></soap:Envelope>";

        RespostaSefaz resposta = RespostaSefazParser.parsear(xml);

        assertEquals("104", resposta.getCStat());
        assertEquals("Lote processado", resposta.getXMotivo());
        assertEquals("35250812345678000195550010000001231123456789", resposta.getChNFe());
        assertNotNull(resposta.getProtocoloXml());
        assertTrue(resposta.getProtocoloXml().contains("<nProt>135250000000001</nProt>"));
        assertNull(resposta.getNRec());
    }

    @Test
    public void parseiaLoteAindaEmProcessamentoComRecibo() {
        String xml = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">" +
            "<retEnviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
            "<cStat>103</cStat><xMotivo>Lote recebido com sucesso</xMotivo>" +
            "<infRec><nRec>123456789012345</nRec></infRec>" +
            "</retEnviNFe></nfeResultMsg></soap:Body></soap:Envelope>";

        RespostaSefaz resposta = RespostaSefazParser.parsear(xml);

        assertEquals("103", resposta.getCStat());
        assertEquals("123456789012345", resposta.getNRec());
        assertNull(resposta.getProtocoloXml());
    }
}
