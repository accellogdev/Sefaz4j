package net.accellog.sefaz4j.nfse.webservice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PayloadCompactadoTest {

    @Test
    public void comprimeECodificaEDepoisDescomprimeEDecodificaDeVolta() {
        String xmlOriginal = "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infDPS Id=\"DPS123\"/></DPS>";

        String comprimido = PayloadCompactado.comprimirECodificar(xmlOriginal);
        String restaurado = PayloadCompactado.decodificarEDescomprimir(comprimido);

        assertEquals(xmlOriginal, restaurado);
    }

    @Test
    public void montaRequisicaoJsonComACampoChaveCorreta() {
        String xmlAssinado = "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infDPS Id=\"DPS123\"/></DPS>";

        String json = PayloadCompactado.montarRequisicaoJson("dpsXmlGZipB64", xmlAssinado);

        assertTrue(json.contains("\"dpsXmlGZipB64\""));
        // O valor não deve conter o XML em claro — deve estar comprimido+codificado.
        assertTrue(!json.contains("<DPS"));
    }
}
