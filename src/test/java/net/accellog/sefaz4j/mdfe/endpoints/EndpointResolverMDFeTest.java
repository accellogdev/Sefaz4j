package net.accellog.sefaz4j.mdfe.endpoints;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.EndpointResolver;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class EndpointResolverMDFeTest {

    private static final String MDFE_INI = "/endpoints/mdfe-servicos.ini";
    private static final String PREFIXO = "MDFE_";

    @Test
    public void todasAs27UFsResolvemRecepcaoNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                String url = EndpointResolver.resolver(MDFE_INI, PREFIXO, uf, ambiente, Servico.MDFE_RECEPCAO.getChaveIni());
                assertTrue(uf + "/" + ambiente + " não resolveu uma URL de MDFeRecepcao", url != null && url.startsWith("http"));
            }
        }
    }

    @Test
    public void todasAs27UFsResolvemRetRecepcaoNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                String url = EndpointResolver.resolver(MDFE_INI, PREFIXO, uf, ambiente, Servico.MDFE_RET_RECEPCAO.getChaveIni());
                assertTrue(uf + "/" + ambiente + " não resolveu uma URL de MDFeRetRecepcao", url != null && url.startsWith("http"));
            }
        }
    }

    @Test
    public void todasAs27UFsResolvemConsultaProtocoloNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                String url = EndpointResolver.resolver(MDFE_INI, PREFIXO, uf, ambiente, Servico.MDFE_CONSULTA_PROTOCOLO.getChaveIni());
                assertTrue(uf + "/" + ambiente + " não resolveu uma URL de MDFeConsultaProtocolo", url != null && url.startsWith("http"));
            }
        }
    }

    @Test
    public void todasAs27UFsResolvemRecepcaoEventoNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                String url = EndpointResolver.resolver(MDFE_INI, PREFIXO, uf, ambiente, Servico.RECEPCAO_EVENTO.getChaveIni());
                assertTrue(uf + "/" + ambiente + " não resolveu uma URL de RecepcaoEvento", url != null && url.startsWith("http"));
            }
        }
    }
}
