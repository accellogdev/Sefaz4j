package net.accellog.sefaz4j.endpoints;

import net.accellog.sefaz4j.nfe.endpoints.Servico;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EndpointResolverTest {

    private static final String NFE_INI = "/endpoints/nfe-servicos.ini";
    private static final String PREFIXO = "NFE_";

    @Test
    public void resolveDireto_SP_Producao_Autorizacao() {
        String url = EndpointResolver.resolver(NFE_INI, PREFIXO, UF.SP, Ambiente.PRODUCAO, Servico.NFE_AUTORIZACAO.getChaveIni());
        assertEquals("https://nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx", url);
    }

    @Test
    public void resolveDireto_SP_Homologacao_RetAutorizacao() {
        String url = EndpointResolver.resolver(NFE_INI, PREFIXO, UF.SP, Ambiente.HOMOLOGACAO, Servico.NFE_RET_AUTORIZACAO.getChaveIni());
        assertEquals("https://homologacao.nfe.fazenda.sp.gov.br/ws/nferetautorizacao4.asmx", url);
    }

    @Test
    public void resolveIndireto_AC_ViaSVRS_Producao() {
        // [NFe_AC_P] só tem "Usar=NFe_SVRS_P" -> tem que cair no grupo compartilhado
        String url = EndpointResolver.resolver(NFE_INI, PREFIXO, UF.AC, Ambiente.PRODUCAO, Servico.NFE_AUTORIZACAO.getChaveIni());
        assertEquals("https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx", url);
    }

    @Test
    public void resolveIndireto_MA_ViaSVAN_Homologacao() {
        // [NFe_MA_H] só tem "Usar=NFe_SVAN_H" (+ override de ConsultaCadastro, irrelevante aqui)
        String url = EndpointResolver.resolver(NFE_INI, PREFIXO, UF.MA, Ambiente.HOMOLOGACAO, Servico.NFE_AUTORIZACAO.getChaveIni());
        assertEquals("https://hom.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx", url);
    }

    @Test
    public void todasAs27UFsResolvemOsDoisServicosNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                for (Servico servico : Servico.values()) {
                    String url = EndpointResolver.resolver(NFE_INI, PREFIXO, uf, ambiente, servico.getChaveIni());
                    assertTrue(uf + "/" + ambiente + "/" + servico + " não resolveu uma URL", url != null && url.startsWith("http"));
                }
            }
        }
    }
}
