package net.accellog.sefaz4j.mdfe.webservice;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SoapEnvelopeBuilderTest {

    @Test
    public void envelopeRecepcaoEnvolveOMdfeAssinadoNoWrapperCorretoComIdLote() {
        String mdfeAssinado = "<MDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\"><infMDFe Id=\"MDFe123\"/></MDFe>";

        String envelope = SoapEnvelopeBuilder.envelopeRecepcao(mdfeAssinado, 1L);

        assertTrue(envelope.contains("<soap12:Envelope"));
        assertTrue(envelope.contains("http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcao"));
        assertTrue(envelope.contains("<enviMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">"));
        assertTrue(envelope.contains("<idLote>1</idLote>"));
        assertTrue(envelope.contains(mdfeAssinado));
        assertFalse("enviMDFe nao tem indSinc (diferente de enviNFe)", envelope.contains("indSinc"));
    }

    @Test
    public void envelopeRetRecepcaoEnvolveConsReciMDFeNoWrapperCorreto() {
        String envelope = SoapEnvelopeBuilder.envelopeRetRecepcao("111000000000001", 1);

        assertTrue(envelope.contains("http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRetRecepcao"));
        assertTrue(envelope.contains("<consReciMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">"));
        assertTrue(envelope.contains("<tpAmb>1</tpAmb>"));
        assertTrue(envelope.contains("<nRec>111000000000001</nRec>"));
    }

    @Test
    public void envelopeConsultaSituacaoEnvolveOXmlDeConsultaNoWrapperCorreto() {
        String consSitMDFe = "<consSitMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\"><tpAmb>2</tpAmb><xServ>CONSULTAR</xServ><chMDFe>123</chMDFe></consSitMDFe>";

        String envelope = SoapEnvelopeBuilder.envelopeConsultaSituacao(consSitMDFe);

        assertTrue(envelope.contains("http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeConsulta"));
        assertTrue(envelope.contains(consSitMDFe));
    }

    @Test
    public void envelopeRecepcaoEventoEnvolveOEventoAssinadoSemLote() {
        String eventoAssinado = "<eventoMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\"><infEvento Id=\"ID1\"/></eventoMDFe>";

        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoEvento(eventoAssinado);

        assertTrue(envelope.contains("http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento"));
        assertTrue(envelope.contains(eventoAssinado));
        assertFalse("MDFe nao usa envelope de lote (idLote) para eventos, igual ao CTe", envelope.contains("idLote"));
    }
}
