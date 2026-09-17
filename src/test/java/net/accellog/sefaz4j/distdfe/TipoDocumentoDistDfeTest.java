package net.accellog.sefaz4j.distdfe;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TipoDocumentoDistDfeTest {
    @Test
    public void nfeTemMetadadosCorretos() {
        TipoDocumentoDistDfe tipo = TipoDocumentoDistDfe.NFE;
        assertEquals("http://www.portalfiscal.inf.br/nfe", tipo.getNamespaceDocumento());
        assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe", tipo.getNamespaceWsdl());
        assertEquals("nfeDistDFeInteresse", tipo.getElementoBodySoap());
        assertEquals("/endpoints/nfe-servicos.ini", tipo.getArquivoIni());
        assertEquals("NFE_", tipo.getPrefixoSecaoIni());
        assertEquals("NFeDistribuicaoDFe_1.01", tipo.getChaveServicoIni());
    }

    @Test
    public void cteTemMetadadosCorretos() {
        TipoDocumentoDistDfe tipo = TipoDocumentoDistDfe.CTE;
        assertEquals("http://www.portalfiscal.inf.br/cte", tipo.getNamespaceDocumento());
        assertEquals("http://www.portalfiscal.inf.br/cte/wsdl/CTeDistribuicaoDFe", tipo.getNamespaceWsdl());
        assertEquals("cteDistDFeInteresse", tipo.getElementoBodySoap());
        assertEquals("/endpoints/cte-servicos.ini", tipo.getArquivoIni());
        assertEquals("CTE_", tipo.getPrefixoSecaoIni());
        assertEquals("CTeDistribuicaoDFe_1.00", tipo.getChaveServicoIni());
    }
}
