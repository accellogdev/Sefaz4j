package net.accellog.sefaz4j.cte;

import net.accellog.sefaz4j.cte.endpoints.Servico;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Sefaz4jCTeRoteamentoContingenciaTest {

    private static Sefaz4jConfig config(UF uf) {
        return new Sefaz4jConfig(uf, Ambiente.HOMOLOGACAO, new byte[0], "");
    }

    @Test
    public void tpEmisNormalVaiParaOAutorizadorDaUF() {
        assertEquals("https://homologacao.cte.fazenda.pr.gov.br/cte4/CTeRecepcaoSincV4",
            Sefaz4jCTe.resolverUrlAutorizador(config(UF.PR), "1", Servico.CTE_RECEPCAO_SINC));
    }

    @Test
    public void tpEmis8VaiParaSvcSp() {
        assertEquals("https://homologacao.nfe.fazenda.sp.gov.br/CTeWS/WS/CTeRecepcaoSincV4.asmx",
            Sefaz4jCTe.resolverUrlAutorizador(config(UF.PR), "8", Servico.CTE_RECEPCAO_SINC));
    }

    @Test
    public void tpEmis7VaiParaSvcRs() {
        assertEquals("https://cte-homologacao.svrs.rs.gov.br/ws/CTeRecepcaoSincV4/CTeRecepcaoSincV4.asmx",
            Sefaz4jCTe.resolverUrlAutorizador(config(UF.SP), "7", Servico.CTE_RECEPCAO_SINC));
    }

    @Test
    public void consultaEEventosDeCteEmSvcSpVaoParaOSvcSp() {
        Sefaz4jConfig config = config(UF.PR).setTpEmis("8");
        assertEquals("https://homologacao.nfe.fazenda.sp.gov.br/CTeWS/WS/CTeConsultaV4.asmx",
            Sefaz4jCTe.urlConsultaProtocolo(config));
        assertEquals("https://homologacao.nfe.fazenda.sp.gov.br/CTeWS/WS/CTeRecepcaoEventoV4.asmx",
            Sefaz4jCTe.urlRecepcaoEvento(config));
    }

    @Test
    public void contingenciaOffLineContinuaNaUF() {
        // EPEC (4) e FS-DA (5): o CT-e é transmitido depois ao autorizador normal da UF.
        for (String tpEmis : new String[] {"4", "5"}) {
            assertEquals("https://homologacao.cte.fazenda.pr.gov.br/cte4/CTeConsultaV4",
                Sefaz4jCTe.urlConsultaProtocolo(config(UF.PR).setTpEmis(tpEmis)));
        }
    }
}
