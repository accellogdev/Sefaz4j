package net.accellog.sefaz4j.nfse.xml;

import net.accellog.sefaz4j.nfse.model.ObjectFactory;
import net.accellog.sefaz4j.nfse.model.TCDPS;
import net.accellog.sefaz4j.nfse.model.TCInfDPS;
import net.accellog.sefaz4j.nfse.model.TCInfoPrestador;
import org.junit.Test;
import org.w3c.dom.Document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DpsXmlBuilderTest {

    @Test
    public void injetaIdQuandoAusente() throws Exception {
        TCDPS dps = montarDpsMinima();

        Document documento = DpsXmlBuilder.marcarIdEMontarDocumento(dps);

        assertNotNull(documento.getDocumentElement());
        String id = dps.getInfDPS().getId();
        assertEquals("DPS" + "3550308" + "2" + "12345678000195" + "00001" + "000000000000123", id);
    }

    @Test
    public void naoSobrescreveIdJaPresente() throws Exception {
        TCDPS dps = montarDpsMinima();
        dps.getInfDPS().setId("DPSJaDefinido");

        DpsXmlBuilder.marcarIdEMontarDocumento(dps);

        assertEquals("DPSJaDefinido", dps.getInfDPS().getId());
    }

    private static TCDPS montarDpsMinima() {
        ObjectFactory fabrica = new ObjectFactory();
        TCDPS dps = fabrica.createTCDPS();

        TCInfDPS infDPS = fabrica.createTCInfDPS();
        infDPS.setTpAmb("2");
        infDPS.setSerie("00001");
        infDPS.setNDPS("000000000000123");
        infDPS.setCLocEmi("3550308");

        TCInfoPrestador prest = fabrica.createTCInfoPrestador();
        prest.setCNPJ("12345678000195");
        infDPS.setPrest(prest);

        dps.setInfDPS(infDPS);
        return dps;
    }
}
