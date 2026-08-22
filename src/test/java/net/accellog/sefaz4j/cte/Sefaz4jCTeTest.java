package net.accellog.sefaz4j.cte;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Sefaz4jCTeTest {

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
    public void enviarXmlAssinadoRetornaResultadoAutorizado() {
        servidor.createContext("/recepcao-sinc", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSinc\">" +
                "<retCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\">" +
                "<tpAmb>1</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do CT-e</xMotivo>" +
                "<protCTe versao=\"4.00\"><infProt>" +
                "<tpAmb>1</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<chCTe>35250812345678000195570010000001231123456789</chCTe>" +
                "<dhRecbto>2025-08-12T10:00:05-03:00</dhRecbto>" +
                "<nProt>135250000000005</nProt>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do CT-e</xMotivo>" +
                "</infProt></protCTe>" +
                "</retCTe></cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/recepcao-sinc"
        );

        ResultadoEmissao resultado = Sefaz4jCTe.enviarXmlAssinado(config, xmlCteAssinadoValido());

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195570010000001231123456789", resultado.getChCTe());
        assertTrue(resultado.getXmlAutorizado().contains("<nProt>135250000000005</nProt>"));
        assertTrue("xmlAutorizado deve ser um único documento bem-formado (cteProc)",
            resultado.getXmlAutorizado().startsWith("<cteProc") && resultado.getXmlAutorizado().endsWith("</cteProc>"));
    }

    @Test
    public void enviarXmlAssinadoRetornaRejeitadoSemLancarExcecao() {
        servidor.createContext("/recepcao-sinc-rejeitada", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSinc\">" +
                "<retCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\">" +
                "<tpAmb>1</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>204</cStat><xMotivo>Duplicidade de CT-e</xMotivo>" +
                "</retCTe></cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/recepcao-sinc-rejeitada"
        );

        ResultadoEmissao resultado = Sefaz4jCTe.enviarXmlAssinado(config, xmlCteAssinadoValido());

        assertFalse(resultado.isOk());
        assertEquals("204", resultado.getCStat());
    }

    /**
     * CT-e mínimo que passa integralmente pelo {@code cte_v4.00.xsd} — foi gerado montando um
     * {@code TCTe} via os tipos JAXB de {@code net.accellog.sefaz4j.cte.model} com todo o conjunto
     * obrigatório do schema (ide + toma3, emit + enderEmit + CRT, rem, dest, vPrest, imp/ICMS00 e o
     * ramo {@code infCTeNorm} da choice, com infCarga/infQ, infDoc e infModal), serializando o DOM
     * produzido por {@code CTeXmlBuilder} e acrescentando o bloco {@code <Signature>} fake abaixo.
     *
     * <p>A assinatura é estruturalmente bem-formada mas criptograficamente falsa de propósito:
     * {@code ds:Signature} é obrigatório no {@code TCTe} (sem {@code minOccurs="0"}), então o XSD
     * exige o elemento, mas {@code enviarXmlAssinado} não verifica a assinatura — só valida contra
     * o XSD e transmite. Mesmo padrão de {@code Sefaz4jNFeTest.xmlAssinadoValido()}.</p>
     */
    private static String xmlCteAssinadoValido() {
        return "<CTe xmlns=\"http://www.portalfiscal.inf.br/cte\">" +
            "<infCte Id=\"CTe35250812345678000195570010000001231123456789\" versao=\"4.00\">" +
            "<ide>" +
            "<cUF>35</cUF><cCT>12345678</cCT><CFOP>5353</CFOP><natOp>Prestacao de servico de transporte</natOp>" +
            "<mod>57</mod><serie>1</serie><nCT>123</nCT><dhEmi>2025-08-12T10:00:00-03:00</dhEmi>" +
            "<tpImp>1</tpImp><tpEmis>1</tpEmis><cDV>9</cDV><tpAmb>1</tpAmb><tpCTe>0</tpCTe>" +
            "<procEmi>0</procEmi><verProc>1.0.0</verProc>" +
            "<cMunEnv>3550308</cMunEnv><xMunEnv>Sao Paulo</xMunEnv><UFEnv>SP</UFEnv>" +
            "<modal>01</modal><tpServ>0</tpServ>" +
            "<cMunIni>3550308</cMunIni><xMunIni>Sao Paulo</xMunIni><UFIni>SP</UFIni>" +
            "<cMunFim>3304557</cMunFim><xMunFim>Rio de Janeiro</xMunFim><UFFim>RJ</UFFim>" +
            "<retira>1</retira><indIEToma>1</indIEToma><toma3><toma>0</toma></toma3>" +
            "</ide>" +
            "<emit>" +
            "<CNPJ>12345678000195</CNPJ><IE>111111111111</IE><xNome>Transportadora Teste LTDA</xNome>" +
            "<enderEmit><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro>" +
            "<cMun>3550308</cMun><xMun>Sao Paulo</xMun><CEP>01001000</CEP><UF>SP</UF></enderEmit>" +
            "<CRT>3</CRT>" +
            "</emit>" +
            "<rem>" +
            "<CNPJ>12345678000195</CNPJ><IE>111111111111</IE><xNome>Remetente Teste LTDA</xNome>" +
            "<enderReme><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro>" +
            "<cMun>3550308</cMun><xMun>Sao Paulo</xMun><CEP>01001000</CEP><UF>SP</UF></enderReme>" +
            "</rem>" +
            "<dest>" +
            "<CNPJ>98765432000198</CNPJ><IE>222222222222</IE><xNome>Destinatario Teste LTDA</xNome>" +
            "<enderDest><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro>" +
            "<cMun>3550308</cMun><xMun>Sao Paulo</xMun><CEP>01001000</CEP><UF>SP</UF></enderDest>" +
            "</dest>" +
            "<vPrest><vTPrest>1000.00</vTPrest><vRec>1000.00</vRec></vPrest>" +
            "<imp><ICMS><ICMS00><CST>00</CST><vBC>1000.00</vBC><pICMS>12.00</pICMS><vICMS>120.00</vICMS></ICMS00></ICMS></imp>" +
            "<infCTeNorm>" +
            "<infCarga><vCarga>1000.00</vCarga><proPred>Carga geral</proPred>" +
            "<infQ><cUnid>01</cUnid><tpMed>PESO BRUTO</tpMed><qCarga>100.0000</qCarga></infQ></infCarga>" +
            "<infDoc><infNFe><chave>35250812345678000195550010000001231123456789</chave></infNFe></infDoc>" +
            "<infModal versaoModal=\"4.00\"><rodo><RNTRC>12345678</RNTRC></rodo></infModal>" +
            "</infCTeNorm>" +
            "</infCte>" +
            "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">" +
            "<SignedInfo>" +
            "<CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
            "<SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"/>" +
            "<Reference URI=\"#CTe35250812345678000195570010000001231123456789\">" +
            "<Transforms>" +
            "<Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/>" +
            "<Transform Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
            "</Transforms>" +
            "<DigestMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#sha1\"/>" +
            "<DigestValue>MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=</DigestValue>" +
            "</Reference>" +
            "</SignedInfo>" +
            "<SignatureValue>MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=</SignatureValue>" +
            "<KeyInfo><X509Data><X509Certificate>MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=</X509Certificate></X509Data></KeyInfo>" +
            "</Signature>" +
            "</CTe>";
    }
}
