package net.accellog.sefaz4j.nfse.chave;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DpsIdCalculatorTest {

    @Test
    public void calculaIdConcatenandoOsCamposNaOrdemCorreta() {
        String id = DpsIdCalculator.calcular("3550308", "2", "12345678000195", "00001", "000000000000123");

        assertEquals("DPS" + "3550308" + "2" + "12345678000195" + "00001" + "000000000000123", id);
        assertEquals(3 + 7 + 1 + 14 + 5 + 15, id.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejeitaCodMunIbgeComTamanhoErrado() {
        DpsIdCalculator.calcular("123", "2", "12345678000195", "00001", "000000000000123");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejeitaCampoNaoNumerico() {
        DpsIdCalculator.calcular("355030A", "2", "12345678000195", "00001", "000000000000123");
    }
}
