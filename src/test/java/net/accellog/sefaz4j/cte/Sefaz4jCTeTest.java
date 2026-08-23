package net.accellog.sefaz4j.cte;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import net.accellog.sefaz4j.cte.model.ObjectFactory;
import net.accellog.sefaz4j.cte.model.TCTe;
import net.accellog.sefaz4j.cte.model.TEndeEmi;
import net.accellog.sefaz4j.cte.model.TEndereco;
import net.accellog.sefaz4j.cte.model.TImp;
import net.accellog.sefaz4j.cte.model.TUFSemEX;
import net.accellog.sefaz4j.cte.model.TUf;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    // Cobre o caminho completo montar -> assinar -> validar XSD -> transmitir a partir de um
    // objeto JAXB (TCTe), e não de um XML já pronto. É o único teste offline que exercita a
    // perna CT-e de AssinadorXml.assinar (namespace .../cte + elemento "infCte") e a injeção de
    // chave/Id/cDV do CTeXmlBuilder dentro da fachada.
    @Test
    public void emitirMontaAssinaEValidaCteAntesDeTransmitir() {
        servidor.createContext("/recepcao-sinc-emitir", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSinc\">" +
                "<retCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\">" +
                "<tpAmb>1</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do CT-e</xMotivo>" +
                "<protCTe versao=\"4.00\"><infProt>" +
                "<tpAmb>1</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<chCTe>35250812345678000195571231000001231123456781</chCTe>" +
                "<dhRecbto>2025-08-12T10:00:05-03:00</dhRecbto>" +
                "<nProt>135250000000007</nProt>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do CT-e</xMotivo>" +
                "</infProt></protCTe>" +
                "</retCTe></cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/recepcao-sinc-emitir"
        );

        TCTe cte = montarCteMinimoValido();

        ResultadoEmissao resultado = Sefaz4jCTe.emitir(config, cte);

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195571231000001231123456781", resultado.getChCTe());
        assertEquals("Chave/Id devem ter sido injetados pelo CTeXmlBuilder",
            "CTe35250812345678000195571231000001231123456781", cte.getInfCte().getId());
        assertEquals("cDV deve ter sido injetado pelo CTeXmlBuilder", "1", cte.getInfCte().getIde().getCDV());
        assertTrue("o XML enviado deve carregar a assinatura do infCte",
            resultado.getXmlAutorizado().contains("<Signature")
                || resultado.getXmlAutorizado().contains(":Signature"));
        assertTrue(resultado.getXmlAutorizado().contains("<nProt>135250000000007</nProt>"));
        assertTrue("xmlAutorizado deve ser um único documento bem-formado (cteProc)",
            resultado.getXmlAutorizado().startsWith("<cteProc") && resultado.getXmlAutorizado().endsWith("</cteProc>"));
    }

    // Mesma proteção de Sefaz4jNFeTest.emitirRejeitaTpAmbDivergenteDoAmbienteConfigurado: emitir
    // um CT-e de homologação contra um Ambiente de produção (ou o inverso) é erro de uso e tem
    // que falhar antes de montar/assinar/transmitir qualquer coisa — por isso este teste não
    // precisa de um TCTe completo nem do servidor HTTPS.
    @Test(expected = IllegalArgumentException.class)
    public void emitirRejeitaTpAmbDivergenteDoAmbienteConfigurado() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        ObjectFactory fabrica = new ObjectFactory();
        TCTe cte = fabrica.createTCTe();
        TCTe.InfCte infCte = fabrica.createTCTeInfCte();
        TCTe.InfCte.Ide ide = fabrica.createTCTeInfCteIde();
        ide.setTpAmb("2"); // Homologação, config está em PRODUCAO (tpAmb=1)
        infCte.setIde(ide);
        cte.setInfCte(infCte);

        Sefaz4jCTe.emitir(config, cte);
    }

    /**
     * Constrói o mesmo CT-e mínimo de {@link #xmlCteAssinadoValido()}, mas como objeto
     * {@code TCTe} — é a forma que {@code Sefaz4jCTe.emitir} recebe. Deliberadamente sem
     * {@code infCte/@Id} e sem {@code ide/cDV}, para que o {@code CTeXmlBuilder} chamado por
     * {@code emitir} calcule a chave de acesso e injete ambos.
     *
     * <p>{@code serie}/{@code nCT} usam a largura cheia da chave ("123"/"100000123") porque
     * {@code ChaveAcessoCalculator} concatena os campos sem preenchê-los com zeros à esquerda,
     * e ambos os valores continuam válidos para {@code TSerie}/{@code TNF} do XSD.</p>
     */
    private static TCTe montarCteMinimoValido() {
        ObjectFactory fabrica = new ObjectFactory();

        TCTe cte = fabrica.createTCTe();
        TCTe.InfCte infCte = fabrica.createTCTeInfCte();
        infCte.setVersao("4.00");

        TCTe.InfCte.Ide ide = fabrica.createTCTeInfCteIde();
        ide.setCUF("35");
        ide.setCCT("12345678");
        ide.setCFOP("5353");
        ide.setNatOp("Prestacao de servico de transporte");
        ide.setMod("57");
        ide.setSerie("123");
        ide.setNCT("100000123");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setTpImp("1");
        ide.setTpEmis("1");
        ide.setTpAmb("1");
        ide.setTpCTe("0");
        ide.setProcEmi("0");
        ide.setVerProc("1.0.0");
        ide.setCMunEnv("3550308");
        ide.setXMunEnv("Sao Paulo");
        ide.setUFEnv(TUf.SP);
        ide.setModal("01");
        ide.setTpServ("0");
        ide.setCMunIni("3550308");
        ide.setXMunIni("Sao Paulo");
        ide.setUFIni(TUf.SP);
        ide.setCMunFim("3304557");
        ide.setXMunFim("Rio de Janeiro");
        ide.setUFFim(TUf.RJ);
        ide.setRetira("1");
        ide.setIndIEToma("1");
        TCTe.InfCte.Ide.Toma3 toma3 = fabrica.createTCTeInfCteIdeToma3();
        toma3.setToma("0");
        ide.setToma3(toma3);
        infCte.setIde(ide);

        TCTe.InfCte.Emit emit = fabrica.createTCTeInfCteEmit();
        emit.setCNPJ("12345678000195");
        emit.setIE("111111111111");
        emit.setXNome("Transportadora Teste LTDA");
        TEndeEmi enderEmit = new TEndeEmi();
        enderEmit.setXLgr("Rua Teste");
        enderEmit.setNro("100");
        enderEmit.setXBairro("Centro");
        enderEmit.setCMun("3550308");
        enderEmit.setXMun("Sao Paulo");
        enderEmit.setCEP("01001000");
        enderEmit.setUF(TUFSemEX.SP);
        emit.setEnderEmit(enderEmit);
        emit.setCRT("3");
        infCte.setEmit(emit);

        TCTe.InfCte.Rem rem = fabrica.createTCTeInfCteRem();
        rem.setCNPJ("12345678000195");
        rem.setIE("111111111111");
        rem.setXNome("Remetente Teste LTDA");
        rem.setEnderReme(enderecoTeste());
        infCte.setRem(rem);

        TCTe.InfCte.Dest dest = fabrica.createTCTeInfCteDest();
        dest.setCNPJ("98765432000198");
        dest.setIE("222222222222");
        dest.setXNome("Destinatario Teste LTDA");
        dest.setEnderDest(enderecoTeste());
        infCte.setDest(dest);

        TCTe.InfCte.VPrest vPrest = fabrica.createTCTeInfCteVPrest();
        vPrest.setVTPrest("1000.00");
        vPrest.setVRec("1000.00");
        infCte.setVPrest(vPrest);

        TImp.ICMS00 icms00 = new TImp.ICMS00();
        icms00.setCST("00");
        icms00.setVBC("1000.00");
        icms00.setPICMS("12.00");
        icms00.setVICMS("120.00");
        TImp tImp = new TImp();
        tImp.setICMS00(icms00);
        TCTe.InfCte.Imp imp = fabrica.createTCTeInfCteImp();
        imp.setICMS(tImp);
        infCte.setImp(imp);

        TCTe.InfCte.InfCTeNorm norm = fabrica.createTCTeInfCteInfCTeNorm();

        TCTe.InfCte.InfCTeNorm.InfCarga infCarga = fabrica.createTCTeInfCteInfCTeNormInfCarga();
        infCarga.setVCarga("1000.00");
        infCarga.setProPred("Carga geral");
        TCTe.InfCte.InfCTeNorm.InfCarga.InfQ infQ = fabrica.createTCTeInfCteInfCTeNormInfCargaInfQ();
        infQ.setCUnid("01");
        infQ.setTpMed("PESO BRUTO");
        infQ.setQCarga("100.0000");
        infCarga.getInfQ().add(infQ);
        norm.setInfCarga(infCarga);

        TCTe.InfCte.InfCTeNorm.InfDoc infDoc = fabrica.createTCTeInfCteInfCTeNormInfDoc();
        TCTe.InfCte.InfCTeNorm.InfDoc.InfNFe infNFe = fabrica.createTCTeInfCteInfCTeNormInfDocInfNFe();
        infNFe.setChave("35250812345678000195550010000001231123456789");
        infDoc.getInfNFe().add(infNFe);
        norm.setInfDoc(infDoc);

        // infModal é <xs:any processContents="skip">: o conteúdo do modal não é validado pelo
        // cte_v4.00.xsd, só precisa ser um elemento bem-formado.
        TCTe.InfCte.InfCTeNorm.InfModal infModal = fabrica.createTCTeInfCteInfCTeNormInfModal();
        infModal.setVersaoModal("4.00");
        infModal.setAny(fragmentoModalRodoviario());
        norm.setInfModal(infModal);

        infCte.setInfCTeNorm(norm);

        cte.setInfCte(infCte);
        return cte;
    }

    private static TEndereco enderecoTeste() {
        TEndereco endereco = new TEndereco();
        endereco.setXLgr("Rua Teste");
        endereco.setNro("100");
        endereco.setXBairro("Centro");
        endereco.setCMun("3550308");
        endereco.setXMun("Sao Paulo");
        endereco.setCEP("01001000");
        endereco.setUF(TUf.SP);
        return endereco;
    }

    private static Element fragmentoModalRodoviario() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document documento = dbf.newDocumentBuilder().newDocument();
            Element rodo = documento.createElementNS("http://www.portalfiscal.inf.br/cte", "rodo");
            Element rntrc = documento.createElementNS("http://www.portalfiscal.inf.br/cte", "RNTRC");
            rntrc.setTextContent("12345678");
            rodo.appendChild(rntrc);
            documento.appendChild(rodo);
            return documento.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o fragmento de infModal do teste", e);
        }
    }

    @Test
    public void consultarSituacaoRetornaAutorizada() {
        servidor.createContext("/consulta", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeConsultaV4\">" +
                "<retConsSitCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\">" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do CT-e</xMotivo>" +
                "<cUF>35</cUF><chCTe>35250812345678000195570010000001231123456789</chCTe>" +
                "<protCTe versao=\"4.00\"><infProt>" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<chCTe>35250812345678000195570010000001231123456789</chCTe>" +
                "<dhRecbto>2025-08-12T10:05:00-03:00</dhRecbto>" +
                "<nProt>135250000000001</nProt>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do CT-e</xMotivo>" +
                "</infProt></protCTe>" +
                "</retConsSitCTe></cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlConsultaProtocoloOverride("https://localhost:" + servidor.getAddress().getPort() + "/consulta");

        ResultadoConsulta resultado = Sefaz4jCTe.consultarSituacao(config, "35250812345678000195570010000001231123456789");

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195570010000001231123456789", resultado.getChCTe());
        assertTrue(resultado.getProtocoloXml().contains("<nProt>135250000000001</nProt>"));
    }

    @Test
    public void consultarSituacaoRetornaNaoAutorizadaSemLancarExcecao() {
        servidor.createContext("/consulta-inexistente", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeConsultaV4\">" +
                "<retConsSitCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\">" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic>" +
                "<cStat>656</cStat><xMotivo>Consulta a CT-e não permitida para o Ambiente de Produção</xMotivo>" +
                "<cUF>35</cUF><chCTe>35250812345678000195570010000001231123456789</chCTe>" +
                "</retConsSitCTe></cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlConsultaProtocoloOverride("https://localhost:" + servidor.getAddress().getPort() + "/consulta-inexistente");

        ResultadoConsulta resultado = Sefaz4jCTe.consultarSituacao(config, "35250812345678000195570010000001231123456789");

        assertFalse(resultado.isOk());
        assertEquals("656", resultado.getCStat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void consultarSituacaoRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jCTe.consultarSituacao(config, "chave-invalida");
    }

    @Test
    public void cancelarRetornaEventoRegistrado() {
        AtomicReference<String> corpoCapturado = new AtomicReference<>();
        servidor.createContext("/evento-cancelamento", exchange -> {
            String corpoRecebido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            corpoCapturado.set(corpoRecebido);
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoEventoV4\">" +
                "<retEventoCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado ao CT-e</xMotivo>" +
                "<chCTe>35250812345678000195570010000001231123456789</chCTe>" +
                "<tpEvento>110111</tpEvento><xEvento>Cancelamento</xEvento><nSeqEvento>1</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:11:00-03:00</dhRegEvento>" +
                "<nProt>135250000000002</nProt>" +
                "</infEvento></retEventoCTe>" +
                "</cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-cancelamento");

        ResultadoEvento resultado = Sefaz4jCTe.cancelar(
            config,
            "35250812345678000195570010000001231123456789",
            "135250000000001",
            "Justificativa de teste com quinze ou mais caracteres"
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertEquals("135250000000002", resultado.getNProt());
        assertTrue(corpoCapturado.get().contains("<tpEvento>110111</tpEvento>"));
        assertTrue(corpoCapturado.get().contains("versaoEvento=\"4.00\""));
    }

    // TRetEvento (eventoCTeTiposBasico_v4.00.xsd) não tem idLote nem cStat/xMotivo fora de
    // infEvento — infEvento em si é obrigatório (sem minOccurs="0"), mas seus campos finais
    // (chCTe/tpEvento/xEvento/nSeqEvento/dhRegEvento/nProt, todos com minOccurs="0") ficam de
    // fora quando o evento é rejeitado antes de qualquer registro individual, restando só
    // tpAmb/verAplic/cOrgao/cStat/xMotivo.
    @Test
    public void cancelarRetornaRejeitadoQuandoEventoNaoERegistrado() {
        servidor.createContext("/evento-cancelamento-rejeitado", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoEventoV4\">" +
                "<retEventoCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>573</cStat><xMotivo>Duplicidade de evento</xMotivo>" +
                "</infEvento></retEventoCTe></cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-cancelamento-rejeitado");

        ResultadoEvento resultado = Sefaz4jCTe.cancelar(
            config,
            "35250812345678000195570010000001231123456789",
            "135250000000001",
            "Justificativa de teste com quinze ou mais caracteres"
        );

        assertFalse(resultado.isOk());
        assertEquals("573", resultado.getCStat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaJustificativaCurta() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jCTe.cancelar(
            config,
            "35250812345678000195570010000001231123456789",
            "135250000000001",
            "curta"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jCTe.cancelar(
            config,
            "chave-invalida",
            "135250000000001",
            "Justificativa de teste com quinze ou mais caracteres"
        );
    }

    @Test
    public void corrigirCartaDeCorrecaoRetornaEventoRegistrado() {
        AtomicReference<String> corpoCapturado = new AtomicReference<>();
        servidor.createContext("/evento-cce", exchange -> {
            String corpoRecebido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            corpoCapturado.set(corpoRecebido);
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoEventoV4\">" +
                "<retEventoCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado ao CT-e</xMotivo>" +
                "<chCTe>35250812345678000195570010000001231123456789</chCTe>" +
                "<tpEvento>110110</tpEvento><xEvento>Carta de Correção</xEvento><nSeqEvento>1</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:12:00-03:00</dhRegEvento>" +
                "<nProt>135250000000003</nProt>" +
                "</infEvento></retEventoCTe>" +
                "</cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-cce");

        ResultadoEvento resultado = Sefaz4jCTe.corrigirCartaDeCorrecao(
            config,
            "35250812345678000195570010000001231123456789",
            List.of(new InfCorrecao("ide", "xEmi", "Endereço do emitente corrigido"))
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertEquals("135250000000003", resultado.getNProt());
        assertTrue(corpoCapturado.get().contains("<tpEvento>110110</tpEvento>"));
        assertTrue(corpoCapturado.get().contains("versaoEvento=\"4.00\""));
    }

    @Test
    public void corrigirCartaDeCorrecaoAceitaVariasCorrecoesENSeqEventoExplicito() {
        servidor.createContext("/evento-cce-multiplo", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><cteResultMsg xmlns=\"http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoEventoV4\">" +
                "<retEventoCTe xmlns=\"http://www.portalfiscal.inf.br/cte\" versao=\"4.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>SP_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado ao CT-e</xMotivo>" +
                "<chCTe>35250812345678000195570010000001231123456789</chCTe>" +
                "<tpEvento>110110</tpEvento><xEvento>Carta de Correção</xEvento><nSeqEvento>2</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:13:00-03:00</dhRegEvento>" +
                "<nProt>135250000000004</nProt>" +
                "</infEvento></retEventoCTe>" +
                "</cteResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-cce-multiplo");

        ResultadoEvento resultado = Sefaz4jCTe.corrigirCartaDeCorrecao(
            config,
            "35250812345678000195570010000001231123456789",
            List.of(
                new InfCorrecao("ide", "xEmi", "Endereço do emitente corrigido"),
                new InfCorrecao("rem", "xNome", "Nome do remetente corrigido", 1)
            ),
            2
        );

        assertTrue(resultado.isOk());
        assertEquals("135250000000004", resultado.getNProt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void corrigirCartaDeCorrecaoRejeitaListaVazia() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jCTe.corrigirCartaDeCorrecao(
            config,
            "35250812345678000195570010000001231123456789",
            List.of()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void corrigirCartaDeCorrecaoRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jCTe.corrigirCartaDeCorrecao(
            config,
            "chave-invalida",
            List.of(new InfCorrecao("ide", "xEmi", "Endereço do emitente corrigido"))
        );
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
