package net.accellog.sefaz4j.distdfe;

import org.junit.Test;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.Assert.*;

public class RetDistDFeIntParserTest {

    private static String gzipBase64(String conteudo) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(conteudo.getBytes("UTF-8"));
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    @Test
    public void parseiaRespostaComElementoAninhado() throws Exception {
        String resNFeXml = "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><chNFe>35240612345678000199550010000000011234567890</chNFe></resNFe>";
        String docZipBase64 = gzipBase64(resNFeXml);

        String resposta = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
            + "<soap:Body>"
            + "<nfeDistDFeInteresseResponse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">"
            + "<nfeDistDFeInteresseResult>"
            + "<retDistDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
            + "<tpAmb>2</tpAmb><cStat>138</cStat><xMotivo>Documento(s) localizado(s)</xMotivo>"
            + "<ultNSU>000000000000001</ultNSU><maxNSU>000000000000005</maxNSU>"
            + "<loteDistDFeInt><docZip NSU=\"000000000000001\" schema=\"resNFe_v1.01\">" + docZipBase64 + "</docZip></loteDistDFeInt>"
            + "</retDistDFeInt>"
            + "</nfeDistDFeInteresseResult>"
            + "</nfeDistDFeInteresseResponse>"
            + "</soap:Body></soap:Envelope>";

        ResultadoDistribuicaoDFe resultado = RetDistDFeIntParser.parsear(resposta);

        assertTrue(resultado.isOk());
        assertEquals("138", resultado.getCStat());
        assertEquals("000000000000001", resultado.getUltNSU());
        assertEquals("000000000000005", resultado.getMaxNSU());
        assertEquals(1, resultado.getDocumentos().size());
        DocumentoDistDfe doc = resultado.getDocumentos().get(0);
        assertEquals("000000000000001", doc.getNsu());
        assertEquals("resNFe_v1.01", doc.getSchema());
        assertTrue(doc.getXml().contains("<chNFe>35240612345678000199550010000000011234567890</chNFe>"));
    }

    @Test
    public void parseiaRespostaComResultadoXmlEscapadoComoTexto() throws Exception {
        String retDistDfeIntXml = "<retDistDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
            + "<tpAmb>2</tpAmb><cStat>137</cStat><xMotivo>Nenhum documento localizado</xMotivo>"
            + "<ultNSU>000000000000005</ultNSU><maxNSU>000000000000005</maxNSU>"
            + "</retDistDFeInt>";
        String escapado = retDistDfeIntXml
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;");

        String resposta = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
            + "<soap:Body>"
            + "<nfeDistDFeInteresseResponse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">"
            + "<nfeDistDFeInteresseResult>" + escapado + "</nfeDistDFeInteresseResult>"
            + "</nfeDistDFeInteresseResponse>"
            + "</soap:Body></soap:Envelope>";

        ResultadoDistribuicaoDFe resultado = RetDistDFeIntParser.parsear(resposta);

        assertTrue(resultado.isOk());
        assertEquals("137", resultado.getCStat());
        assertTrue(resultado.nadaMaisADistribuir());
        assertEquals(0, resultado.getDocumentos().size());
    }

    @Test
    public void marcaConsumoIndevidoComoNaoOk() {
        String resposta = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
            + "<retDistDFeInt xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
            + "<cStat>656</cStat><xMotivo>Rejeicao: Consumo Indevido</xMotivo>"
            + "<ultNSU>000000000000005</ultNSU><maxNSU>000000000000005</maxNSU>"
            + "</retDistDFeInt></soap:Body></soap:Envelope>";

        ResultadoDistribuicaoDFe resultado = RetDistDFeIntParser.parsear(resposta);

        assertFalse(resultado.isOk());
        assertTrue(resultado.consumoIndevido());
    }
}
