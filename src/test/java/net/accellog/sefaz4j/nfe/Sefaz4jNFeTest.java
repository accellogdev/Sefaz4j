package net.accellog.sefaz4j.nfe;

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

import net.accellog.sefaz4j.nfe.endpoints.UF;
import net.accellog.sefaz4j.nfe.model.ObjectFactory;
import net.accellog.sefaz4j.nfe.model.TNFe;
import net.accellog.sefaz4j.nfe.validacao.ValidacaoXsdException;
import net.accellog.sefaz4j.nfe.webservice.ComunicacaoException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class Sefaz4jNFeTest {

    private HttpsServer servidor;
    private byte[] pfxBytes;

    // Sefaz4jNFe delega em SefazHttpClient, que valida o certificado do
    // servidor contra o truststore padrão da JVM (Task 8). O servidor HTTPS
    // local deste teste apresenta o certificado autoassinado de teste.pfx,
    // que não faz parte dessa cadeia de confiança padrão — então, só para
    // este teste, apontamos o truststore padrão da JVM para o próprio
    // teste.pfx, restaurando as propriedades no @After.
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
        servidor.createContext("/autorizacao", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">" +
                "<retEnviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
                "<cStat>104</cStat><xMotivo>Lote processado</xMotivo>" +
                "<protNFe versao=\"4.00\"><infProt>" +
                "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>" +
                "<nProt>135250000000001</nProt>" +
                "</infProt></protNFe>" +
                "</retEnviNFe></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });
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
        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/autorizacao"
        );

        String xmlJaAssinado = "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">" +
            "<infNFe Id=\"NFe35250812345678000195550010000001231123456789\" versao=\"4.00\">" +
            "<ide>" +
            "<cUF>35</cUF><cNF>12345678</cNF><natOp>Venda</natOp><mod>55</mod><serie>1</serie>" +
            "<nNF>123</nNF><dhEmi>2025-08-12T10:00:00-03:00</dhEmi><tpNF>1</tpNF><idDest>1</idDest>" +
            "<cMunFG>3550308</cMunFG><tpImp>1</tpImp><tpEmis>1</tpEmis><cDV>9</cDV><tpAmb>1</tpAmb>" +
            "<finNFe>1</finNFe><indFinal>1</indFinal><indPres>1</indPres><procEmi>0</procEmi><verProc>1.0</verProc>" +
            "</ide>" +
            "<emit>" +
            "<CNPJ>12345678000195</CNPJ><xNome>Empresa Teste LTDA</xNome>" +
            "<enderEmit><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro>" +
            "<cMun>3550308</cMun><xMun>Sao Paulo</xMun><UF>SP</UF><CEP>01310100</CEP></enderEmit>" +
            "<IE>ISENTO</IE><CRT>3</CRT>" +
            "</emit>" +
            "<det nItem=\"1\">" +
            "<prod><cProd>PROD001</cProd><cEAN>SEM GTIN</cEAN><xProd>Produto Teste</xProd><NCM>00</NCM>" +
            "<CFOP>5102</CFOP><uCom>UN</uCom><qCom>1</qCom><vUnCom>10.00</vUnCom><vProd>10.00</vProd>" +
            "<cEANTrib>SEM GTIN</cEANTrib><uTrib>UN</uTrib><qTrib>1</qTrib><vUnTrib>10.00</vUnTrib><indTot>1</indTot></prod>" +
            "<imposto/>" +
            "</det>" +
            "<total><ICMSTot>" +
            "<vBC>0.00</vBC><vICMS>0.00</vICMS><vICMSDeson>0.00</vICMSDeson><vFCP>0.00</vFCP>" +
            "<vBCST>0.00</vBCST><vST>0.00</vST><vFCPST>0.00</vFCPST><vFCPSTRet>0.00</vFCPSTRet>" +
            "<vProd>10.00</vProd><vFrete>0.00</vFrete><vSeg>0.00</vSeg><vDesc>0.00</vDesc><vII>0.00</vII>" +
            "<vIPI>0.00</vIPI><vIPIDevol>0.00</vIPIDevol><vPIS>0.00</vPIS><vCOFINS>0.00</vCOFINS>" +
            "<vOutro>0.00</vOutro><vNF>10.00</vNF>" +
            "</ICMSTot></total>" +
            "<transp><modFrete>9</modFrete></transp>" +
            "<pag><detPag><tPag>01</tPag><vPag>10.00</vPag></detPag></pag>" +
            "</infNFe>" +
            "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">" +
            "<SignedInfo>" +
            "<CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
            "<SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"/>" +
            "<Reference URI=\"#NFe35250812345678000195550010000001231123456789\">" +
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
            "</NFe>";

        ResultadoEmissao resultado = Sefaz4jNFe.enviarXmlAssinado(config, xmlJaAssinado);

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195550010000001231123456789", resultado.getChNFe());
        assertTrue(resultado.getXmlAutorizado().contains("<nProt>135250000000001</nProt>"));
        assertTrue("xmlAutorizado deve ser um único documento bem-formado (nfeProc)",
            resultado.getXmlAutorizado().startsWith("<nfeProc") && resultado.getXmlAutorizado().endsWith("</nfeProc>"));
    }

    // Prova do contrato de erro corrigido (finding #1 do review final):
    // ok=false deve significar SOMENTE "SEFAZ devolveu cStat != 100" — uma
    // falha técnica de validação de schema tem que se propagar como
    // exceção, nunca ser engolida num ResultadoEmissao(ok=false) fabricado.
    // enviarXmlAssinado valida o XML (ValidadorXsd.validar) antes de tocar
    // rede, então este teste não depende do servidor HTTPS local.
    @Test(expected = ValidacaoXsdException.class)
    public void enviarXmlAssinadoPropagaValidacaoXsdExceptionParaXmlInvalido() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jNFe.enviarXmlAssinado(config, "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe/></NFe>");
    }

    // Prova do finding #4 do review final: se o lote continuar com cStat
    // 103 ("em processamento") mesmo depois de ReciboPoller esgotar as
    // tentativas, a fachada deve lançar ComunicacaoException — nunca
    // devolver um ResultadoEmissao(ok=false) indistinguível de uma rejeição
    // de negócio real. Usa um segundo contexto HTTPS local (retautorizacao)
    // que sempre responde 103, e o override de URL simétrico ao de
    // autorização para apontar o polling para ele em vez do endpoint real
    // da SEFAZ.
    @Test(expected = ComunicacaoException.class)
    public void enviarXmlAssinadoLancaComunicacaoExceptionQuandoPollingEsgotaAindaEmProcessamento() {
        servidor.createContext("/autorizacao-em-processamento", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">" +
                "<retEnviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
                "<cStat>103</cStat><xMotivo>Lote recebido com sucesso</xMotivo>" +
                "<infRec><nRec>123456789012345</nRec></infRec>" +
                "</retEnviNFe></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });
        servidor.createContext("/retautorizacao-em-processamento", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRetAutorizacao4\">" +
                "<retConsReciNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
                "<cStat>103</cStat><xMotivo>Lote ainda em processamento</xMotivo>" +
                "</retConsReciNFe></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/autorizacao-em-processamento"
        );
        config.setUrlRetAutorizacaoOverride(
            "https://localhost:" + servidor.getAddress().getPort() + "/retautorizacao-em-processamento"
        );
        config.setMaxTentativasPolling(2);
        config.setIntervaloPolling(Duration.ofMillis(1));

        Sefaz4jNFe.enviarXmlAssinado(config, xmlAssinadoValido());
    }

    // Prova do finding #8 do review final: enviar um TNFe cujo ide.tpAmb
    // não corresponde ao Ambiente configurado deve falhar cedo e
    // explicitamente, antes de montar/assinar/enviar nada — evita o
    // footgun real de emitir um documento marcado Homologação contra
    // Produção (ou vice-versa).
    @Test(expected = IllegalArgumentException.class)
    public void emitirRejeitaTpAmbDivergenteDoAmbienteConfigurado() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        ObjectFactory fabrica = new ObjectFactory();
        TNFe nfe = fabrica.createTNFe();
        TNFe.InfNFe infNFe = fabrica.createTNFeInfNFe();
        TNFe.InfNFe.Ide ide = fabrica.createTNFeInfNFeIde();
        ide.setTpAmb("2"); // Homologação, config está em PRODUCAO (tpAmb=1)
        infNFe.setIde(ide);
        nfe.setInfNFe(infNFe);

        Sefaz4jNFe.emitir(config, nfe);
    }

    @Test
    public void consultarSituacaoRetornaAutorizada() {
        servidor.createContext("/consulta", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4\">" +
                "<retConsSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>" +
                "<cUF>35</cUF><dhRecbto>2025-08-12T10:05:00-03:00</dhRecbto>" +
                "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
                "<protNFe versao=\"4.00\"><infProt>" +
                "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>" +
                "<nProt>135250000000001</nProt>" +
                "</infProt></protNFe>" +
                "</retConsSitNFe></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlConsultaProtocoloOverride("https://localhost:" + servidor.getAddress().getPort() + "/consulta");

        ResultadoConsulta resultado = Sefaz4jNFe.consultarSituacao(config, "35250812345678000195550010000001231123456789");

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195550010000001231123456789", resultado.getChNFe());
        assertTrue(resultado.getProtocoloXml().contains("<nProt>135250000000001</nProt>"));
    }

    @Test
    public void consultarSituacaoRetornaNaoAutorizadaSemLancarExcecao() {
        servidor.createContext("/consulta-inexistente", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4\">" +
                "<retConsSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>217</cStat><xMotivo>NF-e não consta na base de dados da SEFAZ</xMotivo>" +
                "<cUF>35</cUF><dhRecbto>2025-08-12T10:05:00-03:00</dhRecbto>" +
                "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
                "</retConsSitNFe></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlConsultaProtocoloOverride("https://localhost:" + servidor.getAddress().getPort() + "/consulta-inexistente");

        ResultadoConsulta resultado = Sefaz4jNFe.consultarSituacao(config, "35250812345678000195550010000001231123456789");

        assertFalse(resultado.isOk());
        assertEquals("217", resultado.getCStat());
    }

    @Test
    public void cancelarRetornaEventoRegistrado() {
        servidor.createContext("/evento", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">" +
                "<retEnvEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\">" +
                "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>128</cStat><xMotivo>Lote de evento processado</xMotivo>" +
                "<retEvento versao=\"1.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>" +
                "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
                "<tpEvento>110111</tpEvento><xEvento>Cancelamento</xEvento><nSeqEvento>1</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:11:00-03:00</dhRegEvento>" +
                "<nProt>135250000000002</nProt>" +
                "</infEvento></retEvento>" +
                "</retEnvEvento></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento");

        ResultadoEvento resultado = Sefaz4jNFe.cancelar(
            config,
            "35250812345678000195550010000001231123456789",
            "135250000000001",
            "Justificativa de teste com quinze ou mais caracteres"
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertEquals("135250000000002", resultado.getNProt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaJustificativaCurta() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jNFe.cancelar(
            config,
            "35250812345678000195550010000001231123456789",
            "135250000000001",
            "curta"
        );
    }

    @Test
    public void corrigirCartaDeCorrecaoRetornaEventoRegistrado() {
        servidor.createContext("/evento-cce", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">" +
                "<retEnvEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\">" +
                "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>128</cStat><xMotivo>Lote de evento processado</xMotivo>" +
                "<retEvento versao=\"1.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>" +
                "<chNFe>35250812345678000195550010000001231123456789</chNFe>" +
                "<tpEvento>110110</tpEvento><xEvento>Carta de Correção</xEvento><nSeqEvento>1</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:12:00-03:00</dhRegEvento>" +
                "<nProt>135250000000003</nProt>" +
                "</infEvento></retEvento>" +
                "</retEnvEvento></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-cce");

        ResultadoEvento resultado = Sefaz4jNFe.corrigirCartaDeCorrecao(
            config,
            "35250812345678000195550010000001231123456789",
            "Correção de teste com quinze ou mais caracteres para descrever o erro cadastral corrigido"
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertEquals("135250000000003", resultado.getNProt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void corrigirCartaDeCorrecaoRejeitaTextoCurto() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jNFe.corrigirCartaDeCorrecao(
            config,
            "35250812345678000195550010000001231123456789",
            "curta",
            2
        );
    }

    @Test
    public void inutilizarRetornaHomologado() {
        servidor.createContext("/inutilizacao", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4\">" +
                "<retInutNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">" +
                "<infInut Id=\"ID35" + "25" + "12345678000195" + "55" + "001" + "000000123" + "000000124\">" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>102</cStat><xMotivo>Inutilização de número homologado</xMotivo>" +
                "<cUF>35</cUF><ano>25</ano><CNPJ>12345678000195</CNPJ><mod>55</mod><serie>001</serie>" +
                "<nNFIni>000000123</nNFIni><nNFFin>000000124</nNFFin>" +
                "<dhRecbto>2025-08-12T10:15:00-03:00</dhRecbto>" +
                "<nProt>135250000000004</nProt>" +
                "</infInut>" +
                "</retInutNFe></nfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlInutilizacaoOverride("https://localhost:" + servidor.getAddress().getPort() + "/inutilizacao");

        ResultadoInutilizacao resultado = Sefaz4jNFe.inutilizar(
            config, "35", "25", "12345678000195", "1", "123", "124",
            "Justificativa de teste com quinze ou mais caracteres"
        );

        assertTrue(resultado.isOk());
        assertEquals("102", resultado.getCStat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void inutilizarRejeitaJustificativaCurta() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jNFe.inutilizar(config, "35", "25", "12345678000195", "1", "123", "124", "curta");
    }

    @Test(expected = IllegalArgumentException.class)
    public void inutilizarRejeitaCamposComTamanhoErrado() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        // nNFIni com 10 dígitos (formato natural, sem zero à esquerda) excede a largura de 9
        // reservada para o Id — concatenação não bate em 41 dígitos.
        Sefaz4jNFe.inutilizar(
            config, "35", "25", "12345678000195", "1", "1234567890", "124",
            "Justificativa de teste com quinze ou mais caracteres"
        );
    }

    private static String xmlAssinadoValido() {
        return "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">" +
            "<infNFe Id=\"NFe35250812345678000195550010000001231123456789\" versao=\"4.00\">" +
            "<ide>" +
            "<cUF>35</cUF><cNF>12345678</cNF><natOp>Venda</natOp><mod>55</mod><serie>1</serie>" +
            "<nNF>123</nNF><dhEmi>2025-08-12T10:00:00-03:00</dhEmi><tpNF>1</tpNF><idDest>1</idDest>" +
            "<cMunFG>3550308</cMunFG><tpImp>1</tpImp><tpEmis>1</tpEmis><cDV>9</cDV><tpAmb>1</tpAmb>" +
            "<finNFe>1</finNFe><indFinal>1</indFinal><indPres>1</indPres><procEmi>0</procEmi><verProc>1.0</verProc>" +
            "</ide>" +
            "<emit>" +
            "<CNPJ>12345678000195</CNPJ><xNome>Empresa Teste LTDA</xNome>" +
            "<enderEmit><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro>" +
            "<cMun>3550308</cMun><xMun>Sao Paulo</xMun><UF>SP</UF><CEP>01310100</CEP></enderEmit>" +
            "<IE>ISENTO</IE><CRT>3</CRT>" +
            "</emit>" +
            "<det nItem=\"1\">" +
            "<prod><cProd>PROD001</cProd><cEAN>SEM GTIN</cEAN><xProd>Produto Teste</xProd><NCM>00</NCM>" +
            "<CFOP>5102</CFOP><uCom>UN</uCom><qCom>1</qCom><vUnCom>10.00</vUnCom><vProd>10.00</vProd>" +
            "<cEANTrib>SEM GTIN</cEANTrib><uTrib>UN</uTrib><qTrib>1</qTrib><vUnTrib>10.00</vUnTrib><indTot>1</indTot></prod>" +
            "<imposto/>" +
            "</det>" +
            "<total><ICMSTot>" +
            "<vBC>0.00</vBC><vICMS>0.00</vICMS><vICMSDeson>0.00</vICMSDeson><vFCP>0.00</vFCP>" +
            "<vBCST>0.00</vBCST><vST>0.00</vST><vFCPST>0.00</vFCPST><vFCPSTRet>0.00</vFCPSTRet>" +
            "<vProd>10.00</vProd><vFrete>0.00</vFrete><vSeg>0.00</vSeg><vDesc>0.00</vDesc><vII>0.00</vII>" +
            "<vIPI>0.00</vIPI><vIPIDevol>0.00</vIPIDevol><vPIS>0.00</vPIS><vCOFINS>0.00</vCOFINS>" +
            "<vOutro>0.00</vOutro><vNF>10.00</vNF>" +
            "</ICMSTot></total>" +
            "<transp><modFrete>9</modFrete></transp>" +
            "<pag><detPag><tPag>01</tPag><vPag>10.00</vPag></detPag></pag>" +
            "</infNFe>" +
            "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">" +
            "<SignedInfo>" +
            "<CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
            "<SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"/>" +
            "<Reference URI=\"#NFe35250812345678000195550010000001231123456789\">" +
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
            "</NFe>";
    }
}
