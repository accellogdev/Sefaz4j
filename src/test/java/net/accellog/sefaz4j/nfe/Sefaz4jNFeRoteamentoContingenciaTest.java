package net.accellog.sefaz4j.nfe;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import net.accellog.sefaz4j.nfe.endpoints.Servico;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Sefaz4jNFeRoteamentoContingenciaTest {

    private static Sefaz4jConfig config(UF uf) {
        return new Sefaz4jConfig(uf, Ambiente.HOMOLOGACAO, new byte[0], "");
    }

    @Test
    public void tpEmisNormalVaiParaOAutorizadorDaUF() {
        assertEquals("https://homologacao.nfe.sefa.pr.gov.br/nfe/NFeAutorizacao4?wsdl",
            Sefaz4jNFe.resolverUrlAutorizador(config(UF.PR), "1", Servico.NFE_AUTORIZACAO));
    }

    @Test
    public void tpEmis7VaiParaSvcRs() {
        assertEquals("https://nfe-homologacao.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx",
            Sefaz4jNFe.resolverUrlAutorizador(config(UF.PR), "7", Servico.NFE_AUTORIZACAO));
        assertEquals("https://nfe-homologacao.svrs.rs.gov.br/ws/NfeRetAutorizacao/NFeRetAutorizacao4.asmx",
            Sefaz4jNFe.resolverUrlAutorizador(config(UF.PR), "7", Servico.NFE_RET_AUTORIZACAO));
    }

    @Test
    public void tpEmis6VaiParaSvcAn() {
        assertEquals("https://hom.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx",
            Sefaz4jNFe.resolverUrlAutorizador(config(UF.SP), "6", Servico.NFE_AUTORIZACAO));
    }
}
