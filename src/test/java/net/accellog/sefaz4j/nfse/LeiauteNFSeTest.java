package net.accellog.sefaz4j.nfse;

import net.accellog.sefaz4j.endpoints.Ambiente;
import org.junit.Test;

import java.time.OffsetDateTime;

import static org.junit.Assert.assertEquals;

public class LeiauteNFSeTest {

    @Test
    public void producaoUsaNT004Ate30DeNovembroNoHorarioDeBrasilia() {
        assertEquals(LeiauteNFSe.NT004, LeiauteNFSe.vigente(Ambiente.PRODUCAO,
            OffsetDateTime.parse("2026-11-30T23:59:59-03:00").toInstant()));
        // 02:59 UTC de 01/12 ainda é 30/11 em Brasília
        assertEquals(LeiauteNFSe.NT004, LeiauteNFSe.vigente(Ambiente.PRODUCAO,
            OffsetDateTime.parse("2026-12-01T02:59:59Z").toInstant()));
    }

    @Test
    public void producaoUsaNT009A_partirDe1DeDezembro() {
        assertEquals(LeiauteNFSe.NT009, LeiauteNFSe.vigente(Ambiente.PRODUCAO,
            OffsetDateTime.parse("2026-12-01T00:00:00-03:00").toInstant()));
        assertEquals(LeiauteNFSe.NT009, LeiauteNFSe.vigente(Ambiente.PRODUCAO,
            OffsetDateTime.parse("2027-03-01T10:00:00-03:00").toInstant()));
    }

    @Test
    public void homologacaoJaUsaNT009() {
        assertEquals(LeiauteNFSe.NT009, LeiauteNFSe.vigente(Ambiente.HOMOLOGACAO,
            OffsetDateTime.parse("2026-11-18T10:00:00-03:00").toInstant()));
    }

    @Test
    public void cadaLeiauteValidaContraASuaXsd() {
        assertEquals("/schemas/nfse/nt004/DPS_v1.01.xsd", LeiauteNFSe.NT004.getXsdRaiz());
        assertEquals("/schemas/nfse/DPS_v1.01.xsd", LeiauteNFSe.NT009.getXsdRaiz());
    }
}
