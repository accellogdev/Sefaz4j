package net.accellog.sefaz4j.distdfe.integration;

import net.accellog.sefaz4j.distdfe.ResultadoDistribuicaoDFe;
import net.accellog.sefaz4j.distdfe.Sefaz4jConfig;
import net.accellog.sefaz4j.distdfe.Sefaz4jDistDfe;
import net.accellog.sefaz4j.distdfe.TipoDocumentoDistDfe;
import net.accellog.sefaz4j.endpoints.Ambiente;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assume.assumeTrue;

/**
 * Teste de integração real contra a Distribuição de DFe da SEFAZ (Ambiente Nacional,
 * Homologação). Gated por variáveis de ambiente (mesmo padrão de
 * {@code net.accellog.sefaz4j.nfe.integration.HomologacaoIntegrationTest}) para não rodar em
 * CI/local sem um certificado A1 de teste disponível.
 *
 * <p>Objetivo principal deste teste NÃO é apenas "passar": é validar empiricamente qual das
 * duas formas de resposta a SEFAZ realmente devolve para nfeDistDFeInteresseResult —
 * (a) um elemento retDistDFeInt já aninhado como XML, ou (b) o mesmo conteúdo como texto
 * XML escapado dentro do result — e, se necessário, ajustar
 * {@link net.accellog.sefaz4j.distdfe.RetDistDFeIntParser} antes de liberar o bot-dist-dfe
 * para produção. Por isso imprime cStat/xMotivo explicitamente em vez de só assertar ok().</p>
 */
public class Sefaz4jDistDfeIntegrationTest {

    @Test
    public void consultaDistribuicaoPorUltNsuEmHomologacao() throws Exception {
        String caminhoPfx = System.getenv("SEFAZ4J_TEST_PFX_PATH");
        String senhaPfx = System.getenv("SEFAZ4J_TEST_PFX_SENHA");
        String cnpjDoCertificadoDeTeste = System.getenv("SEFAZ4J_TEST_CNPJ");
        assumeTrue(
            "Defina SEFAZ4J_TEST_PFX_PATH, SEFAZ4J_TEST_PFX_SENHA e SEFAZ4J_TEST_CNPJ (CNPJ titular do certificado de teste) para rodar este teste contra Homologação real",
            caminhoPfx != null && senhaPfx != null && cnpjDoCertificadoDeTeste != null
        );

        byte[] pfxBytes = Files.readAllBytes(Path.of(caminhoPfx));
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, senhaPfx);

        ResultadoDistribuicaoDFe resultado = Sefaz4jDistDfe.distribuicaoPorUltNSU(
            TipoDocumentoDistDfe.NFE, config, null, cnpjDoCertificadoDeTeste, "0"
        );

        System.out.println("cStat=" + resultado.getCStat() + " xMotivo=" + resultado.getXMotivo());
        System.out.println("ultNSU=" + resultado.getUltNSU() + " maxNSU=" + resultado.getMaxNSU());
        System.out.println("documentos retornados=" + resultado.getDocumentos().size());
    }
}
