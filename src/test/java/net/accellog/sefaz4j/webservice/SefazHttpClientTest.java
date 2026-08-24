package net.accellog.sefaz4j.webservice;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
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
import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class SefazHttpClientTest {

    private HttpsServer servidor;
    private int porta;
    private byte[] pfxBytes;

    // SefazHttpClient valida o certificado do servidor contra o truststore
    // padrão da JVM (por design: em produção isso reconhece a cadeia
    // ICP-Brasil real dos servidores da SEFAZ). O servidor HTTPS local deste
    // teste apresenta o certificado autoassinado de teste.pfx, que não faz
    // parte dessa cadeia de confiança padrão — então, só para este teste,
    // apontamos o truststore padrão da JVM (via propriedades de sistema
    // javax.net.ssl.trustStore*) para o próprio teste.pfx, para que esse
    // certificado autoassinado seja aceito como âncora de confiança. As
    // propriedades são restauradas no @After para não vazar para outros
    // testes do mesmo processo Maven/Surefire.
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
        servidor.createContext("/echo", exchange -> {
            byte[] resposta = "<retEnviNFe>ok</retEnviNFe>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });
        servidor.start();
        porta = servidor.getAddress().getPort();
    }

    @After
    public void pararServidor() {
        // As restaurações de propriedade abaixo precisam rodar sempre, mesmo
        // que @Before tenha falhado antes de atribuir `servidor` (deixando-o
        // null) ou que servidor.stop(0) lance — caso contrário o truststore
        // de teste e a flag de verificação de hostname vazariam para outras
        // classes de teste executadas no mesmo fork do Surefire.
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
    public void postaEnvelopeERecebeResposta() {
        String resposta = SefazHttpClient.postar(
            "https://localhost:" + porta + "/echo",
            "application/soap+xml; charset=utf-8; action=\"qualquer-action\"",
            "<envelope/>",
            pfxBytes,
            "teste123",
            Duration.ofSeconds(5)
        );
        assertEquals("<retEnviNFe>ok</retEnviNFe>", resposta);
    }

    @Test(expected = ComunicacaoException.class)
    public void lancaComunicacaoExceptionParaHostInexistente() {
        SefazHttpClient.postar(
            "https://host-que-nao-existe.invalid/echo",
            "application/soap+xml; charset=utf-8; action=\"qualquer-action\"",
            "<envelope/>",
            pfxBytes,
            "teste123",
            Duration.ofSeconds(2)
        );
    }
}
