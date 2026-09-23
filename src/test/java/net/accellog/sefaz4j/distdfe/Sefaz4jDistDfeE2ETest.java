package net.accellog.sefaz4j.distdfe;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.validacao.ValidacaoXsdException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Prova end-to-end (finding #2 do review final da branch inteira): antes deste teste, nenhum
 * teste exercitava {@link Sefaz4jDistDfe#executar} de ponta a ponta (montar -> validar contra o
 * XSD real -> resolver endpoint -> enviar -> parsear a resposta) para NENHUM dos dois tipos. Cada
 * task revisada isoladamente só olhava para uma classe (DistDfeIntXmlBuilderTest,
 * RetDistDFeIntParserTest, TipoDocumentoDistDfeTest, e o Sefaz4jDistDfeTest existente só cobre o
 * helper package-private montarEnvelopeSoap) -- por isso finding #1 (CTe quebrado em toda
 * chamada: namespace do documento e versão/XSD de validação errados para CTe) sobreviveu a 9
 * revisões de task.
 *
 * <p>Segue o mesmo padrão de servidor HTTPS local com certificado de teste usado por
 * {@code net.accellog.sefaz4j.nfe.Sefaz4jNFeTest} (Task 9), apontando
 * {@link Sefaz4jConfig#setUrlDistribuicaoDFeOverride(String)} para ele.</p>
 */
public class Sefaz4jDistDfeE2ETest {

    private HttpsServer servidor;
    private byte[] pfxBytes;

    private String trustStoreOriginal;
    private String trustStoreTypeOriginal;
    private String trustStorePasswordOriginal;
    private String disableHostnameVerificationOriginal;

    @Before
    public void subirServidorHttpsLocal() throws Exception {
        pfxBytes = Files.readAllBytes(Path.of("src/test/resources/certs/teste.pfx"));

        trustStoreOriginal = System.getProperty("javax.net.ssl.trustStore");
        trustStoreTypeOriginal = System.getProperty("javax.net.ssl.trustStoreType");
        trustStorePasswordOriginal = System.getProperty("javax.net.ssl.trustStorePassword");
        disableHostnameVerificationOriginal = System.getProperty("jdk.internal.httpclient.disableHostnameVerification");
        System.setProperty("javax.net.ssl.trustStore", "src/test/resources/certs/teste.pfx");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        System.setProperty("javax.net.ssl.trustStorePassword", "teste123");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(pfxBytes), "teste123".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "teste123".toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), null, null);

        servidor = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        servidor.start();
    }

    @After
    public void pararServidor() {
        try {
            if (servidor != null) {
                servidor.stop(0);
            }
        } finally {
            restaurarPropriedade("javax.net.ssl.trustStore", trustStoreOriginal);
            restaurarPropriedade("javax.net.ssl.trustStoreType", trustStoreTypeOriginal);
            restaurarPropriedade("javax.net.ssl.trustStorePassword", trustStorePasswordOriginal);
            restaurarPropriedade("jdk.internal.httpclient.disableHostnameVerification", disableHostnameVerificationOriginal);
        }
    }

    private static void restaurarPropriedade(String nome, String valorOriginal) {
        if (valorOriginal == null) {
            System.clearProperty(nome);
        } else {
            System.setProperty(nome, valorOriginal);
        }
    }

    @Test
    public void distribuicaoPorUltNsuExecutaEndToEndParaNfe() {
        servidor.createContext("/dist-dfe-nfe", exchange -> {
            byte[] resposta = respostaCanned("NFeDistribuicaoDFe", "nfe", "1.01", "138",
                "Documento(s) localizado(s)", "000000000000010", "000000000000020")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlDistribuicaoDFeOverride("https://localhost:" + servidor.getAddress().getPort() + "/dist-dfe-nfe");

        ResultadoDistribuicaoDFe resultado = Sefaz4jDistDfe.distribuicaoPorUltNSU(
            TipoDocumentoDistDfe.NFE, config, null, "12345678000199", "0"
        );

        assertTrue(resultado.isOk());
        assertEquals("138", resultado.getCStat());
        assertEquals("000000000000010", resultado.getUltNSU());
        assertEquals("000000000000020", resultado.getMaxNSU());
    }

    // Prova direta do finding #1 original: antes da primeira correção, TipoDocumentoDistDfe.CTE
    // usava distDFeInt_v1.01.xsd (o do NFe) direto, então toda chamada falhava antes de tocar
    // rede. Depois passou a usar namespace "nfe" + versao "1.00" com XSD próprio
    // (distDFeInt_cte_v1.00.xsd) -- só que esse XSD "próprio" era, na prática, uma cópia
    // byte-a-byte do de NFe (o exemplo que o ACBr distribui em Schemas/CTe/ não é uma fonte
    // curada especificamente para CTe, é datado de 2017 e idêntico ao de Schemas/NFe/). Rodando
    // contra a SEFAZ real (hom1.cte.fazenda.gov.br) essa versão ainda falhava, com
    // cStat=215 "Falha no esquema xml" -- e a própria resposta da SEFAZ já vinha com
    // xmlns="http://www.portalfiscal.inf.br/cte" no retDistDFeInt. TipoDocumentoDistDfe.CTE
    // agora usa namespace "cte" (confirmado também no código Delphi real do ACBr,
    // ACBrCTe.Consts.NAME_SPACE_CTE) e os XSDs *_cte_v1.00.xsd foram corrigidos para
    // targetNamespace "cte".
    @Test
    public void distribuicaoPorUltNsuExecutaEndToEndParaCte() {
        servidor.createContext("/dist-dfe-cte", exchange -> {
            byte[] resposta = respostaCanned("CTeDistribuicaoDFe", "cte", "1.00", "138",
                "Documento(s) localizado(s)", "000000000000030", "000000000000040")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlDistribuicaoDFeOverride("https://localhost:" + servidor.getAddress().getPort() + "/dist-dfe-cte");

        // cUFAutor=35 (SP): a CTe exige cUFAutor (minOccurs=1 no XSD oficial, diferente do
        // NFe 1.01 onde é opcional) -- confirmado como parte do próprio finding #1.
        ResultadoDistribuicaoDFe resultado = Sefaz4jDistDfe.distribuicaoPorUltNSU(
            TipoDocumentoDistDfe.CTE, config, 35, "12345678000199", "0"
        );

        assertTrue(resultado.isOk());
        assertEquals("138", resultado.getCStat());
        assertEquals("000000000000030", resultado.getUltNSU());
        assertEquals("000000000000040", resultado.getMaxNSU());
    }

    // Prova complementar: mesmo com um servidor pronto para responder, a ausência de cUFAutor
    // para CTe agora é rejeitada cedo (facade), antes de qualquer I/O -- em vez de gerar um
    // documento estruturalmente inválido perante o XSD do CTe (cUFAutor obrigatório).
    @Test(expected = IllegalArgumentException.class)
    public void distribuicaoPorUltNsuParaCteSemCUFAutorLancaIllegalArgumentException() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlDistribuicaoDFeOverride("https://localhost:" + servidor.getAddress().getPort() + "/nao-deveria-ser-chamado");

        Sefaz4jDistDfe.distribuicaoPorUltNSU(TipoDocumentoDistDfe.CTE, config, null, "12345678000199", "0");
    }

    // Prova de que, se por algum motivo a validação de cUFAutor for contornada e um XML
    // estruturalmente inválido para o CTe chegar ao validador (ex.: sem cUFAutor), a falha
    // continua sendo uma ValidacaoXsdException legível, não um erro de rede/parsing confuso.
    @Test(expected = ValidacaoXsdException.class)
    public void xmlDeCteSemCUFAutorFalhaNaValidacaoXsd() {
        String xmlSemCUFAutor = "<distDFeInt versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/cte\">"
            + "<tpAmb>2</tpAmb><CNPJ>12345678000199</CNPJ>"
            + "<distNSU><ultNSU>000000000000000</ultNSU></distNSU>"
            + "</distDFeInt>";

        net.accellog.sefaz4j.validacao.ValidadorXsd.validar(xmlSemCUFAutor, TipoDocumentoDistDfe.CTE.getXsdRequisicao());
    }

    private static String respostaCanned(String servicoWsdl, String prefixo, String versao, String cStat,
                                          String xMotivo, String ultNSU, String maxNSU) {
        String elementoResult = prefixo + "DistDFeInteresseResult";
        String elementoResponse = prefixo + "DistDFeInteresseResponse";
        return "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
            + "<soap:Body>"
            + "<" + elementoResponse + " xmlns=\"http://www.portalfiscal.inf.br/" + prefixo + "/wsdl/" + servicoWsdl + "\">"
            + "<" + elementoResult + ">"
            + "<retDistDFeInt versao=\"" + versao + "\" xmlns=\"http://www.portalfiscal.inf.br/" + prefixo + "\">"
            + "<tpAmb>2</tpAmb><verAplic>AN_1.0.0</verAplic>"
            + "<cStat>" + cStat + "</cStat><xMotivo>" + xMotivo + "</xMotivo>"
            + "<dhResp>2025-09-17T10:00:00-03:00</dhResp>"
            + "<ultNSU>" + ultNSU + "</ultNSU><maxNSU>" + maxNSU + "</maxNSU>"
            + "</retDistDFeInt>"
            + "</" + elementoResult + ">"
            + "</" + elementoResponse + ">"
            + "</soap:Body></soap:Envelope>";
    }
}
