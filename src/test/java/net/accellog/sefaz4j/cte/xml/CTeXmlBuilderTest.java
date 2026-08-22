package net.accellog.sefaz4j.cte.xml;

import net.accellog.sefaz4j.cte.model.ObjectFactory;
import net.accellog.sefaz4j.cte.model.TCTe;
import org.junit.Test;
import org.w3c.dom.Document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CTeXmlBuilderTest {

    @Test
    public void injetaIdECDVQuandoAusentes() throws Exception {
        TCTe cte = montarCteMinimo();

        Document documento = CTeXmlBuilder.marcarChaveEMontarDocumento(cte);

        assertNotNull(documento.getDocumentElement());
        String id = cte.getInfCte().getId();
        assertNotNull("Id deveria ter sido injetado", id);
        assertTrue("Id deve começar com 'CTe' seguido de 44 dígitos", id.matches("CTe[0-9]{44}"));
        assertEquals("Ano/mês da chave deve vir de dhEmi (2025-08)", "2508", id.substring(5, 9));
        assertNotNull("cDV deveria ter sido injetado", cte.getInfCte().getIde().getCDV());
        assertEquals(id.substring(id.length() - 1), cte.getInfCte().getIde().getCDV());
    }

    @Test
    public void naoSobrescreveIdJaPresente() throws Exception {
        TCTe cte = montarCteMinimo();
        cte.getInfCte().setId("CTeJaDefinido");

        CTeXmlBuilder.marcarChaveEMontarDocumento(cte);

        assertEquals("CTeJaDefinido", cte.getInfCte().getId());
    }

    private static TCTe montarCteMinimo() {
        ObjectFactory fabrica = new ObjectFactory();
        TCTe cte = fabrica.createTCTe();

        TCTe.InfCte infCte = fabrica.createTCTeInfCte();
        infCte.setVersao("4.00");

        TCTe.InfCte.Ide ide = fabrica.createTCTeInfCteIde();
        ide.setCUF("35");
        ide.setCCT("12345678");
        ide.setSerie("001");
        ide.setNCT("123456789");
        ide.setDhEmi("2025-08-12T10:00:00-03:00");
        ide.setTpEmis("1");
        ide.setTpAmb("2");
        infCte.setIde(ide);

        TCTe.InfCte.Emit emit = fabrica.createTCTeInfCteEmit();
        emit.setCNPJ("12345678000195");
        infCte.setEmit(emit);

        cte.setInfCte(infCte);
        return cte;
    }
}
