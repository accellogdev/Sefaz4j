package net.accellog.sefaz4j.distdfe;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class Sefaz4jDistDfeTest {
    @Test
    public void envelopeNfeContemBodyEDadosCorretos() {
        String distDFeIntXml = DistDfeIntXmlBuilder.porUltNsu(TipoDocumentoDistDfe.NFE, 2, null, "12345678000199", "0");
        String envelope = Sefaz4jDistDfe.montarEnvelopeSoap(TipoDocumentoDistDfe.NFE, distDFeIntXml);

        assertTrue(envelope.contains("<soap12:Envelope"));
        assertTrue(envelope.contains("<nfeDistDFeInteresse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">"));
        assertTrue(envelope.contains("<nfeDadosMsg>"));
        assertTrue(envelope.contains(distDFeIntXml));
    }

    @Test
    public void envelopeCteUsaElementoBodyDoCte() {
        String distDFeIntXml = DistDfeIntXmlBuilder.porUltNsu(TipoDocumentoDistDfe.CTE, 2, null, "12345678000199", "0");
        String envelope = Sefaz4jDistDfe.montarEnvelopeSoap(TipoDocumentoDistDfe.CTE, distDFeIntXml);

        assertTrue(envelope.contains("<cteDistDFeInteresse xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeDistribuicaoDFe\">"));
    }
}
