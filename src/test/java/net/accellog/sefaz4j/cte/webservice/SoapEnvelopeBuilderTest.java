package net.accellog.sefaz4j.cte.webservice;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SoapEnvelopeBuilderTest {

    @Test
    public void envelopeRecepcaoSincEnvolveOCteAssinadoNoWrapperCorreto() {
        String cteAssinado = "<CTe xmlns=\"http://www.portalfiscal.inf.br/cte\"><infCte Id=\"CTe123\"/></CTe>";

        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoSinc(cteAssinado);

        assertTrue(envelope.contains("<soap12:Envelope"));
        assertTrue(envelope.contains("http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSincV4"));
        assertTrue(envelope.contains(cteAssinado));
    }

    @Test
    public void envelopeConsultaSituacaoEnvolveOXmlDeConsultaNoWrapperCorreto() {
        String consSitCTe = "<consSitCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\"><tpAmb>2</tpAmb><xServ>CONSULTAR</xServ><chCTe>123</chCTe></consSitCTe>";

        String envelope = SoapEnvelopeBuilder.envelopeConsultaSituacao(consSitCTe);

        assertTrue(envelope.contains("<soap12:Envelope"));
        assertTrue(envelope.contains("http://www.portalfiscal.inf.br/cte/wsdl/CTeConsultaV4"));
        assertTrue(envelope.contains(consSitCTe));
    }

    @Test
    public void envelopeRecepcaoEventoEnvolveOEventoAssinadoNoWrapperCorretoSemLote() {
        String eventoAssinado = "<eventoCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\"><infEvento Id=\"ID1\"/></eventoCTe>";

        String envelope = SoapEnvelopeBuilder.envelopeRecepcaoEvento(eventoAssinado);

        assertTrue(envelope.contains("<soap12:Envelope"));
        assertTrue(envelope.contains("http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoEventoV4"));
        assertTrue(envelope.contains(eventoAssinado));
        assertFalse("CTe não usa envelope de lote (idLote) para eventos", envelope.contains("idLote"));
    }
}
