package net.accellog.sefaz4j.nfe.webservice;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class SoapEnvelopeBuilderTest {

    private static final String NFE_XML_ASSINADO =
        "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe Id=\"NFe123\" versao=\"4.00\"></infNFe></NFe>";

    @Test
    public void envelopeAutorizacaoContemIndSincEIdLoteEXmlAssinadoEmbutido() {
        String envelope = SoapEnvelopeBuilder.envelopeAutorizacao(NFE_XML_ASSINADO, 1L);

        assertTrue(envelope.contains("<soap12:Envelope"));
        assertTrue(envelope.contains("xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\""));
        assertTrue(envelope.contains("<idLote>1</idLote>"));
        assertTrue(envelope.contains("<indSinc>1</indSinc>"));
        assertTrue(envelope.contains(NFE_XML_ASSINADO));
    }

    @Test
    public void envelopeRetAutorizacaoContemReciboETpAmb() {
        String envelope = SoapEnvelopeBuilder.envelopeRetAutorizacao("123456789012345", 2);

        assertTrue(envelope.contains("<tpAmb>2</tpAmb>"));
        assertTrue(envelope.contains("<nRec>123456789012345</nRec>"));
        assertTrue(envelope.contains("xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRetAutorizacao4\""));
    }
}
