package net.accellog.sefaz4j.cte.webservice;

import org.junit.Test;

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
}
