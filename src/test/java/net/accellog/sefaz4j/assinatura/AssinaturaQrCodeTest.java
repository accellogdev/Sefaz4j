package net.accellog.sefaz4j.assinatura;

import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssinaturaQrCodeTest {

    private static final String CHAVE = "35250812345678000195581231000001231123456785";
    private static final String NS = "http://www.portalfiscal.inf.br/mdfe";

    private static byte[] pfx() throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/certs/teste.pfx"));
    }

    private static boolean confere(String chave, String signBase64) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(pfx()), "teste123".toCharArray());
        Certificate certificado = ks.getCertificate(ks.aliases().nextElement());
        Signature verificador = Signature.getInstance("SHA1withRSA");
        verificador.initVerify(certificado.getPublicKey());
        verificador.update(chave.getBytes(StandardCharsets.US_ASCII));
        return verificador.verify(Base64.getDecoder().decode(signBase64));
    }

    private static Document documentoComQrCode(String url) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();
        var raiz = documento.createElementNS(NS, "MDFe");
        var supl = documento.createElementNS(NS, "infMDFeSupl");
        var qr = documento.createElementNS(NS, "qrCodMDFe");
        qr.setTextContent(url);
        supl.appendChild(qr);
        raiz.appendChild(supl);
        documento.appendChild(raiz);
        return documento;
    }

    @Test
    public void assinaAChaveComSha1WithRsaEmBase64() throws Exception {
        String sign = AssinaturaQrCode.assinarChave(pfx(), "teste123", CHAVE);

        assertTrue(confere(CHAVE, sign));
    }

    @Test
    public void acrescentaSignAoQrCode() throws Exception {
        String url = "https://dfe-portal.svrs.rs.gov.br/mdfe/qrCode?chMDFe=" + CHAVE + "&tpAmb=1";
        Document documento = documentoComQrCode(url);

        AssinaturaQrCode.completarQrCode(documento, NS, "qrCodMDFe", CHAVE, pfx(), "teste123");

        String qr = documento.getElementsByTagNameNS(NS, "qrCodMDFe").item(0).getTextContent();
        assertTrue(qr.startsWith(url + "&sign="));
        assertTrue(confere(CHAVE, qr.substring(qr.indexOf("&sign=") + 6)));
    }

    @Test
    public void naoDuplicaSignJaPresente() throws Exception {
        String url = "https://dfe-portal.svrs.rs.gov.br/mdfe/qrCode?chMDFe=" + CHAVE + "&tpAmb=1&sign=abc";
        Document documento = documentoComQrCode(url);

        AssinaturaQrCode.completarQrCode(documento, NS, "qrCodMDFe", CHAVE, pfx(), "teste123");

        assertEquals(url, documento.getElementsByTagNameNS(NS, "qrCodMDFe").item(0).getTextContent());
    }
}
