package net.accellog.sefaz4j.nfe;

import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class Sefaz4jNFeManifestacaoTest {
    @Test
    public void montaXmlDeCienciaComCOrgao91EEventoCorreto() {
        byte[] pfxFake = new byte[]{1, 2, 3}; // XML montado antes da assinatura; assinatura testada nos testes de integracao existentes
        Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, pfxFake, "senha");
        String chave = "35240612345678000199550010000000011234567890";

        String xml = Sefaz4jNFe.montarXmlManifestacaoCiencia(config, chave, "98765432000188", 1);

        assertTrue(xml.contains("<cOrgao>91</cOrgao>"));
        assertTrue(xml.contains("<CNPJ>98765432000188</CNPJ>"));
        assertTrue(xml.contains("<chNFe>" + chave + "</chNFe>"));
        assertTrue(xml.contains("<tpEvento>210210</tpEvento>"));
        assertTrue(xml.contains("<descEvento>Ciencia da Operacao</descEvento>"));
    }
}
