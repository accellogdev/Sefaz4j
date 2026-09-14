package net.accellog.sefaz4j.webservice;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * {@code folha-fake-teste.pem}/{@code raiz-fake-teste.pem} são um par sintético gerado só para
 * este teste (keytool: uma "raiz" autoassinada que emite uma "folha" via CSR/gencert, com uma
 * extensão AIA apontando para uma URL falsa embutida via {@code -ext} do próprio keytool) —
 * nenhum dos dois tem qualquer relação com um certificado ou cliente real. O parser ASN.1 da AIA
 * em si é testado em separado com bytes sintéticos ({@link #construirExtensaoAia}), sem depender
 * do par de certificados.
 */
public class CadeiaCertificadoCompletaTest {

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

    @Test
    public void extraiUrlsDeCaIssuersDeUmaExtensaoAiaComDuasUrls() {
        byte[] extensao = construirExtensaoAia(
            "http://ca1.example.invalid/fake-ca.p7b",
            "http://ca2.example.invalid/fake-ca.p7b"
        );

        List<String> urls = CadeiaCertificadoCompleta.urlsCaIssuersDeExtensao(extensao);

        assertEquals(List.of(
            "http://ca1.example.invalid/fake-ca.p7b",
            "http://ca2.example.invalid/fake-ca.p7b"
        ), urls);
    }

    @Test
    public void retornaListaVaziaQuandoCertificadoNaoTemExtensaoAia() throws Exception {
        // Nenhum dos certificados sintéticos deste par tem AIA (keytool não adiciona por padrão).
        List<String> urls = CadeiaCertificadoCompleta.urlsCaIssuers(certificadoRaizFake());

        assertEquals(List.of(), urls);
    }

    @Test
    public void completaCadeiaDeUmUnicoCertificadoBuscandoAsAcsViaAia() throws Exception {
        X509Certificate folha = certificadoFolhaFake();
        X509Certificate raiz = certificadoRaizFake();
        byte[] bundleComARaiz = Files.readAllBytes(Path.of("src/test/resources/certs/raiz-fake-teste.pem"));
        Function<String, byte[]> downloaderFalso = url -> bundleComARaiz;

        X509Certificate[] cadeiaCompleta = CadeiaCertificadoCompleta.completar(
            new X509Certificate[]{folha}, downloaderFalso);

        assertEquals(2, cadeiaCompleta.length);
        assertSame(folha, cadeiaCompleta[0]);
        assertEquals(raiz.getSubjectX500Principal(), cadeiaCompleta[1].getSubjectX500Principal());
        assertEquals("cadeia[0] (folha) deveria ser emitida por cadeia[1] (raiz)",
            cadeiaCompleta[0].getIssuerX500Principal(), cadeiaCompleta[1].getSubjectX500Principal());
        assertEquals("último certificado da cadeia deveria ser autoassinado",
            cadeiaCompleta[1].getSubjectX500Principal(), cadeiaCompleta[1].getIssuerX500Principal());
    }

    @Test
    public void naoMexeNaCadeiaQuandoJaTemMaisDeUmCertificado() throws Exception {
        X509Certificate folha = certificadoFolhaFake();
        X509Certificate[] cadeiaOriginal = new X509Certificate[]{folha, folha};

        X509Certificate[] resultado = CadeiaCertificadoCompleta.completar(
            cadeiaOriginal, url -> {
                throw new AssertionError("não deveria tentar baixar nada quando a cadeia já tem mais de 1 certificado");
            });

        assertArrayEquals(cadeiaOriginal, resultado);
    }

    @Test
    public void mantemCadeiaOriginalQuandoDownloadFalhaParaTodasAsUrls() throws Exception {
        X509Certificate folha = certificadoFolhaFake();
        X509Certificate[] cadeiaOriginal = new X509Certificate[]{folha};

        X509Certificate[] resultado = CadeiaCertificadoCompleta.completar(
            cadeiaOriginal, url -> {
                throw new RuntimeException("falha simulada de rede");
            });

        assertArrayEquals(cadeiaOriginal, resultado);
    }

    /**
     * Monta, à mão, os bytes que {@code X509Certificate.getExtensionValue(...)} devolveria para uma
     * extensão Authority Information Access com uma ou mais URLs de "CA Issuers" — só com tipos e
     * comprimentos em forma curta (suficiente pra qualquer URL de teste usada aqui, todas < 128
     * bytes), sem precisar de nenhum certificado real ou forjado para exercitar o parser.
     */
    private static byte[] construirExtensaoAia(String... urlsCaIssuers) {
        byte[] oidCaIssuers = {0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x02};

        ByteArrayOutputStream aiaSequenceBody = new ByteArrayOutputStream();
        for (String url : urlsCaIssuers) {
            byte[] urlBytes = url.getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream accessDescriptionBody = new ByteArrayOutputStream();
            accessDescriptionBody.writeBytes(oidCaIssuers);
            accessDescriptionBody.write(0x86); // GeneralName [6] IMPLICIT, primitivo
            accessDescriptionBody.write(urlBytes.length);
            accessDescriptionBody.writeBytes(urlBytes);
            byte[] accessDescriptionBytes = accessDescriptionBody.toByteArray();

            aiaSequenceBody.write(0x30);
            aiaSequenceBody.write(accessDescriptionBytes.length);
            aiaSequenceBody.writeBytes(accessDescriptionBytes);
        }
        byte[] aiaSequenceContent = aiaSequenceBody.toByteArray();

        ByteArrayOutputStream aiaSequence = new ByteArrayOutputStream();
        aiaSequence.write(0x30); // SEQUENCE do AuthorityInfoAccessSyntax
        aiaSequence.write(aiaSequenceContent.length);
        aiaSequence.writeBytes(aiaSequenceContent);
        byte[] aiaSequenceBytes = aiaSequence.toByteArray();

        ByteArrayOutputStream extnValue = new ByteArrayOutputStream();
        extnValue.write(0x04); // OCTET STRING externo (o que getExtensionValue devolve)
        extnValue.write(aiaSequenceBytes.length);
        extnValue.writeBytes(aiaSequenceBytes);
        return extnValue.toByteArray();
    }
}
