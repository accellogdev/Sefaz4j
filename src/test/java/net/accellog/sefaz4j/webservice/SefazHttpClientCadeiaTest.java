package net.accellog.sefaz4j.webservice;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Cobre {@link SefazHttpClient#completarCadeiasIncompletas}: o passo que, dentro de
 * {@code criarSslContext}, detecta uma entrada do KeyStore PKCS12 cujo certificado veio sem a
 * cadeia da AC e a completa via AIA antes do {@code KeyManagerFactory} ser inicializado.
 *
 * <p>{@code folha-fake-teste.pem}/{@code raiz-fake-teste.pem} são um par 100% sintético (ver
 * {@link CadeiaCertificadoCompletaTest}), sem nenhuma relação com certificado ou cliente real. A
 * chave privada usada aqui é gerada solta, sem nenhuma relação criptográfica com a folha fake — o
 * KeyStore não valida esse pareamento ao gravar a entrada, e este teste não faz handshake TLS de
 * verdade, só verifica que a cadeia associada ao alias foi trocada.</p>
 */
public class SefazHttpClientCadeiaTest {

    private static final char[] SENHA = "teste123".toCharArray();

    private static X509Certificate certificadoFolhaFake() throws Exception {
        return lerCertificado("folha-fake-teste.pem");
    }

    private static X509Certificate certificadoRaizFake() throws Exception {
        return lerCertificado("raiz-fake-teste.pem");
    }

    private static X509Certificate lerCertificado(String nomeArquivo) throws Exception {
        Path caminho = Path.of("src/test/resources/certs/" + nomeArquivo);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(Files.readAllBytes(caminho)));
    }

    private static byte[] bundleComARaizFake() throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/certs/raiz-fake-teste.pem"));
    }

    private static KeyStore keyStoreComEntradaUnica(String alias, Certificate[] cadeia) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair parDeChavesQualquer = kpg.generateKeyPair();

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, parDeChavesQualquer.getPrivate(), SENHA, cadeia);
        return ks;
    }

    @Test
    public void completaCadeiaDeEntradaComSoOCertificadoFolha() throws Exception {
        X509Certificate folha = certificadoFolhaFake();
        byte[] bundle = bundleComARaizFake();
        KeyStore ks = keyStoreComEntradaUnica("teste", new Certificate[]{folha});

        SefazHttpClient.completarCadeiasIncompletas(ks, SENHA, url -> bundle);

        Certificate[] cadeiaFinal = ks.getCertificateChain("teste");
        assertEquals(2, cadeiaFinal.length);
        assertSame(folha, cadeiaFinal[0]);
    }

    @Test
    public void naoMexeQuandoEntradaJaTemCadeiaCompleta() throws Exception {
        X509Certificate folha = certificadoFolhaFake();
        X509Certificate raiz = certificadoRaizFake();
        // Precisa ser uma cadeia válida de verdade (subject/issuer batendo e assinatura
        // verificável) -- a raiz fake É a emissora real da folha fake -- senão o
        // PKCS12KeyStore já rejeita no setKeyEntry() do setup deste teste.
        Certificate[] cadeiaOriginal = new Certificate[]{folha, raiz};
        KeyStore ks = keyStoreComEntradaUnica("teste", cadeiaOriginal);
        Function<String, byte[]> downloaderQueNuncaDeveriaSerChamado = url -> {
            throw new AssertionError("não deveria baixar nada quando a cadeia já tem mais de 1 certificado");
        };

        SefazHttpClient.completarCadeiasIncompletas(ks, SENHA, downloaderQueNuncaDeveriaSerChamado);

        assertEquals(2, ks.getCertificateChain("teste").length);
    }

    @Test
    public void naoQuebraQuandoDownloadFalha() throws Exception {
        X509Certificate folha = certificadoFolhaFake();
        KeyStore ks = keyStoreComEntradaUnica("teste", new Certificate[]{folha});

        SefazHttpClient.completarCadeiasIncompletas(ks, SENHA, url -> {
            throw new RuntimeException("falha simulada de rede");
        });

        assertEquals(1, ks.getCertificateChain("teste").length);
    }
}
