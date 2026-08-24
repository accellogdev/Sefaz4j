package net.accellog.sefaz4j.nfse;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.nfse.model.ObjectFactory;
import net.accellog.sefaz4j.nfse.model.TCCServ;
import net.accellog.sefaz4j.nfse.model.TCDPS;
import net.accellog.sefaz4j.nfse.model.TCInfDPS;
import net.accellog.sefaz4j.nfse.model.TCInfoPrestador;
import net.accellog.sefaz4j.nfse.model.TCInfoTributacao;
import net.accellog.sefaz4j.nfse.model.TCInfoValores;
import net.accellog.sefaz4j.nfse.model.TCLocPrest;
import net.accellog.sefaz4j.nfse.model.TCRegTrib;
import net.accellog.sefaz4j.nfse.model.TCServ;
import net.accellog.sefaz4j.nfse.model.TCTribMunicipal;
import net.accellog.sefaz4j.nfse.model.TCTribTotal;
import net.accellog.sefaz4j.nfse.model.TCVServPrest;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Sefaz4jNFSeTest {

    // Sefaz4jNFSe.exigirChaveAcessoValida exige exatamente 50 caracteres alfanuméricos
    // ([0-9A-Za-z]{50}) — um valor deliberadamente diferente do "chaveAcesso" de 42 caracteres
    // usado nos corpos JSON de resposta simulados abaixo (esse último é apenas um campo opaco
    // retornado pelo ADN, sem formato validado por esta biblioteca), por isso os testes de
    // consultarSituacao usam esta constante só para o parâmetro de entrada (que vira o path da URL).
    private static final String CHAVE_ACESSO_VALIDA_50_CHARS = "35202512345678000195000510000000000001234500000000";

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
        servidor.createContext("/nfse", exchange -> {
            String xmlNFSe = "<NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infNFSe Id=\"NFS123\"><cStat>100</cStat></infNFSe></NFSe>";
            String corpoJson = "{\"chaveAcesso\":\"352025123456780001950005100000000000012345\",\"nfseXmlGZipB64\":\""
                + net.accellog.sefaz4j.nfse.webservice.PayloadCompactado.comprimirECodificar(xmlNFSe) + "\"}";
            byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
        config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + "/nfse");

        ResultadoEmissao resultado = Sefaz4jNFSe.enviarXmlAssinado(config, xmlDpsAssinadoValido());

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("352025123456780001950005100000000000012345", resultado.getChaveAcesso());
        assertTrue(resultado.getXmlAutorizado().contains("<infNFSe Id=\"NFS123\">"));
    }

    /**
     * TCInfNFSe/cStat (TStat, tiposSimples_v1.01.xsd) enumera só 100/102/103/107, e as quatro são
     * NFS-e geradas com sucesso (Gerada / Decisão Judicial / Avulsa / MEI) — ao contrário da NFe/CTe,
     * aqui cStat classifica o TIPO de sucesso, não autorização-vs-rejeição. Prova que 102/103/107
     * também são reportados como ok=true (sem enfraquecer a cobertura já existente do cStat=100).
     */
    @Test
    public void enviarXmlAssinadoRetornaOkParaTodosOsCStatDeSucesso() {
        for (String cStatDeSucesso : new String[] {"102", "103", "107"}) {
            String caminho = "/nfse-cstat-" + cStatDeSucesso;
            servidor.createContext(caminho, exchange -> {
                String xmlNFSe = "<NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infNFSe Id=\"NFS123\"><cStat>"
                    + cStatDeSucesso + "</cStat></infNFSe></NFSe>";
                String corpoJson = "{\"chaveAcesso\":\"352025123456780001950005100000000000012345\",\"nfseXmlGZipB64\":\""
                    + net.accellog.sefaz4j.nfse.webservice.PayloadCompactado.comprimirECodificar(xmlNFSe) + "\"}";
                byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resposta.length);
                exchange.getResponseBody().write(resposta);
                exchange.close();
            });

            Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
            config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + caminho);

            ResultadoEmissao resultado = Sefaz4jNFSe.enviarXmlAssinado(config, xmlDpsAssinadoValido());

            assertTrue("cStat " + cStatDeSucesso + " deveria ser ok=true", resultado.isOk());
            assertEquals(cStatDeSucesso, resultado.getCStat());
        }
    }

    @Test
    public void enviarXmlAssinadoRetornaRejeitadoSemLancarExcecao() {
        servidor.createContext("/nfse-rejeitada", exchange -> {
            String corpoJson = "{\"erros\":[{\"Codigo\":\"E01\",\"Descricao\":\"CNPJ do prestador invalido\"}]}";
            byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
        config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + "/nfse-rejeitada");

        ResultadoEmissao resultado = Sefaz4jNFSe.enviarXmlAssinado(config, xmlDpsAssinadoValido());

        assertFalse(resultado.isOk());
        assertEquals("E01", resultado.getCStat());
        assertEquals("CNPJ do prestador invalido", resultado.getMensagem());
    }

    /**
     * Cobre o caminho completo montar -> assinar -> validar XSD -> transmitir a partir de um objeto
     * JAXB ({@code TCDPS}), e não de um XML já pronto. É o único teste offline que exercita a perna
     * NFS-e de {@code AssinadorXml.assinar} (namespace .../nfse + elemento "infDPS" + RSA-SHA256/
     * SHA-256) e a injeção do {@code infDPS/@Id} feita pelo {@code DpsXmlBuilder} dentro da fachada.
     */
    @Test
    public void emitirMontaAssinaEValidaDpsAntesDeTransmitir() {
        servidor.createContext("/nfse-emitir", exchange -> {
            String xmlNFSe = "<NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infNFSe Id=\"NFS456\"><cStat>100</cStat></infNFSe></NFSe>";
            String corpoJson = "{\"chaveAcesso\":\"352025123456780001950005100000000000067890\",\"nfseXmlGZipB64\":\""
                + net.accellog.sefaz4j.nfse.webservice.PayloadCompactado.comprimirECodificar(xmlNFSe) + "\"}";
            byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
        config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + "/nfse-emitir");

        TCDPS dps = montarDpsMinimaValida();

        ResultadoEmissao resultado = Sefaz4jNFSe.emitir(config, dps);

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("352025123456780001950005100000000000067890", resultado.getChaveAcesso());
        assertEquals("o Id deve ter sido injetado pelo DpsXmlBuilder",
            "DPS355030821234567800019500001100000000000123", dps.getInfDPS().getId());
    }

    /**
     * Mesma DPS mínima de {@link #xmlDpsAssinadoValido()}, mas como objeto {@code TCDPS} — é a forma
     * que {@code Sefaz4jNFSe.emitir} recebe. Deliberadamente sem {@code infDPS/@Id}, para que o
     * {@code DpsXmlBuilder} chamado por {@code emitir} o calcule e injete. O {@code versao} do
     * {@code TCDPS} é responsabilidade do chamador (o builder não o preenche).
     */
    private static TCDPS montarDpsMinimaValida() {
        ObjectFactory fabrica = new ObjectFactory();

        TCDPS dps = fabrica.createTCDPS();
        dps.setVersao("1.01");

        TCInfDPS infDPS = fabrica.createTCInfDPS();
        infDPS.setTpAmb("2");
        infDPS.setDhEmi("2025-08-12T10:00:00-03:00");
        infDPS.setVerAplic("1.0.0");
        infDPS.setSerie("00001");
        infDPS.setNDPS("100000000000123");
        infDPS.setDCompet("2025-08-12");
        infDPS.setTpEmit("1");
        infDPS.setCLocEmi("3550308");

        TCInfoPrestador prest = new TCInfoPrestador();
        prest.setCNPJ("12345678000195");
        TCRegTrib regTrib = new TCRegTrib();
        regTrib.setOpSimpNac("1");
        regTrib.setRegEspTrib("0");
        prest.setRegTrib(regTrib);
        infDPS.setPrest(prest);

        TCServ serv = new TCServ();
        TCLocPrest locPrest = new TCLocPrest();
        locPrest.setCLocPrestacao("3550308");
        serv.setLocPrest(locPrest);
        TCCServ cServ = new TCCServ();
        cServ.setCTribNac("010101");
        cServ.setXDescServ("Servico de teste");
        serv.setCServ(cServ);
        infDPS.setServ(serv);

        TCInfoValores valores = new TCInfoValores();
        TCVServPrest vServPrest = new TCVServPrest();
        vServPrest.setVServ("1000.00");
        valores.setVServPrest(vServPrest);
        TCInfoTributacao trib = new TCInfoTributacao();
        TCTribMunicipal tribMun = new TCTribMunicipal();
        tribMun.setTribISSQN("1");
        tribMun.setTpRetISSQN("1");
        trib.setTribMun(tribMun);
        TCTribTotal totTrib = new TCTribTotal();
        totTrib.setIndTotTrib("0");
        trib.setTotTrib(totTrib);
        valores.setTrib(trib);
        infDPS.setValores(valores);

        dps.setInfDPS(infDPS);
        return dps;
    }

    @Test
    public void consultarSituacaoRetornaAutorizada() {
        servidor.createContext("/consulta", exchange -> {
            String xmlNFSe = "<NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infNFSe Id=\"NFS123\"><cStat>100</cStat></infNFSe></NFSe>";
            String corpoJson = "{\"chaveAcesso\":\"352025123456780001950005100000000000012345\",\"nfseXmlGZipB64\":\""
                + net.accellog.sefaz4j.nfse.webservice.PayloadCompactado.comprimirECodificar(xmlNFSe) + "\"}";
            byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
        config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + "/consulta");

        ResultadoConsulta resultado = Sefaz4jNFSe.consultarSituacao(config, CHAVE_ACESSO_VALIDA_50_CHARS);

        assertTrue(resultado.isOk());
        assertEquals("100", resultado.getCStat());
        assertEquals("352025123456780001950005100000000000012345", resultado.getChaveAcesso());
    }

    @Test(expected = IllegalArgumentException.class)
    public void consultarSituacaoRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");

        Sefaz4jNFSe.consultarSituacao(config, "chave-muito-curta");
    }

    /**
     * Mesma cobertura que {@link #enviarXmlAssinadoRetornaOkParaTodosOsCStatDeSucesso()}, mas para
     * {@code consultarSituacao} (GET) — prova que o mesmo conjunto CSTAT_SUCESSO (102/103/107, além
     * de 100) também é reconhecido como ok=true nesta operação.
     */
    @Test
    public void consultarSituacaoRetornaOkParaTodosOsCStatDeSucesso() {
        for (String cStatDeSucesso : new String[] {"102", "103", "107"}) {
            String caminho = "/consulta-cstat-" + cStatDeSucesso;
            servidor.createContext(caminho, exchange -> {
                String xmlNFSe = "<NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infNFSe Id=\"NFS123\"><cStat>"
                    + cStatDeSucesso + "</cStat></infNFSe></NFSe>";
                String corpoJson = "{\"chaveAcesso\":\"352025123456780001950005100000000000012345\",\"nfseXmlGZipB64\":\""
                    + net.accellog.sefaz4j.nfse.webservice.PayloadCompactado.comprimirECodificar(xmlNFSe) + "\"}";
                byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resposta.length);
                exchange.getResponseBody().write(resposta);
                exchange.close();
            });

            Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
            config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + caminho);

            ResultadoConsulta resultado = Sefaz4jNFSe.consultarSituacao(config, CHAVE_ACESSO_VALIDA_50_CHARS);

            assertTrue("cStat " + cStatDeSucesso + " deveria ser ok=true", resultado.isOk());
            assertEquals(cStatDeSucesso, resultado.getCStat());
        }
    }

    /**
     * Chave de 50 caracteres compatível com {@code TSChaveNFSe} E com os patterns de
     * {@code TSIdPedRegEvt}/{@code TSIdEvento} (que exigem, nas posições 9 a 23, o tipo de inscrição
     * federal "1"/"2" seguido dos 14 caracteres da inscrição). A
     * {@link #CHAVE_ACESSO_VALIDA_50_CHARS} usada pelos testes de consulta NÃO serve aqui: ela tem
     * '3' na 9ª posição e reprovaria nos patterns dos dois Ids.
     */
    private static final String CHAVE_ACESSO_EVENTO = "35503081" + "2" + "12345678000195" + "1000000000001" + "2508" + "000012345" + "0";

    private static final String CNPJ_AUTOR = "12345678000195";
    private static final String X_MOTIVO_VALIDO = "Cancelamento por erro na emissao da nota";

    /**
     * Caminho feliz de {@code cancelar}: monta o evento de 4 níveis, valida o {@code pedRegEvento}
     * isolado, assina o {@code infEvento}, valida o {@code evento} completo e transmite. Também
     * inspeciona o corpo que chegou ao servidor para provar que o evento transmitido é mesmo o
     * {@code e101101} (e não outro tipo) — sem essa verificação, uma troca de tipo de evento passaria
     * despercebida, exatamente o defeito que a fase 2 do CT-e encontrou.
     */
    @Test
    public void cancelarAssinaValidaETransmiteOEventoE101101() {
        String[] xmlRecebido = new String[1];
        String[] pathRecebido = new String[1];

        servidor.createContext("/nfse-cancelar", exchange -> {
            pathRecebido[0] = exchange.getRequestURI().getPath();
            String corpoRequisicao = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            xmlRecebido[0] = xmlDoCampoGZipB64(corpoRequisicao, "eventoXmlGZipB64");

            String xmlEventoProcessado = "<evento xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.01\">"
                + "<infEvento Id=\"EVT123\"><cStat>135</cStat></infEvento></evento>";
            String corpoJson = "{\"eventoXmlGZipB64\":\""
                + net.accellog.sefaz4j.nfse.webservice.PayloadCompactado.comprimirECodificar(xmlEventoProcessado) + "\"}";
            byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
        config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + "/nfse-cancelar");

        ResultadoEvento resultado = Sefaz4jNFSe.cancelar(config, CHAVE_ACESSO_EVENTO, CNPJ_AUTOR, 1, X_MOTIVO_VALIDO);

        assertTrue(resultado.isOk());
        assertEquals("135", resultado.getCStat());
        assertTrue(resultado.getProtocoloXml().contains("<infEvento Id=\"EVT123\">"));

        assertEquals("/nfse-cancelar/" + CHAVE_ACESSO_EVENTO + "/eventos", pathRecebido[0]);

        String xmlEnviado = xmlRecebido[0];
        assertTrue("o XML transmitido deve conter o elemento do evento de cancelamento",
            xmlEnviado.contains("<e101101"));
        assertTrue("o xDesc é uma enumeração de valor único no TE101101",
            xmlEnviado.contains("<xDesc>Cancelamento de NFS-e</xDesc>"));
        assertTrue(xmlEnviado.contains("<cMotivo>1</cMotivo>"));
        assertTrue(xmlEnviado.contains("<xMotivo>" + X_MOTIVO_VALIDO + "</xMotivo>"));
        assertTrue("o pedRegEvento deve estar embutido no infEvento", xmlEnviado.contains("<pedRegEvento"));
        assertTrue(xmlEnviado.contains("Id=\"EVT" + CHAVE_ACESSO_EVENTO + "101101001\""));
        assertTrue(xmlEnviado.contains("Id=\"PRE" + CHAVE_ACESSO_EVENTO + "101101\""));
        // O Santuario serializa a assinatura com o prefixo "ds:", por isso a asserção é feita sobre o
        // nome local e sobre a URI referenciada, não sobre uma tag literal sem prefixo.
        assertTrue("o infEvento deve ter sido assinado", xmlEnviado.contains("SignatureValue"));
        assertTrue("a assinatura deve referenciar o Id do infEvento",
            xmlEnviado.contains("URI=\"#EVT" + CHAVE_ACESSO_EVENTO + "101101001\""));
    }

    /**
     * O leiaute de evento da NFS-e não tem NENHUM campo de status (não há cStat em
     * {@code tiposEventos_v1.01.xsd}), então o único sinal confiável de rejeição de negócio é o array
     * JSON {@code erros} — o mesmo caminho já usado por emissão/consulta.
     */
    @Test
    public void cancelarRetornaRejeitadoSemLancarExcecao() {
        servidor.createContext("/nfse-cancelar-rejeitado", exchange -> {
            String corpoJson = "{\"erros\":[{\"Codigo\":\"E1234\",\"Descricao\":\"NFS-e ja cancelada\"}]}";
            byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
        config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + "/nfse-cancelar-rejeitado");

        ResultadoEvento resultado = Sefaz4jNFSe.cancelar(config, CHAVE_ACESSO_EVENTO, CNPJ_AUTOR, 2, X_MOTIVO_VALIDO);

        assertFalse(resultado.isOk());
        assertEquals("E1234", resultado.getCStat());
        assertEquals("NFS-e ja cancelada", resultado.getMensagem());
    }

    /**
     * Uma resposta de evento aceita mas sem nenhum cStat (o caso esperado, já que o schema de evento
     * não tem esse campo) continua sendo ok=true: gatear o sucesso num cStat não confirmado
     * produziria um falso ok=false, o mesmo defeito que o {@code emitir} já teve.
     */
    @Test
    public void cancelarRetornaOkMesmoQuandoARespostaNaoTrazCStat() {
        servidor.createContext("/nfse-cancelar-sem-cstat", exchange -> {
            String xmlEventoProcessado = "<evento xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.01\">"
                + "<infEvento Id=\"EVT999\"><nSeqEvento>001</nSeqEvento></infEvento></evento>";
            String corpoJson = "{\"eventoXmlGZipB64\":\""
                + net.accellog.sefaz4j.nfse.webservice.PayloadCompactado.comprimirECodificar(xmlEventoProcessado) + "\"}";
            byte[] resposta = corpoJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });

        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");
        config.setUrlOverride("https://localhost:" + servidor.getAddress().getPort() + "/nfse-cancelar-sem-cstat");

        ResultadoEvento resultado = Sefaz4jNFSe.cancelar(config, CHAVE_ACESSO_EVENTO, CNPJ_AUTOR, 9, X_MOTIVO_VALIDO);

        assertTrue(resultado.isOk());
        assertNull(resultado.getCStat());
        assertTrue(resultado.getProtocoloXml().contains("EVT999"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaChaveAcessoInvalida() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");

        Sefaz4jNFSe.cancelar(config, "chave-muito-curta", CNPJ_AUTOR, 1, X_MOTIVO_VALIDO);
    }

    /** {@code TSCodJustCanc} enumera só 1, 2 e 9. */
    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaCMotivoForaDaEnumeracao() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");

        Sefaz4jNFSe.cancelar(config, CHAVE_ACESSO_EVENTO, CNPJ_AUTOR, 3, X_MOTIVO_VALIDO);
    }

    /** {@code TSMotivo} exige de 15 a 255 caracteres. */
    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaXMotivoCurtoDemais() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");

        Sefaz4jNFSe.cancelar(config, CHAVE_ACESSO_EVENTO, CNPJ_AUTOR, 1, "curto");
    }

    /** {@code TSCNPJ} é {@code [0-9A-Z]{14}}. */
    @Test(expected = IllegalArgumentException.class)
    public void cancelarRejeitaCnpjAutorInvalido() {
        Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "teste123");

        Sefaz4jNFSe.cancelar(config, CHAVE_ACESSO_EVENTO, "123", 1, X_MOTIVO_VALIDO);
    }

    private static String xmlDoCampoGZipB64(String corpoJson, String campo) throws java.io.IOException {
        com.fasterxml.jackson.databind.JsonNode raiz = new com.fasterxml.jackson.databind.ObjectMapper().readTree(corpoJson);
        return net.accellog.sefaz4j.nfse.webservice.PayloadCompactado
            .decodificarEDescomprimir(raiz.get(campo).asText());
    }

    /**
     * DPS mínima que passa integralmente pelo {@code DPS_v1.01.xsd} (→ {@code tiposComplexos_v1.01.xsd}
     * → {@code tiposSimples_v1.01.xsd}). Foi obtida montando um {@code TCDPS} com o conjunto
     * obrigatório do schema e iterando sobre as violações reportadas por {@code ValidadorXsd.validar}
     * até zerar; o resultado está fixado aqui como literal, mesma convenção de
     * {@code Sefaz4jCTeTest.xmlCteAssinadoValido()}/{@code Sefaz4jNFeTest.xmlAssinadoValido()}.
     *
     * <p>Campos obrigatórios de {@code TCInfDPS}: tpAmb, dhEmi, verAplic, serie, nDPS, dCompet,
     * tpEmit, cLocEmi, prest, serv, valores (+ o atributo {@code Id}); {@code toma}/{@code interm}/
     * {@code subst}/{@code IBSCBS} são {@code minOccurs="0"} e ficam de fora. Dentro de
     * {@code prest} (TCInfoPrestador) só a choice de inscrição federal e {@code regTrib}
     * (opSimpNac + regEspTrib) são obrigatórios. {@code serv} (TCServ) exige {@code locPrest}
     * (choice cLocPrestacao|cPaisPrestacao) e {@code cServ} (cTribNac + xDescServ).
     * {@code valores} (TCInfoValores) exige {@code vServPrest/vServ} e {@code trib}, e este último
     * exige {@code tribMun} (tribISSQN + tpRetISSQN) e {@code totTrib} (choice, aqui
     * {@code indTotTrib}).
     *
     * <p>O {@code Id} segue {@code TSIdDPS} — {@code DPS} + cLocEmi(7) + tipo de inscrição(1) +
     * inscrição(14) + serie(5) + nDPS(15) = 45 caracteres —, por isso {@code serie}/{@code nDPS}
     * aqui usam a largura cheia ("00001"/"100000000000123"): {@code DpsIdCalculator} concatena os
     * campos sem preenchê-los com zeros à esquerda.
     *
     * <p>{@code ds:Signature} é {@code minOccurs="0"} em {@code TCDPS}, mas o bloco abaixo é incluído
     * mesmo assim para que o fixture represente um XML já assinado. A assinatura é estruturalmente
     * bem-formada e criptograficamente falsa de propósito: {@code enviarXmlAssinado} não verifica
     * assinatura, só valida contra o XSD e transmite.
     */
    private static String xmlDpsAssinadoValido() {
        return "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.01\">" +
            "<infDPS Id=\"DPS3550308212345678000195" + "00001" + "100000000000123\">" +
            "<tpAmb>2</tpAmb>" +
            "<dhEmi>2025-08-12T10:00:00-03:00</dhEmi>" +
            "<verAplic>1.0.0</verAplic>" +
            "<serie>00001</serie>" +
            "<nDPS>100000000000123</nDPS>" +
            "<dCompet>2025-08-12</dCompet>" +
            "<tpEmit>1</tpEmit>" +
            "<cLocEmi>3550308</cLocEmi>" +
            "<prest>" +
            "<CNPJ>12345678000195</CNPJ>" +
            "<regTrib><opSimpNac>1</opSimpNac><regEspTrib>0</regEspTrib></regTrib>" +
            "</prest>" +
            "<serv>" +
            "<locPrest><cLocPrestacao>3550308</cLocPrestacao></locPrest>" +
            "<cServ><cTribNac>010101</cTribNac><xDescServ>Servico de teste</xDescServ></cServ>" +
            "</serv>" +
            "<valores>" +
            "<vServPrest><vServ>1000.00</vServ></vServPrest>" +
            "<trib>" +
            "<tribMun><tribISSQN>1</tribISSQN><tpRetISSQN>1</tpRetISSQN></tribMun>" +
            "<totTrib><indTotTrib>0</indTotTrib></totTrib>" +
            "</trib>" +
            "</valores>" +
            "</infDPS>" +
            "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">" +
            "<SignedInfo>" +
            "<CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
            "<SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"/>" +
            "<Reference URI=\"#DPS355030821234567800019500001100000000000123\">" +
            "<Transforms>" +
            "<Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/>" +
            "<Transform Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
            "</Transforms>" +
            "<DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"/>" +
            "<DigestValue>MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=</DigestValue>" +
            "</Reference>" +
            "</SignedInfo>" +
            "<SignatureValue>MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=</SignatureValue>" +
            "<KeyInfo><X509Data><X509Certificate>MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=</X509Certificate></X509Data></KeyInfo>" +
            "</Signature>" +
            "</DPS>";
    }
}
