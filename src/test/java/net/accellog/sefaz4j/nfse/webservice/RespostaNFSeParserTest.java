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

        RespostaNFSe resposta = RespostaNFSeParser.parsear(corpoJson, "nfseXmlGZipB64");

        assertTrue(resposta.isSucesso());
        assertEquals("35202512345678000195000051000000000000123456", resposta.getChaveAcesso());
        assertEquals(xmlOriginal, resposta.getXmlDescomprimido());
    }

    @Test
    public void parseiaRespostaDeErroSemLancarExcecao() {
        String corpoJson = "{\"erros\":[{\"Codigo\":\"E01\",\"Descricao\":\"CNPJ inválido\"}]}";

        RespostaNFSe resposta = RespostaNFSeParser.parsear(corpoJson, "nfseXmlGZipB64");

        assertFalse(resposta.isSucesso());
        assertEquals("E01", resposta.getCodigoErro());
        assertEquals("CNPJ inválido", resposta.getMensagemErro());
    }

    @Test
    public void parseiaErroComChavesEmCamelCase() {
        String corpoJson = "{\"erros\":[{\"codigo\":\"E02\",\"descricao\":\"Certificado expirado\"}]}";

        RespostaNFSe resposta = RespostaNFSeParser.parsear(corpoJson, "nfseXmlGZipB64");

        assertFalse(resposta.isSucesso());
        assertEquals("E02", resposta.getCodigoErro());
        assertEquals("Certificado expirado", resposta.getMensagemErro());
    }
}
