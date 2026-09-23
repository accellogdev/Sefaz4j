package net.accellog.sefaz4j.distdfe;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class DistDfeIntXmlBuilderTest {
    @Test
    public void porUltNsuMontaXmlComNsuZeroPaddedA15Digitos() {
        String xml = DistDfeIntXmlBuilder.porUltNsu(TipoDocumentoDistDfe.NFE, 2, 35, "12345678000199", "7");

        assertTrue(xml.contains("<distDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"));
        assertTrue(xml.contains("<tpAmb>2</tpAmb>"));
        assertTrue(xml.contains("<cUFAutor>35</cUFAutor>"));
        assertTrue(xml.contains("<CNPJ>12345678000199</CNPJ>"));
        assertTrue(xml.contains("<distNSU><ultNSU>000000000000007</ultNSU></distNSU>"));
    }

    @Test
    public void porUltNsuSemCUFAutorOmiteOElemento() {
        String xml = DistDfeIntXmlBuilder.porUltNsu(TipoDocumentoDistDfe.NFE, 1, null, "12345678000199", "0");
        assertTrue(!xml.contains("cUFAutor"));
    }

    @Test
    public void porNsuMontaConsNsu() {
        // CTe tem namespace/versao proprios, diferentes do NFe (ver TipoDocumentoDistDfe) --
        // cUFAutor obrigatorio e' validado na facade Sefaz4jDistDfe, nao no builder.
        String xml = DistDfeIntXmlBuilder.porNsu(TipoDocumentoDistDfe.CTE, 2, 35, "12345678000199", "42");
        assertTrue(xml.contains("<consNSU><NSU>000000000000042</NSU></consNSU>"));
        assertTrue(xml.contains("<distDFeInt versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/cte\">"));
    }

    @Test
    public void porChaveMontaConsChNFe() {
        String chave = "35240612345678000199550010000000011234567890";
        String xml = DistDfeIntXmlBuilder.porChave(TipoDocumentoDistDfe.NFE, 1, null, "12345678000199", chave);
        assertTrue(xml.contains("<consChNFe><chNFe>" + chave + "</chNFe></consChNFe>"));
    }
}
