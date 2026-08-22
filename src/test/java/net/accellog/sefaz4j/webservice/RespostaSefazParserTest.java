package net.accellog.sefaz4j.webservice;

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
        assertEquals("35250812345678000195550010000001231123456789", resposta.getChaveDocumento());
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

    @Test
    public void parseiaComNomeDeElementoDeProtocoloDiferenteDeProtNFe() {
        String xml = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">" +
            "<retEnvEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\">" +
            "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
            "<cStat>128</cStat><xMotivo>Lote de evento processado</xMotivo>" +
            "<retEvento versao=\"1.00\"><infEvento>" +
            "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
            "<cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>" +
            "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
            "<tpEvento>110111</tpEvento><xEvento>Cancelamento</xEvento><nSeqEvento>1</nSeqEvento>" +
            "<dhRegEvento>2025-08-12T10:11:00-03:00</dhRegEvento>" +
            "<nProt>135250000000002</nProt>" +
            "</infEvento></retEvento>" +
            "</retEnvEvento></nfeResultMsg></soap:Body></soap:Envelope>";

        RespostaSefaz resposta = RespostaSefazParser.parsear(xml, "retEvento");

        assertEquals("128", resposta.getCStat());
        assertEquals("Lote de evento processado", resposta.getXMotivo());
        assertNotNull(resposta.getProtocoloXml());
        assertTrue(resposta.getProtocoloXml().contains("<cStat>135</cStat>"));
        assertTrue(resposta.getProtocoloXml().contains("<nProt>135250000000002</nProt>"));
    }
}
