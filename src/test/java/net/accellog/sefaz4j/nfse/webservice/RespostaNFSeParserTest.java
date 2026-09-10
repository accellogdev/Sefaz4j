package net.accellog.sefaz4j.nfse.webservice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RespostaNFSeParserTest {

    @Test
    public void parseiaRespostaDeSucessoComXmlComprimido() {
        String xmlOriginal = "<NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infNFSe Id=\"NFS123\"><cStat>100</cStat></infNFSe></NFSe>";
        String comprimido = PayloadCompactado.comprimirECodificar(xmlOriginal);
        String corpoJson = "{\"chaveAcesso\":\"35202512345678000195000051000000000000123456\",\"nfseXmlGZipB64\":\"" + comprimido + "\"}";

        RespostaNFSe resposta = RespostaNFSeParser.parsear(corpoJson, "nfseXmlGZipB64", 200);

        assertTrue(resposta.isSucesso());
        assertEquals("35202512345678000195000051000000000000123456", resposta.getChaveAcesso());
        assertEquals(xmlOriginal, resposta.getXmlDescomprimido());
    }

    @Test
    public void parseiaRespostaDeErroSemLancarExcecao() {
        String corpoJson = "{\"erros\":[{\"Codigo\":\"E01\",\"Descricao\":\"CNPJ inválido\"}]}";

        RespostaNFSe resposta = RespostaNFSeParser.parsear(corpoJson, "nfseXmlGZipB64", 200);

        assertFalse(resposta.isSucesso());
        assertEquals("E01", resposta.getCodigoErro());
        assertEquals("CNPJ inválido", resposta.getMensagemErro());
    }

    @Test
    public void parseiaErroComChavesEmCamelCase() {
        String corpoJson = "{\"erros\":[{\"codigo\":\"E02\",\"descricao\":\"Certificado expirado\"}]}";

        RespostaNFSe resposta = RespostaNFSeParser.parsear(corpoJson, "nfseXmlGZipB64", 200);

        assertFalse(resposta.isSucesso());
        assertEquals("E02", resposta.getCodigoErro());
        assertEquals("Certificado expirado", resposta.getMensagemErro());
    }

    @Test
    public void statusHttpNao2xxSemArrayDeErrosNaoEhSucesso() {
        // Corpo real observado contra Homologacao (2026-09-10): HTTP 500 com pagina de erro
        // generica do ASP.NET, sem "erros" nem qualquer campo reconhecivel -- sem checar o status,
        // isso caia no caminho de sucesso por omissao.
        String corpoJson = "{\"message\":\"An error has occurred.\"}";

        RespostaNFSe resposta = RespostaNFSeParser.parsear(corpoJson, "eventoXmlGZipB64", 500);

        assertFalse(resposta.isSucesso());
        assertEquals("HTTP 500", resposta.getCodigoErro());
        assertEquals("An error has occurred.", resposta.getMensagemErro());
    }
}
