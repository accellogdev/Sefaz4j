package net.accellog.sefaz4j.distdfe;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TipoDocumentoDistDfeTest {
    @Test
    public void nfeTemMetadadosCorretos() {
        TipoDocumentoDistDfe tipo = TipoDocumentoDistDfe.NFE;
        assertEquals("http://www.portalfiscal.inf.br/nfe", tipo.getNamespaceDocumento());
        assertEquals("1.01", tipo.getVersaoDocumento());
        assertEquals("/schemas/distdfe/distDFeInt_v1.01.xsd", tipo.getXsdRequisicao());
        assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe", tipo.getNamespaceWsdl());
        assertEquals("nfeDistDFeInteresse", tipo.getElementoBodySoap());
        assertEquals("/endpoints/nfe-servicos.ini", tipo.getArquivoIni());
        assertEquals("NFE_", tipo.getPrefixoSecaoIni());
        assertEquals("NFeDistribuicaoDFe_1.01", tipo.getChaveServicoIni());
        assertFalse(tipo.isCUFAutorObrigatorio());
    }

    @Test
    public void cteTemMetadadosCorretos() {
        TipoDocumentoDistDfe tipo = TipoDocumentoDistDfe.CTE;
        // O distDFeInt do CTe reaproveita o namespace e o XSD do NFe (confirmado no XSD oficial
        // distDFeInt_v1.00.xsd do CTe, targetNamespace "http://www.portalfiscal.inf.br/nfe") --
        // so o corpo SOAP e' especifico do CTe.
        assertEquals("http://www.portalfiscal.inf.br/nfe", tipo.getNamespaceDocumento());
        assertEquals("1.00", tipo.getVersaoDocumento());
        assertEquals("/schemas/distdfe/distDFeInt_cte_v1.00.xsd", tipo.getXsdRequisicao());
        assertEquals("http://www.portalfiscal.inf.br/cte/wsdl/CTeDistribuicaoDFe", tipo.getNamespaceWsdl());
        assertEquals("cteDistDFeInteresse", tipo.getElementoBodySoap());
        assertEquals("/endpoints/cte-servicos.ini", tipo.getArquivoIni());
        assertEquals("CTE_", tipo.getPrefixoSecaoIni());
        assertEquals("CTeDistribuicaoDFe_1.00", tipo.getChaveServicoIni());
        assertTrue(tipo.isCUFAutorObrigatorio());
    }
}
