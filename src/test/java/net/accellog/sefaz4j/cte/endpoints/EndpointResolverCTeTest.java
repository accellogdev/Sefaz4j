package net.accellog.sefaz4j.cte.endpoints;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.EndpointResolver;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class EndpointResolverCTeTest {

    private static final String CTE_INI = "/endpoints/cte-servicos.ini";
    private static final String PREFIXO = "CTE_";

    @Test
    public void todasAs27UFsResolvemRecepcaoSincNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                String url = EndpointResolver.resolver(CTE_INI, PREFIXO, uf, ambiente, Servico.CTE_RECEPCAO_SINC.getChaveIni());
                assertTrue(uf + "/" + ambiente + " não resolveu uma URL de CTeRecepcaoSinc", url != null && url.startsWith("http"));
            }
        }
    }

    @Test
    public void todasAs27UFsResolvemConsultaProtocoloNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                String url = EndpointResolver.resolver(CTE_INI, PREFIXO, uf, ambiente, Servico.CTE_CONSULTA_PROTOCOLO.getChaveIni());
                assertTrue(uf + "/" + ambiente + " não resolveu uma URL de CTeConsultaProtocolo", url != null && url.startsWith("http"));
            }
        }
    }

    @Test
    public void todasAs27UFsResolvemRecepcaoEventoNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                String url = EndpointResolver.resolver(CTE_INI, PREFIXO, uf, ambiente, Servico.CTE_RECEPCAO_EVENTO.getChaveIni());
                assertTrue(uf + "/" + ambiente + " não resolveu uma URL de RecepcaoEvento", url != null && url.startsWith("http"));
            }
        }
    }
}
