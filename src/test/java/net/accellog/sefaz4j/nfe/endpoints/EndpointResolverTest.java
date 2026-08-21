package net.accellog.sefaz4j.nfe.endpoints;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EndpointResolverTest {

    @Test
    public void resolveDireto_SP_Producao_Autorizacao() {
        String url = EndpointResolver.resolver(UF.SP, Ambiente.PRODUCAO, Servico.NFE_AUTORIZACAO);
        assertEquals("https://nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx", url);
    }

    @Test
    public void resolveDireto_SP_Homologacao_RetAutorizacao() {
        String url = EndpointResolver.resolver(UF.SP, Ambiente.HOMOLOGACAO, Servico.NFE_RET_AUTORIZACAO);
        assertEquals("https://homologacao.nfe.fazenda.sp.gov.br/ws/nferetautorizacao4.asmx", url);
    }

    @Test
    public void resolveIndireto_AC_ViaSVRS_Producao() {
        // [NFe_AC_P] só tem "Usar=NFe_SVRS_P" -> tem que cair no grupo compartilhado
        String url = EndpointResolver.resolver(UF.AC, Ambiente.PRODUCAO, Servico.NFE_AUTORIZACAO);
        assertEquals("https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx", url);
    }

    @Test
    public void resolveIndireto_MA_ViaSVAN_Homologacao() {
        // [NFe_MA_H] só tem "Usar=NFe_SVAN_H" (+ override de ConsultaCadastro, irrelevante aqui)
        String url = EndpointResolver.resolver(UF.MA, Ambiente.HOMOLOGACAO, Servico.NFE_AUTORIZACAO);
        assertEquals("https://hom.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx", url);
    }

    @Test
    public void todasAs27UFsResolvemOsDoisServicosNosDoisAmbientes() {
        for (UF uf : UF.values()) {
            for (Ambiente ambiente : Ambiente.values()) {
                for (Servico servico : Servico.values()) {
                    String url = EndpointResolver.resolver(uf, ambiente, servico);
                    assertTrue(uf + "/" + ambiente + "/" + servico + " não resolveu uma URL", url != null && url.startsWith("http"));
                }
            }
        }
    }
}
