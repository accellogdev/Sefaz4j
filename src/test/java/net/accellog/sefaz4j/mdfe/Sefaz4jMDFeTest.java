package net.accellog.sefaz4j.mdfe;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import net.accellog.sefaz4j.mdfe.model.ObjectFactory;
import net.accellog.sefaz4j.mdfe.model.TMDFe;
import net.accellog.sefaz4j.mdfe.model.TEndeEmi;
import net.accellog.sefaz4j.mdfe.model.TUf;
import net.accellog.sefaz4j.webservice.ComunicacaoException;
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
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Sefaz4jMDFeTest {

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
        servidor.createContext("/recepcao", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcao\">" +
                "<retEnviMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
                "<tpAmb>1</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<cStat>104</cStat><xMotivo>Lote processado</xMotivo>" +
                "<protMDFe versao=\"3.00\"><infProt>" +
                "<tpAmb>1</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<chMDFe>35250812345678000195580010000001231123456789</chMDFe>" +
                "<dhRecbto>2025-08-12T10:00:05-03:00</dhRecbto>" +
                "<nProt>135250000000005</nProt>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do MDF-e</xMotivo>" +
                "</infProt></protMDFe>" +
                "</retEnviMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/recepcao"
        );

        ResultadoEmissao resultado = Sefaz4jMDFe.enviarXmlAssinado(config, xmlMdfeAssinadoValido());

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195580010000001231123456789", resultado.getChMDFe());
        assertTrue(resultado.getXmlAutorizado().contains("<nProt>135250000000005</nProt>"));
        assertTrue("xmlAutorizado deve ser um único documento bem-formado (mdfeProc)",
            resultado.getXmlAutorizado().startsWith("<mdfeProc") && resultado.getXmlAutorizado().endsWith("</mdfeProc>"));
    }

    @Test
    public void enviarXmlAssinadoRetornaRejeitadoSemLancarExcecao() {
        servidor.createContext("/recepcao-rejeitada", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcao\">" +
                "<retEnviMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
                "<tpAmb>1</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<cStat>204</cStat><xMotivo>Duplicidade de MDF-e</xMotivo>" +
                "</retEnviMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/recepcao-rejeitada"
        );

        ResultadoEmissao resultado = Sefaz4jMDFe.enviarXmlAssinado(config, xmlMdfeAssinadoValido());

        assertFalse(resultado.isOk());
        assertEquals("204", resultado.getCStat());
    }

    // Mesmo papel de Sefaz4jNFeTest.enviarXmlAssinadoLancaComunicacaoExceptionQuandoPollingEsgotaAindaEmProcessamento:
    // se o lote continuar com cStat 103 ("em processamento") mesmo depois de ReciboPoller esgotar
    // as tentativas, a fachada deve lançar ComunicacaoException — nunca devolver um
    // ResultadoEmissao(ok=false) indistinguível de uma rejeição de negócio real. Usa um segundo
    // contexto HTTPS local (retrecepcao) que sempre responde 103, e o override de URL simétrico ao
    // de recepção para apontar o polling para ele em vez do endpoint real da SEFAZ.
    @Test(expected = ComunicacaoException.class)
    public void enviarXmlAssinadoLancaComunicacaoExceptionQuandoPollingEsgotaAindaEmProcessamento() {
        servidor.createContext("/recepcao-em-processamento", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcao\">" +
                "<retEnviMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
                "<cStat>103</cStat><xMotivo>Lote recebido com sucesso</xMotivo>" +
                "<infRec><nRec>123456789012345</nRec></infRec>" +
                "</retEnviMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });
        servidor.createContext("/retrecepcao-em-processamento", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRetRecepcao\">" +
                "<retConsReciMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
                "<cStat>103</cStat><xMotivo>Lote ainda em processamento</xMotivo>" +
                "</retConsReciMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/recepcao-em-processamento"
        );
        config.setUrlRetRecepcaoOverride(
            "https://localhost:" + servidor.getAddress().getPort() + "/retrecepcao-em-processamento"
        );
        config.setMaxTentativasPolling(2);
        config.setIntervaloPolling(Duration.ofMillis(1));

        Sefaz4jMDFe.enviarXmlAssinado(config, xmlMdfeAssinadoValido());
    }

    // Cobre o caminho completo montar -> assinar -> validar XSD -> transmitir a partir de um
    // objeto JAXB (TMDFe), e não de um XML já pronto — mesmo papel de
    // Sefaz4jCTeTest.emitirMontaAssinaEValidaCteAntesDeTransmitir.
    @Test
    public void emitirMontaAssinaEValidaMdfeAntesDeTransmitir() {
        servidor.createContext("/recepcao-emitir", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcao\">" +
                "<retEnviMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
                "<tpAmb>1</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<cStat>104</cStat><xMotivo>Lote processado</xMotivo>" +
                "<protMDFe versao=\"3.00\"><infProt>" +
                "<tpAmb>1</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<chMDFe>35250812345678000195581231000001231123456785</chMDFe>" +
                "<dhRecbto>2025-08-12T10:00:05-03:00</dhRecbto>" +
                "<nProt>135250000000007</nProt>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do MDF-e</xMotivo>" +
                "</infProt></protMDFe>" +
                "</retEnviMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(
            UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123",
            "https://localhost:" + servidor.getAddress().getPort() + "/recepcao-emitir"
        );

        TMDFe mdfe = montarMdfeMinimoValido();

        ResultadoEmissao resultado = Sefaz4jMDFe.emitir(config, mdfe);

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195581231000001231123456785", resultado.getChMDFe());
        assertTrue("Id deve ter sido injetado pelo MDFeXmlBuilder",
            mdfe.getInfMDFe().getId().startsWith("MDFe35250812345678000195581231000001231123456785"));
        assertTrue("o XML enviado deve carregar a assinatura do infMDFe",
            resultado.getXmlAutorizado().contains("<Signature") || resultado.getXmlAutorizado().contains(":Signature"));
        assertTrue(resultado.getXmlAutorizado().startsWith("<mdfeProc") && resultado.getXmlAutorizado().endsWith("</mdfeProc>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emitirRejeitaTpAmbDivergenteDoAmbienteConfigurado() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        ObjectFactory fabrica = new ObjectFactory();
        TMDFe mdfe = fabrica.createTMDFe();
        TMDFe.InfMDFe infMDFe = fabrica.createTMDFeInfMDFe();
        TMDFe.InfMDFe.Ide ide = fabrica.createTMDFeInfMDFeIde();
        ide.setTpAmb("2"); // Homologação, config está em PRODUCAO (tpAmb=1)
        infMDFe.setIde(ide);
        mdfe.setInfMDFe(infMDFe);

        Sefaz4jMDFe.emitir(config, mdfe);
    }

    /**
     * MDF-e mínimo que passa integralmente pelo {@code mdfe_v3.00.xsd} — grupos obrigatórios de
     * {@code infMDFe} confirmados diretamente contra {@code mdfeTiposBasico_v3.00.xsd}: ide, emit,
     * infModal (xs:any, igual ao infModal do CT-e — não validado, qualquer elemento bem-formado
     * serve), infDoc e tot; seg/infAdic/lacres/autXML/prodPred/infRespTec/infSolicNFF/infPAA são
     * todos opcionais e ficam de fora.
     */
    private static TMDFe montarMdfeMinimoValido() {
        ObjectFactory fabrica = new ObjectFactory();

        TMDFe mdfe = fabrica.createTMDFe();
        TMDFe.InfMDFe infMDFe = fabrica.createTMDFeInfMDFe();
        infMDFe.setVersao("3.00");

        TMDFe.InfMDFe.Ide ide = fabrica.createTMDFeInfMDFeIde();
        ide.setCUF("35");
        ide.setTpAmb("1");
        ide.setTpEmit("1");
        ide.setMod("58");
        ide.setSerie("123");
        ide.setNMDF("100000123");
        ide.setCMDF("12345678");
        ide.setModal("1");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setTpEmis("1");
        ide.setProcEmi("0");
        ide.setVerProc("1.0.0");
        ide.setUFIni(TUf.SP);
        ide.setUFFim(TUf.RJ);
        TMDFe.InfMDFe.Ide.InfMunCarrega infMunCarrega = fabrica.createTMDFeInfMDFeIdeInfMunCarrega();
        infMunCarrega.setCMunCarrega("3550308");
        infMunCarrega.setXMunCarrega("Sao Paulo");
        ide.getInfMunCarrega().add(infMunCarrega);
        infMDFe.setIde(ide);

        TMDFe.InfMDFe.Emit emit = fabrica.createTMDFeInfMDFeEmit();
        emit.setCNPJ("12345678000195");
        emit.setXNome("Transportadora Teste LTDA");
        TEndeEmi enderEmit = new TEndeEmi();
        enderEmit.setXLgr("Rua Teste");
        enderEmit.setNro("100");
        enderEmit.setXBairro("Centro");
        enderEmit.setCMun("3550308");
        enderEmit.setXMun("Sao Paulo");
        enderEmit.setCEP("01001000");
        enderEmit.setUF(TUf.SP);
        emit.setEnderEmit(enderEmit);
        infMDFe.setEmit(emit);

        TMDFe.InfMDFe.InfModal infModal = fabrica.createTMDFeInfMDFeInfModal();
        infModal.setVersaoModal("3.00");
        infModal.setAny(fragmentoModalRodoviario());
        infMDFe.setInfModal(infModal);

        TMDFe.InfMDFe.InfDoc infDoc = fabrica.createTMDFeInfMDFeInfDoc();
        TMDFe.InfMDFe.InfDoc.InfMunDescarga infMunDescarga = fabrica.createTMDFeInfMDFeInfDocInfMunDescarga();
        infMunDescarga.setCMunDescarga("3304557");
        infMunDescarga.setXMunDescarga("Rio de Janeiro");
        TMDFe.InfMDFe.InfDoc.InfMunDescarga.InfNFe infNFe = fabrica.createTMDFeInfMDFeInfDocInfMunDescargaInfNFe();
        infNFe.setChNFe("35250812345678000195550010000001231123456789");
        infMunDescarga.getInfNFe().add(infNFe);
        infDoc.getInfMunDescarga().add(infMunDescarga);
        infMDFe.setInfDoc(infDoc);

        TMDFe.InfMDFe.Tot tot = fabrica.createTMDFeInfMDFeTot();
        tot.setVCarga("1000.00");
        tot.setCUnid("01");
        tot.setQCarga("100.0000");
        infMDFe.setTot(tot);

        mdfe.setInfMDFe(infMDFe);
        return mdfe;
    }

    private static Element fragmentoModalRodoviario() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document documento = dbf.newDocumentBuilder().newDocument();
            Element rodo = documento.createElementNS("http://www.portalfiscal.inf.br/mdfe", "rodo");
            Element rntrc = documento.createElementNS("http://www.portalfiscal.inf.br/mdfe", "RNTRC");
            rntrc.setTextContent("12345678");
            rodo.appendChild(rntrc);
            documento.appendChild(rodo);
            return documento.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o fragmento de infModal do teste", e);
        }
    }

    /**
     * Assinatura estruturalmente bem-formada mas criptograficamente falsa de propósito, mesmo
     * padrão de {@code Sefaz4jCTeTest.xmlCteAssinadoValido()}: {@code ds:Signature} é obrigatório
     * em {@code TMDFe} (sem minOccurs="0"), então o XSD exige o elemento, mas
     * {@code enviarXmlAssinado} só valida contra o XSD e transmite — não verifica a assinatura.
     */
    @Test
    public void consultarSituacaoRetornaAutorizada() {
        servidor.createContext("/consulta", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeConsulta\">" +
                "<retConsSitMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do MDF-e</xMotivo>" +
                "<cUF>35</cUF>" +
                "<protMDFe versao=\"3.00\"><infProt>" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<chMDFe>35250812345678000195580010000001231123456789</chMDFe>" +
                "<dhRecbto>2025-08-12T10:05:00-03:00</dhRecbto>" +
                "<nProt>135250000000001</nProt>" +
                "<cStat>100</cStat><xMotivo>Autorizado o uso do MDF-e</xMotivo>" +
                "</infProt></protMDFe>" +
                "</retConsSitMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlConsultaProtocoloOverride("https://localhost:" + servidor.getAddress().getPort() + "/consulta");

        ResultadoConsulta resultado = Sefaz4jMDFe.consultarSituacao(config, "35250812345678000195580010000001231123456789");

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("35250812345678000195580010000001231123456789", resultado.getChMDFe());
        assertTrue(resultado.getProtocoloXml().contains("<nProt>135250000000001</nProt>"));
    }

    @Test
    public void consultarSituacaoRetornaNaoAutorizadaSemLancarExcecao() {
        servidor.createContext("/consulta-inexistente", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeConsulta\">" +
                "<retConsSitMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\">" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic>" +
                "<cStat>656</cStat><xMotivo>Consulta a MDF-e não permitida para o Ambiente de Produção</xMotivo>" +
                "<cUF>35</cUF>" +
                "</retConsSitMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlConsultaProtocoloOverride("https://localhost:" + servidor.getAddress().getPort() + "/consulta-inexistente");

        ResultadoConsulta resultado = Sefaz4jMDFe.consultarSituacao(config, "35250812345678000195580010000001231123456789");

        assertFalse(resultado.isOk());
        assertEquals("656", resultado.getCStat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void consultarSituacaoRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jMDFe.consultarSituacao(config, "chave-invalida");
    }

    @Test
    public void cancelarRetornaEventoRegistrado() {
        AtomicReference<String> corpoCapturado = new AtomicReference<>();
        servidor.createContext("/evento-cancelamento", exchange -> {
            String corpoRecebido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            corpoCapturado.set(corpoRecebido);
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento\">" +
                "<retEventoMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado ao MDF-e</xMotivo>" +
                "<chMDFe>35250812345678000195580010000001231123456789</chMDFe>" +
                "<tpEvento>110111</tpEvento><xEvento>Cancelamento</xEvento><nSeqEvento>1</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:11:00-03:00</dhRegEvento>" +
                "<nProt>135250000000002</nProt>" +
                "</infEvento></retEventoMDFe>" +
                "</mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-cancelamento");

        ResultadoEvento resultado = Sefaz4jMDFe.cancelar(
            config,
            "35250812345678000195580010000001231123456789",
            "135250000000001",
            "Justificativa de teste com quinze ou mais caracteres"
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertEquals("135250000000002", resultado.getNProt());
        assertTrue(corpoCapturado.get().contains("<tpEvento>110111</tpEvento>"));
        assertTrue(corpoCapturado.get().contains("versaoEvento=\"3.00\""));
    }

    @Test
    public void cancelarRetornaRejeitadoQuandoEventoNaoERegistrado() {
        servidor.createContext("/evento-cancelamento-rejeitado", exchange -> {
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento\">" +
                "<retEventoMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>573</cStat><xMotivo>Duplicidade de evento</xMotivo>" +
                "</infEvento></retEventoMDFe></mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-cancelamento-rejeitado");

        ResultadoEvento resultado = Sefaz4jMDFe.cancelar(
            config,
            "35250812345678000195580010000001231123456789",
            "135250000000001",
            "Justificativa de teste com quinze ou mais caracteres"
        );

        assertFalse(resultado.isOk());
        assertEquals("573", resultado.getCStat());
        assertNull("evento rejeitado nao deve ter nProt", resultado.getNProt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaNProtComFormatoInvalido() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jMDFe.cancelar(
            config,
            "35250812345678000195580010000001231123456789",
            "nao-e-um-numero",
            "Justificativa de teste com quinze ou mais caracteres"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaJustificativaCurta() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jMDFe.cancelar(
            config,
            "35250812345678000195580010000001231123456789",
            "135250000000001",
            "curta"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jMDFe.cancelar(
            config,
            "chave-invalida",
            "135250000000001",
            "Justificativa de teste com quinze ou mais caracteres"
        );
    }

    @Test
    public void encerrarRetornaEventoRegistrado() {
        AtomicReference<String> corpoCapturado = new AtomicReference<>();
        servidor.createContext("/evento-encerramento", exchange -> {
            String corpoRecebido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            corpoCapturado.set(corpoRecebido);
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento\">" +
                "<retEventoMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado ao MDF-e</xMotivo>" +
                "<chMDFe>35250812345678000195580010000001231123456789</chMDFe>" +
                "<tpEvento>110112</tpEvento><xEvento>Encerramento</xEvento><nSeqEvento>1</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:14:00-03:00</dhRegEvento>" +
                "<nProt>135250000000006</nProt>" +
                "</infEvento></retEventoMDFe>" +
                "</mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-encerramento");

        ResultadoEvento resultado = Sefaz4jMDFe.encerrar(
            config,
            "35250812345678000195580010000001231123456789",
            "135250000000001",
            "35",
            "3550308",
            "2025-08-12"
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertEquals("135250000000006", resultado.getNProt());
        assertTrue(corpoCapturado.get().contains("<tpEvento>110112</tpEvento>"));
        assertTrue(corpoCapturado.get().contains("<dtEnc>2025-08-12</dtEnc>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void encerrarRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jMDFe.encerrar(config, "chave-invalida", "135250000000001", "35", "3550308", "2025-08-12");
    }

    @Test(expected = IllegalArgumentException.class)
    public void encerrarRejeitaNProtComFormatoInvalido() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jMDFe.encerrar(
            config,
            "35250812345678000195580010000001231123456789",
            "nao-e-um-numero",
            "35",
            "3550308",
            "2025-08-12"
        );
    }

    @Test
    public void incluirCondutorRetornaEventoRegistrado() {
        AtomicReference<String> corpoCapturado = new AtomicReference<>();
        servidor.createContext("/evento-condutor", exchange -> {
            String corpoRecebido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            corpoCapturado.set(corpoRecebido);
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento\">" +
                "<retEventoMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado ao MDF-e</xMotivo>" +
                "<chMDFe>35250812345678000195580010000001231123456789</chMDFe>" +
                "<tpEvento>110114</tpEvento><xEvento>Inclusao Condutor</xEvento><nSeqEvento>1</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:16:00-03:00</dhRegEvento>" +
                "</infEvento></retEventoMDFe>" +
                "</mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-condutor");

        ResultadoEvento resultado = Sefaz4jMDFe.incluirCondutor(
            config,
            "35250812345678000195580010000001231123456789",
            "Jose da Silva",
            "12345678909"
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertTrue(corpoCapturado.get().contains("<tpEvento>110114</tpEvento>"));
        assertTrue(corpoCapturado.get().contains("<xNome>Jose da Silva</xNome>"));
        assertTrue(corpoCapturado.get().contains("<CPF>12345678909</CPF>"));
    }

    // Mesmo papel de Sefaz4jCTeTest para corrigirCartaDeCorrecao(config, chave, correcoes, nSeqEvento):
    // evIncCondutorMDFe_v3.00.xsd só permite um <condutor> por evento, então incluir um segundo
    // condutor no mesmo manifesto exige um segundo evento com nSeqEvento=2 — a variante de 4
    // argumentos sempre envia nSeqEvento=1 e não consegue expressar isso.
    @Test
    public void incluirCondutorComNSeqEventoExplicitoEnviaSequenciaCorreta() {
        AtomicReference<String> corpoCapturado = new AtomicReference<>();
        servidor.createContext("/evento-condutor-seq2", exchange -> {
            String corpoRecebido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            corpoCapturado.set(corpoRecebido);
            byte[] resposta = ("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap:Body><mdfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento\">" +
                "<retEventoMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\" versao=\"3.00\"><infEvento>" +
                "<tpAmb>2</tpAmb><verAplic>RS_1.0.0</verAplic><cOrgao>35</cOrgao>" +
                "<cStat>135</cStat><xMotivo>Evento registrado e vinculado ao MDF-e</xMotivo>" +
                "<chMDFe>35250812345678000195580010000001231123456789</chMDFe>" +
                "<tpEvento>110114</tpEvento><xEvento>Inclusao Condutor</xEvento><nSeqEvento>2</nSeqEvento>" +
                "<dhRegEvento>2025-08-12T10:17:00-03:00</dhRegEvento>" +
                "</infEvento></retEventoMDFe>" +
                "</mdfeResultMsg></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");
        config.setUrlRecepcaoEventoOverride("https://localhost:" + servidor.getAddress().getPort() + "/evento-condutor-seq2");

        ResultadoEvento resultado = Sefaz4jMDFe.incluirCondutor(
            config,
            "35250812345678000195580010000001231123456789",
            "Maria Souza",
            "98765432100",
            2
        );

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertTrue(corpoCapturado.get().contains("<nSeqEvento>2</nSeqEvento>"));
        assertTrue(corpoCapturado.get().contains("<xNome>Maria Souza</xNome>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incluirCondutorRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.PRODUCAO, pfxBytes, "teste123");

        Sefaz4jMDFe.incluirCondutor(config, "chave-invalida", "Jose da Silva", "12345678909");
    }

    private static String xmlMdfeAssinadoValido() {
        return "<MDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\">" +
            "<infMDFe Id=\"MDFe35250812345678000195580010000001231123456789\" versao=\"3.00\">" +
            "<ide>" +
            "<cUF>35</cUF><tpAmb>1</tpAmb><tpEmit>1</tpEmit><mod>58</mod><serie>1</serie><nMDF>123</nMDF>" +
            "<cMDF>12345678</cMDF><cDV>9</cDV><modal>1</modal><dhEmi>2025-08-12T10:00:00-03:00</dhEmi>" +
            "<tpEmis>1</tpEmis><procEmi>0</procEmi><verProc>1.0.0</verProc><UFIni>SP</UFIni><UFFim>RJ</UFFim>" +
            "<infMunCarrega><cMunCarrega>3550308</cMunCarrega><xMunCarrega>Sao Paulo</xMunCarrega></infMunCarrega>" +
            "</ide>" +
            "<emit>" +
            "<CNPJ>12345678000195</CNPJ><xNome>Transportadora Teste LTDA</xNome>" +
            "<enderEmit><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro>" +
            "<cMun>3550308</cMun><xMun>Sao Paulo</xMun><CEP>01001000</CEP><UF>SP</UF></enderEmit>" +
            "</emit>" +
            "<infModal versaoModal=\"3.00\"><rodo><RNTRC>12345678</RNTRC></rodo></infModal>" +
            "<infDoc><infMunDescarga><cMunDescarga>3304557</cMunDescarga><xMunDescarga>Rio de Janeiro</xMunDescarga>" +
            "<infNFe><chNFe>35250812345678000195550010000001231123456789</chNFe></infNFe>" +
            "</infMunDescarga></infDoc>" +
            "<tot><vCarga>1000.00</vCarga><cUnid>01</cUnid><qCarga>100.0000</qCarga></tot>" +
            "</infMDFe>" +
            "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">" +
            "<SignedInfo>" +
            "<CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
            "<SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"/>" +
            "<Reference URI=\"#MDFe35250812345678000195580010000001231123456789\">" +
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
            "</MDFe>";
    }
}
