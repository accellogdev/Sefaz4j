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
    public void consultaECancelamentoDeNotaEmSvcRsVaoParaOSvcRs() {
        Sefaz4jConfig config = config(UF.PR).setTpEmis("7");
        assertEquals("https://nfe-homologacao.svrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx",
            Sefaz4jNFe.urlConsultaProtocolo(config));
        assertEquals("https://nfe-homologacao.svrs.rs.gov.br/ws/recepcaoevento/recepcaoevento4.asmx",
            Sefaz4jNFe.urlRecepcaoEventoDoAutorizador(config));
    }

    @Test
    public void consultaECancelamentoSemTpEmisVaoParaAUF() {
        Sefaz4jConfig config = config(UF.PR);
        assertEquals("https://homologacao.nfe.sefa.pr.gov.br/nfe/NFeConsultaProtocolo4?wsdl",
            Sefaz4jNFe.urlConsultaProtocolo(config));
        assertEquals("https://homologacao.nfe.sefa.pr.gov.br/nfe/NFeRecepcaoEvento4?wsdl",
            Sefaz4jNFe.urlRecepcaoEventoDoAutorizador(config));
    }

    @Test
    public void contingenciaOffLineContinuaNaUF() {
        // FS-IA (2), EPEC (4) e FS-DA (5) são transmitidas depois ao autorizador normal da UF.
        for (String tpEmis : new String[] {"2", "4", "5"}) {
            assertEquals("https://homologacao.nfe.sefa.pr.gov.br/nfe/NFeConsultaProtocolo4?wsdl",
                Sefaz4jNFe.urlConsultaProtocolo(config(UF.PR).setTpEmis(tpEmis)));
        }
    }

    @Test
    public void tpEmis6VaiParaSvcAn() {
        assertEquals("https://hom.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx",
            Sefaz4jNFe.resolverUrlAutorizador(config(UF.SP), "6", Servico.NFE_AUTORIZACAO));
    }
}
